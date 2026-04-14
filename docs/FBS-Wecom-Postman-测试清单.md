# FBS 企微同步 · Postman 集成测试清单

> **适用范围**: OpenSpec #6 企微 CLI 集成（`add-wecom-cli-integration`）MVP 验收
> **基础 URL**: `http://localhost:8080`
> **认证方式**: `Authorization: Bearer <token>`（JWT 登录后获取）
> **Content-Type**: `application/json`

---

## 一、前置条件

### 1.1 环境要求

| # | 条件 | 验证方式 |
|---|------|---------|
| 1 | 数据库已执行 `V20260413__add-wecom-cli-mvp__create_table.sql` | `SHOW TABLES LIKE 'fbs_wecom_sync_log';` |
| 2 | 应用正常启动，无报错 | 访问 `http://localhost:8080` 无 500 |
| 3 | `wecom.cli.path` 指向的文件存在（或故意不存在以测试错误路径） | 检查 `application.yml` |
| 4 | `wecom.sheet.doc-id` 已配置有效的智能表格 ID | 检查 `application.yml` |
| 5 | wecom-cli 已完成企微授权登录（或故意未登录以测试 AUTH_REQUIRED） | 命令行执行 `wecom-cli.exe --version` |

### 1.2 权限要求

测试用户需具备以下功能权限（通过 `sys_menu` + `sys_role_menu` 分配）：

| 权限码 | 说明 |
|--------|------|
| `business:fbs:wecom:sync:read` | 读取 Sheet 数据 |
| `business:fbs:wecom:sync:check` | 检测 CLI 可用性 |

> ⚠️ 若权限未分配，接口返回 HTTP 403

### 1.3 清理脚本（可选，每次完整测试前执行）

```sql
-- 清空同步日志
TRUNCATE TABLE fbs_wecom_sync_log;
```

---

## 二、测试模块总览

| # | 模块 | API 数量 | 说明 |
|---|------|---------|------|
| A | **CLI 可用性检测** | 1 | check 接口 |
| B | **读取 Sheet 数据** | 4+ | read 接口（成功 + 异常场景） |
| C | **权限与安全** | 3 | 未登录 / 无权限 / 无效方法 |
| D | **日志验证** | 2 | 数据库日志记录校验 |

---

## 三、模块 A — CLI 可用性检测

> **端点**: `POST /fbs/business/wecom/sync/check`
> **请求体**: 无
> **权限码**: `business:fbs:wecom:sync:check`

### A-1. CLI 可用（文件存在）

**前置**: `wecom.cli.path` 指向的文件确实存在

**Request:**
```
POST /fbs/business/wecom/sync/check
```

**预期:**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "cliAvailable": true,
    "cliPath": "<配置的路径>",
    "lastError": null
  }
}
```

**断言:**
- `data.cliAvailable = true`
- `data.cliPath` 非空
- `data.lastError = null`

### A-2. CLI 不可用（文件不存在）

**前置**: 临时将 `wecom.cli.path` 改为不存在的路径（如 `C:\nonexistent\wecom-cli.exe`），重启应用

**Request:**
```
POST /fbs/business/wecom/sync/check
```

**预期:**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "cliAvailable": false,
    "cliPath": "C:\\nonexistent\\wecom-cli.exe",
    "lastError": "wecom-cli 文件不存在"
  }
}
```

**断言:**
- `data.cliAvailable = false`
- `data.lastError` 包含"不存在"

> ✅ 测试后恢复正确路径并重启

### A-3. check 不写日志（验证轻量接口）

**前置**: 记录当前 `fbs_wecom_sync_log` 行数

**Request:**
```
POST /fbs/business/wecom/sync/check
```

**验证:**
```sql
SELECT COUNT(*) FROM fbs_wecom_sync_log;
-- 应与之前行数相同（check 不写日志）
```

**断言:** 日志行数未增加

---

## 四、模块 B — 读取 Sheet 数据

> **端点**: `POST /fbs/business/wecom/sync/read`
> **权限码**: `business:fbs:wecom:sync:read`

### B-1. 读取 meta（默认 Sheet）

**前置**: wecom-cli 已安装 + 已认证 + doc-id 对应的表格有 meta sheet

**Request:**
```json
{
  "sheetName": "meta"
}
```

**预期（成功）:**
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
    "snapshot": [
      { "字段1": "值1" }
    ],
    "errorCode": null,
    "errorMessage": null
  }
}
```

**断言:**
- `data.success = true`
- `data.sheetName = "meta"`
- `data.recordCount > 0`
- `data.durationMs > 0`
- `data.syncLogId > 0`
- `data.snapshot` 为数组，长度 ≤ 10
- `data.errorCode = null`

> ⚠️ 记录 `data.syncLogId`，用于模块 D 日志验证

### B-2. 读取 commercial_hub

**Request:**
```json
{
  "sheetName": "commercial_hub"
}
```

**预期:**
- `data.success = true`
- `data.sheetName = "commercial_hub"`
- `data.recordCount > 0`
- `data.snapshot` 非空

### B-3. 不传 sheetName → 默认读 meta

**Request:**
```json
{}
```

或

**Request:**
```json
{
  "sheetName": null
}
```

**预期:**
- `data.success = true`
- `data.sheetName = "meta"`（默认值）

### B-4. 空字符串 sheetName → 默认读 meta

**Request:**
```json
{
  "sheetName": ""
}
```

**预期:**
- `data.success = true`
- `data.sheetName = "meta"`

### B-5. 不在白名单的 sheetName → INVALID_SHEET

**Request:**
```json
{
  "sheetName": "unknown_sheet"
}
```

**预期:**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": false,
    "sheetName": "unknown_sheet",
    "recordCount": 0,
    "durationMs": 0,
    "syncLogId": null,
    "snapshot": null,
    "errorCode": "INVALID_SHEET",
    "errorMessage": "不支持的 sheetName: unknown_sheet，仅支持: [meta, commercial_hub]"
  }
}
```

**断言:**
- `data.success = false`
- `data.errorCode = "INVALID_SHEET"`
- `data.syncLogId = null`（不写日志）

### B-6. CLI 文件不存在 → CLI_NOT_FOUND

**前置**: 临时将 `wecom.cli.path` 改为不存在的路径

**Request:**
```json
{
  "sheetName": "meta"
}
```

**预期:**
- `data.success = false`
- `data.errorCode = "CLI_NOT_FOUND"`
- `data.syncLogId` 非空（失败也写日志）
- `data.errorMessage` 包含"不存在"

> ✅ 测试后恢复正确路径

### B-7. CLI 未认证 → AUTH_REQUIRED

**前置**: wecom-cli 文件存在但未完成扫码登录

**Request:**
```json
{
  "sheetName": "meta"
}
```

**预期:**
- `data.success = false`
- `data.errorCode = "AUTH_REQUIRED"`
- `data.errorMessage` 包含"扫码"或"登录"或"auth"

### B-8. 不传请求体 → 默认读 meta

**Request:**
```
POST /fbs/business/wecom/sync/read
Content-Type: application/json

(无请求体)
```

**预期:**
- `data.success = true`
- `data.sheetName = "meta"`

### B-9. 预览不超过 10 条

**前置**: 确保 meta sheet 有 > 10 条数据

**Request:**
```json
{
  "sheetName": "meta"
}
```

**断言:**
- `data.snapshot.length <= 10`
- `data.recordCount` 为实际总数（可能远大于 10）

---

## 五、模块 C — 权限与安全

### C-1. 未登录访问 → 401

**Request:**
```
POST /fbs/business/wecom/sync/check
(无 Authorization Header)
```

**预期:** HTTP 401 或 302（重定向登录页）

### C-2. 无功能权限 → 403

**前置**: 测试用户未分配 `business:fbs:wecom:sync:read` 权限

**Request:**
```
POST /fbs/business/wecom/sync/read
Authorization: Bearer <无权限用户的token>
```

**预期:** HTTP 403，msg 包含"权限"或"forbidden"

### C-3. GET 方法调用 → 405

**Request:**
```
GET /fbs/business/wecom/sync/read
```

**预期:** HTTP 405 Method Not Allowed

---

## 六、模块 D — 日志验证

> 验证 `fbs_wecom_sync_log` 表写入正确

### D-1. 成功读取写入日志

**前置**: B-1 执行成功，记录 `syncLogId`

**验证 SQL:**
```sql
SELECT * FROM fbs_wecom_sync_log WHERE id = <syncLogId>;
```

**断言:**
| 字段 | 预期值 |
|------|--------|
| `sync_type` | `READ` |
| `sheet_name` | `meta` |
| `status` | `SUCCESS` |
| `record_count` | > 0（与 B-1 返回值一致） |
| `duration_ms` | > 0 |
| `error_code` | NULL |
| `error_message` | NULL |
| `snapshot_json` | 非空（JSON 数组，长度 ≤ 10） |
| `created_by` | 当前登录用户名 |
| `create_time` | 近期时间 |

### D-2. 失败读取写入日志

**前置**: B-6 或 B-7 执行失败

**验证 SQL:**
```sql
SELECT * FROM fbs_wecom_sync_log 
WHERE status = 'FAILED' 
ORDER BY id DESC LIMIT 1;
```

**断言:**
| 字段 | 预期值 |
|------|--------|
| `status` | `FAILED`（或 `TIMEOUT` / `PARSE_ERROR`） |
| `error_code` | 非空（如 `CLI_NOT_FOUND` / `AUTH_REQUIRED`） |
| `error_message` | 非空 |
| `record_count` | 0 |
| `snapshot_json` | 可为 NULL |

### D-3. INVALID_SHEET 不写日志

**前置**: B-5 执行

**验证:**
```sql
SELECT COUNT(*) FROM fbs_wecom_sync_log WHERE error_code = 'INVALID_SHEET';
-- 应为 0
```

---

## 七、Postman Collection JSON

```json
{
  "info": {
    "name": "FBS企微同步集成测试",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "A-CLI可用性检测",
      "item": [
        {
          "name": "A-1 CLI可用",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/check",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ]
          }
        },
        {
          "name": "A-2 CLI不可用（文件不存在）",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/check",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ]
          }
        }
      ]
    },
    {
      "name": "B-读取Sheet数据",
      "item": [
        {
          "name": "B-1 读取meta",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"sheetName\": \"meta\"\n}"
            }
          }
        },
        {
          "name": "B-2 读取commercial_hub",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"sheetName\": \"commercial_hub\"\n}"
            }
          }
        },
        {
          "name": "B-3 不传sheetName→默认meta",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{}"
            }
          }
        },
        {
          "name": "B-4 空字符串sheetName→默认meta",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"sheetName\": \"\"\n}"
            }
          }
        },
        {
          "name": "B-5 不在白名单→INVALID_SHEET",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"sheetName\": \"unknown_sheet\"\n}"
            }
          }
        },
        {
          "name": "B-6 CLI不存在→CLI_NOT_FOUND",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"sheetName\": \"meta\"\n}"
            }
          }
        },
        {
          "name": "B-7 未认证→AUTH_REQUIRED",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"sheetName\": \"meta\"\n}"
            }
          }
        },
        {
          "name": "B-8 不传请求体→默认meta",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ]
          }
        },
        {
          "name": "B-9 预览不超过10条",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" },
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"sheetName\": \"meta\"\n}"
            }
          }
        }
      ]
    },
    {
      "name": "C-权限与安全",
      "item": [
        {
          "name": "C-1 未登录→401",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/check",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ]
          }
        },
        {
          "name": "C-2 无功能权限→403",
          "request": {
            "method": "POST",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{noPermissionToken}}" },
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"sheetName\": \"meta\"\n}"
            }
          }
        },
        {
          "name": "C-3 GET方法→405",
          "request": {
            "method": "GET",
            "url": "{{baseUrl}}/fbs/business/wecom/sync/read",
            "header": [
              { "key": "Authorization", "value": "Bearer {{token}}" }
            ]
          }
        }
      ]
    }
  ],
  "variable": [
    { "key": "baseUrl", "value": "http://localhost:8080" },
    { "key": "token", "value": "" },
    { "key": "noPermissionToken", "value": "" }
  ]
}
```

---

## 八、测试执行建议

### 执行顺序

```
A → B → D → C
│   │   │   └─ 权限测试（不依赖数据）
│   │   └─ 日志校验（依赖 B 的结果）
│   └─ 核心功能
└─ 基础检测
```

1. **A 组先行**: check 可用性确认环境就绪
2. **B 组**: 依次执行成功 → 边界 → 异常场景
3. **D 组**: B 执行后立即查库验证日志
4. **C 组**: 随时可执行，不依赖数据

### 关键变量（Postman Collection Variables）

| 变量名 | 说明 | 获取方式 |
|--------|------|---------|
| `{{baseUrl}}` | 服务地址 | 默认 `http://localhost:8080` |
| `{{token}}` | 有权限用户的 JWT | 登录接口获取 |
| `{{noPermissionToken}}` | 无权限用户的 JWT | 用无权限用户登录获取 |

### 常见失败原因排查

| 症状 | 最可能原因 |
|------|-----------|
| HTTP 403 | 未分配 `business:fbs:wecom:*` 权限 |
| `CLI_NOT_FOUND` | `wecom.cli.path` 配置错误或文件不存在 |
| `AUTH_REQUIRED` | wecom-cli 未扫码登录 |
| `PARSE_ERROR` | CLI 返回格式异常，检查 CLI 版本 |
| `INVALID_SHEET` | sheetName 不在白名单（meta/commercial_hub） |
| 空请求体 500 | Controller 接收 `@RequestBody(required=false)` 允许空，确认版本 |
| 日志未写入 | 检查 `fbs_wecom_sync_log` 表是否存在（未执行建表 SQL） |

### 环境切换说明

| 场景 | 需要修改 |
|------|---------|
| 测试 CLI 不存在 | `application.yml` → `wecom.cli.path: C:\nonexistent\wecom-cli.exe` → 重启 |
| 测试未认证 | 先执行 `wecom-cli.exe logout`（或等认证过期） |
| 恢复正常 | 改回正确路径 + 重新扫码登录 → 重启 |
