# OpenSpec Proposal: add-platform-scene-pack-ops

## 元信息

| 字段 | 值 |
|------|-----|
| **Proposal ID** | 002 |
| **标题** | 平台侧场景包与授权码运营 |
| **状态** | ✅ APPROVED → IMPLEMENTED |
| **创建日期** | 2026-04-09 |
| **实施完成日期** | 2026-04-09 |
| **关联规范** | `specs/platform-scene-pack-ops/spec.md` |

## 变更范围

在 OpenSpec #1（fbs-rights-foundation）已建立的领域底座之上，实现平台运营侧的完整 CRUD 能力：

1. **场景包管理** — 列表/详情/创建/更新/启用/禁用
2. **授权码管理** — 批量生成/列表查询/启用/撤销
3. **用户权益查询** — 权益列表/统计概览
4. **菜单权限** — sys_menu 后台管理入口

## MVP 边界

- ✅ 后端 BusinessService + Controller + Mapper
- ❌ 前端 Vue 页面
- ❌ 企业侧 ZIP 分发
- ❌ 场景包版本管理
