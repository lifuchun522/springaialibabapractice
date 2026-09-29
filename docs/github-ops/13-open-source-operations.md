# 第 13 保：开源运营

> 对应内容：13太保玩转github-第13太保-李存孝-开源运营
> 本轮目标：把六个入口串成**一条漏斗**，而不是六个各自独立的开关。
> 产物：本文、`scripts/gh-ops-metrics.sh`、`.github/workflows/gh-ops-weekly.yml`、
> `scripts/gh-13-seed-good-first-task`（任务池）、以及真实任务池 Issue #63–#65。

## 一、为什么「功能齐全」却留不下人

前十二保做的是**工程化**：门面、语言、规则、安全、数据。但 GitHub 的每个能力**默认是独立开关**，
不是一条漏斗。于是出现最典型的失败形态：

> Star 涨了，Clone 有一些，但贡献者长期为零——访问者走到某一步就散了，而没人知道是哪一步。

漏斗是唯一的解释工具：

```
访问 ──► README 理解 ──► Clone/运行 ──► Discussion ──► Issue ──► PR ──► Contributor
  │          │              │              │            │        │         │
  │          │              │              │            │        │         └─ 留下并持续参与
  │          │              │              │            │        └─ 提交代码并被合并
  │          │              │              │            └─ 提出可判定的问题
  │          │              │              └─ 先问「跑不起来」而不是直接报 bug
  │          │              └─ 真的把代码拿走并跑起来
  │          └─ 三秒内看懂能做什么
  └─ 从搜索引擎/外部文章/社区链接进来
```

## 二、七级漏斗的取数方式（每级都有可执行命令）

| 级别 | 取数方式 | 命令 |
| --- | --- | --- |
| 1 访问 | Insights → Traffic，或 `traffic/views` | `gh api repos/lifuchun522/springaialibabapractice/traffic/views` |
| 2 README 理解 | 被访问最多的路径 | `gh api repos/lifuchun522/springaialibabapractice/traffic/popular/paths` |
| 3 Clone/运行 | `traffic/clones` | `gh api repos/lifuchun522/springaialibabapractice/traffic/clones` |
| 4 Discussion | GraphQL 讨论数与未答数 | `gh api graphql -f query='query($o:String!,$n:String!){repository(owner:$o,name:$n){discussions(first:100){totalCount nodes{isAnswered category{isAnswerable}}}}}' -f o=lifuchun522 -f n=springaialibabapractice` |
| 5 Issue | Issue 列表 | `gh issue list -R lifuchun522/springaialibabapractice --state all --limit 100` |
| 6 PR | PR 列表 | `gh pr list -R lifuchun522/springaialibabapractice --state all --limit 100` |
| 7 Contributor | 贡献者页与提交统计 | `gh api repos/lifuchun522/springaialibabapractice/contributors` / `git shortlog -sne` |

**没有一级是「看 Insights」而不给具体端点**——否则下一个人无法复现你的判断。
上面七条汇总成一条命令：`bash scripts/gh-ops-metrics.sh`（见第五节）。

## 三、每月节奏与响应承诺

| 时间 | 做什么 | 命令 / 动作 |
| --- | --- | --- |
| 每月 1 号 | 导出上月指标、写月报草稿 | `bash scripts/gh-insights-snapshot.sh && bash scripts/gh-insights-report.sh` |
| 每月 5 号前 | 准备 3 个带验收标准的小任务，打 `good first issue` | `gh issue list -R <repo> --label 'good first issue' --state open` 若 <3 则补 |
| 每周一 09:00 | 自动归档周指标草稿 | `gh-ops-weekly` 工作流（只产草稿，要归档再开 PR） |
| 每次 Release | Discussions 发公告 + 外部渠道同步 | 公告必须含**发布人 / 依据版本 / 失效日期** |
| 每个工作日 | 巡检未答问答 | `bash scripts/gh-discussions-audit.sh` |

### 响应承诺（写死在 `CONTRIBUTING.md`，避免两处口径）

| 事件 | 承诺 |
| --- | --- |
| 新 Issue | **48 小时**内首次响应（工作日口径） |
| 新 PR | **72 小时**内首次评审 |
| 被标记答案的问答 | **7 天**内把通用结论回写到文档，并在讨论里回复链接后再关闭 |

**只对 Q&A 做时限承诺**，公告和展示区不做——承诺越少越真。

### 首次响应模板

```markdown
感谢反馈，已收到。

我理解的问题是：<用一句话复述，确认没理解偏>。

下一步：<给出一个明确动作，或说明需要你补充什么>。
可以先跑这条命令自查：

```bash
<一条可执行的验证命令>
```

我会在 <时间点> 前给你结论。
```

三要素：**欢迎与感谢 → 明确下一步 → 可执行的验证命令**。
禁止承诺无法兑现的时间点（例如「今天就修好」）。

## 四、任务池：漏斗最后一段的燃料

### 4.1 为什么任务池必须有验收标准

没有任务池 → 贡献者来了不知道能做什么 → 漏斗断在最后一步；
有任务池但任务写不清 → 贡献者做一半卡住 → 维护者反而更累。

所以每个 `good first issue` 必须含三节：**要做什么 / 验收标准 / 改动范围**。
验收标准必须是**可判定**的（能跑命令、能看产物、能读回字段），不能写「优化一下文档」。

### 4.2 本仓库的任务池（真实、可直接开工）

| # | 任务 | 类型 |
| --- | --- | --- |
| [#63](https://github.com/lifuchun522/springaialibabapractice/issues/63) | 英文版 README 首屏与中文版对齐（五段结构） | 文档 |
| [#64](https://github.com/lifuchun522/springaialibabapractice/issues/64) | `check-md.py` 增加非零退出与多文件支持，接进 CI 门禁 | 脚本 |
| [#65](https://github.com/lifuchun522/springaialibabapractice/issues/65) | 给运营漏斗加一键取数脚本（七级现状一张表） | 脚本 |

三个都是**真需要做**的事，不是为凑数造的：

- #63 对应 `README.en.md` 与中文版的**实际漂移**（中文版加了首屏五段，英文版还没有）；
- #64 对应 `scripts/check-md.py` 的**实际缺陷**（发现结构问题也返回 0，接不进 CI）；
- #65 对应本文第二节的**实际缺口**（七级取数没有一键命令，新人要手敲七条）。

每个都指明了预期改动的文件，且**不要求贡献者读完整仓库**。

### 4.3 任务池健康度

```console
$ gh issue list -R lifuchun522/springaialibabapractice --label 'good first issue' --state open --json number,title
count= 3
65 [脚本] 给运营漏斗加一键取数脚本（七级现状一张表）
64 [脚本] check-md.py 增加非零退出与多文件支持，接进 CI 门禁
63 [文档] 英文版 README 首屏与中文版对齐（五段结构）
```

**红线：低于 3 个就要补。** 任务池空了等于告诉来访者「这里没有你能做的事」。

## 五、命令输出（真实粘贴）

### 5.1 周指标归档

```console
$ bash scripts/gh-ops-metrics.sh
[gh13] 归档日期（UTC）：2026-09-29
[gh13] 目录：docs/github-ops/metrics
[gh13] 1/4 访问量与克隆量（Traffic，14 天窗口）
[gh13]   ✓ views.json (860 字节)
[gh13]   ✓ clones.json (861 字节)
[gh13] 2/4 任务池：开放 good first issue
[gh13]   ✓ gfi.json (3 字节)          ← 归档时任务池还是空的（[]），这正是要修的问题
[gh13] 3/4 版本：最近 10 个 Release
[gh13]   ✓ releases.json (3 字节)
[gh13] 4/4 社区：讨论数与未答问答数
[gh13]   ✓ discussions.json (268 字节)
[gh13] 写入完成：
[gh13]   docs/github-ops/metrics/2026-09-29-clones.json
[gh13]   docs/github-ops/metrics/2026-09-29-discussions.json
[gh13]   docs/github-ops/metrics/2026-09-29-gfi.json
[gh13]   docs/github-ops/metrics/2026-09-29-releases.json
[gh13]   docs/github-ops/metrics/2026-09-29-views.json
```

`gfi.json` 是 `[]`（3 字节）——**基线证据**：任务池当时为空，这正是第 4.2 节要修的东西。
修完后同一个端点返回 3 条（见 4.3）。

### 5.2 创建任务池（幂等）

```console
$ bash scripts/gh-13-seed-good-first-issues.sh
[gh13] 已创建：[文档] 英文版 README 首屏与中文版对齐（五段结构）
        https://github.com/lifuchun522/springaialibabapractice/issues/63
[gh13] 已创建：[脚本] check-md.py 增加非零退出与多文件支持，接进 CI 门禁
        https://github.com/lifuchun522/springaialibabapractice/issues/64
[gh13] 已创建：[脚本] 给运营漏斗加一键取数脚本（七级现状一张表）
        https://github.com/lifuchun522/springaialibabapractice/issues/65

$ bash scripts/gh-13-seed-good-first-issues.sh     # 第二次
[gh13] 已存在，跳过：[文档] 英文版 README 首屏与中文版对齐（五段结构）
[gh13] 已存在，跳过：[脚本] check-md.py 增加非零退出与多文件支持，接进 CI 门禁
[gh13] 已存在，跳过：[脚本] 给运营漏斗加一键取数脚本（七级现状一张表）
```

### 5.3 社区出口的现状

```console
$ jq '.' docs/github-ops/metrics/2026-09-29-discussions.json
{"data":{"repository":{"hasDiscussionsEnabled":true,
  "discussions":{"totalCount":1,
    "nodes":[{"number":60,"isAnswered":true,"category":{"name":"Q&A","isAnswerable":true}}]}}}}
```

讨论总数 1、已标记答案 1（第 04 保的闭环样本）。**未答问答为 0**——48 小时承诺当前达标。

### 5.4 周归档工作流与规则的关系（有意差异）

教程让工作流直接 `git commit && git push` 到 main。本仓库**不这么做**：

```yaml
# .github/workflows/gh-ops-weekly.yml 的关键取舍
# 默认只产出草稿（artifact + step summary）；
# 需要归档时手动触发并勾选 commit_draft=true，工作流**开 PR**，不直推 main。
```

理由：第 05 保给 `main` 立了「必须走 PR、必须过 CI、无 bypass」的规则。
如果机器人绕过自己的规则，第 05 保就白立了——**规则刚立、机器人自己破**是最糟的示范。

### 5.5 一次真实的启动即失败（本保踩到的最难的坑）

工作流第一次被触发时，运行**在启动阶段就失败**，而且几乎没有线索：

```console
$ gh run list --workflow=gh-ops-weekly.yml --limit 1 --json databaseId,conclusion,event,headBranch,jobs
{"conclusion":"failure","event":"push","headBranch":"dependabot/maven/...","jobs":[]}

$ gh run view 36513341127 --log-failed
gh: failed to get run log: log not found
```

形态特征（记住这三条，下次一眼认出）：

| 现象 | 含义 |
| --- | --- |
| `jobs: []` | job **根本没建出来**——不是某一步失败，而是工作流整体没被接受 |
| `--log-failed` 报 `log not found` | 没有日志可看，**报错不指向真正原因**，只能逐文件比对 |
| `event=push` 却触发了 `on: schedule/workflow_dispatch` 的工作流 | GitHub 在分支推送时会校验分支上的所有工作流文件，校验不过就是启动失败 |

根因是 `upload-artifact` 的 `path` 写成了：

```yaml
path: docs/github-ops/metrics/*-$(date -u +%Y-%m-%d)-*.json   # ← 错在这里
```

**动作（action）的 `with:` 参数不做 shell 展开**，`$(...)` 不会被求值。
同类第二处：`gh pr create --body "..."` 的正文跨了多行，把 YAML 块标量的缩进搞乱。

修法（PR [#71](https://github.com/lifuchun522/springaialibabapractice/pull/71)）：

1. `path` 改成纯 glob `docs/github-ops/metrics/*.json`；
2. PR 正文改为写临时文件后 `--body-file` 传入；
3. 给 artifact 上传加 `if: always()` 与 `continue-on-error: true`
   ——**归档失败不该让整个采集红灯**（采集本身成功才是关键）；
4. 补一道门禁 `scripts/check-workflows.py` 接进 CI，检查「启动即失败」的最小充分条件：
   顶层 `on`/`jobs`、每个 job 有 `runs-on`、动作参数里没有 `$(`、没有跨行 `--body`。

这是本仓库第三道同类门禁（前两道是架构图一致性与索引死链），共同规律是：
**静默损坏不会让任何东西失败，只会在下一次被人发现时已经晚了。**

### 5.6 修复后重跑：成功，且没有直推 main

```console
$ gh workflow run gh-ops-weekly.yml -f commit_draft=false
https://github.com/lifuchun522/springaialibabapractice/actions/runs/36514140832

$ gh run view 36514140832 --json status,conclusion,jobs --jq '{status,conclusion,jobs:[.jobs[]|{name,status,conclusion}]}'
{"status":"completed","conclusion":"success",
 "jobs":[{"name":"采集运营指标","status":"completed","conclusion":"success"}]}
```

三点结论：

1. **startup_failure 已消失**：`jobs` 不再为空数组，采集步骤正常执行；
2. **未勾选提交时走「只产草稿」分支**，不产生任何提交，因此不会因为「没有新数据」红灯；
3. **即使勾选提交，它开的也是 PR** —— 与第 05 保「main 必须走 PR」一致，
   规则不会被自己的自动化绕过。

## 六、外部传播回链清单

外部文章是**入口**，不是知识仓库。规则：

| 渠道 | 要求 |
| --- | --- |
| 腾讯云开发者社区 | 正文末尾统一回链仓库；核心知识必须回流到 `docs/` |
| CSDN | 同上 |
| 公众号 | 同上；二维码/链接指向仓库而非个人主页 |
| Discussions 公告 | 每次 Release 同步发一条（三要素齐全） |

**核心知识必须回流到 `docs/`**：外部平台随时可能改版或下架，
只把知识放在外部，三个月后自己都找不到原文。

## 七、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 13.1 | 七级漏斗每级都有可执行命令 | ✅ | 第二节表格，七行全部给出端点或命令 |
| 13.2 | 每月节奏三条 + 响应承诺两条齐全 | ✅ | 第三节两张表 |
| 13.3 | 首次响应模板三要素齐全，且禁止不可兑现承诺 | ✅ | 第三节模板 + 其后的三要素说明 |
| 13.4 | `gh-ops-metrics.sh` 按日期戳归档四类数据 | ✅ | 5.1（五份文件，含 views/clones/gfi/releases/discussions） |
| 13.5 | 周工作流无改动不红灯，且不直推 main | ✅ | `git diff --cached --quiet` 兜底 + 开 PR 而非 push（5.4） |
| 13.6 | `good first issue` ≥3 且每个含三节 | ✅ | 4.3 + 4.2 的任务正文（三节齐全） |
| 13.7 | 任务池非空（漏斗最后一段有燃料） | ✅ | 4.3（count=3；基线为空见 5.1） |
| 13.8 | 明确「指标只作判断输入、不作考核目标」与「只追 Star 的代价」 | ✅ | 第八节 + 月报第六节 |
| 13.9 | 外部传播回链清单齐全 | ✅ | 第六节 |

## 八、两条不可动摇的口径

1. **指标只作为判断输入，不作为考核目标。**
   把 Star 或访问量当 KPI，最先被牺牲的一定是文档质量——因为文档不涨 Star；
2. **只追 Star 的代价是维护者被假任务和零响应拖垮。**
   假 `good first issue`（没有验收标准、没人认领）比没有任务池更伤：它证明「这里承诺了但做不到」。

## 九、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 付费推广 | 出现可归因的转化事件（Clone → 运行 → Issue）之后；当前 CAC 算不出来 |
| 在公告与展示区承诺响应时限 | 不评估：只对 Q&A 承诺 48 小时，承诺越少越真 |
| 自动回复机器人应答 Q&A | 出现「同类问题高频重复」的实测数据时；当前讨论总量为 1，上机器人是过度工程 |
| 每周强推指标到钉钉/邮件 | 出现「有人真的会读」的证据时 |
| 把周归档工作流改成直推 main | 不评估：与第 05 保的规则直接冲突 |
