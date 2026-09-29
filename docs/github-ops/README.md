# 十三保仓库运营治理（GitHub Ops）

本目录是「13太保玩转github」系列（13 篇文章 + 13 集视频）在本仓库的落地产物：
每一保都给出**可复现的命令 + 可审计的落盘证据 + 可验收的断言**，而不是只写一篇文档。

- OpenSpec 变更（已归档）：[`openspec/changes/archive/2026-09-29-github-ops-13/`](../../openspec/changes/archive/2026-09-29-github-ops-13/proposal.md)
- 归档后的能力规格（当前行为真源）：[`openspec/specs/`](../../openspec/specs)
- 阅读顺序：按下表 01 → 13。每篇结构一致（改前状态 / 改后状态 / 命令输出 / 验收记录 / 明确不做）。

## 索引

| 保次 | 主题 | 文档 | 关键产物 | 一句话结论 |
| --- | --- | --- | --- | --- |
| 01 | 仓库建制 | [01-repo-baseline.md](01-repo-baseline.md) | `scripts/gh-01-repo-baseline.sh`、`01-repo-baseline.json` | description/homepage/Topics 与 Features 一次性配好并留快照；不运营的能力一律关闭 |
| 02 | 本地客户端 | [02-local-clients.md](02-local-clients.md) | `.gitattributes` | 三入口收敛为一条流程：CLI 是唯一权威验收路径，Desktop 只保证看得懂菜单 |
| 03 | 全面中文化 | [03-chinese-localization.md](03-chinese-localization.md) | `glossary.md`、`labels.txt`、`scripts/gh-labels.sh` | 内容层全翻、约定层翻值不翻键、平台层绝对不翻 |
| 04 | 社区讨论 | [04-discussions-community.md](04-discussions-community.md) | `scripts/gh-discussions-audit.sh` | Discussions 是分流入口：只有 Q&A 可标记答案，其余不做响应承诺 |
| 05 | 规则权限 | [05-rules-permissions.md](05-rules-permissions.md) | `.github/CODEOWNERS`、`evidence/ruleset-*.json` | main 与 tag 规则实测拦得住；**不留 bypass**（配了 admin always 等于没规则） |
| 06 | PR 协作 | [06-pr-collaboration.md](06-pr-collaboration.md) | `.github/pull_request_template.md` | Draft → Ready → Checks → Squash 闭环，回滚用 `gh pr revert` 不重写历史 |
| 07 | Issue 治理 | [07-issue-governance.md](07-issue-governance.md) | 三份 Issue Form、`config.yml` | 入口结构化的关键不是多加字段，而是把「验收标准」设成必填 |
| 08 | Actions 自动化 | [08-actions-automation.md](08-actions-automation.md) | `.github/workflows/ci.yml`、`deploy-gate.yml` | 规则由机器执行；部署有 production 人工闸门 |
| 09 | Project 规划 | [09-project-planning.md](09-project-planning.md) | `project-fields.json`、`scripts/gh-project-sync.sh` | 七字段四视图的作战盘，排期可导出、可交接 |
| 10 | 发布门户 | [10-release-pages.md](10-release-pages.md) | `.github/release.yml`、`deploy-pages.yml`、`docs/site/` | Release 管版本契约、Pages 管稳定门户、教程 tag 与软件版本分离 |
| 11 | 安全治理 | [11-security-governance.md](11-security-governance.md) | `.github/SECURITY.md`、`.github/dependabot.yml` | 检测 → 通知 → 升级 PR → 最小权限；泄漏处置先吊销后清历史 |
| 12 | 数据复盘 | [12-data-review.md](12-data-review.md) | `scripts/gh-insights-*.sh`、`metrics/` | Traffic 只有 14 天，第一次采集就必须落盘；指标只作判断输入 |
| 13 | 开源运营 | [13-open-source-operations.md](13-open-source-operations.md) | `scripts/gh-ops-metrics.sh`、`gh-ops-weekly.yml` | 六个入口要串成一条漏斗，任务池里的任务必须真能开工 |

## 通用约定

| 项 | 约定 |
| --- | --- |
| 证据位置 | 每篇的「命令输出」小节粘贴真实终端输出；JSON 快照放 `evidence/`，指标放 `metrics/` |
| 脚本命名 | `scripts/gh-NN-<主题>.sh`（保次对齐）；只读脚本不加写操作 |
| 幂等 | 所有写远端配置的脚本都要能重复执行且结果一致 |
| 红线 | Topics 不翻、Issue Form 键名不翻、仓库内无 token/邮箱/口令；`main` 直推 0 次、tag 移动或删除 0 次 |
| 验收 | 每条断言都要能指向一个证据文件、一条命令输出或一个 Issue/PR 编号 |
| 死链门禁 | `python3 scripts/check-github-ops-links.py`（本目录的索引不允许有死链） |

## 全量验收

逐保断言、红线复检、未完成项与 CI 回归的汇总见 [`验收记录.md`](验收记录.md)。

## 脚本清单

| 脚本 | 用途 | 是否写远端 |
| --- | --- | --- |
| `scripts/gh-01-repo-baseline.sh` | 仓库 About/Topics/Features + 快照 | 写 |
| `scripts/gh-labels.sh` | 中文标签幂等同步 + 表单引用一致性校验 | 写 |
| `scripts/gh-discussions-audit.sh` | 讨论只读巡检（未答问答） | **只读** |
| `scripts/gh-05-rulesets.sh` | main / tag ruleset + JSON 快照 | 写 |
| `scripts/gh-09-project-fields.sh` | 作战盘字段幂等补齐 + 字段快照 | 写 |
| `scripts/gh-09-project-sync.sh` | 条目加入作战盘（幂等 + 归属校验） | 写 |
| `scripts/gh-13-seed-good-first-issues.sh` | 任务池创建（幂等） | 写 |
| `scripts/gh-insights-snapshot.sh` | Traffic 九端点按月落盘 | **只读** |
| `scripts/gh-insights-report.sh` | 月报生成（口径固定） | **只读** |
| `scripts/gh-ops-metrics.sh` | 周指标四类按日归档 | **只读** |
| `scripts/check-github-ops-links.py` | 索引死链门禁（CI 门禁之一） | 本地校验 |

## 明确不做（全系列汇总）

| 不做 | 什么条件下再评估 |
| --- | --- |
| GitHub 界面汉化 | 不评估：平台不提供简中 UI，任何补丁都会在下一次改版失效 |
| 打开 Wiki | 不评估：知识只进 `docs/` 与 `openspec/specs/`，避免第二真源 |
| 全仓库强制 code owner 审批 | 出现第二名具备写权限的长期维护者时 |
| 自定义域名 | Pages 站点连续可用 1 个月、且需要对外承诺长期地址时 |
| 付费推广 | 仓库出现可归因的转化事件（Clone → 运行 → Issue）之后 |
| 全仓库 stale bot 自动关 Issue | 出现「僵尸 Issue 影响阅读」的实际投诉时 |
| 用脚本自动创建/关闭 Discussion | `gh discussion` 子命令稳定之后 |
