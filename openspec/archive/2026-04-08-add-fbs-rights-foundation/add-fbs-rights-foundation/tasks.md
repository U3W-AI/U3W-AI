# 福帮手权益与场景包领域底座 - 实施任务清单（MVP）

## 任务概览

| 编号 | 任务 | 状态 |
|------|------|------|
| 1 | 数据库设计与脚本 | ✅ |
| 2 | 实体类开发 | ✅ |
| 3 | 核心服务开发（权益校验 + Skill 消费） | ✅ |
| 4 | 内部 API 开发（含 /usage/consume） | ✅ |
| 5 | 扩展现有积分系统（IPointsService 轻量重载） | ✅ |
| 6 | 单元测试 | ✅ |
| 7 | 文档与代码清理 | ✅ |

---

## 任务 1：数据库设计与脚本

- [x] 1.1 编写 `fbs_scene_pack` 建表脚本（含 `current_version`、`content_snapshot`，不建 fbs_pack_version）
- [x] 1.2 编写 `fbs_auth_code` 建表脚本（含 `available` 独立字段，对齐 cv_access_code 语义）
- [x] 1.3 编写 `fbs_user_pack` 建表脚本
- [x] 1.4 编写 `fbs_skill_usage_record` 建表脚本
- [x] 1.5 编写 `wx_points_record` 扩展字段 ALTER 语句（仅新增 `scene_pack_id` + `usage_record_id`）
- [x] 1.6 脚本命名规范：`V{日期}__{change-id}__{description}.sql`
- [x] 1.7 在本地数据库执行脚本并验证

**延期不建**：`fbs_points_freeze`、`fbs_pack_version`、`fbs_enterprise`、`fbs_enterprise_user`
**延期不做**：`wx_points_rule` 扩展（`rule_scope`、`is_fail_open` 等字段）

---

## 任务 2：实体类开发

### 2.1 新增实体

- [x] 2.1.1 创建 `FbsScenePack` 实体类（含 `currentVersion`、`contentSnapshot`）
- [x] 2.1.2 创建 `FbsAuthCode` 实体类
  - 注意：`available`（TINYINT，0=禁用/1=启用）与 `status`（0=未激活/1=已激活/2=已用尽/3=已过期/4=已撤销）是两个独立字段
- [x] 2.1.3 创建 `FbsUserPack` 实体类
- [x] 2.1.4 创建 `FbsSkillUsageRecord` 实体类
  - 注意：`usageRecordId`（String 幂等键，对应 WorkBuddy taskId）

### 2.2 新增枚举

- [x] 2.2.1 创建 `PackStatus` 枚举（0=草稿 / 1=已发布 / 2=已下架）
- [x] 2.2.2 创建 `AuthCodeStatus` 枚举（0=未激活 / 1=已激活 / 2=已用尽 / 3=已过期 / 4=已撤销）
- [x] 2.2.3 创建 `UsageStatus` 枚举（0=进行中 / 1=成功 / 2=失败）

**延期不做**：`FreezeStatus`、`PointsAction`、`FbsEnterprise`、`FbsEnterpriseUser`、`FbsPointsFreeze`、`FbsPackVersion`

### 2.3 扩展现有实体

- [x] 2.3.1 扩展 `PointsRecord` 实体（新增 `scenePackId`（Long）、`usageRecordId`（String）字段）

**延期不做**：`PointsRule` 扩展（`ruleScope`、`isFailOpen` 等字段）

### 2.4 Mapper 接口

- [x] 2.4.1 创建 `FbsScenePackMapper.java`（含 `selectByPackCode`）
- [x] 2.4.2 创建 `FbsAuthCodeMapper.java`（含 `selectByAuthCode`、`updateActivated`）
- [x] 2.4.3 创建 `FbsUserPackMapper.java`（含 `selectByUserIdPackId`、`selectActiveByUserId`）
- [x] 2.4.4 创建 `FbsSkillUsageRecordMapper.java`（含 `selectByRecordId`、`updateStatusByRecordId`）

**延期不做**：`FbsPointsFreezeMapper`、`FbsEnterpriseMapper`、`FbsEnterpriseUserMapper`、`FbsPackVersionMapper`

---

## 任务 3：核心服务开发

### 3.1 DTO 定义

- [x] 3.1.1 定义 `RightsCheckResult` DTO（`allowed`、`reason`）
- [x] 3.1.2 定义 `ComprehensiveRightsResult` DTO（`pass`、`failReason`）
- [x] 3.1.3 定义 `ScenePackDTO` DTO
- [x] 3.1.4 定义 `ConsumeResult` DTO（`success`、`usageRecordId`、`remainPoints`、`failReason`）

**延期不做**：`FreezeResult`、`ConfirmResult`、`RollbackResult`

### 3.2 权益校验服务 RightsCheckService

- [x] 3.2.1 定义 `RightsCheckService` 接口
- [x] 3.2.2 实现 `checkScenePack` 方法
  - 校验场景包是否存在
  - 校验用户是否有有效的 `fbs_user_pack` 关联（status=1，未过期）
- [x] 3.2.3 实现 `checkAuthCode` 方法
  - 检查：授权码存在 + `available=1` + `status IN (0,1)` + 未过期 + `activated_count < max_activations`
- [x] 3.2.4 实现 `checkPoints` 方法
  - 从 `wx_points_rule.points_value` 读取 amount（通过 `ruleCode` 查询）
  - 直接比较 `sys_user.points >= amount`（不查冻结）
  - 若 `ruleCode = NULL`，返回免费（跳过）
- [x] 3.2.5 实现 `comprehensiveCheck` 方法
  - 依次执行：checkScenePack → checkAuthCode（如有）→ checkPoints（如关联规则且 ruleCode 非 NULL）
  - 任意失败 → Fail-Closed，返回失败及原因

### 3.3 授权码激活服务

- [x] 3.3.1 实现 `activateAuthCode(authCode, userId)` 方法
  - 调用 `checkAuthCode` 预校验（status IN (0,1) 均可通过）
  - 更新 `fbs_auth_code.activated_count + 1`
  - 若激活前 `status=0`，更新 `status=1`（已激活）
  - 若激活后 `activated_count >= max_activations`，更新 `status=2`（已用尽）
  - 创建 `fbs_user_pack` 记录（`source_type=3`，`auth_code_id` 关联）
  - 返回激活结果

### 3.4 Skill 消费服务 SkillConsumeService（P0 新增）

- [x] 3.4.1 定义 `SkillConsumeService` 接口（`consume` 方法）
- [x] 3.4.2 实现 `SkillConsumeServiceImpl`，内部流程：
  1. 查 `fbs_scene_pack`（packCode → packId, points_rule_code）
  2. 从 `wx_points_rule` 读取 amount；`points_rule_code = NULL` → 免费包
  3. 调用 `comprehensiveCheck`
  4. 幂等写入 `fbs_skill_usage_record`（status=0）
  5. 调用 `IPointsService.changePoints(userId, ruleCode, -amount, scenePackId, usageRecordId)`（非免费包）
  6. 更新 `fbs_skill_usage_record`（status=1）
  7. 失败时更新 status=2，返回 failReason

---

## 任务 4：内部 API 开发

- [x] 4.1 实现 `FbsScenePackController`（POST create，GET getById）
- [x] 4.2 实现 `FbsAuthCodeController`（POST generate，POST activate）
- [x] 4.3 实现 `FbsRightsCheckController`（POST comprehensiveCheck）
- [x] 4.4 实现 `FbsUsageRecordController`（POST record，PUT end）
- [x] 4.5 实现 `FbsSkillConsumeController`（POST `/fbs/internal/usage/consume`）——**P0，MVP 跑通核心入口**
- [x] 4.6 路由统一前缀：`/fbs/internal/`
- [x] 4.7 响应格式统一：使用项目现有 `AjaxResult` 或统一 `R<T>` 封装

**延期不做**：`FbsPointsTransactionController`（freeze/confirm/rollback 接口）

---

## 任务 5：扩展现有积分系统（最小化改动）

- [x] 5.1 在 `IPointsService` 新增轻量重载方法：
  ```java
  AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount,
                          Long scenePackId, String usageRecordId);
  ```
  - 实现中，在插入 `wx_points_record` 时同时写入 `scene_pack_id` 和 `usage_record_id`
  - **不改动**原 `changePoints(userId, ruleCode, amount)` 方法
  - **不加**行锁、冻结预占逻辑

- [x] 5.2 确认 `wx_points_record` 实体和 Mapper 的 INSERT SQL 已包含新增两字段

> **不采用"调用后 UPDATE 补写"方案**：原 `changePoints` 返回 `AjaxResult("积分操作成功")`，不返回 `recordId`，无法定位刚插入的流水，因此改为轻量重载直接写入。

**延期不做**：`PointsPrecheckService` 扩展、`changePoints` 可用积分全局校验、SELECT FOR UPDATE 行锁

---

## 任务 6：单元测试

### 6.1 RightsCheckService 测试

- [x] 6.1.1 测试 checkScenePack - 用户有有效权益
- [x] 6.1.2 测试 checkScenePack - 场景包不存在（Fail-Closed）
- [x] 6.1.3 测试 checkScenePack - 用户无权益（Fail-Closed）
- [x] 6.1.4 测试 checkAuthCode - 有效码
- [x] 6.1.5 测试 checkAuthCode - available=0（禁用，Fail-Closed）
- [x] 6.1.6 测试 checkAuthCode - 过期（Fail-Closed）
- [x] 6.1.7 测试 checkAuthCode - 次数已用尽（Fail-Closed）
- [x] 6.1.8 测试 comprehensiveCheck - 全部通过
- [x] 6.1.9 测试 comprehensiveCheck - 场景包失败（Fail-Closed）
- [x] 6.1.10 测试 comprehensiveCheck - 积分不足（Fail-Closed）

### 6.2 授权码激活测试

- [x] 6.2.1 测试 activateAuthCode - 正常激活（创建 fbs_user_pack）
- [x] 6.2.2 测试 activateAuthCode - 最后一次激活（status 更新为已用尽）
- [x] 6.2.3 测试 activateAuthCode - 已禁用（Fail-Closed）
- [x] 6.2.4 测试 activateAuthCode - 过期（Fail-Closed）
- [x] 6.2.5 测试 activateAuthCode - 次数超限（Fail-Closed）

### 6.3 积分扣减与 Skill 消费测试

- [x] 6.3.1 测试 `SkillConsumeService.consume` - 有积分场景包，扣减成功
- [x] 6.3.2 测试 `SkillConsumeService.consume` - 免费包（ruleCode=NULL），跳过积分扣减
- [x] 6.3.3 测试 `SkillConsumeService.consume` - 积分不足（Fail-Closed）
- [x] 6.3.4 测试 `SkillConsumeService.consume` - 幂等（usageRecordId 重复提交）
- [x] 6.3.5 测试 `wx_points_record` 是否写入了 `scene_pack_id` 和 `usage_record_id`（轻量重载验证）

**延期不做**：冻结并发测试、freeze/confirm/rollback 测试、超时释放测试

---

## 任务 7：文档与代码清理

- [x] 7.1 补充 Javadoc 注释（Service 接口、关键方法）
- [x] 7.2 确认延期内容均已在代码中添加 TODO 注释（标注对应 OpenSpec 编号）

---

## 实施顺序

```
阶段一：基础设施（任务 1、2）
    └── 产出：4张表SQL脚本 + 1张表ALTER + 实体类 + Mapper

阶段二：权益校验、激活与消费（任务 3）
    └── 产出：RightsCheckService + 授权码激活服务 + SkillConsumeService

阶段三：API 与积分扩展（任务 4、5）
    └── 产出：5个内部 Controller（含 /usage/consume）+ IPointsService 轻量重载

阶段四：测试与文档（任务 6、7）
    └── 产出：单元测试报告 + Javadoc
```

---

## MVP 验收目标

- [x] 能创建场景包
- [x] 能生成授权码
- [x] 用户能激活授权码并获得场景包权益（fbs_user_pack）
- [x] 能校验用户是否可用某场景包
- [x] comprehensiveCheck 全部 Fail-Closed
- [x] 能调用现有积分系统完成一次性扣减
- [x] 能记录 Skill 使用记录
