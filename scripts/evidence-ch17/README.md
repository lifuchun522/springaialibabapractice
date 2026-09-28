# 第 17 掌 验收证据（观星治理 · 全链路诊断）

本目录是**第 17 掌的可核对证据**：脚本可重跑，原始输出留档。
本掌的结论只有一句：**一条失败请求要能定位到模型、工具、RAG、图节点或远程 Agent 五层中的某一层**。

## 脚本与产物

| 脚本 | 验证什么 | 原始输出 | 判据 |
| --- | --- | --- | --- |
| `01-real-run.ps1` | 真实启动一次并跑三类请求（正常/早失败/RAG） | `01-app-run.log`、`02~04-*`、`05-*` | 三类请求都能按 traceId 取回调用树 |
| `02-offline-verify.ps1` | 离线门禁（身份传播、失败分类、调用树、契约） | `11-offline-verify.txt` | 全绿，且新增观测用例 25 条 |

## 这批证据里的四件事

| 文件 | 说明了什么 |
| --- | --- |
| `05-call-trees.md` | 三种场景的调用树：`http → model → tool`（正常）、`http[INPUT_INVALID]`（早失败）、`http → rag` |
| `07-log-identity.txt` | 同一请求的两行日志身份四元组**完全一致**，且一行来自 Tomcat 线程、一行来自工具执行池线程 |
| `08-tool-audit-trace.txt` | `tool_call_audit.trace_id` 与响应头/日志里的 traceId **是同一个号**（修掉了两套号并存） |
| `09-metric-failure-tag.txt` | Prometheus 里能按 `failure_type` 聚合（`none` 与 `INPUT_INVALID` 同时存在，没有 meter 被丢弃） |
| `10-cross-process-traceparent.txt` | 调用方的 W3C `traceparent` 被正确接续，服务端返回同一个号 |

## 顺序与前置

- 需要 MySQL（`dh-mysql:33079`）、MCP Server（`dh-quick-mcp:8081`）与真实 `DEEPSEEK_API_KEY`（可从机器级环境变量读取）。
- 诊断接口需要登录令牌：脚本会自己注册/登录一个观测账号。
- **Windows 上 curl 传中文 JSON 必须走文件**（`--data-binary @file`），
  直接内联会把引号弄坏，服务端得到的是 400——本掌实测踩到过。
- `02-offline-verify.ps1` 会 `clean target/`：**先跑它，再跑 `01-real-run.ps1`**，或先停掉进程，
  否则 clean 会因为 jar 被占用而失败。
