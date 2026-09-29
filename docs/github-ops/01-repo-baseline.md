# 第 01 保：仓库建制

> 对应内容：[13太保玩转github-第1太保-李嗣源-仓库建制](https://cloud.tencent.com/developer/user/10854450)（文章 + 视频）
> 本轮目标：把仓库门面与基础制度一次立起来，并留下**可复现、可 diff** 的基线。
> 产物：`scripts/gh-01-repo-baseline.sh`、`docs/github-ops/01-repo-baseline.json`、本文。

## 一、改前状态（2026-09-29 实测）

```console
$ gh repo view lifuchun522/springaialibabapractice --json name,description,homepageUrl,repositoryTopics,visibility,defaultBranchRef
{"defaultBranchRef":{"name":"main"},
 "description":"spring-ai-alibaba结合数字人项目实战，按照框架能力一步一步实战代码，真实可运行。",
 "homepageUrl":"",
 "repositoryTopics":[{"name":"agent"},{"name":"digital-human"},{"name":"llm"},{"name":"practice"},
                     {"name":"spring-ai-alibaba"},{"name":"spring-ai-ali"}],
 "visibility":"PUBLIC",
 "name":"springaialibabapractice"}
```

| 维度 | 改前 | 判断 |
| --- | --- | --- |
| description | 长句口语化，未点明「中文实战」「数字人示例」 | 关键词入口不清 |
| homepageUrl | **空** | 无稳定入口，外部文章只能回链到仓库首页 |
| repositoryTopics | 6 个，但含 **`spring-ai-ali`**（截断项）、缺 `java`/`rag`/`mcp` | 搜索侧覆盖不足，且有一个错误项 |
| Features | Issues 开 / Discussions 开 / **Wiki 开** / **Projects 开** | 四项全开，但 Wiki 与仓库级 Projects 无任何运营动作 |
| 默认分支 | `main` | 无需改动 |
| 许可证 | Apache-2.0 | 无需改动 |

> 截断项 `spring-ai-ali` 就是「靠注意力手工配置」的典型产物：漏项不会被发现，因为没有快照可比。

## 二、改后状态

| 维度 | 改后 |
| --- | --- |
| description | `Spring AI Alibaba 中文实战与数字人示例：18 掌实战代码、验收证据与运维治理` |
| homepageUrl | `https://lifuchun522.github.io/springaialibabapractice/`（第 10 保的 Pages 门户） |
| repositoryTopics | `spring-ai-alibaba` `java` `agent` `rag` `mcp` `digital-human`（6 个，全小写英文连字符，无截断项） |
| Features | Issues **开** / Discussions **开** / Wiki **关** / Projects **关** |
| 默认分支 | `main` |
| 许可证 | Apache-2.0（不动） |

Features 的取舍依据是**会不会运营**，不是「看起来齐不齐」：

- Issues 开：有三份 Issue Form（第 03/07 保）与首次响应承诺（第 04/13 保）；
- Discussions 开：有五类分流与 48 小时首响承诺（第 04 保）；
- Wiki 关：没有任何运营动作，开了没人管比不开更伤信任；
- 仓库级 Projects 关：规划只用第 09 保的个人作战盘，避免两处排期互相打脸。

半年后若 Discussions 周活稳定，再评估打开仓库级 Projects。

## 三、Web 配置路径（无 admin token 时的手工等价路径）

| 项 | 路径 |
| --- | --- |
| Description / Website / Topics | 仓库首页 About 区右侧齿轮 → Description / Website / Topics |
| Default branch | Settings → General → Default branch |
| Features（Issues / Discussions / Wiki / Projects） | Settings → General → Features |
| Collaborators | Settings → Collaborators and teams |
| Social preview | Settings → General → Social preview |

Social preview 图片必须自有版权；本仓库用 `docs/images/banner.svg` 派生图，不含第三方素材。

## 四、命令输出（真实粘贴）

```console
$ bash scripts/gh-01-repo-baseline.sh
[gh01] 校验 Topics（spring-ai-alibaba,java,agent,rag,mcp,digital-human）
[gh01] 基线快照已写入 docs/github-ops/01-repo-baseline.json
[gh01] 快照字段非空率：6/6
[gh01] Topics：agent, digital-human, spring-ai-alibaba, java, mcp, rag
[gh01] 通过：字段非空率 100%%，Issues 开 / Wiki 关 / Projects 关
```

`docs/github-ops/01-repo-baseline.json` 是上面这条命令产出的**真实输出**，不是手写的：

```json
{"defaultBranchRef":{"name":"main"},"description":"Spring AI Alibaba 中文实战与数字人示例：18 掌实战代码、验收证据与运维治理","hasDiscussionsEnabled":true,"hasIssuesEnabled":true,"hasProjectsEnabled":false,"hasWikiEnabled":false,"homepageUrl":"https://lifuchun522.github.io/springaialibabapractice/","name":"springaialibabapractice","repositoryTopics":[{"name":"agent"},{"name":"digital-human"},{"name":"spring-ai-alibaba"},{"name":"java"},{"name":"mcp"},{"name":"rag"}],"visibility":"PUBLIC"}
```

### 幂等验证：连续执行两次

```console
$ bash scripts/gh-01-repo-baseline.sh   # 第二次
[gh01] 校验 Topics（spring-ai-alibaba,java,agent,rag,mcp,digital-human）
[gh01] 基线快照已写入 docs/github-ops/01-repo-baseline.json
[gh01] 快照字段非空率：6/6
[gh01] Topics：agent, digital-human, spring-ai-alibaba, java, mcp, rag
[gh01] 通过：字段非空率 100%%，Issues 开 / Wiki 关 / Projects 关
```

Topics 用的是 `PUT /repos/{owner}/{repo}/topics`（整体替换语义），所以重复执行不会出现重复项。

### 红线自检：非法 Topics 必须被拦下

```console
$ TOPICS_OVERRIDE='spring-ai-ali,java' DRY_RUN=1 bash scripts/gh-01-repo-baseline.sh
[gh01] 校验 Topics（spring-ai-ali,java）
红线：Topics 少于 6 个（当前 2 个）
红线：命中历史截断项 'spring-ai-ali'，应为 'spring-ai-alibaba'
$ echo $?
1
```

中文项同样会被拦：`grep -Eq '^[a-z0-9]+(-[a-z0-9]+)*$'` 不匹配任何含中文的字符串。

## 五、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 1.1 | 脚本 `set -euo pipefail`，重复执行两次退出码均为 0 | ✅ | 第四节两段输出 |
| 1.2 | `gh repo view --json` 五项非空率 100% | ✅ | 脚本自检打印 `6/6` |
| 1.3 | Topics 全为 `^[a-z0-9-]+$`，无中文无截断项 | ✅ | 第四节红线自检输出 |
| 1.4 | Issues 开 / Wiki 关 / Projects 关 | ✅ | 快照 JSON：`hasWikiEnabled:false`、`hasProjectsEnabled:false`、`hasIssuesEnabled:true` |
| 1.5 | README 首屏五段齐全且每段有可点击内链 | ✅ | `README.md` 首屏「首屏五段」表，五段各含内链 |
| 1.6 | 本文件含改前/改后/Web 路径/命令输出/验收记录五节 | ✅ | 本文一至五节 |

### 记录在案的小坑

本机 `gh` 2.63 的 `gh repo edit` **没有** `--disable-wiki` / `--disable-projects` 开关（教程示例里的写法在本机报 `unknown flag`），
正确写法是布尔赋值 `--enable-wiki=false --enable-projects=false`。
脚本已改用后者，并在注释里记下这个差异——否则换个人按教程敲，第一条命令就卡住。

## 六、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 打开 Wiki | 永远不开：本仓知识全部进 `docs/` 与 `openspec/specs/`，Wiki 会形成第二真源 |
| 打开仓库级 Projects | Discussions 周活稳定，且需要多人共同看同一张盘时 |
| 自定义域名 | Pages 站点（第 10 保）连续可用 1 个月、且需要对外承诺长期地址时 |
| 付费推广 | 仓库已有转化事件（Clone → 运行 → Issue）可归因之后 |
