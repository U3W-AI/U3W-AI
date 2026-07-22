-- ============================================================
-- FBSir Independent Board W3g default-off credit administration
-- Migration: 20260722_independent_board_credit_candidate_menu_v1
-- Target: MySQL 8
-- Scope: one disabled credit page and two disabled fine permissions
--
-- This manual candidate migration is inert unless the caller sets, in the
-- same mysql session before sourcing this file:
--
--   SET @u3w_enable_independent_board_w3g_credit_candidate = 1;
--
-- The migration never activates a menu and never grants a role. Runtime API
-- availability remains independently default-off behind
-- FBSIR_INDEPENDENT_BOARD_CREDIT_CANDIDATE_ENABLED=false. A future reviewed
-- activation must change status and authorization separately.
--
-- Replays are read-only audits. They do not repair missing rows, drifted
-- identities, unknown children, role bindings, or a changed receipt.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_credit_candidate_menu_20260722`$$
CREATE PROCEDURE `u3w_migrate_independent_board_credit_candidate_menu_20260722`()
BEGIN
    DECLARE public_lock_name CHAR(64);
    DECLARE candidate_lock_name CHAR(64);
    DECLARE public_lock_acquired INT DEFAULT 0;
    DECLARE candidate_lock_acquired INT DEFAULT 0;
    DECLARE lock_owner BIGINT DEFAULT NULL;
    DECLARE current_connection BIGINT DEFAULT 0;
    DECLARE required_table_count INT DEFAULT 0;
    DECLARE prerequisite_receipt_count INT DEFAULT 0;
    DECLARE prerequisite_admin_root_count INT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE exact_receipt_count INT DEFAULT 0;
    DECLARE first_apply INT DEFAULT 0;
    DECLARE target_identity_count INT DEFAULT 0;
    DECLARE target_child_count INT DEFAULT 0;
    DECLARE target_binding_count INT DEFAULT 0;
    DECLARE exact_page_count INT DEFAULT 0;
    DECLARE exact_grant_count INT DEFAULT 0;
    DECLARE exact_reverse_count INT DEFAULT 0;
    DECLARE target_admin_root_id BIGINT DEFAULT NULL;
    DECLARE target_page_id BIGINT DEFAULT NULL;
    DECLARE target_grant_id BIGINT DEFAULT NULL;
    DECLARE target_reverse_id BIGINT DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF candidate_lock_acquired = 1 THEN
            SELECT IS_USED_LOCK(candidate_lock_name) INTO lock_owner;
            IF lock_owner = CONNECTION_ID() THEN
                DO RELEASE_LOCK(candidate_lock_name);
            END IF;
        END IF;
        IF public_lock_acquired = 1 THEN
            SELECT IS_USED_LOCK(public_lock_name) INTO lock_owner;
            IF lock_owner = CONNECTION_ID() THEN
                DO RELEASE_LOCK(public_lock_name);
            END IF;
        END IF;
        RESIGNAL;
    END;

    IF COALESCE(@u3w_enable_independent_board_w3g_credit_candidate, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu migration requires explicit session opt-in';
    END IF;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu migration requires an explicit target database';
    END IF;

    SET public_lock_name = SHA2(
        CONCAT(DATABASE(), ':public-database-manifest:v1'), 256
    );
    SET candidate_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260722_independent_board_credit_candidate_menu_v1'), 256
    );
    IF CHAR_LENGTH(public_lock_name) <> 64 OR CHAR_LENGTH(candidate_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu lock names must be stable digests';
    END IF;

    SELECT GET_LOCK(public_lock_name, 30) INTO public_lock_acquired;
    IF public_lock_acquired IS NULL OR public_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the public manifest lock for W3g credit menu';
    END IF;
    SELECT IS_USED_LOCK(public_lock_name), CONNECTION_ID()
      INTO lock_owner, current_connection;
    IF lock_owner IS NULL OR lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit menu public manifest lock is not owned by this connection';
    END IF;

    SELECT GET_LOCK(candidate_lock_name, 30) INTO candidate_lock_acquired;
    IF candidate_lock_acquired IS NULL OR candidate_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the W3g credit candidate menu lock';
    END IF;
    SELECT IS_USED_LOCK(candidate_lock_name) INTO lock_owner;
    IF lock_owner IS NULL OR lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu lock is not owned by this connection';
    END IF;

    SELECT COUNT(*) INTO required_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('sys_menu', 'sys_role_menu', 'u3w_schema_migration')
      AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB';
    IF required_table_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu requires canonical InnoDB menu tables';
    END IF;

    SELECT COUNT(*) INTO prerequisite_receipt_count
    FROM `u3w_schema_migration`
    WHERE (`version` = 'public_init_030'
           AND `description` = 'APPLIED:Independent Board administration menu')
       OR (`version` = '20260720_independent_board_admin_menu_v1'
           AND `description` = 'Independent Board administration directory, entitlement governance and meeting audit menus')
       OR (`version` = 'public_init_038'
           AND `description` = 'APPLIED:Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger')
       OR (`version` = '20260722_independent_board_credit_ledger_v1'
           AND `description` = 'Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger');
    IF prerequisite_receipt_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu prerequisite receipts are missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO prerequisite_admin_root_count, target_admin_root_id
    FROM `sys_menu`
    WHERE BINARY `menu_name` = BINARY '独董会管理'
      AND `parent_id` = 0
      AND `order_num` = 5
      AND BINARY `path` = BINARY 'independent-board-admin'
      AND `component` IS NULL
      AND `query` IS NULL
      AND BINARY `route_name` = BINARY 'IndependentBoardAdmin'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND BINARY `menu_type` = BINARY 'M'
      AND BINARY `visible` = BINARY '0'
      AND BINARY `status` = BINARY '0'
      AND BINARY `perms` = BINARY ''
      AND BINARY `icon` = BINARY 'peoples'
      AND BINARY `create_by` = BINARY 'admin'
      AND `create_time` IS NOT NULL
      AND BINARY `update_by` = BINARY ''
      AND `update_time` IS NULL
      AND BINARY `remark` = BINARY '福帮手 FBSir 独董会管理目录';
    IF prerequisite_admin_root_count <> 1 OR target_admin_root_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu admin root is missing or drifted';
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_credit_candidate_menu_v1';
    IF migration_exists NOT IN (0, 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu receipt identity is ambiguous';
    END IF;

    SELECT COUNT(*) INTO target_identity_count
    FROM `sys_menu`
    WHERE BINARY `menu_name` IN (BINARY '积分账本', BINARY '积分发放', BINARY '积分冲正')
       OR BINARY `path` = BINARY 'credit-ledger'
       OR BINARY `component` = BINARY 'business/independentBoard/admin/credit/index'
       OR BINARY `route_name` = BINARY 'IndependentBoardCreditGovernance'
       OR BINARY `perms` IN (
            BINARY 'board:credit:query',
            BINARY 'board:credit:grant',
            BINARY 'board:credit:reverse'
          );
    IF migration_exists = 0 AND target_identity_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu identity exists without its receipt';
    END IF;
    IF migration_exists = 1 AND target_identity_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu receipt exists but identity set is incomplete';
    END IF;

    IF migration_exists = 0 THEN
        SET first_apply = 1;
        START TRANSACTION;

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('积分账本', target_admin_root_id, 8, 'credit-ledger',
             'business/independentBoard/admin/credit/index', NULL,
             'IndependentBoardCreditGovernance', 1, 0, 'C', '0', '1',
             'board:credit:query', 'money', 'admin', CURRENT_TIMESTAMP, '', NULL,
             'W3g 默认禁用的 USER_GLOBAL FBS_POINTS 积分治理候选页');
        SET target_page_id = LAST_INSERT_ID();

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('积分发放', target_page_id, 1, '', NULL, NULL, '',
             1, 0, 'F', '0', '1', 'board:credit:grant', '#',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             'W3g 默认禁用的独董会积分受控发放权限');
        SET target_grant_id = LAST_INSERT_ID();

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('积分冲正', target_page_id, 2, '', NULL, NULL, '',
             1, 0, 'F', '0', '1', 'board:credit:reverse', '#',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             'W3g 默认禁用的独董会原发放积分冲正权限');
        SET target_reverse_id = LAST_INSERT_ID();

        IF target_page_id IS NULL OR target_page_id <= 0
           OR target_grant_id IS NULL OR target_grant_id <= 0
           OR target_reverse_id IS NULL OR target_reverse_id <= 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'W3g credit candidate menu generated identifiers are invalid';
        END IF;

        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260722_independent_board_credit_candidate_menu_v1',
            'Independent Board default-off credit governance menu and fine-grained permissions'
        );
        SET migration_exists = 1;
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_page_count, target_page_id
    FROM `sys_menu`
    WHERE BINARY `menu_name` = BINARY '积分账本'
      AND `parent_id` = target_admin_root_id
      AND `order_num` = 8
      AND BINARY `path` = BINARY 'credit-ledger'
      AND BINARY `component` = BINARY 'business/independentBoard/admin/credit/index'
      AND `query` IS NULL
      AND BINARY `route_name` = BINARY 'IndependentBoardCreditGovernance'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND BINARY `menu_type` = BINARY 'C'
      AND BINARY `visible` = BINARY '0'
      AND BINARY `status` = BINARY '1'
      AND BINARY `perms` = BINARY 'board:credit:query'
      AND BINARY `icon` = BINARY 'money'
      AND BINARY `create_by` = BINARY 'admin'
      AND `create_time` IS NOT NULL
      AND BINARY `update_by` = BINARY ''
      AND `update_time` IS NULL
      AND BINARY `remark` = BINARY 'W3g 默认禁用的 USER_GLOBAL FBS_POINTS 积分治理候选页';
    IF exact_page_count <> 1 OR target_page_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit governance page is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_grant_count, target_grant_id
    FROM `sys_menu`
    WHERE BINARY `menu_name` = BINARY '积分发放'
      AND `parent_id` = target_page_id
      AND `order_num` = 1
      AND BINARY `path` = BINARY ''
      AND `component` IS NULL
      AND `query` IS NULL
      AND BINARY `route_name` = BINARY ''
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND BINARY `menu_type` = BINARY 'F'
      AND BINARY `visible` = BINARY '0'
      AND BINARY `status` = BINARY '1'
      AND BINARY `perms` = BINARY 'board:credit:grant'
      AND BINARY `icon` = BINARY '#'
      AND BINARY `create_by` = BINARY 'admin'
      AND `create_time` IS NOT NULL
      AND BINARY `update_by` = BINARY ''
      AND `update_time` IS NULL
      AND BINARY `remark` = BINARY 'W3g 默认禁用的独董会积分受控发放权限';
    IF exact_grant_count <> 1 OR target_grant_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit grant permission is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_reverse_count, target_reverse_id
    FROM `sys_menu`
    WHERE BINARY `menu_name` = BINARY '积分冲正'
      AND `parent_id` = target_page_id
      AND `order_num` = 2
      AND BINARY `path` = BINARY ''
      AND `component` IS NULL
      AND `query` IS NULL
      AND BINARY `route_name` = BINARY ''
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND BINARY `menu_type` = BINARY 'F'
      AND BINARY `visible` = BINARY '0'
      AND BINARY `status` = BINARY '1'
      AND BINARY `perms` = BINARY 'board:credit:reverse'
      AND BINARY `icon` = BINARY '#'
      AND BINARY `create_by` = BINARY 'admin'
      AND `create_time` IS NOT NULL
      AND BINARY `update_by` = BINARY ''
      AND `update_time` IS NULL
      AND BINARY `remark` = BINARY 'W3g 默认禁用的独董会原发放积分冲正权限';
    IF exact_reverse_count <> 1 OR target_reverse_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit reversal permission is missing or drifted';
    END IF;

    SELECT COUNT(*) INTO target_identity_count
    FROM `sys_menu`
    WHERE BINARY `menu_name` IN (BINARY '积分账本', BINARY '积分发放', BINARY '积分冲正')
       OR BINARY `path` = BINARY 'credit-ledger'
       OR BINARY `component` = BINARY 'business/independentBoard/admin/credit/index'
       OR BINARY `route_name` = BINARY 'IndependentBoardCreditGovernance'
       OR BINARY `perms` IN (
            BINARY 'board:credit:query',
            BINARY 'board:credit:grant',
            BINARY 'board:credit:reverse'
          );
    IF target_identity_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu identity is ambiguous';
    END IF;

    SELECT COUNT(*) INTO target_child_count
    FROM `sys_menu`
    WHERE `parent_id` = target_page_id;
    IF target_child_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit page child set contains an unknown or missing identity';
    END IF;

    SELECT COUNT(*) INTO target_binding_count
    FROM `sys_role_menu`
    WHERE `menu_id` IN (target_page_id, target_grant_id, target_reverse_id);
    IF target_binding_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu must have zero role bindings';
    END IF;

    SELECT COUNT(*) INTO exact_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_credit_candidate_menu_v1'
      AND `description` = 'Independent Board default-off credit governance menu and fine-grained permissions';
    IF migration_exists <> 1 OR exact_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu receipt is missing or drifted';
    END IF;

    IF first_apply = 1 THEN
        COMMIT;
    END IF;

    SELECT IS_USED_LOCK(candidate_lock_name) INTO lock_owner;
    IF lock_owner IS NULL OR lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing W3g credit candidate lock release from another connection';
    END IF;
    SELECT RELEASE_LOCK(candidate_lock_name) INTO candidate_lock_acquired;
    IF candidate_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g credit candidate menu lock release failed';
    END IF;
    SET candidate_lock_acquired = 0;

    SELECT IS_USED_LOCK(public_lock_name) INTO lock_owner;
    IF lock_owner IS NULL OR lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing W3g public manifest lock release from another connection';
    END IF;
    SELECT RELEASE_LOCK(public_lock_name) INTO public_lock_acquired;
    IF public_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3g public manifest lock release failed';
    END IF;
    SET public_lock_acquired = 0;
END$$

CALL `u3w_migrate_independent_board_credit_candidate_menu_20260722`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_credit_candidate_menu_20260722`$$

DELIMITER ;
