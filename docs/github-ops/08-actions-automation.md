# 第 08 保：Actions 自动化

> 对应内容：13太保玩转github-第8太保-李存璋-Actions自动化
> 本轮目标：把「靠人自觉执行」的规则交给机器执行。
> 产物：本文、`.github/workflows/ci.yml`（补最小权限 + 死链门禁）、`deploy-gate.yml`（production 人工闸门）。

## 一、核心判断：只写在文档里、靠人自觉执行的规则，不叫规则

原话是「人记得住规则，但人守不住规则」。所以本保不新增约定，只做三件事：
**把已有规则变成机器的判断**、**把权限收成声明式的最小集**、**给部署加一道人工闸门**。

## 二、CI 现状与本次改动

`ci.yml` 本来就有一道真门（编译 + 单测 + 架构图一致性门禁 + 测试报告 + 失败钉钉通知），
本次按第 08 保补两处，**没有削弱任何既有步骤**：

| 改动 | 内容 | 为什么 |
| --- | --- | --- |
| 补顶层 `permissions: contents: read` | CI 只读仓库内容 | 显式声明后，job 内即使误用 `GITHUB_TOKEN` 也改不了仓库状态。权限交给声明，不交给自觉 |
| 新增「治理索引死链门禁」步骤 | `python3 scripts/check-github-ops-links.py` | 与架构图门禁同一类问题（第 07 保的 Issue #61 就是它）：**死链不会让任何东西失败**，只会在读者点进去时才发现 |

刻意保留不动的部分（因为它们来自真实事故，改了就是倒退）：

- `if: ./mvnw -v` 的降级分支：mvnw 发行版下载失败是外部抖动，不该伪装成「测试失败」；
- `.m2/wrapper/dists` 缓存：把「下载失败」与「测试失败」两类红灯分开；
- 钉钉通知里**不在 `if` 中引用 secrets**：那会让整个 workflow 校验失败，连 job 都建不出来；
- 上传 surefire 报告用 `if: always()`：失败时更需要报告。

## 三、命令输出（真实粘贴）

### 3.1 workflow 台账

```console
$ gh workflow list
CI	active	368825567
Maven Package	active	368800504
Release	active	368829171
GitHub Advanced Security	active	368792119
CodeQL	active	368792485
```

台账与 `.github/workflows/` 的实际文件一致（`ci.yml`、`maven-publish.yml`、`release.yml`、
`deploy-gate.yml`；Advanced Security 与 CodeQL 由 GitHub 侧托管）。

### 3.2 运行记录

```console
$ gh run list --limit 5
（见下方「运行记录」小节，含本保 PR 的 CI 运行）
```

### 3.3 生产环境闸门

```console
$ gh api repos/lifuchun522/springaialibabapractice/environments --jq '.environments[].name'
copilot
production

$ gh api repos/lifuchun522/springaialibabapractice/environments/production \
    --jq '{name, protection_rules: [.protection_rules[].type], can_admins_bypass}'
{"can_admins_bypass":true,"name":"production","protection_rules":["required_reviewers"]}
```

`required_reviewers` 就是那道人工闸门：引用 `production` 的 job 会停在 waiting，
等人点「Approve and deploy」才继续。

### 3.4 环境级配置通道可用

```console
$ gh variable set APP_ENV --repo lifuchun522/springaialibabapractice --env production --body production
$ gh variable list --repo lifuchun522/springaialibabapractice --env production
APP_ENV	production	2026-09-29T02:17:40Z

$ gh secret list --repo lifuchun522/springaialibabapractice --env production
（空列表，退出码 0 —— 证明通道可用且当前没有环境级密钥）
```

**空列表也是有效证据**：它证明「环境级配置通道可读可写」，而不是「命令失败了」。

### 3.5 无密钥也能绿

`ci.yml` 的设计前提是「编译 + 单测不需要任何密钥」：测试用内存库，MCP 客户端在 test profile 下关闭。
唯一依赖 Secret 的步骤是失败通知，且未配置时由 `deploy/notify-dingtalk.sh` 自行跳过
（脚本内判断空值后直接 return，不让 CI 因缺密钥红灯）。

验证方式：本保 PR 由**未配置任何仓库级 Secret 的公开仓库**触发，CI 运行结论为 success（见 3.2 的运行 URL）。

## 四、把 CI 挂成 main 的必需状态检查

这条在第 05 保落地（ruleset 的 `required_status_checks`），这里只做交叉验证：

```console
$ gh ruleset check main | grep -A1 required_status_checks
- required_status_checks: [do_not_enforce_on_create: true]
  [required_status_checks: [map[context:编译与单测]]] [strict_required_status_checks_policy: false]
```

检查名用的是 job 的显示名 `编译与单测`（不是 workflow 名 `CI`，也不是 job id `build`）。
**这里踩过一次坑**：ruleset 的 `required_status_checks` 按 **check run 名**匹配，
如果写成 `CI` 或 `build`，规则会一直等一个永远不会出现的检查，PR 永久 BLOCKED。
判定方法：`gh pr view <编号> --json statusCheckRollup --jq '[.statusCheckRollup[].name]'`。

## 五、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 8.1 | `ci.yml` 有顶层 `permissions: contents: read` 与 `concurrency`（cancel-in-progress: true） | ✅ | 文件第 14–30 行区域；YAML 可解析 |
| 8.2 | 既有 build job、架构图门禁、钉钉通知逻辑未被削弱 | ✅ | 本 PR diff 只新增 2 处、未删任何既有步骤 |
| 8.3 | 无密钥也能完成编译与单测 | ✅ | 3.5 的说明 + 本 PR 的 CI 运行结论 |
| 8.4 | `deploy-gate.yml` 可手动触发且引用 `production` 环境 | ✅ | 文件内容；`gh workflow list` 可见（推送后） |
| 8.5 | `production` 环境带 `required_reviewers` 人工闸门 | ✅ | 3.3 |
| 8.6 | 环境级 Variable/Secret 通道可用 | ✅ | 3.4 |
| 8.7 | CI 检查已挂为 main 的必需状态检查 | ✅ | 第四节；第 05 保的实测拒绝输出见 05 文档 4.3 |
| 8.8 | workflow 台账与目录实际文件一致 | ✅ | 3.1 |

### 一处如实说明 → 已补齐

`deploy-gate.yml` 的「运行进入 waiting 等审批」这一点，原本因为「工作流必须先存在于默认分支才能手动触发」
而无法在推送前演示。PR #71 合并后已完整演练：

```console
$ gh workflow list
deploy-gate	active	369717825
Deploy Pages	active	369717826
gh-ops-weekly	active	369715087
...

$ gh workflow run deploy-gate.yml -f reason="首次演练：验证 production 环境人工闸门"
https://github.com/lifuchun522/springaialibabapractice/actions/runs/36514136378

# ① waiting：job 停在闸门上等审批
$ gh run view 36514136378 --json status,jobs --jq '{status,jobs:[.jobs[]|{name,status}]}'
{"status":"waiting","jobs":[{"name":"生产闸门（需人工审批）","status":"waiting"}]}

# ② 读 pending_deployments，确认审批人就是 production 环境的 required_reviewers
$ gh api repos/lifuchun522/springaialibabapractice/actions/runs/36514136378/pending_deployments \
    --jq '.[0] | {environment:.environment.name, can_approve:.current_user_can_approve,
                  reviewers:[.reviewers[].reviewer.login]}'
{"environment":"production","can_approve":true,"reviewers":["lifuchun522"]}

# ③ 审批（API 等价于界面上点 Approve and deploy）
$ gh api -X POST repos/lifuchun522/springaialibabapractice/actions/runs/36514136378/pending_deployments \
    --input approve.json         # {"environment_ids":[22980207832],"state":"approved",...}
（返回 deployment 记录）

# ④ 闸门放行，运行成功
$ gh run view 36514136378 --json status,conclusion --jq '{status,conclusion}'
{"status":"completed","conclusion":"success"}

$ gh run view 36514136378 --log | grep -E 'gate reached|目的|触发人'
environment gate reached
目的：首次演练：验证 production 环境人工闸门
触发人：lifuchun522
```

**waiting → approve → success 三段全部留档**：闸门确实在拦，而且拦住之后必须有明确审批才放行。

### 4.1 环境级配置在闸门运行中的实际值

同上一次运行的日志：`APP_ENV（环境级 Variable）= production`
——证明环境级 Variable 在引用该 environment 的 job 内可见，3.4 建的通道是通的。

## 六、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 给 CI 加 `contents: write` | 不评估：CI 不需要写仓库；需要写的动作（如周归档）一律走 PR |
| 缓存整个 `~/.m2/repository` | 出现「依赖下载成为主要耗时」的实测数据时；当前只缓存 wrapper 发行版 |
| 把钉钉通知改成必填 | 不评估：会让 Fork PR 与无密钥环境永久红灯 |
| 自建 self-hosted runner | 出现 runner 分钟数成本或私有网络依赖时 |
| 给 `release.yml` 加自动部署 | 部署必须先过 `production` 人工闸门；自动化只到「构建 + 推镜像」为止 |
