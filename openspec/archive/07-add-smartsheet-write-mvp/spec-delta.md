# 规范差异：add-smartsheet-write-mvp

> **变更 ID**：`07-add-smartsheet-write-mvp`
> **影响规范**：`specs/platform-scene-pack-ops/spec.md` (v1.5)
> **新增章节**：第十五章「企微智能表格写入（MVP）」
> **范围**：最简可跑通 — Java 调用 wecom-cli 写入记录到企微智能表格 + 20KB 分片 + 手动触发写入 API

---

## ADDED Requirements

### Requirement: WecomWriteService — 企微智能表格写入服务

WHEN 需要向企微智能表格写入记录,
系统 SHALL 提供 `WecomWriteService` 封装 `smartsheet_add_records` 命令调用。

#### Scenario: 写入少量记录（无需分片）

```text
GIVEN WecomCliService 可用
AND   记录总 payload ≤ 20KB
AND   所有单条记录 payload ≤ 20KB
WHEN  调用 WecomWriteService.writeRecords("meta", records)
THEN  序列化 records 为 JSON（CLI 格式：[{values: {...}}]）
AND   调用 WecomCliService.execute("doc", "smartsheet_add_records", {docid, sheet_id, records})
AND   写入 fbs_wecom_sync_log（sync_type=WRITE, status=SUCCESS, record_count=N）
AND   返回 WecomSyncWriteResponse(success=true, writtenRecords=N, shardCount=1)
```

#### Scenario: 写入大量记录（需要分片）

```text
GIVEN WecomCliService 可用
AND   所有单条记录 payload ≤ 20KB
AND   记录总 payload > 20KB
WHEN  调用 WecomWriteService.writeRecords("meta", records)
THEN  按 20KB 上限将 records 拆分为多个分片
AND   串行逐片调用 WecomCliService.execute
AND   汇总所有分片结果
AND   写入 fbs_wecom_sync_log（sync_type=WRITE, status=SUCCESS, record_count=总写入数）
AND   返回 WecomSyncWriteResponse(success=true, writtenRecords=总写入数, shardCount=M)
```

#### Scenario: 单条记录超过 20KB

```text
GIVEN WecomCliService 可用
AND   单条记录序列化后 > 20KB
WHEN  调用 WecomWriteService.writeRecords(sheetName, records)
THEN  不尝试写入（前置校验阶段即拒绝）
AND   不写入 sync_log
AND   返回 WecomSyncWriteResponse(success=false, errorCode="BIZ_PAYLOAD_TOO_LARGE")
```

#### Scenario: 空记录列表

```text
GIVEN 传入的 records 为空列表
WHEN  调用 WecomWriteService.writeRecords(sheetName, records)
THEN  直接返回 no-op
AND   不写入 sync_log
AND   返回 WecomSyncWriteResponse(success=true, writtenRecords=0, shardCount=0)
```

#### Scenario: records 为 null

```text
GIVEN 传入的 records 为 null
WHEN  调用 WecomWriteService.writeRecords(sheetName, records)
THEN  不调用 WecomCliService
AND   不写入 sync_log
AND   返回 WecomSyncWriteResponse(success=false, errorCode="INVALID_REQUEST", errorMessage="records 不能为 null")
```

#### Scenario: 白名单外 Sheet

```text
GIVEN sheetName 不在允许列表中
WHEN  调用 WecomWriteService.writeRecords(sheetName, records)
THEN  不调用 WecomCliService
AND   不写入 sync_log（与 #6 INVALID_SHEET 行为一致：白名单外不写日志）
AND   返回 WecomSyncWriteResponse(success=false, errorCode="INVALID_SHEET")
```

#### Scenario: 写入失败（CLI 错误）

```text
GIVEN WecomCliService 返回失败结果
WHEN  调用 WecomWriteService.writeRecords(sheetName, records)
THEN  写入 fbs_wecom_sync_log（sync_type=WRITE, status=FAILED, error_code=具体错误码）
AND   返回 WecomSyncWriteResponse(success=false, errorCode=具体错误码)
```

#### Scenario: 部分分片写入失败

```text
GIVEN 多分片写入中部分分片成功、部分失败
WHEN  串行写入过程中某分片返回失败
THEN  停止后续分片写入
AND   写入 fbs_wecom_sync_log（sync_type=WRITE, status=FAILED, error_code=PARTIAL_WRITE_FAILED, record_count=已成功写入数）
AND   返回 WecomSyncWriteResponse(success=false, errorCode="PARTIAL_WRITE_FAILED", writtenRecords=已成功写入数)
AND   不回滚已成功写入的分片
```

---

### Requirement: WecomSyncController — 写入 API 端点

WHEN 需要手动触发向企微智能表格写入数据,
系统 SHALL 提供 RESTful API 端点。

#### Scenario: 手动触发写入

```text
GIVEN 用户已登录且有 business:fbs:wecom:sync:write 权限
WHEN  POST /fbs/business/wecom/sync/write
      body: { sheetName: "meta", records: [{ "values": { "字段标题": value } }] }
THEN  同步执行写入（非异步，等待结果后返回）
AND   成功时返回 200 + { success, sheetName, totalRecords, writtenRecords, shardCount, durationMs, syncLogId }
AND   失败时返回 200 + { success:false, errorCode, errorMessage }
```

#### Scenario: 未登录写入

```text
GIVEN 请求未携带有效 JWT
WHEN  POST /fbs/business/wecom/sync/write
THEN  返回 401
```

#### Scenario: sheetName 为空时默认 meta

```text
GIVEN 用户已登录
AND   sheetName 为 null 或空字符串
WHEN  POST /fbs/business/wecom/sync/write
      body: { sheetName: "", records: [...] }
THEN  使用默认 Sheet "meta" 继续处理（与 #6 读取行为一致）
```

---

## MODIFIED Requirements

### Requirement: fbs_wecom_sync_log — 支持写入类型

> 原规范 v1.5 定义 sync_type 默认 READ。
> 新增 WRITE 类型。

WHEN sync_type 为 WRITE 时,
系统 SHALL 记录写入操作的日志。

#### Scenario: 写入日志记录

```text
GIVEN 通过校验后的实际写入操作完成（白名单校验通过、非空记录、所有单条 ≤ 20KB）
WHEN  WecomWriteService 写入 sync_log
THEN  sync_type = "WRITE"
AND   record_count = 成功写入的记录数
AND   status = SUCCESS 或 FAILED
AND   不记录 key_type（smartsheet_add_records 无此参数，字段键固定使用字段标题）
```

#### Scenario: 不写日志的拒绝场景

```text
GIVEN sheetName 不在白名单 或 records 为空列表 或 存在单条记录 > 20KB
WHEN  WecomWriteService.writeRecords
THEN  不写入 sync_log（这些是请求校验失败，与 #6 INVALID_SHEET / check 行为一致）
```

---

## 附录：错误码扩展

| 错误码 | 含义 | 重试 | 备注 |
|--------|------|------|------|
| `BIZ_PAYLOAD_TOO_LARGE` | 单条记录超过 payload 上限（默认 20KB） | 不重试 | 前置校验，不调用 CLI |
| `PARTIAL_WRITE_FAILED` | 部分分片写入失败 | 不重试 | 已写入不回滚 |

> 注：#6 已有错误码（CLI_NOT_FOUND / EXEC_TIMEOUT / AUTH_REQUIRED / NET_* / PARSE_ERROR）继续适用写入场景。
> 注：`RATE_LIMITED` 不在 MVP 范围内。限频由 CLI 底层处理，MVP 直接透传为 FAILED。

---

## 附录：CLI 参数格式说明

`smartsheet_add_records` 的 records 格式（经 wecom-cli --help 实际验证）：

```json
{
  "docid": "文档ID",
  "sheet_id": "子表ID",
  "records": [
    {
      "values": {
        "字段标题A": [{"type": "text", "text": "文本内容"}],
        "字段标题B": 100,
        "字段标题C": true
      }
    }
  ]
}
```

**关键约束**：
- `smartsheet_add_records` **没有 `key_type` 参数**（该参数仅存在于 update/get）
- values 的 key 固定使用**字段标题**，不能使用字段 ID
- 每条记录必须包装在 `values` 对象中
- 文本字段：`[{"type":"text","text":"内容"}]`
- 数字/货币/百分比/复选框：直接传值
- MVP 策略：调用方负责提供正确格式，WriteService 透传不校验字段类型
