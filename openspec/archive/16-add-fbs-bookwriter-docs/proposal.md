# 提案：FBS-BookWriter 产品文档生成（add-fbs-bookwriter-docs）

## Why

**问题**：FBS-BookWriter 项目的后端 API 已通过 OpenSpec #5（Skill API 网关）、#12（Skill API 对接）、#15（安全加固）实现，但缺少面向第三方开发者的正式产品文档。现有文档分散且存在大量过时描述。

**现状问题**：

| 问题 | 说明 |
|------|------|
| 文档分散 | API 参考散布在 `docs/api/FBS-BUSINESS-API.md` 和代码注释中 |
| DTO 描述不准确 | 响应示例与 `FbsSkillApiController.java` 源码不符（如 `user/info` 缺少多个字段） |
| 错误码混用两层机制 | 文档将认证过滤器的"真实 HTTP 4xx"与控制器的"body.code"混用，SDK 使用者无法正确判断错误 |
| 两阶段 API 说明缺失 | `/usage/start` + `/usage/end` 两阶段模式没有任何失败场景说明 |
| 文档结构不完整 | `SDK-REFERENCE.md` 缺少错误码参考章（§10），变更日志描述不准确 |

**背景**：
- OpenSpec #5（Skill API 网关）定义了 7 个 Skill API 端点
- OpenSpec #12（Skill API 对接）补充了 userId 可选和 `/points/earn`
- OpenSpec #15（安全加固）新增了 HMAC-SHA256 签名和 `X-FBS-Timestamp`/`X-FBS-Signature` Header
- 当前文档基于早期设计，未同步更新

---

## What Changes

**目标**：生成三份准确、完整、内部一致的产品文档，覆盖 FBS-BookWriter 后端 API 的完整参考。

### 交付物

| 文件 | 说明 | 状态 |
|------|------|------|
| `docs/SDK-REFERENCE.md` | SDK 参考文档，含 API 完整参考、认证机制、业务体系、错误码参考、变更日志 | 新增 |
| `docs/QUICK-START.md` | 快速开始指南，含账号注册、API Key 配置、cURL/Python/Node.js 签名示例 | 新增 |
| `docs/ERROR-CODES.md` | 错误码参考，含认证错误、业务错误合同、两层错误机制说明 | 新增 |

### 核心原则

1. **源码优先**：所有 API DTO 以 `FbsSkillApiController.java` 为唯一事实来源，不依赖早期设计文档
2. **两层错误机制统一**：严格区分认证过滤器（真实 HTTP 4xx）与控制器（body.code）错误
3. **三份文档内部一致**：SDK-REFERENCE、QUICK-START、ERROR-CODES 共享相同的错误码描述和术语
4. **变更日志精确**：变更记录与 `openspec/archive/` 目录实际归档内容严格对应

---

## Impact

### 受影响文档（替换）

- `docs/api/FBS-BUSINESS-API.md` — 旧版 API 文档（部分内容被 SDK-REFERENCE.md 覆盖）
- `docs/api/skill-dev-guide.md` — 旧版 Skill 开发指南（内容整合到 SDK-REFERENCE.md）

### 不受影响

- `docs/FBS-DATABASE-SCHEMA.md` — 数据库设计文档
- `docs/FBS-FRONTEND-INTEGRATION-GUIDE.md` — 前端集成指南
- `openspec/archive/` 目录 — 规范归档（只读）

### 依赖

- ✅ OpenSpec #5（Skill API 网关）
- ✅ OpenSpec #12（Skill API 对接）
- ✅ OpenSpec #15（安全加固）
- ✅ `FbsSkillApiController.java` 源码
- ✅ `FbsApiKeyAuthFilter.java` 源码
- ✅ `AjaxResult.java` 源码

---

## Timeline

小型任务（文档生成，无代码改动），预计 0.5 天。
