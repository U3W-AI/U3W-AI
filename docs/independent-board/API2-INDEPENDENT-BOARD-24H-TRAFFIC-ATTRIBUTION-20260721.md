# API2 独董会 24 小时流量归因结论

本文件是 2026-07-21 固定窗口审计的工程入口；完整数字、边界和机器可读判定见：

- `reports/independent-board/api2-independent-board-24h-traffic-attribution-20260721.json`
- `reports/independent-board/api2-independent-board-24h-traffic-attribution-20260721.md`

## 当前事实

在北京时间 `[2026-07-20 22:44:00, 2026-07-21 22:44:00)` 内，对当前可得的标准账本、MCP 原始事件、稀疏 access 行和 business ledger 逐源扫描，均未出现 `fbsir-eight-seat-board@26.7.20`。严格判定是“可归因流量为 0”，而不是“实际使用为 0”；结构化原始证据仅保留约 3 小时 6 分钟，且滚动账本不是不可变 24 小时快照，因此不能把扫描范围表述为穷尽全部证据。

官方入口至少有 26,344,641 次请求；已分类状态项之外仍有 7 次余项，已分类方法项之外仍有 6 次余项，这些分解同样只是下界内的选定分类，不能视为完整分布。当前分钟投影把产品统一折叠成 connector，且版本、渠道、意图及 canonical binding 缺失，所以不能从该总量反推独董会用户或转化。原始事件层出现的 52 行 `natural` 也只是未受信源分类，没有目标签名，不能晋级。

## 不变量

- 26.7.20 已提交审核包保持冻结、无写回；
- “私董会”只进入 confusion debt，绝不并入独董会；
- official HTTP 200、客户端声明、receipt observed 和自然/业务/产品 credit 是不同层级；
- 只有精确身份版本、受信 authority、server-verified receipt、字段级有效 binding、无冲突整链、严格顺序和同宿主路线同时成立，才允许形成 report-only 候选；
- unsigned 报告永远不能直接写产品 credit。

## 服务侧改造顺序

1. 连接器和 API2 注册 `productId=fbsir-eight-seat-board`、`expertVersion=26.7.20`，并在宿主转发中携带 entry、channel、terminal、host、client version 和 server binding。
2. 把 `hostForwardingReceiptObserved` 与真正的 server verification 分开；只有挑战回执同 binding join 后才允许 `server_verified`。
3. 结构化日志保留不少于 26 小时，每个固定窗口输出 canonical digest、row count、as-of、runtime release 和 embedded release。
4. 先以一次真实官方入口同 binding `whoami → scene_pack → consume` 验证；再观察 first value、continued use 和 service closure，不跨层补数。
5. 宿主暂时不能提供的字段进入下一提审包升级需求，不回改 26.7.20 包。

## 当前阻断

在上述合同和不可变证据到位前，目标转化率、分版本/渠道/终端/人群转化、自然调用和服务闭环均保持 `not_proven`，不得发布为业务结论。
