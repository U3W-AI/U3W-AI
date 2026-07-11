# OpenClaw主机纳管部署指南
> 更新日期：2026-07-11

> **更新日期**：2026-07-11

本文档详细说明OpenClaw主机纳管功能的部署和配置过程，包括环境要求、部署步骤和常见问题解决。

---

## 📋 目录

- [环境要求](#环境要求)
- [部署前准备](#部署前准备)
- [部署步骤](#部署步骤)
- [配置说明](#配置说明)
- [健康检查配置](#健康检查配置)
- [Nginx配置](#nginx配置)
- [常见问题](#常见问题)

---

## 环境要求

### 1. 系统要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17+ | 运行Java应用 |
| MySQL | 8.0+ | 数据存储 |
| Redis | 7.0+ | 缓存支持（可选） |
| Node.js | 与 Vite 6 兼容的版本 | 前端构建 |
| Nginx | 1.20+ | 反向代理（可选） |

### 2. 软件依赖

- Spring Boot 3.5.x
- Vue 3
- Element Plus
- WebSocket

---

## 部署前准备

### 1. 数据库准备

1. 创建数据库
   ```sql
   CREATE DATABASE fbsir CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 导入初始化脚本
   ```bash
   mysql -u root -p fbsir < sql/fbsir.sql
   ```

### 2. 配置文件准备

确保以下配置文件已正确配置：

- `FBSir-admin/src/main/resources/application.yml`

---

## 部署步骤

### 1. 后端部署

#### 1.1 构建后端项目

```bash
# 进入项目根目录
cd d:\code\project\U3W-AI-fbsir\U3W-AI-fbsir

# 构建项目
mvn clean package -DskipTests

# Engine 是独立 Maven 工程，需单独构建
mvn -f FBSir-engine/pom.xml clean package -DskipTests
```

#### 1.2 运行Admin服务

```bash
# 运行Admin服务
java -jar FBSir-admin/target/FBSir-admin.jar
```

#### 1.3 运行Engine服务（可选）

```bash
# 运行Engine服务（如果需要）
java -jar FBSir-engine/target/fbsir-engine-1.3.1.jar
```

### 2. 前端部署

#### 2.1 安装依赖

```bash
# 进入前端目录
cd FBSir-ui

# 安装依赖
npm install
```

#### 2.2 构建前端项目

```bash
# 构建生产版本
npm run build
```

#### 2.3 部署前端静态文件

将 `dist` 目录下的文件部署到Web服务器（如Nginx、Apache）。

---

## 配置说明

### 1. WebSocket配置

在 `application.yml` 中配置WebSocket服务：

```yaml
fbsir:
  websocket: 
      enabled: true
      path: /ws/engine
      max-connections: 10000
      max-message-size: 1048576
      heartbeat-interval: 30
      heartbeat-timeout: 10
```

### 2. 健康检查配置

健康检查默认每30秒执行一次，无需额外配置：

```java
@Scheduled(fixedRate = 30000)
public void checkOpenClawHosts() {
    // 健康检查逻辑
}
```

---

## 健康检查配置

### 1. OpenClaw主机配置

在OpenClaw主机上需要提供健康检查接口，例如：

```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "UP");
        return result;
    }
}
```

### 2. 主机登记配置

在系统中登记OpenClaw主机时，需要配置健康检查URL，例如：

```
http://openclaw-host:8080/health
```

---

## Nginx配置

### 1. WebSocket代理配置

在Nginx配置文件中添加WebSocket代理配置：

```nginx
# WebSocket请求的代理配置
location /ws/ {
    proxy_pass http://admin:8080/ws/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 120s;  # 延长WebSocket连接超时时间
}
```

### 2. 静态文件配置

```nginx
# 前端静态文件配置
location / {
    root /usr/share/nginx/html;
    index index.html;
    try_files $uri $uri/ /index.html;
}
```

---

## 常见问题

### 1. WebSocket连接失败

**问题**：OpenClaw主机无法连接WebSocket服务

**解决方案**：
- 检查WebSocket配置是否启用
- 检查Nginx是否正确配置WebSocket代理
- 检查防火墙是否开放相关端口

### 2. 健康检查失败

**问题**：主机健康检查失败，显示离线状态

**解决方案**：
- 检查健康检查URL是否正确
- 检查OpenClaw主机是否正常运行
- 检查网络连接是否正常
- 检查防火墙是否开放健康检查端口

### 3. 主机登记失败

**问题**：新增主机时提示"主机ID已存在"

**解决方案**：
- 使用唯一的主机ID
- 检查是否有已删除但未清理的主机记录

### 4. 权限不足

**问题**：执行操作时提示"权限不足"

**解决方案**：
- 检查用户是否拥有相应权限
- 联系管理员分配权限

---

**最后更新**: 2026-07-11
**文档版本**: v1.0.0
