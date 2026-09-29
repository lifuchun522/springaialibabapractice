# Tasks：十三保治理落地执行清单

> 每一条都用「动作；验证：<可执行断言>」的格式写。**没有验证行的条目不算完成**。
> 证据统一落在 `docs/github-ops/`，远端配置一律留 `gh api` / `gh` 的真实输出。

## 0. 准备与工作区

- [x] 0.1 从媒体素材库读取「13太保玩转github」全部 26 条内容（文章 13 条 + 视频 13 条，素材 id 1314–1341），
      提取每保的产物路径、命令与验收数字；
      验证：本目录 `proposal.md` 的保次表逐条对应视频中点名的产物路径，且十三保一一不漏
- [x] 0.2 实测仓库改前基线（description/homepage/topics/features/labels/issue模板/rulesets/CODEOWNERS/dependabot/
      workflow权限/security_and_analysis/pages/releases/metrics 共 14 项）；
      验证：`proposal.md` 第二节表格中每一项都有实测值，无「待确认」
- [x] 0.3 建立工作分支 `githubops/13-open-source-operations`（合并保次 01–13 的总分支）；
      验证：`git branch --show-current` 输出该分支名，且 `git status -sb` 显示与 `origin/main` 的关系
- [x] 0.4 创建 `docs/github-ops/` 与 `docs/github-ops/evidence/`、`docs/github-ops/metrics/raw/` 目录；
      验证：三个目录存在，且 `docs/github-ops/README.md` 给出十三保阅读顺序索引

## 1. 第 01 保：仓库建制

- [x] 1.1 写 `scripts/gh-01-repo-baseline.sh`：设置 description（含「Spring AI Alibaba」「中文实战」「数字人示例」）、
      homepage（Pages 地址）、六个 Topics，并按规则开关 Features；
      验证：脚本以 `set -euo pipefail` 编写，重复执行两次退出码均为 0
- [x] 1.2 执行脚本写远端配置并落盘 `docs/github-ops/01-repo-baseline.json`；
      验证：`gh repo view --json description,homepageUrl,repositoryTopics,visibility,defaultBranchRef` 五项非空率 100%
- [x] 1.3 Topics 红线自检：6 个标签均为 `^[a-z0-9-]+$`，且截断项 `spring-ai-ali` 已消除；
      验证：脚本内置校验，命中中文或截断项时以非零退出码结束并打印非法标签名（人为构造一次非法输入实测）
- [x] 1.4 Features 取舍：禁 Wiki、禁 Projects，Issues 保持开；
      验证：`gh repo view --json hasWikiEnabled,hasProjectsEnabled,hasIssuesEnabled` 返回 `false,false,true`
- [x] 1.5 README 首屏五段（项目定位/快速开始/掌次路线/技术栈/参与贡献）齐全；
      验证：脚本或人工检索确认五段标题存在，且每段含可点击内链，无「待补充」占位
- [x] 1.6 写 `docs/github-ops/01-repo-baseline.md`（改前/改后/Web 配置路径/命令输出/验收记录五节）；
      验证：文档五节齐全，命令输出为真实粘贴（含日期）

## 2. 第 02 保：本地客户端

- [x] 2.1 写 `docs/github-ops/02-local-clients.md`，含 Git/gh 命令速查（≥15 条）与 Desktop 英文菜单中文对照表（≥20 条）；
      验证：脚本统计两个表格行数达标，且对照表每条都有「对应命令」列
- [x] 2.2 新增 `.gitattributes`：`* text=auto eol=lf`，`.bat`/`.cmd` 保留 CRLF，二进制文件显式声明；
      验证：Windows 上克隆后 `git status` 为 clean，无行尾差异类改动
- [x] 2.3 核查 `.gitignore` 已排除 `.env`/`.env.*`/日志/IDE/构建产物；
      验证：`git ls-files` 过滤 `\.env$|\.env\.` 的输出只有 `.env.example` 类模板
- [x] 2.4 写「冲突只解不覆盖」章节，明确禁止 `git push --force`，给出 `git revert` 替代路径；
      验证：文档中该章节存在，且包含 `git status` → 解决 → `git add -p` → `git commit` 的完整顺序

## 3. 第 03 保：全面中文化

- [x] 3.1 写 `docs/github-ops/glossary.md`：Issue/Pull Request/Label/Milestone/Discussion/Release/Topics 七条用词规则；
      验证：表格七行齐全，且 Topics 行明确「不可翻译，保持小写英文连字符」
- [x] 3.2 写 `docs/github-ops/labels.txt`：五维度 14 条标签（类型/优先级/状态/模块/难度），格式 `名称|颜色|说明`；
      验证：行数 ≥14，每行三段均可解析，颜色为 6 位十六进制
- [x] 3.3 写 `scripts/gh-labels.sh`：读清单用 `gh label create --force` 幂等同步，末尾断言官方默认标签仍在；
      验证：连续执行两次退出码均为 0，`gh label list --limit 100` 总数不变
- [x] 3.4 执行同步并落盘输出；
      验证：`gh label list -R lifuchun522/springaialibabapractice --limit 100` 能查到全部 14 条中文标签，
      且 `good first issue`、`help wanted` 未被改名
- [x] 3.5 三份中文 Issue Form + `config.yml`（字段名全英文，值与正文中文）；
      验证：YAML 解析通过，且键名集合不含任何中文键名；`id: acceptance` 且 `required: true` 在三份文件中均存在
- [x] 3.6 定稿中文 PR 模板与 `CONTRIBUTING.md`（含「英文版本优先」声明）；
      验证：PR 模板含背景/改动/架构取舍/测试证据/兼容性/截图/回滚/Checklist 八节；CONTRIBUTING 含三条贡献路径
- [x] 3.7 用中文标签真实创建一个测试 Issue 并回读；
      验证：`gh issue view <编号> --json labels` 返回 `类型:缺陷` 与 `优先级:中`，创建后关闭并记录编号
- [x] 3.8 平台层红线自检：Topics 全英文、LICENSE 未翻、Actions 关键字未翻；
      验证：三条检查各有一条命令输出贴进 `docs/github-ops/03-chinese-localization.md`
- [x] 3.9 写 `docs/github-ops/03-chinese-localization.md`；
      验证：含「翻哪一层、不翻哪一层」三层对照表，且每层都有实例

## 4. 第 04 保：社区讨论

- [x] 4.1 盘点并用 Web/GraphQL 落实五类讨论分类（公告/问答/建议讨论/作品展示/投票）；
      验证：GraphQL 回读 `discussionCategories(first:20)`，分类数 ≤6，且只有问答类 `isAnswerable = true`
- [x] 4.2 写 `scripts/gh-discussions-audit.sh`（只读巡检未回答的问答讨论）；
      验证：脚本内不含 `mutation` 字样；执行后输出「分类 + 编号 + 标题 + URL」四列
- [x] 4.3 新增 `.github/DISCUSSION_TEMPLATE/welcome.yml`；
      验证：YAML 可解析，含 `title` 与 `body`，正文中文
- [x] 4.4 写 `docs/github-ops/04-discussions-community.md`：五类说明、分流规则、FAQ 回写规则（7 天内）、
      主持人操作清单（48 小时首响）、公告模板（含发布人/依据版本/失效日期）；
      验证：五节齐全，分流规则每条都可判定（不是「看情况」）
- [x] 4.5 在 Discussions 发一条真实问答并标记答案，验证闭环；
      验证：讨论 URL 落盘进文档，且回读 `isAnswered = true`；FAQ 回写目标文件已更新

## 5. 第 05 保：规则权限

- [x] 5.1 建 `main` 分支 ruleset：禁删除、禁非快进推送、要求 PR、要求状态检查（CI 的 `build`）；
      验证：`gh api repos/{repo}/rulesets` 中该 ruleset `enforcement = active`，且 `rules` 含上述四项
- [x] 5.2 建 tag ruleset：`ch*` 与 `v*` 禁删除、禁更新；
      验证：人为尝试 `git push origin :refs/tags/ch18` 被远端拒绝，拒绝原文贴进文档（失败后立即确认 tag 仍在）
- [x] 5.3 强推 main 被拒实测；
      验证：`git push --force origin main` 被拒，且 `git log --oneline -1 origin/main` 不变
- [x] 5.4 新增 `.github/CODEOWNERS`：只卡 `.github/`、`scripts/`、`docs/github-ops/` 三条关键路径，**不做**全仓库强审；
      验证：`gh api repos/{repo}/contents/.github/CODEOWNERS --jq .path` 返回路径；三条模式均匹配真实存在的路径
- [x] 5.5 owner 权限前置校验；
      验证：`gh api repos/{repo}/collaborators/lifuchun522/permission` 返回 `admin` 或 `write`，输出贴进文档
- [x] 5.6 导出 ruleset JSON 快照到 `docs/github-ops/evidence/`；
      验证：至少两份详情快照（分支 + tag），每份含 `id`/`name`/`target`/`enforcement`/`conditions`/`rules`
- [x] 5.7 写 `docs/github-ops/05-rules-permissions.md`，含教程 tag 与软件版本 tag 语义分离说明、bypass 与红线；
      验证：文档明确「规则变更必须同步 JSON 快照」与「bypass 只给 admin 且每次绕过要在 PR 写原因」

## 6. 第 06 保：PR 协作

- [x] 6.1 按第 06 保定稿覆盖 `.github/pull_request_template.md`（八节 + Checklist + `Closes #`）；
      验证：`gh pr view <编号> --json body --jq .body` 中能检索到八个小节标题
- [x] 6.2 全流程演练一次：`--draft` → `ready` → `checks --watch` → `review --comment` → `merge --squash --delete-branch`；
      验证：每一步的真实终端输出贴进 `docs/github-ops/06-pr-collaboration.md`，且 `gh pr view <编号> --json state,mergedAt` 显示已合并
- [x] 6.3 回滚路径演练（用一次无副作用的提交）；
      验证：`gh pr revert <编号>` 或 `git revert <sha>` 产出反向提交，`main` 历史中两者都在，未重写历史
- [x] 6.4 合并策略固化为 squash、合并后删源分支；
      验证：`gh api repos/{repo}` 或 ruleset 导出中可见 squash 相关设置；`git branch -r` 中已合并的 `githubops/*` 源分支被删除
- [x] 6.5 写 `docs/github-ops/06-pr-collaboration.md`（含门禁未过不能合的证据、可回滚证据）；
      验证：文档「命令输出」小节含 `gh pr checks --watch` 与 `gh pr merge --squash --delete-branch` 的真实输出

## 7. 第 07 保：Issue 治理

- [x] 7.1 补齐状态机标签：`triage`/`accepted`/`wontfix`/`duplicate`/`status:verify`（若第 03 保未覆盖则在此补）；
      验证：`gh label list --limit 100 --json name --jq '.[].name'` 输出含六个名称，且每个都有 description
- [x] 7.2 复核三份 Issue Form 的标签引用与 `labels.txt` 一致；
      验证：脚本逐个比对，输出「无缺失标签」
- [x] 7.3 `.github/ISSUE_TEMPLATE/config.yml`：`blank_issues_enabled: false` + Discussions 联系入口；
      验证：文件内容两项齐全，且 url 指向 `https://github.com/lifuchun522/springaialibabapractice/discussions`
- [x] 7.4 用 `gh issue create --template` 真实提交一次并推进状态机（打 triage → 认领分支 → 关闭）；
      验证：三条命令的真实输出贴进文档，`gh issue list --label triage` 能反映状态变化
- [x] 7.5 写 `docs/github-ops/07-issue-governance.md`：判重规则、字段-标签映射表、认领推进、僵尸清理四节；
      验证：四节齐全，每节至少一条可执行 `gh` 命令
- [x] 7.6 建立首响口径；
      验证：文档写明「工作日 48 小时内首次响应」并给出 `gh issue list --label triage --state open` 的周检命令

## 8. 第 08 保：Actions 自动化

- [x] 8.1 给 `ci.yml` 补顶层 `permissions: contents: read` 与 `concurrency`（`cancel-in-progress: true`），
      保留既有 `build` job、架构图门禁与钉钉通知逻辑不变；
      验证：YAML 可解析；`gh workflow view ci.yml` 能读回；一次 PR 触发的运行成功
- [x] 8.2 验证无密钥也能绿：确认 CI 不依赖任何 Secret 即可完成编译与单测；
      验证：PR 运行的 `build` job 在零 Secret 情况成功，证据 URL 贴进文档
- [x] 8.3 新增 `.github/workflows/deploy-gate.yml`：`workflow_dispatch` + `environment: production` + 部署 concurrency；
      验证：`gh workflow list` 能看到该 workflow；手动触发后运行进入 waiting 等审批（若无人审批则记录为 waiting 状态原文）
- [x] 8.4 在 `production` 环境建一个非敏感 Variable 并读回（证明环境级配置通道可用）；
      验证：`gh variable list -R {repo} --env production` 能列出该项；`gh secret list --env production` 可正常返回
- [x] 8.5 把 CI 的 `build` 作为 `main` ruleset 的必需状态检查（与 5.1 联动）；
      验证：ruleset 导出的 `required_status_checks` 中含该检查名；未过检查的 PR 无法合并（实测一次 BLOCKED）
- [x] 8.6 写 `docs/github-ops/08-actions-automation.md`，落盘 `gh workflow list`、`gh run list --limit 5`、
      `gh run view <id> --log-failed` 真实输出；
      验证：文档「命令输出」小节三段齐全，且 workflow 名与 `.github/workflows/` 实际文件一致

## 9. 第 09 保：Project 规划

- [x] 9.1 盘点既有 Projects，确定作战盘编号（`gh project list --owner lifuchun522`）；
      验证：命令输出贴进文档，并记录复用还是新建的决定与理由
- [x] 9.2 建/补齐 7 个字段：状态、优先级、类型、章节、版本、开始日期、目标日期；
      验证：`gh project field-list <number> --owner lifuchun522 --format json` 含 7 个字段，
      状态选项恰为「待办/进行中/评审中/已完成」
- [x] 9.3 导出字段快照 `docs/github-ops/project-fields.json`；
      验证：文件为 GraphQL `projectV2.fields` 真实输出，不含占位 id（无 `xxx`）
- [x] 9.4 配置四类视图（Table 总览 / Board 状态 / Roadmap 日期 / 按版本分组），并记录各自回答的问题；
      验证：文档中四个视图职责互不重复，并给出访问 URL
- [x] 9.5 写 `scripts/gh-project-sync.sh`：把 Issue/PR 加入作战盘并回填字段，幂等；
      验证：同一 Issue 连续同步两次不产生重复条目，第二次输出「已存在，跳过」
- [x] 9.6 同步真实条目并导出作战盘 JSON；
      验证：至少 13 条（每保一条）条目落盘，导出 JSON 可被解析，且含章节/版本字段值
- [x] 9.7 写 `docs/github-ops/09-project-planning.md`（含「本章已完成/进行中/下一章」的可读结论）；
      验证：文档给出的结论能由导出 JSON 反查证实

## 10. 第 10 保：发布门户

- [x] 10.1 新增 `.github/release.yml`（中文分类 + `skip-changelog` 排除 + `'*'` 兜底）；
      验证：YAML 可解析，最后一个分类 labels 含 `'*'`，引用的标签均存在于仓库
- [x] 10.2 新增 `docs/site/index.html`（Quick Start / 掌次路线 / FAQ / 贡献入口四块，`lang="zh-CN"`）；
      验证：本地打开或静态检查确认四块标题存在，无外部依赖导致的空白页
- [x] 10.3 新增 `.github/workflows/deploy-pages.yml`（`configure-pages@v5` + `upload-pages-artifact@v3` + `deploy-pages@v4`，
      `permissions: pages: write`/`id-token: write`，`concurrency: pages`）；
      验证：YAML 可解析，`gh workflow list` 可见；首次运行成功且有部署 URL
- [x] 10.4 打首个软件版本 tag 并建 Release；
      验证：`git tag -a v<版本>` + `gh release create v<版本> --verify-tag --generate-notes`，
      `gh release view v<版本> --json tagName,isDraft,url` 返回 `isDraft=false`
- [x] 10.5 Pages 站点可访问校验；
      验证：请求站点地址返回 HTTP 200（或部署输出中的 page_url 可打开），且页面含四块内容
- [x] 10.6 homepageUrl 与 Pages 地址对齐（回写第 01 保脚本来幂等保证）；
      验证：`gh repo view --json homepageUrl` 与实际站点地址一致
- [x] 10.7 写 `docs/github-ops/10-release-pages.md`（含 `ch*` 与 `v*` 语义分离一节）；
      验证：文档含 `gh release view`、`gh run list --workflow=deploy-pages.yml` 的真实输出

## 11. 第 11 保：安全治理

- [x] 11.1 新增 `.github/SECURITY.md`（支持范围、私密报告入口、响应约定、范围外 + 英文优先声明）；
      验证：四项齐全，且 `gh api repos/{repo}/private-vulnerability-reporting` 返回 `enabled: true`
- [x] 11.2 开启 Dependabot alerts 与 security updates；
      验证：`gh api repos/{repo}/vulnerability-alerts --silent` 与 `.../automated-security-fixes --silent` 退出码均为 0；
      改前 404 原文与改后成功一起贴进文档
- [x] 11.3 新增 `.github/dependabot.yml`（maven 周扫 + github-actions 周扫 + Spring 栈分组 + 忽略 Spring Boot 主版本跳变）；
      验证：YAML 可解析，`version: 2`，两条 ecosystem 齐全；引用的标签均存在
- [x] 11.4 落盘供应链检测状态与 Actions 默认权限；
      验证：`gh api repos/{repo} --jq '.security_and_analysis'` 与 `gh api repos/{repo}/actions/permissions/workflow`
      的真实输出贴进文档；后者为 `read` + `false`
- [x] 11.5 密钥泄漏应急顺序写进文档（先吊销轮换、再清历史）；
      验证：文档明确「顺序反了等于没修」，且给出第一步吊销的具体操作清单
- [x] 11.6 仓库明文凭据自检；
      验证：检索 `ghp_`/`github_pat_`/`sk-`/`AKIA` 仅命中文档中的占位示例（无真实值）
- [x] 11.7 开启非提供方模式检测（`secret_scanning_non_provider_patterns` 可开则开，不可开则记录原因）；
      验证：`gh api repos/{repo} --jq '.security_and_analysis.secret_scanning_non_provider_patterns'` 的输出贴进文档

## 12. 第 12 保：数据复盘

- [x] 12.1 写 `scripts/gh-insights-snapshot.sh`（九个端点，按月落盘到 `metrics/raw/`，声明 14 天 UTC 口径）；
      验证：执行后 `metrics/raw/` 出现当月前缀 JSON 文件，且每个文件可被 JSON 解析器读出
- [x] 12.2 执行首次采集并落盘；
      验证：`views`/`clones` 文件含 `count`/`uniques`/明细数组；`gh api .../traffic/views --jq '{window_days:(.views|length),visits:.count,uniques:.uniques}'` 输出贴进文档
- [x] 12.3 写 `scripts/gh-insights-report.sh` 并生成当月月报 `docs/github-ops/metrics/YYYY-MM.md`；
      验证：月报含四项核心指标行 + 原始快照清单，表头与指标名固定
- [x] 12.4 补「指标 → 判断 → 动作」结构与「长期为零就砍」原则；
      验证：`docs/github-ops/12-data-review.md` 中两处均可检索到
- [x] 12.5 采集只读自检；
      验证：脚本中不含任何写 GitHub 状态的操作（无 `-X POST/PUT/PATCH/DELETE`，无 `mutation`）

## 13. 第 13 保：开源运营

- [x] 13.1 写 `docs/github-ops/13-open-source-operations.md`：七级漏斗逐级取数、
      每月节奏（1 号导数据/5 号前备 3 个任务/每次 Release 发公告）、响应承诺（Issue 48h / PR 72h）、
      首次响应模板、外部传播回链清单；
      验证：七级每级都有可执行命令；节奏三条与承诺两条齐全；模板三要素齐全
- [x] 13.2 写 `scripts/gh-ops-metrics.sh`（按日期戳归档 views/clones/gfi/releases 四类）；
      验证：执行后四类文件均生成，输出打印写入路径
- [x] 13.3 新增 `.github/workflows/gh-ops-weekly.yml`（cron 周一 + `workflow_dispatch`，无改动不红灯）；
      验证：YAML 可解析；`gh workflow list` 可见；手动触发一次运行成功（或无改动分支输出 `no changes` 且成功）
- [x] 13.4 建 ≥3 个带验收标准的 `good first issue` 真实任务；
      验证：`gh issue list --label 'good first issue' --state open --json number,title` 返回 ≥3 条，
      每条正文含「要做什么/验收标准/改动范围」三节
- [x] 13.5 执行一次真实漏斗取数（access → gfi → releases → discussions）；
      验证：四条命令的真实输出贴进文档，`gh issue list --label 'good first issue'` 不再是空列表
- [x] 13.6 明确「指标只作判断输入、不作考核目标」与「只追 Star 的代价」；
      验证：文档中两处声明均可检索到

## 14. 汇总验收与归档

- [x] 14.1 写 `docs/github-ops/README.md`：十三保索引 + 阅读顺序 + 每保一句话结论；
      验证：十三个链接全部指向真实存在的文件（脚本校验，无死链）
- [x] 14.2 写 `docs/github-ops/验收记录.md`：逐条对应本清单与十三份 spec 的场景，如实记录未通过项；
      验证：每条断言都能指向一个证据文件、一条命令输出或一个 Issue/PR 编号
- [x] 14.3 全量红线复检（四条）：
      (a) Topics 无中文无截断；(b) Issue Form 键名未翻；(c) 仓库内无 token/邮箱/口令；
      (d) `main` 直推 0 次、tag 移动或删除 0 次、PR 检查通过率 100%；
      验证：四条各有一条命令输出落盘到验收记录
- [x] 14.4 回归 CI 全绿：`./mvnw -B -ntp clean verify` 通过，既有架构图门禁与测试断言未被削弱；
      验证：本地命令通过 + PR 上 CI 运行成功，两个证据都贴进验收记录
- [x] 14.5 `openspec validate github-ops-13` 通过（全部 artifact 完成、无缺项）；
      验证：命令输出 `Change 'github-ops-13' is valid`
- [x] 14.6 归档变更并合并主线；
      验证：`openspec archive github-ops-13 --yes` 成功，`openspec/specs/` 下出现十三份能力规格；
      PR 以 `--squash --delete-branch` 合并且 `gh pr view <编号> --json state` 为 `MERGED`
- [x] 14.7 收敛未完成项：把本次未做（如自定义域、付费推广、全仓库审批矩阵）显式记入文档「明确不做」清单；
      验证：清单可检索，且每条都写了「什么条件下再评估」
