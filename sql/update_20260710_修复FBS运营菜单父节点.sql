-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-07-10
-- 变更类型：菜单迁移修复
-- 变更内容：将 FBS运营管理 从错误的按钮父节点迁移到根菜单
-- 影响范围：sys_menu 表（UPDATE 操作）
-- 兼容性：幂等，可重复执行
-- ============================================================

UPDATE sys_menu
SET parent_id = 0,
    update_by = 'admin',
    update_time = SYSDATE()
WHERE menu_name = 'FBS运营管理'
  AND menu_type = 'M'
  AND parent_id <> 0;

-- 验证：结果应为 parent_id=0、menu_type=M。
-- SELECT menu_id, parent_id, menu_name, path, menu_type
-- FROM sys_menu
-- WHERE menu_name = 'FBS运营管理';
