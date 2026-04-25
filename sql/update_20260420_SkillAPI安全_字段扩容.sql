-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-04-20
-- 变更类型：ALTER 表（字段扩容）
-- 变更内容：fbs_api_key 表 api_key 字段从 VARCHAR(64) 扩容到 VARCHAR(128)
-- 影响范围：fbs_api_key 表（MODIFY COLUMN）
-- 回滚方式：ALTER TABLE fbs_api_key MODIFY COLUMN api_key VARCHAR(64) NOT NULL COMMENT 'API Key'
-- 说明：
--   - 原格式：fbs_ + Base64(24B) ≈ 36 字符，VARCHAR(64) 足够
--   - 新格式：fbs_ + Hex(32B) = 68 字符，需 VARCHAR(128)
-- ============================================================

ALTER TABLE fbs_api_key MODIFY COLUMN api_key VARCHAR(128) NOT NULL COMMENT 'API Key（Hex(32B)格式，68字符）';
