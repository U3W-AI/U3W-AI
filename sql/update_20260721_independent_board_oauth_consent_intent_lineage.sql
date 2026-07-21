-- ============================================================
-- FBSir Independent Board OAuth consent-intent lineage
-- Migration: 20260721_independent_board_oauth_consent_intent_lineage_v1
-- Public manifest step: public_init_035
-- Target: exact MySQL 8.0.30 or 8.4.8 metadata baselines only
-- Scope: persist the server-derived FIRST_CONNECT or
--        EXPLICIT_REAUTHORIZATION decision from request to code to family.
-- Preconditions: exact APPLIED public_init_033 and public_init_034 shapes.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_assert_ib_oauth_consent_intent_20260721`$$
CREATE PROCEDURE `u3w_assert_ib_oauth_consent_intent_20260721`(
    OUT request_stage_complete TINYINT,
    OUT code_stage_complete TINYINT,
    OUT family_stage_complete TINYINT,
    OUT trigger_stage_complete TINYINT
)
BEGIN
    DECLARE server_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE expected_foreign_key_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE expected_column_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE expected_check_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE stage_prefix CHAR(3) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE table_count INT DEFAULT 0;
    DECLARE base_table_count INT DEFAULT 0;
    DECLARE engine_count INT DEFAULT 0;
    DECLARE collation_count INT DEFAULT 0;
    DECLARE foundation_column_count INT DEFAULT 0;
    DECLARE generated_column_count INT DEFAULT 0;
    DECLARE foundation_column_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE family_slot_name_count INT DEFAULT 0;
    DECLARE family_slot_contract_count INT DEFAULT 0;
    DECLARE request_column_name_count INT DEFAULT 0;
    DECLARE request_column_contract_count INT DEFAULT 0;
    DECLARE code_column_name_count INT DEFAULT 0;
    DECLARE code_column_contract_count INT DEFAULT 0;
    DECLARE family_column_name_count INT DEFAULT 0;
    DECLARE family_column_contract_count INT DEFAULT 0;
    DECLARE foundation_index_count INT DEFAULT 0;
    DECLARE foundation_index_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE family_slot_index_name_count INT DEFAULT 0;
    DECLARE family_slot_index_contract_count INT DEFAULT 0;
    DECLARE request_index_name_count INT DEFAULT 0;
    DECLARE request_index_contract_count INT DEFAULT 0;
    DECLARE code_parent_index_name_count INT DEFAULT 0;
    DECLARE code_parent_index_contract_count INT DEFAULT 0;
    DECLARE code_identity_index_name_count INT DEFAULT 0;
    DECLARE code_identity_index_contract_count INT DEFAULT 0;
    DECLARE family_parent_index_name_count INT DEFAULT 0;
    DECLARE family_parent_index_contract_count INT DEFAULT 0;
    DECLARE foundation_foreign_key_count INT DEFAULT 0;
    DECLARE foundation_foreign_key_column_count INT DEFAULT 0;
    DECLARE foundation_foreign_key_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE code_foreign_key_name_count INT DEFAULT 0;
    DECLARE code_foreign_key_contract_count INT DEFAULT 0;
    DECLARE family_foreign_key_name_count INT DEFAULT 0;
    DECLARE family_foreign_key_contract_count INT DEFAULT 0;
    DECLARE foundation_check_count INT DEFAULT 0;
    DECLARE foundation_enforced_check_count INT DEFAULT 0;
    DECLARE foundation_check_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE request_check_name_count INT DEFAULT 0;
    DECLARE request_check_contract_count INT DEFAULT 0;
    DECLARE code_check_name_count INT DEFAULT 0;
    DECLARE code_check_contract_count INT DEFAULT 0;
    DECLARE family_check_name_count INT DEFAULT 0;
    DECLARE family_check_contract_count INT DEFAULT 0;
    DECLARE foundation_trigger_count INT DEFAULT 0;
    DECLARE foundation_trigger_contract_count INT DEFAULT 0;
    DECLARE request_trigger_name_count INT DEFAULT 0;
    DECLARE request_trigger_contract_count INT DEFAULT 0;
    DECLARE total_column_count INT DEFAULT 0;
    DECLARE total_index_count INT DEFAULT 0;
    DECLARE total_foreign_key_count INT DEFAULT 0;
    DECLARE total_foreign_key_column_count INT DEFAULT 0;
    DECLARE total_check_count INT DEFAULT 0;
    DECLARE total_enforced_check_count INT DEFAULT 0;
    DECLARE total_trigger_count INT DEFAULT 0;
    DECLARE diagnostic_message VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;

    SET request_stage_complete = 0;
    SET code_stage_complete = 0;
    SET family_stage_complete = 0;
    SET trigger_stage_complete = 0;
    SET server_version = VERSION();
    SET expected_foreign_key_digest = CASE server_version
        WHEN '8.0.30' THEN '016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539'
        WHEN '8.4.8' THEN '3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f'
        ELSE NULL
    END;

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

    SELECT COUNT(*) INTO total_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO foundation_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (table_name = 'fbs_oauth_receipt' AND column_name = 'family_created_slot')
      AND NOT (table_name = 'fbs_oauth_authorization_request' AND column_name = 'consent_intent')
      AND NOT (table_name = 'fbs_oauth_authorization_code' AND column_name = 'consent_intent')
      AND NOT (table_name = 'fbs_oauth_token_family' AND column_name = 'consent_intent');
    SELECT COUNT(*) INTO generated_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND extra = 'STORED GENERATED'
      AND generation_expression <> '';
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
      INTO foundation_column_digest
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (table_name = 'fbs_oauth_receipt' AND column_name = 'family_created_slot')
      AND NOT (table_name = 'fbs_oauth_authorization_request' AND column_name = 'consent_intent')
      AND NOT (table_name = 'fbs_oauth_authorization_code' AND column_name = 'consent_intent')
      AND NOT (table_name = 'fbs_oauth_token_family' AND column_name = 'consent_intent');

    SELECT COUNT(*),
           COALESCE(SUM(column_type = 'varchar(128)'
               AND is_nullable = 'YES'
               AND column_default IS NULL
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
               )), 0)
      INTO family_slot_name_count, family_slot_contract_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fbs_oauth_receipt'
      AND column_name = 'family_created_slot';

    SELECT COUNT(*), COALESCE(SUM(
               ordinal_position = 31
               AND column_type = 'varchar(32)'
               AND is_nullable = 'YES'
               AND column_default IS NULL
               AND character_set_name = 'ascii'
               AND collation_name = 'ascii_bin'
               AND extra = ''
               AND COALESCE(generation_expression, '') = ''), 0)
      INTO request_column_name_count, request_column_contract_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fbs_oauth_authorization_request'
      AND column_name = 'consent_intent';
    SELECT COUNT(*), COALESCE(SUM(
               ordinal_position = 27
               AND column_type = 'varchar(32)'
               AND is_nullable = 'NO'
               AND column_default IS NULL
               AND character_set_name = 'ascii'
               AND collation_name = 'ascii_bin'
               AND extra = ''
               AND COALESCE(generation_expression, '') = ''), 0)
      INTO code_column_name_count, code_column_contract_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fbs_oauth_authorization_code'
      AND column_name = 'consent_intent';
    SELECT COUNT(*), COALESCE(SUM(
               ordinal_position = 28
               AND column_type = 'varchar(32)'
               AND is_nullable = 'NO'
               AND column_default IS NULL
               AND character_set_name = 'ascii'
               AND collation_name = 'ascii_bin'
               AND extra = ''
               AND COALESCE(generation_expression, '') = ''), 0)
      INTO family_column_name_count, family_column_contract_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fbs_oauth_token_family'
      AND column_name = 'consent_intent';

    SELECT COUNT(*) INTO foundation_index_count
    FROM (
        SELECT table_name, index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_oauth_client', 'fbs_oauth_authorization_request',
              'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
              'fbs_oauth_token', 'fbs_oauth_receipt'
          )
          AND NOT (table_name = 'fbs_oauth_receipt'
                   AND index_name = 'uk_oauth_receipt_family_created_slot')
          AND NOT (table_name = 'fbs_oauth_authorization_request'
                   AND index_name = 'uk_oauth_request_id_consent')
          AND NOT (table_name = 'fbs_oauth_authorization_code'
                   AND index_name IN ('idx_oauth_code_request_consent', 'uk_oauth_code_id_consent'))
          AND NOT (table_name = 'fbs_oauth_token_family'
                   AND index_name = 'idx_oauth_family_code_consent')
        GROUP BY table_name, index_name
    ) base_indexes;
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
      INTO foundation_index_digest
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (table_name = 'fbs_oauth_receipt'
               AND index_name = 'uk_oauth_receipt_family_created_slot')
      AND NOT (table_name = 'fbs_oauth_authorization_request'
               AND index_name = 'uk_oauth_request_id_consent')
      AND NOT (table_name = 'fbs_oauth_authorization_code'
               AND index_name IN ('idx_oauth_code_request_consent', 'uk_oauth_code_id_consent'))
      AND NOT (table_name = 'fbs_oauth_token_family'
               AND index_name = 'idx_oauth_family_code_consent');

    SELECT
        COALESCE(SUM(table_name = 'fbs_oauth_receipt'
            AND index_name = 'uk_oauth_receipt_family_created_slot'), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_receipt'
            AND index_name = 'uk_oauth_receipt_family_created_slot'
            AND non_unique = 0 AND index_type = 'BTREE' AND visibility = 'YES'
            AND key_part_count = 1 AND non_column_key_parts = 0 AND partial_columns = 0
            AND CAST(column_signature AS BINARY) = CAST('family_created_slot:A' AS BINARY)), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_request'
            AND index_name = 'uk_oauth_request_id_consent'), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_request'
            AND index_name = 'uk_oauth_request_id_consent'
            AND non_unique = 0 AND index_type = 'BTREE' AND visibility = 'YES'
            AND key_part_count = 2 AND non_column_key_parts = 0 AND partial_columns = 0
            AND CAST(column_signature AS BINARY) = CAST('id:A,consent_intent:A' AS BINARY)), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
            AND index_name = 'idx_oauth_code_request_consent'), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
            AND index_name = 'idx_oauth_code_request_consent'
            AND non_unique = 1 AND index_type = 'BTREE' AND visibility = 'YES'
            AND key_part_count = 2 AND non_column_key_parts = 0 AND partial_columns = 0
            AND CAST(column_signature AS BINARY) = CAST('authorization_request_id:A,consent_intent:A' AS BINARY)), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
            AND index_name = 'uk_oauth_code_id_consent'), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
            AND index_name = 'uk_oauth_code_id_consent'
            AND non_unique = 0 AND index_type = 'BTREE' AND visibility = 'YES'
            AND key_part_count = 2 AND non_column_key_parts = 0 AND partial_columns = 0
            AND CAST(column_signature AS BINARY) = CAST('id:A,consent_intent:A' AS BINARY)), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_token_family'
            AND index_name = 'idx_oauth_family_code_consent'), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_token_family'
            AND index_name = 'idx_oauth_family_code_consent'
            AND non_unique = 1 AND index_type = 'BTREE' AND visibility = 'YES'
            AND key_part_count = 2 AND non_column_key_parts = 0 AND partial_columns = 0
            AND CAST(column_signature AS BINARY) = CAST('origin_authorization_code_id:A,consent_intent:A' AS BINARY)), 0)
      INTO family_slot_index_name_count, family_slot_index_contract_count,
           request_index_name_count, request_index_contract_count,
           code_parent_index_name_count, code_parent_index_contract_count,
           code_identity_index_name_count, code_identity_index_contract_count,
           family_parent_index_name_count, family_parent_index_contract_count
    FROM (
        SELECT table_name, index_name, non_unique, index_type,
               MIN(is_visible) AS visibility,
               COUNT(*) AS key_part_count,
               SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
               SUM(sub_part IS NOT NULL) AS partial_columns,
               GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                            ORDER BY seq_in_index SEPARATOR ',') AS column_signature
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND ((table_name = 'fbs_oauth_receipt'
                AND index_name = 'uk_oauth_receipt_family_created_slot')
            OR (table_name = 'fbs_oauth_authorization_request'
                AND index_name = 'uk_oauth_request_id_consent')
            OR (table_name = 'fbs_oauth_authorization_code'
                AND index_name IN ('idx_oauth_code_request_consent', 'uk_oauth_code_id_consent'))
            OR (table_name = 'fbs_oauth_token_family'
                AND index_name = 'idx_oauth_family_code_consent'))
        GROUP BY table_name, index_name, non_unique, index_type
    ) named_indexes;

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
    ) all_indexes;

    SELECT COUNT(*) INTO foundation_foreign_key_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND unique_constraint_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (table_name = 'fbs_oauth_authorization_code'
               AND constraint_name = 'fk_oauth_code_request_consent')
      AND NOT (table_name = 'fbs_oauth_token_family'
               AND constraint_name = 'fk_oauth_family_code_consent');
    SELECT COUNT(*) INTO foundation_foreign_key_column_count
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND referenced_table_schema = DATABASE()
      AND referenced_table_name IS NOT NULL
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (table_name = 'fbs_oauth_authorization_code'
               AND constraint_name = 'fk_oauth_code_request_consent')
      AND NOT (table_name = 'fbs_oauth_token_family'
               AND constraint_name = 'fk_oauth_family_code_consent');
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
      INTO foundation_foreign_key_digest
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
      )
      AND NOT (rc.table_name = 'fbs_oauth_authorization_code'
               AND rc.constraint_name = 'fk_oauth_code_request_consent')
      AND NOT (rc.table_name = 'fbs_oauth_token_family'
               AND rc.constraint_name = 'fk_oauth_family_code_consent');

    SELECT
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
            AND constraint_name = 'fk_oauth_code_request_consent'), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
            AND constraint_name = 'fk_oauth_code_request_consent'
            AND unique_constraint_schema = DATABASE()
            AND CAST(unique_constraint_name AS BINARY) = CAST('uk_oauth_request_id_consent' AS BINARY)
            AND CAST(referenced_table_name AS BINARY) = CAST('fbs_oauth_authorization_request' AS BINARY)
            AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT' AND match_option = 'NONE'
            AND key_part_count = 2 AND same_schema_part_count = 2
            AND CAST(key_signature AS BINARY) =
                CAST('1:authorization_request_id>id:1,2:consent_intent>consent_intent:2' AS BINARY)), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_token_family'
            AND constraint_name = 'fk_oauth_family_code_consent'), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_token_family'
            AND constraint_name = 'fk_oauth_family_code_consent'
            AND unique_constraint_schema = DATABASE()
            AND CAST(unique_constraint_name AS BINARY) = CAST('uk_oauth_code_id_consent' AS BINARY)
            AND CAST(referenced_table_name AS BINARY) = CAST('fbs_oauth_authorization_code' AS BINARY)
            AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT' AND match_option = 'NONE'
            AND key_part_count = 2 AND same_schema_part_count = 2
            AND CAST(key_signature AS BINARY) =
                CAST('1:origin_authorization_code_id>id:1,2:consent_intent>consent_intent:2' AS BINARY)), 0)
      INTO code_foreign_key_name_count, code_foreign_key_contract_count,
           family_foreign_key_name_count, family_foreign_key_contract_count
    FROM (
        SELECT rc.table_name, rc.constraint_name, rc.unique_constraint_schema,
               rc.unique_constraint_name, rc.referenced_table_name,
               rc.update_rule, rc.delete_rule, rc.match_option,
               COUNT(*) AS key_part_count,
               SUM(kcu.referenced_table_schema = DATABASE()) AS same_schema_part_count,
               GROUP_CONCAT(CONCAT(
                   kcu.ordinal_position, ':', kcu.column_name, '>',
                   kcu.referenced_column_name, ':', kcu.position_in_unique_constraint)
                   ORDER BY kcu.ordinal_position SEPARATOR ',') AS key_signature
        FROM information_schema.referential_constraints rc
        INNER JOIN information_schema.key_column_usage kcu
          ON kcu.constraint_schema = rc.constraint_schema
         AND kcu.table_name = rc.table_name
         AND kcu.constraint_name = rc.constraint_name
        WHERE rc.constraint_schema = DATABASE()
          AND ((rc.table_name = 'fbs_oauth_authorization_code'
                AND rc.constraint_name = 'fk_oauth_code_request_consent')
            OR (rc.table_name = 'fbs_oauth_token_family'
                AND rc.constraint_name = 'fk_oauth_family_code_consent'))
        GROUP BY rc.table_name, rc.constraint_name, rc.unique_constraint_schema,
                 rc.unique_constraint_name, rc.referenced_table_name,
                 rc.update_rule, rc.delete_rule, rc.match_option
    ) named_foreign_keys;
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
      AND referenced_table_name IS NOT NULL
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );

    SELECT COUNT(*), COALESCE(SUM(enforced = 'YES'), 0)
      INTO foundation_check_count, foundation_enforced_check_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_type = 'CHECK'
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (table_name = 'fbs_oauth_authorization_request'
               AND constraint_name = 'chk_oauth_request_consent_intent')
      AND NOT (table_name = 'fbs_oauth_authorization_code'
               AND constraint_name = 'chk_oauth_code_consent_intent')
      AND NOT (table_name = 'fbs_oauth_token_family'
               AND constraint_name = 'chk_oauth_family_consent_intent');
    SELECT SHA2(GROUP_CONCAT(CONCAT(
               'T:', HEX(CAST(table_name AS BINARY)),
               '|C:', HEX(CAST(constraint_name AS BINARY)),
               '|E:', HEX(CAST(enforced AS BINARY)),
               '|X:', HEX(CAST(check_clause AS BINARY)))
               ORDER BY table_name, constraint_name SEPARATOR 0x0A), 256)
      INTO foundation_check_digest
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
          AND NOT (tc.table_name = 'fbs_oauth_authorization_code'
                   AND tc.constraint_name = 'chk_oauth_code_consent_intent')
          AND NOT (tc.table_name = 'fbs_oauth_token_family'
                   AND tc.constraint_name = 'chk_oauth_family_consent_intent')
    ) base_checks;

    SELECT
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_request'
            AND constraint_name = 'chk_oauth_request_consent_intent'), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
            AND constraint_name = 'chk_oauth_code_consent_intent'), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_token_family'
            AND constraint_name = 'chk_oauth_family_consent_intent'), 0)
      INTO request_check_name_count, code_check_name_count, family_check_name_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND ((table_name = 'fbs_oauth_authorization_request'
            AND constraint_name = 'chk_oauth_request_consent_intent')
        OR (table_name = 'fbs_oauth_authorization_code'
            AND constraint_name = 'chk_oauth_code_consent_intent')
        OR (table_name = 'fbs_oauth_token_family'
            AND constraint_name = 'chk_oauth_family_consent_intent'));
    SELECT
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_request'
            AND constraint_name = 'chk_oauth_request_consent_intent'
            AND enforced = 'YES'
            AND CAST(normalized_clause AS BINARY) = CAST(
                'consent_intentisnullandprincipal_subject_digestisnullorconsent_intentin''first_connect'',''explicit_reauthorization''andprincipal_subject_digestisnotnull'
                AS BINARY)), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
            AND constraint_name = 'chk_oauth_code_consent_intent'
            AND enforced = 'YES'
            AND CAST(normalized_clause AS BINARY) = CAST(
                'consent_intentin''first_connect'',''explicit_reauthorization'''
                AS BINARY)), 0),
        COALESCE(SUM(table_name = 'fbs_oauth_token_family'
            AND constraint_name = 'chk_oauth_family_consent_intent'
            AND enforced = 'YES'
            AND CAST(normalized_clause AS BINARY) = CAST(
                'consent_intentin''first_connect'',''explicit_reauthorization'''
                AS BINARY)), 0)
      INTO request_check_contract_count, code_check_contract_count,
           family_check_contract_count
    FROM (
        SELECT tc.table_name, tc.constraint_name, tc.enforced,
               LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                   REPLACE(REPLACE(REPLACE(cc.check_clause,
                       '_utf8mb4', ''), '_utf8mb3', ''), '_ascii', ''),
                       '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                       CHAR(13), ''), CHAR(92), ''), '(', ''), ')', '')) AS normalized_clause
        FROM information_schema.table_constraints tc
        INNER JOIN information_schema.check_constraints cc
          ON cc.constraint_schema = tc.constraint_schema
         AND cc.constraint_name = tc.constraint_name
        WHERE tc.constraint_schema = DATABASE()
          AND tc.constraint_type = 'CHECK'
          AND ((tc.table_name = 'fbs_oauth_authorization_request'
                AND tc.constraint_name = 'chk_oauth_request_consent_intent')
            OR (tc.table_name = 'fbs_oauth_authorization_code'
                AND tc.constraint_name = 'chk_oauth_code_consent_intent')
            OR (tc.table_name = 'fbs_oauth_token_family'
                AND tc.constraint_name = 'chk_oauth_family_consent_intent'))
    ) named_checks;
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

    SELECT COUNT(*) INTO foundation_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      )
      AND NOT (event_object_table = 'fbs_oauth_authorization_request'
               AND trigger_name = 'trg_oauth_request_consent_intent_once');
    SELECT COUNT(*) INTO foundation_trigger_contract_count
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
          AND NOT (event_object_table = 'fbs_oauth_authorization_request'
                   AND trigger_name = 'trg_oauth_request_consent_intent_once')
          AND action_condition IS NULL
    ) receipt_triggers
    WHERE event_object_table = 'fbs_oauth_receipt'
      AND action_timing = 'BEFORE' AND action_orientation = 'ROW'
      AND CAST(normalized_action AS BINARY) = CAST(
          'signalsqlstate''45000''setmessage_text=''oauthreceiptsareimmutable'''
          AS BINARY)
      AND ((trigger_name = 'trg_oauth_receipt_no_update' AND event_manipulation = 'UPDATE')
        OR (trigger_name = 'trg_oauth_receipt_no_delete' AND event_manipulation = 'DELETE'));
    SELECT COUNT(*), COALESCE(SUM(
               event_object_table = 'fbs_oauth_authorization_request'
               AND event_manipulation = 'UPDATE'
               AND action_timing = 'BEFORE'
               AND action_orientation = 'ROW'
               AND action_condition IS NULL
               AND CAST(normalized_action AS BINARY) = CAST(
                   'beginifold.consent_intentisnotnullandnotnew.consent_intent<=>old.consent_intentthensignalsqlstate''45000''setmessage_text=''oauthconsentintentisimmutable'';endif;ifold.consent_intentisnullandnew.consent_intentisnotnullandnotold.status=''pending''andnew.statusin''approved'',''denied''thensignalsqlstate''45000''setmessage_text=''oauthconsentintentmustbesetbyadecisiontransition'';endif;end'
                   AS BINARY)), 0)
      INTO request_trigger_name_count, request_trigger_contract_count
    FROM (
        SELECT trigger_name, event_object_table, event_manipulation,
               action_timing, action_orientation, action_condition,
               LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                   REPLACE(REPLACE(REPLACE(action_statement,
                       '_utf8mb4', ''), '_utf8mb3', ''), '_ascii', ''),
                       '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                       CHAR(13), ''), CHAR(92), ''), '(', ''), ')', '')) AS normalized_action
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND event_object_table = 'fbs_oauth_authorization_request'
          AND trigger_name = 'trg_oauth_request_consent_intent_once'
    ) named_trigger;
    SELECT COUNT(*) INTO total_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );

    -- Classify every successor object before selecting a stage-bound raw baseline.
    IF request_column_name_count = 0 AND request_column_contract_count = 0
       AND request_check_name_count = 0 AND request_check_contract_count = 0
       AND request_index_name_count = 0 AND request_index_contract_count = 0 THEN
        SET request_stage_complete = 0;
    ELSEIF request_column_name_count = 1 AND request_column_contract_count = 1
       AND request_check_name_count = 1 AND request_check_contract_count = 1
       AND request_index_name_count = 1 AND request_index_contract_count = 1 THEN
        SET request_stage_complete = 1;
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth request consent-intent DDL is partial or drifted';
    END IF;

    IF code_column_name_count = 0 AND code_column_contract_count = 0
       AND code_check_name_count = 0 AND code_check_contract_count = 0
       AND code_parent_index_name_count = 0 AND code_parent_index_contract_count = 0
       AND code_identity_index_name_count = 0 AND code_identity_index_contract_count = 0
       AND code_foreign_key_name_count = 0 AND code_foreign_key_contract_count = 0 THEN
        SET code_stage_complete = 0;
    ELSEIF code_column_name_count = 1 AND code_column_contract_count = 1
       AND code_check_name_count = 1 AND code_check_contract_count = 1
       AND code_parent_index_name_count = 1 AND code_parent_index_contract_count = 1
       AND code_identity_index_name_count = 1 AND code_identity_index_contract_count = 1
       AND code_foreign_key_name_count = 1 AND code_foreign_key_contract_count = 1 THEN
        SET code_stage_complete = 1;
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth code consent-intent DDL is partial or drifted';
    END IF;

    IF family_column_name_count = 0 AND family_column_contract_count = 0
       AND family_check_name_count = 0 AND family_check_contract_count = 0
       AND family_parent_index_name_count = 0 AND family_parent_index_contract_count = 0
       AND family_foreign_key_name_count = 0 AND family_foreign_key_contract_count = 0 THEN
        SET family_stage_complete = 0;
    ELSEIF family_column_name_count = 1 AND family_column_contract_count = 1
       AND family_check_name_count = 1 AND family_check_contract_count = 1
       AND family_parent_index_name_count = 1 AND family_parent_index_contract_count = 1
       AND family_foreign_key_name_count = 1 AND family_foreign_key_contract_count = 1 THEN
        SET family_stage_complete = 1;
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth family consent-intent DDL is partial or drifted';
    END IF;

    IF request_trigger_name_count = 0 AND request_trigger_contract_count = 0 THEN
        SET trigger_stage_complete = 0;
    ELSEIF request_trigger_name_count = 1 AND request_trigger_contract_count = 1 THEN
        SET trigger_stage_complete = 1;
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth request consent-intent trigger is partial or drifted';
    END IF;

    IF request_stage_complete = 0 AND code_stage_complete = 0
       AND family_stage_complete = 0 THEN
        SET stage_prefix = '000';
    ELSEIF request_stage_complete = 1 AND code_stage_complete = 0
       AND family_stage_complete = 0 THEN
        SET stage_prefix = '100';
    ELSEIF request_stage_complete = 1 AND code_stage_complete = 1
       AND family_stage_complete = 0 THEN
        SET stage_prefix = '110';
    ELSEIF request_stage_complete = 1 AND code_stage_complete = 1
       AND family_stage_complete = 1 THEN
        SET stage_prefix = '111';
    ELSE
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent shape must be a 000, 100, 110 or 111 prefix';
    END IF;
    IF trigger_stage_complete = 1 AND CAST(stage_prefix AS BINARY) <> CAST('111' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent trigger cannot precede the 111 lineage prefix';
    END IF;

    SET expected_column_digest = CASE CONCAT(server_version, ':', stage_prefix)
        WHEN '8.0.30:000' THEN '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'
        WHEN '8.0.30:100' THEN '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'
        WHEN '8.0.30:110' THEN '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'
        WHEN '8.0.30:111' THEN 'e222fafb27746a52bdc6e31c08a9b8cb3e34c2a8eb88bad9345d53b496d0c612'
        WHEN '8.4.8:000' THEN '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'
        WHEN '8.4.8:100' THEN '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'
        WHEN '8.4.8:110' THEN '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'
        WHEN '8.4.8:111' THEN 'e222fafb27746a52bdc6e31c08a9b8cb3e34c2a8eb88bad9345d53b496d0c612'
        ELSE NULL
    END;
    SET expected_check_digest = CASE CONCAT(server_version, ':', stage_prefix)
        WHEN '8.0.30:000' THEN 'd8878ff64c7e897ddd8d42db6f0afd1a264d2cc8c1794d155051f0cd1638103b'
        WHEN '8.0.30:100' THEN '85ee62cd04574bb7fe27a05c361bb8b7655144e8036b2ddb83a9837c7d88ca49'
        WHEN '8.0.30:110' THEN '300d34a5fcd2f6a55d121bee598edc6e6a509dd4a1c80a04cf0b5b1d788e0574'
        WHEN '8.0.30:111' THEN '0aca71671f1632851a45f57eeacfa18f23529d628ddaa159dc2ca7cb472a0d24'
        WHEN '8.4.8:000' THEN 'd8878ff64c7e897ddd8d42db6f0afd1a264d2cc8c1794d155051f0cd1638103b'
        WHEN '8.4.8:100' THEN '85ee62cd04574bb7fe27a05c361bb8b7655144e8036b2ddb83a9837c7d88ca49'
        WHEN '8.4.8:110' THEN '300d34a5fcd2f6a55d121bee598edc6e6a509dd4a1c80a04cf0b5b1d788e0574'
        WHEN '8.4.8:111' THEN '0aca71671f1632851a45f57eeacfa18f23529d628ddaa159dc2ca7cb472a0d24'
        ELSE NULL
    END;

    IF table_count <> 6 OR base_table_count <> 6
       OR engine_count <> 6 OR collation_count <> 6 THEN
        SET diagnostic_message = CONCAT(
            'OAuth 034 table prerequisite drift: ', table_count, '/', base_table_count,
            '/', engine_count, '/', collation_count);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF foundation_column_count <> 144 OR generated_column_count <> 3 THEN
        SET diagnostic_message = CONCAT(
            'OAuth 034 column-count prerequisite drift: ', foundation_column_count,
            '/', generated_column_count);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF family_slot_name_count <> 1 OR family_slot_contract_count <> 1 THEN
        SET diagnostic_message = CONCAT(
            'OAuth 034 family-slot column drift: ', family_slot_name_count,
            '/', family_slot_contract_count);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF foundation_index_count <> 52 THEN
        SET diagnostic_message = CONCAT(
            'OAuth 034 index-count prerequisite drift: ', foundation_index_count);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF foundation_index_digest IS NULL
       OR CAST(foundation_index_digest AS BINARY) <>
          CAST('1a3122b7238b444939a7981ccd759322bd168f351131e4d9c0a16346242c3d79' AS BINARY) THEN
        SET diagnostic_message = CONCAT(
            'OAuth 034 index digest drift: ', COALESCE(foundation_index_digest, 'NULL'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF family_slot_index_name_count <> 1 OR family_slot_index_contract_count <> 1 THEN
        SET diagnostic_message = CONCAT(
            'OAuth 034 family-slot index drift: ', family_slot_index_name_count,
            '/', family_slot_index_contract_count);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF foundation_foreign_key_count <> 14
       OR foundation_foreign_key_column_count <> 57 THEN
        SET diagnostic_message = CONCAT(
            'OAuth 034 FK-count prerequisite drift: ', foundation_foreign_key_count,
            '/', foundation_foreign_key_column_count);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF foundation_foreign_key_digest IS NULL
       OR CAST(foundation_foreign_key_digest AS BINARY) <>
          CAST(expected_foreign_key_digest AS BINARY) THEN
        SET diagnostic_message = CONCAT(
            'OAuth 034 FK digest drift: ', COALESCE(foundation_foreign_key_digest, 'NULL'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF foundation_check_count <> 33 OR foundation_enforced_check_count <> 33 THEN
        SET diagnostic_message = CONCAT(
            'OAuth 034 CHECK-count prerequisite drift: ', foundation_check_count,
            '/', foundation_enforced_check_count);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF foundation_trigger_count <> 2 OR foundation_trigger_contract_count <> 2 THEN
        SET diagnostic_message = CONCAT(
            'OAuth 034 trigger prerequisite drift: ', foundation_trigger_count,
            '/', foundation_trigger_contract_count);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF total_column_count <> 145 + request_stage_complete + code_stage_complete + family_stage_complete
       OR total_index_count <> 53 + request_stage_complete
                                      + (2 * code_stage_complete) + family_stage_complete
       OR total_foreign_key_count <> 14 + code_stage_complete + family_stage_complete
       OR total_foreign_key_column_count <> 57 + (2 * code_stage_complete)
                                                 + (2 * family_stage_complete)
       OR total_check_count <> 33 + request_stage_complete + code_stage_complete + family_stage_complete
       OR total_enforced_check_count <> total_check_count
       OR total_trigger_count <> 2 + trigger_stage_complete THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent raw metadata counts are not exact';
    END IF;

    IF expected_column_digest IS NULL OR expected_check_digest IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent exact stage baseline is unavailable';
    END IF;
    IF foundation_column_digest IS NULL
       OR CAST(foundation_column_digest AS BINARY) <>
          CAST(expected_column_digest AS BINARY) THEN
        SET diagnostic_message = CONCAT(
            'OAuth ', stage_prefix, ' column digest drift: ',
            COALESCE(foundation_column_digest, 'NULL'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;
    IF foundation_check_digest IS NULL
       OR CAST(foundation_check_digest AS BINARY) <>
          CAST(expected_check_digest AS BINARY) THEN
        SET diagnostic_message = CONCAT(
            'OAuth ', stage_prefix, ' CHECK digest drift: ',
            COALESCE(foundation_check_digest, 'NULL'));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = diagnostic_message;
    END IF;

END$$

DROP PROCEDURE IF EXISTS `u3w_migrate_ib_oauth_consent_intent_20260721`$$
CREATE PROCEDURE `u3w_migrate_ib_oauth_consent_intent_20260721`()
BEGIN
    DECLARE migration_lock_name CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE current_connection BIGINT DEFAULT 0;
    DECLARE server_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE original_group_concat_max_len BIGINT UNSIGNED DEFAULT 0;
    DECLARE group_concat_limit_changed TINYINT DEFAULT 0;
    DECLARE foundation_receipt_count INT DEFAULT 0;
    DECLARE provenance_receipt_count INT DEFAULT 0;
    DECLARE migration_receipt_count INT DEFAULT 0;
    DECLARE migration_state VARCHAR(255) DEFAULT NULL;
    DECLARE request_stage_complete TINYINT DEFAULT 0;
    DECLARE code_stage_complete TINYINT DEFAULT 0;
    DECLARE family_stage_complete TINYINT DEFAULT 0;
    DECLARE trigger_stage_complete TINYINT DEFAULT 0;
    DECLARE invalid_request_count BIGINT DEFAULT 0;
    DECLARE downstream_row_count BIGINT DEFAULT 0;
    DECLARE invalid_receipt_count BIGINT DEFAULT 0;
    DECLARE migration_stage VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT 'preflight';
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
            SET MESSAGE_TEXT = 'Independent Board OAuth consent-intent migration requires an explicit target database';
    END IF;
    SET server_version = VERSION();
    IF INSTR(LOWER(VERSION()), 'mariadb') > 0
       OR CAST(server_version AS BINARY) NOT IN (
           CAST('8.0.30' AS BINARY), CAST('8.4.8' AS BINARY))
       OR CAST(@@version_comment AS BINARY) <>
          CAST('MySQL Community Server - GPL' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth consent-intent migration supports exact MySQL 8.0.30 or 8.4.8 baselines';
    END IF;

    SET original_group_concat_max_len = @@SESSION.group_concat_max_len;
    IF original_group_concat_max_len < 1048576 THEN
        SET SESSION group_concat_max_len = 1048576;
        SET group_concat_limit_changed = 1;
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260721_independent_board_oauth_consent_intent_lineage_v1'),
        256
    );
    IF CHAR_LENGTH(migration_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth consent-intent lock name must be a stable digest';
    END IF;
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the Independent Board OAuth consent-intent migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name), CONNECTION_ID()
      INTO migration_lock_owner, current_connection;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth consent-intent migration lock is not owned by this connection';
    END IF;

    SET migration_stage = 'prerequisite-receipt-audit';
    SELECT COUNT(*) INTO foundation_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_foundation_v1'
      AND `description` = 'Independent Board OAuth client, authorization, token family and immutable receipt tables';
    SELECT COUNT(*) INTO provenance_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_receipt_provenance_v1'
      AND `description` = 'APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness';
    IF foundation_receipt_count <> 1 OR provenance_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent migration requires exact APPLIED public_init_033 and public_init_034 receipts';
    END IF;
    SELECT COUNT(*), MAX(`description`)
      INTO migration_receipt_count, migration_state
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_consent_intent_lineage_v1';
    IF migration_receipt_count > 1
       OR (migration_receipt_count = 1
           AND CAST(migration_state AS BINARY) NOT IN (
               CAST('RUNNING:Independent Board OAuth consent intent lineage' AS BINARY),
               CAST('APPLIED:Independent Board OAuth consent intent lineage' AS BINARY))) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth consent-intent migration state is invalid';
    END IF;

    SET migration_stage = 'raw-metadata-preflight';
    CALL `u3w_assert_ib_oauth_consent_intent_20260721`(
        request_stage_complete, code_stage_complete,
        family_stage_complete, trigger_stage_complete
    );
    IF request_stage_complete = 0
       AND (code_stage_complete = 1 OR family_stage_complete = 1 OR trigger_stage_complete = 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent recovery is not a valid request-first prefix';
    ELSEIF code_stage_complete = 0
       AND (family_stage_complete = 1 OR trigger_stage_complete = 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent recovery is not a valid code-first prefix';
    ELSEIF family_stage_complete = 0 AND trigger_stage_complete = 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent trigger cannot precede family lineage';
    END IF;
    IF migration_receipt_count = 0
       AND (request_stage_complete = 1 OR code_stage_complete = 1
            OR family_stage_complete = 1 OR trigger_stage_complete = 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent DDL exists without its RUNNING receipt';
    END IF;
    IF migration_receipt_count = 1
       AND CAST(migration_state AS BINARY) =
           CAST('APPLIED:Independent Board OAuth consent intent lineage' AS BINARY)
       AND (request_stage_complete <> 1 OR code_stage_complete <> 1
            OR family_stage_complete <> 1 OR trigger_stage_complete <> 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent APPLIED receipt is ahead of its exact shape';
    END IF;

    IF migration_receipt_count = 0
       OR CAST(migration_state AS BINARY) =
          CAST('RUNNING:Independent Board OAuth consent intent lineage' AS BINARY) THEN
        SET migration_stage = 'legacy-data-fail-closed';
        SELECT COUNT(*) INTO invalid_request_count
        FROM `fbs_oauth_authorization_request`
        WHERE CAST(`status` AS BINARY) NOT IN (CAST('PENDING' AS BINARY), CAST('EXPIRED' AS BINARY))
           OR `enterprise_id` IS NOT NULL OR `member_id` IS NOT NULL OR `user_id` IS NOT NULL
           OR `principal_subject_digest` IS NOT NULL
           OR `approved_at` IS NOT NULL OR `denied_at` IS NOT NULL OR `consumed_at` IS NOT NULL;
        SELECT
            (SELECT COUNT(*) FROM `fbs_oauth_authorization_code`)
          + (SELECT COUNT(*) FROM `fbs_oauth_token_family`)
          + (SELECT COUNT(*) FROM `fbs_oauth_token`)
          INTO downstream_row_count;
        SELECT COUNT(*) INTO invalid_receipt_count
        FROM `fbs_oauth_receipt`
        WHERE CAST(`action` AS BINARY) NOT IN (
            CAST('OAUTH_CLIENT_REGISTERED' AS BINARY),
            CAST('OAUTH_CLIENT_REVOKED' AS BINARY)
        );
        IF request_stage_complete = 1 THEN
            SELECT invalid_request_count + COUNT(*) INTO invalid_request_count
            FROM `fbs_oauth_authorization_request`
            WHERE `consent_intent` IS NOT NULL;
        END IF;
        IF invalid_request_count <> 0 OR downstream_row_count <> 0 OR invalid_receipt_count <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Legacy OAuth data cannot be assigned a consent intent without reviewed evidence';
        END IF;
    END IF;

    IF migration_receipt_count = 0 THEN
        SET migration_stage = 'running-receipt-create';
        START TRANSACTION;
        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260721_independent_board_oauth_consent_intent_lineage_v1',
            'RUNNING:Independent Board OAuth consent intent lineage'
        );
        COMMIT;
        SET migration_receipt_count = 1;
        SET migration_state = 'RUNNING:Independent Board OAuth consent intent lineage';
    END IF;

    IF request_stage_complete = 0 THEN
        SET migration_stage = 'request-consent-intent-ddl';
        ALTER TABLE `fbs_oauth_authorization_request`
            ADD COLUMN `consent_intent`
                VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
            ADD CONSTRAINT `chk_oauth_request_consent_intent`
                CHECK ((`consent_intent` IS NULL AND `principal_subject_digest` IS NULL)
                    OR (`consent_intent` IN ('FIRST_CONNECT', 'EXPLICIT_REAUTHORIZATION')
                        AND `principal_subject_digest` IS NOT NULL)),
            ADD UNIQUE KEY `uk_oauth_request_id_consent` (`id`, `consent_intent`);
    END IF;
    IF code_stage_complete = 0 THEN
        SET migration_stage = 'code-consent-intent-ddl';
        ALTER TABLE `fbs_oauth_authorization_code`
            ADD COLUMN `consent_intent`
                VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            ADD CONSTRAINT `chk_oauth_code_consent_intent`
                CHECK (`consent_intent` IN ('FIRST_CONNECT', 'EXPLICIT_REAUTHORIZATION')),
            ADD UNIQUE KEY `uk_oauth_code_id_consent` (`id`, `consent_intent`),
            ADD KEY `idx_oauth_code_request_consent`
                (`authorization_request_id`, `consent_intent`),
            ADD CONSTRAINT `fk_oauth_code_request_consent`
                FOREIGN KEY (`authorization_request_id`, `consent_intent`)
                REFERENCES `fbs_oauth_authorization_request` (`id`, `consent_intent`)
                ON DELETE RESTRICT ON UPDATE RESTRICT;
    END IF;
    IF family_stage_complete = 0 THEN
        SET migration_stage = 'family-consent-intent-ddl';
        ALTER TABLE `fbs_oauth_token_family`
            ADD COLUMN `consent_intent`
                VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            ADD CONSTRAINT `chk_oauth_family_consent_intent`
                CHECK (`consent_intent` IN ('FIRST_CONNECT', 'EXPLICIT_REAUTHORIZATION')),
            ADD KEY `idx_oauth_family_code_consent`
                (`origin_authorization_code_id`, `consent_intent`),
            ADD CONSTRAINT `fk_oauth_family_code_consent`
                FOREIGN KEY (`origin_authorization_code_id`, `consent_intent`)
                REFERENCES `fbs_oauth_authorization_code` (`id`, `consent_intent`)
                ON DELETE RESTRICT ON UPDATE RESTRICT;
    END IF;

    SET migration_stage = 'post-ddl-current-read';
    CALL `u3w_assert_ib_oauth_consent_intent_20260721`(
        request_stage_complete, code_stage_complete,
        family_stage_complete, trigger_stage_complete
    );
    IF request_stage_complete <> 1 OR code_stage_complete <> 1 OR family_stage_complete <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent column, CHECK, index or foreign-key DDL is incomplete';
    END IF;

    IF group_concat_limit_changed = 1 THEN
        SET SESSION group_concat_max_len = original_group_concat_max_len;
        SET group_concat_limit_changed = 0;
    END IF;
    -- Keep the connection-scoped named lock across CREATE TRIGGER and finalization.
    SET migration_lock_acquired = 0;
END$$

CALL `u3w_migrate_ib_oauth_consent_intent_20260721`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_ib_oauth_consent_intent_20260721`$$

CREATE TRIGGER IF NOT EXISTS `trg_oauth_request_consent_intent_once`
BEFORE UPDATE ON `fbs_oauth_authorization_request`
FOR EACH ROW
BEGIN
    IF OLD.`consent_intent` IS NOT NULL
       AND NOT (NEW.`consent_intent` <=> OLD.`consent_intent`) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent intent is immutable';
    END IF;
    IF OLD.`consent_intent` IS NULL
       AND NEW.`consent_intent` IS NOT NULL
       AND NOT (OLD.`status` = 'PENDING'
                AND NEW.`status` IN ('APPROVED', 'DENIED')) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent intent must be set by a decision transition';
    END IF;
END$$

DROP PROCEDURE IF EXISTS `u3w_finalize_ib_oauth_consent_intent_20260721`$$
CREATE PROCEDURE `u3w_finalize_ib_oauth_consent_intent_20260721`()
BEGIN
    DECLARE migration_lock_name CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE migration_lock_released INT DEFAULT 0;
    DECLARE lock_owned TINYINT DEFAULT 0;
    DECLARE server_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE original_group_concat_max_len BIGINT UNSIGNED DEFAULT 0;
    DECLARE group_concat_limit_changed TINYINT DEFAULT 0;
    DECLARE foundation_receipt_count INT DEFAULT 0;
    DECLARE provenance_receipt_count INT DEFAULT 0;
    DECLARE migration_receipt_count INT DEFAULT 0;
    DECLARE migration_state VARCHAR(255) DEFAULT NULL;
    DECLARE request_stage_complete TINYINT DEFAULT 0;
    DECLARE code_stage_complete TINYINT DEFAULT 0;
    DECLARE family_stage_complete TINYINT DEFAULT 0;
    DECLARE trigger_stage_complete TINYINT DEFAULT 0;
    DECLARE invalid_request_count BIGINT DEFAULT 0;
    DECLARE downstream_row_count BIGINT DEFAULT 0;
    DECLARE invalid_receipt_count BIGINT DEFAULT 0;
    DECLARE invalid_lineage_count BIGINT DEFAULT 0;
    DECLARE migration_stage VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT 'finalizer-preflight';
    DECLARE diagnostic_errno INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 diagnostic_errno = MYSQL_ERRNO;
        ROLLBACK;
        IF group_concat_limit_changed = 1 THEN
            SET SESSION group_concat_max_len = original_group_concat_max_len;
            SET group_concat_limit_changed = 0;
        END IF;
        IF lock_owned = 1 THEN
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
            SET MESSAGE_TEXT = 'Independent Board OAuth consent-intent finalizer requires an explicit target database';
    END IF;
    SET server_version = VERSION();
    IF INSTR(LOWER(VERSION()), 'mariadb') > 0
       OR CAST(server_version AS BINARY) NOT IN (
           CAST('8.0.30' AS BINARY), CAST('8.4.8' AS BINARY))
       OR CAST(@@version_comment AS BINARY) <>
          CAST('MySQL Community Server - GPL' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth consent-intent finalizer supports exact MySQL baselines only';
    END IF;
    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260721_independent_board_oauth_consent_intent_lineage_v1'),
        256
    );
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent finalizer does not own the migration lock';
    END IF;
    SET lock_owned = 1;

    SET original_group_concat_max_len = @@SESSION.group_concat_max_len;
    IF original_group_concat_max_len < 1048576 THEN
        SET SESSION group_concat_max_len = 1048576;
        SET group_concat_limit_changed = 1;
    END IF;

    SELECT COUNT(*) INTO foundation_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_foundation_v1'
      AND `description` = 'Independent Board OAuth client, authorization, token family and immutable receipt tables';
    SELECT COUNT(*) INTO provenance_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_receipt_provenance_v1'
      AND `description` = 'APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness';
    IF foundation_receipt_count <> 1 OR provenance_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent finalizer lost an exact prerequisite receipt';
    END IF;

    SELECT COUNT(*), MAX(`description`)
      INTO migration_receipt_count, migration_state
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_consent_intent_lineage_v1';
    IF migration_receipt_count <> 1
       OR CAST(migration_state AS BINARY) NOT IN (
           CAST('RUNNING:Independent Board OAuth consent intent lineage' AS BINARY),
           CAST('APPLIED:Independent Board OAuth consent intent lineage' AS BINARY)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent finalizer receipt state is missing or drifted';
    END IF;

    SET migration_stage = 'final-raw-metadata-current-read';
    CALL `u3w_assert_ib_oauth_consent_intent_20260721`(
        request_stage_complete, code_stage_complete,
        family_stage_complete, trigger_stage_complete
    );
    IF request_stage_complete <> 1 OR code_stage_complete <> 1
       OR family_stage_complete <> 1 OR trigger_stage_complete <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent final raw metadata current-read is incomplete';
    END IF;

    SET migration_stage = 'final-data-current-read';
    IF CAST(migration_state AS BINARY) =
       CAST('RUNNING:Independent Board OAuth consent intent lineage' AS BINARY) THEN
        SELECT COUNT(*) INTO invalid_request_count
        FROM `fbs_oauth_authorization_request`
        WHERE CAST(`status` AS BINARY) NOT IN (CAST('PENDING' AS BINARY), CAST('EXPIRED' AS BINARY))
           OR `enterprise_id` IS NOT NULL OR `member_id` IS NOT NULL OR `user_id` IS NOT NULL
           OR `principal_subject_digest` IS NOT NULL OR `consent_intent` IS NOT NULL
           OR `approved_at` IS NOT NULL OR `denied_at` IS NOT NULL OR `consumed_at` IS NOT NULL;
        SELECT
            (SELECT COUNT(*) FROM `fbs_oauth_authorization_code`)
          + (SELECT COUNT(*) FROM `fbs_oauth_token_family`)
          + (SELECT COUNT(*) FROM `fbs_oauth_token`)
          INTO downstream_row_count;
        SELECT COUNT(*) INTO invalid_receipt_count
        FROM `fbs_oauth_receipt`
        WHERE CAST(`action` AS BINARY) NOT IN (
            CAST('OAUTH_CLIENT_REGISTERED' AS BINARY),
            CAST('OAUTH_CLIENT_REVOKED' AS BINARY)
        );
        IF invalid_request_count <> 0 OR downstream_row_count <> 0 OR invalid_receipt_count <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'OAuth legacy data changed while consent-intent migration was RUNNING';
        END IF;
    ELSE
        SELECT
            (SELECT COUNT(*)
             FROM `fbs_oauth_authorization_code` c
             LEFT JOIN `fbs_oauth_authorization_request` r
               ON r.`id` = c.`authorization_request_id`
              AND r.`consent_intent` = c.`consent_intent`
             WHERE r.`id` IS NULL)
          + (SELECT COUNT(*)
             FROM `fbs_oauth_token_family` f
             LEFT JOIN `fbs_oauth_authorization_code` c
               ON c.`id` = f.`origin_authorization_code_id`
              AND c.`consent_intent` = f.`consent_intent`
             WHERE c.`id` IS NULL)
          INTO invalid_lineage_count;
        IF invalid_lineage_count <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'OAuth APPLIED consent-intent lineage has drifted';
        END IF;
    END IF;

    SET migration_stage = 'receipt-finalization';
    START TRANSACTION;
    SELECT `description` INTO migration_state
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_consent_intent_lineage_v1'
    FOR UPDATE;
    IF CAST(migration_state AS BINARY) =
       CAST('RUNNING:Independent Board OAuth consent intent lineage' AS BINARY) THEN
        UPDATE `u3w_schema_migration`
        SET `description` = 'APPLIED:Independent Board OAuth consent intent lineage',
            `applied_at` = CURRENT_TIMESTAMP
        WHERE `version` = '20260721_independent_board_oauth_consent_intent_lineage_v1'
          AND `description` = 'RUNNING:Independent Board OAuth consent intent lineage';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'OAuth consent-intent receipt promotion lost its expected state';
        END IF;
    ELSEIF CAST(migration_state AS BINARY) <>
           CAST('APPLIED:Independent Board OAuth consent intent lineage' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent receipt changed during finalization';
    END IF;
    SELECT COUNT(*) INTO migration_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_consent_intent_lineage_v1'
      AND `description` = 'APPLIED:Independent Board OAuth consent intent lineage';
    IF migration_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent APPLIED receipt is not exact';
    END IF;
    COMMIT;

    IF group_concat_limit_changed = 1 THEN
        SET SESSION group_concat_max_len = original_group_concat_max_len;
        SET group_concat_limit_changed = 0;
    END IF;
    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_released;
    IF migration_lock_released IS NULL OR migration_lock_released <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth consent-intent migration advisory lock release failed';
    END IF;
    SET lock_owned = 0;
END$$

CALL `u3w_finalize_ib_oauth_consent_intent_20260721`()$$
DROP PROCEDURE IF EXISTS `u3w_finalize_ib_oauth_consent_intent_20260721`$$
DROP PROCEDURE IF EXISTS `u3w_assert_ib_oauth_consent_intent_20260721`$$

DELIMITER ;
