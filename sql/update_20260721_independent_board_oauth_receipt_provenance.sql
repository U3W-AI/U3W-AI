-- ============================================================
-- FBSir Independent Board OAuth TOKEN_FAMILY_CREATED provenance
-- Migration: 20260721_independent_board_oauth_receipt_provenance_v1
-- Public manifest step: public_init_034
-- Target: exact MySQL 8.0.30 or 8.4.8 metadata baselines only
-- Scope: one semantic TOKEN_FAMILY_CREATED receipt per token family.
-- Precondition: public_init_033 and its exact internal receipt exist.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_oauth_receipt_provenance_20260721`$$
CREATE PROCEDURE `u3w_migrate_independent_board_oauth_receipt_provenance_20260721`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE current_connection BIGINT DEFAULT 0;
    DECLARE server_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE migration_receipt_count INT DEFAULT 0;
    DECLARE migration_state VARCHAR(255) DEFAULT NULL;
    DECLARE foundation_receipt_count INT DEFAULT 0;
    DECLARE target_table_count INT DEFAULT 0;
    DECLARE target_base_table_count INT DEFAULT 0;
    DECLARE target_engine_count INT DEFAULT 0;
    DECLARE target_collation_count INT DEFAULT 0;
    DECLARE target_column_count INT DEFAULT 0;
    DECLARE target_generated_column_count INT DEFAULT 0;
    DECLARE family_created_slot_name_count INT DEFAULT 0;
    DECLARE family_created_slot_contract_count INT DEFAULT 0;
    DECLARE target_index_count INT DEFAULT 0;
    DECLARE family_created_index_name_count INT DEFAULT 0;
    DECLARE family_created_index_contract_count INT DEFAULT 0;
    DECLARE target_foreign_key_count INT DEFAULT 0;
    DECLARE target_foreign_key_column_count INT DEFAULT 0;
    DECLARE target_check_count INT DEFAULT 0;
    DECLARE target_enforced_check_count INT DEFAULT 0;
    DECLARE target_trigger_count INT DEFAULT 0;
    DECLARE target_trigger_contract_count INT DEFAULT 0;
    DECLARE invalid_lineage_count INT DEFAULT 0;
    DECLARE duplicate_family_count INT DEFAULT 0;
    DECLARE target_column_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_index_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_foreign_key_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_check_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE expected_column_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE expected_index_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE expected_foreign_key_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE expected_successor_check_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE original_group_concat_max_len BIGINT UNSIGNED DEFAULT 0;
    DECLARE group_concat_limit_changed TINYINT DEFAULT 0;
    DECLARE migration_stage VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT 'preflight';
    DECLARE diagnostic_message VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE diagnostic_errno INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 diagnostic_errno = MYSQL_ERRNO;
        ROLLBACK;
        IF group_concat_limit_changed = 1 THEN
            SET SESSION group_concat_max_len = original_group_concat_max_len;
            SET group_concat_limit_changed = 0;
        END IF;
        IF migration_lock_acquired = 1 THEN
            SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
            IF migration_lock_owner = CONNECTION_ID() THEN
                DO RELEASE_LOCK(migration_lock_name);
            END IF;
        END IF;
        IF diagnostic_errno = 1267 THEN
            RESIGNAL SET MESSAGE_TEXT = migration_stage;
        END IF;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance migration requires an explicit target database';
    END IF;

    SET server_version = VERSION();
    IF INSTR(LOWER(VERSION()), 'mariadb') > 0
       OR CAST(server_version AS BINARY) NOT IN (
           CAST('8.0.30' AS BINARY), CAST('8.4.8' AS BINARY))
       OR CAST(@@version_comment AS BINARY) <>
          CAST('MySQL Community Server - GPL' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance supports exact MySQL 8.0.30 or 8.4.8 baselines';
    END IF;

    SET expected_foreign_key_contract_digest = CASE server_version
        WHEN '8.0.30' THEN '016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539'
        WHEN '8.4.8' THEN '3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f'
        ELSE NULL
    END;
    SET expected_successor_check_contract_digest = CASE server_version
        WHEN '8.0.30' THEN 'd8878ff64c7e897ddd8d42db6f0afd1a264d2cc8c1794d155051f0cd1638103b'
        WHEN '8.4.8' THEN 'd8878ff64c7e897ddd8d42db6f0afd1a264d2cc8c1794d155051f0cd1638103b'
        ELSE NULL
    END;

    SET original_group_concat_max_len = @@SESSION.group_concat_max_len;
    IF original_group_concat_max_len < 1048576 THEN
        SET SESSION group_concat_max_len = 1048576;
        SET group_concat_limit_changed = 1;
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260721_independent_board_oauth_receipt_provenance_v1'),
        256
    );
    IF CHAR_LENGTH(migration_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance lock name must be a stable 64-character digest';
    END IF;
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the Independent Board OAuth provenance migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name), CONNECTION_ID()
      INTO migration_lock_owner, current_connection;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance migration lock is not owned by the current connection';
    END IF;

    SET migration_stage = 'foundation-receipt-audit';
    SELECT COUNT(*) INTO foundation_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_foundation_v1'
      AND `description` = 'Independent Board OAuth client, authorization, token family and immutable receipt tables';
    IF foundation_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance requires the exact completed public_init_033 foundation';
    END IF;

    SELECT COUNT(*), MAX(`description`)
      INTO migration_receipt_count, migration_state
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_receipt_provenance_v1';
    IF migration_receipt_count > 1
       OR (migration_receipt_count = 1
           AND CAST(migration_state AS BINARY) NOT IN (
               CAST('RUNNING:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness' AS BINARY),
               CAST('APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness' AS BINARY))) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance migration state is invalid';
    END IF;

    SET migration_stage = 'lineage-preflight';
    SELECT COUNT(*) INTO invalid_lineage_count
    FROM `fbs_oauth_receipt` r
    LEFT JOIN `fbs_oauth_token_family` f
      ON f.`family_id` = r.`family_id`
     AND f.`client_id` = r.`client_id`
     AND f.`principal_subject_digest` = r.`principal_subject_digest`
     AND f.`enterprise_id` = r.`enterprise_id`
     AND f.`member_id` = r.`member_id`
     AND f.`user_id` = r.`user_id`
    LEFT JOIN `fbs_oauth_authorization_code` c
      ON c.`id` = r.`authorization_code_id`
     AND c.`client_id` = r.`client_id`
     AND c.`principal_subject_digest` = r.`principal_subject_digest`
     AND c.`enterprise_id` = r.`enterprise_id`
     AND c.`member_id` = r.`member_id`
     AND c.`user_id` = r.`user_id`
    WHERE r.`action` = 'TOKEN_FAMILY_CREATED'
      AND (r.`family_id` IS NULL
        OR r.`client_id` IS NULL
        OR r.`authorization_code_id` IS NULL
        OR r.`principal_subject_digest` IS NULL
        OR r.`enterprise_id` IS NULL
        OR r.`member_id` IS NULL
        OR r.`user_id` IS NULL
        OR r.`authorization_request_id` IS NOT NULL
        OR r.`token_id` IS NOT NULL
        OR r.`binding_id` IS NOT NULL
        OR f.`id` IS NULL
        OR c.`id` IS NULL
        OR f.`origin_authorization_code_id` <> r.`authorization_code_id`);
    SELECT COUNT(*) INTO duplicate_family_count
    FROM (
        SELECT `family_id`
        FROM `fbs_oauth_receipt`
        WHERE `action` = 'TOKEN_FAMILY_CREATED'
        GROUP BY `family_id`
        HAVING COUNT(*) > 1
    ) duplicate_families;
    IF invalid_lineage_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'TOKEN_FAMILY_CREATED receipt lineage is missing or drifted';
    END IF;
    IF duplicate_family_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate TOKEN_FAMILY_CREATED receipt families require reviewed recovery';
    END IF;

    SET migration_stage = 'shape-state-audit';
    SELECT COUNT(*),
           SUM(table_type = 'BASE TABLE'),
           SUM(engine = 'InnoDB'),
           SUM(table_collation = 'utf8mb4_unicode_ci')
      INTO target_table_count, target_base_table_count,
           target_engine_count, target_collation_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*),
           SUM(column_name = 'family_created_slot'),
           SUM(column_name = 'family_created_slot'
               AND column_type = 'varchar(128)'
               AND is_nullable = 'YES'
               AND character_set_name = 'ascii'
               AND collation_name = 'ascii_bin'
               AND extra = 'STORED GENERATED'
               AND generation_expression <> '')
      INTO target_generated_column_count,
           family_created_slot_name_count,
           family_created_slot_contract_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_oauth_token_family' AND column_name = 'lifecycle_slot'
            AND extra = 'STORED GENERATED' AND generation_expression <> '')
        OR (table_name = 'fbs_oauth_token' AND column_name = 'active_refresh_slot'
            AND extra = 'STORED GENERATED' AND generation_expression <> '')
        OR (table_name = 'fbs_oauth_receipt' AND column_name = 'family_created_slot'));
    SELECT COUNT(*) INTO target_index_count
    FROM (
        SELECT table_name, index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_oauth_client', 'fbs_oauth_authorization_request',
              'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
              'fbs_oauth_token', 'fbs_oauth_receipt'
          )
        GROUP BY table_name, index_name
    ) target_indexes;
    SELECT COUNT(*) INTO family_created_index_name_count
    FROM (
        SELECT table_name, index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'fbs_oauth_receipt'
          AND index_name = 'uk_oauth_receipt_family_created_slot'
        GROUP BY table_name, index_name
    ) named_indexes;
    SELECT COUNT(*) INTO family_created_index_contract_count
    FROM (
        SELECT table_name, index_name, non_unique, index_type,
               MIN(is_visible) AS visibility,
               COUNT(*) AS key_part_count,
               SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
               SUM(CASE WHEN sub_part IS NULL THEN 0 ELSE 1 END) AS partial_columns,
               GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                            ORDER BY seq_in_index SEPARATOR ',') AS column_signature
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'fbs_oauth_receipt'
          AND index_name = 'uk_oauth_receipt_family_created_slot'
        GROUP BY table_name, index_name, non_unique, index_type
    ) exact_index
    WHERE non_unique = 0 AND index_type = 'BTREE'
      AND visibility = 'YES' AND key_part_count = 1
      AND non_column_key_parts = 0 AND partial_columns = 0
      AND CAST(column_signature AS BINARY) = CAST('family_created_slot:A' AS BINARY);

    SELECT COUNT(*) INTO target_foreign_key_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND unique_constraint_schema = DATABASE()
      AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_foreign_key_column_count
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND referenced_table_schema = DATABASE()
      AND referenced_table_name IS NOT NULL
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*), SUM(enforced = 'YES')
      INTO target_check_count, target_enforced_check_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_type = 'CHECK'
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_trigger_contract_count
    FROM (
        SELECT trigger_name, event_object_table, event_manipulation,
               action_timing, action_orientation,
               LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                   action_statement, '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                   CHAR(13), ''), CHAR(92), '')) AS normalized_action
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND event_object_table IN (
              'fbs_oauth_client', 'fbs_oauth_authorization_request',
              'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
              'fbs_oauth_token', 'fbs_oauth_receipt'
          )
          AND action_condition IS NULL
    ) receipt_triggers
    WHERE event_object_table = 'fbs_oauth_receipt'
      AND action_timing = 'BEFORE' AND action_orientation = 'ROW'
      AND CAST(normalized_action AS BINARY) = CAST(
          'signalsqlstate''45000''setmessage_text=''oauthreceiptsareimmutable'''
          AS BINARY)
      AND ((trigger_name = 'trg_oauth_receipt_no_update' AND event_manipulation = 'UPDATE')
        OR (trigger_name = 'trg_oauth_receipt_no_delete' AND event_manipulation = 'DELETE'));

    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(table_name AS BINARY)),
               '|O:', LPAD(ordinal_position, 3, '0'),
               '|N:', HEX(CAST(column_name AS BINARY)),
               '|Y:', HEX(CAST(column_type AS BINARY)),
               '|U:', HEX(CAST(is_nullable AS BINARY)),
               '|D:', IF(column_default IS NULL, 'N', CONCAT('V:', HEX(CAST(column_default AS BINARY)))),
               '|C:', IF(character_set_name IS NULL, 'N', CONCAT('V:', HEX(CAST(character_set_name AS BINARY)))),
               '|L:', IF(collation_name IS NULL, 'N', CONCAT('V:', HEX(CAST(collation_name AS BINARY)))),
               '|E:', HEX(CAST(extra AS BINARY)),
               '|G:', IF(generation_expression IS NULL, 'N', CONCAT('V:', HEX(CAST(generation_expression AS BINARY)))))
               ORDER BY table_name, ordinal_position SEPARATOR 0x0A), 256)
      INTO target_column_contract_digest
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(table_name AS BINARY)),
               '|I:', HEX(CAST(index_name AS BINARY)),
               '|U:', non_unique,
               '|Y:', HEX(CAST(index_type AS BINARY)),
               '|V:', HEX(CAST(is_visible AS BINARY)),
               '|S:', seq_in_index,
               '|N:', IF(column_name IS NULL, 'N', CONCAT('V:', HEX(CAST(column_name AS BINARY)))),
               '|X:', IF(expression IS NULL, 'N', CONCAT('V:', HEX(CAST(expression AS BINARY)))),
               '|C:', IF(collation IS NULL, 'N', CONCAT('V:', HEX(CAST(collation AS BINARY)))),
               '|P:', IF(sub_part IS NULL, 'N', CONCAT('V:', sub_part)),
               '|Q:', HEX(CAST(nullable AS BINARY)))
               ORDER BY table_name, index_name, seq_in_index SEPARATOR 0x0A), 256)
      INTO target_index_contract_digest
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
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
               '|I:', IF(kcu.position_in_unique_constraint IS NULL, 'N', CONCAT('V:', kcu.position_in_unique_constraint)))
               ORDER BY rc.table_name, rc.constraint_name, kcu.ordinal_position SEPARATOR 0x0A), 256)
      INTO target_foreign_key_contract_digest
    FROM information_schema.referential_constraints rc
    INNER JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_schema = rc.constraint_schema
     AND kcu.table_name = rc.table_name
     AND kcu.constraint_name = rc.constraint_name
    WHERE rc.constraint_schema = DATABASE()
      AND rc.unique_constraint_schema = DATABASE()
      AND kcu.referenced_table_schema = DATABASE()
      AND rc.table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(table_name AS BINARY)),
               '|C:', HEX(CAST(constraint_name AS BINARY)),
               '|E:', HEX(CAST(enforced AS BINARY)),
               '|X:', HEX(CAST(check_clause AS BINARY)))
               ORDER BY table_name, constraint_name SEPARATOR 0x0A), 256)
      INTO target_check_contract_digest
    FROM (
        SELECT tc.table_name, tc.constraint_name, tc.enforced, cc.check_clause
        FROM information_schema.table_constraints tc
        INNER JOIN information_schema.check_constraints cc
          ON cc.constraint_schema = tc.constraint_schema
         AND cc.constraint_name = tc.constraint_name
        WHERE tc.constraint_schema = DATABASE()
          AND tc.constraint_type = 'CHECK'
          AND tc.table_name IN (
              'fbs_oauth_client', 'fbs_oauth_authorization_request',
              'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
              'fbs_oauth_token', 'fbs_oauth_receipt'
          )
    ) exact_checks;

    IF target_table_count <> 6 OR target_base_table_count <> 6
       OR target_engine_count <> 6 OR target_collation_count <> 6
       OR target_foreign_key_count <> 14 OR target_foreign_key_column_count <> 57
       OR target_check_count <> 33 OR target_enforced_check_count <> 33
       OR target_trigger_count <> 2 OR target_trigger_contract_count <> 2
       OR target_foreign_key_contract_digest IS NULL
       OR CAST(target_foreign_key_contract_digest AS BINARY) <>
          CAST(expected_foreign_key_contract_digest AS BINARY)
       OR target_check_contract_digest IS NULL
       OR (CAST(target_check_contract_digest AS BINARY) <>
              CAST('51f71711dfc17b5a6741fbbd1bd3d31ae7d4761e9da5722c614eb199a81afb11' AS BINARY)
           AND CAST(target_check_contract_digest AS BINARY) <>
              CAST(expected_successor_check_contract_digest AS BINARY)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth foundation metadata has drifted before provenance migration';
    END IF;

    IF family_created_slot_name_count = 0 AND family_created_index_name_count = 0 THEN
        IF target_column_count <> 144 OR target_generated_column_count <> 2
           OR target_index_count <> 52
           OR target_column_contract_digest IS NULL
           OR CAST(target_column_contract_digest AS BINARY) <>
              CAST('89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0' AS BINARY)
           OR target_index_contract_digest IS NULL
           OR CAST(target_index_contract_digest AS BINARY) <>
              CAST('1a3122b7238b444939a7981ccd759322bd168f351131e4d9c0a16346242c3d79' AS BINARY)
           OR (migration_receipt_count = 1
               AND CAST(migration_state AS BINARY) <>
                   CAST('RUNNING:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness' AS BINARY)) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Independent Board OAuth public_init_033 shape or provenance state has drifted';
        END IF;

        IF migration_receipt_count = 0 THEN
            START TRANSACTION;
            INSERT INTO `u3w_schema_migration` (`version`, `description`)
            VALUES (
                '20260721_independent_board_oauth_receipt_provenance_v1',
                'RUNNING:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness'
            );
            COMMIT;
            SET migration_receipt_count = 1;
            SET migration_state =
                'RUNNING:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness';
        END IF;

        SET migration_stage = 'provenance-ddl';
        ALTER TABLE `fbs_oauth_receipt`
            ADD COLUMN `family_created_slot`
                VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin
                GENERATED ALWAYS AS (
                    CASE
                        WHEN `action` = 'TOKEN_FAMILY_CREATED' THEN `family_id`
                        ELSE NULL
                    END
                ) STORED,
            ADD UNIQUE KEY `uk_oauth_receipt_family_created_slot`
                (`family_created_slot`);
    ELSEIF family_created_slot_name_count <> 1
       OR family_created_slot_contract_count <> 1
       OR family_created_index_name_count <> 1
       OR family_created_index_contract_count <> 1
       OR migration_receipt_count <> 1
       OR CAST(migration_state AS BINARY) NOT IN (
           CAST('RUNNING:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness' AS BINARY),
           CAST('APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness' AS BINARY)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance DDL is partial, orphaned or drifted';
    END IF;

    -- Final current-read: recompute every raw metadata surface after the
    -- implicit ALTER commit and immediately before promoting RUNNING to APPLIED.
    SET migration_stage = 'final-metadata-current-read';
    SELECT COUNT(*),
           SUM(table_type = 'BASE TABLE'),
           SUM(engine = 'InnoDB'),
           SUM(table_collation = 'utf8mb4_unicode_ci')
      INTO target_table_count, target_base_table_count,
           target_engine_count, target_collation_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*),
           SUM(column_name = 'family_created_slot'
               AND column_type = 'varchar(128)'
               AND is_nullable = 'YES'
                AND character_set_name = 'ascii'
                AND collation_name = 'ascii_bin'
                AND extra = 'STORED GENERATED'
                AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                        REPLACE(REPLACE(generation_expression, '`', ''), ' ', ''),
                        CHAR(9), ''), CHAR(10), ''), CHAR(13), ''), CHAR(92), ''),
                        '(', ''), ')', '')) IN (
                    'casewhenaction=_utf8mb4''token_family_created''thenfamily_idelsenullend',
                    'casewhenaction=_ascii''token_family_created''thenfamily_idelsenullend',
                    'casewhenaction=''token_family_created''thenfamily_idelsenullend'
                ))
      INTO target_generated_column_count, family_created_slot_contract_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_oauth_token_family' AND column_name = 'lifecycle_slot'
            AND extra = 'STORED GENERATED' AND generation_expression <> '')
        OR (table_name = 'fbs_oauth_token' AND column_name = 'active_refresh_slot'
            AND extra = 'STORED GENERATED' AND generation_expression <> '')
        OR (table_name = 'fbs_oauth_receipt' AND column_name = 'family_created_slot'));
    SELECT COUNT(*) INTO target_index_count
    FROM (
        SELECT table_name, index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_oauth_client', 'fbs_oauth_authorization_request',
              'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
              'fbs_oauth_token', 'fbs_oauth_receipt'
          )
        GROUP BY table_name, index_name
    ) target_indexes;
    SELECT COUNT(*) INTO family_created_index_contract_count
    FROM (
        SELECT table_name, index_name, non_unique, index_type,
               MIN(is_visible) AS visibility,
               COUNT(*) AS key_part_count,
               SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
               SUM(CASE WHEN sub_part IS NULL THEN 0 ELSE 1 END) AS partial_columns,
               GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                            ORDER BY seq_in_index SEPARATOR ',') AS column_signature
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'fbs_oauth_receipt'
          AND index_name = 'uk_oauth_receipt_family_created_slot'
        GROUP BY table_name, index_name, non_unique, index_type
    ) exact_index
    WHERE non_unique = 0 AND index_type = 'BTREE'
      AND visibility = 'YES' AND key_part_count = 1
      AND non_column_key_parts = 0 AND partial_columns = 0
      AND CAST(column_signature AS BINARY) = CAST('family_created_slot:A' AS BINARY);

    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(table_name AS BINARY)),
               '|O:', LPAD(ordinal_position, 3, '0'),
               '|N:', HEX(CAST(column_name AS BINARY)),
               '|Y:', HEX(CAST(column_type AS BINARY)),
               '|U:', HEX(CAST(is_nullable AS BINARY)),
               '|D:', IF(column_default IS NULL, 'N', CONCAT('V:', HEX(CAST(column_default AS BINARY)))),
               '|C:', IF(character_set_name IS NULL, 'N', CONCAT('V:', HEX(CAST(character_set_name AS BINARY)))),
               '|L:', IF(collation_name IS NULL, 'N', CONCAT('V:', HEX(CAST(collation_name AS BINARY)))),
               '|E:', HEX(CAST(extra AS BINARY)),
               '|G:', IF(generation_expression IS NULL, 'N', CONCAT('V:', HEX(CAST(generation_expression AS BINARY)))))
               ORDER BY table_name, ordinal_position SEPARATOR 0x0A), 256)
      INTO target_column_contract_digest
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (table_name = 'fbs_oauth_receipt'
               AND column_name = 'family_created_slot');
    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(table_name AS BINARY)),
               '|I:', HEX(CAST(index_name AS BINARY)),
               '|U:', non_unique,
               '|Y:', HEX(CAST(index_type AS BINARY)),
               '|V:', HEX(CAST(is_visible AS BINARY)),
               '|S:', seq_in_index,
               '|N:', IF(column_name IS NULL, 'N', CONCAT('V:', HEX(CAST(column_name AS BINARY)))),
               '|X:', IF(expression IS NULL, 'N', CONCAT('V:', HEX(CAST(expression AS BINARY)))),
               '|C:', IF(collation IS NULL, 'N', CONCAT('V:', HEX(CAST(collation AS BINARY)))),
               '|P:', IF(sub_part IS NULL, 'N', CONCAT('V:', sub_part)),
               '|Q:', HEX(CAST(nullable AS BINARY)))
               ORDER BY table_name, index_name, seq_in_index SEPARATOR 0x0A), 256)
      INTO target_index_contract_digest
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (table_name = 'fbs_oauth_receipt'
               AND index_name = 'uk_oauth_receipt_family_created_slot');
    SELECT COUNT(*) INTO target_foreign_key_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND unique_constraint_schema = DATABASE()
      AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_foreign_key_column_count
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND referenced_table_schema = DATABASE()
      AND referenced_table_name IS NOT NULL
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*), SUM(enforced = 'YES')
      INTO target_check_count, target_enforced_check_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_type = 'CHECK'
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_trigger_contract_count
    FROM (
        SELECT trigger_name, event_object_table, event_manipulation,
               action_timing, action_orientation,
               LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                   action_statement, '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                   CHAR(13), ''), CHAR(92), '')) AS normalized_action
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND event_object_table IN (
              'fbs_oauth_client', 'fbs_oauth_authorization_request',
              'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
              'fbs_oauth_token', 'fbs_oauth_receipt'
          )
          AND action_condition IS NULL
    ) receipt_triggers
    WHERE event_object_table = 'fbs_oauth_receipt'
      AND action_timing = 'BEFORE' AND action_orientation = 'ROW'
      AND CAST(normalized_action AS BINARY) = CAST(
          'signalsqlstate''45000''setmessage_text=''oauthreceiptsareimmutable'''
          AS BINARY)
      AND ((trigger_name = 'trg_oauth_receipt_no_update' AND event_manipulation = 'UPDATE')
        OR (trigger_name = 'trg_oauth_receipt_no_delete' AND event_manipulation = 'DELETE'));
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
               '|I:', IF(kcu.position_in_unique_constraint IS NULL, 'N', CONCAT('V:', kcu.position_in_unique_constraint)))
               ORDER BY rc.table_name, rc.constraint_name, kcu.ordinal_position SEPARATOR 0x0A), 256)
      INTO target_foreign_key_contract_digest
    FROM information_schema.referential_constraints rc
    INNER JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_schema = rc.constraint_schema
     AND kcu.table_name = rc.table_name
     AND kcu.constraint_name = rc.constraint_name
    WHERE rc.constraint_schema = DATABASE()
      AND rc.unique_constraint_schema = DATABASE()
      AND kcu.referenced_table_schema = DATABASE()
      AND rc.table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(table_name AS BINARY)),
               '|C:', HEX(CAST(constraint_name AS BINARY)),
               '|E:', HEX(CAST(enforced AS BINARY)),
               '|X:', HEX(CAST(check_clause AS BINARY)))
               ORDER BY table_name, constraint_name SEPARATOR 0x0A), 256)
      INTO target_check_contract_digest
    FROM (
        SELECT tc.table_name, tc.constraint_name, tc.enforced, cc.check_clause
        FROM information_schema.table_constraints tc
        INNER JOIN information_schema.check_constraints cc
          ON cc.constraint_schema = tc.constraint_schema
         AND cc.constraint_name = tc.constraint_name
        WHERE tc.constraint_schema = DATABASE()
          AND tc.constraint_type = 'CHECK'
          AND tc.table_name IN (
              'fbs_oauth_client', 'fbs_oauth_authorization_request',
              'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
              'fbs_oauth_token', 'fbs_oauth_receipt'
          )
    ) exact_checks;

    -- 034 is decomposed into the byte-stable 033 foundation subset plus the
    -- exact generated column and unique-index contracts checked above. This
    -- avoids accepting an unproved successor-wide digest while retaining full
    -- coverage of every metadata object.
    SET expected_column_contract_digest =
        '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0';
    SET expected_index_contract_digest =
        '1a3122b7238b444939a7981ccd759322bd168f351131e4d9c0a16346242c3d79';
    IF target_table_count <> 6 OR target_base_table_count <> 6
       OR target_engine_count <> 6 OR target_collation_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth provenance final table metadata current-read failed';
    ELSEIF target_column_count <> 145 OR target_generated_column_count <> 3
       OR family_created_slot_contract_count <> 1 THEN
        SELECT CONCAT('OAuth slot=', LEFT(LOWER(REPLACE(REPLACE(REPLACE(REPLACE(
                   REPLACE(REPLACE(REPLACE(REPLACE(generation_expression, '`', ''),
                   ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''),
                   CHAR(92), ''), '(', ''), ')', '')), 100))
          INTO diagnostic_message
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'fbs_oauth_receipt'
          AND column_name = 'family_created_slot';
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = diagnostic_message;
    ELSEIF target_index_count <> 53 OR family_created_index_contract_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth provenance final index shape current-read failed';
    ELSEIF target_foreign_key_count <> 14 OR target_foreign_key_column_count <> 57 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth provenance final foreign-key count current-read failed';
    ELSEIF target_check_count <> 33 OR target_enforced_check_count <> 33 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth provenance final CHECK count current-read failed';
    ELSEIF target_trigger_count <> 2 OR target_trigger_contract_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth provenance final trigger contract current-read failed';
    ELSEIF target_column_contract_digest IS NULL
       OR CAST(target_column_contract_digest AS BINARY) <>
          CAST(expected_column_contract_digest AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth provenance final foundation-column digest failed';
    ELSEIF target_index_contract_digest IS NULL
       OR CAST(target_index_contract_digest AS BINARY) <>
          CAST(expected_index_contract_digest AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth provenance final foundation-index digest failed';
    ELSEIF target_foreign_key_contract_digest IS NULL
       OR CAST(target_foreign_key_contract_digest AS BINARY) <>
          CAST(expected_foreign_key_contract_digest AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth provenance final foreign-key digest failed';
    ELSEIF target_check_contract_digest IS NULL
       OR CAST(target_check_contract_digest AS BINARY) <>
          CAST(expected_successor_check_contract_digest AS BINARY) THEN
        SET diagnostic_message = CONCAT(
            'OAuth CHECK target=',
            COALESCE(target_check_contract_digest, 'NULL'),
            ';tlen=', COALESCE(CHAR_LENGTH(target_check_contract_digest), -1),
            ';elen=', COALESCE(CHAR_LENGTH(expected_successor_check_contract_digest), -1));
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = diagnostic_message;
    END IF;

    SELECT COUNT(*) INTO invalid_lineage_count
    FROM `fbs_oauth_receipt` r
    LEFT JOIN `fbs_oauth_token_family` f
      ON f.`family_id` = r.`family_id`
     AND f.`client_id` = r.`client_id`
     AND f.`principal_subject_digest` = r.`principal_subject_digest`
     AND f.`enterprise_id` = r.`enterprise_id`
     AND f.`member_id` = r.`member_id`
     AND f.`user_id` = r.`user_id`
    LEFT JOIN `fbs_oauth_authorization_code` c
      ON c.`id` = r.`authorization_code_id`
     AND c.`client_id` = r.`client_id`
     AND c.`principal_subject_digest` = r.`principal_subject_digest`
     AND c.`enterprise_id` = r.`enterprise_id`
     AND c.`member_id` = r.`member_id`
     AND c.`user_id` = r.`user_id`
    WHERE r.`action` = 'TOKEN_FAMILY_CREATED'
      AND (r.`family_id` IS NULL
        OR r.`client_id` IS NULL
        OR r.`authorization_code_id` IS NULL
        OR r.`principal_subject_digest` IS NULL
        OR r.`enterprise_id` IS NULL
        OR r.`member_id` IS NULL
        OR r.`user_id` IS NULL
        OR r.`authorization_request_id` IS NOT NULL
        OR r.`token_id` IS NOT NULL
        OR r.`binding_id` IS NOT NULL
        OR r.`family_created_slot` IS NULL
        OR CAST(r.`family_created_slot` AS BINARY) <> CAST(r.`family_id` AS BINARY)
        OR f.`family_id` IS NULL
        OR c.`id` IS NULL
        OR f.`origin_authorization_code_id` <> r.`authorization_code_id`);
    SELECT COUNT(*) INTO duplicate_family_count
    FROM (
        SELECT `family_created_slot`
        FROM `fbs_oauth_receipt`
        WHERE `action` = 'TOKEN_FAMILY_CREATED'
        GROUP BY `family_created_slot`
        HAVING COUNT(*) > 1
    ) duplicate_families;
    IF invalid_lineage_count <> 0 OR duplicate_family_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance final lineage current-read failed';
    END IF;

    SET migration_stage = 'receipt-finalization';
    START TRANSACTION;
    SELECT `description` INTO migration_state
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_receipt_provenance_v1'
    FOR UPDATE;
    IF CAST(migration_state AS BINARY) =
       CAST('RUNNING:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness' AS BINARY) THEN
        UPDATE `u3w_schema_migration`
        SET `description` = 'APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness',
            `applied_at` = CURRENT_TIMESTAMP
        WHERE `version` = '20260721_independent_board_oauth_receipt_provenance_v1'
          AND `description` = 'RUNNING:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Independent Board OAuth provenance receipt promotion lost its expected state';
        END IF;
    ELSEIF CAST(migration_state AS BINARY) <>
           CAST('APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance receipt is missing or drifted';
    END IF;
    SELECT COUNT(*) INTO migration_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_receipt_provenance_v1'
      AND `description` = 'APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness';
    IF migration_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance APPLIED receipt is not exact';
    END IF;
    COMMIT;

    IF group_concat_limit_changed = 1 THEN
        SET SESSION group_concat_max_len = original_group_concat_max_len;
        SET group_concat_limit_changed = 0;
    END IF;
    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth provenance migration advisory lock release failed';
    END IF;
    SET migration_lock_acquired = 0;
END$$

CALL `u3w_migrate_independent_board_oauth_receipt_provenance_20260721`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_oauth_receipt_provenance_20260721`$$

DELIMITER ;
