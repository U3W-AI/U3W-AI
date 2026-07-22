# W3k 后继：独董会套餐策略受控写入权限合同

## 边界与目标

- 冻结审核包 `D:\Spg719\fbsir-eight-seat-board-26.7.20.zip` 不修改；基线 SHA-256 为 `d2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd`。
- 本合同只收敛 `FBSIR_INDEPENDENT_BOARD` 的套餐策略版本推进，不改变积分账本、OAuth、连接器、Webhook、Watch 或 Hub 的生产状态。
- 管理入口保持默认关闭。只有 `fbsir.independent-board.plan-policy-candidate.enabled=true` 且 `fbsir.independent-board.plan-policy-candidate.procedure-authority.enabled=true` 时，策略修订和审计路由才注册。

## 写入合同

1. Java 事务服务仍在固定顺序锁定两个 mutable head、验证目录/回放/回滚语义并生成完整 receipt；实际持久化只能调用 `fbsir_independent_board_plan_policy_transition_v1`。
2. 041 过程以 `SQL SECURITY DEFINER` 执行，并在同一事务内只锁定 mutable head；当前不可变 receipt 的摘要必须以非锁定读取校验。随后它插入直接后继 receipt 并 CAS 推进 head，不能阻塞引用旧 receipt 的独立操作谱系插入；首次迁移拒绝没有 041 回执的同名既存过程，最终校验安全模式与 23 个入参，避免静默复用漂移定义。
3. 041 不自动执行任何 `GRANT`、`REVOKE`、用户创建或生产切流。它只提供可审计的受控写入原语，避免把未知生产账户、host 或密钥写入源码。
4. 遗留 MyBatis 直表绑定只保留给离线迁移/故障夹具，并标注待删除；主事务路径不得调用它们。真实生产应用账户在批准窗口必须撤销对 `fbs_plan_policy_revision_receipt`、`fbs_plan_policy_head` 和 `fbs_product_plan` 的直写及 DDL/trigger 权限，仅保留必要 `SELECT` 与该过程的 `EXECUTE`。

## 生产前批准与回滚

生产激活仍为 NO-GO，必须有：

1. 专用 procedure owner 的 `SHOW CREATE PROCEDURE`、`SHOW GRANTS`、definer 存活与最小默认角色回执；禁止把未核实的 root/application 账户当作 owner 事实。
2. 应用账户的 `SHOW GRANTS`、直表 I/U/D 与 DDL/trigger 拒绝证据、`CALL` 成功证据，以及双账号同版本回放/冲突/并发回执。
3. 精确应用构件、041 迁移、候选双开关、菜单权限、健康检查和回滚读回在同一发布窗口内关联。
4. 回滚只关闭两个应用开关和回退应用构件；不得删除 receipt/head 或随意删除过程。权限恢复须由 DBA 依据批准单单独执行。

## 已证明与未证明

- 已证明：控制器双开关反证、Java 主写链的过程调用、Mapper 合同和事务边界单测、数据库清单的 41 步完整覆盖。
- 未证明：真实生产账号/GRANT、真实库迁移、双账号拒绝矩阵、生产域名或自然业务调用。它们不得由本地 DNS/HTTP 或代码审查替代。

## 单一下一动作

在可销毁 MySQL 8.0.30/8.4.8 中完成 041 的过程执行与双账号权限拒绝矩阵；通过后收集受控生产只读权限回执，再决定是否请求人工激活。
