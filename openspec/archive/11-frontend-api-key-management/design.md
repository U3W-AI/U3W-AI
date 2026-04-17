# Design: frontend-api-key-management

> **Change ID**: 11-frontend-api-key-management
> **日期**: 2026-04-17

---

## 1. 数据模型变更

### 1.1 fbs_api_key 表字段扩展

**SQL 文件**: `sql/V20260417__11-frontend-api-key-management__add_fields.sql`

```sql
-- 新增字段
ALTER TABLE fbs_api_key
ADD COLUMN user_id BIGINT COMMENT '绑定用户ID' AFTER api_key,
ADD COLUMN last_used_at DATETIME COMMENT '最后使用时间' AFTER status;

-- 新增索引
CREATE INDEX idx_user_id ON fbs_api_key(user_id);
```

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | BIGINT | 绑定用户ID（用户自助创建时自动填充，运营端生成的 Key 可为空） |
| `last_used_at` | DATETIME | 最后使用时间，每次 API Key 认证成功时更新 |

**约束说明**:
- `user_id` 字段**允许为空**（NOT NULL 会破坏历史数据和运营端流程）：
  - **用户自助创建的 Key**：自动绑定 `user_id`
  - **运营端生成的 Key**（OpenSpec #5 已有功能）：`user_id` 为空
  - **历史 Key**（本变更前创建）：`user_id` 为空
- MVP 阶段：允许一个用户有多个 API Key（便于测试和调试）
- 后续迭代：可考虑限制一个用户只能有一个 API Key，但运营端生成的 Key 仍需允许空值

---

## 2. 后端改造

### 2.1 Entity + Mapper

**文件**: `WxFbsir-business/src/main/java/com/wx/fbsir/business/fbs/`

#### FbsApiKey.java
```java
public class FbsApiKey {
    private Long id;
    private String apiKey;
    private Long userId;           // 新增
    private String name;
    private String packCode;
    private Integer rateLimitPerMin;
    private Integer status;
    private Date lastUsedAt;       // 新增
    private String createdBy;
    private Date createTime;
    private String updatedBy;
    private Date updateTime;
    private String remark;
}
```

#### FbsApiKeyMapper.xml
```xml
<!-- 新增查询：按用户ID查询 -->
<select id="selectByUserId" parameterType="Long" resultType="FbsApiKey">
    SELECT * FROM fbs_api_key WHERE user_id = #{userId} ORDER BY create_time DESC
</select>

<!-- 更新最后使用时间 -->
<update id="updateLastUsedAt">
    UPDATE fbs_api_key SET last_used_at = NOW() WHERE api_key = #{apiKey}
</update>
```

### 2.2 BusinessService 改造

**文件**: `FbsApiKeyBusinessService.java`

#### 关键方法

```java
/**
 * 用户创建 API Key
 * 自动绑定当前用户
 */
public FbsApiKey createByUser(String name) {
    Long userId = SecurityUtils.getUserId();
    
    // 检查是否已有 API Key（后续迭代可限制）
    // if (countByUserId(userId) >= 1) { throw ... }
    
    FbsApiKey key = new FbsApiKey();
    key.setApiKey(generateApiKey());
    key.setUserId(userId);  // 自动绑定
    key.setName(name);
    key.setStatus(1);
    key.setRateLimitPerMin(60);
    key.setCreatedBy(SecurityUtils.getUsername());
    key.setCreateTime(new Date());
    
    apiKeyMapper.insert(key);
    return key;
}

/**
 * 查询当前用户的 API Key 列表
 * 脱敏显示密钥
 */
public List<FbsApiKey> listMyKeys() {
    Long userId = SecurityUtils.getUserId();
    List<FbsApiKey> keys = apiKeyMapper.selectByUserId(userId);
    
    // 脱敏处理
    keys.forEach(key -> {
        String masked = maskApiKey(key.getApiKey());
        key.setApiKey(masked);
    });
    
    return keys;
}

/**
 * 脱敏显示：前8位 + ****
 */
private String maskApiKey(String apiKey) {
    if (apiKey == null || apiKey.length() < 12) return apiKey;
    return apiKey.substring(0, 8) + "****";
}

/**
 * 禁用/启用 API Key
 * 只能操作自己的 Key
 */
public void toggleStatus(Long id, Integer status) {
    Long userId = SecurityUtils.getUserId();
    FbsApiKey key = apiKeyMapper.selectById(id);
    
    if (key == null) {
        throw new ServiceException("API Key 不存在");
    }
    
    if (!userId.equals(key.getUserId())) {
        throw new ServiceException("无权操作此 API Key");
    }
    
    key.setStatus(status);
    key.setUpdatedBy(SecurityUtils.getUsername());
    key.setUpdateTime(new Date());
    apiKeyMapper.updateById(key);
}
```

### 2.3 Controller 改造

**文件**: `FbsApiKeyBusinessController.java`

```java
/**
 * 用户侧 API Key 管理
 * 路径前缀：/fbs/business/my/apikey
 */

@GetMapping("/list")
public AjaxResult list() {
    return AjaxResult.success(apiKeyBusinessService.listMyKeys());
}

@PostMapping("/create")
public AjaxResult create(@RequestBody FbsApiKeyCreateRequest request) {
    FbsApiKey key = apiKeyBusinessService.createByUser(request.getName());
    // 创建成功，返回完整密钥（仅此一次）
    return AjaxResult.success("创建成功", key);
}

@PutMapping("/toggle/{id}")
public AjaxResult toggle(@PathVariable Long id, @RequestParam Integer status) {
    apiKeyBusinessService.toggleStatus(id, status);
    return AjaxResult.success();
}

@DeleteMapping("/{id}")
public AjaxResult delete(@PathVariable Long id) {
    apiKeyBusinessService.deleteById(id);
    return AjaxResult.success();
}
```

---

## 3. 前端实现

### 3.1 用户侧 API Key 管理

#### 页面结构

```
src/views/my/apikey/
├── index.vue              # 列表页
└── components/
    ├── CreateDialog.vue   # 创建对话框
    └── ShowKeyDialog.vue  # 显示密钥对话框（创建成功后）
```

#### API 层

**文件**: `src/api/my/apikey.js`

```javascript
import request from '@/utils/request'

// 查询我的 API Key 列表
export function listMyApiKeys() {
  return request({
    url: '/fbs/business/my/apikey/list',
    method: 'get'
  })
}

// 创建 API Key
export function createApiKey(name) {
  return request({
    url: '/fbs/business/my/apikey/create',
    method: 'post',
    data: { name }
  })
}

// 禁用/启用
export function toggleApiKey(id, status) {
  return request({
    url: `/fbs/business/my/apikey/toggle/${id}?status=${status}`,
    method: 'put'
  })
}

// 删除
export function deleteApiKey(id) {
  return request({
    url: `/fbs/business/my/apikey/${id}`,
    method: 'delete'
  })
}
```

#### 列表页核心逻辑

**文件**: `src/views/my/apikey/index.vue`

```vue
<template>
  <div class="app-container">
    <!-- 操作按钮 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" @click="handleCreate">
          创建 API Key
        </el-button>
      </el-col>
    </el-row>

    <!-- 列表 -->
    <el-table :data="keyList">
      <el-table-column label="名称" prop="name" />
      <el-table-column label="密钥" prop="apiKey">
        <template slot-scope="scope">
          <code>{{ scope.row.apiKey }}</code>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" />
      <el-table-column label="操作" width="200">
        <template slot-scope="scope">
          <el-button size="mini" @click="handleToggle(scope.row)">
            {{ scope.row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button size="mini" type="danger" @click="handleDelete(scope.row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建对话框 -->
    <create-dialog ref="createDialog" @success="handleCreateSuccess" />

    <!-- 显示密钥对话框 -->
    <show-key-dialog ref="showKeyDialog" />
  </div>
</template>

<script>
import { listMyApiKeys, toggleApiKey, deleteApiKey } from '@/api/my/apikey'
import CreateDialog from './components/CreateDialog'
import ShowKeyDialog from './components/ShowKeyDialog'

export default {
  components: { CreateDialog, ShowKeyDialog },
  data() {
    return {
      keyList: []
    }
  },
  created() {
    this.loadList()
  },
  methods: {
    loadList() {
      listMyApiKeys().then(res => {
        this.keyList = res.data
      })
    },
    handleCreate() {
      this.$refs.createDialog.open()
    },
    handleCreateSuccess(apiKey) {
      // 创建成功，显示完整密钥
      this.$refs.showKeyDialog.open(apiKey)
      this.loadList()
    },
    handleToggle(row) {
      const newStatus = row.status === 1 ? 0 : 1
      toggleApiKey(row.id, newStatus).then(() => {
        this.$message.success('操作成功')
        this.loadList()
      })
    },
    handleDelete(row) {
      this.$confirm('确定删除此 API Key？删除后无法恢复', '警告', {
        type: 'warning'
      }).then(() => {
        deleteApiKey(row.id).then(() => {
          this.$message.success('删除成功')
          this.loadList()
        })
      })
    }
  }
}
</script>
```

#### 显示密钥对话框（关键）

**文件**: `src/views/my/apikey/components/ShowKeyDialog.vue`

```vue
<template>
  <el-dialog title="API Key 创建成功" :visible.sync="visible" width="500px">
    <el-alert
      type="warning"
      title="请立即复制保存，关闭后无法再次查看完整密钥！"
      :closable="false"
      show-icon
      style="margin-bottom: 20px"
    />

    <el-form label-width="80px">
      <el-form-item label="名称">
        <el-input v-model="form.name" disabled />
      </el-form-item>
      <el-form-item label="密钥">
        <el-input v-model="form.apiKey" readonly>
          <el-button slot="append" @click="handleCopy">复制</el-button>
        </el-input>
      </el-form-item>
    </el-form>

    <div slot="footer">
      <el-button @click="visible = false">关闭</el-button>
    </div>
  </el-dialog>
</template>

<script>
export default {
  data() {
    return {
      visible: false,
      form: {
        name: '',
        apiKey: ''
      }
    }
  },
  methods: {
    open(data) {
      this.form = data
      this.visible = true
    },
    handleCopy() {
      navigator.clipboard.writeText(this.form.apiKey).then(() => {
        this.$message.success('已复制到剪贴板')
      })
    }
  }
}
</script>
```

---

## 4. 企业侧管理界面说明

> **重要**：企业侧管理界面已在 **OpenSpec #3** 中完成，无需重复开发。

### 4.1 已完成功能

| 功能 | 路径 | 说明 |
|------|------|------|
| 企业列表页 | `src/views/business/fbs/enterprise/index.vue` | 企业 CRUD |
| 企业包列表页 | `src/views/business/fbs/enterprise/pack/index.vue` | 分发/撤销企业包 |
| 成员管理页 | `src/views/business/fbs/enterprise/member/index.vue` | 添加/移除成员 |

### 4.2 后端接口

| 接口 | Controller | 状态 |
|------|-----------|------|
| `/business/fbs/enterprise/*` | FbsEnterpriseBusinessController | ✅ 已完成 |
| `/business/fbs/enterprise/pack/*` | FbsEnterprisePackBusinessController | ✅ 已完成 |
| `/business/fbs/enterprise/member/*` | FbsEnterpriseMemberBusinessController | ✅ 已完成 |

### 4.3 数据库表

| 表名 | 说明 | 状态 |
|------|------|------|
| `fbs_enterprise` | 企业组织主表 | ✅ 已创建 |
| `fbs_enterprise_pack` | 企业已获场景包表（含配额） | ✅ 已创建 |
| `fbs_enterprise_member` | 企业成员关联表 | ✅ 已创建 |
| `fbs_member_pack` | 成员场景包授权表 | ✅ 已创建 |

---

## 5. 权限菜单配置

### 5.1 用户侧菜单

**文件**: `sql/V20260417__11-user-api-key-menu.sql`

```sql
-- 父菜单：我的工作台（假设 menu_id = 2000）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('我的 API Keys', 2000, 1, 'apikey', 'my/apikey/index', 'C', '0', '0', 'my:apikey:list', 'key', 'admin', NOW(), '用户API Key管理菜单');

-- 获取刚插入的 menu_id（假设为 2001）
-- 按钮权限
INSERT INTO sys_menu (menu_name, parent_id, order_num, menu_type, visible, status, perms, create_by, create_time)
VALUES
('创建', 2001, 1, 'F', '0', '0', 'my:apikey:create', 'admin', NOW()),
('禁用', 2001, 2, 'F', '0', '0', 'my:apikey:toggle', 'admin', NOW()),
('删除', 2001, 3, 'F', '0', '0', 'my:apikey:delete', 'admin', NOW());
```

### 5.2 企业侧菜单（已完成）

> **说明**：企业侧菜单已在 OpenSpec #3 中配置完成，见 `sql/V20260410__add-enterprise-scene-pack-ops__sys_menu.sql`。

---

## 6. 核心流程

### 6.1 用户创建 API Key 流程

```
用户登录后台
    ↓
进入"我的 API Keys"页面
    ↓
点击"创建 API Key"
    ↓
输入名称 → 提交
    ↓
后端生成 Key（自动绑定当前用户）
    ↓
返回完整密钥（仅此一次）
    ↓
前端弹出对话框显示完整密钥
    ↓
用户复制保存 → 配置到 WorkBuddy
```

### 6.2 用户使用 API Key 流程

```
用户在 WorkBuddy 配置 API Key
    ↓
Skill 读取配置
    ↓
用户使用场景包（如写书）
    ↓
Skill 调用后端 API（带 X-FBS-API-Key Header）
    ↓
后端验证 Key → 查询 user_id → 操作该用户资源
    ↓
返回结果给 Skill
```

---

## 7. 安全注意事项

| 项 | 说明 |
|----|------|
| **API Key 明文存储** | MVP 阶段存明文，创建时仅返回一次完整 Key |
| **脱敏显示** | 列表查询必须脱敏：前8位 + **** |
| **数据隔离** | 用户只能看到/操作自己的 API Key |
| **HTTPS** | 生产环境必须 HTTPS，防止 API Key 被嗅探 |
| **Key 轮换** | 用户可随时禁用旧 Key + 创建新 Key |
| **一个用户一个 Key** | MVP 允许多个，后续迭代限制为一个 |

---

## 8. 与 OpenSpec #12 的关系

**OpenSpec #11（本次）**：前端界面 + 用户管理 API Key
**OpenSpec #12（下一步）**：Skill 端使用 API Key 调用后端

```
OpenSpec #11：
  - 用户创建 API Key
  - 用户配置到 WorkBuddy

OpenSpec #12：
  - Skill 读取配置
  - Skill 调用后端 API
  - 积分扣减同步
```
