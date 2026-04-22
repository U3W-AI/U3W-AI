-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-04-11
-- 变更类型：新增菜单记录
-- 变更内容：用户侧自助权益菜单 + 按钮权限写入 sys_menu 表
-- 影响范围：sys_menu 表（INSERT 操作）
-- 回滚方式：执行 DELETE FROM sys_menu WHERE perms LIKE 'my:fbs%'
-- 兼容性：幂等操作（执行前请先清理旧数据）
-- 注意：用户侧菜单需对所有登录用户可见，建议角色分配时将这些菜单分配给普通用户角色
-- ============================================================

DELETE FROM sys_menu WHERE menu_name IN ('我的权益中心','我的权益','激活授权码','可领取场景包','我的权益查询','授权码激活','场景包领取');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('我的权益中心', 0, 20, 'myFbs', NULL, 1, 'M', '0', '0', '', 'peoples', 'admin', SYSDATE(), 'FBS用户自助权益菜单');

SET @my_fbs_parent_id = (SELECT menu_id FROM sys_menu WHERE menu_name = '我的权益中心' ORDER BY menu_id DESC LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('我的权益', @my_fbs_parent_id, 1, 'myPacks', 'business/fbs/myPacks/index', 1, 'C', '0', '0', 'my:fbs:packs:list', 'list', 'admin', SYSDATE(), '用户自助查看自己的权益列表');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('我的权益查询', @my_fbs_parent_id, 2, '', NULL, 1, 'F', '0', '0', 'my:fbs:packs:query', '#', 'admin', SYSDATE(), '');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('激活授权码', @my_fbs_parent_id, 11, 'activateAuthCode', 'business/fbs/activateAuthCode/index', 1, 'C', '0', '0', 'my:fbs:authCode:activate', 'key', 'admin', SYSDATE(), '用户自助激活授权码');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('可领取场景包', @my_fbs_parent_id, 21, 'claimablePacks', 'business/fbs/claimablePacks/index', 1, 'C', '0', '0', 'my:fbs:scenePack:claimable', 'download', 'admin', SYSDATE(), '用户自助领取免费场景包');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('场景包领取', @my_fbs_parent_id, 22, '', NULL, 1, 'F', '0', '0', 'my:fbs:scenePack:claim', '#', 'admin', SYSDATE(), '');
