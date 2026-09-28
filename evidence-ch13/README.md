# 第 13 掌真实验收证据

`docs/ch13-验收记录.md` 引用的原始产物。真 DeepSeek + MySQL 8 + **两个独立的知识 Agent 进程**。

| 文件 | 对应验收 | 说明 |
|------|----------|------|
| `card-8082.json` / `card-8083.json` | #1 | 两个独立进程各自的能力声明（AgentCard） |
| `instances.json` | #2 | 主服务发现的实例列表（本环境用静态表，Nacos 分支见配置注释） |
| `ask-1.json` … `ask-4.json` | #2 #3 | 四次真实跨服务调用（实例、任务号、traceId、流式片段数、耗时） |
| `calls.tsv` | #2 | 上面四次的汇总表（看轮询：8082 → 8083 → 8082 → 8083） |
| `trace-correlation.txt` | #3 | 同一条 traceId 在主服务日志与知识 Agent 日志里的对照 |
| `version-mismatch-body.txt` | #4 | 版本不兼容的真实响应（HTTP 409） |
| `run-console.log` | 全部 | 一轮验收的完整控制台输出 |
| `run-verify.ps1` | — | 验收脚本（能力声明 → 各自灌知识 → 四次调用 → 版本不兼容） |

## 复现方式

```powershell
# 1) MySQL 8（digital_human / digital_human_ext / knowledge_agent）+ 真实 DEEPSEEK_API_KEY
# 2) 两个知识 Agent 实例（注意不同的 A2A_INSTANCE_ID 与端口）
java -jar knowledge-agent\target\knowledge-agent-0.0.1-SNAPSHOT.jar --server.port=8082   # A2A_INSTANCE_ID=ka-1
java -jar knowledge-agent\target\knowledge-agent-0.0.1-SNAPSHOT.jar --server.port=8083   # A2A_INSTANCE_ID=ka-2

# 3) MCP Server 与主应用（主应用启动期要连 MCP，所以它也得在）
java -jar digital-human-mcp\target\digital-human-mcp-0.0.1-SNAPSHOT.jar
java -jar digital-human\target\digital-human-0.0.1-SNAPSHOT.jar

# 4) 一轮验收
powershell -NoProfile -ExecutionPolicy Bypass -File evidence-ch13\run-verify.ps1
```

> `.ps1` 必须 UTF-8 with BOM；传中文 JSON 一律「写 UTF-8 文件 + `curl -d @file`」。
> 本次真实验收里踩到的两个环境问题记在 `docs/ch13-验收记录.md` 第三节：
> 已应用迁移的 checksum（CRLF 归一化）与静态实例表的绑定写法。
