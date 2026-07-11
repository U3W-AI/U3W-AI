# FBSir Engine

> 更新日期：2026-07-11

FBSir Engine 是福帮手的受控执行节点，通过 WebSocket 接入 Admin，承载 Playwright 浏览器自动化、流式任务和可扩展执行能力。它是完整 U3W-AI 编排体系中的执行器底座，不是独立控制面。

## 1. 运行前提

- JDK 17
- Maven 3.8 或更高版本
- Admin 已在 8080 端口启动
- 已按根目录[部署文档](../部署文档.md)完成 26 步数据库初始化
- `engine-001` 已在 `ws_host_whitelist` 中且主机类型为 Engine
- 首次使用 Playwright 时允许下载浏览器组件并写入本机缓存

Engine 默认监听 8081，Admin WebSocket 默认地址为 `ws://localhost:8080/ws/engine`。

## 2. 配置事实

当前主配置前缀是 `fbsir.engine`。历史 `wxfbsir.engine` 只用于完整旧配置兼容；同一配置层不能用两套前缀拼接缺失字段。

出于安全策略，Engine 会屏蔽：

- 系统环境变量
- JVM `-D` 系统属性
- Spring Boot 命令行属性覆盖

因此，不能用环境变量或 `--server.port` 一类参数覆盖 Engine 配置。推荐在仓库已忽略的本地运行目录中放置外部 `application.yml`，并从该目录启动 JAR；不要把真实令牌提交到源码。

```powershell
New-Item -ItemType Directory -Force -Path .\fbsir\engine | Out-Null
Copy-Item .\FBSir-engine\target\classes\application.yml .\fbsir\engine\application.yml -Force
notepad .\fbsir\engine\application.yml
```

至少确认以下内容：

```yaml
fbsir:
  engine:
    ws-url: ws://localhost:8080/ws/engine
    host-id: engine-001
    engine-token: "<SAME_VALUE_AS_ADMIN_FBSIR_ENGINE_TOKEN>"
    playwright:
      enabled: true
      data-dir: ./data/playwright
      headless: false
```

`engine-token` 必须与 Admin 的 `FBSIR_ENGINE_TOKEN` 完全一致，长度至少 32 个字符。示例占位符不是可用凭据。

## 3. 构建与启动

在仓库根目录执行：

```powershell
mvn -f .\FBSir-engine\pom.xml clean package
```

精确产物为 `FBSir-engine/target/FBSir-engine-1.3.1.jar`。从外部配置所在目录启动：

```powershell
Push-Location .\fbsir\engine
java -jar ..\..\FBSir-engine\target\FBSir-engine-1.3.1.jar
Pop-Location
```

注意：进程运行期间不要执行 `Pop-Location`；以上三行适合逐行输入。若需要自动化守护，应由 Windows 服务管理器或容器平台设置工作目录为 `fbsir/engine`，这属于外部运行环境配置。

## 4. 验证

健康检查：

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

配置兼容测试：

```powershell
mvn -f .\FBSir-engine\pom.xml -Dtest=FBSirConfigurationAliasEnvironmentPostProcessorTest test
```

再登录福帮手后台，在“主机管理”中确认 `engine-001` 在线。只有健康接口正常且 Admin 显示节点在线，才算 Engine 接入闭环通过。

## 5. Playwright 边界

Playwright 浏览器下载、浏览器缓存、目标网站可达性和目标网站账号登录态都属于外部边界。Engine 负责会话隔离、执行与结果回传，但不会替代目标网站授权。

首次浏览器任务可能因组件下载而明显变慢。浏览器资料保存在配置的 `data-dir` 下；该目录可能包含登录态，不得提交或共享。仓库已忽略常见浏览器会话与认证状态目录。

更多开发信息：

- [Playwright 框架完整指南](../docs/功能说明/engine/Playwright框架完整指南.md)
- [WebSocket 通信完整指南](../docs/功能说明/engine/WebSocket通信完整指南.md)
- [Playwright 与 WebSocket 故障排查](../docs/运行维护/Playwright和WebSocket故障排查手册.md)

## 6. 常见故障

### 健康接口不可访问

```powershell
Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue
Get-Process | Where-Object { $_.ProcessName -eq 'java' }
```

确认 JAR 已构建、8081 未被占用，并从包含外部 `application.yml` 的目录启动。

### WebSocket 注册失败

依次检查：Admin 8080 是否可访问、`ws-url` 路径是否为 `/ws/engine`、两侧令牌是否相同、`host-id` 是否存在且类型正确。

### 浏览器启动失败

先确认网络、磁盘空间和浏览器缓存目录权限，再查看 Engine 日志。不要在日志或问题报告中粘贴令牌、Cookie、存储状态或客户数据。

## 7. 安全要求

- 不在仓库、截图、日志或工单中保存真实 `engine-token`。
- 不把 Playwright 登录态当作可公开测试数据。
- 对外部网站的写入、发送和关键业务状态修改必须保留授权与人工门禁。
- 生产环境使用独立强令牌、最小权限账号、受控网络和可审计的密钥管理。
