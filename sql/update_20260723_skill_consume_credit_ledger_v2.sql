-- ============================================================
-- FBSir skill-consume v2 credit ledger (default-off candidate)
-- Migration: 20260723_skill_consume_credit_ledger_v2_042
-- Public manifest step: public_init_042
-- Target: exact MySQL Community 8.0.30 or 8.4.8 raw-metadata profiles
-- Scope: additive v2 ledger; never alters 038 or fbs_skill_usage_record.
-- Atomic-DDL note: MySQL atomic DDL is statement-atomic, not multi-statement
-- transactional. The public RUNNING receipt and exact current-read state
-- machine are therefore the recovery boundary.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_assert_skill_consume_credit_ledger_v2_triggers_20260723`$$
CREATE PROCEDURE `u3w_assert_skill_consume_credit_ledger_v2_triggers_20260723`()
BEGIN
    DECLARE target_trigger_count INT DEFAULT 0;
    DECLARE target_trigger_contract_count INT DEFAULT 0;

    SELECT COUNT(*) INTO target_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                                 'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');

    SELECT COUNT(*) INTO target_trigger_contract_count
    FROM (
        SELECT trigger_name, event_object_table, event_manipulation, action_timing,
               action_orientation, action_condition, action_order,
               SHA2(CAST(action_statement AS BINARY), 256) AS action_sha256
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND event_object_table IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                                     'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2')
    ) AS v2_triggers
    WHERE action_timing = 'BEFORE' AND action_orientation = 'ROW'
      AND action_condition IS NULL AND action_order = 1
      AND (
          (trigger_name = 'trg_skill_credit_account_v2_transition'
           AND event_object_table = 'fbs_skill_credit_account_v2' AND event_manipulation = 'UPDATE'
           AND CAST(action_sha256 AS BINARY) = CAST('0cee59ea32e300fb668eae3ab4f7d26053b0d61d96a6e024bc583d487a656d00' AS BINARY))
       OR (trigger_name = 'trg_skill_credit_account_v2_no_delete'
           AND event_object_table = 'fbs_skill_credit_account_v2' AND event_manipulation = 'DELETE'
           AND CAST(action_sha256 AS BINARY) = CAST('b3b22a50327eef51eae218ef63a88393ac4ec135a8fb697d94bc954b979b91da' AS BINARY))
       OR (trigger_name = 'trg_skill_credit_operation_v2_no_update'
           AND event_object_table = 'fbs_skill_credit_operation_v2' AND event_manipulation = 'UPDATE'
           AND CAST(action_sha256 AS BINARY) = CAST('34b5934eedb28e7193d3baece34efe3c2f2b0b6adec4ab751c1e40bcf3b2aa87' AS BINARY))
       OR (trigger_name = 'trg_skill_credit_operation_v2_no_delete'
           AND event_object_table = 'fbs_skill_credit_operation_v2' AND event_manipulation = 'DELETE'
           AND CAST(action_sha256 AS BINARY) = CAST('085e2bda7bd883b653f89d645718babe93cec6dca81ad6352aafe9deb6200654' AS BINARY))
       OR (trigger_name = 'trg_skill_credit_entry_v2_no_update'
           AND event_object_table = 'fbs_skill_credit_entry_v2' AND event_manipulation = 'UPDATE'
           AND CAST(action_sha256 AS BINARY) = CAST('88f1e0ce3746408140785ce97a97451095c43e229ef57e2c0de81f45b18a2cca' AS BINARY))
       OR (trigger_name = 'trg_skill_credit_entry_v2_no_delete'
           AND event_object_table = 'fbs_skill_credit_entry_v2' AND event_manipulation = 'DELETE'
           AND CAST(action_sha256 AS BINARY) = CAST('0f49dad15d89d2d1687de51f60dd6f41b096705e78b321892c02f81e4d131040' AS BINARY))
       OR (trigger_name = 'trg_skill_credit_projection_bridge_v2_transition'
           AND event_object_table = 'fbs_skill_credit_projection_bridge_v2' AND event_manipulation = 'UPDATE'
           AND CAST(action_sha256 AS BINARY) = CAST('dd4eebc8ae154cebb5a2235cf35a076c86b1e3ba644c7177b9feea2faa0843b4' AS BINARY))
       OR (trigger_name = 'trg_skill_credit_projection_bridge_v2_no_delete'
           AND event_object_table = 'fbs_skill_credit_projection_bridge_v2' AND event_manipulation = 'DELETE'
           AND CAST(action_sha256 AS BINARY) = CAST('058f1aafa0b4e28ccb1ea3eae24f2319f1c63d877fc4aa3816f6b405b743fa1d' AS BINARY))
      );

    IF target_trigger_count <> 8 OR target_trigger_contract_count <> 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Skill consume v2 ledger trigger body contract has drifted';
    END IF;
END$$

DROP PROCEDURE IF EXISTS `u3w_migrate_skill_consume_credit_ledger_v2_20260723`$$
CREATE PROCEDURE `u3w_migrate_skill_consume_credit_ledger_v2_20260723`()
BEGIN
    DECLARE migration_lock_name CHAR(64) CHARACTER SET ascii COLLATE ascii_bin;
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE public_receipt_count INT DEFAULT 0;
    DECLARE public_running_receipt_count INT DEFAULT 0;
    DECLARE internal_receipt_count INT DEFAULT 0;
    DECLARE target_table_count INT DEFAULT 0;
    DECLARE target_base_table_count INT DEFAULT 0;
    DECLARE target_engine_count INT DEFAULT 0;
    DECLARE target_collation_count INT DEFAULT 0;
    DECLARE target_index_count INT DEFAULT 0;
    DECLARE target_foreign_key_count INT DEFAULT 0;
    DECLARE target_check_count INT DEFAULT 0;
    DECLARE target_enforced_check_count INT DEFAULT 0;
    DECLARE target_trigger_count INT DEFAULT 0;
    DECLARE target_column_metadata_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_index_metadata_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_foreign_key_metadata_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_check_metadata_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_internal_receipt_count INT DEFAULT 0;
    DECLARE target_version_receipt_count INT DEFAULT 0;
    DECLARE previous_group_concat_max_len BIGINT UNSIGNED DEFAULT 1024;
    DECLARE migration_stage VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        DEFAULT 'running';

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        IF migration_lock_acquired = 1 THEN
            SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
            IF migration_lock_owner = CONNECTION_ID() THEN
                DO RELEASE_LOCK(migration_lock_name);
            END IF;
        END IF;
        SET SESSION group_concat_max_len = previous_group_concat_max_len;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Skill consume v2 ledger requires an explicit target database';
    END IF;
    IF VERSION() NOT IN ('8.0.30', '8.4.8')
       OR @@version_comment <> 'MySQL Community Server - GPL' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Skill consume v2 ledger requires exact MySQL Community 8.0.30 or 8.4.8';
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260723_skill_consume_credit_ledger_v2_042'), 256);
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire skill consume v2 ledger migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Skill consume v2 ledger migration lock is not owned by this connection';
    END IF;
    SET previous_group_concat_max_len = @@SESSION.group_concat_max_len;
    IF previous_group_concat_max_len < 1048576 THEN
        SET SESSION group_concat_max_len = 1048576;
    END IF;

    SET migration_stage = 'running';
    SET migration_stage = 'preflight';
    SELECT COUNT(*) INTO public_receipt_count
    FROM `u3w_schema_migration` WHERE `version` = 'public_init_042';
    SELECT COUNT(*) INTO public_running_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = 'public_init_042'
      AND `description` = 'RUNNING:Independent Board default-off skill-consume v2 credit ledger';
    IF public_receipt_count <> 1 OR public_running_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Skill consume v2 ledger requires one exact public RUNNING receipt';
    END IF;

    SELECT COUNT(*) INTO internal_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260723_skill_consume_credit_ledger_v2_042';
    IF internal_receipt_count NOT IN (0, 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Skill consume v2 ledger internal receipt cardinality has drifted';
    END IF;

    SELECT COUNT(*) INTO target_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_skill_credit_account_v2',
          'fbs_skill_credit_operation_v2',
          'fbs_skill_credit_entry_v2',
          'fbs_skill_credit_projection_bridge_v2'
      );
    SELECT COUNT(*) INTO target_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                                 'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    IF (internal_receipt_count = 0
            AND NOT ((target_table_count = 0 AND target_trigger_count = 0)
                     OR (target_table_count = 4 AND target_trigger_count IN (0, 8))))
       OR (internal_receipt_count = 1
            AND (target_table_count <> 4 OR target_trigger_count <> 8)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Skill consume v2 ledger partial or receipt-drifted state is not recoverable';
    END IF;

    IF target_table_count = 0 THEN
        SET migration_stage = 'first-apply-ddl';
        CREATE TABLE IF NOT EXISTS `fbs_skill_credit_account_v2` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `account_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `subject_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER',
            `user_id` BIGINT NOT NULL,
            `account_scope` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER_GLOBAL',
            `currency_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'FBS_POINTS',
            `opening_balance` BIGINT NOT NULL,
            `balance` BIGINT NOT NULL,
            `version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `last_entry_sequence` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `last_entry_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                DEFAULT '0000000000000000000000000000000000000000000000000000000000000000',
            `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_skill_credit_account_id` (`account_id`),
            UNIQUE KEY `uk_skill_credit_account_scope` (`subject_type`, `user_id`, `account_scope`, `currency_code`),
            UNIQUE KEY `uk_skill_credit_account_snapshot` (`account_id`, `user_id`, `account_scope`, `currency_code`),
            CONSTRAINT `fk_skill_credit_account_user`
                FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_skill_credit_account_identifier`
                CHECK (`account_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                    AND `user_id` > 0),
            CONSTRAINT `chk_skill_credit_account_scope`
                CHECK (`subject_type` = 'USER' AND `account_scope` = 'USER_GLOBAL'
                    AND `currency_code` = 'FBS_POINTS'),
            CONSTRAINT `chk_skill_credit_account_balance`
                CHECK (`opening_balance` BETWEEN 0 AND 2147483647
                    AND `balance` BETWEEN 0 AND 2147483647 AND `balance` <= `opening_balance`),
            CONSTRAINT `chk_skill_credit_account_chain`
                CHECK (`version` = `last_entry_sequence`
                    AND `last_entry_hash` REGEXP '^[0-9a-f]{64}$'
                    AND ((`version` = 0 AND `last_entry_hash` = REPEAT('0', 64)
                          AND `balance` = `opening_balance`)
                         OR (`version` > 0 AND `last_entry_hash` <> REPEAT('0', 64)))),
            CONSTRAINT `chk_skill_credit_account_status`
                CHECK (`status` = 'ACTIVE')
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

        CREATE TABLE IF NOT EXISTS `fbs_skill_credit_operation_v2` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `operation_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `usage_record_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `request_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `account_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `user_id` BIGINT NOT NULL,
            `account_scope` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `currency_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `operation_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SKILL_CONSUME',
            `reason_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SKILL_USE',
            `delta_amount` BIGINT NOT NULL,
            `balance_before` BIGINT NOT NULL,
            `balance_after` BIGINT NOT NULL,
            `issuer_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SERVICE',
            `issuer_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'FBS_SKILL_CONSUME_V1',
            `pack_id` BIGINT NOT NULL,
            `pack_version` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `skill_code` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `host_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `host_session_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_skill_credit_operation_id` (`operation_id`),
            UNIQUE KEY `uk_skill_credit_operation_usage` (`usage_record_id`),
            UNIQUE KEY `uk_skill_credit_operation_idempotency` (`idempotency_key`),
            UNIQUE KEY `uk_skill_credit_operation_account` (`operation_id`, `account_id`),
            KEY `idx_skill_credit_operation_account_history` (`account_id`, `created_at`, `id`),
            CONSTRAINT `fk_skill_credit_operation_account`
                FOREIGN KEY (`account_id`, `user_id`, `account_scope`, `currency_code`)
                REFERENCES `fbs_skill_credit_account_v2` (`account_id`, `user_id`, `account_scope`, `currency_code`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_skill_credit_operation_identifier`
                CHECK (`operation_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                    AND `usage_record_id` REGEXP '^[A-Za-z0-9._:-]{1,128}$'
                    AND `idempotency_key` REGEXP '^[A-Za-z0-9._:-]{1,128}$'
                    AND `request_digest` REGEXP '^[0-9a-f]{64}$'
                    AND `host_session_digest` REGEXP '^[0-9a-f]{64}$'),
            CONSTRAINT `chk_skill_credit_operation_scope`
                CHECK (`user_id` > 0 AND `account_scope` = 'USER_GLOBAL'
                    AND `currency_code` = 'FBS_POINTS' AND `pack_id` > 0),
            CONSTRAINT `chk_skill_credit_operation_type`
                CHECK (`operation_type` = 'SKILL_CONSUME' AND `reason_code` = 'SKILL_USE'),
            CONSTRAINT `chk_skill_credit_operation_issuer`
                CHECK (`issuer_type` = 'SERVICE' AND `issuer_id` = 'FBS_SKILL_CONSUME_V1'),
            CONSTRAINT `chk_skill_credit_operation_delta`
                CHECK (`delta_amount` < 0 AND `balance_before` >= 0
                    AND `balance_after` = `balance_before` + `delta_amount`
                    AND `balance_after` >= 0),
            CONSTRAINT `chk_skill_credit_operation_snapshot`
                CHECK (`pack_version` REGEXP '^[A-Za-z0-9._:-]{1,64}$'
                    AND `skill_code` REGEXP '^[A-Za-z0-9._:-]{1,128}$'
                    AND `host_type` REGEXP '^[A-Za-z0-9._:-]{1,32}$')
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

        CREATE TABLE IF NOT EXISTS `fbs_skill_credit_entry_v2` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `entry_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `operation_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `account_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `sequence_no` BIGINT UNSIGNED NOT NULL,
            `request_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `delta_amount` BIGINT NOT NULL,
            `balance_before` BIGINT NOT NULL,
            `balance_after` BIGINT NOT NULL,
            `previous_entry_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `entry_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `canonicalization_version` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'skill-credit-entry-v1',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_skill_credit_entry_id` (`entry_id`),
            UNIQUE KEY `uk_skill_credit_entry_operation` (`operation_id`),
            UNIQUE KEY `uk_skill_credit_entry_account_sequence` (`account_id`, `sequence_no`),
            KEY `idx_skill_credit_entry_operation_account` (`operation_id`, `account_id`),
            CONSTRAINT `fk_skill_credit_entry_operation`
                FOREIGN KEY (`operation_id`, `account_id`)
                REFERENCES `fbs_skill_credit_operation_v2` (`operation_id`, `account_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_skill_credit_entry_identifier`
                CHECK (`entry_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                    AND `sequence_no` > 0 AND `request_digest` REGEXP '^[0-9a-f]{64}$'),
            CONSTRAINT `chk_skill_credit_entry_amount`
                CHECK (`delta_amount` < 0 AND `balance_before` >= 0
                    AND `balance_after` = `balance_before` + `delta_amount`
                    AND `balance_after` >= 0),
            CONSTRAINT `chk_skill_credit_entry_hash`
                CHECK (`previous_entry_hash` REGEXP '^[0-9a-f]{64}$'
                    AND `entry_hash` REGEXP '^[0-9a-f]{64}$'
                    AND `entry_hash` <> `previous_entry_hash`
                    AND `canonicalization_version` = 'skill-credit-entry-v1')
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

        CREATE TABLE IF NOT EXISTS `fbs_skill_credit_projection_bridge_v2` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `account_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `user_id` BIGINT NOT NULL,
            `account_scope` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER_GLOBAL',
            `currency_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'FBS_POINTS',
            `projected_balance` BIGINT NOT NULL,
            `projection_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_skill_credit_projection_bridge_scope` (`account_id`, `user_id`, `account_scope`, `currency_code`),
            CONSTRAINT `fk_skill_credit_projection_bridge_account`
                FOREIGN KEY (`account_id`, `user_id`, `account_scope`, `currency_code`)
                REFERENCES `fbs_skill_credit_account_v2` (`account_id`, `user_id`, `account_scope`, `currency_code`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_skill_credit_projection_bridge_scope`
                CHECK (`user_id` > 0 AND `account_scope` = 'USER_GLOBAL'
                    AND `currency_code` = 'FBS_POINTS' AND `projected_balance` >= 0),
            CONSTRAINT `chk_skill_credit_projection_bridge_version`
                CHECK (`projection_version` >= 0)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    END IF;

    SET migration_stage = 'table-audit';
    SELECT COUNT(*) INTO target_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                         'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    SELECT COUNT(*) INTO target_base_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
      AND table_name IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                         'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    SELECT COUNT(*) INTO target_engine_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND engine = 'InnoDB'
      AND table_name IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                         'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    SELECT COUNT(*) INTO target_collation_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_collation = 'utf8mb4_unicode_ci'
      AND table_name IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                         'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    SELECT COUNT(*) INTO target_index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                         'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    SELECT COUNT(*) INTO target_foreign_key_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                         'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    SELECT COUNT(*) INTO target_check_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
      AND table_name IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                         'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    SELECT COUNT(*) INTO target_enforced_check_count
    FROM information_schema.table_constraints tc
    INNER JOIN information_schema.check_constraints cc
      ON cc.constraint_schema = tc.constraint_schema AND cc.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = DATABASE() AND tc.constraint_type = 'CHECK' AND tc.enforced = 'YES'
      AND tc.table_name IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                            'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    SELECT
      (SELECT SHA2(GROUP_CONCAT(CONCAT(
          'T:',HEX(CAST(table_name AS BINARY)),'|O:',LPAD(ordinal_position,3,'0'),
          '|N:',HEX(CAST(column_name AS BINARY)),'|Y:',HEX(CAST(column_type AS BINARY)),
          '|U:',HEX(CAST(is_nullable AS BINARY)),'|D:',IF(column_default IS NULL,'N',CONCAT('V:',HEX(CAST(column_default AS BINARY)))),
          '|C:',IF(character_set_name IS NULL,'N',CONCAT('V:',HEX(CAST(character_set_name AS BINARY)))),
          '|L:',IF(collation_name IS NULL,'N',CONCAT('V:',HEX(CAST(collation_name AS BINARY)))),
          '|E:',HEX(CAST(extra AS BINARY)),'|G:',IF(generation_expression IS NULL,'N',CONCAT('V:',HEX(CAST(generation_expression AS BINARY)))))
          ORDER BY table_name,ordinal_position SEPARATOR 0x0A),256)
       FROM information_schema.columns WHERE table_schema=DATABASE()
         AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
      (SELECT SHA2(GROUP_CONCAT(CONCAT(
          'T:',HEX(CAST(table_name AS BINARY)),'|I:',HEX(CAST(index_name AS BINARY)),'|U:',non_unique,
          '|Y:',HEX(CAST(index_type AS BINARY)),'|V:',HEX(CAST(is_visible AS BINARY)),'|S:',seq_in_index,
          '|N:',IF(column_name IS NULL,'N',CONCAT('V:',HEX(CAST(column_name AS BINARY)))),
          '|X:',IF(expression IS NULL,'N',CONCAT('V:',HEX(CAST(expression AS BINARY)))),
          '|C:',IF(collation IS NULL,'N',CONCAT('V:',HEX(CAST(collation AS BINARY)))),
          '|P:',IF(sub_part IS NULL,'N',CONCAT('V:',sub_part)),'|Q:',HEX(CAST(nullable AS BINARY)))
          ORDER BY table_name,index_name,seq_in_index SEPARATOR 0x0A),256)
       FROM information_schema.statistics WHERE table_schema=DATABASE()
         AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
      (SELECT SHA2(GROUP_CONCAT(CONCAT(
          'T:',HEX(CAST(rc.table_name AS BINARY)),'|C:',HEX(CAST(rc.constraint_name AS BINARY)),
          '|S:',IF(rc.unique_constraint_schema=DATABASE(),'SAME','OTHER'),'|K:',HEX(CAST(rc.unique_constraint_name AS BINARY)),
          '|R:',HEX(CAST(rc.referenced_table_name AS BINARY)),'|U:',HEX(CAST(rc.update_rule AS BINARY)),
          '|D:',HEX(CAST(rc.delete_rule AS BINARY)),'|M:',HEX(CAST(rc.match_option AS BINARY)),'|O:',kcu.ordinal_position,
          '|N:',HEX(CAST(kcu.column_name AS BINARY)),'|Q:',IF(kcu.referenced_table_schema=DATABASE(),'SAME','OTHER'),
          '|P:',HEX(CAST(kcu.referenced_column_name AS BINARY)),'|I:',IF(kcu.position_in_unique_constraint IS NULL,'N',CONCAT('V:',kcu.position_in_unique_constraint)))
          ORDER BY rc.table_name,rc.constraint_name,kcu.ordinal_position SEPARATOR 0x0A),256)
       FROM information_schema.referential_constraints rc
       INNER JOIN information_schema.key_column_usage kcu
         ON kcu.constraint_schema=rc.constraint_schema AND kcu.table_name=rc.table_name AND kcu.constraint_name=rc.constraint_name
       WHERE rc.constraint_schema=DATABASE() AND rc.unique_constraint_schema=DATABASE() AND kcu.referenced_table_schema=DATABASE()
         AND rc.table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
      (SELECT SHA2(GROUP_CONCAT(CONCAT(
          'T:',HEX(CAST(tc.table_name AS BINARY)),'|C:',HEX(CAST(tc.constraint_name AS BINARY)),
          '|E:',HEX(CAST(tc.enforced AS BINARY)),'|X:',HEX(CAST(cc.check_clause AS BINARY)))
          ORDER BY tc.table_name,tc.constraint_name SEPARATOR 0x0A),256)
       FROM information_schema.table_constraints tc
       INNER JOIN information_schema.check_constraints cc
         ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
       WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
         AND tc.table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2'))
    INTO target_column_metadata_digest, target_index_metadata_digest,
         target_foreign_key_metadata_digest, target_check_metadata_digest;
    IF target_table_count <> 4 OR target_base_table_count <> 4 OR target_engine_count <> 4
       OR target_collation_count <> 4 OR target_index_count < 16 OR target_foreign_key_count <> 4
       OR target_check_count <> 16 OR target_enforced_check_count <> 16
       OR target_column_metadata_digest <> 'b90f2665d993943fd6df22bcd88f8c1fe89594a1f73be985bcf1b4caba45f4e0'
       OR target_index_metadata_digest <> '72adb6082d425d913a1a235ccdc398ed5fc40a0fe7ba1aa0122bdc2f6a3a8d32'
       OR target_foreign_key_metadata_digest <> 'de942cb491f1b4dfc74035c5db0e6c515c184074e4b691ce6ec5c59b14d41c1d'
       OR target_check_metadata_digest <> '8e8eea4f21f1a262acc9384015e3be5a73dfd29abb175fa9c4686dfffb33ead8' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Skill consume v2 ledger exact current-read contract has drifted';
    END IF;

    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill consume v2 ledger lost migration lock';
    END IF;
    -- This session-level lock deliberately spans the top-level CREATE TRIGGER
    -- statements below. The finalizer verifies the same-session ownership and
    -- is the only successful path that releases it.
    SET SESSION group_concat_max_len = previous_group_concat_max_len;
END$$

DROP PROCEDURE IF EXISTS `u3w_finalize_skill_consume_credit_ledger_v2_20260723`$$
CREATE PROCEDURE `u3w_finalize_skill_consume_credit_ledger_v2_20260723`()
BEGIN
    DECLARE migration_lock_name CHAR(64) CHARACTER SET ascii COLLATE ascii_bin;
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE public_running_receipt_count INT DEFAULT 0;
    DECLARE internal_receipt_count INT DEFAULT 0;
    DECLARE target_table_count INT DEFAULT 0;
    DECLARE target_trigger_count INT DEFAULT 0;
    DECLARE target_trigger_contract_count INT DEFAULT 0;
    DECLARE target_column_metadata_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_index_metadata_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_foreign_key_metadata_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_check_metadata_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_internal_receipt_count INT DEFAULT 0;
    DECLARE target_version_receipt_count INT DEFAULT 0;
    DECLARE previous_group_concat_max_len BIGINT UNSIGNED DEFAULT 1024;
    DECLARE migration_stage VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        DEFAULT 'receipt';

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF migration_lock_acquired = 1 THEN
            SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
            IF migration_lock_owner = CONNECTION_ID() THEN
                DO RELEASE_LOCK(migration_lock_name);
            END IF;
        END IF;
        SET SESSION group_concat_max_len = previous_group_concat_max_len;
        RESIGNAL;
    END;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260723_skill_consume_credit_ledger_v2_042'), 256);
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Skill consume v2 ledger finalizer requires the migration session lock';
    END IF;
    SET migration_lock_acquired = 1;
    SET previous_group_concat_max_len = @@SESSION.group_concat_max_len;
    IF previous_group_concat_max_len < 1048576 THEN
        SET SESSION group_concat_max_len = 1048576;
    END IF;
    CALL `u3w_assert_skill_consume_credit_ledger_v2_triggers_20260723`();
    SELECT COUNT(*) INTO public_running_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = 'public_init_042'
      AND `description` = 'RUNNING:Independent Board default-off skill-consume v2 credit ledger';
    IF public_running_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill consume v2 ledger finalizer requires exact public RUNNING receipt';
    END IF;
    SELECT COUNT(*) INTO target_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                         'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    SELECT COUNT(*) INTO target_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN ('fbs_skill_credit_account_v2', 'fbs_skill_credit_operation_v2',
                                 'fbs_skill_credit_entry_v2', 'fbs_skill_credit_projection_bridge_v2');
    SELECT COUNT(*) INTO target_trigger_contract_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE() AND action_timing = 'BEFORE' AND action_orientation = 'ROW'
      AND trigger_name IN ('trg_skill_credit_account_v2_transition', 'trg_skill_credit_account_v2_no_delete',
                           'trg_skill_credit_operation_v2_no_update', 'trg_skill_credit_operation_v2_no_delete',
                           'trg_skill_credit_entry_v2_no_update', 'trg_skill_credit_entry_v2_no_delete',
                           'trg_skill_credit_projection_bridge_v2_transition',
                           'trg_skill_credit_projection_bridge_v2_no_delete');
    SELECT
      (SELECT SHA2(GROUP_CONCAT(CONCAT('T:',HEX(CAST(table_name AS BINARY)),'|O:',LPAD(ordinal_position,3,'0'),'|N:',HEX(CAST(column_name AS BINARY)),'|Y:',HEX(CAST(column_type AS BINARY)),'|U:',HEX(CAST(is_nullable AS BINARY)),'|D:',IF(column_default IS NULL,'N',CONCAT('V:',HEX(CAST(column_default AS BINARY)))),'|C:',IF(character_set_name IS NULL,'N',CONCAT('V:',HEX(CAST(character_set_name AS BINARY)))),'|L:',IF(collation_name IS NULL,'N',CONCAT('V:',HEX(CAST(collation_name AS BINARY)))),'|E:',HEX(CAST(extra AS BINARY)),'|G:',IF(generation_expression IS NULL,'N',CONCAT('V:',HEX(CAST(generation_expression AS BINARY))))) ORDER BY table_name,ordinal_position SEPARATOR 0x0A),256) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
      (SELECT SHA2(GROUP_CONCAT(CONCAT('T:',HEX(CAST(table_name AS BINARY)),'|I:',HEX(CAST(index_name AS BINARY)),'|U:',non_unique,'|Y:',HEX(CAST(index_type AS BINARY)),'|V:',HEX(CAST(is_visible AS BINARY)),'|S:',seq_in_index,'|N:',IF(column_name IS NULL,'N',CONCAT('V:',HEX(CAST(column_name AS BINARY)))),'|X:',IF(expression IS NULL,'N',CONCAT('V:',HEX(CAST(expression AS BINARY)))),'|C:',IF(collation IS NULL,'N',CONCAT('V:',HEX(CAST(collation AS BINARY)))),'|P:',IF(sub_part IS NULL,'N',CONCAT('V:',sub_part)),'|Q:',HEX(CAST(nullable AS BINARY))) ORDER BY table_name,index_name,seq_in_index SEPARATOR 0x0A),256) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
      (SELECT SHA2(GROUP_CONCAT(CONCAT('T:',HEX(CAST(rc.table_name AS BINARY)),'|C:',HEX(CAST(rc.constraint_name AS BINARY)),'|S:',IF(rc.unique_constraint_schema=DATABASE(),'SAME','OTHER'),'|K:',HEX(CAST(rc.unique_constraint_name AS BINARY)),'|R:',HEX(CAST(rc.referenced_table_name AS BINARY)),'|U:',HEX(CAST(rc.update_rule AS BINARY)),'|D:',HEX(CAST(rc.delete_rule AS BINARY)),'|M:',HEX(CAST(rc.match_option AS BINARY)),'|O:',kcu.ordinal_position,'|N:',HEX(CAST(kcu.column_name AS BINARY)),'|Q:',IF(kcu.referenced_table_schema=DATABASE(),'SAME','OTHER'),'|P:',HEX(CAST(kcu.referenced_column_name AS BINARY)),'|I:',IF(kcu.position_in_unique_constraint IS NULL,'N',CONCAT('V:',kcu.position_in_unique_constraint))) ORDER BY rc.table_name,rc.constraint_name,kcu.ordinal_position SEPARATOR 0x0A),256) FROM information_schema.referential_constraints rc INNER JOIN information_schema.key_column_usage kcu ON kcu.constraint_schema=rc.constraint_schema AND kcu.table_name=rc.table_name AND kcu.constraint_name=rc.constraint_name WHERE rc.constraint_schema=DATABASE() AND rc.unique_constraint_schema=DATABASE() AND kcu.referenced_table_schema=DATABASE() AND rc.table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
      (SELECT SHA2(GROUP_CONCAT(CONCAT('T:',HEX(CAST(tc.table_name AS BINARY)),'|C:',HEX(CAST(tc.constraint_name AS BINARY)),'|E:',HEX(CAST(tc.enforced AS BINARY)),'|X:',HEX(CAST(cc.check_clause AS BINARY))) ORDER BY tc.table_name,tc.constraint_name SEPARATOR 0x0A),256) FROM information_schema.table_constraints tc INNER JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK' AND tc.table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2'))
    INTO target_column_metadata_digest, target_index_metadata_digest,
         target_foreign_key_metadata_digest, target_check_metadata_digest;
    IF target_table_count <> 4 OR target_trigger_count <> 8 OR target_trigger_contract_count <> 8 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill consume v2 ledger finalizer trigger contract has drifted';
    END IF;
    IF target_column_metadata_digest <> 'b90f2665d993943fd6df22bcd88f8c1fe89594a1f73be985bcf1b4caba45f4e0'
       OR target_index_metadata_digest <> '72adb6082d425d913a1a235ccdc398ed5fc40a0fe7ba1aa0122bdc2f6a3a8d32'
       OR target_foreign_key_metadata_digest <> 'de942cb491f1b4dfc74035c5db0e6c515c184074e4b691ce6ec5c59b14d41c1d'
       OR target_check_metadata_digest <> '8e8eea4f21f1a262acc9384015e3be5a73dfd29abb175fa9c4686dfffb33ead8' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill consume v2 ledger finalizer raw metadata has drifted';
    END IF;
    SELECT COUNT(*) INTO internal_receipt_count
    FROM `u3w_schema_migration` WHERE `version` = '20260723_skill_consume_credit_ledger_v2_042';
    IF internal_receipt_count NOT IN (0, 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill consume v2 ledger internal receipt cardinality has drifted';
    END IF;
    SET migration_stage = 'receipt';
    START TRANSACTION;
    IF internal_receipt_count = 0 THEN
        INSERT INTO `u3w_schema_migration` (`version`, `description`) VALUES
            ('20260723_skill_consume_credit_ledger_v2_042',
             'Independent Board default-off skill-consume v2 credit ledger');
    END IF;
    SELECT COUNT(*) INTO target_internal_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260723_skill_consume_credit_ledger_v2_042'
      AND `description` = 'Independent Board default-off skill-consume v2 credit ledger';
    SELECT COUNT(*) INTO target_version_receipt_count
    FROM `u3w_schema_migration` WHERE `version` = '20260723_skill_consume_credit_ledger_v2_042';
    IF target_internal_receipt_count <> 1 OR target_version_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill consume v2 ledger internal receipt has drifted';
    END IF;
    COMMIT;
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill consume v2 ledger finalizer lost migration lock';
    END IF;
    SET SESSION group_concat_max_len = previous_group_concat_max_len;
    DO RELEASE_LOCK(migration_lock_name);
END$$

CALL `u3w_migrate_skill_consume_credit_ledger_v2_20260723`()$$

CREATE TRIGGER IF NOT EXISTS `trg_skill_credit_account_v2_transition`
BEFORE UPDATE ON `fbs_skill_credit_account_v2`
FOR EACH ROW
BEGIN
    IF NOT (OLD.account_id <=> NEW.account_id AND OLD.subject_type <=> NEW.subject_type
            AND OLD.user_id <=> NEW.user_id AND OLD.account_scope <=> NEW.account_scope
            AND OLD.currency_code <=> NEW.currency_code AND OLD.opening_balance <=> NEW.opening_balance
            AND OLD.status <=> NEW.status AND NEW.version = OLD.version + 1
            AND NEW.last_entry_sequence = OLD.last_entry_sequence + 1
            AND NEW.balance < OLD.balance AND NEW.last_entry_hash <> OLD.last_entry_hash) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill credit account v2 immutable transition rejected';
    END IF;
END$$
CREATE TRIGGER IF NOT EXISTS `trg_skill_credit_account_v2_no_delete`
BEFORE DELETE ON `fbs_skill_credit_account_v2`
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill credit account v2 delete rejected'$$
CREATE TRIGGER IF NOT EXISTS `trg_skill_credit_operation_v2_no_update`
BEFORE UPDATE ON `fbs_skill_credit_operation_v2`
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill credit operation v2 immutable'$$
CREATE TRIGGER IF NOT EXISTS `trg_skill_credit_operation_v2_no_delete`
BEFORE DELETE ON `fbs_skill_credit_operation_v2`
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill credit operation v2 delete rejected'$$
CREATE TRIGGER IF NOT EXISTS `trg_skill_credit_entry_v2_no_update`
BEFORE UPDATE ON `fbs_skill_credit_entry_v2`
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill credit entry v2 immutable'$$
CREATE TRIGGER IF NOT EXISTS `trg_skill_credit_entry_v2_no_delete`
BEFORE DELETE ON `fbs_skill_credit_entry_v2`
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill credit entry v2 delete rejected'$$
CREATE TRIGGER IF NOT EXISTS `trg_skill_credit_projection_bridge_v2_transition`
BEFORE UPDATE ON `fbs_skill_credit_projection_bridge_v2`
FOR EACH ROW
BEGIN
    IF NOT (OLD.account_id <=> NEW.account_id AND OLD.user_id <=> NEW.user_id
            AND OLD.account_scope <=> NEW.account_scope AND OLD.currency_code <=> NEW.currency_code
            AND NEW.projection_version = OLD.projection_version + 1
            AND NEW.projected_balance < OLD.projected_balance) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill credit projection v2 transition rejected';
    END IF;
END$$
CREATE TRIGGER IF NOT EXISTS `trg_skill_credit_projection_bridge_v2_no_delete`
BEFORE DELETE ON `fbs_skill_credit_projection_bridge_v2`
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Skill credit projection v2 delete rejected'$$

CALL `u3w_finalize_skill_consume_credit_ledger_v2_20260723`()$$
DROP PROCEDURE IF EXISTS `u3w_assert_skill_consume_credit_ledger_v2_triggers_20260723`$$
DROP PROCEDURE IF EXISTS `u3w_migrate_skill_consume_credit_ledger_v2_20260723`$$
DROP PROCEDURE IF EXISTS `u3w_finalize_skill_consume_credit_ledger_v2_20260723`$$

DELIMITER ;
