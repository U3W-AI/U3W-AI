# Spec Delta: Skill 端 API 对接

> **Change ID**: `12-skill-api-integration`
> **日期**: 2026-04-17
> **类型**: Enhancement（增强现有能力）

---

## 1. Capability 变更

### 1.1 修改现有 Capability

| Capability | 变更类型 | 说明 |
|------------|----------|------|
| `POST /fbs/skill-api/user/info` | **修改** | `userId` 参数改为可选，不传时从 API Key 反查 |
| `POST /fbs/skill-api/usage/consume` | **修改** | `userId` 参数改为可选，不传时从 API Key 反查 |

### 1.2 新增 Capability

| Capability | 类型 | 说明 |
|------------|------|------|
| Skill 端后端 API 调用 | 新增 | Skill 端可调用后端 API 实时扣减积分 |

---

## 2. Requirement 变更

### 2.1 新增 Requirement

#### REQ-12-001: Skill 端积分查询

**描述**: Skill 端可通过 API Key 查询用户积分余额，无需传递 userId。

**验收标准**:
- [ ] Skill 端调用 `/user/info` 不传 userId，后端从 API Key 反查
- [ ] 返回 `{userId, pointsBalance, activatedPacks}`
- [ ] API Key 未绑定用户时返回 403

#### REQ-12-002: Skill 端积分扣减

**描述**: Skill 端可通过 API Key 扣减用户积分，无需传递 userId。

**验收标准**:
- [ ] Skill 端调用 `/usage/consume` 不传 userId，后端从 API Key 反查
- [ ] 扣减成功返回 `{usageRecordId, remainPoints}`
- [ ] API Key 未绑定用户时返回 403

#### REQ-12-003: Fallback 策略

**描述**: 后端 API 不可用时，Skill 端可降级到本地账本。

**验收标准**:
- [ ] 后端 API 超时（3s）后 fallback 到本地账本
- [ ] 本地账本扣减记录标记 `mode: 'local'`
- [ ] 输出警告日志

---

## 3. 现有 Requirement 影响

### 3.1 无破坏性变更

本次变更**向后兼容**：
- `/user/info` 传 `userId` 时行为不变
- `/usage/consume` 传 `userId` 时行为不变
- Skill 端未配置 API Key 时行为不变（使用本地账本）

### 3.2 依赖 Requirement

| Requirement | 来源 | 说明 |
|-------------|------|------|
| API Key 绑定用户 | OpenSpec #11 | 用户自助创建的 API Key 会绑定 user_id |
| API Key 认证 | OpenSpec #5 | FbsApiKeyAuthFilter 校验 `X-FBS-API-Key` |

---

## 4. 数据模型变更

### 4.1 无新增表

本次变更不需要新增数据库表，复用现有：

| 表 | 用途 |
|----|------|
| `fbs_api_key` | API Key 认证（#11 已完成） |
| `sys_user.points` | 积分余额 |
| `fbs_skill_usage_record` | 使用记录（#5 已完成） |

---

## 5. 安全影响

### 5.1 认证机制

- 复用现有 `FbsApiKeyAuthFilter`（`X-FBS-API-Key` Header）
- Skill 端无需管理 userId，由后端从 API Key 反查

### 5.2 权限控制

- 用户自助创建的 API Key 只能操作自己的积分
- 运营端生成的 API Key（user_id 为空）返回 403

---

## 6. 验收对齐

| 验收项 | 对应 Spec |
|--------|-----------|
| `/user/info` userId 可选 | REQ-12-001 |
| `/usage/consume` userId 可选 | REQ-12-002 |
| Skill 端 Fallback 策略 | REQ-12-003 |
| API Key 反查用户 | Capability: Skill API |

---

## 7. 追溯

- **前置变更**: `11-frontend-api-key-management`（用户侧 API Key 管理）
- **依赖接口**: `FbsApiKeyAuthFilter`（`X-FBS-API-Key` 认证）
