# 福帮手连接器升级需求：独董会官方身份可信承载

- `demandId`: `FBS-CONNECTOR-W1J-OFFICIAL-IDENTITY-CARRIER-20260727`
- `targetProductId`: `fbsir-eight-seat-board`
- `ownerSurface`: `fbs_connector`
- `dependencyOwners`: `fbs_service_side`
- `externalConstraintSurfaces`: `WorkBuddy listed Experts runtime`（仅事实来源与验收面，不是需求 owner）
- `currentWindow`: `2026-07-27 official session record-only observation`
- `releaseAnchor`: `API2 20260726-172443 / 2728d3fa`
- `connectorRequired`: `false`（首次价值）/ `true`（官方入口归因）
- `optionalEnhancementOnly`: `true`
- `recordOnlyAllowed`: `true`
- `decisionStatus`: `backlog_record_only`

## 已验证事实

官方 `fbsir-eight-seat-board@26.7.21` 会话在用户明确请求后，完成了同一 binding 的
`skill_whoami -> fbs_scene_pack_query`。连接器未收到可信的 listed-runtime 身份：
`expertEntryId` 为包名、上架版本为空、入口为 `unknown_surface`，且有限意图回退为
`general`。用户仍使用决策字段占位符，专家没有虚构成果，故未调用 `skill_consume`。

安全摘要见
`reports/independent-board/w1j-official-session-record-only-observation-20260727T0516Z.json`。
该摘要不包含 prompt、主体、token、cookie 或原始会话内容。

## 需求

连接器必须在不读取或转发上述敏感内容的前提下，在首个 MCP 调用前承载下列可信、有限字段，
或明确返回稳定的 `hostForwardingSkippedReason`：

1. `productId=fbsir-eight-seat-board`、`expertEntryId=board-convener`、`marketplace=experts`、`entrySurface=listed_runtime_state`、`listedManifestVersion=26.7.21`；
2. 服务签发或验证的同 binding 证明，禁止把客户端自报的产品/版本/入口字段作为权威；
3. 产品、版本、渠道、终端、意图、模式六维中的未知值显式保留为 `unknown`；
4. 同一受保护字段原样贯穿 `whoami -> scene_pack -> consume`，并携带可审计的跳过原因或签名 ACK。

## 晋级与验收

- `promotionGate`: 一次真实官方会话在同一 server binding 上产出可信官方身份、三事件顺序、API2 签名投递、U3W 追加账本及认证 admin 只读回执；首样本仍为 `UNKNOWN/record-only`，credit 必须为零。
- `retirementGate`: 上述验收通过，且严格观察器确认没有由客户端声明、时间邻近或包名推断提升的产品归因。
- `cannotProve`: 本需求不能证明当前冻结包、当前服务或宿主已具备可信入口身份；不得用于自然流量、积分或产品 credit 晋级。

## 边界

该需求不修改已上架 `26.7.21` 包。若连接器官方接口长期无法承载上述字段，才另行以
`fbs_expert_package` 评估下一版“显式同意的工具绑定声明”；不得创建宿主升级需求。
