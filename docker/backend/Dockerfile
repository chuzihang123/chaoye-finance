# ==========================================
# JinBooks 后端 Dockerfile
# 多阶段构建：Stage 1 编译，Stage 2 运行
# ==========================================

# --------------------------------------------------
# Stage 1: 构建阶段
# --------------------------------------------------
FROM gradle:8.8-jdk17 AS builder

# 设置工作目录
WORKDIR /app

# 先复制 Gradle 配置文件，利用 Docker 缓存层
COPY gradle.properties settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gradlew ./
RUN chmod +x gradlew

# 复制所有子模块构建配置（不复制源码，先下载依赖）
COPY jinbooks-commons/jinbooks-common/build.gradle ./jinbooks-commons/jinbooks-common/
COPY jinbooks-commons/jinbooks-crypto/build.gradle ./jinbooks-commons/jinbooks-crypto/
COPY jinbooks-commons/jinbooks-authn-core/build.gradle ./jinbooks-commons/jinbooks-authn-core/
COPY jinbooks-commons/jinbooks-authn-provider/build.gradle ./jinbooks-commons/jinbooks-authn-provider/
COPY jinbooks-core/build.gradle ./jinbooks-core/
COPY jinbooks-persistence/build.gradle ./jinbooks-persistence/
COPY jinbooks-starter/jinbooks-starter-captcha/build.gradle ./jinbooks-starter/jinbooks-starter-captcha/
COPY jinbooks-starter/jinbooks-starter-ip2location/build.gradle ./jinbooks-starter/jinbooks-starter-ip2location/
COPY jinbooks-starter/jinbooks-starter-oss/build.gradle ./jinbooks-starter/jinbooks-starter-oss/
COPY jinbooks-starter/jinbooks-starter-otp/build.gradle ./jinbooks-starter/jinbooks-starter-otp/
COPY jinbooks-starter/jinbooks-starter-sms/build.gradle ./jinbooks-starter/jinbooks-starter-sms/
COPY jinbooks-starter/jinbooks-starter-web/build.gradle ./jinbooks-starter/jinbooks-starter-web/
COPY jinbooks-starter/jinbooks-starter-xss/build.gradle ./jinbooks-starter/jinbooks-starter-xss/
COPY jinbooks-web/build.gradle ./jinbooks-web/

# 预下载依赖（利用缓存层，源码变更不重新下载）
RUN ./gradlew dependencies --no-daemon || true

# 复制全部源码
COPY . .

# 构建 jinbooks-web 模块的可执行 jar
RUN ./gradlew :jinbooks-web:bootJar --no-daemon -x test

# --------------------------------------------------
# Stage 2: 运行阶段
# --------------------------------------------------
FROM eclipse-temurin:17-jre

# 安装 wget（用于健康检查）
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建工作目录和日志目录
WORKDIR /app
RUN mkdir -p /app/logs

# 从构建阶段复制 jar 文件
COPY --from=builder /app/jinbooks-web/build/libs/jinbooks-boot-1.1.0-ga.jar app.jar

# 暴露后端端口
EXPOSE 2154

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
