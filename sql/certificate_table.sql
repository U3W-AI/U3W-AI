-- ----------------------------
-- 1、 证书模板表
-- ----------------------------
DROP TABLE IF EXISTS `certificate_template`;
CREATE TABLE `certificate_template`  (
                                         `template_id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板ID',
                                         `template_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
                                         `certificate_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '证书类型',
                                         `template_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '模板内容',
                                         `apply_required_fields` json NULL COMMENT '申请必填字段',
                                         `template_fields` json NULL COMMENT '模板字段配置',
                                         `review_process_config_id` bigint NULL DEFAULT NULL COMMENT '审核流程配置ID',
                                         `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
                                         `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
                                         `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                         `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
                                         `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                         `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
                                         `certificate_bg_image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '证书底版图片路径',
                                         `field_positions` json NULL COMMENT '字段位置配置',
                                         PRIMARY KEY (`template_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 30 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证书模板表' ROW_FORMAT = DYNAMIC;


-- ----------------------------
-- 2、 申请记录表
-- ----------------------------
DROP TABLE IF EXISTS `certificate_application`;
CREATE TABLE `certificate_application`  (
                                            `application_id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
                                            `user_id` bigint NOT NULL COMMENT '申请人ID',
                                            `template_id` bigint NOT NULL COMMENT '模板ID',
                                            `certificate_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '证书ID',
                                            `application_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '申请状态（0待审核 1审核通过 2审核拒绝）',
                                            `application_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '申请内容',
                                            `reviewer_id` bigint NULL DEFAULT NULL COMMENT '审核人ID',
                                            `review_time` datetime NULL DEFAULT NULL COMMENT '审核时间',
                                            `review_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '审核备注',
                                            `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
                                            `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                            `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
                                            `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                            `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
                                            `points_deducted` int NULL DEFAULT 0 COMMENT '申请提交时扣除的积分',
                                            `application_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '申请数据（JSON格式）',
                                            `application_number` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '申请编号',
                                            `approve_time` datetime NULL DEFAULT NULL COMMENT '审批通过时间',
                                            `receive_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'not_received' COMMENT '领取状态（not_received-未领取，received-已领取）',
                                            PRIMARY KEY (`application_id`) USING BTREE,
                                            INDEX `idx_user_id`(`user_id` ASC) USING BTREE COMMENT '申请人ID索引',
                                            INDEX `idx_template_id`(`template_id` ASC) USING BTREE COMMENT '模板ID索引',
                                            INDEX `idx_application_status`(`application_status` ASC) USING BTREE COMMENT '申请状态索引'
) ENGINE = InnoDB AUTO_INCREMENT = 98 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '证书申请表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 3、申请信息记录表
-- ----------------------------
DROP TABLE IF EXISTS `application_review`;
CREATE TABLE `application_review`  (
                                       `review_id` bigint NOT NULL AUTO_INCREMENT COMMENT '审核ID',
                                       `application_id` bigint NOT NULL COMMENT '申请ID',
                                       `reviewer_id` bigint NULL DEFAULT NULL COMMENT '审核人ID',
                                       `review_opinion` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '审核意见',
                                       `review_result` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '审核结果',
                                       `review_time` datetime NULL DEFAULT NULL COMMENT '审核时间',
                                       `node_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '审核节点ID',
                                       `node_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '审核节点名称',
                                       `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
                                       `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
                                       `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
                                       `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
                                       `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '备注',
                                       PRIMARY KEY (`review_id`) USING BTREE,
                                       INDEX `idx_application_id`(`application_id` ASC) USING BTREE COMMENT '申请ID索引',
                                       INDEX `idx_reviewer_id`(`reviewer_id` ASC) USING BTREE COMMENT '审核人ID索引',
                                       INDEX `idx_review_time`(`review_time` ASC) USING BTREE COMMENT '审核时间索引'
) ENGINE = InnoDB AUTO_INCREMENT = 64 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '申请审核记录表' ROW_FORMAT = DYNAMIC;
