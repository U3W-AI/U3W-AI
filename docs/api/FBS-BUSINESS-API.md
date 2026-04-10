# FBS 运营业务层 API 接口文档

> **模块**：FBS 平台侧场景包与授权码运营（OpenSpec #2）
>
> **基础路径**：`/business/fbs`
>
> **说明**：本模块为 WxFbsir 管理后台提供 FBS 场景包、授权码、用户权益的运营管理接口。所有接口均需登录认证并分配相应权限字符（`business:fbs:*`）。
>
> **源码路径**：`WxFbsir-business/src/main/java/com/wx/fbsir/business/fbs/controller/business/`

---

## 目录

- [1. 场景包管理](#1-场景包管理)
  - [1.1 场景包分页列表](#11-场景包分页列表)
  - [1.2 场景包详情](#12-场景包详情)
  - [1.3 创建场景包](#13-创建场景包)
  - [1.4 编辑场景包](#14-编辑场景包)
  - [1.5 发布场景包](#15-发布场景包)
  - [1.6 下架场景包](#16-下架场景包)
- [2. 授权码管理](#2-授权码管理)
  - [2.1 授权码分页列表](#21-授权码分页列表)
  - [2.2 批量生成授权码](#22-批量生成授权码)
  - [2.3 禁用授权码](#23-禁用授权码)
  - [2.4 启用授权码](#24-启用授权码)
  - [2.5 撤销授权码](#25-撤销授权码)
- [3. 用户权益查询](#3-用户权益查询)
  - [3.1 用户-场景包分页列表](#31-用户-场景包分页列表)
  - [3.2 用户权益统计](#32-用户权益统计)
- [4. 企业管理](#4-企业管理)
  - [4.1 企业分页列表](#41-企业分页列表)
  - [4.2 企业详情](#42-企业详情)
  - [4.3 创建企业](#43-创建企业)
  - [4.4 更新企业](#44-更新企业)
  - [4.5 禁用企业](#45-禁用企业)
- [5. 企业场景包管理](#5-企业场景包管理)
  - [5.1 企业包分发](#51-企业包分发)
  - [5.2 企业包回收](#52-企业包回收)
  - [5.3 企业包详情](#53-企业包详情)
  - [5.4 企业包列表](#54-企业包列表)
- [6. 企业成员管理](#6-企业成员管理)
  - [6.1 添加成员](#61-添加成员)
  - [6.2 移除成员](#62-移除成员)
  - [6.3 成员分页列表](#63-成员分页列表)
  - [6.4 成员详情](#64-成员详情)
- [附录 A：枚举值说明](#附录-a枚举值说明)
- [附录 B：通用响应格式](#附录-b通用响应格式)

---

## 1. 场景包管理

> **Controller**：`FbsScenePackBusinessController`
>
> **基础路径**：`/business/fbs/scene-pack`

### 1.1 场景包分页列表

分页查询场景包列表，支持按状态/类型/所属者筛选。

- **路径**：`GET /business/fbs/scene-pack/list`
- **权限字符**：`business:fbs:scenePack:list`

**请求参数**（Query）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | 否 | 状态：0=草稿, 1=已发布, 2=已下架 |
| packType | Integer | 否 | 场景包类型：1=平台包, 2=企业包, 3=自定义包 |
| ownerType | Integer | 否 | 所属者类型：1=平台, 2=企业, 3=个人 |
| pageNum | Integer | 否 | 页码（默认 1） |
| pageSize | Integer | 否 | 每页大小（默认 10） |

**响应**：`TableDataInfo`

```json
{
  "total": 25,
  "rows": [
    {
      "id": 1,
      "packCode": "PACK001",
      "packName": "创作助手基础版",
      "packType": 1,
      "ownerType": 1,
      "ownerId": null,
      "description": "基础创作场景包",
      "status": 1,
      "visibleScope": "ALL",
      "pointsRuleCode": "rule_creative_basic",
      "currentVersion": "1.0.0",
      "contentSnapshot": "{}",
      "createdBy": "admin",
      "createTime": "2026-04-08 10:00:00"
    }
  ],
  "code": 200,
  "msg": "查询成功"
}
```

---

### 1.2 场景包详情

根据 ID 获取场景包详情，含积分规则名称。

- **路径**：`GET /business/fbs/scene-pack/{id}`
- **权限字符**：`business:fbs:scenePack:query`

**路径参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 场景包主键 |

**响应**：`AjaxResult<ScenePackDetailResponse>`

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "packCode": "PACK001",
    "packName": "创作助手基础版",
    "packType": 1,
    "packTypeDesc": "平台包",
    "ownerType": 1,
    "ownerId": null,
    "description": "基础创作场景包",
    "status": 1,
    "statusDesc": "已发布",
    "visibleScope": "ALL",
    "pointsRuleCode": "rule_creative_basic",
    "pointsRuleName": "创作助手基础积分规则",
    "currentVersion": "1.0.0",
    "contentSnapshot": "{}",
    "userCount": null,
    "createdBy": "admin",
    "createTime": "2026-04-08 10:00:00"
  }
}
```

**业务异常**

| 场景 | code | msg |
|------|------|-----|
| 场景包不存在 | 500 | 场景包不存在 |

---

### 1.3 创建场景包

新建一个场景包（草稿状态）。

- **路径**：`POST /business/fbs/scene-pack`
- **权限字符**：`business:fbs:scenePack:add`
- **操作日志**：`FBS场景包`（INSERT）

**请求体**：`ScenePackCreateRequest`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| packCode | String | **是** | 场景包编码（业务唯一键） |
| packName | String | **是** | 场景包名称 |
| packType | Integer | 否 | 场景包类型：1=平台包, 2=企业包, 3=自定义包（默认 1） |
| ownerType | Integer | 否 | 所属者类型：1=平台, 2=企业, 3=个人（默认 1） |
| ownerId | Long | 否 | 所属者ID（ownerType=1 时为 NULL） |
| description | String | 否 | 场景包描述 |
| visibleScope | String | 否 | 可见范围：ALL/PRIVATE（默认 ALL） |
| pointsRuleCode | String | 否 | 关联积分规则编码（NULL=免费包） |
| contentSnapshot | String | 否 | 内容快照 JSON |

**请求示例**

```json
{
  "packCode": "PACK_NEW_001",
  "packName": "新版创作助手",
  "packType": 1,
  "ownerType": 1,
  "description": "全新升级的创作助手场景包",
  "visibleScope": "ALL",
  "pointsRuleCode": "rule_creative_v2",
  "contentSnapshot": "{\"skills\": [\"write\", \"edit\"]}"
}
```

**响应**：`AjaxResult`

```json
{
  "code": 200,
  "msg": "创建成功",
  "data": 16
}
```

**业务异常**

| 场景 | code | msg |
|------|------|-----|
| packCode 为空 | 500 | packCode不能为空 |
| packName 为空 | 500 | packName不能为空 |

---

### 1.4 编辑场景包

编辑场景包信息。**已下架（status=2）的场景包不可编辑**。

- **路径**：`PUT /business/fbs/scene-pack`
- **权限字符**：`business:fbs:scenePack:edit`
- **操作日志**：`FBS场景包`（UPDATE）

**请求体**：`ScenePackUpdateRequest`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | **是** | 场景包主键 |
| packName | String | 否 | 场景包名称 |
| description | String | 否 | 场景包描述 |
| visibleScope | String | 否 | 可见范围：ALL/PRIVATE |
| pointsRuleCode | String | 否 | 关联积分规则编码（NULL=免费包） |
| contentSnapshot | String | 否 | 内容快照 JSON |

**请求示例**

```json
{
  "id": 1,
  "packName": "创作助手基础版（更新）",
  "description": "更新描述",
  "pointsRuleCode": "rule_creative_basic_v2"
}
```

**响应**：`AjaxResult`

```json
{
  "code": 200,
  "msg": "编辑成功"
}
```

**业务异常**

| 场景 | code | msg |
|------|------|-----|
| id 为空 | 500 | id不能为空 |
| 场景包不存在或已下架 | 500 | 编辑失败（场景包不存在或已下架不可编辑） |

> **Fail-Closed**：status=2（已下架）时编辑失败。

---

### 1.5 发布场景包

将草稿状态的场景包发布为已发布状态。

- **路径**：`PUT /business/fbs/scene-pack/publish`
- **权限字符**：`business:fbs:scenePack:publish`
- **操作日志**：`FBS场景包-发布`（UPDATE）

**请求体**

```json
{ "id": 1 }
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | **是** | 场景包主键 |

**响应**：`AjaxResult`

```json
{
  "code": 200,
  "msg": "发布成功"
}
```

**业务异常**

| 场景 | code | msg |
|------|------|-----|
| id 为空 | 500 | id不能为空 |
| 非草稿状态 | 500 | 发布失败（仅草稿状态可发布） |

> **Fail-Closed**：仅 status=0（草稿）可发布。

---

### 1.6 下架场景包

将已发布状态的场景包下架。

- **路径**：`PUT /business/fbs/scene-pack/unpublish`
- **权限字符**：`business:fbs:scenePack:unpublish`
- **操作日志**：`FBS场景包-下架`（UPDATE）

**请求体**

```json
{ "id": 1 }
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | **是** | 场景包主键 |

**响应**：`AjaxResult`

```json
{
  "code": 200,
  "msg": "下架成功"
}
```

**业务异常**

| 场景 | code | msg |
|------|------|-----|
| id 为空 | 500 | id不能为空 |
| 非已发布状态 | 500 | 下架失败（仅已发布状态可下架） |

> **Fail-Closed**：仅 status=1（已发布）可下架。已下架（status=2）为终态，不可再次发布或编辑（OpenSpec MVP 范围）。

---

## 2. 授权码管理

> **Controller**：`FbsAuthCodeBusinessController`
>
> **基础路径**：`/business/fbs/auth-code`

> **注意**：`available`（启用状态）与 `status`（使用状态）是两个**独立维度**：
>
> - `available`：管理层面开关，0=禁用，1=启用
> - `status`：使用状态，0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销

### 2.1 授权码分页列表

分页查询授权码列表，支持按目标ID/启用状态/使用状态/发放者类型筛选。

- **路径**：`GET /business/fbs/auth-code/list`
- **权限字符**：`business:fbs:authCode:list`

**请求参数**（Query）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| targetId | Long | 否 | 关联目标ID（场景包ID） |
| available | Integer | 否 | 启用状态：0=禁用, 1=启用 |
| status | Integer | 否 | 使用状态：0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销 |
| issuerType | Integer | 否 | 发放者类型：1=平台, 2=企业, 3=用户 |
| pageNum | Integer | 否 | 页码（默认 1） |
| pageSize | Integer | 否 | 每页大小（默认 10） |

**响应**：`TableDataInfo`

```json
{
  "total": 100,
  "rows": [
    {
      "id": 1,
      "authCode": "ABCD1234EFGH5678",
      "codeType": 1,
      "targetType": "SCENE_PACK",
      "targetId": 1,
      "issuerType": 1,
      "issuerId": null,
      "available": 1,
      "status": 0,
      "deadline": null,
      "maxActivations": 1,
      "activatedCount": 0,
      "description": "新用户礼包",
      "createdBy": "admin",
      "createTime": "2026-04-08 10:00:00"
    }
  ],
  "code": 200,
  "msg": "查询成功"
}
```

---

### 2.2 批量生成授权码

批量生成 N 个授权码。

- **路径**：`POST /business/fbs/auth-code/generate`
- **权限字符**：`business:fbs:authCode:generate`
- **操作日志**：`FBS授权码-生成`（INSERT）

**请求体**：`AuthCodeGenerateRequest`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| targetType | String | 否 | 关联目标类型：SCENE_PACK/GENERIC（默认 SCENE_PACK） |
| targetId | Long | **是** | 关联目标ID（如场景包ID） |
| issuerType | Integer | 否 | 发放者类型：1=平台, 2=企业, 3=用户（默认 1） |
| issuerId | Long | 否 | 发放者ID |
| maxActivations | Integer | 否 | 最大激活次数（默认 1） |
| deadline | Date | 否 | 截止时间，NULL=不限 |
| description | String | 否 | 说明/备注 |
| count | Integer | 否 | 生成数量（默认 1，最大 1000） |

**请求示例**

```json
{
  "targetType": "SCENE_PACK",
  "targetId": 1,
  "issuerType": 1,
  "maxActivations": 1,
  "description": "2026年4月推广礼包",
  "count": 10
}
```

**响应**：`AjaxResult<Map<Long, String>>`

```json
{
  "code": 200,
  "msg": "生成成功",
  "data": {
    "1": "A1B2C3D4E5F6G7H8",
    "2": "I9J0K1L2M3N4O5P6",
    "3": "Q7R8S9T0U1V2W3X4"
  }
}
```

> **说明**：`Map<Long, String>` 的 Key=授权码ID，Value=授权码字符串。授权码格式为 UUID 前 16 位 + 随机后 4 位，共 20 位。

**业务异常**

| 场景 | code | msg |
|------|------|-----|
| targetId 为空 | 500 | targetId不能为空 |
| count 超过 1000 | 500 | 单次最多生成1000个授权码 |

---

### 2.3 禁用授权码

将授权码设为禁用状态（available=0），任何人无法使用。

- **路径**：`PUT /business/fbs/auth-code/disable`
- **权限字符**：`business:fbs:authCode:disable`
- **操作日志**：`FBS授权码-禁用`（UPDATE）

**请求体**

```json
{ "id": 1 }
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | **是** | 授权码主键 |

**响应**：`AjaxResult`

```json
{
  "code": 200,
  "msg": "禁用成功"
}
```

**业务异常**

| 场景 | code | msg |
|------|------|-----|
| id 为空 | 500 | id不能为空 |
| 已是禁用状态 | 500 | 禁用失败（仅启用状态可禁用） |

> **Fail-Closed**：仅 available=1（启用）可禁用。

---

### 2.4 启用授权码

将授权码设为启用状态（available=1）。

- **路径**：`PUT /business/fbs/auth-code/enable`
- **权限字符**：`business:fbs:authCode:enable`
- **操作日志**：`FBS授权码-启用`（UPDATE）

**请求体**

```json
{ "id": 1 }
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | **是** | 授权码主键 |

**响应**：`AjaxResult`

```json
{
  "code": 200,
  "msg": "启用成功"
}
```

**业务异常**

| 场景 | code | msg |
|------|------|-----|
| id 为空 | 500 | id不能为空 |
| 已是启用状态 | 500 | 启用失败（仅禁用状态可启用） |
| status=2/3/4（已用尽/已过期/已撤销） | 500 | 启用失败（仅禁用状态且未用尽/未过期/未撤销的授权码可启用） |

> **Fail-Closed**：仅 `available=0 AND status IN (0,1)` 可启用。status=2/3/4 均不可启用。

---

### 2.5 撤销授权码

将授权码状态设为已撤销（status=4），**不可逆**。

- **路径**：`PUT /business/fbs/auth-code/revoke`
- **权限字符**：`business:fbs:authCode:revoke`
- **操作日志**：`FBS授权码-撤销`（UPDATE）

**请求体**

```json
{ "id": 1 }
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | **是** | 授权码主键 |

**响应**：`AjaxResult`

```json
{
  "code": 200,
  "msg": "撤销成功"
}
```

**业务异常**

| 场景 | code | msg |
|------|------|-----|
| id 为空 | 500 | id不能为空 |
| 已撤销 | 500 | 撤销失败（已撤销的授权码不可重复撤销） |

> **Fail-Closed**：仅 `status != 4`（未撤销）可撤销。status=4 为终态，不可重复撤销。

---

## 3. 用户权益查询

> **Controller**：`FbsUserPackBusinessController`
>
> **基础路径**：`/business/fbs/user-pack`

> **注意**：`fbs_user_pack.status` 与 `fbs_auth_code.status` 是两个不同维度。
>
> - `fbs_user_pack.status`：用户权益状态，1=有效, 2=已过期, 3=已撤销（无 exhaustedCount）
> - `fbs_auth_code.status`：授权码使用状态，0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销

### 3.1 用户-场景包分页列表

分页查询用户-场景包关联列表，支持按用户ID/权益状态筛选。

- **路径**：`GET /business/fbs/user-pack/list`
- **权限字符**：`business:fbs:userPack:list`

**请求参数**（Query）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 否 | 用户ID |
| status | Integer | 否 | 权益状态：1=有效, 2=已过期, 3=已撤销 |
| pageNum | Integer | 否 | 页码（默认 1） |
| pageSize | Integer | 否 | 每页大小（默认 10） |

**响应**：`TableDataInfo`

```json
{
  "total": 5,
  "rows": [
    {
      "id": 1,
      "userId": 100,
      "packId": 1,
      "packName": "创作助手基础版",
      "packVersion": "1.0.0",
      "authCodeId": 5,
      "activatedAt": "2026-04-08 10:30:00",
      "expiresAt": null,
      "status": 1,
      "sourceType": 3,
      "createdBy": "admin",
      "createTime": "2026-04-08 10:30:00"
    }
  ],
  "code": 200,
  "msg": "查询成功"
}
```

---

### 3.2 用户权益统计

获取指定用户的权益统计：总数、有效数、已过期数、已撤销数。

- **路径**：`GET /business/fbs/user-pack/stats`
- **权限字符**：`business:fbs:userPack:query`

**请求参数**（Query）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | **是** | 用户ID |

**响应**：`AjaxResult<UserPackStatsResponse>`

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": 100,
    "totalCount": 5,
    "activeCount": 3,
    "expiredCount": 1,
    "revokedCount": 1
  }
}
```

**业务异常**

| 场景 | code | msg |
|------|------|-----|
| userId 为空 | 500 | userId不能为空 |

---

## 4. 企业管理

> **Controller**：`FbsEnterpriseBusinessController`
>
> **基础路径**：`/business/fbs/enterprise`
>
> **说明**：企业是场景包分发的组织单位。企业管理员可在后台创建/管理企业，并分发场景包给企业成员使用。

### 4.1 企业分页列表

- **路径**：`GET /business/fbs/enterprise/list`
- **权限字符**：`business:fbs:enterprise:list`

**请求参数**（Query）

| 参数 | 类型 | 必填 | 说明 |
:|------|------|------|------|
| enterpriseName | String | 否 | 企业名称（模糊匹配） |
| status | Integer | 否 | 状态：1=正常, 2=已禁用 |
| pageNum | Integer | 否 | 页码（默认 1） |
| pageSize | Integer | 否 | 每页大小（默认 10） |

**响应**：`TableDataInfo`

```json
{
  "total": 10,
  "rows": [
    {
      "id": 1,
      "enterpriseName": "悟空科技",
      "enterpriseCode": "ENT_ABC12345",
      "contactName": "张三",
      "contactPhone": "13800138000",
      "status": 1,
      "statusDesc": "正常",
      "createdBy": "admin",
      "createTime": "2026-04-09 10:00:00"
    }
  ],
  "code": 200,
  "msg": "查询成功"
}
```

---

### 4.2 企业详情

- **路径**：`GET /business/fbs/enterprise/{id}`
- **权限字符**：`business:fbs:enterprise:query`

**响应**：`AjaxResult<EnterpriseDetailResponse>`

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "enterpriseName": "悟空科技",
    "enterpriseCode": "ENT_ABC12345",
    "contactName": "张三",
    "contactPhone": "13800138000",
    "status": 1,
    "statusDesc": "正常",
    "memberCount": 15,
    "packCount": 3,
    "createdBy": "admin",
    "createTime": "2026-04-09 10:00:00"
  }
}
```

> **统计说明**：`memberCount` 和 `packCount` 仅统计 `status=1`（正常）的记录。

---

### 4.3 创建企业

- **路径**：`POST /business/fbs/enterprise`
- **权限字符**：`business:fbs:enterprise:add`
- **操作日志**：`FBS企业管理-新增`（INSERT）

**请求体**

```json
{
  "enterpriseName": "悟空科技",
  "contactName": "张三",
  "contactPhone": "13800138000",
  "contactEmail": "admin@example.com"
}
```

| 参数 | 类型 | 必填 | 说明 |
:|------|------|------|------|
| enterpriseName | String | **是** | 企业名称（唯一） |
| contactName | String | 否 | 联系人姓名 |
| contactPhone | String | 否 | 联系电话 |
| contactEmail | String | 否 | 联系邮箱 |

**响应**：`AjaxResult`

```json
{ "code": 200, "msg": "新增成功", "data": 1 }
```

**业务异常**

| 场景 | code | msg |
:|------|------|-----|
| 企业名称为空 | 500 | 企业名称不能为空 |
| 企业名称重复 | 500 | 企业名称已存在 |

---

### 4.4 更新企业

- **路径**：`PUT /business/fbs/enterprise`
- **权限字符**：`business:fbs:enterprise:edit`
- **操作日志**：`FBS企业管理-编辑`（UPDATE）

**响应**：`AjaxResult`

```json
{ "code": 200, "msg": "修改成功" }
```

---

### 4.5 禁用企业

将企业状态设为已禁用（status=2）。禁用后企业成员无法使用企业配额。

- **路径**：`PUT /business/fbs/enterprise/disable`
- **权限字符**：`business:fbs:enterprise:disable`
- **操作日志**：`FBS企业管理-禁用`（UPDATE）

**请求体**

```json
{ "id": 1 }
```

**响应**：`AjaxResult`

```json
{ "code": 200, "msg": "禁用成功" }
```

**业务异常**

| 场景 | code | msg |
:|------|------|-----|
| 企业不存在 | 500 | 企业不存在 |
| 已禁用 | 500 | 禁用失败（仅正常状态可禁用） |

> **Fail-Closed**：仅 `status=1`（正常）可禁用。status=2 不可重复禁用。

---

## 5. 企业场景包管理

> **Controller**：`FbsEnterprisePackBusinessController`
>
> **基础路径**：`/business/fbs/enterprise/pack`
>
> **说明**：平台管理员向指定企业分发/回收场景包授权。企业包授权后，所有正常企业成员均可使用该配额。

### 5.1 企业包分发

向企业分发场景包授权。**幂等分发**：已授权返回已有；已回收可重新授权。

- **路径**：`POST /business/fbs/enterprise/pack`
- **权限字符**：`business:fbs:enterprise:pack:grant`
- **操作日志**：`FBS企业包-分发`（INSERT）

**请求体**

```json
{
  "enterpriseId": 1,
  "packCode": "PACK_BOOK_WRITER",
  "packQuota": 100,
  "expiryTime": "2027-04-09 23:59:59"
}
```

| 参数 | 类型 | 必填 | 说明 |
:|------|------|------|------|
| enterpriseId | Long | **是** | 企业ID |
| packCode | String | **是** | 场景包编码 |
| packQuota | Integer | **是** | 配额数量（正整数） |
| expiryTime | String | 否 | 过期时间（格式：yyyy-MM-dd HH:mm:ss） |

**响应**：`AjaxResult`

```json
{ "code": 200, "msg": "分发成功", "data": 1 }
```

**幂等规则**

| 当前状态 | 行为 |
|------|------|
| 不存在 | 创建新记录（usedQuota=0） |
| `status=1`（已授权） | 返回现有记录（幂等） |
| `status=2`（已到期） | 重新激活（usedQuota 重置为 0） |
| `status=3`（已回收） | 重新授权（usedQuota 重置为 0） |

---

### 5.2 企业包回收

回收企业的场景包授权，级联将所有成员授权记录设为已撤销（status=4）。

- **路径**：`PUT /business/fbs/enterprise/pack/revoke`
- **权限字符**：`business:fbs:enterprise:pack:revoke`
- **操作日志**：`FBS企业包-回收`（UPDATE）

**请求体**

```json
{ "id": 1 }
```

**响应**：`AjaxResult`

```json
{ "code": 200, "msg": "回收成功" }
```

> **Fail-Closed**：仅 `status=1`（正常）可回收。`status != 1` 均不可回收。
>
> **级联影响**：回收操作将 `fbs_member_pack` 对应企业包的所有记录状态设为 4（已撤销）。

---

### 5.3 企业包详情

- **路径**：`GET /business/fbs/enterprise/pack/{id}`
- **权限字符**：`business:fbs:enterprise:pack:query`

**响应**：`AjaxResult<EnterprisePackDetailResponse>`

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "enterpriseId": 1,
    "enterpriseName": "悟空科技",
    "packCode": "PACK_BOOK_WRITER",
    "packName": "创作助手",
    "packQuota": 100,
    "usedQuota": 45,
    "remainQuota": 55,
    "expiryTime": "2027-04-09 23:59:59",
    "status": 1,
    "statusDesc": "已授权"
  }
}
```

> **remainQuota 计算**：`packQuota - usedQuota`（实时计算，不存储独立字段）。

---

### 5.4 企业包列表

- **路径**：`GET /business/fbs/enterprise/pack/list`
- **权限字符**：`business:fbs:enterprise:pack:list`

**请求参数**（Query）

| 参数 | 类型 | 必填 | 说明 |
:|------|------|------|------|
| enterpriseId | Long | **是** | 企业ID |
| status | Integer | 否 | 状态：1=已授权, 2=已到期, 3=已回收 |

**响应**：`TableDataInfo`

```json
{
  "total": 3,
  "rows": [
    {
      "id": 1,
      "enterpriseId": 1,
      "enterpriseName": "悟空科技",
      "packCode": "PACK_BOOK_WRITER",
      "packName": "创作助手",
      "packQuota": 100,
      "usedQuota": 45,
      "remainQuota": 55,
      "status": 1,
      "statusDesc": "已授权"
    }
  ],
  "code": 200,
  "msg": "查询成功"
}
```

---

## 6. 企业成员管理

> **Controller**：`FbsEnterpriseMemberBusinessController`
>
> **基础路径**：`/business/fbs/enterprise/member`
>
> **说明**：管理企业的成员。添加成员时自动继承企业所有已授权场景包；移除成员时授权记录保留（消费时 fail-closed）。

### 6.1 添加成员

将用户添加为企业成员，自动继承企业所有已授权场景包。

- **路径**：`POST /business/fbs/enterprise/member`
- **权限字符**：`business:fbs:enterprise:member:add`
- **操作日志**：`FBS企业成员-添加`（INSERT）

**请求体**

```json
{
  "enterpriseId": 1,
  "userId": 100,
  "role": "MEMBER"
}
```

| 参数 | 类型 | 必填 | 说明 |
:|------|------|------|------|
| enterpriseId | Long | **是** | 企业ID |
| userId | Long | **是** | 用户ID（`sys_user.user_id`） |
| role | String | 否 | 角色：MEMBER（默认）, ADMIN |

**响应**：`AjaxResult`

```json
{ "code": 200, "msg": "添加成功", "data": 3001 }
```

**自动继承规则**：新成员自动继承企业所有 `status=1`（已授权）的场景包，在 `fbs_member_pack` 中创建授权记录。已移除成员重新添加时恢复为 `status=1`。

---

### 6.2 移除成员

将企业成员状态设为已移除（status=2）。授权记录**保留不删除**，消费时 fail-closed。

- **路径**：`DELETE /business/fbs/enterprise/member/{id}`
- **权限字符**：`business:fbs:enterprise:member:remove`
- **操作日志**：`FBS企业成员-移除`（UPDATE）

**响应**：`AjaxResult`

```json
{ "code": 200, "msg": "移除成功" }
```

> **Fail-Closed**：仅 `status=1`（正常）可移除。移除成员**不删除** `fbs_member_pack` 授权记录。

---

### 6.3 成员分页列表

- **路径**：`GET /business/fbs/enterprise/member/list`
- **权限字符**：`business:fbs:enterprise:member:list`

**请求参数**（Query）

| 参数 | 类型 | 必填 | 说明 |
:|------|------|------|------|
| enterpriseId | Long | **是** | 企业ID |
| status | Integer | 否 | 状态：1=正常, 2=已移除 |

**响应**：`TableDataInfo`

```json
{
  "total": 15,
  "rows": [
    {
      "id": 3001,
      "enterpriseId": 1,
      "userId": 100,
      "userName": "张三",
      "role": "MEMBER",
      "joinTime": "2026-04-09 10:00:00",
      "status": 1,
      "statusDesc": "正常"
    }
  ],
  "code": 200,
  "msg": "查询成功"
}
```

---

### 6.4 成员详情

- **路径**：`GET /business/fbs/enterprise/member/{id}`
- **权限字符**：`business:fbs:enterprise:member:query`

**响应**：`AjaxResult<EnterpriseMemberDetailResponse>`

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 3001,
    "enterpriseId": 1,
    "enterpriseName": "悟空科技",
    "userId": 100,
    "userName": "张三",
    "role": "MEMBER",
    "joinTime": "2026-04-09 10:00:00",
    "status": 1,
    "statusDesc": "正常",
    "inheritedPacks": [
      {
        "packCode": "PACK_BOOK_WRITER",
        "packName": "创作助手",
        "grantTime": "2026-04-09 10:00:00",
        "expiryTime": "2027-04-09 23:59:59",
        "status": 1,
        "statusDesc": "正常"
      }
    ]
  }
}
```

---

## 附录 A：枚举值说明

### A.1 场景包状态（`fbs_scene_pack.status`）

| 值 | 说明 | 状态说明 |
|----|------|---------|
| 0 | 草稿 | 可编辑、可发布 |
| 1 | 已发布 | 可下架、可编辑 |
| 2 | 已下架 | **终态**，不可编辑、不可再次发布 |

### A.2 授权码启用状态（`fbs_auth_code.available`）

| 值 | 说明 | 说明 |
|----|------|------|
| 0 | 禁用 | 任何人无法使用 |
| 1 | 启用 | 符合条件可使用 |

### A.3 授权码使用状态（`fbs_auth_code.status`）

| 值 | 说明 | 触发条件 |
|----|------|---------|
| 0 | 未激活 | 初始状态 |
| 1 | 已激活 | 用户首次激活 |
| 2 | 已用尽 | `activated_count >= max_activations` |
| 3 | 已过期 | 超过 `deadline` |
| 4 | 已撤销 | 平台管理员撤销（不可逆） |

### A.4 用户权益状态（`fbs_user_pack.status`）

| 值 | 说明 |
|----|------|
| 1 | 有效 |
| 2 | 已过期 |
| 3 | 已撤销 |

### A.5 场景包类型（`fbs_scene_pack.pack_type`）

| 值 | 说明 |
|----|------|
| 1 | 平台包 |
| 2 | 企业包 |
| 3 | 自定义包 |

### A.6 所属者类型（`fbs_scene_pack.owner_type`）

| 值 | 说明 |
|----|------|
| 1 | 平台 |
| 2 | 企业 |
| 3 | 个人 |

### A.7 发放者类型（`fbs_auth_code.issuer_type`）

| 值 | 说明 |
|----|------|
| 1 | 平台 |
| 2 | 企业 |
| 3 | 用户 |

### A.8 权益来源类型（`fbs_user_pack.source_type`）

| 值 | 说明 |
|----|------|
| 1 | 平台分发 |
| 2 | 企业分发 |
| 3 | 用户激活（通过授权码） |

### A.9 企业状态（`fbs_enterprise.status`）

| 值 | 说明 |
|----|------|
| 1 | 正常 |
| 2 | 已禁用 |

### A.10 企业包状态（`fbs_enterprise_pack.status`）

| 值 | 说明 | 触发条件 |
|----|------|---------|
| 1 | 已授权 | 正常可用，企业成员可消费 |
| 2 | 已到期 | 超过 `expiry_time` |
| 3 | 已回收 | 管理员主动回收 |

### A.11 企业成员状态（`fbs_enterprise_member.status`）

| 值 | 说明 |
|----|------|
| 1 | 正常 |
| 2 | 已移除 |

### A.12 成员包授权状态（`fbs_member_pack.status`）

| 值 | 说明 | 触发条件 |
|----|------|---------|
| 1 | 正常 | 自动继承企业活跃包 |
| 2 | 已移除 | 对应成员已被移除 |
| 4 | 已撤销 | 对应企业包被回收 |

### A.13 消费主机类型（`fbs_skill_usage_record.host_type`）

| 值 | 说明 |
|----|------|
| WORKBUDDY | 个人授权路径（扣个人积分） |
| ENTERPRISE | 企业配额路径（扣企业配额，不扣个人积分） |

### A.14 配额消费流程说明

- **hostType=WORKBUDDY**：用户个人授权 → 校验 `fbs_user_pack` 状态 → 扣减个人积分
- **hostType=ENTERPRISE**：查找企业成员 → 校验 `fbs_enterprise.status=1` → 校验 `fbs_enterprise_pack` 状态和配额 → `usedQuota++`（原子操作）

> **重要**：两条路径**完全独立**，互不 fallback。

---

## 附录 B：通用响应格式

### B.1 AjaxResult（单对象）

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": { ... }
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 500 | 业务失败（见各接口 msg 描述） |

### B.2 TableDataInfo（分页列表）

```json
{
  "total": 100,
  "rows": [ ... ],
  "code": 200,
  "msg": "查询成功"
}
```

| 字段 | 含义 |
|------|------|
| total | 总记录数 |
| rows | 当前页数据列表 |
| code | 同 AjaxResult.code |
| msg | 同 AjaxResult.msg |

---

**最后更新**：2026-04-09（OpenSpec #2 + OpenSpec #3 企业场景包运营实施完成）
