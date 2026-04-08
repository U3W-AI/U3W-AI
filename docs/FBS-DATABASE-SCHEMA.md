# add-fbs-rights-foundation MVP — 数据库表手册

## 一、涉及哪些表

| 表名 | 类型 | 说明 |
|------|------|------|
| `fbs_scene_pack` | **新建** | 场景包主表 |
| `fbs_auth_code` | **新建** | 授权码表 |
| `fbs_user_pack` | **新建** | 用户-场景包关联表 |
| `fbs_skill_usage_record` | **新建** | Skill 使用记录表 |
| `wx_points_rule` | 现有 | 积分规则配置表（新增了 FBS_BOOK_WRITER 规则） |
| `wx_points_record` | 现有扩展 | 积分明细记录表（新增 2 个字段） |
| `sys_user` | 现有 | 用户信息表（积分余额字段 points） |

---

## 二、表结构详解

### 2.1 fbs_scene_pack（场景包主表）— 新建

定义一个场景包是什么、收多少积分。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `pack_code` | VARCHAR(64) | **场景包编码**（全局唯一，如 `PACK_BOOK_WRITER_PRO`） |
| `pack_name` | VARCHAR(128) | 场景包名称（如「悟空写书专业版」） |
| `pack_type` | TINYINT | 1=平台包, 2=企业包, 3=自定义包 |
| `owner_type` | TINYINT | 1=平台, 2=企业, 3=个人 |
| `owner_id` | BIGINT | 所属者ID（owner_type=1时为NULL） |
| `points_rule_code` | VARCHAR(64) | 关联积分规则编码（**NULL=免费包**） |
| `status` | TINYINT | 0=草稿, 1=已发布, 2=已下架 |
| `visible_scope` | VARCHAR(32) | 可见范围：ALL/PRIVATE |
| `current_version` | VARCHAR(32) | 当前版本号 |

**关键逻辑**：`points_rule_code` 关联到 `wx_points_rule.rule_code`，消费时根据规则编码计算扣积分数。

---

### 2.2 fbs_auth_code（授权码表）— 新建

管理授权码的生成、发放和激活。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `auth_code` | VARCHAR(128) | **授权码字符串**（如 `TEST-AUTH-0001`） |
| `code_type` | TINYINT | 1=场景包权益码, 2=通用授权码 |
| `target_type` | VARCHAR(32) | 关联目标类型：`SCENE_PACK` / `GENERIC` |
| `target_id` | BIGINT | **关联目标ID**（指向 fbs_scene_pack.id） |
| `available` | TINYINT | **管理层面开关**：0=禁用, 1=启用 |
| `status` | TINYINT | **使用状态**：0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销 |
| `deadline` | DATETIME | 截止时间，NULL=不限 |
| `max_activations` | INT | **最大激活次数** |
| `activated_count` | INT | 已激活次数（自动+1） |

**注意**：`available` 和 `status` 是两个独立维度：
- `available=0` → 管理员禁用，整张码不可用
- `status=2` → 已用尽（达到 max_activations 上限）
- `status=3` → 已过期（超过 deadline）

---

### 2.3 fbs_user_pack（用户-场景包关联表）— 新建

记录用户拥有哪些场景包，是**权益校验的核心查询表**。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | **用户ID**（关联 sys_user.user_id） |
| `pack_id` | BIGINT | **场景包ID**（关联 fbs_scene_pack.id） |
| `pack_version` | VARCHAR(32) | 激活时的版本快照 |
| `auth_code_id` | BIGINT | **激活用的授权码ID**（source_type=3时有值） |
| `activated_at` | DATETIME | 激活时间 |
| `expires_at` | DATETIME | 过期时间，NULL=永不过期 |
| `status` | TINYINT | 1=有效, 2=已过期, 3=已撤销 |
| `source_type` | TINYINT | **来源**：1=平台分发, 2=企业分发, **3=用户激活**（通过授权码） |
| `created_by` | VARCHAR(64) | 创建者 |
| `create_time` | DATETIME | 创建时间 |

**唯一索引**：`uk_user_pack (user_id, pack_id)` — 防止同一用户重复开通同一场景包。

---

### 2.4 fbs_skill_usage_record（Skill 使用记录表）— 新建

记录每一次 Skill 调用的明细，用于积分扣减追溯和审计。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `usage_record_id` | VARCHAR(64) | **幂等键**（调用方传入，如 WorkBuddy taskId） |
| `user_id` | BIGINT | 用户ID |
| `skill_code` | VARCHAR(64) | 技能编码（如 `book_writer.generate_chapter`） |
| `host_type` | VARCHAR(32) | 宿主类型：`WORKBUDDY` / `STANDALONE` / `API` |
| `host_session_id` | VARCHAR(128) | 宿主会话ID |
| `pack_id` | BIGINT | **使用的场景包ID** |
| `pack_version` | VARCHAR(32) | 使用的场景包版本 |
| `points_amount` | INT | **扣减积分数量**（0=免费） |
| `status` | TINYINT | 0=进行中, 1=成功, 2=失败 |
| `start_time` | DATETIME | 使用开始时间 |
| `end_time` | DATETIME | 使用结束时间 |
| `duration_seconds` | INT | 使用时长（秒） |
| `error_message` | TEXT | 失败原因 |

**幂等保证**：`uk_usage_record_id (usage_record_id)` 唯一索引，同一 usageRecordId 重复调用不重复扣积分。

---

### 2.5 wx_points_rule（积分规则配置表）— 现有，新增 FBS_BOOK_WRITER 规则

定义每种操作的积分消耗/奖励规则。

| 字段 | 类型 | 说明 |
|------|------|------|
| `rule_id` | BIGINT | 主键 |
| `rule_code` | VARCHAR(50) | **规则编码**（唯一索引，如 `FBS_BOOK_WRITER`） |
| `rule_name` | VARCHAR(100) | 规则名称 |
| `points_value` | INT | **积分值**（负数=扣减，正数=奖励） |
| `limit_type` | VARCHAR(20) | 限频类型：`DAILY`/`WEEKLY`/`MONTHLY`/`TOTAL`，NULL=不限 |
| `limit_value` | INT | 限频次数 |
| `max_amount` | INT | 累计上限 |
| `status` | CHAR(1) | 0=正常, 1=停用 |
| `create_by` | VARCHAR(64) | 创建者（**注意：不是 created_by**） |
| `create_time` | DATETIME | 创建时间（**注意：不是 create_time**） |

MVP 新增规则：
```sql
('FBS_BOOK_WRITER', '悟空写书消费', -10, 'DAY', 100, NULL, 0, 10, 'FBS-BookWriter每次消费扣10积分', 'admin', NOW())
```
→ 每次扣 **10 积分**，每天最多扣 **100 分**（即每天最多消费10次）

---

### 2.6 wx_points_record（积分明细记录表）— 现有，扩展 2 个字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `record_id` | BIGINT | 主键 |
| `user_id` | BIGINT | 用户ID |
| `rule_code` | VARCHAR(50) | 规则编码（关联 wx_points_rule） |
| `change_amount` | INT | 变动金额（正=增加，负=扣减） |
| `balance_before` | INT | 变动前余额 |
| `balance_after` | INT | 变动后余额 |
| `remark` | VARCHAR(500) | 备注 |
| `scene_pack_id` | BIGINT | **【新增】关联场景包ID** |
| `usage_record_id` | VARCHAR(64) | **【新增】关联使用记录幂等键** |
| `create_by` | VARCHAR(64) | 创建者 |

---

### 2.7 sys_user（用户信息表）— 现有，积分余额

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | BIGINT | 主键 |
| `points` | INT | **用户积分余额**（直接余额，不是独立表） |

**重要**：用户积分存在这里，不是 `wx_user_points` 表！积分扣减/增加直接 UPDATE 此字段。

---

## 三、表关系图

```
                    ┌─────────────────────┐
                    │   fbs_scene_pack    │  1
                    │  （场景包定义）       │
                    │  points_rule_code ──┼──→ wx_points_rule.rule_code
                    └──────────┬──────────┘
                               │ 1:N（一张场景包 → 多个授权码）
                               │
                    ┌──────────▼──────────┐
                    │    fbs_auth_code   │  1
                    │   （授权码）         │  target_id → fbs_scene_pack.id
                    │  max_activations   │  activated_count++
                    │  status: 0未用/2用尽 │
                    └──────────┬──────────┘
                               │ 1:N（一个授权码 → 多个用户激活）
                               │
                    ┌──────────▼──────────┐
                    │   fbs_user_pack     │  1
                    │（用户-场景包关联）    │  user_id + pack_id → UK
                    │  source_type: 1/2/3  │
                    │  auth_code_id ──────┘
                    └──────────┬──────────┘
                               │ 1:N（一个用户开通 → 多次消费）
                               │
                    ┌──────────▼──────────┐
                    │ fbs_skill_usage_    │  1
                    │     record          │  usage_record_id → UK（幂等）
                    │（使用记录）          │  points_amount 扣减数量
                    └──────────┬──────────┘
                               │
                               │ 1:1（通过 usage_record_id 关联）
                               │
                    ┌──────────▼──────────┐
                    │  wx_points_record  │  1
                    │（积分明细）          │  scene_pack_id（新增字段）
                    │  usage_record_id ───┘（新增字段）
                    └─────────────────────┘


用户积分余额（直接余额）：
                    ┌─────────────────────┐
                    │     sys_user        │
                    │  points 字段        │
                    └─────────────────────┘
```

---

## 四、字段名对照（易错点）

| 表 | 正确字段 | 常见错误 |
|----|---------|---------|
| fbs_scene_pack | `created_by`, `create_time` | ❌ `create_by` |
| fbs_auth_code | `created_by`, `create_time` | ❌ `create_by` |
| fbs_user_pack | `created_by`, `create_time` | ❌ `create_by` |
| wx_points_rule | `create_by`, `create_time` | ✅ 正确 |
| sys_user | `points`（余额字段） | ❌ `wx_user_points` 表不存在 |

---

## 五、核心业务流程中的表读写

### 流程 A：授权码激活
```
写入 fbs_user_pack
  - user_id = 入参
  - pack_id = fbs_auth_code.target_id
  - auth_code_id = fbs_auth_code.id
  - source_type = 3（用户激活）
```

### 流程 B：Skill 消费校验（/fbs/internal/rights/check）
```
查询 fbs_user_pack
  WHERE user_id = ? AND pack_id = ?
  AND status = 1（有效）
  AND (expires_at IS NULL OR expires_at > NOW())
→ 找到记录 = 有权益，找不到 = CODE_NOT_OWNED
```

### 流程 C：Skill 消费扣积分（/fbs/internal/usage/consume）
```
1. 读 fbs_scene_pack.points_rule_code
2. 读 wx_points_rule.points_value（扣减数）
3. UPDATE sys_user.points -= points_value
4. INSERT fbs_skill_usage_record（幂等）
5. INSERT wx_points_record（含 scene_pack_id + usage_record_id）
```

### 流程 D：幂等保证
```
fbs_skill_usage_record.usage_record_id = UK
→ 同一 usageRecordId 不重复扣积分，不重复写记录
```
