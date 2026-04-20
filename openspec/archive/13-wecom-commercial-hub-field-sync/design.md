# OpenSpec #13：commercial_hub 字段补全

> **变更 ID**: `13-wecom-commercial-hub-field-sync`
> **状态**: 提案
> **创建**: 2026-04-18
> **依赖**: #7（写入引擎）+ #9（业务数据同步）

---

## 一、问题

`WecomBusinessSyncServiceImpl.buildCommercialHubRecord()` 只写入 10 个字段（6 文本 + 4 数字），智能表格 commercial_hub 有 24 个字段，**14 个字段始终为空**。

### 现有写入字段（10 个）

| 字段 | 类型 | 写入方式 |
|------|------|---------|
| record_id | TEXT | `[{"type":"text","text":"UUID"}]` |
| record_type | TEXT | `[{"type":"text","text":"SKILL_USAGE"}]` |
| genre | TEXT | `[{"type":"text","text":packCode}]` |
| event | TEXT | `[{"type":"text","text":"CONSUME"}]` |
| status | TEXT | `[{"type":"text","text":"SUCCESS"}]` |
| created_at | TEXT | `[{"type":"text","text":timestamp}]` |
| user_id | TEXT | ⚠️ 直接传 Long（应文本包装） |
| delta | NUMBER | 直接传 int |
| balance_after | NUMBER | 直接传 int |
| credits_required | NUMBER | 直接传 int |

### 缺失字段（14 个）

| 字段 | 智能表格类型 | 写入格式 |
|------|:---:|---------|
| corp_id | TEXT | `[{"type":"text","text":enterpriseCode}]` |
| code_prefix | TEXT | `[{"type":"text","text":""}]`（⚠️ 无数据源，写空） |
| code_hash | TEXT | `[{"type":"text","text":""}]`（⚠️ 无数据源，写空） |
| code_type | TEXT | `[{"type":"text","text":"1"/"2"}]`（从 FbsAuthCode.codeType） |
| redeem_target | TEXT | `[{"type":"text","text":packCode}]` |
| request_id | TEXT | `[{"type":"text","text":usageRecordId}]` |
| order_id | TEXT | `[{"type":"text","text":usageRecordId}]` |
| trial_allowed | SINGLE_SELECT | ⚠️ 需确认选项值格式 |
| enterprise_only | SINGLE_SELECT | ⚠️ 需确认选项值格式 |
| source | TEXT | `[{"type":"text","text":"SKILL_API"/"ENTERPRISE"}]` |
| operator | TEXT | `[{"type":"text","text":userId.toString()}]` |
| risk_flag | TEXT | `[{"type":"text","text":""}]` |
| payload_json | TEXT | `[{"type":"text","text":jsonString}]` |
| updated_at | TEXT | `[{"type":"text","text":timestamp}]` |

---

## 二、变更范围

### IN（本阶段做）

1. **改造 `buildCommercialHubRecord()`**：补全 14 个空字段写入
2. **扩展 `syncCommercialHub()` 签名**：传入额外上下文
3. **改造调用点 `SkillConsumeServiceImpl`**：
   - `consume()` 路径：传递扩展参数
   - ⚠️ **`consumeEnterprise()` 路径：新增同步调用（当前完全没有调用！）**
4. **修复 `user_id` 字段格式**：从 Long 改为文本包装
5. **单元测试**

### OUT（本阶段不做）

1. ~~entitlement 改动~~ → 已完整，不动
2. ~~新增智能表格字段/子表~~ → 表结构不变
3. ~~commercial_hub 批量历史回填~~ → 只同步增量
4. ~~code_prefix/code_hash 数据源~~ → FbsAuthCode 无此字段，写空字符串

---

## 三、技术设计

### 3.1 字段取值来源（修正版）

| 字段 | 取值 | 来源 | 备注 |
|------|------|------|------|
| corp_id | enterpriseCode 或空 | `FbsEnterpriseMember.enterpriseId` → `FbsEnterprise.enterpriseCode` | 非企业用户=空 |
| code_prefix | 空字符串 | ⚠️ `FbsAuthCode` 无此字段 | 智能表格预留，暂无数据 |
| code_hash | 空字符串 | ⚠️ `FbsAuthCode` 无此字段 | 智能表格预留，暂无数据 |
| code_type | codeType 值（1/2） | `FbsAuthCode.codeType`（authCode 非空时查） | 无授权码=空 |
| redeem_target | packCode | ctx.packCode | 与 genre 相同，明确语义 |
| request_id | usageRecordId | ctx.usageRecordId | |
| order_id | usageRecordId | ctx.usageRecordId | MVP 同 request_id |
| trial_allowed | "true"/"false" | SINGLE_SELECT，需确认选项 | MVP 固定 "false" |
| enterprise_only | "true"/"false" | SINGLE_SELECT，需确认选项 | packType==2 → "true" |
| source | "SKILL_API"/"ENTERPRISE" | ctx.hostType 映射 | |
| operator | userId.toString() | ctx.userId | |
| risk_flag | 空字符串 | 固定 | P3 延期 |
| payload_json | JSON 字符串 | 消费请求体序列化 | |
| updated_at | 时间戳 | 同 created_at | |

### 3.2 corp_id 查询路径

```
userId → FbsEnterpriseMemberMapper.selectActiveByUserId(userId)
       → FbsEnterpriseMember.enterpriseId
       → FbsEnterpriseMapper.selectById(enterpriseId)
       → FbsEnterprise.enterpriseCode  (如 "ENT_abc123")
```

⚠️ `WecomBusinessSyncServiceImpl` 目前没有注入 `FbsEnterpriseMemberMapper` 和 `FbsEnterpriseMapper`，需要新增。

### 3.3 authCode 关联查询路径

```
authCode → FbsAuthCodeMapper（需确认是否有 selectByCode 方法）
         → FbsAuthCode.codeType (1=场景包权益码, 2=通用授权码)
         → code_prefix/code_hash：FbsAuthCode 无此字段，写空字符串
```

### 3.4 SINGLE_SELECT 字段写入格式

`trial_allowed` 和 `enterprise_only` 是 `FIELD_TYPE_SINGLE_SELECT`，需确认：
- 写入格式是 `[{"type":"text","text":"true"}]` 还是 `[{"type":"enum","id":"xxx"}]`
- 如果选项不存在于选项列表中，是否自动创建

**MVP 策略**：先用文本格式写入 `"true"/"false"`，如果写入失败再调整。

### 3.5 签名扩展

```java
// 原签名
void syncCommercialHub(Long userId, String packCode, int pointsAmount, int remainPoints);

// 新签名
void syncCommercialHub(CommercialHubSyncContext context);
```

### 3.6 CommercialHubSyncContext（新增 DTO）

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
    // getter/setter/builder
}
```

### 3.7 consumeEnterprise 新增同步调用

当前 `consumeEnterprise()` 路径完全没有调用 `syncCommercialHub`。需要在步骤 8（更新 usage_record status=1）之后新增：

```java
// ---- 步骤 9：同步到企微智能表格（commercial_hub）----
try {
    if (wecomBusinessSyncService != null) {
        CommercialHubSyncContext ctx = CommercialHubSyncContext.builder()
            .userId(userId)
            .packCode(packCode)
            .pointsAmount(0)          // 企业路径不扣积分
            .remainPoints(remainQuota) // 企业路径用剩余配额
            .hostType("ENTERPRISE")
            .usageRecordId(usageRecordId)
            .packId(pack.getId())
            .authCode(null)           // 企业路径无授权码
            .pointsRuleCode(pack.getPointsRuleCode())
            .packType(pack.getPackType() != null ? pack.getPackType() : 0)
            .build();
        wecomBusinessSyncService.syncCommercialHub(ctx);
    }
} catch (Exception e) {
    log.warn("同步企业消费到企微失败 userId={}, packCode={}", userId, packCode, e);
}
```

### 3.8 user_id 字段格式修复

```java
// 当前（错误）：数字格式
values.put("user_id", userId);  // Long → 智能表格是 TEXT 类型

// 修正：文本包装
values.put("user_id", Arrays.asList(Map.of("type", "text", "text", String.valueOf(userId))));
```

---

## 四、任务清单

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 1 | 新增 `CommercialHubSyncContext` | `fbs/dto/business/CommercialHubSyncContext.java` | DTO |
| 2 | 扩展 `WecomBusinessSyncService` 签名 | `fbs/service/WecomBusinessSyncService.java` | 新增重载方法 |
| 3 | 改造 `WecomBusinessSyncServiceImpl` | `fbs/service/impl/WecomBusinessSyncServiceImpl.java` | 补全 14 字段 + 关联查询 + user_id 修复 |
| 4 | 改造 `SkillConsumeServiceImpl.consume()` | `fbs/service/impl/SkillConsumeServiceImpl.java` | 传 context |
| 5 | 新增 `SkillConsumeServiceImpl.consumeEnterprise()` 同步调用 | `fbs/service/impl/SkillConsumeServiceImpl.java` | ⚠️ 当前完全没有！ |
| 6 | 单元测试 | `test/.../WecomBusinessSyncServiceImplTest.java` | 全字段 + 关联查询兜底 |

---

## 五、验收标准

- [ ] commercial_hub 新写入记录包含全部 24 字段
- [ ] P1 字段有实际值：corp_id, code_type, redeem_target, request_id, enterprise_only, source, updated_at
- [ ] code_prefix/code_hash 写空字符串（无数据源）
- [ ] user_id 字段用文本格式写入
- [ ] `consumeEnterprise` 路径也同步到 commercial_hub
- [ ] 关联查询失败时降级为空值，不阻塞同步
- [ ] 旧 4 参数签名向后兼容
- [ ] 单元测试通过
