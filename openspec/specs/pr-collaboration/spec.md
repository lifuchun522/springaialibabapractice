# pr-collaboration Specification

## Purpose
TBD - created by archiving change github-ops-13. Update Purpose after archive.
## Requirements
### Requirement: PR 模板含取舍与回滚
仓库 SHALL 提供中文 PR 模板，正文 MUST 含以下小节：背景、改动、架构取舍、测试证据、兼容性、截图、回滚、Checklist。
Checklist MUST 至少覆盖：已关联 Issue、已贴出本地命令输出、已说明兼容性、已说明回滚路径。

#### Scenario: 模板被自动带出
- **WHEN** 用 `gh pr create --web` 或 `gh pr create --body-file .github/pull_request_template.md` 新建 PR
- **THEN** PR 正文包含上述全部小节标题
- **AND** 模板中的 `<!-- 注释 -->` 提示不会阻断创建

#### Scenario: 空证据的 PR 不可合并
- **WHEN** 检查已合并的 PR
- **THEN** 每个 PR 正文的「测试证据」小节都有具体命令与输出（不是「我本地试过了」）
- **AND** 「回滚」小节给出了可执行命令或明确说明无需回滚的理由

### Requirement: Draft → Ready → Checks → Squash 闭环
仓库 SHALL 以 `gh pr create --draft` 起草、`gh pr ready` 转正、`gh pr checks --watch` 等待门禁、
`gh pr merge --squash --delete-branch` 合并，并保留 `gh pr revert <编号>` 的回滚路径。
合并策略 MUST 为 squash，合并后 MUST 删除源分支。

#### Scenario: 门禁未过不能合
- **WHEN** PR 的 status check 为 failing 或 pending
- **THEN** `gh pr merge --squash` 被拒，或 `gh pr view --json mergeStateStatus` 显示为 `BLOCKED`
- **AND** 门禁通过后 `gh pr checks --watch` 输出全部 successful

#### Scenario: 合并后可回滚
- **WHEN** 对已合并 PR 执行 `gh pr revert <编号>` 或 `git revert <merge-sha>`
- **THEN** 生成一个反向提交且不重写历史
- **AND** `main` 上出现该 revert 提交，原提交仍在历史中

#### Scenario: main 保持随时可发布
- **WHEN** 任意一次合并后执行 `gh pr view <编号> --json mergeable,mergeStateStatus,reviewDecision,statusCheckRollup`
- **THEN** 该 PR 的 `statusCheckRollup` 全部为成功态
- **AND** `git log --oneline -3 origin/main` 显示历史线性、无未完成合并

