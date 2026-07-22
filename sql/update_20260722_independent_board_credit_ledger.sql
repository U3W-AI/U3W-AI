-- ============================================================
-- FBSir Independent Board USER_GLOBAL/FBS_POINTS shadow ledger
-- Migration: 20260722_independent_board_credit_ledger_v1
-- Public manifest step: public_init_038
-- Target: exact MySQL Community 8.0.30 or 8.4.8 raw-metadata profiles
-- Scope: default-off immutable administrator-adjustment shadow ledger
-- Precondition: public_init_028, u3w_schema_migration and sys_user exist.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_assert_independent_board_credit_triggers_20260722`$$
CREATE PROCEDURE `u3w_assert_independent_board_credit_triggers_20260722`()
BEGIN
    DECLARE target_trigger_count INT DEFAULT 0;
    DECLARE target_trigger_contract_count INT DEFAULT 0;

    SELECT COUNT(*) INTO target_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );

    SELECT COUNT(*) INTO target_trigger_contract_count
    FROM (
        SELECT trigger_name, event_object_table, event_manipulation,
               action_timing, action_orientation, action_condition, action_order,
               SHA2(CAST(action_statement AS BINARY), 256) AS action_sha256
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND event_object_table IN (
              'fbs_credit_account',
              'fbs_credit_operation',
              'fbs_credit_entry'
          )
    ) AS credit_triggers
    WHERE action_timing = 'BEFORE'
      AND action_orientation = 'ROW'
      AND action_condition IS NULL
      AND action_order = 1
      AND (
          (trigger_name = 'trg_credit_account_transition'
           AND event_object_table = 'fbs_credit_account'
           AND event_manipulation = 'UPDATE'
           AND CAST(action_sha256 AS BINARY) = CAST(
               'fe61351bc245be129eedf83daa790444022925b660ed3f2c0241f9ec15917fc4'
               AS BINARY))
       OR (trigger_name = 'trg_credit_account_no_delete'
           AND event_object_table = 'fbs_credit_account'
           AND event_manipulation = 'DELETE'
           AND CAST(action_sha256 AS BINARY) = CAST(
               '48497a4b694103753c63b9ab29337376bb2068c3e211c7e8c39fb28650381369'
               AS BINARY))
       OR (trigger_name = 'trg_credit_operation_no_update'
           AND event_object_table = 'fbs_credit_operation'
           AND event_manipulation = 'UPDATE'
           AND CAST(action_sha256 AS BINARY) = CAST(
               '4dc21272fe82c7e48cc4fbb2dabb0913903148af12c5661e63082952797bb1d0'
               AS BINARY))
       OR (trigger_name = 'trg_credit_operation_no_delete'
           AND event_object_table = 'fbs_credit_operation'
           AND event_manipulation = 'DELETE'
           AND CAST(action_sha256 AS BINARY) = CAST(
               '4dc21272fe82c7e48cc4fbb2dabb0913903148af12c5661e63082952797bb1d0'
               AS BINARY))
       OR (trigger_name = 'trg_credit_entry_no_update'
           AND event_object_table = 'fbs_credit_entry'
           AND event_manipulation = 'UPDATE'
           AND CAST(action_sha256 AS BINARY) = CAST(
               '29f2ebca36c354a73509468e251b4edb61f096056d3ded28d21d16b76e7b34dc'
               AS BINARY))
       OR (trigger_name = 'trg_credit_entry_no_delete'
           AND event_object_table = 'fbs_credit_entry'
           AND event_manipulation = 'DELETE'
           AND CAST(action_sha256 AS BINARY) = CAST(
               '29f2ebca36c354a73509468e251b4edb61f096056d3ded28d21d16b76e7b34dc'
               AS BINARY))
      );

    IF target_trigger_count <> 6 OR target_trigger_contract_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger exact six-trigger contract has drifted';
    END IF;
END$$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_credit_ledger_20260722`$$
CREATE PROCEDURE `u3w_migrate_independent_board_credit_ledger_20260722`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE current_connection BIGINT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE prerequisite_table_count INT DEFAULT 0;
    DECLARE sys_user_column_contract_count INT DEFAULT 0;
    DECLARE target_table_count INT DEFAULT 0;
    DECLARE target_base_table_count INT DEFAULT 0;
    DECLARE target_engine_count INT DEFAULT 0;
    DECLARE target_collation_count INT DEFAULT 0;
    DECLARE target_column_count INT DEFAULT 0;
    DECLARE target_total_column_count INT DEFAULT 0;
    DECLARE target_column_contract_count INT DEFAULT 0;
    DECLARE target_column_metadata_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        DEFAULT NULL;
    DECLARE target_index_count INT DEFAULT 0;
    DECLARE target_index_contract_count INT DEFAULT 0;
    DECLARE target_index_metadata_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        DEFAULT NULL;
    DECLARE target_foreign_key_count INT DEFAULT 0;
    DECLARE target_foreign_key_contract_count INT DEFAULT 0;
    DECLARE target_foreign_key_column_count INT DEFAULT 0;
    DECLARE target_foreign_key_total_column_count INT DEFAULT 0;
    DECLARE target_foreign_key_metadata_digest CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_check_count INT DEFAULT 0;
    DECLARE target_enforced_check_count INT DEFAULT 0;
    DECLARE target_check_name_count INT DEFAULT 0;
    DECLARE target_check_contract_count INT DEFAULT 0;
    DECLARE target_trigger_presence_count INT DEFAULT 0;
    DECLARE target_internal_receipt_count INT DEFAULT 0;
    DECLARE target_version_receipt_count INT DEFAULT 0;
    DECLARE previous_group_concat_max_len BIGINT UNSIGNED DEFAULT 1024;
    DECLARE diagnostic_errno INT DEFAULT 0;
    DECLARE migration_stage VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        DEFAULT 'preflight';

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 diagnostic_errno = MYSQL_ERRNO;
        ROLLBACK;
        IF migration_lock_acquired = 1 THEN
            SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
            IF migration_lock_owner = CONNECTION_ID() THEN
                DO RELEASE_LOCK(migration_lock_name);
            END IF;
        END IF;
        SET SESSION group_concat_max_len = previous_group_concat_max_len;
        IF diagnostic_errno = 1267 THEN
            RESIGNAL SET MESSAGE_TEXT = migration_stage;
        END IF;
        RESIGNAL;
    END;

    SET previous_group_concat_max_len = @@SESSION.group_concat_max_len;
    IF previous_group_concat_max_len < 1048576 THEN
        SET SESSION group_concat_max_len = 1048576;
    END IF;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger migration requires an explicit target database';
    END IF;

    IF VERSION() NOT IN ('8.0.30', '8.4.8')
       OR @@version_comment <> 'MySQL Community Server - GPL' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger migration requires exact MySQL Community 8.0.30 or 8.4.8';
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260722_independent_board_credit_ledger_v1'),
        256
    );
    IF CHAR_LENGTH(migration_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger lock name must be a stable 64-character digest';
    END IF;
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the Credit ledger migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name), CONNECTION_ID()
      INTO migration_lock_owner, current_connection;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger migration lock is not owned by this connection';
    END IF;

    SET migration_stage = 'prerequisite-audit';
    SELECT COUNT(*) INTO prerequisite_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_type = 'BASE TABLE'
      AND table_name IN ('u3w_schema_migration', 'sys_user');
    IF prerequisite_table_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger migration prerequisites are missing';
    END IF;

    SELECT COUNT(*) INTO sys_user_column_contract_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND ((column_name = 'user_id'
            AND data_type = 'bigint'
            AND column_type NOT LIKE '%unsigned%'
            AND is_nullable = 'NO'
            AND column_key = 'PRI')
        OR (column_name = 'points'
            AND data_type = 'int'
            AND column_type NOT LIKE '%unsigned%'
            AND extra NOT LIKE '%GENERATED%')
        OR (column_name = 'status'
            AND column_type = 'char(1)')
        OR (column_name = 'del_flag'
            AND column_type = 'char(1)')
        OR (column_name = 'update_time'
            AND data_type = 'datetime'
            AND extra NOT LIKE '%GENERATED%'));
    IF sys_user_column_contract_count <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger requires the five-column sys_user runtime projection';
    END IF;

    SET migration_stage = 'partial-state-audit';
    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_credit_ledger_v1';
    IF migration_exists NOT IN (0, 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger migration receipt cardinality has drifted';
    END IF;

    SELECT COUNT(*) INTO target_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    SELECT COUNT(*) INTO target_trigger_presence_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND (event_object_table IN (
              'fbs_credit_account',
              'fbs_credit_operation',
              'fbs_credit_entry'
          )
        OR trigger_name IN (
              'trg_credit_account_transition',
              'trg_credit_account_no_delete',
              'trg_credit_operation_no_update',
              'trg_credit_operation_no_delete',
              'trg_credit_entry_no_update',
              'trg_credit_entry_no_delete'
          ));
    IF migration_exists = 0
       AND (target_table_count <> 0 OR target_trigger_presence_count <> 0) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger objects exist without the exact migration receipt';
    END IF;
    IF migration_exists = 1 AND target_table_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger receipt exists but its three-table set is incomplete';
    END IF;

    IF migration_exists = 0 THEN
        SET migration_stage = 'first-apply-ddl';

        CREATE TABLE IF NOT EXISTS `fbs_credit_account` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
            `account_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'server-generated UUID',
            `subject_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER',
            `user_id` BIGINT NOT NULL COMMENT 'sys_user subject',
            `account_scope` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER_GLOBAL',
            `currency_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'FBS_POINTS',
            `opening_balance` BIGINT NOT NULL COMMENT 'locked sys_user.points at first open',
            `balance` BIGINT NOT NULL COMMENT 'shadow-ledger current balance',
            `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'contiguous CAS version',
            `last_entry_sequence` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'contiguous chain sequence',
            `last_entry_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                DEFAULT '0000000000000000000000000000000000000000000000000000000000000000',
            `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_credit_account_id` (`account_id`),
            UNIQUE KEY `uk_credit_account_scope` (`subject_type`, `user_id`, `account_scope`, `currency_code`),
            UNIQUE KEY `uk_credit_account_snapshot` (`account_id`, `user_id`, `account_scope`, `currency_code`),
            KEY `idx_credit_account_user` (`user_id`, `account_scope`, `currency_code`),
            CONSTRAINT `fk_credit_account_user`
                FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_credit_account_identifier`
                CHECK (`account_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                    AND `user_id` > 0),
            CONSTRAINT `chk_credit_account_subject`
                CHECK (`subject_type` = 'USER'),
            CONSTRAINT `chk_credit_account_scope`
                CHECK (`account_scope` = 'USER_GLOBAL'),
            CONSTRAINT `chk_credit_account_currency`
                CHECK (`currency_code` = 'FBS_POINTS'),
            CONSTRAINT `chk_credit_account_balance`
                CHECK (`opening_balance` BETWEEN 0 AND 2147483647
                    AND `balance` BETWEEN 0 AND 2147483647),
            CONSTRAINT `chk_credit_account_version_chain`
                CHECK (`version` = `last_entry_sequence`
                    AND `last_entry_hash` REGEXP '^[0-9a-f]{64}$'),
            CONSTRAINT `chk_credit_account_genesis`
                CHECK ((`version` = 0
                        AND `last_entry_sequence` = 0
                        AND `balance` = `opening_balance`
                        AND `last_entry_hash` = REPEAT('0', 64))
                    OR (`version` > 0
                        AND `last_entry_sequence` > 0
                        AND `last_entry_hash` <> REPEAT('0', 64))),
            CONSTRAINT `chk_credit_account_status`
                CHECK (`status` = 'ACTIVE')
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Independent Board USER_GLOBAL FBS_POINTS shadow-ledger account';

        CREATE TABLE IF NOT EXISTS `fbs_credit_operation` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
            `operation_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'server-generated UUID',
            `idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `request_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'canonical credit-command-v1 SHA-256',
            `account_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `user_id` BIGINT NOT NULL COMMENT 'subject snapshot',
            `account_scope` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `currency_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `operation_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `delta_amount` BIGINT NOT NULL,
            `reason_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `reason_note` VARCHAR(128) NOT NULL COMMENT 'bounded administrator audit note',
            `actor_user_id` BIGINT NOT NULL COMMENT 'JWT actor snapshot',
            `reversal_of_operation_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
            `balance_before` BIGINT NOT NULL,
            `balance_after` BIGINT NOT NULL,
            `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'COMMITTED',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_credit_operation_id` (`operation_id`),
            UNIQUE KEY `uk_credit_operation_idempotency` (`idempotency_key`),
            UNIQUE KEY `uk_credit_operation_reversal` (`reversal_of_operation_id`),
            UNIQUE KEY `uk_credit_operation_account` (`operation_id`, `account_id`),
            KEY `idx_credit_operation_account_snapshot` (`account_id`, `user_id`, `account_scope`, `currency_code`),
            KEY `idx_credit_operation_reversal_account` (`reversal_of_operation_id`, `account_id`),
            KEY `idx_credit_operation_account_history` (`account_id`, `created_at`, `id`),
            KEY `idx_credit_operation_user_history` (`user_id`, `created_at`, `id`),
            CONSTRAINT `fk_credit_operation_account`
                FOREIGN KEY (`account_id`, `user_id`, `account_scope`, `currency_code`)
                REFERENCES `fbs_credit_account`
                    (`account_id`, `user_id`, `account_scope`, `currency_code`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `fk_credit_operation_reversal`
                FOREIGN KEY (`reversal_of_operation_id`, `account_id`)
                REFERENCES `fbs_credit_operation` (`operation_id`, `account_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_credit_operation_identifiers`
                CHECK (`operation_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                    AND `idempotency_key` REGEXP '^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$'
                    AND `request_digest` REGEXP '^[0-9a-f]{64}$'),
            CONSTRAINT `chk_credit_operation_scope`
                CHECK (`account_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                    AND `user_id` > 0
                    AND `account_scope` = 'USER_GLOBAL'
                    AND `currency_code` = 'FBS_POINTS'),
            CONSTRAINT `chk_credit_operation_type`
                CHECK (`operation_type` IN ('GRANT','REVERSAL')),
            CONSTRAINT `chk_credit_operation_reason`
                CHECK (((`operation_type` = 'GRANT'
                        AND `reason_code` IN ('CUSTOMER_SUPPORT','SERVICE_RECOVERY','MIGRATION_CORRECTION'))
                    OR (`operation_type` = 'REVERSAL'
                        AND `reason_code` IN ('DUPLICATE_GRANT','OPERATOR_ERROR','POLICY_VIOLATION')))
                    AND CHAR_LENGTH(`reason_note`) BETWEEN 8 AND 128
                    AND `reason_note` = TRIM(`reason_note`)
                    AND `reason_note` NOT REGEXP '[[:cntrl:]]'),
            CONSTRAINT `chk_credit_operation_delta_reversal`
                CHECK ((`operation_type` = 'GRANT'
                        AND `delta_amount` BETWEEN 1 AND 100000
                        AND `reversal_of_operation_id` IS NULL)
                    OR (`operation_type` = 'REVERSAL'
                        AND `delta_amount` BETWEEN -100000 AND -1
                        AND `reversal_of_operation_id` IS NOT NULL)),
            CONSTRAINT `chk_credit_operation_balance`
                CHECK (`balance_before` BETWEEN 0 AND 2147483647
                    AND `balance_after` BETWEEN 0 AND 2147483647
                    AND `balance_after` = `balance_before` + `delta_amount`),
            CONSTRAINT `chk_credit_operation_actor`
                CHECK (`actor_user_id` > 0),
            CONSTRAINT `chk_credit_operation_status`
                CHECK (`status` = 'COMMITTED')
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Immutable Independent Board credit operation receipt';

        CREATE TABLE IF NOT EXISTS `fbs_credit_entry` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
            `entry_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'server-generated UUID',
            `operation_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `request_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `account_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `sequence_no` BIGINT UNSIGNED NOT NULL,
            `delta_amount` BIGINT NOT NULL,
            `balance_before` BIGINT NOT NULL,
            `balance_after` BIGINT NOT NULL,
            `previous_entry_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `entry_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `canonicalization_version` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'credit-entry-v1',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_credit_entry_id` (`entry_id`),
            UNIQUE KEY `uk_credit_entry_operation` (`operation_id`),
            UNIQUE KEY `uk_credit_entry_account_sequence` (`account_id`, `sequence_no`),
            KEY `idx_credit_entry_operation_account` (`operation_id`, `account_id`),
            KEY `idx_credit_entry_account_history` (`account_id`, `created_at`, `id`),
            CONSTRAINT `fk_credit_entry_operation`
                FOREIGN KEY (`operation_id`, `account_id`)
                REFERENCES `fbs_credit_operation` (`operation_id`, `account_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_credit_entry_identifiers`
                CHECK (`entry_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                    AND `operation_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                    AND `account_id` REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                    AND `request_digest` REGEXP '^[0-9a-f]{64}$'),
            CONSTRAINT `chk_credit_entry_sequence`
                CHECK (`sequence_no` > 0),
            CONSTRAINT `chk_credit_entry_delta`
                CHECK (`delta_amount` BETWEEN -100000 AND 100000
                    AND `delta_amount` <> 0),
            CONSTRAINT `chk_credit_entry_balance`
                CHECK (`balance_before` BETWEEN 0 AND 2147483647
                    AND `balance_after` BETWEEN 0 AND 2147483647
                    AND `balance_after` = `balance_before` + `delta_amount`),
            CONSTRAINT `chk_credit_entry_hashes`
                CHECK (`previous_entry_hash` REGEXP '^[0-9a-f]{64}$'
                    AND `entry_hash` REGEXP '^[0-9a-f]{64}$'),
            CONSTRAINT `chk_credit_entry_canonicalization`
                CHECK (`canonicalization_version` = 'credit-entry-v1')
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Immutable hash-linked Independent Board credit entry';
    END IF;

    SET migration_stage = 'table-audit';
    SELECT COUNT(*) INTO target_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    SELECT COUNT(*) INTO target_base_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_type = 'BASE TABLE'
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    SELECT COUNT(*) INTO target_engine_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND engine = 'InnoDB'
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    SELECT COUNT(*) INTO target_collation_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_collation = 'utf8mb4_unicode_ci'
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    IF target_table_count <> 3 OR target_base_table_count <> 3
       OR target_engine_count <> 3 OR target_collation_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger three-table engine or collation contract has drifted';
    END IF;

    SET migration_stage = 'column-contract-audit';
    SELECT COUNT(*) INTO target_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_credit_account' AND column_name IN (
              'id','account_id','subject_type','user_id','account_scope','currency_code',
              'opening_balance','balance','version','last_entry_sequence','last_entry_hash',
              'status','created_at','updated_at'
          ))
        OR (table_name = 'fbs_credit_operation' AND column_name IN (
              'id','operation_id','idempotency_key','request_digest','account_id','user_id',
              'account_scope','currency_code','operation_type','delta_amount','reason_code',
              'reason_note','actor_user_id','reversal_of_operation_id','balance_before',
              'balance_after','status','created_at'
          ))
        OR (table_name = 'fbs_credit_entry' AND column_name IN (
              'id','entry_id','operation_id','request_digest','account_id','sequence_no',
              'delta_amount','balance_before','balance_after','previous_entry_hash','entry_hash',
              'canonicalization_version','created_at'
          )));
    SELECT COUNT(*) INTO target_total_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    IF target_column_count <> 45 OR target_total_column_count <> 45 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger exact 45-column contract has drifted';
    END IF;

    SELECT COUNT(*) INTO target_column_contract_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND (
          (table_name = 'fbs_credit_account' AND (
              (column_name = 'id' AND column_type = 'bigint unsigned'
                  AND is_nullable = 'NO' AND extra LIKE '%auto_increment%')
           OR (column_name = 'account_id' AND column_type = 'varchar(36)'
                  AND is_nullable = 'NO' AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin')
           OR (column_name = 'subject_type' AND column_type = 'varchar(16)'
                  AND is_nullable = 'NO' AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin'
                  AND CAST(column_default AS BINARY) = CAST('USER' AS BINARY))
           OR (column_name = 'user_id' AND column_type = 'bigint' AND is_nullable = 'NO')
           OR (column_name IN ('account_scope','currency_code')
                  AND column_type = 'varchar(32)' AND is_nullable = 'NO'
                  AND character_set_name = 'ascii' AND collation_name = 'ascii_bin')
           OR (column_name IN ('opening_balance','balance')
                  AND column_type = 'bigint' AND is_nullable = 'NO')
           OR (column_name IN ('version','last_entry_sequence')
                  AND column_type = 'bigint unsigned' AND is_nullable = 'NO'
                  AND column_default = '0')
           OR (column_name = 'last_entry_hash' AND column_type = 'char(64)'
                  AND is_nullable = 'NO' AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin'
                  AND column_default = REPEAT('0', 64))
           OR (column_name = 'status' AND column_type = 'varchar(16)'
                  AND is_nullable = 'NO' AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin'
                  AND CAST(column_default AS BINARY) = CAST('ACTIVE' AS BINARY))
           OR (column_name IN ('created_at','updated_at')
                  AND data_type = 'datetime' AND datetime_precision = 3
                  AND is_nullable = 'NO')))
       OR (table_name = 'fbs_credit_operation' AND (
              (column_name = 'id' AND column_type = 'bigint unsigned'
                  AND is_nullable = 'NO' AND extra LIKE '%auto_increment%')
           OR (column_name IN ('operation_id','account_id')
                  AND column_type = 'varchar(36)' AND is_nullable = 'NO'
                  AND character_set_name = 'ascii' AND collation_name = 'ascii_bin')
           OR (column_name = 'idempotency_key' AND column_type = 'varchar(128)'
                  AND is_nullable = 'NO' AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin')
           OR (column_name = 'request_digest' AND column_type = 'char(64)'
                  AND is_nullable = 'NO' AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin')
           OR (column_name IN ('user_id','delta_amount','actor_user_id','balance_before','balance_after')
                  AND column_type = 'bigint' AND is_nullable = 'NO')
           OR (column_name IN ('account_scope','currency_code','reason_code')
                  AND column_type = 'varchar(32)' AND is_nullable = 'NO'
                  AND character_set_name = 'ascii' AND collation_name = 'ascii_bin')
           OR (column_name = 'operation_type' AND column_type = 'varchar(16)'
                  AND is_nullable = 'NO' AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin')
           OR (column_name = 'reason_note' AND column_type = 'varchar(128)'
                  AND is_nullable = 'NO' AND character_set_name = 'utf8mb4'
                  AND collation_name = 'utf8mb4_unicode_ci')
           OR (column_name = 'reversal_of_operation_id' AND column_type = 'varchar(36)'
                  AND is_nullable = 'YES' AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin')
           OR (column_name = 'status' AND column_type = 'varchar(16)'
                  AND is_nullable = 'NO' AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin'
                  AND CAST(column_default AS BINARY) = CAST('COMMITTED' AS BINARY))
           OR (column_name = 'created_at' AND data_type = 'datetime'
                  AND datetime_precision = 3 AND is_nullable = 'NO')))
       OR (table_name = 'fbs_credit_entry' AND (
              (column_name = 'id' AND column_type = 'bigint unsigned'
                  AND is_nullable = 'NO' AND extra LIKE '%auto_increment%')
           OR (column_name IN ('entry_id','operation_id','account_id')
                  AND column_type = 'varchar(36)' AND is_nullable = 'NO'
                  AND character_set_name = 'ascii' AND collation_name = 'ascii_bin')
           OR (column_name IN ('request_digest','previous_entry_hash','entry_hash')
                  AND column_type = 'char(64)' AND is_nullable = 'NO'
                  AND character_set_name = 'ascii' AND collation_name = 'ascii_bin')
           OR (column_name = 'sequence_no' AND column_type = 'bigint unsigned'
                  AND is_nullable = 'NO')
           OR (column_name IN ('delta_amount','balance_before','balance_after')
                  AND column_type = 'bigint' AND is_nullable = 'NO')
           OR (column_name = 'canonicalization_version' AND column_type = 'varchar(32)'
                  AND is_nullable = 'NO' AND character_set_name = 'ascii'
                  AND collation_name = 'ascii_bin'
                  AND CAST(column_default AS BINARY) = CAST('credit-entry-v1' AS BINARY))
           OR (column_name = 'created_at' AND data_type = 'datetime'
                  AND datetime_precision = 3 AND is_nullable = 'NO')))
      );
    IF target_column_contract_count <> 45 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger exact column type/default contract has drifted';
    END IF;

    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(table_name AS BINARY)),
               '|O:', LPAD(ordinal_position, 3, '0'),
               '|N:', HEX(CAST(column_name AS BINARY)),
               '|Y:', HEX(CAST(column_type AS BINARY)),
               '|U:', HEX(CAST(is_nullable AS BINARY)),
               '|D:', IF(column_default IS NULL, 'N',
                   CONCAT('V:', HEX(CAST(column_default AS BINARY)))),
               '|C:', IF(character_set_name IS NULL, 'N',
                   CONCAT('V:', HEX(CAST(character_set_name AS BINARY)))),
               '|L:', IF(collation_name IS NULL, 'N',
                   CONCAT('V:', HEX(CAST(collation_name AS BINARY)))),
               '|E:', HEX(CAST(extra AS BINARY)),
               '|G:', IF(generation_expression IS NULL, 'N',
                   CONCAT('V:', HEX(CAST(generation_expression AS BINARY)))))
               ORDER BY table_name, ordinal_position SEPARATOR 0x0A), 256)
      INTO target_column_metadata_digest
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    IF target_column_metadata_digest IS NULL
       OR CAST(target_column_metadata_digest AS BINARY) <>
          CAST('4717b6466040c2b33513ef1fb92ccb9044e08a00fea41edd7ba617192c9c8d00'
               AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger exact raw column metadata contract has drifted';
    END IF;

    SET migration_stage = 'index-contract-audit';
    SELECT COUNT(*) INTO target_index_count
    FROM (
        SELECT table_name, index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_credit_account',
              'fbs_credit_operation',
              'fbs_credit_entry'
          )
        GROUP BY table_name, index_name
    ) AS credit_indexes;
    SELECT COUNT(*) INTO target_index_contract_count
    FROM (
        SELECT table_name, index_name, MIN(non_unique) AS non_unique,
               MIN(index_type) AS index_type,
               COUNT(DISTINCT index_type) AS index_type_count,
               SUM(CASE WHEN sub_part IS NOT NULL THEN 1 ELSE 0 END) AS prefix_part_count,
               SUM(CASE WHEN expression IS NOT NULL THEN 1 ELSE 0 END) AS expression_part_count,
               SUM(CASE WHEN is_visible <> 'YES' THEN 1 ELSE 0 END) AS invisible_part_count,
               SUM(CASE WHEN collation IS NULL OR collation <> 'A' THEN 1 ELSE 0 END)
                   AS non_ascending_part_count,
               GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS column_signature
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_credit_account',
              'fbs_credit_operation',
              'fbs_credit_entry'
          )
        GROUP BY table_name, index_name
    ) AS credit_indexes
    WHERE index_type = 'BTREE'
      AND index_type_count = 1
      AND prefix_part_count = 0
      AND expression_part_count = 0
      AND invisible_part_count = 0
      AND non_ascending_part_count = 0
      AND (
          (table_name = 'fbs_credit_account' AND index_name = 'PRIMARY'
              AND non_unique = 0 AND column_signature = 'id')
       OR (table_name = 'fbs_credit_account' AND index_name = 'uk_credit_account_id'
              AND non_unique = 0 AND column_signature = 'account_id')
       OR (table_name = 'fbs_credit_account' AND index_name = 'uk_credit_account_scope'
              AND non_unique = 0
              AND column_signature = 'subject_type,user_id,account_scope,currency_code')
       OR (table_name = 'fbs_credit_account' AND index_name = 'uk_credit_account_snapshot'
              AND non_unique = 0
              AND column_signature = 'account_id,user_id,account_scope,currency_code')
       OR (table_name = 'fbs_credit_account' AND index_name = 'idx_credit_account_user'
              AND non_unique = 1 AND column_signature = 'user_id,account_scope,currency_code')
       OR (table_name = 'fbs_credit_operation' AND index_name = 'PRIMARY'
              AND non_unique = 0 AND column_signature = 'id')
       OR (table_name = 'fbs_credit_operation' AND index_name = 'uk_credit_operation_id'
              AND non_unique = 0 AND column_signature = 'operation_id')
       OR (table_name = 'fbs_credit_operation' AND index_name = 'uk_credit_operation_idempotency'
              AND non_unique = 0 AND column_signature = 'idempotency_key')
       OR (table_name = 'fbs_credit_operation' AND index_name = 'uk_credit_operation_reversal'
              AND non_unique = 0 AND column_signature = 'reversal_of_operation_id')
       OR (table_name = 'fbs_credit_operation' AND index_name = 'uk_credit_operation_account'
              AND non_unique = 0 AND column_signature = 'operation_id,account_id')
       OR (table_name = 'fbs_credit_operation' AND index_name = 'idx_credit_operation_account_snapshot'
              AND non_unique = 1
              AND column_signature = 'account_id,user_id,account_scope,currency_code')
       OR (table_name = 'fbs_credit_operation' AND index_name = 'idx_credit_operation_reversal_account'
              AND non_unique = 1
              AND column_signature = 'reversal_of_operation_id,account_id')
       OR (table_name = 'fbs_credit_operation' AND index_name = 'idx_credit_operation_account_history'
              AND non_unique = 1 AND column_signature = 'account_id,created_at,id')
       OR (table_name = 'fbs_credit_operation' AND index_name = 'idx_credit_operation_user_history'
              AND non_unique = 1 AND column_signature = 'user_id,created_at,id')
       OR (table_name = 'fbs_credit_entry' AND index_name = 'PRIMARY'
              AND non_unique = 0 AND column_signature = 'id')
       OR (table_name = 'fbs_credit_entry' AND index_name = 'uk_credit_entry_id'
              AND non_unique = 0 AND column_signature = 'entry_id')
       OR (table_name = 'fbs_credit_entry' AND index_name = 'uk_credit_entry_operation'
              AND non_unique = 0 AND column_signature = 'operation_id')
       OR (table_name = 'fbs_credit_entry' AND index_name = 'uk_credit_entry_account_sequence'
              AND non_unique = 0 AND column_signature = 'account_id,sequence_no')
       OR (table_name = 'fbs_credit_entry' AND index_name = 'idx_credit_entry_operation_account'
              AND non_unique = 1 AND column_signature = 'operation_id,account_id')
       OR (table_name = 'fbs_credit_entry' AND index_name = 'idx_credit_entry_account_history'
              AND non_unique = 1 AND column_signature = 'account_id,created_at,id')
      );
    IF target_index_count <> 20 OR target_index_contract_count <> 20 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger exact 20-index contract has drifted';
    END IF;

    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(table_name AS BINARY)),
               '|I:', HEX(CAST(index_name AS BINARY)),
               '|U:', non_unique,
               '|Y:', HEX(CAST(index_type AS BINARY)),
               '|V:', HEX(CAST(is_visible AS BINARY)),
               '|S:', seq_in_index,
               '|N:', IF(column_name IS NULL, 'N',
                   CONCAT('V:', HEX(CAST(column_name AS BINARY)))),
               '|X:', IF(expression IS NULL, 'N',
                   CONCAT('V:', HEX(CAST(expression AS BINARY)))),
               '|C:', IF(collation IS NULL, 'N',
                   CONCAT('V:', HEX(CAST(collation AS BINARY)))),
               '|P:', IF(sub_part IS NULL, 'N', CONCAT('V:', sub_part)),
               '|Q:', HEX(CAST(nullable AS BINARY)))
               ORDER BY table_name, index_name, seq_in_index SEPARATOR 0x0A), 256)
      INTO target_index_metadata_digest
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    IF target_index_metadata_digest IS NULL
       OR CAST(target_index_metadata_digest AS BINARY) <>
          CAST('3975985e059c133c0e91ef274702e5d270e4392b7b55b724b1d0d031b76f23cf'
               AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger exact raw index metadata contract has drifted';
    END IF;

    SET migration_stage = 'foreign-key-contract-audit';
    SELECT COUNT(*) INTO target_foreign_key_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    SELECT COUNT(*) INTO target_foreign_key_contract_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND unique_constraint_schema = DATABASE()
      AND update_rule = 'RESTRICT'
      AND delete_rule = 'RESTRICT'
      AND ((table_name = 'fbs_credit_account'
            AND constraint_name = 'fk_credit_account_user'
            AND referenced_table_name = 'sys_user')
        OR (table_name = 'fbs_credit_operation'
            AND constraint_name = 'fk_credit_operation_account'
            AND referenced_table_name = 'fbs_credit_account')
        OR (table_name = 'fbs_credit_operation'
            AND constraint_name = 'fk_credit_operation_reversal'
            AND referenced_table_name = 'fbs_credit_operation')
        OR (table_name = 'fbs_credit_entry'
            AND constraint_name = 'fk_credit_entry_operation'
            AND referenced_table_name = 'fbs_credit_operation'));
    SELECT COUNT(*) INTO target_foreign_key_column_count
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND referenced_table_schema = DATABASE()
      AND referenced_table_name IS NOT NULL
      AND ((table_name = 'fbs_credit_account'
            AND constraint_name = 'fk_credit_account_user'
            AND ordinal_position = 1 AND column_name = 'user_id'
            AND referenced_table_name = 'sys_user' AND referenced_column_name = 'user_id')
        OR (table_name = 'fbs_credit_operation'
            AND constraint_name = 'fk_credit_operation_account'
            AND referenced_table_name = 'fbs_credit_account'
            AND ((ordinal_position = 1 AND column_name = 'account_id' AND referenced_column_name = 'account_id')
              OR (ordinal_position = 2 AND column_name = 'user_id' AND referenced_column_name = 'user_id')
              OR (ordinal_position = 3 AND column_name = 'account_scope' AND referenced_column_name = 'account_scope')
              OR (ordinal_position = 4 AND column_name = 'currency_code' AND referenced_column_name = 'currency_code')))
        OR (table_name = 'fbs_credit_operation'
            AND constraint_name = 'fk_credit_operation_reversal'
            AND referenced_table_name = 'fbs_credit_operation'
            AND ((ordinal_position = 1 AND column_name = 'reversal_of_operation_id' AND referenced_column_name = 'operation_id')
              OR (ordinal_position = 2 AND column_name = 'account_id' AND referenced_column_name = 'account_id')))
        OR (table_name = 'fbs_credit_entry'
            AND constraint_name = 'fk_credit_entry_operation'
            AND referenced_table_name = 'fbs_credit_operation'
            AND ((ordinal_position = 1 AND column_name = 'operation_id' AND referenced_column_name = 'operation_id')
              OR (ordinal_position = 2 AND column_name = 'account_id' AND referenced_column_name = 'account_id'))));
    SELECT COUNT(*) INTO target_foreign_key_total_column_count
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND referenced_table_schema = DATABASE()
      AND referenced_table_name IS NOT NULL
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    IF target_foreign_key_count <> 4 OR target_foreign_key_contract_count <> 4
       OR target_foreign_key_column_count <> 9
       OR target_foreign_key_total_column_count <> 9 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger exact foreign-key contract has drifted';
    END IF;

    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(rc.table_name AS BINARY)),
               '|C:', HEX(CAST(rc.constraint_name AS BINARY)),
               '|S:', IF(rc.unique_constraint_schema = DATABASE(), 'SAME', 'OTHER'),
               '|K:', HEX(CAST(rc.unique_constraint_name AS BINARY)),
               '|R:', HEX(CAST(rc.referenced_table_name AS BINARY)),
               '|U:', HEX(CAST(rc.update_rule AS BINARY)),
               '|D:', HEX(CAST(rc.delete_rule AS BINARY)),
               '|M:', HEX(CAST(rc.match_option AS BINARY)),
               '|O:', kcu.ordinal_position,
               '|N:', HEX(CAST(kcu.column_name AS BINARY)),
               '|Q:', IF(kcu.referenced_table_schema = DATABASE(), 'SAME', 'OTHER'),
               '|P:', HEX(CAST(kcu.referenced_column_name AS BINARY)),
               '|I:', IF(kcu.position_in_unique_constraint IS NULL, 'N',
                   CONCAT('V:', kcu.position_in_unique_constraint)))
               ORDER BY rc.table_name, rc.constraint_name, kcu.ordinal_position
               SEPARATOR 0x0A), 256)
      INTO target_foreign_key_metadata_digest
    FROM information_schema.referential_constraints rc
    INNER JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_schema = rc.constraint_schema
     AND kcu.table_name = rc.table_name
     AND kcu.constraint_name = rc.constraint_name
    WHERE rc.constraint_schema = DATABASE()
      AND rc.unique_constraint_schema = DATABASE()
      AND kcu.referenced_table_schema = DATABASE()
      AND rc.table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    IF target_foreign_key_metadata_digest IS NULL
       OR CAST(target_foreign_key_metadata_digest AS BINARY) <>
          CAST('412a5276aca76d608111eecf0f2297dce0ebe299a35c6d106a4aa09da004ea67'
               AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger exact raw foreign-key metadata contract has drifted';
    END IF;

    SET migration_stage = 'check-contract-audit';
    SELECT COUNT(*) INTO target_check_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_type = 'CHECK'
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    SELECT COUNT(*) INTO target_enforced_check_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_type = 'CHECK'
      AND enforced = 'YES'
      AND table_name IN (
          'fbs_credit_account',
          'fbs_credit_operation',
          'fbs_credit_entry'
      );
    SELECT COUNT(*) INTO target_check_name_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_type = 'CHECK'
      AND enforced = 'YES'
      AND ((table_name = 'fbs_credit_account' AND constraint_name IN (
              'chk_credit_account_identifier','chk_credit_account_subject',
              'chk_credit_account_scope','chk_credit_account_currency',
              'chk_credit_account_balance','chk_credit_account_version_chain',
              'chk_credit_account_genesis','chk_credit_account_status'
          ))
        OR (table_name = 'fbs_credit_operation' AND constraint_name IN (
              'chk_credit_operation_identifiers','chk_credit_operation_scope',
              'chk_credit_operation_type','chk_credit_operation_reason',
              'chk_credit_operation_delta_reversal','chk_credit_operation_balance',
              'chk_credit_operation_actor','chk_credit_operation_status'
          ))
        OR (table_name = 'fbs_credit_entry' AND constraint_name IN (
              'chk_credit_entry_identifiers','chk_credit_entry_sequence',
              'chk_credit_entry_delta','chk_credit_entry_balance',
               'chk_credit_entry_hashes','chk_credit_entry_canonicalization'
           )));
    SELECT COUNT(*) INTO target_check_contract_count
    FROM (
        SELECT tc.table_name, tc.constraint_name,
               SHA2(CAST(cc.check_clause AS BINARY), 256) AS clause_sha256
        FROM information_schema.table_constraints tc
        INNER JOIN information_schema.check_constraints cc
          ON cc.constraint_schema = tc.constraint_schema
         AND cc.constraint_name = tc.constraint_name
        WHERE tc.constraint_schema = DATABASE()
          AND tc.constraint_type = 'CHECK'
          AND tc.enforced = 'YES'
          AND tc.table_name IN (
              'fbs_credit_account',
              'fbs_credit_operation',
              'fbs_credit_entry'
          )
    ) AS credit_checks
    WHERE (table_name = 'fbs_credit_account'
           AND constraint_name = 'chk_credit_account_balance'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '98ec9d2165952222da7c44b8cf63dfe361856aeedecb68a44a137d09c66cb1e2' AS BINARY))
       OR (table_name = 'fbs_credit_account'
           AND constraint_name = 'chk_credit_account_currency'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '98ccc1151138419780c2f74bc8e0825dd18bacf3e1c2bbf706f2d357e962768d' AS BINARY))
       OR (table_name = 'fbs_credit_account'
           AND constraint_name = 'chk_credit_account_genesis'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '0c1e09bfbe474f187ea32698dcb3d5d9ed3cc728775fc59e10d21caf7ce93a3b' AS BINARY))
       OR (table_name = 'fbs_credit_account'
           AND constraint_name = 'chk_credit_account_identifier'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '00c4f65192a67acd95086b6db9aa143801e71014970900f87288db3e4957f449' AS BINARY))
       OR (table_name = 'fbs_credit_account'
           AND constraint_name = 'chk_credit_account_scope'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               'e9ae80d17d2df62c24b39522538276af749249df444133a77697e360a4560653' AS BINARY))
       OR (table_name = 'fbs_credit_account'
           AND constraint_name = 'chk_credit_account_status'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '93b000e883dee377fb2c4ff72b0cc56c81edfee3b0e177bb2692bd7a6fd96b8e' AS BINARY))
       OR (table_name = 'fbs_credit_account'
           AND constraint_name = 'chk_credit_account_subject'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '802c6c4c4df77559e68d20bc819ddb7606c0dff5a22bbf42c0ec1623c20964a4' AS BINARY))
       OR (table_name = 'fbs_credit_account'
           AND constraint_name = 'chk_credit_account_version_chain'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               'cdad50870d01f0a2e6c288761992c5814ecabf040238bdfe84a84efc6e4b38fa' AS BINARY))
       OR (table_name = 'fbs_credit_entry'
           AND constraint_name = 'chk_credit_entry_balance'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '04c6d189e216b5663b6d9776126c271b648356593a0bc88905f445ad224228f7' AS BINARY))
       OR (table_name = 'fbs_credit_entry'
           AND constraint_name = 'chk_credit_entry_canonicalization'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               'dd73d43f3732167a1c319c8f84a4cc11e9987107849f308483c6e896e5e2dbb5' AS BINARY))
       OR (table_name = 'fbs_credit_entry'
           AND constraint_name = 'chk_credit_entry_delta'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '8f3dc49338d30337d202c01d3b979b3225460f655997580841090b60c8b230bd' AS BINARY))
       OR (table_name = 'fbs_credit_entry'
           AND constraint_name = 'chk_credit_entry_hashes'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '355a1396dabbb316ab2bbf1d0e7739dc159cb97a983fd8ac9ee21092fbbad225' AS BINARY))
       OR (table_name = 'fbs_credit_entry'
           AND constraint_name = 'chk_credit_entry_identifiers'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '5b643571318f4d53c9529b88f1f97d4722055a8a7899d6d962759ddbec5270c3' AS BINARY))
       OR (table_name = 'fbs_credit_entry'
           AND constraint_name = 'chk_credit_entry_sequence'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '2cfe0612a4f09bc1ac35239368b754f5d2e5b8ec751dea213fef585eb04af330' AS BINARY))
       OR (table_name = 'fbs_credit_operation'
           AND constraint_name = 'chk_credit_operation_actor'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '3036aeff108d469d6fb7e95a4efb11f19734bf9d8661637a4c73d9ae0f5a797c' AS BINARY))
       OR (table_name = 'fbs_credit_operation'
           AND constraint_name = 'chk_credit_operation_balance'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '04c6d189e216b5663b6d9776126c271b648356593a0bc88905f445ad224228f7' AS BINARY))
       OR (table_name = 'fbs_credit_operation'
           AND constraint_name = 'chk_credit_operation_delta_reversal'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '829f60b010d40466a2c63de1ec9ca3d432e4b262ebcf91007fd54d730dfedf66' AS BINARY))
       OR (table_name = 'fbs_credit_operation'
           AND constraint_name = 'chk_credit_operation_identifiers'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '3a4d08a2cf35978743fc76d82ab06c0ab953b062391bd3a46e6b8f79f9ac4fae' AS BINARY))
       OR (table_name = 'fbs_credit_operation'
           AND constraint_name = 'chk_credit_operation_reason'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               'ea0026b10ff279da0e2f86ec914bc1053547e77acd51baace5b9c004a0cfa776' AS BINARY))
       OR (table_name = 'fbs_credit_operation'
           AND constraint_name = 'chk_credit_operation_scope'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               '283e4d4671a09b2940f7361c82937213a8885e6a7bab1f2f44df9173417f541d' AS BINARY))
       OR (table_name = 'fbs_credit_operation'
           AND constraint_name = 'chk_credit_operation_status'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               'e0001d6c06df8197d075e979cdc2bad9963b0971b3a6954a7a315a5325b193b7' AS BINARY))
       OR (table_name = 'fbs_credit_operation'
           AND constraint_name = 'chk_credit_operation_type'
           AND CAST(clause_sha256 AS BINARY) = CAST(
               'd04d7ef95cac75a2205dde9ffd89cdf23f5c387c3ece90a10926bb4feab62557' AS BINARY));
    IF target_check_count <> 22 OR target_enforced_check_count <> 22
       OR target_check_name_count <> 22 OR target_check_contract_count <> 22 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger exact 22-check clause contract has drifted';
    END IF;

    SELECT COUNT(*) INTO target_internal_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_credit_ledger_v1'
      AND `description` = 'Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger';
    SELECT COUNT(*) INTO target_version_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_credit_ledger_v1';
    IF migration_exists = 1
       AND (target_internal_receipt_count <> 1 OR target_version_receipt_count <> 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger internal migration receipt has drifted';
    END IF;

    IF migration_exists = 1 THEN
        CALL `u3w_assert_independent_board_credit_triggers_20260722`();
    END IF;

    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger migration lost lock before trigger finalization';
    END IF;
    SET SESSION group_concat_max_len = previous_group_concat_max_len;
END$$

DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_credit_ledger_20260722`$$
CREATE PROCEDURE `u3w_finalize_independent_board_credit_ledger_20260722`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE migration_lock_released INT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE target_internal_receipt_count INT DEFAULT 0;
    DECLARE target_version_receipt_count INT DEFAULT 0;
    DECLARE migration_stage VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        DEFAULT 'trigger-contract-audit';

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
        IF migration_lock_owner = CONNECTION_ID() THEN
            DO RELEASE_LOCK(migration_lock_name);
        END IF;
        RESIGNAL;
    END;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260722_independent_board_credit_ledger_v1'),
        256
    );
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger finalizer does not own the migration lock';
    END IF;

    SET migration_stage = 'trigger-contract-audit';
    CALL `u3w_assert_independent_board_credit_triggers_20260722`();

    START TRANSACTION;
    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_credit_ledger_v1';
    IF migration_exists = 0 THEN
        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260722_independent_board_credit_ledger_v1',
            'Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger'
        );
    END IF;
    SELECT COUNT(*) INTO target_internal_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_credit_ledger_v1'
      AND `description` = 'Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger';
    SELECT COUNT(*) INTO target_version_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_credit_ledger_v1';
    IF target_internal_receipt_count <> 1 OR target_version_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger internal migration receipt is missing or drifted';
    END IF;
    COMMIT;

    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_released;
    IF migration_lock_released IS NULL OR migration_lock_released <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit ledger migration advisory lock release failed';
    END IF;
END$$

CALL `u3w_migrate_independent_board_credit_ledger_20260722`()$$

CREATE TRIGGER IF NOT EXISTS `trg_credit_account_transition`
    BEFORE UPDATE ON `fbs_credit_account`
    FOR EACH ROW
BEGIN
    IF NOT (
        OLD.id <=> NEW.id
        AND OLD.account_id <=> NEW.account_id
        AND OLD.subject_type <=> NEW.subject_type
        AND OLD.user_id <=> NEW.user_id
        AND OLD.account_scope <=> NEW.account_scope
        AND OLD.currency_code <=> NEW.currency_code
        AND OLD.opening_balance <=> NEW.opening_balance
        AND OLD.status <=> NEW.status
        AND OLD.created_at <=> NEW.created_at
        AND NEW.balance BETWEEN 0 AND 2147483647
        AND NEW.version = OLD.version + 1
        AND NEW.last_entry_sequence = OLD.last_entry_sequence + 1
        AND NEW.version = NEW.last_entry_sequence
        AND NEW.last_entry_hash REGEXP '^[0-9a-f]{64}$'
        AND NEW.last_entry_hash <> OLD.last_entry_hash
        AND NEW.updated_at >= OLD.updated_at
        AND EXISTS (
            SELECT 1
            FROM `fbs_credit_entry` e
            WHERE e.account_id = NEW.account_id
              AND e.sequence_no = NEW.last_entry_sequence
              AND e.balance_before = OLD.balance
              AND e.balance_after = NEW.balance
              AND e.previous_entry_hash = OLD.last_entry_hash
              AND e.entry_hash = NEW.last_entry_hash
        )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit account transition contract violated';
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS `trg_credit_account_no_delete`
    BEFORE DELETE ON `fbs_credit_account`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Credit accounts cannot be deleted'$$

CREATE TRIGGER IF NOT EXISTS `trg_credit_operation_no_update`
    BEFORE UPDATE ON `fbs_credit_operation`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Credit operations are immutable'$$

CREATE TRIGGER IF NOT EXISTS `trg_credit_operation_no_delete`
    BEFORE DELETE ON `fbs_credit_operation`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Credit operations are immutable'$$

CREATE TRIGGER IF NOT EXISTS `trg_credit_entry_no_update`
    BEFORE UPDATE ON `fbs_credit_entry`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Credit entries are immutable'$$

CREATE TRIGGER IF NOT EXISTS `trg_credit_entry_no_delete`
    BEFORE DELETE ON `fbs_credit_entry`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Credit entries are immutable'$$

CALL `u3w_finalize_independent_board_credit_ledger_20260722`()$$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_credit_ledger_20260722`$$
DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_credit_ledger_20260722`$$
DROP PROCEDURE IF EXISTS `u3w_assert_independent_board_credit_triggers_20260722`$$

DELIMITER ;
