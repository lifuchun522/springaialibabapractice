# release-portal（第 10 保：发布门户）

## ADDED Requirements

### Requirement: Release 分类自动生成
仓库 SHALL 提供 `.github/release.yml`，把 PR 标签映射到中文分类（新增/修复/文档/依赖/其他），
并支持 `skip-changelog` 标签排除；分类 MUST 至少含一个 `'*'` 兜底分类，保证所有 PR 都落入某一类。

#### Scenario: 自动生成带分类的 release notes
- **WHEN** 执行 `gh release create v<版本> --verify-tag --generate-notes`
- **THEN** 生成的 notes 含「新增」「修复」「文档」「依赖」「其他」分类标题
- **AND** 带 `skip-changelog` 标签的 PR 不出现在 notes 中

#### Scenario: 分类覆盖全部标签
- **WHEN** 检查 `.github/release.yml` 的 `categories`
- **THEN** 最后一个分类的 labels 含 `'*'`
- **AND** 文中引用的每个标签名都存在于仓库标签列表（或已在文档中说明为可选）

### Requirement: 教程 tag 与软件版本分离
仓库 SHALL 明确 `ch*`（教程标签，指向章节内容）与 `v*`（软件 SemVer）两套标签语义，
并为软件版本建立首个 Release。Release MUST NOT 与教程 tag 混用同一命名空间。

#### Scenario: 首个 Release 可读回
- **WHEN** 执行 `gh release view v<版本> --json tagName,isDraft,url`
- **THEN** `isDraft` 为 `false`，`tagName` 与实际推送的 tag 一致
- **AND** `git rev-parse v<版本>^{commit}` 能解析到具体提交

#### Scenario: 标签语义不混用
- **WHEN** 检查 `docs/github-ops/10-release-pages.md`
- **THEN** 文档用一节说明 `ch01`–`ch18` 与 `v*` 的区别和各自用途
- **AND** 文档要求新增软件版本必须走 `v*`，不得复用 `ch*`

### Requirement: Pages 发布门户可访问
仓库 SHALL 通过 GitHub Actions 部署 Pages 站点（`docs/site/`），站点首屏 MUST 给出
Quick Start、掌次路线、FAQ、贡献入口四块内容，并 MUST 与仓库 homepageUrl 指向同一地址。

#### Scenario: 站点可访问且内容齐备
- **WHEN** 站点部署后请求站点根地址
- **THEN** HTTP 状态为 200，页面含 Quick Start、掌次路线、FAQ、贡献入口四块标题
- **AND** 页面语言标记为 `zh-CN`

#### Scenario: 工作流用官方 Pages 动作
- **WHEN** 检查 `.github/workflows/deploy-pages.yml`
- **THEN** 使用 `actions/configure-pages@v5`、`actions/upload-pages-artifact@v3`、`actions/deploy-pages@v4`
- **AND** 声明了 `permissions: pages: write` 与 `id-token: write`，并配置 `concurrency: pages`

#### Scenario: 发布门户是首页的唯一入口
- **WHEN** 执行 `gh repo view --json homepageUrl`
- **THEN** homepageUrl 等于实际 Pages 地址
- **AND** 文档记录了两者必须保持一致（改一处要改另一处）
