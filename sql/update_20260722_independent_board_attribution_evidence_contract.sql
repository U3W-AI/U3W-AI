-- W4b.2d 独董会证据合同（default-off / report-only）
-- 仅建立不可变证据底座，不注册公开入口、不写入冻结 26.7.20 包、不产生业务积分。
CREATE TABLE IF NOT EXISTS `fbs_attribution_product_contract` (
  `contract_id` VARCHAR(96) NOT NULL,
  `product_id` VARCHAR(96) NOT NULL,
  `product_version` VARCHAR(32) NOT NULL,
  `host_type` VARCHAR(32) NOT NULL,
  `connector_type` VARCHAR(64) NOT NULL,
  `entry_surface` VARCHAR(64) NOT NULL,
  `package_id` VARCHAR(191) NOT NULL DEFAULT '',
  `expert_entry_id` VARCHAR(191) NOT NULL DEFAULT '',
  `registration_status` VARCHAR(48) NOT NULL,
  `candidate_enabled` TINYINT NOT NULL DEFAULT 0,
  `public_route_enabled` TINYINT NOT NULL DEFAULT 0,
  `authoritative_credit_enabled` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`contract_id`),
  UNIQUE KEY `uk_fbs_attr_product_contract` (`product_id`, `product_version`),
  CONSTRAINT `chk_fbs_attr_product_exact` CHECK (`product_id` = 'fbsir-eight-seat-board' AND `product_version` = '26.7.20'),
  CONSTRAINT `chk_fbs_attr_contract_default_off` CHECK (`candidate_enabled` = 0 AND `public_route_enabled` = 0 AND `authoritative_credit_enabled` = 0),
  CONSTRAINT `chk_fbs_attr_registration_pending` CHECK (`registration_status` = 'PENDING_HOST_REGISTRATION')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独董会精确产品合同，默认关闭';

INSERT IGNORE INTO `fbs_attribution_product_contract`
(`contract_id`,`product_id`,`product_version`,`host_type`,`connector_type`,`entry_surface`,`registration_status`)
VALUES ('FBSIR_INDEPENDENT_BOARD_W4B2D','fbsir-eight-seat-board','26.7.20','WORKBUDDY','fbs-connector','official_entry','PENDING_HOST_REGISTRATION');

CREATE TABLE IF NOT EXISTS `fbs_host_forwarding_challenge` (
  `challenge_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `contract_id` VARCHAR(96) NOT NULL,
  `server_binding_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `nonce_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issued_at` DATETIME(3) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `retention_until` DATETIME(3) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`challenge_id`),
  UNIQUE KEY `uk_fbs_attr_challenge_binding` (`challenge_id`,`server_binding_id`,`contract_id`),
  CONSTRAINT `fk_fbs_attr_challenge_contract` FOREIGN KEY (`contract_id`) REFERENCES `fbs_attribution_product_contract` (`contract_id`),
  CONSTRAINT `chk_fbs_attr_challenge_status` CHECK (`status` IN ('ISSUED','CONSUMED','EXPIRED')),
  CONSTRAINT `chk_fbs_attr_challenge_time` CHECK (`expires_at` > `issued_at` AND `retention_until` >= DATE_ADD(`expires_at`, INTERVAL 26 HOUR))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务端一次性 host forwarding challenge';

CREATE TABLE IF NOT EXISTS `fbs_attribution_evidence_event` (
  `event_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `receipt_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `challenge_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `contract_id` VARCHAR(96) NOT NULL,
  `server_binding_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `stage` VARCHAR(32) NOT NULL,
  `outcome` VARCHAR(48) NOT NULL,
  `entry_surface` VARCHAR(64) NOT NULL,
  `channel_track` VARCHAR(64) NOT NULL,
  `observed_at` DATETIME(3) NOT NULL,
  `sequence_no` BIGINT NOT NULL,
  `sample_count` INT NOT NULL,
  `canonical_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_fbs_attr_event_receipt` (`receipt_id`),
  KEY `idx_fbs_attr_event_chain` (`challenge_id`,`server_binding_id`,`sequence_no`),
  CONSTRAINT `fk_fbs_attr_event_challenge` FOREIGN KEY (`challenge_id`,`server_binding_id`,`contract_id`) REFERENCES `fbs_host_forwarding_challenge` (`challenge_id`,`server_binding_id`,`contract_id`),
  CONSTRAINT `chk_fbs_attr_event_stage` CHECK (`stage` IN ('whoami','scene_pack','consume','closure')),
  CONSTRAINT `chk_fbs_attr_event_sample` CHECK (`sample_count` = 1),
  CONSTRAINT `chk_fbs_attr_event_entry` CHECK (`entry_surface` = 'official_entry')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独董会有限维度归因证据事件，不存原文';

CREATE TABLE IF NOT EXISTS `fbs_attribution_snapshot` (
  `snapshot_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `contract_id` VARCHAR(96) NOT NULL,
  `window_start` DATETIME(3) NOT NULL,
  `window_end` DATETIME(3) NOT NULL,
  `retention_until` DATETIME(3) NOT NULL,
  `watermark_at` DATETIME(3) NOT NULL,
  `event_high_watermark` BIGINT NOT NULL,
  `row_count` BIGINT NOT NULL,
  `parse_error_count` BIGINT NOT NULL DEFAULT 0,
  `gap_count` BIGINT NOT NULL DEFAULT 0,
  `invalid_count` BIGINT NOT NULL DEFAULT 0,
  `canonicalization_version` VARCHAR(32) NOT NULL,
  `event_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `runtime_release` VARCHAR(191) NOT NULL,
  `embedded_release` VARCHAR(191) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'SEALED_REPORT_ONLY',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`snapshot_id`),
  UNIQUE KEY `uk_fbs_attr_snapshot_window` (`contract_id`,`window_start`,`window_end`),
  CONSTRAINT `fk_fbs_attr_snapshot_contract` FOREIGN KEY (`contract_id`) REFERENCES `fbs_attribution_product_contract` (`contract_id`),
  CONSTRAINT `chk_fbs_attr_snapshot_window` CHECK (`window_end` = DATE_ADD(`window_start`, INTERVAL 24 HOUR)),
  CONSTRAINT `chk_fbs_attr_snapshot_retention` CHECK (`retention_until` >= DATE_ADD(`window_end`, INTERVAL 26 HOUR)),
  CONSTRAINT `chk_fbs_attr_snapshot_quality` CHECK (`parse_error_count` = 0 AND `gap_count` = 0 AND `invalid_count` = 0),
  CONSTRAINT `chk_fbs_attr_snapshot_status` CHECK (`status` = 'SEALED_REPORT_ONLY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独董会 24 小时不可变 report-only 快照';

DROP TRIGGER IF EXISTS `trg_fbs_attr_event_no_update`;
DROP TRIGGER IF EXISTS `trg_fbs_attr_snapshot_no_update`;
DROP TRIGGER IF EXISTS `trg_fbs_attr_event_no_delete`;
DROP TRIGGER IF EXISTS `trg_fbs_attr_snapshot_no_delete`;
DELIMITER $$
CREATE TRIGGER `trg_fbs_attr_event_no_update` BEFORE UPDATE ON `fbs_attribution_evidence_event` FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d evidence events are append-only'; END$$
CREATE TRIGGER `trg_fbs_attr_snapshot_no_update` BEFORE UPDATE ON `fbs_attribution_snapshot` FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d snapshots are immutable'; END$$
CREATE TRIGGER `trg_fbs_attr_event_no_delete` BEFORE DELETE ON `fbs_attribution_evidence_event` FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d evidence events cannot be deleted'; END$$
CREATE TRIGGER `trg_fbs_attr_snapshot_no_delete` BEFORE DELETE ON `fbs_attribution_snapshot` FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d snapshots cannot be deleted'; END$$
DELIMITER ;

INSERT IGNORE INTO `u3w_schema_migration` (`version`,`description`)
VALUES ('20260722_independent_board_attribution_evidence_contract','W4b2d independent board exact product receipt and sealed snapshot contract');
