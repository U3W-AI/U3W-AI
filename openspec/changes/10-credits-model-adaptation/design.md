# OpenSpec #10 Design: 积分模型适配与 LedgerSync

> **Change ID**: `credits-model-adaptation`
> **日期**: 2026-04-16

---

## 一、架构设计

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    U3WV2 (WxFbsir)                          │
│  ┌──────────────┐    ┌──────────────────┐                  │
│  │ sys_user     │    │ fbs_credits_     │                  │
│  │ (points余额) │◄───│ ledger (流水)     │                  │
│  └──────────────┘    └──────────────────┘                  │
│         ▲                    ▲                              │
│         │                    │                              │
│  ┌──────┴────────────────────┴───────┐                     │
│  │     CreditsLedgerService          │                     │
│  │  - getBalance()                   │                     │
│  │  - changeCredits() (幂等)         │                     │
│  │  - getLedgerRecords()             │                     │
│  └──────────────┬────────────────────┘                     │
│                 │                                            │
│  ┌──────────────▼────────────────────┐                     │
│  │  CreditsInternalController        │                     │
│  │  - GET  /balance                  │                     │
│  │  - POST /earn                     │                     │
│  │  - GET  /sync                     │                     │
│  └──────────────┬────────────────────┘                     │
└─────────────────┼───────────────────────────────────────────┘
                  │ HTTP API
                  │
┌─────────────────▼───────────────────────────────────────────┐
│              福帮手主机侧                                     │
│  ┌──────────────────────────────────┐                       │
│  │      LedgerSync 进程             │                       │
│  │  - 定期轮询 /sync API            │                       │
│  │  - 写入 credits-ledger.json      │                       │
│  └──────────────┬───────────────────┘                       │
│                 │                                            │
│                 ▼                                            │
│         credits-ledger.json                                  │
│                 │                                            │
│                 ▼                                            │
│  ┌──────────────────────────────────┐                       │
│  │   FBS-BookWriter Skill           │                       │
│  │   - 读取本地 JSON 获取余额        │                       │
│  │   - 支持离线/弱网                 │                       │
│  └──────────────────────────────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 数据流

**积分赚取流程**：
```
Skill 端触发事件 → POST /earn → CreditsLedgerService.changeCredits()
                                      ↓
                              检查 event_id 幂等
                                      ↓
                              插入 fbs_credits_ledger
                                      ↓
                              更新 sys_user.points
                                      ↓
                              返回 balance_after
```

**积分同步流程**：
```
LedgerSync 定时器 → GET /sync → CreditsLedgerService.getBalance()
                                       ↓
                               返回余额快照
                                       ↓
                               写入 credits-ledger.json
```

---

## 二、数据模型设计

### 2.1 fbs_credits_ledger 表

```sql
CREATE TABLE fbs_credits_ledger (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id         BIGINT NOT NULL COMMENT '用户 ID',
    book_id         VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '账本 ID',
    event_type      VARCHAR(32) NOT NULL COMMENT '事件类型',
    event_id        VARCHAR(128) NOT NULL COMMENT '幂等键（事件唯一标识）',
    delta           INT NOT NULL COMMENT '积分变动（正=增加，负=扣减）',
    balance_after   INT NOT NULL COMMENT '变动后余额',
    remark          VARCHAR(500) COMMENT '备注',
    created_by      VARCHAR(64) COMMENT '创建人',
    created_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_user_book (user_id, book_id),
    UNIQUE KEY uk_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分账本流水表';
```

### 2.2 event_type 枚举

| 值 | 说明 | delta |
|----|------|-------|
| `FIRST_INSTALL` | 首次安装 | +100（配置） |
| `DAILY_LOGIN` | 每日登录 | +10（配置） |
| `SKILL_CONSUME` | Skill 消费 | -N（实际扣减） |
| `ADMIN_GRANT` | 管理员发放 | +N |
| `PACK_PURCHASE` | 场景包购买 | -N |
| `REFUND` | 退款/返还 | +N |

### 2.3 event_id 生成规则

| event_type | event_id 格式 | 示例 |
|------------|---------------|------|
| FIRST_INSTALL | `first_install_{userId}` | `first_install_1` |
| DAILY_LOGIN | `daily_login_{userId}_{date}` | `daily_login_1_2026-04-16` |
| SKILL_CONSUME | `skill_consume_{usageId}` | `skill_consume_12345` |
| ADMIN_GRANT | `admin_grant_{adminId}_{timestamp}` | `admin_grant_1_1713254400` |

---

## 三、服务设计

### 3.1 CreditsLedgerService

```java
public interface CreditsLedgerService {
    
    /**
     * 查询用户积分余额
     * 
     * @param userId 用户 ID
     * @param bookId 账本 ID（默认 default）
     * @return 积分余额
     */
    int getBalance(Long userId, String bookId);
    
    /**
     * 积分变动（幂等）
     * 
     * @param userId 用户 ID
     * @param bookId 账本 ID
     * @param eventType 事件类型
     * @param eventId 幂等键
     * @param delta 变动值（正数=增加，负数=扣减）
     * @param remark 备注
     * @return 变动后余额
     */
    int changeCredits(Long userId, String bookId, String eventType, 
                      String eventId, int delta, String remark);
    
    /**
     * 查询用户积分流水
     * 
     * @param userId 用户 ID
     * @param bookId 账本 ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 流水记录列表
     */
    List<FbsCreditsLedger> getLedgerRecords(Long userId, String bookId,
                                             LocalDateTime startTime, 
                                             LocalDateTime endTime);
    
    /**
     * 同步快照（供 LedgerSync 调用）
     * 
     * @param userId 用户 ID
     * @param bookId 账本 ID
     * @return 快照对象
     */
    CreditsSnapshot getSnapshot(Long userId, String bookId);
}
```

### 3.2 CreditsLedgerServiceImpl 实现要点

#### getBalance()
```java
public int getBalance(Long userId, String bookId) {
    // 直接从 sys_user.points 查询
    SysUser user = userMapper.selectById(userId);
    return user != null ? user.getPoints() : 0;
}
```

#### changeCredits() 幂等控制
```java
@Transactional
public int changeCredits(Long userId, String bookId, String eventType,
                         String eventId, int delta, String remark) {
    // 1. 检查幂等
    FbsCreditsLedger existing = ledgerMapper.selectByEventId(eventId);
    if (existing != null) {
        // 已处理，返回既有余额
        return existing.getBalanceAfter();
    }
    
    // 2. 获取当前余额（加锁）
    SysUser user = userMapper.selectByIdForUpdate(userId);
    int currentBalance = user.getPoints();
    int newBalance = currentBalance + delta;
    
    // 3. 余额校验（扣减时不能为负）
    if (newBalance < 0) {
        throw new InsufficientCreditsException("积分余额不足");
    }
    
    // 4. 更新 sys_user.points
    userMapper.updatePoints(userId, newBalance);
    
    // 5. 插入流水记录
    FbsCreditsLedger ledger = new FbsCreditsLedger();
    ledger.setUserId(userId);
    ledger.setBookId(bookId);
    ledger.setEventType(eventType);
    ledger.setEventId(eventId);
    ledger.setDelta(delta);
    ledger.setBalanceAfter(newBalance);
    ledger.setRemark(remark);
    ledger.setCreatedBy("system");
    ledgerMapper.insert(ledger);
    
    return newBalance;
}
```

---

## 四、API 设计

### 4.1 GET /fbs/internal/credits/balance

**请求**：
```json
{
  "userId": 1,
  "bookId": "default"
}
```

**响应**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": 1,
    "bookId": "default",
    "balance": 1000
  }
}
```

### 4.2 POST /fbs/internal/credits/earn

**请求**：
```json
{
  "userId": 1,
  "bookId": "default",
  "eventType": "FIRST_INSTALL",
  "eventId": "first_install_1",
  "delta": 100,
  "remark": "首次安装奖励"
}
```

**响应**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": 1,
    "bookId": "default",
    "balanceBefore": 900,
    "balanceAfter": 1000,
    "delta": 100,
    "eventId": "first_install_1"
  }
}
```

### 4.3 GET /fbs/internal/credits/sync

**请求参数**：
- `userId`: 用户 ID
- `bookId`: 账本 ID（可选，默认 default）

**响应**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": 1,
    "bookId": "default",
    "balance": 1000,
    "lastUpdated": "2026-04-16T12:00:00",
    "version": "v1.0.0"
  }
}
```

---

## 五、LedgerSync 进程设计

### 5.1 配置文件

`ledgersync.conf`：
```ini
[api]
base_url = http://localhost:8080/fbs/internal/credits
api_key = your_api_key_here

[sync]
interval_seconds = 300
output_path = ./credits-ledger.json

[logging]
level = INFO
file = ledgersync.log
```

### 5.2 Python 实现

```python
#!/usr/bin/env python3
"""LedgerSync - 积分同步进程"""

import requests
import json
import time
import configparser
import logging
from pathlib import Path

def load_config():
    config = configparser.ConfigParser()
    config.read('ledgersync.conf')
    return {
        'base_url': config.get('api', 'base_url'),
        'api_key': config.get('api', 'api_key'),
        'interval': config.getint('sync', 'interval_seconds'),
        'output_path': config.get('sync', 'output_path'),
    }

def fetch_balance(config, user_id, book_id='default'):
    url = f"{config['base_url']}/sync"
    headers = {'Authorization': f"Bearer {config['api_key']}"}
    params = {'userId': user_id, 'bookId': book_id}
    
    resp = requests.get(url, headers=headers, params=params, timeout=10)
    resp.raise_for_status()
    return resp.json()['data']

def write_ledger(config, data):
    output_path = Path(config['output_path'])
    with output_path.open('w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def main():
    config = load_config()
    user_id = 1  # TODO: 从配置或环境变量读取
    
    while True:
        try:
            data = fetch_balance(config, user_id)
            write_ledger(config, data)
            logging.info(f"同步成功: balance={data['balance']}")
        except Exception as e:
            logging.error(f"同步失败: {e}")
        
        time.sleep(config['interval'])

if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)
    main()
```

### 5.3 credits-ledger.json 格式

```json
{
  "userId": 1,
  "bookId": "default",
  "balance": 1000,
  "lastUpdated": "2026-04-16T12:00:00",
  "version": "v1.0.0"
}
```

---

## 六、安全性设计

### 6.1 API 认证

- **内部 API**：使用 API Key 认证（Header: `Authorization: Bearer {api_key}`）
- **API Key 管理**：复用 OpenSpec #5 的 `fbs_api_key` 表

### 6.2 权限控制

- `/fbs/internal/*` 端点仅允许内部服务调用（通过 IP 白名单或 API Key）
- 不对外暴露给终端用户

---

## 七、测试设计

### 7.1 单元测试

- `CreditsLedgerServiceTest`
  - testGetBalance()
  - testChangeCredits_Success()
  - testChangeCredits_Idempotent()
  - testChangeCredits_InsufficientBalance()

### 7.2 集成测试

- `CreditsApiControllerTest`
  - testBalanceApi()
  - testEarnApi()
  - testSyncApi()

### 7.3 LedgerSync 测试

- 手动运行 LedgerSync 进程
- 验证 credits-ledger.json 文件生成
- 验证内容正确性

---

## 八、部署说明

### 8.1 后端部署

- 无特殊要求，常规 Spring Boot 部署

### 8.2 LedgerSync 部署

```bash
# 安装依赖
pip install requests

# 配置
cp ledgersync.conf.example ledgersync.conf
vim ledgersync.conf

# 启动（systemd 示例）
systemctl start ledgersync
systemctl enable ledgersync
```

---

## 九、风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| LedgerSync 进程崩溃 | systemd 自动重启 + 心跳检测 |
| API 不可用 | 重试机制 + 本地缓存 |
| 余额不一致 | 定期对账 + 异常告警 |
| event_id 冲突 | 唯一索引 + 事务保证 |
