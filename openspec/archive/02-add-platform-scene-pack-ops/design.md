# Design: add-platform-scene-pack-ops

> **状态**: ✅ 已实施
> **日期**: 2026-04-09

## 架构概览

```
Controller → BusinessService → Mapper
     ↓              ↓           ↓
   DTO校验      业务逻辑      SQL查询
```

## 模块清单

| 文件 | 类型 | 说明 |
|------|------|------|
| FbsScenePackBusinessServiceImpl | Service | 场景包 CRUD + 启用/禁用 |
| FbsAuthCodeBusinessServiceImpl | Service | 授权码生成/启用/撤销 |
| FbsUserPackBusinessServiceImpl | Service | 用户权益查询+统计 |
| FbsScenePackController | REST API | /business/fbs/scene-pack/* |
| FbsAuthCodeController | REST API | /business/fbs/auth-code/* |
| FbsUserPackController | REST API | /business/fbs/user-pack/* |

## 关键设计决策

1. **issuer_id fallback** — 请求未传时取 SecurityUtils.getUserId()
2. **Fail-Closed 语义** — enable/revoke 条件收紧，异常状态一律拒绝
3. **统计字段精简** — totalCount/activeCount/expiredCount/revokedCount（无 exhausted）

## SQL

见 `sql/V20260409__add-platform-scene-pack-ops__sys_menu.sql`
