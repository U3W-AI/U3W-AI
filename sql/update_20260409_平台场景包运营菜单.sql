-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-04-09
-- 变更类型：新增菜单记录
-- 变更内容：FBS平台侧运营管理菜单 + 按钮权限写入 sys_menu 表
-- 影响范围：sys_menu 表（INSERT 操作）
-- 回滚方式：执行 DELETE FROM sys_menu WHERE menu_name IN ('FBS运营管理','场景包管理'...)
-- 兼容性：幂等操作（执行前请先清理旧数据，见下方 SQL）
-- ============================================================

-- ============================================================
-- 执行前请先删除旧菜单数据（防止重复插入）
-- ============================================================
DELETE FROM sys_menu WHERE menu_name IN (
    'FBS运营管理','场景包管理','场景包新增','场景包编辑','场景包发布',
    '场景包下架','场景包查询','授权码管理','授权码生成','授权码禁用',
    '授权码启用','授权码撤销','用户权益查询','用户权益详情'
);

-- ============================================================
-- 第一步：插入父菜单 - FBS运营管理
-- ============================================================
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

-- ============================================================
-- 第二步：插入子菜单与按钮权限
-- 使用变量保存父菜单 ID，避免硬编码
-- ============================================================
SET @fbs_parent_id = (
    SELECT menu_id FROM sys_menu
    WHERE menu_name = 'FBS运营管理'
    ORDER BY menu_id DESC LIMIT 1
);

-- ---- 场景包管理 菜单（C 类型页面） ----
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

-- ---- 场景包管理 按钮权限（F 类型按钮） ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('场景包新增', @fbs_parent_id, 2, '', NULL, 1, 'F', '0', '0', 'business:fbs:scenePack:add',     '#', 'admin', SYSDATE(), ''),
    ('场景包编辑', @fbs_parent_id, 3, '', NULL, 1, 'F', '0', '0', 'business:fbs:scenePack:edit',    '#', 'admin', SYSDATE(), ''),
    ('场景包发布', @fbs_parent_id, 4, '', NULL, 1, 'F', '0', '0', 'business:fbs:scenePack:publish', '#', 'admin', SYSDATE(), ''),
    ('场景包下架', @fbs_parent_id, 5, '', NULL, 1, 'F', '0', '0', 'business:fbs:scenePack:unpublish', '#', 'admin', SYSDATE(), ''),
    ('场景包查询', @fbs_parent_id, 6, '', NULL, 1, 'F', '0', '0', 'business:fbs:scenePack:query',   '#', 'admin', SYSDATE(), '');

-- ---- 授权码管理 菜单（C 类型页面） ----
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

-- ---- 授权码管理 按钮权限（F 类型按钮） ----
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

-- ---- 用户权益查询 菜单（C 类型页面） ----
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

-- ---- 用户权益查询 按钮权限（F 类型按钮） ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('用户权益详情', @fbs_parent_id, 22, '', NULL, 1, 'F', '0', '0', 'business:fbs:userPack:query', '#', 'admin', SYSDATE(), '');

-- ============================================================
-- 验证 SQL（执行后可删除）
-- ============================================================
-- SELECT menu_id, menu_name, parent_id, component, perms, menu_type
-- FROM sys_menu
-- WHERE perms LIKE 'business:fbs%' OR menu_name LIKE 'FBS%'
-- ORDER BY menu_id;
