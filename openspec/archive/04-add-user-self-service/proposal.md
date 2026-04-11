# OpenSpec #4 Proposal: 用户侧自助权益中心

> **Change ID**: `add-user-self-service`
> **阶段**: 用户侧自助（User Self-Service）
> **日期**: 2026-04-10

---

## 一、Why（为什么做）

白皮书 FBS 三侧架构中：

| 侧 | 定位 | 状态 |
|---|------|------|
| 用户侧 | 个人写作者 | OpenSpec #1 权益底座 ✅ / **本 OpenSpec** |
| 平台侧 | 福帮手运营后台 | OpenSpec #2 场景包运营 ✅ |
| 企业侧 | 机构部署、成员分发 | OpenSpec #3 企业分发 ✅ |

**当前缺口**：
- 现有权益查询 (`/business/fbs/user-pack/list`) 是**运营视角**，需要输入 `userId`，用户无法查看**自己的**权益
- 授权码激活 (`RightsCheckServiceImpl.activate`) 只有内部 API，**没有用户自助入口**
- 平台侧场景包只有运营分发 (`giftPackToUser`)，**没有用户自助领取入口**

---

## 二、What Changes（做什么）

### 2.1 后端 API（4个新增）

| # | 方法 | 路径 | 说明 |
|---|------|------|------|
| 1 | `GET` | `/my/packs` | 用户自助查询自己的权益列表（分页+多条件过滤） |
| 2 | `POST` | `/my/auth-code/activate` | 用户自助激活授权码 |
| 3 | `GET` | `/my/scene-packs` | 用户查看可领取的平台场景包列表 |
| 4 | `POST` | `/my/scene-pack/claim` | 用户自助领取场景包 |

> 路径前缀 `/my/` 标识用户自助接口，与 `/business/`（运营接口）区分。

### 2.2 复用已有能力

| 已有模块 | 复用方式 |
|----------|----------|
| `AuthCodeServiceImpl.activateAuthCode(authCode, userId)` | **外层补幂等**：`POST /my/auth-code/activate` 先查用户是否已有该包有效权益（幂等拦截）→ 有则返回 `ALREADY_ACTIVATED`；无则调用 `activateAuthCode(...)`（FOR UPDATE 行锁 + 原子递增 `activated_count`） |
| `FbsScenePackMapper` | `GET /my/scene-packs` 复用已有查询条件 |
| `FbsUserPackMapper` | `POST /my/scene-pack/claim` 直接 INSERT（`FbsUserPackBusinessService` 只有查询，无 giftPackToUser）；需补字段：sourceType=1, status=1, activatedAt, createdBy |
| `FbsAuthCodeBusinessService` | 仅运营侧使用，用户侧不新增批量生成 |

### 2.3 新增 BusinessService

| 类 | 职责 |
|---|------|
| `FbsUserSelfServiceBusinessService` | 用户自助服务聚合入口（薄封装，直接调用已有 Mapper/Service） |

### 2.4 新增 Controller

| 类 | 路径 |
|---|------|
| `FbsUserSelfServiceController` | `/my/*` |

---

## 三、API 详细设计

### 3.0 通用约定

| 约定项 | 说明 |
|--------|------|
| 用户来源 | 所有 `/my/*` 接口从 `SecurityContext` 获取当前登录用户ID，**禁止前端传 userId** |
| 鉴权 | 直接调用 `SecurityUtils.getUserId()`，为 null 则抛 401；不依赖不存在的 `isLogin()` 方法 |
| 审计字段 | claim/activate 接口内部写入 `operator`（当前用户ID）、`requestId`（UUID，防重放）、`operate_time` |
| 分页默认 | `pageNum=1`, `pageSize=10`，最大 `pageSize=100` |

### 3.1 `GET /my/packs` — 我的权益列表

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pageNum` | int | 否 | 页码，默认 1 |
| `pageSize` | int | 否 | 每页条数，默认 10 |
| `status` | int | 否 | 权益状态：1=有效, 2=已过期, 3=已撤销, 空=全部 |
| `packId` | Long | 否 | 场景包ID精确过滤 |
| `sourceType` | int | 否 | 来源类型：1=平台分发, 2=企业分发, 3=用户激活, 空=全部 |

**返回字段**（在原有 `FbsUserPack` 基础上扩展 JOIN）：

| 字段 | 来源 | 说明 |
|------|------|------|
| `id` | fbs_user_pack | 权益记录ID |
| `packId` | fbs_user_pack | 场景包ID |
| `packName` | JOIN fbs_scene_pack | 场景包名称（**新增JOIN**） |
| `packCode` | JOIN fbs_scene_pack | 场景包编码（**新增JOIN**） |
| `packVersion` | fbs_user_pack | 版本 |
| `authCodeId` | fbs_user_pack | 关联授权码ID |
| `activatedAt` | fbs_user_pack | 激活时间 |
| `expiresAt` | fbs_user_pack | 到期时间 |
| `sourceType` | fbs_user_pack | 来源类型：1=平台分发, 2=企业分发, 3=授权码激活 |
| `sourceTypeDesc` | 枚举 | 来源中文描述（前端可直接用） |
| `status` | fbs_user_pack | 权益状态：1=有效, 2=已过期, 3=已撤销 |
| `statusDesc` | 枚举 | 状态中文描述（前端可直接用） |
| `createTime` | fbs_user_pack | 创建时间 |

### 3.2 `POST /my/auth-code/activate` — 激活授权码

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `authCode` | String | ✅ | 授权码（授权码内含 packId，无需单独传 packCode） |

> **说明**：激活时只需给授权码，`activateAuthCode` 内部通过 `authCode.targetId` 找到目标包，不需要 `packCode` 参数。

**业务流程**（复用已有 `AuthCodeServiceImpl`，外层补幂等拦截）：

1. **幂等前置检查**：查当前用户是否已有该包有效权益（status=1）；**有则返回 `ALREADY_ACTIVATED`（HTTP 200），不再往下走**
2. **Fail-Closed 校验链**（对授权码本身，顺序短路，任一失败直接返回对应错误码）：
   - `AUTH_CODE_INVALID` — 授权码不存在
   - `AUTH_CODE_DISABLED` — available != 1（available=0 表示禁用）
   - `AUTH_CODE_REVOKED` — status=4（已撤销）
   - `AUTH_CODE_EXPIRED` — deadline < now
   - `AUTH_CODE_EXHAUSTED` — activated_count >= max_activations
3. 调用 `AuthCodeServiceImpl.activateAuthCode(...)`（内部 FOR UPDATE 行锁 + 原子递增 `activated_count`，事务已封装）；失败时返回含 `failReason` 的 `ActivateResult`
4. **map failReason → ServiceException(code, msg)**：BusinessService 统一抛 `ServiceException`，Controller 层转 `AjaxResult`；写入审计字段（operator、requestId、operate_time）
5. 返回激活结果（含错误码 + msg）

> **注意**：`activateAuthCode` 内部不做用户幂等检查（`uk_user_pack` 唯一约束会抛异常），幂等拦截必须在外层第 1 步完成。

**响应**：

```json
// 成功
{
  "code": 200,
  "msg": "激活成功",
  "data": {
    "packId": 1,
    "packName": "福帮手标准版",
    "expiresAt": "2026-05-10T00:00:00"
  }
}

// 失败（AUTH_CODE_* 错误码，由 ServiceException 承载）
{
  "code": 400,
  "msg": "AUTH_CODE_EXPIRED",
  "data": null
}
```

**幂等响应**（重复激活）：

```json
{
  "code": 200,
  "msg": "已激活该场景包",
  "data": {
    "packId": 1,
    "packName": "福帮手标准版",
    "expiresAt": "2026-05-10T00:00:00"
  }
}
```

### 2.5 幂等与并发保护

| 接口 | 幂等策略 | 并发保护 |
|------|----------|----------|
| `POST /my/scene-pack/claim` | 重复领取 → 返回 `ALREADY_CLAIMED`，不报错，HTTP 200 | 事务 + `UNIQUE(user_id, pack_id)` 唯一约束（基础建表已有） |
| `POST /my/auth-code/activate` | 外层先查用户是否已有该包有效权益（status=1）→ 有则返回 `ALREADY_ACTIVATED`（HTTP 200）；无则调用 `activateAuthCode`（内部 FOR UPDATE 行锁 + 原子递增 `activated_count`） | 幂等：外层前置查询；并发：内层 `activateAuthCode` FOR UPDATE 行锁 |

### 2.6 数据一致性约定

| 一致性项 | 说明 |
|----------|------|
| `fbs_user_pack` 状态 | claim 后 status=1（有效）；activate 后 status=1（有效） |
| 授权码计数 | activate 时 `activated_count++` 与 `fbs_user_pack` 插入须在同一事务 |
| 事务边界 | claim/activate 均为单条事务，失败自动回滚，不留中间态 |

### 2.7 错误码字典

| 错误码 | HTTP Status | 说明 | 触发条件 |
|--------|-------------|------|----------|
| `AUTH_CODE_INVALID` | 400 | 授权码无效 | 授权码不存在 |
| `AUTH_CODE_DISABLED` | 400 | 授权码已禁用 | available != 1（available=0 表示禁用） |
| `AUTH_CODE_REVOKED` | 400 | 授权码已撤销 | status = 4（已撤销） |
| `AUTH_CODE_EXPIRED` | 400 | 授权码已过期 | expiry_time < now |
| `AUTH_CODE_EXHAUSTED` | 400 | 授权码次数已用尽 | activated_count >= max_activations |
| `ALREADY_ACTIVATED` | 200 | 已激活（幂等） | 用户已有该包有效权益 |
| `ALREADY_CLAIMED` | 200 | 已领取（幂等） | 用户已领取过该场景包 |
| `PACK_NOT_FOUND` | 404 | 场景包不存在 | packId 对应记录不存在或 del_flag != '0' |
| `PACK_OFFLINE` | 400 | 场景包已下架 | status != 1（已发布） |
| `PACK_NOT_FREE` | 400 | 场景包非免费 | points_rule_code IS NOT NULL（需付费） |
| `PACK_PRIVATE` | 400 | 场景包不可领取 | owner_type != 1（非平台包） |
| `NO_PERMISSION` | 403 | 无权限操作 | 尝试领取企业包 / 越权操作 |
| `SESSION_REQUIRED` | 401 | 未登录 | SecurityContext 无用户信息 |

> **fail-closed 原则**：所有边界条件均返回错误，不静默降级。

---

### 3.3 `GET /my/scene-packs` — 可领取场景包列表

**可领取判定规则（全部满足）**：

| 条件 | 字段 | 值 | 说明 |
|------|------|-----|------|
| 归属类型 | `owner_type` | `1`（平台包） | |
| 上架状态 | `status` | `1`（已发布） | |
| 可见性 | `visible_scope` | `'ALL'`（所有人可见） | `'PRIVATE'`=私有不可见 |
| 免费 | `points_rule_code` | `NULL` | 非 NULL=需付费包 |
| 用户未拥有 | — | 用户在 `fbs_user_pack` 中无该 packId 且 status!=3 的记录 | |

> **MVP 说明**：`end_time` 字段本阶段不新增，未过期判定暂以 `status=1`（已发布）代替；后续新增 `end_time` 后再补精确逻辑。

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `pageNum` | int | 否 | 1 | 页码 |
| `pageSize` | int | 否 | 10 | 每页条数，最大 100 |
| `keyword` | String | 否 | — | 名称/编码关键字搜索 |

**排序约定**：

| 优先级 | 字段 | 方向 | 说明 |
|--------|------|------|------|
| 1 | `recommended` | DESC | 推荐权重，越高越靠前（**需新增字段**，暂无则按 create_time 排序） |
| 2 | `create_time` | DESC | 创建时间，越新越靠前 |

**返回字段**（`FbsScenePack` 公开信息）：

| 字段 | 说明 |
|------|------|
| `id` | 场景包ID |
| `packCode` | 场景包编码 |
| `packName` | 场景包名称 |
| `currentVersion` | 当前版本号 |
| `description` | 描述 |
| `pointsRuleCode` | 积分规则编码（NULL=免费） |
| `recommended` | 推荐权重（**需新增**，暂无则不返回） |
| `createTime` | 创建时间 |

### 3.4 `POST /my/scene-pack/claim` — 领取场景包

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `packId` | Long | ✅ | 场景包ID |

**业务流程**：
1. **Fail-Closed 校验链**：
   - `PACK_NOT_FOUND` — packId 不存在或 del_flag != '0'
   - `PACK_OFFLINE` — status != 1（已发布）
   - `PACK_PRIVATE` — owner_type != 1（企业包走企业路径，禁止个人领取）
   - `PACK_NOT_FREE` — points_rule_code IS NOT NULL（付费包走后续购买流程）
2. 幂等检查：用户是否已有该包的有效权益（status IN(1,2)）→ 返回 `ALREADY_CLAIMED`（HTTP 200）
3. 幂等返回：已有权益时直接返回已有记录，不报错
4. 事务写入（依赖基础建表已有 `UNIQUE(user_id, pack_id)` 约束兜底）：
   - 创建 `fbs_user_pack` 记录：sourceType=1（平台分发）、status=1（有效）、activatedAt=now、createdBy=当前用户ID
   - 写入审计字段（operator、requestId、operate_time）
5. 返回领取结果

**幂等响应**（重复领取）：

```json
{
  "code": 200,
  "msg": "已领取该场景包",
  "data": {
    "packId": 1,
    "packName": "福帮手标准版",
    "expiresAt": "2026-05-10T00:00:00"
  }
}
```

---

## 四、Impact（影响范围）

### 4.1 受影响模块

| 模块 | 影响 |
|------|------|
| `WxFbsir-business` | 新增 `FbsUserSelfServiceBusinessService` + `FbsUserSelfServiceController` |
| `WxFbsir-admin` | 新增 sys_menu（用户自助菜单） |
| `WxFbsir-ui` | **本阶段不涉及前端，延期** |
| `WxFbsir-system` | 无变更 |

### 4.2 受影响现有表（ALTER/DDL）

| 表 | 变更 |
|---|------|
| `fbs_user_pack` | ResultMap 新增 JOIN `fbs_scene_pack` 回显 packCode/packName |
| `fbs_user_pack` | 审计字段扩展（operator, request_id, operate_time） |

> `UNIQUE(user_id, pack_id)` 已在基础建表 `V20260408` 中定义（`uk_user_pack`），幂等约束复用已有，无需新增。

### 4.3 不在本 OpenSpec 范围内

- 前端页面 → 延期
- 付费购买场景包 → 延期（等积分支付完善）
- 企业成员自助申请场景包 → 延期（等企业侧完善）
- 微信小程序/APP 入口 → 延期

---

## 五、MVP 验收标准

| # | 标准 | 优先级 |
|---|------|--------|
| 1 | 用户登录后可直接调用 `GET /my/packs` 查看自己的权益列表，**无需传 userId**（从 SecurityContext 获取） | P0 |
| 2 | 权益列表支持按 status/packId/sourceType 过滤，支持分页 | P0 |
| 3 | 权益列表正确显示 packName/packCode（JOIN 查询生效） | P0 |
| 4 | 权益列表返回 sourceTypeDesc 和 statusDesc（前端可直接展示） | P1 |
| 5 | 用户可调用 `POST /my/auth-code/activate` 激活授权码，重复激活返回 `ALREADY_ACTIVATED`（幂等） | P0 |
| 6 | 授权码激活链路 fail-closed（无效/过期/禁用/次数用尽均返回对应错误码） | P0 |
| 7 | 用户可调用 `GET /my/scene-packs` 查看平台可领取场景包（仅平台包、已发布、可见、免费、未过期、未下架、用户未拥有） | P0 |
| 8 | 用户可调用 `POST /my/scene-pack/claim` 领取免费场景包，重复领取返回 `ALREADY_CLAIMED`（幂等） | P0 |
| 9 | claim 接口并发保护：`UNIQUE(user_id, pack_id)` 唯一约束 + 事务，不出现重复记录 | P0 |
| 10 | 企业包（owner_type != 1）禁止走 claim 路径，返回 `PACK_PRIVATE` | P0 |
| 11 | **数据一致性**：claim/activate 操作后，`fbs_user_pack`、`wx_points_record`（如有）、`fbs_auth_code.remaining_uses` 最终状态一致 | P0 |
| 12 | 所有 API 通过单元测试 | P0 |
| 13 | 权限控制：上述4个接口仅登录用户可访问，未登录返回 `SESSION_REQUIRED` | P0 |
| 14 | claim/activate 接口写入审计字段（operator、requestId、operate_time） | P1 |

---

## 六、关键设计决策

### D-1：/my 路径 vs /business 路径
- `/my/*` = 用户自助接口（需登录，自动获取当前用户）
- `/business/*` = 运营管理接口（需权限，可指定任意 userId）
- 两者复用同一套 Service/Mapper 层，不重复实现业务逻辑

### D-2：FbsUserPack JOIN 查询影响范围
- `GET /business/fbs/user-pack/list`（运营）和 `GET /my/packs` 共用同一套 Mapper + ResultMap
- JOIN 不影响运营查询功能（运营侧本来就只需要列表）
- 如果未来运营侧有性能问题，可以拆分为两个查询

### D-3：前端延期
- 后端 API + 单元测试先行
- 前端页面在下一个迭代实现

