#!/usr/bin/env python3
"""Target-side W1A default-off release state machine.

The worker is executed from an exact committed Git blob. Stage only writes an
isolated release directory. Apply performs the additive 043 migration, an
atomic current-link switch, a systemd drop-in switch, and the two portal Nginx
cutover. Rollback restores the pre-existing unit and placeholder site while
retaining the additive, default-off database objects.
"""

import argparse
import base64
import datetime as dt
import fcntl
import hashlib
import hmac
import json
import os
import pathlib
import re
import shutil
import stat
import subprocess
import tarfile
import time
import urllib.error
import urllib.request
import zipfile


TARGET_HOST = "api2.u3w.com"
SERVICE_UNIT = "fbsir-admin.service"
DATABASE = "fbsir"
ADMIN_ROOT = pathlib.Path("/opt/fbsir/admin")
RELEASE_ROOT = ADMIN_ROOT / "releases"
CURRENT_LINK = ADMIN_ROOT / "current"
LOCK_PATH = ADMIN_ROOT / ".u3w-production-change.lock"
ENV_PATH = pathlib.Path("/etc/u3w/fbsir-admin.env")
ENV_PATH_TEXT = "/etc/u3w/fbsir-admin.env"
ADDITIONAL_CONFIG_PATH = pathlib.Path(
    "/opt/fbsir/admin/application-connector.yml"
)
DROPIN_DIRECTORY = pathlib.Path("/etc/systemd/system/fbsir-admin.service.d")
DROPIN_PATH = DROPIN_DIRECTORY / "20-u3w-default-off-release.conf"
NGINX_PATH = pathlib.Path("/etc/nginx/conf.d/u3w-placeholder-sites.conf")
LATEST_RECEIPT = RELEASE_ROOT / "latest-receipt.json"
BACKUP_RECEIPT = ADMIN_ROOT / "backups/latest/receipt.json"
BASELINE_RECEIPT = ADMIN_ROOT / "baselines/latest/adoption-receipt.json"
ADMIN_ROOT_DEPENDENCY_RECEIPT = (
    ADMIN_ROOT / "dependencies/latest/adoption-receipt.json"
)
CONFIGURATION_RECEIPT = (
    ADMIN_ROOT / "configuration/latest/configuration-receipt.json"
)
API2_EVENT_KEY_PATH = pathlib.Path(
    "/etc/u3w/secrets/independent-board-attribution-event-key"
)
RUN_PATTERN = re.compile(
    r"w1a-release-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z"
)
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
MIGRATION_PUBLIC_VERSION = "public_init_043"
MIGRATION_PUBLIC_DESCRIPTION = (
    "APPLIED:Independent Board exact official experts attribution v1"
)
MIGRATION_INTERNAL_VERSION = (
    "20260723_independent_board_attribution_v1_043"
)
MIGRATION_INTERNAL_DESCRIPTION = (
    "APPLIED:exact WorkBuddy experts 26.7.21 attribution journey "
    "and append-only event ledger"
)
EXPECTED_W1A_SCHEMA_FINGERPRINT = (
    "fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d"
)
MIGRATION_DORMANT_DATA_FIELDS = (
    "eventCount",
    "probeEventCount",
    "naturalEventCount",
    "nonProbeEventCount",
    "authoritativeProductCreditCount",
    "journeyCount",
    "probeJourneyCount",
    "naturalJourneyCount",
    "nonProbeJourneyCount",
)
LEGACY_MIGRATION_FACT_FIELDS = frozenset(
    {
        "publicReceiptCount",
        "internalReceiptCount",
        "tableCount",
        "triggerCount",
        "permissionCount",
        "eventCount",
        "journeyCount",
        "schemaFingerprintSha256",
    }
)
MIGRATION_FACT_FIELDS = (
    LEGACY_MIGRATION_FACT_FIELDS
    | frozenset(MIGRATION_DORMANT_DATA_FIELDS)
)
DEPLOYMENT_RECEIPT_SCHEMA = (
    "fbsir.u3wW1aDeploymentReadinessReceipt.v3"
)
LEGACY_DEPLOYMENT_RECEIPT_SCHEMA = (
    "fbsir.u3wW1aDeploymentReadinessReceipt.v2"
)
DATABASE_ROLLBACK_SAFETY_SCHEMA = (
    "fbsir.u3wDatabaseRollbackSafetyReceipt.v2"
)
LEGACY_DATABASE_ROLLBACK_SAFETY_SCHEMA = (
    "fbsir.u3wDatabaseRollbackSafetyReceipt.v1"
)
FINAL_CURRENT_READ_SCHEMA = (
    "fbsir.u3wDefaultOffFinalCurrentRead.v2"
)
LEGACY_FINAL_CURRENT_READ_SCHEMA = (
    "fbsir.u3wDefaultOffFinalCurrentRead.v1"
)
APPLICATION_ROLLBACK_EXECUTION_SCHEMA = (
    "fbsir.u3wApplicationRollbackExecutionReceipt.v2"
)
LEGACY_APPLICATION_ROLLBACK_EXECUTION_SCHEMA = (
    "fbsir.u3wApplicationRollbackExecutionReceipt.v1"
)
ROLLBACK_RECEIPT_SCHEMA = (
    "fbsir.u3wDefaultOffReleaseRollbackReceipt.v2"
)
LEGACY_ROLLBACK_RECEIPT_SCHEMA = (
    "fbsir.u3wDefaultOffReleaseRollbackReceipt.v1"
)
ROLLBACK_VERIFICATION_SCHEMA = (
    "fbsir.u3wDefaultOffRollbackVerificationReceipt.v2"
)
LEGACY_ROLLBACK_VERIFICATION_SCHEMA = (
    "fbsir.u3wDefaultOffRollbackVerificationReceipt.v1"
)
APPLY_FAILURE_RECEIPT_SCHEMA = (
    "fbsir.u3wDefaultOffReleaseFailureReceipt.v2"
)
LEGACY_APPLY_FAILURE_RECEIPT_SCHEMA = (
    "fbsir.u3wDefaultOffReleaseFailureReceipt.v1"
)
INTERRUPTED_APPLY_RECOVERY_RECEIPT_SCHEMA = (
    "fbsir.u3wDefaultOffInterruptedApplyRecoveryReceipt.v1"
)
INTERRUPTED_APPLY_RECOVERY_STATE = "INTERRUPTED_APPLY_RECOVERED_APPLICATION_DATABASE_043_RETAINED_DORMANT"
INTERRUPTED_APPLY_RECOVERY_TOPOLOGY_STATE = (
    "EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR"
)
INTERRUPTED_APPLY_RECOVERY_PLAN_SCHEMA = (
    "fbsir.u3wInterruptedApplyRecoveryPlan.v1"
)
INTERRUPTED_APPLY_RECOVERY_PLAN_STATE = (
    "INTERRUPTED_APPLY_RECOVERY_CANONICALIZATION_PLANNED"
)
INTERRUPTED_APPLY_RECOVERY_APPROVAL_NAME = (
    "interrupted-apply-recovery-approval.json"
)
INTERRUPTED_APPLY_RECOVERY_PLAN_NAME = (
    "interrupted-apply-recovery-plan.json"
)
INTERRUPTED_APPLY_RECOVERY_RECEIPT_FIELDS = frozenset(
    (
        "schema",
        "state",
        "releaseId",
        "sourceCommit",
        "recoveryRunId",
        "executorSourceCommit",
        "approvalReceiptSha256",
        "approvalNonce",
        "runnerSha256",
        "workerSha256",
        "recoveryPlanReceiptSha256",
        "stageReceiptPath",
        "stageReceiptSha256",
        "applyFailureReceiptPath",
        "applyFailureReceiptSha256",
        "applyFailureReceiptSchema",
        "applyFailureReceiptState",
        "applyFailureReceiptManifest",
        "applyFailureReceiptManifestSha256",
        "applicationRestored",
        "topologyRestored",
        "deploymentCommitOutcome",
        "deploymentReceiptAbsent",
        "rollbackReceiptAbsent",
        "currentLinkAbsent",
        "releaseDropInMatched",
        "retainedMigrationFacts",
        "allW1aFlagsExplicitFalse",
        "databaseDownClaimed",
        "productionFilesystemChanged",
        "productionDatabaseChanged",
        "productionDatabaseChangedThisRecoveryRun",
        "productionDatabaseChangedSinceStage",
        "productionServiceChanged",
        "productionServiceChangedThisRecoveryRun",
        "productionServiceChangedSinceStage",
        "officialExpertsPackageChanged",
        "observedAt",
    )
)
INTERRUPTED_APPLY_RECOVERY_APPROVAL_FIELDS = frozenset(
    (
        "schema",
        "action",
        "targetHost",
        "runId",
        "executorSourceCommit",
        "targetReleaseId",
        "targetSourceCommit",
        "approvedAt",
        "expiresAt",
        "authorizedBy",
        "concurrentDdlProhibited",
        "productionFilesystemWrite",
        "productionDatabaseWrite",
        "productionServiceChange",
        "officialExpertsPackageChange",
        "expectedStageReceiptSha256",
        "expectedApplyFailureReceiptSha256",
        "expectedApplyFailureManifestSha256",
        "requestDigest",
        "approvalNonce",
        "runnerSha256",
        "workerSha256",
    )
)
INTERRUPTED_APPLY_RECOVERY_PLAN_FIELDS = frozenset(
    (
        "schema",
        "mode",
        "state",
        "targetHost",
        "serviceUnit",
        "recoveryRunId",
        "executorSourceCommit",
        "targetReleaseId",
        "targetSourceCommit",
        "stageReceiptSha256",
        "applyFailureReceiptSha256",
        "applyFailureManifestSha256",
        "runnerSha256",
        "workerSha256",
        "productionFilesystemWrite",
        "productionDatabaseWrite",
        "productionServiceChange",
        "officialExpertsPackageChange",
        "generatedAt",
        "expiresAt",
    )
)
WORKER_RESULT_SCHEMA = "fbsir.u3wDefaultOffReleaseWorkerResult.v2"
FALSE_FLAGS = (
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
)
EVENT_KEY_ID_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID"
)
EVENT_KEY_NAME = "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY"
PREVIOUS_EVENT_KEY_ID_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY_ID"
)
PREVIOUS_EVENT_KEY_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY"
)
SAME_BINDING_KEY_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET"
)
ADMIN_ENGINE_TOKEN_NAME = "FBSIR_ENGINE_TOKEN"
TOKEN_SECRET_NAME = "FBSIR_TOKEN_SECRET"
ATTRIBUTION_INGRESS_PATH = (
    "/internal/independent-board/attribution/events"
)
DISABLED_INGRESS_HTTP_STATUS = 404
ATTRIBUTION_EVENT_SIGNED_STRING_FIELDS = (
    "agentName",
    "channel",
    "classificationSource",
    "classifierVersion",
    "confidenceBucket",
    "contractId",
    "embeddedContractVersion",
    "eventId",
    "eventType",
    "expiresAt",
    "hostClientFamily",
    "hostVersion",
    "intentSignal",
    "issuedAt",
    "journeyId",
    "keyId",
    "listedManifestVersion",
    "listedSurface",
    "marketplace",
    "nonce",
    "occurredAt",
    "outcome",
    "packageId",
    "previousEventDigest",
    "productId",
    "receiptId",
    "requestSource",
    "reviewMode",
    "sameBindingKey",
    "schemaVersion",
    "serverBindingId",
    "signatureAlgorithm",
    "tenantSubjectDigest",
    "terminal",
    "traceparent",
    "trafficAuthority",
    "trafficClass",
)
MANAGED_W1A_ENVIRONMENT_NAMES = FALSE_FLAGS + (
    EVENT_KEY_ID_NAME,
    EVENT_KEY_NAME,
    PREVIOUS_EVENT_KEY_ID_NAME,
    PREVIOUS_EVENT_KEY_NAME,
    SAME_BINDING_KEY_NAME,
)
MANAGED_RESTART_ENVIRONMENT_NAMES = (
    MANAGED_W1A_ENVIRONMENT_NAMES
    + (ADMIN_ENGINE_TOKEN_NAME, TOKEN_SECRET_NAME)
)
DATABASE_ENVIRONMENT_NAMES = (
    "WXFBSIR_MYSQL_URL",
    "WXFBSIR_MYSQL_USERNAME",
    "WXFBSIR_MYSQL_PASSWORD",
)
DATABASE_ENVIRONMENT_ALIAS_NAMES = (
    "FBSIR_MYSQL_URL",
    "FBSIR_MYSQL_USERNAME",
    "FBSIR_MYSQL_PASSWORD",
)
PROCESS_SECURITY_ENVIRONMENT_NAMES = (
    DATABASE_ENVIRONMENT_NAMES
    + DATABASE_ENVIRONMENT_ALIAS_NAMES
    + FALSE_FLAGS
    + (ADMIN_ENGINE_TOKEN_NAME, TOKEN_SECRET_NAME)
)
FORBIDDEN_RUNTIME_OVERRIDE_NAMES = frozenset(
    (
        "SPRING_APPLICATION_JSON",
        "SPRING_CONFIG_IMPORT",
        "SPRING_CONFIG_LOCATION",
        "SPRING_CONFIG_ADDITIONAL_LOCATION",
        "SPRING_CONFIG_NAME",
        "SPRING_CONFIG_ON_NOT_FOUND",
        "SPRING_PROFILES_ACTIVE",
        "SPRING_PROFILES_INCLUDE",
        "JAVA_TOOL_OPTIONS",
        "_JAVA_OPTIONS",
        "JDK_JAVA_OPTIONS",
    )
)
EXPECTED_ADDITIONAL_CONFIG_PATHS = frozenset(
    (
        "fbsir",
        "fbsir.connector-insights",
        "fbsir.connector-insights.ingest-enabled",
    )
)
EXPECTED_PYYAML_VERSION = "6.0.1"
EXTERNAL_CONFIG_NAME_PATTERN = re.compile(
    r"application(?:-[A-Za-z0-9._-]+)?\.(?:yml|yaml|properties)",
    re.IGNORECASE,
)
PLAN_TARGET_FIELDS = frozenset(
    (
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
    )
)
RELEASE_ARTIFACT_RELATIVE_PATHS = (
    "backend/fbsir-admin.jar",
    "sql/public_init_043.sql",
    "release-runner.ps1",
    "release-worker.py",
    "evidence/build-receipt.json",
    "evidence/release-plan.json",
    "evidence/frontend-manifest.txt",
    "evidence/systemd-dropin.conf",
    "evidence/rollback-systemd-dropin.conf",
    "evidence/u3w-portal-sites.conf",
    "evidence/application-rollback-assembly.json",
    "evidence/database-rollback-safety.json",
    "rollback/previous-admin.jar",
    "rollback/placeholder-sites.conf",
)
APPLY_FAILURE_NAME_PATTERN = re.compile(
    r"apply-failure-[0-9]{8}T[0-9]{12}Z-[0-9a-f]{12}\.json"
)
ROLLBACK_FAILURE_NAME_PATTERN = re.compile(
    r"rollback-failure-[0-9]{8}T[0-9]{12}Z-[0-9a-f]{12}\.json"
)


class ReleaseMutationFailure(RuntimeError):
    """A failed mutation with an immutable, release-owned failure receipt."""

    def __init__(self, message, failure_receipt_path):
        super().__init__(message)
        self.failure_receipt_path = pathlib.Path(failure_receipt_path)


def canonical_json(value):
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )


def utc_now():
    return (
        dt.datetime.now(dt.timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with pathlib.Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def fsync_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def validate_sha(value, label, allow_zero=False):
    text = str(value or "")
    if not SHA_PATTERN.fullmatch(text):
        raise RuntimeError(label + " is not a SHA-256 digest")
    if not allow_zero and text == "0" * 64:
        raise RuntimeError(label + " cannot be zero")
    return text


def validate_ancestor_chain(path):
    candidate = pathlib.Path(path)
    chain = [candidate]
    while candidate != candidate.parent:
        candidate = candidate.parent
        chain.append(candidate)
    for entry in reversed(chain):
        if not entry.exists():
            continue
        status = entry.lstat()
        if stat.S_ISLNK(status.st_mode):
            raise RuntimeError("unsafe symlink ancestor: " + str(entry))
        if status.st_uid != 0 or status.st_gid != 0:
            raise RuntimeError("unsafe ancestor owner: " + str(entry))
        if stat.S_ISDIR(status.st_mode) and status.st_mode & 0o022:
            raise RuntimeError("unsafe writable ancestor: " + str(entry))


def safe_directory(path, mode=0o700):
    path = pathlib.Path(path)
    validate_ancestor_chain(path.parent)
    created = False
    if not path.exists():
        path.mkdir(mode=mode)
        fsync_directory(path.parent)
        created = True
    status = path.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink < 2
        or status.st_mode & 0o022
        or (not created and status.st_mode & 0o777 != mode)
    ):
        raise RuntimeError("unsafe release directory: " + str(path))
    if created:
        os.chmod(path, mode)
    return path


def ensure_parent_directory(path, create_mode=0o700):
    """Validate an existing parent without rewriting system directory modes."""
    path = pathlib.Path(path)
    validate_ancestor_chain(path.parent)
    if not path.exists():
        return safe_directory(path, create_mode)
    status = path.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink < 2
        or status.st_mode & 0o022
    ):
        raise RuntimeError("unsafe parent directory: " + str(path))
    return path


def acquire_release_lock(descriptor, lock_mode, timeout_seconds=30):
    deadline = time.monotonic() + timeout_seconds
    operation = lock_mode | getattr(fcntl, "LOCK_NB", 4)
    while True:
        try:
            fcntl.flock(descriptor, operation)
            return
        except BlockingIOError as error:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise RuntimeError(
                    "global release lock acquisition timed out"
                ) from error
            time.sleep(min(0.1, remaining))


def validate_regular_file(path, parent=None, modes=(0o600, 0o640, 0o644)):
    path = pathlib.Path(path)
    if path.is_symlink() or not path.is_file():
        raise RuntimeError("required regular file is absent: " + str(path))
    if parent is not None and path.resolve().parent != pathlib.Path(parent).resolve():
        raise RuntimeError("file escaped its expected parent: " + str(path))
    status = path.stat()
    if (
        status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink != 1
        or status.st_mode & 0o777 not in modes
    ):
        raise RuntimeError("file custody is invalid: " + str(path))
    return path


def atomic_bytes(path, payload, mode=0o600):
    path = pathlib.Path(path)
    ensure_parent_directory(path.parent)
    if path.exists() or path.is_symlink():
        status = path.lstat()
        if (
            not stat.S_ISREG(status.st_mode)
            or stat.S_ISLNK(status.st_mode)
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_nlink != 1
            or status.st_mode & 0o022
        ):
            raise RuntimeError("unsafe existing target: " + str(path))
    partial = path.with_name(path.name + ".partial")
    if partial.exists() or partial.is_symlink():
        status = partial.lstat()
        if (
            not partial.is_file()
            or partial.is_symlink()
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_nlink != 1
        ):
            raise RuntimeError("unsafe stale partial: " + str(partial))
        partial.unlink()
    descriptor = os.open(
        partial,
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | getattr(os, "O_NOFOLLOW", 0),
        mode,
    )
    try:
        with os.fdopen(descriptor, "wb", closefd=False) as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
    finally:
        os.close(descriptor)
    os.chown(partial, 0, 0)
    os.chmod(partial, mode)
    os.replace(partial, path)
    fsync_directory(path.parent)


def atomic_json(path, value):
    atomic_bytes(
        path, (canonical_json(value) + "\n").encode("utf-8"), 0o600
    )


def atomic_bytes_create_new(path, payload, mode=0o600):
    """Create one immutable file without replacing a race winner."""
    path = pathlib.Path(path)
    ensure_parent_directory(path.parent)
    descriptor = os.open(
        path,
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | getattr(os, "O_NOFOLLOW", 0),
        mode,
    )
    created_identity = os.fstat(descriptor)
    try:
        with os.fdopen(descriptor, "wb", closefd=False) as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        if hasattr(os, "fchown"):
            os.fchown(descriptor, 0, 0)
        if hasattr(os, "fchmod"):
            os.fchmod(descriptor, mode)
        os.fsync(descriptor)
    except Exception:
        try:
            current = path.lstat()
            if (
                current.st_dev == created_identity.st_dev
                and current.st_ino == created_identity.st_ino
                and current.st_nlink == 1
            ):
                path.unlink()
                if os.name == "posix":
                    fsync_directory(path.parent)
        except OSError:
            pass
        raise
    finally:
        os.close(descriptor)
    if os.name == "posix":
        fsync_directory(path.parent)


def atomic_json_create_new(path, value):
    """Create one immutable JSON receipt without replacing a race winner."""
    atomic_bytes_create_new(
        path,
        (canonical_json(value) + "\n").encode("utf-8"),
        0o600,
    )


def atomic_symlink(target, link):
    link = pathlib.Path(link)
    ensure_parent_directory(link.parent)
    if link.exists() and not link.is_symlink():
        raise RuntimeError("symlink target path is occupied: " + str(link))
    if link.is_symlink():
        status = link.lstat()
        if status.st_uid != 0 or status.st_gid != 0:
            raise RuntimeError("existing symlink custody is invalid: " + str(link))
    partial = link.with_name("." + link.name + ".next")
    if partial.exists() or partial.is_symlink():
        status = partial.lstat()
        if (
            not stat.S_ISLNK(status.st_mode)
            or status.st_uid != 0
            or status.st_gid != 0
        ):
            raise RuntimeError("unsafe stale symlink partial: " + str(partial))
        partial.unlink()
    os.symlink(str(target), partial)
    os.replace(partial, link)
    fsync_directory(link.parent)


def validated_latest_receipt_target():
    if not LATEST_RECEIPT.exists() and not LATEST_RECEIPT.is_symlink():
        return None
    if not LATEST_RECEIPT.is_symlink():
        raise RuntimeError("latest receipt is not a symlink")
    resolved = LATEST_RECEIPT.resolve(strict=True)
    if (
        resolved.parent.parent != RELEASE_ROOT
        or not RUN_PATTERN.fullmatch(resolved.parent.name)
        or resolved.name not in {
            "deployment-readiness-receipt.json",
            "deployment-receipt.json",
            "rollback-receipt.json",
            "rollback-verification-receipt.json",
            "interrupted-apply-recovery-receipt.json",
        }
    ):
        raise RuntimeError("latest receipt target escaped its lifecycle")
    validate_regular_file(
        resolved, resolved.parent, modes=(0o600,)
    )
    receipt = read_json(resolved)
    if (
        receipt.get("releaseId") != resolved.parent.name
        or not COMMIT_PATTERN.fullmatch(
            str(receipt.get("sourceCommit") or "")
        )
    ):
        raise RuntimeError("latest receipt target identity is invalid")
    return resolved


def advance_latest_receipt(target, allowed_predecessors=()):
    target = pathlib.Path(target)
    validate_regular_file(target, target.parent, modes=(0o600,))
    target = target.resolve(strict=True)
    current = validated_latest_receipt_target()
    if current == target:
        return False
    allowed = {
        pathlib.Path(value)
        for value in allowed_predecessors
        if value is not None
    }
    if current is not None and current not in allowed:
        raise RuntimeError(
            "latest receipt points to another lifecycle transition"
        )
    atomic_symlink(target, LATEST_RECEIPT)
    return True


def read_json(path):
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))


def file_anchor_matches(path, expected):
    path = pathlib.Path(path)
    try:
        resolved = path.resolve(strict=True)
        status = resolved.stat()
        return bool(
            resolved.is_file()
            and not resolved.is_symlink()
            and status.st_uid == 0
            and status.st_gid == 0
            and status.st_nlink == 1
            and status.st_mode & 0o777 == 0o600
            and sha256_file(resolved) == expected
        )
    except OSError:
        return False


def parse_environment(*, allow_undersized_token_secret=False):
    validate_regular_file(ENV_PATH, ENV_PATH.parent, modes=(0o600,))
    values = {}
    for number, raw in enumerate(
        ENV_PATH.read_text(encoding="utf-8").splitlines(), 1
    ):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            raise RuntimeError(
                "invalid environment assignment at line {}".format(number)
            )
        name, value = line.split("=", 1)
        name = name.strip()
        value = value.strip()
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
            raise RuntimeError("invalid environment key")
        if name in values:
            raise RuntimeError("duplicate environment key")
        if (
            len(value) >= 2
            and value[0] == value[-1]
            and value[0] in "\"'"
        ):
            value = value[1:-1]
        values[name] = value
    if any(values.get(name) != "false" for name in FALSE_FLAGS):
        raise RuntimeError("all W1A flags must remain explicitly false")
    engine_credential = str(
        values.get(ADMIN_ENGINE_TOKEN_NAME, "")
    ).strip()
    if re.fullmatch(
        r"[A-Za-z0-9_-]{43,128}", engine_credential
    ) is None:
        raise RuntimeError(
            "admin Engine credential shape is invalid"
        )
    engine_material = engine_credential.encode("utf-8")
    for name in (
        EVENT_KEY_NAME,
        PREVIOUS_EVENT_KEY_NAME,
        SAME_BINDING_KEY_NAME,
        "FBSIR_TOKEN_SECRET",
        "WXFBSIR_TOKEN_SECRET",
    ):
        comparison = decode_secret_material(values.get(name))
        if (
            comparison is not None
            and hmac.compare_digest(engine_material, comparison)
        ):
            raise RuntimeError(
                "admin Engine credential is not independent"
            )
    token_secret = str(values.get(TOKEN_SECRET_NAME, "")).strip()
    token_secret_valid = re.fullmatch(
        r"[A-Za-z0-9_-]{43,128}",
        token_secret,
    ) is not None
    legacy_undersized_token_secret = bool(
        allow_undersized_token_secret
        and 0 < len(token_secret) < 32
    )
    if not token_secret_valid and not legacy_undersized_token_secret:
        raise RuntimeError("token secret shape is invalid")
    token_material = token_secret.encode("utf-8")
    for name in (
        ADMIN_ENGINE_TOKEN_NAME,
        EVENT_KEY_NAME,
        PREVIOUS_EVENT_KEY_NAME,
        SAME_BINDING_KEY_NAME,
    ):
        comparison = (
            str(values.get(name, "")).strip().encode("utf-8")
            if name == ADMIN_ENGINE_TOKEN_NAME
            else decode_secret_material(values.get(name))
        )
        if (
            comparison is not None
            and hmac.compare_digest(token_material, comparison)
        ):
            raise RuntimeError("token secret is not independent")
    required = DATABASE_ENVIRONMENT_NAMES
    if any(not values.get(name) for name in required):
        raise RuntimeError("application database credentials are absent")
    aliases = DATABASE_ENVIRONMENT_ALIAS_NAMES
    alias_values = tuple(values.get(name) for name in aliases)
    if any(alias_values) and (
        not all(alias_values)
        or alias_values
        != tuple(values[name] for name in required)
    ):
        raise RuntimeError("database credential aliases drifted")
    match = re.fullmatch(
        r"jdbc:mysql://([^/:?]+)(?::([0-9]{1,5}))?/([^?]+)(?:\?.*)?",
        values["WXFBSIR_MYSQL_URL"],
    )
    if not match or match.group(3) != DATABASE:
        raise RuntimeError("database URL is not the fixed fbsir target")
    return values, {
        "host": match.group(1),
        "port": int(match.group(2) or "3306"),
        "user": values["WXFBSIR_MYSQL_USERNAME"],
        "password": values["WXFBSIR_MYSQL_PASSWORD"],
    }


def environment_flags_explicit_false():
    try:
        validate_regular_file(ENV_PATH, ENV_PATH.parent, modes=(0o600,))
        values = {}
        for raw in ENV_PATH.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            if line.startswith("export "):
                line = line[7:].lstrip()
            name, value = line.split("=", 1)
            name = name.strip()
            value = value.strip()
            if (
                len(value) >= 2
                and value[0] == value[-1]
                and value[0] in "\"'"
            ):
                value = value[1:-1]
            if name in values:
                return False
            values[name] = value
        return all(values.get(name) == "false" for name in FALSE_FLAGS)
    except (OSError, UnicodeError, RuntimeError, ValueError):
        return None


class Mysql:
    def __init__(self, connection):
        try:
            import pymysql
        except ImportError as error:
            raise RuntimeError("PyMySQL is required on the target") from error
        self.connection = pymysql.connect(
            host=connection["host"],
            port=connection["port"],
            user=connection["user"],
            password=connection["password"],
            database=DATABASE,
            charset="utf8mb4",
            autocommit=True,
            connect_timeout=10,
            read_timeout=120,
            write_timeout=120,
        )

    def close(self):
        self.connection.close()

    def execute(self, sql, args=None):
        with self.connection.cursor() as cursor:
            cursor.execute(sql, args)
            return cursor.rowcount

    def execute_script(self, statements):
        """Execute every parsed statement on this exact locked connection."""
        with self.connection.cursor() as cursor:
            for statement in statements:
                cursor.execute(statement)
                while cursor.nextset():
                    pass

    def rows(self, sql, args=None):
        with self.connection.cursor() as cursor:
            cursor.execute(sql, args)
            return list(cursor.fetchall())

    def scalar(self, sql, args=None):
        rows = self.rows(sql, args)
        if len(rows) != 1 or len(rows[0]) != 1:
            raise RuntimeError("database scalar query shape is invalid")
        return rows[0][0]


def parse_mysql_script(text):
    """Parse the reviewed mysql-client DELIMITER subset deterministically."""
    if not isinstance(text, str) or "\x00" in text:
        raise RuntimeError("migration SQL text is invalid")
    delimiter = ";"
    buffered = []
    statements = []
    for line in text.splitlines(keepends=True):
        directive = re.fullmatch(
            r"\s*DELIMITER\s+(\S+)\s*(?:\r?\n)?",
            line,
            flags=re.IGNORECASE,
        )
        if directive:
            if "".join(buffered).strip():
                raise RuntimeError(
                    "DELIMITER directive appeared inside a statement"
                )
            delimiter = directive.group(1)
            if (
                len(delimiter) > 16
                or delimiter.startswith("--")
                or any(character.isspace() for character in delimiter)
            ):
                raise RuntimeError("migration DELIMITER is invalid")
            buffered = []
            continue
        if re.match(r"\s*DELIMITER\b", line, flags=re.IGNORECASE):
            raise RuntimeError("migration DELIMITER directive is invalid")
        buffered.append(line)
        candidate = "".join(buffered).rstrip()
        if candidate.endswith(delimiter):
            statement = candidate[:-len(delimiter)].strip()
            if statement:
                statements.append(statement)
            buffered = []
    if "".join(buffered).strip():
        raise RuntimeError("migration SQL has an unterminated statement")
    if not statements:
        raise RuntimeError("migration SQL contains no statements")
    return statements


class MigrationLease:
    """Own the exact MySQL session and named lock through receipt commit."""

    def __init__(
        self,
        mysql,
        connection,
        facts,
        database_changed_this_run=False,
        database_changed_since_stage=False,
    ):
        self.mysql = mysql
        self.connection = connection
        self.facts = facts
        self.database_changed_this_run = database_changed_this_run
        self.database_changed_since_stage = database_changed_since_stage
        self.closed = False

    def close(self):
        if self.closed:
            return
        try:
            self.mysql.scalar(
                "SELECT RELEASE_LOCK('u3w:w1a:public_init_043')"
            )
        except Exception:
            pass
        finally:
            self.mysql.close()
            self.closed = True


def load_additional_yaml_documents(text):
    """Parse with the exact target-side safe loader and reject duplicate keys."""
    try:
        import yaml
    except ImportError as error:
        raise RuntimeError(
            "target PyYAML dependency is absent"
        ) from error
    if getattr(yaml, "__version__", "") != EXPECTED_PYYAML_VERSION:
        raise RuntimeError("target PyYAML version drifted")

    class NoDuplicateSafeLoader(yaml.SafeLoader):
        pass

    def construct_mapping(loader, node, deep=False):
        loader.flatten_mapping(node)
        pairs = []
        seen = set()
        for key_node, value_node in node.value:
            key = loader.construct_object(key_node, deep=deep)
            try:
                duplicate = key in seen
            except TypeError as error:
                raise RuntimeError(
                    "additional config mapping key is not scalar"
                ) from error
            if duplicate:
                raise RuntimeError(
                    "additional config contains a duplicate key"
                )
            seen.add(key)
            pairs.append(
                (key, loader.construct_object(value_node, deep=deep))
            )
        return dict(pairs)

    NoDuplicateSafeLoader.add_constructor(
        yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
        construct_mapping,
    )
    try:
        return list(yaml.load_all(text, Loader=NoDuplicateSafeLoader))
    except RuntimeError:
        raise
    except yaml.YAMLError as error:
        raise RuntimeError("additional config YAML is invalid") from error


def flattened_yaml_paths(documents):
    paths = set()
    active = set()

    def walk(value, prefix=()):
        if isinstance(value, dict):
            identity = id(value)
            if identity in active:
                raise RuntimeError(
                    "additional config contains a recursive YAML alias"
                )
            active.add(identity)
            try:
                for key, nested in value.items():
                    if not isinstance(key, str) or not re.fullmatch(
                        r"[A-Za-z0-9][A-Za-z0-9-]*", key
                    ):
                        raise RuntimeError(
                            "additional config key is outside the allowlist"
                        )
                    path = prefix + (key.lower(),)
                    paths.add(".".join(path))
                    walk(nested, path)
            finally:
                active.remove(identity)
        elif isinstance(value, list):
            raise RuntimeError(
                "additional config sequences are outside the allowlist"
            )

    for document in documents:
        if document is None:
            continue
        if not isinstance(document, dict):
            raise RuntimeError(
                "additional config document root must be a mapping"
            )
        walk(document)
    return paths


def validate_additional_config():
    """Accept only the reviewed connector-insights YAML semantic surface."""
    validate_regular_file(
        ADDITIONAL_CONFIG_PATH,
        ADDITIONAL_CONFIG_PATH.parent,
        modes=(0o600, 0o640, 0o644),
    )
    raw = ADDITIONAL_CONFIG_PATH.read_bytes()
    text = raw.decode("utf-8", errors="strict")
    documents = load_additional_yaml_documents(text)
    paths = flattened_yaml_paths(documents)
    if paths != EXPECTED_ADDITIONAL_CONFIG_PATHS:
        raise RuntimeError(
            "additional config semantic surface is outside the allowlist"
        )
    root = documents[0] if len(documents) == 1 else None
    try:
        ingest_enabled = root["fbsir"]["connector-insights"][
            "ingest-enabled"
        ]
    except (KeyError, TypeError) as error:
        raise RuntimeError(
            "additional config allowlisted leaf is absent"
        ) from error
    if not isinstance(ingest_enabled, bool):
        raise RuntimeError(
            "additional config allowlisted leaf must be boolean"
        )
    return sha256_bytes(raw)


def external_config_manifest():
    """Reject every Spring default-search config except the reviewed file."""
    candidates = []
    for entry in ADMIN_ROOT.iterdir():
        if EXTERNAL_CONFIG_NAME_PATTERN.fullmatch(entry.name):
            candidates.append(entry)
    config_root = ADMIN_ROOT / "config"
    if config_root.exists() or config_root.is_symlink():
        if config_root.is_symlink() or not config_root.is_dir():
            raise RuntimeError(
                "Spring default config directory custody is invalid"
            )
        for current, directories, files in os.walk(
            config_root, followlinks=False
        ):
            current_path = pathlib.Path(current)
            current_status = current_path.lstat()
            if (
                not stat.S_ISDIR(current_status.st_mode)
                or current_status.st_uid != 0
                or current_status.st_gid != 0
                or current_status.st_mode & 0o022
            ):
                raise RuntimeError(
                    "Spring default config directory custody is invalid"
                )
            for name in directories:
                if (current_path / name).is_symlink():
                    raise RuntimeError(
                        "Spring default config tree contains a symlink"
                    )
            for name in files:
                if EXTERNAL_CONFIG_NAME_PATTERN.fullmatch(name):
                    candidates.append(current_path / name)
    resolved_allowed = ADDITIONAL_CONFIG_PATH.resolve()
    manifest = []
    seen = set()
    for candidate in candidates:
        validate_regular_file(
            candidate,
            candidate.parent,
            modes=(0o600, 0o640, 0o644),
        )
        resolved = candidate.resolve()
        if resolved != resolved_allowed or resolved in seen:
            raise RuntimeError(
                "unreviewed Spring external config is present"
            )
        seen.add(resolved)
        manifest.append(
            {
                "path": str(candidate),
                "sha256": validate_additional_config(),
                "mode": candidate.stat().st_mode & 0o777,
            }
        )
    if seen != {resolved_allowed}:
        raise RuntimeError(
            "reviewed Spring additional config is absent"
        )
    return sorted(manifest, key=lambda item: item["path"])


def candidate_process_arguments():
    return [
        "/usr/bin/java",
        "-Dspring.config.additional-location=file:"
        + str(ADDITIONAL_CONFIG_PATH),
        "-jar",
        str(CURRENT_LINK / "backend/fbsir-admin.jar"),
    ]


def process_environment(main_pid, require_active):
    values = {}
    if main_pid <= 0:
        if require_active:
            raise RuntimeError("active U3W process environment is absent")
        return values
    path = pathlib.Path("/proc") / str(main_pid) / "environ"
    try:
        status = path.stat()
        raw = path.read_bytes()
        if status.st_uid != 0:
            raise RuntimeError("active U3W process environment has unsafe owner")
        for item in raw.split(b"\0"):
            if not item:
                continue
            name_bytes, separator, value_bytes = item.partition(b"=")
            if not separator:
                raise RuntimeError("active U3W process environment is invalid")
            name = name_bytes.decode("utf-8", errors="strict")
            value = value_bytes.decode("utf-8", errors="strict")
            if name in values:
                raise RuntimeError(
                    "active U3W process environment has duplicate keys"
                )
            values[name] = value
    except (OSError, UnicodeError) as error:
        if require_active:
            raise RuntimeError(
                "active U3W process environment is unreadable"
            ) from error
    return values


def decode_secret_material(encoded):
    value = str(encoded or "").strip()
    if not value:
        return None
    try:
        if value.startswith("base64:"):
            raw = value[7:]
            if len(raw) % 4 == 1:
                return None
            padded = raw + ("=" * (-len(raw) % 4))
            return base64.b64decode(padded, validate=True)
        if value.startswith("hex:"):
            raw = value[4:]
            if (
                not raw
                or len(raw) % 2
                or re.fullmatch(r"[0-9a-fA-F]+", raw) is None
            ):
                return None
            return bytes.fromhex(raw)
        if value.startswith("utf8:"):
            value = value[5:]
        return value.encode("utf-8")
    except (ValueError, UnicodeError):
        return None


def security_configuration_evidence(process_values, expected_values):
    """Bind effective process credentials without serializing their values."""
    event_key_manifest = root_regular_manifest(
        API2_EVENT_KEY_PATH, modes=(0o600,)
    )
    key = API2_EVENT_KEY_PATH.read_bytes()
    if not key:
        raise RuntimeError("API2 event key material is empty")
    configured_event_key = decode_secret_material(
        expected_values.get(EVENT_KEY_NAME)
    )
    if (
        configured_event_key is None
        or not hmac.compare_digest(configured_event_key, key)
    ):
        raise RuntimeError("configured event key does not match API2 material")
    expected_names = sorted(
        name
        for name in PROCESS_SECURITY_ENVIRONMENT_NAMES
        if name in expected_values
    )
    actual_names = sorted(
        name
        for name in PROCESS_SECURITY_ENVIRONMENT_NAMES
        if name in process_values
    )

    def configuration_hmac(values, names):
        payload = canonical_json(
            [[name, values[name]] for name in names]
        ).encode("utf-8")
        return hmac.new(
            key,
            b"fbsir.u3wProcessSecurityConfiguration.v1\0" + payload,
            hashlib.sha256,
        ).hexdigest()

    expected_hmac = configuration_hmac(expected_values, expected_names)
    actual_hmac = (
        configuration_hmac(process_values, actual_names)
        if actual_names
        else None
    )
    expected_database_names = sorted(
        name
        for name in (
            DATABASE_ENVIRONMENT_NAMES
            + DATABASE_ENVIRONMENT_ALIAS_NAMES
        )
        if name in expected_values
    )
    actual_database_names = sorted(
        name
        for name in expected_database_names
        if name in process_values
    )
    expected_database_hmac = configuration_hmac(
        expected_values, expected_database_names
    )
    actual_database_hmac = (
        configuration_hmac(process_values, actual_database_names)
        if actual_database_names else None
    )
    matched = bool(
        actual_database_hmac is not None
        and actual_database_names == expected_database_names
        and hmac.compare_digest(
            actual_database_hmac, expected_database_hmac
        )
        and all(
            name in process_values
            and hmac.compare_digest(
                process_values[name], expected_values[name]
            )
            for name in DATABASE_ENVIRONMENT_NAMES
        )
    )
    configured_names = sorted(expected_values)
    process_configured_names = sorted(
        name for name in configured_names if name in process_values
    )
    configured_hmac = configuration_hmac(
        expected_values, configured_names
    )
    process_configured_hmac = (
        configuration_hmac(process_values, process_configured_names)
        if process_configured_names
        else None
    )
    configured_matched = bool(
        process_configured_hmac is not None
        and process_configured_names == configured_names
        and hmac.compare_digest(
            process_configured_hmac, configured_hmac
        )
    )
    mismatch_names = sorted(
        name
        for name in process_configured_names
        if not hmac.compare_digest(
            process_values[name], expected_values[name]
        )
    )
    pending_names = sorted(
        set(configured_names) - set(process_configured_names)
    )
    configured_flags = {
        name: expected_values.get(name) for name in FALSE_FLAGS
    }
    all_managed_configured = all(
        name in expected_values
        for name in MANAGED_RESTART_ENVIRONMENT_NAMES
    )
    flags_configured_false = all(
        configured_flags[name] == "false" for name in FALSE_FLAGS
    )
    if configured_matched and not pending_names and not mismatch_names:
        load_state = "EXACT_CONFIGURED"
    elif (
        pending_names == sorted(MANAGED_RESTART_ENVIRONMENT_NAMES)
        and not mismatch_names
        and all_managed_configured
        and flags_configured_false
        and matched
    ):
        load_state = "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART"
    elif (
        pending_names == [ADMIN_ENGINE_TOKEN_NAME]
        and not mismatch_names
        and all_managed_configured
        and flags_configured_false
        and matched
    ):
        load_state = "ENGINE_CREDENTIAL_PENDING_RESTART"
    elif (
        not pending_names
        and mismatch_names == [TOKEN_SECRET_NAME]
        and all_managed_configured
        and flags_configured_false
        and matched
    ):
        load_state = "TOKEN_SECRET_ROTATION_PENDING_RESTART"
    else:
        load_state = "INVALID_PARTIAL_OR_DRIFTED"
    pre_stage_compatible = load_state in {
        "EXACT_CONFIGURED",
        "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART",
        "ENGINE_CREDENTIAL_PENDING_RESTART",
        "TOKEN_SECRET_ROTATION_PENDING_RESTART",
    }
    return {
        "api2EventKeyManifest": event_key_manifest,
        "processSecurityConfigurationNames": actual_names,
        "processSecurityConfigurationHmacSha256": actual_hmac,
        "expectedSecurityConfigurationNames": expected_names,
        "expectedSecurityConfigurationHmacSha256": expected_hmac,
        "processDatabaseBindingMatched": matched,
        "configuredEnvironmentSha256": sha256_file(ENV_PATH),
        "configuredEnvironmentNames": configured_names,
        "configuredEnvironmentHmacSha256": configured_hmac,
        "processConfiguredEnvironmentHmacSha256":
            process_configured_hmac,
        "processConfiguredEnvironmentMatched": configured_matched,
        "configuredFlagValues": configured_flags,
        "processConfiguredEnvironmentMismatchNames": mismatch_names,
        "processPendingRestartEnvironmentNames": pending_names,
        "processConfiguredEnvironmentLoadState": load_state,
        "processConfiguredEnvironmentPreStageCompatible":
            pre_stage_compatible,
    }


def unit_fragment_manifest(raw_path):
    path = pathlib.Path(str(raw_path or ""))
    if not path.is_absolute():
        raise RuntimeError("systemd unit fragment path is invalid")
    validate_regular_file(
        path, path.parent, modes=(0o600, 0o640, 0o644)
    )
    status = path.stat()
    return {
        "path": str(path),
        "sha256": sha256_file(path),
        "mode": status.st_mode & 0o777,
        "uid": status.st_uid,
        "gid": status.st_gid,
        "nlink": status.st_nlink,
    }


def root_regular_manifest(path, modes=(0o600, 0o640, 0o644)):
    candidate = pathlib.Path(path)
    if not candidate.is_absolute():
        raise RuntimeError("manifest path is not absolute")
    validate_regular_file(candidate, candidate.parent, modes=modes)
    status = candidate.stat()
    return {
        "path": str(candidate),
        "sha256": sha256_file(candidate),
        "mode": status.st_mode & 0o777,
        "uid": status.st_uid,
        "gid": status.st_gid,
        "nlink": status.st_nlink,
    }


def root_file_custody(path, modes=(0o600, 0o640, 0o644)):
    manifest = root_regular_manifest(path, modes=modes)
    return {
        "path": manifest["path"],
        "uid": manifest["uid"],
        "gid": manifest["gid"],
        "mode": oct(manifest["mode"]),
        "nlink": manifest["nlink"],
    }


def plan_dropin_manifest(raw_paths):
    return sorted(
        [
            root_regular_manifest(path)
            for path in str(raw_paths or "").split()
            if path
        ],
        key=lambda item: item["path"],
    )


def stable_root_regular_manifest(path, modes=(0o600, 0o640, 0o644)):
    candidate = pathlib.Path(path)
    if not candidate.is_absolute():
        raise RuntimeError("stable manifest path is not absolute")
    descriptor = os.open(
        candidate, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    )
    try:
        before = os.fstat(descriptor)
        if (
            not stat.S_ISREG(before.st_mode)
            or before.st_uid != 0
            or before.st_gid != 0
            or before.st_nlink != 1
            or before.st_mode & 0o777 not in modes
        ):
            raise RuntimeError("stable manifest custody is invalid")
        digest = hashlib.sha256()
        size = 0
        while True:
            block = os.read(descriptor, 1024 * 1024)
            if not block:
                break
            digest.update(block)
            size += len(block)
        after = os.fstat(descriptor)
        identity = lambda value: (
            value.st_dev,
            value.st_ino,
            value.st_mode,
            value.st_uid,
            value.st_gid,
            value.st_nlink,
            value.st_size,
            value.st_mtime_ns,
            value.st_ctime_ns,
        )
        if identity(before) != identity(after) or size != after.st_size:
            raise RuntimeError("stable manifest changed while reading")
        return {
            "path": str(candidate),
            "sha256": digest.hexdigest(),
            "mode": after.st_mode & 0o777,
            "uid": after.st_uid,
            "gid": after.st_gid,
            "nlink": after.st_nlink,
            "sizeBytes": size,
        }
    finally:
        os.close(descriptor)


def nginx_dump_bytes():
    result = subprocess.run(
        ["nginx", "-T"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    return result.stdout


def active_nginx_manifest():
    dump_bytes = nginx_dump_bytes()
    dump = dump_bytes.decode("utf-8", errors="strict")
    paths = sorted(set(re.findall(
        r"^# configuration file ([^:]+):$", dump, re.MULTILINE
    )))
    manifests = []
    for path in paths:
        manifests.append(stable_root_regular_manifest(path))
    if not manifests:
        raise RuntimeError("active Nginx configuration manifest is empty")
    if not hmac.compare_digest(dump_bytes, nginx_dump_bytes()):
        raise RuntimeError("active Nginx configuration changed during snapshot")
    return manifests, sha256_bytes(dump_bytes)


def release_root_entry_manifest():
    if not RELEASE_ROOT.exists() and not RELEASE_ROOT.is_symlink():
        return []
    status = RELEASE_ROOT.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o755
    ):
        raise RuntimeError("release root custody is invalid")
    result = []
    for entry in sorted(RELEASE_ROOT.iterdir(), key=lambda item: item.name):
        entry_status = entry.lstat()
        item = {
            "name": entry.name,
            "uid": entry_status.st_uid,
            "gid": entry_status.st_gid,
            "mode": entry_status.st_mode & 0o777,
            "nlink": entry_status.st_nlink,
        }
        if (
            entry_status.st_uid != 0
            or entry_status.st_gid != 0
            or (
                not stat.S_ISLNK(entry_status.st_mode)
                and entry_status.st_mode & 0o022
            )
        ):
            raise RuntimeError("release root entry custody is invalid")
        if stat.S_ISDIR(entry_status.st_mode):
            item["type"] = "directory"
        elif stat.S_ISLNK(entry_status.st_mode):
            if entry != LATEST_RECEIPT:
                raise RuntimeError("unexpected release root symlink")
            resolved = entry.resolve(strict=True)
            if RELEASE_ROOT.resolve(strict=True) not in resolved.parents:
                raise RuntimeError("latest release receipt escaped release root")
            validate_regular_file(
                resolved, resolved.parent, modes=(0o600,)
            )
            item.update({
                "type": "symlink",
                "target": os.readlink(entry),
                "targetSha256": sha256_file(resolved),
            })
        elif stat.S_ISREG(entry_status.st_mode):
            item.update({
                "type": "file",
                "sha256": stable_root_regular_manifest(entry)["sha256"],
            })
        else:
            raise RuntimeError("unsupported release root entry type")
        result.append(item)
    return result


def normalized_environment_files(raw_value):
    """Parse systemd EnvironmentFiles without exposing file contents."""
    value = str(raw_value or "")
    declarations = []
    position = 0
    item_pattern = re.compile(
        r"""\s*
        (?P<token>
            -?
            (?:
                "(?:[^"\\]|\\.)*"
                |
                '(?:[^'\\]|\\.)*'
                |
                [^\s()]+
            )
        )
        (?:\s+\(ignore_errors=(?P<ignore_errors>yes|no)\))?
        """,
        re.VERBOSE,
    )
    while position < len(value):
        if not value[position:].strip():
            break
        match = item_pattern.match(value, position)
        if match is None:
            raise RuntimeError(
                "systemd EnvironmentFiles declaration is invalid"
            )
        token = match.group("token")
        optional_prefix = token.startswith("-")
        if optional_prefix:
            token = token[1:]
        if (
            len(token) >= 2
            and token[0] in ("'", '"')
            and token[-1] == token[0]
        ):
            token = token[1:-1]
        if not token or "\\" in token:
            raise RuntimeError(
                "systemd EnvironmentFiles path is unsupported"
            )
        marker = match.group("ignore_errors")
        marker_optional = marker == "yes"
        if optional_prefix and marker == "no":
            raise RuntimeError(
                "systemd EnvironmentFiles optional marker conflicts"
            )
        declarations.append(
            {
                "path": token,
                "ignoreErrors": (
                    optional_prefix or marker_optional
                ),
            }
        )
        position = match.end()
    if declarations != [{
        "path": ENV_PATH_TEXT,
        "ignoreErrors": False,
    }]:
        raise RuntimeError(
            "fbsir-admin requires exactly one mandatory fixed EnvironmentFile"
        )
    return declarations


def environment_file_manifest(raw_value):
    declarations = normalized_environment_files(raw_value)
    validate_regular_file(ENV_PATH, ENV_PATH.parent, modes=(0o600,))
    status = ENV_PATH.stat()
    return [
        {
            "path": declarations[0]["path"],
            "ignoreErrors": declarations[0]["ignoreErrors"],
            "sha256": sha256_file(ENV_PATH),
            "mode": status.st_mode & 0o777,
            "uid": status.st_uid,
            "gid": status.st_gid,
            "nlink": status.st_nlink,
        }
    ]


def dropin_manifest(raw_paths):
    manifest = []
    for raw_path in str(raw_paths or "").split():
        path = pathlib.Path(raw_path)
        validate_regular_file(
            path, DROPIN_DIRECTORY, modes=(0o600, 0o640, 0o644)
        )
        manifest.append(
            {
                "path": str(path),
                "sha256": sha256_file(path),
                "mode": path.stat().st_mode & 0o777,
            }
        )
    return sorted(manifest, key=lambda item: item["path"])


def service_snapshot(
    require_active=True,
    *,
    allow_undersized_token_secret=False,
):
    fields = (
        "ActiveState,MainPID,User,Group,FragmentPath,DropInPaths,"
        "EnvironmentFiles,ExecStart,WorkingDirectory,InvocationID,"
        "ExecMainStartTimestampMonotonic,NRestarts"
    )
    result = subprocess.run(
        ["systemctl", "show", SERVICE_UNIT, "--property=" + fields],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError("systemctl show failed")
    values = {}
    for line in result.stdout.splitlines():
        key, _, value = line.partition("=")
        values[key] = value
    jar_match = re.search(
        r"(/[A-Za-z0-9._/-]+\.jar)", values.get("ExecStart", "")
    )
    configured_jar = (
        pathlib.Path(jar_match.group(1)) if jar_match else None
    )
    main_pid = int(values.get("MainPID") or "0")
    process_jar = None
    process_arguments = []
    if main_pid > 0:
        cmdline_path = pathlib.Path("/proc") / str(main_pid) / "cmdline"
        cmdline_status = None
        try:
            cmdline_status = cmdline_path.stat()
            raw_cmdline = cmdline_path.read_bytes()
            process_arguments = [
                value.decode("utf-8", errors="strict")
                for value in raw_cmdline.split(b"\0")
                if value
            ]
        except (OSError, UnicodeError) as error:
            if require_active:
                raise RuntimeError(
                    "active U3W process command line is unreadable"
                ) from error
        if (
            cmdline_status is None
            or cmdline_status.st_uid != 0
            or not process_arguments
            or "-jar" not in process_arguments
        ):
            if require_active:
                raise RuntimeError(
                    "active U3W process command line is invalid"
                )
        else:
            jar_index = process_arguments.index("-jar") + 1
            for argument in process_arguments[jar_index:]:
                if argument.startswith("/") and argument.endswith(".jar"):
                    process_jar = pathlib.Path(argument)
                    break
    process_values = process_environment(main_pid, require_active)
    expected_environment, _ = parse_environment(
        allow_undersized_token_secret=allow_undersized_token_secret
    )
    security_evidence = security_configuration_evidence(
        process_values, expected_environment
    )
    process_flag_values = {
        name: process_values.get(name) for name in FALSE_FLAGS
    }
    process_environment_names = sorted(process_values)
    normalized_flag_names = {
        re.sub(r"[^a-z0-9]", "", name.lower())
        for name in FALSE_FLAGS
    }
    normalized_java_override_names = {
        re.sub(r"[^a-z0-9]", "", name.lower())
        for name in (
            "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS",
            "JDK_JAVA_OPTIONS",
        )
    }
    forbidden_override_names = sorted(
        name
        for name in process_values
        if (
            name in FORBIDDEN_RUNTIME_OVERRIDE_NAMES
            or (
                name not in FALSE_FLAGS
                and re.sub(r"[^a-z0-9]", "", name.lower())
                in normalized_flag_names
            )
            or re.sub(r"[^a-z0-9]", "", name.lower()).startswith(
                "spring"
            )
            or re.sub(r"[^a-z0-9]", "", name.lower())
                in normalized_java_override_names
        )
    )
    manifest = dropin_manifest(values.get("DropInPaths"))
    fragment_manifest = unit_fragment_manifest(values.get("FragmentPath"))
    environment_manifest = environment_file_manifest(
        values.get("EnvironmentFiles")
    )
    external_configs = external_config_manifest()
    additional_config_sha256 = external_configs[0]["sha256"]
    effective_jar = process_jar or configured_jar
    if (
        (require_active and values.get("ActiveState") != "active")
        or values.get("User") not in ("", "root")
        or values.get("Group") not in ("", "root")
        or not configured_jar
        or not configured_jar.is_file()
        or not effective_jar
        or not effective_jar.is_file()
        or (
            require_active
            and (
                not process_jar
                or process_jar.resolve() != configured_jar.resolve()
            )
        )
    ):
        raise RuntimeError("active U3W service identity is invalid")
    snapshot = {
        "activeState": values["ActiveState"],
        "mainPid": main_pid,
        "user": values.get("User") or "root",
        "group": values.get("Group") or "root",
        "fragmentPath": values.get("FragmentPath"),
        "fragmentFileManifest": fragment_manifest,
        "dropInPaths": values.get("DropInPaths"),
        "environmentFiles": values.get("EnvironmentFiles"),
        "environmentFilePaths": [
            item["path"] for item in environment_manifest
        ],
        "environmentFileManifest": environment_manifest,
        "execStart": values.get("ExecStart"),
        "workingDirectory": values.get("WorkingDirectory"),
        "invocationId": values.get("InvocationID", "").lower(),
        "execMainStartTimestampMonotonic": int(
            values.get("ExecMainStartTimestampMonotonic") or "0"
        ),
        "nRestarts": int(values.get("NRestarts") or "0"),
        "configuredJarPath": str(configured_jar),
        "configuredJarSha256": sha256_file(configured_jar),
        "processJarPath": str(process_jar) if process_jar else None,
        "processJarSha256": (
            sha256_file(process_jar) if process_jar else None
        ),
        "processArgvSha256": sha256_bytes(
            ("\0".join(process_arguments) + "\0").encode("utf-8")
        ) if process_arguments else None,
        "processEnvironmentNamesSha256": sha256_bytes(
            ("\n".join(process_environment_names) + "\n").encode("utf-8")
        ) if process_environment_names else None,
        "processFlagValues": process_flag_values,
        "processForbiddenOverrideNames": forbidden_override_names,
        "dropInManifest": manifest,
        "externalConfigManifest": external_configs,
        "additionalConfigSha256": additional_config_sha256,
        "jarPath": str(effective_jar),
        "jarSha256": sha256_file(effective_jar),
    }
    snapshot.update(security_evidence)
    return snapshot


def stage_plan_anchor(prior_anchor):
    if prior_anchor is None:
        return None
    fields = (
        "releaseId",
        "sourceCommit",
        "receiptPath",
        "receiptSha256",
        "receiptSchema",
        "state",
    )
    if (
        not isinstance(prior_anchor, dict)
        or any(field not in prior_anchor for field in fields)
    ):
        raise RuntimeError("prior lifecycle Plan anchor is invalid")
    return {
        field: prior_anchor[field]
        for field in fields
    }


def stage_live_plan_target(
    before_service,
    prior_rollback_anchor,
    prior_recovery_anchor=None,
):
    nginx_manifest, nginx_dump_sha256 = active_nginx_manifest()
    dropins = plan_dropin_manifest(before_service.get("dropInPaths"))
    fragment = before_service["fragmentFileManifest"]
    unit_files = sorted(
        [
            {"path": item["path"], "sha256": item["sha256"]}
            for item in [fragment] + dropins
        ],
        key=lambda item: item["path"],
    )
    rollback_plan_anchor = stage_plan_anchor(prior_rollback_anchor)
    recovery_plan_anchor = stage_plan_anchor(prior_recovery_anchor)
    if (
        rollback_plan_anchor is not None
        and recovery_plan_anchor is not None
    ):
        raise RuntimeError("Stage predecessor anchors are ambiguous")
    topology_state = (
        "EXACT_PRIOR_ROLLBACK_PREDECESSOR"
        if rollback_plan_anchor is not None
        else INTERRUPTED_APPLY_RECOVERY_TOPOLOGY_STATE
        if recovery_plan_anchor is not None
        else "UNTOUCHED_LEGACY"
    )
    topology = {
        "state": topology_state,
        "priorRollbackAnchor": rollback_plan_anchor,
        "priorRecoveryAnchor": recovery_plan_anchor,
    }
    current_exists = CURRENT_LINK.exists() or CURRENT_LINK.is_symlink()
    current_resolved = None
    if CURRENT_LINK.is_symlink():
        current_resolved = str(CURRENT_LINK.resolve(strict=True))
    service = {
        "ActiveState": before_service["activeState"],
        "MainPID": str(before_service["mainPid"]),
        "User": before_service["user"],
        "Group": before_service["group"],
        "FragmentPath": before_service["fragmentPath"],
        "DropInPaths": before_service["dropInPaths"],
        "EnvironmentFiles": before_service["environmentFiles"],
        "ExecStart": before_service["execStart"],
        "WorkingDirectory": before_service["workingDirectory"],
        "InvocationID": before_service["invocationId"],
        "ExecMainStartTimestampMonotonic": str(
            before_service["execMainStartTimestampMonotonic"]
        ),
        "NRestarts": str(before_service["nRestarts"]),
    }
    target = {
        "schema": "fbsir.u3wDefaultOffRemotePlanSnapshot.v1",
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "service": service,
        "unitSha256": fragment["sha256"],
        "fragmentFileManifest": fragment,
        "dropInManifest": dropins,
        "unitFiles": unit_files,
        "environmentCustody": root_file_custody(
            ENV_PATH, modes=(0o600,)
        ),
        "environmentSha256":
            before_service["configuredEnvironmentSha256"],
        "environmentFilePaths":
            before_service["environmentFilePaths"],
        "environmentFileManifest":
            before_service["environmentFileManifest"],
        "additionalConfigCustody":
            root_file_custody(ADDITIONAL_CONFIG_PATH),
        "additionalConfigSha256":
            before_service["additionalConfigSha256"],
        "externalConfigManifest":
            before_service["externalConfigManifest"],
        "activeJarPath": before_service["processJarPath"],
        "activeJarSha256": before_service["processJarSha256"],
        "configuredJarPath": before_service["configuredJarPath"],
        "configuredJarSha256": before_service["configuredJarSha256"],
        "processJarPath": before_service["processJarPath"],
        "processJarSha256": before_service["processJarSha256"],
        "processArgvSha256": before_service["processArgvSha256"],
        "processEnvironmentNamesSha256":
            before_service["processEnvironmentNamesSha256"],
        "processFlagValues": before_service["processFlagValues"],
        "configuredFlagValues": before_service["configuredFlagValues"],
        "processForbiddenOverrideNames":
            before_service["processForbiddenOverrideNames"],
        "api2EventKeyManifest":
            before_service["api2EventKeyManifest"],
        "processSecurityConfigurationNames":
            before_service["processSecurityConfigurationNames"],
        "processSecurityConfigurationHmacSha256":
            before_service[
                "processSecurityConfigurationHmacSha256"
            ],
        "expectedSecurityConfigurationNames":
            before_service["expectedSecurityConfigurationNames"],
        "expectedSecurityConfigurationHmacSha256":
            before_service[
                "expectedSecurityConfigurationHmacSha256"
            ],
        "processDatabaseBindingMatched":
            before_service["processDatabaseBindingMatched"],
        "configuredEnvironmentSha256":
            before_service["configuredEnvironmentSha256"],
        "configuredEnvironmentNames":
            before_service["configuredEnvironmentNames"],
        "configuredEnvironmentHmacSha256":
            before_service["configuredEnvironmentHmacSha256"],
        "processConfiguredEnvironmentHmacSha256":
            before_service[
                "processConfiguredEnvironmentHmacSha256"
            ],
        "processConfiguredEnvironmentMatched":
            before_service["processConfiguredEnvironmentMatched"],
        "processConfiguredEnvironmentMismatchNames":
            before_service[
                "processConfiguredEnvironmentMismatchNames"
            ],
        "processPendingRestartEnvironmentNames":
            before_service[
                "processPendingRestartEnvironmentNames"
            ],
        "processConfiguredEnvironmentLoadState":
            before_service[
                "processConfiguredEnvironmentLoadState"
            ],
        "processConfiguredEnvironmentPreStageCompatible":
            before_service[
                "processConfiguredEnvironmentPreStageCompatible"
            ],
        "nginxConfigs": nginx_manifest,
        "activeNginxManifest": nginx_manifest,
        "nginxDumpSha256": nginx_dump_sha256,
        "releaseRootExists": RELEASE_ROOT.exists(),
        "releaseRootEntryManifest": release_root_entry_manifest(),
        "currentLinkExists": current_exists,
        "currentLinkResolved": current_resolved,
        "currentLifecycleState": (
            rollback_plan_anchor["state"]
            if rollback_plan_anchor is not None
            else recovery_plan_anchor["state"]
            if recovery_plan_anchor is not None
            else "UNTOUCHED_LEGACY"
        ),
        "stageEntryTopology": topology,
        "productionChanged":
            rollback_plan_anchor is not None
            or recovery_plan_anchor is not None,
        "productionChangedByPlan": False,
    }
    if set(target) != PLAN_TARGET_FIELDS:
        raise RuntimeError("live Stage Plan target field set is invalid")
    return target


def stage_owned_release_root_delta_verified(
    args, planned_target, live_target
):
    planned_exists = planned_target.get("releaseRootExists")
    planned_entries = planned_target.get("releaseRootEntryManifest")
    live_entries = live_target.get("releaseRootEntryManifest")
    if (
        type(planned_exists) is not bool
        or not isinstance(planned_entries, list)
        or not isinstance(live_entries, list)
    ):
        return False
    status = RELEASE_ROOT.lstat()
    incoming = incoming_directory(args)
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o755
        or not incoming.is_dir()
        or incoming.is_symlink()
    ):
        return False
    incoming_entries = [
        item
        for item in live_entries
        if isinstance(item, dict) and item.get("name") == incoming.name
    ]
    if (
        len(incoming_entries) != 1
        or incoming_entries[0].get("type") != "directory"
        or incoming_entries[0].get("uid") != 0
        or incoming_entries[0].get("gid") != 0
        or incoming_entries[0].get("mode") != 0o700
    ):
        return False
    retained_entries = [
        item for item in live_entries
        if item.get("name") != incoming.name
    ]
    if planned_exists:
        planned_state = planned_target.get(
            "stageEntryTopology", {}
        ).get("state")
        return bool(
            planned_state in {
                "EXACT_PRIOR_ROLLBACK_PREDECESSOR",
                INTERRUPTED_APPLY_RECOVERY_TOPOLOGY_STATE,
            }
            and live_target.get(
                "stageEntryTopology", {}
            ).get("state") == planned_state
            and retained_entries == planned_entries
        )
    return planned_entries == [] and retained_entries == []


def assert_stage_plan_target_matches(
    args, planned_target, live_target
):
    topology_state = (
        planned_target.get("stageEntryTopology", {}).get("state")
        if isinstance(planned_target, dict) else None
    )
    allowed_load_states = {
        "UNTOUCHED_LEGACY": {
            "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART": {
                "pending":
                    sorted(MANAGED_RESTART_ENVIRONMENT_NAMES),
                "mismatch": [],
            },
        },
        "EXACT_PRIOR_ROLLBACK_PREDECESSOR": {
            "EXACT_CONFIGURED": {
                "pending": [],
                "mismatch": [],
            },
            "ENGINE_CREDENTIAL_PENDING_RESTART": {
                "pending": [ADMIN_ENGINE_TOKEN_NAME],
                "mismatch": [],
            },
            "TOKEN_SECRET_ROTATION_PENDING_RESTART": {
                "pending": [],
                "mismatch": [TOKEN_SECRET_NAME],
            },
        },
        INTERRUPTED_APPLY_RECOVERY_TOPOLOGY_STATE: {
            "EXACT_CONFIGURED": {
                "pending": [],
                "mismatch": [],
            },
            "ENGINE_CREDENTIAL_PENDING_RESTART": {
                "pending": [ADMIN_ENGINE_TOKEN_NAME],
                "mismatch": [],
            },
            "TOKEN_SECRET_ROTATION_PENDING_RESTART": {
                "pending": [],
                "mismatch": [TOKEN_SECRET_NAME],
            },
        },
    }.get(topology_state)
    planned_topology = (
        planned_target.get("stageEntryTopology", {})
        if isinstance(planned_target, dict) else {}
    )
    rollback_anchor = planned_topology.get("priorRollbackAnchor")
    recovery_anchor = planned_topology.get("priorRecoveryAnchor")
    topology_anchor_valid = bool(
        (
            topology_state == "UNTOUCHED_LEGACY"
            and rollback_anchor is None
            and recovery_anchor is None
        )
        or (
            topology_state == "EXACT_PRIOR_ROLLBACK_PREDECESSOR"
            and isinstance(rollback_anchor, dict)
            and recovery_anchor is None
        )
        or (
            topology_state
                == INTERRUPTED_APPLY_RECOVERY_TOPOLOGY_STATE
            and rollback_anchor is None
            and isinstance(recovery_anchor, dict)
        )
    )
    planned_load_state = (
        planned_target.get("processConfiguredEnvironmentLoadState")
        if isinstance(planned_target, dict) else None
    )
    expected_load_delta = (
        allowed_load_states.get(planned_load_state)
        if isinstance(allowed_load_states, dict) else None
    )
    if (
        not isinstance(planned_target, dict)
        or not isinstance(live_target, dict)
        or set(planned_target) != PLAN_TARGET_FIELDS
        or set(live_target) != PLAN_TARGET_FIELDS
        or not topology_anchor_valid
        or planned_target.get("schema")
            != "fbsir.u3wDefaultOffRemotePlanSnapshot.v1"
        or planned_target.get("targetHost") != TARGET_HOST
        or planned_target.get("serviceUnit") != SERVICE_UNIT
        or planned_target.get("productionChangedByPlan") is not False
        or planned_target.get("currentLinkExists") is not False
        or planned_target.get("currentLinkResolved") is not None
        or planned_target.get("processDatabaseBindingMatched") is not True
        or planned_target.get(
            "processConfiguredEnvironmentPreStageCompatible"
        ) is not True
        or planned_target.get(
            "processConfiguredEnvironmentMismatchNames"
        ) != (
            expected_load_delta.get("mismatch")
            if isinstance(expected_load_delta, dict)
            else None
        )
        or expected_load_delta is None
        or planned_target.get(
            "processPendingRestartEnvironmentNames"
        ) != expected_load_delta.get("pending")
        or planned_target.get("processForbiddenOverrideNames") != []
        or any(
            planned_target.get("configuredFlagValues", {}).get(name)
                != "false"
            or planned_target.get("processFlagValues", {}).get(name)
                not in (None, "false")
            for name in FALSE_FLAGS
        )
    ):
        raise RuntimeError("release Plan target contract is invalid")
    planned_comparable = dict(planned_target)
    live_comparable = dict(live_target)
    for field in ("releaseRootExists", "releaseRootEntryManifest"):
        planned_comparable.pop(field)
        live_comparable.pop(field)
    planned_comparable_sha256 = sha256_bytes(
        canonical_json(planned_comparable).encode("utf-8")
    )
    live_comparable_sha256 = sha256_bytes(
        canonical_json(live_comparable).encode("utf-8")
    )
    release_root_delta_verified = (
        stage_owned_release_root_delta_verified(
            args, planned_target, live_target
        )
    )
    if (
        not release_root_delta_verified
        or not hmac.compare_digest(
            planned_comparable_sha256, live_comparable_sha256
        )
    ):
        raise RuntimeError("release Plan target drifted before FinalizeStage")
    return {
        "releasePlanTargetSha256": sha256_bytes(
            canonical_json(planned_target).encode("utf-8")
        ),
        "releasePlanTargetComparableSha256":
            planned_comparable_sha256,
        "finalizeStageLiveTargetComparableSha256":
            live_comparable_sha256,
        "stageOwnedReleaseRootDeltaVerified":
            release_root_delta_verified,
    }


def pre_stage_environment_mismatch_valid(snapshot):
    state = snapshot.get(
        "processConfiguredEnvironmentLoadState"
    )
    mismatch_names = snapshot.get(
        "processConfiguredEnvironmentMismatchNames"
    )
    pending_names = snapshot.get(
        "processPendingRestartEnvironmentNames"
    )
    if state == "TOKEN_SECRET_ROTATION_PENDING_RESTART":
        return bool(
            mismatch_names == [TOKEN_SECRET_NAME]
            and pending_names == []
        )
    return mismatch_names == []


def run_checked(arguments, timeout=120):
    result = subprocess.run(
        arguments,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "{} failed with stderr SHA-256 {}".format(
                arguments[0], sha256_bytes(result.stderr)
            )
        )
    return result


def tree_manifest(root):
    root = pathlib.Path(root).resolve(strict=True)
    if not root.is_dir() or root.is_symlink():
        raise RuntimeError("frontend tree root is invalid")
    lines = []
    total = 0
    count = 0
    for path in sorted(root.rglob("*"), key=lambda item: item.as_posix()):
        relative = path.relative_to(root).as_posix()
        if path.is_symlink():
            raise RuntimeError("frontend tree contains a symlink")
        if path.is_dir():
            continue
        if not path.is_file() or "\n" in relative or relative.startswith("../"):
            raise RuntimeError("frontend tree contains an unsafe entry")
        status = path.stat()
        if status.st_uid != 0 or status.st_gid != 0 or status.st_nlink != 1:
            raise RuntimeError("frontend file custody is invalid")
        lines.append("{}  {}".format(sha256_file(path), relative))
        total += status.st_size
        count += 1
    if count == 0:
        raise RuntimeError("frontend tree is empty")
    manifest = ("\n".join(lines) + "\n").encode("utf-8")
    return {
        "algorithm": "u3w.sorted-posix-tree-sha256.v1",
        "sha256": sha256_bytes(manifest),
        "fileCount": count,
        "totalBytes": total,
        "manifest": manifest,
    }


def extract_frontend_archive(incoming):
    """Extract a regular-file-only tar into the isolated candidate tree."""
    incoming = pathlib.Path(incoming)
    archive_path = validate_regular_file(
        incoming / "evidence/frontend.tar",
        incoming / "evidence",
        modes=(0o600, 0o644),
    )
    frontend = incoming / "frontend"
    safe_directory(frontend, 0o700)
    if any(frontend.iterdir()):
        raise RuntimeError("frontend destination is not empty")
    entries = set()
    total_size = 0
    with tarfile.open(archive_path, mode="r:") as archive:
        members = archive.getmembers()
        if len(members) > 10000:
            raise RuntimeError("frontend archive has too many entries")
        for member in members:
            name = member.name.replace("\\", "/")
            while name.startswith("./"):
                name = name[2:]
            if not name or name == ".":
                continue
            relative = pathlib.PurePosixPath(name)
            if (
                relative.is_absolute()
                or ".." in relative.parts
                or "\n" in name
                or "\x00" in name
                or name in entries
            ):
                raise RuntimeError("frontend archive path is unsafe")
            entries.add(name)
            target = frontend.joinpath(*relative.parts)
            if member.isdir():
                target.mkdir(mode=0o700, parents=True, exist_ok=True)
                continue
            if not member.isfile():
                raise RuntimeError("frontend archive contains a non-file entry")
            if member.size < 0 or member.size > 256 * 1024 * 1024:
                raise RuntimeError("frontend archive member size is invalid")
            total_size += member.size
            if total_size > 1024 * 1024 * 1024:
                raise RuntimeError("frontend archive is too large")
            target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            source = archive.extractfile(member)
            if source is None:
                raise RuntimeError("frontend archive member is unreadable")
            payload = source.read(member.size + 1)
            if len(payload) != member.size:
                raise RuntimeError("frontend archive member length drifted")
            atomic_bytes(target, payload, 0o644)
    if not entries:
        raise RuntimeError("frontend archive is empty")
    return frontend


def make_frontend_public(release):
    """Give Nginx read/traverse access without exposing private evidence."""
    release = pathlib.Path(release)
    frontend = release / "frontend"
    for path in sorted(
        frontend.rglob("*"), key=lambda item: item.as_posix(), reverse=True
    ):
        if path.is_symlink():
            raise RuntimeError("frontend tree contains a symlink")
        if path.is_dir():
            os.chmod(path, 0o755)
        elif path.is_file():
            os.chmod(path, 0o644)
        else:
            raise RuntimeError("frontend tree contains an unsafe entry")
    os.chmod(frontend, 0o755)
    os.chmod(release, 0o755)


def portal_nginx_config(release_directory):
    frontend = release_directory / "frontend"
    return """server {{
    listen 80;
    server_name admin.u3w.com;
    return 301 https://$host$request_uri;
}}

server {{
    listen 443 ssl;
    http2 on;
    server_name admin.u3w.com;
    ssl_certificate /etc/nginx/ssl/admin.u3w.com/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/admin.u3w.com/privkey.pem;
    root {frontend};
    location / {{
        try_files $uri $uri/ /index.html;
    }}
    location /prod-api/ {{
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_pass http://127.0.0.1:8080/;
    }}
}}

server {{
    listen 80;
    server_name me.u3w.com;
    return 301 https://$host$request_uri;
}}

server {{
    listen 443 ssl;
    http2 on;
    server_name me.u3w.com;
    ssl_certificate /etc/nginx/ssl/me.u3w.com/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/me.u3w.com/privkey.pem;
    root {frontend};
    location / {{
        try_files $uri $uri/ /index.html;
    }}
    location /prod-api/ {{
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_pass http://127.0.0.1:8080/;
    }}
}}
""".format(frontend=frontend)


def systemd_dropin():
    return """[Service]
ExecStart=
ExecStart=/usr/bin/java -Dspring.config.additional-location=file:/opt/fbsir/admin/application-connector.yml -jar /opt/fbsir/admin/current/backend/fbsir-admin.jar
WorkingDirectory=/opt/fbsir/admin
"""


def rollback_systemd_dropin(release_directory):
    predecessor = pathlib.PurePosixPath(
        str(release_directory).replace("\\", "/")
    ) / "rollback/previous-admin.jar"
    return """[Service]
ExecStart=
ExecStart=/usr/bin/java -Dspring.config.additional-location=file:/opt/fbsir/admin/application-connector.yml -jar {}
WorkingDirectory=/opt/fbsir/admin
""".format(predecessor)


def release_artifact_manifest(root):
    root = pathlib.Path(root).resolve(strict=True)
    result = {}
    for relative in RELEASE_ARTIFACT_RELATIVE_PATHS:
        path = root.joinpath(*pathlib.PurePosixPath(relative).parts)
        if root not in path.resolve(strict=True).parents:
            raise RuntimeError("release artifact escaped release root")
        manifest = stable_root_regular_manifest(path)
        result[relative] = {
            key: manifest[key]
            for key in (
                "sha256",
                "mode",
                "uid",
                "gid",
                "nlink",
                "sizeBytes",
            )
        }
    return result


def frontend_tree_evidence(root):
    root = pathlib.Path(root)
    first = tree_manifest(root / "frontend")
    second = tree_manifest(root / "frontend")
    comparable_fields = (
        "algorithm",
        "sha256",
        "fileCount",
        "totalBytes",
        "manifest",
    )
    if any(first[field] != second[field] for field in comparable_fields):
        raise RuntimeError("frontend tree changed while reading")
    manifest_path = root / "evidence/frontend-manifest.txt"
    validate_regular_file(
        manifest_path, manifest_path.parent, modes=(0o600,)
    )
    if manifest_path.read_bytes() != first["manifest"]:
        raise RuntimeError("frontend manifest no longer matches the tree")
    return {
        field: first[field]
        for field in (
            "algorithm",
            "sha256",
            "fileCount",
            "totalBytes",
        )
    }


def validate_release_marker(path, args):
    validate_regular_file(path, path.parent, modes=(0o600, 0o644))
    marker = read_json(path)
    if (
        set(marker)
        != {
            "schema",
            "releaseId",
            "sourceCommit",
            "officialExpertsPackageChanged",
        }
        or marker.get("schema") != "fbsir.u3wReleaseMarker.v1"
        or marker.get("releaseId") != args.release_id
        or marker.get("sourceCommit") != args.source_commit
        or marker.get("officialExpertsPackageChanged") is not False
    ):
        raise RuntimeError("frontend release marker identity drifted")
    return marker


def http_json(url, timeout=5):
    class RejectRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(
            self, request, file_pointer, code, message, headers, new_url
        ):
            return None

    request = urllib.request.Request(
        url, headers={"User-Agent": "u3w-release-verifier/1"}
    )
    opener = urllib.request.build_opener(RejectRedirect())
    try:
        with opener.open(request, timeout=timeout) as response:
            payload = response.read(1024 * 1024)
            status = response.status
    except urllib.error.HTTPError as error:
        payload = error.read(1024 * 1024)
        status = error.code
    try:
        parsed = json.loads(payload.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError):
        parsed = None
    return status, parsed


def candidate_portal_http_json(host, endpoint_class):
    paths = {
        "api": "/prod-api/captchaImage",
        "marker": "/w1a-release.json",
    }
    if host not in {"me.u3w.com", "admin.u3w.com"}:
        raise RuntimeError("candidate portal host is invalid")
    if endpoint_class not in paths:
        raise RuntimeError("candidate portal endpoint class is invalid")
    url = "https://{}{}".format(host, paths[endpoint_class])
    last_error = None
    for attempt in range(3):
        try:
            return http_json(url, timeout=10)
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = error
            if attempt < 2:
                time.sleep(2)
    raise RuntimeError(
        "candidate portal transport failed: {} {}".format(
            host, endpoint_class
        )
    ) from last_error


def format_probe_timestamp(value):
    if (
        not isinstance(value, dt.datetime)
        or value.tzinfo is None
    ):
        raise RuntimeError("attribution probe time is invalid")
    return (
        value.astimezone(dt.timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def build_signed_attribution_probe_event(
    environment,
    now=None,
    entropy=None,
):
    if not isinstance(environment, dict):
        raise RuntimeError("attribution probe environment is invalid")
    key_id = str(environment.get(EVENT_KEY_ID_NAME, "")).strip()
    key_material = decode_secret_material(environment.get(EVENT_KEY_NAME))
    if (
        re.fullmatch(
            r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}", key_id
        ) is None
        or key_material is None
        or len(key_material) < 32
    ):
        raise RuntimeError(
            "attribution probe signing key is invalid"
        )
    observed = now or dt.datetime.now(dt.timezone.utc)
    if not isinstance(observed, dt.datetime) or observed.tzinfo is None:
        raise RuntimeError("attribution probe time is invalid")
    observed = observed.astimezone(dt.timezone.utc)
    seed = os.urandom(32) if entropy is None else entropy
    if not isinstance(seed, bytes) or len(seed) < 32:
        raise RuntimeError("attribution probe entropy is invalid")

    def identifier(label):
        return sha256_bytes(
            b"fbsir.u3wDefaultOffSignedProbe.v1\0"
            + label.encode("ascii")
            + b"\0"
            + seed
        )

    nonce = "u3w.default.off.probe." + identifier("nonce")[:32]
    event = {
        "schemaVersion": "fbsir.independentBoardAttributionEvent.v1",
        "eventId": identifier("event"),
        "receiptId": identifier("receipt"),
        "contractId": "FBSIR_INDEPENDENT_BOARD_W1A_V1",
        "eventType": "ENTRY_OBSERVED",
        "sequenceNo": 1,
        "occurredAt": format_probe_timestamp(observed),
        "productId": "fbsir-eight-seat-board",
        "packageId": "fbsir-eight-seat-board",
        "agentName": "board-convener",
        "marketplace": "experts",
        "listedSurface": "listed_runtime_state",
        "listedManifestVersion": "26.7.21",
        "embeddedContractVersion": "26.7.20",
        "hostClientFamily": "WORKBUDDY",
        "hostVersion": "UNKNOWN",
        "terminal": "UNKNOWN",
        "channel": "OFFICIAL_EXPERTS",
        "requestSource": "UNKNOWN",
        "intentSignal": "default_off_probe",
        "classificationSource": "SERVER_CLASSIFIER",
        "classifierVersion": "u3w.default.off.probe.v1",
        "confidenceBucket": "UNKNOWN",
        "reviewMode": "UNKNOWN",
        "journeyId": identifier("journey"),
        "serverBindingId": "srv_" + identifier("binding")[:32],
        "sameBindingKey": "",
        "tenantSubjectDigest": identifier("tenant"),
        "trafficClass": "PROBE",
        "trafficAuthority": "API2_SERVER_CLASSIFIER_V1",
        "outcome": "WITHHELD",
        "previousEventDigest": "",
        "traceparent": "",
        "rawContentStored": False,
        "issuedAt": format_probe_timestamp(observed),
        "expiresAt": format_probe_timestamp(
            observed + dt.timedelta(seconds=60)
        ),
        "nonce": nonce,
        "keyId": key_id,
        "signatureAlgorithm": "hmac-sha256-v1",
    }
    signed_fields = {
        name: str(event[name]).strip()
        for name in ATTRIBUTION_EVENT_SIGNED_STRING_FIELDS
    }
    signed_fields["rawContentStored"] = "false"
    signed_fields["sequenceNo"] = "1"
    signature = hmac.new(
        key_material,
        canonical_json(signed_fields).encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    event["signature"] = "v1=" + signature
    identity = {
        "eventId": event["eventId"],
        "receiptId": event["receiptId"],
        "nonceHash": sha256_bytes(nonce.encode("utf-8")),
        "journeyId": event["journeyId"],
    }
    return event, identity


def post_signed_attribution_probe(event, timeout=10):
    if not isinstance(event, dict):
        raise RuntimeError("signed attribution probe event is invalid")

    class RejectRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(
            self, request, file_pointer, code, message, headers, new_url
        ):
            return None

    url = "http://127.0.0.1:8080" + ATTRIBUTION_INGRESS_PATH
    request = urllib.request.Request(
        url,
        data=canonical_json(event).encode("utf-8"),
        method="POST",
        headers={
            "User-Agent": "u3w-release-signed-disabled-route-verifier/1",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    opener = urllib.request.build_opener(RejectRedirect())
    try:
        with opener.open(request, timeout=timeout) as response:
            response.read(1024 * 1024)
            return response.status
    except urllib.error.HTTPError as error:
        error.read(1024 * 1024)
        return error.code
    except (OSError, TimeoutError, urllib.error.URLError) as error:
        raise RuntimeError(
            "default-off attribution ingress probe failed"
        ) from error


def attribution_probe_identity_counts(mysql, identity):
    if (
        not isinstance(identity, dict)
        or set(identity)
        != {"eventId", "receiptId", "nonceHash", "journeyId"}
        or any(
            not SHA_PATTERN.fullmatch(str(value or ""))
            for value in identity.values()
        )
    ):
        raise RuntimeError("attribution probe identity is invalid")
    queries = (
        (
            "eventId",
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE event_id=%s",
        ),
        (
            "receiptId",
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE receipt_id=%s",
        ),
        (
            "nonceHash",
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE nonce_hash=%s",
        ),
        (
            "journeyId",
            "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
            "WHERE journey_id=%s",
        ),
    )
    return {
        name: int(mysql.scalar(statement, (identity[name],)))
        for name, statement in queries
    }


def attribution_global_counts(facts):
    if (
        not isinstance(facts, dict)
        or type(facts.get("eventCount")) is not int
        or type(facts.get("journeyCount")) is not int
        or facts["eventCount"] < 0
        or facts["journeyCount"] < 0
    ):
        raise RuntimeError("attribution global counts are invalid")
    return {
        "eventCount": facts["eventCount"],
        "journeyCount": facts["journeyCount"],
    }


def disabled_attribution_ingress_probe(
    mysql,
    global_before,
    now=None,
    entropy=None,
):
    environment, _ = parse_environment()
    event, identity = build_signed_attribution_probe_event(
        environment,
        now=now,
        entropy=entropy,
    )
    zero_identity_counts = {
        name: 0 for name in identity
    }
    identity_before = attribution_probe_identity_counts(mysql, identity)
    if identity_before != zero_identity_counts:
        raise RuntimeError(
            "attribution probe identity is not unique before request"
        )
    http_status = post_signed_attribution_probe(event)
    identity_after = attribution_probe_identity_counts(mysql, identity)
    global_after = exact_migration_facts(mysql)
    if http_status != DISABLED_INGRESS_HTTP_STATUS:
        raise RuntimeError(
            "attribution ingress is not disabled at the live service"
        )
    if identity_after != zero_identity_counts:
        raise RuntimeError(
            "attribution probe identity was persisted"
        )
    if global_after != global_before:
        raise RuntimeError(
            "attribution counts changed during signed default-off probe"
        )
    return {
        "schema": "fbsir.u3wSignedDisabledAttributionIngressProbe.v1",
        "path": ATTRIBUTION_INGRESS_PATH,
        "method": "POST",
        "httpStatus": http_status,
        "responseDisposition": "ROUTE_NOT_FOUND",
        "verifiedDisabled": True,
        "acceptedDisabledHttpStatuses": [DISABLED_INGRESS_HTTP_STATUS],
        "trafficClass": event["trafficClass"],
        "signingKeyId": event["keyId"],
        "signatureAlgorithm": event["signatureAlgorithm"],
        "probeIdentity": identity,
        "identityCountsBefore": identity_before,
        "identityCountsAfter": identity_after,
        "globalCountsBefore": attribution_global_counts(global_before),
        "globalCountsAfter": attribution_global_counts(global_after),
        "rawNonceDisclosed": False,
        "rawSignatureDisclosed": False,
        "signingKeyMaterialDisclosed": False,
        "secretsDisclosed": False,
        "observedAt": utc_now(),
    }, global_after


def wait_for_u3w_health(timeout=180):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            last = http_json("http://127.0.0.1:8080/captchaImage")
            if (
                last[0] == 200
                and isinstance(last[1], dict)
                and last[1].get("code") == 200
            ):
                return True
        except (OSError, ValueError):
            pass
        time.sleep(2)
    raise RuntimeError("U3W captcha readiness did not become healthy")


def validate_approval(args):
    if args.approval_sha == "0" * 64 or not args.approval_json_base64:
        raise RuntimeError("release mutation requires approval")
    try:
        raw = base64.b64decode(args.approval_json_base64, validate=True)
        approval = json.loads(raw.decode("utf-8-sig"))
    except (ValueError, UnicodeError, json.JSONDecodeError) as error:
        raise RuntimeError("approval receipt cannot be decoded") from error
    fields = {
        "schema",
        "action",
        "targetHost",
        "runId",
        "sourceCommit",
        "approvedAt",
        "expiresAt",
        "authorizedBy",
        "concurrentDdlProhibited",
        "productionFilesystemWrite",
        "productionDatabaseWrite",
        "productionServiceChange",
        "officialExpertsPackageChange",
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
    }
    if set(approval) != fields:
        raise RuntimeError("approval receipt has missing or unknown fields")
    mode = "Stage" if args.mode in ("PrepareStage", "FinalizeStage") else args.mode
    actions = {
        "Stage": "STAGE_W1A_DEFAULT_OFF_RELEASE",
        "Apply": "APPLY_W1A_DEFAULT_OFF_RELEASE",
        "Rollback": "ROLLBACK_W1A_DEFAULT_OFF_RELEASE",
        "Verify": "VERIFY_W1A_DEFAULT_OFF_RELEASE",
    }
    filesystem_write = mode in ("Stage", "Apply", "Rollback")
    database_write = mode == "Apply"
    service_change = mode in ("Apply", "Rollback")
    try:
        approved = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        expires = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
    except (TypeError, ValueError) as error:
        raise RuntimeError("approval time is invalid") from error
    current = dt.datetime.now(dt.timezone.utc)
    expected = {
        "expectedBuildReceiptSha256": args.build_receipt_sha,
        "expectedReleasePlanReceiptSha256": args.plan_receipt_sha,
        "expectedBackupReceiptSha256": args.backup_receipt_sha,
        "expectedLegacyBaselineReceiptSha256": args.baseline_receipt_sha,
        "expectedAdminRootDependencyAdoptionReceiptSha256":
            args.admin_root_dependency_adoption_receipt_sha,
        "expectedConfigurationReceiptSha256": args.configuration_receipt_sha,
        "expectedStageReceiptSha256": args.stage_receipt_sha,
        "expectedDeploymentReceiptSha256": args.deployment_receipt_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
    }
    invalid = (
        sha256_bytes(raw) != args.approval_sha
        or approval["schema"]
        != "fbsir.u3wProductionChangeApprovalReceipt.v1"
        or approval["action"] != actions[mode]
        or approval["targetHost"] != TARGET_HOST
        or approval["runId"] != args.release_id
        or approval["sourceCommit"] != args.source_commit
        or approval["authorizedBy"] != "workspace-user"
        or approval["concurrentDdlProhibited"] is not True
        or approval["productionFilesystemWrite"] is not filesystem_write
        or approval["productionDatabaseWrite"] is not database_write
        or approval["productionServiceChange"] is not service_change
        or approval["officialExpertsPackageChange"] is not False
        or approved.tzinfo is None
        or expires.tzinfo is None
        or approved > current
        or expires <= current
        or expires - approved > dt.timedelta(hours=24)
        or any(approval[key] != value for key, value in expected.items())
    )
    if invalid:
        raise RuntimeError("release approval identity or scope is invalid")
    return approval


def validate_interrupted_apply_recovery_approval(
    args,
    require_current=True,
):
    if args.approval_sha == "0" * 64 or not args.approval_json_base64:
        raise RuntimeError("interrupted Apply recovery requires approval")
    try:
        raw = base64.b64decode(
            args.approval_json_base64, validate=True
        )
        approval = json.loads(raw.decode("utf-8-sig"))
    except (ValueError, UnicodeError, json.JSONDecodeError) as error:
        raise RuntimeError(
            "interrupted Apply recovery approval cannot be decoded"
        ) from error
    if set(approval) != INTERRUPTED_APPLY_RECOVERY_APPROVAL_FIELDS:
        raise RuntimeError(
            "interrupted Apply recovery approval fields are invalid"
        )
    try:
        approved = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        expires = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
    except (AttributeError, TypeError, ValueError) as error:
        raise RuntimeError(
            "interrupted Apply recovery approval time is invalid"
        ) from error
    current = dt.datetime.now(dt.timezone.utc)
    invalid = (
        sha256_bytes(raw) != args.approval_sha
        or approval["schema"]
            != "fbsir.u3wProductionChangeApprovalReceipt.v2"
        or approval["action"]
            != "CANONICALIZE_W1A_INTERRUPTED_APPLY_RECOVERY"
        or approval["targetHost"] != TARGET_HOST
        or approval["runId"] != args.release_id
        or approval["executorSourceCommit"] != args.source_commit
        or approval["targetReleaseId"] != args.target_release_id
        or approval["targetSourceCommit"]
            != args.target_source_commit
        or approval["authorizedBy"] != "workspace-user"
        or approval["concurrentDdlProhibited"] is not True
        or approval["productionFilesystemWrite"] is not True
        or approval["productionDatabaseWrite"] is not False
        or approval["productionServiceChange"] is not False
        or approval["officialExpertsPackageChange"] is not False
        or approval["expectedStageReceiptSha256"]
            != args.stage_receipt_sha
        or approval["expectedApplyFailureReceiptSha256"]
            != args.apply_failure_receipt_sha
        or approval["expectedApplyFailureManifestSha256"]
            != args.apply_failure_manifest_sha
        or approval["requestDigest"]
            != args.recovery_plan_receipt_sha
        or approval["runnerSha256"] != args.runner_sha
        or approval["workerSha256"] != args.worker_sha
        or not re.fullmatch(
            r"[0-9a-f]{32}", str(approval["approvalNonce"])
        )
        or approved.tzinfo is None
        or expires.tzinfo is None
        or approved > current
        or expires <= approved
        or (require_current and expires <= current)
        or expires - approved > dt.timedelta(hours=24)
    )
    if invalid:
        raise RuntimeError(
            "interrupted Apply recovery approval is invalid"
        )
    return approval


def validate_interrupted_apply_recovery_plan(
    args,
    require_current=True,
):
    if not getattr(args, "recovery_plan_json_base64", ""):
        raise RuntimeError("interrupted Apply recovery Plan is absent")
    try:
        raw = base64.b64decode(
            args.recovery_plan_json_base64, validate=True
        )
        plan = json.loads(raw.decode("utf-8-sig"))
    except (ValueError, UnicodeError, json.JSONDecodeError) as error:
        raise RuntimeError(
            "interrupted Apply recovery Plan cannot be decoded"
        ) from error
    try:
        generated = dt.datetime.fromisoformat(
            plan["generatedAt"].replace("Z", "+00:00")
        )
        expires = dt.datetime.fromisoformat(
            plan["expiresAt"].replace("Z", "+00:00")
        )
    except (KeyError, AttributeError, TypeError, ValueError) as error:
        raise RuntimeError(
            "interrupted Apply recovery Plan time is invalid"
        ) from error
    current = dt.datetime.now(dt.timezone.utc)
    invalid = (
        set(plan) != INTERRUPTED_APPLY_RECOVERY_PLAN_FIELDS
        or sha256_bytes(raw) != args.recovery_plan_receipt_sha
        or plan.get("schema")
            != INTERRUPTED_APPLY_RECOVERY_PLAN_SCHEMA
        or plan.get("mode") != "RecoveryPlan"
        or plan.get("state")
            != INTERRUPTED_APPLY_RECOVERY_PLAN_STATE
        or plan.get("targetHost") != TARGET_HOST
        or plan.get("serviceUnit") != SERVICE_UNIT
        or plan.get("recoveryRunId") != args.release_id
        or plan.get("executorSourceCommit") != args.source_commit
        or plan.get("targetReleaseId") != args.target_release_id
        or plan.get("targetSourceCommit")
            != args.target_source_commit
        or plan.get("stageReceiptSha256")
            != args.stage_receipt_sha
        or plan.get("applyFailureReceiptSha256")
            != args.apply_failure_receipt_sha
        or plan.get("applyFailureManifestSha256")
            != args.apply_failure_manifest_sha
        or plan.get("runnerSha256") != args.runner_sha
        or plan.get("workerSha256") != args.worker_sha
        or plan.get("productionFilesystemWrite") is not True
        or plan.get("productionDatabaseWrite") is not False
        or plan.get("productionServiceChange") is not False
        or plan.get("officialExpertsPackageChange") is not False
        or generated.tzinfo is None
        or expires.tzinfo is None
        or generated > current
        or expires <= generated
        or (require_current and expires <= current)
        or expires - generated > dt.timedelta(hours=24)
    )
    if invalid:
        raise RuntimeError(
            "interrupted Apply recovery Plan is invalid"
        )
    return plan, raw


def interrupted_recovery_authorized_commit_time(approval, plan):
    try:
        approved = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        approval_expires = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
        generated = dt.datetime.fromisoformat(
            plan["generatedAt"].replace("Z", "+00:00")
        )
        plan_expires = dt.datetime.fromisoformat(
            plan["expiresAt"].replace("Z", "+00:00")
        )
    except (KeyError, AttributeError, ValueError) as error:
        raise RuntimeError(
            "interrupted recovery authorization time is invalid"
        ) from error
    current = dt.datetime.now(dt.timezone.utc)
    if (
        generated > approved
        or current < approved
        or current < generated
        or current >= approval_expires
        or current >= plan_expires
        or min(approval_expires, plan_expires) - current
            <= dt.timedelta(minutes=1)
    ):
        raise RuntimeError(
            "interrupted recovery authorization expired before commit"
        )
    return (
        current.isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def validate_arguments(args):
    if not RUN_PATTERN.fullmatch(args.release_id):
        raise RuntimeError("release id is invalid")
    if not COMMIT_PATTERN.fullmatch(args.source_commit):
        raise RuntimeError("source commit is invalid")
    if args.mode == "InspectInterruptedApplyRecovery":
        interrupted_apply_target_release(args)
        for name in (
            "runner_sha",
            "worker_sha",
            "stage_receipt_sha",
            "apply_failure_receipt_sha",
        ):
            validate_sha(getattr(args, name), name)
        validate_sha(
            args.apply_failure_manifest_sha,
            "apply_failure_manifest_sha",
            allow_zero=True,
        )
        return
    if args.mode == "CanonicalizeInterruptedApplyRecovery":
        interrupted_apply_target_release(args)
        for name in (
            "approval_sha",
            "runner_sha",
            "worker_sha",
            "stage_receipt_sha",
            "apply_failure_receipt_sha",
            "apply_failure_manifest_sha",
            "recovery_plan_receipt_sha",
        ):
            validate_sha(getattr(args, name), name)
        validate_interrupted_apply_recovery_approval(
            args, require_current=False
        )
        validate_interrupted_apply_recovery_plan(
            args, require_current=False
        )
        return
    for name in (
        "approval_sha",
        "runner_sha",
        "worker_sha",
        "build_receipt_sha",
        "plan_receipt_sha",
        "backup_receipt_sha",
        "baseline_receipt_sha",
        "admin_root_dependency_adoption_receipt_sha",
        "configuration_receipt_sha",
        "backend_sha",
        "frontend_tree_sha",
        "migration_sha",
    ):
        validate_sha(getattr(args, name), name)
    validate_sha(args.stage_receipt_sha, "stage_receipt_sha", allow_zero=True)
    validate_sha(
        args.deployment_receipt_sha,
        "deployment_receipt_sha",
        allow_zero=True,
    )
    validate_approval(args)


def validate_target_plan_time(args, now=None):
    if args.mode not in {"FinalizeStage", "Apply"}:
        return
    root = (
        incoming_directory(args)
        if args.mode == "FinalizeStage"
        else release_directory(args)
    )
    path = validate_regular_file(
        root / "evidence/release-plan.json",
        root / "evidence",
        modes=(0o600, 0o644),
    )
    if sha256_file(path) != args.plan_receipt_sha:
        raise RuntimeError("target release Plan digest drifted")
    plan = read_json(path)
    if (
        plan.get("schema") != "fbsir.u3wDefaultOffReleasePlan.v2"
        or plan.get("releaseId") != args.release_id
        or plan.get("sourceCommit") != args.source_commit
    ):
        raise RuntimeError("target release Plan identity drifted")
    try:
        generated = dt.datetime.fromisoformat(
            str(plan.get("generatedAt") or "").replace("Z", "+00:00")
        )
        expires = dt.datetime.fromisoformat(
            str(plan.get("expiresAt") or "").replace("Z", "+00:00")
        )
    except ValueError as error:
        raise RuntimeError("target release Plan time is invalid") from error
    current = now or dt.datetime.now(dt.timezone.utc)
    if (
        generated.tzinfo is None
        or expires.tzinfo is None
        or current.tzinfo is None
        or generated > current
        or expires <= generated
        or expires - generated > dt.timedelta(hours=24)
    ):
        raise RuntimeError("target release Plan validity is invalid")
    if current >= expires:
        raise RuntimeError("target release Plan expired")


def incoming_directory(args):
    return RELEASE_ROOT / (".incoming-" + args.release_id)


def release_directory(args):
    return RELEASE_ROOT / args.release_id


def validate_preparation_anchors(args):
    checks = (
        (BACKUP_RECEIPT, args.backup_receipt_sha, "backup"),
        (BASELINE_RECEIPT, args.baseline_receipt_sha, "baseline"),
        (
            ADMIN_ROOT_DEPENDENCY_RECEIPT,
            args.admin_root_dependency_adoption_receipt_sha,
            "admin root dependency adoption",
        ),
        (
            CONFIGURATION_RECEIPT,
            args.configuration_receipt_sha,
            "configuration",
        ),
    )
    for path, expected, label in checks:
        if not file_anchor_matches(path, expected):
            raise RuntimeError(label + " receipt anchor drifted")


def prepare_stage(args):
    validate_preparation_anchors(args)
    safe_directory(RELEASE_ROOT, 0o755)
    final = release_directory(args)
    incoming = incoming_directory(args)
    if final.exists() or final.is_symlink():
        receipt = final / "deployment-readiness-receipt.json"
        if (
            receipt.is_file()
            and read_json(receipt).get("state") == "STAGED_FOR_SWITCH"
            and read_json(receipt).get(
                "adminRootDependencyAdoptionReceiptSha256"
            ) == args.admin_root_dependency_adoption_receipt_sha
            and not (final / "deployment-receipt.json").exists()
            and not (final / "rollback-receipt.json").exists()
        ):
            return {
                "schema": WORKER_RESULT_SCHEMA,
                "mode": "PrepareStage",
                "state": "ALREADY_STAGED",
                "releaseId": args.release_id,
                "sourceCommit": args.source_commit,
                "approvalReceiptSha256": args.approval_sha,
                "transitionApprovalReceiptSha256":
                    read_json(receipt).get(
                        "stageApprovalReceiptSha256"
                    ),
                "evidenceReceipts": [],
                "incomingPath": None,
                "productionFilesystemChanged": False,
                "productionDatabaseChanged": False,
                "productionServiceChanged": False,
                "officialExpertsPackageChanged": False,
            }
        raise RuntimeError("release destination already exists")
    if incoming.exists() or incoming.is_symlink():
        status = incoming.lstat()
        if (
            not incoming.is_dir()
            or incoming.is_symlink()
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_mode & 0o022
        ):
            raise RuntimeError("unsafe existing incoming directory")
        shutil.rmtree(incoming)
    safe_directory(incoming, 0o700)
    for relative in ("backend", "frontend", "sql", "evidence", "rollback"):
        safe_directory(incoming / relative, 0o700)
    return {
        "schema": WORKER_RESULT_SCHEMA,
        "mode": "PrepareStage",
        "state": "READY_FOR_UPLOAD",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "evidenceReceipts": [],
        "incomingPath": str(incoming),
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionDatabaseChangedThisRun": False,
        "productionDatabaseChangedSinceStage": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
    }


def jar_attribution_class_count(path):
    with zipfile.ZipFile(path) as archive:
        return sum(
            1
            for name in archive.namelist()
            if (
                "BoardAttribution" in name
                or "BoardIntentClassifier" in name
                or "BoardSameBinding" in name
            )
        )


def migration_structure_matches(facts, public_receipt_count=1):
    return bool(
        isinstance(facts, dict)
        and set(facts) == MIGRATION_FACT_FIELDS
        and facts.get("publicReceiptCount") == public_receipt_count
        and facts.get("internalReceiptCount") == 1
        and facts.get("tableCount") == 2
        and facts.get("triggerCount") == 2
        and facts.get("permissionCount") == 1
        and all(
            type(facts.get(field)) is int and facts[field] >= 0
            for field in MIGRATION_DORMANT_DATA_FIELDS
        )
        and facts.get("probeEventCount") == facts.get("eventCount")
        and facts.get("naturalEventCount") == 0
        and facts.get("nonProbeEventCount") == 0
        and facts.get("authoritativeProductCreditCount") == 0
        and facts.get("probeJourneyCount") == facts.get("journeyCount")
        and facts.get("naturalJourneyCount") == 0
        and facts.get("nonProbeJourneyCount") == 0
        and facts.get("schemaFingerprintSha256")
            == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )


def legacy_migration_structure_matches(facts, public_receipt_count=1):
    return bool(
        isinstance(facts, dict)
        and set(facts) == LEGACY_MIGRATION_FACT_FIELDS
        and facts.get("publicReceiptCount") == public_receipt_count
        and facts.get("internalReceiptCount") == 1
        and facts.get("tableCount") == 2
        and facts.get("triggerCount") == 2
        and facts.get("permissionCount") == 1
        and type(facts.get("eventCount")) is int
        and facts["eventCount"] >= 0
        and type(facts.get("journeyCount")) is int
        and facts["journeyCount"] >= 0
        and facts.get("schemaFingerprintSha256")
            == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )


def recorded_migration_facts_match_current(recorded, current, schema):
    if not migration_structure_matches(current):
        return False
    if not recorded_migration_facts_valid(recorded, schema):
        return False
    return all(
        recorded.get(field) == current.get(field)
        for field in LEGACY_MIGRATION_FACT_FIELDS
    )


def recorded_migration_facts_valid(recorded, schema):
    if schema in {
        LEGACY_ROLLBACK_RECEIPT_SCHEMA,
        LEGACY_ROLLBACK_VERIFICATION_SCHEMA,
        LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
    }:
        return legacy_migration_structure_matches(recorded)
    if schema in {
        ROLLBACK_RECEIPT_SCHEMA,
        ROLLBACK_VERIFICATION_SCHEMA,
        DEPLOYMENT_RECEIPT_SCHEMA,
    }:
        return migration_structure_matches(recorded)
    return False


def recorded_migration_counts_are_monotonic(current, baseline, schema):
    return bool(
        migration_structure_matches(current)
        and recorded_migration_facts_valid(baseline, schema)
        and all(
            baseline.get(field) == current.get(field)
            for field in (
                "publicReceiptCount",
                "internalReceiptCount",
                "tableCount",
                "triggerCount",
                "permissionCount",
                "schemaFingerprintSha256",
            )
        )
        and current["eventCount"] >= baseline["eventCount"]
        and current["journeyCount"] >= baseline["journeyCount"]
    )


def migration_counts_are_monotonic(current, baseline):
    return recorded_migration_counts_are_monotonic(
        current,
        baseline,
        DEPLOYMENT_RECEIPT_SCHEMA,
    )


def recorded_migration_facts_match_runtime_identity(
    recorded,
    identity,
    schema,
):
    if (
        not recorded_migration_facts_valid(recorded, schema)
        or not isinstance(identity, dict)
    ):
        return False
    runtime_legacy = {
        "publicReceiptCount": identity.get("public043ReceiptCount"),
        "internalReceiptCount":
            identity.get("attributionInternalReceiptCount"),
        "tableCount": identity.get("attributionTableCount"),
        "triggerCount": identity.get("attributionTriggerCount"),
        "permissionCount": identity.get("attributionPermissionCount"),
        "eventCount": identity.get("attributionEventCount"),
        "journeyCount": identity.get("attributionJourneyCount"),
        "schemaFingerprintSha256":
            identity.get("w1aSchemaFingerprintSha256"),
    }
    if any(
        recorded.get(field) != runtime_legacy.get(field)
        for field in LEGACY_MIGRATION_FACT_FIELDS
    ):
        return False
    if schema == LEGACY_DEPLOYMENT_RECEIPT_SCHEMA:
        return True
    try:
        return recorded == migration_facts_from_runtime_identity(identity)
    except RuntimeError:
        return False


def deployment_contract_schemas(deployment_schema):
    if deployment_schema == LEGACY_DEPLOYMENT_RECEIPT_SCHEMA:
        return {
            "finalCurrentRead": LEGACY_FINAL_CURRENT_READ_SCHEMA,
            "databaseRollbackSafety":
                LEGACY_DATABASE_ROLLBACK_SAFETY_SCHEMA,
            "applicationRollbackExecution":
                LEGACY_APPLICATION_ROLLBACK_EXECUTION_SCHEMA,
        }
    if deployment_schema == DEPLOYMENT_RECEIPT_SCHEMA:
        return {
            "finalCurrentRead": FINAL_CURRENT_READ_SCHEMA,
            "databaseRollbackSafety": DATABASE_ROLLBACK_SAFETY_SCHEMA,
            "applicationRollbackExecution":
                APPLICATION_ROLLBACK_EXECUTION_SCHEMA,
        }
    raise RuntimeError("deployment receipt schema is unsupported")


def migration_facts_from_runtime_identity(identity):
    if not isinstance(identity, dict):
        raise RuntimeError("runtime migration identity is invalid")
    facts = {
        "publicReceiptCount": identity.get("public043ReceiptCount"),
        "internalReceiptCount":
            identity.get("attributionInternalReceiptCount"),
        "tableCount": identity.get("attributionTableCount"),
        "triggerCount": identity.get("attributionTriggerCount"),
        "permissionCount": identity.get("attributionPermissionCount"),
        "eventCount": identity.get("attributionEventCount"),
        "probeEventCount": identity.get("attributionProbeEventCount"),
        "naturalEventCount":
            identity.get("attributionNaturalEventCount"),
        "nonProbeEventCount":
            identity.get("attributionNonProbeEventCount"),
        "authoritativeProductCreditCount": identity.get(
            "attributionAuthoritativeProductCreditCount"
        ),
        "journeyCount": identity.get("attributionJourneyCount"),
        "probeJourneyCount":
            identity.get("attributionProbeJourneyCount"),
        "naturalJourneyCount":
            identity.get("attributionNaturalJourneyCount"),
        "nonProbeJourneyCount":
            identity.get("attributionNonProbeJourneyCount"),
        "schemaFingerprintSha256":
            identity.get("w1aSchemaFingerprintSha256"),
    }
    if not migration_structure_matches(facts):
        raise RuntimeError("runtime migration structure is not exact")
    return facts


def w1a_database_state(mysql):
    any_public_receipts = int(
        mysql.scalar(
            "SELECT COUNT(*) FROM u3w_schema_migration WHERE version=%s",
            (MIGRATION_PUBLIC_VERSION,),
        )
    )
    internal_receipts = int(
        mysql.scalar(
            "SELECT COUNT(*) FROM u3w_schema_migration "
            "WHERE version=%s AND description=%s",
            (MIGRATION_INTERNAL_VERSION, MIGRATION_INTERNAL_DESCRIPTION),
        )
    )
    table_count = int(
        mysql.scalar(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema=DATABASE() AND table_name IN "
            "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
        )
    )
    trigger_count = int(
        mysql.scalar(
            "SELECT COUNT(*) FROM information_schema.triggers "
            "WHERE trigger_schema=DATABASE() AND event_object_table IN "
            "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
        )
    )
    permission_count = int(
        mysql.scalar(
            "SELECT COUNT(*) FROM sys_menu WHERE "
            "BINARY perms=BINARY 'board:attribution:query'"
        )
    )
    if (
        any_public_receipts == 0
        and internal_receipts == 0
        and table_count == 0
        and trigger_count == 0
        and permission_count == 0
    ):
        return {
            "w1aDatabaseState": "ABSENT",
            "public043AnyReceiptCount": 0,
            "public043ReceiptCount": 0,
            "attributionInternalReceiptCount": 0,
            "attributionTableCount": 0,
            "attributionTriggerCount": 0,
            "attributionPermissionCount": 0,
            "attributionEventCount": 0,
            "attributionProbeEventCount": 0,
            "attributionNaturalEventCount": 0,
            "attributionNonProbeEventCount": 0,
            "attributionAuthoritativeProductCreditCount": 0,
            "attributionJourneyCount": 0,
            "attributionProbeJourneyCount": 0,
            "attributionNaturalJourneyCount": 0,
            "attributionNonProbeJourneyCount": 0,
            "w1aSchemaFingerprintSha256": None,
        }
    if table_count == 2:
        exact = exact_migration_facts(mysql)
        if (
            any_public_receipts == 1
            and migration_structure_matches(exact)
        ):
            return {
                "w1aDatabaseState": "EXACT_043_RETAINED_DORMANT",
                "public043AnyReceiptCount": any_public_receipts,
                "public043ReceiptCount": exact["publicReceiptCount"],
                "attributionInternalReceiptCount":
                    exact["internalReceiptCount"],
                "attributionTableCount": exact["tableCount"],
                "attributionTriggerCount": exact["triggerCount"],
                "attributionPermissionCount": exact["permissionCount"],
                "attributionEventCount": exact["eventCount"],
                "attributionProbeEventCount":
                    exact["probeEventCount"],
                "attributionNaturalEventCount":
                    exact["naturalEventCount"],
                "attributionNonProbeEventCount":
                    exact["nonProbeEventCount"],
                "attributionAuthoritativeProductCreditCount":
                    exact["authoritativeProductCreditCount"],
                "attributionJourneyCount": exact["journeyCount"],
                "attributionProbeJourneyCount":
                    exact["probeJourneyCount"],
                "attributionNaturalJourneyCount":
                    exact["naturalJourneyCount"],
                "attributionNonProbeJourneyCount":
                    exact["nonProbeJourneyCount"],
                "w1aSchemaFingerprintSha256":
                    exact["schemaFingerprintSha256"],
            }
    raise RuntimeError("W1A database state is partial or drifted")


def exact_admin_engine_delta_predecessor_sha256(path):
    raw = pathlib.Path(path).read_bytes()
    try:
        current = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise RuntimeError(
            "admin Engine credential environment is not UTF-8"
        ) from error
    match = re.search(
        r"(?:^|\n)FBSIR_ENGINE_TOKEN="
        r"(?P<credential>[A-Za-z0-9_-]{43,128})\n\Z",
        current,
    )
    if match is None:
        raise RuntimeError(
            "admin Engine credential is not one canonical append-only delta"
        )
    predecessor = current[: match.start()]
    if match.start() > 0:
        predecessor += "\n"
    return sha256_bytes(predecessor.encode("utf-8"))


def exact_token_secret_rotation_matches(before_path, after_path):
    try:
        before_lines = pathlib.Path(before_path).read_text(
            encoding="utf-8"
        ).splitlines(keepends=True)
        after_lines = pathlib.Path(after_path).read_text(
            encoding="utf-8"
        ).splitlines(keepends=True)
    except UnicodeDecodeError:
        return False
    if len(before_lines) != len(after_lines):
        return False
    changed = [
        index
        for index, pair in enumerate(zip(before_lines, after_lines))
        if pair[0] != pair[1]
    ]
    if len(changed) != 1:
        return False
    before_line = before_lines[changed[0]].rstrip("\r\n")
    after_line = after_lines[changed[0]].rstrip("\r\n")
    before_match = re.fullmatch(
        re.escape(TOKEN_SECRET_NAME) + r"=(?P<secret>[^\r\n]+)",
        before_line,
    )
    after_match = re.fullmatch(
        re.escape(TOKEN_SECRET_NAME)
        + r"=(?P<secret>[A-Za-z0-9_-]{43,128})",
        after_line,
    )
    return bool(
        before_match is not None
        and after_match is not None
        and 0 < len(before_match.group("secret")) < 32
        and not hmac.compare_digest(
            before_match.group("secret"),
            after_match.group("secret"),
        )
    )


def configuration_predecessor_receipt(predecessor_sha):
    if (
        not SHA_PATTERN.fullmatch(str(predecessor_sha or ""))
        or predecessor_sha == "0" * 64
    ):
        raise RuntimeError(
            "configuration predecessor receipt digest is invalid"
        )
    root = ADMIN_ROOT / "configuration/w1a"
    matches = []
    for run_directory in root.iterdir():
        if (
            run_directory.is_symlink()
            or not run_directory.is_dir()
            or not re.fullmatch(
                r"w1a-config-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}",
                run_directory.name,
            )
        ):
            continue
        candidate = run_directory / "configuration-receipt.json"
        if candidate.is_file() and not candidate.is_symlink():
            try:
                validate_regular_file(
                    candidate,
                    candidate.parent,
                    modes=(0o600,),
                )
            except (OSError, RuntimeError):
                continue
            if sha256_file(candidate) == predecessor_sha:
                matches.append(candidate)
    if len(matches) != 1:
        raise RuntimeError(
            "configuration predecessor receipt is absent or ambiguous"
        )
    return read_json(matches[0])


def validate_configuration_runtime_anchor(configuration):
    schema = configuration.get("schema")
    if (
        schema
        not in {
            "fbsir.u3wDefaultOffConfigurationReceipt.v2",
            "fbsir.u3wDefaultOffConfigurationReceipt.v3",
            "fbsir.u3wDefaultOffConfigurationReceipt.v4",
        }
        or configuration.get("environmentAfterSha256")
        != sha256_file(ENV_PATH)
        or configuration.get("api2EventKeyPath")
        != str(API2_EVENT_KEY_PATH)
        or configuration.get("stagedKeyMaterialMatched") is not True
        or configuration.get("serviceRestarted") is not False
        or configuration.get("productionServiceChanged") is not False
        or configuration.get("officialExpertsPackageChanged") is not False
        or configuration.get("secretsDisclosed") is not False
    ):
        raise RuntimeError("configuration runtime anchor is invalid")
    if schema == "fbsir.u3wDefaultOffConfigurationReceipt.v2":
        return configuration
    evidence = configuration.get("configurationEvidence")
    predecessor_sha = configuration.get(
        "predecessorConfigurationReceiptSha256"
    )
    credential_state = configuration.get(
        "adminEngineCredentialProvisioningState"
    )
    def engine_evidence_valid(value):
        return bool(
            isinstance(value, dict)
            and value.get("allManagedKeysPresent") is True
            and value.get(
                "allDefaultOffFlagsExplicitFalse"
            ) is True
            and value.get("activeEventKeyPairValid") is True
            and value.get(
                "previousEventKeyPairCompleteAndValid"
            ) is True
            and value.get(
                "sameBindingSecretValidAndIndependent"
            ) is True
            and value.get(
                "api2RawEventKeyMatchesU3wActiveMaterial"
            ) is True
            and value.get("adminEngineCredentialValid") is True
            and value.get(
                "adminEngineCredentialIndependent"
            ) is True
            and int(
                value.get(
                    "adminEngineCredentialMinimumCharacters"
                ) or 0
            ) >= 43
            and value.get("secretsDisclosed") is False
        )

    common_engine_evidence_valid = engine_evidence_valid(evidence)
    if (
        not common_engine_evidence_valid
        or configuration.get(
            "engineCounterpartClosureClaimed"
        ) is not False
        or configuration.get("api2EventKeyProvisioningState")
            != "REUSED_FROM_PREDECESSOR_RECEIPT"
        or not SHA_PATTERN.fullmatch(str(predecessor_sha or ""))
        or predecessor_sha == "0" * 64
    ):
        raise RuntimeError(
            "configuration reconciliation evidence is invalid"
        )
    predecessor = configuration_predecessor_receipt(predecessor_sha)
    if schema == "fbsir.u3wDefaultOffConfigurationReceipt.v3":
        predecessor_environment_matched = (
            configuration.get("environmentBeforeSha256")
            == configuration.get("environmentAfterSha256")
            and predecessor.get("environmentAfterSha256")
            == exact_admin_engine_delta_predecessor_sha256(ENV_PATH)
        )
        if (
            credential_state != "ADOPTED_EXISTING_EXACT_DELTA"
            or predecessor.get("schema")
                != "fbsir.u3wDefaultOffConfigurationReceipt.v2"
            or not predecessor_environment_matched
            or predecessor.get("api2EventKeyPath")
                != str(API2_EVENT_KEY_PATH)
            or predecessor.get("stagedKeyMaterialMatched") is not True
            or predecessor.get(
                "officialExpertsPackageChanged"
            ) is not False
            or predecessor.get("secretsDisclosed") is not False
            or configuration.get(
                "productionConfigurationChanged"
            ) is not False
        ):
            raise RuntimeError(
                "configuration predecessor receipt chain is invalid"
            )
        return configuration
    backup_path = pathlib.Path(
        str(configuration.get("environmentBackupPath") or "")
    )
    try:
        validate_regular_file(
            backup_path,
            backup_path.parent,
            modes=(0o600,),
        )
    except (OSError, RuntimeError) as error:
        raise RuntimeError(
            "token rotation predecessor backup is invalid"
        ) from error
    predecessor_v2_sha = predecessor.get(
        "predecessorConfigurationReceiptSha256"
    )
    predecessor_v2 = configuration_predecessor_receipt(
        predecessor_v2_sha
    )
    if (
        credential_state != "REUSED_FROM_PREDECESSOR_RECEIPT"
        or configuration.get("tokenSecretProvisioningState")
            != "ROTATED_BY_RUN"
        or evidence.get("tokenSecretValid") is not True
        or evidence.get("tokenSecretIndependent") is not True
        or int(
            evidence.get("tokenSecretMinimumCharacters") or 0
        ) < 43
        or configuration.get("productionConfigurationChanged")
            is not True
        or configuration.get("environmentBeforeSha256")
            == configuration.get("environmentAfterSha256")
        or configuration.get("environmentBeforeSha256")
            != predecessor.get("environmentAfterSha256")
        or configuration.get("environmentBackupSha256")
            != configuration.get("environmentBeforeSha256")
        or sha256_file(backup_path)
            != configuration.get("environmentBeforeSha256")
        or not exact_token_secret_rotation_matches(
            backup_path,
            ENV_PATH,
        )
        or predecessor.get("schema")
            != "fbsir.u3wDefaultOffConfigurationReceipt.v3"
        or predecessor.get(
            "adminEngineCredentialProvisioningState"
        ) != "ADOPTED_EXISTING_EXACT_DELTA"
        or not engine_evidence_valid(
            predecessor.get("configurationEvidence")
        )
        or predecessor.get("engineCounterpartClosureClaimed") is not False
        or predecessor.get("productionConfigurationChanged") is not False
        or predecessor.get("api2EventKeyProvisioningState")
            != "REUSED_FROM_PREDECESSOR_RECEIPT"
        or predecessor_v2.get("schema")
            != "fbsir.u3wDefaultOffConfigurationReceipt.v2"
        or predecessor_v2.get("environmentAfterSha256")
            != exact_admin_engine_delta_predecessor_sha256(
                backup_path
            )
        or predecessor.get("environmentBeforeSha256")
            != predecessor.get("environmentAfterSha256")
        or predecessor.get("api2EventKeyPath")
            != str(API2_EVENT_KEY_PATH)
        or predecessor_v2.get("api2EventKeyPath")
            != str(API2_EVENT_KEY_PATH)
        or any(
            receipt.get("stagedKeyMaterialMatched") is not True
            or receipt.get(
                "officialExpertsPackageChanged"
            ) is not False
            or receipt.get("secretsDisclosed") is not False
            for receipt in (predecessor, predecessor_v2)
        )
    ):
        raise RuntimeError(
            "token rotation configuration receipt chain is invalid"
        )
    return configuration


def database_pre_stage_facts():
    _, connection = parse_environment()
    mysql = Mysql(connection)
    try:
        identity_rows = mysql.rows(
            "SELECT LOWER(@@server_uuid),DATABASE(),@@version"
        )
        if len(identity_rows) != 1 or len(identity_rows[0]) != 3:
            raise RuntimeError("database identity query shape is invalid")
        legacy_rows = mysql.rows(
            "SELECT adoption_receipt_sha256 FROM "
            "u3w_legacy_schema_baseline_receipt_v2"
        )
        if len(legacy_rows) != 1 or len(legacy_rows[0]) != 1:
            raise RuntimeError("legacy baseline database anchor is invalid")
        configuration = read_json(CONFIGURATION_RECEIPT)
        validate_regular_file(
            API2_EVENT_KEY_PATH,
            API2_EVENT_KEY_PATH.parent,
            modes=(0o600,),
        )
        validate_configuration_runtime_anchor(configuration)
        facts = {
            "environmentSha256": sha256_file(ENV_PATH),
            "api2EventKeySha256": sha256_file(API2_EVENT_KEY_PATH),
            "additionalConfigSha256": validate_additional_config(),
            "databaseEndpoint": "{}:{}".format(
                connection["host"], connection["port"]
            ),
            "database": identity_rows[0][1],
            "databaseServerUuid": identity_rows[0][0],
            "databaseServerVersion": identity_rows[0][2],
            "legacyBaselineReceiptSha256": legacy_rows[0][0],
            "legacyBaselineMigrationCount": int(
                mysql.scalar(
                    "SELECT COUNT(*) FROM u3w_schema_migration "
                    "WHERE version='legacy_w1a_baseline_20260724_001' "
                    "AND description=CONCAT("
                    "'APPLIED:LEGACY_ADOPTED_W1A_V2:',%s)",
                    (legacy_rows[0][0],),
                )
            ),
        }
        facts.update(w1a_database_state(mysql))
        return facts
    finally:
        mysql.close()


def validate_stage_runtime_identity(args, stage):
    expected = stage.get("preStageRuntimeIdentity")
    if not isinstance(expected, dict):
        raise RuntimeError("staged runtime identity is absent")
    current = database_pre_stage_facts()
    validate_runtime_identity_anchors(args, expected, current)
    expected_state = expected.get("w1aDatabaseState")
    current_state = current.get("w1aDatabaseState")
    if expected_state == "ABSENT":
        retry_after_additive_043 = bool(
            current_state == "EXACT_043_RETAINED_DORMANT"
            and current.get("attributionEventCount") == 0
            and current.get("attributionJourneyCount") == 0
        )
        if current_state != "ABSENT" and not retry_after_additive_043:
            raise RuntimeError(
                "W1A database state drifted from the staged absent prestate"
            )
    elif expected_state == "EXACT_043_RETAINED_DORMANT":
        if (
            current_state != expected_state
            or migration_facts_from_runtime_identity(current)
                != migration_facts_from_runtime_identity(expected)
        ):
            raise RuntimeError(
                "retained 043 ledger counts drifted after Stage"
            )
    else:
        raise RuntimeError("staged W1A database prestate is invalid")
    return current


def validate_runtime_identity_anchors(args, expected, current):
    stable_fields = (
        "environmentSha256",
        "api2EventKeySha256",
        "additionalConfigSha256",
        "databaseEndpoint",
        "database",
        "databaseServerUuid",
        "databaseServerVersion",
        "legacyBaselineReceiptSha256",
        "legacyBaselineMigrationCount",
    )
    if any(current.get(key) != expected.get(key) for key in stable_fields):
        raise RuntimeError("runtime identity drifted after Stage")
    if (
        current["legacyBaselineReceiptSha256"]
        != args.baseline_receipt_sha
        or current["legacyBaselineMigrationCount"] != 1
    ):
        raise RuntimeError("legacy baseline runtime anchor drifted")
    return current


def read_staged_release_plan(args, release):
    plan_path = validate_regular_file(
        release / "evidence/release-plan.json",
        release / "evidence",
        modes=(0o600, 0o644),
    )
    if sha256_file(plan_path) != args.plan_receipt_sha:
        raise RuntimeError("staged release Plan receipt anchor drifted")
    plan = read_json(plan_path)
    target = plan.get("target")
    if (
        plan.get("schema") != "fbsir.u3wDefaultOffReleasePlan.v2"
        or plan.get("sourceCommit") != args.source_commit
        or plan.get("releaseId") != args.release_id
        or plan.get("runnerSha256") != args.runner_sha
        or plan.get("workerSha256") != args.worker_sha
        or not isinstance(target, dict)
        or set(target) != PLAN_TARGET_FIELDS
    ):
        raise RuntimeError("staged release Plan identity drifted")
    return plan, target


def validate_staged_plan_target_binding(args, release, receipt):
    _, target = read_staged_release_plan(args, release)
    target_sha256 = sha256_bytes(
        canonical_json(target).encode("utf-8")
    )
    comparable = dict(target)
    for field in ("releaseRootExists", "releaseRootEntryManifest"):
        comparable.pop(field)
    comparable_sha256 = sha256_bytes(
        canonical_json(comparable).encode("utf-8")
    )
    if (
        receipt.get("releasePlanTargetSha256") != target_sha256
        or receipt.get("releasePlanTargetComparableSha256")
            != comparable_sha256
        or receipt.get("finalizeStageLiveTargetComparableSha256")
            != comparable_sha256
        or receipt.get("stageOwnedReleaseRootDeltaVerified") is not True
    ):
        raise RuntimeError("staged release Plan target binding drifted")
    return {
        "releasePlanTargetSha256": target_sha256,
        "releasePlanTargetComparableSha256": comparable_sha256,
        "finalizeStageLiveTargetComparableSha256":
            comparable_sha256,
        "stageOwnedReleaseRootDeltaVerified": True,
    }


def validate_pre_apply_nginx_target(args, release):
    _, target = read_staged_release_plan(args, release)
    manifest, dump_sha256 = active_nginx_manifest()
    if (
        manifest != target.get("activeNginxManifest")
        or manifest != target.get("nginxConfigs")
        or dump_sha256 != target.get("nginxDumpSha256")
    ):
        raise RuntimeError("active Nginx drifted after Stage")
    return {
        "activeNginxManifest": manifest,
        "nginxDumpSha256": dump_sha256,
    }


def staged_worker_result(args, receipt_path, changed):
    receipt = read_json(receipt_path)
    validate_staged_plan_target_binding(
        args, receipt_path.parent, receipt
    )
    validate_staged_release_artifacts(
        args, receipt_path.parent, receipt
    )
    return {
        "schema": WORKER_RESULT_SCHEMA,
        "mode": "FinalizeStage",
        "state": "STAGED_FOR_SWITCH",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "transitionApprovalReceiptSha256":
            receipt.get("stageApprovalReceiptSha256"),
        "receiptPath": str(receipt_path),
        "receiptSha256": sha256_file(receipt_path),
        "evidenceReceipts": validate_release_evidence(
            args,
            release_directory(args),
            receipt,
            require_execution=False,
        ),
        "productionFilesystemChanged": changed,
        "productionDatabaseChanged": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
    }


def finalize_stage(args):
    validate_preparation_anchors(args)
    incoming = incoming_directory(args)
    final = release_directory(args)
    if final.exists():
        if (
            (final / "deployment-receipt.json").exists()
            or (final / "rollback-receipt.json").exists()
        ):
            raise RuntimeError("release lifecycle already progressed past Stage")
        receipt = final / "deployment-readiness-receipt.json"
        validate_regular_file(receipt, final, modes=(0o600,))
        staged = read_json(receipt)
        digest = sha256_file(receipt)
        expected_stage = args.stage_receipt_sha
        if (
            staged.get("schema")
            != DEPLOYMENT_RECEIPT_SCHEMA
            or staged.get("state") != "STAGED_FOR_SWITCH"
            or staged.get("releaseId") != args.release_id
            or staged.get("sourceCommit") != args.source_commit
            or staged.get("backendBuildSha256") != args.backend_sha
            or staged.get("frontendBuildSha256")
            != args.frontend_tree_sha
            or staged.get("runnerSha256") != args.runner_sha
            or staged.get("workerSha256") != args.worker_sha
            or staged.get("adminRootDependencyAdoptionReceiptSha256")
                != args.admin_root_dependency_adoption_receipt_sha
            or not SHA_PATTERN.fullmatch(
                str(staged.get("stageApprovalReceiptSha256") or "")
            )
            or (
                expected_stage != "0" * 64
                and digest != expected_stage
            )
        ):
            raise RuntimeError("release was staged with a different identity")
        result = staged_worker_result(args, receipt, False)
        assembly = predecessor_facts(final)
        allowed_latest_predecessors = [
            anchor["receiptPath"]
            for anchor in (
                assembly.get("priorRollbackAnchor"),
                assembly.get("priorRecoveryAnchor"),
            )
            if isinstance(anchor, dict)
        ]
        latest_changed = advance_latest_receipt(
            receipt, allowed_latest_predecessors
        )
        if latest_changed:
            result["productionFilesystemChanged"] = True
        return result
    safe_directory(incoming, 0o700)
    backend = validate_regular_file(
        incoming / "backend/fbsir-admin.jar",
        incoming / "backend",
        modes=(0o600, 0o644),
    )
    migration = validate_regular_file(
        incoming / "sql/public_init_043.sql",
        incoming / "sql",
        modes=(0o600, 0o644),
    )
    runner = validate_regular_file(
        incoming / "release-runner.ps1", incoming, modes=(0o600, 0o644)
    )
    worker = validate_regular_file(
        incoming / "release-worker.py", incoming, modes=(0o600, 0o644)
    )
    build_receipt = validate_regular_file(
        incoming / "evidence/build-receipt.json",
        incoming / "evidence",
        modes=(0o600, 0o644),
    )
    plan_receipt = validate_regular_file(
        incoming / "evidence/release-plan.json",
        incoming / "evidence",
        modes=(0o600, 0o644),
    )
    extract_frontend_archive(incoming)
    validate_release_marker(
        incoming / "frontend/w1a-release.json", args
    )
    frontend = tree_manifest(incoming / "frontend")
    actual = {
        "backend": sha256_file(backend),
        "migration": sha256_file(migration),
        "runner": sha256_file(runner),
        "worker": sha256_file(worker),
        "buildReceipt": sha256_file(build_receipt),
        "planReceipt": sha256_file(plan_receipt),
        "frontend": frontend["sha256"],
    }
    expected = {
        "backend": args.backend_sha,
        "migration": args.migration_sha,
        "runner": args.runner_sha,
        "worker": args.worker_sha,
        "buildReceipt": args.build_receipt_sha,
        "planReceipt": args.plan_receipt_sha,
        "frontend": args.frontend_tree_sha,
    }
    if actual != expected:
        raise RuntimeError("uploaded release artifacts drifted")
    build = read_json(build_receipt)
    plan = read_json(plan_receipt)
    if (
        build.get("schema") != "fbsir.u3wDefaultOffBuildReceipt.v1"
        or build.get("sourceCommit") != args.source_commit
        or build.get("releaseId") != args.release_id
        or build.get("runnerSha256") != args.runner_sha
        or build.get("backend", {}).get("sha256") != args.backend_sha
        or build.get("frontend", {}).get("treeSha256")
        != args.frontend_tree_sha
        or build.get("migrationSha256") != args.migration_sha
        or plan.get("schema") != "fbsir.u3wDefaultOffReleasePlan.v2"
        or plan.get("sourceCommit") != args.source_commit
        or plan.get("releaseId") != args.release_id
        or plan.get("buildReceiptSha256") != args.build_receipt_sha
        or plan.get("runnerSha256") != args.runner_sha
        or plan.get("workerSha256") != args.worker_sha
        or not SHA_PATTERN.fullmatch(
            str(plan.get("collectorSha256") or "")
        )
        or plan.get("productionChanged") is not False
        or not isinstance(plan.get("target"), dict)
    ):
        raise RuntimeError("build or plan receipt identity drifted")
    atomic_bytes(
        incoming / "evidence/frontend-manifest.txt",
        frontend["manifest"],
        0o600,
    )
    before_service = service_snapshot()
    prior_rollback_anchor = None
    prior_recovery_anchor = None
    if DROPIN_PATH.exists() or DROPIN_PATH.is_symlink():
        latest_stage_entry = validated_latest_receipt_target()
        if (
            latest_stage_entry is not None
            and latest_stage_entry.name
                == "interrupted-apply-recovery-receipt.json"
        ):
            prior_recovery_anchor = (
                validate_prior_interrupted_recovery_stage_entry(
                    before_service
                )
            )
        else:
            prior_rollback_anchor = validate_prior_rollback_stage_entry(
                before_service
            )
    if (
        CURRENT_LINK.exists()
        or CURRENT_LINK.is_symlink()
        or (
            (DROPIN_PATH.exists() or DROPIN_PATH.is_symlink())
            and prior_rollback_anchor is None
            and prior_recovery_anchor is None
        )
        or before_service.get("processForbiddenOverrideNames") != []
        or before_service.get("processDatabaseBindingMatched") is not True
        or before_service.get(
            "processConfiguredEnvironmentPreStageCompatible"
        ) is not True
        or not pre_stage_environment_mismatch_valid(before_service)
        or before_service.get("environmentFilePaths")
            != [str(ENV_PATH)]
        or len(before_service.get("environmentFileManifest", [])) != 1
        or any(
            before_service.get("configuredFlagValues", {}).get(name)
                != "false"
            or before_service.get("processFlagValues", {}).get(name)
                not in (None, "false")
            for name in FALSE_FLAGS
        )
        or environment_flags_explicit_false() is not True
    ):
        raise RuntimeError(
            "Stage requires an untouched, rollback or interrupted "
            "recovery predecessor topology"
        )
    live_plan_target = stage_live_plan_target(
        before_service,
        prior_rollback_anchor,
        prior_recovery_anchor,
    )
    plan_target_binding = assert_stage_plan_target_matches(
        args, plan["target"], live_plan_target
    )
    if jar_attribution_class_count(before_service["jarPath"]) != 0:
        raise RuntimeError("legacy rollback JAR unexpectedly contains W1A classes")
    shutil.copy2(
        before_service["jarPath"], incoming / "rollback/previous-admin.jar"
    )
    os.chown(incoming / "rollback/previous-admin.jar", 0, 0)
    os.chmod(incoming / "rollback/previous-admin.jar", 0o600)
    validate_regular_file(NGINX_PATH, NGINX_PATH.parent, modes=(0o600, 0o644))
    shutil.copy2(NGINX_PATH, incoming / "rollback/placeholder-sites.conf")
    os.chown(incoming / "rollback/placeholder-sites.conf", 0, 0)
    os.chmod(incoming / "rollback/placeholder-sites.conf", 0o600)
    nginx_status = NGINX_PATH.stat()
    pre_database = database_pre_stage_facts()
    if (
        pre_database["w1aDatabaseState"] not in {
            "ABSENT",
            "EXACT_043_RETAINED_DORMANT",
        }
        or pre_database["legacyBaselineReceiptSha256"]
            != args.baseline_receipt_sha
        or pre_database["legacyBaselineMigrationCount"] != 1
        or before_service.get("configuredEnvironmentSha256")
            != pre_database.get("environmentSha256")
    ):
        raise RuntimeError("Stage database identity or W1A prestate drifted")
    rehearsal_root = incoming / "evidence/application-rehearsal"
    safe_directory(rehearsal_root, 0o700)
    rehearsal_link = rehearsal_root / "current"
    atomic_symlink(incoming, rehearsal_link)
    if rehearsal_link.resolve() != incoming.resolve():
        raise RuntimeError("candidate symlink rehearsal failed")
    atomic_symlink(incoming / "rollback", rehearsal_link)
    if rehearsal_link.resolve() != (incoming / "rollback").resolve():
        raise RuntimeError("rollback symlink rehearsal failed")
    rehearsal_link.unlink()
    application_rollback = {
        "schema": "fbsir.u3wApplicationRollbackAssemblyReceipt.v1",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "stageApprovalReceiptSha256": args.approval_sha,
        "assemblyVerified": True,
        "applicationRollbackProven": False,
        "strategy": (
            "IMMUTABLE_PREDECESSOR_DROPIN_RESTORE_NGINX_AND_RESTART"
        ),
        "previousJarPath": before_service["jarPath"],
        "previousJarSha256": before_service["jarSha256"],
        "immutablePreviousJarPath": str(
            final / "rollback/previous-admin.jar"
        ),
        "serviceSnapshotBeforeStage": before_service,
        "preStageApplicationState": (
            "EXACT_PRIOR_ROLLBACK_PREDECESSOR"
            if prior_rollback_anchor is not None
            else INTERRUPTED_APPLY_RECOVERY_TOPOLOGY_STATE
            if prior_recovery_anchor is not None
            else "UNTOUCHED_LEGACY"
        ),
        "priorRollbackAnchor": prior_rollback_anchor,
        "priorRecoveryAnchor": prior_recovery_anchor,
        **plan_target_binding,
        "previousNginxPath": str(NGINX_PATH),
        "previousNginxSha256": sha256_file(NGINX_PATH),
        "previousNginxMode": nginx_status.st_mode & 0o777,
        "previousNginxUid": nginx_status.st_uid,
        "previousNginxGid": nginx_status.st_gid,
        "symlinkForwardAndReverseVerified": True,
        "productionServiceChanged": False,
        "productionDatabaseChanged": False,
        "observedAt": utc_now(),
    }
    application_path = (
        incoming / "evidence/application-rollback-assembly.json"
    )
    atomic_json(application_path, application_rollback)
    database_safety = {
        "schema": DATABASE_ROLLBACK_SAFETY_SCHEMA,
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "stageApprovalReceiptSha256": args.approval_sha,
        "verified": True,
        "strategy": "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
        "migrationSha256": args.migration_sha,
        "preDeploymentDatabaseState":
            pre_database["w1aDatabaseState"],
        "preDeploymentPublic043Absent": (
            pre_database["w1aDatabaseState"] == "ABSENT"
        ),
        "preDeploymentAttributionTablesAbsent": (
            pre_database["w1aDatabaseState"] == "ABSENT"
        ),
        "preDeploymentRetainedMigrationFacts": (
            {
                "publicReceiptCount":
                    pre_database["public043ReceiptCount"],
                "internalReceiptCount":
                    pre_database["attributionInternalReceiptCount"],
                "tableCount": pre_database["attributionTableCount"],
                "triggerCount": pre_database["attributionTriggerCount"],
                "permissionCount":
                    pre_database["attributionPermissionCount"],
                "eventCount": pre_database["attributionEventCount"],
                "probeEventCount":
                    pre_database["attributionProbeEventCount"],
                "naturalEventCount":
                    pre_database["attributionNaturalEventCount"],
                "nonProbeEventCount":
                    pre_database["attributionNonProbeEventCount"],
                "authoritativeProductCreditCount":
                    pre_database[
                        "attributionAuthoritativeProductCreditCount"
                    ],
                "journeyCount": pre_database["attributionJourneyCount"],
                "probeJourneyCount":
                    pre_database["attributionProbeJourneyCount"],
                "naturalJourneyCount":
                    pre_database["attributionNaturalJourneyCount"],
                "nonProbeJourneyCount":
                    pre_database["attributionNonProbeJourneyCount"],
                "schemaFingerprintSha256":
                    pre_database["w1aSchemaFingerprintSha256"],
            }
            if pre_database["w1aDatabaseState"]
                == "EXACT_043_RETAINED_DORMANT"
            else None
        ),
        "legacyJarAttributionClassCount": 0,
        "allW1aFlagsExplicitFalse": True,
        "databaseDownClaimed": False,
        "productionDatabaseChanged": False,
        "observedAt": utc_now(),
    }
    database_path = (
        incoming / "evidence/database-rollback-safety.json"
    )
    atomic_json(database_path, database_safety)
    candidate_nginx = portal_nginx_config(final).encode("utf-8")
    atomic_bytes(
        incoming / "evidence/u3w-portal-sites.conf",
        candidate_nginx,
        0o600,
    )
    atomic_bytes(
        incoming / "evidence/systemd-dropin.conf",
        systemd_dropin().encode("utf-8"),
        0o600,
    )
    atomic_bytes(
        incoming / "evidence/rollback-systemd-dropin.conf",
        rollback_systemd_dropin(final).encode("utf-8"),
        0o600,
    )
    final_backend = final / "backend/fbsir-admin.jar"
    final_manifest = final / "evidence/frontend-manifest.txt"
    final_runner = final / "release-runner.ps1"
    final_application = (
        final / "evidence/application-rollback-assembly.json"
    )
    final_database = final / "evidence/database-rollback-safety.json"
    frontend_tree_receipt = frontend_tree_evidence(incoming)
    artifact_manifest = release_artifact_manifest(incoming)
    receipt = {
        "schema": DEPLOYMENT_RECEIPT_SCHEMA,
        "state": "STAGED_FOR_SWITCH",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "stageApprovalReceiptSha256": args.approval_sha,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "database": DATABASE,
        "buildReceiptSha256": args.build_receipt_sha,
        "releasePlanReceiptSha256": args.plan_receipt_sha,
        **plan_target_binding,
        "backupReceiptSha256": args.backup_receipt_sha,
        "legacyBaselineReceiptSha256": args.baseline_receipt_sha,
        "adminRootDependencyAdoptionReceiptSha256":
            args.admin_root_dependency_adoption_receipt_sha,
        "configurationReceiptSha256": args.configuration_receipt_sha,
        "preStageRuntimeIdentity": pre_database,
        "backendBuildPath": str(final_backend),
        "backendBuildSha256": args.backend_sha,
        "frontendBuildPath": str(final_manifest),
        "frontendBuildSha256": args.frontend_tree_sha,
        "migrationSha256": args.migration_sha,
        "runnerPath": str(final_runner),
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "releaseArtifactManifest": artifact_manifest,
        "frontendTreeEvidence": frontend_tree_receipt,
        "applicationRollbackAssemblyReceiptPath": str(final_application),
        "applicationRollbackAssemblyReceiptSha256": sha256_file(
            application_path
        ),
        "applicationRollbackAssemblyVerified": True,
        "applicationRollbackProven": False,
        "databaseRollbackSafetyReceiptPath": str(final_database),
        "databaseRollbackSafetyReceiptSha256": sha256_file(database_path),
        "databaseRollbackSafetyProven": True,
        "databaseDownClaimed": False,
        "strictHeadBuildUploadSwitchReceiptScriptPresent": True,
        "actualActiveArtifactsMatched": False,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionDatabaseChangedThisRun": False,
        "productionDatabaseChangedSinceStage": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
        "generatedAt": utc_now(),
    }
    receipt_path = incoming / "deployment-readiness-receipt.json"
    commit_migration_lease = None
    recovery_baseline = None
    recovery_facts_before_commit = None
    recovery_release = None
    try:
        if prior_recovery_anchor is not None:
            refreshed_anchor = (
                validate_prior_interrupted_recovery_stage_entry(
                    service_snapshot()
                )
            )
            if refreshed_anchor != prior_recovery_anchor:
                raise RuntimeError(
                    "interrupted recovery Stage anchor changed "
                    "before commit"
                )
            recovery_release = pathlib.Path(
                prior_recovery_anchor["receiptPath"]
            ).parent
            recovery_document = read_json(
                pathlib.Path(
                    prior_recovery_anchor["receiptPath"]
                )
            )
            recovery_baseline = recovery_document.get(
                "retainedMigrationFacts"
            )
            commit_migration_lease = (
                interrupted_apply_recovery_lease()
            )
            recovery_facts_before_commit = exact_migration_facts(
                commit_migration_lease.mysql
            )
            if not recorded_migration_counts_are_monotonic(
                recovery_facts_before_commit,
                recovery_baseline,
                DEPLOYMENT_RECEIPT_SCHEMA,
            ):
                raise RuntimeError(
                    "interrupted recovery 043 drifted before "
                    "Stage commit"
                )
        atomic_json(receipt_path, receipt)
        make_frontend_public(incoming)
        os.replace(incoming, final)
        fsync_directory(RELEASE_ROOT)
        if prior_recovery_anchor is not None:
            assert_predecessor_active(recovery_release)
            recovery_facts_at_commit = exact_migration_facts(
                commit_migration_lease.mysql
            )
            if (
                not recorded_migration_counts_are_monotonic(
                    recovery_facts_at_commit,
                    recovery_baseline,
                    DEPLOYMENT_RECEIPT_SCHEMA,
                )
                or not recorded_migration_counts_are_monotonic(
                    recovery_facts_at_commit,
                    recovery_facts_before_commit,
                    DEPLOYMENT_RECEIPT_SCHEMA,
                )
            ):
                raise RuntimeError(
                    "interrupted recovery 043 drifted at Stage CAS"
                )
        allowed_latest_predecessors = (
            [prior_rollback_anchor["receiptPath"]]
            if prior_rollback_anchor is not None
            else [prior_recovery_anchor["receiptPath"]]
            if prior_recovery_anchor is not None
            else []
        )
        advance_latest_receipt(
            final / "deployment-readiness-receipt.json",
            allowed_latest_predecessors,
        )
    finally:
        if commit_migration_lease is not None:
            commit_migration_lease.close()
    return staged_worker_result(
        args, final / "deployment-readiness-receipt.json", True
    )


def migration_fingerprint_rows(mysql):
    statement = """SELECT row_value FROM (
      SELECT CONCAT_WS('|','C',HEX(table_name),LPAD(ordinal_position,3,'0'),
        HEX(column_name),HEX(column_type),is_nullable,
        HEX(COALESCE(column_default,'<NULL>')),HEX(extra),
        HEX(COALESCE(character_set_name,'')),HEX(COALESCE(collation_name,'')),
        HEX(COALESCE(generation_expression,''))) AS row_value
      FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','I',HEX(table_name),HEX(index_name),non_unique,
        LPAD(seq_in_index,3,'0'),HEX(column_name),COALESCE(sub_part,''),
        HEX(COALESCE(collation,'')),HEX(index_type),HEX(nullable))
      FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','T',HEX(table_name),HEX(constraint_name),
        HEX(constraint_type))
      FROM information_schema.table_constraints
      WHERE table_schema=DATABASE() AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','K',HEX(table_name),HEX(constraint_name),
        HEX(column_name),LPAD(ordinal_position,3,'0'),
        HEX(COALESCE(referenced_table_name,'')),
        HEX(COALESCE(referenced_column_name,'')))
      FROM information_schema.key_column_usage
      WHERE table_schema=DATABASE() AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','F',HEX(constraint_name),HEX(table_name),
        HEX(referenced_table_name),HEX(update_rule),HEX(delete_rule),
        HEX(match_option))
      FROM information_schema.referential_constraints
      WHERE constraint_schema=DATABASE() AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','H',HEX(tc.table_name),HEX(cc.constraint_name),
        HEX(cc.check_clause))
      FROM information_schema.check_constraints cc
      JOIN information_schema.table_constraints tc
        ON tc.constraint_schema=cc.constraint_schema
       AND tc.constraint_name=cc.constraint_name
       AND tc.constraint_type='CHECK'
      WHERE tc.table_schema=DATABASE() AND tc.table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','R',HEX(trigger_name),HEX(event_manipulation),
        HEX(event_object_table),HEX(action_timing),HEX(action_orientation),
        HEX(REGEXP_REPLACE(TRIM(action_statement),'[[:space:]]+',' ')))
      FROM information_schema.triggers
      WHERE trigger_schema=DATABASE() AND event_object_table IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','M',HEX(permission.menu_name),
        LPAD(permission.order_num,6,'0'),HEX(COALESCE(permission.path,'')),
        HEX(COALESCE(permission.component,'<NULL>')),
        HEX(COALESCE(permission.query,'<NULL>')),
        HEX(COALESCE(permission.route_name,'')),permission.is_frame,
        permission.is_cache,HEX(permission.menu_type),HEX(permission.visible),
        HEX(permission.status),HEX(permission.perms),HEX(permission.icon),
        HEX(COALESCE(root.menu_name,'<NULL>')),
        LPAD(COALESCE(root.order_num,-1),6,'0'),
        HEX(COALESCE(root.path,'<NULL>')),
        HEX(COALESCE(root.component,'<NULL>')),
        HEX(COALESCE(root.query,'<NULL>')),
        HEX(COALESCE(root.route_name,'<NULL>')),
        COALESCE(root.is_frame,-1),COALESCE(root.is_cache,-1),
        HEX(COALESCE(root.menu_type,'<NULL>')),
        HEX(COALESCE(root.visible,'<NULL>')),
        HEX(COALESCE(root.status,'<NULL>')),
        HEX(COALESCE(root.perms,'<NULL>')),
        HEX(COALESCE(root.icon,'<NULL>')),
        IF(root.parent_id=0,'ROOT','NONROOT'))
      FROM sys_menu permission
      LEFT JOIN sys_menu root ON root.menu_id=permission.parent_id
      WHERE BINARY permission.perms=BINARY 'board:attribution:query'
    ) AS fingerprint_rows
    ORDER BY BINARY row_value"""
    return [str(row[0]) for row in mysql.rows(statement)]


def migration_fingerprint(mysql):
    rows = migration_fingerprint_rows(mysql)
    return sha256_bytes(("\n".join(rows) + "\n").encode("utf-8"))


def exact_migration_facts(mysql):
    return {
        "publicReceiptCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s AND description=%s",
                (MIGRATION_PUBLIC_VERSION, MIGRATION_PUBLIC_DESCRIPTION),
            )
        ),
        "internalReceiptCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s AND description=%s",
                (MIGRATION_INTERNAL_VERSION, MIGRATION_INTERNAL_DESCRIPTION),
            )
        ),
        "tableCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM information_schema.tables "
                "WHERE table_schema=DATABASE() AND table_name IN "
                "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
            )
        ),
        "triggerCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM information_schema.triggers "
                "WHERE trigger_schema=DATABASE() AND event_object_table IN "
                "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
            )
        ),
        "permissionCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM sys_menu WHERE "
                "BINARY perms=BINARY 'board:attribution:query'"
            )
        ),
        "eventCount": int(
            mysql.scalar("SELECT COUNT(*) FROM fbs_board_attr_event_v1")
        ),
        "probeEventCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE BINARY traffic_class=BINARY 'PROBE'"
            )
        ),
        "naturalEventCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE BINARY traffic_class=BINARY 'NATURAL'"
            )
        ),
        "nonProbeEventCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE BINARY traffic_class<>BINARY 'PROBE'"
            )
        ),
        "authoritativeProductCreditCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE authoritative_product_credit<>0"
            )
        ),
        "journeyCount": int(
            mysql.scalar("SELECT COUNT(*) FROM fbs_board_attr_journey_v1")
        ),
        "probeJourneyCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
                "WHERE BINARY traffic_class=BINARY 'PROBE'"
            )
        ),
        "naturalJourneyCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
                "WHERE BINARY traffic_class=BINARY 'NATURAL'"
            )
        ),
        "nonProbeJourneyCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
                "WHERE BINARY traffic_class<>BINARY 'PROBE'"
            )
        ),
        "schemaFingerprintSha256": migration_fingerprint(mysql),
    }


def validate_apply_session_identity(mysql, connection, expected):
    if not isinstance(expected, dict):
        raise RuntimeError("Apply session identity anchor is absent")
    identity = mysql.rows("SELECT LOWER(@@server_uuid),DATABASE(),@@version")
    if len(identity) != 1 or len(identity[0]) != 3:
        raise RuntimeError("Apply database identity query shape is invalid")
    actual = {
        "environmentSha256": sha256_file(ENV_PATH),
        "api2EventKeySha256": sha256_file(API2_EVENT_KEY_PATH),
        "additionalConfigSha256": validate_additional_config(),
        "databaseEndpoint": "{}:{}".format(
            connection["host"], connection["port"]
        ),
        "database": identity[0][1],
        "databaseServerUuid": identity[0][0],
        "databaseServerVersion": identity[0][2],
        "legacyBaselineReceiptSha256": mysql.scalar(
            "SELECT adoption_receipt_sha256 FROM "
            "u3w_legacy_schema_baseline_receipt_v2"
        ),
        "legacyBaselineMigrationCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version='legacy_w1a_baseline_20260724_001' "
                "AND description=CONCAT("
                "'APPLIED:LEGACY_ADOPTED_W1A_V2:',%s)",
                (expected.get("legacyBaselineReceiptSha256"),),
            )
        ),
    }
    if any(actual.get(key) != expected.get(key) for key in actual):
        raise RuntimeError("Apply session identity drifted")
    return actual


def apply_migration(args, release, expected_runtime_identity):
    _, connection = parse_environment()
    mysql = Mysql(connection)
    lock_acquired = False
    database_changed_this_run = False
    migration = release / "sql/public_init_043.sql"
    validate_regular_file(migration, migration.parent, modes=(0o600, 0o644))
    if sha256_file(migration) != args.migration_sha:
        raise RuntimeError("migration digest drifted")
    prestate = expected_runtime_identity.get("w1aDatabaseState")
    if prestate not in {"ABSENT", "EXACT_043_RETAINED_DORMANT"}:
        raise RuntimeError("staged W1A migration prestate is invalid")
    expected_event_count = (
        int(expected_runtime_identity.get("attributionEventCount", -1))
        if prestate == "EXACT_043_RETAINED_DORMANT"
        else 0
    )
    expected_journey_count = (
        int(expected_runtime_identity.get("attributionJourneyCount", -1))
        if prestate == "EXACT_043_RETAINED_DORMANT"
        else 0
    )
    if expected_event_count < 0 or expected_journey_count < 0:
        raise RuntimeError("staged W1A ledger counts are invalid")
    try:
        if int(mysql.scalar(
            "SELECT GET_LOCK('u3w:w1a:public_init_043',0)"
        )) != 1:
            raise RuntimeError("W1A migration named lock is unavailable")
        lock_acquired = True
        validate_apply_session_identity(
            mysql, connection, expected_runtime_identity
        )
        existing = mysql.rows(
            "SELECT description FROM u3w_schema_migration WHERE version=%s",
            (MIGRATION_PUBLIC_VERSION,),
        )
        if len(existing) == 1 and existing[0][0] == MIGRATION_PUBLIC_DESCRIPTION:
            facts = exact_migration_facts(mysql)
        else:
            if prestate != "ABSENT":
                raise RuntimeError(
                    "retained 043 prestate lost its exact migration receipt"
                )
            if existing and existing[0][0] != (
                "RUNNING:Independent Board exact official experts attribution v1"
            ):
                raise RuntimeError("public 043 receipt is in an unsafe state")
            if not existing:
                database_changed_this_run = True
                mysql.execute(
                    "INSERT INTO u3w_schema_migration(version,description) "
                    "VALUES(%s,%s)",
                    (
                        MIGRATION_PUBLIC_VERSION,
                        "RUNNING:Independent Board exact official experts "
                        "attribution v1",
                    ),
                )
            database_changed_this_run = True
            mysql.execute_script(
                parse_mysql_script(
                    migration.read_text(encoding="utf-8")
                )
            )
            validate_apply_session_identity(
                mysql, connection, expected_runtime_identity
            )
            facts = exact_migration_facts(mysql)
            if (
                not migration_structure_matches(
                    facts, public_receipt_count=0
                )
                or facts.get("eventCount") != 0
                or facts.get("journeyCount") != 0
            ):
                raise RuntimeError(
                    "043 exact current-read failed: {}".format(
                        json.dumps(
                            {
                                "facts": facts,
                                "expectedSchemaFingerprintSha256":
                                    EXPECTED_W1A_SCHEMA_FINGERPRINT,
                            },
                            sort_keys=True,
                        )
                    )
                )
            mysql.execute(
                "UPDATE u3w_schema_migration SET description=%s,"
                "applied_at=CURRENT_TIMESTAMP WHERE version=%s AND "
                "description=%s",
                (
                    MIGRATION_PUBLIC_DESCRIPTION,
                    MIGRATION_PUBLIC_VERSION,
                    "RUNNING:Independent Board exact official experts "
                    "attribution v1",
                ),
            )
            facts = exact_migration_facts(mysql)
        if (
            not migration_structure_matches(facts)
            or facts.get("eventCount") != expected_event_count
            or facts.get("journeyCount") != expected_journey_count
        ):
            raise RuntimeError("043 final current-read failed")
        validate_apply_session_identity(
            mysql, connection, expected_runtime_identity
        )
        return MigrationLease(
            mysql,
            connection,
            facts,
            database_changed_this_run=database_changed_this_run,
            database_changed_since_stage=(prestate == "ABSENT"),
        )
    except Exception:
        if lock_acquired:
            try:
                mysql.scalar(
                    "SELECT RELEASE_LOCK('u3w:w1a:public_init_043')"
                )
            except Exception:
                pass
        mysql.close()
        raise


def deployed_migration_lease(args, release, deployment):
    expected_runtime_identity = deployment.get("preStageRuntimeIdentity")
    baseline = deployment.get("migrationFacts")
    deployment_schema = deployment.get("schema")
    if (
        not isinstance(expected_runtime_identity, dict)
        or not recorded_migration_facts_valid(
            baseline, deployment_schema
        )
    ):
        raise RuntimeError("deployment migration baseline is invalid")
    migration = release / "sql/public_init_043.sql"
    validate_regular_file(migration, migration.parent, modes=(0o600, 0o644))
    if sha256_file(migration) != args.migration_sha:
        raise RuntimeError("migration digest drifted")
    _, connection = parse_environment()
    mysql = Mysql(connection)
    lock_acquired = False
    try:
        if int(mysql.scalar(
            "SELECT GET_LOCK('u3w:w1a:public_init_043',0)"
        )) != 1:
            raise RuntimeError("W1A migration named lock is unavailable")
        lock_acquired = True
        validate_apply_session_identity(
            mysql, connection, expected_runtime_identity
        )
        facts = exact_migration_facts(mysql)
        if not recorded_migration_counts_are_monotonic(
            facts, baseline, deployment_schema
        ):
            raise RuntimeError(
                "deployed W1A ledger structure or monotonic counts drifted"
            )
        return MigrationLease(mysql, connection, facts)
    except Exception:
        if lock_acquired:
            try:
                mysql.scalar(
                    "SELECT RELEASE_LOCK('u3w:w1a:public_init_043')"
                )
            except Exception:
                pass
        mysql.close()
        raise


def validate_staged_release_artifacts(args, release, receipt):
    expected_manifest = receipt.get("releaseArtifactManifest")
    if (
        not isinstance(expected_manifest, dict)
        or set(expected_manifest) != set(RELEASE_ARTIFACT_RELATIVE_PATHS)
    ):
        raise RuntimeError("staged release artifact manifest is invalid")
    actual_manifest = release_artifact_manifest(release)
    if actual_manifest != expected_manifest:
        raise RuntimeError("staged release artifact manifest drifted")
    frontend = frontend_tree_evidence(release)
    if (
        frontend != receipt.get("frontendTreeEvidence")
        or frontend.get("sha256") != args.frontend_tree_sha
    ):
        raise RuntimeError("staged frontend tree evidence drifted")
    predecessor = predecessor_facts(release)
    expected_digests = {
        "backend/fbsir-admin.jar": args.backend_sha,
        "sql/public_init_043.sql": args.migration_sha,
        "release-runner.ps1": args.runner_sha,
        "release-worker.py": args.worker_sha,
        "evidence/build-receipt.json": args.build_receipt_sha,
        "evidence/release-plan.json": args.plan_receipt_sha,
        "evidence/frontend-manifest.txt": sha256_bytes(
            tree_manifest(release / "frontend")["manifest"]
        ),
        "evidence/systemd-dropin.conf": sha256_bytes(
            systemd_dropin().encode("utf-8")
        ),
        "evidence/rollback-systemd-dropin.conf": sha256_bytes(
            rollback_systemd_dropin(release).encode("utf-8")
        ),
        "evidence/u3w-portal-sites.conf": sha256_bytes(
            portal_nginx_config(release).encode("utf-8")
        ),
        "evidence/application-rollback-assembly.json":
            receipt.get(
                "applicationRollbackAssemblyReceiptSha256"
            ),
        "evidence/database-rollback-safety.json":
            receipt.get("databaseRollbackSafetyReceiptSha256"),
        "rollback/previous-admin.jar":
            predecessor.get("previousJarSha256"),
        "rollback/placeholder-sites.conf":
            predecessor.get("previousNginxSha256"),
    }
    if any(
        expected_manifest[relative].get("sha256") != expected
        or not SHA_PATTERN.fullmatch(str(expected or ""))
        for relative, expected in expected_digests.items()
    ):
        raise RuntimeError("staged release artifact digest binding drifted")
    validate_release_marker(release / "frontend/w1a-release.json", args)
    return {
        "releaseArtifactManifest": actual_manifest,
        "frontendTreeEvidence": frontend,
    }


def validate_stage_receipt(args, release):
    path = release / "deployment-readiness-receipt.json"
    validate_regular_file(path, release, modes=(0o600,))
    digest = sha256_file(path)
    if digest != args.stage_receipt_sha:
        raise RuntimeError("staged receipt anchor drifted")
    receipt = read_json(path)
    if (
        receipt.get("schema") != DEPLOYMENT_RECEIPT_SCHEMA
        or receipt.get("state") != "STAGED_FOR_SWITCH"
        or receipt.get("releaseId") != args.release_id
        or receipt.get("sourceCommit") != args.source_commit
        or receipt.get("backendBuildSha256") != args.backend_sha
        or receipt.get("frontendBuildSha256") != args.frontend_tree_sha
        or receipt.get("runnerSha256") != args.runner_sha
        or receipt.get("workerSha256") != args.worker_sha
        or receipt.get("adminRootDependencyAdoptionReceiptSha256")
            != args.admin_root_dependency_adoption_receipt_sha
        or not SHA_PATTERN.fullmatch(
            str(receipt.get("stageApprovalReceiptSha256") or "")
        )
        or receipt.get("databaseDownClaimed") is not False
        or receipt.get("databaseRollbackSafetyProven") is not True
        or receipt.get("productionDatabaseChanged") is not False
        or receipt.get("productionDatabaseChangedThisRun") is not False
        or receipt.get("productionDatabaseChangedSinceStage") is not False
        or receipt.get("productionServiceChanged") is not False
        or receipt.get("officialExpertsPackageChanged") is not False
        or receipt.get("actualActiveArtifactsMatched") is not False
    ):
        raise RuntimeError("staged receipt identity is invalid")
    validate_staged_plan_target_binding(args, release, receipt)
    validate_staged_release_artifacts(args, release, receipt)
    validate_release_evidence(
        args, release, receipt, require_execution=False
    )
    return path, receipt


def restore_application(release, remove_current=True):
    rollback_config = release / "rollback/placeholder-sites.conf"
    rollback_dropin = release / "evidence/rollback-systemd-dropin.conf"
    validate_regular_file(
        rollback_config, rollback_config.parent, modes=(0o600,)
    )
    validate_regular_file(
        rollback_dropin, rollback_dropin.parent, modes=(0o600,)
    )
    predecessor = predecessor_facts(release)
    if (
        predecessor.get("previousNginxUid") != 0
        or predecessor.get("previousNginxGid") != 0
        or predecessor.get("previousNginxMode") not in (0o600, 0o640, 0o644)
    ):
        raise RuntimeError("predecessor Nginx custody evidence is invalid")
    atomic_bytes(
        NGINX_PATH,
        rollback_config.read_bytes(),
        predecessor["previousNginxMode"],
    )
    ensure_parent_directory(DROPIN_DIRECTORY)
    atomic_bytes(DROPIN_PATH, rollback_dropin.read_bytes(), 0o644)
    run_checked(["systemctl", "daemon-reload"], timeout=60)
    run_checked(["systemctl", "restart", SERVICE_UNIT], timeout=180)
    wait_for_u3w_health()
    run_checked(["nginx", "-t"], timeout=60)
    run_checked(["systemctl", "reload", "nginx.service"], timeout=60)
    if remove_current and (CURRENT_LINK.exists() or CURRENT_LINK.is_symlink()):
        CURRENT_LINK.unlink()
        fsync_directory(CURRENT_LINK.parent)


def predecessor_facts(release):
    path = release / "evidence/application-rollback-assembly.json"
    validate_regular_file(path, path.parent, modes=(0o600,))
    facts = read_json(path)
    predecessor = release / "rollback/previous-admin.jar"
    rollback_nginx = release / "rollback/placeholder-sites.conf"
    rollback_dropin = release / "evidence/rollback-systemd-dropin.conf"
    if (
        facts.get("schema")
        != "fbsir.u3wApplicationRollbackAssemblyReceipt.v1"
        or facts.get("releaseId") != release.name
        or not COMMIT_PATTERN.fullmatch(
            str(facts.get("sourceCommit") or "")
        )
        or facts.get("assemblyVerified") is not True
        or facts.get("applicationRollbackProven") is not False
        or facts.get("strategy")
        != "IMMUTABLE_PREDECESSOR_DROPIN_RESTORE_NGINX_AND_RESTART"
        or facts.get("immutablePreviousJarPath") != str(predecessor)
        or not validate_regular_file(
            predecessor, predecessor.parent, modes=(0o600,)
        )
        or sha256_file(predecessor) != facts.get("previousJarSha256")
        or not validate_regular_file(
            rollback_dropin, rollback_dropin.parent, modes=(0o600,)
        )
        or sha256_file(rollback_dropin) != sha256_bytes(
            rollback_systemd_dropin(release).encode("utf-8")
        )
        or not validate_regular_file(
            rollback_nginx, rollback_nginx.parent, modes=(0o600,)
        )
        or sha256_file(rollback_nginx)
            != facts.get("previousNginxSha256")
    ):
        raise RuntimeError("immutable predecessor evidence is invalid")
    return facts


def rollback_process_arguments(release):
    return [
        "/usr/bin/java",
        "-Dspring.config.additional-location=file:"
        + str(ADDITIONAL_CONFIG_PATH),
        "-jar",
        str(release / "rollback/previous-admin.jar"),
    ]


def release_dropin_manifest(release, file_name):
    previous = predecessor_facts(release)["serviceSnapshotBeforeStage"]
    path = release / "evidence" / file_name
    validate_regular_file(path, path.parent, modes=(0o600,))
    release_dropin = {
        "path": str(DROPIN_PATH),
        "sha256": sha256_file(path),
        "mode": 0o644,
    }
    return sorted(
        [
            item
            for item in previous.get("dropInManifest", [])
            if item.get("path") != str(DROPIN_PATH)
        ] + [release_dropin],
        key=lambda item: item["path"],
    )


def security_measurement_contract_matches(
    current,
    previous,
    *,
    allow_token_pending=False,
):
    previous_expected_names = previous.get(
        "expectedSecurityConfigurationNames"
    )
    previous_process_names = previous.get(
        "processSecurityConfigurationNames"
    )
    current_expected_names = current.get(
        "expectedSecurityConfigurationNames"
    )
    current_process_names = current.get(
        "processSecurityConfigurationNames"
    )
    previous_expected_hmac = previous.get(
        "expectedSecurityConfigurationHmacSha256"
    )
    previous_process_hmac = previous.get(
        "processSecurityConfigurationHmacSha256"
    )
    current_expected_hmac = current.get(
        "expectedSecurityConfigurationHmacSha256"
    )
    current_process_hmac = current.get(
        "processSecurityConfigurationHmacSha256"
    )
    previous_configured_names = previous.get(
        "configuredEnvironmentNames"
    )
    current_configured_names = current.get(
        "configuredEnvironmentNames"
    )
    previous_configured_environment_hmac = previous.get(
        "configuredEnvironmentHmacSha256"
    )
    previous_process_environment_hmac = previous.get(
        "processConfiguredEnvironmentHmacSha256"
    )
    current_configured_environment_hmac = current.get(
        "configuredEnvironmentHmacSha256"
    )
    current_process_environment_hmac = current.get(
        "processConfiguredEnvironmentHmacSha256"
    )
    previous_load_state = previous.get(
        "processConfiguredEnvironmentLoadState"
    )
    previous_token_pending = bool(
        previous_load_state
            == "TOKEN_SECRET_ROTATION_PENDING_RESTART"
        and TOKEN_SECRET_NAME in (previous_expected_names or [])
        and TOKEN_SECRET_NAME in (previous_configured_names or [])
        and SHA_PATTERN.fullmatch(
            str(previous_expected_hmac or "")
        ) is not None
        and SHA_PATTERN.fullmatch(
            str(previous_process_hmac or "")
        ) is not None
        and not hmac.compare_digest(
            previous_expected_hmac,
            previous_process_hmac,
        )
        and SHA_PATTERN.fullmatch(
            str(previous_configured_environment_hmac or "")
        ) is not None
        and SHA_PATTERN.fullmatch(
            str(previous_process_environment_hmac or "")
        ) is not None
        and not hmac.compare_digest(
            previous_configured_environment_hmac,
            previous_process_environment_hmac,
        )
        and previous.get("processConfiguredEnvironmentMatched") is False
        and previous.get(
            "processConfiguredEnvironmentMismatchNames"
        ) == [TOKEN_SECRET_NAME]
        and previous.get(
            "processPendingRestartEnvironmentNames"
        ) == []
    )
    if (
        not isinstance(previous_expected_names, list)
        or previous_expected_names
            != sorted(set(previous_expected_names))
        or previous_process_names != previous_expected_names
        or not isinstance(current_expected_names, list)
        or current_expected_names != sorted(set(current_expected_names))
        or current_process_names != current_expected_names
        or not SHA_PATTERN.fullmatch(str(previous_expected_hmac or ""))
        or not SHA_PATTERN.fullmatch(
            str(previous_process_hmac or "")
        )
        or (
            previous_load_state
                == "TOKEN_SECRET_ROTATION_PENDING_RESTART"
            and not previous_token_pending
        )
        or (
            not previous_token_pending
            and not hmac.compare_digest(
                previous_process_hmac,
                previous_expected_hmac,
            )
        )
        or not SHA_PATTERN.fullmatch(str(current_expected_hmac or ""))
        or not SHA_PATTERN.fullmatch(str(current_process_hmac or ""))
    ):
        return False
    current_load_state = current.get(
        "processConfiguredEnvironmentLoadState"
    )
    token_pending = bool(
        current_load_state
            == "TOKEN_SECRET_ROTATION_PENDING_RESTART"
        and TOKEN_SECRET_NAME in current_expected_names
        and TOKEN_SECRET_NAME in (current_configured_names or [])
        and not hmac.compare_digest(
            current_expected_hmac,
            current_process_hmac,
        )
        and SHA_PATTERN.fullmatch(
            str(current_configured_environment_hmac or "")
        ) is not None
        and SHA_PATTERN.fullmatch(
            str(current_process_environment_hmac or "")
        ) is not None
        and not hmac.compare_digest(
            current_configured_environment_hmac,
            current_process_environment_hmac,
        )
        and current.get("processConfiguredEnvironmentMatched") is False
        and current.get(
            "processConfiguredEnvironmentMismatchNames"
        ) == [TOKEN_SECRET_NAME]
        and current.get(
            "processPendingRestartEnvironmentNames"
        ) == []
    )
    if (
        current_load_state == "TOKEN_SECRET_ROTATION_PENDING_RESTART"
        and not token_pending
    ):
        return False
    if token_pending:
        expected_process_environment_hmac = (
            previous.get("processConfiguredEnvironmentHmacSha256")
            if previous_token_pending
            else previous.get("configuredEnvironmentHmacSha256")
        )
        if (
            not allow_token_pending
            or SHA_PATTERN.fullmatch(
                str(expected_process_environment_hmac or "")
            ) is None
            or current_process_environment_hmac
                != expected_process_environment_hmac
        ):
            return False
    elif not hmac.compare_digest(
        current_process_hmac,
        current_expected_hmac,
    ):
        return False
    if current_expected_names == previous_expected_names:
        if not token_pending:
            return hmac.compare_digest(
                current_expected_hmac,
                previous_expected_hmac,
            )
        if previous_token_pending:
            return bool(
                hmac.compare_digest(
                    current_expected_hmac,
                    previous_expected_hmac,
                )
                and hmac.compare_digest(
                    current_process_hmac,
                    previous_process_hmac,
                )
            )
        return hmac.compare_digest(
            current_process_hmac,
            previous_expected_hmac,
        )
    if previous_token_pending:
        return False
    return bool(
        TOKEN_SECRET_NAME
            in previous.get("configuredEnvironmentNames", [])
        and TOKEN_SECRET_NAME not in previous_expected_names
        and current_expected_names
            == sorted(previous_expected_names + [TOKEN_SECRET_NAME])
    )


def exact_loaded_environment_matches(current, previous):
    static_fields = (
        "environmentFilePaths",
        "environmentFileManifest",
        "api2EventKeyManifest",
        "configuredEnvironmentSha256",
        "configuredEnvironmentNames",
        "configuredEnvironmentHmacSha256",
        "configuredFlagValues",
    )
    return bool(
        all(
            current.get(field) == previous.get(field)
            for field in static_fields
        )
        and security_measurement_contract_matches(current, previous)
        and current.get("processDatabaseBindingMatched") is True
        and current.get("processConfiguredEnvironmentMatched") is True
        and current.get(
            "processConfiguredEnvironmentPreStageCompatible"
        ) is True
        and current.get("processConfiguredEnvironmentLoadState")
            == "EXACT_CONFIGURED"
        and current.get("processPendingRestartEnvironmentNames") == []
        and current.get(
            "processConfiguredEnvironmentMismatchNames"
        ) == []
        and current.get("processConfiguredEnvironmentHmacSha256")
            == current.get("configuredEnvironmentHmacSha256")
        and current.get("processForbiddenOverrideNames") == []
        and all(
            current.get("processFlagValues", {}).get(name) == "false"
            and current.get("configuredFlagValues", {}).get(name)
                == "false"
            for name in FALSE_FLAGS
        )
    )


def authorized_admin_engine_configuration_evolution_matches(
    current,
    previous,
):
    try:
        configuration = validate_configuration_runtime_anchor(
            read_json(CONFIGURATION_RECEIPT)
        )
        if configuration.get("schema") != (
            "fbsir.u3wDefaultOffConfigurationReceipt.v3"
        ):
            return False
        predecessor = configuration_predecessor_receipt(
            configuration.get(
                "predecessorConfigurationReceiptSha256"
            )
        )
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError):
        return False
    previous_manifest = previous.get("environmentFileManifest", [])
    current_manifest = current.get("environmentFileManifest", [])
    if len(previous_manifest) != 1 or len(current_manifest) != 1:
        return False
    previous_file = dict(previous_manifest[0])
    current_file = dict(current_manifest[0])
    previous_file_sha = previous_file.pop("sha256", None)
    current_file_sha = current_file.pop("sha256", None)
    previous_names = previous.get("configuredEnvironmentNames", [])
    current_names = current.get("configuredEnvironmentNames", [])
    expected_current_names = sorted(
        list(previous_names) + [ADMIN_ENGINE_TOKEN_NAME]
    )
    previous_security_names = previous.get(
        "expectedSecurityConfigurationNames",
        [],
    )
    expected_current_security_names = sorted(
        list(previous_security_names) + [ADMIN_ENGINE_TOKEN_NAME]
    )
    load_state = current.get(
        "processConfiguredEnvironmentLoadState"
    )
    exact_loaded = bool(
        load_state == "EXACT_CONFIGURED"
        and current.get("processConfiguredEnvironmentMatched") is True
        and current.get("processPendingRestartEnvironmentNames") == []
        and current.get("processSecurityConfigurationNames")
            == current.get("expectedSecurityConfigurationNames")
        and current.get("processSecurityConfigurationHmacSha256")
            == current.get("expectedSecurityConfigurationHmacSha256")
    )
    engine_pending = bool(
        load_state == "ENGINE_CREDENTIAL_PENDING_RESTART"
        and current.get("processConfiguredEnvironmentMatched") is False
        and current.get("processPendingRestartEnvironmentNames")
            == [ADMIN_ENGINE_TOKEN_NAME]
    )
    return bool(
        configuration.get("adminEngineCredentialProvisioningState")
            == "ADOPTED_EXISTING_EXACT_DELTA"
        and configuration.get("engineCounterpartClosureClaimed") is False
        and predecessor.get("environmentAfterSha256")
            == previous.get("configuredEnvironmentSha256")
        and previous_file_sha
            == previous.get("configuredEnvironmentSha256")
        and configuration.get("environmentAfterSha256")
            == current.get("configuredEnvironmentSha256")
        and current_file_sha
            == current.get("configuredEnvironmentSha256")
        and previous_file == current_file
        and current.get("environmentFilePaths")
            == previous.get("environmentFilePaths")
        and ADMIN_ENGINE_TOKEN_NAME not in previous_names
        and current_names == expected_current_names
        and ADMIN_ENGINE_TOKEN_NAME not in previous_security_names
        and current.get("expectedSecurityConfigurationNames")
            == expected_current_security_names
        and current.get("api2EventKeyManifest")
            == previous.get("api2EventKeyManifest")
        and current.get("configuredFlagValues")
            == previous.get("configuredFlagValues")
        and current.get("processDatabaseBindingMatched") is True
        and current.get(
            "processConfiguredEnvironmentPreStageCompatible"
        ) is True
        and current.get(
            "processConfiguredEnvironmentMismatchNames"
        ) == []
        and current.get("processForbiddenOverrideNames") == []
        and (exact_loaded or engine_pending)
        and all(
            current.get("configuredFlagValues", {}).get(name) == "false"
            and current.get("processFlagValues", {}).get(name) == "false"
            for name in FALSE_FLAGS
        )
    )


def authorized_token_secret_configuration_evolution_matches(
    current,
    previous,
):
    try:
        configuration = validate_configuration_runtime_anchor(
            read_json(CONFIGURATION_RECEIPT)
        )
        if configuration.get("schema") != (
            "fbsir.u3wDefaultOffConfigurationReceipt.v4"
        ):
            return False
        predecessor = configuration_predecessor_receipt(
            configuration.get(
                "predecessorConfigurationReceiptSha256"
            )
        )
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError):
        return False
    if predecessor.get("schema") != (
        "fbsir.u3wDefaultOffConfigurationReceipt.v3"
    ):
        return False
    previous_manifest = previous.get("environmentFileManifest", [])
    current_manifest = current.get("environmentFileManifest", [])
    if len(previous_manifest) != 1 or len(current_manifest) != 1:
        return False
    previous_file = dict(previous_manifest[0])
    current_file = dict(current_manifest[0])
    previous_file_sha = previous_file.pop("sha256", None)
    current_file_sha = current_file.pop("sha256", None)
    previous_load_state = previous.get(
        "processConfiguredEnvironmentLoadState"
    )
    allowed_previous_states = (
        "EXACT_CONFIGURED",
        "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART",
        "ENGINE_CREDENTIAL_PENDING_RESTART",
    )
    return bool(
        configuration.get("tokenSecretProvisioningState")
            == "ROTATED_BY_RUN"
        and configuration.get(
            "adminEngineCredentialProvisioningState"
        ) == "REUSED_FROM_PREDECESSOR_RECEIPT"
        and predecessor.get("environmentAfterSha256")
            == previous.get("configuredEnvironmentSha256")
        and configuration.get("environmentBeforeSha256")
            == previous.get("configuredEnvironmentSha256")
        and previous_file_sha
            == previous.get("configuredEnvironmentSha256")
        and configuration.get("environmentAfterSha256")
            == current.get("configuredEnvironmentSha256")
        and current_file_sha
            == current.get("configuredEnvironmentSha256")
        and previous_file == current_file
        and current.get("environmentFilePaths")
            == previous.get("environmentFilePaths")
        and current.get("configuredEnvironmentNames")
            == previous.get("configuredEnvironmentNames")
        and security_measurement_contract_matches(
            current,
            previous,
            allow_token_pending=True,
        )
        and current.get("api2EventKeyManifest")
            == previous.get("api2EventKeyManifest")
        and current.get("configuredFlagValues")
            == previous.get("configuredFlagValues")
        and current.get("processDatabaseBindingMatched") is True
        and current.get(
            "processConfiguredEnvironmentPreStageCompatible"
        ) is True
        and current.get("processConfiguredEnvironmentMatched") is False
        and current.get(
            "processConfiguredEnvironmentMismatchNames"
        ) == [TOKEN_SECRET_NAME]
        and current.get("processPendingRestartEnvironmentNames") == []
        and current.get("processForbiddenOverrideNames") == []
        and current.get("processConfiguredEnvironmentLoadState")
            == "TOKEN_SECRET_ROTATION_PENDING_RESTART"
        and current.get("processConfiguredEnvironmentHmacSha256")
            == previous.get("configuredEnvironmentHmacSha256")
        and previous_load_state in allowed_previous_states
        and all(
            previous.get("configuredFlagValues", {}).get(name) == "false"
            and previous.get("processFlagValues", {}).get(name) == "false"
            and current.get("configuredFlagValues", {}).get(name) == "false"
            and current.get("processFlagValues", {}).get(name) == "false"
            for name in FALSE_FLAGS
        )
    )


def assert_legacy_runtime_contract(release, current):
    previous = predecessor_facts(release)["serviceSnapshotBeforeStage"]
    exact_fields = (
        "user",
        "group",
        "fragmentPath",
        "fragmentFileManifest",
        "environmentFilePaths",
        "environmentFileManifest",
        "workingDirectory",
        "configuredJarPath",
        "configuredJarSha256",
        "processJarPath",
        "processJarSha256",
        "processArgvSha256",
        "processEnvironmentNamesSha256",
        "configuredFlagValues",
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
        "processFlagValues",
        "processForbiddenOverrideNames",
        "dropInManifest",
        "externalConfigManifest",
        "additionalConfigSha256",
        "jarPath",
        "jarSha256",
    )
    if any(current.get(field) != previous.get(field) for field in exact_fields):
        raise RuntimeError("untouched legacy runtime contract drifted")
    return current


def assert_restored_predecessor_runtime_contract(release, current):
    previous = predecessor_facts(release)["serviceSnapshotBeforeStage"]
    predecessor_path = release / "rollback/previous-admin.jar"
    expected_argv = rollback_process_arguments(release)
    expected_argv_sha256 = sha256_bytes(
        ("\0".join(expected_argv) + "\0").encode("utf-8")
    )
    configuration_matches = (
        exact_loaded_environment_matches(current, previous)
        or authorized_admin_engine_configuration_evolution_matches(
            current,
            previous,
        )
        or authorized_token_secret_configuration_evolution_matches(
            current,
            previous,
        )
    )
    if (
        current.get("user") != previous.get("user")
        or current.get("group") != previous.get("group")
        or current.get("fragmentPath") != previous.get("fragmentPath")
        or current.get("fragmentFileManifest")
            != previous.get("fragmentFileManifest")
        or current.get("workingDirectory") != str(ADMIN_ROOT)
        or current.get("configuredJarPath") != str(predecessor_path)
        or current.get("processJarPath") != str(predecessor_path)
        or current.get("processArgvSha256") != expected_argv_sha256
        or not configuration_matches
        or current.get("dropInManifest")
            != release_dropin_manifest(
                release, "rollback-systemd-dropin.conf"
            )
        or current.get("externalConfigManifest")
            != previous.get("externalConfigManifest")
        or current.get("additionalConfigSha256")
            != previous.get("additionalConfigSha256")
        or current.get("configuredJarSha256")
            != previous.get("jarSha256")
        or current.get("processJarSha256")
            != previous.get("jarSha256")
        or current.get("jarSha256") != previous.get("jarSha256")
    ):
        raise RuntimeError("restored predecessor runtime contract drifted")
    return current


def assert_predecessor_topology(release, current):
    predecessor = predecessor_facts(release)
    previous_snapshot = predecessor["serviceSnapshotBeforeStage"]
    if (
        current["jarSha256"] != predecessor["previousJarSha256"]
        or current["configuredJarSha256"]
            != predecessor["previousJarSha256"]
        or sha256_file(NGINX_PATH) != predecessor["previousNginxSha256"]
        or CURRENT_LINK.exists()
        or CURRENT_LINK.is_symlink()
    ):
        raise RuntimeError("predecessor topology drifted after Stage")
    baseline_release_dropins = [
        item
        for item in previous_snapshot.get("dropInManifest", [])
        if item.get("path") == str(DROPIN_PATH)
    ]
    if baseline_release_dropins:
        expected_dropin = baseline_release_dropins[0]
        if (
            len(baseline_release_dropins) != 1
            or DROPIN_PATH.is_symlink()
            or not DROPIN_PATH.is_file()
            or sha256_file(DROPIN_PATH)
                != expected_dropin.get("sha256")
            or DROPIN_PATH.stat().st_mode & 0o777
                != expected_dropin.get("mode")
        ):
            raise RuntimeError("staged predecessor drop-in drifted")
    elif DROPIN_PATH.exists() or DROPIN_PATH.is_symlink():
        raise RuntimeError("unexpected staged predecessor drop-in")
    if current.get("activeState") == "active":
        assert_legacy_runtime_contract(release, current)
    return predecessor


def assert_candidate_owned_topology(args, release):
    current = service_snapshot(require_active=False)
    candidate_dropin = release / "evidence/systemd-dropin.conf"
    candidate_nginx = release / "evidence/u3w-portal-sites.conf"
    if (
        not CURRENT_LINK.is_symlink()
        or CURRENT_LINK.resolve() != release.resolve()
        or current["jarSha256"] != args.backend_sha
        or current["configuredJarSha256"] != args.backend_sha
        or not DROPIN_PATH.is_file()
        or DROPIN_PATH.is_symlink()
        or sha256_file(DROPIN_PATH) != sha256_file(candidate_dropin)
        or not NGINX_PATH.is_file()
        or NGINX_PATH.is_symlink()
        or sha256_file(NGINX_PATH) != sha256_file(candidate_nginx)
    ):
        raise RuntimeError("candidate active topology is incomplete or drifted")
    return current


def assert_candidate_runtime_contract(args, release, current):
    predecessor = predecessor_facts(release)
    previous_snapshot = predecessor["serviceSnapshotBeforeStage"]
    expected_argv = candidate_process_arguments()
    expected_argv_sha256 = sha256_bytes(
        ("\0".join(expected_argv) + "\0").encode("utf-8")
    )
    expected_dropins = release_dropin_manifest(
        release, "systemd-dropin.conf"
    )
    if (
        current.get("processArgvSha256") != expected_argv_sha256
        or current.get("processJarPath")
        != str(CURRENT_LINK / "backend/fbsir-admin.jar")
        or current.get("environmentFilePaths")
        != previous_snapshot.get("environmentFilePaths")
        or current.get("environmentFileManifest")
        != previous_snapshot.get("environmentFileManifest")
        or current.get("fragmentFileManifest")
            != previous_snapshot.get("fragmentFileManifest")
        or not exact_loaded_environment_matches(
            current, previous_snapshot
        )
        or current.get("dropInManifest") != expected_dropins
        or current.get("externalConfigManifest")
            != previous_snapshot.get("externalConfigManifest")
        or current.get("additionalConfigSha256")
            != previous_snapshot.get("additionalConfigSha256")
    ):
        raise RuntimeError(
            "candidate actual process or effective default-off contract drifted"
        )
    return current


def assert_candidate_active(args, release):
    current = assert_candidate_owned_topology(args, release)
    if current["activeState"] != "active":
        raise RuntimeError("candidate service is not active")
    assert_candidate_runtime_contract(args, release, current)
    wait_for_u3w_health()
    for host in ("me.u3w.com", "admin.u3w.com"):
        status, body = candidate_portal_http_json(host, "api")
        if (
            status != 200
            or not isinstance(body, dict)
            or body.get("code") != 200
        ):
            raise RuntimeError(host + " API portal readiness failed")
        marker_status, marker = candidate_portal_http_json(
            host, "marker"
        )
        if (
            marker_status != 200
            or not isinstance(marker, dict)
            or marker.get("schema") != "fbsir.u3wReleaseMarker.v1"
            or marker.get("releaseId") != args.release_id
            or marker.get("sourceCommit") != args.source_commit
            or marker.get("officialExpertsPackageChanged") is not False
        ):
            raise RuntimeError(host + " frontend release marker drifted")
    environment, _ = parse_environment()
    if any(environment.get(name, "").strip() != "false" for name in FALSE_FLAGS):
        raise RuntimeError("candidate flags are not explicitly false")
    return current


def final_default_off_current_read(
    args,
    release,
    mysql,
    expected_migration_facts,
):
    initial_service = assert_candidate_active(args, release)
    before_probe = exact_migration_facts(mysql)
    if before_probe != expected_migration_facts:
        raise RuntimeError(
            "043 current-read drifted before final default-off probe"
        )
    disabled_probe, after_probe = disabled_attribution_ingress_probe(
        mysql, before_probe
    )
    final_service = assert_candidate_active(args, release)
    if initial_service != final_service:
        raise RuntimeError(
            "candidate service changed during final default-off probe"
        )
    if after_probe != before_probe:
        raise RuntimeError(
            "attribution counts changed during final default-off probe"
        )
    return final_service, {
        "schema": FINAL_CURRENT_READ_SCHEMA,
        "verified": True,
        "serviceStableDuringProbe": True,
        "serviceInvocationId": final_service.get("invocationId"),
        "serviceJarSha256": final_service.get("jarSha256"),
        "disabledAttributionIngressProbe": disabled_probe,
        "migrationFactsBeforeProbe": before_probe,
        "migrationFactsAfterProbe": after_probe,
        "eventAndJourneyCountsUnchanged": True,
        "observedAt": utc_now(),
    }


def validate_final_default_off_current_read(
    evidence,
    service,
    migration_facts,
    deployment_schema,
):
    contract_schemas = deployment_contract_schemas(deployment_schema)
    probe = (
        evidence.get("disabledAttributionIngressProbe")
        if isinstance(evidence, dict) else None
    )
    probe_fields = {
        "schema",
        "path",
        "method",
        "httpStatus",
        "responseDisposition",
        "verifiedDisabled",
        "acceptedDisabledHttpStatuses",
        "trafficClass",
        "signingKeyId",
        "signatureAlgorithm",
        "probeIdentity",
        "identityCountsBefore",
        "identityCountsAfter",
        "globalCountsBefore",
        "globalCountsAfter",
        "rawNonceDisclosed",
        "rawSignatureDisclosed",
        "signingKeyMaterialDisclosed",
        "secretsDisclosed",
        "observedAt",
    }
    probe_identity = (
        probe.get("probeIdentity") if isinstance(probe, dict) else None
    )
    identity_before = (
        probe.get("identityCountsBefore")
        if isinstance(probe, dict) else None
    )
    identity_after = (
        probe.get("identityCountsAfter")
        if isinstance(probe, dict) else None
    )
    expected_identity_fields = {
        "eventId", "receiptId", "nonceHash", "journeyId"
    }
    expected_global_counts = {
        "eventCount": migration_facts.get("eventCount"),
        "journeyCount": migration_facts.get("journeyCount"),
    } if isinstance(migration_facts, dict) else None
    if (
        not isinstance(evidence, dict)
        or not isinstance(service, dict)
        or not recorded_migration_facts_valid(
            migration_facts, deployment_schema
        )
        or evidence.get("schema")
            != contract_schemas["finalCurrentRead"]
        or evidence.get("verified") is not True
        or evidence.get("serviceStableDuringProbe") is not True
        or evidence.get("serviceInvocationId")
            != service.get("invocationId")
        or evidence.get("serviceJarSha256") != service.get("jarSha256")
        or evidence.get("migrationFactsBeforeProbe") != migration_facts
        or evidence.get("migrationFactsAfterProbe") != migration_facts
        or evidence.get("eventAndJourneyCountsUnchanged") is not True
        or not isinstance(probe, dict)
        or set(probe) != probe_fields
        or probe.get("schema")
            != "fbsir.u3wSignedDisabledAttributionIngressProbe.v1"
        or probe.get("path") != ATTRIBUTION_INGRESS_PATH
        or probe.get("method") != "POST"
        or probe.get("httpStatus") != DISABLED_INGRESS_HTTP_STATUS
        or probe.get("responseDisposition") != "ROUTE_NOT_FOUND"
        or probe.get("verifiedDisabled") is not True
        or probe.get("acceptedDisabledHttpStatuses")
            != [DISABLED_INGRESS_HTTP_STATUS]
        or probe.get("trafficClass") != "PROBE"
        or re.fullmatch(
            r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}",
            str(probe.get("signingKeyId") or ""),
        ) is None
        or probe.get("signatureAlgorithm") != "hmac-sha256-v1"
        or not isinstance(probe_identity, dict)
        or set(probe_identity) != expected_identity_fields
        or any(
            not SHA_PATTERN.fullmatch(str(value or ""))
            for value in probe_identity.values()
        )
        or identity_before
            != {name: 0 for name in expected_identity_fields}
        or identity_after
            != {name: 0 for name in expected_identity_fields}
        or probe.get("globalCountsBefore") != expected_global_counts
        or probe.get("globalCountsAfter") != expected_global_counts
        or probe.get("rawNonceDisclosed") is not False
        or probe.get("rawSignatureDisclosed") is not False
        or probe.get("signingKeyMaterialDisclosed") is not False
        or probe.get("secretsDisclosed") is not False
        or not isinstance(probe.get("observedAt"), str)
    ):
        raise RuntimeError(
            "final default-off current-read evidence is invalid"
        )
    return evidence


def deployment_receipt(
    args,
    stage_path,
    stage,
    migration_facts,
    before,
    after,
    rollback_execution_path,
    database_changed_this_run,
    database_changed_since_stage,
    final_current_read,
):
    validate_final_default_off_current_read(
        final_current_read,
        after,
        migration_facts,
        DEPLOYMENT_RECEIPT_SCHEMA,
    )
    active_nginx, nginx_dump_sha256 = active_nginx_manifest()
    receipt = dict(stage)
    receipt.update(
        {
            "state": "DEPLOYED_DEFAULT_OFF",
            "stageReceiptSha256": sha256_file(stage_path),
            "applyApprovalReceiptSha256": args.approval_sha,
            "migrationFacts": migration_facts,
            "serviceBefore": before,
            "serviceAfter": after,
            "currentLinkResolved": str(CURRENT_LINK.resolve()),
            "nginxPath": str(NGINX_PATH),
            "nginxSha256": sha256_file(NGINX_PATH),
            "activeNginxManifestAfterApply": active_nginx,
            "nginxDumpSha256AfterApply": nginx_dump_sha256,
            "applicationRollbackReceiptPath": str(
                rollback_execution_path
            ),
            "applicationRollbackReceiptSha256": sha256_file(
                rollback_execution_path
            ),
            "applicationRollbackProven": True,
            "databaseRollbackSafetyProven": True,
            "databaseDownClaimed": False,
            "actualActiveArtifactsMatched": True,
            "productionFilesystemChanged": True,
            "productionDatabaseChanged":
                database_changed_since_stage,
            "productionDatabaseChangedThisRun":
                database_changed_this_run,
            "productionDatabaseChangedSinceStage":
                database_changed_since_stage,
            "productionServiceChanged": True,
            "configurationLoaded": True,
            "finalDefaultOffCurrentRead": final_current_read,
            "generatedAt": utc_now(),
        }
    )
    return receipt


def install_candidate_application(args, release):
    safe_directory(DROPIN_DIRECTORY, 0o755)
    atomic_symlink(release, CURRENT_LINK)
    atomic_bytes(
        DROPIN_PATH,
        (release / "evidence/systemd-dropin.conf").read_bytes(),
        0o644,
    )
    atomic_bytes(
        NGINX_PATH,
        (release / "evidence/u3w-portal-sites.conf").read_bytes(),
        0o644,
    )
    run_checked(["nginx", "-t"], timeout=60)
    run_checked(["systemctl", "daemon-reload"], timeout=60)
    run_checked(["systemctl", "restart", SERVICE_UNIT], timeout=180)
    wait_for_u3w_health()
    run_checked(["systemctl", "reload", "nginx.service"], timeout=60)
    return assert_candidate_active(args, release)


def application_rollback_execution_receipt(
    args,
    release,
    migration_facts,
    candidate_before,
    predecessor_after,
    candidate_after,
):
    if (
        candidate_before["invocationId"] == predecessor_after["invocationId"]
        or predecessor_after["invocationId"] == candidate_after["invocationId"]
        or candidate_before["jarSha256"] != args.backend_sha
        or candidate_after["jarSha256"] != args.backend_sha
        or predecessor_after["jarSha256"]
        != predecessor_facts(release)["previousJarSha256"]
    ):
        raise RuntimeError("application rollback execution proof is invalid")
    receipt = {
        "schema": APPLICATION_ROLLBACK_EXECUTION_SCHEMA,
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "applyApprovalReceiptSha256": args.approval_sha,
        "strategy": (
            "LIVE_CANDIDATE_TO_IMMUTABLE_PREDECESSOR_TO_CANDIDATE"
        ),
        "verified": True,
        "candidateBeforeRollback": candidate_before,
        "predecessorAfterRollback": predecessor_after,
        "candidateAfterReapply": candidate_after,
        "retainedMigrationFacts": migration_facts,
        "database043Retained": True,
        "productionServiceChanged": True,
        "productionDatabaseChanged": False,
        "officialExpertsPackageChanged": False,
        "observedAt": utc_now(),
    }
    path = release / "evidence/application-rollback-execution.json"
    atomic_json(path, receipt)
    return path


def apply_worker_result(
    args,
    deployment_path,
    filesystem_changed,
    database_changed_this_run,
    database_changed_since_stage,
    service_changed,
    deployment_committed,
    latest_repaired,
    recovered=False,
):
    receipt = read_json(deployment_path)
    return {
        "schema": WORKER_RESULT_SCHEMA,
        "mode": "Apply",
        "state": "DEPLOYED_DEFAULT_OFF",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "transitionApprovalReceiptSha256":
            receipt.get("applyApprovalReceiptSha256"),
        "receiptPath": str(deployment_path),
        "receiptSha256": sha256_file(deployment_path),
        "evidenceReceipts": validate_release_evidence(
            args,
            release_directory(args),
            receipt,
            require_execution=True,
        ),
        "recoveredExistingSideEffects": recovered,
        "deploymentCommittedThisRun": deployment_committed,
        "deploymentAlreadyCommitted": not deployment_committed,
        "latestReceiptLinkRepaired": latest_repaired,
        "changeKind": (
            "DEPLOYMENT_COMMIT"
            if deployment_committed
            else (
                "RECEIPT_LINK_REPAIR"
                if latest_repaired
                else "IDEMPOTENT_REVALIDATION"
            )
        ),
        "productionFilesystemChanged": filesystem_changed,
        "productionDatabaseChanged": database_changed_this_run,
        "productionDatabaseChangedThisRun":
            database_changed_this_run,
        "productionDatabaseChangedSinceStage":
            database_changed_since_stage,
        "productionServiceChanged": service_changed,
        "officialExpertsPackageChanged": False,
    }


APPLY_FAILURE_STATES = frozenset(
    (
        "FAIL_CLOSED_BEFORE_APPLICATION_SWITCH",
        "PRE_APPLY_RECOVERY_FAILED_TOPOLOGY_UNCERTAIN",
        "APPLICATION_RESTORED_DATABASE_043_RETAINED_OR_FAIL_CLOSED",
        "APPLICATION_RESTORE_FAILED_TOPOLOGY_UNCERTAIN",
        "DEPLOYMENT_COMMIT_AMBIGUOUS_NO_AUTOMATIC_RESTORE",
        "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
    )
)


def write_apply_failure_receipt(
    args,
    error,
    migration_facts,
    state,
    *,
    application_started,
    topology_restored,
    deployment_commit_outcome,
    service_changed_this_run,
    database_changed_this_run,
):
    if (
        state not in APPLY_FAILURE_STATES
        or type(application_started) is not bool
        or type(topology_restored) is not bool
        or deployment_commit_outcome
            not in {"NOT_ATTEMPTED", "NOT_COMMITTED", "COMMITTED", "AMBIGUOUS"}
        or type(service_changed_this_run) not in {bool, type(None)}
        or type(database_changed_this_run) not in {bool, type(None)}
    ):
        raise RuntimeError("Apply failure receipt semantics are invalid")
    error_identity = "{}:{}".format(
        type(error).__name__, sha256_bytes(str(error).encode("utf-8"))
    )
    timestamp = dt.datetime.now(dt.timezone.utc).strftime(
        "%Y%m%dT%H%M%S%fZ"
    )
    path = release_directory(args) / (
        "apply-failure-{}-{}.json".format(
            timestamp, sha256_bytes(error_identity.encode("utf-8"))[:12]
        )
    )
    deployment_path = release_directory(args) / "deployment-receipt.json"
    deployment_receipt_sha256 = None
    if deployment_path.is_file() and not deployment_path.is_symlink():
        validate_regular_file(
            deployment_path, deployment_path.parent, modes=(0o600,)
        )
        deployment_receipt_sha256 = sha256_file(deployment_path)
    receipt = {
        "schema": APPLY_FAILURE_RECEIPT_SCHEMA,
        "state": state,
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "applyApprovalReceiptSha256": args.approval_sha,
        "errorType": type(error).__name__,
        "errorMessageSha256": sha256_bytes(str(error).encode("utf-8")),
        "migrationFacts": migration_facts,
        "applicationStarted": application_started,
        "applicationAlreadyCommitted": (
            True
            if deployment_commit_outcome == "COMMITTED"
            else (
                None
                if deployment_commit_outcome == "AMBIGUOUS"
                else False
            )
        ),
        "applicationRestored": topology_restored,
        "topologyRestored": topology_restored,
        "deploymentCommitOutcome": deployment_commit_outcome,
        "deploymentReceiptPath": (
            str(deployment_path)
            if deployment_receipt_sha256 is not None else None
        ),
        "deploymentReceiptSha256": deployment_receipt_sha256,
        "productionFilesystemChanged": True,
        "productionServiceChangedThisRun": service_changed_this_run,
        "productionDatabaseChangedThisRun": database_changed_this_run,
        "databaseRollbackStrategy": "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
        "databaseDownClaimed": False,
        "officialExpertsPackageChanged": False,
        "observedAt": utc_now(),
    }
    atomic_json(path, receipt)
    return path


def remove_exact_uncommitted_deployment_receipt(path, receipt):
    """Remove only the exact release-owned marker from an interrupted commit."""
    path = pathlib.Path(path)
    if not path.exists() and not path.is_symlink():
        return False
    validate_regular_file(path, path.parent, modes=(0o600,))
    expected = (canonical_json(receipt) + "\n").encode("utf-8")
    if path.read_bytes() != expected:
        raise RuntimeError(
            "deployment receipt commit outcome is ambiguous"
        )
    path.unlink()
    fsync_directory(path.parent)
    return True


def validate_recoverable_running_migration(stage):
    expected = stage.get("preStageRuntimeIdentity", {})
    if expected.get("w1aDatabaseState") != "ABSENT":
        raise RuntimeError(
            "only an absent staged prestate can recover interrupted 043"
        )
    _, connection = parse_environment()
    mysql = Mysql(connection)
    try:
        validate_apply_session_identity(mysql, connection, expected)
        running = int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s AND description=%s",
                (
                    MIGRATION_PUBLIC_VERSION,
                    "RUNNING:Independent Board exact official experts "
                    "attribution v1",
                ),
            )
        )
        any_public = int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s",
                (MIGRATION_PUBLIC_VERSION,),
            )
        )
        applied = int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s AND description=%s",
                (
                    MIGRATION_PUBLIC_VERSION,
                    MIGRATION_PUBLIC_DESCRIPTION,
                ),
            )
        )
        internal = int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s AND description=%s",
                (
                    MIGRATION_INTERNAL_VERSION,
                    MIGRATION_INTERNAL_DESCRIPTION,
                ),
            )
        )
        tables = {
            str(row[0])
            for row in mysql.rows(
                "SELECT table_name FROM information_schema.tables "
                "WHERE table_schema=DATABASE() AND table_name IN "
                "('fbs_board_attr_journey_v1',"
                "'fbs_board_attr_event_v1')"
            )
        }
        triggers = {
            str(row[0])
            for row in mysql.rows(
                "SELECT trigger_name FROM information_schema.triggers "
                "WHERE trigger_schema=DATABASE() AND event_object_table IN "
                "('fbs_board_attr_journey_v1',"
                "'fbs_board_attr_event_v1')"
            )
        }
        allowed_tables = {
            "fbs_board_attr_journey_v1",
            "fbs_board_attr_event_v1",
        }
        allowed_triggers = {
            "trg_board_attr_event_v1_no_update",
            "trg_board_attr_event_v1_no_delete",
        }
        event_count = (
            int(mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1"
            ))
            if "fbs_board_attr_event_v1" in tables
            else 0
        )
        journey_count = (
            int(mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1"
            ))
            if "fbs_board_attr_journey_v1" in tables
            else 0
        )
        permission_count = int(
            mysql.scalar(
                "SELECT COUNT(*) FROM sys_menu WHERE "
                "BINARY perms=BINARY 'board:attribution:query'"
            )
        )
        if applied == 1 and any_public == 1 and running == 0:
            facts = exact_migration_facts(mysql)
            if (
                not migration_structure_matches(facts)
                or facts.get("eventCount") != 0
                or facts.get("journeyCount") != 0
            ):
                raise RuntimeError(
                    "applied 043 recovery shape is unsafe"
                )
            return "EXACT_APPLIED"
        if (
            running != 1
            or applied != 0
            or any_public != 1
            or internal not in (0, 1)
            or not tables.issubset(allowed_tables)
            or not triggers.issubset(allowed_triggers)
            or event_count != 0
            or journey_count != 0
            or permission_count not in (0, 1)
        ):
            raise RuntimeError("running 043 recovery shape is unsafe")
        return "RUNNING_PARTIAL"
    finally:
        mysql.close()


def apply_release(args):
    release = release_directory(args)
    stage_path, stage = validate_stage_receipt(args, release)
    observed = service_snapshot(require_active=False)
    deployment_path = release / "deployment-receipt.json"
    if deployment_path.exists():
        validate_regular_file(deployment_path, release, modes=(0o600,))
        existing = read_json(deployment_path)
        if (
            existing.get("schema")
            != DEPLOYMENT_RECEIPT_SCHEMA
            or existing.get("state") != "DEPLOYED_DEFAULT_OFF"
            or existing.get("releaseId") != args.release_id
            or existing.get("sourceCommit") != args.source_commit
            or existing.get(
                "adminRootDependencyAdoptionReceiptSha256"
            ) != args.admin_root_dependency_adoption_receipt_sha
            or existing.get("stageReceiptSha256")
            != sha256_file(stage_path)
            or not SHA_PATTERN.fullmatch(
                str(existing.get("applyApprovalReceiptSha256") or "")
            )
            or type(
                existing.get("productionDatabaseChangedThisRun")
            ) is not bool
            or type(
                existing.get("productionDatabaseChangedSinceStage")
            ) is not bool
            or existing.get("productionDatabaseChanged")
                != existing.get(
                    "productionDatabaseChangedSinceStage"
                )
        ):
            raise RuntimeError("existing deployment receipt identity drifted")
        validate_final_default_off_current_read(
            existing.get("finalDefaultOffCurrentRead"),
            existing.get("serviceAfter"),
            existing.get("migrationFacts"),
            existing.get("schema"),
        )
        migration_lease = None
        try:
            migration_lease = deployed_migration_lease(
                args, release, existing
            )
            validate_release_evidence(
                args, release, existing, require_execution=True
            )
            assert_candidate_active(args, release)
            validate_apply_session_identity(
                migration_lease.mysql,
                migration_lease.connection,
                stage["preStageRuntimeIdentity"],
            )
            current_migration = exact_migration_facts(
                migration_lease.mysql
            )
            if not migration_counts_are_monotonic(
                current_migration, migration_lease.facts
            ):
                raise RuntimeError(
                    "existing deployment database evidence drifted"
                )
            final_default_off_current_read(
                args,
                release,
                migration_lease.mysql,
                current_migration,
            )
        except Exception as error:
            try:
                failure_path = write_apply_failure_receipt(
                    args,
                    error,
                    None,
                    "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
                    application_started=False,
                    topology_restored=False,
                    deployment_commit_outcome="COMMITTED",
                    service_changed_this_run=False,
                    database_changed_this_run=False,
                )
            except Exception as receipt_error:
                raise RuntimeError(
                    "existing deployment validation failed without topology "
                    "mutation and failure receipt write also failed: "
                    + type(receipt_error).__name__
                ) from error
            raise ReleaseMutationFailure(
                "existing deployment validation failed without topology "
                "mutation; use explicit Rollback for any service switch",
                failure_path,
            ) from error
        finally:
            if migration_lease is not None:
                migration_lease.close()
        try:
            latest_changed = advance_latest_receipt(
                deployment_path, [stage_path]
            )
        except Exception as error:
            failure_path = write_apply_failure_receipt(
                args,
                error,
                None,
                "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
                application_started=False,
                topology_restored=False,
                deployment_commit_outcome="COMMITTED",
                service_changed_this_run=False,
                database_changed_this_run=False,
            )
            raise ReleaseMutationFailure(
                "deployment receipt is valid but latest receipt link "
                "repair failed",
                failure_path,
            ) from error
        return apply_worker_result(
            args,
            deployment_path,
            filesystem_changed=latest_changed,
            database_changed_this_run=False,
            database_changed_since_stage=bool(
                existing.get(
                    "productionDatabaseChangedSinceStage",
                    existing.get("productionDatabaseChanged"),
                )
            ),
            service_changed=False,
            deployment_committed=False,
            latest_repaired=latest_changed,
            recovered=True,
        )
    validate_preparation_anchors(args)
    validate_pre_apply_nginx_target(args, release)
    recovered_existing_side_effects = False
    try:
        validate_stage_runtime_identity(args, stage)
    except RuntimeError:
        validate_recoverable_running_migration(stage)
        recovered_existing_side_effects = True
    try:
        assert_predecessor_topology(release, observed)
        if observed["activeState"] != "active":
            restore_application(release)
            recovered_existing_side_effects = True
        before = assert_predecessor_active(release)
    except Exception as topology_error:
        try:
            assert_release_owned_recovery_topology(args, release)
            restore_application(release)
            before = assert_predecessor_active(release)
            recovered_existing_side_effects = True
        except Exception as restore_error:
            failure_path = write_apply_failure_receipt(
                args,
                restore_error,
                None,
                "PRE_APPLY_RECOVERY_FAILED_TOPOLOGY_UNCERTAIN",
                application_started=False,
                topology_restored=False,
                deployment_commit_outcome="NOT_ATTEMPTED",
                service_changed_this_run=None,
                database_changed_this_run=False,
            )
            raise ReleaseMutationFailure(
                "release-owned recovery classification or immutable "
                "predecessor restore failed",
                failure_path,
            ) from topology_error
    application_started = False
    migration_facts = None
    receipt = None
    deployment_committed = False
    migration_lease = None
    try:
        migration_lease = apply_migration(
            args, release, stage["preStageRuntimeIdentity"]
        )
        migration_facts = migration_lease.facts
        validate_apply_session_identity(
            migration_lease.mysql,
            migration_lease.connection,
            stage["preStageRuntimeIdentity"],
        )
        application_started = True
        candidate_before_rollback = install_candidate_application(
            args, release
        )
        if candidate_before_rollback["invocationId"] == before["invocationId"]:
            raise RuntimeError("candidate service invocation did not change")
        restore_application(release)
        predecessor_after_rollback = assert_predecessor_active(release)
        retained_after_rollback = exact_migration_facts(
            migration_lease.mysql
        )
        if retained_after_rollback != migration_facts:
            raise RuntimeError(
                "043 current-read drifted during application rollback"
            )
        after = install_candidate_application(args, release)
        rollback_execution_path = application_rollback_execution_receipt(
            args,
            release,
            migration_facts,
            candidate_before_rollback,
            predecessor_after_rollback,
            after,
        )
        after, final_current_read = final_default_off_current_read(
            args,
            release,
            migration_lease.mysql,
            migration_facts,
        )
        receipt = deployment_receipt(
            args,
            stage_path,
            stage,
            migration_facts,
            before,
            after,
            rollback_execution_path,
            migration_lease.database_changed_this_run,
            migration_lease.database_changed_since_stage,
            final_current_read,
        )
        validate_release_evidence(
            args, release, receipt, require_execution=True
        )
        validate_apply_session_identity(
            migration_lease.mysql,
            migration_lease.connection,
            stage["preStageRuntimeIdentity"],
        )
        if exact_migration_facts(migration_lease.mysql) != migration_facts:
            raise RuntimeError(
                "043 final current-read drifted before receipt commit"
            )
        after, final_current_read = final_default_off_current_read(
            args,
            release,
            migration_lease.mysql,
            migration_facts,
        )
        receipt = deployment_receipt(
            args,
            stage_path,
            stage,
            migration_facts,
            before,
            after,
            rollback_execution_path,
            migration_lease.database_changed_this_run,
            migration_lease.database_changed_since_stage,
            final_current_read,
        )
        atomic_json(deployment_path, receipt)
        deployment_committed = True
    except Exception as error:
        if (
            not deployment_committed
            and receipt is not None
            and (deployment_path.exists() or deployment_path.is_symlink())
        ):
            try:
                remove_exact_uncommitted_deployment_receipt(
                    deployment_path, receipt
                )
            except Exception as cleanup_error:
                failure_path = write_apply_failure_receipt(
                    args,
                    cleanup_error,
                    migration_facts,
                    "DEPLOYMENT_COMMIT_AMBIGUOUS_NO_AUTOMATIC_RESTORE",
                    application_started=True,
                    topology_restored=False,
                    deployment_commit_outcome="AMBIGUOUS",
                    service_changed_this_run=True,
                    database_changed_this_run=(
                        migration_lease.database_changed_this_run
                        if migration_lease is not None else None
                    ),
                )
                raise ReleaseMutationFailure(
                    "deployment commit outcome is ambiguous; automatic "
                    "application restore was refused",
                    failure_path,
                ) from error
        restored = False
        if application_started:
            try:
                assert_release_owned_recovery_topology(args, release)
                restore_application(release)
                assert_predecessor_active(release)
                restored = True
            except Exception as restore_error:
                failure_path = write_apply_failure_receipt(
                    args,
                    restore_error,
                    migration_facts,
                    "APPLICATION_RESTORE_FAILED_TOPOLOGY_UNCERTAIN",
                    application_started=True,
                    topology_restored=False,
                    deployment_commit_outcome="NOT_COMMITTED",
                    service_changed_this_run=True,
                    database_changed_this_run=(
                        migration_lease.database_changed_this_run
                        if migration_lease is not None else None
                    ),
                )
                raise ReleaseMutationFailure(
                    "release failed and immutable predecessor restore failed: "
                    + type(restore_error).__name__,
                    failure_path,
                ) from error
        failure_path = write_apply_failure_receipt(
            args,
            error,
            migration_facts,
            (
                "APPLICATION_RESTORED_DATABASE_043_"
                "RETAINED_OR_FAIL_CLOSED"
                if restored
                else "FAIL_CLOSED_BEFORE_APPLICATION_SWITCH"
            ),
            application_started=application_started,
            topology_restored=restored,
            deployment_commit_outcome="NOT_COMMITTED",
            service_changed_this_run=(
                True if application_started else False
            ),
            database_changed_this_run=(
                migration_lease.database_changed_this_run
                if migration_lease is not None else None
            ),
        )
        raise ReleaseMutationFailure(
            "Apply failed with a release-owned failure receipt",
            failure_path,
        ) from error
    finally:
        if migration_lease is not None:
            migration_lease.close()
    try:
        latest_changed = advance_latest_receipt(
            deployment_path, [stage_path]
        )
    except Exception as error:
        failure_path = write_apply_failure_receipt(
            args,
            error,
            migration_lease.facts,
            "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
            application_started=True,
            topology_restored=False,
            deployment_commit_outcome="COMMITTED",
            service_changed_this_run=True,
            database_changed_this_run=
                migration_lease.database_changed_this_run,
        )
        raise ReleaseMutationFailure(
            "deployment receipt committed but latest receipt link repair "
            "failed",
            failure_path,
        ) from error
    return apply_worker_result(
        args,
        deployment_path,
        filesystem_changed=True,
        database_changed_this_run=
            migration_lease.database_changed_this_run,
        database_changed_since_stage=
            migration_lease.database_changed_since_stage,
        service_changed=True,
        deployment_committed=True,
        latest_repaired=latest_changed,
        recovered=recovered_existing_side_effects,
    )


def anchored_evidence_json(
    release,
    receipt,
    path_field,
    digest_field,
    relative_path,
):
    expected = release / relative_path
    if receipt.get(path_field) != str(expected):
        raise RuntimeError("nested release evidence path drifted")
    validate_regular_file(expected, expected.parent, modes=(0o600,))
    digest = receipt.get(digest_field)
    if (
        not isinstance(digest, str)
        or not SHA_PATTERN.fullmatch(digest)
        or sha256_file(expected) != digest
    ):
        raise RuntimeError("nested release evidence digest drifted")
    return expected, read_json(expected)


def assert_candidate_snapshot_evidence(args, release, snapshot):
    candidate_jar = str(CURRENT_LINK / "backend/fbsir-admin.jar")
    if (
        not isinstance(snapshot, dict)
        or snapshot.get("configuredJarPath") != candidate_jar
        or snapshot.get("processJarPath") != candidate_jar
        or snapshot.get("configuredJarSha256") != args.backend_sha
        or snapshot.get("processJarSha256") != args.backend_sha
        or snapshot.get("jarSha256") != args.backend_sha
        or snapshot.get("activeState") != "active"
    ):
        raise RuntimeError("candidate rollback snapshot identity drifted")
    return assert_candidate_runtime_contract(args, release, snapshot)


def validate_release_evidence(
    args, release, receipt, require_execution
):
    deployment_schema = receipt.get("schema")
    contract_schemas = deployment_contract_schemas(deployment_schema)
    assembly_path, assembly = anchored_evidence_json(
        release,
        receipt,
        "applicationRollbackAssemblyReceiptPath",
        "applicationRollbackAssemblyReceiptSha256",
        "evidence/application-rollback-assembly.json",
    )
    predecessor = predecessor_facts(release)
    if (
        assembly != predecessor
        or assembly.get("releaseId") != args.release_id
        or assembly.get("sourceCommit") != args.source_commit
        or assembly.get("symlinkForwardAndReverseVerified") is not True
        or assembly.get("productionServiceChanged") is not False
        or assembly.get("productionDatabaseChanged") is not False
        or assembly.get("stageApprovalReceiptSha256")
            != receipt.get("stageApprovalReceiptSha256")
        or assembly.get("releasePlanTargetSha256")
            != receipt.get("releasePlanTargetSha256")
        or assembly.get("releasePlanTargetComparableSha256")
            != receipt.get("releasePlanTargetComparableSha256")
        or assembly.get(
            "finalizeStageLiveTargetComparableSha256"
        ) != receipt.get(
            "finalizeStageLiveTargetComparableSha256"
        )
        or assembly.get(
            "stageOwnedReleaseRootDeltaVerified"
        ) is not True
        or any(
            not SHA_PATTERN.fullmatch(
                str(receipt.get(field) or "")
            )
            for field in (
                "releasePlanTargetSha256",
                "releasePlanTargetComparableSha256",
                "finalizeStageLiveTargetComparableSha256",
            )
        )
        or receipt.get("releasePlanTargetComparableSha256")
            != receipt.get(
                "finalizeStageLiveTargetComparableSha256"
            )
        or receipt.get(
            "stageOwnedReleaseRootDeltaVerified"
        ) is not True
        or assembly.get("preStageApplicationState") not in {
            "UNTOUCHED_LEGACY",
            "EXACT_PRIOR_ROLLBACK_PREDECESSOR",
            INTERRUPTED_APPLY_RECOVERY_TOPOLOGY_STATE,
        }
        or (
            assembly.get("preStageApplicationState")
                == "UNTOUCHED_LEGACY"
            and (
                assembly.get("priorRollbackAnchor") is not None
                or assembly.get("priorRecoveryAnchor") is not None
            )
        )
        or (
            assembly.get("preStageApplicationState")
                == "EXACT_PRIOR_ROLLBACK_PREDECESSOR"
            and (
                not isinstance(
                    assembly.get("priorRollbackAnchor"), dict
                )
                or assembly["priorRollbackAnchor"].get("schema")
                    != "fbsir.u3wPriorRollbackStageAnchor.v1"
                or assembly["priorRollbackAnchor"].get("state")
                    != (
                        "ROLLED_BACK_APPLICATION_DATABASE_043_"
                        "RETAINED_DORMANT"
                    )
                or assembly.get("priorRecoveryAnchor") is not None
            )
        )
        or (
            assembly.get("preStageApplicationState")
                == INTERRUPTED_APPLY_RECOVERY_TOPOLOGY_STATE
            and (
                assembly.get("priorRollbackAnchor") is not None
                or not isinstance(
                    assembly.get("priorRecoveryAnchor"), dict
                )
                or assembly["priorRecoveryAnchor"].get("schema")
                    != (
                        "fbsir.u3wPriorInterruptedApplyRecovery"
                        "StageAnchor.v1"
                    )
                or assembly["priorRecoveryAnchor"].get(
                    "receiptSchema"
                ) != INTERRUPTED_APPLY_RECOVERY_RECEIPT_SCHEMA
                or assembly["priorRecoveryAnchor"].get("state")
                    != INTERRUPTED_APPLY_RECOVERY_STATE
            )
        )
    ):
        raise RuntimeError("application rollback assembly evidence drifted")
    database_path, database = anchored_evidence_json(
        release,
        receipt,
        "databaseRollbackSafetyReceiptPath",
        "databaseRollbackSafetyReceiptSha256",
        "evidence/database-rollback-safety.json",
    )
    database_prestate = database.get("preDeploymentDatabaseState")
    retained_prestate = database.get(
        "preDeploymentRetainedMigrationFacts"
    )
    staged_runtime = receipt.get("preStageRuntimeIdentity", {})
    database_prestate_valid = bool(
        (
            database_prestate == "ABSENT"
            and database.get("preDeploymentPublic043Absent") is True
            and database.get(
                "preDeploymentAttributionTablesAbsent"
            ) is True
            and retained_prestate is None
        )
        or (
            database_prestate == "EXACT_043_RETAINED_DORMANT"
            and database.get("preDeploymentPublic043Absent") is False
            and database.get(
                "preDeploymentAttributionTablesAbsent"
            ) is False
            and recorded_migration_facts_match_runtime_identity(
                retained_prestate,
                staged_runtime,
                deployment_schema,
            )
        )
    )
    if (
        database.get("schema")
            != contract_schemas["databaseRollbackSafety"]
        or database.get("releaseId") != args.release_id
        or database.get("sourceCommit") != args.source_commit
        or database.get("stageApprovalReceiptSha256")
            != receipt.get("stageApprovalReceiptSha256")
        or database.get("verified") is not True
        or database.get("strategy")
            != "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
        or database.get("migrationSha256") != args.migration_sha
        or not database_prestate_valid
        or staged_runtime.get("w1aDatabaseState") != database_prestate
        or database.get("legacyJarAttributionClassCount") != 0
        or database.get("allW1aFlagsExplicitFalse") is not True
        or database.get("databaseDownClaimed") is not False
        or database.get("productionDatabaseChanged") is not False
    ):
        raise RuntimeError("database rollback safety evidence drifted")
    anchors = [
        {
            "name": "application-rollback-assembly",
            "path": str(assembly_path),
            "sha256": sha256_file(assembly_path),
            "schema": assembly["schema"],
        },
        {
            "name": "database-rollback-safety",
            "path": str(database_path),
            "sha256": sha256_file(database_path),
            "schema": database["schema"],
        },
    ]
    if not require_execution:
        if receipt.get("applicationRollbackProven") is not False:
            raise RuntimeError(
                "Stage cannot claim application rollback execution"
            )
        return anchors
    active_nginx, nginx_dump_sha256 = active_nginx_manifest()
    if (
        receipt.get("activeNginxManifestAfterApply") != active_nginx
        or receipt.get("nginxDumpSha256AfterApply")
            != nginx_dump_sha256
    ):
        raise RuntimeError("deployed active Nginx evidence drifted")
    execution_path, execution = anchored_evidence_json(
        release,
        receipt,
        "applicationRollbackReceiptPath",
        "applicationRollbackReceiptSha256",
        "evidence/application-rollback-execution.json",
    )
    candidate_before = execution.get("candidateBeforeRollback")
    predecessor_after = execution.get("predecessorAfterRollback")
    candidate_after = execution.get("candidateAfterReapply")
    assert_candidate_snapshot_evidence(
        args, release, candidate_before
    )
    assert_restored_predecessor_runtime_contract(
        release, predecessor_after
    )
    assert_candidate_snapshot_evidence(args, release, candidate_after)
    invocation_ids = {
        candidate_before.get("invocationId"),
        predecessor_after.get("invocationId"),
        candidate_after.get("invocationId"),
    }
    if (
        execution.get("schema")
            != contract_schemas["applicationRollbackExecution"]
        or execution.get("releaseId") != args.release_id
        or execution.get("sourceCommit") != args.source_commit
        or execution.get("applyApprovalReceiptSha256")
            != receipt.get("applyApprovalReceiptSha256")
        or execution.get("strategy")
            != "LIVE_CANDIDATE_TO_IMMUTABLE_PREDECESSOR_TO_CANDIDATE"
        or execution.get("verified") is not True
        or execution.get("database043Retained") is not True
        or execution.get("retainedMigrationFacts")
            != receipt.get("migrationFacts")
        or execution.get("productionServiceChanged") is not True
        or execution.get("productionDatabaseChanged") is not False
        or execution.get("officialExpertsPackageChanged") is not False
        or len(invocation_ids) != 3
        or any(
            not re.fullmatch(r"[0-9a-f]{32}", str(value or ""))
            for value in invocation_ids
        )
    ):
        raise RuntimeError(
            "application rollback execution evidence drifted"
        )
    anchors.append(
        {
            "name": "application-rollback-execution",
            "path": str(execution_path),
            "sha256": sha256_file(execution_path),
            "schema": execution["schema"],
        }
    )
    return anchors


def validate_deployment_receipt(args, release):
    path = release / "deployment-receipt.json"
    validate_regular_file(path, release, modes=(0o600,))
    if sha256_file(path) != args.deployment_receipt_sha:
        raise RuntimeError("deployment receipt anchor drifted")
    receipt = read_json(path)
    receipt_schema = receipt.get("schema")
    if (
        receipt_schema not in {
            LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
            DEPLOYMENT_RECEIPT_SCHEMA,
        }
        or receipt.get("state") != "DEPLOYED_DEFAULT_OFF"
        or receipt.get("releaseId") != args.release_id
        or receipt.get("sourceCommit") != args.source_commit
        or receipt.get("adminRootDependencyAdoptionReceiptSha256")
            != args.admin_root_dependency_adoption_receipt_sha
        or receipt.get("applicationRollbackProven") is not True
        or receipt.get("databaseRollbackSafetyProven") is not True
        or receipt.get("databaseDownClaimed") is not False
        or not SHA_PATTERN.fullmatch(
            str(receipt.get("applyApprovalReceiptSha256") or "")
        )
        or type(
            receipt.get("productionDatabaseChangedThisRun")
        ) is not bool
        or type(
            receipt.get("productionDatabaseChangedSinceStage")
        ) is not bool
        or receipt.get("productionDatabaseChanged")
            != receipt.get("productionDatabaseChangedSinceStage")
        or not recorded_migration_facts_valid(
            receipt.get("migrationFacts"), receipt_schema
        )
    ):
        raise RuntimeError("deployment receipt identity is invalid")
    validate_final_default_off_current_read(
        receipt.get("finalDefaultOffCurrentRead"),
        receipt.get("serviceAfter"),
        receipt.get("migrationFacts"),
        receipt.get("schema"),
    )
    validate_staged_release_artifacts(args, release, receipt)
    validate_release_evidence(
        args, release, receipt, require_execution=True
    )
    return path, receipt


def verify_release(args):
    release = release_directory(args)
    deployment_path, deployment = validate_deployment_receipt(args, release)
    current = assert_candidate_active(args, release)
    migration_lease = None
    try:
        migration_lease = deployed_migration_lease(
            args, release, deployment
        )
        migration = migration_lease.facts
        current, _ = final_default_off_current_read(
            args,
            release,
            migration_lease.mysql,
            migration,
        )
        frontend = tree_manifest(release / "frontend")
        verified = bool(
            CURRENT_LINK.resolve() == release.resolve()
            and current["jarSha256"] == args.backend_sha
            and frontend["sha256"] == args.frontend_tree_sha
            and recorded_migration_counts_are_monotonic(
                migration,
                deployment.get("migrationFacts"),
                deployment.get("schema"),
            )
            and sha256_file(
                release / "evidence/frontend-manifest.txt"
            )
            == args.frontend_tree_sha
            and sha256_file(NGINX_PATH) == deployment.get("nginxSha256")
        )
        if not verified:
            raise RuntimeError(
                "deployed default-off release verification failed"
            )
        return {
            "schema": WORKER_RESULT_SCHEMA,
            "mode": "Verify",
            "state": "DEPLOYED_DEFAULT_OFF",
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "approvalReceiptSha256": args.approval_sha,
            "transitionApprovalReceiptSha256":
                deployment.get("applyApprovalReceiptSha256"),
            "deploymentReceiptPath": str(deployment_path),
            "deploymentReceiptSha256": sha256_file(deployment_path),
            "evidenceReceipts": validate_release_evidence(
                args, release, deployment, require_execution=True
            ),
            "migrationFacts": migration,
            "verified": True,
            "actualActiveArtifactsMatched": True,
            "databaseRollbackSafetyProven": True,
            "databaseDownClaimed": False,
            "productionFilesystemChanged": False,
            "productionDatabaseChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
            "observedAt": utc_now(),
        }
    finally:
        if migration_lease is not None:
            migration_lease.close()


def read_exact_migration_facts():
    _, connection = parse_environment()
    mysql = Mysql(connection)
    try:
        facts = exact_migration_facts(mysql)
    finally:
        mysql.close()
    expected = {
        "publicReceiptCount": 1,
        "internalReceiptCount": 1,
        "tableCount": 2,
        "triggerCount": 2,
        "permissionCount": 1,
        "schemaFingerprintSha256": EXPECTED_W1A_SCHEMA_FINGERPRINT,
    }
    if any(facts.get(key) != value for key, value in expected.items()):
        raise RuntimeError("retained 043 current-read failed")
    return facts


def interrupted_apply_target_release(args):
    target_release_id = getattr(args, "target_release_id", "")
    target_source_commit = getattr(args, "target_source_commit", "")
    if (
        not RUN_PATTERN.fullmatch(target_release_id)
        or not COMMIT_PATTERN.fullmatch(target_source_commit)
        or target_release_id == args.release_id
        or target_source_commit == args.source_commit
    ):
        raise RuntimeError("interrupted Apply target identity is invalid")
    release = RELEASE_ROOT / target_release_id
    try:
        status = release.lstat()
        root = RELEASE_ROOT.resolve(strict=True)
        resolved = release.resolve(strict=True)
    except OSError as error:
        raise RuntimeError(
            "interrupted Apply target release is absent"
        ) from error
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink < 2
        or status.st_mode & 0o022
        or resolved.parent != root
    ):
        raise RuntimeError(
            "interrupted Apply target release custody is invalid"
        )
    return release


def interrupted_apply_failure_manifest(args, release):
    expected_digest = getattr(
        args, "apply_failure_receipt_sha", ""
    )
    if not SHA_PATTERN.fullmatch(expected_digest):
        raise RuntimeError("interrupted Apply failure anchor is invalid")
    manifest = []
    terminal_path = None
    terminal = None
    for path in sorted(release.glob("apply-failure-*.json")):
        if not APPLY_FAILURE_NAME_PATTERN.fullmatch(path.name):
            raise RuntimeError("interrupted Apply failure name is invalid")
        validate_regular_file(path, release, modes=(0o600,))
        digest = sha256_file(path)
        receipt = read_json(path)
        if (
            receipt.get("schema")
            not in {
                LEGACY_APPLY_FAILURE_RECEIPT_SCHEMA,
                APPLY_FAILURE_RECEIPT_SCHEMA,
            }
            or receipt.get("state") not in APPLY_FAILURE_STATES
            or receipt.get("releaseId") != release.name
            or receipt.get("sourceCommit")
                != args.target_source_commit
            or receipt.get("officialExpertsPackageChanged") is not False
            or not SHA_PATTERN.fullmatch(
                str(
                    receipt.get(
                        "applyApprovalReceiptSha256"
                    ) or ""
                )
            )
        ):
            raise RuntimeError(
                "interrupted Apply failure manifest is invalid"
            )
        manifest.append({
            "name": path.name,
            "sha256": digest,
            "schema": receipt["schema"],
            "state": receipt["state"],
        })
        if hmac.compare_digest(digest, expected_digest):
            if terminal_path is not None:
                raise RuntimeError(
                    "interrupted Apply terminal failure is ambiguous"
                )
            terminal_path = path
            terminal = receipt
    if terminal_path is None or terminal is None:
        raise RuntimeError(
            "interrupted Apply terminal failure is absent"
        )
    facts = terminal.get("migrationFacts")
    terminal_schema = terminal.get("schema")
    migration_facts_valid = (
        legacy_migration_structure_matches(facts)
        and facts.get("eventCount") == 0
        and facts.get("journeyCount") == 0
        if terminal_schema == LEGACY_APPLY_FAILURE_RECEIPT_SCHEMA
        else (
            migration_structure_matches(facts)
            if terminal_schema == APPLY_FAILURE_RECEIPT_SCHEMA
            else False
        )
    )
    v2_change_facts_valid = bool(
        terminal_schema != APPLY_FAILURE_RECEIPT_SCHEMA
        or (
            terminal.get("productionServiceChangedThisRun") is True
            and type(
                terminal.get("productionDatabaseChangedThisRun")
            ) is bool
        )
    )
    if (
        terminal.get("state")
            != (
                "APPLICATION_RESTORED_DATABASE_043_"
                "RETAINED_OR_FAIL_CLOSED"
            )
        or terminal.get("applicationStarted") is not True
        or terminal.get("applicationAlreadyCommitted") is not False
        or terminal.get("applicationRestored") is not True
        or terminal.get("topologyRestored") is not True
        or terminal.get("deploymentCommitOutcome") != "NOT_COMMITTED"
        or terminal.get("deploymentReceiptPath") is not None
        or terminal.get("deploymentReceiptSha256") is not None
        or terminal.get("databaseRollbackStrategy")
            != "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
        or terminal.get("databaseDownClaimed") is not False
        or not migration_facts_valid
        or not v2_change_facts_valid
    ):
        raise RuntimeError(
            "interrupted Apply terminal failure is not recoverable"
        )
    return terminal_path, terminal, manifest


def validate_interrupted_apply_stage(args, release):
    stage_path = release / "deployment-readiness-receipt.json"
    validate_regular_file(stage_path, release, modes=(0o600,))
    if sha256_file(stage_path) != args.stage_receipt_sha:
        raise RuntimeError("interrupted Apply Stage anchor drifted")
    stage = read_json(stage_path)
    stage_schema = stage.get("schema")
    current_stage_invariants_invalid = bool(
        stage_schema == DEPLOYMENT_RECEIPT_SCHEMA
        and (
            stage.get("databaseRollbackSafetyProven") is not True
            or stage.get("actualActiveArtifactsMatched") is not False
            or stage.get("productionDatabaseChangedThisRun") is not False
            or stage.get("productionDatabaseChangedSinceStage") is not False
        )
    )
    if (
        stage_schema
            not in {
                LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
                DEPLOYMENT_RECEIPT_SCHEMA,
            }
        or stage.get("state") != "STAGED_FOR_SWITCH"
        or stage.get("releaseId") != args.target_release_id
        or stage.get("sourceCommit") != args.target_source_commit
        or not SHA_PATTERN.fullmatch(
            str(stage.get("stageApprovalReceiptSha256") or "")
        )
        or stage.get("databaseDownClaimed") is not False
        or stage.get("productionDatabaseChanged") is not False
        or stage.get("productionServiceChanged") is not False
        or stage.get("officialExpertsPackageChanged") is not False
        or current_stage_invariants_invalid
    ):
        raise RuntimeError("interrupted Apply Stage identity is invalid")
    return stage_path, stage


def assert_predecessor_owned_topology(
    release,
    *,
    allow_undersized_token_secret=False,
):
    previous = predecessor_facts(release)
    after = service_snapshot(
        require_active=False,
        allow_undersized_token_secret=allow_undersized_token_secret,
    )
    rollback_dropin = release / "evidence/rollback-systemd-dropin.conf"
    if (
        after["jarSha256"] != previous["previousJarSha256"]
        or after["configuredJarSha256"]
            != previous["previousJarSha256"]
        or CURRENT_LINK.exists()
        or CURRENT_LINK.is_symlink()
        or not DROPIN_PATH.is_file()
        or DROPIN_PATH.is_symlink()
        or sha256_file(DROPIN_PATH) != sha256_file(rollback_dropin)
        or sha256_file(NGINX_PATH) != previous["previousNginxSha256"]
    ):
        raise RuntimeError("immutable predecessor current-read failed")
    return after


def assert_predecessor_active(
    release,
    *,
    allow_undersized_token_secret=False,
):
    after = assert_predecessor_owned_topology(
        release,
        allow_undersized_token_secret=allow_undersized_token_secret,
    )
    if after["activeState"] != "active":
        raise RuntimeError("immutable predecessor service is not active")
    assert_restored_predecessor_runtime_contract(release, after)
    wait_for_u3w_health()
    return after


def interrupted_apply_recovery_lease():
    """Hold the 043 named lock until the recovery receipt CAS commits."""
    _, connection = parse_environment(
        allow_undersized_token_secret=True
    )
    mysql = Mysql(connection)
    lock_acquired = False
    try:
        if int(mysql.scalar(
            "SELECT GET_LOCK('u3w:w1a:public_init_043',0)"
        )) != 1:
            raise RuntimeError(
                "interrupted recovery migration lock is unavailable"
            )
        lock_acquired = True
        facts = exact_migration_facts(mysql)
        if not migration_structure_matches(facts):
            raise RuntimeError(
                "interrupted recovery retained 043 is not dormant"
            )
        return MigrationLease(mysql, connection, facts)
    except Exception:
        if lock_acquired:
            try:
                mysql.scalar(
                    "SELECT RELEASE_LOCK('u3w:w1a:public_init_043')"
                )
            except Exception:
                pass
        mysql.close()
        raise


def interrupted_recovery_runtime_matches(
    service,
    facts,
    terminal,
):
    terminal_schema = terminal.get("schema")
    migration_schema = (
        LEGACY_DEPLOYMENT_RECEIPT_SCHEMA
        if terminal_schema == LEGACY_APPLY_FAILURE_RECEIPT_SCHEMA
        else (
            DEPLOYMENT_RECEIPT_SCHEMA
            if terminal_schema == APPLY_FAILURE_RECEIPT_SCHEMA
            else None
        )
    )
    return bool(
        migration_structure_matches(facts)
        and recorded_migration_counts_are_monotonic(
            facts,
            terminal.get("migrationFacts"),
            migration_schema,
        )
        and service.get("activeState") == "active"
        and service.get("processForbiddenOverrideNames") == []
        and all(
            service.get("configuredFlagValues", {}).get(name)
                == "false"
            and service.get("processFlagValues", {}).get(name)
                == "false"
            for name in FALSE_FLAGS
        )
        and environment_flags_explicit_false() is True
    )


def interrupted_recovery_observed_at_valid(receipt, terminal):
    try:
        observed = dt.datetime.fromisoformat(
            str(receipt.get("observedAt") or "").replace(
                "Z", "+00:00"
            )
        )
        failed = dt.datetime.fromisoformat(
            str(terminal.get("observedAt") or "").replace(
                "Z", "+00:00"
            )
        )
    except ValueError:
        return False
    return bool(
        observed.tzinfo is not None
        and failed.tzinfo is not None
        and observed > failed
    )


def interrupted_recovery_receipt(
    args,
    approval,
    release,
    stage_path,
    terminal_path,
    terminal,
    failure_manifest,
    failure_manifest_sha256,
    facts,
    observed_at,
):
    database_changed_since_stage = (
        terminal.get("productionDatabaseChangedThisRun")
        if terminal.get("schema") == APPLY_FAILURE_RECEIPT_SCHEMA
        else True
    )
    if type(database_changed_since_stage) is not bool:
        raise RuntimeError(
            "interrupted Apply database change evidence is invalid"
        )
    return {
        "schema": INTERRUPTED_APPLY_RECOVERY_RECEIPT_SCHEMA,
        "state": INTERRUPTED_APPLY_RECOVERY_STATE,
        "releaseId": release.name,
        "sourceCommit": args.target_source_commit,
        "recoveryRunId": args.release_id,
        "executorSourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "approvalNonce": approval["approvalNonce"],
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "recoveryPlanReceiptSha256":
            args.recovery_plan_receipt_sha,
        "stageReceiptPath": str(stage_path),
        "stageReceiptSha256": args.stage_receipt_sha,
        "applyFailureReceiptPath": str(terminal_path),
        "applyFailureReceiptSha256":
            args.apply_failure_receipt_sha,
        "applyFailureReceiptSchema": terminal["schema"],
        "applyFailureReceiptState": terminal["state"],
        "applyFailureReceiptManifest": failure_manifest,
        "applyFailureReceiptManifestSha256":
            failure_manifest_sha256,
        "applicationRestored": True,
        "topologyRestored": True,
        "deploymentCommitOutcome": "NOT_COMMITTED",
        "deploymentReceiptAbsent": True,
        "rollbackReceiptAbsent": True,
        "currentLinkAbsent": True,
        "releaseDropInMatched": True,
        "retainedMigrationFacts": facts,
        "allW1aFlagsExplicitFalse": True,
        "databaseDownClaimed": False,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": database_changed_since_stage,
        "productionDatabaseChangedThisRecoveryRun": False,
        "productionDatabaseChangedSinceStage":
            database_changed_since_stage,
        "productionServiceChanged": True,
        "productionServiceChangedThisRecoveryRun": False,
        "productionServiceChangedSinceStage": True,
        "officialExpertsPackageChanged": False,
        "observedAt": observed_at,
    }


def existing_interrupted_recovery_matches(
    existing,
    expected,
    current_facts,
    terminal,
):
    recorded = existing.get("retainedMigrationFacts")
    replay_expected = dict(expected)
    replay_expected["retainedMigrationFacts"] = recorded
    replay_expected["observedAt"] = existing.get("observedAt")
    return bool(
        set(expected) == INTERRUPTED_APPLY_RECOVERY_RECEIPT_FIELDS
        and set(existing) == INTERRUPTED_APPLY_RECOVERY_RECEIPT_FIELDS
        and existing == replay_expected
        and migration_structure_matches(recorded)
        and recorded_migration_counts_are_monotonic(
            current_facts,
            recorded,
            DEPLOYMENT_RECEIPT_SCHEMA,
        )
        and interrupted_recovery_observed_at_valid(
            existing, terminal
        )
    )


def create_or_validate_interrupted_recovery_anchor(
    path,
    raw,
    expected_sha256,
    allow_create=True,
):
    path = pathlib.Path(path)
    if path.exists() or path.is_symlink():
        validate_regular_file(path, path.parent, modes=(0o600,))
        if (
            sha256_file(path) != expected_sha256
            or path.read_bytes() != raw
        ):
            raise RuntimeError(
                "interrupted recovery immutable anchor drifted"
            )
        return False
    if not allow_create:
        raise RuntimeError(
            "interrupted recovery immutable anchor is absent"
        )
    atomic_bytes_create_new(path, raw, 0o600)
    return True


def inspect_interrupted_apply_recovery(args):
    release = interrupted_apply_target_release(args)
    stage_path, _ = validate_interrupted_apply_stage(args, release)
    recovery_path = (
        release / "interrupted-apply-recovery-receipt.json"
    )
    if any(
        path.exists() or path.is_symlink()
        for path in (
            release / "deployment-receipt.json",
            release / "rollback-receipt.json",
            release / "rollback-verification-receipt.json",
            recovery_path,
        )
    ):
        raise RuntimeError(
            "interrupted Apply inspection requires Stage-only lifecycle"
        )
    terminal_path, terminal, failure_manifest = (
        interrupted_apply_failure_manifest(args, release)
    )
    failure_manifest_sha256 = sha256_bytes(
        canonical_json(failure_manifest).encode("utf-8")
    )
    if (
        args.apply_failure_manifest_sha != "0" * 64
        and args.apply_failure_manifest_sha
            != failure_manifest_sha256
    ):
        raise RuntimeError(
            "interrupted Apply inspection manifest drifted"
        )
    if validated_latest_receipt_target() != stage_path.resolve():
        raise RuntimeError(
            "interrupted Apply inspection latest Stage drifted"
        )
    migration_lease = interrupted_apply_recovery_lease()
    try:
        service_before = assert_predecessor_active(
            release,
            allow_undersized_token_secret=True,
        )
        facts = exact_migration_facts(migration_lease.mysql)
        service_after = assert_predecessor_active(
            release,
            allow_undersized_token_secret=True,
        )
        final_facts = exact_migration_facts(migration_lease.mysql)
        if (
            not interrupted_recovery_runtime_matches(
                service_before,
                facts,
                terminal,
            )
            or not interrupted_recovery_runtime_matches(
                service_after,
                final_facts,
                terminal,
            )
            or not recorded_migration_counts_are_monotonic(
                final_facts,
                facts,
                DEPLOYMENT_RECEIPT_SCHEMA,
            )
        ):
            raise RuntimeError(
                "interrupted Apply inspection current-read drifted"
            )
        _, _, final_manifest = interrupted_apply_failure_manifest(
            args, release
        )
        if final_manifest != failure_manifest:
            raise RuntimeError(
                "interrupted Apply inspection manifest changed"
            )
        return {
            "schema": WORKER_RESULT_SCHEMA,
            "mode": "InspectInterruptedApplyRecovery",
            "state": "INTERRUPTED_APPLY_RECOVERY_PLAN_READY",
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "targetReleaseId": release.name,
            "targetSourceCommit": args.target_source_commit,
            "stageReceiptPath": str(stage_path),
            "stageReceiptSha256": args.stage_receipt_sha,
            "applyFailureReceiptPath": str(terminal_path),
            "applyFailureReceiptSha256":
                args.apply_failure_receipt_sha,
            "applyFailureManifest": failure_manifest,
            "applyFailureManifestSha256":
                failure_manifest_sha256,
            "retainedMigrationFacts": final_facts,
            "serviceJarSha256": service_after["jarSha256"],
            "serviceInvocationId":
                service_after.get("invocationId"),
            "allW1aFlagsExplicitFalse": True,
            "productionFilesystemChanged": False,
            "productionDatabaseChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
        }
    finally:
        migration_lease.close()


def canonicalize_interrupted_apply_recovery(args):
    approval = validate_interrupted_apply_recovery_approval(
        args, require_current=False
    )
    recovery_plan, recovery_plan_raw = (
        validate_interrupted_apply_recovery_plan(
            args, require_current=False
        )
    )
    approval_raw = base64.b64decode(
        args.approval_json_base64, validate=True
    )
    release = interrupted_apply_target_release(args)
    stage_path, _ = validate_interrupted_apply_stage(args, release)
    recovery_path = (
        release / "interrupted-apply-recovery-receipt.json"
    )
    approval_path = (
        release / INTERRUPTED_APPLY_RECOVERY_APPROVAL_NAME
    )
    recovery_plan_path = (
        release / INTERRUPTED_APPLY_RECOVERY_PLAN_NAME
    )
    committed_paths = (
        release / "deployment-receipt.json",
        release / "rollback-receipt.json",
        release / "rollback-verification-receipt.json",
    )
    if any(path.exists() or path.is_symlink() for path in committed_paths):
        raise RuntimeError(
            "interrupted Apply recovery cannot follow a committed lifecycle"
        )
    terminal_path, terminal, failure_manifest = (
        interrupted_apply_failure_manifest(args, release)
    )
    failure_manifest_sha256 = sha256_bytes(
        canonical_json(failure_manifest).encode("utf-8")
    )
    if failure_manifest_sha256 != args.apply_failure_manifest_sha:
        raise RuntimeError(
            "interrupted Apply failure manifest anchor drifted"
        )
    migration_lease = interrupted_apply_recovery_lease()
    try:
        service_before = assert_predecessor_active(
            release,
            allow_undersized_token_secret=True,
        )
        facts_before = exact_migration_facts(migration_lease.mysql)
        if not interrupted_recovery_runtime_matches(
            service_before,
            facts_before,
            terminal,
        ):
            raise RuntimeError(
                "interrupted Apply recovered predecessor drifted"
            )
        current_latest = validated_latest_receipt_target()
        receipt_exists = (
            recovery_path.exists() or recovery_path.is_symlink()
        )
        preflight_receipt = interrupted_recovery_receipt(
            args,
            approval,
            release,
            stage_path,
            terminal_path,
            terminal,
            failure_manifest,
            failure_manifest_sha256,
            facts_before,
            utc_now(),
        )
        existing = None
        if receipt_exists:
            validate_regular_file(
                recovery_path, release, modes=(0o600,)
            )
            existing = read_json(recovery_path)
            if (
                current_latest
                    not in {
                        None,
                        stage_path.resolve(),
                        recovery_path.resolve(),
                    }
                or not existing_interrupted_recovery_matches(
                    existing,
                    preflight_receipt,
                    facts_before,
                    terminal,
                )
            ):
                raise RuntimeError(
                    "interrupted Apply recovery receipt drifted"
                )
            validate_interrupted_recovery_historic_anchors(
                release, existing
            )
        elif current_latest != stage_path.resolve():
            raise RuntimeError(
                "interrupted Apply latest Stage CAS predecessor drifted"
            )
        else:
            approval = validate_interrupted_apply_recovery_approval(
                args, require_current=True
            )
            recovery_plan, recovery_plan_raw = (
                validate_interrupted_apply_recovery_plan(
                    args, require_current=True
                )
            )

        service_after = assert_predecessor_active(
            release,
            allow_undersized_token_secret=True,
        )
        facts_after = exact_migration_facts(migration_lease.mysql)
        if (
            not interrupted_recovery_runtime_matches(
                service_after,
                facts_after,
                terminal,
            )
            or not recorded_migration_counts_are_monotonic(
                facts_after,
                facts_before,
                DEPLOYMENT_RECEIPT_SCHEMA,
            )
        ):
            raise RuntimeError(
                "interrupted Apply recovery commit current-read drifted"
            )
        _, _, final_failure_manifest = (
            interrupted_apply_failure_manifest(args, release)
        )
        if (
            final_failure_manifest != failure_manifest
            or sha256_bytes(
                canonical_json(final_failure_manifest).encode("utf-8")
            )
            != args.apply_failure_manifest_sha
            or any(
                path.exists() or path.is_symlink()
                for path in committed_paths
            )
        ):
            raise RuntimeError(
                "interrupted Apply recovery anchors changed before CAS"
            )
        validate_interrupted_apply_stage(args, release)
        if existing is None:
            commit_observed_at = (
                interrupted_recovery_authorized_commit_time(
                    approval, recovery_plan
                )
            )
        else:
            validate_interrupted_recovery_historic_anchors(
                release, existing
            )
            commit_observed_at = existing["observedAt"]
        receipt = interrupted_recovery_receipt(
            args,
            approval,
            release,
            stage_path,
            terminal_path,
            terminal,
            failure_manifest,
            failure_manifest_sha256,
            facts_after,
            commit_observed_at,
        )
        if existing is not None and not (
            existing_interrupted_recovery_matches(
                existing,
                receipt,
                facts_after,
                terminal,
            )
        ):
            raise RuntimeError(
                "interrupted Apply recovery receipt changed at commit"
            )
        anchors_created = any((
            create_or_validate_interrupted_recovery_anchor(
                approval_path,
                approval_raw,
                args.approval_sha,
                allow_create=existing is None,
            ),
            create_or_validate_interrupted_recovery_anchor(
                recovery_plan_path,
                recovery_plan_raw,
                args.recovery_plan_receipt_sha,
                allow_create=existing is None,
            ),
        ))
        receipt_created = False
        if existing is None:
            atomic_json_create_new(recovery_path, receipt)
            receipt_created = True
        path = recovery_path
        changed = advance_latest_receipt(path, [stage_path])
        return {
            "schema": WORKER_RESULT_SCHEMA,
            "mode": "CanonicalizeInterruptedApplyRecovery",
            "state": INTERRUPTED_APPLY_RECOVERY_STATE,
            "recoveryDisposition": (
                "CANONICALIZED"
                if receipt_created
                else (
                    "RECEIPT_LINK_REPAIRED"
                    if changed
                    else "ALREADY_EXACT"
                )
            ),
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "targetReleaseId": release.name,
            "targetSourceCommit": args.target_source_commit,
            "receiptPath": str(recovery_path),
            "receiptSha256": sha256_file(recovery_path),
            "productionFilesystemChanged":
                anchors_created or receipt_created or changed,
            "productionDatabaseChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
        }
    finally:
        migration_lease.close()


def validate_rollback_receipt_base(original, release):
    deployment_path = release / "deployment-receipt.json"
    validate_regular_file(deployment_path, release, modes=(0o600,))
    receipt_schema = original.get("schema")
    state = original.get("state")
    retained = original.get("retainedMigrationFacts")
    if state == "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT":
        retained_valid = (
            legacy_migration_structure_matches(retained)
            if receipt_schema == LEGACY_ROLLBACK_RECEIPT_SCHEMA
            else migration_structure_matches(retained)
            if receipt_schema == ROLLBACK_RECEIPT_SCHEMA
            else False
        )
        database_evidence_valid = bool(
            original.get("databaseSafetyCurrentRead") == "VERIFIED"
            and retained_valid
        )
    elif (
        state
        == "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE"
    ):
        database_evidence_valid = bool(
            isinstance(original.get("databaseSafetyCurrentRead"), str)
            and original["databaseSafetyCurrentRead"].startswith(
                "UNAVAILABLE:"
            )
            and retained is None
        )
    else:
        database_evidence_valid = False
    if (
        receipt_schema not in {
            LEGACY_ROLLBACK_RECEIPT_SCHEMA,
            ROLLBACK_RECEIPT_SCHEMA,
        }
        or original.get("releaseId") != release.name
        or not COMMIT_PATTERN.fullmatch(
            str(original.get("sourceCommit") or "")
        )
        or original.get("deploymentReceiptPath")
            != str(deployment_path)
        or original.get("deploymentReceiptSha256")
            != sha256_file(deployment_path)
        or not SHA_PATTERN.fullmatch(
            str(original.get("rollbackApprovalReceiptSha256") or "")
        )
        or original.get("applicationRollbackVerified") is not True
        or original.get("retainedRollbackDropIn") is not True
        or original.get("databaseRollbackStrategy")
            != "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
        or original.get("databaseDownClaimed") is not False
        or original.get("allW1aFlagsExplicitFalse") is not True
        or original.get("productionFilesystemChanged") is not True
        or original.get("productionDatabaseChanged") is not False
        or type(original.get("productionServiceChanged")) is not bool
        or original.get("officialExpertsPackageChanged") is not False
        or type(original.get("recoveredExistingSideEffects")) is not bool
        or not isinstance(original.get("serviceBefore"), dict)
        or not isinstance(original.get("serviceAfter"), dict)
        or not database_evidence_valid
    ):
        raise RuntimeError("rollback receipt chain identity drifted")
    return deployment_path


def validated_rollback_receipt_chain(receipt_path):
    receipt_path = pathlib.Path(receipt_path)
    validate_regular_file(
        receipt_path, receipt_path.parent, modes=(0o600,)
    )
    release = receipt_path.parent
    if (
        release.parent != RELEASE_ROOT
        or not RUN_PATTERN.fullmatch(release.name)
    ):
        raise RuntimeError("rollback receipt escaped the release root")
    latest = read_json(receipt_path)
    verification = None
    if latest.get("schema") in {
        LEGACY_ROLLBACK_VERIFICATION_SCHEMA,
        ROLLBACK_VERIFICATION_SCHEMA,
    }:
        if receipt_path.name != "rollback-verification-receipt.json":
            raise RuntimeError("rollback verification path is invalid")
        original_path = release / "rollback-receipt.json"
        validate_regular_file(
            original_path, release, modes=(0o600,)
        )
        if (
            latest.get("rollbackReceiptPath") != str(original_path)
            or latest.get("rollbackReceiptSha256")
                != sha256_file(original_path)
        ):
            raise RuntimeError(
                "rollback verification original anchor drifted"
            )
        original = read_json(original_path)
        verification = latest
    else:
        if receipt_path.name != "rollback-receipt.json":
            raise RuntimeError("rollback receipt path is invalid")
        original_path = receipt_path
        original = latest
    deployment_path = validate_rollback_receipt_base(original, release)
    effective_state = original.get("state")
    retained_facts = original.get("retainedMigrationFacts")
    retained_facts_schema = original.get("schema")
    if verification is not None:
        if (
            original.get("state")
            != "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE"
            or verification.get("state")
            != "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
            or verification.get("releaseId") != release.name
            or verification.get("sourceCommit")
                != original.get("sourceCommit")
            or verification.get("deploymentReceiptPath")
                != str(deployment_path)
            or verification.get("deploymentReceiptSha256")
                != sha256_file(deployment_path)
            or not SHA_PATTERN.fullmatch(
                str(
                    verification.get(
                        "rollbackApprovalReceiptSha256"
                    ) or ""
                )
            )
            or verification.get("databaseSafetyCurrentRead")
                != "VERIFIED"
            or verification.get("databaseRollbackStrategy")
                != "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
            or verification.get("databaseDownClaimed") is not False
            or verification.get("productionFilesystemChanged") is not True
            or verification.get("productionDatabaseChanged") is not False
            or verification.get("productionServiceChanged") is not False
            or verification.get("officialExpertsPackageChanged") is not False
        ):
            raise RuntimeError("rollback verification receipt drifted")
        effective_state = verification["state"]
        retained_facts = verification.get("retainedMigrationFacts")
        retained_facts_schema = verification.get("schema")
    retained_facts_valid = (
        legacy_migration_structure_matches(retained_facts)
        if retained_facts_schema == LEGACY_ROLLBACK_VERIFICATION_SCHEMA
        or retained_facts_schema == LEGACY_ROLLBACK_RECEIPT_SCHEMA
        else migration_structure_matches(retained_facts)
        if retained_facts_schema == ROLLBACK_VERIFICATION_SCHEMA
        or retained_facts_schema == ROLLBACK_RECEIPT_SCHEMA
        else False
    )
    if (
        effective_state
        != "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
        or not retained_facts_valid
    ):
        raise RuntimeError("rollback chain does not prove retained 043")
    return {
        "release": release,
        "receiptPath": receipt_path,
        "receiptSha256": sha256_file(receipt_path),
        "receiptSchema": latest["schema"],
        "sourceCommit": original["sourceCommit"],
        "state": effective_state,
        "retainedMigrationFacts": retained_facts,
        "retainedMigrationFactsSchema": retained_facts_schema,
        "originalReceiptPath": original_path,
        "originalReceiptSha256": sha256_file(original_path),
    }


def validate_prior_rollback_stage_entry(current):
    if (
        not LATEST_RECEIPT.is_symlink()
        or CURRENT_LINK.exists()
        or CURRENT_LINK.is_symlink()
    ):
        raise RuntimeError("prior rollback latest topology is invalid")
    chain = validated_rollback_receipt_chain(
        LATEST_RECEIPT.resolve(strict=True)
    )
    release = chain["release"]
    previous = predecessor_facts(release)
    rollback_dropin = release / "evidence/rollback-systemd-dropin.conf"
    if (
        not DROPIN_PATH.is_file()
        or DROPIN_PATH.is_symlink()
        or sha256_file(DROPIN_PATH) != sha256_file(rollback_dropin)
        or not NGINX_PATH.is_file()
        or NGINX_PATH.is_symlink()
        or sha256_file(NGINX_PATH) != previous["previousNginxSha256"]
        or current.get("activeState") != "active"
        or current.get("jarSha256") != previous["previousJarSha256"]
    ):
        raise RuntimeError("prior rollback predecessor topology drifted")
    assert_restored_predecessor_runtime_contract(release, current)
    migration = read_exact_migration_facts()
    if not recorded_migration_facts_match_current(
        chain["retainedMigrationFacts"],
        migration,
        chain["retainedMigrationFactsSchema"],
    ):
        raise RuntimeError("prior rollback retained 043 facts drifted")
    wait_for_u3w_health()
    return {
        "schema": "fbsir.u3wPriorRollbackStageAnchor.v1",
        "releaseId": release.name,
        "sourceCommit": chain["sourceCommit"],
        "receiptPath": str(chain["receiptPath"]),
        "receiptSha256": chain["receiptSha256"],
        "receiptSchema": chain["receiptSchema"],
        "state": chain["state"],
    }


def validate_interrupted_recovery_historic_anchors(
    release,
    recovery,
):
    approval_path = (
        release / INTERRUPTED_APPLY_RECOVERY_APPROVAL_NAME
    )
    plan_path = release / INTERRUPTED_APPLY_RECOVERY_PLAN_NAME
    validate_regular_file(approval_path, release, modes=(0o600,))
    validate_regular_file(plan_path, release, modes=(0o600,))
    if (
        sha256_file(approval_path)
            != recovery.get("approvalReceiptSha256")
        or sha256_file(plan_path)
            != recovery.get("recoveryPlanReceiptSha256")
    ):
        raise RuntimeError(
            "prior interrupted recovery approval or Plan drifted"
        )
    approval = read_json(approval_path)
    plan = read_json(plan_path)
    if (
        set(approval) != INTERRUPTED_APPLY_RECOVERY_APPROVAL_FIELDS
        or set(plan) != INTERRUPTED_APPLY_RECOVERY_PLAN_FIELDS
        or approval.get("schema")
            != "fbsir.u3wProductionChangeApprovalReceipt.v2"
        or approval.get("action")
            != "CANONICALIZE_W1A_INTERRUPTED_APPLY_RECOVERY"
        or approval.get("targetHost") != TARGET_HOST
        or approval.get("runId") != recovery.get("recoveryRunId")
        or approval.get("executorSourceCommit")
            != recovery.get("executorSourceCommit")
        or approval.get("targetReleaseId")
            != recovery.get("releaseId")
        or approval.get("targetSourceCommit")
            != recovery.get("sourceCommit")
        or approval.get("authorizedBy") != "workspace-user"
        or approval.get("concurrentDdlProhibited") is not True
        or approval.get("productionFilesystemWrite") is not True
        or approval.get("productionDatabaseWrite") is not False
        or approval.get("productionServiceChange") is not False
        or approval.get("officialExpertsPackageChange") is not False
        or approval.get("expectedStageReceiptSha256")
            != recovery.get("stageReceiptSha256")
        or approval.get("expectedApplyFailureReceiptSha256")
            != recovery.get("applyFailureReceiptSha256")
        or approval.get("expectedApplyFailureManifestSha256")
            != recovery.get(
                "applyFailureReceiptManifestSha256"
            )
        or approval.get("requestDigest")
            != recovery.get("recoveryPlanReceiptSha256")
        or approval.get("approvalNonce")
            != recovery.get("approvalNonce")
        or approval.get("runnerSha256")
            != recovery.get("runnerSha256")
        or approval.get("workerSha256")
            != recovery.get("workerSha256")
        or plan.get("schema")
            != INTERRUPTED_APPLY_RECOVERY_PLAN_SCHEMA
        or plan.get("mode") != "RecoveryPlan"
        or plan.get("state")
            != INTERRUPTED_APPLY_RECOVERY_PLAN_STATE
        or plan.get("targetHost") != TARGET_HOST
        or plan.get("serviceUnit") != SERVICE_UNIT
        or plan.get("recoveryRunId")
            != recovery.get("recoveryRunId")
        or plan.get("executorSourceCommit")
            != recovery.get("executorSourceCommit")
        or plan.get("targetReleaseId")
            != recovery.get("releaseId")
        or plan.get("targetSourceCommit")
            != recovery.get("sourceCommit")
        or plan.get("stageReceiptSha256")
            != recovery.get("stageReceiptSha256")
        or plan.get("applyFailureReceiptSha256")
            != recovery.get("applyFailureReceiptSha256")
        or plan.get("applyFailureManifestSha256")
            != recovery.get(
                "applyFailureReceiptManifestSha256"
            )
        or plan.get("runnerSha256")
            != recovery.get("runnerSha256")
        or plan.get("workerSha256")
            != recovery.get("workerSha256")
        or plan.get("productionFilesystemWrite") is not True
        or plan.get("productionDatabaseWrite") is not False
        or plan.get("productionServiceChange") is not False
        or plan.get("officialExpertsPackageChange") is not False
    ):
        raise RuntimeError(
            "prior interrupted recovery approval or Plan is invalid"
        )
    try:
        approved = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        approval_expires = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
        generated = dt.datetime.fromisoformat(
            plan["generatedAt"].replace("Z", "+00:00")
        )
        plan_expires = dt.datetime.fromisoformat(
            plan["expiresAt"].replace("Z", "+00:00")
        )
        observed = dt.datetime.fromisoformat(
            recovery["observedAt"].replace("Z", "+00:00")
        )
    except (KeyError, AttributeError, ValueError) as error:
        raise RuntimeError(
            "prior interrupted recovery anchor time is invalid"
        ) from error
    if (
        any(
            value.tzinfo is None
            for value in (
                approved,
                approval_expires,
                generated,
                plan_expires,
                observed,
            )
        )
        or generated > approved
        or approval_expires <= approved
        or approval_expires - approved > dt.timedelta(hours=24)
        or plan_expires <= generated
        or plan_expires - generated > dt.timedelta(hours=24)
        or observed < approved
        or observed < generated
        or observed >= approval_expires
        or observed >= plan_expires
    ):
        raise RuntimeError(
            "prior interrupted recovery anchor time drifted"
        )
    return approval, plan


def validate_prior_interrupted_recovery_stage_entry(current):
    latest = validated_latest_receipt_target()
    if (
        latest is None
        or latest.name != "interrupted-apply-recovery-receipt.json"
        or CURRENT_LINK.exists()
        or CURRENT_LINK.is_symlink()
    ):
        raise RuntimeError(
            "prior interrupted recovery latest topology is invalid"
        )
    recovery = read_json(latest)
    if (
        set(recovery)
            != INTERRUPTED_APPLY_RECOVERY_RECEIPT_FIELDS
        or recovery.get("schema")
            != INTERRUPTED_APPLY_RECOVERY_RECEIPT_SCHEMA
        or recovery.get("state") != INTERRUPTED_APPLY_RECOVERY_STATE
        or not RUN_PATTERN.fullmatch(
            str(recovery.get("releaseId") or "")
        )
        or not COMMIT_PATTERN.fullmatch(
            str(recovery.get("sourceCommit") or "")
        )
        or not RUN_PATTERN.fullmatch(
            str(recovery.get("recoveryRunId") or "")
        )
        or not COMMIT_PATTERN.fullmatch(
            str(recovery.get("executorSourceCommit") or "")
        )
        or recovery.get("releaseId")
            == recovery.get("recoveryRunId")
        or recovery.get("sourceCommit")
            == recovery.get("executorSourceCommit")
        or any(
            not SHA_PATTERN.fullmatch(
                str(recovery.get(field) or "")
            )
            for field in (
                "approvalReceiptSha256",
                "runnerSha256",
                "workerSha256",
                "recoveryPlanReceiptSha256",
                "stageReceiptSha256",
                "applyFailureReceiptSha256",
                "applyFailureReceiptManifestSha256",
            )
        )
        or not re.fullmatch(
            r"[0-9a-f]{32}",
            str(recovery.get("approvalNonce") or ""),
        )
        or recovery.get("applicationRestored") is not True
        or recovery.get("topologyRestored") is not True
        or recovery.get("deploymentCommitOutcome") != "NOT_COMMITTED"
        or recovery.get("deploymentReceiptAbsent") is not True
        or recovery.get("rollbackReceiptAbsent") is not True
        or recovery.get("currentLinkAbsent") is not True
        or recovery.get("releaseDropInMatched") is not True
        or recovery.get("allW1aFlagsExplicitFalse") is not True
        or recovery.get("databaseDownClaimed") is not False
        or recovery.get("productionFilesystemChanged") is not True
        or type(recovery.get("productionDatabaseChanged")) is not bool
        or recovery.get(
            "productionDatabaseChangedThisRecoveryRun"
        ) is not False
        or type(
            recovery.get("productionDatabaseChangedSinceStage")
        ) is not bool
        or recovery.get("productionDatabaseChanged")
            != recovery.get("productionDatabaseChangedSinceStage")
        or recovery.get("productionServiceChanged") is not True
        or recovery.get(
            "productionServiceChangedThisRecoveryRun"
        ) is not False
        or recovery.get(
            "productionServiceChangedSinceStage"
        ) is not True
        or recovery.get("officialExpertsPackageChanged") is not False
        or not migration_structure_matches(
            recovery.get("retainedMigrationFacts")
        )
    ):
        raise RuntimeError(
            "prior interrupted recovery receipt is invalid"
        )
    anchor_args = argparse.Namespace(
        release_id=recovery["recoveryRunId"],
        source_commit=recovery["executorSourceCommit"],
        target_release_id=recovery["releaseId"],
        target_source_commit=recovery["sourceCommit"],
        stage_receipt_sha=recovery["stageReceiptSha256"],
        apply_failure_receipt_sha=
            recovery["applyFailureReceiptSha256"],
    )
    release = interrupted_apply_target_release(anchor_args)
    if latest.parent != release:
        raise RuntimeError(
            "prior interrupted recovery receipt escaped release"
        )
    stage_path, _ = validate_interrupted_apply_stage(
        anchor_args, release
    )
    terminal_path, terminal, failure_manifest = (
        interrupted_apply_failure_manifest(anchor_args, release)
    )
    manifest_sha256 = sha256_bytes(
        canonical_json(failure_manifest).encode("utf-8")
    )
    if (
        recovery.get("stageReceiptPath") != str(stage_path)
        or recovery.get("applyFailureReceiptPath")
            != str(terminal_path)
        or recovery.get("applyFailureReceiptSchema")
            != terminal.get("schema")
        or recovery.get("applyFailureReceiptState")
            != terminal.get("state")
        or recovery.get("applyFailureReceiptManifest")
            != failure_manifest
        or recovery.get("applyFailureReceiptManifestSha256")
            != manifest_sha256
        or not interrupted_recovery_observed_at_valid(
            recovery, terminal
        )
        or any(
            path.exists() or path.is_symlink()
            for path in (
                release / "deployment-receipt.json",
                release / "rollback-receipt.json",
                release / "rollback-verification-receipt.json",
            )
        )
    ):
        raise RuntimeError(
            "prior interrupted recovery evidence chain drifted"
        )
    validate_interrupted_recovery_historic_anchors(
        release, recovery
    )
    previous = predecessor_facts(release)
    rollback_dropin = (
        release / "evidence/rollback-systemd-dropin.conf"
    )
    if (
        not DROPIN_PATH.is_file()
        or DROPIN_PATH.is_symlink()
        or sha256_file(DROPIN_PATH) != sha256_file(rollback_dropin)
        or not NGINX_PATH.is_file()
        or NGINX_PATH.is_symlink()
        or sha256_file(NGINX_PATH) != previous["previousNginxSha256"]
        or current.get("activeState") != "active"
        or current.get("jarSha256")
            != previous["previousJarSha256"]
        or current.get("configuredJarSha256")
            != previous["previousJarSha256"]
        or current.get("processForbiddenOverrideNames") != []
        or any(
            current.get("configuredFlagValues", {}).get(name)
                != "false"
            or current.get("processFlagValues", {}).get(name)
                != "false"
            for name in FALSE_FLAGS
        )
        or environment_flags_explicit_false() is not True
    ):
        raise RuntimeError(
            "prior interrupted recovery predecessor topology drifted"
        )
    assert_restored_predecessor_runtime_contract(release, current)
    migration_lease = interrupted_apply_recovery_lease()
    try:
        current_facts = exact_migration_facts(
            migration_lease.mysql
        )
        if not recorded_migration_counts_are_monotonic(
            current_facts,
            recovery["retainedMigrationFacts"],
            DEPLOYMENT_RECEIPT_SCHEMA,
        ):
            raise RuntimeError(
                "prior interrupted recovery retained 043 drifted"
            )
    finally:
        migration_lease.close()
    wait_for_u3w_health()
    return {
        "schema":
            "fbsir.u3wPriorInterruptedApplyRecoveryStageAnchor.v1",
        "releaseId": release.name,
        "sourceCommit": recovery["sourceCommit"],
        "receiptPath": str(latest),
        "receiptSha256": sha256_file(latest),
        "receiptSchema": recovery["schema"],
        "state": recovery["state"],
    }


def assert_release_owned_recovery_topology(args, release):
    previous = predecessor_facts(release)
    previous_snapshot = previous["serviceSnapshotBeforeStage"]
    current = service_snapshot(require_active=False)
    candidate_dropin = sha256_file(
        release / "evidence/systemd-dropin.conf"
    )
    rollback_dropin = sha256_file(
        release / "evidence/rollback-systemd-dropin.conf"
    )
    baseline_dropin_entries = [
        item
        for item in previous_snapshot.get("dropInManifest", [])
        if item.get("path") == str(DROPIN_PATH)
    ]
    baseline_dropin = (
        baseline_dropin_entries[0].get("sha256")
        if len(baseline_dropin_entries) == 1
        else None
    )
    if len(baseline_dropin_entries) > 1:
        raise RuntimeError("staged predecessor drop-in manifest is ambiguous")
    candidate_nginx = sha256_file(
        release / "evidence/u3w-portal-sites.conf"
    )
    allowed_loaded_dropins = (
        previous_snapshot.get("dropInManifest", []),
        release_dropin_manifest(release, "systemd-dropin.conf"),
        release_dropin_manifest(
            release, "rollback-systemd-dropin.conf"
        ),
    )
    allowed_argv_sha256 = {
        previous_snapshot.get("processArgvSha256"),
        sha256_bytes(
            ("\0".join(candidate_process_arguments()) + "\0").encode(
                "utf-8"
            )
        ),
        sha256_bytes(
            ("\0".join(rollback_process_arguments(release)) + "\0").encode(
                "utf-8"
            )
        ),
    }
    allowed_jar_paths = {
        previous_snapshot.get("configuredJarPath"),
        previous_snapshot.get("processJarPath"),
        str(CURRENT_LINK / "backend/fbsir-admin.jar"),
        str(release / "rollback/previous-admin.jar"),
    }
    if (
        current.get("user") != previous_snapshot.get("user")
        or current.get("group") != previous_snapshot.get("group")
        or current.get("fragmentPath")
            != previous_snapshot.get("fragmentPath")
        or current.get("fragmentFileManifest")
            != previous_snapshot.get("fragmentFileManifest")
        or current.get("environmentFilePaths")
            != previous_snapshot.get("environmentFilePaths")
        or current.get("environmentFileManifest")
            != previous_snapshot.get("environmentFileManifest")
        or current.get("api2EventKeyManifest")
            != previous_snapshot.get("api2EventKeyManifest")
        or current.get("dropInManifest") not in allowed_loaded_dropins
        or current.get("externalConfigManifest")
            != previous_snapshot.get("externalConfigManifest")
        or current.get("additionalConfigSha256")
            != previous_snapshot.get("additionalConfigSha256")
        or current.get("configuredJarPath") not in allowed_jar_paths
        or (
            current.get("processJarPath") is not None
            and current.get("processJarPath") not in allowed_jar_paths
        )
        or current.get("workingDirectory") not in {
            previous_snapshot.get("workingDirectory"),
            str(ADMIN_ROOT),
        }
        or (
            current.get("activeState") == "active"
            and current.get("processArgvSha256")
                not in allowed_argv_sha256
        )
    ):
        raise RuntimeError(
            "rollback recovery runtime contract is not release-owned"
        )
    if current.get("activeState") == "active":
        if (
            current.get("processArgvSha256")
            == previous_snapshot.get("processArgvSha256")
        ):
            assert_legacy_runtime_contract(release, current)
        elif not exact_loaded_environment_matches(
            current, previous_snapshot
        ):
            raise RuntimeError(
                "rollback recovery environment is partial or drifted"
            )
    if not CURRENT_LINK.exists() and not CURRENT_LINK.is_symlink():
        link_state = "ABSENT"
    elif (
        CURRENT_LINK.is_symlink()
        and CURRENT_LINK.resolve() == release.resolve()
    ):
        link_state = "CANDIDATE"
    else:
        raise RuntimeError("rollback recovery current link is unowned")
    if not DROPIN_PATH.exists() and not DROPIN_PATH.is_symlink():
        dropin_state = "ABSENT"
    elif DROPIN_PATH.is_file() and not DROPIN_PATH.is_symlink():
        dropin_sha = sha256_file(DROPIN_PATH)
        if dropin_sha == candidate_dropin:
            dropin_state = "CANDIDATE"
        elif dropin_sha == rollback_dropin:
            dropin_state = "ROLLBACK"
        elif baseline_dropin and dropin_sha == baseline_dropin:
            dropin_state = "BASELINE"
        else:
            raise RuntimeError("rollback recovery drop-in is unowned")
    else:
        raise RuntimeError("rollback recovery drop-in is unsafe")
    if (
        NGINX_PATH.is_file()
        and not NGINX_PATH.is_symlink()
        and sha256_file(NGINX_PATH) == candidate_nginx
    ):
        nginx_state = "CANDIDATE"
    elif (
        NGINX_PATH.is_file()
        and not NGINX_PATH.is_symlink()
        and sha256_file(NGINX_PATH) == previous["previousNginxSha256"]
    ):
        nginx_state = "PREDECESSOR"
    else:
        raise RuntimeError("rollback recovery Nginx file is unowned")
    previous_sha = previous["previousJarSha256"]
    jar_state = (
        current["configuredJarSha256"],
        current["jarSha256"],
    )
    topology = (link_state, dropin_state, nginx_state, jar_state)
    initial_dropin_state = (
        "BASELINE" if baseline_dropin is not None else "ABSENT"
    )
    allowed_topologies = {
        ("ABSENT", initial_dropin_state, "PREDECESSOR",
         (previous_sha, previous_sha)),
        ("CANDIDATE", initial_dropin_state, "PREDECESSOR",
         (previous_sha, previous_sha)),
        ("CANDIDATE", "CANDIDATE", "PREDECESSOR",
         (previous_sha, previous_sha)),
        ("CANDIDATE", "CANDIDATE", "PREDECESSOR",
         (args.backend_sha, previous_sha)),
        ("CANDIDATE", "CANDIDATE", "CANDIDATE",
         (previous_sha, previous_sha)),
        ("CANDIDATE", "CANDIDATE", "CANDIDATE",
         (args.backend_sha, previous_sha)),
        ("CANDIDATE", "CANDIDATE", "CANDIDATE",
         (args.backend_sha, args.backend_sha)),
        ("CANDIDATE", "CANDIDATE", "PREDECESSOR",
         (args.backend_sha, args.backend_sha)),
        ("CANDIDATE", "ROLLBACK", "PREDECESSOR",
         (args.backend_sha, args.backend_sha)),
        ("CANDIDATE", "ROLLBACK", "PREDECESSOR",
         (previous_sha, args.backend_sha)),
        ("CANDIDATE", "ROLLBACK", "PREDECESSOR",
         (previous_sha, previous_sha)),
        ("ABSENT", "ROLLBACK", "PREDECESSOR",
         (previous_sha, previous_sha)),
    }
    if topology not in allowed_topologies:
        raise RuntimeError("rollback recovery topology is not release-owned")
    return current


def rollback_transition(args, release):
    restore_performed = False
    recovered_existing_side_effects = False
    try:
        before = assert_candidate_owned_topology(args, release)
        restore_application(release)
        restore_performed = True
    except Exception:
        try:
            before = assert_predecessor_owned_topology(release)
            recovered_existing_side_effects = True
            if before["activeState"] != "active":
                restore_application(release)
                restore_performed = True
        except Exception:
            before = assert_release_owned_recovery_topology(args, release)
            restore_application(release)
            restore_performed = True
            recovered_existing_side_effects = True
    after = assert_predecessor_active(release)
    return (
        before,
        after,
        restore_performed,
        recovered_existing_side_effects,
    )


ROLLBACK_FAILURE_STATES = frozenset((
    "ROLLBACK_FAILED_AFTER_BOUNDED_RELEASE_OWNED_RECOVERY",
    "ROLLBACK_APPLICATION_RESTORED_EVIDENCE_FINALIZATION_FAILED",
))


def write_rollback_failure_receipt(
    args,
    initial_error,
    recovery_error,
    *,
    state="ROLLBACK_FAILED_AFTER_BOUNDED_RELEASE_OWNED_RECOVERY",
    application_restored=False,
):
    if (
        state not in ROLLBACK_FAILURE_STATES
        or type(application_restored) is not bool
    ):
        raise RuntimeError("rollback failure receipt semantics are invalid")
    release = release_directory(args)
    timestamp = dt.datetime.now(dt.timezone.utc).strftime(
        "%Y%m%dT%H%M%S%fZ"
    )
    identity = "{}:{}:{}".format(
        type(initial_error).__name__,
        type(recovery_error).__name__,
        timestamp,
    )
    path = release / (
        "rollback-failure-{}-{}.json".format(
            timestamp, sha256_bytes(identity.encode("utf-8"))[:12]
        )
    )
    receipt = {
        "schema": "fbsir.u3wDefaultOffRollbackFailureReceipt.v1",
        "state": state,
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "rollbackApprovalReceiptSha256": args.approval_sha,
        "initialErrorType": type(initial_error).__name__,
        "initialErrorMessageSha256": sha256_bytes(
            str(initial_error).encode("utf-8")
        ),
        "recoveryErrorType": type(recovery_error).__name__,
        "recoveryErrorMessageSha256": sha256_bytes(
            str(recovery_error).encode("utf-8")
        ),
        "applicationRestored": application_restored,
        "databaseDownClaimed": False,
        "officialExpertsPackageChanged": False,
        "observedAt": utc_now(),
    }
    atomic_json(path, receipt)
    return path


def ensure_rollback_verification_receipt(
    args, release, rollback_path, rollback, migration_facts
):
    if not migration_structure_matches(migration_facts):
        raise RuntimeError("rollback verification migration facts drifted")
    path = release / "rollback-verification-receipt.json"
    expected = {
        "schema": ROLLBACK_VERIFICATION_SCHEMA,
        "state": (
            "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
        ),
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "rollbackReceiptPath": str(rollback_path),
        "rollbackReceiptSha256": sha256_file(rollback_path),
        "deploymentReceiptPath": rollback["deploymentReceiptPath"],
        "deploymentReceiptSha256":
            rollback["deploymentReceiptSha256"],
        "databaseSafetyCurrentRead": "VERIFIED",
        "retainedMigrationFacts": migration_facts,
        "databaseRollbackStrategy": "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
        "databaseDownClaimed": False,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
    }
    if path.exists() or path.is_symlink():
        validate_regular_file(path, release, modes=(0o600,))
        receipt = read_json(path)
        if (
            any(receipt.get(key) != value for key, value in expected.items())
            or not SHA_PATTERN.fullmatch(
                str(
                    receipt.get(
                        "rollbackApprovalReceiptSha256"
                    ) or ""
                )
            )
        ):
            raise RuntimeError(
                "existing rollback verification receipt drifted"
            )
        return path, receipt, False
    receipt = dict(expected)
    receipt["rollbackApprovalReceiptSha256"] = args.approval_sha
    receipt["observedAt"] = utc_now()
    atomic_json(path, receipt)
    return path, receipt, True


def _rollback_release(args):
    release = release_directory(args)
    deployment_path, deployment = validate_deployment_receipt(args, release)
    path = release / "rollback-receipt.json"
    if path.exists():
        validate_regular_file(path, release, modes=(0o600,))
        receipt = read_json(path)
        receipt_schema = receipt.get("schema")
        if (
            receipt_schema not in {
                LEGACY_ROLLBACK_RECEIPT_SCHEMA,
                ROLLBACK_RECEIPT_SCHEMA,
            }
            or receipt.get("releaseId") != args.release_id
            or receipt.get("sourceCommit") != args.source_commit
            or receipt.get("deploymentReceiptSha256")
            != sha256_file(deployment_path)
        ):
            raise RuntimeError("existing rollback receipt identity drifted")
        validate_rollback_receipt_base(receipt, release)
        assert_predecessor_active(release)
        migration_facts = None
        try:
            migration_facts = read_exact_migration_facts()
            current_state = (
                "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
            )
        except Exception:
            current_state = (
                "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE"
            )
        result_path = path
        result_receipt = receipt
        verification_created = False
        if receipt.get("state") == current_state:
            if (
                current_state
                == "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
                and not recorded_migration_facts_match_current(
                    receipt.get("retainedMigrationFacts"),
                    migration_facts,
                    receipt_schema,
                )
            ):
                raise RuntimeError(
                    "existing rollback retained ledger facts drifted"
                )
        elif (
            receipt.get("state")
                == (
                    "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_"
                    "UNAVAILABLE"
                )
            and current_state
                == (
                    "ROLLED_BACK_APPLICATION_DATABASE_043_"
                    "RETAINED_DORMANT"
                )
        ):
            (
                result_path,
                result_receipt,
                verification_created,
            ) = ensure_rollback_verification_receipt(
                args, release, path, receipt, migration_facts
            )
            validated_rollback_receipt_chain(result_path)
        else:
            raise RuntimeError(
                "existing rollback receipt no longer matches current "
                "database safety read"
            )
        if (
            result_receipt.get("state")
            == "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
        ):
            validated_rollback_receipt_chain(result_path)
        latest_changed = advance_latest_receipt(
            result_path,
            [
                deployment_path,
                path,
                release / "rollback-verification-receipt.json",
            ],
        )
        return {
            "schema": WORKER_RESULT_SCHEMA,
            "mode": "Rollback",
            "state": result_receipt["state"],
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "approvalReceiptSha256": args.approval_sha,
            "transitionApprovalReceiptSha256":
                result_receipt.get(
                    "rollbackApprovalReceiptSha256"
                ),
            "receiptPath": str(result_path),
            "receiptSha256": sha256_file(result_path),
            "evidenceReceipts": validate_release_evidence(
                args, release, deployment, require_execution=True
            ),
            "productionFilesystemChanged": bool(
                verification_created or latest_changed
            ),
            "productionDatabaseChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
        }
    try:
        (
            before,
            after,
            restore_performed,
            recovered_existing_side_effects,
        ) = rollback_transition(args, release)
    except Exception as initial_error:
        try:
            before = assert_release_owned_recovery_topology(args, release)
            restore_application(release)
            after = assert_predecessor_active(release)
            restore_performed = True
            recovered_existing_side_effects = True
        except Exception as recovery_error:
            failure_path = write_rollback_failure_receipt(
                args, initial_error, recovery_error
            )
            raise ReleaseMutationFailure(
                "rollback and bounded release-owned recovery failed; "
                "failure receipt was committed",
                failure_path,
            ) from initial_error
    previous = predecessor_facts(release)
    try:
        migration_facts = read_exact_migration_facts()
        database_safety_current_read = "VERIFIED"
    except Exception as database_error:
        migration_facts = None
        database_safety_current_read = (
            "UNAVAILABLE:" + type(database_error).__name__
        )
    database_current_read_verified = (
        database_safety_current_read == "VERIFIED"
    )
    flags_false = environment_flags_explicit_false()
    verified = bool(
        after["jarSha256"] == previous["previousJarSha256"]
        and (
            before["jarSha256"] != args.backend_sha
            or before["activeState"] != "active"
            or after["invocationId"] != before["invocationId"]
        )
        and DROPIN_PATH.is_file()
        and not DROPIN_PATH.is_symlink()
        and not CURRENT_LINK.exists()
        and not CURRENT_LINK.is_symlink()
        and sha256_file(NGINX_PATH) == previous["previousNginxSha256"]
        and flags_false is True
    )
    if not verified:
        raise RuntimeError("application rollback current-read failed")
    rollback_state = (
        "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
        if database_current_read_verified
        else "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE"
    )
    receipt = {
        "schema": ROLLBACK_RECEIPT_SCHEMA,
        "state": rollback_state,
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "rollbackApprovalReceiptSha256": args.approval_sha,
        "deploymentReceiptPath": str(deployment_path),
        "deploymentReceiptSha256": sha256_file(deployment_path),
        "serviceBefore": (
            deployment.get("serviceAfter")
            if recovered_existing_side_effects
            else before
        ),
        "serviceAfter": after,
        "applicationRollbackVerified": True,
        "retainedRollbackDropIn": True,
        "recoveredExistingSideEffects":
            recovered_existing_side_effects,
        "databaseRollbackStrategy": "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
        "retainedMigrationFacts": migration_facts,
        "databaseSafetyCurrentRead": database_safety_current_read,
        "databaseDownClaimed": False,
        "allW1aFlagsExplicitFalse": flags_false,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionServiceChanged": restore_performed,
        "officialExpertsPackageChanged": False,
        "observedAt": utc_now(),
    }
    atomic_json(path, receipt)
    advance_latest_receipt(path, [deployment_path])
    return {
        "schema": WORKER_RESULT_SCHEMA,
        "mode": "Rollback",
        "state": receipt["state"],
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "transitionApprovalReceiptSha256":
            receipt.get("rollbackApprovalReceiptSha256"),
        "receiptPath": str(path),
        "receiptSha256": sha256_file(path),
        "evidenceReceipts": validate_release_evidence(
            args, release, deployment, require_execution=True
        ),
        "recoveredExistingSideEffects":
            recovered_existing_side_effects,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionServiceChanged": restore_performed,
        "officialExpertsPackageChanged": False,
    }


def rollback_release(args):
    try:
        return _rollback_release(args)
    except ReleaseMutationFailure:
        raise
    except Exception as error:
        release = release_directory(args)
        try:
            current = assert_predecessor_owned_topology(release)
            if current.get("activeState") != "active":
                raise RuntimeError(
                    "predecessor is not active after rollback failure"
                )
            assert_restored_predecessor_runtime_contract(
                release, current
            )
            failure_path = write_rollback_failure_receipt(
                args,
                error,
                error,
                state=(
                    "ROLLBACK_APPLICATION_RESTORED_"
                    "EVIDENCE_FINALIZATION_FAILED"
                ),
                application_restored=True,
            )
        except Exception as evidence_error:
            raise RuntimeError(
                "rollback failed and post-failure predecessor state "
                "could not be safely evidenced: "
                + type(evidence_error).__name__
            ) from error
        raise ReleaseMutationFailure(
            "rollback application restore succeeded but evidence "
            "finalization failed",
            failure_path,
        ) from error


def execute(args):
    if args.mode == "InspectInterruptedApplyRecovery":
        return inspect_interrupted_apply_recovery(args)
    if args.mode == "CanonicalizeInterruptedApplyRecovery":
        return canonicalize_interrupted_apply_recovery(args)
    if args.mode == "PrepareStage":
        return prepare_stage(args)
    if args.mode == "FinalizeStage":
        return finalize_stage(args)
    if args.mode == "Apply":
        return apply_release(args)
    if args.mode == "Rollback":
        return rollback_release(args)
    if args.mode == "Verify":
        return verify_release(args)
    raise RuntimeError("unsupported release mode")


def validated_failure_receipt_evidence(args, error):
    if not isinstance(error, ReleaseMutationFailure):
        return None
    path = error.failure_receipt_path
    release = release_directory(args)
    if path.parent != release:
        raise RuntimeError("failure receipt escaped the exact release root")
    if args.mode == "Apply":
        name_matches = APPLY_FAILURE_NAME_PATTERN.fullmatch(path.name)
        expected_schema = APPLY_FAILURE_RECEIPT_SCHEMA
        approval_field = "applyApprovalReceiptSha256"
        allowed_states = APPLY_FAILURE_STATES
    elif args.mode == "Rollback":
        name_matches = ROLLBACK_FAILURE_NAME_PATTERN.fullmatch(path.name)
        expected_schema = "fbsir.u3wDefaultOffRollbackFailureReceipt.v1"
        approval_field = "rollbackApprovalReceiptSha256"
        allowed_states = ROLLBACK_FAILURE_STATES
    else:
        raise RuntimeError(
            "non-mutating worker mode cannot attach a failure receipt"
        )
    if not name_matches:
        raise RuntimeError("failure receipt name is invalid")
    validate_regular_file(path, release, modes=(0o600,))
    receipt = read_json(path)
    if (
        receipt.get("schema") != expected_schema
        or receipt.get("state") not in allowed_states
        or receipt.get("releaseId") != args.release_id
        or receipt.get("sourceCommit") != args.source_commit
        or receipt.get(approval_field) != args.approval_sha
        or receipt.get("officialExpertsPackageChanged") is not False
    ):
        raise RuntimeError("failure receipt identity is invalid")
    return {
        "failureReceiptPath": str(path),
        "failureReceiptSha256": sha256_file(path),
        "failureReceiptSchema": receipt["schema"],
        "failureReceiptState": receipt["state"],
        "failureReceiptEvidenceValid": True,
    }


def worker_error_envelope(args, error):
    result = {
        "schema": "fbsir.u3wDefaultOffReleaseWorkerError.v2",
        "state": "WORKER_FAILED",
        "mode": args.mode,
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "errorType": type(error).__name__,
        "errorMessageSha256": sha256_bytes(str(error).encode("utf-8")),
        "failureReceiptPath": None,
        "failureReceiptSha256": None,
        "failureReceiptSchema": None,
        "failureReceiptState": None,
        "failureReceiptEvidenceValid": False,
        "officialExpertsPackageChanged": False,
    }
    try:
        evidence = validated_failure_receipt_evidence(args, error)
        if evidence is not None:
            result.update(evidence)
    except Exception as evidence_error:
        result["failureReceiptValidationErrorType"] = type(
            evidence_error
        ).__name__
        result["failureReceiptValidationErrorMessageSha256"] = (
            sha256_bytes(str(evidence_error).encode("utf-8"))
        )
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode",
        required=True,
        choices=(
            "PrepareStage",
            "FinalizeStage",
            "Apply",
            "Rollback",
            "Verify",
            "InspectInterruptedApplyRecovery",
            "CanonicalizeInterruptedApplyRecovery",
        ),
    )
    parser.add_argument("--release-id", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--approval-sha", required=True)
    parser.add_argument("--approval-json-base64", required=True)
    parser.add_argument("--runner-sha", required=True)
    parser.add_argument("--worker-sha", required=True)
    parser.add_argument("--build-receipt-sha", required=True)
    parser.add_argument("--plan-receipt-sha", required=True)
    parser.add_argument("--backup-receipt-sha", required=True)
    parser.add_argument("--baseline-receipt-sha", required=True)
    parser.add_argument(
        "--admin-root-dependency-adoption-receipt-sha",
        required=True,
    )
    parser.add_argument("--configuration-receipt-sha", required=True)
    parser.add_argument("--stage-receipt-sha", required=True)
    parser.add_argument("--deployment-receipt-sha", required=True)
    parser.add_argument("--backend-sha", required=True)
    parser.add_argument("--frontend-tree-sha", required=True)
    parser.add_argument("--migration-sha", required=True)
    parser.add_argument("--target-release-id", default="")
    parser.add_argument("--target-source-commit", default="")
    parser.add_argument(
        "--apply-failure-receipt-sha", default="0" * 64
    )
    parser.add_argument(
        "--apply-failure-manifest-sha", default="0" * 64
    )
    parser.add_argument(
        "--recovery-plan-receipt-sha", default="0" * 64
    )
    parser.add_argument("--recovery-plan-json-base64", default="")
    args = parser.parse_args()
    try:
        validate_arguments(args)
        if args.mode in {"Verify", "InspectInterruptedApplyRecovery"}:
            ensure_parent_directory(ADMIN_ROOT)
            descriptor = os.open(
                LOCK_PATH,
                os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
            )
            lock_mode = fcntl.LOCK_SH
        else:
            safe_directory(ADMIN_ROOT, 0o755)
            descriptor = os.open(
                LOCK_PATH,
                os.O_RDWR
                | os.O_CREAT
                | getattr(os, "O_NOFOLLOW", 0),
                0o600,
            )
            lock_mode = fcntl.LOCK_EX
        try:
            status = os.fstat(descriptor)
            if (
                not stat.S_ISREG(status.st_mode)
                or status.st_uid != 0
                or status.st_gid != 0
                or status.st_nlink != 1
            ):
                raise RuntimeError("global release lock custody is invalid")
            if args.mode not in {
                "Verify",
                "InspectInterruptedApplyRecovery",
            }:
                os.fchmod(descriptor, 0o600)
            elif status.st_mode & 0o777 != 0o600:
                raise RuntimeError("global release lock mode is invalid")
            acquire_release_lock(descriptor, lock_mode)
            validate_arguments(args)
            validate_target_plan_time(args)
            print(canonical_json(execute(args)))
        finally:
            fcntl.flock(descriptor, fcntl.LOCK_UN)
            os.close(descriptor)
        return 0
    except Exception as error:
        print(canonical_json(worker_error_envelope(args, error)))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
