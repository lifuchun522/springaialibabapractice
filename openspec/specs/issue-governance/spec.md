# issue-governance Specification

## Purpose
TBD - created by archiving change github-ops-13. Update Purpose after archive.
## Requirements
### Requirement: Issue Forms 结构化入口
仓库 SHALL 提供三份 Issue Forms：缺陷报告、功能建议、文档问题。每份 MUST 为合法 YAML，
含 `name`/`description`/`title`/`labels`/`body`；每份 MUST 含一个「验收标准」必填字段；
缺陷报告 MUST 额外含业务背景、当前行为、期望行为、复现步骤、环境与版本五个必填字段。

#### Scenario: 表单结构可校验
- **WHEN** 用脚本解析 `.github/ISSUE_TEMPLATE/*.yml`
- **THEN** 三份文件均能被 YAML 解析器读出（中文 `name` 不出乱码）
- **AND** 每份都存在 `id: acceptance` 且 `validations.required: true`

#### Scenario: 标签名与标签清单一致
- **WHEN** 取出三份表单的 `labels` 字段值并逐个与 `docs/github-ops/labels.txt` 比对
- **THEN** 每个标签值都能在清单中找到同名标签
- **AND** 不存在清单里没有、表单却引用的标签（避免提交时标签不生效）

#### Scenario: 表单可真实提交
- **WHEN** 执行 `gh issue create --template .github/ISSUE_TEMPLATE/bug.yml --title '[Bug] 表单校验' --label 类型:缺陷`
- **THEN** 创建的 Issue 带上了表单声明的标签
- **AND** `gh issue view <编号>` 的正文含表单各字段标题

### Requirement: 入口路由把提问引到 Discussions
仓库 SHALL 通过 `.github/ISSUE_TEMPLATE/config.yml` 关闭空白 Issue，并把使用问题引导到 Discussions。
`blank_issues_enabled` MUST 为 `false`，`contact_links` MUST 至少含一条指向 Discussions 的入口。

#### Scenario: 空白 Issue 被关闭
- **WHEN** 检查 `.github/ISSUE_TEMPLATE/config.yml`
- **THEN** `blank_issues_enabled: false`
- **AND** `contact_links` 中存在 url 指向 `https://github.com/lifuchun522/springaialibabapractice/discussions` 的条目

### Requirement: Triage 状态机与响应承诺
仓库 SHALL 建立标签状态机：`triage`（待确认）→ `accepted`（已接受）/ `wontfix`（不处理）/ `duplicate`（重复），
并以 `status:verify` 标记「修复完成待验证」。每个新 Issue MUST 在 48 小时内被至少打上一次 `triage` 或等价状态标签。

#### Scenario: 状态标签齐备
- **WHEN** 执行 `gh label list --limit 100 --json name --jq '.[].name'`
- **THEN** 输出包含 `triage`、`accepted`、`wontfix`、`duplicate`、`status:verify`
- **AND** 每个标签都有 description 说明含义（不靠猜）

#### Scenario: 认领后能关联开发分支
- **WHEN** 对一个已打 `triage` 的 Issue 执行 `gh issue develop <编号> --name fix/<编号>-xxx --base main --checkout`
- **THEN** Issue 侧出现关联分支，本地切到该分支
- **AND** `docs/github-ops/07-issue-governance.md` 记录了这条认领流程

#### Scenario: 关闭必须给原因
- **WHEN** 执行 `gh issue close <编号> --reason 'not planned'`
- **THEN** Issue 显示为 not planned 关闭并有可见的关闭原因
- **AND** 文档要求关闭前先在评论里写清判定依据

### Requirement: Triage SOP 可交接
第 07 保 SHALL 产出 `docs/github-ops/07-issue-governance.md`，包含判重规则、字段-标签映射表、
认领与推进流程、僵尸 Issue 清理规则四节，且每节都给出可执行命令。

#### Scenario: SOP 覆盖四条链路
- **WHEN** 检查该文档的目录
- **THEN** 判重、字段映射、认领推进、僵尸清理四节齐全
- **AND** 每节至少含一条可直接执行的 `gh` 命令

#### Scenario: 基线可持续观测
- **WHEN** 执行 `gh issue list --label triage --state open`
- **THEN** 能列出所有待确认 Issue，用作「首响未达标」的观测口径
- **AND** 文档写明该命令每周至少执行一次

