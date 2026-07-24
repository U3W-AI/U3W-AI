import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { evaluateProductionReadiness } from "./independent-board-production-readiness.mjs";

const commit = "a".repeat(40);
const digest = "b".repeat(64);
const readinessRunner = fs.readFileSync(
  new URL("./verify-independent-board-production-readiness.ps1", import.meta.url),
  "utf8",
);
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
        "fbsir.u3wDefaultOffReleaseRunner.v1",
      releasePlanReceiptPath:
        "reports/independent-board/w1a-default-off-release-plan-latest.json",
      releasePlanReceiptSha256: digest,
      preparationSourceCommit: commit,
      preparationCommitAncestorOfSourceCommit: true,
      preparationSourceCommitsConsistent: true,
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
      totalTableCount: 120,
      migrationTableCount: 1,
      migrationVersions: migrations,
      migrationDescriptions: { ...migrationDescriptions },
      publicInit043Applied: false,
      boardAttributionTableCount: 0,
      boardAttributionTriggerCount: 0,
      boardAttributionPermissionCount: 0,
      boardAttributionInternalReceiptCount: 0,
    },
    portals: {
      meHttpStatus: 404,
      adminHttpStatus: 404,
      u3wDirectCaptcha: { httpStatus: 200, businessCode: 200 },
      meApiCaptcha: { httpStatus: 404, businessCode: null },
      adminApiCaptcha: { httpStatus: 404, businessCode: null },
    },
    configuration: {
      environmentKeyNames: [...flags],
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
      configurationReceiptValid: true,
      configurationReceiptAnchorMatched: true,
      configurationReceiptSourceCommit: commit,
      configurationReceiptSha256: digest,
    },
    backup: {
      receiptPath: "/opt/fbsir/admin/backups/latest/receipt.json",
      sourceCommit: commit,
      proven: true,
      receiptAnchorMatched: true,
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

test("requires all production flags to be explicit and false", () => {
  const snapshot = readySnapshot();
  snapshot.configuration.environmentKeyNames.pop();
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

test("accepts only exact-loaded or untouched legacy pending-restart runtime configuration", () => {
  const snapshot = readySnapshot();
  snapshot.runtime.processConfiguredEnvironmentMatched = false;
  snapshot.runtime.processConfiguredEnvironmentLoadState =
    "LEGACY_W1A_PENDING_RESTART";
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
});

test("requires application rollback and forward-only database safety after deployment", () => {
  const snapshot = readySnapshot();
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
  const snapshot = readySnapshot();
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
});

test("reports an anchored application rollback without claiming database down", () => {
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

test("live collector observes 302 responses without following redirects", () => {
  const collector = fs.readFileSync(
    new URL(
      "./verify-independent-board-production-readiness.ps1",
      import.meta.url,
    ),
    "utf8",
  );
  const match = collector.match(
    /\$remotePython = @'\r?\n([\s\S]*?)\r?\n'@/,
  );
  assert.ok(match, "embedded readiness collector must be extractable");
  const pythonSource = match[1].replace(/__[A-Z0-9_]+__/g, '""');
  const bundledPython = path.join(
    os.homedir(),
    ".cache",
    "codex-runtimes",
    "codex-primary-runtime",
    "dependencies",
    "python",
    process.platform === "win32" ? "python.exe" : "bin/python",
  );
  const python = process.env.U3W_PYTHON_EXE
    || (fs.existsSync(bundledPython)
      ? bundledPython
      : process.platform === "win32"
        ? "python.exe"
        : "python3");
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
  const result = spawnSync(python, ["-c", harness], {
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
