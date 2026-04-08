# FBS 权益体系 MVP 集成测试指南

> 本文档覆盖 MVP 上线前的完整验证路径，包括前置数据准备、手动 API 测试和端到端场景。

---

## 一、前置条件

### 1.1 启动服务

```bash
cd G:/Interview/wukongshigang/Weihu/U3W-AI

# 编译打包
mvn clean package -DskipTests

# 启动后端（默认端口 8080）
java -jar WxFbsir-admin/target/wxfbsir-admin.jar
```

### 1.2 执行数据库迁移

```sql
-- 1. 创建新表
source G:/Interview/wukongshigang/Weihu/U3W-AI/sql/V20260408__add-fbs-rights-foundation__create_tables.sql

-- 2. 确认 4 张新表已创建
SHOW TABLES LIKE 'fbs_%';
-- 应看到：fbs_scene_pack, fbs_auth_code, fbs_user_pack, fbs_skill_usage_record

-- 3. 确认 wx_points_record 已扩展 2 个字段
SHOW COLUMNS FROM wx_points_record LIKE '%scene_pack%';
SHOW COLUMNS FROM wx_points_record LIKE '%usage_record%';
```

### 1.3 准备测试数据

```sql
-- === A. 准备积分规则（必须） ===
-- 检查是否已有 FBS 相关的积分规则
SELECT * FROM wx_points_rule WHERE rule_code LIKE 'FBS%';

-- 如果没有，插入示例规则
INSERT INTO wx_points_rule
  (rule_code, rule_name, points_value, limit_type, limit_value, max_amount, status, sort_order, remark, create_by, create_time)
VALUES
  ('FBS_BOOK_WRITER', '悟空写书消费', -10, 'DAY', 100, NULL, 0, 10, 'FBS-BookWriter每次消费扣10积分', 'admin', NOW());

-- === B. 准备场景包 ===
INSERT INTO fbs_scene_pack
  (pack_code, pack_name, pack_type, owner_type, owner_id, points_rule_code, visible_scope, status, current_version, del_flag, created_by, create_time)
VALUES
  ('PACK_BOOK_WRITER_PRO', '悟空写书专业版', 1, 1, 1, 'FBS_BOOK_WRITER', 'ALL', 1, '1.0.0', '0', 'admin', NOW());

-- 获取 pack_id（后续测试需要）
SELECT id, pack_code, pack_name FROM fbs_scene_pack;

-- === C. 准备授权码 ===
-- 先获取上面创建的场景包 ID
INSERT INTO fbs_auth_code
  (auth_code, code_type, target_type, target_id, issuer_type, issuer_id,
   available, status, max_activations, activated_count, del_flag, created_by, create_time)
VALUES
  ('TEST-AUTH-0001', 1, 'SCENE_PACK', <替换为上面PACK的ID>, 1, 1,
   1, 0, 3, 0, '0', 'admin', NOW());

SELECT auth_code, status, activated_count, max_activations FROM fbs_auth_code WHERE auth_code = 'TEST-AUTH-0001';

-- === D. 准备测试用户积分 ===
-- 用户积分余额实际存储在 sys_user.points 表
SELECT user_id, points FROM sys_user WHERE user_id = 1;

-- 如果积分不足，给测试用户加积分
UPDATE sys_user SET points = 100 WHERE user_id = 1;
-- 验证
SELECT user_id, points FROM sys_user WHERE user_id = 1;
```

---

## 二、API 测试用例

### 2.1 端点速查

| # | 端点 | 方法 | 说明 |
|---|---|---|---|
| 1 | `/fbs/internal/scene-pack/create` | POST | 创建场景包 |
| 2 | `/fbs/internal/scene-pack/{id}` | GET | 查询场景包 |
| 3 | `/fbs/internal/auth-code/generate` | POST | 生成授权码 |
| 4 | `/fbs/internal/auth-code/activate` | POST | 激活授权码 |
| 5 | `/fbs/internal/rights/check` | POST | 综合权益校验 |
| 6 | `/fbs/internal/usage/consume` | POST | **Skill消费统一入口** |
| 7 | `/fbs/internal/usage/record` | POST | 写使用记录 |
| 8 | `/fbs/internal/usage/record/{id}/end` | PUT | 结束使用记录 |

---

### 2.2 场景一：完整正向流程（✅）

> **目标**：验证用户积分充足、场景包有效时的完整消费链路

#### Step 1：权益校验
```bash
curl -X POST http://localhost:8080/fbs/internal/rights/check \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "packCode": "PACK_BOOK_WRITER_PRO",
    "authCode": null,
    "hostType": "WORKBUDDY",
    "taskId": "task-001"
  }'
```
**预期**：`code=200`, `data.pass=true`, `data.pointsAmount=-10`

#### Step 2：消费（扣积分+写记录）
```bash
curl -X POST http://localhost:8080/fbs/internal/usage/consume \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "packCode": "PACK_BOOK_WRITER_PRO",
    "skillCode": "book_writer.generate_chapter",
    "usageRecordId": "task-001",
    "hostType": "WORKBUDDY",
    "hostSessionId": "session-001",
    "authCode": null
  }'
```
**预期**：`code=200`, `data.success=true`, `data.remainPoints` 减少 10

#### Step 3：验证积分扣减
```sql
SELECT user_id, points FROM sys_user WHERE user_id = 1;
-- points 应从 100 变为 90
```

#### Step 4：验证使用记录
```sql
SELECT usage_record_id, user_id, skill_code, status, points_amount
FROM fbs_skill_usage_record WHERE user_id = 1;
-- status = 1（成功）, points_amount = 10
```

---

### 2.3 场景二：授权码激活 + 消费（✅）

> **目标**：授权码激活 → 创建 fbs_user_pack → 消费时走授权码通道

#### Step 1：激活授权码
```bash
curl -X POST http://localhost:8080/fbs/internal/auth-code/activate \
  -H "Content-Type: application/json" \
  -d '{
    "authCode": "TEST-AUTH-0001",
    "userId": 1
  }'
```
**预期**：`code=200`, `data.userPackId` 有值

#### Step 2：验证 fbs_user_pack
```sql
SELECT id, user_id, pack_id, status, auth_code_id
FROM fbs_user_pack WHERE user_id = 1 AND auth_code_id IS NOT NULL;
```

#### Step 3：消费（带授权码，应正常扣积分）
```bash
curl -X POST http://localhost:8080/fbs/internal/usage/consume \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "packCode": "PACK_BOOK_WRITER_PRO",
    "skillCode": "book_writer.generate_chapter",
    "usageRecordId": "task-002",
    "hostType": "WORKBUDDY",
    "authCode": "TEST-AUTH-0001"
  }'
```
**预期**：`code=200`, `data.success=true`, `remainPoints` 减少 10

> **说明**：authCode 传参用于消费时校验授权码合法性（是否禁用/过期/已达激活次数上限），不影响积分扣分金额。积分由场景包的 points_rule_code 决定。

---

### 2.4 场景三：Fail-Closed 验证（❌）

| 子场景 | 操作 | 预期拒绝原因 |
|--------|------|-------------|
| **积分不足** | 积分归0后再消费 | `"积分不足，当前剩余: 0，需要: 10"` |
| **授权码无效** | 填一个不存在的 authCode | `"授权码不存在"` |
| **授权码已用尽** | 连续激活4次（max=3）后再激活 | `"授权码激活次数已达上限"` |
| **授权码已过期** | 激活 deadline=过去的授权码 | `"授权码已过期"` |
| **场景包不存在** | 消费 packCode=FAKE_PACK | `"场景包不存在"` |
| **授权码绑定场景包不存在** | 激活指向已删场景包的授权码 | `"授权码关联的场景包不存在"` |

---

### 2.5 场景四：幂等验证（✅）

> **目标**：同一个 `usageRecordId` 重复提交，不重复扣积分

```bash
# 第一次消费
curl -X POST http://localhost:8080/fbs/internal/usage/consume \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "packCode": "PACK_BOOK_WRITER_PRO",
    "skillCode": "book_writer.generate_chapter",
    "usageRecordId": "idempotent-test-001",
    "hostType": "WORKBUDDY"
  }'

# 记录第一次扣了多少积分
SELECT points FROM sys_user WHERE user_id = 1;

# 第二次消费（同一 usageRecordId）
curl -X POST http://localhost:8080/fbs/internal/usage/consume \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "packCode": "PACK_BOOK_WRITER_PRO",
    "skillCode": "book_writer.generate_chapter",
    "usageRecordId": "idempotent-test-001",
    "hostType": "WORKBUDDY"
  }'

# 验证积分不变
SELECT points FROM sys_user WHERE user_id = 1;
-- 两次调用后积分应只扣了一次
```

---

### 2.6 场景五：积分消费记录扩展字段

> **目标**：消费后 wx_points_record 有 scene_pack_id 和 usage_record_id

```bash
# 消费一次
curl -X POST http://localhost:8080/fbs/internal/usage/consume \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "packCode": "PACK_BOOK_WRITER_PRO",
    "skillCode": "book_writer.generate_chapter",
    "usageRecordId": "points-trace-001",
    "hostType": "WORKBUDDY"
  }'
```

```sql
-- 验证积分记录关联了场景包和使用记录
SELECT record_id, user_id, points, rule_code, scene_pack_id, usage_record_id, remark
FROM wx_points_record
WHERE usage_record_id = 'points-trace-001'
ORDER BY create_time DESC LIMIT 1;
-- scene_pack_id 应 = fbs_scene_pack.id
-- usage_record_id 应 = fbs_skill_usage_record.usage_record_id
```

---

## 三、回归检查清单

### 3.1 编译 & 单元测试

```bash
cd G:/Interview/wukongshigang/Weihu/U3W-AI
mvn test -pl WxFbsir-business -am
```

✅ **预期**：Tests run: 20, Failures: 0, Errors: 0

### 3.2 完整性自检

- [ ] 4 张新表存在（`fbs_scene_pack`, `fbs_auth_code`, `fbs_user_pack`, `fbs_skill_usage_record`）
- [ ] `wx_points_record` 有 `scene_pack_id` 和 `usage_record_id` 两个新字段
- [ ] `wx_points_rule` 有 FBS 相关规则（或能正常处理 rule_code=NULL 的免费包场景）
- [ ] 场景包创建后可查询
- [ ] 授权码生成后可激活
- [ ] 权益校验返回正确的 `pass/fail`
- [ ] 消费成功后积分正确扣减
- [ ] 使用记录状态为 1（成功）
- [ ] 重复 usageRecordId 不重复扣积分
- [ ] 积分不足时返回错误码（code≠200）
- [ ] 所有日志无 ERROR 级别输出

---

## 四、常见问题排查

| 问题 | 排查方向 |
|------|---------|
| 编译找不到 `RightsCheckServiceImpl` | Impl 类在 `service.impl` 子包，确认 import 正确 |
| `AjaxResult.error` 返回 200 | 这是设计决策（框架风格），Fail-Closed 体现在业务 code 字段 |
| 授权码激活失败 | 先查 `fbs_auth_code` 确认 `available=1` 且 `status=0` |
| 积分扣减为0 | 检查 `wx_points_rule` 中对应 `rule_code` 的 `points_value` |
| `scene_pack_id` 为NULL | 确认 `wx_points_record` 已执行 ALTER TABLE 扩展脚本 |
