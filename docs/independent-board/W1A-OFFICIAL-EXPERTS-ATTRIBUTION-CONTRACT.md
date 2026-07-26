# W1A 当前官方 Experts 服务归因与意图合同

状态：U3W 本地纵向切片已实现并通过双 MySQL；API2 发布与真实自然流量尚未证明。

## 1. 唯一产品身份

当前只接受 WorkBuddy 已上架身份：

| 字段 | 唯一值 |
|---|---|
| `productId` / `packageId` | `fbsir-eight-seat-board` |
| `agentName` | `board-convener` |
| `marketplace` | `experts` |
| `listedSurface` | `listed_runtime_state` |
| `listedManifestVersion` | `26.7.21` |
| `embeddedContractVersion` | `26.7.20` |

`embeddedContractVersion=26.7.20` 只是当前上架包内的合同元数据，不是旧上架版本兼容分支。任何 `listedManifestVersion != 26.7.21` 的事件都必须拒绝。官方 experts 内容树保持只读。

## 2. 最小首值链

基础链严格为：

1. `ENTRY_OBSERVED`，序号 1；
2. `INTENT_CLASSIFIED`，序号 2；
3. `FIRST_VALUE_COMPLETED`，序号 3。

API2 分别从已持久化的 `whoami_emitted`、`scene_pack_resolved`、
`first_value_completed` 服务事实产生上述三段；入口流失只写第 1 段，意图后
流失只写前 2 段，禁止在首值完成后回填前置事件。`whoami`、`scene_pack`
和 `consume` 是观测映射，不得反向成为独董会产品首值的前置条件。

每个事件必须包含上一事件摘要；第一事件的上一摘要为空。`eventId`、`receiptId`、`nonceHash`、`sameBindingKey + sequenceNo` 分别唯一。完全相同重放返回同一结果，任何同标识异载荷重放都冲突拒绝。

## 3. 同绑定与签名

API2 使用独立事件签名密钥签署短 TTL HMAC-SHA256 信封。U3W 不信任上游给出的 `sameBindingKey`，必须用另一把 U3W 专用密钥重算：

```text
HMAC(
  K_u3w_epoch,
  contractId | tenantSubjectDigest | serverBindingId |
  journeyId | productId | listedManifestVersion
)
```

API2 必须保留可信回执绑定的服务器 `serverBindingId`，不得二次哈希或接受
匿名标识提升绑定权威；提交的 `sameBindingKey` 必须为空。签名密钥和同绑定
密钥不得复用。

短期运输信封 TTL 上限 120 秒。业务 canonical 与 `eventDigest` 排除
`issuedAt`、`expiresAt`、`nonce`、`keyId`，因此 outbox 可在保留窗内对同一
业务事件重签；`occurredAt` 由独立 retention 校验。无密钥、未知 `kid`、
过期运输信封、超出保留窗、乱序、错版本、上游绑定键、摘要冲突均零写入失败。

## 4. 有限意图和六维归因

允许的意图族：

- `OPERATING_DIAGNOSIS`
- `STRATEGY_TRANSITION`
- `CAPITAL_TRANSACTION`
- `GROWTH_CHANNEL`
- `ORGANIZATION_SUCCESSION`
- `CROSS_BORDER_EXPANSION`
- `DIGITAL_AI_TRANSFORMATION`
- `OTHER`
- `UNKNOWN`

admin 按产品、上架版本、渠道、终端、意图、评审模式六维聚合，并额外按流量类别分拆。

流量优先级固定为：

```text
SYNTHETIC > PROBE > DIAGNOSTIC > UNKNOWN > NATURAL
```

只要存在冲突、空缺或客户端自报，便不得进入 `NATURAL`。自然、探针、诊断、合成和未知分母不得合并。

## 5. 存储与隐私

- `fbs_board_attr_journey_v1` 是唯一可变序列头，只锁这一行。
- `fbs_board_attr_event_v1` 是纯追加账本，数据库触发器拒绝 UPDATE 和 DELETE。
- 不存原始 prompt、对话正文、邮箱、手机号或企业内容。
- `traceparent` 只允许 W3C 格式且不得携带 PII。
- `authoritative_product_credit` 在 W1 强制为 0。

## 6. 接口

内部写入：

```http
POST /internal/independent-board/attribution/events
```

- 新事件：`202`
- 精确重放：`200`
- 信封或身份无效：`400`
- 重放冲突、乱序或绑定冲突：`409`
- 写入开关关闭：`404`

admin 读回：

```http
GET /business/independent-board/attribution/summary
    ?windowStart=<UTC instant>
    &windowEnd=<UTC instant>
    &mode=<ALL|NATURAL|PROBE|DIAGNOSTIC|SYNTHETIC|UNKNOWN>
```

要求 `admin` 角色和 `board:attribution:query` 权限；窗口必须大于 0 且不超过 24 小时；返回最多 500 个聚合组合及事件高水位。所有成功与失败响应均为 `Cache-Control: no-store`。

单事件同绑定回执读回：

```http
GET /business/independent-board/attribution/receipt
    ?eventId=<64-char lowercase hex event id>
```

同样要求 `admin` 角色和 `board:attribution:query` 权限，仅接受 64 位小写十六进制事件 ID。响应包含事件类型、序号、官方产品/版本、有限意图、流量类、信用标记和事件水位，以及可比较的 `sameBindingFingerprint`；绝不返回 `serverBindingId`、`journeyId`、租户主体、原始 `sameBindingKey`、签名、nonce 或 trace。未找到返回 `404`，无效 ID 返回 `400`，所有成功与失败响应均为 `Cache-Control: no-store`。

## 7. 开关与故障语义

四个开关默认关闭：

- `observation-writer-enabled`
- `intent-classifier-enabled`
- `observation-admin-read-enabled`
- `product-credit-enabled`

观测链故障不得阻断 WorkBuddy 用户首值，由 API2 outbox 异步重试；产品积分归因必须失败关闭。

## 8. 发布门禁

发布前必须同时满足：

1. API2 真源固定为
   `fubangshou/FBSAI@a0834ea5d4c1c3f95be9d25d26913c2d973e09d0`
   派生的清洁候选，并完成提交、推送和服务侧逐文件字节对齐；
2. Publisher、outbox、健康状态和回滚脚本包含在 strict-HEAD 闭包；
3. `public_init_043` 在 MySQL 8.0.30 与 8.4.8 通过；
4. U3W 内部入口和 admin 读回保持默认关闭；
5. 一次真实官方 experts 会话产生同 binding 三事件；
6. admin 读回能定位该会话，且 probe 与 natural 分母分离；
7. 官方 experts 内容树字节未变化。

本地单测、合成事件、健康接口或旧 24 小时报告都不能替代第 5、6 项。
