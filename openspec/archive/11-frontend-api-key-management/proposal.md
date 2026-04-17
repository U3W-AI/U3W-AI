# OpenSpec #11 Proposal: API Key 管理前端补全

> **Change ID**: `frontend-api-key-management`
> **阶段**: 前端补全（用户自助管理 API Key）
> **日期**: 2026-04-17
> **状态**: 待审核

---

## 一、Why（为什么做）

OpenSpec #5 已完成后端 API Key 管理接口（6 个管理端点），但 **缺乏前端界面，用户无法自助管理 API Key**：

| 问题 | 现状 |
|------|------|
| 后端接口就绪 | ✅ `/fbs/business/api-key/*` CRUD 接口已完成 |
| 前端界面缺失 | ❌ 用户无法在后台看到/创建/管理 API Key |
| API Key 绑定用户 | ❌ 当前表结构无 `user_id` 字段，无法绑定用户 |
| Skill 无法对接 | ❌ 用户无法获取 API Key 并配置到 WorkBuddy |

**核心矛盾**：后端有能力，但用户无法自助操作，阻碍 Skill 端在线化对接。

---

## 二、What Changes（做什么）

### 2.1 数据模型变更

#### 2.1.1 `fbs_api_key` 表扩展

**新增字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | BIGINT | **新增**：绑定用户ID（谁创建的，归属谁） |
| `last_used_at` | DATETIME | **新增**：最后使用时间（审计用） |

**DDL**：

```sql
ALTER TABLE fbs_api_key 
ADD COLUMN user_id BIGINT COMMENT '绑定用户ID' AFTER api_key,
ADD COLUMN last_used_at DATETIME COMMENT '最后使用时间' AFTER status;

-- 索引
CREATE INDEX idx_user_id ON fbs_api_key(user_id);
```

**约束**：
- `user_id` NOT NULL（每个 API Key 必须绑定用户）
- **MVP 阶段**：一个用户可以有多个 API Key（便于测试和调试）
- **后续迭代**：限制一个用户只能有一个 API Key（添加唯一约束 `UNIQUE KEY uk_user_id (user_id)`）

#### 2.1.2 权限菜单新增

| 菜单名称 | 菜单ID | 权限标识 | 父菜单 | 说明 |
|---------|-------|---------|-------|------|
| API Key 管理 | 待定 | `user:apikey:list` | 我的权益 | 一级菜单 |
| 查看列表 | 待定 | `user:apikey:query` | API Key 管理 | 按钮 |
| 创建 Key | 待定 | `user:apikey:create` | API Key 管理 | 按钮 |
| 禁用/启用 | 待定 | `user:apikey:toggle` | API Key 管理 | 按钮 |
| 删除 Key | 待定 | `user:apikey:remove` | API Key 管理 | 按钮 |

### 2.2 前端界面设计

#### 2.2.1 API Key 列表页

**路径**：`/my/apikey`（用户侧，我的权益菜单下）

**功能**：
- 显示当前用户的所有 API Key
- 状态标识（启用/禁用）
- 创建时间、最后使用时间
- 操作按钮（禁用/启用、删除）

**列表示例**：

```
┌──────────────────────────────────────────────────────────┐
│  我的 API Keys                          [+ 创建新 Key]     │
├──────────────────────────────────────────────────────────┤
│  名称          密钥              状态    创建时间    操作    │
│  我的 Skill    fbs_****abcd    ✅ 启用  2026-04-17  [禁用] [删除] │
│  测试 Key      fbs_****efgh    ⚠️ 禁用  2026-04-10  [启用] [删除] │
└──────────────────────────────────────────────────────────┘
```

**脱敏规则**：
- 列表页只显示前 8 位 + `****`（如 `fbs_abc1****`）
- 不显示完整密钥

#### 2.2.2 创建 API Key 对话框

**交互流程**：

```
点击"创建新 Key"
    ↓
弹出对话框
    ↓
用户输入：
  - 名称（必填，如"我的 Skill Key"）
    ↓
点击"生成"
    ↓
后端生成 Key（格式：fbs_user_{userId}_{random}）
    ↓
**关键：显示完整密钥（只显示一次）**
    ↓
用户复制保存
    ↓
关闭对话框后无法再次查看完整密钥
```

**对话框设计**：

```
┌─────────────────────────────────────────────────────┐
│  创建 API Key                                        │
├─────────────────────────────────────────────────────┤
│  名称：[________________________] *                  │
│                                                      │
│                    [取消]  [生成]                     │
└─────────────────────────────────────────────────────┘

生成成功后：

┌─────────────────────────────────────────────────────┐
│  ✅ API Key 创建成功                                 │
├─────────────────────────────────────────────────────┤
│  密钥：fbs_user_1001_abc123def456xyz789              │
│                                                      │
│  ⚠️ 请立即复制保存，关闭后无法再次查看完整密钥！        │
│                                                      │
│                    [已复制]  [关闭]                   │
└─────────────────────────────────────────────────────┘
```

**安全提示**：
- 显示大字体警告：**"请立即复制保存，关闭后无法再次查看完整密钥！"**
- 提供"已复制"按钮，确认用户已保存

#### 2.2.3 禁用/启用/删除操作

**禁用**：
- 点击"禁用" → 确认对话框 → 调用 `/fbs/business/api-key/toggle/{id}`
- 状态变为"禁用"，该 Key 无法调用 Skill API
- 提示："API Key 已禁用，使用此 Key 的 Skill 将无法调用后端 API"

**启用**：
- 点击"启用" → 调用 `/fbs/business/api-key/toggle/{id}`
- 状态变为"启用"，恢复使用

**删除**：
- 点击"删除" → 确认对话框（红色警告）
- 调用 `/fbs/business/api-key/remove/{id}`
- 物理删除记录
- 警告："删除后无法恢复，使用此 Key 的 Skill 将无法调用后端 API"

### 2.3 后端 API 调整

#### 2.3.1 创建 API Key 接口调整

**当前接口**：`POST /fbs/business/api-key/create`

**当前请求体**：
```json
{
  "name": "我的 Skill Key"
}
```

**调整后请求体**：
```json
{
  "name": "我的 Skill Key"
}
```

**调整后响应**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1,
    "apiKey": "fbs_user_1001_abc123def456xyz789",  // ← 完整密钥（仅此一次）
    "name": "我的 Skill Key",
    "userId": 1001,  // ← 自动从当前登录用户获取
    "status": 1,
    "createdAt": "2026-04-17 12:00:00"
  }
}
```

**关键逻辑**：
- `userId` 从 `SecurityUtils.getUserId()` 自动获取（当前登录用户）
- 不允许手动指定 `userId`
- **返回完整密钥**（仅创建时返回一次）

#### 2.3.2 列表接口调整

**当前接口**：`GET /fbs/business/api-key/list`

**调整后逻辑**：
- 只返回当前用户的 API Key（`WHERE user_id = ?`）
- 脱敏显示：`api_key` 字段只返回前 8 位 + `****`

**调整后响应**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 2,
    "rows": [
      {
        "id": 1,
        "apiKey": "fbs_abc1****",  // ← 脱敏
        "name": "我的 Skill Key",
        "userId": 1001,
        "status": 1,
        "lastUsedAt": "2026-04-17 10:30:00",
        "createdAt": "2026-04-17 12:00:00"
      }
    ]
  }
}
```

#### 2.3.3 API Key 认证逻辑调整

**当前逻辑**（OpenSpec #5）：
```java
// 通过 api_key 查询记录
FbsApiKey key = apiKeyMapper.selectByApiKey(apiKey);
// 校验 status
if (key == null || key.getStatus() != 1) {
    return error(401, "Invalid API Key");
}
// 放行
```

**调整后逻辑**：
```java
// 通过 api_key 查询记录
FbsApiKey key = apiKeyMapper.selectByApiKey(apiKey);
// 校验 status
if (key == null || key.getStatus() != 1) {
    return error(401, "Invalid API Key");
}
// 更新最后使用时间
apiKeyMapper.updateLastUsedAt(key.getId(), new Date());
// 返回 user_id（用于后续业务操作）
Long userId = key.getUserId();
```

**关键变化**：
- API Key 绑定 `user_id`
- 通过 API Key 找到用户
- 操作该用户的资源（如扣积分）

### 2.4 API Key 生成规则

**格式**：`fbs_user_{userId}_{random}`

**示例**：
- `fbs_user_1001_abc123def456xyz789`
- `fbs_user_2002_qwe456rty789uio123`

**随机部分**：
- 长度：24 位
- 字符集：`[a-z0-9]`
- 生成方式：`SecureRandom` + `Hex`

**去重**：
- 生成前检查是否已存在
- 如存在则重新生成

---

## 三、Impact（影响范围）

### 3.1 受影响模块

| 模块 | 影响 | 优先级 |
|------|------|--------|
| `WxFbsir-ui` | 新增 API Key 管理页面（用户侧） | P0 |
| `WxFbsir-business` | 调整 API Key CRUD 接口（绑定 user_id） | P0 |
| `sys_menu` | 新增权限菜单 | P0 |
| `fbs_api_key` 表 | 新增 user_id + last_used_at 字段 | P0 |

### 3.2 受影响现有表

| 表 | 变更 |
|---|------|
| `fbs_api_key` | **新增字段**：user_id, last_used_at |

### 3.3 API 兼容性

| 接口 | 变更 | 向后兼容 |
|------|------|---------|
| `POST /fbs/business/api-key/create` | 自动绑定当前用户，返回完整密钥 | ✅ 兼容（前端未使用） |
| `GET /fbs/business/api-key/list` | 只返回当前用户的 Key，脱敏显示 | ✅ 兼容（前端未使用） |
| `PUT /fbs/business/api-key/toggle/{id}` | 校验 Key 是否属于当前用户 | ✅ 兼容 |
| `DELETE /fbs/business/api-key/remove/{id}` | 校验 Key 是否属于当前用户 | ✅ 兼容 |

---

## 三、Impact（影响范围）

### 3.1 受影响模块

| 模块 | 影响 | 优先级 |
|------|------|--------|
| `WxFbsir-ui` | 新增用户侧 API Key 管理页面 | P0 |
| `WxFbsir-ui` | 新增企业侧管理界面（企业包/成员/配额） | P0 |
| `WxFbsir-business` | 调整 API Key CRUD 接口（绑定 user_id） | P0 |
| `sys_menu` | 新增权限菜单（用户侧 + 企业侧） | P0 |
| `fbs_api_key` 表 | 新增 user_id + last_used_at 字段 | P0 |

### 3.2 不在本 OpenSpec 范围内

- **API Key 使用统计图表**（后续迭代）
- **API Key 过期时间**（后续迭代）
- **API Key 权限粒度控制**（后续迭代）
- **一个用户只能有一个 API Key 的限制**（后续迭代）
- **Skill 端 API 对接**（OpenSpec #12）
- **企业侧管理界面**（已在 OpenSpec #3 完成，见 `WxFbsir-ui/src/views/business/fbs/enterprise/`）

---

## 四、MVP 验收标准

### 4.1 用户侧 API Key 管理（P0）

| # | 标准 | 优先级 |
|---|------|--------|
| 1 | `fbs_api_key` 表新增 `user_id` + `last_used_at` 字段 | P0 |
| 2 | 用户可登录后台，在"我的 API Keys"页面看到自己的 Key 列表 | P0 |
| 3 | 用户可创建新的 API Key，系统自动绑定当前用户 | P0 |
| 4 | 创建后显示完整密钥（只显示一次） | P0 |
| 5 | 列表页脱敏显示密钥（前 8 位 + ****） | P0 |
| 6 | 用户可禁用/启用 API Key | P0 |
| 7 | 用户可删除 API Key | P0 |
| 8 | 用户只能看到/操作自己的 API Key（数据隔离） | P0 |
| 9 | 权限菜单配置正确（sys_menu 入库） | P0 |
| 10 | API Key 认证时更新 `last_used_at` 字段 | P1 |

### 4.2 企业侧管理界面（P0）

| # | 标准 | 优先级 |
|---|------|--------|
| 1 | 企业管理员可登录后台，看到本企业的场景包列表 | P0 |
| 2 | 企业管理员可分发场景包给企业 | P0 |
| 3 | 企业管理员可回收企业的场景包 | P0 |
| 4 | 企业管理员可添加/移除企业成员 | P0 |
| 5 | 企业管理员可查看剩余配额和使用记录 | P1 |
| 6 | 数据隔离正确（企业管理员只能看到本企业数据） | P0 |
| 7 | 权限菜单配置正确（sys_menu 入库） | P0 |

---

## 五、关键设计决策

### D-1：API Key 绑定用户

- **问题**：OpenSpec #5 设计中，API Key 未绑定用户，导致无法区分是谁在使用
- **决策**：新增 `user_id` 字段，每个 Key 必须绑定用户
- **理由**：
  - 用户通过 API Key 调用后端，后端需要知道是谁，才能操作该用户的资源（如扣积分）
  - 用户需要管理自己的 Key，不能看到别人的 Key

### D-2：创建时显示完整密钥

- **问题**：如果创建后不显示，用户无法配置到 WorkBuddy
- **决策**：创建时返回完整密钥，但只显示一次
- **理由**：
  - API Key 类似密码，生成后必须让用户看到并保存
  - 类似 GitHub Personal Access Token、微信公众号 AppSecret
  - 列表页脱敏显示，防止泄露

### D-3：API Key 格式

- **格式**：`fbs_user_{userId}_{random}`
- **优点**：
  - 可从 Key 解析出 `user_id`（便于调试）
  - 格式统一，便于识别
- **缺点**：
  - 暴露 `user_id`（可接受，`user_id` 不是敏感信息）

### D-4：数据隔离策略

- **决策**：用户只能看到/操作自己的 API Key
- **实现**：
  - 列表接口：`WHERE user_id = ?`（当前登录用户）
  - 操作接口：校验 `user_id` 是否匹配

### D-5：一个用户只能有一个 API Key（后续迭代）

- **问题**：一个用户如果有多个 API Key，管理复杂，容易混淆
- **决策**：MVP 阶段允许创建多个，后续迭代限制为一个
- **理由**：
  - MVP 优先完成核心功能（用户能创建/管理 API Key）
  - 多个 Key 便于测试和调试
  - 后续迭代添加唯一约束：`ALTER TABLE fbs_api_key ADD UNIQUE KEY uk_user_id (user_id)`
- **风险**：用户可能创建多个 Key，忘记哪个在使用
- **应对**：提供禁用/删除功能，用户可自行管理

### D-6：企业侧管理界面纳入 MVP

- **理由**：企业侧界面是业务闭环的重要组成部分，需要同步完成
- **范围**：企业包列表 + 成员管理 + 配额查看 + 数据隔离
- **优先级**：与用户侧 API Key 管理同等优先（都是 P0）

---

## 六、前端开发任务

### 6.1 用户侧 API Key 管理

| # | 任务 | 文件 | 预估工时 |
|---|------|------|---------|
| 1 | 创建 API Key 管理页面 | `src/views/my/apikey/index.vue` | 1 天 |
| 2 | 创建对话框组件 | `src/views/my/apikey/components/CreateDialog.vue` | 0.5 天 |
| 3 | 添加 API 调用 | `src/api/my/apikey.js` | 0.5 天 |
| 4 | 路由配置 | `src/router/modules/my.js` | 0.5 天 |

**小计**：2.5 天

### 6.2 企业侧管理界面

| # | 任务 | 文件 | 预估工时 |
|---|------|------|---------|
| 1 | 企业包列表页 | `src/views/enterprise/pack/index.vue` | 1.5 天 |
| 2 | 成员管理页 | `src/views/enterprise/member/index.vue` | 1 天 |
| 3 | 配额统计页 | `src/views/enterprise/quota/index.vue` | 0.5 天 |
| 4 | 数据权限隔离逻辑 | `src/permission.js` + 后端数据范围控制 | 0.5 天 |
| 5 | API 调用 | `src/api/enterprise/*.js` | 0.5 天 |
| 6 | 路由配置 | `src/router/modules/enterprise.js` | 0.5 天 |

**小计**：4.5 天

### 6.3 权限菜单 SQL

| # | 任务 | 文件 | 预估工时 |
|---|------|------|---------|
| 1 | 用户侧菜单 SQL | `sql/V20260417__11-user-api-key-menu.sql` | 0.5 天 |
| 2 | 企业侧菜单 SQL | `sql/V20260417__11-enterprise-menu.sql` | 0.5 天 |

**小计**：1 天

---

## 七、风险与依赖

| 风险 | 应对措施 |
|------|---------|
| 用户忘记保存 API Key | 提供禁用/删除/重新生成功能 |
| API Key 泄露 | 用户可立即禁用或删除 |
| 前端开发资源不足 | 优先完成核心功能（列表+创建） |

---

## 八、下一步

1. **审核通过后** → 创建 `design.md`（详细设计）
2. **设计评审通过后** → 创建 `tasks.md`（任务列表）
3. **开始开发** → 前端界面 + 后端调整
4. **验收通过后** → 归档到 `openspec/archive/11-frontend-api-key-management/`
