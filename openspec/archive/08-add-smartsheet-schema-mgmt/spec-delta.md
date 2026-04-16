# Spec Delta: 企微智能表格结构管理

> **变更 ID**: `08-add-smartsheet-schema-mgmt`
> **目标规范**: `specs/platform-scene-pack-ops/spec.md`（v1.6 → v1.7）

---

## ADDED Requirements

### Requirement: 未登录用户访问

系统 SHALL 对所有结构管理接口进行身份认证，未登录用户返回 401。

#### Scenario: 未登录用户访问

```text
GIVEN SecurityContext 无用户信息
WHEN  调用任意结构管理接口
THEN  系统 SHALL 返回 401 AUTH_REQUIRED
```

---

### Requirement: 子表信息查询

系统 SHALL 支持查询指定智能表格文档中的子表列表。

> **CLI 命令**: `doc smartsheet_get_sheet`

#### Scenario: 查询子表列表成功

```text
GIVEN 管理员已登录
AND   提供有效 docid
WHEN  调用 GET /fbs/business/wecom/schema/sheets?docid={docid}
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_get_sheet
AND   返回子表列表 [{sheetId, title, rowCount}]
AND   返回 HTTP 200
```

#### Scenario: CLI 未认证

```text
GIVEN wecom-cli 未扫码认证
WHEN  调用 GET /fbs/business/wecom/schema/sheets
THEN  系统 SHALL 返回 CLI_ERROR
AND   返回 HTTP 200 + {success: false, errorCode: "CLI_ERROR"}
```

#### Scenario: 文档不存在

```text
GIVEN 提供无效 docid
WHEN  调用 GET /fbs/business/wecom/schema/sheets
THEN  系统 SHALL 返回 DOC_NOT_FOUND（CLI 返回）
AND   返回 HTTP 200 + {success: false, errorCode: "CLI_ERROR", message: "文档不存在"}
```

---

### Requirement: 子表添加

系统 SHALL 支持在指定智能表格文档中添加新子表。

> **CLI 命令**: `doc smartsheet_add_sheet`

#### Scenario: 添加子表成功

```text
GIVEN 管理员已登录
AND   提供有效 docid 和 title
WHEN  调用 POST /fbs/business/wecom/schema/sheet
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_add_sheet
AND   返回新子表的 sheetId 和 title
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200 + {sheetId, title}
```

---

### Requirement: 子表更新

系统 SHALL 支持更新指定子表的标题。

> **CLI 命令**: `doc smartsheet_update_sheet`

#### Scenario: 更新子表标题成功

```text
GIVEN 管理员已登录
AND   提供有效 docid, sheetId, title
WHEN  调用 PUT /fbs/business/wecom/schema/sheet
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_update_sheet
AND   返回更新后的 sheetId 和 title
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200
```

---

### Requirement: 子表删除

系统 SHALL 支持删除指定子表。

> **CLI 命令**: `doc smartsheet_delete_sheet`
>
> **警告**: 删除子表会删除该子表内的所有数据，不可恢复。

#### Scenario: 删除子表成功

```text
GIVEN 管理员已登录
AND   提供有效 docid 和 sheetId
WHEN  调用 DELETE /fbs/business/wecom/schema/sheet?docid={docid}&sheetId={sheetId}
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_delete_sheet
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200 + {success: true}
```

#### Scenario: 重复删除子表

```text
GIVEN 子表已被删除
WHEN  再次调用 DELETE /fbs/business/wecom/schema/sheet
THEN  系统 SHALL 返回成功（CLI 行为：幂等）
AND   返回 HTTP 200 + {success: true}
```

#### Scenario: 子表不存在

```text
GIVEN 提供无效 sheetId
WHEN  调用 DELETE /fbs/business/wecom/schema/sheet
THEN  系统 SHALL 返回 CLI_ERROR（CLI 返回 SHEET_NOT_FOUND）
AND   返回 HTTP 200 + {success: false, errorCode: "CLI_ERROR", message: "子表不存在"}
```

---

### Requirement: 字段信息查询

系统 SHALL 支持查询指定子表的字段列表。

> **CLI 命令**: `doc smartsheet_get_fields`

#### Scenario: 查询字段列表成功

```text
GIVEN 管理员已登录
AND   提供有效 docid 和 sheetId
WHEN  调用 GET /fbs/business/wecom/schema/fields?docid={docid}&sheetId={sheetId}
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_get_fields
AND   返回字段列表 [{fieldId, fieldTitle, fieldType}]
AND   返回 HTTP 200
```

---

### Requirement: 字段添加

系统 SHALL 支持在指定子表中添加新字段（列）。

> **CLI 命令**: `doc smartsheet_add_fields`
>
> **限制**: 单个子表最多 150 个字段。

#### Scenario: 添加字段成功

```text
GIVEN 管理员已登录
AND   提供有效 docid, sheetId, fields: [{fieldTitle, fieldType}]
WHEN  调用 POST /fbs/business/wecom/schema/fields
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_add_fields
AND   返回新字段列表 [{fieldId, fieldTitle, fieldType}]
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200
```

#### Scenario: 字段数量超限

```text
GIVEN 子表现有 140 个字段
AND   请求添加 15 个新字段（总计 155 > 150）
WHEN  调用 POST /fbs/business/wecom/schema/fields
THEN  系统 SHALL 返回 FIELD_LIMIT_EXCEEDED
AND   返回 HTTP 400 + {success: false, errorCode: "FIELD_LIMIT_EXCEEDED"}
```

#### Scenario: 无效字段类型

```text
GIVEN 管理员已登录
AND   请求包含无效 fieldType（如 "invalid_type"）
WHEN  调用 POST /fbs/business/wecom/schema/fields
THEN  系统 SHALL 返回 INVALID_FIELD_TYPE
AND   返回 HTTP 400 + {success: false, errorCode: "INVALID_FIELD_TYPE"}
```

---

### Requirement: 字段更新

系统 SHALL 支持更新指定字段的标题或类型。

> **CLI 命令**: `doc smartsheet_update_fields`

#### Scenario: 更新字段标题成功

```text
GIVEN 管理员已登录
AND   提供有效 docid, sheetId, fields: [{fieldId, fieldTitle}]
WHEN  调用 PUT /fbs/business/wecom/schema/fields
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_update_fields
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200 + 更新后的字段信息
```

---

### Requirement: 字段删除

系统 SHALL 支持删除指定字段。

> **CLI 命令**: `doc smartsheet_delete_fields`
>
> **警告**: 删除字段会删除该字段下的所有数据，不可恢复。

#### Scenario: 删除字段成功

```text
GIVEN 管理员已登录
AND   提供有效 docid, sheetId, fieldIds: ["f1", "f2"]
WHEN  调用 DELETE /fbs/business/wecom/schema/fields
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_delete_fields
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200 + {success: true, deletedCount: 2}
```

#### Scenario: 字段不存在

```text
GIVEN 提供无效 fieldId
WHEN  调用 DELETE /fbs/business/wecom/schema/fields
THEN  系统 SHALL 返回 CLI_ERROR（CLI 返回 FIELD_NOT_FOUND）
AND   返回 HTTP 200 + {success: false, errorCode: "CLI_ERROR", message: "字段不存在"}
```

---

### Requirement: 记录更新

系统 SHALL 支持更新指定子表中的记录数据。

> **CLI 命令**: `doc smartsheet_update_records`

#### Scenario: 更新记录成功

```text
GIVEN 管理员已登录
AND   提供有效 docid, sheetId, records: [{recordId, values: {fieldId: value}}]
WHEN  调用 PUT /fbs/business/wecom/records
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_update_records
AND   写入 fbs_wecom_sync_log（sync_type=RECORD）
AND   返回 HTTP 200 + {success: true, updatedCount: N}
```

#### Scenario: 批量更新记录

```text
GIVEN 管理员已登录
AND   提供多条记录（records 数组长度 > 1，单次最多 100 条）
WHEN  调用 PUT /fbs/business/wecom/records
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_update_records
AND   返回 HTTP 200 + {success: true, updatedCount: N}
```

#### Scenario: 记录数量超限

```text
GIVEN 管理员已登录
AND   提供超过 100 条记录
WHEN  调用 PUT /fbs/business/wecom/records
THEN  系统 SHALL 返回 RECORD_LIMIT_EXCEEDED
AND   返回 HTTP 400 + {success: false, errorCode: "RECORD_LIMIT_EXCEEDED", message: "单次操作记录数量超过限制（最多100条）"}
```

---

### Requirement: 记录删除

系统 SHALL 支持删除指定子表中的记录。

> **CLI 命令**: `doc smartsheet_delete_records`

#### Scenario: 删除记录成功

```text
GIVEN 管理员已登录
AND   提供有效 docid, sheetId, recordIds: ["r1", "r2"]
WHEN  调用 DELETE /fbs/business/wecom/records
THEN  系统 SHALL 调用 wecom-cli doc smartsheet_delete_records
AND   写入 fbs_wecom_sync_log（sync_type=RECORD）
AND   返回 HTTP 200 + {success: true, deletedCount: 2}
```

#### Scenario: 记录不存在

```text
GIVEN 提供无效 recordId
WHEN  调用 DELETE /fbs/business/wecom/records
THEN  系统 SHALL 返回 CLI_ERROR（CLI 返回 RECORD_NOT_FOUND）
AND   返回 HTTP 200 + {success: false, errorCode: "CLI_ERROR", message: "记录不存在"}
```

---

## ADDED Error Codes

| 错误码 | HTTP Status | 说明 |
|--------|-------------|------|
| FIELD_LIMIT_EXCEEDED | 400 | 字段数量超过限制（单表最多150个），系统前置校验 |
| RECORD_LIMIT_EXCEEDED | 400 | 单次操作记录数量超过限制（最多100条），系统前置校验 |
| INVALID_FIELD_TYPE | 400 | 无效的字段类型，系统前置校验 |
| DOC_NOT_FOUND | - | 文档不存在（CLI 返回，映射为 CLI_ERROR） |
| SHEET_NOT_FOUND | - | 子表不存在（CLI 返回，映射为 CLI_ERROR） |
| FIELD_NOT_FOUND | - | 字段不存在（CLI 返回，映射为 CLI_ERROR） |
| RECORD_NOT_FOUND | - | 记录不存在（CLI 返回，映射为 CLI_ERROR） |

> **注意**: `DOC_NOT_FOUND`/`SHEET_NOT_FOUND`/`FIELD_NOT_FOUND`/`RECORD_NOT_FOUND` 由 CLI 返回，Java 层统一映射为 `CLI_ERROR`，具体错误信息在 message 字段中返回。

---

## MODIFIED Requirements

### Requirement: 同步日志表扩展

`fbs_wecom_sync_log` 表的 `sync_type` 字段 SHALL 支持新值 `SCHEMA` 和 `RECORD`。

#### Scenario: 结构变更操作日志

```text
GIVEN 执行智能表格结构变更操作
WHEN  操作类型为 CREATE_DOC / ADD_SHEET / UPDATE_SHEET / DELETE_SHEET / ADD_FIELDS / UPDATE_FIELDS / DELETE_FIELDS
THEN  系统 SHALL 写入 fbs_wecom_sync_log
AND   sync_type = 'SCHEMA'
```

#### Scenario: 记录变更操作日志

```text
GIVEN 执行记录更新/删除操作
WHEN  操作类型为 UPDATE_RECORDS / DELETE_RECORDS
THEN  系统 SHALL 写入 fbs_wecom_sync_log
AND   sync_type = 'RECORD'
```

---

## ADDED Permissions

| 权限标识 | 说明 |
|----------|------|
| `business:fbs:wecom:schema:create` | 创建智能表格文档 |
| `business:fbs:wecom:schema:read` | 查询子表/字段信息 |
| `business:fbs:wecom:schema:write` | 添加/更新子表/字段 |
| `business:fbs:wecom:schema:delete` | 删除子表/字段 |
| `business:fbs:wecom:records:write` | 更新记录 |
| `business:fbs:wecom:records:delete` | 删除记录 |
