# 快速开始指南

本文档帮助第三方开发者快速接入 FBS-BookWriter 后端 API。

---

## 第 1 步：注册账号

访问后台管理系统，注册账号并登录。

> **获取 API Key**：登录后在「我的 API Key」页面查看已有的 Key，或创建新 Key。

---

## 第 2 步：获取 API Key

### 2.1 创建 API Key

登录后访问：`我的 API Key → 创建`

后台会生成一个 API Key，格式为 `fbs_` + 64 位 hex 字符（共 68 字符）。

> **示例**：`fbs_a1b2c3d4e5f6...`（64 个 hex 字符）

### 2.2 配置到 Skill

在 WorkBuddy/CodeBuddy 的 FBS-BookWriter 配置中写入：

```json
{
  "FBS_API_KEY": "fbs_your_key_here",
  "FBS_API_BASE_URL": "https://api.U3W.com"
}
```

> **重要**：`FBS_API_BASE_URL` 只配**主机地址**（不含 `/fbs/skill-api`），代码会自动追加 `/fbs/skill-api` 再拼接具体路径。
> 错误示例：`https://api.U3W.com/fbs/skill-api` → 代码会再次追加 `/fbs/skill-api`，导致 URL 变成 `https://api.U3W.com/fbs/skill-api/fbs/skill-api/...`，返回 404。
>
> **本地开发调试**：使用 `http://localhost:8080`（Spring Boot 默认端口，无 HTTPS）
>
> | 环境 | `FBS_API_BASE_URL` |
> |------|---------------------|
> | 生产服务器 | `https://api.U3W.com` |
> | 本地开发 | `http://localhost:8080` |

---

## 第 3 步：调用第一个 API

本示例调用 `POST /fbs/skill-api/user/info`，查询当前用户的积分余额和已激活场景包。

### 3.1 签名原理（#15 安全加固后）

所有 `/fbs/skill-api/**` 请求**必须**携带三个 Header：

| Header | 说明 |
|--------|------|
| `X-FBS-API-Key` | API Key（在控制台获取） |
| `X-FBS-Timestamp` | Unix 毫秒时间戳（如 `1740000000000`） |
| `X-FBS-Signature` | `HMAC-SHA256(apiKey, timestamp + '\n' + body)` 的十六进制小写表示 |

**签名字符串格式**：`${timestamp}\n${JSON.stringify(body)}`

- body 为空 JSON 对象 `{}` 时，序列化为 `"{}"`（含引号）
- GET 请求 body 为空字符串 `""`

### 3.2 cURL 示例（Bash/Linux）

> **适用平台**：Linux、macOS、以及安装了 Git Bash 或 WSL 的 Windows。macOS/Linux 直接运行；Windows PowerShell 用户请参见下方注释。

```bash
API_KEY="fbs_your_key_here"
TIMESTAMP=$(date +%s%3N)
BODY='{}'

# 签名字符串：timestamp + "\n" + body
STRING_TO_SIGN="${TIMESTAMP}
${BODY}"

# 计算 HMAC-SHA256（小写 hex）
SIGNATURE=$(echo -n -e "$STRING_TO_SIGN" | openssl dgst -sha256 -hmac "$API_KEY" | awk '{print tolower($NF)}')

curl -X POST https://api.U3W.com/fbs/skill-api/user/info \
  -H "X-FBS-API-Key: ${API_KEY}" \
  -H "X-FBS-Timestamp: ${TIMESTAMP}" \
  -H "X-FBS-Signature: ${SIGNATURE}" \
  -H "Content-Type: application/json" \
  -d "${BODY}"
```

> **Windows/PowerShell 用户**：Bash 脚本无法直接在 PowerShell 运行。建议使用 Python 或 Node.js 示例（见 3.3、3.4 节），或安装 Git Bash / WSL 后执行上述脚本。

### 3.3 Python 示例

```python
import requests
import time
import hmac
import hashlib

API_KEY = "fbs_your_key_here"
BASE_URL = "https://api.U3W.com"

def sign_request(api_key: str, body: str):
    timestamp = str(int(time.time() * 1000))
    string_to_sign = timestamp + "\n" + body
    signature = hmac.new(
        api_key.encode(),
        string_to_sign.encode(),
        hashlib.sha256
    ).hexdigest()  # 小写 hex
    return timestamp, signature

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

### 3.4 Node.js 示例

```javascript
import crypto from 'crypto';

const API_KEY = 'fbs_your_key_here';
const BASE_URL = 'https://api.U3W.com';

function signRequest(apiKey, body = '') {
  const timestamp = String(Date.now());
  const stringToSign = timestamp + '\n' + body;
  const signature = crypto
    .createHmac('sha256', apiKey)
    .update(stringToSign)
    .digest('hex'); // 小写 hex
  return { timestamp, signature };
}

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

### 3.5 响应示例

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": 1001,
    "pointsBalance": 500,
    "activatedPacks": [
      { "packCode": "whitepaper", "packName": "白皮书", "expiresAt": "2026-12-31" }
    ]
  }
}
```

---

## 下一步

- 完整 API 端点列表 → 见 `SDK-REFERENCE.md` 第 4 章
- 错误码排查 → 见 `ERROR-CODES.md`
- 安全机制详解 → 见 `SDK-REFERENCE.md` 第 9 章
