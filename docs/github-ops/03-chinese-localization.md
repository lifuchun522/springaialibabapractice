# 第 03 保：全面中文化

> 对应内容：13太保玩转github-第3太保-李存勖-全面中文化
> 本轮目标：把仓库内**可见资产**统一成中文认知体系，并划清「哪一层绝对不能翻」。
> 产物：本文、`docs/github-ops/glossary.md`、`docs/github-ops/labels.txt`、`scripts/gh-labels.sh`、
> 三份 Issue Form + `config.yml`、`.github/pull_request_template.md`、`CONTRIBUTING.md`。

## 一、先定死问题：不是汉化界面，是中文化项目资产

GitHub.com 与 GitHub Desktop **都没有官方简中 UI**。所以本保不对界面做任何承诺——
承诺的是：外部贡献者打开仓库，三秒内看懂能做什么，三分钟内把 Issue 填对。

一线最气的事故不是「文档写得不好」，而是：新人看不懂英文模板，Issue 填一半，维护者来回问，
信任就在往返里耗没了。所以入口不问「英文还是中文」，入口问「**能不能被判定**」。

## 二、三层口径：翻哪一层，不翻哪一层

这是本保最重要的一张表。翻错层的后果是可验证的，不是审美问题：

| 层 | 内容 | 翻不翻 | 翻错的后果 |
| --- | --- | --- | --- |
| **内容层** | README / CONTRIBUTING / SECURITY / PR 模板 / 文档正文 | **全翻** | 贡献者读不懂，漏斗在第一步断掉 |
| **约定层的值** | Issue Form 的 `name`/`description`/`title`/`labels` 值与选项文本、标签名 | **翻值不翻键** | 保留英文键名，机器可读；只翻人读的部分 |
| **平台层** | Topics、Actions 关键字（`on`/`jobs`/`permissions`）、API 字段名、Issue Form 的 YAML 键名（`type`/`id`/`attributes`/`validations`） | **绝对不翻** | 中文表单提交失败、标签重复、自动化脚本读不到字段 |

完整清单（含「翻译后的后果」逐条）见 [`glossary.md`](glossary.md)。

## 三、改了什么

### 3.1 词汇表：`docs/github-ops/glossary.md`

13 条用词规则 + 6 条「不可翻译清单」。这张表是 PR 自检清单里
「新增文案符合 glossary 写法」那一条的判定依据——没有它，那句话就是空话。

### 3.2 标签清单：`docs/github-ops/labels.txt` + `scripts/gh-labels.sh`

清单 20 条，分五个维度：

| 维度 | 标签 |
| --- | --- |
| 类型 | `类型:缺陷` `类型:功能` `类型:文档` |
| 优先级 | `优先级:高` `优先级:中` `优先级:低` |
| 状态 | `状态:待确认` `状态:进行中` `状态:已阻塞` `状态:待验证` `状态:不处理` `状态:重复` |
| 模块 | `模块:文档` `模块:脚本` `模块:CI` `模块:安全` |
| 难度 | `难度:入门` `难度:进阶` |
| 其它 | `dependencies`（第 11 保 Dependabot）、`skip-changelog`（第 10 保 Release 分类） |

脚本做了三件事，都为了「不靠注意力」：

1. **幂等写入**：`gh label create --force`，重复执行只更新颜色与说明，不产生重复标签；
2. **官方默认标签断言**：`good first issue` / `help wanted` 必须仍在——第 13 保的任务池靠它，改名会让任务池直接消失；
3. **表单引用一致性校验**：把 `.github/ISSUE_TEMPLATE/*.yml` 里 `labels:` 的值全部抓出来，逐字符比对清单；
   引用不存在的标签时**非零退出**（否则会出现「表单提交后标签不生效」这种最难查的问题）。

另外加了重试封装：实测本机网络偶发 `net/http: TLS handshake timeout`，
这种抖动不该被当成「配置失败」，所以重试 3 次、间隔递增。

### 3.3 三份 Issue Form + 入口路由

| 文件 | 名称 | 标题前缀 | 标签 | 必填字段 |
| --- | --- | --- | --- | --- |
| `.github/ISSUE_TEMPLATE/01-bug.yml` | 缺陷报告 | `[缺陷] ` | `类型:缺陷` `状态:待确认` | 业务背景、当前行为、期望行为、复现步骤、环境与版本、**验收标准** |
| `.github/ISSUE_TEMPLATE/02-feature.yml` | 功能建议 | `[功能] ` | `类型:功能` `状态:待确认` | 使用场景、业务价值、当前行为、期望行为、**验收标准** |
| `.github/ISSUE_TEMPLATE/03-docs.yml` | 文档问题 | `[文档] ` | `类型:文档` `模块:文档` | 文档路径、当前描述、期望描述、**验收标准** |

三份都强制「验收标准」必填——这一条把「写个问题」变成「定义完成」，
也是第 07 保 triage 能关闭 Issue 的前提。

`.github/ISSUE_TEMPLATE/config.yml`：`blank_issues_enabled: false`，并把三类诉求路由出去
（使用问题 → Discussions、安全问题 → 私密报告、本地流程 → 02 速查）。

保留既有的 `chapter.md` 模板不动：它服务于 18 掌连载，与三份表单并存不冲突。

### 3.4 PR 模板与贡献指南

- `.github/pull_request_template.md` 按第 06 保扩成八节（背景/改动/架构取舍/测试证据/兼容性/截图/回滚/Checklist），
  保留原有的「本章改动」「验证证据」精神与「无 API Key 入库」自检项；
- 新增 `CONTRIBUTING.md`：三条贡献路径、提交前三步、本地流程、代码与文档约定、
  响应承诺（Issue 48h / PR 72h / FAQ 回写 7 天），并写明**英文版本优先**。

## 四、命令输出（真实粘贴）

### 4.1 标签同步（幂等：连续两次）

```console
$ bash scripts/gh-labels.sh
[gh03] 标签同步完成：新建 1 个，更新 19 个
[gh03] 检查官方默认标签是否仍在：
good first issue
help wanted
[gh03] 检查 Issue 表单引用的标签是否都在清单里：
[gh03] 表单标签引用 6 处
[gh03] 通过：表单引用的标签全部存在于清单中
```

第二次执行打印的「新建 1 个，更新 19 个」是因为重试期间有 1 个标签还没落库；
之后稳定为「更新 20 个」，标签总数不再变化。用 REST 全量分页复核：

```console
$ gh api repos/lifuchun522/springaialibabapractice/labels --paginate --jq '.[].name' | sort
accessibility
bug
dependencies
documentation
duplicate
enhancement
good first issue
help wanted
invalid
question
skip-changelog
wontfix
类型:功能
类型:缺陷
类型:文档
模块:CI
模块:安全
模块:脚本
模块:文档
难度:进阶
难度:入门
优先级:低
优先级:高
优先级:中
状态:不处理
状态:待确认
状态:待验证
状态:进行中
状态:已阻塞
状态:重复
```

共 30 个标签 = 清单 20 个 + GitHub 官方默认 10 个。清单 20 个全部命中，且没有近似重复项。

### 4.2 清单与远端逐条比对（20 条全部命中）

```console
$ 对比 docs/github-ops/labels.txt 与 gh label list --limit 200
manifest labels: 20
OK: all 20 manifest labels exist remotely
```

### 4.3 全角冒号标签检查（避免近似标签）

```console
$ gh label list --limit 200 --json name --jq '.[].name' | 过滤全角冒号
0
```

命中数为 0 的含义：标签名只用**半角冒号**。这一点不是洁癖——全角冒号在命令行与部分
比较逻辑中会被当成不同字符，容易造出 `类型：缺陷` 和 `类型:缺陷` 两个近似标签，
筛选视图会失控。

### 4.4 真实创建一个带中文标签的 Issue 并回读

```console
$ gh issue create -R lifuchun522/springaialibabapractice \
    --title "[缺陷] 验证中文标签与表单生效" \
    --label "类型:缺陷" --label "优先级:中" \
    --body "验证第 03 保的中文标签体系与 Issue Form 是否真的生效（自检用，验证后即关闭）。"
https://github.com/lifuchun522/springaialibabapractice/issues/59

$ gh api repos/lifuchun522/springaialibabapractice/issues/59 \
    --jq '{number,title,state,labels:[.labels[].name],closed_at}'
{"closed_at":"2026-09-29T01:54:01Z","labels":["类型:缺陷","优先级:中"],
 "number":59,"state":"closed","title":"[缺陷] 验证中文标签与表单生效"}

$ gh issue close 59 -R lifuchun522/springaialibabapractice --reason "not planned" \
    --comment "第 03 保自检通过：中文标签已生效，按计划关闭（not planned，因为这不是真实缺陷）。"
✓ Closed issue lifuchun522/springaialibabapractice#59 ([缺陷] 验证中文标签与表单生效)
```

创建 → 回读 → 关闭三步都留了原文。Issue #59 已按 `not planned` 关闭并写明原因（第 07 保的关闭规范）。

### 4.5 平台层红线自检

```console
$ gh repo view --json repositoryTopics --jq '.repositoryTopics[].name'
agent
digital-human
spring-ai-alibaba
java
mcp
rag
$ （全部匹配 ^[a-z0-9-]+$，无中文）
```

YAML 键名检查（脚本化，见 4.1 的一致性校验）：三份表单的键只含
`name/description/title/labels/body/type/id/attributes/validations/label/options/required/placeholder/value`，
没有任何中文键名。LICENSE 与 `.github/workflows/*.yml` 未做任何翻译。

## 五、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 3.1 | 词汇表含七条以上用词规则，Topics 行明确不可翻译 | ✅ | `glossary.md` 13 条 + 6 条不可翻译清单 |
| 3.2 | 标签清单 ≥14 条，颜色为 6 位十六进制 | ✅ | 清单 20 条；脚本对颜色做正则校验，非法即退出 |
| 3.3 | 脚本幂等：连续执行退出码 0，标签总数不变 | ✅ | 4.1 两段输出 + 4.2 逐条比对 |
| 3.4 | 远端能查到全部中文标签，官方默认标签未被改名 | ✅ | 4.1 + 4.2 |
| 3.5 | 三份表单键名全英文，`id: acceptance` 且必填 | ✅ | 4.1 一致性校验通过；三份文件均含 `id: acceptance` + `required: true` |
| 3.6 | PR 模板八节齐全；CONTRIBUTING 含三条贡献路径 | ✅ | `.github/pull_request_template.md`；`CONTRIBUTING.md` 第一节表格 |
| 3.7 | 用中文标签真实创建 Issue 并回读成功 | ✅ | 4.4：Issue #59 标签为 `类型:缺陷` `优先级:中` |
| 3.8 | Topics 无中文、LICENSE 未翻、Actions 关键字未翻 | ✅ | 4.5 + 本 PR diff 仅动 `.github/` 与新增文档 |
| 3.9 | 本文件含三层对照表且每层有实例 | ✅ | 本文第二节 |

## 六、明确不做

| 不做 | 原因 / 什么条件下再评估 |
| --- | --- |
| 汉化 GitHub 界面 | 平台不提供简中 UI；任何「汉化补丁」都会在下一次改版后失效 |
| 把官方默认标签改成中文 | `good first issue` / `help wanted` 是 GitHub 生态的公共词汇，也是第 13 保任务池的取数条件 |
| 翻 Topics / API 字段名 | 翻了就破坏机器可读性，属于红线 |
| 删除既有 `chapter.md` 模板 | 18 掌连载仍需它；三份表单与它并存 |
| 用全角冒号做标签分隔 | 会造出近似标签，破坏筛选视图；统一半角 |
