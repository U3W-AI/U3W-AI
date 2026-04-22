-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-04-17
-- 变更类型：新增菜单记录
-- 变更内容：用户侧 API Key 管理菜单 + 按钮权限写入 sys_menu 表
-- 影响范围：sys_menu 表（INSERT 操作）
-- 回滚方式：执行 DELETE FROM sys_menu WHERE perms LIKE 'my:apikey%'
-- 兼容性：幂等操作（执行前请先清理旧数据）
-- 前置条件：需先执行 update_20260411_用户自助服务菜单.sql 创建'我的权益中心'父菜单
-- 注意：用户侧菜单需对所有登录用户可见，建议角色分配时将这些菜单分配给普通用户角色
-- ============================================================

DELETE FROM sys_menu WHERE menu_name IN ('我的 API Keys', '创建 Key', '禁用 Key', '删除 Key');

SET @my_fbs_parent_id = (SELECT menu_id FROM sys_menu WHERE menu_name = '我的权益中心' ORDER BY menu_id DESC LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('我的 API Keys', @my_fbs_parent_id, 31, 'myApikey', 'business/fbs/myApikey/index', 1, 'C', '0', '0', 'my:apikey:list', 'key', 'admin', SYSDATE(), '用户自助管理 API Key');

SET @my_apikey_menu_id = (SELECT menu_id FROM sys_menu WHERE menu_name = '我的 API Keys' AND parent_id = @my_fbs_parent_id ORDER BY menu_id DESC LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    ('创建 Key', @my_apikey_menu_id, 1, '', NULL, 1, 'F', '0', '0', 'my:apikey:create', '#', 'admin', SYSDATE(), ''),
    ('禁用 Key', @my_apikey_menu_id, 2, '', NULL, 1, 'F', '0', '0', 'my:apikey:toggle', '#', 'admin', SYSDATE(), ''),
    ('删除 Key', @my_apikey_menu_id, 3, '', NULL, 1, 'F', '0', '0', 'my:apikey:delete', '#', 'admin', SYSDATE(), '');
