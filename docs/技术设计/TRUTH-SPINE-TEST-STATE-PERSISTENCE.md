# Truth Spine 测试态持久化边界

## 结论

`fbs_truth_spine_receipt_batch` 是 API2 已完成验签后的脱敏批次账本，第一阶段只允许 `TEST_QUARANTINE`。它不是 `fbs_orchestration_receipt` 的扩展，也不代表 WorkBuddy 宿主已签发 `HostReceipt`。

## 入库条件

- 调用方已在 API2 的受控测试态完成验签与工作负载认证；本服务不接收原始签名、私钥、会话正文或用户正文。
- 仅保存调用、同绑定、宿主回执和规范化负载的 SHA-256 摘要，以及验签公钥 ID；摘要必须为小写十六进制，拒绝大小写变体。
- 幂等键只在受控 `workload_id` 内有效；同工作负载重复时必须逐项比对批次 ID 和证据身份，相同请求返回原批次，任一摘要或批次 ID 不同即拒绝。
- SQL 沿用 U3W 的 `u3w_schema_migration` 与数据库命名锁约定；若发现“表已存在但迁移未登记”即停止，已登记的重跑也会核验 InnoDB、16 个必需列、工作负载复合唯一键和 5 条零信用约束。

## 明确不计入的信用

每一行固定为 `product_credit_eligible=0` 与 `business_closure_eligible=0`。它不能证明自然首值、继续使用、转化、成交或正式上架效果。

## 仍未具备的生产条件

生产接入另需独立方案与审批：宿主原生不可导出签名密钥、渲染后 ACK、三轮同绑定、生产数据库迁移、KMS/密钥轮换、mTLS、限流、审计 outbox 与影子观测。不得通过修改这张测试态表绕过这些闸门。

## 真实 MySQL 验证

`TruthSpineReceiptBatchMysqlIT` 默认不参与 Maven 生命周期，且只有在显式设置专用数据库、`TRUTH_SPINE_MYSQL_IT_ALLOW_DROP=true` 时才能执行。它使用真实迁移脚本，验证 32 并发同证据幂等、以 `useAffectedRows=false` 为基准并在内部派生 `true` 对照连接的并发证据冲突拒绝，以及数据库 `CHECK` 对两类信用提升的拒绝。目标库必须精确为 `127.0.0.1` 上的 `u3w_truth_spine_it`，不能指向任何共享或生产库。
