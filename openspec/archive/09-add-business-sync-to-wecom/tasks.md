# OpenSpec #9 实施任务

> **变更 ID**: `09-add-business-sync-to-wecom`
> **状态**: ✅ 已完成
> **完成时间**: 2026-04-15
> **MVP 范围**: 只实现 `commercial_hub` 同步

---

## 任务清单

### Task 1: 创建 WecomBusinessSyncService 接口

- [x] 1.1 创建接口 `WecomBusinessSyncService.java`
- [x] 1.2 定义 `syncCommercialHub(Long userId, String packCode, int pointsAmount, int remainPoints)` 方法

### Task 2: 实现 WecomBusinessSyncServiceImpl

- [x] 2.1 创建实现类 `WecomBusinessSyncServiceImpl.java`
- [x] 2.2 注入 `WecomWriteService`
- [x] 2.3 实现 `syncCommercialHub()` 方法
- [x] 2.4 实现 `buildCommercialHubRecord()` 字段映射方法
- [x] 2.5 添加 try-catch 异常处理

### Task 3: 集成触发点 - 积分消费

- [x] 3.1 在 `SkillConsumeServiceImpl` 中注入 `WecomBusinessSyncService`（`required=false`）
- [x] 3.2 在 `consume()` 方法成功返回前调用 `syncCommercialHub()`
- [x] 3.3 添加 try-catch 包裹

### Task 4: 单元测试

- [x] 4.1 创建 `WecomBusinessSyncServiceTest.java`
- [x] 4.2 测试 `syncCommercialHub()` 成功场景
- [x] 4.3 测试 `syncCommercialHub()` 失败场景（不影响业务）
- [x] 4.4 测试 `WecomWriteService` 返回失败时的处理

### Task 5: 集成测试

- [x] 5.1 调用积分消费 API，验证 `commercial_hub` Sheet 新增记录
- [x] 5.2 验证同步失败不影响业务主流程

### Task 6: 文档更新

- [x] 6.1 更新 `阶段规划.md` 状态为已完成
- [x] 6.2 更新 `FBS-BUSINESS-API.md` 新增同步说明

---

## 延期任务（不在 MVP 范围）

| # | 任务 | 原因 |
|---|------|------|
| D-1 | `entitlement` 白名单扩展 | MVP 只做 `commercial_hub` |
| D-2 | `entitlement` Sheet ID 配置 | MVP 只做 `commercial_hub` |
| D-3 | `entitlement` 字段映射 | 需要新增字段或推导逻辑 → credits_required 已修复（2026-04-18）：从 `wx_points_rule.points_value` 查询，不再写死 100 |
| D-4 | `entitlement` Upsert 逻辑 | 需要读取能力 + record_id 定位 |
| D-5 | 异步机制 | MVP 简化，使用同步调用 |
| D-6 | 失败重试 | 同步失败记录日志即可 |
| D-7 | 其他子表同步 | MVP 只做核心子表 |

---

## 统计

| 指标 | 数值 |
|------|------|
| 总任务数 | 6 |
| 总步骤数 | 15 |
| 预估工时 | 2h |
| 延期任务 | 7 |
