# 企微同步接口（FBS Business API）

> **变更 ID**：`06-add-wecom-cli-integration`  
> **日期**：2026-04-13  
> **范围**：MVP — Java 调用 wecom-cli + 读取 2 张 Sheet + 手动触发 + 日志落库

---

## 14. 企微同步接口（WeCom Sync）

### 14.1 概述

通过调用本地安装的 `wecom-cli` 工具，读取企微智能表格（SmartSheet）中的数据。

**前置要求**：
- 已在服务器部署 `wecom-cli.exe`（路径通过 `wecom.cli.path` 配置）
- wecom-cli 已完成企微账号授权登录
- 智能表格 ID 已配置（`wecom.sheet.doc-id`）

**认证方式**：JWT Token（`/fbs/business/**` 均需登录）

**安全说明**：
- CLI 路径信息仅对登录用户可见（内部接口）
- 日志按次写入，业务数据不上报外部系统

---

### 14.2 读取 Sheet 数据

**端点**：`POST /fbs/business/wecom/sync/read`

**请求体**（可选）：
```json
{
  "sheetName": "meta"         // 可选，不传默认 "meta"。可传 "commercial_hub"
}
```

**成功响应**（HTTP 200）：
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
    "snapshot": [              // 前 10 条预览
      { "字段1": "值1", "字段2": "值2" },
      ...
    ],
    "errorCode": null,
    "errorMessage": null
  }
}
```

**失败响应**（HTTP 200，业务失败）：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": false,
    "sheetName": "meta",
    "recordCount": 0,
    "durationMs": 56,
    "syncLogId": 2,
    "snapshot": null,
    "errorCode": "CLI_NOT_FOUND",
    "errorMessage": "wecom-cli 不存在: C:\\bin\\wecom-cli.exe"
  }
}
```

**业务错误码**（`errorCode`）：

| errorCode | 说明 | 处理建议 |
|-----------|------|----------|
| `CLI_NOT_FOUND` | wecom-cli.exe 文件不存在或路径未配置 | 配置正确的 `wecom.cli.path` |
| `EXEC_TIMEOUT` | CLI 执行超时（默认 30s） | 检查网络或增大 `wecom.cli.timeout-ms` |
| `NET_CONNECTION_FAILED` | 企微服务器连接失败 | 检查网络连通性 |
| `AUTH_REQUIRED` | wecom-cli 未完成授权登录 | 重新登录 wecom-cli |
| `PARSE_ERROR` | 返回的 JSON 解析失败 | 反馈给运维排查 |
| `UNKNOWN` | 未知异常 | 查看 `errorMessage` |

---

### 14.3 检测 CLI 可用性

**端点**：`POST /fbs/business/wecom/sync/check`

**请求体**：无

**可用时响应**（HTTP 200）：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "cliAvailable": true,
    "cliPath": "C:\\bin\\wecom-cli.exe",
    "lastError": null
  }
}
```

**不可用时响应**（HTTP 200）：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "cliAvailable": false,
    "cliPath": "C:\\bin\\wecom-cli.exe",
    "lastError": "wecom-cli 文件不存在"
  }
}
```

**设计说明**：
- MVP 只检测文件存在性，不检测登录态
- 不写 `fbs_wecom_sync_log`（轻量检测接口）
- JWT 保护，内部接口可接受返回 CLI 路径

---

## 附录 J：错误码汇总

| errorCode | 来源 | 说明 |
|-----------|------|------|
| `CLI_NOT_FOUND` | WecomCliService | wecom-cli.exe 文件不存在 |
| `EXEC_TIMEOUT` | WecomCliService | CLI 执行超时 |
| `EXEC_INTERRUPTED` | WecomCliService | 重试被中断 |
| `NET_TIMEOUT` | WecomCliService | wecom-cli 返回超时错误 |
| `NET_CONNECTION_FAILED` | WecomCliService | 连接企微服务器失败 |
| `AUTH_REQUIRED` | WecomCliService | 未授权（需扫码登录） |
| `CLI_ERROR` | WecomCliService | 其他 CLI 执行错误 |
| `PARSE_ERROR` | WecomSyncService | JSON 解析失败 |

---

*本文档由 OpenSpec #6 变更自动生成，变更 ID：`06-add-wecom-cli-integration`*  
*最后更新：2026-04-13*
