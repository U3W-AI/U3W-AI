# Design: add-skill-api-gateway

> **Change ID**：05-add-skill-api-gateway
> **日期**：2026-04-11

---

## 1. 认证链路

```
请求进入 Spring Security Filter Chain
  │
  ├─ 路径匹配 /fbs/skill-api/** ?
  │    │
  │    ├─ YES → FbsApiKeyAuthFilter
  │    │         │
  │    │         ├─ 读取 X-FBS-API-Key Header
  │    │         ├─ 查 fbs_api_key 表（selectActiveByKey）
  │    │         │    ├─ Key 不存在 → 401 SKILL_API_KEY_INVALID
  │    │         │    ├─ Key 已禁用(status!=1) → 403 SKILL_API_KEY_DISABLED
  │    │         │    └─ Key 有效 → 继续
  │    │         ├─ 速率限制检查（Redis 计数 或 内存计数器）
  │    │         │    └─ 超限 → 429 SKILL_API_RATE_LIMITED
  │    │         ├─ 设置 SecurityContext（PreAuthenticatedAuthenticationToken）
  │    │         └─ chain.doFilter() → FbsSkillApiController
  │    │
  │    └─ NO → JwtAuthenticationTokenFilter（原有逻辑不变）
  │
  └─ 后续 Filter 链
```

**关键点**：
- FbsApiKeyAuthFilter 注册在 JwtAuthenticationTokenFilter **之前**
- `/fbs/skill-api/**` 在 SecurityConfig 中配置为 `permitAll()`（不要求 JWT），但由 FbsApiKeyAuthFilter 做认证
- 其他 `/fbs/internal/**` 和 `/fbs/business/**` 不受影响，仍走 JWT

## 2. 过滤器顺序

```
SecurityConfig 中的 Filter Chain 顺序：

1. FbsApiKeyAuthFilter        ← 新增，拦截 /fbs/skill-api/**
2. JwtAuthenticationTokenFilter  ← 已有，拦截其他路径
3. UsernamePasswordAuthenticationFilter
4. ...
```

**实现方式**：在 SecurityConfig 中用 `http.addFilterBefore(apiKeyAuthFilter, JwtAuthenticationTokenFilter.class)` 注册。

## 3. /skill-api/** 与 /internal/** 的关系

| 对比项 | /fbs/skill-api/** | /fbs/internal/** |
|--------|-------------------|-------------------|
| 认证方式 | API Key (X-FBS-API-Key Header) | JWT (Cookie/Header) |
| 调用方 | Skill 脚本（Node.js，无会话） | 系统内部前端（浏览器，有会话） |
| 用户标识 | 请求 Body 中传 userId | SecurityContext 中获取 userId |
| 权限控制 | API Key 粒度 | @PreAuthorize 注解 |
| 速率限制 | 每个 Key 60次/分钟 | 无 |

**Skill API 网关不修改任何 /fbs/internal/** 接口，完全并行存在。**

## 4. 各接口返回内容

### 4.1 rights/check

```json
{
  "pass": true,
  "failReason": null,
  "packId": 1,
  "pointsRuleCode": "rule_bookwriter",
  "pointsAmount": 10
}
```

- 复用 `RightsCheckService.comprehensiveCheck`
- pointsAmount 从 `wx_points_rule.points_value` 获取（只读，不扣减）

### 4.2 usage/consume

```json
{
  "success": true,
  "usageRecordId": "wb-task-uuid-001",
  "pointsAmount": 10,
  "remainPoints": 990,
  "failReason": null
}
```

- 复用 `SkillConsumeService.consume(userId, packCode, skillCode, usageRecordId, hostType, null, authCode)`
- **不传 pointsAmount**，由 Service 内部计算
- hostSessionId 传 null（Skill API 场景无宿主会话）

### 4.3 usage/start

> **⚠️ 与 consume 互斥**：start/end 和 consume 是两种独立的消费模式，同一个 usageRecordId 只能走一种。现有 `SkillConsumeService.consume` 是自闭环的（自己 INSERT status=0 → 扣减 → UPDATE status=1），如果先用 start 插入 record，consume 会因 status=0 拒绝"正在处理中"；如果先 end 改成 status=1/2，consume 会幂等返回或拒绝。**选择逻辑：需要扣费 → consume；只需记录 → start/end。**

**幂等语义**：
- 先查 `FbsSkillUsageRecordMapper.selectByRecordId(usageRecordId)`
- 已存在且 status=0 → 返回已有记录（不重复创建）
- 已存在且 status=1 → 返回 409 "使用记录已成功结束"
- 已存在且 status=2 → 返回 409 "使用记录已失败结束"
- 不存在 → insertUsageRecord 创建新记录

```json
{
  "usageRecordId": "wb-task-uuid-001",
  "status": 0
}
```

- 复用 `FbsSkillUsageRecordMapper.insertUsageRecord`
- usageRecordId 由调用方生成（String 类型）

### 4.4 usage/end/{usageRecordId}

> **⚠️ 与 consume 互斥**：同 §4.3，end 只负责更新记录状态，不扣减积分/配额。同一 usageRecordId 不能再调用 consume。

```json
{ "msg": "更新成功" }
```

- 复用 `FbsSkillUsageRecordMapper.updateStatusByRecordId(usageRecordId, status, errorMessage)`
- usageRecordId 为路径参数，String 类型
- **幂等语义**：
  - 记录不存在 → 404 "使用记录不存在"
  - status=0 → 更新为 1 或 2（只允许一次状态转换）
  - status=1 → 409 "使用记录已成功结束"
  - status=2 → 409 "使用记录已失败结束"

### 4.5 scene-pack/query

```json
{
  "packCode": "pack_bookwriter_v2",
  "packName": "写书助手 v2.0",
  "currentVersion": "1.0.0",
  "status": 1,
  "pointsRuleCode": "rule_bookwriter",
  "contentSnapshot": "{...JSON规则内容...}"
}
```

- 查询 `FbsScenePackMapper.selectByPackCode`
- **直接返回 contentSnapshot**（现有字段，原始 JSON 字符串）
- **不做二次结构化转换**：返回的是 contentSnapshot 字段的原始值，Skill 端自行 JSON.parse，后端不新建 DTO 来解构规则内容
- **不返回 ruleFileUrl**（当前仓库无此字段/机制）

### 4.6 user/info

```json
{
  "userId": 1,
  "pointsBalance": 990,
  "activatedPacks": [
    { "packCode": "pack_bookwriter_v2", "packName": "写书助手 v2.0", "status": 1 }
  ]
}
```

- pointsBalance：`IPointsService.getUserPoints(userId)` → 返回 Integer（已验证：PointsMapper → `SELECT IFNULL(points,0) FROM sys_user WHERE user_id=?`）
- activatedPacks：`FbsUserPackMapper.selectMyPacks` → 过滤 status=1
- **不返回 T0-T3 用户层级**（当前仓库无此模型）

## 5. 限流方案

**MVP 阶段**：内存计数器（ConcurrentHashMap + AtomicLong）

```java
// FbsApiKeyAuthService 内部
ConcurrentHashMap<String, RateLimitEntry> rateLimitMap;

static class RateLimitEntry {
    AtomicLong count;
    long windowStart;  // 毫秒时间戳
}
```

- 每次请求：检查 windowStart 是否在当前分钟内，是则 count++，否则重置窗口
- 超过 rateLimitPerMin → 返回 429
- 优点：无外部依赖，MVP 够用
- 缺点：单机限流，集群部署时不共享
- **后续迭代**：改为 Redis + 滑动窗口

## 6. API Key 生成算法

```
fbs_ + Base64.urlSafeEncode(randomBytes(24))
```

- 24 字节随机 → Base64 编码 ≈ 32 字符
- 前缀 `fbs_` 便于识别
- 总长度 ≈ 36 字符，VARCHAR(64) 足够
- 生成时查 fbs_api_key 表确保 UNIQUE

## 7. 安全注意事项

| 项 | 说明 |
|----|------|
| API Key 明文 | MVP 存明文，创建时仅返回一次完整 Key，列表查询脱敏（前8位+****） |
| **userId 越权风险** | **⚠️ 核心风险**：API Key + body.userId 模型存在天然越权风险。持有有效 API Key 即可伪造任意 userId 查权益/扣积分/写记录。MVP 接受此风险，**前提**：（1）仅部署在受控 WorkBuddy 宿主环境；（2）不面向公网第三方开放；（3）生产环境强制 HTTPS。后续迭代加固：API Key 绑定 packCode 白名单、宿主签名 userId（HMAC-SHA256）、IP 白名单 |
| HTTPS | 生产环境必须 HTTPS，防止 API Key 被嗅探 |
| Key 轮换 | 管理员可随时禁用旧 Key + 生成新 Key |
| 受控部署 | API Key 仅由管理员手动配置到宿主 `_plugin_meta.json`，不通过公开文档/SDK 分发 |
