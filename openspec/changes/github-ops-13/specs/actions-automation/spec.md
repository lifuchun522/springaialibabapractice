# actions-automation（第 08 保：Actions 自动化）

## ADDED Requirements

### Requirement: CI 在 PR 与 main 双触发且最小权限
`.github/workflows/ci.yml` SHALL 在 `pull_request`（目标 `main`）与 `push`（`main` 及 `chapter/**`）上触发，
顶层 MUST 声明 `permissions: contents: read`，并 MUST 配置 `concurrency` 且 `cancel-in-progress: true` 以避免重复跑。
CI MUST NOT 依赖任何密钥即可完成编译与单测。

#### Scenario: 无密钥也能绿
- **WHEN** 在一个未配置任何 Secret 的 Fork PR 上触发 CI
- **THEN** 编译与单测阶段成功，不因缺少 Secret 而失败
- **AND** 依赖 Secret 的通知步骤在未配置时自行跳过而非报错

#### Scenario: 最小权限已声明
- **WHEN** 读取 `ci.yml` 的顶层 `permissions`
- **THEN** 值为 `contents: read`
- **AND** 若某个 job 需要更宽权限，必须在该 job 上显式声明并在注释里说明原因

#### Scenario: 并发取消生效
- **WHEN** 对同一分支连续推送两次
- **THEN** 先前的运行被标记为 cancelled 而不是并行跑完
- **AND** 最终状态由最后一次推送决定

### Requirement: 合并门禁由机器执行
仓库 SHALL 把 CI 的 `build` job 配置为 `main` ruleset 的必需状态检查，使规则不依赖人的自觉。
状态检查 MUST 先成功跑过一次，才能被 ruleset 选中。

#### Scenario: 门禁挂在 ruleset 上
- **WHEN** 检查 `main` 分支 ruleset 导出的 `rules` 数组
- **THEN** 存在 `required_status_checks` 规则项，且其中含 CI 的检查名
- **AND** 未通过该检查的 PR 无法合并

#### Scenario: 失败可定位
- **WHEN** 执行 `gh run view <run-id> --log-failed`
- **THEN** 能定位到失败步骤与失败原因，不需要翻完整日志
- **AND** `docs/github-ops/08-actions-automation.md` 记录该排查命令

### Requirement: 部署有 Environment 人工闸门
仓库 SHALL 提供带 `environment: production` 的部署工作流，使生产动作必须经过人工审批闸门，
且该环境 MUST 支持在环境级配置 Secret/Variable（与仓库级隔离）。

#### Scenario: 闸门工作流可手动触发
- **WHEN** 执行 `gh workflow run deploy-gate.yml`（workflow_dispatch）
- **THEN** 运行进入 waiting 状态，等待 `production` 环境的人工审批
- **AND** 审批通过后运行成功并打印闸门已到达

#### Scenario: 环境级配置可读
- **WHEN** 执行 `gh secret list -R <owner>/<repo> --env production` 或 `gh variable list -R <owner>/<repo> --env production`
- **THEN** 命令可正常返回（空列表也算通过），证明环境级配置通道可用
- **AND** 文档说明密钥只允许环境级或仓库级配置，禁止写进工作流文件

### Requirement: 工作流台账可核查
第 08 保 SHALL 把 `gh workflow list`、`gh run list` 的真实输出落盘到 `docs/github-ops/08-actions-automation.md`。

#### Scenario: 台账含真实运行记录
- **WHEN** 检查该文档的「命令输出」小节
- **THEN** 含 `gh workflow list` 与 `gh run list --limit 5` 的真实输出
- **AND** 输出里的 workflow 名称与仓库内 `.github/workflows/` 实际文件一致
