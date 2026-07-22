# W3f 独董会后台不可变积分影子账本合同

## 1. 本波目标与关闭边界

本波只建立 `USER_GLOBAL / FBS_POINTS` 后台影子账本及其受控管理员发放、冲正和审计读取候选能力。HTTP 表面由 `fbsir.independent-board.credit-candidate.enabled` 控制，配置缺失或为 `false` 时 controller 与专属 advice 均不注册，已认证请求得到 404。它不恢复 `/points/changePoints`、`/fbs/skill-api/points/earn` 或内部 caller-selected mutation，不迁移现有积分消费者，不把账本晋级为生产权威真源，也不修改已冻结的 `fbsir-eight-seat-board@26.7.20`。

管理员发放中的正向金额是经 `admin` 角色与 `board:credit:grant` 双重授权的显式运营调整，不是面向宿主或用户的定价输入。冲正另需 `board:credit:reverse`，未来业务奖励和消费仍必须由服务端版本化规则计算。

## 2. 固定账户与权限合同

- 账户主体：`subject_type=USER`、`user_id>0`。
- 账户范围：`account_scope=USER_GLOBAL`，不得由请求选择。
- 币种：`currency_code=FBS_POINTS`，不得由请求选择。
- 发放：全局 `admin` 角色且具备 `board:credit:grant`。
- 冲正：全局 `admin` 角色且具备 `board:credit:reverse`；发放权限不得隐式包含冲正。
- 查询：全局 `admin` 角色且具备 `board:credit:query`。
- 上述权限沿用 RuoYi `PermissionService` 语义：普通管理员必须显式拥有细粒度权限，内置超级管理员的 `*:*:*` 通配权限继续生效；本波不另造一套与宿主分裂的授权解释器。
- actor 只取 JWT 登录用户，任何请求 DTO 均不得包含 actor。
- 首次开户以锁定读到的 `sys_user.points` 作为 `opening_balance`；之后账本余额与该兼容投影不一致时失败关闭。

候选开关开启后，固定 HTTP 入口为 `POST /business/independent-board/credit-operations/grants`、`POST /business/independent-board/credit-operations/reversals` 和 `GET /business/independent-board/credit-accounts/{userId}`。本波不写入 `board:credit:*` 菜单权限、不提供 UI、不绑定角色；因此只证明接口候选，不宣称后台已可操作或可生产开放。旧 `POST /points/fans/grantPoints` 对任意已认证身份固定返回 HTTP 410，匿名请求仍由全局 JWT 链返回 401，且不再调用旧积分服务。

## 3. 写入合同

### 3.1 发放

请求字段严格为 `userId`、`amount`、`reasonCode`、`note`、`idempotencyKey`：

- `amount` 为 `1..100000`；
- `reasonCode` 只能是服务端 allowlist 中的 `CUSTOMER_SUPPORT`、`SERVICE_RECOVERY` 或 `MIGRATION_CORRECTION`；
- `note` 为去除首尾空白后仍完全相同、不含控制字符的 `8..128` 字符审计备注；
- `idempotencyKey` 为 `16..128` 个 ASCII 字符，匹配 `[A-Za-z0-9][A-Za-z0-9._:-]*`；
- 未知、重复、缺失、null、类型错误或尾随 JSON 均失败关闭。

### 3.2 冲正

请求字段严格为 `originalOperationId`、`reasonCode`、`note`、`idempotencyKey`。`reasonCode` 只能是 `DUPLICATE_GRANT`、`OPERATOR_ERROR` 或 `POLICY_VIOLATION`。服务端只允许冲正已提交的 `GRANT`，从原操作读取目标账户和金额并生成相反 delta；请求不得携带用户、范围、币种、产品或金额。同一原操作最多有一个冲正操作，重复同键同摘要返回原回执，异键或异摘要返回稳定冲突。

### 3.3 原子性与幂等

固定锁序为 `sys_user -> fbs_credit_account -> original operation`。协调层必须先在独立当前读事务中按幂等键查询已提交操作：同键同摘要直接回放原结果，同键异摘要失败关闭；仅在未命中时进入新操作事务并读取用户、账户等可变状态。新操作使用普通唯一键 INSERT 抢占；并发唯一键竞争仍必须先完整回滚，再在独立 `REQUIRES_NEW` 事务中做已提交当前读回放。这样，已提交回放不依赖用户随后被删除、状态变化或兼容积分投影漂移。

冲正事务在锁定最新账户链头后，成员链证明必须使用 `FOR SHARE` 锁定当前读；不得把事务早期普通 `SELECT` 建立的 `REPEATABLE_READ` 旧快照与最新锁定账户头混用。否则并发输家会把已经提交的赢家误判为链缺失并返回伪 500。

以下动作必须在同一 MySQL 事务中完成：

1. 锁定用户兼容余额和唯一账户；
2. 校验账户余额与 `sys_user.points` 投影一致；
3. 插入不可变 operation；
4. 基于旧链头插入不可变 entry；
5. 以版本、旧余额和旧链头 CAS 更新账户到新链头；
6. 以旧余额 CAS 更新 `sys_user.points`。

任何一步失败均整体回滚。余额必须保持 `0..2147483647`，冲正不得造成负余额。账户 `updated_at` 由数据库以 `GREATEST(updated_at, CURRENT_TIMESTAMP(3))` 单调推进，避免宿主时钟回拨触发伪状态转换失败。

## 4. 不可变与可追溯合同

- operation 保存服务端生成的 operation ID、幂等键、请求摘要、固定账户范围、类型、delta、原因码、短审计备注、actor、原操作引用和前后余额。
- entry 保存账户序号、delta、前后余额、前一 entry 哈希和当前 entry 哈希。
- entry 哈希使用带长度前缀的 canonical v1 字段编码和 SHA-256；请求摘要覆盖 actor 及全部允许的请求语义。
- 数据库触发器拒绝 operation/entry 的 UPDATE 与 DELETE，拒绝账户身份、opening balance 的变化及非连续版本转换。
- API 和异常不得回显 SQL、表名、数据库异常、请求摘要、链哈希或自由备注；摘要和哈希只供服务内完整性校验，管理员 HTTP 查询再次投影后也不返回这些字段。

旧管理员发放入口及其旧 UI 写按钮已关闭，但现有内部积分消费者尚未迁移，因此本波账户仍只能称为管理员调整影子账本。内部旧写者可能在本事务提交后再次改变 `sys_user.points`；下一次账本操作会检测漂移并失败关闭，但这不等于持续全局一致。生产晋级前必须迁移或关闭全部内部旧写者，并完成交错并发对账。

## 5. 验证与晋级门槛

`public_init_038` 只允许精确的 MySQL Community `8.0.30` 与 `8.4.8` profile；其它构建在任何目标表或迁移回执写入前失败关闭。首装与当前读审计除对象数量和逻辑条件外，还绑定 45 列 raw typed metadata、20 个完整可见升序索引、4 个外键、22 个 CHECK clause 和 6 个 trigger body 的原始摘要。APPLIED 后的 canonical current-read 还必须重新审计 `sys_user` 五列依赖，并证明所有账户余额与 `COALESCE(sys_user.points, 0)` 全局零漂移；它只持有 public manifest advisory lock，不写数据库。每个破坏性漂移向量必须在独立 fresh DB 中验证，因为 MySQL 的表重建可能重序列化语义等价的 CHECK literal introducer；失败路径还必须证明回执、锁、目标表和 helper 状态并清理 helper。`group_concat_max_len` 必须在成功与异常路径恢复。

必须通过严格 DTO、RuoYi 超级管理员通配与普通管理员细粒度权限矩阵、默认关闭 404/无 controller、服务事务、MyBatis SQL 合同、迁移首装/重放/漂移、真实 MySQL 并发同键、异摘要、单次冲正、余额不足、CAS 失败回滚、触发器不可变和 `sys_user.points` 一致性测试。完成这些仅证明本地后台候选；权限菜单与 UI、真实生产迁移、旧消费者对账、切流和权威真源晋级继续保持关闭。
