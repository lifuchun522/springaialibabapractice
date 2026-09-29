# rules-permissions（第 05 保：规则权限）

## ADDED Requirements

### Requirement: main 分支最小规则
仓库 SHALL 为默认分支配置 active 状态的 branch ruleset，至少包含：禁止删除、禁止非快进推送（禁强推）、
要求通过拉取请求合并、要求状态检查通过。
规则 MUST NOT 强制单人仓库的 code owner review（避免自锁），bypass 名单 MUST 只含 admin 角色。

#### Scenario: 强推 main 被拒
- **WHEN** 在本地对 `main` 执行 `git push --force origin main`
- **THEN** 远端拒绝该推送，且本地能看到 ruleset 相关错误信息
- **AND** `gh api repos/{owner}/{repo}/branches/main/protection` 或 ruleset 导出中能看到禁强推的规则项

#### Scenario: 直推 main 计数为零
- **WHEN** 检查规则生效后的 `main` 提交历史
- **THEN** 直接推送产生的提交数为 0，所有改动均经 PR 合并
- **AND** 每条第 05 保之后的提交都能对应到一个已合并 PR

### Requirement: tag 保护不可移动
仓库 SHALL 为 tag 配置 ruleset，命中 `ch*` 与 `v*` 的 tag MUST 不可删除、不可移动（禁更新）。

#### Scenario: 删除受保护 tag 被拒
- **WHEN** 执行 `git push origin :refs/tags/ch18`（或对 `v*` 标签执行同样操作）
- **THEN** 远端拒绝删除
- **AND** `gh api repos/{owner}/{repo}/rulesets` 中该 tag ruleset 的 `enforcement` 为 `active`

#### Scenario: 教程 tag 与软件版本 tag 分离
- **WHEN** 检查 `docs/github-ops/05-rules-permissions.md`
- **THEN** 文档明确 `ch01`–`ch18` 为教程标签、`v*` 为软件 SemVer，二者语义分离且都被保护

### Requirement: CODEOWNERS 只卡关键路径
仓库 SHALL 提供 `.github/CODEOWNERS`，仅对 `.github/`、`scripts/`、`docs/github-ops/` 等治理关键路径声明 owner，
MUST NOT 对全仓库所有路径强制审批。

#### Scenario: CODEOWNERS 路径可达
- **WHEN** 执行 `gh api repos/{owner}/{repo}/contents/.github/CODEOWNERS --jq .path`
- **THEN** 返回 `.github/CODEOWNERS`
- **AND** 文件内每条规则的模式都能匹配到仓库中真实存在的路径

#### Scenario: owner 权限满足要求
- **WHEN** 检查 CODEOWNERS 中被声明的用户
- **THEN** 该用户对仓库具备 write 及以上权限（CODEOWNERS 生效前提）
- **AND** 文档记录了这条前置条件

### Requirement: 规则配置可导出审计
第 05 保 SHALL 把 rulesets 列表与每个 ruleset 详情导出为 JSON 快照，落在 `docs/github-ops/evidence/`。
规则变更时 MUST 同步更新快照，否则审计断链。

#### Scenario: 快照存在且含关键字段
- **WHEN** 列出 `docs/github-ops/evidence/ruleset-*.json`
- **THEN** 至少存在分支与 tag 两个 ruleset 的详情快照
- **AND** 每份快照含 `id`、`name`、`target`、`enforcement`、`conditions`、`rules` 字段

#### Scenario: 规则可自检
- **WHEN** 执行 `gh ruleset check main`
- **THEN** 输出显示 `main` 命中分支 ruleset
- **AND** 文档给出的检查命令可被第二人复现
