# 福帮手 FBSir

> 更新日期：2026-07-11

福帮手是一个以 Admin 为控制面、Engine 为自动化执行节点的前后端分离系统，包含企业微信智能机器人编排、Webhook 管理、内容与长文档处理、场景包、积分和权限治理等能力。

- 后端：Spring Boot 3、Java 17、MySQL 8、Redis
- 前端：Vue 3、Vite 6、Node.js 18+
- Engine：Java 17、Playwright、WebSocket
- 开源协议：[AGPL-3.0](LICENSE)

## Windows PowerShell 快速入口

完整步骤、26 步数据库清单和故障排查以 [部署文档](部署文档.md) 为准。下面只给出不会偏离该文档的最短入口。

### 1. 下载 GitHub 仓库

```powershell
git clone --branch fbsir --single-branch https://github.com/U3W-AI/U3W-AI.git
Set-Location .\U3W-AI
```

也可以在 GitHub 页面选择 **Code → Download ZIP**，解压后在项目根目录打开 PowerShell。

### 2. 检查环境

```powershell
git --version
java -version
mvn -version
mysql --version
mysql_config_editor --version
node --version
npm.cmd --version
redis-cli --version
```

最低要求：JDK 17、Maven 3.8、MySQL 8、Node.js 18、npm 9。登录、验证码和会话依赖 Redis 6+，因此可登录的本地部署必须启动 Redis。

如果 MySQL 已安装却未加入 `PATH`，或 Windows 尚未选择 Redis 运行方式，请直接按[部署文档：环境准备](部署文档.md#2-环境准备)中的可复制命令处理；不要在缺少 Redis 时继续排查登录页面。

### 3. 初始化数据库

先创建不把密码写入命令历史的 MySQL 登录路径：

```powershell
mysql_config_editor set --login-path=fbsir-local --host=127.0.0.1 --port=3306 --user=root --password
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\init-database.ps1 -DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\init-database.ps1
```

脚本固定执行 26 步并记录回执；已完成步骤重复运行会跳过，失败步骤不会盲目重放。数据库技术名保留为 `wxfbsir`。

### 4. 配置并启动三个进程

Admin 使用完整 `FBSIR_*` 环境变量组；不能只设置其中一个变量。Engine 的共享凭证必须与 Admin 相同。完整的可复制配置块见 [部署文档：配置 Admin](部署文档.md#5-配置-admin)。

在项目根目录分别打开三个 PowerShell 终端：

```powershell
# Terminal A - Admin
mvn -f .\FBSir-admin\pom.xml spring-boot:run
```

```powershell
# Terminal B - Engine（先按部署文档创建本地外部 application.yml）
mvn -f .\FBSir-engine\pom.xml clean package
New-Item -ItemType Directory -Force -Path .\fbsir\engine | Out-Null
Copy-Item .\FBSir-engine\target\classes\application.yml .\fbsir\engine\application.yml -Force
Push-Location .\fbsir\engine
java -jar ..\..\FBSir-engine\target\FBSir-engine-1.3.1.jar
```

```powershell
# Terminal C - UI
Set-Location .\FBSir-ui
npm.cmd ci
npm.cmd run dev
```

Windows PowerShell 可能因执行策略拦截 `npm.ps1`；使用上面的 `npm.cmd` 不会修改系统执行策略。仓库自带的 PowerShell 脚本也应按本文示例通过仅对当前子进程生效的 `-ExecutionPolicy Bypass` 运行。

默认访问关系：UI `http://localhost:80`，Admin `http://localhost:8080`，Engine 健康端口 `8081`。若端口 80 被占用，请按部署文档使用临时端口，不要修改 API 代理前缀。

## 项目结构

```text
FBSir-admin       Admin 启动入口
FBSir-business    业务与企微编排控制面
FBSir-common      通用配置与工具
FBSir-framework   Security、Redis、MyBatis
FBSir-system      用户、角色和菜单
FBSir-quartz      调度任务
FBSir-generator   代码生成
FBSir-engine      Playwright 自动化执行节点
FBSir-ui          Vue 3 前端
sql               基础结构与有序迁移
scripts           公共初始化和校验脚本
docs              API、功能与运维说明
```

## 外部服务边界

MySQL、Redis、Admin、Engine 和 UI 构成本地基础闭环。Gitee OAuth、企业微信回调/Webhook、元器工作流、OpenClaw、第三方 AI 平台和公网穿透都属于按场景启用的外部系统；未配置它们不应阻止基础后台启动，但对应功能不会可用。真实第三方凭据不得提交到仓库。

## 安全提醒

- 数据库、Token、AES、Engine 和 Webhook 密钥没有可用的仓库默认值。
- 首次登录后立即更换初始化管理员口令，不在文档、脚本或截图中传播默认凭据。
- 不要把真实企微 Webhook、回调 Token、EncodingAESKey、OAuth Secret 或浏览器登录态提交到 Git。
- Druid 控制台默认关闭；只有配置独立账号密码后才可显式开启。

## 文档入口

- [完整部署文档](部署文档.md)
- [Engine 开发与部署](FBSir-engine/README.md)
- [文档中心](docs/README.md)
- [项目结构](项目结构说明.md)
