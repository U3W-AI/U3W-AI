# 独董会 API2 24 小时流量归因（只读）

窗口：`2026-07-20T18:19:54Z` 至 `2026-07-21T18:25:34Z`，左闭右开。

- 结构化日志 2,657,326 行，解析错误 0；`fbsir-eight-seat-board`、`26.7.20` 和独董会近义身份均为 0。
- 797 条 `boardSecretaryEntryIntent` 是旧董事会秘书信号，不能归因成独董会。
- business ledger 460 条，全部 `/mcp`，版本均为 1.2.9；独董会精确命中 0。
- 旧董事会秘书 23 条全部 `hostReceiptVerified=false`、`trafficAuthority=unknown`、积分资格关闭。
- active anchor 明确 P1-004 已 active 且 sourceGitHead 为 `c01891a0`；但 `data/current-service-release.json` 仍记录旧 release，P1-004 manifest 的语义也未与 active anchor 完全对齐，存在 release metadata drift。

结论：当前证明的是“没有可信独董会归因信号”，不是“真实需求为零”；本窗口维持 NO-GO，不发布、不切流、不产生积分。
