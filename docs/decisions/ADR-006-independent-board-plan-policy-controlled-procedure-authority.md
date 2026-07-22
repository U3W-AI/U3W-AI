# ADR-006：独董会套餐策略采用默认关闭的受控过程写入权威

## 状态

已接受（本地候选；生产权限未激活）

## 日期

2026-07-23

## 背景

ADR-004/005 已建立不可变 receipt、head CAS 与单步链触发器，但应用运行时仍能通过 Mapper 对 receipt/head 执行直表 DML。数据约束不能代替账户最小权限，也不能形成可审计的唯一写入边界。

## 决策

添加 `public_init_041`，持久化一个 `SQL SECURITY DEFINER` 的受控过程。运行时事务经过既有输入、回放、目录和锁序验证后，只调用该过程推进完整 receipt/head 转换；HTTP 管理面改为候选开关和过程授权开关同时为真才注册。

过程显式使用 `DEFINER = CURRENT_USER`，使部署审批窗口能将其绑定至经核准的专用 owner，而不在仓库中猜测或固化生产账户。首次迁移拒绝没有 041 回执的同名既存过程，并核对过程安全模式和参数数量；生产 `GRANT/REVOKE` 不随迁移自动执行。

## 备选方案

- 只依赖 040 trigger：拒绝。它不能限制拥有表 DML 的账户。
- 直接在迁移中创建/授权固定生产账户：拒绝。host、认证和现网角色未知，且会把高权限变更越权自动化。
- `SQL SECURITY INVOKER`：拒绝。应用账户仍需表写权限，无法完成权限收敛。

## 后果

- 默认情况下没有新增公开能力；两道开关和真实 DB 权限批准都缺一不可。
- 过程 definer 的生命周期、默认角色和 `EXECUTE` 授权成为生产前必须回读的发布工件。
- 在完成所有旧积分写入者迁移和宿主协议升级之前，积分账本仍不能被提升为全局生产权威。

## 依据

MySQL 将存储过程执行权限由 `DEFINER` 与 `SQL SECURITY` 共同决定，调用方需要 `EXECUTE`；`DEFINER = CURRENT_USER` 是受支持的显式写法：[MySQL 8.4 CREATE PROCEDURE](https://dev.mysql.com/doc/refman/8.4/en/create-procedure.html)。
