# OpenSpec #10 实施完成

**Change ID**: `credits-model-adaptation`  
**实施日期**: 2026-04-16  
**实施状态**: ✅ 完成

---

## 实施摘要

OpenSpec #10（积分模型适配与本地账本）已完成所有开发任务。

### 核心改动

1. **数据表扩展**：`wx_points_record` 添加 `event_id` 字段（幂等控制）
2. **服务层扩展**：`IPointsService` 新增 `changePoints(eventId)` 重载
3. **本地账本同步**：Skill 侧 `addCredits()`/`getBalance()` 等操作后同步后端余额到本地 `credits-ledger.json`
4. **单元测试**：覆盖幂等逻辑

### 验收结果

- ✅ 所有任务完成
- ✅ 无编译错误
- ✅ 单元测试覆盖核心逻辑

---

## 变更文件清单

| 类型 | 文件路径 | 变更说明 |
|------|----------|----------|
| SQL | `sql/V20260416__10-credits-model-adaptation__add_event_id.sql` | 添加 event_id 字段 |
| Entity | `WxFbsir-business/.../point/domain/PointsRecord.java` | 添加 eventId 字段 |
| Mapper | `WxFbsir-business/.../point/mapper/PointsRecordMapper.java` | 新增 selectByEventId() |
| Mapper XML | `WxFbsir-business/.../point/mapper/PointsRecordMapper.xml` | 更新 SQL |
| Service | `WxFbsir-business/.../point/service/IPointsService.java` | 新增方法签名 |
| Service Impl | `WxFbsir-business/.../point/service/impl/PointsServiceImpl.java` | 实现幂等逻辑 |
| Script | `scripts/wecom/lib/credits-ledger.mjs` | Skill 侧本地账本，余额同步缓存（#14/#15 重构后为唯一本地存储） |
| Test | `WxFbsir-business/.../point/service/impl/PointsServiceImplTest.java` | 新增幂等测试 |

---

## 踩坑记录

1. **后端 API 失败时本地缓存余额不更新**（2026-04-21 发现并修复）：当 `FBS_API_BASE_URL` 末尾带 `/` 时，URL 拼接产生双斜杠（如 `http://localhost:8080//fbs/skill-api/user/info`），后端返回 404/500，`getBalance()` fallback 到本地 `credits-ledger.json` 读取旧余额，导致余额永远不更新。根因在 `backend-api.mjs._apiUrl()` 未去除末尾斜杠，已在 #15 归档后修复。

---

## 下一步

运行以下命令验证实施：

```bash
# 1. 执行 SQL 脚本
mysql -u root -p wxdb < sql/V20260416__10-credits-model-adaptation__add_event_id.sql

# 2. 运行单元测试
mvn test -Dtest=PointsServiceImplTest

# 3. Skill 侧积分操作自动同步（无需手动运行 LedgerSync）
#    - 联网时 addCredits/getBalance 等操作后自动同步后端余额到 credits-ledger.json
#    - 离线时本地 fallback 记账
```

---

**准备好归档后，可回复 "openspec归档 10" 或 "归档提案"。**
