-- ============================================================
-- FBSir Independent Board OAuth / MCP authorization foundation
-- Migration: 20260721_independent_board_oauth_foundation_v1
-- Public manifest step: public_init_033
-- Target: exact MySQL 8.0.30 or 8.4.8 metadata baselines only
-- Scope: restricted public clients, authorization transactions, opaque
--        token families and immutable OAuth receipts. No public route.
-- Precondition: public_init_032 and its exact internal receipt exist.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_oauth_foundation_20260721`$$
CREATE PROCEDURE `u3w_migrate_independent_board_oauth_foundation_20260721`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE current_connection BIGINT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE prerequisite_receipt_count INT DEFAULT 0;
    DECLARE prerequisite_table_count INT DEFAULT 0;
    DECLARE prerequisite_control_plane_receipt_count INT DEFAULT 0;
    DECLARE prerequisite_dependency_table_count INT DEFAULT 0;
    DECLARE prerequisite_dependency_column_count INT DEFAULT 0;
    DECLARE prerequisite_dependency_index_count INT DEFAULT 0;
    DECLARE prerequisite_binding_fk_count INT DEFAULT 0;
    DECLARE target_table_count INT DEFAULT 0;
    DECLARE target_base_table_count INT DEFAULT 0;
    DECLARE target_engine_count INT DEFAULT 0;
    DECLARE target_collation_count INT DEFAULT 0;
    DECLARE target_total_column_count INT DEFAULT 0;
    DECLARE target_column_order_count INT DEFAULT 0;
    DECLARE target_digest_column_count INT DEFAULT 0;
    DECLARE target_generated_column_count INT DEFAULT 0;
    DECLARE target_index_count INT DEFAULT 0;
    DECLARE target_index_contract_count INT DEFAULT 0;
    DECLARE target_foreign_key_count INT DEFAULT 0;
    DECLARE target_foreign_key_contract_count INT DEFAULT 0;
    DECLARE target_foreign_key_column_count INT DEFAULT 0;
    DECLARE target_foreign_key_column_contract_count INT DEFAULT 0;
    DECLARE target_check_count INT DEFAULT 0;
    DECLARE target_enforced_check_count INT DEFAULT 0;
    DECLARE target_check_name_count INT DEFAULT 0;
    DECLARE target_trigger_count INT DEFAULT 0;
    DECLARE target_trigger_contract_count INT DEFAULT 0;
    DECLARE target_column_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_index_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_foreign_key_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE target_check_contract_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE original_group_concat_max_len BIGINT UNSIGNED DEFAULT 0;
    DECLARE group_concat_limit_changed TINYINT DEFAULT 0;
    DECLARE server_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
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
            SET MESSAGE_TEXT = 'Independent Board OAuth foundation migration requires an explicit target database';
    END IF;

    SET server_version = VERSION();
    IF INSTR(LOWER(VERSION()), 'mariadb') > 0
       OR CAST(server_version AS BINARY) NOT IN (
           CAST('8.0.30' AS BINARY), CAST('8.4.8' AS BINARY))
       OR CAST(@@version_comment AS BINARY) <>
          CAST('MySQL Community Server - GPL' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth foundation supports exact MySQL 8.0.30 or 8.4.8 baselines';
    END IF;

    SET original_group_concat_max_len = @@SESSION.group_concat_max_len;
    IF original_group_concat_max_len < 1048576 THEN
        SET SESSION group_concat_max_len = 1048576;
        SET group_concat_limit_changed = 1;
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260721_independent_board_oauth_foundation_v1'),
        256
    );
    IF CHAR_LENGTH(migration_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth foundation lock name must be a stable 64-character digest';
    END IF;
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the Independent Board OAuth foundation migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name), CONNECTION_ID()
      INTO migration_lock_owner, current_connection;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth foundation migration lock is not owned by the current connection';
    END IF;

    SET migration_stage = 'w4a-prerequisite-audit';
    SELECT COUNT(*) INTO prerequisite_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_connector_binding_v1'
      AND `description` = 'Independent Board authoritative Connector binding, scope and receipt tables';
    SELECT COUNT(*) INTO prerequisite_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_type = 'BASE TABLE'
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      );
    IF prerequisite_receipt_count <> 1 OR prerequisite_table_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth foundation requires the completed W4a binding migration';
    END IF;

    SET migration_stage = 'external-dependency-contract-audit';
    SELECT COUNT(*) INTO prerequisite_control_plane_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260720_independent_board_control_plane_v1'
      AND `description` = 'Independent Board generic product plan, entitlement, budget, operation and receipt control plane';
    SELECT COUNT(*) INTO prerequisite_dependency_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB'
      AND table_collation = 'utf8mb4_unicode_ci'
      AND table_name IN ('fbs_product_entitlement', 'fbs_connector_binding');
    SELECT COUNT(*) INTO prerequisite_dependency_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND (
        (table_name = 'fbs_product_entitlement'
         AND column_name IN ('enterprise_id', 'member_id')
         AND column_type = 'bigint unsigned' AND is_nullable = 'NO'
         AND column_default IS NULL AND extra = '')
        OR (table_name = 'fbs_product_entitlement' AND column_name = 'product_code'
            AND column_type = 'varchar(64)' AND is_nullable = 'NO'
            AND column_default IS NULL AND extra = ''
            AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci')
        OR (table_name = 'fbs_connector_binding' AND column_name = 'binding_id'
            AND column_type = 'varchar(128)' AND is_nullable = 'NO'
            AND column_default IS NULL AND extra = ''
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin')
        OR (table_name = 'fbs_connector_binding'
            AND column_name IN ('enterprise_id', 'member_id', 'user_id')
            AND column_type = 'bigint unsigned' AND is_nullable = 'NO'
            AND column_default IS NULL AND extra = '')
      );
    SELECT COUNT(*) INTO prerequisite_dependency_index_count
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
          AND ((table_name = 'fbs_product_entitlement'
                AND index_name = 'uk_product_entitlement_scope')
            OR (table_name = 'fbs_connector_binding'
                AND index_name = 'uk_connector_binding_receipt_scope'))
        GROUP BY table_name, index_name, non_unique, index_type
    ) dependency_indexes
    WHERE non_unique = 0 AND index_type = 'BTREE'
      AND visibility = 'YES' AND partial_columns = 0 AND non_column_key_parts = 0
      AND ((table_name = 'fbs_product_entitlement'
            AND index_name = 'uk_product_entitlement_scope'
            AND key_part_count = 3
            AND CAST(column_signature AS BINARY) = CAST('enterprise_id:A,member_id:A,product_code:A' AS BINARY))
        OR (table_name = 'fbs_connector_binding'
            AND index_name = 'uk_connector_binding_receipt_scope'
            AND key_part_count = 4
            AND CAST(column_signature AS BINARY) = CAST('binding_id:A,enterprise_id:A,member_id:A,user_id:A' AS BINARY)));
    SELECT COUNT(*) INTO prerequisite_binding_fk_count
    FROM (
        SELECT rc.table_name, rc.constraint_name, rc.referenced_table_name,
               rc.unique_constraint_schema, rc.update_rule, rc.delete_rule,
               GROUP_CONCAT(CONCAT(kcu.column_name, '>', kcu.referenced_column_name)
                            ORDER BY kcu.ordinal_position SEPARATOR ',') AS column_signature
        FROM information_schema.referential_constraints rc
        INNER JOIN information_schema.key_column_usage kcu
          ON kcu.constraint_schema = rc.constraint_schema
         AND kcu.table_name = rc.table_name
         AND kcu.constraint_name = rc.constraint_name
        WHERE rc.constraint_schema = DATABASE()
          AND kcu.referenced_table_schema = DATABASE()
          AND rc.table_name = 'fbs_connector_binding'
          AND rc.constraint_name = 'fk_connector_binding_entitlement'
        GROUP BY rc.table_name, rc.constraint_name, rc.referenced_table_name,
                 rc.unique_constraint_schema, rc.update_rule, rc.delete_rule
    ) binding_dependency_fk
    WHERE unique_constraint_schema = DATABASE()
      AND referenced_table_name = 'fbs_product_entitlement'
      AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'
      AND column_signature = 'enterprise_id>enterprise_id,member_id>member_id,product_code>product_code';
    IF prerequisite_control_plane_receipt_count <> 1
       OR prerequisite_dependency_table_count <> 2
       OR prerequisite_dependency_column_count <> 7
       OR prerequisite_dependency_index_count <> 2
       OR prerequisite_binding_fk_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board OAuth external FK dependency contract has drifted';
    END IF;

    SET migration_stage = 'partial-state-audit';
    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_foundation_v1';
    SELECT COUNT(*) INTO target_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client',
          'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code',
          'fbs_oauth_token_family',
          'fbs_oauth_token',
          'fbs_oauth_receipt'
      );
    IF migration_exists = 0 AND target_table_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation tables exist without the exact migration receipt; audit before continuing';
    END IF;
    IF migration_exists <> 0 AND target_table_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation migration receipt exists but its six-table set is incomplete';
    END IF;
    IF migration_exists = 0 THEN
        SELECT COUNT(*) INTO target_trigger_count
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND trigger_name IN (
              'trg_oauth_receipt_no_update',
              'trg_oauth_receipt_no_delete'
          );
        IF target_trigger_count <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'OAuth receipt trigger names collide before first apply';
        END IF;
    END IF;

    IF migration_exists = 0 THEN
        SET migration_stage = 'first-apply-ddl';

        CREATE TABLE IF NOT EXISTS `fbs_oauth_client` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `client_id` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `client_name` VARCHAR(128) NOT NULL,
            `issuer_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `resource_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `product_code` VARCHAR(64) NOT NULL,
            `source_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `connector_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `redirect_port` SMALLINT UNSIGNED NOT NULL,
            `redirect_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `token_endpoint_auth_method` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `grant_types_canonical` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `response_types_canonical` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `scope_canonical` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `scope_digest` BINARY(32) NOT NULL,
            `metadata_digest` BINARY(32) NOT NULL,
            `registration_source_digest` BINARY(32) NOT NULL,
            `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `registered_at` DATETIME(3) NOT NULL,
            `expires_at` DATETIME(3) NOT NULL,
            `terminated_at` DATETIME(3) DEFAULT NULL,
            `version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_oauth_client_id` (`client_id`),
            UNIQUE KEY `uk_oauth_client_redirect` (`client_id`, `redirect_uri`),
            KEY `idx_oauth_client_status_expiry` (`status`, `expires_at`),
            KEY `idx_oauth_client_source_time` (`registration_source_digest`, `registered_at`),
            CONSTRAINT `chk_oauth_client_id`
                CHECK (`client_id` REGEXP '^[A-Za-z0-9_-]{43,191}$'),
            CONSTRAINT `chk_oauth_client_fixed_profile`
                CHECK (CAST(`client_name` AS BINARY) = CAST('未验证的本地公共客户端' AS BINARY)
                    AND `issuer_uri` = 'https://api2.u3w.com'
                    AND `resource_uri` = 'https://api2.u3w.com/fbs-mcp/mcp'
                    AND CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
                    AND `source_code` = 'WORKBUDDY'
                    AND `connector_code` = 'fbs-connector'
                    AND `token_endpoint_auth_method` = 'none'
                    AND `grant_types_canonical` = 'authorization_code refresh_token'
                    AND `response_types_canonical` = 'code'),
            CONSTRAINT `chk_oauth_client_redirect`
                CHECK (`redirect_port` BETWEEN 1024 AND 65535
                    AND `redirect_uri` = CONCAT(
                        'http://127.0.0.1:', `redirect_port`, '/oauth/callback')),
            CONSTRAINT `chk_oauth_client_scope`
                CHECK (`scope_canonical` = 'identity.read entitlement.read board.meeting.reserve board.receipt.write'
                    AND `scope_digest` = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1')),
            CONSTRAINT `chk_oauth_client_status`
                CHECK (`status` IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
            CONSTRAINT `chk_oauth_client_lifetime`
                CHECK (`expires_at` = TIMESTAMPADD(DAY, 31, `registered_at`)),
            CONSTRAINT `chk_oauth_client_lifecycle`
                CHECK ((`status` = 'ACTIVE' AND `terminated_at` IS NULL)
                    OR (`status` = 'REVOKED'
                        AND `terminated_at` IS NOT NULL
                        AND `terminated_at` >= `registered_at`)
                    OR (`status` = 'EXPIRED'
                        AND `terminated_at` IS NOT NULL
                        AND `terminated_at` >= `expires_at`))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Restricted unverified local OAuth public clients';

        CREATE TABLE IF NOT EXISTS `fbs_oauth_authorization_request` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `request_handle_digest` BINARY(32) NOT NULL,
            `client_id` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `redirect_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `code_challenge` CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `code_challenge_method` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `state_digest` BINARY(32) NOT NULL,
            `state_key_ref` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
            `state_nonce` BINARY(12) DEFAULT NULL,
            `state_ciphertext` VARBINARY(528) DEFAULT NULL,
            `issuer_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `resource_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `product_code` VARCHAR(64) NOT NULL,
            `source_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `connector_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `scope_canonical` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `scope_digest` BINARY(32) NOT NULL,
            `principal_subject_digest` BINARY(32) DEFAULT NULL,
            `enterprise_id` BIGINT UNSIGNED DEFAULT NULL,
            `member_id` BIGINT UNSIGNED DEFAULT NULL,
            `user_id` BIGINT UNSIGNED DEFAULT NULL,
            `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `requested_at` DATETIME(3) NOT NULL,
            `expires_at` DATETIME(3) NOT NULL,
            `approved_at` DATETIME(3) DEFAULT NULL,
            `denied_at` DATETIME(3) DEFAULT NULL,
            `consumed_at` DATETIME(3) DEFAULT NULL,
            `version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_oauth_request_handle` (`request_handle_digest`),
            UNIQUE KEY `uk_oauth_request_state` (`state_digest`),
            UNIQUE KEY `uk_oauth_request_code_context`
                (`id`, `client_id`, `redirect_uri`, `code_challenge`,
                 `principal_subject_digest`, `enterprise_id`, `member_id`, `user_id`),
            UNIQUE KEY `uk_oauth_request_receipt_scope`
                (`id`, `client_id`, `principal_subject_digest`, `enterprise_id`, `member_id`, `user_id`),
            KEY `idx_oauth_request_client_state` (`client_id`, `status`, `expires_at`),
            KEY `idx_oauth_request_identity_state`
                (`enterprise_id`, `member_id`, `user_id`, `status`),
            KEY `idx_oauth_request_client_redirect` (`client_id`, `redirect_uri`),
            KEY `idx_oauth_request_entitlement`
                (`enterprise_id`, `member_id`, `product_code`),
            CONSTRAINT `fk_oauth_request_client_redirect`
                FOREIGN KEY (`client_id`, `redirect_uri`)
                REFERENCES `fbs_oauth_client` (`client_id`, `redirect_uri`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `fk_oauth_request_entitlement`
                FOREIGN KEY (`enterprise_id`, `member_id`, `product_code`)
                REFERENCES `fbs_product_entitlement` (`enterprise_id`, `member_id`, `product_code`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_oauth_request_fixed_profile`
                CHECK (`issuer_uri` = 'https://api2.u3w.com'
                    AND `resource_uri` = 'https://api2.u3w.com/fbs-mcp/mcp'
                    AND CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
                    AND `source_code` = 'WORKBUDDY'
                    AND `connector_code` = 'fbs-connector'
                    AND `code_challenge_method` = 'S256'),
            CONSTRAINT `chk_oauth_request_pkce`
                CHECK (`code_challenge` REGEXP '^[A-Za-z0-9_-]{43}$'),
            CONSTRAINT `chk_oauth_request_scope`
                CHECK (`scope_canonical` = 'identity.read entitlement.read board.meeting.reserve board.receipt.write'
                    AND `scope_digest` = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1')),
            CONSTRAINT `chk_oauth_request_crypto_tuple`
                CHECK ((`state_key_ref` IS NOT NULL
                        AND `state_nonce` IS NOT NULL
                        AND `state_ciphertext` IS NOT NULL
                        AND OCTET_LENGTH(`state_ciphertext`) BETWEEN 32 AND 528)
                    OR (`state_key_ref` IS NULL
                        AND `state_nonce` IS NULL
                        AND `state_ciphertext` IS NULL)),
            CONSTRAINT `chk_oauth_request_lifetime`
                CHECK (`expires_at` = TIMESTAMPADD(MINUTE, 5, `requested_at`)),
            CONSTRAINT `chk_oauth_request_lifecycle`
                CHECK ((`status` = 'PENDING'
                        AND `principal_subject_digest` IS NULL
                        AND `enterprise_id` IS NULL AND `member_id` IS NULL AND `user_id` IS NULL
                        AND `approved_at` IS NULL AND `denied_at` IS NULL AND `consumed_at` IS NULL
                        AND `state_ciphertext` IS NOT NULL)
                    OR (`status` = 'APPROVED'
                        AND `principal_subject_digest` IS NOT NULL
                        AND `enterprise_id` IS NOT NULL AND `member_id` IS NOT NULL AND `user_id` IS NOT NULL
                        AND `approved_at` IS NOT NULL
                        AND `approved_at` BETWEEN `requested_at` AND `expires_at`
                        AND `denied_at` IS NULL AND `consumed_at` IS NULL
                        AND `state_ciphertext` IS NOT NULL)
                    OR (`status` = 'DENIED'
                        AND `principal_subject_digest` IS NOT NULL
                        AND `enterprise_id` IS NOT NULL AND `member_id` IS NOT NULL AND `user_id` IS NOT NULL
                        AND `approved_at` IS NULL
                        AND `denied_at` IS NOT NULL
                        AND `denied_at` BETWEEN `requested_at` AND `expires_at`
                        AND `consumed_at` IS NULL AND `state_ciphertext` IS NULL)
                    OR (`status` = 'CONSUMED'
                        AND `principal_subject_digest` IS NOT NULL
                        AND `enterprise_id` IS NOT NULL AND `member_id` IS NOT NULL AND `user_id` IS NOT NULL
                        AND `approved_at` IS NOT NULL
                        AND `approved_at` BETWEEN `requested_at` AND `expires_at`
                        AND `consumed_at` IS NOT NULL
                        AND `consumed_at` BETWEEN `approved_at` AND `expires_at`
                        AND `denied_at` IS NULL AND `state_ciphertext` IS NULL)
                    OR (`status` = 'EXPIRED'
                        AND `denied_at` IS NULL AND `consumed_at` IS NULL
                        AND `state_ciphertext` IS NULL
                        AND ((`approved_at` IS NULL
                              AND `principal_subject_digest` IS NULL
                              AND `enterprise_id` IS NULL AND `member_id` IS NULL AND `user_id` IS NULL)
                            OR (`approved_at` IS NOT NULL
                                AND `approved_at` BETWEEN `requested_at` AND `expires_at`
                                AND `principal_subject_digest` IS NOT NULL
                                AND `enterprise_id` IS NOT NULL AND `member_id` IS NOT NULL AND `user_id` IS NOT NULL))))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Single-use OAuth authorization request handles and encrypted state';

        CREATE TABLE IF NOT EXISTS `fbs_oauth_authorization_code` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `code_digest` BINARY(32) NOT NULL,
            `authorization_request_id` BIGINT UNSIGNED NOT NULL,
            `client_id` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `redirect_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `code_challenge` CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `code_challenge_method` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `issuer_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `resource_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `product_code` VARCHAR(64) NOT NULL,
            `source_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `connector_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `scope_canonical` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `scope_digest` BINARY(32) NOT NULL,
            `principal_subject_digest` BINARY(32) NOT NULL,
            `enterprise_id` BIGINT UNSIGNED NOT NULL,
            `member_id` BIGINT UNSIGNED NOT NULL,
            `user_id` BIGINT UNSIGNED NOT NULL,
            `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `issued_at` DATETIME(3) NOT NULL,
            `expires_at` DATETIME(3) NOT NULL,
            `used_at` DATETIME(3) DEFAULT NULL,
            `revoked_at` DATETIME(3) DEFAULT NULL,
            `version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_oauth_code_digest` (`code_digest`),
            UNIQUE KEY `uk_oauth_code_request` (`authorization_request_id`),
            UNIQUE KEY `uk_oauth_code_family_context`
                (`id`, `client_id`, `principal_subject_digest`,
                 `enterprise_id`, `member_id`, `user_id`, `resource_uri`, `scope_digest`),
            UNIQUE KEY `uk_oauth_code_receipt_scope`
                (`id`, `client_id`, `principal_subject_digest`, `enterprise_id`, `member_id`, `user_id`),
            KEY `idx_oauth_code_client_state` (`client_id`, `status`, `expires_at`),
            KEY `idx_oauth_code_identity_state`
                (`enterprise_id`, `member_id`, `user_id`, `status`),
            KEY `idx_oauth_code_request_context`
                (`authorization_request_id`, `client_id`, `redirect_uri`, `code_challenge`,
                 `principal_subject_digest`, `enterprise_id`, `member_id`, `user_id`),
            KEY `idx_oauth_code_entitlement`
                (`enterprise_id`, `member_id`, `product_code`),
            CONSTRAINT `fk_oauth_code_request_context`
                FOREIGN KEY (`authorization_request_id`, `client_id`, `redirect_uri`, `code_challenge`,
                             `principal_subject_digest`, `enterprise_id`, `member_id`, `user_id`)
                REFERENCES `fbs_oauth_authorization_request`
                    (`id`, `client_id`, `redirect_uri`, `code_challenge`,
                     `principal_subject_digest`, `enterprise_id`, `member_id`, `user_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `fk_oauth_code_entitlement`
                FOREIGN KEY (`enterprise_id`, `member_id`, `product_code`)
                REFERENCES `fbs_product_entitlement` (`enterprise_id`, `member_id`, `product_code`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_oauth_code_fixed_profile`
                CHECK (`issuer_uri` = 'https://api2.u3w.com'
                    AND `resource_uri` = 'https://api2.u3w.com/fbs-mcp/mcp'
                    AND CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
                    AND `source_code` = 'WORKBUDDY'
                    AND `connector_code` = 'fbs-connector'
                    AND `code_challenge_method` = 'S256'
                    AND `code_challenge` REGEXP '^[A-Za-z0-9_-]{43}$'),
            CONSTRAINT `chk_oauth_code_scope`
                CHECK (`scope_canonical` = 'identity.read entitlement.read board.meeting.reserve board.receipt.write'
                    AND `scope_digest` = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1')),
            CONSTRAINT `chk_oauth_code_lifetime`
                CHECK (`expires_at` = TIMESTAMPADD(SECOND, 60, `issued_at`)),
            CONSTRAINT `chk_oauth_code_lifecycle`
                CHECK ((`status` = 'ACTIVE' AND `used_at` IS NULL AND `revoked_at` IS NULL)
                    OR (`status` = 'USED'
                        AND `used_at` IS NOT NULL
                        AND `used_at` BETWEEN `issued_at` AND `expires_at`
                        AND `revoked_at` IS NULL)
                    OR (`status` = 'REVOKED'
                        AND `used_at` IS NULL
                        AND `revoked_at` IS NOT NULL
                        AND `revoked_at` BETWEEN `issued_at` AND `expires_at`)
                    OR (`status` = 'EXPIRED' AND `used_at` IS NULL AND `revoked_at` IS NULL))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Digest-only single-use OAuth authorization codes';

        CREATE TABLE IF NOT EXISTS `fbs_oauth_token_family` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `family_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `origin_authorization_code_id` BIGINT UNSIGNED NOT NULL,
            `client_id` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `enterprise_id` BIGINT UNSIGNED NOT NULL,
            `member_id` BIGINT UNSIGNED NOT NULL,
            `user_id` BIGINT UNSIGNED NOT NULL,
            `product_code` VARCHAR(64) NOT NULL,
            `source_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `connector_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `issuer_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `resource_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `scope_canonical` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `scope_digest` BINARY(32) NOT NULL,
            `principal_subject_digest` BINARY(32) NOT NULL,
            `binding_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
            `binding_version` BIGINT UNSIGNED DEFAULT NULL,
            `status` VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `lifecycle_slot` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
                GENERATED ALWAYS AS (
                    CASE
                        WHEN `status` = 'ACTIVE' THEN 'ACTIVE'
                        WHEN `status` = 'PENDING_BINDING' THEN 'PENDING_BINDING'
                        ELSE NULL
                    END
                ) STORED,
            `current_refresh_generation` INT UNSIGNED NOT NULL DEFAULT 0,
            `issued_at` DATETIME(3) NOT NULL,
            `activated_at` DATETIME(3) DEFAULT NULL,
            `expires_at` DATETIME(3) NOT NULL,
            `terminated_at` DATETIME(3) DEFAULT NULL,
            `version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_oauth_family_id` (`family_id`),
            UNIQUE KEY `uk_oauth_family_origin_code` (`origin_authorization_code_id`),
            UNIQUE KEY `uk_oauth_family_live_slot`
                (`enterprise_id`, `member_id`, `product_code`,
                 `source_code`, `connector_code`, `lifecycle_slot`),
            UNIQUE KEY `uk_oauth_family_receipt_scope`
                (`family_id`, `client_id`, `principal_subject_digest`,
                 `enterprise_id`, `member_id`, `user_id`),
            KEY `idx_oauth_family_binding_state` (`binding_id`, `status`),
            KEY `idx_oauth_family_client_state` (`client_id`, `status`, `expires_at`),
            KEY `idx_oauth_family_identity_state`
                (`enterprise_id`, `member_id`, `user_id`, `status`),
            KEY `idx_oauth_family_origin_context`
                (`origin_authorization_code_id`, `client_id`, `principal_subject_digest`,
                 `enterprise_id`, `member_id`, `user_id`, `resource_uri`, `scope_digest`),
            KEY `idx_oauth_family_binding_scope`
                (`binding_id`, `enterprise_id`, `member_id`, `user_id`),
            CONSTRAINT `fk_oauth_family_origin_code_context`
                FOREIGN KEY (`origin_authorization_code_id`, `client_id`, `principal_subject_digest`,
                             `enterprise_id`, `member_id`, `user_id`, `resource_uri`, `scope_digest`)
                REFERENCES `fbs_oauth_authorization_code`
                    (`id`, `client_id`, `principal_subject_digest`,
                     `enterprise_id`, `member_id`, `user_id`, `resource_uri`, `scope_digest`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `fk_oauth_family_entitlement`
                FOREIGN KEY (`enterprise_id`, `member_id`, `product_code`)
                REFERENCES `fbs_product_entitlement` (`enterprise_id`, `member_id`, `product_code`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `fk_oauth_family_binding_scope`
                FOREIGN KEY (`binding_id`, `enterprise_id`, `member_id`, `user_id`)
                REFERENCES `fbs_connector_binding` (`binding_id`, `enterprise_id`, `member_id`, `user_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_oauth_family_id`
                CHECK (CHAR_LENGTH(`family_id`) BETWEEN 1 AND 128),
            CONSTRAINT `chk_oauth_family_fixed_profile`
                CHECK (`issuer_uri` = 'https://api2.u3w.com'
                    AND `resource_uri` = 'https://api2.u3w.com/fbs-mcp/mcp'
                    AND CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
                    AND `source_code` = 'WORKBUDDY'
                    AND `connector_code` = 'fbs-connector'),
            CONSTRAINT `chk_oauth_family_scope`
                CHECK (`scope_canonical` = 'identity.read entitlement.read board.meeting.reserve board.receipt.write'
                    AND `scope_digest` = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1')),
            CONSTRAINT `chk_oauth_family_lifetime`
                CHECK (`expires_at` > `issued_at`
                    AND `expires_at` <= TIMESTAMPADD(DAY, 30, `issued_at`)),
            CONSTRAINT `chk_oauth_family_binding_pair`
                CHECK ((`binding_id` IS NULL AND `binding_version` IS NULL)
                    OR (`binding_id` IS NOT NULL AND `binding_version` IS NOT NULL)),
            CONSTRAINT `chk_oauth_family_lifecycle`
                CHECK ((`status` = 'PENDING_BINDING'
                        AND `binding_id` IS NULL AND `activated_at` IS NULL AND `terminated_at` IS NULL)
                    OR (`status` = 'ACTIVE'
                        AND `binding_id` IS NOT NULL
                        AND `activated_at` IS NOT NULL
                        AND `activated_at` BETWEEN `issued_at` AND `expires_at`
                        AND `terminated_at` IS NULL)
                    OR (`status` IN ('REVOKED', 'COMPROMISED', 'EXPIRED')
                        AND `terminated_at` IS NOT NULL
                        AND `terminated_at` >= `issued_at`
                        AND ((`binding_id` IS NULL AND `activated_at` IS NULL)
                            OR (`binding_id` IS NOT NULL
                                AND `activated_at` IS NOT NULL
                                AND `terminated_at` >= `activated_at`
                                AND `activated_at` BETWEEN `issued_at` AND `expires_at`))))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='OAuth refresh-token families with one ACTIVE and one PENDING_BINDING slot';

        CREATE TABLE IF NOT EXISTS `fbs_oauth_token` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `token_digest` BINARY(32) NOT NULL,
            `family_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `token_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `generation` INT UNSIGNED NOT NULL,
            `resource_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `scope_canonical` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `scope_digest` BINARY(32) NOT NULL,
            `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `active_refresh_slot` TINYINT UNSIGNED
                GENERATED ALWAYS AS (
                    CASE
                        WHEN `token_type` = 'REFRESH' AND `status` = 'ACTIVE' THEN 1
                        ELSE NULL
                    END
                ) STORED,
            `issued_at` DATETIME(3) NOT NULL,
            `used_at` DATETIME(3) DEFAULT NULL,
            `revoked_at` DATETIME(3) DEFAULT NULL,
            `expires_at` DATETIME(3) NOT NULL,
            `version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_oauth_token_digest` (`token_digest`),
            UNIQUE KEY `uk_oauth_token_family_type_generation` (`family_id`, `token_type`, `generation`),
            UNIQUE KEY `uk_oauth_token_active_refresh` (`family_id`, `active_refresh_slot`),
            UNIQUE KEY `uk_oauth_token_receipt_scope` (`id`, `family_id`),
            KEY `idx_oauth_token_family_state` (`family_id`, `status`, `expires_at`),
            KEY `idx_oauth_token_state_expiry` (`status`, `expires_at`),
            CONSTRAINT `fk_oauth_token_family`
                FOREIGN KEY (`family_id`)
                REFERENCES `fbs_oauth_token_family` (`family_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_oauth_token_type`
                CHECK (`token_type` IN ('ACCESS', 'REFRESH')),
            CONSTRAINT `chk_oauth_token_scope`
                CHECK (`resource_uri` = 'https://api2.u3w.com/fbs-mcp/mcp'
                    AND `scope_canonical` = 'identity.read entitlement.read board.meeting.reserve board.receipt.write'
                    AND `scope_digest` = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1')),
            CONSTRAINT `chk_oauth_access_token_lifetime`
                CHECK (`token_type` <> 'ACCESS'
                    OR `expires_at` = TIMESTAMPADD(MINUTE, 10, `issued_at`)),
            CONSTRAINT `chk_oauth_token_temporal`
                CHECK (`expires_at` > `issued_at`
                    AND (`used_at` IS NULL OR `used_at` BETWEEN `issued_at` AND `expires_at`)
                    AND (`revoked_at` IS NULL OR `revoked_at` BETWEEN `issued_at` AND `expires_at`)),
            CONSTRAINT `chk_oauth_token_lifecycle`
                CHECK ((`token_type` = 'ACCESS'
                        AND `status` IN ('ACTIVE', 'REVOKED', 'EXPIRED')
                        AND ((`status` = 'REVOKED' AND `revoked_at` IS NOT NULL)
                            OR (`status` IN ('ACTIVE', 'EXPIRED') AND `revoked_at` IS NULL)))
                    OR (`token_type` = 'REFRESH'
                        AND ((`status` = 'ACTIVE' AND `used_at` IS NULL AND `revoked_at` IS NULL)
                            OR (`status` = 'USED' AND `used_at` IS NOT NULL AND `revoked_at` IS NULL)
                            OR (`status` = 'REVOKED' AND `used_at` IS NULL AND `revoked_at` IS NOT NULL)
                            OR (`status` = 'EXPIRED' AND `used_at` IS NULL AND `revoked_at` IS NULL))))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Digest-only opaque access and rotating refresh tokens';

        CREATE TABLE IF NOT EXISTS `fbs_oauth_receipt` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `receipt_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `action` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `client_id` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `authorization_request_id` BIGINT UNSIGNED DEFAULT NULL,
            `authorization_code_id` BIGINT UNSIGNED DEFAULT NULL,
            `family_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
            `token_id` BIGINT UNSIGNED DEFAULT NULL,
            `binding_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
            `enterprise_id` BIGINT UNSIGNED DEFAULT NULL,
            `member_id` BIGINT UNSIGNED DEFAULT NULL,
            `user_id` BIGINT UNSIGNED DEFAULT NULL,
            `principal_subject_digest` BINARY(32) DEFAULT NULL,
            `actor_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `actor_user_id` BIGINT UNSIGNED DEFAULT NULL,
            `actor_subject_digest` BINARY(32) NOT NULL,
            `correlation_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `payload_digest` BINARY(32) NOT NULL,
            `evidence_level` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_oauth_receipt_id` (`receipt_id`),
            KEY `idx_oauth_receipt_client` (`client_id`, `created_at`),
            KEY `idx_oauth_receipt_request` (`authorization_request_id`, `created_at`),
            KEY `idx_oauth_receipt_family` (`family_id`, `created_at`),
            KEY `idx_oauth_receipt_identity`
                (`enterprise_id`, `member_id`, `user_id`, `principal_subject_digest`, `created_at`),
            KEY `idx_oauth_receipt_action` (`action`, `created_at`),
            KEY `idx_oauth_receipt_request_scope`
                (`authorization_request_id`, `client_id`, `principal_subject_digest`,
                 `enterprise_id`, `member_id`, `user_id`),
            KEY `idx_oauth_receipt_code_scope`
                (`authorization_code_id`, `client_id`, `principal_subject_digest`,
                 `enterprise_id`, `member_id`, `user_id`),
            KEY `idx_oauth_receipt_family_scope`
                (`family_id`, `client_id`, `principal_subject_digest`,
                 `enterprise_id`, `member_id`, `user_id`),
            KEY `idx_oauth_receipt_token_family` (`token_id`, `family_id`),
            KEY `idx_oauth_receipt_binding_scope`
                (`binding_id`, `enterprise_id`, `member_id`, `user_id`),
            CONSTRAINT `fk_oauth_receipt_client`
                FOREIGN KEY (`client_id`)
                REFERENCES `fbs_oauth_client` (`client_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `fk_oauth_receipt_request_scope`
                FOREIGN KEY (`authorization_request_id`, `client_id`, `principal_subject_digest`,
                             `enterprise_id`, `member_id`, `user_id`)
                REFERENCES `fbs_oauth_authorization_request`
                    (`id`, `client_id`, `principal_subject_digest`, `enterprise_id`, `member_id`, `user_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `fk_oauth_receipt_code_scope`
                FOREIGN KEY (`authorization_code_id`, `client_id`, `principal_subject_digest`,
                             `enterprise_id`, `member_id`, `user_id`)
                REFERENCES `fbs_oauth_authorization_code`
                    (`id`, `client_id`, `principal_subject_digest`, `enterprise_id`, `member_id`, `user_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `fk_oauth_receipt_family_scope`
                FOREIGN KEY (`family_id`, `client_id`, `principal_subject_digest`,
                             `enterprise_id`, `member_id`, `user_id`)
                REFERENCES `fbs_oauth_token_family`
                    (`family_id`, `client_id`, `principal_subject_digest`, `enterprise_id`, `member_id`, `user_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `fk_oauth_receipt_token_family`
                FOREIGN KEY (`token_id`, `family_id`)
                REFERENCES `fbs_oauth_token` (`id`, `family_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `fk_oauth_receipt_binding_scope`
                FOREIGN KEY (`binding_id`, `enterprise_id`, `member_id`, `user_id`)
                REFERENCES `fbs_connector_binding` (`binding_id`, `enterprise_id`, `member_id`, `user_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_oauth_receipt_action`
                CHECK (CHAR_LENGTH(`receipt_id`) > 0
                    AND CHAR_LENGTH(`correlation_id`) > 0
                    AND `action` IN (
                    'OAUTH_CLIENT_REGISTERED', 'OAUTH_CLIENT_REVOKED',
                    'AUTHORIZATION_APPROVED', 'AUTHORIZATION_DENIED',
                    'AUTHORIZATION_CODE_ISSUED', 'AUTHORIZATION_CODE_REPLAY_DETECTED',
                    'TOKEN_FAMILY_CREATED', 'TOKEN_FAMILY_ACTIVATED',
                    'TOKEN_FAMILY_REAUTHORIZED', 'TOKEN_FAMILY_ROTATED',
                    'TOKEN_FAMILY_REVOKED', 'TOKEN_FAMILY_COMPROMISED',
                    'REFRESH_REPLAY_DETECTED', 'TOKEN_REVOCATION_COMPLETED')),
            CONSTRAINT `chk_oauth_receipt_identity_tuple`
                CHECK ((`principal_subject_digest` IS NULL
                        AND `enterprise_id` IS NULL AND `member_id` IS NULL AND `user_id` IS NULL)
                    OR (`principal_subject_digest` IS NOT NULL
                        AND `enterprise_id` IS NOT NULL AND `member_id` IS NOT NULL AND `user_id` IS NOT NULL)),
            CONSTRAINT `chk_oauth_receipt_actor`
                CHECK ((`actor_type` = 'USER' AND `actor_user_id` IS NOT NULL)
                    OR (`actor_type` IN ('SYSTEM', 'CLIENT') AND `actor_user_id` IS NULL)),
            CONSTRAINT `chk_oauth_receipt_evidence`
                CHECK (`evidence_level` = 'ACTION_COMPLETED'),
            CONSTRAINT `chk_oauth_receipt_required_objects`
                CHECK ((`action` IN ('OAUTH_CLIENT_REGISTERED', 'OAUTH_CLIENT_REVOKED')
                        AND `authorization_request_id` IS NULL AND `authorization_code_id` IS NULL
                        AND `family_id` IS NULL AND `token_id` IS NULL AND `binding_id` IS NULL
                        AND `enterprise_id` IS NULL)
                    OR (`action` IN ('AUTHORIZATION_APPROVED', 'AUTHORIZATION_DENIED')
                        AND `authorization_request_id` IS NOT NULL
                        AND `authorization_code_id` IS NULL AND `family_id` IS NULL
                        AND `token_id` IS NULL AND `binding_id` IS NULL
                        AND `enterprise_id` IS NOT NULL)
                    OR (`action` = 'AUTHORIZATION_CODE_ISSUED'
                        AND `authorization_request_id` IS NOT NULL
                        AND `authorization_code_id` IS NOT NULL AND `family_id` IS NULL
                        AND `token_id` IS NULL AND `binding_id` IS NULL
                        AND `enterprise_id` IS NOT NULL)
                    OR (`action` = 'AUTHORIZATION_CODE_REPLAY_DETECTED'
                        AND `authorization_request_id` IS NULL
                        AND `authorization_code_id` IS NOT NULL AND `family_id` IS NOT NULL
                        AND `token_id` IS NULL AND `enterprise_id` IS NOT NULL)
                    OR (`action` = 'TOKEN_FAMILY_CREATED'
                        AND `authorization_request_id` IS NULL
                        AND `authorization_code_id` IS NOT NULL AND `family_id` IS NOT NULL
                        AND `token_id` IS NULL AND `binding_id` IS NULL
                        AND `enterprise_id` IS NOT NULL)
                    OR (`action` IN ('TOKEN_FAMILY_ACTIVATED', 'TOKEN_FAMILY_REAUTHORIZED')
                        AND `authorization_request_id` IS NULL AND `authorization_code_id` IS NULL
                        AND `token_id` IS NULL
                        AND `family_id` IS NOT NULL AND `binding_id` IS NOT NULL
                        AND `enterprise_id` IS NOT NULL)
                    OR (`action` = 'TOKEN_FAMILY_ROTATED'
                        AND `authorization_request_id` IS NULL AND `authorization_code_id` IS NULL
                        AND `family_id` IS NOT NULL AND `token_id` IS NOT NULL
                        AND `binding_id` IS NOT NULL AND `enterprise_id` IS NOT NULL)
                    OR (`action` IN ('TOKEN_FAMILY_REVOKED', 'TOKEN_FAMILY_COMPROMISED')
                        AND `authorization_request_id` IS NULL AND `authorization_code_id` IS NULL
                        AND `token_id` IS NULL
                        AND `family_id` IS NOT NULL AND `enterprise_id` IS NOT NULL)
                    OR (`action` IN ('REFRESH_REPLAY_DETECTED', 'TOKEN_REVOCATION_COMPLETED')
                        AND `authorization_request_id` IS NULL AND `authorization_code_id` IS NULL
                        AND `family_id` IS NOT NULL AND `token_id` IS NOT NULL
                        AND `enterprise_id` IS NOT NULL))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Immutable W4b OAuth action-completed receipts';
    END IF;

    -- Exact table, engine and collation audit.
    SET migration_stage = 'table-audit';
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
    IF target_table_count <> 6 OR target_base_table_count <> 6
       OR target_engine_count <> 6 OR target_collation_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation six-table engine or collation contract has drifted';
    END IF;

    SET migration_stage = 'column-order-audit';
    SELECT COUNT(*) INTO target_total_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_column_order_count
    FROM (
        SELECT table_name,
               GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ',') AS column_signature
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_oauth_client', 'fbs_oauth_authorization_request',
              'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
              'fbs_oauth_token', 'fbs_oauth_receipt'
          )
        GROUP BY table_name
    ) ordered_columns
    WHERE (table_name = 'fbs_oauth_client'
           AND CAST(column_signature AS BINARY) = CAST('id,client_id,client_name,issuer_uri,resource_uri,product_code,source_code,connector_code,redirect_port,redirect_uri,token_endpoint_auth_method,grant_types_canonical,response_types_canonical,scope_canonical,scope_digest,metadata_digest,registration_source_digest,status,registered_at,expires_at,terminated_at,version,created_at,updated_at' AS BINARY))
       OR (table_name = 'fbs_oauth_authorization_request'
           AND CAST(column_signature AS BINARY) = CAST('id,request_handle_digest,client_id,redirect_uri,code_challenge,code_challenge_method,state_digest,state_key_ref,state_nonce,state_ciphertext,issuer_uri,resource_uri,product_code,source_code,connector_code,scope_canonical,scope_digest,principal_subject_digest,enterprise_id,member_id,user_id,status,requested_at,expires_at,approved_at,denied_at,consumed_at,version,created_at,updated_at' AS BINARY))
       OR (table_name = 'fbs_oauth_authorization_code'
           AND CAST(column_signature AS BINARY) = CAST('id,code_digest,authorization_request_id,client_id,redirect_uri,code_challenge,code_challenge_method,issuer_uri,resource_uri,product_code,source_code,connector_code,scope_canonical,scope_digest,principal_subject_digest,enterprise_id,member_id,user_id,status,issued_at,expires_at,used_at,revoked_at,version,created_at,updated_at' AS BINARY))
       OR (table_name = 'fbs_oauth_token_family'
           AND CAST(column_signature AS BINARY) = CAST('id,family_id,origin_authorization_code_id,client_id,enterprise_id,member_id,user_id,product_code,source_code,connector_code,issuer_uri,resource_uri,scope_canonical,scope_digest,principal_subject_digest,binding_id,binding_version,status,lifecycle_slot,current_refresh_generation,issued_at,activated_at,expires_at,terminated_at,version,created_at,updated_at' AS BINARY))
       OR (table_name = 'fbs_oauth_token'
           AND CAST(column_signature AS BINARY) = CAST('id,token_digest,family_id,token_type,generation,resource_uri,scope_canonical,scope_digest,status,active_refresh_slot,issued_at,used_at,revoked_at,expires_at,version,created_at,updated_at' AS BINARY))
       OR (table_name = 'fbs_oauth_receipt'
           AND CAST(column_signature AS BINARY) = CAST('id,receipt_id,action,client_id,authorization_request_id,authorization_code_id,family_id,token_id,binding_id,enterprise_id,member_id,user_id,principal_subject_digest,actor_type,actor_user_id,actor_subject_digest,correlation_id,payload_digest,evidence_level,created_at' AS BINARY));
    IF target_total_column_count <> 144 OR target_column_order_count <> 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation exact ordered 144-column contract has drifted';
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
      INTO target_column_contract_digest
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    IF target_column_contract_digest IS NULL OR CAST(target_column_contract_digest AS BINARY) <>
       CAST('89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation exact column metadata contract has drifted';
    END IF;

    SET migration_stage = 'digest-and-generated-column-audit';
    SELECT COUNT(*) INTO target_digest_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND column_type = 'binary(32)'
      AND character_set_name IS NULL
      AND collation_name IS NULL
      AND ((table_name = 'fbs_oauth_client'
            AND column_name IN ('scope_digest','metadata_digest','registration_source_digest')
            AND is_nullable = 'NO')
        OR (table_name = 'fbs_oauth_authorization_request'
            AND ((column_name IN ('request_handle_digest','state_digest','scope_digest') AND is_nullable = 'NO')
              OR (column_name = 'principal_subject_digest' AND is_nullable = 'YES')))
        OR (table_name = 'fbs_oauth_authorization_code'
            AND column_name IN ('code_digest','scope_digest','principal_subject_digest')
            AND is_nullable = 'NO')
        OR (table_name = 'fbs_oauth_token_family'
            AND column_name IN ('scope_digest','principal_subject_digest')
            AND is_nullable = 'NO')
        OR (table_name = 'fbs_oauth_token'
            AND column_name IN ('token_digest','scope_digest')
            AND is_nullable = 'NO')
        OR (table_name = 'fbs_oauth_receipt'
            AND ((column_name = 'principal_subject_digest' AND is_nullable = 'YES')
              OR (column_name IN ('actor_subject_digest','payload_digest') AND is_nullable = 'NO'))));
    SELECT COUNT(*) INTO target_generated_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_oauth_token_family'
            AND column_name = 'lifecycle_slot'
            AND column_type = 'varchar(16)'
            AND is_nullable = 'YES'
            AND character_set_name = 'ascii'
            AND collation_name = 'ascii_bin'
            AND extra = 'STORED GENERATED'
            AND generation_expression <> '')
        OR (table_name = 'fbs_oauth_token'
            AND column_name = 'active_refresh_slot'
            AND column_type = 'tinyint unsigned'
            AND is_nullable = 'YES'
            AND extra = 'STORED GENERATED'
            AND generation_expression <> ''));
    IF target_digest_column_count <> 17 OR target_generated_column_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation digest or generated-slot column contract has drifted';
    END IF;

    SET migration_stage = 'index-contract-audit';
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
    SELECT COUNT(*) INTO target_index_contract_count
    FROM (
        SELECT table_name, index_name, non_unique, index_type,
               MIN(is_visible) AS visibility,
               SUM(CASE WHEN sub_part IS NULL THEN 0 ELSE 1 END) AS partial_columns,
               GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS column_signature
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_oauth_client', 'fbs_oauth_authorization_request',
              'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
              'fbs_oauth_token', 'fbs_oauth_receipt'
          )
        GROUP BY table_name, index_name, non_unique, index_type
    ) indexes_by_name
    WHERE visibility = 'YES' AND partial_columns = 0 AND (
        (table_name = 'fbs_oauth_client' AND index_name = 'PRIMARY' AND non_unique = 0 AND column_signature = 'id')
        OR (table_name = 'fbs_oauth_client' AND index_name = 'uk_oauth_client_id' AND non_unique = 0 AND column_signature = 'client_id')
        OR (table_name = 'fbs_oauth_client' AND index_name = 'uk_oauth_client_redirect' AND non_unique = 0 AND column_signature = 'client_id,redirect_uri')
        OR (table_name = 'fbs_oauth_client' AND index_name = 'idx_oauth_client_status_expiry' AND non_unique = 1 AND column_signature = 'status,expires_at')
        OR (table_name = 'fbs_oauth_client' AND index_name = 'idx_oauth_client_source_time' AND non_unique = 1 AND column_signature = 'registration_source_digest,registered_at')
        OR (table_name = 'fbs_oauth_authorization_request' AND index_name = 'PRIMARY' AND non_unique = 0 AND column_signature = 'id')
        OR (table_name = 'fbs_oauth_authorization_request' AND index_name = 'uk_oauth_request_handle' AND non_unique = 0 AND column_signature = 'request_handle_digest')
        OR (table_name = 'fbs_oauth_authorization_request' AND index_name = 'uk_oauth_request_state' AND non_unique = 0 AND column_signature = 'state_digest')
        OR (table_name = 'fbs_oauth_authorization_request' AND index_name = 'uk_oauth_request_code_context' AND non_unique = 0 AND column_signature = 'id,client_id,redirect_uri,code_challenge,principal_subject_digest,enterprise_id,member_id,user_id')
        OR (table_name = 'fbs_oauth_authorization_request' AND index_name = 'uk_oauth_request_receipt_scope' AND non_unique = 0 AND column_signature = 'id,client_id,principal_subject_digest,enterprise_id,member_id,user_id')
        OR (table_name = 'fbs_oauth_authorization_request' AND index_name = 'idx_oauth_request_client_state' AND non_unique = 1 AND column_signature = 'client_id,status,expires_at')
        OR (table_name = 'fbs_oauth_authorization_request' AND index_name = 'idx_oauth_request_identity_state' AND non_unique = 1 AND column_signature = 'enterprise_id,member_id,user_id,status')
        OR (table_name = 'fbs_oauth_authorization_request' AND index_name = 'idx_oauth_request_client_redirect' AND non_unique = 1 AND column_signature = 'client_id,redirect_uri')
        OR (table_name = 'fbs_oauth_authorization_request' AND index_name = 'idx_oauth_request_entitlement' AND non_unique = 1 AND column_signature = 'enterprise_id,member_id,product_code')
        OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'PRIMARY' AND non_unique = 0 AND column_signature = 'id')
        OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'uk_oauth_code_digest' AND non_unique = 0 AND column_signature = 'code_digest')
        OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'uk_oauth_code_request' AND non_unique = 0 AND column_signature = 'authorization_request_id')
        OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'uk_oauth_code_family_context' AND non_unique = 0 AND column_signature = 'id,client_id,principal_subject_digest,enterprise_id,member_id,user_id,resource_uri,scope_digest')
        OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'uk_oauth_code_receipt_scope' AND non_unique = 0 AND column_signature = 'id,client_id,principal_subject_digest,enterprise_id,member_id,user_id')
        OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'idx_oauth_code_client_state' AND non_unique = 1 AND column_signature = 'client_id,status,expires_at')
        OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'idx_oauth_code_identity_state' AND non_unique = 1 AND column_signature = 'enterprise_id,member_id,user_id,status')
        OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'idx_oauth_code_request_context' AND non_unique = 1 AND column_signature = 'authorization_request_id,client_id,redirect_uri,code_challenge,principal_subject_digest,enterprise_id,member_id,user_id')
        OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'idx_oauth_code_entitlement' AND non_unique = 1 AND column_signature = 'enterprise_id,member_id,product_code')
        OR (table_name = 'fbs_oauth_token_family' AND index_name = 'PRIMARY' AND non_unique = 0 AND column_signature = 'id')
        OR (table_name = 'fbs_oauth_token_family' AND index_name = 'uk_oauth_family_id' AND non_unique = 0 AND column_signature = 'family_id')
        OR (table_name = 'fbs_oauth_token_family' AND index_name = 'uk_oauth_family_origin_code' AND non_unique = 0 AND column_signature = 'origin_authorization_code_id')
        OR (table_name = 'fbs_oauth_token_family' AND index_name = 'uk_oauth_family_live_slot' AND non_unique = 0 AND column_signature = 'enterprise_id,member_id,product_code,source_code,connector_code,lifecycle_slot')
        OR (table_name = 'fbs_oauth_token_family' AND index_name = 'uk_oauth_family_receipt_scope' AND non_unique = 0 AND column_signature = 'family_id,client_id,principal_subject_digest,enterprise_id,member_id,user_id')
        OR (table_name = 'fbs_oauth_token_family' AND index_name = 'idx_oauth_family_binding_state' AND non_unique = 1 AND column_signature = 'binding_id,status')
        OR (table_name = 'fbs_oauth_token_family' AND index_name = 'idx_oauth_family_client_state' AND non_unique = 1 AND column_signature = 'client_id,status,expires_at')
        OR (table_name = 'fbs_oauth_token_family' AND index_name = 'idx_oauth_family_identity_state' AND non_unique = 1 AND column_signature = 'enterprise_id,member_id,user_id,status')
        OR (table_name = 'fbs_oauth_token_family' AND index_name = 'idx_oauth_family_origin_context' AND non_unique = 1 AND column_signature = 'origin_authorization_code_id,client_id,principal_subject_digest,enterprise_id,member_id,user_id,resource_uri,scope_digest')
        OR (table_name = 'fbs_oauth_token_family' AND index_name = 'idx_oauth_family_binding_scope' AND non_unique = 1 AND column_signature = 'binding_id,enterprise_id,member_id,user_id')
        OR (table_name = 'fbs_oauth_token' AND index_name = 'PRIMARY' AND non_unique = 0 AND column_signature = 'id')
        OR (table_name = 'fbs_oauth_token' AND index_name = 'uk_oauth_token_digest' AND non_unique = 0 AND column_signature = 'token_digest')
        OR (table_name = 'fbs_oauth_token' AND index_name = 'uk_oauth_token_family_type_generation' AND non_unique = 0 AND column_signature = 'family_id,token_type,generation')
        OR (table_name = 'fbs_oauth_token' AND index_name = 'uk_oauth_token_active_refresh' AND non_unique = 0 AND column_signature = 'family_id,active_refresh_slot')
        OR (table_name = 'fbs_oauth_token' AND index_name = 'uk_oauth_token_receipt_scope' AND non_unique = 0 AND column_signature = 'id,family_id')
        OR (table_name = 'fbs_oauth_token' AND index_name = 'idx_oauth_token_family_state' AND non_unique = 1 AND column_signature = 'family_id,status,expires_at')
        OR (table_name = 'fbs_oauth_token' AND index_name = 'idx_oauth_token_state_expiry' AND non_unique = 1 AND column_signature = 'status,expires_at')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'PRIMARY' AND non_unique = 0 AND column_signature = 'id')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'uk_oauth_receipt_id' AND non_unique = 0 AND column_signature = 'receipt_id')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'idx_oauth_receipt_client' AND non_unique = 1 AND column_signature = 'client_id,created_at')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'idx_oauth_receipt_request' AND non_unique = 1 AND column_signature = 'authorization_request_id,created_at')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'idx_oauth_receipt_family' AND non_unique = 1 AND column_signature = 'family_id,created_at')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'idx_oauth_receipt_identity' AND non_unique = 1 AND column_signature = 'enterprise_id,member_id,user_id,principal_subject_digest,created_at')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'idx_oauth_receipt_action' AND non_unique = 1 AND column_signature = 'action,created_at')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'idx_oauth_receipt_request_scope' AND non_unique = 1 AND column_signature = 'authorization_request_id,client_id,principal_subject_digest,enterprise_id,member_id,user_id')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'idx_oauth_receipt_code_scope' AND non_unique = 1 AND column_signature = 'authorization_code_id,client_id,principal_subject_digest,enterprise_id,member_id,user_id')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'idx_oauth_receipt_family_scope' AND non_unique = 1 AND column_signature = 'family_id,client_id,principal_subject_digest,enterprise_id,member_id,user_id')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'idx_oauth_receipt_token_family' AND non_unique = 1 AND column_signature = 'token_id,family_id')
        OR (table_name = 'fbs_oauth_receipt' AND index_name = 'idx_oauth_receipt_binding_scope' AND non_unique = 1 AND column_signature = 'binding_id,enterprise_id,member_id,user_id')
    );
    IF target_index_count <> 52 OR target_index_contract_count <> 52 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation exact visible full-index contract has drifted';
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
      INTO target_index_contract_digest
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_client', 'fbs_oauth_authorization_request',
          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
          'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    IF target_index_contract_digest IS NULL OR CAST(target_index_contract_digest AS BINARY) <>
       CAST('1a3122b7238b444939a7981ccd759322bd168f351131e4d9c0a16346242c3d79' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation exact index metadata contract has drifted';
    END IF;

    SET migration_stage = 'foreign-key-contract-audit';
    SELECT COUNT(*) INTO target_foreign_key_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_foreign_key_contract_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND unique_constraint_schema = DATABASE()
      AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'
      AND ((table_name = 'fbs_oauth_authorization_request' AND constraint_name = 'fk_oauth_request_client_redirect' AND referenced_table_name = 'fbs_oauth_client')
        OR (table_name = 'fbs_oauth_authorization_request' AND constraint_name = 'fk_oauth_request_entitlement' AND referenced_table_name = 'fbs_product_entitlement')
        OR (table_name = 'fbs_oauth_authorization_code' AND constraint_name = 'fk_oauth_code_request_context' AND referenced_table_name = 'fbs_oauth_authorization_request')
        OR (table_name = 'fbs_oauth_authorization_code' AND constraint_name = 'fk_oauth_code_entitlement' AND referenced_table_name = 'fbs_product_entitlement')
        OR (table_name = 'fbs_oauth_token_family' AND constraint_name = 'fk_oauth_family_origin_code_context' AND referenced_table_name = 'fbs_oauth_authorization_code')
        OR (table_name = 'fbs_oauth_token_family' AND constraint_name = 'fk_oauth_family_entitlement' AND referenced_table_name = 'fbs_product_entitlement')
        OR (table_name = 'fbs_oauth_token_family' AND constraint_name = 'fk_oauth_family_binding_scope' AND referenced_table_name = 'fbs_connector_binding')
        OR (table_name = 'fbs_oauth_token' AND constraint_name = 'fk_oauth_token_family' AND referenced_table_name = 'fbs_oauth_token_family')
        OR (table_name = 'fbs_oauth_receipt' AND constraint_name = 'fk_oauth_receipt_client' AND referenced_table_name = 'fbs_oauth_client')
        OR (table_name = 'fbs_oauth_receipt' AND constraint_name = 'fk_oauth_receipt_request_scope' AND referenced_table_name = 'fbs_oauth_authorization_request')
        OR (table_name = 'fbs_oauth_receipt' AND constraint_name = 'fk_oauth_receipt_code_scope' AND referenced_table_name = 'fbs_oauth_authorization_code')
        OR (table_name = 'fbs_oauth_receipt' AND constraint_name = 'fk_oauth_receipt_family_scope' AND referenced_table_name = 'fbs_oauth_token_family')
        OR (table_name = 'fbs_oauth_receipt' AND constraint_name = 'fk_oauth_receipt_token_family' AND referenced_table_name = 'fbs_oauth_token')
        OR (table_name = 'fbs_oauth_receipt' AND constraint_name = 'fk_oauth_receipt_binding_scope' AND referenced_table_name = 'fbs_connector_binding'));
    SELECT COUNT(*) INTO target_foreign_key_column_count
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND referenced_table_schema = DATABASE()
      AND referenced_table_name IS NOT NULL
      AND table_name IN (
          'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
          'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
      );
    SELECT COUNT(*) INTO target_foreign_key_column_contract_count
    FROM (
        SELECT table_name, constraint_name, referenced_table_name,
               GROUP_CONCAT(CONCAT(column_name, '>', referenced_column_name)
                            ORDER BY ordinal_position SEPARATOR ',') AS column_signature
        FROM information_schema.key_column_usage
        WHERE constraint_schema = DATABASE()
          AND referenced_table_schema = DATABASE()
          AND referenced_table_name IS NOT NULL
          AND table_name IN (
              'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code',
              'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt'
          )
        GROUP BY table_name, constraint_name, referenced_table_name
    ) foreign_keys
    WHERE (constraint_name = 'fk_oauth_request_client_redirect' AND column_signature = 'client_id>client_id,redirect_uri>redirect_uri')
       OR (constraint_name = 'fk_oauth_request_entitlement' AND column_signature = 'enterprise_id>enterprise_id,member_id>member_id,product_code>product_code')
       OR (constraint_name = 'fk_oauth_code_request_context' AND column_signature = 'authorization_request_id>id,client_id>client_id,redirect_uri>redirect_uri,code_challenge>code_challenge,principal_subject_digest>principal_subject_digest,enterprise_id>enterprise_id,member_id>member_id,user_id>user_id')
       OR (constraint_name = 'fk_oauth_code_entitlement' AND column_signature = 'enterprise_id>enterprise_id,member_id>member_id,product_code>product_code')
       OR (constraint_name = 'fk_oauth_family_origin_code_context' AND column_signature = 'origin_authorization_code_id>id,client_id>client_id,principal_subject_digest>principal_subject_digest,enterprise_id>enterprise_id,member_id>member_id,user_id>user_id,resource_uri>resource_uri,scope_digest>scope_digest')
       OR (constraint_name = 'fk_oauth_family_entitlement' AND column_signature = 'enterprise_id>enterprise_id,member_id>member_id,product_code>product_code')
       OR (constraint_name = 'fk_oauth_family_binding_scope' AND column_signature = 'binding_id>binding_id,enterprise_id>enterprise_id,member_id>member_id,user_id>user_id')
       OR (constraint_name = 'fk_oauth_token_family' AND column_signature = 'family_id>family_id')
       OR (constraint_name = 'fk_oauth_receipt_client' AND column_signature = 'client_id>client_id')
       OR (constraint_name = 'fk_oauth_receipt_request_scope' AND column_signature = 'authorization_request_id>id,client_id>client_id,principal_subject_digest>principal_subject_digest,enterprise_id>enterprise_id,member_id>member_id,user_id>user_id')
       OR (constraint_name = 'fk_oauth_receipt_code_scope' AND column_signature = 'authorization_code_id>id,client_id>client_id,principal_subject_digest>principal_subject_digest,enterprise_id>enterprise_id,member_id>member_id,user_id>user_id')
       OR (constraint_name = 'fk_oauth_receipt_family_scope' AND column_signature = 'family_id>family_id,client_id>client_id,principal_subject_digest>principal_subject_digest,enterprise_id>enterprise_id,member_id>member_id,user_id>user_id')
       OR (constraint_name = 'fk_oauth_receipt_token_family' AND column_signature = 'token_id>id,family_id>family_id')
       OR (constraint_name = 'fk_oauth_receipt_binding_scope' AND column_signature = 'binding_id>binding_id,enterprise_id>enterprise_id,member_id>member_id,user_id>user_id');
    IF target_foreign_key_count <> 14 OR target_foreign_key_contract_count <> 14
       OR target_foreign_key_column_count <> 57
       OR target_foreign_key_column_contract_count <> 14 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation exact same-schema foreign-key contract has drifted';
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
    IF target_foreign_key_contract_digest IS NULL
       OR (CAST(server_version AS BINARY) = CAST('8.0.30' AS BINARY)
           AND CAST(target_foreign_key_contract_digest AS BINARY) <>
               CAST('016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539' AS BINARY))
       OR (CAST(server_version AS BINARY) = CAST('8.4.8' AS BINARY)
           AND CAST(target_foreign_key_contract_digest AS BINARY) <>
               CAST('3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f' AS BINARY)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation exact foreign-key metadata contract has drifted';
    END IF;

    SET migration_stage = 'check-contract-audit';
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
    SELECT COUNT(*) INTO target_check_name_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_type = 'CHECK' AND enforced = 'YES'
      AND ((table_name = 'fbs_oauth_client' AND constraint_name IN
            ('chk_oauth_client_id','chk_oauth_client_fixed_profile','chk_oauth_client_redirect',
             'chk_oauth_client_scope','chk_oauth_client_status','chk_oauth_client_lifetime','chk_oauth_client_lifecycle'))
        OR (table_name = 'fbs_oauth_authorization_request' AND constraint_name IN
            ('chk_oauth_request_fixed_profile','chk_oauth_request_pkce','chk_oauth_request_scope',
             'chk_oauth_request_crypto_tuple','chk_oauth_request_lifetime','chk_oauth_request_lifecycle'))
        OR (table_name = 'fbs_oauth_authorization_code' AND constraint_name IN
            ('chk_oauth_code_fixed_profile','chk_oauth_code_scope',
             'chk_oauth_code_lifetime','chk_oauth_code_lifecycle'))
        OR (table_name = 'fbs_oauth_token_family' AND constraint_name IN
            ('chk_oauth_family_id','chk_oauth_family_fixed_profile','chk_oauth_family_scope',
             'chk_oauth_family_lifetime','chk_oauth_family_binding_pair','chk_oauth_family_lifecycle'))
        OR (table_name = 'fbs_oauth_token' AND constraint_name IN
            ('chk_oauth_token_type','chk_oauth_token_scope','chk_oauth_access_token_lifetime',
             'chk_oauth_token_temporal','chk_oauth_token_lifecycle'))
        OR (table_name = 'fbs_oauth_receipt' AND constraint_name IN
            ('chk_oauth_receipt_action','chk_oauth_receipt_identity_tuple','chk_oauth_receipt_actor',
             'chk_oauth_receipt_evidence','chk_oauth_receipt_required_objects')));
    IF target_check_count <> 33 OR target_enforced_check_count <> 33
       OR target_check_name_count <> 33 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation exact enforced CHECK contract has drifted';
    END IF;

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
    IF target_check_contract_digest IS NULL OR CAST(target_check_contract_digest AS BINARY) <>
       CAST('51f71711dfc17b5a6741fbbd1bd3d31ae7d4761e9da5722c614eb199a81afb11' AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation exact CHECK clause contract has drifted';
    END IF;

    IF migration_exists <> 0 THEN
        SET migration_stage = 'completed-trigger-audit';
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
          AND action_timing = 'BEFORE'
          AND action_orientation = 'ROW'
          AND CAST(normalized_action AS BINARY) = CAST(
              'signalsqlstate''45000''setmessage_text=''oauthreceiptsareimmutable'''
              AS BINARY)
          AND ((trigger_name = 'trg_oauth_receipt_no_update' AND event_manipulation = 'UPDATE')
            OR (trigger_name = 'trg_oauth_receipt_no_delete' AND event_manipulation = 'DELETE'));
        IF target_trigger_count <> 2 OR target_trigger_contract_count <> 2 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'OAuth receipt immutability trigger contract has drifted';
        END IF;
    END IF;

    -- Keep the connection-scoped lock held for top-level trigger creation and
    -- the finalizer. A client failure closes the connection and releases it.
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation migration lost lock ownership before trigger finalization';
    END IF;
    IF group_concat_limit_changed = 1 THEN
        SET SESSION group_concat_max_len = original_group_concat_max_len;
        SET group_concat_limit_changed = 0;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_oauth_foundation_20260721`$$
CREATE PROCEDURE `u3w_finalize_independent_board_oauth_foundation_20260721`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE migration_lock_released INT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE target_trigger_count INT DEFAULT 0;
    DECLARE target_trigger_contract_count INT DEFAULT 0;
    DECLARE target_internal_receipt_count INT DEFAULT 0;
    DECLARE target_version_receipt_count INT DEFAULT 0;

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
        CONCAT(DATABASE(), ':20260721_independent_board_oauth_foundation_v1'),
        256
    );
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation finalizer does not own the migration lock';
    END IF;

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
      AND action_timing = 'BEFORE'
      AND action_orientation = 'ROW'
      AND CAST(normalized_action AS BINARY) = CAST(
          'signalsqlstate''45000''setmessage_text=''oauthreceiptsareimmutable'''
          AS BINARY)
      AND ((trigger_name = 'trg_oauth_receipt_no_update' AND event_manipulation = 'UPDATE')
        OR (trigger_name = 'trg_oauth_receipt_no_delete' AND event_manipulation = 'DELETE'));
    IF target_trigger_count <> 2 OR target_trigger_contract_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth receipt immutability trigger contract has drifted';
    END IF;

    START TRANSACTION;
    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_foundation_v1';
    IF migration_exists = 0 THEN
        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260721_independent_board_oauth_foundation_v1',
            'Independent Board OAuth client, authorization, token family and immutable receipt tables'
        );
    END IF;
    SELECT COUNT(*) INTO target_internal_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_foundation_v1'
      AND `description` = 'Independent Board OAuth client, authorization, token family and immutable receipt tables';
    SELECT COUNT(*) INTO target_version_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_oauth_foundation_v1';
    IF target_internal_receipt_count <> 1 OR target_version_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation internal migration receipt is missing or drifted';
    END IF;
    COMMIT;

    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_released;
    IF migration_lock_released IS NULL OR migration_lock_released <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'OAuth foundation migration advisory lock release failed';
    END IF;
END$$

CALL `u3w_migrate_independent_board_oauth_foundation_20260721`()$$
CREATE TRIGGER IF NOT EXISTS `trg_oauth_receipt_no_update`
    BEFORE UPDATE ON `fbs_oauth_receipt`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'OAuth receipts are immutable'$$
CREATE TRIGGER IF NOT EXISTS `trg_oauth_receipt_no_delete`
    BEFORE DELETE ON `fbs_oauth_receipt`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'OAuth receipts are immutable'$$
CALL `u3w_finalize_independent_board_oauth_foundation_20260721`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_oauth_foundation_20260721`$$
DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_oauth_foundation_20260721`$$

DELIMITER ;
