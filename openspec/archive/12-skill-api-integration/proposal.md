# Proposal: Skill 端 API 对接

> **Change ID**: `12-skill-api-integration`
> **日期**: 2026-04-17
> **依赖**: `11-frontend-api-key-management`（用户侧 API Key 管理）

---

## 背景

### 当前状态

**Skill 端**（FBS-BookWriter，外部仓库）：
- **代码位置**：`fbs-bookwriter/scripts/wecom/lib/`（相对于 Skill 仓库根目录）
- `entitlement.mjs`：检查用户权益，读取本地 `credits-ledger.json` 获取乐包余额
- `credits-ledger.mjs`：本地乐包账本，纯文件实现，不依赖网络

**后端**（U3W-AI）：
- `POST /fbs/skill-api/user/info`：返回用户积分余额 + 已激活场景包
- `POST /fbs/skill-api/usage/consume`：扣减积分（通过 `IPointsService.changePoints`）
- `POST /fbs/skill-api/rights/check`：权益校验
- API Key 认证：后端通过 API Key 反查 `user_id`（OpenSpec #11）

### 问题

1. **数据不同步**：Skill 端本地账本与后端数据库不一致
2. **双写风险**：用户在 Skill 端消费积分后，数据库积分未扣减
3. **体验割裂**：用户在后台查看的积分余额与 Skill 端显示不一致

---

## 目标

**MVP 目标**：Skill 端调用后端 API 实时扣减用户积分

**核心流程**：
```
用户在 WorkBuddy 配置 API Key
       ↓
Skill 读取配置，获取 API Key
       ↓
Skill 调用后端 API（带 API Key）
       ↓
后端通过 API Key 识别用户
       ↓
扣减该用户的积分
```

---

## 范围

### MVP 范围（必须完成）

#### 1. 后端改造现有接口

**1.1 改造 `/user/info`：userId 参数改为可选**

```http
POST /fbs/skill-api/user/info
X-FBS-API-Key: fbs_xxx

Request（可选 userId）:
{
  // userId 可不传，后端从 API Key 反查
}

Response:
{
  "code": 200,
  "data": {
    "userId": 1001,
    "pointsBalance": 500,
    "activatedPacks": [...]
  }
}
```

**改动说明**：
- 现有接口需要传 `userId`，改造后**可选**
- 不传 `userId` 时，从 API Key 反查
- 保持向后兼容：传 `userId` 时行为不变

**1.2 改造 `/usage/consume`：userId 参数改为可选**

```http
POST /fbs/skill-api/usage/consume
X-FBS-API-Key: fbs_xxx

Request:
{
  "packCode": "book-standard",
  "skillCode": "FBS-BookWriter",
  "usageRecordId": "uuid-xxx"
  // userId 可不传，后端从 API Key 反查
}
```

**改动说明**：
- 现有接口需要传 `userId`，改造后**可选**
- 不传 `userId` 时，从 API Key 反查
- **兼容层**：传 `userId` 时仍然有效（兼容旧调用）

#### 2. Skill 端改造

**2.1 新增 `backend-api.mjs`**

封装后端 API 调用：
- `getUserBalance()`：获取用户积分余额
- `consumeCredits()`：扣减用户积分
- 配置读取：从环境变量或配置文件读取 `FBS_API_KEY` + `FBS_API_BASE_URL`

**2.2 改造 `credits-ledger.mjs`**

**策略**：优先调用后端 API，失败时 fallback 到本地账本

```javascript
export function getBalance() {
  // 1. 优先调用后端 API
  if (isBackendAvailable()) {
    const remoteBalance = await fetchUserBalance();
    if (remoteBalance !== null) return remoteBalance;
  }
  
  // 2. Fallback：本地账本
  return _readLedgerRaw().balance;
}

export async function deductCredits(source, amount, note = '') {
  // 1. 优先调用后端 API
  if (isBackendAvailable()) {
    const result = await consumeCreditsViaApi(source, amount, note);
    if (result.success) {
      // 同步更新本地账本（缓存）
      _syncLocalLedger(result.newBalance);
      return result.newBalance;
    }
    throw new Error(result.failReason);
  }
  
  // 2. Fallback：本地账本
  return _deductLocalCredits(source, amount, note);
}
```

**2.3 改造 `entitlement.mjs`**

`_getBalance()` 改成异步调用 `credits-ledger.mjs` 的 `getBalance()`

#### 3. 配置管理

**3.1 用户配置 API Key**

用户在 WorkBuddy 配置：
```json
{
  "FBS_API_KEY": "fbs_abc123...",
  "FBS_API_BASE_URL": "https://api.example.com/fbs/skill-api"
}
```

**3.2 Skill 读取配置**

优先级：
1. 环境变量 `FBS_API_KEY` / `FBS_API_BASE_URL`
2. 配置文件 `~/.fbs/config.json`
3. 用户目录 `scene-packs/config.json`

---

### 明确不在范围内

1. **积分增加**：`addCredits()` 不改造（奖励积分由后端业务逻辑控制）
2. **离线模式**：MVP 要求有网络连接
3. **积分同步策略**：不处理历史数据迁移
4. **多用户切换**：MVP 只支持单一 API Key

---

## 验收标准

### P0（必须完成）

- [ ] 后端改造 `POST /fbs/skill-api/user/info`：`userId` 参数改为可选
- [ ] 后端改造 `POST /fbs/skill-api/usage/consume`：`userId` 参数改为可选
- [ ] Skill 端新增 `backend-api.mjs` 封装后端调用
- [ ] `credits-ledger.mjs` 改造：`getBalance()` / `deductCredits()` 调用后端 API
- [ ] `entitlement.mjs` 改造：`_getBalance()` 异步化
- [ ] 配置管理：用户可在 WorkBuddy 配置 API Key

### P1（优先完成）

- [ ] 后端 API 调用失败时 fallback 到本地账本
- [ ] 单元测试覆盖

### P2（后续迭代）

- [ ] 离线模式支持（本地账本 + 同步策略）
- [ ] 积分变更事件订阅（WebSocket / Webhook）

---

## 关键设计决策

### 决策 1：异步 vs 同步

**选择**：同步调用（阻塞）

**理由**：
- MVP 阶段保持简单
- Skill 端积分扣减是关键操作，必须同步确认
- 失败时可 fallback 到本地账本

### 决策 2：Fallback 策略

**选择**：后端不可用时降级到本地账本

**理由**：
- 保证 Skill 端可用性
- 本地账本作为离线备份
- 下次联网时可同步（后续迭代）

### 决策 3：用户身份识别

**选择**：后端通过 API Key 反查 userId

**理由**：
- OpenSpec #11 已实现 `fbs_api_key.user_id` 绑定
- Skill 端不需要管理 userId
- 安全性更高

---

## 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 后端 API 不可用 | Skill 端无法扣减积分 | Fallback 到本地账本 |
| 网络延迟 | 用户体验下降 | 超时设置（3s）+ 本地缓存 |
| API Key 泄露 | 用户积分被盗用 | 后端限流 + IP 白名单（后续） |

---

## 时间估算

| 任务 | 工时 |
|------|------|
| 后端改造现有接口（userId 可选） | 0.5 天 |
| Skill 端 `backend-api.mjs` | 1 天 |
| 改造 `credits-ledger.mjs` | 1 天 |
| 改造 `entitlement.mjs` | 0.5 天 |
| 配置管理 | 0.5 天 |
| 单元测试 | 0.5 天 |
| **总计** | **4 天** |

---

## 依赖

### 前置依赖

- ✅ OpenSpec #11（用户侧 API Key 管理）

### 外部依赖

- Skill 端代码仓库（FBS-BookWriter）
- 用户在 WorkBuddy 配置 API Key

---

## 后续迭代

1. **离线模式**：本地账本 + 同步策略
2. **积分变更通知**：WebSocket / Webhook
3. **多用户切换**：支持多个 API Key
4. **审计日志**：积分变更记录查询
