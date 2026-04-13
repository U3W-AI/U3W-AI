# FBS-BookWriter Skill — 权益对接说明

## 概述

本 Skill 通过 FBS Skill API 网关与后端权益系统对接，实现：

1. **权益校验**：用户操作前校验是否有对应场景包权益
2. **积分扣减**：操作完成后一次性扣减积分（consume 模式）
3. **使用记录**：两阶段记录模式（start/end，纯日志不扣费）
4. **场景包热更新**：查询最新场景包规则（contentSnapshot）
5. **用户信息查询**：积分余额 + 已激活场景包

## API 认证

所有 Skill API 请求使用 `X-FBS-API-Key` Header 认证（不使用 JWT），Key 由管理员在后端生成并配置到 `_plugin_meta.json`。

```
X-FBS-API-Key: fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

## 配置项

在 `_plugin_meta.json` 中配置：

```json
{
  "fbs": {
    "api_key": "fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "api_base_url": "http://localhost:8080"
  }
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `fbs.api_key` | 是 | API Key（管理员生成后手动配置） |
| `fbs.api_base_url` | 是 | 后端地址（生产环境必须 HTTPS） |

环境变量备选（开发调试用）：

- `FBS_API_KEY`：覆盖 `_plugin_meta.json` 中的 api_key
- `FBS_API_BASE_URL`：覆盖后端地址
- `WB_USER_ID`：当前用户 ID（由宿主环境注入）

## 接口调用

使用 `lib/fbs-rights-client.mjs` 封装：

```javascript
import FbsRightsClient from './lib/fbs-rights-client.mjs';

const client = new FbsRightsClient({
  apiKey: meta.fbs.api_key,
  apiBaseUrl: meta.fbs.api_base_url,
  userId: process.env.WB_USER_ID,
});

// 1. 权益校验
const checkResult = await client.rightsCheck('pack_bookwriter_v2', { authCode: 'ABCD1234' });

// 2. 一次性消费（扣费 + 记录，自闭环）
const consumeResult = await client.usageConsume('pack_bookwriter_v2', 'bookwriter', 'task-uuid-001');

// 3. 两阶段模式（不扣费，纯日志）
const startResult = await client.usageStart('pack_bookwriter_v2', 'bookwriter', 'task-uuid-002');
// ... 执行操作 ...
const endResult = await client.usageEnd('task-uuid-002', 1); // 1=成功, 2=失败

// 4. 场景包规则查询
const packInfo = await client.scenePackQuery('pack_bookwriter_v2');
const rules = JSON.parse(packInfo.contentSnapshot); // 自行解析 JSON

// 5. 用户信息
const userInfo = await client.userInfo();
```

## consume vs start/end 模式

⚠️ **互斥**：同一个 `usageRecordId` 只能走一种模式，不能组合调用。

| 模式 | 适用场景 | 是否扣费 | 流程 |
|------|---------|---------|------|
| `consume` | 需要扣减积分的操作 | ✅ 是 | 一次调用：校验→扣减→记录 |
| `start/end` | 只需记录不需扣费 | ❌ 否 | 两次调用：start 记录→执行→end 更新状态 |

**原因**：`SkillConsumeService.consume` 是自闭环的（自己 INSERT status=0 → 扣减 → UPDATE status=1）。如果先用 start 插入记录，consume 会因 status=0 已存在而拒绝。

## 离线降级

API 不可用时，`fbs-rights-client.mjs` 自动降级：

1. **场景包查询**：返回本地缓存（5 分钟 TTL），缓存过期返回 null
2. **其他接口**：返回 null，Skill 需自行处理降级逻辑

降级链：`API 在线 → 本地缓存 → null`

## 安全边界

- API Key 仅部署在受控 WorkBuddy 宿主环境
- 不面向公网第三方开放
- 生产环境强制 HTTPS
- API Key 由管理员手动配置，不通过公开文档/SDK 分发
