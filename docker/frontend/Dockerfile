# ==========================================
# JinBooks 前端 Dockerfile
# 多阶段构建：Stage 1 编译，Stage 2 Nginx 托管
# ==========================================

# --------------------------------------------------
# Stage 1: 构建阶段
# --------------------------------------------------
FROM node:20-alpine AS builder

# 安装必要的构建工具
RUN apk add --no-cache python3 make g++

# 设置工作目录
WORKDIR /app

# 复制 package.json 和 package-lock.json（利用缓存层）
COPY package*.json ./

# 安装依赖
RUN npm install

# 复制前端源码
COPY . .

# 覆盖生产环境变量，适配 Docker 根路径部署
# VITE_APP_CONTEXT_PATH 设为 /，使前端适配 Nginx 根路径部署
RUN echo "VITE_APP_TITLE='JinBooks财务记账系统'" > .env.production && \
    echo "VITE_APP_ENV='production'" >> .env.production && \
    echo "VITE_APP_BASE_API='/jinbooks-api'" >> .env.production && \
    echo "VITE_APP_CONTEXT_PATH='/'" >> .env.production && \
    echo "VITE_BUILD_COMPRESS='gzip'" >> .env.production

# 构建生产版本
RUN npm run build

# --------------------------------------------------
# Stage 2: Nginx 运行阶段
# --------------------------------------------------
FROM nginx:1.31

# 设置时区
RUN apt-get update && \
    apt-get install -y --no-install-recommends tzdata && \
    rm -rf /var/lib/apt/lists/*
    
ENV TZ=Asia/Shanghai

# 从构建阶段复制打包后的静态文件到 Nginx 目录
COPY --from=builder /app/dist /usr/share/nginx/html

# 暴露 80 端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
