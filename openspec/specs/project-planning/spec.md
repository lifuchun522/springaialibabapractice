# project-planning Specification

## Purpose
TBD - created by archiving change github-ops-13. Update Purpose after archive.
## Requirements
### Requirement: 作战盘字段承载元数据
仓库 SHALL 在 GitHub Projects（用户级或仓库级）建立一张作战盘 Project，字段至少含 7 个：
状态（单选：待办/进行中/评审中/已完成）、优先级（单选：P0/P1/P2/P3）、类型（单选：章节/文档/实验/治理）、
章节（单选）、版本（单选，与 Milestone 同名对齐）、开始日期（日期）、目标日期（日期）。
字段定义 MUST 可被导出为 JSON 并落盘。

#### Scenario: 字段齐备可读回
- **WHEN** 执行 `gh project field-list <number> --owner lifuchun522 --format json`
- **THEN** 输出含上述 7 个字段，且类型为 single_select 的字段都带 options
- **AND** 状态字段的 options 恰为「待办/进行中/评审中/已完成」

#### Scenario: 字段快照落盘
- **WHEN** 检查 `docs/github-ops/project-fields.json`
- **THEN** 文件为 GraphQL 查询 `projectV2.fields` 的真实输出
- **AND** 其中不含占位 id（如 `PVTSSF_xxx`）

### Requirement: 视图按用途分层
作战盘 SHALL 至少提供四类视图：Table 总览、Board（按状态）、Roadmap（按日期）、按版本分组。
视图用途 MUST 在 `docs/github-ops/09-project-planning.md` 中写明「这个视图回答什么问题」。

#### Scenario: 视图职责无重复
- **WHEN** 检查文档中的视图清单
- **THEN** 四个视图各自对应一个明确问题（排期？状态？版本？总览）
- **AND** 不存在两个视图回答同一问题

### Requirement: 条目可脚本同步
仓库 SHALL 提供 `scripts/gh-project-sync.sh`（或等价脚本）把 Issue/PR 加入作战盘并回填字段，
脚本 MUST 幂等：同一对象重复同步不得产生重复条目。

#### Scenario: 重复同步不重复建条目
- **WHEN** 对同一个 Issue 连续两次执行同步脚本
- **THEN** 作战盘中该 Issue 只有一条条目
- **AND** 脚本输出显示第二次为「已存在，跳过」

#### Scenario: 归属校验
- **WHEN** 同步一个不属于本仓库的 URL
- **THEN** 脚本以非零退出码结束并打印原因
- **AND** 作战盘中不产生任何条目

### Requirement: 排期可导出交接
第 09 保 SHALL 把作战盘条目导出为 JSON 落盘，使排期不依赖人的记忆即可交接。

#### Scenario: 交接不需要口头补充
- **WHEN** 第二人只读 `docs/github-ops/09-project-planning.md` 与导出 JSON
- **THEN** 能说清「哪些章节已完成、哪些进行中、下一章是什么、对应哪个版本」
- **AND** 文档给出了重新导出该 JSON 的命令

