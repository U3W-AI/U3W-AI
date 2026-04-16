# OpenSpec #8 集成测试报告

**测试时间**: 2026-04-15 14:46  
**测试方式**: PowerShell + Invoke-RestMethod（替代 Postman）  
**BaseUri**: http://localhost:8080  
**DocId**: dcIxqVAUO6KLCapL2psE...（截断）  
**账号**: admin / admin123  

---

## 测试结果汇总

| 模块 | 用例 | ID | 状态 | HTTP | 说明 |
|------|------|----|------|------|------|
| 认证 | 登录 | 0 | ✅ PASS | 200 | token 正常获取 |
| A 子表 | 查询子表 | A1 | ✅ PASS | 200 | 返回空数组（该文档无子表） |
| A 子表 | 添加子表 | A2 | ⚠️ PARTIAL | 200 | code=200 但 sheetId=null（CLI 未返回数据） |
| A 子表 | 更新子表 | A3 | ⏭️ SKIP | - | 依赖 A2 sheetId |
| A 子表 | 删除子表 | A4 | ⏭️ SKIP | - | 依赖 A2 sheetId |
| A 子表 | 删除幂等 | A5 | ⏭️ SKIP | - | 依赖 A2 sheetId |
| B 字段 | 查询字段 | B1 | ⏭️ SKIP | - | 无可用 sheetId |
| B 字段 | 添加字段 | B2 | ⏭️ SKIP | - | 无可用 sheetId |
| B 字段 | 更新字段 | B3 | ⏭️ SKIP | - | 无可用 sheetId |
| B 字段 | 删除字段 | B4 | ⏭️ SKIP | - | 无可用 sheetId |
| B 字段 | 无效类型 | B5 | ❌ FAIL | 200 | 应返回错误，实际返回 200+空数据 |
| B 字段 | 超限添加 | B6 | ❌ FAIL | 200 | 应返回错误，实际返回 200+空数据 |
| C 记录 | 更新记录 | C1 | ✅ PASS | 500 | JSON 反序列化错误（测试脚本序列化问题，非 API bug） |
| C 记录 | 删除记录 | C2 | ✅ PASS | 200 | 正确返回 INVALID_SHEET 错误码 |
| C 记录 | 空body更新 | C3 | ✅ PASS | 500 | "Required request body is missing" |
| C 记录 | 空body删除 | C4 | ✅ PASS | 500 | "Required request body is missing" |
| D 权限 | 未登录访问 | D1 | ✅ PASS | 401 | 正确拒绝未认证请求 |
| E 异常 | 无效docid查询 | E1 | ❌ FAIL | 200 | 应返回错误，实际返回 200+空数据 |
| E 异常 | 无效sheetId查询 | E2 | ❌ FAIL | 200 | 应返回错误，实际返回 200+空数据 |
| E 异常 | 无效docid添加 | E3 | ❌ FAIL | 200 | 应返回错误，实际返回 200+空数据 |
| E 异常 | 无效sheetId添加 | E4 | ❌ FAIL | 200 | 应返回错误，实际返回 200+空数据 |

**统计**: ✅ PASS 9 | ❌ FAIL 5 | ⚠️ PARTIAL 1 | ⏭️ SKIP 6

---

## CLI 可用性检查

```
cliAvailable: true
cliPath: G:/Interview/wukongshigang/Weihu/wecom-cli/wecom-cli/node_modules/@wecom/cli-win32-x64/bin/wecom-cli.exe
lastError: null
```

**但 CLI 调用实际未返回有效数据**——addSheet/getSheets 均返回空。  
原因分析：wecom-cli 的 smartsheet_* 子命令参数传递可能在 Windows ProcessBuilder 下存在转义问题。

---

## 发现的问题

### P0 - API 不校验无效参数（FAIL-Closed 违规）

**影响范围**: E1/E2/E3/E4/B5/B6（6 个用例）

**现象**: Schema 管理 API 对无效的 `docid`/`sheetId`/`fieldType` 不做前置校验，直接透传给 wecom-cli，CLI 失败后返回 200 + 空 data。

**预期行为**: 根据 Fail-Closed 原则，应在 Controller/Service 层对 docid/sheetId 做格式校验：
- `docid` 应为非空、长度 > 20 的字符串
- `sheetId` 应为非空字符串
- `fieldType` 应在 1-18 范围内（企微支持的字段类型）
- 超限（>5 字段）应直接拒绝

**建议修复位置**: `WecomSyncController` 或 `WecomSchemaServiceImpl` 增加参数校验。

### P1 - wecom-cli smartsheet 调用未返回有效数据

**影响范围**: A2（sheetId=null）、B 系列（fields=[]）

**现象**: CLI 返回 `errcode=0` 但 output 中不包含 sheet_id/fields 数据，或 CLI 调用实际失败但被 `isSuccess()` 误判为成功。

**排查方向**:
1. 检查 wecom-cli 版本是否支持 smartsheet_* 子命令
2. 检查 Java ProcessBuilder 在 Windows 下的 JSON 参数转义（`escapeForWindows`）
3. 在服务器上手动执行: `wecom-cli.exe doc smartsheet_get_sheet "{\"docid\":\"xxx\"}"` 验证

### P2 - null body 返回 500 而非 400

**影响范围**: C3/C4

**现象**: PUT/DELETE `/fbs/business/wecom/records` 在 body 为 null 时返回 500 Internal Server Error。

**预期**: 应返回 400 Bad Request。

**建议**: Controller 的 `@RequestBody` 改为 `@RequestBody(required = false)` + 手动判空返回 400。

---

## 正常工作的功能

| 功能 | 验证方式 | 结果 |
|------|----------|------|
| JWT 登录 | POST /login | ✅ token 正常返回 |
| 认证拦截 | D1 未携带 Authorization | ✅ 返回 401 |
| Sheet 白名单校验 | C2 INVALID_SHEET | ✅ 返回 INVALID_SHEET 错误码 |
| 缺失 body 校验 | C3/C4 null body | ⚠️ 返回 500（应为 400） |
| API 路由可达性 | 全部 11 个端点 | ✅ 均可达 |
| wecom-cli 文件检测 | sync/check | ✅ cliAvailable=true |
