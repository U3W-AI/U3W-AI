-- ============================================================================
-- AI咨询功能数据库迁移脚本（完整版 - 支持多AI上下文对话）
-- 从cube项目迁移到WxFbsir项目
-- 创建时间：2026-01-07
-- 更新时间：2026-01-08 - 合并上下文优化方案，支持所有AI会话ID
-- ============================================================================

-- 🔥 核心设计原则（完全参考旧项目cube-admin）：
-- 1. id = sessionId（每轮对话唯一标识）
-- 2. chat_id = 会话ID（多轮对话共享，用于上下文关联）
-- 3. 各AI会话ID字段允许为NULL（用户单次可能只调用3-4个AI）
-- 4. 使用ON DUPLICATE KEY UPDATE实现上下文复用

-- 1. 给用户表添加主机ID字段
ALTER TABLE sys_user ADD COLUMN host_id VARCHAR(100) DEFAULT NULL COMMENT '用户绑定的主机ID';

-- 2. 创建聊天历史记录表（完整版：支持多AI上下文对话）
DROP TABLE IF EXISTS `wc_chat_history`;
CREATE TABLE `wc_chat_history`  (
  `id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主键ID（sessionId，每轮对话唯一）',
  `user_id` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户ID',
  `userPrompt` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '用户指令',
  `data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '全部数据（JSON格式，含progressLogs、screenshots等）',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `chat_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '会话ID（多轮对话共享，用于上下文关联）',
  
  -- 🔥 AI会话ID字段（完全参考旧项目cube-admin，支持上下文复用）
  `tone_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '通义千问会话ID',
  `yb_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '元宝会话ID',
  `db_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '豆包会话ID',
  `ty_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '通义会话ID',
  `deepseek_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'DeepSeek会话ID',
  `max_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MiniMax会话ID',
  `metaso_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '秘塔AI会话ID',
  `kimi_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'Kimi会话ID',
  `baidu_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '百度AI会话ID',
  `zhzd_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '知乎直答会话ID',
  
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_chat`(`user_id`, `chat_id`) USING BTREE,
  INDEX `idx_chat_create`(`chat_id`, `create_time` DESC) USING BTREE,
  INDEX `idx_user_create_time`(`user_id`, `create_time`) USING BTREE,
  INDEX `idx_deepseek`(`deepseek_chat_id`) USING BTREE,
  INDEX `idx_yuanbao`(`yb_chat_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '聊天历史记录表（支持多AI上下文对话）' ROW_FORMAT = Dynamic;

/*
🔥 上下文复用核心逻辑说明（完全参考旧项目cube-admin AIGCMapper.xml:346-366）：

1. 前端维护chatId：创建新对话时生成新的chatId（uuid）
2. 前端维护各AI的chatId：通过userInfoReq对象存储
3. 加载历史时恢复AI会话ID：从数据库获取后赋值给userInfoReq
4. 保存时使用ON DUPLICATE KEY UPDATE：同一sessionId下更新而非插入

数据流转示例：
+------------------+----------+---------+------------------+-----------------+
| id (sessionId)   | chat_id  | user_id | deepseek_chat_id | yb_chat_id      |
+------------------+----------+---------+------------------+-----------------+
| session-001      | chat-A   | user-1  | ds_chat_123      | yb_chat_456     |  <- 第1轮
| session-002      | chat-A   | user-1  | ds_chat_123      | yb_chat_456     |  <- 第2轮复用
| session-003      | chat-A   | user-1  | ds_chat_123      | yb_chat_456     |  <- 第3轮复用
| session-004      | chat-B   | user-1  | ds_chat_789      | yb_chat_012     |  <- 新会话
+------------------+----------+---------+------------------+-----------------+

🔥 关键：通过chat_id实现上下文关联，AI会话ID在首次调用后保存，后续对话复用
*/

-- 3. 创建AI记录扩展表（原草稿表，用于存储AI生成的多类型内容）
DROP TABLE IF EXISTS `wc_playwright_draft`;
CREATE TABLE `wc_playwright_draft`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '扩展记录ID（自动生成UUID）',
  `task_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联的聊天历史记录ID（wc_chat_history.id）',
  `keyword` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '主题词（保留字段，暂未使用）',
  `user_prompt` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '用户指令',
  `draft_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'AI生成的内容（文本/图片URL/视频URL等）',
  `is_push` int(4) NULL DEFAULT NULL COMMENT '是否已推送（预留字段）',
  `ai_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'AI来源（deepseek/yuanbao等）',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `user_name` bigint(4) NULL DEFAULT 0 COMMENT '创建人用户ID',
  `share_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'AI分享链接',
  `share_img_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'AI对话截图URL',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `user_name`(`user_name`) USING BTREE,
  INDEX `idx_task_id`(`task_id`) USING BTREE COMMENT '关联聊天历史记录索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI记录扩展表（存储AI生成的多类型内容：文本/图片/视频等）' ROW_FORMAT = Dynamic;

/*
🔥 AI记录扩展表说明：
- task_id：关联wc_chat_history表的主键id，一次聊天可能产生多条扩展记录
- draft_content：AI生成的主要内容
- share_url：DeepSeek等AI的分享链接
- share_img_url：对话截图URL
- 用途：当AI返回多种类型结果时（如图片+文本），聊天历史表只存储主结果，其他结果存入扩展表
*/

-- 4. 添加AI助手菜单（在内容管理下，parent_id=1）
-- 注意：wxfbsir.sql中已定义菜单体系，此处仅添加AIGC相关菜单
-- 菜单ID规划：内容管理子菜单从118开始，已用118-119，AIGC使用129-130
-- 字段顺序：menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark

-- 插入AI助手菜单（二级菜单，ID=129）
INSERT IGNORE INTO sys_menu VALUES ('129', 'AI助手', '1', '3', 'aigc', 'aigc/index', NULL, '', 1, 0, 'C', '0', '0', 'aigc:assistant:list', 'system', 'admin', sysdate(), '', null, 'AI助手菜单');

-- 插入草稿库菜单（二级菜单，ID=130）
INSERT IGNORE INTO sys_menu VALUES ('130', '草稿库', '1', '4', 'drafts', 'aigc/drafts', NULL, '', 1, 0, 'C', '0', '0', 'aigc:drafts:list', 'documentation', 'admin', sysdate(), '', null, '草稿库菜单');

-- 5. 给管理员角色分配AIGC菜单权限
-- 管理员角色ID=2（来自wxfbsir.sql中的sys_role表）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (2, 129);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (2, 130);

-- 6. 添加AIGC相关按钮权限（按钮权限ID从1081起，已用到1080）
-- AI助手按钮权限（parent_id=129）
INSERT IGNORE INTO sys_menu VALUES ('1081', 'AI咨询', '129', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'aigc:assistant:query', '#', 'admin', sysdate(), '', null, '');

INSERT IGNORE INTO sys_menu VALUES ('1082', '历史查询', '129', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'aigc:assistant:history', '#', 'admin', sysdate(), '', null, '');

-- 草稿库按钮权限（parent_id=130）
INSERT IGNORE INTO sys_menu VALUES ('1083', '草稿查询', '130', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'aigc:drafts:list', '#', 'admin', sysdate(), '', null, '');

INSERT IGNORE INTO sys_menu VALUES ('1084', '草稿保存', '130', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'aigc:drafts:save', '#', 'admin', sysdate(), '', null, '');

-- 分配按钮权限给管理员
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (2, 1081);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (2, 1082);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (2, 1083);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (2, 1084);

-- 完成提示
SELECT 'AI咨询功能数据库迁移完成！' as result;
