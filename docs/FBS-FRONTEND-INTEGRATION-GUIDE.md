# FBS 权益体系 — 前端对接指南

> 本文档说明 WxFbsir 管理后台和 WorkBuddy 如何对接 FBS 内部 API。

---

## 一、现状分析

### 1.1 FBS 内部 API 位置
```
/fbs/internal/*
```
所有 FBS 相关接口均以 `/fbs/internal/` 为前缀，属于**内部接口**（非公开业务 API）。

### 1.2 调用方现状

| 调用方 | 现状 | 需要做什么 |
|--------|------|-----------|
| **WxFbsir 管理后台** | 目前无 FBS 相关 API 调用 | 新增业务层 Controller 包装 FBS 内部服务 |
| **WorkBuddy** | 目前无对接 | 按需调用指定 API |

---

## 二、WxFbsir 管理后台对接

### 2.1 已有业务层 API 参考

参考现有模式：`/business/certificateApplication/*` → 调用内部 `certificateApplicationService`

**建议新增**（可选，视需求决定）：

| 路由 | 方法 | 说明 |
|------|------|------|
| `/business/fbs/scene-pack/list` | GET | 场景包列表（分页） |
| `/business/fbs/scene-pack` | POST | 创建场景包（调用内部 `/scene-pack/create`） |
| `/business/fbs/auth-code/generate` | POST | 生成授权码 |
| `/business/fbs/auth-code/list` | GET | 授权码列表（分页） |
| `/business/fbs/user-pack/list` | GET | 用户-场景包关联列表 |

> **注意**：如果管理后台不需要操作这些数据，可以暂时不做。新增业务层 Controller 不是 MVP 必须项。

### 2.2 前端 API 文件结构建议

```
WxFbsir-ui/src/api/business/
└── fbs/
    └── scenePack.js      # 或合并到现有 business/points.js（视团队规范）
```

---

## 三、WorkBuddy 对接（核心场景）

WorkBuddy 是 FBS 场景的实际消费方，需要在特定 Skill 执行前调用权益校验。

### 3.1 调用时机

```
WorkBuddy 用户发起写书任务
    ↓
WorkBuddy 调用 U3W-AI: POST /fbs/internal/rights/check
    ↓
返回: { pass: true/false, pointsAmount: 10 }
    ↓
前端展示"即将消耗 10 积分"（可选）
    ↓
任务完成后
WorkBuddy 调用 U3W-AI: POST /fbs/internal/usage/consume
    ↓
返回: { success: true, remainPoints: 80 }
    ↓
WorkBuddy 更新用户积分显示
```

### 3.2 关键 API 详解

#### ① 权益预检（可选）

**POST** `/fbs/internal/rights/check`

```json
// Request
{
  "userId": 1,
  "packCode": "PACK_BOOK_WRITER_PRO",
  "authCode": null,          // 可选，用户输入授权码时传入
  "hostType": "WORKBUDDY",
  "taskId": "wb-task-001"    // 幂等键
}

// Response (校验通过)
{
  "code": 200,
  "msg": "校验通过",
  "data": {
    "pass": true,
    "failReason": null,
    "packId": 2,
    "pointsRuleCode": "FBS_BOOK_WRITER",
    "pointsAmount": 10       // 本次将扣 10 积分
  }
}

// Response (校验失败)
{
  "code": 200,
  "msg": "校验失败",
  "data": {
    "pass": false,
    "failReason": "积分不足，当前剩余: 5，需要: 10",
    "packId": null,
    "pointsRuleCode": null,
    "pointsAmount": null
  }
}
```

> ⚠️ **注意**：`code=200` 不代表成功，`data.pass=false` 才是业务失败。Fail-Closed 体现在业务层。

#### ② 消费扣积分（必须）

**POST** `/fbs/internal/usage/consume`

```json
// Request
{
  "userId": 1,
  "packCode": "PACK_BOOK_WRITER_PRO",
  "skillCode": "book_writer.generate_chapter",
  "usageRecordId": "wb-task-001",      // ⚠️ 幂等键，建议用 WorkBuddy taskId
  "hostType": "WORKBUDDY",
  "hostSessionId": "wb-session-001",    // 可选
  "authCode": null                      // 可选，消费时携带授权码
}

// Response (成功)
{
  "code": 200,
  "msg": "消费成功",
  "data": {
    "success": true,
    "usageRecordId": "wb-task-001",
    "remainPoints": 80,                 // ⚠️ 扣减后剩余积分
    "failReason": null
  }
}

// Response (失败)
{
  "code": 500,
  "msg": "积分不足，当前剩余: 5，需要: 10",
  "data": {
    "success": false,
    "usageRecordId": "wb-task-001",
    "remainPoints": null,
    "failReason": "积分不足，当前剩余: 5，需要: 10"
  }
}
```

#### ③ 授权码激活（用户输入授权码时调用）

**POST** `/fbs/internal/auth-code/activate`

```json
// Request
{
  "authCode": "TEST-AUTH-0001",   // 用户输入的授权码
  "userId": 1
}

// Response (成功)
{
  "code": 200,
  "msg": "激活成功",
  "data": {
    "userPackId": 5,
    "packId": 2,
    "packCode": "PACK_BOOK_WRITER_PRO"
  }
}

// Response (失败)
{
  "code": 500,
  "msg": "授权码已用尽"
}
```

### 3.3 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | Long | ✅ | 当前登录用户 ID（从 U3W-AI session 传入） |
| `packCode` | String | ✅ | 场景包编码（如 `PACK_BOOK_WRITER_PRO`） |
| `skillCode` | String | ✅ | 技能编码（如 `book_writer.generate_chapter`） |
| `usageRecordId` | String | ✅ | **幂等键**，建议用 WorkBuddy taskId，同一值不重复扣积分 |
| `hostType` | String | ✅ | 固定传 `WORKBUDDY` |
| `hostSessionId` | String | ❌ | WorkBuddy session ID |
| `authCode` | String | ❌ | 用户激活时用的授权码，消费时传入用于校验合法性 |

### 3.4 积分规则说明

| 字段 | 值 | 说明 |
|------|----|------|
| `points_rule_code` | `FBS_BOOK_WRITER` | 规则编码 |
| `points_value` | `-10` | 负数=扣10分，正数=奖10分 |
| `limit_type` | `DAY` | 每天限100分（10次/天） |
| `max_amount` | `100` | 当天累计上限 |

**免费包**：场景包 `points_rule_code = NULL` 时，不扣积分。

---

## 四、认证与鉴权

### 4.1 接口认证

`/fbs/internal/*` 接口目前**不需要额外鉴权**（Spring Security 未拦截），但在生产环境建议：

- 在 `FbsSkillConsumeController` 等处补充 `userId` 来源校验
- 从 Spring Security Context 获取当前登录用户 ID，而非信任前端传入

### 4.2 userId 来源

| 场景 | userId 来源 |
|------|------------|
| WxFbsir 管理后台 | 从 `@RequestHeader("userId")` 或 Security Context 获取 |
| WorkBuddy | 从请求 Header 传入（需双方约定 Header 名称，如 `X-User-Id`） |

---

## 五、错误码汇总

| 业务失败原因 | HTTP code | msg 示例 |
|-------------|-----------|---------|
| 场景包不存在 | 500 | `场景包不存在` |
| 用户无权益 | 500 | `用户未开通该场景包` |
| 积分不足 | 500 | `积分不足，当前剩余: X，需要: Y` |
| 授权码不存在 | 500 | `授权码不存在` |
| 授权码已用尽 | 500 | `授权码激活次数已达上限` |
| 授权码已过期 | 500 | `授权码已过期` |
| 授权码已禁用 | 500 | `授权码已禁用` |
| 重复激活 | 500 | `Duplicate entry '1-2' for key 'uk_user_pack'` |

> ⚠️ **HTTP 状态码说明**：目前内部 API 在业务失败时返回 `code=500`（如积分不足）和 `code=200`（如校验失败 `pass=false`）两种风格混用。调用方需要同时判断 `code` 和 `data.pass`。
