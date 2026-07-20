-- ============================================================
-- FBSir Independent Board generic entitlement control plane
-- Migration: 20260720_independent_board_control_plane_v1
-- Target: MySQL 8
-- Scope: plans, entitlements, usage budgets/operations, durable receipts
-- Precondition: the canonical init runner has created u3w_schema_migration.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_control_plane_20260720`$$
CREATE PROCEDURE `u3w_migrate_independent_board_control_plane_20260720`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE current_connection BIGINT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE target_table_count INT DEFAULT 0;
    DECLARE target_column_count INT DEFAULT 0;
    DECLARE target_total_column_count INT DEFAULT 0;
    DECLARE target_engine_count INT DEFAULT 0;
    DECLARE target_unique_index_columns INT DEFAULT 0;
    DECLARE target_total_unique_index_columns INT DEFAULT 0;
    DECLARE target_digest_columns INT DEFAULT 0;
    DECLARE target_plan_seed_count INT DEFAULT 0;
    DECLARE target_product_plan_count INT DEFAULT 0;
    DECLARE target_internal_receipt_count INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        IF migration_lock_acquired = 1 THEN
            SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
            IF migration_lock_owner = CONNECTION_ID() THEN
                DO RELEASE_LOCK(migration_lock_name);
            END IF;
        END IF;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board control-plane migration requires an explicit target database';
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260720_independent_board_control_plane_v1'),
        256
    );
    IF CHAR_LENGTH(migration_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board migration lock name must be a stable 64-character digest';
    END IF;
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the Independent Board migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name), CONNECTION_ID()
      INTO migration_lock_owner, current_connection;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board migration lock is not owned by the current connection';
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260720_independent_board_control_plane_v1';

    SELECT COUNT(*) INTO target_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_product_plan',
          'fbs_product_entitlement',
          'fbs_usage_budget',
          'fbs_usage_operation',
          'fbs_entitlement_receipt'
      );
    IF target_table_count <> 0 AND migration_exists = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board control-plane tables exist without a migration receipt; audit before continuing';
    END IF;
    IF migration_exists <> 0 AND target_table_count <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board migration receipt exists but its table set is incomplete';
    END IF;

    IF migration_exists = 0 THEN
    CREATE TABLE IF NOT EXISTS `fbs_product_plan` (
        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
        `product_code` VARCHAR(64) NOT NULL COMMENT 'canonical product code',
        `plan_code` VARCHAR(64) NOT NULL COMMENT 'product-scoped plan code',
        `plan_name` VARCHAR(128) NOT NULL COMMENT 'display name',
        `vip` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'VIP plan flag',
        `connector_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'verified connector binding is required',
        `daily_meeting_limit` INT UNSIGNED NOT NULL COMMENT 'daily meeting allowance',
        `agenda_limit` INT UNSIGNED NOT NULL COMMENT 'maximum agenda items per meeting',
        `seat_limit` INT UNSIGNED DEFAULT NULL COMMENT 'maximum professional seats; NULL means no plan cap',
        `secretary_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Independent Board secretary entitlement',
        `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'plan lifecycle status',
        `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'optimistic concurrency version',
        `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_product_plan_code` (`product_code`, `plan_code`),
        KEY `idx_product_plan_status` (`product_code`, `status`),
        CONSTRAINT `chk_product_plan_vip` CHECK (`vip` IN (0, 1)),
        CONSTRAINT `chk_product_plan_connector` CHECK (`connector_required` IN (0, 1)),
        CONSTRAINT `chk_product_plan_secretary` CHECK (`secretary_enabled` IN (0, 1))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
      COMMENT='Generic product plan contract';

    CREATE TABLE IF NOT EXISTS `fbs_product_entitlement` (
        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
        `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT 'tenant scope',
        `member_id` BIGINT UNSIGNED NOT NULL COMMENT 'enterprise member scope',
        `user_id` BIGINT UNSIGNED NOT NULL COMMENT 'bound user identity',
        `product_code` VARCHAR(64) NOT NULL COMMENT 'canonical product code',
        `plan_code` VARCHAR(64) NOT NULL COMMENT 'effective plan code',
        `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'entitlement lifecycle status',
        `connector_binding_id` VARCHAR(128) DEFAULT NULL COMMENT 'verified connector binding identifier',
        `connector_verified_at` DATETIME(3) DEFAULT NULL COMMENT 'binding verification time',
        `valid_from` DATETIME(3) NOT NULL COMMENT 'entitlement validity start',
        `valid_until` DATETIME(3) DEFAULT NULL COMMENT 'entitlement validity end; NULL means open-ended',
        `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'optimistic concurrency version',
        `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_product_entitlement_scope` (`enterprise_id`, `member_id`, `product_code`),
        KEY `idx_product_entitlement_user` (`user_id`, `product_code`, `status`),
        KEY `idx_product_entitlement_plan` (`product_code`, `plan_code`),
        KEY `idx_product_entitlement_validity` (`status`, `valid_from`, `valid_until`),
        CONSTRAINT `fk_product_entitlement_plan`
            FOREIGN KEY (`product_code`, `plan_code`)
            REFERENCES `fbs_product_plan` (`product_code`, `plan_code`)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
        CONSTRAINT `chk_product_entitlement_validity`
            CHECK (`valid_until` IS NULL OR `valid_until` > `valid_from`),
        CONSTRAINT `chk_product_entitlement_connector_pair`
            CHECK ((`connector_binding_id` IS NULL AND `connector_verified_at` IS NULL)
                OR (`connector_binding_id` IS NOT NULL AND `connector_verified_at` IS NOT NULL))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
      COMMENT='Effective product entitlement by enterprise member';

    CREATE TABLE IF NOT EXISTS `fbs_usage_budget` (
        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
        `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT 'tenant scope',
        `member_id` BIGINT UNSIGNED NOT NULL COMMENT 'enterprise member scope',
        `product_code` VARCHAR(64) NOT NULL COMMENT 'canonical product code',
        `metric_code` VARCHAR(64) NOT NULL COMMENT 'budgeted metric, for example MEETING',
        `bucket_date` DATE NOT NULL COMMENT 'tenant-local daily bucket date',
        `daily_limit` INT UNSIGNED NOT NULL COMMENT 'effective daily allowance snapshot',
        `reserved_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'units reserved but not committed',
        `used_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'committed units',
        `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'optimistic concurrency version',
        `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_usage_budget_scope_date`
            (`enterprise_id`, `member_id`, `product_code`, `metric_code`, `bucket_date`),
        KEY `idx_usage_budget_member_date` (`member_id`, `bucket_date`),
        CONSTRAINT `chk_usage_budget_counts`
            CHECK (`reserved_count` + `used_count` <= `daily_limit`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
      COMMENT='Daily entitlement usage budget';

    CREATE TABLE IF NOT EXISTS `fbs_usage_operation` (
        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
        `operation_id` VARCHAR(128) NOT NULL COMMENT 'caller-provided idempotency identifier',
        `request_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'canonical request SHA-256',
        `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT 'tenant scope',
        `member_id` BIGINT UNSIGNED NOT NULL COMMENT 'enterprise member scope',
        `user_id` BIGINT UNSIGNED NOT NULL COMMENT 'actor user identity',
        `product_code` VARCHAR(64) NOT NULL COMMENT 'canonical product code',
        `metric_code` VARCHAR(64) NOT NULL COMMENT 'budgeted metric',
        `bucket_date` DATE NOT NULL COMMENT 'tenant-local daily bucket date',
        `units` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'requested units',
        `status` VARCHAR(32) NOT NULL COMMENT 'PENDING, RESERVED, COMMITTED, RELEASED, or REJECTED',
        `effective_plan_code` VARCHAR(64) NOT NULL COMMENT 'plan snapshot used for the decision',
        `agenda_count` INT UNSIGNED DEFAULT NULL COMMENT 'validated agenda count snapshot',
        `seat_count` INT UNSIGNED DEFAULT NULL COMMENT 'validated professional seat count snapshot',
        `remaining_count` INT UNSIGNED NOT NULL COMMENT 'remaining units after this operation',
        `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        `completed_at` DATETIME(3) DEFAULT NULL COMMENT 'terminal transition time',
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_usage_operation_enterprise_operation` (`enterprise_id`, `operation_id`),
        KEY `idx_usage_operation_scope_date`
            (`enterprise_id`, `member_id`, `product_code`, `metric_code`, `bucket_date`, `created_at`),
        KEY `idx_usage_operation_request_digest` (`request_digest`),
        CONSTRAINT `chk_usage_operation_units` CHECK (`units` > 0)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
      COMMENT='Idempotent usage budget operation ledger';

    CREATE TABLE IF NOT EXISTS `fbs_entitlement_receipt` (
        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
        `receipt_id` VARCHAR(128) NOT NULL COMMENT 'globally unique receipt identifier',
        `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT 'tenant scope',
        `actor_user_id` BIGINT UNSIGNED NOT NULL COMMENT 'actor user identity',
        `target_member_id` BIGINT UNSIGNED NOT NULL COMMENT 'entitlement target member',
        `action` VARCHAR(64) NOT NULL COMMENT 'audited entitlement action',
        `payload_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'canonical receipt payload SHA-256',
        `evidence_level` VARCHAR(32) NOT NULL COMMENT 'ACTION_COMPLETED or another reviewed evidence level',
        `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_entitlement_receipt_id` (`receipt_id`),
        KEY `idx_entitlement_receipt_enterprise_created` (`enterprise_id`, `created_at`),
        KEY `idx_entitlement_receipt_target_created` (`target_member_id`, `created_at`),
        KEY `idx_entitlement_receipt_payload` (`payload_digest`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
      COMMENT='Durable product entitlement audit receipt';

    INSERT INTO `fbs_product_plan` (
        `product_code`, `plan_code`, `plan_name`, `vip`, `connector_required`,
        `daily_meeting_limit`, `agenda_limit`, `seat_limit`, `secretary_enabled`, `status`, `version`
    ) VALUES
        ('FBSIR_INDEPENDENT_BOARD', 'BOARD_FREE', 'Independent Board Free', 0, 0, 1, 5, 3, 0, 'ACTIVE', 1),
        ('FBSIR_INDEPENDENT_BOARD', 'BOARD_VIP', 'Independent Board VIP', 1, 1, 5, 30, NULL, 1, 'ACTIVE', 1);
    END IF;

    SELECT COUNT(*) INTO target_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_product_plan',
          'fbs_product_entitlement',
          'fbs_usage_budget',
          'fbs_usage_operation',
          'fbs_entitlement_receipt'
      );
    SELECT COUNT(*) INTO target_engine_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_product_plan',
          'fbs_product_entitlement',
          'fbs_usage_budget',
          'fbs_usage_operation',
          'fbs_entitlement_receipt'
      )
      AND engine = 'InnoDB';
    IF target_table_count <> 5 OR target_engine_count <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board control-plane table or engine verification failed';
    END IF;

    SELECT COUNT(*) INTO target_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_product_plan' AND column_name IN
            ('id','product_code','plan_code','plan_name','vip','connector_required','daily_meeting_limit','agenda_limit','seat_limit','secretary_enabled','status','version','created_at','updated_at'))
        OR (table_name = 'fbs_product_entitlement' AND column_name IN
            ('id','enterprise_id','member_id','user_id','product_code','plan_code','status','connector_binding_id','connector_verified_at','valid_from','valid_until','version','created_at','updated_at'))
        OR (table_name = 'fbs_usage_budget' AND column_name IN
            ('id','enterprise_id','member_id','product_code','metric_code','bucket_date','daily_limit','reserved_count','used_count','version','created_at','updated_at'))
        OR (table_name = 'fbs_usage_operation' AND column_name IN
            ('id','operation_id','request_digest','enterprise_id','member_id','user_id','product_code','metric_code','bucket_date','units','status','effective_plan_code','agenda_count','seat_count','remaining_count','created_at','updated_at','completed_at'))
        OR (table_name = 'fbs_entitlement_receipt' AND column_name IN
            ('id','receipt_id','enterprise_id','actor_user_id','target_member_id','action','payload_digest','evidence_level','created_at')));
    SELECT COUNT(*) INTO target_total_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_product_plan',
          'fbs_product_entitlement',
          'fbs_usage_budget',
          'fbs_usage_operation',
          'fbs_entitlement_receipt'
      );
    IF target_column_count <> 67 OR target_total_column_count <> 67 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board control-plane 67-column contract has drifted';
    END IF;

    SELECT COUNT(*) INTO target_unique_index_columns
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND non_unique = 0
      AND ((table_name = 'fbs_product_plan' AND index_name = 'uk_product_plan_code'
            AND ((seq_in_index = 1 AND column_name = 'product_code') OR (seq_in_index = 2 AND column_name = 'plan_code')))
        OR (table_name = 'fbs_product_entitlement' AND index_name = 'uk_product_entitlement_scope'
            AND ((seq_in_index = 1 AND column_name = 'enterprise_id') OR (seq_in_index = 2 AND column_name = 'member_id') OR (seq_in_index = 3 AND column_name = 'product_code')))
        OR (table_name = 'fbs_usage_budget' AND index_name = 'uk_usage_budget_scope_date'
            AND ((seq_in_index = 1 AND column_name = 'enterprise_id') OR (seq_in_index = 2 AND column_name = 'member_id') OR (seq_in_index = 3 AND column_name = 'product_code') OR (seq_in_index = 4 AND column_name = 'metric_code') OR (seq_in_index = 5 AND column_name = 'bucket_date')))
        OR (table_name = 'fbs_usage_operation' AND index_name = 'uk_usage_operation_enterprise_operation'
            AND ((seq_in_index = 1 AND column_name = 'enterprise_id') OR (seq_in_index = 2 AND column_name = 'operation_id')))
        OR (table_name = 'fbs_entitlement_receipt' AND index_name = 'uk_entitlement_receipt_id'
            AND seq_in_index = 1 AND column_name = 'receipt_id'));
    SELECT COUNT(*) INTO target_total_unique_index_columns
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_product_plan',
          'fbs_product_entitlement',
          'fbs_usage_budget',
          'fbs_usage_operation',
          'fbs_entitlement_receipt'
      )
      AND non_unique = 0
      AND index_name <> 'PRIMARY';
    IF target_unique_index_columns <> 13 OR target_total_unique_index_columns <> 13 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board control-plane 13-column unique-key contract has drifted';
    END IF;

    SELECT COUNT(*) INTO target_digest_columns
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_usage_operation' AND column_name = 'request_digest')
        OR (table_name = 'fbs_entitlement_receipt' AND column_name = 'payload_digest'))
      AND column_type = 'char(64)'
      AND is_nullable = 'NO'
      AND character_set_name = 'ascii'
      AND collation_name = 'ascii_bin';
    IF target_digest_columns <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board digest columns must be ASCII binary CHAR(64)';
    END IF;

    SELECT COUNT(*) INTO target_plan_seed_count
    FROM `fbs_product_plan`
    WHERE `product_code` = 'FBSIR_INDEPENDENT_BOARD'
      AND ((`plan_code` = 'BOARD_FREE' AND `vip` = 0 AND `connector_required` = 0
            AND `daily_meeting_limit` = 1 AND `agenda_limit` = 5 AND `seat_limit` = 3
            AND `secretary_enabled` = 0 AND `status` = 'ACTIVE')
        OR (`plan_code` = 'BOARD_VIP' AND `vip` = 1 AND `connector_required` = 1
            AND `daily_meeting_limit` = 5 AND `agenda_limit` = 30 AND `seat_limit` IS NULL
            AND `secretary_enabled` = 1 AND `status` = 'ACTIVE'));
    SELECT COUNT(*) INTO target_product_plan_count
    FROM `fbs_product_plan`
    WHERE `product_code` = 'FBSIR_INDEPENDENT_BOARD';
    IF target_plan_seed_count <> 2 OR target_product_plan_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board FREE/VIP plan seed contract has drifted';
    END IF;

    IF migration_exists = 0 THEN
        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260720_independent_board_control_plane_v1',
            'Independent Board generic product plan, entitlement, budget, operation and receipt control plane'
        );
    END IF;

    SELECT COUNT(*) INTO target_internal_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260720_independent_board_control_plane_v1'
      AND `description` = 'Independent Board generic product plan, entitlement, budget, operation and receipt control plane';
    IF target_internal_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board internal migration receipt has drifted';
    END IF;

    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board migration lost advisory lock ownership before release';
    END IF;
    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board migration advisory lock release failed';
    END IF;
    SET migration_lock_acquired = 0;
END$$

CALL `u3w_migrate_independent_board_control_plane_20260720`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_control_plane_20260720`$$

DELIMITER ;
