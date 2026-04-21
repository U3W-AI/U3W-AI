# #15 Skill API 安全加固 — 实施记录

**归档时间**：2026-04-20
**状态**：✅ 全部完成，6/6 任务通过

## 实施摘要

| 任务 | 状态 | 关键交付 |
|------|------|----------|
| 1. API Key 新格式 | ✅ | `fbs_` + Hex(32B) = 68字符，VARCHAR(128)，Skill端正则更新 |
| 2. Skill 端签名基础设施 | ✅ | `backend-api.mjs` 自动计算 HMAC-SHA256 + 时间戳 |
| 3. 后端 Wrapper + Filter 校验 | ✅ | `RepeatedlyReadRequestWrapper` + 四步校验链 + 22 单元测试 |
| 4. 集成验证 | ✅ | 11 集成测试全通过 |
| 5. 积分文件 HMAC | ✅ | `credits-ledger.mjs` 写入 `_hmac` + 读取校验 |
| 6. 文档更新 | ✅ | SKILL.md + 测试脚本 |

## 集成测试结果

| 测试项 | ID | 状态 | 详情 |
|--------|----|----|------|
| 管理员登录 | A0 | ✅ | token 正常 |
| 创建 API Key | A1 | ✅ | 新格式 `fbs_9841e418296c9d07...` |
| /user/info 不传 userId | T5.1.1 | ✅ | userId=1, balance=1641 |
| /user/info 传 userId | T5.1.2 | ✅ | 向后兼容正常 |
| 无效 API Key | T5.1.3 | ✅ | 正确返回 401 |
| /usage/consume 不传 userId | T5.1.4 | ✅ | remain=1621 |
| 幂等性测试 | T5.1.5 | ✅ | 相同 usageRecordId 返回相同结果 |
| 缺少时间戳 → 401 | T15.1 | ✅ | msg=时间戳缺失 |
| 缺少签名 → 401 | T15.2 | ✅ | msg=签名缺失 |
| 签名不匹配 → 401 | T15.3 | ✅ | msg=签名不匹配 |
| 正确签名+时间戳 → 200 | T15.4 | ✅ | 请求成功 |

## 踩坑记录

1. **`HttpServletRequest` import 路径**：`jakarta.servlet.HttpServletRequest` 应为 `jakarta.servlet.http.HttpServletRequest`（少了个 `.http` 包）
2. **Windows GBK 编码**：Python print 不能输出 emoji，改 ASCII 标记
3. **集成测试路径**：登录接口 `/login`（非 `/fbs/login`），API Key 管理 `/fbs/business/my/apikey`
4. **packCode**：测试用 `PACK_GENEALOGY`（数据库中不存在 `general`）
5. **FBS_API_BASE_URL 末尾斜杠导致 URL 双斜杠**（2026-04-21 修复）：用户配置 `FBS_API_BASE_URL=http://localhost:8080/`（末尾带 `/`），`backend-api.mjs` 拼接后产生 `http://localhost:8080//fbs/skill-api/user/info`（双斜杠），后端返回 404/500，`getBalance()` fallback 到本地缓存 `credits-ledger.json`，导致余额永远显示旧值（1646）而非实际值（1711）。修复方式：`_apiUrl()` 去除末尾斜杠 + `setApiBaseUrl()` 写入时自动去除末尾斜杠。

## 向领导汇报的摘要

### 解决了什么问题

之前 Skill（写书工具）调用后端接口时，**只靠一个 API Key 明文传输**，存在三个风险：

| 风险 | 后果 |
|------|------|
| **Key 被截获就能冒用** | 别人拿到 Key 就能以你的身份调接口 |
| **请求可以被重放** | 截获一条请求，反复发送，积分被重复扣减 |
| **本地积分文件可被篡改** | 用户手动改文件，把余额从 0 改成 9999 |

### 做了什么

**1. 请求签名 —— 防截获冒用**
每次调用自动用 HMAC-SHA256 算一个签名，后端校验签名是否匹配。即使 Key 被截获，不知道签名算法也伪造不了合法请求。

**2. 时间戳校验 —— 防重放攻击**
每个请求带上毫秒级时间戳，后端只接受 5 分钟内的请求。截获的旧请求再发一遍，过时直接拒绝。

**3. API Key 去规律化 —— 防猜测**
旧 Key 格式有规律（Base64 编码，36字符），容易被猜到生成规则。新 Key 改为 68 位随机十六进制串，无法推测。

**4. 积分文件校验 —— 防本地篡改**
写入本地积分文件时自动附加 HMAC 校验码。读取时校验，文件被篡改则余额自动归零，篡改无收益。

### 效果

- 4 种攻击手段均被阻断（冒用、重放、猜测、篡改）
- **对用户无感知**：签名和时间戳由代码自动计算，用户无需额外操作
- 集成测试 11 项全部通过，原有功能不受影响
