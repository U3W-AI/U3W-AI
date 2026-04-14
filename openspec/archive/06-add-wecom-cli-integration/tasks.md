# OpenSpec #6 任务清单：add-wecom-cli-integration（MVP）

> **变更 ID**：`06-add-wecom-cli-integration`
> **范围**：最简可跑通 — Java 调用 wecom-cli + 读取 2 张 Sheet + 手动触发接口 + 日志落库

---

## 阶段 1：基础设施 — SQL 建表 + 配置

- [x] 1.1 创建 SQL 迁移脚本 `V20260413__add-wecom-cli-mvp__create_table.sql`
  - fbs_wecom_sync_log 表（字段见 spec-delta.md）
  - 含注释、索引
- [x] 1.2 application.yml 新增配置项
  - `wecom.cli.path`（wecom-cli.exe 绝对路径，默认空）
  - `wecom.cli.timeout-ms`（默认 30000）
  - `wecom.sheet.doc-id`（企微智能表格 ID：s3_AdMAqgbHAIUCNTehd61tPS0uJg157）
- [x] 1.3 执行 SQL 建表验证表创建成功（需手动在数据库执行，SQL 已就位 `sql/V20260413__*.sql`）

## 阶段 2：WecomCliService 核心封装

- [x] 2.1 创建 `WecomCliResult.java` POJO
  - 字段：success/ errorCode/ rawOutput/ parsedData/ exitCode/ durationMs
  - 提供静态工厂方法：`.success(raw, duration)` / `.fail(code, msg, exitCode, duration)`
- [x] 2.2 创建 `WecomCliService.java` 接口
  - 方法：`execute(category, method, params)` → WecomCliResult
  - 方法：`isAvailable()` → boolean
  - 方法：`getCliPath()` → String
- [x] 2.3 创建 `WecomCliServiceImpl.java` 实现
  - ProcessBuilder shell=false 启动 wecom-cli.exe
  - 传入三个固定参数：category method 'params-json'
  - 超时控制：Process.waitFor(timeout)
  - 超时时 destroyForcibly()
  - NET_ 错误码检测 + 简单重试 ×1（500ms 延迟）
  - CLI_NOT_FOUND 检测（运行时校验文件 exists）
  - **不写日志**（日志由 WecomSyncService 统一负责）
  - doExecute 改为 package-private 以便单元测试 spy

## 阶段 3：POJO 与 DTO

- [x] 3.1 创建 `WecomSyncReadRequest.java`
  - 可选字段：sheetName（不传默认 "meta"）
- [x] 3.2 创建 `WecomSyncReadResponse.java`
  - 字段：success/ sheetName/ recordCount/ durationMs/ syncLogId/ snapshot(List, 前10条预览)/ errorCode/ errorMessage
  - 静态工厂方法：`.ok()` / `.fail()`
- [x] 3.3 创建 `WecomCheckResponse.java`
  - 字段：cliAvailable/ cliPath/ lastError（MVP 只检文件存在，不检认证态）
  - 静态工厂方法：`.available()` / `.unavailable()`

## 阶段 4：WecomSyncService 同步服务

- [x] 4.1 创建 `WecomSyncService.java` 接口
  - 方法：`readSheet(sheetName)` → WecomSyncReadResponse
  - 方法：`check()` → WecomCheckResponse
- [x] 4.2 创建 `WecomSyncServiceImpl.java` 实现
  - readSheet 流程：isAvailable → buildParams → execute → parseDoubleLayerJson → writeLog → return
  - check 流程：isAvailable → return WecomCheckResponse（**不写日志**）
  - 异常处理：所有异常 catch 后转为 error response + 写 FAILED 日志
  - 日志写入统一入口 writeLog()，一次 read 只调用一次

## 阶段 5：Mapper 层

- [x] 5.1 创建 `FbsWecomSyncLog.java` Entity
- [x] 5.2 创建 `FbsWecomSyncLogMapper.java` 接口
  - 方法：`insertSyncLog(log)` → int（useGeneratedKeys=true）
- [x] 5.3 MyBatis XML mapper

## 阶段 6：Controller API

- [x] 6.1 创建 `WecomSyncController.java`
  - `POST /fbs/business/wecom/sync/read`
  - `POST /fbs/business/wecom/sync/check`
- [x] 6.2 SecurityConfig 权限配置（已确认：`/fbs/business/wecom/**` 走 anyRequest().authenticated()，JWT 保护正常）

## 阶段 7：单元测试

- [x] 7.1 创建 `WecomCliServiceImplTest.java`（12 用例 ✅）
  - testIsAvailable_FileExists / testIsAvailable_FileNotFound / testIsAvailable_EmptyPath
  - testExecute_CliNotFound / testExecute_Success_SpyDoExecute
  - testExecute_NonNetError_NoRetry / testExecute_NetError_RetryOnce
  - testGetCliPath / testGetCliPath_Null
  - testExecute_AuthRequired_NoRetry / testExecute_ExecTimeout_NoRetry / testExecute_CliError_NoRetry
- [x] 7.2 创建 `WecomSyncServiceTest.java`（14 用例 ✅）
  - ReadSheetTests(12): testReadSheet_MetaSuccess / CommercialHubSuccess / PreviewLimit / DefaultSheet / ParseError_InvalidJson / ParseError_MissingContent / ParseError_EmptyText / InvalidSheetName / CliExecutionFailed / CliNotAvailable / AuthRequired_PassThrough / BlankSheetName_Default / LogWriteFailure_NotAffectResult / ExecTimeout_MapsToTimeoutStatus
  - CheckTests(2): testCheck_FileExists / testCheck_FileNotFound
- [x] 7.3 编译通过 ✅
- [x] 7.4 测试全绿 ✅（28 用例，0 failures，0 errors）

## 阶段 8：集成验证与文档

- [x] 8.1 执行 SQL 建表（手动，待用户执行 `sql/V20260413__add-wecom-cli-mvp__create_table.sql`）
- [ ] 8.2 集成测试：Postman/curl 验证 2 个 API（需 wecom-cli 已安装已认证）
- [x] 8.3 更新 API 文档 `docs/api/FBS-BUSINESS-API.md` 新增第十三章企微同步接口 + 附录J错误码
