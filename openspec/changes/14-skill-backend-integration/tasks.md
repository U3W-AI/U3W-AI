# Tasks: Skill 后端对接改造

> **Change ID**: `14-skill-backend-integration`
> **日期**: 2026-04-18
>
> **与 #12 的关系**：阶段 1-2 中标注 `[#12重做]` 的任务是 #12 Skill 端改动的重做（因 Skill 仓库恢复），标注 `[#14新增]` 的是增量能力。

---

## 阶段 1：API Key 配置层

### 任务 1：新增 api-key-config.mjs — API Key 配置管理模块 `[#14新增]`

- [ ] 创建 `scripts/wecom/lib/api-key-config.mjs`，实现 `getApiKey()` / `getApiBaseUrl()` / `isBackendConfigured()` 三个导出函数
- [ ] 实现配置来源优先级：`FBS_API_KEY` 环境变量 → `~/.fbs/config.json` → 返回 null（纯本地模式）
- [ ] 实现 API Key 格式校验（`fbs_` 前缀 + 32位随机串），无效 Key 返回 null 并输出 warn 日志
- [ ] 实现 API Base URL 默认值 `https://api.u3w.com`，支持 `FBS_API_BASE_URL` 环境变量覆盖
- [ ] 在 Skill 启动时（intake-router）调用 `isBackendConfigured()`，输出「后端模式已启用」或「纯本地模式」日志

### 任务 2：新增 backend-api.mjs — 后端 API 调用封装 `[#12重做]`

- [ ] 创建 `scripts/wecom/lib/backend-api.mjs`，实现 `fetchUserInfo()` / `consumeCredits()` / `earnPoints()` / `checkRights()` 四个导出函数
- [ ] 实现 API Key 注入：所有请求携带 `X-FBS-API-Key` Header，从 `api-key-config.mjs` 读取
- [ ] 实现超时控制：fetch 请求 5s 超时（`AbortSignal.timeout`）
- [ ] 实现错误处理：网络不可达→返回 null（触发 fallback），HTTP 401/403→输出 warn 日志，HTTP 429→输出限流日志
- [ ] 实现 `earnPoints()` 的 fire-and-forget 封装：不 await 结果，失败仅写 stderr warn

---

## 阶段 2：积分模块改造

### 任务 3：改造 credits-ledger.mjs — 后端优先 + 本地 fallback `[#12重做 + #14新增]`

- [ ] 将 `getBalance()` 改为 async function：优先调用 `backend-api.fetchUserInfo()` → 成功返回 `data.pointsBalance` → 失败 fallback `_readLedgerRaw().balance`
- [ ] 将 `deductCredits()` 改为 async function：优先调用 `backend-api.consumeCredits()` → 成功同步本地账本余额 → 后端余额不足则 throw Error（不 fallback）→ 网络失败 fallback 本地扣减
- [ ] 改造 `addCredits()`：本地操作前生成 `usageRecordId`（UUID v4）→ 本地账本先加 → 写本地流水（含 usageRecordId）→ 异步调用 `backend-api.earnPoints()`（fire-and-forget）上报后端；**addCredits() 签名不变，仍同步返回 number**
- [ ] 保留 `_readLedgerRaw()` / `_writeLedger()` / `_appendLog()` 内部函数不变，新增 `_syncLocalLedger(newBalance)` 同步本地余额到后端值
- [ ] `readLedger()` 改为 async，增加 `fromBackend` 布尔字段标识余额来源
- [ ] `checkDailyLogin()` / `checkFirstInstall()` 内部本地操作后，额外调用 `backend-api.earnPoints()`（fire-and-forget），使用确定性格式 usageRecordId（`DL_{userId}_{date}` / `FI_{userId}`）
- [ ] `getUpgradeHint()` 改为 async（内部调用 `await getBalance()`）
- [ ] `notifyUpgradeIfNeeded()` 改为 async（内部调用 `await getBalance()`）
- [ ] `formatBalanceSummary()` 改为 async（内部调用 `await readLedger()` + `await getUpgradeHint()`）

### 任务 4：改造 entitlement.mjs — 异步化 _getBalance() `[#12重做]`

- [ ] 将 `_getBalance()` 改为 async function，内部调用 `await getBalance()`（已含后端逻辑）
- [ ] `checkEntitlement()` 已是 async function，内部 `await _getBalance()` 改动最小

### 任务 5：改造 verify-member.mjs — API Base URL 可配置 + getMemberTier() 异步化 `[#14新增]`

- [ ] 将 `VERIFY_API_BASE` 硬编码改为从 `api-key-config.getApiBaseUrl()` 动态读取
- [ ] `verifyMember()` 和 `verifyActivationCode()` 的 fetch URL 使用动态 base URL
- [ ] `getMemberTier()` 改为 async function（内部调用 `await getBalance()`，getBalance 改 async 后必须 await）
- [ ] CLI 入口 `--check` 改为 `const { tier, reason, balance } = await getMemberTier();`（需顶层 await 或包在 async IIFE 中）
- [ ] CLI 入口 `--activate` 已使用 `.then()` 模式，无需改动

---

## 阶段 3：后端新增 API

### 任务 6：后端新增 POST /fbs/skill-api/points/earn — 行为积分上报 `[#14新增]`

- [ ] 在 `FbsSkillApiController` 新增 `earnPoints()` 端点，接收 `{ source, amount, usageRecordId }` 请求体
- [ ] source 校验：白名单（chapter_done / book_complete / quality_pass / first_install / daily_login / s6_transform / release_ready），排除 scene_pack_use 和 manual
- [ ] source 直接映射为 ruleCode（1:1，命名一致），调用 `IPointsService.changePoints(userId, ruleCode=source, changeAmount=amount, scenePackId=null, usageRecordId, eventId=usageRecordId)`
- [ ] **复用 #10 幂等模型**：`eventId=usageRecordId`，由 `wx_points_record.uk_event_id` 唯一索引保证幂等，不需要额外查 `fbs_skill_usage_record`
- [ ] ⚠️ 实施前确认 `PointsServiceImpl` 对 `scenePackId=null` 不 NPE
- [ ] 写入 `fbs_skill_usage_record`（`host_type=WORKBUDDY, status=1`，`scene_pack_id=null`）
- [ ] 返回 `{ success, pointsAmount, remainPoints, usageRecordId }`
- [ ] 后端 `/user/info` 中 `activatedPacks` 字段缺失问题：当 `scenePackMapper.selectById()` 返回 null 时补 null 占位（`packInfo.put("packCode", null)` 等）

### 任务 7：验证并复用 #12 已有能力 — /user/info 已返回 activatedPacks `[#12后端已完成]`

> 后端 `/user/info` 已由 #12 实现返回 `activatedPacks`（含 packId/packCode/packName/packStatus/status/expiresAt），无需任何后端改动。

- [ ] 验证：确认后端 `POST /fbs/skill-api/user/info` 已返回 `activatedPacks` 字段（#12 已实现）
- [ ] Skill 端：`backend-api.mjs` 的 `fetchUserInfo()` 返回值中正确解析 `activatedPacks` 数组
- [ ] 可选增强：在 `entitlement.mjs` 中利用 `activatedPacks` 替代本地 `enterprise.json` 校验（MVP 可跳过）

---

## 阶段 4：SKILL.md 更新 + 集成验证

### 任务 8：更新 SKILL.md 配置指引 + 端到端集成测试 `[#14新增]`

- [ ] SKILL.md 添加「API Key 配置」章节：环境变量说明 + 配置文件格式 + 在线/离线模式说明
- [ ] 集成测试：设置 `FBS_API_KEY` 环境变量，验证 Skill 启动日志显示「后端模式已启用」
- [ ] 集成测试：验证 `getBalance()` 返回后端余额（对比 `/user/info` API 直接调用）
- [ ] 集成测试：验证 `deductCredits()` 调用后端扣减（检查 `sys_user.points` 变化）
- [ ] 集成测试：验证 `addCredits()` 上报后端（检查 `/points/earn` 返回成功）
- [ ] 集成测试：移除 `FBS_API_KEY`，验证 graceful 降级为纯本地模式
