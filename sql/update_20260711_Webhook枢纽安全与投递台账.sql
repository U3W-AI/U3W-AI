-- 2026-07-11
-- U3W Webhook hub P0 security migration (MySQL 8, re-runnable).
-- Existing global/plaintext records are disabled and require explicit enterprise reassignment
-- plus secret rotation before they may be used again. Back up wc_webhook_url before execution;
-- clearing legacy plaintext Webhook URLs is intentionally irreversible without that backup.

CREATE TABLE IF NOT EXISTS `u3w_schema_migration` (
    `version` VARCHAR(96) NOT NULL,
    `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `description` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='U3W可重入数据库迁移记录';

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_webhook_hub_20260711`$$
CREATE PROCEDURE `u3w_migrate_webhook_hub_20260711`()
BEGIN
    DECLARE migration_lock INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        IF migration_lock = 1 THEN
            DO RELEASE_LOCK(CONCAT(DATABASE(), ':20260711_webhook_hub_security_v1'));
        END IF;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Webhook migration requires an explicit target database';
    END IF;
    SELECT GET_LOCK(CONCAT(DATABASE(), ':20260711_webhook_hub_security_v1'), 30)
      INTO migration_lock;
    IF migration_lock IS NULL OR migration_lock <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Could not acquire the Webhook migration lock';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'wc_webhook_url' AND column_name = 'enterprise_id'
    ) THEN
        ALTER TABLE `wc_webhook_url`
            ADD COLUMN `enterprise_id` BIGINT NULL COMMENT '企业ID；新记录必填' AFTER `id`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'wc_webhook_url' AND column_name = 'webhook_secret_ref'
    ) THEN
        ALTER TABLE `wc_webhook_url`
            ADD COLUMN `webhook_secret_ref` VARCHAR(1024) NULL COMMENT '加密后的Webhook密钥引用' AFTER `webhook_url`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'wc_webhook_url' AND column_name = 'version'
    ) THEN
        ALTER TABLE `wc_webhook_url`
            ADD COLUMN `version` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER `status`;
    END IF;

    UPDATE `wc_webhook_url`
    SET `status` = 0,
        `webhook_url` = '__SECRET_REF__',
        `webhook_secret_ref` = NULL
    WHERE `enterprise_id` IS NULL
      AND (`status` <> 0 OR `webhook_url` <> '__SECRET_REF__' OR `webhook_secret_ref` IS NOT NULL);

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'wc_webhook_url' AND index_name = 'uk_name'
    ) THEN
        ALTER TABLE `wc_webhook_url` DROP INDEX `uk_name`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'wc_webhook_url' AND index_name = 'uk_enterprise_name'
    ) THEN
        ALTER TABLE `wc_webhook_url`
            ADD UNIQUE KEY `uk_enterprise_name` (`enterprise_id`, `name`);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'wc_webhook_url' AND index_name = 'idx_webhook_enterprise_status'
    ) THEN
        ALTER TABLE `wc_webhook_url`
            ADD KEY `idx_webhook_enterprise_status` (`enterprise_id`, `status`);
    END IF;

    CREATE TABLE IF NOT EXISTS `wc_webhook_delivery` (
        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        `enterprise_id` BIGINT NOT NULL,
        `webhook_id` BIGINT UNSIGNED NOT NULL,
        `actor_user_id` BIGINT NOT NULL COMMENT '发起投递的U3W用户ID',
        `idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        `payload_hash` CHAR(64) NOT NULL,
        `trace_id` CHAR(36) NOT NULL,
        `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/PROVIDER_ACCEPTED/REJECTED/UNKNOWN',
        `provider_http_status` INT NULL,
        `provider_errcode` INT NULL,
        `provider_errmsg` VARCHAR(255) NULL,
        `attempt_count` INT NOT NULL DEFAULT 1,
        `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_delivery_enterprise_idempotency` (`enterprise_id`, `idempotency_key`),
        KEY `idx_delivery_trace` (`trace_id`),
        KEY `idx_delivery_webhook_time` (`webhook_id`, `create_time`),
        KEY `idx_delivery_pending_sweep` (`status`, `update_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
      COMMENT='企微消息推送幂等投递台账';

    -- Recover an earlier partial draft that created the ledger before actor auditing existed.
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'wc_webhook_delivery' AND column_name = 'actor_user_id'
    ) THEN
        ALTER TABLE `wc_webhook_delivery`
            ADD COLUMN `actor_user_id` BIGINT NULL COMMENT '发起投递的U3W用户ID' AFTER `webhook_id`;
    END IF;

    -- Converge early draft schemas to the same canonical ledger definition.
    UPDATE `wc_webhook_delivery` SET `actor_user_id` = 0 WHERE `actor_user_id` IS NULL;
    ALTER TABLE `wc_webhook_delivery`
        MODIFY COLUMN `actor_user_id` BIGINT NOT NULL COMMENT '发起投递的U3W用户ID',
        MODIFY COLUMN `idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/PROVIDER_ACCEPTED/REJECTED/UNKNOWN';

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'wc_webhook_delivery'
          AND index_name = 'idx_delivery_pending_sweep'
    ) THEN
        ALTER TABLE `wc_webhook_delivery`
            ADD KEY `idx_delivery_pending_sweep` (`status`, `update_time`);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 151) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Webhook parent menu 151 is missing';
    END IF;
    IF EXISTS (
        SELECT 1 FROM `sys_menu`
        WHERE `menu_id` = 1516
          AND (`parent_id` <> 151 OR `perms` <> 'business:wecom:send' OR `menu_type` <> 'F')
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Menu id 1516 is already used by another feature';
    END IF;

    INSERT INTO `sys_menu`
      (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
       `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`,
       `update_by`, `update_time`, `remark`)
    VALUES
      (1516, 'Webhook发送', 151, 6, '', '', '', '', 1, 0, 'F', '0', '0',
       'business:wecom:send', '#', 'admin', SYSDATE(), '', NULL, '')
    ON DUPLICATE KEY UPDATE `menu_id` = 1516;

    INSERT INTO `u3w_schema_migration` (`version`, `description`)
    VALUES ('20260711_webhook_hub_security_v1', 'Webhook租户隔离、密钥加密与幂等投递台账')
    ON DUPLICATE KEY UPDATE `description` = 'Webhook租户隔离、密钥加密与幂等投递台账';

    DO RELEASE_LOCK(CONCAT(DATABASE(), ':20260711_webhook_hub_security_v1'));
END$$

CALL `u3w_migrate_webhook_hub_20260711`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_webhook_hub_20260711`$$

DELIMITER ;
