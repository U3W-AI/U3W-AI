-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-04-17
-- 变更类型：ALTER 表（新增字段 + 索引）
-- 变更内容：fbs_api_key 表扩展字段（user_id, last_used_at）和索引
-- 影响范围：fbs_api_key 表（新增 2 个字段和 1 个索引）
-- 回滚方式：DROP INDEX idx_user_id ON fbs_api_key; ALTER TABLE fbs_api_key DROP COLUMN user_id, last_used_at
-- 兼容性：使用存储过程实现幂等（IF NOT EXISTS）
-- 说明：支持 API Key 绑定用户和记录最后使用时间
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$

CREATE PROCEDURE add_column_if_not_exists()
BEGIN
    IF NOT EXISTS (
        SELECT * FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'fbs_api_key'
        AND COLUMN_NAME = 'user_id'
    ) THEN
        ALTER TABLE fbs_api_key
        ADD COLUMN `user_id` BIGINT COMMENT '绑定用户ID' AFTER `api_key`;
    END IF;

    IF NOT EXISTS (
        SELECT * FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'fbs_api_key'
        AND COLUMN_NAME = 'last_used_at'
    ) THEN
        ALTER TABLE fbs_api_key
        ADD COLUMN `last_used_at` DATETIME COMMENT '最后使用时间' AFTER `status`;
    END IF;
END$$

DELIMITER ;

CALL add_column_if_not_exists();
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

DELIMITER $$

DROP PROCEDURE IF EXISTS add_index_if_not_exists$$

CREATE PROCEDURE add_index_if_not_exists()
BEGIN
    IF NOT EXISTS (
        SELECT * FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'fbs_api_key'
        AND INDEX_NAME = 'idx_user_id'
    ) THEN
        CREATE INDEX idx_user_id ON fbs_api_key(user_id);
    END IF;
END$$

DELIMITER ;

CALL add_index_if_not_exists();
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
