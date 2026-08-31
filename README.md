<p align="center" >
    <img src="images/logo.png?raw=true"  width="150px"   alt=""/>
</p>
<p align="center">
  <strong>JinBooks-功能齐全的财务记账系统，告别Excel手工记账</strong>
</p>  

# 概述

<b>JinBooks</b>财务记账系统-功能齐全，告别Excel手工记账，产品覆盖财务记账的整个生命周期、实现记账无纸化办公，满足中小企业日常财务记账需求。财务人员录入凭证、日记账、员工管理等；月末自动结转；结账后自动生成资产负债表、利润表、现金流量表、科目余额表等；仪表盘统计让企业管理者对企业财务状况一览无余。

官方网站  <a href="http://www.jinbooks.com" target="_blank"><b>http://www.jinbooks.com</b></a> 

付费服务支持或商务合作：

<img src="https://gitee.com/jinbooks/jinbooks/raw/main/images/weixin.jpg?raw=true" width="200px" />


 
# 产品功能


| 序号    | 功能    |  备注    | 
| --------| :-----  |  :-----  | 
| 1.1     | 账套管理        | 无限账套| 
| 1.2     | 科目管理        |  资产、负债、共同、所有者权益、成本、损益类| 
| 1.3     | 凭证管理        |  科目选择、辅助核算、借贷平衡测算、凭证字自动生成、凭证整理| 
| 1.4     | 薪资管理        |  员工管理、薪资自动计算、计提/发放工资凭证| 
| 1.5     | 日记账          |  账户管理、月末汇总| 
| 1.6     | 结账            |   月末结转、结账、报表生成| 
| 1.7     | 报表            |  资产负债表、利润表、现金流量表、科目余额表等| 


# 界面

### 账套管理
<img src="https://gitee.com/jinbooks/jinbooks/raw/main/images/jinbooks_book.png?raw=true"/>


### 科目管理
<img src="https://gitee.com/jinbooks/jinbooks/raw/main/images/jinbooks_kemu.png?raw=true"/>

### 仪表盘
<img src="https://gitee.com/jinbooks/jinbooks/raw/main/images/jinbooks_dashbord.png?raw=true"/>

### 凭证
<img src="https://gitee.com/jinbooks/jinbooks/raw/main/images/jinbooks_pingzheng.png?raw=true"/>

<img src="https://gitee.com/jinbooks/jinbooks/raw/main/images/jinbooks_pingzheng_gl.png?raw=true"/>

### 报表
#### 科目余额表
<img src="https://gitee.com/jinbooks/jinbooks/raw/main/images/jinbooks_rpt_kmey.png?raw=true"/>

#### 资产负债表
<img src="https://gitee.com/jinbooks/jinbooks/raw/main/images/jinbooks_rpt_zcfz.png?raw=true"/>

#### 利润表
<img src="https://gitee.com/jinbooks/jinbooks/raw/main/images/jinbooks_rpt_lrb.png?raw=true"/>

# Docker 部署

Docker Compose 一键部署教程请参考 [DOCKER_DEPLOY.md](DOCKER_DEPLOY.md)

# Windows 本地一键启动 (无需 Docker)

社区版用户如果不想装 Docker, 只想在 Windows 上双击 bat 就跑起来, 请用我们打包好的便携版:
**MySQL 8 + Redis 8 + JRE 21 + Nginx + Spring Boot 后端 jar + 前端 dist** 全部自带, 跑在非常规端口 (`33306/36379/32315/32080`) 避免冲突。

📘 完整文档: [README_LOCAL.md](README_LOCAL.md)

快速三步:
1. 右键 `init_local.bat` → 以管理员身份运行 (首次约 5-10 分钟, 需联网下载组件)
2. 右键 `start_one_window.bat` → 单窗口 + 实时日志
3. 浏览器打开 `http://127.0.0.1:32080`

# 技术框架

基于 Spring Boot 4 平台，采用 JDK 21、MySQL、Tomcat 等开源技术，具有良好扩展性。 

## 后端技术

| 框架                     | 说明            | 版本     |
|------------------------|---------------|--------|
| JDK                    | Java 运行环境     | 21     |
| Spring Boot            | 系统框架          | 4.1.0  |
| Mysql Connector        | 数据库驱动         | 9.7.0  |
| Druid                  | JDBC 连接池、监控组件 | 1.2.24 |
| MyBatis                | MyBatis 数据库工具 | 3.5.19 | 
| MyBatis Plus           | MyBatis 增强工具包 | 3.5.16 | 
| Hutool                 | Java工具类库      | 5.8.40 | 
| Lombok                 | 消除冗长的 Java 代码 | 1.18.34 | 


## 前端技术

| 框架           | 说明        | 版本     |
|--------------|-----------|--------|
| vue          | 框架语言      | 3.4.31 |
| vite         | 构建工具      | 5.4.19 |
| element-plus | 组件库       | 2.11.3 |
| echarts      | 报表库       | 5.6.0  |

# 许可证

JinBooks 遵循 <b>Apache License, Version 2.0 </b>开源免费

