# Spec Delta: add-skill-api-gateway

> **变更 ID**：05-add-skill-api-gateway
> **基于规范**：`specs/fbs-rights-foundation/spec.md` v1.0 + `specs/platform-scene-pack-ops/spec.md` v1.3

---

## ADDED Requirements

### Requirement: Skill API 网关

系统 SHALL 提供一组面向 FBS-BookWriter Skill 的 REST API（`/fbs/skill-api/**`），使用 API Key 认证（而非 JWT），让 Skill 脚本可以校验权益、扣减积分、查询场景包规则。

> **⚠️ 安全边界声明**：API Key + body.userId 模型存在天然越权风险——持有有效 API Key 即可伪造任意 userId。MVP 接受此风险，前提：（1）仅部署在受控 WorkBuddy 宿主环境；（2）不面向公网第三方开放；（3）生产环境强制 HTTPS。后续迭代加固：API Key 绑定 packCode 白名单、宿主签名 userId（HMAC-SHA256）、IP 白名单。

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
THEN  系统 SHALL 校验 API Key 有效性
AND   复用 SkillConsumeService.consume(userId, packCode, skillCode, usageRecordId, hostType, hostSessionId, authCode)
AND   **不传 pointsAmount**，后端按 packCode → pointsRuleCode → wx_points_rule.points_value 自动计算
AND   返回 ConsumeResult（success/failReason/pointsAmount/remainPoints）
```

#### Scenario: 使用记录两阶段模式

> **⚠️ 与 consume 互斥**：start/end 和 consume 是两种独立的消费模式，同一个 usageRecordId 只能走一种。现有 `SkillConsumeService.consume` 是自闭环的（自己 INSERT status=0 → 扣减 → UPDATE status=1），如果先用 start 插入 record，consume 会因 status=0 拒绝"正在处理中"；如果先 end 改成 status=1/2，consume 会幂等返回或拒绝。**选择逻辑：需要扣费 → consume；只需记录 → start/end。**

```text
GIVEN Skill 脚本携带有效 API Key
WHEN  调用 POST /fbs/skill-api/usage/start
THEN  系统 SHALL 先查 FbsSkillUsageRecordMapper.selectByRecordId(usageRecordId)
AND   按幂等语义处理：
  - 已存在且 status=0 → 返回已有记录（200），不重复创建
  - 已存在且 status=1 → 返回 409 "使用记录已成功结束"
  - 已存在且 status=2 → 返回 409 "使用记录已失败结束"
  - 不存在 → insertUsageRecord 创建新记录（status=0）
AND   返回 usageRecordId（String 类型，非 Long）

WHEN  调用 PUT /fbs/skill-api/usage/end/{usageRecordId}（usageRecordId 为 String）
THEN  系统 SHALL 复用 FbsSkillUsageRecordMapper.updateStatusByRecordId 更新状态
AND   status=1 成功 / status=2 失败
AND   **幂等语义**：
  - 记录不存在 → 404 "使用记录不存在"
  - status=0 → 更新为 1 或 2（只允许一次）
  - status=1 → 409 "使用记录已成功结束"
  - status=2 → 409 "使用记录已失败结束"
```

#### Scenario: 场景包规则查询

```text
GIVEN Skill 脚本携带有效 API Key 调用 POST /fbs/skill-api/scene-pack/query
WHEN  提交 packCode
THEN  系统 SHALL 返回场景包元信息：
  - packCode, packName, currentVersion, status, pointsRuleCode
  - contentSnapshot（原始 JSON 字符串，MVP 直接返回现有字段，不做二次结构化转换，不新建 ruleFileUrl / 文件下载机制）
```

#### Scenario: 用户信息查询

```text
GIVEN Skill 脚本携带有效 API Key 调用 POST /fbs/skill-api/user/info
WHEN  提交 userId
THEN  系统 SHALL 返回：
  - userId
  - pointsBalance（个人积分余额，复用 IPointsService.getUserPoints）
  - activatedPacks（已激活场景包列表，复用 FbsUserPackMapper.selectMyPacks）
AND   **不返回 T0-T3 用户层级**（当前仓库无此模型，如需实现另开 OpenSpec）
```

#### Scenario: API Key 无效或已禁用

```text
GIVEN Skill 脚本携带无效/已禁用 API Key 调用任意 /fbs/skill-api/** 接口
WHEN  请求到达
THEN  系统 SHALL 返回 401 + "API Key 无效" 或 403 + "API Key 已禁用"（INVALID→401, DISABLED→403）
```

#### Scenario: API Key 速率限制

```text
GIVEN API Key 的调用频率超过 rate_limit_per_min
WHEN  1 分钟内请求次数超限
THEN  系统 SHALL 返回 429 + "API 调用频率超限，请稍后重试"
```

---

### Requirement: API Key 管理

系统 SHALL 支持 API Key 的生成、查询、启用/禁用、删除，供运营人员管理 Skill 访问凭证。

#### Scenario: 生成 API Key

```text
GIVEN 管理员调用生成 API Key 接口
WHEN  提交 name（名称）+ packCode（关联场景包，可选）+ rateLimitPerMin（速率限制，默认 60）
THEN  系统 SHALL 生成唯一 API Key（前缀 fbs_ + 32位随机串）
AND   存储到 fbs_api_key 表（status=1 启用）
AND   明文存储 api_key 字段（#15 HMAC 签名方案依赖原文，不再迁移到 SHA-256 hash）
AND   返回 API Key（仅创建时可见，后续不可查询原文）
```

#### Scenario: API Key 列表查询

```text
GIVEN 管理员调用 API Key 列表接口
WHEN  提交分页请求
THEN  系统 SHALL 返回分页列表（api_key 脱敏显示，仅保留前 8 位 + ****）
AND   每条记录包含：id, apiKey(masked), name, packCode, rateLimitPerMin, status, createdTime
```

#### Scenario: 禁用/启用 API Key

```text
GIVEN 管理员调用启用/禁用接口
WHEN  提交 apiKeyId + status(0=禁用, 1=启用)
THEN  系统 SHALL 更新 fbs_api_key.status
AND   禁用后所有使用该 Key 的 Skill API 调用返回 403（SKILL_API_KEY_DISABLED）
```

#### Scenario: 删除 API Key

```text
GIVEN 管理员调用删除接口
WHEN  提交 apiKeyId
THEN  系统 SHALL 逻辑删除（del_flag=2）或物理删除该 API Key
AND   删除后所有使用该 Key 的 Skill API 调用返回 401（SKILL_API_KEY_INVALID）
```

---

### Requirement: Skill 端权益对接

FBS-BookWriter Skill SHALL 通过 fbs-rights-client.mjs 与后端 Skill API 网关对接，优先在线校验，离线时降级到本地缓存。

#### Scenario: 在线权益校验

```text
GIVEN Skill 运行在 WorkBuddy 宿主中且网络可达
WHEN  用户触发需要权益校验的操作（如激活场景包、开始写书）
THEN  Skill SHALL 调用 /fbs/skill-api/rights/check
AND   校验通过则继续操作
AND   校验失败则告知用户具体原因（中文）
```

#### Scenario: 离线降级

```text
GIVEN Skill 运行时后端不可达（网络故障/服务器宕机）
WHEN  fbs-rights-client.mjs 调用 API 超时或失败
THEN  Skill SHALL 按四级降级链工作：disk_cache → offline_cache → local_rule → no_pack
AND   明确告知用户"离线降级模式，权益信息可能不是最新"
```

#### Scenario: 积分扣减回调

```text
GIVEN Skill 完成一次需要积分扣减的操作（如写章节）
WHEN  操作完成后
THEN  Skill SHALL 调用 /fbs/skill-api/usage/consume 进行积分扣减
AND   不传 pointsAmount，由后端按场景包规则自动计算
AND   扣减失败（余额不足/网络异常）时记录到本地 .fbs/points.json
AND   下次在线时重试扣减
```

---

## MODIFIED Requirements

### Requirement: 安全配置扩展

> 修改 `specs/fbs-rights-foundation/spec.md` 安全章节

```text
GIVEN Spring Security 配置
WHEN  请求路径匹配 /fbs/skill-api/**
THEN  系统 SHALL 不要求 JWT 认证
AND   改由 FbsApiKeyAuthFilter 校验 X-FBS-API-Key Header
AND   FbsApiKeyAuthFilter 注册在 JwtAuthenticationTokenFilter 之前
AND   API Key 无效/缺失返回 401（SKILL_API_KEY_INVALID）；API Key 已禁用返回 403（SKILL_API_KEY_DISABLED）
AND   速率超限返回 429
```

### Requirement: 场景包规则查询适配

> 修改 `specs/platform-scene-pack-ops/spec.md` 场景包相关章节

```text
GIVEN Skill 调用 /fbs/skill-api/scene-pack/query
WHEN  后端返回 contentSnapshot 版本高于本地缓存版本
THEN  Skill SHALL 解析 contentSnapshot JSON
AND   更新本地 disk_cache
AND   保留旧版本作为 offline_cache
```
