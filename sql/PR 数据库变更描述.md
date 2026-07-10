# PR 数据库变更说明

> **最新更新日期：2026-07-10**

## 概述

本 PR 涉及数据库变更，共包含 **14 个 SQL 文件**，累计变更：

| 变更类型 | 数量 |
|---------|------|
| 新建表 | **10 张** |
| ALTER 表（新增字段/索引） | **7 处** |
| 新增菜单记录 | **30+ 条** |

> 20260710 文件是幂等修复迁移，不新增表或菜单，仅把已存在且错误挂载的 `FBS运营管理` 菜单移到根节点。

---

## 一、新建表（10 张）

| 序号 | 表名 | 说明 | 文件 |
|------|------|------|------|
| 1 | `fbs_scene_pack` | 场景包主表（pack_code, pack_name, owner_type, status 等） | V20260408 |
| 2 | `fbs_auth_code` | 授权码表（auth_code, available/status 双状态机） | V20260408 |
| 3 | `fbs_user_pack` | 用户场景包关系表（user_id ↔ pack_id） | V20260408 |
| 4 | `fbs_skill_usage_record` | Skill 使用记录表（usage_record_id 幂等键） | V20260408 |
| 5 | `fbs_enterprise` | 企业组织主表（enterprise_code, contact_info） | V20260409 |
| 6 | `fbs_enterprise_pack` | 企业已获场景包表（**含企业级配额 packQuota/usedQuota**） | V20260409 |
| 7 | `fbs_member_pack` | 成员场景包授权表（**纯授权凭证，不存配额**） | V20260409 |
| 8 | `fbs_enterprise_member` | 企业成员关联表（用户↔企业关系） | V20260409 |
| 9 | `fbs_api_key` | API Key 管理表（rate_limit, status） | V20260411 |
| 10 | `fbs_wecom_sync_log` | 企微同步日志表（MVP，只记录 READ 类型） | V20260413 |

---

## 二、ALTER 表变更（7 处）

| 序号 | 表 | 变更内容 | 文件 |
|------|------|---------|------|
| 1 | `wx_points_record` | 新增 `scene_pack_id` + `usage_record_id` 字段 | V20260408 |
| 2 | `fbs_scene_pack` | 新增 `idx_owner_type` 索引 | V20260409 |
| 3 | `fbs_skill_usage_record` | 新增 `idx_host_type` 索引 | V20260409 |
| 4 | `fbs_user_pack` | 新增审计字段（operator, request_id, operate_time） | V20260410 |
| 5 | `wx_points_record` | 新增 `event_id` + `uk_event_id` 唯一索引（**幂等控制**） | V20260416 |
| 6 | `fbs_api_key` | 新增 `user_id` + `last_used_at` 字段 + `idx_user_id` 索引 | V20260417 |
| 7 | `fbs_api_key` | `api_key` 扩容 VARCHAR(64) → VARCHAR(128)（**Hex(32B)格式**） | V20260420 |

---

## 三、新增菜单记录（30+ 条）

### FBS 平台运营菜单（V20260409）

| 父菜单 | 子菜单/按钮 | 权限标识 |
|--------|------------|---------|
| FBS运营管理 | 场景包管理 | `business:fbs:scenePack:list` |
| FBS运营管理 | 场景包新增 | `business:fbs:scenePack:add` |
| FBS运营管理 | 场景包编辑 | `business:fbs:scenePack:edit` |
| FBS运营管理 | 场景包发布 | `business:fbs:scenePack:publish` |
| FBS运营管理 | 场景包下架 | `business:fbs:scenePack:unpublish` |
| FBS运营管理 | 场景包查询 | `business:fbs:scenePack:query` |
| FBS运营管理 | 授权码管理 | `business:fbs:authCode:list` |
| FBS运营管理 | 授权码生成 | `business:fbs:authCode:generate` |
| FBS运营管理 | 授权码禁用 | `business:fbs:authCode:disable` |
| FBS运营管理 | 授权码启用 | `business:fbs:authCode:enable` |
| FBS运营管理 | 授权码撤销 | `business:fbs:authCode:revoke` |
| FBS运营管理 | 用户权益查询 | `business:fbs:userPack:list` |
| FBS运营管理 | 用户权益详情 | `business:fbs:userPack:query` |

### FBS 企业侧菜单（V20260410）

| 父菜单 | 子菜单/按钮 | 权限标识 |
|--------|------------|---------|
| 企业管理 | 企业列表 | `business:fbs:enterprise:list` |
| 企业管理 | 企业详情 | `business:fbs:enterprise:query` |
| 企业管理 | 添加企业 | `business:fbs:enterprise:add` |
| 企业管理 | 编辑企业 | `business:fbs:enterprise:edit` |
| 企业管理 | 禁用企业 | `business:fbs:enterprise:disable` |
| 企业场景包 | 企业包列表 | `business:fbs:enterprisePack:list` |
| 企业场景包 | 企业包详情 | `business:fbs:enterprisePack:query` |
| 企业场景包 | 分发企业包 | `business:fbs:enterprisePack:grant` |
| 企业场景包 | 回收企业包 | `business:fbs:enterprisePack:revoke` |
| 企业成员 | 成员列表 | `business:fbs:enterpriseMember:list` |
| 企业成员 | 成员详情 | `business:fbs:enterpriseMember:query` |
| 企业成员 | 添加成员 | `business:fbs:enterpriseMember:add` |
| 企业成员 | 移除成员 | `business:fbs:enterpriseMember:remove` |

### 用户自助菜单（V20260411）

| 父菜单 | 子菜单/按钮 | 权限标识 |
|--------|------------|---------|
| 我的权益中心 | 我的权益 | `my:fbs:packs:list` |
| 我的权益中心 | 我的权益查询 | `my:fbs:packs:query` |
| 我的权益中心 | 激活授权码 | `my:fbs:authCode:activate` |
| 我的权益中心 | 可领取场景包 | `my:fbs:scenePack:claimable` |
| 我的权益中心 | 场景包领取 | `my:fbs:scenePack:claim` |

### 用户 API Key 菜单（V20260417）

| 父菜单 | 子菜单/按钮 | 权限标识 |
|--------|------------|---------|
| 我的 API Keys | 创建 Key | `my:apikey:create` |
| 我的 API Keys | 禁用 Key | `my:apikey:toggle` |
| 我的 API Keys | 删除 Key | `my:apikey:delete` |

### FBS 运营菜单父节点修复（V20260710）

`sql/update_20260710_修复FBS运营菜单父节点.sql` 将 `FBS运营管理` 的 `parent_id` 修正为 `0`，仅在菜单类型为 `M` 且当前父节点非根节点时更新。该迁移可重复执行，验证结果应为 `menu_type=M`、`parent_id=0`。

---

## 四、配额模型说明

企业级配额采用**单一真相源**设计：

- **配额存储层**：`fbs_enterprise_pack.packQuota / usedQuota`
- **授权凭证层**：`fbs_member_pack` 仅记录授权资格，**不存独立配额**
- **扣减计算**：统一在 `fbs_enterprise_pack` 层面

---

## 五、回滚说明

20260710 修复迁移只更新菜单父节点；若需回滚，应使用升级前数据库备份恢复，或由管理员将该菜单恢复到经确认的父菜单，不建议直接删除 FBS 菜单树。

如需回滚，按相反顺序执行以下 SQL 文件：

| 顺序 | 文件 | 回滚操作 |
|------|------|---------|
| 1 | V20260420 | ALTER TABLE fbs_api_key MODIFY api_key VARCHAR(64) |
| 2 | V20260417 菜单 | DELETE FROM sys_menu WHERE perms LIKE 'my:apikey%' |
| 3 | V20260417 字段 | DROP INDEX idx_user_id; ALTER TABLE fbs_api_key DROP COLUMN user_id, last_used_at |
| 4 | V20260416 | DROP INDEX uk_event_id; ALTER TABLE wx_points_record DROP COLUMN event_id |
| 5 | V20260413 | DROP TABLE fbs_wecom_sync_log |
| 6 | V20260411 菜单 | DELETE FROM sys_menu WHERE perms LIKE 'my:fbs%' |
| 7 | V20260411 表 | DROP TABLE fbs_api_key |
| 8 | V20260410 | ALTER TABLE fbs_user_pack DROP COLUMN operator, request_id, operate_time |
| 9 | V20260410 菜单 | DELETE FROM sys_menu WHERE perms LIKE 'business:fbs:enterprise%' |
| 10 | V20260409 菜单 | DELETE FROM sys_menu WHERE perms LIKE 'business:fbs%' |
| 11 | V20260409 | DROP TABLE fbs_member_pack, fbs_enterprise_member, fbs_enterprise_pack, fbs_enterprise; DROP INDEX idx_owner_type, idx_host_type |
| 12 | V20260408 | DROP TABLE fbs_skill_usage_record, fbs_user_pack, fbs_auth_code, fbs_scene_pack; ALTER TABLE wx_points_record DROP COLUMN scene_pack_id, usage_record_id |
