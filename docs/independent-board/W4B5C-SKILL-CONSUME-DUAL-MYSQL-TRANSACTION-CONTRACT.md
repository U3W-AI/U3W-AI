# W4B5C：技能消费 v2 双 MySQL 应用事务合同

## 目标与边界

本切片只补齐 W4B5B 内部事务写者的真实数据库证明和 038/042 双向权威互斥。验证通过不等于生产启用：
`SkillConsumeCreditWriter` 仍未接入 `SkillConsumeServiceImpl`，两个候选开关仍默认关闭，不新增 HTTP、MCP、管理员写入口，
不执行生产迁移、发布或切流，也不修改已上架独董会 26.7.20 包。

## 必须同时成立的证据

- 使用原样 038、042 迁移，在隔离回环实例上顺序验证 MySQL `8.0.30` 和 `8.4.8`；Java 侧使用真实
  Connector/J、Spring 事务代理、MyBatis mapper 与 InnoDB `REPEATABLE-READ`。
- 首次消费与完全相同命令重放只提交一套 account、operation、entry、bridge、usage 结果，并返回已提交赢家。
- 32 路同命令并发只产生一个不可变操作；同锚点不同摘要稳定拒绝；同用户两个 60 点消费争抢 100 点余额时，只允许一个成功。
- usage 终态 CAS 返回 0 时，旧 usage、v2 财务表、bridge 与 `sys_user.points` 必须在同一事务中全部回滚。
- 已存在 038 account 的用户不得创建 042 account；已存在 042 account 且两个候选开关同时打开时，038 的 grant、reverse、audit
  必须在事务前统一返回 `CREDIT_LEDGER_V2_AUTHORITY_CANDIDATE_ACTIVE`（HTTP 语义 409）。

## 安全闸

- 运行器要求显式 `-AllowDestructiveTest`，数据库 URL 必须是 `127.0.0.1`、随机非 3306 端口及唯一 `w3l_*` schema。
- 每个版本均使用独立临时数据目录；成功或失败都停止实例、恢复环境变量并清理目录。
- Surefire 报告必须是本轮新生成，且精确为 `4/4`、零失败、零错误、零跳过；任一条件不满足即失败关闭。
- 运行器记录迁移、写者、事务服务、mapper、038 反向围栏、Java IT 与自身源码 SHA-256，防止证据与被测代码漂移。

## 当前结论

本地候选结果为 `PASS_LOCAL_DUAL_MYSQL_APPLICATION_TRANSACTION_MATRIX`。该结果仅解除 W4B5B 的真实双 MySQL 应用事务证明和
038 反向围栏两个缺口；`releaseReady` 继续为 `false`。下一主线是单独完成默认关闭的宿主接线与准入/回滚合同，不能以本合同直接启用生产。

## 验证命令

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts\run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1 `
  -AllowDestructiveTest
```
