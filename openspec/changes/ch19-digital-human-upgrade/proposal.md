# Proposal

## Why

仓库现有的主架构图（`README.md` 第十节）只画了「浏览器 → digital-human → DeepSeek / MCP / MySQL」一条链路，
**实时交互层与语音能力完全没有出现在图上**：`livekit-agent`、`livekit-server`、`tts`、`asr`、`nacos` 五个组件在代码与编排里都不存在。
后果不是「图不好看」，而是三件事无法回答：

1. 数字人项目要「能看见、能听见、能说话」时，能力从哪一层进、替换供应商要动谁——图上没有位置。
2. 第 3 掌已经把「实时链路」记为明确的欠账（`docs/ch03-数字人底座.md` 第四节：「LiveKit 实时语音……单独成章/单独 PR」），
   但第 4–15 掌一路推进后，这笔账始终没有章节来还。
3. Nacos 已经半落地却不可用：`knowledge-agent` 有 `NacosRegistrar`，`digital-human` 有 `AgentRegistrySupport`，
   但两个开关默认 `false`、编排文件里没有 Nacos、`NacosRegistrar` 还把实例 IP 硬编码成 `127.0.0.1`——
   **注册中心在文档里存在，在运行环境里不存在**。

所以本掌做两件事：把这些组件放回架构图的正确位置，并把「注册」这件事真正跑通到可验收。

## What Changes

**架构与文档（本变更的第一交付）**

- `README.md` 第十节的架构图从「一条链路」扩为**三条链路**（管理 / 对话 / 注册），
  五个组件各归其层：`livekit-server`+`livekit-agent` 进实时交互层，`tts`+`asr` 进能力层，`nacos` 进注册层。
- 新增 `docs/ch19-数字人功能升级.md`：五组架构图（总架构 / 语音链路 / 注册中心 / 运行页数据流 / 实时交互预留）
  与「数字人主流程」五步的逐步落地口径。
- `README.en.md` 同步同一张图——不同步就是中英两份架构漂移。

**能力实现（第二步执行）**

- 新增能力 `service-registry`：Nacos 官方镜像 + 复用同一 MySQL 实例（新增 `nacos_config` 库）；
  A2A 实例注册发现跑通；MCP 服务清单登记上去；Nacos 不可用时**显式降级**而不是连带服务下线。
- 新增能力 `voice-io`：TTS 与 ASR 接阿里云 NLS（实时流式），但调用方只依赖 Spring AI 标准
  `TextToSpeechModel` / `TranscriptionModel` 接口；NLS 临时 token 由服务端签发。
- 新增能力 `project-config`：数字人项目的预置配置目录（音色 / 形象 / 开场白 / 立场白 / 知识库 / MCP / A2A）
  与「发布」状态机——**未发布的配置改动不得影响已发布的运行实例**。
- 新增能力 `digital-human-runtime`：对外开放的 Web 入口（登记用户名即可对话），
  输出字幕（时间轴）、声音（音轨）、标签（可溯源）与形象（前端驱动 + 预留驱动接口）。

**明确不做（记账，不装作已完成）**

- 不部署 `livekit-server` / `livekit-agent` 容器，不做真实口型与表情推理——需要算法镜像与 GPU，
  沿用第 3 掌「单独成章」的口径，本掌只在图上与接口上给它留位。
- 不把 Nacos 当配置中心用：本掌只用它的注册与发现。
- 不解决令牌持久化：`X-Token` 仍是进程内存态，重启失效，如实记录。

## Capabilities

### New Capabilities

- `service-registry`：Nacos 作为 MCP 与 A2A 的注册管理中心——实例注册、健康校验、服务清单登记、
  不可用时的显式降级，以及「注册中心不承载工具 schema 真值」这条边界。
- `voice-io`：TTS 合成与 ASR 识别能力——供应商无关的标准接口、服务端签发临时凭证、
  流式音频与识别时间轴对外契约。
- `project-config`：数字人项目的可配置项与发布状态机——预置目录的可选项、配置校验、
  草稿与已发布版本的隔离。
- `digital-human-runtime`：对外开放的交互入口——登记用户名、会话建立、
  字幕 / 声音 / 标签 / 形象四路输出的契约与降级行为。

### Modified Capabilities

无。本仓库此前没有任何 OpenSpec 规格（`openspec/specs/` 为空），因此不存在需要变更的既有能力。

## Impact

**受影响代码**

| 位置 | 影响 |
|------|------|
| `digital-human` | 新增语音适配层、项目配置目录与发布状态机、开放运行入口；`AgentRegistrySupport` 增补健康校验 |
| `knowledge-agent` | `NacosRegistrar` 修正实例 IP 来源并增加注册结果校验 |
| `digital-human-mcp` | 启动期登记 MCP 服务清单到 Nacos（不搬运工具 schema） |
| `deploy/docker-compose.yml` | 新增 `nacos` 服务，复用现有 MySQL 实例 |
| `deploy/init-db.sql` | 新增 `nacos_config` 库与 Nacos 2.4.x 表结构 |
| `README.md` / `README.en.md` | 架构图与进度表同步 |
| `docs/ch19-*` | 新增章节设计与验收记录 |
| `scripts/evidence-ch19/` | 新增验收证据 |

**新增外部依赖**

- 镜像：`nacos/nacos-server`（版本在 design.md 定死）
- Java 依赖：阿里云智能语音交互 NLS SDK（版本与坐标在 design.md 定死）
- 外部服务：阿里云 NLS（需要 AccessKey，只从环境变量注入）

**新增运行期配置**

- `NACOS_SERVER_ADDR`、`NACOS_MYSQL_*`、`ALIYUN_NLS_ACCESS_KEY_ID`、`ALIYUN_NLS_ACCESS_KEY_SECRET`、`ALIYUN_NLS_APP_KEY`

**风险**

- 注册中心与语音能力都是**外部依赖**，本机与 CI 不一定具备。
  对策：两者都必须可关闭且关闭后服务照常启动，离线测试用假适配器覆盖；
  真实往返证据（Nacos 注册查询、NLS 调用）在 `scripts/evidence-ch19/` 留档，不在 CI 里强跑。
