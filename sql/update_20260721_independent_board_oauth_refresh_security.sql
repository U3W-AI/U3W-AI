-- ============================================================
-- FBSir Independent Board OAuth refresh-token security receipts
-- Migration: 20260721_independent_board_oauth_refresh_security_v1
-- Public manifest step: public_init_036
-- Target: exact MySQL Community 8.0.30 or 8.4.8 metadata baselines only
-- Scope: strict consent NULL pairing; generation-bound, causally linked
--        TOKEN_FAMILY_ROTATED and REFRESH_REPLAY_DETECTED receipt v2; and
--        exact lock-order support indexes for refresh and binding workflows.
-- Preconditions: exact APPLIED public_init_033, public_init_034 and
--                public_init_035 shapes.
-- ============================================================

-- Fail before any persistent helper or business-schema DDL on an unsupported
-- server. PREPARE/EXECUTE is session state only and permits a conditional
-- top-level SIGNAL without first creating a stored routine.
SET @u3w_ib_oauth_refresh_profile_guard = IF(
    CAST(VERSION() AS BINARY) IN (CAST('8.0.30' AS BINARY), CAST('8.4.8' AS BINARY))
    AND INSTR(LOWER(VERSION()), 'mariadb') = 0
    AND CAST(@@version_comment AS BINARY) =
        CAST('MySQL Community Server - GPL' AS BINARY),
    'DO 0',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''Independent Board OAuth refresh security supports exact MySQL baselines only'''
);
PREPARE u3w_ib_oauth_refresh_profile_guard_stmt
    FROM @u3w_ib_oauth_refresh_profile_guard;
EXECUTE u3w_ib_oauth_refresh_profile_guard_stmt;
DEALLOCATE PREPARE u3w_ib_oauth_refresh_profile_guard_stmt;
SET @u3w_ib_oauth_refresh_profile_guard = NULL;

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_assert_ib_oauth_refresh_security_20260721`$$
CREATE PROCEDURE `u3w_assert_ib_oauth_refresh_security_20260721`(
    OUT request_stage_complete TINYINT,
    OUT token_support_complete TINYINT,
    OUT causation_support_complete TINYINT,
    OUT connector_support_complete TINYINT,
    OUT receipt_stage_complete TINYINT
)
BEGIN
    DECLARE server_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE table_count INT DEFAULT 0;
    DECLARE base_table_count INT DEFAULT 0;
    DECLARE engine_count INT DEFAULT 0;
    DECLARE collation_count INT DEFAULT 0;
    DECLARE total_column_count INT DEFAULT 0;
    DECLARE total_generated_column_count INT DEFAULT 0;
    DECLARE total_index_count INT DEFAULT 0;
    DECLARE total_foreign_key_count INT DEFAULT 0;
    DECLARE total_foreign_key_column_count INT DEFAULT 0;
    DECLARE total_check_count INT DEFAULT 0;
    DECLARE total_enforced_check_count INT DEFAULT 0;
    DECLARE total_trigger_count INT DEFAULT 0;
    DECLARE connector_table_count INT DEFAULT 0;
    DECLARE connector_total_index_count INT DEFAULT 0;
    DECLARE legacy_connector_index_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE legacy_column_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE legacy_index_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE legacy_foreign_key_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE legacy_check_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE expected_legacy_column_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE expected_legacy_foreign_key_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE expected_legacy_check_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE request_check_name_count INT DEFAULT 0;
    DECLARE request_check_weak_count INT DEFAULT 0;
    DECLARE request_check_strict_count INT DEFAULT 0;
    DECLARE token_index_name_count INT DEFAULT 0;
    DECLARE token_index_contract_count INT DEFAULT 0;
    DECLARE causation_index_name_count INT DEFAULT 0;
    DECLARE causation_index_contract_count INT DEFAULT 0;
    DECLARE connector_index_name_count INT DEFAULT 0;
    DECLARE connector_index_contract_count INT DEFAULT 0;
    DECLARE receipt_column_name_count INT DEFAULT 0;
    DECLARE receipt_column_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE receipt_index_name_count INT DEFAULT 0;
    DECLARE receipt_index_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE receipt_foreign_key_name_count INT DEFAULT 0;
    DECLARE receipt_foreign_key_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE receipt_check_name_count INT DEFAULT 0;
    DECLARE receipt_check_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE immutable_receipt_trigger_count INT DEFAULT 0;
    DECLARE consent_intent_trigger_count INT DEFAULT 0;
    DECLARE diagnostic_message VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;

    SET request_stage_complete = 0;
    SET token_support_complete = 0;
    SET causation_support_complete = 0;
    SET connector_support_complete = 0;
    SET receipt_stage_complete = 0;
    SET server_version = VERSION();
    IF INSTR(LOWER(server_version), 'mariadb') > 0
       OR CAST(server_version AS BINARY) NOT IN (
           CAST('8.0.30' AS BINARY), CAST('8.4.8' AS BINARY))
       OR CAST(@@version_comment AS BINARY) <>
          CAST('MySQL Community Server - GPL' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security metadata audit supports exact MySQL baselines only';
    END IF;

    SELECT COUNT(*),
           COALESCE(SUM(table_type = 'BASE TABLE'), 0),
           COALESCE(SUM(engine = 'InnoDB'), 0),
           COALESCE(SUM(table_collation = 'utf8mb4_unicode_ci'), 0)
      INTO table_count, base_table_count, engine_count, collation_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );

    SELECT COUNT(*),
           COALESCE(SUM(extra = 'STORED GENERATED' AND generation_expression <> ''), 0)
      INTO total_column_count, total_generated_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO total_index_count
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
    ) exact_indexes;
    SELECT COUNT(*) INTO total_foreign_key_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO total_foreign_key_column_count
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND referenced_table_schema = DATABASE()
      AND referenced_table_name IS NOT NULL
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*), COALESCE(SUM(enforced = 'YES'), 0)
      INTO total_check_count, total_enforced_check_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_type = 'CHECK'
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO total_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO connector_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB' AND table_collation = 'utf8mb4_unicode_ci'
      AND table_name IN (
          'fbs_connector_binding', 'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      );
    SELECT COUNT(*) INTO connector_total_index_count
    FROM (
        SELECT table_name, index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_connector_binding', 'fbs_connector_binding_scope',
              'fbs_connector_binding_receipt'
          )
        GROUP BY table_name, index_name
    ) connector_indexes;
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
      INTO legacy_connector_index_digest
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_connector_binding', 'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      )
      AND NOT (table_name = 'fbs_connector_binding_receipt'
               AND index_name = 'idx_connector_binding_receipt_lock_order');

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
      INTO legacy_column_digest
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (table_name = 'fbs_oauth_receipt' AND column_name IN (
          'receipt_format_version', 'subject_generation', 'result_generation',
          'causation_receipt_id', 'before_state_digest', 'after_state_digest',
          'subject_token_type', 'security_event_slot'
      ));
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
      INTO legacy_index_digest
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (table_name = 'fbs_oauth_token' AND index_name IN (
          'uk_oauth_token_receipt_generation_type_scope',
          'idx_oauth_token_family_lock_order'
      ))
      AND NOT (table_name = 'fbs_oauth_receipt' AND index_name IN (
          'uk_oauth_receipt_causation_scope', 'idx_oauth_receipt_causation_scope',
          'idx_oauth_receipt_family_client_lock_order',
          'idx_oauth_receipt_refresh_subject', 'uk_oauth_receipt_security_event_slot'
      ));
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
               ORDER BY rc.table_name, rc.constraint_name, kcu.ordinal_position SEPARATOR 0x0A), 256)
      INTO legacy_foreign_key_digest
    FROM information_schema.referential_constraints rc
    INNER JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_schema = rc.constraint_schema
     AND kcu.table_name = rc.table_name
     AND kcu.constraint_name = rc.constraint_name
    WHERE rc.constraint_schema = DATABASE()
      AND rc.table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (rc.table_name = 'fbs_oauth_receipt' AND rc.constraint_name IN (
          'fk_oauth_receipt_refresh_subject', 'fk_oauth_receipt_causation_scope'
      ));
    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(table_name AS BINARY)),
               '|C:', HEX(CAST(constraint_name AS BINARY)),
               '|E:', HEX(CAST(enforced AS BINARY)),
               '|X:', HEX(CAST(check_clause AS BINARY)))
               ORDER BY table_name, constraint_name SEPARATOR 0x0A), 256)
      INTO legacy_check_digest
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
          AND NOT (tc.table_name = 'fbs_oauth_authorization_request'
                   AND tc.constraint_name = 'chk_oauth_request_consent_intent')
          AND NOT (tc.table_name = 'fbs_oauth_receipt' AND tc.constraint_name IN (
              'chk_oauth_receipt_format_version', 'chk_oauth_receipt_v2_shape'
          ))
    ) exact_legacy_checks;

    SELECT COUNT(*),
           COALESCE(SUM(CAST(normalized_clause AS BINARY) = CAST(
               'consent_intentisnullandprincipal_subject_digestisnullorconsent_intentin''first_connect'',''explicit_reauthorization''andprincipal_subject_digestisnotnull'
               AS BINARY)), 0),
           COALESCE(SUM(CAST(normalized_clause AS BINARY) = CAST(
               'consent_intentisnullandprincipal_subject_digestisnullorconsent_intentisnotnullandconsent_intentin''first_connect'',''explicit_reauthorization''andprincipal_subject_digestisnotnull'
               AS BINARY)), 0)
      INTO request_check_name_count, request_check_weak_count, request_check_strict_count
    FROM (
        SELECT LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                   REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause,
                       '_utf8mb4', ''), '_utf8mb3', ''), '_ascii', ''), '_gbk', ''),
                       CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                       CHAR(13), ''), CHAR(92), ''), '(', ''), ')', '')) AS normalized_clause
        FROM information_schema.table_constraints tc
        INNER JOIN information_schema.check_constraints cc
          ON cc.constraint_schema = tc.constraint_schema
         AND cc.constraint_name = tc.constraint_name
        WHERE tc.constraint_schema = DATABASE()
          AND tc.constraint_type = 'CHECK'
          AND tc.enforced = 'YES'
          AND tc.table_name = 'fbs_oauth_authorization_request'
          AND tc.constraint_name = 'chk_oauth_request_consent_intent'
    ) request_check;
    IF request_check_name_count <> 1
       OR request_check_weak_count + request_check_strict_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth request consent-intent CHECK is missing, ambiguous or drifted';
    END IF;
    SET request_stage_complete = request_check_strict_count;

    SELECT COUNT(*), COALESCE(SUM(
               index_type = 'BTREE' AND visibility = 'YES'
               AND non_column_key_parts = 0 AND partial_columns = 0
               AND ((index_name = 'uk_oauth_token_receipt_generation_type_scope'
                     AND non_unique = 0 AND key_part_count = 4
                     AND CAST(column_signature AS BINARY) = CAST(
                         'id:A,family_id:A,generation:A,token_type:A' AS BINARY))
                 OR (index_name = 'idx_oauth_token_family_lock_order'
                     AND non_unique = 1 AND key_part_count = 2
                     AND CAST(column_signature AS BINARY) = CAST(
                         'family_id:A,id:A' AS BINARY)))), 0)
      INTO token_index_name_count, token_index_contract_count
    FROM (
        SELECT index_name, non_unique, index_type, MIN(is_visible) AS visibility,
               COUNT(*) AS key_part_count,
               SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
               SUM(sub_part IS NOT NULL) AS partial_columns,
               GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                   ORDER BY seq_in_index SEPARATOR ',') AS column_signature
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'fbs_oauth_token'
          AND index_name IN (
              'uk_oauth_token_receipt_generation_type_scope',
              'idx_oauth_token_family_lock_order'
          )
        GROUP BY table_name, index_name, non_unique, index_type
    ) token_index;
    IF token_index_name_count = 0 AND token_index_contract_count = 0 THEN
        SET token_support_complete = 0;
    ELSEIF token_index_name_count = 2 AND token_index_contract_count = 2 THEN
        SET token_support_complete = 1;
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-subject token support index is partial or drifted';
    END IF;

    SELECT COUNT(*), COALESCE(SUM(
               index_type = 'BTREE' AND visibility = 'YES'
               AND non_column_key_parts = 0 AND partial_columns = 0
               AND ((index_name = 'uk_oauth_receipt_causation_scope'
                     AND non_unique = 0 AND key_part_count = 3
                     AND CAST(column_signature AS BINARY) = CAST(
                         'receipt_id:A,family_id:A,client_id:A' AS BINARY))
                 OR (index_name = 'idx_oauth_receipt_family_client_lock_order'
                     AND non_unique = 1 AND key_part_count = 3
                     AND CAST(column_signature AS BINARY) = CAST(
                         'family_id:A,client_id:A,id:A' AS BINARY)))), 0)
      INTO causation_index_name_count, causation_index_contract_count
    FROM (
        SELECT index_name, non_unique, index_type, MIN(is_visible) AS visibility,
               COUNT(*) AS key_part_count,
               SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
               SUM(sub_part IS NOT NULL) AS partial_columns,
               GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                   ORDER BY seq_in_index SEPARATOR ',') AS column_signature
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'fbs_oauth_receipt'
          AND index_name IN (
              'uk_oauth_receipt_causation_scope',
              'idx_oauth_receipt_family_client_lock_order'
          )
        GROUP BY table_name, index_name, non_unique, index_type
    ) causation_index;
    IF causation_index_name_count = 0 AND causation_index_contract_count = 0 THEN
        SET causation_support_complete = 0;
    ELSEIF causation_index_name_count = 2 AND causation_index_contract_count = 2 THEN
        SET causation_support_complete = 1;
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth receipt causation support index is partial or drifted';
    END IF;

    SELECT COUNT(*), COALESCE(SUM(
               non_unique = 1 AND index_type = 'BTREE' AND visibility = 'YES'
               AND key_part_count = 2 AND non_column_key_parts = 0
               AND partial_columns = 0
               AND CAST(column_signature AS BINARY) =
                   CAST('binding_id:A,id:A' AS BINARY)), 0)
      INTO connector_index_name_count, connector_index_contract_count
    FROM (
        SELECT non_unique, index_type, MIN(is_visible) AS visibility,
               COUNT(*) AS key_part_count,
               SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
               SUM(sub_part IS NOT NULL) AS partial_columns,
               GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                   ORDER BY seq_in_index SEPARATOR ',') AS column_signature
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'fbs_connector_binding_receipt'
          AND index_name = 'idx_connector_binding_receipt_lock_order'
        GROUP BY table_name, index_name, non_unique, index_type
    ) connector_index;
    IF connector_index_name_count = 0 AND connector_index_contract_count = 0 THEN
        SET connector_support_complete = 0;
    ELSEIF connector_index_name_count = 1 AND connector_index_contract_count = 1 THEN
        SET connector_support_complete = 1;
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding receipt lock-order index is partial or drifted';
    END IF;

    SELECT COUNT(*), SHA2(GROUP_CONCAT(CONCAT(
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
               ORDER BY ordinal_position SEPARATOR 0x0A), 256)
      INTO receipt_column_name_count, receipt_column_digest
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fbs_oauth_receipt'
      AND column_name IN (
          'receipt_format_version', 'subject_generation', 'result_generation',
          'causation_receipt_id', 'before_state_digest', 'after_state_digest',
          'subject_token_type', 'security_event_slot'
      );
    SELECT COUNT(DISTINCT index_name), SHA2(GROUP_CONCAT(CONCAT(
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
               ORDER BY index_name, seq_in_index SEPARATOR 0x0A), 256)
      INTO receipt_index_name_count, receipt_index_digest
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'fbs_oauth_receipt'
      AND index_name IN (
          'idx_oauth_receipt_causation_scope', 'idx_oauth_receipt_refresh_subject',
          'uk_oauth_receipt_security_event_slot'
      );
    SELECT COUNT(DISTINCT rc.constraint_name), SHA2(GROUP_CONCAT(CONCAT(
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
               ORDER BY rc.constraint_name, kcu.ordinal_position SEPARATOR 0x0A), 256)
      INTO receipt_foreign_key_name_count, receipt_foreign_key_digest
    FROM information_schema.referential_constraints rc
    INNER JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_schema = rc.constraint_schema
     AND kcu.table_name = rc.table_name
     AND kcu.constraint_name = rc.constraint_name
    WHERE rc.constraint_schema = DATABASE()
      AND rc.table_name = 'fbs_oauth_receipt'
      AND rc.constraint_name IN (
          'fk_oauth_receipt_refresh_subject', 'fk_oauth_receipt_causation_scope'
      );
    SELECT COUNT(*), SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(tc.table_name AS BINARY)),
               '|C:', HEX(CAST(tc.constraint_name AS BINARY)),
               '|E:', HEX(CAST(tc.enforced AS BINARY)),
               '|X:', HEX(CAST(cc.check_clause AS BINARY)))
               ORDER BY tc.constraint_name SEPARATOR 0x0A), 256)
      INTO receipt_check_name_count, receipt_check_digest
    FROM information_schema.table_constraints tc
    INNER JOIN information_schema.check_constraints cc
      ON cc.constraint_schema = tc.constraint_schema
     AND cc.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = DATABASE()
      AND tc.constraint_type = 'CHECK'
      AND tc.table_name = 'fbs_oauth_receipt'
      AND tc.constraint_name IN (
          'chk_oauth_receipt_format_version', 'chk_oauth_receipt_v2_shape'
      );

    IF receipt_column_name_count = 0
       AND receipt_index_name_count = 0
       AND receipt_foreign_key_name_count = 0
       AND receipt_check_name_count = 0 THEN
        SET receipt_stage_complete = 0;
    ELSEIF receipt_column_name_count = 8
       AND CAST(receipt_column_digest AS BINARY) =
           CAST('b10e1732683f4590814595d37bbbc6b956d25d7647d9f6e8daf5c62e35898724' AS BINARY)
       AND receipt_index_name_count = 3
       AND CAST(receipt_index_digest AS BINARY) =
           CAST('45b458a075c8906b94e09a5b2bf9f9920cd7586e2642d2bb210d817e8ec7e656' AS BINARY)
       AND receipt_foreign_key_name_count = 2
       AND CAST(receipt_foreign_key_digest AS BINARY) =
           CAST('00ea1783aea41e0875c043f00bc85bffa7418f744e12060671baa6ff55150905' AS BINARY)
       AND receipt_check_name_count = 2
       AND CAST(receipt_check_digest AS BINARY) =
           CAST('d30ff1730699536a7dcfcc76ac026a2744bc10a07ea028d01cc5cfea5ddd05ad' AS BINARY) THEN
        SET receipt_stage_complete = 1;
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth receipt-v2 DDL is partial or drifted';
    END IF;

    SELECT COUNT(*) INTO immutable_receipt_trigger_count
    FROM (
        SELECT trigger_name, event_object_table, event_manipulation,
               action_timing, action_orientation, action_condition,
               LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                   action_statement, CHAR(96), ''), ' ', ''), CHAR(9), ''),
                   CHAR(10), ''), CHAR(13), ''), CHAR(92), '')) AS normalized_action
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND event_object_table = 'fbs_oauth_receipt'
    ) receipt_triggers
    WHERE action_condition IS NULL
      AND action_timing = 'BEFORE' AND action_orientation = 'ROW'
      AND CAST(normalized_action AS BINARY) = CAST(
          'signalsqlstate''45000''setmessage_text=''oauthreceiptsareimmutable'''
          AS BINARY)
      AND ((trigger_name = 'trg_oauth_receipt_no_update'
            AND event_manipulation = 'UPDATE')
        OR (trigger_name = 'trg_oauth_receipt_no_delete'
            AND event_manipulation = 'DELETE'));
    SELECT COUNT(*) INTO consent_intent_trigger_count
    FROM (
        SELECT event_manipulation, action_timing, action_orientation, action_condition,
               LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                   REPLACE(REPLACE(REPLACE(action_statement,
                       '_utf8mb4', ''), '_utf8mb3', ''), '_ascii', ''),
                       CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                       CHAR(13), ''), CHAR(92), ''), '(', ''), ')', '')) AS normalized_action
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND event_object_table = 'fbs_oauth_authorization_request'
          AND trigger_name = 'trg_oauth_request_consent_intent_once'
    ) consent_trigger
    WHERE event_manipulation = 'UPDATE' AND action_timing = 'BEFORE'
      AND action_orientation = 'ROW' AND action_condition IS NULL
      AND CAST(normalized_action AS BINARY) = CAST(
          'beginifold.consent_intentisnotnullandnotnew.consent_intent<=>old.consent_intentthensignalsqlstate''45000''setmessage_text=''oauthconsentintentisimmutable'';endif;ifold.consent_intentisnullandnew.consent_intentisnotnullandnotold.status=''pending''andnew.statusin''approved'',''denied''thensignalsqlstate''45000''setmessage_text=''oauthconsentintentmustbesetbyadecisiontransition'';endif;end'
          AS BINARY);

    -- S2 has three atomic support groups because MySQL cannot add the receipt
    -- self-reference target index and its self FK in one ALTER. The exact
    -- token-only and token+receipt shapes are recoverable internal S2 prefixes;
    -- external S2 exists only after token, receipt and connector groups are all
    -- complete. Every other non-prefix combination is rejected.
    IF request_stage_complete = 0 THEN
        IF token_support_complete <> 0 OR causation_support_complete <> 0
           OR connector_support_complete <> 0 OR receipt_stage_complete <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'OAuth refresh-security shape is not an S0 prefix';
        END IF;
    ELSEIF token_support_complete = 0 THEN
        IF causation_support_complete <> 0 OR connector_support_complete <> 0
           OR receipt_stage_complete <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'OAuth refresh-security shape is not an S1 prefix';
        END IF;
    ELSEIF causation_support_complete = 0 THEN
        IF connector_support_complete <> 0 OR receipt_stage_complete <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'OAuth refresh-security token support prefix is drifted';
        END IF;
    ELSEIF connector_support_complete = 0 THEN
        IF receipt_stage_complete <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'OAuth refresh-security receipt support prefix is drifted';
        END IF;
    ELSEIF receipt_stage_complete NOT IN (0, 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security shape is not an S2 or S3 prefix';
    END IF;

    SET expected_legacy_column_digest = CASE
        WHEN causation_support_complete = 1 THEN
            '09ac52b1ef4f2a095507a243d3a7048aa592e7b18b8057a2132d594a9d484197'
        WHEN token_support_complete = 1 THEN
            '51075bde94d5f08b4eeef573f7c7730ba22ec596dc02e0dae929682ce8cd7952'
        ELSE
            '914545f8794180df710cd05a9ee3430e21a78995bfd4142e4dc3d54e9330780d'
    END;
    SET expected_legacy_check_digest = CASE
        WHEN token_support_complete = 1 THEN
            '11daf3aed2f9a13b314ab60fda6b28620d6f04dd3fb3ca992509ee64c7c7e503'
        ELSE
            '8cdd90a87ffcd6a12830df96ecc90b023fb90ee8470b3905a22e4c83fb327de8'
    END;
    SET expected_legacy_foreign_key_digest = CASE server_version
        WHEN '8.0.30' THEN 'd3b99d9c1923c59729166110ceeddd0c6987ef9806e1d644add9ca018ca8166a'
        WHEN '8.4.8' THEN '1e103fc572908bef3353dfdec2a2076a76c68a5ec9dd3f8b5f938a486d431d22'
        ELSE NULL
    END;

    IF table_count <> 6 OR base_table_count <> 6
       OR engine_count <> 6 OR collation_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security six-table prerequisite drifted';
    END IF;
    IF total_column_count <> 148 + (8 * receipt_stage_complete)
       OR total_generated_column_count <> 3 + (2 * receipt_stage_complete)
       OR total_index_count <> 57 + (2 * token_support_complete)
                                    + (2 * causation_support_complete)
                                    + (3 * receipt_stage_complete)
       OR total_foreign_key_count <> 16 + (2 * receipt_stage_complete)
       OR total_foreign_key_column_count <> 61 + (7 * receipt_stage_complete)
       OR total_check_count <> 36 + (2 * receipt_stage_complete)
       OR total_enforced_check_count <> total_check_count
       OR total_trigger_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security raw metadata counts are not exact';
    END IF;
    IF connector_table_count <> 3
       OR connector_total_index_count <> 11 + connector_support_complete THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security connector support metadata counts are not exact';
    END IF;
    IF CAST(legacy_connector_index_digest AS BINARY) <>
       CAST('504a017fe7c4a8ecc4c60619ea8beaf5e7d4aa207fcffe536ff47b8e1d9919b3' AS BINARY) THEN
        SET diagnostic_message = CONCAT(
            'OAuth refresh legacy connector index digest drift: ',
            COALESCE(legacy_connector_index_digest, 'NULL'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF immutable_receipt_trigger_count <> 2 OR consent_intent_trigger_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security exact trigger contract has drifted';
    END IF;
    IF CAST(legacy_column_digest AS BINARY) <>
       CAST(expected_legacy_column_digest AS BINARY) THEN
        SET diagnostic_message = CONCAT(
            'OAuth refresh legacy column digest drift: ',
            COALESCE(legacy_column_digest, 'NULL'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF CAST(legacy_index_digest AS BINARY) <>
       CAST('4d19566ba4b4c3922ee41e1a09b4f8d2f9adb6f57a9788159303a5dcac0d0a00' AS BINARY) THEN
        SET diagnostic_message = CONCAT(
            'OAuth refresh legacy index digest drift: ',
            COALESCE(legacy_index_digest, 'NULL'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF expected_legacy_foreign_key_digest IS NULL
       OR CAST(legacy_foreign_key_digest AS BINARY) <>
          CAST(expected_legacy_foreign_key_digest AS BINARY) THEN
        SET diagnostic_message = CONCAT(
            'OAuth refresh legacy FK digest drift: ',
            COALESCE(legacy_foreign_key_digest, 'NULL'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF CAST(legacy_check_digest AS BINARY) <>
       CAST(expected_legacy_check_digest AS BINARY) THEN
        SET diagnostic_message = CONCAT(
            'OAuth refresh legacy CHECK digest drift: ',
            COALESCE(legacy_check_digest, 'NULL'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `u3w_migrate_ib_oauth_refresh_security_20260721`$$
CREATE PROCEDURE `u3w_migrate_ib_oauth_refresh_security_20260721`()
BEGIN
    DECLARE migration_lock_name CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE lock_owned TINYINT DEFAULT 0;
    DECLARE original_group_concat_max_len BIGINT UNSIGNED DEFAULT 0;
    DECLARE group_concat_limit_changed TINYINT DEFAULT 0;
    DECLARE prerequisite_receipt_count INT DEFAULT 0;
    DECLARE migration_receipt_count INT DEFAULT 0;
    DECLARE migration_state VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL;
    DECLARE request_stage_complete TINYINT DEFAULT 0;
    DECLARE token_support_complete TINYINT DEFAULT 0;
    DECLARE causation_support_complete TINYINT DEFAULT 0;
    DECLARE connector_support_complete TINYINT DEFAULT 0;
    DECLARE receipt_stage_complete TINYINT DEFAULT 0;
    DECLARE invalid_request_count BIGINT DEFAULT 0;
    DECLARE legacy_security_receipt_count BIGINT DEFAULT 0;
    DECLARE migration_stage VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT 'entry';

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF group_concat_limit_changed = 1 THEN
            SET SESSION group_concat_max_len = original_group_concat_max_len;
        END IF;
        IF lock_owned = 1 THEN
            SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
            IF migration_lock_owner = CONNECTION_ID() THEN
                DO RELEASE_LOCK(migration_lock_name);
            END IF;
        END IF;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth refresh-security migration requires an explicit target database';
    END IF;
    IF INSTR(LOWER(VERSION()), 'mariadb') > 0
       OR CAST(VERSION() AS BINARY) NOT IN (
           CAST('8.0.30' AS BINARY), CAST('8.4.8' AS BINARY))
       OR CAST(@@version_comment AS BINARY) <>
          CAST('MySQL Community Server - GPL' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth refresh-security migration supports exact MySQL baselines only';
    END IF;

    SET migration_lock_name = SHA2(CONCAT(
        DATABASE(), ':20260721_independent_board_oauth_refresh_security_v1'), 256);
    SELECT GET_LOCK(migration_lock_name, 10) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth refresh-security migration lock was not acquired';
    END IF;
    SET lock_owned = 1;

    SET original_group_concat_max_len = @@SESSION.group_concat_max_len;
    IF original_group_concat_max_len < 1048576 THEN
        SET SESSION group_concat_max_len = 1048576;
        SET group_concat_limit_changed = 1;
    END IF;

    SET migration_stage = 'prerequisite-receipts';
    SELECT
        (SELECT COUNT(*) FROM u3w_schema_migration
         WHERE version = '20260721_independent_board_oauth_foundation_v1'
           AND description = 'Independent Board OAuth client, authorization, token family and immutable receipt tables')
      + (SELECT COUNT(*) FROM u3w_schema_migration
         WHERE version = '20260721_independent_board_oauth_receipt_provenance_v1'
           AND description = 'APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness')
      + (SELECT COUNT(*) FROM u3w_schema_migration
         WHERE version = '20260721_independent_board_oauth_consent_intent_lineage_v1'
           AND description = 'APPLIED:Independent Board OAuth consent intent lineage')
      INTO prerequisite_receipt_count;
    IF prerequisite_receipt_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security migration requires exact APPLIED public_init_033 through public_init_035 receipts';
    END IF;
    SELECT COUNT(*), MAX(description)
      INTO migration_receipt_count, migration_state
    FROM u3w_schema_migration
    WHERE version = '20260721_independent_board_oauth_refresh_security_v1';
    IF migration_receipt_count > 1
       OR (migration_receipt_count = 1
           AND CAST(migration_state AS BINARY) NOT IN (
               CAST('RUNNING:Independent Board OAuth refresh security receipt v2' AS BINARY),
               CAST('APPLIED:Independent Board OAuth refresh security receipt v2' AS BINARY))) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth refresh-security migration state is invalid';
    END IF;

    SET migration_stage = 'raw-metadata-preflight';
    CALL u3w_assert_ib_oauth_refresh_security_20260721(
        request_stage_complete, token_support_complete,
        causation_support_complete, connector_support_complete,
        receipt_stage_complete);
    IF migration_receipt_count = 0
       AND (request_stage_complete <> 0 OR token_support_complete <> 0
            OR causation_support_complete <> 0
            OR connector_support_complete <> 0 OR receipt_stage_complete <> 0) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security DDL exists without its RUNNING receipt';
    END IF;
    IF migration_receipt_count = 1
       AND CAST(migration_state AS BINARY) =
           CAST('APPLIED:Independent Board OAuth refresh security receipt v2' AS BINARY)
       AND (request_stage_complete <> 1 OR token_support_complete <> 1
            OR causation_support_complete <> 1
            OR connector_support_complete <> 1 OR receipt_stage_complete <> 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security APPLIED receipt is ahead of its exact S3 shape';
    END IF;

    SET migration_stage = 'legacy-data-fail-closed';
    SELECT COUNT(*) INTO invalid_request_count
    FROM fbs_oauth_authorization_request
    WHERE (`consent_intent` IS NULL AND `principal_subject_digest` IS NOT NULL)
       OR (`consent_intent` IS NOT NULL AND `principal_subject_digest` IS NULL)
       OR (`consent_intent` IS NOT NULL
           AND `consent_intent` NOT IN ('FIRST_CONNECT', 'EXPLICIT_REAUTHORIZATION'));
    IF invalid_request_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth request data violates strict consent-intent NULL pairing';
    END IF;
    IF receipt_stage_complete = 0 THEN
        SELECT COUNT(*) INTO legacy_security_receipt_count
        FROM fbs_oauth_receipt
        WHERE action IN ('TOKEN_FAMILY_ROTATED', 'REFRESH_REPLAY_DETECTED');
        IF legacy_security_receipt_count <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Legacy OAuth rotation or replay receipts cannot be trusted as receipt v2';
        END IF;
    END IF;

    IF migration_receipt_count = 0 THEN
        SET migration_stage = 'running-receipt-create';
        START TRANSACTION;
        INSERT INTO u3w_schema_migration (version, description)
        VALUES (
            '20260721_independent_board_oauth_refresh_security_v1',
            'RUNNING:Independent Board OAuth refresh security receipt v2'
        );
        COMMIT;
        SET migration_receipt_count = 1;
        SET migration_state = 'RUNNING:Independent Board OAuth refresh security receipt v2';
    END IF;

    IF request_stage_complete = 0 THEN
        SET migration_stage = 's1-strict-consent-null-check';
        ALTER TABLE fbs_oauth_authorization_request
            DROP CHECK chk_oauth_request_consent_intent,
            ADD CONSTRAINT chk_oauth_request_consent_intent
                CHECK ((consent_intent IS NULL AND principal_subject_digest IS NULL)
                    OR (consent_intent IS NOT NULL
                        AND consent_intent IN ('FIRST_CONNECT', 'EXPLICIT_REAUTHORIZATION')
                        AND principal_subject_digest IS NOT NULL));
    END IF;
    CALL u3w_assert_ib_oauth_refresh_security_20260721(
        request_stage_complete, token_support_complete,
        causation_support_complete, connector_support_complete,
        receipt_stage_complete);

    IF token_support_complete = 0 THEN
        SET migration_stage = 's2-refresh-subject-support-half';
        ALTER TABLE fbs_oauth_token
            ADD UNIQUE KEY uk_oauth_token_receipt_generation_type_scope
                (id, family_id, generation, token_type),
            ADD KEY idx_oauth_token_family_lock_order
                (family_id, id);
    END IF;
    CALL u3w_assert_ib_oauth_refresh_security_20260721(
        request_stage_complete, token_support_complete,
        causation_support_complete, connector_support_complete,
        receipt_stage_complete);
    IF causation_support_complete = 0 THEN
        SET migration_stage = 's2-causation-target-support-complete';
        ALTER TABLE fbs_oauth_receipt
            ADD UNIQUE KEY uk_oauth_receipt_causation_scope
                (receipt_id, family_id, client_id),
            ADD KEY idx_oauth_receipt_family_client_lock_order
                (family_id, client_id, id);
    END IF;
    CALL u3w_assert_ib_oauth_refresh_security_20260721(
        request_stage_complete, token_support_complete,
        causation_support_complete, connector_support_complete,
        receipt_stage_complete);
    IF connector_support_complete = 0 THEN
        SET migration_stage = 's2-connector-receipt-lock-support-complete';
        ALTER TABLE fbs_connector_binding_receipt
            ADD KEY idx_connector_binding_receipt_lock_order
                (binding_id, id);
    END IF;
    CALL u3w_assert_ib_oauth_refresh_security_20260721(
        request_stage_complete, token_support_complete,
        causation_support_complete, connector_support_complete,
        receipt_stage_complete);
    IF request_stage_complete <> 1 OR token_support_complete <> 1
       OR causation_support_complete <> 1 OR connector_support_complete <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security external S2 support stage is incomplete';
    END IF;

    IF receipt_stage_complete = 0 THEN
        SET migration_stage = 's3-receipt-v2-ddl';
        ALTER TABLE fbs_oauth_receipt
            ADD COLUMN receipt_format_version
                TINYINT UNSIGNED NOT NULL DEFAULT 1,
            ADD COLUMN subject_generation INT UNSIGNED NULL,
            ADD COLUMN result_generation INT UNSIGNED NULL,
            ADD COLUMN causation_receipt_id
                VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
            ADD COLUMN before_state_digest BINARY(32) NULL,
            ADD COLUMN after_state_digest BINARY(32) NULL,
            ADD COLUMN subject_token_type
                VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
                GENERATED ALWAYS AS (
                    CASE WHEN action IN (
                        'TOKEN_FAMILY_ROTATED', 'REFRESH_REPLAY_DETECTED'
                    ) THEN 'REFRESH' ELSE NULL END
                ) STORED,
            ADD COLUMN security_event_slot
                VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin
                GENERATED ALWAYS AS (
                    CASE
                        WHEN action = 'TOKEN_FAMILY_ROTATED' THEN
                            CONCAT('R:', family_id, ':',
                                   LPAD(result_generation, 10, '0'))
                        WHEN action = 'REFRESH_REPLAY_DETECTED' THEN
                            CONCAT('P:', family_id)
                        ELSE NULL
                    END
                ) STORED,
            ADD KEY idx_oauth_receipt_causation_scope
                (causation_receipt_id, family_id, client_id),
            ADD KEY idx_oauth_receipt_refresh_subject
                (token_id, family_id, subject_generation, subject_token_type),
            ADD UNIQUE KEY uk_oauth_receipt_security_event_slot
                (security_event_slot),
            ADD CONSTRAINT fk_oauth_receipt_refresh_subject
                FOREIGN KEY (token_id, family_id, subject_generation, subject_token_type)
                REFERENCES fbs_oauth_token (id, family_id, generation, token_type)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
            ADD CONSTRAINT fk_oauth_receipt_causation_scope
                FOREIGN KEY (causation_receipt_id, family_id, client_id)
                REFERENCES fbs_oauth_receipt (receipt_id, family_id, client_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
            ADD CONSTRAINT chk_oauth_receipt_format_version
                CHECK (receipt_format_version IN (1, 2)),
            ADD CONSTRAINT chk_oauth_receipt_v2_shape
                CHECK (
                    (receipt_format_version = 1
                     AND action NOT IN (
                         'TOKEN_FAMILY_ROTATED', 'REFRESH_REPLAY_DETECTED')
                     AND subject_generation IS NULL
                     AND result_generation IS NULL
                     AND causation_receipt_id IS NULL
                     AND before_state_digest IS NULL
                     AND after_state_digest IS NULL
                     AND subject_token_type IS NULL
                     AND security_event_slot IS NULL)
                    OR
                    (receipt_format_version = 2
                     AND action = 'TOKEN_FAMILY_ROTATED'
                     AND authorization_request_id IS NULL
                     AND authorization_code_id IS NULL
                     AND family_id IS NOT NULL
                     AND token_id IS NOT NULL
                     AND binding_id IS NOT NULL
                     AND subject_generation IS NOT NULL
                     AND subject_generation < 4294967295
                     AND result_generation IS NOT NULL
                     AND result_generation = subject_generation + 1
                     AND causation_receipt_id IS NOT NULL
                     AND causation_receipt_id <> receipt_id
                     AND before_state_digest IS NOT NULL
                     AND after_state_digest IS NOT NULL
                     AND before_state_digest <> after_state_digest
                     AND subject_token_type = 'REFRESH'
                     AND security_event_slot IS NOT NULL
                     AND actor_type = 'CLIENT'
                     AND actor_user_id IS NULL)
                    OR
                    (receipt_format_version = 2
                     AND action = 'REFRESH_REPLAY_DETECTED'
                     AND authorization_request_id IS NULL
                     AND authorization_code_id IS NULL
                     AND family_id IS NOT NULL
                     AND token_id IS NOT NULL
                     AND binding_id IS NOT NULL
                     AND subject_generation IS NOT NULL
                     AND subject_generation < 4294967295
                     AND result_generation IS NULL
                     AND causation_receipt_id IS NOT NULL
                     AND causation_receipt_id <> receipt_id
                     AND before_state_digest IS NOT NULL
                     AND after_state_digest IS NOT NULL
                     AND before_state_digest <> after_state_digest
                     AND subject_token_type = 'REFRESH'
                     AND security_event_slot IS NOT NULL
                     AND actor_type = 'CLIENT'
                     AND actor_user_id IS NULL)
                );
    END IF;

    SET migration_stage = 'final-current-read';
    CALL u3w_assert_ib_oauth_refresh_security_20260721(
        request_stage_complete, token_support_complete,
        causation_support_complete, connector_support_complete,
        receipt_stage_complete);
    IF request_stage_complete <> 1 OR token_support_complete <> 1
       OR causation_support_complete <> 1 OR connector_support_complete <> 1
       OR receipt_stage_complete <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security exact S3 current-read is incomplete';
    END IF;

    SET migration_stage = 'receipt-finalization';
    START TRANSACTION;
    SELECT description INTO migration_state
    FROM u3w_schema_migration
    WHERE version = '20260721_independent_board_oauth_refresh_security_v1'
    FOR UPDATE;
    IF CAST(migration_state AS BINARY) =
       CAST('RUNNING:Independent Board OAuth refresh security receipt v2' AS BINARY) THEN
        UPDATE u3w_schema_migration
        SET description = 'APPLIED:Independent Board OAuth refresh security receipt v2',
            applied_at = CURRENT_TIMESTAMP
        WHERE version = '20260721_independent_board_oauth_refresh_security_v1'
          AND description = 'RUNNING:Independent Board OAuth refresh security receipt v2';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'OAuth refresh-security receipt promotion lost its expected state';
        END IF;
    ELSEIF CAST(migration_state AS BINARY) <>
           CAST('APPLIED:Independent Board OAuth refresh security receipt v2' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security receipt changed during finalization';
    END IF;
    SELECT COUNT(*) INTO migration_receipt_count
    FROM u3w_schema_migration
    WHERE version = '20260721_independent_board_oauth_refresh_security_v1'
      AND description = 'APPLIED:Independent Board OAuth refresh security receipt v2';
    IF migration_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth refresh-security APPLIED receipt is not exact';
    END IF;
    COMMIT;

    IF group_concat_limit_changed = 1 THEN
        SET SESSION group_concat_max_len = original_group_concat_max_len;
    END IF;
    -- Cleanup after the final COMMIT is deliberately non-asserting. A failed
    -- release cannot roll back the completed receipt and the connection owns
    -- no further migration work.
    DO RELEASE_LOCK(migration_lock_name);
    SET lock_owned = 0;
END$$

CALL u3w_migrate_ib_oauth_refresh_security_20260721()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_ib_oauth_refresh_security_20260721`$$
DROP PROCEDURE IF EXISTS `u3w_assert_ib_oauth_refresh_security_20260721`$$

DELIMITER ;
