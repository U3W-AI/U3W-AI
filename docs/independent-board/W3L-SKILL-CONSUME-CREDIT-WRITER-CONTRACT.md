# W3l：技能消费积分内部写者合同（工程决策已锁定，生产启用仍需审批）

## 目标

将个人场景包的付费 `SkillConsumeServiceImpl` 消费链，从当前
`fbs_skill_usage_record(status=0) → IPointsService.changePoints → status=1/2`
逐步迁移为可审计、可重放、原子化的内部账本写者。目标是消除旧
`sys_user.points` 的读-计算-更新路径，并保持所有公开积分写入口和内部 HTTP 消费入口
继续返回 `410`。

成功不等于生产启用：W3l 的首个可交付物必须默认关闭、无新增 Controller、无菜单、无
生产 GRANT/REVOKE、无冻结审核包写入。只有本合同、`public_init_042`、双开关、真实
MySQL 回归和人工发布审批均成立，才允许替换个人消费的内部写链。

**本轮工程决策：** 042 采用独立 v2 账本表与投影桥接，服务主体使用有界
`issuer_type/issuer_id`；绝不演进、复用或改写 038。该决策只定义默认关闭的候选实现，
不授权任何生产开关、切流或管理员积分操作。

## 当前事实与范围

- 当前个人消费在 `SkillConsumeServiceImpl` 写入 `status=0` 后，调用
  `IPointsService.changePoints(userId, ruleCode, -amount, packId, usageRecordId)`，再无条件尝试
  写入成功或失败状态；它不是账本级终态 CAS。提交 `ee7d2f0f` 已把既有成功分支的
  `status=1` 转换为受检 CAS，防止 CAS 丢失伪装成功；它没有、也不声称解决旧积分读改写
  并发或证明真实数据库回滚。
- `public_init_038` 的 `fbs_credit_operation` 仅允许 `GRANT|REVERSAL`，要求正的
  `actor_user_id`，并绑定管理员原因码和正/负金额。不得把消费伪装成其中任一语义，也
  不得改写 038 历史清单、约束或回执。
- 企业配额消费、积分管理员治理、OAuth、连接器、Webhook、Watch/Hub 与已提交审核包
  不在本切片写入范围内。

## 拟定内部合同

### 准入与开关

1. 仅 Java 服务内部可调用的 `SkillConsumeCreditWriter` 可以请求消费记账；不得增加 HTTP、
   MCP 或管理端写接口。
2. 运行时同时要求：
   `fbsir.independent-board.credit-ledger-candidate.enabled=true` 与
   `fbsir.independent-board.skill-consume-credit-writer.enabled=true`，并且 042 current-read 已
   验证。任一条件不满足时，保持旧链且不声称账本已权威化。
3. 仅个人 `USER_GLOBAL/FBS_POINTS` 的已授权、非免费场景包消费可进入候选写者；免费包、
   企业配额路径和任何范围不明输入不得旁路现有 fail-closed 检查。

### `skill-consume-credit-v1` 命令

服务端创建规范化摘要，至少绑定：`userId`、`usageRecordId`、`packId`、`packVersion`、
`skillCode`、`ruleCode`、绝对消费额、`hostType`、`hostSessionId` 与协议版本。

- `usageRecordId` 是外部重放锚点，不是可自由选择的积分幂等键；写者生成受限格式的
  `idempotency_key` 和 UUID `operation_id`。
- 相同命令重放必须返回同一已提交结果；相同重放锚点但不同摘要、用户、包、技能、主机
  或金额必须稳定冲突且零写入。
- 余额不足、规则停用、范围不匹配、042 不完整、终态 CAS 失败或任一数据库异常必须整体
  回滚；不得留下孤儿 usage、operation、entry、account 链头或 `sys_user.points` 投影。

### 042 独立 v2 模型与双账本互斥

042 只能新建 `fbs_skill_credit_account_v2`、`fbs_skill_credit_operation_v2`、
`fbs_skill_credit_entry_v2` 和 `fbs_skill_credit_projection_bridge_v2`。新表统一使用
InnoDB 与 `utf8mb4_unicode_ci`，机器标识、摘要、UUID、幂等键和枚举使用 `ascii_bin`。

- account 以 `(subject_type='USER', user_id, account_scope='USER_GLOBAL',
  currency_code='FBS_POINTS')` 唯一；保存余额、版本、链序号及末链哈希。
- operation 以 `usage_record_id` 与受限 `idempotency_key` 分别唯一，固定
  `operation_type='SKILL_CONSUME'`、`reason_code='SKILL_USE'`、负金额、
  `issuer_type='SERVICE'`、`issuer_id='FBS_SKILL_CONSUME_V1'`，并保存完整命令摘要、
  pack/version、host 类型和 host session 的 SHA-256（不保存原文）。
- entry 以 `(account_id, sequence_no)` 和 `operation_id` 唯一，保存前后余额和
  `skill-credit-entry-v1` 链哈希；bridge 以 account 和用户投影范围唯一，保存
  `projected_balance` 与 `projection_version`。
- v2 operation **不得**对旧 `fbs_skill_usage_record` 建 FK：旧表只能在同一业务事务中
  被精确锁读与终态 CAS，绑定事实由 operation 的唯一锚点、命令摘要和不可变回执证明。

042 的候选写者开启时必须选择唯一权威，不能双写：先锁 `sys_user`，若该用户已有 038
`fbs_credit_account`，v2 零写入失败关闭；v2 current-read 必须同时验证 account、bridge、
`sys_user.points`、版本、链哈希以及“无 038 account”。两开关都开启后，038 v1 的
grant/reversal/audit current-read 与写入必须稳定失败关闭，直到候选关闭或完成明确的人工
迁移；默认关闭时保持 038 与旧消费链不变。

### 原子事务与终态

同一事务必须按固定锁序 `sys_user → usage_record → v2 account/bridge → operation/entry` 读取或
创建状态，再更新 `sys_user.points` 投影，并以
`WHERE usage_record_id=? AND status=IN_PROGRESS` 的影响行数精确决定 usage 成功。CAS 输家
只能读取已提交赢家或返回稳定冲突，绝不可二次扣款；任何受影响行数不为 1 都必须抛异常。

`SKILL_CONSUME` 是负金额的独立操作语义，理由码固定为 `SKILL_USE`；操作 actor 不能伪造
管理员用户。042 必须提供明确的服务主体/issuer 表达，并让 038 v1 与 042 v2 current-read
互斥、可审计、失败关闭。

### 042 迁移状态机与 DDL 事实边界

MySQL atomic DDL 只保证单条 DDL 原子，DDL 仍会隐式提交；042 不得声称多语句迁移可整体
`ROLLBACK`。迁移 runner 必须以受限状态机失败关闭：

1. `public_init_042=RUNNING` 后获取稳定 `GET_LOCK`，核验 MySQL 8.0.30/8.4.8、
   `u3w_schema_migration`、`sys_user` 投影列、usage 唯一键/状态形状，以及 038 的只读精确
   合同。
2. `RUNNING + 零 v2 对象` 才是首次执行；`RUNNING + 完整精确对象 + 无内部 receipt` 只允许
   一次有界重放以补 receipt；任意部分对象、索引/FK/CHECK/trigger 漂移、receipt 重复或孤儿
   bridge 均立即失败，**不**删除、重建或清理数据对象。
3. 所有 v2 FK、具名 `ENFORCED CHECK`、索引和八个保护 trigger 必须在新表 `CREATE TABLE`
   或本迁移内定义；不得 `ALTER` 旧表或 038。先全量 raw metadata current-read，再写内部
   receipt；runner 另行 current-read 后才把 public step 提升为 `APPLIED`。
4. manifest、`initialize-database.ps1`、`verify-database-manifest.ps1` 与双 MySQL runner
   必须同时覆盖 042；042 不能只留下 SQL 而没有可执行 current-read 验证。

## 技术结构与代码风格

```text
FBSir-business/.../fbs/service/impl/SkillConsumeServiceImpl.java
  -> board/credit/SkillConsumeCreditWriter (package-private/internal port)
  -> board/credit/SkillConsumeCreditTransactionService (@Transactional)
  -> mapper XML / 042 v2 ledger tables-or-constraints
  -> fbs_skill_usage_record terminal CAS
```

遵循现有 Spring Java 风格：构造函数或既有注入方式保持一致、常量使用稳定错误码、SQL 由
MyBatis 绑定参数表达，不拼接调用方输入。请求摘要只接受有限枚举和长度受限字段；日志不
输出 host session、原始身份令牌或未裁剪的调用方文本。

## 命令与验证

```powershell
# 目标服务单元与事务回归（test 会重新编译测试源；不要以 surefire:test 代替）
& $env:U3W_MAVEN -pl FBSir-business -Dtest=SkillConsumeServiceTest,SkillConsumeCreditWriterTest test

# API Key/HMAC 链路需从当前源码 reactor 编译依赖闭包，不能引用本地旧业务 JAR
& $env:U3W_MAVEN -pl FBSir-admin -am `
  -Dtest=FbsSkillApiHttpSecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test

# 现有独董会全局合同与清单
$env:U3W_NODE_EXE='C:\Users\10171\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-independent-board-control-plane.ps1 -Mode Contract
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-database-manifest.ps1

# 042 双 MySQL runner（仅在实现后新增，必须显式允许可销毁数据库）
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1 -AllowDestructiveTest -IsolatedWorkRoot C:\u3w-w3l-it
```

测试必须覆盖：双开关关闭、042 未就绪、精确/异摘要重放、跨用户/包/技能/主机攻击、余额
不足、规则停用、并发同 usage、并发不同 usage 的余额竞争、usage CAS 输家、整体回滚、
038 不漂移、042 首次/重放/半完成恢复、8.0.30 与 8.4.8、旧 `410` HTTP 回归。既有旧链 P0
还必须以真实事务验证“终态 CAS=0 时积分、企业额度和 usage 均未提交”；Mockito 只可证明
拒绝伪成功，不能替代该门禁。

## 边界

- **始终执行：** 先写 RED 测试；双版本真实 MySQL；保持冻结包 SHA；保留失败回执；提交前
  运行合同与清单验证。
- **需要人工批准：** 生产 definer/GRANT/REVOKE、任何候选开关启用、v1/v2 实际权威迁移、
  发布或切流。042 v2 表与 `issuer_type/issuer_id` 的工程形状已在本轮锁定，不再作为代码实现
  的开放分支。
- **绝不执行：** 修改 038 或审核包历史字节；把消费记为 GRANT/REVERSAL；恢复旧 `/fbs/skill-api/points/earn`、
  `FbsSkillConsumeController` 或管理员直写入口；记录或提交密钥。

## 分解任务与验收

1. **042 模式决策与迁移合同**
   - 验收：038 字节/元数据保持不变，042 对 v1/v2 状态 fail-closed，并有初次、重放、漂移、
     半完成恢复矩阵。
2. **内部写者与 usage CAS**
   - 验收：无新 Controller；双开关关闭零交互；相同命令单一 operation/entry；异摘要稳定冲突；
     所有失败整体回滚。
3. **真实双 MySQL 与 HTTP 回归**
   - 验收：两个版本各通过并发/负向矩阵；所有旧 HTTP 写入口继续 410；生产连接次数为零。
4. **发布准备**
   - 验收：生成受限权限、回滚、观察和人工审批清单；未获得真实目标回读时报告保持 NO-GO。

## 已关闭的设计分歧与仍然阻断生产的事项

- 已关闭：042 为独立 v2 表及 projection bridge；服务身份为
  `issuer_type/issuer_id`；v1/v2 current-read 必须互斥。
- 仍然阻断生产：真实双 MySQL 迁移/并发/事务回滚证据、v1/v2 切换对账、最小权限、候选开关
  启用、发布窗口和服务侧目标版本回读。候选实现完成前不得影子写、不得扣减真实余额、不得切流。
