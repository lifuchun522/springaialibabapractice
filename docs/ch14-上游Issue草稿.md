# 上游 Issue 草稿（按 alibaba/spring-ai-alibaba 的 `bug_report.yml` 字段撰写）

> 状态：**草稿**。是否真的提交由仓库主人决定——提交到上游是代表个人账号的动作，
> 本仓库只负责把证据准备到「可提交」的程度（第 14 掌验收第三条）。
> 目标仓库：`alibaba/spring-ai-alibaba`；版本：`v1.1.2.2`（与 `bug_report.yml` 的 Environment 字段对应）。

---

**Title**：`[BUG] ToolRetryInterceptor does not retry when an inner interceptor converts the exception into a ToolCallResponse`

**Is there an existing issue for this?** ✅ searched（截至本稿撰写时未检索到同题）

## Current Behavior

在 `v1.1.2.2` 上，`ToolRetryInterceptor` 只对 `handler.call(request)` **抛出的异常**重试
（`ToolRetryInterceptor.java:86-90`）。如果它的**内层**存在一个把异常转成 `ToolCallResponse` 的拦截器，
重试就永远不会发生：外层看到的是正常返回。

框架自带的 `ToolErrorInterceptor`（`ToolErrorInterceptor.java:26-32`，v1.1.2.2 引入）正好就是这种拦截器：

```text
[retry, error] 顺序（先声明的在外层，见 InterceptorChain.java:83-91）
  调用链：retry → error → 真实工具
  工具抛异常 → error 捕获并返回 "Tool failed: ..." → retry 看到正常返回 → **不重试**
```

实测：`maxRetries=2`、工具每次都抛异常，**工具只被调用 1 次**。

## Expected Behavior

两种合理期望之一，需要维护者确认哪个是本意：

1. `ToolRetryInterceptor` 把「非成功的 `ToolCallResponse`」也视为失败并重试
   （**main 上已经这么做了**：`ToolCallResponse.SUCCESS_STATUS` 判定 + 抛 `RuntimeException(result)` 重试）；
2. 或者在文档/注释里明确写出顺序契约：**「重试拦截器必须声明在『把异常转成响应』的拦截器之前（即更外层）』**。
   当前 `InterceptorChain` 的注释只说明了「先声明的在外层」，没有说明它与错误处理拦截器的组合含义。

## Steps To Reproduce

最小复现（无模型、无数据库、3 秒内跑完，仅依赖 `spring-ai-alibaba-agent-framework:1.1.2.2`）：

```java
AtomicInteger calls = new AtomicInteger();
ToolCallHandler failing = req -> {
    calls.incrementAndGet();
    throw new IllegalStateException("downstream 503");
};

// 关键：retry 先声明（外层），error 后声明（内层）
ToolCallHandler handler = InterceptorChain.chainToolInterceptors(List.of(
        ToolRetryInterceptor.builder().maxRetries(2).build(),
        ToolErrorInterceptor.builder().build()), failing);

handler.call(ToolCallRequest.builder().toolName("flaky").arguments("{}").toolCallId("c1").build());

// 观察：calls == 1（期望 3）
```

把两个拦截器顺序对调（`[error, retry]`）→ `calls == 3`，符合预期。

另一条同样值得确认的路径：工具**不抛异常、只返回失败响应**时，v1.1.2.2 也不重试（`calls == 1`）；
main 的实现会重试（`calls == 3`）。

## Environment

```text
Spring AI Alibaba version(s): 1.1.2.2
Spring AI version: 1.1.2
JDK: 21 (Temurin)
OS: Windows 11
Artifact: spring-ai-alibaba-agent-framework:1.1.2.2（Maven Central sources jar，行号以此为准）
```

## Debug logs

```text
# 复现脚本未开启 debug 日志；如需要可开 ToolRetryInterceptor 的 debug：
#   log.debug("Exception {} not configured for retry, re-throwing", ...)  ← ToolRetryInterceptor.java:95
# 该分支不会被执行，因为异常在内层就被 ToolErrorInterceptor 吃掉了。
```

## Anything else?

- 我们是把 Agent 用于生产链路的使用方（Spring AI Alibaba 1.1.2.2，MCP + RAG + 状态图），
  第 9 掌踩到的正是这个坑：工具边界做了「失败转可读结果」的兜底，于是外层重试形同虚设。
  最后的处置是把重试与兜底收回同一边界（自己实现），并写了一条断言「外层重试不触发」的路标测试。
- tag 与 main 的差异（`v1.1.2.2` → main）：
  `maxRetries` → `maxAttempts`，并新增「非成功响应也抛异常重试」的分支。
  如果 main 的改法就是最终答案，**本 Issue 可以只作为「顺序契约需要写进文档」的请求**——
  因为 1.1.2.2 是当前稳定版，使用者读不到 main 的这行注释。
- 复算方式：本仓库 `scripts/locate-source.ps1`（从 Maven Central 下 `*-sources.jar` 并输出
  `模块@版本 文件:行号`），证据在 `evidence-ch14/`。
