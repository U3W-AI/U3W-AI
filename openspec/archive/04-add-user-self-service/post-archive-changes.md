# OpenSpec #4 归档后变更记录

> **日期**: 2026-04-11
> **说明**: OpenSpec #4 于 2026-04-11 首次归档后的 Bug 修复与功能增强

---

## 1. 可领取场景包 claimed 状态改造

### 问题
领取完场景包后卡片消失，刷新后又出现（SQL 用 NOT EXISTS 排除了已领取的包）

### 方案
已领取的包仍然显示，标记 `claimed=true`，前端显示「已领取」标签 + 禁用领取按钮

### 改动
| 文件 | 改动 |
|------|------|
| `FbsScenePackMapper.xml` | selectClaimablePacks 去掉 NOT EXISTS 子查询 |
| `FbsUserPackMapper.java` | 新增 `selectClaimedPackIds(userId, packIds)` |
| `FbsUserPackMapper.xml` | 新增 selectClaimedPackIds SQL |
| `MyScenePackItemDTO.java` | 新增 `claimed` 字段 |
| `FbsUserSelfServiceBusinessServiceImpl.java` | 查包列表后批量查 claimed 状态 |
| `claimablePacks/index.vue` | 领取按钮 `v-if="!pack.claimed"`；已领取显示 el-tag；领取成功后 `pack.claimed=true` |

---

## 2. 激活授权码历史记录持久化

### 问题
刷新页面后激活历史记录清空（纯内存 `ref([])`）

### 方案
localStorage 持久化，最多保留 50 条

### 改动
| 文件 | 改动 |
|------|------|
| `activateAuthCode/index.vue` | `ref([])` → `ref(loadHistory())`；每次激活后 `saveHistory()` |

---

## 3. 激活授权码错误码中文化

### 问题
撤销/禁用码报错显示 `AUTH_CODE_DISABLED` 等英文码

### 方案
`mapFailReasonToException` 返回中文消息

### 改动
| 文件 | 改动 |
|------|------|
| `FbsUserSelfServiceBusinessServiceImpl.java` | mapFailReasonToException 改为中文（"授权码已禁用"/"授权码已撤销"/"授权码已过期"/"授权码激活次数已用尽"）；`AUTH_CODE_INVALID:xxx` 前缀改为纯中文 |

---

## 4. 禁用码激活提示成功（安全 Bug）

### 问题
禁用/撤销的授权码，如果用户已拥有该场景包权益，走幂等路径返回成功

### 方案
available/status 校验提前到幂等检查之前

### 改动
| 文件 | 改动 |
|------|------|
| `FbsUserSelfServiceBusinessServiceImpl.java` | 校验链重排：available → status → 幂等 → authCodeService |

---

## 5. 重复授权码激活报错

### 问题
用同场景包的不同授权码激活，返回"激活成功"（幂等路径），但第二个授权码未被消耗

### 方案
幂等路径改为 409 拒绝："您已拥有该场景包权益，无法使用其他授权码重复激活"

### 改动
| 文件 | 改动 |
|------|------|
| `FbsUserSelfServiceBusinessServiceImpl.java` | 幂等分支 200 → 409 |
| `request.js` | `Promise.reject('error')` → `Promise.reject(new Error(msg))` |
| `activateAuthCode/index.vue` | catch 取 `error.message` |
| `claimablePacks/index.vue` | catch 取 `error.message` |

---

## 6. 激活校验增强（三需求）

### 需求
- 已绑定用户的授权码 → "该授权码已被其他用户绑定"
- 过期实时校验 → "授权码已过期"（不依赖 status 字段）
- 场景包下架 → "场景包已下架，授权码无法激活"

### 改动
| 文件 | 改动 |
|------|------|
| `FbsUserSelfServiceBusinessServiceImpl.java` | 新增 3 段校验（已绑定/实时过期/场景包状态） |

### 激活校验链（最终版）
1. authCode 为空 → 400
2. 查授权码 → 不存在 → 400
3. targetType/targetId 无效 → 400
4. available!=1 → "授权码已禁用"
5. status=4 → "授权码已撤销"
6. status=3 → "授权码已过期"
7. status=2 → "授权码激活次数已用尽"
8. status=1 且 activatedCount>0 → "该授权码已被其他用户绑定"
9. deadline 实时过期 → "授权码已过期"
10. 场景包不存在/已下架 → 400
11. 用户已有该包权益 → 409
12. 调用 authCodeService

---

## 7. 授权码生成截止时间校验

### 问题
生成授权码时截止时间可以选过去的时间，导致"出生即过期"

### 方案
前端日期选择器禁用过去日期 + 后端 deadline 校验

### 改动
| 文件 | 改动 |
|------|------|
| `FbsAuthCodeBusinessController.java` | generate 增加 deadline < now 校验 |
| `FbsAuthCodeController.java` | 内部接口也加校验 |
| `authCode/index.vue` | 日期选择器加 `disabled-date` |

---

## 8. 场景包下架联动

### 问题1
我的权益中已下架场景包仍显示"有效"

### 问题2
授权码管理中已下架场景包仍能生成授权码

### 改动
| 文件 | 改动 |
|------|------|
| `FbsUserPackMapper.xml` | selectMyPacks 增加 `s.status as pack_status`；ResultMap 加映射 |
| `FbsUserPack.java` | 增加 packStatus 非持久化字段 |
| `MyPackItemDTO.java` | 增加 packStatus；getStatusDesc 综合判断场景包下架/删除 |
| `FbsUserSelfServiceBusinessServiceImpl.java` | getMyPacks 组装 packStatus |
| `FbsAuthCodeBusinessServiceImpl.java` | generateAuthCodeBatch 增加场景包状态校验 |
| `FbsAuthCodeController.java` | 内部接口也加场景包校验 |
| `myPacks/index.vue` | 状态标签颜色函数改为综合判断 |
| `FbsAuthCodeMapper.xml` | selectAuthCodeList 增加 `s.status AS pack_status` |
| `FbsAuthCode.java` | 增加 packStatus 非持久化字段 |
| `authCode/index.vue` | 关联场景包列增加"已下架"红色标签 |

---

## 9. 授权码管理 UI 改造

### 需求
- 生成授权码时"关联目标ID"改为下拉框选场景包
- 授权码列表"关联目标"列显示场景包名称而非 ID
- 下拉框显示 packCode 传参，列表/下拉框均显示 packName

### 改动
| 文件 | 改动 |
|------|------|
| `FbsAuthCode.java` | 新增 targetPackCode + targetPackName 非持久化字段 |
| `FbsAuthCodeMapper.xml` | selectAuthCodeList LEFT JOIN fbs_scene_pack 联查 pack_code/pack_name |
| `AuthCodeGenerateRequest.java` | 新增 targetPackCode 字段 |
| `FbsAuthCodeBusinessController.java` | generate 支持 targetPackCode → 解析 targetId |
| `authCode/index.vue` | 关联目标列→packName；生成对话框→下拉框（filterable，显示 packName，value=packCode） |

---

## 测试总览

| 阶段 | 用例数 | 结果 |
|------|--------|------|
| 首次归档前 | 48 | ✅ 全绿 |
| claimed 改造后 | 50 | ✅ 全绿 |
| 激活校验增强后 | 151 | ✅ 全绿 |
| 最终 | 155 | ✅ 全绿 |
