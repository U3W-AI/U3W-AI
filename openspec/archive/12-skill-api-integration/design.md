# Design: Skill 端 API 对接

> **Change ID**: `12-skill-api-integration`
> **日期**: 2026-04-17
> **依赖**: `11-frontend-api-key-management`（用户侧 API Key 管理）

---

## 1. 系统架构

### 1.1 整体流程

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   WorkBuddy     │     │   Skill 端      │     │   后端 API     │
│   配置 API Key  │────▶│   读取配置      │────▶│   扣减积分     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                               │                        │
                               │                        ▼
                               │               ┌─────────────────┐
                               └──────────────▶│   数据库        │
                                               │   sys_user.points│
                                               └─────────────────┘
```

### 1.2 认证流程

```
Skill 端                         后端 API
   │                               │
   │  POST /fbs/skill-api/xxx      │
   │  Header: X-FBS-API-Key: fbs_xxx
   │──────────────────────────────▶│
   │                               │
   │                        查询 fbs_api_key 表
   │                        反查 user_id
   │                               │
   │                        校验权限（status=1）
   │                               │
   │  Response: {userId, points}    │
   │◀──────────────────────────────│
```

---

## 2. 后端改造

### 2.1 改造现有接口：`/user/info`

**端点**：`POST /fbs/skill-api/user/info`

**改动**：`userId` 参数改为可选，不传时从 API Key 反查。

**Controller**：`FbsSkillApiController.java`

**当前实现**（第 238-276 行）：
```java
@PostMapping("/user/info")
public AjaxResult userInfo(@RequestBody SkillApiUserInfoRequest request) {
    if (request.getUserId() == null) {
        return AjaxResult.error("用户ID不能为空");
    }
    // ...
}
```

**改造后**：
```java
@PostMapping("/user/info")
public AjaxResult userInfo(@RequestBody SkillApiUserInfoRequest request) {
    // 1. 从 API Key 获取 userId（优先）
    FbsApiKey apiKey = getCurrentApiKey();
    Long userId = (apiKey != null && apiKey.getUserId() != null) 
                  ? apiKey.getUserId() 
                  : request.getUserId();
    
    if (userId == null) {
        return AjaxResult.error(403, "无法识别用户（API Key 未绑定且未传 userId）");
    }
    
    // 2. 其余逻辑不变
    Integer pointsBalance = pointsService.getUserPoints(userId);
    // ...
}
```

**响应示例**（不变）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": 1001,
    "pointsBalance": 500,
    "activatedPacks": [...]
  }
}
```

---

### 2.2 改造现有接口：`/usage/consume`

**端点**：`POST /fbs/skill-api/usage/consume`

**改动**：`userId` 参数改为可选，不传时从 API Key 反查。

**当前实现**（第 87-111 行）：
```java
@PostMapping("/usage/consume")
public AjaxResult usageConsume(@RequestBody SkillApiConsumeRequest request) {
    if (request.getUserId() == null || ...) {
        return AjaxResult.error("参数不能为空");
    }
    // ...
}
```

**改造后**：
```java
@PostMapping("/usage/consume")
public AjaxResult usageConsume(@RequestBody SkillApiConsumeRequest request) {
    // 1. 从 API Key 获取 userId（优先）
    FbsApiKey apiKey = getCurrentApiKey();
    Long userId = (apiKey != null && apiKey.getUserId() != null) 
                  ? apiKey.getUserId() 
                  : request.getUserId();
    
    if (userId == null) {
        return AjaxResult.error(403, "无法识别用户（API Key 未绑定且未传 userId）");
    }
    
    // 2. 校验其他参数
    if (!StringUtils.hasText(request.getPackCode())
            || !StringUtils.hasText(request.getUsageRecordId()) 
            || !StringUtils.hasText(request.getSkillCode())) {
        return AjaxResult.error("参数不能为空");
    }
    
    // 3. 其余逻辑不变（使用上面确定的 userId）
    // ...
}
```

**说明**：
- `userId` 改为可选参数
- **兼容层**：传 `userId` 时仍然有效（兼容旧调用）
- 新 Skill 集成应不传 `userId`，让后端从 API Key 反查

---

## 3. Skill 端改造

**代码位置**：`fbs-bookwriter/scripts/wecom/lib/`（相对于 Skill 仓库根目录）

> **注意**：实施时需确认 Skill 仓库具体路径。新手指南见 `skill-dev-guide.md`。

### 3.1 新增 `backend-api.mjs`

**职责**：封装后端 API 调用，处理认证、超时、错误重试。

**文件路径**：`backend-api.mjs`（相对于上述 lib 目录）

```javascript
#!/usr/bin/env node
/**
 * scripts/wecom/lib/backend-api.mjs
 * 后端 API 封装
 *
 * 职责：
 *   - 封装后端 REST API 调用
 *   - 处理 API Key 认证
 *   - 超时控制（默认 3s）
 *   - 错误重试（可选）
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { resolveWecomDataPaths, C } from './utils.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../../..');
const { scenePacksDir } = resolveWecomDataPaths(ROOT);

const CONFIG_PATH = path.join(scenePacksDir, 'backend-config.json');

// 默认配置
const DEFAULT_CONFIG = {
  apiBaseUrl: null,
  apiKey: null,
  timeoutMs: 3000,
};

// ─────────────────────────────────────────────
// 配置读取
// ─────────────────────────────────────────────

function loadConfig() {
  // 1. 优先环境变量
  const envApiKey = process.env.FBS_API_KEY?.trim();
  const envApiBaseUrl = process.env.FBS_API_BASE_URL?.trim();
  
  if (envApiKey && envApiBaseUrl) {
    return {
      apiBaseUrl: envApiBaseUrl,
      apiKey: envApiKey,
      timeoutMs: DEFAULT_CONFIG.timeoutMs,
    };
  }
  
  // 2. 配置文件
  if (fs.existsSync(CONFIG_PATH)) {
    try {
      const config = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
      return { ...DEFAULT_CONFIG, ...config };
    } catch {
      // 解析失败，返回默认
    }
  }
  
  return DEFAULT_CONFIG;
}

/**
 * 检查后端是否可用
 */
export function isBackendAvailable() {
  const config = loadConfig();
  return !!(config.apiKey && config.apiBaseUrl);
}

/**
 * 获取用户积分余额（调用 /user/info，不传 userId）
 * @returns {Promise<{success: boolean, userId?: number, pointsBalance?: number, error?: string}>}
 */
export async function getUserBalance() {
  const config = loadConfig();
  if (!config.apiKey || !config.apiBaseUrl) {
    return { success: false, error: 'API Key 或 Base URL 未配置' };
  }
  
  const url = `${config.apiBaseUrl}/user/info`;  // 改造后的接口
  
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), config.timeoutMs);
    
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-FBS-API-Key': config.apiKey,  // 注意：是 X-FBS-API-Key
      },
      body: JSON.stringify({}),  // 不传 userId，让后端反查
      signal: controller.signal,
    });
    
    clearTimeout(timeoutId);
    
    if (!response.ok) {
      return { success: false, error: `HTTP ${response.status}` };
    }
    
    const result = await response.json();
    
    if (result.code !== 200) {
      return { success: false, error: result.msg || '未知错误' };
    }
    
    return {
      success: true,
      userId: result.data.userId,
      pointsBalance: result.data.pointsBalance,
    };
  } catch (err) {
    if (err.name === 'AbortError') {
      return { success: false, error: '请求超时' };
    }
    return { success: false, error: err.message };
  }
}

/**
 * 扣减用户积分
 * @param {string} packCode - 场景包编码
 * @param {string} skillCode - Skill 编码
 * @param {string} usageRecordId - 使用记录 ID（幂等）
 * @param {number} pointsAmount - 扣减积分数量
 * @returns {Promise<{success: boolean, remainPoints?: number, error?: string}>}
 */
export async function consumeCredits(packCode, skillCode, usageRecordId, pointsAmount) {
  const config = loadConfig();
  if (!config.apiKey || !config.apiBaseUrl) {
    return { success: false, error: 'API Key 或 Base URL 未配置' };
  }
  
  const url = `${config.apiBaseUrl}/usage/consume`;
  
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), config.timeoutMs);
    
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-FBS-API-Key': config.apiKey,  // 注意：是 X-FBS-API-Key
      },
      body: JSON.stringify({
        packCode,
        skillCode,
        usageRecordId,
        // 不传 userId，让后端从 API Key 反查
      }),
      signal: controller.signal,
    });
    
    clearTimeout(timeoutId);
    
    if (!response.ok) {
      return { success: false, error: `HTTP ${response.status}` };
    }
    
    const result = await response.json();
    
    if (result.code !== 200) {
      return { success: false, error: result.msg || '未知错误' };
    }
    
    return {
      success: true,
      usageRecordId: result.data.usageRecordId,
      remainPoints: result.data.remainPoints,
    };
  } catch (err) {
    if (err.name === 'AbortError') {
      return { success: false, error: '请求超时' };
    }
    return { success: false, error: err.message };
  }
}

export default {
  isBackendAvailable,
  getUserBalance,
  consumeCredits,
};
```

---

### 3.2 改造 `credits-ledger.mjs`

**策略**：优先调用后端 API，失败时 fallback 到本地账本。

**关键改动**：

```javascript
import { isBackendAvailable, getUserBalance, consumeCredits } from './backend-api.mjs';

// ─────────────────────────────────────────────
// getBalance：优先后端 API
// ─────────────────────────────────────────────

/**
 * 读取当前乐包余额
 * 优先后端 API，失败时 fallback 到本地账本
 */
export function getBalance() {
  return _getBalanceAsync();
}

/**
 * 异步获取余额
 */
async function _getBalanceAsync() {
  // 1. 优先后端 API
  if (isBackendAvailable()) {
    try {
      const result = await getUserBalance();
      if (result.success) {
        // 同步更新本地缓存（可选）
        _updateLocalCache(result.pointsBalance);
        return result.pointsBalance;
      }
      // 后端失败，输出警告，fallback
      process.stderr.write(`${C.yellow}[乐包] 后端 API 失败：${result.error}，fallback 到本地账本${C.reset}\n`);
    } catch (err) {
      process.stderr.write(`${C.yellow}[乐包] 后端 API 异常：${err.message}，fallback 到本地账本${C.reset}\n`);
    }
  }
  
  // 2. Fallback：本地账本
  return _readLedgerRaw().balance;
}

// ─────────────────────────────────────────────
// deductCredits：优先后端 API
// ─────────────────────────────────────────────

/**
 * 扣减乐包（用于兑换/解锁场景包）
 * 优先后端 API，失败时 fallback 到本地账本
 */
export async function deductCredits(source, amount, note = '') {
  const delta = typeof amount === 'number' ? Math.abs(amount) : 0;
  if (delta <= 0) return getBalance();
  
  // 1. 优先后端 API（需要配置 usageRecordId）
  if (isBackendAvailable()) {
    const usageRecordId = _generateUsageRecordId();
    const result = await consumeCredits(source, 'FBS-BookWriter', usageRecordId, delta);
    
    if (result.success) {
      // 同步更新本地账本（缓存）
      const ledger = _readLedgerRaw();
      ledger.balance = result.remainPoints;
      ledger.total_spent += delta;
      _writeLedger(ledger);
      _appendLog({ event: 'deduct', source, amount: delta, balance_after: result.remainPoints, note, mode: 'backend' });
      
      emitCreditsLog(
        `${C.yellow}[乐包] -${delta} 个乐包 (后端) → ${result.remainPoints} 个乐包${C.reset}`,
      );
      return result.remainPoints;
    }
    
    // 后端失败，输出警告，fallback
    process.stderr.write(`${C.yellow}[乐包] 后端扣减失败：${result.error}，fallback 到本地账本${C.reset}\n`);
  }
  
  // 2. Fallback：本地账本
  return _deductLocalCredits(source, delta, note);
}

/**
 * 本地扣减（Fallback）
 */
function _deductLocalCredits(source, amount, note) {
  const ledger = _readLedgerRaw();
  if (ledger.balance < amount) {
    throw new Error(`乐包不足：需要 ${amount} 个，当前余额 ${ledger.balance} 个`);
  }
  ledger.balance -= amount;
  ledger.total_spent += amount;
  _writeLedger(ledger);
  _appendLog({ event: 'deduct', source, amount, balance_after: ledger.balance, note, mode: 'local' });
  
  emitCreditsLog(
    `${C.yellow}[乐包] -${amount} 个乐包 (本地) → ${ledger.balance} 个乐包${C.reset}`,
  );
  return ledger.balance;
}

/**
 * 生成使用记录 ID（UUID v4）
 */
function _generateUsageRecordId() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}
```

---

### 3.3 改造 `entitlement.mjs`

**关键改动**：`_getBalance()` 异步化。

```javascript
/**
 * 检查用户是否有权使用指定体裁
 * @returns {Promise<{allowed: boolean, genre: string, mode: 'free'|'paid'|'fallback'}>}
 */
export async function checkEntitlement(genre, corpConfig, entitlementSheet, bookRoot) {
  // general 永久免费，直接放行
  if (genre === 'general') {
    return { allowed: true, genre: 'general', mode: 'free' };
  }
  
  const threshold = _getThreshold(genre, entitlementSheet);
  if (threshold === 0) {
    return { allowed: true, genre, mode: 'free' };
  }
  
  // 检查企业包是否启用该体裁
  const packConfig = corpConfig?.packs?.[genre];
  if (packConfig && packConfig.enabled === false) {
    _logFallback(bookRoot, genre, 'corp_pack_disabled', threshold, 0);
    return { allowed: false, genre: 'general', mode: 'fallback' };
  }
  
  // 异步获取余额（优先后端 API）
  const balance = await _getBalance(corpConfig, genre);
  
  if (balance >= threshold) {
    return { allowed: true, genre, mode: 'paid' };
  }
  
  // 余额不足
  const upgradeHint = getUpgradeHint(entitlementSheet);
  _logFallback(bookRoot, genre, 'insufficient_credits', threshold, balance, upgradeHint);
  
  if (upgradeHint.hint) {
    process.stderr.write(`${C.cyan}[乐包] ${upgradeHint.hint}${C.reset}\n`);
  }
  process.stderr.write(
    `${C.gray}[entitlement] ${genre}(需${threshold}个乐包) 余额${balance}，降级→general${C.reset}\n`
  );
  
  return { allowed: false, genre: 'general', mode: 'fallback', upgradeHint };
}

/**
 * 异步获取余额
 */
async function _getBalance(corpConfig, genre) {
  // 优先后端 API（credits-ledger.mjs 已实现）
  const { getBalance } = await import('./credits-ledger.mjs');
  return await getBalance();
}
```

---

### 3.4 配置文件格式

**文件路径**：`scene-packs/backend-config.json`

```json
{
  "apiBaseUrl": "https://api.example.com/fbs/skill-api",
  "apiKey": "fbs_abc123...",
  "timeoutMs": 3000
}
```

**优先级**：
1. 环境变量 `FBS_API_KEY` / `FBS_API_BASE_URL`
2. 配置文件 `backend-config.json`

---

## 4. 数据模型

### 4.1 无新增表

本次变更不需要新增数据库表，复用现有：

| 表 | 用途 |
|------|------|
| `fbs_api_key` | API Key 认证（#11 已完成） |
| `sys_user.points` | 积分余额 |
| `fbs_skill_usage_record` | 使用记录（#5 已完成） |

---

## 5. 安全考虑

### 5.1 API Key 安全

- 用户在 WorkBuddy 配置 API Key，不硬编码在代码中
- API Key 明文传输（HTTPS 保护）
- 后端限流（`rate_limit_per_min`）

### 5.2 Fallback 安全

- 本地账本作为离线备份，不覆盖后端数据
- 后端恢复后，下次调用会同步最新余额
- 不处理历史数据迁移（后续迭代）

---

## 6. 测试策略

### 6.1 后端单元测试

- `FbsSkillApiControllerTest`：新增 `/user/balance` 接口测试
- Mock `IPointsService.getUserPoints()`

### 6.2 Skill 端集成测试

- Mock 后端 API 响应
- 测试 Fallback 逻辑
- 测试配置读取优先级

---

## 7. 部署说明

### 7.1 后端部署

- 无需额外部署步骤
- 新增接口随服务启动自动可用

### 7.2 Skill 端配置

用户需要在 WorkBuddy 配置：
```json
{
  "FBS_API_KEY": "fbs_xxx",
  "FBS_API_BASE_URL": "https://api.example.com/fbs/skill-api"
}
```

---

## 8. 监控与日志

### 8.1 后端日志

- API Key 认证失败：`WARN`
- 积分查询成功：`INFO`
- 积分扣减成功：`INFO`

### 8.2 Skill 端日志

- 后端 API 调用失败：`stderr`（黄色警告）
- Fallback 到本地账本：`stderr`（黄色警告）
- 积分扣减成功：`stderr`（彩色）

---

## 9. 后续迭代

### 9.1 离线模式

- 本地账本 + 同步策略
- 冲突解决（以后端为准）

### 9.2 积分变更通知

- WebSocket 推送
- Webhook 回调

### 9.3 多用户切换

- 支持多个 API Key
- 用户切换 UI
