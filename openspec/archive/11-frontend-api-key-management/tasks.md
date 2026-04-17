# OpenSpec #11 实施任务清单

> **Change ID**: `11-frontend-api-key-management`
> **日期**: 2026-04-17
> **状态**: ✅ 已归档

---

## 阶段 1：数据库变更

### 1.1 fbs_api_key 表字段扩展

**文件**: `sql/V20260417__11-frontend-api-key-management__add_fields.sql`

- [ ] 1.1.1 编写 SQL：`ALTER TABLE fbs_api_key ADD COLUMN user_id BIGINT, ADD COLUMN last_used_at DATETIME`
- [ ] 1.1.2 编写 SQL：`CREATE INDEX idx_user_id ON fbs_api_key(user_id)`
- [ ] 1.1.3 验证 SQL 可执行（目标库 MySQL 5.7）

---

## 阶段 2：后端改造

### 2.1 Entity + Mapper

**文件**: `WxFbsir-business/src/main/java/com/wx/fbsir/business/fbs/`

- [ ] 2.1.1 修改 `FbsApiKey.java`：新增 `userId` + `lastUsedAt` 字段
- [ ] 2.1.2 修改 `FbsApiKeyMapper.xml`：新增 `selectByUserId` + `updateLastUsedAt` 方法
- [ ] 2.1.3 编译验证通过

### 2.2 BusinessService 改造

**文件**: `FbsApiKeyBusinessService.java`

- [ ] 2.2.1 新增 `createByUser(String name)` 方法：自动绑定当前用户
- [ ] 2.2.2 新增 `listMyKeys()` 方法：查询当前用户的 Key 列表（脱敏显示）
- [ ] 2.2.3 新增 `toggleStatus(Long id, Integer status)` 方法：禁用/启用（校验归属）
- [ ] 2.2.4 新增 `deleteById(Long id)` 方法：删除（校验归属）
- [ ] 2.2.5 新增 `maskApiKey(String apiKey)` 方法：脱敏逻辑（前8位 + ****）

### 2.3 Controller 改造

**文件**: `FbsApiKeyBusinessController.java`

- [ ] 2.3.1 新增 `/my/apikey/list` 接口：查询当前用户 Key 列表
- [ ] 2.3.2 新增 `/my/apikey/create` 接口：创建 API Key（自动绑定用户）
- [ ] 2.3.3 新增 `/my/apikey/toggle/{id}` 接口：禁用/启用
- [ ] 2.3.4 新增 `/my/apikey/{id}` DELETE 接口：删除

### 2.4 企业侧接口（已完成，跳过）

> **说明**：企业侧接口已在 OpenSpec #3 中完成，包括：
> - `FbsEnterpriseBusinessController`（企业管理）
> - `FbsEnterprisePackBusinessController`（企业包分发/撤销）
> - `FbsEnterpriseMemberBusinessController`（成员管理）
>
> 数据库表也已创建：fbs_enterprise, fbs_enterprise_pack, fbs_enterprise_member, fbs_member_pack

---

## 阶段 3：前端 - 用户侧 API Key 管理

### 3.1 API 层

**文件**: `src/api/my/apikey.js`

- [ ] 3.1.1 创建 API 文件：`listMyApiKeys` + `createApiKey` + `toggleApiKey` + `deleteApiKey`

### 3.2 页面组件

**文件**: `src/views/my/apikey/`

- [ ] 3.2.1 创建 `index.vue`：API Key 列表页
- [ ] 3.2.2 创建 `components/CreateDialog.vue`：创建对话框
- [ ] 3.2.3 创建 `components/ShowKeyDialog.vue`：显示密钥对话框（关键！）

### 3.3 列表页核心功能

- [ ] 3.3.1 列表展示：名称、密钥（脱敏）、状态、创建时间
- [ ] 3.3.2 创建按钮 → 打开创建对话框
- [ ] 3.3.3 禁用/启用按钮 → 切换状态
- [ ] 3.3.4 删除按钮 → 确认删除

### 3.4 创建流程（关键）

- [ ] 3.4.1 创建对话框：输入名称
- [ ] 3.4.2 提交后显示完整密钥（仅此一次）
- [ ] 3.4.3 提示用户复制保存
- [ ] 3.4.4 关闭对话框后无法再次查看完整密钥

### 3.5 路由配置

**文件**: `src/router/modules/my.js`

- [ ] 3.5.1 配置路由：`/my/apikey` → `src/views/my/apikey/index.vue`

---

## 阶段 4：权限菜单配置

### 4.1 用户侧菜单 SQL

**文件**: `sql/V20260417__11-user-api-key-menu.sql`

- [ ] 4.1.1 插入菜单：我的 API Keys（menu_type='C'）
- [ ] 4.1.2 插入按钮权限：创建、禁用、删除（menu_type='F'）

> **说明**：企业侧菜单已在 OpenSpec #3 中配置完成，见 `sql/V20260410__add-enterprise-scene-pack-ops__sys_menu.sql`。

---

## 阶段 5：单元测试

### 5.1 后端测试

- [ ] 5.1.1 `FbsApiKeyBusinessServiceTest`：用户侧 CRUD 测试
  - 测试创建 API Key（自动绑定用户）
  - 测试查询列表（脱敏显示）
  - 测试禁用/启用（校验归属）
  - 测试删除（校验归属）
- [ ] 5.1.2 `FbsApiKeyBusinessControllerTest`：用户侧接口测试
  - 测试 `/my/apikey/list` 接口
  - 测试 `/my/apikey/create` 接口
  - 测试 `/my/apikey/toggle/{id}` 接口
  - 测试 `/my/apikey/{id}` DELETE 接口

### 5.2 前端测试（可选）

- [ ] 5.2.1 手工测试：创建 API Key → 显示完整密钥 → 列表显示脱敏
- [ ] 5.2.2 手工测试：禁用/启用功能
- [ ] 5.2.3 手工测试：删除功能
- [ ] 5.2.4 手工测试：数据隔离（用户 A 看不到用户 B 的 Key）

---

## 阶段 6：文档 + 收尾

### 6.1 文档

- [ ] 6.1.1 更新 `FBS-BUSINESS-API.md`：新增用户侧 API Key 管理接口
- [ ] 6.1.2 更新常驻规范 `spec.md`：新增用户侧 API Key 管理章节

### 6.2 集成测试

- [ ] 6.2.1 端到端验证：用户创建 API Key → 配置到 WorkBuddy → Skill 调用成功
- [ ] 6.2.2 后端编译通过（BUILD SUCCESS）
- [ ] 6.2.3 全量测试通过

### 6.3 归档

- [ ] 6.3.1 清空 `changes/11-frontend-api-key-management/` 目录
- [ ] 6.3.2 归档到 `archive/11-frontend-api-key-management/`
- [ ] 6.3.3 更新 MEMORY.md

---

## 验收标准

### P0（必须完成）

- [ ] 用户可创建 API Key（自动绑定当前用户）
- [ ] 创建后显示完整密钥（只显示一次）
- [ ] 列表显示脱敏密钥（前8位 + ****）
- [ ] 用户可禁用/启用/删除 API Key
- [ ] 用户只能看到自己的 API Key（数据隔离）
- [ ] 权限菜单配置正确

### P1（优先完成）

- [ ] 单元测试通过
- [ ] 文档更新完成

### P2（后续迭代）

- [ ] 一个用户只能有一个 API Key 的限制
- [ ] API Key 过期时间
- [ ] API Key 使用统计图表
