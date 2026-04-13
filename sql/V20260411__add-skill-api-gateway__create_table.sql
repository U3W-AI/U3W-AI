-- ============================================================
-- add-skill-api-gateway：新建 fbs_api_key 表
-- 命名规范：V{日期}__{change-id}__{description}.sql
-- 执行顺序：本文件全量执行，幂等（IF NOT EXISTS）
-- ============================================================

-- ----------------------------
-- 1. API Key 管理表 fbs_api_key
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fbs_api_key` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `api_key`           VARCHAR(64)  NOT NULL COMMENT 'API Key（MVP明文存储，后续迭代改为SHA-256 hash）',
    `name`              VARCHAR(128) NOT NULL COMMENT 'API Key 名称',
    `pack_code`         VARCHAR(64)  DEFAULT NULL COMMENT '关联场景包编码（NULL=全局Key）',
    `rate_limit_per_min` INT         NOT NULL DEFAULT 60 COMMENT '每分钟速率限制',
    `status`            TINYINT      NOT NULL DEFAULT 1  COMMENT '1=启用, 0=禁用',
    `created_by`        VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time`       DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_by`        VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
    `update_time`       DATETIME     DEFAULT NULL COMMENT '更新时间',
    `remark`            VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_key` (`api_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='FBS API Key 管理表';
