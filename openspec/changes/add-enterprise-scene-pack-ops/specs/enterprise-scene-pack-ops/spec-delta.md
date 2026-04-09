# OpenSpec #3 规范差异：企业侧场景包与成员分发

> **Change ID**: `add-enterprise-scene-pack-ops`
> **规范版本**: v1.2（修订后）
> **日期**: 2026-04-09

---

## ADDED Requirements

### Requirement: 企业组织管理

系统 SHALL 支持平台管理员创建、编辑、查询和禁用企业组织。

#### Scenario: 创建企业

```
GIVEN 平台管理员调用 createEnterprise(request)
WHEN  提交企业信息（enterpriseName, contactName, contactPhone, contactEmail, remark）
AND   企业名称不与已有企业重名
THEN  系统 SHALL 创建企业记录
AND   返回企业 ID 和编码（ENT_xxxxxx）
AND   状态为正常（status=1）
AND   初始无积分池（本阶段不涉及）
```

#### Scenario: 企业名称重复

```
GIVEN 已存在企业名称 "悟空科技"
WHEN  管理员提交新企业请求，名称同样为 "悟空科技"
THEN  系统 SHALL 拒绝创建
AND   返回错误 "企业名称已存在"
```

#### Scenario: 查询企业详情

```
GIVEN 企业 ID 存在
WHEN  管理员调用 getEnterpriseDetail(id)
THEN  系统 SHALL 返回企业详情
AND   包含：enterpriseName, contactName, contactPhone, contactEmail, memberCount, packCount, status, createTime
```

#### Scenario: 企业分页列表查询

```
GIVEN 管理员调用 getEnterprisePage(request)
WHEN  提交分页参数（pageNum, pageSize）
AND   可选过滤（enterpriseName, status）
THEN  系统 SHALL 返回分页结果
AND   每条包含：id, enterpriseCode, enterpriseName, memberCount, packCount, status
```

#### Scenario: 禁用企业

```
GIVEN 企业 ID=1 存在且 status=1
WHEN  管理员调用 disableEnterprise(id)
THEN  系统 SHALL 将企业状态改为已禁用（status=2）
AND   企业成员不可再使用企业场景包配额
AND   fbs_member_pack 记录保留（不级联修改状态）
AND   消费时通过企业状态 fail-closed（不在禁用时批量操作成员 pack 记录）
```

---

### Requirement: 企业场景包分发（平台→企业）

系统 SHALL 支持平台管理员将场景包分发（gift）给指定企业，并设定使用配额。

> **分发语义**：平台向企业分发 = gift（赠送/授权），不等同于企业购买。本 MVP 不含计费、订单和充值。
>
> **配额模型**：企业总额度 = fbs_enterprise_pack.packQuota / usedQuota（企业级）。fbs_member_pack 仅作为成员是否有资格使用该包的授权记录，不存独立配额，不参与扣减计算。

#### Scenario: 平台向企业分发场景包

```
GIVEN 企业 ID=1 存在且 status=1
AND   场景包 packCode 存在且 status=1
AND   该企业尚未获得此场景包（fbs_enterprise_pack 无 status=1 记录）
WHEN  平台管理员调用 grantPackToEnterprise(enterpriseId, packCode, packQuota)
THEN  系统 SHALL 创建 fbs_enterprise_pack 记录
AND   packQuota = 请求中的配额数量（企业级总配额）
AND   usedQuota = 0
AND   状态为已授权（status=1）
AND   为企业当前所有成员在 fbs_member_pack 中批量创建授权记录（授权记录，无配额字段）
AND   返回分发记录 ID
```

#### Scenario: 幂等分发（企业已拥有该场景包）

```
GIVEN 企业 ID=1 已获得场景包 PACK_BOOK_WRITER_PRO
AND   分发记录状态 status=1（正常）
WHEN  管理员再次提交 grantPackToEnterprise(enterpriseId=1, packCode=PACK_BOOK_WRITER_PRO)
THEN  系统 SHALL 返回已有记录（幂等）
AND   不创建新记录
AND   不报错
```

#### Scenario: 重新授权（分发已撤销的场景包）

```
GIVEN 企业 ID=1 的 PACK_BOOK_WRITER_PRO 分发记录 status=3（已撤销）
WHEN  管理员提交 grantPackToEnterprise(enterpriseId=1, packCode=PACK_BOOK_WRITER_PRO, packQuota)
THEN  系统 SHALL 更新分发记录
AND   status 流转为 1（重新授权）
AND   packQuota 覆盖更新
AND   usedQuota 归零
AND   重新批量生成成员授权记录
```

#### Scenario: 平台撤销企业场景包

```
GIVEN 企业 ID=1 的 PACK_BOOK_WRITER_PRO 分发记录存在且 status=1
WHEN  管理员调用 revokePackFromEnterprise(enterprisePackId)
THEN  系统 SHALL 将 fbs_enterprise_pack.status 更新为 3（已撤销）
AND   将该企业所有成员在 fbs_member_pack 中对应记录更新为已撤销（status=3）
```

#### Scenario: 查询企业已获场景包列表

```
GIVEN 企业 ID=1
WHEN  管理员调用 getEnterprisePackPage(enterpriseId, pageNum, pageSize)
THEN  系统 SHALL 返回企业已获场景包分页列表
AND   每条包含：id, packCode, packName, packQuota, usedQuota, remainQuota, status, grantTime, expiryTime
AND   remainQuota = packQuota - usedQuota
```

---

### Requirement: 企业成员管理

系统 SHALL 支持平台管理员管理企业的成员（添加/移除）。

> **成员语义**：企业成员 = 企业管理员在平台上添加的企业用户。成员与普通用户的区别：成员可以使用企业场景包配额。
> **授权记录语义**：fbs_member_pack 是"成员是否有资格使用某企业包"的授权凭证，不存独立配额。配额扣减统一在 fbs_enterprise_pack 层面进行。

#### Scenario: 添加企业成员

```
GIVEN 企业 ID=1 存在且 status=1
AND   用户 userId=10 存在且当前不属于该企业（fbs_enterprise_member 无 status=1 记录）
WHEN  管理员调用 addEnterpriseMember(enterpriseId, userId, role)
THEN  系统 SHALL 创建 fbs_enterprise_member 记录
AND   状态为正常（status=1）
AND   自动继承企业当前所有已授权场景包
AND   fbs_member_pack 批量写入（授权记录，无配额字段）
AND   返回成员记录 ID
```

#### Scenario: 重复添加同一成员

```
GIVEN 用户 userId=10 已属于企业 ID=1（fbs_enterprise_member.status=1）
WHEN  管理员再次调用 addEnterpriseMember(enterpriseId=1, userId=10)
THEN  系统 SHALL 拒绝
AND   返回错误 "该用户已是企业成员"
```

#### Scenario: 移除企业成员

```
GIVEN 企业成员 memberId=10 存在且 status=1
WHEN  管理员调用 removeEnterpriseMember(memberId)
THEN  系统 SHALL 将 fbs_enterprise_member.status 更新为 2（已移除）
AND   成员 pack 授权记录保留（fbs_member_pack.status 保持不变）
AND   成员不可再使用企业配额（消费时通过成员状态 fail-closed）
```

#### Scenario: 查询企业成员列表

```
GIVEN 企业 ID=1
WHEN  管理员调用 getEnterpriseMemberPage(enterpriseId, pageNum, pageSize)
THEN  系统 SHALL 返回企业成员分页列表
AND   每条包含：memberId, userId, userName, role, status, joinTime
AND   包含 packCount（成员已授权的场景包数量）
```

---

### Requirement: 成员场景包授权查询

系统 SHALL 支持成员场景包授权的查询。fbs_member_pack 作为授权凭证，不存配额，查询时实时计算 remainQuota。

#### Scenario: 查询成员已授权场景包

```
GIVEN 成员 memberId=10 存在
WHEN  管理员调用 getMemberPackList(memberId)
THEN  系统 SHALL 返回成员已授权场景包列表
AND   每条包含：memberPackId, packCode, packName, status, remainQuota（实时 = fbs_enterprise_pack.packQuota - fbs_enterprise_pack.usedQuota）, grantTime, expiryTime
AND   状态含义：1=正常, 2=已用尽, 3=已过期, 4=已撤销
AND   remainQuota 实时计算，不从 fbs_member_pack 读取
```

#### Scenario: 成员配额用尽时无法使用

```
GIVEN 成员 memberId=10 的 PACK_BOOK_WRITER_PRO 授权
AND   fbs_enterprise_pack.remainQuota = 0（配额已用尽）
WHEN  外部调用 /fbs/internal/usage/consume 且该用户为企业成员
THEN  系统 SHALL 返回失败
AND   failReason = "企业配额已用尽，请联系管理员"
AND   fbs_skill_usage_record.status = 2（失败）
AND   不扣积分
```

---

### Requirement: 企业成员消费场景包（集成 consume 路径）

当调用方 hostType 为 ENTERPRISE 时，系统 SHALL 从企业配额扣减，Fail-Closed 处理。

> **配额模型**：配额扣减统一在 fbs_enterprise_pack 层面（packQuota / usedQuota）。fbs_member_pack 仅作授权凭证。
>
> **hostType 边界**：仅当 hostType=ENTERPRISE 时走企业配额路径。同一个用户如需走个人授权/个人积分，调用方应使用 hostType=WORKBUDDY（走 OpenSpec #1 路径）。两套路径互不 fallback。
>
> **消费语义**：本阶段只扣企业包配额，不涉及企业积分池（延期）。

#### Scenario: 企业成员使用场景包（配额充足）

```
GIVEN 用户 userId=10 属于企业 enterpriseId=1（fbs_enterprise_member.status=1）
AND   企业已获场景包 PACK_BOOK_WRITER_PRO，fbs_enterprise_pack.packQuota=100，usedQuota=10，remainQuota=90
AND   fbs_member_pack 存在且 status=1（成员有授权）
WHEN  外部调用 /fbs/internal/usage/consume(hostType=ENTERPRISE, ...)
THEN  系统 SHALL 更新 fbs_enterprise_pack.usedQuota（+1）
AND   写入 fbs_skill_usage_record（host_type=ENTERPRISE）
AND   返回 success=true, remainPoints=remainQuota（90）
AND   不操作用户个人积分（sys_user.points 不变）
AND   不走 wx_points_rule 规则
AND   不修改 fbs_member_pack
```

#### Scenario: 企业成员使用场景包（企业配额不足）

```
GIVEN fbs_enterprise_pack.remainQuota = 0（配额已用尽）
WHEN  外部调用 /fbs/internal/usage/consume(hostType=ENTERPRISE, ...)
THEN  系统 SHALL 返回失败
AND   failReason = "企业配额已用尽，请联系管理员"
AND   fbs_skill_usage_record.status = 2
AND   不扣配额（已是0）
```

#### Scenario: 企业成员但企业未获该场景包

```
GIVEN 用户 userId=10 属于企业 enterpriseId=1
AND   企业 enterpriseId=1 未获得 PACK_BOOK_WRITER_PRO（fbs_enterprise_pack 无有效记录）
WHEN  外部调用 /fbs/internal/usage/consume(hostType=ENTERPRISE, ...)
THEN  系统 SHALL 返回失败
AND   failReason = "企业未获此场景包授权"
AND   fbs_skill_usage_record.status = 2
AND   不 fallback 到个人授权路径
```

#### Scenario: 企业成员但企业已禁用

```
GIVEN 企业 enterpriseId=1 的 fbs_enterprise.status=2（已禁用）
WHEN  外部调用 /fbs/internal/usage/consume(hostType=ENTERPRISE, ...)
THEN  系统 SHALL 返回失败
AND   failReason = "企业账户已禁用"
AND   fbs_skill_usage_record.status = 2
```

#### Scenario: 同一用户走 WORKBUDDY 路径（个人授权）

```
GIVEN 用户 userId=10 属于企业 enterpriseId=1
AND   用户有个人 fbs_user_pack 授权（status=1，available>0）
WHEN  外部调用 /fbs/internal/usage/consume(hostType=WORKBUDDY, ...)
THEN  系统 SHALL 走 OpenSpec #1 个人授权路径
AND   扣个人积分（sys_user.points）
AND   不受企业配额限制
AND   host_type 记录为 WORKBUDDY
```

---

## MODIFIED Requirements

### Requirement: 场景包所有者类型扩展

#### 修改前

```
WHERE fbs_scene_pack.owner_type
THEN  0 = 平台自有
      1 = 个人用户（预留）
```

#### 修改后

```
WHERE fbs_scene_pack.owner_type
THEN  0 = 平台自有
      1 = 个人用户（预留）
      2 = 企业（owner_id = enterprise_id）
```

---

### Requirement: 消费记录主机类型扩展

#### 修改前

```
WHERE fbs_skill_usage_record.host_type
THEN  WORKBUDDY = 福帮手自身调用
      （其他）
```

#### 修改后

```
WHERE fbs_skill_usage_record.host_type
THEN  WORKBUDDY  = 福帮手自身调用（个人授权包，走积分）
      ENTERPRISE = 企业成员调用（走企业配额，本 MVP 不涉及积分）
      （其他保留）
```
