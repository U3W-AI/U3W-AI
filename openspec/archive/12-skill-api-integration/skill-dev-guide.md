# Skill 端开发指南（新手版）

> **目标读者**：没有 Skill 端开发经验的开发者
> **目标**：手把手教你完成 Skill 端 API 对接改造
> **预计时间**：2-3 小时

---

## 📂 目录结构

**Skill 端代码位置**：

> ⚠️ **实施时确认路径**：以下路径为示例，实施时请确认 Skill 仓库（fbs-bookwriter）的实际位置。

```
fbs-bookwriter/scripts/wecom/lib/
```

**需要操作的文件**：
```
lib/
├── backend-api.mjs       # 【新增】后端 API 封装
├── credits-ledger.mjs    # 【改造】乐包账本
├── entitlement.mjs       # 【改造】权益校验
└── utils.mjs             # 【不动】工具函数
```

---

## 🔧 第一步：新增 backend-api.mjs

### 1.1 创建文件

在 `lib/` 目录下新建文件 `backend-api.mjs`。

### 1.2 完整代码

**复制以下代码到文件中**：

```javascript
#!/usr/bin/env node
/**
 * backend-api.mjs
 * 后端 API 封装
 *
 * 职责：
 *   - 封装后端 REST API 调用
 *   - 处理 API Key 认证
 *   - 超时控制（默认 3s）
 *   - 错误处理
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

/**
 * 读取配置（优先环境变量，次读配置文件）
 */
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
 * @returns {boolean}
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
 * @returns {Promise<{success: boolean, usageRecordId?: string, remainPoints?: number, error?: string}>}
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

### 1.3 代码说明

| 函数 | 作用 | 参数 | 返回值 |
|------|------|------|--------|
| `loadConfig()` | 读取配置（环境变量优先） | 无 | 配置对象 |
| `isBackendAvailable()` | 检查后端是否可用 | 无 | boolean |
| `getUserBalance()` | 获取用户积分余额（调用 /user/info） | 无 | `{success, userId, pointsBalance, error}` |
| `consumeCredits()` | 扣减用户积分 | packCode, skillCode, usageRecordId, pointsAmount | `{success, usageRecordId, remainPoints, error}` |

**⚠️ 重要**：
- 认证 Header 是 `X-FBS-API-Key`（不是 `X-API-Key`）
- `getUserBalance()` 调用的是 `/user/info`（不传 userId），不是新增的 `/user/balance`

---

## 🔧 第二步：改造 credits-ledger.mjs

### 2.1 打开文件

用编辑器打开 `lib/credits-ledger.mjs`。

### 2.2 添加 import

在文件顶部（约第 1-10 行），找到现有的 import 语句，在最后添加：

```javascript
// 现有 import（不要动）
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { resolveWecomDataPaths, C } from './utils.mjs';

// 【新增】导入后端 API
import { isBackendAvailable, getUserBalance, consumeCredits } from './backend-api.mjs';
```

### 2.3 改造 getBalance() 函数

找到 `getBalance()` 函数（约第 50-60 行），**替换整个函数**：

```javascript
/**
 * 读取当前乐包余额
 * 【改造】优先后端 API，失败时 fallback 到本地账本
 */
export function getBalance() {
  return _getBalanceAsync();
}

/**
 * 异步获取余额（内部函数）
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

/**
 * 更新本地缓存（可选）
 */
function _updateLocalCache(balance) {
  try {
    const ledger = _readLedgerRaw();
    ledger.balance = balance;
    _writeLedger(ledger);
  } catch {
    // 忽略缓存更新失败
  }
}
```

### 2.4 改造 deductCredits() 函数

找到 `deductCredits()` 函数（约第 80-100 行），**替换整个函数**：

```javascript
/**
 * 扣减乐包（用于兑换/解锁场景包）
 * 【改造】优先后端 API，失败时 fallback 到本地账本
 */
export async function deductCredits(source, amount, note = '') {
  const delta = typeof amount === 'number' ? Math.abs(amount) : 0;
  if (delta <= 0) return getBalance();
  
  // 1. 优先后端 API
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
 * 本地扣减（Fallback，保持原有逻辑）
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

## 🔧 第三步：改造 entitlement.mjs

### 3.1 打开文件

用编辑器打开 `lib/entitlement.mjs`。

### 3.2 改造 checkEntitlement() 函数

找到 `checkEntitlement()` 函数（约第 30-80 行），**替换整个函数**：

```javascript
/**
 * 检查用户是否有权使用指定体裁
 * 【改造】改成 async，支持异步获取余额
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
  
  // 【改造】异步获取余额（优先后端 API）
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
 * 【改造】异步获取余额
 */
async function _getBalance(corpConfig, genre) {
  // 优先后端 API（credits-ledger.mjs 已实现）
  const { getBalance } = await import('./credits-ledger.mjs');
  return await getBalance();
}
```

### 3.3 更新所有调用方

找到所有调用 `checkEntitlement()` 的地方，添加 `await`：

```javascript
// 旧代码（同步调用）
const result = checkEntitlement(genre, corpConfig, entitlementSheet, bookRoot);

// 新代码（异步调用）
const result = await checkEntitlement(genre, corpConfig, entitlementSheet, bookRoot);
```

**常见位置**：
- `scripts/wecom/xxx.mjs`（具体文件需要搜索）
- 在 `async function` 内部调用

---

## 🔧 第四步：创建配置文件

### 4.1 创建配置目录

在 `scene-packs/` 目录下创建配置文件：

**路径**：`fbs-bookwriter/scene-packs/backend-config.json`（相对于 Skill 仓库根目录）

### 4.2 配置文件内容

```json
{
  "apiBaseUrl": "http://localhost:8080/fbs/skill-api",
  "apiKey": "fbs_你的API_Key",
  "timeoutMs": 3000
}
```

**字段说明**：

| 字段 | 说明 | 示例 |
|------|------|------|
| `apiBaseUrl` | 后端 API 地址 | `http://localhost:8080/fbs/skill-api` |
| `apiKey` | 用户在后台创建的 API Key | `fbs_abc123...` |
| `timeoutMs` | 超时时间（毫秒） | `3000`（3秒） |

### 4.3 获取 API Key

1. 登录后台管理系统
2. 进入"用户中心" → "API Key 管理"
3. 点击"创建 API Key"
4. 复制生成的 Key（只显示一次）
5. 粘贴到配置文件

---

## 🧪 第五步：测试验证

### 5.1 测试后端 API（可选）

用 Postman 或 curl 测试：

```bash
# 查询余额（不传 userId，让后端从 API Key 反查）
curl -X POST http://localhost:8080/fbs/skill-api/user/info \
  -H "Content-Type: application/json" \
  -H "X-FBS-API-Key: fbs_你的Key" \
  -d '{}'

# 预期响应
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": 1,
    "pointsBalance": 100,
    "activatedPacks": [...]
  }
}
```

### 5.2 测试 Skill 端

在 Skill 端运行测试：

```bash
# 在 Skill 仓库根目录执行
cd fbs-bookwriter

# 运行 Skill
node scripts/wecom/some-script.mjs
```

**预期输出**：
```
[乐包] 后端 API 调用成功，余额：100 个乐包
```

**如果后端不可用**：
```
[乐包] 后端 API 失败：请求超时，fallback 到本地账本
[乐包] -10 个乐包 (本地) → 90 个乐包
```

---

## 🐛 常见问题

### Q1：import 报错 "Cannot find module"

**原因**：Node.js 版本过低（需要 14.6+）

**解决**：升级 Node.js 或改用 CommonJS 语法

### Q2：fetch is not defined

**原因**：Node.js 版本过低（需要 18+）

**解决**：
```bash
# 方案 1：升级 Node.js 到 18+

# 方案 2：使用 node-fetch
npm install node-fetch
import fetch from 'node-fetch';
```

### Q3：配置文件找不到

**检查路径**：
```bash
# 正确路径
G:\Interview\wukongshigang\Weihu\fbs\v2.1.2\fbs-bookwriter-v212-workbuddy\fbs-bookwriter\scene-packs\backend-config.json

# 错误路径（不要放在这里）
G:\Interview\wukongshigang\Weihu\fbs\v2.1.2\fbs-bookwriter-v212-workbuddy\fbs-bookwriter\scripts\wecom\lib\backend-config.json
```

### Q4：后端返回 401

**原因**：API Key 无效或未绑定用户

**解决**：
1. 检查 API Key 是否正确
2. 确认 API Key 已绑定 user_id（用户自助创建的 Key 才有）

### Q5：后端返回 403

**原因**：API Key 未绑定用户（运营端生成的 Key）

**解决**：在用户侧重新创建 API Key

---

## 📝 改动清单（核对）

- [ ] 新增 `lib/backend-api.mjs`（完整复制代码）
- [ ] 改造 `lib/credits-ledger.mjs`（添加 import + 改造 2 个函数）
- [ ] 改造 `lib/entitlement.mjs`（改造 checkEntitlement 函数）
- [ ] 创建 `scene-packs/backend-config.json`（配置 API Key）
- [ ] 测试验证

---

## 🎯 总结

**改动的核心思路**：

1. **新增 backend-api.mjs**：封装后端 API 调用
2. **改造 credits-ledger.mjs**：优先调用后端，失败时 fallback
3. **改造 entitlement.mjs**：支持异步获取余额
4. **配置文件**：用户配置 API Key

**Fallback 策略**：
- 后端可用 → 调用后端 API
- 后端不可用 → 使用本地账本（不影响使用）

---

如有问题，随时询问！
