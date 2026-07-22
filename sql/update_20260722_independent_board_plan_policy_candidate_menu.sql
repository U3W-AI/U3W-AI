-- ============================================================
-- FBSir Independent Board W3h default-off plan-policy permissions
-- Migration: 20260722_independent_board_plan_policy_candidate_menu_v1
-- Target: MySQL 8
-- Scope: two disabled F identities under the existing entitlement page
--
-- This manual candidate migration is inert unless the caller sets, in the
-- same mysql session before sourcing this file:
--
--   SET @u3w_enable_independent_board_w3h_plan_policy_candidate = 1;
--
-- The migration never creates a route, activates a permission, or grants a
-- role. Replays are read-only audits and never repair drift, unknown plan
-- children, role bindings, collisions, or a changed receipt.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_board_plan_policy_menu_20260722`$$
CREATE PROCEDURE `u3w_migrate_board_plan_policy_menu_20260722`()
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
    DECLARE prerequisite_entitlement_page_count INT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE exact_receipt_count INT DEFAULT 0;
    DECLARE first_apply INT DEFAULT 0;
    DECLARE target_identity_count INT DEFAULT 0;
    DECLARE target_child_count INT DEFAULT 0;
    DECLARE target_binding_count INT DEFAULT 0;
    DECLARE exact_revise_count INT DEFAULT 0;
    DECLARE exact_audit_count INT DEFAULT 0;
    DECLARE target_admin_root_id BIGINT DEFAULT NULL;
    DECLARE target_entitlement_page_id BIGINT DEFAULT NULL;
    DECLARE target_revise_id BIGINT DEFAULT NULL;
    DECLARE target_audit_id BIGINT DEFAULT NULL;

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

    IF COALESCE(@u3w_enable_independent_board_w3h_plan_policy_candidate, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu migration requires explicit session opt-in';
    END IF;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu migration requires an explicit target database';
    END IF;

    SET public_lock_name = SHA2(
        CONCAT(DATABASE(), ':public-database-manifest:v1'), 256
    );
    SET candidate_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260722_independent_board_plan_policy_candidate_menu_v1'), 256
    );
    IF CHAR_LENGTH(public_lock_name) <> 64 OR CHAR_LENGTH(candidate_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu lock names must be stable digests';
    END IF;

    SELECT GET_LOCK(public_lock_name, 30) INTO public_lock_acquired;
    IF public_lock_acquired IS NULL OR public_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the public manifest lock for W3h plan-policy menu';
    END IF;
    SELECT IS_USED_LOCK(public_lock_name), CONNECTION_ID()
      INTO lock_owner, current_connection;
    IF lock_owner IS NULL OR lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy menu public manifest lock is not owned by this connection';
    END IF;

    SELECT GET_LOCK(candidate_lock_name, 30) INTO candidate_lock_acquired;
    IF candidate_lock_acquired IS NULL OR candidate_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the W3h plan-policy candidate menu lock';
    END IF;
    SELECT IS_USED_LOCK(candidate_lock_name) INTO lock_owner;
    IF lock_owner IS NULL OR lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu lock is not owned by this connection';
    END IF;

    SELECT COUNT(*) INTO required_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('sys_menu', 'sys_role_menu', 'u3w_schema_migration')
      AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB';
    IF required_table_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu requires canonical InnoDB menu tables';
    END IF;

    SELECT COUNT(*) INTO prerequisite_receipt_count
    FROM `u3w_schema_migration`
    WHERE (`version` = 'public_init_030'
           AND `description` = 'APPLIED:Independent Board administration menu')
       OR (`version` = '20260720_independent_board_admin_menu_v1'
           AND `description` = 'Independent Board administration directory, entitlement governance and meeting audit menus')
       OR (`version` = 'public_init_039'
           AND `description` = 'APPLIED:Independent Board immutable plan policy revisions and operation lineage')
       OR (`version` = '20260722_independent_board_plan_policy_v1'
           AND `description` = 'Independent Board immutable plan policy revisions and operation lineage');
    IF prerequisite_receipt_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu prerequisite receipts are missing or drifted';
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
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu admin root is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO prerequisite_entitlement_page_count, target_entitlement_page_id
    FROM `sys_menu`
    WHERE BINARY `menu_name` = BINARY '权益治理'
      AND `parent_id` = target_admin_root_id
      AND `order_num` = 1
      AND BINARY `path` = BINARY 'entitlements'
      AND BINARY `component` = BINARY 'business/independentBoard/admin/entitlement/index'
      AND `query` IS NULL
      AND BINARY `route_name` = BINARY 'IndependentBoardEntitlementGovernance'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND BINARY `menu_type` = BINARY 'C'
      AND BINARY `visible` = BINARY '0'
      AND BINARY `status` = BINARY '0'
      AND BINARY `perms` = BINARY 'board:entitlement:query'
      AND BINARY `icon` = BINARY 'peoples'
      AND BINARY `create_by` = BINARY 'admin'
      AND `create_time` IS NOT NULL
      AND BINARY `update_by` = BINARY ''
      AND `update_time` IS NULL
      AND BINARY `remark` = BINARY '独董会产品权益查询与治理';
    IF prerequisite_entitlement_page_count <> 1 OR target_entitlement_page_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu entitlement parent is missing or drifted';
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_plan_policy_candidate_menu_v1';
    IF migration_exists NOT IN (0, 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu receipt identity is ambiguous';
    END IF;

    SELECT COUNT(*) INTO target_identity_count
    FROM `sys_menu`
    WHERE BINARY `menu_name` IN (BINARY '套餐策略修订', BINARY '套餐策略审计')
       OR BINARY `perms` IN (BINARY 'board:plan:revise', BINARY 'board:plan:audit');
    IF migration_exists = 0 AND target_identity_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu identity exists without its receipt';
    END IF;
    IF migration_exists = 1 AND target_identity_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu receipt exists but identity set is incomplete';
    END IF;

    IF migration_exists = 0 THEN
        SET first_apply = 1;
        START TRANSACTION;

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('套餐策略修订', target_entitlement_page_id, 3, '', NULL, NULL, '',
             1, 0, 'F', '0', '1', 'board:plan:revise', '#',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             'W3h 默认禁用的独董会套餐策略完整替换修订权限');
        SET target_revise_id = LAST_INSERT_ID();

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('套餐策略审计', target_entitlement_page_id, 4, '', NULL, NULL, '',
             1, 0, 'F', '0', '1', 'board:plan:audit', '#',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             'W3h 默认禁用的独董会套餐策略不可变回执审计权限');
        SET target_audit_id = LAST_INSERT_ID();

        IF target_revise_id IS NULL OR target_revise_id <= 0
           OR target_audit_id IS NULL OR target_audit_id <= 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'W3h plan-policy candidate menu generated identifiers are invalid';
        END IF;

        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260722_independent_board_plan_policy_candidate_menu_v1',
            'Independent Board default-off plan-policy revision and audit permissions'
        );
        SET migration_exists = 1;
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_revise_count, target_revise_id
    FROM `sys_menu`
    WHERE BINARY `menu_name` = BINARY '套餐策略修订'
      AND `parent_id` = target_entitlement_page_id
      AND `order_num` = 3
      AND BINARY `path` = BINARY ''
      AND `component` IS NULL
      AND `query` IS NULL
      AND BINARY `route_name` = BINARY ''
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND BINARY `menu_type` = BINARY 'F'
      AND BINARY `visible` = BINARY '0'
      AND BINARY `status` = BINARY '1'
      AND BINARY `perms` = BINARY 'board:plan:revise'
      AND BINARY `icon` = BINARY '#'
      AND BINARY `create_by` = BINARY 'admin'
      AND `create_time` IS NOT NULL
      AND BINARY `update_by` = BINARY ''
      AND `update_time` IS NULL
      AND BINARY `remark` = BINARY 'W3h 默认禁用的独董会套餐策略完整替换修订权限';
    IF exact_revise_count <> 1 OR target_revise_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan revision permission is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_audit_count, target_audit_id
    FROM `sys_menu`
    WHERE BINARY `menu_name` = BINARY '套餐策略审计'
      AND `parent_id` = target_entitlement_page_id
      AND `order_num` = 4
      AND BINARY `path` = BINARY ''
      AND `component` IS NULL
      AND `query` IS NULL
      AND BINARY `route_name` = BINARY ''
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND BINARY `menu_type` = BINARY 'F'
      AND BINARY `visible` = BINARY '0'
      AND BINARY `status` = BINARY '1'
      AND BINARY `perms` = BINARY 'board:plan:audit'
      AND BINARY `icon` = BINARY '#'
      AND BINARY `create_by` = BINARY 'admin'
      AND `create_time` IS NOT NULL
      AND BINARY `update_by` = BINARY ''
      AND `update_time` IS NULL
      AND BINARY `remark` = BINARY 'W3h 默认禁用的独董会套餐策略不可变回执审计权限';
    IF exact_audit_count <> 1 OR target_audit_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan audit permission is missing or drifted';
    END IF;

    SELECT COUNT(*) INTO target_identity_count
    FROM `sys_menu`
    WHERE BINARY `menu_name` IN (BINARY '套餐策略修订', BINARY '套餐策略审计')
       OR BINARY `perms` IN (BINARY 'board:plan:revise', BINARY 'board:plan:audit');
    IF target_identity_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu identity is ambiguous';
    END IF;

    SELECT COUNT(*) INTO target_child_count
    FROM `sys_menu`
    WHERE `parent_id` = target_entitlement_page_id
      AND (BINARY `menu_name` IN (BINARY '套餐策略修订', BINARY '套餐策略审计')
           OR BINARY COALESCE(`perms`, '') LIKE BINARY 'board:plan:%');
    IF target_child_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy child subset contains an unknown or missing identity';
    END IF;

    SELECT COUNT(*) INTO target_binding_count
    FROM `sys_role_menu`
    WHERE `menu_id` IN (target_revise_id, target_audit_id);
    IF target_binding_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate permissions must have zero role bindings';
    END IF;

    SELECT COUNT(*) INTO exact_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260722_independent_board_plan_policy_candidate_menu_v1'
      AND `description` = 'Independent Board default-off plan-policy revision and audit permissions';
    IF migration_exists <> 1 OR exact_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu receipt is missing or drifted';
    END IF;

    IF first_apply = 1 THEN
        COMMIT;
    END IF;

    SELECT IS_USED_LOCK(candidate_lock_name) INTO lock_owner;
    IF lock_owner IS NULL OR lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing W3h plan-policy candidate lock release from another connection';
    END IF;
    SELECT RELEASE_LOCK(candidate_lock_name) INTO candidate_lock_acquired;
    IF candidate_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h plan-policy candidate menu lock release failed';
    END IF;
    SET candidate_lock_acquired = 0;

    SELECT IS_USED_LOCK(public_lock_name) INTO lock_owner;
    IF lock_owner IS NULL OR lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing W3h public manifest lock release from another connection';
    END IF;
    SELECT RELEASE_LOCK(public_lock_name) INTO public_lock_acquired;
    IF public_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W3h public manifest lock release failed';
    END IF;
    SET public_lock_acquired = 0;
END$$

CALL `u3w_migrate_board_plan_policy_menu_20260722`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_board_plan_policy_menu_20260722`$$

DELIMITER ;
