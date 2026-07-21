# 宿主升级需求：独董会官方入口归因闭环

- 状态：Backlog for next submitted package / host release
- 当前冻结包：`fbsir-eight-seat-board@26.7.20`，禁止修改或写回
- 触发证据：API2 固定 24 小时审计无法从官方入口总量还原独董会产品、同 binding 与闭环

## 问题

当前宿主、连接器和 API2 各自能观察到部分客户端、入口和工具事件，但权威产品身份、版本、渠道、宿主路线、服务端 binding 与回执验证没有形成一个可连接合同。结果是官方入口量很大，独董会可归因量仍只能报告为 0/unknown；receipt “observed”也不能代替服务端 verified。

## 下一版宿主必须转发的合同

| 字段 | 约束 | 用途 |
|---|---|---|
| `productId` | 精确 `fbsir-eight-seat-board` | 唯一产品身份 |
| `expertVersion` | 精确 `26.7.20` 或下一提审版本 | 上架版本连续性 |
| `expertEntryId` / `packageId` | 与产品映射唯一且无冲突 | official entry 证明 |
| `entrySurface` / `hostRoute` | 有限枚举 | 区分官方入口、插件目录和探针 |
| `channel` / `terminal` | 有限枚举 | 六维分析与同路线校验 |
| `hostType` / `hostPatchVersion` | 有限宿主 + 语义版本 | 阻断 WorkBuddy/CodeBuddy 或版本跨链 |
| `serverBindingId` | 服务端生成、16–64 位 base64url；不可由客户端自报 | whoami/scene/consume 同主体连接 |
| `trafficClassificationAuthority` | 服务端 allowlist | 自然、探针、诊断、合成分离 |
| `serverVerificationState` / `serverVerificationSource` | 精确合同值 | 防止 observed 被提升为 verified |
| `hostForwardingAckChallenge*` | challenge 与 binding 同库持久化 | receipt 防伪和同 binding join |
| `serviceClosure*` | 明确 closure 成功事件，继承同一宿主路线 | continued use / 闭环 |

宿主不得发送 token、cookie、authorization code、PKCE verifier、原始主体标识、邮箱、手机号或自由文本作为维度。主体只允许服务端 HMAC/opaque reference；渠道、终端、意图、宿主和来源使用有限枚举。

## 连接器与 API2 配套

1. 连接器注册独董会精确产品/版本签名，不把 connector 自身当作最终产品。
2. API2 同时保存 edge request、标准事件和 business event 的 canonical correlation，但各层计数不得相加。
3. 结构化原始证据保留至少 26 小时；固定窗口生成不可变快照、SHA-256、row count、`asOf`、runtime release 和 embedded release。
4. 分离 `observed`、`server_verified`、`product_credit_candidate` 与 `authoritative_product_credit`；客户端声明永不直接晋级。
5. 运行 release 与内嵌 release 不一致时，候选归因失败关闭并告警。

## P1-005 服务侧注册与清洁发布补充

### 2026-07-22 当前窗口与候选边界

- API2 固定 24 小时窗口（2026-07-20 18:19:54Z 至 2026-07-21 18:25:34Z）共 2,657,326 条结构化日志，精确 `fbsir-eight-seat-board@26.7.20` 及独董会别名均为 0；旧独董秘书助手 23 条与独董会严格隔离。
- 当前证据只能说明“没有可信的独董会产品信号”，不能推出实际使用量为 0；旧产品 23 条均 `hostReceiptVerified=false`、`trafficAuthority=unknown`、业务/产品 credit 均 withheld。
- P1-005A 已在本地 cleanroom 构建 report-only 候选：精确产品/版本门禁、错误版本拒绝、独董秘书隔离、伪造注册拒绝均通过；`PENDING_HOST_REGISTRATION`、`candidateEnabled=0`、`publicRouteEnabled=0`、`authoritativeCreditEnabled=0`，独董会自然分母权重为 0。
- 当前仍为 `NO_GO`：active-release 元数据存在漂移，候选基础捕获缺少可证明 Git HEAD，尚未取得签名精确宿主注册回执，也未完成完整 serve 依赖闭包、健康切换与回滚证据。P1-005B 只允许复用现有签名 API2 service receipt/replay verifier，禁止以客户端布尔值升权。

本轮只读复查确认 API2 当前 host-forwarding 结果不是 boolean，而是对象：

- `serverVerifiedHostForwardingAck.schemaVersion = fbss.hostForwardingAckVerification.v1`
- `verified = true`、`cryptographicallyVerified = true`
- `serverVerificationState = verified`
- `serverVerificationSource = server_dispatch_ack_hmac`
- `challengeJoinState = finalized`
- `trafficClassificationAuthority = server_verified_host_forwarding_ack`
- `serverObservedHostForwardingEvidenceTrust = server_dispatch_ack_verified`

服务端 binding 当前默认形态为 `srv_<12-char stableHash>`；校验必须同时检查 `bindingIdentitySource` 为服务端派生来源，不能把 trace/anonymous hash 当作 binding。若 ack 携带 binding，必须与 row 的 `serverBindingId` 完全一致。

独董会还需要一个独立的 API2 签名注册回执，覆盖精确 `productId`、`productVersion`、`expertEntryId`、`packageId`、`entrySurface`、issuer、有效期和 key id；注册成功也必须保持 `candidateEnabled=0`、`publicRouteEnabled=0`、`authoritativeCreditEnabled=0`。当前 `expertEntryId/packageId` 尚未取得权威值，状态保持 `PENDING_HOST_REGISTRATION`。

P1-005 不得从 active release 目录直接热补丁。必须以可证明的 clean Git HEAD 或明确的 hash-anchored reconstruction cleanroom 构建，显式扩大 package target/module-closure 清单，并在新 release 上完成健康探针、产物 hash、symlink 切换和失败回滚证据；当前 active P1-004 overlay 不包含产品签名归因器，不能复用为独董会候选发布源。

## 验收矩阵

- 正向：一次真实官方入口、同 server binding、同 channel/terminal/host/version，严格 `whoami < scene_pack < consume < closure`，所有成功阶段有受信 natural authority 和 server-verified receipt。
- 负向：私董会文本、董秘助手、其他专家 ID、多产品签名数组、版本冲突、跨 binding、跨宿主/渠道/终端/版本、乱序/同毫秒、失败/preview 工具、字符串布尔、低熵或格式错误 binding、缺摘要、release drift 均不得形成候选。
- 权限：未签名离线报告只能输出 report-only candidate，权威产品 credit 恒为 0。
- 隐私：报告不回显 binding、主体标识、自由文本或非 allowlist 维度；source/release 非法值进入 redacted/unverified。
- 保留：完整 26 小时回放无缺片、无解析失败；快照摘要重算一致。

## 完成定义

完成不等于某个接口 HTTP 200。必须同时取得宿主 receipt、不可变固定窗口、同 binding 链、服务闭环和发布身份对齐证据；否则继续保持 `not_proven`。本需求只进入下一版本宿主/专家包计划，不改变 26.7.20 冻结工件。
