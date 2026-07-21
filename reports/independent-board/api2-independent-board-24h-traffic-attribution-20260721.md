# API2 独董会固定 24 小时流量归因

- 窗口：`[2026-07-20 22:44:00, 2026-07-21 22:44:00)`，`Asia/Shanghai`
- 唯一目标：`fbsir-eight-seat-board@26.7.20`
- 结论：**独董会可归因流量为 0；不能据此断言独董会实际使用量为 0。**
- 冻结边界：未修改已提交审核的 26.7.20 专家包。
- 机器可读证据：[api2-independent-board-24h-traffic-attribution-20260721.json](api2-independent-board-24h-traffic-attribution-20260721.json)

## 对账结果

| 证据层 | 固定窗口规模 | 独董会精确信号 | 归因判断 |
|---|---:|---:|---|
| 标准 evidence ledger | 733 行 | 0 | trusted natural 0；probe 306、synthetic 4、unknown 423 |
| MCP 原始事件 | 1,664,314 行 | 0 | 52 个 `natural` 只是未受信源分类，不能给产品 credit |
| MCP `access.tsv` | 183 行 | 0 | 无 session/target/binding，183 行全部失败关闭 |
| business ledger | 473 原始 / 470 去重 | 0 | 473 行 authority 均 unknown，全部 credit withheld |
| 官方 `/fbs-mcp/mcp` | 至少 26,344,641 请求 | 不可投影 | 桶将产品折叠为 connector，版本/渠道/意图未知且无 canonical binding |

以上结论来自对当前可得应用证据的逐源扫描，不代表已取得不可变的完整 24 小时原始快照。官方入口的选定状态下界之和另有 7 次未分类余项，选定方法下界之和另有 6 次未分类余项；两组分解都不得提升为完整分布。

733 行标准账本的产品分布为 general reference 601、bookwriter 67、super partner 35、董秘助手 24、行业研究员 6。董秘助手和其他产品均为明确 off-target，不得并入独董会；“私董会”只作为混淆信号，本窗口命中 0。

## 转化与六维边界

目标产品的 whoami、scene pack、consume、first value、continued use、同 binding、verified host receipt 和 service closure 全部为 0。因此不能计算目标转化率，也不能把全产品上下文中的 551 个 binding、595 个 whoami 或 144 个 consume 转嫁给独董会。

全量诊断六维可用于观察 API2 的总体流量结构，但不得提升为目标归因：

- 版本：unknown 430，5.2.6 为 148，4.10.4 为 67，5.1.7 为 24，其余 64；
- 渠道：workbuddy_cn 365、fbs-live-monitor 270、bookwriter_skill 67、unknown 22，其余 9；
- 终端：desktop 371、unknown 291、node 71；
- 意图：general 562、long document 67、company strategy 35、board secretary 24，其余 45；
- 模式：whoami 487、consume/value 144、scene pack 101、continued-only 1。

## 主要缺口

1. 活跃 Phase1 没有注册 `fbsir-eight-seat-board` 的签名和 credit gate。
2. 标准账本 733 行中 `businessTrafficGroup` 全缺失；`productId` 缺失 601；版本缺失 430；host/channel/terminal 各缺失 362；entry surface 缺失 542。
3. 584 行只有 receipt “observed”指标，business ledger 中 server-verified receipt 为 0，不能跨层推断。
4. 结构化日志只保留约 3 小时 6 分钟；滚动 endpoint 在审计中由 723→719→718→730 行变化，不是不可变 24 小时快照。
5. 运行 release 路径 `202607152006-p1-004-runtime-state-14e20a6d.staged` 与内嵌 `20260711-112155` 存在身份漂移。

## 已落地的失败关闭归因器

`scripts/independent-board-traffic-attribution.mjs` 只生成 unsigned、report-only 候选，权威产品 credit 恒为 0；候选默认关闭。42 个测试覆盖精确身份/版本、受信 natural authority、严格 JSON receipt、字段级 binding 格式、整 binding 冲突、成功阶段精确白名单、严格时间顺序、渠道/终端/宿主/客户端版本连续、快照/发布/时间对齐、隐私脱敏以及“私董会”隔离。

单一下一步：不改冻结包，先在 API2/连接器/宿主转发面注册精确产品与版本合同，把结构化证据保留提高到至少 26 小时并生成不可变摘要；然后以一次真实官方入口同 binding `whoami → scene_pack → consume` 作为晋级门槛。
