-- ============================================================
-- FBSir Independent Board me.u3w.com entry
-- Migration: 20260720_independent_board_me_menu_v1
-- Target: MySQL 8
-- Scope: one top-level RuoYi page and the baseline ordinary-user role binding
--
-- Rollback is intentionally not automatic. A reviewed rollback must acquire
-- the same named lock and use one transaction to: resolve exactly one menu_id
-- from all immutable identity fields below; verify the exact role_id 10 binding
-- and the exact u3w_schema_migration receipt; delete only that binding; prove
-- no other role references the row; delete that exact menu_id; delete only the exact version+description receipt;
-- and prove all three objects absent before
-- COMMIT. Never roll back by Chinese name, LIKE pattern, a guessed numeric
-- menu_id, or by deleting the menu while leaving its receipt behind.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_me_menu_20260720`$$
CREATE PROCEDURE `u3w_migrate_independent_board_me_menu_20260720`()
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
    DECLARE exact_menu_count INT DEFAULT 0;
    DECLARE target_menu_id BIGINT DEFAULT NULL;
    DECLARE user_role_count INT DEFAULT 0;
    DECLARE target_user_role_id BIGINT DEFAULT NULL;
    DECLARE user_role_binding_count INT DEFAULT 0;

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
            SET MESSAGE_TEXT = 'Independent Board me menu migration requires an explicit target database';
    END IF;

    SELECT COUNT(*) INTO required_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('sys_menu', 'sys_role', 'sys_role_menu', 'u3w_schema_migration')
      AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB';
    IF required_table_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board me menu migration requires the canonical InnoDB RuoYi menu and migration tables';
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260720_independent_board_me_menu_v1'),
        256
    );
    IF CHAR_LENGTH(migration_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board me menu lock name must be a stable 64-character digest';
    END IF;
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the Independent Board me menu migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name), CONNECTION_ID()
      INTO migration_lock_owner, current_connection;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board me menu migration lock is not owned by the current connection';
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260720_independent_board_me_menu_v1';

    SELECT COUNT(*) INTO target_identity_count
    FROM `sys_menu`
    WHERE `path` = 'independent-board'
       OR `component` = 'business/independentBoard/me/index'
       OR `route_name` = 'IndependentBoardMe'
       OR `perms` = 'my:independent-board:view';

    IF migration_exists = 0 AND target_identity_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board me menu identity exists without its migration receipt; audit before continuing';
    END IF;

    SELECT COUNT(*), MIN(`role_id`)
      INTO user_role_count, target_user_role_id
    FROM `sys_role`
    WHERE `role_key` = 'user'
      AND `status` = '0'
      AND `del_flag` = '0';
    IF user_role_count <> 1 OR target_user_role_id <> 10 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board me menu requires active role_id 10 with role_key user, matching registration semantics';
    END IF;

    IF migration_exists = 0 THEN
        SET first_apply = 1;
        START TRANSACTION;

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('独董会', 0, 0, 'independent-board', 'business/independentBoard/me/index', NULL,
             'IndependentBoardMe', 1, 0, 'C', '0', '0', 'my:independent-board:view', 'peoples',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             '福帮手 FBSir 独董会用户后台：权益、额度与会议');

        SET target_menu_id = LAST_INSERT_ID();
        IF target_menu_id IS NULL OR target_menu_id <= 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Independent Board me menu did not receive a valid generated identifier';
        END IF;

        INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
        VALUES (target_user_role_id, target_menu_id);

        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260720_independent_board_me_menu_v1',
            'Independent Board top-level me portal menu and ordinary-user role binding'
        );

        SET migration_exists = 1;
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_menu_count, target_menu_id
    FROM `sys_menu`
    WHERE `menu_name` = '独董会'
      AND `parent_id` = 0
      AND `order_num` = 0
      AND `path` = 'independent-board'
      AND `component` = 'business/independentBoard/me/index'
      AND `query` IS NULL
      AND `route_name` = 'IndependentBoardMe'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND `menu_type` = 'C'
      AND `visible` = '0'
      AND `status` = '0'
      AND `perms` = 'my:independent-board:view'
      AND `icon` = 'peoples';
    IF exact_menu_count <> 1 OR target_menu_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board me menu current state is missing or drifted';
    END IF;

    SELECT COUNT(*) INTO target_identity_count
    FROM `sys_menu`
    WHERE `path` = 'independent-board'
       OR `component` = 'business/independentBoard/me/index'
       OR `route_name` = 'IndependentBoardMe'
       OR `perms` = 'my:independent-board:view';
    IF target_identity_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board me menu identity is ambiguous';
    END IF;

    SELECT COUNT(*) INTO user_role_binding_count
    FROM `sys_role_menu` AS role_menu
    INNER JOIN `sys_role` AS role_row ON role_row.`role_id` = role_menu.`role_id`
    WHERE role_menu.`menu_id` = target_menu_id
      AND role_row.`role_id` = target_user_role_id
      AND role_row.`role_key` = 'user'
      AND role_row.`status` = '0'
      AND role_row.`del_flag` = '0';
    IF user_role_binding_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board me menu ordinary-user role binding is missing or ambiguous';
    END IF;

    SELECT COUNT(*) INTO exact_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260720_independent_board_me_menu_v1'
      AND `description` = 'Independent Board top-level me portal menu and ordinary-user role binding';
    IF migration_exists <> 1 OR exact_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board me menu migration receipt is missing or drifted';
    END IF;

    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing Independent Board me menu lock release because the current session is not its owner';
    END IF;
    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_acquired;
    IF migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Independent Board me menu migration lock release failed';
    END IF;
    SET migration_lock_acquired = 0;

    IF first_apply = 1 THEN
        COMMIT;
    END IF;
END$$

CALL `u3w_migrate_independent_board_me_menu_20260720`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_me_menu_20260720`$$

DELIMITER ;
