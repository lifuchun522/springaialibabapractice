# open-source-operations Specification

## Purpose
TBD - created by archiving change github-ops-13. Update Purpose after archive.
## Requirements
### Requirement: 运营漏斗被写成可执行 SOP
第 13 保 SHALL 产出 `docs/github-ops/13-open-source-operations.md`，把
`访问 → README 理解 → Clone/运行 → Discussion → Issue → PR → Contributor` 七级漏斗逐级写出：
每一级 MUST 给出取数方式（具体 `gh` 命令或界面路径），MUST 给出该级「留不住人」的典型症状与对应动作。

#### Scenario: 每一级都有取数命令
- **WHEN** 检查该文档的漏斗章节
- **THEN** 七级中的每一级都至少对应一条可执行命令（`gh api` / `gh issue list` / `gh pr list` / `git shortlog` 等）
- **AND** 没有一级只写「看 Insights」而不给具体端点

#### Scenario: 节奏与承诺落到日历
- **WHEN** 检查文档的每月节奏章节
- **THEN** 含「每月 1 号导出上月指标」「每月 5 号前准备 3 个带验收标准的小任务」「每次 Release 发公告」三条
- **AND** 含响应承诺：新 Issue 首次回复 ≤48 小时、新 PR 首次评审 ≤72 小时

#### Scenario: 首次响应模板不越权承诺
- **WHEN** 检查文档的首次响应模板
- **THEN** 模板含「欢迎与感谢」「明确下一步」「可执行的验证命令」三要素
- **AND** 明确禁止承诺无法兑现的时间点

### Requirement: 运营指标可归档
仓库 SHALL 提供 `scripts/gh-ops-metrics.sh`，按日期戳归档 views、clones、`good first issue` 开放列表与
releases 列表四类数据到 `docs/github-ops/metrics/`；并 SHALL 提供 `.github/workflows/gh-ops-weekly.yml`
按周自动执行并提交草稿。自动提交 MUST 走 PR 或允许在无改动时跳过，MUST NOT 因无改动而红灯。

#### Scenario: 本地归档可跑通
- **WHEN** 执行 `bash scripts/gh-ops-metrics.sh`
- **THEN** `docs/github-ops/metrics/` 下出现 `YYYY-MM-DD-views.json`、`-clones.json`、`-gfi.json`、`-releases.json` 四类文件
- **AND** 命令输出打印写入路径

#### Scenario: 周任务无改动不报错
- **WHEN** `.github/workflows/gh-ops-weekly.yml` 在无新增数据时运行
- **THEN** `git commit` 步骤通过 `|| echo 'no changes'` 兜底，运行结果为成功
- **AND** 工作流声明了 `permissions: contents: write` 与最小必要权限，且配置 `workflow_dispatch` 便于手动触发

#### Scenario: 指标不作为考核
- **WHEN** 检查第 13 保文档
- **THEN** 明确写出「指标只作为判断输入，不作为考核目标」
- **AND** 明确写出「只追 Star 的代价是维护者被假任务和零响应拖垮」

### Requirement: 真实任务池可被外部认领
仓库 SHALL 维护至少 3 个带验收标准的 `good first issue` 开放任务，每个任务 MUST 在正文写明
「要做什么」「验收标准」「改动范围（哪些文件/模块）」。

#### Scenario: 任务池非空
- **WHEN** 执行 `gh issue list --label 'good first issue' --state open --json number,title`
- **THEN** 返回条目数 ≥ 3

#### Scenario: 每个任务可直接开工
- **WHEN** 逐个阅读任务正文
- **THEN** 每个都含「验收标准」小节，且标准是可判定的（能跑命令或能看产物）
- **AND** 每个都指明了预期改动的文件或目录

#### Scenario: 外部传播回链一致
- **WHEN** 检查文档的外部传播回链清单
- **THEN** 要求外部文章（腾讯云社区/CSDN/公众号）正文末尾统一回链仓库
- **AND** 要求核心知识回流到 `docs/`，外部文章只作入口

