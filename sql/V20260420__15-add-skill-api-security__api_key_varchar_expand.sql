-- #15 Skill API 安全加固：API Key 字段扩容 + 格式更新
-- 原格式：fbs_ + Base64(24B) ≈ 36 字符，VARCHAR(64) 足够
-- 新格式：fbs_ + Hex(32B) = 68 字符，需 VARCHAR(128)
-- ⚠️ 勿直接执行，需根据实际数据库环境替换

ALTER TABLE fbs_api_key MODIFY COLUMN api_key VARCHAR(128) NOT NULL COMMENT 'API Key（Hex(32B)格式，68字符）';
