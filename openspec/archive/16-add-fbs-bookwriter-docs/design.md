# 设计：FBS-BookWriter 产品文档生成（16-add-fbs-bookwriter-docs）

## 1. 文档体系架构

### 1.1 三份文档定位

```
FBS-BookWriter 产品文档
├── SDK-REFERENCE.md     — 完整 SDK 参考（面向集成开发者）
├── QUICK-START.md       — 快速开始（面向首次接入）
└── ERROR-CODES.md       — 错误码字典（面向调试排错）
```

**SDK-REFERENCE.md** 是主文档，QUICK-START.md 和 ERROR-CODES.md 是配套文档。

### 1.2 术语对齐

贯穿三份文档的核心术语：

| 概念 | 说明 |
|------|------|
| 乐包 / Credits | 后端积分余额，Skill 端等价概念 |
| body.code | AjaxResult 响应体中的 code 字段，与真实 HTTP Status 不同 |
| 真实 HTTP Status | 过滤器层通过 `response.setStatus()` 设置的 HTTP 状态码 |
| 两层错误机制 | 过滤器层（真实 HTTP 4xx）+ 控制器层（body.code） |

---

## 2. 两层错误机制（核心技术背景）

### 2.1 过滤器层（认证过滤器）

**来源**：`FbsApiKeyAuthFilter.java` 的 `writeErrorResponse()` 方法

```java
// FbsApiKeyAuthFilter.java
private void writeErrorResponse(HttpServletResponse response, int httpStatus,
                                 String errorCode, String errorMessage) throws IOException {
    response.setStatus(httpStatus);                          // ← 设置真实 HTTP Status
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    var errorBody = java.util.Map.of(
            "code", httpStatus,    // ← body.code = HTTP Status
            "msg", errorMessage
    );
    response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    response.getWriter().flush();
}
```

**特点**：
- `response.setStatus(httpStatus)` 设置真实 HTTP Status（401/403/429）
- body.code = HTTP Status
- 请求在到达 Controller 之前被拦截

### 2.2 控制器层（Skill API Controller）

**来源**：`FbsSkillApiController.java` 和 `AjaxResult.java`

```java
// FbsSkillApiController.java — 典型业务错误
return AjaxResult.error("积分余额不足");        // body.code = 500（HttpStatus.ERROR）
return AjaxResult.error(409, "使用记录已成功结束"); // body.code = 409

// AjaxResult.java
public static AjaxResult error(String msg) {
    return new AjaxResult(HttpStatus.ERROR, msg, null);  // HttpStatus.ERROR = 500
}

public static AjaxResult error(int code, String msg) {
    return new AjaxResult(code, msg, null);             // 自定义 code
}
```

**特点**：
- `AjaxResult.error()` 不调用 `HttpServletResponse.setStatus()`
- Spring 默认使用 HTTP 200（因为没有 `ResponseEntity`）
- **真实 HTTP Status 通常仍为 200**，业务层必须读 `body.code` 判断

### 2.3 客户端判断规则

```
优先读 response.body.code：

body.code = 200  →  成功
              （但 /rights/check 的 data.pass=false 也走 body.code=200，需额外判断）
body.code ≠ 200  →  失败（body.code = 403/409/500 等）
body 不存在 / 真实 HTTP Status = 401/403/429  →  认证过滤器拦截
```

---

## 3. Skill API DTO 契约（源码核实）

### 3.1 POST /fbs/skill-api/user/info

**源码**：`FbsSkillApiController.java` 行 258-301

**响应 data 字段**（全部 6 个）：
```java
data.put("userId", userId);
data.put("pointsBalance", pointsBalance);
data.put("activatedPacks", activatedPacks);  // List<Map>
// 每项 Map: packId, packCode, packName, packStatus, status, expiresAt
```

**注意**：旧版文档只写了 3 个字段（userId/pointsBalance/activatedPacks），漏掉了 activatedPacks 内部的详细字段。

### 3.2 POST /fbs/skill-api/usage/consume

**源码**：`FbsSkillApiController.java` 行 91-127

**成功响应 data 字段**（全部 4 个）：
```java
data.put("success", result.isSuccess());        // true
data.put("usageRecordId", result.getUsageRecordId());
data.put("remainPoints", result.getRemainPoints());
data.put("failReason", result.getFailReason()); // null（成功时）
```

**失败路径**（行 122-126）：
```java
if (result.isSuccess()) {
    return AjaxResult.success(data);
} else {
    return AjaxResult.error(result.getFailReason()); // body.code=500
}
```

**关键**：`ConsumeResult.fail()` 设置 `failReason`，但 `success=false` 路径不走 `data`，而是直接 `AjaxResult.error(failReason)`，body.code=500。

### 3.3 POST /fbs/skill-api/points/earn

**源码**：`FbsSkillApiController.java` 行 310-364

**成功响应 data 字段**（全部 5 个）：
```java
data.put("success", true);
data.put("pointsAmount", request.getAmount());
data.put("remainPoints", remainPoints);
data.put("usageRecordId", request.getUsageRecordId());
```

**失败路径**（行 354-363）：
```java
} else {
    // changePoints 返回错误（规则未配置、限频等）
    data.put("success", false);
    data.put("failReason", result.get(AjaxResult.MSG_TAG));
    return AjaxResult.error(String.valueOf(result.get(AjaxResult.MSG_TAG)));
    // body.code=500（AjaxResult.error(String) 使用 HttpStatus.ERROR=500）
}
```

**关键**：失败时 `data` 包含 `success=false` 和 `failReason`，但 Controller 仍然返回 `AjaxResult.error()`（body.code=500），客户端根据 body.code 而非 data.success=false 判断。

---

## 4. 文档结构设计

### 4.1 SDK-REFERENCE.md 章节结构

```
1. 产品概述         — 产品定位、核心概念、系统架构、术语对照表
2. 快速开始         — 3 步接入摘要
3. 认证机制         — 三 Header 签名机制
4. API 参考         — Skill API（7个）、用户自助（4个）、用户侧API Key（4个）、运营API（46个）
5. 乐包体系         — 积分概念、规则、幂等、离线降级
6. 场景包体系       — 8种体裁、门槛规则、加载流程
7. 授权码体系       — 类型、生命周期、激活流程
8. 企业体系         — 企业包分发、配额管理
9. 安全说明         — API Key安全、请求签名、本地文件HMAC
10. 错误码参考      — 概述 + 快速索引（两层机制）
11. 变更日志        — #1-#15 各阶段交付
```

### 4.2 ERROR-CODES.md 章节结构

```
1. 认证与签名错误（Skill API，HTTP 4xx）
2. 业务逻辑错误（按端点分表：rights/check、usage/consume、usage/start、usage/end、points/earn、scene-pack/query）
   2.7 通用参数错误（所有端点适用）
3. 特殊业务约定
4. WebSocket 错误码
5. 其他体系错误码
6. 两层错误机制与状态码说明（过滤器层 + 控制器层）
```

---

## 5. API Key 配置路径

### 5.1 Skill 端配置读取优先级

**源码**：`api-key-config.mjs`

```
1. 环境变量  FBS_API_KEY / FBS_API_BASE_URL
2. 本地文件  ~/.fbs/config.json
```

**注意**：
- 旧文档曾错误描述为 `workbuddy/channel-manifest.json`（不存在）
- `FBS_API_BASE_URL` 只配主机地址（不含 `/fbs/skill-api`），代码自动追加
- 错误配置示例：`https://api.U3W.com/fbs/skill-api` → 代码追加后变成双斜杠 `.../fbs/skill-api/fbs/skill-api/...` → 404

---

## 6. OpenSpec #14 不存在的问题

变更日志中不应出现 `#14`：

| 变更阶段 | 实际内容 |
|----------|----------|
| #10-#13 | 积分与乐包统一（credits-ledger、API Key管理、Skill API、企微字段同步） |
| #15 | 安全加固（HMAC-SHA256 + 时间戳防重放） |

`points/earn` 实现于 #12/#15，未单独归档 — 变更日志中应注明。
