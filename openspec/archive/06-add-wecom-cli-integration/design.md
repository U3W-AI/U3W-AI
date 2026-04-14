# OpenSpec #6 技术设计（MVP）

> **定位**：最简可跑通的技术实现细节，聚焦 3 个核心链路

---

## 1. ProcessBuilder 调用链

### 1.1 调用模型

```
WecomSyncService.readSheet(sheetName)
    │
    ▼
WecomCliService.execute("doc", "smartsheet_get_records", paramsJson)
    │
    ▼
ProcessBuilder(cliPath, category, method, paramsJson)
    │  shell = false
    │  redirectErrorStream = true
    ▼
Process.waitFor(timeout, TimeUnit.SECONDS)
    │
    ├── 成功 (exitCode=0) → 读取 stdout → 解析 JSON → WecomCliResult.success()
    │
    ├── 超时 → process.destroyForcibly() → WecomCliResult.fail("EXEC_TIMEOUT")
    │
    └── 失败 (exitCode≠0) → WecomCliResult.fail("CLI_ERROR", exitCode)
```

### 1.2 参数格式

与 Skill 侧 `wecom-client.mjs` 完全对齐，三参数模式：

```bash
wecom-cli.exe  "doc"  "smartsheet_get_records"  '{"docId":"s3_AdMAqgbHAIUCNTehd61tPS0uJg157","sheetName":"meta"}'
```

```java
// WecomCliServiceImpl 核心逻辑
ProcessBuilder pb = new ProcessBuilder(cliPath, category, method, params);
pb.directory(null);           // 继承工作目录
pb.redirectErrorStream(true); // 合并 stderr 到 stdout
```

### 1.3 重试策略

```
仅在 NET_* 错误时重试（exitCode=0 但 stdout 含 "NET_" 前缀错误码）：
  第 1 次：立即执行
  第 2 次（重试）：等待 500ms 后执行
  不做第 3 次

其他错误（CLI_NOT_FOUND / EXEC_TIMEOUT / AUTH_* / PARSE_ERROR）不重试。
```

### 1.4 路径检测

```
运行时检测（不阻塞启动）：
  isAvailable() → new File(cliPath).exists()
  
  false → WecomCliResult.fail("CLI_NOT_FOUND")
  true  → 继续执行

不在 @PostConstruct 中校验，不影响应用启动。
```

---

## 2. 双层 JSON 解析

### 2.1 企微返回结构

wecom-cli stdout 返回的 JSON 是双层嵌套：

```json
{
  "content": [
    {
      "text": "[{\"record_id\":\"xxx\",\"field1\":\"val1\",...}, {\"record_id\":\"yyy\",\"field2\":\"val2\",...}]"
    }
  ]
}
```

**外层**：wecom-cli 标准响应格式（content 数组）
**内层**：`content[0].text` 是一个**字符串化的 JSON 数组**，需要二次解析

### 2.2 解析逻辑

```java
// 伪代码
ObjectNode outer = objectMapper.readValue(rawOutput, ObjectNode.class);
String innerJson = outer.at("/content/0/text").asText();
List<Map<String, Object>> records = objectMapper.readValue(
    innerJson,
    new TypeReference<List<Map<String, Object>>>() {}
);
```

### 2.3 异常处理

| 异常场景 | 错误码 | 处理 |
|----------|--------|------|
| content 数组为空 | PARSE_ERROR | 返回空列表 + 日志记录 |
| content[0].text 缺失 | PARSE_ERROR | 返回空列表 + 日志记录 |
| 内层 JSON 格式错误 | PARSE_ERROR | 记录原始输出到日志 + 返回错误 |
| 企微返回 AUTH_REQUIRED | AUTH_REQUIRED | 直接透传，不重试 |
| 企微返回 NET_TIMEOUT | NET_TIMEOUT | 触发重试逻辑 |

---

## 3. 日志写入边界

### 3.1 职责分离（核心约束）

```
WecomCliService    →  只负责执行命令，返回 WecomCliResult。不写日志。
WecomSyncService   →  负责业务编排 + 日志写入。一次 read 写一条 sync_log。
```

### 3.2 写入时机

```
WecomSyncService.readSheet(sheetName):
  │
  ├── 调用 WecomCliService.execute()
  │     └── 返回 WecomCliResult（纯执行结果，无日志副作用）
  │
  ├── 解析结果（双层 JSON → List<Map>）
  │
  └── 写入 fbs_wecom_sync_log（无论成功失败都写）
        ├── SUCCESS: status=SUCCESS, record_count=N, snapshot_json=[...], duration_ms=T
        └── FAILED:  status=FAILED/TIMEOUT/PARSE_ERROR, record_count=0, error_code=XXX, duration_ms=T

**status vs error_code 职责划分**：

| 字段 | 职责 | 取值 |
|------|------|------|
| `status` | **大类**（日志记录/统计用） | SUCCESS / FAILED / TIMEOUT / PARSE_ERROR |
| `error_code` | **细分原因**（定位排查用） | CLI_NOT_FOUND / EXEC_FAILED / NET_TIMEOUT / NET_CONNECTION / AUTH_REQUIRED / PARSE_ERROR / UNKNOWN |

- status 用于日志列表筛选、成功率统计
- error_code 用于问题定位，FAILED 状态下才有细分
- TIMEOUT 是 status 的大类（不是 error_code），对应的 error_code = NET_TIMEOUT

WecomSyncService.check():
  │
  ├── 调用 WecomCliService.isAvailable()（纯文件检查）
  │
  └── 不写 sync_log（轻量文件检查，不值得记录）
```

### 3.3 日志不重复的保证

```
一次 read 流程只产生一条日志：

  SyncService.readSheet()
      │
      ├─ step 1: CliService.execute()    → 不写日志 ✅
      ├─ step 2: JSON 解析               → 不写日志 ✅
      └─ step 3: SyncService 写日志       → 写 1 条 ✅

如果 CliService 内部也写日志，一次 read 就会产生 2 条 → 错误
```

---

## 4. 错误码体系

与 Skill 侧 `wecom-client.mjs` 对齐，统一前缀：

| 前缀 | 来源 | 示例 |
|------|------|------|
| `CLI_NOT_FOUND` | 本地 | wecom-cli.exe 文件不存在 |
| `EXEC_TIMEOUT` | 本地 | 进程执行超过 30s |
| `CLI_ERROR` | 本地 | exitCode ≠ 0 |
| `NET_TIMEOUT` | 企微 | 网络超时（会触发重试） |
| `NET_ERROR` | 企微 | 其他网络错误（会触发重试） |
| `AUTH_REQUIRED` | 企微 | 认证过期，需重新扫码 |
| `RATE_LIMITED` | 企微 | API 限频 |
| `PARSE_ERROR` | 本地 | JSON 解析失败 |
| `SHEET_NOT_FOUND` | 本地 | 指定 Sheet 不存在 |
