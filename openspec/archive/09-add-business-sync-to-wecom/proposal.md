# OpenSpec #9: 业务数据同步到企微智能表格

> **变更 ID**: `09-add-business-sync-to-wecom`
> **状态**: ✅ 已完成
> **创建时间**: 2026-04-15
> **完成时间**: 2026-04-15

---

## 一、变更背景

### 1.1 现状

- **#6 完成了企微智能表格读取能力**：通过 `wecom-cli` 读取数据，写入 `fbs_wecom_sync_log.snapshot_json`
- **#7 完成了写入能力**：向智能表格添加记录，支持 `meta` 和 `commercial_hub` 两个 Sheet
- **#8 完成了结构管理**：子表/字段的 CRUD + 记录的 update/delete

但 MySQL 业务表和企微智能表格之间**没有自动同步**。数据需要手动在两处维护，容易出现不一致。

### 1.2 目标

实现 **MySQL 业务表 → 企微智能表格的单向同步**，打通业务数据到 Skill 配置层的数据流。

---

## 二、MVP 范围

### 2.1 本次实现

| 同步内容 | 触发点 | 同步目标 |
|----------|--------|----------|
| **积分消费流水** | 用户消费积分后 | `commercial_hub` Sheet |

### 2.2 延期内容（不在 MVP 范围）

| 同步内容 | 延期原因 |
|----------|----------|
| **场景包权益配置** (`entitlement`) | 需要扩展写入白名单 + 添加 `entitlementSheetId` 配置 + 字段映射逻辑 |

### 2.3 延期原因说明

`entitlement` 同步需要以下基础设施扩展（当前不存在）：

1. **写入白名单扩展**：`WecomWriteServiceImpl` 当前只允许 `meta` + `commercial_hub`
2. **读取能力扩展**：`WecomSyncServiceImpl` 当前只允许读取 `meta` + `commercial_hub`
3. **Sheet ID 配置**：`application.yml` 缺少 `wecom.sheet.sheet-ids.entitlement`
4. **字段映射逻辑**：`FbsScenePack` 没有 `creditsRequired`/`trialAllowed`/`enterpriseOnly` 字段，需要从 `pointsRuleCode` 推导
5. **Upsert 逻辑**：需要"按 genre 查询 → 存在则更新，不存在则新增"，需要 `record_id` 定位能力

**决策**：为了保证 MVP 最小可跑通，`entitlement` 延期到后续版本。

---

## 三、同步策略

### 3.1 积分消费同步（commercial_hub）

**触发点**：`SkillConsumeServiceImpl.consume()` 执行完成后

**同步方式**：同步调用（非异步），失败不影响业务

**数据流向**：
```
用户消费积分 → SkillConsumeServiceImpl.consume()
                        ↓
              WecomBusinessSyncService.syncCommercialHub()
                        ↓
              WecomWriteService.writeRecords("commercial_hub", records)
                        ↓
              企微智能表格（新增一条记录）
```

**字段映射**（10 个字段）：

| 智能表格字段 | MySQL 来源 | 说明 |
|--------------|------------|------|
| `record_id` | 自动生成 | UUID |
| `record_type` | 固定值 | `SKILL_USAGE` |
| `user_id` | 入参 `userId` | 消费用户 ID |
| `genre` | 入参 `packCode` | 场景包编码 |
| `event` | 固定值 | `CONSUME` |
| `delta` | 入参 `pointsAmount` | 本次消费积分（负数） |
| `balance_after` | 入参 `remainPoints` | 变更后余额 |
| `credits_required` | 入参 `pointsAmount` | 所需积分 |
| `status` | 固定值 | `SUCCESS` |
| `created_at` | 当前时间戳 | 格式 `yyyy-MM-dd HH:mm:ss` |

### 3.2 失败处理

- 同步失败时，**业务仍然成功**（积分已扣除）
- 日志表记录失败原因（`fbs_wecom_sync_log.status=FAILED`）
- 不阻塞业务主流程

---

## 四、依赖关系

### 4.1 直接依赖

| 依赖项 | 说明 |
|--------|------|
| **#7 WecomWriteService** | 写入能力（可直接复用 `commercial_hub` 白名单） |

### 4.2 不依赖（延期）

| 依赖项 | 说明 |
|--------|------|
| #7 白名单扩展 | `entitlement` 延期，不需要扩展白名单 |

---

## 五、验收标准

### 5.1 功能验收

1. 用户消费积分后，`commercial_hub` Sheet 自动新增一条记录
2. 记录包含 10 个字段，字段值与消费行为一致
3. 同步失败不影响积分扣除

### 5.2 测试验收

1. 单元测试：`WecomBusinessSyncServiceTest` 覆盖成功/失败场景
2. 集成测试：调用消费 API 后验证企微侧数据

---

## 六、后续计划

| 版本 | 内容 |
|------|------|
| **v1.1** | 扩展 `entitlement` 同步（需要扩展白名单 + 读取能力 + Upsert 逻辑） |
| **v1.2** | 其他子表同步（`quality`/`outline`/`init` 等） |
| **v2.0** | 双向同步（企微 → MySQL） |

---

## 七、风险与约束

| 风险 | 缓解措施 |
|------|----------|
| 同步失败导致数据不一致 | 日志记录 + 后续手工修复 |
| 企微 API 不可用 | 失败记录日志，不影响业务 |
| 并发写入冲突 | 企微侧保证幂等性（record_id 唯一） |
