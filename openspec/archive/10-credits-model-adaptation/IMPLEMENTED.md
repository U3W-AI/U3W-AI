# OpenSpec #10 实施完成

**Change ID**: `credits-model-adaptation`  
**实施日期**: 2026-04-16  
**实施状态**: ✅ 完成

---

## 实施摘要

OpenSpec #10（积分模型适配与 LedgerSync）已完成所有开发任务。

### 核心改动

1. **数据表扩展**：`wx_points_record` 添加 `event_id` 字段（幂等控制）
2. **服务层扩展**：`IPointsService` 新增 `changePoints(eventId)` 重载
3. **LedgerSync 进程**：Python 脚本，复用 `/user/info` 端点
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
| Script | `ledgersync/ledgersync.py` | 新增 LedgerSync 进程 |
| Script | `ledgersync/ledgersync-skill-bridge.py` | 新增 LedgerSync Skill Bridge（后端积分同步到 Skill 本地 credits-ledger.json） |
| Test | `WxFbsir-business/.../point/service/impl/PointsServiceImplTest.java` | 新增幂等测试 |

---

## 下一步

运行以下命令验证实施：

```bash
# 1. 执行 SQL 脚本
mysql -u root -p wxdb < sql/V20260416__10-credits-model-adaptation__add_event_id.sql

# 2. 运行单元测试
mvn test -Dtest=PointsServiceImplTest

# 3. 启动 LedgerSync 进程（需要配置环境变量）
cd ledgersync
export API_BASE_URL="http://localhost:8080/fbs/skill-api"
export API_KEY="fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
export USER_ID="1"
python ledgersync.py

# 3b. 同步到 Skill 本地账本（一次性）
python ledgersync-skill-bridge.py --once --skill-root "/path/to/fbs-bookwriter" --api-key "fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

---

**准备好归档后，可回复 "openspec归档 10" 或 "归档提案"。**
