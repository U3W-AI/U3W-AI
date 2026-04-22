-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-04-11
-- 变更类型：新建表
-- 变更内容：技能 API 网关建表
-- 影响范围：新建 fbs_api_key 表
-- 回滚方式：DROP TABLE fbs_api_key
-- 兼容性：CREATE TABLE IF NOT EXISTS（幂等）
-- 说明：API Key 管理表，用于 Skill API 的身份认证和流量控制
-- 安全说明：api_key 明文存储，HMAC签名需原文校验
-- ============================================================

-- ============================================================
-- 1. API Key 管理表 fbs_api_key
-- ============================================================
-- 说明：存储 API Key 信息，支持按用户/场景包绑定，提供速率限制能力
-- 字段说明：
--   - api_key: API Key 值（明文存储，HMAC签名需原文校验）
--   - pack_code: 关联场景包编码（NULL=全局Key）
--   - rate_limit_per_min: 每分钟速率限制
--   - status: 1=启用, 0=禁用
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fbs_api_key` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `api_key`           VARCHAR(64)  NOT NULL COMMENT 'API Key（明文存储，HMAC签名需原文校验）',
    `name`              VARCHAR(128) NOT NULL COMMENT 'API Key 名称',
    `pack_code`         VARCHAR(64)  DEFAULT NULL COMMENT '关联场景包编码（NULL=全局Key）',
    `rate_limit_per_min` INT         NOT NULL DEFAULT 60 COMMENT '每分钟速率限制',
    `status`            TINYINT      NOT NULL DEFAULT 1  COMMENT '状态：1=启用, 0=禁用',
    `created_by`        VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time`       DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_by`        VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
    `update_time`       DATETIME     DEFAULT NULL COMMENT '更新时间',
    `remark`            VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_key` (`api_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='FBS API Key 管理表';
