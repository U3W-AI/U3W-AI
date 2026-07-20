# 独董会 W3b 权益生命周期合同

状态：`IMPLEMENTATION_LOCKED`
适用范围：W3b 受控权益撤销、不可变权益回执审计和撤销状态回读。
明确不在本波次：计划/额度策略编辑、积分账本、OAuth/Connector、Webhook、Watch/FBSir Hub、租户委派管理员、真实域名部署。

## 1. 授权边界

- 所有 W3b 管理接口仍只允许若依全局系统管理员使用，必须同时满足 `admin` 角色与对应细权限。
- 撤销权限为 `board:entitlement:revoke`；权益回执只读权限为 `board:entitlement:audit`。
- 菜单迁移不得写入 `sys_role_menu`，也不得据此宣称已经支持租户管理员。
- 域名、菜单可见性或按钮可见性不能替代服务端授权。

## 2. 受控撤销

接口：`POST /business/independent-board/entitlements/revoke`

请求只包含：

```json
{
  "tenantId": 1001,
  "memberId": 2001,
  "userId": 3001,
  "expectedVersion": 2
}
```

规则：

1. 按 `tenantId + memberId + FBSIR_INDEPENDENT_BOARD` 对权益行执行锁定当前读；随后核对持久化行的租户、成员、用户、产品、版本和状态。
2. 撤销不依赖企业或成员仍为活跃状态。成员离开或企业禁用后，全局系统管理员仍必须能够关闭遗留权益；这不放宽对目标权益行的精确作用域校验。
3. 只有 `ACTIVE` 权益可以撤销。权益不存在、目标不匹配、版本变化或已经撤销均以冲突失败，不进行自动重试。
4. 成功撤销把 `status` 写为 `REVOKED`；`validUntil` 取“不早于服务端当前时间且严格晚于 `validFrom`”的最小安全时间，以满足数据库约束并兼容同毫秒操作或时钟回拨。版本加一并更新 `updatedAt`；历史租户、成员、用户、计划和创建时间保持不变。API 采用秒级时间格式时，前端允许撤销记录的开始和截止时间显示为同一秒，但持久化值仍须严格有序。
5. 状态写入与 `ENTITLEMENT_REVOKED / ACTION_COMPLETED` 回执必须在同一事务中提交；回执写入失败时，权益更新必须回滚。
6. 本波次不接收“原因”字段。现有回执表没有可安全明文回读的原因列，不能把仅进入摘要、无法审计展示的信息伪装为原因审计。

## 3. 状态机

```text
ACTIVE --受控撤销且 CAS 成功--> REVOKED
ACTIVE --到期时间已过---------> EXPIRED（派生状态，不改持久化状态）
REVOKED -----------------------> 仅可由后续显式重新授予合同处理
```

- 持久化 `status=REVOKED` 必须映射为 `activationState=REVOKED`，不能显示为 `EXPIRED`。
- `REVOKED` 的有效计划固定回退为 `BOARD_FREE`，不得保留 VIP 生效能力。
- `PENDING_CONNECTOR` 仍表示已授予但 Connector 尚未完成权威绑定，不能显示为 VIP 已激活。
- 本波次不实现“恢复撤销”按钮；如后续允许重新授予，必须以新的版本和新的不可变回执完成。

## 4. 不可变权益回执审计

接口：`GET /business/independent-board/entitlement-receipts?tenantId={tenantId}`

每条对外 DTO 只允许：

```text
receiptId, tenantId, actorUserId, targetMemberId,
action, evidenceLevel, createdAt
```

响应固定为：

```json
{
  "records": [],
  "limit": 500,
  "truncated": false
}
```

- 查询固定取 501 条，用第 501 条判定 `truncated`，对外最多返回 500 条。
- 禁止暴露数据库主键 `id` 和 `payloadDigest`。
- 当前表没有 `productCode`，因此本页只能如实标注为“独董会当前专用权益回执表的租户回读”，不能伪造不存在的产品过滤证据。
- 页面只读，无删除、修改、补写或“重放回执”动作；回执只证明所标记的 evidence level，不跨级推断业务结果。

## 5. 前端交互

- “撤销”按钮仅在权益为 `ACTIVE` 且当前账号具备撤销权限时出现。
- 撤销前显示目标企业、成员、用户、计划和当前版本的二次确认；提交期间禁用重复操作。
- 409 表示版本或作用域已经变化：提示用户刷新后的真实状态，不得用新版本自动重试原撤销意图。
- 回执页复用现有企业选择上下文，不要求用户手工填写租户编号；超过 500 条时显示明确截断提示。
- 严格模型解析拒绝多余或缺失字段，避免把后端内部对象直接传播到 UI。

## 6. 数据库与迁移

- W3b 菜单迁移版本为 `public_init_031`，内部迁移标识为 `20260720_independent_board_entitlement_lifecycle_menu_v1`。
- “权益治理”下新增功能权限“权益撤销”；“独董会管理”下新增只读页面“权益回执”。
- 迁移必须持有命名锁，顺序固定为写入、迁移回执、完成态审计、提交、释放命名锁；首次应用、完成态重跑和标识碰撞均需验证。
- W3a 的四个菜单身份仍须精确存在；W3b 新增身份不能使旧门禁因“全局总数增加”而误报失败。

## 7. 完成门禁

W3b 只有在以下证据全部来自同一候选树时，才能标记为本地源码与集成验证通过：

1. 后端服务、控制器、DTO、Mapper 单元和 HTTP/JWT 权限/输出字段测试通过；
2. MySQL 8.4 InnoDB、REPEATABLE-READ 下的撤销 CAS、并发和回执失败回滚通过；
3. me/admin 的 `REVOKED` 状态与免费计划回退通过；
4. 前端严格字段、确认、防重复提交、409 恢复和只读回执测试通过；
5. 菜单迁移首次应用、完成态重跑、碰撞失败关闭和组件映射通过；
6. 全量后端、前端生产构建、文档一致性和 `git diff --check` 通过；
7. 已提交审核的 26.7.20 专家包保持原提交且工作树清洁。

本地通过不等于生产迁移、真实域名可用或业务确认。生产与外部系统状态必须由后续同绑定回执单独证明。
