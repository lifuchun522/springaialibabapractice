# security-governance Specification

## Purpose
TBD - created by archiving change github-ops-13. Update Purpose after archive.
## Requirements
### Requirement: 安全政策与私密报告通道
仓库 SHALL 提供 `.github/SECURITY.md`，写明支持范围、报告方式（走私密漏洞报告，不走公开 Issue）、
响应约定（确认 ≤3 工作日、初判 ≤7 工作日）与范围外事项。

#### Scenario: 政策内容可判定
- **WHEN** 阅读 `.github/SECURITY.md`
- **THEN** 支持范围写明「只针对默认分支最新状态，历史 chapter 分支只读不回溯」
- **AND** 明确要求不要在公开 Issue 披露细节，并给出 Security → Advisories 入口

#### Scenario: 私密报告入口可用
- **WHEN** 执行 `gh api repos/{owner}/{repo}/private-vulnerability-reporting`
- **THEN** `enabled` 为 `true`（私密漏洞报告已开启）
- **AND** 文档记录了检查该状态的确切命令

### Requirement: 依赖漏洞检测与升级
仓库 SHALL 开启 Dependabot alerts 与 Dependabot security updates，并提供 `.github/dependabot.yml`
按周升级 Maven 依赖与 GitHub Actions；Maven 升级 MUST 对 Spring 栈分组、并 MUST 忽略 Spring Boot 主版本跳变。

#### Scenario: 检测开关可断言
- **WHEN** 执行 `gh api repos/{owner}/{repo}/vulnerability-alerts --silent` 与 `gh api repos/{owner}/{repo}/automated-security-fixes --silent`
- **THEN** 两条命令均以退出码 0 结束（204 表示已开启）
- **AND** 第 11 保文档中记录了改前的 404（未开启）状态

#### Scenario: 依赖升级配置合法
- **WHEN** 解析 `.github/dependabot.yml`
- **THEN** `version: 2`，且 `updates` 含 `package-ecosystem: maven` 与 `github-actions` 两条
- **AND** maven 条目含 `groups`（Spring 栈）与忽略 Spring Boot 主版本跳变的 `ignore` 规则
- **AND** 引用的每个标签名都存在于仓库标签列表中

### Requirement: 供应链检测与最小权限落盘
仓库 SHALL 记录 Secret scanning、Push protection、非提供方模式检测三项状态，并把 Actions 默认权限
收紧为只读、禁止工作流自行批准 PR；所有结论 MUST 由 `gh api` 真实输出支撑。

#### Scenario: 安全状态可断言
- **WHEN** 执行 `gh api repos/{owner}/{repo} --jq '.security_and_analysis'`
- **THEN** 输出含 `secret_scanning` 与 `secret_scanning_push_protection` 两项及其状态
- **AND** 第 11 保文档中落盘了该命令的真实输出

#### Scenario: Actions 默认权限已收紧
- **WHEN** 执行 `gh api repos/{owner}/{repo}/actions/permissions/workflow`
- **THEN** `default_workflow_permissions` 为 `read`
- **AND** `can_approve_pull_request_reviews` 为 `false`

### Requirement: 密钥泄漏应急顺序
第 11 保 SHALL 在 `docs/github-ops/11-security-governance.md` 明确泄漏处置顺序：
先吊销并轮换凭证，再清理提交历史；MUST 说明清理历史不能让凭证失效，顺序不可颠倒。

#### Scenario: 顺序写死不可误用
- **WHEN** 检查该文档的应急章节
- **THEN** 明确第一步为「吊销并轮换」，第二步为「清理历史」
- **AND** 明确写出「顺序反了等于没修」

#### Scenario: 仓库无明文凭据
- **WHEN** 在仓库工作区检索常见的密钥形态（`ghp_`、`github_pat_`、`sk-`、`AKIA`）
- **THEN** 除文档中的示例占位外，不出现任何真实密钥值
- **AND** 文档中出现的示例均以 `<占位>` 形式给出

