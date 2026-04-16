# OpenSpec #8: 企微智能表格结构管理

> **状态**: ✅ 已归档
> **创建日期**: 2026-04-14
> **完成日期**: 2026-04-15
> **归档日期**: 2026-04-15
> **变更 ID**: `08-add-smartsheet-schema-mgmt`

---

## 1. Why（为什么）

### 1.1 问题背景

当前 U3W-AI 已集成企微智能表格的读取（OpenSpec #6）和写入（OpenSpec #7）能力：

- **已实现**: 读取记录（#6）、添加记录（#7）
- **缺失**: 更新记录、删除记录、子表管理、字段管理

### 1.2 目标

通过 Java 后端调用 wecom-cli，补齐 #7 缺失的能力 + 新增子表/字段管理：

| 能力组 | 操作 | MVP 范围 |
|--------|------|----------|
| **子表** | 查询 / 添加 / 更新 / 删除 | ✅ |
| **字段** | 查询 / 添加 / 更新 / 删除 | ✅ |
| **记录** | 添加（#7）/ 更新 / 删除 | ✅（update/delete 是新增） |
| **文档** | 创建 | ❌ 延期至后续 OpenSpec |

### 1.3 范围声明

本 OpenSpec **不是轻量 MVP**，包含：
- 10 个新 API 端点（不含 create_doc）
- 11 个 DTO（不含 create_doc 相关）
- 子表/字段/记录三类资源的完整 CRUD
- 限额校验（150 字段 / 100 记录）
- 结构变更日志

**明确延期**：
- `create_doc`（创建智能表格）- 需手动在企微创建 docid
- `sys_menu` SQL 权限入库 - 前后端联调时处理

---

## 2. What Changes（变更内容）

### 2.1 新增能力

| 能力 | CLI 命令 | Java 层 | REST API |
|------|----------|---------|----------|
| ~~创建智能表格~~ | ~~`doc create_doc`~~ | ~~`WecomSchemaService.createDoc()`~~ | ~~`POST /fbs/business/wecom/schema/doc`~~（❌ 延期） |
| 查询子表 | `doc smartsheet_get_sheet` | `WecomSchemaService.getSheets()` | `GET /fbs/business/wecom/schema/sheets` |
| 添加子表 | `doc smartsheet_add_sheet` | `WecomSchemaService.addSheet()` | `POST /fbs/business/wecom/schema/sheet` |
| 更新子表 | `doc smartsheet_update_sheet` | `WecomSchemaService.updateSheet()` | `PUT /fbs/business/wecom/schema/sheet` |
| 删除子表 | `doc smartsheet_delete_sheet` | `WecomSchemaService.deleteSheet()` | `DELETE /fbs/business/wecom/schema/sheet` |
| 查询字段 | `doc smartsheet_get_fields` | `WecomSchemaService.getFields()` | `GET /fbs/business/wecom/schema/fields` |
| 添加字段 | `doc smartsheet_add_fields` | `WecomSchemaService.addFields()` | `POST /fbs/business/wecom/schema/fields` |
| 更新字段 | `doc smartsheet_update_fields` | `WecomSchemaService.updateFields()` | `PUT /fbs/business/wecom/schema/fields` |
| 删除字段 | `doc smartsheet_delete_fields` | `WecomSchemaService.deleteFields()` | `DELETE /fbs/business/wecom/schema/fields` |
| 更新记录 | `doc smartsheet_update_records` | `WecomWriteService.updateRecords()` | `PUT /fbs/business/wecom/records` |
| 删除记录 | `doc smartsheet_delete_records` | `WecomWriteService.deleteRecords()` | `DELETE /fbs/business/wecom/records` |

### 2.2 新增文件

**后端**：
- `WecomSchemaService.java`（接口）
- `WecomSchemaServiceImpl.java`（实现）
- `WecomSyncController.java`（扩展，新增 10 个端点）
- `WecomWriteService.java`（扩展，新增 updateRecords/deleteRecords）
- 11 个 DTO（见 tasks.md Task 1）

**测试**：
- `WecomSchemaServiceTest.java`（单元测试）
- `WecomWriteServiceTest.java`（扩展测试）

### 2.3 数据库变更

无新增表，复用 `fbs_wecom_sync_log` 记录结构变更操作日志。

---

## 3. Impact（影响）

### 3.1 影响范围

| 影响项 | 说明 |
|--------|------|
| **常驻规范** | `specs/platform-scene-pack-ops/spec.md` → v1.7，新增第十六章「智能表格结构管理」+ 第十七章「记录 CRUD 完整化」+ 附录错误码 |
| **API 文档** | `docs/api/FBS-BUSINESS-API.md` 新增 14.5/14.6/14.7 章节 |
| **权限控制** | 新增权限标识（`business:fbs:wecom:schema:*` + `records:*`，延期至联调） |
| **sys_menu SQL** | ❌ 延期（前后端联调时处理） |
| **前端** | ❌ 延期（前后端联调时处理，本阶段仅提供后端 API） |

### 3.2 兼容性

- **向后兼容**: 不影响现有 `smartsheet_get_records` / `smartsheet_add_records` 功能
- **CLI 依赖**: wecom-cli 无需升级（已支持所需命令）

### 3.3 安全边界

- **认证**: 复用现有 JWT 认证（管理后台）
- **权限**: 新增权限标识，需管理员授权
- **审计**: 结构变更操作写入 `fbs_wecom_sync_log`（sync_type=SCHEMA/RECORD）

---

## 4. 范围边界说明

本 OpenSpec 包含两部分：

1. **结构管理（Schema）**: 子表/字段的 CRUD
2. **补齐 #7 缺失的 update/delete**: 记录的更新和删除

> **注意**: 本次不是纯 schema-only，而是"结构管理 + 补齐 #7 缺失的 update/delete"。归档后 capability 名称将反映这一范围。

## 5. 明确不在本 OpenSpec 范围

以下能力延期至后续 OpenSpec：

- **前端管理页面**
- **字段类型自动推断**（MVP 需调用方显式指定 field_type）
- **批量操作优化**（如批量删除子表）
- **操作回滚机制**
- **字段值校验**（如枚举字段的选项验证）

---

## 5. 关键设计决策

### D-1: 字段类型由调用方显式指定

`smartsheet_add_fields` / `smartsheet_update_fields` 要求每个字段指定 `field_type`（如 `text`、`number`、`date`）。MVP 不做类型推断。

**字段数量限制校验**：
1. 调用方请求添加 N 个字段
2. 系统先调用 `getFields()` 获取现有字段数 M
3. 若 M + N > 150，返回 `FIELD_LIMIT_EXCEEDED`（HTTP 400）
4. 否则继续调用 CLI `smartsheet_add_fields`

### D-2: 更新/删除操作的幂等性

- **更新子表**: 支持仅更新标题，幂等
- **删除子表**: 重复删除返回成功（CLI 行为）
- **更新字段**: 支持仅更新标题/类型，幂等
- **删除字段**: 重复删除返回成功
- **更新记录**: 按 recordId 更新，幂等
- **删除记录**: 按 recordId 删除，重复删除返回成功

### D-3: 操作日志统一记录

所有结构变更操作写入 `fbs_wecom_sync_log`：
- `sync_type = 'SCHEMA'`（子表/字段操作）
- `sync_type = 'RECORD'`（记录更新/删除）
- `request_text` = 操作类型
- `response_text` = CLI 返回结果

### D-4: 危险操作确认

以下操作具有破坏性，后端不做额外拦截：
- 删除子表（会删除子表内所有数据）
- 删除字段（会删除字段下所有数据）
- 删除记录

> **调用声明**: 本阶段仅提供后台管理员 API，调用前需人工确认（无前端二次确认保护层）。

---

## 6. 验收标准

1. **单元测试**: `WecomSchemaServiceTest` + `WecomWriteServiceTest` 所有用例通过
2. **集成测试**: 完整 CRUD 链路验证
3. **日志验证**: 操作日志正确写入 `fbs_wecom_sync_log`
4. **权限验证**: 未授权用户返回 401，无权限用户返回 403

---

## 7. 时间估算

| 任务 | 估算 |
|------|------|
| DTO + Service 实现 | 3h |
| Controller 扩展 + 权限 | 2h |
| 单元测试 | 2h |
| 集成测试 | 2h |
| 文档更新 | 1h |
| **总计** | **10h** |
