# 🚀 AIGC功能完整迁移指南

## 📊 项目对比总览

### 旧项目 vs 新项目架构对比

| 组件 | 旧项目(cube) | 新项目(WxFbsir) | 变化说明 |
|------|-------------|-----------------|----------|
| **后端框架** | Spring Boot | Spring Boot | ✅ 保持一致 |
| **前端框架** | Vue 3 + Element Plus | Vue 3 + Element Plus | ✅ 保持一致 |
| **WebSocket** | 内联实现 | 通用工具类 | 🔄 重构优化 |
| **AI模型** | 10+ AI平台 | DeepSeek + 元宝 | ⚡ 简化配置 |
| **数据库表** | 复杂字段 | 简化字段 | ⚡ 优化结构 |
| **消息格式** | 旧格式 | 标准化格式 | 🔄 格式统一 |

---

## 🔄 完整数据流验证

### 1. 前端发送消息格式 ✅

**标准消息格式**：
```javascript
{
  "type": "AI_DEEPSEEK_QUERY",
  "engineId": "engine-001",  // 动态从用户配置获取
  "payload": {
    "query": "什么是人工智能？",
    "enableDeepThinking": false,
    "enableWebSearch": false,
    "sessionId": "uuid-generated-frontend",  // ✅ 前端生成
    "aiType": "deepseek"                    // ✅ AI类型标识
  }
}
```

**关键验证点**：
- ✅ **sessionId传递**: 前端生成UUID作为业务会话ID
- ✅ **aiType传递**: 标识AI类型（deepseek/yuanbao）
- ✅ **engineId动态**: 从用户个人配置获取主机ID

### 2. Engine处理流程 ✅

Engine接收到消息后：
1. 解析`payload`中的`sessionId`和`aiType`
2. 执行AI咨询逻辑
3. 返回`TASK_RESULT`消息，包含原始的`sessionId`和`aiType`

### 3. Admin存储逻辑 ✅

当前实现的存储触发点：
```javascript
// 前端收到TASK_RESULT后立即保存
if (messageType === 'TASK_RESULT' && resultData.answer) {
  // ... 处理结果展示
  
  // 🔥 关键：自动保存到数据库
  saveChatToDatabase(resultData, payload.sessionId || sessionId, payload.aiType || 'deepseek')
}
```

### 4. 数据库存储验证 ✅

**存储字段映射**：
```sql
-- wc_chat_history 表结构（简化版）
CREATE TABLE `wc_chat_history` (
  `id` varchar(255) NOT NULL COMMENT '主键ID（使用sessionId）',
  `user_id` varchar(10) COMMENT '用户ID',
  `userPrompt` longtext COMMENT '用户指令',
  `data` longtext COMMENT '完整JSON数据',
  `chat_id` varchar(36) COMMENT '内部chatID',
  `deepseek_chat_id` varchar(100) COMMENT 'DeepSeek会话ID',
  `ybds_chat_id` varchar(100) COMMENT '元宝会话ID（统一）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);
```

**存储数据示例**：
```javascript
{
  id: "session-uuid-123",           // sessionId作为主键
  userId: "103",                    // 当前用户ID
  userPrompt: "什么是人工智能？",      // 用户提问
  chatId: "session-uuid-123",       // 内部会话ID
  deepseekChatId: "deepseek-chat-456", // DeepSeek平台会话ID
  data: "{\"results\":[...],\"screenshots\":[...],\"answer\":\"...\", \"shareUrl\":\"...\"}" // 完整结果JSON
}
```

---

## 🆚 新旧项目详细对比

### 数据库表结构对比

#### 旧项目表结构
```sql
-- 旧项目：复杂的多AI字段
CREATE TABLE `wc_chat_history` (
  `tone_chat_id` varchar(100) COMMENT '元宝T1会话ID',
  `ybds_chat_id` varchar(100) COMMENT '元宝DS会话ID',
  `db_chat_id` varchar(100) COMMENT '豆包会话ID',
  `ty_chat_id` varchar(100) COMMENT '通义会话ID',
  `deepseek_chat_id` varchar(100) COMMENT 'DeepSeek会话ID',
  `max_chat_id` varchar(100) COMMENT 'MiniMax会话ID',
  `metaso_chat_id` varchar(100) COMMENT '秘塔会话ID',
  `kimi_chat_id` varchar(100) COMMENT 'KiMi会话ID',
  `baidu_chat_id` varchar(100) COMMENT '百度会话ID',
  `zhzd_chat_id` varchar(100) COMMENT '知乎直答会话ID'
);
```

#### 新项目表结构（简化版）
```sql
-- 新项目：精简的双AI字段
CREATE TABLE `wc_chat_history` (
  `deepseek_chat_id` varchar(100) COMMENT 'DeepSeek会话ID',
  `ybds_chat_id` varchar(100) COMMENT '元宝会话ID（统一）'
  -- 移除了8个不使用的AI字段
);
```

### 前端UI布局对比

#### 旧项目布局特点
- 支持10+个AI平台选择
- 复杂的选项配置界面
- 多AI并行执行状态展示

#### 新项目布局特点（保持兼容）
- 🎯 **精简AI选择**：只显示DeepSeek和元宝
- 🔄 **保持UI结构**：布局、样式、交互逻辑完全一致
- ⚡ **优化性能**：减少不必要的状态管理

### WebSocket实现对比

#### 旧项目实现
```javascript
// 旧项目：内联WebSocket连接
const wsUrl = `ws://${window.location.host}/ws/client?clientType=web&token=${token}`
websocket = new WebSocket(wsUrl)
```

#### 新项目实现
```javascript
// 新项目：通用工具类
import { buildWebSocketUrl } from '@/utils/websocket'

const wsUrl = buildWebSocketUrl({
  path: '/ws/client',
  token: token,
  clientType: 'web'
})
websocket = new WebSocket(wsUrl)
```

**优势**：
- ✅ 支持开发/生产环境自动适配
- ✅ 支持HTTP/HTTPS自动转换WS/WSS
- ✅ 统一的连接管理逻辑

---

## 🧪 测试验证流程

### DeepSeek调用测试

#### 1. 准备工作
```bash
# 1. 执行数据库迁移
mysql -u root -p < /path/to/sql/aigc_migration.sql

# 2. 启动Engine服务
cd WxFbsir-engine
mvn spring-boot:run

# 3. 启动Admin服务
cd WxFbsir-admin  
mvn spring-boot:run

# 4. 启动前端服务
cd WxFbsir-ui
npm run dev
```

#### 2. 配置用户主机ID
1. 登录系统：http://localhost:8080
2. 用户名：`admin` 密码：`123@123.com`
3. 进入 **个人中心** → **主机配置**
4. 设置主机ID：`engine-001`
5. 点击"检测连接"验证Engine在线状态

#### 3. 测试AI咨询
1. 进入 **内容管理** → **AI助手**
2. 输入测试问题："什么是人工智能？"
3. 点击"发送"

#### 4. 验证数据存储
```sql
-- 检查聊天记录是否正确存储
SELECT 
  id,
  user_id,
  userPrompt,
  deepseek_chat_id,
  create_time,
  JSON_EXTRACT(data, '$.answer') as ai_answer
FROM wc_chat_history 
WHERE user_id = '103'
ORDER BY create_time DESC
LIMIT 5;
```

#### 5. 验证历史记录加载
1. 点击右上角"历史记录"按钮
2. 验证是否显示刚才的对话记录
3. 点击历史记录项验证是否能正确加载

---

## ⚠️ 注意事项与最佳实践

### 关键配置项

#### 1. 用户主机ID配置
- **必须配置**：每个用户必须配置`host_id`才能使用AI功能
- **格式规范**：建议格式`engine-xxx`，如`engine-001`
- **唯一性**：多用户可以共享同一个Engine节点

#### 2. Engine服务配置
- **端口配置**：确保Engine端口（通常8081）可访问
- **WebSocket路径**：`/ws/engine`
- **心跳检测**：Engine需要定期向Admin报告在线状态

#### 3. 数据库索引优化
```sql
-- 关键索引（已在迁移脚本中包含）
CREATE INDEX idx_user_create_time ON wc_chat_history(user_id, create_time);
CREATE INDEX idx_deepseek ON wc_chat_history(deepseek_chat_id);  
CREATE INDEX idx_yuanbao ON wc_chat_history(ybds_chat_id);
```

### 常见问题排查

#### 1. WebSocket连接失败
```bash
# 检查端口占用
netstat -an | grep :8080

# 检查防火墙设置
sudo ufw status

# 查看Admin日志
tail -f logs/sys-info.log
```

#### 2. AI咨询无响应
```sql
-- 检查用户主机ID配置
SELECT user_id, user_name, host_id FROM sys_user WHERE user_id = 103;

-- 检查Engine在线状态
SELECT * FROM ws_connection_log WHERE connection_type = 'ENGINE' ORDER BY create_time DESC LIMIT 10;
```

#### 3. 数据存储失败
```sql
-- 检查表结构
DESCRIBE wc_chat_history;

-- 检查最近的错误日志
SELECT * FROM sys_oper_log WHERE business_type = 'INSERT' AND status = 1 ORDER BY oper_time DESC LIMIT 10;
```

---

## 🚀 部署清单

### 必需文件清单

#### 后端文件
- ✅ `AigcMapper.xml` - 数据库映射文件
- ✅ `AiResultHandler.java` - AI结果处理器
- ✅ `AigcController.java` - API控制器
- ✅ `IAigcService.java` - 服务接口
- ✅ `AigcServiceImpl.java` - 服务实现

#### 前端文件
- ✅ `index.vue` - AI助手主页面
- ✅ `assistant.js` - API调用封装
- ✅ `websocket.js` - WebSocket工具类
- ✅ `hostConfig.vue` - 主机配置页面

#### 数据库文件
- ✅ `aigc_migration.sql` - 完整迁移脚本

### 部署步骤
1. **数据库迁移**: 执行`aigc_migration.sql`
2. **后端部署**: 更新Admin和Engine服务
3. **前端部署**: 更新UI资源文件
4. **配置验证**: 确保用户主机ID正确配置
5. **功能测试**: 执行完整的测试流程

---

## 📞 技术支持

如遇到问题，请按以下顺序排查：
1. 检查数据库迁移是否成功
2. 验证Engine服务是否在线
3. 确认用户主机ID配置正确
4. 查看相关日志文件

**日志文件位置**：
- Admin日志: `logs/sys-info.log`
- Engine日志: `logs/engine.log`
- WebSocket日志: 浏览器控制台

---

*更新时间: 2026-01-08*
*版本: v2.0*
