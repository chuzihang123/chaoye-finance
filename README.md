# 朝烨财务

朝烨财务提供日常财务记账、凭证管理、出纳日记账、员工及薪资管理、期末结转和结账功能。系统支持资产负债表、利润表、现金流量表、科目余额表及财务仪表盘。

## 系统功能

- 多账套管理、科目与辅助核算、期初余额维护。
- 凭证录入、暂存、提交、审核、导出及打印。
- 出纳账户、收支流水及凭证关联。
- 员工、部门、专项扣除资料及工资计算和导出。
- 会计准则、报表规则、个税和社保配置。
- 期末结转、结账检查、账期控制及报表查询。
- 用户、角色、资源权限及账套访问授权。

## 技术栈

后端使用 Java、Spring Boot、Gradle、MyBatis Plus、MySQL 和 Redis。前端使用 Vue 3、TypeScript、Vite 和 Element Plus。

## 部署

在项目根目录配置部署所需的环境变量，再执行以下命令。

```shell
docker compose up -d --build
```

浏览器访问部署后的站点。公开业务接口前缀为 `/chaoye-api`，OpenAPI 地址为 `/chaoye-api/v3/api-docs`。已有数据库升级时，先备份数据库，再执行品牌更名补丁 `sql/patches/20261117_chaoye_brand.sql`。

## 朝烨财务 MCP

通过 Codex 对话查询账套、科目、凭证和报表，并按授权执行业务操作。MCP 支持 stdio 与带访问令牌认证的 Streamable HTTP。

需要 Node.js 22 或更高版本，使用以下命令运行公开版本。

```powershell
npx --yes --package=github:chuzihang123/chaoye-finance#mcp-v2.0.0 chaoye-finance-mcp
```

[安装、环境变量与 Codex 接入说明](mcp-server/README.md)

[公开发布与安装包](https://github.com/chuzihang123/chaoye-finance/releases/tag/mcp-v2.0.0)

## 许可证

本项目采用 Apache License 2.0，源码许可及版权信息见 [LICENSE](LICENSE)。
