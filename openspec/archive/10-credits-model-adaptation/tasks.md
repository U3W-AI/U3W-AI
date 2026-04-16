# OpenSpec #10 Tasks: 积分模型适配与 LedgerSync

> **Change ID**: `credits-model-adaptation`
> **日期**: 2026-04-16
> **预估工时**: 2.5 小时

---

## Task 1: 数据表扩展（0.5h）

### 1.1 添加字段和索引
- [ ] 为 `wx_points_record` 表添加 `event_id` 字段（VARCHAR(128)）
- [ ] 添加唯一索引 `uk_event_id`
- [ ] 更新 `PointsRecord.java` 实体类，添加 `eventId` 字段及 getter/setter（与现有手写风格一致，不使用 Lombok）

### 1.2 Mapper 扩展
- [ ] 在 `PointsRecordMapper.java` 中新增 `selectByEventId()` 方法
- [ ] 在 `PointsRecordMapper.xml` 中：
  - 新增 `selectByEventId` SQL
  - 在 `insertPointsRecord` 中添加 `<if test="eventId != null and eventId != ''">event_id,</if>` 条件列（与现有 `usageRecordId` 写法一致）

---

## Task 2: 服务层扩展（1h）

### 2.1 接口扩展
- [ ] 在 `IPointsService.java` 中新增方法签名：
  ```java
  public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount,
                                 Long scenePackId, String usageRecordId, String eventId);
  ```

### 2.2 实现幂等逻辑
- [ ] 在 `PointsServiceImpl.java` 中实现新方法
- [ ] 幂等检查：若 `eventId` 已存在，返回既有余额
- [ ] 插入记录时写入 `event_id` 字段

---

## Task 3: LedgerSync 进程（0.5h）

### 3.1 Python 脚本
- [ ] 创建 `ledgersync/ledgersync.py`（主进程）
- [ ] 复用 `/fbs/skill-api/user/info` 端点（取 `data.pointsBalance`）
- [ ] 请求头使用 `X-FBS-API-Key`（非 `X-API-Key`）
- [ ] 支持环境变量配置：`API_BASE_URL` / `API_KEY` / `USER_ID` / `SYNC_INTERVAL` / `OUTPUT_PATH`
- [ ] 写入 `credits-ledger.json`

---

## Task 4: 测试（0.5h）

### 4.1 单元测试
- [ ] `PointsServiceImplTest`：测试幂等逻辑
  - `testChangePoints_WithEventId_Idempotent()`
  - `testChangePoints_WithoutEventId_Normal()`

### 4.2 LedgerSync 测试
- [ ] 手动运行 LedgerSync 进程
- [ ] 验证 `credits-ledger.json` 文件生成

---

## 任务依赖关系

```
Task 1 (数据表扩展)
   ↓
Task 2 (服务层扩展)
   ↓
Task 3 (LedgerSync)
   ↓
Task 4 (测试)
```

---

## 验收清单

- [ ] `wx_points_record.event_id` 字段添加成功
- [ ] `IPointsService.changePoints()` 支持 `eventId` 参数（幂等）
- [ ] LedgerSync 进程可以轮询 `/user/info` 并写入本地 JSON
- [ ] 单元测试通过（幂等逻辑）
- [ ] 集成测试通过（Skill 端可读取 `credits-ledger.json`）

---

## 变更文件清单

| 类型 | 文件路径 | 变更说明 |
|------|----------|----------|
| SQL | `sql/openspec/10_credits_ledger.sql` | 添加 event_id 字段 |
| Entity | `WxFbsir-business/.../point/domain/PointsRecord.java` | 添加 eventId 字段 |
| Mapper | `WxFbsir-business/.../point/mapper/PointsRecordMapper.java` | 新增 selectByEventId() |
| Mapper XML | `WxFbsir-business/.../point/mapper/PointsRecordMapper.xml` | 更新 SQL |
| Service | `WxFbsir-business/.../point/service/IPointsService.java` | 新增方法签名 |
| Service Impl | `WxFbsir-business/.../point/service/impl/PointsServiceImpl.java` | 实现幂等逻辑 |
| Script | `ledgersync/ledgersync.py` | 新增 LedgerSync 进程 |
| Test | `WxFbsir-business/.../point/service/impl/PointsServiceImplTest.java` | 新增幂等测试 |
