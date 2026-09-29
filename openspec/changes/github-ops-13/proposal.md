# 变更方案：「13太保玩转github」仓库运营治理落地

## 一、为什么做（Why）

`springaialibabapractice` 已经连载到第 18 掌，代码、文档、CI 都有，但**仓库治理层是空的**：
门面、语言一致性、问题入口、规则权限、安全基线、运营数据六条线各自为政，外部维护者凭首页十秒判断不出定位，
贡献者卡在英文模板与英文标签上，`main` 没有任何保护规则，依赖漏洞靠运气发现。

本变更把「13太保玩转github」系列（13 篇文章 + 13 集视频）讲过的**十三保运营治理**按章节顺序落到本仓库，
每一保都产出「可复现的命令 + 可审计的落盘产物 + 可验收的断言」，而不是只写一篇文档。

内容真源：媒体素材库 `media_material` 中 `13太保玩转github-*` 共 26 条（文章 13 条 `kind=article`、
视频 13 条 `kind=video`，素材 id 1314–1341）。本方案与下面的 tasks 逐保对齐视频里点名的产物路径
（`docs/github-ops/NN-*.md`、`scripts/*`、`.github/*`）与验收数字。

## 二、当前基线（改前实测）

| 维度 | 实测值 | 结论 |
|---|---|---|
| description | 「spring-ai-alibaba结合数字人项目实战，按照框架能力一步一步实战代码，真实可运行。」 | 与教程口径不同，未含「中文实战与数字人示例」定位 |
| homepageUrl | 空 | 无稳定入口 |
| repositoryTopics | `agent` `digital-human` `llm` `practice` `spring-ai-alibaba` `spring-ai-ali` | 含疑似截断项 `spring-ai-ali`，缺 `java` `rag` `mcp` |
| Features | Issues 开 / Discussions 开 / Wiki 开 / Projects 开 | 四项全开但除 Issues 外均无运营动作 |
| labels | 11 个 GitHub 默认英文标签 | 无中文标签体系，无 `triage` |
| Issue 模板 | `.github/ISSUE_TEMPLATE/chapter.md`（单文件 markdown） | 非 Issue Forms，无字段校验 |
| PR 模板 | `.github/pull_request_template.md` | 无背景/取舍/回滚章节 |
| Rulesets | `[]` | `main` 可强推、可删除，tag 可移动 |
| CODEOWNERS | 404 不存在 | 无 owner 归属 |
| Dependabot | `dependabot_security_updates: disabled`，vulnerability-alerts 404 | 依赖漏洞不告警、无升级 PR |
| Actions 默认权限 | `read` + `can_approve_pull_request_reviews=false` | 已合规，只需落盘为断言 |
| Secret scanning | `enabled` + push protection `enabled` | 已开，缺 SECURITY.md 与之配套 |
| Pages | 404 未启用 | 无发布门户 |
| Releases | 0 条（仅有 `ch01`–`ch18` 教程 tag） | 教程 tag 与软件 SemVer 混用无说明 |
| Metrics | 无任何落盘 | Traffic 只有 14 天窗口，历史不可追 |
| `docs/github-ops/` | 不存在 | 十三保产物目录整体缺失 |

## 三、做什么（What Changes）

按保次推进，一保一条分支与一个 PR，产物统一落在 `docs/github-ops/`：

| 保次 | 主题 | 核心产物 | 落盘的远端配置 |
|---|---|---|---|
| 01 | 仓库建制 | `docs/github-ops/01-repo-baseline.md` + `01-repo-baseline.json` + `scripts/gh-01-repo-baseline.sh` | description、homepage、topics 六项、Features |
| 02 | 本地客户端 | `02-local-clients.md`（Git/gh 速查 + Desktop 中文对照表）+ `.gitattributes` | 无（契约入仓库） |
| 03 | 全面中文化 | `glossary.md`、`labels.txt`、`scripts/gh-labels.sh`、三份 Issue Forms、`config.yml`、中文 PR 模板、`CONTRIBUTING.md`、`SECURITY.md` | 14 个中文标签幂等创建 |
| 04 | 社区讨论 | `04-discussions-community.md`、`.github/DISCUSSION_TEMPLATE/welcome.yml`、`scripts/gh-discussions-audit.sh` | 五类讨论分类 + Q&A 可标记答案 |
| 05 | 规则权限 | `05-rules-permissions.md`、`.github/CODEOWNERS`、`docs/github-ops/evidence/ruleset-*.json` | `main` branch ruleset + `ch*`/`v*` tag ruleset |
| 06 | PR 协作 | `06-pr-collaboration.md` + 定稿 PR 模板（背景/改动/取舍/证据/兼容/回滚/Checklist） | 合并策略 = squash，合并后删分支 |
| 07 | Issue 治理 | `07-issue-governance.md` + 三份 Issue Form（Bug/Feature/Docs）+ `config.yml` 入口路由 + `triage` 状态机标签 | `triage`/`accepted`/`wontfix`/`duplicate`/`type:*`/`status:verify` |
| 08 | Actions 自动化 | `08-actions-automation.md` + `.github/workflows/ci.yml` 补齐 `pull_request` 最小权限与并发 + `.github/workflows/release-gate.yml` | `production` Environment 人工闸门 |
| 09 | Project 规划 | `09-project-planning.md` + `docs/github-ops/project-fields.json` + `scripts/gh-project-sync.sh` | Projects 字段 7 个、视图 4 个 |
| 10 | 发布门户 | `10-release-pages.md` + `.github/release.yml` + `.github/workflows/deploy-pages.yml` + `docs/site/index.html` | Release 分类生成 + Pages 站点 |
| 11 | 安全治理 | `11-security-governance.md` + `.github/SECURITY.md` + `.github/dependabot.yml` | Dependabot alerts/security updates、依赖分组升级 |
| 12 | 数据复盘 | `12-data-review.md` + `scripts/gh-insights-snapshot.sh` + `scripts/gh-insights-report.sh` + `docs/github-ops/metrics/**` | 无（只读采集） |
| 13 | 开源运营 | `13-open-source-operations.md`（运营漏斗 SOP）+ `scripts/gh-ops-metrics.sh` + `.github/workflows/gh-ops-weekly.yml` | `good first issue` 任务池 ≥3 条 |

## 四、非目标（Non-goals）

- **不做 GitHub 界面汉化**：GitHub.com 与 GitHub Desktop 均无官方简中 UI，本变更只中文化仓库内可见资产。
- **不动平台层**：Topics 保持小写英文连字符；Actions 关键字、Issue Form 的 YAML 字段名、API 字段名一律不翻。
- **不翻 LICENSE**：保持 Apache-2.0 原文。
- **不开全仓库强制审批矩阵**：单人维护仓库强开 code owner review 会自锁，只对关键路径开。
- **不投付费推广**：十三保全部零现金预算，只投工时。
- **不重写业务代码**：除 `ci.yml` 的最小权限与并发补齐外，不碰 `digital-human*` / `knowledge-agent` 业务模块。
- **不改写历史已有 tag**：`ch01`–`ch18` 教程 tag 保持不动，只新增软件版本 tag 命名约定说明。

## 五、取舍（Trade-offs）

| 得到 | 失去 | 适用边界 |
|---|---|---|
| 可复现、可 diff、可审计的仓库基线（配置进脚本 + JSON 快照） | 「随手在 Web 上改一下就走」的轻快 | 需要被外部理解和接手的仓库 |
| 贡献漏斗从「看得懂」到「提得对」全中文且可复现 | 部分好看但无用的汉化；把 UI 也翻成中文的幻想 | 有外部贡献者的仓库 |
| `main` 与 tag 不可改写、审核有归属 | 单人直推的便利 | 已有第二个贡献者或自动化写入者 |
| 规则由机器执行，合并有硬门槛 | 一部分「随手就能合」的便利 | 多人并行仓库 |
| 安全状态可断言、泄漏处置有顺序 | 一部分「随手改」的自由度、CI 平均耗时上升 | 有依赖清单与 CI 的仓库 |
| 运营数据可追溯、任务池可被外部认领 | 仓库多一份自动提交、一个 cron、一份要人读的草稿 | 已进入有外部访问阶段 |

## 六、风险与回滚

| 风险 | 缓解 | 回滚 |
|---|---|---|
| Ruleset 误拦自动化写入（Actions 推 metrics 分支） | bypass 只给 admin，且 `gh-ops-weekly.yml` 走 PR 而非直推 main | 删除 ruleset：`gh api -X DELETE repos/{owner}/{repo}/rulesets/{id}` |
| 中文标签建重复 | 脚本 `gh label create --force` 幂等，先 `list` 再 `create` | 删除新增标签；官方默认标签 `good first issue`/`help wanted` 不重命名 |
| Pages 首次部署失败（未在 Settings 里选 Source） | 用 `actions/deploy-pages@v4` + `configure-pages@v5`，工作流自动建站点 | 关闭 Pages：`gh api -X DELETE repos/{owner}/{repo}/pages` |
| 依赖分组升级 PR 引入不兼容 | `dependabot.yml` 忽略 Spring Boot 主版本跳变 | 关闭该 PR，或回退 `dependabot.yml` |
| Traffic 数据已过期无法采到 14 天前 | 首次采集即落盘，之后按月追加 | 不改历史文件，缺失标注为「缺」 |
| 破坏既有 CI | 每个 PR 必过 `ci.yml`（编译 + 单测 + 架构图门禁） | `gh pr revert <PR号>` |

## 七、验收口径（三条硬线）

1. **产物线**：十三保各自的 `docs/github-ops/NN-*.md` 全部存在，且每篇含「命令输出」与「验收记录」两节。
2. **断言线**：每保至少一条 `gh api` / `gh` 命令输出被落盘为证据文件，可被第二人复现；不许只写「已配置成功」。
3. **红线线**：
   - Topics 中不含中文，且不存在截断项；
   - Issue Form 的 YAML 字段名未被翻译；
   - 仓库内不出现任何 token、邮箱、口令（截图同样不出现）；
   - `main` 直推成功 0 次、tag 被移动或删除 0 次、PR 检查通过率 100%。
