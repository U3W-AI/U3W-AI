# OpenSpec #3 Proposal: 企业侧场景包与成员分发

> **Change ID**: `add-enterprise-scene-pack-ops`
> **阶段**: 第三阶段 — 企业侧（Enterprise Side）
> **日期**: 2026-04-09

---

## 一、Why（为什么做）

白皮书定义了 FBS 三侧架构：

| 侧 | 定位 | 已完成 |
|---|------|--------|
| 用户侧 | 个人写作者 | OpenSpec #1 权益底座 ✅ |
| 平台侧 | 福帮手运营后台 | OpenSpec #2 场景包运营 ✅ |
| **企业侧** | 机构部署、场景包分发、多成员主编 | **本 OpenSpec** |

当前缺口：
- **没有企业组织概念**：场景包只有平台分发，没有面向企业的分发与配额管理
- **没有成员体系**：用户之间没有"企业成员"关系，无法区分个人用户和企业成员
- **没有分发与配额能力**：平台无法向企业分发场景包（gift），企业获得的包没有独立配额控制

---

## 二、What Changes（做什么）

### 2.1 新增数据表（4张）

| 表名 | 用途 | 关键字段 |
|------|------|---------|
| `fbs_enterprise` | 企业组织主表 | enterpriseName, contact, status |
| `fbs_enterprise_pack` | 企业已获场景包（平台→企业的分发记录，含配额） | enterpriseId, packId, packQuota, usedQuota |
| `fbs_enterprise_member` | 企业成员关联表 | enterpriseId, userId, role, status |
| `fbs_member_pack` | 成员场景包授权（纯授权凭证，不存独立配额） | memberId, enterprisePackId, status |

> **配额模型**：企业级配额在 fbs_enterprise_pack（packQuota / usedQuota）。fbs_member_pack 仅作授权凭证，消费时检查企业级配额，不修改成员记录。
> **企业积分池不在本阶段范围内，延期。**

### 2.2 新增后端 API（内部 + 运营管理）

| 类别 | 数量 | 说明 |
|------|------|------|
| 运营管理 API | 5 | 企业 CRUD、企业包分发/撤销、成员管理 |
| 内部 API | 1 | 成员消费（hostType=ENTERPRISE） |

### 2.3 新增 BusinessService

| 类 | 职责 | 命名风格 |
|---|------|---------|
| `FbsEnterpriseBusinessService` | 企业组织管理 | 与 #1/#2 保持一致（Fbs 前缀） |
| `FbsEnterprisePackBusinessService` | 企业场景包分发 | 同上 |
| `FbsEnterpriseMemberBusinessService` | 企业成员管理 | 同上 |

### 2.4 原有模块扩展

- `FbsScenePack.ownerType`：增加 `2`（企业）类型
- `FbsSkillUsageRecord.hostType`：增加 `ENTERPRISE` 类型

---

## 三、Impact（影响范围）

### 3.1 受影响模块

| 模块 | 影响 |
|------|------|
| `WxFbsir-business` | 新增 fbs/enterprise 目录（entity/mapper/service/controller/dto） |
| `WxFbsir-admin` | 新增 sys_menu（企业中心菜单） |
| `WxFbsir-ui` | **本阶段不涉及前端，延期** |
| `WxFbsir-system` | 无变更 |

### 3.2 受影响现有表（ALTER）

| 表 | 变更 |
|---|------|
| `fbs_scene_pack` | `owner_type` 增加枚举值 `2`（企业） |
| `fbs_skill_usage_record` | `host_type` 增加 `ENTERPRISE` |

### 3.3 sys_user.enterprise_id 不在本阶段增加

用户与企业的关系**只通过 `fbs_enterprise_member` 表维护**，不修改 `sys_user` 表。
避免两个关系来源造成数据一致性问题。

### 3.4 不在本 OpenSpec 范围内（延期）

- 企业积分池（points_balance / 充值 / 流水记录）→ 延期
- 企业自建场景包（企业创建并分发给自己成员）→ 延期
- 成员自助申请/购买场景包 → 延期
- 企业 fail-open 策略 → 延期
- 前端页面 → 延期

---

## 四、MVP 验收标准

1. 平台管理员可在后台创建企业、查询企业列表、禁用企业
2. 平台管理员可将场景包分发（gift）给指定企业，设定 packQuota（配额数）
3. 平台管理员可管理企业的成员（添加/移除）
4. 企业成员使用场景包时，从 fbs_enterprise_pack.usedQuota 扣减（企业级），配额不足则返回失败；fbs_member_pack 仅作授权凭证不参与扣减
5. 企业禁用、成员已移除 → consume 路径 Fail-Closed
6. 所有 API 通过单元测试
7. 数据库脚本可重复执行（幂等）

---

## 五、关键设计决策（D 系列）

### D-1：企业积分池不在本阶段
- `fbs_enterprise.points_balance` 暂不添加
- `fbs_enterprise_points_record` 暂不添加
- 消费链路：只扣 fbs_enterprise_pack.usedQuota，不扣企业积分池

### D-2：用户与企业关系只通过 fbs_enterprise_member
- 不在 sys_user 表增加 enterprise_id 字段
- 成员身份查询统一走 fbs_enterprise_member 表

### D-3：配额模型 — 企业级单一真相源
- **配额扣减统一在 fbs_enterprise_pack 层面（packQuota / usedQuota）**
- fbs_member_pack 仅作"成员是否有资格使用该企业包"的授权凭证，**不存独立配额，不参与扣减计算**
- 查询成员配额时，remainQuota 实时计算：`fbs_enterprise_pack.packQuota - fbs_enterprise_pack.usedQuota`
- 原因：多成员场景下，如果每个成员都存 remainCount，多个成员消耗后无法保证与企业级 usedQuota 一致

### D-4：前端延期
- 阶段 7 标记 deferred，sys_menu SQL 作为后续预留（见 tasks.md 阶段 8）

### D-5：hostType 互不 fallback
- **hostType=ENTERPRISE**：强制走企业配额路径（Fail-Closed），不 fallback 到个人授权
- **hostType=WORKBUDDY**：走 OpenSpec #1 个人授权路径（扣个人积分），不受企业配额限制
- 同一个用户如需走个人权限，调用方应使用 hostType=WORKBUDDY
- 两套路径完全独立，互不 fallback

