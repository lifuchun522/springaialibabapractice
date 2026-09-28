# 第 16 掌 验收证据（履霜冰至 · 立派服务）

本目录是**第 16 掌的可核对证据**：脚本可重跑，原始输出留档。
本掌的结论只有一句——**「跑得通」和「能交付」之间隔着的全是边界**，而边界必须能被机器验证。

## 脚本与产物

| 脚本 | 验证什么 | 原始输出 | 判据 |
| --- | --- | --- | --- |
| `01-fail-fast.ps1` | 配置缺失必须在**启动期**失败 | `01-startup-fail-fast.txt` | 进程起不来，且错误信息点名 `DEEPSEEK_API_KEY` 与声明位置 |
| `02-real-run.ps1` | 可自检 + 流式契约（心跳） | `02-app-run.log`、`03-health*.json`、`04-sse-*.txt` | 健康分组可区分「进程活着」与「配置齐了」；静默期有 `:heartbeat` 帧；数据帧逐段落 |
| `03-compose-contract.ps1` | 部署清单声明必需项 | `06-compose-required-env.txt` | 缺变量时 `docker compose config` 直接报错并给出提示，而不是把缺失带进容器 |
| `04-offline-verify.ps1` | 分层门禁 | `07-offline-verify.txt` | 三个模块 `BUILD SUCCESS`，ArchUnit 依赖方向 6 条规则全绿 |

## 顺序提醒

`01`/`02` 会启动真实进程，`04` 里的 `clean` 会删 `target/` 并**因为 jar 被占用而失败**（本掌实测踩到一次）。
顺序是：**04（纯离线）→ 01 → 02 → 03**，或在 02 之后先停进程。

## 这批证据里的三条真实结论

1. **缺密钥启动即失败**，错误信息来自我们自己的部署契约（不是框架的 `OpenAI API key must be set`）——
   靠 `ApplicationContextInitializer` 把校验放在 Bean 创建之前才拿得到这个信息所有权。
2. **健康检查能区分两件事**：`/actuator/health/liveness` 只有 `ping`，`/actuator/health/readiness`
   含 `configReadiness + db + diskSpace`；`configReadiness` 的 details 逐项写出「必需项 present / 可选项 set」。
3. **心跳真的在静默期发帧**：`04-sse-heartbeat-trace.txt` 里 `:heartbeat` 每隔 ~2s 出现一次
   （该次运行把间隔临时改成 `2s` 以便在几十秒内看清；生产 profile 写的是 `15s`），
   随后数据帧才开始下发——这正是「模型在思考时链路不会把连接判成死连接」的证据。
