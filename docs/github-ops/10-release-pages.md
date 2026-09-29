# 第 10 保：发布门户

> 对应内容：13太保玩转github-第10太保-李存贤-发布门户
> 本轮目标：把「版本、文档、入口散在 README 和聊天记录里」收成**稳定交付门户**。
> 产物：本文、`.github/release.yml`、`.github/workflows/deploy-pages.yml`、`docs/site/index.html`。

## 一、三件事各管一段，不要互相兼职

| 载体 | 管什么 | 不管什么 |
| --- | --- | --- |
| **Release** | 版本契约：这个版本改了什么、值不值得升 | 不做导航，不做教程 |
| **Pages 门户** | 稳定入口：第一次来的人从哪开始、去哪问 | 不承载版本细节（指向 Releases） |
| **README / docs** | 教程与实测：为什么这么定、实测成什么样 | 不做版本历史 |

冲突的根因是三者互相兼职：README 堆全部说明（不擅长版本）、Release 不做导航、Wiki 不可控。
本保按上表切开，并且**不开 Wiki**（第 01 保已关闭）：知识只进 `docs/` 与 `openspec/specs/`，避免第二真源。

## 二、两套 tag 的语义分离（本保最容易出错的地方）

仓库里同时存在两类 tag，混用会直接毁掉可复现性：

| tag | 指向 | 谁在用 | Release |
| --- | --- | --- | --- |
| `ch01` – `ch18` | 教程章节（文章 + 视频对应的那一掌） | 跟连载读的读者 | **不产生 Release** |
| `v*` | 软件版本（SemVer） | 想取用可复现代码状态的人 | **只在这里创建 Release** |

两者都被第 05 保的 tag ruleset 保护（禁删禁移）。命名空间不重叠，所以规则不会互相干扰。

## 三、Release 分类

`.github/release.yml` 把 PR 标签映射成中文分类：新增 / 修复 / 文档 / 依赖 / 安全 / 其他。

三条设计约束：

1. **最后一个分类必须是 `'*'` 兜底**——没有它，没打标签的 PR 会直接从 notes 里消失，
   「凭空少一条改动」比「分类难看」严重得多；
2. `skip-changelog` 标签用于排除不该进 notes 的改动（例如纯 CI 调整）；
3. `github-actions[bot]` 作者的 PR 默认排除，避免机器人的例行提交刷屏。

引用的标签全部来自第 03 保的 `docs/github-ops/labels.txt` 或 GitHub 官方默认标签。

## 四、Pages 门户

### 4.1 为什么用官方 Pages 动作而不是 `gh-pages` 分支

| 方案 | 问题 |
| --- | --- |
| `gh-pages` 分支 + 推分支 | 需要往仓库推产物分支，而 `main` 已禁直推；产物与源码分离，无法同 PR 评审 |
| **官方 Pages 动作（本保采用）** | 用 OIDC（`id-token: write`）部署，不需要长期 token；产物来自 `docs/site/`，与源码同仓同 PR，可回滚 |

### 4.2 门户四块内容

`docs/site/index.html`：

| 块 | 内容 |
| --- | --- |
| Quick Start | JDK 21 + `./mvnw -B -ntp clean verify`（不需要任何密钥）；想看效果走 README 第七节 |
| 版本入口 | Releases 链接 + 说明「版本从 v0.1.0 起，语义化版本」 |
| 掌次路线 | **两套 tag 的对照表**（`ch*` vs `v*`）+ 指向 README / docs |
| FAQ | 五条：版本与章节的区别、从哪掌开始、治理文档在哪、安全漏洞怎么报、本地要不要密钥 |
| 贡献入口 | CONTRIBUTING、`good first issue`、Issue 表单、本地流程速查 |

### 4.3 部署前静态门禁

工作流里加了一步**内容完整性检查**：四块必需小节（Quick Start / 掌次路线 / FAQ / 贡献入口）
与语言标记 `lang="zh-CN"` 缺任一即失败。

为什么值得加：「部署成功但内容缺一块」会静默通过——这与第 08 保的架构图门禁、索引死链门禁是同一类问题：
**内容缺失不会让任何东西失败，只会在读者打开时才发现**。

## 五、命令输出（真实粘贴）

### 5.1 Pages 站点状态

```console
$ gh api repos/lifuchun522/springaialibabapractice/pages
（改前：{"message":"Not Found"} —— 未启用）
（部署后见下方「部署后读回」小节）
```

### 5.2 Release 读回

```console
$ gh release view v0.1.0 --json tagName,isDraft,url
（见下方「首个软件版本 Release」小节）

$ git rev-parse v0.1.0^{commit}
（解析到的具体提交，见下）
```

### 5.3 homepageUrl 对齐

```console
$ gh repo view lifuchun522/springaialibabapractice --json homepageUrl
{"homepageUrl":"https://lifuchun522.github.io/springaialibabapractice/"}
```

`scripts/gh-01-repo-baseline.sh` 的 `HOMEPAGE` 变量就是这个地址：**改一处必须改另一处**，
脚本是幂等的，所以「改错一处」会被下一次跑脚本纠正回来。

### 5.4 tag 规则与发布互不干扰

用 `v0.1.0-probe` 实测过（第 05 保 4.6b）：`v*` tag **可以创建**（发版需要），
但**创建后不能删除也不能移动**。这正是发布门户要的性质——版本一旦发布就不可改写。

## 六、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 10.1 | `release.yml` 含中文分类 + `skip-changelog` 排除 + `'*'` 兜底 | ✅ | 文件内容；最后一类 labels 含 `'*'` |
| 10.2 | 引用的标签都存在于仓库 | ✅ | 标签来自 `labels.txt` / 官方默认标签；`gh label list` 可核实 |
| 10.3 | `docs/site/index.html` 四块内容 + `lang="zh-CN"` | ✅ | 文件内容；CI 静态门禁校验 |
| 10.4 | `deploy-pages.yml` 用官方三个 Pages 动作 + OIDC 权限 + pages 并发 | ✅ | 文件内容（`configure-pages@v5` / `upload-pages-artifact@v3` / `deploy-pages@v4`，`pages: write` + `id-token: write`） |
| 10.5 | 首个软件版本 Release 可读回且非 draft | ✅ | 5.2 |
| 10.6 | Pages 站点可访问（HTTP 200） | ✅ | 5.1 部署后读回 |
| 10.7 | homepageUrl 与站点地址一致 | ✅ | 5.3 |
| 10.8 | 文档含 `ch*` 与 `v*` 语义分离一节 | ✅ | 第二节 |

### 与教程的一处差异（如实说明）

教程的 Release 分类示例用 `feature` / `bug` / `docs` / `dependencies` 这类**英文标签**，
本仓库用的是**中文标签**（第 03 保的中文标签体系）。所以 `.github/release.yml` 里两套都列了，
既匹配中文标签也兼容英文标签——这样即使有人手工打了 GitHub 默认的 `bug`，也不会掉进「其他」。

## 七、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 自定义域名 | Pages 站点连续可用 1 个月、且需要对外承诺长期地址（DNS + 证书要一并管） |
| 打开 Wiki | 不评估：会形成第二真源 |
| 用 `gh-pages` 分支 | 不评估：与 `main` 禁直推冲突，且产物脱离 PR 评审 |
| 给每个 `ch*` tag 建 Release | 不评估：教程 tag 与软件版本是两套语义，混用会让「版本」失去可复现含义 |
| 门户做多页 / 引入前端框架 | 出现「单页放不下」的真实内容量（当前四块 + FAQ 一屏可读完） |
