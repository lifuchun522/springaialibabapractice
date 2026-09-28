# 第 9 掌 · 或跃在渊 · ReactAgent：把「何时停」从采样过程收回到工程侧

> 对应文章：<https://cloud.tencent.com/developer/article/2752096>
> 分支：`chapter/09-react-agent` ｜ 标签：`ch09`

## 一、这一掌要解决的问题

前八掌的数字人已经会聊天、会记忆、会调工具、会查知识库。但那一切**都由我们写死**：
要不要检索、检索几次、调哪个工具，都是业务代码里的 if-else 决定的。
模型只在最后一刻被叫来「组织一段话」。

问题不在能力，在**掌控与可见性**：

- 多步任务靠手写编排，每一步的分支都要自己写，越写越像一棵树；
- 出问题时只看得见「输入一句话、输出一段文本」，中间发生了什么完全靠猜；
- 循环没有上界，模型探索意愿越强，越可能在一个问题上反复打转。

这一掌把「主脑」换成 `ReactAgent`：**调不调工具、调几次，交给模型在边界内决定；
但边界本身由工程侧定死，并且每一步都留痕。**

文章里那句话是本掌全部代码的出发点：

> 引入 Agent 框架的第一收益不是能力提升，是**可观测性**提升。
> 如果你引入之后仍然只能看到「输入一句话、输出一段文本」，那这次升级等于没做。

## 二、四条可验证的完成标准（本掌逐条落）

| # | 标准 | 本掌怎么落 |
|---|------|-----------|
| 1 | 事件序列完整可看：Agent → 模型 → 工具 → 模型 → 收尾 | `AgentTrace` 作为一次请求的时间线；模型调用与工具调用各由一个**框架拦截器**写入（业务代码拿不到节点调用点，拦截器是唯一稳定接入位置） |
| 2 | 单次请求的模型调用有**硬上界**，超限**显式结束**而不是超时 | `ModelCallLimitHook(runLimit=8, ExitBehavior.END)`；事件流在到达上限后停止产出 `model` 节点，仍以 `agent.end` 收尾 |
| 3 | 工具失败有重试，重试耗尽后 Agent 仍能产出面向用户的回答 | 重试与兜底都放在**工具边界**（`AuditingToolCallback`）：失败尝试逐次留痕，耗尽后返回一句模型可读的话 |
| 4 | 对外仍然只是一个字符串（STT → Agent → TTS 不变） | 接口返回 `reply` 字符串；`traceId` / `modelCalls` / `events` 是**附加**字段，调用方可以忽略 |

## 三、链路与边界

```text
[入口] POST /api/projects/{id}/agent/chat {question, sessionId}
   → 账本先落一笔 USER（第 5 掌的规矩：Memory 是投影，chat_message 是事实）
   → 构建请求内 Agent：
        工具 = 项目作用域工具（身份闭包）+ 远程 MCP 工具
        toolContext = projectId / sessionId / conversationId / traceId（旁路身份）
        hooks = ModelCallLimitHook(runLimit=8, END)
        interceptors = ModelTracing + ToolTracing
   → agent.call(question)                     ← ReAct 循环在框架的图节点里跑
        节点事件：agent.start → model#n → tool:xxx → … → agent.end
   → 空内容兜底成一句可展示的话 → 账本落一笔 ASSISTANT
   → 返回 {reply, traceId, modelCalls, events}
```

本掌的边界画在四处：

- **身份**：项目 / 会话 / 追踪号全部走 `toolContext`，**不进工具参数的 JSON Schema**（第 6 掌的硬规矩）；
  工具对象按请求绑定身份，所以模型看不到、也就填不错。
- **预算**：模型调用次数上限在图节点这一层生效——它必须站在循环外面才看得见「循环」。
- **失败**：只画在工具边界。工具失败是「一次可观察的观察结果」，不是「整段会话的终止信号」。
- **账本**：换了主脑不换账本，Agent 这条路同样落账，否则运行页历史断档、会话统计永远查到 0 条。

## 四、三件必须自己撞一遍才知道的事

### 1. 框架的重试拦截器，在我们的工具上永远不会触发

文章建议挂 `ToolRetryHook` 做有限重试、并在工具内部对最终失败做兜底返回。我们照着挂了
`ToolRetryInterceptor(maxRetries=2, RETURN_MESSAGE)`，写完测试才发现它**不可能触发**：

第 6 掌的工具边界（`AuditingToolCallback`）已经把异常转成了「一句模型能读懂的话」——
外层拦截器看到的是**正常返回**，不是失败。一个配了但永远不会生效的通道，比没有这个通道更糟：
它会让后来的人以为「重试这件事已经有人管了」。

处置：**失败策略只有一个主人，放在工具边界**。重试（`max-retries=2`）与兜底返回都实现在
`AuditingToolCallback` 里，每一次失败的尝试都单独写审计行——「重试了几次」在表里能数出来，
不靠日志猜。框架的拦截器只留可观测的那两个。

顺带一个真实缺陷：远程工具断连时，外层是**没有 message** 的 `ToolExecutionException`，
审计里只剩一个异常类型，等于没留痕。现在会往 cause 链里走一层，把根因类型带上
（实测：`工具执行失败：…ToolExecutionException（根因 ClosedChannelException）`）。

### 2. 超时不重试

失败分两种，处置必须不同：

- **异常**（下游 503、连接断开）：值得重试，抖动是常态；
- **超时**（`timeout-ms` 3 秒耗尽）：**不重试**。重试只会把等待时间翻倍，而等的人还是同一个用户。

所以 `max-retries` 只作用于异常路径；超时仍按第 6 掌的语义返回一句确定性结果。

### 3. 账本必须跟着主脑一起换

Agent 版本一开始没落账，真实跑一遍立刻暴露：问「这个会话有几条消息」，答案是 **0 条**。
因为 `session_stats` 读的是 `chat_message`，而 Agent 这条路没往里写。
这不是显示问题，是**事实层的断档**——第 5 掌定下的「Memory 是投影、账本是事实」，
换了调用路径就不成立，等于把事实丢了。

补齐之后同一个问题返回「一共 3 条消息，其中你问了我 2 次」：3 = 第一问 + 第一答 + 第二问，
数字对得上，说明账本、工具、事件三条线是同一份事实。

## 五、本掌的取舍（写清楚代价）

- **每个请求构建一次 Agent（含一次图编译）**。为什么还这么做：身份不能由模型填，
  而框架的 `toolContext` 是**构建期**参数，所以只能用「请求内作用域的工具对象」把身份闭包进去。
  长期做法是让框架支持按调用传身份，或按项目缓存 Agent 实例——这条留在验收记录的遗留问题里。
- **不引入 ChatMemory / checkpoint**：本掌先把「循环可控」做扎实；跨请求的会话记忆依赖
  saver 与状态持久化，属于下一掌（HITL 与中断恢复）的地基，提前做等于把它做浅。
- **不做流式**：`stream` 返回的是事件而不是最终字符串，调用方要自己处理收尾。
  本掌先保证「一条请求 = 一条可核对的记录」，流式留给接口层单独一掌。

## 六、验收怎么证明（不靠「应该可以」）

- **离线**：`AgentFlowTest` 用假 ChatModel 就能跑通整条 ReAct 链路——
  这是本掌比第 6 掌强的地方：那时循环在真实 `ChatModel` 实现内部（`OpenAiChatModel` 持有
  `ToolCallingManager`），假模型根本不执行工具；换到 Agent Framework 后循环搬进了图节点，
  于是「模型写 tool_calls → 框架执行工具 → 结果回灌 → 再问模型」这条链**可测了**。
- **真实**：真 DeepSeek + MySQL + MCP Server 跑一遍，把原始 JSON、审计表、日志行抄进
  `docs/ch09-验收记录.md`。

| 用例 | 对应标准 |
|------|----------|
| `agent_shouldExposeFullNodeEventSequence` | #1：`agent.start → model#1 → tool:session_stats → model#2 → agent.end` |
| `agent_shouldStopAtModelCallLimitInsteadOfLoopingForever` | #2：模型侧 `call()` 恰好 8 次，事件以 `agent.end` 收尾且无 `agent.error` |
| `agent_shouldRetryFailingToolAndStillAnswerUser` | #3：失败 3 次（1+2 重试）后仍给出回答，审计表三行 ERROR |
| `agent_shouldReturnStringEvenWhenModelProducesNothing` | #4：模型吐空白也要变成一句可展示的话 |
| `agent_shouldRejectBlankQuestion` | 入口校验：空问题 400，不进模型 |
| `call_shouldRetryUntilSuccessAndAuditOnlyTheSuccessfulAttempt` | 重试边界：第 3 次成功，审计 = 失败 ×2 + 成功 ×1 |
| `call_shouldGiveUpAfterMaxRetriesAndReturnReadableMessage` | 重试耗尽：返回一句话，不是异常 |
| `call_shouldNotRetryTimeoutBecauseWaitingLongerHelpsNobody` | 超时不重试，也不会等满 3 个超时周期 |
| `call_shouldShowRootCauseWhenWrapperExceptionHasNoMessage` | 无 message 的包装异常要能说出根因 |
