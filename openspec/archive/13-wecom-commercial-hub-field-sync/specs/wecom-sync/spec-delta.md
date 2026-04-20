# 规范差异：wecom-sync

本文件包含对 `openspec/specs/platform-scene-pack-ops/spec.md` 的规范变更。

## ADDED 需求

### Requirement: commercial_hub 完整字段同步
WHEN 积分消费成功并触发 `syncCommercialHub()`,
系统 SHALL 将 commercial_hub 子表的全部 24 个字段写入企微智能表格。

#### Scenario: 个人用户消费完整同步
GIVEN 用户 userId=1001 消费场景包 packCode="bookwriter-genre-genealogy" 积分 100
AND 该场景包 packType=1（平台包）
AND hostType="WORKBUDDY"
AND usageRecordId="usr_20260418_001"
WHEN 调用 syncCommercialHub(CommercialHubSyncContext)
THEN 企微智能表格新增一条记录
AND record_id 为 UUID（文本格式）
AND record_type 为 "SKILL_USAGE"
AND corp_id 为空字符串（非企业用户无企业编码）
AND user_id 为 "1001"（文本格式，非裸 Long）
AND genre 为 "bookwriter-genre-genealogy"
AND event 为 "CONSUME"
AND delta 为 -100
AND balance_after 为剩余积分
AND credits_required 为 100
AND code_prefix 为空字符串（FbsAuthCode 无此字段）
AND code_hash 为空字符串（FbsAuthCode 无此字段）
AND code_type 为空字符串（无授权码）
AND redeem_target 为 "bookwriter-genre-genealogy"
AND request_id 为 "usr_20260418_001"
AND order_id 为 "usr_20260418_001"
AND trial_allowed 为 "false"（SINGLE_SELECT）
AND enterprise_only 为 "false"（SINGLE_SELECT）
AND source 为 "SKILL_API"
AND operator 为 "1001"
AND risk_flag 为空字符串
AND payload_json 包含消费参数
AND created_at 和 updated_at 为当前时间

#### Scenario: 企业用户消费完整同步
GIVEN 企业用户消费企业场景包 packCode="bookwriter-genre-startup"
AND 该场景包 packType=2（企业包）
AND hostType="ENTERPRISE"
AND 用户关联企业 enterpriseCode="ENT_abc123"
AND authCode="AC-GENEALOGY-001"
WHEN 调用 syncCommercialHub(CommercialHubSyncContext)
THEN 企微智能表格新增一条记录
AND corp_id 为 "ENT_abc123"（从 FbsEnterpriseMember → FbsEnterprise 关联查）
AND enterprise_only 为 "true"（SINGLE_SELECT）
AND source 为 "ENTERPRISE"
AND code_type 为授权码类型值

#### Scenario: 关联查询失败降级
GIVEN 用户消费场景包
AND 查询企业编码时 FbsEnterpriseMemberMapper 返回空列表
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
THEN 构建 CommercialHubSyncContext 包含 hostType="ENTERPRISE", pointsAmount=0（企业不扣积分）
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
AND pointsAmount 为 0（企业路径不扣个人积分）

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
THEN user_id 字段值为 [{"type":"text","text":"1001"}]
AND 不直接传 Long 类型

---

## MODIFIED 需求

### Requirement: syncCommercialHub 接口签名
**Previous**：`void syncCommercialHub(Long userId, String packCode, int pointsAmount, int remainPoints)` 仅接收 4 个参数。

WHEN 调用 syncCommercialHub 同步消费记录,
系统 SHALL 支持 CommercialHubSyncContext 上下文参数，包含全部字段映射所需的上下文数据。

#### Scenario: 新签名调用
GIVEN 完整的 CommercialHubSyncContext
WHEN 调用 syncCommercialHub(CommercialHubSyncContext)
THEN 系统使用上下文中的数据构建全部 24 字段的记录

#### Scenario: 旧签名兼容
GIVEN 调用旧 4 参数签名的 syncCommercialHub(userId, packCode, pointsAmount, remainPoints)
WHEN 内部转调新签名
THEN 构建 CommercialHubSyncContext，hostType 默认 "WORKBUDDY"，其他扩展字段为空/默认值
AND 核心字段（record_id, user_id, genre, delta, balance_after, credits_required, status, created_at）正常写入
AND 扩展字段写入默认值（enterprise_only="false", source="SKILL_API", trial_allowed="false"）

---

## 备注

- 智能表格字段结构不变，只补全后端写入映射
- entitlement 子表已完整（4 字段全部写入），不在本变更范围
- `code_prefix`/`code_hash`：FbsAuthCode 实体无此字段，写空字符串（智能表格字段预留）
- `corp_id`：从 `FbsEnterpriseMember.enterpriseId` → `FbsEnterprise.enterpriseCode` 关联查询
- `trial_allowed`/`enterprise_only`：commercial_hub 中是 SINGLE_SELECT（非 CHECKBOX），写入格式待确认
- `user_id`：智能表格中是 TEXT 类型，需文本包装而非直接传 Long
- risk_flag 暂固定空字符串，风控逻辑 P3 延期
- **⚠️ `consumeEnterprise` 路径当前完全没有调用 `syncCommercialHub`，需新增**
