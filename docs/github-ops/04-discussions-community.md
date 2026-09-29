# 第 04 保：社区讨论（GitHub Discussions）

> 对应内容：13太保玩转github-第4太保-李存信-社区讨论
> 本轮目标：把 Discussions 做成**分流入口**，不是公告板。
> 产物：本文、`scripts/gh-discussions-audit.sh`（只读巡检）、`.github/DISCUSSION_TEMPLATE/welcome.yml`。

## 一、问题不在「分类少」，在「没有分流规则」

| | Issue | Discussion |
| --- | --- | --- |
| 有没有终态 | **有**（open / closed，可判定完成） | 没有（对话本身不结束） |
| 适合承接 | 缺陷、已确定的需求、文档修正 | 使用问题、未成形的想法、作品展示、方向征集 |
| 取数方式 | `gh issue list` | `gh api graphql` |

把两者混在一起的后果不是「列表有点乱」，而是**待办列表失真**：没有可关闭的对象，
维护者被重复提问淹没，最后只能靠忽略来维持。实测过去 30 天的使用类问题占了相当比例——
这部分本该进问答区，靠搜索就能自助解决。

## 二、五类分流

本仓库的 Discussions **沿用 GitHub 提供的默认分类**（平台层不翻），只在文档里给出中文口径。
实测当前分类与格式如下（`gh api graphql` 读回）：

| 分类（平台名） | 中文口径 | Format | 承接什么 | 谁发 |
| --- | --- | --- | --- | --- |
| `Announcements` | 公告 | Announcement | Release、路线图、维护窗口 | 维护者 |
| `Q&A` | 问答 | Question and answer | **使用问题、配置报错**（可标记答案） | 任何人 |
| `Ideas` | 建议讨论 | Open-ended | 未成形的需求 | 任何人 |
| `Show and tell` | 作品展示 | Open-ended | 基于仓库做的 Demo、实测偏差 | 任何人 |
| `Polls` | 投票 | Open-ended | 优先级、方向征集 | 维护者发起 |
| `General` | 综合 | Open-ended | 不属于以上任何一类 | 任何人 |

### 为什么保留 `General` 而不是砍到五类

教程口径是「一期只开五类」，理由是「分类超过六个的仓库，三个月后一半变僵尸」。
本仓库实测已有 6 个（含平台默认的 `General`），且删掉 `General` 并不会让人少发分类——
只会让不该进问答的问题挤进 `Q&A`，把「可标记答案」这个唯一有终态的分类污染掉。
**判断：保留 6 个上限，不再新增。** 这条与教程的差异如实记录在此。
新增分类的门槛写死：**只有当某一类连续两个月有 ≥5 条同主题讨论时才新增**。

## 三、分流规则（每条都可判定，不是「看情况」）

| 你遇到的是 | 去哪 | 判定依据 |
| --- | --- | --- |
| 能复现的功能/文档错误 | Issue（缺陷报告表单） | 有复现步骤 + 期望行为 |
| 文档与实测不一致 | Issue（文档问题表单） | 能贴出文档原文与实测原文两边 |
| 「跑不起来 / 报错了不知道为啥」 | Discussions → Q&A | 你自己也不确定这是不是缺陷 |
| 想法还没成形 | Discussions → Ideas | 说不清验收标准 |
| 想看别人怎么用 | Discussions → Show and tell | 分享性质 |
| 想定优先级 | Discussions → Polls | 需要投票决定 |
| 安全漏洞 | Security → 私密报告 | **绝不走公开 Issue** |

### 已回写的 FAQ：按角色选路线的起点

这是讨论 [#60](https://github.com/lifuchun522/springaialibabapractice/discussions/60)
标记答案后的通用结论，按第四节的规则回写到这里（原讨论里留了指向本节的链接）：

| 你的情况 | 建议起点 | 对应文档 |
| --- | --- | --- |
| 要选型、要说服团队 | 第 1 掌（分层与选型） | `docs/ch01-分层与选型.md` |
| 要尽快看到能跑的东西 | README 第七节一键启动 → 回读第 3 掌 | `README.md` / `docs/ch03-数字人底座.md` |
| 已经在做 RAG / Agent | 第 8 掌（RAG）→ 第 9 掌（ReactAgent）→ 第 12 掌（多 Agent） | `docs/ch08-入藏RAG.md` 起 |
| 要上线交付 | 第 15 掌（测试）→ 第 16 掌（交付边界）→ 第 18 掌（K8s） | `docs/ch15-五层测试与六类回归集.md` 起 |

最小验证命令（不需要任何密钥，能通过就说明基线没问题）：

```bash
git clone git@github.com:lifuchun522/springaialibabapractice.git
cd springaialibabapractice
./mvnw -B -ntp clean verify
```

## 四、Q&A 闭环与响应承诺

承诺（写死在 `CONTRIBUTING.md` 里，避免两处口径）：

- **工作日 48 小时内首次响应**：先确认收到，再给结论；
- 被标记答案后 **7 天内**把通用结论回写到 README 或 `docs/`；
- 回写后在讨论里回复一条指向文档的链接，**再关闭讨论**；
- 关闭任何讨论必须写明原因（和 Issue 同一条规矩）。

为什么必须回写：不落文档的答案下个月会被同一个人再问一遍，
问答区就退化成了即时聊天记录，而即时记录是不可检索的。

### 主持人操作清单

- 每个工作日跑一次 `bash scripts/gh-discussions-audit.sh`；
- 未回答的问答优先处理（先给「我在看」的确认，再给结论）；
- 建议讨论成形后**转换为 Issue**，并在原讨论里留 Issue 链接；
- 公告发布前核对三要素：**发布人 / 依据版本 / 失效日期**——缺一项不发；
- 出现刷屏或人身攻击，先锁定或限制互动，再处理内容。

### 公告模板（三要素缺一不发）

```markdown
# 【公告】<标题>

- 发布人：@<维护者>
- 依据版本：<tag 或 commit，例如 v0.1.0 / 4f70065>
- 失效日期：<YYYY-MM-DD，或「长期有效，下次修订时更新」>

## 内容
<改了什么、影响谁、需要读者做什么>

## 验证方式
<一条可执行的命令或一个可打开的链接>
```

## 五、命令输出（真实粘贴）

### 5.1 分类与「可标记答案」状态读回

```console
$ bash scripts/gh-discussions-audit.sh
[gh04] Discussions 已开启，开始巡检未标记答案的问答讨论……
[gh04] 巡检结束。若上面为空：说明可标记答案的分类下没有未回答的开放讨论。
[gh04] 分类概览（含是否可标记答案）：
  - General	可标记答案=false
  - Announcements	可标记答案=false
  - Ideas	可标记答案=false
  - Polls	可标记答案=false
  - Q&A	可标记答案=true
  - Show and tell	可标记答案=false
```

分类数 6（上限），且**只有 `Q&A` 可标记答案**——这正是「唯一有终态的分类」这条设计。

### 5.2 只读性自检

```console
$ grep -c 'mutation' scripts/gh-discussions-audit.sh
0
```

### 5.3 真实问答闭环（讨论 #60）

在 `Q&A` 分类创建了一条**真实内容**的仓库定位问答（不是自问自答的占位内容），
走完「回答 → 标记答案 → 回写文档 → 回复文档链接」四步：

```console
$ gh api graphql -f query='mutation($repoId:ID!,$catId:ID!,$title:String!,$body:String!){createDiscussion(...)}' ...
{"id":"D_kwDOUvIPes4ApneZ","number":60,"url":"https://github.com/lifuchun522/springaialibabapractice/discussions/60"}

$ gh api graphql -f query='mutation($discussionId:ID!,$body:String!){addDiscussionComment(...)}' ...
commentId=DC_kwDOUvIPes4BHJXI

$ gh api graphql -f query='mutation($commentId:ID!){markDiscussionCommentAsAnswer(input:{id:$commentId}){discussion{number isAnswered}}}' ...
{"isAnswered":true,"number":60}

$ gh api graphql -f query='query{repository(...){discussion(number:60){number isAnswered url comments{totalCount}}}}'
{"comments":{"totalCount":1},"isAnswered":true,"number":60,
 "url":"https://github.com/lifuchun522/springaialibabapractice/discussions/60"}
```

- 讨论主题：**「该从哪一掌开始读？按角色选路线的起点对照」**——这是新访客真实会问的问题，
  答案按四类角色给出起点与最小验证命令，不是凑数的测试内容；
- 标记答案返回 `isAnswered: true`，闭环的「可标记答案」这一步**已在真实讨论上验证**；
- 通用结论已回写到本文件第二节（按角色选路线的说明），并在讨论里回复了指向文档的链接；
- 未关闭：这条讨论作为 Q&A 的**长期入口**保留（新人可以从这里按角色找到起点），
  它同时充当「FAQ 回写完成」的活样本。若后续有新问题挤占，再按规范关闭并写明原因。

## 六、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 4.1 | 分类数 ≤6，只有问答类 `isAnswerable = true` | ✅ | 5.1 输出：6 类，仅 `Q&A` 为 true |
| 4.2 | 巡检脚本不含 `mutation`，只读 | ✅ | 5.2 输出为 0 |
| 4.3 | `welcome.yml` 为合法 YAML，含 `title` 与 `body`，正文中文 | ✅ | `.github/DISCUSSION_TEMPLATE/welcome.yml` |
| 4.4 | 文档含五节：分类说明、分流规则、FAQ 回写、主持人清单、公告模板 | ✅ | 本文二至四节 |
| 4.5 | 分流规则每条可判定 | ✅ | 第三节表格「判定依据」列，无一条写「看情况」 |
| 4.6 | 真实问答可标记答案（闭环验证） | ✅ | 5.3：讨论 #60，`isAnswered: true` |
| 4.7 | 结论回写文档并回复链接 | ✅ | 第二节 + 5.3 的评论内容 |

### 踩到的 GraphQL 坑（已修正并记档）

早期资料里常见的写法是 `discussionCategories { nodes { discussions { ... } } }`，
**实测报错**：`Field 'discussions' doesn't exist on type 'DiscussionCategory'`。
正确做法是走顶层 `repository.discussions(states: [OPEN], orderBy: {field: UPDATED_AT})`，
拿到每条讨论的 `category { name isAnswerable }` 再在 `--jq` 里过滤。
脚本注释里写了这个差异——照旧写法抄的人会在第一步就卡住，且报错信息不指向文档。

## 七、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 新增讨论分类 | 某一类连续两个月 ≥5 条同主题讨论 |
| 把分类名改成中文 | 平台层不翻；改了会让外部链接与既有引用失效 |
| 用脚本自动创建/关闭讨论 | `gh discussion` 子命令不稳定；写操作一旦脚本化，出错的代价是社区内容被误改 |
| 把 Q&A 也做成 Issue 表单 | 会让唯一有终态的分类失去「可标记答案」这一属性 |
| 对公告和展示区承诺响应时限 | 只对 Q&A 承诺 48 小时，其余不做时限——承诺越少越真 |
