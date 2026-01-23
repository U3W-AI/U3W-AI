-- sql/updates/update_20260122_interview_record.sql

-- ============================================
-- 数据库更新脚本
-- 功能：创建面试记录表 (interview_record)
-- 作者：WxFbsir Team
-- 日期：2026-01-22
-- ============================================

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS interview_bot 
  DEFAULT CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

-- 2. 切换到该数据库
USE interview_bot;

-- 3. 创建表结构

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