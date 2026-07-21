# 独董会 W4b 数据库支持矩阵

状态：`exact_build_allowlist_and_foundation_verified_local`

品牌：福帮手 / FBSir

产品：独董会 `26.7.20`

## 1. 结论

W4b 数据库迁移当前只允许以下两个**精确 MySQL Community Server 构建**：

- `8.0.30`
- `8.4.8`

两者均已用真实 MySQL Server 完成 W4b foundation 首次执行、重放、raw typed metadata
基线、负向漂移、命名锁释放和失败恢复验证。这里的“已验证”只指精确构建上的数据库
foundation；不等于授权码/token-family/受保护 MCP 内部服务、公开 OAuth/MCP 端点、生产
数据库迁移或端到端业务闭环已经完成。

`8.0.29` 只属于 W4a 迁移的最低语法门槛，不属于 W4b 已验证 allowlist。任何不在 W4b
allowlist 中的数据库构建，canonical initializer 必须在写入 `public_init_033=RUNNING` 和执行
迁移文件前 fail closed。直接执行迁移 SQL 时，用于条件控制的 helper procedure DDL 会先发生，
但首张 W4b 持久化目标表不得创建；失败后 helper procedure 可能需要受控清理。这个拒绝只表示
“当前没有该精确构建的验证合同”，不得写成该版本天然不兼容，也不得扩展为对整个 MySQL 或
MariaDB 产品线的兼容性结论。

## 2. 精确支持矩阵

| 数据库构建 | W4a 含义 | W4b 当前状态 | W4b 行为 | 可宣称内容 |
|---|---|---|---|---|
| MySQL Community `8.0.30` | 满足 W4a 最低语法门槛 | `ALLOWLISTED_FOUNDATION_VERIFIED_LOCAL` | 允许进入 W4b 迁移的后续前置与 current-read 门禁 | 该精确构建的 W4b foundation 与 raw typed metadata 基线已由真实实例验证 |
| MySQL Community `8.4.8` | W4a 已有真实集成证据 | `ALLOWLISTED_FOUNDATION_VERIFIED_LOCAL` | 允许进入 W4b 迁移的后续前置与 current-read 门禁 | 该精确构建的 W4b foundation 与 raw typed metadata 基线已由真实实例验证 |
| MySQL Community `8.0.29` | 仅为 W4a 的最低语法门槛 | `NOT_ALLOWLISTED_FOR_W4B` | initializer 在 RUNNING/文件执行前拒绝；直跑 SQL 在首张持久化目标表前拒绝 | 尚无 W4b 精确构建验证；不得称为不兼容 |
| 其它 MySQL 构建 | 不从版本号范围推断 | `UNVERIFIED_FOR_W4B` | initializer 在 RUNNING/文件执行前拒绝；直跑 SQL 在首张持久化目标表前拒绝 | 尚无该精确构建的 W4b 验证；不得称为不兼容 |
| MariaDB 或其它数据库产品 | 不属于 MySQL Community 精确构建 allowlist | `OUTSIDE_CURRENT_W4B_CONTRACT` | initializer 在 RUNNING/文件执行前拒绝；直跑 SQL 在首张持久化目标表前拒绝 | 当前合同未覆盖；不得外推产品级兼容或不兼容结论 |

## 3. 元数据基线合同

W4b current-read 必须使用保留原始类型和值边界的 raw typed metadata digest，而不是只比较
表数、列数或经过 `LOWER`、空白折叠等有损归一化后的文本。基线至少覆盖：

- 列类型、nullable、默认值、字符集/排序规则以及生成列表达式；
- 索引列/表达式、方向、前缀长度、可见性和索引类型；
- 同库外键、列链接、唯一约束名以及 `ON UPDATE` / `ON DELETE` 动作；
- CHECK 原始子句、约束名和 enforced 状态；
- 六张 W4b 表范围内的不可变 trigger 精确集合；
- W1 与 W4a 外部依赖表、列、唯一键、外键和完成回执的当前状态。

任何摘要为 `NULL`、字段缺失、同名弱化约束、生成列表达式漂移、索引/FK 语义漂移、trigger
漂移或外部依赖漂移都必须 fail closed，不能用“迁移回执存在”代替当前结构审计。

## 4. 扩展支持门禁

新增任何数据库构建到 W4b allowlist，必须同时完成并留存：

1. 该**精确 MySQL Community 构建**上的真实 MySQL 首次执行、重放和 current-read 基线；
2. 与该构建对应的 raw typed metadata digest 基线；
3. 同名弱化 CHECK、生成列表达式、nullable/type/charset、索引、FK、trigger 和外部依赖等
   负向漂移矩阵，且每次失败均证明首张持久化目标表 DDL 前或完成态写入前 fail closed；
4. 命名锁释放、失败后可恢复重跑和完成回执不越过结构审计的证据；
5. 本支持矩阵、W4b 授权合同、状态文件、验证报告和自动化断言的同一轮修订。

缺少任一项时，该构建只能保持 `UNVERIFIED_FOR_W4B`，不能因版本号更高、同属 MySQL 8.x、
一次迁移成功或表数相同而进入 allowlist。

## 5. 与公开能力和 UI 的边界

数据库 allowlist 不解锁产品表面。当前仍保持：

- 所有公共 OAuth/MCP 路由关闭；
- me 的 OAuth 同意、连接、断开页和 admin 的 client、token family、安全事件及 Connector
  管理页仅进入详细原型规划；
- 上述详细原型必须取得用户明确确认后才可进入生产实现；
- 本地数据库验证不得推断 WorkBuddy 已联调、Connector 已上架、真实域名已部署或 VIP 已激活。

## 6. `public_init_034` 后继迁移状态

`public_init_034` 为 `public_init_033` 的只增量后继迁移，新增
`fbs_oauth_receipt.family_created_slot` nullable generated column 及其唯一索引，约束范围仅为
`action='TOKEN_FAMILY_CREATED'`：该 action 的 slot 取 `family_id`，其它 action 的 slot 必须为
`NULL`，因此不限制其它回执语义。`public_init_033` 保持字节不变，其固定 SHA-256 为
`b103ac5936ab1cb4bce865f04256f41826d90693556bc5627c09fc4ffee5de27`。

当前状态为 `DIRECT_SQL_DUAL_BUILD_VERIFIED_CANONICAL_PENDING`。静态合同包括：精确 033 回执与
shape 前置、缺失 lineage/重复 family 拒绝、摘要命名锁、内部 `RUNNING/APPLIED` 状态、DDL
中断后的精确恢复分支，以及在完成回执前重新 current-read 表/列/索引/FK/CHECK/trigger 与
lineage。034 的列/索引元数据采用“033 原始对象子集 raw digest + 新增列和索引逐字段精确合同”
的分解门禁；生成表达式只接受 `_utf8mb4`、`_ascii` 与无前缀三种等价规范形式。完整 metadata
摘要计算前必须把 session `group_concat_max_len` 提升到 `1048576`，异常和成功路径均恢复原值。

在同一当前工作树上，disposable runner 已分别于 MySQL Community `8.0.30` 与 `8.4.8` 完成
034 direct SQL matrix；两版均为 `BUILD SUCCESS`、`44 tests / 0 failures / 0 errors`。实际覆盖：

- clean first apply、completed replay、`RUNNING+无 DDL` 与 `RUNNING+完整 DDL` 恢复；
- column-only/孤立 DDL、缺失 lineage、重复历史、不可见索引漂移拒绝；
- 32 路同 family 并发仅一个 winner、8 路不同 family、其它 action 的 NULL 语义；
- winner rollback 后重试、receipt UPDATE/DELETE 不可变、命名锁与 helper procedure 清理。

两版完整 successor CHECK digest 均实测为
`d8878ff64c7e897ddd8d42db6f0afd1a264d2cc8c1794d155051f0cd1638103b`。以上只构成 direct SQL
证据；canonical public initializer 的三阶段实测仍为 `PENDING`，不得写成 canonical 已通过、
生产迁移已完成，亦不得据此解锁任何 OAuth/MCP 公共路由或 me/admin 页面。
