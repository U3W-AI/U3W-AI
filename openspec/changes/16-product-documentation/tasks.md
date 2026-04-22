# OpenSpec #16 任务分解

**Change ID**: `16-product-documentation`
**总任务数**: 6
**状态**: 待实施

---

## 任务概览

| # | 任务 | 状态 | 说明 |
|---|------|:----:|------|
| 1 | 创建 docs 目录结构 | 🔲 | 建立文档工作区 |
| 2 | 产品概述章节 | 🔲 | 约 300 行，覆盖核心概念 |
| 3 | 快速开始指南 | 🔲 | 约 200 行，3 步接入 |
| 4 | API 参考（全部端点） | 🔲 | 约 1500 行，按 5 大域分类 |

> **修正说明**：经代码验证，实际端点远超原始估算的 16 个。按域分类如下：
> - 用户侧 API Key 管理：4 个
> - Skill API：7 个
> - 运营 API：场景包 6 + 授权码 5 + 用户权益 2 + 运营 API Key 6 + 企业 14 + 企微 13 = **46 个**
> - 用户自助 API：4 个
> - 总计约 **61 个**端点（含企微 13 个）
>
> MVP 文档范围建议：**核心端点 20 个**（Skill API 7 + 用户自助 4 + 用户侧 API Key 4 + 运营场景包 5），其余端点以"运营参考"表格形式简要列出。
| 5 | 错误码参考 | 🔲 | 约 200 行，覆盖全部错误码 |
| 6 | 安全说明 + 变更日志 | 🔲 | 约 200 行 |

---

## Task 1：创建 docs 目录结构

**内容**：
- 创建 `docs/` 目录
- 创建 `docs/SDK-REFERENCE.md`（主文档框架，含目录）
- 创建 `docs/QUICK-START.md`
- 创建 `docs/ERROR-CODES.md`

**产出**：
- `docs/SDK-REFERENCE.md` — 包含 11 章目录骨架
- `docs/QUICK-START.md`
- `docs/ERROR-CODES.md`

**验证**：
- [ ] 三个文件存在且可打开
- [ ] 主文档目录完整（11 章）

---

## Task 2：产品概述章节

**内容**：撰写 `SDK-REFERENCE.md` 第 1 章
- FBS-BookWriter 定位与价值
- 核心概念：乐包/场景包/授权码/用户层级
- 系统架构图（Skill-后端-企微三方关系）
- 概念对应关系表（Skill 术语 → 后端术语）

**参考来源**：
- `openspec/archive/01-add-fbs-rights-foundation/`
- `openspec/specs/fbs-rights-foundation/spec.md`
- `SKILL.md` 第 1 章

**验证**：
- [ ] 包含 4 个核心概念的定义
- [ ] 包含系统架构图（文本形式）
- [ ] 术语对照表覆盖 ≥ 10 个术语

---

## Task 3：快速开始指南

**内容**：撰写 `QUICK-START.md`
- 第 1 步：注册账号 + 登录后台
- 第 2 步：创建 API Key（截图指引）
- 第 3 步：调用第一个 API（余额查询，cURL + Python + Node.js 三版本）

**API 示例**（注意：`/user/info` 为 POST，需 RequestBody；#15 后所有请求须带签名三头部）：

```bash
# ========== cURL ==========
# 步骤 1：准备签名三要素
API_KEY="fbs_your_key_here"                  # API Key 即为签名密钥，在控制台获取
TIMESTAMP=$(date +%s%3N)                    # Unix ms 时间戳（Linux/macOS）
METHOD="POST"
PATH="/fbs/skill-api/user/info"
BODY='{}'                                   # POST 请求体（JSON 对象序列化后为 "{}"）

# 步骤 2：构造待签名字符串：timestamp + "\n" + body
# 注意：body 为空 JSON 对象时序列化为 "{}"（含引号）；GET 请求 body 为空字符串
STRING_TO_SIGN="${TIMESTAMP}
${BODY}"

# 步骤 3：计算 HMAC-SHA256 签名（小写 hex，Linux/macOS 用 awk 转为小写）
SIGNATURE=$(echo -n -e "$STRING_TO_SIGN" | openssl dgst -sha256 -hmac "$API_KEY" | awk '{print tolower($NF)}')

# 步骤 4：发送请求（三个头部缺一不可）
curl -X POST https://api.U3W.com/fbs/skill-api/user/info \
  -H "X-FBS-API-Key: ${API_KEY}" \
  -H "X-FBS-Timestamp: ${TIMESTAMP}" \
  -H "X-FBS-Signature: ${SIGNATURE}" \
  -H "Content-Type: application/json" \
  -d "${BODY}"
```

```python
# ========== Python ==========
import requests
import time
import hmac
import hashlib

API_KEY = "fbs_your_key_here"              # API Key 即为签名密钥
BASE_URL = "https://api.U3W.com"

def sign_request(api_key: str, body: str) -> tuple[str, str]:
    """返回 (timestamp, signature)
    签名字符串：timestamp + "\\n" + body（小写 hex）
    """
    timestamp = str(int(time.time() * 1000))
    # body 为空 JSON 对象时序列化为 "{}"（含引号）；GET 请求 body = ""
    string_to_sign = timestamp + "\n" + body
    signature = hmac.new(
        api_key.encode(),
        string_to_sign.encode(),
        hashlib.sha256
    ).hexdigest()  # 小写 hex
    return timestamp, signature

# 查询用户信息（POST，body 为空 JSON 对象 "{}"）
body = "{}"
timestamp, signature = sign_request(API_KEY, body)

resp = requests.post(
    BASE_URL + "/fbs/skill-api/user/info",
    headers={
        "X-FBS-API-Key": API_KEY,
        "X-FBS-Timestamp": timestamp,
        "X-FBS-Signature": signature,
        "Content-Type": "application/json",
    },
    data=body
)
print(resp.json())
```

```javascript
// ========== Node.js ==========
import crypto from 'crypto';

const API_KEY = 'fbs_your_key_here'; // API Key 即为签名密钥
const BASE_URL = 'https://api.U3W.com';

/**
 * 生成签名头部
 * @param {string} apiKey - API Key（同时作为 HMAC 密钥）
 * @param {string} body   - 请求体（POST 为 JSON 字符串如 '{}'；GET 为 ''）
 * @returns {{ timestamp: string, signature: string }}
 */
function signRequest(apiKey, body = '') {
  const timestamp = String(Date.now());
  const stringToSign = timestamp + '\n' + body; // 注意：body 为 "{}" 时含引号
  const signature = crypto
    .createHmac('sha256', apiKey)
    .update(stringToSign)
    .digest('hex'); // 小写 hex
  return { timestamp, signature };
}

// 查询用户信息（POST，body 为空 JSON 对象）
const { timestamp, signature } = signRequest(API_KEY, '{}');
const resp = await fetch(`${BASE_URL}/fbs/skill-api/user/info`, {
  method: 'POST',
  headers: {
    'X-FBS-API-Key': API_KEY,
    'X-FBS-Timestamp': timestamp,
    'X-FBS-Signature': signature,
    'Content-Type': 'application/json',
  },
  body: '{}',
});
console.log(await resp.json());
```

**验证**：
- [ ] 三个代码示例均为有效可运行代码
- [ ] 域名使用 `https://api.U3W.com`

---

## Task 4：API 参考（核心端点全覆盖）

**内容**：在 `SDK-REFERENCE.md` 中撰写第 3-8 章

### 3 用户侧 API Key 管理（4 个）
| 端点 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 创建 API Key | POST | `/fbs/business/my/apikey/create` | 实际前缀 `/fbs/business/my/apikey` |
| 列表 API Key | GET | `/fbs/business/my/apikey/list` | |
| 禁用/启用 | PUT | `/fbs/business/my/apikey/toggle/{id}?status=` | PUT 非 POST；需传 status 参数 |
| 删除 API Key | DELETE | `/fbs/business/my/apikey/{id}` | |

### 4 Skill API（7 个）
| 端点 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 权益校验 | POST | `/fbs/skill-api/rights/check` | |
| 使用开始 | POST | `/fbs/skill-api/usage/start` | |
| 使用结束 | PUT | `/fbs/skill-api/usage/end/{usageRecordId}` | PUT 非 POST；usageRecordId 为路径变量 |
| 消费 | POST | `/fbs/skill-api/usage/consume` | |
| 场景包查询 | POST | `/fbs/skill-api/scene-pack/query` | POST 非 GET（需 RequestBody） |
| 用户信息 | POST | `/fbs/skill-api/user/info` | POST 非 GET（需 RequestBody） |
| 行为积分上报 | POST | `/fbs/skill-api/points/earn` | SDK 中实际调用 |

### 5 运营 API（按子域分组）

#### 5a 场景包运营（6 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 场景包分页列表 | GET | `/business/fbs/scene-pack/list` |
| 场景包详情 | GET | `/business/fbs/scene-pack/{id}` |
| 创建场景包 | POST | `/business/fbs/scene-pack` |
| 编辑场景包 | PUT | `/business/fbs/scene-pack` |
| 发布场景包 | PUT | `/business/fbs/scene-pack/publish` |
| 下架场景包 | PUT | `/business/fbs/scene-pack/unpublish` |

#### 5b 授权码运营（5 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 授权码分页列表 | GET | `/business/fbs/auth-code/list` |
| 批量生成授权码 | POST | `/business/fbs/auth-code/generate` |
| 禁用授权码 | PUT | `/business/fbs/auth-code/disable` |
| 启用授权码 | PUT | `/business/fbs/auth-code/enable` |
| 撤销授权码 | PUT | `/business/fbs/auth-code/revoke` |

#### 5c 用户权益查询运营（2 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 用户-场景包分页列表 | GET | `/business/fbs/user-pack/list` |
| 用户权益统计 | GET | `/business/fbs/user-pack/stats` |

#### 5d 运营侧 API Key 管理（6 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 生成 API Key | POST | `/fbs/business/api-key/generate` |
| 查询列表（脱敏） | GET | `/fbs/business/api-key/list` |
| 查询详情（脱敏） | GET | `/fbs/business/api-key/{id}` |
| 禁用 API Key | PUT | `/fbs/business/api-key/disable/{id}` |
| 启用 API Key | PUT | `/fbs/business/api-key/enable/{id}` |
| 删除 API Key | DELETE | `/fbs/business/api-key/{id}` |

#### 5e 企业运营（13 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 企业分页列表 | GET | `/business/fbs/enterprise/list` |
| 企业详情 | GET | `/business/fbs/enterprise/{id}` |
| 创建企业 | POST | `/business/fbs/enterprise` |
| 编辑企业 | PUT | `/business/fbs/enterprise` |
| 禁用企业 | PUT | `/business/fbs/enterprise/disable` |
| 成员列表 | GET | `/business/fbs/enterprise/member/list` |
| 成员详情 | GET | `/business/fbs/enterprise/member/{id}` |
| 添加成员 | POST | `/business/fbs/enterprise/member` |
| 移除成员 | DELETE | `/business/fbs/enterprise/member/{id}` |
| 企业包分发/撤销等 | - | `/business/fbs/enterprise/pack/*`（4个） |

#### 5f 企微同步运营（13 个）
| 端点 | 方法 | 路径 |
|------|------|------|
| 读取企微数据 | POST | `/fbs/business/wecom/sync/read` |
| 检测可用性 | POST | `/fbs/business/wecom/sync/check` |
| 写入企微数据 | POST | `/fbs/business/wecom/sync/write` |
| 子表/字段/记录 CRUD | - | `/fbs/business/wecom/schema/*` + `/records`（10个） |

### 6 用户自助 API（4 个）
| 端点 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 激活授权码 | POST | `/my/auth-code/activate` | |
| 我的场景包 | GET | `/my/packs` | |
| 可领取场景包 | GET | `/my/scene-packs` | 实际路径 `/my/scene-packs` 非 `/my/packs/claimable` |
| 领取场景包 | POST | `/my/scene-pack/claim` | 文档遗漏，代码中存在 |

**每个端点格式**：
```markdown
### {端点名称}

**路径**: `{METHOD} {path}`
**说明**: 一句话描述

**请求头**：
| 参数 | 必填 | 说明 |
|------|:----:|------|
| X-FBS-API-Key | ✅ | API Key |

> **注意**：以上为普通业务 API 模板（如 `/my/**`、`/business/**`）。<br>
> Skill API 端点（`/fbs/skill-api/**`）请求头须额外增加两个签名头：
> | X-FBS-Timestamp | ✅ | Unix ms 时间戳 |
> | X-FBS-Signature | ✅ | `HMAC-SHA256(apiKey, timestamp + '\n' + body)` 小写 hex |

**请求体**（如有）：
```json
{}
```

**响应示例**：
```json
{}
```

**错误码**：列出本端点可能返回的错误码
```

**验证**：
- [ ] 核心端点（Skill API 7 + 用户自助 4 + 用户 API Key 4 = 15 个）全部覆盖，含请求/响应示例
- [ ] 运营端点以表格形式列出
- [ ] 所有路径使用 `https://api.U3W.com`
- [ ] 错误码引用 `ERROR-CODES.md`
- [ ] HTTP 方法与代码一致（特别注意：usage/end 为 PUT，scene-pack/query 和 user/info 为 POST）

---

## Task 5：错误码参考

**内容**：撰写 `ERROR-CODES.md`

按 HTTP 状态码组织：
- 400 Bad Request
- 401 Unauthorized
- 403 Forbidden
- 404 Not Found
- 409 Conflict
- 429 Too Many Requests
- 500 Internal Server Error

每个错误码格式：
```markdown
### {错误码}（HTTP {状态码}）

**说明**：
**触发场景**：
**排查建议**：
**请求示例**：
```

**验证**：
- [ ] 覆盖 ≥ 30 个错误码
- [ ] 每个错误码有排查建议
- [ ] 与 `SDK-REFERENCE.md` 中的引用一致

---

## Task 6：安全说明 + 变更日志

**内容**：

### A. 安全说明（`SDK-REFERENCE.md` 第 9 章）
- API Key 安全存储
- 请求签名（HMAC-SHA256）说明
- 时间戳防重放机制
- 本地积分文件校验

### B. 变更日志（`SDK-REFERENCE.md` 第 11 章）
按 OpenSpec 阶段记录：
- #1-#5：底座与网关
- #6-#9：企微打通
- #10-#14：积分与乐包统一
- #15：安全加固

**验证**：
- [ ] 安全说明覆盖 4 个加固项
- [ ] 变更日志覆盖 #1-#15 每阶段核心交付

---

## 实施顺序

```
Task 1（目录结构）→ Task 2（概述）→ Task 3（快速开始）
    ↓
Task 4（API 参考，最耗时）
    ↓
Task 5（错误码）→ Task 6（安全+变更日志）
    ↓
终审 → 归档
```
