# W4B5A：技能消费 v2 内部命令合同

## 范围

本切片只创建 package-private `SkillConsumeCreditCommand`，为将来的 042 内部事务写者锁定输入规范、重放锚点和摘要。它不创建 mapper、事务服务、数据库写入或 HTTP/MCP 入口，不修改 `SkillConsumeServiceImpl`、W4B4 默认关闭围栏、038、042 DDL、旧积分 CAS 或已上架 26.7.20 包。

## 固定字段与规范化

命令仅由内部代码通过 `create` 创建，并绑定：用户、usage record、场景包及版本、技能、规则、正消费额、主机类型和主机会话。它固定以下服务端语义：

- `protocolVersion=skill-consume-credit-v1`
- `USER_GLOBAL/FBS_POINTS`
- `SKILL_CONSUME/SKILL_USE`
- `SERVICE/FBS_SKILL_CONSUME_V1`

所有落库机器标识同时遵循 042 与不可 ALTER 的旧 `fbs_skill_usage_record` 的较窄 ASCII
边界：usage record/pack version/skill/host session 分别最多为 64/32/64/128 字符；个人宿主严格只允许既有的
`WORKBUDDY|STANDALONE|API`（大小写精确）。范围外主机、空/控制字符/格式字符/未配对
surrogate/超长主机会话、非正用户/包/金额和范围外标识立即以稳定
`SKILL_CREDIT_COMMAND_INVALID_*` 拒绝。命令不保留原始 `hostSessionId`，只保存其 SHA-256 摘要。

`requestDigest` 使用 UTF-8 字节长度前缀的 SHA-256 规范化序列，覆盖全部命令字段和服务端固定语义。`idempotencyKey` 固定为受限的 `scv1:` 加 usage anchor SHA-256；同一 usage 的不同摘要因此保有同一重放锚点，后续写者必须稳定冲突而不是二次扣款。

## 边界与后续

本命令零数据库副作用，不能被视为 writer 已可用；W4B4 的双开关仍必须返回 `SKILL_CONSUME_CREDIT_WRITER_NOT_READY`。下一切片只能以此命令实现 042 专用 mapper 与单事务写者，并按固定锁序维护 `sys_user → usage_record → v2 account/bridge → operation/entry`、旧积分投影和 usage 终态 CAS。

## 验证

- RED：命令类型缺失时，`SkillConsumeCreditCommandTest` 无法编译。
- GREEN：同命令摘要稳定，用户、usage、包/版本、技能、规则、金额、主机和会话任一变化均改变摘要；同 usage 异摘要共享幂等锚点、主机会话不保留、负 delta 固定；不安全、非正、非法宿主、企业和非法 UTF-16 输入被拒绝。
- `releaseReady` 始终为 `false`；不执行生产连接、迁移、开关启用或发布。
