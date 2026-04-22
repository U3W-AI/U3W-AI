-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-04-16
-- 变更类型：ALTER 表（新增字段 + 唯一索引）
-- 变更内容：wx_points_record 表添加 event_id 字段支持幂等控制
-- 影响范围：wx_points_record 表（新增字段和唯一索引）
-- 回滚方式：DROP INDEX uk_event_id ON wx_points_record; ALTER TABLE wx_points_record DROP COLUMN event_id
-- 兼容性：使用存储过程实现幂等（IF NOT EXISTS）
-- 说明：event_id 用于实现积分记录的幂等控制，避免重复发放
-- ============================================================

DELIMITER $$

CREATE PROCEDURE add_event_id_if_not_exists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'wx_points_record'
        AND COLUMN_NAME = 'event_id'
    ) THEN
        ALTER TABLE wx_points_record
        ADD COLUMN event_id VARCHAR(128) COMMENT '幂等键（事件唯一标识）';

        ALTER TABLE wx_points_record
        ADD UNIQUE KEY uk_event_id (event_id);

        SELECT 'event_id 字段和唯一索引已添加成功' AS result;
    ELSE
        SELECT 'event_id 字段已存在，跳过' AS result;
    END IF;
END$$

DELIMITER ;

CALL add_event_id_if_not_exists();
DROP PROCEDURE IF EXISTS add_event_id_if_not_exists;
