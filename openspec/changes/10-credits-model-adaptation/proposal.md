# OpenSpec #10 Proposal: 积分模型适配与 LedgerSync

> **Change ID**: `credits-model-adaptation`
> **阶段**: 第四阶段 — 积分与乐包体系打通
> **日期**: 2026-04-16
> **依赖**: OpenSpec #6（企微 CLI 集成）

---

## 一、Why（为什么做）

### 1.1 背景

技术方案文档（`u3wv2-fbs_skill_后台打通-技术方案.pdf`）定义了核心架构：

- **U3WV2（WxFbsir）**：唯一账务权威，所有积分消费在 DB 落库
- **FBS-BookWriter Skill**：AI 辅助写作包，需要本地可读的积分余额
- **设计原则**：DB 权威 + Outbox 异步投影 + 本地文件最终一致

当前缺口：
1. **缺少幂等控制**：`FIRST_INSTALL` / `DAILY_LOGIN` 等行为事件需要去重
2. **主机侧无同步机制**：Skill 需要离线/弱网访问积分数据
3. **缺少余额查询 API**：Skill API 只有用户信息查询，无专门的积分余额端点

### 1.2 目标

1. 扩展 **IPointsService**：增加 `event_id` 参数支持幂等控制
2. 新增 **Skill API 端点**：`/fbs/skill-api/credits/balance` 供 Skill 端查询余额
3. 实现 **LedgerSync 进程**：主机侧同步，写入本地 JSON
4. 最小改动：复用现有 `wx_points_record` 表和 `IPointsService` 逻辑

---

## 二、What Changes（做什么）

### 2.1 数据模型扩展

#### wx_points_record 表扩展

**新增字段**：
| 字段 | 类型 | 说明 |
|------|------|------|
| `event_id` | VARCHAR(128) | 幂等键（事件唯一标识） |

**新增索引**：
- `uk_event_id` UNIQUE (event_id) — 幂等控制

**SQL**：
```sql
-- MySQL 5.7 兼容写法（使用存储过程）
DELIMITER $$
CREATE PROCEDURE add_event_id_if_not_exists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() 
        AND TABLE_NAME = 'wx_points_record' 
        AND COLUMN_NAME = 'event_id'
    ) THEN
        ALTER TABLE wx_points_record ADD COLUMN event_id VARCHAR(128) COMMENT '幂等键';
        ALTER TABLE wx_points_record ADD UNIQUE KEY uk_event_id (event_id);
    END IF;
END$$
DELIMITER ;
CALL add_event_id_if_not_exists();
DROP PROCEDURE IF EXISTS add_event_id_if_not_exists;
```

### 2.2 服务层扩展

#### IPointsService 新增方法

```java
/**
 * 积分变动（幂等）
 * 
 * @param userId 用户ID
 * @param ruleCode 规则编码（空表示免费包）
 * @param changeAmount 积分变动值
 * @param scenePackId 场景包ID
 * @param usageRecordId 使用记录幂等键
 * @param eventId 事件幂等键（可选，用于 FIRST_INSTALL / DAILY_LOGIN 等行为去重）
 * @return 结果
 */
public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount,
                               Long scenePackId, String usageRecordId, String eventId);
```

**实现要点**：
- 若 `eventId != null` 且已存在 → 直接返回成功（幂等）
- 若 `eventId != null` 且不存在 → 正常执行积分变动，插入时写入 `event_id`
- 若 `eventId == null` → 走原有逻辑（不检查幂等）

### 2.3 API 扩展

#### Skill API 新增端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/fbs/skill-api/credits/balance` | 查询用户积分余额 |

**请求**：
```json
{
  "userId": 1
}
```

**响应**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": 1,
    "balance": 1000
  }
}
```

**认证**：
- 复用现有 `/fbs/skill-api/**` 的 API Key 认证机制
- 无需新增 SecurityConfig 配置

### 2.4 event_id 生成规则

| event_type | event_id 格式 | 示例 |
|------------|---------------|------|
| FIRST_INSTALL | `first_install_{userId}` | `first_install_1` |
| DAILY_LOGIN | `daily_login_{userId}_{date}` | `daily_login_1_2026-04-16` |
| SKILL_CONSUME | `skill_consume_{usageId}` | `skill_consume_12345` |

### 2.5 LedgerSync 进程

#### 设计目标

- 独立进程，部署在福帮手主机侧
- 定期调用 Skill API，将积分余额写入本地 JSON
- 供 Skill 端离线/弱网访问

#### 本地文件契约

`credits-ledger.json`：
```json
{
  "user_id": 1,
  "balance": 1000,
  "last_updated": "2026-04-16T12:00:00Z",
  "sync_version": "v1.0.0"
}
```

---

## 三、Impact（影响范围）

### 3.1 受影响模块

| 模块 | 影响 |
|------|------|
| `wx_points_record` | 新增 `event_id` 字段 |
| `IPointsService` | 新增 `changePoints()` 幂等重载 |
| `FbsSkillApiController` | 新增 `/credits/balance` 端点 |
| `LedgerSync` | 新增独立进程（Python） |

### 3.2 不在本 OpenSpec 范围内

- WebSocket 推送（Phase 2）
- 增量同步优化（Phase 2）
- 新建积分账本表（复用现有 `wx_points_record`）

---

## 四、MVP 验收标准

1. ✅ `wx_points_record.event_id` 字段添加成功
2. ✅ `IPointsService.changePoints()` 支持 `eventId` 参数（幂等）
3. ✅ `/fbs/skill-api/credits/balance` API 返回用户积分
4. ✅ LedgerSync 进程可以轮询 API 并写入本地 JSON
5. ✅ 单元测试覆盖幂等逻辑
6. ✅ 集成测试：Skill 端可读取 `credits-ledger.json` 获取余额

---

## 五、任务拆分（tasks.md 预告）

### Task 1：数据表扩展
- [ ] 为 `wx_points_record` 添加 `event_id` 字段和唯一索引

### Task 2：服务层扩展
- [ ] 扩展 `PointsServiceImpl.changePoints()` 方法签名
- [ ] 实现幂等检查逻辑

### Task 3：Skill API 扩展
- [ ] 在 `FbsSkillApiController` 中新增 `/credits/balance` 端点

### Task 4：LedgerSync 进程
- [ ] 编写 LedgerSync 脚本（Python）

### Task 5：测试
- [ ] 幂等逻辑单元测试
- [ ] LedgerSync 集成测试

---

## 六、参考资料

- 技术方案文档：`u3wv2-fbs_skill_后台打通-技术方案.pdf`
- 阶段规划：`quest/阶段规划.md`
- OpenSpec #6：wecom-cli 集成
