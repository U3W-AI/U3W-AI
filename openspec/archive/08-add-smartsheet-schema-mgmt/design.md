# Design: 企微智能表格结构管理

> **变更 ID**: `08-add-smartsheet-schema-mgmt`
> **日期**: 2026-04-15

---

## 1. 架构概览

本变更包含三个子模块：

| 模块 | 说明 | 复用现有 |
|------|------|----------|
| **Sheet 模块** | 子表 CRUD | 新增 `WecomSchemaService` |
| **Field 模块** | 字段 CRUD | 新增 `WecomSchemaService` |
| **Record 模块** | 补齐 #7 缺失的 update/delete | 扩展 `WecomWriteService` |

### 1.1 延期范围

以下功能**延期至后续 OpenSpec**：
- `create_doc`（创建智能表格）- 需手动在企微创建 docid
- `sys_menu` SQL 权限入库 - 前后端联调时处理

### 1.2 前置条件

调用方需自行准备：
- 有效的 `docid`（智能表格文档 ID）
- 有效的 `sheetId`（子表 ID）
- wecom-cli 已完成扫码认证

---

## 2. CLI 命令格式

所有命令统一前缀 `doc`，格式如下：

### 2.1 子表管理

| 操作 | CLI 命令 | 参数 |
|------|----------|------|
| 查询子表 | `wecom-cli doc smartsheet_get_sheet --docid {docid}` | docid |
| 添加子表 | `wecom-cli doc smartsheet_add_sheet --docid {docid} --title {title}` | docid, title |
| 更新子表 | `wecom-cli doc smartsheet_update_sheet --docid {docid} --sheet_id {sheetId} --title {title}` | docid, sheet_id, title |
| 删除子表 | `wecom-cli doc smartsheet_delete_sheet --docid {docid} --sheet_id {sheetId}` | docid, sheet_id |

### 2.2 字段管理

| 操作 | CLI 命令 | 参数 |
|------|----------|------|
| 查询字段 | `wecom-cli doc smartsheet_get_fields --docid {docid} --sheet_id {sheetId}` | docid, sheet_id |
| 添加字段 | `wecom-cli doc smartsheet_add_fields --docid {docid} --sheet_id {sheetId} --fields {json}` | docid, sheet_id, fields |
| 更新字段 | `wecom-cli doc smartsheet_update_fields --docid {docid} --sheet_id {sheetId} --fields {json}` | docid, sheet_id, fields |
| 删除字段 | `wecom-cli doc smartsheet_delete_fields --docid {docid} --sheet_id {sheetId} --field_ids {json}` | docid, sheet_id, field_ids |

#### fields 参数格式（JSON）

```json
[{"field_title": "姓名", "field_type": "text"}, {"field_title": "年龄", "field_type": "number"}]
```

#### field_ids 参数格式（JSON）

```json
["field_id_1", "field_id_2"]
```

### 2.3 记录管理（扩展 #7）

| 操作 | CLI 命令 | 参数 |
|------|----------|------|
| 更新记录 | `wecom-cli doc smartsheet_update_records --docid {docid} --sheet_id {sheetId} --records {json}` | docid, sheet_id, records |
| 删除记录 | `wecom-cli doc smartsheet_delete_records --docid {docid} --sheet_id {sheetId} --record_ids {json}` | docid, sheet_id, record_ids |

#### records 参数格式（JSON）

```json
[{"record_id": "r1", "values": {"f1": "value1", "f2": "value2"}}]
```

---

## 3. 错误码统一规范

### 3.1 错误码定义

| 错误码 | HTTP Status | 触发条件 | 说明 |
|--------|-------------|----------|------|
| **AUTH_REQUIRED** | 401 | SecurityContext 无用户 | 未登录（复用 #6 口径） |
| **INVALID_SHEET** | - | sheetName 不在白名单 | 仅 #6 read 场景 |
| **CLI_ERROR** | 200 | CLI 返回 errcode ≠ 0 | wecom-cli 执行失败 |
| **INVALID_REQUEST** | 400 | 请求参数缺失或格式错误 | 参数校验失败 |
| **FIELD_LIMIT_EXCEEDED** | 400 | 字段数量 > 150 | 前置校验 |
| **RECORD_LIMIT_EXCEEDED** | 400 | 记录数量 > 100 | 前置校验 |
| **INVALID_FIELD_TYPE** | 400 | field_type 不在允许列表 | 前置校验 |

> **注意**: `CLI_ERROR` 是通用错误码，表示 CLI 执行失败。具体错误信息在 `message` 字段中返回。调用方可通过 message 判断具体原因（如"无效的sheet_id"等）。

### 3.2 错误码映射表

| CLI 返回 | Java 错误码 | HTTP Status |
|----------|-------------|-------------|
| 未认证 / token 过期 | AUTH_REQUIRED | 401 |
| 参数格式错误 | INVALID_REQUEST | 400 |
| 字段数量超限（>150） | FIELD_LIMIT_EXCEEDED | 400 |
| 记录数量超限（>100） | RECORD_LIMIT_EXCEEDED | 400 |
| 无效字段类型 | INVALID_FIELD_TYPE | 400 |
| docid/sheetId/fieldId/recordId 不存在 | CLI_ERROR + message | 200 |

### 3.3 与 #6 的错误码统一

| 场景 | #6 处理方式 | #8 处理方式 |
|------|-------------|-------------|
| 未登录 | AUTH_REQUIRED (401) | 统一：AUTH_REQUIRED (401) |
| CLI 未认证 | AUTH_REQUIRED (401) | 统一：AUTH_REQUIRED (401) |
| CLI 执行失败 | CLI_ERROR (200) | 统一：CLI_ERROR (200) |
| 无效参数 | INVALID_REQUEST (400) | 统一：INVALID_REQUEST (400) |

---

## 4. 操作日志规范

### 4.1 sync_type 枚举

| sync_type | 说明 | 触发操作 |
|-----------|------|----------|
| SCHEMA | 结构变更 | add/update/delete sheet, add/update/delete fields |
| RECORD | 记录变更 | add/update/delete records |

### 4.2 日志字段映射

| 字段 | 取值 |
|------|------|
| sync_type | SCHEMA / RECORD |
| request_text | 操作类型枚举 |
| response_text | CLI 返回结果摘要 |

#### 操作类型枚举（request_text）

| 操作 | request_text |
|------|--------------|
| 添加子表 | ADD_SHEET |
| 更新子表 | UPDATE_SHEET |
| 删除子表 | DELETE_SHEET |
| 添加字段 | ADD_FIELDS |
| 更新字段 | UPDATE_FIELDS |
| 删除字段 | DELETE_FIELDS |
| 添加记录 | ADD_RECORDS |
| 更新记录 | UPDATE_RECORDS |
| 删除记录 | DELETE_RECORDS |

### 4.3 日志写入时机

| 操作 | 是否写日志 | 失败时是否写 |
|------|-----------|-------------|
| addSheet | ✅ | ✅ |
| updateSheet | ✅ | ✅ |
| deleteSheet | ✅ | ✅ |
| addFields | ✅ | ✅ |
| updateFields | ✅ | ✅ |
| deleteFields | ✅ | ✅ |
| updateRecords | ✅ | ✅ |
| deleteRecords | ✅ | ✅ |

---

## 5. 字段类型允许列表

### 5.1 支持的 field_type

| field_type | 说明 |
|------------|------|
| text | 文本 |
| number | 数字 |
| number自动编号 | 自动编号 |
| date | 日期 |
| datetime | 日期时间 |
| checkbox | 多选 |
| phone | 电话 |
| email | 邮箱 |
| url | 链接 |
| attachment | 附件 |
| member | 成员 |
| department | 部门 |
| lookup | 关联 |
| formula | 公式 |
| progress | 进度 |
| grade | 评分 |

### 5.2 前置校验逻辑

```java
private static final Set<String> ALLOWED_FIELD_TYPES = Set.of(
    "text", "number", "number自动编号", "date", "datetime",
    "checkbox", "phone", "email", "url", "attachment",
    "member", "department", "lookup", "formula", "progress", "grade"
);

public void validateFieldType(String fieldType) {
    if (!ALLOWED_FIELD_TYPES.contains(fieldType)) {
        throw new WecomException("INVALID_FIELD_TYPE", "无效的字段类型: " + fieldType);
    }
}
```

---

## 6. 数量限制

| 限制项 | 最大值 | 校验时机 |
|--------|--------|----------|
| 单个子表字段数 | 150 | addFields 前置校验 |
| 单次操作记录数 | 100 | updateRecords/deleteRecords 前置校验 |
| 单次添加字段数 | 20 | addFields 前置校验 |

### 6.1 前置校验失败处理

- **不调用 CLI**
- **不写日志**（校验失败属于请求参数问题，非业务执行）
- **直接返回错误码**

---

## 7. 删除操作安全声明

### 7.1 删除类操作的破坏性

| 操作 | 破坏范围 |
|------|----------|
| deleteSheet | 删除子表及所有数据 |
| deleteFields | 删除字段及所有数据 |
| deleteRecords | 删除记录数据 |

### 7.2 MVP 安全策略

- **后端不做额外拦截**
- **本阶段仅提供后台管理员 API，调用前需人工确认**（无前端二次确认保护层）
- **生产环境调用前需人工确认**
- **接口仅限后台管理员使用**

> 本 OpenSpec 不实现软删除、回收站、操作回滚等保护机制。

---

## 8. API 端点汇总

| 方法 | 路径 | 权限 | sync_type |
|------|------|------|-----------|
| GET | /fbs/business/wecom/schema/sheets | schema:read | - |
| POST | /fbs/business/wecom/schema/sheet | schema:write | SCHEMA |
| PUT | /fbs/business/wecom/schema/sheet | schema:write | SCHEMA |
| DELETE | /fbs/business/wecom/schema/sheet | schema:delete | SCHEMA |
| GET | /fbs/business/wecom/schema/fields | schema:read | - |
| POST | /fbs/business/wecom/schema/fields | schema:write | SCHEMA |
| PUT | /fbs/business/wecom/schema/fields | schema:write | SCHEMA |
| DELETE | /fbs/business/wecom/schema/fields | schema:delete | SCHEMA |
| PUT | /fbs/business/wecom/records | records:write | RECORD |
| DELETE | /fbs/business/wecom/records | records:delete | RECORD |

### 8.1 延期端点

| 方法 | 路径 | 原因 | 延期至 |
|------|------|------|--------|
| POST | /fbs/business/wecom/schema/doc | create_doc 延期 | 后续 OpenSpec |

---

## 9. DTO 汇总

| DTO 类 | 用途 | 字段 |
|--------|------|------|
| WecomGetSheetsResponse | 查询子表响应 | sheets: [{sheetId, title, rowCount}] |
| WecomAddSheetRequest | 添加子表请求 | docid, title |
| WecomAddSheetResponse | 添加子表响应 | sheetId, title |
| WecomUpdateSheetRequest | 更新子表请求 | docid, sheetId, title |
| WecomDeleteSheetRequest | 删除子表请求 | docid, sheetId |
| WecomGetFieldsResponse | 查询字段响应 | fields: [{fieldId, fieldTitle, fieldType}] |
| WecomAddFieldsRequest | 添加字段请求 | docid, sheetId, fields: [{fieldTitle, fieldType}] |
| WecomUpdateFieldsRequest | 更新字段请求 | docid, sheetId, fields: [{fieldId, fieldTitle, fieldType}] |
| WecomDeleteFieldsRequest | 删除字段请求 | docid, sheetId, fieldIds: [] |
| WecomUpdateRecordsRequest | 更新记录请求 | docid, sheetId, records: [{recordId, values: {}}] |
| WecomDeleteRecordsRequest | 删除记录请求 | docid, sheetId, recordIds: [] |

### 9.1 延期 DTO

| DTO 类 | 原因 |
|--------|------|
| WecomCreateDocRequest | create_doc 延期 |
| WecomCreateDocResponse | create_doc 延期 |

---

## 10. 实现文件清单

### 10.1 新增文件

```
WxFbsir-business/src/main/java/com/wx/fbsir/business/dto/business/wecom/
├── WecomGetSheetsResponse.java
├── WecomAddSheetRequest.java
├── WecomAddSheetResponse.java
├── WecomUpdateSheetRequest.java
├── WecomDeleteSheetRequest.java
├── WecomGetFieldsResponse.java
├── WecomAddFieldsRequest.java
├── WecomUpdateFieldsRequest.java
├── WecomDeleteFieldsRequest.java
├── WecomUpdateRecordsRequest.java
└── WecomDeleteRecordsRequest.java

WxFbsir-business/src/main/java/com/wx/fbsir/business/service/wecom/
├── WecomSchemaService.java（接口）
└── WecomSchemaServiceImpl.java（实现）
```

### 10.2 修改文件

```
WxFbsir-business/src/main/java/com/wx/fbsir/business/service/wecom/
└── WecomWriteService.java（扩展 updateRecords/deleteRecords）
WxFbsir-business/src/main/java/com/wx/fbsir/business/controller/wecom/
└── WecomSyncController.java（新增 9 个端点）
```

### 10.3 测试文件

```
WxFbsir-business/src/test/java/com/wx/fbsir/business/service/wecom/
└── WecomSchemaServiceTest.java
```

---

## 11. 与 #6/#7 的边界

### 11.1 复用关系

| 模块 | 复用 #6/#7 |
|------|------------|
| WecomCliService | ✅ 完全复用 |
| WecomSyncLogMapper | ✅ 完全复用 |
| 错误码规范 | ✅ 统一（AUTH_REQUIRED） |
| 日志格式 | ✅ 统一（sync_type + request_text） |

### 11.2 差异点

| 模块 | #6/#7 | #8 |
|------|-------|-----|
| read（查询） | ✅ | ✅ getSheets/getFields 复用 |
| write（添加） | ✅ | ✅ addSheet/addFields 复用 |
| update（更新） | ❌ | ✅ #8 新增 |
| delete（删除） | ❌ | ✅ #8 新增 |
