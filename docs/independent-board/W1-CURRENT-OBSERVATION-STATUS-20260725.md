# W1 当前观测状态（2026-07-25）

本文件覆盖运行手册中历史准备阶段的完成定义，当前事实以
`reports/independent-board/w1a-current-observation-20260725T2328Z.json`、
`reports/independent-board/w1a-cross-service-signal-audit-20260725T2328Z.json`、
`reports/independent-board/api2-live-signal-capture-20260725T2328Z.json` 和
`scripts/verify-w1a-current-observation.ps1` 为准。

- API2 W1 publisher 已部署并健康运行；23:27Z 决策板 15m/1h/24h 均 `NATURAL=0`，active-release critical 1279 文件字节已完成独立回读。
- U3W 账本在 22:50Z 仍为 1 个 `PROBE` journey / 3 个 `PROBE` events，`NATURAL=0`；候选并发修复已在 `be231f22`，线上 JAR 尚未包含该候选。
- API2 交互式只读采集器记录了一条 12:43Z–12:45Z、内部同一 binding 的 `whoami → scene_pack → first_value` 三阶段声明；它是 `diagnostic` 且 `isSynthetic=true`，`productId` 来自 `tool_argument_untrusted`，版本为未受信的 `26.7.2`，并缺少 `serviceProductId/packageName/expertEntryId`。该投影只能作为采集器声明，不构成官方 `26.7.21` 自然信号的独立证明。
- 采集器声明中的 API2 binding 摘要与 U3W 已固化历史探针摘要字面不同；由于前者是 `collector_claim_record_only`，该比较也不构成跨服务 binding 差异证明。publisher delivered=0，U3W 未见成功 POST/receipt，跨服务闭环未成立。
- API2 health 与 decision-board 原始 HTTP 响应已无损压缩固化并记录原始 SHA-256；runtime-state 仅保存全文件 SHA-256 与脱敏投影，不保存生产状态原文。该投影缺少可复验采集器摘要、目标侧签名回执或 inclusion proof，只能作为 `collector_claim_record_only`，不能作为由该 SHA 独立推导出的线上事实。
- 未观察到真实官方入口的三事件成功 POST/追加/receipt；admin 未认证读回返回 401，真实自然流量六维读回尚未完成。
- `authoritative_product_credit=0`，public route 关闭，官方专家包保持冻结。
- 旧 `verify-independent-board-control-plane.ps1` 的默认关闭准备断言属于历史准备面；当前观测门禁必须使用 `verify-w1a-current-observation.ps1`。
- 直接运行旧总体验证器仍会在历史 W1/W3 pin 上 fail-closed；本轮不把该历史门禁结果冒充当前线上失败，后续可单独清理其硬编码 pin。

下一步只做一件事：取得宿主签名的精确 listed receipt 与 trusted forwarding authority，在同一 `serverBindingId` 上完成一次真实官方入口闭环。
