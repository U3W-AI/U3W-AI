# W1 当前观测状态（2026-07-25）

本文件覆盖运行手册中历史准备阶段的完成定义，当前事实以
`reports/independent-board/w1a-current-observation-20260725T1445Z.json` 和
`scripts/verify-w1a-current-observation.ps1` 为准。

- API2 W1 publisher 已部署并健康运行；严格 active-release 文件字节证明仍未完成。
- U3W 账本为 1 个 `PROBE` journey / 3 个 `PROBE` events，`NATURAL=0`。
- 未观察到真实官方入口的三事件成功 POST/追加/receipt；admin 未认证读回返回 401，真实自然流量六维读回尚未完成。
- `authoritative_product_credit=0`，public route 关闭，官方专家包保持冻结。
- 旧 `verify-independent-board-control-plane.ps1` 的默认关闭准备断言属于历史准备面；当前观测门禁必须使用 `verify-w1a-current-observation.ps1`。
- 直接运行旧总体验证器仍会在历史 W1/W3 pin 上 fail-closed；本轮不把该历史门禁结果冒充当前线上失败，后续可单独清理其硬编码 pin。

下一步只做一件事：取得宿主签名的精确 listed receipt 与 trusted forwarding authority，在同一 `serverBindingId` 上完成一次真实官方入口闭环。
