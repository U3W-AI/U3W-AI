-- ============================================================
-- frontend-api-key-management：扩展 fbs_api_key 表字段
-- 命名规范：V{日期}__{change-id}__{description}.sql
-- 执行说明：本文件包含 ALTER TABLE，幂等（IF NOT EXISTS 通过存储过程实现）
-- ============================================================

-- ----------------------------
-- 1. 新增 user_id + last_used_at 字段（绑定用户）
-- ----------------------------
DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$

CREATE PROCEDURE add_column_if_not_exists()
BEGIN
    -- 检查 user_id 字段是否存在
    IF NOT EXISTS (
        SELECT * FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() 
        AND TABLE_NAME = 'fbs_api_key' 
        AND COLUMN_NAME = 'user_id'
    ) THEN
        ALTER TABLE fbs_api_key 
        ADD COLUMN `user_id` BIGINT COMMENT '绑定用户ID' AFTER `api_key`;
    END IF;
    
    -- 检查 last_used_at 字段是否存在
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

-- 执行存储过程
CALL add_column_if_not_exists();

-- 清理存储过程
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- ----------------------------
-- 2. 新增索引 idx_user_id（幂等）
-- ----------------------------
DELIMITER $$

DROP PROCEDURE IF EXISTS add_index_if_not_exists$$

CREATE PROCEDURE add_index_if_not_exists()
BEGIN
    -- 检查索引是否存在
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

-- 执行存储过程
CALL add_index_if_not_exists();

-- 清理存储过程
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
