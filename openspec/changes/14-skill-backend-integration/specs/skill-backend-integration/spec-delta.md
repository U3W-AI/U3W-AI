# Spec Delta: Skill 后端对接改造

> **Change ID**: `14-skill-backend-integration`
> **基线规范**: `platform-scene-pack-ops/spec.md` v1.8
>
> **与 #12 的关系**：本 spec-delta 中"Skill 后端 API 调用封装"、"Skill 乐包余额后端同步"、"Skill 会员验证 API 域名可配置"包含 #12 Skill 端改动的重做；"Skill 行为积分上报"、"Skill API Key 配置管理"为 #14 新增。后端侧仅新增 `POST /points/earn`，`/user/info` 的 `activatedPacks` 已由 #12 实现。

---

## ADDED Requirements

### Requirement: Skill 行为积分上报

WHEN Skill 端发生正向积分事件（完章+10、完书+50、质检+3、首次安装+100、每日登录+5）,
系统 SHALL 提供 `POST /fbs/skill-api/points/earn` 接口接收上报。

#### Scenario: 行为积分上报成功

```text
GIVEN Skill 脚本携带有效 API Key 调用 POST /fbs/skill-api/points/earn
AND   提交 { source, amount, usageRecordId }
AND   source 为 CREDIT_SOURCES 中的正向来源 key（chapter_done / book_complete / quality_pass / first_install / daily_login / s6_transform / release_ready）
AND   source 不包含消耗类（scene_pack_use）和手动类（manual），这两类不应通过 Skill API 上报
AND   source 直接映射为后端 ruleCode（1:1，命名一致）
AND   amount > 0
AND   usageRecordId 为全局唯一字符串（幂等 key，由 Skill 端生成：addCredits 用 UUID v4，checkDailyLogin 用 DL_{userId}_{date}，checkFirstInstall 用 FI_{userId}）
WHEN  后端处理请求
THEN  系统 SHALL 调用 IPointsService.changePoints(userId, ruleCode=source, changeAmount=amount, scenePackId=null, usageRecordId, eventId=usageRecordId) 增加积分
AND   写入 fbs_skill_usage_record（host_type=WORKBUDDY, status=1）
AND   幂等由 wx_points_record.uk_event_id（eventId=usageRecordId）保证，复用 #10 幂等模型
AND   返回 { success: true, pointsAmount, remainPoints, usageRecordId }
AND   remainPoints 为用户当前 sys_user.points 余额
```

#### Scenario: 行为积分幂等（重复上报）

```text
GIVEN eventId = usageRecordId 已命中 wx_points_record.uk_event_id（#10 幂等模型）
WHEN  再次调用 POST /fbs/skill-api/points/earn
THEN  系统 SHALL 由 changePoints(eventId) 幂等返回已有余额（不重复加积分）
AND   返回 { success: true, pointsAmount: 0, remainPoints: 当前余额, usageRecordId }
AND   不写入新的 fbs_skill_usage_record
```

#### Scenario: 行为积分为空或无效

```text
GIVEN amount ≤ 0 或 source 不在白名单中
WHEN  调用 POST /fbs/skill-api/points/earn
THEN  系统 SHALL 返回 400
AND   错误消息为 "积分数量必须大于0" 或 "无效的积分来源"
```

#### Scenario: API Key 无效

```text
GIVEN 请求携带无效 API Key
WHEN  调用 POST /fbs/skill-api/points/earn
THEN  系统 SHALL 返回 401 SKILL_API_KEY_INVALID
AND   不增加积分
```

---

### Requirement: Skill API Key 配置管理

WHEN Skill 端需要与后端通信,
系统 SHALL 提供 API Key 配置管理模块（`api-key-config.mjs`）读取和管理 API Key。

#### Scenario: 从环境变量读取 API Key

```text
GIVEN 环境变量 FBS_API_KEY 存在且格式有效（fbs_ 前缀 + 32位随机串）
WHEN  调用 getApiKey()
THEN  返回完整 API Key 字符串
AND   isBackendConfigured() 返回 true
```

#### Scenario: 从配置文件读取 API Key

```text
GIVEN 环境变量 FBS_API_KEY 不存在
AND   ~/.fbs/config.json 存在且包含 { "FBS_API_KEY": "fbs_xxx" }
WHEN  调用 getApiKey()
THEN  返回配置文件中的 API Key
AND   isBackendConfigured() 返回 true
```

#### Scenario: 无 API Key 时纯本地模式

```text
GIVEN 环境变量和配置文件均无 FBS_API_KEY
WHEN  调用 isBackendConfigured()
THEN  返回 false
AND   所有后端调用跳过，使用本地账本
AND   日志输出「纯本地模式：未配置 API Key」
```

#### Scenario: API Base URL 可配置

```text
GIVEN 环境变量 FBS_API_BASE_URL 存在
WHEN  调用 getApiBaseUrl()
THEN  返回该环境变量值
AND IF 环境变量不存在
THEN  返回默认值 "https://api.u3w.com"
```

---

### Requirement: Skill 后端 API 调用封装

WHEN Skill 端需要调用后端 Skill API,
系统 SHALL 提供 `backend-api.mjs` 模块封装所有后端调用。

#### Scenario: 查询用户信息

```text
GIVEN isBackendConfigured() 为 true
WHEN  调用 fetchUserInfo()
THEN  发起 POST /fbs/skill-api/user/info（携带 X-FBS-API-Key Header）
AND   5s 超时控制
AND   成功返回 { userId, pointsBalance, activatedPacks: [{ packId, packCode, packName, packStatus, status, expiresAt }] }
AND   失败返回 null（触发调用方 fallback）
```

#### Scenario: 后端扣减积分

```text
GIVEN isBackendConfigured() 为 true
WHEN  调用 consumeCredits({ packCode, skillCode, usageRecordId })
THEN  发起 POST /fbs/skill-api/usage/consume（携带 X-FBS-API-Key Header）
AND   成功返回 { success: true, pointsAmount, remainPoints }
AND   失败返回 null（触发调用方 fallback）
```

#### Scenario: 行为积分上报（fire-and-forget）

```text
GIVEN isBackendConfigured() 为 true
WHEN  调用 earnPoints({ source, amount, usageRecordId })
THEN  发起 POST /fbs/skill-api/points/earn（携带 X-FBS-API-Key Header）
AND   不等待响应（fire-and-forget）
AND   失败仅写 stderr warn 日志
AND   不影响本地 addCredits 流程
```

#### Scenario: 后端不可达

```text
GIVEN 后端 API 返回网络错误或超时
WHEN  调用任意 backend-api 函数
THEN  返回 null
AND   输出 stderr warn 日志（含错误类型）
AND   不抛异常到上层
```

---

### Requirement: Skill 乐包余额后端同步

WHEN Skill 端查询或操作乐包余额,
系统 SHALL 优先后端 API，本地账本作为离线兜底。

#### Scenario: 在线查询余额

```text
GIVEN isBackendConfigured() 为 true 且网络可用
WHEN  调用 getBalance()
THEN  优先调用 backend-api.fetchUserInfo()
AND   成功时返回 data.pointsBalance
AND   同时更新本地账本余额（_syncLocalLedger）
```

#### Scenario: 离线查询余额

```text
GIVEN isBackendConfigured() 为 false 或后端不可达
WHEN  调用 getBalance()
THEN  返回本地账本 _readLedgerRaw().balance
AND   不发起任何网络请求
```

#### Scenario: 在线扣减积分

```text
GIVEN isBackendConfigured() 为 true 且网络可用
WHEN  调用 deductCredits(source, amount, note)
THEN  优先调用 backend-api.consumeCredits()
AND   成功时同步更新本地账本余额
AND   写本地流水日志（event: 'deduct', mode: 'backend'）
AND   返回后端返回的 remainPoints
```

#### Scenario: 离线扣减积分

```text
GIVEN isBackendConfigured() 为 false 或后端不可达
WHEN  调用 deductCredits(source, amount, note)
THEN  使用本地账本扣减（与改造前行为一致）
AND   写本地流水日志（event: 'deduct', mode: 'local'）
AND   返回本地新余额
```

#### Scenario: 后端返回余额不足

```text
GIVEN isBackendConfigured() 为 true 且网络可用
AND   后端 /usage/consume 返回余额不足（业务失败）
WHEN  调用 deductCredits(source, amount, note)
THEN  不执行本地扣减（不 fallback）
AND   throw Error（与当前本地余额不足行为一致）
```

#### Scenario: 行为积分上报

```text
GIVEN isBackendConfigured() 为 true
WHEN  调用 addCredits(source, amount, note)
THEN  本地账本先执行增加（保持现有逻辑）
AND   异步调用 backend-api.earnPoints()（fire-and-forget）
AND   本地流水日志标记 mode: 'backend_pending'
AND   不等待后端结果，直接返回本地新余额
```

---

### Requirement: Skill 会员验证 API 域名可配置

WHEN Skill 端验证会员身份或激活码,
系统 SHALL 使用可配置的 API 域名。

#### Scenario: 使用配置的 API Base URL

```text
GIVEN FBS_API_BASE_URL 环境变量已设置为 "https://custom-api.example.com"
WHEN  调用 verifyMember() 或 verifyActivationCode()
THEN  fetch 请求的目标 URL 使用配置的 base URL
AND   不使用硬编码的 https://api.u3w.com
```

#### Scenario: 无配置时使用默认域名

```text
GIVEN FBS_API_BASE_URL 环境变量未设置
WHEN  调用 verifyMember() 或 verifyActivationCode()
THEN  使用默认域名 https://api.u3w.com
AND   行为与改造前完全一致
```

---

## MODIFIED Requirements

### Requirement: 用户信息查询（已由 #12 实现激活包列表）

> **注**：`POST /fbs/skill-api/user/info` 已返回 `activatedPacks`（由 OpenSpec #12 实现），
> 此处仅记录 Skill 端需感知的返回值结构，不再需要后端改动。

WHEN Skill 脚本携带有效 API Key 调用 POST /fbs/skill-api/user/info,
系统 SHALL 返回用户积分余额 + 已激活场景包列表。

#### Scenario: 查询用户信息（含已激活场景包）

```text
GIVEN Skill 脚本携带有效 API Key 调用 POST /fbs/skill-api/user/info
WHEN  提交 userId（可选，不传时从 API Key 反查）
THEN  系统 SHALL 返回：
  - userId: Long
  - pointsBalance: Integer（sys_user.points）
  - activatedPacks: Array<{ packId, packCode, packName, packStatus, status, expiresAt }>
AND   activatedPacks 从 fbs_user_pack JOIN fbs_scene_pack 查询
AND   仅返回 status=1（有效）的权益
AND   packStatus 为场景包上架状态（0=草稿, 1=已发布, 2=已下架）
AND   ⚠️ 当 fbs_scene_pack 记录不存在时，packCode/packName/packStatus 字段可能缺失（key 不存在而非 null），Skill 端须防御性读取（pack.packCode ?? null）
```

---

## REMOVED Requirements

无移除的需求。
