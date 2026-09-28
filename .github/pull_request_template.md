## 关联 Issue

Closes #

## 本章改动

<!-- 一句话说清这一掌让系统多了什么能力 -->

-

## 验证证据

| 项 | 命令 | 结果 |
|----|------|------|
| 编译 | `mvn -B -ntp clean compile` | |
| 单测 | `mvn -B -ntp clean test` | |
| 真实调用 | `curl "http://localhost:8080/api/chat?q=..."` | |

```console
# 关键输出的原文粘贴
```

## 自查

- [ ] 只包含本章改动，没有夹带后续章节的预留
- [ ] 无 API Key / 口令 / 本地配置入库（`git diff` 自检过）
- [ ] 文档与验收记录已同步
- [ ] 合并进 main 后 `mvn -B -ntp clean test` 仍通过
