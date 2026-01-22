-- ============================================
-- 知识库配置SQL脚本
-- 执行方式：在数据库中执行此SQL脚本
-- ============================================
-- 
-- ✅ 功能状态：生产可用
-- 
-- 架构优化说明：
-- 1. 采用扩展表设计模式，避免 sys_user 主表字段膨胀
-- 2. 使用 user_id 外键关联，支持一对一查询
-- 3. 扩展表仅存储知识库相关业务字段，遵循单一职责原则
-- 4. 支持企业微信机器人和腾讯元器平台的知识库同步功能
-- ============================================

-- ----------------------------
-- 用户扩展表（知识库相关字段）
-- 设计理念：分离业务扩展字段，避免核心用户表臃肿
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_extend`;
CREATE TABLE `sys_user_extend` (
    `user_id` BIGINT(20) NOT NULL COMMENT '用户ID（关联 sys_user.user_id）',
    
    -- 知识库空间管理
    `kb_space_quota` BIGINT DEFAULT 1024 COMMENT '知识库空间额度（MB，默认1GB）',
    `kb_space_used` BIGINT DEFAULT 0 COMMENT '已使用空间（MB）',
    
    -- 知识库关联
    `kb_space_include_kb_ids` VARCHAR(2000) DEFAULT '' COMMENT '空间内包含的知识库ID，逗号分隔（如1,2,3）',
    `has_knowledge_base` VARCHAR(2000) DEFAULT '' COMMENT '用户自己创建的知识库ID，逗号分隔',
    `kb_likes_ids` VARCHAR(2000) DEFAULT '' COMMENT '收藏的知识库ID，逗号分隔',
    
    -- 权限控制
    `is_super` TINYINT(1) DEFAULT 0 COMMENT '是否为超级账户：0-普通，1-超级',
    `is_open_account_perm` TINYINT(1) DEFAULT 0 COMMENT '账户权限管理：0-关闭，1-开放',
    `is_open_module_perm` TINYINT(1) DEFAULT 0 COMMENT '模块功能操作权限：0-关闭，1-开放',
    
    -- 时间戳
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_user_extend_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户扩展表-知识库相关';

-- 索引优化
CREATE INDEX `idx_is_super` ON `sys_user_extend`(`is_super`);
CREATE INDEX `idx_kb_space_quota` ON `sys_user_extend`(`kb_space_quota`);
-- ----------------------------
-- 知识库主表
-- ----------------------------
DROP TABLE IF EXISTS `kb_base`;
CREATE TABLE `kb_base` (
    `kb_id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '知识库ID（自增且唯一）',
    `kb_name` VARCHAR(100) NOT NULL COMMENT '知识库名称',
    `kb_content` LONGTEXT COMMENT '知识库内容（JSON格式）',
    `is_public_template` TINYINT(1) DEFAULT 0 COMMENT '是否为公共模板：0-私有，1-公共',
    `creator_id` BIGINT(20) COMMENT '创建者用户ID',
    `kb_size` BIGINT DEFAULT 0 COMMENT '知识库大小（MB）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`kb_id`),
    KEY `idx_is_public_template` (`is_public_template`),
    KEY `idx_creator_id` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库主表';

-- 初始化 admin 用户的扩展信息（超级管理员）
INSERT INTO `sys_user_extend` (`user_id`, `kb_space_quota`, `is_super`, `is_open_account_perm`, `is_open_module_perm`)
SELECT `user_id`, 10240, 1, 1, 1 FROM `sys_user` WHERE `user_name` = 'admin'
ON DUPLICATE KEY UPDATE 
    `is_super` = 1, 
    `is_open_account_perm` = 1, 
    `is_open_module_perm` = 1,
    `kb_space_quota` = 10240;
-- ----------------------------
-- 1. 添加知识库主菜单（二级菜单，parent_id=1，内容管理下）
-- ----------------------------
-- 菜单ID: 150（避免与认证申请菜单133-137冲突）
-- 路径: business/content/knowledge/knowledge
-- 权限标识: business:knowledge:view
-- 图标: guide
-- visible: 1 (显示)
INSERT INTO sys_menu VALUES('150', '知识库', '1', '7', 'knowledge', 'business/content/knowledge/knowledge', '', '', 1, 0, 'C', '0', '0', 'business:knowledge:view', 'guide', 'admin', sysdate(), '', null, '知识库管理，支持上传到元器/企微机器人');

-- ----------------------------
-- 2. 添加知识库按钮权限（parent_id=150）
-- ----------------------------
-- 按钮权限ID: 1137-1142（避免与认证申请按钮权限1104-1131冲突）
INSERT INTO sys_menu VALUES('1137', '知识库查询', '150', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:query', '#', 'admin', sysdate(), '', null, '查询知识库列表');
INSERT INTO sys_menu VALUES('1138', '知识库新增', '150', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:add', '#', 'admin', sysdate(), '', null, '新增知识库');
INSERT INTO sys_menu VALUES('1139', '知识库修改', '150', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:edit', '#', 'admin', sysdate(), '', null, '修改知识库');
INSERT INTO sys_menu VALUES('1140', '知识库删除', '150', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:remove', '#', 'admin', sysdate(), '', null, '删除知识库');
INSERT INTO sys_menu VALUES('1141', '知识库上传', '150', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:upload', '#', 'admin', sysdate(), '', null, '上传知识库到元器/企微机器人');
INSERT INTO sys_menu VALUES('1142', '空间管理', '150', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:knowledge:space', '#', 'admin', sysdate(), '', null, '知识库空间管理');

-- ----------------------------
-- 3. 为管理员角色（role_id=2）分配知识库菜单权限
-- ----------------------------
INSERT INTO sys_role_menu VALUES ('2', '150');  -- 知识库菜单
INSERT INTO sys_role_menu VALUES ('2', '1137'); -- 知识库查询
INSERT INTO sys_role_menu VALUES ('2', '1138'); -- 知识库新增
INSERT INTO sys_role_menu VALUES ('2', '1139'); -- 知识库修改
INSERT INTO sys_role_menu VALUES ('2', '1140'); -- 知识库删除
INSERT INTO sys_role_menu VALUES ('2', '1141'); -- 知识库上传
INSERT INTO sys_role_menu VALUES ('2', '1142'); -- 空间管理

-- ----------------------------
-- 4. 为普通角色（role_id=10）分配知识库基础权限
-- ----------------------------
-- 普通用户只能查询和创建自己的知识库
INSERT INTO sys_role_menu VALUES ('10', '150');  -- 知识库菜单
INSERT INTO sys_role_menu VALUES ('10', '1137'); -- 知识库查询
INSERT INTO sys_role_menu VALUES ('10', '1138'); -- 知识库新增



