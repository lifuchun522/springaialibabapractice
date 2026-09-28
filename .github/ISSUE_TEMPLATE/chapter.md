---
name: 章节任务
about: 每掌一个 Issue，写清本章要落的能力、验收标准与证据
title: "ch01 · 亢龙有悔 · 识势选型"
labels: chapter
---

## 章节信息

- 系列：降 SpringAI 阿里 十八掌
- 文章：<https://cloud.tencent.com/developer/article/xxxxxxx>
- 分支：`chapter/NN-主题`
- 对应文档：`docs/chNN-*.md`

## 本章要落的能力

<!-- 只写这一章的能力，不写后面的预留与抽象 -->

-
-

## 验收标准（可验证，不写「感觉做完了」）

- [ ] `mvn -B -ntp clean test` 通过，且用例不是 0 个
- [ ] 真实模型调用有一次可复现的请求与响应（无有效密钥时明确写「未验证」，不写「应该能通」）
- [ ] `git diff` 中不出现任何真实 API Key
- [ ] `docs/chNN-验收记录.md` 已更新（命令 + 真实输出）

## 证据

```console
# 命令与输出粘贴到这里
```

## 遗留问题

<!-- 本章刻意不解决的问题，留给哪一章 -->
