# 企微同步接口（FBS Business API）

> **变更 ID**：`06-add-wecom-cli-integration` / `07-add-smartsheet-write-mvp` / `08-add-smartsheet-schema-mgmt`
> **日期**：2026-04-15
> **范围**：MVP — Java 调用 wecom-cli + 读写智能表格 + Schema 管理 + 日志落库

---

## 14. 企微同步接口（WeCom Sync）

### 14.1 概述

通过调用本地安装的 `wecom-cli` 工具，读取企微智能表格（SmartSheet）中的数据。

**前置要求**：
- 已在服务器部署 `wecom-cli.exe`（路径通过 `wecom.cli.path` 配置）
- wecom-cli 已完成企微账号授权登录
- 智能表格 ID 已配置（`wecom.sheet.doc-id`）

**认证方式**：JWT Token（`/fbs/business/**` 均需登录）

**安全说明**：
- CLI 路径信息仅对登录用户可见（内部接口）
- 日志按次写入，业务数据不上报外部系统

---

### 14.2 读取 Sheet 数据

**端点**：`POST /fbs/business/wecom/sync/read`

**请求体**（可选）：
```json
{
  "sheetName": "meta"         // 可选，不传默认 "meta"。可传 "commercial_hub"
}
```

**成功响应**（HTTP 200）：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": true,
    "sheetName": "meta",
    "recordCount": 42,
    "durationMs": 1234,
    "syncLogId": 1,
    "snapshot": [              // 前 10 条预览
      { "字段1": "值1", "字段2": "值2" },
      ...
    ],
    "errorCode": null,
    "errorMessage": null
  }
}
```

**失败响应**（HTTP 200，业务失败）：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": false,
    "sheetName": "meta",
    "recordCount": 0,
    "durationMs": 56,
    "syncLogId": 2,
    "snapshot": null,
    "errorCode": "CLI_NOT_FOUND",
    "errorMessage": "wecom-cli 不存在: C:\\bin\\wecom-cli.exe"
  }
}
```

**业务错误码**（`errorCode`）：

| errorCode | 说明 | 处理建议 |
|-----------|------|----------|
| `CLI_NOT_FOUND` | wecom-cli.exe 文件不存在或路径未配置 | 配置正确的 `wecom.cli.path` |
| `EXEC_TIMEOUT` | CLI 执行超时（默认 30s） | 检查网络或增大 `wecom.cli.timeout-ms` |
| `NET_CONNECTION_FAILED` | 企微服务器连接失败 | 检查网络连通性 |
| `AUTH_REQUIRED` | wecom-cli 未完成授权登录 | 重新登录 wecom-cli |
| `PARSE_ERROR` | 返回的 JSON 解析失败 | 反馈给运维排查 |
| `UNKNOWN` | 未知异常 | 查看 `errorMessage` |

---

### 14.3 检测 CLI 可用性

**端点**：`POST /fbs/business/wecom/sync/check`

**请求体**：无

**可用时响应**（HTTP 200）：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "cliAvailable": true,
    "cliPath": "C:\\bin\\wecom-cli.exe",
    "lastError": null
  }
}
```

**不可用时响应**（HTTP 200）：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "cliAvailable": false,
    "cliPath": "C:\\bin\\wecom-cli.exe",
    "lastError": "wecom-cli 文件不存在"
  }
}
```

**设计说明**：
- MVP 只检测文件存在性，不检测登录态
- 不写 `fbs_wecom_sync_log`（轻量检测接口）
- JWT 保护，内部接口可接受返回 CLI 路径

---

### 14.4 写入记录到智能表格

**接口**：`POST /fbs/business/wecom/sync/write`

**权限**：`business:fbs:wecom:sync:write`

**说明**：手动触发向企微智能表格写入记录，支持 20KB 分片。

#### 请求体

```json
{
  "sheetName": "meta",
  "records": [
    {
      "values": {
        "标题": [{"type": "text", "text": "内容"}],
        "数量": 100,
        "启用": true
      }
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sheetName | String | 否 | 目标 Sheet 名称，null/空串默认 "meta"。仅允许 "meta" / "commercial_hub" |
| records | Array | 是 | 待写入记录数组，每条须包含 `values` 字段 |

**record 格式说明**（遵循 wecom-cli `smartsheet_add_records` 要求）：

| 字段类型 | 格式 | 示例 |
|----------|------|------|
| 文本 (TEXT) | `[{"type":"text","text":"内容"}]` | 数组格式，不可省略方括号 |
| 数字/货币/百分比 | 直接传值 | `100` / `0.6` |
| 复选框 (CHECKBOX) | 直接传值 | `true` / `false` |
| 单选/多选 (SELECT) | `[{"text":"选项内容"}]` | 数组格式 |
| 日期时间 (DATE_TIME) | 字符串 | `"2026-01-15 14:30:00"` |
| 手机号/邮箱 | 字符串 | `"13800138000"` |
| 成员 (USER) | `[{"user_id":"成员ID"}]` | 数组格式 |

> ⚠️ `values` 的 key 必须使用**字段标题**，不能使用字段 ID。

#### 成功响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": true,
    "sheetName": "meta",
    "totalRecords": 2,
    "writtenRecords": 2,
    "shardCount": 1,
    "durationMs": 3200,
    "syncLogId": 42
  }
}
```

#### 失败响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": false,
    "sheetName": "meta",
    "errorCode": "BIZ_PAYLOAD_TOO_LARGE",
    "errorMessage": "单条记录超过 payload 上限 (25000 > 20480 bytes)",
    "durationMs": 5,
    "syncLogId": null
  }
}
```

#### 响应口径

| 场景 | HTTP 状态码 | data.success |
|------|------------|--------------|
| 写入成功 | 200 | true |
| 空记录列表（no-op） | 200 | true（writtenRecords=0） |
| 单条记录超限 | 200 | false（errorCode=BIZ_PAYLOAD_TOO_LARGE） |
| 白名单外 Sheet | 200 | false（errorCode=INVALID_SHEET） |
| records 为 null | 200 | false（errorCode=INVALID_REQUEST） |
| CLI 不可用/执行失败 | 200 | false（透传 CLI 错误码） |
| 部分分片失败 | 200 | false（errorCode=PARTIAL_WRITE_FAILED） |
| 未登录 | 401 | 若依标准错误体 |

**设计说明**：
- 分片策略：20KB 上限（可通过 `wecom.write.max-payload-bytes` 配置），单条超限直接拒绝
- 部分分片失败时不回滚已写入分片（MVP 接受），sync_log 记录实际成功写入数
- 写入日志 sync_type=WRITE，与 #6 读取（READ）区分
- 重试由底层 WecomCliService 承担（NET_ ×1），WriteService 层不加重试

---

### 14.5 子表管理（Sheet Schema）

> **变更 ID**：`08-add-smartsheet-schema-mgmt`  
> **日期**：2026-04-15

#### 14.5.1 查询子表列表

**端点**：`GET /fbs/business/wecom/schema/sheets`

**权限**：`business:fbs:wecom:schema:read`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docid | String | 是 | 文档 ID（Query 参数） |

**成功响应**（HTTP 200）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "sheets": [
      { "sheetId": "q979lj", "title": "meta", "rowCount": 42 },
      { "sheetId": "04bLwp", "title": "commercial_hub", "rowCount": 15 }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| sheets | Array | 子表列表 |
| sheets[].sheetId | String | 子表 ID |
| sheets[].title | String | 子表标题 |
| sheets[].rowCount | Integer | 行数 |

**失败场景**：CLI 错误时返回空数组 `{"sheets":[]}`。

---

#### 14.5.2 添加子表

**端点**：`POST /fbs/business/wecom/schema/sheet`

**权限**：`business:fbs:wecom:schema:write`

**请求体**：

```json
{
  "docid": "s3_AdMAqgbHAIUCNTehd61tPS0uJg157",
  "title": "新子表名称"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docid | String | 是 | 文档 ID |
| title | String | 是 | 子表标题 |

**成功响应**（HTTP 200）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "sheetId": "newSheetId",
    "title": "新子表名称"
  }
}
```

**失败响应**：CLI 错误时返回 `{"sheetId": null, "title": null}`。

---

#### 14.5.3 更新子表标题

**端点**：`PUT /fbs/business/wecom/schema/sheet`

**权限**：`business:fbs:wecom:schema:write`

**请求体**：

```json
{
  "docid": "s3_AdMAqgbHAIUCNTehd61tPS0uJg157",
  "sheetId": "q979lj",
  "title": "更新后标题"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docid | String | 是 | 文档 ID |
| sheetId | String | 是 | 子表 ID |
| title | String | 是 | 新标题 |

**成功响应**（HTTP 200）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "sheetId": "q979lj",
    "title": "更新后标题"
  }
}
```

---

#### 14.5.4 删除子表

**端点**：`DELETE /fbs/business/wecom/schema/sheet`

**权限**：`business:fbs:wecom:schema:delete`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docid | String | 是 | 文档 ID（Query 参数） |
| sheetId | String | 是 | 子表 ID（Query 参数） |

**成功响应**（HTTP 200）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": true
}
```

> ⚠️ 删除操作幂等：对已删除的子表重复调用也返回 `true`。

---

### 14.6 字段管理（Field Schema）

> **变更 ID**：`08-add-smartsheet-schema-mgmt`  
> **日期**：2026-04-15

#### 14.6.1 查询字段列表

**端点**：`GET /fbs/business/wecom/schema/fields`

**权限**：`business:fbs:wecom:schema:read`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docid | String | 是 | 文档 ID（Query 参数） |
| sheetId | String | 是 | 子表 ID（Query 参数） |

**成功响应**（HTTP 200）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "fields": [
      { "fieldId": "fldXXX", "fieldTitle": "名称", "fieldType": "text" },
      { "fieldId": "fldYYY", "fieldTitle": "数量", "fieldType": "number" }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| fields | Array | 字段列表 |
| fields[].fieldId | String | 字段 ID |
| fields[].fieldTitle | String | 字段标题 |
| fields[].fieldType | String | 字段类型 |

**失败场景**：CLI 错误时返回空数组 `{"fields":[]}`。

---

#### 14.6.2 添加字段

**端点**：`POST /fbs/business/wecom/schema/fields`

**权限**：`business:fbs:wecom:schema:write`

**请求体**：

```json
{
  "docid": "s3_AdMAqgbHAIUCNTehd61tPS0uJg157",
  "sheetId": "q979lj",
  "fields": [
    { "fieldTitle": "新字段名", "fieldType": "text" },
    { "fieldTitle": "数量", "fieldType": "number" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docid | String | 是 | 文档 ID |
| sheetId | String | 是 | 子表 ID |
| fields | Array | 是 | 待添加的字段列表 |
| fields[].fieldTitle | String | 是 | 字段标题 |
| fields[].fieldType | String | 是 | 字段类型 |

**允许的字段类型**（白名单）：

| fieldType | 说明 |
|-----------|------|
| text | 文本 |
| number | 数字 |
| number自动编号 | 自动编号 |
| date | 日期 |
| datetime | 日期时间 |
| checkbox | 复选框 |
| phone | 手机号 |
| email | 邮箱 |
| url | 链接 |
| attachment | 附件 |
| member | 成员 |
| department | 部门 |
| lookup | 引用 |
| formula | 公式 |
| progress | 进度 |
| grade | 评分 |

**约束**：
- 单个子表最多 **150** 个字段（现有 + 新增 ≤ 150）
- 不在白名单中的 fieldType 会被拒绝（返回空 fields 数组）

**成功响应**（HTTP 200）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "fields": [
      { "fieldId": "fldNew1", "fieldTitle": "新字段名", "fieldType": "text" }
    ]
  }
}
```

**失败响应**：

| 场景 | data.fields |
|------|------------|
| 字段类型不在白名单 | `[]`（不调用 CLI） |
| 现有 + 新增 > 150 | `[]`（不调用 CLI） |
| CLI 执行失败 | `[]` |

---

#### 14.6.3 更新字段

**端点**：`PUT /fbs/business/wecom/schema/fields`

**权限**：`business:fbs:wecom:schema:write`

**请求体**：

```json
{
  "docid": "s3_AdMAqgbHAIUCNTehd61tPS0uJg157",
  "sheetId": "q979lj",
  "fields": [
    { "fieldId": "fldXXX", "fieldTitle": "更新后名称" },
    { "fieldId": "fldYYY", "fieldTitle": "数量", "fieldType": "number自动编号" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docid | String | 是 | 文档 ID |
| sheetId | String | 是 | 子表 ID |
| fields | Array | 是 | 待更新的字段列表 |
| fields[].fieldId | String | 是 | 字段 ID |
| fields[].fieldTitle | String | 否 | 新字段标题（不传则不修改） |
| fields[].fieldType | String | 否 | 新字段类型（不传则不修改；传则须在白名单内） |

**成功响应**：同添加字段。

---

#### 14.6.4 删除字段

**端点**：`DELETE /fbs/business/wecom/schema/fields`

**权限**：`business:fbs:wecom:schema:delete`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docid | String | 是 | 文档 ID（Query 参数） |
| sheetId | String | 是 | 子表 ID（Query 参数） |
| fieldIds | String[] | 是 | 待删除的字段 ID 列表（Query 参数，多值） |

**成功响应**（HTTP 200）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": 2
}
```

`data` 为成功删除的字段数量。CLI 错误时返回 `0`。

---

### 14.7 记录更新与删除

> **变更 ID**：`08-add-smartsheet-schema-mgmt`  
> **日期**：2026-04-15

#### 14.7.1 更新记录

**端点**：`PUT /fbs/business/wecom/records`

**权限**：`business:fbs:wecom:records:write`

**请求体**：

```json
{
  "docid": "s3_AdMAqgbHAIUCNTehd61tPS0uJg157",
  "sheetId": "q979lj",
  "records": [
    {
      "recordId": "recXXX",
      "values": {
        "名称": [{"type": "text", "text": "更新内容"}],
        "数量": 200
      }
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docid | String | 是 | 文档 ID |
| sheetId | String | 是 | 子表 ID |
| records | Array | 是 | 待更新记录列表（最多 100 条） |
| records[].recordId | String | 是 | 记录 ID |
| records[].values | Object | 是 | 字段值映射（fieldId → value） |

> ⚠️ 更新记录的 `values` key 使用**字段 ID**，而非字段标题（与 addRecords 不同）。

**成功响应**（HTTP 200）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": true,
    "sheetName": "meta",
    "totalRecords": 1,
    "writtenRecords": 1,
    "shardCount": 1,
    "durationMs": 1200,
    "syncLogId": 50
  }
}
```

**失败场景**：

| 场景 | HTTP 状态码 | data.success | errorCode |
|------|------------|--------------|-----------|
| 更新成功 | 200 | true | - |
| records 为 null | 200 | false | INVALID_REQUEST |
| records 为空 | 200 | true | -（noOp, writtenRecords=0） |
| 记录数 > 100 | 200 | false | RECORD_LIMIT_EXCEEDED |
| 白名单外 Sheet | 200 | false | INVALID_SHEET |
| CLI 执行失败 | 200 | false | 透传 CLI 错误码 |

---

#### 14.7.2 删除记录

**端点**：`DELETE /fbs/business/wecom/records`

**权限**：`business:fbs:wecom:records:delete`

**请求体**：

```json
{
  "docid": "s3_AdMAqgbHAIUCNTehd61tPS0uJg157",
  "sheetId": "q979lj",
  "recordIds": ["recXXX", "recYYY"]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| docid | String | 是 | 文档 ID |
| sheetId | String | 是 | 子表 ID |
| recordIds | String[] | 是 | 待删除记录 ID 列表（最多 100 条） |

**成功响应**（HTTP 200）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": true,
    "sheetName": "meta",
    "totalRecords": 2,
    "writtenRecords": 2,
    "shardCount": 1,
    "durationMs": 800,
    "syncLogId": 51
  }
}
```

**失败场景**：同更新记录（INVALID_REQUEST / RECORD_LIMIT_EXCEEDED / INVALID_SHEET / CLI 错误）。

---

## 14.8 业务数据同步（Business Sync）

> **变更 ID**：`09-add-business-sync-to-wecom`  
> **日期**：2026-04-15  
> **MVP 范围**：仅同步 `commercial_hub`（积分消费流水）

### 14.8.1 概述

业务数据同步服务将 MySQL 业务数据自动同步到企微智能表格，实现业务数据到 Skill 配置层的数据流打通。

**当前支持**：
- 积分消费流水同步到 `commercial_hub` Sheet

**触发方式**：
- 用户调用 Skill 消费积分后，自动触发同步
- 同步失败不影响业务主流程（积分已扣除）

**同步字段**（`commercial_hub`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| record_id | String | 唯一标识（UUID） |
| record_type | String | 固定值 `SKILL_USAGE` |
| user_id | Long | 用户 ID |
| genre | String | 场景包编码 |
| event | String | 固定值 `CONSUME` |
| delta | Integer | 变更量（负数） |
| balance_after | Integer | 变更后余额 |
| credits_required | Integer | 所需积分 |
| status | String | 固定值 `SUCCESS` |
| created_at | String | 时间戳（yyyy-MM-dd HH:mm:ss） |

### 14.8.2 集成方式

**调用位置**：`SkillConsumeServiceImpl.consume()` 方法成功返回前

**同步方式**：同步调用（非异步）

**失败处理**：
- 同步失败时记录日志，不影响业务
- 不阻塞业务主流程

**示例日志**：
```
同步积分消费成功 userId=1001, packCode=FBS-test-pack, writtenRecords=1
同步积分消费失败 userId=1001, packCode=FBS-test-pack, errorCode=NET_TIMEOUT, errorMsg=网络超时
同步积分消费异常 userId=1001, packCode=FBS-test-pack
```

### 14.8.3 延期内容（不在 MVP 范围）

| 同步内容 | 延期原因 |
|----------|----------|
| **entitlement**（场景包权益配置） | 需要扩展写入白名单 + 字段映射 + Upsert 逻辑 |

**entitlement 延期原因**：
1. `WecomWriteServiceImpl` 白名单只允许 `meta` + `commercial_hub`
2. `FbsScenePack` 缺少 `creditsRequired`/`trialAllowed`/`enterpriseOnly` 字段
3. 需要 Upsert 逻辑（按 genre 查询 → 存在则更新，不存在则新增）

---

## 附录 J：错误码汇总

| errorCode | 来源 | 说明 |
|-----------|------|------|
| `CLI_NOT_FOUND` | WecomCliService | wecom-cli.exe 文件不存在 |
| `EXEC_TIMEOUT` | WecomCliService | CLI 执行超时 |
| `EXEC_INTERRUPTED` | WecomCliService | 重试被中断 |
| `NET_TIMEOUT` | WecomCliService | wecom-cli 返回超时错误 |
| `NET_CONNECTION_FAILED` | WecomCliService | 连接企微服务器失败 |
| `AUTH_REQUIRED` | WecomCliService | 未授权（需扫码登录） |
| `CLI_ERROR` | WecomCliService | 其他 CLI 执行错误 |
| `PARSE_ERROR` | WecomSyncService | JSON 解析失败 |
| `INVALID_SHEET` | WecomSyncService / WecomWriteService | 不支持的 Sheet 名称 |
| `INVALID_REQUEST` | WecomWriteService | 请求参数无效（records 为 null） |
| `BIZ_PAYLOAD_TOO_LARGE` | WecomWriteService | 单条记录超过 payload 上限 |
| `PARTIAL_WRITE_FAILED` | WecomWriteService | 部分分片写入失败 |
| `RECORD_LIMIT_EXCEEDED` | WecomWriteService | 单次操作记录数量超过限制（最多100条） |

---

*本文档由 OpenSpec #6/#7/#8 变更更新，变更 ID：`06-add-wecom-cli-integration` / `07-add-smartsheet-write-mvp` / `08-add-smartsheet-schema-mgmt`*  
*最后更新：2026-04-15*
