import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { evaluateProductionReadiness } from "./independent-board-production-readiness.mjs";

const commit = "a".repeat(40);
const digest = "b".repeat(64);
const schemaFingerprint =
  "a0507f51960622d49b66c4d8b1b7382dc8bc16a904d577bac1ca942bb8748b28";
const migrations = Array.from(
  { length: 8 },
  (_, index) => `public_init_0${35 + index}`,
);
const flags = [
  "FBSIR_BOARD_ATTRIBUTION_ENABLED",
  "FBSIR_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
  "FBSIR_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
  "FBSIR_BOARD_ATTRIBUTION_CREDIT_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_WRITER_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_INTENT_CLASSIFIER_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_ADMIN_READ_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED",
];
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
      releasePlanSourceCommit: commit,
      releaseRunnerContractVersion:
        "fbsir.u3wDefaultOffReleaseRunner.v1",
      releasePlanReceiptPath:
        "reports/independent-board/w1a-default-off-release-plan-latest.json",
      releasePlanReceiptSha256: digest,
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
      api2FbssHealthHttpStatus: 200,
    },
    configuration: {
      environmentKeyNames: [...flags],
      explicitFalseKeyNames: [...flags],
      eventKeyEntryCount: 1,
      activeEventKeyPairPresent: true,
      activeEventKeyId: "wave1-k1",
      previousEventKeyPairComplete: true,
      sameBindingSecretPresent: true,
      environmentFileCustodySecure: true,
      cryptographicConfigurationShapeValid: true,
    },
    backup: {
      receiptPath: "/opt/fbsir/admin/backups/latest/receipt.json",
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
      applicationRollbackProven: false,
      databaseRollbackProven: false,
      actualActiveArtifactsMatched: false,
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
  snapshot.database.schemaBaselineMode = "LEGACY_ADOPTED_W1A_V1";
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
    sourceCommit: commit,
    strictHeadBuildUploadSwitchReceiptScriptPresent: true,
    applicationRollbackProven: false,
    databaseRollbackProven: false,
    actualActiveArtifactsMatched: false,
  };
  const result = evaluateProductionReadiness(snapshot);
  assert.equal(result.status, "STAGED_FOR_SWITCH");
  assert.equal(result.readyForDefaultOffRelease, true);
  assert.deepEqual(result.failedGateIds, []);
  assert.deepEqual(result.postDeployFailedGateIds, [
    "deployed_default_off_receipt",
  ]);
});

test("requires jointly proven rollback evidence after deployment", () => {
  const snapshot = readySnapshot();
  snapshot.deploymentChannel = {
    state: "DEPLOYED_DEFAULT_OFF",
    receiptValidated: true,
    receiptAnchorMatched: true,
    sourceCommit: commit,
    strictHeadBuildUploadSwitchReceiptScriptPresent: true,
    applicationRollbackProven: true,
    databaseRollbackProven: false,
    actualActiveArtifactsMatched: false,
  };
  const result = evaluateProductionReadiness(snapshot);
  assert.equal(result.status, "PREPARED_FOR_STAGE");
  assert.ok(
    result.postDeployFailedGateIds.includes(
      "deployed_default_off_receipt",
    ),
  );
  snapshot.deploymentChannel.databaseRollbackProven = true;
  const selfReportedOnly = evaluateProductionReadiness(snapshot);
  assert.equal(selfReportedOnly.status, "PREPARED_FOR_STAGE");
  snapshot.deploymentChannel.actualActiveArtifactsMatched = true;
  const deployed = evaluateProductionReadiness(snapshot);
  assert.equal(deployed.status, "DEPLOYED_DEFAULT_OFF");
  assert.equal(deployed.productionChanged, true);
  assert.deepEqual(deployed.postDeployFailedGateIds, []);
});

test("rejects a staged receipt from a different source commit", () => {
  const snapshot = readySnapshot();
  snapshot.deploymentChannel = {
    state: "STAGED_FOR_SWITCH",
    receiptValidated: true,
    receiptAnchorMatched: true,
    sourceCommit: "d".repeat(40),
    strictHeadBuildUploadSwitchReceiptScriptPresent: true,
    applicationRollbackProven: false,
    databaseRollbackProven: false,
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
  for (const name of flags.slice(4)) {
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
  assert.ok(collector.includes("HEX(action_statement)"));
  assert.ok(collector.includes("verified_release_file("));
  assert.ok(collector.includes("sha256_file(candidate) == expected_digest"));
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
