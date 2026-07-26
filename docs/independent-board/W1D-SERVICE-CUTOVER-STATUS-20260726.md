# 独董会第一轮服务侧切流状态（2026-07-26）

## 结论

API2 已于 2026-07-26 10:30:32（CST）完成原子切流，当前线上版本为
`20260726-103012`，源码提交为
`016980eb5a20fb252d5a5315ac54b0ff841cfa0a`。服务端已经具备在完全没有
listing keyring/registry 的现状下，仅对**精确匹配官方独董会完整路由**的真实请求形成
三段 HMAC 签名、record-only 归因事件的能力。

本轮没有修改 WorkBuddy 已上架 experts 包，也没有把流量提升为 NATURAL 或给予产品积分。
截至 2026-07-26 10:39（CST），切流后还没有观察到真实官方独董会精确路由，所以第一阶段
尚差一次真实会话后的生产回执，不应宣称闭环完成。

## 已完成

- 官方基线：
  `fbsir-eight-seat-board-26.7.21-official-experts-baseline.zip`
  SHA-256 仍为
  `57443E8FBCBCD2620736BCE35EDCCC8001E578918E13EE6521F618D983348510`。
- API2 本地、GitHub 分支及线上 release 全部绑定提交 `016980e`。
- 原子发布目标：
  `/opt/fbss/phase1/releases/20260726-103012`；没有触发回滚。
- API2 发布器状态 `ready`，严格 outbox 持久化已确认，pending/dead/blocked 均为 0。
- 精确路由需要同时匹配产品、服务产品、专家入口、包名、26.7.21 版本、experts、
  WorkBuddy、official_experts、listed_runtime_state、decision_start_card、
  `fbsir-independent-review-board-v2` 和 `ai-expert-team`。
- 无 listing keyring/registry 时只允许服务器生成绑定身份；客户端显式输入绑定不能生成
  ENTRY，后续两段还会逐段重验精确路由和同绑定连续性。
- 定向门禁全部通过：49 项 trust smoke、33/33 Node 测试、activation contract、
  双进程三事件 E2E、SP26715 默认运行回归、ACK required-no-write 回归和当前 release
  spine 回归。
- 双进程 E2E 同时证明正常 keyring 路径、record-only 路径，以及彻底清空 JSON/legacy
  keyring 和 registry 变量后的独立服务进程均能产生三段签名事件；全部保持
  `productCreditEligible=false`。

## 生产读回

API2：

- public health 返回 `status=ok`；
- release/source/包哈希/关键快照哈希与发布回执一致；
- runtime 共有 51873 条事件，最新事件时间为 `2026-07-26T02:38:01.997Z`；
- 精确官方独董会路由历史总数 0，切流后 0，record-only 0；
- 近期新增仅为既有 `fbs-live-monitor` 的 general 路由，不应误归因给独董会。

U3W：

- `fbsir-admin.service` 为 active；
- 当前线上仍是
  `w1a-release-13c203a5c42d-20260725T040706Z`
  （提交 `13c203a5...`，jar SHA-256
  `5b3582e2e4f97ab8a09845a1b6940e334cb2452a2c1970a01aeaf40dd1cc5cc1`）；
- 账本仍为 1 个历史 PROBE journey、3 个事件；切流后新增 0；
- NATURAL=0、UNKNOWN=0、`authoritative_product_credit` 合计 0；
- U3W 候选分支 `codex/w1-natural-ingress-fence` 的提交 `ccec982...`
  已增加“拒绝未验证 NATURAL”防线，但尚未部署。本轮 API2 永不发送 NATURAL，
  因而不把完整 U3W 发布扩大为当前阻塞项。

## 非阻塞技术债

生产 U3W 的既有 `WebhookDeliveryReconciliationTask` 每分钟出现一次
`BadSqlGrammarException`。当前未发现它与独董会归因入口、账本追加或 API2 发布器耦合；
它需要单独排查，但不应打断本轮唯一主线。

旧的全量 `api2-release-candidate-gate.mjs` 依赖本工作树之外的历史环境变量和旧留学助手
工件，运行时出现 9 项陈旧/环境型失败。它生成的三个跟踪文档已精确恢复，工作树无残留。
本次切流的发布依据是定向门禁、双进程 E2E、原子发布预检和线上只读读回。

## 唯一下一步

在 WorkBuddy 已上架「独董会」中发送一句真实业务问题。随后只验证：

1. API2 同一服务器绑定产生 ENTRY、INTENT、FIRST_VALUE 三段 record-only 事件；
2. 三段事件均经 HMAC 投递到 U3W；
3. U3W 新增 1 个 UNKNOWN journey / 3 个 UNKNOWN 事件；
4. `authoritative_product_credit` 仍为 0。

完成这四项后，第一轮“官方入口 → API2 → U3W 账本”的服务侧闭环才可判定完成。
