# Design: add-fbs-rights-foundation

> **状态**: ✅ 已实施
> **日期**: 2026-04-08

## 数据库设计（4表）

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| fbs_scene_pack | 场景包定义 | pack_name / pack_type / points_cost / status |
| fbs_auth_code | 授权码 | auth_code(UNIQUE) / target_type / issuer_id / max_activations / status |
| fbs_user_pack | 用户权益 | user_id / pack_id / auth_code / available / expire_time / status |
| fbs_usage_record | 使用记录 | user_id / points_cost / use_type / use_record_id(幂等键) |

## SQL 迁移

见 `sql/V20260408__add-fbs-rights-foundation__create_tables.sql`
