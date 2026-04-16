-- OpenSpec #10: 积分模型适配
-- 添加 event_id 字段支持幂等控制
-- MySQL 5.7 兼容写法（使用存储过程）

DELIMITER $$

CREATE PROCEDURE add_event_id_if_not_exists()
BEGIN
    -- 检查 event_id 字段是否存在
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() 
        AND TABLE_NAME = 'wx_points_record' 
        AND COLUMN_NAME = 'event_id'
    ) THEN
        -- 添加 event_id 字段
        ALTER TABLE wx_points_record 
        ADD COLUMN event_id VARCHAR(128) COMMENT '幂等键（事件唯一标识）';
        
        -- 添加唯一索引
        ALTER TABLE wx_points_record 
        ADD UNIQUE KEY uk_event_id (event_id);
        
        -- 输出日志
        SELECT 'event_id 字段和唯一索引已添加成功' AS result;
    ELSE
        SELECT 'event_id 字段已存在，跳过' AS result;
    END IF;
END$$

DELIMITER ;

-- 执行存储过程
CALL add_event_id_if_not_exists();

-- 清理存储过程
DROP PROCEDURE IF EXISTS add_event_id_if_not_exists;
