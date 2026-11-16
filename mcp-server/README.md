# JinBooks MCP 服务与 Codex 接入

通过 Codex 对话查询账套、科目、凭证、工资、报表，以及调用新增、修改、审核、过账、结账等后台操作。服务使用官方 `@modelcontextprotocol/sdk` 1.32.2，通过 stdio 传输 MCP JSON-RPC 消息，支持初始化、工具发现和工具调用。标准输出由 SDK 管理。

## 运行要求

- Node.js 22 或更高版本。
- 已运行的 JinBooks 后端，且 `/jinbooks-api/v3/api-docs` 可访问。仓库默认启用此接口。
- JinBooks 用户账号或登录令牌。操作权限及当前账套由系统中的登录用户决定。

MCP 进程由 Codex 启动。系统后端、数据库和 Redis 按项目原有方式启动。

## 安装和配置

### 通过公开 GitHub 包安装

Node.js 22 及以上可直接运行以下命令。`mcp-v1.1.1` 标签提供固定版本的 MCP 包，`npm` 分支提供当前发布版本。

```powershell
npx --yes --package=github:chuzihang123/jinbooks-mcp#mcp-v1.1.1 jinbooks-mcp
```

也可以全局安装后运行。

```powershell
npm install --global github:chuzihang123/jinbooks-mcp#mcp-v1.1.1
jinbooks-mcp
```

服务通过 stdio 等待 MCP 客户端请求。安装后在 Codex 中使用以下配置，填写实际后端地址和认证信息。

```toml
[mcp_servers.jinbooks]
command = "npx"
args = ["--yes", "--package=github:chuzihang123/jinbooks-mcp#mcp-v1.1.1", "jinbooks-mcp"]
startup_timeout_sec = 120
tool_timeout_sec = 180

[mcp_servers.jinbooks.env]
JINBOOKS_BASE_URL = "http://127.0.0.1:2154/jinbooks-api"
JINBOOKS_USERNAME = "填写账号"
JINBOOKS_PASSWORD = "填写密码"
```

首次运行需要联网下载依赖。配置中的账号和密码保存在本机 Codex 配置中。npx 运行时通过客户端环境变量接收配置。源码 `npm start` 支持从当前目录 `.env` 加载配置。当前上传文件的默认允许目录为 MCP 进程工作目录，可使用 `JINBOOKS_UPLOAD_ROOTS` 显式配置。

公开下载包与校验文件可从 [MCP 1.1.1 发布页](https://github.com/chuzihang123/jinbooks-mcp/releases/tag/mcp-v1.1.1) 获取。使用发布包安装时执行以下命令。

```powershell
npm install --global https://github.com/chuzihang123/jinbooks-mcp/releases/download/mcp-v1.1.1/jinbooks-mcp-1.1.1.tgz
```

### 1.1.1 更新说明

本版本更新服务版本号和安装说明，MCP 核心调用逻辑沿用 1.1.0。对应后端修复已同步到仓库主分支，包含账套访问和写接口权限校验、上传接口 multipart 定义，以及无效访问令牌的 401 响应。

部署后端更新后重启 MCP 进程，使其重新读取 OpenAPI。普通用户的写操作需要由管理员分配匹配 HTTP 方法及路径的 API 资源权限。管理员身份通过当前角色实时识别。上传工具的 `files.field` 按接口描述填写，当前通用上传接口为 `POST /filestorage/upload/`，文件字段为 `uploadFile`。

使用固定版本配置的客户端更新标签并重新连接 MCP。远程 HTTP 客户端重新初始化会话后可继续调用。MCP 连接令牌、用户账号与密码通过本地或服务端环境变量配置。

### 从源码安装

在 PowerShell 执行以下命令。

```powershell
Set-Location 'E:\WorkSpace\财务\jinbooks\mcp-server'
npm ci
if (!(Test-Path .env)) { Copy-Item .env.example .env }
```

编辑 `.env`，填写实际后端地址和认证信息。默认源码启动端口为 `2154`，项目 Windows 一键启动方案使用 `32315`，Nginx 入口使用 `32080`。例如后端直连地址可填写 `http://127.0.0.1:32315/jinbooks-api`。

认证可采用以下任意方式。

- 填写 `JINBOOKS_USERNAME` 和 `JINBOOKS_PASSWORD`，对话中让 Codex 调用 `jinbooks_login`。服务先获取登录 state，再提交与现有前端一致的登录字段。账号密码从环境变量读取。
- 填写 `JINBOOKS_TOKEN`，可同时填写 `JINBOOKS_REFRESH_TOKEN`。令牌可从已登录浏览器的 API 请求认证头和登录响应取得。`JINBOOKS_TOKEN` 仅填写令牌本身。

`.env` 已加入忽略规则。登录与刷新得到的令牌保存在进程内存中。配置了账号密码时，重启后首次业务调用会尝试重新登录，验证码开启时按下面的挑战流程操作。配置令牌的用户需要维护配置中令牌的有效性。使用远端部署时在 `JINBOOKS_BASE_URL` 填写 HTTPS 地址。

启用验证码时，让 Codex 调用 `jinbooks_login_challenge`，查看其返回图片和 state，再将人工识别的验证码及 state 传给 `jinbooks_login`。验证码图片由后端返回，展示形式取决于客户端。也可使用浏览器登录取得的令牌。

## 接入 Codex

将同目录 `codex.config.example.toml` 中的配置段合并到 `C:\Users\A\.codex\config.toml`。配置示例如下，路径按实际安装位置调整。

```toml
[mcp_servers.jinbooks]
command = "node"
args = ["--env-file=E:/WorkSpace/财务/jinbooks/mcp-server/.env", "E:/WorkSpace/财务/jinbooks/mcp-server/src/index.js"]
startup_timeout_sec = 20
tool_timeout_sec = 180

[mcp_servers.jinbooks.env]
JINBOOKS_DOWNLOAD_DIR = "E:/WorkSpace/财务/jinbooks/mcp-server/downloads"
```

保存后重新打开 Codex，在 MCP 设置中查看 `jinbooks`，CLI 可执行 `codex mcp list` 检查注册情况。新对话中要求调用 `jinbooks_status` 检查连接，随后执行登录。手工执行 `npm start` 适合排查启动错误，正常连接由 Codex 负责启动进程。

如果 Node 未加入 Codex 的 PATH，将 `command` 改为 `node.exe` 的绝对路径。使用绝对 `.env`、入口和下载目录可避免 Codex 工作目录变化造成路径错误。

## 工具

### 远程 HTTP 接入

部署服务器可运行 `npm run start:http` 或 `jinbooks-mcp-http`。它使用 MCP Streamable HTTP 传输，同一会话保留登录、Cookie 和操作确认状态，每个会话独立持有客户端实例。

启动前配置 `JINBOOKS_MCP_TOKEN`，至少 32 个字符。HTTP 服务默认监听 `127.0.0.1:3155`，通过 HTTPS 反向代理将 `/mcp` 转发至该服务。所有 `/mcp` 请求均校验 `Authorization: Bearer <访问令牌>`。此访问令牌用于连接 MCP，JinBooks 用户认证仍按前述配置处理。

```toml
[mcp_servers.jinbooks_remote]
url = "https://你的域名/mcp"
bearer_token_env_var = "JINBOOKS_REMOTE_MCP_TOKEN"
tool_timeout_sec = 180
```

在启动 Codex 的环境中设置 `JINBOOKS_REMOTE_MCP_TOKEN` 为服务器的访问令牌。浏览器来源请求默认拒绝，可通过 `JINBOOKS_MCP_ORIGINS` 指定允许的 Origin JSON 数组。服务提供 `/healthz` 健康检查，限制最大请求体为 1 MiB、最大会话数为 32，空闲会话一小时后回收。服务重启后客户端需要重新初始化会话。

HTTP 模式部署时，应通过 `JINBOOKS_UPLOAD_ROOTS` 指定服务器上用于上传的目录。远程工具中的上传和下载路径对应服务端文件系统。

| 工具 | 用途 |
| --- | --- |
| `jinbooks_status` | 检查 OpenAPI 连通性、认证状态，读取当前用户及账套 |
| `jinbooks_login_challenge` | 获取验证码挑战 |
| `jinbooks_login` | 使用配置的账号密码及可选验证码登录 |
| `jinbooks_search_operations` | 按关键词搜索接口，支持 offset、limit 分页 |
| `jinbooks_describe_operation` | 获取参数、请求体、响应及引用 DTO 模型 |
| `jinbooks_preview_operation` | 校验并预览操作内容，生成绑定参数和当前用户账套的一次性确认凭据 |
| `jinbooks_execute` | 执行业务接口，支持路径参数、查询参数、请求体和文件 |

服务从后端 OpenAPI 的 `paths` 加载接口目录。当前文档中的全部 HTTP 操作均可发现，执行工具支持 JSON、表单、multipart 文件上传，以及二进制导出。认证路径使用专用工具。接口目录在进程内缓存，后端新增接口后重启 MCP 进程即可更新。

执行前先搜索和查看参数。示例工具参数如下。

```json
{
  "operationId": "GET /voucher/fetch",
  "query": { "pageNumber": 1, "pageSize": 10 }
}
```

分页字段以实际接口描述为准。路径参数通过 `pathParams` 传递，请求体通过 `body` 传递，上传文件通过 `files` 数组传递。

```json
{
  "operationId": "GET /voucher/get/{id}",
  "pathParams": { "id": "实际凭证ID" }
}
```

```json
{
  "operationId": "POST /实际上传接口",
  "body": { "业务字段": "值" },
  "files": [{ "field": "file", "path": "E:/资料/待导入.xlsx" }]
}
```

上传接口及字段名从接口描述中取得。上传路径必须为绝对路径，文件必须存在且为普通文件。默认允许上传的目录为 MCP 进程工作目录，可通过 `JINBOOKS_UPLOAD_ROOTS` 配置绝对目录的 JSON 数组。例如上传 `E:/资料` 内文件时，在 `.env` 中加入以下配置。

```dotenv
JINBOOKS_UPLOAD_ROOTS=["E:/WorkSpace/财务/jinbooks","E:/资料"]
JINBOOKS_MAX_UPLOAD_BYTES=20971520
```

检查按解析符号链接后的真实路径执行，拒绝目录越界。默认每个文件限制 20 MiB，可通过 `JINBOOKS_MAX_UPLOAD_BYTES` 调整。文件不存在或权限不足时返回明确错误。

导出文件写入 `JINBOOKS_DOWNLOAD_DIR`，以随机名称和 `.bin` 后缀保存，工具返回绝对路径、Content-Type、Content-Disposition，Codex 可依据返回的文件名和类型重命名。

### 危险操作预览与确认

DELETE 请求，以及路径或摘要包含删除、结账、结转、批量标记的操作，会要求预览凭据，包括带 `{ids}` 的批量接口。先用完整执行参数调用 `jinbooks_preview_operation`。工具返回接口、参数、文件摘要、当前用户和账套、`confirmationId` 及过期时间。Codex 负责向用户展示这些内容并取得确认，随后将相同参数和 `confirmationId` 传入 `jinbooks_execute`。

凭据有效期为五分钟，绑定请求参数、文件内容 SHA-256、登录会话及当前用户账套，仅能消费一次。参数、文件或账套发生变化时需要重新预览。预览和执行期间应保持当前账套稳定。后端最终仍按执行时的业务上下文校验操作。

```json
{
  "operationId": "DELETE /voucher/delete/{ids}",
  "pathParams": { "ids": ["实际凭证ID"] },
  "confirmationId": "预览工具返回且用户确认后的凭据"
}
```

接口路径参数的数据类型以实际 OpenAPI 为准。确认凭据在发送请求前消费，并发调用只能使用一次。若请求超时，需要先查询业务结果再决定是否重新操作。

## 对话操作示例

可以直接向 Codex 发送以下请求。

> 连接 JinBooks，登录并告诉我当前用户和当前账套。

> 查询当前账套本月的凭证，列出日期、凭证号、摘要和金额。

> 查询当前账套的银行存款和管理费用科目，查看创建凭证需要的字段。

> 按我提供的日期、金额和科目暂存一张凭证，保存后返回凭证 ID。

> 导出当前账套本月的凭证，告诉我文件保存在哪里。

> 查看当前账套切换相关接口，将账套切换为我指定的账套，再查询利润表。

Codex 会搜索接口、读取参数，再调用执行工具。具体字段和操作规则以运行中的后端为准。写入工具标记了 `readOnlyHint=false`、`destructiveHint=true`，客户端可据此展示操作审批。查询与写入共用执行工具，因此客户端会将该工具整体视为具有写入能力。

请求超时为 60 秒。HTTP 或业务码 401 且已有刷新令牌时会刷新并重试一次。其余业务失败通过 MCP `isError` 返回。MCP 按 OpenAPI Schema 检查路径参数、查询参数、请求体的类型、必填字段、嵌套结构、枚举、范围及格式，并解析本地 `$ref` 和引用请求体。JSON 数值、布尔值请使用对应类型。查询参数支持标量和标量数组，对象查询参数会返回明确的序列化限制提示。后端继续执行 DTO 校验和财务业务规则校验。

成功结果和错误文本统一脱敏，覆盖敏感字段，以及此进程持有过的新旧令牌、刷新令牌和密码。接口描述保留请求体、响应和引用模型，搜索列表中的摘要裁剪保持原有行为。

## 验证与排障

```powershell
Set-Location 'E:\WorkSpace\财务\jinbooks\mcp-server'
npm test
```

测试启动模拟 HTTP 后端，再通过 SDK 客户端启动真实 stdio MCP 子进程，覆盖初始化、工具发现、参数描述、登录、Cookie、401 刷新重试、查询、写入、文件上传、导出、错误返回和预览确认。此外，回归测试覆盖引用请求体、嵌套 Schema、参数类型、必填、枚举、范围、确认过期与并发消费、账套变化、重启登录恢复、新旧令牌脱敏、上传目录越界及链接逃逸。

真实环境按以下顺序验证。

1. 打开后端 `/jinbooks-api/v3/api-docs`，确认返回 OpenAPI JSON。
2. 在 Codex 调用 `jinbooks_status`、`jinbooks_login`，再次读取当前用户。
3. 搜索并查询账套、科目或凭证，核对返回结果与页面。
4. 在用户指定的账套中操作一张测试凭证，核对保存结果及后续业务操作。
5. 导出文件并打开核对。

`fetch failed` 通常需要检查后端地址和服务监听情况。401 需要检查令牌或重新登录。OpenAPI 无法读取时检查后端文档配置和反向代理路径。服务启动时无需后端在线，首次调用状态或接口工具时才访问后端。

## 参考

- [MCP 官方服务端指南](https://modelcontextprotocol.io/docs/develop/build-server)
- [TypeScript SDK](https://github.com/modelcontextprotocol/typescript-sdk)
- [Codex MCP 配置文档](https://developers.openai.com/codex/mcp)
