# 第 18 掌 验收证据（登云 K8s · 无损发布）

本目录是**第 18 掌的可核对证据**。这一掌有一条边界必须先说清楚：

> **本环境没有可用的 Kubernetes 集群**（`kubectl` 有客户端 v1.36.1，`kubectl config get-contexts` 为空）。
> 所以「容器终止时序」用 Docker 复现（K8s 删除 Pod 时发的同样是 SIGTERM，信号流一致），
> 清单部分做**结构校验**；真集群上的 `apply` / `rollout` / `undo` 没有验过——这一条写在验收记录的遗留里，
> 而不是含混过去。

## 脚本与产物

| 脚本 | 验证什么 | 原始输出 | 判据 |
| --- | --- | --- | --- |
| `01-real-run.ps1` | 构建镜像并以 K8s 同等安全上下文启动（只读根 + 非 root + 内存上限） | `01-image-inspect.txt`、`02-container-identity.txt`、`04-container-aware-heap.txt` | uid=10001、只读根写入被拒、堆上限=内存上限×75% |
| `02-graceful-shutdown.ps1` | 流式请求进行中发 SIGTERM | `06-graceful-shutdown.txt`、`06-graceful-shutdown.log` | 客户端拿到完整回答（无 error 帧），进程等请求收尾后才退出 |
| `03-validate-manifests.ps1` | 清单结构校验（不需要集群） | `07-manifest-validation.txt` | 校验脚本退出码 0，全部判据通过 |
| `04-offline-verify.ps1` | 离线门禁（含记忆外置的仓储用例） | `08-offline-verify.txt` | 三模块全绿 |

## 这批证据里的三件事

| 文件 | 说明了什么 |
| --- | --- |
| `02-container-identity.txt` | 非 root（uid 10001）+ 只读根文件系统（写入被拒）——K8s `securityContext` 的同等条件 |
| `04-container-aware-heap.txt` | 容器上限 1024 MiB → JVM 堆 768 MiB，`MaxRAMPercentage` 真的读到了 cgroup 限制 |
| `06-graceful-shutdown.txt` | SIGTERM 到达时正在生成的流**完整答完**（416 帧、0 错误帧），`docker stop` 等了 16.3s |

## 跑之前要知道的三件事

1. **`--add-host=host.docker.internal:host-gateway` 不能忘**：纯 Docker 下这个域名不解析，
   应用启动会直接失败（本掌实测踩到）。K8s 里用 Service 名，不存在这个问题。
2. **只读根文件系统必须挂 `/tmp` 与 `/app/logs`**：否则 JVM 与应用写日志都会失败；
   镜像里也要先建好 `/app/logs` 并 chown 给 10001（无挂卷场景要用）。
3. **顺序**：先跑 `04-offline-verify.ps1`（它 `clean target/`，有进程占用 jar 会失败），
   再跑 `01`→`02`→`03`。
