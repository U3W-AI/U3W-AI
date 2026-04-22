# FBS-BookWriter SDK 参考文档

> 本文档为 FBS-BookWriter 产品 SDK 参考文档，涵盖完整的 API 参考、认证机制、业务体系说明。
> 生产环境 API Base URL：`https://api.U3W.com`

---

## 目录

1. [产品概述](#1-产品概述)
2. [快速开始](#2-快速开始)
3. [认证机制](#3-认证机制)
4. [API 参考](#4-api-参考)
5. [乐包体系](#5-乐包体系)
6. [场景包体系](#6-场景包体系)
7. [授权码体系](#7-授权码体系)
8. [企业体系](#8-企业体系)
9. [安全说明](#9-安全说明)
10. [错误码参考](#10-错误码参考)
11. [变更日志](#11-变更日志)

---

## 1. 产品概述

### 1.1 产品定位

FBS-BookWriter（以下简称 Skill）是**福帮手出品的高质量长文档手稿工具链**，为 3 万字以上书籍、手册、白皮书、行业指南、长篇报道、深度专题提供 AI 辅助写作能力。

Skill 运行于宿主环境（WorkBuddy / CodeBuddy），通过后端 API 与积分/场景包体系交互，实现权益校验、积分扣减、场景包激活与降级等核心功能。

### 1.2 核心概念

| 概念 | 后端术语 | Skill 术语 | 说明 |
|------|---------|-----------|------|
| 积分（余额） | 乐包 / 积分余额 | Credits / 乐包 | 用户余额，Skill 端可查询/扣减 |
| 场景包 | Scene Pack | Scene Pack | 体裁模板，决定可用功能（general/whitepaper/report 等 8 种） |
| 授权码 | Auth Code | — | 单次或批量激活码，用于兑换场景包 |
| 用户层级 | User Tier | — | 决定场景包激活后的有效期和可用次数 |
| API Key | API Key | `FBS_API_KEY` | Skill 访问后端 API 的凭证 |
| 使用记录 | Usage Record | — | 积分扣减的幂等记录（usageRecordId） |

> **重要语义对齐**：后端"积分"即 Skill 端"乐包/Credits"，两者等价。文档中统一使用**积分/乐包**称呼。

### 1.3 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     FBS-BookWriter (Skill)                   │
│  scripts/lib/                                               │
│   ├── backend-api.mjs     ← 后端 API 调用封装               │
│   ├── credits-ledger.mjs  ← 本地账本（余额缓存）             │
│   ├── entitlement.mjs     ← 权益校验                         │
│   └── utils.mjs           ← 工具函数                         │
└────────────────────┬────────────────────────────────────────┘
                     │ HTTPS + X-FBS-API-Key + 签名
                     │ POST /fbs/skill-api/**
        ┌────────────▼────────────────────────────────────────┐
        │              U3W 后端 (api.U3W.com)                   │
        │  Spring Boot                                        │
        │   ├── FbsApiKeyAuthFilter  ← API Key + 签名校验      │
        │   ├── FbsSkillApiController ← Skill API 端点        │
        │   ├── PointsService          ← 积分管理              │
        │   └── ScenePackService       ← 场景包管理            │
        └────────────────────┬─────────────────────────────────┘
                           │ 企微智能表格
        ┌──────────────────▼─────────────────────────────────┐
        │              企业微信（WeCom）                        │
        │   ├── 企微通讯录                                     │
        │   ├── 智能表格（场景包/授权码数据）                   │
        │   └── 消息推送                                      │
        └─────────────────────────────────────────────────────┘
```

### 1.4 术语对照表（后端 ↔ Skill）

| 后端 / OpenSpec | Skill / FBS-BookWriter | 说明 |
|----------------|----------------------|------|
| 积分 / 乐包 / pointsBalance | Credits / 乐包 / `balance` | 等价概念，本地账本中为 `credits-ledger.json.balance` |
| 场景包 scene_pack | Scene Pack | 8 种体裁：general/consultant/ghostwriter/training/personal-book/whitepaper/report/genealogy |
| 授权码 auth_code | — | SINGLE（单次）/ BATCH（批量）两类 |
| API Key (`fbs_api_key`) | `FBS_API_KEY` + `FBS_API_BASE_URL` | Skill 端按优先级读取：1. 环境变量 `FBS_API_KEY` / `FBS_API_BASE_URL`；2. `~/.fbs/config.json` |
| 使用记录 usage_record | `usageRecordId` | 积分扣减的幂等 ID |
| 权益校验 rights/check | `entitlement.mjs`（待按后端契约补充） | 检查用户是否有权使用特定场景包，**推荐接口**：后端 `pass` + `failReason` |
| 消费积分 usage/consume | `consumeCredits()` | 后端扣减积分 |
| 行为积分上报 points/earn | `earnPoints()` | 后端增加用户积分（如完章奖励） |
| 用户信息 user/info | `fetchUserInfo()` | 返回积分余额和已激活场景包 |

> **接口对齐说明**：当前 `backend-api.mjs` 的 `checkRights({ packCode })` 尚未按后端 `rights/check` 契约实现（不传 `userId`、读 `hasRights`），`entitlement.mjs` 目前通过本地门槛 + `fetchUserInfo()` 判断。文档将 `rights/check` 列为 `entitlement.mjs` 的后端契约，实际接入时需先补充 `backend-api.mjs` 的 `checkRights` 封装。

### 1.5 关键约束

- Skill **不管理**用户身份，靠 `X-FBS-API-Key` 识别用户
- 后端 API 必须携带有效签名（`X-FBS-Timestamp` + `X-FBS-Signature`），否则 401
- 积分以**后端为权威**，本地账本为离线缓存
- 场景包降级策略：余额不足时自动降级到 `general`，不阻塞写作流程

---

## 2. 快速开始

本节帮助第三方开发者在 3 步内完成 FBS-BookWriter 后端 API 的接入。

> 完整文档见 `QUICK-START.md`，此处为摘要版。

### 2.1 第 1 步：注册账号

访问 FBS 后台管理系统，注册账号并登录。

> **获取 API Key**：登录后在「我的 API Key」页面查看已有 Key，或创建新 Key。

### 2.2 第 2 步：配置 API Key

在 FBS-BookWriter 配置中写入：

```json
{
  "FBS_API_KEY": "fbs_your_key_here",
  "FBS_API_BASE_URL": "https://api.U3W.com"
}
```

> `FBS_API_BASE_URL` 只配**主机地址**（不含 `/fbs/skill-api`），代码会自动追加。配置错误会导致双斜杠 404。

### 2.3 第 3 步：调用第一个 API

所有 `/fbs/skill-api/**` 请求必须携带三个 Header（#15 安全加固后）：

| Header | 说明 |
|--------|------|
| `X-FBS-API-Key` | API Key（同时作为 HMAC 签名密钥） |
| `X-FBS-Timestamp` | Unix 毫秒时间戳 |
| `X-FBS-Signature` | `HMAC-SHA256(apiKey, timestamp + '\n' + body)` 小写 hex |

**签名字符串**：`${timestamp}\n${JSON.stringify(body)}`

> body = `{}` 时序列化为 `"{}"`（含双引号）；GET 请求 body = `""`

**Python 示例（查询用户余额）：**

```python
import requests, time, hmac, hashlib

API_KEY = "fbs_your_key_here"
BASE_URL = "https://api.U3W.com"

def sign(api_key, body):
    ts = str(int(time.time() * 1000))
    sig = hmac.new(api_key.encode(), (ts + "\n" + body).encode(),
                   hashlib.sha256).hexdigest()
    return ts, sig

body = "{}"
ts, sig = sign(API_KEY, body)
resp = requests.post(
    BASE_URL + "/fbs/skill-api/user/info",
    headers={"X-FBS-API-Key": API_KEY, "X-FBS-Timestamp": ts,
             "X-FBS-Signature": sig, "Content-Type": "application/json"},
    data=body
)
print(resp.json())
# → {"code": 200, "data": {"userId": 1001, "pointsBalance": 500, "activatedPacks": [...]}}
```

> 完整多语言示例（cURL / Python / Node.js）→ 见 `QUICK-START.md`
> 错误码排查 → 见 `ERROR-CODES.md`

---

## 3. 认证机制

### 3.1 概述

所有 `/fbs/skill-api/**` 请求必须携带三个 Header，由 `FbsApiKeyAuthFilter` 按序校验：

```
API Key → 时间戳 → 签名 → 速率限制
```

### 3.2 三个必需 Header

| Header | 格式 | 说明 |
|--------|------|------|
| `X-FBS-API-Key` | `fbs_` + 64 位 hex | API Key 明文，在控制台获取 |
| `X-FBS-Timestamp` | Unix ms 整数 | 如 `1740000000000`；超过 5 分钟差异则拒绝 |
| `X-FBS-Signature` | HMAC-SHA256 十六进制小写 | `HMAC-SHA256(apiKey, timestamp + '\n' + body)` |

**签名字符串**：`${timestamp}\n${JSON.stringify(body)}`

- body 为空对象 `{}` 时，序列化为 `"{}"`（带双引号）
- GET 请求 body = `""`（空字符串）

### 3.3 签名示例（Python）

```python
import hmac, hashlib, time, json

def sign(api_key: str, body: str):
    ts = str(int(time.time() * 1000))
    sig = hmac.new(api_key.encode(), (ts + "\n" + body).encode(),
                   hashlib.sha256).hexdigest()
    return ts, sig
```

### 3.4 失败响应

| 错误码 | HTTP 状态 | 原因 |
|--------|----------|------|
| `SKILL_API_KEY_INVALID` | 401 | Key 不存在 |
| `SKILL_API_KEY_DISABLED` | 403 | Key 已禁用 |
| `SKILL_API_TIMESTAMP_MISSING` | 401 | 缺少时间戳 |
| `SKILL_API_TIMESTAMP_EXPIRED` | 401 | 时间戳超过 5 分钟 |
| `SKILL_API_SIGNATURE_INVALID` | 401 | 签名不匹配 |
| `SKILL_API_RATE_LIMITED` | 429 | 请求过于频繁 |

> **返回格式**：`{"code": 401, "msg": "API Key 无效"}`（`errorCode` 字段不会被写入响应体）

---

## 4. API 参考

### 4.1 端点概览

| 分类 | 端点数 | 认证方式 |
|------|--------|----------|
| Skill API | 7 | 三签名头（API Key + Timestamp + Signature） |
| 用户自助 API | 4 | 普通业务 API（JWT/登录态） |
| 用户侧 API Key 管理 | 4 | 普通业务 API |
| 运营 API | 46 | 普通业务 API |

---

### 4.2 Skill API（7 个）

> **认证**：所有端点必须携带 `X-FBS-API-Key` + `X-FBS-Timestamp` + `X-FBS-Signature`

#### POST /fbs/skill-api/user/info
**说明**：查询当前用户积分余额和已激活场景包

**请求头**：
| 参数 | 必填 | 说明 |
|------|:----:|------|
| X-FBS-API-Key | ✅ | API Key |
| X-FBS-Timestamp | ✅ | Unix ms 时间戳 |
| X-FBS-Signature | ✅ | HMAC-SHA256 小写 hex |

**请求体**：
```json
{}
```

**响应示例**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": 1001,
    "pointsBalance": 500,
    "activatedPacks": [
      {
        "packId": 5,
        "packCode": "whitepaper",
        "packName": "白皮书",
        "packStatus": 1,
        "status": 1,
        "expiresAt": "2026-12-31"
      }
    ]
  }
}
```
> **说明**：`activatedPacks` 中每项字段：`packId`（场景包 ID）、`packCode`（编码）、`packName`（名称）、`packStatus`（包状态：0=草稿/1=已发布/2=已下架）、`status`（用户激活状态）、`expiresAt`（过期时间，可为 null 表示永不过期）。

**错误码**：认证过滤器失败 → 真实 HTTP 4xx；控制器业务失败 → `body.code` 非 200（如 403/500），真实 HTTP 通常仍为 200

---

#### POST /fbs/skill-api/rights/check
**说明**：权益校验，检查用户是否有权使用特定场景包

**请求体**：
```json
{
  "userId": 1001,
  "packCode": "whitepaper"
}
```

**响应示例**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "pass": true,
    "failReason": null,
    "packId": 5,
    "pointsRuleCode": "PACK_WHITE_PAPER",
    "pointsAmount": 100
  }
}
```

**失败响应示例**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "pass": false,
    "failReason": "积分余额不足",
    "packId": 5,
    "pointsRuleCode": "PACK_WHITE_PAPER",
    "pointsAmount": 100
  }
}
```

**错误码**：认证过滤器失败 → 真实 HTTP 4xx；控制器业务失败 → `body.code` 非 200（如 500），真实 HTTP 通常仍为 200（`/rights/check` 的 `pass=false` 业务失败走 body.code=200）

---

#### POST /fbs/skill-api/usage/start
**说明**：标记一次使用开始（usageRecordId 由调用方提供，幂等 — 若已存在则返回已有记录）

**请求体**：
```json
{
  "userId": 1001,
  "packCode": "whitepaper",
  "skillCode": "FBS-BookWriter",
  "usageRecordId": "caller-provided-uuid-xxx"
}
```

**响应示例**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "usageRecordId": "caller-provided-uuid-xxx",
    "status": 0
  }
}
```

> **幂等说明**：若调用方传入的 `usageRecordId` 已存在且状态为 `IN_PROGRESS`（status=0），接口返回已有记录而非创建新记录，调用方应使用返回的 `usageRecordId` 继续后续流程（如 `/usage/consume`）。

**错误响应示例**（usageRecordId 已处于 SUCCESS 或 FAILED 状态）：
```json
{
  "code": 409,
  "msg": "使用记录已成功结束"
}
```
> **说明**：已存在的记录为 SUCCESS → body.code=409；为 FAILED → body.code=409，msg 为"使用记录已失败结束"。换用新的 usageRecordId 即可解决。

---

#### PUT /fbs/skill-api/usage/end/{usageRecordId}
**说明**：标记使用结束（幂等）

**路径参数**：`usageRecordId` — 使用记录 ID（来自 `/usage/start`）

**请求体**：
```json
{
  "status": 1,
  "errorMessage": ""
}
```
> `status`：1 = 成功，2 = 失败（失败时 errorMessage 必填）

**响应示例**：
```json
{
  "code": 200,
  "msg": "更新成功"
}
```

> **幂等说明**：若记录已处于 SUCCESS/FAILED 状态，再次调用返回 `body.code=409`（真实 HTTP 通常仍为 200），msg 为"使用记录已成功结束"或"使用记录已失败结束"。

---

#### POST /fbs/skill-api/usage/consume
**说明**：扣减用户积分（幂等，通过 usageRecordId 防重）

**请求体**：
```json
{
  "packCode": "whitepaper",
  "skillCode": "FBS-BookWriter",
  "usageRecordId": "uuid-xxx-xxx"
}
```

**响应示例**（成功）：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": true,
    "remainPoints": 490,
    "usageRecordId": "caller-provided-uuid-xxx",
    "failReason": null
  }
}
```
> **说明**：失败时 `success=false`，`failReason` 含具体原因（如"积分余额不足"）；成功时 `failReason=null`。

**错误码**：认证过滤器失败 → 真实 HTTP 4xx；控制器业务失败 → `body.code` 非 200（如 500/403），真实 HTTP 通常仍为 200

---

#### POST /fbs/skill-api/scene-pack/query
**说明**：通过 packCode 查询单个场景包的详细信息（包含规则快照）

**请求体**：
```json
{
  "packCode": "whitepaper"
}
```

**响应示例**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "packCode": "whitepaper",
    "packName": "白皮书",
    "currentVersion": 1,
    "status": 1,
    "pointsRuleCode": "PACK_WHITE_PAPER",
    "contentSnapshot": "{...}"
  }
}
```

> **说明**：`status` 为 Integer，语义：`0` = 草稿，`1` = 已发布，`2` = 已下架。

---

#### POST /fbs/skill-api/points/earn
**说明**：用户完成特定行为（如完章）后增加积分

**请求体**：
```json
{
  "source": "chapter_complete",
  "amount": 10,
  "usageRecordId": "caller-provided-uuid-xxx"
}
```

**响应示例**（成功）：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": true,
    "pointsAmount": 10,
    "remainPoints": 510,
    "usageRecordId": "caller-provided-uuid-xxx"
  }
}
```

**错误码**：认证过滤器失败 → 真实 HTTP 4xx；控制器业务失败 → `body.code` 非 200（如 500/403），真实 HTTP 通常仍为 200

---

### 4.3 用户自助 API（4 个）

> **认证**：普通业务 API（登录态/JWT）

| 端点 | 方法 | 路径 |
|------|------|------|
| 激活授权码 | POST | /my/auth-code/activate |
| 我的场景包 | GET | /my/packs |
| 可领取场景包 | GET | /my/scene-packs |
| 领取场景包 | POST | /my/scene-pack/claim |

**请求头**（通用模板）：
| 参数 | 必填 | 说明 |
|------|:----:|------|
| Authorization | ✅ | Bearer Token（或登录态 Header） |

---

### 4.4 用户侧 API Key 管理（4 个）

> **认证**：普通业务 API；前缀 `/fbs/business/my/apikey`

| 端点 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 创建 API Key | POST | /fbs/business/my/apikey/create | |
| 列表 API Key | GET | /fbs/business/my/apikey/list | |
| 禁用/启用 | PUT | /fbs/business/my/apikey/toggle/{id}?status= | status=0 禁用，status=1 启用 |
| 删除 API Key | DELETE | /fbs/business/my/apikey/{id} | |

---

### 4.5 运营 API（按子域分组）

#### 4.5.1 场景包运营（6 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 场景包分页列表 | GET | /business/fbs/scene-pack/list |
| 场景包详情 | GET | /business/fbs/scene-pack/{id} |
| 创建场景包 | POST | /business/fbs/scene-pack |
| 编辑场景包 | PUT | /business/fbs/scene-pack |
| 发布场景包 | PUT | /business/fbs/scene-pack/publish |
| 下架场景包 | PUT | /business/fbs/scene-pack/unpublish |

#### 4.5.2 授权码运营（5 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 授权码分页列表 | GET | /business/fbs/auth-code/list |
| 批量生成授权码 | POST | /business/fbs/auth-code/generate |
| 禁用授权码 | PUT | /business/fbs/auth-code/disable |
| 启用授权码 | PUT | /business/fbs/auth-code/enable |
| 撤销授权码 | PUT | /business/fbs/auth-code/revoke |

#### 4.5.3 用户权益查询运营（2 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 用户-场景包分页列表 | GET | /business/fbs/user-pack/list |
| 用户权益统计 | GET | /business/fbs/user-pack/stats |

#### 4.5.4 运营侧 API Key 管理（6 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 生成 API Key | POST | /fbs/business/api-key/generate |
| 查询列表（脱敏） | GET | /fbs/business/api-key/list |
| 查询详情（脱敏） | GET | /fbs/business/api-key/{id} |
| 禁用 API Key | PUT | /fbs/business/api-key/disable/{id} |
| 启用 API Key | PUT | /fbs/business/api-key/enable/{id} |
| 删除 API Key | DELETE | /fbs/business/api-key/{id} |

#### 4.5.5 企业运营（13 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 企业分页列表 | GET | /business/fbs/enterprise/list |
| 企业详情 | GET | /business/fbs/enterprise/{id} |
| 创建企业 | POST | /business/fbs/enterprise |
| 编辑企业 | PUT | /business/fbs/enterprise |
| 禁用企业 | PUT | /business/fbs/enterprise/disable |
| 成员列表 | GET | /business/fbs/enterprise/member/list |
| 成员详情 | GET | /business/fbs/enterprise/member/{id} |
| 添加成员 | POST | /business/fbs/enterprise/member |
| 移除成员 | DELETE | /business/fbs/enterprise/member/{id} |
| 企业包分发/撤销等 | — | /business/fbs/enterprise/pack/*（4 个子路径：GET /list、GET /{id}、POST /grant、PUT /revoke） |

#### 4.5.6 企微同步运营（13 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 读取企微数据 | POST | /fbs/business/wecom/sync/read |
| 检测可用性 | POST | /fbs/business/wecom/sync/check |
| 写入企微数据 | POST | /fbs/business/wecom/sync/write |
| 子表管理（4 个） | GET/POST/PUT/DELETE | /fbs/business/wecom/schema/sheets、/schema/sheet |
| 字段管理（4 个） | GET/POST/PUT/DELETE | /fbs/business/wecom/schema/fields |
| 记录更新/删除（2 个） | PUT/DELETE | /fbs/business/wecom/records |

---

## 5. 乐包体系

### 5.1 概念

乐包（即积分/Credits）是 FBS-BookWriter 的核心货币单位。用户通过完章等行为获得积分，用于解锁场景包功能。

> **后端与 Skill 术语对齐**：后端"积分/乐包" = Skill 端"Credits/乐包"，完全等价。

### 5.2 积分规则

| 行为 | 奖励积分 | 说明 |
|------|----------|------|
| 完成一章 | +10 | 最常见行为激励 |
| 首次激活授权码 | +X | 由运营配置 |
| 运营补偿 | 任意 | 后台手动发放 |

### 5.3 幂等控制

积分扣减通过 `usageRecordId` 实现幂等：同一 `usageRecordId` 重复调用 `/usage/consume` 不会重复扣减。

### 5.4 离线降级

当后端 API 不可用时：
1. Skill 优先调用后端 API
2. 请求失败则 fallback 到本地 `credits-ledger.json` 缓存
3. 本地账本通过 HMAC 校验完整性（`HMAC-SHA256(apiKey, JSON.stringify({balance, total_earned, total_spent}))`）

> **注意**：本地账本仅作缓存，余额以**后端为权威**。联网恢复后自动同步。

---

## 6. 场景包体系

### 6.1 概念

场景包（Scene Pack）决定用户可使用的体裁和功能。共 8 种体裁：

| 体裁 | 说明 | 激活方式 |
|------|------|----------|
| `general` | 内置，默认可用 | 无需激活 |
| `consultant` | 咨询报告 | 授权码 |
| `ghostwriter` | 代笔服务 | 授权码 |
| `training` | 培训材料 | 授权码 |
| `personal-book` | 个人出书 | 授权码 |
| `whitepaper` | 白皮书 | 授权码 |
| `report` | 深度报道 | 授权码 |
| `genealogy` | 家谱 | 授权码 |

### 6.2 门槛规则

场景包有最低积分门槛（如 `whitepaper` 需要 100 积分）。余额不足时自动降级到 `general`。

### 6.3 加载流程

1. Skill 启动时加载 `scene-packs/` 配置
2. 用户发起写作请求 → `entitlement.mjs` 检查权益
3. 权益不足 → 输出降级提示 + 降级到 `general`
4. 权益充足 → 加载对应场景包资产

### 6.4 离线降级

场景包激活信息存储在企微智能表格（`fbs_user_pack`），离线时无法激活新场景包。已有激活的包在离线状态下可用。

---

## 7. 授权码体系

### 7.1 概念

授权码（Auth Code）用于激活场景包，分为两种类型：

| 类型 | 说明 | `max_activations` |
|------|------|-------------------|
| `SINGLE` | 单次激活 | 1 |
| `BATCH` | 批量激活 | > 1 |

### 7.2 生命周期

```
可用（available=1, status=0）→ 第一次激活（activated_count=1, status=1）→ 用尽（status=2）
```

### 7.3 状态字段

| 字段 | 说明 |
|------|------|
| `available` | 启用状态（独立维度，0=禁用，1=启用） |
| `status` | 激活状态（0=未激活，1=已激活，2=已用尽） |
| `activated_count` | 已激活次数 |
| `max_activations` | 最大激活次数 |
| `deadline` | 截止时间（NULL=永不过期） |

### 7.4 激活流程

1. 用户在 Skill 中输入授权码
2. Skill 调用 `POST /my/auth-code/activate`
3. 后端校验 `available`、`status`、`activated_count`、`deadline`
4. 成功后写入 `fbs_user_pack`，返回激活的场景包信息

---

## 8. 企业体系

### 8.1 概念

企业包（Enterprise Pack）用于批量分发场景包给企业成员，支持配额管理。

### 8.2 企业包分发

企业管理员通过后台创建企业包，指定成员范围和配额。成员通过企业账号登录后自动解锁对应场景包。

### 8.3 配额管理

企业包有配额上限（`quota`），成员解锁时占用配额。配额用尽后需联系企业管理员扩展。

---

## 9. 安全说明

### 9.1 API Key 安全

- **存储**：API Key 明文存储在数据库（`fbs_api_key.api_key`），不迁移为 SHA-256 hash（HMAC 签名依赖明文）
- **传输**：通过 HTTPS 传输，Header 为 `X-FBS-API-Key`
- **格式**：`fbs_` + 64 位 hex 字符（共 68 字符），熵 256 bit，无规律可循
- **轮换**：当前仅支持手动禁用+重新创建，自动轮换为 P2

### 9.2 请求签名（HMAC-SHA256）

所有 `/fbs/skill-api/**` 请求必须携带签名：

```
X-FBS-Signature = HMAC-SHA256(apiKey, timestamp + '\n' + body)
```

- 签名消息格式：`${timestamp}\n${JSON.stringify(body)}`
- body 为空对象 `{}` 时序列化为 `"{}"`
- 输出为**小写**十六进制
- 后端使用 `MessageDigest.isEqual()` 防时序攻击

### 9.3 时间戳防重放

- `X-FBS-Timestamp` 缺失 → 401 `SKILL_API_TIMESTAMP_MISSING`
- 时间戳与服务器时间差异 > 5 分钟 → 401 `SKILL_API_TIMESTAMP_EXPIRED`

### 9.4 本地积分文件完整性校验

`credits-ledger.json` 写入时附加 `_hmac` 字段：
```
_hmac = HMAC-SHA256(apiKey, JSON.stringify({balance, total_earned, total_spent}))
```

读取时校验 HMAC，失败则 balance 清零并标记需从后端拉取。

---

## 10. 错误码参考

### 10.1 概述

本文档不重复完整错误码列表，请参见配套文档 `ERROR-CODES.md`。该文档按端点分类描述所有 Skill API 的业务逻辑错误契约，包括：

- **认证与签名错误**（HTTP 4xx）：API Key 无效、签名校验失败、时间戳缺失或过期、Key 已禁用、限频
- **端点级业务合同**（按 `/rights/check`、`/usage/consume`、`/usage/start`、`/usage/end`、`/points/earn`、`/scene-pack/query` 分表）：各端点的成功/失败响应格式、`body.code` 与 data 字段的对应关系
- **通用参数错误**：400/401/403 在 Skill API 上下文中的具体含义
- **其他体系错误码**：WebSocket、Engine、WecomSync 等，仅需了解

> **重要**：`/rights/check` 的业务失败（`data.pass=false`）走 `body.code=200`；`/usage/consume`、`/points/earn` 等端点失败时 `body.code≠200`（如 403、500）。判断逻辑：**优先读 `body.code`，非 200 即失败**；等于 200 时再按端点特定字段（`pass` / `success`）判断业务结果。

### 10.2 快速索引

#### 过滤器拦截（真实 HTTP Status）

| 错误类型 | 典型 msg | 真实 HTTP Status |
|----------|----------|------------------|
| API Key 无效 | `API Key 无效` | 401 |
| 签名校验失败 | `签名不匹配` | 401 |
| 时间戳过期 | `时间戳超过 5 分钟` | 401 |
| Key 已禁用 | `API Key 已被禁用` | 403 |
| 请求超限 | `请求过于频繁` | 429 |

#### 控制器业务错误（body.code，非 200；真实 HTTP 通常仍为 200）

| 错误类型 | 典型 msg | body.code |
|----------|----------|-----------|
| 参数不能为空 | `参数不能为空` | 500 |
| 场景包不存在 | `场景包不存在: xxx` | 500 |
| 用户身份无法识别 | `无法识别用户（API Key 未绑定且未传 userId）` | 403 |
| 使用记录状态冲突 | `使用记录已成功结束` / `使用记录已失败结束` | 409 |
| 积分余额不足 | `积分余额不足` | 500 |
| 使用记录不存在 | `使用记录不存在` | 404 |

> **判断规则**：优先读 `response.body.code`——等于 200 则成功（但需注意 `/rights/check` 的 `pass=false` 也走 200）；非 200 则失败。真实 HTTP Status 只有在过滤器拦截时才有效（401/403/429）。

---

## 11. 变更日志

本文档记录 FBS-BookWriter 后端 API 的完整变更历史，按 OpenSpec 阶段分组。

### #1-#5：底座与网关

| 阶段 | 核心交付 |
|------|----------|
| #1 | 福帮手权益与场景包领域底座：4 张数据库表（scene_pack/auth_code/user_pack/usage_record）+ 核心服务 |
| #2 | 平台场景包运营 CRUD（创建/发布/下架） |
| #3 | 企业场景包体系（企业包/成员分发/配额管理） |
| #4 | 用户自助服务体系（激活授权码/领取场景包/查看我的包） |
| #5 | Skill API 网关（/fbs/skill-api/** 统一入口） |

### #6-#9：企微打通

| 阶段 | 核心交付 |
|------|----------|
| #6 | 企微 CLI 集成基础 |
| #7 | 企微智能表格写入 MVP |
| #8 | 企微 Schema 管理（子表/字段 CRUD） |
| #9 | 业务数据同步至企微 |

### #10-#13：积分与乐包统一

| 阶段 | 核心交付 |
|------|----------|
| #10 | 积分模型适配（credits-ledger.mjs 本地账本 + 幂等扣减） |
| #11 | 前端 API Key 管理（用户侧 API Key 的 CRUD） |
| #12 | Skill API 对接（user/info + usage/consume + points/earn，userId 可选） |
| #13 | 企微商业枢纽字段同步（审计字段写入） |

> **说明**：`points/earn` 能力实现于 #12 / #15，未单独归档。

### #15：安全加固

| 阶段 | 核心交付 |
|------|----------|
| #15 | API Key 格式升级（Base64 36字符 → Hex 68字符）；签名机制（HMAC-SHA256 + 时间戳防重放） |

---

## 附录：规范版本历史

| 规范文件 | 版本 | 说明 |
|----------|------|------|
| `fbs-rights-foundation/spec.md` | v1.0 | MVP 权益底座 |
| `platform-scene-pack-ops/spec.md` | v1.8 | 平台运营（#13 扩展后） |
| `skill-api-security/spec.md` | — | 无显式版本号 |
