# OpenSpec #12 集成测试报告

**时间**: 2026-04-20 17:01:22

**结果**: [OK] 通过 11 | [FAIL] 失败 0 | [WARN] 警告 0 | [SKIP] 跳过 0

## 测试详情

| 测试项 | ID | 状态 | 详情 |
|--------|----|----|------|
| 管理员登录 | A0 | [OK] | token=eyJhbGciOiJIUzUxMiJ9... |
| 获取 API Key | A1 | [OK] | apiKey=fbs_9841e418296c9d07..., userId=1 |
| /user/info 不传 userId | T5.1.1 | [OK] | userId=1, balance=1641 |
| /user/info 传 userId | T5.1.2 | [OK] | 向后兼容正常 |
| 无效 API Key | T5.1.3 | [OK] | 正确返回 401 |
| /usage/consume 不传 userId | T5.1.4 | [OK] | usageRecordId=test-d234ddc1d2d6, remain=1621 |
| 幂等性测试 | T5.1.5 | [OK] | 相同 usageRecordId 返回相同结果 |
| 缺少时间戳 Header -> 401 | T15.1 | [OK] | msg=时间戳缺失 |
| 缺少签名 Header -> 401 | T15.2 | [OK] | msg=签名缺失 |
| 签名不匹配 -> 401 | T15.3 | [OK] | msg=签名不匹配 |
| 正确签名+时间戳 → 200 | T15.4 | [OK] |  |
