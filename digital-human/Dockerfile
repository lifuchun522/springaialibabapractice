# 运行镜像：只装 JRE，不含构建工具。
#
# 契约：构建这个镜像前，先在本模块目录跑过 `mvn -DskipTests package`，
# 也就是 target/ 下已经有可运行的 fat jar（CI 里就是这么做的，见 .github/workflows/release.yml）。
# 这样做而不是在镜像里跑 Maven 的理由：镜像只负责运行，构建失败不该变成镜像构建失败，
# 而且 CI 缓存 Maven 依赖比每次在容器里重新下载快得多。
#
# 基础镜像可覆盖：默认用 Docker Hub 的 temurin 21 JRE alpine；
# 内网/国内环境请把仓库变量 DOCKER_BASE_IMAGE 指向你们的基础镜像仓库（应用要求 JDK 21，不能用 17 的 JRE）。
ARG BASE_IMAGE=eclipse-temurin:21-jre-alpine
FROM ${BASE_IMAGE}

# 时区与字符集：容器日志与数据库时间必须和业务时区一致。
#
# 第 18 掌补的三个 JVM 参数，每个都对应 K8s 上的一个真实故障：
#   -XX:MaxRAMPercentage=75    ：容器感知。不写它，JVM 可能按宿主机内存算堆，
#                                在 limits=2Gi 的 Pod 里被 OOMKilled（而且看不出原因）；
#   -XX:+ExitOnOutOfMemoryError：OOM 之后立刻退出，把「重启」交给 K8s，而不是带着坏堆硬撑；
#   -XX:+UseG1GC               ：长连接服务对停顿敏感，G1 比 Parallel 更合适。
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

# 固定 uid/gid 10001：K8s 的 securityContext.runAsUser 必须与镜像里的用户对得上，
# 否则「只读根文件系统 + fsGroup」一起用时会出现「目录属 root、进程是 10001」的写失败。
RUN addgroup -g 10001 -S app 2>/dev/null || true \
 && adduser -u 10001 -S app -G app 2>/dev/null || true

WORKDIR /app

# 日志目录预先建好并交给非 root：K8s 里根文件系统只读，这个目录会被 emptyDir 覆盖，
# 但**镜像内也必须先存在且可写**——否则在没有挂卷的场景（compose、本地 docker run）启动就失败。
# 本掌实测踩到过：readOnlyRootFilesystem 一开，应用就因为创建不了 /app/logs 起不来。
RUN mkdir -p /app/logs && chown -R 10001:10001 /app

COPY target/*.jar /app/app.jar
RUN chown 10001:10001 /app/app.jar

# 用数字 uid：K8s 的 runAsNonRoot 校验的是这个数字，字符串用户名在部分运行时上会被判成 root
USER 10001:10001

EXPOSE 8080

# exec 形式：让 java 成为 PID 1，SIGTERM 才能直接送到 JVM，
# 优雅退出（server.shutdown=graceful，见 application.yml）才会真的生效。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
