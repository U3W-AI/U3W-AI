# OpenSpec #12 集成测试报告

**时间**: 2026/4/17 16:23:30

**结果**: ✅ 通过 6 | ❌ 失败 0 | ⚠️ 警告 0 | ⏭️ 跳过 0

## 测试详情

| 测试项 | ID | 状态 | 详情 |
|--------|----|----|------|
| 后端服务可达 | H1 | PASS | ✅ 登录成功，服务正常 |
| /user/info 不传 userId | T1.1 | PASS | ✅ userId=1, balance=108 |
| /user/info 传 userId | T1.2 | PASS | ✅ 向后兼容正常 |
| /usage/consume | T2.1 | PASS | ✅ usageRecordId=test-1776414206422-i5sd1c, remain=98 |
| 幂等性 | T2.2 | PASS | ✅ 相同 usageRecordId 返回相同结果 |
| 无效 API Key | T3.1 | PASS | ✅ 正确返回 401 |

## 运行说明

运行此测试前，请设置环境变量：

```powershell
$env:FBS_TEST_API_KEY = "你的API Key"
$env:FBS_TEST_USER_ID = "你的用户ID"  # 可选，如果 API Key 已绑定用户则不需要
node test_skill_api_integration_v2.mjs
```