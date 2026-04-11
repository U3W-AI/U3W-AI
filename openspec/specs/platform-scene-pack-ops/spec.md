# 平台侧场景包运营 - 规范

> **规范版本**：v1.3（合并版）
> **归档日期**：2026-04-11
> **来源 OpenSpec**：`add-platform-scene-pack-ops` + `add-enterprise-scene-pack-ops` + `add-user-self-service`

---

## 概述

本文档定义平台侧对 FBS 场景包、授权码、用户权益和企业组织的运营能力规范。

> **v1.0 范围（add-platform-scene-pack-ops）**：后端运营 API、BusinessService、Mapper 列表查询和权限控制，以及前端运营 Vue 页面（场景包管理、授权码管理、用户权益查询）；不包含企业侧分发。
>
> **v1.1 扩展（add-enterprise-scene-pack-ops）**：新增企业组织管理、企业场景包分发、企业成员管理能力，以及企业成员消费集成路径（hostType=ENTERPRISE）。本规范同步更新。
>
> **v1.2 扩展（add-user-self-service）**：新增用户侧自助权益中心：用户自助查看权益、激活授权码、查看可领取场景包、领取免费场景包；前端用户侧 Vue 页面。
>
> **v1.3 修订（归档后增强）**：激活校验链增强（已绑定/实时过期/场景包下架/重复码拒绝）、错误码中文化、可领取包 claimed 状态、我的权益展示场景包下架状态、授权码生成 deadline 校验、授权码管理 UI 改造（下拉框选场景包/列表显示名称）。

---

## 附录 A：共享枚举扩展

### A.1 场景包所有者类型（owner_type）

```text
WHERE fbs_scene_pack.owner_type
THEN  0 = 平台自有
      1 = 个人用户（预留）
      2 = 企业（owner_id = enterprise_id）
```

> **扩展来源**：OpenSpec #3 add-enterprise-scene-pack-ops。

### A.2 消费记录主机类型（host_type）

```text
WHERE fbs_skill_usage_record.host_type
THEN  WORKBUDDY  = 福帮手自身调用（个人授权包，走积分）
      ENTERPRISE = 企业成员调用（走企业配额，本 MVP 不涉及积分）
      （其他保留）
```

> **扩展来源**：OpenSpec #3 add-enterprise-scene-pack-ops。

---

## 一、场景包运营管理

### Requirement: 场景包运营管理

系统 SHALL 支持平台管理员对场景包进行发布、下架、编辑、详情查询和分页列表查询。

> OpenSpec #1 已提供场景包基础创建与内部查询能力；本规范补充平台运营侧管理能力。

#### Scenario: 场景包分页列表查询

```text
GIVEN 管理员调用 getScenePackPage(request)
WHEN  提交分页请求（pageNum, pageSize, status, packType, ownerType）
THEN  系统 SHALL 返回分页结果（total, rows）
AND   每条记录包含：id, packCode, packName, packType, status, pointsRuleCode, createdBy, createTime
```

#### Scenario: 场景包详情查询

```text
GIVEN 管理员调用 getScenePackDetail(id)
WHEN  场景包存在
THEN  系统 SHALL 返回场景包详情
AND   详情包含：packCode, packName, packType, ownerType, status, visibleScope, pointsRuleCode, contentSnapshot, currentVersion
```

#### Scenario: 场景包编辑

```text
GIVEN 管理员调用 updateScenePack(id, request)
AND   场景包 status IN (0, 1)
WHEN  更新 packName, pointsRuleCode, contentSnapshot, visibleScope 等字段
THEN  系统 SHALL 更新对应字段并返回成功
AND IF status = 2
THEN  系统 SHALL 返回失败（Fail-Closed：已下架不可编辑）
```

#### Scenario: 场景包发布

```text
GIVEN 管理员调用 publishScenePack(id)
AND   场景包 status = 0
WHEN  执行发布操作
THEN  系统 SHALL 更新 status = 1 并返回成功
AND IF status != 0
THEN  系统 SHALL 返回失败（Fail-Closed）
```

#### Scenario: 场景包下架

```text
GIVEN 管理员调用 unpublishScenePack(id)
AND   场景包 status = 1
WHEN  执行下架操作
THEN  系统 SHALL 更新 status = 2 并返回成功
AND IF status != 1
THEN  系统 SHALL 返回失败（Fail-Closed）
```

---

## 二、授权码运营管理

### Requirement: 授权码运营管理

系统 SHALL 支持平台管理员对授权码进行分页查询、批量生成、禁用、启用和撤销。

> `available` 是管理层开关，`status` 是使用状态；两者是独立维度。

#### Scenario: 授权码分页列表查询

```text
GIVEN 管理员调用 getAuthCodePage(request)
WHEN  提交分页请求（pageNum, pageSize, targetId, available, status, issuerType）
THEN  系统 SHALL 返回分页结果（total, rows）
AND   每条记录包含：id, authCode, targetType, targetId, targetPackCode, targetPackName, packStatus, available, status, maxActivations, activatedCount, deadline
AND   targetPackCode/targetPackName 通过 LEFT JOIN fbs_scene_pack 联查获取
AND   packStatus!=1 时前端 SHALL 显示「已下架」标签
```

#### Scenario: 批量生成授权码

```text
GIVEN 管理员调用 generateAuthCodeBatch(count, targetPackCode, maxActivations, deadline)
WHEN  count >= 1
THEN  系统 SHALL 校验场景包状态：
  - 场景包不存在 → 400 "场景包不存在"
  - 场景包已下架(status!=1) → 400 "场景包已下架，无法生成授权码"
AND   系统 SHALL 校验截止时间：
  - deadline < 当前时间 → 400 "截止时间不能早于当前时间"
AND   系统 SHALL 通过 targetPackCode 解析 targetId
AND   生成 count 个唯一授权码
AND   每个授权码默认 available = 1, status = 0, activated_count = 0
AND   返回生成结果列表
```

#### Scenario: 禁用授权码

```text
GIVEN 管理员调用 disableAuthCode(id)
AND   available = 1
WHEN  执行禁用
THEN  系统 SHALL 更新 available = 0
AND IF available = 0
THEN  系统 SHALL 返回失败（Fail-Closed）
```

#### Scenario: 启用授权码

```text
GIVEN 管理员调用 enableAuthCode(id)
AND   available = 0
AND   status IN (0, 1)
WHEN  执行启用
THEN  系统 SHALL 更新 available = 1
AND IF status IN (2, 3, 4)
THEN  系统 SHALL 返回失败（Fail-Closed：已用尽、已过期、已撤销不可启用）
```

#### Scenario: 撤销授权码

```text
GIVEN 管理员调用 revokeAuthCode(id)
AND   status != 4
WHEN  执行撤销
THEN  系统 SHALL 更新 status = 4
AND IF status = 4
THEN  系统 SHALL 返回失败（Fail-Closed：不可重复撤销）
```

---

## 三、用户权益查询

### Requirement: 用户权益查询

系统 SHALL 支持平台管理员分页查询用户已开通的场景包权益，并查看权益统计。

#### Scenario: 用户权益分页列表查询

```text
GIVEN 管理员调用 getUserPackPage(request)
WHEN  提交分页请求（pageNum, pageSize, userId, status）
THEN  系统 SHALL 返回分页结果（total, rows）
AND   每条记录包含：id, userId, packId, packName, sourceType, status, activatedAt, expiresAt
AND   通过 JOIN fbs_scene_pack 获取 packName
```

#### Scenario: 用户权益统计

```text
GIVEN 管理员调用 getUserPackStats(userId)
WHEN  查询指定用户的权益统计
THEN  系统 SHALL 返回：
  - totalCount
  - activeCount（status = 1）
  - expiredCount（status = 2）
  - revokedCount（status = 3）
```

---

## 四、权限与接口边界

### Requirement: 平台侧权限控制

系统 SHALL 复用若依权限体系，对平台运营接口施加 `@PreAuthorize` 权限控制。

#### Scenario: 场景包权限控制

```text
GIVEN 平台运营接口被调用
WHEN  调用场景包相关接口
THEN  系统 SHALL 校验以下权限之一：
  - business:fbs:scenePack:list
  - business:fbs:scenePack:query
  - business:fbs:scenePack:add
  - business:fbs:scenePack:edit
  - business:fbs:scenePack:publish
  - business:fbs:scenePack:unpublish
```

#### Scenario: 授权码权限控制

```text
GIVEN 平台运营接口被调用
WHEN  调用授权码相关接口
THEN  系统 SHALL 校验以下权限之一：
  - business:fbs:authCode:list
  - business:fbs:authCode:generate
  - business:fbs:authCode:disable
  - business:fbs:authCode:enable
  - business:fbs:authCode:revoke
```

#### Scenario: 用户权益权限控制

```text
GIVEN 平台运营接口被调用
WHEN  调用用户权益相关接口
THEN  系统 SHALL 校验以下权限之一：
  - business:fbs:userPack:list
  - business:fbs:userPack:query
```

#### Scenario: 企业管理权限控制

```text
GIVEN 平台运营接口被调用
WHEN  调用企业相关接口
THEN  系统 SHALL 校验以下权限之一：
  - business:fbs:enterprise:list
  - business:fbs:enterprise:query
  - business:fbs:enterprise:add
  - business:fbs:enterprise:edit
  - business:fbs:enterprise:disable
```

#### Scenario: 企业场景包分发权限控制

```text
GIVEN 平台运营接口被调用
WHEN  调用企业场景包分发相关接口
THEN  系统 SHALL 校验以下权限之一：
  - business:fbs:enterprisePack:list
  - business:fbs:enterprisePack:query
  - business:fbs:enterprisePack:grant
  - business:fbs:enterprisePack:revoke
```

#### Scenario: 企业成员管理权限控制

```text
GIVEN 平台运营接口被调用
WHEN  调用企业成员管理相关接口
THEN  系统 SHALL 校验以下权限之一：
  - business:fbs:enterpriseMember:list
  - business:fbs:enterpriseMember:add
  - business:fbs:enterpriseMember:remove
```

---

## 六、企业组织管理

### Requirement: 企业组织管理

系统 SHALL 支持平台管理员创建、编辑、查询和禁用企业组织。

#### Scenario: 创建企业

```text
GIVEN 平台管理员调用 createEnterprise(request)
WHEN  提交企业信息（enterpriseName, contactName, contactPhone, contactEmail, remark）
AND   企业名称不与已有企业重名
THEN  系统 SHALL 创建企业记录
AND   返回企业 ID 和编码（ENT_xxxxxx）
AND   状态为正常（status=1）
AND   初始无积分池（本阶段不涉及）
```

#### Scenario: 企业名称重复

```text
GIVEN 已存在企业名称 "悟空科技"
WHEN  管理员提交新企业请求，名称同样为 "悟空科技"
THEN  系统 SHALL 拒绝创建
AND   返回错误 "企业名称已存在"
```

#### Scenario: 查询企业详情

```text
GIVEN 企业 ID 存在
WHEN  管理员调用 getEnterpriseDetail(id)
THEN  系统 SHALL 返回企业详情
AND   包含：enterpriseName, contactName, contactPhone, contactEmail, memberCount, packCount, status, createTime
```

#### Scenario: 企业分页列表查询

```text
GIVEN 管理员调用 getEnterprisePage(request)
WHEN  提交分页参数（pageNum, pageSize）
AND   可选过滤（enterpriseName, status）
THEN  系统 SHALL 返回分页结果
AND   每条包含：id, enterpriseCode, enterpriseName, memberCount, packCount, status
```

#### Scenario: 禁用企业

```text
GIVEN 企业 ID=1 存在且 status=1
WHEN  管理员调用 disableEnterprise(id)
THEN  系统 SHALL 将企业状态改为已禁用（status=2）
AND   企业成员不可再使用企业场景包配额
AND   fbs_member_pack 记录保留（不级联修改状态）
AND   消费时通过企业状态 fail-closed（不在禁用时批量操作成员 pack 记录）
```

---

## 七、企业场景包分发

### Requirement: 企业场景包分发（平台→企业）

系统 SHALL 支持平台管理员将场景包分发（gift）给指定企业，并设定使用配额。

> **分发语义**：平台向企业分发 = gift（赠送/授权），不等同于企业购买。本 MVP 不含计费、订单和充值。
>
> **配额模型**：企业总额度 = fbs_enterprise_pack.packQuota / usedQuota（企业级）。fbs_member_pack 仅作为成员是否有资格使用该包的授权记录，不存独立配额，不参与扣减计算。

#### Scenario: 平台向企业分发场景包

```text
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

```text
GIVEN 企业 ID=1 已获得场景包 PACK_BOOK_WRITER_PRO
AND   分发记录状态 status=1（正常）
WHEN  管理员再次提交 grantPackToEnterprise(enterpriseId=1, packCode=PACK_BOOK_WRITER_PRO)
THEN  系统 SHALL 返回已有记录（幂等）
AND   不创建新记录
AND   不报错
```

#### Scenario: 重新授权（分发已撤销的场景包）

```text
GIVEN 企业 ID=1 的 PACK_BOOK_WRITER_PRO 分发记录 status=3（已撤销）
WHEN  管理员提交 grantPackToEnterprise(enterpriseId=1, packCode=PACK_BOOK_WRITER_PRO, packQuota)
THEN  系统 SHALL 更新分发记录
AND   status 流转为 1（重新授权）
AND   packQuota 覆盖更新
AND   usedQuota 归零
AND   重新批量生成成员授权记录
```

#### Scenario: 平台撤销企业场景包

```text
GIVEN 企业 ID=1 的 PACK_BOOK_WRITER_PRO 分发记录存在且 status=1
WHEN  管理员调用 revokePackFromEnterprise(enterprisePackId)
THEN  系统 SHALL 将 fbs_enterprise_pack.status 更新为 3（已撤销）
AND   将该企业所有成员在 fbs_member_pack 中对应记录更新为已撤销（status=3）
```

#### Scenario: 查询企业已获场景包列表

```text
GIVEN 企业 ID=1
WHEN  管理员调用 getEnterprisePackPage(enterpriseId, pageNum, pageSize)
THEN  系统 SHALL 返回企业已获场景包分页列表
AND   每条包含：id, packCode, packName, packQuota, usedQuota, remainQuota, status, grantTime, expiryTime
AND   remainQuota = packQuota - usedQuota
```

---

## 八、企业成员管理

### Requirement: 企业成员管理

系统 SHALL 支持平台管理员管理企业的成员（添加/移除）。

> **成员语义**：企业成员 = 企业管理员在平台上添加的企业用户。成员与普通用户的区别：成员可以使用企业场景包配额。
> **授权记录语义**：fbs_member_pack 是"成员是否有资格使用某企业包"的授权凭证，不存独立配额。配额扣减统一在 fbs_enterprise_pack 层面进行。

#### Scenario: 添加企业成员

```text
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

```text
GIVEN 用户 userId=10 已属于企业 ID=1（fbs_enterprise_member.status=1）
WHEN  管理员再次调用 addEnterpriseMember(enterpriseId=1, userId=10)
THEN  系统 SHALL 拒绝
AND   返回错误 "该用户已是企业成员"
```

#### Scenario: 移除企业成员

```text
GIVEN 企业成员 memberId=10 存在且 status=1
WHEN  管理员调用 removeEnterpriseMember(memberId)
THEN  系统 SHALL 将 fbs_enterprise_member.status 更新为 2（已移除）
AND   成员 pack 授权记录保留（fbs_member_pack.status 保持不变）
AND   成员不可再使用企业配额（消费时通过成员状态 fail-closed）
```

#### Scenario: 查询企业成员列表

```text
GIVEN 企业 ID=1
WHEN  管理员调用 getEnterpriseMemberPage(enterpriseId, pageNum, pageSize)
THEN  系统 SHALL 返回企业成员分页列表
AND   每条包含：memberId, userId, userName, role, status, joinTime
AND   包含 packCount（成员已授权的场景包数量）
```

#### Scenario: 查询成员已授权场景包

```text
GIVEN 成员 memberId=10 存在
WHEN  管理员调用 getMemberPackList(memberId)
THEN  系统 SHALL 返回成员已授权场景包列表
AND   每条包含：memberPackId, packCode, packName, status, remainQuota（实时 = fbs_enterprise_pack.packQuota - fbs_enterprise_pack.usedQuota）, grantTime, expiryTime
AND   remainQuota 实时计算，不从 fbs_member_pack 读取
```

#### Scenario: 成员配额用尽时无法使用

```text
GIVEN 成员 memberId=10 的 PACK_BOOK_WRITER_PRO 授权
AND   fbs_enterprise_pack.remainQuota = 0（配额已用尽）
WHEN  外部调用 /fbs/internal/usage/consume 且该用户为企业成员
THEN  系统 SHALL 返回失败
AND   failReason = "企业配额已用尽，请联系管理员"
AND   fbs_skill_usage_record.status = 2（失败）
AND   不扣积分
```

---

## 九、企业成员消费（集成 consume 路径）

### Requirement: 企业成员消费场景包

当调用方 hostType 为 ENTERPRISE 时，系统 SHALL 从企业配额扣减，Fail-Closed 处理。

> **配额模型**：配额扣减统一在 fbs_enterprise_pack 层面（packQuota / usedQuota）。fbs_member_pack 仅作授权凭证。
>
> **hostType 边界**：仅当 hostType=ENTERPRISE 时走企业配额路径。同一个用户如需走个人授权/个人积分，调用方应使用 hostType=WORKBUDDY（走 OpenSpec #1 路径）。两套路径互不 fallback。
>
> **消费语义**：本阶段只扣企业包配额，不涉及企业积分池（延期）。

#### Scenario: 企业成员使用场景包（配额充足）

```text
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

```text
GIVEN fbs_enterprise_pack.remainQuota = 0（配额已用尽）
WHEN  外部调用 /fbs/internal/usage/consume(hostType=ENTERPRISE, ...)
THEN  系统 SHALL 返回失败
AND   failReason = "企业配额已用尽，请联系管理员"
AND   fbs_skill_usage_record.status = 2
AND   不扣配额（已是0）
```

#### Scenario: 企业成员但企业未获该场景包

```text
GIVEN 用户 userId=10 属于企业 enterpriseId=1
AND   企业 enterpriseId=1 未获得 PACK_BOOK_WRITER_PRO（fbs_enterprise_pack 无有效记录）
WHEN  外部调用 /fbs/internal/usage/consume(hostType=ENTERPRISE, ...)
THEN  系统 SHALL 返回失败
AND   failReason = "企业未获此场景包授权"
AND   fbs_skill_usage_record.status = 2
AND   不 fallback 到个人授权路径
```

#### Scenario: 企业成员但企业已禁用

```text
GIVEN 企业 enterpriseId=1 的 fbs_enterprise.status=2（已禁用）
WHEN  外部调用 /fbs/internal/usage/consume(hostType=ENTERPRISE, ...)
THEN  系统 SHALL 返回失败
AND   failReason = "企业账户已禁用"
AND   fbs_skill_usage_record.status = 2
```

#### Scenario: 同一用户走 WORKBUDDY 路径（个人授权）

```text
GIVEN 用户 userId=10 属于企业 enterpriseId=1
AND   用户有个人 fbs_user_pack 授权（status=1，available>0）
WHEN  外部调用 /fbs/internal/usage/consume(hostType=WORKBUDDY, ...)
THEN  系统 SHALL 走 OpenSpec #1 个人授权路径
AND   扣个人积分（sys_user.points）
AND   不受企业配额限制
AND   host_type 记录为 WORKBUDDY
```

---

## 明确不在本 OpenSpec 范围

以下能力延期至后续 OpenSpec：

- 场景包版本管理（`fbs_pack_version`）
- 积分冻结 / 确认扣减 / 回滚
- 企业积分池（`fbs_enterprise.points_balance` / `fbs_enterprise_points_record`）
- fail-open 策略
- 企业中心前端页面（已实现后端 API，前端延期，见第十章）
- 付费购买场景包（等积分支付完善）
- 企业成员自助申请场景包（等企业侧完善）

---

## 十、前端运营页面（MVP）

### Requirement: 运营前端页面

系统 SHALL 提供若依风格 Vue 3 页面，供平台管理员在后台运营场景包、授权码、用户权益和企业。

#### 文件清单

| 类型 | 路径 | 说明 |
|------|------|------|
| API | `src/api/business/fbs/scenePack.js` | 场景包 CRUD + 发布/下架 | #2 |
| API | `src/api/business/fbs/authCode.js` | 授权码生成 + 启用/禁用/撤销 | #2 |
| API | `src/api/business/fbs/userPack.js` | 用户权益列表 + 统计 + 详情 | #2 |
| 页面 | `src/views/business/fbs/scenePack/index.vue` | 场景包管理（CRUD + 状态切换） | #2 |
| 页面 | `src/views/business/fbs/authCode/index.vue` | 授权码管理（批量生成 + 状态操作） | #2 |
| 页面 | `src/views/business/fbs/userPack/index.vue` | 用户权益查询（列表 + 统计卡片） | #2 |
| 页面 | `src/views/business/fbs/enterprise/index.vue` | 企业中心（延期，后续阶段） | #3 |
| 页面 | `src/views/business/fbs/enterprise/pack/index.vue` | 企业场景包管理（延期，后续阶段） | #3 |
| 页面 | `src/views/business/fbs/enterprise/member/index.vue` | 企业成员管理（延期，后续阶段） | #3 |

#### 菜单配置

| 菜单 | component | 权限标识 |
|------|-----------|----------|
| FBS运营管理（目录） | NULL | — |
| 场景包管理 | `business/fbs/scenePack/index` | `business:fbs:scenePack:list` |
| 授权码管理 | `business/fbs/authCode/index` | `business:fbs:authCode:list` |
| 用户权益查询 | `business/fbs/userPack/index` | `business:fbs:userPack:list` |

#### 场景包管理页面覆盖

| 功能 | 对应 Spec 场景 | 前端实现 |
|------|---------------|---------|
| 分页列表查询 | 场景包分页列表查询 | 搜索栏（名称/状态/类型）+ el-table + pagination |
| 详情查询 | 场景包详情查询 | 详情弹窗（el-descriptions 展示完整字段） |
| 新增 | 场景包编辑（id=null 时新增） | 新增弹窗（编码/名称/类型/可见范围/积分规则/描述） |
| 编辑 | 场景包编辑 | 编辑弹窗（同新增，编码不可修改） |
| 发布 | 场景包发布 | 操作列按钮（仅 status=0 时显示），confirm 确认 |
| 下架 | 场景包下架 | 操作列按钮（仅 status=1 时显示），confirm 确认 |
| 按钮权限 | 场景包权限控制 | v-hasPermi 绑定各操作权限标识 |

#### 授权码管理页面覆盖

| 功能 | 对应 Spec 场景 | 前端实现 |
|------|---------------|---------|
| 分页列表查询 | 授权码分页列表查询 | 搜索栏（授权码/启用状态/使用状态）+ el-table + pagination |
| 批量生成 | 批量生成授权码 | 生成弹窗（目标ID/最大激活次数/发放者类型/数量/截止时间/说明），结果弹窗展示+复制 |
| 禁用 | 禁用授权码 | 操作列按钮（仅 available=1 时显示），confirm 确认 |
| 启用 | 启用授权码 | 操作列按钮（available=0 且 status 非 2/3/4 时显示），confirm 确认 |
| 撤销 | 撤销授权码 | 操作列按钮（status 非 4 时显示），confirm 确认 |
| 按钮权限 | 授权码权限控制 | v-hasPermi 绑定各操作权限标识 |

#### 用户权益查询页面覆盖

| 功能 | 对应 Spec 场景 | 前端实现 |
|------|---------------|---------|
| 分页列表查询 | 用户权益分页列表查询 | 搜索栏（用户ID/权益状态）+ el-table + pagination |
| 权益统计 | 用户权益统计 | 统计卡片（total/active/expired/revoked）+ 统计弹窗（按用户ID查询） |
| 按钮权限 | 用户权益权限控制 | v-hasPermi 绑定权限标识 |

> **#3 前端延期**：企业中心 Vue 页面暂未实现（OpenSpec #3 阶段 7 标记 deferred），后续可单独开变更提案处理。sys_menu SQL 已预留（`V20260409__add-enterprise-scene-pack-ops__sys_menu.sql`）。

---

## 十一、用户侧自助权益中心

### Requirement: 用户侧自助权益中心

系统 SHALL 支持登录用户自助查看自己的权益、激活授权码、查看可领取场景包和领取免费场景包。

> **来源 OpenSpec**：`add-user-self-service`（#4）
>
> **路径约定**：`/my/*` 标识用户自助接口，与 `/business/*`（运营接口）区分。所有 `/my/*` 接口从 SecurityContext 获取当前登录用户 ID，禁止前端传 userId。

#### Scenario: 用户查看自己的权益列表

```text
GIVEN 用户已登录
WHEN  调用 GET /my/packs（可选 status/packId/sourceType 过滤 + 分页）
THEN  系统 SHALL 返回当前用户的权益分页列表
AND   每条记录包含：id, packId, packName, packCode（JOIN fbs_scene_pack）, packVersion, authCodeId, activatedAt, expiresAt, sourceType, sourceTypeDesc, status, statusDesc, packStatus, createTime
AND   userId 从 SecurityContext 获取，无需前端传参
AND   statusDesc SHALL 综合判断场景包状态：
  - packStatus=null → "场景包已删除"
  - packStatus!=1 且 status=1 → "场景包已下架"
  - 优先级：场景包已删除 > 场景包已下架 > 权益已过期 > 权益已撤销 > 有效
```

#### Scenario: 用户激活授权码

```text
GIVEN 用户已登录
AND   提供授权码字符串 authCode
WHEN  调用 POST /my/auth-code/activate
THEN  系统 SHALL 按以下校验链依次检查：
  1. authCode 为空 → 400 "授权码不能为空"
  2. 查授权码 → 不存在 → 400 "授权码不存在"
  3. targetType/targetId 无效 → 400
  4. available!=1 → 400 "授权码已禁用"
  5. status=4 → 400 "授权码已撤销"
  6. status=3 → 400 "授权码已过期"
  7. status=2 → 400 "授权码激活次数已用尽"
  8. status=1 且 activatedCount>0 → 400 "该授权码已被其他用户绑定"
  9. deadline < now → 400 "授权码已过期"（实时校验）
  10. 场景包不存在 → 400 "授权码关联的场景包不存在"
  11. 场景包已下架(status!=1) → 400 "场景包已下架，授权码无法激活"
  12. 用户已有该包权益 → 409 "您已拥有该场景包权益，无法使用其他授权码重复激活"
  13. 校验通过后调用 activateAuthCode（内部 FOR UPDATE 行锁 + 事务）
AND   成功返回 packId/packName/expiresAt
AND   写入审计字段（operator/requestId/operateTime）
AND   所有错误消息 SHALL 为中文（非英文错误码）
```

#### Scenario: 用户查看可领取场景包

```text
GIVEN 用户已登录
WHEN  调用 GET /my/scene-packs（可选 keyword 搜索 + 分页）
THEN  系统 SHALL 返回满足以下全部条件的场景包列表：
  - owner_type = 1（平台包）
  - status = 1（已发布）
  - visible_scope = 'ALL'（所有人可见）
  - points_rule_code IS NULL（免费）
AND   已领取的场景包 SHALL 仍然显示，但标记 claimed=true
AND   前端 SHALL 对 claimed=true 的包显示「已领取」标签并禁用领取按钮
```

#### Scenario: 用户领取免费场景包

```text
GIVEN 用户已登录
AND   提供场景包 ID packId
WHEN  调用 POST /my/scene-pack/claim
THEN  系统 SHALL 执行 fail-closed 校验链（PACK_NOT_FOUND / PACK_OFFLINE / PACK_PRIVATE / PACK_NOT_FREE）
AND   执行幂等检查（用户已有 → 返回 ALREADY_CLAIMED）
AND   校验通过后创建 fbs_user_pack 记录（sourceType=1, status=1）
AND   UNIQUE(user_id, pack_id) 唯一约束兜底并发
AND   写入审计字段
```

#### Scenario: 未登录用户访问自助接口

```text
GIVEN SecurityContext 无用户信息
WHEN  调用任意 /my/* 接口
THEN  系统 SHALL 返回 SESSION_REQUIRED（401）
```

### 用户侧错误码字典

| 错误码 | HTTP Status | 中文消息 | 说明 |
|--------|-------------|----------|------|
| AUTH_CODE_INVALID | 400 | 授权码无效（含具体原因） | 授权码为空/不存在/类型无效 |
| AUTH_CODE_DISABLED | 400 | 授权码已禁用 | available!=1 |
| AUTH_CODE_REVOKED | 400 | 授权码已撤销 | status=4 |
| AUTH_CODE_EXPIRED | 400 | 授权码已过期 | status=3 或 deadline 实时过期 |
| AUTH_CODE_EXHAUSTED | 400 | 授权码激活次数已用尽 | status=2 |
| AUTH_CODE_BOUND | 400 | 该授权码已被其他用户绑定 | status=1 且 activatedCount>0 |
| PACK_OFFLINE_FOR_CODE | 400 | 场景包已下架，授权码无法激活 | 场景包 status!=1 |
| DUPLICATE_ACTIVATE | 409 | 您已拥有该场景包权益，无法使用其他授权码重复激活 | 同场景包不同授权码 |
| ALREADY_CLAIMED | 200 | 已领取（幂等） | |
| PACK_NOT_FOUND | 404 | 场景包不存在 | |
| PACK_OFFLINE | 400 | 场景包已下架 | |
| PACK_NOT_FREE | 400 | 场景包非免费 | |
| PACK_PRIVATE | 400 | 场景包不可领取 | |
| SESSION_REQUIRED | 401 | 未登录 | |

> **v1.3 修订**：所有错误消息 SHALL 为中文（非英文错误码）。新增 AUTH_CODE_BOUND / PACK_OFFLINE_FOR_CODE / DUPLICATE_ACTIVATE。

### 用户侧权限控制

| 权限标识 | 说明 |
|----------|------|
| my:fbs:myPacks:list | 我的权益列表 |
| my:fbs:authCode:activate | 授权码激活 |
| my:fbs:claimablePacks:list | 可领取场景包 |
| my:fbs:scenePack:claim | 场景包领取 |

### 用户侧前端页面

| 类型 | 路径 | 说明 |
|------|------|------|
| API | `src/api/business/fbs/mySelfService.js` | 4 个自助接口 |
| 页面 | `src/views/business/fbs/myPacks/index.vue` | 我的权益（搜索+表格+分页） |
| 页面 | `src/views/business/fbs/activateAuthCode/index.vue` | 激活授权码（表单+结果+历史） |
| 页面 | `src/views/business/fbs/claimablePacks/index.vue` | 可领取场景包（卡片+领取） |
| SQL | `sql/V20260411__add-user-self-service__sys_menu.sql` | 菜单（parent_id=0 一级目录） |

> **菜单结构**：我的权益中心（一级目录，parent_id=0）→ 我的权益 / 激活授权码 / 领取场景包

