import assert from "node:assert/strict";
import test from "node:test";
import { evaluateProductionReadiness } from "./independent-board-production-readiness.mjs";

const commit = "a".repeat(40);
const digest = "b".repeat(64);
const migrations = Array.from(
  { length: 8 },
  (_, index) => `public_init_0${35 + index}`,
);
const flags = [
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_CREDIT_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_WRITER_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_INTENT_CLASSIFIER_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_ADMIN_READ_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED",
];
const migrationDescriptions = {
  public_init_035: "Independent Board OAuth consent-intent lineage",
  public_init_036: "Independent Board OAuth refresh security receipt v2",
  public_init_037:
    "Independent Board exact product attribution evidence contract",
  public_init_038:
    "Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger",
  public_init_039:
    "Independent Board immutable plan policy revisions and operation lineage",
  public_init_040:
    "Independent Board plan policy database monotonic-chain guards",
  public_init_041:
    "Independent Board plan policy controlled procedure authority",
  public_init_042:
    "Independent Board default-off skill-consume v2 credit ledger",
};

function readySnapshot() {
  return {
    observedAt: "2026-07-23T14:00:00.000Z",
    local: {
      clean: true,
      sourceCommit: commit,
      expectedSourceCommit: commit,
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
      sameBindingSecretPresent: true,
    },
    backup: {
      receiptPath: "/opt/fbsir/admin/backups/latest/receipt.json",
      proven: true,
      receiptAnchorMatched: true,
      sha256: digest,
      sizeBytes: 10,
      restoreProcedureVerified: true,
    },
    deploymentChannel: {
      receiptValidated: true,
      receiptAnchorMatched: true,
      sourceCommit: commit,
      strictHeadBuildUploadSwitchReceiptScriptPresent: true,
      applicationRollbackProven: true,
      databaseRollbackProven: true,
    },
  };
}

test("passes only when every release proof is present", () => {
  const result = evaluateProductionReadiness(readySnapshot());
  assert.equal(result.status, "READY_FOR_DEFAULT_OFF_RELEASE");
  assert.equal(result.readyForDefaultOffRelease, true);
  assert.deepEqual(result.failedGateIds, []);
  assert.equal(result.productionChanged, false);
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

test("fails closed for an unverified MySQL patch version", () => {
  const snapshot = readySnapshot();
  snapshot.database.serverVersion = "8.0.45";
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("versioned_schema_baseline"));
  assert.match(
    result.gates.find((item) => item.id === "versioned_schema_baseline")
      .detail,
    /unverified MySQL version/,
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

test("fails closed when the database backup cannot be restored", () => {
  const snapshot = readySnapshot();
  snapshot.backup.restoreProcedureVerified = false;
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
      (name) => name !== "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
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

test("requires out-of-band anchors for backup and deployment receipts", () => {
  const snapshot = readySnapshot();
  snapshot.backup.receiptAnchorMatched = false;
  snapshot.deploymentChannel.receiptAnchorMatched = false;
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(result.failedGateIds.includes("backup_restore_anchor"));
  assert.ok(
    result.failedGateIds.includes("release_switch_and_rollback_channel"),
  );
});

test("requires both application and database rollback proofs", () => {
  const snapshot = readySnapshot();
  snapshot.deploymentChannel.databaseRollbackProven = false;
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(
    result.failedGateIds.includes("release_switch_and_rollback_channel"),
  );
});

test("rejects a release receipt from a different source commit", () => {
  const snapshot = readySnapshot();
  snapshot.deploymentChannel.sourceCommit = "d".repeat(40);
  const result = evaluateProductionReadiness(snapshot);
  assert.ok(
    result.failedGateIds.includes("release_switch_and_rollback_channel"),
  );
});

test("rejects invalid snapshots", () => {
  assert.throws(() => evaluateProductionReadiness(null), /snapshot/);
  assert.throws(() => evaluateProductionReadiness([]), /snapshot/);
});
