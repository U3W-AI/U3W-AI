# Design: Skill 后端对接改造

> **Change ID**: `14-skill-backend-integration`
> **日期**: 2026-04-18

> **与 #12 的关系**：#12 的后端改动（userId 可选 + activatedPacks）仍在代码中，Skill 端改动因仓库恢复已丢失。本 design 中 Skill 端模块设计包含 #12 的重做 + #14 的增量（`points/earn`、`addCredits()` 上报、`api-key-config.mjs`、`verify-member.mjs` 可配置）。

---

## 1. 架构概览

```
┌─────────────────────────────────────────────────────────┐
│  Skill 端 (FBStest01)                                   │
│                                                         │
│  ┌─────────────┐    ┌──────────────┐    ┌────────────┐  │
│  │ credits-    │───▶│ backend-api  │───▶│ 后端 Skill │  │
│  │ ledger.mjs  │◀───│   .mjs       │◀───│    API     │  │
│  │ (改造)      │    │ (新增)       │    │ (/fbs/     │  │
│  └──────┬──────┘    └──────┬───────┘    │ skill-api) │  │
│         │                  │            └────────────┘  │
│  ┌──────▼──────┐    ┌──────▼───────┐                     │
│  │ entitlement │    │ api-key-     │                     │
│  │   .mjs      │    │ config.mjs   │                     │
│  │ (改造)      │    │ (新增)       │                     │
│  └─────────────┘    └──────────────┘                     │
│                                                         │
│  ┌─────────────────┐                                    │
│  │ verify-member   │                                    │
│  │   .mjs (改造)   │                                    │
│  └─────────────────┘                                    │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 核心契约：在线/离线切换模型

### 2.1 两种操作，两种策略

| 操作类型 | 代表函数 | 策略 | 是否 await 后端 | 失败处理 |
|----------|----------|------|-----------------|----------|
| **查询类** | `getBalance()` | 后端优先 + fallback 本地 | ✅ 是（await 5s 超时） | 返回本地值 |
| **扣减类** | `deductCredits()` | 后端优先 + fallback 本地 | ✅ 是（await 5s 超时） | 网络失败→本地扣减；业务失败（余额不足）→不扣减，throw Error |
| **奖励类** | `addCredits()` | **本地立即生效 + 异步投递后端** | ❌ 否（fire-and-forget） | 不回滚本地，仅 warn 日志 |

### 2.2 关键契约（MUST）

1. **本地余额是权威快照**：`addCredits()` 执行后本地余额立即更新，返回新余额（`number`），调用方无需 await
2. **后端上报是尽力投递**：`addCredits()` 内部调用 `backend-api.earnPoints()` 时不 await，失败不回滚本地余额
3. **后端余额是联网权威**：`getBalance()` 联网成功时以 `sys_user.points` 为准，同时覆盖本地账本（`_syncLocalLedger`）
4. **离线不阻断主流程**：所有后端调用失败时 graceful 降级到本地，不抛异常到上层

### 2.3 不允许的假设

- ❌ "后端成功才算加分" → 正确：本地加分立即生效
- ❌ "后端失败需要回滚本地" → 正确：不回滚
- ❌ "addCredits 需要等后端返回再继续" → 正确：fire-and-forget
- ❌ "扣减后端失败就不扣" → 正确：网络失败 fallback 本地扣减；但后端返回"余额不足"时不应 fallback

### 2.4 与 LedgerSync 的关系

改造前：
- 后端→本地积分同步：通过 **LedgerSync**（`ledgersync.py --once`）定时/手动从后端 `sys_user.points` 拉取余额，覆盖本地 `credits-ledger.json`。LedgerSync 使用 `API_KEY` 环境变量（非 `FBS_API_KEY`）认证
- 本地→后端积分同步：**无**（行为积分留在本地，后端无感知）

改造后：
- 后端→本地积分同步：**`getBalance()` 实时查后端**，取代 LedgerSync 的拉取角色（LedgerSync 仍可保留作为离线后的补对工具）
- 本地→后端积分同步：**`addCredits()` → `earnPoints()` fire-and-forget 上报**，解决"积分黑洞"
- LedgerSync 定位变化：从"唯一同步通道"降级为"离线后对账辅助工具"

---

## 3. 模块设计

### 3.1 `api-key-config.mjs`（新增）

```
导出函数：
  getApiKey(): string | null
  getApiBaseUrl(): string
  isBackendConfigured(): boolean

配置来源优先级：
  1. FBS_API_KEY 环境变量
  2. ~/.fbs/config.json → { "FBS_API_KEY": "fbs_xxx" }
  3. null → 纯本地模式

> **与 #12 的差异**：#12 使用 `scene-packs/backend-config.json`（Skill 仓库内），#14 改为 `~/.fbs/config.json`（用户目录）。
> 理由：API Key 是用户级凭证，不应与 Skill 代码耦合；用户重新安装 Skill 时不应丢失 API Key 配置。
> LedgerSync 已使用 `API_KEY` 环境变量，#14 的 `~/.fbs/config.json` 为非环境变量的备选方案。

API Key 校验规则：
  - 前缀 "fbs_"
  - 后跟 32 位随机串（[a-zA-Z0-9]）
  - 无效 Key → 返回 null + stderr warn
```

### 3.2 `backend-api.mjs`（新增）

```
导出函数：
  fetchUserInfo(): Promise<{ userId, pointsBalance, activatedPacks } | null>
  consumeCredits({ packCode, skillCode, usageRecordId }): Promise<{ success, pointsAmount, remainPoints } | null>
  earnPoints({ source, amount, usageRecordId }): void  // fire-and-forget
  checkRights({ packCode }): Promise<{ hasRights, ... } | null>

设计要点：
  - 所有请求携带 X-FBS-API-Key Header（从 api-key-config 读取）
  - isBackendConfigured() === false → 所有函数直接返回 null（不发请求）
  - fetch 5s 超时（AbortSignal.timeout）
  - 网络错误 → 返回 null + stderr warn
  - HTTP 401/403 → stderr warn + 返回 null
  - HTTP 429 → stderr warn（限流）+ 返回 null
  - earnPoints() 不 await，失败仅 stderr warn
```

### 3.3 `credits-ledger.mjs`（改造）

```
改造前 → 改造后：
  getBalance(): number          → async getBalance(): Promise<number>
  deductCredits(s, a, n): number → async deductCredits(s, a, n): Promise<number>
  addCredits(s, a, n): number   → addCredits(s, a, n): number  // 签名不变！
  readLedger(): object          → async readLedger(): Promise<object & { fromBackend: boolean }>
  getUpgradeHint(): 同步         → async getUpgradeHint(): Promise<object>  // 内部调 getBalance()
  notifyUpgradeIfNeeded(): 同步  → async notifyUpgradeIfNeeded(): Promise<object>  // 内部调 getBalance()
  formatBalanceSummary(): 同步   → async formatBalanceSummary(): Promise<string>  // 内部调 readLedger() + getUpgradeHint()

addCredits() 内部流程（关键）：
  1. 生成 usageRecordId（UUID v4）            ← 同步，在本地操作前生成
  2. 本地账本先加（_writeLedger）             ← 同步
  3. 写本地流水（_appendLog，含 usageRecordId） ← 同步
  4. 异步投递后端 earnPoints({ source, amount, usageRecordId })  ← 不 await，使用第1步生成的 ID
  5. 返回本地新余额（number）                 ← 同步返回

usageRecordId 生成策略：
  - 时机：在 addCredits() 入口处立即生成（本地操作前）
  - 格式：UUID v4（与 deductCredits 一致，复用 _generateUsageRecordId()）
  - 幂等保证：同一个 usageRecordId 写入本地流水 + 传给后端 earnPoints()，
    后端根据 usageRecordId 去重，重试安全

getBalance() 内部流程：
  1. 检查 isBackendConfigured() → false → 直接返回本地
  2. await fetchUserInfo() → 5s 超时
  3. 成功 → _syncLocalLedger(data.pointsBalance) → 返回后端值
  4. 失败/超时 → 返回 _readLedgerRaw().balance

deductCredits() 内部流程：
  1. 检查 isBackendConfigured() → false → 本地扣减
  2. await consumeCredits() → 5s 超时
  3. 成功（后端扣减通过）→ _syncLocalLedger(data.remainPoints) → 返回后端值
  4. 后端返回余额不足（业务失败）→ **不扣减**，throw Error（与当前本地行为一致）
  5. 后端不可达/超时（网络失败）→ 本地扣减 → 返回本地新余额（fallback）
  6. 后端返回其他错误 → 本地扣减 → 返回本地新余额（fallback）

> **关键区分**：后端"余额不足"是业务错误，不应 fallback（本地余额也不够）；后端"不可达"是网络问题，应 fallback（本地可能够）。
```

### 3.4 `entitlement.mjs`（改造）

```
改造前 → 改造后：
  _getBalance(): number    → async _getBalance(): Promise<number>
  checkEntitlement(): 已是 async → 内部 await _getBalance() 改动最小
```

> **注**：`getUpgradeHint()` / `notifyUpgradeIfNeeded()` / `formatBalanceSummary()` 虽然在 §4.1 影响面中曾列为 entitlement.mjs 的改造项，但实际代码中这三个函数**都在 credits-ledger.mjs 中定义**（entitlement.mjs 仅 re-export）。它们的异步化改造归属于 §3.3 credits-ledger.mjs 的改造范围。

### 3.5 `verify-member.mjs`（改造）

```
改造：
  VERIFY_API_BASE 硬编码 → 从 api-key-config.getApiBaseUrl() 动态读取
  verifyMember() / verifyActivationCode() 的 fetch URL 使用动态 base URL
  CLI 入口（--check / --activate）保持兼容
```

---

## 4. 异步化影响面

### 4.1 `getBalance()` 改 async 后的调用方

| 调用方 | 文件 | 当前用法 | 改造方式 |
|--------|------|----------|----------|
| `_getBalance()` | entitlement.mjs | 同步调用 | 改为 await |
| `getMemberTier()` | verify-member.mjs | 同步调用 | 改为 async + await |
| `getUpgradeHint()` | credits-ledger.mjs | 同步调用 getBalance() | 改为 async + await |
| `notifyUpgradeIfNeeded()` | credits-ledger.mjs | 同步调用 getBalance() | 改为 async + await |
| `formatBalanceSummary()` | credits-ledger.mjs | 同步调用 readLedger() + getUpgradeHint() | 改为 async + await |
| `checkDailyLogin()` | credits-ledger.mjs | 不调用 getBalance | 无影响 |
| `checkFirstInstall()` | credits-ledger.mjs | 不调用 getBalance | 无影响 |

### 4.2 `addCredits()` 签名不变的保证

`addCredits()` 保持同步返回 `number`，是因为：
- 调用方（checkDailyLogin / checkFirstInstall / S0-S6 写作流程）不感知后端
- 后端上报是 fire-and-forget，不改变返回值语义
- 本地积分立即生效，这是用户感知的"加分"

### 4.3 `checkDailyLogin()` / `checkFirstInstall()` 的后端上报

当前代码：这两个函数内部直接操作 `_readLedgerRaw()` + `_writeLedger()`，不走 `addCredits()`。

改造方案：在这两个函数内部，本地操作完成后，额外调用 `backend-api.earnPoints()`（fire-and-forget），与 `addCredits()` 行为一致。

#### 两层幂等协调

本地和后端各有独立的幂等机制，需协调 `usageRecordId` 确保两层幂等对齐：

| 函数 | 本地幂等 | usageRecordId（= 后端 eventId） | 说明 |
|------|----------|-------------------------------|------|
| `checkDailyLogin()` | `ledger.last_daily_login === today` | `'DL_' + userId + '_' + today`（如 `DL_1_2026-04-18`） | 同时写入 `wx_points_record.event_id`，复用 #10 幂等 |
| `checkFirstInstall()` | `ledger.first_install_done` | `'FI_' + userId`（如 `FI_1`） | 同时写入 `wx_points_record.event_id`，复用 #10 幂等 |

**设计选择**：
- `usageRecordId` 使用**确定性格式**（非 UUID v4），因为这两个函数有明确的业务幂等语义（同一天/同一次安装）
- `usageRecordId` 传给后端后直接映射为 `eventId`（见 §5.1），复用 #10 已建立的 `uk_event_id` 幂等屏障
- 确定格 ID 与 #10 的 `event_id` 生成规则对齐：`first_install_{userId}` / `daily_login_{userId}_{date}`（#10 见 §2.4）

---

## 5. 后端新增端点设计

### 5.1 `POST /fbs/skill-api/points/earn`

```
请求体：
{
  "source": "chapter_done",       // CREDIT_SOURCES 正向来源 key，1:1 映射为后端 ruleCode
  "amount": 10,                   // > 0
  "usageRecordId": "uuid-xxx"     // 幂等 key（由 Skill 端在 addCredits/checkDailyLogin/checkFirstInstall 中生成）
}

响应 200：
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": true,
    "pointsAmount": 10,
    "remainPoints": 1461,         // sys_user.points 当前值
    "usageRecordId": "uuid-xxx"
  }
}
```

#### source → ruleCode 映射规则

**决策**：`source` 与后端 `ruleCode` **1:1 直接映射**，两者命名完全一致。

| source（Skill 端 CREDIT_SOURCES key） | ruleCode（后端 changePoints 参数） | amount |
|----------------------------------------|-----------------------------------|--------|
| `chapter_done` | `chapter_done` | 10 |
| `book_complete` | `book_complete` | 50 |
| `quality_pass` | `quality_pass` | 3 |
| `first_install` | `first_install` | 100 |
| `daily_login` | `daily_login` | 5 |
| `s6_transform` | `s6_transform` | 12 |
| `release_ready` | `release_ready` | 8 |

**排除**：`scene_pack_use`（消耗类）/ `manual`（手动调整类），这两类不应通过 Skill API 上报。

#### 后端实现：调用 IPointsService.changePoints() 重载3

```
changePoints(userId, ruleCode=source, changeAmount=amount, scenePackId=null, usageRecordId, eventId=usageRecordId)
```

- **scenePackId = null**：行为积分不属于某个场景包的消费，传 null
- **eventId = usageRecordId**：复用 #10 已建立的幂等模型，`usageRecordId` 同时作为 `wx_points_record.event_id` 写入，由 `uk_event_id` 唯一索引保证幂等
- **⚠️ 实施前确认**：需验证 `PointsServiceImpl` 对 `scenePackId=null` 不会 NPE（写记录时 `fbs_skill_usage_record.scene_pack_id` 允许为 null）

> **与 #10 幂等模型的关系**：#10 归档建立了 `wx_points_record.event_id` 幂等机制。#14 的 `/points/earn` 不另起炉灶，而是将 Skill 端的 `usageRecordId` 直接映射为 `eventId`，复用 `uk_event_id` 唯一索引做幂等。这样全链路只有一套幂等模型，`usageRecordId` = `fbs_skill_usage_record.usage_record_id` = `wx_points_record.event_id`。

#### 幂等逻辑

- `changePoints(eventId=usageRecordId)` 内部先查 `wx_points_record` 的 `uk_event_id`
- 命中 → 幂等返回（#10 已实现：`return AjaxResult.success("积分操作成功（幂等）", existing.getBalanceAfter())`）
- 未命中 → 正常执行积分变动 + 写 `wx_points_record`（含 event_id=usageRecordId）+ 写 `fbs_skill_usage_record`
- **不需要额外查 `fbs_skill_usage_record` 做幂等**——`wx_points_record.uk_event_id` 已经是幂等屏障

### 5.2 `/user/info` — 复用 #12 已有能力

无需后端改动。已返回 `activatedPacks` 字段：
```
{
  "userId": 1,
  "pointsBalance": 1461,
  "activatedPacks": [{
    "packId": 1,
    "packCode": "startup-advisor",
    "packName": "创业顾问",
    "packStatus": 1,         // 场景包上架状态
    "status": 1,             // 用户权益状态
    "expiresAt": "2026-12-31"
  }]
}
```

⚠️ **字段缺失风险**：当 `fbs_scene_pack` 记录不存在（被删除）时，后端不会设置 `packCode`/`packName`/`packStatus`（key 不存在，而非 null）。Skill 端 `backend-api.mjs` 解析时必须防御性读取：
- 使用 `pack.packCode ?? null` 而非 `pack.packCode`
- 后端侧建议补 `else { packInfo.put("packCode", null); ... }` null 占位（可作为 #14 实施时的小 fix，不单独开 spec）

---

## 6. 错误处理矩阵

| 场景 | 函数 | 行为 |
|------|------|------|
| 无 API Key | 所有 backend-api 函数 | 直接返回 null，不发请求 |
| 网络不可达 | fetchUserInfo / consumeCredits | 返回 null + stderr warn |
| 请求超时（5s） | fetchUserInfo / consumeCredits | 返回 null + stderr warn |
| HTTP 401 | 所有函数 | 返回 null + stderr "API Key 无效" |
| HTTP 429 | 所有函数 | 返回 null + stderr "请求限流" |
| earnPoints 网络失败 | earnPoints | stderr warn，不影响本地流程 |
| earnPoints HTTP 错误 | earnPoints | stderr warn，不影响本地流程 |

---

## 7. 与 #10 的依赖关系

#10（credits-model-adaptation）建立了 `wx_points_record.event_id` 幂等机制：
- `IPointsService.changePoints()` 重载3 支持 `eventId` 参数
- `uk_event_id` 唯一索引保证幂等
- `PointsServiceImpl` 已实现：`StringUtils.hasText(eventId)` → 查 `wx_points_record`，命中则幂等返回

#14 的 `/points/earn` 直接复用此模型：**`eventId = usageRecordId`**，不另起幂等体系。

> **注意**：#13（wecom-commercial-hub-field-sync）是企微智能表格字段补全，与 `activatedPacks` 数据源无关。`activatedPacks` 来自 `fbs_user_pack JOIN fbs_scene_pack`（#12 已实现），不依赖 #13。
