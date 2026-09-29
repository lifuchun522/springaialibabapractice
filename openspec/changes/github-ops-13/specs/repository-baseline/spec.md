# repository-baseline（第 01 保：仓库建制）

## ADDED Requirements

### Requirement: About 三件套与 Topics 关键词覆盖
仓库 SHALL 通过脚本或 `gh` 命令一次性配置 description、homepageUrl 与 repositoryTopics，且配置结果可被 `gh repo view --json` 读回。
description MUST 同时含「Spring AI Alibaba」「中文实战」「数字人示例」三个语义要素；repositoryTopics MUST 为小写英文连字符形式，
数量不少于 6 个，并覆盖 `spring-ai-alibaba`、`java`、`agent`、`rag`、`mcp`、`digital-human`；Topics MUST NOT 出现中文或截断词项。

#### Scenario: 读回基线快照
- **WHEN** 执行 `gh repo view lifuchun522/springaialibabapractice --json name,description,homepageUrl,repositoryTopics,visibility,defaultBranchRef`
- **THEN** 输出 JSON 中 description、homepageUrl、repositoryTopics、visibility、defaultBranchRef 五项均非空
- **AND** repositoryTopics 含上述 6 个英文标签，且无任一标签含中文字符或形如 `spring-ai-ali` 的截断项

#### Scenario: 幂等重跑
- **WHEN** 连续两次执行基线脚本
- **THEN** 第二次执行不报错，且 `gh repo view --json repositoryTopics` 的输出与第一次完全一致（不产生重复标签）

#### Scenario: Topics 触碰红线时被拦下
- **WHEN** 有人试图把中文写入 Topics
- **THEN** 基线校验脚本以非零退出码结束，并打印命中的非法标签名

### Requirement: Features 开关按运营能力取舍
仓库 SHALL 只保留有实际运营动作的能力开关：Issues 保持开启，Wiki 与 Projects MUST 关闭，Discussions 仅在确定运营节奏后开启。
关闭动作 MUST 可被 `gh repo view --json` 的 `hasWikiEnabled` / `hasProjectsEnabled` 字段读回验证。

#### Scenario: 关闭无运营的能力
- **WHEN** 执行 `gh repo edit --disable-wiki --disable-projects`
- **THEN** `gh repo view --json hasWikiEnabled,hasProjectsEnabled` 返回两个 `false`
- **AND** `hasIssuesEnabled` 仍为 `true`

#### Scenario: README 首屏五段齐全
- **WHEN** 检查仓库根 `README.md`
- **THEN** 首屏存在「项目定位」「快速开始」「掌次路线」「技术栈」「参与贡献」五段结构
- **AND** 每段都有可点击的内链或明确的文件路径，不存在「待补充」占位

### Requirement: 基线快照落盘可审计
第 01 保 SHALL 产出 `docs/github-ops/01-repo-baseline.md` 与 `docs/github-ops/01-repo-baseline.json` 两份证据，
且 JSON MUST 为 `gh repo view --json` 的真实输出，不得手工编写。文档 MUST 含改前状态、改后状态、Web 配置路径与命令输出四节。

#### Scenario: 快照字段非空率 100%
- **WHEN** 解析 `docs/github-ops/01-repo-baseline.json`
- **THEN** 约定的 6 个字段非空率为 100%
- **AND** 文件中不存在占位符文本（如 `<待填>`、`xxx`、`TODO`）

#### Scenario: 换人十分钟内复现
- **WHEN** 第二名维护者按 `01-repo-baseline.md` 从干净克隆执行脚本
- **THEN** 脚本在 10 分钟内执行完毕且不报错，得到的 JSON 与仓库内快照除时间无关字段外一致
