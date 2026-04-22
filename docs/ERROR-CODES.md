# 错误码参考

---

## 1. 认证与签名错误（Skill API，HTTP 4xx）

| HTTP Status | 错误码 | 说明 | 排查建议 |
|-------------|--------|------|----------|
| 401 | `SKILL_API_KEY_INVALID` | API Key 不存在或缺失 | 确认控制台生成的 Key 正确，格式为 `fbs_` + 64 位 hex |
| 401 | `SKILL_API_SIGNATURE_INVALID` | 签名不匹配（请求被篡改或密钥错误） | 检查签名算法：`HMAC-SHA256(apiKey, timestamp + '\n' + body)`，输出小写 hex |
| 401 | `SKILL_API_TIMESTAMP_MISSING` | 缺少时间戳 Header | 确认请求携带 `X-FBS-Timestamp`，值为 Unix 毫秒时间戳 |
| 401 | `SKILL_API_TIMESTAMP_EXPIRED` | 时间戳超过 5 分钟（防重放） | 检查客户端时间与服务器时间差异，确保 ≤ 5 分钟 |
| 403 | `SKILL_API_KEY_DISABLED` | API Key 已被禁用 | 在控制台重新启用或创建新 Key |
| 429 | `SKILL_API_RATE_LIMITED` | 请求过于频繁 | 降低请求频率，或联系运营扩展配额 |

> **返回格式**：`{"code": 401, "msg": "API Key 无效"}`
> 注意：`errorCode` 字段不会被写入响应体，实际返回的是 `{code: httpStatusInt, msg: "中文文本"}`

---

## 2. 业务逻辑错误（Skill API 端点级合同）

业务层错误均通过 `AjaxResult` 返回，**响应判断逻辑：优先读响应体 `body.code`**：

- `body.code = 200`：成功（含 `/rights/check` 的 `data.pass=false` 业务失败）
- `body.code ≠ 200`（如 403/500/409）：失败，msg 含具体原因
- 认证过滤器拦截时（API Key/签名/时间戳问题）：**真实 HTTP Status 4xx** + body `{code: httpStatus, msg: "..."}`

> **注意**：控制器返回 `AjaxResult.error(msg)` 时，body.code 通常为 500，但**真实 HTTP Status 仍为 200**（Spring 默认）。`body.code` 才是业务层判断依据，不是 HTTP Status。

### 2.1 POST /rights/check

| body.code | data 字段 | msg 示例 | 说明 | 排查 |
|-----------|-----------|----------|------|------|
| 200 | `pass: true` | — | 权益校验通过 | — |
| 200 | `pass: false, failReason: "积分余额不足"` | — | 积分不足，data.failReason 含原因 | 充值积分 |
| 200 | `pass: false, failReason: "场景包不存在"` | — | 场景包编码错误 | 确认 packCode |
| 200 | `pass: false, failReason: "授权码无效"` | — | 授权码不存在/已过期/已用尽 | 检查 authCode |
| 500 | — | `"参数不能为空"` | 缺少 userId 或 packCode，body.code=500，真实 HTTP 仍为 200 | 检查请求体 |

### 2.2 POST /usage/consume

| body.code | data 字段 | msg 示例 | 说明 | 排查 |
|-----------|-----------|----------|------|------|
| 200 | `success: true, remainPoints, usageRecordId, failReason: null` | — | 消费成功 | — |
| 500 | — | `"积分余额不足"` | 余额不足，body.code=500，真实 HTTP 仍为 200 | 充值积分 |
| 500 | — | `"场景包不存在"` | 场景包编码错误，body.code=500，真实 HTTP 仍为 200 | 确认 packCode |
| 403 | — | `"无法识别用户（API Key 未绑定且未传 userId）"` | body.code=403，真实 HTTP 仍为 200 | 传入 userId 或换用已绑定 Key |
| 500 | — | `"参数不能为空"` | 缺少必填参数，body.code=500，真实 HTTP 仍为 200 | 检查请求体 |

### 2.3 POST /usage/start

| body.code | data 字段 | msg 示例 | 说明 | 排查 |
|-----------|-----------|----------|------|------|
| 200 | `usageRecordId, status: 0` | — | 创建成功，status=0 表示 IN_PROGRESS | — |
| 200 | `usageRecordId, status: 0`（同前） | — | 幂等：usageRecordId 已存在且 IN_PROGRESS，直接返回 | 正常，可继续后续流程 |
| 409 | — | `"使用记录已成功结束"` | body.code=409，真实 HTTP 仍为 200，换用新 usageRecordId | 换用新 usageRecordId |
| 409 | — | `"使用记录已失败结束"` | body.code=409，真实 HTTP 仍为 200 | 换用新 usageRecordId |
| 500 | — | `"场景包不存在: xxx"` | body.code=500，真实 HTTP 仍为 200 | 确认 packCode |
| 500 | — | `"参数不能为空"` | 缺少必填参数，body.code=500，真实 HTTP 仍为 200 | 检查请求体 |

### 2.4 PUT /usage/end/{usageRecordId}

| body.code | msg 示例 | 说明 | 排查 |
|-----------|----------|------|------|
| 200 | `"更新成功"` | 状态更新成功 | — |
| 409 | `"使用记录已成功结束"` | body.code=409，真实 HTTP 仍为 200，幂等：已 SUCCESS，不可重复 end | 正常 |
| 409 | `"使用记录已失败结束"` | body.code=409，真实 HTTP 仍为 200，幂等：已 FAILED，不可重复 end | 正常 |
| 404 | `"使用记录不存在"` | body.code=404，真实 HTTP 仍为 200 | 确认 usageRecordId |
| 500 | `"status 必须为 1（成功）或 2（失败）"` | body.code=500，真实 HTTP 仍为 200 | 传 1（成功）或 2（失败） |

### 2.5 POST /points/earn

| body.code | data 字段 | msg 示例 | 说明 | 排查 |
|-----------|-----------|----------|------|------|
| 200 | `success: true, pointsAmount, remainPoints, usageRecordId, failReason: null` | — | 积分增加成功 | — |
| 500 | — | `"source 不能为空"` / `"amount 必须为正整数"` / `"usageRecordId 不能为空"` | 参数校验失败，body.code=500，真实 HTTP 仍为 200 | 检查请求体 |
| 403 | — | `"无法识别用户（API Key 未绑定且未传 userId）"` | body.code=403，真实 HTTP 仍为 200 | 传入 userId |
| 500 | — | 来自 `changePoints` 的错误（积分规则未配置、限频等） | 行为无对应积分规则，body.code=500，真实 HTTP 仍为 200 | 确认 source 对应规则存在 |

### 2.6 POST /scene-pack/query

| body.code | msg 示例 | 说明 | 排查 |
|-----------|----------|------|------|
| 200 | — | 查询成功，data 含 scenePack 信息 | — |
| 500 | `"场景包不存在: xxx"` | body.code=500，真实 HTTP 仍为 200 | 确认 packCode |
| 500 | `"场景包编码不能为空"` | body.code=500，真实 HTTP 仍为 200 | 检查请求体 |

### 2.7 通用参数错误（所有端点适用）

> **说明**：以下描述的是 AjaxResult 响应体中的 `code` 字段（body.code），非真实 HTTP Status。Spring 默认情况下 `AjaxResult.error()` 返回的 HTTP Status 仍为 200。

| body.code | msg 示例 | 说明 | 排查 |
|-----------|----------|------|------|
| 400 | `"请求参数错误"` | JSON 格式错误或必填字段缺失（通常来自 Spring 默认异常，非控制器显式返回） | 检查 JSON 格式和必填字段 |
| 403 | `"无法识别用户（API Key 未绑定且未传 userId）"` | Skill API 控制器返回，用户身份无法确定 | 传入 userId 或使用已绑定 userId 的 API Key |
| 500 | `"参数不能为空"` / `"场景包不存在: xxx"` 等 | 控制器显式 `AjaxResult.error(msg)`，body.code=500 | 对应排查列 |

---

## 3. 特殊业务约定

少数业务错误通过 `ServiceException(code, message)` 抛出：

| AjaxResult.code | msg | 说明 | 来源 |
|----------------|-----|------|------|
| 200 | `ALREADY_ACTIVATED` | 授权码已激活（幂等返回） | #4 |
| — | `PARTIAL_WRITE_FAILED` | 企微多分片写入部分失败 | #7 |

---

## 4. WebSocket 错误码

格式：`E####`（E1001 ~ E9999），来源 `WebSocketErrorCode.java`，共约 14 个。

| 错误码 | 说明 | 排查建议 |
|--------|------|----------|
| E1001 | 连接超时 | 检查网络和服务器状态 |
| E2001 | 认证失败 | 确认 WebSocket 握手携带有效 Token |
| E3001 | 消息格式错误 | 检查发送的 JSON 格式 |
| … | 其他 | 见 `WebSocketErrorCode.java` 完整定义 |

---

## 5. 其他体系错误码（仅需了解）

| 体系 | 错误码格式 | 数量 | 说明 |
|------|-----------|------|------|
| Engine 全局异常 | 字符串如 `INVALID_PARAMETER` | 6 | `GlobalExceptionHandler.java` |
| WecomSync 写入 | 字符串如 `INVALID_REQUEST` | 6 | `WecomWriteServiceImpl.java` |
| WecomSync 读取 | `INVALID_SHEET` / `CLI_NOT_FOUND` | 2 | `WecomSyncServiceImpl.java` |
| 积分预检 | `POINTS_ERROR` | 1 | `PointsPrecheckService.java` |

---

## 6. 两层错误机制与状态码说明

Skill API 的错误返回存在**两个不同的层次**，客户端必须按层次分别判断：

### 6.1 过滤器层：真实 HTTP Status

认证过滤器（`FbsApiKeyAuthFilter`）在请求到达控制器之前拦截，返回真实的 HTTP Status：

| 真实 HTTP Status | 触发条件 | 响应体 |
|------------------|----------|--------|
| 401 | API Key 缺失/无效、签名不匹配、时间戳缺失/过期 | `{code: 401, msg: "..."}` |
| 403 | API Key 被禁用 | `{code: 403, msg: "..."}` |
| 429 | 请求超限（速率限制） | `{code: 429, msg: "..."}` |

> **特点**：真实 HTTP Status 由 `response.setStatus()` 设置，body.code 与 HTTP Status 相同。

### 6.2 控制器层：body.code（AjaxResult）

控制器返回 `AjaxResult.error(msg)` 时，设置的是响应体中的 `body.code` 字段，**不改变真实 HTTP Status**（Spring 默认仍为 200）：

| body.code | 触发条件 | 真实 HTTP Status | 示例 msg |
|-----------|----------|-------------------|----------|
| 400 | Spring 默认参数绑定失败（JSON 格式错误等） | 200 | `"请求参数错误"` |
| 403 | API Key 未绑定 userId 且请求未传 userId | 200 | `"无法识别用户（API Key 未绑定且未传 userId）"` |
| 404 | 使用记录不存在 | 200 | `"使用记录不存在"` |
| 409 | usageRecordId 已处于终态（SUCCESS/FAILED） | 200 | `"使用记录已成功结束"` |
| 500 | 控制器显式 `AjaxResult.error(msg)`（参数校验/业务失败） | 200 | `"参数不能为空"`、`"场景包不存在: xxx"` |

> **判断规则**：客户端应**优先读 `response.body.code`**，非 200 即为失败（无论真实 HTTP Status 是否为 200）。
