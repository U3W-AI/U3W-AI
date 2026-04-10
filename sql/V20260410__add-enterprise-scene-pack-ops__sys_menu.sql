-- =============================================================
-- OpenSpec #3: add-enterprise-scene-pack-ops
-- FBS 企业侧菜单 + 按钮权限字符写入 sys_menu
-- 日期: 2026-04-10
--
-- 前置条件:
--   FBS 父菜单 'FBS运营管理' 必须已存在（由 OpenSpec #2 创建）
--   若未创建，请先执行 V20260409__add-platform-scene-pack-ops__sys_menu.sql
--
-- 执行前清理旧数据（可选，防止重复插入）:
--   DELETE FROM sys_menu WHERE menu_name IN (
--     '企业管理','企业详情','添加企业','编辑企业','禁用企业',
--     '企业场景包','企业包详情','分发企业包','回收企业包',
--     '企业成员','成员详情','添加成员','移除成员'
--   );
--
-- 权限标识对应后端 Controller（已校验）:
--   business:fbs:enterprise:list     FbsEnterpriseBusinessController#list
--   business:fbs:enterprise:query    FbsEnterpriseBusinessController#getDetail
--   business:fbs:enterprise:add      FbsEnterpriseBusinessController#create
--   business:fbs:enterprise:edit     FbsEnterpriseBusinessController#update
--   business:fbs:enterprise:disable  FbsEnterpriseBusinessController#disable
--   business:fbs:enterprisePack:list    FbsEnterprisePackBusinessController#list
--   business:fbs:enterprisePack:query   FbsEnterprisePackBusinessController#getDetail
--   business:fbs:enterprisePack:grant   FbsEnterprisePackBusinessController#grant
--   business:fbs:enterprisePack:revoke  FbsEnterprisePackBusinessController#revoke
--   business:fbs:enterpriseMember:list    FbsEnterpriseMemberBusinessController#list
--   business:fbs:enterpriseMember:query   FbsEnterpriseMemberBusinessController#getDetail
--   business:fbs:enterpriseMember:add     FbsEnterpriseMemberBusinessController#add
--   business:fbs:enterpriseMember:remove  FbsEnterpriseMemberBusinessController#remove
--
-- 验证:
--   SELECT menu_id, menu_name, parent_id, component, perms, menu_type
--   FROM sys_menu WHERE perms LIKE 'business:fbs:enterprise%'
--   ORDER BY menu_id;
-- =============================================================


-- =============================================================
-- 第一步: 获取 FBS 父菜单 ID
-- =============================================================
SET @fbs_parent_id = (
    SELECT menu_id FROM sys_menu
    WHERE menu_name = 'FBS运营管理'
    ORDER BY menu_id DESC LIMIT 1
);

-- 父菜单不存在时抛出错误，防止插入孤儿菜单
DROP PROCEDURE IF EXISTS _fbs_ent_check_parent;
DELIMITER $$
CREATE PROCEDURE _fbs_ent_check_parent()
BEGIN
    IF @fbs_parent_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'FBS父菜单(FBS运营管理)不存在，请先执行 OpenSpec #2 sys_menu 脚本';
    END IF;
END$$
DELIMITER ;
CALL _fbs_ent_check_parent();
DROP PROCEDURE IF EXISTS _fbs_ent_check_parent;


-- =============================================================
-- 4.1 企业管理（页面菜单，menu_type='C'）
-- 权限: business:fbs:enterprise:*
-- Component: business/fbs/enterprise/index
-- =============================================================
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '企业管理', @fbs_parent_id, 31, 'enterprise', 'business/fbs/enterprise/index',
    1, 'C', '0', '0', 'business:fbs:enterprise:list', 'peoples',
    'admin', SYSDATE(), 'FBS企业组织管理'
);

SET @ent_menu_id = (
    SELECT menu_id FROM sys_menu WHERE menu_name = '企业管理'
    ORDER BY menu_id DESC LIMIT 1
);

-- 企业管理 按钮权限（5个，含 query 详情）
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('企业列表', @ent_menu_id, 1, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterprise:list',    '#', 'admin', SYSDATE(), ''),
    ('企业详情', @ent_menu_id, 2, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterprise:query',   '#', 'admin', SYSDATE(), ''),
    ('添加企业', @ent_menu_id, 3, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterprise:add',     '#', 'admin', SYSDATE(), ''),
    ('编辑企业', @ent_menu_id, 4, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterprise:edit',    '#', 'admin', SYSDATE(), ''),
    ('禁用企业', @ent_menu_id, 5, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterprise:disable', '#', 'admin', SYSDATE(), '');


-- =============================================================
-- 4.2 企业场景包（页面菜单，menu_type='C'）
-- 权限: business:fbs:enterprisePack:*
-- Component: business/fbs/enterprise/pack/index
-- =============================================================
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '企业场景包', @fbs_parent_id, 32, 'enterprisePack', 'business/fbs/enterprise/pack/index',
    1, 'C', '0', '0', 'business:fbs:enterprisePack:list', 'lock',
    'admin', SYSDATE(), 'FBS企业场景包分发管理'
);

SET @entpack_menu_id = (
    SELECT menu_id FROM sys_menu WHERE menu_name = '企业场景包'
    ORDER BY menu_id DESC LIMIT 1
);

-- 企业场景包 按钮权限（4个，含 query 详情）
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('企业包列表', @entpack_menu_id, 1, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterprisePack:list',   '#', 'admin', SYSDATE(), ''),
    ('企业包详情', @entpack_menu_id, 2, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterprisePack:query',  '#', 'admin', SYSDATE(), ''),
    ('分发企业包', @entpack_menu_id, 3, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterprisePack:grant',  '#', 'admin', SYSDATE(), ''),
    ('回收企业包', @entpack_menu_id, 4, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterprisePack:revoke', '#', 'admin', SYSDATE(), '');


-- =============================================================
-- 4.3 企业成员（页面菜单，menu_type='C'）
-- 权限: business:fbs:enterpriseMember:*
-- Component: business/fbs/enterprise/member/index
-- =============================================================
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '企业成员', @fbs_parent_id, 33, 'enterpriseMember', 'business/fbs/enterprise/member/index',
    1, 'C', '0', '0', 'business:fbs:enterpriseMember:list', 'user',
    'admin', SYSDATE(), 'FBS企业成员管理'
);

SET @entmember_menu_id = (
    SELECT menu_id FROM sys_menu WHERE menu_name = '企业成员'
    ORDER BY menu_id DESC LIMIT 1
);

-- 企业成员 按钮权限（4个，含 query 详情）
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('成员列表', @entmember_menu_id, 1, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterpriseMember:list',   '#', 'admin', SYSDATE(), ''),
    ('成员详情', @entmember_menu_id, 2, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterpriseMember:query',  '#', 'admin', SYSDATE(), ''),
    ('添加成员', @entmember_menu_id, 3, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterpriseMember:add',    '#', 'admin', SYSDATE(), ''),
    ('移除成员', @entmember_menu_id, 4, '', NULL, 1, 'F', '0', '0', 'business:fbs:enterpriseMember:remove', '#', 'admin', SYSDATE(), '');
