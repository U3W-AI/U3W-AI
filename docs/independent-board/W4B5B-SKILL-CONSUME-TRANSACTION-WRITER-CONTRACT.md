# W4B5B：技能消费 v2 内部事务写者合同

## 本切片目标

本切片为 042 默认关闭账本补齐 Java 内部写者：`SkillConsumeCreditWriter` 只协调
规范化命令、已提交重放和 `REQUIRES_NEW` 事务；`SkillConsumeCreditTransactionService`
在单一事务内维护旧 usage 兼容记录、v2 account/bridge、不可变 operation/entry、`sys_user.points`
投影及 usage 终态 CAS。它没有 Controller、MCP、管理员写入口或配置启用代码，也没有注入
`SkillConsumeServiceImpl`；W4B4 双开关仍稳定返回 `SKILL_CONSUME_CREDIT_WRITER_NOT_READY`。

## 固定安全规则

- 命令先同时收紧到 042 与不可 ALTER 的旧 usage 表的交集：usage/pack version/skill/host session
  最长为 `64/32/64/128`。不得为候选写者修改旧表或 042 DDL。
- fresh transaction 是 `REQUIRES_NEW + REPEATABLE_READ`，锁序固定为
  `sys_user → fbs_skill_usage_record → v2 account/bridge → operation/entry`，随后才进行 account、
  bridge、`sys_user.points` 和 usage 终态 CAS；任一影响行数不为 1 均抛出并回滚。
- `operation/entry` 永不更新或删除；v2 entry 固定使用 `skill-credit-entry-v1`，不复用 038 的
  `credit-entry-v1` 哈希语义。写者不调用 `IPointsService.changePoints`，也不写 038 表。
- 同键或同 usage 的唯一冲突必须先退出失败的 fresh transaction，再在独立 `REQUIRES_NEW` 事务
  读取唯一已提交赢家。范围精确但已成功的 usage 也触发该回放路径；不同摘要稳定失败关闭。
- 已提交回放仅验证不可变 v2 operation/entry 与旧 usage 的精确 `SUCCESS` 回执，不依赖用户后续
  停用、余额变化或未来修复动作，确保同一命令返回同一已提交结果。

## 仍然禁止启用

本地 Mockito/MyBatis 合同通过不等于可上线。以下门槛仍为 NO-GO：真实 MySQL 8.0.30/8.4.8 的
042 迁移与并发/回滚矩阵、`sys_user`/bridge 漂移证明、038 反向建账互斥围栏、042 runtime
readiness 回执，以及审批准入。切片未启用任何开关、未连接生产、未执行迁移或发布，已上架
26.7.20 包不变。

## 验证

```powershell
& $env:U3W_MAVEN -o -pl FBSir-business `
  -Dtest=SkillConsumeCreditCommandTest,SkillConsumeCreditWriterTest,SkillConsumeCreditTransactionServiceTest,SkillConsumeCreditLedgerMapperContractTest `
  -DforkCount=0 test

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts\verify-independent-board-control-plane.ps1 -Mode Contract
```
