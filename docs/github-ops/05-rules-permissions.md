# 第 05 保：规则权限

> 对应内容：13太保玩转github-第5太保-李存进-规则权限
> 本轮目标：先立**拦得住事故的最小规则**，再谈协作速度。
> 产物：本文、`scripts/gh-05-rulesets.sh`、`.github/CODEOWNERS`、`docs/github-ops/evidence/ruleset-*.json`。

## 一、最坏结果先写出来：main 被强推翻掉

不是「列表有点乱」，而是：历史被覆盖、tag 被移动、发布物对不上证据链、评审记录形同废纸，
最后**查不到人、回不去版本**。所以规则先立，且必须是**能实测拦住**的规则，不是文档里的一句约定。

## 二、做了什么 / 没做什么

| 做 | 规则内容 |
| --- | --- |
| `main` 分支 ruleset（`main-protect`） | 禁删除、禁非快进推送（禁强推）、要求线性历史（只允许 squash）、必须走 PR（`allowed_merge_methods: [squash]`）、要求状态检查 `编译与单测` 通过 |
| tag ruleset（`tag-protect`） | 命中 `refs/tags/ch*` 与 `refs/tags/v*` 的 tag 禁删除、禁更新（不可移动） |
| `.github/CODEOWNERS` | 只卡四条关键路径：`/.github/`、`/openspec/`、`/scripts/`、`/docs/github-ops/` |
| JSON 快照 | `docs/github-ops/evidence/rulesets-list.json` + 每份 ruleset 详情，供审计与 diff |

| 不做 | 原因 |
| --- | --- |
| 全仓库强制 code owner 审批 | 单人维护仓库会**自锁**：自己的 PR 也合不进去。收益为零，代价是每月 ≥2 小时 |
| 要求 1 个以上 approve | 仓库只有一名具备写权限的维护者，强开等于把 main 焊死 |
| 给 bypass 名单配 admin「always」 | **实测证明这会让规则失效**，见第五节 |

### 与教程的一处有意收紧

教程口径写的是「单人留 bypass，但必须记录」。本仓库**不配 bypass**，理由在下面第五节——
「留 bypass 但要求记录」在实践中退化成「规则看起来在，实际随手就绕」。
代价是紧急绕过必须先去改 ruleset（自身也要走一次 API/Web 操作），换来的是规则真的有效。
紧急流程写死在第六节。

## 三、怎么读这份规则（不是「配好了」就算完）

| 层级 | 判定方式 |
| --- | --- |
| 规则存在 | `gh api repos/{repo}/rulesets` 能列出 `main-protect` / `tag-protect`，`enforcement=active` |
| 规则生效范围 | `gh ruleset check main` 输出 5 条规则 |
| **规则真的拦得住** | 用真实的 `git push` 触发 violation，看 remote 是否真的拒绝 |
| 无旁路 | `gh api repos/{repo}/rulesets/{id} --jq .current_user_can_bypass` 必须是 `never` |

只有第四层过了，前三层才有意义。

## 四、命令输出（真实粘贴）

### 4.1 ruleset 快照与读回

```console
$ bash scripts/gh-05-rulesets.sh
[gh05] 更新已有 ruleset 'main-protect' (id=24152533)
[gh05] 更新已有 ruleset 'tag-protect' (id=24152536)
[gh05] 快照：docs/github-ops/evidence/rulesets-list.json (980 字节)
[gh05] 快照：docs/github-ops/evidence/ruleset-24152533.json (1229 字节)
[gh05] 快照：docs/github-ops/evidence/ruleset-24152536.json (672 字节)
[gh05] 读回校验：
  rulesets: main-protect, tag-protect
  通过：main-protect 与 tag-protect 均为 active

$ gh api repos/lifuchun522/springaialibabapractice/rulesets \
    --jq '.[] | {name, enforcement, bypass: .bypass_actors}'
{"bypass":null,"enforcement":"active","name":"main-protect"}
{"bypass":null,"enforcement":"active","name":"tag-protect"}
```

`bypass: null` 是本保的核心结论：**没有任何角色可以绕过这两条规则。**

### 4.2 规则生效范围

```console
$ gh ruleset check main
5 rules apply to branch main in repo lifuchun522/springaialibabapractice

- deletion
  (configured in ruleset 24152533 from repository lifuchun522/springaialibabapractice)

- non_fast_forward
  (configured in ruleset 24152533 from repository lifuchun522/springaialibabapractice)

- pull_request: [allowed_merge_methods: [squash]] [dismiss_stale_reviews_on_push: false]
  [require_code_owner_review: false] [require_extra_approval_for_unattributed_changes: true]
  [require_last_push_approval: false] [required_approving_review_count: 0]
  [required_review_thread_resolution: false] [required_reviewers: []]
  (configured in ruleset 24152533 from repository lifuchun522/springaialibabapractice)

- required_linear_history
  (configured in ruleset 24152533 from repository lifuchun522/springaialibabapractice)

- required_status_checks: [do_not_enforce_on_create: true]
  [required_status_checks: [map[context:编译与单测]]] [strict_required_status_checks_policy: false]
  (configured in ruleset 24152533 from repository lifuchun522/springaialibabapractice)
```

### 4.3 直推 main 被拒（实测）

```console
$ git log --oneline -1
4f70065 fix(scripts): Projects 同步按 content.title 兜底匹配，避免重复建条目 (#58)

$ git commit -m "chore(gh05): 一次性探针提交（用于验证规则，验证后丢弃）"   # 只本地存在
PROBE_COMMIT=8e76918

$ git push origin main
remote: error: GH013: Repository rule violations found for refs/heads/main.
remote: Review all repository rules at https://github.com/lifuchun522/springaialibabapractice/rules?ref=refs%2Fheads%2Fmain
remote:
remote: - Changes must be made through a pull request.
remote:
remote: - Required status check "编译与单测" is expected.
remote:
To github.com:lifuchun522/springaialibabapractice.git
 ! [remote rejected] main -> main (push declined due to repository rule violations)
error: failed to push some refs to 'github.com:lifuchun522/springaialibabapractice.git'
DIRECT_PUSH_EXIT=1
PASS: 直推 main 被拒
```

两条 violation 同时命中：**必须走 PR** + **必须过状态检查**。探针提交随后用 `git reset --hard origin/main` 丢弃，从未落到远端。

### 4.4 强推 main 被拒（实测）

```console
$ git push --force origin 1ef8b948d95e2cae586b151741187f937054fc71:refs/heads/main
remote: error: GH013: Repository rule violations found for refs/heads/main.
remote:
remote: - Cannot force-push to this branch
remote:
remote: - Changes must be made through a pull request.
remote:
 ! [remote rejected] 1ef8b948d95e2cae586b151741187f937054fc71 -> main (push declined due to repository rule violations)
FORCE_PUSH_EXIT=1
PASS: 强推 main 被拒

$ git log --oneline -1 origin/main
4f70065 fix(scripts): Projects 同步按 content.title 兜底匹配，避免重复建条目 (#58)
```

远端 main 引用**未被改动**。

### 4.5 删除受保护 tag 被拒（实测）

```console
$ git push origin :refs/tags/ch18
remote: error: GH013: Repository rule violations found for refs/tags/ch18.
remote: Review all repository rules at https://github.com/lifuchun522/springaialibabapractice/rules?ref=refs%2Ftags%2Fch18
remote:
remote: - Cannot delete this tag
remote:
To github.com:lifuchun522/springaialibabapractice.git
 ! [remote rejected] ch18 (push declined due to repository rule violations)
error: failed to push some refs to 'github.com:lifuchun522/springaialibabapractice.git'
DELETE_TAG_EXIT=1
PASS: 受保护 tag 无法删除

$ git ls-remote --tags origin ch18
b1e223156891d757405023feca8a6f0f35233804	refs/tags/ch18
```

tag 仍在，指向提交未变。

### 4.6 删除 main 被拒（实测）

```console
$ git push origin --delete main
To github.com:lifuchun522/springaialibabapractice.git
 ! [remote rejected] main (refusing to delete the current branch: refs/heads/main)
DELETE_BRANCH_EXIT=1
PASS: 删除 main 被拒
```

### 4.7 CODEOWNERS 与 owner 权限

```console
$ gh api repos/lifuchun522/springaialibabapractice/contents/.github/CODEOWNERS --jq .path
.github/CODEOWNERS

$ gh api repos/lifuchun522/springaialibabapractice/collaborators/lifuchun522/permission --jq '{role_name, permissions}'
{"role_name":"admin","permissions":{"admin":true,"maintain":true,"push":true,"triage":true,"pull":true}}
```

CODEOWNERS 生效前提（被点名用户需 write 及以上）已满足：`role_name = admin`。
四条路径模式（`/.github/`、`/openspec/`、`/scripts/`、`/docs/github-ops/`）都能匹配到仓库中真实存在的目录。

## 五、实测发现（本保最重要的部分）：配了 bypass 就等于没规则

第一版按教程口径给两条 ruleset 都配了 bypass：

```json
"bypass_actors": [ { "actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "always" } ]
```

`actor_id=5` 是 GitHub 固定的 RepositoryRole「admin」映射。写进去之后 ruleset 显示 `active`、
`gh ruleset check main` 也照样列出 5 条规则——**看起来一切正常**。然后实测删除受保护 tag：

```console
$ git push origin :refs/tags/ch18
remote: Bypassed rule violations for refs/tags/ch18:
remote:
remote: - Cannot delete this tag
remote:
To github.com:lifuchun522/springaialibabapractice.git
 - [deleted]         ch18                    ← 删成功了
DELETE_TAG_EXIT=0
```

三个可复用的判断：

1. **`active` 不等于生效**：只要当前身份在 bypass 名单里，规则就会被「记录后放行」。
   判定要看 `current_user_can_bypass`，不是看 `enforcement`；
2. **`== always` 的 bypass 实质上取消了规则**：它让持 token 的人（也就是最容易误操作的人）永远畅通；
3. **受保护 tag 被删过一次就不会自己回来**，必须手动 `git push origin refs/tags/ch18` 复原。
   本次已复原并核对指向（`b1e2231`，即「登云 K8s」合并后的提交），复原命令与核对输出见 4.5。

修正动作：两条 ruleset 的 `bypass_actors` 都清空，重测后 `current_user_can_bypass = never`，
同样的删除命令变成 `! [remote rejected]`。

## 六、紧急绕过流程（因为不留 bypass，必须写清）

不加 bypass 的代价是「紧急时也得先改规则」。所以流程要短且可回滚：

```bash
# 1) 记下当前配置（快照已在版本库，但再取一次确认）
gh api repos/lifuchun522/springaialibabapractice/rulesets/24152533 \
  --jq '.rules' > /tmp/rules-before.json

# 2) 把 enforcement 临时降级（不是删规则，保留可追溯）
gh api --method PUT repos/lifuchun522/springaialibabapractice/rulesets/24152533 \
  -f enforcement=disabled

# 3) 做紧急动作（push / 改 tag）

# 4) 立刻恢复
gh api --method PUT repos/lifuchun522/springaialibabapractice/rulesets/24152533 \
  -f enforcement=active

# 5) 在对应 PR / Issue 里写明：为什么绕过、绕过了哪条规则、如何防止再发生
```

红线：

- 规则变更**必须**同步提交 JSON 快照进 `docs/github-ops/evidence/`，否则审计断链；
- 绕过**必须**在 PR 或 Issue 里留书面原因——「不留 bypass」要求的是绕过必须留下痕迹，
  而不是绕过必须不可能；
- 只允许给**当前确需操作的身份**开临时窗口，不允许把 admin「always」写回 bypass 名单。

## 七、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 5.1 | `main` ruleset 为 active，含禁删除/禁强推/要求 PR/要求状态检查 | ✅ | 4.1 + 4.2（5 条规则逐条列出） |
| 5.2 | tag ruleset 覆盖 `ch*` 与 `v*`，禁删除禁更新 | ✅ | `ruleset-24152536.json` 的 `conditions.ref_name.include` 与 `rules` |
| 5.3 | 强推 main 被远端拒绝，引用未变 | ✅ | 4.4 |
| 5.4 | 直推 main 被远端拒绝 | ✅ | 4.3 |
| 5.5 | 删除 main 被拒 | ✅ | 4.6 |
| 5.6 | 删除受保护 tag 被拒，tag 仍指向原提交 | ✅ | 4.5 |
| 5.7 | CODEOWNERS 存在且只卡关键路径 | ✅ | 4.7 + 文件内容四条模式 |
| 5.8 | owner 权限满足 CODEOWNERS 生效前提 | ✅ | 4.7：`role_name = admin` |
| 5.9 | 快照含两个 ruleset 详情，且含关键字段 | ✅ | `evidence/ruleset-24152533.json`、`ruleset-24152536.json` |
| 5.10 | 无旁路：`current_user_can_bypass = never` | ✅ | 4.1 的 `bypass: null`；修正过程见第五节 |

### 学习点小结（换仓库可直接复用）

- 关闭 ruleset 的自动 bypass 后，`required_status_checks` 会拦下**所有**直推，包括本仓库自己的自动化。
  第 13 保的周归档工作流据此改为**本地手动触发 + 只在 PR 里提交**，避免「规则刚立、机器人自己破」；
- `pull_request` 规则的 `parameters` 若只写 `required_approving_review_count` +
  `allowed_merge_methods`，API 会报 `Invalid property /rules/3: data matches no possible input`；
  要么写全字段，要么整个 `parameters` 省略；
- `gh_retry` 这类「捕获 stdout 再重试」的封装**不能包住带 `>` 重定向的命令**，
  否则重定向得到 0 字节文件（本脚本第一版把快照写成了空文件，第二次跑才发现）。

## 八、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 全仓库强制 code owner review | 出现第二名具备写权限的长期维护者时 |
| 要求 ≥1 个 approve | 同上；届时把 `required_approving_review_count` 提到 1 |
| 给 admin 配 always bypass | 不再评估。这是被实测否掉的方案 |
| 保护 `chapter/**` 分支 | 历史章节分支视为只读参考，靠约定不靠规则；若出现误改事故再上规则 |
| 要求 commit 签名 | 出现合规要求时；当前单人维护，签名收益不抵配置成本 |
