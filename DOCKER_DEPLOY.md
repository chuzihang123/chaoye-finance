# JinBooks Docker Compose 一键部署指南

## 快速开始

确保已安装 [Docker](https://docs.docker.com/get-docker/) 和 [Docker Compose](https://docs.docker.com/compose/install/)。

```bash
# 1. 克隆项目后进入根目录
cd jinbooks-sq

# 2. 启动所有服务（首次构建可能需要 5-15 分钟）
docker compose up -d

# 3. 查看服务状态
docker compose ps

# 4. 访问系统
# 前端: http://localhost
# 后端 API: http://localhost:2154/jinbooks-api
# Swagger 文档: http://localhost/jinbooks-api/swagger-ui.html
```

## 服务架构

| 服务 | 容器名 | 镜像          | 内部端口  | 外部端口 | 说明 |
|------|--------|-------------|-------|---------|------|
| MySQL | jinbooks-mysql | mysql:8.0   | 3306  | 3306 | 数据持久化存储 |
| Redis | jinbooks-redis | redis:7.4.0 | 6379  | 6379 | 缓存服务 |
| 后端 | jinbooks-backend | 本地构建        | 2154  | 2154 | Spring Boot 服务 |
| 前端 | jinbooks-frontend | 本地构建        | 80    | 18080 | Nginx 静态资源托管 |

## 目录结构

```
jinbooks-sq/
├── docker-compose.yml          # Docker Compose 编排文件
├── .env                        # 环境变量配置
├── docker/
│   ├── backend/
│   │   └── Dockerfile          # 后端多阶段构建
│   └── frontend/
│       └── Dockerfile          # 前端多阶段构建
├── nginx/
│   └── nginx.conf              # Nginx 反向代理配置
├── jinbooks/                   # 后端源码 (Gradle)
├── jinbooks-ui/                # 前端源码 (Vite + Vue3)
├── sql/
│   └── jinbooks_v1.1.0.sql     # 数据库初始化脚本
├── nginx/
│   └── nginx.conf              # Nginx 反向代理配置
└── logs/                       # 后端日志（容器映射到宿主机）
    └── backend/
```

## 常用命令

```bash
# 构建并启动
docker compose up -d --build

# 查看容器 stdout 日志
docker compose logs -f

# 只看后端容器日志
docker compose logs -f backend

# 查看后端写入宿主机的日志文件
tail -f logs/backend/jinbooks.log
ls -la logs/backend/

# 停止服务
docker compose down

# 停止并删除数据卷（谨慎使用）
docker compose down -v

# 重启单个服务
docker compose restart backend

# 进入容器排查问题
docker exec -it jinbooks-backend sh
docker exec -it jinbooks-mysql mysql -ujinbooks -p
```

## 环境变量配置

所有配置集中在 `.env` 文件中，修改后需重启服务生效：

```bash
# 数据库密码（生产环境请务必修改）
MYSQL_ROOT_PASSWORD=JinbooksRoot321!
MYSQL_PASSWORD=Jinbooks321!

# Redis 密码（生产环境请务必修改）
REDIS_PWD=JinbooksRedis321!

# 端口映射（如 80 被占用可修改）
FRONTEND_PORT=80
BACKEND_PORT=2154
MYSQL_PORT=3306
REDIS_PORT=6379

# 缓存模式: 0=内存缓存, 2=Redis
SERVER_CACHED=0
```

## 数据库说明

- **首次启动**：MySQL 会自动执行 `sql/jinbooks_v1.1.0.sql` 初始化数据库
- **数据持久化**：使用 Docker Volume `mysql_data`，即使容器删除数据也不会丢失
- **重新初始化**：如需重新导入 SQL，先执行 `docker compose down -v` 删除卷，再启动

## 生产环境建议

1. **修改默认密码**：务必修改 `.env` 中的所有默认密码
2. **关闭数据库外网端口**：生产环境建议删除或注释 `docker-compose.yml` 中 MySQL 的 `ports` 映射
3. **使用 HTTPS**：配合反向代理（如 Nginx/Caddy/Traefik）启用 HTTPS
4. **启用 Redis 缓存**：修改 `SERVER_CACHED=2`，后端将自动使用 Redis 作为缓存
5. **关闭不需要的服务端口**：生产环境建议删除或注释 MySQL、Redis 的 `ports` 映射，仅通过 Docker 网络内网访问
6. **日志持久化**：后端日志已自动映射到 `logs/backend/` 目录，建议配置日志轮转或定期清理
7. **关闭 IP 定位**：如不需要，设置 `LOGIN_IPLOCATION=false`

## 故障排查

### 1. 前端页面空白或 404
```bash
docker compose logs frontend
# 检查 Nginx 配置是否挂载正确
docker exec jinbooks-frontend cat /etc/nginx/conf.d/default.conf
```

### 2. 后端无法连接数据库
```bash
# 检查 MySQL 健康状态
docker compose ps

# 进入 MySQL 容器检查用户和数据库
docker exec -it jinbooks-mysql mysql -uroot -p -e "SHOW DATABASES; SELECT user, host FROM mysql.user;"

# 检查后端环境变量
docker exec jinbooks-backend env | grep DATABASE
```

### 3. 构建失败（Gradle/Node）
```bash
# 清理缓存重新构建
docker compose build --no-cache backend
docker compose build --no-cache frontend
```

### 4. 端口被占用
修改 `.env` 中的端口配置，例如：
```env
FRONTEND_PORT=8080
BACKEND_PORT=2155
MYSQL_PORT=3307
```

## 技术细节

- **后端构建**：使用 Gradle 8.14.5 + JDK 21 多阶段构建，最终镜像基于 Eclipse Temurin JRE 21
- **前端构建**：使用 Node 20 多阶段构建，最终镜像基于 Nginx Alpine
- **Nginx 代理**：前端 `/jinbooks-api/` 请求通过 Nginx 反向代理到后端服务
- **健康检查**：MySQL、Redis 和后端均配置了健康检查，确保服务按正确顺序启动
- **日志持久化**：后端日志通过 Volume 映射到宿主机 `logs/backend/` 目录，每天滚动、最多保留 15 个文件

## 许可证

本项目采用 Apache License 2.0 开源协议。
