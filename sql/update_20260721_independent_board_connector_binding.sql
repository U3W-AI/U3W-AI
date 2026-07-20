-- ============================================================
-- FBSir Independent Board authoritative Connector binding
-- Migration: 20260721_independent_board_connector_binding_v1
-- Public manifest step: public_init_032
-- Target: MySQL >= 8.0.29 (CREATE TRIGGER IF NOT EXISTS is required)
-- Scope: binding state, exact scopes and immutable receipts
-- Precondition: public_init_028 and u3w_schema_migration exist.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_connector_binding_20260721`$$
CREATE PROCEDURE `u3w_migrate_independent_board_connector_binding_20260721`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE current_connection BIGINT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE target_table_count INT DEFAULT 0;
    DECLARE target_base_table_count INT DEFAULT 0;
    DECLARE target_engine_count INT DEFAULT 0;
    DECLARE target_collation_count INT DEFAULT 0;
    DECLARE target_column_count INT DEFAULT 0;
    DECLARE target_total_column_count INT DEFAULT 0;
    DECLARE target_column_order_count INT DEFAULT 0;
    DECLARE target_column_contract_count INT DEFAULT 0;
    DECLARE target_default_contract_count INT DEFAULT 0;
    DECLARE target_index_count INT DEFAULT 0;
    DECLARE target_index_contract_count INT DEFAULT 0;
    DECLARE target_foreign_key_count INT DEFAULT 0;
    DECLARE target_foreign_key_column_count INT DEFAULT 0;
    DECLARE target_foreign_key_total_column_count INT DEFAULT 0;
    DECLARE target_foreign_key_contract_count INT DEFAULT 0;
    DECLARE target_check_count INT DEFAULT 0;
    DECLARE target_enforced_check_count INT DEFAULT 0;
    DECLARE target_check_contract_count INT DEFAULT 0;
    DECLARE check_contract_error VARCHAR(255) DEFAULT NULL;
    DECLARE target_digest_column_count INT DEFAULT 0;
    DECLARE target_trigger_count INT DEFAULT 0;
    DECLARE target_trigger_contract_count INT DEFAULT 0;
    DECLARE target_internal_receipt_count INT DEFAULT 0;
    DECLARE target_version_receipt_count INT DEFAULT 0;
    DECLARE server_major INT DEFAULT 0;
    DECLARE server_minor INT DEFAULT 0;
    DECLARE server_patch INT DEFAULT 0;
    DECLARE migration_stage VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT 'preflight';
    DECLARE diagnostic_errno INT DEFAULT 0;

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
        IF diagnostic_errno = 1267 THEN
            RESIGNAL SET MESSAGE_TEXT = migration_stage;
        END IF;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board Connector binding migration requires an explicit target database';
    END IF;

    SET server_major = CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED);
    SET server_minor = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(VERSION(), '.', 2), '.', -1) AS UNSIGNED);
    SET server_patch = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(VERSION(), '.', 3), '.', -1) AS UNSIGNED);
    IF INSTR(LOWER(VERSION()), 'mariadb') > 0
       OR server_major < 8
       OR (server_major = 8 AND server_minor = 0 AND server_patch < 29) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board Connector binding migration requires MySQL 8.0.29 or newer';
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260721_independent_board_connector_binding_v1'),
        256
    );
    IF CHAR_LENGTH(migration_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board Connector binding lock name must be a stable 64-character digest';
    END IF;
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the Independent Board Connector binding migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name), CONNECTION_ID()
      INTO migration_lock_owner, current_connection;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board Connector binding migration lock is not owned by the current connection';
    END IF;

    SET migration_stage = 'partial-state-audit';
    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_connector_binding_v1';

    SELECT COUNT(*) INTO target_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      );
    IF migration_exists = 0 AND target_table_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding tables exist without the exact migration receipt; audit before continuing';
    END IF;
    IF migration_exists <> 0 AND target_table_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding migration receipt exists but its three-table set is incomplete';
    END IF;

    IF migration_exists = 0 THEN
        SET migration_stage = 'first-apply-ddl';
        CREATE TABLE IF NOT EXISTS `fbs_connector_binding` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
            `binding_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'globally unique binding identifier',
            `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT 'tenant scope',
            `member_id` BIGINT UNSIGNED NOT NULL COMMENT 'enterprise member scope',
            `user_id` BIGINT UNSIGNED NOT NULL COMMENT 'bound user identity',
            `product_code` VARCHAR(64) NOT NULL COMMENT 'canonical product code',
            `source_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'trusted host source',
            `connector_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Marketplace Connector code',
            `issuer_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'deployment-configured OAuth issuer',
            `resource_uri` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'protected MCP resource URI',
            `client_id` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'verified OAuth client identifier',
            `principal_subject_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 subject digest',
            `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ACTIVE, REVOKED, or COMPROMISED',
            `verification_method` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'validated first protected MCP request type',
            `evidence_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 evidence digest',
            `verified_at` DATETIME(3) NOT NULL COMMENT 'first protected access verification time',
            `last_seen_at` DATETIME(3) NOT NULL COMMENT 'latest accepted protected access time',
            `valid_until` DATETIME(3) NOT NULL COMMENT 'explicit binding validity end',
            `revoked_at` DATETIME(3) DEFAULT NULL COMMENT 'terminal transition time',
            `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'optimistic concurrency version',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_connector_binding_id` (`binding_id`),
            UNIQUE KEY `uk_connector_binding_receipt_scope`
                (`binding_id`, `enterprise_id`, `member_id`, `user_id`),
            UNIQUE KEY `uk_connector_binding_scope`
                (`enterprise_id`, `member_id`, `product_code`, `source_code`, `connector_code`),
            KEY `idx_connector_binding_user` (`enterprise_id`, `user_id`, `product_code`, `status`),
            KEY `idx_connector_binding_state` (`status`, `valid_until`),
            CONSTRAINT `fk_connector_binding_entitlement`
                FOREIGN KEY (`enterprise_id`, `member_id`, `product_code`)
                REFERENCES `fbs_product_entitlement` (`enterprise_id`, `member_id`, `product_code`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_connector_binding_identifiers`
                CHECK (CHAR_LENGTH(`binding_id`) > 0
                    AND CHAR_LENGTH(`issuer_uri`) > 0
                    AND CHAR_LENGTH(`resource_uri`) > 0
                    AND CHAR_LENGTH(`client_id`) > 0
                    AND CAST(`product_code` AS BINARY)
                        = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)),
            CONSTRAINT `chk_connector_binding_source`
                CHECK (`source_code` = 'WORKBUDDY'),
            CONSTRAINT `chk_connector_binding_connector`
                CHECK (`connector_code` = 'fbs-connector'),
            CONSTRAINT `chk_connector_binding_resource`
                CHECK (`resource_uri` = 'https://api2.u3w.com/fbs-mcp/mcp'),
            CONSTRAINT `chk_connector_binding_status`
                CHECK (`status` IN ('ACTIVE', 'REVOKED', 'COMPROMISED')),
            CONSTRAINT `chk_connector_binding_verification`
                CHECK (`verification_method` IN ('MCP_INITIALIZE', 'MCP_TOOLS_LIST')),
            CONSTRAINT `chk_connector_binding_principal_digest`
                CHECK (`principal_subject_digest` REGEXP '^[0-9a-f]{64}$'),
            CONSTRAINT `chk_connector_binding_evidence_digest`
                CHECK (`evidence_digest` REGEXP '^[0-9a-f]{64}$'),
            CONSTRAINT `chk_connector_binding_lifecycle`
                CHECK ((`status` = 'ACTIVE' AND `revoked_at` IS NULL)
                    OR (`status` IN ('REVOKED', 'COMPROMISED') AND `revoked_at` IS NOT NULL)),
            CONSTRAINT `chk_connector_binding_temporal`
                CHECK (`last_seen_at` >= `verified_at`
                    AND `valid_until` > `last_seen_at`
                    AND (`revoked_at` IS NULL OR `revoked_at` >= `last_seen_at`))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Authoritative WorkBuddy Connector binding state';

        CREATE TABLE IF NOT EXISTS `fbs_connector_binding_scope` (
            `binding_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'authoritative binding identifier',
            `scope_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'exact granted scope',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`binding_id`, `scope_code`),
            CONSTRAINT `fk_connector_binding_scope_binding`
                FOREIGN KEY (`binding_id`)
                REFERENCES `fbs_connector_binding` (`binding_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_connector_binding_scope_code`
                CHECK (`scope_code` IN (
                    'identity.read',
                    'entitlement.read',
                    'board.meeting.reserve',
                    'board.receipt.write'
                ))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Exact authoritative Connector binding scopes';

        CREATE TABLE IF NOT EXISTS `fbs_connector_binding_receipt` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'surrogate primary key',
            `receipt_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'globally unique immutable receipt identifier',
            `binding_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'authoritative binding identifier',
            `enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT 'tenant scope snapshot',
            `member_id` BIGINT UNSIGNED NOT NULL COMMENT 'member scope snapshot',
            `user_id` BIGINT UNSIGNED NOT NULL COMMENT 'bound user snapshot',
            `actor_user_id` BIGINT UNSIGNED NOT NULL COMMENT 'actor identity',
            `action` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'verified or revoked binding action',
            `payload_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'canonical payload SHA-256',
            `evidence_level` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'bounded receipt evidence level',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_connector_binding_receipt_id` (`receipt_id`),
            KEY `idx_connector_binding_receipt_binding`
                (`binding_id`, `enterprise_id`, `member_id`, `user_id`, `created_at`),
            KEY `idx_connector_binding_receipt_scope` (`enterprise_id`, `member_id`, `created_at`),
            CONSTRAINT `fk_connector_binding_receipt_binding`
                FOREIGN KEY (`binding_id`, `enterprise_id`, `member_id`, `user_id`)
                REFERENCES `fbs_connector_binding`
                    (`binding_id`, `enterprise_id`, `member_id`, `user_id`)
                ON UPDATE RESTRICT ON DELETE RESTRICT,
            CONSTRAINT `chk_connector_binding_receipt_id`
                CHECK (CHAR_LENGTH(`receipt_id`) > 0),
            CONSTRAINT `chk_connector_binding_receipt_action`
                CHECK (`action` IN ('CONNECTOR_BINDING_VERIFIED', 'CONNECTOR_BINDING_REVOKED')),
            CONSTRAINT `chk_connector_binding_receipt_payload_digest`
                CHECK (`payload_digest` REGEXP '^[0-9a-f]{64}$'),
            CONSTRAINT `chk_connector_binding_receipt_evidence`
                CHECK (`evidence_level` = 'ACTION_COMPLETED')
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='Immutable authoritative Connector binding receipts';

    END IF;

    SET migration_stage = 'table-audit';
    -- Exact table, engine and collation audit. This also rejects views that
    -- shadow an expected target name.
    SELECT COUNT(*) INTO target_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      );
    SELECT COUNT(*) INTO target_base_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      )
      AND table_type = 'BASE TABLE';
    SELECT COUNT(*) INTO target_engine_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      )
      AND engine = 'InnoDB';
    SELECT COUNT(*) INTO target_collation_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      )
      AND table_collation = 'utf8mb4_unicode_ci';
    IF target_table_count <> 3 OR target_base_table_count <> 3
       OR target_engine_count <> 3 OR target_collation_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding three-table engine or collation contract has drifted';
    END IF;

    -- All and only the 36 contract columns must exist.
    SELECT COUNT(*) INTO target_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_connector_binding' AND column_name IN (
              'id','binding_id','enterprise_id','member_id','user_id','product_code',
              'source_code','connector_code','issuer_uri','resource_uri','client_id',
              'principal_subject_digest','status','verification_method','evidence_digest',
              'verified_at','last_seen_at','valid_until','revoked_at','version','created_at','updated_at'
          ))
        OR (table_name = 'fbs_connector_binding_scope' AND column_name IN (
              'binding_id','scope_code','created_at'
          ))
        OR (table_name = 'fbs_connector_binding_receipt' AND column_name IN (
              'id','receipt_id','binding_id','enterprise_id','member_id','user_id',
              'actor_user_id','action','payload_digest','evidence_level','created_at'
          )));
    SELECT COUNT(*) INTO target_total_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      );
    IF target_column_count <> 36 OR target_total_column_count <> 36 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding exact 36-column contract has drifted';
    END IF;

    SET migration_stage = 'column-order-audit';
    SELECT COUNT(*) INTO target_column_order_count
    FROM (
        SELECT table_name,
               GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ',') AS column_signature
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_connector_binding',
              'fbs_connector_binding_scope',
              'fbs_connector_binding_receipt'
          )
        GROUP BY table_name
    ) AS ordered_columns
    WHERE (table_name = 'fbs_connector_binding'
           AND CAST(column_signature AS BINARY) = CAST('id,binding_id,enterprise_id,member_id,user_id,product_code,source_code,connector_code,issuer_uri,resource_uri,client_id,principal_subject_digest,status,verification_method,evidence_digest,verified_at,last_seen_at,valid_until,revoked_at,version,created_at,updated_at' AS BINARY))
       OR (table_name = 'fbs_connector_binding_scope'
           AND CAST(column_signature AS BINARY) = CAST('binding_id,scope_code,created_at' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt'
           AND CAST(column_signature AS BINARY) = CAST('id,receipt_id,binding_id,enterprise_id,member_id,user_id,actor_user_id,action,payload_digest,evidence_level,created_at' AS BINARY));
    IF target_column_order_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding column order contract has drifted';
    END IF;

    -- Exact types, nullability, charset/collation and non-generated shape.
    SET migration_stage = 'column-contract-audit';
    SELECT COUNT(*) INTO target_column_contract_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND (
        (table_name = 'fbs_connector_binding' AND column_name = 'id'
         AND column_type = 'bigint unsigned' AND is_nullable = 'NO'
         AND column_default IS NULL AND extra = 'auto_increment')
        OR (table_name = 'fbs_connector_binding' AND column_name IN ('enterprise_id','member_id','user_id')
            AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name = 'version'
            AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND column_default = '0' AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name = 'binding_id'
            AND column_type = 'varchar(128)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name = 'product_code'
            AND column_type = 'varchar(64)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci' AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name IN ('source_code','connector_code')
            AND column_type = 'varchar(64)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name IN ('issuer_uri','resource_uri')
            AND column_type = 'varchar(512)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name = 'client_id'
            AND column_type = 'varchar(191)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name IN ('principal_subject_digest','evidence_digest')
            AND column_type = 'char(64)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name IN ('status','verification_method')
            AND column_type = 'varchar(32)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name IN ('verified_at','last_seen_at','valid_until')
            AND column_type = 'datetime(3)' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name = 'revoked_at'
            AND column_type = 'datetime(3)' AND is_nullable = 'YES' AND column_default IS NULL AND extra = '')
        OR (table_name = 'fbs_connector_binding' AND column_name = 'created_at'
            AND column_type = 'datetime(3)' AND is_nullable = 'NO'
            AND CAST(UPPER(column_default) AS BINARY) = CAST('CURRENT_TIMESTAMP(3)' AS BINARY)
            AND extra = 'DEFAULT_GENERATED')
        OR (table_name = 'fbs_connector_binding' AND column_name = 'updated_at'
            AND column_type = 'datetime(3)' AND is_nullable = 'NO'
            AND CAST(UPPER(column_default) AS BINARY) = CAST('CURRENT_TIMESTAMP(3)' AS BINARY)
            AND CAST(LOWER(extra) AS BINARY) = CAST('default_generated on update current_timestamp(3)' AS BINARY))
        OR (table_name = 'fbs_connector_binding_scope' AND column_name = 'binding_id'
            AND column_type = 'varchar(128)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding_scope' AND column_name = 'scope_code'
            AND column_type = 'varchar(64)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding_scope' AND column_name = 'created_at'
            AND column_type = 'datetime(3)' AND is_nullable = 'NO'
            AND CAST(UPPER(column_default) AS BINARY) = CAST('CURRENT_TIMESTAMP(3)' AS BINARY)
            AND extra = 'DEFAULT_GENERATED')
        OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'id'
            AND column_type = 'bigint unsigned' AND is_nullable = 'NO'
            AND column_default IS NULL AND extra = 'auto_increment')
        OR (table_name = 'fbs_connector_binding_receipt' AND column_name IN ('receipt_id','binding_id')
            AND column_type = 'varchar(128)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding_receipt' AND column_name IN ('enterprise_id','member_id','user_id','actor_user_id')
            AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
        OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'action'
            AND column_type = 'varchar(64)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'payload_digest'
            AND column_type = 'char(64)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'evidence_level'
            AND column_type = 'varchar(32)' AND is_nullable = 'NO' AND column_default IS NULL
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
        OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'created_at'
            AND column_type = 'datetime(3)' AND is_nullable = 'NO'
            AND CAST(UPPER(column_default) AS BINARY) = CAST('CURRENT_TIMESTAMP(3)' AS BINARY)
            AND extra = 'DEFAULT_GENERATED')
      );
    IF target_column_contract_count <> 36 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding exact column type and nullability contract has drifted';
    END IF;

    SET migration_stage = 'default-contract-audit';
    SELECT COUNT(*) INTO target_default_contract_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_connector_binding' AND column_name = 'id' AND extra = 'auto_increment')
        OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'id' AND extra = 'auto_increment')
        OR (table_name = 'fbs_connector_binding' AND column_name = 'version' AND column_default = '0' AND extra = '')
        OR (table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')
            AND column_name = 'created_at'
            AND CAST(UPPER(column_default) AS BINARY) = CAST('CURRENT_TIMESTAMP(3)' AS BINARY)
            AND extra = 'DEFAULT_GENERATED')
        OR (table_name = 'fbs_connector_binding' AND column_name = 'updated_at'
            AND CAST(UPPER(column_default) AS BINARY) = CAST('CURRENT_TIMESTAMP(3)' AS BINARY)
            AND CAST(LOWER(extra) AS BINARY) = CAST('default_generated on update current_timestamp(3)' AS BINARY)));
    IF target_default_contract_count <> 7 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding identity or timestamp default contract has drifted';
    END IF;

    -- Eleven and only eleven visible, full-column, ascending BTREE indexes.
    SET migration_stage = 'index-contract-audit';
    SELECT COUNT(*) INTO target_index_count
    FROM (
        SELECT table_name, index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_connector_binding',
              'fbs_connector_binding_scope',
              'fbs_connector_binding_receipt'
          )
        GROUP BY table_name, index_name
    ) AS target_indexes;
    SELECT COUNT(*) INTO target_index_contract_count
    FROM (
        SELECT table_name, index_name, non_unique, index_type,
               MIN(is_visible) AS visibility,
               SUM(CASE WHEN sub_part IS NULL THEN 0 ELSE 1 END) AS partial_columns,
               GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                            ORDER BY seq_in_index SEPARATOR ',') AS column_signature
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'fbs_connector_binding',
              'fbs_connector_binding_scope',
              'fbs_connector_binding_receipt'
          )
        GROUP BY table_name, index_name, non_unique, index_type
    ) AS indexes_by_name
    WHERE CAST(visibility AS BINARY) = CAST('YES' AS BINARY) AND partial_columns = 0 AND (
       (table_name = 'fbs_connector_binding' AND index_name = 'PRIMARY'
           AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('id:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND index_name = 'uk_connector_binding_id'
           AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND index_name = 'uk_connector_binding_receipt_scope'
           AND non_unique = 0 AND index_type = 'BTREE'
           AND CAST(column_signature AS BINARY) = CAST('binding_id:A,enterprise_id:A,member_id:A,user_id:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND index_name = 'uk_connector_binding_scope'
           AND non_unique = 0 AND index_type = 'BTREE'
           AND CAST(column_signature AS BINARY) = CAST('enterprise_id:A,member_id:A,product_code:A,source_code:A,connector_code:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND index_name = 'idx_connector_binding_user'
           AND non_unique = 1 AND index_type = 'BTREE'
           AND CAST(column_signature AS BINARY) = CAST('enterprise_id:A,user_id:A,product_code:A,status:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND index_name = 'idx_connector_binding_state'
           AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('status:A,valid_until:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding_scope' AND index_name = 'PRIMARY'
           AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id:A,scope_code:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'PRIMARY'
           AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('id:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'uk_connector_binding_receipt_id'
           AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('receipt_id:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'idx_connector_binding_receipt_binding'
           AND non_unique = 1 AND index_type = 'BTREE'
           AND CAST(column_signature AS BINARY) = CAST('binding_id:A,enterprise_id:A,member_id:A,user_id:A,created_at:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'idx_connector_binding_receipt_scope'
           AND non_unique = 1 AND index_type = 'BTREE'
           AND CAST(column_signature AS BINARY) = CAST('enterprise_id:A,member_id:A,created_at:A' AS BINARY)));
    IF target_index_count <> 11 OR target_index_contract_count <> 11 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding exact eleven-index contract has drifted';
    END IF;

    -- Three exact RESTRICT foreign keys and their eight ordered column links.
    SET migration_stage = 'foreign-key-contract-audit';
    SELECT COUNT(*) INTO target_foreign_key_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      );
    SELECT COUNT(*) INTO target_foreign_key_contract_count
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND unique_constraint_schema = DATABASE()
      AND update_rule = 'RESTRICT'
      AND delete_rule = 'RESTRICT'
      AND ((table_name = 'fbs_connector_binding'
            AND constraint_name = 'fk_connector_binding_entitlement'
            AND referenced_table_name = 'fbs_product_entitlement')
        OR (table_name = 'fbs_connector_binding_scope'
            AND constraint_name = 'fk_connector_binding_scope_binding'
            AND referenced_table_name = 'fbs_connector_binding')
        OR (table_name = 'fbs_connector_binding_receipt'
            AND constraint_name = 'fk_connector_binding_receipt_binding'
            AND referenced_table_name = 'fbs_connector_binding'));
    SELECT COUNT(*) INTO target_foreign_key_column_count
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND referenced_table_schema = DATABASE()
      AND referenced_table_name IS NOT NULL
      AND ((table_name = 'fbs_connector_binding'
            AND constraint_name = 'fk_connector_binding_entitlement'
            AND referenced_table_name = 'fbs_product_entitlement'
            AND ((ordinal_position = 1 AND column_name = 'enterprise_id' AND referenced_column_name = 'enterprise_id')
              OR (ordinal_position = 2 AND column_name = 'member_id' AND referenced_column_name = 'member_id')
              OR (ordinal_position = 3 AND column_name = 'product_code' AND referenced_column_name = 'product_code')))
        OR (table_name = 'fbs_connector_binding_scope'
            AND constraint_name = 'fk_connector_binding_scope_binding'
            AND referenced_table_name = 'fbs_connector_binding'
            AND ordinal_position = 1 AND column_name = 'binding_id' AND referenced_column_name = 'binding_id')
        OR (table_name = 'fbs_connector_binding_receipt'
            AND constraint_name = 'fk_connector_binding_receipt_binding'
            AND referenced_table_name = 'fbs_connector_binding'
            AND ((ordinal_position = 1 AND column_name = 'binding_id' AND referenced_column_name = 'binding_id')
              OR (ordinal_position = 2 AND column_name = 'enterprise_id' AND referenced_column_name = 'enterprise_id')
              OR (ordinal_position = 3 AND column_name = 'member_id' AND referenced_column_name = 'member_id')
              OR (ordinal_position = 4 AND column_name = 'user_id' AND referenced_column_name = 'user_id'))));
    SELECT COUNT(*) INTO target_foreign_key_total_column_count
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND referenced_table_schema = DATABASE()
      AND referenced_table_name IS NOT NULL
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      );
    IF target_foreign_key_count <> 3 OR target_foreign_key_contract_count <> 3
       OR target_foreign_key_column_count <> 8
       OR target_foreign_key_total_column_count <> 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding exact foreign-key contract has drifted';
    END IF;

    -- Fifteen named and enforced CHECK constraints must exist. Exact normalized
    -- clauses reject same-name weakening such as NOT ENFORCED or OR 1=1 while
    -- tolerating MySQL charset introducers, whitespace and escaped quotes.
    SET migration_stage = 'check-contract-audit';
    SELECT COUNT(*) INTO target_check_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_type = 'CHECK'
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      );
    SELECT COUNT(*) INTO target_enforced_check_count
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_type = 'CHECK'
      AND enforced = 'YES'
      AND table_name IN (
          'fbs_connector_binding',
          'fbs_connector_binding_scope',
          'fbs_connector_binding_receipt'
      );
    SELECT COUNT(*) INTO target_check_contract_count
    FROM (
        SELECT tc.table_name, tc.constraint_name,
               REPLACE(REPLACE(
                   LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                       cc.check_clause, '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                       CHAR(13), ''), CHAR(92), '')),
                   '_utf8mb4', ''), '_utf8mb3', '') AS normalized_clause
        FROM information_schema.table_constraints tc
        INNER JOIN information_schema.check_constraints cc
          ON cc.constraint_schema = tc.constraint_schema
         AND cc.constraint_name = tc.constraint_name
        WHERE tc.constraint_schema = DATABASE()
          AND tc.constraint_type = 'CHECK'
          AND tc.enforced = 'YES'
          AND tc.table_name IN (
              'fbs_connector_binding',
              'fbs_connector_binding_scope',
              'fbs_connector_binding_receipt'
          )
    ) AS checks_by_name
    WHERE (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_identifiers'
           AND CAST(normalized_clause AS BINARY) = CAST('((char_length(binding_id)>0)and(char_length(issuer_uri)>0)and(char_length(resource_uri)>0)and(char_length(client_id)>0)and(cast(product_codeascharcharsetbinary)=cast(''fbsir_independent_board''ascharcharsetbinary)))' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_source'
           AND CAST(normalized_clause AS BINARY) = CAST('(source_code=''workbuddy'')' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_connector'
           AND CAST(normalized_clause AS BINARY) = CAST('(connector_code=''fbs-connector'')' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_resource'
           AND CAST(normalized_clause AS BINARY) = CAST('(resource_uri=''https://api2.u3w.com/fbs-mcp/mcp'')' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_status'
           AND CAST(normalized_clause AS BINARY) = CAST('(statusin(''active'',''revoked'',''compromised''))' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_verification'
           AND CAST(normalized_clause AS BINARY) = CAST('(verification_methodin(''mcp_initialize'',''mcp_tools_list''))' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_principal_digest'
           AND CAST(normalized_clause AS BINARY) = CAST('regexp_like(principal_subject_digest,''^[0-9a-f]{64}$'')' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_evidence_digest'
           AND CAST(normalized_clause AS BINARY) = CAST('regexp_like(evidence_digest,''^[0-9a-f]{64}$'')' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_lifecycle'
           AND CAST(normalized_clause AS BINARY) = CAST('(((status=''active'')and(revoked_atisnull))or((statusin(''revoked'',''compromised''))and(revoked_atisnotnull)))' AS BINARY))
       OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_temporal'
           AND CAST(normalized_clause AS BINARY) = CAST('((last_seen_at>=verified_at)and(valid_until>last_seen_at)and((revoked_atisnull)or(revoked_at>=last_seen_at)))' AS BINARY))
       OR (table_name = 'fbs_connector_binding_scope' AND constraint_name = 'chk_connector_binding_scope_code'
           AND CAST(normalized_clause AS BINARY) = CAST('(scope_codein(''identity.read'',''entitlement.read'',''board.meeting.reserve'',''board.receipt.write''))' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'chk_connector_binding_receipt_id'
           AND CAST(normalized_clause AS BINARY) = CAST('(char_length(receipt_id)>0)' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'chk_connector_binding_receipt_action'
           AND CAST(normalized_clause AS BINARY) = CAST('(actionin(''connector_binding_verified'',''connector_binding_revoked''))' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'chk_connector_binding_receipt_payload_digest'
           AND CAST(normalized_clause AS BINARY) = CAST('regexp_like(payload_digest,''^[0-9a-f]{64}$'')' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'chk_connector_binding_receipt_evidence'
           AND CAST(normalized_clause AS BINARY) = CAST('(evidence_level=''action_completed'')' AS BINARY));
    IF target_check_count <> 15 OR target_enforced_check_count <> 15
       OR target_check_contract_count <> 15 THEN
        SET check_contract_error = CONCAT(
            'Connector binding exact fifteen-check contract has drifted: discovered=',
            target_check_count,
            ', enforced=',
            target_enforced_check_count,
            ', matched=',
            target_check_contract_count
        );
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = check_contract_error;
    END IF;

    SET migration_stage = 'digest-column-audit';
    SELECT COUNT(*) INTO target_digest_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_connector_binding'
            AND column_name IN ('principal_subject_digest','evidence_digest'))
        OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'payload_digest'))
      AND column_type = 'char(64)'
      AND is_nullable = 'NO'
      AND character_set_name = 'ascii'
      AND collation_name = 'ascii_bin';
    IF target_digest_column_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding digest columns must be ASCII binary CHAR(64)';
    END IF;

    IF migration_exists <> 0 THEN
        SET migration_stage = 'completed-trigger-audit';
        SELECT COUNT(*) INTO target_trigger_count
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND event_object_table = 'fbs_connector_binding_receipt';
        SELECT COUNT(*) INTO target_trigger_contract_count
        FROM (
            SELECT trigger_name, event_manipulation, action_timing, action_orientation,
                   LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                       action_statement, '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                       CHAR(13), ''), CHAR(92), '')) AS normalized_action
            FROM information_schema.triggers
            WHERE trigger_schema = DATABASE()
              AND event_object_table = 'fbs_connector_binding_receipt'
              AND action_condition IS NULL
        ) receipt_triggers
        WHERE action_timing = 'BEFORE'
          AND action_orientation = 'ROW'
          AND CAST(normalized_action AS BINARY) = CAST(
              'signalsqlstate''45000''setmessage_text=''connectorbindingreceiptsareimmutable'''
              AS BINARY)
          AND ((trigger_name = 'trg_connector_binding_receipt_no_update'
                AND event_manipulation = 'UPDATE')
            OR (trigger_name = 'trg_connector_binding_receipt_no_delete'
                AND event_manipulation = 'DELETE'));
        IF target_trigger_count <> 2 OR target_trigger_contract_count <> 2 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Connector binding receipt immutability trigger contract has drifted';
        END IF;
    END IF;

    -- Leave the connection-scoped lock held for top-level trigger creation and
    -- the finalizer. Any error that exits the mysql client releases it.
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding migration lost lock ownership before trigger finalization';
    END IF;
END$$

DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_connector_binding_20260721`$$
CREATE PROCEDURE `u3w_finalize_independent_board_connector_binding_20260721`()
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
        CONCAT(DATABASE(), ':20260721_independent_board_connector_binding_v1'),
        256
    );
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding finalizer does not own the migration lock';
    END IF;

    SELECT COUNT(*) INTO target_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table = 'fbs_connector_binding_receipt';
    SELECT COUNT(*) INTO target_trigger_contract_count
    FROM (
        SELECT trigger_name, event_manipulation, action_timing, action_orientation,
               LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                   action_statement, '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                   CHAR(13), ''), CHAR(92), '')) AS normalized_action
        FROM information_schema.triggers
        WHERE trigger_schema = DATABASE()
          AND event_object_table = 'fbs_connector_binding_receipt'
          AND action_condition IS NULL
    ) receipt_triggers
    WHERE action_timing = 'BEFORE'
      AND action_orientation = 'ROW'
      AND CAST(normalized_action AS BINARY) = CAST(
          'signalsqlstate''45000''setmessage_text=''connectorbindingreceiptsareimmutable'''
          AS BINARY)
      AND ((trigger_name = 'trg_connector_binding_receipt_no_update'
            AND event_manipulation = 'UPDATE')
        OR (trigger_name = 'trg_connector_binding_receipt_no_delete'
            AND event_manipulation = 'DELETE'));
    IF target_trigger_count <> 2 OR target_trigger_contract_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding receipt immutability trigger contract has drifted';
    END IF;

    START TRANSACTION;
    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_connector_binding_v1';
    IF migration_exists = 0 THEN
        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260721_independent_board_connector_binding_v1',
            'Independent Board authoritative Connector binding, scope and receipt tables'
        );
    END IF;
    SELECT COUNT(*) INTO target_internal_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_connector_binding_v1'
      AND `description` = 'Independent Board authoritative Connector binding, scope and receipt tables';
    SELECT COUNT(*) INTO target_version_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_connector_binding_v1';
    IF target_internal_receipt_count <> 1 OR target_version_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding internal migration receipt is missing or drifted';
    END IF;
    COMMIT;

    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_released;
    IF migration_lock_released IS NULL OR migration_lock_released <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Connector binding migration advisory lock release failed';
    END IF;
END$$

CALL `u3w_migrate_independent_board_connector_binding_20260721`()$$
CREATE TRIGGER IF NOT EXISTS `trg_connector_binding_receipt_no_update`
    BEFORE UPDATE ON `fbs_connector_binding_receipt`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Connector binding receipts are immutable'$$
CREATE TRIGGER IF NOT EXISTS `trg_connector_binding_receipt_no_delete`
    BEFORE DELETE ON `fbs_connector_binding_receipt`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Connector binding receipts are immutable'$$
CALL `u3w_finalize_independent_board_connector_binding_20260721`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_connector_binding_20260721`$$
DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_connector_binding_20260721`$$

DELIMITER ;
