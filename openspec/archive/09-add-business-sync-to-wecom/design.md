# OpenSpec #9 技术设计

> **变更 ID**: `09-add-business-sync-to-wecom`
> **版本**: v1.0（MVP 简化版）

---

## 一、架构设计

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                  业务层（已存在）                              │
│  SkillConsumeServiceImpl.consume()                          │
│    → 扣积分 → 记录使用记录                                     │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ↓ (新增调用)
┌─────────────────────────────────────────────────────────────┐
│              同步层（本次新增）                                │
│  WecomBusinessSyncService.syncCommercialHub()                │
│    → 字段映射 → 构建 records                                  │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ↓ (复用 #7)
┌─────────────────────────────────────────────────────────────┐
│              写入层（#7 已实现）                               │
│  WecomWriteService.writeRecords("commercial_hub", records)   │
│    → 白名单校验 → 20KB 分片写入                                │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ↓
┌─────────────────────────────────────────────────────────────┐
│              企微智能表格                                     │
│  commercial_hub Sheet（已配置 sheet_id）                      │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **最小改动** | 只新增服务，不修改现有基础设施 |
| **复用 #7** | 直接使用 `WecomWriteService.writeRecords()` |
| **失败隔离** | 同步失败不影响业务主流程 |
| **同步调用** | 不使用异步机制（简化实现） |

---

## 二、服务设计

### 2.1 WecomBusinessSyncService 接口

```java
package com.wx.fbsir.business.fbs.service;

/**
 * 企微业务同步服务
 */
public interface WecomBusinessSyncService {

    /**
     * 同步积分消费到 commercial_hub Sheet
     *
     * @param userId        用户ID
     * @param packCode      场景包编码
     * @param pointsAmount  消费积分数量（正数）
     * @param remainPoints  剩余积分
     */
    void syncCommercialHub(Long userId, String packCode, int pointsAmount, int remainPoints);
}
```

### 2.2 WecomBusinessSyncServiceImpl 实现

```java
package com.wx.fbsir.business.fbs.service.impl;

@Service
public class WecomBusinessSyncServiceImpl implements WecomBusinessSyncService {

    private static final Logger log = LoggerFactory.getLogger(WecomBusinessSyncServiceImpl.class);

    @Autowired
    private WecomWriteService wecomWriteService;

    @Override
    public void syncCommercialHub(Long userId, String packCode, int pointsAmount, int remainPoints) {
        try {
            // 1. 构建记录
            Map<String, Object> record = buildCommercialHubRecord(userId, packCode, pointsAmount, remainPoints);

            // 2. 调用写入服务
            List<Map<String, Object>> records = Collections.singletonList(record);
            WecomSyncWriteResponse response = wecomWriteService.writeRecords("commercial_hub", records);

            // 3. 日志记录
            if (response.isSuccess()) {
                log.info("同步积分消费成功 userId={}, packCode={}, recordCount={}",
                        userId, packCode, response.getRecordCount());
            } else {
                log.warn("同步积分消费失败 userId={}, packCode={}, errorCode={}, errorMsg={}",
                        userId, packCode, response.getErrorCode(), response.getErrorMessage());
            }
        } catch (Exception e) {
            // 同步失败不影响业务
            log.error("同步积分消费异常 userId={}, packCode={}", userId, packCode, e);
        }
    }

    private Map<String, Object> buildCommercialHubRecord(Long userId, String packCode,
                                                          int pointsAmount, int remainPoints) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("record_id", UUID.randomUUID().toString());
        record.put("record_type", "SKILL_USAGE");
        record.put("user_id", userId);
        record.put("genre", packCode);
        record.put("event", "CONSUME");
        record.put("delta", -pointsAmount); // 消费是负数
        record.put("balance_after", remainPoints);
        record.put("credits_required", pointsAmount);
        record.put("status", "SUCCESS");
        record.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return record;
    }
}
```

---

## 三、触发点集成

### 3.1 SkillConsumeServiceImpl 集成

**文件**：`WxFbsir-business/src/main/java/com/wx/fbsir/business/fbs/service/impl/SkillConsumeServiceImpl.java`

**修改点**：在 `consume()` 方法成功返回前，调用同步服务。

```java
// 原有代码：扣积分、记录使用记录...

log.info("Skill消费成功 userId={}, packCode={}, usageRecordId={}, pointsAmount={}, remainPoints={}",
        userId, packCode, usageRecordId, pointsAmount, remainPoints);

// ===== 新增：同步到企微智能表格 =====
try {
    if (wecomBusinessSyncService != null) {
        wecomBusinessSyncService.syncCommercialHub(userId, packCode, pointsAmount, remainPoints);
    }
} catch (Exception e) {
    // 同步失败不影响业务
    log.warn("同步积分消费到企微失败 userId={}, packCode={}", userId, packCode, e);
}

return ConsumeResult.success(usageRecordId, remainPoints);
```

**注入依赖**：
```java
@Autowired(required = false)
private WecomBusinessSyncService wecomBusinessSyncService;
```

---

## 四、字段映射详解

### 4.1 commercial_hub 字段映射

| 智能表格字段 | Java 来源 | 类型 | 说明 |
|--------------|-----------|------|------|
| `record_id` | `UUID.randomUUID()` | String | 唯一标识 |
| `record_type` | 固定 `"SKILL_USAGE"` | String | 记录类型 |
| `user_id` | `userId` | Long | 用户 ID |
| `genre` | `packCode` | String | 场景包编码 |
| `event` | 固定 `"CONSUME"` | String | 事件类型 |
| `delta` | `-pointsAmount` | Integer | 变更量（负数） |
| `balance_after` | `remainPoints` | Integer | 变更后余额 |
| `credits_required` | `pointsAmount` | Integer | 所需积分 |
| `status` | 固定 `"SUCCESS"` | String | 状态 |
| `created_at` | `LocalDateTime.now()` | String | 时间戳 |

---

## 五、错误处理

### 5.1 异常捕获策略

| 场景 | 处理方式 |
|------|----------|
| 同步服务注入失败 | `required=false`，跳过同步 |
| 同步方法抛异常 | catch 并记录日志，不影响业务 |
| 写入服务返回失败 | 记录警告日志，不影响业务 |

### 5.2 日志规范

```java
log.info("同步积分消费成功 userId={}, packCode={}, recordCount={}", ...);
log.warn("同步积分消费失败 userId={}, packCode={}, errorCode={}, errorMsg={}", ...);
log.error("同步积分消费异常 userId={}, packCode={}", userId, packCode, e);
```

---

## 六、不修改的内容（明确声明）

| 模块 | 不修改原因 |
|------|------------|
| `WecomWriteServiceImpl` 白名单 | `commercial_hub` 已在白名单中，可直接使用 |
| `application.yml` 配置 | `commercial_hub` 的 `sheet_id` 已配置 |
| `FbsScenePack` 实体 | MVP 只同步 `commercial_hub`，不涉及场景包 |

---

## 七、延期设计（entitlement）

以下设计延期到后续版本，不在 MVP 范围：

### 7.1 需要扩展的基础设施

1. **WecomWriteServiceImpl 白名单**：添加 `"entitlement"` 到 `ALLOWED_SHEETS`
2. **WecomSyncServiceImpl 读取白名单**：添加 `"entitlement"` 到 `ALLOWED_SHEETS`
3. **application.yml 配置**：添加 `wecom.sheet.sheet-ids.entitlement`
4. **resolveSheetId 方法**：添加 `case "entitlement": return entitlementSheetId;`

### 7.2 字段映射挑战

`FbsScenePack` 缺少以下字段：
- `creditsRequired` → 需从 `pointsRuleCode` + `wx_points_rule` 推导
- `trialAllowed` → 需新增字段或固定默认值
- `enterpriseOnly` → 需新增字段或固定默认值

### 7.3 Upsert 逻辑

需要实现：
1. 按 `genre` 查询 `entitlement` 现有记录
2. 存在则获取 `record_id`，调用 `updateRecords`
3. 不存在则调用 `writeRecords` 新增

---

## 八、总结

**MVP 范围**：
- 新增 `WecomBusinessSyncService` + 实现
- 在 `SkillConsumeServiceImpl` 中调用
- 只同步 `commercial_hub`

**复用能力**：
- `WecomWriteService.writeRecords()` (#7)
- `commercial_hub` 白名单和配置 (#7)

**不修改**：
- 不扩展白名单
- 不修改实体类
- 不修改配置文件
