import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { evaluateProductionReadiness } from "./independent-board-production-readiness.mjs";

const commit = "a".repeat(40);
const digest = "b".repeat(64);
const liveFactsDigest = "c".repeat(64);
const databaseServerUuid = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
const readinessRunner = fs.readFileSync(
  new URL("./verify-independent-board-production-readiness.ps1", import.meta.url),
  "utf8",
);
const releaseWorker = fs.readFileSync(
  new URL("./u3w-default-off-release-remote.py", import.meta.url),
  "utf8",
);
const javaEventVerifier = fs.readFileSync(
  new URL(
    "../FBSir-business/src/main/java/com/wx/fbsir/business/board/"
      + "attribution/receipt/BoardAttributionEventV1Verifier.java",
    import.meta.url,
  ),
  "utf8",
);

function embeddedCollectorSource() {
  const match = readinessRunner.match(
    /\$remotePython = @'\r?\n([\s\S]*?)\r?\n'@/,
  );
  assert.ok(match, "embedded readiness collector must be extractable");
  return match[1].replace(/__[A-Z0-9_]+__/g, '""');
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

function pythonStringTuple(source, name) {
  const match = source.match(
    new RegExp(`${name} = \\(\\r?\\n([\\s\\S]*?)\\r?\\n\\)`),
  );
  assert.ok(match, `${name} must be an extractable Python tuple`);
  return [...match[1].matchAll(/^\s*"([^"]+)",?\s*$/gm)]
    .map((item) => item[1]);
}

const schemaFingerprint =
  "fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d";
const migrations = Array.from(
  { length: 8 },
  (_, index) => `public_init_0${35 + index}`,
);
const flags = [
  "FBSIR_BOARD_ATTRIBUTION_ENABLED",
  "FBSIR_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
  "FBSIR_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
  "FBSIR_BOARD_ATTRIBUTION_CREDIT_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_CREDIT_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_WRITER_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_INTENT_CLASSIFIER_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_ADMIN_READ_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED",
];
const managedEnvironmentNames = [
  ...flags,
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY_ID",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET",
  "FBSIR_ENGINE_TOKEN",
].sort();
const migrationDescriptions = {
  public_init_035:
    "APPLIED:Independent Board OAuth consent-intent lineage",
  public_init_036:
    "APPLIED:Independent Board OAuth refresh security receipt v2",
  public_init_037:
    "APPLIED:Independent Board exact product attribution evidence contract",
  public_init_038:
    "APPLIED:Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger",
  public_init_039:
    "APPLIED:Independent Board immutable plan policy revisions and operation lineage",
  public_init_040:
    "APPLIED:Independent Board plan policy database monotonic-chain guards",
  public_init_041:
    "APPLIED:Independent Board plan policy controlled procedure authority",
  public_init_042:
    "APPLIED:Independent Board default-off skill-consume v2 credit ledger",
};

function readySnapshot() {
  return {
    observedAt: "2026-07-23T14:00:00.000Z",
    local: {
      clean: true,
      sourceCommit: commit,
      expectedSourceCommit: commit,
      branch: "codex/w1-official-attribution-closure",
      upstreamCommit: commit,
      originHead: commit,
      upstreamOriginAligned: true,
      w1a043CompatibilityVersions: ["8.0.30", "8.0.45", "8.4.8"],
      w1a043CompatibilityReceipts: [
        {
          path: "reports/independent-board/w1a-attribution-v1-dual-mysql-latest.json",
          sha256: digest,
        },
      ],
      canonicalBaselineCompatibilityVersions: ["8.0.30", "8.4.8"],
      releasePlanVerified: true,
      releasePlanTargetMatchedLive: true,
      releasePlanSourceCommit: commit,
      releaseRunnerContractVersion:
        "fbsir.u3wDefaultOffReleaseRunner.v2",
      releasePlanReceiptPath:
        "reports/independent-board/w1a-default-off-release-plan-latest.json",
      releasePlanReceiptSha256: digest,
      preparationSourceCommit: commit,
      preparationCommitAncestorOfSourceCommit: true,
      preparationSourceCommitsConsistent: true,
      legacyBaselineCommitAncestorOfPreparationSourceCommit: true,
    },
    target: {
      host: "api2.u3w.com",
      authorityObserved: "root",
      serviceUnit: "fbsir-admin.service",
      serviceState: "active",
    },
    runtime: {
      jarPath: "/opt/fbsir/admin/releases/1/fbsir-admin.jar",
      jarSha256: digest,
      processDatabaseBindingMatched: true,
      processSecurityConfigurationHmacSha256: digest,
      configuredEnvironmentHmacSha256: digest,
      processConfiguredEnvironmentMatched: true,
      processConfiguredEnvironmentMismatchNames: [],
      processPendingRestartEnvironmentNames: [],
      processConfiguredEnvironmentLoadState: "EXACT_CONFIGURED",
      processConfiguredEnvironmentPreStageCompatible: true,
      attributionClassCount: 0,
    },
    database: {
      serverVersion: "8.0.30",
      database: "fbsir",
      databaseServerUuid,
      totalTableCount: 120,
      migrationTableCount: 1,
      migrationVersions: migrations,
      migrationDescriptions: { ...migrationDescriptions },
      publicInit043Applied: false,
      boardAttributionTableCount: 0,
      boardAttributionTriggerCount: 0,
      boardAttributionPermissionCount: 0,
      boardAttributionInternalReceiptCount: 0,
      boardAttributionEventCount: 0,
      boardAttributionProbeEventCount: 0,
      boardAttributionNaturalEventCount: 0,
      boardAttributionNonProbeEventCount: 0,
      boardAttributionAuthoritativeProductCreditCount: 0,
      boardAttributionJourneyCount: 0,
      boardAttributionProbeJourneyCount: 0,
      boardAttributionNaturalJourneyCount: 0,
      boardAttributionNonProbeJourneyCount: 0,
      publicInit043AnyReceiptCount: 0,
      legacyAdminRootDependencyState: "EXACT_CONTROLLED_DEPENDENCY",
      legacyAdminRootDependencyStateVerified: true,
      legacyAdminRootDependencyFactsSha256: digest,
      legacyAdminRootDependencyReceiptCount: 1,
      legacyAdminRootDependencyVersionCount: 1,
      legacyAdminRootIdentityCount: 1,
      legacyAdminRootExactCount: 1,
      legacyAdminRootRoleBindingCount: 0,
      legacyAdminRootPageChildCount: 0,
      legacyForbiddenPublicInit001Through042ReceiptCount: 0,
      w1a043State: "ABSENT",
      w1aSchemaFingerprintSha256: null,
      adminRootDependencyAdoptionW1a043State: "ABSENT",
      legacyBaselineReceiptSha256: digest,
      legacyBaselineBackupBundleReceiptSha256: digest,
      adminRootDependencyAdoptionReceiptPath:
        "/opt/fbsir/admin/dependencies/latest/adoption-receipt.json",
      adminRootDependencyAdoptionReceiptSchema:
        "fbsir.u3wLegacyAdminRootDependencyAdoptionReceipt.v3",
      adminRootDependencyAdoptionReceiptSha256: digest,
      adminRootDependencyAdoptionReceiptValid: true,
      adminRootDependencyAdoptionReceiptAnchorMatched: true,
      adminRootDependencyAdoptionSourceCommit: commit,
      adminRootDependencyAdoptionDatabaseServerUuid: databaseServerUuid,
      adminRootDependencyAdoptionLiveFactsSha256: liveFactsDigest,
      adminRootDependencyAdoptionPlanReceiptSha256: digest,
      adminRootDependencyAdoptionDependencyRowsFingerprintSha256: digest,
      adminRootDependencyAdoptionLiveFactsMatched: true,
      adminRootDependencyAdoptionBaselineReceiptSha256: digest,
      adminRootDependencyAdoptionBackupReceiptSha256: digest,
      adminRootDependencyAdoptionHistoricalBindingsMatched: true,
    },
    portals: {
      meHttpStatus: 404,
      adminHttpStatus: 404,
      u3wDirectCaptcha: { httpStatus: 200, businessCode: 200 },
      meApiCaptcha: { httpStatus: 404, businessCode: null },
      adminApiCaptcha: { httpStatus: 404, businessCode: null },
    },
    configuration: {
      environmentKeyNames: [...flags, "FBSIR_ENGINE_TOKEN"],
      explicitFalseKeyNames: [...flags],
      eventKeyEntryCount: 1,
      activeEventKeyPairPresent: true,
      activeEventKeyId: "wave1-k1",
      previousEventKeyPairComplete: true,
      sameBindingSecretPresent: true,
      stagedApi2EventKeyMaterialMatched: true,
      api2EventKeyFileCustodySecure: true,
      environmentFilePathExact: true,
      environmentFileCustodySecure: true,
      cryptographicConfigurationShapeValid: true,
      adminEngineCredentialValid: true,
      adminEngineCredentialIndependent: true,
      configurationReceiptValid: true,
      configurationReceiptAnchorMatched: true,
      configurationReceiptSchema:
        "fbsir.u3wDefaultOffConfigurationReceipt.v3",
      engineCounterpartClosureClaimed: false,
      configurationReceiptSourceCommit: commit,
      configurationReceiptSha256: digest,
    },
    backup: {
      receiptPath: "/opt/fbsir/admin/backups/latest/receipt.json",
      runId: "w1a-20260724T120000Z-0123456789ab",
      bundleSchema: "fbsir.u3wDatabaseBackupRestoreBundleReceipt.v3",
      backupReceiptSchema: "fbsir.u3wDatabaseBackupReceipt.v4",
      restoreReceiptSchema:
        "fbsir.u3wDatabaseRestoreRehearsalReceipt.v4",
      externalAnchorSchema:
        "fbsir.u3wDatabaseBackupRestoreExternalAnchor.v3",
      planReceiptSha256: digest,
      sourceCommit: commit,
      sourceDatabaseServerUuid: databaseServerUuid,
      adminRootDependencyAdoptionReceiptSha256: digest,
      proven: true,
      receiptAnchorMatched: true,
      externalAnchorVerified: true,
      adoptionReceiptBindingMatched: true,
      sourceDatabaseServerUuidMatched: true,
      backupRestoreSchemaFactsMatched: true,
      sourceRestoreObservationManifestMatched: true,
      restoredManifestObserved: true,
      sourceSnapshotExactlyMatched: false,
      sha256: digest,
      sizeBytes: 10,
      restoreProcedureVerified: true,
      restoreLiveFactsMatched: true,
    },
    deploymentChannel: {
      state: null,
      receiptValidated: false,
      receiptAnchorMatched: false,
      sourceCommit: null,
      strictHeadBuildUploadSwitchReceiptScriptPresent: false,
      applicationRollbackAssemblyVerified: false,
      applicationRollbackProven: false,
      databaseRollbackSafetyProven: false,
      databaseDownClaimed: null,
      actualActiveArtifactsMatched: false,
      currentLinkResolved: null,
      u3wDirectCaptchaHealthy: false,
      mePortalApiHealthy: false,
      adminPortalApiHealthy: false,
      mePortalReleaseMarkerMatched: false,
      adminPortalReleaseMarkerMatched: false,
    },
  };
}

function setExactRetained043(snapshot) {
  Object.assign(snapshot.database, {
    publicInit043Applied: true,
    w1a043State: "EXACT_043_RETAINED_DORMANT",
    adminRootDependencyAdoptionW1a043State:
      "EXACT_043_RETAINED_DORMANT",
    publicInit043AnyReceiptCount: 1,
    boardAttributionInternalReceiptCount: 1,
    boardAttributionTableCount: 2,
    boardAttributionTriggerCount: 2,
    boardAttributionPermissionCount: 1,
    boardAttributionEventCount: 3,
    boardAttributionProbeEventCount: 3,
    boardAttributionNaturalEventCount: 0,
    boardAttributionNonProbeEventCount: 0,
    boardAttributionAuthoritativeProductCreditCount: 0,
    boardAttributionJourneyCount: 1,
    boardAttributionProbeJourneyCount: 1,
    boardAttributionNaturalJourneyCount: 0,
    boardAttributionNonProbeJourneyCount: 0,
    w1aSchemaFingerprintSha256: schemaFingerprint,
  });
  if (!snapshot.database.migrationVersions.includes("public_init_043")) {
    snapshot.database.migrationVersions.push("public_init_043");
  }
  snapshot.database.migrationDescriptions.public_init_043 =
    "APPLIED:Independent Board exact official experts attribution v1";
  return snapshot;
}

test("preparation can pass before any production upload or switch", () => {
  const result = evaluateProductionReadiness(readySnapshot());
  assert.equal(result.status, "PREPARED_FOR_STAGE");
  assert.equal(result.readyForDefaultOffRelease, true);
  assert.deepEqual(result.failedGateIds, []);
  assert.equal(result.productionChanged, false);
  assert.deepEqual(result.postDeployFailedGateIds, [
    "staged_release_receipt",
    "deployed_default_off_receipt",
  ]);
});

test("accepts one byte-identical ancestor preparation commit", () => {
  const snapshot = readySnapshot();
  const releaseCommit = "c".repeat(40);
  snapshot.local.sourceCommit = releaseCommit;
  snapshot.local.expectedSourceCommit = releaseCommit;
  snapshot.local.upstreamCommit = releaseCommit;
  snapshot.local.originHead = releaseCommit;
  snapshot.local.releasePlanSourceCommit = releaseCommit;
  snapshot.local.preparationSourceCommit = commit;
  snapshot.local.preparationCommitAncestorOfSourceCommit = true;
  snapshot.configuration.configurationReceiptSourceCommit = commit;
  snapshot.backup.sourceCommit = commit;
  const result = evaluateProductionReadiness(snapshot);
  assert.equal(result.status, "PREPARED_FOR_STAGE");
  assert.deepEqual(result.failedGateIds, []);
});

test("rejects a preparation commit without proven ancestry", () => {
  const snapshot = readySnapshot();
  snapshot.local.preparationCommitAncestorOfSourceCommit = false;
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("preparation_source_provenance"));
});

test("requires config v3, backup v4, and adoption to share one preparation source", () => {
  for (const mutate of [
    (snapshot) => {
      snapshot.configuration.configurationReceiptSchema =
        "fbsir.u3wDefaultOffConfigurationReceipt.v2";
    },
    (snapshot) => {
      snapshot.configuration.configurationReceiptSourceCommit =
        "c".repeat(40);
    },
    (snapshot) => {
      snapshot.backup.sourceCommit = "c".repeat(40);
    },
    (snapshot) => {
      snapshot.database.adminRootDependencyAdoptionSourceCommit =
        "c".repeat(40);
    },
  ]) {
    const snapshot = readySnapshot();
    mutate(snapshot);
    const result = evaluateProductionReadiness(snapshot);
    assert.ok(
      result.failedGateIds.includes("preparation_source_provenance"),
    );
  }
});

test("allows a historical baseline commit only when it is an ancestor of preparation", () => {
  const snapshot = readySnapshot();
  snapshot.database.serverVersion = "8.0.45";
  snapshot.database.migrationVersions = [];
  snapshot.database.migrationDescriptions = {};
  snapshot.database.schemaBaselineMode = "LEGACY_ADOPTED_W1A_V2";
  snapshot.database.legacyBaselineReceiptValid = true;
  snapshot.database.legacyBaselineReceiptAnchorMatched = true;
  snapshot.database.legacyBaselineLiveFactsMatched = true;
  snapshot.database.legacyBaselineReceiptDigest = digest;
  snapshot.database.legacyBaselineSourceCommit = "c".repeat(40);

  assert.equal(
    evaluateProductionReadiness(snapshot).status,
    "PREPARED_FOR_STAGE",
  );

  snapshot.local.legacyBaselineCommitAncestorOfPreparationSourceCommit =
    false;
  assert.ok(
    evaluateProductionReadiness(snapshot).failedGateIds.includes(
      "versioned_schema_baseline",
    ),
  );
});

test("embedded collector has no stale environment_files alias", () => {
  assert.equal(/\benvironment_files\b/.test(readinessRunner), false);
  assert.ok(readinessRunner.includes("environment_file_paths"));
});

test("fails closed for a dirty or non-exact source tree", () => {
  const snapshot = readySnapshot();
  snapshot.local.clean = false;
  snapshot.local.expectedSourceCommit = "c".repeat(40);
  const result = evaluateProductionReadiness(snapshot);
  assert.deepEqual(result.failedGateIds, ["strict_head"]);
});

test("strict head requires a named branch aligned to upstream and origin", () => {
  for (const [field, value] of [
    ["branch", ""],
    ["upstreamCommit", "d".repeat(40)],
    ["originHead", "e".repeat(40)],
    ["upstreamOriginAligned", false],
  ]) {
    const snapshot = readySnapshot();
    snapshot.local[field] = value;
    assert.ok(
      evaluateProductionReadiness(snapshot).failedGateIds.includes(
        "strict_head",
      ),
      `${field} drift must fail strict_head`,
    );
  }
});

test("fails closed for an unversioned legacy schema", () => {
  const snapshot = readySnapshot();
  snapshot.database.migrationTableCount = 0;
  snapshot.database.migrationVersions = [];
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("versioned_schema_baseline"));
  assert.equal(
    result.evidence.database.missingPredecessorMigrations.length,
    8,
  );
});

test("separates 043 compatibility from full canonical baseline support", () => {
  const snapshot = readySnapshot();
  snapshot.database.serverVersion = "8.0.45";
  const verified043Only = evaluateProductionReadiness(snapshot);
  assert.equal(
    verified043Only.failedGateIds.includes("w1a_043_mysql_compatibility"),
    false,
  );
  assert.ok(
    verified043Only.failedGateIds.includes("versioned_schema_baseline"),
  );
  snapshot.local.w1a043CompatibilityVersions =
    snapshot.local.w1a043CompatibilityVersions.filter(
      (version) => version !== "8.0.45",
    );
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("w1a_043_mysql_compatibility"));
  assert.match(
    result.gates.find((item) => item.id === "w1a_043_mysql_compatibility")
      .detail,
    /public_init_043 is unverified/,
  );
});

test("accepts only an anchored and verified legacy baseline alternative", () => {
  const snapshot = readySnapshot();
  snapshot.database.serverVersion = "8.0.45";
  snapshot.database.migrationVersions = [];
  snapshot.database.migrationDescriptions = {};
  snapshot.database.schemaBaselineMode = "LEGACY_ADOPTED_W1A_V2";
  snapshot.database.legacyBaselineReceiptValid = true;
  snapshot.database.legacyBaselineReceiptAnchorMatched = true;
  snapshot.database.legacyBaselineLiveFactsMatched = true;
  snapshot.database.legacyAdminRootDependencyState =
    "EXACT_CONTROLLED_DEPENDENCY";
  snapshot.database.legacyAdminRootDependencyStateVerified = true;
  snapshot.database.legacyAdminRootDependencyReceiptCount = 1;
  snapshot.database.legacyAdminRootDependencyVersionCount = 1;
  snapshot.database.legacyAdminRootIdentityCount = 1;
  snapshot.database.legacyAdminRootExactCount = 1;
  snapshot.database.legacyAdminRootRoleBindingCount = 0;
  snapshot.database.legacyAdminRootPageChildCount = 0;
  snapshot.database.legacyForbiddenPublicInit001Through042ReceiptCount = 0;
  snapshot.database.legacyBaselineReceiptDigest = digest;
  snapshot.database.legacyBaselineSourceCommit = commit;
  const accepted = evaluateProductionReadiness(snapshot);
  assert.equal(
    accepted.failedGateIds.includes("versioned_schema_baseline"),
    false,
  );
  snapshot.database.legacyBaselineReceiptAnchorMatched = false;
  const rejected = evaluateProductionReadiness(snapshot);
  assert.ok(rejected.failedGateIds.includes("versioned_schema_baseline"));
  snapshot.database.legacyBaselineReceiptAnchorMatched = true;
  snapshot.database.legacyBaselineLiveFactsMatched = false;
  const selfAttestedOnly = evaluateProductionReadiness(snapshot);
  assert.ok(
    selfAttestedOnly.failedGateIds.includes("versioned_schema_baseline"),
  );
  snapshot.database.legacyBaselineLiveFactsMatched = true;
  snapshot.database.legacyAdminRootDependencyStateVerified = false;
  const unprovenDependency = evaluateProductionReadiness(snapshot);
  assert.ok(
    unprovenDependency.failedGateIds.includes("versioned_schema_baseline"),
  );
});

test("rejects a legacy baseline when the required 043 admin root is absent", () => {
  const snapshot = readySnapshot();
  snapshot.database.serverVersion = "8.0.45";
  snapshot.database.migrationVersions = [];
  snapshot.database.migrationDescriptions = {};
  snapshot.database.schemaBaselineMode = "LEGACY_ADOPTED_W1A_V2";
  snapshot.database.legacyBaselineReceiptValid = true;
  snapshot.database.legacyBaselineReceiptAnchorMatched = true;
  snapshot.database.legacyBaselineLiveFactsMatched = true;
  snapshot.database.legacyAdminRootDependencyState = "ABSENT";
  snapshot.database.legacyAdminRootDependencyStateVerified = true;
  snapshot.database.legacyAdminRootDependencyReceiptCount = 0;
  snapshot.database.legacyAdminRootDependencyVersionCount = 0;
  snapshot.database.legacyAdminRootIdentityCount = 0;
  snapshot.database.legacyAdminRootExactCount = 0;
  snapshot.database.legacyAdminRootRoleBindingCount = 0;
  snapshot.database.legacyAdminRootPageChildCount = 0;
  snapshot.database.legacyForbiddenPublicInit001Through042ReceiptCount = 0;
  snapshot.database.legacyBaselineReceiptDigest = digest;
  snapshot.database.legacyBaselineSourceCommit = commit;

  const result = evaluateProductionReadiness(snapshot);

  assert.ok(result.failedGateIds.includes("versioned_schema_baseline"));
});

test("fails closed when a migration receipt description drifts", () => {
  const snapshot = readySnapshot();
  snapshot.database.migrationDescriptions.public_init_041 = "forged";
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("versioned_schema_baseline"));
  assert.deepEqual(
    result.evidence.database.driftedPredecessorDescriptions,
    ["public_init_041"],
  );
});

test("rejects pre-apply migration descriptions as incomplete receipts", () => {
  const snapshot = readySnapshot();
  snapshot.database.migrationDescriptions.public_init_035 =
    "Independent Board OAuth consent-intent lineage";
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("versioned_schema_baseline"));
  assert.deepEqual(
    result.evidence.database.driftedPredecessorDescriptions,
    ["public_init_035"],
  );
});

test("requires the exact 043 schema and trigger fingerprint when applied", () => {
  const snapshot = readySnapshot();
  snapshot.database.publicInit043Applied = true;
  snapshot.database.migrationVersions.push("public_init_043");
  snapshot.database.boardAttributionTableCount = 2;
  snapshot.database.boardAttributionTriggerCount = 2;
  snapshot.database.boardAttributionPermissionCount = 1;
  snapshot.database.boardAttributionInternalReceiptCount = 1;
  snapshot.database.w1aSchemaFingerprintSha256 = digest;
  const failed = evaluateProductionReadiness(snapshot);
  assert.ok(failed.failedGateIds.includes("w1a_schema_state"));
  snapshot.database.w1aSchemaFingerprintSha256 = schemaFingerprint;
  const passed = evaluateProductionReadiness(snapshot);
  assert.equal(passed.failedGateIds.includes("w1a_schema_state"), false);
});

test("requires the 043 fingerprint to be absent when the schema is absent", () => {
  const snapshot = readySnapshot();
  snapshot.database.w1aSchemaFingerprintSha256 = schemaFingerprint;
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("w1a_schema_state"));
  assert.ok(
    result.failedGateIds.includes("admin_root_dependency_adoption_anchor"),
  );
});

test("rejects partial W1A objects without the public 043 receipt", () => {
  for (const field of [
    "boardAttributionTableCount",
    "boardAttributionTriggerCount",
    "boardAttributionPermissionCount",
    "boardAttributionInternalReceiptCount",
  ]) {
    const snapshot = readySnapshot();
    snapshot.database[field] = 1;
    const result = evaluateProductionReadiness(snapshot);
    assert.ok(
      result.failedGateIds.includes("w1a_schema_state"),
      `${field} must fail closed`,
    );
  }
});

test("fails closed when the database backup cannot be restored", () => {
  const snapshot = readySnapshot();
  snapshot.backup.restoreProcedureVerified = false;
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("backup_restore_anchor"));
});

test("rejects a restore receipt that only self-attests success", () => {
  const snapshot = readySnapshot();
  snapshot.backup.restoreLiveFactsMatched = false;
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("backup_restore_anchor"));
});

test("accepts only the final v4/v4/v3 backup chain and v3 external anchor", () => {
  for (const [field, staleSchema] of [
    ["backupReceiptSchema", "fbsir.u3wDatabaseBackupReceipt.v2"],
    [
      "restoreReceiptSchema",
      "fbsir.u3wDatabaseRestoreRehearsalReceipt.v2",
    ],
    ["bundleSchema", "fbsir.u3wDatabaseBackupRestoreBundleReceipt.v1"],
    [
      "externalAnchorSchema",
      "fbsir.u3wDatabaseBackupRestoreExternalAnchor.v1",
    ],
  ]) {
    const snapshot = readySnapshot();
    snapshot.backup[field] = staleSchema;
    assert.ok(
      evaluateProductionReadiness(snapshot).failedGateIds.includes(
        "backup_restore_anchor",
      ),
      `${field} must reject ${staleSchema}`,
    );
  }
});

test("rejects a final backup chain when adoption, UUID, facts, or anchor unbind", () => {
  for (const field of [
    "externalAnchorVerified",
    "adoptionReceiptBindingMatched",
    "sourceDatabaseServerUuidMatched",
    "backupRestoreSchemaFactsMatched",
    "restoredManifestObserved",
  ]) {
    const snapshot = readySnapshot();
    snapshot.backup[field] = false;
    assert.ok(
      evaluateProductionReadiness(snapshot).failedGateIds.includes(
        "backup_restore_anchor",
      ),
      `${field} must fail closed`,
    );
  }
  const planDigestDrift = readySnapshot();
  planDigestDrift.backup.planReceiptSha256 = "not-a-sha256";
  assert.ok(
    evaluateProductionReadiness(planDigestDrift).failedGateIds.includes(
      "backup_restore_anchor",
    ),
  );
  const snapshotOverclaim = readySnapshot();
  snapshotOverclaim.backup.sourceSnapshotExactlyMatched = true;
  assert.ok(
    evaluateProductionReadiness(snapshotOverclaim).failedGateIds.includes(
      "backup_restore_anchor",
    ),
  );
  const observationDifference = readySnapshot();
  observationDifference.backup.sourceRestoreObservationManifestMatched =
    false;
  assert.equal(
    evaluateProductionReadiness(observationDifference).failedGateIds.includes(
      "backup_restore_anchor",
    ),
    false,
  );
  const adoptionDigestDrift = readySnapshot();
  adoptionDigestDrift.backup.adminRootDependencyAdoptionReceiptSha256 =
    "c".repeat(64);
  assert.ok(
    evaluateProductionReadiness(
      adoptionDigestDrift,
    ).failedGateIds.includes("backup_restore_anchor"),
  );
  const uuidDrift = readySnapshot();
  uuidDrift.backup.sourceDatabaseServerUuid =
    "ffffffff-bbbb-4ccc-8ddd-eeeeeeeeeeee";
  assert.ok(
    evaluateProductionReadiness(uuidDrift).failedGateIds.includes(
      "backup_restore_anchor",
    ),
  );
});

test("requires an externally anchored adoption receipt and exact dormant 043 state", () => {
  const exactSnapshot = readySnapshot();
  const exactResult = evaluateProductionReadiness(exactSnapshot);
  assert.equal(exactResult.status, "PREPARED_FOR_STAGE");
  assert.equal(
    exactResult.evidence.database
      .adminRootDependencyAdoptionLiveFactsSha256,
    liveFactsDigest,
  );
  assert.equal(
    exactResult.evidence.database
      .adminRootDependencyAdoptionDependencyRowsFingerprintSha256,
    digest,
  );
  assert.equal(
    exactResult.evidence.database
      .adminRootDependencyAdoptionPlanReceiptSha256,
    digest,
  );
  const retainedSnapshot = setExactRetained043(readySnapshot());
  assert.equal(
    evaluateProductionReadiness(retainedSnapshot).status,
    "PREPARED_FOR_STAGE",
  );
  const retainedAdoptionStateDrift = structuredClone(retainedSnapshot);
  retainedAdoptionStateDrift.database
    .adminRootDependencyAdoptionW1a043State = "ABSENT";
  assert.ok(
    evaluateProductionReadiness(
      retainedAdoptionStateDrift,
    ).failedGateIds.includes("admin_root_dependency_adoption_anchor"),
  );
  const retainedNaturalDrift = setExactRetained043(readySnapshot());
  retainedNaturalDrift.database.boardAttributionProbeEventCount = 2;
  retainedNaturalDrift.database.boardAttributionNaturalEventCount = 1;
  retainedNaturalDrift.database.boardAttributionNonProbeEventCount = 1;
  assert.ok(
    evaluateProductionReadiness(
      retainedNaturalDrift,
    ).failedGateIds.includes("w1a_schema_state"),
  );
  const retainedNegativeLedger = setExactRetained043(readySnapshot());
  retainedNegativeLedger.database.boardAttributionEventCount = -1;
  retainedNegativeLedger.database.boardAttributionProbeEventCount = -1;
  assert.ok(
    evaluateProductionReadiness(
      retainedNegativeLedger,
    ).failedGateIds.includes("w1a_schema_state"),
  );
  for (const field of [
    "adminRootDependencyAdoptionReceiptValid",
    "adminRootDependencyAdoptionReceiptAnchorMatched",
    "adminRootDependencyAdoptionLiveFactsMatched",
    "adminRootDependencyAdoptionHistoricalBindingsMatched",
  ]) {
    const snapshot = readySnapshot();
    snapshot.database[field] = false;
    assert.ok(
      evaluateProductionReadiness(snapshot).failedGateIds.includes(
        "admin_root_dependency_adoption_anchor",
      ),
      `${field} must fail closed`,
    );
  }
  for (const field of [
    "publicInit043AnyReceiptCount",
    "boardAttributionInternalReceiptCount",
    "boardAttributionTableCount",
    "boardAttributionTriggerCount",
    "boardAttributionPermissionCount",
  ]) {
    const snapshot = readySnapshot();
    snapshot.database[field] = 1;
    assert.ok(
      evaluateProductionReadiness(snapshot).failedGateIds.includes(
        "admin_root_dependency_adoption_anchor",
      ),
      `${field} must prove zero`,
    );
  }
  const fingerprintDrift = readySnapshot();
  fingerprintDrift.database
    .adminRootDependencyAdoptionDependencyRowsFingerprintSha256 =
    "d".repeat(64);
  assert.ok(
    evaluateProductionReadiness(fingerprintDrift).failedGateIds.includes(
      "admin_root_dependency_adoption_anchor",
    ),
  );
  const invalidLiveFactsDigest = readySnapshot();
  invalidLiveFactsDigest.database
    .adminRootDependencyAdoptionLiveFactsSha256 = "not-a-sha256";
  assert.ok(
    evaluateProductionReadiness(
      invalidLiveFactsDigest,
    ).failedGateIds.includes("admin_root_dependency_adoption_anchor"),
  );
  const invalidPlanDigest = readySnapshot();
  invalidPlanDigest.database
    .adminRootDependencyAdoptionPlanReceiptSha256 = "not-a-sha256";
  assert.ok(
    evaluateProductionReadiness(invalidPlanDigest).failedGateIds.includes(
      "admin_root_dependency_adoption_anchor",
    ),
  );
});

test("requires all production flags to be explicit and false", () => {
  const snapshot = readySnapshot();
  snapshot.configuration.environmentKeyNames =
    snapshot.configuration.environmentKeyNames.filter(
      (name) => name !== flags.at(-1),
    );
  snapshot.configuration.explicitFalseKeyNames.pop();
  snapshot.configuration.explicitFalseKeyNames.pop();
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("default_off_configuration"));
  assert.equal(result.evidence.configuration.missingExplicitFlags.length, 1);
  assert.equal(
    result.evidence.configuration.flagsNotExplicitlyFalse.length,
    2,
  );
});

test("requires legacy attribution master flags to be explicitly false", () => {
  const snapshot = readySnapshot();
  snapshot.configuration.explicitFalseKeyNames =
    snapshot.configuration.explicitFalseKeyNames.filter(
      (name) => name !== "FBSIR_BOARD_ATTRIBUTION_ENABLED",
    );
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("default_off_configuration"));
});

test("never emits secret values", () => {
  const snapshot = readySnapshot();
  snapshot.configuration.eventKeyValue = "sentinel-event-secret";
  snapshot.configuration.sameBindingSecret = "sentinel-binding-secret";
  snapshot.portals.secret = "sentinel-portal-secret";
  snapshot.deploymentChannel.secret = "sentinel-deployment-secret";
  const result = evaluateProductionReadiness(snapshot);
  const serialized = JSON.stringify(result);
  assert.equal(serialized.includes("sentinel-event-secret"), false);
  assert.equal(serialized.includes("sentinel-binding-secret"), false);
  assert.equal(serialized.includes("sentinel-portal-secret"), false);
  assert.equal(serialized.includes("sentinel-deployment-secret"), false);
});

test("requires secure and structurally valid key custody", () => {
  const snapshot = readySnapshot();
  snapshot.configuration.environmentFileCustodySecure = false;
  const insecure = evaluateProductionReadiness(snapshot);
  assert.ok(
    insecure.failedGateIds.includes("cryptographic_material_custody"),
  );

  snapshot.configuration.environmentFileCustodySecure = true;
  snapshot.configuration.previousEventKeyPairComplete = false;
  const incompleteRotation = evaluateProductionReadiness(snapshot);
  assert.ok(
    incompleteRotation.failedGateIds.includes(
      "cryptographic_material_custody",
    ),
  );
});

test("requires API2/U3W key parity and an external configuration receipt", () => {
  const snapshot = readySnapshot();
  snapshot.configuration.stagedApi2EventKeyMaterialMatched = false;
  let result = evaluateProductionReadiness(snapshot);
  assert.ok(
    result.failedGateIds.includes("cryptographic_material_custody"),
  );
  snapshot.configuration.stagedApi2EventKeyMaterialMatched = true;
  snapshot.configuration.configurationReceiptAnchorMatched = false;
  result = evaluateProductionReadiness(snapshot);
  assert.ok(
    result.failedGateIds.includes("cryptographic_material_custody"),
  );
  snapshot.configuration.configurationReceiptAnchorMatched = true;
  snapshot.configuration.configurationReceiptSourceCommit =
    "d".repeat(40);
  result = evaluateProductionReadiness(snapshot);
  assert.ok(
    result.failedGateIds.includes("cryptographic_material_custody"),
  );
});

test("requires an out-of-band anchor for the backup receipt", () => {
  const snapshot = readySnapshot();
  snapshot.backup.receiptAnchorMatched = false;
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("backup_restore_anchor"));
});

test("keeps deployment evidence separate from preparation", () => {
  const snapshot = readySnapshot();
  snapshot.deploymentChannel = {
    state: "STAGED_FOR_SWITCH",
    receiptValidated: true,
    receiptAnchorMatched: true,
    releasePlanTargetBindingVerified: true,
    sourceCommit: commit,
    strictHeadBuildUploadSwitchReceiptScriptPresent: true,
    applicationRollbackAssemblyVerified: true,
    applicationRollbackProven: false,
    databaseRollbackSafetyProven: true,
    stableDatabaseIdentityMatched: true,
    stagedLiveStateMatched: true,
    databaseDownClaimed: false,
    actualActiveArtifactsMatched: false,
  };
  const result = evaluateProductionReadiness(snapshot);
  assert.equal(result.status, "STAGED_FOR_SWITCH");
  assert.equal(result.readyForDefaultOffRelease, true);
  assert.deepEqual(result.failedGateIds, []);
  assert.deepEqual(result.postDeployFailedGateIds, [
    "deployed_default_off_receipt",
  ]);
  snapshot.deploymentChannel.stagedLiveStateMatched = false;
  assert.equal(
    evaluateProductionReadiness(snapshot).status,
    "PREPARED_FOR_STAGE",
  );
});

test("requires the active process to use the expected database binding", () => {
  const snapshot = readySnapshot();
  snapshot.runtime.processDatabaseBindingMatched = false;
  assert.ok(
    evaluateProductionReadiness(snapshot).failedGateIds.includes(
      "active_runtime_anchor",
    ),
  );
});

test("accepts exact-loaded or fully managed pending-restart runtime configuration", () => {
  const snapshot = readySnapshot();
  snapshot.runtime.processConfiguredEnvironmentMatched = false;
  snapshot.runtime.processConfiguredEnvironmentLoadState =
    "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART";
  snapshot.runtime.processPendingRestartEnvironmentNames = [
    ...managedEnvironmentNames,
  ];
  assert.equal(
    evaluateProductionReadiness(snapshot).status,
    "PREPARED_FOR_STAGE",
  );
  snapshot.runtime.processPendingRestartEnvironmentNames.pop();
  assert.ok(
    evaluateProductionReadiness(snapshot).failedGateIds.includes(
      "active_runtime_anchor",
    ),
  );
  snapshot.runtime.processPendingRestartEnvironmentNames = [
    ...managedEnvironmentNames,
  ];
  snapshot.runtime.processConfiguredEnvironmentMismatchNames = [
    flags[0],
  ];
  assert.ok(
    evaluateProductionReadiness(snapshot).failedGateIds.includes(
      "active_runtime_anchor",
    ),
  );
  const enginePending = readySnapshot();
  enginePending.runtime.processConfiguredEnvironmentMatched = false;
  enginePending.runtime.processConfiguredEnvironmentLoadState =
    "ENGINE_CREDENTIAL_PENDING_RESTART";
  enginePending.runtime.processPendingRestartEnvironmentNames = [
    "FBSIR_ENGINE_TOKEN",
  ];
  assert.equal(
    evaluateProductionReadiness(enginePending).status,
    "PREPARED_FOR_STAGE",
  );
});

test("requires application rollback and forward-only database safety after deployment", () => {
  const snapshot = setExactRetained043(readySnapshot());
  snapshot.deploymentChannel = {
    state: "DEPLOYED_DEFAULT_OFF",
    receiptValidated: true,
    receiptAnchorMatched: true,
    releasePlanTargetBindingVerified: true,
    sourceCommit: commit,
    strictHeadBuildUploadSwitchReceiptScriptPresent: true,
    applicationRollbackAssemblyVerified: true,
    applicationRollbackProven: true,
    databaseRollbackSafetyProven: false,
    stableDatabaseIdentityMatched: true,
    databaseDownClaimed: false,
    actualActiveArtifactsMatched: false,
    currentLinkResolved: "/opt/fbsir/admin/releases/w1a-release-test",
    u3wDirectCaptchaHealthy: true,
    mePortalApiHealthy: true,
    adminPortalApiHealthy: true,
    mePortalReleaseMarkerMatched: true,
    adminPortalReleaseMarkerMatched: true,
    defaultOffIngressProbeVerified: true,
  };
  const result = evaluateProductionReadiness(snapshot);
  assert.equal(result.status, "PREPARED_FOR_STAGE");
  assert.ok(
    result.postDeployFailedGateIds.includes(
      "deployed_default_off_receipt",
    ),
  );
  snapshot.deploymentChannel.databaseRollbackSafetyProven = true;
  const selfReportedOnly = evaluateProductionReadiness(snapshot);
  assert.equal(selfReportedOnly.status, "PREPARED_FOR_STAGE");
  snapshot.deploymentChannel.actualActiveArtifactsMatched = true;
  const deployed = evaluateProductionReadiness(snapshot);
  assert.equal(deployed.status, "DEPLOYED_DEFAULT_OFF");
  assert.equal(deployed.productionChanged, true);
  assert.deepEqual(deployed.postDeployFailedGateIds, []);
  snapshot.deploymentChannel.stableDatabaseIdentityMatched = false;
  assert.notEqual(
    evaluateProductionReadiness(snapshot).status,
    "DEPLOYED_DEFAULT_OFF",
  );
});

test("deployed state rejects a database down claim or wrong portal health", () => {
  const snapshot = setExactRetained043(readySnapshot());
  snapshot.deploymentChannel = {
    state: "DEPLOYED_DEFAULT_OFF",
    receiptValidated: true,
    receiptAnchorMatched: true,
    releasePlanTargetBindingVerified: true,
    sourceCommit: commit,
    strictHeadBuildUploadSwitchReceiptScriptPresent: true,
    applicationRollbackAssemblyVerified: true,
    applicationRollbackProven: true,
    databaseRollbackSafetyProven: true,
    stableDatabaseIdentityMatched: true,
    databaseDownClaimed: false,
    actualActiveArtifactsMatched: true,
    currentLinkResolved: "/opt/fbsir/admin/releases/w1a-release-test",
    u3wDirectCaptchaHealthy: true,
    mePortalApiHealthy: true,
    adminPortalApiHealthy: true,
    mePortalReleaseMarkerMatched: true,
    adminPortalReleaseMarkerMatched: true,
    defaultOffIngressProbeVerified: true,
  };
  assert.equal(
    evaluateProductionReadiness(snapshot).status,
    "DEPLOYED_DEFAULT_OFF",
  );
  snapshot.deploymentChannel.databaseDownClaimed = true;
  assert.notEqual(
    evaluateProductionReadiness(snapshot).status,
    "DEPLOYED_DEFAULT_OFF",
  );
  snapshot.deploymentChannel.databaseDownClaimed = false;
  snapshot.deploymentChannel.mePortalApiHealthy = false;
  assert.notEqual(
    evaluateProductionReadiness(snapshot).status,
    "DEPLOYED_DEFAULT_OFF",
  );
  snapshot.deploymentChannel.mePortalApiHealthy = true;
  snapshot.deploymentChannel.defaultOffIngressProbeVerified = false;
  assert.notEqual(
    evaluateProductionReadiness(snapshot).status,
    "DEPLOYED_DEFAULT_OFF",
  );
});

test("reports an anchored application rollback without claiming database down", () => {
  const snapshot = setExactRetained043(readySnapshot());
  snapshot.local.releasePlanTargetMatchedLive = false;
  snapshot.deploymentChannel = {
    state: "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT",
    receiptValidated: true,
    receiptAnchorMatched: true,
    sourceCommit: commit,
    strictHeadBuildUploadSwitchReceiptScriptPresent: true,
    applicationRollbackAssemblyVerified: true,
    applicationRollbackProven: true,
    databaseRollbackSafetyProven: true,
    stableDatabaseIdentityMatched: true,
    rollbackLiveStateMatched: true,
    databaseDownClaimed: false,
    actualActiveArtifactsMatched: false,
    currentLinkResolved: null,
    u3wDirectCaptchaHealthy: true,
    mePortalApiHealthy: false,
    adminPortalApiHealthy: false,
    mePortalReleaseMarkerMatched: false,
    adminPortalReleaseMarkerMatched: false,
  };
  const result = evaluateProductionReadiness(snapshot);
  assert.equal(
    result.status,
    "ROLLED_BACK_APPLICATION_DB_043_RETAINED",
  );
  assert.equal(result.productionChanged, true);
  assert.match(result.nextAction, /do not claim database rollback/);
  snapshot.deploymentChannel.databaseDownClaimed = true;
  assert.notEqual(
    evaluateProductionReadiness(snapshot).status,
    "ROLLED_BACK_APPLICATION_DB_043_RETAINED",
  );
  snapshot.deploymentChannel.databaseDownClaimed = false;
  snapshot.deploymentChannel.rollbackLiveStateMatched = false;
  assert.notEqual(
    evaluateProductionReadiness(snapshot).status,
    "ROLLED_BACK_APPLICATION_DB_043_RETAINED",
  );
});

test("rejects a rolled-back state when retained 043 current-read drifts", () => {
  const snapshot = readySnapshot();
  snapshot.local.releasePlanTargetMatchedLive = false;
  snapshot.deploymentChannel = {
    state: "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT",
    receiptValidated: true,
    receiptAnchorMatched: true,
    sourceCommit: commit,
    strictHeadBuildUploadSwitchReceiptScriptPresent: true,
    applicationRollbackAssemblyVerified: true,
    applicationRollbackProven: true,
    databaseRollbackSafetyProven: false,
    databaseDownClaimed: false,
    actualActiveArtifactsMatched: false,
    currentLinkResolved: null,
  };
  const result = evaluateProductionReadiness(snapshot);
  assert.notEqual(
    result.status,
    "ROLLED_BACK_APPLICATION_DB_043_RETAINED",
  );
  assert.equal(result.productionChanged, false);
});

test("rejects a staged receipt from a different source commit", () => {
  const snapshot = readySnapshot();
  snapshot.deploymentChannel = {
    state: "STAGED_FOR_SWITCH",
    receiptValidated: true,
    receiptAnchorMatched: true,
    releasePlanTargetBindingVerified: true,
    sourceCommit: "d".repeat(40),
    strictHeadBuildUploadSwitchReceiptScriptPresent: true,
    applicationRollbackAssemblyVerified: true,
    applicationRollbackProven: false,
    databaseRollbackSafetyProven: true,
    stableDatabaseIdentityMatched: true,
    stagedLiveStateMatched: true,
    databaseDownClaimed: false,
    actualActiveArtifactsMatched: false,
  };
  const result = evaluateProductionReadiness(snapshot);
  assert.equal(result.status, "PREPARED_FOR_STAGE");
  assert.ok(
    result.postDeployFailedGateIds.includes("staged_release_receipt"),
  );
});

test("requires a locally verified release plan bound to strict HEAD", () => {
  const snapshot = readySnapshot();
  snapshot.local.releasePlanSourceCommit = "d".repeat(40);
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(
    result.failedGateIds.includes("release_plan_and_rollback_contract"),
  );
});

test("rejects a release plan after the live target drifts", () => {
  const snapshot = readySnapshot();
  snapshot.local.releasePlanTargetMatchedLive = false;
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(
    result.failedGateIds.includes("release_plan_and_rollback_contract"),
  );
});

test("rejects invalid snapshots", () => {
  assert.throws(() => evaluateProductionReadiness(null), /snapshot/);
  assert.throws(() => evaluateProductionReadiness([]), /snapshot/);
});

test("live collector is pinned, online-only, and verifies actual artifacts", () => {
  const collector = fs.readFileSync(
    new URL("./verify-independent-board-production-readiness.ps1", import.meta.url),
    "utf8",
  );
  const application = fs.readFileSync(
    new URL(
      "../FBSir-admin/src/main/resources/application.yml",
      import.meta.url,
    ),
    "utf8",
  );
  assert.equal(collector.includes("[string]$SnapshotPath"), false);
  assert.equal(collector.includes("[string]$SshTarget"), false);
  assert.equal(
    collector.includes("[string]$ExpectedRemoteHostKeyFingerprint"),
    false,
  );
  for (const name of flags.slice(0, 4)) {
    assert.ok(collector.includes(`"${name}"`));
    assert.ok(application.includes(`\${${name}:false}`));
  }
  for (const name of flags.slice(4, 8)) {
    assert.ok(collector.includes(`"${name}"`));
  }
  for (const name of flags.slice(8)) {
    assert.ok(collector.includes(`"${name}"`));
    assert.ok(application.includes(`\${${name}:false}`));
  }
  assert.ok(
    application.includes(
      "${FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID:}",
    ),
  );
  assert.ok(
    application.includes(
      "${FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY:}",
    ),
  );
  assert.ok(
    application.includes(
      "${FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET:}",
    ),
  );
  assert.ok(collector.includes("information_schema.check_constraints"));
  assert.ok(
    collector.includes(
      "HEX(REGEXP_REPLACE(TRIM(action_statement),'[[:space:]]+',' '))",
    ),
  );
  assert.ok(collector.includes("class RejectRedirect"));
  assert.ok(collector.includes("NO_REDIRECT_OPENER.open"));
  assert.equal(collector.includes("urllib.request.urlopen(request"), false);
  assert.ok(collector.includes("normalized_environment_file("));
  assert.ok(
    collector.includes(
      "fbsir-admin requires one mandatory fixed EnvironmentFile",
    ),
  );
  assert.ok(collector.includes('"environmentFilePaths"'));
  assert.ok(collector.includes('"environmentFileManifest"'));
  assert.ok(collector.includes('"api2EventKeyManifest"'));
  assert.ok(collector.includes('"processFlagValues"'));
  assert.ok(collector.includes('"processForbiddenOverrideNames"'));
  assert.ok(collector.includes('"activeNginxManifest"'));
  assert.ok(collector.includes('"nginxDumpSha256"'));
  assert.ok(collector.includes('"stageEntryTopology"'));
  assert.ok(collector.includes('"productionChangedByPlan": False'));
  assert.ok(collector.includes('"releasePlanTargetSha256"'));
  assert.ok(
    collector.includes('"releasePlanTargetComparableSha256"'),
  );
  assert.ok(
    collector.includes('"finalizeStageLiveTargetComparableSha256"'),
  );
  assert.ok(
    collector.includes('"stageOwnedReleaseRootDeltaVerified"'),
  );
  assert.ok(collector.includes("def rollback_source_allowed("));
  assert.ok(collector.includes("not EXPECTED_DEPLOYMENT_RECEIPT_SHA256"));
  assert.ok(
    collector.includes(
      '"state": "EXACT_PRIOR_ROLLBACK_PREDECESSOR"',
    ),
  );
  assert.ok(
    collector.includes(
      "$plannedTarget.stageEntryTopology.priorRollbackAnchor",
    ),
  );
  assert.ok(
    collector.includes(
      "$snapshot.deploymentChannel.rollbackLiveStateMatched",
    ),
  );
  assert.ok(collector.includes("fcntl.LOCK_SH"));
  assert.ok(collector.includes("nginx_dump_bytes.decode"));
  assert.equal(collector.includes("st_size >= 1024 * 1024"), false);
  assert.ok(
    collector.includes("active Nginx configuration manifest is empty"),
  );
  assert.equal(
    collector.includes('if "fbsir" in content.lower()'),
    false,
  );
  assert.ok(collector.includes("Test-WorkerPyMySqlOrchestrationProof"));
  assert.ok(collector.includes("pymysqlDistributionVersion"));
  assert.ok(collector.includes("'1.1.2'"));
  assert.ok(collector.includes("verified_release_file("));
  assert.ok(collector.includes("sha256_file(candidate) == expected_digest"));
  assert.ok(collector.includes('pathlib.Path("/proc")'));
  assert.ok(collector.includes('"processJarSha256"'));
  assert.ok(collector.includes('"configuredJarSha256"'));
  assert.ok(
    collector.includes(
      'padded_base64 = raw_base64 + ("=" * (-len(raw_base64) % 4))',
    ),
  );
  assert.ok(
    collector.includes(
      're.fullmatch(r"[0-9a-fA-F]+", raw_hex) is None',
    ),
  );
  assert.ok(collector.includes("return bytes.fromhex(raw_hex)"));
  assert.ok(
    collector.includes(
      "not hmac.compare_digest(material, same_binding_material)",
    ),
  );
  assert.ok(
    collector.includes(
      "w1a_043_legacy_admin_root_dependency_20260724_001",
    ),
  );
  assert.ok(
    collector.includes(
      "APPLIED:W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY_V1",
    ),
  );
  assert.ok(collector.includes("for index in range(1, 43)"));
  assert.ok(
    collector.includes("E78BACE891A3E4BC9AE7AEA1E79086"),
  );
  assert.ok(collector.includes("root_role_binding_count == 0"));
  assert.ok(collector.includes("root_page_child_count == 0"));
  assert.ok(
    collector.includes(
      "[string]$ExpectedAdminRootDependencyAdoptionReceiptSha256",
    ),
  );
  assert.ok(
    collector.includes(
      "/opt/fbsir/admin/dependencies/latest/adoption-receipt.json",
    ),
  );
  for (const schema of [
    "fbsir.u3wLegacyAdminRootDependencyAdoptionReceipt.v3",
    "fbsir.u3wDatabaseBackupReceipt.v4",
    "fbsir.u3wDatabaseRestoreRehearsalReceipt.v4",
    "fbsir.u3wDatabaseBackupRestoreBundleReceipt.v3",
    "fbsir.u3wDatabaseBackupRestoreExternalAnchor.v3",
    "fbsir.u3wW1aDeploymentReadinessReceipt.v2",
    "fbsir.u3wW1aDeploymentReadinessReceipt.v3",
    "fbsir.u3wDefaultOffReleaseRollbackReceipt.v1",
    "fbsir.u3wDefaultOffReleaseRollbackReceipt.v2",
    "fbsir.u3wDefaultOffRollbackVerificationReceipt.v1",
    "fbsir.u3wDefaultOffRollbackVerificationReceipt.v2",
  ]) {
    assert.ok(collector.includes(schema));
  }
  assert.ok(
    collector.includes(
      "[string]$ExpectedBackupPlanReceiptSha256",
    ),
  );
  assert.ok(collector.includes("bundle_receipt_fields = {"));
  assert.ok(collector.includes("backup_receipt_fields = {"));
  assert.ok(collector.includes("restore_receipt_fields = {"));
  assert.ok(collector.includes("isolation_evidence_fields = {"));
  assert.ok(
    collector.includes("def backup_static_control_facts(facts):"),
  );
  assert.ok(
    collector.includes(
      "def backup_attribution_observations_monotonic(",
    ),
  );
  assert.ok(
    collector.includes(
      "def adoption_attribution_observations_monotonic(",
    ),
  );
  assert.ok(
    collector.includes(
      "current_adoption_attribution_observations",
    ),
  );
  assert.ok(
    !collector.includes(
      'adoption_live_facts.get("attributionEventCount")\n'
        + '            == database.get("boardAttributionEventCount")',
    ),
  );
  assert.ok(
    collector.includes(
      "backup_static_control_facts(live_backup_facts)",
    ),
  );
  assert.ok(
    collector.includes(
      'source_receipt["sourceFacts"],\n'
        + '                restore_receipt["restoredFacts"],',
    ),
  );
  assert.ok(
    !collector.includes(
      'restore_receipt.get("restoredFacts")\n'
        + '                == source_receipt.get("sourceFacts")',
    ),
  );
  for (const field of [
    "businessDatabaseChanged",
    "serviceChanged",
    "officialExpertsPackageChanged",
  ]) {
    assert.ok(collector.includes(`source_receipt.get("${field}") is False`));
    assert.ok(collector.includes(`restore_receipt.get("${field}") is False`));
  }
  assert.ok(
    collector.includes(
      "source_receipt.get(\"sourceSnapshotExactlyMatched\") is False",
    ),
  );
  assert.ok(collector.includes("def legacy_w1a_migration_facts("));
  assert.ok(
    collector.includes("def recorded_w1a_migration_matches_live("),
  );
  for (const field of [
    "publicInit043ReceiptCount",
    "attributionInternalReceiptCount",
    "attributionTableCount",
    "attributionTriggerCount",
    "attributionPermissionCount",
  ]) {
    assert.ok(collector.includes(field));
  }
  assert.match(
    collector,
    /adoption_live_fact_fields = \{[\s\S]*?"w1aSchemaFingerprintSha256",[\s\S]*?"w1a043State",\n    \}/,
  );
  assert.ok(
    collector.includes(
      '"w1aSchemaFingerprintSha256":\n'
        + '            database.get("w1aSchemaFingerprintSha256"),\n'
        + '        "w1a043State":',
    ),
  );
  assert.ok(
    collector.includes(
      '"legacyAdminRootDependencyStateVerified":\n'
        + "                dependency_state_verified",
    ),
  );
});

test("live collector independently rehashes the fixed staged release Plan", () => {
  const collector = fs.readFileSync(
    new URL(
      "./verify-independent-board-production-readiness.ps1",
      import.meta.url,
    ),
    "utf8",
  );
  const requiredNeedles = [
    "PLAN_TARGET_FIELDS = frozenset(",
    "def validate_release_plan_target_binding(",
    'plan_path = release_directory / "evidence/release-plan.json"',
    "set(target) != PLAN_TARGET_FIELDS",
    'comparable.pop("releaseRootExists")',
    '"releasePlanTargetSha256": target_sha256',
    '"releasePlanTargetComparableSha256": comparable_sha256',
    "release_plan_target_binding_verified",
  ];
  for (const needle of requiredNeedles) {
    assert.ok(
      collector.includes(needle),
      `readiness collector must include ${needle}`,
    );
  }
  assert.ok(
    collector.includes(
      "sha256_file(plan_path) != "
        + "EXPECTED_RELEASE_PLAN_RECEIPT_SHA256",
    ),
  );
  assert.ok(
    collector.includes(
      "and release_plan_target_binding_verified",
    ),
    "receipt_validated must fail closed without independent Plan binding",
  );
});

test("readiness independently accepts only an exact interrupted Apply recovery predecessor", () => {
  const collector = embeddedCollectorSource();
  for (const value of [
    "fbsir.u3wDefaultOffInterruptedApplyRecoveryReceipt.v1",
    "INTERRUPTED_APPLY_RECOVERED_APPLICATION_DATABASE_043_RETAINED_DORMANT",
    "EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR",
    "fbsir.u3wPriorInterruptedApplyRecoveryStageAnchor.v1",
    "interrupted-apply-recovery-receipt.json",
    "priorRecoveryAnchor",
    "INTERRUPTED_APPLY_RECOVERY_RECEIPT_FIELDS",
    "INTERRUPTED_APPLY_RECOVERY_APPROVAL_FIELDS",
    "INTERRUPTED_APPLY_RECOVERY_PLAN_FIELDS",
    "recovery_latest",
    "recovery_valid",
    "recovery_live_state_matched",
    "recorded_recovery_migration_matches_live",
    "interrupted_recovery_historic_anchors_valid",
    "fbsir.u3wProductionChangeApprovalReceipt.v2",
    "fbsir.u3wInterruptedApplyRecoveryPlan.v1",
    "INTERRUPTED_APPLY_RECOVERY_CANONICALIZATION_PLANNED",
    "interrupted-apply-recovery-approval.json",
    "interrupted-apply-recovery-plan.json",
  ]) {
    assert.ok(
      readinessRunner.includes(value),
      `readiness must include recovery contract ${value}`,
    );
  }
  for (const field of [
    "applicationRestored",
    "topologyRestored",
    "deploymentReceiptAbsent",
    "rollbackReceiptAbsent",
    "currentLinkAbsent",
    "releaseDropInMatched",
    "allW1aFlagsExplicitFalse",
    "databaseDownClaimed",
    "productionDatabaseChangedThisRecoveryRun",
    "productionServiceChangedThisRecoveryRun",
    "officialExpertsPackageChanged",
    "retainedMigrationFacts",
  ]) {
    assert.ok(
      new RegExp(
        `recovery\\.get\\(\\s*"${field}"\\s*\\)`,
      ).test(collector),
      `recovery validation must independently check ${field}`,
    );
  }
  assert.ok(
    collector.includes(
      'live.get("boardAttributionEventCount")'
        + ' >= recorded.get("eventCount")',
    ),
  );
  assert.ok(
    collector.includes(
      'live.get("boardAttributionJourneyCount")'
        + ' >= recorded.get("journeyCount")',
    ),
  );
  for (const field of [
    "boardAttributionNaturalEventCount",
    "boardAttributionNonProbeEventCount",
    "boardAttributionAuthoritativeProductCreditCount",
    "boardAttributionNaturalJourneyCount",
    "boardAttributionNonProbeJourneyCount",
  ]) {
    assert.ok(
      new RegExp(
        `live\\.get\\(\\s*"${field}"\\s*\\)\\s*==\\s*0`,
      ).test(collector),
    );
  }
  for (const value of [
    "anchored_actual_failure_manifest = []",
    "anchored_failure_manifest_current = bool(",
    'anchored_recovery_release.glob(\n'
      + '                        "apply-failure-*.json"',
    "anchored_actual_failure_manifest\n"
      + "                        == anchored_failure_receipt_manifest",
    "and anchored_failure_manifest_current",
  ]) {
    assert.ok(
      collector.includes(value),
      `historical recovery must re-enumerate ${value}`,
    );
  }
  assert.ok(
    /"state"\s*:\s*"EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR"/
      .test(collector),
  );
  assert.ok(
    collector.includes('"priorRollbackAnchor": None'),
  );
  assert.ok(
    collector.includes('"priorRecoveryAnchor": prior_recovery_anchor'),
  );
  assert.ok(
    readinessRunner.includes(
      "$plannedTarget.stageEntryTopology.priorRecoveryAnchor",
    ),
  );
  assert.ok(
    readinessRunner.includes(
      "$snapshot.deploymentChannel.recoveryLiveStateMatched",
    ),
  );
  assert.ok(
    /application_rollback_assembly\.get\(\s*"priorRecoveryAnchor"\s*\)/
      .test(collector),
  );
  assert.ok(
    /pre_application_state\s*==\s*"EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR"/
      .test(collector),
  );
  assert.ok(
    /prior_recovery_anchor\.get\("schema"\)\s*==\s*"fbsir\.u3wPriorInterruptedApplyRecoveryStageAnchor\.v1"/
      .test(collector),
  );
  assert.ok(
    collector.includes(
      "and not release_status.st_mode & 0o022",
    ),
    "recovery readiness must accept Stage-owned 0755 directories",
  );
  assert.ok(
    collector.includes(
      "and not anchored_recovery_release_status.st_mode\n"
        + "                        & 0o022",
    ),
    "anchored recovery readiness must use the worker custody rule",
  );
  assert.equal(
    collector.includes(
      "release_status.st_mode & 0o777 == 0o700",
    ),
    false,
    "recovery readiness must not contradict make_frontend_public",
  );
  assert.equal(
    collector.includes(
      "anchored_recovery_release_status.st_mode\n"
        + "                        & 0o777 == 0o700",
    ),
    false,
  );
  for (const binding of [
    'approval_manifest["sha256"]\n'
      + '            == recovery.get("approvalReceiptSha256")',
    'plan_manifest["sha256"]\n'
      + '            == recovery.get("recoveryPlanReceiptSha256")',
    'approval.get("requestDigest")\n'
      + '            == recovery.get("recoveryPlanReceiptSha256")',
    'approval.get("approvalNonce")\n'
      + '            == recovery.get("approvalNonce")',
    'plan.get("stageReceiptSha256")\n'
      + '            == recovery.get("stageReceiptSha256")',
    'plan.get("applyFailureReceiptSha256")\n'
      + '            == recovery.get("applyFailureReceiptSha256")',
    'plan.get("applyFailureManifestSha256")\n'
      + '            == recovery.get("applyFailureReceiptManifestSha256")',
  ]) {
    assert.ok(
      collector.includes(binding),
      `historic recovery evidence must bind ${binding}`,
    );
  }
  for (const timeRule of [
    "approval_expires > approved",
    "approval_expires - approved <= timedelta(hours=24)",
    "plan_expires > generated",
    "plan_expires - generated <= timedelta(hours=24)",
    "observed >= approved",
    "observed >= generated",
    "observed < approval_expires",
    "observed < plan_expires",
  ]) {
    assert.ok(collector.includes(timeRule));
  }
  for (const compatible of [
    "UNTOUCHED_LEGACY",
    "EXACT_PRIOR_ROLLBACK_PREDECESSOR",
  ]) {
    assert.ok(collector.includes(compatible));
  }
});

test("live signed probe shares the exact 39-field Java verifier contract", () => {
  const collector = embeddedCollectorSource();
  const readinessFields = pythonStringTuple(
    collector,
    "ATTRIBUTION_EVENT_SIGNED_STRING_FIELDS",
  );
  const releaseFields = pythonStringTuple(
    releaseWorker,
    "ATTRIBUTION_EVENT_SIGNED_STRING_FIELDS",
  );
  assert.deepEqual(readinessFields, releaseFields);

  const signedFieldsMethod = javaEventVerifier.match(
    /private Map<String, String> signedFields\([\s\S]*?\{([\s\S]*?)\n    \}/,
  );
  assert.ok(signedFieldsMethod, "Java signedFields method must be extractable");
  const javaFields = [
    ...signedFieldsMethod[1].matchAll(/values\.put\("([^"]+)"/g),
  ].map((item) => item[1]);
  const readinessCanonicalFields = [
    ...readinessFields,
    "rawContentStored",
    "sequenceNo",
  ];
  assert.equal(readinessCanonicalFields.length, 39);
  assert.deepEqual(
    [...readinessCanonicalFields].sort(),
    [...javaFields].sort(),
  );
  assert.match(collector, /DISABLED_INGRESS_HTTP_STATUS = 404/);
  assert.equal(
    /DISABLED_INGRESS_HTTP_STATUSES\s*=/.test(collector),
    false,
  );
  assert.ok(
    collector.includes(
      '"acceptedDisabledHttpStatuses":\n'
        + "            [DISABLED_INGRESS_HTTP_STATUS]",
    ),
  );
  const probeFunction = collector.match(
    /def disabled_attribution_ingress_probe\([\s\S]*?\n\n(?=def )/,
  );
  assert.ok(probeFunction, "signed disabled probe must be extractable");
  const probeSource = probeFunction[0];
  const identityBefore = probeSource.indexOf("identity_before =");
  const globalBefore = probeSource.indexOf("global_before =");
  const post = probeSource.indexOf(
    "http_status = post_signed_attribution_probe(event)",
  );
  const identityAfter = probeSource.indexOf("identity_after =");
  const globalAfter = probeSource.indexOf("global_after =");
  assert.ok(identityBefore >= 0);
  assert.ok(identityBefore < globalBefore);
  assert.ok(globalBefore < post);
  assert.ok(post < identityAfter);
  assert.ok(identityAfter < globalAfter);
});

test("live signed probe accepts only 404 with identity and global zero-write", () => {
  const pythonSource = embeddedCollectorSource();
  const harness = String.raw`
import ast
import json
import sys

source = sys.stdin.read()
tree = ast.parse(source, filename="<u3w-readiness-collector>")
wanted_assignments = {
    "EVENT_KEY_ID_NAME",
    "EVENT_KEY_NAME",
    "ATTRIBUTION_INGRESS_PATH",
    "DISABLED_INGRESS_HTTP_STATUS",
    "ATTRIBUTION_EVENT_SIGNED_STRING_FIELDS",
}
wanted_functions = {
    "canonical_json",
    "decode_secret_material",
    "build_signed_attribution_probe_event",
    "attribution_probe_identity_counts",
    "attribution_global_counts",
    "disabled_attribution_ingress_probe",
}
selected = []
for node in tree.body:
    if isinstance(node, ast.Assign) and any(
        isinstance(target, ast.Name)
        and target.id in wanted_assignments
        for target in node.targets
    ):
        selected.append(node)
    elif isinstance(node, ast.FunctionDef) and node.name in wanted_functions:
        selected.append(node)

namespace = {}
prefix = (
    "import base64\n"
    "import hashlib\n"
    "import hmac\n"
    "import json\n"
    "import os\n"
    "import re\n"
    "from datetime import datetime, timedelta, timezone\n"
)
exec(prefix, namespace)
exec(
    compile(
        ast.Module(body=selected, type_ignores=[]),
        "<u3w-readiness-signed-probe>",
        "exec",
    ),
    namespace,
)

key_material = b"K" * 32
environment = {
    namespace["EVENT_KEY_ID_NAME"]: "w1a-active-key",
    namespace["EVENT_KEY_NAME"]:
        "base64:" + namespace["base64"].b64encode(key_material).decode("ascii"),
}
namespace["os"].urandom = lambda size: b"P" * size
observed = namespace["datetime"](
    2026,
    7,
    24,
    18,
    0,
    30,
    tzinfo=namespace["timezone"].utc,
)
event, identity = namespace["build_signed_attribution_probe_event"](
    environment,
    observed=observed,
)
signed_fields = {
    name: str(event[name]).strip()
    for name in namespace["ATTRIBUTION_EVENT_SIGNED_STRING_FIELDS"]
}
signed_fields["rawContentStored"] = "false"
signed_fields["sequenceNo"] = "1"
assert len(signed_fields) == 39
expected_signature = "v1=" + namespace["hmac"].new(
    key_material,
    namespace["canonical_json"](signed_fields).encode("utf-8"),
    namespace["hashlib"].sha256,
).hexdigest()
assert event["signature"] == expected_signature
assert event["trafficClass"] == "PROBE"

global_before = {"eventCount": 7, "journeyCount": 3}
zero_counts = {name: 0 for name in identity}
def zero_write_query(statement):
    if " WHERE " in statement:
        return 0
    if "fbs_board_attr_event_v1" in statement:
        return global_before["eventCount"]
    if "fbs_board_attr_journey_v1" in statement:
        return global_before["journeyCount"]
    raise AssertionError("unexpected probe query")

namespace["post_signed_attribution_probe"] = lambda _: 404
evidence = namespace["disabled_attribution_ingress_probe"](
    zero_write_query,
    environment,
)
assert evidence["verifiedDisabled"] is True
assert evidence["acceptedDisabledHttpStatuses"] == [404]
assert evidence["identityCountsBefore"] == zero_counts
assert evidence["identityCountsAfter"] == zero_counts
assert evidence["globalCountsBefore"] == global_before
assert evidence["globalCountsAfter"] == global_before
serialized = namespace["canonical_json"](evidence)
assert event["nonce"] not in serialized
assert event["signature"] not in serialized
assert key_material.decode("ascii") not in serialized
assert evidence["secretsDisclosed"] is False

for status in (200, 202, 400, 401, 403, 405):
    namespace["post_signed_attribution_probe"] = (
        lambda _, current=status: current
    )
    rejected = namespace["disabled_attribution_ingress_probe"](
        zero_write_query,
        environment,
    )
    assert rejected["verifiedDisabled"] is False

identity_responses = iter([
    dict(zero_counts),
    {**zero_counts, "nonceHash": 1},
])
namespace["attribution_probe_identity_counts"] = (
    lambda *_: next(identity_responses)
)
namespace["attribution_global_counts"] = lambda *_: dict(global_before)
namespace["post_signed_attribution_probe"] = lambda _: 404
identity_write = namespace["disabled_attribution_ingress_probe"](
    zero_write_query,
    environment,
)
assert identity_write["verifiedDisabled"] is False

namespace["attribution_probe_identity_counts"] = lambda *_: dict(zero_counts)
global_responses = iter([
    dict(global_before),
    {"eventCount": 8, "journeyCount": 3},
])
namespace["attribution_global_counts"] = (
    lambda *_: next(global_responses)
)
global_write = namespace["disabled_attribution_ingress_probe"](
    zero_write_query,
    environment,
)
assert global_write["verifiedDisabled"] is False

posted = {"called": False}
namespace["attribution_probe_identity_counts"] = lambda *_: {
    **zero_counts,
    "eventId": 1,
}
namespace["post_signed_attribution_probe"] = (
    lambda _: posted.update(called=True) or 404
)
try:
    namespace["disabled_attribution_ingress_probe"](
        zero_write_query,
        environment,
    )
    raise AssertionError("pre-existing probe identity must fail closed")
except RuntimeError as error:
    assert "identity is not unique" in str(error)
assert posted["called"] is False

print(json.dumps({
    "status": "PASS",
    "signedFieldCount": len(signed_fields),
    "acceptedDisabledHttpStatuses":
        evidence["acceptedDisabledHttpStatuses"],
}))
`;
  const result = spawnSync(pythonExecutable(), ["-c", harness], {
    input: pythonSource,
    encoding: "utf8",
    timeout: 15_000,
  });
  assert.equal(
    result.status,
    0,
    `signed probe harness failed: ${result.stderr || result.stdout}`,
  );
  assert.deepEqual(JSON.parse(result.stdout.trim()), {
    status: "PASS",
    signedFieldCount: 39,
    acceptedDisabledHttpStatuses: [404],
  });
});

test("live collector observes 302 responses without following redirects", () => {
  const pythonSource = embeddedCollectorSource();
  const harness = String.raw`
import ast
import json
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

source = sys.stdin.read()
tree = ast.parse(source, filename="<u3w-readiness-collector>")
selected = []
wanted_functions = {"http_status", "http_json_status", "http_json_document"}
for node in tree.body:
    if isinstance(node, ast.ClassDef) and node.name == "RejectRedirect":
        selected.append(node)
    elif isinstance(node, ast.FunctionDef) and node.name in wanted_functions:
        selected.append(node)
    elif isinstance(node, ast.Assign) and any(
        isinstance(target, ast.Name) and target.id == "NO_REDIRECT_OPENER"
        for target in node.targets
    ):
        selected.append(node)
namespace = {}
prefix = (
    "import json\n"
    "import urllib.error\n"
    "import urllib.request\n"
)
exec(prefix, namespace)
exec(compile(ast.Module(body=selected, type_ignores=[]),
             "<u3w-readiness-http>", "exec"), namespace)

hits = {"ok": 0}
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/redirect":
            self.send_response(302)
            self.send_header("Location", "/ok")
            self.end_headers()
            return
        if self.path == "/ok":
            hits["ok"] += 1
            payload = b'{"code":200}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        self.send_response(404)
        self.end_headers()
    def log_message(self, *_):
        return

server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
url = "http://127.0.0.1:{}/redirect".format(server.server_port)
try:
    assert namespace["http_status"](url) == 302
    assert namespace["http_json_status"](url) == {
        "httpStatus": 302,
        "businessCode": None,
    }
    assert namespace["http_json_document"](url) == {
        "httpStatus": 302,
        "document": None,
    }
    assert hits["ok"] == 0
finally:
    server.shutdown()
    server.server_close()
print(json.dumps({"status": "PASS", "followedRedirects": hits["ok"]}))
`;
  const result = spawnSync(pythonExecutable(), ["-c", harness], {
    input: pythonSource,
    encoding: "utf8",
    timeout: 15_000,
  });
  assert.equal(
    result.status,
    0,
    `redirect harness failed: ${result.stderr || result.stdout}`,
  );
  assert.deepEqual(JSON.parse(result.stdout.trim()), {
    status: "PASS",
    followedRedirects: 0,
  });
});
