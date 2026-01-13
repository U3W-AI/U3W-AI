# 功能说明文档目录

系统功能模块说明文档索引。

---

## 📚 文档列表

### [日更助手功能说明](./日更助手功能说明.md)
基于腾讯元器智能体的文章自动生成系统，支持多模型并行生成、智能优化、智能排版。

**代码位置：**
- 后端：`WxFbsir-business/src/main/java/com/wx/fbsir/business/dailyassistant/`
- 前端：`WxFbsir-ui/src/views/business/content/dailyassistant/`

---

### [公众号草稿上传功能说明](./公众号草稿上传功能说明.md)
文章自动投递到微信公众号草稿箱，包含配置管理、图片上传、发布记录等功能。

**代码位置：**
- 后端：`WxFbsir-business/src/main/java/com/wx/fbsir/business/officialaccount/`
- 前端：`WxFbsir-ui/src/views/system/user/profile/officeAccountConfig.vue`
- 工具：`WxFbsir-common/src/main/java/com/wx/fbsir/common/utils/AesEncryptUtils.java`

---

### [积分系统使用指南](./积分系统使用指南.md)
用户积分管理系统，支持积分规则配置、积分实现和用户积分管理。

**代码位置：**
- 后端：`WxFbsir-business/src/main/java/com/wx/fbsir/business/point/`
- 前端：`WxFbsir-ui/src/views/system/point/`

---

### [AES加密配置说明](./AES加密配置说明.md)
AES-256-GCM加密算法配置和使用说明。

**代码位置：**
- 工具类：`WxFbsir-common/src/main/java/com/wx/fbsir/common/utils/AesEncryptUtils.java`
- 配置：`application.yml`

---

### [Gitee用户开源相关能力分析](./gitee用户开源相关能力分析.md)
Gitee OAuth授权、能力评测与运营统计的功能说明。

**代码位置：**
- 后端：`WxFbsir-business/src/main/java/com/wx/fbsir/business/gitee/`
- 前端：`WxFbsir-ui/src/views/business/gitee/`
- 接口：`WxFbsir-ui/src/api/business/gitee/`

---

### [Playwright框架完整指南](./Playwright框架完整指南.md)
Engine 端浏览器自动化能力与最佳实践指南。

**代码位置：**
- Engine：`WxFbsir-engine/src/main/java/com/wx/fbsir/engine/playwright/`
- 能力示例：`WxFbsir-engine/src/main/java/com/wx/fbsir/engine/controller/`

---

### [WebSocket通信完整指南](./WebSocket通信完整指南.md)
Admin 与 Engine 的 WebSocket 通信协议与实现说明。

**代码位置：**
- Engine：`WxFbsir-engine/src/main/java/com/wx/fbsir/engine/websocket/`
- 主服务：`WxFbsir-business/src/main/java/com/wx/fbsir/business/websocket/`

---

### [AIGC框架完整功能说明](./AIGC框架完整功能说明.md)
多AI模型集成框架，支持 DeepSeek、通义千问、元宝、豆包等多个 AI 模型的并行调用与上下文管理。

**代码位置：**
- Engine：`WxFbsir-engine/src/main/java/com/wx/fbsir/engine/controller/ai/`
- 业务层：`WxFbsir-business/src/main/java/com/wx/fbsir/business/aigc/`
- 前端：`WxFbsir-ui/src/views/business/content/aigc/`
- 数据表：`wc_chat_history`、`wc_playwright_draft`

---

### [文档解析助手功能说明](./文档解析助手功能说明.md)
基于腾讯元器智能体的文档智能解析系统，支持多格式文档上传、智能提取与内容生成。

**代码位置：**
- 后端：`WxFbsir-business/src/main/java/com/wx/fbsir/business/documentparse/`
- 前端：`WxFbsir-ui/src/views/business/content/documentparse/`
- 数据表：`document_parse`

---

**最后更新：** 2026-01-13
