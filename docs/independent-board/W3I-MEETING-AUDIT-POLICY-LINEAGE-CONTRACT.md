# 独董会 W3i 会议审计策略血缘与套餐标签权威读取合同

状态：`IMPLEMENTATION_CANDIDATE`

## 1. 目标与边界

W3i 让 `admin.u3w.com` 的会议额度审计页展示每一次操作**当时实际消费**的不可变策略回执，而非用当前套餐目录或浏览器硬编码反推名称。这样，后续策略修订、权益撤销或当前 head 前进均不会改写历史操作的审计含义。

本波次只允许修改候选管理端会议审计读取链、其 DTO/UI/测试和可追溯验证材料：

- 冻结的 `D:\Spg719\fbsir-eight-seat-board-26.7.20.zip` 必须保持 SHA-256 `d2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd`；
- 不新增迁移、菜单、角色绑定、权限、配置开关、OAuth、MCP、Connector、Webhook、积分、Apple Watch/FBSir Hub 或 API2 变更；
- 保持 `GET /business/independent-board/operations?tenantId=…`、全局 `admin + board:operation:audit` 授权和 500 条对外上限不变；
- W3h 的生产迁移/开关仍为关闭门禁，本地候选验证不等同于生产激活或自然业务闭环。

## 2. 读取模型与一致性

新增仅供读取的内部模型 `BoardOperationAuditRow`，不得向写入领域对象 `BoardUsageOperation` 注入策略回执字段。Mapper 必须至多取回 501 行，并执行只读精确关联：

```text
fbs_usage_operation o
LEFT JOIN fbs_usage_operation_policy_receipt l
  ON l.enterprise_id = o.enterprise_id
 AND BINARY l.operation_id = BINARY o.operation_id
 AND BINARY l.product_code = BINARY o.product_code
 AND BINARY l.plan_code = BINARY o.effective_plan_code
LEFT JOIN fbs_plan_policy_revision_receipt r
  ON BINARY r.receipt_id = BINARY l.policy_receipt_id
 AND BINARY r.product_code = BINARY o.product_code
 AND BINARY r.plan_code = BINARY o.effective_plan_code
 AND r.policy_version = l.policy_version
 AND BINARY r.policy_digest = BINARY l.policy_digest
```

查询固定以 `tenantId + FBSIR_INDEPENDENT_BOARD + MEETING_RESERVATION` 过滤，按 `o.created_at DESC, o.id DESC` 排序，`LIMIT 501`。不得关联 `fbs_plan_policy_head`、权益/会员表或当前套餐目录，不得使用 `FOR UPDATE`、字符串拼接或 `${}`。

服务必须先校验全部至多 501 行，再决定是否截断并仅投影前 500 行。任何缺失、重复、跨租户、产品/套餐不一致、回执 id/版本/摘要不一致、非法操作数据或非法原始 Unicode `policyPlanName` 均稳定失败关闭为 `BOARD_OPERATION_AUDIT_POLICY_LINEAGE_INVALID`（沿用 RuoYi Ajax 错误体 `code=500`，HTTP 传输状态保持既有 200 语义）；不允许以当前 head 或代码常量回退补全。

## 3. 对外合同

既有响应字段保留，并只新增以下安全投影字段：

```text
policyReceiptId, policyVersion, policyDigest, policyPlanName
```

绝不返回数据库主键、请求/命令摘要、幂等键、操作者、前序链、内部锁字段、当前策略字段或配额细节。`policyPlanName` 按原始值精确展示；它必须为 1..128 个 Unicode 码点、首尾非 Unicode 空白/分隔符，且不含 `Cc`、`Cf`、`Cs` 或畸形 UTF-16。`policyDigest` 为精确 64 位小写十六进制摘要，回执和 operation 标识符使用既有安全标识格式。

响应在成功、401、403 与 Ajax 400/500 错误情况都必须带：

```text
Cache-Control: no-store, no-cache, must-revalidate, max-age=0
Pragma: no-cache
Expires: 0（Servlet 可序列化为 1970-01-01 的 RFC 日期）
```

## 4. 管理端行为

管理端严格解析包含新字段的完整响应结构；字段缺失、多余、类型错误、跨企业或血缘不一致时清空页面数据并显示安全错误。会议审计表使用服务端 `policyPlanName` 作为名称，并固定显示 `effectivePlanCode / policyVersion` 以便追溯；不得再调用 `planLabel(effectivePlanCode)` 为历史记录赋名。

不新增前端路由、权限或候选开关。现有页面仍要求企业列表、成员列表和 `board:operation:audit` 三项既有权限。

## 5. 实施切片与验收

1. RED：Mapper 合同测试锁定精确 JOIN、排序、501、无 head/权益/`FOR UPDATE`/`${}`。
2. RED：服务测试证明策略 v1 的操作在当前 head 为 v2 时仍返回 v1，并覆盖缺失、重复、跨租户、摘要/版本/套餐漂移和第 501 行非法血缘的失败关闭。
3. GREEN：实现读模型、严格投影、no-store 与 HTTP 200/401/403/500/default-off 验证。
4. GREEN：更新前端严格模型、审计页面和生产构建；补入双 MySQL 8.0.30/8.4.8 的真实历史读取验证。

完成门禁：相关 Java 单元/Mapper/HTTP 测试、前端模型测试和生产构建、双 MySQL 候选验证、总合同验证、冻结包哈希、`git diff --check` 及工作树/远端一致性均通过。任何生产发布、流量切换或外部写入仍需独立 release 许可和回执。
