# 功能说明文档目录
> 更新日期：2026-07-11

> **更新日期**：2026-07-11

系统功能模块说明文档索引。

---

## 📚 文档列表

### [日更助手功能说明](business功能/日更助手功能说明.md)
基于腾讯元器智能体的文章自动生成系统，支持多模型并行生成、智能优化、智能排版。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/dailyassistant/`
- 前端：`FBSir-ui/src/views/business/content/dailyassistant/`

---

### [公众号草稿上传功能说明](business功能/公众号草稿上传功能说明.md)
文章自动投递到微信公众号草稿箱，包含配置管理、图片上传、发布记录等功能。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/officialaccount/`
- 前端：`FBSir-ui/src/views/system/user/profile/officeAccountConfig.vue`
- 工具：`FBSir-common/src/main/java/com/wx/fbsir/common/utils/AesEncryptUtils.java`

---

### [积分系统使用指南](business功能/积分系统使用指南.md)
用户积分管理系统，支持积分规则配置、积分实现和用户积分管理。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/point/`
- 前端：`FBSir-ui/src/views/business/points/`

---

### [AES加密配置说明](./AES加密配置说明.md)
AES-256-GCM加密算法配置和使用说明。

**代码位置：**
- 工具类：`FBSir-common/src/main/java/com/wx/fbsir/common/utils/AesEncryptUtils.java`
- 配置：`application.yml`

---

### [Gitee用户开源相关能力分析](business功能/gitee用户开源相关能力分析.md)
Gitee OAuth授权、能力评测与运营统计的功能说明。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/gitee/`
- 前端：`FBSir-ui/src/views/business/gitee/`
- 接口：`FBSir-ui/src/api/business/gitee/`

---

### [Playwright框架完整指南](engine/Playwright框架完整指南.md)
Engine 端浏览器自动化能力与最佳实践指南。

**代码位置：**
- Engine：`FBSir-engine/src/main/java/com/wx/fbsir/engine/playwright/`
- 能力示例：`FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/`

---

### [WebSocket通信完整指南](engine/WebSocket通信完整指南.md)
Admin 与 Engine 的 WebSocket 通信协议与实现说明。

**代码位置：**
- Engine：`FBSir-engine/src/main/java/com/wx/fbsir/engine/websocket/`
- 主服务：`FBSir-business/src/main/java/com/wx/fbsir/business/websocket/`

---

### [AIGC框架完整功能说明](engine/engine功能/AIGC框架完整功能说明.md)
多AI模型集成框架，支持 DeepSeek、通义千问、元宝、豆包等多个 AI 模型的并行调用与上下文管理。

**代码位置：**
- Engine：`FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/ai/`
- 业务层：`FBSir-business/src/main/java/com/wx/fbsir/business/aigc/`
- 前端：`FBSir-ui/src/views/business/content/aigc/`
- 数据表：`wc_chat_history`、`wc_playwright_draft`

---

### [文档解析助手功能说明](business功能/文档解析助手功能说明.md)
基于腾讯元器智能体的文档智能解析系统，支持多格式文档上传、智能提取与内容生成。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/documentparse/`
- 前端：`FBSir-ui/src/views/business/content/documentparse/`
- 数据表：`document_parse`

---

### [企业微信机器人配置说明](engine/engine功能/企业微信机器人配置说明.md)
企业微信机器人集成指南，包含配置步骤和参数说明。

**代码位置：**
- 后端：`FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/JiQiRen/`
- 工具类：`FBSir-engine/src/main/java/com/wx/fbsir/engine/utils/JiQiRen/`

---

### [知识库管理功能说明](engine/engine功能/知识库管理功能说明.md)
知识库管理功能说明，包含知识库的创建、同步和管理功能。

**代码位置：**
- 后端：`FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/yuanqi/`
- 工具类：`FBSir-engine/src/main/java/com/wx/fbsir/engine/utils/yuanqi/`

---

### [面试助手功能说明](business功能/面试助手功能说明.md)
面试助手功能说明，包含面试题生成、面试评估等功能。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/interviewbot/`
- 前端：当前源码未提供独立的面试助手页面；后端能力位于上述 `interviewbot` 模块。

---

### [认证易功能说明](business功能/认证易功能说明.md)
完整的证书申请和审核系统，覆盖用户侧和管理员不同角色，集成认证申请、证书模板管理、审核工作流标准化、积分系统适配等模块。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/certificate/`
- 前端：`FBSir-ui/src/views/business/certificate/`

---

### [节点编辑管理和策略管理功能说明](engine/engine功能/节点编辑管理和策略管理功能说明.md)
节点编辑管理和策略管理功能说明，包含节点的创建、编辑、删除和策略配置。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/nodeeditwithstrategy/`
- 前端：`FBSir-ui/src/views/business/content/NodeEditWithStrategy/`

---

### [engine知识库功能技术接口说明](engine/engine功能/engine知识库功能技术接口说明.md)
Engine端知识库功能的技术接口说明文档。

**代码位置：**
- 后端：`FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/yuanqi/`
- 工具类：`FBSir-engine/src/main/java/com/wx/fbsir/engine/utils/yuanqi/`

---

### [企业微信机器人消息功能说明](business功能/企业微信机器人消息功能说明.md)
企业微信机器人消息推送功能，支持向微信群发送模板消息和文本消息。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/airobotmessage/`
- 前端：`FBSir-ui/src/views/business/airobotmessage/`
- 数据表：`wc_webhook_url`

---

### [系统提示词管理功能说明](business功能/系统提示词管理功能说明.md)
系统提示词管理功能，支持工作流提示词的增删改查，用于AI工作流的系统提示词配置。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/systemprompt/`
- 前端：`FBSir-ui/src/views/business/systemPrompt/`
- 数据表：`system_prompts`

---

### [OpenClaw主机纳管功能说明](business功能/OpenClaw主机纳管功能说明.md)
OpenClaw主机纳管功能，支持主机的登记、状态监控、管控和健康检查。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/websocket/`
- 前端：与Engine主机管理共用界面

---

### [OpenClaw主机纳管部署指南](engine/OpenClaw主机纳管部署指南.md)
OpenClaw主机纳管功能的部署和配置指南。

**代码位置：**
- 后端：`FBSir-business/src/main/java/com/wx/fbsir/business/websocket/`
- 健康检查：`FBSir-business/src/main/java/com/wx/fbsir/business/websocket/task/OpenClawHealthChecker.java`

---

**最后更新：** 2026-07-11
