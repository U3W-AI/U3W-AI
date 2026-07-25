import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";

const runner = fs.readFileSync(
  new URL("./deploy-independent-board-default-off.ps1", import.meta.url),
  "utf8",
);
const readiness = fs.readFileSync(
  new URL("./verify-independent-board-production-readiness.ps1", import.meta.url),
  "utf8",
);
const configurationRunner = fs.readFileSync(
  new URL("./run-u3w-default-off-configuration.ps1", import.meta.url),
  "utf8",
);
const configurationWorker = fs.readFileSync(
  new URL("./u3w-default-off-configuration-remote.py", import.meta.url),
  "utf8",
);
const baselineRunner = fs.readFileSync(
  new URL("./run-u3w-legacy-baseline.ps1", import.meta.url),
  "utf8",
);
const backupRunner = fs.readFileSync(
  new URL("./run-u3w-production-backup-restore.ps1", import.meta.url),
  "utf8",
);
const worker = fs.readFileSync(
  new URL("./u3w-default-off-release-remote.py", import.meta.url),
  "utf8",
);
const controlPlane = fs.readFileSync(
  new URL("./verify-independent-board-control-plane.ps1", import.meta.url),
  "utf8",
);
const preparationRunbook = fs.readFileSync(
  new URL(
    "../docs/independent-board/W1A-PREPARATION-RUNBOOK.md",
    import.meta.url,
  ),
  "utf8",
);
const orchestrationContract = JSON.parse(
  fs.readFileSync(
    new URL("../.fbs-engineering/contract.json", import.meta.url),
    "utf8",
  ),
);

function sectionBetween(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(start, -1, `missing section start: ${startMarker}`);
  assert.notEqual(end, -1, `missing section end: ${endMarker}`);
  assert.ok(end > start, `invalid section bounds: ${startMarker}`);
  return source.slice(start, end);
}

function embeddedPlanCollectorSource() {
  const source = sectionBetween(
    runner,
    "function Invoke-ReadOnlyRemotePlanSnapshot {",
    "function Invoke-Plan {",
  );
  const match = source.match(/\$collector = @'\r?\n([\s\S]*?)\r?\n'@/);
  assert.ok(match, "embedded release Plan collector must be extractable");
  return match[1];
}

function pythonExecutable() {
  const bundledPython = path.join(
    os.homedir(),
    ".cache",
    "codex-runtimes",
    "codex-primary-runtime",
    "dependencies",
    "python",
    process.platform === "win32" ? "python.exe" : "bin/python",
  );
  return process.env.U3W_PYTHON_EXE
    || (fs.existsSync(bundledPython)
      ? bundledPython
      : process.platform === "win32"
        ? "python.exe"
        : "python3");
}

test("release runner exposes one explicit fail-closed state machine", () => {
  for (const mode of [
    "Build",
    "Plan",
    "Stage",
    "Apply",
    "Rollback",
    "Verify",
  ]) {
    assert.ok(runner.includes(`'${mode}'`), `${mode} mode is missing`);
  }
  assert.ok(runner.includes("Assert-StrictHead"));
  assert.ok(runner.includes("origin"));
  assert.ok(runner.includes("PREPARED_FOR_STAGE"));
  assert.ok(runner.includes("STAGED_FOR_SWITCH"));
  assert.ok(runner.includes("DEPLOYED_DEFAULT_OFF"));
});

test("configuration runner exposes an explicit token secret rotation contract", () => {
  for (const marker of [
    "'PlanTokenSecretRotation'",
    "'RotateTokenSecret'",
    "ROTATE_FBSIR_TOKEN_SECRET_FOR_W1A_DEFAULT_OFF",
    "fbsir.u3wDefaultOffConfigurationPlan.v3",
    "fbsir.u3wDefaultOffConfigurationReceipt.v4",
    "ROTATE_UNDERSIZED_FBSIR_TOKEN_SECRET",
  ]) {
    assert.ok(configurationRunner.includes(marker), `missing ${marker}`);
  }
  assert.ok(
    configurationRunner.includes(
      "expectedPredecessorConfigurationReceiptSha256",
    ),
  );
  assert.ok(
    configurationRunner.includes(
      "'fbsir.u3wDefaultOffConfigurationReceipt.v4'",
    ),
  );
});

test("configuration failures preserve bounded mutation truth before throwing", () => {
  for (const marker of [
    "fbsir.u3wDefaultOffConfigurationWorkerError.v2",
    "errorMessageSha256",
    "productionFilesystemChanged",
    "productionConfigurationChanged",
    "sys.exit(main())",
  ]) {
    assert.ok(configurationWorker.includes(marker), `missing ${marker}`);
  }
  assert.equal(configurationWorker.includes('"error": str(error)'), false);
  const remote = sectionBetween(
    configurationRunner,
    "function Invoke-RemoteWorker {",
    "function Save-ExternalAnchor {",
  );
  const captureExit = remote.indexOf("$remoteExitCode = $LASTEXITCODE");
  const parseEnvelope = remote.indexOf("$parsed = $json | ConvertFrom-Json");
  const inspectFailure = remote.lastIndexOf("if ($remoteExitCode -ne 0)");
  assert.ok(captureExit >= 0);
  assert.ok(parseEnvelope > captureExit);
  assert.ok(inspectFailure > parseEnvelope);
  assert.ok(remote.includes("$nameDrift.Count -ne 0"));
  assert.ok(remote.includes("$parsed.secretsDisclosed -ne $false"));
  assert.ok(configurationRunner.includes("$expiredRecoveryEligible"));
  assert.ok(
    configurationRunner.includes(
      "$RunId-default-off-configuration-worker-error-$digest.json",
    ),
  );
  const failureFlow = configurationRunner.slice(
    configurationRunner.indexOf(
      "if ($result.schema -eq",
      configurationRunner.indexOf("$result = Invoke-RemoteWorker"),
    ),
  );
  const persist = failureFlow.indexOf(
    "Save-ExternalWorkerFailureAnchor -WorkerError $result",
  );
  const throwAfterPersist = failureFlow.indexOf(
    "remote configuration worker failed; external evidence:",
  );
  assert.ok(persist >= 0);
  assert.ok(throwAfterPersist > persist);
});

test("distribution-bearing release receipts use the v2/v3 contract line", () => {
  for (const value of [
    "fbsir.u3wDefaultOffReleaseRunner.v2",
    "fbsir.u3wDefaultOffReleasePlan.v2",
    "fbsir.u3wDefaultOffReleaseRunnerResult.v2",
    "fbsir.u3wDefaultOffReleaseWorkerResult.v2",
    "fbsir.u3wW1aDeploymentReadinessReceipt.v3",
    "fbsir.u3wDatabaseRollbackSafetyReceipt.v2",
    "fbsir.u3wApplicationRollbackExecutionReceipt.v2",
    "fbsir.u3wDefaultOffReleaseRollbackReceipt.v2",
    "fbsir.u3wDefaultOffRollbackVerificationReceipt.v2",
  ]) {
    assert.ok(
      runner.includes(value) || worker.includes(value),
      `missing current release contract ${value}`,
    );
  }
  for (const value of [
    "fbsir.u3wW1aDeploymentReadinessReceipt.v2",
    "fbsir.u3wDatabaseRollbackSafetyReceipt.v1",
    "fbsir.u3wDefaultOffFinalCurrentRead.v1",
    "fbsir.u3wApplicationRollbackExecutionReceipt.v1",
    "fbsir.u3wDefaultOffReleaseRollbackReceipt.v1",
    "fbsir.u3wDefaultOffRollbackVerificationReceipt.v1",
  ]) {
    assert.ok(
      runner.includes(value) || worker.includes(value),
      `missing immutable legacy release contract ${value}`,
    );
  }
  for (const helper of [
    "legacy_migration_structure_matches",
    "recorded_migration_facts_match_current",
    "recorded_migration_counts_are_monotonic",
    "deployment_contract_schemas",
  ]) {
    assert.ok(worker.includes(helper));
  }
  assert.ok(runner.includes("deploymentReceiptSchema"));
});

test("plan is local-only and every mutating mode needs an approval receipt", () => {
  assert.ok(runner.includes("productionChanged = $false"));
  assert.ok(runner.includes("ApprovalReceiptPath"));
  assert.ok(runner.includes("Get-ApprovalDigest"));
  assert.ok(runner.includes("officialExpertsPackageChange"));
  assert.ok(runner.includes("productionServiceChange"));
});

test("plan validity uses one clock instant for an exact 24 hour window", () => {
  assert.ok(runner.includes("$planGeneratedAt = [DateTime]::UtcNow"));
  assert.ok(
    runner.includes("generatedAt = $planGeneratedAt.ToString('o')"),
  );
  assert.ok(
    runner.includes("expiresAt = $planGeneratedAt.AddHours(24).ToString('o')"),
  );
  assert.equal(
    runner.includes("[DateTime]::UtcNow.AddHours(24).ToString('o')"),
    false,
  );
});

test("plan publishes one immutable content-addressed readiness receipt", () => {
  const plan = sectionBetween(
    runner,
    "function Invoke-Plan {",
    "function Get-ApprovalDigest {",
  );
  assert.ok(plan.includes("Write-ContentAddressedPlanReceipt"));
  assert.ok(runner.includes(
    "work\\release-plans\\by-sha256",
  ));
  assert.ok(plan.includes(
    "planReceiptPath = $immutablePlan.path",
  ));
  assert.ok(runner.includes("[IO.FileMode]::CreateNew"));
  assert.ok(readiness.includes(
    "'work\\release-plans\\by-sha256'",
  ));
  assert.ok(readiness.includes(
    "\"$($ExpectedReleasePlanReceiptSha256.ToLowerInvariant()).json\"",
  ));
});

test("stage uploads only receipt bytes captured before its readiness gate", () => {
  const resolveBuild = sectionBetween(
    runner,
    "function Resolve-BuildReceipt {",
    "function Resolve-PlanReceipt {",
  );
  const resolvePlan = sectionBetween(
    runner,
    "function Resolve-PlanReceipt {",
    "function Invoke-Build {",
  );
  const stage = sectionBetween(
    runner,
    "function Invoke-Stage {",
    "function Invoke-Apply {",
  );
  const snapshot = sectionBetween(
    runner,
    "function New-StageArtifactSnapshot {",
    "function Copy-ReleaseArtifact {",
  );
  assert.ok(resolveBuild.includes("[IO.File]::ReadAllBytes($resolved)"));
  assert.ok(resolveBuild.includes("bytes = $receiptBytes"));
  assert.ok(resolvePlan.includes("[IO.File]::ReadAllBytes($resolved)"));
  assert.ok(resolvePlan.includes("bytes = $receiptBytes"));
  assert.ok(snapshot.includes("$Context.plan.bytes"));
  assert.ok(snapshot.includes("$Context.build.bytes"));
  assert.ok(snapshot.includes("Get-CommittedBlobBytes"));
  assert.ok(snapshot.includes("Get-TreeManifest"));
  assert.ok(snapshot.includes("[IO.FileShare]::Read"));
  assert.ok(snapshot.includes("$lockedStreams"));
  assert.ok(snapshot.includes("$Context.plan.receipt.runnerSha256"));
  assert.ok(snapshot.includes("$Context.plan.receipt.workerSha256"));
  assert.ok(snapshot.includes("backend/fbsir-admin.jar"));
  assert.ok(snapshot.includes("frontend.tar"));
  assert.ok(snapshot.includes("sql/public_init_043.sql"));
  const snapshotCall = stage.indexOf("New-StageArtifactSnapshot");
  const preparedGate = stage.indexOf("Invoke-PreparedGate");
  const remoteWorker = stage.indexOf("Invoke-CommittedRemoteWorker");
  assert.ok(snapshotCall >= 0);
  assert.ok(snapshotCall < preparedGate);
  assert.ok(preparedGate < remoteWorker);
  assert.ok(stage.includes(
    "Copy-ReleaseArtifact -LocalPath $snapshot.planReceiptPath",
  ));
  assert.equal(
    stage.includes("Copy-ReleaseArtifact -LocalPath $context.plan.path"),
    false,
  );
  assert.equal(
    stage.includes("Copy-ReleaseArtifact -LocalPath $context.build.path"),
    false,
  );
  assert.ok(stage.includes("$stream.Dispose()"));
});

test("readiness parses and hashes the same immutable Plan bytes", () => {
  assert.ok(readiness.includes(
    "$releasePlanBytes = [IO.File]::ReadAllBytes($releasePlanPath)",
  ));
  assert.match(
    readiness,
    /\$releasePlanReceiptSha256\s*=\s*Get-BytesSha256 \$releasePlanBytes/,
  );
  assert.ok(readiness.includes(
    ").GetString($releasePlanBytes) | ConvertFrom-Json",
  ));
  assert.equal(
    readiness.includes(
      "Get-Content -LiteralPath $releasePlanPath -Raw -Encoding UTF8",
    ),
    false,
  );
  assert.match(
    readiness,
    /\$buildReceiptBytes\s*=\s*\[IO\.File\]::ReadAllBytes\(\$buildReceiptPath\)/,
  );
  assert.match(
    readiness,
    /\$buildReceiptSha256\s*=\s*Get-BytesSha256 \$buildReceiptBytes/,
  );
  assert.match(
    readiness,
    /\)\.GetString\(\s*\$buildReceiptBytes\) \| ConvertFrom-Json/,
  );
});

test("preparation runbook names the exact backup and plan anchors", () => {
  assert.ok(preparationRunbook.includes(
    "<final-backup-receipt-file-sha256>",
  ));
  assert.equal(
    preparationRunbook.includes("<final-backup-bundle-sha256>"),
    false,
  );
  assert.ok(preparationRunbook.includes(
    "work/release-plans/by-sha256/<release-plan-sha256>.json",
  ));
  assert.match(
    preparationRunbook,
    /`latest`\s*仅用于展示，不作为 Stage、Apply 或\s*readiness 的权威输入/,
  );
});

test("recovery rehearsal is never misrepresented as database rollback", () => {
  assert.equal(runner.includes("databaseRollbackProven = $true"), false);
  assert.equal(
    runner.includes("u3wDatabaseRollbackRehearsalReceipt"),
    false,
  );
  assert.ok(runner.includes("databaseRollbackSafety"));
  assert.ok(readiness.includes("databaseRollbackSafetyProven"));
  assert.equal(readiness.includes('"databaseRollbackProven"'), false);
});

test("apply and rollback cannot run without independently anchored receipts", () => {
  assert.ok(runner.includes("ExpectedBackupReceiptSha256"));
  assert.ok(runner.includes("ExpectedBackupPlanReceiptSha256"));
  assert.ok(readiness.includes("ExpectedBackupPlanReceiptSha256"));
  assert.ok(runner.includes("ExpectedLegacyBaselineReceiptDigest"));
  assert.ok(runner.includes("ExpectedDeploymentReceiptSha256"));
  assert.ok(runner.includes("receipt anchor"));
});

test("backup evidence binds the approved Plan across bundle and external anchor", () => {
  for (const value of [
    "PlanReceiptPath",
    "ExpectedPlanReceiptSha256",
    "expectedBackupPlanReceiptSha256",
    "planReceiptSha256",
    "fbsir.u3wDatabaseBackupRestoreExternalAnchor.v3",
  ]) {
    assert.ok(backupRunner.includes(value), `missing backup Plan binding ${value}`);
  }
  assert.ok(backupRunner.includes("$expectedBundleFields"));
  assert.ok(backupRunner.includes("$expectedAnchorFields"));
  assert.ok(backupRunner.includes("planReceiptPath = $planPath"));
  assert.ok(
    backupRunner.includes(
      "immutable out-of-band backup Plan evidence is missing or drifted",
    ),
  );
  assert.ok(readiness.includes("EXPECTED_BACKUP_PLAN_RECEIPT_SHA256"));
  assert.ok(readiness.includes("plan_receipt_binding_matched"));
  assert.ok(readiness.includes("$localBackupPlanSha256"));
});

test("build returns one result and binds logs plus the committed runner", () => {
  assert.ok(runner.includes("$verificationOutput = @("));
  assert.ok(runner.includes("$mavenOutput = @("));
  assert.ok(runner.includes("verificationLog"));
  assert.ok(runner.includes("packageLog"));
  assert.ok(runner.includes("$receipt.runnerSha256 -ne (Get-RunnerSha256)"));
  assert.ok(runner.includes("runnerSha256 = Get-RunnerSha256"));
  assert.ok(runner.includes("w1a-release.json"));
  assert.ok(runner.includes("fbsir.u3wReleaseMarker.v1"));
  assert.ok(runner.includes("-f (Join-Path $RepoRoot 'pom.xml')"));
  assert.ok(runner.includes("Resolve-MavenExecutable"));
  assert.ok(runner.includes("Resolve-JavaHome"));
  assert.ok(runner.includes("apache-maven-3.9.16"));
  assert.ok(runner.includes("jdk-17.0.19+10"));
});

test("frontend tree hashing uses cross-platform ordinal relative-path order", () => {
  const treeManifest = sectionBetween(
    runner,
    "function Get-TreeManifest {",
    "function Write-Utf8NoBomAtomic {",
  );
  const readinessTreeFacts = sectionBetween(
    readiness,
    "function Get-LocalTreeFacts {",
    "function Test-WorkerPyMySqlOrchestrationProof {",
  );
  assert.ok(treeManifest.includes("[StringComparer]::Ordinal.Compare"));
  assert.equal(treeManifest.includes("Sort-Object FullName"), false);
  assert.ok(readinessTreeFacts.includes("[StringComparer]::Ordinal.Compare"));
  assert.equal(readinessTreeFacts.includes("Sort-Object FullName"), false);
});

test("SSH is pinned to the derived private key and exact collector bytes", () => {
  assert.ok(runner.includes("ssh-keygen.exe -y -f $SshKeyPath"));
  assert.ok(runner.includes("IdentitiesOnly=yes"));
  assert.ok(runner.includes("IdentityAgent=none"));
  assert.ok(runner.includes("collector sha256 mismatch"));
  assert.ok(runner.includes("[Convert]::ToBase64String($collectorBytes)"));
  assert.ok(runner.includes("$payload | & ssh.exe @sshArguments"));
  assert.equal(runner.includes("$collector | & ssh.exe @sshArguments"), false);
});

test("Windows OpenSSH preserves Python bootstrap string literals", () => {
  for (const [name, source, expectedCount] of [
    ["release runner", runner, 3],
    ["readiness runner", readiness, 1],
    ["configuration runner", configurationRunner, 1],
    ["baseline runner", baselineRunner, 1],
    ["backup runner", backupRunner, 1],
  ]) {
    assert.equal(
      source.match(/\$escapedBootstrap = \$bootstrap\.Replace\('"', '\\"'\)/g)
        ?.length ?? 0,
      expectedCount,
      `${name} does not escape every bootstrap for Windows OpenSSH`,
    );
    assert.equal(
      source.includes(`python3 -c '$bootstrap'`),
      false,
      `${name} still passes an unescaped bootstrap to ssh.exe`,
    );
  }
});

test("stage, apply, rollback and verify execute the committed remote worker", () => {
  assert.ok(runner.includes("u3w-default-off-release-remote.py"));
  assert.ok(runner.includes("Get-CommittedBlobBytes"));
  assert.ok(runner.includes("Invoke-Stage"));
  assert.ok(runner.includes("Invoke-Apply"));
  assert.ok(runner.includes("Invoke-Rollback"));
  assert.ok(runner.includes("Invoke-Verify"));
  assert.ok(runner.includes("Save-ExternalReleaseAnchor"));
  assert.ok(runner.includes("Get-RemoteReceiptBytes"));
  assert.ok(runner.includes("downloaded remote receipt SHA-256 drifted"));
  assert.ok(runner.includes("$result.mode -ne $WorkerMode"));
  assert.ok(runner.includes("remote-receipt.json"));
  assert.ok(runner.includes("remoteEvidenceReceipts"));
  assert.ok(runner.includes("nestedReceipts"));
  assert.ok(runner.includes("top receipt did not anchor nested evidence"));
  assert.ok(runner.includes("application-rollback-execution.json"));
  assert.ok(runner.includes("$result.state -cne $receipt.state"));
  assert.equal(
    runner.includes("Stage is fail-closed until PREPARED_FOR_STAGE"),
    false,
  );
  assert.equal(
    runner.includes("Apply is fail-closed until STAGED_FOR_SWITCH"),
    false,
  );
});

test("PrepareStage declares an explicit empty nested-evidence receipt set", () => {
  const prepareStage = sectionBetween(
    worker,
    "def prepare_stage(args):",
    "\ndef jar_attribution_class_count(",
  );
  assert.equal(
    prepareStage.match(/"evidenceReceipts": \[\]/g)?.length ?? 0,
    2,
  );
});

test("committed remote worker validates the assigned result object only", () => {
  const remoteWorker = sectionBetween(
    runner,
    "function Invoke-CommittedRemoteWorker {",
    "function Write-ImmutableEvidence {",
  );
  assert.ok(remoteWorker.includes("$result = $raw | ConvertFrom-Json"));
  assert.ok(remoteWorker.includes("$result.mode -ne $WorkerMode"));
  assert.ok(remoteWorker.includes("$result.state"));
  assert.equal(remoteWorker.includes("$worker.result"), false);
});

test("every remote release action proves external evidence durability first", () => {
  const actionSections = [
    ["Stage", "function Invoke-Stage {", "function Invoke-Apply {"],
    ["Apply", "function Invoke-Apply {", "function Invoke-Rollback {"],
    ["Rollback", "function Invoke-Rollback {", "function Invoke-Verify {"],
    ["Verify", "function Invoke-Verify {", "\n$result = switch ($Mode) {"],
  ];

  for (const [name, startMarker, endMarker] of actionSections) {
    const action = sectionBetween(runner, startMarker, endMarker);
    const preflight = action.indexOf("Assert-ExternalEvidenceWritable");
    const remoteWorker = action.indexOf("Invoke-CommittedRemoteWorker");
    assert.notEqual(preflight, -1, `${name} evidence preflight is missing`);
    assert.notEqual(remoteWorker, -1, `${name} remote worker is missing`);
    assert.ok(
      preflight < remoteWorker,
      `${name} can reach the remote worker before evidence preflight`,
    );
  }

  const evidencePreflight = sectionBetween(
    runner,
    "function Assert-ExternalEvidenceWritable {",
    "function Save-ExternalReleaseAnchor {",
  );
  assert.ok(evidencePreflight.includes("[IO.FileMode]::CreateNew"));
  assert.ok(evidencePreflight.includes("$stream.Flush($true)"));
  assert.ok(evidencePreflight.includes("[IO.File]::ReadAllBytes($probePath)"));
  assert.ok(evidencePreflight.includes("Remove-Item -LiteralPath $resolvedProbe"));
});

test("stage and apply force immediate receipt-anchored readiness gates", () => {
  assert.ok(runner.includes("function Invoke-ReadinessGate"));
  assert.ok(runner.includes("-RequiredStage STAGED_FOR_SWITCH"));
  assert.ok(runner.includes("-RequiredStage DEPLOYED_DEFAULT_OFF"));
  assert.ok(runner.includes(
    "-ExpectedRemoteReceiptSha256 $finalize.result.receiptSha256",
  ));
  assert.ok(runner.includes(
    "-ExpectedRemoteReceiptSha256 $worker.result.receiptSha256",
  ));
  assert.ok(runner.includes("$statusAccepted"));
  assert.ok(runner.includes("$result.status -ceq $RequiredStage"));
  assert.ok(runner.includes("-AllowAlreadyStaged"));
});

test("stage and apply persist immutable mutation evidence before readiness", () => {
  const apply = sectionBetween(
    runner,
    "function Invoke-Apply {",
    "function Invoke-Rollback {",
  );
  const remoteWorker = apply.indexOf("Invoke-CommittedRemoteWorker");
  const readinessGate = apply.indexOf("Invoke-ReadinessGate");
  const saveEvidence = apply.indexOf("Save-ExternalReleaseAnchor");
  assert.ok(remoteWorker >= 0);
  assert.ok(readinessGate > remoteWorker);
  assert.ok(saveEvidence > remoteWorker);
  assert.ok(saveEvidence < readinessGate);
  assert.ok(apply.includes("-RequiredStage DEPLOYED_DEFAULT_OFF"));
  assert.ok(
    apply.includes(
      "-ExpectedRemoteReceiptSha256 $worker.result.receiptSha256",
    ),
  );
  const stage = sectionBetween(
    runner,
    "function Invoke-Stage {",
    "function Invoke-Apply {",
  );
  const finalizeWorker = stage.indexOf(
    "-WorkerMode FinalizeStage -Context $context",
  );
  const finalizeEvidence = stage.indexOf(
    "-Name 'stage-finalize' -WorkerResult $finalize",
  );
  const stageReadiness = stage.indexOf(
    "-RequiredStage STAGED_FOR_SWITCH",
  );
  assert.ok(finalizeWorker >= 0);
  assert.ok(finalizeEvidence > finalizeWorker);
  assert.ok(stageReadiness > finalizeEvidence);
});

test("plan collector holds the shared production lock with verified custody", () => {
  const collector = sectionBetween(
    runner,
    "function Invoke-ReadOnlyRemotePlanSnapshot {",
    "function Invoke-Plan {",
  );
  assert.ok(
    collector.includes(
      '"/opt/fbsir/admin/.u3w-production-change.lock"',
    ),
  );
  assert.ok(
    collector.includes(
      'os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)',
    ),
  );
  for (const custodyCheck of [
    "stat.S_ISREG(lock_status.st_mode)",
    "lock_status.st_uid != 0",
    "lock_status.st_gid != 0",
    "lock_status.st_nlink != 1",
    "lock_status.st_mode & 0o777 != 0o600",
  ]) {
    assert.ok(collector.includes(custodyCheck), custodyCheck);
  }
  assert.ok(
    collector.includes(
      "fcntl.flock(lock_descriptor, fcntl.LOCK_SH)",
    ),
  );
});

test("plan collector requires exactly one mandatory fixed environment file", () => {
  const collector = sectionBetween(
    runner,
    "function Invoke-ReadOnlyRemotePlanSnapshot {",
    "function Invoke-Plan {",
  );
  assert.ok(
    collector.includes(
      'ENV_PATH = pathlib.Path("/etc/u3w/fbsir-admin.env")',
    ),
  );
  assert.ok(collector.includes("EnvironmentFiles declaration is invalid"));
  assert.ok(
    collector.includes(
      "fbsir-admin requires one mandatory fixed EnvironmentFile",
    ),
  );
  assert.ok(
    collector.includes(
      '"path": "/etc/u3w/fbsir-admin.env"',
    ),
  );
  assert.ok(collector.includes('"ignoreErrors": False'));
  assert.ok(collector.includes("invalid or duplicate environment key"));
  assert.ok(collector.includes('read_text(encoding="utf-8")'));
  assert.ok(
    collector.includes(
      '"environmentFilePaths": [str(ENV_PATH)]',
    ),
  );
  assert.ok(
    collector.includes(
      '"environmentFileManifest": [env_manifest]',
    ),
  );
});

test("plan collector inventories every active Nginx file without keywords", () => {
  const collector = sectionBetween(
    runner,
    "function Invoke-ReadOnlyRemotePlanSnapshot {",
    "function Invoke-Plan {",
  );
  const nginxCollector = sectionBetween(
    collector,
    "def active_nginx_manifest():",
    "\nlock_path = pathlib.Path(",
  );
  assert.ok(nginxCollector.includes('["nginx", "-T"]'));
  assert.ok(nginxCollector.includes("dump_bytes = first"));
  assert.ok(nginxCollector.includes("stable_regular_manifest(value)"));
  assert.equal(
    nginxCollector.match(/\["nginx", "-T"\]/g)?.length,
    2,
  );
  assert.ok(nginxCollector.includes("hmac.compare_digest(first, second)"));
  assert.ok(
    nginxCollector.includes(
      'r"^# configuration file ([^:]+):$", dump, re.M',
    ),
  );
  assert.ok(collector.includes("os.fstat(descriptor)"));
  assert.ok(collector.includes("changed while reading"));
  assert.ok(
    nginxCollector.includes(
      "hashlib.sha256(dump_bytes).hexdigest()",
    ),
  );
  assert.equal(nginxCollector.toLowerCase().includes("fbsir"), false);
  assert.equal(nginxCollector.toLowerCase().includes("u3w"), false);
  for (const custodyField of [
    '"path"',
    '"sha256"',
    '"mode"',
    '"uid"',
    '"gid"',
    '"nlink"',
    '"sizeBytes"',
  ]) {
    assert.ok(
      collector.includes(custodyField),
      `Nginx custody field ${custodyField} is missing`,
    );
  }
  assert.ok(collector.includes('"nginxConfigs": nginx_manifest'));
  assert.ok(collector.includes('"activeNginxManifest": nginx_manifest'));
  assert.ok(collector.includes('"nginxDumpSha256": nginx_dump_sha256'));
});

test("plan target captures the full immutable live identity contract", () => {
  const collector = sectionBetween(
    runner,
    "function Invoke-ReadOnlyRemotePlanSnapshot {",
    "function Invoke-Plan {",
  );
  const requiredTargetFields = [
    "schema",
    "targetHost",
    "serviceUnit",
    "service",
    "unitSha256",
    "fragmentFileManifest",
    "dropInManifest",
    "unitFiles",
    "environmentCustody",
    "environmentSha256",
    "environmentFilePaths",
    "environmentFileManifest",
    "additionalConfigCustody",
    "additionalConfigSha256",
    "externalConfigManifest",
    "activeJarPath",
    "activeJarSha256",
    "configuredJarPath",
    "configuredJarSha256",
    "processJarPath",
    "processJarSha256",
    "processArgvSha256",
    "processEnvironmentNamesSha256",
    "processFlagValues",
    "configuredFlagValues",
    "processForbiddenOverrideNames",
    "api2EventKeyManifest",
    "processSecurityConfigurationNames",
    "processSecurityConfigurationHmacSha256",
    "expectedSecurityConfigurationNames",
    "expectedSecurityConfigurationHmacSha256",
    "processDatabaseBindingMatched",
    "configuredEnvironmentSha256",
    "configuredEnvironmentNames",
    "configuredEnvironmentHmacSha256",
    "processConfiguredEnvironmentHmacSha256",
    "processConfiguredEnvironmentMatched",
    "processConfiguredEnvironmentMismatchNames",
    "processPendingRestartEnvironmentNames",
    "processConfiguredEnvironmentLoadState",
    "processConfiguredEnvironmentPreStageCompatible",
    "nginxConfigs",
    "activeNginxManifest",
    "nginxDumpSha256",
    "releaseRootExists",
    "releaseRootEntryManifest",
    "currentLinkExists",
    "currentLinkResolved",
    "currentLifecycleState",
    "stageEntryTopology",
    "productionChanged",
    "productionChangedByPlan",
  ];
  for (const field of requiredTargetFields) {
    assert.ok(
      collector.includes(`"${field}"`),
      `Plan target field ${field} is missing`,
    );
  }
});

test("release Plan accepts only the exact token-secret restart mismatch", () => {
  const collector = sectionBetween(
    runner,
    "function Invoke-ReadOnlyRemotePlanSnapshot {",
    "function Invoke-Plan {",
  );
  const plan = sectionBetween(
    runner,
    "function Invoke-Plan {",
    "function Get-ApprovalDigest {",
  );
  for (const source of [collector, plan]) {
    assert.ok(source.includes("TOKEN_SECRET_ROTATION_PENDING_RESTART"));
    assert.ok(source.includes("FBSIR_TOKEN_SECRET"));
    assert.ok(source.includes("processConfiguredEnvironmentMismatchNames"));
    assert.ok(source.includes("processPendingRestartEnvironmentNames"));
  }
  assert.ok(collector.includes("mismatch_names == [TOKEN_SECRET_NAME]"));
  assert.ok(collector.includes("not pending_names"));
  assert.match(
    plan,
    /processConfiguredEnvironmentMismatchNames\s*\r?\n\s*\)\.Count -eq 1/,
  );
  assert.match(
    plan,
    /processConfiguredEnvironmentMismatchNames\s*\r?\n\s*\)\[0\] -ceq/,
  );
  assert.match(
    plan,
    /processPendingRestartEnvironmentNames\s*\r?\n\s*\)\.Count -eq 0/,
  );
});

test("release Plan accepts the exact v3/v2 recovery with no DB delta", () => {
  const pythonSource = embeddedPlanCollectorSource();
  const harness = String.raw`
import ast
import copy
import json
import re
import sys

source = sys.stdin.read()
tree = ast.parse(source, filename="<u3w-release-plan-collector>")
wanted = {
    "migration_structure_matches",
    "legacy_migration_structure_matches",
    "interrupted_apply_terminal_failure_valid",
    "interrupted_apply_stage_contract_valid",
    "interrupted_recovery_database_change_valid",
}
selected = [
    node for node in tree.body
    if isinstance(node, ast.FunctionDef) and node.name in wanted
]
legacy_fields = {
    "publicReceiptCount", "internalReceiptCount", "tableCount",
    "triggerCount", "permissionCount", "eventCount",
    "journeyCount", "schemaFingerprintSha256",
}
fact_fields = legacy_fields | {
    "probeEventCount", "naturalEventCount", "nonProbeEventCount",
    "authoritativeProductCreditCount", "probeJourneyCount",
    "naturalJourneyCount", "nonProbeJourneyCount",
}
namespace = {
    "EXPECTED_W1A_SCHEMA_FINGERPRINT": "f" * 64,
    "LEGACY_MIGRATION_FACT_FIELDS": legacy_fields,
    "MIGRATION_FACT_FIELDS": fact_fields,
    "MIGRATION_DORMANT_DATA_FIELDS": fact_fields - {
        "schemaFingerprintSha256",
    },
    "LEGACY_APPLY_FAILURE_RECEIPT_SCHEMA":
        "fbsir.u3wDefaultOffReleaseFailureReceipt.v1",
    "APPLY_FAILURE_RECEIPT_SCHEMA":
        "fbsir.u3wDefaultOffReleaseFailureReceipt.v2",
    "LEGACY_DEPLOYMENT_RECEIPT_SCHEMA":
        "fbsir.u3wW1aDeploymentReadinessReceipt.v2",
    "DEPLOYMENT_RECEIPT_SCHEMA":
        "fbsir.u3wW1aDeploymentReadinessReceipt.v3",
    "SHA_PATTERN": re.compile(r"[0-9a-f]{64}"),
}
exec(
    compile(
        ast.Module(body=selected, type_ignores=[]),
        "<u3w-release-plan-recovery-v2>",
        "exec",
    ),
    namespace,
)
facts = {
    "publicReceiptCount": 1,
    "internalReceiptCount": 1,
    "tableCount": 2,
    "triggerCount": 2,
    "permissionCount": 1,
    "eventCount": 3,
    "probeEventCount": 3,
    "naturalEventCount": 0,
    "nonProbeEventCount": 0,
    "authoritativeProductCreditCount": 0,
    "journeyCount": 1,
    "probeJourneyCount": 1,
    "naturalJourneyCount": 0,
    "nonProbeJourneyCount": 0,
    "schemaFingerprintSha256": "f" * 64,
}
terminal = {
    "schema": "fbsir.u3wDefaultOffReleaseFailureReceipt.v2",
    "state": "APPLICATION_RESTORED_DATABASE_043_RETAINED_OR_FAIL_CLOSED",
    "releaseId": "release",
    "sourceCommit": "a" * 40,
    "applyApprovalReceiptSha256": "b" * 64,
    "migrationFacts": facts,
    "applicationStarted": True,
    "applicationAlreadyCommitted": False,
    "applicationRestored": True,
    "topologyRestored": True,
    "deploymentCommitOutcome": "NOT_COMMITTED",
    "deploymentReceiptPath": None,
    "deploymentReceiptSha256": None,
    "productionServiceChangedThisRun": True,
    "productionDatabaseChangedThisRun": False,
    "databaseRollbackStrategy": "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
    "databaseDownClaimed": False,
    "officialExpertsPackageChanged": False,
}
stage = {
    "schema": "fbsir.u3wW1aDeploymentReadinessReceipt.v3",
    "state": "STAGED_FOR_SWITCH",
    "releaseId": "release",
    "sourceCommit": "a" * 40,
    "stageApprovalReceiptSha256": "c" * 64,
    "databaseRollbackSafetyProven": True,
    "databaseDownClaimed": False,
    "actualActiveArtifactsMatched": False,
    "productionDatabaseChanged": False,
    "productionDatabaseChangedThisRun": False,
    "productionDatabaseChangedSinceStage": False,
    "productionServiceChanged": False,
    "officialExpertsPackageChanged": False,
}
recovery = {
    "productionDatabaseChanged": False,
    "productionDatabaseChangedThisRecoveryRun": False,
    "productionDatabaseChangedSinceStage": False,
}
validate_terminal = namespace["interrupted_apply_terminal_failure_valid"]
validate_stage = namespace["interrupted_apply_stage_contract_valid"]
validate_db = namespace["interrupted_recovery_database_change_valid"]
assert validate_terminal(terminal, "release", "a" * 40) is True
assert validate_stage(stage, "release", "a" * 40) is True
assert validate_db(recovery, terminal) is True

drifted = copy.deepcopy(terminal)
drifted["migrationFacts"]["naturalEventCount"] = 1
assert validate_terminal(drifted, "release", "a" * 40) is False
drifted = copy.deepcopy(stage)
drifted["productionDatabaseChangedSinceStage"] = True
assert validate_stage(drifted, "release", "a" * 40) is False
drifted = dict(recovery)
drifted["productionDatabaseChanged"] = True
assert validate_db(drifted, terminal) is False
print(json.dumps({"status": "PASS"}))
`;
  const result = spawnSync(pythonExecutable(), ["-c", harness], {
    input: pythonSource,
    encoding: "utf8",
    timeout: 15_000,
  });
  assert.equal(
    result.status,
    0,
    `release Plan recovery v2 harness failed: ${
      result.stderr || result.stdout
    }`,
  );
  assert.deepEqual(JSON.parse(result.stdout.trim()), { status: "PASS" });
});

test("plan accepts only exact untouched, rollback or interrupted recovery predecessors", () => {
  const collector = sectionBetween(
    runner,
    "function Invoke-ReadOnlyRemotePlanSnapshot {",
    "function Invoke-Plan {",
  );
  const plan = sectionBetween(
    runner,
    "function Invoke-Plan {",
    "function Get-ApprovalDigest {",
  );
  const topology = sectionBetween(
    collector,
    "dropin_exists = RELEASE_DROPIN.exists() or RELEASE_DROPIN.is_symlink()",
    "\nsnapshot = {",
  );
  assert.ok(collector.includes('"state": "UNTOUCHED_LEGACY"'));
  assert.ok(
    collector.includes(
      '"state": "EXACT_PRIOR_ROLLBACK_PREDECESSOR"',
    ),
  );
  assert.ok(
    collector.includes(
      '"state": "EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR"',
    ),
  );
  assert.ok(
    collector.includes(
      '"fbsir.u3wDefaultOffInterruptedApplyRecoveryReceipt.v1"',
    ),
  );
  assert.ok(
    collector.includes(
      '"INTERRUPTED_APPLY_RECOVERED_APPLICATION_DATABASE_043_RETAINED_DORMANT"',
    ),
  );
  assert.ok(collector.includes("prior_rollback_anchor = None"));
  assert.ok(collector.includes("prior_recovery_anchor = None"));
  assert.ok(collector.includes('"state": "INVALID_STAGE_ENTRY"'));
  assert.ok(
    collector.includes("or release_status.st_mode & 0o022"),
    "recovery release custody must accept Stage-owned 0755 directories",
  );
  assert.equal(
    collector.includes(
      "release_status.st_mode & 0o777 != 0o700",
    ),
    false,
    "recovery release custody must not contradict make_frontend_public",
  );
  assert.equal(
    (topology.match(/"priorRollbackAnchor":/g) ?? []).length,
    4,
  );
  assert.equal(
    (topology.match(/"priorRecoveryAnchor":/g) ?? []).length,
    4,
  );
  assert.match(
    topology,
    /"state": "EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR",\s+"priorRollbackAnchor": None,\s+"priorRecoveryAnchor": prior_recovery_anchor/,
  );
  for (const anchorField of [
    "releaseId",
    "sourceCommit",
    "receiptPath",
    "receiptSha256",
    "receiptSchema",
    "state",
  ]) {
    assert.ok(
      collector.includes(`"${anchorField}"`),
      `predecessor anchor field ${anchorField} is missing`,
    );
  }
  assert.ok(plan.includes("'UNTOUCHED_LEGACY'"));
  assert.ok(plan.includes("'EXACT_PRIOR_ROLLBACK_PREDECESSOR'"));
  assert.ok(
    plan.includes(
      "'EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR'",
    ),
  );
  assert.ok(plan.includes("$remote.productionChanged -ne $false"));
  assert.ok(plan.includes("$remote.productionChanged -ne $true"));
  const recoveryValidationStart = plan.lastIndexOf(
    "'EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR'",
  );
  const recoveryValidationEnd = plan.indexOf(
    "throw 'remote plan snapshot identity is invalid'",
    recoveryValidationStart,
  );
  assert.notEqual(recoveryValidationStart, -1);
  assert.notEqual(recoveryValidationEnd, -1);
  const recoveryValidation = plan.slice(
    recoveryValidationStart,
    recoveryValidationEnd,
  );
  for (const assertion of [
    "$remote.productionChanged -ne $true",
    "$remote.releaseRootExists -ne $true",
    "$remote.processConfiguredEnvironmentLoadState -ceq",
    "$null -eq $remote.stageEntryTopology.priorRecoveryAnchor",
    "$null -ne $remote.stageEntryTopology.priorRollbackAnchor",
  ]) {
    assert.ok(
      recoveryValidation.includes(assertion),
      `interrupted recovery Plan validation is missing ${assertion}`,
    );
  }
});

test("collector digest is top-level receipt evidence and never target data", () => {
  const collector = sectionBetween(
    runner,
    "function Invoke-ReadOnlyRemotePlanSnapshot {",
    "function Invoke-Plan {",
  );
  const snapshot = sectionBetween(
    collector,
    "snapshot = {",
    "\nsnapshot.update(security)",
  );
  const plan = sectionBetween(
    runner,
    "function Invoke-Plan {",
    "function Get-ApprovalDigest {",
  );
  assert.equal(snapshot.includes("collectorSha256"), false);
  assert.ok(
    plan.includes(
      "collectorSha256 = $remoteCollection.collectorSha256",
    ),
  );
  assert.ok(plan.includes("target = $remote"));
  assert.ok(
    plan.indexOf("collectorSha256 = $remoteCollection.collectorSha256") <
      plan.indexOf("target = $remote"),
  );
});

test("FBS orchestration entries expose every mutating anchor", () => {
  const commands = orchestrationContract.commands;
  const configuration = commands["w1a-default-off-configuration"].run;
  for (const token of [
    "U3W_EXPECTED_ENVIRONMENT_SHA256",
    "U3W_EXPECTED_CONFIGURED_ENVIRONMENT_SHA256",
    "U3W_PREDECESSOR_CONFIGURATION_RECEIPT_SHA256",
    "U3W_ORIGINAL_APPROVAL_RECEIPT_SHA256",
  ]) {
    assert.ok(configuration.includes(token), `${token} is missing`);
  }
  const baseline = commands["w1a-legacy-baseline"].run;
  assert.ok(baseline.includes("U3W_EXPECTED_ADOPTION_RECEIPT_SHA256"));
  assert.ok(baseline.includes("U3W_ORIGINAL_APPROVAL_RECEIPT_SHA256"));
  const dependency =
    commands["w1a-admin-root-dependency-adoption"].run;
  for (const token of [
    "U3W_ADMIN_ROOT_DEPENDENCY_MODE",
    "U3W_LEGACY_BASELINE_RECEIPT_SHA256",
    "U3W_PREDECESSOR_BACKUP_RECEIPT_SHA256",
    "U3W_APPROVAL_RECEIPT",
  ]) {
    assert.ok(dependency.includes(token), `${token} is missing`);
  }
  const backupCommand = commands["w1a-production-backup-restore"].run;
  for (const token of [
    "U3W_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256",
    "U3W_BACKUP_PLAN_RECEIPT",
    "U3W_BACKUP_PLAN_RECEIPT_SHA256",
    "U3W_ANCHOR_OUTPUT_DIRECTORY",
  ]) {
    assert.ok(backupCommand.includes(token), `${token} is missing`);
  }
  const releaseCommand = commands["w1a-default-off-release"].run;
  for (const token of [
    "U3W_RELEASE_PLAN_RECEIPT_SHA256",
    "U3W_BACKUP_RECEIPT_SHA256",
    "U3W_BACKUP_PLAN_RECEIPT_SHA256",
    "U3W_LEGACY_BASELINE_RECEIPT_SHA256",
    "U3W_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256",
    "U3W_CONFIGURATION_RECEIPT_SHA256",
    "U3W_STAGE_RECEIPT_SHA256",
    "U3W_DEPLOYMENT_RECEIPT_SHA256",
    "U3W_APPROVAL_RECEIPT",
  ]) {
    assert.ok(releaseCommand.includes(token), `${token} is missing`);
  }
});

test("release approvals bind every preparation and predecessor anchor", () => {
  for (const field of [
    "expectedBuildReceiptSha256",
    "expectedReleasePlanReceiptSha256",
    "expectedBackupReceiptSha256",
    "expectedLegacyBaselineReceiptSha256",
    "expectedAdminRootDependencyAdoptionReceiptSha256",
    "expectedConfigurationReceiptSha256",
    "expectedStageReceiptSha256",
    "expectedDeploymentReceiptSha256",
    "runnerSha256",
    "workerSha256",
  ]) {
    assert.ok(runner.includes(field), `${field} approval binding is missing`);
  }
});

test("approval bytes are externally immutable before every remote action", () => {
  assert.ok(runner.includes("function Save-ExternalApprovalAnchor"));
  assert.ok(runner.includes("approval bytes drifted before external persistence"));
  for (const [mode, next] of [
    ["Stage", "Apply"],
    ["Apply", "Rollback"],
    ["Rollback", "Verify"],
    ["Verify", null],
  ]) {
    const start = `function Invoke-${mode} {`;
    const body = next
      ? sectionBetween(runner, start, `function Invoke-${next} {`)
      : runner.slice(runner.indexOf(start));
    const approval = body.indexOf("Save-ExternalApprovalAnchor");
    const workerCall = body.indexOf("Invoke-CommittedRemoteWorker");
    assert.ok(approval >= 0, `${mode} approval anchor is missing`);
    assert.ok(workerCall > approval, `${mode} mutates before approval persistence`);
  }
  for (const field of [
    "stageApprovalReceiptSha256",
    "applyApprovalReceiptSha256",
    "rollbackApprovalReceiptSha256",
    "approvalReceiptSha256",
    "transitionApprovalReceiptSha256",
  ]) {
    assert.ok(worker.includes(field), `${field} is absent from worker evidence`);
  }
});

test("nonzero workers expose and externally recover bounded failure receipts", () => {
  assert.ok(worker.includes("fbsir.u3wDefaultOffReleaseWorkerError.v2"));
  assert.ok(worker.includes("errorMessageSha256"));
  assert.equal(worker.includes('"message": str(error)'), false);
  assert.ok(worker.includes("ReleaseMutationFailure"));
  assert.ok(worker.includes("validated_failure_receipt_evidence"));
  assert.ok(runner.includes("failureReceiptEvidenceValid"));
  assert.ok(runner.includes("Get-RemoteReceiptBytes"));
  assert.ok(runner.includes("apply|rollback)-failure-"));
  const remote = sectionBetween(
    runner,
    "function Invoke-CommittedRemoteWorker {",
    "function Write-ImmutableEvidence {",
  );
  const download = remote.indexOf(
    "$failureReceiptBytes = Get-RemoteReceiptBytes",
  );
  const envelopePersist = remote.indexOf(
    "$failureEnvelopeAnchor = Save-ExternalReleaseAnchor",
  );
  const persist = remote.indexOf(
    "$failureAnchor = Save-ExternalReleaseAnchor",
  );
  const throwAfterPersist = remote.indexOf(
    "$WorkerMode remote release worker failed; external evidence:",
  );
  assert.ok(envelopePersist >= 0);
  assert.ok(download > envelopePersist);
  assert.ok(persist > download);
  assert.ok(throwAfterPersist > persist);
});

test("committed Apply and Verify do not depend on mutable latest preparation links", () => {
  const apply = sectionBetween(
    worker,
    "def apply_release(args):",
    "\ndef anchored_evidence_json(",
  );
  assert.ok(
    apply.indexOf("if deployment_path.exists():") <
      apply.indexOf("validate_preparation_anchors(args)"),
  );
  const verify = sectionBetween(
    worker,
    "def verify_release(args):",
    "\ndef read_exact_migration_facts(",
  );
  assert.equal(verify.includes("validate_preparation_anchors(args)"), false);
  assert.ok(verify.includes("validate_deployment_receipt(args, release)"));
  const runnerAnchors = sectionBetween(
    runner,
    "function Assert-ReceiptAnchorsForMutatingMode {",
    "function Get-RemoteReceiptBytes {",
  );
  assert.ok(runnerAnchors.includes("$Mode -eq 'Apply'"));
  assert.ok(runnerAnchors.includes("$ExpectedDeploymentReceiptSha256"));
  assert.ok(runnerAnchors.includes("return Resolve-RecoveryContext"));
});

test("external evidence rejects reparse and hardlink aliases and becomes read-only", () => {
  const writer = sectionBetween(
    runner,
    "function Assert-NoReparsePointChain {",
    "function Save-ExternalReleaseAnchor {",
  );
  assert.ok(writer.includes("[IO.FileAttributes]::ReparsePoint"));
  assert.ok(writer.includes("fsutil.exe hardlink list"));
  assert.ok(writer.includes("[IO.FileMode]::CreateNew"));
  assert.ok(writer.includes("[IO.FileShare]::None"));
  assert.ok(writer.includes("$stream.Flush($true)"));
  assert.ok(writer.includes("[IO.FileAttributes]::ReadOnly"));
  assert.ok(writer.includes("escaped its exact release directory"));
});

test("latest lifecycle receipt advances with bounded predecessors only", () => {
  const advance = sectionBetween(
    worker,
    "def validated_latest_receipt_target():",
    "\ndef read_json(",
  );
  assert.ok(advance.includes("allowed_predecessors"));
  assert.ok(advance.includes("another lifecycle transition"));
  assert.ok(advance.includes("RUN_PATTERN.fullmatch"));
  const directLatestWrites = worker.match(
    /atomic_symlink\([^)]*LATEST_RECEIPT[^)]*\)/g,
  ) ?? [];
  assert.equal(directLatestWrites.length, 1);
});

test("stage-only interrupted Apply recovery has a dedicated immutable CAS receipt", () => {
  for (const value of [
    "CanonicalizeInterruptedApplyRecovery",
    "CANONICALIZE_W1A_INTERRUPTED_APPLY_RECOVERY",
    "fbsir.u3wDefaultOffInterruptedApplyRecoveryReceipt.v1",
    "INTERRUPTED_APPLY_RECOVERED_APPLICATION_DATABASE_043_RETAINED_DORMANT",
    "interrupted-apply-recovery-receipt.json",
    "APPLICATION_RESTORED_DATABASE_043_RETAINED_OR_FAIL_CLOSED",
    "applicationRestored",
    "topologyRestored",
    "deploymentCommitOutcome",
    "deploymentReceiptAbsent",
    "rollbackReceiptAbsent",
    "currentLinkAbsent",
    "releaseDropInMatched",
    "retainedMigrationFacts",
    "allW1aFlagsExplicitFalse",
  ]) {
    assert.ok(
      worker.includes(value),
      `missing interrupted recovery contract ${value}`,
    );
  }
  assert.ok(
    worker.includes(
      "advance_latest_receipt(path, [stage_path])",
    ),
  );
  assert.ok(
    worker.includes(
      "EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR",
    ),
  );
});

test("readiness rejects stale plans and target drift", () => {
  assert.ok(readiness.includes("releasePlanTimeValid"));
  assert.ok(readiness.includes("TotalHours -le 24"));
  assert.ok(readiness.includes("releasePlanTargetMatchedLive"));
  assert.ok(readiness.includes("activeJarSha256"));
  assert.ok(readiness.includes("unitSha256"));
  assert.ok(readiness.includes("nginxConfigs"));
  assert.ok(readiness.includes("actualActiveArtifactsMatched"));
  assert.ok(readiness.includes("currentLinkResolved"));
});

test("readiness serializes the legacy baseline ancestry as a dictionary key", () => {
  assert.ok(
    readiness.includes(
      "$gitState['legacyBaselineCommitAncestorOfPreparationSourceCommit']",
    ),
  );
  assert.doesNotMatch(
    readiness,
    /Add-Member\s+`\s*\n\s*-NotePropertyName legacyBaselineCommitAncestorOfPreparationSourceCommit/,
  );
});

test("readiness compares JSON-shaped target facts without object key-order coupling", () => {
  assert.ok(readiness.includes("function Test-JsonStructuralEquality"));
  const structuralEquality = sectionBetween(
    readiness,
    "function Test-JsonStructuralEquality",
    "function Get-CommittedFileSha256",
  );
  assert.ok(
    structuralEquality.includes(
      "-Left ($leftItems[$index]) `\n" +
        "                    -Right ($rightItems[$index])",
    ),
  );
  assert.ok(
    structuralEquality.includes(
      "$Left.psobject.BaseObject -is " +
        "[System.Management.Automation.PSCustomObject]",
    ),
  );
  assert.ok(
    structuralEquality.includes(
      "$Right.psobject.BaseObject -is " +
        "[System.Management.Automation.PSCustomObject]",
    ),
  );
  assert.equal(
    structuralEquality.includes("BaseObject -is [pscustomobject]"),
    false,
  );
  const targetMatch = sectionBetween(
    readiness,
    "$plannedTarget = $gitState.releasePlanTarget",
    "$gitState.releasePlanTargetMatchedLive = $releaseTargetMatched",
  );
  assert.ok(targetMatch.includes("Test-JsonStructuralEquality"));
  assert.equal(targetMatch.includes("ConvertTo-Json"), false);
});

test("plan, readiness and worker use the one real release drop-in", () => {
  const dropin = "20-u3w-default-off-release.conf";
  for (const [name, source] of [
    ["runner", runner],
    ["readiness", readiness],
    ["worker", worker],
  ]) {
    assert.ok(source.includes(dropin), `${name} drop-in is split`);
    assert.equal(
      source.includes("50-w1a-release.conf"),
      false,
      `${name} still names the obsolete drop-in`,
    );
  }
});

test("control-plane worker proof cases use literal hashtable keys", () => {
  assert.equal(controlPlane.includes("@{$Value ="), false);
  for (const value of [
    "$proof.firstApply",
    "$proof.exactAppliedRecovery",
    "$proof.runningRecovery",
  ]) {
    assert.ok(controlPlane.includes(`@{Value = ${value}`));
  }
});

test("rollback and verify survive plan expiry, cleaned builds and GitHub outage", () => {
  assert.ok(runner.includes("Assert-RecoveryRunnerExact"));
  assert.ok(runner.includes("Get-RemoteDeploymentContext"));
  assert.ok(runner.includes("Resolve-RecoveryContext"));
  assert.ok(runner.includes("githubRequired = $false"));
  assert.ok(runner.includes("cleanWorktreeRequired = $false"));
  const rollback = runner.match(
    /function Invoke-Rollback \{([\s\S]*?)\n\}/,
  )?.[1];
  const verify = runner.match(
    /function Invoke-Verify \{([\s\S]*?)\n\}/,
  )?.[1];
  assert.ok(rollback);
  assert.ok(verify);
  assert.equal(rollback.includes("Assert-StrictHead"), false);
  assert.equal(verify.includes("Assert-StrictHead"), false);
  assert.equal(verify.includes("Invoke-ReadinessGate"), false);
  assert.ok(verify.includes(
    "POST_DEPLOYMENT_ONLY_NO_PLAN_TTL_OR_GITHUB_DEPENDENCY",
  ));
});

test("readiness consumes exact schema adversarial proofs and monotonic ledgers", () => {
  assert.ok(readiness.includes("event_object_table IN"));
  assert.ok(readiness.includes("information_schema.referential_constraints"));
  assert.ok(readiness.includes("HEX(update_rule)"));
  assert.ok(readiness.includes("HEX(delete_rule)"));
  assert.ok(readiness.includes("extraTriggerFingerprintRejected"));
  assert.ok(readiness.includes("cascadingFkFingerprintRejected"));
  assert.ok(readiness.includes("permissionSemanticFingerprintRejected"));
  assert.ok(readiness.includes('|2|2|1|1|1"'));
  assert.ok(readiness.includes("migration_baseline_valid"));
  assert.ok(readiness.includes(
    '>= migration_baseline.get("eventCount")',
  ));
  assert.ok(readiness.includes(
    '>= migration_baseline.get("journeyCount")',
  ));
  assert.equal(
    readiness.includes(
      'receipt.get("migrationFacts", {}).get("eventCount") == 0',
    ),
    false,
  );
});

test("rollback database-unavailable state has an immutable verification chain", () => {
  assert.ok(runner.includes("rollback-verification-receipt.json"));
  assert.ok(runner.includes(
    "fbsir.u3wDefaultOffRollbackVerificationReceipt.v2",
  ));
  assert.ok(runner.includes("rollback-original"));
  assert.ok(readiness.includes(
    "fbsir.u3wDefaultOffRollbackVerificationReceipt.v2",
  ));
  assert.ok(readiness.includes("rollbackReceiptSha256"));
  assert.ok(readiness.includes("retainedMigrationFacts"));
});
