-- =============================================================
-- OpenSpec #4: add-user-self-service
-- 用户侧自助权益菜单 + 按钮权限字符写入 sys_menu
-- 日期: 2026-04-11
--
-- 注意: 用户侧菜单需对所有登录用户可见，建议角色分配时
--       将这些菜单分配给普通用户角色
--
-- 执行前请先删除旧菜单数据:
--   DELETE FROM sys_menu WHERE menu_name IN ('我的权益中心','我的权益','激活授权码','可领取场景包','我的权益查询','授权码激活','场景包领取');
-- =============================================================


-- =============================================================
-- 第一步: 插入父菜单
-- =============================================================
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '我的权益中心', 0, 20, 'myFbs', NULL,
    1, 'M', '0', '0', '', 'peoples',
    'admin', SYSDATE(), 'FBS用户自助权益菜单'
);


-- =============================================================
-- 第二步: 插入子菜单与按钮权限
-- =============================================================

SET @my_fbs_parent_id = (SELECT menu_id FROM sys_menu WHERE menu_name = '我的权益中心' ORDER BY menu_id DESC LIMIT 1);


-- ---- 我的权益 菜单 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '我的权益', @my_fbs_parent_id, 1, 'myPacks', 'business/fbs/myPacks/index',
    1, 'C', '0', '0', 'my:fbs:packs:list', 'list',
    'admin', SYSDATE(), '用户自助查看自己的权益列表'
);

-- ---- 我的权益 按钮权限 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('我的权益查询', @my_fbs_parent_id, 2, '', NULL, 1, 'F', '0', '0', 'my:fbs:packs:query', '#', 'admin', SYSDATE(), '');


-- ---- 激活授权码 菜单 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '激活授权码', @my_fbs_parent_id, 11, 'activateAuthCode', 'business/fbs/activateAuthCode/index',
    1, 'C', '0', '0', 'my:fbs:authCode:activate', 'key',
    'admin', SYSDATE(), '用户自助激活授权码'
);


-- ---- 可领取场景包 菜单 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '可领取场景包', @my_fbs_parent_id, 21, 'claimablePacks', 'business/fbs/claimablePacks/index',
    1, 'C', '0', '0', 'my:fbs:scenePack:claimable', 'download',
    'admin', SYSDATE(), '用户自助领取免费场景包'
);

-- ---- 可领取场景包 按钮权限 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('场景包领取', @my_fbs_parent_id, 22, '', NULL, 1, 'F', '0', '0', 'my:fbs:scenePack:claim', '#', 'admin', SYSDATE(), '');


-- =============================================================
-- 验证:
-- SELECT menu_id, menu_name, parent_id, component, perms, menu_type
-- FROM sys_menu
-- WHERE perms LIKE 'my:fbs%' OR menu_name LIKE '我的%'
-- ORDER BY menu_id;
-- =============================================================
