-- =============================================================
-- OpenSpec #2: add-platform-scene-pack-ops
-- FBS运营管理菜单 + 按钮权限字符写入 sys_menu
-- 日期: 2026-04-09
--
-- 执行前请先删除旧菜单数据:
--   DELETE FROM sys_menu WHERE menu_name IN ('FBS运营管理','场景包管理','场景包新增','场景包编辑','场景包发布','场景包下架','场景包查询','授权码管理','授权码生成','授权码禁用','授权码启用','授权码撤销','用户权益查询','用户权益详情');
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
    'FBS运营管理', 1000, 10, 'fbs', NULL,
    1, 'M', '0', '0', '', 'config',
    'admin', SYSDATE(), 'FBS场景包与授权码运营菜单'
);


-- =============================================================
-- 第二步: 插入子菜单与按钮权限
-- 用变量保存父菜单 ID，避免硬编码
-- =============================================================

-- 获取刚插入的父菜单 ID（手动替换下面的值）
-- SELECT menu_id INTO @fbs_parent_id FROM sys_menu WHERE menu_name = 'FBS运营管理' ORDER BY menu_id DESC LIMIT 1;
SET @fbs_parent_id = (SELECT menu_id FROM sys_menu WHERE menu_name = 'FBS运营管理' ORDER BY menu_id DESC LIMIT 1);


-- ---- 场景包管理 菜单 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '场景包管理', @fbs_parent_id, 1, 'scenePack', 'business/fbs/scenePack/index',
    1, 'C', '0', '0', 'business:fbs:scenePack:list', 'list',
    'admin', SYSDATE(), 'FBS场景包管理'
);

-- ---- 场景包 按钮权限 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('场景包新增', @fbs_parent_id, 2, '', NULL, 1, 'F', '0', '0', 'business:fbs:scenePack:add',       '#', 'admin', SYSDATE(), ''),
    ('场景包编辑', @fbs_parent_id, 3, '', NULL, 1, 'F', '0', '0', 'business:fbs:scenePack:edit',      '#', 'admin', SYSDATE(), ''),
    ('场景包发布', @fbs_parent_id, 4, '', NULL, 1, 'F', '0', '0', 'business:fbs:scenePack:publish',   '#', 'admin', SYSDATE(), ''),
    ('场景包下架', @fbs_parent_id, 5, '', NULL, 1, 'F', '0', '0', 'business:fbs:scenePack:unpublish', '#', 'admin', SYSDATE(), ''),
    ('场景包查询', @fbs_parent_id, 6, '', NULL, 1, 'F', '0', '0', 'business:fbs:scenePack:query',     '#', 'admin', SYSDATE(), '');


-- ---- 授权码管理 菜单 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '授权码管理', @fbs_parent_id, 11, 'authCode', 'business/fbs/authCode/index',
    1, 'C', '0', '0', 'business:fbs:authCode:list', 'lock',
    'admin', SYSDATE(), 'FBS授权码管理'
);

-- ---- 授权码 按钮权限 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('授权码生成', @fbs_parent_id, 12, '', NULL, 1, 'F', '0', '0', 'business:fbs:authCode:generate', '#', 'admin', SYSDATE(), ''),
    ('授权码禁用', @fbs_parent_id, 13, '', NULL, 1, 'F', '0', '0', 'business:fbs:authCode:disable',  '#', 'admin', SYSDATE(), ''),
    ('授权码启用', @fbs_parent_id, 14, '', NULL, 1, 'F', '0', '0', 'business:fbs:authCode:enable',   '#', 'admin', SYSDATE(), ''),
    ('授权码撤销', @fbs_parent_id, 15, '', NULL, 1, 'F', '0', '0', 'business:fbs:authCode:revoke',   '#', 'admin', SYSDATE(), '');


-- ---- 用户权益查询 菜单 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '用户权益查询', @fbs_parent_id, 21, 'userPack', 'business/fbs/userPack/index',
    1, 'C', '0', '0', 'business:fbs:userPack:list', 'user',
    'admin', SYSDATE(), 'FBS用户权益查询'
);

-- ---- 用户权益 按钮权限 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('用户权益详情', @fbs_parent_id, 22, '', NULL, 1, 'F', '0', '0', 'business:fbs:userPack:query', '#', 'admin', SYSDATE(), '');


-- =============================================================
-- 验证:
-- SELECT menu_id, menu_name, parent_id, component, perms, menu_type
-- FROM sys_menu
-- WHERE perms LIKE 'business:fbs%' OR menu_name LIKE 'FBS%'
-- ORDER BY menu_id;
-- =============================================================
