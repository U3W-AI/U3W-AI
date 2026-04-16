# OpenSpec #10 Design: 积分模型适配与 LedgerSync

> **Change ID**: `credits-model-adaptation`
> **日期**: 2026-04-16

---

## 一、架构设计

### 1.1 整体架构（复用现有系统）

```
┌─────────────────────────────────────────────────────────────┐
│                    U3WV2 (WxFbsir)                          │
│  ┌──────────────┐    ┌──────────────────┐                  │
│  │ sys_user     │    │ wx_points_record │                  │
│  │ (points余额) │◄───│ (流水+幂等)       │                  │
│  └──────────────┘    └──────────────────┘                  │
│         ▲                    ▲                              │
│         │                    │                              │
│  ┌──────┴────────────────────┴───────┐                     │
│  │     IPointsService（扩展）          │                     │
│  │  - getUserPoints()                │                     │
│  │  - changePoints(eventId) (幂等)   │                     │
│  └──────────────┬────────────────────┘                     │
│                 │                                            │
│  ┌──────────────▼────────────────────┐                     │
│  │  FbsSkillApiController             │                     │
│  │  - POST /user/info  (已有，复用)   │                     │
│  └──────────────┬────────────────────┘                     │
└─────────────────┼───────────────────────────────────────────┘
                  │ HTTP API (X-FBS-API-Key)
                  │
┌─────────────────▼───────────────────────────────────────────┐
│              福帮手主机侧                                     │
│  ┌──────────────────────────────────┐                       │
│  │      LedgerSync 进程             │                       │
│  │  - 定期轮询 /user/info           │                       │
│  │  - 写入 credits-ledger.json      │                       │
│  └──────────────────┬───────────────┘                       │
│                     │                                        │
│                     ▼                                        │
│           credits-ledger.json                                │
│                     │                                        │
│                     ▼                                        │
│  ┌──────────────────────────────────┐                       │
│  │   FBS-BookWriter Skill           │                       │
│  │   - 读取本地 JSON 获取余额        │                       │
│  │   - 支持离线/弱网                 │                       │
│  └──────────────────────────────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 数据流

**积分变动流程（幂等）**：
```
业务层调用 changePoints(eventId)
        ↓
检查 event_id 是否已存在
        ↓
若存在：返回既有余额（幂等）
若不存在：正常执行积分变动
        ↓
插入 wx_points_record（含 event_id）
        ↓
更新 sys_user.points
```

**积分同步流程**：
```
LedgerSync 定时器 → POST /user/info (X-FBS-API-Key)
                          ↓
                  返回 pointsBalance
                          ↓
                  写入 credits-ledger.json
```

---

## 二、数据模型扩展

### 2.1 wx_points_record 表扩展

**新增字段**：
```sql
ALTER TABLE wx_points_record ADD COLUMN event_id VARCHAR(128) COMMENT '幂等键';
ALTER TABLE wx_points_record ADD UNIQUE KEY uk_event_id (event_id);
```

### 2.2 event_id 生成规则

| event_type | event_id 格式 | 示例 |
|------------|---------------|------|
| FIRST_INSTALL | `first_install_{userId}` | `first_install_1` |
| DAILY_LOGIN | `daily_login_{userId}_{date}` | `daily_login_1_2026-04-16` |
| SKILL_CONSUME | `skill_consume_{usageId}` | `skill_consume_12345` |
| ADMIN_GRANT | `admin_grant_{adminId}_{timestamp}` | `admin_grant_1_1713254400` |

---

## 三、服务层扩展

### 3.1 IPointsService 接口扩展

```java
public interface IPointsService {
    // 原有方法（保持不变）
    public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount);
    
    // Skill 消费场景（保持不变）
    public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount,
                                   Long scenePackId, String usageRecordId);
    
    // 新增：幂等控制
    /**
     * 积分变动（支持幂等）
     * 
     * @param userId 用户ID
     * @param ruleCode 规则编码
     * @param changeAmount 积分变动值
     * @param scenePackId 场景包ID
     * @param usageRecordId 使用记录幂等键
     * @param eventId 事件幂等键（可选，用于行为事件去重）
     * @return 结果
     */
    public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount,
                                   Long scenePackId, String usageRecordId, String eventId);
    
    // 其他方法保持不变...
}
```

### 3.2 PointsServiceImpl 实现要点

#### 幂等检查逻辑

```java
@Override
@Transactional(rollbackFor = Exception.class)
public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount,
                               Long scenePackId, String usageRecordId, String eventId) {
    // 免费包：直接返回成功
    if (StringUtils.isEmpty(ruleCode)) {
        return AjaxResult.success("免费包，无需扣减积分");
    }
    
    // 参数校验
    if (userId == null) {
        return AjaxResult.error("用户ID不能为空");
    }
    
    // ===== 幂等检查（新增） =====
    // 注意：MySQL UNIQUE 索引允许多个 NULL，eventId=null 不会触发幂等（符合预期）
    // StringUtils.hasText 也排除了空字符串 "" 的情况
    if (StringUtils.hasText(eventId)) {
        PointsRecord existing = pointsRecordMapper.selectByEventId(eventId);
        if (existing != null) {
            // 已处理，返回既有余额（幂等）
            return AjaxResult.success("积分操作成功（幂等）", existing.getBalanceAfter());
        }
    }
    // ============================
    
    // 获取积分规则
    PointsRule rule = pointsRuleService.getRuleByCode(ruleCode);
    if (rule == null || !"0".equals(rule.getStatus())) {
        return AjaxResult.error("积分规则未配置或已停用");
    }
    
    // 计算实际变动值
    Integer actualChange = changeAmount != null ? changeAmount : rule.getPointsValue();
    if (actualChange == null || actualChange == 0) {
        return AjaxResult.error("积分变动值无效");
    }
    
    // 查询当前余额
    Integer currentPoints = getUserPoints(userId);
    if (currentPoints == null) {
        currentPoints = 0;
    }
    
    // 限频校验（使用规则编码）
    if (!pointsRuleService.checkLimit(userId, ruleCode, rule)) {
        return AjaxResult.error("已达到限频上限，请稍后再试");
    }
    
    // 累计上限校验
    if (!pointsRuleService.checkMaxAmount(userId, ruleCode, actualChange, rule)) {
        return AjaxResult.error("已达到累计上限，无法继续发放");
    }
    
    // 余额校验（扣减场景）
    if (actualChange < 0 && (currentPoints + actualChange) < 0) {
        return AjaxResult.error("积分余额不足，扣减失败");
    }
    
    // 更新积分余额
    Integer newPoints = currentPoints + actualChange;
    pointsMapper.updateUserPoints(userId, newPoints);
    
    // 插入积分记录（包含 event_id）
    PointsRecord record = new PointsRecord();
    record.setUserId(userId);
    record.setRuleCode(ruleCode);
    record.setChangeAmount(actualChange);
    record.setBalanceBefore(currentPoints);
    record.setBalanceAfter(newPoints);
    record.setScenePackId(scenePackId);
    record.setUsageRecordId(usageRecordId);
    record.setEventId(eventId);  // ← 新增
    // 注意：create_time 在 XML 中硬编码为 NOW()，此处无需 setCreateTime
    pointsRecordMapper.insertPointsRecord(record);
    
    // 记录限频
    pointsRuleService.markLimit(userId, ruleCode, rule);
    
    return AjaxResult.success("积分操作成功", newPoints);
}
```

---

## 四、API 设计

### 4.1 复用现有端点

**本轮不新增 Skill API 端点**。LedgerSync 复用现有 `/fbs/skill-api/user/info`。

| 方法 | 路径 | 说明 | 来源 |
|------|------|------|------|
| POST | `/fbs/skill-api/user/info` | 返回 `pointsBalance` + `activatedPacks` | OpenSpec #5 已有 |

**请求**（已有）：
```json
{
  "userId": 1
}
```

**响应**（已有）：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "userId": 1,
    "pointsBalance": 1000,
    "activatedPacks": [...]
  }
}
```

**认证**：
- 复用现有 `/fbs/skill-api/**` 的 API Key 认证（`X-FBS-API-Key` Header）
- 无需新增 SecurityConfig 配置

**LedgerSync 取值**：`data.pointsBalance`

---

## 五、LedgerSync 进程设计

### 5.1 Python 实现

```python
#!/usr/bin/env python3
"""LedgerSync - 积分同步进程"""

import requests
import json
import time
import os
from datetime import datetime

# 配置（环境变量）
API_BASE_URL = os.environ.get('API_BASE_URL', 'http://localhost:8080/fbs/skill-api')
API_KEY = os.environ.get('API_KEY', 'your_api_key_here')
USER_ID = int(os.environ.get('USER_ID', '1'))
SYNC_INTERVAL = int(os.environ.get('SYNC_INTERVAL', '300'))  # 5分钟
OUTPUT_PATH = os.environ.get('OUTPUT_PATH', './credits-ledger.json')

def fetch_balance():
    """调用 /user/info 获取用户积分余额"""
    url = f"{API_BASE_URL}/user/info"
    headers = {'X-FBS-API-Key': API_KEY}  # ← 注意：X-FBS-API-Key，不是 X-API-Key
    data = {'userId': USER_ID}
    
    resp = requests.post(url, headers=headers, json=data, timeout=10)
    resp.raise_for_status()
    result = resp.json()
    
    if result.get('code') == 200:
        return result['data']['pointsBalance']
    else:
        raise Exception(result.get('msg', '未知错误'))

def write_ledger(balance):
    """写入本地 JSON 文件"""
    data = {
        'user_id': USER_ID,
        'balance': balance,
        'last_updated': datetime.utcnow().isoformat() + 'Z',
        'sync_version': 'v1.0.0'
    }
    
    with open(OUTPUT_PATH, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def main():
    """主循环"""
    print(f"LedgerSync 启动，用户ID: {USER_ID}，轮询间隔: {SYNC_INTERVAL}s")
    
    while True:
        try:
            balance = fetch_balance()
            write_ledger(balance)
            print(f"[{datetime.now().isoformat()}] 同步成功: balance={balance}")
        except Exception as e:
            print(f"[{datetime.now().isoformat()}] 同步失败: {e}")
        
        time.sleep(SYNC_INTERVAL)

if __name__ == '__main__':
    main()
```

### 5.2 本地文件格式

`credits-ledger.json`：
```json
{
  "user_id": 1,
  "balance": 1000,
  "last_updated": "2026-04-16T12:00:00Z",
  "sync_version": "v1.0.0"
}
```

### 5.3 环境变量配置

```bash
export API_BASE_URL="http://your-domain/fbs/skill-api"
export API_KEY="fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
export USER_ID="1"
export SYNC_INTERVAL="300"
export OUTPUT_PATH="./credits-ledger.json"
```

---

## 六、测试设计

### 6.1 单元测试

#### PointsServiceImplTest
- `testChangePoints_WithEventId_Idempotent()` — 幂等测试
- `testChangePoints_WithoutEventId_Normal()` — 正常流程测试

### 6.2 集成测试

- 手动运行 LedgerSync 进程
- 验证 `credits-ledger.json` 文件生成
- 验证内容正确性

---

## 七、部署说明

### 7.1 后端部署

- 无特殊要求，常规 Spring Boot 部署
- 需要执行 SQL 脚本添加 `event_id` 字段

### 7.2 LedgerSync 部署

```bash
# 安装依赖
pip install requests

# 配置环境变量
export API_BASE_URL="http://your-domain/fbs/skill-api"
export API_KEY="fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
export USER_ID="1"

# 启动
python ledgersync.py
```

---

## 八、风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| LedgerSync 进程崩溃 | systemd 自动重启 + 心跳检测 |
| API 不可用 | 重试机制 + 本地缓存 |
| 余额不一致 | 定期对账 + 异常告警 |
| event_id 冲突 | 唯一索引 + 事务保证 |
