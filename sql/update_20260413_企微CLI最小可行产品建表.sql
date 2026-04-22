-- ============================================================
-- 数据库变更说明
-- ============================================================
-- 变更日期：2026-04-13
-- 变更类型：新建表
-- 变更内容：企业微信 CLI 最小可行产品建表
-- 影响范围：新建 fbs_wecom_sync_log 表
-- 回滚方式：DROP TABLE fbs_wecom_sync_log
-- 兼容性：CREATE TABLE IF NOT EXISTS（幂等）
-- 说明：企微同步日志表（MVP），记录每次 read 调用的状态/快照/耗时/错误
--       sync_type MVP 只保留 READ（check 不写日志）
-- ============================================================

CREATE TABLE IF NOT EXISTS fbs_wecom_sync_log (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    sync_type       VARCHAR(16)  NOT NULL DEFAULT 'READ'   COMMENT '同步类型：READ（MVP 只用 READ）',
    sheet_name      VARCHAR(64)  NOT NULL                  COMMENT 'Sheet 名称（meta / commercial_hub）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态大类：SUCCESS / FAILED / TIMEOUT / PARSE_ERROR',
    record_count    INT          DEFAULT 0                 COMMENT '读取到的记录数',
    error_code      VARCHAR(32)  DEFAULT NULL              COMMENT '细分错误码：CLI_NOT_FOUND / NET_TIMEOUT / NET_CONNECTION / AUTH_REQUIRED / PARSE_ERROR / UNKNOWN',
    error_message   TEXT         DEFAULT NULL              COMMENT '错误信息（截断 2000 字符）',
    snapshot_json   LONGTEXT     DEFAULT NULL              COMMENT '原始数据 JSON 快照',
    duration_ms     BIGINT       DEFAULT 0                 COMMENT '总耗时（毫秒）',
    created_by      BIGINT       DEFAULT NULL              COMMENT '操作人 ID',
    created_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_status (status),
    INDEX idx_sheet_name (sheet_name),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企微同步日志表（MVP）';
