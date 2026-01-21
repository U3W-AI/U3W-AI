-- ============================================
-- 知识库配置SQL脚本
-- 执行方式：在数据库中执行此SQL脚本
-- ============================================
-- 
-- ⚠️ 功能状态：内测功能（暂不可用）
-- 
-- 原因说明：
-- 由于Engine模块中的循环依赖问题已被移除，导致以下功能不完善：
-- 1. JiQiRenLoginUtil - 企业微信机器人登录功能受影响
-- 2. YuanQiKnowledgeController - 元器知识库配置功能受影响
-- 
-- 这些功能需要进一步完善后才能正式上线。
-- 当前菜单权限已配置，但建议在前端隐藏或禁用相关功能入口。
-- ============================================
-- 添加扩展的用户字段
ALTER TABLE `sys_user`
    ADD COLUMN `kb_space_quota` BIGINT DEFAULT 0 COMMENT '知识库空间额度（按字符长度计算，1024MB为默认额度）' AFTER `points`,
    ADD COLUMN `kb_likes_ids` VARCHAR(2000) DEFAULT '' COMMENT '收藏的知识库ID，多个用逗号分隔（如1,2,3）' AFTER `kb_space_quota`,
    ADD COLUMN `kb_space_include_kb_ids` VARCHAR(2000) DEFAULT '' COMMENT '空间内包含的知识库ID，多个用逗号分隔（如1,2,3）' AFTER `kb_likes_ids`,
    ADD COLUMN `has_knowledge_base` VARCHAR(2000) DEFAULT '' COMMENT '用户自己创建的知识库id' AFTER `kb_space_include_kb_ids`,
    ADD COLUMN `is_super` TINYINT(1) DEFAULT 0 COMMENT '是否为超级账户：0-普通账户，1-超级账户' AFTER `has_knowledge_base`,
    ADD COLUMN `is_open_account_perm` TINYINT(1) DEFAULT 0 COMMENT '账户权限管理权限是否开放：0-关闭，1-开放' AFTER `is_super`,
    ADD COLUMN `is_open_module_perm` TINYINT(1) DEFAULT 0 COMMENT '模块功能操作权限是否开放：0-关闭，1-开放' AFTER `is_open_account_perm`;
-- 添加知识库表
CREATE TABLE `kb_base` (
                           `kb_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '知识库ID（自增且唯一）',
                           `kb_name` VARCHAR(100) NOT NULL COMMENT '知识库名字',
                           `kb_content` LONGTEXT COMMENT '知识库内容（JSON格式数据）',
                           `is_public_template` TINYINT(1) DEFAULT 0 COMMENT '是否为公共模板：0-私有，1-公共',
                           PRIMARY KEY (`kb_id`),
                           KEY `idx_is_public_template` (`is_public_template`) COMMENT '公共模板筛选索引'
) ENGINE=InnoDB COMMENT='知识库表';
UPDATE sys_user SET is_super = 1 WHERE user_name = 'admin';
-- 1. 添加知识库主菜单（二级菜单，parent_id=1，内容管理下）
-- 菜单ID: 150（避免与认证申请菜单133-137冲突）
-- 路径: business/content/knowledge/knowledge
-- 权限标识: business:knowledge:view

-- 知识库菜单 - 标记为内测功能（visible=1表示显示，暂改为0隐藏）
-- 当Engine模块功能完善后，将visible改为1即可启用
INSERT INTO sys_menu VALUES('150', '知识库', '1', '7', 'knowledge', 'business/content/knowledge/knowledge', '', '', 0, 0, 'C', '0', '0', 'business:knowledge:view', 'guide', 'admin', sysdate(), '', null, '【内测功能】知识库管理菜单 - 暂不可用');

-- 2. 添加知识库按钮权限（parent_id=150）
-- 按钮权限ID: 1137-1142（避免与认证申请按钮权限1104-1131冲突）
-- 注意：这些权限已配置但菜单已隐藏，当功能完善后可启用
INSERT INTO sys_menu VALUES('1137', '【内测】知识库查询', '150', '1', '#', '', '', '', 0, 0, 'F', '0', '0', 'business:knowledge:query', '#', 'admin', sysdate(), '', null, '【内测功能】查询知识库列表 - 暂不可用');
INSERT INTO sys_menu VALUES('1138', '【内测】知识库新增', '150', '2', '#', '', '', '', 0, 0, 'F', '0', '0', 'business:knowledge:add', '#', 'admin', sysdate(), '', null, '【内测功能】新增知识库 - 暂不可用');
INSERT INTO sys_menu VALUES('1139', '【内测】知识库修改', '150', '3', '#', '', '', '', 0, 0, 'F', '0', '0', 'business:knowledge:edit', '#', 'admin', sysdate(), '', null, '【内测功能】修改知识库 - 暂不可用');
INSERT INTO sys_menu VALUES('1140', '【内测】知识库删除', '150', '4', '#', '', '', '', 0, 0, 'F', '0', '0', 'business:knowledge:remove', '#', 'admin', sysdate(), '', null, '【内测功能】删除知识库 - 暂不可用');
INSERT INTO sys_menu VALUES('1141', '【内测】知识库上传', '150', '5', '#', '', '', '', 0, 0, 'F', '0', '0', 'business:knowledge:upload', '#', 'admin', sysdate(), '', null, '【内测功能】上传知识库到元器/企微机器人 - 暂不可用');
INSERT INTO sys_menu VALUES('1142', '【内测】空间管理', '150', '6', '#', '', '', '', 0, 0, 'F', '0', '0', 'business:knowledge:space', '#', 'admin', sysdate(), '', null, '【内测功能】知识库空间管理 - 暂不可用');

-- 3. 为管理员角色（role_id=2）分配知识库菜单权限
-- 注意：权限已配置，但菜单已隐藏（visible=0），前端不会显示
-- 当功能完善后，修改菜单visible字段为1即可启用
INSERT INTO sys_role_menu VALUES ('2', '150');  -- 知识库菜单（内测）
INSERT INTO sys_role_menu VALUES ('2', '1137'); -- 知识库查询（内测）
INSERT INTO sys_role_menu VALUES ('2', '1138'); -- 知识库新增（内测）
INSERT INTO sys_role_menu VALUES ('2', '1139'); -- 知识库修改（内测）
INSERT INTO sys_role_menu VALUES ('2', '1140'); -- 知识库删除（内测）
INSERT INTO sys_role_menu VALUES ('2', '1141'); -- 知识库上传（内测）
INSERT INTO sys_role_menu VALUES ('2', '1142'); -- 空间管理（内测）

-- 4. 为普通角色（role_id=10）分配知识库查看权限（可根据实际需求调整）
-- 注意：权限已配置，但菜单已隐藏（visible=0），前端不会显示
INSERT INTO sys_role_menu VALUES ('10', '150');  -- 知识库菜单（内测）
INSERT INTO sys_role_menu VALUES ('10', '1137'); -- 知识库查询（内测）
INSERT INTO sys_role_menu VALUES ('10', '1138'); -- 知识库新增（内测，允许普通用户创建自己的知识库）



