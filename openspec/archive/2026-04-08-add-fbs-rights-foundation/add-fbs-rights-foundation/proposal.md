# ADD-FBS-RIGHTS-FOUNDATION（MVP）

## 1. Why（为什么）

### 背景

U3W-AI（福帮手）需要一套最小可运行的权益基础层，支撑 Skill 场景包的访问控制与积分消费。当前积分体系（`sys_user.points` / `wx_points_rule` / `wx_points_record`）可直接复用，不需要两阶段冻结即可满足 MVP 诉求。

### 业务价值

| 角色 | 价值 |
|------|------|
| 平台 | 能创建场景包、发放授权码 |
| 用户 | 能激活授权码、获得场景包权益、消费积分使用 Skill |
| 技术 | 建立权益校验与使用记录基础，为后续 OpenSpec 打地基 |

---

## 2. What Changes（做什么）

### MVP 核心原则

> **不新建第二套积分余额体系。积分扣减直接调用现有 `IPointsService.changePoints()`。**
> 冻结/确认/回滚两阶段语义、企业能力、fail-open 策略延期至后续 OpenSpec。

### 数据模型（4 张新表 + 1 张表扩展）

| 表名 | 操作 | 说明 |
|------|------|------|
| `fbs_scene_pack` | ADD | 场景包主表（含 current_version/content_snapshot，不建 fbs_pack_version） |
| `fbs_auth_code` | ADD | 授权码表（对齐 cv_access_code：available 独立字段 + status） |
| `fbs_user_pack` | ADD | 用户场景包关系表 |
| `fbs_skill_usage_record` | ADD | Skill 使用记录表 |
| `wx_points_record` | MODIFY | 新增 scene_pack_id（Long）、usage_record_id（String）2 个字段 |

**延期不建**：`fbs_points_freeze`、`fbs_pack_version`、`fbs_enterprise`、`fbs_enterprise_user`

### 核心服务

| 服务 | 说明 | 复用/新增 |
|------|------|----------|
| `RightsCheckService` | 权益校验：场景包 + 授权码 + 积分（全部 Fail-Closed） | 新增 |
| `IPointsService` | 直接复用 `changePoints()` 一次性扣减 | 复用，不改造 |

**延期不建**：`PointsTransactionService`（冻结适配层）

### 内部 API（#1 MVP 范围）

```
POST /fbs/internal/scene-pack/create     # 场景包创建
GET  /fbs/internal/scene-pack/{id}       # 场景包查询
POST /fbs/internal/auth-code/generate    # 授权码生成
POST /fbs/internal/auth-code/activate    # 授权码激活
POST /fbs/internal/rights/check          # 综合权益校验
POST /fbs/internal/usage/record          # 记录使用开始
PUT  /fbs/internal/usage/record/{id}/end # 记录使用结束
```

**延期不建**：`/points/freeze`、`/points/confirm`、`/points/rollback`

---

## 3. Impact（影响）

### 受影响模块

| 模块 | 影响 |
|------|------|
| `WxFbsir-business` | 新增 `com.wx.fbsir.business.fbs` 包 |
| 数据库 | 新增 4 张表，扩展 1 张现有表（2 个字段） |

### 不受影响

| 模块 | 说明 |
|------|------|
| `sys_user.points` | 不修改，不新增冻结字段 |
| `cv_access_code` | 保持不动，不做迁移 |
| `IPointsService` | 不改造冻结逻辑，直接复用 |

### 不在 #1 MVP 范围

- 积分冻结/确认/回滚（→ 后续 OpenSpec）
- 企业管理功能（→ OpenSpec #3）
- 平台运营 CRUD 页面（→ OpenSpec #2）
- 场景包版本管理（→ OpenSpec #2）
- 用户激活页面（→ OpenSpec #4）
- fail-open 策略（→ 后续 OpenSpec）

---

## 4. 验收边界（MVP）

- [ ] 能创建场景包
- [ ] 能生成授权码
- [ ] 用户能激活授权码并获得场景包权益（创建 fbs_user_pack）
- [ ] 能校验用户是否可用某场景包（checkScenePack）
- [ ] comprehensiveCheck 全部 Fail-Closed（场景包无权限/授权码无效/积分不足均拒绝）
- [ ] 能调用现有积分系统完成一次性扣减，并将 scene_pack_id / usage_record_id 写入 wx_points_record
- [ ] 能记录 Skill 使用记录（fbs_skill_usage_record）
- [ ] 与现有积分系统、简历访问码共存，不影响现有逻辑

---

## 5. 依赖

- **前置依赖**：无
- **后续依赖**：OpenSpec #2（平台运营）、#3（企业分发）、#4（用户界面）、#5（Skill 对接 + 冻结适配）
