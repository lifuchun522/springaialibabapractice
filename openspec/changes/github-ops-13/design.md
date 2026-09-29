# 设计方案：十三保治理层的落地结构

## 一、整体结构

十三保是**同一个仓库的十三层治理面**，不是十三个独立项目。它们共享一套产物骨架：

```text
springaialibabapractice/
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── 01-bug.yml              # 第 07 保：缺陷报告（Issue Form）
│   │   ├── 02-feature.yml          # 第 07 保：功能建议（Issue Form）
│   │   ├── 03-docs.yml             # 第 07 保：文档问题（Issue Form）
│   │   └── config.yml              # 第 07 保：空白 Issue 关闭 + Discussions 路由
│   ├── DISCUSSION_TEMPLATE/
│   │   └── welcome.yml             # 第 04 保：欢迎帖模板
│   ├── CODEOWNERS                  # 第 05 保：关键路径 owner
│   ├── SECURITY.md                 # 第 03/11 保：安全政策（中文）
│   ├── dependabot.yml              # 第 11 保：依赖升级
│   ├── release.yml                 # 第 10 保：Release 分类
│   ├── pull_request_template.md    # 第 06 保：PR 模板（覆盖既有文件）
│   └── workflows/
│       ├── ci.yml                  # 第 08 保：已有，补齐 permissions/concurrency
│       ├── deploy-gate.yml         # 第 08 保：production Environment 闸门
│       ├── deploy-pages.yml        # 第 10 保：Pages 发布
│       └── gh-ops-weekly.yml       # 第 13 保：运营指标周归档
├── .gitattributes                  # 第 02 保：LF 策略
├── CONTRIBUTING.md                 # 第 03 保：中文贡献指南
├── scripts/
│   ├── gh-01-repo-baseline.sh      # 第 01 保：About/Topics/Features + 快照
│   ├── gh-labels.sh                # 第 03 保：中文标签幂等同步
│   ├── gh-discussions-audit.sh     # 第 04 保：讨论只读巡检
│   ├── gh-project-sync.sh          # 第 09 保：作战盘条目同步
│   ├── gh-insights-snapshot.sh     # 第 12 保：Traffic 原始快照
│   ├── gh-insights-report.sh       # 第 12 保：月报生成
│   └── gh-ops-metrics.sh           # 第 13 保：周指标归档
├── docs/
│   ├── github-ops/
│   │   ├── README.md               # 十三保索引与阅读顺序
│   │   ├── 01-repo-baseline.md     # 每保一篇：改前/改后/命令输出/验收记录
│   │   ├── 01-repo-baseline.json   # 第 01 保：gh repo view 真实输出
│   │   ├── 02-local-clients.md
│   │   ├── glossary.md             # 第 03 保：用词词汇表
│   │   ├── labels.txt              # 第 03 保：标签清单真源
│   │   ├── 03-chinese-localization.md
│   │   ├── 04-discussions-community.md
│   │   ├── 05-rules-permissions.md
│   │   ├── 06-pr-collaboration.md
│   │   ├── 07-issue-governance.md
│   │   ├── 08-actions-automation.md
│   │   ├── 09-project-planning.md
│   │   ├── 10-release-pages.md
│   │   ├── 11-security-governance.md
│   │   ├── 12-data-review.md
│   │   ├── 13-open-source-operations.md
│   │   ├── evidence/               # 第 05 保：ruleset JSON、权限输出
│   │   ├── metrics/                # 第 12/13 保：月报与原始快照
│   │   │   └── raw/
│   │   └── project-fields.json     # 第 09 保：字段导出
│   └── site/index.html             # 第 10 保：Pages 门户首页
└── openspec/
    ├── specs/<capability>/spec.md           # 归档后成为仓库治理真源
    └── changes/github-ops-13/               # 本变更
```

## 二、关键设计决策

### 决策 1：远端配置用脚本 + JSON 快照，不用纯 Web 点击

| 维度 | V1 手工点击 | V2 脚本 + 快照 |
|---|---|---|
| 正确性 | 依赖注意力，易漏项（当前 `spring-ai-ali` 截断项就是漏项产物） | 参数固定，漏项被快照暴露 |
| 稳定性 | 每次结果可能不同 | 幂等，重复执行结果一致 |
| 复杂度 | 低 | 中，需维护脚本 |
| 可审计 | 无记录，换人即失传 | JSON 进版本库，可 diff |

**选择 V2**。适用边界：需要被外部理解和接手的仓库；一次性实验仓库不值得。

### 决策 2：CLI 为唯一权威验收路径，Desktop 只做中文映射

三套入口（Git / Desktop / gh）三套记忆，是过去工单里「冲突后直接强推」一类事故的根因。
统一为：**Git 管底层、gh 为权威、Desktop 只保证看得懂菜单**。
文档给两条路径（GUI 用户也要能用），但只把 CLI 标为验收路径。

### 决策 3：中文化分三层，只翻中间层

| 层 | 例子 | 翻不翻 |
|---|---|---|
| 内容层 | README 正文、CONTRIBUTING、SECURITY、PR 模板 | **全翻** |
| 约定层的值 | Issue Form 的 `name`/`description`/`title`/`labels`/选项文本、标签名 | **翻值不翻键** |
| 平台层 | Topics、Actions 关键字、API 字段名、Issue Form 的 YAML 键名 | **绝对不翻** |

翻平台层的后果是可验证的：中文表单提交失败、标签重复、自动化脚本读不到字段。

### 决策 4：规则先立最小集，不做全仓库审批矩阵

单人维护仓库强开 code owner review 会**自锁**（自己的 PR 也合不进去）。
选择：`main` 最小保护（禁强推/禁删除/要 PR/要状态检查）+ tag 保护（`ch*` `v*` 禁删禁移）
+ CODEOWNERS 只卡 `.github/`、`scripts/`、`docs/github-ops/` 三条关键路径。
全员审批矩阵的成本是每月多烧至少 2 小时且无法回本。

### 决策 5：安全治理按「可见性 → 检测 → 修复 → 阻断」顺序开

先确保依赖图能解析，再开 Dependabot alerts / security updates，再收紧 Actions 权限，
最后评估 Secret scanning / push protection。
**顺序不可颠倒的例外是密钥泄漏处置**：先吊销轮换凭证，再清理历史——清理历史不能让凭证失效。

### 决策 6：Traffic 只有 14 天窗口，所以第一次采集就必须落盘

Insights 是看板不是账本。缺落盘层就不构成可追溯的复盘系统：三个月后回看只剩「好像涨过一批人」。
因此第 12 保的第一动作是采集落盘，第二动作才是分析。

### 决策 7：Dashboard/自动化全部走 PR，不直推 main

第 08 保给 `main` 上了「必须经 PR 合并」，第 13 保的周归档工作流也据此走 PR 或本地手动触发，
避免出现「规则刚立、机器人自己破」的尴尬。

## 三、幂等与失败处理约定

| 场景 | 约定 |
|---|---|
| 标签创建 | `gh label create --force`，清单 `docs/github-ops/labels.txt` 为真源，脚本先读后建 |
| Topics 写入 | `gh api --method PUT repos/{owner}/{repo}/topics` 为整体替换语义，天然幂等 |
| 作战盘字段 | 先 `field-list` 查重再建，缺字段才补，不重复建同名字段 |
| 规则集 | `gh api` 写入后立即读回并导出 JSON 快照；重复执行以「读回一致」为通过 |
| `gh` 子命令不稳（如 discussion） | 用 GraphQL 兜底；写操作走 Web 人工，脚本只做只读巡检 |
| 无权限（非 admin） | 脚本以非零退出并打印「缺少 X 权限，请改用 Web 手册路径」，不静默失败 |
| 采集无数据 | 标注为「缺」并写原因，不产出空文件冒充成功 |

## 四、分支与 PR 策略

- 分支命名：`githubops/NN-<主题>`（与教程口径一致）。**本次施工采用「一保一提交、一保一验收」的合并 PR 模式**：
  全部十三保落在 `githubops/13-open-source-operations` 一条分支上，每保一个独立提交（`docs(ghNN): ...`），
  由一个 PR 一次合并。理由：单人维护仓库若为每保开一条 PR，会产出十三个「自己审自己」的空评审，
  与第 05 保「不做无意义强制审批」的判断矛盾。教程原始口径的「一保一 PR」在第 06 保文档里如实保留并说明差异。
  分支命名规则本身不变，后续若要细分仍按 `githubops/NN-<主题>` 开。
- 提交前缀：`docs(ghNN): ...` / `chore(ghNN): ...` / `feat(ghNN): ...`。
- 合并：`gh pr merge --squash --delete-branch`。回滚：`gh pr revert <编号>` 或 `git revert <sha>`。
- 每保 PR 正文按第 06 保模板填写，证据（命令输出）贴在 PR 里并同时落到 `docs/github-ops/NN-*.md`。

## 五、与既有仓库资产的对接

| 既有资产 | 对接方式 |
|---|---|
| `.github/workflows/ci.yml` | 保留既有 `build` job 与架构图门禁，只补顶层 `permissions` 与 `concurrency` 声明 |
| `.github/ISSUE_TEMPLATE/chapter.md` | 保留（章节连载用途），新增三份 Issue Form 并列存在；`config.yml` 只关空白 Issue |
| `.github/pull_request_template.md` | 按第 06 保定稿覆盖，保留原有中文风格与 Checklist 精神 |
| `docs/CI-CD配置清单.md` | 不改内容；第 08 保文档链接到它，避免两处口径 |
| `ch01`–`ch18` tag | 不动；第 05 保 ruleset 覆盖 `ch*`，第 10 保文档说明语义 |
| `scripts/check-diagrams.py` 等 | 不改；新增的 `gh-*.sh` 脚本与既有 `*.py`/`*.ps1` 命名空间区分 |
| `openspec/changes/ch19-digital-human-upgrade/` | 不动；本变更独立目录 `github-ops-13/` |
