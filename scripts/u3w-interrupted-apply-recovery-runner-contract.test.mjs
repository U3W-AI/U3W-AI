import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const runner = fs.readFileSync(
  path.join(root, "scripts", "reconcile-u3w-interrupted-apply.ps1"),
  "utf8",
);
const worker = fs.readFileSync(
  path.join(root, "scripts", "u3w-default-off-release-remote.py"),
  "utf8",
);

function sectionBetween(source, start, end) {
  const startIndex = source.indexOf(start);
  assert.notEqual(startIndex, -1, `missing section start ${start}`);
  const endIndex = source.indexOf(end, startIndex + start.length);
  assert.notEqual(endIndex, -1, `missing section end ${end}`);
  return source.slice(startIndex, endIndex);
}

test("recovery runner exposes only Plan and approved Reconcile", () => {
  assert.ok(runner.includes("[ValidateSet('Plan', 'Reconcile')]"));
  assert.ok(runner.includes("Assert-StrictHead"));
  assert.ok(runner.includes("HEAD, upstream and origin branch must be identical"));
  assert.ok(
    runner.includes(
      "scripts/reconcile-u3w-interrupted-apply.ps1",
    ),
  );
  assert.ok(runner.includes("recovery runner differs from its committed blob"));
});

test("recovery Plan is read-only, content-addressed and worker-observed", () => {
  const plan = sectionBetween(
    runner,
    "function Invoke-RecoveryPlan {",
    "function Invoke-Reconcile {",
  );
  for (const value of [
    "InspectInterruptedApplyRecovery",
    "INTERRUPTED_APPLY_RECOVERY_PLAN_READY",
    "schema = $RecoveryPlanSchema",
    "state = $RecoveryPlanState",
    "applyFailureManifestSha256",
    "productionFilesystemWrite = $true",
    "productionDatabaseWrite = $false",
    "productionServiceChange = $false",
    "officialExpertsPackageChange = $false",
    "Write-ContentAddressedRecoveryPlan",
  ]) {
    assert.ok(plan.includes(value), `missing recovery Plan contract ${value}`);
  }
  assert.ok(worker.includes("def inspect_interrupted_apply_recovery(args):"));
  assert.ok(worker.includes('"productionFilesystemChanged": False'));
});

test("worker invocation accepts empty Plan inputs and preserves failures", () => {
  const invocation = sectionBetween(
    runner,
    "function Invoke-RecoveryWorker {",
    "function Resolve-RecoveryPlan {",
  );
  assert.equal(
    (invocation.match(/\[AllowEmptyCollection\(\)\]/g) ?? []).length,
    2,
    "Plan must be able to bind two explicit empty byte arrays",
  );
  for (const binding of [
    "$approvalBase64 = if ($ApprovalBytes.Count -gt 0)",
    "$planBase64 = if ($PlanBytes.Count -gt 0)",
    "'--approval-json-base64', $approvalBase64",
    "'--recovery-plan-json-base64', $planBase64",
  ]) {
    assert.ok(
      invocation.includes(binding),
      `PowerShell 5.1-safe argv binding is missing ${binding}`,
    );
  }
  assert.equal(
    invocation.includes(
      "'--approval-json-base64', (\n            if ",
    ),
    false,
  );
  assert.equal(
    invocation.includes(
      "'--recovery-plan-json-base64', (\n            if ",
    ),
    false,
  );
  for (const value of [
    "fbsir.u3wDefaultOffReleaseWorkerError.v2",
    "worker-failure-$failureSha.json",
    "Write-ImmutableEvidence",
    "raw evidence persisted at $failureEvidence",
    "immutable evidence persisted at ",
    "$result.errorMessageSha256",
    "$result.failureReceiptEvidenceValid -ne $false",
    "$result.officialExpertsPackageChanged -ne $false",
  ]) {
    assert.ok(
      invocation.includes(value),
      `worker failure preservation is missing ${value}`,
    );
  }
  assert.ok(
    invocation.indexOf("Write-ImmutableEvidence")
      < invocation.indexOf("returned an invalid failure envelope"),
    "raw failure bytes must be durable before envelope validation throws",
  );
});

test("Reconcile approval binds exact Plan, manifest and executor", () => {
  const approval = sectionBetween(
    runner,
    "function Resolve-RecoveryApproval {",
    "function Assert-NoReparsePointChain {",
  );
  for (const value of [
    "fbsir.u3wProductionChangeApprovalReceipt.v2",
    "CANONICALIZE_W1A_INTERRUPTED_APPLY_RECOVERY",
    "executorSourceCommit",
    "expectedStageReceiptSha256",
    "expectedApplyFailureReceiptSha256",
    "expectedApplyFailureManifestSha256",
    "requestDigest",
    "approvalNonce",
    "runnerSha256",
    "workerSha256",
  ]) {
    assert.ok(approval.includes(value), `missing approval binding ${value}`);
  }
});

test("Reconcile downloads and persists the three immutable anchors", () => {
  const reconcile = sectionBetween(
    runner,
    "function Invoke-Reconcile {",
    "if ($Mode -eq 'Reconcile'",
  );
  for (const value of [
    "CanonicalizeInterruptedApplyRecovery",
    "interrupted-apply-recovery-receipt.json",
    "interrupted-apply-recovery-approval.json",
    "interrupted-apply-recovery-plan.json",
    "CANONICALIZED",
    "RECEIPT_LINK_REPAIRED",
    "ALREADY_EXACT",
    "Write-ImmutableEvidence",
    "productionDatabaseChanged = $false",
    "productionServiceChanged = $false",
    "officialExpertsPackageChanged = $false",
  ]) {
    assert.ok(reconcile.includes(value), `missing Reconcile contract ${value}`);
  }
});

test("expired original authorization is replay-only and can repair latest", () => {
  const planValidation = sectionBetween(
    runner,
    "function Resolve-RecoveryPlan {",
    "function Resolve-RecoveryApproval {",
  );
  const approvalValidation = sectionBetween(
    runner,
    "function Resolve-RecoveryApproval {",
    "function Assert-NoReparsePointChain {",
  );
  assert.equal(
    planValidation.includes(
      "$expires -le [DateTimeOffset]::UtcNow",
    ),
    false,
  );
  assert.equal(
    approvalValidation.includes(
      "$expires -le [DateTimeOffset]::UtcNow",
    ),
    false,
  );
  assert.ok(planValidation.includes("$expires -le $generated"));
  assert.ok(approvalValidation.includes("$expires -le $approved"));

  const canonicalizer = sectionBetween(
    worker,
    "def canonicalize_interrupted_apply_recovery(args):",
    "\ndef validate_rollback_receipt_base(",
  );
  for (const value of [
    "args, require_current=False",
    "None,\n                        stage_path.resolve()",
    "validate_interrupted_recovery_historic_anchors(",
    "args, require_current=True",
    'commit_observed_at = existing["observedAt"]',
  ]) {
    assert.ok(
      canonicalizer.includes(value),
      `deterministic recovery replay is missing ${value}`,
    );
  }
  assert.ok(
    canonicalizer.indexOf("args, require_current=True")
      < canonicalizer.indexOf(
        "interrupted_recovery_authorized_commit_time(",
      ),
  );
});

test("recovery worker preflights before fixed writes and commits under locks", () => {
  const canonicalizer = sectionBetween(
    worker,
    "def canonicalize_interrupted_apply_recovery(args):",
    "\ndef validate_rollback_receipt_base(",
  );
  const latestPreflight = canonicalizer.indexOf(
    "current_latest = validated_latest_receipt_target()",
  );
  const secondServiceRead = canonicalizer.lastIndexOf(
    "assert_predecessor_active(release)",
  );
  const secondDatabaseRead = canonicalizer.lastIndexOf(
    "exact_migration_facts(migration_lease.mysql)",
  );
  const authorization = canonicalizer.indexOf(
    "interrupted_recovery_authorized_commit_time",
  );
  const anchorWrite = canonicalizer.indexOf(
    "create_or_validate_interrupted_recovery_anchor",
  );
  const receiptWrite = canonicalizer.indexOf(
    "atomic_json_create_new(recovery_path, receipt)",
  );
  const latestCas = canonicalizer.indexOf(
    "advance_latest_receipt(path, [stage_path])",
  );
  const lockRelease = canonicalizer.indexOf("migration_lease.close()");
  assert.ok(latestPreflight >= 0);
  assert.ok(secondServiceRead > latestPreflight);
  assert.ok(secondDatabaseRead > latestPreflight);
  assert.ok(authorization > secondServiceRead);
  assert.ok(authorization > secondDatabaseRead);
  assert.ok(anchorWrite > authorization);
  assert.ok(receiptWrite > anchorWrite);
  assert.ok(latestCas > receiptWrite);
  assert.ok(lockRelease > latestCas);
});

test("Stage revalidates recovery service and holds 043 through latest CAS", () => {
  const stage = sectionBetween(
    worker,
    "def finalize_stage(args):",
    "\ndef migration_fingerprint_rows(",
  );
  for (const value of [
    "validate_prior_interrupted_recovery_stage_entry",
    "commit_migration_lease = (",
    "interrupted_apply_recovery_lease()",
    "assert_predecessor_active(recovery_release)",
    "recovery_facts_at_commit = exact_migration_facts(",
    "advance_latest_receipt(",
    "commit_migration_lease.close()",
  ]) {
    assert.ok(stage.includes(value), `missing Stage commit guard ${value}`);
  }
  assert.ok(
    stage.indexOf("commit_migration_lease.close()")
      > stage.indexOf("advance_latest_receipt("),
  );
});
