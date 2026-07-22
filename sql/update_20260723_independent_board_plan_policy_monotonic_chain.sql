-- ============================================================
-- FBSir Independent Board plan-policy database monotonic chain
-- Migration: 20260723_independent_board_plan_policy_monotonic_chain_v1
-- Public manifest step: public_init_040
-- Target: MySQL Community 8.0.30 and 8.4.8
-- Scope: direct-DML guards for immutable policy receipts and CAS heads
-- Precondition: public_init_039 is complete and exact.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_ib_plan_policy_monotonic_20260723`$$
CREATE PROCEDURE `u3w_migrate_ib_plan_policy_monotonic_20260723`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE target_count INT DEFAULT 0;
    DECLARE allowed_trigger_count INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
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
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration requires an explicit target database';
    END IF;
    IF VERSION() NOT LIKE '8.0.30%' AND VERSION() NOT LIKE '8.4.8%' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration requires reviewed MySQL 8.0.30 or 8.4.8';
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260723_independent_board_plan_policy_monotonic_chain_v1'),
        256
    );
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the plan policy monotonic-chain migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration lock ownership verification failed';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_plan_policy_v1'
      AND `description` = 'Independent Board immutable plan policy revisions and operation lineage';
    IF target_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration requires the exact public_init_039 internal receipt';
    END IF;

    -- A direct invocation of 040 must independently prove its frozen 039
    -- predecessor.  The normal initializer already makes this assertion, but
    -- the migration itself cannot rely on that caller boundary.
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
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration requires the exact public_init_039 seven-trigger contract';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM `fbs_plan_policy_revision_receipt`
    WHERE `policy_version` = 1
      AND `action` = 'PLAN_POLICY_BASELINED'
      AND `actor_type` = 'SYSTEM_MIGRATION' AND `actor_user_id` IS NULL
      AND `previous_receipt_id` IS NULL AND `previous_policy_digest` IS NULL
      AND `rollback_of_receipt_id` IS NULL
      AND ((CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
            AND CAST(`plan_code` AS BINARY) = CAST('BOARD_FREE' AS BINARY)
            AND CAST(`receipt_id` AS BINARY) = CAST('plan-policy-baseline-board-free-v1' AS BINARY)
            AND `policy_digest` = '8ae3df83c9f56974261d1e471eb19034b2b782f793f74333c64a13c61dae8982'
            AND CAST(`plan_name` AS BINARY) = CAST('Independent Board Free' AS BINARY)
            AND `vip` = 0 AND `connector_required` = 0
            AND `daily_meeting_limit` = 1 AND `agenda_limit` = 5
            AND `seat_limit` = 3 AND `secretary_enabled` = 0
            AND CAST(`status` AS BINARY) = CAST('ACTIVE' AS BINARY)
            AND CAST(`evidence_level` AS BINARY) = CAST('ACTION_COMPLETED' AS BINARY))
        OR (CAST(`product_code` AS BINARY) = CAST('FBSIR_INDEPENDENT_BOARD' AS BINARY)
            AND CAST(`plan_code` AS BINARY) = CAST('BOARD_VIP' AS BINARY)
            AND CAST(`receipt_id` AS BINARY) = CAST('plan-policy-baseline-board-vip-v1' AS BINARY)
            AND `policy_digest` = '02e90096b76d25b69c938fa65cfc3931207ac0a2648447bf613dca591619da51'
            AND CAST(`plan_name` AS BINARY) = CAST('Independent Board VIP' AS BINARY)
            AND `vip` = 1 AND `connector_required` = 1
            AND `daily_meeting_limit` = 5 AND `agenda_limit` = 30
            AND `seat_limit` IS NULL AND `secretary_enabled` = 1
            AND CAST(`status` AS BINARY) = CAST('ACTIVE' AS BINARY)
            AND CAST(`evidence_level` AS BINARY) = CAST('ACTION_COMPLETED' AS BINARY)));
    IF target_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration requires exact public_init_039 SYSTEM_MIGRATION baselines';
    END IF;
    SELECT COUNT(*) INTO target_count
    FROM `fbs_plan_policy_revision_receipt`
    WHERE `policy_version` = 1;
    IF target_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration requires exactly two public_init_039 baseline revisions';
    END IF;
    SELECT COUNT(*) INTO target_count FROM `fbs_plan_policy_head`;
    IF target_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration requires the exact two-row public_init_039 head catalog';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB'
      AND table_name IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt'
      );
    IF target_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain dependency tables are incomplete or not InnoDB';
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260723_independent_board_plan_policy_monotonic_chain_v1';
    IF migration_exists > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain internal migration receipt is duplicated';
    END IF;
    IF migration_exists = 1 THEN
        SELECT COUNT(*) INTO target_count
        FROM `u3w_schema_migration`
        WHERE `version` = '20260723_independent_board_plan_policy_monotonic_chain_v1'
          AND `description` = 'Independent Board plan policy database monotonic-chain trigger guards';
        IF target_count <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Plan policy monotonic-chain internal migration receipt has drifted';
        END IF;
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt',
          'fbs_entitlement_receipt'
      );
    SELECT COUNT(*) INTO allowed_trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND trigger_name IN (
          'trg_plan_policy_receipt_no_update',
          'trg_plan_policy_receipt_no_delete',
          'trg_usage_operation_policy_guard_insert',
          'trg_usage_operation_policy_no_update',
          'trg_usage_operation_policy_no_delete',
          'trg_entitlement_receipt_no_update',
          'trg_entitlement_receipt_no_delete',
          'trg_plan_policy_receipt_guard_insert',
          'trg_plan_policy_head_guard_update',
          'trg_plan_policy_head_no_insert',
          'trg_plan_policy_head_no_delete'
      );
    IF target_count <> allowed_trigger_count OR target_count < 7 OR target_count > 11 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain trigger prefix has drifted';
    END IF;
    IF migration_exists = 1 AND target_count <> 11 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Completed plan policy monotonic-chain trigger set has drifted';
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
     AND latest.`max_version` = h.`policy_version`;
    IF target_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration requires both heads to point to latest committed receipts';
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
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration requires an exact contiguous predecessor chain';
    END IF;
END$$

DROP PROCEDURE IF EXISTS `u3w_finalize_ib_plan_policy_monotonic_20260723`$$
CREATE PROCEDURE `u3w_finalize_ib_plan_policy_monotonic_20260723`()
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
        CONCAT(DATABASE(), ':20260723_independent_board_plan_policy_monotonic_chain_v1'),
        256
    );
    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain finalizer does not own the migration lock';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND action_timing = 'BEFORE' AND action_orientation = 'ROW'
      AND action_condition IS NULL AND action_order = 1
      AND ((trigger_name = 'trg_plan_policy_receipt_guard_insert'
            AND event_object_table = 'fbs_plan_policy_revision_receipt'
            AND event_manipulation = 'INSERT'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                '800164bb628bf25e15b736152d0879f862ca174eadd470d46ac368c817fa5c5e')
        OR (trigger_name = 'trg_plan_policy_head_guard_update'
            AND event_object_table = 'fbs_plan_policy_head'
            AND event_manipulation = 'UPDATE'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                'e80f9ee7c66392747f96a911ca5ae9cbe090dfcbbf58e67e6ad2b3947f49efb3')
        OR (trigger_name = 'trg_plan_policy_head_no_insert'
            AND event_object_table = 'fbs_plan_policy_head'
            AND event_manipulation = 'INSERT'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                '3781ce8eff52c52614c2876b8e8a4a63501b29f1cfcf6c363dc9199df5e6e9ae')
        OR (trigger_name = 'trg_plan_policy_head_no_delete'
            AND event_object_table = 'fbs_plan_policy_head'
            AND event_manipulation = 'DELETE'
            AND SHA2(CAST(action_statement AS BINARY),256) =
                '8f0befc9a585fa853465adcea60a0e98457ca64f873d611e75536bc7c17d184f'));
    IF target_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain exact trigger metadata contract has drifted';
    END IF;
    SELECT COUNT(*) INTO target_count
    FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND event_object_table IN (
          'fbs_plan_policy_revision_receipt',
          'fbs_plan_policy_head',
          'fbs_usage_operation_policy_receipt',
          'fbs_entitlement_receipt'
      );
    IF target_count <> 11 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain receipt tables contain unexpected triggers';
    END IF;

    START TRANSACTION;
    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260723_independent_board_plan_policy_monotonic_chain_v1';
    IF migration_exists = 0 THEN
        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260723_independent_board_plan_policy_monotonic_chain_v1',
            'Independent Board plan policy database monotonic-chain trigger guards'
        );
    END IF;
    SELECT COUNT(*) INTO target_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260723_independent_board_plan_policy_monotonic_chain_v1'
      AND `description` = 'Independent Board plan policy database monotonic-chain trigger guards';
    IF target_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain internal migration receipt is missing or drifted';
    END IF;
    COMMIT;

    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_released;
    IF migration_lock_released IS NULL OR migration_lock_released <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy monotonic-chain migration advisory lock release failed';
    END IF;
END$$

CALL `u3w_migrate_ib_plan_policy_monotonic_20260723`()$$

CREATE TRIGGER IF NOT EXISTS `trg_plan_policy_receipt_guard_insert`
    BEFORE INSERT ON `fbs_plan_policy_revision_receipt`
    FOR EACH ROW
BEGIN
    DECLARE current_receipt_id VARCHAR(128);
    DECLARE current_policy_version BIGINT UNSIGNED;
    DECLARE current_policy_digest CHAR(64);

    SELECT h.`active_receipt_id`, h.`policy_version`, r.`policy_digest`
      INTO current_receipt_id, current_policy_version, current_policy_digest
    FROM `fbs_plan_policy_head` h
    INNER JOIN `fbs_plan_policy_revision_receipt` r
      ON r.`product_code` = h.`product_code`
     AND r.`plan_code` = h.`plan_code`
     AND r.`receipt_id` = h.`active_receipt_id`
     AND r.`policy_version` = h.`policy_version`
    WHERE CAST(h.`product_code` AS BINARY) = CAST(NEW.`product_code` AS BINARY)
      AND CAST(h.`plan_code` AS BINARY) = CAST(NEW.`plan_code` AS BINARY)
    LIMIT 1;

    IF current_receipt_id IS NULL OR current_policy_version IS NULL
       OR current_policy_digest IS NULL
       OR NEW.`policy_version` <> current_policy_version + 1
       OR CAST(NEW.`previous_receipt_id` AS BINARY) <> CAST(current_receipt_id AS BINARY)
       OR CAST(NEW.`previous_policy_digest` AS BINARY) <> CAST(current_policy_digest AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy receipt must be the exact direct successor of the current head';
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS `trg_plan_policy_head_guard_update`
    BEFORE UPDATE ON `fbs_plan_policy_head`
    FOR EACH ROW
BEGIN
    DECLARE target_count INT DEFAULT 0;

    IF CAST(NEW.`product_code` AS BINARY) <> CAST(OLD.`product_code` AS BINARY)
       OR CAST(NEW.`plan_code` AS BINARY) <> CAST(OLD.`plan_code` AS BINARY)
       OR NEW.`policy_version` <> OLD.`policy_version` + 1
       OR CAST(NEW.`created_at` AS BINARY) <> CAST(OLD.`created_at` AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy head may only advance one immutable version in place';
    END IF;

    SELECT COUNT(*) INTO target_count
    FROM `fbs_plan_policy_revision_receipt` r
    INNER JOIN `fbs_plan_policy_revision_receipt` p
      ON p.`product_code` = OLD.`product_code`
     AND p.`plan_code` = OLD.`plan_code`
     AND p.`receipt_id` = OLD.`active_receipt_id`
     AND p.`policy_version` = OLD.`policy_version`
    WHERE r.`product_code` = OLD.`product_code`
      AND r.`plan_code` = OLD.`plan_code`
      AND CAST(r.`receipt_id` AS BINARY) = CAST(NEW.`active_receipt_id` AS BINARY)
      AND r.`policy_version` = NEW.`policy_version`
      AND r.`policy_version` = OLD.`policy_version` + 1
      AND CAST(r.`previous_receipt_id` AS BINARY) = CAST(OLD.`active_receipt_id` AS BINARY)
      AND CAST(r.`previous_policy_digest` AS BINARY) = CAST(p.`policy_digest` AS BINARY);
    IF target_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Plan policy head must point to its exact direct successor receipt';
    END IF;
END$$

CREATE TRIGGER IF NOT EXISTS `trg_plan_policy_head_no_insert`
    BEFORE INSERT ON `fbs_plan_policy_head`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Plan policy heads are initialized only by public_init_039';
END$$

CREATE TRIGGER IF NOT EXISTS `trg_plan_policy_head_no_delete`
    BEFORE DELETE ON `fbs_plan_policy_head`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Plan policy heads are immutable routing pointers';
END$$

CALL `u3w_finalize_ib_plan_policy_monotonic_20260723`()$$

DROP PROCEDURE IF EXISTS `u3w_finalize_ib_plan_policy_monotonic_20260723`$$
DROP PROCEDURE IF EXISTS `u3w_migrate_ib_plan_policy_monotonic_20260723`$$

DELIMITER ;
