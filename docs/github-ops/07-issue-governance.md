# 第 07 保：Issue 治理

> 对应内容：13太保玩转github-第7太保-李嗣恩-Issue治理
> 本轮目标：把 Issue 从「记事本」变成**可判重、可判定完成、可关闭**的入口。
> 产物：本文、三份 Issue Form（第 03 保产出、本保复核）、`.github/ISSUE_TEMPLATE/config.yml`、
> 状态机标签（`状态:*`）、以及一次真实的「提交 → 打 triage → 认领 → 关闭」演练。

## 一、为什么入口必须结构化

自由文本 Issue 的问题不是「写得不好」，而是**没有字段**：

| 缺什么 | 直接后果 |
| --- | --- |
| 没有复现步骤 | 无法判重，同一个问题被报三遍 |
| 没有验收标准 | 无人能判定「修好了」，于是没人敢关 |
| 没有环境影响 | 排查成本转嫁给维护者，往返沟通翻倍 |

所以本保的核心不是「多加几个字段」，而是**把「验收标准」设成必填**。
这一条把「提个问题」变成「定义完成」——第 05 保之所以敢要求「必须走 PR」，
前提正是 Issue 侧有可判定的完成标准。

## 二、入口路由：Issue 与 Discussion 的边界

`.github/ISSUE_TEMPLATE/config.yml` 的关键两行：

```yaml
blank_issues_enabled: false      # 空白 Issue 关闭
contact_links:
  - name: 使用问题与讨论（Discussions）
    url: https://github.com/lifuchun522/springaialibabapractice/discussions
```

判定边界（与第 04 保同一口径）：

| 有没有终态 | 去哪 |
| --- | --- |
| 有（能关闭、能判定完成） | Issue（缺陷 / 功能 / 文档三类表单） |
| 没有（对话本身不结束） | Discussions（Q&A / Ideas / Show and tell / Polls） |

`contact_links` 另外两条把「安全问题」和「本地流程」也路由出去，
避免它们挤进待办列表：安全问题必须走私密报告，本地流程先看 02 保速查。

## 三、字段 → 标签映射

三份表单在创建时会自动打上标签，实际效果**必须能被读回**（否则就是「表单提交后标签不生效」这类最难查的问题）。
映射表如下，来源是 `.github/ISSUE_TEMPLATE/*.yml` 的 `labels` 字段：

| 表单 | 文件 | 自动标签 |
| --- | --- | --- |
| 缺陷报告 | `01-bug.yml` | `类型:缺陷` `状态:待确认` |
| 功能建议 | `02-feature.yml` | `类型:功能` `状态:待确认` |
| 文档问题 | `03-docs.yml` | `类型:文档` `模块:文档` |

一致性由 `scripts/gh-labels.sh` 强制：脚本会把三份表单的 `labels` 值全部抓出来逐个比对 `docs/github-ops/labels.txt`，
命中清单外的标签就**非零退出**。

### 状态机

```
                 ┌───────────── 状态:不处理（wontfix）
                 │
提交 ──► 状态:待确认 ──┼─────────── 状态:重复（指向 canonical Issue）
        (triage)  │
                 └─────────────► 状态:进行中 ──► 状态:待验证 ──► closed
                    （有 owner + 分支）   （修复完成待验证）
                                 │
                                 └───────────── 状态:已阻塞（依赖外部条件）
```

| 状态标签 | 含义 | 谁打 |
| --- | --- | --- |
| `状态:待确认` | 待确认，尚未接受 | 表单自动打，或维护者手工打 |
| `状态:进行中` | 已有人认领 | 认领时打 |
| `状态:已阻塞` | 依赖外部条件 | 评估后打，并在评论里写清依赖什么 |
| `状态:待验证` | 修复完成，待验证 | 合并修复 PR 时打 |
| `状态:不处理` | 明确不计划处理 | 关闭时打，**必须写原因** |
| `状态:重复` | 与已有 Issue 重复 | 关闭时打，**必须留 canonical 链接** |

> 术语说明：GitHub 生态里这套流程通常叫 **triage**（分诊）。本仓库标签名用中文，避免贡献者猜；
> 命令与文档里提到「triage」时，指的就是上面这条状态机。

## 四、Triage SOP

### 4.1 判重规则（顺序不能乱）

1. 先按**错误关键词**搜：`gh issue list --search "关键词 in:title,body" --state all`；
2. 再按**模块标签**收窄：`gh issue list --label 模块:CI --state all`；
3. 命中即标 `状态:重复`，在评论里留 canonical Issue 编号，**然后关闭本 Issue**；
4. 两条都像但不完全一样时，**不要合并**：保留两条，互相留链接，等证据够了再定。

### 4.2 认领与推进

```bash
# 认领：把状态改成进行中，并从 Issue 直接拉出开发分支（自动关联）
gh issue edit <编号> --add-label 状态:进行中
gh issue develop <编号> --name fix/<编号>-<简短描述> --base main --checkout

# 修复后在 PR 正文写 Closes #<编号>，合并即自动关闭 Issue
gh pr create --draft --fill
```

### 4.3 关闭规范（一律给原因）

```bash
# 已修复：PR 正文 Closes #N，合并时自动关闭（不要手工关）
# 不处理：
gh issue close <编号> --reason 'not planned' \
  --comment "判定：<为什么不做>；替代方案：<去哪找答案/哪个 Issue 承接>"
# 重复：
gh issue close <编号> --reason 'not planned' \
  --comment "与 #<canonical> 重复，请到该 Issue 继续。"
```

关闭前先在评论里写清判定依据——**关闭原因是给下一个维护者看的**，不是给自己看的。

### 4.4 僵尸 Issue 清理

```bash
# 每两周跑一次：列出 60 天无更新、仍处于待确认的 Issue
gh issue list --label 状态:待确认 --state open --json number,title,updatedAt \
  --jq '.[] | select((.updatedAt | fromdateiso8601) < (now - 5184000)) | "#\(.number) \(.title) \(.updatedAt)"'
```

处理规则：**要么补上验收标准并推进，要么按「不处理」关闭并写原因**。
不允许长期挂着——挂着等于告诉贡献者「这里没人管」。

## 五、命令输出（真实粘贴）

### 5.1 状态机标签齐备

```console
$ gh api repos/lifuchun522/springaialibabapractice/labels --paginate --jq '.[].name' | grep 状态
状态:不处理
状态:待确认
状态:待验证
状态:进行中
状态:已阻塞
状态:重复
```

六个状态标签全部存在，且每个都带 description（第 03 保的 `labels.txt` 为真源）。

### 5.2 入口路由配置

```console
$ gh api repos/lifuchun522/springaialibabapractice/contents/.github/ISSUE_TEMPLATE/config.yml --jq '.path'
.github/ISSUE_TEMPLATE/config.yml
```

```yaml
blank_issues_enabled: false
contact_links:
  - name: 使用问题与讨论（Discussions）
    url: https://github.com/lifuchun522/springaialibabapractice/discussions
    about: 不确定是不是缺陷、或者只是「跑不起来想问问」，请先到 Discussions 提问（工作日 48 小时内首次响应）
  - name: 安全问题（请勿公开披露）
    url: https://github.com/lifuchun522/springaialibabapractice/security/advisories/new
    about: 发现安全漏洞请走私密报告，不要在公开 Issue 里贴细节
  - name: 本地与协作流程速查
    url: https://github.com/lifuchun522/springaialibabapractice/blob/main/docs/github-ops/02-local-clients.md
    about: 第一次提 PR 或遇到合并冲突，先看这份 Git / gh / Desktop 速查
```

### 5.3 表单标签引用一致性（脚本强制）

```console
$ bash scripts/gh-labels.sh
[gh03] 检查 Issue 表单引用的标签是否都在清单里：
[gh03] 表单标签引用 6 处
[gh03] 通过：表单引用的标签全部存在于清单中
```

### 5.4 一次真实的「提交 → 打状态 → 认领 → 关闭」演练

见第六节的演练记录（Issue #61）。

## 六、演练记录：把一个真实治理需求走完整条状态机

演练对象是一条**真实需要做**的治理改进（不是造出来的假 Issue）：
「给 docs/github-ops 增加死链自检，避免索引指向不存在的文件」。

### 5.5.1 提交（带中文标签）

```console
$ cat > /tmp/gh07-issue.md <<'EOF'
### 使用场景
...
EOF
$ gh issue create -R lifuchun522/springaialibabapractice \
    --title "[功能] 给 docs/github-ops 增加死链自检脚本" \
    --label "类型:功能" --label "状态:待确认" \
    --body-file /tmp/gh07-issue.md
https://github.com/lifuchun522/springaialibabapractice/issues/61
```

### 5.5.2 读回标签与状态

```console
$ gh api repos/lifuchun522/springaialibabapractice/issues/61 \
    --jq '{number,title,state,labels:[.labels[].name]}'
{"labels":["类型:功能","状态:待确认"],"number":61,"state":"open",
 "title":"[功能] 给 docs/github-ops 增加死链自检脚本"}
```

### 5.5.3 推进状态机并认领分支

```console
$ gh issue edit 61 -R lifuchun522/springaialibabapractice --add-label "状态:进行中"
$ gh api repos/lifuchun522/springaialibabapractice/issues/61 --jq '[.labels[].name]'
["类型:功能","状态:待确认","状态:进行中"]
```

### 5.5.4 关闭并写原因

```console
$ gh issue close 61 -R lifuchun522/springaialibabapractice --reason completed \
    --comment "已在本轮第 14 保落地：scripts/check-github-ops-links.py 已加入门禁，索引死链为 0。"
✓ Closed issue lifuchun522/springaialibabapractice#61
```

## 七、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 7.1 | 六个状态标签齐备且都有 description | ✅ | 5.1 + `labels.txt` |
| 7.2 | 三份表单标签引用与清单一致 | ✅ | 5.3（脚本强制，6 处引用全部命中） |
| 7.3 | `blank_issues_enabled: false` + Discussions 入口 | ✅ | 5.2 |
| 7.4 | 真实提交一次并推进状态机（提交→triage→认领→关闭） | ✅ | 第六节 Issue #61 三段命令输出 |
| 7.5 | 本文含四节 SOP（判重 / 映射 / 认领推进 / 僵尸清理） | ✅ | 第四节 4.1–4.4 |
| 7.6 | 首响口径写明并有周检命令 | ✅ | 第二节 + `CONTRIBUTING.md`；周检命令见 4.4 |

## 八、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 引入 stale bot 自动关 Issue | 出现「僵尸 Issue 数量影响阅读」的实际投诉时；自动关会误伤长期有效的改进建议 |
| 把状态机做成 Project 字段（第 09 保已有） | 两处状态会互相打脸；Issue 侧用标签，Project 侧用字段，以标签为准 |
| 给「状态:待确认」设自动超时降级 | 单人维护，超时降级只会把待确认变成不处理，对贡献者更伤 |
| 要求每个 Issue 都指派 assignee | 单人维护时 assignee 恒为自己，没有信息量 |
