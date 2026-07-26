# 福帮手连接器与服务侧升级需求刷新 — 2026-07-26

- `ownerSurface`: `fbs_connector`
- `dependencyOwners`: `fbs_service_side`
- `externalConstraintSurfaces`: WorkBuddy listed runtime、官方 experts 会话上下文
- 官方基线：`fbsir-eight-seat-board@26.7.21`
- 工件边界：当前官方专家包保持冻结；本文不授权修改专家包、开启产品积分或切换公开路由

## 当前事实

一次真实官方 WorkBuddy 会话已在会话层观察到精确专家身份
`ex_u2AMqqM8mAFi / fbsir-eight-seat-board / board-convener`，但同期服务链仍未形成
可归因闭环：

- API2 收到同一时窗内的通用 same-binding 调用链，但产品身份仍为
  `unknown_surface`，`productId/packageId/expertEntryId` 缺失，
  `productSignatureStatus=missing_or_partial_product_signature`；
- API2 对精确官方独董会路由的命中数和发布器交付数均为 `0`，保持 fail-closed；
- U3W 未新增独董会事件或旅程，匿名管理读仍为 `401` 且带 `no-store`；
- 会话层观察与服务层事件是两个证据层，不能按时间、IP、UA 或相邻请求做模糊拼接。

因此，当前缺口的主归属是福帮手连接器的身份载体/转发合同，服务侧负责签名验证、
same-binding、不可变账本和只读回读。WorkBuddy 只作为外部运行事实和真机验收面，
不得成为升级需求 owner、backlog 或交付落点。

## 福帮手连接器最小能力

连接器必须通过官方支持的连接器/MCP 接口，在不读取或转发 prompt、对话、企业材料、
cookie、token 或原始主体标识的前提下，承载以下有界字段：

1. 精确官方身份：产品/服务/包 `fbsir-eight-seat-board`、专家 `board-convener`、
   市场 `experts`、上架版本 `26.7.21`、终端 `WorkBuddy` 和官方渠道；
2. 服务端签发或验证的 `serverBindingId`；客户端声明不得成为权威来源；
3. 六维账本所需的产品、版本、渠道、终端、意图和模式，未知值必须显式为 unknown，
   不得猜测；
4. 与同一 binding、challenge/nonce、请求摘要、listing-receipt 摘要、
   route-profile 摘要、签发/过期时间和 key id 绑定的签名回执；
5. 无法承载或转发时返回稳定、可审计的 `hostForwardingSkippedReason`，不得静默降级
   或借助时间/IP/UA 补写产品身份。

如果当前官方连接器接口不能取得上述会话身份，归属仍保持 `fbs_connector`，交付状态
保持 `not_proven`，不得把需求转移给宿主；另行评估下一版福帮手专家包的工具绑定
需求，但不得修改当前冻结的官方包。

## 福帮手服务侧配套

API2/U3W 只接受受管密钥签名、nonce 防重放、严格有效期和同一
`serverBindingId` 的证据；缺字段、跨 binding、版本冲突或未知权威来源均失败关闭。
服务侧必须把入口观察、意图分类、首值完成和后续使用按真实持久化时间追加写入，
并提供六维只读回读，不得首值后反向补写入口或意图。

## 验收闸门

一次真实官方 WorkBuddy 会话必须在同一 `serverBindingId` 上产生可信 listing receipt、
可信 forwarding ACK、API2 发布器交付和 U3W 三事件链，且管理端可读回六维自然流量
证据。在全部证据出现前，状态保持 record-only/unknown，积分归属与公开晋级继续关闭。
