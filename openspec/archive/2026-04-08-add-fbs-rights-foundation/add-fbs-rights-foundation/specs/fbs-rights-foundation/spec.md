# 福帮手权益与场景包领域底座 - 规范增量（MVP）

## 概述

本文档记录福帮手权益与场景包领域底座的功能规范增量，与代码保持同步。

> **#1 MVP 范围边界**：最简约能跑通。保留场景包、授权码、用户权益、积分一次性扣减、使用记录五个核心闭环。冻结/确认/回滚两阶段积分、企业能力、fail-open 策略均延期至后续 OpenSpec。

---

## ADDED Requirements（新增需求）

### Requirement: 场景包基础管理

系统 SHALL 支持场景包的创建与查询（发布/下架运营流程在 OpenSpec #2）。

#### Scenario: 创建场景包

```
GIVEN 系统内部服务调用 createScenePack
WHEN  调用方提交场景包创建请求（packCode, packName, packType, ownerType, ownerId, pointsRuleCode, ...）
THEN  系统 SHALL 创建场景包记录
AND   返回场景包ID和编码
```

#### Scenario: 查询场景包

```
GIVEN 场景包存在
WHEN  系统内部服务调用 getScenePack(packId)
THEN  系统 SHALL 返回场景包详情（含状态、关联规则）
```

---

### Requirement: 授权码基础管理

系统 SHALL 支持授权码的生成与激活（内部 API 范围，不含前端运营流程）。

> **语义对齐 cv_access_code**：`auth_code`（授权码字符串）+ `max_activations`（可用次数）+ `deadline`（截止时间）+ `available`（启用状态，**独立字段**）。
>
> `available`（TINYINT，0=禁用，1=启用）与 `status`（激活状态）是两个独立维度。即使 `available=1`，`status` 为已用尽/已过期/已撤销时同样不可用。

#### Scenario: 生成授权码

```
GIVEN 系统内部服务调用 generateAuthCode
WHEN  调用方提交授权码生成请求（codeType, targetType, targetId, maxActivations, deadline, ...）
THEN  系统 SHALL 生成唯一授权码或使用提供的授权码
AND   创建授权码记录（available=1, status=0）
AND   返回授权码内容
```

#### Scenario: 授权码激活

```
GIVEN 系统内部服务调用 activateAuthCode
WHEN  调用方提交激活请求（authCode, userId）
AND   授权码 available = 1（已启用）
AND   授权码 status IN (0, 1)（未激活或已激活，即尚未用尽/过期/撤销）
AND   activated_count < max_activations
AND   当前时间在 deadline 之前（或 deadline 为 NULL）
THEN  系统 SHALL 更新授权码 activated_count + 1
AND   若激活后 activated_count >= 1 且之前 status = 0，则更新 status = 1（已激活）
AND   若激活后 activated_count >= max_activations，则更新 status = 2（已用尽）
AND   创建 fbs_user_pack 记录（source_type=3，用户激活）
AND   返回激活成功及场景包信息
```

> **状态机说明**：
> - 初始：`status=0`（未激活）
> - 第一次激活后：`status=1`（已激活），表示该码已有人用过但仍可继续使用（`max_activations > 1` 场景）
> - `activated_count >= max_activations` 时：`status=2`（已用尽），不可再激活
> - 激活允许 `status IN (0, 1)`，状态 2/3/4 均不可激活（Fail-Closed）

#### Scenario: 授权码不可用（禁用）

```
GIVEN 授权码 available = 0（已禁用）
WHEN  系统内部服务调用 checkAuthCode 或 activateAuthCode
THEN  系统 SHALL 返回授权码不可用（Fail-Closed）
AND   不允许激活
```

#### Scenario: 授权码不可用（过期）

```
GIVEN 授权码 deadline 已过
WHEN  系统内部服务调用 checkAuthCode 或 activateAuthCode
THEN  系统 SHALL 返回授权码已过期（Fail-Closed）
AND   不允许激活
```

#### Scenario: 授权码不可用（次数用尽）

```
GIVEN 授权码 activated_count >= max_activations
WHEN  系统内部服务调用 activateAuthCode
THEN  系统 SHALL 返回授权码已用尽（Fail-Closed）
AND   不创建用户场景包关联
```

---

### Requirement: 权益校验

系统 SHALL 提供统一的权益校验接口，判断用户是否有权使用指定场景包。

> **MVP 策略：全部 Fail-Closed**。场景包无权限、授权码无效、积分不足均拒绝。fail-open 延期至后续 OpenSpec。

#### Scenario: 校验场景包权限

```
GIVEN 系统内部服务调用 checkScenePack(userId, packId)
WHEN  执行以下检查：
  - 场景包是否存在
  - 用户是否有有效的 fbs_user_pack 关联记录（status=1，未过期）
THEN  系统 SHALL 返回校验结果（allowed, reason）
```

#### Scenario: 校验授权码有效性

```
GIVEN 系统内部服务调用 checkAuthCode(authCode)
WHEN  执行以下检查：
  - 授权码是否存在
  - available = 1（已启用）
  - status IN (0, 1)（未激活或已激活，非用尽/过期/撤销）
  - 当前时间在 deadline 之前（或 deadline 为 NULL）
  - activated_count < max_activations
THEN  系统 SHALL 返回校验结果
```

> **与激活状态对齐**：checkAuthCode 与 activateAuthCode 使用相同的 status 判断逻辑，均允许 status IN (0, 1)。

#### Scenario: 校验用户积分（一次性扣减前预检）

```
GIVEN 系统内部服务调用 checkPoints(userId, ruleCode, amount)
WHEN  执行以下检查：
  - 积分规则是否存在
  - 积分规则是否启用：wx_points_rule.status = "0"（"0"=启用，其他=禁用）
  - sys_user.points >= amount
THEN  系统 SHALL 返回校验结果（sufficient / insufficient）
```

> **积分扣减金额来源**：`amount` 从 `wx_points_rule.points_value` 读取（通过 `points_rule_code` 查询）。
> 若场景包 `points_rule_code = NULL`，视为**免费包**，跳过积分校验和积分扣减步骤。
>
> ⚠️ **规则启用状态与惯例相反**：`wx_points_rule.status = "0"` 表示启用，对齐现有 `PointsServiceImpl` 的判断逻辑 `!"0".equals(rule.getStatus())`。实现时不得写成 `status = 1`。

#### Scenario: 综合校验（全部 Fail-Closed）

```
GIVEN 系统内部服务调用 comprehensiveCheck(userId, packCode, authCode, hostType, taskId)
WHEN  依次执行：
  1. 场景包权限校验
  2. 授权码校验（如提供）
  3. 积分余额预检（如场景包关联积分规则）
THEN  系统 SHALL 按以下规则处理：
  - IF 场景包不存在或用户无权限
    THEN 系统 SHALL 返回失败（Fail-Closed）
  - IF 授权码失败（无效/禁用/已用尽/已过期/已撤销）
    THEN 系统 SHALL 返回失败（Fail-Closed）
  - IF 积分余额不足
    THEN 系统 SHALL 返回失败（Fail-Closed）
AND  返回综合校验结果（pass, failReason）
```

> **注意**：MVP 阶段不实现 fail-open。`wx_points_rule.is_fail_open` 字段暂不新增，延期至后续 OpenSpec。

---

### Requirement: 积分一次性扣减（MVP Skill 消费）

系统 SHALL 在 Skill 场景中直接调用现有积分系统完成一次性扣减，不实现冻结/确认/回滚两阶段语义。

> **核心原则：直接复用现有 `IPointsService.changePoints()`，不新建第二套余额体系，不做冻结预占。**
>
> **扣减入口**：系统 SHALL 提供 `POST /fbs/internal/usage/consume` 作为统一扣减入口，内部完成"综合校验 → 扣减积分 → 写入使用记录"三步原子操作。调用方无需分拆调用。

#### Scenario: Skill 消费积分（一次性扣减）

```
GIVEN 系统内部服务调用 consumePointsForSkill(userId, packCode, skillCode, usageRecordId, hostType)
AND   扣减金额 amount 从 wx_points_rule.points_value 读取（通过场景包 points_rule_code 查询）
AND   若 points_rule_code = NULL，视为免费包（amount = 0，跳过积分扣减）
WHEN  综合校验（comprehensiveCheck）通过
AND   sys_user.points >= amount（非免费包）
THEN  系统 SHALL：
  1. 调用 IPointsService.changePoints(userId, ruleCode, -amount, scenePackId, usageRecordId)
     （轻量重载，见 design.md §4.2）
  2. 写入/更新 fbs_skill_usage_record（含 usage_record_id、points_amount、状态）
  3. 返回扣减成功及剩余积分
AND IF sys_user.points < amount
THEN  系统 SHALL 返回积分不足（Fail-Closed），不写记录
```

---

### Requirement: 使用记录追踪

系统 SHALL 记录所有 Skill 使用行为，支持审计和回溯。

#### Scenario: 记录使用开始

```
GIVEN 系统内部服务调用 recordUsage(usageRecordId, userId, hostType, skillCode, packId, ...)
THEN  系统 SHALL 创建 fbs_skill_usage_record 记录：
  - usage_record_id（String 幂等键）
  - user_id, host_type, skill_code, pack_id
  - points_amount（扣减数量）
  - start_time = 当前时间
  - status = 进行中
```

#### Scenario: 更新使用结束

```
GIVEN 系统内部服务调用 updateUsageEnd(usageRecordId, endTime, status, errorMessage)
WHEN  使用结束（成功或失败）
THEN  系统 SHALL 更新记录：
  - end_time = 当前时间
  - duration_seconds = 结束时间 - 开始时间
  - status = 成功 / 失败
  - error_message（如失败）
```

---

## MODIFIED Requirements（修改需求）

### Requirement: 积分流水表扩展（最小化）

**原规范**：`wx_points_record` 表仅包含通用积分流水。

**新规范**：`wx_points_record` 表新增以下字段（MVP 最小集）：
- `scene_pack_id`：关联场景包ID（Long）
- `usage_record_id`：关联使用记录幂等键（**String**，非 record_id）

> **注意**：`wx_points_record.record_id`（数据库自增主键）是 **Long** 类型，与 `usage_record_id`（业务幂等键，**String** 类型）严格区分。
>
> `auth_code_id`、`trigger_event`、`source_type` 等扩展字段延期至后续 OpenSpec 按需添加。

---

## 验收标准（MVP）

### 功能验收

- [ ] 能创建场景包
- [ ] 能生成授权码
- [ ] 用户能激活授权码并获得场景包权益（创建 fbs_user_pack）
- [ ] 能校验用户是否可用某场景包（checkScenePack）
- [ ] 综合校验（comprehensiveCheck）全部 fail-closed
- [ ] 能调用现有积分系统完成一次性扣减
- [ ] 能记录 Skill 使用记录（fbs_skill_usage_record）

### 非功能验收

- [ ] 兼容性：与现有积分系统共存，不新建第二套积分余额
- [ ] 类型严格：String 幂等键（usage_record_id）与 Long 数据库主键（record_id）不混用

### 明确不在 #1 MVP 范围

- 积分冻结/确认/回滚（→ 后续 OpenSpec）
- `fbs_points_freeze` 表（→ 延期）
- 企业能力 `fbs_enterprise` / `fbs_enterprise_user`（→ OpenSpec #3）
- fail-open 策略（→ 延期）
- `fbs_pack_version` 版本管理（→ 可选，若不需要则仅在 `fbs_scene_pack` 保留 `content_snapshot`）
- 超时释放定时任务（→ 延期）
- 对 `IPointsService.changePoints` 的全局冻结改造（→ 延期）
