# local-client-contract Specification

## Purpose
TBD - created by archiving change github-ops-13. Update Purpose after archive.
## Requirements
### Requirement: 三入口收敛为一条验收路径
仓库 SHALL 以 Git 为底层、`gh` CLI 为唯一权威验收路径、GitHub Desktop 仅做中文菜单映射。
`docs/github-ops/02-local-clients.md` MUST 给出「Git/gh 命令速查」与「Desktop 英文菜单中文对照表」两张表，
对照表 MUST 不少于 20 条菜单项并逐条给出等价命令；命令速查 MUST 不少于 15 条。

#### Scenario: 新人十分钟内完成首次 PR
- **WHEN** 一名未接触过本仓库的开发者只按 `02-local-clients.md` 操作
- **THEN** 在 10 分钟内完成 clone → 建分支 → 提交 → push → `gh pr create`
- **AND** 全过程未使用 Desktop 全流程自动化，也未查阅仓库外资料

#### Scenario: 对照表覆盖真实菜单
- **WHEN** 打开 GitHub Desktop 英文界面逐项核对对照表
- **THEN** 表中每一行都能在界面上找到对应菜单项，且「对应命令」列的命令可直接执行

### Requirement: 换行符与忽略策略写进仓库
仓库 SHALL 通过 `.gitattributes` 统一文本行尾策略（`* text=auto eol=lf`，Windows 批处理脚本除外），
并通过 `.gitignore` 排除 `.env`、日志、IDE 目录与构建产物。
凭据类文件 MUST NOT 出现在版本库中，任何截图 MUST NOT 出现 token 或邮箱。

#### Scenario: LF 策略生效
- **WHEN** 在 Windows 上克隆并执行 `git status`
- **THEN** 工作区为 clean，不出现「整文件行尾差异」类改动
- **AND** `.bat` / `.cmd` 文件仍保留 CRLF

#### Scenario: 凭据未入库
- **WHEN** 执行 `git ls-files | Select-String -Pattern '\.env$|\.env\.'`
- **THEN** 输出为空（仅允许 `.env.example` 这类模板文件）

#### Scenario: 冲突只解不覆盖
- **WHEN** 按文档处理一次真实合并冲突
- **THEN** 文档要求的顺序为 `git status` → 逐处解决 → `git add -p` → `git commit`
- **AND** 文档明确禁止 `git push --force` 覆盖他人历史，并给出改用 `git revert` 的路径

