# OpenSpec #4 实施任务清单

> **Change ID**: `add-user-self-service`
> **日期**: 2026-04-10
> **状态**: 已完成

---

## 阶段 1：数据库 ALTER

### 1.1 fbs_user_pack 审计字段扩展

**目的**：支撑 claim/activate 写入审计字段（operator、requestId、operateTime）

**文件**：`G:\...\sql\V20260410__add-user-self-service__audit_fields.sql`

- [x] 1.1.1 编写 SQL：`ALTER TABLE fbs_user_pack ADD COLUMN operator BIGINT COMMENT '操作人用户ID'`（允许 NULL）
- [x] 1.1.2 编写 SQL：`ALTER TABLE fbs_user_pack ADD COLUMN request_id VARCHAR(64) COMMENT '请求追踪ID'`
- [x] 1.1.3 编写 SQL：`ALTER TABLE fbs_user_pack ADD COLUMN operate_time DATETIME COMMENT '操作时间'`
- [x] 1.1.4 验证 SQL 可执行（目标库为 MySQL 5.7，使用标准 ADD COLUMN）

> **说明**：`UNIQUE(user_id, pack_id)`（`uk_user_pack`）已在 `V20260408` 基础建表中定义，本阶段**不新增**唯一约束。

---

## 阶段 2：Entity 扩展

### 2.1 FbsUserPack.java 扩展

**文件**：`G:\...\src\main\java\com\wx\fbsir\business\fbs\domain\entity\FbsUserPack.java`

> **技术栈说明**：当前项目使用普通 POJO + MyBatis XML 映射，无 MyBatis-Plus 注解。新增字段为普通 Java 字段，只需 getter/setter，不需要任何持久层注解。

- [x] 2.1.1 新增非持久化字段 `packCode`（String，getter/setter，XML ResultMap 映射由阶段 3 处理）
- [x] 2.1.2 新增审计字段 `operator`（Long，getter/setter，对应 DDL operator 列）
- [x] 2.1.3 新增审计字段 `requestId`（String，getter/setter，对应 DDL request_id 列）
- [x] 2.1.4 新增审计字段 `operateTime`（Date，getter/setter，对应 DDL operate_time 列）

### 2.2 ActivateResult / AuthCodeActivateResponseDTO 扩展

**文件**：`G:\...\src\main\java\com\wx\fbsir\business\fbs\service\AuthCodeService.java`

> **expiresAt 设计**：expiresAt 属于 fbs_user_pack（激活快照），不属于 fbs_scene_pack。通过在 ActivateResult.success() 中追加 expiresAt 参数，使调用方在激活成功后直接拿到有效期，无需二次查询。

- [x] 2.2.1 `ActivateResult` 新增字段 `expiresAt`（Date）及 getter；`success()` 工厂方法新增 `expiresAt` 参数
- [x] 2.2.2 `AuthCodeServiceImpl.activateAuthCode` 末尾 `userPack` 创建后，将 `userPack.getExpiresAt()` 传入 `ActivateResult.success()` 参数
- [x] 2.2.3 确认 `AuthCodeActivateResponseDTO` 包含 `expiresAt` 字段（阶段4）

---

## 阶段 3：Mapper 扩展

### 3.1 FbsUserPackMapper.xml 扩展

**文件**：`G:\...\src\main\resources\mapper\fbs\FbsUserPackMapper.xml`

> **说明**：`selectMyPacks`（本阶段新增，用户自助分页查询）与 `selectUserPackList`（已有，运营后台）的区别：前者强制加 userId 条件（从 SecurityContext 取），后者由前端传 userId。

- [x] 3.1.1 `FbsUserPackWithPackNameResult` 新增 `<result property="packCode" column="pack_code" />`（已有 JOIN，需补字段映射）
- [x] 3.1.2 `selectFbsUserPackList` SQL 片段新增 `s.pack_code` 列（JOIN 左联已有，补上即可）
- [x] 3.1.3 新增 `selectMyPacks`：分页查询当前用户权益，支持 status/sourceType 过滤，JOIN packName/packCode，ResultMap 为 `FbsUserPackWithPackNameResult`
- [x] 3.1.4 新增 `selectExistsByUserIdAndPackId`：查询用户对指定 packId 是否有 status!=3 的权益（claim 幂等用）

### 3.2 FbsUserPackMapper.java 接口

**文件**：`G:\...\src\main\java\com\wx\fbsir\business\fbs\mapper\FbsUserPackMapper.java`

- [x] 3.2.1 新增方法签名 `List<FbsUserPack> selectMyPacks(FbsUserPack filter)`
- [x] 3.2.2 新增方法签名 `int selectExistsByUserIdAndPackId(@Param("userId") Long userId, @Param("packId") Long packId)`

### 3.3 FbsAuthCodeMapper.java 幂等检查用方法

**文件**：`G:\...\src\main\java\com\wx\fbsir\business\fbs\mapper\FbsAuthCodeMapper.java`

> **说明**：幂等前置检查需要根据 authCode 字符串查到 targetId（场景包ID），现有 `selectByAuthCode(String)` 已满足，无需新增方法。

- [x] 3.3.1 确认 `FbsAuthCodeMapper.java` 已有 `FbsAuthCode selectByAuthCode(String authCode)` 方法（含 `targetId` 字段）；如有则无需新增

### 3.4 FbsScenePackMapper.xml 扩展

**文件**：`G:\...\src\main\resources\mapper\fbs\FbsScenePackMapper.xml`

- [x] 3.4.1 新增 `selectClaimablePacks`：查询可领取平台场景包，条件：owner_type=1, status=1, visible_scope='ALL', points_rule_code IS NULL，用户未拥有（NOT EXISTS 子查询），keyword 搜索：`pack_name LIKE CONCAT('%', #{keyword}, '%') OR pack_code LIKE CONCAT('%', #{keyword}, '%')`，支持分页

### 3.5 FbsScenePackMapper.java 接口

**文件**：`G:\...\src\main\java\com\wx\fbsir\business\fbs\mapper\FbsScenePackMapper.java`

- [x] 3.5.1 新增方法签名 `List<FbsScenePack> selectClaimablePacks(FbsScenePack filter)`（含分页/keyword 参数封装）

---

## 阶段 4：DTO 定义

### 4.1 我的权益相关 DTO

**包**：`com.wx.fbsir.business.fbs.dto.self`

- [x] 4.1.1 `MyPacksQueryDTO`：pageNum（int，默认1）, pageSize（int，默认10）, status（Integer）, packId（Long）, sourceType（Integer）
- [x] 4.1.2 `MyPackItemDTO`：id, packId, packName, packCode, packVersion, authCodeId, activatedAt, expiresAt, sourceType, sourceTypeDesc, status, statusDesc, createTime；含枚举方法 `getSourceTypeDesc()` 和 `getStatusDesc()`
- [x] ~~4.1.3 `MyPacksResponseDTO`：List\<MyPackItemDTO\>, total（Long）~~ — 已删除（分页改为 TableDataInfo，此类成死代码）

### 4.2 授权码激活相关 DTO

**包**：`com.wx.fbsir.business.fbs.dto.self`

- [x] 4.2.1 `AuthCodeActivateRequestDTO`：authCode（String，@NotBlank）
- [x] 4.2.2 `AuthCodeActivateResponseDTO`：packId（Long）, packName（String）, expiresAt（LocalDateTime）, msg（String，成功/幂等信息）

### 4.3 可领取场景包相关 DTO

**包**：`com.wx.fbsir.business.fbs.dto.self`

- [x] 4.3.1 `MyScenePacksQueryDTO`：pageNum（int，默认1）, pageSize（int，默认10，最大100）, keyword（String）
- [x] 4.3.2 `MyScenePackItemDTO`：id, packCode, packName, currentVersion, description, pointsRuleCode（null=免费）, recommended, createTime
- [x] ~~4.3.3 `MyScenePacksResponseDTO`：List\<MyScenePackItemDTO\>, total（Long）~~ — 已删除（分页改为 TableDataInfo，此类成死代码）

### 4.4 领取场景包相关 DTO

**包**：`com.wx.fbsir.business.fbs.dto.self`

- [x] 4.4.1 `ScenePackClaimRequestDTO`：packId（Long，@NotNull）
- [x] 4.4.2 `ScenePackClaimResponseDTO`：packId（Long）, packName（String）, expiresAt（LocalDateTime）, msg（String，成功/幂等信息）

---

## 阶段 5：BusinessService 实现

**包**：`com.wx.fbsir.business.fbs.service.business`
**实现**：`com.wx.fbsir.business.fbs.service.business.impl.FbsUserSelfServiceBusinessServiceImpl`
**路径前缀**：`/my/*`

### 5.1 IFbsUserSelfServiceBusinessService 接口

- [x] 5.1.1 方法 `getMyPacks(MyPacksQueryDTO query)` → `MyPacksResponseDTO`
- [x] 5.1.2 方法 `activateAuthCode(AuthCodeActivateRequestDTO req)` → `AuthCodeActivateResponseDTO`
- [x] 5.1.3 方法 `getClaimableScenePacks(MyScenePacksQueryDTO query)` → `MyScenePacksResponseDTO`
- [x] 5.1.4 方法 `claimScenePack(ScenePackClaimRequestDTO req)` → `ScenePackClaimResponseDTO`

### 5.2 FbsUserSelfServiceBusinessServiceImpl — getMyPacks

- [x] 5.2.1 从 `SecurityUtils.getUserId()` 获取 userId，为 null 抛 `ServiceException(HttpStatus.UNAUTHORIZED.value(), "未登录")`
- [x] 5.2.2 构造 `FbsUserPack` filter：userId + status + packId + sourceType
- [x] 5.2.3 调用 `userPackMapper.selectMyPacks(filter)` 分页查询
- [x] 5.2.4 组装 `MyPacksResponseDTO`（sourceTypeDesc/statusDesc 由 DTO 枚举方法提供）

### 5.3 FbsUserSelfServiceBusinessServiceImpl — activateAuthCode

**关键**：幂等前置检查在调用 `authCodeService.activateAuthCode` **之前**，不可绕过。

- [x] 5.3.1 从 `SecurityUtils.getUserId()` 获取 userId，为 null 抛 `ServiceException(HttpStatus.UNAUTHORIZED.value(), "未登录")`
- [x] 5.3.2 **幂等前置检查（外层，在调用 activateAuthCode 之前，不可绕过）**：
  - 5.3.2a `authCode` 为空（null 或 blank）→ 抛 `ServiceException(400, "AUTH_CODE_INVALID")`
  - 5.3.2b 调用 `authCodeMapper.selectByAuthCode(authCode)`（只读），取 `code`
  - 5.3.2c `code == null` → 抛 `ServiceException(400, "AUTH_CODE_INVALID")`
  - 5.3.2d `code` 存在但 `targetType != "SCENE_PACK"` 或 `targetId == null` → 抛 `ServiceException(400, "AUTH_CODE_INVALID")`
  - 5.3.2e 用 `code.targetId` 查 `userPackMapper.selectExistsByUserIdAndPackId(userId, targetId)` → 有则返回 `ALREADY_ACTIVATED`（HTTP 200，msg="已激活该场景包"），**不再往下走**
- [x] 5.3.3 调用 `authCodeService.activateAuthCode(authCode, userId)`（内部 FOR UPDATE + 事务封装）
- [x] 5.3.4 成功路径：从 `ActivateResult.success` 直接取 `userPackId + packId + packCode + expiresAt`（expiresAt 已由阶段2.2在激活时写入 ActivateResult）
- [x] 5.3.5 根据 packId 查 `scenePackMapper.selectById` 补取 `packName`，组装 `AuthCodeActivateResponseDTO`（msg="激活成功"，含 packId/packName/expiresAt）
- [x] 5.3.6 **失败路径**：map failReason → `ServiceException(code, msg)`（与 5.5.2 统一口径）：
  - "授权码不存在" / "授权码不可激活" → `ServiceException(400, "AUTH_CODE_INVALID")`
  - "授权码已禁用" → `ServiceException(400, "AUTH_CODE_DISABLED")`
  - "授权码已撤销" → `ServiceException(400, "AUTH_CODE_REVOKED")`
  - "授权码已过期" → `ServiceException(400, "AUTH_CODE_EXPIRED")`
  - "授权码已达激活次数上限" → `ServiceException(400, "AUTH_CODE_EXHAUSTED")`
- [x] 5.3.7 **审计字段写入**：激活成功后，在 `fbs_user_pack` 记录中补写 operator=userId、requestId=UUID、operateTime=now；`authCodeService.activateAuthCode` 内部已有 `userPackMapper.insertUserPack` 调用，在该调用**之前**设置 userPack 的审计字段值

### 5.4 FbsUserSelfServiceBusinessServiceImpl — getClaimableScenePacks

- [x] 5.4.1 从 `SecurityUtils.getUserId()` 获取 userId
- [x] 5.4.2 调用 `scenePackMapper.selectClaimablePacks(filter)`（含 keyword、分页）
- [x] 5.4.3 组装 `MyScenePacksResponseDTO`

### 5.5 FbsUserSelfServiceBusinessServiceImpl — claimScenePack

- [x] 5.5.1 从 `SecurityUtils.getUserId()` 获取 userId，为 null 抛 `ServiceException(HttpStatus.UNAUTHORIZED.value(), "未登录")`
- [x] 5.5.2 **Fail-Closed 校验链**（顺序短路，全部抛 `ServiceException`）：
  - `PACK_NOT_FOUND`：scenePackMapper.selectById(packId) == null → 抛 `ServiceException(404, "PACK_NOT_FOUND")`
  - `PACK_OFFLINE`：pack.status != 1 → 抛 `ServiceException(400, "PACK_OFFLINE")`
  - `PACK_PRIVATE`：pack.ownerType != 1 → 抛 `ServiceException(400, "PACK_PRIVATE")`
  - `PACK_NOT_FREE`：pack.pointsRuleCode != null → 抛 `ServiceException(400, "PACK_NOT_FREE")`
- [x] 5.5.3 **幂等检查**：`userPackMapper.selectExistsByUserIdAndPackId(userId, packId)` → true 则返回 `ALREADY_CLAIMED`（HTTP 200，msg="已领取该场景包"）
- [x] 5.5.4 **事务写入**（try 块）：创建 `FbsUserPack`，字段：userId, packId, packVersion=pack.currentVersion, status=1, sourceType=1, activatedAt=now，审计字段：operator=userId（Long）、requestId=UUID、operateTime=now；`createdBy` 与 `operator` 语义重复，选 `operator` 为主要口径，`createdBy` 可同步写入但非必须
- [x] 5.5.5 成功：组装 `ScenePackClaimResponseDTO`（msg="领取成功"，expiresAt=null；**MVP 阶段不设置过期时间**，`expiresAt` 固定为 null，后续新增 `end_time` 字段后再补精确逻辑）
- [x] 5.5.6 失败（DuplicateKeyException）：捕获唯一约束冲突，返回 `ALREADY_CLAIMED`（兜底幂等）

---

## 阶段 6：Controller 实现

**文件**：`G:\...\src\main\java\com\wx\fbsir\business\fbs\controller\business\FbsUserSelfServiceController.java`

**路径**：`/my/*`（已在 proposal §2.4 确定）

- [x] 6.1 `GET /my/packs` → `getMyPacks(MyPacksQueryDTO)`（直接透传 queryParams）
- [x] 6.2 `POST /my/auth-code/activate` → `activateAuthCode(AuthCodeActivateRequestDTO)`（@RequestBody）
- [x] 6.3 `GET /my/scene-packs` → `getClaimableScenePacks(MyScenePacksQueryDTO)`
- [x] 6.4 `POST /my/scene-pack/claim` → `claimScenePack(ScenePackClaimRequestDTO)`（@RequestBody）
- [x] 6.5 统一异常处理：`ServiceException` 且 code=401 → AjaxResult(401, "SESSION_REQUIRED")；其他 RuntimeException → AjaxResult(500, msg)

---

## 阶段 7：单元测试

**目录**：`G:\...\src\test\java\com\wx\fbsir\business\fbs\service\business\`

### 7.1 FbsUserSelfServiceBusinessServiceTest — getMyPacks

- [x] 7.1.1 有数据时返回分页列表，字段完整（packName/packCode/sourceTypeDesc/statusDesc）
- [x] 7.1.2 无数据时返回空列表
- [x] 7.1.3 status 过滤生效
- [x] 7.1.4 sourceType 过滤生效
- [x] 7.1.5 SecurityContext 为 null 时抛 `ServiceException(HttpStatus.UNAUTHORIZED)`

### 7.2 FbsUserSelfServiceBusinessServiceTest — activateAuthCode

- [x] 7.2.1 正常激活成功返回 HTTP 200，msg="激活成功"
- [x] 7.2.2 重复激活返回 HTTP 200（幂等），msg="已激活该场景包"，不重复调用 activateAuthCode
- [x] 7.2.3 授权码不存在返回 AUTH_CODE_INVALID（400）
- [x] 7.2.4 授权码已禁用返回 AUTH_CODE_DISABLED（400）
- [x] 7.2.5 授权码已撤销返回 AUTH_CODE_REVOKED（400）
- [x] 7.2.6 授权码已过期返回 AUTH_CODE_EXPIRED（400）
- [x] 7.2.7 授权码次数用尽返回 AUTH_CODE_EXHAUSTED（400）
- [x] 7.2.8 未登录抛 `ServiceException(HttpStatus.UNAUTHORIZED)` → Controller 层返回 SESSION_REQUIRED（401）
- [x] 7.2.9 测试 mock `authCodeService.activateAuthCode` 返回值：`ActivateResult.success(userPackId, packId, packCode, expiresAt)`

### 7.3 FbsUserSelfServiceBusinessServiceTest — getClaimableScenePacks

- [x] 7.3.1 返回分页列表，字段完整
- [x] 7.3.2 keyword 搜索生效
- [x] 7.3.3 只返回 owner_type=1 + status=1 + points_rule_code IS NULL

### 7.4 FbsUserSelfServiceBusinessServiceTest — claimScenePack

- [x] 7.4.1 正常领取成功返回 HTTP 200，fbs_user_pack 记录写入
- [x] 7.4.2 重复领取返回 HTTP 200（幂等），msg="已领取该场景包"
- [x] 7.4.3 场景包不存在返回 PACK_NOT_FOUND（404）
- [x] 7.4.4 场景包已下架返回 PACK_OFFLINE（400）
- [x] 7.4.5 企业包（owner_type!=1）返回 PACK_PRIVATE（400）
- [x] 7.4.6 付费包（pointsRuleCode!=null）返回 PACK_NOT_FREE（400）
- [x] 7.4.7 未登录抛 `ServiceException(HttpStatus.UNAUTHORIZED)` → Controller 层返回 SESSION_REQUIRED（401）
- [x] 7.4.8 DuplicateKeyException 捕获后返回 ALREADY_CLAIMED（并发幂等兜底）

---

## 阶段 8：sys_menu SQL

**文件**：`G:\...\sql\V20260410__add-user-self-service__sys_menu.sql`

- [x] 8.1 编写 sys_menu SQL（parent_id 用 `@USER_SELF_PARENT_ID` 占位，执行前需替换为实际 menu_id）

> **菜单规划**（初稿，待前端实现后确认）：
> - 用户自助（顶级菜单）
>   - 我的权益（/my/packs）
>   - 激活授权码（/my/auth-code/activate）
>   - 领取场景包（/my/scene-packs → /my/scene-packs/claim）

---

## 阶段 9：API 文档更新

**文件**：`G:\...\docs\api\FBS-BUSINESS-API.md`

- [x] 9.1 新增章节 7：`GET /my/packs`
- [x] 9.2 新增章节 8：`POST /my/auth-code/activate`
- [x] 9.3 新增章节 9：`GET /my/scene-packs`
- [x] 9.4 新增章节 10：`POST /my/scene-pack/claim`
- [x] 9.5 附录补充错误码字典（AUTH_CODE_* / PACK_* / ALREADY_* / SESSION_REQUIRED）
- [x] 9.6 文档头部注明 API 版本（如 v2.1）

---

## 验收标准映射

| 验收标准 | 相关任务 |
|----------|----------|
| #1 userId 从 SecurityContext 取 | 5.2.1, 5.3.1, 5.4.1, 5.5.1, 6.5 |
| #2 权益列表分页+过滤 | 3.1.3, 5.2, 7.1 |
| #3 packName/packCode JOIN 生效 | 2.1.1, 3.1.1, 3.1.2, 7.1.1 |
| #4 sourceTypeDesc/statusDesc | 4.1.2（DTO 枚举方法）, 7.1 |
| #5 授权码激活幂等 | 5.3.2a~5.3.2e（外层幂等+空边界+targetType检查）, 7.2.2 |
| #6 fail-closed 校验链 | 5.3.2a~5.3.2d（throw ServiceException）, 5.3.6（failReason→ServiceException map）, 5.5.2（ServiceException 校验链）, 7.2.3~7.2.7 |
| #7 可领取场景包列表 | 3.4.1, 3.5.1, 5.4, 7.3（MVP：end_time 暂以 status=1 代替） |
| #8 claim 幂等 | 5.5.3, 5.5.6（DuplicateKeyException 兜底）, 7.4.2 |
| #9 并发保护 | 5.5.4, 5.5.6（DuplicateKeyException 兜底）, 7.4.8 |
| #10 企业包禁止 claim | 5.5.2（PACK_PRIVATE 校验）, 7.4.5 |
| #11 数据一致性 | 1.1（审计字段 DDL）, 2.2（ActivateResult.expiresAt 扩展→由 activateAuthCode 末尾 userPack 快照写入）, 5.3.4（从 ActivateResult.success 直接取 expiresAt）, 5.5.4（claim 事务写入）, 5.5.5（MVP expiresAt=null） |
| #12 单元测试通过 | 阶段7（全部） |
| #13 未登录返回 401 | 5.2.1, 5.3.1, 5.5.1, 6.5, 7.1.5, 7.2.8, 7.4.7 |
| #14 审计字段写入 | 1.1（DDL）, 2.1.2~2.1.4, 5.3.7（activate）, 5.5.4（claim） |

---

## 执行顺序

```
阶段1(DDL)       → 阶段2(Entity) → 阶段3(Mapper)
     ↓
阶段4(DTO)      ←（Mapper 签名确认后）
     ↓
阶段5(BusinessService)
     ↓
阶段6(Controller)
     ↓
阶段7(单元测试)  → 有测试失败则回改
     ↓
阶段8(sys_menu SQL)
     ↓
阶段9(API文档)
     ↓
→ 归档（扁平结构）
```
