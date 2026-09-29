# 第 11 保：安全治理

> 对应内容：13太保玩转github-第11太保-史敬思-安全治理
> 本轮目标：把「依赖带洞靠运气、密钥靠 .gitignore、CI 权限一路 write」换成**可断言**的安全基线。
> 产物：本文、`.github/SECURITY.md`、`.github/dependabot.yml`、以及一份密钥泄漏应急顺序。

## 一、按「可见性 → 检测 → 修复 → 阻断」的顺序开

顺序不是随便排的，每一步都依赖前一步能成立：

| 阶段 | 做什么 | 为什么必须在前 |
| --- | --- | --- |
| 可见性 | 确认依赖图能解析、仓库公开 | 依赖图不全时，告警会漏掉整个子树 |
| 检测 | 开 Dependabot alerts、Secret scanning | 没有检测就没有「谁发现了什么」的记录 |
| 修复 | 开 Dependabot security updates、依赖分组升级、Actions 权限收紧 | 有告警没升级通道，告警只会烂在列表里 |
| 阻断 | Push protection、非提供方模式检测 | 阻断面太大时先开会把正常提交卡住 |

**唯一不可颠倒的例外是密钥泄漏处置**：先吊销并轮换凭证，再清理提交历史（见第六节）。

## 二、改前基线（本次实测）

```console
$ gh api repos/lifuchun522/springaialibabapractice/vulnerability-alerts --silent
gh: Vulnerability alerts are disabled. (HTTP 404)          ← 退出码 1

$ gh api repos/lifuchun522/springaialibabapractice --jq '.security_and_analysis'
{"dependabot_security_updates":{"status":"disabled"},
 "secret_scanning":{"status":"enabled"},
 "secret_scanning_non_provider_patterns":{"status":"disabled"},
 "secret_scanning_push_protection":{"status":"enabled"},
 "secret_scanning_validity_checks":{"status":"disabled"}}

$ gh api repos/lifuchun522/springaialibabapractice/private-vulnerability-reporting
{"enabled":false}

$ gh api repos/lifuchun522/springaialibabapractice/actions/permissions/workflow
{"default_workflow_permissions":"read","can_approve_pull_request_reviews":false}
```

判断：

| 项 | 改前 | 结论 |
| --- | --- | --- |
| Dependabot alerts | **关闭**（404） | 依赖漏洞不会告警 |
| Dependabot security updates | **disabled** | 即使告警也没有升级 PR |
| Secret scanning | enabled | 已开，缺配套政策 |
| Push protection | enabled | 已开 |
| 非提供方模式检测 | disabled | 见第五节（不可用，非配置错误） |
| 私密漏洞报告 | **false** | `SECURITY.md` 里指的入口点开是 404 |
| Actions 默认权限 | read + 不可自行批 PR | 已经是最小权限，本次只做落盘断言 |

## 三、改了什么

### 3.1 `.github/SECURITY.md`

| 节 | 内容 |
| --- | --- |
| 支持范围 | 只针对默认分支最新状态；`chapter/**` 历史分支视为只读参考，**不再回溯修复** |
| 报告漏洞 | **禁止**公开 Issue，走私密漏洞报告入口（给出直达链接） |
| 响应约定 | 确认 ≤3 工作日、初判 ≤7 工作日；明确不承诺无法兑现的修复日期 |
| 范围外 | 本地配置错误、第三方云服务账号配额、你自己 Fork 的改动、依赖库自身漏洞 |
| 可自行核实的安全基线 | 把七项状态与核实命令列成表，让外部人不必信我们的说法 |
| 密钥规范 + 泄漏顺序 | 给贡献者的红线与处置顺序 |

顶部写明「英文版本优先」，避免中文译文与官方口径冲突。

### 3.2 `.github/dependabot.yml`

三个取舍写在文件注释里，都是为了「PR 能用」而不是「PR 越多越好」：

| 取舍 | 原因 |
| --- | --- |
| maven 与 github-actions 都按**周**扫（周一 09:00 Asia/Shanghai） | 日扫产生的 PR 噪音大于收益 |
| Spring 栈**分组**成一条 PR（`org.springframework*`、`com.alibaba.cloud*`） | 这些版本互相咬合，拆开升级会互相冲突 |
| 忽略 Spring Boot / Spring AI 的**主版本**跳变 | 主版本会改配置与 API，机器人开 PR 等于诱导「点一下就合」 |

标签只引用已登记的两个：`dependencies`、`模块:CI`（第 03 保的 `labels.txt` 为真源）。

### 3.3 开启检测与修复通道

```console
$ gh api -X PUT repos/lifuchun522/springaialibabapractice/vulnerability-alerts
（无输出，HTTP 204）
$ gh api -X PUT repos/lifuchun522/springaialibabapractice/automated-security-fixes
（无输出，HTTP 204）
$ gh api -X PUT repos/lifuchun522/springaialibabapractice/private-vulnerability-reporting
（无输出，HTTP 204）
```

## 四、命令输出（改后断言，真实粘贴）

```console
$ gh api repos/lifuchun522/springaialibabapractice/vulnerability-alerts --silent
$ echo $?
0
$ gh api repos/lifuchun522/springaialibabapractice/automated-security-fixes --silent
$ echo $?
0
```

**204 表示已开启，404 表示未开启**——这是本保最关键的一条判定口径：
`--silent` 下看不到正文，只有退出码能区分，所以断言必须打印退出码。

```console
$ gh api repos/lifuchun522/springaialibabapractice --jq '.security_and_analysis'
{"dependabot_security_updates":{"status":"enabled"},
 "secret_scanning":{"status":"enabled"},
 "secret_scanning_non_provider_patterns":{"status":"disabled"},
 "secret_scanning_push_protection":{"status":"enabled"},
 "secret_scanning_validity_checks":{"status":"disabled"}}

$ gh api repos/lifuchun522/springaialibabapractice/private-vulnerability-reporting
{"enabled":true}

$ gh api repos/lifuchun522/springaialibabapractice/actions/permissions/workflow
{"default_workflow_permissions":"read","can_approve_pull_request_reviews":false}
```

对照改前：`dependabot_security_updates` 从 `disabled` → `enabled`；
`private-vulnerability-reporting` 从 `false` → `true`。
Actions 默认权限本来就合规，本次的价值是把它**落盘成断言**——「没坏」也需要证据，否则下一个人无法判断是「一直对」还是「没人动过」。

### 4.1 密钥形态自检

```console
$ git grep -nE 'ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}' -- .
.env.example:3:AI_DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
.env.example:5:DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
deploy/.env.example:18:APP_DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

$ # 排除纯占位（连续同一字符）后再看：
$ git grep -nE 'sk-[A-Za-z0-9]{20,}' -- . | grep -vE 'sk-x{20,}'
（无输出）
```

结论：命中的三条**全部是 `.env.example` 里的占位符**（`sk-` 后面 32 个连续的 `x`），
排除占位后没有任何命中。这正是「grep 命中不等于泄漏」的例子——
如果把「grep 到 `sk-`」直接当成事故，就会产生一个永久的误报，最后所有人都学会忽略这条检查。
**判定规则**：命中后再看该值是否为占位（连续同一字符 / `changeme` / `<...>`），是占位则放行。

`ghp_`（经典 PAT）、`github_pat_`（细粒度 PAT）、`AKIA`（AWS Access Key）三类完全没有命中。

## 五、两条如实记录的边界

### 5.1 非提供方模式检测与有效性校验：本仓库不可用

`secret_scanning_non_provider_patterns` 与 `secret_scanning_validity_checks` 在改后仍然是 `disabled`。
实际动作与结果：

```console
$ gh api -X PATCH repos/lifuchun522/springaialibabapractice \
    --input <(echo '{"security_and_analysis":{"secret_scanning_non_provider_patterns":{"status":"enabled"},"secret_scanning_validity_checks":{"status":"enabled"}}}')
（HTTP 200，无报错）
$ gh api repos/lifuchun522/springaialibabapractice --jq '.security_and_analysis.secret_scanning_non_provider_patterns'
{"status":"disabled"}
```

**API 返回成功但状态不变**——判断为账号计划层面的能力限制（非组织仓库 / 免费计划不支持这两项），
不是我们的配置错误。如实记录在这里，而不是写成「已开启」。

因此本仓库的**阻断**能力实际只有两项：`secret_scanning`（检测）+ `secret_scanning_push_protection`（推送时阻断），
这两项都是 `enabled`。非提供方模式（例如自研系统的私有 token 形态）扫不到，属于已知缺口，
补偿手段是第六节的人工自检与 PR Checklist。

### 5.2 `SECURITY.md` 里的私密报告入口在此之前是死链

`private-vulnerability-reporting` 原本是 `false`，也就是说 `security/advisories/new` 点进去是 404 ——
一份指向 404 的安全政策比没有安全政策更伤信任。本次开启后才成立，这条依赖关系值得单独记下来：
**写「请走私密报告」之前，先确认私密报告入口真的存在。**

## 六、密钥泄漏应急顺序（顺序反了等于没修）

```text
1. 吊销并轮换凭证        ← 第一步，永远是这一步
   - 去对应平台把泄漏的 Key / 口令作废，生成新值
   - 更新所有消费方：CI Secrets（仓库级 / production 环境级）、服务器 .env、本地开发环境
2. 然后才清理提交历史
   - git filter-repo / BFG 去掉历史中的文件
   - 强推（注意：main 有 ruleset，需要按第 05 保的紧急流程临时降级 enforcement）
   - 通知所有协作者重新克隆
3. 复盘
   - 为什么会被提交进来：缺检测？缺 .gitignore？缺 PR Checklist？
   - 补上对应门禁，并在 PR / Issue 里留书面记录
```

**为什么不能反过来**：清理历史**不会让凭证失效**。Key 一旦公开，任何人拿到都能用，
删掉文件只是让「已经泄漏」这件事更难被发现而已。
先清历史 = 花了大代价（重写历史、全员重新克隆）却仍在裸奔。

## 七、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 11.1 | `SECURITY.md` 含支持范围/私密报告/响应约定/范围外四节 + 英文优先声明 | ✅ | `.github/SECURITY.md` 一至三节与顶部声明 |
| 11.2 | 私密漏洞报告入口可用（`enabled: true`） | ✅ | 第四节 |
| 11.3 | Dependabot alerts 与 security updates 已开（退出码 0） | ✅ | 第三节开启 + 第四节改后断言；改前 404 见第二节 |
| 11.4 | `dependabot.yml` 合法，两条 ecosystem + 分组 + 忽略主版本跳变 | ✅ | 文件内容；标签均在 `labels.txt` 内 |
| 11.5 | 供应链检测状态与 Actions 默认权限落盘 | ✅ | 第二节（改前）+ 第四节（改后） |
| 11.6 | 泄漏应急顺序写明「先吊销后清历史」 | ✅ | 第六节 |
| 11.7 | 仓库无明文凭据（示例均为占位） | ✅ | 4.1 自检输出 |
| 11.8 | 非提供方模式检测的状态与原因被如实记录 | ✅ | 5.1（未开启，附实测命令与判断依据） |

## 八、明确不做

| 不做 | 什么条件下再评估 |
| --- | --- |
| 开 Code scanning（CodeQL 自定义规则） | 仓库已有 CodeQL 默认扫描在跑；只有出现具体误报/漏报时再上自定义查询 |
| 引入第三方 SCA（Snyk / OWASP Dependency-Check） | Dependabot 覆盖不到的语言或生态出现时；当前 Maven + Actions 已被覆盖 |
| 给依赖升级 PR 设自动合并 | Spring 栈升级需要看 CI 与运行证据，自动合并会把风险直接送到 main |
| 追求 `secret_scanning_non_provider_patterns` | 平台能力具备时（组织仓库或更高计划）；当前用人工自检 + PR Checklist 补偿 |
| 对 `chapter/**` 分支回溯安全修复 | 不评估：历史分支是只读参考，回溯修复会让版本契约失效 |
