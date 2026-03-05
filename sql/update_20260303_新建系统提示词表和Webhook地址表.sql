
CREATE TABLE `system_prompts`  (
                                  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  `name` VARCHAR(255) NOT NULL COMMENT '提示词名称（建议唯一，可配合版本）',
                                  `content` TEXT NOT NULL COMMENT '提示词内容（即系统提示词）',
                                  `description` VARCHAR(500) DEFAULT NULL COMMENT '简要描述',
                                  `version` VARCHAR(50) DEFAULT '1.0' COMMENT '版本号',
                                  `status` TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
                                  `category` VARCHAR(100) DEFAULT NULL COMMENT '分类（如：翻译、代码、对话等）',
                                  `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签，可用逗号分隔或JSON格式',
                                  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uniq_name_version` (`name`, `version`),  -- 保证同一名称下版本唯一
                                  KEY `idx_status` (`status`),
                                  KEY `idx_category` (`category`),
                                  KEY `idx_update` (`update_time`)                     -- 方便按更新时间查询
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信智能机器人系统提示词表';

CREATE TABLE `wc_webhook_url`  (
                                  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                                  `name` VARCHAR(100) NOT NULL COMMENT 'Webhook 名称，如“生产环境告警”',
                                  `webhook_url` VARCHAR(512) NOT NULL COMMENT '企业微信 Webhook 地址',
                                  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述信息',
                                  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
                                  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_name` (`name`) COMMENT '名称唯一，便于管理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业微信 Webhook url配置表';