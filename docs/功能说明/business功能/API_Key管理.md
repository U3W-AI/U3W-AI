# API Key 管理

本文档说明 API Key 的功能、安全机制、运营管理和用户自助管理的完整流程。

---

## 一、功能概述

API Key 是 Skill 脚本调用后端 FBS Skill API 的身份凭证。运营人员可以在后台创建 API Key，用户也可以在"我的权益"页面自助管理自己的 Key。

**核心特性：**
- API Key 格式：`fbs_` + 64位 Hex 随机串（总计68字符）
- 创建时返回完整 Key（仅此一次），关闭后无法再查看完整值
- 列表查询只返回脱敏值（前8位 + `****`）
- 需绑定用户（userId），一个用户可创建多个 Key
- 支持 HMAC-SHA256 签名 + 时间戳防重放（OpenSpec #15）

---

## 二、安全机制

### 2.1 API Key 认证

Skill API（`/fbs/skill-api/**`）使用 API Key 认证，不走 JWT：

**请求头**：
```
X-FBS-API-Key: fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
X-FBS-Timestamp: 1745143200000
X-FBS-Signature: HMAC-SHA256(apiKey, timestamp + "\n" + body).toHex()
```

**签名算法**：
```
message = timestamp + "\n" + bodyJSON
signature = HMAC-SHA256(apiKey, message).toHex()
```

**校验规则**：
- API Key 存在且 status=1（启用）
- 时间戳与服务器时间差 ≤ 5分钟
- 签名验证通过

### 2.2 速率限制

每个 API Key 默认 60次/分钟，超出返回 429。

---

## 三、运营侧管理（/fbs/business/api-key）

运营人员可管理所有用户的 API Key。

### 3.1 生成 API Key

**接口**：`POST /fbs/business/api-key/generate`

**请求体**：
```json
{
  "name": "我的Skill Key",
  "packCode": "pack_bookwriter",
  "rateLimitPerMin": 60,
  "remark": "写书助手专用"
}
```

**响应**（创建时返回完整 Key）：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "apiKey": "fbs_abc123def456...（完整68字符）",
    "name": "我的Skill Key"
  }
}
```

### 3.2 查询 API Key 列表

**接口**：`GET /fbs/business/api-key/list`

**响应**（脱敏显示）：
```json
{
  "code": 200,
  "data": [
    { "id": 1, "apiKey": "fbs_abc1****", "name": "我的Skill Key", "status": 1 }
  ]
}
```

### 3.3 禁用/启用 API Key

**接口**：`PUT /fbs/business/api-key/disable/{id}`

**接口**：`PUT /fbs/business/api-key/enable/{id}`

### 3.4 删除 API Key

**接口**：`DELETE /fbs/business/api-key/{id}`

---

## 四、用户自助管理（/fbs/business/my/apikey）

用户可以在"我的权益"页面自助管理自己的 API Key。

### 4.1 创建 API Key

**接口**：`POST /fbs/business/my/apikey/create`（路径前缀 `/fbs/business/my/apikey`，完整路径见接口定义）

**请求体**：`{ "name": "我的Key" }`

**说明**：
- userId 自动从当前登录用户获取
- 创建时返回完整 Key（仅此一次），关闭后无法再查看

**响应**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "apiKey": "fbs_3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5",
    "name": "我的Key"
  }
}
```

### 4.2 查询我的 API Key 列表

**接口**：`GET /fbs/business/my/apikey/list`

**说明**：只返回当前用户的 Key，数据隔离

### 4.3 禁用/启用 API Key

**接口**：`PUT /fbs/business/my/apikey/toggle/{id}`

**请求参数**（QueryParam）：`status` = 0（禁用）或 1（启用）

### 4.4 删除 API Key

**接口**：`DELETE /fbs/business/my/apikey/{id}`

---

## 五、数据模型

### fbs_api_key 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| api_key | VARCHAR(128) | API Key（唯一） |
| user_id | BIGINT | 绑定用户ID |
| name | VARCHAR(128) | Key 名称 |
| pack_code | VARCHAR(64) | 关联场景包编码 |
| rate_limit_per_min | INT | 速率限制（次/分钟），默认60 |
| status | INT | 状态：1=启用, 0=禁用 |
| last_used_at | DATETIME | 最后使用时间 |
| remark | VARCHAR(256) | 备注 |
| del_flag | CHAR(1) | 删除标志 |

---

## 六、权限说明

### 运营侧（/fbs/business/api-key）

| 接口 | 权限标识 |
|------|---------|
| 生成 | `business:fbs:apikey:add` |
| 列表查询 | `business:fbs:apikey:list` |
| 详情查询 | `business:fbs:apikey:query` |
| 禁用 | `business:fbs:apikey:edit` |
| 启用 | `business:fbs:apikey:edit` |
| 删除 | `business:fbs:apikey:remove` |

### 用户侧（/fbs/business/my/apikey）

| 接口 | 权限标识 |
|------|---------|
| 列表查询 | `my:apikey:list` |
| 创建 | `my:apikey:create` |
| 切换状态 | `my:apikey:toggle` |
| 删除 | `my:apikey:delete` |

---

## 七、已知安全限制

API Key + userId 模型存在天然越权风险：持有有效 API Key 的调用方可以伪造任意 userId。

**MVP 阶段接受此风险的前提条件：**
1. API Key 仅配置在受控宿主环境（WorkBuddy），不面向公网第三方开放
2. 不通过公开文档/SDK 分发
3. 生产环境强制 HTTPS

**后续迭代加固方向：**
- API Key 绑定 packCode 白名单
- 宿主签名 userId（HMAC-SHA256(apiKey, userId + timestamp)）
- IP 白名单限制

---

**最后更新**：2026-04-22