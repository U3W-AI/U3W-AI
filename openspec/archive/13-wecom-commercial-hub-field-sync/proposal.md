# 提案：commercial_hub 字段补全

## Why

企微智能表格 `commercial_hub` 子表有 24 个字段，但后端 `WecomBusinessSyncServiceImpl.buildCommercialHubRecord()` 只写入 9 个字段，**15 个字段始终为空**。运营查看智能表格时大量空白列，无法支撑数据分析和运营决策。

**背景**：
- `WecomBusinessSyncServiceImpl` 在 OpenSpec #9 中创建，MVP 只映射了 9 个核心字段
- 智能表格在 OpenSpec #7（写入引擎）+ #8（Schema 管理）阶段已建好完整的 24 字段结构
- 消费时后端实际拥有这些数据（packType、hostType、authCode、usageRecordId 等），只是没有传到同步层

**当前状态**：`syncCommercialHub(userId, packCode, pointsAmount, remainPoints)` 只传 4 个参数，无法获取其他 15 个字段的值

**期望状态**：`syncCommercialHub(CommercialHubSyncContext)` 传入完整上下文，`buildCommercialHubRecord()` 输出全部 24 个字段

## What Changes

- 新增 `CommercialHubSyncContext` DTO，封装同步所需上下文
- 扩展 `WecomBusinessSyncService` 接口，新增重载方法（旧签名保留，标记 `@Deprecated`）
- 改造 `WecomBusinessSyncServiceImpl.buildCommercialHubRecord()`，补全 14 个空字段
- **修复 `user_id` 字段格式**：智能表格是 TEXT 类型，当前代码直接传 Long，需改为文本包装
- 改造 `SkillConsumeServiceImpl.consume()` 路径，传递扩展上下文
- **新增 `SkillConsumeServiceImpl.consumeEnterprise()` 同步调用**（⚠️ 当前完全没有！）
- 新增关联查询逻辑（corp_id 从 FbsEnterpriseMember → FbsEnterprise.enterpriseCode 查询），查询失败降级为空值

### 自查发现的关键问题

| # | 问题 | 严重度 |
|---|------|:---:|
| 1 | `consumeEnterprise` 路径完全没有调用 `syncCommercialHub` | **P1** |
| 2 | `user_id` 在智能表格是 TEXT 类型，代码直接传 Long（无文本包装） | **P2** |
| 3 | `code_prefix`/`code_hash` 在 `FbsAuthCode` 实体中不存在，无法关联查询 | **P1** |
| 4 | `corp_id` 在实体层不存在，需从 `FbsEnterpriseMember` → `FbsEnterprise.enterpriseCode` 关联查 | **P1** |
| 5 | `trial_allowed`/`enterprise_only` 在 commercial_hub 是 `SINGLE_SELECT`（非 CHECKBOX），写入格式待确认 | **P2** |

## Impact

### 受影响的规范
- `openspec/specs/platform-scene-pack-ops/spec.md` - 新增 commercial_hub 完整字段映射规范

### 受影响的代码
- `WecomBusinessSyncService.java` - 新增重载方法
- `WecomBusinessSyncServiceImpl.java` - 核心改造：补全字段 + 关联查询
- `SkillConsumeServiceImpl.java` - 两个调用点改造
- 新增 `CommercialHubSyncContext.java` - DTO

### 用户影响
- 运营：智能表格 commercial_hub 数据更完整，可做更精准的分析
- 终端用户：无感知（同步失败不影响业务）

### API 变更
- 无破坏性变更
- `syncCommercialHub(旧4参数签名)` 保留，内部转调新签名（兼容）

### 需要迁移
- [ ] 数据库迁移 — 否
- [ ] API 版本提升 — 否
- [ ] 用户沟通 — 否
- [x] 文档更新 — API 文档中 commercial_hub 字段映射表

## 时间线评估

小（1-2 天）

## 风险

- **关联查询增加延迟**：corp_id 和 authCode 查询是额外 DB 调用 → 所有查询用 try-catch 包裹，失败降级为空值，不阻塞同步
- **旧签名兼容**：旧 4 参数签名内部构建最小 context → 补全字段为默认值/空值，不报错
