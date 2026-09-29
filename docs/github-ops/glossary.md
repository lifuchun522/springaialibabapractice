# 13太保玩转github 仓库用词词汇表（第 03 保产物）
#
# 三层口径：内容层全翻；约定层翻值不翻键；平台层绝对不翻。
# 本表是 PR 自检清单里「新增文案符合词汇表写法」这一条的判定依据。
#
# 用法：写文档/模板前先查这里；改这里必须与 .github/ISSUE_TEMPLATE/*.yml 的 labels 值同步。

| 英文原名 | 中文写法 | 首现规则 | 层级 |
| --- | --- | --- | --- |
| Issue | Issue | 首现写 Issue（问题），此后用 Issue | 内容层 |
| Pull Request | Pull Request | 首现写 Pull Request（拉取请求），此后用 PR | 内容层 |
| Label | 标签 | 首现写 Label（标签），此后用「标签」 | 约定层（值） |
| Milestone | 里程碑 | 直接中文 | 约定层（值） |
| Discussion | Discussion | 保留英文 | 平台层 |
| Release | Release | 保留英文 | 平台层 |
| Topics | Topics | **不可翻译**，保持小写英文连字符 | 平台层 |
| Ruleset | Ruleset | 保留英文 | 平台层 |
| Workflow | 工作流 | 直接中文 | 内容层 |
| Actions | Actions | 保留英文（指 GitHub Actions 时） | 平台层 |
| Code Owner | Code Owner | 保留英文（CODEOWNERS 文件名不翻） | 平台层 |
| Triage | 待确认 | 直接中文（标签名 `triage` 不翻） | 约定层（键不翻） |
| Issue Form | Issue 表单 | 首现写 Issue Form（Issue 表单），此后用「表单」 | 约定层（键不翻） |

## 不可翻译清单（改这些等于改机器可读的契约）

| 项 | 位置 | 翻译后的后果 |
| --- | --- | --- |
| 表单 YAML 键名 | `.github/ISSUE_TEMPLATE/*.yml` 的 `type`/`id`/`attributes`/`validations` | 表单提交失败或字段不生效 |
| `blank_issues_enabled` / `contact_links` | `.github/ISSUE_TEMPLATE/config.yml` | GitHub 读不到配置，空白 Issue 仍可创建 |
| Topics | 仓库 About 面板 | 搜索侧失效，且可能被判定为非法标签 |
| Actions 关键字 | `.github/workflows/*.yml` 的 `on`/`jobs`/`steps`/`uses`/`run`/`permissions` | 工作流校验失败，整个运行连 job 都建不出来 |
| API 字段名 | 脚本文档中的 `--json` 参数、GraphQL 字段 | 命令报错，快照解析失败 |
| `releases[].categories[].labels[].*` | `.github/release.yml` | Release notes 分类失效 |
