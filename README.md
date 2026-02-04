<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">福帮手数据智能化系统</h1>
<h4 align="center">幸福有AI，幸运有你</h4>
<p align="center">
	<a href="https://gitee.com/U3W-AI/RuoYi-Vue"><img src="https://img.shields.io/badge/WxFbsir-v1.2.5B-brightgreen.svg"></a>
	<a href="https://www.fbsir.com"><img src="https://img.shields.io/badge/website-www.fbsir.com-blue.svg"></a>
    <a href="https://gitee.com/U3W-AI/WxFbsir"><img src="https://img.shields.io/badge/WxFbsir-v1.0.0-brightgreen.svg"></a>
    <a href="https://gitee.com/U3W-AI/WxFbsir/blob/master/LICENSE"><img src="https://img.shields.io/badge/license-AGPL--3.0-blue.svg"></a>
    <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.x-green.svg"></a>
    <a href="https://vuejs.org/"><img src="https://img.shields.io/badge/Vue-3.x-4FC08D.svg"></a>
</p>

<p align="center">
    以AI工具链赋能团队建设，推动智能原生企业更快涌现，助力智能社会高质量发展。<br>
    福帮手FBSir，幸福有AI，幸运有你。Fbsir, AI 4 Happiness, U 4 Fortune。
</p>

---

## 📖 项目介绍

**福帮手数据智能化系统（WxFbsir）** 是一个开源的、面向企业团队与内容运营场景的 **AI工具链与智能协同平台**。

项目采用 **AGPL-3.0** 协议开源，旨在通过“主节点（Admin）+引擎节点（Engine）+多AI平台”的架构，将内容生产、文档解析、公众号投递、积分与权限治理等能力统一纳管，并在可扩展的AIGC框架内实现多模型协同与高并发任务处理。

### 核心价值
- **🚀 效率提升**：多模型并行生成与智能优化，显著缩短内容产出周期。
- **📊 可运营性**：内置积分系统、统计报表、角色权限体系，支持精细化运营。
- **🔌 可扩展性**：Engine端AIGC框架设计，轻松支持新增AI平台与新能力扩展。
- **🛡️ 企业级能力**：安全、审计、可控部署、可定制流程，完美适配企业场景。

---

## ✨ 核心功能

### 1. 内容生产与运营
- **日更助手**：同时调用多个模型生成不同风格初稿，支持智能优化合成与排版。
- **公众号集成**：支持微信公众号草稿箱投递、素材管理与发布记录追踪。
- **文档解析**：强大的文档解析助手，支持多格式解析与异步回调处理。

### 2. AIGC 智能协同
- **多模型协同**：支持腾讯元器等多种AI平台接入，实现流式对话与上下文管理。
- **智能工作流**：内置AIGC框架，支持复杂的智能体工作流编排。

### 3. 系统治理与运维
- **自动化引擎**：基于 Playwright 的自动化能力，支持 WebSocket 双节点协同（Admin ↔ Engine）。
- **积分体系**：可配置的积分规则、消耗拦截与前置校验，助力商业化运营。
- **权限管理**：完善的用户、角色、菜单权限控制，保障数据安全。

---

## 🛠️ 技术栈

本项目基于 **Spring Boot 3.x** + **Vue 3** 前后端分离架构开发。

### 后端 (Backend)
- **核心框架**：Spring Boot 3.5.x
- **ORM框架**：MyBatis 3.x
- **数据库**：MySQL 8.x
- **缓存**：Redis 7.x (可选，推荐)
- **任务调度**：Quartz
- **连接池**：Druid
- **工具库**：Hutool, FastJson2, Lombok

### 前端 (Frontend)
- **框架**：Vue 3
- **UI组件**：Element Plus
- **构建工具**：Vite
- **状态管理**：Pinia
- **路由管理**：Vue Router

### 引擎端 (Engine)
- **自动化**：Playwright
- **通信**：WebSocket

---

## 📂 项目结构

```
WxFbsir
├── WxFbsir-admin       // [核心] 后端启动入口，Web服务
├── WxFbsir-ui          // [核心] 前端源代码 (Vue3)
├── WxFbsir-business    // [业务] 核心业务逻辑 (AIGC, 证书, 积分等)
├── WxFbsir-common      // [通用] 工具类、常量、注解
├── WxFbsir-engine      // [引擎] 自动化任务执行引擎 (Playwright)
├── WxFbsir-framework   // [框架] 核心配置 (Security, Redis, MyBatis)
├── WxFbsir-generator   // [工具] 代码生成器
├── WxFbsir-quartz      // [调度] 定时任务
└── WxFbsir-system      // [系统] 用户、权限、日志管理
```

---

## 🚀 快速开始

### 1. 环境准备
- **JDK**：>= 17
- **Node.js**：>= 16 (推荐 18+)
- **MySQL**：>= 8.0
- **Redis**：>= 5.0

### 2. 后端启动
1.  **克隆项目**：
    ```bash
    git clone https://gitee.com/U3W-AI/WxFbsir.git
    ```
2.  **导入数据库**：
    创建数据库 `wxfbsir`，并导入 `sql` 目录下的初始化脚本（如有）。
3.  **修改配置**：
    修改 `WxFbsir-admin/src/main/resources/application-druid.yml` 中的数据库连接信息。
4.  **运行服务**：
    运行 `WxFbsir-admin` 模块下的 `WxFbsirApplication.java`。

### 3. 前端启动
```bash
cd WxFbsir-ui
npm install      # 安装依赖
npm run dev      # 启动开发服务器
```
访问地址：`http://localhost:80` (默认)

### 4. 引擎启动 (可选)
如果需要使用自动化功能（如爬虫、自动化操作）：
1.  进入 `WxFbsir-engine` 模块。
2.  配置相关参数。
3.  运行 `WxFbsirEngineApplication.java`。

---

## 🤝 参与贡献

欢迎提交 Pull Request 或 Issue！

1.  **Fork** 本仓库
2.  新建分支 `Feat_xxx`
3.  提交代码
4.  新建 Pull Request

---

## 📄 开源协议

本项目采用 **AGPL-3.0** 开源协议。
这意味着如果您基于本项目进行修改并提供网络服务，您必须**开源您的修改代码**。

详细协议内容请参阅 [LICENSE](LICENSE) 文件。

---

<p align="center">Copyright © 2024-2026 WxFbsir. All Rights Reserved.</p>


### ✨ **特色模块之Gitee用户能力分析**

支持Gitee用户在首页授权登录，并通过Gitee接口，拉取在开源社区的参与情况数据，进行用户画像和能力分析。


### ✨ **特色功能之腾讯元器自动化工具链**

福帮手主机引擎支持Playwright能力管理，并提供Playwright实现示例，如登录状态检查、工作流导航等元器控制器。


### ✨ **特色功能之文档分析MCP服务**

文档分析MCP及可用于验证的元器智能体工作流上线，支付丰富格式和快速接入。


### ✨ **特色模块之认证易**

完整流程、全功能覆盖证书申请和审核系统，覆盖用户侧和管理员不同角色。集成认证申请、证书模板管理、审核工作流标准化、积分系统适配等模块。


### ✨ **AI应用场景内测功能上线，可自行部署测试**

基于福帮手独特的engine-admin框架，有两个测试模块进库：1、知识库管理及同步机器人（目前支持企业微信、元器智能体知识库同步）2、元器智能体自动调参工具。


## 快速开始

### 环境要求
- 必需：JDK 17+（推荐 OpenJDK 17）
- 必需：Maven 3.8+
- 必需：MySQL 8.0+
- 必需：Node.js 18+（推荐 LTS）
- 必需：npm 9+ 或 pnpm 8+
- 可选：Redis 6.0+（缓存/会话）
- 系统：Windows 10+ / macOS 10.15+ / Linux
- 资源：内存 8GB+（推荐 16GB），硬盘可用空间 10GB+
- 工具：IntelliJ IDEA 2023+ / Eclipse、VS Code / WebStorm、Navicat / DBeaver / MySQL Workbench


### 快速部署

详细部署步骤请查看 [部署文档](./部署文档.md)

```bash
# 1. 克隆项目
git clone https://gitee.com/U3W-AI/U3W-AI.git
cd U3W-AI

# 2. 初始化数据库
mysql -u root -p wxfbsir < sql/wxfbsir.sql

# 3. 启动后端
cd WxFbsir-admin
mvn clean install
mvn spring-boot:run

# 4. 启动前端
cd WxFbsir-ui
npm install
npm run dev
```

### 默认账户
- 用户名：`admin`
- 密码：`admin123`

## 文档中心

- **[部署文档](./部署文档.md)** - 完整的部署指南（包含元器工作流配置）
- **[项目结构说明](./项目结构说明.md)** - 项目目录结构和模块说明
- **[常见问题 (FAQ)](./docs/运行维护/FAQ.md)** - 常见问题解答
- **[代码合并PR规范](./docs/开发规范/代码合并PR规范.md)** - PR 合并与提交流程
- **[代码规范](./docs/开发规范/代码规范.md)** - 代码风格与质量规范
- **[文档规范总结](./docs/开发规范/文档规范总结.md)** - 文档编写规范汇总
- **[权限控制规范](./docs/开发规范/权限控制规范.md)** - 权限与鉴权规范
- **[功能说明](./docs/功能说明)** - 功能说明目录

## 贡献指南

欢迎贡献代码和文档！请遵循以下步骤：

1. Fork 本仓库到你的账号
2. 创建功能分支：`git checkout -b feature/your-feature`
3. 提交代码：`git commit -m "feat: 添加新功能 (2025-12-05)"`
4. 推送到分支：`git push origin feature/your-feature`
5. 提交 Pull Request

详细规范请查看 [代码合并PR规范](./docs/开发规范/代码合并PR规范.md)

## 技术支持

- **源码**: [https://gitee.com/U3W-AI/U3W-AI](https://gitee.com/U3W-AI/U3W-AI)

---

本项目后台管理系统基于 **若依(RuoYi)** 框架进行二次开发，感谢若依团队提供的优秀开源框架。


文档更新日期：2026年2月4日 15：50  文档版本：1.2.5B