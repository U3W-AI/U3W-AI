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
});

const W1A_SCHEMA_FINGERPRINT_SHA256 =
  "fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d";

const REQUIRED_DEFAULT_OFF_FLAGS = Object.freeze([
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
]);

const MANAGED_W1A_ENVIRONMENT_NAMES = Object.freeze([
  ...REQUIRED_DEFAULT_OFF_FLAGS,
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY_ID",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY",
  "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET",
  "FBSIR_ENGINE_TOKEN",
].sort());

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
  const portals = snapshot.portals ?? {};
  const w1a043CompatibilityVersions = asSet(
    local.w1a043CompatibilityVersions,
  );
  const canonicalBaselineCompatibilityVersions = asSet(
    local.canonicalBaselineCompatibilityVersions,
  );
  const migrationVersions = asSet(database.migrationVersions);
  const migrationDescriptions =
    database.migrationDescriptions &&
    typeof database.migrationDescriptions === "object" &&
    !Array.isArray(database.migrationDescriptions)
      ? database.migrationDescriptions
      : {};
  const configuredNames = asSet(configuration.environmentKeyNames);
  const explicitFalseNames = asSet(configuration.explicitFalseKeyNames);
  const runtimeMismatchNames = Array.isArray(
    runtime.processConfiguredEnvironmentMismatchNames,
  )
    ? runtime.processConfiguredEnvironmentMismatchNames
    : [];
  const runtimePendingNames = Array.isArray(
    runtime.processPendingRestartEnvironmentNames,
  )
    ? runtime.processPendingRestartEnvironmentNames
    : [];
  const exactRuntimeConfiguration =
    runtime.processConfiguredEnvironmentLoadState ===
      "EXACT_CONFIGURED" &&
    runtime.processConfiguredEnvironmentMatched === true &&
    runtime.processConfiguredEnvironmentPreStageCompatible === true &&
    runtimeMismatchNames.length === 0 &&
    runtimePendingNames.length === 0;
  const legacyPendingRuntimeConfiguration =
    runtime.processConfiguredEnvironmentLoadState ===
      "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART" &&
    runtime.processConfiguredEnvironmentMatched === false &&
    runtime.processConfiguredEnvironmentPreStageCompatible === true &&
    runtimeMismatchNames.length === 0 &&
    JSON.stringify(runtimePendingNames) ===
      JSON.stringify(MANAGED_W1A_ENVIRONMENT_NAMES);
  const engineCredentialPendingRuntimeConfiguration =
    runtime.processConfiguredEnvironmentLoadState ===
      "ENGINE_CREDENTIAL_PENDING_RESTART" &&
    runtime.processConfiguredEnvironmentMatched === false &&
    runtime.processConfiguredEnvironmentPreStageCompatible === true &&
    runtimeMismatchNames.length === 0 &&
    JSON.stringify(runtimePendingNames) ===
      JSON.stringify(["FBSIR_ENGINE_TOKEN"]);
  const runtimeConfigurationValid =
    exactRuntimeConfiguration ||
    legacyPendingRuntimeConfiguration ||
    engineCredentialPendingRuntimeConfiguration;
  const w1aSchemaAbsent =
    database.publicInit043Applied !== true &&
    database.boardAttributionTableCount === 0 &&
    database.boardAttributionTriggerCount === 0 &&
    database.boardAttributionPermissionCount === 0 &&
    database.boardAttributionInternalReceiptCount === 0;
  const w1aSchemaApplied =
    database.publicInit043Applied === true &&
    database.boardAttributionTableCount === 2 &&
    database.boardAttributionTriggerCount === 2 &&
    database.boardAttributionPermissionCount === 1 &&
    database.boardAttributionInternalReceiptCount === 1 &&
    database.w1aSchemaFingerprintSha256 ===
      W1A_SCHEMA_FINGERPRINT_SHA256;
  const missingPredecessors = REQUIRED_PREDECESSOR_MIGRATIONS.filter(
    (version) => !migrationVersions.has(version),
  );
  const driftedPredecessorDescriptions =
    REQUIRED_PREDECESSOR_MIGRATIONS.filter(
      (version) =>
        migrationDescriptions[version] !==
        REQUIRED_MIGRATION_DESCRIPTIONS[version],
    );
  const canonicalSchemaBaseline =
    database.migrationTableCount === 1 &&
    canonicalBaselineCompatibilityVersions.has(database.serverVersion) &&
    missingPredecessors.length === 0 &&
    driftedPredecessorDescriptions.length === 0;
  const preparationSourceProvenanceValid =
    hasExactString(local.preparationSourceCommit, /^[0-9a-f]{40}$/) &&
    local.preparationCommitAncestorOfSourceCommit === true &&
    local.preparationSourceCommitsConsistent === true &&
    configuration.configurationReceiptSchema ===
      "fbsir.u3wDefaultOffConfigurationReceipt.v3" &&
    configuration.configurationReceiptSourceCommit ===
      local.preparationSourceCommit &&
    backup.sourceCommit === local.preparationSourceCommit &&
    database.adminRootDependencyAdoptionSourceCommit ===
      local.preparationSourceCommit;
  const legacySchemaBaseline =
    database.migrationTableCount === 1 &&
    database.schemaBaselineMode === "LEGACY_ADOPTED_W1A_V2" &&
    database.legacyBaselineReceiptValid === true &&
    database.legacyBaselineReceiptAnchorMatched === true &&
    database.legacyBaselineLiveFactsMatched === true &&
    database.legacyAdminRootDependencyState ===
      "EXACT_CONTROLLED_DEPENDENCY" &&
    database.legacyAdminRootDependencyStateVerified === true &&
    database.legacyAdminRootDependencyReceiptCount === 1 &&
    database.legacyAdminRootDependencyVersionCount === 1 &&
    database.legacyAdminRootIdentityCount === 1 &&
    database.legacyAdminRootExactCount === 1 &&
    database.legacyAdminRootRoleBindingCount === 0 &&
    database.legacyAdminRootPageChildCount === 0 &&
    database.legacyForbiddenPublicInit001Through042ReceiptCount === 0 &&
    local.legacyBaselineCommitAncestorOfPreparationSourceCommit === true;
  const adminRootDependencyAdoptionValid =
    database.adminRootDependencyAdoptionReceiptSchema ===
      "fbsir.u3wLegacyAdminRootDependencyAdoptionReceipt.v2" &&
    database.adminRootDependencyAdoptionReceiptValid === true &&
    database.adminRootDependencyAdoptionReceiptAnchorMatched === true &&
    database.adminRootDependencyAdoptionLiveFactsMatched === true &&
    database.adminRootDependencyAdoptionHistoricalBindingsMatched === true &&
    database.adminRootDependencyAdoptionSourceCommit ===
      local.preparationSourceCommit &&
    database.adminRootDependencyAdoptionDatabaseServerUuid ===
      database.databaseServerUuid &&
    hasExactString(
      database.adminRootDependencyAdoptionLiveFactsSha256,
      /^[0-9a-f]{64}$/,
    ) &&
    hasExactString(
      database.adminRootDependencyAdoptionPlanReceiptSha256,
      /^[0-9a-f]{64}$/,
    ) &&
    database.adminRootDependencyAdoptionDependencyRowsFingerprintSha256 ===
      database.legacyAdminRootDependencyFactsSha256 &&
    database.publicInit043AnyReceiptCount === 0 &&
    database.boardAttributionInternalReceiptCount === 0 &&
    database.boardAttributionTableCount === 0 &&
    database.boardAttributionTriggerCount === 0 &&
    database.boardAttributionPermissionCount === 0 &&
    database.w1a043State === "ABSENT";
  const finalBackupChainValid =
    backup.bundleSchema ===
      "fbsir.u3wDatabaseBackupRestoreBundleReceipt.v2" &&
    backup.backupReceiptSchema ===
      "fbsir.u3wDatabaseBackupReceipt.v3" &&
    backup.restoreReceiptSchema ===
      "fbsir.u3wDatabaseRestoreRehearsalReceipt.v3" &&
    backup.externalAnchorSchema ===
      "fbsir.u3wDatabaseBackupRestoreExternalAnchor.v2" &&
    backup.externalAnchorVerified === true &&
    backup.adoptionReceiptBindingMatched === true &&
    backup.sourceDatabaseServerUuidMatched === true &&
    backup.sourceRestoredFactsExactlyMatched === true &&
    backup.adminRootDependencyAdoptionReceiptSha256 ===
      database.adminRootDependencyAdoptionReceiptSha256 &&
    backup.sourceDatabaseServerUuid === database.databaseServerUuid;
  const missingFlags = REQUIRED_DEFAULT_OFF_FLAGS.filter(
    (name) => !configuredNames.has(name),
  );
  const nonFalseFlags = REQUIRED_DEFAULT_OFF_FLAGS.filter(
    (name) => !explicitFalseNames.has(name),
  );

  const preparationGates = [
    gate(
      "strict_head",
      local.clean === true &&
        hasExactString(local.sourceCommit, /^[0-9a-f]{40}$/) &&
        local.sourceCommit === local.expectedSourceCommit &&
        hasExactString(local.branch, /^[A-Za-z0-9][A-Za-z0-9._/-]*$/) &&
        local.upstreamCommit === local.sourceCommit &&
        local.originHead === local.sourceCommit &&
        local.upstreamOriginAligned === true,
      "checked-out source must be the expected clean named branch aligned to upstream and origin",
    ),
    gate(
      "scoped_authority",
      target.host === "api2.u3w.com" &&
        target.authorityObserved === "root" &&
        target.serviceUnit === "fbsir-admin.service",
      "production authority must be root on api2.u3w.com and scoped to fbsir-admin.service",
    ),
    gate(
      "preparation_source_provenance",
      preparationSourceProvenanceValid &&
        (canonicalSchemaBaseline ||
          local.legacyBaselineCommitAncestorOfPreparationSourceCommit ===
            true),
      "configuration, dependency adoption and backup receipts must share one preparation commit; a historical baseline must be its proven ancestor",
    ),
    gate(
      "active_runtime_anchor",
      target.serviceState === "active" &&
        hasExactString(runtime.jarSha256, /^[0-9a-f]{64}$/) &&
        runtime.processDatabaseBindingMatched === true &&
        runtimeConfigurationValid &&
        hasExactString(
          runtime.processSecurityConfigurationHmacSha256,
          /^[0-9a-f]{64}$/,
        ) &&
        hasExactString(
          runtime.configuredEnvironmentHmacSha256,
          /^[0-9a-f]{64}$/,
        ) &&
        typeof runtime.jarPath === "string" &&
        runtime.jarPath.startsWith("/opt/fbsir/admin/"),
      "the active service and exact current JAR digest must be readable",
    ),
    gate(
      "versioned_schema_baseline",
      canonicalSchemaBaseline || legacySchemaBaseline,
      legacySchemaBaseline
        ? "a verified and externally anchored legacy W1A baseline receipt is present"
        : !canonicalBaselineCompatibilityVersions.has(database.serverVersion)
          ? `canonical baseline is unverified on MySQL ${database.serverVersion ?? "missing"} and no verified legacy baseline exists`
          : missingPredecessors.length > 0
          ? `missing migration predecessors: ${missingPredecessors.join(",")}`
          : driftedPredecessorDescriptions.length > 0
            ? `migration description drift: ${driftedPredecessorDescriptions.join(",")}`
            : "exact public_init_035 through public_init_042 receipts are present on a verified MySQL version",
    ),
    gate(
      "w1a_043_mysql_compatibility",
      w1a043CompatibilityVersions.has(database.serverVersion),
      w1a043CompatibilityVersions.has(database.serverVersion)
        ? "the exact public_init_043 migration and fingerprint passed on this MySQL build"
        : `public_init_043 is unverified on MySQL ${database.serverVersion ?? "missing"}`,
    ),
    gate(
      "w1a_schema_state",
      w1aSchemaAbsent || w1aSchemaApplied,
      "public_init_043 must be fully absent or backed by the exact public/internal receipts, tables, triggers, permission and fingerprint",
    ),
    gate(
      "admin_root_dependency_adoption_anchor",
      adminRootDependencyAdoptionValid,
      "the exact legacy admin-root dependency must be independently recomputed and bound to an externally anchored current-state adoption receipt while 043 remains absent",
    ),
    gate(
      "backup_restore_anchor",
      backup.proven === true &&
        backup.receiptAnchorMatched === true &&
        finalBackupChainValid &&
        hasExactString(backup.sha256, /^[0-9a-f]{64}$/) &&
        Number.isSafeInteger(backup.sizeBytes) &&
        backup.sizeBytes > 0 &&
        backup.restoreProcedureVerified === true &&
        backup.restoreLiveFactsMatched === true,
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
        configuration.activeEventKeyPairPresent === true &&
        configuration.previousEventKeyPairComplete === true &&
        configuration.sameBindingSecretPresent === true &&
        configuration.stagedApi2EventKeyMaterialMatched === true &&
        configuration.api2EventKeyFileCustodySecure === true &&
        configuration.environmentFilePathExact === true &&
        configuration.environmentFileCustodySecure === true &&
        configuration.cryptographicConfigurationShapeValid === true &&
        configuration.adminEngineCredentialValid === true &&
        configuration.adminEngineCredentialIndependent === true &&
        configuration.configurationReceiptValid === true &&
        configuration.configurationReceiptAnchorMatched === true &&
        configuration.configurationReceiptSchema ===
          "fbsir.u3wDefaultOffConfigurationReceipt.v3" &&
        configuration.engineCounterpartClosureClaimed === false &&
        configuration.configurationReceiptSourceCommit ===
          local.preparationSourceCommit,
      "the explicit active event key pair and independent same-binding secret must be held in a root-owned 0600 env file without disclosure",
    ),
    gate(
      "release_plan_and_rollback_contract",
      local.releasePlanVerified === true &&
        local.releasePlanTargetMatchedLive === true &&
        local.releasePlanSourceCommit === local.sourceCommit &&
        local.releaseRunnerContractVersion ===
          "fbsir.u3wDefaultOffReleaseRunner.v1",
      "a locally verified strict-HEAD stage/switch/rollback plan bound to this source commit is required",
    ),
  ];
  const stagedChannelValid =
    snapshot.deploymentChannel?.state === "STAGED_FOR_SWITCH" &&
    snapshot.deploymentChannel?.receiptValidated === true &&
    snapshot.deploymentChannel?.receiptAnchorMatched === true &&
    snapshot.deploymentChannel?.releasePlanTargetBindingVerified === true &&
    snapshot.deploymentChannel?.sourceCommit === local.sourceCommit &&
    snapshot.deploymentChannel
      ?.strictHeadBuildUploadSwitchReceiptScriptPresent === true &&
    snapshot.deploymentChannel?.applicationRollbackAssemblyVerified ===
      true &&
    snapshot.deploymentChannel?.applicationRollbackProven === false &&
    snapshot.deploymentChannel?.databaseRollbackSafetyProven === true &&
    snapshot.deploymentChannel?.stableDatabaseIdentityMatched === true &&
    snapshot.deploymentChannel?.stagedLiveStateMatched === true &&
    snapshot.deploymentChannel?.databaseDownClaimed === false;
  const deployedChannelValid =
    snapshot.deploymentChannel?.state === "DEPLOYED_DEFAULT_OFF" &&
    snapshot.deploymentChannel?.receiptValidated === true &&
    snapshot.deploymentChannel?.receiptAnchorMatched === true &&
    snapshot.deploymentChannel?.releasePlanTargetBindingVerified === true &&
    snapshot.deploymentChannel?.sourceCommit === local.sourceCommit &&
    snapshot.deploymentChannel
      ?.strictHeadBuildUploadSwitchReceiptScriptPresent === true &&
    snapshot.deploymentChannel?.actualActiveArtifactsMatched === true &&
    snapshot.deploymentChannel?.applicationRollbackAssemblyVerified ===
      true &&
    snapshot.deploymentChannel?.applicationRollbackProven === true &&
    snapshot.deploymentChannel?.databaseRollbackSafetyProven === true &&
    snapshot.deploymentChannel?.stableDatabaseIdentityMatched === true &&
    snapshot.deploymentChannel?.databaseDownClaimed === false &&
    snapshot.deploymentChannel?.u3wDirectCaptchaHealthy === true &&
    snapshot.deploymentChannel?.mePortalApiHealthy === true &&
    snapshot.deploymentChannel?.adminPortalApiHealthy === true &&
    snapshot.deploymentChannel?.mePortalReleaseMarkerMatched === true &&
    snapshot.deploymentChannel?.adminPortalReleaseMarkerMatched === true &&
    snapshot.deploymentChannel?.defaultOffIngressProbeVerified === true;
  const rolledBackChannelValid =
    snapshot.deploymentChannel?.state ===
      "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT" &&
    snapshot.deploymentChannel?.receiptValidated === true &&
    snapshot.deploymentChannel?.receiptAnchorMatched === true &&
    snapshot.deploymentChannel?.sourceCommit === local.sourceCommit &&
    snapshot.deploymentChannel?.applicationRollbackProven === true &&
    snapshot.deploymentChannel?.databaseRollbackSafetyProven === true &&
    snapshot.deploymentChannel?.stableDatabaseIdentityMatched === true &&
    snapshot.deploymentChannel?.rollbackLiveStateMatched === true &&
    snapshot.deploymentChannel?.databaseDownClaimed === false;
  const postDeploymentGates = [
    gate(
      "staged_release_receipt",
      stagedChannelValid || deployedChannelValid,
      "a target-rehashed, externally anchored staged release receipt is required before switching",
    ),
    gate(
      "deployed_default_off_receipt",
      deployedChannelValid,
      "a deployed default-off receipt with active U3W artifacts, portal health, application rollback proof and forward-only database rollback safety is required",
    ),
  ];

  const failedGateIds = preparationGates
    .filter((item) => !item.pass)
    .map((item) => item.id);
  const postDeployFailedGateIds = postDeploymentGates
    .filter((item) => !item.pass)
    .map((item) => item.id);
  const prepared = failedGateIds.length === 0;
  const status = rolledBackChannelValid
    ? "ROLLED_BACK_APPLICATION_DB_043_RETAINED"
    : !prepared
    ? "NOT_READY_FOR_PRODUCTION_RELEASE"
    : deployedChannelValid
      ? "DEPLOYED_DEFAULT_OFF"
      : stagedChannelValid
        ? "STAGED_FOR_SWITCH"
        : "PREPARED_FOR_STAGE";

  return {
    schema: SCHEMA,
    observedAt: snapshot.observedAt ?? null,
    status,
    productionChanged: deployedChannelValid || rolledBackChannelValid,
    readyForDefaultOffRelease: prepared,
    gates: preparationGates,
    failedGateIds,
    postDeploymentGates,
    postDeployFailedGateIds,
    evidence: {
      localSourceCommit: local.sourceCommit ?? null,
      sourceControl: {
        branch: local.branch ?? null,
        upstreamCommit: local.upstreamCommit ?? null,
        originHead: local.originHead ?? null,
        upstreamOriginAligned: local.upstreamOriginAligned === true,
      },
      preparationSource: {
        commit: local.preparationSourceCommit ?? null,
        ancestorOfSourceCommit:
          local.preparationCommitAncestorOfSourceCommit === true,
        receiptsConsistent:
          local.preparationSourceCommitsConsistent === true,
        legacyBaselineAncestor:
          local.legacyBaselineCommitAncestorOfPreparationSourceCommit ===
          true,
      },
      releasePlan: {
        verified: local.releasePlanVerified === true,
        targetMatchedLive: local.releasePlanTargetMatchedLive === true,
        sourceCommit: local.releasePlanSourceCommit ?? null,
        runnerContractVersion: local.releaseRunnerContractVersion ?? null,
        receiptPath: local.releasePlanReceiptPath ?? null,
        receiptSha256: local.releasePlanReceiptSha256 ?? null,
      },
      w1a043CompatibilityVersions: Array.from(
        w1a043CompatibilityVersions,
      ).sort(),
      w1a043CompatibilityReceipts: Array.isArray(
        local.w1a043CompatibilityReceipts,
      )
        ? local.w1a043CompatibilityReceipts
        : [],
      canonicalBaselineCompatibilityVersions: Array.from(
        canonicalBaselineCompatibilityVersions,
      ).sort(),
      target: {
        host: target.host ?? null,
        authorityObserved: target.authorityObserved ?? null,
        serviceUnit: target.serviceUnit ?? null,
        serviceState: target.serviceState ?? null,
      },
      runtime: {
        jarPath: runtime.jarPath ?? null,
        jarSha256: runtime.jarSha256 ?? null,
        configuredJarPath: runtime.configuredJarPath ?? null,
        configuredJarSha256: runtime.configuredJarSha256 ?? null,
        processJarPath: runtime.processJarPath ?? null,
        processJarSha256: runtime.processJarSha256 ?? null,
        attributionClassCount: runtime.attributionClassCount ?? null,
      },
      database: {
        serverVersion: database.serverVersion ?? null,
        database: database.database ?? null,
        totalTableCount: database.totalTableCount ?? null,
        migrationTableCount: database.migrationTableCount ?? null,
        schemaBaselineMode: database.schemaBaselineMode ?? null,
        legacyBaselineReceiptValid:
          database.legacyBaselineReceiptValid === true,
        legacyBaselineReceiptAnchorMatched:
          database.legacyBaselineReceiptAnchorMatched === true,
        legacyBaselineLiveFactsMatched:
          database.legacyBaselineLiveFactsMatched === true,
        legacyAdminRootDependencyState:
          database.legacyAdminRootDependencyState ?? null,
        legacyAdminRootDependencyStateVerified:
          database.legacyAdminRootDependencyStateVerified === true,
        legacyAdminRootDependencyReceiptCount:
          database.legacyAdminRootDependencyReceiptCount ?? null,
        legacyAdminRootDependencyVersionCount:
          database.legacyAdminRootDependencyVersionCount ?? null,
        legacyAdminRootIdentityCount:
          database.legacyAdminRootIdentityCount ?? null,
        legacyAdminRootExactCount:
          database.legacyAdminRootExactCount ?? null,
        legacyAdminRootRoleBindingCount:
          database.legacyAdminRootRoleBindingCount ?? null,
        legacyAdminRootPageChildCount:
          database.legacyAdminRootPageChildCount ?? null,
        legacyForbiddenPublicInit001Through042ReceiptCount:
          database.legacyForbiddenPublicInit001Through042ReceiptCount ?? null,
        legacyAdminRootDependencyFactsSha256:
          database.legacyAdminRootDependencyFactsSha256 ?? null,
        adminRootDependencyAdoptionReceiptPath:
          database.adminRootDependencyAdoptionReceiptPath ?? null,
        adminRootDependencyAdoptionReceiptSha256:
          database.adminRootDependencyAdoptionReceiptSha256 ?? null,
        adminRootDependencyAdoptionReceiptValid:
          database.adminRootDependencyAdoptionReceiptValid === true,
        adminRootDependencyAdoptionReceiptAnchorMatched:
          database.adminRootDependencyAdoptionReceiptAnchorMatched === true,
        adminRootDependencyAdoptionLiveFactsMatched:
          database.adminRootDependencyAdoptionLiveFactsMatched === true,
        adminRootDependencyAdoptionLiveFactsSha256:
          database.adminRootDependencyAdoptionLiveFactsSha256 ?? null,
        adminRootDependencyAdoptionPlanReceiptSha256:
          database.adminRootDependencyAdoptionPlanReceiptSha256 ?? null,
        adminRootDependencyAdoptionDependencyRowsFingerprintSha256:
          database
            .adminRootDependencyAdoptionDependencyRowsFingerprintSha256 ??
          null,
        adminRootDependencyAdoptionHistoricalBindingsMatched:
          database.adminRootDependencyAdoptionHistoricalBindingsMatched ===
          true,
        legacyBaselineReceiptDigest:
          database.legacyBaselineReceiptDigest ?? null,
        legacyBaselineSourceCommit:
          database.legacyBaselineSourceCommit ?? null,
        publicInit043Applied: database.publicInit043Applied ?? null,
        boardAttributionTableCount:
          database.boardAttributionTableCount ?? null,
        boardAttributionTriggerCount:
          database.boardAttributionTriggerCount ?? null,
        boardAttributionPermissionCount:
          database.boardAttributionPermissionCount ?? null,
        boardAttributionInternalReceiptCount:
          database.boardAttributionInternalReceiptCount ?? null,
        w1aSchemaFingerprintVerified:
          database.w1aSchemaFingerprintSha256 ===
          W1A_SCHEMA_FINGERPRINT_SHA256,
        w1aSchemaFingerprintSha256:
          database.w1aSchemaFingerprintSha256 ?? null,
        missingPredecessorMigrations: missingPredecessors,
        driftedPredecessorDescriptions,
      },
      portals: {
        meHttpStatus: portals.meHttpStatus ?? null,
        adminHttpStatus: portals.adminHttpStatus ?? null,
        u3wDirectCaptcha: portals.u3wDirectCaptcha ?? null,
        meApiCaptcha: portals.meApiCaptcha ?? null,
        adminApiCaptcha: portals.adminApiCaptcha ?? null,
      },
      configuration: {
        missingExplicitFlags: missingFlags,
        flagsNotExplicitlyFalse: nonFalseFlags,
        eventKeyEntryCount: configuration.eventKeyEntryCount ?? 0,
        activeEventKeyPairPresent:
          configuration.activeEventKeyPairPresent === true,
        activeEventKeyId: configuration.activeEventKeyId ?? null,
        previousEventKeyPairComplete:
          configuration.previousEventKeyPairComplete === true,
        sameBindingSecretPresent:
          configuration.sameBindingSecretPresent === true,
        stagedApi2EventKeyMaterialMatched:
          configuration.stagedApi2EventKeyMaterialMatched === true,
        api2EventKeyFileCustodySecure:
          configuration.api2EventKeyFileCustodySecure === true,
        environmentFilePathExact:
          configuration.environmentFilePathExact === true,
        environmentFileCustodySecure:
          configuration.environmentFileCustodySecure === true,
        cryptographicConfigurationShapeValid:
          configuration.cryptographicConfigurationShapeValid === true,
        adminEngineCredentialValid:
          configuration.adminEngineCredentialValid === true,
        adminEngineCredentialIndependent:
          configuration.adminEngineCredentialIndependent === true,
        configurationReceiptValid:
          configuration.configurationReceiptValid === true,
        configurationReceiptAnchorMatched:
          configuration.configurationReceiptAnchorMatched === true,
        configurationReceiptSourceCommit:
          configuration.configurationReceiptSourceCommit ?? null,
        configurationReceiptSha256:
          configuration.configurationReceiptSha256 ?? null,
        configurationReceiptSchema:
          configuration.configurationReceiptSchema ?? null,
        engineCounterpartClosureClaimed:
          configuration.engineCounterpartClosureClaimed ?? null,
      },
      backup: {
        receiptPath: backup.receiptPath ?? null,
        sourceCommit: backup.sourceCommit ?? null,
        proven: backup.proven === true,
        receiptAnchorMatched: backup.receiptAnchorMatched === true,
        sha256: backup.sha256 ?? null,
        sizeBytes: backup.sizeBytes ?? null,
        restoreProcedureVerified:
          backup.restoreProcedureVerified === true,
        restoreLiveFactsMatched:
          backup.restoreLiveFactsMatched === true,
        bundleSchema: backup.bundleSchema ?? null,
        backupReceiptSchema: backup.backupReceiptSchema ?? null,
        restoreReceiptSchema: backup.restoreReceiptSchema ?? null,
        externalAnchorSchema: backup.externalAnchorSchema ?? null,
        externalAnchorVerified: backup.externalAnchorVerified === true,
        adoptionReceiptBindingMatched:
          backup.adoptionReceiptBindingMatched === true,
        sourceDatabaseServerUuidMatched:
          backup.sourceDatabaseServerUuidMatched === true,
        sourceRestoredFactsExactlyMatched:
          backup.sourceRestoredFactsExactlyMatched === true,
      },
      deploymentChannel: {
        state: snapshot.deploymentChannel?.state ?? null,
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
        applicationRollbackAssemblyVerified:
          snapshot.deploymentChannel
            ?.applicationRollbackAssemblyVerified === true,
        databaseRollbackSafetyProven:
          snapshot.deploymentChannel
            ?.databaseRollbackSafetyProven === true,
        databaseDownClaimed:
          snapshot.deploymentChannel?.databaseDownClaimed ?? null,
        actualActiveArtifactsMatched:
          snapshot.deploymentChannel?.actualActiveArtifactsMatched === true,
        currentLinkResolved:
          snapshot.deploymentChannel?.currentLinkResolved ?? null,
        u3wDirectCaptchaHealthy:
          snapshot.deploymentChannel?.u3wDirectCaptchaHealthy === true,
        mePortalApiHealthy:
          snapshot.deploymentChannel?.mePortalApiHealthy === true,
        adminPortalApiHealthy:
          snapshot.deploymentChannel?.adminPortalApiHealthy === true,
        mePortalReleaseMarkerMatched:
          snapshot.deploymentChannel?.mePortalReleaseMarkerMatched === true,
        adminPortalReleaseMarkerMatched:
          snapshot.deploymentChannel
            ?.adminPortalReleaseMarkerMatched === true,
      },
    },
    nextAction: rolledBackChannelValid
      ? "The application is on the immutable predecessor while 043 is retained; inspect database safety, issue a fresh plan and do not claim database rollback."
      : !prepared
      ? `Close preparation gates before any upload, migration, Nginx change, restart or cutover: ${failedGateIds.join(",")}`
      : deployedChannelValid
        ? "Observe default-off U3W and portal health while retaining the forward-only 043 safety and application rollback evidence."
        : stagedChannelValid
          ? "Run the separately approved default-off switch and verify active artifacts."
          : "Run the separate stage command; it must not switch current, restart services or mutate the database.",
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
