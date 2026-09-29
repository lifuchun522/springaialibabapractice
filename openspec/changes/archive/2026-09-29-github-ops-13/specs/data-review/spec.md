# data-review（第 12 保：数据复盘）

## ADDED Requirements

### Requirement: Traffic 短窗口必须落盘
仓库 SHALL 提供采集脚本，把 GitHub Traffic 四类端点（views、clones、popular/referrers、popular/paths）
以及 releases、issues、pulls、contributors、actions/runs 的原始响应按月落盘到
`docs/github-ops/metrics/raw/YYYY-MM-<name>.json`。
采集脚本 MUST 显式声明 14 天窗口与 UTC 聚合口径，缺失数据 MUST 标注为「缺」而不是留空。

#### Scenario: 首次采集产出原始快照
- **WHEN** 执行 `bash scripts/gh-insights-snapshot.sh`
- **THEN** `docs/github-ops/metrics/raw/` 下出现以当月 `YYYY-MM` 为前缀的 JSON 文件
- **AND** 每个文件都是有效 JSON（可被解析器读出），且 `views`/`clones` 文件含 `count`、`uniques`、`views`/`clones` 字段

#### Scenario: 窗口口径写进产物
- **WHEN** 检查月报或采集脚本
- **THEN** 明确写出「Traffic 只保留最近 14 天，按 UTC 天聚合」
- **AND** 采集无数据时（如仓库刚公开）输出「缺」而不是空值

### Requirement: 月报口径固定可对照
仓库 SHALL 提供报告脚本，生成 `docs/github-ops/metrics/YYYY-MM.md`，内容 MUST 含
「14 天访问次数、独立访客、14 天克隆次数、独立克隆者」四项核心指标表，以及原始快照清单。

#### Scenario: 月报可生成且指标齐全
- **WHEN** 执行 `bash scripts/gh-insights-report.sh`
- **THEN** 生成的 Markdown 含四项核心指标行，且数值来自 `gh api` 真实返回
- **AND** 「原始快照」小节列出了本次采集到的全部 JSON 文件名

#### Scenario: 口径跨月一致
- **WHEN** 对比相邻两个月的月报
- **THEN** 表头与指标名称完全一致（口径不随月份变化）
- **AND** 如果某项指标本期缺失，月报注明原因而不是删掉该行

### Requirement: 数据只作判断输入
第 12 保 SHALL 在文档中明确：指标用于判断下一步投向，MUST NOT 作为考核目标；
每次复盘 MUST 产出一条明确动作（改哪份文档、投哪个方向、砍哪项长期为零的工作）。

#### Scenario: 复盘有动作
- **WHEN** 检查 `docs/github-ops/12-data-review.md`
- **THEN** 含「指标 → 判断 → 动作」的对照结构
- **AND** 明确写出「长期为零的指标要砍，而不是继续维护」

#### Scenario: 采集不写入远端
- **WHEN** 检查采集脚本
- **THEN** 脚本只包含 `gh api` 的 GET 请求与本地写文件
- **AND** 不包含任何写入 GitHub 状态的操作
