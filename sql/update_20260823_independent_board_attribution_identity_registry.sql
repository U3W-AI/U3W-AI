-- public_init_044
-- Exact successor for the W1A attribution host/version registry.
-- This migration is additive with respect to accepted identities: it keeps the
-- historical WorkBuddy replay tuple and adds only the reviewed 26.8.19 tuples.
-- It never changes event rows, journey rows, product credit, or immutable
-- triggers. Re-running is safe because each ALTER atomically replaces the same
-- named CHECK with the exact successor expression.

SET @u3w_board_attr_identity_registry_lock_name =
  CONCAT('w05:', LEFT(SHA2(CONCAT(
    DATABASE(), ':20260823_board_attr_identity_registry_v1'), 256), 60));
SELECT GET_LOCK(@u3w_board_attr_identity_registry_lock_name, 10)
  INTO @u3w_board_attr_identity_registry_lock_acquired;

DELIMITER $$
DROP PROCEDURE IF EXISTS `u3w_migrate_board_attr_identity_registry_20260823`$$
CREATE PROCEDURE `u3w_migrate_board_attr_identity_registry_20260823`()
BEGIN
  DECLARE predecessor_receipts INT DEFAULT 0;
  DECLARE target_tables INT DEFAULT 0;
  DECLARE target_constraints INT DEFAULT 0;
  DECLARE constraint_contract_sha CHAR(64) DEFAULT '';
  DECLARE identity_index_columns TEXT DEFAULT '';

  IF @u3w_board_attr_identity_registry_lock_acquired <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'W0.5 attribution identity-registry lock unavailable';
  END IF;

  SELECT COUNT(*) INTO predecessor_receipts
  FROM `u3w_schema_migration`
  WHERE (`version` = 'public_init_043'
      AND `description` =
        'APPLIED:Independent Board exact official experts attribution v1')
     OR (`version` =
          '20260723_independent_board_attribution_v1_043'
      AND `description` =
        'APPLIED:exact WorkBuddy experts 26.7.21 attribution journey and append-only event ledger');
  IF predecessor_receipts <> 2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'W0.5 exact public_init_043 predecessor missing';
  END IF;

  SELECT COUNT(*) INTO target_tables
  FROM `information_schema`.`tables`
  WHERE `table_schema` = DATABASE()
    AND `table_type` = 'BASE TABLE'
    AND `engine` = 'InnoDB'
    AND `table_name` IN
      ('fbs_board_attr_journey_v1', 'fbs_board_attr_event_v1');
  IF target_tables <> 2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'W0.5 attribution target tables missing or drifted';
  END IF;

  SELECT COUNT(*) INTO target_constraints
  FROM `information_schema`.`table_constraints`
  WHERE `constraint_schema` = DATABASE()
    AND `constraint_type` = 'CHECK'
    AND ((`table_name` = 'fbs_board_attr_journey_v1'
          AND `constraint_name` = 'chk_board_attr_journey_versions')
      OR (`table_name` = 'fbs_board_attr_event_v1'
          AND `constraint_name` = 'chk_board_attr_event_versions'));
  IF target_constraints <> 2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'W0.5 predecessor version constraints missing';
  END IF;

  SELECT SHA2(CONCAT(GROUP_CONCAT(
    CONCAT(tc.`table_name`, '|', cc.`check_clause`)
    ORDER BY tc.`table_name`, tc.`constraint_name` SEPARATOR '\n'), '\n'), 256)
    INTO constraint_contract_sha
  FROM `information_schema`.`table_constraints` tc
  INNER JOIN `information_schema`.`check_constraints` cc
    ON cc.`constraint_schema` = tc.`constraint_schema`
   AND cc.`constraint_name` = tc.`constraint_name`
  WHERE tc.`constraint_schema` = DATABASE()
    AND tc.`constraint_name` IN
      ('chk_board_attr_journey_versions', 'chk_board_attr_event_versions');
  IF constraint_contract_sha NOT IN (
      'd58530b0728c929370fed784297cfb67cab29ccfa1f6d46b4b720e77fc830dc5',
      'b4a819eb9050889bdcc6f01a9d86163663ef5eeea603a378fe5d706a30d2e717',
      'e1a3fff0186f4b0fbf099c36283ebcb7ca6ba1116a47a04f4bfbd8a6a99ebe71',
      'e4874cace1b0c1211be58e1ad650371db62e4f49e12e2faf8012f75bd3902f6d') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'W0.5 predecessor CHECK contract is not exact or recoverable';
  END IF;

  SELECT GROUP_CONCAT(`column_name`
      ORDER BY `seq_in_index` SEPARATOR ',')
    INTO identity_index_columns
  FROM `information_schema`.`statistics`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'fbs_board_attr_journey_v1'
    AND `index_name` = 'uk_board_attr_journey_identity'
    AND `non_unique` = 0;
  IF identity_index_columns NOT IN (
      'contract_id,tenant_subject_digest,server_binding_id,journey_id',
      'contract_id,tenant_subject_digest,server_binding_id,journey_id,product_id,listed_manifest_version,embedded_contract_version') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'W0.5 predecessor journey identity index is not exact';
  END IF;

  ALTER TABLE `fbs_board_attr_journey_v1`
    DROP CHECK `chk_board_attr_journey_versions`,
    ADD CONSTRAINT `chk_board_attr_journey_versions`
      CHECK (
        (`listed_manifest_version` = '26.7.21'
          AND `embedded_contract_version` = '26.7.20')
        OR (`listed_manifest_version` = '26.8.19'
          AND `embedded_contract_version` = '26.8.19'));

  -- sameBindingKey includes product and listed version. Keep the secondary
  -- identity unique key aligned so a legitimate cross-version journey cannot
  -- collide with its historical predecessor under a different primary key.
  ALTER TABLE `fbs_board_attr_journey_v1`
    DROP INDEX `uk_board_attr_journey_identity`,
    ADD UNIQUE KEY `uk_board_attr_journey_identity`
      (`contract_id`, `tenant_subject_digest`, `server_binding_id`,
       `journey_id`, `product_id`, `listed_manifest_version`,
       `embedded_contract_version`);

  ALTER TABLE `fbs_board_attr_event_v1`
    DROP CHECK `chk_board_attr_event_versions`,
    ADD CONSTRAINT `chk_board_attr_event_versions`
      CHECK (
        (`host_client_family` = 'WORKBUDDY'
          AND `listed_manifest_version` = '26.7.21'
          AND `embedded_contract_version` = '26.7.20')
        OR (`host_client_family` IN ('WORKBUDDY', 'WORKBUDDYAI')
          AND `listed_manifest_version` = '26.8.19'
          AND `embedded_contract_version` = '26.8.19'));

  INSERT INTO `u3w_schema_migration` (`version`, `description`)
  VALUES (
    '20260823_independent_board_attribution_identity_registry_044',
    'APPLIED:exact legacy and current WorkBuddy attribution identity registry')
  ON DUPLICATE KEY UPDATE
    `description` = IF(
      `description` = VALUES(`description`), `description`, NULL);

  SELECT COUNT(*) INTO target_constraints
  FROM `information_schema`.`table_constraints`
  WHERE `constraint_schema` = DATABASE()
    AND `constraint_type` = 'CHECK'
    AND `enforced` = 'YES'
    AND ((`table_name` = 'fbs_board_attr_journey_v1'
          AND `constraint_name` = 'chk_board_attr_journey_versions')
      OR (`table_name` = 'fbs_board_attr_event_v1'
          AND `constraint_name` = 'chk_board_attr_event_versions'));
  IF target_constraints <> 2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'W0.5 successor version constraints not enforced';
  END IF;
END$$

CALL `u3w_migrate_board_attr_identity_registry_20260823`()$$
DROP PROCEDURE `u3w_migrate_board_attr_identity_registry_20260823`$$
DELIMITER ;

SELECT RELEASE_LOCK(@u3w_board_attr_identity_registry_lock_name)
  INTO @u3w_board_attr_identity_registry_lock_released;
