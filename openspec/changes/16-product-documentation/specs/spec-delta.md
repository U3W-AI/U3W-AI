# OpenSpec #16 Spec Delta

**Change ID**: `16-product-documentation`
**类型**: 文档工程（不修改代码规范，仅新增文档产出）

---

## 概述

本文档说明 #16 实施后，对常驻规范的变更。

**重要说明**：#16 是文档工程，不修改任何功能代码或接口规范。产出为 `docs/` 目录下的 Markdown 文档，对现有 `specs/` 目录下的规范文件无任何结构性变更。

---

## 变更内容

### 新增文件

| 文件路径 | 说明 |
|---------|------|
| `docs/SDK-REFERENCE.md` | 主产品文档，覆盖 11 章 |
| `docs/QUICK-START.md` | 快速开始指南 |
| `docs/ERROR-CODES.md` | 错误码参考 |

### 常驻规范更新

| 规范文件 | 变更类型 | 说明 |
|---------|---------|------|
| `specs/platform-scene-pack-ops/spec.md` | 无变更 | #16 是文档工程，不修改规范文件；文档引用 v1.8 |
| `specs/fbs-rights-foundation/spec.md` | 无变更 | 当前 v1.0，仅文档引用 |
| `specs/skill-api-security/spec.md` | 无变更 | 无显式版本号，仅文档引用 |

> **修正**：经代码验证，常驻 spec 中仅 `platform-scene-pack-ops` 为 v1.8，`fbs-rights-foundation` 为 v1.0，`skill-api-security` 无显式版本号。不存在 `FBS_*` 前缀的错误码。

### 文档版本对齐

| 维度 | 版本 |
|------|------|
| 主文档 | v1.0 |
| spec 版本对齐 | v1.8（与 Skill v2.1.2 对齐） |
| Skill 版本对齐 | v2.1.2 |

---

## API 域名规范（新增）

**生产环境域名**：`https://api.U3W.com`

所有文档示例必须使用此域名，不得使用 `http://localhost:8080`（开发环境域名仅在快速开始在"本地调试"小节作为对比说明）。

---

## 错误码表（新增至文档）

### Skill API 安全相关（6 个，来源 #15 安全加固）

| HTTP Status | 错误码 | 说明 |
|-------------|--------|------|
| 401 | `SKILL_API_KEY_INVALID` | Key 缺失/不存在 |
| 401 | `SKILL_API_SIGNATURE_INVALID` | 签名缺失/不匹配 |
| 401 | `SKILL_API_TIMESTAMP_MISSING` | 时间戳缺失 |
| 401 | `SKILL_API_TIMESTAMP_EXPIRED` | 时间戳过期 |
| 403 | `SKILL_API_KEY_DISABLED` | Key 已禁用 |
| 429 | `SKILL_API_RATE_LIMITED` | 速率超限 |

> 以上 6 个错误码已验证与 `FbsApiKeyAuthService.java` 代码一致，全部以硬编码字符串形式使用。

### 业务逻辑错误（中文描述，非标准错误码格式）

Skill API 端点的业务失败通过 `AjaxResult.error(msg)` 返回，msg 为中文描述字符串（如"场景包不存在"、"积分余额不足"等），无独立错误码标识。

**已知特例**（通过 `ServiceException(code, message)` 抛出，使用 code 值区分）：

| code | 错误标识 | 说明 | 来源 |
|------|---------|------|------|
| 200 | `ALREADY_ACTIVATED` | 授权码已激活（幂等返回） | #4 |
| - | `PARTIAL_WRITE_FAILED` | 企微多分片写入部分失败 | #7 |

> 上述业务错误 HTTP 响应状态码仍为 200，通过 `AjaxResult.code` 字段区分。文档需明确区分"HTTP 层错误"和"业务层错误"。

### AjaxResult 标准响应结构

```json
// 成功
{ "code": 200, "msg": "操作成功", "data": {...} }

// 业务失败
{ "code": 500, "msg": "场景包不存在" }

// 认证失败（FbsApiKeyAuthFilter 返回，非 AjaxResult 格式）
{ "code": 401, "msg": "API Key 无效" }
```

### 其他错误码体系（非 Skill API 范围，文档仅需提及）

| 体系 | 错误码格式 | 数量 | 说明 |
|------|-----------|------|------|
| WebSocket | `E1xxx`~`E9xxx` 枚举 | 14 | `WebSocketErrorCode.java` |
| Engine 全局异常 | 字符串如 `INVALID_PARAMETER` | 6 | `GlobalExceptionHandler.java` |
| WecomSync 写入 | 字符串如 `INVALID_REQUEST` | 6 | `WecomWriteServiceImpl.java` |
| WecomSync 读取 | `INVALID_SHEET`/`CLI_NOT_FOUND` | 2 | `WecomSyncServiceImpl.java` |
| 积分预检 | `POINTS_ERROR` | 1 | `PointsPrecheckService.java` |

> **注意**：代码中**不存在** `FBS_` 前缀的错误码。`FBS_` 前缀仅出现在 API Key 格式（`fbs_` 小写）、表名、Header 名等处。

---

## 归档后操作

#16 归档后，`docs/` 目录应移至项目根目录或指定文档站点目录，具体部署方案在归档时确认。
