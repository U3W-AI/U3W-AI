# OpenSpec #12 集成测试报告

**时间**: 2026-04-20 16:59:00

**结果**: [OK] 通过 6 | [FAIL] 失败 2 | [WARN] 警告 3 | [SKIP] 跳过 0

## 测试详情

| 测试项 | ID | 状态 | 详情 |
|--------|----|----|------|
| 管理员登录 | A0 | [OK] | token=eyJhbGciOiJIUzUxMiJ9... |
| 获取 API Key | A1 | [OK] | apiKey=fbs_ddae59ed0ef6e3c3..., userId=1 |
| /user/info 不传 userId | T5.1.1 | [OK] | userId=1, balance=1641 |
| /user/info 传 userId | T5.1.2 | [OK] | 向后兼容正常 |
| 无效 API Key | T5.1.3 | [OK] | 正确返回 401 |
| /usage/consume 不传 userId | T5.1.4 | [FAIL] | HTTP 200, code=500, msg=场景包不存在: general |
| 幂等性测试 | T5.1.5 | [FAIL] | HTTP 200, msg=场景包不存在: general |
| 缺少时间戳 Header → 401 | T15.1 | [WARN] | HTTP 401 msg=时间戳缺失 |
| 缺少签名 Header → 401 | T15.2 | [WARN] | HTTP 401 msg=签名缺失 |
| 签名不匹配 → 401 | T15.3 | [WARN] | HTTP 401 msg=签名不匹配 |
| 正确签名+时间戳 → 200 | T15.4 | [OK] |  |
