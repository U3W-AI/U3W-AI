# FBS 企微 Schema 管理 · Postman 集成测试清单

> **适用范围**: OpenSpec #8 智能表格结构管理（`add-smartsheet-schema-mgmt`）全链路验收
> **基础 URL**: `http://localhost:8080`
> **认证方式**: Cookie/Session（登录后 Cookie 自动携带）或 `Authorization: Bearer <token>`
> **Content-Type**: `application/json`
> **前置依赖**: OpenSpec #6 CLI集成 + #7 写入MVP 已部署且验证通过

---

## 一、前置条件

### 1.1 环境要求

- [x] 后端服务已启动（WxFbsir-admin）
- [x] wecom-cli.exe 已部署且可执行（`POST /fbs/business/wecom/sync/check` → `cliAvailable: true`）
- [x] 企微智能表格已创建，docid 已配置（`wecom.sheet.doc-id`）
- [x] 当前登录用户拥有 `business:fbs:wecom:*` 权限

### 1.2 配置确认

```yaml
# application.yml 关键配置
wecom:
  cli:
    path: C:/bin/wecom-cli.exe
    timeout-ms: 30000
  sheet:
    doc-id: s3_AdMAqgbHAIUCNTehd61tPS0uJg157
    sheet-ids:
      meta: q979lj
      commercial-hub: 04bLwp
```

---

## 二、测试模块总览

| # | 模块 | API 数量 | 端点 |
|---|------|---------|------|
| A | 子表管理 (Sheet) | 4 | GET/POST/PUT/DELETE /fbs/business/wecom/schema/sheet(s) |
| B | 字段管理 (Field) | 4 | GET/POST/PUT/DELETE /fbs/business/wecom/schema/fields |
| C | 记录更新/删除 | 2 | PUT/DELETE /fbs/business/wecom/records |
| D | 权限校验 | 2 | 未登录 + 无权限 |
| E | 异常场景 | 4 | 无效ID/超限/空参数 |

---

## 三、模块 A — 子表管理

### A1. 查询子表列表

```
GET /fbs/business/wecom/schema/sheets?docid={{DOCID}}
```

**预期**：
- HTTP 200
- `data.sheets` 为数组，包含 `sheetId`/`title`/`rowCount`
- 至少包含 "meta" 和 "commercial_hub" 两个子表

**验证点**：
- [ ] 返回结构正确
- [ ] sheetId 非空
- [ ] rowCount >= 0

---

### A2. 添加子表

```
POST /fbs/business/wecom/schema/sheet
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "title": "测试子表_postman"
}
```

**预期**：
- HTTP 200
- `data.sheetId` 非空
- `data.title` = "测试子表_postman"

**验证点**：
- [ ] sheetId 已生成
- [ ] 记录返回的 sheetId 用于后续步骤

> ⚠️ 测试后需清理：使用 A4 删除此子表

---

### A3. 更新子表标题

```
PUT /fbs/business/wecom/schema/sheet
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "{{A2_SHEET_ID}}",
  "title": "测试子表_已更新"
}
```

**预期**：
- HTTP 200
- `data.sheetId` = A2 返回的 sheetId
- `data.title` = "测试子表_已更新"

**验证点**：
- [ ] 标题已更新

---

### A4. 删除子表

```
DELETE /fbs/business/wecom/schema/sheet?docid={{DOCID}}&sheetId={{A2_SHEET_ID}}
```

**预期**：
- HTTP 200
- `data` = true

**验证点**：
- [ ] 删除成功
- [ ] 再次查询子表列表，确认已删除的子表不再出现

---

### A5. 删除子表幂等验证

```
DELETE /fbs/business/wecom/schema/sheet?docid={{DOCID}}&sheetId={{A2_SHEET_ID}}
```

**预期**：
- HTTP 200
- `data` = true（重复删除也返回成功）

---

## 四、模块 B — 字段管理

### B1. 查询字段列表

```
GET /fbs/business/wecom/schema/fields?docid={{DOCID}}&sheetId={{META_SHEET_ID}}
```

**预期**：
- HTTP 200
- `data.fields` 为数组，包含 `fieldId`/`fieldTitle`/`fieldType`

**验证点**：
- [ ] 返回结构正确
- [ ] fieldId/fieldTitle/fieldType 均非空

---

### B2. 添加字段

```
POST /fbs/business/wecom/schema/fields
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "{{META_SHEET_ID}}",
  "fields": [
    { "fieldTitle": "测试字段_text", "fieldType": "text" },
    { "fieldTitle": "测试字段_number", "fieldType": "number" }
  ]
}
```

**预期**：
- HTTP 200
- `data.fields` 非空数组
- 每个字段包含生成的 `fieldId`

**验证点**：
- [ ] 返回 2 个新字段
- [ ] fieldId 已生成
- [ ] 记录返回的 fieldId 用于后续步骤

> ⚠️ 测试后需清理：使用 B4 删除这些字段

---

### B3. 更新字段

```
PUT /fbs/business/wecom/schema/fields
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "{{META_SHEET_ID}}",
  "fields": [
    { "fieldId": "{{B2_FIELD_ID_1}}", "fieldTitle": "测试字段_text_已更新" }
  ]
}
```

**预期**：
- HTTP 200
- `data.fields[0].fieldTitle` = "测试字段_text_已更新"

---

### B4. 删除字段

```
DELETE /fbs/business/wecom/schema/fields?docid={{DOCID}}&sheetId={{META_SHEET_ID}}&fieldIds={{B2_FIELD_ID_1}}&fieldIds={{B2_FIELD_ID_2}}
```

**预期**：
- HTTP 200
- `data` = 2（删除了 2 个字段）

**验证点**：
- [ ] 返回删除数量正确
- [ ] 再次查询字段列表，确认字段已删除

---

### B5. 添加字段 — 无效类型

```
POST /fbs/business/wecom/schema/fields
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "{{META_SHEET_ID}}",
  "fields": [
    { "fieldTitle": "无效字段", "fieldType": "invalid_type_xyz" }
  ]
}
```

**预期**：
- HTTP 200
- `data.fields` = []（空数组，前置校验拒绝）
- 不应有 CLI 调用

---

### B6. 添加字段 — 数量超限（前置校验）

> 需先通过 B1 确认当前字段数量，然后尝试添加使得 现有+新增 > 150

```
POST /fbs/business/wecom/schema/fields
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "{{META_SHEET_ID}}",
  "fields": [
    ...（构造 151 个字段项）
  ]
}
```

**预期**：
- HTTP 200
- `data.fields` = []（前置校验拒绝）

> 实际测试中可传 150 个字段（如果现有已有 ≥1 个），验证逻辑即可

---

## 五、模块 C — 记录更新/删除

### C1. 更新记录

> 前提：先通过 `POST /fbs/business/wecom/sync/write` 写入至少一条记录，获取 recordId

```
PUT /fbs/business/wecom/records
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "{{META_SHEET_ID}}",
  "records": [
    {
      "recordId": "{{RECORD_ID}}",
      "values": {
        "fldXXX": [{"type": "text", "text": "更新后的内容"}]
      }
    }
  ]
}
```

**预期**：
- HTTP 200
- `data.success` = true
- `data.writtenRecords` = 1

**验证点**：
- [ ] 更新成功
- [ ] syncLogId 非空

---

### C2. 删除记录

```
DELETE /fbs/business/wecom/records
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "{{META_SHEET_ID}}",
  "recordIds": ["{{RECORD_ID_1}}", "{{RECORD_ID_2}}"]
}
```

**预期**：
- HTTP 200
- `data.success` = true
- `data.writtenRecords` = 2

---

### C3. 更新记录 — records 超限

```
PUT /fbs/business/wecom/records
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "{{META_SHEET_ID}}",
  "records": [
    ...(101 条记录)
  ]
}
```

**预期**：
- HTTP 200
- `data.success` = false
- `data.errorCode` = "RECORD_LIMIT_EXCEEDED"

---

### C4. 删除记录 — recordIds 为 null

```
DELETE /fbs/business/wecom/records
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "{{META_SHEET_ID}}",
  "recordIds": null
}
```

**预期**：
- HTTP 200
- `data.success` = false
- `data.errorCode` = "INVALID_REQUEST"

---

## 六、模块 D — 权限校验

### D1. 未登录访问

```
GET /fbs/business/wecom/schema/sheets?docid={{DOCID}}
（不携带 Cookie/Token）
```

**预期**：
- HTTP 401
- 若依标准未登录响应体

---

### D2. 无权限用户访问

```
POST /fbs/business/wecom/schema/sheet
（以无 business:fbs:wecom:schema:write 权限的用户登录）
```

**预期**：
- HTTP 403
- 若依标准无权限响应体

---

## 七、模块 E — 异常场景

### E1. 无效 docid

```
GET /fbs/business/wecom/schema/sheets?docid=invalid_doc_id
```

**预期**：
- HTTP 200
- `data.sheets` = []（CLI 报错后返回空数组）

---

### E2. 无效 sheetId（字段查询）

```
GET /fbs/business/wecom/schema/fields?docid={{DOCID}}&sheetId=invalid_sheet_id
```

**预期**：
- HTTP 200
- `data.fields` = []（CLI 报错后返回空数组）

---

### E3. 无效 recordId（更新）

```
PUT /fbs/business/wecom/records
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "{{META_SHEET_ID}}",
  "records": [{ "recordId": "invalid_record_id", "values": {} }]
}
```

**预期**：
- HTTP 200
- `data.success` = false（CLI 报错）

---

### E4. 无效 sheetName（删除记录）

```
DELETE /fbs/business/wecom/records
Content-Type: application/json

{
  "docid": "{{DOCID}}",
  "sheetId": "invalid_sheet",
  "recordIds": ["rec1"]
}
```

**预期**：
- HTTP 200
- `data.success` = false
- `data.errorCode` = "INVALID_SHEET"

---

## 八、测试结果汇总

| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| A1 | 查询子表列表 | ☐ | |
| A2 | 添加子表 | ☐ | |
| A3 | 更新子表标题 | ☐ | |
| A4 | 删除子表 | ☐ | |
| A5 | 删除子表幂等 | ☐ | |
| B1 | 查询字段列表 | ☐ | |
| B2 | 添加字段 | ☐ | |
| B3 | 更新字段 | ☐ | |
| B4 | 删除字段 | ☐ | |
| B5 | 添加字段-无效类型 | ☐ | |
| B6 | 添加字段-数量超限 | ☐ | |
| C1 | 更新记录 | ☐ | |
| C2 | 删除记录 | ☐ | |
| C3 | 更新记录-超限 | ☐ | |
| C4 | 删除记录-null参数 | ☐ | |
| D1 | 未登录访问 | ☐ | |
| D2 | 无权限用户 | ☐ | |
| E1 | 无效docid | ☐ | |
| E2 | 无效sheetId | ☐ | |
| E3 | 无效recordId | ☐ | |
| E4 | 无效sheetName | ☐ | |

**总计**: 21 用例

---

*本文档由 OpenSpec #8 变更生成，变更 ID：`08-add-smartsheet-schema-mgmt`*
*最后更新：2026-04-15*
