# 第 11 掌真实验收证据

`docs/ch11-验收记录.md` 引用的**原始产物**。本掌的核心证据跨了一次进程边界
（中断 → 杀进程 → 重启 → 恢复），所以文件按执行的先后顺序编号。

| 文件 | 对应验收 | 说明 |
|------|----------|------|
| `01-run.json` | 验收 2 可中断 | 高风险退款请求：`status=INTERRUPTED`、`executedNodes=intent→biz→risk`、`reply` 为空 |
| `02-before-restart-state.json` | 验收 4 | 重启**前**的检查点历史：4 条，最后一条 `next=humanReview`（卡在这儿） |
| `05-db-checkpoints-before-restart.txt` | 验收 4 | 同上的数据库直查（`graph_checkpoint`，按自增 `seq` 排序） |
| `03-after-restart-resume.json` | 验收 3 可恢复 | **杀进程并重启之后**用同一个 threadId 恢复：`status=DONE`、`executedNodes=humanReview→reply` |
| `04-after-restart-state.json` | 验收 4 | 恢复**后**的检查点历史：6 条，`humanReview→reply→__END__` |
| `06-db-checkpoints-after-restart.txt` | 验收 4 | 同上的数据库直查 |
| `app-before-restart.log` / `app-after-restart.log` | 全部 | 重启前后两个进程的启动日志（Flyway V6 迁移、MCP 工具发现都在里面） |
| `app-final.log` | 附加 | 最终形态（业务记录改走 messages 之后）的启动日志 |
| `mcp.log` | — | MCP Server 日志 |
| `RESTART-TEST.md` | 验收 2/3/4 | 手工步骤与观察点（这一步没法用一个脚本跑完，原因写在里面） |
| `run-graph-acceptance.ps1` | — | 三个动作的脚本：发起、查状态、恢复（`-Run` / `-State` / `-Resume`） |

## 复现方式

```powershell
# 1) MySQL 8（含 digital_human 与 digital_human_ext）+ 真实 DEEPSEEK_API_KEY
# 2) 起 MCP Server 与主应用（首次启动会自动跑 Flyway V6 建检查点表）
java -jar digital-human-mcp\target\digital-human-mcp-0.0.1-SNAPSHOT.jar
java -jar digital-human\target\digital-human-0.0.1-SNAPSHOT.jar

# 3) 高风险请求 → 应中断
powershell -NoProfile -ExecutionPolicy Bypass -File evidence-ch11\run-graph-acceptance.ps1 -Run -tag 01

# 4) 查断点
powershell -NoProfile -ExecutionPolicy Bypass -File evidence-ch11\run-graph-acceptance.ps1 -State -ThreadId <threadId> -tag 02-before-restart

# 5) 杀掉应用进程，重新启动（关键一步：内存 saver 随进程消失）

# 6) 同一个 threadId 恢复 → 应只执行 humanReview → reply
powershell -NoProfile -ExecutionPolicy Bypass -File evidence-ch11\run-graph-acceptance.ps1 -Resume -ThreadId <threadId> -tag 03-after-restart
```

> `.ps1` 脚本必须以 **UTF-8 with BOM** 保存（PowerShell 5.1 读无 BOM 的 UTF-8 会把中文解析坏）；
> 传中文 JSON 一律走「写 UTF-8 文件 + `curl -d @file`」，否则会被转成 GBK 导致 400。
