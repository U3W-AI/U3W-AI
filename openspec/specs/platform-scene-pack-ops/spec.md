# 平台侧场景包与授权码运营 - 规范

> **规范版本**：v1.0（MVP）
> **归档日期**：2026-04-09
> **来源 OpenSpec**：add-platform-scene-pack-ops

---

## 概述

本文档定义平台侧对 FBS 场景包、授权码和用户权益的运营能力规范。

> **#2 MVP 范围边界**：实现后端运营 API、BusinessService、Mapper 列表查询和权限控制，以及前端运营 Vue 页面（场景包管理、授权码管理、用户权益查询）；不包含企业侧分发、用户自助中心、场景包版本管理、积分冻结/回滚和 fail-open 策略。

---

## 一、场景包运营管理

### Requirement: 场景包运营管理

系统 SHALL 支持平台管理员对场景包进行发布、下架、编辑、详情查询和分页列表查询。

> OpenSpec #1 已提供场景包基础创建与内部查询能力；本规范补充平台运营侧管理能力。

#### Scenario: 场景包分页列表查询

```text
GIVEN 管理员调用 getScenePackPage(request)
WHEN  提交分页请求（pageNum, pageSize, status, packType, ownerType）
THEN  系统 SHALL 返回分页结果（total, rows）
AND   每条记录包含：id, packCode, packName, packType, status, pointsRuleCode, createdBy, createTime
```

#### Scenario: 场景包详情查询

```text
GIVEN 管理员调用 getScenePackDetail(id)
WHEN  场景包存在
THEN  系统 SHALL 返回场景包详情
AND   详情包含：packCode, packName, packType, ownerType, status, visibleScope, pointsRuleCode, contentSnapshot, currentVersion
```

#### Scenario: 场景包编辑

```text
GIVEN 管理员调用 updateScenePack(id, request)
AND   场景包 status IN (0, 1)
WHEN  更新 packName, pointsRuleCode, contentSnapshot, visibleScope 等字段
THEN  系统 SHALL 更新对应字段并返回成功
AND IF status = 2
THEN  系统 SHALL 返回失败（Fail-Closed：已下架不可编辑）
```

#### Scenario: 场景包发布

```text
GIVEN 管理员调用 publishScenePack(id)
AND   场景包 status = 0
WHEN  执行发布操作
THEN  系统 SHALL 更新 status = 1 并返回成功
AND IF status != 0
THEN  系统 SHALL 返回失败（Fail-Closed）
```

#### Scenario: 场景包下架

```text
GIVEN 管理员调用 unpublishScenePack(id)
AND   场景包 status = 1
WHEN  执行下架操作
THEN  系统 SHALL 更新 status = 2 并返回成功
AND IF status != 1
THEN  系统 SHALL 返回失败（Fail-Closed）
```

---

## 二、授权码运营管理

### Requirement: 授权码运营管理

系统 SHALL 支持平台管理员对授权码进行分页查询、批量生成、禁用、启用和撤销。

> `available` 是管理层开关，`status` 是使用状态；两者是独立维度。

#### Scenario: 授权码分页列表查询

```text
GIVEN 管理员调用 getAuthCodePage(request)
WHEN  提交分页请求（pageNum, pageSize, targetId, available, status, issuerType）
THEN  系统 SHALL 返回分页结果（total, rows）
AND   每条记录包含：id, authCode, targetType, targetId, available, status, maxActivations, activatedCount, deadline
```

#### Scenario: 批量生成授权码

```text
GIVEN 管理员调用 generateAuthCodeBatch(count, targetType, targetId, maxActivations, deadline)
WHEN  count >= 1
THEN  系统 SHALL 生成 count 个唯一授权码
AND   每个授权码默认 available = 1, status = 0, activated_count = 0
AND   返回生成结果列表
```

#### Scenario: 禁用授权码

```text
GIVEN 管理员调用 disableAuthCode(id)
AND   available = 1
WHEN  执行禁用
THEN  系统 SHALL 更新 available = 0
AND IF available = 0
THEN  系统 SHALL 返回失败（Fail-Closed）
```

#### Scenario: 启用授权码

```text
GIVEN 管理员调用 enableAuthCode(id)
AND   available = 0
AND   status IN (0, 1)
WHEN  执行启用
THEN  系统 SHALL 更新 available = 1
AND IF status IN (2, 3, 4)
THEN  系统 SHALL 返回失败（Fail-Closed：已用尽、已过期、已撤销不可启用）
```

#### Scenario: 撤销授权码

```text
GIVEN 管理员调用 revokeAuthCode(id)
AND   status != 4
WHEN  执行撤销
THEN  系统 SHALL 更新 status = 4
AND IF status = 4
THEN  系统 SHALL 返回失败（Fail-Closed：不可重复撤销）
```

---

## 三、用户权益查询

### Requirement: 用户权益查询

系统 SHALL 支持平台管理员分页查询用户已开通的场景包权益，并查看权益统计。

#### Scenario: 用户权益分页列表查询

```text
GIVEN 管理员调用 getUserPackPage(request)
WHEN  提交分页请求（pageNum, pageSize, userId, status）
THEN  系统 SHALL 返回分页结果（total, rows）
AND   每条记录包含：id, userId, packId, packName, sourceType, status, activatedAt, expiresAt
AND   通过 JOIN fbs_scene_pack 获取 packName
```

#### Scenario: 用户权益统计

```text
GIVEN 管理员调用 getUserPackStats(userId)
WHEN  查询指定用户的权益统计
THEN  系统 SHALL 返回：
  - totalCount
  - activeCount（status = 1）
  - expiredCount（status = 2）
  - revokedCount（status = 3）
```

---

## 四、权限与接口边界

### Requirement: 平台侧权限控制

系统 SHALL 复用若依权限体系，对平台运营接口施加 `@PreAuthorize` 权限控制。

#### Scenario: 场景包权限控制

```text
GIVEN 平台运营接口被调用
WHEN  调用场景包相关接口
THEN  系统 SHALL 校验以下权限之一：
  - business:fbs:scenePack:list
  - business:fbs:scenePack:query
  - business:fbs:scenePack:add
  - business:fbs:scenePack:edit
  - business:fbs:scenePack:publish
  - business:fbs:scenePack:unpublish
```

#### Scenario: 授权码权限控制

```text
GIVEN 平台运营接口被调用
WHEN  调用授权码相关接口
THEN  系统 SHALL 校验以下权限之一：
  - business:fbs:authCode:list
  - business:fbs:authCode:generate
  - business:fbs:authCode:disable
  - business:fbs:authCode:enable
  - business:fbs:authCode:revoke
```

#### Scenario: 用户权益权限控制

```text
GIVEN 平台运营接口被调用
WHEN  调用用户权益相关接口
THEN  系统 SHALL 校验以下权限之一：
  - business:fbs:userPack:list
  - business:fbs:userPack:query
```

---

## 明确不在本 OpenSpec 范围

以下能力延期至后续 OpenSpec：

- 场景包版本管理（`fbs_pack_version`）
- 积分冻结 / 确认扣减 / 回滚
- 企业分发（`fbs_enterprise` / `fbs_enterprise_user`）
- 用户自助前端页面
- fail-open 策略

---

## 五、前端运营页面（MVP）

### Requirement: 运营前端页面

系统 SHALL 提供三个若依风格 Vue 3 页面，供平台管理员在后台运营场景包、授权码和用户权益。

#### 文件清单

| 类型 | 路径 | 说明 |
|------|------|------|
| API | `src/api/business/fbs/scenePack.js` | 场景包 CRUD + 发布/下架 |
| API | `src/api/business/fbs/authCode.js` | 授权码生成 + 启用/禁用/撤销 |
| API | `src/api/business/fbs/userPack.js` | 用户权益列表 + 统计 + 详情 |
| 页面 | `src/views/business/fbs/scenePack/index.vue` | 场景包管理（CRUD + 状态切换） |
| 页面 | `src/views/business/fbs/authCode/index.vue` | 授权码管理（批量生成 + 状态操作） |
| 页面 | `src/views/business/fbs/userPack/index.vue` | 用户权益查询（列表 + 统计卡片） |

#### 菜单配置

| 菜单 | component | 权限标识 |
|------|-----------|----------|
| FBS运营管理（目录） | NULL | — |
| 场景包管理 | `business/fbs/scenePack/index` | `business:fbs:scenePack:list` |
| 授权码管理 | `business/fbs/authCode/index` | `business:fbs:authCode:list` |
| 用户权益查询 | `business/fbs/userPack/index` | `business:fbs:userPack:list` |

#### 场景包管理页面覆盖

| 功能 | 对应 Spec 场景 | 前端实现 |
|------|---------------|---------|
| 分页列表查询 | 场景包分页列表查询 | 搜索栏（名称/状态/类型）+ el-table + pagination |
| 详情查询 | 场景包详情查询 | 详情弹窗（el-descriptions 展示完整字段） |
| 新增 | 场景包编辑（id=null 时新增） | 新增弹窗（编码/名称/类型/可见范围/积分规则/描述） |
| 编辑 | 场景包编辑 | 编辑弹窗（同新增，编码不可修改） |
| 发布 | 场景包发布 | 操作列按钮（仅 status=0 时显示），confirm 确认 |
| 下架 | 场景包下架 | 操作列按钮（仅 status=1 时显示），confirm 确认 |
| 按钮权限 | 场景包权限控制 | v-hasPermi 绑定各操作权限标识 |

#### 授权码管理页面覆盖

| 功能 | 对应 Spec 场景 | 前端实现 |
|------|---------------|---------|
| 分页列表查询 | 授权码分页列表查询 | 搜索栏（授权码/启用状态/使用状态）+ el-table + pagination |
| 批量生成 | 批量生成授权码 | 生成弹窗（目标ID/最大激活次数/发放者类型/数量/截止时间/说明），结果弹窗展示+复制 |
| 禁用 | 禁用授权码 | 操作列按钮（仅 available=1 时显示），confirm 确认 |
| 启用 | 启用授权码 | 操作列按钮（available=0 且 status 非 2/3/4 时显示），confirm 确认 |
| 撤销 | 撤销授权码 | 操作列按钮（status 非 4 时显示），confirm 确认 |
| 按钮权限 | 授权码权限控制 | v-hasPermi 绑定各操作权限标识 |

#### 用户权益查询页面覆盖

| 功能 | 对应 Spec 场景 | 前端实现 |
|------|---------------|---------|
| 分页列表查询 | 用户权益分页列表查询 | 搜索栏（用户ID/权益状态）+ el-table + pagination |
| 权益统计 | 用户权益统计 | 统计卡片（total/active/expired/revoked）+ 统计弹窗（按用户ID查询） |
| 按钮权限 | 用户权益权限控制 | v-hasPermi 绑定权限标识 |

