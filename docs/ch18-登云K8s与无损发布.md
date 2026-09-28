# 第 18 掌 · 神龙摆尾（登云 K8s）：发版不掉线、Pod 挂了能自愈、发错了能回滚

> 文章：[降SpringAI阿里第18掌-神龙摆尾-登云K8s](https://cloud.tencent.com/developer/article/2752084)
> 分支：`chapter/18-k8s-production` ｜ 证据：`scripts/evidence-ch18/`
> 一句话：**本地 Compose 跑得欢，一上 K8s 长连接全断、会话全丢。**

## 1. 五条完成标准与落点

| 完成标准 | 本仓库落点 | 怎么验 |
| --- | --- | --- |
| 镜像可复现、非 root | `digital-human/Dockerfile`：多阶段、uid 10001、容器感知 JVM 参数 | `01-image-inspect.txt`、`02-container-identity.txt` |
| Pod 可被正确判定（三类探针语义分层） | `deploy/k8s/20-deployment.yaml`：startup→readiness、liveness 只探 `/liveness` | `scripts/validate-k8s-manifests.py` |
| 状态可外置 | 记忆改走账本（`LedgerChatMemoryRepository`，`digital-human.memory.store=jdbc`）；检查点第 11 掌就在 MySQL | `LedgerChatMemoryRepositoryTest`（多实例读同一会话） |
| 滚动可无损 | `maxUnavailable: 0` + `preStop` + `terminationGracePeriodSeconds: 60` + `server.shutdown=graceful` | `06-graceful-shutdown.txt/.log` |
| 回滚可执行 | `revisionHistoryLimit` + `kubectl rollout undo`；数据库结构走 Flyway 前向迁移，回滚不碰 schema | 见第 5 节的 honest gap |

## 2. 三个探针的语义分层（写错一个就是全集群震荡）

文章 03 章那段是这一掌最值钱的部分，本仓库按它落：

| 探针 | 探什么 | 失败后果 | 本仓库取值 |
| --- | --- | --- | --- |
| `startupProbe` | 允许慢慢启动 | 通过前抑制 liveness | `/actuator/health/readiness`，30×5s=150s（JVM + Flyway + 向量索引重建） |
| `readinessProbe` | 能不能接流量 | **只摘端点，不重启** | `/actuator/health/readiness`（含 configReadiness + db，第 16 掌的健康分组） |
| `livenessProbe` | 要不要重启 | 重启进程 | `/actuator/health/liveness`（**只含 ping**） |

为什么 liveness 绝不能用 readiness：把模型 API 或数据库写进 liveness，
依赖一抖 → 探针失败 → Pod 重启 → 重启期间又失败 → 整个 Deployment 一起躺平。
这就是文章 01 章第三个坑「多副本耦合坍塌」的机制。

## 3. 状态到底分几类（决定 Pod 能不能无状态）

| 状态 | 性质 | 本仓库放哪 |
| --- | --- | --- |
| 图检查点 | 可重放的执行快照，必须持久化 | MySQL `graph_checkpoint`（第 11 掌） |
| 对话记忆（Memory） | 用户可感知的连续性，必须共享 | **`chat_message` 账本**（本掌改的：`store=jdbc`） |
| 产品历史 | 用户可查的事实 | `chat_message` 账本（第 5 掌） |
| 模型客户端连接池、模板缓存、本地配置 | 可重建的派生物，允许留在 Pod 内 | 进程内，随 Pod 生死 |

最后一行是关键：**不是所有状态都要外置**，把可重建的派生物也搬出去，只是给自己加延迟。

## 4. 真实验收（没有集群，所以要说清验的是什么）

本环境没有可用的 K8s 集群（`kubectl` 有客户端、没有 context），所以**能验的都验实了，不能验的直说**：

**验实了的（用容器终止时序复现 K8s 的同样信号流）：**

```text
== 终止期间的 SSE 长连接 ==
终止时机      : 流式请求发出后 6 秒（模型仍在生成）
docker stop   : 16.3s（等待在途请求收尾）
data 帧数量   : 416
是否出现 error 帧 : False
客户端是否拿到完整回答 : True

日志：
23:25:28.930 INFO o.s.b.w.e.t.GracefulShutdown - Commencing graceful shutdown. Waiting for active requests to complete
23:25:35.867 INFO o.s.b.w.e.t.GracefulShutdown - Graceful shutdown complete
23:25:35.897 INFO HikariDataSource - HikariPool-1 - Shutdown initiated...
```

也就是：**SIGTERM 到达时正在生成的那条流，完整答完才退出**——这就是「滚动可无损」的机制本体。

**镜像与安全上下文（真实运行）：**

```text
User=10001:10001 WorkDir=/app
uid=10001(app) gid=10001(app) groups=10001(app)
touch: cannot touch '/app/jar-probe': Read-only file system     ← 只读根文件系统生效
容器内存上限 1024 MiB → JVM 堆上限 768 MiB = 75%                 ← 容器感知生效
```

**没验的（诚实清单）：**真集群上的 `kubectl apply` / `rollout status` / `rollout undo`、
HPA 实际扩缩、PDB 在 `kubectl drain` 时的行为、Ingress 注解在真实网关上的效果。
它们由 `scripts/validate-k8s-manifests.py` 做**结构校验**（把这一掌的上线判据写成可执行清单），
但结构对 ≠ 集群上跑通。

## 5. 真实验收抓到的两个问题

### 5.1 `host.docker.internal` 在纯 Docker 里不解析（已定位）

第一次用 `--read-only` 起容器直接退出，日志里是：

```text
Caused by: java.net.UnknownHostException: host.docker.internal: Name does not resolve
```

与仓库既有结论一致（compose 注释里早就写了「Linux 上 Docker 不会自动提供它」），
修法是显式加 `--add-host=host.docker.internal:host-gateway`，K8s 里则是直接用 Service 名。
**这一条值得单独记**：容器内存问题、权限问题、网络问题在日志里都长成「启动失败」，
不看第一段 Caused by 就会往错方向修。

### 5.2 只读根文件系统会连累日志目录（已修）

K8s 的 `readOnlyRootFilesystem: true` 一开，应用要写的目录必须显式挂出来
（`/tmp` 与 `/app/logs`），而且**镜像里也要先建好并 chown 给 10001**——
否则在没有挂卷的场景（compose、本地 `docker run`）启动就失败。
镜像与 Deployment 里现在都有这两处，注释写明了原因。

## 6. 与文章的差异

| 文章 | 本仓库 | 原因 |
| --- | --- | --- |
| Redis 存 Session/Memory | **没有引入 Redis**：记忆直接用 `chat_message` 账本 | 本仓库的真源本来就是账本（第 5 掌的边界），多引入一个中间件等于多一个要同步的真源 |
| kind/minikube 本地集群实测 | **本环境没有集群**，用容器终止时序 + 清单结构校验替代 | 不能假装验过；`kubectl apply` 类的验证列进遗留 |
| LiveKit 音频信令 / WebSocket | 未实现（承接第 3、16 掌的遗留） | 本仓库的实时交互只到 Bridge 契约端点 |
| Nacos 单容器 → 托管 | 第 13 掌的 Nacos 是可选中间层，未起注册中心 | 静态实例表在单机演示规模够用 |

## 7. 遗留问题（诚实清单）

1. **没有真集群验证**：apply / rollout / undo / HPA / PDB / Ingress 注解都只做了结构校验。
2. **没有 CI 侧镜像发布到 K8s**：流水线有镜像构建与推送，没有 `kubectl set image` 这一步。
3. **HPA 用 CPU**：SSE 的并发由连接数决定，按 CPU 扩缩是近似；接上 Prometheus Adapter 后应按 `genai_chat_seconds_count` 速率扩缩。
4. **多副本的会话连续性只测到仓储层**：两个实例共享记忆由 `LedgerChatMemoryRepositoryTest` 证明，真集群里两个 Pod 的端到端验证没有做。
5. **数据库结构回滚**：Flyway 前向迁移，回滚应用版本不涉及 schema；但「迁移写错且已应用」的恢复流程没有演练过。
