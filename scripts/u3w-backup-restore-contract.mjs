import path from "node:path";

export const BACKUP_SCHEMA = "fbsir.u3wDatabaseBackupReceipt.v4";
export const RESTORE_SCHEMA =
  "fbsir.u3wDatabaseRestoreRehearsalReceipt.v4";
export const BUNDLE_SCHEMA =
  "fbsir.u3wDatabaseBackupRestoreBundleReceipt.v3";
export const W1A_SCHEMA_FINGERPRINT_SHA256 =
  "fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d";

const TARGET_HOST = "api2.u3w.com";
const DATABASE = "fbsir";
const BACKUP_ROOT = "/opt/fbsir/admin/backups/w1a";
const FACTS_ALGORITHM = "u3w.mysql-schema-metadata.v2";
const RUN_ID = /^w1a-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$/;
const SHA256 = /^[0-9a-f]{64}$/;
const COMMIT = /^[0-9a-f]{40}$/;
const MYSQL_VERSION = /^[0-9]+\.[0-9]+\.[0-9]+$/;
const UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ISO_INSTANT =
  /^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,6})?Z$/;

function validInstant(value) {
  return ISO_INSTANT.test(value ?? "") && Number.isFinite(Date.parse(value));
}

const FACT_FIELDS = Object.freeze([
  "fingerprintAlgorithm",
  "schemaFingerprintSha256",
  "prerequisiteShapeSha256",
  "baseTableCount",
  "viewCount",
  "triggerCount",
  "routineCount",
  "eventCount",
  "independentBoardAdminRootCount",
  "legacyAdminRootDependencyState",
  "legacyAdminRootDependencyFactsSha256",
  "legacyAdminRootDependencyVersionCount",
  "legacyAdminRootDependencyReceiptCount",
  "legacyAdminRootIdentityCount",
  "legacyAdminRootExactCount",
  "legacyAdminRootRoleBindingCount",
  "legacyAdminRootPageChildCount",
  "legacyForbiddenPublicInit001Through042ReceiptCount",
  "publicInit043AnyReceiptCount",
  "attributionInternalReceiptCount",
  "attributionTableCount",
  "attributionTriggerCount",
  "attributionPermissionCount",
  "attributionEventCount",
  "attributionProbeEventCount",
  "attributionNaturalEventCount",
  "attributionNonProbeEventCount",
  "attributionAuthoritativeProductCreditCount",
  "attributionJourneyCount",
  "attributionProbeJourneyCount",
  "attributionNaturalJourneyCount",
  "attributionNonProbeJourneyCount",
  "w1aSchemaFingerprintSha256",
  "w1a043State",
  "allBaseTablesInnoDB",
]);
const ATTRIBUTION_DATA_FACT_FIELDS = new Set([
  "attributionEventCount",
  "attributionProbeEventCount",
  "attributionNaturalEventCount",
  "attributionNonProbeEventCount",
  "attributionAuthoritativeProductCreditCount",
  "attributionJourneyCount",
  "attributionProbeJourneyCount",
  "attributionNaturalJourneyCount",
  "attributionNonProbeJourneyCount",
]);

const BACKUP_FIELDS = Object.freeze([
  "schema",
  "runId",
  "sourceCommit",
  "planReceiptSha256",
  "targetHost",
  "database",
  "sourceDatabaseServerUuid",
  "serverVersion",
  "serverVersionComment",
  "generatedAt",
  "backupPath",
  "backupSha256",
  "backupSizeBytes",
  "backupPlaintextSha256",
  "encryptionContract",
  "encryptionKeyFingerprintSha256",
  "dumpToolVersion",
  "dumpOptionsContract",
  "ddlProtectionMode",
  "sourceFacts",
  "sourceTotalRows",
  "sourceTableRowCountsSha256",
  "sourceSnapshotExactlyMatched",
  "sourceJarSha256",
  "adminRootDependencyAdoptionReceiptSha256",
  "approvalReceiptSha256",
  "runnerSha256",
  "backupWorkerSha256",
  "businessDatabaseChanged",
  "serviceChanged",
  "officialExpertsPackageChanged",
]);

const RESTORE_FIELDS = Object.freeze([
  "schema",
  "runId",
  "sourceCommit",
  "planReceiptSha256",
  "targetHost",
  "database",
  "sourceDatabaseServerUuid",
  "sourceBackupPath",
  "sourceBackupSha256",
  "sourceBackupPlaintextSha256",
  "sourceBackupReceiptSha256",
  "startedAt",
  "completedAt",
  "isolatedTarget",
  "isolatedNetworkingDisabled",
  "isolatedDataRemoved",
  "serverVersion",
  "serverVersionComment",
  "restoredFacts",
  "restoredTotalRows",
  "restoredTableRowCountsSha256",
  "restoreLogPath",
  "restoreLogSha256",
  "isolationEvidencePath",
  "isolationEvidenceSha256",
  "mysqlcheckPath",
  "mysqlcheckSha256",
  "adminRootDependencyAdoptionReceiptSha256",
  "approvalReceiptSha256",
  "backupWorkerSha256",
  "verifierSha256",
  "businessDatabaseChanged",
  "serviceChanged",
  "officialExpertsPackageChanged",
]);

const BUNDLE_FIELDS = Object.freeze([
  "schema",
  "runId",
  "sourceCommit",
  "planReceiptSha256",
  "targetHost",
  "database",
  "sourceDatabaseServerUuid",
  "generatedAt",
  "backupReceiptPath",
  "backupReceiptSha256",
  "restoreReceiptPath",
  "restoreReceiptSha256",
  "backupPath",
  "backupSha256",
  "backupSizeBytes",
  "approvalReceiptSha256",
  "runnerSha256",
  "backupWorkerSha256",
  "verifierSha256",
  "adminRootDependencyAdoptionReceiptSha256",
  "productionBusinessStateChanged",
]);

function isPlainObject(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return false;
  }
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

function sortForCanonicalJson(value) {
  if (Array.isArray(value)) {
    return value.map(sortForCanonicalJson);
  }
  if (isPlainObject(value)) {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, sortForCanonicalJson(value[key])]),
    );
  }
  return value;
}

export function canonicalJson(value) {
  return JSON.stringify(sortForCanonicalJson(value));
}

function staticControlFacts(value) {
  return Object.fromEntries(
    Object.entries(value).filter(
      ([field]) => !ATTRIBUTION_DATA_FACT_FIELDS.has(field),
    ),
  );
}

function attributionObservationsMonotonic(recordedFacts, currentFacts) {
  return (
    recordedFacts.w1a043State === currentFacts.w1a043State &&
    [...ATTRIBUTION_DATA_FACT_FIELDS].every(
      (field) => currentFacts[field] >= recordedFacts[field],
    )
  );
}

function exactFields(value, expected) {
  if (!isPlainObject(value)) {
    return false;
  }
  const actual = Object.keys(value).sort();
  const required = [...expected].sort();
  return (
    actual.length === required.length &&
    actual.every((field, index) => field === required[index])
  );
}

function safeIntegerAtLeast(value, minimum) {
  return Number.isSafeInteger(value) && value >= minimum;
}

function expectedRunDirectory(runId) {
  return path.posix.join(BACKUP_ROOT, runId);
}

function pathBoundToRun(candidate, runId, filename) {
  if (typeof candidate !== "string" || !RUN_ID.test(runId)) {
    return false;
  }
  return (
    path.posix.normalize(candidate) ===
    path.posix.join(expectedRunDirectory(runId), filename)
  );
}

function validateFacts(value, prefix, errors) {
  if (!exactFields(value, FACT_FIELDS)) {
    errors.push(`${prefix}_facts_unknown_fields`);
    return;
  }
  if (value.fingerprintAlgorithm !== FACTS_ALGORITHM) {
    errors.push(`${prefix}_facts_algorithm_invalid`);
  }
  if (!SHA256.test(value.schemaFingerprintSha256 ?? "")) {
    errors.push(`${prefix}_schema_fingerprint_invalid`);
  }
  if (!SHA256.test(value.prerequisiteShapeSha256 ?? "")) {
    errors.push(`${prefix}_prerequisite_shape_invalid`);
  }
  for (const field of [
    "baseTableCount",
    "viewCount",
    "triggerCount",
    "routineCount",
    "eventCount",
    "independentBoardAdminRootCount",
    "legacyAdminRootDependencyVersionCount",
    "legacyAdminRootDependencyReceiptCount",
    "legacyAdminRootIdentityCount",
    "legacyAdminRootExactCount",
    "legacyAdminRootRoleBindingCount",
    "legacyAdminRootPageChildCount",
    "legacyForbiddenPublicInit001Through042ReceiptCount",
    "publicInit043AnyReceiptCount",
    "attributionInternalReceiptCount",
    "attributionTableCount",
    "attributionTriggerCount",
    "attributionPermissionCount",
    "attributionEventCount",
    "attributionProbeEventCount",
    "attributionNaturalEventCount",
    "attributionNonProbeEventCount",
    "attributionAuthoritativeProductCreditCount",
    "attributionJourneyCount",
    "attributionProbeJourneyCount",
    "attributionNaturalJourneyCount",
    "attributionNonProbeJourneyCount",
  ]) {
    if (!safeIntegerAtLeast(value[field], 0)) {
      errors.push(`${prefix}_${field}_invalid`);
    }
  }
  if (value.baseTableCount < 1) {
    errors.push(`${prefix}_base_tables_empty`);
  }
  if (
    !SHA256.test(value.legacyAdminRootDependencyFactsSha256 ?? "") ||
    value.legacyAdminRootDependencyState !==
      "EXACT_CONTROLLED_DEPENDENCY" ||
    value.independentBoardAdminRootCount !== 1 ||
    value.legacyAdminRootDependencyVersionCount !== 1 ||
    value.legacyAdminRootDependencyReceiptCount !== 1 ||
    value.legacyAdminRootIdentityCount !== 1 ||
    value.legacyAdminRootExactCount !== 1 ||
    value.legacyAdminRootRoleBindingCount !== 0 ||
    value.legacyAdminRootPageChildCount !== 0 ||
    value.legacyForbiddenPublicInit001Through042ReceiptCount !== 0
  ) {
    errors.push(`${prefix}_admin_root_dependency_invalid`);
  }
  const exact043State =
    (
      value.w1a043State === "ABSENT" &&
      value.publicInit043AnyReceiptCount === 0 &&
      value.attributionInternalReceiptCount === 0 &&
      value.attributionTableCount === 0 &&
      value.attributionTriggerCount === 0 &&
      value.attributionPermissionCount === 0 &&
      value.attributionEventCount === 0 &&
      value.attributionProbeEventCount === 0 &&
      value.attributionNaturalEventCount === 0 &&
      value.attributionNonProbeEventCount === 0 &&
      value.attributionAuthoritativeProductCreditCount === 0 &&
      value.attributionJourneyCount === 0 &&
      value.attributionProbeJourneyCount === 0 &&
      value.attributionNaturalJourneyCount === 0 &&
      value.attributionNonProbeJourneyCount === 0 &&
      value.w1aSchemaFingerprintSha256 === null
    ) ||
    (
      value.w1a043State === "EXACT_043_RETAINED_DORMANT" &&
      value.publicInit043AnyReceiptCount === 1 &&
      value.attributionInternalReceiptCount === 1 &&
      value.attributionTableCount === 2 &&
      value.attributionTriggerCount === 2 &&
      value.attributionPermissionCount === 1 &&
      value.attributionEventCount === value.attributionProbeEventCount &&
      value.attributionNaturalEventCount === 0 &&
      value.attributionNonProbeEventCount === 0 &&
      value.attributionAuthoritativeProductCreditCount === 0 &&
      value.attributionJourneyCount ===
        value.attributionProbeJourneyCount &&
      value.attributionNaturalJourneyCount === 0 &&
      value.attributionNonProbeJourneyCount === 0 &&
      value.w1aSchemaFingerprintSha256 ===
        W1A_SCHEMA_FINGERPRINT_SHA256
    );
  if (!exact043State) {
    errors.push(`${prefix}_w1a_043_prestate_invalid`);
  }
  if (value.allBaseTablesInnoDB !== true) {
    errors.push(
      prefix === "source"
        ? "source_tables_not_all_innodb"
        : `${prefix}_tables_not_all_innodb`,
    );
  }
}

export function validateBackupReceipt(receipt) {
  const errors = [];
  if (!exactFields(receipt, BACKUP_FIELDS)) {
    return { ok: false, errors: ["backup_receipt_unknown_fields"] };
  }
  if (receipt.schema !== BACKUP_SCHEMA) {
    errors.push("backup_schema_invalid");
  }
  if (!RUN_ID.test(receipt.runId ?? "")) {
    errors.push("backup_run_id_invalid");
  }
  if (!COMMIT.test(receipt.sourceCommit ?? "")) {
    errors.push("backup_source_commit_invalid");
  }
  if (receipt.targetHost !== TARGET_HOST || receipt.database !== DATABASE) {
    errors.push("backup_target_invalid");
  }
  if (
    !UUID.test(receipt.sourceDatabaseServerUuid ?? "") ||
    !MYSQL_VERSION.test(receipt.serverVersion ?? "") ||
    typeof receipt.serverVersionComment !== "string" ||
    receipt.serverVersionComment.length < 1 ||
    receipt.serverVersionComment.length > 128
  ) {
    errors.push("backup_mysql_identity_invalid");
  }
  if (!validInstant(receipt.generatedAt)) {
    errors.push("backup_generated_at_invalid");
  }
  if (!pathBoundToRun(receipt.backupPath, receipt.runId, "fbsir.sql.gpg")) {
    errors.push("backup_path_not_bound_to_run");
  }
  if (
    !SHA256.test(receipt.backupSha256 ?? "") ||
    !safeIntegerAtLeast(receipt.backupSizeBytes, 1) ||
    !SHA256.test(receipt.backupPlaintextSha256 ?? "") ||
    receipt.encryptionContract !== "u3w.gnupg-aes256-symmetric.v1" ||
    !SHA256.test(receipt.encryptionKeyFingerprintSha256 ?? "")
  ) {
    errors.push("backup_artifact_invalid");
  }
  if (
    typeof receipt.dumpToolVersion !== "string" ||
    !receipt.dumpToolVersion.includes(receipt.serverVersion) ||
    receipt.dumpOptionsContract !==
      "u3w.mysqldump.innodb-consistent.v1" ||
    receipt.ddlProtectionMode !==
      "HOST_FLOCK_NAMED_LOCK_READ_ONLY_SNAPSHOT_FULL_OBJECT_MDL_"
        + "PRE_POST_STABILITY_AND_APPROVED_NO_DDL_WINDOW"
  ) {
    errors.push("backup_dump_contract_invalid");
  }
  validateFacts(receipt.sourceFacts, "source", errors);
  if (
    !safeIntegerAtLeast(receipt.sourceTotalRows, 0) ||
    !SHA256.test(receipt.sourceTableRowCountsSha256 ?? "") ||
    receipt.sourceSnapshotExactlyMatched !== false ||
    !SHA256.test(receipt.sourceJarSha256 ?? "") ||
    !SHA256.test(receipt.planReceiptSha256 ?? "") ||
    !SHA256.test(
      receipt.adminRootDependencyAdoptionReceiptSha256 ?? "",
    ) ||
    !SHA256.test(receipt.approvalReceiptSha256 ?? "") ||
    !SHA256.test(receipt.runnerSha256 ?? "") ||
    !SHA256.test(receipt.backupWorkerSha256 ?? "")
  ) {
    errors.push("backup_provenance_invalid");
  }
  if (
    receipt.businessDatabaseChanged !== false ||
    receipt.serviceChanged !== false ||
    receipt.officialExpertsPackageChanged !== false
  ) {
    errors.push("backup_scope_overclaim");
  }
  return { ok: errors.length === 0, errors };
}

export function validateRestoreReceipt(
  receipt,
  { backupReceipt, backupReceiptSha256 } = {},
) {
  const errors = [];
  if (!exactFields(receipt, RESTORE_FIELDS)) {
    return { ok: false, errors: ["restore_receipt_unknown_fields"] };
  }
  const backupResult = validateBackupReceipt(backupReceipt);
  if (!backupResult.ok) {
    errors.push("restore_backup_receipt_invalid");
  }
  if (
    receipt.schema !== RESTORE_SCHEMA ||
    receipt.runId !== backupReceipt?.runId ||
    receipt.sourceCommit !== backupReceipt?.sourceCommit ||
    receipt.targetHost !== TARGET_HOST ||
    receipt.database !== DATABASE ||
    !UUID.test(receipt.sourceDatabaseServerUuid ?? "")
  ) {
    errors.push("restore_identity_mismatch");
  }
  if (
    receipt.sourceBackupPath !== backupReceipt?.backupPath ||
    receipt.sourceBackupSha256 !== backupReceipt?.backupSha256 ||
    receipt.sourceBackupPlaintextSha256 !==
      backupReceipt?.backupPlaintextSha256 ||
    receipt.sourceBackupReceiptSha256 !== backupReceiptSha256 ||
    !SHA256.test(backupReceiptSha256 ?? "")
  ) {
    errors.push("restore_backup_binding_mismatch");
  }
  if (
    !validInstant(receipt.startedAt) ||
    !validInstant(receipt.completedAt) ||
    Date.parse(receipt.completedAt) < Date.parse(receipt.startedAt)
  ) {
    errors.push("restore_time_invalid");
  }
  if (
    receipt.isolatedTarget !== true ||
    receipt.isolatedNetworkingDisabled !== true ||
    receipt.isolatedDataRemoved !== true
  ) {
    errors.push("restore_isolation_incomplete");
  }
  if (
    receipt.serverVersion !== backupReceipt?.serverVersion ||
    receipt.serverVersionComment !==
      backupReceipt?.serverVersionComment ||
    receipt.sourceDatabaseServerUuid !==
      backupReceipt?.sourceDatabaseServerUuid
  ) {
    errors.push("restored_mysql_identity_mismatch");
  }
  validateFacts(receipt.restoredFacts, "restored", errors);
  if (
    isPlainObject(receipt.restoredFacts) &&
    isPlainObject(backupReceipt?.sourceFacts)
  ) {
    if (
      receipt.restoredFacts.schemaFingerprintSha256 !==
      backupReceipt.sourceFacts.schemaFingerprintSha256
    ) {
      errors.push("restored_schema_fingerprint_mismatch");
    }
    if (
      receipt.restoredFacts.prerequisiteShapeSha256 !==
      backupReceipt.sourceFacts.prerequisiteShapeSha256
    ) {
      errors.push("restored_prerequisite_shape_mismatch");
    }
    if (
      canonicalJson(staticControlFacts(receipt.restoredFacts)) !==
      canonicalJson(staticControlFacts(backupReceipt.sourceFacts))
    ) {
      errors.push("restored_facts_mismatch");
    }
    if (
      !attributionObservationsMonotonic(
        backupReceipt.sourceFacts,
        receipt.restoredFacts,
      )
    ) {
      errors.push("restored_attribution_observations_regressed");
    }
  }
  if (
    !safeIntegerAtLeast(receipt.restoredTotalRows, 0) ||
    !SHA256.test(receipt.restoredTableRowCountsSha256 ?? "")
  ) {
    errors.push("restored_data_manifest_invalid");
  }
  if (
    !pathBoundToRun(receipt.restoreLogPath, receipt.runId, "restore.log") ||
    !SHA256.test(receipt.restoreLogSha256 ?? "") ||
    !pathBoundToRun(
      receipt.isolationEvidencePath,
      receipt.runId,
      "isolation-evidence.json",
    ) ||
    !SHA256.test(receipt.isolationEvidenceSha256 ?? "") ||
    !pathBoundToRun(receipt.mysqlcheckPath, receipt.runId, "mysqlcheck.log") ||
    !SHA256.test(receipt.mysqlcheckSha256 ?? "") ||
    receipt.approvalReceiptSha256 !==
      backupReceipt?.approvalReceiptSha256 ||
    receipt.planReceiptSha256 !== backupReceipt?.planReceiptSha256 ||
    receipt.backupWorkerSha256 !== backupReceipt?.backupWorkerSha256 ||
    receipt.adminRootDependencyAdoptionReceiptSha256 !==
      backupReceipt?.adminRootDependencyAdoptionReceiptSha256 ||
    !SHA256.test(receipt.verifierSha256 ?? "")
  ) {
    errors.push("restore_evidence_invalid");
  }
  if (
    receipt.businessDatabaseChanged !== false ||
    receipt.serviceChanged !== false ||
    receipt.officialExpertsPackageChanged !== false
  ) {
    errors.push("restore_scope_overclaim");
  }
  return { ok: errors.length === 0, errors };
}

function validateBundle(bundle) {
  const errors = [];
  if (!exactFields(bundle, BUNDLE_FIELDS)) {
    return { ok: false, errors: ["bundle_unknown_fields"] };
  }
  if (
    bundle.schema !== BUNDLE_SCHEMA ||
    !RUN_ID.test(bundle.runId ?? "") ||
    !COMMIT.test(bundle.sourceCommit ?? "") ||
    bundle.targetHost !== TARGET_HOST ||
    bundle.database !== DATABASE ||
    !UUID.test(bundle.sourceDatabaseServerUuid ?? "") ||
    !validInstant(bundle.generatedAt)
  ) {
    errors.push("bundle_identity_invalid");
  }
  if (
    !pathBoundToRun(
      bundle.backupReceiptPath,
      bundle.runId,
      "backup-receipt.json",
    ) ||
    !pathBoundToRun(
      bundle.restoreReceiptPath,
      bundle.runId,
      "restore-receipt.json",
    ) ||
    !pathBoundToRun(bundle.backupPath, bundle.runId, "fbsir.sql.gpg")
  ) {
    errors.push("bundle_path_invalid");
  }
  for (const field of [
    "backupReceiptSha256",
    "restoreReceiptSha256",
    "backupSha256",
    "planReceiptSha256",
    "approvalReceiptSha256",
    "runnerSha256",
    "backupWorkerSha256",
    "verifierSha256",
    "adminRootDependencyAdoptionReceiptSha256",
  ]) {
    if (!SHA256.test(bundle[field] ?? "")) {
      errors.push(`bundle_${field}_invalid`);
    }
  }
  if (
    !safeIntegerAtLeast(bundle.backupSizeBytes, 1) ||
    bundle.productionBusinessStateChanged !== false
  ) {
    errors.push("bundle_scope_invalid");
  }
  return { ok: errors.length === 0, errors };
}

function liveFactsMatch(liveFacts, restoreReceipt) {
  if (!isPlainObject(liveFacts) || !isPlainObject(restoreReceipt)) {
    return false;
  }
  const restoredFacts = restoreReceipt.restoredFacts;
  const currentFacts = Object.fromEntries(
    FACT_FIELDS.map((field) => [field, liveFacts[field]]),
  );
  const liveFactErrors = [];
  validateFacts(currentFacts, "live", liveFactErrors);
  return (
    liveFactErrors.length === 0 &&
    liveFacts.serverVersion === restoreReceipt.serverVersion &&
    liveFacts.serverVersionComment ===
      restoreReceipt.serverVersionComment &&
    canonicalJson(staticControlFacts(currentFacts)) ===
      canonicalJson(staticControlFacts(restoredFacts)) &&
    attributionObservationsMonotonic(restoredFacts, currentFacts)
  );
}

export function evaluateBackupRestoreBundle(input) {
  const bundleResult = validateBundle(input?.bundle);
  const backupResult = validateBackupReceipt(input?.backupReceipt);
  const restoreResult = validateRestoreReceipt(input?.restoreReceipt, {
    backupReceipt: input?.backupReceipt,
    backupReceiptSha256: input?.actualBackupReceiptSha256,
  });
  const bundle = input?.bundle ?? {};
  const backup = input?.backupReceipt ?? {};
  const restore = input?.restoreReceipt ?? {};
  const receiptAnchorMatched =
    SHA256.test(input?.expectedBundleReceiptSha256 ?? "") &&
    input.bundleReceiptSha256 === input.expectedBundleReceiptSha256;
  const sourceCommitMatched =
    COMMIT.test(input?.expectedSourceCommit ?? "") &&
    bundle.sourceCommit === input.expectedSourceCommit &&
    backup.sourceCommit === input.expectedSourceCommit &&
    restore.sourceCommit === input.expectedSourceCommit;
  const runnerMatched =
    SHA256.test(input?.expectedRunnerSha256 ?? "") &&
    bundle.runnerSha256 === input.expectedRunnerSha256 &&
    backup.runnerSha256 === input.expectedRunnerSha256;
  const backupWorkerMatched =
    SHA256.test(input?.expectedBackupWorkerSha256 ?? "") &&
    bundle.backupWorkerSha256 === input.expectedBackupWorkerSha256 &&
    backup.backupWorkerSha256 === input.expectedBackupWorkerSha256 &&
    restore.backupWorkerSha256 === input.expectedBackupWorkerSha256;
  const verifierMatched =
    SHA256.test(input?.expectedVerifierSha256 ?? "") &&
    bundle.verifierSha256 === input.expectedVerifierSha256 &&
    restore.verifierSha256 === input.expectedVerifierSha256;
  const approvalReceiptMatched =
    SHA256.test(input?.expectedApprovalReceiptSha256 ?? "") &&
    bundle.approvalReceiptSha256 ===
      input.expectedApprovalReceiptSha256 &&
    backup.approvalReceiptSha256 ===
      input.expectedApprovalReceiptSha256 &&
    restore.approvalReceiptSha256 ===
      input.expectedApprovalReceiptSha256;
  const planReceiptMatched =
    SHA256.test(input?.expectedPlanReceiptSha256 ?? "") &&
    bundle.planReceiptSha256 === input.expectedPlanReceiptSha256 &&
    backup.planReceiptSha256 === input.expectedPlanReceiptSha256 &&
    restore.planReceiptSha256 === input.expectedPlanReceiptSha256;
  const adminRootDependencyAdoptionMatched =
    SHA256.test(
      input?.expectedAdminRootDependencyAdoptionReceiptSha256 ?? "",
    ) &&
    bundle.adminRootDependencyAdoptionReceiptSha256 ===
      input.expectedAdminRootDependencyAdoptionReceiptSha256 &&
    backup.adminRootDependencyAdoptionReceiptSha256 ===
      input.expectedAdminRootDependencyAdoptionReceiptSha256 &&
    restore.adminRootDependencyAdoptionReceiptSha256 ===
      input.expectedAdminRootDependencyAdoptionReceiptSha256;
  const artifactMatched =
    bundle.backupSha256 === input?.actualBackupSha256 &&
    backup.backupSha256 === input?.actualBackupSha256 &&
    restore.sourceBackupSha256 === input?.actualBackupSha256 &&
    bundle.backupSizeBytes === input?.actualBackupSizeBytes &&
    backup.backupSizeBytes === input?.actualBackupSizeBytes;
  const receiptFilesMatched =
    bundle.backupReceiptSha256 ===
      input?.actualBackupReceiptSha256 &&
    bundle.restoreReceiptSha256 ===
      input?.actualRestoreReceiptSha256;
  const identityBindingMatched =
    bundle.runId === backup.runId &&
    bundle.runId === restore.runId &&
    bundle.targetHost === backup.targetHost &&
    bundle.targetHost === restore.targetHost &&
    bundle.database === backup.database &&
    bundle.database === restore.database &&
    bundle.sourceDatabaseServerUuid === backup.sourceDatabaseServerUuid &&
    bundle.sourceDatabaseServerUuid ===
      restore.sourceDatabaseServerUuid &&
    bundle.backupPath === backup.backupPath &&
    bundle.backupPath === restore.sourceBackupPath;
  const restoreLiveFactsMatched =
    restoreResult.ok && liveFactsMatch(input?.liveFacts, restore);
  const restoreProcedureVerified =
    restoreResult.ok && receiptFilesMatched && verifierMatched;
  const proven =
    bundleResult.ok &&
    backupResult.ok &&
    restoreResult.ok &&
    receiptAnchorMatched &&
    sourceCommitMatched &&
    approvalReceiptMatched &&
    planReceiptMatched &&
    adminRootDependencyAdoptionMatched &&
    runnerMatched &&
    backupWorkerMatched &&
    verifierMatched &&
    artifactMatched &&
    receiptFilesMatched &&
    identityBindingMatched &&
    restoreLiveFactsMatched;
  return {
    proven,
    receiptAnchorMatched,
    sourceCommitMatched,
    approvalReceiptMatched,
    planReceiptMatched,
    adminRootDependencyAdoptionMatched,
    runnerMatched,
    backupWorkerMatched,
    verifierMatched,
    artifactMatched,
    receiptFilesMatched,
    identityBindingMatched,
    restoreProcedureVerified,
    restoreLiveFactsMatched,
    sourceSnapshotExactlyMatched:
      backup.sourceSnapshotExactlyMatched === true,
    errors: [
      ...bundleResult.errors,
      ...backupResult.errors,
      ...restoreResult.errors,
    ],
  };
}
