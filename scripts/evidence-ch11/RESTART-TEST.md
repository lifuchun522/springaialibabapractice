# 第 11 掌验收第 2、3、4 条：中断 → 重启进程 → 恢复

这一条**没法用一个脚本跑完**，因为它的核心动作是「把应用进程杀掉再起来」——
脚本杀不掉自己所在的进程。所以按步骤手工执行，每一步的输出都落到本目录：

```powershell
# 0) 前提：MySQL 8（含 digital_human / digital_human_ext）+ 真实 DEEPSEEK_API_KEY；
#    MCP Server 与主应用都起来
java -jar digital-human-mcp\target\digital-human-mcp-0.0.1-SNAPSHOT.jar
java -jar digital-human\target\digital-human-0.0.1-SNAPSHOT.jar
```

```powershell
# 1) 第一次进入：高风险（退款 / 送修）应在人工确认前中断
.\run-1-run.ps1          # → 01-run.json（status=INTERRUPTED，记下 threadId）

# 2) 「这条工单现在卡在哪一步」：直接读检查点
.\run-2-state.ps1 -ThreadId <上一步的 threadId>   # → 02-state-before-restart.json

# 3) 杀掉应用进程（模拟发版 / 进程崩溃），确认进程真的没了
#    Stop-Process -Id <pid> -Force ；Get-NetTCPConnection -LocalPort 8080 应为空

# 4) 重新启动应用（同一份数据库）

# 5) 拿**同一个 threadId** 恢复：应该从 humanReview 继续，前面的节点不重跑
.\run-3-resume.ps1 -ThreadId <同一个 threadId>    # → 03-resume-after-restart.json

# 6) 复查检查点历史：新增了 humanReview / reply 两条，nextNodeId 走到 __END__
.\run-2-state.ps1 -ThreadId <同一个 threadId>     # → 04-state-after-restart.json
```

要证明的三件事与对应的观察点：

| 验收 | 观察点 |
|------|--------|
| 可中断（第二条） | 第一次调用返回 `status=INTERRUPTED`、`executedNodes` 里没有 `reply`，且**这次调用是马上返回的**（没有阻塞等人工） |
| 可恢复（第三条） | 恢复调用的 `executedNodes` **只有 `humanReview` 与 `reply`**——`intent`/`knowledge`/`biz`/`risk` 没有重跑 |
| 状态可持久化（第四条） | 恢复发生在一个**全新的进程**里：内存 saver 这时是空的，能恢复就说明状态确实从 MySQL 读回来了；对照证据是 `02-state-before-restart.json`（重启前）与 `04-state-after-restart.json`（重启后）里的检查点序列 |
