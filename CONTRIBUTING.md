# 贡献指南（Contributing）

感谢愿意花时间。本仓库是「降 Spring AI 阿里」18 掌的**练习仓库**：系列文章负责讲「为什么这么定」，
仓库负责把「实测成什么样」摆出来。所以这里的判定口径很简单——**写在文档里不算数，跑出来的才算数**。

> 语言约定：本文件与仓库内文档正文用中文；技术名词首次出现写成「英文原名（中文说明）」，例如 Label（标签）。
> 用词口径见 [`docs/github-ops/glossary.md`](docs/github-ops/glossary.md)。
> 行为准则与安全政策的**英文版本优先**，中文译文只作参考；两者冲突时以英文为准。

## 一、你可以怎么贡献

| 我想…… | 走哪里 | 前置 |
| --- | --- | --- |
| 报告缺陷 | [缺陷报告表单](.github/ISSUE_TEMPLATE/01-bug.yml) | 附最小复现步骤 + 环境版本 |
| 提交文档修正 | 直接开 Pull Request，标题用 `[文档]` 前缀 | 贴出文档原文与实测原文两边 |
| 提新能力或改进 | [功能建议表单](.github/ISSUE_TEMPLATE/02-feature.yml) | 先说清「谁在什么情况下需要它」 |
| 认领入门任务 | 筛选标签 `good first issue` | 每个任务都带验收标准，可直接开工 |
| 问「跑不起来」 | [Discussions 问答](https://github.com/lifuchun522/springaialibabapractice/discussions) | 工作日 48 小时内首次响应 |
| 报安全问题 | [私密漏洞报告](https://github.com/lifuchun522/springaialibabapractice/security/advisories/new) | **不要在公开 Issue 贴细节**，见 [SECURITY.md](.github/SECURITY.md) |

空白 Issue 已关闭：自由文本没有字段，无法判重、无法定义完成、无法关闭。
不确定是不是缺陷时，先到 Discussions 提问，别急着开 Issue。

## 二、提交前（三步，缺一步就别推）

1. 查 [`docs/github-ops/glossary.md`](docs/github-ops/glossary.md)，确认用词（尤其是**不可翻译清单**里的项）。
2. 跑一次标签同步脚本，确认标签面没有漂移：

   ```bash
   CHECK_ONLY=1 bash scripts/gh-labels.sh   # 只自检，不写远端
   ```

3. 在 PR 自检清单里逐项打勾——打不了勾的项，请在 PR 正文里说明为什么。

## 三、本地流程（详细版见 [02-local-clients.md](docs/github-ops/02-local-clients.md)）

```bash
git switch main && git pull --ff-only
git switch -c <类型>/<简短描述>      # 例如 githubops/03-chinese-localization
git add -p                          # 按块暂存，别把无关文件带进来
git commit -m "docs(gh03): ..."     # Conventional Commits，正文中文
git push -u origin HEAD
gh pr create --draft --fill         # 草稿 → ready → checks → squash merge
```

红线：

- **不要**直推 `main`，也**不要** `git push --force`（第 05 保的 ruleset 会在服务端拒绝）；
- 被规则拒绝时不要临时关规则，正确路径是 `git revert <sha>` 或重开 PR；
- **不要**把 API Key、口令、本地配置提交进仓库（`.gitignore` 已排除 `.env`、`*.local.yml`、`*.pem` 等）；
- 截图与日志里不得出现 token 与个人邮箱。

## 四、代码与文档约定

| 项 | 约定 |
| --- | --- |
| 编译基线 | JDK 21，`./mvnw -B -ntp clean verify` 必须通过（CI 同一道门） |
| 提交信息 | Conventional Commits：`type(scope): 中文描述`；类型用 `feat/fix/docs/refactor/test/chore` |
| 章节分支 | `chapter/NN-<主题>`，历史章节分支视为只读参考，不再回溯修复 |
| 治理改动 | 走 `githubops/NN-<主题>`，改动落在 `docs/github-ops/` |
| 文档口径 | 每个断言都要能指向一条命令输出、一个产物文件或一个 Issue/PR 编号 |
| 平台层 | Topics、Actions 关键字、Issue Form 的 YAML 键名、API 字段名**一律不翻** |

## 五、评审与响应承诺

| 事件 | 承诺 |
| --- | --- |
| 新 Issue | 工作日 48 小时内首次响应（先确认收到，再给结论） |
| 新 PR | 72 小时内首次评审 |
| 标记为答案的问答 | 7 天内把通用结论回写到 README 或 `docs/` |

首次响应模板（避免空话）：**欢迎与感谢 → 明确下一步 → 给出可执行的验证命令**。
不承诺无法兑现的时间点。关闭任何 Issue 或讨论都要写明原因。

## 六、许可

贡献即表示同意以本仓库的 [Apache-2.0](LICENSE) 许可发布。LICENSE 正文不翻译。
