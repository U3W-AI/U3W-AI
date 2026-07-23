# W4B5E：技能消费 v2 激活安全合同

## 本切片关闭的边界

本切片只关闭 W4B5D 的两项可在仓库内完整证明的激活风险，两个生产候选开关继续
保持默认关闭：

1. `SkillConsumeServiceImpl.consume` 不再持有外层数据库事务后调用 v2
   `REQUIRES_NEW` writer；legacy 个人和企业写入改由必需注入的独立事务执行器承载；
2. v2 writer 和宿主分派器都拒绝从活动外层事务进入，稳定返回
   `SKILL_CONSUME_V2_AMBIENT_TRANSACTION_FORBIDDEN`；
3. legacy 个人路径在事务内重新读取并校验 pack、版本、规则和积分额，漂移时在任何
   usage 或 points 写入前返回 `SKILL_CONSUME_LEGACY_ROUTE_DRIFT`；
4. 公共接口允许的 `null hostSessionId` 映射为域隔离的 64 位摘要；空串、全空白、
   控制字符和超长值仍失败关闭；非空会话沿用 W4B5A 摘要，避免迁移已存在候选数据；
5. 命令生成与 usage 重放范围校验共用同一摘要函数，`null` 与任意非空会话不能互相重放。

Spring 官方文档明确说明 `REQUIRES_NEW` 会获取独立物理连接，外层资源仍保持绑定，
连接池不足时可能耗尽；默认代理模式也只拦截经代理的外部调用。因此 legacy 事务边界
必须是独立 Spring bean，不能依赖同类自调用。

## 验证门禁

- 定向 Java 回归必须覆盖命令、writer、transaction service、host service；
- 精确 MySQL Community 8.0.30 与 8.4.8 各运行 6 个固定用例；
- 宿主双开用例必须以 `null hostSessionId` 完成首次消费与精确重放，并证明兼容 usage
  行仍保存 SQL `NULL`；
- ambient transaction 用例必须证明用户余额、v2 operation 和 legacy usage 均为零写；
- 专用数据库实例只能绑定随机 loopback 端口，成功或失败都必须停止并清理；
- 26.7.20 上架包哈希继续固定为
  `d2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd`。

## 尚未关闭的生产阻断

Commercial Hub 不能用“本地 outbox 已入队”冒充端到端幂等。现状仍有：

- `smartsheet_add_records` 每次生成随机 `record_id`；
- `syncCommercialHub` 吞掉异常且不返回 provider receipt；
- 网络不确定失败会在 CLI 内重试，远端成功、本地未知时可能重复追加；
- 当前没有精确生产 CLI 的按 operation-id read-back/upsert 证明。

因此本切片保持：

- `commercialHubOutboxOpen=true`；
- `externalDeliveryExactlyOnce=false`；
- `exactTargetActivationReceiptOpen=true`；
- `releaseReady=false`。

下一切片 W4B5F 必须新增 043 后继迁移和专用 Commercial Hub gateway：冻结 operation
payload、确定性远端记录键、事务内 enqueue、短事务 lease/token fencing、事务外网络
调用、provider receipt/响应摘要、不确定结果 read-back 对账，以及精确 MySQL 和目标
CLI 能力证明。不得修改 042 或 W4B5D 历史回执。
