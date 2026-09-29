# 第 02 保：本地客户端（Git / GitHub Desktop / gh CLI）

> 对应内容：13太保玩转github-第2太保-李嗣昭-本地客户端
> 本轮目标：把三套本地入口收敛成**一条可复现的流程**——Git 管底层、`gh` 为唯一权威验收路径、Desktop 只做中文映射。
> 产物：本文、`.gitattributes` 的 LF 策略说明、`docs/github-ops/glossary.md`（第 03 保）用词口径。

## 一、为什么是「CLI 权威、Desktop 辅助」

过去的真实工单里，最常炸的不是命令写错，而是**冲突后直接强推**：历史没了、评审记录变成废纸、查不到人是谁改的。
根因不是工具差，而是三套入口三套记忆——桌面点几下、命令行补两刀、`gh` 现查现用，
Git 状态在脑子里对不上，于是「我这台机器能跑」成了默认真相。

所以本仓定死一条口径：

| 角色 | 工具 | 承诺 |
| --- | --- | --- |
| 底层 | Git | 管对象与历史；所有状态判断以 `git status` / `git log` 为准 |
| 权威 | `gh` CLI | **唯一验收路径**：PR、Issue、Ruleset、Release 全部用 `gh` 复现 |
| 辅助 | GitHub Desktop | 只保证「看得懂英文菜单」；不承诺全流程自动化 |

文档给 GUI 用户两条路径都能走通，但验收只认 CLI 那一条。

## 二、Git 全局配置（换机后先做这一步）

```bash
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
git config --global init.defaultBranch main
git config --global pull.rebase false
# Windows 建议 true；macOS/Linux 视团队策略取 input 或 false
# 注意：本仓库已在 .gitattributes 里钉死 * text=auto eol=lf，
# 所以无论这一项取什么，仓库内的行尾都以 .gitattributes 为准。
git config --global core.autocrlf true
git config --list --show-origin
```

**凭据红线**：授权只走 `gh auth login`，凭据绝不进仓库。`.gitignore` 已排除 `.env`、`.env.*`（保留 `.env.example`）、
`*.local.yml`、`*.pem` / `*.key` / `*.p12` / `*.jks`、`secrets/`。出事责任在个人账号，不能写成项目资产。

## 三、命令速查（15 条，全部在本仓库实测可用）

| # | 场景 | 命令 |
| --- | --- | --- |
| 1 | 克隆 | `git clone git@github.com:lifuchun522/springaialibabapractice.git` |
| 2 | 看自己在哪 | `git status -sb` |
| 3 | 建分支 | `git switch -c githubops/02-local-clients` |
| 4 | 拉主分支（只快进） | `git switch main && git pull --ff-only` |
| 5 | **按块暂存**（避免混入无关文件） | `git add -p` |
| 6 | 提交 | `git commit -m "docs(gh02): ..."` |
| 7 | 推分支并建立跟踪 | `git push -u origin HEAD` |
| 8 | 看本地与远端差异 | `git log --oneline -5 origin/main` |
| 9 | 看某个文件的历史 | `git log --oneline -- docs/github-ops/02-local-clients.md` |
| 10 | 撤销未暂存改动 | `git restore <file>` |
| 11 | 撤销已暂存改动 | `git restore --staged <file>` |
| 12 | 用反向提交回滚（**不用强推**） | `git revert <sha>` |
| 13 | 登录/确认身份 | `gh auth login` / `gh auth status` |
| 14 | 建 PR（草稿） | `gh pr create --draft --fill` |
| 15 | 等门禁 + 合并 + 删源分支 | `gh pr checks --watch` → `gh pr merge --squash --delete-branch` |

### 一分钟提交（维护者视角：一分钟看懂、三步提交）

```bash
git switch -c githubops/02-local-clients   # 1. 开分支
git add -p                                 # 2. 只挑本次相关改动
git commit -m "docs(gh02): document desktop git and gh workflows"
git push -u origin HEAD                    # 3. 推 + 建 PR
gh pr create --draft --fill
```

## 四、GitHub Desktop 英文菜单中文对照表（20 条）

Desktop 菜单在中文系统上仍是英文。这张表保证「看得懂」，但每行都给出等价命令，方便切到 CLI 验收路径。

| # | Desktop 菜单（英文） | 中文解释 | 等价 git / gh 命令 |
| --- | --- | --- | --- |
| 1 | File → Clone repository | 文件 → 克隆仓库：把远端仓库拉成本地目录 | `git clone <url>` |
| 2 | File → New repository | 文件 → 新建仓库：本地初始化并可选发布到远端 | `git init` + `gh repo create` |
| 3 | File → Add local repository | 文件 → 添加本地仓库：把已有目录纳管 | `git status`（确认已是仓库） |
| 4 | Repository → Repository settings | 仓库 → 仓库设置：改远端地址、忽略文件 | `git remote -v` / 编辑 `.gitignore` |
| 5 | Repository → Open in Command Prompt | 仓库 → 在命令行中打开 | 直接在终端 `cd` 到该目录 |
| 6 | Repository → Show in Explorer | 仓库 → 在文件管理器中显示 | `explorer .`（Windows） |
| 7 | Branch → New branch | 分支 → 新建分支：基于当前 HEAD 建分支并切换 | `git switch -c <branch>` |
| 8 | Current branch → Choose a branch | 当前分支 → 选择分支：切换工作分支 | `git switch <branch>` |
| 9 | Branch → Rename | 分支 → 重命名 | `git branch -m <new>` |
| 10 | Branch → Delete | 分支 → 删除 | `git branch -d <branch>` |
| 11 | Branch → Create pull request | 分支 → 创建拉取请求 | `gh pr create --fill` |
| 12 | Branch → Merge into current branch | 分支 → 合并到当前分支 | `git merge <branch>` |
| 13 | Branch → Rebase current branch | 分支 → 变基当前分支 | `git rebase <branch>`（**本仓不推荐**，main 走 squash） |
| 14 | Branch → Update from main | 分支 → 从 main 更新 | `git fetch && git merge --ff-only origin/main` |
| 15 | Branch → Compare on GitHub | 分支 → 在 GitHub 上比较 | `gh pr create --fill` 或仓库 `/compare` 页 |
| 16 | Changes → 勾选文件 | 变更 → 勾选要提交的文件（**等价按块暂存**） | `git add -p` |
| 17 | Changes → Commit to `<branch>` | 变更 → 提交到当前分支 | `git commit -m "<msg>"` |
| 18 | Changes → Discard changes | 变更 → 丢弃改动 | `git restore <file>` |
| 19 | Repository → Push origin / Push | 推送源端：把本地提交推到 origin | `git push -u origin HEAD` |
| 20 | Repository → Pull origin / Fetch origin | 拉取源端：取回远端提交 | `git pull --ff-only` / `git fetch` |
| 21 | History → Revert this commit | 历史 → 还原该提交：生成一个反向提交 | `git revert <sha>` |
| 22 | History → Reset to commit | 历史 → 重置到该提交（**破坏性**） | `git reset --hard <sha>`（本仓禁止对已推送提交使用） |

> 第 22 条要特别注意：Desktop 的 Reset 会重写本地历史，推到远端就变成强推。
> **本仓口径：已推送的提交只允许 `revert`（向前生成反向提交），不允许 `reset --hard` + 强推。**

## 五、冲突处理：只解不覆盖

顺序不能乱：

```bash
git status                 # 1. 先看清状态：哪些文件冲突、处于哪个阶段
git diff                   # 2. 看冲突具体内容（不要先动手删标记）
# 3. 逐处解决，保留双方意图；不确定就问，不要猜
git add -p                 # 4. 按块暂存，确认没混入无关改动
git status                 # 5. 复查：确认无残留 <<<<<<< 标记
git commit                 # 6. 完成合并提交
```

红线：

- **禁止** `git push --force` / `--force-with-lease` 到 `main`（第 05 保 ruleset 会在服务端拦下）；
- 被 ruleset 拒绝后，**不要**改用 Web 强推或临时关规则，正确路径是 `git revert` 或重开 PR；
- 冲突解决后必须 `git status` 复查，确认没有把 `<<<<<<<`、`=======`、`>>>>>>>` 标记提交进去。

## 六、验收记录

| # | 断言 | 结果 | 证据 |
| --- | --- | --- | --- |
| 2.1 | 本文含命令速查 ≥15 条与 Desktop 对照表 ≥20 条，且对照表每条都有等价命令 | ✅ | 第三节 15 条、第四节 22 条，每行都有「等价命令」列 |
| 2.2 | `.gitattributes` 统一 LF，`.bat`/`.cmd` 保留 CRLF，新增 `.sh` 明确 LF | ✅ | `.gitattributes` 第 2–9 行；本条改动见本 PR diff |
| 2.3 | `.gitignore` 已排除 `.env` 类凭据与构建产物 | ✅ | `git ls-files \| Select-String '\.env$'` 输出为空 |
| 2.4 | 含「冲突只解不覆盖」章节并给出 `git revert` 替代路径 | ✅ | 本文第五节 + 第四节第 22 条说明 |

### 实测命令输出

```console
$ git ls-files | Select-String -Pattern '\.env$|\.env\.'
（无输出：只有 .env.example 类模板，符合预期）
```

### 变更范围与不做的事

- **改了**：`.gitattributes`（补 `.sh` LF 与三个二进制扩展）、新增本文。
- **没改**：`.gitignore`（已有的凭据排除规则已经正确，本次只做核查不重写）；
  没有为 Desktop 编写全流程自动化，也没有承诺「Desktop 一条龙」——教程口径与仓库口径一致：
  Desktop 只保证看得懂菜单。
- **与教程的差异**：教程示例里 `.gitignore` 用 `*.log`/`logs/` 两条，本仓实际已有更严格的 `*.log` + 本地配置排除清单，
  直接沿用既有文件，不重复添加。
