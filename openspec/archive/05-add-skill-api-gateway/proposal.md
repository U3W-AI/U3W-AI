# OpenSpec #5 Proposal: WorkBuddy / FBS-BookWriter Skill 对接

> **Change ID**: `add-skill-api-gateway`
> **阶段**: Skill API 网关（Skill ↔ 后端权益系统对接）
> **日期**: 2026-04-11

---

## 一、Why（为什么做）

白皮书 FBS 三侧架构中，用户侧权益已实现（OpenSpec #4），但 **Skill 端无法访问后端权益系统**：

| 问题 | 现状 |
|------|------|
| 内部 API 需 JWT | `/fbs/internal/**` 要求用户登录态，Skill 是 Node.js 脚本无会话 |
| Skill 缺少权益校验 | FBS-BookWriter 无法实时查询用户是否有某场景包权益 |
| Skill 缺少积分扣减 | 后端有 SkillConsumeService，但 Skill 无法调用 |
| 使用记录无法回传 | Skill 端只在本地 `.fbs/points.json` 记录，后端无感知 |

**核心矛盾**：后端有完整的权益/积分/记录基础设施，但 Skill 访问不到。

---

## 二、What Changes（做什么）

### 2.1 新增：Skill API 网关（`/fbs/skill-api/**`）

一组面向 Skill 的 REST API，使用 **API Key 认证**（而非 JWT），让 Skill 脚本可以：

| # | 方法 | 路径 | 说明 | 优先级 |
|---|------|------|------|--------|
| 1 | `POST` | `/fbs/skill-api/rights/check` | 校验用户是否有指定场景包的权益 | P0 |
| 2 | `POST` | `/fbs/skill-api/usage/consume` | 一次性完成"校验→扣减→记录" | P0 |
| 3 | `POST` | `/fbs/skill-api/usage/start` | 开始使用记录（两阶段模式） | P0 |
| 4 | `PUT` | `/fbs/skill-api/usage/end/{usageRecordId}` | 结束使用记录 | P0 |
| 5 | `POST` | `/fbs/skill-api/scene-pack/query` | 查询场景包元信息+规则 | P1 |
| 6 | `POST` | `/fbs/skill-api/user/info` | 查询用户福分余额+已激活场景包 | P1 |

### 2.2 认证方案

```
Header: X-FBS-API-Key: <api_key>
Body:   userId + packCode [+ authCode]
```

- **API Key**：由后端生成，配置在 Skill 的 `_plugin_meta.json` 中，每个部署实例一个
- **userId**：从宿主环境获取（WorkBuddy 提供 `process.env.WB_USER_ID`）
- **authCode**：仅在权益校验场景需要

#### ⚠️ 安全边界声明（必须遵守）

> **API Key + userId 模型存在天然越权风险**：持有有效 API Key 的调用方可以伪造任意 userId。
>
> **MVP 阶段接受此风险**，前提条件：
> 1. **仅部署在受控宿主环境**：API Key 仅配置在 WorkBuddy 宿主内，不面向公网第三方开放
> 2. **不对外分发**：API Key 不通过公开文档/SDK 分发，仅管理员手动配置
> 3. **生产环境强制 HTTPS**：防止 API Key 被网络嗅探
> 4. **后续迭代加固**（非 MVP）：
>    - API Key 绑定 packCode 白名单（每个 Key 只能访问指定场景包的接口）
>    - 宿主签名 userId（HMAC-SHA256(apiKey, userId + timestamp)）
>    - IP 白名单限制
>    - ~~API Key 改为 SHA-256 hash 存储~~（已取消——#15 HMAC 签名依赖明文）
>
> **如果不接受此风险**，则不应发布此 MVP，等待加固方案完成后再上线。

### 2.3 新增：API Key 管理

- `fbs_api_key` 表：存储 API Key + 关联场景包 + 速率限制
- **存明文**（api_key 字段直接存储原文），创建时返回完整 Key（仅此一次），后续查询脱敏显示前8位+`****`
- ~~**后续迭代**改为存 SHA-256 hash~~（已取消——#15 HMAC 签名依赖明文，不再迁移到 hash）
- SecurityConfig 放行 `/fbs/skill-api/**`，改用自定义 Filter 校验 API Key

### 2.4 新增：Skill 端对接模块

- `scripts/wecom/lib/fbs-rights-client.mjs`：封装 API Key 认证 + 5 个接口调用
- SKILL.md 场景包激活流程改为优先调用后端 API，离线时降级到本地缓存

### 2.5 复用已有能力

| 已有模块 | 复用方式 | 实际类名 |
|----------|----------|----------|
| `RightsCheckService.comprehensiveCheck` | `/skill-api/rights/check` 直接调用 | ✅ 已验证 |
| `SkillConsumeService.consume` | `/skill-api/usage/consume` 直接调用 | ✅ 已验证，签名：`consume(userId, packCode, skillCode, usageRecordId, hostType, hostSessionId, authCode)` |
| `FbsSkillUsageRecordMapper` | 两阶段使用记录 start/end | ✅ 已验证（非 FbsUsageRecordMapper） |
| `FbsScenePackMapper` | 场景包查询 | ✅ 已验证 |
| `IPointsService` | 用户福分余额查询 | ✅ 已验证：`IPointsService.getUserPoints(Long userId)` 返回 Integer，内部调用 `PointsMapper.getUserPoints` → `SELECT IFNULL(points,0) FROM sys_user WHERE user_id=?` |

### 2.6 不复用/不存在的（明确声明）

| 项目 | 说明 |
|------|------|
| T0-T3 用户层级 | **当前仓库不存在**。user/info 仅返回 userId + 福分余额 + 已激活场景包列表。T0-T3 如需实现，须另开 OpenSpec |
| ruleFileUrl | **当前仓库不存在**。scene-pack/query 返回 contentSnapshot（JSON字符串）+ currentVersion，MVP 不新建文件下载机制 |
| FbsUsageRecordMapper | **不存在**。实际是 `FbsSkillUsageRecordMapper` |

---

## 三、API 详细设计

### 3.0 通用约定

| 约定项 | 说明 |
|--------|------|
| 认证方式 | 所有 `/fbs/skill-api/**` 请求必须携带 `X-FBS-API-Key` Header |
| 用户标识 | 请求 Body 中必须包含 `userId`（从宿主环境获取） |
| 响应格式 | 统一 `AjaxResult`（code/msg/data），与内部 API 一致 |
| 速率限制 | 每个 API Key 默认 60 次/分钟，超出返回 429 |
| usageRecordId | 全链路为 **String** 类型（非 Long），对应 WorkBuddy taskId 或 UUID |

### 3.1 `POST /fbs/skill-api/rights/check` — 权益校验

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | Long | ✅ | 用户ID（宿主环境获取） |
| `packCode` | String | ✅ | 场景包编码 |
| `authCode` | String | 否 | 授权码（个人包需要） |
| `hostType` | String | 否 | 宿主类型（默认 WORKBUDDY） |

**响应**：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "pass": true,
    "failReason": null,
    "packId": 1,
    "pointsRuleCode": null,
    "pointsAmount": 0
  }
}
```

**内部调用**：`RightsCheckService.comprehensiveCheck(userId, packCode, authCode, hostType, usageRecordId)`

### 3.2 `POST /fbs/skill-api/usage/consume` — 一次性消费

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | Long | ✅ | 用户ID |
| `packCode` | String | ✅ | 场景包编码 |
| `authCode` | String | 否 | 授权码 |
| `skillCode` | String | ✅ | Skill 编码（如 "bookwriter"） |
| `usageRecordId` | String | ✅ | 幂等键（调用方生成，UUID 或 taskId） |
| `hostType` | String | 否 | 宿主类型（默认 WORKBUDDY） |

**⚠️ 不传 pointsAmount**：扣减金额由后端按 `packCode → pointsRuleCode → wx_points_rule.points_value` 自动计算，客户端无法指定。

**内部调用**：`SkillConsumeService.consume(userId, packCode, skillCode, usageRecordId, hostType, hostSessionId, authCode)`

**响应**：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": true,
    "usageRecordId": "wb-task-uuid-001",
    "pointsAmount": 10,
    "remainPoints": 990,
    "failReason": null
  }
}
```

### 3.3 `POST /fbs/skill-api/usage/start` + `PUT /fbs/skill-api/usage/end/{usageRecordId}` — 两阶段模式

> **⚠️ 与 consume 互斥**：start/end 和 consume 是两种独立的消费模式，**同一个 usageRecordId 只能走一种**。
>
> 现有 `SkillConsumeService.consume` 是自闭环的：自己 INSERT status=0 → 扣减 → UPDATE status=1。如果先用 start 插入 status=0，consume 会发现已存在且 status=0，直接拒绝"正在处理中，请勿重复提交"；如果先 end 改成 status=1/2，consume 会幂等返回或拒绝。
>
> **选择逻辑**：
> - 需要扣费 → 用 **consume**（一次性完成校验+扣减+记录）
> - 只需记录不需要扣费 → 用 **start/end**（纯日志模式）

**start 请求**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | Long | ✅ | 用户ID |
| `packCode` | String | ✅ | 场景包编码 |
| `skillCode` | String | ✅ | Skill 编码 |
| `usageRecordId` | String | ✅ | 幂等键（String，UUID 或 taskId） |
| `hostType` | String | 否 | 宿主类型 |

**start 幂等语义**：

| 重复 usageRecordId 的情况 | 处理 |
|---------------------------|------|
| 已存在且 status=0（进行中） | 返回已有记录（200），不重复创建 |
| 已存在且 status=1（成功） | 返回 409 "使用记录已成功结束" |
| 已存在且 status=2（失败） | 返回 409 "使用记录已失败结束" |
| 不存在 | 创建新记录（status=0） |

**start 响应**：

```json
{
  "code": 200,
  "data": {
    "usageRecordId": "wb-task-uuid-001",
    "status": 0
  }
}
```

**end 请求**：`PUT /fbs/skill-api/usage/end/{usageRecordId}`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `usageRecordId` | String | ✅ | 路径参数，start 返回的幂等键 |
| `status` | Integer | ✅ | 1=成功, 2=失败 |
| `errorMessage` | String | 否 | 失败原因 |

**内部调用**：`FbsSkillUsageRecordMapper.insertUsageRecord` + `FbsSkillUsageRecordMapper.updateStatusByRecordId(usageRecordId, status, errorMessage)`

**end 幂等语义**：
- 记录不存在 → 404 "使用记录不存在"
- status=0 → 更新为 1 或 2（只允许一次状态转换）
- status=1 → 409 "使用记录已成功结束"
- status=2 → 409 "使用记录已失败结束"

### 3.4 `POST /fbs/skill-api/scene-pack/query` — 场景包规则查询

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `packCode` | String | ✅ | 场景包编码 |

**响应**：

```json
{
  "code": 200,
  "data": {
    "packCode": "pack_bookwriter_v2",
    "packName": "写书助手 v2.0",
    "currentVersion": "1.0.0",
    "status": 1,
    "pointsRuleCode": "rule_bookwriter",
    "contentSnapshot": "{...JSON规则内容...}"
  }
}
```

**说明**：MVP 直接返回 `contentSnapshot`（JSON 字符串），不新建 ruleFileUrl / 文件下载机制。Skill 端解析 JSON 使用。**不做二次结构化转换**，返回原始 JSON 字符串，后端不新建 DTO 来解构规则内容。

### 3.5 `POST /fbs/skill-api/user/info` — 用户信息查询

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | Long | ✅ | 用户ID |

**响应**：

```json
{
  "code": 200,
  "data": {
    "userId": 1,
    "pointsBalance": 990,
    "activatedPacks": [
      { "packCode": "pack_bookwriter_v2", "packName": "写书助手 v2.0", "status": 1 }
    ]
  }
}
```

**说明**：不返回 T0-T3 层级（当前仓库不存在此模型）。如需用户层级，另开 OpenSpec 新增能力。

**内部调用**：`IPointsService.getUserPoints(userId)` + `FbsUserPackMapper.selectMyPacks`

### 3.6 错误码字典（新增）

| 错误码 | HTTP Status | 说明 | 触发条件 |
|--------|-------------|------|----------|
| `SKILL_API_KEY_INVALID` | 401 | API Key 无效 | Header 缺失或 Key 不存在 |
| `SKILL_API_KEY_DISABLED` | 403 | API Key 已禁用 | Key 的 status != 1 |
| `SKILL_API_RATE_LIMITED` | 429 | 速率超限 | 超过 rate_limit_per_min |

---

## 四、Impact（影响范围）

### 4.1 受影响模块

| 模块 | 影响 | 优先级 |
|------|------|--------|
| `WxFbsir-business` | 新增 FbsSkillApiController + FbsApiKeyAuthService + FbsApiKeyAuthFilter + FbsApiKey Entity/Mapper | P0 |
| `WxFbsir-framework` | SecurityConfig 放行 `/fbs/skill-api/**`，注册 FbsApiKeyAuthFilter | P0 |
| `WxFbsir-ui` | 新增 API Key 管理页面（运营侧） | **P2（不阻塞 MVP）** |
| `FBS-BookWriter Skill` | 新增 fbs-rights-client.mjs，修改场景包激活流程 | P1 |

### 4.2 受影响现有表

| 表 | 变更 |
|---|------|
| `fbs_api_key` | **新增表**：id, api_key VARCHAR(64) UNIQUE, name VARCHAR(128), pack_code VARCHAR(64), rate_limit_per_min INT DEFAULT 60, status TINYINT DEFAULT 1, created_by, created_time, updated_by, updated_time, remark |

**明文存储 api_key**（#15 HMAC 签名依赖原文，不再迁移到 SHA-256 hash）。

### 4.3 受影响规范

- `specs/fbs-rights-foundation/spec.md`：新增 Skill API 网关章节
- `specs/platform-scene-pack-ops/spec.md`：新增 API Key 管理章节

### 4.4 API 兼容性

- 新增接口，不修改现有 `/fbs/internal/**`，完全向后兼容

### 4.5 不在本 OpenSpec 范围内

- OAuth2 / 第三方 OAuth 集成
- API Key 权限粒度（MVP 阶段一个 Key 访问所有 Skill API）
- WebHook 回调机制
- CodeBuddy 宿主特殊适配（降级走离线即可）
- 场景包规则推送（仅拉取模式）
- **T0-T3 用户层级体系**（需另开 OpenSpec）
- **API Key hash 存储**（后续迭代）
- **规则文件下载 / ruleFileUrl**（后续迭代）

---

## 五、MVP 验收标准

| # | 标准 | 优先级 |
|---|------|--------|
| 1 | API Key 可在后端生成/禁用/删除（运营侧接口就绪，前端页面不阻塞）；创建时返回完整 Key（仅此一次），列表接口仅返回脱敏值（前8位+****） | P0 |
| 2 | `/fbs/skill-api/rights/check` 可正确校验用户权益 | P0 |
| 3 | `/fbs/skill-api/usage/consume` 可完成一次性校验+扣减+记录（不传 pointsAmount） | P0 |
| 4 | `/fbs/skill-api/usage/start` + `/end/{usageRecordId}` 可完成两阶段使用记录（与 consume 互斥，同一 usageRecordId 只能走一种模式） | P0 |
| 5 | FbsApiKeyAuthFilter 正确拦截无效/禁用 Key | P0 |
| 6 | 所有 Skill API 通过单元测试 | P0 |
| 7 | `/fbs/skill-api/scene-pack/query` 可返回 contentSnapshot | P1 |
| 8 | `/fbs/skill-api/user/info` 可返回福分余额+已激活场景包（无 T0-T3） | P1 |
| 9 | API Key 速率限制生效，超限返回 429 | P1 |
| 10 | fbs-rights-client.mjs 可调用所有 Skill API | P1 |
| 11 | 离线降级：API 不可用时回退本地缓存 | P2 |
| 12 | 运营侧 API Key 管理前端页面 | P2 |

---

## 六、关键设计决策

### D-1：API Key 认证 vs JWT 代理

- **API Key**：Skill 无浏览器会话，无法获取 JWT；API Key 是服务间认证的标准做法
- **JWT 代理**（备选）：由宿主转发用户 JWT，但 WorkBuddy 宿主不暴露用户 JWT 给 Skill
- **结论**：使用 API Key + userId 组合
- **⚠️ 越权风险**：此方案信任调用方传入 userId，持有 API Key 即可操作任意用户。MVP 仅部署在受控宿主环境，不面向公网。详见 §2.2 安全边界声明

### D-2：consume 与 start/end 互斥模式

- **一次性模式**（consume）：简单场景，调用即完成"校验→扣减→记录"，SkillConsumeService 自闭环管理 usageRecord 生命周期
- **两阶段模式**（start/end）：长会话场景，只负责"插入/更新使用记录"，不发生积分扣减/企业配额扣减
- **⚠️ 两种模式互斥**：同一个 usageRecordId 只能走一种模式
  - 先 start 再 consume → consume 发现 status=0 已存在，拒绝"正在处理中，请勿重复提交"
  - 先 end 再 consume → consume 发现 status=1/2 已存在，幂等返回或拒绝
  - 结论：**不允许 start → end → consume 组合调用**
- **选择逻辑**：需要扣费 → 用 consume；只需记录不需要扣费 → 用 start/end

### D-3：API Key 存储方式

- **MVP**：明文存储（api_key 字段存原文），创建时返回完整 Key（仅此一次），列表查询脱敏
- ~~**后续迭代**：改为 SHA-256 hash 存储，校验时比对 hash~~（已取消——#15 HMAC 签名依赖明文）
- **理由**：HMAC 签名方案需用原文 API Key 重新计算签名，hash 存储会破坏此能力

### D-4：consume 不传 pointsAmount

- **问题**：如果客户端传 pointsAmount，等于把计费权交给了客户端
- **现有机制**：`SkillConsumeService.consume` 内部按 `packCode → pointsRuleCode → wx_points_rule.points_value` 自动计算
- **结论**：Skill API 不传 pointsAmount，完全由后端决定扣减金额

### D-5：user/info 不返回 T0-T3

- **现状**：仓库不存在 T0-T3 用户层级实体或服务
- **结论**：MVP 只返回 userId + 福分余额 + 已激活场景包列表。T0-T3 如需实现，另开 OpenSpec

### D-6：scene-pack/query 返回 contentSnapshot

- **现状**：FbsScenePack 有 contentSnapshot（JSON）+ currentVersion，无 ruleFileUrl
- **结论**：MVP 直接返回 contentSnapshot 字段内容，Skill 端解析 JSON 使用。不新建文件下载机制

### D-7：usageRecordId 统一为 String

- **现状**：全链路（Entity/Mapper/Controller/Service）usageRecordId 均为 String 类型
- **结论**：Skill API 的 usage/start 返回 usageRecordId（String），end 路径参数也是 String。不使用 Long 型数据库主键

### D-8：运营侧前端页面 P2

- **理由**：MVP 核心是后端 Skill API 网关 + API Key 认证。运营侧 API Key CRUD 接口属于 P0（必须可操作），但前端页面可以延后
- **结论**：P0 完成后端 API Key CRUD 接口，P2 完成前端页面（MVP 期间可用 Postman/SQL 操作）
