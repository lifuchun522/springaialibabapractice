# 第 12 掌真实验收证据

`docs/ch12-验收记录.md` 引用的原始产物（真 DeepSeek + MySQL + MCP Server，一轮跑完）。

| 文件 | 对应验收 | 说明 |
|------|----------|------|
| `run-acc1-reception-to-knowledge.json` | #1 #2 | 知识类问题：`route=knowledge`，记忆只有 knowledge 增长 |
| `run-acc2-business.json` | #1 #2 | 业务类问题：走业务角色，缺参数时诚实追问 |
| `run-acc3-memory-isolation.json` | #2 | 同一会话再问知识问题：业务记忆增长到 4，接待仍为 0 |
| `run-acc4-reception-hop.json` | #1 | 寒暄：`route=reception`，接待记忆 2 条 |
| `run-acc5-knowledge-in-same-session.json` | #1 #2 | 同一会话内 `reception → knowledge` 流转，接待记忆不被污染 |
| `run-console.log` | 全部 | 上面五条的完整控制台输出 |

## 复现方式

```powershell
# 1) MySQL 8（含 digital_human / digital_human_ext）+ 真实 DEEPSEEK_API_KEY
# 2) 起 MCP Server 与主应用
java -jar digital-human-mcp\target\digital-human-mcp-0.0.1-SNAPSHOT.jar
java -jar digital-human\target\digital-human-0.0.1-SNAPSHOT.jar

# 3) 一轮跑完五条
powershell -NoProfile -ExecutionPolicy Bypass -File evidence-ch12\run-verify.ps1
```

> `.ps1` 必须以 UTF-8 with BOM 保存；传中文 JSON 走「写 UTF-8 文件 + `curl -d @file`」。
