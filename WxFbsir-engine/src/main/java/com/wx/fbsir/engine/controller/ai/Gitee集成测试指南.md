# 🧪 Gitee AI Chat 集成测试指南

> **作者**: 实习生  
> **日期**: 2026-01-22  
> **版本**: v1.0 (原型阶段)

---

## 📋 已完成的工作

### ✅ 前端配置
- ✅ `WxFbsir-ui/src/config/engineConfig.js` - 已添加 Gitee AI Chat 配置
  - 消息类型: `AI_GITEE_QUERY`, `GITEE_CHECK_LOGIN`, `GITEE_SCAN_LOGIN`
  - 会话ID字段: `giteeChatId`
  - 排序权重: 2

### ✅ Engine 端实现
- ✅ `GiteeController.java` - AI 控制器（3个核心功能）
  - 登录状态检测 (`GITEE_CHECK_LOGIN`)
  - 扫码登录 (`GITEE_SCAN_LOGIN`)
  - AI 咨询 (`AI_GITEE_QUERY`)

- ✅ `GiteeAiUtil.java` - 工具类（封装 Playwright 操作）
  - `checkLoginStatus()` - 登录状态检测
  - `navigateToLoginPage()` - 导航到登录页
  - `sendMessageAndWaitResponse()` - 发送消息并等待回复

---

## 🎯 测试流程

### 阶段1：准备工作

#### 1.1 确认 Gitee AI Chat URL

⚠️ **重要**：当前代码中使用的是占位符 URL，需要替换为实际地址。

打开 `GiteeAiUtil.java`，修改以下常量：

```java
// 🔥 修改为实际的 Gitee AI Chat 地址
private static final String GITEE_AI_HOME_URL = "https://ai.gitee.com/";
private static final String GITEE_AI_LOGIN_URL = "https://ai.gitee.com/login";
```

#### 1.2 启动服务

```bash
# 1. 启动 Engine 服务
cd WxFbsir-engine
mvn spring-boot:run

# 2. 启动 Admin 服务（另一个终端）
cd WxFbsir-business
mvn spring-boot:run

# 3. 启动前端服务（另一个终端）
cd WxFbsir-ui
npm run dev
```

#### 1.3 配置用户主机ID

1. 访问 `http://localhost:8080`
2. 登录系统
3. 进入 **个人中心** → **主机配置**
4. 设置主机ID（例如 `engine-001`）
5. 检测连接确认 Engine 在线

---

### 阶段2：测试登录检测

#### 2.1 前端操作

1. 进入 **内容管理** → **AI助手**
2. 查看 **AI选择配置** 区域
3. 确认能看到 **Gitee AI Chat** 选项
4. 点击 Gitee AI Chat 的 "检测登录" 按钮（如果有）

#### 2.2 预期结果

- WebSocket 消息流:
  ```
  → GITEE_CHECK_LOGIN (前端发送)
  ← AI_TASK_RESULT (Engine返回)
     {
       "isLoggedIn": false,  // 首次应该是未登录
       "userName": null,
       "platform": "Gitee AI Chat"
     }
  ```

- 前端提示: "Gitee AI Chat 未登录"

#### 2.3 调试方法

**查看 Engine 日志**:
```bash
tail -f logs/engine.log | grep "Gitee"
```

应该看到类似输出：
```
🔍 [Gitee登录检测] 开始 - 用户: 103, 会话: xxx, AI: gitee
✅ [Gitee登录检测] 完成 - 登录状态: false, 用户: false
```

**查看浏览器控制台**:
- 打开开发者工具 (F12)
- Network → WS → 查看 WebSocket 消息
- 筛选包含 `GITEE` 的消息

---

### 阶段3：测试扫码登录

#### 3.1 前端操作

1. 点击 Gitee AI Chat 的 "扫码登录" 按钮
2. 观察弹窗显示二维码
3. 使用 Gitee 账号扫码
4. 等待登录成功提示

#### 3.2 预期结果

- WebSocket 消息流:
  ```
  → GITEE_SCAN_LOGIN (前端发送)
  ← TASK_LOG: "正在初始化浏览器..."
  ← TASK_LOG: "正在导航到 Gitee AI Chat 登录页..."
  ← TASK_LOG: "正在获取登录二维码..."
  ← TASK_SCREENSHOT: 二维码截图URL
  ← TASK_LOG: "等待扫码登录..."
  ← TASK_LOG: "✅ 登录成功！"
  ← TASK_RESULT: 
     {
       "success": true,
       "userName": "用户名",
       "platform": "Gitee AI Chat",
       "loginTime": 时间戳
     }
  ```

- 前端显示: 
  - 二维码图片
  - 进度日志
  - 成功提示 "Gitee AI Chat 登录成功"

#### 3.3 可能遇到的问题

**问题1: 未找到二维码**
- 原因: 登录页结构与预期不符
- 解决: 
  1. 手动访问 Gitee AI Chat 登录页
  2. 查看二维码元素的选择器
  3. 调整 `GiteeAiUtil.navigateToLoginPage()` 中的选择器

**问题2: 登录状态未检测到**
- 原因: 登录成功标志选择器不正确
- 解决:
  1. 登录后查看页面元素
  2. 找到用户头像/用户名的选择器
  3. 调整 `GiteeAiUtil.checkLoginStatus()` 中的选择器

---

### 阶段4：测试 AI 咨询

#### 4.1 前端操作

1. 确保已登录 Gitee AI Chat
2. 在 **AI选择配置** 中勾选 **Gitee AI Chat**
3. 在提示词输入框中输入问题，例如: "介绍一下 Gitee"
4. 点击 "发送" 按钮
5. 观察任务流程和结果

#### 4.2 预期结果

- WebSocket 消息流:
  ```
  → AI_GITEE_QUERY (前端发送)
     {
       "query": "介绍一下 Gitee",
       "sessionId": "xxx",
       "chatId": "xxx",
       "aiType": "gitee"
     }
  
  ← AI_TASK_LOG: "正在连接 Gitee AI Chat..."
  ← AI_TASK_LOG: "正在验证登录状态..."
  ← AI_TASK_LOG: "登录验证通过，准备发送问题..."
  ← AI_TASK_LOG: "正在向 Gitee AI 发送问题..."
  ← AI_TASK_LOG: "✅ Gitee AI 回复完成"
  ← AI_TASK_SCREENSHOT: 结果截图URL
  ← AI_TASK_RESULT:
     {
       "answer": "Gitee是...（AI的回复内容）",
       "giteeChatId": "xxx",
       "shareUrl": "",
       "elapsedTime": 15,
       "query": "介绍一下 Gitee"
     }
  ```

- 前端显示:
  - **任务流程**: 显示进度日志
  - **执行可视化**: 显示截图轮播
  - **AI响应结果**: 显示 Gitee AI Chat 的回复

#### 4.3 可能遇到的问题

**问题1: 未找到输入框**
- 原因: 输入框选择器不正确
- 解决:
  1. 手动访问 Gitee AI Chat 聊天页面
  2. 找到输入框的选择器（可能是 `textarea` 或 `input`）
  3. 调整 `GiteeAiUtil.sendMessageAndWaitResponse()` 中的选择器

**问题2: 未找到发送按钮**
- 原因: 发送按钮选择器不正确
- 解决:
  1. 查看发送按钮的文本和属性
  2. 调整选择器，或使用 `Enter` 键发送
  3. 当前代码已支持按 Enter 键作为备选方案

**问题3: AI 回复提取失败**
- 原因: 回复内容选择器不正确
- 解决:
  1. 发送一条消息后，查看 AI 回复的 DOM 结构
  2. 找到包含回复内容的元素选择器
  3. 调整 `GiteeAiUtil.sendMessageAndWaitResponse()` 中的提取策略

---

## 🔧 选择器调整指南

### 关键方法和需要调整的选择器

#### 1. `checkLoginStatus()` - 登录状态检测

```java
// 🔥 需要根据实际页面调整以下选择器

// 登录按钮（未登录标志）
Locator loginButton = page.locator("button:has-text('登录'), a:has-text('登录')");

// 用户头像（已登录标志）
Locator userAvatar = page.locator(".user-avatar, .avatar, [class*='avatar']");

// 用户名
Locator userNameElement = page.locator(".user-name, .username, [class*='username']");

// 聊天输入框（已登录才有）
Locator inputBox = page.locator("textarea, input[type='text']");
```

#### 2. `sendMessageAndWaitResponse()` - 发送消息

```java
// 🔥 需要根据实际页面调整以下选择器

// 输入框
Locator inputBox = page.locator("textarea, input[type='text']");

// 发送按钮
Locator sendButton = page.locator("button:has-text('发送'), button[type='submit']");

// 停止生成按钮（判断回复是否完成）
Locator stopButton = page.locator("button:has-text('停止'), button:has-text('Stop')");

// AI 回复内容
Locator messages = page.locator(".message, .chat-message, [class*='message']");
Locator aiReply = page.locator(".ai-response, .assistant-message");
```

### 如何找到正确的选择器

1. **使用浏览器开发者工具**:
   - 右键点击目标元素 → "检查"
   - 查看元素的 `class`、`id`、`data-*` 属性
   - 在 Console 中测试选择器:
     ```javascript
     document.querySelector('.your-selector')
     ```

2. **使用 Playwright Inspector**:
   ```bash
   # 启动 Playwright Inspector
   npx playwright codegen https://ai.gitee.com
   ```
   - 点击页面元素自动生成选择器

3. **通用选择器策略**:
   - 优先使用 `data-testid` 等测试属性
   - 其次使用稳定的 `class` 名称
   - 避免使用动态生成的 `class`（如 `css-xyz123`）
   - 可以使用文本匹配: `button:has-text('登录')`

---

## 📊 数据库验证

### 验证聊天历史存储

登录 MySQL，执行以下查询：

```sql
-- 查看最新的 Gitee AI Chat 记录
SELECT 
    id,
    user_id,
    userPrompt,
    gitee_chat_id,
    JSON_EXTRACT(data, '$.answer') as answer,
    JSON_EXTRACT(data, '$.progressLogs') as logs,
    JSON_EXTRACT(data, '$.screenshots') as screenshots,
    create_time
FROM wc_chat_history 
WHERE user_id = '你的用户ID'
ORDER BY create_time DESC
LIMIT 5;

-- 检查 gitee_chat_id 字段是否存在
DESCRIBE wc_chat_history;

-- 如果没有 gitee_chat_id 字段，需要添加：
ALTER TABLE wc_chat_history 
ADD COLUMN gitee_chat_id VARCHAR(100) COMMENT 'Gitee AI Chat会话ID';
```

---

## 🐛 常见问题排查

### 问题1: 前端看不到 Gitee AI Chat 选项

**检查步骤**:
1. 清除浏览器缓存，刷新页面
2. 检查浏览器控制台是否有 JS 错误
3. 验证 `engineConfig.js` 是否正确导入

**验证命令**:
```javascript
// 在浏览器控制台执行
import { ENGINE_CONFIGS } from '@/config/engineConfig'
console.log(ENGINE_CONFIGS.find(c => c.id === 'gitee'))
```

### 问题2: WebSocket 消息未发送

**检查步骤**:
1. 查看 Network → WS，确认 WebSocket 已连接
2. 检查 Engine 是否在线（个人中心 → 主机配置）
3. 查看 Admin 日志: `tail -f logs/sys-info.log`

### 问题3: Engine 未收到消息

**检查步骤**:
1. 确认 `GiteeController` 类上有 `@Controller` 注解
2. 确认方法上有 `@OnceCapability` 或 `@StreamCapability` 注解
3. 确认消息类型匹配: `type = "GITEE_CHECK_LOGIN"`
4. 重启 Engine 服务

### 问题4: 浏览器无法打开 Gitee AI Chat

**可能原因**:
1. URL 不正确
2. 网络无法访问
3. Playwright 浏览器未正确安装

**解决方法**:
```bash
# 安装 Playwright 浏览器
cd WxFbsir-engine
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

---

## 📈 下一步优化方向

### 原型验证后的改进计划

1. **精确选择器** ✨
   - 实际访问 Gitee AI Chat
   - 确定所有关键元素的准确选择器
   - 更新 `GiteeAiUtil.java` 中的选择器

2. **会话管理** 🔄
   - 实现 `giteeChatId` 的提取和存储
   - 支持上下文连续对话
   - 参考 DeepSeek 的会话复用逻辑

3. **错误处理** 🛡️
   - 统一错误码定义
   - 完善异常捕获和提示
   - 添加重试机制

4. **功能扩展** 🚀
   - 支持特殊功能选项（如果 Gitee AI Chat 有）
   - 支持文件上传（如果支持）
   - 支持分享链接提取

5. **性能优化** ⚡
   - 优化等待策略
   - 减少不必要的截图
   - 提升响应速度

---

## ✅ 测试清单

使用以下清单验证原型功能：

- [ ] 前端显示 Gitee AI Chat 选项
- [ ] 登录检测功能正常
- [ ] 扫码登录流程完整
- [ ] 二维码正常显示
- [ ] 登录状态正确更新
- [ ] AI 咨询请求发送成功
- [ ] 进度日志正常显示
- [ ] 截图正常显示
- [ ] AI 回复内容正确提取
- [ ] 数据库正确存储聊天历史
- [ ] WebSocket 消息流正常
- [ ] 错误提示友好清晰

---

## 📞 技术支持

如遇到问题，请检查以下日志：

1. **Engine 日志**: `WxFbsir-engine/logs/engine.log`
2. **Admin 日志**: `WxFbsir-business/logs/sys-info.log`
3. **前端控制台**: 浏览器开发者工具 Console
4. **WebSocket 消息**: 浏览器开发者工具 Network → WS

---

**祝测试顺利！🎉**

