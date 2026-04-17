-- 为集成测试创建 API Key
-- 使用 admin 用户的 ID（假设为 1）

-- 删除已存在的测试 Key
DELETE FROM fbs_api_key WHERE api_key LIKE 'fbs_test_%';

-- 创建测试 API Key（绑定到 admin 用户，user_id=1）
INSERT INTO fbs_api_key (
  api_key, 
  user_id,
  key_name, 
  rate_limit_per_min, 
  status, 
  created_by, 
  created_time
) VALUES (
  'fbs_test_skill_integration_key_001',
  1,  -- admin 用户
  'Skill 集成测试 Key',
  1000,
  1,  -- 启用状态
  'admin',
  NOW()
);

-- 验证
SELECT 
  id, 
  api_key, 
  user_id, 
  key_name, 
  status,
  created_time
FROM fbs_api_key 
WHERE api_key = 'fbs_test_skill_integration_key_001';
