# OpenSpec Proposal: add-fbs-rights-foundation

## 元信息

| 字段 | 值 |
|------|-----|
| **Proposal ID** | 001 |
| **标题** | 福帮手权益与场景包领域底座 |
| **状态** | ✅ APPROVED → IMPLEMENTED |
| **创建日期** | 2026-04-08 |
| **实施完成日期** | 2026-04-08 |
| **关联规范** | `specs/fbs-rights-foundation/spec.md` |

## 变更范围

建立福帮手 FBS 权益体系的核心领域底座：

1. **数据库表** — fbs_scene_pack / fbs_auth_code / fbs_user_pack / fbs_usage_record
2. **核心服务** — 权益校验 / 积分扣减 / 使用记录
3. **权限基础** — 领域实体、枚举、Mapper

## MVP 边界

- ✅ 4 表 + 核心业务逻辑
- ❌ 平台运营 CRUD（#2 范围）
- ❌ 企业侧分发
