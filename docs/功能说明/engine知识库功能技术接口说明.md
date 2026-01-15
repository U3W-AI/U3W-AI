# 知识库上传接口技术文档

> 基于 Playwright 自动化引擎的知识库上传接口  
> 版本：v1.0.0  
> 更新时间：2026-01-13

---

## 📖 目录

1. [接口概述](#-接口概述)
2. [接口1：元器知识库配置](#-接口1元器知识库配置)
3. [接口2：企业微信机器人知识库配置](#-接口2企业微信机器人知识库配置)
4. [消息类型说明](#-消息类型说明)
5. [错误处理](#-错误处理)

---

## 🎯 接口概述

### 接口架构

```
Admin服务器 → WebSocket → Engine模块 → Playwright自动化 → 外部平台
```

### 接口特点

- ✅ **流式输出**：支持实时推送执行进度和二维码截图
- ✅ **自动登录**：自动检测登录状态，未登录时触发扫码登录
- ✅ **会话管理**：使用持久化浏览器会话，支持多任务串行执行
- ✅ **资源管理**：自动管理浏览器会话和锁资源

### 通信协议

- **传输方式**：WebSocket（双向通信）
- **消息格式**：JSON
- **编码**：UTF-8

---

## 📡 接口1：元器知识库配置

### 基本信息

| 项目 | 说明 |
|------|------|
| **接口类型** | `YUANQI_SET_KNOWLEDGE` |
| **消息类型** | 流式输出（Stream） |
| **Controller** | `YuanQiKnowledgeController` |
| **方法** | `yuanQi_addKnowledge(EngineMessage message)` |
| **推送间隔** | 2000ms |

### 功能说明

完整的元器知识库配置流程，包括：

1. **自动扫码登录**（如果未登录）
   - 检测登录状态
   - 触发扫码登录流程
   - 生成并推送二维码截图
   - 等待用户扫码（5分钟超时）
   - 每30秒更新二维码截图

2. **知识库管理**
   - 检查知识库是否存在
   - 不存在则创建新知识库（通用知识库类型）
   - 填写知识库名称和描述

3. **网页内容导入**
   - 打开知识库详情
   - 选择"网页文件"导入方式
   - 输入网页URL并导入
   - 等待导入完成（最长300秒）
   - 添加标题并导入到文档库

4. **智能体关联**
   - 跳转到"我的智能体"页面
   - 查找并进入指定智能体
   - 检查知识库关联状态
   - 未关联则关联并发布智能体

### 请求参数

#### 消息结构

```json
{
  "type": "YUANQI_SET_KNOWLEDGE",
  "engineId": "engine-001",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "teamName": "个人空间",
    "knowledgeBaseName": "招聘简历匹配知识库",
    "importWebUrl": "https://example.com/knowledge",
    "agentName": "智能助手"
  }
}
```

#### 参数说明

| 参数名 | 类型 | 必填 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `type` | String | ✅ | 消息类型，固定值：`YUANQI_SET_KNOWLEDGE` | `"YUANQI_SET_KNOWLEDGE"` |
| `engineId` | String | ✅ | Engine节点ID | `"engine-001"` |
| `userId` | String | ✅ | 用户ID | `"user-123"` |
| payload.teamName | String | ✅ | 智能体团队名称 | "个人空间" |
| `payload.requestId` | String | ⭕ | 请求ID（Admin自动生成，用于追踪） | `"req-001"` |
| `payload.knowledgeBaseName` | String | ✅ | 知识库名称 | `"招聘简历匹配知识库"` |
| `payload.importWebUrl` | String | ✅ | 要导入的网页URL（必须以http://或https://开头） | `"https://example.com/knowledge"` |
| `payload.agentName` | String | ✅ | 要关联的智能体名称（必须完全匹配） | `"智能助手"` |

### 响应消息

#### 流式消息类型

执行过程中会推送以下类型的消息：

1. **TASK_LOG** - 执行步骤日志
2. **TASK_SCREENSHOT** - 二维码截图（登录过程中）
3. **TASK_RESULT** - 最终结果（成功/失败）

#### 成功响应

```json
{
  "type": "TASK_RESULT",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "success": true,
    "data": {
      "message": "知识库配置完成",
      "teamName": "个人空间",
      "knowledgeBaseName": "招聘简历匹配知识库",
      "agentName": "智能助手",
      "importWebUrl": "https://example.com/knowledge"
    },
    "timestamp": 1736144400000
  }
}
```

#### 失败响应

```json
{
  "type": "TASK_RESULT",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "success": false,
    "errorCode": "TASK_ERROR",
    "errorMessage": "知识库配置失败: 无法找到智能体",
    "timestamp": 1736144400000
  }
}
```

#### 登录超时响应

```json
{
  "type": "TASK_RESULT",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "success": false,
    "timeout": true,
    "qrCodeUrl": "http://admin-server:8080/profile/engine/user-123/2026/01/12/yuanqi_qrcode_3.png",
    "timestamp": 1736144400000
  }
}
```

### 执行流程

```
1. 获取用户会话锁（6分钟超时）
   ↓
2. 获取持久化浏览器会话
   ↓
3. 导航到元器首页
   ↓
4. 检查登录状态
   ├─ 已登录 → 跳过登录步骤
   └─ 未登录 → 触发扫码登录
      ├─ 生成二维码截图
      ├─ 推送二维码给前端
      ├─ 等待用户扫码（5分钟超时）
      ├─ 每30秒更新二维码
      └─ 检测登录成功
5. 检查团队是否存在
   ├─ 存在 → 跳转到团队界面
   └─ 不存在 → 提示用户未检测到团队
   ↓
6. 进入知识库页面
   ↓
7. 检查知识库是否存在
   ├─ 已存在 → 跳过创建
   └─ 不存在 → 创建新知识库
      ├─ 点击"新建知识库"
      ├─ 选择"通用知识库"
      ├─ 填写名称和描述
      └─ 确认创建
   ↓
8. 打开知识库详情
   ↓
9. 导入网页内容
   ├─ 点击导入按钮
   ├─ 选择"网页文件"
   ├─ 输入网页URL
   ├─ 点击"导入网页"
   ├─ 等待导入完成（最长300秒）
   └─ 添加标题并导入到文档库
   ↓
10. 关联智能体
   ├─ 跳转到"我的智能体"
   ├─ 查找并进入指定智能体
   ├─ 检查知识库关联状态
   ├─ 未关联 → 关联并发布
   └─ 已关联 → 跳过发布
   ↓
11. 发送成功结果
   ↓
12. 释放资源（锁、浏览器会话）
```

### 超时设置

| 操作 | 超时时间 | 说明 |
|------|---------|------|
| 用户会话锁获取 | 6分钟 | 防止死锁 |
| 扫码登录等待 | 5分钟 | 从开始等待到超时 |
| 网页导入等待 | 300秒 | 等待导入完成 |

### 错误码说明

| 错误码 | 说明 | 可能原因 |
|--------|------|---------|
| `TASK_ERROR` | 任务执行失败 | 网络错误、页面元素未找到、操作失败等 |
| `TIMEOUT` | 登录超时 | 用户未在5分钟内完成扫码 |
| `LOCK_TIMEOUT` | 获取锁超时 | 用户有其他任务正在执行 |

---

## 📡 接口2：企业微信机器人知识库配置

### 基本信息

| 项目 | 说明 |
|------|------|
| **接口类型** | `ROBOT_SET_KNOWLEDGE` |
| **消息类型** | 流式输出（Stream） |
| **Controller** | `RobotController` |
| **方法** | `robot_addKnowledge(EngineMessage message)` |
| **推送间隔** | 2000ms |

### 功能说明

完整的企业微信机器人知识库配置流程，包括：

1. **企业微信扫码登录**
   - 导航到企业微信登录页面
   - 生成并推送二维码截图
   - 等待用户扫码（5分钟超时）
   - 每30秒更新二维码截图
   - 检测"退出"元素判断登录成功

2. **导航到机器人管理**
   - 点击"安全与管理"
   - 点击"管理工具"
   - 点击"智能机器人"

3. **定位目标机器人**
   - 查找指定名称的机器人（完全匹配）
   - 点击机器人进入详情页
   - 点击第二个"查看"按钮

4. **添加知识库内容**
   - 点击"添加内容"按钮
   - 选择"网页"导入方式
   - 输入网页URL
   - 点击"确定"按钮
   - 等待添加完成（检测"添加中"状态消失）

### 请求参数

#### 消息结构

```json
{
  "type": "ROBOT_SET_KNOWLEDGE",
  "engineId": "engine-001",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "robotName": "智能客服机器人",
    "importWebUrl": "https://example.com/knowledge"
  }
}
```

#### 参数说明

| 参数名 | 类型 | 必填 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `type` | String | ✅ | 消息类型，固定值：`ROBOT_SET_KNOWLEDGE` | `"ROBOT_SET_KNOWLEDGE"` |
| `engineId` | String | ✅ | Engine节点ID | `"engine-001"` |
| `userId` | String | ✅ | 用户ID | `"user-123"` |
| `payload.requestId` | String | ⭕ | 请求ID（Admin自动生成） | `"req-001"` |
| `payload.robotName` | String | ✅ | 智能机器人名称（必须完全匹配） | `"智能客服机器人"` |
| `payload.importWebUrl` | String | ✅ | 要导入的网页URL（必须以http://或https://开头） | `"https://example.com/knowledge"` |

### 响应消息

#### 流式消息类型

执行过程中会推送以下类型的消息：

1. **TASK_LOG** - 执行步骤日志
2. **TASK_SCREENSHOT** - 二维码截图（登录过程中）
3. **TASK_RESULT** - 最终结果（成功/失败）

#### 成功响应

```json
{
  "type": "TASK_RESULT",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "success": true,
    "data": {
      "message": "知识集添加完成",
      "robotName": "智能客服机器人",
      "importWebUrl": "https://example.com/knowledge"
    },
    "timestamp": 1736144400000
  }
}
```

#### 失败响应

```json
{
  "type": "TASK_RESULT",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "success": false,
    "errorCode": "TASK_ERROR",
    "errorMessage": "机器人知识库配置失败: 无法找到机器人",
    "timestamp": 1736144400000
  }
}
```

### 执行流程

```
1. 获取用户会话锁（6分钟超时）
   ↓
2. 获取持久化浏览器会话
   ↓
3. 导航到企业微信登录页面
   ↓
4. 生成并推送二维码截图
   ↓
5. 等待用户扫码（5分钟超时）
   ├─ 每30秒更新二维码截图
   └─ 检测"退出"元素判断登录成功
   ↓
6. 导航到安全与管理页面
   ↓
7. 进入管理工具页面
   ↓
8. 选择智能机器人
   ↓
9. 定位目标机器人
   ├─ 查找指定名称的机器人
   └─ 点击进入详情页
   ↓
10. 进入机器人详情页面
    └─ 点击第二个"查看"按钮
   ↓
11. 添加知识库内容
    ├─ 点击"添加内容"按钮
    ├─ 选择"网页"导入方式
    ├─ 输入网页URL
    ├─ 点击"确定"按钮
    └─ 等待添加完成（检测"添加中"状态）
   ↓
12. 发送成功结果
   ↓
13. 释放资源（锁、浏览器会话）
```

### 超时设置

| 操作 | 超时时间 | 说明 |
|------|---------|------|
| 用户会话锁获取 | 6分钟 | 防止死锁 |
| 扫码登录等待 | 5分钟 | 从开始等待到超时 |
| 登录状态检测 | 30秒 | 等待"退出"元素出现 |
| 知识库添加等待 | 5分钟 | 等待"添加中"状态消失 |

### 错误码说明

| 错误码 | 说明 | 可能原因 |
|--------|------|---------|
| `TASK_ERROR` | 任务执行失败 | 网络错误、机器人未找到、操作失败等 |
| `TIMEOUT` | 登录超时 | 用户未在5分钟内完成扫码 |
| `LOCK_TIMEOUT` | 获取锁超时 | 用户有其他任务正在执行 |

---

## 📨 消息类型说明

### TASK_LOG - 任务日志

**用途**：推送任务执行进度的文本日志

**消息格式**：
```json
{
  "type": "TASK_LOG",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "message": "正在创建知识库...",
    "timestamp": 1736144400000
  }
}
```

**常见日志消息**：
- `"正在获取浏览器会话..."`
- `"正在检查登录状态，如未登录将自动扫码登录..."`
- `"请使用微信扫码登录"`
- `"登录成功！"`
- `"正在进入知识库页面..."`
- `"知识库配置完成！"`

### TASK_SCREENSHOT - 任务截图

**用途**：推送二维码截图URL（登录过程中）

**消息格式**：

```json
{
  "type": "TASK_SCREENSHOT",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "screenshotUrl": "http://admin-server:8080/profile/engine/user-123/2026/01/12/yuanqi_qrcode_1.png",
    "timestamp": 1736144400000
  }
}
```

**说明**：
- 登录过程中会推送多次截图（初始截图 + 每30秒更新）
- 前端应显示二维码弹窗供用户扫码
- 截图URL由Engine上传到Admin服务器后返回

### TASK_RESULT - 任务结果

**用途**：推送最终结果（成功或失败）

**成功消息格式**：
```json
{
  "type": "TASK_RESULT",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "success": true,
    "data": {
      "message": "知识库配置完成",
      "knowledgeBaseName": "招聘简历匹配知识库",
      "agentName": "智能助手",
      "importWebUrl": "https://example.com/knowledge"
    },
    "timestamp": 1736144400000
  }
}
```

**失败消息格式**：
```json
{
  "type": "TASK_RESULT",
  "userId": "user-123",
  "payload": {
    "requestId": "req-001",
    "success": false,
    "errorCode": "TASK_ERROR",
    "errorMessage": "知识库配置失败: 无法找到智能体",
    "timestamp": 1736144400000
  }
}
```

---

## ⚠️ 错误处理

### 常见错误场景

#### 1. 用户会话锁获取超时

**错误消息**：
```json
{
  "type": "TASK_RESULT",
  "payload": {
    "success": false,
    "errorMessage": "当前有其他任务正在执行，请稍后重试"
  }
}
```

**原因**：同一用户有其他任务正在执行

**解决方案**：
- 等待当前任务完成
- 或取消当前任务后重试

#### 2. 登录超时

**错误消息**：
```json
{
  "type": "TASK_RESULT",
  "payload": {
    "success": false,
    "timeout": true,
    "qrCodeUrl": "..."
  }
}
```

**原因**：用户未在5分钟内完成扫码

**解决方案**：
- 重新发起请求
- 确保二维码未过期

#### 3. 智能体/机器人未找到

**错误消息**：
```json
{
  "type": "TASK_RESULT",
  "payload": {
    "success": false,
    "errorMessage": "未查询到目标智能体"
  }
}
```

**原因**：
- 智能体/机器人名称不匹配（需要完全匹配）
- 智能体/机器人不存在

**解决方案**：
- 检查名称是否正确
- 确认智能体/机器人已创建

#### 4. 网络连接失败

**错误消息**：
```json
{
  "type": "TASK_RESULT",
  "payload": {
    "success": false,
    "errorMessage": "无法加载元器首页，请检查网络连接"
  }
}
```

**原因**：Engine无法访问外部平台

**解决方案**：
- 检查Engine服务器网络连接
- 检查防火墙设置

#### 5. 页面元素未找到

**错误消息**：

```json
{
  "type": "TASK_RESULT",
  "payload": {
    "success": false,
    "errorMessage": "知识库操作失败"
  }
}
```

**原因**：页面结构变化或元素定位失败

**解决方案**：
- 检查外部平台页面是否更新
- 更新元素定位逻辑

---

**最后更新**：2026-01-13

**文档版本**: v1.0.0