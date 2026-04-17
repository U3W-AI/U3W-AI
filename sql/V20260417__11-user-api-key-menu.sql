-- =============================================================
-- OpenSpec #11: frontend-api-key-management
-- 用户侧 API Key 管理菜单 + 按钮权限写入 sys_menu
-- 日期: 2026-04-17
--
-- 注意: 用户侧菜单需对所有登录用户可见，建议角色分配时
--       将这些菜单分配给普通用户角色
--
-- 执行前请先删除旧菜单数据:
--   DELETE FROM sys_menu WHERE menu_name IN ('我的 API Keys', '创建 Key', '禁用 Key', '删除 Key');
-- =============================================================


-- =============================================================
-- 第一步: 获取父菜单ID
-- =============================================================
SET @my_fbs_parent_id = (SELECT menu_id FROM sys_menu WHERE menu_name = '我的权益中心' ORDER BY menu_id DESC LIMIT 1);


-- =============================================================
-- 第二步: 插入菜单 + 按钮权限
-- =============================================================

-- ---- 我的 API Keys 菜单 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES (
    '我的 API Keys', @my_fbs_parent_id, 31, 'myApikey', 'business/fbs/myApikey/index',
    1, 'C', '0', '0', 'my:apikey:list', 'key',
    'admin', SYSDATE(), '用户自助管理 API Key'
);

-- ---- 获取刚插入的菜单ID ----
SET @my_apikey_menu_id = (SELECT menu_id FROM sys_menu WHERE menu_name = '我的 API Keys' AND parent_id = @my_fbs_parent_id ORDER BY menu_id DESC LIMIT 1);

-- ---- 按钮权限 ----
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component,
    is_frame, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
VALUES
    ('创建 Key', @my_apikey_menu_id, 1, '', NULL, 1, 'F', '0', '0', 'my:apikey:create', '#', 'admin', SYSDATE(), ''),
    ('禁用 Key', @my_apikey_menu_id, 2, '', NULL, 1, 'F', '0', '0', 'my:apikey:toggle', '#', 'admin', SYSDATE(), ''),
    ('删除 Key', @my_apikey_menu_id, 3, '', NULL, 1, 'F', '0', '0', 'my:apikey:delete', '#', 'admin', SYSDATE(), '');


-- =============================================================
-- 验证:
-- SELECT menu_id, menu_name, parent_id, component, perms, menu_type
-- FROM sys_menu
-- WHERE perms LIKE 'my:apikey%' OR menu_name = '我的 API Keys'
-- ORDER BY menu_id;
-- =============================================================
