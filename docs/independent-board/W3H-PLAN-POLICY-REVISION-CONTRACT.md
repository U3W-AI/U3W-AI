# 独董会 W3h 套餐策略修订与不可变回执合同

状态：`IMPLEMENTATION_CANDIDATE`

## 1. 目标与边界

W3h 为 `admin.u3w.com` 增加独董会套餐名称、会议额度、议题额度、席位额度和秘书能力的受控修订，并让 `me.u3w.com`、权益授予和会议配额读取只消费已提交的当前策略版本。

本波次始终满足：

- 已提审的独董会 `26.7.20` 包只读，SHA-256 必须保持 `d2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd`；
- 生产 HTTP、菜单和数据库迁移均不激活；API2 归因仍为次要观察线；
- 不增加租户委派管理员，不修改 OAuth、Connector、Webhook、Watch/FBSir Hub 或积分账本权威性；
- `fbs_product_plan` 继续是不可改写的产品/套餐身份与基线策略真源，W3h 不原地修改其两条种子记录。

## 2. 数据模型

新增 `public_init_039`：

1. `fbs_plan_policy_revision_receipt`：只增不改的完整策略修订回执。保存产品、套餐、版本、前序回执、操作者、幂等键摘要、命令摘要、前后策略摘要、提交后的类型化策略字段、证据等级和服务端时间。
2. `fbs_plan_policy_head`：每个 `productCode + planCode` 唯一一行，只保存当前已提交回执指针和策略版本。

迁移从 `fbs_product_plan` 的两条已验证基线生成确定性的版本 1 回执，并原子建立两个 head。回执表使用 `BEFORE UPDATE` 与 `BEFORE DELETE` 触发器拒绝修改和删除。head 只能通过服务端 CAS 从版本 `N` 前进到 `N+1`。

当前读必须执行：

```text
fbs_product_plan identity
  JOIN fbs_plan_policy_head current pointer
  JOIN fbs_plan_policy_revision_receipt committed typed snapshot
```

任何缺行、重复、跨套餐指针、版本不一致、摘要格式错误、未知套餐、非 ACTIVE 基线或超过两条的目录都失败关闭，不允许回退到部分目录或未提交修订。

## 3. 可变与不可变字段

服务端固定：

- `productCode=FBSIR_INDEPENDENT_BOARD`
- `planCode` 仅 `BOARD_FREE` 或 `BOARD_VIP`
- `vip`、`connectorRequired`、基线 `status` 来自 `fbs_product_plan`，本接口不可改

管理员可以提交完整替换值：

- `planName`
- `dailyMeetingLimit`
- `agendaLimit`
- `seatLimit`（VIP 可为 `null` 表示无套餐上限；FREE 必须为正整数）
- `secretaryEnabled`

边界：名称去首尾空白后为 1..128 个非控制字符；三个数值额度均为正数且不超过 10000。完整候选目录必须满足 VIP 的会议、议题和有限席位额度不低于 FREE；若 FREE 开启秘书能力，VIP 也必须开启。无实质变化的命令拒绝生成回执。

## 4. 写入接口

候选开关：

```text
fbsir.independent-board.plan-policy-candidate.enabled=false
```

默认关闭时控制器和专属异常处理器均不存在，已认证请求得到真实 404。

接口：

```text
POST /business/independent-board/plan-policy-revisions
```

授权：全局 `admin` 角色且具有 `board:plan:revise`。

请求必须且只能包含：

```json
{
  "planCode": "BOARD_VIP",
  "expectedVersion": 1,
  "planName": "独董会 VIP 版",
  "dailyMeetingLimit": 5,
  "agendaLimit": 30,
  "seatLimit": null,
  "secretaryEnabled": true,
  "idempotencyKey": "01J9PLANREVISIONEXAMPLE"
}
```

未知、重复、缺失、类型错误或尾随 JSON 均返回稳定 400。幂等键为 16..128 位受限 ASCII；同一操作者和产品范围内，同键同命令返回原已提交回执，同键异命令返回稳定 409。`expectedVersion` 是新意图的 CAS 前置条件；精确重放不因后续版本前进而失效。

事务顺序固定：

1. 按 `planCode` 顺序锁定两个 head 和对应当前回执；
2. 校验完整当前目录；
3. 对操作者范围内的幂等键执行锁定当前读并处理精确重放；
4. 校验 `expectedVersion`、非空变更和跨套餐单调不变量；
5. 插入版本 `N+1` 的不可变回执；
6. 以旧 head 回执和版本为条件执行 CAS；
7. 回读新当前策略并在同一事务提交。

回执写入或 head CAS 任一失败时全部回滚。不得自动把陈旧命令改写为新版本重试。

## 5. 读取接口与安全投影

既有 `GET /business/independent-board/plans` 保持字段集合与授权不变，但 `version` 和 `updatedAt` 来自当前 head 指向的已提交回执。

新增：

```text
GET /business/independent-board/plan-policy-receipts
```

授权：全局 `admin` 角色且具有 `board:plan:audit`。固定读取 101 条，对外最多 100 条并返回 `truncated`。

安全回执字段仅为：

```text
receiptId, planCode, policyVersion, previousReceiptId, actorUserId,
planName, dailyMeetingLimit, agendaLimit, seatLimit, secretaryEnabled,
previousPolicyDigest, policyDigest, evidenceLevel, createdAt
```

不返回数据库主键、幂等键、命令摘要或内部锁字段。所有响应使用 `Cache-Control: no-store`。

## 6. 管理后台

复用现有“权益治理”页的套餐目录，不新增公共路由。只有同时满足：

- `VITE_FBSIR_BOARD_PLAN_POLICY_CANDIDATE === 'true'`
- `board:plan:revise` 或 `board:plan:audit` 对应权限

才显示修订动作或回执区。表单从服务器当前版本完整填充；保存期间禁止重复提交；409 后保留原意图但必须重新读取，绝不自动套用新版本。浏览器侧为操作者使用单一 pending 槽与独占 Web Lock，歧义结果只能用同一幂等键和同一负载精确重放。

权限菜单迁移为显式 opt-in、默认禁用、零角色绑定，仅增加 `board:plan:revise` 和 `board:plan:audit` 两个功能身份。

## 7. 测试与完成门禁

实现前 RED 必须覆盖严格 DTO、权限、当前读缺失/漂移、陈旧版本、同键异负载、同键并发重放、跨套餐单调约束、无变化拒绝、回执失败回滚和 head CAS 失败回滚。

完成需要：

- Java 单元、Mapper、Spring 代理和真实 HTTP/JWT 测试通过；
- MySQL Community 8.0.30 与 8.4.8 首次应用、完成态重放、严格元数据、不可变触发器、负向漂移和并发矩阵通过；
- me/admin/权益授予/会议预留均只消费 committed head；
- 前端严格模型、权限、默认关闭、歧义重放、版本冲突和生产构建通过；
- 本地浏览器验证默认关闭 404、启用后的修订/回执/陈旧版本及控制台零错误；
- 总合同、数据库 manifest、工作树清洁、本地/远端一致且冻结包哈希不变。

本地候选通过不证明生产迁移、真实域名可用或自然业务使用。
