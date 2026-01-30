INSERT INTO `wx_points_rule` VALUES (11, 'USE_GITEE_AI', '使用Gitee AI助手', -1, NULL, NULL, NULL, '0', 0, '使用Gitee AI助手服务时扣减积分', 'admin', '2026-01-28 15:39:10', '', NULL);

ALTER TABLE `wc_chat_history` ADD COLUMN   `gitee_chat_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'Gitee AI Chat会话ID';