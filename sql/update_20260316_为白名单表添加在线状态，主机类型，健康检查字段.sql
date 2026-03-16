/*
 Navicat Premium Dump SQL

 Source Server         : mysql_3306
 Source Server Type    : MySQL
 Source Server Version : 80039 (8.0.39)
 Source Host           : localhost:3306
 Source Schema         : wxfbsir

 Target Server Type    : MySQL
 Target Server Version : 80039 (8.0.39)
 File Encoding         : 65001

 Date: 16/03/2026 11:48:19
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ws_host_whitelist
-- ----------------------------
DROP TABLE IF EXISTS `ws_host_whitelist`;
CREATE TABLE `ws_host_whitelist`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `host_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主机ID（用户申请后由管理员分配）',
  `host_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '主机名称/描述',
  `owner_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人姓名',
  `owner_contact` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人联系方式',
  `is_team` tinyint NOT NULL DEFAULT 0 COMMENT '是否团队主机：0-个人，1-团队（仅用于统计分类）',
  `team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '团队名称（is_team=1时填写）',
  `allowed_ips` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '允许的IP地址列表（逗号分隔，为空表示不限制）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '过期时间（为空表示永不过期）',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `del_flag` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0-正常，1-已删除',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `host_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'engine' COMMENT '主机类型：engine/openclaw',
  `health_check_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '健康检查URL',
  `online_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'offline' COMMENT '在线状态：online/offline',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_host_id`(`host_id` ASC) USING BTREE,
  INDEX `idx_is_team`(`is_team` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_del_flag`(`del_flag` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'WebSocket主机白名单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ws_host_whitelist
-- ----------------------------
INSERT INTO `ws_host_whitelist` VALUES (1, 'engine-001', '默认Engine节点', '管理员', NULL, 0, NULL, NULL, 1, NULL, '默认配置的Engine节点，对应application.yml中的host-id', 0, '', '2026-03-10 12:10:37', '', '2026-03-10 12:10:37', 'engine', NULL, 'offline');
INSERT INTO `ws_host_whitelist` VALUES (2, 'engine-dev-001', '开发测试节点1', '张三', NULL, 0, NULL, NULL, 1, NULL, '开发环境测试用', 0, '', '2026-03-10 12:10:37', '', '2026-03-10 12:10:37', 'engine', NULL, 'offline');
INSERT INTO `ws_host_whitelist` VALUES (3, 'engine-prod-001', '生产节点-运维组', '运维组', NULL, 1, '运维团队', NULL, 1, NULL, '生产环境主节点', 0, '', '2026-03-10 12:10:37', '', '2026-03-10 12:10:37', 'engine', NULL, 'offline');
INSERT INTO `ws_host_whitelist` VALUES (6, 'test', 'test', 'jjx', NULL, 0, NULL, NULL, 1, NULL, NULL, 0, 'admin', '2026-03-16 11:45:40', 'admin', '2026-03-16 11:46:14', 'openclaw', 'http://127.0.0.1:18789', 'online');

SET FOREIGN_KEY_CHECKS = 1;
