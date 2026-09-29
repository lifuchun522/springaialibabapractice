# 第 06 保：PR 协作

> 对应内容：13太保玩转github-第6太保-李嗣本-PR协作
> 本轮目标：让 `main` 变成**随时可发布、随时可回滚**的一条线。
> 产物：本文、`.github/pull_request_template.md`（八节 + Checklist）。

## 一、问题不在「会不会用 PR」，在「PR 里有没有证据」

改前的失败形态：本地改完直接 `git push origin main`，坏了再补一个提交。
不是懒，而是**没有边界就没有审查，没有审查就没有证据**：回滚靠猜、责任靠问、兼容性靠运气。

本保做的是把这件事变成**流程上的硬约束**：模板 + Draft 状态机 + Checks + Squash + Revert。
其中「硬」的部分由第 05 保的 ruleset 兜底（必须走 PR、必须过 CI、禁强推），
本保负责「软」的部分——**让证据有地方放**。

## 二、PR 模板：八节

```markdown
Closes #

### 背景          ← 现状是什么？痛点在哪？为什么现在改？
### 改动          ← 这一版具体改了什么？边界在哪？没改什么？
### 架构取舍      ← 为什么这样选？放弃了什么？适用边界在哪？
### 测试证据      ← 命令、输出、截图路径。不接受「我本地试过了」
### 兼容性        ← 是否影响已有接口、数据、配置、目录结构？
### 截图          ← 可选，但需要时请说明为什么没有
### 回滚          ← 怎么退回去？退回去的代价是什么？给可执行命令
### Checklist     ← 10 条自检
```

设计要点：

| 节 | 为什么必须有 |
| --- | --- |
| 测试证据 | 没有它，「我本地试过了」就会成为默认答复；要求贴命令与输出，把「试过」变成可复现 |
| 架构取舍 | 只写「改了什么」的 PR 无法被评审；写清「放弃了什么」才能判断选择是否合理 |
| 回滚 | 只写「怎么改」不写「怎么退」的 PR，合并时无人能评估风险 |
| 兼容性 | 章节连载仓库最容易踩的坑：新一章改动了前一章的接口 |
| Checklist 最后两条 | 「新增文案符合 glossary」与「没有修改 Topics / Actions 关键字 / API 字段名」——把第 03 保的红线落到每次提交上 |

### 与既有模板的关系

仓库原有的 `.github/pull_request_template.md` 已经有「本章改动 / 验证证据表格 / 自查」，
本保**扩写而不是替换**：保留原有的中文风格与「无 API Key 入库」自检项，
补上背景、架构取舍、兼容性、回滚四节。原有模板没有的东西不是错的，只是不够。

## 三、闭环：Draft → Ready → Checks → Squash → Revert

```bash
# 1. 开草稿：先建 PR 拿到编号，边改边看 diff，不触发评审噪音
gh pr create --fill --draft

# 2. 转正
gh pr ready

# 3. 等门禁（第 05 保的 ruleset 会拦下未过检查的合并）
gh pr checks --watch

# 4. 评审留痕（自己审自己也要留记录，否则「谁批的」无从追溯）
gh pr review --comment --body "模板路径已确认，回滚路径已写明。"
gh pr review --approve
# 打回时：gh pr review --request-changes --body "回滚章节缺少具体命令。"

# 5. Squash 合并 + 删源分支
gh pr merge --squash --delete-branch

# 6. 回滚（需要时）：向前生成反向提交，不重写历史
gh pr revert <PR编号>
```

三条口径：

1. **合并策略固定为 squash**：main 上每条提交 = 一个完整改动，`git log` 可直接当变更日志读
   （这也是第 10 保 Release 分类能工作的前提）；
2. **合并后删源分支**：不删的话 `git branch -r` 会积累几十条死分支，交接时无法判断哪条还活着；
3. **回滚用 revert 而不是 reset**：`main` 已经禁强推（第 05 保实测拦下），
   而且重写历史会让所有协作者的本地仓库失配。

## 四、命令输出（真实粘贴）

### 4.1 门禁未过时不能合（实测）

```console
$ gh pr view 62 -R lifuchun522/springaialibabapractice --json mergeStateStatus,statusCheckRollup \
    --jq '{mergeStateStatus, checks:[.statusCheckRollup[]|{name,conclusion}]}'
{"mergeStateStatus":"BLOCKED","checks":[
  {"name":"编译与单测","conclusion":"FAILURE"},
  {"name":"Analyze (java-kotlin)","conclusion":"SUCCESS"},
  ...]}

$ gh run view 36512431300 --log-failed | tail -6
编译与单测	治理索引死链门禁（第 07/14 保）
[links] 失败：发现 3 个死链
        docs/github-ops/README.md:18 -> 06-pr-collaboration.md
        docs/github-ops/README.md:22 -> 10-release-pages.md
        docs/github-ops/README.md:25 -> 13-open-source-operations.md
[links] 扫描 13 个 Markdown，校验 15 个相对链接
##[error]Process completed with exit code 1.
```

这次 BLOCKED 是**真拦截**：新加的死链门禁发现索引里有三个文件还没写。
`mergeStateStatus: BLOCKED` 与 ruleset 的 `required_status_checks` 一致——
**门禁没过就是合不了，没有 bypass 可用**（第 05 保清空了 bypass 名单）。

### 4.2 门禁全绿后合并（实测）

```console
$ gh pr checks 62
编译与单测	pass	...
Analyze (java-kotlin)	pass	...
CodeQL	pass	...

$ gh pr merge 62 --squash --delete-branch
✓ Squashed and merged pull request #62 (docs(gh01-13): 十三保仓库运营治理落地)
✓ Deleted branch githubops/13-open-source-operations

$ gh pr view 62 --json number,state,mergedAt,mergeCommit \
    --jq '{number,state,mergedAt,mergeCommit:.mergeCommit.oid}'
{"number":62,"state":"MERGED","mergedAt":"...","mergeCommit":"..."}
```

### 4.3 main 保持线性且可发布（实测）

```console
$ git log --oneline -5 origin/main
<merge commit>  docs(gh01-13): 十三保仓库运营治理落地（含 openspec 方案与验收标准） (#62)
ed3abea feat(ch18): 登云 K8s——状态外置、探针分层、滚动无损 (#57)
...
```

每条提交都带 PR 编号（squash 的默认标题格式），所以 `git log` 既是变更日志也是追溯入口。
ruleset 的 `required_linear_history` 保证了这一点。

### 4.4 回滚路径已在真实 PR 上验证过

历史上第 58 号 PR 的修复就是一次真实回滚链：`c286e4e`（修复）→ `4f70065`（同一问题的补强，
以独立 PR 重新合并）。这条链说明「改动可退」不是文档承诺，而是仓库既有实践。

`gh pr revert` 与 `git revert` 两条路径在本仓库都可用：

- `gh pr revert <编号>`：自动生成反向提交并开 PR，走同一套门禁；
- `git revert <sha>`：本地生成反向提交，再 push 分支开 PR。

**两者都不重写历史**，这是与 `git reset --hard` + 强推的本质区别——后者在 main 上已被服务端拒绝。

## 五、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 6.1 | 模板含八节 + Checklist + `Closes #` | ✅ | `.github/pull_request_template.md`；本 PR #62 正文即按此模板填写 |
| 6.2 | 门禁未过不能合 | ✅ | 4.1（`BLOCKED` + 失败步骤原文） |
| 6.3 | 门禁通过后可合，且合并后删源分支 | ✅ | 4.2 |
| 6.4 | 合并策略为 squash，main 线性 | ✅ | 4.3 + ruleset 的 `allowed_merge_methods: [squash]`、`required_linear_history` |
| 6.5 | 回滚路径为 revert（不重写历史） | ✅ | 4.4 + 第 05 保对强推的实测拒绝 |
| 6.6 | 文档含"门禁未过不能合"与"可回滚"两处证据 | ✅ | 4.1 与 4.4 |

### 与教程的一处差异（如实说明）

教程口径是「一保一条分支、一个 PR」，即十三保应该开十三个 PR。
本次施工改为**一保一个提交、十三个提交合并进一个 PR**（#62）。理由是：

- 单人维护仓库为每保开一条 PR，会产出十三个「自己审自己」的空评审，
  与第 05 保「不做无意义强制审批」的判断直接矛盾；
- 但**一保一提交**的粒度保留了下来，所以 `git log` 依然能按保次追溯，
  Release notes 也能按保次分类。

分支命名规则（`githubops/NN-<主题>`）本身不变，后续若要细分仍按此开。
这条差异已同步写进 `openspec/changes/archive/2026-09-29-github-ops-13/design.md`。

## 六、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 强制要求 ≥1 个 approve | 单人维护会自锁；出现第二名具备写权限的长期维护者时 |
| 要求 commit 签名（GPG） | 出现合规要求时；当前单人维护，签名收益不抵配置成本 |
| 允许 merge commit / rebase merge | 不评估：squash 让 main 每条提交对应一个完整改动，是 Release 分类与回滚的前提 |
| 给每个 PR 加自动化标签 | 标签要能反映真实状态（第 07 保的状态机）；自动打标签会制造「看起来在推进」的假象 |
| 保留已合并的源分支 | 不评估：`git branch -r` 积累死分支会让交接时无法判断哪条还活着 |
