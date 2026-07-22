-- ============================================================
-- FBSir Independent Board immutable plan-policy governance
-- Migration: 20260722_independent_board_plan_policy_v1
-- Public manifest step: public_init_039
-- Target: MySQL Community 8.0.30 and 8.4.8
-- Scope: immutable policy revisions, CAS heads, operation policy lineage
-- Precondition: public_init_028 is complete and exact.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_plan_policy_20260722`$$
CREATE PROCEDURE `u3w_migrate_independent_board_plan_policy_20260722`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE dependency_count INT DEFAULT 0;
    DECLARE target_count INT DEFAULT 0;
    DECLARE expected_count INT DEFAULT 0;
    DECLARE previous_group_concat_max_len BIGINT UNSIGNED DEFAULT 0;
    DECLARE target_column_metadata_digest CHAR(64);
    DECLARE target_index_metadata_digest CHAR(64);
    DECLARE target_foreign_key_metadata_digest CHAR(64);
    DECLARE target_check_metadata_digest CHAR(64);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET SESSION group_concat_max_len = previous_group_concat_max_len;
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
            SET MESSAGE_TEXT = 'Plan policy migration requires an explicit target database';
    END IF;
    IF VERSION() NOT LIKE '8.0.30%' AND VERSION() NOT LIKE '8.4.8%' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy migration requires reviewed MySQL 8.0.30 or 8.4.8';
    END IF;

    SET previous_group_concat_max_len = @@SESSION.group_concat_max_len;
    SET SESSION group_concat_max_len = 1048576;
    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260722_independent_board_plan_policy_v1'),
        256
    );
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the plan policy migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy migration lock ownership verification failed';
    END IF;

    SELECT COUNT(*) INTO dependency_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260720_independent_board_control_plane_v1'
      AND `description` = 'Independent Board generic product plan, entitlement, budget, operation and receipt control plane';
    IF dependency_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy migration requires the exact public_init_028 internal receipt';
    END IF;

    SELECT COUNT(*) INTO dependency_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB'
      AND table_name IN ('fbs_product_plan','fbs_usage_operation','fbs_entitlement_receipt');
    IF dependency_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy migration dependency tables are incomplete or not InnoDB';
    END IF;

    SELECT COUNT(*) INTO dependency_count
    FROM `fbs_product_plan`
    WHERE CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
      AND ((CAST(`plan_code` AS BINARY) = CAST('BOARD_FREE' AS BINARY)
            AND CAST(`plan_name` AS BINARY) = CAST('Independent Board Free' AS BINARY)
            AND `vip` = 0 AND `connector_required` = 0
            AND `daily_meeting_limit` = 1 AND `agenda_limit` = 5
            AND `seat_limit` = 3 AND `secretary_enabled` = 0
            AND CAST(`status` AS BINARY) = CAST('ACTIVE' AS BINARY) AND `version` = 1)
        OR (CAST(`plan_code` AS BINARY) = CAST('BOARD_VIP' AS BINARY)
            AND CAST(`plan_name` AS BINARY) = CAST('Independent Board VIP' AS BINARY)
            AND `vip` = 1 AND `connector_required` = 1
            AND `daily_meeting_limit` = 5 AND `agenda_limit` = 30
            AND `seat_limit` IS NULL AND `secretary_enabled` = 1
            AND CAST(`status` AS BINARY) = CAST('ACTIVE' AS BINARY) AND `version` = 1));
    SELECT COUNT(*) INTO target_count
    FROM `fbs_product_plan`
    WHERE `product_code` = 'FBSIR_INDEPENDENT_BOARD';
    IF dependency_count <> 2 OR target_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy migration requires the exact FREE/VIP baseline catalog';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM `fbs_usage_operation`
    WHERE `product_code` = 'FBSIR_INDEPENDENT_BOARD'
      AND NOT (
          CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
          AND CAST(`effective_plan_code` AS BINARY) IN (
              CAST('BOARD_FREE' AS BINARY), CAST('BOARD_VIP' AS BINARY)
          )
      );
    IF target_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Unknown Independent Board operation plan prevents deterministic policy backfill';
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_plan_policy_v1';
    IF migration_exists > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy internal migration receipt is duplicated';
    END IF;
    IF migration_exists = 1 THEN
        SELECT COUNT(*) INTO target_count
        FROM `u3w_schema_migration`
        WHERE `version` = '20260722_independent_board_plan_policy_v1'
          AND `description` = 'Independent Board immutable plan policy revisions and operation lineage';
        IF target_count <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Plan policy internal migration receipt has drifted';
        END IF;
        SELECT COUNT(*) INTO target_count
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND event_object_table IN (
              'fbs_plan_policy_revision_receipt',
              'fbs_usage_operation_policy_receipt',
              'fbs_entitlement_receipt'
          );
        IF target_count <> 7 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Completed plan policy migration trigger set has drifted';
        END IF;
    END IF;

    CREATE TABLE IF NOT EXISTS `fbs_plan_policy_revision_receipt` (
        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
        `receipt_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'globally unique immutable receipt id',
        `product_code` VARCHAR(64) NOT NULL COMMENT 'canonical product code',
        `plan_code` VARCHAR(64) NOT NULL COMMENT 'product-scoped plan code',
        `policy_version` BIGINT UNSIGNED NOT NULL COMMENT 'monotonic policy version',
        `previous_receipt_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'previous committed receipt',
        `rollback_of_receipt_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'historical policy restored by compensation',
        `action` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'BASELINED, REVISED, or ROLLED_BACK',
        `actor_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SYSTEM_MIGRATION or ADMIN_USER',
        `actor_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT 'nullable only for system migration',
        `idempotency_key_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 of scoped idempotency key',
        `command_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 of canonical command',
        `previous_policy_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'previous committed policy SHA-256',
        `policy_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'committed policy SHA-256',
        `plan_name` VARCHAR(128) NOT NULL COMMENT 'committed display name',
        `vip` TINYINT(1) NOT NULL COMMENT 'immutable plan identity snapshot',
        `connector_required` TINYINT(1) NOT NULL COMMENT 'immutable connector identity snapshot',
        `daily_meeting_limit` INT UNSIGNED NOT NULL COMMENT 'committed daily meeting quota',
        `agenda_limit` INT UNSIGNED NOT NULL COMMENT 'committed agenda quota',
        `seat_limit` INT UNSIGNED DEFAULT NULL COMMENT 'committed seat quota; NULL means no plan cap',
        `secretary_enabled` TINYINT(1) NOT NULL COMMENT 'committed secretary capability',
        `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'immutable baseline lifecycle status',
        `evidence_level` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'reviewed evidence level',
        `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_plan_policy_receipt_id` (`receipt_id`),
        UNIQUE KEY `uk_plan_policy_receipt_version` (`product_code`, `plan_code`, `policy_version`),
        UNIQUE KEY `uk_plan_policy_receipt_idempotency`
            (`product_code`, `actor_type`, `actor_user_id`, `idempotency_key_digest`),
        UNIQUE KEY `uk_plan_policy_receipt_scope_id`
            (`product_code`, `plan_code`, `receipt_id`),
        UNIQUE KEY `uk_plan_policy_receipt_scope_id_version`
            (`product_code`, `plan_code`, `receipt_id`, `policy_version`),
        KEY `idx_plan_policy_receipt_previous`
            (`product_code`, `plan_code`, `previous_receipt_id`),
        KEY `idx_plan_policy_receipt_rollback`
            (`product_code`, `plan_code`, `rollback_of_receipt_id`),
        KEY `idx_plan_policy_receipt_history`
            (`product_code`, `plan_code`, `created_at`, `id`),
        CONSTRAINT `fk_plan_policy_receipt_plan`
            FOREIGN KEY (`product_code`, `plan_code`)
            REFERENCES `fbs_product_plan` (`product_code`, `plan_code`)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
        CONSTRAINT `fk_plan_policy_receipt_previous`
            FOREIGN KEY (`product_code`, `plan_code`, `previous_receipt_id`)
            REFERENCES `fbs_plan_policy_revision_receipt`
                (`product_code`, `plan_code`, `receipt_id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
        CONSTRAINT `fk_plan_policy_receipt_rollback`
            FOREIGN KEY (`product_code`, `plan_code`, `rollback_of_receipt_id`)
            REFERENCES `fbs_plan_policy_revision_receipt`
                (`product_code`, `plan_code`, `receipt_id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
        CONSTRAINT `chk_plan_policy_receipt_scope` CHECK (
            CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
            AND CAST(`plan_code` AS BINARY) IN (
                CAST('BOARD_FREE' AS BINARY), CAST('BOARD_VIP' AS BINARY)
            )
        ),
        CONSTRAINT `chk_plan_policy_receipt_version` CHECK (`policy_version` > 0),
        CONSTRAINT `chk_plan_policy_receipt_action_actor` CHECK (
            (`action` = 'PLAN_POLICY_BASELINED' AND `actor_type` = 'SYSTEM_MIGRATION'
                AND `actor_user_id` IS NULL AND `policy_version` = 1)
            OR (`action` IN ('PLAN_POLICY_REVISED','PLAN_POLICY_ROLLED_BACK')
                AND `actor_type` = 'ADMIN_USER' AND `actor_user_id` IS NOT NULL
                AND `policy_version` > 1)
        ),
        CONSTRAINT `chk_plan_policy_receipt_chain` CHECK (
            (`policy_version` = 1 AND `previous_receipt_id` IS NULL
                AND `previous_policy_digest` IS NULL AND `rollback_of_receipt_id` IS NULL)
            OR (`policy_version` > 1 AND `previous_receipt_id` IS NOT NULL
                AND `previous_policy_digest` IS NOT NULL
                AND ((`action` = 'PLAN_POLICY_REVISED' AND `rollback_of_receipt_id` IS NULL)
                    OR (`action` = 'PLAN_POLICY_ROLLED_BACK' AND `rollback_of_receipt_id` IS NOT NULL)))
        ),
        CONSTRAINT `chk_plan_policy_receipt_digests` CHECK (
            `idempotency_key_digest` REGEXP '^[0-9a-f]{64}$'
            AND `command_digest` REGEXP '^[0-9a-f]{64}$'
            AND (`previous_policy_digest` IS NULL
                OR `previous_policy_digest` REGEXP '^[0-9a-f]{64}$')
            AND `policy_digest` REGEXP '^[0-9a-f]{64}$'
        ),
        CONSTRAINT `chk_plan_policy_receipt_identity` CHECK (
            (CAST(`plan_code` AS BINARY) = CAST('BOARD_FREE' AS BINARY)
                AND `vip` = 0 AND `connector_required` = 0 AND `seat_limit` IS NOT NULL)
            OR (CAST(`plan_code` AS BINARY) = CAST('BOARD_VIP' AS BINARY)
                AND `vip` = 1 AND `connector_required` = 1)
        ),
        CONSTRAINT `chk_plan_policy_receipt_quotas` CHECK (
            CHAR_LENGTH(`plan_name`) BETWEEN 1 AND 128
            AND CAST(`plan_name` AS BINARY) = CAST(TRIM(`plan_name`) AS BINARY)
            AND `plan_name` NOT REGEXP '[\\p{Cc}\\p{Cf}]'
            AND `plan_name` NOT REGEXP '(^[\\p{Z}])|([\\p{Z}]$)'
            AND `daily_meeting_limit` BETWEEN 1 AND 10000
            AND `agenda_limit` BETWEEN 1 AND 30
            AND (`seat_limit` IS NULL OR `seat_limit` BETWEEN 1 AND 100)
            AND `secretary_enabled` IN (0,1)
        ),
        CONSTRAINT `chk_plan_policy_receipt_status_evidence` CHECK (
            `status` = 'ACTIVE' AND `evidence_level` = 'ACTION_COMPLETED'
        )
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
      COMMENT='Immutable Independent Board plan policy revision receipts';

    CREATE TABLE IF NOT EXISTS `fbs_plan_policy_head` (
        `product_code` VARCHAR(64) NOT NULL COMMENT 'canonical product code',
        `plan_code` VARCHAR(64) NOT NULL COMMENT 'product-scoped plan code',
        `active_receipt_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'current committed receipt',
        `policy_version` BIGINT UNSIGNED NOT NULL COMMENT 'current committed version',
        `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
        PRIMARY KEY (`product_code`, `plan_code`),
        KEY `idx_plan_policy_head_active`
            (`product_code`, `plan_code`, `active_receipt_id`, `policy_version`),
        CONSTRAINT `fk_plan_policy_head_plan`
            FOREIGN KEY (`product_code`, `plan_code`)
            REFERENCES `fbs_product_plan` (`product_code`, `plan_code`)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
        CONSTRAINT `fk_plan_policy_head_active`
            FOREIGN KEY (`product_code`, `plan_code`, `active_receipt_id`, `policy_version`)
            REFERENCES `fbs_plan_policy_revision_receipt`
                (`product_code`, `plan_code`, `receipt_id`, `policy_version`)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
        CONSTRAINT `chk_plan_policy_head_scope` CHECK (
            CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
            AND CAST(`plan_code` AS BINARY) IN (
                CAST('BOARD_FREE' AS BINARY), CAST('BOARD_VIP' AS BINARY)
            )
            AND `policy_version` > 0
        )
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
      COMMENT='CAS pointer to each current committed plan policy';

    CREATE TABLE IF NOT EXISTS `fbs_usage_operation_policy_receipt` (
        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
        `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT 'tenant scope copied from operation',
        `operation_id` VARCHAR(128) NOT NULL COMMENT 'operation id copied from usage ledger',
        `product_code` VARCHAR(64) NOT NULL COMMENT 'operation product snapshot',
        `plan_code` VARCHAR(64) NOT NULL COMMENT 'operation effective plan snapshot',
        `policy_receipt_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'policy receipt consumed by operation',
        `policy_version` BIGINT UNSIGNED NOT NULL COMMENT 'policy version consumed by operation',
        `policy_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'policy digest consumed by operation',
        `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_usage_operation_policy_scope` (`enterprise_id`, `operation_id`),
        KEY `idx_usage_operation_policy_receipt`
            (`product_code`, `plan_code`, `policy_receipt_id`, `policy_version`),
        KEY `idx_usage_operation_policy_history`
            (`product_code`, `plan_code`, `created_at`, `id`),
        CONSTRAINT `fk_usage_operation_policy_operation`
            FOREIGN KEY (`enterprise_id`, `operation_id`)
            REFERENCES `fbs_usage_operation` (`enterprise_id`, `operation_id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
        CONSTRAINT `fk_usage_operation_policy_receipt`
            FOREIGN KEY (`product_code`, `plan_code`, `policy_receipt_id`, `policy_version`)
            REFERENCES `fbs_plan_policy_revision_receipt`
                (`product_code`, `plan_code`, `receipt_id`, `policy_version`)
            ON UPDATE RESTRICT ON DELETE RESTRICT,
        CONSTRAINT `chk_usage_operation_policy_scope` CHECK (
            CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
            AND CAST(`plan_code` AS BINARY) IN (
                CAST('BOARD_FREE' AS BINARY), CAST('BOARD_VIP' AS BINARY)
            )
            AND `policy_version` > 0
            AND `policy_digest` REGEXP '^[0-9a-f]{64}$'
        )
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
      COMMENT='Immutable operation-to-policy lineage';

    SELECT COUNT(*) INTO target_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB' AND table_collation = 'utf8mb4_unicode_ci'
      AND table_name IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt'
      );
    IF target_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy exact three-table engine/collation contract has drifted';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt'
      );
    SELECT COUNT(*) INTO expected_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_plan_policy_revision_receipt' AND column_name IN (
            'id','receipt_id','product_code','plan_code','policy_version',
            'previous_receipt_id','rollback_of_receipt_id','action','actor_type',
            'actor_user_id','idempotency_key_digest','command_digest',
            'previous_policy_digest','policy_digest','plan_name','vip',
            'connector_required','daily_meeting_limit','agenda_limit','seat_limit',
            'secretary_enabled','status','evidence_level','created_at'))
        OR (table_name = 'fbs_plan_policy_head' AND column_name IN (
            'product_code','plan_code','active_receipt_id','policy_version',
            'created_at','updated_at'))
        OR (table_name = 'fbs_usage_operation_policy_receipt' AND column_name IN (
            'id','enterprise_id','operation_id','product_code','plan_code',
            'policy_receipt_id','policy_version','policy_digest','created_at')));
    IF target_count <> 39 OR expected_count <> 39 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy exact 39-column name contract has drifted';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
      AND enforced = 'YES'
      AND ((table_name = 'fbs_plan_policy_revision_receipt'
            AND constraint_name IN (
              'chk_plan_policy_receipt_scope','chk_plan_policy_receipt_version',
              'chk_plan_policy_receipt_action_actor','chk_plan_policy_receipt_chain',
              'chk_plan_policy_receipt_digests','chk_plan_policy_receipt_identity',
              'chk_plan_policy_receipt_quotas','chk_plan_policy_receipt_status_evidence'))
        OR (table_name = 'fbs_plan_policy_head'
            AND constraint_name = 'chk_plan_policy_head_scope')
        OR (table_name = 'fbs_usage_operation_policy_receipt'
            AND constraint_name = 'chk_usage_operation_policy_scope'));
    SELECT COUNT(*) INTO expected_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
      AND table_name IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt'
      );
    IF target_count <> 10 OR expected_count <> 10 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy exact ten-check contract has drifted';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE() AND unique_constraint_schema = DATABASE()
      AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'
      AND ((table_name = 'fbs_plan_policy_revision_receipt'
            AND constraint_name IN (
              'fk_plan_policy_receipt_plan','fk_plan_policy_receipt_previous',
              'fk_plan_policy_receipt_rollback'))
        OR (table_name = 'fbs_plan_policy_head'
            AND constraint_name IN ('fk_plan_policy_head_plan','fk_plan_policy_head_active'))
        OR (table_name = 'fbs_usage_operation_policy_receipt'
            AND constraint_name IN (
              'fk_usage_operation_policy_operation','fk_usage_operation_policy_receipt')));
    SELECT COUNT(*) INTO expected_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt'
      );
    IF target_count <> 7 OR expected_count <> 7 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy exact seven-foreign-key contract has drifted';
    END IF;

    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:',HEX(CAST(table_name AS BINARY)),
               '|O:',LPAD(ordinal_position,3,'0'),
               '|N:',HEX(CAST(column_name AS BINARY)),
               '|Y:',HEX(CAST(column_type AS BINARY)),
               '|U:',HEX(CAST(is_nullable AS BINARY)),
               '|D:',IF(column_default IS NULL,'N',
                   CONCAT('V:',HEX(CAST(column_default AS BINARY)))),
               '|C:',IF(character_set_name IS NULL,'N',
                   CONCAT('V:',HEX(CAST(character_set_name AS BINARY)))),
               '|L:',IF(collation_name IS NULL,'N',
                   CONCAT('V:',HEX(CAST(collation_name AS BINARY)))),
               '|E:',HEX(CAST(extra AS BINARY)),
               '|G:',IF(generation_expression IS NULL,'N',
                   CONCAT('V:',HEX(CAST(generation_expression AS BINARY)))))
               ORDER BY table_name,ordinal_position SEPARATOR 0x0A),256)
      INTO target_column_metadata_digest
    FROM information_schema.columns
    WHERE table_schema=DATABASE()
      AND table_name IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt'
      );
    IF target_column_metadata_digest IS NULL OR
       target_column_metadata_digest <>
         '3861fa022a759a9f9a5f773da995273b5259e361be7a931a9ad761eca02473d7' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy exact raw column metadata contract has drifted';
    END IF;

    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:',HEX(CAST(table_name AS BINARY)),
               '|I:',HEX(CAST(index_name AS BINARY)),
               '|U:',non_unique,
               '|Y:',HEX(CAST(index_type AS BINARY)),
               '|V:',HEX(CAST(is_visible AS BINARY)),
               '|S:',seq_in_index,
               '|N:',IF(column_name IS NULL,'N',
                   CONCAT('V:',HEX(CAST(column_name AS BINARY)))),
               '|X:',IF(expression IS NULL,'N',
                   CONCAT('V:',HEX(CAST(expression AS BINARY)))),
               '|C:',IF(collation IS NULL,'N',
                   CONCAT('V:',HEX(CAST(collation AS BINARY)))),
               '|P:',IF(sub_part IS NULL,'N',CONCAT('V:',sub_part)),
               '|Q:',HEX(CAST(nullable AS BINARY)))
               ORDER BY table_name,index_name,seq_in_index SEPARATOR 0x0A),256)
      INTO target_index_metadata_digest
    FROM information_schema.statistics
    WHERE table_schema=DATABASE()
      AND table_name IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt'
      );
    IF target_index_metadata_digest IS NULL OR
       target_index_metadata_digest <>
         '2d21d400829820467a0915c202fbdd5343e5d9417fe1ac062a48742ec0a6541b' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy exact raw index metadata contract has drifted';
    END IF;

    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:',HEX(CAST(rc.table_name AS BINARY)),
               '|C:',HEX(CAST(rc.constraint_name AS BINARY)),
               '|K:',HEX(CAST(rc.unique_constraint_name AS BINARY)),
               '|R:',HEX(CAST(rc.referenced_table_name AS BINARY)),
               '|U:',HEX(CAST(rc.update_rule AS BINARY)),
               '|D:',HEX(CAST(rc.delete_rule AS BINARY)),
               '|O:',kcu.ordinal_position,
               '|N:',HEX(CAST(kcu.column_name AS BINARY)),
               '|P:',HEX(CAST(kcu.referenced_column_name AS BINARY)),
               '|I:',IF(kcu.position_in_unique_constraint IS NULL,'N',
                   CONCAT('V:',kcu.position_in_unique_constraint)))
               ORDER BY rc.table_name,rc.constraint_name,kcu.ordinal_position
               SEPARATOR 0x0A),256)
      INTO target_foreign_key_metadata_digest
    FROM information_schema.referential_constraints rc
    INNER JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_schema=rc.constraint_schema
     AND kcu.table_name=rc.table_name
     AND kcu.constraint_name=rc.constraint_name
    WHERE rc.constraint_schema=DATABASE()
      AND kcu.referenced_table_schema=DATABASE()
      AND rc.table_name IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt'
      );
    IF target_foreign_key_metadata_digest IS NULL OR
       target_foreign_key_metadata_digest <>
         '03dfe7bb006d20b768840f71a435d0233355001dc35d6c7cffda5c68ef6151de' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy exact raw foreign-key metadata contract has drifted';
    END IF;

    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:',HEX(CAST(tc.table_name AS BINARY)),
               '|C:',HEX(CAST(tc.constraint_name AS BINARY)),
               '|E:',HEX(CAST(tc.enforced AS BINARY)),
               '|Q:',HEX(CAST(cc.check_clause AS BINARY)))
               ORDER BY tc.table_name,tc.constraint_name SEPARATOR 0x0A),256)
      INTO target_check_metadata_digest
    FROM information_schema.table_constraints tc
    INNER JOIN information_schema.check_constraints cc
      ON cc.constraint_schema=tc.constraint_schema
     AND cc.constraint_name=tc.constraint_name
    WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
      AND tc.table_name IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt'
      );
    IF target_check_metadata_digest IS NULL OR
       target_check_metadata_digest <>
         'b97cf71e29e1bfbbb58bd9ef58ed8f9334c586de102e43b6bb231e392ac84fad' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy exact raw CHECK metadata contract has drifted';
    END IF;

    START TRANSACTION;
    IF migration_exists = 0 THEN
        INSERT INTO `fbs_plan_policy_revision_receipt` (
            `receipt_id`,`product_code`,`plan_code`,`policy_version`,
            `previous_receipt_id`,`rollback_of_receipt_id`,`action`,`actor_type`,
            `actor_user_id`,`idempotency_key_digest`,`command_digest`,
            `previous_policy_digest`,`policy_digest`,`plan_name`,`vip`,
            `connector_required`,`daily_meeting_limit`,`agenda_limit`,`seat_limit`,
            `secretary_enabled`,`status`,`evidence_level`
        )
        SELECT
            CASE p.`plan_code`
                WHEN 'BOARD_FREE' THEN 'plan-policy-baseline-board-free-v1'
                WHEN 'BOARD_VIP' THEN 'plan-policy-baseline-board-vip-v1'
            END,
            p.`product_code`, p.`plan_code`, 1,
            NULL, NULL, 'PLAN_POLICY_BASELINED', 'SYSTEM_MIGRATION', NULL,
            SHA2(CONCAT('fbsir.plan-policy-baseline/v1:', p.`plan_code`), 256),
            SHA2(CONCAT_WS(CHAR(31),
                'fbsir.plan-policy-command/v1','SYSTEM_MIGRATION',
                p.`product_code`,p.`plan_code`,'1'), 256),
            NULL,
            CASE p.`plan_code`
                WHEN 'BOARD_FREE' THEN '8ae3df83c9f56974261d1e471eb19034b2b782f793f74333c64a13c61dae8982'
                WHEN 'BOARD_VIP' THEN '02e90096b76d25b69c938fa65cfc3931207ac0a2648447bf613dca591619da51'
            END,
            p.`plan_name`,p.`vip`,p.`connector_required`,p.`daily_meeting_limit`,
            p.`agenda_limit`,p.`seat_limit`,p.`secretary_enabled`,p.`status`,
            'ACTION_COMPLETED'
        FROM `fbs_product_plan` p
        WHERE CAST(p.`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
          AND CAST(p.`plan_code` AS BINARY) IN (
              CAST('BOARD_FREE' AS BINARY), CAST('BOARD_VIP' AS BINARY)
          )
          AND NOT EXISTS (
              SELECT 1 FROM `fbs_plan_policy_revision_receipt` r
              WHERE r.`product_code` = p.`product_code`
                AND r.`plan_code` = p.`plan_code` AND r.`policy_version` = 1
          )
        ORDER BY p.`plan_code`;

        INSERT INTO `fbs_plan_policy_head` (
            `product_code`,`plan_code`,`active_receipt_id`,`policy_version`
        )
        SELECT r.`product_code`,r.`plan_code`,r.`receipt_id`,r.`policy_version`
        FROM `fbs_plan_policy_revision_receipt` r
        WHERE r.`policy_version` = 1
          AND r.`action` = 'PLAN_POLICY_BASELINED'
          AND NOT EXISTS (
              SELECT 1 FROM `fbs_plan_policy_head` h
              WHERE h.`product_code` = r.`product_code`
                AND h.`plan_code` = r.`plan_code`
          )
        ORDER BY r.`plan_code`;

        INSERT INTO `fbs_usage_operation_policy_receipt` (
            `enterprise_id`,`operation_id`,`product_code`,`plan_code`,
            `policy_receipt_id`,`policy_version`,`policy_digest`
        )
        SELECT o.`enterprise_id`,o.`operation_id`,o.`product_code`,o.`effective_plan_code`,
               r.`receipt_id`,r.`policy_version`,r.`policy_digest`
        FROM `fbs_usage_operation` o
        INNER JOIN `fbs_plan_policy_revision_receipt` r
          ON r.`product_code` = o.`product_code`
         AND r.`plan_code` = o.`effective_plan_code`
         AND r.`policy_version` = 1
         AND r.`action` = 'PLAN_POLICY_BASELINED'
        WHERE CAST(o.`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
          AND CAST(o.`effective_plan_code` AS BINARY) IN (
              CAST('BOARD_FREE' AS BINARY), CAST('BOARD_VIP' AS BINARY)
          )
          AND NOT EXISTS (
              SELECT 1 FROM `fbs_usage_operation_policy_receipt` l
              WHERE l.`enterprise_id` = o.`enterprise_id`
                AND l.`operation_id` = o.`operation_id`
          )
        ORDER BY o.`enterprise_id`,o.`operation_id`;
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM `fbs_plan_policy_revision_receipt`
    WHERE `policy_version` = 1
      AND `action` = 'PLAN_POLICY_BASELINED'
      AND `actor_type` = 'SYSTEM_MIGRATION' AND `actor_user_id` IS NULL
      AND `previous_receipt_id` IS NULL AND `previous_policy_digest` IS NULL
      AND `rollback_of_receipt_id` IS NULL
      AND ((CAST(`plan_code` AS BINARY) = CAST('BOARD_FREE' AS BINARY)
            AND CAST(`receipt_id` AS BINARY) = CAST('plan-policy-baseline-board-free-v1' AS BINARY)
            AND `policy_digest` = '8ae3df83c9f56974261d1e471eb19034b2b782f793f74333c64a13c61dae8982'
            AND CAST(`plan_name` AS BINARY) = CAST('Independent Board Free' AS BINARY)
            AND `daily_meeting_limit` = 1 AND `agenda_limit` = 5
            AND `seat_limit` = 3 AND `secretary_enabled` = 0)
        OR (CAST(`plan_code` AS BINARY) = CAST('BOARD_VIP' AS BINARY)
            AND CAST(`receipt_id` AS BINARY) = CAST('plan-policy-baseline-board-vip-v1' AS BINARY)
            AND `policy_digest` = '02e90096b76d25b69c938fa65cfc3931207ac0a2648447bf613dca591619da51'
            AND CAST(`plan_name` AS BINARY) = CAST('Independent Board VIP' AS BINARY)
            AND `daily_meeting_limit` = 5 AND `agenda_limit` = 30
            AND `seat_limit` IS NULL AND `secretary_enabled` = 1));
    IF target_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy deterministic SYSTEM_MIGRATION baselines have drifted';
    END IF;

    SELECT COUNT(*) INTO target_count FROM `fbs_plan_policy_head`;
    IF target_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy head catalog must contain exactly two rows';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM `fbs_plan_policy_revision_receipt` r
    LEFT JOIN `fbs_plan_policy_revision_receipt` p
      ON p.`product_code` = r.`product_code`
     AND p.`plan_code` = r.`plan_code`
     AND p.`receipt_id` = r.`previous_receipt_id`
    WHERE r.`policy_version` > 1
      AND (p.`id` IS NULL OR p.`policy_version` + 1 <> r.`policy_version`
           OR p.`policy_digest` <> r.`previous_policy_digest`);
    IF target_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy revision chain is not contiguous';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM `fbs_plan_policy_revision_receipt` r
    INNER JOIN `fbs_plan_policy_revision_receipt` target
      ON target.`product_code` = r.`product_code`
     AND target.`plan_code` = r.`plan_code`
     AND target.`receipt_id` = r.`rollback_of_receipt_id`
    WHERE r.`action` = 'PLAN_POLICY_ROLLED_BACK'
      AND NOT (CAST(r.`plan_name` AS BINARY) <=> CAST(target.`plan_name` AS BINARY)
           AND r.`vip` <=> target.`vip`
           AND r.`connector_required` <=> target.`connector_required`
           AND r.`daily_meeting_limit` <=> target.`daily_meeting_limit`
           AND r.`agenda_limit` <=> target.`agenda_limit`
           AND r.`seat_limit` <=> target.`seat_limit`
           AND r.`secretary_enabled` <=> target.`secretary_enabled`
           AND r.`status` <=> target.`status`
           AND r.`policy_digest` <=> target.`policy_digest`);
    IF target_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy compensation receipt does not match its target snapshot';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM `fbs_plan_policy_head` h
    INNER JOIN `fbs_plan_policy_revision_receipt` r
      ON r.`product_code` = h.`product_code`
     AND r.`plan_code` = h.`plan_code`
     AND r.`receipt_id` = h.`active_receipt_id`
     AND r.`policy_version` = h.`policy_version`
    INNER JOIN (
        SELECT `product_code`,`plan_code`,MAX(`policy_version`) AS max_version
        FROM `fbs_plan_policy_revision_receipt`
        GROUP BY `product_code`,`plan_code`
    ) latest
      ON latest.`product_code` = h.`product_code`
     AND latest.`plan_code` = h.`plan_code`
     AND latest.max_version = h.`policy_version`;
    IF target_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy heads must point to both latest committed receipts';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM `fbs_plan_policy_head` free_head
    INNER JOIN `fbs_plan_policy_revision_receipt` free_policy
      ON free_policy.`product_code` = free_head.`product_code`
     AND free_policy.`plan_code` = free_head.`plan_code`
     AND free_policy.`receipt_id` = free_head.`active_receipt_id`
     AND free_policy.`policy_version` = free_head.`policy_version`
    INNER JOIN `fbs_plan_policy_head` vip_head
      ON vip_head.`product_code` = free_head.`product_code`
    INNER JOIN `fbs_plan_policy_revision_receipt` vip_policy
      ON vip_policy.`product_code` = vip_head.`product_code`
     AND vip_policy.`plan_code` = vip_head.`plan_code`
     AND vip_policy.`receipt_id` = vip_head.`active_receipt_id`
     AND vip_policy.`policy_version` = vip_head.`policy_version`
    WHERE CAST(free_head.`plan_code` AS BINARY) = CAST('BOARD_FREE' AS BINARY)
      AND CAST(vip_head.`plan_code` AS BINARY) = CAST('BOARD_VIP' AS BINARY)
      AND vip_policy.`daily_meeting_limit` >= free_policy.`daily_meeting_limit`
      AND vip_policy.`agenda_limit` >= free_policy.`agenda_limit`
      AND (vip_policy.`seat_limit` IS NULL
           OR vip_policy.`seat_limit` >= free_policy.`seat_limit`)
      AND (free_policy.`secretary_enabled` = 0 OR vip_policy.`secretary_enabled` = 1);
    IF target_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy current FREE/VIP monotonicity has drifted';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM `fbs_usage_operation` o
    LEFT JOIN `fbs_usage_operation_policy_receipt` l
      ON l.`enterprise_id` = o.`enterprise_id`
     AND l.`operation_id` = o.`operation_id`
     AND CAST(l.`operation_id` AS BINARY) = CAST(o.`operation_id` AS BINARY)
    LEFT JOIN `fbs_plan_policy_revision_receipt` r
      ON r.`product_code` = l.`product_code`
     AND r.`plan_code` = l.`plan_code`
     AND r.`receipt_id` = l.`policy_receipt_id`
     AND r.`policy_version` = l.`policy_version`
    WHERE CAST(o.`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
      AND (l.`id` IS NULL OR r.`id` IS NULL
           OR CAST(l.`product_code` AS BINARY) <> CAST(o.`product_code` AS BINARY)
           OR CAST(l.`plan_code` AS BINARY) <> CAST(o.`effective_plan_code` AS BINARY)
           OR l.`policy_digest` <> r.`policy_digest`);
    IF target_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board operation policy lineage is incomplete or drifted';
    END IF;

    COMMIT;
    SET SESSION group_concat_max_len = previous_group_concat_max_len;
END$$

DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_plan_policy_20260722`$$
CREATE PROCEDURE `u3w_finalize_independent_board_plan_policy_20260722`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE migration_lock_released INT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE target_count INT DEFAULT 0;

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
        CONCAT(DATABASE(), ':20260722_independent_board_plan_policy_v1'),
        256
    );
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy finalizer does not own the migration lock';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND action_timing = 'BEFORE' AND action_orientation = 'ROW'
      AND action_condition IS NULL AND action_order = 1
      AND ((trigger_name = 'trg_plan_policy_receipt_no_update'
            AND event_object_table = 'fbs_plan_policy_revision_receipt'
            AND event_manipulation = 'UPDATE'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                '59d2d90cab68d42f6655f1fb176eb83d098c3c29a25c7242ccab4273e558170a')
        OR (trigger_name = 'trg_plan_policy_receipt_no_delete'
            AND event_object_table = 'fbs_plan_policy_revision_receipt'
            AND event_manipulation = 'DELETE'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                '59d2d90cab68d42f6655f1fb176eb83d098c3c29a25c7242ccab4273e558170a')
        OR (trigger_name = 'trg_usage_operation_policy_guard_insert'
            AND event_object_table = 'fbs_usage_operation_policy_receipt'
            AND event_manipulation = 'INSERT'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                'c9409524d7203b611129b9704cdc9752ffcdecdcb2b29b97a21cb3ef08b675f9')
        OR (trigger_name = 'trg_usage_operation_policy_no_update'
            AND event_object_table = 'fbs_usage_operation_policy_receipt'
            AND event_manipulation = 'UPDATE'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                'be062b76a71de8c859ea35de136217a34f5a900e454a19e4284c691deb4134a3')
        OR (trigger_name = 'trg_usage_operation_policy_no_delete'
            AND event_object_table = 'fbs_usage_operation_policy_receipt'
            AND event_manipulation = 'DELETE'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                'be062b76a71de8c859ea35de136217a34f5a900e454a19e4284c691deb4134a3')
        OR (trigger_name = 'trg_entitlement_receipt_no_update'
            AND event_object_table = 'fbs_entitlement_receipt'
            AND event_manipulation = 'UPDATE'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                '5c40f4bae16986eae2b1cbbef38994a93263c870a0940432902dd5d6b9cc151e')
        OR (trigger_name = 'trg_entitlement_receipt_no_delete'
            AND event_object_table = 'fbs_entitlement_receipt'
            AND event_manipulation = 'DELETE'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                '5c40f4bae16986eae2b1cbbef38994a93263c870a0940432902dd5d6b9cc151e'));
    IF target_count <> 7 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy exact seven-trigger metadata contract has drifted';
    END IF;
    SELECT COUNT(*) INTO target_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_usage_operation_policy_receipt',
          'fbs_entitlement_receipt'
      );
    IF target_count <> 7 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy receipt tables contain unexpected triggers';
    END IF;

    START TRANSACTION;
    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_plan_policy_v1';
    IF migration_exists = 0 THEN
        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260722_independent_board_plan_policy_v1',
            'Independent Board immutable plan policy revisions and operation lineage'
        );
    END IF;
    SELECT COUNT(*) INTO target_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_plan_policy_v1'
      AND `description` = 'Independent Board immutable plan policy revisions and operation lineage';
    IF target_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy internal migration receipt is missing or drifted';
    END IF;
    COMMIT;

    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_released;
    IF migration_lock_released IS NULL OR migration_lock_released <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy migration advisory lock release failed';
    END IF;
END$$

CALL `u3w_migrate_independent_board_plan_policy_20260722`()$$

CREATE TRIGGER IF NOT EXISTS `trg_plan_policy_receipt_no_update`
    BEFORE UPDATE ON `fbs_plan_policy_revision_receipt`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Plan policy receipts are immutable';
END$$

CREATE TRIGGER IF NOT EXISTS `trg_plan_policy_receipt_no_delete`
    BEFORE DELETE ON `fbs_plan_policy_revision_receipt`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Plan policy receipts are immutable';
END$$

CREATE TRIGGER IF NOT EXISTS `trg_usage_operation_policy_guard_insert`
    BEFORE INSERT ON `fbs_usage_operation_policy_receipt`
    FOR EACH ROW
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM `fbs_usage_operation` o
        INNER JOIN `fbs_plan_policy_revision_receipt` r
          ON r.`product_code` = NEW.`product_code`
         AND r.`plan_code` = NEW.`plan_code`
         AND r.`receipt_id` = NEW.`policy_receipt_id`
         AND r.`policy_version` = NEW.`policy_version`
        WHERE o.`enterprise_id` = NEW.`enterprise_id`
          AND o.`operation_id` = NEW.`operation_id`
          AND CAST(o.`operation_id` AS BINARY) = CAST(NEW.`operation_id` AS BINARY)
          AND CAST(o.`product_code` AS BINARY) = CAST(NEW.`product_code` AS BINARY)
          AND CAST(o.`effective_plan_code` AS BINARY) = CAST(NEW.`plan_code` AS BINARY)
          AND r.`policy_digest` = NEW.`policy_digest`
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Operation policy lineage must match the exact operation and receipt';
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS `trg_usage_operation_policy_no_update`
    BEFORE UPDATE ON `fbs_usage_operation_policy_receipt`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operation policy lineage is immutable';
END$$

CREATE TRIGGER IF NOT EXISTS `trg_usage_operation_policy_no_delete`
    BEFORE DELETE ON `fbs_usage_operation_policy_receipt`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Operation policy lineage is immutable';
END$$

CREATE TRIGGER IF NOT EXISTS `trg_entitlement_receipt_no_update`
    BEFORE UPDATE ON `fbs_entitlement_receipt`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Entitlement receipts are immutable';
END$$

CREATE TRIGGER IF NOT EXISTS `trg_entitlement_receipt_no_delete`
    BEFORE DELETE ON `fbs_entitlement_receipt`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Entitlement receipts are immutable';
END$$

CALL `u3w_finalize_independent_board_plan_policy_20260722`()$$

DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_plan_policy_20260722`$$
DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_plan_policy_20260722`$$

DELIMITER ;
