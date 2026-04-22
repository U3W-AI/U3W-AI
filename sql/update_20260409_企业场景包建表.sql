-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-04-09
-- 变更类型：新建表 + ALTER 表
-- 变更内容：企业场景包运营体系建表（4张新表 + 2处索引变更）
-- 影响范围：新建 fbs_enterprise、fbs_enterprise_pack、fbs_enterprise_member、fbs_member_pack 表
--          ALTER fbs_scene_pack（新增索引）、fbs_skill_usage_record（新增索引）
-- 回滚方式：依次删除 4 张新表，删除 2 处新增索引
-- 兼容性：CREATE TABLE IF NOT EXISTS（幂等），ALTER 使用存储过程（幂等）
-- 配额模型说明：企业级配额统一在 fbs_enterprise_pack.packQuota/usedQuota
--              fbs_member_pack 仅作授权凭证，不存独立配额
-- ============================================================

-- ============================================================
-- 1. 企业组织主表 fbs_enterprise
-- ============================================================
-- 说明：企业组织主表，存储企业基本信息
-- 状态机：1=正常, 2=已禁用（终态）
-- 注意：不含 points_balance（企业积分池延期至后续阶段）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fbs_enterprise` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `enterprise_code`  VARCHAR(32)  NOT NULL COMMENT '企业编码（格式：ENT_xxxxxx）',
    `enterprise_name`  VARCHAR(128) NOT NULL COMMENT '企业名称（唯一）',
    `contact_name`     VARCHAR(64)  DEFAULT NULL COMMENT '联系人姓名',
    `contact_phone`    VARCHAR(32)  DEFAULT NULL COMMENT '联系人电话',
    `contact_email`    VARCHAR(128) DEFAULT NULL COMMENT '联系人邮箱',
    `remark`           VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=正常, 2=已禁用',
    `created_by`       VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time`      DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_by`       VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
    `update_time`      DATETIME     DEFAULT NULL COMMENT '更新时间',
    `del_flag`         CHAR(1)      DEFAULT '0' COMMENT '删除标志（0=存在, 2=删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_name` (`enterprise_name`),
    UNIQUE KEY `uk_enterprise_code` (`enterprise_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业组织主表';

-- ============================================================
-- 2. 企业已获场景包表 fbs_enterprise_pack
-- ============================================================
-- 说明：平台向企业分发（gift）记录，含企业级配额
-- 配额扣减统一在此层（packQuota / usedQuota），remainQuota 实时计算
-- 状态机：1=已授权, 2=已用尽（配额用完）, 3=已撤销（终态）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fbs_enterprise_pack` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `enterprise_id`    BIGINT       NOT NULL COMMENT '企业ID（关联 fbs_enterprise.id）',
    `pack_id`          BIGINT       NOT NULL COMMENT '场景包ID（关联 fbs_scene_pack.id）',
    `pack_quota`       INT          NOT NULL COMMENT '企业级总配额（packQuota）',
    `used_quota`       INT          NOT NULL DEFAULT 0 COMMENT '已使用配额（usedQuota）',
    `grant_time`       DATETIME     NOT NULL COMMENT '分发时间',
    `expiry_time`      DATETIME     DEFAULT NULL COMMENT '过期时间，NULL=永不过期',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=已授权, 2=已用尽, 3=已撤销',
    `created_by`       VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time`      DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_by`       VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
    `update_time`      DATETIME     DEFAULT NULL COMMENT '更新时间',
    `del_flag`         CHAR(1)      DEFAULT '0' COMMENT '删除标志（0=存在, 2=删除）',
    PRIMARY KEY (`id`),
    -- 同一企业同一场景包只有一条有效记录
    UNIQUE KEY `uk_enterprise_pack` (`enterprise_id`, `pack_id`),
    KEY `idx_enterprise` (`enterprise_id`),
    KEY `idx_pack` (`pack_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业已获场景包表（平台→企业分发，含企业级配额）';

-- ============================================================
-- 3. 企业成员关联表 fbs_enterprise_member
-- ============================================================
-- 说明：用户与企业的关系只通过此表维护，不改 sys_user 表
-- 状态机：1=正常, 2=已移除（终态）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fbs_enterprise_member` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `enterprise_id`    BIGINT      NOT NULL COMMENT '企业ID（关联 fbs_enterprise.id）',
    `user_id`          BIGINT      NOT NULL COMMENT '用户ID（关联 sys_user.user_id）',
    `role`             VARCHAR(32) NOT NULL DEFAULT 'MEMBER' COMMENT '成员角色：ADMIN=管理员, MEMBER=普通成员',
    `join_time`        DATETIME    NOT NULL COMMENT '加入时间',
    `status`           TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=正常, 2=已移除',
    `created_by`       VARCHAR(64) DEFAULT NULL COMMENT '创建者',
    `create_time`      DATETIME    DEFAULT NULL COMMENT '创建时间',
    `updated_by`       VARCHAR(64) DEFAULT NULL COMMENT '更新者',
    `update_time`      DATETIME    DEFAULT NULL COMMENT '更新时间',
    `del_flag`         CHAR(1)     DEFAULT '0' COMMENT '删除标志（0=存在, 2=删除）',
    PRIMARY KEY (`id`),
    -- 同一用户同一企业只有一条正常状态记录
    UNIQUE KEY `uk_member_active` (`enterprise_id`, `user_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业成员关联表';

-- ============================================================
-- 4. 成员场景包授权表 fbs_member_pack
-- ============================================================
-- 说明：纯授权凭证（成员是否有资格使用某企业包），不存独立配额
--       配额扣减统一在 fbs_enterprise_pack 层面，不在此表操作
-- 状态机：1=正常, 2=已用尽, 3=已过期, 4=已撤销
-- 注意：禁用企业/移除成员不批量修改此表状态，消费时通过企业/成员状态 fail-closed
-- ----------------------------
CREATE TABLE IF NOT EXISTS `fbs_member_pack` (
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `member_id`          BIGINT      NOT NULL COMMENT '企业成员ID（关联 fbs_enterprise_member.id）',
    `enterprise_pack_id` BIGINT      NOT NULL COMMENT '企业包ID（关联 fbs_enterprise_pack.id）',
    `pack_id`            BIGINT      NOT NULL COMMENT '场景包ID（关联 fbs_scene_pack.id，冗余便于查询）',
    `grant_time`         DATETIME    NOT NULL COMMENT '授权时间',
    `expiry_time`        DATETIME    DEFAULT NULL COMMENT '过期时间，NULL=永不过期',
    `status`             TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=正常, 2=已用尽, 3=已过期, 4=已撤销',
    `created_by`         VARCHAR(64) DEFAULT NULL COMMENT '创建者',
    `create_time`        DATETIME    DEFAULT NULL COMMENT '创建时间',
    `updated_by`         VARCHAR(64) DEFAULT NULL COMMENT '更新者',
    `update_time`        DATETIME    DEFAULT NULL COMMENT '更新时间',
    `del_flag`           CHAR(1)     DEFAULT '0' COMMENT '删除标志（0=存在, 2=删除）',
    PRIMARY KEY (`id`),
    KEY `idx_member` (`member_id`),
    KEY `idx_enterprise_pack` (`enterprise_pack_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成员场景包授权表（纯授权凭证，不存配额）';

-- ============================================================
-- 5. ALTER fbs_scene_pack：新增 idx_owner_type 索引
-- ============================================================
-- 说明：支持企业类型(owner_type=2)查询加速
-- 注意：owner_type 字段已是 TINYINT，只需确保复合索引存在
--       MySQL 5.7 不支持直接 ALTER ENUM，枚举值由应用层（Java enum）保证
-- ----------------------------
DROP PROCEDURE IF EXISTS add_fbs_scene_pack_owner_type_2;

DELIMITER ;;
CREATE PROCEDURE add_fbs_scene_pack_owner_type_2()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'fbs_scene_pack'
          AND INDEX_NAME   = 'idx_owner_type'
          AND COLUMN_NAME  = 'owner_type'
    ) THEN
        ALTER TABLE `fbs_scene_pack` ADD INDEX `idx_owner_type` (`owner_type`);
    END IF;
END;;
DELIMITER ;

CALL add_fbs_scene_pack_owner_type_2();
DROP PROCEDURE IF EXISTS add_fbs_scene_pack_owner_type_2;

-- ============================================================
-- 6. ALTER fbs_skill_usage_record：新增 idx_host_type 索引
-- ============================================================
-- 说明：支持 ENTERPRISE 主机类型查询加速
-- 注意：host_type 字段已是 VARCHAR(32)，只需添加索引
--       host_type 新增合法值：ENTERPRISE（企业成员调用，走企业配额）
-- ----------------------------
DROP PROCEDURE IF EXISTS add_fbs_skill_usage_record_host_type_idx;

DELIMITER ;;
CREATE PROCEDURE add_fbs_skill_usage_record_host_type_idx()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'fbs_skill_usage_record'
          AND INDEX_NAME   = 'idx_host_type'
          AND COLUMN_NAME  = 'host_type'
    ) THEN
        ALTER TABLE `fbs_skill_usage_record` ADD INDEX `idx_host_type` (`host_type`);
    END IF;
END;;
DELIMITER ;

CALL add_fbs_skill_usage_record_host_type_idx();
DROP PROCEDURE IF EXISTS add_fbs_skill_usage_record_host_type_idx;
