# W4B3 旧积分余额 CAS 合同

## 边界

本合同只收紧现有 `PointsServiceImpl` 的余额写入并发边界。它不修改已上架的独董会 26.7.20 包，不启用任何独董会新账本开关，也不授权生产迁移或切流。

## 条件更新

三个 `changePoints` 入口在读到 `currentPoints` 并完成既有限频、累计额度和扣减余额校验后，必须以 `long` 计算新余额。超出 `Integer` 可表示范围或形成负余额时，必须在写入前失败；其余请求必须调用 `PointsMapper.updateUserPointsIfBalance(userId, currentPoints, newPoints)`。

映射 SQL 必须同时限定用户和读到的余额：

```sql
UPDATE sys_user
SET points = #{points}
WHERE user_id = #{userId}
  AND (points = #{expectedPoints} OR (points IS NULL AND #{expectedPoints} = 0))
```

`NULL` 分支与既有 `getUserPoints` 的 `IFNULL(points, 0)` 读语义一致，避免历史空余额在首次写入时被误判为并发冲突。

## 冲突结果与副作用

当条件更新影响行数不等于 1 时，服务必须返回失败 `AjaxResult`，其 `msg` 严格为 `POINTS_BALANCE_CONCURRENT_CONFLICT`。当余额计算溢出时，`msg` 严格为 `POINTS_BALANCE_OUT_OF_RANGE`。两种返回均发生在积分流水插入和 `markLimit` 之前；因此失败不能生成流水或占用限频。

事件幂等命中仍优先于余额写入，保持既有成功回放行为。三个成功入口均在 `AjaxResult.data` 中返回已由条件更新提交的余额。`PointsPrecheckService` 必须使用该提交余额而不是自己的早期读快照。旧的无条件 Mapper 方法暂时保留为兼容接口，但三个积分服务入口不得再调用它。

## 验证

- `PointsServiceImplTest` 覆盖三个入口的成功、竞争失败无副作用、范围失败、免费分支和事件幂等。
- `PointsMapperSqlContractTest` 用 MyBatis `XMLMapperBuilder` 和 `BoundSql` 锁定完整 SQL、四个参数绑定顺序，并拒绝 `${}`。
- `PointsPrecheckServiceTest` 验证前置校验在两次读之间发生并发写入时，仍返回条件更新实际提交的余额。
- `scripts/run-points-balance-cas-mysql-it.ps1 -AllowDestructiveTest` 在隔离的 MySQL 8.0.30 与 8.4.8 上验证两个同时读取同一余额的客户端只有一个条件更新成功、空余额归一化和不存在用户零影响。运行器仅使用 loopback 临时实例，验证 PID/数据目录/端口绑定并清理临时工作目录。
