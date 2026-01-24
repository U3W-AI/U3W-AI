-- ============================================
-- 数据库初始化脚本
-- 功能：1. 检查并创建 wxfbsir 数据库
--       2. 切换到 wxfbsir 数据库
--       3. 创建面试记录表 (interview_record)
-- ============================================

-- 1. 如果不存在 wxfbsir 数据库，则创建
CREATE DATABASE IF NOT EXISTS `wxfbsir` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. 切换当前操作的数据库为 wxfbsir
USE `wxfbsir`;

-- 3. 建表

DROP TABLE IF EXISTS interview_record;

CREATE TABLE interview_record (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `document_id` VARCHAR(64) DEFAULT NULL COMMENT '文档唯一标识',
    `name` VARCHAR(32) NOT NULL COMMENT '姓名',
    `school` VARCHAR(64) DEFAULT NULL COMMENT '学校',
    `grade` VARCHAR(32) DEFAULT NULL COMMENT '年级',
    `tech_stack` VARCHAR(255) DEFAULT NULL COMMENT '技术栈',
    `summary` TEXT COMMENT '简历摘要',
    `interview_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '面试/录入时间',
    `phone_number` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    `id_card` VARCHAR(32) DEFAULT NULL COMMENT '身份证号',

    `create_by` VARCHAR(64) DEFAULT 'system' COMMENT '创建者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT 'system' COMMENT '更新者',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    
    PRIMARY KEY (`id`),
    INDEX `idx_interview_time` (`interview_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试记录表';