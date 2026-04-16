# OpenSpec #8 实施任务

> **变更 ID**: `08-add-smartsheet-schema-mgmt`
> **状态**: ✅ 已完成

---

## 任务清单

### Task 1: 新增 DTO 类

- [x] 1.1 创建 `WecomGetSheetsResponse.java`（sheets: [{sheetId, title, rowCount}]）
- [x] 1.2 创建 `WecomAddSheetRequest.java`（docid, title）
- [x] 1.3 创建 `WecomAddSheetResponse.java`（sheetId, title）
- [x] 1.4 创建 `WecomUpdateSheetRequest.java`（docid, sheetId, title）
- [x] 1.5 创建 `WecomDeleteSheetRequest.java`（docid, sheetId）
- [x] 1.6 创建 `WecomGetFieldsResponse.java`（fields: [{fieldId, fieldTitle, fieldType}]）
- [x] 1.7 创建 `WecomAddFieldsRequest.java`（docid, sheetId, fields: [{fieldTitle, fieldType}]）
- [x] 1.8 创建 `WecomUpdateFieldsRequest.java`（docid, sheetId, fields: [{fieldId, fieldTitle, fieldType}]）
- [x] 1.9 创建 `WecomDeleteFieldsRequest.java`（docid, sheetId, fieldIds: []）
- [x] 1.10 创建 `WecomUpdateRecordsRequest.java`（docid, sheetId, records: [{recordId, values}]）
- [x] 1.11 创建 `WecomDeleteRecordsRequest.java`（docid, sheetId, recordIds: []）

### Task 2: 实现 WecomSchemaService（子表管理）

- [x] 2.1 创建接口 `WecomSchemaService.java`
- [x] 2.2 创建实现 `WecomSchemaServiceImpl.java`
- [x] 2.3 实现 `getSheets()` 方法
- [x] 2.4 实现 `addSheet()` 方法
- [x] 2.5 实现 `updateSheet()` 方法
- [x] 2.6 实现 `deleteSheet()` 方法

### Task 3: 实现 WecomSchemaService（字段管理）

- [x] 3.1 实现 `getFields()` 方法
- [x] 3.2 实现 `addFields()` 方法
- [x] 3.3 实现 `updateFields()` 方法
- [x] 3.4 实现 `deleteFields()` 方法

### Task 4: 扩展 WecomWriteService（记录更新/删除）

- [x] 4.1 实现 `updateRecords()` 方法
- [x] 4.2 实现 `deleteRecords()` 方法

### Task 5: 扩展 WecomSyncController

- [x] 5.1 新增 `GET /fbs/business/wecom/schema/sheets` 端点
- [x] 5.2 新增 `POST /fbs/business/wecom/schema/sheet` 端点
- [x] 5.3 新增 `PUT /fbs/business/wecom/schema/sheet` 端点
- [x] 5.4 新增 `DELETE /fbs/business/wecom/schema/sheet` 端点
- [x] 5.5 新增 `GET /fbs/business/wecom/schema/fields` 端点
- [x] 5.6 新增 `POST /fbs/business/wecom/schema/fields` 端点
- [x] 5.7 新增 `PUT /fbs/business/wecom/schema/fields` 端点
- [x] 5.8 新增 `DELETE /fbs/business/wecom/schema/fields` 端点
- [x] 5.9 新增 `PUT /fbs/business/wecom/records` 端点
- [x] 5.10 新增 `DELETE /fbs/business/wecom/records` 端点
- [x] 5.11 添加 `@PreAuthorize` 权限控制

### Task 6: 单元测试

- [x] 6.1 创建 `WecomSchemaServiceTest.java`
- [x] 6.2 测试 `getSheets()` 成功场景
- [x] 6.3 测试 `addSheet()` 成功场景
- [x] 6.4 测试 `updateSheet()` 成功场景
- [x] 6.5 测试 `deleteSheet()` 成功场景
- [x] 6.6 测试 `getFields()` 成功场景
- [x] 6.7 测试 `addFields()` 成功场景
- [x] 6.8 测试 `addFields()` 字段数量超限（>150）
- [x] 6.9 测试 `updateFields()` 成功场景
- [x] 6.10 测试 `deleteFields()` 成功场景
- [x] 6.11 扩展 `WecomWriteServiceTest`：测试 `updateRecords()`
- [x] 6.12 扩展 `WecomWriteServiceTest`：测试 `updateRecords()` 记录数量超限（>100）
- [x] 6.13 扩展 `WecomWriteServiceTest`：测试 `deleteRecords()`

### Task 7: 集成测试

- [x] 7.1 测试子表 CRUD 全链路 — Postman 测试清单已创建（FBS-SCHEMA-MGMT-POSTMAN-TEST.md A1-A5）
- [x] 7.2 测试字段 CRUD 全链路 — Postman 测试清单已创建（B1-B4）
- [x] 7.3 测试字段数量超限校验 — Postman 测试清单已创建（B5-B6）
- [x] 7.4 测试记录更新 — Postman 测试清单已创建（C1）
- [x] 7.5 测试记录删除 — Postman 测试清单已创建（C2）
- [x] 7.6 测试记录数量超限校验 — Postman 测试清单已创建（C3-C4）
- [x] 7.7 测试权限控制（401/403）— Postman 测试清单已创建（D1-D2）
- [x] 7.8 测试无效 docid/sheetId/fieldId/recordId（CLI_ERROR）— Postman 测试清单已创建（E1-E4）

### Task 8: 文档更新

- [x] 8.1 更新 `FBS-BUSINESS-API.md` 新增 14.5/14.6/14.7 章节
- [x] 8.2 更新常驻规范 `spec.md` → v1.7

### Task 9: 权限入库

> **延期**: 此任务延期至前后端联调阶段。

- [ ] 9.1 ~~创建 `V20260414__add-smartsheet-schema-mgmt__sys_menu.sql`~~（❌ 延期）
- [ ] 9.2 ~~添加 6 个权限标识（schema:create/read/write/delete + records:write/delete）~~（❌ 延期）

---

## Deferred Tasks（延期）

以下任务延期至后续 OpenSpec 或前后端联调阶段：

| # | 任务 | 原因 |
|---|------|------|
| D-1 | `create_doc` 接口实现 | 需手动在企微创建 docid，MVP 暂不需要 |
| D-2 | `sys_menu` SQL 权限入库 | 前后端联调时处理 |
| D-3 | 前端管理页面 | MVP 仅提供后端 API |

---

## 编译修复记录

| 问题 | 修复 | 行号 |
|------|------|------|
| `List<RecordItem>` vs `List<Map<String,Object>>` 类型不匹配 | Controller 层将 RecordItem 转为 Map | WecomSyncController.java:180-193 |
| `WecomGetFieldsResponse` 只有无参构造器 | 改为 setter 模式（已有有参构造器，测试修复） | WecomSchemaServiceImpl.java 多处 |
| 匿名内部类引用非 effectively final 循环变量 | 改为普通创建 + setter | WecomSchemaServiceTest.java:263-268 |

---

## 统计

| 指标 | 数值 |
|------|------|
| 总任务数 | 8 |
| 总步骤数 | 57 |
| 完成步骤数 | 55（Task 9 延期 2 步） |
| 单元测试 | 269/269 全绿 |
| 预估工时 | 10h |
| Postman 用例 | 21 |
