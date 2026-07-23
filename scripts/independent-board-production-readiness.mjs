import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const SCHEMA =
  "fbsir.independentBoardProductionReadiness.v1";

const REQUIRED_PREDECESSOR_MIGRATIONS = Object.freeze([
  "public_init_035",
  "public_init_036",
  "public_init_037",
  "public_init_038",
  "public_init_039",
  "public_init_040",
  "public_init_041",
  "public_init_042",
]);

const REQUIRED_MIGRATION_DESCRIPTIONS = Object.freeze({
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
});

const SUPPORTED_MYSQL_VERSIONS = Object.freeze([
  "8.0.30",
  "8.4.8",
]);

const REQUIRED_DEFAULT_OFF_FLAGS = Object.freeze([
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_CREDIT_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_WRITER_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_INTENT_CLASSIFIER_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_ADMIN_READ_ENABLED",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED",
]);

function hasExactString(value, pattern) {
  return typeof value === "string" && pattern.test(value);
}

function asSet(values) {
  return new Set(Array.isArray(values) ? values : []);
}

function gate(id, pass, detail) {
  return { id, pass: pass === true, detail };
}

export function evaluateProductionReadiness(snapshot) {
  if (!snapshot || typeof snapshot !== "object" || Array.isArray(snapshot)) {
    throw new TypeError("snapshot must be an object");
  }

  const local = snapshot.local ?? {};
  const target = snapshot.target ?? {};
  const runtime = snapshot.runtime ?? {};
  const database = snapshot.database ?? {};
  const configuration = snapshot.configuration ?? {};
  const backup = snapshot.backup ?? {};
  const migrationVersions = asSet(database.migrationVersions);
  const migrationDescriptions =
    database.migrationDescriptions &&
    typeof database.migrationDescriptions === "object" &&
    !Array.isArray(database.migrationDescriptions)
      ? database.migrationDescriptions
      : {};
  const configuredNames = asSet(configuration.environmentKeyNames);
  const explicitFalseNames = asSet(configuration.explicitFalseKeyNames);

  const missingPredecessors = REQUIRED_PREDECESSOR_MIGRATIONS.filter(
    (version) => !migrationVersions.has(version),
  );
  const driftedPredecessorDescriptions =
    REQUIRED_PREDECESSOR_MIGRATIONS.filter(
      (version) =>
        migrationDescriptions[version] !==
        REQUIRED_MIGRATION_DESCRIPTIONS[version],
    );
  const missingFlags = REQUIRED_DEFAULT_OFF_FLAGS.filter(
    (name) => !configuredNames.has(name),
  );
  const nonFalseFlags = REQUIRED_DEFAULT_OFF_FLAGS.filter(
    (name) => !explicitFalseNames.has(name),
  );

  const gates = [
    gate(
      "strict_head",
      local.clean === true &&
        hasExactString(local.sourceCommit, /^[0-9a-f]{40}$/) &&
        local.sourceCommit === local.expectedSourceCommit,
      "checked-out source must be the expected clean 40-hex commit",
    ),
    gate(
      "scoped_authority",
      target.host === "api2.u3w.com" &&
        target.authorityObserved === "root" &&
        target.serviceUnit === "fbsir-admin.service",
      "production authority must be root on api2.u3w.com and scoped to fbsir-admin.service",
    ),
    gate(
      "active_runtime_anchor",
      target.serviceState === "active" &&
        hasExactString(runtime.jarSha256, /^[0-9a-f]{64}$/) &&
        typeof runtime.jarPath === "string" &&
        runtime.jarPath.startsWith("/opt/fbsir/admin/"),
      "the active service and exact current JAR digest must be readable",
    ),
    gate(
      "versioned_schema_baseline",
      database.migrationTableCount === 1 &&
        SUPPORTED_MYSQL_VERSIONS.includes(database.serverVersion) &&
        missingPredecessors.length === 0 &&
        driftedPredecessorDescriptions.length === 0,
      !SUPPORTED_MYSQL_VERSIONS.includes(database.serverVersion)
        ? `unverified MySQL version: ${database.serverVersion ?? "missing"}`
        : missingPredecessors.length > 0
          ? `missing migration predecessors: ${missingPredecessors.join(",")}`
          : driftedPredecessorDescriptions.length > 0
            ? `migration description drift: ${driftedPredecessorDescriptions.join(",")}`
            : "exact public_init_035 through public_init_042 receipts are present on a verified MySQL version",
    ),
    gate(
      "w1a_schema_state",
      database.publicInit043Applied === false ||
        (database.publicInit043Applied === true &&
          database.w1aSchemaFingerprintVerified === true),
      "public_init_043 must be explicitly absent or backed by the exact W1A table/trigger fingerprint",
    ),
    gate(
      "backup_restore_anchor",
      backup.proven === true &&
        backup.receiptAnchorMatched === true &&
        hasExactString(backup.sha256, /^[0-9a-f]{64}$/) &&
        Number.isSafeInteger(backup.sizeBytes) &&
        backup.sizeBytes > 0 &&
        backup.restoreProcedureVerified === true,
      "a non-empty database backup digest and verified restore procedure receipt are required",
    ),
    gate(
      "default_off_configuration",
      missingFlags.length === 0 && nonFalseFlags.length === 0,
      missingFlags.length > 0
        ? `missing explicit flags: ${missingFlags.join(",")}`
        : nonFalseFlags.length > 0
          ? `flags not explicitly false: ${nonFalseFlags.join(",")}`
          : "all W1A production flags are explicitly false",
    ),
    gate(
      "cryptographic_material_custody",
      configuration.eventKeyEntryCount > 0 &&
        configuration.sameBindingSecretPresent === true,
      "event verification key material and same-binding secret must be present without disclosure",
    ),
    gate(
      "release_switch_and_rollback_channel",
      snapshot.deploymentChannel?.receiptValidated === true &&
        snapshot.deploymentChannel?.receiptAnchorMatched === true &&
        snapshot.deploymentChannel?.sourceCommit === local.sourceCommit &&
        snapshot.deploymentChannel?.strictHeadBuildUploadSwitchReceiptScriptPresent ===
          true &&
        snapshot.deploymentChannel?.applicationRollbackProven === true &&
        snapshot.deploymentChannel?.databaseRollbackProven === true,
      "strict-HEAD build/upload/switch plus application-and-database rollback receipts are required",
    ),
  ];

  const failedGateIds = gates
    .filter((item) => !item.pass)
    .map((item) => item.id);
  const ready = failedGateIds.length === 0;

  return {
    schema: SCHEMA,
    observedAt: snapshot.observedAt ?? null,
    status: ready
      ? "READY_FOR_DEFAULT_OFF_RELEASE"
      : "NOT_READY_FOR_PRODUCTION_RELEASE",
    productionChanged: false,
    readyForDefaultOffRelease: ready,
    gates,
    failedGateIds,
    evidence: {
      localSourceCommit: local.sourceCommit ?? null,
      target: {
        host: target.host ?? null,
        authorityObserved: target.authorityObserved ?? null,
        serviceUnit: target.serviceUnit ?? null,
        serviceState: target.serviceState ?? null,
      },
      runtime: {
        jarPath: runtime.jarPath ?? null,
        jarSha256: runtime.jarSha256 ?? null,
        attributionClassCount: runtime.attributionClassCount ?? null,
      },
      database: {
        serverVersion: database.serverVersion ?? null,
        database: database.database ?? null,
        totalTableCount: database.totalTableCount ?? null,
        migrationTableCount: database.migrationTableCount ?? null,
        publicInit043Applied: database.publicInit043Applied ?? null,
        w1aSchemaFingerprintVerified:
          database.w1aSchemaFingerprintVerified === true,
        missingPredecessorMigrations: missingPredecessors,
        driftedPredecessorDescriptions,
      },
      portals: {
        meHttpStatus: snapshot.portals?.meHttpStatus ?? null,
        adminHttpStatus: snapshot.portals?.adminHttpStatus ?? null,
        api2FbssHealthHttpStatus:
          snapshot.portals?.api2FbssHealthHttpStatus ?? null,
      },
      configuration: {
        missingExplicitFlags: missingFlags,
        flagsNotExplicitlyFalse: nonFalseFlags,
        eventKeyEntryCount: configuration.eventKeyEntryCount ?? 0,
        sameBindingSecretPresent:
          configuration.sameBindingSecretPresent === true,
      },
      backup: {
        receiptPath: backup.receiptPath ?? null,
        proven: backup.proven === true,
        receiptAnchorMatched: backup.receiptAnchorMatched === true,
        sha256: backup.sha256 ?? null,
        sizeBytes: backup.sizeBytes ?? null,
        restoreProcedureVerified:
          backup.restoreProcedureVerified === true,
      },
      deploymentChannel: {
        receiptPath: snapshot.deploymentChannel?.receiptPath ?? null,
        receiptValidated:
          snapshot.deploymentChannel?.receiptValidated === true,
        receiptAnchorMatched:
          snapshot.deploymentChannel?.receiptAnchorMatched === true,
        sourceCommit: snapshot.deploymentChannel?.sourceCommit ?? null,
        strictHeadBuildUploadSwitchReceiptScriptPresent:
          snapshot.deploymentChannel
            ?.strictHeadBuildUploadSwitchReceiptScriptPresent === true,
        applicationRollbackProven:
          snapshot.deploymentChannel?.applicationRollbackProven === true,
        databaseRollbackProven:
          snapshot.deploymentChannel?.databaseRollbackProven === true,
      },
    },
    nextAction: ready
      ? "Run the separate default-off release command with an explicit approval receipt."
      : `Close failed gates before any upload, migration, Nginx change, restart or cutover: ${failedGateIds.join(",")}`,
  };
}

function parseArgs(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const name = argv[index];
    if (!name.startsWith("--")) {
      throw new Error(`unexpected argument: ${name}`);
    }
    const value = argv[index + 1];
    if (!value || value.startsWith("--")) {
      throw new Error(`missing value for ${name}`);
    }
    options[name.slice(2)] = value;
    index += 1;
  }
  return options;
}

const isMain =
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isMain) {
  const options = parseArgs(process.argv.slice(2));
  if (!options.snapshot) {
    throw new Error("--snapshot is required");
  }
  const snapshotText = fs
    .readFileSync(options.snapshot, "utf8")
    .replace(/^\uFEFF/, "");
  const snapshot = JSON.parse(snapshotText);
  const result = evaluateProductionReadiness(snapshot);
  const json = `${JSON.stringify(result, null, 2)}\n`;
  if (options.output) {
    fs.mkdirSync(path.dirname(path.resolve(options.output)), {
      recursive: true,
    });
    fs.writeFileSync(options.output, json, "utf8");
  }
  process.stdout.write(json);
}
