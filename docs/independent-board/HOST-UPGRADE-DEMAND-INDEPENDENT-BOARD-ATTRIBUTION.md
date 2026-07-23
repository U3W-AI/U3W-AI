# 宿主升级需求：独董会官方入口归因闭环

- 状态：宿主升级需求已确认；官方 experts 内容包继续只读
- 当前唯一官方基线：WorkBuddy listed
  `fbsir-eight-seat-board@26.7.21`；包内 `26.7.20` 仅为嵌入合同元数据，
  不是兼容目标，禁止修改或写回
- 触发证据：API2 固定 24 小时审计无法从官方入口总量还原独董会产品、同 binding 与闭环

> 本文中 P1-005、`26.7.20` 上架身份和 Connector 四阶段链描述属于
> 2026-07-22 历史调查记录，已被 W1A/ADR-007 的当前合同取代，不进入支持矩阵。

## 问题

当前宿主、连接器和 API2 各自能观察到部分客户端、入口和工具事件，但权威产品身份、版本、渠道、宿主路线、服务端 binding 与回执验证没有形成一个可连接合同。结果是官方入口量很大，独董会可归因量仍只能报告为 0/unknown；receipt “observed”也不能代替服务端 verified。

## 下一版宿主必须转发的合同

| 字段 | 约束 | 用途 |
|---|---|---|
| `productId` | 精确 `fbsir-eight-seat-board` | 唯一产品身份 |
| `listedManifestVersion` | 精确 `26.7.21` | 当前唯一上架版本 |
| `embeddedContractVersion` | 精确 `26.7.20` | 仅用于当前包内合同元数据 |
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

## 2026-07-23 当前宿主闭环缺口

当前官方包 `contracts/runtime-capabilities.json` 明确
`connectorRequired=false`、`contentTelemetry=false`，且包本身没有 API2/U3W
网络写入能力。这是正确的内容隐私边界，但也意味着不能靠修改已上架专家包补齐
服务归因。

下一版 WorkBuddy 宿主需要在不向专家脚本开放密钥的前提下，由宿主服务层完成：

1. 官方入口首次进入时生成服务器签名的
   `serverVerifiedHostListingReceipt + listedProductTrustContext`，精确覆盖
   `fbsir-eight-seat-board / board-convener / experts / 26.7.21 / WorkBuddy`；
2. 把可信回执绑定到服务器 `serverBindingId`，并保证回执 context 中的 binding
   与运行事件 binding 完全一致；
3. 分别在真实持久化阶段形成 `whoami_emitted`、`scene_pack_resolved`、
   `first_value_completed`，保留各自时间，禁止首值后补写入口和意图；
4. 将上述有限字段送达 API2；不得转发 prompt、对话、企业材料、邮箱、手机号、
   token 或 cookie；
5. 对自然、探针、诊断、合成和未知流量提供服务器权威分类，优先级固定为
   `SYNTHETIC > PROBE > DIAGNOSTIC > UNKNOWN > NATURAL`；
6. 支持回执丢失后的同业务事件重签重放，不把 120 秒运输 TTL 当成旅程时限。

若现有 WorkBuddy 5.3.3.0 已能提供上述宿主事件，只需配置和真机回读，不需要
升级专家包；若不能，则将本节纳入下一版 WorkBuddy 宿主提审需求。

## 2026-07-23 安全增补：宿主日志必须脱敏连接器凭据

只读审计发现，当前 WorkBuddy 主进程日志和 sandbox 命令日志会把
connector-proxy 的 `Authorization: Bearer ...` 作为命令环境内容明文记录。
交付证据不得复制该值；本问题按宿主凭据暴露风险处理，不通过修改官方 experts
包修复。

下一版宿主必须：

1. 在进入主日志、sandbox 日志、命令审计、异常栈和诊断包之前，对
   `Authorization`、`Cookie`、token、API key、client secret 及其常见环境变量
   做结构化 redaction；禁止只依赖字符串截断；
2. 日志只记录凭据类型、来源组件、是否配置和不可逆短摘要，不记录原值；
3. sandbox 执行记录采用环境变量 allowlist，默认不序列化完整进程环境；
4. 增加带 sentinel secret 的自动化负向测试，扫描主日志、sandbox 日志和导出诊断包，
   发现 sentinel 即失败；
5. 对已暴露凭据执行宿主侧轮换和旧日志访问/保留处置，并生成不含秘密的安全回执。

在凭据完成轮换前，不得把含原值的日志作为归因、发布或问题排查附件传播。

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

完成不等于某个接口 HTTP 200。必须同时取得宿主 receipt、不可变固定窗口、同 binding 链、服务闭环和发布身份对齐证据；否则继续保持 `not_proven`。本需求只进入下一版本 WorkBuddy 宿主计划；除非宿主接口规范要求新增声明字段，否则不升级专家内容包。无论如何不改变当前 listed 26.7.21 官方工件。

## 2026-07-22 增补：v2 回执与 U3W 租户绑定

- API2 cleanroom 的权威输入是 `fbss.hostForwardingAck.v2`（HMAC-SHA256、受管 keyring、nonce replay、`requestDigest`/`actionEnvelopeDigest`/`toolArgumentsDigest`、服务端 binding）；仓内 `fbss.hostForwardingAckVerification.v1` 只是 API2 已验证结果的 wrapper，不能把其中的 boolean 当作 U3W 可信回执。
- v2 ack 不携带 U3W `tenantSubjectDigest`。U3W 必须以自己签发并锁定的 challenge/session 期望值注入 verifier，并同时比较 tenant、serverBindingId、challengeId、requestDigest，禁止从 ack 自报租户。
- Java writer 在 verifier/adapter/keyring 未落地前必须 fail-closed；本轮仅补齐 challenge nonce 绑定与严格 `expiresAt > issuedAt` 静态护栏，不能宣称 API2 加密回执闭环。
- 下一版宿主升级需求：提供完整 v2 ack DTO、受管 key resolver（最少 32 bytes）、canonical JSON/HMAC 常量时间校验、nonce replay 存储，以及由唯一工厂生成 `VerifiedApi2Receipt` 后才允许写入证据端口。
- 本轮补充：Java writer 先锁 challenge 并验证独董会前序链，再执行 v2 verifier；verifier 的验签/nonce replay 预留必须保持无副作用，直到 append/seal 事务接受证据，避免乱序或拒绝事件消耗 replay 状态。
