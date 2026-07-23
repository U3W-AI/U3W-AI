# W4B4：技能消费 v2 候选写者默认关闭围栏

## 目标与边界

W4B4 只为尚未实现的 042 v2 内部写者建立运行时安全围栏。它不实现账本写者、事务、Mapper、重放或 v1/v2 权威迁移；不修改 038、042 DDL、旧积分 CAS、任何 Controller、菜单、生产配置或已上架独董会 26.7.20 包。

## 双开关语义

个人付费消费仅在同时满足以下两个默认 `false` 的配置时进入候选围栏：

```yaml
fbsir.independent-board.credit-ledger-candidate.enabled: false
fbsir.independent-board.skill-consume-credit-writer.enabled: false
```

两个开关都为 `true` 时，`SkillConsumeServiceImpl` 在场景包、规则和权限已确认之后、创建 `fbs_skill_usage_record` 或调用 `IPointsService.changePoints` 之前，稳定返回：

```
SKILL_CONSUME_CREDIT_WRITER_NOT_READY
```

这表示拒绝不完整的候选激活，并不表示 v2 写者已可用。任意一个开关为 `false` 时，旧个人积分链保持原样；这避免既有 `credit-candidate` 管理读面单独启用时意外改变消费。免费个人包与 `ENTERPRISE` 配额路径不属于 v2 个人付费写者范围，双开关也不得拦截它们。

## 不变量

- 双开关拒绝只允许在付费个人路径的已授权读校验之后发生；拒绝时零 usage 查询/插入/终态更新，零旧积分写入。
- 不新增 HTTP、MCP、管理员或内部 Controller 写入口。
- 042 继续只是默认关闭的数据库候选；没有真实 `SkillConsumeCreditWriter` 前，不得将任一开关用于生产切流。
- 26.7.20 冻结包 SHA-256 必须保持 `d2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd`。

## 验证

- RED：新测试在围栏字段缺失时失败，证明合同不是回填式断言。
- GREEN：`SkillConsumeServiceTest` 覆盖双 true 的付费个人零写拒绝、任一单独开关保留旧链、免费包与企业配额不受双开关影响，以及既有个人/企业重放与终态 CAS 回归。
- 全局独董会合同、数据库清单和冻结包指纹必须继续通过；`releaseReady` 保持 `false`，不执行生产迁移或发布。
