-- Wave 1 official WorkBuddy experts attribution spine.
-- Exact listed identity: fbsir-eight-seat-board@26.7.21.
-- Historical public_init_037 and its 26.7.20 listed contract remain immutable.

CREATE TABLE IF NOT EXISTS `fbs_board_attr_journey_v1` (
  `same_binding_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `contract_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `tenant_subject_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `server_binding_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `journey_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `product_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `listed_manifest_version` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `embedded_contract_version` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `channel` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `terminal` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `host_version` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `traffic_class` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `intent_family` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNKNOWN',
  `last_sequence_no` BIGINT NOT NULL DEFAULT 0,
  `last_event_digest` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `head_version` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`same_binding_key`),
  UNIQUE KEY `uk_board_attr_journey_identity`
    (`contract_id`,`tenant_subject_digest`,`server_binding_id`,`journey_id`),
  CONSTRAINT `chk_board_attr_journey_contract`
    CHECK (`contract_id` = 'FBSIR_INDEPENDENT_BOARD_W1A_V1'),
  CONSTRAINT `chk_board_attr_journey_product`
    CHECK (`product_id` = 'fbsir-eight-seat-board'),
  CONSTRAINT `chk_board_attr_journey_versions`
    CHECK (`listed_manifest_version` = '26.7.21'
      AND `embedded_contract_version` = '26.7.20'),
  CONSTRAINT `chk_board_attr_journey_channel`
    CHECK (`channel` IN ('OFFICIAL_EXPERTS','UNKNOWN')),
  CONSTRAINT `chk_board_attr_journey_terminal`
    CHECK (`terminal` IN
      ('WORKBUDDY_WINDOWS','WORKBUDDY_MACOS','WORKBUDDYAI','UNKNOWN')),
  CONSTRAINT `chk_board_attr_journey_traffic`
    CHECK (`traffic_class` IN
      ('NATURAL','PROBE','DIAGNOSTIC','SYNTHETIC','UNKNOWN')),
  CONSTRAINT `chk_board_attr_journey_intent`
    CHECK (`intent_family` IN
      ('OPERATING_DIAGNOSIS','STRATEGY_TRANSITION','CAPITAL_TRANSACTION',
       'GROWTH_CHANNEL','ORGANIZATION_SUCCESSION','CROSS_BORDER_EXPANSION',
       'DIGITAL_AI_TRANSFORMATION','OTHER','UNKNOWN')),
  CONSTRAINT `chk_board_attr_journey_sequence`
    CHECK (`last_sequence_no` BETWEEN 0 AND 3
      AND `head_version` = `last_sequence_no`
      AND ((`last_sequence_no` = 0 AND `last_event_digest` = '')
        OR (`last_sequence_no` > 0
          AND `last_event_digest` REGEXP '^[0-9a-f]{64}$')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Wave 1 mutable sequence head for exact official experts traffic';

CREATE TABLE IF NOT EXISTS `fbs_board_attr_event_v1` (
  `event_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `receipt_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `same_binding_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `contract_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `journey_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `server_binding_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `tenant_subject_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `sequence_no` BIGINT NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL,
  `product_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `package_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `agent_name` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `marketplace` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `listed_surface` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `listed_manifest_version` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `embedded_contract_version` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `host_client_family` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `host_version` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `terminal` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `channel` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_source` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `intent_signal` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `intent_family` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `classification_source` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `classifier_version` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `confidence_bucket` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `review_mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `traffic_class` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `traffic_authority` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `outcome` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `previous_event_digest` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `traceparent` VARCHAR(55) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
  `canonical_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `signer_key_id` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `signature_hex` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `nonce_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issued_at` DATETIME(3) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `authoritative_product_credit` TINYINT NOT NULL DEFAULT 0,
  `event_watermark` BIGINT NOT NULL AUTO_INCREMENT,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_board_attr_event_receipt` (`receipt_id`),
  UNIQUE KEY `uk_board_attr_event_nonce` (`nonce_hash`),
  UNIQUE KEY `uk_board_attr_event_digest` (`event_digest`),
  UNIQUE KEY `uk_board_attr_event_watermark` (`event_watermark`),
  UNIQUE KEY `uk_board_attr_event_binding_sequence`
    (`same_binding_key`,`sequence_no`),
  KEY `idx_board_attr_event_window`
    (`occurred_at`,`traffic_class`,`event_type`),
  KEY `idx_board_attr_event_dimensions`
    (`product_id`,`listed_manifest_version`,`channel`,`terminal`,
     `intent_family`,`review_mode`),
  CONSTRAINT `fk_board_attr_event_journey`
    FOREIGN KEY (`same_binding_key`)
    REFERENCES `fbs_board_attr_journey_v1` (`same_binding_key`),
  CONSTRAINT `chk_board_attr_event_contract`
    CHECK (`contract_id` = 'FBSIR_INDEPENDENT_BOARD_W1A_V1'),
  CONSTRAINT `chk_board_attr_event_product`
    CHECK (`product_id` = 'fbsir-eight-seat-board'
      AND `package_id` = 'fbsir-eight-seat-board'
      AND `agent_name` = 'board-convener'
      AND `marketplace` = 'experts'
      AND `listed_surface` = 'listed_runtime_state'),
  CONSTRAINT `chk_board_attr_event_versions`
    CHECK (`listed_manifest_version` = '26.7.21'
      AND `embedded_contract_version` = '26.7.20'),
  CONSTRAINT `chk_board_attr_event_sequence`
    CHECK ((`event_type` = 'ENTRY_OBSERVED' AND `sequence_no` = 1)
      OR (`event_type` = 'INTENT_CLASSIFIED' AND `sequence_no` = 2)
      OR (`event_type` = 'FIRST_VALUE_COMPLETED' AND `sequence_no` = 3)),
  CONSTRAINT `chk_board_attr_event_previous`
    CHECK ((`sequence_no` = 1 AND `previous_event_digest` = '')
      OR (`sequence_no` > 1
        AND `previous_event_digest` REGEXP '^[0-9a-f]{64}$')),
  CONSTRAINT `chk_board_attr_event_intent`
    CHECK (`intent_family` IN
      ('OPERATING_DIAGNOSIS','STRATEGY_TRANSITION','CAPITAL_TRANSACTION',
       'GROWTH_CHANNEL','ORGANIZATION_SUCCESSION','CROSS_BORDER_EXPANSION',
       'DIGITAL_AI_TRANSFORMATION','OTHER','UNKNOWN')),
  CONSTRAINT `chk_board_attr_event_traffic`
    CHECK (`traffic_class` IN
      ('NATURAL','PROBE','DIAGNOSTIC','SYNTHETIC','UNKNOWN')),
  CONSTRAINT `chk_board_attr_event_outcome`
    CHECK (`outcome` IN ('SUCCESS','FAILED','WITHHELD')),
  CONSTRAINT `chk_board_attr_event_time`
    CHECK (`expires_at` > `issued_at`),
  CONSTRAINT `chk_board_attr_event_product_credit`
    CHECK (`authoritative_product_credit` = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only content-free Wave 1 six-dimensional attribution events';

DROP TRIGGER IF EXISTS `trg_board_attr_event_v1_no_update`;
DROP TRIGGER IF EXISTS `trg_board_attr_event_v1_no_delete`;
DELIMITER $$
CREATE TRIGGER `trg_board_attr_event_v1_no_update`
BEFORE UPDATE ON `fbs_board_attr_event_v1`
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'Wave 1 attribution events are append-only';
END$$
CREATE TRIGGER `trg_board_attr_event_v1_no_delete`
BEFORE DELETE ON `fbs_board_attr_event_v1`
FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'Wave 1 attribution events cannot be deleted';
END$$
DELIMITER ;

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,
   `is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,
   `create_by`,`create_time`,`update_by`,`update_time`,`remark`)
SELECT
  'Attribution summary', root.`menu_id`, 99, '', NULL, NULL, '',
  1, 0, 'F', '0', '0', 'board:attribution:query', '#',
  'admin', CURRENT_TIMESTAMP, '', NULL,
  'Bounded read-only Wave 1 attribution aggregate permission'
FROM `sys_menu` root
WHERE root.`parent_id` = 0
  AND root.`path` = 'independent-board-admin'
  AND root.`route_name` = 'IndependentBoardAdmin'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_menu`
    WHERE BINARY `perms` = BINARY 'board:attribution:query'
  );

DELIMITER $$
DROP PROCEDURE IF EXISTS `u3w_assert_board_attr_v1_permission_20260723`$$
CREATE PROCEDURE `u3w_assert_board_attr_v1_permission_20260723`()
BEGIN
  IF (SELECT COUNT(*) FROM `sys_menu`
      WHERE BINARY `perms` = BINARY 'board:attribution:query') <> 1
     OR (SELECT COUNT(*)
         FROM `sys_menu` permission
         INNER JOIN `sys_menu` root
           ON root.`menu_id` = permission.`parent_id`
         WHERE BINARY permission.`perms`
                 = BINARY 'board:attribution:query'
           AND root.`parent_id` = 0
           AND root.`path` = 'independent-board-admin'
           AND root.`route_name` = 'IndependentBoardAdmin') <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT =
        'Wave 1 attribution read permission is missing or ambiguous';
  END IF;
END$$
CALL `u3w_assert_board_attr_v1_permission_20260723`()$$
DROP PROCEDURE `u3w_assert_board_attr_v1_permission_20260723`$$
DELIMITER ;

INSERT INTO `u3w_schema_migration` (`version`,`description`)
VALUES (
  '20260723_independent_board_attribution_v1_043',
  'APPLIED:exact WorkBuddy experts 26.7.21 attribution journey and append-only event ledger'
)
ON DUPLICATE KEY UPDATE
  description = IF(description = VALUES(description), description, NULL);
