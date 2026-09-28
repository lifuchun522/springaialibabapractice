# 第 15 掌 验收证据（五层测试与六类回归集）

本目录是**第 15 掌的可核对证据**：脚本负责重跑，原始输出负责留档。
每条结论都能顺着这里的文件名回到一次真实运行，而不是回到一句「应该没问题」。

## 脚本与产物对照

| 脚本 | 层 | 原始输出 | 判据 |
| --- | --- | --- | --- |
| `01-run-offline.ps1` | L1 + L2（CI 阻断路径） | `01-offline-verify.txt` | 三个模块 `BUILD SUCCESS`，用例数与 CI 一致 |
| `02-run-integration.ps1` | L3（Testcontainers） | `02-integration-testcontainers.txt` | 真实 MySQL 容器起来、六条迁移 applied、实体读写往返一致 |
| `03-run-eval-offline.ps1` | L4（离线回归评测） | `03-eval-offline-replay.txt` | 快照重放结论与基线一致 |
| `04-run-eval-live.ps1` | L5（在线评测） | `04-eval-live.txt`、`04-eval-run-record.json`、`04-recorded-baseline.json` | 记录带数据集版本/模型/时间/逐条明细；每条 LIVE 用例都有真实回答 |
| `05-install-baseline.ps1` | L4 基线装载 | （打印到控制台，见 `04-recorded-baseline.json`） | 把 L5 的输出快照人工确认后装进 `src/test/resources/regression/recorded/` |
| `06-attribution-negative-control.txt` | L2 归因验证（负向对照） | 同左 | 临时注释掉工具注册后，**只有**工具定义那条用例变红，失败信息给出真实请求体 |

## 顺序很重要

`01-run-offline.ps1` 里的 `clean verify` 会删掉 `target/`，而 L5 的记录写在 `target/eval-runs/`。
所以证据采集的顺序是 **01 → 02 → 03 → 04 → 05 →（重跑 03 确认）**；
若先跑 04 再跑 01，`target/eval-runs/` 会被清空，记录也就没了（本掌实测踩到一次）。

## 跑之前必须知道的两件事

1. **tag 隔离是「两条命令成对」**。Surefire 里 `excludedGroups` 与 `groups` 同时命中时**排除优先**，
   只写 `-Dgroups=integration` 会一条用例都跑不到，而且**构建成功**——这是最容易被误读成「跑过了」的一种假绿。
   所以按层触发时必须成对写：

   ```bash
   ./mvnw -B -ntp test -pl digital-human -Dsurefire.groups=integration -Dsurefire.excludedGroups=
   ```

2. **`.ps1` 必须是带 BOM 的 UTF-8**。本目录的脚本要在 Windows PowerShell 5.1 下也能跑，
   而 5.1 读无 BOM 的 UTF-8 会按 GBK 解码，中文注释和中文输出会直接变成语法错误
   （本掌实测踩到过：`Unexpected token`）。另外脚本里 `$ErrorActionPreference` 用 `Continue` 而不是 `Stop`——
   `mvnw` 往 stderr 写 Mockito 警告时，PS 5.1 会把它当终止性错误，脚本会在打印结论之前就中断。

## 关于这些证据本身

- L5 的记录是**一次运行的快照**，不是长期趋势：趋势要落库（本掌没有 Admin 侧模块，见设计文档遗留问题）。
- `04-recorded-baseline.json` 里包含**通过基线**（11 条）与**已知失败**（pk-003）：
  失败用例是故意留在数据集里的真实缺陷，不是笔误，改成绿才是真的坏。
- Judge 的分数（`judgeScore`）只在阈值 60 上做通过判断，**不对外当准确率使用**：
  未经定期校准的 Judge 分数只是噪声的量化，校准方法与样本见 `docs/ch15-验收记录.md`。
