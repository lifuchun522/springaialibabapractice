# 第 9 掌真实验收证据

这里是 `docs/ch09-验收记录.md` 引用的**原始产物**：真 DeepSeek + MySQL 8 + MCP Server 跑出来的响应 JSON，
以及从 `tool_call_audit` 表导出的审计行。文件内容没有手工润色，文档里的引用都能在这里对上。

| 文件 | 对应验收 | 说明 |
|------|----------|------|
| `runA-acc1-event-sequence.json` | 验收 1 | 模型自己决定调 `knowledge_search`，事件序列完整 |
| `runA-acc2-session-stats.json` | 验收 1 | 同一会话里连调两次 `session_stats`，证明 Agent 路径也在写账本 |
| `acc2-model-call-limit.json` | 验收 2 | 诱导循环的真实模型回答（它拒绝了，因此上界靠离线用例证明） |
| `runA-acc3-remote-tool.json` | 验收 3 | MCP 健康时的远程工具调用 |
| `acc3-healthy-result.json` | 验收 3 | 断连前的健康基线 |
| `acc3-mcp-down-result.json` | 验收 3 | 杀掉 MCP Server 后：模型试了 3 次，每次都重试 3 遍 |
| `acc3-mcp-down-final-result.json` | 验收 3 | 补上根因留痕后的同一场景（`根因 ClosedChannelException`） |
| `runA-acc4-single-string.json` | 验收 4 | 零工具调用也走同一条路径，`reply` 仍是字符串 |
| `audit-trace-f95d5f5e21cc.txt` | 验收 3 | 9 行 ERROR + 1 行 `knowledge_search` OK（Agent 的自救路径） |
| `audit-trace-77d5af760850.txt` | 验收 3 | 6 行 ERROR = 2 次逻辑调用 × 3 次尝试 |

## 复现方式

```powershell
# 1) 需要 MySQL 8（含 digital_human 与 digital_human_ext 两个库）与一个真实 DEEPSEEK_API_KEY
# 2) MCP Server 先起（它有自己的库）
java -jar digital-human-mcp\target\digital-human-mcp-0.0.1-SNAPSHOT.jar

# 3) 应用连上去（启动日志应出现「MCP 远程工具已发现 1 个」）
java -jar digital-human\target\digital-human-0.0.1-SNAPSHOT.jar

# 4) 跑验收 1/2/4（脚本自己注册用户、建项目、灌知识）
powershell -NoProfile -ExecutionPolicy Bypass -File evidence-ch09\run-verify.ps1 -tag runA

# 5) 跑验收 3：先把 MCP Server 起在 8082 并让应用连它，调用一次（健康基线），
#    再杀掉 8082，调用第二次（故障）：两次的输出与审计行就是上面那几张表
powershell -NoProfile -ExecutionPolicy Bypass -File evidence-ch09\run-tool-failure.ps1 -tag acc3-healthy
powershell -NoProfile -ExecutionPolicy Bypass -File evidence-ch09\run-tool-failure.ps1 -tag acc3-mcp-down
```

审计行查询（`--default-character-set=utf8mb4` 是为了中文不乱码）：

```console
$ docker exec dh-mysql mysql --default-character-set=utf8mb4 -uroot -proot -e \
    "select id, tool_name, status, elapsed_ms, result_summary from digital_human.tool_call_audit \
     where trace_id='77d5af760850' order by id;"
```

> `.ps1` 脚本必须以 **UTF-8 with BOM** 保存：PowerShell 5.1 读无 BOM 的 UTF-8 会把中文解析坏。
> 通过 `curl -d '{...}'` 传中文 JSON 会被转成 GBK 导致 400，所以脚本统一「写 UTF-8 文件 + `-d @file`」。
