# OpenSpec #13：commercial_hub 字段补全 — 任务

> **变更 ID**: `13-wecom-commercial-hub-field-sync`
> **状态**: 待实施
> **创建**: 2026-04-18

---

## 任务

| # | 任务 | 优先级 | 状态 | 说明 |
|---|------|:---:|:---:|------|
| 1 | 新增 `CommercialHubSyncContext` DTO | P1 | 🔲 | 字段：userId, packCode, pointsAmount, remainPoints, hostType, usageRecordId, packId, authCode, pointsRuleCode, packType |
| 2 | 扩展 `WecomBusinessSyncService` 接口 | P1 | 🔲 | 新增重载 `syncCommercialHub(CommercialHubSyncContext)`，旧签名标记 `@Deprecated` |
| 3 | 改造 `WecomBusinessSyncServiceImpl` | P1 | 🔲 | ① 修复 user_id 文本格式 ② 补全 14 字段 ③ 注入 EnterpriseMemberMapper+EnterpriseMapper+AuthCodeMapper ④ 实现 resolveCorpId/resolveCodeType |
| 4 | 改造 `SkillConsumeServiceImpl.consume()` | P1 | 🔲 | 构建 CommercialHubSyncContext 传参 |
| 5 | 新增 `consumeEnterprise()` 同步调用 | P1 | 🔲 | ⚠️ 当前完全没有调用 syncCommercialHub！需新增同步调用（hostType=ENTERPRISE, source=ENTERPRISE, pointsAmount=0） |
| 6 | 单元测试 | P1 | 🔲 | 全字段验证 + user_id 文本格式 + 关联查询兜底 + 旧签名兼容 |
