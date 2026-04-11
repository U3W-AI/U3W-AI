-- ============================================================
-- OpenSpec #4: add-user-self-service
-- 阶段1 DDL：fbs_user_pack 审计字段扩展
--
-- 日期: 2026-04-10
-- 说明: 支撑 claim/activate 写入审计字段（operator、requestId、operateTime）
-- 注意: UNIQUE(user_id, pack_id)（uk_user_pack）已在 V20260408 基础建表中定义
--       本 SQL 不新增唯一约束
-- ============================================================

-- 1.1 操作人用户ID
ALTER TABLE fbs_user_pack
ADD COLUMN operator BIGINT COMMENT '操作人用户ID';

-- 1.2 请求追踪ID
ALTER TABLE fbs_user_pack
ADD COLUMN request_id VARCHAR(64) COMMENT '请求追踪ID';

-- 1.3 操作时间
ALTER TABLE fbs_user_pack
ADD COLUMN operate_time DATETIME COMMENT '操作时间';
