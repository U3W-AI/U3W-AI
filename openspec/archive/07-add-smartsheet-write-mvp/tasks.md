# 实施任务

## 阶段 1：基础设施

### Task 1：确认无 DB 变更

- [x] 1.1 确认 `fbs_wecom_sync_log.sync_type` 已是 VARCHAR(16)，可直接写入 `'WRITE'`（#6 已实现，无需 ALTER）
- [x] 1.2 确认不需要新增 `key_type` 列（`smartsheet_add_records` 无此参数）

### Task 2：配置扩展

- [x] 2.1 `application.yml` 新增 `wecom.write.max-payload-bytes: 20480`
- [x] 2.2 确认复用 #6 已有的 sheet_id 映射配置（`wecom.sheet.sheet-ids.meta` / `commercial-hub`）

## 阶段 2：核心实现

### Task 3：DTO 创建

- [x] 3.1 创建 `WecomSyncWriteRequest.java`：sheetName(String) + records(List<Map<String,Object>>)
- [x] 3.2 创建 `WecomSyncWriteResponse.java`：success + sheetName + totalRecords + writtenRecords + shardCount + durationMs + syncLogId + errorCode + errorMessage
  - 提供 ok() / fail() 静态工厂方法（与 #6 WecomSyncReadResponse 风格一致）

### Task 4：WecomWriteService 接口 + 实现

- [x] 4.1 创建 `WecomWriteService.java` 接口：`WecomSyncWriteResponse writeRecords(String sheetName, List<Map<String, Object>> records)`
- [x] 4.2 创建 `WecomWriteServiceImpl.java`：
  - 注入 WecomCliService + FbsWecomSyncLogMapper + ObjectMapper
  - 注入 docId / metaSheetId / commercialHubSheetId（同 #6）
  - 自行定义 ALLOWED_SHEETS 白名单（与 WecomSyncServiceImpl 相同集合，MVP 允许重复）
  - sheetName 为 null/空串 → 默认使用 "meta"（与 #6 读取行为一致）
  - 校验 sheetName 白名单 → 不在名单返回 INVALID_SHEET（不写日志）
  - records 为 null → 返回 INVALID_REQUEST（不写日志）
  - 空记录列表 → 返回 no-op（success=true, writtenRecords=0, 不写日志）
  - **前置校验**：遍历每条记录序列化后字节数，单条 > 20KB → 返回 BIZ_PAYLOAD_TOO_LARGE（不写日志）
  - 实现 splitRecords 分片逻辑（20KB 上限，按记录条数切割）
  - 逐片构造 params JSON：`{docid, sheet_id, records: [分片]}`（CLI 格式，records 保持调用方传入的 values 结构）
  - 串行逐片调用 WecomCliService.execute("doc", "smartsheet_add_records", paramsJson)
  - 任一分片失败 → 停止后续，汇总已成功写入数，返回 PARTIAL_WRITE_FAILED
  - 重试由底层 WecomCliService.execute() 承担（NET_ ×1），WriteService 层不加重试
  - 写入 fbs_wecom_sync_log（sync_type=WRITE, status=SUCCESS/FAILED, record_count=成功写入数）

### Task 5：Controller 端点

- [x] 5.1 `WecomSyncController.java` 新增 `POST /fbs/business/wecom/sync/write`
- [x] 5.2 加 `@PreAuthorize("@ss.hasPermi('business:fbs:wecom:sync:write')")` 权限注解
- [x] 5.3 请求体校验（sheetName 非空时使用，为空则默认 meta；records 非空）

## 阶段 3：测试

### Task 6：单元测试

- [x] 6.1 创建 `WecomWriteServiceTest.java`（10 用例）：
  - 写入成功（小数据，无需分片）
  - 写入成功（需分片 ×2）
  - 单条记录超 20KB → BIZ_PAYLOAD_TOO_LARGE
  - 空记录列表 → success=true, writtenRecords=0
  - 白名单外 sheetName → INVALID_SHEET
  - CLI 失败 → 日志记录 FAILED
  - 部分分片失败 → PARTIAL_WRITE_FAILED（前 N 片成功，第 N+1 片失败）
  - sheetName 为 null → 默认使用 meta
  - sheetName 为空字符串 → 默认使用 meta
  - records 为 null → INVALID_REQUEST
- [x] 6.2 运行全部测试，确认通过（10/10 全绿）

## 阶段 4：文档

### Task 7：API 文档更新

- [x] 7.1 `docs/api/FBS-BUSINESS-API.md` 新增写入 API 章节（含 record 格式说明）
- [x] 7.2 新增错误码 BIZ_PAYLOAD_TOO_LARGE / PARTIAL_WRITE_FAILED 文档
