# 独董会第一轮服务侧切流状态（2026-07-26）

## 结论

API2 已于 2026-07-26 12:48:46（CST）完成第二次原子切流，当前线上 release 为
`20260726-124824`，源码提交为
`c02e9677f912a92262e8c02555dc636e51dab283`。本次切流完成了两项服务侧修复：

1. 独董会专家包版本 `26.7.21` 与福帮手连接器包版本 `26.7.2` 分离；
2. 发布守卫改用轻量 `/scene-packs`，不再被重型 `/baseline` 的既有性能债误判并触发回滚。

2026-07-26 13:08（CST），正式公网 MCP
`https://api2.u3w.com/fbs-mcp/mcp` 已完成一条隔离的 `synthetic_probe` 同绑定三跳链：
`skill_whoami → fbs_scene_pack_query → skill_consume`。三段事件经 API2 HMAC
发布器写入 U3W 追加账本，均为 `SYNTHETIC`，产品积分为 0，publisher pending/dead/blocked
均为 0。因此只证明“受控合成调用 → API2 同绑定运行时 → 签名发布器 → U3W 账本”的
服务侧传输能力成立。

这不等于真实官方入口闭环完成。用户在 WorkBuddy 已上架独董会中的操作于
2026-07-26 12:59（CST）触达了正式福帮手连接器公网入口，出现 initialize、ping、
prompts/list，但没有产生 `tools/call`，也没有携带独董会 13 项精确身份。因此当前剩余主线
是福帮手连接器首跳载体和续调闭环，不得归入或修改宿主。

本轮没有修改已上架官方 experts 包，没有修改宿主，没有把任何流量提升为 NATURAL，
也没有产生产品积分或 case。

## 已完成

- 官方基线包保持不变：
  `fbsir-eight-seat-board-26.7.21-official-experts-baseline.zip`，
  SHA-256 为
  `57443E8FBCBCD2620736BCE35EDCCC8001E578918E13EE6521F618D983348510`。
- API2 本地、GitHub 分支、生产 release 均绑定提交 `c02e967`，工作树清洁。
- 原子发布目标：
  `/opt/fbss/phase1/releases/20260726-124824`；没有触发回滚。
- 发布工件回读：
  - package manifest SHA-256：
    `CF0083E368A35DF7B3657D3DB9106817B38F98ACD92922E2A053CCDC2F1C20BD`
  - critical deploy snapshot SHA-256：
    `56ADE7AC58ED3BE12FC2F26C79B39AFC988C994D6F039FF0227FBDC1D4B63A89`
- 公网 MCP initialize 成功，tools/list 返回 11 个工具；三段核心工具均显式暴露
  `marketplace`、`packageVersion`、`connectorPackageVersion`、`hostType`、
  `requestSource`、`sourceRequestSource` 等闭环字段。
- 定向测试通过：49 项 listed-product trust 断言、33/33 Node 测试、版本分离 smoke、
  独董会双进程三事件 E2E、轻量发布守卫正向/缺字段/超时 fail-closed 测试。
- 旧全量 release candidate gate 在严格基线 `016980e` 与候选上均为 101 pass / 8 fail，
  失败集合完全一致，属于继承的跨产品/环境缺口，不是本次修复回归。
- 受控合成投递与数据库只读回执：
  `reports/independent-board/w1d-controlled-synthetic-delivery-receipt-20260726T054427Z.json`，
  SHA-256 为
  `96ba9afeb24ab801e289a92ee0a5929ff90ad94200c19a2022f97371b1465ba4`。

## 生产受控合成传输证据

### API2

- release：`20260726-124824`
- source：`c02e9677f912a92262e8c02555dc636e51dab283`
- 受控完整链 server binding ID SHA-256 前缀：`23edd77a4421685d38c3`
- 受控合成调用显式提供并匹配的规范身份元组：
  - `productId/serviceProductId/packageName=fbsir-eight-seat-board`
  - `expertEntryId=board-convener`
  - `packageVersion=26.7.21`
  - `marketplace=experts`
  - `hostType=WORKBUDDY`
  - `channelTrack=official_experts`
  - `entrySurface=listed_runtime_state`
  - `entryPromptCode/scenePackId=decision_start_card`
  - `packCode=fbsir-independent-review-board-v2`
  - `assetType=ai-expert-team`
- runtime 三段 spine 顺序：
  `whoami_emitted → scene_pack_resolved → first_value_completed`
- 三段均为 `originAuthority=synthetic_probe`、
  `officialWorkBuddyRouteObserved=false`、`exactOfficialIdentityTupleMatched=true`、
  `recordOnly=true`、
  `naturalPromotionAllowed=false`、`canPromoteProductCredit=false`。
- publisher 当前：
  `ready/initialized/workerRunning/strictOutboxDurabilityConfirmed=true`，
  delivered=4、pending=0、inflight=0、dead=0、blocked=0，无失败。
- delivered=4 包含：
  - 首次安全闸因 `hostType` 大小写规范化差异主动停止后形成的 1 条隔离 ENTRY；
  - 随后新绑定完成的 3 条完整链事件。
- case 文件计数在完整链前后均为 3425，没有新增。

### U3W

完整链 journey ID SHA-256 前缀为 `62f56df6a75baa2fba20`，追加账本读回：

| watermark | traffic | event | sequence | intent | credit |
|---:|---|---|---:|---|---:|
| 5 | SYNTHETIC | ENTRY_OBSERVED | 1 | UNKNOWN | 0 |
| 6 | SYNTHETIC | INTENT_CLASSIFIED | 2 | OPERATING_DIAGNOSIS | 0 |
| 7 | SYNTHETIC | FIRST_VALUE_COMPLETED | 3 | OPERATING_DIAGNOSIS | 0 |

切流窗口内总计 2 个隔离 journey、4 条 SYNTHETIC 事件；NATURAL、PROBE、
DIAGNOSTIC、UNKNOWN 均为 0，`authoritative_product_credit` 合计 0。三个完整链事件
均为首次投递成功，`rawContentStored=false`。

U3W 数据库已完成权威只读回读。admin HTTP 正确入口为
`https://admin.u3w.com/prod-api/business/independent-board/attribution/summary`；
未认证请求返回 401 且带 no-store。当前没有已认证浏览器会话或只读 bearer token，因此
没有绕过权限执行 admin HTTP 聚合读回；这保留为服务侧验证缺口，不影响已证实的签名入账。

## 全局一致性结论

- `fbs_expert_package`：官方 26.7.21 包冻结且未修改；当前无升级需求。
- `fbs_connector`：唯一主线缺口。已观察到连接器握手，但没有首个 `tools/call`，也没有
  将独董会 13 项精确身份送入 API2。
- `fbs_service_side`：公网 MCP、受控合成同绑定运行时、签名 outbox、U3W 追加账本
  已由独立回执验证；
  admin 已认证聚合读回和重型运维看板性能治理仍是服务侧缺口。
- WorkBuddy/WorkBuddyAI 宿主仅作为外部事实和验收面，不是升级 owner，不建立宿主 backlog。

## 唯一下一步

只在福帮手连接器中实现或配置独董会首跳参数载体：官方 13 项身份随首次
`skill_whoami` 发送，随后原样透传服务端 `actionEnvelope.toolArguments` 完成 scene 和
consume。同一真实官方入口会话到达后，只验证 3 条 `UNKNOWN` record-only 事件、
同一 binding、U3W/admin 读回和积分 0；不再扩展相邻功能。
