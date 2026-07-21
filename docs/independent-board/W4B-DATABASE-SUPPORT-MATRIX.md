# 独董会 W4b 数据库支持矩阵

状态：`exact_build_allowlist_refresh_security_verified_local`

品牌：福帮手 / FBSir
产品：独董会 `26.7.20`

## 1. 当前结论

W4b 数据库迁移当前只允许两个**精确 MySQL Community Server 构建**：

- `8.0.30`
- `8.4.8`

两者均已在一次性回环实例上完成 `public_init_001` 至 `public_init_036` 首次执行、完成态
重跑、只读 current-read，以及内部事务和 refresh-security 真库测试。该结论只证明本地数据库、
Spring/MyBatis 事务与当前源码字节；不等于生产数据库已经迁移、公共 OAuth/MCP 路由已开启、
WorkBuddy 已完成 OAuth 联调或 VIP 商业闭环成立。

`8.0.29` 仍只属于 W4a 的最低语法门槛，不属于 W4b allowlist。其它 MySQL 构建、MariaDB 或
其它数据库产品均保持 `UNVERIFIED`，不得依据版本高低推断兼容或不兼容。

## 2. 精确支持矩阵

| 数据库构建 | W4b 状态 | 允许行为 | 已证明 | 未证明 |
|---|---|---|---|---|
| MySQL Community `8.0.30` | `ALLOWLISTED_REFRESH_SECURITY_VERIFIED_LOCAL` | 允许进入 033–036 迁移和 current-read 门禁 | 55 项既有事务测试、3 项 refresh-security、36/36 canonical receipts | 生产迁移、公开路由、宿主联调 |
| MySQL Community `8.4.8` | `ALLOWLISTED_REFRESH_SECURITY_VERIFIED_LOCAL` | 允许进入 033–036 迁移和 current-read 门禁 | 55 项既有事务测试、3 项 refresh-security、36/36 canonical receipts | 生产迁移、公开路由、宿主联调 |
| MySQL Community `8.0.29` | `NOT_ALLOWLISTED_FOR_W4B` | initializer 在写 RUNNING 和执行文件前拒绝 | 仅 W4a 最低语法门槛 | 全部 W4b 语义 |
| 其它 MySQL 构建 | `UNVERIFIED_FOR_W4B` | 失败关闭 | 无 | 兼容性与业务能力 |
| MariaDB 或其它产品 | `OUTSIDE_CURRENT_W4B_CONTRACT` | 失败关闭 | 无 | 兼容性与业务能力 |

## 3. 不可改写基线与后继迁移

033–035 已冻结，036 只能作为只增量后继：

| public step | 内部版本 | SHA-256 |
|---|---|---|
| `public_init_033` | `20260721_independent_board_oauth_foundation_v1` | `b103ac5936ab1cb4bce865f04256f41826d90693556bc5627c09fc4ffee5de27` |
| `public_init_034` | `20260721_independent_board_oauth_receipt_provenance_v1` | `2ba6fce7c3161b1647397485c37681974202b3a566ab370cc05587b1cfde7af3` |
| `public_init_035` | `20260721_independent_board_oauth_consent_intent_lineage_v1` | `55dc772d54a4a457f00711a45266b2d0042522d63ed0d5a8e408d35a8ecaf560` |
| `public_init_036` | `20260721_independent_board_oauth_refresh_security_v1` | `4d82cb93d7bd15035de882be69ea4bc03c5101e3851ad56083aecdfc93d2b041` |

036 在不改写前三个文件的前提下完成：

- 把 035 的 consent/principal nullable 配对升级为严格三值逻辑安全 CHECK；
- 为 `fbs_oauth_receipt` 增加 8 个 receipt-v2 字段/生成列；
- 增加 generation、causation、自因果拒绝、before/after 摘要变化与 replay 上界约束；
- 增加 refresh subject 与 causation 两组外键；
- 增加每个 security event 的唯一 slot；
- 增加 `(family_id,id)`、`(family_id,client_id,id)`、`(binding_id,id)` 三个明确锁序索引。

## 4. 中断恢复状态机

外部状态只暴露 `S0 -> S1 -> S2 -> S3/APPLIED`。MySQL 无法在同一个 ALTER 中同时增加
被引用唯一键和自引用外键，因此 S2 内部按以下单向前缀恢复：

```text
request CHECK
  -> token support
  -> causation/receipt support
  -> connector receipt lock-order support
  -> receipt-v2 columns/FK/CHECK/generated columns
  -> exact S3 current-read
  -> APPLIED
```

只接受精确前缀；列、索引、FK、CHECK 或完成回执的任意孤立/部分/漂移组合均 fail closed，
不得自动删除、改名、补猜或越过 current-read。成功和失败路径都必须清理 helper procedure 并
释放同一摘要命名锁。

本轮真实实例已覆盖：

1. 8.0.30 fresh 001–036、完成态重跑与 CurrentReadOnly；
2. 8.4.8 fresh 001–036、完成态重跑与 CurrentReadOnly；
3. 8.0.30 内部 S2 token-only 前缀恢复；
4. 8.4.8 内部 S2 token+receipt 前缀恢复；
5. 8.4.8 外部完整 S2 到 S3 恢复；
6. 8.0.30 不完整索引组失败关闭。

## 5. 元数据与应用 current-read

完成态不能只读取 migration receipt 或对象数量。校验器继续比较精确构建对应的 raw typed
metadata：列类型/nullable/default/charset/generated expression、完整索引列序与可见性、外键
列映射与动作、CHECK 原始子句/enforced、不可变 trigger，以及 W1/W4a 外部依赖。

应用层在 `REPEATABLE-READ` 根事务内按统一顺序锁定：

```text
enterprise/member
  -> entitlement/plan
  -> connector binding/scopes
  -> oauth client
  -> active slot
  -> pending slot
  -> exact family
  -> bounded family tokens
  -> W4a binding receipt prefix
  -> bounded W4b family receipts
```

refresh-family token 查询使用 `LIMIT 10001` sentinel，业务上限为 10,000 行；安全回执使用
`LIMIT 6001` sentinel，业务上限为 6,000 行。rotation 在写入前为两个后继 token 和一条回执
预留容量，不能先越界再把 family 永久锁死。

## 6. 双版本可重复证据

统一 runner 在每个精确构建上执行：

- `IndependentBoardMysqlTransactionIT`：`55/55`；
- `IndependentBoardOAuthRefreshSecurityServiceTest`：`3/3`；
- canonical initializer：首次、重跑、只读三阶段；
- public manifest：`36/36 APPLIED`；
- 进程、端口、临时目录、临时 login file 和进程环境清理：全部通过。

refresh-security 三项真库测试证明：同一旧 refresh token 两路并发线性化为一次 rotation 和
一次 replay containment；rotation 的晚期回执失败整笔回滚；replay 的晚期回执失败使 W4b
family/token 与 W4a binding/receipt 同时回滚。终态重复旧 token 请求不追加回执、不再变更状态。

详细机器回执见
`reports/independent-board/w4b-oauth-refresh-security-verification-20260721.json`。

## 7. 公开前仍需保持的门禁

严格 replay 无法仅凭旧 token 区分“凭据被盗”和“rotation 已提交但响应丢失后客户端重试”。
因此公共 token route 继续关闭，直到 WorkBuddy 实机证明：

- 同一 family 单飞刷新；
- 新 token 原子持久化；
- 模糊网络失败不自动重放旧 refresh token；
- `invalid_grant` 有明确重新授权 UX；
- 具备限流、告警和可审计恢复路径。

不得为兼容不安全重试而静默放宽 replay containment。若未来缩短刷新间隔、延长 family 生命周期
或提高请求密度，必须先实现 rolling accumulator 或带维护回执的安全归档，不能仅提高行数上限。

## 8. 产品和迁移边界

- 所有公共 OAuth/MCP 路由仍为 `closed`；
- me/admin 的 OAuth/Connector 页面下一步只能作为现有若依壳内、默认关闭的 W4b.2 候选；
- 生产数据库迁移必须使用独立 migration 账户、备份/回滚、同提交部署回执和完成态 current-read；
- 数据库本地通过不能提升为 WorkBuddy host、自然调用、same-binding、服务侧闭环或
  `BUSINESS_CONFIRMED`。
