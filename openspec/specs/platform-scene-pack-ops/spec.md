# 平台侧场景包运营 - 规范

> **规范版本**：v1.8（合并版）
> **归档日期**：2026-04-18
> **来源 OpenSpec**：`add-platform-scene-pack-ops` + `add-enterprise-scene-pack-ops` + `add-user-self-service` + `add-skill-api-gateway` + `add-wecom-cli-integration` + `add-smartsheet-write-mvp` + `add-smartsheet-schema-mgmt` + `wecom-commercial-hub-field-sync`

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
>
> **v1.4 扩展（add-skill-api-gateway）**：新增 Skill API 网关（6 个 /fbs/skill-api/ 接口）+ API Key 管理（6 个 /fbs/business/api-key/ 接口），API Key 认证替代 JWT，一次性消费 + 两阶段消费互斥模式，MVP 内存级限流。
>
> **v1.5 扩展（add-wecom-cli-integration）**：新增企微智能表格集成基础层——Java 调用 wecom-cli 读取 Sheet 数据，WecomCliService（命令执行封装）+ WecomSyncService（同步读取服务）+ fbs_wecom_sync_log 日志表，2 个 REST API（read + check）。集成测试全链路跑通，修复 Windows ProcessBuilder JSON 参数引号转义 Bug。
>
> **v1.6 扩展（add-smartsheet-write-mvp）**：新增企微智能表格写入能力——WecomWriteService（分片写入服务）+ POST /fbs/business/wecom/sync/write API + 20KB 分片策略 + sync_type=WRITE 日志类型。单元测试 10/10 + 集成测试 10/10 全绿。
>
> **v1.7 扩展（add-smartsheet-schema-mgmt）**：新增企微智能表格结构管理能力——WecomSchemaService（子表+字段 CRUD）+ WecomWriteService 扩展（updateRecords/deleteRecords）+ 10 个新 REST API 端点 + 11 个 DTO + 字段类型白名单 + 150字段上限 + 100记录上限。单元测试 269/269 全绿。
>
> **v1.8 扩展（wecom-commercial-hub-field-sync）**：commercial_hub 完整字段同步——`CommercialHubSyncContext` DTO + `syncCommercialHub` 签名扩展 + 14 个空字段补全写入 + user_id 文本格式修复 + consumeEnterprise 同步调用补漏 + 关联查询降级策略 + cli-proxy.js 方案修复 Windows ProcessBuilder 嵌套 JSON 引号问题。单元测试 19/19 + 集成测试验证 24 字段全写入。

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

---

## 十二、Skill API 网关

> **来源 OpenSpec**：`add-skill-api-gateway`（#5）
>
> **路径约定**：`/fbs/skill-api/**` 标识 Skill API 接口，使用 API Key 认证（`X-FBS-API-Key` Header），不使用 JWT。
>
> **⚠️ 安全边界声明**：API Key + body.userId 模型存在天然越权风险——持有有效 API Key 即可伪造任意 userId。MVP 接受此风险，前提：（1）仅部署在受控 WorkBuddy 宿主环境；（2）不面向公网第三方开放；（3）生产环境强制 HTTPS。后续迭代加固：API Key 绑定 packCode 白名单、宿主签名 userId（HMAC-SHA256）、IP 白名单。

### Requirement: Skill API 网关

系统 SHALL 提供一组面向 FBS-BookWriter Skill 的 REST API，使用 API Key 认证，让 Skill 脚本可以校验权益、扣减积分、查询场景包规则。

#### Scenario: 权益校验（Skill 调用）

```text
GIVEN Skill 脚本携带有效 API Key 调用 POST /fbs/skill-api/rights/check
WHEN  提交 userId + packCode + authCode（可选）+ hostType
THEN  系统 SHALL 校验 API Key 有效性
AND   复用 RightsCheckService.comprehensiveCheck 进行权益校验
AND   返回 { pass, failReason, packId, pointsRuleCode, pointsAmount }
```

#### Scenario: Skill 消费（一次性扣减+记录）

```text
GIVEN Skill 脚本携带有效 API Key 调用 POST /fbs/skill-api/usage/consume
WHEN  提交 userId + packCode + skillCode + usageRecordId(String) + hostType
THEN  系统 SHALL 复用 SkillConsumeService.consume（不传 pointsAmount）
AND   返回 ConsumeResult（success/failReason/pointsAmount/remainPoints）
```

#### Scenario: 使用记录两阶段模式

> **⚠️ 与 consume 互斥**：start/end 和 consume 是两种独立的消费模式，同一个 usageRecordId 只能走一种。

```text
GIVEN Skill 脚本携带有效 API Key
WHEN  调用 POST /fbs/skill-api/usage/start
THEN  系统 SHALL 按幂等语义处理（status=0 返回已有 / status=1/2 返回 409 / 不存在则创建）

WHEN  调用 PUT /fbs/skill-api/usage/end/{usageRecordId}
THEN  系统 SHALL 按幂等语义更新状态（不存在→404 / status=0→更新 / status=1/2→409）
```

#### Scenario: 场景包规则查询

```text
GIVEN Skill 脚本携带有效 API Key 调用 POST /fbs/skill-api/scene-pack/query
WHEN  提交 packCode
THEN  系统 SHALL 返回 contentSnapshot（原始 JSON 字符串，不做二次结构化转换）
```

#### Scenario: 用户信息查询

```text
GIVEN Skill 脚本携带有效 API Key 调用 POST /fbs/skill-api/user/info
WHEN  提交 userId
THEN  系统 SHALL 返回 userId + pointsBalance + activatedPacks（不返回 T0-T3）
```

#### Scenario: API Key 认证失败

```text
GIVEN 请求路径匹配 /fbs/skill-api/**
WHEN  API Key 缺失/不存在 → 401 SKILL_API_KEY_INVALID
AND   API Key 已禁用 → 403 SKILL_API_KEY_DISABLED
AND   速率超限 → 429 SKILL_API_RATE_LIMITED
```

---

## 十三、API Key 管理

### Requirement: API Key 管理

系统 SHALL 支持 API Key 的生成、查询、启用/禁用、删除，供运营人员管理 Skill 访问凭证。

#### Scenario: 生成 API Key

```text
GIVEN 管理员调用生成 API Key 接口
WHEN  提交 name + packCode（可选）+ rateLimitPerMin（默认 60）
THEN  系统 SHALL 生成唯一 API Key（前缀 fbs_ + 32位随机串）
AND   存储到 fbs_api_key 表（status=1 启用）
AND   明文存储（HMAC 签名校验需原文，不迁移到 SHA-256 hash）
AND   返回完整 Key（仅创建时可见，后续查询脱敏：前8位+****）
```

#### Scenario: 禁用/启用/删除 API Key

```text
GIVEN 管理员调用禁用/启用/删除接口
WHEN  提交 apiKeyId
THEN  系统 SHALL 更新或删除 fbs_api_key 记录
AND   禁用后使用该 Key 的请求返回 403
AND   删除后使用该 Key 的请求返回 401
```

### API Key 权限控制

| 权限标识 | 说明 |
|----------|------|
| business:fbs:apikey:add | 生成 API Key |
| business:fbs:apikey:list | 查询 API Key 列表 |
| business:fbs:apikey:query | 查询 API Key 详情 |
| business:fbs:apikey:edit | 启用/禁用 API Key |
| business:fbs:apikey:remove | 删除 API Key |

---

## 明确不在本 OpenSpec 范围（#5 更新）

以下能力延期至后续 OpenSpec：

- API Key 前端管理页面（P2-DEFERRED）
- API Key sys_menu SQL（P2-DEFERRED）
- fbs-rights-client.mjs 单元测试（P2-DEFERRED）
- API Key SHA-256 hash 存储（已取消——#15 HMAC 签名方案依赖明文存储）
- API Key 绑定 packCode 白名单
- 宿主签名 userId（HMAC-SHA256）
- IP 白名单

---

## 十四、企微智能表格集成（MVP 基础层）

> **来源 OpenSpec**：`add-wecom-cli-integration`（#6）
>
> **范围**：最简可跑通 — Java 调用 wecom-cli.exe 读取企微智能表格 Sheet 数据，通过手动触发 REST API 获取结果，落日志表。
>
> **后续**：#7（智能表格写入）、#8（场景包规则管理）。

### Requirement: WecomCliService — wecom-cli 命令执行服务

WHEN 系统需要调用 wecom-cli.exe 执行企微操作,
系统 SHALL 提供 `WecomCliService` 接口封装 ProcessBuilder 调用。

#### Scenario: 正常执行命令

```text
GIVEN wecom-cli.exe 路径已配置且文件存在
AND   wecom-cli 已完成企业微信认证
WHEN  调用 WecomCliService.execute("doc", "smartsheet_get_records", '{"docId":"xxx","sheetName":"meta"}')
THEN  系统 shell=false 启动进程并传入 category/method/params 三个参数
AND   在配置的超时时间（默认 30s）内返回 WecomCliResult(success=true, rawOutput=非空, durationMs>0)
AND   不写入日志（日志由上层 WecomSyncService 统一负责）
```

#### Scenario: wecom-cli 文件不存在

```text
GIVEN 配置的 wecom-cli.exe 路径不存在
WHEN  调用 execute 或 isAvailable 方法
THEN  返回 WecomCliResult(success=false, errorCode="CLI_NOT_FOUND")
AND   不尝试启动任何进程
```

#### Scenario: 认证未完成

```text
GIVEN wecom-cli.exe 可执行
WHEN  调用 execute 方法且 wecom-cli 返回认证错误
THEN  返回 WecomCliResult(success=false, errorCode="AUTH_REQUIRED", errorMessage包含"扫码"或"登录"或"auth")
AND   不触发重试
```

#### Scenario: 进程超时

```text
GIVEN wecom-cli.exe 可执行且已认证
WHEN  单次调用超过配置的超时时间（默认 30s）
THEN  强制销毁进程（DestroyForcibly）
AND   返回 WecomCliResult(success=false, errorCode="EXEC_TIMEOUT")
```

#### Scenario: 网络错误简单重试

```text
GIVEN wecom-cli.exe 可执行且已认证
WHEN  调用 execute 方法且返回 NET_ 错误码（NET_TIMEOUT/NET_CONNECTION_FAILED）
THEN  等待 500ms 后重试 1 次
AND   重试仍失败则返回最后一次错误结果
```

---

### Requirement: WecomSyncService — 最简同步读取服务

WHEN 需要从企微智能表格读取数据并记录,
系统 SHALL 通过 `WecomSyncService` 提供按 Sheet 读取并落日志的能力。

#### Scenario: 读取 meta Sheet（场景包规则）

```text
GIVEN WecomCliService 可用
WHEN  调用 WecomSyncService.readSheet("meta") 或不传 sheetName 默认读 meta
THEN  构造 smartsheet_get_records 命令参数（含 docId + sheetName）
AND   调用 WecomCliService.execute("doc", "smartsheet_get_records", params)
AND   解析双层 JSON 结构（outer.content[0].text → inner data array）
AND   将原始记录列表存入 fbs_wecom_sync_log 的 snapshot_json 字段
AND   返回 WecomSyncReadResponse(sheetName="meta", recordCount=N, success=true)
```

#### Scenario: 读取 commercial_hub Sheet

```text
GIVEN WecomCliService 可用
WHEN  调用 WecomSyncService.readSheet("commercial_hub")
THEN  同上流程读取 commercial_hub Sheet
AND   存入 fbs_wecom_sync_log（sheet_name="commercial_hub"）
AND   不做 record_type 分类拆分，整体作为快照存储
```

#### Scenario: 读取失败

```text
GIVEN WecomCliService 不可用或网络异常
WHEN  调用 readSheet 方法
THEN  返回 WecomSyncReadResponse(success=false, errorcode=具体错误码)
AND   fbs_wecom_sync_log 中 status=FAILED, snapshot_json=NULL
AND   不抛未捕获异常到 Controller 层
```

---

### Requirement: WecomSyncController — 手动触发读取 API

WHEN 需要手动触发从企微智能表格读取数据,
系统 SHALL 提供 RESTful API 端点。

#### Scenario: 手动触发读取

```text
GIVEN 用户已登录且有权限
WHEN  POST /fbs/business/wecom/sync/read（body 可选 { "sheetName": "meta" }）
THEN  同步执行读取（非异步，等待结果后返回）
AND   成功时返回 200 + { success, sheetName, recordCount, durationMs, syncLogId, snapshot[] }
AND   失败时返回对应错误码（400/500 视错误类型）
```

#### Scenario: 连通性检测

```text
GIVEN wecom-cli 路径已配置
WHEN  POST /fbs/business/wecom/sync/check
THEN  检查 wecom-cli.exe 文件是否存在（File.exists()）
AND   不验证认证态（认证态在 read 时自然暴露，MVP 不做额外检测）
AND   返回 { cliAvailable: bool, cliPath: string, lastError: string? }
AND   不写入 sync_log（轻量文件检查）
```

---

### 数据模型：fbs_wecom_sync_log

```sql
CREATE TABLE fbs_wecom_sync_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_type       VARCHAR(16)  NOT NULL DEFAULT 'READ' COMMENT 'READ/WRITE',
    sheet_name      VARCHAR(64)  NOT NULL COMMENT 'Sheet 名称',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'SUCCESS/FAILED/TIMEOUT/PARSE_ERROR',
    record_count    INT          DEFAULT 0 COMMENT '读取到的记录数',
    error_code      VARCHAR(32)  DEFAULT NULL COMMENT '错误码',
    error_message   TEXT         DEFAULT NULL,
    snapshot_json   LONGTEXT     DEFAULT NULL COMMENT '原始数据JSON快照',
    duration_ms     BIGINT       DEFAULT 0,
    created_by      BIGINT       DEFAULT NULL,
    created_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_sheet_name (sheet_name),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微同步日志表';
```

---

### 附录 J：企微 CLI 错误码

| 错误码 | 含义 | 重试 |
|--------|------|------|
| `CLI_NOT_FOUND` | wecom-cli.exe 不存在 | 不重试 |
| `EXEC_TIMEOUT` | 进程超时（默认 30s） | 不重试 |
| `AUTH_REQUIRED` | 未扫码认证或认证过期 | 不重试 |
| `NET_TIMEOUT` | 网络超时 | 重试 ×1（等 500ms） |
| `NET_CONNECTION_FAILED` | 连接失败 | 重试 ×1（等 500ms） |
| `PARSE_ERROR` | JSON 解析失败 | 不重试 |

> 完整错误码集（含 RATE_LIMITED / BIZ_PAYLOAD_TOO_LARGE 等）在 #7 阶段补充。

---

## 十五、企微智能表格写入（MVP）

> **来源 OpenSpec**：`add-smartsheet-write-mvp`（#7）
>
> **范围**：最简可跑通 — Java 调用 wecom-cli 写入记录到企微智能表格 + 20KB 分片 + 手动触发写入 API。
>
> **依赖**：OpenSpec #6（WecomCliService + WecomSyncService）。

### Requirement: WecomWriteService — 企微智能表格写入服务

WHEN 需要向企微智能表格写入记录,
系统 SHALL 提供 `WecomWriteService` 封装 `smartsheet_add_records` 命令调用。

#### Scenario: 写入少量记录（无需分片）

```text
GIVEN WecomCliService 可用
AND   记录总 payload ≤ 20KB
AND   所有单条记录 payload ≤ 20KB
WHEN  调用 WecomWriteService.writeRecords("meta", records)
THEN  序列化 records 为 JSON（CLI 格式：[{values: {...}}]）
AND   调用 WecomCliService.execute("doc", "smartsheet_add_records", {docid, sheet_id, records})
AND   写入 fbs_wecom_sync_log（sync_type=WRITE, status=SUCCESS, record_count=N）
AND   返回 WecomSyncWriteResponse(success=true, writtenRecords=N, shardCount=1)
```

#### Scenario: 写入大量记录（需要分片）

```text
GIVEN WecomCliService 可用
AND   所有单条记录 payload ≤ 20KB
AND   记录总 payload > 20KB
WHEN  调用 WecomWriteService.writeRecords("meta", records)
THEN  按 20KB 上限将 records 拆分为多个分片
AND   串行逐片调用 WecomCliService.execute
AND   汇总所有分片结果
AND   写入 fbs_wecom_sync_log（sync_type=WRITE, status=SUCCESS, record_count=总写入数）
AND   返回 WecomSyncWriteResponse(success=true, writtenRecords=总写入数, shardCount=M)
```

#### Scenario: 单条记录超过 20KB

```text
GIVEN WecomCliService 可用
AND   单条记录序列化后 > 20KB
WHEN  调用 WecomWriteService.writeRecords(sheetName, records)
THEN  不尝试写入（前置校验阶段即拒绝）
AND   不写入 sync_log
AND   返回 WecomSyncWriteResponse(success=false, errorCode="BIZ_PAYLOAD_TOO_LARGE")
```

#### Scenario: 空记录列表

```text
GIVEN 传入的 records 为空列表
WHEN  调用 WecomWriteService.writeRecords(sheetName, records)
THEN  直接返回 no-op
AND   不写入 sync_log
AND   返回 WecomSyncWriteResponse(success=true, writtenRecords=0, shardCount=0)
```

#### Scenario: records 为 null

```text
GIVEN 传入的 records 为 null
WHEN  调用 WecomWriteService.writeRecords(sheetName, records)
THEN  不调用 WecomCliService
AND   不写入 sync_log
AND   返回 WecomSyncWriteResponse(success=false, errorCode="INVALID_REQUEST", errorMessage="records 不能为 null")
```

#### Scenario: 白名单外 Sheet

```text
GIVEN sheetName 不在允许列表中
WHEN  调用 WecomWriteService.writeRecords(sheetName, records)
THEN  不调用 WecomCliService
AND   不写入 sync_log
AND   返回 WecomSyncWriteResponse(success=false, errorCode="INVALID_SHEET")
```

#### Scenario: 写入失败（CLI 错误）

```text
GIVEN WecomCliService 返回失败结果
WHEN  调用 WecomWriteService.writeRecords(sheetName, records)
THEN  写入 fbs_wecom_sync_log（sync_type=WRITE, status=FAILED, error_code=具体错误码）
AND   返回 WecomSyncWriteResponse(success=false, errorCode=具体错误码)
```

#### Scenario: 部分分片写入失败

```text
GIVEN 多分片写入中部分分片成功、部分失败
WHEN  串行写入过程中某分片返回失败
THEN  停止后续分片写入
AND   写入 fbs_wecom_sync_log（sync_type=WRITE, status=FAILED, error_code=PARTIAL_WRITE_FAILED, record_count=已成功写入数）
AND   返回 WecomSyncWriteResponse(success=false, errorCode="PARTIAL_WRITE_FAILED", writtenRecords=已成功写入数)
AND   不回滚已成功写入的分片
```

---

### Requirement: WecomSyncController — 写入 API 端点

WHEN 需要手动触发向企微智能表格写入数据,
系统 SHALL 提供 RESTful API 端点。

#### Scenario: 手动触发写入

```text
GIVEN 用户已登录且有 business:fbs:wecom:sync:write 权限
WHEN  POST /fbs/business/wecom/sync/write
      body: { sheetName: "meta", records: [{ "values": { "字段标题": value } }] }
THEN  同步执行写入（非异步，等待结果后返回）
AND   成功时返回 200 + { success, sheetName, totalRecords, writtenRecords, shardCount, durationMs, syncLogId }
AND   失败时返回 200 + { success:false, errorCode, errorMessage }
```

#### Scenario: 未登录写入

```text
GIVEN 请求未携带有效 JWT
WHEN  POST /fbs/business/wecom/sync/write
THEN  返回 401
```

#### Scenario: sheetName 为空时默认 meta

```text
GIVEN 用户已登录
AND   sheetName 为 null 或空字符串
WHEN  POST /fbs/business/wecom/sync/write
      body: { sheetName: "", records: [...] }
THEN  使用默认 Sheet "meta" 继续处理
```

---

### Requirement: fbs_wecom_sync_log — 支持写入类型

WHEN sync_type 为 WRITE 时,
系统 SHALL 记录写入操作的日志。

#### Scenario: 写入日志记录

```text
GIVEN 通过校验后的实际写入操作完成
WHEN  WecomWriteService 写入 sync_log
THEN  sync_type = "WRITE"
AND   record_count = 成功写入的记录数
AND   status = SUCCESS 或 FAILED
AND   不记录 key_type（smartsheet_add_records 无此参数）
```

#### Scenario: 不写日志的拒绝场景

```text
GIVEN sheetName 不在白名单 或 records 为空列表 或 存在单条记录 > 20KB
WHEN  WecomWriteService.writeRecords
THEN  不写入 sync_log
```

---

### 附录 K：写入相关错误码

| 错误码 | 含义 | 重试 | 备注 |
|--------|------|------|------|
| `BIZ_PAYLOAD_TOO_LARGE` | 单条记录超过 payload 上限（默认 20KB） | 不重试 | 前置校验，不调用 CLI |
| `PARTIAL_WRITE_FAILED` | 部分分片写入失败 | 不重试 | 已写入不回滚 |

> 注：#6 已有错误码（CLI_NOT_FOUND / EXEC_TIMEOUT / AUTH_REQUIRED / NET_* / PARSE_ERROR）继续适用写入场景。

---

## 明确不在本 OpenSpec 范围（#8 更新）

以下能力延期至后续 OpenSpec：

- ~~smartsheet_update_records（更新已有记录）~~ ✅ #8 已实现
- ~~smartsheet_delete_records（删除记录）~~ ✅ #8 已实现
- ~~字段管理（add/update/delete fields）~~ ✅ #8 已实现
- ~~子表管理（add/update/delete sheet）~~ ✅ #8 已实现
- 冲突检测 / 双向 diff
- SyncEngine 同步引擎
- 定时同步 / Quartz
- 异步任务 / syncTaskId / 任务队列
- 分布式锁 / Redis 锁
- 限频策略（Rate Limiting）
- 分类写入（按 record_type 分类到不同 Sheet）
- 前端页面 / sys_menu SQL
- 回写触发器（业务操作自动触发回写）

---

## 十六、企微智能表格结构管理

> **来源 OpenSpec**：`add-smartsheet-schema-mgmt`（#8）
>
> **范围**：最简可跑通 — 运营可通过 API 查看/编辑企微智能表格结构（子表+字段）及更新/删除记录。
>
> **依赖**：OpenSpec #6（WecomCliService）+ #7（WecomWriteService）。
>
> **新增文件**：1 接口 + 1 实现 + 11 DTO + 1 测试 | **修改文件**：1 Controller + 1 接口 + 1 实现 + 1 测试

### Requirement: WecomSchemaService — 子表管理

WHEN 需要管理企微智能表格中的子表,
系统 SHALL 提供 `WecomSchemaService` 封装子表 CRUD 的 CLI 命令调用。

#### Scenario: 查询子表列表

```text
GIVEN 管理员已登录且有 schema:read 权限
AND   提供有效 docid
WHEN  调用 GET /fbs/business/wecom/schema/sheets?docid={docid}
THEN  调用 wecom-cli doc smartsheet_get_sheet
AND   返回子表列表 [{sheetId, title, rowCount}]
AND   返回 HTTP 200
```

#### Scenario: 添加子表

```text
GIVEN 管理员已登录且有 schema:write 权限
AND   提供有效 docid 和 title
WHEN  调用 POST /fbs/business/wecom/schema/sheet
THEN  调用 wecom-cli doc smartsheet_add_sheet
AND   返回新子表的 sheetId 和 title
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200
```

#### Scenario: 更新子表标题

```text
GIVEN 管理员已登录且有 schema:write 权限
AND   提供有效 docid, sheetId, title
WHEN  调用 PUT /fbs/business/wecom/schema/sheet
THEN  调用 wecom-cli doc smartsheet_update_sheet
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200
```

#### Scenario: 删除子表

```text
GIVEN 管理员已登录且有 schema:delete 权限
AND   提供有效 docid 和 sheetId
WHEN  调用 DELETE /fbs/business/wecom/schema/sheet?docid={docid}&sheetId={sheetId}
THEN  调用 wecom-cli doc smartsheet_delete_sheet
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200 + true
```

#### Scenario: 删除子表幂等

```text
GIVEN 子表已被删除
WHEN  再次调用 DELETE /fbs/business/wecom/schema/sheet
THEN  返回成功（CLI 行为：幂等）
AND   返回 HTTP 200 + true
```

---

### Requirement: WecomSchemaService — 字段管理

WHEN 需要管理企微智能表格中的字段,
系统 SHALL 提供 `WecomSchemaService` 封装字段 CRUD 的 CLI 命令调用。

#### Scenario: 查询字段列表

```text
GIVEN 管理员已登录且有 schema:read 权限
AND   提供有效 docid 和 sheetId
WHEN  调用 GET /fbs/business/wecom/schema/fields?docid={docid}&sheetId={sheetId}
THEN  调用 wecom-cli doc smartsheet_get_fields
AND   返回字段列表 [{fieldId, fieldTitle, fieldType}]
AND   返回 HTTP 200
```

#### Scenario: 添加字段

```text
GIVEN 管理员已登录且有 schema:write 权限
AND   提供有效 docid, sheetId, fields: [{fieldTitle, fieldType}]
AND   所有 fieldType 在白名单内（text/number/number自动编号/date/datetime/checkbox/phone/email/url/attachment/member/department/lookup/formula/progress/grade）
AND   现有字段数 + 新增字段数 ≤ 150
WHEN  调用 POST /fbs/business/wecom/schema/fields
THEN  调用 wecom-cli doc smartsheet_add_fields
AND   返回新字段列表 [{fieldId, fieldTitle, fieldType}]
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200
```

#### Scenario: 添加字段 — 无效类型

```text
GIVEN 请求包含无效 fieldType（如 "invalid_type"）
WHEN  调用 POST /fbs/business/wecom/schema/fields
THEN  不调用 CLI（前置校验拒绝）
AND   返回 HTTP 200 + {fields: []}
```

#### Scenario: 添加字段 — 数量超限

```text
GIVEN 子表现有 N 个字段
AND   请求添加 M 个新字段
AND   N + M > 150
WHEN  调用 POST /fbs/business/wecom/schema/fields
THEN  不调用 CLI（前置校验拒绝）
AND   返回 HTTP 200 + {fields: []}
```

#### Scenario: 更新字段

```text
GIVEN 管理员已登录且有 schema:write 权限
AND   提供有效 docid, sheetId, fields: [{fieldId, fieldTitle?, fieldType?}]
WHEN  调用 PUT /fbs/business/wecom/schema/fields
THEN  调用 wecom-cli doc smartsheet_update_fields
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200
```

#### Scenario: 删除字段

```text
GIVEN 管理员已登录且有 schema:delete 权限
AND   提供有效 docid, sheetId, fieldIds: ["f1", "f2"]
WHEN  调用 DELETE /fbs/business/wecom/schema/fields
THEN  调用 wecom-cli doc smartsheet_delete_fields
AND   写入 fbs_wecom_sync_log（sync_type=SCHEMA）
AND   返回 HTTP 200 + deletedCount
```

---

### Requirement: WecomWriteService — 记录更新

WHEN 需要更新企微智能表格中的已有记录,
系统 SHALL 扩展 `WecomWriteService` 提供 `updateRecords` 方法。

#### Scenario: 更新记录成功

```text
GIVEN 管理员已登录且有 records:write 权限
AND   提供有效 sheetId, records: [{recordId, values}]
AND   records 数量 ≤ 100
AND   sheetName 在白名单内（meta/commercial_hub）
WHEN  调用 PUT /fbs/business/wecom/records
THEN  调用 wecom-cli doc smartsheet_update_records
AND   写入 fbs_wecom_sync_log（sync_type=RECORD）
AND   返回 HTTP 200 + {success: true, writtenRecords: N}
```

#### Scenario: 记录数量超限

```text
GIVEN records 数量 > 100
WHEN  调用 PUT /fbs/business/wecom/records
THEN  不调用 CLI（前置校验拒绝）
AND   返回 HTTP 200 + {success: false, errorCode: "RECORD_LIMIT_EXCEEDED"}
```

---

### Requirement: WecomWriteService — 记录删除

WHEN 需要删除企微智能表格中的记录,
系统 SHALL 扩展 `WecomWriteService` 提供 `deleteRecords` 方法。

#### Scenario: 删除记录成功

```text
GIVEN 管理员已登录且有 records:delete 权限
AND   提供有效 sheetId, recordIds
AND   recordIds 数量 ≤ 100
WHEN  调用 DELETE /fbs/business/wecom/records
THEN  调用 wecom-cli doc smartsheet_delete_records
AND   写入 fbs_wecom_sync_log（sync_type=RECORD）
AND   返回 HTTP 200 + {success: true, writtenRecords: N}
```

#### Scenario: recordIds 为 null

```text
GIVEN recordIds 为 null
WHEN  调用 DELETE /fbs/business/wecom/records
THEN  不调用 CLI
AND   返回 HTTP 200 + {success: false, errorCode: "INVALID_REQUEST"}
```

---

### 数据模型：sync_type 扩展

`fbs_wecom_sync_log` 表的 `sync_type` 字段新增支持值：

| sync_type | 说明 | 来源 |
|-----------|------|------|
| READ | 读取操作 | #6 |
| WRITE | 写入操作（addRecords） | #7 |
| SCHEMA | 结构变更操作（addSheet/updateSheet/deleteSheet/addFields/updateFields/deleteFields） | #8 |
| RECORD | 记录变更操作（updateRecords/deleteRecords） | #8 |

---

### 附录 L：Schema 管理相关错误码

| 错误码 | 含义 | 重试 | 备注 |
|--------|------|------|------|
| `RECORD_LIMIT_EXCEEDED` | 单次操作记录数量超过限制（最多100条） | 不重试 | 前置校验，不调用 CLI |
| `INVALID_FIELD_TYPE` | 无效的字段类型 | 不重试 | 前置校验，不调用 CLI |
| `FIELD_LIMIT_EXCEEDED` | 字段数量超过限制（单表最多150个） | 不重试 | 前置校验，不调用 CLI |

> 注：CLI 返回的 DOC_NOT_FOUND / SHEET_NOT_FOUND / FIELD_NOT_FOUND / RECORD_NOT_FOUND 在 Java 层统一映射为 `CLI_ERROR`，具体错误信息在 errorMessage 字段中返回。

---

### #8 权限标识

| 权限标识 | 说明 |
|----------|------|
| `business:fbs:wecom:schema:read` | 查询子表/字段信息 |
| `business:fbs:wecom:schema:write` | 添加/更新子表/字段 |
| `business:fbs:wecom:schema:delete` | 删除子表/字段 |
| `business:fbs:wecom:records:write` | 更新记录 |
| `business:fbs:wecom:records:delete` | 删除记录 |

---

### #8 明确不在范围内

以下能力延期至后续 OpenSpec 或前后端联调阶段：

- create_doc 接口（需手动在企微创建 docid）
- sys_menu SQL 权限入库（前后端联调时处理）
- 前端管理页面（MVP 仅提供后端 API）

---

## 十七、用户侧 API Key 管理

> **来源 OpenSpec**：`frontend-api-key-management`（#11）
>
> **范围**：用户自助创建/管理 API Key（用于 WorkBuddy Skill 调用后端 API）。
>
> **依赖**：OpenSpec #5（Skill API 网关 + API Key 认证）。

### Requirement: 用户侧 API Key 管理

系统 SHALL 支持用户通过前端界面自助创建和管理 API Key，用于 WorkBuddy Skill 调用后端 API。

#### Scenario: 用户创建 API Key

```text
GIVEN 用户已登录
AND   用户无该名称的 API Key（或允许重复，视业务规则）
WHEN  调用 POST /fbs/business/my/apikey/create
      body: { name: "我的 WorkBuddy 密钥" }
THEN  系统 SHALL 生成唯一 API Key（前缀 fbs_ + 32位随机串）
AND   自动绑定当前用户（user_id = SecurityContext.getUserId()）
AND   状态默认为启用（status=1）
AND   写入 fbs_api_key 表
AND   返回完整密钥（仅此一次，后续查询只返回脱敏值）
```

#### Scenario: 创建时返回完整密钥（关键）

```text
GIVEN 用户创建 API Key 成功
WHEN  Controller 返回响应
THEN  apiKey 字段 SHALL 为完整密钥（如：fbs_abc123xyz789def456ghi012jkl345）
AND   前端 SHALL 显示提示："请立即复制保存，关闭后无法再次查看完整密钥！"
AND   后续查询该 Key 时只返回脱敏值（前8位+****）
```

#### Scenario: 用户查询自己的 API Key 列表

```text
GIVEN 用户已登录
WHEN  调用 GET /fbs/business/my/apikey/list
THEN  系统 SHALL 返回当前用户的 API Key 列表
AND   apiKey 字段 SHALL 脱敏显示（如：fbs_abc12****）
AND   userId 从 SecurityContext 获取，无需前端传参
AND   只返回当前用户的 Key（数据隔离）
```

#### Scenario: 用户禁用/启用 API Key

```text
GIVEN 用户已登录
AND   API Key ID 存在且属于当前用户
WHEN  调用 PUT /fbs/business/my/apikey/toggle/{id}
      params: { status: 0 或 1 }
THEN  系统 SHALL 更新该 Key 的状态
AND   校验归属（只能操作自己的 Key）
AND   无权操作时抛出异常
```

#### Scenario: 用户删除 API Key

```text
GIVEN 用户已登录
AND   API Key ID 存在且属于当前用户
WHEN  调用 DELETE /fbs/business/my/apikey/{id}
THEN  系统 SHALL 删除该 Key
AND   校验归属（只能删除自己的 Key）
AND   无权删除时抛出异常
```

---

### 数据模型：fbs_api_key 扩展

```sql
ALTER TABLE fbs_api_key
ADD COLUMN user_id BIGINT COMMENT '绑定用户ID',
ADD COLUMN last_used_at DATETIME COMMENT '最后使用时间';

CREATE INDEX idx_user_id ON fbs_api_key(user_id);
```

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT | 绑定用户ID（用户自助创建时自动填充） |
| last_used_at | DATETIME | 最后使用时间（Skill 调用时更新） |

---

### 用户侧权限控制

| 权限标识 | 说明 |
|----------|------|
| `my:apikey:list` | 查询我的 API Key 列表 |
| `my:apikey:create` | 创建 API Key |
| `my:apikey:toggle` | 禁用/启用 API Key |
| `my:apikey:delete` | 删除 API Key |

---

### 用户侧前端页面

| 类型 | 路径 | 说明 |
|------|------|------|
| API | `src/api/business/fbs/myApikey.js` | 用户侧 API Key 接口 |
| 页面 | `src/views/business/fbs/myApikey/index.vue` | 我的 API Key 列表页 |
| SQL | `sql/V20260417__11-user-api-key-menu.sql` | 菜单配置 |

---

### 菜单结构

```
我的权益中心（一级目录）
├── 我的权益
├── 激活授权码
├── 领取场景包
└── 我的 API Keys（新增，order_num=31）
    ├── 创建 Key（按钮权限）
    ├── 禁用 Key（按钮权限）
    └── 删除 Key（按钮权限）
```

---

### 数据脱敏规则

| 场景 | 密钥显示 |
|------|---------|
| 创建时返回 | 完整密钥（仅此一次） |
| 列表查询 | 前8位 + ****（如：`fbs_abc12****`） |
| 数据库存储 | 明文（MVP） |

> **安全建议**：用户需在创建后立即复制保存，关闭对话框后无法再次查看完整密钥。

---

### #11 明确不在范围内

以下能力延期至后续 OpenSpec：

- ~~API Key SHA-256 hash 存储~~（已取消——#15 HMAC 签名方案依赖明文存储，不再迁移到 hash）
- 一个用户只能有一个 API Key 的限制
- API Key 过期时间
- API Key 使用统计图表

---

## 十八、commercial_hub 完整字段同步

> **扩展来源**：OpenSpec #13 wecom-commercial-hub-field-sync。

### Requirement: commercial_hub 完整字段同步

WHEN 积分消费成功并触发 `syncCommercialHub()`,
系统 SHALL 将 commercial_hub 子表的全部 24 个字段写入企微智能表格。

#### Scenario: 个人用户消费完整同步

GIVEN 用户消费场景包
AND hostType="WORKBUDDY"
AND usageRecordId 已生成
WHEN 调用 syncCommercialHub(CommercialHubSyncContext)
THEN 企微智能表格新增一条记录，包含全部 24 字段
AND record_id 为 UUID（文本格式）
AND user_id 为文本格式 `"1001"`（非裸 Long）
AND source 为 "SKILL_API"
AND enterprise_only 为 "false"
AND trial_allowed 为 "false"
AND redeem_target 与 genre 相同
AND request_id = order_id = usageRecordId
AND payload_json 包含完整消费参数 JSON
AND created_at = updated_at 为当前时间

#### Scenario: 企业用户消费完整同步

GIVEN 企业用户消费企业场景包
AND hostType="ENTERPRISE"
AND 用户关联企业 enterpriseCode="ENT_abc123"
AND authCode 非空
WHEN 调用 syncCommercialHub(CommercialHubSyncContext)
THEN commercial_hub 新增一条记录
AND corp_id 为 "ENT_abc123"（从 FbsEnterpriseMember → FbsEnterprise 关联查）
AND enterprise_only 为 "true"
AND source 为 "ENTERPRISE"
AND code_type 为授权码类型值
AND pointsAmount 为 0（企业路径不扣个人积分）

#### Scenario: 关联查询失败降级

GIVEN 查询企业编码时 FbsEnterpriseMemberMapper 返回空列表
WHEN 调用 syncCommercialHub(CommercialHubSyncContext)
THEN corp_id 字段写入空字符串
AND 同步不中断，其他字段正常写入

---

### Requirement: CommercialHubSyncContext 上下文传递

WHEN SkillConsumeServiceImpl 执行积分消费,
系统 SHALL 构建包含完整消费上下文的 CommercialHubSyncContext 并传递给同步服务。

#### Scenario: 个人消费路径传参

GIVEN 用户通过 WORKBUDDY 路径消费
WHEN consume() 方法成功扣减积分
THEN 构建 CommercialHubSyncContext 包含 userId, packCode, pointsAmount, remainPoints, hostType="WORKBUDDY", usageRecordId, packId, authCode, pointsRuleCode, packType
AND 调用 syncCommercialHub(context)

#### Scenario: 企业消费路径传参

GIVEN 用户通过 ENTERPRISE 路径消费
WHEN consumeEnterprise() 方法成功执行
THEN 构建 CommercialHubSyncContext 包含 hostType="ENTERPRISE", pointsAmount=0
AND 调用 syncCommercialHub(context)

---

### Requirement: 企业消费路径同步（补漏）

WHEN 企业成员通过企业配额路径消费场景包,
系统 SHALL 同步消费记录到企微智能表格 commercial_hub。

#### Scenario: 企业消费同步

GIVEN 企业成员成功消费场景包（consumeEnterprise 返回成功）
WHEN 同步调用执行
THEN commercial_hub 新增一条记录
AND source 为 "ENTERPRISE"
AND enterprise_only 为 "true"（packType=2）
AND pointsAmount 为 0

#### Scenario: 企业同步失败不影响配额扣减

GIVEN 企业消费成功但同步到企微失败
WHEN syncCommercialHub 抛出异常
THEN 配额扣减结果不受影响
AND 记录 warn 日志

---

### Requirement: user_id 字段格式修复

WHEN 构建 commercial_hub 记录,
系统 SHALL 将 user_id 字段以文本格式写入智能表格。

#### Scenario: user_id 文本格式写入

GIVEN userId=1001
WHEN 构建 commercial_hub 记录
THEN user_id 字段值为 `[{"type":"text","text":"1001"}]`
AND 不直接传 Long 类型

---

### Requirement: syncCommercialHub 接口签名

系统 SHALL 支持 CommercialHubSyncContext 上下文参数，旧 4 参数签名保留并标记 `@Deprecated`。

#### Scenario: 旧签名兼容

GIVEN 调用旧 4 参数签名 syncCommercialHub(userId, packCode, pointsAmount, remainPoints)
WHEN 内部转调新签名
THEN 构建 CommercialHubSyncContext，hostType 默认 "WORKBUDDY"，扩展字段为默认值
AND 核心字段正常写入，扩展字段写入默认值

---

### commercial_hub 全量字段映射表

| # | 字段 | 智能表格类型 | 写入格式 | 来源 |
|---|------|:---:|---------|------|
| 1 | record_id | TEXT | `[{"type":"text","text":"UUID"}]` | UUID 生成 |
| 2 | record_type | TEXT | `[{"type":"text","text":"SKILL_USAGE"}]` | 固定值 |
| 3 | corp_id | TEXT | `[{"type":"text","text":enterpriseCode}]` | FbsEnterpriseMember→FbsEnterprise 关联查，失败降级空字符串 |
| 4 | user_id | TEXT | `[{"type":"text","text":"1001"}]` | ⚠️ 文本包装，非裸 Long |
| 5 | genre | TEXT | `[{"type":"text","text":packCode}]` | ctx.packCode |
| 6 | event | TEXT | `[{"type":"text","text":"CONSUME"}]` | 固定值 |
| 7 | delta | NUMBER | 直接传 int | ctx.pointsAmount |
| 8 | balance_after | NUMBER | 直接传 int | ctx.remainPoints |
| 9 | credits_required | NUMBER | 直接传 int | ctx.pointsAmount |
| 10 | code_prefix | TEXT | `[{"type":"text","text":""}]` | FbsAuthCode 无此字段，写空字符串 |
| 11 | code_hash | TEXT | `[{"type":"text","text":""}]` | FbsAuthCode 无此字段，写空字符串 |
| 12 | code_type | TEXT | `[{"type":"text","text":"1"/"2"}]` | FbsAuthCode.codeType，无授权码=空字符串 |
| 13 | redeem_target | TEXT | `[{"type":"text","text":packCode}]` | 与 genre 相同 |
| 14 | request_id | TEXT | `[{"type":"text","text":usageRecordId}]` | ctx.usageRecordId |
| 15 | order_id | TEXT | `[{"type":"text","text":usageRecordId}]` | MVP 同 request_id |
| 16 | trial_allowed | SINGLE_SELECT | `"true"/"false"` | MVP 固定 "false" |
| 17 | enterprise_only | SINGLE_SELECT | `"true"/"false"` | packType=2→"true"，其他→"false" |
| 18 | source | TEXT | `[{"type":"text","text":"SKILL_API"/"ENTERPRISE"}]` | hostType 映射 |
| 19 | operator | TEXT | `[{"type":"text","text":userId}]` | ctx.userId |
| 20 | status | TEXT | `[{"type":"text","text":"SUCCESS"}]` | 固定值 |
| 21 | risk_flag | TEXT | `[{"type":"text","text":""}]` | P3 延期 |
| 22 | payload_json | TEXT | `[{"type":"text","text":jsonString}]` | 消费请求体序列化 |
| 23 | created_at | TEXT | `[{"type":"text","text":timestamp}]` | 当前时间 |
| 24 | updated_at | TEXT | `[{"type":"text","text":timestamp}]` | 同 created_at |

---

### CommercialHubSyncContext DTO

```java
public class CommercialHubSyncContext {
    private Long userId;
    private String packCode;
    private int pointsAmount;
    private Integer remainPoints;
    private String hostType;          // WORKBUDDY / ENTERPRISE
    private String usageRecordId;     // 幂等 key
    private Long packId;              // 用于关联查询
    private String authCode;          // 授权码（可为空）
    private String pointsRuleCode;    // 积分规则编码
    private int packType;             // 场景包类型（1=平台,2=企业）
}
```

---

### Windows ProcessBuilder 引号转义修复

**问题**：Java ProcessBuilder 在 Windows 上传递包含嵌套 JSON 引号的参数时，`escapeForWindows` 的 `replace("\"", "\\\"")` 对扁平 JSON 有效，但对嵌套 JSON（`records: [{values: {...}}]`）无效，CLI 收到格式错误的 JSON（exit code 1）。

**修复方案**：新增 `cli-proxy.js` 中间层脚本——写参数到临时文件，用 Node.js `execFileSync` 调用 CLI。`WecomCliServiceImpl.doExecute()` 在 Windows 上改用代理脚本模式。

**文件**：
- `cli-proxy.js`：Node.js 代理脚本，从临时文件读取 JSON 参数调用 wecom-cli
- `WecomCliServiceImpl.java`：`doExecute()` Windows 分支改用 cli-proxy.js

---

### #13 明确不在范围内

以下能力延期至后续 OpenSpec：

- code_prefix/code_hash 数据源补全（需 FbsAuthCode 表加字段）
- risk_flag 风控逻辑
- commercial_hub 批量历史回填
- cli-proxy.js 非 Windows 路径优化

