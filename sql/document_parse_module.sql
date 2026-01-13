-- ----------------------------
-- 文档解析助手模块SQL
-- 包含：菜单、权限、表结构、角色权限
-- 执行方式：mysql -u root -p wxfbsir < sql/document_parse_module.sql
-- ----------------------------

USE `wxfbsir`;

-- ----------------------------
-- 1、菜单配置
-- ----------------------------

-- 文档解析助手菜单（二级菜单，ID=130）
INSERT INTO sys_menu VALUES(
  '130',
  '文档解析助手',
  '1',
  '3',
  'document-parse',
  'business/content/documentparse/index',
  '',
  '',
  1,
  0,
  'C',
  '0',
  '0',
  'business:document:view',
  'documentation',
  'admin',
  sysdate(),
  '',
  null,
  '文档解析助手菜单'
);

-- 文档解析助手按钮权限（parent_id=130）
INSERT INTO sys_menu VALUES('1097', '解析查询', '130', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:document:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('1098', '解析新增', '130', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:document:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('1099', '解析删除', '130', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'business:document:remove', '#', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 2、角色菜单权限配置
-- ----------------------------

-- 管理员角色（ID=2）- 拥有文档解析助手所有权限
INSERT INTO sys_role_menu VALUES ('2', '130');   -- 文档解析助手菜单
INSERT INTO sys_role_menu VALUES ('2', '1097');  -- 文档解析助手-解析查询
INSERT INTO sys_role_menu VALUES ('2', '1098');  -- 文档解析助手-解析新增
INSERT INTO sys_role_menu VALUES ('2', '1099');  -- 文档解析助手-解析删除

-- 只读权限角色（ID=3）- 拥有文档解析助手查询权限
INSERT INTO sys_role_menu VALUES ('3', '130');   -- 文档解析助手菜单
INSERT INTO sys_role_menu VALUES ('3', '1097');  -- 文档解析助手-解析查询

-- 普通用户角色（ID=10）- 拥有文档解析助手使用权限
INSERT INTO sys_role_menu VALUES ('10', '130');   -- 文档解析助手菜单
INSERT INTO sys_role_menu VALUES ('10', '1097');  -- 文档解析助手-解析查询
INSERT INTO sys_role_menu VALUES ('10', '1098');  -- 文档解析助手-解析新增
INSERT INTO sys_role_menu VALUES ('10', '1099');  -- 文档解析助手-解析删除

-- ----------------------------
-- 3、数据表结构
-- ----------------------------

-- 文档解析表
DROP TABLE IF EXISTS `document_parse`;
CREATE TABLE `document_parse` (
  `id`  bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文档解析ID',
  `user_id`  bigint(20) NOT NULL COMMENT '用户ID',
  `document_id`  varchar(200) NOT NULL COMMENT '文档ID（自动生成）',
  `document_name`  varchar(500) DEFAULT NULL COMMENT '文档名称',
  `prompt`  text COMMENT '提示词',
  `parsed_content`  longtext COMMENT '解析后的内容（来自腾讯元器智能体）',
  `agent_task_id`  varchar(200) DEFAULT NULL COMMENT '腾讯元器智能体任务ID',
  `process_status`  tinyint(1) NOT NULL DEFAULT 0 COMMENT '处理状态：0-处理中，1-已完成，2-失败',
  `error_message`  varchar(1000) DEFAULT NULL COMMENT '错误信息',
  `create_by`  varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time`  datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`  varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time`  datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`  varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE COMMENT '用户ID索引',
  KEY `idx_document_id` (`document_id`) USING BTREE COMMENT '文档ID索引',
  KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '创建时间索引',
  KEY `idx_process_status` (`process_status`) USING BTREE COMMENT '处理状态索引',
  KEY `idx_agent_task_id` (`agent_task_id`) USING BTREE COMMENT '智能体任务ID索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档解析表';

-- ----------------------------
-- 验证安装
-- ----------------------------
SELECT '文档解析助手模块安装完成' AS message;
SELECT menu_id, menu_name, icon, path FROM sys_menu WHERE menu_id = 130;
SELECT COUNT(*) AS role_menu_count FROM sys_role_menu WHERE menu_id IN (130, 1097, 1098, 1099);
SHOW TABLES LIKE 'document_parse';
