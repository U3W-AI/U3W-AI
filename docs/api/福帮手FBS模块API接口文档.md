# 福帮手（FBSir）FBS模块API接口文档
> 更新日期：2026-07-22

> **文档范围说明**：本文档收录当前仓库已实现的全部API，包括：
> - Skill API（`/fbs/skill-api/**`）- 面向Skill脚本的API Key认证接口
> - 业务运营API（`/business/fbs/**`）- 运营侧JWT认证接口
> - 用户自助API（`/my/**`）- 用户侧JWT认证接口
> - 内部API（`/fbs/internal/**`）- 内部服务调用接口
>
> **未收录范围**：`/fbs/business/wecom/**` 企微同步接口因涉及企微智能表格内部实现细节，暂不收录。

---

## 一、Skill API（API Key认证）

**路径前缀**：`/fbs/skill-api/**`
**认证方式**：API Key（通过 `X-FBS-API-Key` 请求头）+ HMAC-SHA256签名（`X-FBS-Signature`）+ 时间戳（`X-FBS-Timestamp`）
**说明**：面向Skill脚本的REST API，由 `FbsApiKeyAuthFilter` 校验API Key

**用户身份规则**：除纯场景包查询和已退役的积分获取 tombstone 外，用户级接口只信任 API Key 的绑定用户。未绑定 Key 返回 body.code `403/SKILL_API_KEY_USER_BINDING_REQUIRED`；请求如携带 `userId`，它仅用于一致性断言，不一致返回 `403/SKILL_API_KEY_USER_MISMATCH`。运营侧生成但未绑定用户的 Key 不能调用用户级接口。

**场景包范围边界**：只有数据库 `fbs_api_key.pack_code IS NULL` 表示全局 Key；空串或纯空白是无效范围并失败关闭。非空 `pack_code` 是场景包专用 Key，按原始字符串精确匹配（不 trim、不忽略大小写），适用于 `rights/check`、`usage/consume`、`usage/start`、`scene-pack/query`。授权预检只读取候选场景包的 `id + pack_code`，保留普通等值前导谓词作为可索引候选，并追加二进制精确比较；结果为零条或多条均失败关闭，不能在授权前读取完整场景包快照。请求其他包返回 body.code `403/SKILL_API_KEY_PACK_SCOPE_MISMATCH`；Key 或记录的包范围无法解析时返回 `403/SKILL_API_KEY_PACK_SCOPE_UNVERIFIABLE`。`usage/end` 先完成 Key 自身范围预检，再读取既有记录、校验记录用户，随后以记录的 `packId` 反查并校验记录包范围，最后才执行终态 CAS；`user/info` 保留用户全局积分余额，但 `activatedPacks` 只投影专用 Key 绑定的包。全局 Key 保持跨包兼容。

**幂等范围规则**：`usageRecordId` 不是独立授权凭证。个人消费重放必须同时匹配 API Key 绑定用户、场景包、技能、规范化宿主类型和 hostSessionId；start 重放匹配用户、场景包、技能和宿主类型。任一维度不一致均失败关闭为 `SKILL_USAGE_RECORD_SCOPE_MISMATCH`，不得读取余额、扣积分或扣企业配额。企业旧记录缺少 enterprise/member 快照，因此一律不提供成功重放。hostSessionId 只是 HMAC 签名调用方声明，不等同于服务端验证过的宿主实例身份。

### 1.1 权益校验 `POST /fbs/skill-api/rights/check`

校验用户是否有权使用指定场景包。

**请求参数**（body）：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 否 | 仅作 API Key 绑定用户的一致性断言 |
| packCode | String | 是 | 场景包编码 |
| authCode | String | 否 | 授权码 |
| hostType | String | 否 | 宿主类型，默认WORKBUDDY |

**响应**（`AjaxResult.success(data)`）：
```json
{
  "code": 200, "msg": "操作成功",
  "data": {
    "pass": true,
    "failReason": null,
    "packId": 1,
    "pointsRuleCode": "rule_bookwriter",
    "pointsAmount": 10
  }
}
```
**失败响应**：返回 `AjaxResult.success(data)` 但 `pass=false`，`failReason` 包含失败原因
**错误响应**：Key 未绑定、身份断言冲突、专用 Key 跨包或包范围不可解析时 body.code 为 403；packCode 为空等参数错误通常为 500。

---

### 1.2 积分消费 `POST /fbs/skill-api/usage/consume`

一次性消费积分（与 `usage/start`+`usage/end` 互斥）。

**请求参数**（body）：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 否 | 仅作 API Key 绑定用户的一致性断言 |
| packCode | String | 是 | 场景包编码 |
| skillCode | String | 是 | 技能编码 |
| usageRecordId | String | 是 | 使用记录ID（幂等键） |
| hostType | String | 否 | 宿主类型，默认WORKBUDDY |
| hostSessionId | String | 否 | 宿主会话ID；个人消费重放时属于幂等范围 |
| authCode | String | 否 | 授权码 |

**响应成功**：
```json
{ "code": 200, "msg": "操作成功",
  "data": { "success": true, "usageRecordId": "xxx", "remainPoints": 490, "failReason": null }
}
```
**响应失败**（返回 `AjaxResult.error(...)`）：
| body.code | 场景 |
|-----------|------|
| 403 | API Key 未绑定用户，或请求 userId 与绑定用户不一致 |
| 403 | 场景包专用 Key 请求其他包（`SKILL_API_KEY_PACK_SCOPE_MISMATCH`） |
| 403 | Key 的 pack_code 为空白或无法证明（`SKILL_API_KEY_PACK_SCOPE_UNVERIFIABLE`） |
| 500 | usageRecordId 已被其他用户、场景包、技能、宿主类型或宿主会话范围占用（`SKILL_USAGE_RECORD_SCOPE_MISMATCH`） |
| 500 | 企业旧记录缺少 tenant/member 快照，无法证明重放范围（`SKILL_ENTERPRISE_USAGE_REPLAY_SCOPE_UNVERIFIED`） |
| 500 | 同一用户存在多个活动企业成员身份（`SKILL_ENTERPRISE_MEMBERSHIP_SCOPE_AMBIGUOUS`） |
| 500 | 参数不能为空（packCode/usageRecordId/skillCode 任一为空） |
| 500 | 场景包不存在 |
| 500 | skillConsumeService.consume 返回业务失败（积分不足、配额已用尽、成员授权已失效等） |

真实 HTTP 状态码通常仍为 200，只有认证过滤器失败时才会产生真实的 HTTP 4xx。

企业路径只在用户恰好有一个活动企业成员身份时允许首次消费；旧 `fbs_skill_usage_record` 未保存 enterprise/member 快照，因此任何既有企业记录都零写失败关闭，不再返回无法证明租户范围的“幂等成功”。

---

### 1.3 开始使用 `POST /fbs/skill-api/usage/start`

两阶段模式开始（与 `consume` 互斥）。

**请求参数**（body）：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 否 | 仅作 API Key 绑定用户的一致性断言 |
| packCode | String | 是 | 场景包编码 |
| skillCode | String | 是 | 技能编码 |
| usageRecordId | String | 是 | 使用记录ID（幂等键） |
| hostType | String | 否 | 宿主类型，默认WORKBUDDY |

**响应成功**：
```json
{ "code": 200, "msg": "操作成功",
  "data": { "usageRecordId": "xxx", "status": 0 }
}
```
**幂等**：记录已存在且 status=0 且用户/场景包/技能/宿主范围完全一致时返回已有记录；范围不一致时 body.code 为 409，消息为 `SKILL_USAGE_RECORD_SCOPE_MISMATCH`。

API Key 必须绑定用户；新记录的 `userId` 始终取自绑定，不信任请求体。场景包专用 Key 只允许为其精确绑定的 `packCode` 开始记录，且在任何记录读取或写入前完成范围校验。

---

### 1.4 结束使用 `PUT /fbs/skill-api/usage/end/{usageRecordId}`

两阶段模式结束（与 `consume` 互斥）。

**路径参数**：`usageRecordId` - 使用记录ID

**请求参数**（body）：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | 是 | 1=成功, 2=失败 |
| errorMessage | String | 否 | 失败时填写 |

**响应成功**：`{ "code": 200, "msg": "更新成功" }`

**错误码**（返回 `AjaxResult.error(...)`，body.code=404/409，真实 HTTP 状态码通常仍为 200）：
| body.code | 说明 |
|-----------|------|
| 404 | 使用记录不存在 |
| 409 | 使用记录已成功结束 |
| 409 | 使用记录已失败结束 |
| 409 | 并发请求已先完成状态转换（`SKILL_USAGE_RECORD_STATE_CONFLICT`） |
| 403 | API Key 未绑定，或记录不属于该 Key 的绑定用户 |
| 403 | 记录所属场景包与专用 Key 不一致（`SKILL_API_KEY_PACK_SCOPE_MISMATCH`） |
| 403 | 记录缺少 packId、场景包已不可解析，或 Key 范围无效（`SKILL_API_KEY_PACK_SCOPE_UNVERIFIABLE`） |

> **注意**：控制器层 `AjaxResult.error(404, ...)` / `AjaxResult.error(409, ...)` 仅设置响应体中的 code 字段，真实 HTTP 状态码通常仍为 200。只有认证过滤器（FbsApiKeyAuthFilter）失败时才会产生真实的 HTTP 4xx 状态码。

当前 end 已校验 API Key 绑定用户、记录用户和记录所属场景包，并通过 `WHERE status=0` 的条件更新阻止终态覆盖；旧表未保存 API Key 实例或宿主会话归属，这两项仍是下一迁移需求，不能宣称已完成 host-instance 级隔离。

---

### 1.5 场景包查询 `POST /fbs/skill-api/scene-pack/query`

查询场景包信息。

**请求参数**（body）：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| packCode | String | 是 | 场景包编码 |

**响应**：
```json
{
  "code": 200, "msg": "操作成功",
  "data": {
    "packCode": "pack_bookwriter", "packName": "写书助手",
    "currentVersion": "1.0.0", "status": 1,
    "pointsRuleCode": "rule_bookwriter",
    "contentSnapshot": "{...}"  // 原始JSON字符串
  }
}
```

场景包专用 Key 只能查询与 `fbs_api_key.pack_code` 精确相等的场景包；全局 Key 可查询任意存在的包。

---

### 1.6 用户信息 `POST /fbs/skill-api/user/info`

查询用户信息和已激活权益包。

**请求参数**（body）：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 否 | 仅作 API Key 绑定用户的一致性断言 |

**响应**：
```json
{
  "code": 200, "msg": "操作成功",
  "data": {
    "userId": 1001, "pointsBalance": 500,
    "activatedPacks": [{
      "packId": 1, "packCode": "pack_bookwriter",
      "packName": "写书助手", "packStatus": 1,
      "status": 1, "expiresAt": "2026-12-31"
    }]
  }
}
```

场景包专用 Key 的响应仍包含绑定用户的全局积分余额，但 `activatedPacks` 只包含该 Key 绑定且用户已激活的包；绑定包已无法解析时失败关闭，不读取积分或用户包列表。全局 Key 返回用户全部已激活包。

---

### 1.7 积分获取 `POST /fbs/skill-api/points/earn`

> **已关闭（2026-07-22）**：旧接口允许调用方选择 `userId`、`source` 和 `amount`，不再作为可写合同。保留路径仅用于向旧客户端返回明确的退役信号；恢复积分写入前必须接入服务端定价、API Key 用户强绑定、请求摘要幂等与不可变账本。

**请求参数**（body）：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 否 | 用户ID（可空，从API Key反查） |
| source | String | 是 | 积分来源标识 |
| amount | Integer | 是 | 积分数量（正整数） |
| usageRecordId | String | 是 | 事件ID（幂等键） |

**当前响应**：真实 HTTP 状态码为 `410 Gone`，不读取请求身份或金额，也不调用积分服务。
```json
{ "code": 410, "msg": "SKILL_POINTS_EARN_DISABLED" }
```

调用方不得重试或回退到 `/points/changePoints`。福帮手连接器与服务侧升级需求见 `docs/independent-board/FBS-CONNECTOR-SERVICE-UPGRADE-DEMAND-CREDIT-LEDGER.md`。

---

## 二、业务运营API（JWT认证）

**路径前缀**：`/business/fbs/**`
**认证方式**：JWT Token（`Authorization: Bearer {token}`）

### 2.1 场景包管理 `/business/fbs/scene-pack`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/list` | 分页列表 | `business:fbs:scenePack:list` |
| GET | `/{id}` | 详情 | `business:fbs:scenePack:query` |
| POST | `/` | 创建 | `business:fbs:scenePack:add` |
| PUT | `/` | 编辑 | `business:fbs:scenePack:edit` |
| PUT | `/publish` | 发布 | `business:fbs:scenePack:publish` |
| PUT | `/unpublish` | 下架 | `business:fbs:scenePack:unpublish` |

**create请求体**：
```json
{ "packCode": "pack_bookwriter", "packName": "写书助手" }
```

**publish/unpublish请求体**：`{ "id": 1 }`

---

### 2.2 授权码管理 `/business/fbs/auth-code`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/list` | 分页列表 | `business:fbs:authCode:list` |
| POST | `/generate` | 批量生成 | `business:fbs:authCode:generate` |
| PUT | `/disable` | 禁用 | `business:fbs:authCode:disable` |
| PUT | `/enable` | 启用 | `business:fbs:authCode:enable` |
| PUT | `/revoke` | 撤销 | `business:fbs:authCode:revoke` |

**generate请求体**：
```json
{ "targetPackCode": "pack_bookwriter", "count": 10, "deadline": "2026-12-31" }
```

---

### 2.3 用户权益管理 `/business/fbs/user-pack`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/list` | 分页列表 | `business:fbs:userPack:list` |
| GET | `/stats?userId={userId}` | 权益统计 | `business:fbs:userPack:query` |

**stats响应**：
```json
{ "code": 200, "msg": "操作成功",
  "data": { "totalCount": 10, "activeCount": 8, "expiredCount": 1, "revokedCount": 1 }
}
```

---

### 2.4 企业管理 `/business/fbs/enterprise`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/list` | 分页列表 | `business:fbs:enterprise:list` |
| GET | `/{id}` | 详情 | `business:fbs:enterprise:query` |
| POST | `/` | 创建 | `business:fbs:enterprise:add` |
| PUT | `/` | 编辑 | `business:fbs:enterprise:edit` |
| PUT | `/disable` | 禁用 | `business:fbs:enterprise:disable` |

---

### 2.5 企业成员管理 `/business/fbs/enterprise/member`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/list?enterpriseId={id}` | 成员列表 | `business:fbs:enterpriseMember:list` |
| GET | `/{id}` | 成员详情 | `business:fbs:enterpriseMember:query` |
| POST | `/` | 添加成员 | `business:fbs:enterpriseMember:add` |
| DELETE | `/{id}` | 移除成员 | `business:fbs:enterpriseMember:remove` |

---

### 2.6 企业场景包管理 `/business/fbs/enterprise/pack`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/list?enterpriseId={id}&status={status}` | 列表 | `business:fbs:enterprisePack:list` |
| GET | `/{id}` | 详情 | `business:fbs:enterprisePack:query` |
| POST | `/grant` | 分发 | `business:fbs:enterprisePack:grant` |
| PUT | `/revoke` | 撤销 | `business:fbs:enterprisePack:revoke` |

**grant请求体**：
```json
{ "enterpriseId": 1, "packCode": "pack_bookwriter", "packQuota": 100 }
```

---

## 三、API Key运营管理（JWT认证）

**路径前缀**：`/fbs/business/api-key`
**权限前缀**：`business:fbs:apikey:*`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/generate` | 生成Key | `business:fbs:apikey:add` |
| GET | `/list` | 列表（脱敏） | `business:fbs:apikey:list` |
| GET | `/{id}` | 详情（脱敏） | `business:fbs:apikey:query` |
| PUT | `/disable/{id}` | 禁用 | `business:fbs:apikey:edit` |
| PUT | `/enable/{id}` | 启用 | `business:fbs:apikey:edit` |
| DELETE | `/{id}` | 删除 | `business:fbs:apikey:remove` |

**generate请求体**：`{ "name": "我的Key", "packCode": "pack_xxx", "rateLimitPerMin": 60 }`

> **当前签发边界（2026-07-22）**：运营端 `/generate` 可写 `packCode`，但不会绑定 `userId`；用户自助创建链路会绑定 `userId`，但不会写 `packCode`。因此仓内正常 API 尚不能签发同时“用户绑定 + 场景包专用”的 Key。前者不能调用用户级 Skill API，后者仍是全局包范围；在补齐经单独授权审查的签发生命周期前，不得用手工数据库写入作为已运营闭环的证据。

**响应**（创建时返回完整Key，仅此一次）：
```json
{ "code": 200, "msg": "操作成功",
  "data": { "id": 1, "apiKey": "fbs_xxxxxxxx...", "name": "我的Key" }
}
```

---

## 四、用户自助API（JWT认证）

**路径前缀**：`/my/**`
**认证方式**：JWT Token（`Authorization: Bearer {token}`）
**说明**：需要登录态/JWT认证，当前控制器未额外声明细粒度权限注解（@PreAuthorize），接口依赖当前登录用户上下文。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/packs` | 我的权益列表（分页） |
| POST | `/auth-code/activate` | 激活授权码 |
| GET | `/scene-packs` | 可领取的平台场景包（分页） |
| POST | `/scene-pack/claim` | 领取场景包 |

**activate请求体**：`{ "authCode": "FBS-XXXXXXXX" }`
**activate响应**：`{ "code": 200, "data": { "userPackId": 1, "packId": 1, "packCode": "pack_xxx" } }`

**claim请求体**：`{ "packCode": "pack_xxx" }`

---

## 五、内部API（内部调用）

**路径前缀**：`/fbs/internal/**`
**说明**：内部服务调用接口，无认证注解

### 5.1 授权码 `/fbs/internal/auth-code`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/generate` | 生成授权码 |
| POST | `/activate` | 激活授权码 |

**generate请求体**：
```json
{
  "codeType": 1, "issuerType": 1, "issuerId": 100,
  "targetType": "SCENE_PACK", "targetId": 1,
  "maxActivations": 1, "deadline": "2026-12-31"
}
```
**响应**：`{ "code": 200, "data": { "id": 1, "authCode": "FBS-XXXXXXXXXXXX" } }`

**activate请求体**：`{ "authCode": "FBS-XXXXXXXX", "userId": 1001 }`
**activate响应**：`{ "code": 200, "data": { "userPackId": 1, "packId": 1, "packCode": "pack_xxx" } }`

---

### 5.2 权益校验 `/fbs/internal/rights`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/check` | 综合权益校验 |

**请求体**：
```json
{ "userId": 1001, "packCode": "pack_bookwriter", "authCode": null, "hostType": "WORKBUDDY", "taskId": null }
```
**响应**：
```json
{ "code": 200, "msg": "校验通过",
  "data": { "pass": true, "failReason": null, "packId": 1, "pointsRuleCode": "rule_xxx", "pointsAmount": 10 }
}
```

---

### 5.3 场景包 `/fbs/internal/scene-pack`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/create` | 创建场景包 |
| GET | `/{id}` | 根据ID查询 |

**create请求体**：
```json
{ "packCode": "pack_xxx", "packName": "场景包", "packType": 1, "pointsRuleCode": "rule_xxx" }
```

---

### 5.4 Skill消费 `/fbs/internal/usage`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/consume` | **已退役**：真实 HTTP 410 |

旧接口仅受 JWT 登录保护且信任 body.userId，不具备可验证的内部服务身份。当前方法不绑定或读取请求体，也不调用 `SkillConsumeService`：
```json
{ "code": 410, "msg": "INTERNAL_SKILL_CONSUME_DISABLED" }
```

调用方必须迁移到绑定用户、HMAC 签名的 `/fbs/skill-api/usage/consume`，或未来另行设计可审计的服务到服务身份合同。

---

### 5.5 使用记录 `/fbs/internal/usage`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/record` | 写入使用记录（开始） |
| PUT | `/record/{usageRecordId}/end` | 结束使用记录 |

**record请求体**：`{ "usageRecordId": "xxx", "userId": 1001, "hostType": "WORKBUDDY", "skillCode": "FBS-BookWriter" }`

**end请求体**：`{ "status": 1, "errorMessage": null }`（status: 1=成功, 2=失败）

---

## 六、认证与安全

### 6.1 Skill API认证

```
X-FBS-API-Key: fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
X-FBS-Timestamp: 1745143200000
X-FBS-Signature: HMAC-SHA256(apiKey, timestamp + "\n" + body).toHex()
```

**签名校验**：时间戳与服务器时间差必须 ≤ 5分钟

### 6.2 业务API认证

所有 `/business/**`、`/my/**` 接口需要JWT Token：
```
Authorization: Bearer {token}
```

### 6.3 响应格式

| 类型 | 说明 |
|------|------|
| `AjaxResult` | 通用响应 `{ code, msg, data }` |
| `TableDataInfo` | 分页响应 `{ total, rows, code, msg }` |

---

## 七、数据库表

| 表名 | 说明 |
|------|------|
| fbs_scene_pack | 场景包定义 |
| fbs_auth_code | 授权码 |
| fbs_user_pack | 用户-场景包权益 |
| fbs_member_pack | 企业成员-场景包授权（纯授权凭证，不存独立配额；配额扣减统一在 fbs_enterprise_pack.packQuota/usedQuota；status 仅用于标记授权有效性：1=正常, 4=已撤销（企业包撤销或重新分发时级联更新）） |
| fbs_skill_usage_record | 使用记录（status: 0=进行中,1=成功结束,2=失败结束） |
| fbs_api_key | API Key |
| fbs_enterprise | 企业主表 |
| fbs_enterprise_pack | 企业-场景包（配额） |
| fbs_enterprise_member | 企业成员 |
| fbs_wecom_sync_log | 企微同步日志 |

---

## 八、OpenSpec变更记录

| 变更ID | 日期 | 主要内容 |
|--------|------|----------|
| #01 | 2026-04-08 | FBS权限基础（4表：scene_pack, auth_code, user_pack, skill_usage_record） |
| #02 | 2026-04-09 | 平台场景包运营（CRUD + 发布/下架） |
| #03 | 2026-04-09 | 企业场景包分发（企业/成员/配额） |
| #04 | 2026-04-10 | 用户自助服务（/my/* 授权码激活+场景包领取） |
| #05 | 2026-04-11 | Skill API网关（API Key认证+积分校验/消费） |
| #06 | 2026-04-15 | 企微CLI集成 |
| #07 | 2026-04-15 | 企微智能表格写入MVP |
| #08 | 2026-04-15 | 企微智能表格Schema管理（子表/字段CRUD） |
| #09 | 2026-04-16 | 业务数据同步到企微（commercial_hub） |
| #10 | 2026-04-16 | 积分模型适配（wx_points_record + eventId幂等） |
| #11 | 2026-04-17 | 前端API Key管理（/my/apikey自助 + /fbs/business/api-key运营） |
| #12 | 2026-04-17 | Skill端API对接（userId参数可选 + API Key反查） |
| 安全收口 | 2026-07-22 | userId 仅作绑定断言；关闭 points/earn 与 internal consume；幂等范围校验、企业重放失败关闭和终态 CAS |
| #13 | 2026-04-18 | commercial_hub字段补全（14个空字段写入） |
| #15 | 2026-04-20 | Skill API安全加固（HMAC-SHA256签名+时间戳防重放） |
| #16 | 2026-04-21 | FBS-BookWriter产品文档 |

---

**最后更新**: 2026-07-22
