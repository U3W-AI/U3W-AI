# ADR-003：独董会 W4b.2d 签名证据与不可变快照边界

## 状态

已接受为本地 default-off 候选；未批准生产启用。

## 决策

独董会目标只能以精确 `fbsir-eight-seat-board@26.7.20` 进入 W4b2d 证据层。归因器不再把客户端自报的 `server_verified`、trace id 或普通摘要视为权威事实；必须消费 API2 生成并验证过的 host-forwarding 对象回执，且要求 `fbss.hostForwardingAckVerification.v1`、HMAC 验证、`verified`、`server_dispatch_ack_hmac`、`finalized` 及服务端派生 binding。

快照固定为 `[start,end)` 24 小时，必须带 watermark/high-watermark、零解析/缺片/非法计数、运行时与宿主发布身份、规范化事件摘要，并证明 `retentionUntil >= windowEnd + 26h`。数据库 037 只建立 append-only 证据表，产品合同、公开路由和权威积分均默认关闭。

本切片任何输出都使用独立 report-only candidate 名称，固定 `canPromote=false`、`productionEligible=false`、`authoritativeProductCredit=0`、`denominatorWeight=0`。真实 API2 注册、官方入口同 binding 链和生产留存证据取得前，不得部署、切流或写冻结包。

## 后果与未决项

- 线上当前 ack 对象合同与旧归因脚本的 boolean 约定不兼容；已通过适配器显式拒绝漂移。
- API2 active release 的 Git/cleanroom provenance 尚未证明，不能直接补丁式修改 `/opt/fbss/phase1/current`。
- 需在下一轮找到可追溯服务源代码或生成 overlay，补充目标产品注册规则和服务侧一次性 challenge/receipt 写入器，再做只读 gate。
