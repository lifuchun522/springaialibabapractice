# 第 12 保：数据复盘

> 对应内容：13太保玩转github-第12太保-康君立-数据复盘
> 本轮目标：把只保留 14 天的 Insights 变成**可复现、可对照的月度快照**。
> 产物：本文、`scripts/gh-insights-snapshot.sh`、`scripts/gh-insights-report.sh`、
> `scripts/jq/*.jq`、`docs/github-ops/metrics/`（首月月报 + 九份原始快照）。

## 一、Insights 是看板，不是账本

三条硬事实决定了一切设计：

| 事实 | 后果 |
| --- | --- |
| Traffic（views / clones / referrers / paths）**只保留 14 天** | 不落盘就等于没有历史；三个月后回看只剩「好像涨过一批人」 |
| 按 **UTC 天**聚合 | 与本地日期会差一天，口径必须写明，否则跨月对照会错位 |
| Star 分不清「传播」与「使用」 | 只看 Star 会把「有人转发」误判成「有人在用」 |

所以本保的第一动作是**采集落盘**，第二动作才是分析。顺序反了就是「凭印象决定下一步」。

## 二、采哪九个端点

| 分类 | 端点 | 回答什么问题 |
| --- | --- | --- |
| Traffic（14 天窗口） | `traffic/views` | 有多少人来看 |
| | `traffic/clones` | 有多少人真的把代码拿走了 |
| | `traffic/popular/referrers` | 人是从哪来的（外部文章？搜索？） |
| | `traffic/popular/paths` | 他们看了哪一页（README？文档？） |
| 协作信号 | `releases` | 有没有版本契约 |
| | `issues`（state=all） | 有没有进入结构化问题入口 |
| | `pulls`（state=all） | 有没有真实代码协作 |
| | `contributors` | 有没有第二个贡献者 |
| | `actions/runs` | 门禁健康度（含非代码红灯） |

「访问高但克隆为 0」= README 没能把人送到快速开始；
「克隆有但 Issue/PR 为 0」= 只被当资料下载，没进社区——这正是第 13 保要修的漏斗。

## 三、命令输出（真实粘贴）

### 3.1 首次采集（九份原始快照）

```console
$ bash scripts/gh-insights-snapshot.sh
[gh12] 采集窗口口径：Traffic 仅保留最近 14 天，按 UTC 天聚合（窗口由 GitHub 决定，不可调）
[gh12] Traffic 四端点（14 天窗口）：
[gh12]   ✓ views -> docs/github-ops/metrics/raw/2026-09-views.json (860 字节)
[gh12]   ✓ clones -> docs/github-ops/metrics/raw/2026-09-clones.json (861 字节)
[gh12]   ✓ referrers -> docs/github-ops/metrics/raw/2026-09-referrers.json (3 字节)
[gh12]   ✓ paths -> docs/github-ops/metrics/raw/2026-09-paths.json (3 字节)
[gh12] 协作信号五端点（用于对照流量是否转成了协作）：
[gh12]   ✓ releases -> docs/github-ops/metrics/raw/2026-09-releases.json (3 字节)
[gh12]   ✓ issues -> docs/github-ops/metrics/raw/2026-09-issues.json (288872 字节)
[gh12]   ✓ pulls -> docs/github-ops/metrics/raw/2026-09-pulls.json (632717 字节)
[gh12]   ✓ contributors -> docs/github-ops/metrics/raw/2026-09-contributors.json (985 字节)
[gh12]   ✓ runs -> docs/github-ops/metrics/raw/2026-09-runs.json (1444115 字节)
[gh12] 原始快照 -> docs/github-ops/metrics/raw
```

过程中 `clones` 报了两次 TLS 握手超时，第三次成功——脚本自带重试，且**重试失败时写占位 JSON 注明原因**，
而不是留空文件：空文件会被下游误读成「这个月克隆数为零」。

```json
{"_note":"本端点采集失败（重试 3 次），数据缺失","_endpoint":"...","_month":"2026-09"}
```

「缺」与「零」必须能分开，这是月报能跨月对照的前提。

### 3.2 三个 3 字节文件是什么

`referrers` / `paths` / `releases` 都是 `[]`——3 字节就是空数组。含义分别是：
没有外部来源把流量送进来、没有被访问的具体路径、没有软件版本 Release。
这三条「零」直接写进了月报的「动作」列（见 3.3），而不是当作异常。

### 3.3 月报

```console
$ bash scripts/gh-insights-report.sh
[gh12] 月报 -> docs/github-ops/metrics/2026-09.md（61 行）
```

`docs/github-ops/metrics/2026-09.md` 的「核心指标」与「协作信号」两节：

| 指标 | 值 |
| --- | --- |
| 14 天访问次数 | 0 |
| 独立访客 | 0 |
| 14 天克隆次数 | 0 |
| 独立克隆者 | 0 |
| 累计 Release 数 | 0 |
| Issue 总数（含已关闭） | 59 |
| 开放 Issue 数 | 1 |
| PR 总数（含已合并） | 33 |
| Contributors 数 | 1 |
| 最近 100 次 Actions 运行中失败数（不含 cancelled） | 19 |

### 3.4 与 GitHub 网页端口径交叉核对

```console
$ gh api "repos/lifuchun522/springaialibabapractice/traffic/views" \
    --jq '{window_days: (.views | length), visits: .count, uniques: .uniques}'
{"window_days":14,"visits":0,"uniques":0}
```

`window_days: 14` 是 GitHub 侧固定窗口的直接证据——**不是我们只取了 14 天，而是只有 14 天可给**。

## 四、指标 → 判断 → 动作

月报的第六节必须写出一条明确动作，否则这个月的复盘没有产出。本月的判断（完整版见月报）：

| 观察到的 | 判断 | 动作 |
| --- | --- | --- |
| 访问/克隆全 0，referrers 为空 | 没有外部入口把流量送进来；且此前从未采集，所以这个 0 只覆盖 14 天窗口 | 下月同日再采一次形成序列；补第 13 保的传播回链，让流量有入口可归因 |
| Release 数 0，但有 18 个教程 tag | 只有教程 tag，没有软件版本契约 | 第 10 保落首个 `v*` Release，并把两套 tag 的语义分离写进文档 |
| Issue 59 条 / 开放 1 条 / PR 33 条 | 协作真实发生过，但历史沟通**走的是 PR 而不是 Issue** | 第 07 保把 Issue 入口结构化；下月看使用类问题是否开始进 Discussions |
| Contributors 数 1 | 「访问 → Contributor」这条腿完全没通 | 第 13 保建 ≥3 个带验收标准的 `good first issue` |
| Actions 失败 19/100（约 19%） | 需要区分代码问题与外部抖动（已知两类：mvnw 发行版下载失败、TLS 握手超时） | CI 已缓存 mvnw 发行版；下月看是否降到 10% 以下，否则按失败步骤分类统计 |

## 五、三条口径纪律

1. **指标只作判断输入，不作考核目标。** 把 Star 或访问量当 KPI，最先被牺牲的是文档质量；
2. **长期为零的指标要砍掉对应工作，而不是继续维护。** 一个永远为零的指标说明那件事没人需要；
3. **表头与指标名跨月不变。** 某月缺失就写「缺（原因）」，不删行——删行会让相邻两月无法对照。

## 六、踩到的坑（换仓库会重复遇到）

### 6.1 带查询串的端点不能拆成两个参数

```bash
# 错误：`${extra}` 未加引号，即使为空也会产生一个空参数
gh api "repos/${REPO}/issues" ${extra} --jq '.'      # ↑ 报 accepts 1 arg(s), received 2
```

实测报错 `accepts 1 arg(s), received 2`。正确做法是把查询串写进端点本身：
`gh api "repos/${REPO}/issues?state=all&per_page=100"`。

### 6.2 双引号会被剥掉，含引号的 jq 表达式要落成文件

```bash
# 在 PowerShell 里，或跨 bash 函数传参时：
gh api ... --jq 'select(.state=="open")'   # ← 收到 select(.state==open)
                                           #    jq 报 function not defined: open/0
```

解法：表达式落成 `scripts/jq/open-issues.jq`，用 `--jq "$(cat scripts/jq/open-issues.jq)"` 传。
本仓库有两个这样的文件（`open-issues.jq`、`failed-runs.jq`），注释里写了为什么不能内联。

### 6.3 `length` 的 0 与「命令失败」必须能分开

第一版用 `[ -n "$out" ]` 判断是否成功，结果 `length` 返回的 `"0"`（非空字符串，合法值）
与命令失败（空输出）混在一起。改成显式判断输出是否为数字（`grep -Eq '^[0-9]+$'`）后，
月报里 `releases` 从「缺」变成正确的 `0`。

### 6.4 `cancelled` 不算失败

失败数统计排除了 `cancelled`：第 08 保给 CI 配了 `cancel-in-progress`，
并发取消是正常工作流行为，把它算成失败会让指标长期虚高，复盘时误判 CI 健康度。

## 七、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 12.1 | 九个端点全部落盘，文件名带当月前缀 | ✅ | 3.1 输出 + `metrics/raw/` 九个文件 |
| 12.2 | 每个快照都是有效 JSON | ✅ | 快照由 `--jq '.'` 产出；`views`/`clones` 含 `count`/`uniques`/明细数组 |
| 12.3 | 窗口口径写进脚本与月报 | ✅ | 3.1 首行 + 月报顶部引用块 |
| 12.4 | 月报含四项核心指标 + 原始快照清单 | ✅ | 3.3 + 月报第五节的九条清单 |
| 12.5 | 表头与指标名跨月固定 | ✅ | 月报「口径说明（跨月不变）」小节 |
| 12.6 | 缺失标注为「缺」并给原因 | ✅ | 脚本占位 JSON + `metric()` 的失败说明 |
| 12.7 | 复盘有明确动作 | ✅ | 第四节表格 + 月报第六节 |
| 12.8 | 采集脚本只读（无写 GitHub 状态的操作） | ✅ | 脚本只含 `gh api` GET 与本地写文件 |

## 八、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 把指标做成仪表盘/图表 | 连续三个月有数据、且需要给外部看时；当前表格足够 |
| 每日采集 | Traffic 按天聚合且保留 14 天，每日采并不会多出信息量 |
| 把失败运行数当 KPI | 不评估：外部抖动（TLS/下载）会污染这个数；它只用来发现「门禁是否在退化」 |
| 采集后自动提交到 main | main 有「必须走 PR」规则；第 13 保的周归档工作流据此改为本地手动触发或走 PR |
| 采集协作者个人信息 | 不做：`contributors` 只取数量与公开登录名，不落盘邮箱 |
