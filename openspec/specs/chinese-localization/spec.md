# chinese-localization Specification

## Purpose
TBD - created by archiving change github-ops-13. Update Purpose after archive.
## Requirements
### Requirement: 只中文化能翻的部分
仓库 SHALL 中文化「内容层」与「约定层的值」，MUST NOT 翻译平台层的键名。
具体地：README / CONTRIBUTING / SECURITY 等正文用中文；Issue Form 的 `name`、`description`、`title`、`labels` 值与
选项文本用中文；而 Issue Form 的 YAML 字段名（`type`/`id`/`attributes`/`validations`）、Topics、
Actions 关键字（`on`/`jobs`/`permissions`）、API 字段名 MUST 保持英文。

#### Scenario: YAML 键名未被翻译
- **WHEN** 逐份检查 `.github/ISSUE_TEMPLATE/*.yml`
- **THEN** 文件中的键名集合仅含 `name`/`description`/`title`/`labels`/`body`/`type`/`id`/`attributes`/`validations`/`label`/`options`/`required`/`placeholder`/`value`
- **AND** 不出现任何中文键名（如 `名称:`、`属性:`）

#### Scenario: Topics 未被翻译
- **WHEN** 执行 `gh repo view --json repositoryTopics`
- **THEN** 所有标签名匹配 `^[a-z0-9-]+$`，无中文字符

#### Scenario: LICENSE 与平台层保持原样
- **WHEN** 检查 `LICENSE` 与 `.github/workflows/*.yml`
- **THEN** LICENSE 文本未被翻译
- **AND** 工作流的 `on`、`permissions`、`uses`、`run` 等键名未被翻译

### Requirement: 中文标签体系幂等可同步
仓库 SHALL 提供 `docs/github-ops/labels.txt` 作为标签清单真源（`名称|颜色|说明` 三列），
并提供 `scripts/gh-labels.sh` 以 `gh label create --force` 幂等同步。
清单 MUST 不少于 14 条，覆盖类型、优先级、状态、模块、难度五个维度；
脚本 MUST NOT 重命名或删除 GitHub 官方默认标签 `good first issue`、`help wanted`。

#### Scenario: 首次同步全部创建
- **WHEN** 在只有官方默认标签的仓库上执行 `scripts/gh-labels.sh`
- **THEN** `gh label list --limit 100` 中能查到清单里的全部标签，且颜色与说明与清单一致
- **AND** 输出末尾仍能看到 `good first issue` 与 `help wanted`

#### Scenario: 重复执行不报错
- **WHEN** 连续两次执行 `scripts/gh-labels.sh`
- **THEN** 第二次退出码为 0，标签总数与第一次相同（不产生重名或重复标签）

#### Scenario: 全角冒号自检
- **WHEN** 执行 `gh label list --limit 100 --json name --jq '.[].name'` 并按全角冒号过滤
- **THEN** 命中的标签名与 Issue Form `labels` 字段值完全一致
- **AND** 不存在两个仅大小写或半/全角不同的近似标签

### Requirement: 社区文件中文且英文优先声明到位
仓库 SHALL 提供中文 `CONTRIBUTING.md`、`SECURITY.md`、中文 PR 模板与中文 Issue Forms；
涉及行为准则、安全政策时 MUST 注明「英文版本优先」，避免中文译文与官方口径冲突。

#### Scenario: 贡献指南给出可执行前置
- **WHEN** 阅读 `CONTRIBUTING.md`
- **THEN** 其中列出「报告缺陷」「提交文档修正」「认领入门任务」三条路径
- **AND** 提交前要求包含「查词汇表」「跑标签同步脚本」「勾 PR 自检清单」三步

#### Scenario: 安全政策划清范围
- **WHEN** 阅读 `SECURITY.md`
- **THEN** 明确支持范围为默认分支最新状态，`chapter/**` 历史分支不再回溯修复
- **AND** 明确要求走私密漏洞报告入口，并给出响应时限（确认 ≤3 工作日、初判 ≤7 工作日）

#### Scenario: 用中文标签真实创建一个 Issue 并验证
- **WHEN** 用 `gh issue create --label "类型:缺陷" --label "优先级:中"` 创建一个测试 Issue
- **THEN** `gh issue view <编号> --json labels` 返回上述两个中文标签
- **AND** 该标签与 `labels.txt` 中的名称逐字符一致

