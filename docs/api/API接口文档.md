# 福帮手 API 接口文档

> **更新日期**：2026-07-10（源码校准）。本文根据当前 Controller 映射整理；实际参数、权限注解和响应体以对应控制器及运行中的 SpringDoc 为准。

## 基础约定

- 后端默认地址：`http://localhost:8080`
- OpenAPI：`/v3/api-docs`；Swagger UI：`/swagger-ui.html`
- 除登录、注册、验证码、公开静态资源和 WebSocket 握手外，HTTP 接口使用 `Authorization: Bearer {token}`。
- `/ws/engine` 与 `/ws/client` 仅放行 WebSocket 握手；`/ws/**` 下的 HTTP 管理和执行接口仍需要 JWT。
- 所有路由均不带前端开发代理前缀 `/dev-api`；该前缀仅由 Vite 开发代理使用。

## 系统与通用模块

系统管理接口以当前控制器为准，主要根路径为：

| 模块 | 根路径 |
|---|---|
| 登录与用户会话 | `/login`、`/logout`、`/getInfo`、`/getRouters`、`/register` |
| 用户、角色、菜单、部门、岗位、字典、参数、公告 | `/system/**` |
| 登录日志、操作日志、在线用户、缓存、服务器监控 | `/monitor/**` |
| 上传与下载 | `/common/**` |
| 验证码 | `/captchaImage` |

## 业务接口（当前源码）

### AIGC

| 方法 | 路径 |
|---|---|
| `POST` | `/aigc/request` |
| `GET` | `/aigc/ai/list`、`/aigc/user/host-status` |
| `GET` | `/aigc/chat/history`、`/aigc/chat/latest` |
| `POST` | `/aigc/chat/save` |
| `GET` | `/aigc/drafts`、`/aigc/getDraftContent`、`/aigc/getPlayWrighDrafts` |
| `POST` | `/aigc/draft/save` |
| `DELETE` | `/aigc/draft/{draftId}` |
| `POST` | `/aigc/output/generate`、`/aigc/output/save`、`/aigc/output/pushWebhook` |
| `GET` | `/aigc/output/exportJson/{sessionId}`、`/aigc/output/exportMarkdown/{sessionId}` |

### 认证易（证书）

| 资源 | 当前根路径 |
|---|---|
| 证书模板 | `/business/certificateTemplate`（`GET /list`、`GET /{templateId}`、`POST`、`PUT`、`DELETE /{templateIds}`、`POST /export`） |
| 证书申请 | `/business/certificateApplication`（`GET /list`、`GET /my`、`GET /{applicationId}`、`POST`、`PUT`、`DELETE /{applicationIds}`、`POST /submit`、`POST /receive/{applicationId}`、`POST /export`） |
| 申请审核 | `/business/applicationReview`（以控制器中的 `/list`、`/info/{reviewId}`、`/reviewApplication` 等映射为准） |

### 日更助手与元器配置

| 资源 | 当前根路径 |
|---|---|
| 日更文章 | `/system/daily-article`（`GET /list`、`GET /myList`、`GET /{id}`、`POST`、`PUT`、`DELETE /{ids}`、`POST /createAndOptimize`、`POST /layoutArticle`、`POST /publishToWechat`、`POST /export`、`GET /status/{id}`） |
| 元器智能体配置 | `/system/yuanqi-config`（`GET /list`、`GET /myConfig`、`GET /{id}`、`POST`、`PUT`、`DELETE /{ids}`、`POST /export`） |

### 文档解析、Gitee 与积分

| 资源 | 当前根路径 |
|---|---|
| 文档解析 | `/system/document-parse`（`GET /list`、`GET /myList`、`GET /status/{id}`、`GET /{id}`、`POST /uploadAndParse`、`POST /updateParsedContent`、`POST`、`PUT`、`DELETE /{ids}`、`POST /export`） |
| Gitee 用户资料 | `/business/gitee`（`GET /status`、`/authorize`、`/profile`、`/repos`、`/issues`、`/notifications`，`POST /unbind`） |
| Gitee 分析与运营 | `/business/gitee/analysis`（`POST /report`、`POST /reevaluate`）与 `/business/gitee/admin` |
| 积分 | `/points`（`GET /getUserPoints`、`/getPointsSummary`、`/getPointsRecord`、`/getPointTaskList`，`POST /changePoints`） |
| 积分规则与粉丝 | `/points/rule` 与 `/points/fans` |

### 公众号、机器人和系统提示词

| 资源 | 当前根路径 |
|---|---|
| 公众号账号 | `/business/office-account`（`GET /list`、`GET /my-config`、`GET /server-ip`、`GET /{id}`、`POST`、`PUT`、`DELETE /{ids}`、`POST /verify`、`POST /publish/{articleId}/{contentType}`） |
| 公众号发布记录 | `/business/publish-record`（`GET /list`、`GET /{id}`、`DELETE /{ids}`、`POST /export`） |
| 企业微信机器人 | `/business/message`（`GET /list`、`GET /get`、`POST /insert`、`POST /update`、`POST /delete`、`POST /send`、`POST /updateprompt`） |
| 系统提示词 | `/business/prompt`（`GET /list`、`GET /system`、`GET /get`、`POST /insert`、`POST /update`、`POST /delete`） |

### 策略管理

| 方法 | 路径 |
|---|---|
| `GET` | `/system/strategy/list`、`/system/strategy/name/{strategyName}`、`/system/strategy/{id}` |
| `POST` | `/system/strategy` |
| `PUT` | `/system/strategy` |
| `DELETE` | `/system/strategy/{ids}` |

策略仅存储和查询参数模板。当前节点编辑页面在发送 Engine 请求前完成参数合并；HTTP 或 WebSocket 转发层不会按 `payload.strategy` 二次改写请求。

## 主机纳管与 Engine

### 主机白名单

| 方法 | 路径 |
|---|---|
| `GET` | `/business/host/whitelist/list`、`/business/host/whitelist/{id}`、`/business/host/whitelist/health-check/{id}`、`/business/host/whitelist/status` |
| `POST` | `/business/host/whitelist` |
| `PUT` | `/business/host/whitelist` |
| `DELETE` | `/business/host/whitelist/{ids}` |

`engine` 类型的白名单记录必须使用 `hostType=engine` 才能注册 Engine；`openclaw` 仅用于健康检查与纳管。引擎主机的 `onlineStatus` 由运行时会话决定。

### Engine HTTP 管理接口

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/ws/engine/list` | 获取已注册 Engine 列表 |
| `POST` | `/ws/engine/request` | 同步转发一次 Engine 能力调用 |
| `GET` | `/ws/admin/stats`、`/ws/admin/engines`、`/ws/admin/engines/{engineId}`、`/ws/admin/config` | 运行状态和配置 |
| `POST` | `/ws/admin/engines/{engineId}/task`、`/ws/admin/broadcast` | 下发任务或广播 |
| `DELETE` | `/ws/admin/engines/{engineId}` | 强制断开 Engine（需要对应权限） |

### WebSocket 协议入口

| 端点 | 握手认证 | 身份与路由 |
|---|---|---|
| `/ws/client?clientType=web&token={token}` | JWT；服务端也支持 `Authorization` 与兼容的 `key` | `userId` 从令牌解析，服务端生成 `clientId` |
| `/ws/engine` | 白名单、IP 规则和连接限流 | Engine 在 `ENGINE_REGISTER.engineId` 提交主机 ID；白名单需为 `hostType=engine` |

请求进入 Admin 后会生成 `requestId`，并携带 `sourceType=WEBSOCKET` 或 `HTTP`。Engine 返回结果会依据该来源回到正确通道。

## 变更与校验原则

- 以 `WxFbsir-admin`、`WxFbsir-business` 控制器映射为接口真相。
- 新增或修改路由时，同步更新本文、对应功能说明与自动化校验脚本。
- 不再使用已移除的 `/business/aigc/**`、`/business/dailyassistant/**`、`/business/documentparse/**`、`/business/officialaccount/**` 等旧路径。
