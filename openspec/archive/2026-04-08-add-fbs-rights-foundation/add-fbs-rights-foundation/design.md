# 福帮手权益与场景包领域底座 - 技术方案（MVP）

## 1. 概述

本设计详细说明权益与场景包领域底座的 MVP 技术实现方案。

**MVP 核心原则：**
- 直接复用现有积分系统（`IPointsService.changePoints()`），不新建第二套余额体系
- 不实现冻结/确认/回滚两阶段语义
- 不新增企业能力表结构
- 全部失败场景 Fail-Closed
- 最小表集：`fbs_scene_pack` + `fbs_auth_code` + `fbs_user_pack` + `fbs_skill_usage_record`

---

## 2. 数据库设计

### 2.1 新增表（4 张）

#### 2.1.1 场景包主表 `fbs_scene_pack`

```sql
CREATE TABLE `fbs_scene_pack` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pack_code`           VARCHAR(64) NOT NULL COMMENT '场景包编码',
    `pack_name`           VARCHAR(128) NOT NULL COMMENT '场景包名称',
    `pack_type`           TINYINT NOT NULL COMMENT '1=平台包, 2=企业包, 3=自定义包',
    `owner_type`          TINYINT NOT NULL COMMENT '1=平台, 2=企业, 3=个人',
    `owner_id`            BIGINT DEFAULT NULL COMMENT '所属者ID',
    `description`         TEXT COMMENT '场景包描述',
    `status`              TINYINT NOT NULL DEFAULT 0 COMMENT '0=草稿, 1=已发布, 2=已下架',
    `visible_scope`       VARCHAR(32) DEFAULT 'ALL' COMMENT '可见范围：ALL/PRIVATE',
    `points_rule_code`    VARCHAR(64) DEFAULT NULL COMMENT '关联积分规则编码（NULL=免费）',
    `content_snapshot`    LONGTEXT COMMENT '内容快照JSON（MVP 版本管理简化方案）',
    `current_version`     VARCHAR(32) DEFAULT '1.0.0' COMMENT '当前版本号',
    `created_by`          VARCHAR(64) DEFAULT NULL,
    `create_time`         DATETIME DEFAULT NULL,
    `updated_by`          VARCHAR(64) DEFAULT NULL,
    `update_time`         DATETIME DEFAULT NULL,
    `del_flag`            CHAR(1) DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pack_code` (`pack_code`),
    KEY `idx_owner` (`owner_type`, `owner_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景包主表';
```

> **版本管理简化**：MVP 阶段不建 `fbs_pack_version` 表，在 `fbs_scene_pack` 保留 `current_version` 和 `content_snapshot` 即可跑通。版本历史管理放 OpenSpec #2。

#### 2.1.2 授权码表 `fbs_auth_code`

> **语义对齐 cv_access_code**：`auth_code`（码字符串）+ `max_activations`（可用次数）+ `deadline`（截止时间）+ `available`（启用状态，**独立字段**）
> `cv_access_code` 保持不动，不做迁移。
>
> **注意**：`status`（激活/用尽/过期/撤销）与 `available`（启用/禁用）是两个独立维度：
> - `available`：管理层面开关，0=禁用（任何人无法使用），1=启用（正常可用）
> - `status`：使用状态，0=未激活，1=已激活，2=已用尽，3=已过期，4=已撤销
>
> **激活状态机**：
> - 可激活条件：`available=1` AND `status IN (0,1)` AND `activated_count < max_activations` AND 未过期
> - 首次激活后：`status → 1`（已激活），`activated_count + 1`
> - 达到 max_activations：`status → 2`（已用尽）
> - `status` 为 2/3/4 时均不可激活（Fail-Closed）

```sql
CREATE TABLE `fbs_auth_code` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `auth_code`           VARCHAR(128) NOT NULL COMMENT '授权码字符串',
    `code_type`           TINYINT NOT NULL COMMENT '1=场景包权益码, 2=通用授权码',
    `target_type`         VARCHAR(32) DEFAULT NULL COMMENT 'SCENE_PACK/GENERIC',
    `target_id`           BIGINT DEFAULT NULL COMMENT '关联目标ID（如场景包ID）',
    `issuer_type`         TINYINT NOT NULL COMMENT '1=平台, 2=企业, 3=用户',
    `issuer_id`           BIGINT NOT NULL COMMENT '发放者ID',
    `available`           TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态：0=禁用, 1=启用（独立于status）',
    `status`              TINYINT NOT NULL DEFAULT 0 COMMENT '0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销',
    `deadline`            DATETIME DEFAULT NULL COMMENT '截止时间，NULL=不限',
    `max_activations`     INT NOT NULL DEFAULT 1 COMMENT '最大激活次数',
    `activated_count`     INT NOT NULL DEFAULT 0 COMMENT '已激活次数',
    `description`         VARCHAR(256) DEFAULT NULL COMMENT '说明/备注',
    `created_by`          VARCHAR(64) DEFAULT NULL,
    `create_time`         DATETIME DEFAULT NULL,
    `updated_by`          VARCHAR(64) DEFAULT NULL,
    `update_time`         DATETIME DEFAULT NULL,
    `del_flag`            CHAR(1) DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_code` (`auth_code`),
    KEY `idx_status` (`status`),
    KEY `idx_available` (`available`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权码表';
```

#### 2.1.3 用户场景包关系表 `fbs_user_pack`

```sql
CREATE TABLE `fbs_user_pack` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`             BIGINT NOT NULL COMMENT '用户ID',
    `pack_id`             BIGINT NOT NULL COMMENT '场景包ID',
    `pack_version`        VARCHAR(32) DEFAULT NULL COMMENT '激活时的版本',
    `auth_code_id`        BIGINT DEFAULT NULL COMMENT '激活时使用的授权码ID',
    `activated_at`        DATETIME NOT NULL COMMENT '激活时间',
    `expires_at`          DATETIME DEFAULT NULL COMMENT '过期时间，NULL=永不过期',
    `status`              TINYINT NOT NULL DEFAULT 1 COMMENT '1=有效, 2=已过期, 3=已撤销',
    `source_type`         TINYINT NOT NULL COMMENT '1=平台分发, 2=企业分发, 3=用户激活',
    `created_by`          VARCHAR(64) DEFAULT NULL,
    `create_time`         DATETIME DEFAULT NULL,
    `updated_by`          VARCHAR(64) DEFAULT NULL,
    `update_time`         DATETIME DEFAULT NULL,
    `del_flag`            CHAR(1) DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_pack` (`user_id`, `pack_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户场景包关系表';
```

#### 2.1.4 Skill 使用记录表 `fbs_skill_usage_record`

```sql
CREATE TABLE `fbs_skill_usage_record` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `usage_record_id`     VARCHAR(64) NOT NULL COMMENT '使用记录幂等键（String，对应 WorkBuddy taskId）',
    `user_id`             BIGINT NOT NULL COMMENT '用户ID',
    `host_type`           VARCHAR(32) NOT NULL COMMENT '宿主类型：WORKBUDDY/STANDALONE/API',
    `host_session_id`     VARCHAR(128) DEFAULT NULL COMMENT '宿主会话ID',
    `skill_code`          VARCHAR(64) NOT NULL COMMENT '技能编码',
    `pack_id`             BIGINT DEFAULT NULL COMMENT '使用的场景包ID',
    `pack_version`        VARCHAR(32) DEFAULT NULL COMMENT '使用的场景包版本',
    `points_amount`       INT NOT NULL DEFAULT 0 COMMENT '扣减积分数量（0=免费）',
    `status`              TINYINT NOT NULL DEFAULT 0 COMMENT '0=进行中, 1=成功, 2=失败',
    `start_time`          DATETIME NOT NULL COMMENT '使用开始时间',
    `end_time`            DATETIME DEFAULT NULL COMMENT '使用结束时间',
    `duration_seconds`    INT DEFAULT NULL COMMENT '使用时长（秒）',
    `error_message`       TEXT DEFAULT NULL COMMENT '失败原因',
    `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_usage_record_id` (`usage_record_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_pack` (`pack_id`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill使用记录表';
```

### 2.2 扩展现有表（最小化）

#### 2.2.1 积分流水表 `wx_points_record` 扩展（2 个字段）

```sql
ALTER TABLE `wx_points_record`
    ADD COLUMN `scene_pack_id`   BIGINT DEFAULT NULL COMMENT '关联场景包ID' AFTER `remark`,
    ADD COLUMN `usage_record_id` VARCHAR(64) DEFAULT NULL COMMENT '关联使用记录幂等键（String）' AFTER `scene_pack_id`;
```

> **注意**：`wx_points_record.record_id` 是 **Long** 类型（数据库自增），与 String 类型的 `usage_record_id`（业务幂等键）严格区分。
>
> **暂不新增**：`auth_code_id`、`trigger_event`、`source_type`、`wx_points_rule` 扩展字段——MVP 不需要，延期至后续 OpenSpec。

### 2.3 延期不建的表

| 表名 | 延期原因 |
|------|----------|
| `fbs_points_freeze` | 冻结/确认/回滚两阶段逻辑延期 |
| `fbs_pack_version` | 版本管理延期，`fbs_scene_pack.current_version` 够用 |
| `fbs_enterprise` | 企业能力延期至 OpenSpec #3 |
| `fbs_enterprise_user` | 企业能力延期至 OpenSpec #3 |

---

## 3. 权益校验服务 RightsCheckService

### 3.1 定位说明

`RightsCheckService` 是 Skill 调用前的统一权益门控。MVP 策略：**全部 Fail-Closed**，任何校验失败均拒绝通行。

### 3.2 接口

```java
package com.wx.fbsir.business.fbs.service;

public interface RightsCheckService {

    /**
     * 校验用户是否有权使用指定场景包
     * @param userId 用户ID（Long）
     * @param packId 场景包ID（Long）
     * @return 校验结果
     */
    RightsCheckResult checkScenePack(Long userId, Long packId);

    /**
     * 校验授权码有效性
     * @param authCode 授权码字符串（String）
     * @return 校验结果
     */
    RightsCheckResult checkAuthCode(String authCode);

    /**
     * 校验积分是否充足（一次性扣减前预检）
     * @param userId   用户ID（Long）
     * @param ruleCode 积分规则编码
     * @param amount   需要积分数
     * @return 校验结果
     */
    RightsCheckResult checkPoints(Long userId, String ruleCode, Integer amount);

    /**
     * 综合校验（场景包 + 授权码 + 积分）
     * MVP 策略：全部 Fail-Closed
     *
     * @param userId    用户ID（Long）
     * @param packCode  场景包编码（String）
     * @param authCode  授权码（String，可为空）
     * @param hostType  宿主类型（String）
     * @param taskId    任务幂等键（String）
     * @return 综合校验结果
     */
    ComprehensiveRightsResult comprehensiveCheck(
            Long userId, String packCode, String authCode,
            String hostType, String taskId);
}
```

### 3.3 综合校验流程

```
comprehensiveCheck()
    │
    ├─ 1. checkScenePack(userId, packId)
    │      └─ 不存在/无权限 → Fail-Closed，返回失败
    │
    ├─ 2. checkAuthCode(authCode)  [如提供]
    │      └─ 无效/禁用/用尽/过期 → Fail-Closed，返回失败
    │         注意：status IN (0,1) 均可通过，2/3/4 均失败
    │
    └─ 3. checkPoints(userId, ruleCode, amount)  [如场景包 points_rule_code 不为 NULL]
           │  amount 来源：wx_points_rule.points_value（通过 points_rule_code 查询）
           │  若 points_rule_code = NULL → 免费包，跳过此步
           └─ 积分不足 → Fail-Closed，返回失败
           
    全部通过 → 返回 pass=true
```

---

## 4. 积分消费服务（一次性扣减）

### 4.1 定位说明

MVP 阶段 Skill 消费积分采用**一次性直接扣减**：调用现有 `IPointsService.changePoints()` 的轻量重载版本，直接在插入 `wx_points_record` 时写入 `scenePackId` 和 `usageRecordId`。

**不实现冻结/确认/回滚**。

### 4.2 IPointsService 轻量重载方案

> **问题背景**：现有 `changePoints(userId, ruleCode, amount)` 返回 `AjaxResult.success("积分操作成功")`，不返回 `recordId`，调用后无法通过返回值定位刚插入的流水。因此**不采用"调用后 UPDATE"方案**，改为增加轻量重载方法，在插入时直接写入扩展字段。

```java
// 在 IPointsService 新增以下重载方法（不改动原方法）
public interface IPointsService {

    // 原方法（保持不动）
    AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount);

    /**
     * MVP 轻量重载：Skill 消费场景，直接写入 scenePackId 和 usageRecordId
     * 不做冻结预占，不做并发大改造
     * 若 ruleCode = NULL（免费包），直接返回成功，不扣积分
     *
     * @param userId          用户ID
     * @param ruleCode        积分规则编码（NULL = 免费包，跳过扣减）
     * @param changeAmount    扣减数量（传负数，如 -10）
     * @param scenePackId     关联场景包ID（写入 wx_points_record.scene_pack_id）
     * @param usageRecordId   使用记录幂等键（写入 wx_points_record.usage_record_id）
     */
    AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount,
                            Long scenePackId, String usageRecordId);
}
```

**实现要点**：
- 在 `WxPointsRecordMapper.insertPointsRecord` 的 `INSERT` SQL 中同时写入 `scene_pack_id` 和 `usage_record_id` 两个字段。
- 该重载仅多接收两个字段，**不触碰冻结逻辑、不改 `sys_user` 扣减流程**，改动量极小。
- 免费包（`ruleCode = NULL`）时直接返回成功，不调用原积分服务。

### 4.3 积分金额来源

扣减金额 `amount` 的获取流程：

```
1. 取场景包 fbs_scene_pack.points_rule_code
2. 若 points_rule_code = NULL → 免费包，amount = 0，跳过积分扣减
3. 若 points_rule_code 不为空 → 查 wx_points_rule WHERE rule_code = ?
4. 判断规则是否启用：!"0".equals(rule.getStatus())
   即 status = "0" 为启用，其他值（"1" 等）为禁用
   （与现有 PointsServiceImpl 判断逻辑保持一致，不使用 status = 1）
5. 取 wx_points_rule.points_value 作为 amount
6. 若规则不存在或已禁用 → Fail-Closed，返回失败
```

> ⚠️ **注意**：`wx_points_rule.status` 字段的语义与通常 Java 惯例相反——`"0"` 表示**启用**，`"1"` 表示**禁用**。实现时务必对齐现有 `PointsServiceImpl` 的判断写法：`!"0".equals(rule.getStatus())`，切勿写成 `status = 1`（否则有积分的规则会被误判为禁用，扣积分链路跑不通）。

### 4.4 SkillConsumeService 接口

为避免调用方自行编排"校验 → 扣减 → 记录"三步，系统 SHALL 提供 `SkillConsumeService` 统一封装：

```java
package com.wx.fbsir.business.fbs.service;

public interface SkillConsumeService {

    /**
     * Skill 消费统一入口：综合校验 → 积分扣减 → 写使用记录
     * 对应 POST /fbs/internal/usage/consume
     *
     * @param userId          用户ID（Long）
     * @param packCode        场景包编码（String）
     * @param skillCode       技能编码（String）
     * @param usageRecordId   使用记录幂等键（String，调用方保证唯一）
     * @param hostType        宿主类型（String）
     * @param hostSessionId   宿主会话ID（String，可空）
     * @param authCode        授权码（String，可空）
     * @return ConsumeResult  { success, usageRecordId, remainPoints, failReason }
     */
    ConsumeResult consume(Long userId, String packCode, String skillCode,
                          String usageRecordId, String hostType,
                          String hostSessionId, String authCode);
}
```

**内部执行流程**：

```
consume()
    │
    ├─ 1. 查 fbs_scene_pack（packCode → packId, points_rule_code）
    │      └─ 不存在 → Fail-Closed
    │
    ├─ 2. 从 wx_points_rule 读取 amount（points_rule_code → points_value）
    │      └─ rule_code = NULL → 免费包，amount = 0
    │      └─ 规则不存在 → Fail-Closed
    │      └─ 规则禁用（!"0".equals(status)）→ Fail-Closed
    │         ⚠️ wx_points_rule.status="0" 为启用，非 "0" 为禁用（与通常惯例相反）
    │
    ├─ 3. comprehensiveCheck(userId, packCode, authCode, hostType, usageRecordId)
    │      └─ 任意失败 → Fail-Closed
    │
    ├─ 4. 写 fbs_skill_usage_record（status=0, start_time=now, points_amount=amount）
    │      └─ 幂等：若 usageRecordId 已存在且 status=0 → 继续（视为重试）
    │         若 usageRecordId 已存在且 status=1/2 → 直接返回（已处理）
    │
    ├─ 5. 调用 pointsService.changePoints(userId, ruleCode, -amount, scenePackId, usageRecordId)
    │      └─ 免费包（amount=0）→ 跳过此步
    │      └─ 积分不足 → Fail-Closed，更新 fbs_skill_usage_record.status=2
    │
    └─ 6. 更新 fbs_skill_usage_record（status=1, end_time=now）
           返回 { success=true, remainPoints }
```

---

## 5. API 规范

### 5.1 场景包接口

```
POST   /fbs/internal/scene-pack/create
       Body: { packCode, packName, packType, ownerType, ownerId, pointsRuleCode, description }
       Response: { id(Long), packCode }

GET    /fbs/internal/scene-pack/{id}
       Response: { id(Long), packCode, packName, status, currentVersion, ... }
```

### 5.2 授权码接口

```
POST   /fbs/internal/auth-code/generate
       Body: { codeType, targetType, targetId(Long), issuerType, issuerId(Long),
               deadline(DateTime nullable), maxActivations(Integer),
               description(String nullable) }
       Response: { id(Long), authCode(String) }

POST   /fbs/internal/auth-code/activate
       Body: { authCode(String), userId(Long) }
       Response: { success, userPackId(Long), packId(Long), packCode }
```

### 5.3 权益校验接口

```
POST   /fbs/internal/rights/check
       Body: { userId(Long), packCode(String), authCode(String nullable),
               hostType(String), taskId(String) }
       Response: { pass(Boolean), failReason(String nullable) }
```

### 5.4 使用记录接口

```
POST   /fbs/internal/usage/record
       Body: { usageRecordId(String), userId(Long), hostType(String),
               hostSessionId(String nullable), skillCode(String),
               packId(Long nullable), packVersion(String nullable),
               pointsAmount(Integer), startTime(DateTime) }
       Response: { success, usageRecordId(String) }

PUT    /fbs/internal/usage/record/{usageRecordId}/end
       Body: { status(Integer), errorMessage(String nullable) }
       Response: { success }
```

### 5.5 Skill 消费统一入口（P0 新增）

```
POST   /fbs/internal/usage/consume
       Body: {
           userId(Long),
           packCode(String),
           skillCode(String),
           usageRecordId(String),   // 调用方生成的幂等键（建议 UUID 或 taskId）
           hostType(String),         // WORKBUDDY / STANDALONE / API
           hostSessionId(String nullable),
           authCode(String nullable)
       }
       Response: {
           success(Boolean),
           usageRecordId(String),
           remainPoints(Integer nullable),  // 扣减后剩余积分（免费包为 null）
           failReason(String nullable)       // 失败时说明
       }
```

> **此接口是跑通 MVP 的核心入口**。调用方只需一次调用，系统内部完成"综合校验 → 积分扣减 → 写使用记录"，不需要调用方分步编排。
> 详细流程见 §4.4 SkillConsumeService。

> **已删除接口**（延期）：
> - `POST /fbs/internal/points/freeze`
> - `POST /fbs/internal/points/confirm`
> - `POST /fbs/internal/points/rollback`

---

## 6. 包结构（MVP）

```
com.wx.fbsir.business.fbs
├── controller/internal/
│   ├── FbsScenePackController.java
│   ├── FbsAuthCodeController.java
│   ├── FbsRightsCheckController.java
│   ├── FbsUsageRecordController.java
│   └── FbsSkillConsumeController.java     # POST /fbs/internal/usage/consume
├── service/
│   ├── RightsCheckService.java
│   ├── RightsCheckServiceImpl.java
│   ├── SkillConsumeService.java            # Skill 消费统一入口（§4.4）
│   └── SkillConsumeServiceImpl.java
├── domain/entity/
│   ├── FbsScenePack.java
│   ├── FbsAuthCode.java    # available（0=禁用/1=启用）独立于 status
│   ├── FbsUserPack.java
│   └── FbsSkillUsageRecord.java
├── domain/enums/
│   ├── PackStatus.java         # 0=草稿, 1=已发布, 2=已下架
│   ├── AuthCodeStatus.java     # 0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销
│   └── UsageStatus.java        # 0=进行中, 1=成功, 2=失败
├── dto/
│   ├── RightsCheckResult.java
│   ├── ComprehensiveRightsResult.java
│   ├── ConsumeResult.java                  # success, usageRecordId, remainPoints, failReason
│   └── ScenePackDTO.java
└── mapper/
    ├── FbsScenePackMapper.java
    ├── FbsAuthCodeMapper.java      # selectByAuthCode, updateActivated
    ├── FbsUserPackMapper.java      # selectByUserIdPackId
    └── FbsSkillUsageRecordMapper.java  # selectByRecordId, updateStatusByRecordId
```

**已删除（延期）**：
- `FbsPointsTransactionController.java`
- `PointsTransactionService.java` / `PointsTransactionServiceImpl.java`
- `FbsEnterprise.java` / `FbsEnterpriseUser.java`
- `FbsPointsFreeze.java` / `FbsPackVersion.java`
- `FreezeStatus.java` / `PointsAction.java`
- `FbsPointsFreezeMapper.java` / `FbsEnterpriseMapper.java` / `FbsEnterpriseUserMapper.java`
- `FreezeResult.java` / `ConfirmResult.java` / `RollbackResult.java`
- `task/FbsPointsFreezeTimeoutTask.java`

---

## 7. 后续 OpenSpec 依赖

| OpenSpec | 依赖内容 |
|----------|----------|
| #2 平台侧运营 | 场景包发布/下架、`fbs_pack_version`、管理员页面 |
| #3 企业侧分发 | `fbs_enterprise` / `fbs_enterprise_user`、企业管理功能 |
| #4 用户侧自助 | 授权码激活用户界面 |
| #5 Skill 对接 | RightsCheckService 对接、积分冻结适配层（如需两阶段） |
