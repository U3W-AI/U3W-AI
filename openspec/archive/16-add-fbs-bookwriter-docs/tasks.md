# 任务清单：FBS-BookWriter 产品文档生成（16-add-fbs-bookwriter-docs）

> **Change ID**: `16-add-fbs-bookwriter-docs`
> **日期**: 2026-04-21
> **状态**: ✅ 已完成

> **核心原则**：
> 1. 源码优先：`FbsSkillApiController.java` 是 DTO 的唯一事实来源
> 2. 两层错误机制统一：严格区分认证过滤器（真实 HTTP 4xx）与控制器（body.code）错误
> 3. 三份文档内部一致：共享相同错误码描述和术语
> 4. OpenSpec #14 不存在：变更日志中不得出现 #14

---

## 任务 1：源码核实（基础工作）

### 1.1 核实 Skill API DTO

**文件**: `WxFbsir-business/src/main/java/com/wx/fbsir/business/fbs/controller/skillapi/FbsSkillApiController.java`

- [x] 1.1.1 核实 `/user/info` 响应 data 字段（源码行 258-301）：userId、pointsBalance、activatedPacks（含内部字段 packId/packCode/packName/packStatus/status/expiresAt）
- [x] 1.1.2 核实 `/rights/check` 响应 data 字段（源码行 62-81）：pass、failReason、packId、pointsRuleCode、pointsAmount
- [x] 1.1.3 核实 `/usage/start` 幂等逻辑和 409 响应（源码行 134-185）
- [x] 1.1.4 核实 `/usage/end` 响应格式（源码行 192-220）：成功返回 `AjaxResult.success("更新成功")`，无 data body
- [x] 1.1.5 核实 `/usage/consume` 成功响应 data 字段（源码行 116-126）：success=true、remainPoints、usageRecordId、failReason=null
- [x] 1.1.6 核实 `/usage/consume` 失败路径（行 122-126）：不走 data，直接 `AjaxResult.error(failReason)`，body.code=500
- [x] 1.1.7 核实 `/points/earn` 成功响应 data 字段（行 340-353）：success=true、pointsAmount、remainPoints、usageRecordId
- [x] 1.1.8 核实 `/points/earn` 失败路径（行 354-363）：`data.success=false` + `AjaxResult.error(msg)`，body.code=500
- [x] 1.1.9 核实 `/scene-pack/query` status 字段类型：Integer（0/1/2），不是字符串

### 1.2 核实两层错误机制

- [x] 1.2.1 核实过滤器 `writeErrorResponse()` 使用 `response.setStatus(httpStatus)`（行 119）
- [x] 1.2.2 核实过滤器响应 body 格式：`{code: httpStatus, msg: "..."}`（行 124-127）
- [x] 1.2.3 核实 `AjaxResult.error(String msg)` 使用 `HttpStatus.ERROR=500`（行 144-147）
- [x] 1.2.4 核实 `AjaxResult.error(int code, String msg)` 使用提供的 code（行 168-171）
- [x] 1.2.5 确认两者均不调用 `HttpServletResponse.setStatus()`

### 1.3 核实运营 API 端点数量

- [x] 1.3.1 核实 `/enterprise/pack/*` 子路径：GET /list、GET /{id}、POST /grant、PUT /revoke（共 4 个）
- [x] 1.3.2 核实企微同步运营端点：3 sync + 4 子表 + 4 字段 + 2 记录 = 13 个
- [x] 1.3.3 字段管理是 4 个方法（GET/POST/PUT/DELETE），不是 3 个

---

## 任务 2：SDK-REFERENCE.md

**文件**: `docs/SDK-REFERENCE.md`

- [x] 2.1 端点数量：Skill API 7 个、用户自助 4 个、用户侧 API Key 4 个、运营 API 46 个
- [x] 2.2 `/user/info` 响应示例：补充 activatedPacks 内部 6 个字段（+packId、+packStatus、+status）
- [x] 2.3 `/rights/check` 错误码：区分 body.code 非 200 与 pass=false 走 body.code=200
- [x] 2.4 `/usage/start` 补充 409 冲突错误响应示例（SUCCESS/FAILED 状态）
- [x] 2.5 `/usage/end` 幂等说明：`body.code=409`（真实 HTTP 通常仍为 200）
- [x] 2.6 `/usage/consume` 成功示例：补充 `failReason: null` 字段
- [x] 2.7 所有端点错误码描述统一：认证过滤器 → 真实 HTTP 4xx；控制器 → body.code 非 200
- [x] 2.8 §4.5.5 企业运营：4 个子路径具体化（list、{id}、grant、revoke）
- [x] 2.9 §4.5.6 企微同步：字段管理从"3 个"改为"4 个"（GET/POST/PUT/DELETE）
- [x] 2.10 新增 §10 错误码参考章（此前缺失，正文从 §9 直接跳到 §11）
- [x] 2.11 §10.2 快速索引：两节结构（过滤器层/真实HTTPStatus + 控制器层/body.code）
- [x] 2.12 变更日志：`#10-#14` → `#10-#13`（#14 不存在）
- [x] 2.13 `points/earn` 说明：实现于 #12/#15，未单独归档
- [x] 2.14 §1.4 blockquote 移至表格下方
- [x] 2.15 API Key 配置路径：`~/.fbs/config.json`（不是 `workbuddy/channel-manifest.json`）
- [x] 2.16 函数名：`earnCredits()` → `earnPoints()`；`getUserBalance()` → `fetchUserInfo()`

---

## 任务 3：ERROR-CODES.md

**文件**: `docs/ERROR-CODES.md`

- [x] 3.1 总述判断规则：优先读 `body.code`（不是先查 HTTP Status）
- [x] 3.2 说明两层机制：认证过滤器 → 真实 HTTP 4xx；控制器 → body.code
- [x] 3.3 明确 `AjaxResult.error(msg)` 时真实 HTTP 仍为 200
- [x] 3.4 所有表格列名从 `HTTP` 改为 `body.code`
- [x] 3.5 所有"非 200"改为具体 body.code 值（500/403/409/404）
- [x] 3.6 每行加"真实 HTTP 仍为 200"说明
- [x] 3.7 §2.2（usage/consume）成功示例补充 `failReason: null`
- [x] 3.8 §2.5（points/earn）成功示例补充 `failReason: null`
- [x] 3.9 §2.7 重构为 body.code 描述（说明 400 是 Spring 默认，403/404/500 是 AjaxResult）
- [x] 3.10 §6 重写为"两层错误机制与状态码说明"（§6.1 过滤器层 + §6.2 控制器层）

---

## 任务 4：QUICK-START.md

**文件**: `docs/QUICK-START.md`

- [x] 4.1 cURL 示例标题：加"(Bash/Linux)"标注
- [x] 4.2 Windows/PowerShell 用户说明：建议使用 Python 或 Node.js
- [x] 4.3 FBS_API_BASE_URL 配置说明：只配主机地址，代码自动追加 `/fbs/skill-api`
- [x] 4.4 双斜杠错误示例：`https://api.U3W.com/fbs/skill-api` 会导致 404

---

## 任务 5：全面自查与修复

### 5.1 第一轮自查

- [x] 5.1.1 "calller" 拼写错误 → "caller"（5 处）
- [x] 5.1.2 三份文档内部术语一致性：body.code、真实 HTTP Status、两层错误机制
- [x] 5.1.3 三份文档共享错误码描述一致

### 5.2 第二轮复查

- [x] 5.2.1 所有 Skill API 响应示例与源码一致
- [x] 5.2.2 变更日志 #10-#13 范围正确，无 #14 引用
- [x] 5.2.3 无残留 "HTTP 409"、"HTTP 401"、"非 200" 等模糊描述

---

## 验收标准

### P0 - ✅ 全部完成

- [x] SDK-REFERENCE.md 所有 Skill API 响应示例与源码一致
- [x] 两层错误机制在三份文档中口径完全一致
- [x] 变更日志 #10-#13 准确，`points/earn` 标注实现于 #12/#15
- [x] §10 错误码参考缺失问题已解决

### P1 - ✅ 全部完成

- [x] QUICK-START.md cURL 示例有平台限制说明
- [x] API Key 配置路径准确（`~/.fbs/config.json`）
- [x] 企微同步端点数量准确（13 个），字段管理为 4 个

### P2 - ✅ 全部完成

- [x] 无拼写错误
- [x] 无残留 blockquote 破坏 Markdown 表格结构

---

## 归档信息

- **归档日期**: 2026-04-21
- **归档位置**: `openspec/archive/16-add-fbs-bookwriter-docs/`
- **产出文件**: `docs/SDK-REFERENCE.md`、`docs/QUICK-START.md`、`docs/ERROR-CODES.md`
