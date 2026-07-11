-- ============================================================
-- U3W-AI 企业微信智能机器人编排 Outbox 租约增强
-- 变更日期：2026-07-11
-- 目标：为持久 Outbox 增加 ownership token 与状态修订序号，使 Quartz/worker
--       只能充当可替换唤醒器，不能成为编排状态真相源。
-- 边界：不包含 response_url、真实企微写入、业务回执提升或场景逻辑。
-- 前置：update_20260711_企微智能机器人编排控制面建表.sql
-- ============================================================

ALTER TABLE `fbs_delivery_outbox`
    ADD COLUMN `lease_token` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
        COMMENT '每次 claim 唯一 fencing token' AFTER `lease_owner`,
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '状态修订序号，fencing 以 lease_token 为准' AFTER `last_error`,
    ADD UNIQUE KEY `uk_fbs_outbox_lease_token` (`lease_token`);

ALTER TABLE `fbs_delivery_outbox`
    DROP INDEX `idx_fbs_outbox_dispatch`,
    ADD KEY `idx_fbs_outbox_dispatch`
        (`destination_type`, `status`, `next_attempt_at`, `lease_until`, `id`);
