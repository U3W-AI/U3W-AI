-- ============================================================
-- U3W-AI 企业微信智能机器人编排加密内容工件
-- 变更日期：2026-07-11
-- 目标：保存经 schema 白名单投影后的租户隔离内容工件，供编排能力读取。
-- 边界：不保存解密原始回调、response_url、企微身份明文、临时媒体 URL 或密钥。
-- 前置：update_20260711_企微智能机器人编排控制面建表.sql
-- 回滚：先确认无活动 Run，再删除 input_ref 列与 fbs_smartbot_input_artifact。
-- ============================================================

CREATE TABLE IF NOT EXISTS `fbs_smartbot_input_artifact` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `input_ref`             VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '内部定位引用，不是解密凭据',
    `purpose`               VARCHAR(64)  NOT NULL COMMENT '固定 smartbot.input.message.v1',
    `inbound_event_id`      BIGINT       NOT NULL COMMENT '入站账本 ID',
    `run_id`                CHAR(36)     NOT NULL COMMENT '编排运行 ID',
    `bot_binding_id`        BIGINT       NOT NULL,
    `enterprise_id`         BIGINT       NOT NULL,
    `enterprise_member_id`  BIGINT       NOT NULL,
    `user_id`               BIGINT       NOT NULL,
    `msg_type`              VARCHAR(32)  NOT NULL,
    `source_payload_hash`   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原始解密负载 SHA-256，仅用于账本关联',
    `content_hash`          CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '投影后内容 SHA-256',
    `cipher_algorithm`      VARCHAR(32)  NOT NULL COMMENT 'AES/GCM/NoPadding',
    `key_ref`               VARCHAR(255) NOT NULL COMMENT '仅密钥引用，不存密钥',
    `key_version`           INT          NOT NULL,
    `nonce`                 VARBINARY(12) NOT NULL COMMENT '每条 AES-GCM 随机 nonce',
    `ciphertext`            MEDIUMBLOB   NOT NULL COMMENT '内容密文及 GCM tag',
    `aad_hash`              CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '绑定作用域 AAD 的 SHA-256',
    `plaintext_size`        INT          NOT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'AVAILABLE',
    `expires_at`            DATETIME     NOT NULL,
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fbs_smartbot_input_ref` (`input_ref`),
    UNIQUE KEY `uk_fbs_smartbot_input_inbound` (`inbound_event_id`),
    UNIQUE KEY `uk_fbs_smartbot_input_run` (`run_id`),
    UNIQUE KEY `uk_fbs_smartbot_input_nonce` (`key_ref`, `key_version`, `nonce`),
    KEY `idx_fbs_smartbot_input_expiry` (`status`, `expires_at`),
    KEY `idx_fbs_smartbot_input_enterprise` (`enterprise_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微智能机器人加密内容工件';

ALTER TABLE `fbs_orchestration_step`
    ADD COLUMN `input_ref` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
        COMMENT '加密内容工件引用，不是解密凭据' AFTER `status`;
