# OpenClaw主机纳管功能说明

> **更新日期**：2026-07-10

OpenClaw主机纳管功能是福帮手Admin侧实现的主机管理原型，支持OpenClaw主机的登记、状态监控、管控和健康检查。

---

## 📋 目录

- [功能概述](#功能概述)
- [使用场景](#使用场景)
- [配置说明](#配置说明)
- [接口说明](#接口说明)
- [权限控制](#权限控制)
- [核心特性](#核心特性)
- [数据库设计](#数据库设计)
- [代码位置](#代码位置)
- [使用示例](#使用示例)

---

## 功能概述

OpenClaw主机纳管功能提供了一套完整的主机管理解决方案，主要包括：

1. **主机登记**：支持OpenClaw主机的信息登记和管理
2. **状态监控**：实时显示主机在线/离线状态
3. **主机管控**：支持主机的启用/禁用、修改和删除操作
4. **健康检查**：定期对主机进行健康检查，自动更新在线状态
5. **类型区分**：支持区分engine和openclaw两种主机类型

---

## 使用场景

- **OpenClaw主机管理**：集中管理多个OpenClaw主机的配置和状态
- **主机健康监控**：实时掌握所有主机的运行状态
- **主机权限控制**：精细控制主机的访问权限

---

## 配置说明

### 1. 系统配置

无需额外配置，使用默认的WebSocket配置即可：

```yaml
wxfbsir:
  websocket: 
      enabled: true
      path: /ws/engine
```

### 2. 主机配置

在主机登记时需要配置以下信息：

- **主机ID**：唯一标识符
- **主机名称**：便于识别的名称
- **主机类型**：选择"openclaw"
- **健康检查URL**：OpenClaw服务的健康检查地址

---

## 接口说明

### 后端接口

| 接口地址 | 请求方式 | 权限标识 | 功能说明 |
|---------|---------|---------|---------|
| `/business/host/whitelist/list` | GET | `business:host:whitelist:query` | 获取主机白名单列表 |
| `/business/host/whitelist/{id}` | GET | `business:host:whitelist:query` | 获取主机详情 |
| `/business/host/whitelist` | POST | `business:host:whitelist:add` | 新增主机 |
| `/business/host/whitelist` | PUT | `business:host:whitelist:edit` | 修改主机信息 |
| `/business/host/whitelist/{ids}` | DELETE | `business:host:whitelist:remove` | 删除主机 |
| `/business/host/whitelist/health-check/{id}` | GET | `business:host:whitelist:edit` | 手动触发 OpenClaw 健康检查 |
| `/business/host/whitelist/status` | GET | `business:host:whitelist:query` | 获取主机 ID、类型和在线状态 |

### 前端接口

位于 `WxFbsir-ui/src/api/business/host/whitelist.js`

```javascript
// 查询主机白名单列表
export function listWhitelist(query)

// 查询主机白名单详情
export function getWhitelist(id)

// 新增主机白名单
export function addWhitelist(data)

// 修改主机白名单
export function updateWhitelist(data)

// 删除主机白名单
export function delWhitelist(ids)
```

---

## 权限控制

系统采用基于Spring Security的权限控制，权限通过菜单表（`sys_menu`）配置：

| 权限标识 | 权限名称 | 功能说明 |
|---------|---------|---------|
| `business:host:whitelist:query` | 主机白名单查询 | 查询主机白名单列表和详情 |
| `business:host:whitelist:add` | 主机白名单新增 | 新增主机信息 |
| `business:host:whitelist:edit` | 主机白名单修改 | 修改主机信息 |
| `business:host:whitelist:remove` | 主机白名单删除 | 删除主机信息 |

---

## 核心特性

### 1. 主机类型支持

支持两种主机类型：
- `engine`：引擎主机
- `openclaw`：OpenClaw主机

补充说明：
- Engine 节点接入主节点时，白名单记录不仅需要 `hostId` 正确，还需要 `hostType=engine`
- `openclaw` 类型仅参与健康检查与纳管，不会作为 Engine 节点注册

### 2. 健康检查机制

- **定时检查**：每30秒自动执行一次健康检查
- **并行处理**：多主机并行检查，提高效率
- **超时控制**：5秒超时机制，避免长时间阻塞
- **状态更新**：根据检查结果自动更新主机在线状态

状态来源说明：
- `engine` 类型主机的 `onlineStatus` 以运行时 Engine 会话状态为准
- `openclaw` 类型主机的 `onlineStatus` 以健康检查结果和数据库字段为准

### 3. 增强的WebSocket支持

- **多参数支持**：同时支持token和key参数获取
- **详细日志**：完善的错误日志，便于问题定位
- **异常处理**：区分不同类型的Token错误

---

## 数据库设计

### 主机白名单表（ws_host_whitelist）

| 字段名 | 类型 | 说明 |
|-------|------|------|
| id | BIGINT | 主键ID |
| host_id | VARCHAR(50) | 主机ID |
| host_name | VARCHAR(100) | 主机名称 |
| host_type | VARCHAR(20) | 主机类型：engine/openclaw |
| health_check_url | VARCHAR(200) | 健康检查URL |
| online_status | VARCHAR(10) | 在线状态：online/offline |
| status | TINYINT | 状态：0禁用 1启用 |
| create_time | DATETIME | 创建时间 |
| create_by | VARCHAR(50) | 创建人 |
| update_time | DATETIME | 更新时间 |
| update_by | VARCHAR(50) | 更新人 |

---

## 代码位置

### 后端代码

| 模块 | 文件位置 |
|------|---------|
| 实体类 | `WxFbsir-business/src/main/java/com/wx/fbsir/business/websocket/domain/WsHostWhitelist.java` |
| Mapper | `WxFbsir-business/src/main/java/com/wx/fbsir/business/websocket/mapper/WsHostWhitelistMapper.java` |
| Mapper XML | `WxFbsir-business/src/main/resources/mapper/websocket/WsHostWhitelistMapper.xml` |
| 控制器 | `WxFbsir-business/src/main/java/com/wx/fbsir/business/websocket/controller/HostWhitelistController.java` |
| 健康检查 | `WxFbsir-business/src/main/java/com/wx/fbsir/business/websocket/task/OpenClawHealthChecker.java` |
| WebSocket配置 | `WxFbsir-business/src/main/java/com/wx/fbsir/business/websocket/config/WebSocketConfig.java` |
| WebSocket拦截器 | `WxFbsir-business/src/main/java/com/wx/fbsir/business/websocket/server/ClientWebSocketInterceptor.java` |

### 前端代码

| 模块 | 文件位置 |
|------|---------|
| 页面组件 | `WxFbsir-ui/src/views/business/host/whitelist/index.vue` |
| API接口 | `WxFbsir-ui/src/api/business/host/whitelist.js` |
| Nginx配置 | 参考部署文档中的反向代理示例；当前源码未提交独立 `nginx.conf` |

---

## 使用示例

### 主机登记

1. 登录Admin管理界面
2. 进入"主机管理" -> "主机ID白名单"
3. 点击"新增"按钮
4. 填写主机信息
   - 主机ID：唯一标识符
   - 主机名称：便于识别的名称
   - 主机类型：选择"openclaw"
   - 健康检查URL：OpenClaw服务地址
5. 点击"保存"按钮

### 状态查看

- 在主机白名单列表中，可直接查看主机在线状态
- 在线：蓝色开关显示"ON"
- 离线：灰色开关显示"OFF"

### 主机管控

1. **启用/禁用**：点击列表中的开关按钮
2. **修改配置**：点击"修改"按钮，更新主机信息
3. **删除主机**：点击"删除"按钮，从系统中移除主机

### 健康检查

- 系统每30秒自动执行健康检查
- 健康检查失败时，主机状态自动更新为"离线"
- 可在Admin服务日志中查看详细的健康检查记录

---

**最后更新**: 2026-03-16  
**文档版本**: v1.0.0
