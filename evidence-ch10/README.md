# 第 10 掌真实验收证据

`docs/ch10-验收记录.md` 引用的**原始产物**：真 DeepSeek + MySQL 8 + MCP Server 跑出来的响应 JSON、
路由命中原始表格，以及踩坑前后的对照。文件内容没有手工润色。

| 文件 | 对应验收 | 说明 |
|------|----------|------|
| `runB-acc1-sequences.txt` / `runD-acc1-sequences-with-real-timings.txt` | 验收 1 | 顺序模式同一问题 20 次，每次的节点序列（20 行完全相同） |
| `runB-acc1-sample-run.json` | 验收 1 | 其中一次的完整响应（reply / nodes / sequence） |
| `runA-BEFORE-FIX-parallel-merge-empty.json` | 验收 2 | **修复前**的并行结果：分支各有产出，但归并节点看不到它们（模型回答「未收到上游两条分支的具体内容」） |
| `runB-acc2-parallel.json` / `runD-acc2-parallel.json` | 验收 2 | **修复后**：三个键都在，回答同时引用知识库与业务系统，并带各节点真实耗时 |
| `runB-acc3-routing.tsv` | 验收 3 | 20 组真实样本（第一版口径，17/20） |
| `runC-routing.tsv` | 验收 3 | 澄清口径后（同一批答案，按修正标注读 = 20/20） |
| `runD-routing.tsv` / `runE-routing.tsv` | 验收 3 | **未固定温度**的两轮：与 runC 相比有 1 条样本翻面（分类方差） |
| `runF-routing.tsv` / `runG-routing.tsv` | 验收 3 | **固定 route-temperature: 0** 的两轮：20 条判定完全一致 |
| `runF-routing.log` / `runG-routing.log` | 验收 3 | 上面两轮的完整控制台输出（逐条 期望/实际/是否命中） |
| `runD-acc4-trace.json` | 验收 4 | 节点名与真实耗时（聚合视图 + 序列视图） |
| `runD-acc5-loop.json` | 循环 | 补信息循环：`check-completeness` / `ask-back` 各 2 次 = 两轮 |

## 复现方式

```powershell
# 1) MySQL 8（含 digital_human 与 digital_human_ext）+ 真实 DEEPSEEK_API_KEY
# 2) 起 MCP Server 与主应用
java -jar digital-human-mcp\target\digital-human-mcp-0.0.1-SNAPSHOT.jar
java -jar digital-human\target\digital-human-0.0.1-SNAPSHOT.jar

# 3) 四条验收（自建用户、项目、知识库）
powershell -NoProfile -ExecutionPolicy Bypass -File evidence-ch10\run-verify.ps1 -tag runX

# 4) 路由命中率专项（20 组样本 + 原始 TSV）
powershell -NoProfile -ExecutionPolicy Bypass -File evidence-ch10\run-routing.ps1 -tag runY
```

> `.ps1` 脚本必须以 **UTF-8 with BOM** 保存（PowerShell 5.1 读无 BOM 的 UTF-8 会把中文解析坏）；
> 传中文 JSON 一律走「写 UTF-8 文件 + `curl -d @file`」，否则会被转成 GBK 导致 400。
