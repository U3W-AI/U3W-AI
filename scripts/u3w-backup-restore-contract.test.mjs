import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import {
  BACKUP_SCHEMA,
  BUNDLE_SCHEMA,
  RESTORE_SCHEMA,
  W1A_SCHEMA_FINGERPRINT_SHA256,
  canonicalJson,
  evaluateBackupRestoreBundle,
  validateBackupReceipt,
  validateRestoreReceipt,
} from "./u3w-backup-restore-contract.mjs";

const commit = "a".repeat(40);
const backupSha = "b".repeat(64);
const backupReceiptSha = "c".repeat(64);
const restoreReceiptSha = "d".repeat(64);
const runnerSha = "e".repeat(64);
const backupWorkerSha = "0".repeat(64);
const verifierSha = "f".repeat(64);
const approvalReceiptSha = "6".repeat(64);
const planReceiptSha = "b".repeat(64);
const adminRootDependencyAdoptionReceiptSha = "a".repeat(64);
const schemaSha = "1".repeat(64);
const prerequisiteSha = "2".repeat(64);
const dependencyFactsSha = "4".repeat(64);
const runId = "w1a-20260723T160000Z-aaaaaaaaaaaa";
const backupPath =
  `/opt/fbsir/admin/backups/w1a/${runId}/fbsir.sql.gpg`;

function facts() {
  return {
    fingerprintAlgorithm: "u3w.mysql-schema-metadata.v2",
    schemaFingerprintSha256: schemaSha,
    prerequisiteShapeSha256: prerequisiteSha,
    baseTableCount: 83,
    viewCount: 16,
    triggerCount: 0,
    routineCount: 0,
    eventCount: 0,
    independentBoardAdminRootCount: 1,
    legacyAdminRootDependencyState: "EXACT_CONTROLLED_DEPENDENCY",
    legacyAdminRootDependencyFactsSha256: dependencyFactsSha,
    legacyAdminRootDependencyVersionCount: 1,
    legacyAdminRootDependencyReceiptCount: 1,
    legacyAdminRootIdentityCount: 1,
    legacyAdminRootExactCount: 1,
    legacyAdminRootRoleBindingCount: 0,
    legacyAdminRootPageChildCount: 0,
    legacyForbiddenPublicInit001Through042ReceiptCount: 0,
    publicInit043AnyReceiptCount: 0,
    attributionInternalReceiptCount: 0,
    attributionTableCount: 0,
    attributionTriggerCount: 0,
    attributionPermissionCount: 0,
    attributionEventCount: 0,
    attributionProbeEventCount: 0,
    attributionNaturalEventCount: 0,
    attributionNonProbeEventCount: 0,
    attributionAuthoritativeProductCreditCount: 0,
    attributionJourneyCount: 0,
    attributionProbeJourneyCount: 0,
    attributionNaturalJourneyCount: 0,
    attributionNonProbeJourneyCount: 0,
    w1aSchemaFingerprintSha256: null,
    w1a043State: "ABSENT",
    allBaseTablesInnoDB: true,
  };
}

function retainedFacts(eventCount = 3, journeyCount = 1) {
  return {
    ...facts(),
    publicInit043AnyReceiptCount: 1,
    attributionInternalReceiptCount: 1,
    attributionTableCount: 2,
    attributionTriggerCount: 2,
    attributionPermissionCount: 1,
    attributionEventCount: eventCount,
    attributionProbeEventCount: eventCount,
    attributionJourneyCount: journeyCount,
    attributionProbeJourneyCount: journeyCount,
    w1aSchemaFingerprintSha256: W1A_SCHEMA_FINGERPRINT_SHA256,
    w1a043State: "EXACT_043_RETAINED_DORMANT",
  };
}

function backupReceipt() {
  return {
    schema: BACKUP_SCHEMA,
    runId,
    sourceCommit: commit,
    planReceiptSha256: planReceiptSha,
    targetHost: "api2.u3w.com",
    database: "fbsir",
    sourceDatabaseServerUuid: "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
    serverVersion: "8.0.45",
    serverVersionComment: "Source distribution",
    generatedAt: "2026-07-23T16:00:00.000Z",
    backupPath,
    backupSha256: backupSha,
    backupSizeBytes: 1024,
    backupPlaintextSha256: "9".repeat(64),
    encryptionContract: "u3w.gnupg-aes256-symmetric.v1",
    encryptionKeyFingerprintSha256: "8".repeat(64),
    dumpToolVersion:
      "mysqldump  Ver 8.0.45 for Linux on x86_64 (Source distribution)",
    dumpOptionsContract: "u3w.mysqldump.innodb-consistent.v1",
    ddlProtectionMode:
      "HOST_FLOCK_NAMED_LOCK_READ_ONLY_SNAPSHOT_FULL_OBJECT_MDL_"
        + "PRE_POST_STABILITY_AND_APPROVED_NO_DDL_WINDOW",
    sourceFacts: facts(),
    sourceTotalRows: 1234,
    sourceTableRowCountsSha256: "7".repeat(64),
    sourceSnapshotExactlyMatched: false,
    sourceJarSha256: "3".repeat(64),
    adminRootDependencyAdoptionReceiptSha256:
      adminRootDependencyAdoptionReceiptSha,
    approvalReceiptSha256: approvalReceiptSha,
    runnerSha256: runnerSha,
    backupWorkerSha256: backupWorkerSha,
    businessDatabaseChanged: false,
    serviceChanged: false,
    officialExpertsPackageChanged: false,
  };
}

function restoreReceipt() {
  return {
    schema: RESTORE_SCHEMA,
    runId,
    sourceCommit: commit,
    planReceiptSha256: planReceiptSha,
    targetHost: "api2.u3w.com",
    database: "fbsir",
    sourceDatabaseServerUuid: "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
    sourceBackupPath: backupPath,
    sourceBackupSha256: backupSha,
    sourceBackupPlaintextSha256: "9".repeat(64),
    sourceBackupReceiptSha256: backupReceiptSha,
    startedAt: "2026-07-23T16:01:00.000Z",
    completedAt: "2026-07-23T16:04:00.000Z",
    isolatedTarget: true,
    isolatedNetworkingDisabled: true,
    isolatedDataRemoved: true,
    serverVersion: "8.0.45",
    serverVersionComment: "Source distribution",
    restoredFacts: facts(),
    restoredTotalRows: 1234,
    restoredTableRowCountsSha256: "7".repeat(64),
    restoreLogPath:
      `/opt/fbsir/admin/backups/w1a/${runId}/restore.log`,
    restoreLogSha256: "4".repeat(64),
    isolationEvidencePath:
      `/opt/fbsir/admin/backups/w1a/${runId}/isolation-evidence.json`,
    isolationEvidenceSha256: "5".repeat(64),
    mysqlcheckPath:
      `/opt/fbsir/admin/backups/w1a/${runId}/mysqlcheck.log`,
    mysqlcheckSha256: "6".repeat(64),
    adminRootDependencyAdoptionReceiptSha256:
      adminRootDependencyAdoptionReceiptSha,
    approvalReceiptSha256: approvalReceiptSha,
    backupWorkerSha256: backupWorkerSha,
    verifierSha256: verifierSha,
    businessDatabaseChanged: false,
    serviceChanged: false,
    officialExpertsPackageChanged: false,
  };
}

function bundle() {
  return {
    schema: BUNDLE_SCHEMA,
    runId,
    sourceCommit: commit,
    planReceiptSha256: planReceiptSha,
    targetHost: "api2.u3w.com",
    database: "fbsir",
    sourceDatabaseServerUuid: "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
    generatedAt: "2026-07-23T16:04:01.000Z",
    backupReceiptPath:
      `/opt/fbsir/admin/backups/w1a/${runId}/backup-receipt.json`,
    backupReceiptSha256: backupReceiptSha,
    restoreReceiptPath:
      `/opt/fbsir/admin/backups/w1a/${runId}/restore-receipt.json`,
    restoreReceiptSha256: restoreReceiptSha,
    backupPath,
    backupSha256: backupSha,
    backupSizeBytes: 1024,
    approvalReceiptSha256: approvalReceiptSha,
    runnerSha256: runnerSha,
    backupWorkerSha256: backupWorkerSha,
    verifierSha256: verifierSha,
    adminRootDependencyAdoptionReceiptSha256:
      adminRootDependencyAdoptionReceiptSha,
    productionBusinessStateChanged: false,
  };
}

test("canonical JSON recursively sorts object keys", () => {
  assert.equal(
    canonicalJson({ z: 1, a: { y: 2, x: [3, { b: 2, a: 1 }] } }),
    '{"a":{"x":[3,{"a":1,"b":2}],"y":2},"z":1}',
  );
});

test("accepts an exact backup receipt and rejects path escape", () => {
  assert.equal(validateBackupReceipt(backupReceipt()).ok, true);
  const escaped = backupReceipt();
  escaped.backupPath = "/opt/fbsir/admin/backups/w1a/other/fbsir.sql";
  const result = validateBackupReceipt(escaped);
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("backup_path_not_bound_to_run"));
});

test("rejects a backup when any source table is non-InnoDB", () => {
  const receipt = backupReceipt();
  receipt.sourceFacts.allBaseTablesInnoDB = false;
  const result = validateBackupReceipt(receipt);
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("source_tables_not_all_innodb"));
});

test("requires an adopted exact admin root and an exact dormant 043 state", () => {
  const missingDependency = backupReceipt();
  missingDependency.sourceFacts.legacyAdminRootDependencyState = "ABSENT";
  missingDependency.sourceFacts.legacyAdminRootDependencyVersionCount = 0;
  missingDependency.sourceFacts.legacyAdminRootDependencyReceiptCount = 0;
  missingDependency.sourceFacts.legacyAdminRootIdentityCount = 0;
  missingDependency.sourceFacts.legacyAdminRootExactCount = 0;
  missingDependency.sourceFacts.independentBoardAdminRootCount = 0;
  const missing = validateBackupReceipt(missingDependency);
  assert.equal(missing.ok, false);
  assert.ok(missing.errors.includes("source_admin_root_dependency_invalid"));

  const applied043 = backupReceipt();
  applied043.sourceFacts.w1a043State =
    "EXACT_043_RETAINED_DORMANT";
  applied043.sourceFacts.publicInit043AnyReceiptCount = 1;
  applied043.sourceFacts.attributionInternalReceiptCount = 1;
  applied043.sourceFacts.attributionTableCount = 2;
  applied043.sourceFacts.attributionTriggerCount = 2;
  applied043.sourceFacts.attributionPermissionCount = 1;
  applied043.sourceFacts.attributionEventCount = 3;
  applied043.sourceFacts.attributionProbeEventCount = 3;
  applied043.sourceFacts.attributionJourneyCount = 1;
  applied043.sourceFacts.attributionProbeJourneyCount = 1;
  applied043.sourceFacts.w1aSchemaFingerprintSha256 =
    W1A_SCHEMA_FINGERPRINT_SHA256;
  const applied = validateBackupReceipt(applied043);
  assert.equal(applied.ok, true);

  applied043.sourceFacts.w1aSchemaFingerprintSha256 = "9".repeat(64);
  const driftedFingerprint = validateBackupReceipt(applied043);
  assert.equal(driftedFingerprint.ok, false);
  assert.ok(
    driftedFingerprint.errors.includes(
      "source_w1a_043_prestate_invalid",
    ),
  );
  applied043.sourceFacts.w1aSchemaFingerprintSha256 =
    W1A_SCHEMA_FINGERPRINT_SHA256;

  applied043.sourceFacts.attributionNaturalEventCount = 1;
  applied043.sourceFacts.attributionNonProbeEventCount = 1;
  applied043.sourceFacts.attributionProbeEventCount = 2;
  const partial = validateBackupReceipt(applied043);
  assert.equal(partial.ok, false);
  assert.ok(partial.errors.includes("source_w1a_043_prestate_invalid"));
});

test("W1A schema fingerprint is state-bound to the release contract", () => {
  assert.equal(
    W1A_SCHEMA_FINGERPRINT_SHA256,
    "fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d",
  );
  const absentWithFingerprint = backupReceipt();
  absentWithFingerprint.sourceFacts.w1aSchemaFingerprintSha256 =
    W1A_SCHEMA_FINGERPRINT_SHA256;
  const invalidAbsent = validateBackupReceipt(absentWithFingerprint);
  assert.equal(invalidAbsent.ok, false);
  assert.ok(
    invalidAbsent.errors.includes("source_w1a_043_prestate_invalid"),
  );
});

test("rejects unknown receipt fields to prevent secret passthrough", () => {
  const receipt = backupReceipt();
  receipt.mysqlPassword = "must-never-be-accepted";
  const result = validateBackupReceipt(receipt);
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("backup_receipt_unknown_fields"));
});

test("rejects syntactically shaped but impossible timestamps", () => {
  const receipt = backupReceipt();
  receipt.generatedAt = "2026-13-40T25:61:61Z";
  const result = validateBackupReceipt(receipt);
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("backup_generated_at_invalid"));
});

test("restore receipt binds exact facts and an observed dump manifest", () => {
  const result = validateRestoreReceipt(restoreReceipt(), {
    backupReceipt: backupReceipt(),
    backupReceiptSha256: backupReceiptSha,
  });
  assert.equal(result.ok, true);

  const drifted = restoreReceipt();
  drifted.restoredFacts.schemaFingerprintSha256 = "9".repeat(64);
  const failed = validateRestoreReceipt(drifted, {
    backupReceipt: backupReceipt(),
    backupReceiptSha256: backupReceiptSha,
  });
  assert.equal(failed.ok, false);
  assert.ok(failed.errors.includes("restored_schema_fingerprint_mismatch"));

  const missingRows = restoreReceipt();
  missingRows.restoredTotalRows = 0;
  missingRows.restoredTableRowCountsSha256 = "8".repeat(64);
  const missingRowsResult = validateRestoreReceipt(missingRows, {
    backupReceipt: backupReceipt(),
    backupReceiptSha256: backupReceiptSha,
  });
  assert.equal(missingRowsResult.ok, true);

  const invalidManifest = restoreReceipt();
  invalidManifest.restoredTotalRows = -1;
  invalidManifest.restoredTableRowCountsSha256 = "not-a-sha256";
  const invalidManifestResult = validateRestoreReceipt(invalidManifest, {
    backupReceipt: backupReceipt(),
    backupReceiptSha256: backupReceiptSha,
  });
  assert.equal(invalidManifestResult.ok, false);
  assert.ok(
    invalidManifestResult.errors.includes("restored_data_manifest_invalid"),
  );

  const observedSource = backupReceipt();
  observedSource.sourceFacts = retainedFacts(3, 1);
  const observedRestore = restoreReceipt();
  observedRestore.restoredFacts = retainedFacts(4, 2);
  const observationDrift = validateRestoreReceipt(observedRestore, {
    backupReceipt: observedSource,
    backupReceiptSha256: backupReceiptSha,
  });
  assert.equal(observationDrift.ok, true);

  const regressedSource = backupReceipt();
  regressedSource.sourceFacts = retainedFacts(5, 3);
  const regressedRestore = restoreReceipt();
  regressedRestore.restoredFacts = retainedFacts(4, 2);
  const observationRegression = validateRestoreReceipt(regressedRestore, {
    backupReceipt: regressedSource,
    backupReceiptSha256: backupReceiptSha,
  });
  assert.equal(observationRegression.ok, false);
  assert.ok(
    observationRegression.errors.includes(
      "restored_attribution_observations_regressed",
    ),
  );
});

test("self-reported success cannot replace independently matched live facts", () => {
  const result = evaluateBackupRestoreBundle({
    bundle: bundle(),
    bundleReceiptSha256: "5".repeat(64),
    expectedBundleReceiptSha256: "5".repeat(64),
    backupReceipt: backupReceipt(),
    actualBackupReceiptSha256: backupReceiptSha,
    restoreReceipt: restoreReceipt(),
    actualRestoreReceiptSha256: restoreReceiptSha,
    actualBackupSha256: backupSha,
    actualBackupSizeBytes: 1024,
    expectedSourceCommit: commit,
    expectedRunnerSha256: runnerSha,
    expectedBackupWorkerSha256: backupWorkerSha,
    expectedVerifierSha256: verifierSha,
    expectedApprovalReceiptSha256: approvalReceiptSha,
    expectedPlanReceiptSha256: planReceiptSha,
    expectedAdminRootDependencyAdoptionReceiptSha256:
      adminRootDependencyAdoptionReceiptSha,
    liveFacts: {
      serverVersion: "8.0.45",
      serverVersionComment: "Source distribution",
      ...facts(),
    },
  });
  assert.equal(result.proven, true);
  assert.equal(result.restoreProcedureVerified, true);
  assert.equal(result.restoreLiveFactsMatched, true);
  assert.equal(result.sourceSnapshotExactlyMatched, false);

  const staleLive = {
    serverVersion: "8.0.45",
    serverVersionComment: "Source distribution",
    ...facts(),
    viewCount: 15,
  };
  const failed = evaluateBackupRestoreBundle({
    bundle: bundle(),
    bundleReceiptSha256: "5".repeat(64),
    expectedBundleReceiptSha256: "5".repeat(64),
    backupReceipt: backupReceipt(),
    actualBackupReceiptSha256: backupReceiptSha,
    restoreReceipt: { ...restoreReceipt(), verified: true },
    actualRestoreReceiptSha256: restoreReceiptSha,
    actualBackupSha256: backupSha,
    actualBackupSizeBytes: 1024,
    expectedSourceCommit: commit,
    expectedRunnerSha256: runnerSha,
    expectedBackupWorkerSha256: backupWorkerSha,
    expectedVerifierSha256: verifierSha,
    expectedApprovalReceiptSha256: approvalReceiptSha,
    expectedPlanReceiptSha256: planReceiptSha,
    expectedAdminRootDependencyAdoptionReceiptSha256:
      adminRootDependencyAdoptionReceiptSha,
    liveFacts: staleLive,
  });
  assert.equal(failed.restoreLiveFactsMatched, false);
  assert.equal(failed.proven, false);
});

test("live dormant attribution observations may grow monotonically", () => {
  const source = backupReceipt();
  source.sourceFacts = retainedFacts(3, 1);
  const restored = restoreReceipt();
  restored.restoredFacts = retainedFacts(4, 2);
  const result = evaluateBackupRestoreBundle({
    bundle: bundle(),
    bundleReceiptSha256: "5".repeat(64),
    expectedBundleReceiptSha256: "5".repeat(64),
    backupReceipt: source,
    actualBackupReceiptSha256: backupReceiptSha,
    restoreReceipt: restored,
    actualRestoreReceiptSha256: restoreReceiptSha,
    actualBackupSha256: backupSha,
    actualBackupSizeBytes: 1024,
    expectedSourceCommit: commit,
    expectedRunnerSha256: runnerSha,
    expectedBackupWorkerSha256: backupWorkerSha,
    expectedVerifierSha256: verifierSha,
    expectedApprovalReceiptSha256: approvalReceiptSha,
    expectedPlanReceiptSha256: planReceiptSha,
    expectedAdminRootDependencyAdoptionReceiptSha256:
      adminRootDependencyAdoptionReceiptSha,
    liveFacts: {
      serverVersion: "8.0.45",
      serverVersionComment: "Source distribution",
      ...retainedFacts(5, 3),
    },
  });
  assert.equal(result.restoreLiveFactsMatched, true);
  assert.equal(result.proven, true);
});

test("bundle anchor, runner and verifier digests are all mandatory", () => {
  const result = evaluateBackupRestoreBundle({
    bundle: bundle(),
    bundleReceiptSha256: "5".repeat(64),
    expectedBundleReceiptSha256: "6".repeat(64),
    backupReceipt: backupReceipt(),
    actualBackupReceiptSha256: backupReceiptSha,
    restoreReceipt: restoreReceipt(),
    actualRestoreReceiptSha256: restoreReceiptSha,
    actualBackupSha256: backupSha,
    actualBackupSizeBytes: 1024,
    expectedSourceCommit: commit,
    expectedRunnerSha256: "7".repeat(64),
    expectedBackupWorkerSha256: "9".repeat(64),
    expectedVerifierSha256: "8".repeat(64),
    expectedApprovalReceiptSha256: "a".repeat(64),
    expectedPlanReceiptSha256: "c".repeat(64),
    expectedAdminRootDependencyAdoptionReceiptSha256: "b".repeat(64),
    liveFacts: {
      serverVersion: "8.0.45",
      serverVersionComment: "Source distribution",
      ...facts(),
    },
  });
  assert.equal(result.proven, false);
  assert.equal(result.receiptAnchorMatched, false);
  assert.equal(result.runnerMatched, false);
  assert.equal(result.backupWorkerMatched, false);
  assert.equal(result.verifierMatched, false);
  assert.equal(result.approvalReceiptMatched, false);
  assert.equal(result.planReceiptMatched, false);
});

test("a bundle cannot cross-bind receipts from another run", () => {
  const crossRunBundle = bundle();
  const otherRunId = "w1a-20260723T170000Z-bbbbbbbbbbbb";
  crossRunBundle.runId = otherRunId;
  crossRunBundle.backupReceiptPath =
    `/opt/fbsir/admin/backups/w1a/${otherRunId}/backup-receipt.json`;
  crossRunBundle.restoreReceiptPath =
    `/opt/fbsir/admin/backups/w1a/${otherRunId}/restore-receipt.json`;
  crossRunBundle.backupPath =
    `/opt/fbsir/admin/backups/w1a/${otherRunId}/fbsir.sql.gpg`;
  const result = evaluateBackupRestoreBundle({
    bundle: crossRunBundle,
    bundleReceiptSha256: "5".repeat(64),
    expectedBundleReceiptSha256: "5".repeat(64),
    backupReceipt: backupReceipt(),
    actualBackupReceiptSha256: backupReceiptSha,
    restoreReceipt: restoreReceipt(),
    actualRestoreReceiptSha256: restoreReceiptSha,
    actualBackupSha256: backupSha,
    actualBackupSizeBytes: 1024,
    expectedSourceCommit: commit,
    expectedRunnerSha256: runnerSha,
    expectedBackupWorkerSha256: backupWorkerSha,
    expectedVerifierSha256: verifierSha,
    expectedApprovalReceiptSha256: approvalReceiptSha,
    expectedPlanReceiptSha256: planReceiptSha,
    expectedAdminRootDependencyAdoptionReceiptSha256:
      adminRootDependencyAdoptionReceiptSha,
    liveFacts: {
      serverVersion: "8.0.45",
      serverVersionComment: "Source distribution",
      ...facts(),
    },
  });
  assert.equal(result.proven, false);
  assert.equal(result.identityBindingMatched, false);
});

test("production runner exposes a pinned plan-backup-verify state machine", () => {
  const runner = fs.readFileSync(
    new URL("./run-u3w-production-backup-restore.ps1", import.meta.url),
    "utf8",
  );
  const backupWorker = fs.readFileSync(
    new URL("./u3w-production-backup-remote.py", import.meta.url),
    "utf8",
  );
  const restoreVerifier = fs.readFileSync(
    new URL(
      "./u3w-isolated-restore-verifier-remote.py",
      import.meta.url,
    ),
    "utf8",
  );
  for (const mode of ["Plan", "Backup", "Verify", "All"]) {
    assert.ok(runner.includes(`'${mode}'`));
  }
  assert.ok(runner.includes("ExpectedRemoteHostKeyFingerprint"));
  assert.ok(runner.includes("ExpectedPublicKeyFingerprint"));
  assert.ok(runner.includes("ApprovalReceiptPath"));
  assert.ok(runner.includes("PlanReceiptPath"));
  assert.ok(runner.includes("ExpectedPlanReceiptSha256"));
  assert.ok(
    runner.includes(
      "fbsir.u3wDatabaseBackupRestoreExternalAnchor.v3",
    ),
  );
  assert.ok(
    runner.includes(
      "ExpectedAdminRootDependencyAdoptionReceiptSha256",
    ),
  );
  assert.ok(runner.includes("git status --porcelain=v1"));
  assert.ok(runner.includes("git ls-remote"));
  assert.ok(runner.includes("Get-CommittedBlobBytes"));
  assert.ok(runner.includes("[Convert]::ToBase64String($WorkerBytes)"));
  assert.ok(runner.includes("worker payload SHA-256 mismatch"));
  assert.ok(runner.includes("'IdentitiesOnly=yes'"));
  assert.ok(runner.includes("'IdentityAgent=none'"));
  assert.ok(
    runner.match(/Assert-StrictHead/g)?.length >= 3,
    "strict HEAD must be checked before and after the remote operation",
  );
  assert.ok(backupWorker.includes('"--single-transaction"'));
  assert.ok(backupWorker.includes('"--quick"'));
  assert.ok(backupWorker.includes('"--routines"'));
  assert.ok(backupWorker.includes('"--events"'));
  assert.ok(backupWorker.includes('"--triggers"'));
  assert.ok(backupWorker.includes("os.replace(partial_path, backup_path)"));
  assert.ok(backupWorker.includes('"u3w.gnupg-aes256-symmetric.v1"'));
  assert.ok(backupWorker.includes("fcntl.flock"));
  assert.ok(backupWorker.includes("u3wDatabaseBackupReceipt.v4"));
  assert.ok(backupWorker.includes("u3wDatabaseBackupPlan.v3"));
  assert.ok(backupWorker.includes("validate_plan_against_live"));
  assert.ok(backupWorker.includes("static_control_facts(facts_before)"));
  assert.ok(!backupWorker.includes("manifest_after"));
  assert.ok(
    backupWorker.includes(
      "adminRootDependencyAdoptionReceiptSha256",
    ),
  );
  assert.ok(restoreVerifier.includes('"--skip-networking=ON"'));
  assert.ok(restoreVerifier.includes('"--mysqlx=OFF"'));
  assert.ok(restoreVerifier.includes("isolatedDataRemoved"));
  assert.ok(
    restoreVerifier.includes("u3wDatabaseRestoreRehearsalReceipt.v4"),
  );
  assert.ok(
    restoreVerifier.includes(
      "u3wDatabaseBackupRestoreBundleReceipt.v3",
    ),
  );
  assert.ok(
    restoreVerifier.includes(
      '"restored row manifest is invalid"',
    ),
  );
  assert.ok(
    restoreVerifier.includes(
      '"sourceSnapshotExactlyMatched":\n'
        + '                backup_receipt.get("sourceSnapshotExactlyMatched")',
    ),
  );
  assert.ok(restoreVerifier.includes("os.replace(next_link, latest_link)"));
});

test("readiness CLI requires anchors by requested stage without a deployment cycle", () => {
  const collector = fs.readFileSync(
    new URL(
      "./verify-independent-board-production-readiness.ps1",
      import.meta.url,
    ),
    "utf8",
  );
  assert.ok(collector.includes("[string]$RequiredStage"));
  assert.ok(collector.includes("'PREPARED_FOR_STAGE'"));
  assert.ok(collector.includes("'STAGED_FOR_SWITCH'"));
  assert.ok(collector.includes("'DEPLOYED_DEFAULT_OFF'"));
  assert.ok(
    collector.includes(
      "PREPARED_FOR_STAGE requires the backup out-of-band SHA-256 anchor",
    ),
  );
  assert.ok(
    collector.includes(
      "PREPARED_FOR_STAGE requires the approved backup Plan SHA-256 anchor",
    ),
  );
});
