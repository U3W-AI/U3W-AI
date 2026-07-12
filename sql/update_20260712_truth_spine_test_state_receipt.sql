-- ============================================================
-- Truth Spine 测试态回执批次账本（MySQL 8，可重入）
-- 变更日期：2026-07-12
-- 边界：仅存已验签批次的脱敏摘要；不存原始会话、签名私钥或用户正文。
-- 信用：所有记录永久为零产品信用、零业务闭环信用；生产入流必须另行审批和迁移。
-- ============================================================

CREATE TABLE IF NOT EXISTS `u3w_schema_migration` (
    `version` VARCHAR(96) NOT NULL,
    `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `description` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='U3W可重入数据库迁移记录';

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_truth_spine_test_state_20260712`$$
CREATE PROCEDURE `u3w_migrate_truth_spine_test_state_20260712`()
BEGIN
    DECLARE migration_lock INT DEFAULT 0;
    DECLARE table_exists INT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE required_columns INT DEFAULT 0;
    DECLARE required_hash_columns INT DEFAULT 0;
    DECLARE required_idempotency_index_columns INT DEFAULT 0;
    DECLARE total_idempotency_index_columns INT DEFAULT 0;
    DECLARE required_constraints INT DEFAULT 0;
    DECLARE storage_engine VARCHAR(64);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        IF migration_lock = 1 THEN
            DO RELEASE_LOCK(CONCAT(DATABASE(), ':20260712_truth_spine_test_state_v1'));
        END IF;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Truth Spine migration requires an explicit target database';
    END IF;
    SELECT GET_LOCK(CONCAT(DATABASE(), ':20260712_truth_spine_test_state_v1'), 30)
      INTO migration_lock;
    IF migration_lock IS NULL OR migration_lock <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Could not acquire the Truth Spine migration lock';
    END IF;

    SELECT COUNT(*) INTO table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'fbs_truth_spine_receipt_batch';
    SELECT COUNT(*) INTO migration_exists
    FROM u3w_schema_migration
    WHERE version = '20260712_truth_spine_test_state_v1';
    IF table_exists <> 0 AND migration_exists = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Truth Spine table exists without its registered migration; audit it before continuing';
    END IF;

    CREATE TABLE IF NOT EXISTS `fbs_truth_spine_receipt_batch` (
        `batch_id`                    VARCHAR(128) NOT NULL COMMENT 'API2 已验签批次标识',
        `workload_id`                 VARCHAR(128) NOT NULL COMMENT '受控测试工作负载标识',
        `idempotency_key`             VARCHAR(191) NOT NULL COMMENT '不可变幂等键',
        `invocation_id`               VARCHAR(191) NOT NULL COMMENT '产品调用标识，不存会话正文',
        `product_id`                  VARCHAR(128) NOT NULL COMMENT '产品标识',
        `binding_hash`                CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同绑定摘要 SHA-256 小写十六进制',
        `host_receipt_hash`           CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '宿主回执摘要 SHA-256 小写十六进制',
        `signer_key_id`               VARCHAR(191) NOT NULL COMMENT '已验签公钥标识，不存密钥',
        `payload_sha256`              CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化批次摘要 SHA-256 小写十六进制',
        `state_class`                 VARCHAR(32)  NOT NULL COMMENT '固定 TEST_QUARANTINE',
        `verification_status`         VARCHAR(32)  NOT NULL COMMENT '固定 VERIFIED',
        `status`                      VARCHAR(48)  NOT NULL COMMENT '固定 ACCEPTED_TEST_QUARANTINE',
        `product_credit_eligible`     TINYINT      NOT NULL DEFAULT 0 COMMENT '固定 0',
        `business_closure_eligible`   TINYINT      NOT NULL DEFAULT 0 COMMENT '固定 0',
        `verified_at`                 DATETIME     NOT NULL COMMENT 'API2 验签完成时间',
        `create_time`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (`batch_id`),
        UNIQUE KEY `uk_truth_spine_batch_workload_idempotency` (`workload_id`, `idempotency_key`),
        KEY `idx_truth_spine_batch_invocation` (`invocation_id`, `create_time`),
        KEY `idx_truth_spine_batch_product` (`product_id`, `create_time`),
        CONSTRAINT `chk_truth_spine_state_class` CHECK (`state_class` = 'TEST_QUARANTINE'),
        CONSTRAINT `chk_truth_spine_verification_status` CHECK (`verification_status` = 'VERIFIED'),
        CONSTRAINT `chk_truth_spine_status` CHECK (`status` = 'ACCEPTED_TEST_QUARANTINE'),
        CONSTRAINT `chk_truth_spine_product_credit` CHECK (`product_credit_eligible` = 0),
        CONSTRAINT `chk_truth_spine_business_credit` CHECK (`business_closure_eligible` = 0)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Truth Spine 已验签测试态批次账本';

    SELECT COUNT(*) INTO required_columns
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fbs_truth_spine_receipt_batch'
      AND column_name IN ('batch_id', 'workload_id', 'idempotency_key', 'invocation_id', 'product_id',
                          'binding_hash', 'host_receipt_hash', 'signer_key_id', 'payload_sha256',
                          'state_class', 'verification_status', 'status', 'product_credit_eligible',
                          'business_closure_eligible', 'verified_at', 'create_time');
    IF required_columns <> 16 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Truth Spine table schema is incomplete';
    END IF;
    SELECT COUNT(*) INTO required_hash_columns
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fbs_truth_spine_receipt_batch'
      AND column_name IN ('binding_hash', 'host_receipt_hash', 'payload_sha256')
      AND column_type = 'char(64)' AND is_nullable = 'NO'
      AND character_set_name = 'ascii' AND collation_name = 'ascii_bin';
    IF required_hash_columns <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Truth Spine hash columns must be ASCII binary CHAR(64)';
    END IF;
    SELECT engine INTO storage_engine
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'fbs_truth_spine_receipt_batch';
    IF storage_engine <> 'InnoDB' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Truth Spine table must use InnoDB';
    END IF;
    SELECT COUNT(*) INTO required_idempotency_index_columns
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'fbs_truth_spine_receipt_batch'
      AND index_name = 'uk_truth_spine_batch_workload_idempotency'
      AND non_unique = 0
      AND ((seq_in_index = 1 AND column_name = 'workload_id')
           OR (seq_in_index = 2 AND column_name = 'idempotency_key'));
    IF required_idempotency_index_columns <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Truth Spine workload idempotency index is missing';
    END IF;
    SELECT COUNT(*) INTO total_idempotency_index_columns
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'fbs_truth_spine_receipt_batch'
      AND index_name = 'uk_truth_spine_batch_workload_idempotency';
    IF total_idempotency_index_columns <> 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Truth Spine workload idempotency index has an unexpected definition';
    END IF;
    SELECT COUNT(*) INTO required_constraints
    FROM information_schema.table_constraints tc
    INNER JOIN information_schema.check_constraints cc
      ON cc.constraint_schema = tc.constraint_schema AND cc.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = DATABASE() AND tc.table_name = 'fbs_truth_spine_receipt_batch'
      AND tc.constraint_type = 'CHECK'
      AND ((tc.constraint_name = 'chk_truth_spine_state_class'
            AND LOWER(REPLACE(REPLACE(REGEXP_REPLACE(cc.check_clause, '=[[:space:]]*_[[:alnum:]]+', '='), '`', ''), CHAR(92), ''))
                REGEXP '^[[:space:]()]*state_class[[:space:]]*=[[:space:]]*''test_quarantine''[[:space:]()]*$')
           OR (tc.constraint_name = 'chk_truth_spine_verification_status'
            AND LOWER(REPLACE(REPLACE(REGEXP_REPLACE(cc.check_clause, '=[[:space:]]*_[[:alnum:]]+', '='), '`', ''), CHAR(92), ''))
                REGEXP '^[[:space:]()]*verification_status[[:space:]]*=[[:space:]]*''verified''[[:space:]()]*$')
           OR (tc.constraint_name = 'chk_truth_spine_status'
            AND LOWER(REPLACE(REPLACE(REGEXP_REPLACE(cc.check_clause, '=[[:space:]]*_[[:alnum:]]+', '='), '`', ''), CHAR(92), ''))
                REGEXP '^[[:space:]()]*status[[:space:]]*=[[:space:]]*''accepted_test_quarantine''[[:space:]()]*$')
           OR (tc.constraint_name = 'chk_truth_spine_product_credit'
            AND REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause, '`', ''), ' ', ''), '(', ''), ')', '') = 'product_credit_eligible=0')
           OR (tc.constraint_name = 'chk_truth_spine_business_credit'
            AND REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause, '`', ''), ' ', ''), '(', ''), ')', '') = 'business_closure_eligible=0'));
    IF required_constraints <> 5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Truth Spine zero-credit constraints are missing';
    END IF;

    INSERT INTO u3w_schema_migration (`version`, `description`)
    VALUES ('20260712_truth_spine_test_state_v1', 'Truth Spine测试态批次账本、工作负载幂等与零信用约束')
    ON DUPLICATE KEY UPDATE description = VALUES(description);
    DO RELEASE_LOCK(CONCAT(DATABASE(), ':20260712_truth_spine_test_state_v1'));
END$$

CALL `u3w_migrate_truth_spine_test_state_20260712`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_truth_spine_test_state_20260712`$$

DELIMITER ;
