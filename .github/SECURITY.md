# 安全策略

> 本文件的**英文版本优先**：若本译文与仓库英文安全政策或 GitHub 官方安全文档冲突，以英文与官方文档为准。
> 相关治理记录见 [`docs/github-ops/11-security-governance.md`](docs/github-ops/11-security-governance.md)。

## 支持范围

本仓库是「降 Spring AI 阿里」18 掌的**练习仓库**（示例代码与文档）。

- 安全修复**只针对默认分支 `main` 上的最新状态**；
- 历史 `chapter/**` 分支视为只读参考，**不再单独回溯修复**；
- 已发布的 Release 只在下一个版本中修复，不回溯补丁。

## 报告漏洞

**请不要用公开 Issue 报告安全问题。** 公开披露会让所有使用者同时暴露。

请通过仓库的私密漏洞报告入口提交：

- Web：仓库 **Security → Advisories → Report a vulnerability**
- 直达：<https://github.com/lifuchun522/springaialibabapractice/security/advisories/new>

报告时请尽量包含：

| 项 | 说明 |
| --- | --- |
| 受影响的位置 | 文件路径或模块名（例如 `digital-human/src/main/...`） |
| 复现步骤 | 从干净环境开始，逐条命令 |
| 影响范围 | 能读到什么 / 能改什么 / 是否需要已认证 |
| 你的联系方式 | 便于追问细节（不会写进公开公告） |

## 响应约定

| 阶段 | 时限 |
| --- | --- |
| 确认收到 | 3 个工作日内 |
| 给出初步判断（是否成立、严重级别） | 7 个工作日内 |
| 修复与发布 | 视严重级别而定，修复后在本仓库发布安全公告 |

我们不会承诺无法兑现的具体修复日期。若报告不成立或超出范围，会说明判定依据。

## 范围外

以下**不属于**本仓库的安全范围：

- 本地开发环境的配置错误（例如把 Key 写进自己机器的 `application-local.yml`）；
- 第三方云服务自身的账号、配额与计费问题（阿里云、DeepSeek 等）；
- 你自己 Fork 之后的任何改动；
- 依赖库自身的漏洞——请报给上游；本仓库通过 Dependabot 跟进（见 [`.github/dependabot.yml`](.github/dependabot.yml)），
  仓库内的处理节奏见治理文档。

## 本仓库的安全基线（可自行核实）

| 项 | 状态 | 核实命令 |
| --- | --- | --- |
| Secret scanning | 已开启 | `gh api repos/lifuchun522/springaialibabapractice --jq '.security_and_analysis.secret_scanning'` |
| Push protection | 已开启 | 同上，`.secret_scanning_push_protection` |
| Dependabot alerts | 已开启 | `gh api repos/lifuchun522/springaialibabapractice/vulnerability-alerts --silent` |
| Dependabot security updates | 已开启 | `gh api repos/lifuchun522/springaialibabapractice/automated-security-fixes --silent` |
| Actions 默认权限 | 只读 | `gh api repos/lifuchun522/springaialibabapractice/actions/permissions/workflow` |
| 私密漏洞报告 | 已开启 | `gh api repos/lifuchun522/springaialibabapractice/private-vulnerability-reporting` |
| `main` 分支保护 | 禁强推/禁删除/必须过 CI | `gh ruleset check main` |

## 密钥规范（给贡献者）

- **不要把任何密钥写进仓库**：`DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`、数据库口令、镜像仓库口令一律通过环境变量注入；
- `.gitignore` 已排除 `.env`、`.env.*`（保留 `.env.example`）、`*.local.yml`、`*.pem` / `*.key` / `*.p12` / `*.jks`、`secrets/`；
- CI 需要的密钥只允许配置在**仓库级或 `production` 环境级** Secrets，不许写进工作流文件；
- **截图与日志里不得出现 token 与个人邮箱**——提交 PR 前自查一遍。

## 如果你不小心提交了密钥

顺序不能反（详见治理文档）：

1. **先吊销并轮换凭证**（去对应平台把那个 Key 作废、换新的）；
2. **再清理提交历史**。

清理历史**不能让凭证失效**——先清历史等于没修：Key 已经公开过了，任何人拿到都能用。
