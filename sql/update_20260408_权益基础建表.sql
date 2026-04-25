-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-04-08
-- 变更类型：新建表 + ALTER 表
-- 变更内容：FBS 权益基础体系建表（4张新表 + 1处 ALTER）
-- 影响范围：新建 fbs_scene_pack、fbs_auth_code、fbs_user_pack、fbs_skill_usage_record 表
--          ALTER wx_points_record（新增 2 个字段）
-- 回滚方式：依次删除 4 张新表，ALTER wx_points_record 删除 2 个新增字段
-- 兼容性：CREATE TABLE IF NOT EXISTS（幂等），ALTER 使用存储过程（幂等）
-- 核心概念：
--   - 场景包：平台/企业/个人创建的技能包
--   - 授权码：用于激活权益的凭证
--   - 用户场景包：用户与场景包的关联关系
--   - Skill使用记录：记录每次技能调用
-- ============================================================

-- ============================================================
-- 1. 场景包主表 fbs_scene_pack
-- ============================================================
-- 说明：场景包主表，存储场景包的基本信息和状态
-- pack_type：1=平台包, 2=企业包, 3=自定义包
-- owner_type：1=平台, 2=企业, 3=个人
-- status：0=草稿, 1=已发布, 2=已下架
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fbs_scene_pack` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pack_code`        VARCHAR(64)  NOT NULL COMMENT '场景包编码（全局唯一业务键）',
    `pack_name`        VARCHAR(128) NOT NULL COMMENT '场景包名称',
    `pack_type`        TINYINT      NOT NULL COMMENT '场景包类型：1=平台包, 2=企业包, 3=自定义包',
    `owner_type`       TINYINT      NOT NULL COMMENT '所有者类型：1=平台, 2=企业, 3=个人',
    `owner_id`         BIGINT       DEFAULT NULL COMMENT '所属者ID（owner_type=1时为NULL）',
    `description`      TEXT         COMMENT '场景包描述',
    `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=草稿, 1=已发布, 2=已下架',
    `visible_scope`    VARCHAR(32)  DEFAULT 'ALL' COMMENT '可见范围：ALL/PRIVATE',
    `points_rule_code` VARCHAR(64)  DEFAULT NULL COMMENT '关联积分规则编码（NULL=免费包）',
    `content_snapshot` LONGTEXT     COMMENT '内容快照JSON（MVP版本管理简化方案）',
    `current_version`  VARCHAR(32)  DEFAULT '1.0.0' COMMENT '当前版本号',
    `created_by`       VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time`      DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_by`       VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
    `update_time`      DATETIME     DEFAULT NULL COMMENT '更新时间',
    `del_flag`         CHAR(1)      DEFAULT '0' COMMENT '删除标志（0=存在, 2=删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pack_code` (`pack_code`),
    KEY `idx_owner` (`owner_type`, `owner_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场景包主表';

-- ============================================================
-- 2. 授权码表 fbs_auth_code
-- ============================================================
-- 说明：存储授权码信息，用于用户激活场景包权益
-- 状态机：
--   - available：管理层面开关，0=禁用, 1=启用
--   - status：使用状态，0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销
-- 注意：available（0=禁用/1=启用）与 status 是两个独立维度
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fbs_auth_code` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `auth_code`       VARCHAR(128) NOT NULL COMMENT '授权码字符串',
    `code_type`       TINYINT      NOT NULL COMMENT '类型：1=场景包权益码, 2=通用授权码',
    `target_type`     VARCHAR(32)  DEFAULT NULL COMMENT '目标类型：SCENE_PACK/GENERIC',
    `target_id`       BIGINT       DEFAULT NULL COMMENT '关联目标ID（如场景包ID）',
    `issuer_type`     TINYINT      NOT NULL COMMENT '发放者类型：1=平台, 2=企业, 3=用户',
    `issuer_id`       BIGINT       NOT NULL COMMENT '发放者ID',
    `available`       TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态：0=禁用, 1=启用',
    `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '使用状态：0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销',
    `deadline`        DATETIME     DEFAULT NULL COMMENT '截止时间，NULL=不限',
    `max_activations` INT          NOT NULL DEFAULT 1 COMMENT '最大激活次数',
    `activated_count` INT          NOT NULL DEFAULT 0 COMMENT '已激活次数',
    `description`     VARCHAR(256) DEFAULT NULL COMMENT '说明/备注',
    `created_by`      VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time`     DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_by`      VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
    `update_time`     DATETIME     DEFAULT NULL COMMENT '更新时间',
    `del_flag`        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0=存在, 2=删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_code` (`auth_code`),
    KEY `idx_status` (`status`),
    KEY `idx_available` (`available`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权码表';

-- ============================================================
-- 3. 用户场景包关系表 fbs_user_pack
-- ============================================================
-- 说明：用户与场景包的关联关系，记录用户的权益
-- source_type：1=平台分发, 2=企业分发, 3=用户激活（通过授权码）
-- status：1=有效, 2=已过期, 3=已撤销
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fbs_user_pack` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT      NOT NULL COMMENT '用户ID',
    `pack_id`      BIGINT      NOT NULL COMMENT '场景包ID',
    `pack_version` VARCHAR(32) DEFAULT NULL COMMENT '激活时的版本',
    `auth_code_id` BIGINT      DEFAULT NULL COMMENT '激活时使用的授权码ID（source_type=3时有值）',
    `activated_at` DATETIME    NOT NULL COMMENT '激活时间',
    `expires_at`   DATETIME    DEFAULT NULL COMMENT '过期时间，NULL=永不过期',
    `status`       TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=有效, 2=已过期, 3=已撤销',
    `source_type`  TINYINT     NOT NULL COMMENT '来源类型：1=平台分发, 2=企业分发, 3=用户激活',
    `created_by`   VARCHAR(64) DEFAULT NULL COMMENT '创建者',
    `create_time`  DATETIME    DEFAULT NULL COMMENT '创建时间',
    `updated_by`   VARCHAR(64) DEFAULT NULL COMMENT '更新者',
    `update_time`  DATETIME    DEFAULT NULL COMMENT '更新时间',
    `del_flag`     CHAR(1)     DEFAULT '0' COMMENT '删除标志（0=存在, 2=删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_pack` (`user_id`, `pack_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_pack` (`pack_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户场景包关系表';

-- ============================================================
-- 4. Skill 使用记录表 fbs_skill_usage_record
-- ============================================================
-- 说明：记录每次技能调用的详细信息，用于积分扣减和审计
-- usage_record_id：调用方生成的幂等键（如 WorkBuddy taskId/UUID）
-- host_type：WORKBUDDY/STANDALONE/API
-- status：0=进行中, 1=成功, 2=失败
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fbs_skill_usage_record` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `usage_record_id`  VARCHAR(64)  NOT NULL COMMENT '使用记录幂等键（对应 WorkBuddy taskId 或 UUID）',
    `user_id`          BIGINT       NOT NULL COMMENT '用户ID',
    `host_type`        VARCHAR(32)  NOT NULL COMMENT '宿主类型：WORKBUDDY/STANDALONE/API',
    `host_session_id`  VARCHAR(128) DEFAULT NULL COMMENT '宿主会话ID（可空）',
    `skill_code`       VARCHAR(64)  NOT NULL COMMENT '技能编码',
    `pack_id`          BIGINT       DEFAULT NULL COMMENT '使用的场景包ID',
    `pack_version`     VARCHAR(32)  DEFAULT NULL COMMENT '使用的场景包版本',
    `points_amount`    INT          NOT NULL DEFAULT 0 COMMENT '扣减积分数量（0=免费）',
    `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=进行中, 1=成功, 2=失败',
    `start_time`       DATETIME     NOT NULL COMMENT '使用开始时间',
    `end_time`         DATETIME     DEFAULT NULL COMMENT '使用结束时间',
    `duration_seconds` INT          DEFAULT NULL COMMENT '使用时长（秒）',
    `error_message`    TEXT         DEFAULT NULL COMMENT '失败原因',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_usage_record_id` (`usage_record_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_pack` (`pack_id`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill使用记录表';

-- ============================================================
-- 5. 扩展现有表 wx_points_record
-- ============================================================
-- 说明：新增 2 个字段关联场景包和使用记录
-- scene_pack_id：关联场景包ID（fbs_skill消费时填写）
-- usage_record_id：关联使用记录幂等键
-- ----------------------------
DROP PROCEDURE IF EXISTS add_wx_points_record_cols;

DELIMITER ;;
CREATE PROCEDURE add_wx_points_record_cols()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'wx_points_record'
          AND COLUMN_NAME  = 'scene_pack_id'
    ) THEN
        ALTER TABLE `wx_points_record`
            ADD COLUMN `scene_pack_id` BIGINT DEFAULT NULL COMMENT '关联场景包ID（fbs_skill消费时填写）' AFTER `remark`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'wx_points_record'
          AND COLUMN_NAME  = 'usage_record_id'
    ) THEN
        ALTER TABLE `wx_points_record`
            ADD COLUMN `usage_record_id` VARCHAR(64) DEFAULT NULL COMMENT '关联使用记录幂等键' AFTER `scene_pack_id`;
    END IF;
END;;
DELIMITER ;

CALL add_wx_points_record_cols();
DROP PROCEDURE IF EXISTS add_wx_points_record_cols;
