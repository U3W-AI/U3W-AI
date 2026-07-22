# W3l：技能消费积分内部写者合同（待人工批准）

## 目标

将个人场景包的付费 `SkillConsumeServiceImpl` 消费链，从当前
`fbs_skill_usage_record(status=0) → IPointsService.changePoints → status=1/2`
逐步迁移为可审计、可重放、原子化的内部账本写者。目标是消除旧
`sys_user.points` 的读-计算-更新路径，并保持所有公开积分写入口和内部 HTTP 消费入口
继续返回 `410`。

成功不等于生产启用：W3l 的首个可交付物必须默认关闭、无新增 Controller、无菜单、无
生产 GRANT/REVOKE、无冻结审核包写入。只有本合同、`public_init_042`、双开关、真实
MySQL 回归和人工发布审批均成立，才允许替换个人消费的内部写链。

## 当前事实与范围

- 当前个人消费在 `SkillConsumeServiceImpl` 写入 `status=0` 后，调用
  `IPointsService.changePoints(userId, ruleCode, -amount, packId, usageRecordId)`，再无条件尝试
  写入成功或失败状态；它不是账本级终态 CAS。
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

### 原子事务与终态

同一事务必须按受控锁序读取账户/当前链头、核验或创建 operation、写 entry 与账户版本、
更新 `sys_user.points` 投影，并以 `WHERE usage_record_id=? AND status=IN_PROGRESS` 的影响行数
精确决定 usage 成功。CAS 输家只能读取已提交赢家或返回稳定冲突，绝不可二次扣款。

`SKILL_CONSUME` 是负金额的独立操作语义，理由码固定为 `SKILL_USE`；操作 actor 不能伪造
管理员用户。042 必须提供明确的服务主体/issuer 表达，并让 038 v1 与 042 v2 current-read
互斥、可审计、失败关闭。

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
# 目标服务单元与事务回归（新增测试类后替换为精确类名）
& $env:U3W_MAVEN -pl FBSir-business -Dtest=SkillConsumeServiceTest,SkillConsumeCreditWriterTest surefire:test

# 现有独董会全局合同与清单
$env:U3W_NODE_EXE='C:\Users\10171\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-independent-board-control-plane.ps1 -Mode Contract
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verify-database-manifest.ps1

# 042 双 MySQL runner（仅在实现后新增，必须显式允许可销毁数据库）
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\run-independent-board-credit-ledger-mysql-it.ps1 -AllowDestructiveTest
```

测试必须覆盖：双开关关闭、042 未就绪、精确/异摘要重放、跨用户/包/技能/主机攻击、余额
不足、规则停用、并发同 usage、并发不同 usage 的余额竞争、usage CAS 输家、整体回滚、
038 不漂移、042 首次/重放/半完成恢复、8.0.30 与 8.4.8、旧 `410` HTTP 回归。

## 边界

- **始终执行：** 先写 RED 测试；双版本真实 MySQL；保持冻结包 SHA；保留失败回执；提交前
  运行合同与清单验证。
- **需要人工批准：** 042 的准确数据库演进（扩展既有表约束或引入 v2 表）、服务主体字段
  形状、生产 definer/GRANT/REVOKE、任何候选开关启用或发布。
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

## 开放问题（阻止代码实现）

1. 042 应采用“在新迁移中受控演进现有 038 账本约束”还是“新建独立 v2 账本并以投影桥接”？
   前者查询路径较短但迁移/回滚风险较高；后者隔离更强但需要明确双账本权威切换和对账。
2. 服务主体应如何表示：新增有界 `actor_type/actor_id`，还是新增独立 `issuer_type/issuer_id`？
   推荐后者，保留 038 已有管理员 `actor_user_id` 的历史语义。
3. 候选开关开启后，是否允许仅影子写/对账而不扣减用户可用余额？推荐先不启用任何真实写入，
   直到 042 双 MySQL 与人工发布窗口均通过。
