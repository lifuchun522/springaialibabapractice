# 第 11 掌 · 鱼跃于渊 · 图谱 Graph：图不是画出来的，是状态合并出来的

> 对应文章：<https://cloud.tencent.com/developer/article/2752094>
> 分支：`chapter/11-graph-core` ｜ 标签：`ch11`

## 一、这一掌要解决的问题

前十掌的数字人已经会聊天、会记忆、会调工具、会查知识库、会自己决定下一步。
但那条链路是「写方法、串调用」攒起来的：执行顺序在代码里，状态在局部变量里，
人机边界是一个 `if` 塞进方法中间的阻塞调用。

售后场景把这三个问题同时放大：

- **要并行取数**：知识库检索与业务系统查询之间没有数据依赖，却被迫排队；
- **要停下来等人**：高风险工单必须人工确认，而人工要等半小时——用线程去等人，
  等于把「不确定的时延」放进线程池；
- **要能续跑**：发版那一刻，所有卡在等人工的工单回到起点，主管昨天点过的同意要重新点一遍。

这三件事的共同根因只有一个：**执行顺序、状态归属、人机边界被揉在了一起**。

## 二、四条可验证的完成标准（本掌逐条落）

| # | 标准 | 本掌怎么落 | 证据 |
|---|------|-----------|------|
| 1 | 图可视化：不读代码就能看懂流程 | 图结构由框架导出成 Mermaid（节点/边/条件边都在） | `GET /after-sale/graph` 的导出内容 |
| 2 | 可中断：在人工确认前停下来，不阻塞线程 | `interruptBefore(humanReview)`；调用**立即返回**，状态标成 `INTERRUPTED` | 真实调用返回 `status=INTERRUPTED` |
| 3 | 可恢复：同一标识再次进入，从断点继续 | `RunnableConfig.threadId` + `resume()` + 放行断点节点 | 恢复时只执行 `humanReview → reply` |
| 4 | 状态可持久化：重启进程后仍拿得到 | 自写 `MysqlCheckpointSaver`（检查点落 MySQL） | **杀掉进程、重启、再恢复**的完整证据 |

## 三、链路与边界

```text
START → intent → (knowledge ∥ biz) → risk →┬→ humanReview → reply → END
                                           └──────────────→ reply

[状态] input / intent / knowledgeHits / bizRecords / riskLevel / humanDecision / reply / nodeLog
[归约] 单值事实 REPLACE ｜ 累积证据 APPEND ｜ 消息 APPEND —— 用到的每个 key 都显式声明
[断点] humanReview 之前；检查点按 threadId 落到 MySQL
```

三件事各归其位：

- **执行顺序**写在边里。并行汇合用 `addEdge(List.of(knowledge, biz), risk)`，
  分流用条件边——**顺序不是代码里的行号，是图上的边**；
- **状态归属**写在策略表里。节点只提交「变更请求」，真正决定状态的是归约规则；
- **人机边界**是一个节点 + 一个断点。它不是 `if`，所以它能被看见、被查询、被恢复。

顺带一处与前十掌一致的取舍：`biz` 这一格交给 Agent Framework（`ReactAgent.asNode()`），
因为业务查询真实场景下要多轮工具调用。**两者不是替代关系，是嵌套关系**：
需要精细流程与状态控制的地方用图，需要自主推理的一格交给 Agent。

## 四、三件只有自己写一遍才知道的事

### 1. 归约策略配错不报错，只丢数据

文章里的现象是「并行跑完之后知识片段整段消失」。本掌把它做成了**可运行的反例**：
`StateReductionTest` 用一张最小的图（两个并行节点写同一个 key）跑两遍，只改策略：

```console
appendStrategy_shouldKeepBothParallelBranchOutputs  → 两个分支的产出都在
replaceStrategy_shouldSilentlyLoseOneBranchOutput   → 只剩一个，且没有异常、没有日志
undeclaredKey_shouldFallBackToReplace...            → 不声明 = 默认覆盖，同样丢
```

所以本仓库的规矩是：**用到的每个 key 都显式声明策略，一个都不留给默认行为**。
默认行为在并行分支上就等于丢数据，而且丢得很安静。

### 2. `threadId` 是运行时配置，不是业务状态

检查点是按 `RunnableConfig.threadId` 索引的。文章里那次排查很典型：
`threadId` 被当成业务字段写进状态，恢复时从状态里读，结果「参数明明对，恢复就是重跑」。
本掌的规则是：**threadId 只在入口生成一次，业务层不持久化它**，并写进日志方便对账。

### 3. 「恢复」不止是再调一次：要说明从哪儿恢复、并且放行断点

第一版只传 `threadId` 再调一次，结果**从头重跑**。第二版加了 `resume()` 与 `checkPointId`，
又变成**原地再停一次**（状态还是 `INTERRUPTED`）——因为 `interruptBefore` 是编译进图里的规则，
恢复时必须显式放行那个节点。最终形态：

```java
RunnableConfig config = RunnableConfig.builder()
        .threadId(threadId)
        .resume()
        .checkPointId(最后一个检查点)   // 跨进程恢复时，内存里没有历史，只能从库里取
        .build();
config.withNodeResumed("humanReview");  // 放行断点节点，否则原地再停一次
```

另外还有一个只会出现在「重启后」的坑：**检查点不能按时间戳排序**。
同一毫秒内会写好几条（`intent`、并行汇合、`risk` 挤在一起），
按 `saved_at` 排序等于随机排序，恢复就会挑错检查点。
本掌在建表时用自增 `seq` 记录写入顺序——这也是为什么 V6 的 `graph_checkpoint`
主键是 `seq` 而不是 `checkpoint_id`。

## 五、本掌的取舍（写清楚代价）

- **多一层状态模型要学**：KeyStrategy、CompileConfig、SaverConfig、RunnableConfig
  四组概念，每一组配错了都不会报错。适用边界很明确：**分支 ≥ 3、有人工节点、
  或需要跨请求续跑**——三条一条都不占的流程，用方法调用更省事。
- **图结构变更要考虑历史检查点兼容**：上线后改了图，旧检查点可能无法在新图上恢复。
  本掌没有做图版本化，这一条留在遗留问题里。
- **检查点只增不删**：`graph_checkpoint` 会随运行时长增长，需要归档策略（未做）。
- **业务语义仍然在节点里**：什么算高风险、退款政策怎么写，全部留在节点内部用原来的 Service。
  **图是编排层，不是业务层**——这条边界不能糊。
- **账本新增了 PENDING 态**：中断时答复还没有产生，但流程还活着。
  用 CANCELLED 会让人以为「不用管了」，所以第 5 掌的账本扩了一态（见 `ChatMessage.Status`）。
