# 规范差异：add-wecom-cli-integration（MVP）

> **变更 ID**：`06-add-wecom-cli-integration`
> **影响规范**：`specs/platform-scene-pack-ops/spec.md` (v1.4)
> **新增章节**：第十四章「企微智能表格集成」（MVP 基础层）
> **范围**：最简可跑通 — Java 调用 wecom-cli + 读取 2 张 Sheet + 手动触发接口

---

## ADDED Requirements

### Requirement: WecomCliService — wecom-cli 命令执行服务

WHEN 系统需要调用 wecom-cli.exe 执行企微操作,
系统 SHALL 提供 `WecomCliService` 接口封装 ProcessBuilder 调用。

#### Scenario: 正常执行命令
GIVEN wecom-cli.exe 路径已配置且文件存在
AND wecom-cli 已完成企业微信认证
WHEN 调用 `WecomCliService.execute("doc", "smartsheet_get_records", '{"docId":"xxx","sheetName":"meta"}')`
THEN 系统 shell=false 启动进程并传入 category/method/params 三个参数
AND 在配置的超时时间（默认 30s）内返回 `WecomCliResult(success=true, rawOutput=非空, durationMs>0)`
AND **不写入日志**（日志由上层 WecomSyncService 统一负责）

#### Scenario: wecom-cli 文件不存在
GIVEN 配置的 wecom-cli.exe 路径不存在
WHEN 调用 execute 或 isAvailable 方法
THEN 返回 `WecomCliResult(success=false, errorCode="CLI_NOT_FOUND")`
AND 不尝试启动任何进程

#### Scenario: 认证未完成
GIVEN wecom-cli.exe 可执行
WHEN 调用 execute 方法且 wecom-cli 返回认证错误
THEN 返回 `WecomCliResult(success=false, errorCode="AUTH_REQUIRED", errorMessage包含"扫码"或"登录"或"auth")`
AND 不触发重试

#### Scenario: 进程超时
GIVEN wecom-cli.exe 可执行且已认证
WHEN 单次调用超过配置的超时时间（默认 30s）
THEN 强制销毁进程（DestroyForcibly）
AND 返回 `WecomCliResult(success=false, errorCode="EXEC_TIMEOUT")`

#### Scenario: 网络错误简单重试
GIVEN wecom-cli.exe 可执行且已认证
WHEN 调用 execute 方法且返回 NET_ 错误码（NET_TIMEOUT/NET_CONNECTION_FAILED）
THEN 等待 500ms 后重试 1 次
AND 重试仍失败则返回最后一次错误结果

---

### Requirement: WecomSyncService — 最简同步读取服务

WHEN 需要从企微智能表格读取数据并记录,
系统 SHALL 通过 `WecomSyncService` 提供按 Sheet 读取并落日志的能力。

#### Scenario: 读取 meta Sheet（场景包规则）
GIVEN WecomCliService 可用
WHEN 调用 `WecomSyncService.readSheet("meta")` 或不传 sheetName 默认读 meta
THEN 构造 smartsheet_get_records 命令参数（含 docId + sheetName）
AND 调用 WecomCliService.execute("doc", "smartsheet_get_records", params)
AND 解析双层 JSON 结构（outer.content[0].text → inner data array）
AND 将原始记录列表存入 fbs_wecom_sync_log 的 snapshot_json 字段
AND 返回 `WecomSyncReadResponse(sheetName="meta", recordCount=N, success=true)`

#### Scenario: 读取 commercial_hub Sheet
GIVEN WecomCliService 可用
WHEN 调用 `WecomSyncService.readSheet("commercial_hub")`
THEN 同上流程读取 commercial_hub Sheet
AND 存入 fbs_wecom_sync_log（sheet_name="commercial_hub"）
AND 不做 record_type 分类拆分，整体作为快照存储

#### Scenario: 读取失败
GIVEN WecomCliService 不可用或网络异常
WHEN 调用 readSheet 方法
THEN 返回 `WecomSyncReadResponse(success=false, errorcode=具体错误码)`
AND fbs_wecom_sync_log 中 status=FAILED, snapshot_json=NULL
AND 不抛未捕获异常到 Controller 层

---

### Requirement: WecomSyncController — 手动触发读取 API

WHEN 需要手动触发从企微智能表格读取数据,
系统 SHALL 提供 RESTful API 端点。

#### Scenario: 手动触发读取
GIVEN 用户已登录且有权限
WHEN POST `/fbs/business/wecom/sync/read`（body 可选 { "sheetName": "meta" }）
THEN 同步执行读取（非异步，等待结果后返回）
AND 成功时返回 200 + { success, sheetName, recordCount, durationMs, syncLogId, snapshot[] }
AND 失败时返回对应错误码（400/500 视错误类型）

#### Scenario: 连通性检测
GIVEN wecom-cli 路径已配置
WHEN POST `/fbs/business/wecom/sync/check`
THEN 检查 wecom-cli.exe 文件是否存在（`File.exists()`）
AND **不验证认证态**（认证态在 read 时自然暴露，MVP 不做额外检测）
AND 返回 { cliAvailable: bool, cliPath: string, lastError: string? }
AND **不写入 sync_log**（轻量文件检查）

---

## 数据模型 ADDED

### Table: fbs_wecom_sync_log

```sql
CREATE TABLE fbs_wecom_sync_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_type       VARCHAR(16)  NOT NULL DEFAULT 'READ' COMMENT 'READ（MVP 阶段只有 READ，check 不写日志）',
    sheet_name      VARCHAR(64)  NOT NULL COMMENT 'Sheet 名称',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'SUCCESS/FAILED/TIMEOUT/PARSE_ERROR',
    record_count    INT          DEFAULT 0 COMMENT '读取到的记录数',
    error_code      VARCHAR(32)  DEFAULT NULL COMMENT '错误码',
    error_message   TEXT         DEFAULT NULL,
    snapshot_json   LONGTEXT     DEFAULT NULL COMMENT '原始数据JSON快照',
    duration_ms     BIGINT       DEFAULT 0,
    created_by      BIGINT       DEFAULT NULL,
    created_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_sheet_name (sheet_name),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微同步日志表（MVP）';
```

**说明**：本阶段只建此 1 张表。不建 fbs_scene_pack_rule、fbs_commercial_hub 及任何业务子表。
snapshot_json 存储 Sheet 原始数据快照，后续 #7/#8 可在此基础上做结构化迁移。

---

## MODIFIED Requirements

### Requirement: SecurityConfig — 权限配置扩展

> 原规范 v1.4 已定义 `/fbs/skill-api/**` permitAll。
> 新增 `/fbs/business/wecom/**` 路径权限配置。

WHEN 请求到达 `/fbs/business/wecom/**` 路径,
系统 SHALL 要求 JWT 认证（走正常 Spring Security 过滤链，非 permitAll）。

---

## 附录：错误码定义（MVP 子集）

| 错误码 | 含义 | 重试 |
|--------|------|------|
| `CLI_NOT_FOUND` | wecom-cli.exe 不存在 | 不重试 |
| `EXEC_TIMEOUT` | 进程超时（默认 30s） | 不重试 |
| `AUTH_REQUIRED` | 未扫码认证或认证过期 | 不重试 |
| `NET_TIMEOUT` | 网络超时 | 重试 ×1（等 500ms） |
| `NET_CONNECTION_FAILED` | 连接失败 | 重试 ×1（等 500ms） |
| `PARSE_ERROR` | JSON 解析失败 | 不重试 |

> 注：完整错误码集（含 RATE_LIMITED / BIZ_PAYLOAD_TOO_LARGE 等）在 #7 阶段补充。
