import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const root = path.dirname(fileURLToPath(import.meta.url));
const runnerPath = path.join(root, "reconcile-u3w-staged-release.ps1");
const workerPath = path.join(
  root,
  "u3w-staged-release-recovery-remote.py",
);

test("staged recovery runner exposes only read-only Plan and approved Reconcile", () => {
  const runner = fs.readFileSync(runnerPath, "utf8");
  assert.match(runner, /ValidateSet\('Plan', 'Reconcile'\)/);
  assert.match(runner, /Assert-StrictHead/);
  assert.match(runner, /RECONCILE_STAGED_W1A_RELEASE/);
  assert.match(runner, /fbsir\.u3wStagedReleaseRecoveryApproval\.v1/);
  assert.match(runner, /ExpectedTargetStageReceiptSha256/);
  assert.match(runner, /ExpectedPriorRecoveryReceiptSha256/);
  assert.match(runner, /ExpectedRemoteHostKeyFingerprint/);
  assert.match(runner, /Get-CommittedBlobBytes/);
  assert.match(runner, /worker sha256 mismatch/);
  assert.match(runner, /external recovery evidence must be outside/);
});

test("remote Plan has no production mutation and Reconcile is lock-scoped", () => {
  const worker = fs.readFileSync(workerPath, "utf8");
  assert.match(worker, /def plan_recovery\(/);
  assert.match(worker, /def reconcile_recovery\(/);
  assert.match(worker, /shared_recovery_lock/);
  assert.match(worker, /exclusive_recovery_lock/);
  assert.match(worker, /types\.ModuleType/);
  assert.doesNotMatch(worker, /importlib\.util/);
  assert.match(worker, /STAGED_RECOVERY_ELIGIBLE/);
  assert.match(worker, /QUARANTINED_LATEST_REPAIR_PENDING/);
  assert.match(worker, /ALREADY_RECONCILED/);
  assert.match(worker, /STAGE_RECONCILED/);
  assert.match(
    worker,
    /current_approval = validate_reconcile_approval\(args\)/,
  );
  assert.match(
    worker,
    /receipt_approval = validate_reconcile_approval\(args\)/,
  );
  assert.ok(
    worker.indexOf("current_approval = validate_reconcile_approval(args)") <
      worker.indexOf("os.rename(target, quarantine)"),
    "approval must be current inside the lock immediately before mutation",
  );
  assert.match(worker, /os\.rename\(target, quarantine\)/);
  assert.match(worker, /atomic_symlink\(prior_receipt, LATEST_RECEIPT\)/);
  assert.match(worker, /temporary = ADMIN_ROOT/);
  assert.match(worker, /os\.replace\(temporary, path\)/);
  assert.ok(
    worker.indexOf("os.rename(target, quarantine)") <
      worker.indexOf("atomic_symlink(prior_receipt, LATEST_RECEIPT)"),
    "the executable ReleaseId must be quarantined before latest is repaired",
  );
  assert.doesNotMatch(worker, /shutil\.rmtree/);
  assert.doesNotMatch(worker, /RELEASE_ROOT\.rmdir/);
});

test("remote recovery verifies exact Stage v3 and prior recovery boundaries", () => {
  const worker = fs.readFileSync(workerPath, "utf8");
  for (const needle of [
    "fbsir.u3wW1aDeploymentReadinessReceipt.v3",
    "STAGED_FOR_SWITCH",
    "fbsir.u3wDefaultOffInterruptedApplyRecoveryReceipt.v1",
    "INTERRUPTED_APPLY_RECOVERED_APPLICATION_",
    "DATABASE_043_RETAINED_DORMANT",
    "deployment-receipt.json",
    "rollback-receipt.json",
    "apply-failure-*.json",
    "preStageRuntimeIdentity",
    "serviceSnapshotBeforeStage",
    'expected["uid"] != 0',
    'expected["gid"] != 0',
    'expected["nlink"] != 1',
    "productionDatabaseChanged",
    "productionServiceChanged",
    "officialExpertsPackageChanged",
  ]) {
    assert.ok(worker.includes(needle), needle);
  }
});
