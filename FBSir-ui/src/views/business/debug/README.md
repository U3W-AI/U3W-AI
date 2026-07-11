# WebSocket 调试工具使用指南
> 更新日期：2026-07-11

> **最新更新日期：2026-07-11**

## 📋 功能概述

WebSocket调试工具是一个专为开发者设计的可视化调试页面，用于快速测试和调试WebSocket消息通信。

### 核心特性

- ✅ **自动连接管理** - 自动从系统配置获取WebSocket URL和Token
- ✅ **可视化消息输出** - 彩色区分发送/接收消息，自动解析JSON
- ✅ **内置示例** - 预置常用消息类型和Payload示例
- ✅ **友好输入** - 支持多行JSON输入，Ctrl+Enter快速发送
- ✅ **美化显示** - 自动格式化JSON，高亮显示关键信息
- ✅ **日志导出** - 支持导出调试日志到本地文件

---

## 🚀 快速开始

### 1. 访问页面

**路径**: 主机管理 > WebSocket调试

**权限**: 仅角色1（超级管理员）和角色2（管理员）可访问

### 2. 连接WebSocket

页面加载后会自动连接WebSocket服务器，连接地址从系统配置自动获取。

**连接状态**:
- 🟢 **已连接** - 可以发送和接收消息
- ⚪ **未连接** - 点击"连接"按钮重新连接

### 3. 发送消息

#### 方式1：使用预置示例（推荐新手）

1. 在"消息类型"下拉框中选择预置类型（如：健康检查、百度热搜）
2. 系统自动加载对应的Payload示例
3. 点击"发送消息"按钮

#### 方式2：自定义消息

1. 在"消息类型"输入框中输入自定义类型（如：`MY_CUSTOM_CAPABILITY`）
2. 在"Payload"文本框中输入JSON格式的参数
3. 点击"发送消息"或按 `Ctrl+Enter` 快速发送

---

## 📝 使用示例

### 示例1：健康检查（单次输出）

**消息类型**: `SIMPLE_HEALTH_CHECK_DEMO`

**Payload**:
```json
{
  "includeDetails": true
}
```

**预期输出**:
- 收到一条 `TASK_RESULT` 类型的消息
- 包含系统健康状态、性能数据、组件状态等信息

---

### 示例2：百度热搜（流式输出）

**消息类型**: `BAIDU_HOT_SEARCH_DEMO`

**Payload**:
```json
{
  "clickIndex": 0,
  "needScreenshot": true
}
```

**预期输出**（按顺序）:
1. `TASK_LOG` - 正在启动浏览器...
2. `TASK_LOG` - 浏览器启动成功
3. `TASK_LOG` - 正在打开百度首页...
4. `TASK_LOG` - 百度首页加载完成
5. `TASK_LOG` - 正在抓取热搜榜数据...
6. `TASK_LOG` - 成功抓取 10 条热搜数据
7. `TASK_LOG` - 正在点击第 1 条热搜...
8. `TASK_LOG` - 页面加载完成
9. `TASK_LOG` - 正在截图...
10. `TASK_LOG` - 截图完成，正在上传...
11. `TASK_LOG` - 图片上传成功: http://...
12. `TASK_SCREENSHOT` - 截图URL
13. `TASK_RESULT` - 最终结果（包含热搜列表和截图）

---

### 示例3：自定义能力测试

**场景**: 测试你刚开发的新能力

**消息类型**: `MY_FIRST_CAPABILITY`

**Payload**:
```json
{
  "name": "张三",
  "age": 25,
  "config": {
    "timeout": 30000,
    "retry": true
  }
}
```

**步骤**:
1. 在消息类型输入框输入 `MY_FIRST_CAPABILITY`
2. 在Payload输入自定义参数（支持嵌套对象和数组）
3. 点击"格式化"按钮美化JSON
4. 点击"发送消息"
5. 在消息输出区域查看返回结果

---

## 🎨 界面说明

### 上半部分：消息发送区

| 字段 | 说明 | 示例 |
|------|------|------|
| **连接地址** | WebSocket连接URL（自动获取） | `ws://当前页面主机/prod-api/ws/client?...` |
| **Engine ID** | 目标Engine节点ID | `engine-001` |
| **消息类型** | WebSocket消息类型（可选择或输入） | `SIMPLE_HEALTH_CHECK_DEMO` |
| **Payload** | JSON格式的消息参数（支持多行） | `{"includeDetails": true}` |

**快捷操作**:
- 🔄 **重新连接** - 断开并重新建立WebSocket连接
- ✨ **格式化** - 格式化当前Payload的JSON
- 📋 **加载示例** - 加载当前消息类型的示例Payload
- 📤 **发送消息** - 发送WebSocket消息（或按 `Ctrl+Enter`）
- 🗑️ **清空消息** - 清空下方的消息输出区域
- 💾 **导出日志** - 导出所有消息到本地文件

### 下半部分：消息输出区

**消息颜色区分**:
- 🟢 **绿色边框** - 发送的消息
- 🔵 **蓝色边框** - 接收的消息
- 🔴 **红色边框** - 错误消息
- ⚪ **灰色边框** - 系统消息

**消息卡片内容**:
- 消息方向（发送/接收）
- 时间戳（精确到毫秒）
- 消息类型标签（如 `TASK_RESULT`、`TASK_LOG`）
- 消息内容（JSON自动美化显示）
- 消息解析（自动提取关键信息：类型、成功状态、消息等）

**显示选项**:
- ✅ **自动滚动** - 新消息到达时自动滚动到底部
- ✅ **美化显示** - JSON消息自动格式化显示

---

## 💡 使用技巧

### 技巧1：快速发送消息

按 `Ctrl+Enter` 可以快速发送消息，无需点击按钮。

### 技巧2：复制输出内容

直接选中消息内容，使用 `Ctrl+C` 复制到剪贴板。

### 技巧3：调试复杂参数

对于复杂的嵌套JSON参数，建议：
1. 先在外部编辑器（如VSCode）编写JSON
2. 粘贴到Payload输入框
3. 点击"格式化"按钮检查格式
4. 发送消息

### 技巧4：对比多次请求

不要点击"清空消息"，保留历史消息记录，方便对比不同参数下的输出结果。

### 技巧5：导出日志分析

使用"导出日志"功能，将完整的请求和响应保存到本地文件，便于后续分析。

---

## 🐛 常见问题

### Q1: 连接失败怎么办？

**可能原因**:
1. Engine节点未启动
2. 网络连接问题
3. Token过期

**解决方案**:
1. 检查Engine是否正常运行
2. 刷新页面重新获取Token
3. 点击"重新连接"按钮

---

### Q2: 发送消息后无响应？

**可能原因**:
1. Engine ID不存在或未连接
2. 消息类型不存在
3. Payload参数错误

**解决方案**:
1. 检查Engine ID是否正确（默认：`engine-001`）
2. 检查消息类型是否在Engine中注册
3. 检查Payload格式是否为有效的JSON

---

### Q3: JSON格式错误？

**错误提示**: `Payload格式错误，请输入有效的JSON`

**解决方案**:
1. 确保JSON格式正确（使用双引号，不能有多余的逗号）
2. 点击"格式化"按钮检查格式
3. 使用JSON验证工具（如 https://jsonlint.com/）

**常见错误**:
```json
// ❌ 错误：使用单引号
{'key': 'value'}

// ✅ 正确：使用双引号
{"key": "value"}

// ❌ 错误：末尾多余逗号
{"key": "value",}

// ✅ 正确：无多余逗号
{"key": "value"}
```

---

### Q4: 如何查看截图？

流式输出任务（如百度热搜）会返回 `TASK_SCREENSHOT` 类型的消息，其中包含 `screenshotUrl` 字段。

**查看方式**:
1. 在消息输出区找到 `TASK_SCREENSHOT` 消息
2. 展开JSON，找到 `payload.screenshotUrl`
3. 复制URL到浏览器打开

**示例URL**:
```
http://localhost:8080/profile/engine/1/2025/12/29/baidu_hot_0_1766989926645.png
```

---

## 🔧 开发者说明

### 文件位置

```
FBSir-ui/
├── src/
│   ├── views/
│   │   └── business/
│   │       └── debug/
│   │           ├── index.vue          # 调试页面组件
│   │           └── README.md          # 本文档
│   └── utils/
│       └── websocket.js               # WebSocket工具类
```

### 菜单配置

- **菜单ID**: 126
- **父菜单**: 7（主机管理）
- **路由**: `/business/debug`
- **权限**: `business:debug:view`
- **图标**: `bug`

### 按钮权限

| 权限标识 | 说明 |
|---------|------|
| `business:debug:query` | 查看调试工具 |
| `business:debug:send` | 发送消息 |
| `business:debug:clear` | 清空消息 |
| `business:debug:export` | 导出日志 |

### WebSocket工具类

位置: `@/utils/websocket.js`

**核心方法**:
- `httpToWs(httpUrl)` - HTTP URL转WebSocket URL
- `wsToHttp(wsUrl)` - WebSocket URL转HTTP URL
- `getWebSocketUrl(path)` - 从系统配置获取WebSocket URL
- `buildWebSocketUrl(options)` - 构建完整的WebSocket连接URL

**特性**:
- 自动识别 `http/https` 并转换为 `ws/wss`
- 相对 API 前缀（`/dev-api`、`/stage-api`、`/prod-api`）使用当前页面同源地址，交由开发代理或 Nginx 转发
- 可通过 `VITE_APP_WS_BASE_URL` 显式指定非同源 WebSocket 网关
- 支持自定义路径和查询参数

---

## 📚 相关文档

- [WebSocket通信完整指南](../../../../../docs/功能说明/engine/WebSocket通信完整指南.md) - 深入了解WebSocket通信机制
- [快速入门 - 5分钟实现你的第一个能力](../../../../../docs/功能说明/engine/WebSocket通信完整指南.md#0-快速入门---5分钟实现你的第一个能力) - 开发新能力
- [流式输出 vs 单次输出完整指南](../../../../../docs/功能说明/engine/WebSocket通信完整指南.md#12-流式输出-vs-单次输出完整指南) - 选择合适的实现方式
- [演示能力使用指南](../../../../../FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/demo/README.md) - 完整示例代码

---

**维护者**: FBSir Team
**版本**: v1.0.0  
**创建日期**: 2025-12-29
