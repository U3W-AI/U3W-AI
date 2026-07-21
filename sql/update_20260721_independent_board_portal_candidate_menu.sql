-- ============================================================
-- FBSir Independent Board W4b.2c default-off portal candidates
-- Migration: 20260721_independent_board_portal_candidate_menu_v1
-- Target: MySQL 8
-- Scope: four disabled candidate pages and one disabled tenant-query permission
--
-- This candidate migration is deliberately inert unless the caller sets the
-- following session variable in the same mysql connection before sourcing it:
--
--   SET @u3w_enable_independent_board_w4b2c_candidate = 1;
--
-- The opt-in only permits creation of status='1' rows. It does not activate a
-- menu, publish a route, open a held security page, or authorize a write action.
-- Activation requires a separate reviewed migration after the W4b.2c gates.
--
-- The me connector is bound only to the unique active `user` role. Because the
-- menu and permission queries both exclude status='1', that binding is inert.
-- The three admin pages and tenant-query permission receive no role bindings;
-- RuoYi's global system administrator may see them only after explicit future
-- activation. Delegated tenant administration is outside this migration.
--
-- Rollback is intentionally not automatic. A reviewed rollback must acquire
-- this named lock, resolve exactly five menu_ids from the immutable identities
-- below, prove the exact parent graph, default-disabled state, one me binding,
-- zero admin bindings, and exact receipt, then delete the binding, five rows,
-- and receipt in one transaction. Never roll back by display name, LIKE, or a
-- guessed numeric identifier.
--
-- All completion assertions run before COMMIT, and the named lock remains held
-- through that durable commit. A rerun performs a read-only current-state audit
-- and never repairs drift or an incomplete identity set.
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_portal_candidate_menu_20260721`$$
CREATE PROCEDURE `u3w_migrate_independent_board_portal_candidate_menu_20260721`()
BEGIN
    DECLARE migration_lock_name CHAR(64);
    DECLARE migration_lock_acquired INT DEFAULT 0;
    DECLARE migration_lock_owner BIGINT DEFAULT NULL;
    DECLARE current_connection BIGINT DEFAULT 0;
    DECLARE required_table_count INT DEFAULT 0;
    DECLARE prerequisite_receipt_count INT DEFAULT 0;
    DECLARE prerequisite_me_count INT DEFAULT 0;
    DECLARE prerequisite_admin_root_count INT DEFAULT 0;
    DECLARE prerequisite_me_binding_count INT DEFAULT 0;
    DECLARE migration_exists INT DEFAULT 0;
    DECLARE first_apply INT DEFAULT 0;
    DECLARE target_identity_count INT DEFAULT 0;
    DECLARE forbidden_identity_count INT DEFAULT 0;
    DECLARE exact_receipt_count INT DEFAULT 0;
    DECLARE user_role_count INT DEFAULT 0;
    DECLARE target_user_role_id BIGINT DEFAULT NULL;
    DECLARE target_me_menu_id BIGINT DEFAULT NULL;
    DECLARE target_admin_root_id BIGINT DEFAULT NULL;
    DECLARE target_connector_id BIGINT DEFAULT NULL;
    DECLARE target_oauth_client_id BIGINT DEFAULT NULL;
    DECLARE target_oauth_family_id BIGINT DEFAULT NULL;
    DECLARE target_connector_binding_id BIGINT DEFAULT NULL;
    DECLARE target_tenant_query_id BIGINT DEFAULT NULL;
    DECLARE exact_connector_count INT DEFAULT 0;
    DECLARE exact_oauth_client_count INT DEFAULT 0;
    DECLARE exact_oauth_family_count INT DEFAULT 0;
    DECLARE exact_connector_binding_count INT DEFAULT 0;
    DECLARE exact_tenant_query_count INT DEFAULT 0;
    DECLARE connector_binding_count INT DEFAULT 0;
    DECLARE connector_user_binding_count INT DEFAULT 0;
    DECLARE admin_binding_count INT DEFAULT 0;

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

    IF COALESCE(@u3w_enable_independent_board_w4b2c_candidate, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu migration requires explicit session opt-in';
    END IF;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu migration requires an explicit target database';
    END IF;

    SELECT COUNT(*) INTO required_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('sys_menu', 'sys_role', 'sys_role_menu', 'u3w_schema_migration')
      AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB';
    IF required_table_count <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu migration requires canonical InnoDB menu tables';
    END IF;

    SET migration_lock_name = SHA2(
        CONCAT(DATABASE(), ':20260721_independent_board_portal_candidate_menu_v1'),
        256
    );
    IF CHAR_LENGTH(migration_lock_name) <> 64 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu lock name must be a stable digest';
    END IF;
    SELECT GET_LOCK(migration_lock_name, 30) INTO migration_lock_acquired;
    IF migration_lock_acquired IS NULL OR migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Could not acquire the W4b.2c candidate menu migration lock';
    END IF;
    SELECT IS_USED_LOCK(migration_lock_name), CONNECTION_ID()
      INTO migration_lock_owner, current_connection;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> current_connection THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu lock is not owned by this connection';
    END IF;

    SELECT COUNT(*) INTO prerequisite_receipt_count
    FROM `u3w_schema_migration`
    WHERE (`version` = '20260720_independent_board_me_menu_v1'
           AND `description` = 'Independent Board top-level me portal menu and ordinary-user role binding')
       OR (`version` = '20260720_independent_board_admin_menu_v1'
           AND `description` = 'Independent Board administration directory, entitlement governance and meeting audit menus')
       OR (`version` = '20260720_independent_board_entitlement_lifecycle_menu_v1'
           AND `description` = 'Independent Board controlled entitlement revoke permission and immutable receipt audit menu');
    IF prerequisite_receipt_count <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu prerequisite receipts are missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO prerequisite_me_count, target_me_menu_id
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
    IF prerequisite_me_count <> 1 OR target_me_menu_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu me prerequisite is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO prerequisite_admin_root_count, target_admin_root_id
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
    IF prerequisite_admin_root_count <> 1 OR target_admin_root_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu admin prerequisite is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`role_id`)
      INTO user_role_count, target_user_role_id
    FROM `sys_role`
    WHERE `role_key` = 'user'
      AND `status` = '0'
      AND `del_flag` = '0';
    IF user_role_count <> 1 OR target_user_role_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu requires one unique active user role';
    END IF;

    SELECT COUNT(*) INTO prerequisite_me_binding_count
    FROM `sys_role_menu`
    WHERE `role_id` = target_user_role_id
      AND `menu_id` = target_me_menu_id;
    IF prerequisite_me_binding_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu me role prerequisite is missing or ambiguous';
    END IF;

    SELECT COUNT(*) INTO migration_exists
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_portal_candidate_menu_v1';
    IF migration_exists NOT IN (0, 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu receipt identity is ambiguous';
    END IF;

    SELECT COUNT(*) INTO target_identity_count
    FROM `sys_menu`
    WHERE `path` IN ('independent-board-connector', 'oauth-clients', 'oauth-families', 'connector-bindings')
       OR `component` IN (
            'business/independentBoard/me/connector/index',
            'business/independentBoard/admin/oauth-client/index',
            'business/independentBoard/admin/oauth-family/index',
            'business/independentBoard/admin/connector-binding/index'
          )
       OR `route_name` IN (
            'IndependentBoardConnectorCandidate',
            'IndependentBoardOAuthClientCandidate',
            'IndependentBoardOAuthFamilyCandidate',
            'IndependentBoardConnectorBindingCandidate'
          )
       OR `perms` IN (
            'my:independent-board:connector:view',
            'board:oauth:client:query',
            'board:oauth:family:query',
            'board:connector:query',
            'board:tenant:query'
          );
    IF migration_exists = 0 AND target_identity_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu identity exists without its receipt';
    END IF;
    IF migration_exists = 1 AND target_identity_count <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu receipt exists but identity set is incomplete';
    END IF;

    SELECT COUNT(*) INTO forbidden_identity_count
    FROM `sys_menu`
    WHERE `route_name` IN (
            'IndependentBoardSecurityCandidate',
            'IndependentBoardSecurityEventCandidate'
          )
       OR `perms` IN (
            'my:independent-board:security:view',
            'board:oauth:security:audit',
            'my:independent-board:connector:authorize',
            'my:independent-board:connector:revoke',
            'board:oauth:family:revoke',
            'board:connector:revoke'
          );
    IF forbidden_identity_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c held security or write menu identity must remain absent';
    END IF;

    IF migration_exists = 0 THEN
        SET first_apply = 1;
        START TRANSACTION;

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('连接与授权', 0, 1, 'independent-board-connector',
             'business/independentBoard/me/connector/index', NULL,
             'IndependentBoardConnectorCandidate', 1, 0, 'C', '0', '1',
             'my:independent-board:connector:view', 'link',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             'W4b.2c 默认禁用的独董会连接状态只读候选页');
        SET target_connector_id = LAST_INSERT_ID();

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('OAuth 客户端', target_admin_root_id, 4, 'oauth-clients',
             'business/independentBoard/admin/oauth-client/index', NULL,
             'IndependentBoardOAuthClientCandidate', 1, 0, 'C', '0', '1',
             'board:oauth:client:query', 'documentation',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             'W4b.2c 默认禁用的 OAuth 客户端安全投影候选页');
        SET target_oauth_client_id = LAST_INSERT_ID();

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('Token Family', target_admin_root_id, 5, 'oauth-families',
             'business/independentBoard/admin/oauth-family/index', NULL,
             'IndependentBoardOAuthFamilyCandidate', 1, 0, 'C', '0', '1',
             'board:oauth:family:query', 'tree-table',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             'W4b.2c 默认禁用的 Token Family 安全投影候选页');
        SET target_oauth_family_id = LAST_INSERT_ID();

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('Connector Binding', target_admin_root_id, 6, 'connector-bindings',
             'business/independentBoard/admin/connector-binding/index', NULL,
             'IndependentBoardConnectorBindingCandidate', 1, 0, 'C', '0', '1',
             'board:connector:query', 'link',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             'W4b.2c 默认禁用的 Connector Binding 安全投影候选页');
        SET target_connector_binding_id = LAST_INSERT_ID();

        INSERT INTO `sys_menu`
            (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
             `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
             `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
        VALUES
            ('企业检索', target_admin_root_id, 7, '', NULL, NULL, '',
             1, 0, 'F', '0', '1', 'board:tenant:query', '#',
             'admin', CURRENT_TIMESTAMP, '', NULL,
             'W4b.2c 默认禁用的全局系统管理员企业检索权限');
        SET target_tenant_query_id = LAST_INSERT_ID();

        IF target_connector_id IS NULL OR target_connector_id <= 0
           OR target_oauth_client_id IS NULL OR target_oauth_client_id <= 0
           OR target_oauth_family_id IS NULL OR target_oauth_family_id <= 0
           OR target_connector_binding_id IS NULL OR target_connector_binding_id <= 0
           OR target_tenant_query_id IS NULL OR target_tenant_query_id <= 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'W4b.2c candidate menu generated identifiers are invalid';
        END IF;

        INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
        VALUES (target_user_role_id, target_connector_id);

        INSERT INTO `u3w_schema_migration` (`version`, `description`)
        VALUES (
            '20260721_independent_board_portal_candidate_menu_v1',
            'Independent Board default-off portal candidate menus and tenant-query permission'
        );
        SET migration_exists = 1;
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_connector_count, target_connector_id
    FROM `sys_menu`
    WHERE `menu_name` = '连接与授权'
      AND `parent_id` = 0
      AND `order_num` = 1
      AND `path` = 'independent-board-connector'
      AND `component` = 'business/independentBoard/me/connector/index'
      AND `query` IS NULL
      AND `route_name` = 'IndependentBoardConnectorCandidate'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND `menu_type` = 'C'
      AND `visible` = '0'
      AND `status` = '1'
      AND `perms` = 'my:independent-board:connector:view'
      AND `icon` = 'link';
    IF exact_connector_count <> 1 OR target_connector_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c connector candidate is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_oauth_client_count, target_oauth_client_id
    FROM `sys_menu`
    WHERE `menu_name` = 'OAuth 客户端'
      AND `parent_id` = target_admin_root_id
      AND `order_num` = 4
      AND `path` = 'oauth-clients'
      AND `component` = 'business/independentBoard/admin/oauth-client/index'
      AND `query` IS NULL
      AND `route_name` = 'IndependentBoardOAuthClientCandidate'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND `menu_type` = 'C'
      AND `visible` = '0'
      AND `status` = '1'
      AND `perms` = 'board:oauth:client:query'
      AND `icon` = 'documentation';
    IF exact_oauth_client_count <> 1 OR target_oauth_client_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c OAuth client candidate is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_oauth_family_count, target_oauth_family_id
    FROM `sys_menu`
    WHERE `menu_name` = 'Token Family'
      AND `parent_id` = target_admin_root_id
      AND `order_num` = 5
      AND `path` = 'oauth-families'
      AND `component` = 'business/independentBoard/admin/oauth-family/index'
      AND `query` IS NULL
      AND `route_name` = 'IndependentBoardOAuthFamilyCandidate'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND `menu_type` = 'C'
      AND `visible` = '0'
      AND `status` = '1'
      AND `perms` = 'board:oauth:family:query'
      AND `icon` = 'tree-table';
    IF exact_oauth_family_count <> 1 OR target_oauth_family_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c OAuth family candidate is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_connector_binding_count, target_connector_binding_id
    FROM `sys_menu`
    WHERE `menu_name` = 'Connector Binding'
      AND `parent_id` = target_admin_root_id
      AND `order_num` = 6
      AND `path` = 'connector-bindings'
      AND `component` = 'business/independentBoard/admin/connector-binding/index'
      AND `query` IS NULL
      AND `route_name` = 'IndependentBoardConnectorBindingCandidate'
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND `menu_type` = 'C'
      AND `visible` = '0'
      AND `status` = '1'
      AND `perms` = 'board:connector:query'
      AND `icon` = 'link';
    IF exact_connector_binding_count <> 1 OR target_connector_binding_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c connector binding candidate is missing or drifted';
    END IF;

    SELECT COUNT(*), MIN(`menu_id`)
      INTO exact_tenant_query_count, target_tenant_query_id
    FROM `sys_menu`
    WHERE `menu_name` = '企业检索'
      AND `parent_id` = target_admin_root_id
      AND `order_num` = 7
      AND `path` = ''
      AND `component` IS NULL
      AND `query` IS NULL
      AND `route_name` = ''
      AND `is_frame` = 1
      AND `is_cache` = 0
      AND `menu_type` = 'F'
      AND `visible` = '0'
      AND `status` = '1'
      AND `perms` = 'board:tenant:query'
      AND `icon` = '#';
    IF exact_tenant_query_count <> 1 OR target_tenant_query_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c tenant-query permission is missing or drifted';
    END IF;

    SELECT COUNT(*) INTO target_identity_count
    FROM `sys_menu`
    WHERE `path` IN ('independent-board-connector', 'oauth-clients', 'oauth-families', 'connector-bindings')
       OR `component` IN (
            'business/independentBoard/me/connector/index',
            'business/independentBoard/admin/oauth-client/index',
            'business/independentBoard/admin/oauth-family/index',
            'business/independentBoard/admin/connector-binding/index'
          )
       OR `route_name` IN (
            'IndependentBoardConnectorCandidate',
            'IndependentBoardOAuthClientCandidate',
            'IndependentBoardOAuthFamilyCandidate',
            'IndependentBoardConnectorBindingCandidate'
          )
       OR `perms` IN (
            'my:independent-board:connector:view',
            'board:oauth:client:query',
            'board:oauth:family:query',
            'board:connector:query',
            'board:tenant:query'
          );
    IF target_identity_count <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu identity is ambiguous';
    END IF;

    SELECT COUNT(*) INTO connector_binding_count
    FROM `sys_role_menu`
    WHERE `menu_id` = target_connector_id;
    SELECT COUNT(*) INTO connector_user_binding_count
    FROM `sys_role_menu` AS role_menu
    INNER JOIN `sys_role` AS role_row ON role_row.`role_id` = role_menu.`role_id`
    WHERE role_menu.`menu_id` = target_connector_id
      AND role_row.`role_id` = target_user_role_id
      AND role_row.`role_key` = 'user'
      AND role_row.`status` = '0'
      AND role_row.`del_flag` = '0';
    IF connector_binding_count <> 1 OR connector_user_binding_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c connector candidate role binding is missing or ambiguous';
    END IF;

    SELECT COUNT(*) INTO admin_binding_count
    FROM `sys_role_menu`
    WHERE `menu_id` IN (
        target_oauth_client_id,
        target_oauth_family_id,
        target_connector_binding_id,
        target_tenant_query_id
    );
    IF admin_binding_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c admin candidate menus must have zero role bindings';
    END IF;

    SELECT COUNT(*) INTO forbidden_identity_count
    FROM `sys_menu`
    WHERE `route_name` IN (
            'IndependentBoardSecurityCandidate',
            'IndependentBoardSecurityEventCandidate'
          )
       OR `perms` IN (
            'my:independent-board:security:view',
            'board:oauth:security:audit',
            'my:independent-board:connector:authorize',
            'my:independent-board:connector:revoke',
            'board:oauth:family:revoke',
            'board:connector:revoke'
          );
    IF forbidden_identity_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c held security or write menu identity must remain absent';
    END IF;

    SELECT COUNT(*) INTO exact_receipt_count
    FROM `u3w_schema_migration`
    WHERE `version` = '20260721_independent_board_portal_candidate_menu_v1'
      AND `description` = 'Independent Board default-off portal candidate menus and tenant-query permission';
    IF migration_exists <> 1 OR exact_receipt_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu migration receipt is missing or drifted';
    END IF;

    IF first_apply = 1 THEN
        COMMIT;
    END IF;

    SELECT IS_USED_LOCK(migration_lock_name) INTO migration_lock_owner;
    IF migration_lock_owner IS NULL OR migration_lock_owner <> CONNECTION_ID() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing W4b.2c candidate menu lock release from another connection';
    END IF;
    SELECT RELEASE_LOCK(migration_lock_name) INTO migration_lock_acquired;
    IF migration_lock_acquired <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'W4b.2c candidate menu migration lock release failed';
    END IF;
    SET migration_lock_acquired = 0;
END$$

CALL `u3w_migrate_independent_board_portal_candidate_menu_20260721`()$$
DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_portal_candidate_menu_20260721`$$

DELIMITER ;
