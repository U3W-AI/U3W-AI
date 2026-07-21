-- W4b.2d 独董会证据合同（default-off / report-only）
-- 仅建立不可变证据底座，不注册公开入口、不写入冻结 26.7.20 包、不产生业务积分。
DELIMITER $$
DROP PROCEDURE IF EXISTS `u3w_assert_fbs_attr_existing_shape_20260722`$$
CREATE PROCEDURE `u3w_assert_fbs_attr_existing_shape_20260722`()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'fbs_attribution_product_contract')
     AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'fbs_attribution_product_contract'
          AND column_name IN ('contract_id','product_id','product_version','host_type','connector_type','entry_surface','package_id','expert_entry_id','registration_status','candidate_enabled','public_route_enabled','authoritative_credit_enabled','created_at')) <> 13 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'W4b2d existing product contract shape is incompatible; manual recovery required';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'fbs_host_forwarding_challenge')
     AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'fbs_host_forwarding_challenge'
          AND column_name IN ('challenge_id','contract_id','server_binding_id','nonce_hash','tenant_subject_digest','issued_at','expires_at','retention_until','status','created_at')) <> 10 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'W4b2d existing challenge shape is incompatible; manual recovery required';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'fbs_attribution_evidence_event')
     AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'fbs_attribution_evidence_event'
          AND column_name IN ('event_id','receipt_id','challenge_id','contract_id','server_binding_id','tenant_subject_digest','stage','outcome','entry_surface','channel_track','observed_at','sequence_no','sample_count','canonical_digest','signer_key_id','issuer','audience','receipt_nonce_hash','receipt_signature','issued_at','expires_at','event_watermark','created_at')) <> 23 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'W4b2d existing evidence event shape is incompatible; manual recovery required';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'fbs_attribution_snapshot')
     AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'fbs_attribution_snapshot'
          AND column_name IN ('snapshot_id','contract_id','window_start','window_end','retention_until','watermark_at','event_high_watermark','row_count','parse_error_count','gap_count','invalid_count','canonicalization_version','event_digest','runtime_release','embedded_release','signer_key_id','issuer','audience','snapshot_signature','status','created_at')) <> 21 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'W4b2d existing snapshot shape is incompatible; manual recovery required';
  END IF;
END$$
CALL `u3w_assert_fbs_attr_existing_shape_20260722`()$$
DROP PROCEDURE `u3w_assert_fbs_attr_existing_shape_20260722`$$
DELIMITER ;

CREATE TABLE IF NOT EXISTS `fbs_attribution_product_contract` (
  `contract_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `product_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `product_version` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `host_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `connector_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `entry_surface` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `package_id` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `expert_entry_id` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `registration_status` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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

DELIMITER $$
DROP PROCEDURE IF EXISTS `u3w_assert_fbs_attr_product_seed_20260722`$$
CREATE PROCEDURE `u3w_assert_fbs_attr_product_seed_20260722`()
BEGIN
  IF EXISTS (SELECT 1 FROM `fbs_attribution_product_contract`
             WHERE `product_id` = 'fbsir-eight-seat-board' AND `product_version` = '26.7.20'
               AND NOT (`contract_id` = 'FBSIR_INDEPENDENT_BOARD_W4B2D'
                        AND `host_type` = 'WORKBUDDY' AND `connector_type` = 'fbs-connector'
                        AND `entry_surface` = 'official_entry'
                        AND `package_id` = '' AND `expert_entry_id` = ''
                        AND `registration_status` = 'PENDING_HOST_REGISTRATION'
                        AND `candidate_enabled` = 0 AND `public_route_enabled` = 0
                        AND `authoritative_credit_enabled` = 0)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'W4b2d product contract seed drift';
  END IF;
END$$
CALL `u3w_assert_fbs_attr_product_seed_20260722`()$$
DROP PROCEDURE `u3w_assert_fbs_attr_product_seed_20260722`$$
DELIMITER ;

INSERT INTO `fbs_attribution_product_contract`
(`contract_id`,`product_id`,`product_version`,`host_type`,`connector_type`,`entry_surface`,`registration_status`)
VALUES ('FBSIR_INDEPENDENT_BOARD_W4B2D','fbsir-eight-seat-board','26.7.20','WORKBUDDY','fbs-connector','official_entry','PENDING_HOST_REGISTRATION')
ON DUPLICATE KEY UPDATE contract_id = VALUES(contract_id);

CREATE TABLE IF NOT EXISTS `fbs_host_forwarding_challenge` (
  `challenge_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `contract_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `server_binding_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `nonce_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `tenant_subject_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issued_at` DATETIME(3) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `retention_until` DATETIME(3) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`challenge_id`),
  UNIQUE KEY `uk_fbs_attr_challenge_binding` (`challenge_id`,`server_binding_id`,`contract_id`),
  UNIQUE KEY `uk_fbs_attr_challenge_tenant_binding` (`challenge_id`,`server_binding_id`,`contract_id`,`tenant_subject_digest`),
  UNIQUE KEY `uk_fbs_attr_challenge_nonce` (`nonce_hash`),
  CONSTRAINT `fk_fbs_attr_challenge_contract` FOREIGN KEY (`contract_id`) REFERENCES `fbs_attribution_product_contract` (`contract_id`),
  CONSTRAINT `chk_fbs_attr_challenge_status` CHECK (`status` IN ('ISSUED','CONSUMED','EXPIRED')),
  CONSTRAINT `chk_fbs_attr_challenge_time` CHECK (`expires_at` > `issued_at` AND `retention_until` >= DATE_ADD(`expires_at`, INTERVAL 26 HOUR))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务端一次性 host forwarding challenge';

CREATE TABLE IF NOT EXISTS `fbs_attribution_evidence_event` (
  `event_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `receipt_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `challenge_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `contract_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `server_binding_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `tenant_subject_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `stage` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `outcome` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `entry_surface` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `channel_track` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `observed_at` DATETIME(3) NOT NULL,
  `sequence_no` BIGINT NOT NULL,
  `sample_count` INT NOT NULL,
  `canonical_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `signer_key_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issuer` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `audience` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `receipt_nonce_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `receipt_signature` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issued_at` DATETIME(3) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `event_watermark` BIGINT NOT NULL AUTO_INCREMENT,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_fbs_attr_event_receipt` (`receipt_id`),
  UNIQUE KEY `uk_fbs_attr_event_nonce` (`receipt_nonce_hash`),
  UNIQUE KEY `uk_fbs_attr_event_watermark` (`event_watermark`),
  KEY `idx_fbs_attr_event_chain` (`challenge_id`,`server_binding_id`,`sequence_no`),
  UNIQUE KEY `uk_fbs_attr_event_stage_sequence` (`challenge_id`,`server_binding_id`,`stage`,`sequence_no`),
  CONSTRAINT `fk_fbs_attr_event_challenge` FOREIGN KEY (`challenge_id`,`server_binding_id`,`contract_id`,`tenant_subject_digest`) REFERENCES `fbs_host_forwarding_challenge` (`challenge_id`,`server_binding_id`,`contract_id`,`tenant_subject_digest`),
  CONSTRAINT `chk_fbs_attr_event_stage` CHECK (`stage` IN ('whoami','scene_pack','consume','closure')),
  CONSTRAINT `chk_fbs_attr_event_outcome` CHECK (`outcome` = 'success'),
  CONSTRAINT `chk_fbs_attr_event_stage_sequence` CHECK ((`stage` = 'whoami' AND `sequence_no` = 1) OR (`stage` = 'scene_pack' AND `sequence_no` = 2) OR (`stage` = 'consume' AND `sequence_no` = 3) OR (`stage` = 'closure' AND `sequence_no` = 4)),
  CONSTRAINT `chk_fbs_attr_event_sample` CHECK (`sample_count` = 1),
  CONSTRAINT `chk_fbs_attr_event_entry` CHECK (`entry_surface` = 'official_entry'),
  CONSTRAINT `chk_fbs_attr_event_time` CHECK (`expires_at` > `issued_at`),
  CONSTRAINT `chk_fbs_attr_event_sequence` CHECK (`sequence_no` BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独董会有限维度归因证据事件，不存原文';

CREATE TABLE IF NOT EXISTS `fbs_attribution_snapshot` (
  `snapshot_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `contract_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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
  `signer_key_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issuer` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `audience` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `snapshot_signature` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'SEALED_REPORT_ONLY',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`snapshot_id`),
  UNIQUE KEY `uk_fbs_attr_snapshot_window` (`contract_id`,`window_start`,`window_end`),
  CONSTRAINT `fk_fbs_attr_snapshot_contract` FOREIGN KEY (`contract_id`) REFERENCES `fbs_attribution_product_contract` (`contract_id`),
  CONSTRAINT `chk_fbs_attr_snapshot_window` CHECK (`window_end` = DATE_ADD(`window_start`, INTERVAL 24 HOUR)),
  CONSTRAINT `chk_fbs_attr_snapshot_retention` CHECK (`retention_until` >= DATE_ADD(`window_end`, INTERVAL 26 HOUR)),
  CONSTRAINT `chk_fbs_attr_snapshot_quality` CHECK (`row_count` >= 0 AND `event_high_watermark` >= 0 AND `parse_error_count` = 0 AND `gap_count` = 0 AND `invalid_count` = 0),
  CONSTRAINT `chk_fbs_attr_snapshot_watermark` CHECK (`event_high_watermark` >= `row_count`),
  CONSTRAINT `chk_fbs_attr_snapshot_status` CHECK (`status` = 'SEALED_REPORT_ONLY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独董会 24 小时不可变 report-only 快照';

DROP TRIGGER IF EXISTS `trg_fbs_attr_product_no_update`;
DROP TRIGGER IF EXISTS `trg_fbs_attr_product_no_delete`;
DROP TRIGGER IF EXISTS `trg_fbs_attr_challenge_immutable_fields`;
DROP TRIGGER IF EXISTS `trg_fbs_attr_event_no_update`;
DROP TRIGGER IF EXISTS `trg_fbs_attr_snapshot_no_update`;
DROP TRIGGER IF EXISTS `trg_fbs_attr_event_no_delete`;
DROP TRIGGER IF EXISTS `trg_fbs_attr_snapshot_no_delete`;
DELIMITER $$
CREATE TRIGGER `trg_fbs_attr_product_no_update` BEFORE UPDATE ON `fbs_attribution_product_contract` FOR EACH ROW BEGIN IF NOT (OLD.contract_id <=> NEW.contract_id AND OLD.product_id <=> NEW.product_id AND OLD.product_version <=> NEW.product_version AND OLD.host_type <=> NEW.host_type AND OLD.connector_type <=> NEW.connector_type AND OLD.entry_surface <=> NEW.entry_surface AND OLD.package_id <=> NEW.package_id AND OLD.expert_entry_id <=> NEW.expert_entry_id AND OLD.registration_status <=> NEW.registration_status AND OLD.candidate_enabled <=> NEW.candidate_enabled AND OLD.public_route_enabled <=> NEW.public_route_enabled AND OLD.authoritative_credit_enabled <=> NEW.authoritative_credit_enabled AND OLD.created_at <=> NEW.created_at) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d product contracts are immutable; insert a successor'; END IF; END$$
CREATE TRIGGER `trg_fbs_attr_product_no_delete` BEFORE DELETE ON `fbs_attribution_product_contract` FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d product contracts cannot be deleted'; END$$
CREATE TRIGGER `trg_fbs_attr_challenge_immutable_fields` BEFORE UPDATE ON `fbs_host_forwarding_challenge` FOR EACH ROW BEGIN IF OLD.challenge_id <> NEW.challenge_id OR OLD.contract_id <> NEW.contract_id OR OLD.server_binding_id <> NEW.server_binding_id OR OLD.nonce_hash <> NEW.nonce_hash OR OLD.tenant_subject_digest <> NEW.tenant_subject_digest OR OLD.issued_at <> NEW.issued_at OR OLD.expires_at <> NEW.expires_at OR OLD.retention_until <> NEW.retention_until OR OLD.created_at <> NEW.created_at OR OLD.status NOT IN ('ISSUED','CONSUMED','EXPIRED') OR (OLD.status = 'CONSUMED' AND NEW.status <> 'CONSUMED') OR (OLD.status = 'EXPIRED' AND NEW.status <> 'EXPIRED') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d challenge immutable fields or status transition invalid'; END IF; END$$
CREATE TRIGGER `trg_fbs_attr_event_no_update` BEFORE UPDATE ON `fbs_attribution_evidence_event` FOR EACH ROW BEGIN IF NOT (OLD.event_id <=> NEW.event_id AND OLD.receipt_id <=> NEW.receipt_id AND OLD.challenge_id <=> NEW.challenge_id AND OLD.contract_id <=> NEW.contract_id AND OLD.server_binding_id <=> NEW.server_binding_id AND OLD.tenant_subject_digest <=> NEW.tenant_subject_digest AND OLD.stage <=> NEW.stage AND OLD.outcome <=> NEW.outcome AND OLD.entry_surface <=> NEW.entry_surface AND OLD.channel_track <=> NEW.channel_track AND OLD.observed_at <=> NEW.observed_at AND OLD.sequence_no <=> NEW.sequence_no AND OLD.sample_count <=> NEW.sample_count AND OLD.canonical_digest <=> NEW.canonical_digest AND OLD.signer_key_id <=> NEW.signer_key_id AND OLD.issuer <=> NEW.issuer AND OLD.audience <=> NEW.audience AND OLD.receipt_nonce_hash <=> NEW.receipt_nonce_hash AND OLD.receipt_signature <=> NEW.receipt_signature AND OLD.issued_at <=> NEW.issued_at AND OLD.expires_at <=> NEW.expires_at AND OLD.event_watermark <=> NEW.event_watermark AND OLD.created_at <=> NEW.created_at) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d evidence events are append-only'; END IF; END$$
CREATE TRIGGER `trg_fbs_attr_snapshot_no_update` BEFORE UPDATE ON `fbs_attribution_snapshot` FOR EACH ROW BEGIN IF NOT (OLD.snapshot_id <=> NEW.snapshot_id AND OLD.contract_id <=> NEW.contract_id AND OLD.window_start <=> NEW.window_start AND OLD.window_end <=> NEW.window_end AND OLD.retention_until <=> NEW.retention_until AND OLD.watermark_at <=> NEW.watermark_at AND OLD.event_high_watermark <=> NEW.event_high_watermark AND OLD.row_count <=> NEW.row_count AND OLD.parse_error_count <=> NEW.parse_error_count AND OLD.gap_count <=> NEW.gap_count AND OLD.invalid_count <=> NEW.invalid_count AND OLD.canonicalization_version <=> NEW.canonicalization_version AND OLD.event_digest <=> NEW.event_digest AND OLD.runtime_release <=> NEW.runtime_release AND OLD.embedded_release <=> NEW.embedded_release AND OLD.signer_key_id <=> NEW.signer_key_id AND OLD.issuer <=> NEW.issuer AND OLD.audience <=> NEW.audience AND OLD.snapshot_signature <=> NEW.snapshot_signature AND OLD.status <=> NEW.status AND OLD.created_at <=> NEW.created_at) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d snapshots are immutable'; END IF; END$$
CREATE TRIGGER `trg_fbs_attr_event_no_delete` BEFORE DELETE ON `fbs_attribution_evidence_event` FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d evidence events cannot be deleted'; END$$
CREATE TRIGGER `trg_fbs_attr_snapshot_no_delete` BEFORE DELETE ON `fbs_attribution_snapshot` FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='W4b2d snapshots cannot be deleted'; END$$
DELIMITER ;

INSERT INTO `u3w_schema_migration` (`version`,`description`)
VALUES ('20260722_independent_board_attribution_evidence_contract','APPLIED:W4b2d independent board exact product receipt and sealed snapshot contract')
ON DUPLICATE KEY UPDATE description = IF(description = VALUES(description), description, NULL);
