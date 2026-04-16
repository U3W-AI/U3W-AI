# OpenSpec #7 提案（MVP）：add-smartsheet-write-mvp

> **变更 ID**：`07-add-smartsheet-write-mvp`
> **状态**：APPROVED（已实施完成，待归档）
> **日期**：2026-04-14
> **依赖**：OpenSpec #6 ✅
> **后续**：OpenSpec #8（场景包规则管理）

---

## 一句话目标

**Java 后端能通过 wecom-cli 向企微智能表格写入记录，支持 20KB 分片，结果落日志表。**

---

## Why — 为什么需要

### 核心问题

OpenSpec #6 打通了「后端 → wecom-cli → 企微智能表格」的**读取**通道。但数据流是单向的——只能读，不能写。

实际业务需要**双向**：当运营在福帮手后台操作（如审核通过一个场景包、修改规则、新增授权码配额）后，结果需要回写到企微智能表格，让企微侧的数据和福帮手保持一致。

### 不解决的问题

- 定时同步 / 异步任务队列（#8+）
- 冲突检测 / 双向 diff（#8+）
- 前端页面 / sys_menu SQL（#8）
- 业务化落库 / entitlement 映射 / genre 对齐（#8/#9）
- 分布式锁 / 并发控制（#8+）
- update_records / delete_records（后续迭代）

---

## What — 变更范围（MVP 收窄版）

### IN 范围（本阶段只做这些）

| # | 模块 | 说明 |
|---|------|------|
| 1 | **WecomWriteService** | 基于 WecomCliService 封装 `smartsheet_add_records` 写入逻辑 |
| 2 | **20KB 分片** | 单次 payload 保守估计上限 20KB（CLI 文档仅建议 500 行/次，未明确字节限制），超过时分片写入 |
| 3 | **1 个写入 API** | POST `/fbs/business/wecom/sync/write`（手动触发写入） |
| 4 | **sync_log WRITE 类型** | fbs_wecom_sync_log 表复用现有 sync_type 列写入 `'WRITE'` 值 |

### OUT 范围（明确延期，不在本次）

| # | 内容 | 延期到 |
|---|------|--------|
| 1 | smartsheet_update_records（更新已有记录） | 后续迭代 |
| 2 | smartsheet_delete_records（删除记录） | 后续迭代 |
| 3 | 字段管理（add/update/delete fields） | 后续迭代 |
| 4 | 子表管理（add/update/delete sheet） | 后续迭代 |
| 5 | 冲突检测 / 双向 diff | #8+ |
| 6 | SyncEngine 同步引擎 | #8+ |
| 7 | 定时同步 / Quartz | #8+ |
| 8 | 异步任务 / syncTaskId / 任务队列 | #8+ |
| 9 | 分布式锁 / Redis 锁 | #8+ |
| 10 | 限频策略（Rate Limiting） | #8+ |
| 11 | 分类写入（按 record_type 分类到不同 Sheet） | 后续迭代 |
| 12 | 前端页面 | #8 |
| 13 | sys_menu SQL | #8 |
| 14 | 回写触发器（业务操作自动触发回写） | #8+ |

---

## Impact — 影响分析

### 新增文件清单

| # | 文件 | 类型 | 说明 |
|---|------|------|------|
| 1 | `fbs/service/WecomWriteService.java` | 接口 | 写入服务接口（writeRecords） |
| 2 | `fbs/service/impl/WecomWriteServiceImpl.java` | 实现 | 分片写入 + 日志记录 |
| 3 | `dto/business/wecom/WecomSyncWriteRequest.java` | DTO | 写入请求（sheetName + records） |
| 4 | `dto/business/wecom/WecomSyncWriteResponse.java` | DTO | 写入响应（写入统计） |
| 5 | `test/.../WecomWriteServiceTest.java` | 测试 | 写入 10 用例 |

**总计：5 个新增文件**

### 修改文件

| 文件 | 改动 |
|------|------|
| `WecomSyncController.java` | 新增 POST `/fbs/business/wecom/sync/write` 端点 |
| `application.yml` | 新增 `wecom.write.max-payload-bytes` 配置（默认 20480） |
| `docs/api/FBS-BUSINESS-API.md` | 新增写入 API 文档 |

### 数据库

**无 DB 变更。** `fbs_wecom_sync_log.sync_type` 已是 VARCHAR(16)，可直接写入 `'WRITE'` 值。不需要新增 `key_type` 列（`smartsheet_add_records` 无此参数，字段键固定使用字段标题）。

---

## 技术方案要点

### 1. 写入流程

```
API 请求 POST /fbs/business/wecom/sync/write
  body: { sheetName: "meta", records: [...] }
    │
    ▼
WecomWriteService.writeRecords(sheetName, records)
    │
    ├── 校验 sheetName 白名单
    │
    ├── 序列化 records → JSON（CLI 格式）
    │
    ├── 单条记录超 20KB → BIZ_PAYLOAD_TOO_LARGE 拒绝
    │
    ├── 分片（总 payload > 20KB → 拆分）
    │     │
    │     ▼ 逐片调用
    │   WecomCliService.execute("doc", "smartsheet_add_records", paramsJson)
    │     paramsJson = { docid, sheet_id, records: [{values: {分片}}] }
    │
    ├── 汇总结果
    │
    └── 写入 fbs_wecom_sync_log（sync_type=WRITE）
```

### 2. CLI 参数格式（经 wecom-cli --help 实际验证）

`smartsheet_add_records` 参数 schema：

```json
{
  "docid": "文档ID（与 url 二选一）",
  "sheet_id": "子表ID（必填）",
  "records": [
    {
      "values": {
        "字段标题": value,
        "另一个字段": value
      }
    }
  ]
}
```

**关键约束**：
- **没有 `key_type` 参数**——`smartsheet_add_records` 固定使用字段标题作为 `values` 的 key。（`key_type` 仅存在于 `smartsheet_update_records` 和 `smartsheet_get_records`）
- 每条记录必须包装在 `values` 字段中，不能是扁平 Map
- 文本字段必须用 `[{ "type": "text", "text": "内容" }]` 数组格式
- 数字/货币/百分比/复选框：直接传值（`100` / `true`）
- 日期时间：字符串格式 `"YYYY-MM-DD HH:MM:SS"`

**MVP 策略**：调用方负责提供正确格式的 records。WriteService 不做字段类型校验，直接透传给 CLI。

### 3. 20KB 分片策略

```java
// 伪代码
int MAX_PAYLOAD_BYTES = 20480; // 可配置

// 前置校验：单条记录是否超限
for (Record r : records) {
    int recordSize = serialize(r).getBytes(UTF_8).length;
    if (recordSize > MAX_PAYLOAD_BYTES) {
        return fail("BIZ_PAYLOAD_TOO_LARGE", "单条记录超过 payload 上限");
    }
}

List<List<Record>> splitRecords(List<Record> records) {
    List<List<Record>> chunks = new ArrayList<>();
    List<Record> current = new ArrayList<>();
    int currentSize = 0;

    for (Record r : records) {
        int recordSize = serialize(r).getBytes(UTF_8).length;
        if (currentSize + recordSize > MAX_PAYLOAD_BYTES && !current.isEmpty()) {
            chunks.add(current);
            current = new ArrayList<>();
            currentSize = 0;
        }
        current.add(r);
        currentSize += recordSize;
    }
    if (!current.isEmpty()) chunks.add(current);
    return chunks;
}
```

**设计约束**：
- **前置校验**：先遍历检查单条记录是否超限，超限直接拒绝
- 分片按记录条数切割，不拆单条记录
- 分片之间串行执行（避免并发冲突）
- 任一分片失败 → 整体标记 FAILED，但不回滚已写入的分片（MVP 接受部分写入）
- 重试由底层 `WecomCliService.execute()` 承担（NET_ ×1），WriteService 层不加重试

### 4. 写入 API

```
POST /fbs/business/wecom/sync/write
请求体：{
  "sheetName": "meta",              // 目标 Sheet 名称
  "records": [                       // 待写入记录数组（CLI 格式）
    { "values": { "标题": [{"type":"text","text":"内容"}], "数量": 100 } },
    { "values": { "标题": [{"type":"text","text":"内容2"}], "数量": 200 } }
  ]
}
响应体：{
  "success": true,
  "sheetName": "meta",
  "totalRecords": 2,
  "writtenRecords": 2,
  "shardCount": 1,                   // 分了几片
  "durationMs": 3200,
  "syncLogId": 42
}
```

### 5. 响应格式口径

延续 #6 口径：HTTP 200 + `data.success` 字段区分业务成功/失败。

| 场景 | HTTP 状态码 | 响应体 |
|------|------------|--------|
| 写入成功 | 200 | `{code:200, data:{success:true, ...}}` |
| 业务失败（CLI 不可用、分片部分失败等） | 200 | `{code:200, data:{success:false, errorCode:"...", errorMessage:"..."}}` |
| 单条记录超限 | 200 | `{code:200, data:{success:false, errorCode:"BIZ_PAYLOAD_TOO_LARGE"}}` |
| 白名单外 Sheet | 200 | `{code:200, data:{success:false, errorCode:"INVALID_SHEET"}}` |
| 空记录列表 | 200 | `{code:200, data:{success:true, writtenRecords:0, shardCount:0}}` |
| 无权限 | 401/403 | 若依标准错误体 |

### 6. WecomCliService 复用

写入直接复用 #6 已实现的 `WecomCliService.execute()` 方法，category 仍为 `"doc"`，method 改为 `"smartsheet_add_records"`，params 改为写入参数。

**不需要修改 WecomCliService 本身**——它的设计已经是通用命令执行器。

### 7. 错误码扩展

| 错误码 | 含义 | 重试 |
|--------|------|------|
| `BIZ_PAYLOAD_TOO_LARGE` | 单条记录超过 payload 上限 | 不重试 |
| `PARTIAL_WRITE_FAILED` | 部分分片写入失败 | 不重试（已写入不回滚） |

> 注：#6 已有错误码（CLI_NOT_FOUND / EXEC_TIMEOUT / AUTH_REQUIRED / NET_* / PARSE_ERROR）继续适用写入场景。
> 注：`RATE_LIMITED` 不在 MVP 范围内，限频时依赖 CLI 底层行为直接返回失败。

---

## 验收标准（MVP 最小可跑通）

### 必须通过（P0）

- [ ] POST `/fbs/business/wecom/sync/write` 可成功写入记录到 meta Sheet
- [ ] 写入成功时 fbs_wecom_sync_log 记录 sync_type=WRITE, status=SUCCESS
- [ ] 写入失败时记录 sync_type=WRITE, status=FAILED + error_code
- [ ] 超过 20KB 的记录集被正确分片，串行写入
- [ ] 单条记录超 20KB 被拒绝并返回 BIZ_PAYLOAD_TOO_LARGE
- [ ] 白名单外 Sheet 被拒绝并返回 INVALID_SHEET
- [ ] 空记录列表返回 success=true, writtenRecords=0
- [ ] 未登录请求返回 401

### 单元测试覆盖（P0）

| 测试类 | 用例 |
|--------|------|
| **WecomWriteServiceTest**（10 用例） | 写入成功（小数据无需分片）/ 写入成功（分片×2）/ 单条超限拒绝 / 空记录 no-op / 白名单外拒绝 / CLI 失败写入 FAILED 日志 / 部分分片失败 |

**目标：10 用例全部通过**

---

## 风险与缓解

| 风险 | 可能性 | 影响 | 缓解 |
|------|--------|------|------|
| 部分分片写入成功+部分失败 | 中 | 数据不完整 | sync_log 记录 record_count（已成功写入数），writtenRecords/shardCount 在 API 响应中返回供人工排查；MVP 不做回滚 |
| wecom-cli 未认证 | 高 | 写入失败 | AUTH_REQUIRED 错误码明确返回 |
| 字段类型不匹配 | 中 | 写入报错 | MVP 策略：调用方负责数据格式正确，WriteService 透传不校验 |
| 调用方传扁平 Map 而非 values 格式 | 中 | CLI 报错 | API 文档明确 record 格式要求；CLI 报错会透传为 FAILED |
