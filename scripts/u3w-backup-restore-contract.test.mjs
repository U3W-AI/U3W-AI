import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import {
  BACKUP_SCHEMA,
  BUNDLE_SCHEMA,
  RESTORE_SCHEMA,
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
const schemaSha = "1".repeat(64);
const prerequisiteSha = "2".repeat(64);
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
    independentBoardAdminRootCount: 0,
    allBaseTablesInnoDB: true,
  };
}

function backupReceipt() {
  return {
    schema: BACKUP_SCHEMA,
    runId,
    sourceCommit: commit,
    targetHost: "api2.u3w.com",
    database: "fbsir",
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
      "PRE_POST_SCHEMA_STABILITY_APPROVED_NO_DDL_WINDOW",
    sourceFacts: facts(),
    sourceTotalRows: 1234,
    sourceTableRowCountsSha256: "7".repeat(64),
    sourceSnapshotExactlyMatched: false,
    sourceJarSha256: "3".repeat(64),
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
    targetHost: "api2.u3w.com",
    database: "fbsir",
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
    targetHost: "api2.u3w.com",
    database: "fbsir",
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

test("restore receipt must bind the exact backup receipt and facts", () => {
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
  assert.equal(missingRowsResult.ok, false);
  assert.ok(missingRowsResult.errors.includes("restored_data_manifest_invalid"));
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
    liveFacts: staleLive,
  });
  assert.equal(failed.restoreLiveFactsMatched, false);
  assert.equal(failed.proven, false);
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
  assert.ok(restoreVerifier.includes('"--skip-networking=ON"'));
  assert.ok(restoreVerifier.includes('"--mysqlx=OFF"'));
  assert.ok(restoreVerifier.includes("isolatedDataRemoved"));
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
});
