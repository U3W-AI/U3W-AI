# OpenSpec #6 提案（MVP）：add-wecom-cli-integration

> **变更 ID**：`06-add-wecom-cli-integration`
> **状态**：PROPOSAL（待评审）
> **日期**：2026-04-13
> **依赖**：OpenSpec #5 ✅
> **后续**：OpenSpec #7（智能表格同步引擎写入）

---

## 一句话目标

**Java 后端能调用 wecom-cli.exe，从企微智能表格读到数据，通过一个接口手动触发，结果落日志表。**

---

## Why — 为什么需要

### 核心问题

Skill 侧（v2.1.0）已经能通过 `wecom-client.mjs` → `wecom-cli.exe` 读取企微智能表格 8 张 Sheet。后端 U3W-AI 目前完全没有这个通道。

**本阶段只解决一件事：把这条路打通。**

### 不解决的问题

- 场景包规则如何对齐到 fbs_scene_pack（#7/#8）
- commercial_hub 激活码/乐包/积分正式落库（#7/#9）
- 定时同步、异步任务、前端页面（全部后续）

---

## What — 变更范围（MVP 收窄版）

### IN 范围（本阶段只做这些）

| # | 模块 | 说明 |
|---|------|------|
| 1 | **WecomCliService** | Java ProcessBuilder 封装 wecom-cli.exe（路径配置 + execute + 超时 + 简单重试 ×1） |
| 2 | **读取 2 张 Sheet** | 1 张场景包规则 Sheet（meta）+ 1 张 commercial_hub Sheet（双层 JSON 解析 + 快照存储） |
| 3 | **2 个手动触发 API** | POST `/fbs/business/wecom/sync/read`（同步读取）+ POST `/fbs/business/wecom/sync/check`（连通性检测） |
| 4 | **fbs_wecom_sync_log 表** | 本阶段唯一新表，记录每次调用的状态/快照/耗时/错误 |

### OUT 范围（明确延期，不在本次）

| # | 内容 | 延期到 |
|---|------|--------|
| 1 | 8 张 Sheet 全量支持 | #7 |
| 2 | commercial_hub 4 种 record_type 业务化落库 | #7/#9 |
| 3 | ENTITLEMENT_RULE 与 fbs_scene_pack 映射/genre 对齐 | #8 |
| 4 | fbs_scene_pack_rule 正式规则表 | #8 |
| 5 | 定时同步 / Quartz | #7 |
| 6 | 异步任务 / syncTaskId / 任务队列 | #7 |
| 7 | 双向同步（写回企微） | #7 |
| 8 | 冲突检测 | #7 |
| 9 | 分布式锁 / Redis 锁 | #7 |
| 10 | 前端页面 | #8 |
| 11 | 菜单 SQL | #8 |
| 12 | 复杂安全加固（白名单/IP限制/签名） | 后续 |
| 13 | 20KB 分片写入（写入在 #7） | #7 |
| 14 | commercial_hub 与激活码/乐包/积分正式对齐 | #9 |

---

## Impact — 影响分析

### 新增文件清单

| # | 文件 | 类型 | 说明 |
|---|------|------|------|
| 1 | `fbs/service/WecomCliService.java` | 接口 | wecom-cli 封装（execute + isAvailable） |
| 2 | `fbs/service/impl/WecomCliServiceImpl.java` | 实现 | ProcessBuilder 执行 + 超时 + 重试 |
| 3 | `fbs/domain/WecomCliResult.java` | POJO | 统一返回值（含静态工厂方法） |
| 4 | `fbs/service/WecomSyncService.java` | 接口 | 同步编排（readSheet + check） |
| 5 | `fbs/service/impl/WecomSyncServiceImpl.java` | 实现 | 读取 2 张 Sheet + 写日志 |
| 6 | `fbs/controller/WecomSyncController.java` | Controller | 2 个 API 端点（read + check） |
| 7 | `fbs/domain/FbsWecomSyncLog.java` | Entity | 同步日志表映射 |
| 8 | `fbs/mapper/FbsWecomSyncLogMapper.java` | Mapper | 日志表 CRUD |
| 9 | `fbs/dto/WecomSyncReadRequest.java` | DTO | 读取请求（可选 sheetName） |
| 10 | `fbs/dto/WecomSyncReadResponse.java` | DTO | 读取响应（含前10条预览） |
| 11 | `fbs/dto/WecomCheckResponse.java` | DTO | 连通性检测响应 |
| 12 | `sql/V20260413__add-wecom-cli-mvp__create_table.sql` | SQL | fbs_wecom_sync_log 建表 |
| 13 | `test/.../WecomCliServiceTest.java` | 测试 | execute 6 用例 |
| 14 | `test/.../WecomSyncServiceTest.java` | 测试 | readSheet + check 6 用例 |

**总计：~14 个文件**

### 修改文件

| 文件 | 改动 |
|------|------|
| `SecurityConfig.java` | `/fbs/business/wecom/**` 需 JWT 认证（非 permitAll） |
| `application.yml` | 新增 `wecom.cli.path` + `wecom.cli.timeout-ms` + `wecom.sheet.doc-id` 配置项 |

### 数据库

| 表 | 操作 | 说明 |
|----|------|------|
| `fbs_wecom_sync_log` | **新建** | 本阶段唯一新表 |

**不新建** fbs_scene_pack_rule、fbs_commercial_hub 及任何子表。

---

## 技术方案要点

### 1. WecomCliService（核心封装）

```java
public interface WecomCliService {
    /** 执行 wecom-cli 命令 */
    WecomCliResult execute(String category, String method, String params);

    /** 检查 wecom-cli 是否可用（文件存在 + 可执行） */
    boolean isAvailable();
}

public class WecomCliResult {
    private boolean success;
    private String errorCode;       // CLI_NOT_FOUND / EXEC_TIMEOUT / NET_* / AUTH_* / PARSE_ERROR
    private String rawOutput;       // stdout 原始输出
    private Object parsedData;      // JSON 解析后的数据
    private int exitCode;
    private long durationMs;
}
```

**设计约束**：
- ProcessBuilder shell=false（安全）
- 默认超时 30s（MVP 够用），可配置
- 简单固定重试 ×1（NET_ 错误时等 500ms 再试一次），不做指数退避多层策略
- wecom-cli 路径从 application.yml 读取，**运行时检测文件存在**（不阻塞应用启动）
- **WecomCliService 不写日志**：只负责执行命令并返回结果，日志由上层 WecomSyncService 统一写入

### 2. SmartSheetReader（MVP 只读 2 张 Sheet）

```
读取流程：
  WecomCliService.execute("doc", "smartsheet_get_records", {docId, sheetName})
       ↓
  解析双层 JSON：outer.content[0].text → inner data array
       ↓
  返回 List<Map<String, Object>>（原始 Map，不强制 POJO 映射）
```

**MVP 只支持的 Sheet**：

| Sheet | 用途 | 落库方式 |
|-------|------|---------|
| meta | 场景包元信息（证明"能读到"） | 存入 sync_log 的 snapshot_json 字段 |
| commercial_hub | 商业化数据（证明"能读到"） | 存入 sync_log 的 snapshot_json 字段 |

**不做的**：按字段拆分 POJO、按 record_type 分类、映射到业务表。本阶段只要"读到原始 JSON 并存下来"。

### 3. 同步 API（2 个端点）

```
POST /fbs/business/wecom/sync/read
请求体（可选）：{ "sheetName": "meta" }   // 不传则读默认 Sheet
响应体：{
  success: true,
  sheetName: "meta",
  recordCount: 12,
  durationMs: 3200,
  syncLogId: 42,
  snapshot: [...]           // 读取到的原始数据列表（预览，最多前 10 条）
}

POST /fbs/business/wecom/sync/check
请求体：无
响应体：{
  cliAvailable: true,       // 文件是否存在
  cliPath: "/usr/local/bin/wecom-cli.exe",  // 当前配置的路径（P1: JWT 内部接口可接受，后续可选脱敏）
  lastError: null
}
```

**check 策略（MVP 最小化）**：
- **只检查文件是否存在**（`new File(cliPath).exists()`），不验证认证态
- 认证态在 read 时自然暴露（AUTH_* 错误码），不需要额外检测
- check 不写 sync_log（轻量文件检查，不值得记录）

### 4. 响应格式口径声明

**本 OpenSpec（#6）使用 HTTP 200 + `data.success` 字段区分业务成功/失败**，与若依 `AjaxResult` 习惯一致：

| 场景 | HTTP 状态码 | 响应体 |
|------|------------|--------|
| 正常读到数据 | 200 | `{code:200, data:{success:true, ...}}` |
| 业务失败（CLI 不可用、解析失败等） | 200 | `{code:200, data:{success:false, errorCode:"...", errorMessage:"..."}}` |
| 无权限（JWT 鉴权失败） | 401/403 | 若依标准错误体 |
| 系统异常 | 500 | 若依标准错误体 |

> **与早期 OpenSpec 口径的偏移确认**：本特性涉及外部 CLI 调用，业务失败（超时、解析异常等）与技术异常（500）需要区分，
> 采用 HTTP 200 + `success=false` 更符合实际调用方的处理习惯。后续 OpenSpec 如需统一，请在新提案中说明。

**关键设计决策**：
- **同步执行**，不异步，不返回 taskId
- **不传 sheetName 时默认读 meta**
- read 返回前 10 条记录作为预览确认
- **日志写入统一由 WecomSyncService 负责**（一次 read 写一条日志，不在 WecomCliService 内部重复写入）

### 4. 数据模型（仅 1 张表）

```sql
CREATE TABLE fbs_wecom_sync_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_type       VARCHAR(16)  NOT NULL DEFAULT 'READ' COMMENT 'READ（MVP 阶段只有 READ，check 不写日志）',
    sheet_name      VARCHAR(64)  NOT NULL COMMENT 'Sheet 名称',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'SUCCESS/FAILED/TIMEOUT/PARSE_ERROR',
    record_count    INT          DEFAULT 0 COMMENT '读取到的记录数',
    error_code      VARCHAR(32)  DEFAULT NULL COMMENT '错误码',
    error_message   TEXT         DEFAULT NULL,
    snapshot_json   LONGTEXT     DEFAULT NULL COMMENT '原始数据JSON快照（本阶段主要存储方式）',
    duration_ms     BIGINT       DEFAULT 0,
    created_by      BIGINT       DEFAULT NULL,
    created_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_sheet_name (sheet_name),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微同步日志表（MVP）';
```

**设计说明**：
- `snapshot_json` 存储读取到的原始 JSON 数组，MVP 阶段不做结构化拆分
- 后续 #7 可以在此基础上增加正式业务表并迁移数据
- `sync_type` MVP 阶段只有 READ（check 接口不写日志，只做文件存在性检查）

---

## 验收标准（MVP 最小可跑通）

### 必须通过（P0）

- [ ] wecom-cli 路径配置正确时可成功执行命令（execute 返回 success=true + 有数据）
- [ ] 读取企微智能表格 meta Sheet 成功时可返回解析后的记录列表
- [ ] 读取 commercial_hub Sheet 成功时可返回原始数据
- [ ] 读取失败时（网络不通/Sheet不存在/认证过期）返回明确错误码
- [ ] POST /fbs/business/wecom/sync/read 接口可工作（手动触发 → 返回结果）
- [ ] POST /fbs/business/wecom/sync/check 接口可工作（检测 CLI 文件是否存在，不依赖认证态）
- [ ] 每次 read 请求都写入 fbs_wecom_sync_log（含 status/record_count/snapshot_json/duration_ms）
- [ ] 进程超时时正确销毁进程并返回 EXEC_TIMEOUT

### 单元测试覆盖（P0）

| 测试类 | 用例 |
|--------|------|
| **WecomCliServiceTest**（6 用例） | execute 成功（mock process）/ execute 失败(exitCode≠0)/ execute 超时(destroyForcibly)/ isAvailable 文件存在/ isAvailable 文件不存在/ CLI_NOT_FOUND 路径无效 |
| **WecomSyncServiceTest**（6 用例） | readSheet 成功(meta)/ readSheet 成功(commercial_hub)/ readSheet 解析失败(invalid JSON)/ readSheet 网络异常/ check 文件存在/ check 文件不存在 |

**目标：≥12 用例全部通过**

---

## 风险与缓解

| 风险 | 可能性 | 影响 | 缓解 |
|------|--------|------|------|
| wecom-cli 未安装/未认证 | 高 | read 失败 | check 接口提前暴露问题；read 返回 CLI_NOT_FOUND / AUTH_* 错误码 |
| 企微 Token 过期 | 中 | 读不到数据 | AUTH_* 错误码明确返回，提示重新扫码 |
| 单次读取大数据量 Sheet OOM | 低 | 内存溢出 | MVP 先不加限制；若遇到加 recordCount 上限截断 |
| ProcessBuilder 安全 | 低 | 命令注入 | shell=false + 参数白名单(category/method/params 固定三参数) |
