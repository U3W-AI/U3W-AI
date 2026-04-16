# OpenSpec #10 Tasks: 积分模型适配与 LedgerSync

> **Change ID**: `credits-model-adaptation`
> **日期**: 2026-04-16
> **预估工时**: 6 小时

---

## Task 1: 数据表与实体（1h）

### 1.1 创建数据库表
- [ ] 创建 `fbs_credits_ledger` 表（含索引）
  - `idx_user_book` (user_id, book_id)
  - `uk_event_id` UNIQUE (event_id)

### 1.2 创建实体类
- [ ] 创建 `FbsCreditsLedger.java`（位于 `domain/entity/`）
  - 字段：id / userId / bookId / eventType / eventId / delta / balanceAfter / remark / createdBy / createdTime
  - 使用 Lombok 注解

### 1.3 创建 Mapper
- [ ] 创建 `FbsCreditsLedgerMapper.java`
- [ ] 创建 `FbsCreditsLedgerMapper.xml`
  - insert()
  - selectByEventId()
  - selectByUserIdAndBookId()

---

## Task 2: 核心服务（2h）

### 2.1 创建服务接口
- [ ] 创建 `CreditsLedgerService.java`（位于 `service/`）
  - getBalance(userId, bookId)
  - changeCredits(userId, bookId, eventType, eventId, delta, remark)
  - getLedgerRecords(userId, bookId, startTime, endTime)
  - getSnapshot(userId, bookId)

### 2.2 实现服务类
- [ ] 创建 `CreditsLedgerServiceImpl.java`（位于 `service/impl/`）
  - 实现 `getBalance()`: 从 sys_user.points 查询
  - 实现 `changeCredits()`: 幂等控制 + 事务保证
    - 检查 event_id 是否已存在
    - 加锁查询 sys_user
    - 更新 sys_user.points
    - 插入 fbs_credits_ledger
  - 实现 `getLedgerRecords()`: 按时间范围查询
  - 实现 `getSnapshot()`: 返回余额快照

### 2.3 异常处理
- [ ] 创建 `InsufficientCreditsException.java`（积分余额不足异常）

---

## Task 3: Skill API（1.5h）

### 3.1 创建 DTO
- [ ] 创建 `CreditsBalanceRequest.java`
- [ ] 创建 `CreditsBalanceResponse.java`
- [ ] 创建 `CreditsEarnRequest.java`
- [ ] 创建 `CreditsEarnResponse.java`
- [ ] 创建 `CreditsSyncResponse.java`

### 3.2 创建 Controller
- [ ] 创建 `CreditsInternalController.java`（位于 `controller/internal/`）
  - `GET /fbs/internal/credits/balance`
  - `POST /fbs/internal/credits/earn`
  - `GET /fbs/internal/credits/sync`

### 3.3 API 认证
- [ ] 复用 OpenSpec #5 的 API Key 认证机制
- [ ] 在 SecurityConfig 中配置 `/fbs/internal/credits/**` 路径权限

---

## Task 4: LedgerSync 进程（1h）

### 4.1 创建进程目录
- [ ] 创建 `ledgersync/` 目录（项目根目录）

### 4.2 编写 Python 脚本
- [ ] 创建 `ledgersync.py`（主进程）
  - load_config()
  - fetch_balance()
  - write_ledger()
  - main() 循环

### 4.3 配置文件
- [ ] 创建 `ledgersync.conf.example`（配置模板）
  - api.base_url
  - api.api_key
  - sync.interval_seconds
  - sync.output_path

### 4.4 本地文件格式
- [ ] 定义 `credits-ledger.json` 格式
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

## Task 5: 测试（0.5h）

### 5.1 单元测试
- [ ] 创建 `CreditsLedgerServiceTest.java`
  - testGetBalance()
  - testChangeCredits_Success()
  - testChangeCredits_Idempotent()
  - testChangeCredits_InsufficientBalance()

### 5.2 Controller 测试
- [ ] 创建 `CreditsInternalControllerTest.java`
  - testBalanceApi()
  - testEarnApi()
  - testSyncApi()

### 5.3 LedgerSync 集成测试
- [ ] 手动运行 LedgerSync 进程
- [ ] 验证 credits-ledger.json 生成
- [ ] 验证内容正确性

---

## Task 6: 文档更新（可选）

### 6.1 API 文档
- [ ] 更新 `docs/api/FBS-BUSINESS-API.md`
  - 新增 §14.9 积分账本 API 章节

### 6.2 阶段规划更新
- [ ] 更新 `quest/阶段规划.md`
  - OpenSpec #10 状态改为已完成

---

## 任务依赖关系

```
Task 1 (数据表与实体)
   ↓
Task 2 (核心服务)
   ↓
Task 3 (Skill API) ────┐
   ↓                   │
Task 4 (LedgerSync) ◄──┘
   ↓
Task 5 (测试)
   ↓
Task 6 (文档更新)
```

---

## 验收清单

- [ ] `fbs_credits_ledger` 表创建成功
- [ ] `CreditsLedgerService` 实现余额查询 + 幂等变动
- [ ] `/fbs/internal/credits/balance` API 返回用户积分
- [ ] `/fbs/internal/credits/earn` API 支持幂等赚取
- [ ] `/fbs/internal/credits/sync` API 返回快照
- [ ] LedgerSync 进程可以轮询 API 并写入本地 JSON
- [ ] 单元测试通过（余额计算、幂等控制）
- [ ] 集成测试通过（Skill 端可读取 credits-ledger.json）
