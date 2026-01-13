-- ============================================
-- 知识库配置SQL脚本
-- 执行方式：在数据库中执行此SQL脚本
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
-- 菜单ID: 129（根据注释，120-199为内容管理预留）
-- 路径: business/content/knowledge/knowledge
-- 权限标识: business:knowledge:view
INSERT INTO sys_menu VALUES('129', '知识库', '1', '3', 'knowledge', 'business/content/knowledge/knowledge', '', '', 1, 0, 'C', '0', '0', 'business:knowledge:view', 'guide', 'admin', sysdate(), '', null, '知识库管理菜单');

-- 2. 添加知识库按钮权限（parent_id=127）
-- 按钮权限ID: 1101+（根据注释，1101+为预留）
INSERT INTO sys_menu VALUES('1101', '知识库查询', '129', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:query', '#', 'admin', sysdate(), '', null, '查询知识库列表');
INSERT INTO sys_menu VALUES('1102', '知识库新增', '129', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:add', '#', 'admin', sysdate(), '', null, '新增知识库');
INSERT INTO sys_menu VALUES('1103', '知识库修改', '129', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:edit', '#', 'admin', sysdate(), '', null, '修改知识库');
INSERT INTO sys_menu VALUES('1104', '知识库删除', '129', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:remove', '#', 'admin', sysdate(), '', null, '删除知识库');
INSERT INTO sys_menu VALUES('1105', '知识库上传', '129', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:upload', '#', 'admin', sysdate(), '', null, '上传知识库到元器/企微机器人');
INSERT INTO sys_menu VALUES('1106', '空间管理', '129', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:space', '#', 'admin', sysdate(), '', null, '知识库空间管理');

-- 3. 为管理员角色（role_id=2）分配知识库菜单权限
INSERT INTO sys_role_menu VALUES ('2', '129');  -- 知识库菜单
INSERT INTO sys_role_menu VALUES ('2', '1101'); -- 知识库查询
INSERT INTO sys_role_menu VALUES ('2', '1102'); -- 知识库新增
INSERT INTO sys_role_menu VALUES ('2', '1103'); -- 知识库修改
INSERT INTO sys_role_menu VALUES ('2', '1104'); -- 知识库删除
INSERT INTO sys_role_menu VALUES ('2', '1105'); -- 知识库上传
INSERT INTO sys_role_menu VALUES ('2', '1106'); -- 空间管理

-- 4. 为普通角色（role_id=10）分配知识库查看权限（可根据实际需求调整）
INSERT INTO sys_role_menu VALUES ('10', '129');  -- 知识库菜单
INSERT INTO sys_role_menu VALUES ('10', '1101'); -- 知识库查询
INSERT INTO sys_role_menu VALUES ('10', '1102'); -- 知识库新增（允许普通用户创建自己的知识库）



