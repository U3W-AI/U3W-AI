-- ============================================================
-- FBSir Independent Board administration menu
-- Migration: 20260720_independent_board_admin_menu_v1
-- Target: MySQL 8
-- Scope: one administration directory, two pages and one action permission
--
-- This migration intentionally creates no sys_role_menu rows. RuoYi's
-- canonical user_id=1 super-administrator sees all active M/C menus and owns
-- the wildcard permission. Delegated administration must be introduced by a
-- separately reviewed role-and-controller change that resolves roles by their
-- semantic role_key rather than a guessed numeric identifier.
--
-- Rollback is intentionally not automatic. A reviewed rollback invoked from
-- the public initializer must acquire the public manifest lock first and then
-- this migration's named lock. In one transaction it must resolve exactly four
-- menu_ids from the immutable path/component/route_name/perms identities below,
-- prove the exact parent/child graph and receipts, refuse unknown children or
-- any sys_role_menu references, delete the grant leaf, both pages and then the
-- directory, and delete only the exact internal receipt. The outer
-- public_init_030 receipt may be removed only when its exact APPLIED description
-- is present and no later applied manifest step depends on it. Never roll back
-- by Chinese display name, LIKE pattern or a guessed numeric menu_id.
--
-- All completion assertions run before COMMIT, and the named lock remains held
-- through that durable commit. If ownership verification or RELEASE_LOCK fails
-- after COMMIT, the internal receipt and menu tree remain applied. Re-executing
-- this SQL uses that receipt to perform a read-only completion-state audit; an
-- outer public-manifest FAILED state must still be reconciled by an operator and
-- must never be cleared or replayed automatically.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_admin_menu_20260720`$$
CREATE PROCEDURE `u3w_migrate_independent_board_admin_menu_20260720`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE current_connection BIGINT DEFAULT 0;
    DECLARE required_table_count INT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE first_apply INT DEFAULT 0;
    DECLARE exact_receipt_count INT DEFAULT 0;
    DECLARE target_identity_count INT DEFAULT 0;
    DECLARE exact_root_count INT DEFAULT 0;
    DECLARE exact_entitlement_count INT DEFAULT 0;
    DECLARE exact_audit_count INT DEFAULT 0;
    DECLARE exact_grant_count INT DEFAULT 0;
    DECLARE target_root_id BIGINT DEFAULT NULL;
    DECLARE target_entitlement_id BIGINT DEFAULT NULL;
    DECLARE target_audit_id BIGINT DEFAULT NULL;
    DECLARE target_grant_id BIGINT DEFAULT NULL;

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
            SET MESSAGE_TEXT = 'Independent Board admin menu migration requires an explicit target database';
    END IF;

    SELECT COUNT(*) INTO required_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('sys_menu', 'sys_role_menu', 'u3w_schema_migration')
      AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB';
    IF required_table_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board admin menu migration requires the canonical InnoDB RuoYi menu and migration tables';
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260720_independent_board_admin_menu_v1'),
        256
    );
    IF CHAR_LENGTH(migration_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board admin menu lock name must be a stable 64-character digest';
    END IF;
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the Independent Board admin menu migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name), CONNECTION_ID()
      INTO migration_lock_owner, current_connection;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board admin menu migration lock is not owned by the current connection';
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260720_independent_board_admin_menu_v1';
    IF migration_exists NOT IN (0, 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board admin menu migration receipt identity is ambiguous';
    END IF;

    SELECT COUNT(*) INTO target_identity_count
    FROM `sys_menu`
    WHERE `path` = 'independent-board-admin'
       OR `route_name` = 'IndependentBoardAdmin'
       OR `component` = 'business/independentBoard/admin/entitlement/index'
       OR `route_name` = 'IndependentBoardEntitlementGovernance'
       OR `perms` = 'board:entitlement:query'
       OR `component` = 'business/independentBoard/admin/meetingAudit/index'
       OR `route_name` = 'IndependentBoardMeetingAudit'
       OR `perms` = 'board:operation:audit'
       OR `perms` = 'board:entitlement:grant';

    IF migration_exists = 0 AND target_identity_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board admin menu identity exists without its migration receipt; audit before continuing';
    END IF;
    IF migration_exists = 1 AND target_identity_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board admin menu receipt exists but its identity set is incomplete or ambiguous';
    END IF;

    IF migration_exists = 0 THEN
        SET first_apply = 1;
        START TRANSACTION;

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('独董会管理', 0, 5, 'independent-board-admin', NULL, NULL,
             'IndependentBoardAdmin', 1, 0, 'M', '0', '0', '', 'peoples',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             '福帮手 FBSir 独董会管理目录');
        SET target_root_id = LAST_INSERT_ID();
        IF target_root_id IS NULL OR target_root_id <= 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Independent Board admin directory did not receive a valid generated identifier';
        END IF;

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('权益治理', target_root_id, 1, 'entitlements',
             'business/independentBoard/admin/entitlement/index', NULL,
             'IndependentBoardEntitlementGovernance', 1, 0, 'C', '0', '0',
             'board:entitlement:query', 'peoples', 'admin', CURRENT_TIMESTAMP, '', NULL,
             '独董会产品权益查询与治理');
        SET target_entitlement_id = LAST_INSERT_ID();
        IF target_entitlement_id IS NULL OR target_entitlement_id <= 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Independent Board entitlement page did not receive a valid generated identifier';
        END IF;

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('会议审计', target_root_id, 2, 'meeting-audit',
             'business/independentBoard/admin/meetingAudit/index', NULL,
             'IndependentBoardMeetingAudit', 1, 0, 'C', '0', '0',
             'board:operation:audit', 'form', 'admin', CURRENT_TIMESTAMP, '', NULL,
             '独董会会议额度预留操作审计');
        SET target_audit_id = LAST_INSERT_ID();
        IF target_audit_id IS NULL OR target_audit_id <= 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Independent Board meeting audit page did not receive a valid generated identifier';
        END IF;

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('权益授予', target_entitlement_id, 1, '', NULL, NULL, '',
             1, 0, 'F', '0', '0', 'board:entitlement:grant', '#',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             '受控授予独董会产品权益');
        SET target_grant_id = LAST_INSERT_ID();
        IF target_grant_id IS NULL OR target_grant_id <= 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Independent Board entitlement grant permission did not receive a valid generated identifier';
        END IF;

        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260720_independent_board_admin_menu_v1',
            'Independent Board administration directory, entitlement governance and meeting audit menus'
        );
        SET migration_exists = 1;
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_root_count, target_root_id
    FROM `sys_menu`
    WHERE `menu_name` = '独董会管理'
      AND `parent_id` = 0
      AND `order_num` = 5
      AND `path` = 'independent-board-admin'
      AND `component` IS NULL
      AND `query` IS NULL
      AND `route_name` = 'IndependentBoardAdmin'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND `menu_type` = 'M'
      AND `visible` = '0'
      AND `status` = '0'
      AND `perms` = ''
      AND `icon` = 'peoples';
    IF exact_root_count <> 1 OR target_root_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board admin directory current state is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_entitlement_count, target_entitlement_id
    FROM `sys_menu`
    WHERE `menu_name` = '权益治理'
      AND `parent_id` = target_root_id
      AND `order_num` = 1
      AND `path` = 'entitlements'
      AND `component` = 'business/independentBoard/admin/entitlement/index'
      AND `query` IS NULL
      AND `route_name` = 'IndependentBoardEntitlementGovernance'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND `menu_type` = 'C'
      AND `visible` = '0'
      AND `status` = '0'
      AND `perms` = 'board:entitlement:query'
      AND `icon` = 'peoples';
    IF exact_entitlement_count <> 1 OR target_entitlement_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board entitlement governance menu current state is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_audit_count, target_audit_id
    FROM `sys_menu`
    WHERE `menu_name` = '会议审计'
      AND `parent_id` = target_root_id
      AND `order_num` = 2
      AND `path` = 'meeting-audit'
      AND `component` = 'business/independentBoard/admin/meetingAudit/index'
      AND `query` IS NULL
      AND `route_name` = 'IndependentBoardMeetingAudit'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND `menu_type` = 'C'
      AND `visible` = '0'
      AND `status` = '0'
      AND `perms` = 'board:operation:audit'
      AND `icon` = 'form';
    IF exact_audit_count <> 1 OR target_audit_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board meeting audit menu current state is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_grant_count, target_grant_id
    FROM `sys_menu`
    WHERE `menu_name` = '权益授予'
      AND `parent_id` = target_entitlement_id
      AND `order_num` = 1
      AND `path` = ''
      AND `component` IS NULL
      AND `query` IS NULL
      AND `route_name` = ''
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND `menu_type` = 'F'
      AND `visible` = '0'
      AND `status` = '0'
      AND `perms` = 'board:entitlement:grant'
      AND `icon` = '#';
    IF exact_grant_count <> 1 OR target_grant_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board entitlement grant permission current state is missing or drifted';
    END IF;

    SELECT COUNT(*) INTO target_identity_count
    FROM `sys_menu`
    WHERE `path` = 'independent-board-admin'
       OR `route_name` = 'IndependentBoardAdmin'
       OR `component` = 'business/independentBoard/admin/entitlement/index'
       OR `route_name` = 'IndependentBoardEntitlementGovernance'
       OR `perms` = 'board:entitlement:query'
       OR `component` = 'business/independentBoard/admin/meetingAudit/index'
       OR `route_name` = 'IndependentBoardMeetingAudit'
       OR `perms` = 'board:operation:audit'
       OR `perms` = 'board:entitlement:grant';
    IF target_identity_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board admin menu identity is ambiguous';
    END IF;

    SELECT COUNT(*) INTO exact_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260720_independent_board_admin_menu_v1'
      AND `description` = 'Independent Board administration directory, entitlement governance and meeting audit menus';
    IF migration_exists <> 1 OR exact_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board admin menu migration receipt is missing or drifted';
    END IF;

    IF first_apply = 1 THEN
        COMMIT;
    END IF;

    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing Independent Board admin menu lock release because the current session is not its owner';
    END IF;
    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_acquired;
    IF migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board admin menu migration lock release failed';
    END IF;
    SET migration_lock_acquired = 0;
END$$

CALL `u3w_migrate_independent_board_admin_menu_20260720`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_admin_menu_20260720`$$

DELIMITER ;
