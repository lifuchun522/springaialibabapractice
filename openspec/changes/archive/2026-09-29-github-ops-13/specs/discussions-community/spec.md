# discussions-community（第 04 保：社区讨论）

## ADDED Requirements

### Requirement: 五类讨论分类承载分流
仓库 SHALL 在 Discussions 中配置五类分流分类，且只有「问答」类型 MUST 可标记答案（`isAnswerable = true`）：
公告（Announcement 格式）、问答（Question and answer）、建议讨论（Open-ended）、作品展示（Open-ended）、投票（Open-ended）。
分类总数 MUST NOT 超过 6 个，避免分类变僵尸。

#### Scenario: 分类读回与格式正确
- **WHEN** 执行 GraphQL 查询 `repository { discussionCategories(first: 20) { nodes { name slug isAnswerable } } }`
- **THEN** 返回的节点中，问答类分类的 `isAnswerable` 为 `true`
- **AND** 公告、建议讨论、作品展示、投票四类的 `isAnswerable` 为 `false`
- **AND** 分类总数 ≤ 6

#### Scenario: 分流规则可判定
- **WHEN** 阅读 `docs/github-ops/04-discussions-community.md` 的分流规则表
- **THEN** 已明确 Bug 与已确定需求走 Issue，未成形建议走建议讨论，使用问题走问答，官方公告走公告，方向征集走投票
- **AND** 每条规则都能对应到一个可执行判断（而不是「看情况」）

### Requirement: Q&A 闭环与响应承诺
仓库 SHALL 承诺问答分类在工作日 48 小时内首次响应；被标记答案的讨论 MUST 在 7 天内把通用结论回写到
README 或 `docs/`，并在讨论里回复指向文档的链接后关闭。
关闭任何讨论 MUST 写明原因。

#### Scenario: 只读巡检能列出未回答问题
- **WHEN** 执行 `scripts/gh-discussions-audit.sh`
- **THEN** 输出列出所有 `isAnswerable` 且 `isAnswered == false` 的开放讨论的「分类 + 编号 + 标题 + URL」
- **AND** 脚本只执行查询，不产生任何写操作（不含 `mutation`）

#### Scenario: 公告模板要素齐全
- **WHEN** 检查 `docs/github-ops/04-discussions-community.md` 中的公告模板
- **THEN** 模板含「发布人」「依据版本」「失效日期」三个字段
- **AND** 缺任一项时文档明确该公告不应发出

#### Scenario: 欢迎模板可被 GitHub 识别
- **WHEN** 检查 `.github/DISCUSSION_TEMPLATE/welcome.yml`
- **THEN** 文件为合法 YAML，含 `title` 与 `body`，且正文为中文
- **AND** 在仓库创建讨论时该模板出现在模板选择列表中
