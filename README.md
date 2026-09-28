# springaialibabapractice

「降 SpringAI 阿里」十八掌练习仓库：从一个最小骨架，逐掌长成数字人 Agent 平台。

系列文章（腾讯云开发者社区，作者李福春）：<https://cloud.tencent.com/developer/article/2752108>

## 技术基线

| 项 | 取值 |
|----|------|
| JDK | 21 LTS |
| Spring Boot | 3.5.10 |
| Spring AI | 1.1.2 |
| Spring AI Alibaba | 1.1.2.2（`v2.0.0-M1.1` 属 Pre-release，只观察不进生产） |
| 模型 | DashScope，默认 `qwen-plus` |
| 构建 | Maven 3.9+（`mvn -B -ntp test`） |

## 模型通道

本项目**只提供一条模型通道**：DeepSeek（OpenAI 兼容协议），业务代码只依赖 `ChatClient`。

| 项 | 值 | 配置项 |
|----|----|--------|
| 通道 | DeepSeek（OpenAI 兼容） | `spring.ai.model.chat=openai` |
| 模型 | `deepseek-flash` | `spring.ai.openai.chat.options.model` |
| 基址 | `https://api.deepseek.com` | `spring.ai.openai.base-url` |
| 密钥 | 环境变量 `DEEPSEEK_API_KEY` | `spring.ai.openai.api-key` |

系列基线是 DashScope，但本仓库当前只验证 DeepSeek 通道，所以不引入「引了但不用」的通道——
等需要 DashScope 的章节（第 3 掌语音、第 8 掌 Embedding）再按章引入。

```bash
export DEEPSEEK_API_KEY=sk-xxxx          # Windows: set DEEPSEEK_API_KEY=sk-xxxx
mvn -pl digital-human spring-boot:run
curl "http://localhost:8080/api/chat?q=用一句话介绍你自己"
```

无密钥时**无法启动**：模型 SDK 会在启动期断言 api-key 非空（`OpenAI API key must be set`）。
这是刻意的启动期校验——密钥不对，就不要让服务假装健康地跑起来。
跑测试不受影响（测试注入假 Key，不发起真实调用）：

```bash
mvn -B -ntp test
```

## 协作流程：Issue → 分支 → PR → main

main 始终是最新的可用状态，任何一章都不直接往 main 上写：

```text
Issue（本章要落的能力与验收标准）
   └─ 分支 chapter/NN-主题   ← 只做这一章
        └─ PR（关联 Issue，贴真实验证证据）
             └─ 合并进 main
```

分支名沿用系列文章里的约定：`chapter/01-value-selection`、`chapter/03-digital-human-demo` 等。
提交信息用 `type(chNN): 描述`，一次提交只对应一掌。

## 密钥规范

- 真实密钥**只走环境变量**，或放在本地 `.env.local` / `application-local.yml`（均已在 `.gitignore` 中）。
- 仓库里只允许出现 `.env.example` 这类占位文件，值一律是 `sk-xxxx`。
- 提交前自查：`git diff --cached | findstr sk-`，出现真实 Key 就不要提交。

## 模块与章节进度

| 掌 | 主题 | 分支 | 状态 |
|----|------|------|------|
| 1 | 亢龙有悔 · 识势选型 | `chapter/01-value-selection` | 已完成：五层架构 + ChatClient 唯一出口骨架 |
| 2 | 飞龙在天 · 筑基环境 | `chapter/02-baseline-env` | 待做 |
| 3 | 见龙在田 · 数字人底座 | `chapter/03-digital-human-demo` | 待做 |
| 4 | 鸿渐于陆 · 御模对话 | `chapter/04-chat-model` | 待做 |
| 5 | 潜龙勿用 · 藏忆流式 | `chapter/05-memory-streaming` | 待做 |
| 6 | 利涉大川 · 御器工具 | `chapter/06-tools` | 待做 |
| 7 | 突如其来 · 通玄 MCP | `chapter/07-mcp` | 待做 |
| 8 | 震惊百里 · 入藏 RAG | `chapter/08-rag` | 待做 |
| 9 | 或跃在渊 · ReactAgent | `chapter/09-react-agent` | 待做 |
| 10 | 双龙取水 · 百阵流程 | `chapter/10-workflow-agents` | 待做 |
| 11 | 鱼跃于渊 · 图谱 Graph | `chapter/11-graph-core` | 待做 |
| 12 | 时乘六龙 · 分身多 Agent | `chapter/12-multi-agent` | 待做 |
| 13 | 密云不雨 · 跨域 A2A | `chapter/13-a2a-nacos` | 待做 |
| 14 | 损则有孚 · 溯源源码 | `chapter/14-source-pr` | 待做 |
| 15 | 龙战于野 · 试炼评测 | `chapter/15-eval-guard` | 待做 |
| 16 | 履霜冰至 · 立派服务 | `chapter/16-spring-service` | 待做 |
| 17 | 羝羊触藩 · 观星治理 | `chapter/17-observability-admin` | 待做 |
| 18 | 神龙摆尾 · 登云 K8s | `chapter/18-k8s-production` | 待做 |

## 目录

```text
pom.xml                 父 POM：BOM 统一 Spring AI / Spring AI Alibaba 版本
digital-human/          数字人应用模块（后续各掌在此长能力）
docs/                   每掌的设计与验收记录
```

## 约定

- 框架版本一律由 BOM 管理，子模块不写框架版本号。
- 业务代码只依赖 `ChatClient`，不直接注入底层模型对象。
- 密钥、口令只走环境变量，禁止进仓库。
- 一次提交只对应一掌；提交信息用 `type(chNN): 描述`。
