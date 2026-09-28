# MCP Server 运行镜像。契约与 digital-human 相同：先 package 出 jar，再构建镜像。
# 它有自己的数据库（默认库名 digital_human_ext），连接信息由部署时的环境变量注入。
ARG BASE_IMAGE=eclipse-temurin:21-jre-alpine
FROM ${BASE_IMAGE}

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

WORKDIR /app

RUN addgroup -S app 2>/dev/null || true \
 && adduser -S app -G app 2>/dev/null || true

COPY target/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
