# OpenSpec #3 实施任务清单

```json
[
  {
    "number": 1,
    "category": "阶段 1：数据库架构",
    "task": "设计并编写 4 张新表的 SQL 迁移脚本（含 2 个 ALTER）",
    "steps": [
      { "step": "确定 fbs_enterprise 表字段（enterpriseName, contact, status，暂不包含 points_balance）", "completed": false },
      { "step": "确定 fbs_enterprise_pack 表字段（enterpriseId, packId, packQuota, usedQuota, expiryTime）", "completed": false },
      { "step": "确定 fbs_enterprise_member 表字段（enterpriseId, userId, role, status）", "completed": false },
      { "step": "确定 fbs_member_pack 表字段（memberId, enterprisePackId, packId, status — 纯授权凭证，无配额字段）", "completed": false },
      { "step": "ALTER fbs_scene_pack.owner_type 增加枚举值 2（企业）", "completed": false },
      { "step": "ALTER fbs_skill_usage_record.host_type 增加 ENTERPRISE", "completed": false },
      { "step": "验证 SQL 可重复执行（存储过程或 DROP + CREATE）", "completed": false }
    ],
    "passes": false
  },
  {
    "number": 2,
    "category": "阶段 2：实体类与 Mapper",
    "task": "新增 4 个实体类 + 4 个 Mapper 接口 + 4 个 Mapper XML",
    "steps": [
      { "step": "FbsEnterprise.java（含 @TableName + 字段注解）", "completed": false },
      { "step": "FbsEnterprisePack.java（企业包分发关联）", "completed": false },
      { "step": "FbsEnterpriseMember.java（企业成员关联）", "completed": false },
      { "step": "FbsMemberPack.java（成员场景包授权）", "completed": false },
      { "step": "FbsEnterpriseMapper.java + XML（CRUD + 分页查询）", "completed": false },
      { "step": "FbsEnterprisePackMapper.java + XML（按企业/按包查询）", "completed": false },
      { "step": "FbsEnterpriseMemberMapper.java + XML（成员列表查询）", "completed": false },
      { "step": "FbsMemberPackMapper.java + XML（成员授权查询）", "completed": false }
    ],
    "passes": false
  },
  {
    "number": 3,
    "category": "阶段 3：FbsEnterpriseBusinessService（企业组织管理）",
    "task": "实现企业组织管理 BusinessService + Controller + DTO",
    "steps": [
      { "step": "EnterpriseDTO（创建/更新请求 DTO）", "completed": false },
      { "step": "EnterprisePageDTO（分页查询请求 DTO）", "completed": false },
      { "step": "EnterpriseDetailDTO（详情响应 DTO）", "completed": false },
      { "step": "IFbsEnterpriseBusinessService 接口定义（create, update, getDetail, getPage, disable）", "completed": false },
      { "step": "FbsEnterpriseBusinessServiceImpl 实现（含字段校验、幂等检查）", "completed": false },
      { "step": "FbsEnterpriseController（运营管理 API：分页列表、详情、新建、编辑、禁用）", "completed": false },
      { "step": "FbsEnterpriseBusinessServiceTest（create/getDetail/page/disable 各 1 个用例）", "completed": false }
    ],
    "passes": false
  },
  {
    "number": 4,
    "category": "阶段 4：FbsEnterprisePackBusinessService（企业场景包分发）",
    "task": "实现企业场景包分发 BusinessService + Controller + DTO",
    "steps": [
      { "step": "EnterprisePackGrantDTO（平台向企业分发场景包请求 DTO，含 packQuota）", "completed": false },
      { "step": "EnterprisePackDetailDTO（企业包详情响应 DTO，含 packQuota/usedQuota）", "completed": false },
      { "step": "IFbsEnterprisePackBusinessService 接口定义（grantPack, revokePack, getPage, getDetail, getByEnterprise）", "completed": false },
      { "step": "FbsEnterprisePackBusinessServiceImpl 实现", "completed": false },
      { "step": "  - grantPack：幂等检查（已授权→返回已有；已撤销→重新授权）、packQuota 写入 fbs_enterprise_pack", "completed": false },
      { "step": "  - 自动为所有企业成员在 fbs_member_pack 生成授权记录（纯授权凭证，无配额字段）", "completed": false },
      { "step": "  - revokePack：将 fbs_enterprise_pack 状态改为已撤销，级联成员授权为已撤销", "completed": false },
      { "step": "FbsEnterprisePackController（运营管理 API）", "completed": false },
      { "step": "FbsEnterprisePackBusinessServiceTest（grant/revoke/page 各 1 个用例）", "completed": false }
    ],
    "passes": false
  },
  {
    "number": 5,
    "category": "阶段 5：FbsEnterpriseMemberBusinessService（企业成员管理）",
    "task": "实现企业成员管理 BusinessService + Controller + DTO",
    "steps": [
      { "step": "EnterpriseMemberAddDTO（添加成员请求 DTO）", "completed": false },
      { "step": "EnterpriseMemberDetailDTO（成员详情响应 DTO）", "completed": false },
      { "step": "IFbsEnterpriseMemberBusinessService 接口定义（addMember, removeMember, getMemberPage, getMemberPackList）", "completed": false },
      { "step": "FbsEnterpriseMemberBusinessServiceImpl 实现", "completed": false },
      { "step": "  - addMember：检查用户存在、检查未重复添加（status=1 不可重复）", "completed": false },
      { "step": "  - 自动继承企业当前所有已授权场景包（fbs_member_pack 批量写入授权记录，无配额字段）", "completed": false },
      { "step": "  - removeMember：将成员状态改为已移除（status=2），成员 pack 记录保留不级联删除", "completed": false },
      { "step": "FbsEnterpriseMemberController（运营管理 API）", "completed": false },
      { "step": "FbsEnterpriseMemberBusinessServiceTest（add/remove/page 各 1 个用例）", "completed": false }
    ],
    "passes": false
  },
  {
    "number": 6,
    "category": "阶段 6：成员消费扣配额（集成 OpenSpec #1）",
    "task": "修改 SkillConsumeService，支持企业成员消费场景包时扣企业包配额",
    "steps": [
      { "step": "RightsCheckServiceImpl.comprehensiveCheck：增加企业成员路径（查 fbs_enterprise_member → fbs_member_pack）", "completed": false },
      { "step": "SkillConsumeServiceImpl.consume：增加 hostType=ENTERPRISE 路径（与 hostType=WORKBUDDY 并行，互不 fallback）", "completed": false },
      { "step": "  - 企业成员 + hostType=ENTERPRISE：查 fbs_enterprise_pack，检查 usedQuota < packQuota", "completed": false },
      { "step": "  - usedQuota++（企业级），不修改 fbs_member_pack", "completed": false },
      { "step": "  - 配额不足（usedQuota >= packQuota）：返回 FAIL，failReason=\"企业配额已用尽，请联系管理员\"", "completed": false },
      { "step": "  - 不操作用户个人积分（sys_user.points 不变），不走 wx_points_rule", "completed": false },
      { "step": "  - hostType=ENTERPRISE 时写 fbs_skill_usage_record.host_type=ENTERPRISE", "completed": false },
      { "step": "RightsCheckServiceTest：增加企业成员路径测试用例", "completed": false },
      { "step": "SkillConsumeServiceTest：增加企业成员消费测试用例（配额充足/配额不足/企业未获包/企业禁用）", "completed": false }
    ],
    "passes": false
  },
  {
    "number": 7,
    "category": "阶段 7（延期）：前端页面",
    "task": "企业中心 Vue 页面 — 延期至后续阶段，本阶段不做",
    "steps": [
      { "step": "src/views/business/fbs/enterprise/index.vue — 延期（out of scope）", "completed": false },
      { "step": "src/views/business/fbs/enterprise/pack/index.vue — 延期（out of scope）", "completed": false },
      { "step": "src/views/business/fbs/enterprise/member/index.vue — 延期（out of scope）", "completed": false }
    ],
    "passes": false,
    "deferred": true
  },
  {
    "number": 8,
    "category": "阶段 8：系统集成与文档",
    "task": "sys_menu 菜单 SQL + API 文档 + 归档准备",
    "steps": [
      { "step": "编写 sys_menu SQL（企业中心父菜单 + 3 个子菜单，需替换 @FBS_PARENT_ID）", "completed": false },
      { "step": "更新 FBS-BUSINESS-API.md，补充企业侧 API 文档（不含前端部分）", "completed": false },
      { "step": "在 INTEGRATION-TEST-GUIDE.md 补充企业成员消费集成测试步骤", "completed": false },
      { "step": "执行 mvn test -pl WxFbsir-business -am 全量单元测试", "completed": false },
      { "step": "执行 sys_menu SQL（替换占位符后）", "completed": false }
    ],
    "passes": false
  }
]
```

---

## 关键设计决策记录

### D-1：企业积分池不在本阶段
- `fbs_enterprise.points_balance` 暂不添加
- `fbs_enterprise_points_record` 暂不添加
- 消费链路：只扣 fbs_enterprise_pack.usedQuota，不扣企业积分池

### D-2：用户与企业关系只通过 fbs_enterprise_member
- 不在 sys_user 表增加 enterprise_id 字段
- 成员身份查询统一走 fbs_enterprise_member 表

### D-3：配额模型 — 企业级单一真相源
- **配额扣减统一在 fbs_enterprise_pack 层面（packQuota / usedQuota）**
- fbs_member_pack 仅作"成员是否有资格使用该企业包"的授权凭证，不存独立配额，不参与扣减计算
- 查询时实时计算 remainQuota = packQuota - usedQuota

### D-4：前端延期
- 阶段 7 已标记 deferred，sys_menu SQL 作为后续预留
- 后续可单独开变更提案处理前端

### D-5：hostType 互不 fallback
- hostType=ENTERPRISE 强制走企业配额路径，不 fallback 到个人授权
- hostType=WORKBUDDY 走 OpenSpec #1 个人授权路径（扣个人积分）
- 两套路径完全独立，互不 fallback

### D-6：禁用企业不批量修改成员 pack 状态
- 禁用企业时，仅修改 fbs_enterprise.status
- 不批量修改 fbs_member_pack 状态
- 消费时通过企业状态 fail-closed
