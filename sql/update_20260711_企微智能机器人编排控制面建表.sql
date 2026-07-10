-- ============================================================
-- U3W-AI 企业微信智能机器人编排控制面（durable-lite）
-- 变更日期：2026-07-11
-- 目标：提供多 Bot 绑定、企业成员绑定、入站消息原子排重、
--       最小 Run/Step、事务 Outbox 与分层 Receipt 的持久真相源。
-- 边界：不包含场景业务，不执行真实企微写入，不存储消息正文或明文密钥。
-- 回滚：按依赖逆序 DROP 下列表；生产回滚前必须先导出审计数据。
-- ============================================================

CREATE TABLE IF NOT EXISTS `fbs_bot_binding` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `callback_key`        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '回调路由键，用于解密前选择凭据',
    `aibot_id`            VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '企微智能机器人 ID',
    `enterprise_id`       BIGINT       NOT NULL COMMENT 'U3W 企业 ID',
    `mode`                VARCHAR(16)  NOT NULL DEFAULT 'CALLBACK' COMMENT '当前纵切片仅支持 CALLBACK',
    `token_secret_ref`    VARCHAR(255) NOT NULL COMMENT 'Token 凭据引用，不存明文',
    `aes_key_secret_ref`  VARCHAR(255) NOT NULL COMMENT 'EncodingAESKey 凭据引用，不存明文',
    `credential_version`  INT          NOT NULL DEFAULT 1 COMMENT '凭据版本',
    `status`              TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `created_by`          VARCHAR(64)  DEFAULT NULL,
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by`          VARCHAR(64)  DEFAULT NULL,
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag`            CHAR(1)      NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fbs_bot_binding_callback` (`callback_key`),
    UNIQUE KEY `uk_fbs_bot_binding_aibot` (`aibot_id`),
    KEY `idx_fbs_bot_binding_enterprise` (`enterprise_id`, `status`),
    CONSTRAINT `chk_fbs_bot_binding_mode` CHECK (`mode` = 'CALLBACK')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微智能机器人绑定';

CREATE TABLE IF NOT EXISTS `fbs_bot_member_binding` (
    `id`                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `bot_binding_id`        BIGINT      NOT NULL COMMENT '机器人绑定 ID',
    `enterprise_id`         BIGINT      NOT NULL COMMENT 'U3W 企业 ID',
    `enterprise_member_id`  BIGINT      NOT NULL COMMENT 'U3W 企业成员 ID',
    `user_id`               BIGINT      NOT NULL COMMENT 'U3W 用户 ID',
    `external_user_hash`    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '企微不透明发送者标识的 HMAC-SHA256',
    `status`                TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `created_by`            VARCHAR(64) DEFAULT NULL,
    `create_time`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by`            VARCHAR(64) DEFAULT NULL,
    `update_time`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag`              CHAR(1)     NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fbs_bot_member_external` (`bot_binding_id`, `external_user_hash`),
    KEY `idx_fbs_bot_member_member` (`enterprise_member_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微用户与 U3W 企业成员绑定';

CREATE TABLE IF NOT EXISTS `fbs_inbound_event` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `bot_binding_id`      BIGINT       NOT NULL COMMENT '机器人绑定 ID',
    `msg_id_hash`         CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Bot 作用域企微 msgid 的 HMAC-SHA256',
    `aibot_id`            VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '企微智能机器人 ID',
    `trace_id`            CHAR(36)     NOT NULL COMMENT 'U3W 全链路追踪 ID',
    `run_id`              CHAR(36)     NOT NULL COMMENT '编排运行 ID',
    `stream_id`           VARCHAR(64)  NOT NULL COMMENT '预分配稳定回复关联 ID；不代表已实现企微流状态机',
    `from_user_hash`      CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发送者不透明标识的 HMAC-SHA256',
    `chat_type`           VARCHAR(16)  DEFAULT NULL COMMENT 'single/group',
    `chat_id_hash`        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '群会话 ID 的 HMAC-SHA256',
    `msg_type`            VARCHAR(32)  NOT NULL COMMENT '消息类型',
    `event_type`          VARCHAR(64)  DEFAULT NULL COMMENT '事件类型',
    `payload_hash`        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端计算的解密入站负载 SHA-256',
    `status`              VARCHAR(32)  NOT NULL DEFAULT 'RECEIVED',
    `duplicate_count`     INT          NOT NULL DEFAULT 0,
    `first_received_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_received_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fbs_inbound_bot_msg` (`bot_binding_id`, `msg_id_hash`),
    KEY `idx_fbs_inbound_trace` (`trace_id`),
    KEY `idx_fbs_inbound_run` (`run_id`),
    KEY `idx_fbs_inbound_status_time` (`status`, `last_received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微智能机器人入站事件账本';

CREATE TABLE IF NOT EXISTS `fbs_orchestration_run` (
    `run_id`                CHAR(36)     NOT NULL COMMENT '运行 ID',
    `inbound_event_id`      BIGINT       NOT NULL COMMENT '入站事件 ID',
    `bot_binding_id`        BIGINT       NOT NULL,
    `enterprise_id`         BIGINT       NOT NULL,
    `enterprise_member_id`  BIGINT       NOT NULL,
    `user_id`               BIGINT       NOT NULL,
    `definition_code`       VARCHAR(128) NOT NULL COMMENT '编排定义代码',
    `definition_version`    INT          NOT NULL COMMENT '固定定义版本',
    `trace_id`              CHAR(36)     NOT NULL,
    `stream_id`             VARCHAR(64)  NOT NULL,
    `status`                VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    `version`               INT          NOT NULL DEFAULT 0 COMMENT 'CAS 版本',
    `next_wakeup_at`        DATETIME     DEFAULT NULL,
    `lease_owner`           VARCHAR(128) DEFAULT NULL,
    `lease_until`           DATETIME     DEFAULT NULL,
    `error_code`            VARCHAR(64)  DEFAULT NULL,
    `error_message`         VARCHAR(500) DEFAULT NULL,
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`run_id`),
    UNIQUE KEY `uk_fbs_run_inbound` (`inbound_event_id`),
    KEY `idx_fbs_run_dispatch` (`status`, `next_wakeup_at`, `lease_until`),
    KEY `idx_fbs_run_enterprise` (`enterprise_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能机器人编排运行';

CREATE TABLE IF NOT EXISTS `fbs_orchestration_step` (
    `step_id`         CHAR(36)     NOT NULL,
    `run_id`          CHAR(36)     NOT NULL,
    `step_key`        VARCHAR(128) NOT NULL,
    `attempt`         INT          NOT NULL DEFAULT 1,
    `kind`            VARCHAR(32)  NOT NULL COMMENT 'SYSTEM/CAPABILITY/HUMAN_GATE',
    `executor_type`   VARCHAR(32)  NOT NULL COMMENT 'JAVA/HTTP/MCP/PLAYWRIGHT/HUMAN',
    `executor_ref`    VARCHAR(255) DEFAULT NULL,
    `status`          VARCHAR(32)  NOT NULL,
    `input_hash`      CHAR(64)     DEFAULT NULL,
    `output_ref`      VARCHAR(512) DEFAULT NULL COMMENT '输出物引用，不存大正文',
    `evidence_ref`    VARCHAR(512) DEFAULT NULL COMMENT '证据引用',
    `version`         INT          NOT NULL DEFAULT 0,
    `lease_owner`     VARCHAR(128) DEFAULT NULL,
    `lease_until`     DATETIME     DEFAULT NULL,
    `error_code`      VARCHAR(64)  DEFAULT NULL,
    `error_message`   VARCHAR(500) DEFAULT NULL,
    `started_at`      DATETIME     DEFAULT NULL,
    `finished_at`     DATETIME     DEFAULT NULL,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`step_id`),
    UNIQUE KEY `uk_fbs_step_attempt` (`run_id`, `step_key`, `attempt`),
    KEY `idx_fbs_step_dispatch` (`status`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能机器人编排步骤与尝试';

CREATE TABLE IF NOT EXISTS `fbs_orchestration_receipt` (
    `receipt_id`        CHAR(36)     NOT NULL,
    `run_id`            CHAR(36)     NOT NULL,
    `step_id`           CHAR(36)     DEFAULT NULL,
    `receipt_type`      VARCHAR(16)  NOT NULL COMMENT 'ACTION/BUSINESS/DELIVERY',
    `source`            VARCHAR(64)  NOT NULL,
    `external_ref_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '外部回执引用哈希',
    `status`            VARCHAR(32)  NOT NULL,
    `evidence_ref`      VARCHAR(512) DEFAULT NULL,
    `verified_at`       DATETIME     DEFAULT NULL,
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`receipt_id`),
    UNIQUE KEY `uk_fbs_receipt_external` (`receipt_type`, `source`, `external_ref_hash`),
    KEY `idx_fbs_receipt_run` (`run_id`, `receipt_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动作、业务与投递分层回执';

CREATE TABLE IF NOT EXISTS `fbs_delivery_outbox` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `event_key`         VARCHAR(191) NOT NULL COMMENT '投递幂等键',
    `run_id`            CHAR(36)     NOT NULL,
    `event_type`        VARCHAR(64)  NOT NULL,
    `destination_type`  VARCHAR(32)  NOT NULL COMMENT 'INTERNAL_DISPATCHER/RESPONSE_URL',
    `destination_ref`   VARCHAR(512) DEFAULT NULL COMMENT '加密目标或内部引用',
    `payload_json`      JSON         NOT NULL COMMENT '最小事件负载，不含消息正文或凭据',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    `attempt_count`     INT          NOT NULL DEFAULT 0,
    `next_attempt_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `lease_owner`       VARCHAR(128) DEFAULT NULL,
    `lease_until`       DATETIME     DEFAULT NULL,
    `last_http_status`  INT          DEFAULT NULL,
    `last_error`        VARCHAR(500) DEFAULT NULL,
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fbs_outbox_event` (`event_key`),
    KEY `idx_fbs_outbox_dispatch` (`status`, `next_attempt_at`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能机器人事务 Outbox';
