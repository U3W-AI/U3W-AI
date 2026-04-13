# OpenSpec #5 实施任务清单

> **Change ID**: `add-skill-api-gateway`
> **日期**: 2026-04-11
> **状态**: 已完成

> **⚠️ 优先级声明**：本次 apply **仅执行 P0 + P1**，不执行 P2 任务。P2 标记为 `[P2-DEFERRED]`，待后续迭代。

---

## 阶段 1：数据库 + Entity

### 1.1 创建 fbs_api_key 表

**文件**：`G:\...\sql\V20260411__add_skill_api_gateway__create_table.sql`

- [x] 1.1.1 编写 SQL：`CREATE TABLE fbs_api_key（id BIGINT AUTO_INCREMENT, api_key VARCHAR(64) UNIQUE, name VARCHAR(128), pack_code VARCHAR(64), rate_limit_per_min INT DEFAULT 60, status TINYINT DEFAULT 1, created_by, created_time, updated_by, updated_time, remark）`
- [x] 1.1.2 验证 SQL 可执行（目标库 MySQL 5.7）

> **注意**：MVP 阶段 api_key 存明文，后续迭代改为 SHA-256 hash

### 1.2 创建 Entity + Mapper

- [x] 1.2.1 创建 `FbsApiKey.java`（与表对应）
- [x] 1.2.2 创建 `FbsApiKeyMapper.java` + `FbsApiKeyMapper.xml`（CRUD + selectByApiKey + selectActiveByKey）

---

## 阶段 2：安全认证

### 2.1 API Key 认证 Filter

**文件**：`G:\...\src\main\java\com\wx\fbsir\business\fbs\`

- [x] 2.1.1 创建 `FbsApiKeyAuthFilter.java`（OncePerRequestFilter），校验 X-FBS-API-Key Header，有效则设置 SecurityContext
- [x] 2.1.2 创建 `FbsApiKeyAuthService.java`（校验 API Key 有效性、状态、速率限制）

### 2.2 SecurityConfig 修改

- [x] 2.2.1 修改 `SecurityConfig.java`：`/fbs/skill-api/**` 走 API Key Filter（不要求 JWT）
- [x] 2.2.2 注册 `FbsApiKeyAuthFilter` 到 Filter Chain（在 JwtAuthenticationTokenFilter 之前）

---

## 阶段 3：Skill API 网关

### 3.1 Controller + DTO

- [x] 3.1.1 创建 `FbsSkillApiController.java`（6 个路由）
- [x] 3.1.2 创建 DTO：SkillApiCheckRequest, SkillApiConsumeRequest, SkillApiStartRequest, SkillApiEndRequest, SkillApiScenePackQueryRequest, SkillApiUserInfoRequest

### 3.2 接口实现

- [x] 3.2.1 `rights/check`：复用 `RightsCheckService.comprehensiveCheck`，返回 pass/failReason/packId/pointsRuleCode/pointsAmount
- [x] 3.2.2 `usage/consume`：复用 `SkillConsumeService.consume(userId, packCode, skillCode, usageRecordId, hostType, hostSessionId, authCode)`，**不传 pointsAmount**（后端自动计算）
- [x] 3.2.3 `usage/start`：复用 `FbsSkillUsageRecordMapper.insertUsageRecord`，返回 usageRecordId（String）；**幂等语义**：先 selectByRecordId，已存在 status=0 返回已有记录，status=1/2 返回 409，不存在才创建；**⚠️ 与 consume 互斥**：同一 usageRecordId 只能走 start/end 或 consume 一种模式，不能组合调用（SkillConsumeService.consume 是自闭环的，会因 status=0 已存在而拒绝"正在处理中"）
- [x] 3.2.4 `usage/end/{usageRecordId}`：复用 `FbsSkillUsageRecordMapper.updateStatusByRecordId(usageRecordId, status, errorMessage)`，usageRecordId 为 **String** 类型；**⚠️ 与 consume 互斥**：end 只负责更新记录状态，不扣减积分/配额，同一 usageRecordId 不能再调用 consume；**幂等语义**：记录不存在→404，status=0→更新为1或2（只允许一次），status=1→409"已成功结束"，status=2→409"已失败结束"
- [x] 3.2.5 `scene-pack/query`：查询场景包元信息，返回 contentSnapshot + currentVersion（**不返回 ruleFileUrl**，MVP 直接返回 contentSnapshot 原始 JSON 字符串，**不做二次结构化转换**）
- [x] 3.2.6 `user/info`：查询福分余额 + 已激活场景包列表（**不返回 T0-T3**，当前仓库无此模型）

---

## 阶段 4：API Key 运营管理（后端 P0，前端 P2）

### 4.1 后端（P0）

- [x] 4.1.1 创建 `FbsApiKeyBusinessService.java`（CRUD + 启用/禁用 + 生成 Key）
- [x] 4.1.2 创建 `FbsApiKeyBusinessController.java`（运营侧 API，带 @PreAuthorize）
- [x] 4.1.3 生成 API Key 算法：前缀 `fbs_` + 32位随机串，创建时返回完整 Key（**仅此一次**，后续查询**必须脱敏**：前8位+`****`）

### 4.2 前端（[P2-DEFERRED] 不在本轮 apply 范围内）

- [ ] 4.2.1 创建 API Key 管理 Vue 页面：列表 + 生成 + 禁用/启用 + 删除
- [ ] 4.2.2 创建 API 层：`src/api/business/fbs/apiKey.js`

### 4.3 菜单（[P2-DEFERRED] 不在本轮 apply 范围内）

- [ ] 4.3.1 创建 sys_menu SQL（FBS运营管理下挂载 API Key 管理菜单）

---

## 阶段 5：Skill 端对接模块（P1）

### 5.1 fbs-rights-client.mjs

- [x] 5.1.1 创建 `scripts/wecom/lib/fbs-rights-client.mjs`（API Key 认证 + 5 个接口封装 + 离线降级 + 重试）
- [x] 5.1.2 修改 `scene-pack-loader.mjs`：文件不存在，跳过（Scene-pack-loader 尚未在仓库中创建）

### 5.2 配置

- [x] 5.2.1 创建 `SKILL.md`：场景包激活流程增加 API 对接步骤说明
- [x] 5.2.2 `_plugin_meta.json` 增加 api_key / api_base_url 配置项

---

## 阶段 6：单元测试

### 6.1 后端测试

- [x] 6.1.1 `FbsApiKeyAuthServiceTest`：API Key 校验逻辑（9 用例全绿：有效/无效/禁用/速率超限/null/空格/其他状态/默认限流）
- [x] 6.1.2 `FbsSkillApiControllerTest`：6 个路由的正常+异常路径（24 用例全绿）
- [x] 6.1.3 `FbsApiKeyBusinessServiceTest`：CRUD + 启用/禁用 + 生成 Key + 脱敏（15 用例全绿）

### 6.2 Skill 测试（[P2-DEFERRED] 不在本轮 apply 范围内）

- [ ] 6.2.1 `fbs-rights-client.mjs` 单元测试（Node.js 原生 test runner）

---

## 阶段 7：文档 + 收尾

### 7.1 文档

- [x] 7.1.1 更新 `FBS-BUSINESS-API.md`：新增 Skill API 网关章节（§7 6个接口）+ API Key 管理章节（§8 6个接口）+ 附录 A.15-A.17
- [x] 7.1.2 更新常驻规范 spec.md：新增第十二章（Skill API 网关）+ 第十三章（API Key 管理）+ 安全边界声明，版本升级至 v1.4
- [x] 7.1.3 **安全边界核对** ✅：
  - （1）SKILL.md 明确写明"API Key 仅部署在受控 WorkBuddy 宿主环境" ✅
  - （2）SKILL.md + design.md 明确"不面向公网第三方开放" ✅
  - （3）SKILL.md 明确"生产环境必须 HTTPS" ✅
  - （4）SKILL.md 明确"API Key 由管理员手动配置，不通过公开文档/SDK 分发" ✅

### 7.2 集成测试

- [x] 7.2.1 端到端验证：后端编译通过（BUILD SUCCESS），全量测试 155+48=203 用例全绿（含新增 48 用例）

### 7.3 归档

- [x] 7.3.1 清空 changes/ 目录，归档到 archive/05-add-skill-api-gateway/
