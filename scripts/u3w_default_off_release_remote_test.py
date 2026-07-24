import base64
import contextlib
import datetime as dt
import hashlib
import hmac
import importlib.util
import inspect
import io
import json
import os
import pathlib
import sys
import tarfile
import tempfile
import types
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parent
if "fcntl" not in sys.modules:
    sys.modules["fcntl"] = types.SimpleNamespace(
        LOCK_EX=2, LOCK_SH=1, LOCK_UN=8, flock=lambda *_: None
    )


def load_module():
    spec = importlib.util.spec_from_file_location(
        "u3w_default_off_release_remote",
        ROOT / "u3w-default-off-release-remote.py",
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


release = load_module()


def release_args(mode="FinalizeStage"):
    now = dt.datetime.now(dt.timezone.utc)
    args = types.SimpleNamespace(
        mode=mode,
        release_id="w1a-release-0123456789ab-20260723T180000Z",
        source_commit="a" * 40,
        approval_sha="",
        approval_json_base64="",
        runner_sha="b" * 64,
        worker_sha="c" * 64,
        build_receipt_sha="d" * 64,
        plan_receipt_sha="e" * 64,
        backup_receipt_sha="f" * 64,
        baseline_receipt_sha="1" * 64,
        admin_root_dependency_adoption_receipt_sha="6" * 64,
        configuration_receipt_sha="2" * 64,
        stage_receipt_sha="0" * 64,
        deployment_receipt_sha="0" * 64,
        backend_sha="3" * 64,
        frontend_tree_sha="4" * 64,
        migration_sha="5" * 64,
    )
    normalized_mode = (
        "Stage"
        if mode in ("PrepareStage", "FinalizeStage")
        else mode
    )
    actions = {
        "Stage": "STAGE_W1A_DEFAULT_OFF_RELEASE",
        "Apply": "APPLY_W1A_DEFAULT_OFF_RELEASE",
        "Rollback": "ROLLBACK_W1A_DEFAULT_OFF_RELEASE",
        "Verify": "VERIFY_W1A_DEFAULT_OFF_RELEASE",
    }
    payload = {
        "schema": "fbsir.u3wProductionChangeApprovalReceipt.v1",
        "action": actions[normalized_mode],
        "targetHost": "api2.u3w.com",
        "runId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvedAt": (now - dt.timedelta(minutes=1))
        .isoformat()
        .replace("+00:00", "Z"),
        "expiresAt": (now + dt.timedelta(hours=1))
        .isoformat()
        .replace("+00:00", "Z"),
        "authorizedBy": "workspace-user",
        "concurrentDdlProhibited": True,
        "productionFilesystemWrite": normalized_mode
        in ("Stage", "Apply", "Rollback"),
        "productionDatabaseWrite": normalized_mode == "Apply",
        "productionServiceChange": normalized_mode
        in ("Apply", "Rollback"),
        "officialExpertsPackageChange": False,
        "expectedBuildReceiptSha256": args.build_receipt_sha,
        "expectedReleasePlanReceiptSha256": args.plan_receipt_sha,
        "expectedBackupReceiptSha256": args.backup_receipt_sha,
        "expectedLegacyBaselineReceiptSha256": args.baseline_receipt_sha,
        "expectedAdminRootDependencyAdoptionReceiptSha256":
            args.admin_root_dependency_adoption_receipt_sha,
        "expectedConfigurationReceiptSha256":
            args.configuration_receipt_sha,
        "expectedStageReceiptSha256": args.stage_receipt_sha,
        "expectedDeploymentReceiptSha256":
            args.deployment_receipt_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
    }
    raw = json.dumps(payload, separators=(",", ":")).encode()
    args.approval_sha = release.sha256_bytes(raw)
    args.approval_json_base64 = base64.b64encode(raw).decode()
    return args, payload


def interrupted_apply_recovery_args():
    args, original = release_args("Rollback")
    args.mode = "CanonicalizeInterruptedApplyRecovery"
    args.target_release_id = (
        "w1a-release-b58fd2f22d40-20260724T072400Z"
    )
    args.target_source_commit = (
        "b58fd2f22d40b4e54ca9c728736a51983b5dffe9"
    )
    args.apply_failure_receipt_sha = "8" * 64
    args.apply_failure_manifest_sha = "b" * 64
    args.recovery_plan_receipt_sha = "a" * 64
    payload = {
        "schema": "fbsir.u3wProductionChangeApprovalReceipt.v2",
        "action": "CANONICALIZE_W1A_INTERRUPTED_APPLY_RECOVERY",
        "targetHost": "api2.u3w.com",
        "runId": args.release_id,
        "executorSourceCommit": args.source_commit,
        "approvedAt": original["approvedAt"],
        "expiresAt": original["expiresAt"],
        "authorizedBy": "workspace-user",
        "concurrentDdlProhibited": True,
        "productionFilesystemWrite": True,
        "productionDatabaseWrite": False,
        "productionServiceChange": False,
        "officialExpertsPackageChange": False,
        "targetReleaseId": args.target_release_id,
        "targetSourceCommit": args.target_source_commit,
        "expectedStageReceiptSha256": args.stage_receipt_sha,
        "expectedApplyFailureReceiptSha256":
            args.apply_failure_receipt_sha,
        "expectedApplyFailureManifestSha256":
            args.apply_failure_manifest_sha,
        "approvalNonce": "9" * 32,
        "requestDigest": args.recovery_plan_receipt_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
    }
    return bind_interrupted_recovery_authorization(args, payload)


def bind_interrupted_recovery_authorization(args, approval):
    plan = {
        "schema": release.INTERRUPTED_APPLY_RECOVERY_PLAN_SCHEMA,
        "mode": "RecoveryPlan",
        "state": release.INTERRUPTED_APPLY_RECOVERY_PLAN_STATE,
        "targetHost": release.TARGET_HOST,
        "serviceUnit": release.SERVICE_UNIT,
        "recoveryRunId": args.release_id,
        "executorSourceCommit": args.source_commit,
        "targetReleaseId": args.target_release_id,
        "targetSourceCommit": args.target_source_commit,
        "stageReceiptSha256": args.stage_receipt_sha,
        "applyFailureReceiptSha256":
            args.apply_failure_receipt_sha,
        "applyFailureManifestSha256":
            args.apply_failure_manifest_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "productionFilesystemWrite": True,
        "productionDatabaseWrite": False,
        "productionServiceChange": False,
        "officialExpertsPackageChange": False,
        "generatedAt": approval["approvedAt"],
        "expiresAt": approval["expiresAt"],
    }
    plan_raw = (
        release.canonical_json(plan) + "\n"
    ).encode("utf-8")
    args.recovery_plan_receipt_sha = release.sha256_bytes(plan_raw)
    args.recovery_plan_json_base64 = base64.b64encode(
        plan_raw
    ).decode()
    args.recovery_plan_document = plan
    args.recovery_plan_raw = plan_raw
    approval["expectedStageReceiptSha256"] = args.stage_receipt_sha
    approval["expectedApplyFailureReceiptSha256"] = (
        args.apply_failure_receipt_sha
    )
    approval["expectedApplyFailureManifestSha256"] = (
        args.apply_failure_manifest_sha
    )
    approval["requestDigest"] = args.recovery_plan_receipt_sha
    raw = json.dumps(approval, separators=(",", ":")).encode()
    args.approval_sha = release.sha256_bytes(raw)
    args.approval_json_base64 = base64.b64encode(raw).decode()
    args.approval_raw = raw
    return args, approval


def shifted_iso(value, seconds):
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    return (
        (parsed + dt.timedelta(seconds=seconds))
        .isoformat()
        .replace("+00:00", "Z")
    )


def exact_migration_facts(event_count=0, journey_count=0):
    return {
        "publicReceiptCount": 1,
        "internalReceiptCount": 1,
        "tableCount": 2,
        "triggerCount": 2,
        "permissionCount": 1,
        "eventCount": event_count,
        "probeEventCount": event_count,
        "naturalEventCount": 0,
        "nonProbeEventCount": 0,
        "authoritativeProductCreditCount": 0,
        "journeyCount": journey_count,
        "probeJourneyCount": journey_count,
        "naturalJourneyCount": 0,
        "nonProbeJourneyCount": 0,
        "schemaFingerprintSha256":
            release.EXPECTED_W1A_SCHEMA_FINGERPRINT,
    }


def final_current_read_evidence(
    facts,
    service=None,
    schema="fbsir.u3wDefaultOffFinalCurrentRead.v2",
):
    service = service or {
        "invocationId": "a" * 32,
        "jarSha256": "3" * 64,
    }
    probe_identity = {
        "eventId": "4" * 64,
        "receiptId": "5" * 64,
        "nonceHash": "6" * 64,
        "journeyId": "7" * 64,
    }
    zero_identity_counts = {
        name: 0 for name in probe_identity
    }
    global_counts = {
        "eventCount": facts["eventCount"],
        "journeyCount": facts["journeyCount"],
    }
    return {
        "serviceAfter": service,
        "migrationFacts": facts,
        "finalDefaultOffCurrentRead": {
            "schema": schema,
            "verified": True,
            "serviceStableDuringProbe": True,
            "serviceInvocationId": service["invocationId"],
            "serviceJarSha256": service["jarSha256"],
            "disabledAttributionIngressProbe": {
                "schema":
                    "fbsir.u3wSignedDisabledAttributionIngressProbe.v1",
                "path": release.ATTRIBUTION_INGRESS_PATH,
                "method": "POST",
                "httpStatus": 404,
                "responseDisposition": "ROUTE_NOT_FOUND",
                "verifiedDisabled": True,
                "acceptedDisabledHttpStatuses": [404],
                "trafficClass": "PROBE",
                "signingKeyId": "w1a-active-key",
                "signatureAlgorithm": "hmac-sha256-v1",
                "probeIdentity": probe_identity,
                "identityCountsBefore": zero_identity_counts,
                "identityCountsAfter": dict(zero_identity_counts),
                "globalCountsBefore": global_counts,
                "globalCountsAfter": dict(global_counts),
                "rawNonceDisclosed": False,
                "rawSignatureDisclosed": False,
                "signingKeyMaterialDisclosed": False,
                "secretsDisclosed": False,
                "observedAt": "2026-07-24T00:00:00.000Z",
            },
            "migrationFactsBeforeProbe": facts,
            "migrationFactsAfterProbe": facts,
            "eventAndJourneyCountsUnchanged": True,
        },
    }


def runtime_identity(state="ABSENT", event_count=0, journey_count=0):
    retained = state == "EXACT_043_RETAINED_DORMANT"
    return {
        "environmentSha256": "b" * 64,
        "api2EventKeySha256": "c" * 64,
        "additionalConfigSha256": "e" * 64,
        "databaseEndpoint": "127.0.0.1:3306",
        "database": "fbsir",
        "databaseServerUuid": "uuid",
        "databaseServerVersion": "8.0.45",
        "legacyBaselineReceiptSha256": "a" * 64,
        "legacyBaselineMigrationCount": 1,
        "w1aDatabaseState": state,
        "public043AnyReceiptCount": 1 if retained else 0,
        "public043ReceiptCount": 1 if retained else 0,
        "attributionInternalReceiptCount": 1 if retained else 0,
        "attributionTableCount": 2 if retained else 0,
        "attributionTriggerCount": 2 if retained else 0,
        "attributionPermissionCount": 1 if retained else 0,
        "attributionEventCount": event_count if retained else 0,
        "attributionProbeEventCount": event_count if retained else 0,
        "attributionNaturalEventCount": 0,
        "attributionNonProbeEventCount": 0,
        "attributionAuthoritativeProductCreditCount": 0,
        "attributionJourneyCount": journey_count if retained else 0,
        "attributionProbeJourneyCount": journey_count if retained else 0,
        "attributionNaturalJourneyCount": 0,
        "attributionNonProbeJourneyCount": 0,
        "w1aSchemaFingerprintSha256": (
            release.EXPECTED_W1A_SCHEMA_FINGERPRINT if retained else None
        ),
    }


def interrupted_apply_recovery_fixture(
    root,
    event_count=7,
    journey_count=4,
):
    args, approval = interrupted_apply_recovery_args()
    retained_facts = exact_migration_facts(
        event_count=event_count,
        journey_count=journey_count,
    )
    failure_facts = {
        key: value
        for key, value in exact_migration_facts().items()
        if key in release.LEGACY_MIGRATION_FACT_FIELDS
    }
    staged = {
        "schema": release.LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
        "state": "STAGED_FOR_SWITCH",
        "releaseId": args.target_release_id,
        "sourceCommit": args.target_source_commit,
        "stageApprovalReceiptSha256": "7" * 64,
        "migrationSha256": args.migration_sha,
        "preStageRuntimeIdentity": runtime_identity("ABSENT"),
        "databaseDownClaimed": False,
        "productionDatabaseChanged": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
    }
    apply_failure = {
        "schema": release.LEGACY_APPLY_FAILURE_RECEIPT_SCHEMA,
        "state": (
            "APPLICATION_RESTORED_DATABASE_043_"
            "RETAINED_OR_FAIL_CLOSED"
        ),
        "releaseId": args.target_release_id,
        "sourceCommit": args.target_source_commit,
        "applyApprovalReceiptSha256": "8" * 64,
        "migrationFacts": failure_facts,
        "applicationStarted": True,
        "applicationAlreadyCommitted": False,
        "applicationRestored": True,
        "topologyRestored": True,
        "deploymentCommitOutcome": "NOT_COMMITTED",
        "deploymentReceiptPath": None,
        "deploymentReceiptSha256": None,
        "productionFilesystemChanged": True,
        "productionServiceChangedThisRun": True,
        "productionDatabaseChangedThisRun": True,
        "databaseRollbackStrategy":
            "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
        "databaseDownClaimed": False,
        "officialExpertsPackageChanged": False,
        "observedAt": shifted_iso(approval["approvedAt"], -1),
    }
    current = {
        "activeState": "active",
        "jarSha256": "9" * 64,
        "configuredJarSha256": "9" * 64,
        "configuredFlagValues": {
            name: "false" for name in release.FALSE_FLAGS
        },
        "processFlagValues": {
            name: "false" for name in release.FALSE_FLAGS
        },
        "processForbiddenOverrideNames": [],
    }
    release_dir = pathlib.Path(root) / args.target_release_id
    release_dir.mkdir()
    stage_path = release_dir / "deployment-readiness-receipt.json"
    failure_path = (
        release_dir
        / "apply-failure-20260725T000000000000Z-0123456789ab.json"
    )
    stage_path.write_text(
        release.canonical_json(staged) + "\n",
        encoding="utf-8",
    )
    failure_path.write_text(
        release.canonical_json(apply_failure) + "\n",
        encoding="utf-8",
    )
    args.stage_receipt_sha = release.sha256_file(stage_path)
    args.apply_failure_receipt_sha = release.sha256_file(failure_path)
    failure_manifest = [{
        "name": failure_path.name,
        "sha256": args.apply_failure_receipt_sha,
        "schema": apply_failure["schema"],
        "state": apply_failure["state"],
    }]
    args.apply_failure_manifest_sha = release.sha256_bytes(
        release.canonical_json(failure_manifest).encode("utf-8")
    )
    bind_interrupted_recovery_authorization(args, approval)
    (
        release_dir / release.INTERRUPTED_APPLY_RECOVERY_APPROVAL_NAME
    ).write_bytes(args.approval_raw)
    (
        release_dir / release.INTERRUPTED_APPLY_RECOVERY_PLAN_NAME
    ).write_bytes(args.recovery_plan_raw)
    return types.SimpleNamespace(
        args=args,
        approval=approval,
        retained_facts=retained_facts,
        failure_facts=failure_facts,
        staged=staged,
        apply_failure=apply_failure,
        current=current,
        release_dir=release_dir,
        stage_path=stage_path,
        failure_path=failure_path,
        failure_manifest=failure_manifest,
        recovery_path=(
            release_dir / "interrupted-apply-recovery-receipt.json"
        ),
    )


def interrupted_apply_recovery_receipt(fixture, retained_facts=None):
    args = fixture.args
    return {
        "schema": release.INTERRUPTED_APPLY_RECOVERY_RECEIPT_SCHEMA,
        "state": release.INTERRUPTED_APPLY_RECOVERY_STATE,
        "releaseId": args.target_release_id,
        "sourceCommit": args.target_source_commit,
        "recoveryRunId": args.release_id,
        "executorSourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "approvalNonce": fixture.approval["approvalNonce"],
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "recoveryPlanReceiptSha256":
            args.recovery_plan_receipt_sha,
        "stageReceiptPath": str(fixture.stage_path),
        "stageReceiptSha256": args.stage_receipt_sha,
        "applyFailureReceiptPath": str(fixture.failure_path),
        "applyFailureReceiptSha256":
            args.apply_failure_receipt_sha,
        "applyFailureReceiptSchema":
            fixture.apply_failure["schema"],
        "applyFailureReceiptState":
            fixture.apply_failure["state"],
        "applyFailureReceiptManifest": fixture.failure_manifest,
        "applyFailureReceiptManifestSha256":
            args.apply_failure_manifest_sha,
        "applicationRestored": True,
        "topologyRestored": True,
        "deploymentCommitOutcome": "NOT_COMMITTED",
        "deploymentReceiptAbsent": True,
        "rollbackReceiptAbsent": True,
        "currentLinkAbsent": True,
        "releaseDropInMatched": True,
        "retainedMigrationFacts": (
            retained_facts
            if retained_facts is not None
            else fixture.retained_facts
        ),
        "allW1aFlagsExplicitFalse": True,
        "databaseDownClaimed": False,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": True,
        "productionDatabaseChangedThisRecoveryRun": False,
        "productionDatabaseChangedSinceStage": True,
        "productionServiceChanged": True,
        "productionServiceChangedThisRecoveryRun": False,
        "productionServiceChangedSinceStage": True,
        "officialExpertsPackageChanged": False,
        "observedAt": shifted_iso(
            fixture.approval["approvedAt"], 1
        ),
    }


def stage_recovery_predecessor_fixture(
    root,
    receipt_event_count=7,
    receipt_journey_count=4,
    current_event_count=9,
    current_journey_count=5,
):
    fixture = interrupted_apply_recovery_fixture(
        root,
        event_count=receipt_event_count,
        journey_count=receipt_journey_count,
    )
    receipt = interrupted_apply_recovery_receipt(fixture)
    fixture.recovery_path.write_text(
        release.canonical_json(receipt) + "\n",
        encoding="utf-8",
    )
    (
        fixture.release_dir
        / release.INTERRUPTED_APPLY_RECOVERY_APPROVAL_NAME
    ).write_bytes(fixture.args.approval_raw)
    (
        fixture.release_dir
        / release.INTERRUPTED_APPLY_RECOVERY_PLAN_NAME
    ).write_bytes(fixture.args.recovery_plan_raw)
    receipt_sha256 = release.sha256_file(fixture.recovery_path)
    evidence = fixture.release_dir / "evidence"
    evidence.mkdir()
    rollback_dropin = evidence / "rollback-systemd-dropin.conf"
    rollback_dropin.write_bytes(b"[Service]\nExecStart=predecessor\n")
    dropin = pathlib.Path(root) / "live-rollback-systemd-dropin.conf"
    dropin.write_bytes(rollback_dropin.read_bytes())
    nginx = pathlib.Path(root) / "live-nginx.conf"
    nginx.write_bytes(b"server { listen 443 ssl; }\n")
    current_facts = exact_migration_facts(
        event_count=current_event_count,
        journey_count=current_journey_count,
    )
    previous = {
        "previousJarSha256": fixture.current["jarSha256"],
        "previousNginxSha256": release.sha256_file(nginx),
    }
    lease = mock.MagicMock()
    lease.mysql = object()
    lease.facts = current_facts
    return types.SimpleNamespace(
        fixture=fixture,
        receipt=receipt,
        receipt_sha256=receipt_sha256,
        current=json.loads(json.dumps(fixture.current)),
        current_facts=current_facts,
        previous=previous,
        rollback_dropin=rollback_dropin,
        dropin=dropin,
        nginx=nginx,
        current_link=pathlib.Path(root) / "current",
        lease=lease,
    )


def validate_stage_recovery_predecessor(
    context,
    *,
    latest_path=None,
    current=None,
    current_facts=None,
):
    latest_path = latest_path or context.fixture.recovery_path
    current = (
        json.loads(json.dumps(context.current))
        if current is None
        else current
    )
    current_facts = (
        json.loads(json.dumps(context.current_facts))
        if current_facts is None
        else current_facts
    )
    with contextlib.ExitStack() as stack:
        for patcher in (
            mock.patch.object(
                release,
                "validated_latest_receipt_target",
                return_value=pathlib.Path(latest_path),
            ),
            mock.patch.object(
                release,
                "interrupted_apply_target_release",
                return_value=context.fixture.release_dir,
            ),
            mock.patch.object(
                release,
                "validate_regular_file",
                side_effect=lambda path, *_args, **_kwargs:
                    pathlib.Path(path),
            ),
            mock.patch.object(
                release,
                "predecessor_facts",
                return_value=context.previous,
            ),
            mock.patch.object(
                release,
                "assert_restored_predecessor_runtime_contract",
            ),
            mock.patch.object(
                release,
                "interrupted_apply_recovery_lease",
                return_value=context.lease,
            ),
            mock.patch.object(
                release,
                "exact_migration_facts",
                return_value=current_facts,
            ),
            mock.patch.object(
                release,
                "environment_flags_explicit_false",
                return_value=True,
            ),
            mock.patch.object(release, "wait_for_u3w_health"),
            mock.patch.object(release, "DROPIN_PATH", context.dropin),
            mock.patch.object(release, "NGINX_PATH", context.nginx),
            mock.patch.object(
                release,
                "CURRENT_LINK",
                context.current_link,
            ),
        ):
            stack.enter_context(patcher)
        return release.validate_prior_interrupted_recovery_stage_entry(
            current
        )


def configured_environment_values():
    event_material = b"independent-secret-hmac-key-material"
    binding_material = b"independent-same-binding-key"
    values = {
        name: "false" for name in release.FALSE_FLAGS
    }
    values.update({
        release.EVENT_KEY_ID_NAME: "w1a-active-key",
        release.EVENT_KEY_NAME: "base64:" + base64.b64encode(
            event_material
        ).decode("ascii"),
        release.PREVIOUS_EVENT_KEY_ID_NAME: "",
        release.PREVIOUS_EVENT_KEY_NAME: "",
        release.SAME_BINDING_KEY_NAME: "base64:" + base64.b64encode(
            binding_material
        ).decode("ascii"),
        "WXFBSIR_MYSQL_URL": "jdbc:mysql://db/fbsir",
        "WXFBSIR_MYSQL_USERNAME": "sensitive-user",
        "WXFBSIR_MYSQL_PASSWORD": "sensitive-password",
        "FBSIR_MYSQL_URL": "jdbc:mysql://db/fbsir",
        "FBSIR_MYSQL_USERNAME": "sensitive-user",
        "FBSIR_MYSQL_PASSWORD": "sensitive-password",
        release.ADMIN_ENGINE_TOKEN_NAME:
            "engine-credential-" + ("z" * 40),
    })
    return values, event_material


def plan_target_fixture():
    target = {field: None for field in release.PLAN_TARGET_FIELDS}
    target.update({
        "schema": "fbsir.u3wDefaultOffRemotePlanSnapshot.v1",
        "targetHost": release.TARGET_HOST,
        "serviceUnit": release.SERVICE_UNIT,
        "service": {"ActiveState": "active"},
        "unitSha256": "1" * 64,
        "fragmentFileManifest": {"sha256": "1" * 64},
        "dropInManifest": [],
        "unitFiles": [],
        "environmentCustody": {"mode": "0o600"},
        "environmentSha256": "2" * 64,
        "environmentFilePaths": [release.ENV_PATH_TEXT],
        "environmentFileManifest": [{
            "path": release.ENV_PATH_TEXT,
            "ignoreErrors": False,
            "sha256": "2" * 64,
        }],
        "additionalConfigCustody": {"mode": "0o644"},
        "additionalConfigSha256": "3" * 64,
        "externalConfigManifest": [{"sha256": "3" * 64}],
        "activeJarPath": "/opt/fbsir/admin/fbsir-admin.jar",
        "activeJarSha256": "4" * 64,
        "configuredJarPath": "/opt/fbsir/admin/fbsir-admin.jar",
        "configuredJarSha256": "4" * 64,
        "processJarPath": "/opt/fbsir/admin/fbsir-admin.jar",
        "processJarSha256": "4" * 64,
        "processArgvSha256": "5" * 64,
        "processEnvironmentNamesSha256": "6" * 64,
        "processFlagValues": {
            name: None for name in release.FALSE_FLAGS
        },
        "configuredFlagValues": {
            name: "false" for name in release.FALSE_FLAGS
        },
        "processForbiddenOverrideNames": [],
        "api2EventKeyManifest": {"sha256": "7" * 64},
        "processSecurityConfigurationNames": [],
        "processSecurityConfigurationHmacSha256": "8" * 64,
        "expectedSecurityConfigurationNames": [],
        "expectedSecurityConfigurationHmacSha256": "8" * 64,
        "processDatabaseBindingMatched": True,
        "configuredEnvironmentSha256": "2" * 64,
        "configuredEnvironmentNames": [],
        "configuredEnvironmentHmacSha256": "9" * 64,
        "processConfiguredEnvironmentHmacSha256": "9" * 64,
        "processConfiguredEnvironmentMatched": False,
        "processConfiguredEnvironmentMismatchNames": [],
        "processPendingRestartEnvironmentNames":
            sorted(release.MANAGED_RESTART_ENVIRONMENT_NAMES),
        "processConfiguredEnvironmentLoadState":
            "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART",
        "processConfiguredEnvironmentPreStageCompatible": True,
        "nginxConfigs": [{"path": "/etc/nginx/nginx.conf"}],
        "activeNginxManifest": [{
            "path": "/etc/nginx/nginx.conf",
        }],
        "nginxDumpSha256": "a" * 64,
        "releaseRootExists": False,
        "releaseRootEntryManifest": [],
        "currentLinkExists": False,
        "currentLinkResolved": None,
        "currentLifecycleState": "UNTOUCHED_LEGACY",
        "stageEntryTopology": {
            "state": "UNTOUCHED_LEGACY",
            "priorRollbackAnchor": None,
        },
        "productionChanged": False,
        "productionChangedByPlan": False,
    })
    return target


class ReleaseWorkerContractTest(unittest.TestCase):
    def test_global_release_lock_is_bounded(self):
        with (
            mock.patch.object(
                release.fcntl,
                "flock",
                side_effect=BlockingIOError(),
            ),
            mock.patch.object(
                release.time,
                "monotonic",
                side_effect=[0.0, 31.0],
            ),
            mock.patch.object(release.time, "sleep"),
        ):
            with self.assertRaisesRegex(RuntimeError, "timed out"):
                release.acquire_release_lock(
                    123,
                    release.fcntl.LOCK_EX,
                    timeout_seconds=30,
                )

    def test_target_revalidates_plan_time_after_lock(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            args = types.SimpleNamespace(
                mode="Apply",
                release_id=(
                    "w1a-release-aaaaaaaaaaaa-20260724T180000Z"
                ),
                source_commit="a" * 40,
                plan_receipt_sha="",
            )
            plan_path = (
                root
                / args.release_id
                / "evidence"
                / "release-plan.json"
            )
            plan_path.parent.mkdir(parents=True)
            plan = {
                "schema": "fbsir.u3wDefaultOffReleasePlan.v2",
                "releaseId": args.release_id,
                "sourceCommit": args.source_commit,
                "generatedAt": "2026-07-24T17:00:00Z",
                "expiresAt": "2026-07-24T18:00:00Z",
            }
            plan_path.write_bytes(
                release.canonical_json(plan).encode("utf-8")
            )
            args.plan_receipt_sha = release.sha256_file(plan_path)
            with (
                mock.patch.object(release, "RELEASE_ROOT", root),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    side_effect=lambda path, *_args, **_kwargs:
                        pathlib.Path(path),
                ),
            ):
                release.validate_target_plan_time(
                    args,
                    now=dt.datetime(
                        2026,
                        7,
                        24,
                        17,
                        30,
                        tzinfo=dt.timezone.utc,
                    ),
                )
                with self.assertRaisesRegex(RuntimeError, "expired"):
                    release.validate_target_plan_time(
                        args,
                        now=dt.datetime(
                            2026,
                            7,
                            24,
                            18,
                            0,
                            tzinfo=dt.timezone.utc,
                        ),
                    )
                args.mode = "Rollback"
                release.validate_target_plan_time(
                    args,
                    now=dt.datetime(
                        2026,
                        7,
                        25,
                        tzinfo=dt.timezone.utc,
                    ),
                )

    def test_environment_requires_url_safe_independent_engine_credential(self):
        values, _ = configured_environment_values()
        with tempfile.TemporaryDirectory() as temporary:
            environment_path = pathlib.Path(temporary) / "fbsir-admin.env"

            def parse(candidate):
                environment_path.write_text(
                    "\n".join(
                        "{}={}".format(name, value)
                        for name, value in candidate.items()
                    ) + "\n",
                    encoding="utf-8",
                )
                with (
                    mock.patch.object(release, "ENV_PATH", environment_path),
                    mock.patch.object(
                        release,
                        "validate_regular_file",
                        return_value=environment_path,
                    ),
                ):
                    return release.parse_environment()

            parsed, _ = parse(values)
            self.assertEqual(
                parsed[release.ADMIN_ENGINE_TOKEN_NAME],
                values[release.ADMIN_ENGINE_TOKEN_NAME],
            )

            short = dict(values)
            short[release.ADMIN_ENGINE_TOKEN_NAME] = "short"
            with self.assertRaisesRegex(RuntimeError, "shape"):
                parse(short)

            invalid = dict(values)
            invalid[release.ADMIN_ENGINE_TOKEN_NAME] = "!" * 64
            with self.assertRaisesRegex(RuntimeError, "shape"):
                parse(invalid)

            reused = dict(values)
            credential = "engine-credential-" + ("x" * 40)
            reused[release.ADMIN_ENGINE_TOKEN_NAME] = credential
            reused[release.EVENT_KEY_NAME] = "utf8:" + credential
            with self.assertRaisesRegex(RuntimeError, "independent"):
                parse(reused)

    def test_runtime_anchor_accepts_only_exact_adopted_engine_delta(self):
        credential = "engine-credential-" + ("a" * 40)
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            environment_path = root / "etc" / "fbsir-admin.env"
            event_key_path = root / "etc" / "event-key"
            run_directory = (
                root
                / "configuration"
                / "w1a"
                / "w1a-config-20260724T180000Z-0123456789ab"
            )
            run_directory.mkdir(parents=True)
            environment_path.parent.mkdir(parents=True, exist_ok=True)
            event_key_path.parent.mkdir(parents=True, exist_ok=True)
            predecessor_environment = (
                "FBSIR_BOARD_ATTRIBUTION_ENABLED=false\n"
            )
            current_environment = predecessor_environment + (
                "{}={}\n".format(
                    release.ADMIN_ENGINE_TOKEN_NAME,
                    credential,
                )
            )
            environment_path.write_bytes(
                current_environment.encode("utf-8")
            )
            event_key_path.write_bytes(b"event-material-" + (b"e" * 40))
            predecessor = {
                "schema":
                    "fbsir.u3wDefaultOffConfigurationReceipt.v2",
                "environmentAfterSha256": hashlib.sha256(
                    predecessor_environment.encode("utf-8")
                ).hexdigest(),
                "api2EventKeyPath": str(event_key_path),
                "stagedKeyMaterialMatched": True,
                "officialExpertsPackageChanged": False,
                "secretsDisclosed": False,
            }
            predecessor_path = (
                run_directory / "configuration-receipt.json"
            )
            predecessor_path.write_text(
                json.dumps(predecessor, separators=(",", ":")),
                encoding="utf-8",
            )
            os.chmod(predecessor_path, 0o600)
            predecessor_sha = release.sha256_file(predecessor_path)
            evidence = {
                "allManagedKeysPresent": True,
                "allDefaultOffFlagsExplicitFalse": True,
                "activeEventKeyPairValid": True,
                "previousEventKeyPairCompleteAndValid": True,
                "sameBindingSecretValidAndIndependent": True,
                "api2RawEventKeyMatchesU3wActiveMaterial": True,
                "adminEngineCredentialValid": True,
                "adminEngineCredentialIndependent": True,
                "adminEngineCredentialMinimumCharacters": len(credential),
                "secretsDisclosed": False,
            }
            receipt = {
                "schema":
                    "fbsir.u3wDefaultOffConfigurationReceipt.v3",
                "environmentBeforeSha256": hashlib.sha256(
                    current_environment.encode("utf-8")
                ).hexdigest(),
                "environmentAfterSha256": hashlib.sha256(
                    current_environment.encode("utf-8")
                ).hexdigest(),
                "api2EventKeyPath": str(event_key_path),
                "stagedKeyMaterialMatched": True,
                "serviceRestarted": False,
                "productionConfigurationChanged": False,
                "productionServiceChanged": False,
                "officialExpertsPackageChanged": False,
                "secretsDisclosed": False,
                "configurationEvidence": evidence,
                "predecessorConfigurationReceiptSha256":
                    predecessor_sha,
                "adminEngineCredentialProvisioningState":
                    "ADOPTED_EXISTING_EXACT_DELTA",
                "engineCounterpartClosureClaimed": False,
                "api2EventKeyProvisioningState":
                    "REUSED_FROM_PREDECESSOR_RECEIPT",
            }
            with (
                mock.patch.object(release, "ADMIN_ROOT", root),
                mock.patch.object(
                    release,
                    "ENV_PATH",
                    environment_path,
                ),
                mock.patch.object(
                    release,
                    "API2_EVENT_KEY_PATH",
                    event_key_path,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    side_effect=lambda path, *_args, **_kwargs:
                        pathlib.Path(path),
                ),
            ):
                accepted = release.validate_configuration_runtime_anchor(
                    receipt
                )
                self.assertIs(accepted, receipt)

                environment_path.write_bytes(
                    (
                        "UNRELATED=drift\n" + current_environment
                    ).encode("utf-8")
                )
                with self.assertRaisesRegex(
                    RuntimeError,
                    "configuration",
                ):
                    release.validate_configuration_runtime_anchor(receipt)

    def test_prior_rollback_accepts_only_receipt_bound_engine_evolution(self):
        predecessor_sha = "1" * 64
        current_sha = "2" * 64
        receipt = {
            "schema": "fbsir.u3wDefaultOffConfigurationReceipt.v3",
            "environmentAfterSha256": current_sha,
            "predecessorConfigurationReceiptSha256": "3" * 64,
            "adminEngineCredentialProvisioningState":
                "ADOPTED_EXISTING_EXACT_DELTA",
            "engineCounterpartClosureClaimed": False,
        }
        predecessor_receipt = {
            "environmentAfterSha256": predecessor_sha,
        }
        previous = {
            "configuredEnvironmentSha256": predecessor_sha,
            "environmentFilePaths": [str(release.ENV_PATH)],
            "environmentFileManifest": [{
                "path": str(release.ENV_PATH),
                "sha256": predecessor_sha,
                "mode": 0o600,
                "uid": 0,
                "gid": 0,
                "nlink": 1,
            }],
            "configuredEnvironmentNames": ["BASE"],
            "expectedSecurityConfigurationNames": ["BASE"],
            "api2EventKeyManifest": {"sha256": "4" * 64},
            "configuredFlagValues": {
                name: "false" for name in release.FALSE_FLAGS
            },
        }
        current = {
            **previous,
            "configuredEnvironmentSha256": current_sha,
            "environmentFileManifest": [{
                **previous["environmentFileManifest"][0],
                "sha256": current_sha,
            }],
            "configuredEnvironmentNames": sorted([
                "BASE",
                release.ADMIN_ENGINE_TOKEN_NAME,
            ]),
            "expectedSecurityConfigurationNames": sorted([
                "BASE",
                release.ADMIN_ENGINE_TOKEN_NAME,
            ]),
            "processSecurityConfigurationNames": sorted([
                "BASE",
                release.ADMIN_ENGINE_TOKEN_NAME,
            ]),
            "processSecurityConfigurationHmacSha256": "5" * 64,
            "expectedSecurityConfigurationHmacSha256": "5" * 64,
            "processDatabaseBindingMatched": True,
            "processConfiguredEnvironmentMatched": True,
            "processConfiguredEnvironmentPreStageCompatible": True,
            "processConfiguredEnvironmentMismatchNames": [],
            "processPendingRestartEnvironmentNames": [],
            "processConfiguredEnvironmentLoadState": "EXACT_CONFIGURED",
            "processForbiddenOverrideNames": [],
            "processFlagValues": {
                name: "false" for name in release.FALSE_FLAGS
            },
        }
        with (
            mock.patch.object(release, "read_json", return_value=receipt),
            mock.patch.object(
                release,
                "validate_configuration_runtime_anchor",
                return_value=receipt,
            ),
            mock.patch.object(
                release,
                "configuration_predecessor_receipt",
                return_value=predecessor_receipt,
            ),
        ):
            self.assertTrue(
                release
                .authorized_admin_engine_configuration_evolution_matches(
                    current,
                    previous,
                )
            )
            drifted = dict(current)
            drifted["configuredEnvironmentNames"] = sorted(
                current["configuredEnvironmentNames"] + ["UNRELATED"]
            )
            self.assertFalse(
                release
                .authorized_admin_engine_configuration_evolution_matches(
                    drifted,
                    previous,
                )
            )

    def test_migration_parser_preserves_routines_on_one_locked_session(self):
        migration = (
            ROOT.parent
            / "sql/update_20260723_independent_board_attribution_v1.sql"
        ).read_text(encoding="utf-8")
        statements = release.parse_mysql_script(migration)
        self.assertEqual(len(statements), 12)
        self.assertEqual(
            sum("CREATE TRIGGER" in statement for statement in statements),
            2,
        )
        self.assertEqual(
            sum("CREATE PROCEDURE" in statement for statement in statements),
            1,
        )
        self.assertTrue(
            all("DELIMITER" not in statement for statement in statements)
        )
        self.assertIn(
            "SIGNAL SQLSTATE '45000'",
            next(
                statement
                for statement in statements
                if "CREATE TRIGGER" in statement
            ),
        )
        with self.assertRaisesRegex(RuntimeError, "unterminated"):
            release.parse_mysql_script("SELECT 1")
        with self.assertRaisesRegex(RuntimeError, "inside a statement"):
            release.parse_mysql_script(
                "SELECT 1\nDELIMITER $$\n"
            )

    def test_apply_migration_never_opens_a_second_mysql_connection(self):
        source = inspect.getsource(release.apply_migration)
        self.assertIn("mysql.execute_script", source)
        self.assertIn("return MigrationLease", source)
        self.assertNotIn("/usr/bin/mysql", source)
        self.assertNotIn("subprocess.run", source)

    def test_migration_structure_is_exact_but_ledger_counts_are_monotonic(self):
        baseline = exact_migration_facts(4, 2)
        grown = exact_migration_facts(7, 3)
        self.assertTrue(release.migration_structure_matches(baseline))
        self.assertTrue(
            release.migration_counts_are_monotonic(grown, baseline)
        )
        self.assertFalse(
            release.migration_counts_are_monotonic(baseline, grown)
        )
        boolean_count = dict(baseline)
        boolean_count["eventCount"] = True
        self.assertFalse(
            release.migration_structure_matches(boolean_count)
        )
        unsafe_natural = dict(baseline)
        unsafe_natural["probeEventCount"] = 3
        unsafe_natural["naturalEventCount"] = 1
        unsafe_natural["nonProbeEventCount"] = 1
        self.assertFalse(
            release.migration_structure_matches(unsafe_natural)
        )

    def test_legacy_recorded_facts_bind_only_to_safe_current_read(self):
        current = exact_migration_facts(4, 2)
        legacy = {
            field: current[field]
            for field in release.LEGACY_MIGRATION_FACT_FIELDS
        }
        self.assertTrue(
            release.legacy_migration_structure_matches(legacy)
        )
        self.assertFalse(release.migration_structure_matches(legacy))
        self.assertTrue(
            release.recorded_migration_facts_match_current(
                legacy,
                current,
                release.LEGACY_ROLLBACK_RECEIPT_SCHEMA,
            )
        )
        self.assertFalse(
            release.recorded_migration_facts_match_current(
                legacy,
                current,
                release.ROLLBACK_RECEIPT_SCHEMA,
            )
        )
        unsafe_live = dict(current)
        unsafe_live["probeEventCount"] = 3
        unsafe_live["naturalEventCount"] = 1
        unsafe_live["nonProbeEventCount"] = 1
        self.assertFalse(
            release.recorded_migration_facts_match_current(
                legacy,
                unsafe_live,
                release.LEGACY_ROLLBACK_RECEIPT_SCHEMA,
            )
        )
        grown = exact_migration_facts(5, 3)
        self.assertTrue(
            release.recorded_migration_counts_are_monotonic(
                grown,
                legacy,
                release.LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
            )
        )
        self.assertFalse(
            release.recorded_migration_counts_are_monotonic(
                current,
                {
                    **legacy,
                    "eventCount": 5,
                },
                release.LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
            )
        )

    def test_legacy_deployment_contract_is_read_only_compatible(self):
        current = exact_migration_facts(4, 2)
        legacy = {
            field: current[field]
            for field in release.LEGACY_MIGRATION_FACT_FIELDS
        }
        identity = runtime_identity(
            "EXACT_043_RETAINED_DORMANT",
            event_count=4,
            journey_count=2,
        )
        for field in (
            "attributionProbeEventCount",
            "attributionNaturalEventCount",
            "attributionNonProbeEventCount",
            "attributionAuthoritativeProductCreditCount",
            "attributionProbeJourneyCount",
            "attributionNaturalJourneyCount",
            "attributionNonProbeJourneyCount",
        ):
            identity.pop(field)
        self.assertTrue(
            release.recorded_migration_facts_match_runtime_identity(
                legacy,
                identity,
                release.LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
            )
        )
        schemas = release.deployment_contract_schemas(
            release.LEGACY_DEPLOYMENT_RECEIPT_SCHEMA
        )
        self.assertEqual(
            schemas,
            {
                "finalCurrentRead":
                    release.LEGACY_FINAL_CURRENT_READ_SCHEMA,
                "databaseRollbackSafety":
                    release.LEGACY_DATABASE_ROLLBACK_SAFETY_SCHEMA,
                "applicationRollbackExecution":
                    release.LEGACY_APPLICATION_ROLLBACK_EXECUTION_SCHEMA,
            },
        )
        evidence = final_current_read_evidence(
            legacy,
            schema=release.LEGACY_FINAL_CURRENT_READ_SCHEMA,
        )
        release.validate_final_default_off_current_read(
            evidence["finalDefaultOffCurrentRead"],
            evidence["serviceAfter"],
            legacy,
            release.LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
        )
        with self.assertRaisesRegex(RuntimeError, "current-read evidence"):
            release.validate_final_default_off_current_read(
                evidence["finalDefaultOffCurrentRead"],
                evidence["serviceAfter"],
                legacy,
                release.DEPLOYMENT_RECEIPT_SCHEMA,
            )

    def test_exact_schema_enumerates_all_w1a_triggers_and_fk_rules(self):
        fingerprint = inspect.getsource(
            release.migration_fingerprint_rows
        )
        exact = inspect.getsource(release.exact_migration_facts)
        state = inspect.getsource(release.w1a_database_state)
        for source in (fingerprint, exact, state):
            self.assertIn("event_object_table IN", source)
        self.assertIn(
            "information_schema.referential_constraints", fingerprint
        )
        self.assertIn("HEX(update_rule)", fingerprint)
        self.assertIn("HEX(delete_rule)", fingerprint)
        self.assertNotIn("trigger_name IN", fingerprint)
        self.assertNotIn("trigger_name IN", exact)
        self.assertNotIn("trigger_name IN", state)

    def test_verify_and_apply_reentry_allow_append_only_ledger_growth(self):
        verify = inspect.getsource(release.verify_release)
        apply = inspect.getsource(release.apply_release)
        self.assertIn("deployed_migration_lease", verify)
        self.assertIn("migration_counts_are_monotonic", verify)
        self.assertNotIn("validate_stage_runtime_identity", verify)
        existing_branch = apply[
            apply.index("if deployment_path.exists():"):
            apply.index("recovered_existing_side_effects = False")
        ]
        self.assertIn("deployed_migration_lease", existing_branch)
        self.assertIn("migration_counts_are_monotonic", existing_branch)
        self.assertNotIn("apply_migration(", existing_branch)
        self.assertNotIn("restore_application(", existing_branch)
        self.assertNotIn(
            "assert_release_owned_recovery_topology", existing_branch
        )
        self.assertIn(
            "existing deployment validation failed", existing_branch
        )
        self.assertNotIn('get("eventCount") == 0', verify)
        self.assertNotIn('get("journeyCount") == 0', verify)

    def test_apply_reentry_db_observation_failure_never_restores_service(self):
        args, _ = release_args("Apply")
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            stage_path = (
                release_dir / "deployment-readiness-receipt.json"
            )
            deployment_path = release_dir / "deployment-receipt.json"
            stage_path.write_text("{}\n", encoding="utf-8")
            deployment_path.write_text("{}\n", encoding="utf-8")
            existing = {
                "schema": "fbsir.u3wW1aDeploymentReadinessReceipt.v3",
                "state": "DEPLOYED_DEFAULT_OFF",
                "releaseId": args.release_id,
                "sourceCommit": args.source_commit,
                "adminRootDependencyAdoptionReceiptSha256":
                    args.admin_root_dependency_adoption_receipt_sha,
                "stageReceiptSha256": "f" * 64,
                "applyApprovalReceiptSha256": "a" * 64,
                "productionDatabaseChanged": True,
                "productionDatabaseChangedThisRun": True,
                "productionDatabaseChangedSinceStage": True,
                **final_current_read_evidence(exact_migration_facts()),
            }
            with (
                mock.patch.object(
                    release, "validate_preparation_anchors"
                ),
                mock.patch.object(
                    release, "release_directory", return_value=release_dir
                ),
                mock.patch.object(
                    release,
                    "validate_stage_receipt",
                    return_value=(stage_path, {}),
                ),
                mock.patch.object(
                    release,
                    "service_snapshot",
                    return_value={"activeState": "active"},
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=deployment_path,
                ),
                mock.patch.object(
                    release, "read_json", return_value=existing
                ),
                mock.patch.object(
                    release, "sha256_file", return_value="f" * 64
                ),
                mock.patch.object(
                    release,
                    "deployed_migration_lease",
                    side_effect=RuntimeError("named lock unavailable"),
                ),
                mock.patch.object(
                    release,
                    "write_apply_failure_receipt",
                    return_value=release_dir / (
                        "apply-failure-20260723T180000000000Z-"
                        "0123456789ab.json"
                    ),
                ) as failure_receipt,
                mock.patch.object(
                    release, "restore_application"
                ) as restore,
            ):
                with self.assertRaisesRegex(
                    RuntimeError, "without topology mutation"
                ):
                    release.apply_release(args)
            failure_receipt.assert_called_once()
            restore.assert_not_called()

    def test_apply_reentry_repairs_latest_only_when_needed(self):
        args, _ = release_args("Apply")
        facts = exact_migration_facts(3, 2)
        for latest_matched in (False, True):
            with self.subTest(latest_matched=latest_matched):
                with tempfile.TemporaryDirectory() as temporary:
                    release_dir = pathlib.Path(temporary)
                    stage_path = (
                        release_dir
                        / "deployment-readiness-receipt.json"
                    )
                    deployment_path = (
                        release_dir / "deployment-receipt.json"
                    )
                    stage_path.write_text("{}\n", encoding="utf-8")
                    deployment_path.write_text("{}\n", encoding="utf-8")
                    existing = {
                        "schema":
                            "fbsir.u3wW1aDeploymentReadinessReceipt.v3",
                        "state": "DEPLOYED_DEFAULT_OFF",
                        "releaseId": args.release_id,
                        "sourceCommit": args.source_commit,
                        "adminRootDependencyAdoptionReceiptSha256":
                            args.admin_root_dependency_adoption_receipt_sha,
                        "stageReceiptSha256": "f" * 64,
                        "applyApprovalReceiptSha256": "a" * 64,
                        "productionDatabaseChanged": False,
                        "productionDatabaseChangedThisRun": False,
                        "productionDatabaseChangedSinceStage": False,
                        **final_current_read_evidence(facts),
                    }
                    lease = types.SimpleNamespace(
                        mysql=object(),
                        connection={},
                        facts=facts,
                        close=mock.Mock(),
                    )
                    with (
                        mock.patch.object(
                            release, "validate_preparation_anchors"
                        ),
                        mock.patch.object(
                            release,
                            "release_directory",
                            return_value=release_dir,
                        ),
                        mock.patch.object(
                            release,
                            "validate_stage_receipt",
                            return_value=(
                                stage_path,
                                {
                                    "preStageRuntimeIdentity":
                                        runtime_identity()
                                },
                            ),
                        ),
                        mock.patch.object(
                            release,
                            "service_snapshot",
                            return_value={"activeState": "active"},
                        ),
                        mock.patch.object(
                            release,
                            "validate_regular_file",
                            return_value=deployment_path,
                        ),
                        mock.patch.object(
                            release, "read_json", return_value=existing
                        ),
                        mock.patch.object(
                            release, "sha256_file", return_value="f" * 64
                        ),
                        mock.patch.object(
                            release,
                            "deployed_migration_lease",
                            return_value=lease,
                        ),
                        mock.patch.object(
                            release, "validate_release_evidence"
                        ),
                        mock.patch.object(
                            release, "assert_candidate_active"
                        ),
                        mock.patch.object(
                            release, "validate_apply_session_identity"
                        ),
                        mock.patch.object(
                            release,
                            "exact_migration_facts",
                            return_value=facts,
                        ),
                        mock.patch.object(
                            release,
                            "migration_counts_are_monotonic",
                            return_value=True,
                        ),
                        mock.patch.object(
                            release,
                            "final_default_off_current_read",
                            return_value=(
                                existing["serviceAfter"],
                                existing[
                                    "finalDefaultOffCurrentRead"
                                ],
                            ),
                        ),
                        mock.patch.object(
                            release,
                            "advance_latest_receipt",
                            return_value=not latest_matched,
                        ) as repair,
                        mock.patch.object(
                            release,
                            "apply_worker_result",
                            side_effect=lambda *a, **kw: kw,
                        ),
                    ):
                        result = release.apply_release(args)
                    self.assertEqual(
                        result["filesystem_changed"],
                        not latest_matched,
                    )
                    self.assertFalse(
                        result["database_changed_this_run"]
                    )
                    self.assertFalse(
                        result["database_changed_since_stage"]
                    )
                    self.assertFalse(result["service_changed"])
                    self.assertFalse(result["deployment_committed"])
                    self.assertEqual(
                        result["latest_repaired"], not latest_matched
                    )
                    repair.assert_called_once_with(
                        deployment_path, [stage_path]
                    )

    def test_process_database_binding_is_secret_safe_and_value_sensitive(self):
        expected, event_material = configured_environment_values()
        event_key_path = mock.Mock()
        event_key_path.parent = pathlib.Path("/etc/u3w/secrets")
        event_key_path.read_bytes.return_value = event_material
        with (
            mock.patch.object(release, "validate_regular_file"),
            mock.patch.object(
                release, "sha256_file", return_value="a" * 64
            ),
            mock.patch.object(
                release,
                "root_regular_manifest",
                return_value={
                    "path": "/etc/u3w/secrets/"
                    "independent-board-event-hmac.key",
                    "sha256": "b" * 64,
                    "mode": 0o600,
                    "uid": 0,
                    "gid": 0,
                    "nlink": 1,
                },
            ),
            mock.patch.object(
                release, "API2_EVENT_KEY_PATH", event_key_path
            ),
        ):
            matched = release.security_configuration_evidence(
                dict(expected), expected
            )
            drifted_values = dict(expected)
            drifted_values["WXFBSIR_MYSQL_PASSWORD"] = "wrong-password"
            drifted = release.security_configuration_evidence(
                drifted_values, expected
            )
        self.assertTrue(matched["processDatabaseBindingMatched"])
        self.assertTrue(
            matched["processConfiguredEnvironmentMatched"]
        )
        self.assertEqual(
            matched["processConfiguredEnvironmentLoadState"],
            "EXACT_CONFIGURED",
        )
        self.assertFalse(drifted["processDatabaseBindingMatched"])
        self.assertFalse(
            drifted["processConfiguredEnvironmentMatched"]
        )
        self.assertEqual(
            matched["processSecurityConfigurationNames"],
            drifted["processSecurityConfigurationNames"],
        )
        self.assertNotEqual(
            matched["processSecurityConfigurationHmacSha256"],
            drifted["processSecurityConfigurationHmacSha256"],
        )
        serialized = json.dumps([matched, drifted])
        for secret in (
            "sensitive-user",
            "sensitive-password",
            "wrong-password",
            "jdbc:mysql://db/fbsir",
            "independent-secret-hmac-key",
        ):
            self.assertNotIn(secret, serialized)

    def test_process_environment_accepts_only_exact_or_full_pending_states(self):
        expected, event_material = configured_environment_values()
        legacy_process = {
            name: value
            for name, value in expected.items()
            if name not in release.MANAGED_RESTART_ENVIRONMENT_NAMES
        }
        engine_pending_process = {
            name: value
            for name, value in expected.items()
            if name != release.ADMIN_ENGINE_TOKEN_NAME
        }
        partial_process = dict(legacy_process)
        partial_process[release.FALSE_FLAGS[0]] = "false"
        event_key_path = mock.Mock()
        event_key_path.parent = pathlib.Path("/etc/u3w/secrets")
        event_key_path.read_bytes.return_value = event_material
        with (
            mock.patch.object(
                release,
                "root_regular_manifest",
                return_value={"sha256": "b" * 64},
            ),
            mock.patch.object(
                release, "sha256_file", return_value="a" * 64
            ),
            mock.patch.object(
                release, "API2_EVENT_KEY_PATH", event_key_path
            ),
        ):
            legacy = release.security_configuration_evidence(
                legacy_process, expected
            )
            partial = release.security_configuration_evidence(
                partial_process, expected
            )
            exact = release.security_configuration_evidence(
                dict(expected), expected
            )
            engine_pending = release.security_configuration_evidence(
                engine_pending_process, expected
            )
        self.assertEqual(
            legacy["processConfiguredEnvironmentLoadState"],
            "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART",
        )
        self.assertEqual(
            legacy["processPendingRestartEnvironmentNames"],
            sorted(release.MANAGED_RESTART_ENVIRONMENT_NAMES),
        )
        self.assertTrue(
            legacy["processConfiguredEnvironmentPreStageCompatible"]
        )
        self.assertEqual(
            partial["processConfiguredEnvironmentLoadState"],
            "INVALID_PARTIAL_OR_DRIFTED",
        )
        self.assertFalse(
            partial["processConfiguredEnvironmentPreStageCompatible"]
        )
        self.assertEqual(
            exact["processConfiguredEnvironmentLoadState"],
            "EXACT_CONFIGURED",
        )
        self.assertEqual(
            exact["processPendingRestartEnvironmentNames"], []
        )
        self.assertEqual(
            engine_pending["processConfiguredEnvironmentLoadState"],
            "ENGINE_CREDENTIAL_PENDING_RESTART",
        )
        self.assertEqual(
            engine_pending["processPendingRestartEnvironmentNames"],
            [release.ADMIN_ENGINE_TOKEN_NAME],
        )
        self.assertTrue(
            engine_pending[
                "processConfiguredEnvironmentPreStageCompatible"
            ]
        )

    def test_candidate_runtime_rejects_base_unit_content_drift(self):
        args, _ = release_args("Apply")
        release_dir = pathlib.Path("/opt/fbsir/admin/releases/test")
        environment_manifest = [{
            "path": str(release.ENV_PATH),
            "ignoreErrors": False,
            "sha256": "f" * 64,
            "mode": 0o600,
            "uid": 0,
            "gid": 0,
            "nlink": 1,
        }]
        previous = {
            "environmentFiles": str(release.ENV_PATH),
            "environmentFilePaths": [str(release.ENV_PATH)],
            "environmentFileManifest": environment_manifest,
            "fragmentFileManifest": {
                "path": "/etc/systemd/system/fbsir-admin.service",
                "sha256": "a" * 64,
            },
            "processEnvironmentNamesSha256": "b" * 64,
            "processSecurityConfigurationNames":
                list(release.PROCESS_SECURITY_ENVIRONMENT_NAMES),
            "processSecurityConfigurationHmacSha256": "c" * 64,
            "expectedSecurityConfigurationNames":
                list(release.PROCESS_SECURITY_ENVIRONMENT_NAMES),
            "expectedSecurityConfigurationHmacSha256": "c" * 64,
            "configuredEnvironmentSha256": "f" * 64,
            "configuredEnvironmentNames":
                list(release.PROCESS_SECURITY_ENVIRONMENT_NAMES),
            "configuredEnvironmentHmacSha256": "1" * 64,
            "processConfiguredEnvironmentHmacSha256": "1" * 64,
            "processConfiguredEnvironmentMatched": True,
            "externalConfigManifest": [{"sha256": "d" * 64}],
            "additionalConfigSha256": "e" * 64,
        }
        expected_argv = release.candidate_process_arguments()
        current = dict(previous)
        current.update({
            "processArgvSha256": release.sha256_bytes(
                ("\0".join(expected_argv) + "\0").encode("utf-8")
            ),
            "processJarPath": str(
                release.CURRENT_LINK / "backend/fbsir-admin.jar"
            ),
            "processDatabaseBindingMatched": True,
            "dropInManifest": [],
            "processFlagValues": {
                name: "false" for name in release.FALSE_FLAGS
            },
            "processForbiddenOverrideNames": [],
            "fragmentFileManifest": {
                "path": "/etc/systemd/system/fbsir-admin.service",
                "sha256": "f" * 64,
            },
        })
        with (
            mock.patch.object(
                release,
                "predecessor_facts",
                return_value={"serviceSnapshotBeforeStage": previous},
            ),
            mock.patch.object(
                release, "release_dropin_manifest", return_value=[]
            ),
        ):
            with self.assertRaisesRegex(
                RuntimeError, "effective default-off contract drifted"
            ):
                release.assert_candidate_runtime_contract(
                    args, release_dir, current
                )

    def test_environment_files_accepts_one_fixed_file_in_systemd_forms(self):
        expected_required = [{
            "path": release.ENV_PATH_TEXT,
            "ignoreErrors": False,
        }]
        self.assertEqual(
            release.normalized_environment_files(
                "  /etc/u3w/fbsir-admin.env   (ignore_errors=no)  "
            ),
            expected_required,
        )
        with self.assertRaisesRegex(RuntimeError, "mandatory fixed"):
            release.normalized_environment_files(
                '  -"/etc/u3w/fbsir-admin.env" '
                "  (ignore_errors=yes)   "
            )

    def test_environment_files_rejects_a_second_file(self):
        with self.assertRaisesRegex(
            RuntimeError, "mandatory fixed EnvironmentFile"
        ):
            release.normalized_environment_files(
                "/etc/u3w/fbsir-admin.env (ignore_errors=no) "
                "/etc/u3w/hidden-override.env (ignore_errors=no)"
            )

    def test_finalize_stage_binds_the_complete_plan_target(self):
        args, _ = release_args("FinalizeStage")
        planned = plan_target_fixture()
        live = json.loads(json.dumps(planned))
        live["releaseRootExists"] = True
        with mock.patch.object(
            release,
            "stage_owned_release_root_delta_verified",
            return_value=True,
        ):
            binding = release.assert_stage_plan_target_matches(
                args, planned, live
            )
        self.assertEqual(
            binding["releasePlanTargetComparableSha256"],
            binding["finalizeStageLiveTargetComparableSha256"],
        )
        self.assertTrue(binding["stageOwnedReleaseRootDeltaVerified"])
        self.assertRegex(
            binding["releasePlanTargetSha256"], r"^[0-9a-f]{64}$"
        )

        for field, drift in (
            ("nginxDumpSha256", "b" * 64),
            ("environmentSha256", "c" * 64),
            ("processArgvSha256", "d" * 64),
        ):
            drifted = json.loads(json.dumps(live))
            drifted[field] = drift
            with (
                mock.patch.object(
                    release,
                    "stage_owned_release_root_delta_verified",
                    return_value=True,
                ),
                self.assertRaisesRegex(RuntimeError, "drifted"),
            ):
                release.assert_stage_plan_target_matches(
                    args, planned, drifted
                )

        unexpected = dict(planned)
        unexpected["collectorSha256"] = "e" * 64
        with self.assertRaisesRegex(RuntimeError, "contract"):
            release.assert_stage_plan_target_matches(
                args, unexpected, live
            )

    def test_staged_receipt_declares_both_database_change_dimensions(self):
        source = inspect.getsource(release.finalize_stage)
        self.assertIn(
            '"productionDatabaseChangedThisRun": False',
            source,
        )
        self.assertIn(
            '"productionDatabaseChangedSinceStage": False',
            source,
        )

    def test_apply_rejects_missing_or_true_staged_change_invariants(self):
        args, _ = release_args("Apply")
        args.stage_receipt_sha = "9" * 64
        receipt = {
            "schema": "fbsir.u3wW1aDeploymentReadinessReceipt.v3",
            "state": "STAGED_FOR_SWITCH",
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "backendBuildSha256": args.backend_sha,
            "frontendBuildSha256": args.frontend_tree_sha,
            "runnerSha256": args.runner_sha,
            "workerSha256": args.worker_sha,
            "adminRootDependencyAdoptionReceiptSha256":
                args.admin_root_dependency_adoption_receipt_sha,
            "stageApprovalReceiptSha256": "8" * 64,
            "databaseDownClaimed": False,
            "databaseRollbackSafetyProven": True,
            "productionDatabaseChanged": False,
            "productionDatabaseChangedThisRun": False,
            "productionDatabaseChangedSinceStage": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
            "actualActiveArtifactsMatched": False,
        }
        invariant_fields = (
            "productionDatabaseChanged",
            "productionDatabaseChangedThisRun",
            "productionDatabaseChangedSinceStage",
            "productionServiceChanged",
            "officialExpertsPackageChanged",
            "actualActiveArtifactsMatched",
        )
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            stage_path = (
                release_dir / "deployment-readiness-receipt.json"
            )
            stage_path.write_text("{}\n", encoding="utf-8")
            with (
                mock.patch.object(release, "validate_regular_file"),
                mock.patch.object(
                    release,
                    "sha256_file",
                    return_value=args.stage_receipt_sha,
                ),
                mock.patch.object(release, "read_json") as read_json,
                mock.patch.object(
                    release, "validate_staged_plan_target_binding"
                ),
                mock.patch.object(
                    release, "validate_staged_release_artifacts"
                ),
                mock.patch.object(release, "validate_release_evidence"),
            ):
                read_json.return_value = receipt
                _, accepted = release.validate_stage_receipt(
                    args, release_dir
                )
                self.assertIs(accepted, receipt)
                for field in invariant_fields:
                    for invalid_value in ("missing", True):
                        with self.subTest(
                            field=field,
                            invalid_value=invalid_value,
                        ):
                            invalid = dict(receipt)
                            if invalid_value == "missing":
                                invalid.pop(field)
                            else:
                                invalid[field] = invalid_value
                            read_json.return_value = invalid
                            with self.assertRaisesRegex(
                                RuntimeError,
                                "staged receipt identity is invalid",
                            ):
                                release.validate_stage_receipt(
                                    args, release_dir
                                )

    def test_stage_artifact_failure_precedes_every_apply_mutation(self):
        args, _ = release_args("Apply")
        with (
            mock.patch.object(
                release,
                "release_directory",
                return_value=pathlib.Path("/opt/fbsir/admin/releases/test"),
            ),
            mock.patch.object(
                release,
                "validate_stage_receipt",
                side_effect=RuntimeError(
                    "staged release artifact manifest drifted"
                ),
            ),
            mock.patch.object(release, "apply_migration") as migration,
            mock.patch.object(
                release, "install_candidate_application"
            ) as install,
        ):
            with self.assertRaisesRegex(RuntimeError, "artifact"):
                release.apply_release(args)
        migration.assert_not_called()
        install.assert_not_called()

    def test_latest_receipt_cannot_regress_across_release_lifecycles(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            target_release = root / (
                "w1a-release-0123456789ab-20260723T180000Z"
            )
            newer_release = root / (
                "w1a-release-fedcba987654-20260723T190000Z"
            )
            target_release.mkdir()
            newer_release.mkdir()
            target = target_release / "deployment-readiness-receipt.json"
            newer = newer_release / "deployment-receipt.json"
            target.write_text("{}\n", encoding="utf-8")
            newer.write_text("{}\n", encoding="utf-8")
            with (
                mock.patch.object(
                    release, "validate_regular_file"
                ),
                mock.patch.object(
                    release,
                    "validated_latest_receipt_target",
                    return_value=newer.resolve(),
                ),
                mock.patch.object(
                    release, "atomic_symlink"
                ) as replace_latest,
            ):
                with self.assertRaisesRegex(
                    RuntimeError, "another lifecycle"
                ):
                    release.advance_latest_receipt(target)
                replace_latest.assert_not_called()
                changed = release.advance_latest_receipt(
                    target, [newer.resolve()]
                )
            self.assertTrue(changed)
            replace_latest.assert_called_once_with(
                target.resolve(), release.LATEST_RECEIPT
            )

    def test_finalize_stage_recollects_all_active_nginx_files(self):
        nginx_source = inspect.getsource(release.active_nginx_manifest)
        dump_source = inspect.getsource(release.nginx_dump_bytes)
        manifest_source = inspect.getsource(
            release.stable_root_regular_manifest
        )
        target_source = inspect.getsource(release.stage_live_plan_target)
        finalize_source = inspect.getsource(release.finalize_stage)
        self.assertIn('["nginx", "-T"]', dump_source)
        self.assertEqual(nginx_source.count("nginx_dump_bytes()"), 2)
        self.assertIn("stable_root_regular_manifest(path)", nginx_source)
        self.assertIn("os.fstat(descriptor)", manifest_source)
        self.assertIn('"sizeBytes"', manifest_source)
        self.assertNotIn('"fbsir"', nginx_source)
        self.assertNotIn('"u3w.com"', nginx_source)
        self.assertIn('"activeNginxManifest"', target_source)
        self.assertIn('"nginxDumpSha256"', target_source)
        self.assertIn("assert_stage_plan_target_matches", finalize_source)
        self.assertIn("**plan_target_binding", finalize_source)

    def test_apply_holds_migration_lease_through_receipt_commit(self):
        source = inspect.getsource(release.apply_release)
        first_commit = source.index(
            "atomic_json(deployment_path, receipt)"
        )
        final_read = source.index(
            "043 final current-read drifted before receipt commit"
        )
        lease_close = source.index(
            "migration_lease.close()", first_commit
        )
        committed = source.index(
            "deployment_committed = True", first_commit
        )
        self.assertLess(final_read, first_commit)
        self.assertLess(first_commit, committed)
        self.assertLess(committed, lease_close)
        self.assertEqual(
            source[first_commit:committed].count("\n"),
            1,
        )
        self.assertIn(
            "exact_migration_facts(migration_lease.mysql)",
            source,
        )

    def test_nested_rollback_evidence_is_not_self_reported(self):
        source = inspect.getsource(release.validate_release_evidence)
        for name in (
            "application-rollback-assembly.json",
            "database-rollback-safety.json",
            "application-rollback-execution.json",
        ):
            self.assertIn(name, source)
        self.assertIn("anchored_evidence_json", source)
        self.assertIn("assert_candidate_snapshot_evidence", source)
        self.assertIn(
            "assert_restored_predecessor_runtime_contract", source
        )
        self.assertIn("len(invocation_ids) != 3", source)

    def test_recovery_topology_is_enumerated_and_runtime_bound(self):
        source = inspect.getsource(
            release.assert_release_owned_recovery_topology
        )
        self.assertIn("allowed_topologies", source)
        self.assertIn("allowed_loaded_dropins", source)
        self.assertIn("allowed_argv_sha256", source)
        self.assertIn("allowed_jar_paths", source)
        self.assertIn("exact_loaded_environment_matches", source)
        exact_source = inspect.getsource(
            release.exact_loaded_environment_matches
        )
        self.assertIn(
            "processConfiguredEnvironmentLoadState", exact_source
        )
        self.assertIn("EXACT_CONFIGURED", exact_source)
        self.assertNotIn(
            "in {args.backend_sha, previous[", source
        )

    def test_all_relaxed_binding_default_off_aliases_are_managed(self):
        self.assertEqual(len(release.FALSE_FLAGS), 12)
        self.assertIn(
            "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
            release.FALSE_FLAGS,
        )
        self.assertIn(
            "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_"
            "AUTHORITATIVE_CREDIT_ENABLED",
            release.FALSE_FLAGS,
        )

    def test_approval_is_exact_and_binds_every_receipt(self):
        args, _ = release_args()
        parsed = release.validate_approval(args)
        self.assertEqual(parsed["expectedBackupReceiptSha256"], "f" * 64)
        self.assertEqual(parsed["workerSha256"], "c" * 64)

        args, payload = release_args()
        payload["unknown"] = True
        raw = json.dumps(payload, separators=(",", ":")).encode()
        args.approval_sha = release.sha256_bytes(raw)
        args.approval_json_base64 = base64.b64encode(raw).decode()
        with self.assertRaisesRegex(RuntimeError, "unknown fields"):
            release.validate_approval(args)

    def test_portal_config_is_bound_to_candidate_and_u3w_backend(self):
        candidate = pathlib.Path(
            "/opt/fbsir/admin/releases/w1a-release-test"
        )
        rendered = release.portal_nginx_config(candidate)
        self.assertEqual(rendered.count("server_name admin.u3w.com"), 2)
        self.assertEqual(rendered.count("server_name me.u3w.com"), 2)
        self.assertEqual(
            rendered.count("proxy_pass http://127.0.0.1:8080/"), 2
        )
        self.assertIn(str(candidate / "frontend"), rendered)
        self.assertNotIn("fbss/health", rendered)

    def test_dropin_points_only_at_the_current_candidate(self):
        rendered = release.systemd_dropin()
        self.assertIn(
            "/opt/fbsir/admin/current/backend/fbsir-admin.jar",
            rendered,
        )
        self.assertNotIn("/opt/fbss/", rendered)
        self.assertLess(rendered.index("-Dspring."), rendered.index("-jar"))
        rollback = release.rollback_systemd_dropin(
            pathlib.Path("/opt/fbsir/admin/releases/test")
        )
        self.assertIn(
            "/opt/fbsir/admin/releases/test/rollback/previous-admin.jar",
            rollback,
        )
        self.assertLess(rollback.index("-Dspring."), rollback.index("-jar"))

    def test_release_dropin_manifest_replaces_prior_release_dropin(self):
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            evidence = release_dir / "evidence"
            evidence.mkdir()
            candidate = evidence / "systemd-dropin.conf"
            candidate.write_text("[Service]\n", encoding="utf-8")
            other = {
                "path": "/etc/systemd/system/fbsir-admin.service.d/10-base.conf",
                "sha256": "1" * 64,
                "mode": 0o644,
            }
            prior = {
                "path": str(release.DROPIN_PATH),
                "sha256": "2" * 64,
                "mode": 0o644,
            }
            predecessor = {
                "serviceSnapshotBeforeStage": {
                    "dropInManifest": [other, prior]
                }
            }
            with (
                mock.patch.object(
                    release,
                    "predecessor_facts",
                    return_value=predecessor,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=candidate,
                ),
            ):
                manifest = release.release_dropin_manifest(
                    release_dir, "systemd-dropin.conf"
                )
            self.assertEqual(
                sum(
                    item["path"] == str(release.DROPIN_PATH)
                    for item in manifest
                ),
                1,
            )
            self.assertIn(other, manifest)
            active = next(
                item
                for item in manifest
                if item["path"] == str(release.DROPIN_PATH)
            )
            self.assertEqual(
                active["sha256"], release.sha256_file(candidate)
            )
            self.assertNotEqual(active["sha256"], prior["sha256"])

    def test_unavailable_rollback_can_only_gain_immutable_db_attestation(self):
        args, _ = release_args("Rollback")
        facts = exact_migration_facts(8, 4)
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            rollback_path = release_dir / "rollback-receipt.json"
            rollback_path.write_text("{}\n", encoding="utf-8")
            rollback = {
                "deploymentReceiptPath":
                    str(release_dir / "deployment-receipt.json"),
                "deploymentReceiptSha256": "9" * 64,
            }
            captured = {}

            def write_receipt(path, value):
                captured["path"] = pathlib.Path(path)
                captured["value"] = value

            with mock.patch.object(
                release, "atomic_json", side_effect=write_receipt
            ):
                path, receipt, changed = (
                    release.ensure_rollback_verification_receipt(
                        args,
                        release_dir,
                        rollback_path,
                        rollback,
                        facts,
                    )
                )
            self.assertTrue(changed)
            self.assertEqual(
                path.name, "rollback-verification-receipt.json"
            )
            self.assertEqual(captured["path"], path)
            self.assertEqual(
                receipt["rollbackReceiptSha256"],
                release.sha256_file(rollback_path),
            )
            self.assertEqual(receipt["retainedMigrationFacts"], facts)
            self.assertEqual(
                receipt["state"],
                "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT",
            )
            self.assertFalse(receipt["databaseDownClaimed"])
            self.assertFalse(receipt["productionDatabaseChanged"])

    def test_candidate_health_uses_environment_values_not_connection(self):
        args = types.SimpleNamespace(
            release_id="w1a-release-test",
            source_commit="a" * 40,
        )
        candidate = pathlib.Path("/opt/fbsir/admin/releases/test")
        environment = {name: "false" for name in release.FALSE_FLAGS}

        def http_result(url, timeout=10):
            if url.endswith("/w1a-release.json"):
                return (
                    200,
                    {
                        "schema": "fbsir.u3wReleaseMarker.v1",
                        "releaseId": args.release_id,
                        "sourceCommit": args.source_commit,
                        "officialExpertsPackageChanged": False,
                    },
                )
            return 200, {"code": 200}

        with (
            mock.patch.object(
                release,
                "assert_candidate_owned_topology",
                return_value={"activeState": "active"},
            ),
            mock.patch.object(release, "assert_candidate_runtime_contract"),
            mock.patch.object(release, "wait_for_u3w_health"),
            mock.patch.object(
                release,
                "http_json",
                side_effect=http_result,
            ),
            mock.patch.object(
                release,
                "parse_environment",
                return_value=(environment, {"host": "127.0.0.1"}),
            ),
        ):
            self.assertEqual(
                release.assert_candidate_active(args, candidate)[
                    "activeState"
                ],
                "active",
            )
            environment[release.FALSE_FLAGS[0]] = "true"
            with self.assertRaisesRegex(RuntimeError, "flags"):
                release.assert_candidate_active(args, candidate)

    def test_atomic_write_preserves_existing_system_parent_mode(self):
        source = inspect.getsource(release.atomic_bytes)
        self.assertIn("ensure_parent_directory(path.parent)", source)
        self.assertNotIn("safe_directory(path.parent", source)
        parent_source = inspect.getsource(release.ensure_parent_directory)
        self.assertIn("if not path.exists()", parent_source)
        self.assertNotIn("os.chmod", parent_source)
        safe_source = inspect.getsource(release.safe_directory)
        self.assertIn("if created:", safe_source)
        self.assertIn("not created", safe_source)

    def test_service_snapshot_binds_loaded_config_and_actual_process(self):
        source = inspect.getsource(release.service_snapshot)
        self.assertIn('pathlib.Path("/proc")', source)
        self.assertIn('"configuredJarSha256"', source)
        self.assertIn('"processJarSha256"', source)
        self.assertIn('"processArgvSha256"', source)
        self.assertIn('"processFlagValues"', source)
        self.assertIn('"dropInManifest"', source)
        self.assertIn('"externalConfigManifest"', source)
        self.assertIn(
            "process_jar.resolve() != configured_jar.resolve()",
            source,
        )

    def test_frontend_archive_rejects_parent_escape(self):
        with tempfile.TemporaryDirectory() as temporary:
            incoming = pathlib.Path(temporary)
            (incoming / "evidence").mkdir()
            (incoming / "frontend").mkdir()
            archive_path = incoming / "evidence/frontend.tar"
            with tarfile.open(archive_path, "w") as archive:
                info = tarfile.TarInfo("../escape.txt")
                payload = b"escape"
                info.size = len(payload)
                archive.addfile(info, io.BytesIO(payload))

            def safe_dir(path, mode=0o700):
                pathlib.Path(path).mkdir(parents=True, exist_ok=True)
                return pathlib.Path(path)

            with (
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=archive_path,
                ),
                mock.patch.object(release, "safe_directory", safe_dir),
            ):
                with self.assertRaisesRegex(RuntimeError, "unsafe"):
                    release.extract_frontend_archive(incoming)

    def test_database_rollback_language_is_forward_only(self):
        worker = (ROOT / "u3w-default-off-release-remote.py").read_text(
            encoding="utf-8"
        )
        self.assertIn("databaseRollbackSafetyProven", worker)
        self.assertIn("RETAIN_ADDITIVE_043_DORMANT_NO_DOWN", worker)
        self.assertNotIn('"databaseRollbackProven": True', worker)

    def test_apply_runtime_identity_fails_closed_on_environment_drift(self):
        args = types.SimpleNamespace(baseline_receipt_sha="a" * 64)
        identity = runtime_identity()
        stage = {"preStageRuntimeIdentity": dict(identity)}
        with mock.patch.object(
            release,
            "database_pre_stage_facts",
            return_value=dict(identity),
        ):
            self.assertEqual(
                release.validate_stage_runtime_identity(args, stage),
                identity,
            )
        drifted = dict(identity)
        drifted["environmentSha256"] = "d" * 64
        with mock.patch.object(
            release,
            "database_pre_stage_facts",
            return_value=drifted,
        ):
            with self.assertRaisesRegex(RuntimeError, "identity drifted"):
                release.validate_stage_runtime_identity(args, stage)

    def test_stage_accepts_exact_absent_or_retained_prestate_only(self):
        args = types.SimpleNamespace(baseline_receipt_sha="a" * 64)
        absent = runtime_identity()
        with mock.patch.object(
            release, "database_pre_stage_facts", return_value=dict(absent)
        ):
            self.assertEqual(
                release.validate_stage_runtime_identity(
                    args, {"preStageRuntimeIdentity": absent}
                ),
                absent,
            )
        completed_retry = runtime_identity(
            "EXACT_043_RETAINED_DORMANT", 0, 0
        )
        with mock.patch.object(
            release,
            "database_pre_stage_facts",
            return_value=completed_retry,
        ):
            self.assertEqual(
                release.validate_stage_runtime_identity(
                    args, {"preStageRuntimeIdentity": absent}
                ),
                completed_retry,
            )
        retained = runtime_identity(
            "EXACT_043_RETAINED_DORMANT", 5, 3
        )
        with mock.patch.object(
            release, "database_pre_stage_facts", return_value=dict(retained)
        ):
            self.assertEqual(
                release.validate_stage_runtime_identity(
                    args, {"preStageRuntimeIdentity": retained}
                ),
                retained,
            )
        grown = runtime_identity(
            "EXACT_043_RETAINED_DORMANT", 6, 3
        )
        with mock.patch.object(
            release, "database_pre_stage_facts", return_value=grown
        ):
            with self.assertRaisesRegex(RuntimeError, "counts drifted"):
                release.validate_stage_runtime_identity(
                    args, {"preStageRuntimeIdentity": retained}
                )
        natural = dict(retained)
        natural["attributionProbeEventCount"] = 4
        natural["attributionNaturalEventCount"] = 1
        natural["attributionNonProbeEventCount"] = 1
        with mock.patch.object(
            release, "database_pre_stage_facts", return_value=natural
        ):
            with self.assertRaisesRegex(
                RuntimeError,
                "partial or drifted|structure is not exact",
            ):
                release.validate_stage_runtime_identity(
                    args, {"preStageRuntimeIdentity": retained}
                )

    def test_emergency_rollback_does_not_require_stage_runtime_identity(self):
        source = inspect.getsource(release._rollback_release)
        self.assertNotIn("validate_stage_runtime_identity", source)
        self.assertIn("databaseSafetyCurrentRead", source)
        self.assertIn("rollback_transition", source)
        self.assertIn(
            "assert_candidate_owned_topology",
            inspect.getsource(release.rollback_transition),
        )

    def test_apply_reentry_classifies_inactive_and_proves_live_rollback(self):
        source = inspect.getsource(release.apply_release)
        self.assertIn(
            "service_snapshot(require_active=False)",
            source,
        )
        self.assertIn("install_candidate_application", source)
        self.assertIn("restore_application", source)
        self.assertIn(
            "application_rollback_execution_receipt",
            source,
        )
        self.assertNotIn(
            'if before["jarSha256"] == args.backend_sha',
            source,
        )
        self.assertLess(
            source.index("if deployment_path.exists():"),
            source.index("validate_stage_runtime_identity(args, stage)"),
        )

    def test_deployment_receipt_is_the_apply_commit_point(self):
        source = inspect.getsource(release.apply_release)
        self.assertNotIn(
            "migration_lease.database_changed,", source
        )
        self.assertIn(
            "migration_lease.database_changed_this_run", source
        )
        self.assertIn(
            "migration_lease.database_changed_since_stage", source
        )
        self.assertIn("deployment_committed = False", source)
        first_time_apply = source.index("deployment_committed = False")
        self.assertLess(
            source.index(
                "atomic_json(deployment_path, receipt)", first_time_apply
            ),
            source.index("deployment_committed = True", first_time_apply),
        )
        self.assertLess(
            source.index("deployment_committed = True", first_time_apply),
            source.index(
                "advance_latest_receipt(\n"
                "            deployment_path, [stage_path]",
                first_time_apply,
            ),
        )
        self.assertIn(
            "remove_exact_uncommitted_deployment_receipt",
            source,
        )

    def test_signed_probe_matches_java_verifier_contract_without_receipt_secrets(
            self):
        values, event_material = configured_environment_values()
        now = dt.datetime(
            2026, 7, 24, 18, 0, 30, tzinfo=dt.timezone.utc
        )

        event, identity = release.build_signed_attribution_probe_event(
            values,
            now=now,
            entropy=b"u3w-default-off-probe-test-seed!",
        )

        string_fields = (
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
        signed_fields = {
            name: str(event[name]).strip() for name in string_fields
        }
        signed_fields["rawContentStored"] = "false"
        signed_fields["sequenceNo"] = "1"
        expected_signature = hmac.new(
            event_material,
            json.dumps(
                signed_fields,
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
                allow_nan=False,
            ).encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()

        self.assertEqual(event["trafficClass"], "PROBE")
        self.assertEqual(event["eventType"], "ENTRY_OBSERVED")
        self.assertEqual(event["sequenceNo"], 1)
        self.assertEqual(
            event["signature"], "v1=" + expected_signature
        )
        self.assertEqual(
            identity,
            {
                "eventId": event["eventId"],
                "receiptId": event["receiptId"],
                "nonceHash": hashlib.sha256(
                    event["nonce"].encode("utf-8")
                ).hexdigest(),
                "journeyId": event["journeyId"],
            },
        )
        serialized_identity = release.canonical_json(identity)
        self.assertNotIn(event["nonce"], serialized_identity)
        self.assertNotIn(event["signature"], serialized_identity)
        self.assertNotIn(event_material.decode("ascii"), serialized_identity)

    def test_signed_disabled_probe_accepts_only_404_and_zero_rows(self):
        values, _ = configured_environment_values()
        facts = exact_migration_facts(event_count=7, journey_count=3)
        zero_counts = {
            "eventId": 0,
            "receiptId": 0,
            "nonceHash": 0,
            "journeyId": 0,
        }
        captured = {}

        def post_probe(event, timeout=10):
            captured["event"] = event
            return 404

        with (
            mock.patch.object(
                release,
                "parse_environment",
                return_value=(values, {}),
            ),
            mock.patch.object(
                release,
                "attribution_probe_identity_counts",
                side_effect=[zero_counts, dict(zero_counts)],
            ),
            mock.patch.object(
                release,
                "post_signed_attribution_probe",
                side_effect=post_probe,
            ),
            mock.patch.object(
                release,
                "exact_migration_facts",
                return_value=dict(facts),
            ),
        ):
            evidence, after = release.disabled_attribution_ingress_probe(
                mock.Mock(),
                facts,
                now=dt.datetime(
                    2026, 7, 24, 18, 0, tzinfo=dt.timezone.utc
                ),
                entropy=b"u3w-default-off-probe-test-seed!",
            )

        self.assertEqual(after, facts)
        self.assertEqual(evidence["method"], "POST")
        self.assertEqual(evidence["httpStatus"], 404)
        self.assertEqual(
            evidence["acceptedDisabledHttpStatuses"], [404]
        )
        self.assertEqual(evidence["trafficClass"], "PROBE")
        self.assertEqual(evidence["identityCountsBefore"], zero_counts)
        self.assertEqual(evidence["identityCountsAfter"], zero_counts)
        self.assertEqual(
            evidence["globalCountsBefore"],
            {"eventCount": 7, "journeyCount": 3},
        )
        self.assertEqual(
            evidence["globalCountsAfter"],
            {"eventCount": 7, "journeyCount": 3},
        )
        self.assertEqual(captured["event"]["trafficClass"], "PROBE")
        serialized = release.canonical_json(evidence)
        self.assertNotIn(captured["event"]["nonce"], serialized)
        self.assertNotIn(captured["event"]["signature"], serialized)
        self.assertFalse(evidence["rawNonceDisclosed"])
        self.assertFalse(evidence["rawSignatureDisclosed"])
        self.assertFalse(evidence["signingKeyMaterialDisclosed"])

    def test_signed_disabled_probe_rejects_auth_and_enabled_responses(self):
        values, _ = configured_environment_values()
        facts = exact_migration_facts()
        zero_counts = {
            "eventId": 0,
            "receiptId": 0,
            "nonceHash": 0,
            "journeyId": 0,
        }
        for status in (200, 202, 400, 401, 403, 405):
            with (
                self.subTest(status=status),
                mock.patch.object(
                    release,
                    "parse_environment",
                    return_value=(values, {}),
                ),
                mock.patch.object(
                    release,
                    "attribution_probe_identity_counts",
                    side_effect=[zero_counts, dict(zero_counts)],
                ),
                mock.patch.object(
                    release,
                    "post_signed_attribution_probe",
                    return_value=status,
                ),
                mock.patch.object(
                    release,
                    "exact_migration_facts",
                    return_value=dict(facts),
                ),
                self.assertRaisesRegex(
                    RuntimeError, "attribution ingress is not disabled"
                ),
            ):
                release.disabled_attribution_ingress_probe(
                    mock.Mock(), facts
                )

    def test_signed_disabled_probe_rejects_identity_or_global_write(self):
        values, _ = configured_environment_values()
        facts = exact_migration_facts(event_count=7, journey_count=3)
        zero_counts = {
            "eventId": 0,
            "receiptId": 0,
            "nonceHash": 0,
            "journeyId": 0,
        }
        identity_write = dict(zero_counts)
        identity_write["nonceHash"] = 1
        for before, after, global_after, message in (
            (
                identity_write,
                zero_counts,
                facts,
                "identity is not unique",
            ),
            (
                zero_counts,
                identity_write,
                facts,
                "probe identity was persisted",
            ),
            (
                zero_counts,
                zero_counts,
                exact_migration_facts(event_count=8, journey_count=3),
                "attribution counts changed",
            ),
        ):
            with (
                self.subTest(message=message),
                mock.patch.object(
                    release,
                    "parse_environment",
                    return_value=(values, {}),
                ),
                mock.patch.object(
                    release,
                    "attribution_probe_identity_counts",
                    side_effect=[before, after],
                ),
                mock.patch.object(
                    release,
                    "post_signed_attribution_probe",
                    return_value=404,
                ) as post,
                mock.patch.object(
                    release,
                    "exact_migration_facts",
                    return_value=global_after,
                ),
                self.assertRaisesRegex(RuntimeError, message),
            ):
                release.disabled_attribution_ingress_probe(
                    mock.Mock(), facts
                )
            if before != zero_counts:
                post.assert_not_called()

    def test_probe_identity_queries_are_parameterized_and_complete(self):
        mysql = mock.Mock()
        mysql.scalar.side_effect = [0, 0, 0, 0]
        identity = {
            "eventId": "1" * 64,
            "receiptId": "2" * 64,
            "nonceHash": "3" * 64,
            "journeyId": "4" * 64,
        }

        counts = release.attribution_probe_identity_counts(
            mysql, identity
        )

        self.assertEqual(
            counts,
            {
                "eventId": 0,
                "receiptId": 0,
                "nonceHash": 0,
                "journeyId": 0,
            },
        )
        self.assertEqual(mysql.scalar.call_count, 4)
        for call, expected in zip(
                mysql.scalar.call_args_list, identity.values()):
            sql, parameters = call.args
            self.assertIn("%s", sql)
            self.assertNotIn(expected, sql)
            self.assertEqual(parameters, (expected,))

    def test_final_current_read_proves_probe_did_not_write(self):
        args, _ = release_args("Apply")
        facts = exact_migration_facts(event_count=7, journey_count=3)
        final_service = {
            "activeState": "active",
            "invocationId": "a" * 32,
            "jarSha256": args.backend_sha,
        }
        with (
            mock.patch.object(
                release,
                "assert_candidate_active",
                side_effect=[dict(final_service), final_service],
            ) as active,
            mock.patch.object(
                release,
                "exact_migration_facts",
                return_value=facts,
            ),
            mock.patch.object(
                release,
                "disabled_attribution_ingress_probe",
                return_value=(
                    final_current_read_evidence(facts)[
                        "finalDefaultOffCurrentRead"
                    ]["disabledAttributionIngressProbe"],
                    dict(facts),
                ),
            ),
        ):
            service, evidence = release.final_default_off_current_read(
                args,
                pathlib.Path("/release"),
                mock.Mock(),
                facts,
            )
        self.assertEqual(service, final_service)
        self.assertTrue(evidence["verified"])
        self.assertTrue(evidence["eventAndJourneyCountsUnchanged"])
        self.assertEqual(evidence["migrationFactsBeforeProbe"], facts)
        self.assertEqual(evidence["migrationFactsAfterProbe"], facts)
        self.assertEqual(active.call_count, 2)

        drifted = exact_migration_facts(event_count=8, journey_count=3)
        with (
            mock.patch.object(
                release,
                "assert_candidate_active",
                return_value=final_service,
            ),
            mock.patch.object(
                release,
                "exact_migration_facts",
                return_value=facts,
            ),
            mock.patch.object(
                release,
                "disabled_attribution_ingress_probe",
                return_value=(
                    final_current_read_evidence(drifted)[
                        "finalDefaultOffCurrentRead"
                    ]["disabledAttributionIngressProbe"],
                    drifted,
                ),
            ),
            self.assertRaisesRegex(
                RuntimeError, "changed during final default-off probe"
            ),
        ):
            release.final_default_off_current_read(
                args,
                pathlib.Path("/release"),
                mock.Mock(),
                facts,
            )

    def test_apply_repeats_final_current_read_immediately_before_commit(self):
        source = inspect.getsource(release.apply_release)
        first_time_apply = source.index("deployment_committed = False")
        commit = source.index(
            "atomic_json(deployment_path, receipt)", first_time_apply
        )
        final_read = source.rindex(
            "final_default_off_current_read(", first_time_apply, commit
        )
        receipt_rebuild = source.rindex(
            "receipt = deployment_receipt(", final_read, commit
        )
        self.assertLess(final_read, receipt_rebuild)
        self.assertLess(receipt_rebuild, commit)
        between = source[receipt_rebuild:commit]
        self.assertNotIn("validate_release_evidence", between)
        self.assertNotIn("exact_migration_facts", between)

    def test_database_change_receipt_distinguishes_this_run_from_stage(self):
        args, _ = release_args("Apply")
        stage = {"schema": "stage"}
        current_link = mock.Mock()
        current_link.resolve.return_value = pathlib.Path(
            "/opt/fbsir/admin/releases/test"
        )
        with (
            mock.patch.object(
                release, "sha256_file", return_value="a" * 64
            ),
            mock.patch.object(release, "CURRENT_LINK", current_link),
            mock.patch.object(
                release,
                "active_nginx_manifest",
                return_value=([], "b" * 64),
            ),
        ):
            receipt = release.deployment_receipt(
                args,
                pathlib.Path("/stage.json"),
                stage,
                exact_migration_facts(),
                {},
                {},
                pathlib.Path("/rollback.json"),
                False,
                True,
                final_current_read_evidence(
                    exact_migration_facts(),
                    service={"invocationId": None, "jarSha256": None},
                )["finalDefaultOffCurrentRead"],
            )
        self.assertFalse(
            receipt["productionDatabaseChangedThisRun"]
        )
        self.assertTrue(
            receipt["productionDatabaseChangedSinceStage"]
        )
        self.assertTrue(receipt["productionDatabaseChanged"])
        lease = release.MigrationLease(
            mock.Mock(),
            {},
            exact_migration_facts(),
            database_changed_this_run=False,
            database_changed_since_stage=True,
        )
        self.assertFalse(lease.database_changed_this_run)
        self.assertTrue(lease.database_changed_since_stage)

    def test_post_commit_failure_receipt_never_claims_pre_switch(self):
        args, _ = release_args("Apply")
        captured = {}
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            deployment = release_dir / "deployment-receipt.json"
            deployment.write_text("{}\n", encoding="utf-8")
            with (
                mock.patch.object(
                    release, "release_directory", return_value=release_dir
                ),
                mock.patch.object(release, "validate_regular_file"),
                mock.patch.object(
                    release, "sha256_file", return_value="a" * 64
                ),
                mock.patch.object(
                    release,
                    "atomic_json",
                    side_effect=lambda path, value: captured.update(
                        {"path": path, "value": value}
                    ),
                ),
            ):
                release.write_apply_failure_receipt(
                    args,
                    RuntimeError("read failed"),
                    None,
                    "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
                    application_started=False,
                    topology_restored=False,
                    deployment_commit_outcome="COMMITTED",
                    service_changed_this_run=False,
                    database_changed_this_run=False,
                )
        receipt = captured["value"]
        self.assertEqual(
            receipt["state"],
            "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
        )
        self.assertTrue(receipt["applicationAlreadyCommitted"])
        self.assertEqual(
            receipt["deploymentCommitOutcome"], "COMMITTED"
        )
        self.assertEqual(
            receipt["deploymentReceiptSha256"], "a" * 64
        )
        self.assertNotEqual(
            receipt["state"], "FAIL_CLOSED_BEFORE_APPLICATION_SWITCH"
        )

    def test_failure_envelope_binds_exact_release_owned_receipt_without_message(self):
        args, _ = release_args("Apply")
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            path = release_dir / (
                "apply-failure-20260723T180000000000Z-"
                "0123456789ab.json"
            )
            receipt = {
                "schema": "fbsir.u3wDefaultOffReleaseFailureReceipt.v2",
                "state": "FAIL_CLOSED_BEFORE_APPLICATION_SWITCH",
                "releaseId": args.release_id,
                "sourceCommit": args.source_commit,
                "applyApprovalReceiptSha256": args.approval_sha,
                "officialExpertsPackageChanged": False,
            }
            path.write_text(
                release.canonical_json(receipt) + "\n",
                encoding="utf-8",
            )
            error = release.ReleaseMutationFailure(
                "sensitive failure detail", path
            )
            with (
                mock.patch.object(
                    release,
                    "release_directory",
                    return_value=release_dir,
                ),
                mock.patch.object(release, "validate_regular_file"),
            ):
                envelope = release.worker_error_envelope(args, error)
        self.assertEqual(
            envelope["schema"],
            "fbsir.u3wDefaultOffReleaseWorkerError.v2",
        )
        self.assertTrue(envelope["failureReceiptEvidenceValid"])
        self.assertEqual(envelope["failureReceiptPath"], str(path))
        self.assertRegex(
            envelope["failureReceiptSha256"], r"^[0-9a-f]{64}$"
        )
        self.assertNotIn("message", envelope)
        self.assertNotIn(
            "sensitive failure detail",
            json.dumps(envelope),
        )

    def test_rollback_reentry_does_not_rewrite_exact_latest_link(self):
        args, _ = release_args("Rollback")
        facts = exact_migration_facts()
        receipt = {
            "schema":
                "fbsir.u3wDefaultOffReleaseRollbackReceipt.v2",
            "state":
                "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT",
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "deploymentReceiptSha256": "a" * 64,
            "retainedMigrationFacts": facts,
        }
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            rollback_path = release_dir / "rollback-receipt.json"
            deployment_path = release_dir / "deployment-receipt.json"
            rollback_path.write_text("{}\n", encoding="utf-8")
            deployment_path.write_text("{}\n", encoding="utf-8")
            with (
                mock.patch.object(
                    release, "release_directory", return_value=release_dir
                ),
                mock.patch.object(
                    release,
                    "validate_deployment_receipt",
                    return_value=(deployment_path, {}),
                ),
                mock.patch.object(release, "validate_regular_file"),
                mock.patch.object(
                    release, "read_json", return_value=receipt
                ),
                mock.patch.object(
                    release, "sha256_file", return_value="a" * 64
                ),
                mock.patch.object(
                    release, "validate_rollback_receipt_base"
                ),
                mock.patch.object(
                    release, "assert_predecessor_active"
                ),
                mock.patch.object(
                    release, "read_exact_migration_facts",
                    return_value=facts,
                ),
                mock.patch.object(
                    release, "validated_rollback_receipt_chain"
                ),
                mock.patch.object(
                    release,
                    "advance_latest_receipt",
                    return_value=False,
                ) as rewrite,
                mock.patch.object(
                    release, "validate_release_evidence", return_value=[]
                ),
            ):
                result = release.rollback_release(args)
        rewrite.assert_called_once()
        self.assertFalse(result["productionFilesystemChanged"])

    def test_uncommitted_exact_receipt_cleanup_never_unlinks_drift(self):
        receipt = {"schema": "test", "state": "candidate"}
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "deployment-receipt.json"
            path.write_bytes(
                (release.canonical_json(receipt) + "\n").encode("utf-8")
            )
            with (
                mock.patch.object(
                    release, "validate_regular_file", return_value=path
                ),
                mock.patch.object(release, "fsync_directory"),
            ):
                self.assertTrue(
                    release.remove_exact_uncommitted_deployment_receipt(
                        path, receipt
                    )
                )
                self.assertFalse(path.exists())
                path.write_bytes(b'{"schema":"drift"}\n')
                with self.assertRaisesRegex(RuntimeError, "ambiguous"):
                    release.remove_exact_uncommitted_deployment_receipt(
                        path, receipt
                    )
                self.assertTrue(path.exists())

    def test_stage_only_claims_rollback_assembly(self):
        source = inspect.getsource(release.finalize_stage)
        self.assertIn(
            "fbsir.u3wApplicationRollbackAssemblyReceipt.v1",
            source,
        )
        self.assertIn('"applicationRollbackProven": False', source)
        self.assertNotIn(
            "fbsir.u3wApplicationRollbackRehearsalReceipt.v1",
            source,
        )
        self.assertIn("environment_flags_explicit_false()", source)
        self.assertIn("processFlagValues", source)

    def test_interrupted_apply_recovery_canonicalizes_stage_only_topology(self):
        args, approval = interrupted_apply_recovery_args()
        retained_facts = exact_migration_facts(event_count=7, journey_count=4)
        failure_facts = {
            key: value
            for key, value in exact_migration_facts().items()
            if key in release.LEGACY_MIGRATION_FACT_FIELDS
        }
        staged = {
            "schema": release.LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
            "state": "STAGED_FOR_SWITCH",
            "releaseId": args.target_release_id,
            "sourceCommit": args.target_source_commit,
            "stageApprovalReceiptSha256": "7" * 64,
            "migrationSha256": args.migration_sha,
            "preStageRuntimeIdentity": runtime_identity("ABSENT"),
            "databaseDownClaimed": False,
            "productionDatabaseChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
        }
        apply_failure = {
            "schema": "fbsir.u3wDefaultOffReleaseFailureReceipt.v1",
            "state": (
                "APPLICATION_RESTORED_DATABASE_043_"
                "RETAINED_OR_FAIL_CLOSED"
            ),
            "releaseId": args.target_release_id,
            "sourceCommit": args.target_source_commit,
            "applyApprovalReceiptSha256": "8" * 64,
            "migrationFacts": failure_facts,
            "applicationStarted": True,
            "applicationAlreadyCommitted": False,
            "applicationRestored": True,
            "topologyRestored": True,
            "deploymentCommitOutcome": "NOT_COMMITTED",
            "deploymentReceiptPath": None,
            "deploymentReceiptSha256": None,
            "productionFilesystemChanged": True,
            "productionServiceChangedThisRun": True,
            "productionDatabaseChangedThisRun": True,
            "databaseRollbackStrategy":
                "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
            "databaseDownClaimed": False,
            "officialExpertsPackageChanged": False,
            "observedAt": shifted_iso(approval["approvedAt"], -1),
        }
        current = {
            "activeState": "active",
            "jarSha256": "9" * 64,
            "configuredJarSha256": "9" * 64,
            "configuredFlagValues": {
                name: "false" for name in release.FALSE_FLAGS
            },
            "processFlagValues": {
                name: "false" for name in release.FALSE_FLAGS
            },
            "processForbiddenOverrideNames": [],
        }

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            release_dir = root / args.target_release_id
            release_dir.mkdir()
            stage_path = (
                release_dir / "deployment-readiness-receipt.json"
            )
            failure_path = (
                release_dir
                / "apply-failure-20260725T000000000000Z-0123456789ab.json"
            )
            stage_path.write_text(
                release.canonical_json(staged) + "\n",
                encoding="utf-8",
            )
            failure_path.write_text(
                release.canonical_json(apply_failure) + "\n",
                encoding="utf-8",
            )
            args.stage_receipt_sha = release.sha256_file(stage_path)
            args.apply_failure_receipt_sha = release.sha256_file(
                failure_path
            )
            failure_manifest = [{
                "name": failure_path.name,
                "sha256": args.apply_failure_receipt_sha,
                "schema": apply_failure["schema"],
                "state": apply_failure["state"],
            }]
            args.apply_failure_manifest_sha = release.sha256_bytes(
                release.canonical_json(failure_manifest).encode("utf-8")
            )
            bind_interrupted_recovery_authorization(args, approval)

            def write_json(path, value):
                pathlib.Path(path).write_text(
                    release.canonical_json(value) + "\n",
                    encoding="utf-8",
                )

            migration_lease = mock.MagicMock()
            migration_lease.mysql = object()
            migration_lease.facts = retained_facts
            with (
                mock.patch.object(
                    release, "release_directory", return_value=release_dir
                ),
                mock.patch.object(
                    release,
                    "interrupted_apply_target_release",
                    return_value=release_dir,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    side_effect=lambda path, *_args, **_kwargs:
                        pathlib.Path(path),
                ),
                mock.patch.object(
                    release,
                    "validated_latest_receipt_target",
                    return_value=stage_path.resolve(),
                ),
                mock.patch.object(
                    release,
                    "validate_stage_receipt",
                    return_value=(stage_path, staged),
                ),
                mock.patch.object(
                    release, "service_snapshot", return_value=current
                ),
                mock.patch.object(
                    release,
                    "assert_predecessor_active",
                    return_value=current,
                ),
                mock.patch.object(
                    release,
                    "read_exact_migration_facts",
                    return_value=retained_facts,
                ),
                mock.patch.object(
                    release,
                    "interrupted_apply_recovery_lease",
                    return_value=migration_lease,
                ),
                mock.patch.object(
                    release,
                    "exact_migration_facts",
                    return_value=retained_facts,
                ),
                mock.patch.object(
                    release,
                    "environment_flags_explicit_false",
                    return_value=True,
                ),
                mock.patch.object(
                    release,
                    "atomic_json_create_new",
                    side_effect=write_json,
                ),
                mock.patch.object(
                    release,
                    "create_or_validate_interrupted_recovery_anchor",
                    return_value=True,
                ),
                mock.patch.object(
                    release, "advance_latest_receipt", return_value=True
                ) as advance_latest,
            ):
                canonicalize = getattr(
                    release,
                    "canonicalize_interrupted_apply_recovery",
                    None,
                )
                self.assertIsNotNone(
                    canonicalize,
                    "missing interrupted Apply recovery canonicalizer",
                )
                result = canonicalize(args)

            receipt_path = pathlib.Path(result["receiptPath"])
            receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
            self.assertEqual(
                receipt["schema"],
                "fbsir.u3wDefaultOffInterruptedApplyRecoveryReceipt.v1",
            )
            self.assertEqual(
                receipt["state"],
                (
                    "INTERRUPTED_APPLY_RECOVERED_APPLICATION_"
                    "DATABASE_043_RETAINED_DORMANT"
                ),
            )
            self.assertEqual(
                receipt["stageReceiptSha256"],
                release.sha256_file(stage_path),
            )
            self.assertEqual(
                receipt["applyFailureReceiptSha256"],
                release.sha256_file(failure_path),
            )
            self.assertIs(receipt["applicationRestored"], True)
            self.assertIs(receipt["topologyRestored"], True)
            self.assertIs(receipt["deploymentReceiptAbsent"], True)
            self.assertIs(receipt["rollbackReceiptAbsent"], True)
            self.assertIs(receipt["currentLinkAbsent"], True)
            self.assertIs(receipt["releaseDropInMatched"], True)
            self.assertEqual(
                receipt["retainedMigrationFacts"], retained_facts
            )
            self.assertEqual(receipt["retainedMigrationFacts"]["eventCount"], 7)
            self.assertEqual(
                receipt["retainedMigrationFacts"]["probeEventCount"], 7
            )
            self.assertEqual(
                receipt["retainedMigrationFacts"]["naturalEventCount"], 0
            )
            self.assertEqual(
                receipt["retainedMigrationFacts"][
                    "authoritativeProductCreditCount"
                ],
                0,
            )
            self.assertIs(receipt["allW1aFlagsExplicitFalse"], True)
            self.assertIs(receipt["productionDatabaseChanged"], True)
            self.assertIs(
                receipt["productionDatabaseChangedThisRecoveryRun"],
                False,
            )
            self.assertIs(
                receipt["productionDatabaseChangedSinceStage"],
                True,
            )
            self.assertIs(receipt["productionServiceChanged"], True)
            self.assertIs(
                receipt["productionServiceChangedThisRecoveryRun"],
                False,
            )
            self.assertIs(
                receipt["productionServiceChangedSinceStage"],
                True,
            )
            advance_latest.assert_called_once_with(
                receipt_path,
                [stage_path],
            )

    def test_interrupted_apply_recovery_receipt_creation_is_no_clobber(self):
        source = inspect.getsource(
            release.canonicalize_interrupted_apply_recovery
        )
        failures = []
        if "atomic_json(recovery_path, receipt)" in source:
            failures.append("canonicalizer still uses overwrite-capable atomic_json")
        if "atomic_json_create_new(recovery_path, receipt)" not in source:
            failures.append("canonicalizer does not use create-new JSON")
        create_new = getattr(release, "atomic_json_create_new", None)
        if create_new is None:
            failures.append("atomic_json_create_new is absent")
        else:
            with tempfile.TemporaryDirectory() as temporary:
                path = pathlib.Path(temporary) / "recovery.json"
                racer_bytes = b'{"winner":"racer"}\n'
                path.write_bytes(racer_bytes)
                try:
                    create_new(path, {"winner": "canonicalizer"})
                except (FileExistsError, RuntimeError):
                    pass
                else:
                    failures.append("create-new accepted an occupied path")
                if path.read_bytes() != racer_bytes:
                    failures.append("create-new clobbered the race winner")
        self.assertEqual(failures, [])

    def test_existing_interrupted_recovery_rejects_every_field_drift(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = interrupted_apply_recovery_fixture(temporary)
            baseline = interrupted_apply_recovery_receipt(fixture)
            fixture.recovery_path.write_text(
                release.canonical_json(baseline) + "\n",
                encoding="utf-8",
            )
            lease = mock.MagicMock()
            lease.mysql = object()
            lease.facts = fixture.retained_facts
            accepted_drift = []

            def drifted(value):
                if isinstance(value, bool):
                    return not value
                if isinstance(value, str):
                    if not value:
                        return "drift"
                    replacement = "0" if value[0] != "0" else "1"
                    return replacement + value[1:]
                if isinstance(value, list):
                    return value + [{"unexpected": True}]
                if isinstance(value, dict):
                    changed = json.loads(json.dumps(value))
                    changed["unexpected"] = True
                    return changed
                return "unexpected"

            def invoke(receipt, label):
                fixture.recovery_path.write_text(
                    release.canonical_json(receipt) + "\n",
                    encoding="utf-8",
                )
                try:
                    release.canonicalize_interrupted_apply_recovery(
                        fixture.args
                    )
                except RuntimeError:
                    return
                accepted_drift.append(label)

            with (
                mock.patch.object(
                    release,
                    "interrupted_apply_target_release",
                    return_value=fixture.release_dir,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    side_effect=lambda path, *_args, **_kwargs:
                        pathlib.Path(path),
                ),
                mock.patch.object(
                    release,
                    "assert_predecessor_active",
                    return_value=fixture.current,
                ),
                mock.patch.object(
                    release,
                    "read_exact_migration_facts",
                    return_value=fixture.retained_facts,
                ),
                mock.patch.object(
                    release,
                    "exact_migration_facts",
                    return_value=fixture.retained_facts,
                ),
                mock.patch.object(
                    release,
                    "environment_flags_explicit_false",
                    return_value=True,
                ),
                mock.patch.object(
                    release,
                    "validated_latest_receipt_target",
                    return_value=fixture.recovery_path.resolve(),
                ),
                mock.patch.object(
                    release,
                    "advance_latest_receipt",
                    return_value=False,
                ),
                mock.patch.object(
                    release,
                    "interrupted_apply_recovery_lease",
                    create=True,
                    return_value=lease,
                ),
            ):
                for field, value in baseline.items():
                    changed = json.loads(json.dumps(baseline))
                    changed[field] = drifted(value)
                    invoke(changed, f"value:{field}")

                    missing = json.loads(json.dumps(baseline))
                    del missing[field]
                    invoke(missing, f"missing:{field}")

                unknown = json.loads(json.dumps(baseline))
                unknown["unexpected"] = True
                invoke(unknown, "unknown:unexpected")

            self.assertEqual(
                accepted_drift,
                [],
                "existing recovery replay accepted receipt drift",
            )

    def test_existing_interrupted_recovery_accepts_monotonic_probe_growth(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = interrupted_apply_recovery_fixture(
                temporary,
                event_count=9,
                journey_count=5,
            )
            baseline_facts = exact_migration_facts(
                event_count=7,
                journey_count=4,
            )
            receipt = interrupted_apply_recovery_receipt(
                fixture,
                retained_facts=baseline_facts,
            )
            fixture.recovery_path.write_text(
                release.canonical_json(receipt) + "\n",
                encoding="utf-8",
            )
            before = fixture.recovery_path.read_bytes()
            lease = mock.MagicMock()
            lease.mysql = object()
            lease.facts = fixture.retained_facts
            with (
                mock.patch.object(
                    release,
                    "interrupted_apply_target_release",
                    return_value=fixture.release_dir,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    side_effect=lambda path, *_args, **_kwargs:
                        pathlib.Path(path),
                ),
                mock.patch.object(
                    release,
                    "assert_predecessor_active",
                    return_value=fixture.current,
                ),
                mock.patch.object(
                    release,
                    "read_exact_migration_facts",
                    return_value=fixture.retained_facts,
                ),
                mock.patch.object(
                    release,
                    "exact_migration_facts",
                    return_value=fixture.retained_facts,
                ),
                mock.patch.object(
                    release,
                    "environment_flags_explicit_false",
                    return_value=True,
                ),
                mock.patch.object(
                    release,
                    "validated_latest_receipt_target",
                    return_value=fixture.recovery_path.resolve(),
                ),
                mock.patch.object(
                    release,
                    "advance_latest_receipt",
                    return_value=False,
                ),
                mock.patch.object(
                    release,
                    "interrupted_apply_recovery_lease",
                    create=True,
                    return_value=lease,
                ),
            ):
                result = (
                    release.canonicalize_interrupted_apply_recovery(
                        fixture.args
                    )
                )
            self.assertEqual(
                fixture.recovery_path.read_bytes(),
                before,
            )
            self.assertIs(
                result["productionFilesystemChanged"],
                False,
            )

    def test_expired_exact_recovery_repairs_missing_latest_link(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = interrupted_apply_recovery_fixture(temporary)
            approved = dt.datetime.now(dt.timezone.utc) - dt.timedelta(
                days=2
            )
            expires = approved + dt.timedelta(hours=12)
            fixture.approval["approvedAt"] = (
                approved.isoformat().replace("+00:00", "Z")
            )
            fixture.approval["expiresAt"] = (
                expires.isoformat().replace("+00:00", "Z")
            )
            fixture.apply_failure["observedAt"] = shifted_iso(
                fixture.approval["approvedAt"], -1
            )
            fixture.failure_path.write_text(
                release.canonical_json(fixture.apply_failure) + "\n",
                encoding="utf-8",
            )
            fixture.args.apply_failure_receipt_sha = (
                release.sha256_file(fixture.failure_path)
            )
            fixture.failure_manifest = [{
                "name": fixture.failure_path.name,
                "sha256":
                    fixture.args.apply_failure_receipt_sha,
                "schema": fixture.apply_failure["schema"],
                "state": fixture.apply_failure["state"],
            }]
            fixture.args.apply_failure_manifest_sha = (
                release.sha256_bytes(
                    release.canonical_json(
                        fixture.failure_manifest
                    ).encode("utf-8")
                )
            )
            bind_interrupted_recovery_authorization(
                fixture.args, fixture.approval
            )
            (
                fixture.release_dir
                / release.INTERRUPTED_APPLY_RECOVERY_APPROVAL_NAME
            ).write_bytes(fixture.args.approval_raw)
            (
                fixture.release_dir
                / release.INTERRUPTED_APPLY_RECOVERY_PLAN_NAME
            ).write_bytes(fixture.args.recovery_plan_raw)
            receipt = interrupted_apply_recovery_receipt(fixture)
            fixture.recovery_path.write_text(
                release.canonical_json(receipt) + "\n",
                encoding="utf-8",
            )
            lease = mock.MagicMock()
            lease.mysql = object()
            lease.facts = fixture.retained_facts
            with (
                mock.patch.object(
                    release,
                    "interrupted_apply_target_release",
                    return_value=fixture.release_dir,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    side_effect=lambda path, *_args, **_kwargs:
                        pathlib.Path(path),
                ),
                mock.patch.object(
                    release,
                    "assert_predecessor_active",
                    return_value=fixture.current,
                ),
                mock.patch.object(
                    release,
                    "exact_migration_facts",
                    return_value=fixture.retained_facts,
                ),
                mock.patch.object(
                    release,
                    "environment_flags_explicit_false",
                    return_value=True,
                ),
                mock.patch.object(
                    release,
                    "validated_latest_receipt_target",
                    return_value=None,
                ),
                mock.patch.object(
                    release,
                    "advance_latest_receipt",
                    return_value=True,
                ) as advance_latest,
                mock.patch.object(
                    release,
                    "interrupted_apply_recovery_lease",
                    create=True,
                    return_value=lease,
                ),
                mock.patch.object(
                    release,
                    "interrupted_recovery_authorized_commit_time",
                    side_effect=AssertionError(
                        "historic replay requested a fresh time window"
                    ),
                ),
            ):
                result = (
                    release.canonicalize_interrupted_apply_recovery(
                        fixture.args
                    )
                )

            self.assertEqual(
                result["recoveryDisposition"],
                "RECEIPT_LINK_REPAIRED",
            )
            self.assertIs(
                result["productionFilesystemChanged"],
                True,
            )
            advance_latest.assert_called_once_with(
                fixture.recovery_path,
                [fixture.stage_path],
            )

    def test_interrupted_apply_approval_binds_failure_manifest_digest(self):
        args, _ = interrupted_apply_recovery_args()
        release.validate_interrupted_apply_recovery_approval(args)
        for field, value in (
            ("apply_failure_manifest_sha", "c" * 64),
            ("recovery_plan_receipt_sha", "d" * 64),
        ):
            with self.subTest(field=field):
                original = getattr(args, field)
                setattr(args, field, value)
                with self.assertRaisesRegex(RuntimeError, "approval"):
                    release.validate_interrupted_apply_recovery_approval(
                        args
                    )
                setattr(args, field, original)

    def test_expired_recovery_authorization_is_replay_only(self):
        args, approval = interrupted_apply_recovery_args()
        approved = dt.datetime.now(dt.timezone.utc) - dt.timedelta(
            days=2
        )
        approval["approvedAt"] = (
            approved.isoformat().replace("+00:00", "Z")
        )
        approval["expiresAt"] = (
            (approved + dt.timedelta(hours=12))
            .isoformat()
            .replace("+00:00", "Z")
        )
        bind_interrupted_recovery_authorization(args, approval)

        release.validate_interrupted_apply_recovery_approval(
            args, require_current=False
        )
        release.validate_interrupted_apply_recovery_plan(
            args, require_current=False
        )
        with self.assertRaisesRegex(RuntimeError, "approval"):
            release.validate_interrupted_apply_recovery_approval(args)
        with self.assertRaisesRegex(RuntimeError, "Plan"):
            release.validate_interrupted_apply_recovery_plan(args)

    def test_interrupted_apply_recomputes_approved_failure_manifest(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = interrupted_apply_recovery_fixture(temporary)
            fixture.args.apply_failure_manifest_sha = "c" * 64
            bind_interrupted_recovery_authorization(
                fixture.args,
                fixture.approval,
            )
            with (
                mock.patch.object(
                    release,
                    "interrupted_apply_target_release",
                    return_value=fixture.release_dir,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    side_effect=lambda path, *_args, **_kwargs:
                        pathlib.Path(path),
                ),
            ):
                with self.assertRaisesRegex(
                    RuntimeError,
                    "failure manifest anchor drifted",
                ):
                    release.canonicalize_interrupted_apply_recovery(
                        fixture.args
                    )

    def test_interrupted_recovery_lock_spans_double_read_and_latest_cas(self):
        canonicalizer = inspect.getsource(
            release.canonicalize_interrupted_apply_recovery
        )
        failures = []
        lease_factory = getattr(
            release,
            "interrupted_apply_recovery_lease",
            None,
        )
        if lease_factory is None:
            failures.append("interrupted recovery named-lock lease is absent")
        else:
            lease_source = inspect.getsource(lease_factory)
            for statement in (
                "GET_LOCK('u3w:w1a:public_init_043',0)",
                "RELEASE_LOCK('u3w:w1a:public_init_043')",
            ):
                if statement not in lease_source:
                    failures.append(
                        f"recovery lease missing {statement}"
                    )

        database_reads = list(release.re.finditer(
            r"exact_migration_facts\(\s*"
            r"([A-Za-z_][A-Za-z0-9_]*)\.mysql\s*\)",
            canonicalizer,
        ))
        service_reads = list(release.re.finditer(
            r"assert_predecessor_active\(",
            canonicalizer,
        ))
        writer_position = canonicalizer.find(
            "atomic_json_create_new(recovery_path, receipt)"
        )
        latest_position = (
            canonicalizer.find(
                "advance_latest_receipt",
                writer_position,
            )
            if writer_position >= 0
            else -1
        )
        if len(database_reads) < 2:
            failures.append("recovery does not double-read DB under one lease")
        elif len({match.group(1) for match in database_reads}) != 1:
            failures.append("recovery DB rereads use different leases")
        if len(service_reads) < 2:
            failures.append("recovery does not double-read service topology")
        if writer_position < 0:
            failures.append("recovery create-new receipt write is absent")
        if latest_position < 0:
            failures.append("recovery receipt-to-latest CAS is absent")
        if (
            len(database_reads) >= 2
            and len(service_reads) >= 2
            and writer_position >= 0
            and latest_position >= 0
        ):
            first_read = max(
                database_reads[0].start(),
                service_reads[0].start(),
            )
            second_read = min(
                database_reads[-1].start(),
                service_reads[-1].start(),
            )
            if not (
                first_read < second_read
                < writer_position < latest_position
            ):
                failures.append(
                    "receipt write does not follow double current-read"
                )
            lease_name = database_reads[0].group(1)
            close_position = canonicalizer.find(
                f"{lease_name}.close()",
                latest_position,
            )
            if close_position < latest_position:
                failures.append("named lock is released before latest CAS")
        self.assertEqual(failures, [])

    def test_interrupted_apply_target_requires_root_owned_real_directory(self):
        args, _ = interrupted_apply_recovery_args()
        accepted = []
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            target = root / args.target_release_id
            target.mkdir()
            real_lstat = pathlib.Path.lstat
            unsafe = {
                "symlink": types.SimpleNamespace(
                    st_mode=release.stat.S_IFLNK | 0o700,
                    st_uid=0,
                    st_gid=0,
                    st_nlink=1,
                ),
                "non-root": types.SimpleNamespace(
                    st_mode=release.stat.S_IFDIR | 0o700,
                    st_uid=1000,
                    st_gid=0,
                    st_nlink=2,
                ),
                "group-writable": types.SimpleNamespace(
                    st_mode=release.stat.S_IFDIR | 0o775,
                    st_uid=0,
                    st_gid=0,
                    st_nlink=2,
                ),
            }
            with mock.patch.object(release, "RELEASE_ROOT", root):
                for label, status in unsafe.items():
                    def fake_lstat(path, *call_args, **call_kwargs):
                        if path == target:
                            return status
                        return real_lstat(
                            path,
                            *call_args,
                            **call_kwargs,
                        )

                    with mock.patch.object(
                        pathlib.Path,
                        "lstat",
                        autospec=True,
                        side_effect=fake_lstat,
                    ):
                        try:
                            release.interrupted_apply_target_release(args)
                        except RuntimeError:
                            pass
                        else:
                            accepted.append(label)

                stage_public_status = types.SimpleNamespace(
                    st_mode=release.stat.S_IFDIR | 0o755,
                    st_uid=0,
                    st_gid=0,
                    st_nlink=2,
                )

                def stage_public_lstat(
                    path, *call_args, **call_kwargs
                ):
                    if path == target:
                        return stage_public_status
                    return real_lstat(
                        path,
                        *call_args,
                        **call_kwargs,
                    )

                with mock.patch.object(
                    pathlib.Path,
                    "lstat",
                    autospec=True,
                    side_effect=stage_public_lstat,
                ):
                    resolved = release.interrupted_apply_target_release(
                        args
                    )
                    self.assertEqual(resolved, target)

                target.rmdir()
                target.write_bytes(b"not-a-directory")
                try:
                    release.interrupted_apply_target_release(args)
                except RuntimeError:
                    pass
                else:
                    accepted.append("regular-file")

        self.assertEqual(
            accepted,
            [],
            "unsafe target release roots were accepted",
        )

    def test_stage_accepts_exact_interrupted_recovery_predecessor(self):
        with tempfile.TemporaryDirectory() as temporary:
            context = stage_recovery_predecessor_fixture(temporary)
            anchor = validate_stage_recovery_predecessor(context)

        self.assertEqual(
            anchor["schema"],
            "fbsir.u3wPriorInterruptedApplyRecoveryStageAnchor.v1",
        )
        self.assertEqual(
            anchor["releaseId"],
            context.fixture.args.target_release_id,
        )
        self.assertEqual(
            anchor["sourceCommit"],
            context.fixture.args.target_source_commit,
        )
        self.assertEqual(
            anchor["receiptPath"],
            str(context.fixture.recovery_path),
        )
        self.assertEqual(
            anchor["receiptSha256"],
            context.receipt_sha256,
        )
        self.assertEqual(
            anchor["receiptSchema"],
            release.INTERRUPTED_APPLY_RECOVERY_RECEIPT_SCHEMA,
        )
        self.assertEqual(
            anchor["state"],
            release.INTERRUPTED_APPLY_RECOVERY_STATE,
        )
        context.lease.close.assert_called_once_with()

    def test_stage_interrupted_recovery_rejects_latest_service_and_043_drift(
        self,
    ):
        with tempfile.TemporaryDirectory() as temporary:
            context = stage_recovery_predecessor_fixture(temporary)

            with self.assertRaisesRegex(RuntimeError, "latest topology"):
                validate_stage_recovery_predecessor(
                    context,
                    latest_path=context.fixture.stage_path,
                )

            service_drift = json.loads(json.dumps(context.current))
            service_drift["jarSha256"] = "0" * 64
            with self.assertRaisesRegex(
                RuntimeError,
                "predecessor topology",
            ):
                validate_stage_recovery_predecessor(
                    context,
                    current=service_drift,
                )

            regressed = exact_migration_facts(
                event_count=6,
                journey_count=3,
            )
            with self.assertRaisesRegex(RuntimeError, "retained 043"):
                validate_stage_recovery_predecessor(
                    context,
                    current_facts=regressed,
                )

            for field in (
                "naturalEventCount",
                "nonProbeEventCount",
                "authoritativeProductCreditCount",
                "naturalJourneyCount",
                "nonProbeJourneyCount",
            ):
                with self.subTest(field=field):
                    non_probe = json.loads(
                        json.dumps(context.current_facts)
                    )
                    non_probe[field] = 1
                    with self.assertRaisesRegex(
                        RuntimeError,
                        "retained 043",
                    ):
                        validate_stage_recovery_predecessor(
                            context,
                            current_facts=non_probe,
                        )

    def test_stage_interrupted_recovery_rejects_bound_field_drift(self):
        with tempfile.TemporaryDirectory() as temporary:
            context = stage_recovery_predecessor_fixture(temporary)
            baseline = context.receipt
            accepted_drift = []
            drift_cases = {
                "recoveryRunId":
                    "w1a-release-deadbeefcafe-20260725T010101Z",
                "executorSourceCommit": "d" * 40,
                "approvalReceiptSha256": "d" * 64,
                "approvalNonce": "a" * 32,
                "runnerSha256": "d" * 64,
                "workerSha256": "e" * 64,
                "recoveryPlanReceiptSha256": "f" * 64,
                "stageReceiptSha256": "0" * 64,
                "applyFailureReceiptSha256": "1" * 64,
                "applyFailureReceiptManifestSha256": "2" * 64,
            }
            for field, value in drift_cases.items():
                with self.subTest(field=field):
                    drifted = json.loads(json.dumps(baseline))
                    drifted[field] = value
                    context.fixture.recovery_path.write_text(
                        release.canonical_json(drifted) + "\n",
                        encoding="utf-8",
                    )
                    try:
                        validate_stage_recovery_predecessor(context)
                    except RuntimeError:
                        continue
                    accepted_drift.append(field)

            self.assertEqual(
                accepted_drift,
                [],
                "Stage accepted drift in a canonical recovery receipt",
            )

    def test_finalize_stage_keeps_recovery_anchor_independent_for_latest_cas(
        self,
    ):
        finalize_source = inspect.getsource(release.finalize_stage)
        plan_source = inspect.getsource(release.stage_live_plan_target)
        self.assertIn(
            "validate_prior_interrupted_recovery_stage_entry",
            finalize_source,
        )
        self.assertIn(
            '"priorRecoveryAnchor": prior_recovery_anchor',
            finalize_source,
        )
        self.assertIn(
            '[prior_recovery_anchor["receiptPath"]]',
            finalize_source,
        )
        self.assertIn(
            '"priorRollbackAnchor": rollback_plan_anchor',
            plan_source,
        )
        self.assertIn(
            '"priorRecoveryAnchor": recovery_plan_anchor',
            plan_source,
        )

    def test_stage_reentry_repairs_latest_after_commit_point_crash(self):
        args, _ = release_args("FinalizeStage")
        staged = {
            "schema": "fbsir.u3wW1aDeploymentReadinessReceipt.v3",
            "state": "STAGED_FOR_SWITCH",
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "backendBuildSha256": args.backend_sha,
            "frontendBuildSha256": args.frontend_tree_sha,
            "runnerSha256": args.runner_sha,
            "workerSha256": args.worker_sha,
            "adminRootDependencyAdoptionReceiptSha256":
                args.admin_root_dependency_adoption_receipt_sha,
            "stageApprovalReceiptSha256": "7" * 64,
        }
        with tempfile.TemporaryDirectory() as temporary:
            final = pathlib.Path(temporary) / args.release_id
            final.mkdir()
            receipt = final / "deployment-readiness-receipt.json"
            receipt.write_text("{}\n", encoding="utf-8")
            expected_result = {
                "productionFilesystemChanged": False,
                "receiptPath": str(receipt),
            }
            with (
                mock.patch.object(
                    release, "validate_preparation_anchors"
                ),
                mock.patch.object(
                    release, "release_directory", return_value=final
                ),
                mock.patch.object(
                    release,
                    "incoming_directory",
                    return_value=pathlib.Path(temporary) / "incoming",
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=receipt,
                ),
                mock.patch.object(
                    release, "read_json", return_value=staged
                ),
                mock.patch.object(
                    release, "sha256_file", return_value="6" * 64
                ),
                mock.patch.object(
                    release,
                    "staged_worker_result",
                    return_value=expected_result,
                ),
                mock.patch.object(
                    release,
                    "predecessor_facts",
                    return_value={
                        "priorRollbackAnchor": None,
                        "priorRecoveryAnchor": None,
                    },
                ),
                mock.patch.object(
                    release,
                    "advance_latest_receipt",
                    return_value=True,
                ) as repair,
            ):
                result = release.finalize_stage(args)
            repair.assert_called_once_with(receipt, [])
            self.assertTrue(result["productionFilesystemChanged"])

    def test_rollback_catches_timeout_and_writes_failure_receipt(self):
        transition = inspect.getsource(release.rollback_transition)
        rollback = inspect.getsource(release._rollback_release)
        wrapper = inspect.getsource(release.rollback_release)
        self.assertIn("except Exception", transition)
        self.assertIn("except Exception as initial_error", rollback)
        self.assertIn("write_rollback_failure_receipt", rollback)
        self.assertIn("EVIDENCE_FINALIZATION_FAILED", wrapper)
        self.assertIn("no longer matches current", rollback)
        self.assertIn("database safety read", rollback)

    def test_actual_process_default_off_contract_is_exact(self):
        source = inspect.getsource(release.assert_candidate_runtime_contract)
        environment_source = inspect.getsource(
            release.exact_loaded_environment_matches
        )
        self.assertIn("candidate_process_arguments", source)
        self.assertIn("processFlagValues", environment_source)
        self.assertIn("processForbiddenOverrideNames", environment_source)
        self.assertIn("dropInManifest", source)
        self.assertIn("externalConfigManifest", source)
        self.assertIn("additionalConfigSha256", source)

    def test_runtime_override_detection_covers_relaxed_aliases_and_spring(self):
        source = inspect.getsource(release.service_snapshot)
        self.assertIn("normalized_flag_names", source)
        self.assertIn(".startswith(", source)
        self.assertIn('"spring"', source)
        self.assertIn("normalized_java_override_names", source)
        self.assertIn("external_config_manifest()", source)

    def test_external_config_surface_is_strictly_allowlisted(self):
        source = inspect.getsource(release.external_config_manifest)
        self.assertIn("ADMIN_ROOT.iterdir()", source)
        self.assertIn('ADMIN_ROOT / "config"', source)
        self.assertIn("followlinks=False", source)
        self.assertIn("unreviewed Spring external config", source)
        self.assertIn("resolved_allowed", source)

    def test_additional_config_rejects_nested_w1a_override(self):
        content = (
            "fbsir:\n"
            "  independent-board:\n"
            "    attribution:\n"
            "      observation-writer-enabled: true\n"
        )
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "connector.yml"
            path.write_text(content, encoding="utf-8")
            with (
                mock.patch.object(
                    release,
                    "ADDITIONAL_CONFIG_PATH",
                    path,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=path,
                ),
                mock.patch.object(
                    release,
                    "load_additional_yaml_documents",
                    return_value=[
                        {
                            "fbsir": {
                                "independent-board": {
                                    "attribution": {
                                        "observation-writer-enabled": True
                                    }
                                }
                            }
                        }
                    ],
                ),
            ):
                with self.assertRaisesRegex(RuntimeError, "allowlist"):
                    release.validate_additional_config()

    def test_additional_config_rejects_inline_spring_import(self):
        content = (
            "spring: {config: {import: file:/tmp/override.yml}}\n"
        )
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "connector.yml"
            path.write_text(content, encoding="utf-8")
            with (
                mock.patch.object(
                    release,
                    "ADDITIONAL_CONFIG_PATH",
                    path,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=path,
                ),
                mock.patch.object(
                    release,
                    "load_additional_yaml_documents",
                    return_value=[
                        {
                            "spring": {
                                "config": {
                                    "import": "file:/tmp/override.yml"
                                }
                            }
                        }
                    ],
                ),
            ):
                with self.assertRaisesRegex(RuntimeError, "allowlist"):
                    release.validate_additional_config()

    def test_additional_config_safe_loader_is_version_and_duplicate_pinned(self):
        source = inspect.getsource(release.load_additional_yaml_documents)
        self.assertIn("EXPECTED_PYYAML_VERSION", source)
        self.assertIn("yaml.SafeLoader", source)
        self.assertIn("duplicate key", source)
        self.assertIn("yaml.load_all", source)


if __name__ == "__main__":
    unittest.main()
