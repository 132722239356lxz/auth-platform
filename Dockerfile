# =============================================================================
# 运行时镜像 — auth-platform 微服务通用镜像
# =============================================================================
# 本镜像只负责"运行"，Maven 构建由 CI 在镜像外完成（见 .gitlab-ci.yml），
# 好处是镜像层最小、可利用 CI 缓存、且不会把源码与 .git 打进生产镜像。
#
# 用法（在仓库根目录执行）：
#   docker build \
#     --build-arg JAR_FILE=gateway/target/gateway.jar \
#     --build-arg SERVER_PORT=8080 \
#     --build-arg APP_PROFILE=dev \
#     -t registry.example.com/auth-platform/gateway:v1.0.0 .
#
# 需要从源码一步构建（无本地 Maven 环境）时，请改用 Dockerfile.build。
#
# 构建参数：
#   JAR_FILE    已构建好的 jar 路径（相对仓库根目录）
#   APP_PROFILE Spring Profile：dev / prod
#   JAVA_OPTS   JVM 参数
#   SERVER_PORT 服务端口（影响 EXPOSE 与健康检查）
# =============================================================================

FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="auth-platform"
LABEL description="Auth Platform Spring Boot Microservice"

# 构建参数（SERVER_PORT 必须在 ARG 中显式声明，否则 EXPOSE/HEALTHCHECK 无法引用）
ARG JAR_FILE=gateway/target/gateway.jar
ARG JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ARG APP_PROFILE=dev
ARG SERVER_PORT=8080

# 同步为环境变量，供 ENTRYPOINT 使用
ENV JAVA_OPTS=${JAVA_OPTS} \
    SPRING_PROFILES_ACTIVE=${APP_PROFILE} \
    SERVER_PORT=${SERVER_PORT} \
    TZ=Asia/Shanghai

# 安装 wget 用于健康检查（alpine 自带 busybox wget，此处仅确保存在）
RUN apk add --no-cache tzdata wget curl \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/cache/apk/*

# 安全基线：以非 root 用户运行，降低容器逃逸风险
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY ${JAR_FILE} app.jar

RUN chown -R appuser:appgroup /app && chmod 500 /app/app.jar

USER appuser

# 健康检查：依赖 Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=90s \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1

EXPOSE ${SERVER_PORT}

# 使用 exec 形式启动，确保 JVM 能收到 SIGTERM 实现优雅停机
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} \
    -Djava.security.egd=file:/dev/./urandom \
    -jar /app/app.jar \
    --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
