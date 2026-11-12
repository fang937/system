# syntax=docker/dockerfile:1

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# 先只拷 pom，利用镜像层缓存跳过重复的依赖下载；离线解析失败不阻断构建
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-jammy

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

WORKDIR /app

# 非 root 运行
RUN groupadd -r app && useradd -r -g app app \
    && mkdir -p /app/logs && chown -R app:app /app

COPY --from=build --chown=app:app /build/target/lab-select.jar /app/app.jar

USER app

EXPOSE 8080

# /dev/tcp 探测端口，避免额外安装 curl
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=10 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
