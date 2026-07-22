-- ============================================================
-- FBSir Independent Board plan-policy controlled write authority
-- Migration: 20260723_independent_board_plan_policy_authority_v1
-- Public manifest step: public_init_041
-- Target: MySQL Community 8.0.30 and 8.4.8
-- Scope: durable, default-off stored authority for policy receipt transitions.
-- Production privilege grants/revokes are deliberately outside this migration.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_ib_plan_policy_authority_20260723`$$
CREATE PROCEDURE `u3w_migrate_ib_plan_policy_authority_20260723`()
BEGIN
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE required_count INT DEFAULT 0;
    DECLARE controlled_routine_exists INT DEFAULT 0;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority migration requires an explicit target database';
    END IF;
    IF VERSION() NOT LIKE '8.0.30%' AND VERSION() NOT LIKE '8.4.8%' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority migration requires reviewed MySQL 8.0.30 or 8.4.8';
    END IF;

    SELECT COUNT(*) INTO required_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_plan_policy_v1'
      AND `description` = 'Independent Board immutable plan policy revisions and operation lineage';
    IF required_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority migration requires the exact public_init_039 internal receipt';
    END IF;

    SELECT COUNT(*) INTO required_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260723_independent_board_plan_policy_monotonic_chain_v1'
      AND `description` = 'Independent Board plan policy database monotonic-chain trigger guards';
    IF required_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority migration requires the exact public_init_040 internal receipt';
    END IF;

    SELECT COUNT(*) INTO required_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND trigger_name IN (
          'trg_plan_policy_receipt_guard_insert',
          'trg_plan_policy_head_guard_update',
          'trg_plan_policy_head_no_insert',
          'trg_plan_policy_head_no_delete'
      );
    IF required_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority migration requires all public_init_040 guard triggers';
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260723_independent_board_plan_policy_authority_v1';
    IF migration_exists > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority migration receipt is duplicated';
    END IF;

    SELECT COUNT(*) INTO controlled_routine_exists
    FROM information_schema.routines
    WHERE routine_schema = DATABASE()
      AND routine_name = 'fbsir_independent_board_plan_policy_transition_v1'
      AND routine_type = 'PROCEDURE';
    IF migration_exists = 0 AND controlled_routine_exists <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority refuses an unreceipted pre-existing controlled procedure';
    END IF;
    IF migration_exists = 1 AND controlled_routine_exists <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority receipt exists but controlled procedure is missing or duplicated';
    END IF;
END$$

CALL `u3w_migrate_ib_plan_policy_authority_20260723`()$$

CREATE DEFINER = CURRENT_USER PROCEDURE IF NOT EXISTS
`fbsir_independent_board_plan_policy_transition_v1`(
    IN p_receipt_id VARCHAR(128),
    IN p_product_code VARCHAR(64),
    IN p_plan_code VARCHAR(64),
    IN p_policy_version BIGINT UNSIGNED,
    IN p_previous_receipt_id VARCHAR(128),
    IN p_rollback_of_receipt_id VARCHAR(128),
    IN p_action VARCHAR(64),
    IN p_actor_type VARCHAR(64),
    IN p_actor_user_id BIGINT,
    IN p_idempotency_key_digest CHAR(64),
    IN p_command_digest CHAR(64),
    IN p_previous_policy_digest CHAR(64),
    IN p_policy_digest CHAR(64),
    IN p_plan_name VARCHAR(128),
    IN p_vip TINYINT(1),
    IN p_connector_required TINYINT(1),
    IN p_daily_meeting_limit INT,
    IN p_agenda_limit INT,
    IN p_seat_limit INT,
    IN p_secretary_enabled TINYINT(1),
    IN p_status VARCHAR(32),
    IN p_evidence_level VARCHAR(32),
    IN p_created_at DATETIME(3)
)
SQL SECURITY DEFINER
MODIFIES SQL DATA
BEGIN
    DECLARE v_active_receipt_id VARCHAR(128);
    DECLARE v_policy_version BIGINT UNSIGNED;
    DECLARE v_policy_digest CHAR(64);
    DECLARE v_updated_rows INT DEFAULT 0;

    IF p_receipt_id IS NULL OR p_product_code IS NULL OR p_plan_code IS NULL
       OR p_policy_version IS NULL OR p_previous_receipt_id IS NULL
       OR p_previous_policy_digest IS NULL OR p_policy_digest IS NULL
       OR p_actor_user_id IS NULL OR p_created_at IS NULL
       OR CAST(p_product_code AS BINARY) <> CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
       OR p_plan_code NOT IN ('BOARD_FREE', 'BOARD_VIP') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority rejected an incomplete or out-of-scope transition';
    END IF;

    /*
       Lock only the mutable directory row. A joining locking read would also
       lock the immutable parent receipt, which in turn blocks unrelated FK
       lineage inserts that reference that receipt while a revision is open.
    */
    SELECT h.`active_receipt_id`, h.`policy_version`
      INTO v_active_receipt_id, v_policy_version
    FROM `fbs_plan_policy_head` h
    WHERE CAST(h.`product_code` AS BINARY) = CAST(p_product_code AS BINARY)
      AND CAST(h.`plan_code` AS BINARY) = CAST(p_plan_code AS BINARY)
    FOR UPDATE;

    /* Immutable receipt lookup deliberately remains a non-locking read. */
    SELECT r.`policy_digest`
      INTO v_policy_digest
    FROM `fbs_plan_policy_revision_receipt` r
    WHERE CAST(r.`product_code` AS BINARY) = CAST(p_product_code AS BINARY)
      AND CAST(r.`plan_code` AS BINARY) = CAST(p_plan_code AS BINARY)
      AND CAST(r.`receipt_id` AS BINARY) = CAST(v_active_receipt_id AS BINARY)
      AND r.`policy_version` = v_policy_version
    LIMIT 1;

    IF v_active_receipt_id IS NULL OR v_policy_version IS NULL OR v_policy_digest IS NULL
       OR p_policy_version <> v_policy_version + 1
       OR CAST(p_previous_receipt_id AS BINARY) <> CAST(v_active_receipt_id AS BINARY)
       OR CAST(p_previous_policy_digest AS BINARY) <> CAST(v_policy_digest AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'BOARD_PLAN_POLICY_VERSION_CONFLICT';
    END IF;

    INSERT INTO `fbs_plan_policy_revision_receipt` (
        `receipt_id`, `product_code`, `plan_code`, `policy_version`,
        `previous_receipt_id`, `rollback_of_receipt_id`, `action`,
        `actor_type`, `actor_user_id`, `idempotency_key_digest`, `command_digest`,
        `previous_policy_digest`, `policy_digest`, `plan_name`, `vip`,
        `connector_required`, `daily_meeting_limit`, `agenda_limit`, `seat_limit`,
        `secretary_enabled`, `status`, `evidence_level`, `created_at`
    ) VALUES (
        p_receipt_id, p_product_code, p_plan_code, p_policy_version,
        p_previous_receipt_id, p_rollback_of_receipt_id, p_action,
        p_actor_type, p_actor_user_id, p_idempotency_key_digest, p_command_digest,
        p_previous_policy_digest, p_policy_digest, p_plan_name, p_vip,
        p_connector_required, p_daily_meeting_limit, p_agenda_limit, p_seat_limit,
        p_secretary_enabled, p_status, p_evidence_level, p_created_at
    );

    UPDATE `fbs_plan_policy_head`
    SET `active_receipt_id` = p_receipt_id,
        `policy_version` = p_policy_version,
        `updated_at` = GREATEST(`updated_at`, p_created_at)
    WHERE CAST(`product_code` AS BINARY) = CAST(p_product_code AS BINARY)
      AND CAST(`plan_code` AS BINARY) = CAST(p_plan_code AS BINARY)
      AND CAST(`active_receipt_id` AS BINARY) = CAST(v_active_receipt_id AS BINARY)
      AND `policy_version` = v_policy_version;
    SET v_updated_rows = ROW_COUNT();

    IF v_updated_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'BOARD_PLAN_POLICY_VERSION_CONFLICT';
    END IF;
END$$

DROP PROCEDURE IF EXISTS `u3w_finalize_ib_plan_policy_authority_20260723`$$
CREATE PROCEDURE `u3w_finalize_ib_plan_policy_authority_20260723`()
BEGIN
    DECLARE routine_count INT DEFAULT 0;
    DECLARE parameter_count INT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO routine_count
    FROM information_schema.routines
    WHERE routine_schema = DATABASE()
      AND routine_name = 'fbsir_independent_board_plan_policy_transition_v1'
      AND routine_type = 'PROCEDURE'
      AND security_type = 'DEFINER';
    IF routine_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority controlled procedure is missing or has unsafe security mode';
    END IF;

    SELECT COUNT(*) INTO parameter_count
    FROM information_schema.parameters
    WHERE specific_schema = DATABASE()
      AND specific_name = 'fbsir_independent_board_plan_policy_transition_v1'
      AND parameter_mode = 'IN';
    IF parameter_count <> 23 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority controlled procedure parameter contract drifted';
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260723_independent_board_plan_policy_authority_v1';
    IF migration_exists = 0 THEN
        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260723_independent_board_plan_policy_authority_v1',
            'Independent Board plan policy controlled procedure authority'
        );
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260723_independent_board_plan_policy_authority_v1'
      AND `description` = 'Independent Board plan policy controlled procedure authority';
    IF migration_exists <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy authority migration receipt drifted';
    END IF;
END$$

CALL `u3w_finalize_ib_plan_policy_authority_20260723`()$$

DROP PROCEDURE IF EXISTS `u3w_finalize_ib_plan_policy_authority_20260723`$$
DROP PROCEDURE IF EXISTS `u3w_migrate_ib_plan_policy_authority_20260723`$$

DELIMITER ;
