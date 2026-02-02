# 🔧 Playwright和WebSocket故障排查手册

> **文档目的**: 提供常见问题的排查方法  
> **更新日期**: 2026-01-27

---

## 目录

- [1. 管理页面说明](#1-管理页面说明)
- [2. WebSocket故障排查](#2-websocket故障排查)
- [3. Playwright故障排查](#3-playwright故障排查)
- [4. 命令行工具](#4-命令行工具)

---

## 1. 管理页面说明

### 1.1 主机管理页面

**访问路径**：主机管理 > 主机白名单 / 连接记录 / IP黑名单

系统提供了完善的前端管理页面，**优先使用页面进行排查**，无需使用命令行或SQL。

#### 主机白名单页面

**功能**：
- 查看所有Engine白名单配置
- 查看主机ID、状态、过期时间
- 一键启用/禁用主机
- 添加/编辑/删除白名单

**使用场景**：
- 检查Engine的hostId是否在白名单中
- 检查白名单状态是否启用（status=1）
- 检查是否已过期

#### 连接记录页面

**功能**：
- 查看所有Engine连接历史记录
- 查看连接状态（已注册、白名单拒绝、黑名单拒绝等）
- 查看拒绝原因（详细错误信息）
- 查看客户端IP、Engine版本、连接时长

**使用场景**：
- **排查连接失败原因**（最重要）
- 查看Engine是否曾经连接成功
- 查看具体的拒绝原因

**状态说明**：
- 🟢 **已注册**：Engine成功连接并注册
- 🔴 **白名单拒绝**：hostId不在白名单或已禁用
- 🔴 **黑名单拒绝**：IP被封禁
- 🟡 **重复连接**：同一hostId已有连接
- 🟡 **异常断开**：网络异常或Engine崩溃

#### IP黑名单页面

**功能**：
- 查看所有被封禁的IP
- 查看封禁原因、封禁类型（临时/永久）
- 查看命中次数、过期时间
- 解除封禁

**使用场景**：
- 检查Engine的IP是否被封禁
- 查看封禁原因
- 手动解除封禁

### 1.2 WebSocket调试页面

**访问路径**：主机管理 > WebSocket调试

**功能**：
- 可视化测试WebSocket连接
- 发送自定义消息到Engine
- 实时查看消息响应
- 内置常用消息示例

**使用场景**：
- 测试Engine是否在线
- 测试消息是否能正常发送和接收
- 调试新开发的功能
- 查看消息格式是否正确

**详细说明**：参考 `WxFbsir-ui/src/views/business/debug/README.md`

---

## 2. WebSocket故障排查

### 2.1 Engine连接失败

#### 步骤1：使用连接记录页面排查（推荐）

1. **打开连接记录页面**：主机管理 > 连接记录
2. **搜索Engine的hostId**：在搜索框输入hostId
3. **查看最近的连接记录**：
   - 如果有记录，查看状态和拒绝原因
   - 如果没有记录，说明Engine未尝试连接

**常见拒绝原因**：

| 状态 | 原因 | 解决方法 |
|------|------|----------|
| **白名单拒绝** | hostId不在白名单 | 在白名单页面添加该hostId |
| **白名单拒绝** | hostId已禁用 | 在白名单页面启用该hostId |
| **白名单拒绝** | hostId已过期 | 在白名单页面延长过期时间 |
| **黑名单拒绝** | IP被封禁 | 在黑名单页面解除封禁 |
| **重复连接** | 同一hostId已连接 | 断开旧连接或使用不同hostId |

#### 步骤2：检查Engine配置

如果连接记录页面没有任何记录，检查Engine配置：

```yaml
# Engine端 application.yml
websocket:
  admin:
    url: ws://192.168.1.100:8080/ws/engine  # 确认Admin地址正确
    engineId: engine-001                     # 确认engineId唯一
```

#### 步骤3：检查网络连通性（仅在必要时）

```bash
# 测试网络连通
ping <admin_ip>
telnet <admin_ip> 8080
```

---

### 2.2 Engine注册被拒绝

#### 使用白名单页面解决（推荐）

1. **打开白名单页面**：主机管理 > 主机白名单
2. **搜索Engine的hostId**
3. **根据情况处理**：

**情况1：hostId不存在**
- 点击"新增"按钮
- 填写主机ID、主机名称、负责人等信息
- 状态选择"启用"
- 保存

**情况2：hostId已禁用**
- 找到对应记录
- 点击状态开关，启用该主机

**情况3：hostId已过期**
- 点击"编辑"按钮
- 修改过期时间或设置为永不过期
- 保存

#### 使用SQL查询（备用方案）

仅在页面无法访问时使用：

```sql
-- 查询白名单
SELECT * FROM ws_host_whitelist WHERE host_id = 'engine-001';

-- 添加到白名单
INSERT INTO ws_host_whitelist (host_id, host_name, status, del_flag) 
VALUES ('engine-001', 'Engine节点1', 1, 0);

-- 启用白名单
UPDATE ws_host_whitelist SET status = 1 WHERE host_id = 'engine-001';
```

---

### 2.3 心跳超时

**现象**：Engine频繁断开重连

**排查方法**：

1. **查看连接记录页面**：
   - 查看连接时长是否很短（<3分钟）
   - 查看是否频繁出现"异常断开"

2. **调整心跳配置**：
```yaml
# Engine端
websocket:
  heartbeat:
    interval: 60000      # 增加到60秒
    timeout: 180000      # 增加到3分钟
```

---

### 2.4 消息无响应

#### 步骤1：使用WebSocket调试页面测试（推荐）

1. **打开调试页面**：主机管理 > WebSocket调试
2. **选择消息类型**：如"健康检查"
3. **发送消息**：点击"发送消息"按钮
4. **查看响应**：在消息输出区查看是否有返回

**如果调试页面有响应**：
- 说明Engine在线且正常
- 检查业务代码的消息类型是否正确

**如果调试页面无响应**：
- 说明Engine离线或消息路由有问题
- 检查Engine是否启动
- 检查engineId是否正确

#### 步骤2：检查消息格式

确认消息格式正确：
```javascript
{
  type: 'AI_DEEPSEEK_QUERY',  // 确认类型拼写正确
  payload: {
    sessionId: 'xxx',
    query: '测试'
  }
}
```

---

## 3. Playwright故障排查

### 3.1 浏览器启动失败

**现象**：
```
[ERROR] 浏览器启动失败: Failed to launch browser
```

**解决方法**：

1. **安装Playwright浏览器**：
```bash
cd WxFbsir-engine
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
```

2. **安装系统依赖**（Linux）：
```bash
# Ubuntu/Debian
sudo apt-get install -y libnss3 libatk1.0-0 libcups2 libdrm2 libxkbcommon0

# CentOS/RHEL
sudo yum install -y nss atk cups-libs libdrm
```

3. **检查文件权限**：
```bash
chmod +x ~/.cache/ms-playwright/chromium-*/chrome-linux/chrome
```

---

### 3.2 内存泄漏

**现象**：Engine运行一段时间后内存持续增长

**排查方法**：

访问监控接口：
```bash
curl http://localhost:9090/engine/playwright/monitor
```

返回示例：
```json
{
  "browserCreateCount": 100,
  "browserCloseCount": 95,
  "leakedBrowsers": 5,      // 泄漏的浏览器数
  "activeSessions": 3
}
```

**解决方法**：

检查代码是否正确释放会话：
```java
BrowserSession session = null;
try {
    session = browserPool.acquirePersistent(userId, "task", false);
    // 执行任务
} finally {
    // 必须释放会话
    if (session != null) {
        session.destroy();
    }
}
```

---

### 3.3 僵尸进程

**现象**：系统中存在大量Chrome进程

**排查方法**：
```bash
# 查看Chrome进程数量
ps aux | grep chrome | wc -l
```

**解决方法**：

1. **手动清理**：
```bash
# 强制终止所有Chrome进程
pkill -9 chrome
pkill -9 chromium
```

2. **清理锁文件**：
```bash
find ~/.wxfbsir/playwright-data/ -name "SingletonLock" -delete
```

3. **重启Engine**：
```bash
systemctl restart wxfbsir-engine
```

---

### 3.4 会话登录状态丢失

**现象**：用户每次使用都需要重新登录

**原因**：使用了临时会话而非持久化会话

**解决方法**：

确保使用持久化会话：
```java
// 正确：持久化会话（保存登录状态）
session = browserPool.acquirePersistent(userId, "deepseek", false);

// 错误：临时会话（不保存登录状态）
session = browserPool.acquireTemporary("task_id");
```

---

### 3.5 截图失败

**现象**：
```
[ERROR] 截图失败: TimeoutError: Timeout 30000ms exceeded
```

**原因**：多线程并发截图冲突

**解决方法**：

确保截图操作已加锁：
```java
private final ReentrantLock screenshotLock = new ReentrantLock();

public String screenshot(Page page) {
    screenshotLock.lock();
    try {
        return page.screenshot();
    } finally {
        screenshotLock.unlock();
    }
}
```

---

## 4. 命令行工具

以下命令仅在页面无法访问时使用。

### 4.1 查看服务状态

```bash
# 查看Admin进程
ps aux | grep WxFbsir-admin

# 查看Engine进程
ps aux | grep WxFbsir-engine

# 查看端口监听
netstat -tuln | grep 8080
netstat -tuln | grep 9090
```

### 4.2 查看日志

```bash
# 实时查看日志
tail -f logs/sys-info.log
tail -f logs/sys-error.log

# 查看WebSocket连接日志
grep "WebSocket" logs/sys-info.log | tail -50

# 查看Engine注册日志
grep "ENGINE_REGISTER" logs/sys-info.log

# 查看错误日志
grep "ERROR" logs/sys-error.log | tail -50
```

### 4.3 数据库查询（备用）

仅在页面无法访问时使用：

```sql
-- 查看白名单列表
SELECT host_id, host_name, status, expire_time 
FROM ws_host_whitelist 
WHERE del_flag = 0;

-- 查看连接记录
SELECT host_id, remote_ip, status, reject_reason, create_time
FROM ws_connection_log
ORDER BY create_time DESC
LIMIT 20;

-- 查看黑名单
SELECT ip_address, block_reason, block_type, expire_time
FROM ws_ip_blacklist
WHERE status = 1 AND del_flag = 0;
```

### 4.4 重启服务

```bash
# 重启Admin
systemctl restart wxfbsir-admin

# 重启Engine
systemctl restart wxfbsir-engine

# 或使用脚本
./restart-admin.sh
./restart-engine.sh
```

---

## 5. 配置文件参考

### Admin端 application.yml

```yaml
server:
  port: 8080

# WebSocket配置
websocket:
  heartbeat:
    checkInterval: 60000  # 心跳检查间隔（毫秒）
    timeout: 180000       # 心跳超时时间（毫秒）
```

### Engine端 application.yml

```yaml
server:
  port: 9090

# WebSocket配置
websocket:
  admin:
    url: ws://192.168.1.100:8080/ws/engine  # Admin地址
    engineId: engine-001                     # 主机ID（需在数据库白名单中）
  heartbeat:
    interval: 60000       # 心跳间隔（毫秒）
    timeout: 180000       # 心跳超时（毫秒）

# Playwright配置
playwright:
  browser:
    type: chromium        # 浏览器类型
    headless: true        # 无头模式
    timeout: 30000        # 启动超时（毫秒）
  pool:
    maxSize: 10           # 最大浏览器实例数
    maxConcurrent: 5      # 最大并发任务数
```

---

## 6. 排查流程总结

### WebSocket问题排查流程

1. **打开连接记录页面** → 查看连接状态和拒绝原因
2. **打开白名单页面** → 检查hostId是否存在、启用、未过期
3. **打开黑名单页面** → 检查IP是否被封禁
4. **使用WebSocket调试页面** → 测试消息是否能正常收发
5. **检查Engine配置** → 确认Admin地址和engineId正确
6. **查看日志**（必要时） → 查看详细错误信息

### Playwright问题排查流程

1. **访问监控接口** → 查看浏览器泄漏情况
2. **检查进程** → 查看是否有僵尸进程
3. **检查代码** → 确认会话正确释放
4. **查看日志** → 查看详细错误信息

---

**文档更新日期**：2026-01-27  
**重要提示**：优先使用前端管理页面进行排查，命令行和SQL仅作为备用方案
