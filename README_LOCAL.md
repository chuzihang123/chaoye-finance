# 朝烨财务 Windows 本地一键启动

> 面向社区版用户 — 无需 Docker、无需手动安装 MySQL/Redis/JRE/Nginx, 双击 bat 即可启动整套系统

## 快速开始

```
1. 右键 init_local.bat        →  以管理员身份运行   (首次, 需联网, 大约 5-10 分钟)
2. 右键 start_one_window.bat  →  以管理员身份运行   (推荐: 单窗口 + 实时日志)
   或  start_local.bat        →  以管理员身份运行   (老式: 每服务一个独立窗口)
3. 浏览器打开 http://127.0.0.1:32080
```

日常使用:

| 想要 | 双击 |
|------|------|
| 启动 (推荐, 单窗口 + 实时日志) | `start_one_window.bat` |
| 启动 (老式, 每服务一个窗口) | `start_local.bat` |
| 停止全部 | `stop_local.bat` |
| 重启 | `restart_local.bat` |

`start_one_window.bat` 启动后会在**一个 cmd 窗口**里实时显示 4 个服务的日志 (MySQL 黄 / Redis 紫 / Backend 绿 / Nginx 青)；
按 `Ctrl+C` 停止日志显示但**服务继续运行**，要彻底停跑 `stop_local.bat`。

## 系统要求

- Windows 10 / 11 / Server 2016+ (64-bit)
- 至少 **2 GB 可用磁盘** (jre 200MB + mysql 600MB + redis 50MB + nginx 30MB + jar + dist)
- **管理员权限** (mysqld 启进程监听 33306 端口建议)
- 可选: 外网访问 (首次 init 需下载 MySQL/Redis/JRE/Nginx)
- 已安装: **PowerShell 5.1+** (Win10 自带), **Node.js 18+** (前端构建), **JDK 21** 或依赖 init 自动安装的 JRE 21

> 如果机器上**没有 Node.js** (前端 build 失败), 请先安装: https://nodejs.org/zh-cn/
> 如果机器上**没有 JDK** (后端首次构建需要), init_local.bat 会自动下载 JRE 21; 后端构建用下载好的 JRE

## 目录结构

```
jinbooks-sq\
├── jinbooks\                后端 Spring Boot 源码
├── jinbooks-ui\             前端 Vue 3 源码
├── sql\                     初始化 SQL 脚本
├── jinbooks_envs\           本地运行环境 (核心)
│   ├── .env                 所有端口/密码/下载源配置
│   ├── bin\                 MySQL/Redis/JRE/Nginx 便携版 + 4 个独立启动 bat
│   │   ├── start_mysql.bat
│   │   ├── start_redis.bat
│   │   ├── start_backend.bat
│   │   ├── start_nginx.bat
│   │   ├── start_all.ps1    (内部: 一键启动 4 个服务)
│   │   └── tail_logs.ps1    (内部: 实时 tail 4 个 log)
│   ├── conf\                my.ini / redis.conf / nginx.conf
│   ├── data\                MySQL/Redis 数据目录
│   ├── sql\                 复制的 SQL 脚本
│   ├── backend\             app.jar + logs\
│   ├── frontend\dist\       前端构建产物
│   ├── logs\                全部服务日志 (mysql.log / redis.log / backend.log / nginx.log)
│   ├── pids\                进程 PID 文件
│   ├── downloads\           下载缓存
│   └── lib\*.ps1            内部 PowerShell 工具
├── init_local.bat           环境初始化
├── start_local.bat          启动服务 (老式, 每服务一个窗口)
├── start_one_window.bat     启动服务 (新, 单窗口 + 实时日志)
├── stop_local.bat           停止服务
├── restart_local.bat        重启服务
└── README_LOCAL.md          本文档
```

## 端口规划

> 全部使用**非常规高位端口**, 避免和本机其他服务 (MySQL 3306 / Redis 6379 等) 冲突。
> 改端口只需编辑 `jinbooks_envs\.env` 对应行, 重启服务即生效。

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL | **33306** | root / `JinbooksRoot321!` |
| Redis | **36379** | 密码 `JinbooksRedis321!` |
| 后端 Spring Boot | **32315** | 直接访问 `http://127.0.0.1:32315/chaoye-api` |
| Nginx | **32080** | 前端访问入口, 同时反向代理后端 API |

- 前端入口: `http://127.0.0.1:32080`
- 后端 API 直连: `http://127.0.0.1:32315/chaoye-api`

> 后端 url 由 `-Dspring.datasource.url=...` 命令行参数覆盖 jar 内的 application 配置, 改 `.env` 即可。

## 默认账号

- 用户名: `admin`
- 密码: `admin123`

(以 `sql/jinbooks_v1.1.0.sql` 中实际数据为准)

## 单窗口实时日志

`start_one_window.bat` 是**推荐**的启动方式:

1. 调 `stop_local.bat` 杀掉所有残留服务
2. 调 `start_all.ps1` (PowerShell) 通过 `Start-Process` 启动 4 个服务 (detach 后台进程)
3. 等 30 秒让端口起来, 输出每个服务的端口监听状态 + 进程 PID
4. 调 `tail_logs.ps1` (PowerShell) 在**当前窗口**实时 tail 4 个 log 文件, 每行带文件名 + 颜色标签
5. 按 `Ctrl+C` 退出 tail, 服务继续在后台跑
6. 跑 `stop_local.bat` 杀所有

颜色映射:
- `[mysql.log]` 黄
- `[redis.log]` 紫
- `[backend.log]` 绿
- `[nginx.log]` 青

> 后端 log 里的中文路径 chcp 65001 切到 UTF-8 后正确显示, 老 cmd (GBK) 会乱码。

## 离线环境 / 下载失败

如果 `init_local.bat` 下载某组件失败, **把已经下好的 zip 路径填到 `.env` 里**即可:

```ini
# 改前
MYSQL_DOWNLOAD_URL=https://mirrors.huaweicloud.com/mysql/MySQL-8.0/mysql-8.0.29-winx64.zip

# 改后 (本地 zip 绝对路径, 不需要带 file://)
MYSQL_DOWNLOAD_URL=D:\soft\mysql-8.0.29-winx64.zip
```

也可以用 `|` 写多个备选 (按顺序尝试):

```ini
REDIS_DOWNLOAD_URL=D:\soft\redis-8.zip|https://gh-proxy.com/https://github.com/zkteco-home/redis-windows/archive/refs/heads/master.zip
```

### 各组件离线 zip 推荐版本

| 组件 | 推荐 zip | 大小 |
|------|----------|------|
| MySQL 8.0 | `mysql-8.0.xx-winx64.zip` (官方 winx64 zip, 非 msi) | ~500MB |
| Redis | `redis-windows-8.x.zip` (推荐 `zkteco-home/redis-windows` 或 `tporadowski/redis` 维护的 Win 版) | ~10MB |
| JRE 21 | `OpenJDK21U-jre_x64_windows_hotspot_*.zip` 或 Oracle `jdk-21_windows-x64_bin.zip` | ~200MB |
| Nginx | `nginx-1.27.x.zip` (官网) | ~5MB |

> 当前测试通过的 Redis Windows 二进制来源 (2026-07):
> - **Redis 8.x (推荐)**: <https://gh-proxy.com/https://github.com/zkteco-home/redis-windows/archive/refs/heads/master.zip> (~5MB, 内含 redis-server.exe + redis.conf)
> - Redis 5.0.14.1 (备选): <https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip> (~12MB)

## 常见问题

### 1. 端口被占用

```powershell
netstat -ano | findstr ":33306 :36379 :32315 :32080"
```

若发现占用, 改 `.env` 对应端口后重启, 或停掉占用进程。

### 2. MySQL 初始化失败 (mysqld --initialize)

检查 `jinbooks_envs\data\mysql\init_stderr.log`:
- **必须以管理员身份运行 init_local.bat**
- 删掉 `jinbooks_envs\data\mysql` 后重试

### 3. 后端启动后访问 500

查看 `jinbooks_envs\logs\backend.log` 末尾错误, 最常见:
- MySQL 没起来 / 密码错 (用 `mysql.exe -h 127.0.0.1 -P 33306 -u root -p'JinbooksRoot321!'` 测)
- Redis 密码错
- 端口被占

### 4. 前端 404

`jinbooks_envs\frontend\dist\index.html` 必须存在, 否则是 `npm run build` 失败。日志:
- `jinbooks-ui\npm_build_err.log`

### 5. 后端控制台中文乱码

bat 默认 GBK, 而 Spring Boot 输出 UTF-8。已在 `start_backend.bat` 和 `start_one_window.bat` 开头自动 `chcp 65001 >nul` 切换; 如果还乱码, 请确认 cmd 是 Windows 10 1809+ / Windows 11。

### 6. 想清空数据重新初始化

```cmd
:: 1. 停止服务
stop_local.bat

:: 2. 删数据
rd /s /q jinbooks_envs\data\mysql
rd /s /q jinbooks_envs\data\redis

:: 3. 重新初始化
init_local.bat
```

### 7. 修改了后端代码想生效

```cmd
:: 删掉旧 jar
del jinbooks_envs\backend\app.jar

:: 重新跑 init (会自动重建 jar)
init_local.bat

:: 重启服务
restart_local.bat
```

### 8. 修改了前端代码想生效

```cmd
:: 删掉旧 dist
rd /s /q jinbooks_envs\frontend\dist

:: 重新跑 init (会自动重建 dist)
init_local.bat

:: 重启服务
restart_local.bat
```

### 9. 后端连接缓存

`.env` 中 `SERVER_CACHED=0` 表示用内存缓存 (单实例够用, 不依赖 Redis);
设为 `2` 则用 Redis, 需要 Redis 服务正常。

### 10. 升级到 docker-compose 部署

本目录的服务用完想迁到 docker, 数据可以从 `jinbooks_envs\data\mysql` 整体导入;
也可以直接用现成的 `docker-compose.yml`。

## 卸载

```cmd
stop_local.bat
rd /s /q jinbooks_envs
del init_local.bat start_local.bat start_one_window.bat stop_local.bat restart_local.bat
```

(项目源码 jinbooks\ jinbooks-ui\ 不受影响, 可继续用 docker 部署)
