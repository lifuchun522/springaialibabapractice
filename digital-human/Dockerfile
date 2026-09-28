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

# 时区与字符集：容器日志与数据库时间必须和业务时区一致
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

WORKDIR /app

# 非 root 运行：容器被攻破时少一层风险
RUN addgroup -S app 2>/dev/null || true \
 && adduser -S app -G app 2>/dev/null || true

COPY target/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
