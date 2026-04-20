# Proposal: Skill 后端对接改造

> **Change ID**: `14-skill-backend-integration`
> **日期**: 2026-04-18
> **依赖**: `05-add-skill-api-gateway`（Skill API 网关）+ `10-credits-model-adaptation`（eventId 幂等模型，`/points/earn` 复用 `wx_points_record.uk_event_id`）+ `12-skill-api-integration`（后端 userId 可选改造 + activatedPacks 返回）

---

## Why

### 与 #12 的关系

**OpenSpec #12** 归档时包含两部分改动：
- ✅ **后端改动（仍在）**：`/user/info` 和 `/usage/consume` 的 userId 可选改造 + `activatedPacks` 返回，代码保留在 `FbsSkillApiController.java`
- ⚠️ **Skill 端改动（已恢复）**：`backend-api.mjs` 新增 + `credits-ledger.mjs` 异步化 + `entitlement.mjs` 异步化——因 Skill 仓库恢复，这些改动已不在

**#14 的定位**：重做 #12 的 Skill 端改动 + 新增行为积分上报能力（#12 明确排除的 `addCredits()` 上报 + `POST /points/earn`）

### 当前状态

**Skill 端**（FBStest01 / fbs-bookwriter v2.1.2，`~/.workbuddy/skills/FBStest01/`）：

| 模块 | 现状 | 问题 |
|------|------|------|
| `credits-ledger.mjs` | 本地读写 `credits-ledger.json`，通过 LedgerSync 工具定期从后端拉取余额覆盖本地 | 同步是定时/手动的，非实时；Skill 端行为积分不回传后端 |
| `entitlement.mjs` | `_getBalance()` 读本地 JSON / `enterprise.json` | 无法感知后端权益变化 |
| `verify-member.mjs` | 硬编码 `VERIFY_API_BASE = 'https://api.u3w.com'` | 不可配置，`FBS_API_KEY` 未使用 |
| `FBS_API_KEY` / `FBS_API_BASE_URL` | Skill 端环境变量已声明但**未被 Skill 代码引用**；LedgerSync 使用 `API_KEY` 环境变量（非 `FBS_API_KEY`）访问后端 | Skill 端模块与后端完全断开，同步依赖外部 LedgerSync 进程 |
| 行为积分（完章+10等） | 留在本地 `credits-ledger-log.jsonl` | 不回传后端，后端无感知 |

**后端**（U3W-AI）：

- 6 个 Skill API 端点已就绪（`/fbs/skill-api/*`，OpenSpec #5）
- API Key 反查 `user_id` 已实现（OpenSpec #11/#12，后端代码仍在）
- `/user/info` 已返回 `activatedPacks`（#12 后端改动仍在）
- `POST /fbs/skill-api/points/earn` 尚未实现（延期事项）

### 问题

1. **#12 Skill 端改动丢失**：`backend-api.mjs`、`credits-ledger.mjs` 异步化、`entitlement.mjs` 异步化需要重做
2. **单向同步延迟**：后端→本地的积分同步依赖 LedgerSync 定时/手动拉取，非实时
3. **积分黑洞**：Skill 端的行为积分（完章+10、完书+50等）留在本地，不回传后端（#12 明确排除，#14 补上）
4. **API Key 闲置**：Skill 端 `FBS_API_KEY` 未被代码引用（LedgerSync 用的是 `API_KEY`，不同变量），Skill 端模块无法直连后端

---

## What Changes

### Skill 端改造（仅 FBStest01）

| # | 类型 | 文件 | 改动 | 来源 |
|---|------|------|------|------|
| 1 | **新增** | `scripts/wecom/lib/backend-api.mjs` | 封装后端 Skill API 调用 + API Key 注入 + 超时/重试/错误处理 | #12 重做 |
| 2 | **新增** | `scripts/wecom/lib/api-key-config.mjs` | API Key 配置管理（环境变量→校验→缓存→刷新提示） | #14 新增（#12 内联在 backend-api 中，#14 拆为独立模块） |
| 3 | **改造** | `scripts/wecom/lib/credits-ledger.mjs` | `getBalance()` 优先后端 → fallback 本地；`deductCredits()` 优先后端扣减 → fallback 本地；`addCredits()` fire-and-forget 上报后端 | #12 重做（查询+扣减）+ #14 新增（行为积分上报） |
| 4 | **改造** | `scripts/wecom/lib/entitlement.mjs` | `_getBalance()` 改为异步，调用 `credits-ledger.getBalance()`（已含后端逻辑） | #12 重做 |
| 5 | **改造** | `scripts/wecom/verify-member.mjs` | `VERIFY_API_BASE` 改为读取 `FBS_API_BASE_URL` 环境变量（默认 `https://api.u3w.com`） | #14 新增 |
| 6 | **更新** | `SKILL.md` | 添加 API Key 配置指引、在线/离线模式说明 | #14 新增 |

### 后端改造

| # | 改动 | 说明 | 来源 |
|---|------|------|------|
| 1 | 新增 `POST /fbs/skill-api/points/earn` | Skill 行为积分上报（source + amount + usageRecordId 幂等） | #14 新增 |
| 2 | 兼容性小修 `/user/info` | 当 `scenePackMapper.selectById()` 返回 null 时，补 null 占位（`packCode`/`packName`/`packStatus`） | #14 小修（不影响现有功能） |
| ~~3~~ | ~~`/user/info` 返回值扩展~~ | ~~已由 #12 实现（后端代码仍在），无需后端改动~~ | ~~#12 已完成~~ |

> **注**：#12 后端改动仍在代码中——`/user/info` 和 `/usage/consume` 的 userId 可选 + `activatedPacks` 返回。#14 仅新增 `POST /points/earn` 端点。

---

## Impact

### 受影响的规范

| 规范 | 章节 | 变更类型 |
|------|------|----------|
| `platform-scene-pack-ops/spec.md` | 十二、Skill API 网关 | MODIFIED — 新增 `POST /fbs/skill-api/points/earn` 端点 |
| ~~`platform-scene-pack-ops/spec.md`~~ | ~~十二、Skill API 网关 > 用户信息查询~~ | ~~已由 #12 实现 activatedPacks，无需再改~~ |

### 受影响的代码（仅 Skill 端）

- `C:\Users\加号\.workbuddy\skills\FBStest01\scripts\wecom\lib\credits-ledger.mjs`
- `C:\Users\加号\.workbuddy\skills\FBStest01\scripts\wecom\lib\entitlement.mjs`
- `C:\Users\加号\.workbuddy\skills\FBStest01\scripts\wecom\verify-member.mjs`
- `C:\Users\加号\.workbuddy\skills\FBStest01\SKILL.md`

### 不受影响

- 后端 Java 代码：新增 1 个 API 端点（`POST /fbs/skill-api/points/earn`）+ 1 处兼容性小修（`/user/info` 的 activatedPacks null 占位），不改现有业务逻辑
- 前端 Vue 代码：无前端改动
- 企微智能表格：无改动
- 其他 Skill：不影响

---

## 明确不在本 OpenSpec 范围

1. ~~API Key 加密存储~~ → #15 安全加固
2. ~~请求签名/防篡改~~ → #15
3. ~~前端 API Key 配置界面优化~~ → 延期
4. ~~LedgerSync 增量同步改造~~ → 延期（优先级低于 Skill 端直连后端）
5. ~~scene-pack-loader.mjs 从后端加载场景包规则~~ → 延期（当前企微表格读取够用）
6. ~~离线同步策略（本地→后端增量同步）~~ → 延期
7. ~~多 API Key / 多用户切换~~ → 延期

---

## 验收标准

- [ ] Skill 启动时检测到 `FBS_API_KEY` 环境变量，日志显示「后端模式已启用」
- [ ] `getBalance()` 在有网络时返回后端余额，断网时 fallback 本地
- [ ] `deductCredits()` 在有网络时调后端扣减，断网时 fallback 本地
- [ ] `addCredits()` 在有网络时上报后端 `POST /fbs/skill-api/points/earn`，后端 `sys_user.points` 正确增加
- [ ] `FBS_API_BASE_URL` 可配置，默认 `https://api.u3w.com`
- [ ] `verify-member.mjs` 使用 `FBS_API_BASE_URL` 而非硬编码域名
- [ ] 无 API Key 时 graceful 降级为纯本地模式（向后兼容）
- [ ] 后端新增 `POST /fbs/skill-api/points/earn` 端点，支持幂等（usageRecordId）
- [ ] Skill 端 `backend-api.mjs` 正确解析 `/user/info` 返回的 `activatedPacks`（复用 #12 已有能力）

---

## 关键设计决策

### 决策 1：getBalance/deductCredits 异步化

**选择**：`getBalance()` 和 `deductCredits()` 改为 `async`

**理由**：
- 后端 API 调用是异步操作（`fetch`）
- 本地读写虽然同步，但统一为 async 可保持 API 一致性
- 调用方已有 async 上下文（`checkEntitlement` 是 `async function`）

### 决策 2：Fallback 策略

**选择**：后端不可用时降级到本地账本，不阻断主流程

**理由**：
- Skill 的核心价值是写作，不能因网络问题中断
- 本地账本作为离线备份保证可用性
- 下次联网时后端余额为权威值，本地会自动同步

### 决策 3：行为积分上报为 fire-and-forget

**选择**：`addCredits()` 上报后端采用 fire-and-forget 策略（不 await）

**理由**：
- 行为积分是正向激励，不应因上报失败阻断写作流程
- 本地已记录流水（`credits-ledger-log.jsonl`），后续可补报
- 后端 `points/earn` 有幂等 key（usageRecordId），重试安全

**关键补充**：
- `addCredits()` 入口处先生成 `usageRecordId`（UUID v4），写入本地流水 + 传给后端，保证重试幂等
- `checkDailyLogin()` / `checkFirstInstall()` 使用确定性格式 ID（`DL_{userId}_{date}` / `FI_{userId}`），与本地幂等语义对齐
- 后端 `eventId = usageRecordId`，复用 #10 的 `wx_points_record.uk_event_id` 幂等模型，不另起炉灶

### 决策 4：API Key 配置来源

**选择**：`FBS_API_KEY` 环境变量 → `~/.fbs/config.json` → 纯本地模式

**理由**：
- WorkBuddy 宿主支持环境变量注入
- 配置文件作为备选（开发/调试场景）
- 都没有时 graceful 降级，不影响纯本地使用

**⚠️ 与 #12 的不兼容变更**：#12 Skill 端配置使用 `scene-packs/backend-config.json`（Skill 仓库内）+ `FBS_API_KEY` 环境变量。#14 将配置文件改为 `~/.fbs/config.json`（用户目录），**不兼容旧文档示例**。理由：API Key 是用户级凭证，不应与 Skill 代码耦合（重新安装 Skill 时不应丢失配置）。迁移时需更新 SKILL.md 配置指引，旧路径 `scene-packs/backend-config.json` 不再读取。

---

## 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 后端 API 不可用 | Skill 端无法在线扣减积分 | Fallback 到本地账本 |
| 异步化改造影响现有调用方 | `getBalance()` 返回 Promise 而非 number | 逐模块改造，确保所有调用方使用 `await`；受影响模块：credits-ledger 内部（getUpgradeHint/notifyUpgradeIfNeeded/formatBalanceSummary）、entitlement（_getBalance）、verify-member（getMemberTier） |
| 本地与后端余额不一致 | 用户看到不同数值 | 有网络时后端为权威，本地仅缓存 |
| 后端 `/points/earn` 的 scenePackId=null | PointsServiceImpl 可能 NPE | 实施前确认 null 安全；不行则改用 changePoints 重载1 + 手动写记录 |
| API Key 泄露 | 用户积分被盗用 | 后端限流 + #15 安全加固 |

---

## 时间估算

| 任务 | 工时 |
|------|------|
| 新增 `backend-api.mjs` | 1 天 |
| 新增 `api-key-config.mjs` | 0.5 天 |
| 改造 `credits-ledger.mjs`（async + 后端优先） | 1 天 |
| 改造 `entitlement.mjs`（async _getBalance） | 0.5 天 |
| 改造 `verify-member.mjs`（可配置 API Base） | 0.5 天 |
| 后端新增 `/points/earn`（`/user/info` 已实现 activatedPacks） | 1 天 |
| SKILL.md 更新 + 集成测试 | 1 天 |
| **总计** | **5.5 天** |
