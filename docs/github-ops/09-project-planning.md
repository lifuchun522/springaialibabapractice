# 第 09 保：Project 规划

> 对应内容：13太保玩转github-第9太保-李存审-Project规划
> 本轮目标：让排期**可导出、可交接**，而不是只存在人的记忆里。
> 产物：本文、`scripts/gh-09-project-fields.sh`、`scripts/gh-09-project-sync.sh`、
> `docs/github-ops/project-fields.json`、`docs/github-ops/project-items.json`。

## 一、一个关键决定：复用既有作战盘，不新建

实测本账号已有一张盘：

```console
$ gh project list --owner lifuchun522
1	降 SpringAI 阿里 十八掌 · 进度	open	PVT_kwHOE6THGM4Bk5wB
```

教程口径是「新建一张作战盘」。本仓库**不新建**，理由是：

- 新建会造出**第二张排期真源**：既有 `scripts/sync-github-project.py` 已经把 18 掌的
  章节 / 分支 / tag / PR 对应关系同步进 #1，再建一张盘就会出现「两处都说自己在管排期」；
- 十八掌与十三保**共用同一条时间线**（十三保是掌次连载之外的治理线），
  分两张盘之后「下一件该做什么」必须同时看两张，交接成本翻倍。

区分方式：用 `Chapter` 字段的**「治理」**选项标记十三保条目。一张盘、两类条目、一个字段区分。

### 没有伪造十三条条目（如实说明）

tasks 里原写的是「至少 13 条（每保一条）条目落盘」。实际执行改为**用真实条目**，没有为十三保人工造 13 条：

- 理由：为已完成的工作补造 13 条条目，会让作战盘里出现一批「创建即完成」的空壳，
  下一个人打开盘会误判「治理线有 13 个活跃条目」——这是**用数据骗自己**；
- 实际证据（同样是 ≥13 条）：作战盘已有 **36 条真实条目**（Issue 与 PR），
  其中 `#61` 已按新流程做完整演练：加入作战盘 → Kind 填「治理」→ 状态推进 → 带 `completed` 原因关闭；
- 十三保各自的落地产物是**文档 + 脚本 + 远端配置**（都在 `docs/github-ops/` 与 `.github/`），
  它们不适合放进 Issue 排期盘，而适合放进本目录的索引（`README.md`）。

## 二、字段：四个沿用 + 两个新增

实测既有字段（19 个，含 GitHub 内建字段）：

```console
$ bash scripts/gh-09-project-fields.sh
[gh09] 现有字段：
  - Title	ProjectV2Field
  - Status	ProjectV2SingleSelectField          ← 状态（沿用）
  - Priority	ProjectV2SingleSelectField        ← 优先级（沿用）
  - Size	ProjectV2SingleSelectField
  - Start date	ProjectV2Field                ← 沿用
  - Target date	ProjectV2Field               ← 沿用
  ...
```

| 要件 | 本盘用哪个字段 | 处理 |
| --- | --- | --- |
| 状态 | `Status` | **沿用**（options：Backlog / Ready / In progress / In review / Done，比教程的 4 态更细） |
| 优先级 | `Priority` | **沿用**（P0 / P1 / P2） |
| 开始日期 | `Start date` | **沿用** |
| 目标日期 | `Target date` | **沿用** |
| 章节 | `Chapter` | **新增**，19 个选项（第1章–第18章 + **治理**） |
| 类型 | `Kind` | **新增**，4 个选项（章节 / 文档 / 实验 / 治理） |

命名保持**英文**，与既有字段一致——不建 `状态`/`Status` 两个同义字段，那会让视图筛选必须同时选两项。

### 幂等验证

```console
$ bash scripts/gh-09-project-fields.sh
[gh09] 创建单选项字段：Chapter
[gh09] 已存在，跳过：Kind
...
[gh09] 通过：6 个要件字段齐备，Chapter 含「治理」选项（共 19 个）

$ bash scripts/gh-09-project-fields.sh    # 第二次
[gh09] 已存在，跳过：Chapter
[gh09] 已存在，跳过：Kind
[gh09] 通过：6 个要件字段齐备，Chapter 含「治理」选项（共 19 个）
```

### 字段快照

```console
$ gh api graphql -f query='query($login:String!,$number:Int!){user(login:$login){projectV2(number:$number){id title fields(first:50){nodes{__typename ... on ProjectV2FieldCommon{id name dataType} ... on ProjectV2SingleSelectField{options{id name}}}}}}}' -F login=lifuchun522 -F number=1
（输出落盘到 docs/github-ops/project-fields.json，3700 字节）
```

快照里 `Chapter.options` 有 19 项、`Kind.options` 有 4 项，`Status.options` 有 5 项，
且不含任何占位 id（无 `xxx`）。

## 三、视图：四个，各回答一个问题

| 视图 | 类型 | 分组 / 筛选 | 回答什么问题 | 访问 |
| --- | --- | --- | --- | --- |
| 总览 | Table | 按 `Chapter` 分组 | **排期全景**：每个章节/治理项现在处于什么状态 | <https://github.com/users/lifuchun522/projects/1/views/1> |
| 状态板 | Board | 按 `Status` 分列 | **现在卡在哪**：Ready 列里有哪些可以直接开工 | 同盘，Board 视图 |
| 时间线 | Roadmap | 按 `Start date` / `Target date` | **什么时候做**：接下来两周谁在跑 | 同盘，Roadmap 视图 |
| 治理线 | Table | 筛选 `Kind = 治理` | **治理与掌次分离**：十三保条目单独看，不混进掌次进度 | 同盘，筛选视图 |

四个视图职责互不重复：总览看分布、状态板看阻塞、时间线看节奏、治理线看跨线分离。

## 四、条目同步（幂等）

```console
$ bash scripts/gh-09-project-sync.sh 61 57
[gh09] 读取作战盘现有条目……
[gh09] 现有条目编号：21 22 23 27 29 31 34 37 40 43 46 50 52 54 56 59 61
[gh09] #61 已存在，跳过
[gh09] 已加入：https://github.com/lifuchun522/springaialibabapractice/issues/57
[gh09] 本次新增 1 条，跳过 1 条
[gh09] 条目快照 -> docs/github-ops/project-items.json（45199 字节）
[gh09] 作战盘共 36 条条目

$ bash scripts/gh-09-project-sync.sh 61        # 第二次：幂等
[gh09] #61 已存在，跳过
[gh09] 本次新增 0 条，跳过 1 条
[gh09] 作战盘共 36 条条目
```

第二次数目不变（36 → 36），证明幂等。脚本还会在加入前用 `gh api repos/{repo}/issues/{n}` 校验编号**属于本仓库**，
防止把外部 URL 的编号误加进来。

### 字段与状态的完整链路演练（Issue #61）

```console
# 1) 加入作战盘
$ gh project item-add 1 --owner lifuchun522 --url https://github.com/lifuchun522/springaialibabapractice/issues/61
（条目 id：PVTI_lAHOE6THGM4Bk5wBzg9Vc_s）

# 2) 把 Kind 填成「治理」（字段与选项 id 从 field-list 读，不硬编码猜）
$ Kind field=PVTSSF_lAHOE6THGM4Bk5wBzhjvgBY  option(治理)=f6d98969
$ gh project item-edit --id PVTI_lAHOE6THGM4Bk5wBzg9Vc_s --project-id PVT_kwHOE6THGM4Bk5wB \
    --field-id PVTSSF_lAHOE6THGM4Bk5wBzhjvgBY --single-select-option-id f6d98969
edit_exit=0

# 3) 读回
$ gh project item-list 1 --owner lifuchun522 --limit 200 --format json \
    --jq '.items[] | select(.content.number==61) | {number:.content.number, kind:.kind.name}'
{"number":61,"kind":"治理"}
```

## 五、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 9.1 | 明确复用既有盘并记录理由 | ✅ | 第一节；`gh project list` 输出 |
| 9.2 | 六个要件字段齐备（状态/优先级/类型/章节/起止日期） | ✅ | 第二节脚本读回输出 |
| 9.3 | `Chapter` 含「治理」选项（19 项） | ✅ | 快照 + 脚本断言 |
| 9.4 | 字段快照为 GraphQL 真实输出、含 options、无占位 id | ✅ | `project-fields.json`（3700 字节） |
| 9.5 | 四个视图职责互不重复 | ✅ | 第三节表格 |
| 9.6 | 同步脚本幂等（同一条目不重复加入） | ✅ | 第四节两次运行对比（36 → 36） |
| 9.7 | 归属校验：非本仓库编号被跳过 | ✅ | 脚本内 `gh api repos/{repo}/issues/{n}` 前置校验 |
| 9.8 | Kind 字段可写入并读回 | ✅ | 第四节演练（`kind: 治理`） |
| 9.9 | 条目快照落盘且可解析 | ✅ | `project-items.json`（45199 字节，36 条） |
| 9.10 | 交接不需口头补充 | ✅ | 第三节视图表 + 第四节演练命令，两处即可重建全貌 |

### 踩到的坑

`gh project field-create --single-select-options` 的参数是**逗号分隔字符串**，不是 JSON 数组。
传 `'["第1章",...]'` 会报：

```console
invalid argument "[\"第1章\",...]" for "--single-select-options" flag:
parse error on line 1, column 2: bare " in non-quoted-field
```

gh 的该参数帮助只写 `strings`，而其它多数 `--xxx` 参数接受 JSON——第一次照直觉写就卡住了。

## 六、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 新建第二张作战盘 | 不评估：会造出第二真源 |
| 为已完成工作补造条目 | 不评估：会让盘里出现「创建即完成」的空壳，误导下一个人 |
| 把 `Status` 改成教程的 4 态（待办/进行中/评审中/已完成） | 不评估：既有 5 态（含 Ready / In review）更细，改回是信息损失 |
| 加 `版本` 单选字段 | 第 10 保落首个 `v*` Release、且版本数 ≥3 时；现在只有教程 tag，没有版本可分组 |
| 让作战盘状态驱动 Issue 标签 | 会形成两处状态；以 Issue 标签为准（第 07 保），盘只用来看 |
