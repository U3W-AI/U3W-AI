#!/usr/bin/env python3
"""Fail-closed reconciliation for one immutable Stage-only U3W release."""

import argparse
import base64
import contextlib
import datetime as dt
import hashlib
import hmac
import json
import os
import pathlib
import re
import stat
import sys
import types
import uuid

try:
    import fcntl
except ImportError:  # pragma: no cover - the production target is Linux.
    fcntl = None


TARGET_HOST = "api2.u3w.com"
SERVICE_UNIT = "fbsir-admin.service"
DATABASE = "fbsir"
ADMIN_ROOT = pathlib.Path("/opt/fbsir/admin")
RELEASE_ROOT = ADMIN_ROOT / "releases"
LATEST_RECEIPT = RELEASE_ROOT / "latest-receipt.json"
CURRENT_LINK = ADMIN_ROOT / "current"
GLOBAL_LOCK = ADMIN_ROOT / ".u3w-production-change.lock"
NGINX_PATH = pathlib.Path(
    "/etc/nginx/conf.d/u3w-placeholder-sites.conf"
)
STAGE_SCHEMA = "fbsir.u3wW1aDeploymentReadinessReceipt.v3"
PRIOR_RECOVERY_SCHEMA = (
    "fbsir.u3wDefaultOffInterruptedApplyRecoveryReceipt.v1"
)
PRIOR_RECOVERY_STATE = (
    "INTERRUPTED_APPLY_RECOVERED_APPLICATION_"
    "DATABASE_043_RETAINED_DORMANT"
)
PRIOR_RECOVERY_ANCHOR_SCHEMA = (
    "fbsir.u3wPriorInterruptedApplyRecoveryStageAnchor.v1"
)
APPROVAL_SCHEMA = "fbsir.u3wStagedReleaseRecoveryApproval.v1"
RECEIPT_SCHEMA = "fbsir.u3wStagedReleaseRecoveryReceipt.v1"
RESULT_SCHEMA = "fbsir.u3wStagedReleaseRecoveryWorkerResult.v1"
PLAN_SCHEMA = "fbsir.u3wStagedReleaseRecoveryPlan.v1"
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
RELEASE_ID_PATTERN = re.compile(
    r"w1a-release-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z"
)
RUN_ID_PATTERN = re.compile(
    r"w1a-stage-recovery-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z"
)
APPROVAL_FIELDS = {
    "schema",
    "action",
    "targetHost",
    "runId",
    "recoverySourceCommit",
    "targetReleaseId",
    "targetSourceCommit",
    "targetStageReceiptSha256",
    "priorRecoveryReceiptSha256",
    "approvedAt",
    "expiresAt",
    "authorizedBy",
    "concurrentDdlProhibited",
    "productionFilesystemWrite",
    "productionDatabaseWrite",
    "productionServiceChange",
    "officialExpertsPackageChange",
    "runnerSha256",
    "workerSha256",
}


def canonical_json(value):
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    )


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with pathlib.Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def json_sha256(value):
    return sha256_bytes(canonical_json(value).encode("utf-8"))


def read_json(path):
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))


def parse_utc(value, label):
    try:
        parsed = dt.datetime.fromisoformat(
            str(value).replace("Z", "+00:00")
        )
    except (TypeError, ValueError) as error:
        raise RuntimeError(label + " time is invalid") from error
    if parsed.tzinfo is None:
        raise RuntimeError(label + " time is invalid")
    return parsed.astimezone(dt.timezone.utc)


def validate_sha(value, label):
    if SHA_PATTERN.fullmatch(str(value or "")) is None:
        raise RuntimeError(label + " SHA-256 is invalid")


def validate_commit(value, label):
    if COMMIT_PATTERN.fullmatch(str(value or "")) is None:
        raise RuntimeError(label + " commit is invalid")


def validate_reconcile_approval(
    args, now=None, *, require_current=True
):
    try:
        raw = base64.b64decode(
            args.approval_json_base64, validate=True
        )
        document = json.loads(raw.decode("utf-8"))
    except (
        ValueError,
        UnicodeDecodeError,
        json.JSONDecodeError,
    ) as error:
        raise RuntimeError("recovery approval encoding is invalid") from error
    if set(document) != APPROVAL_FIELDS:
        raise RuntimeError(
            "recovery approval has missing or unknown fields"
        )
    actual_sha = sha256_bytes(raw)
    approved = parse_utc(document["approvedAt"], "approval")
    expires = parse_utc(document["expiresAt"], "approval")
    current = now or dt.datetime.now(dt.timezone.utc)
    if current.tzinfo is None:
        raise RuntimeError("approval comparison time is invalid")
    current = current.astimezone(dt.timezone.utc)
    expected = {
        "schema": APPROVAL_SCHEMA,
        "action": "RECONCILE_STAGED_W1A_RELEASE",
        "targetHost": TARGET_HOST,
        "runId": args.run_id,
        "recoverySourceCommit": args.recovery_source_commit,
        "targetReleaseId": args.target_release_id,
        "targetSourceCommit": args.target_source_commit,
        "targetStageReceiptSha256":
            args.target_stage_receipt_sha,
        "priorRecoveryReceiptSha256":
            args.prior_recovery_receipt_sha,
        "authorizedBy": "workspace-user",
        "concurrentDdlProhibited": True,
        "productionFilesystemWrite": True,
        "productionDatabaseWrite": False,
        "productionServiceChange": False,
        "officialExpertsPackageChange": False,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
    }
    if (
        actual_sha != args.approval_sha
        or any(document.get(key) != value for key, value in expected.items())
        or expires <= approved
        or expires - approved > dt.timedelta(hours=24)
        or approved > current
        or (
            require_current
            and expires <= current
        )
    ):
        raise RuntimeError(
            "recovery approval identity, scope or validity is invalid"
        )
    return {"document": document, "raw": raw, "sha256": actual_sha}


def validate_stage_identity(
    stage, *, target_release_id, target_source_commit
):
    valid = bool(
        isinstance(stage, dict)
        and stage.get("schema") == STAGE_SCHEMA
        and stage.get("state") == "STAGED_FOR_SWITCH"
        and stage.get("releaseId") == target_release_id
        and stage.get("sourceCommit") == target_source_commit
        and SHA_PATTERN.fullmatch(
            str(stage.get("stageApprovalReceiptSha256") or "")
        )
        is not None
        and stage.get("targetHost") == TARGET_HOST
        and stage.get("serviceUnit") == SERVICE_UNIT
        and stage.get("database") == DATABASE
        and stage.get("applicationRollbackAssemblyVerified") is True
        and stage.get("applicationRollbackProven") is False
        and stage.get("databaseRollbackSafetyProven") is True
        and stage.get("databaseDownClaimed") is False
        and stage.get("actualActiveArtifactsMatched") is False
        and stage.get("productionFilesystemChanged") is True
        and stage.get("productionDatabaseChanged") is False
        and stage.get("productionDatabaseChangedThisRun") is False
        and stage.get("productionDatabaseChangedSinceStage") is False
        and stage.get("productionServiceChanged") is False
        and stage.get("officialExpertsPackageChanged") is False
        and isinstance(stage.get("preStageRuntimeIdentity"), dict)
        and isinstance(stage.get("releaseArtifactManifest"), dict)
    )
    if not valid:
        raise RuntimeError("target Stage v3 identity is invalid")
    return stage


def quarantine_name(release_id, stage_sha256):
    if RELEASE_ID_PATTERN.fullmatch(release_id) is None:
        raise RuntimeError("target release id is invalid")
    validate_sha(stage_sha256, "target Stage receipt")
    return ".quarantine-stage-{}-{}".format(
        release_id.removeprefix("w1a-release-"),
        stage_sha256[:12],
    )


def recovery_receipt_document(
    *,
    run_id,
    recovery_source_commit,
    target_release_id,
    target_source_commit,
    target_stage_receipt_sha256,
    prior_recovery_receipt_sha256,
    approval_sha256,
    runner_sha256,
    worker_sha256,
    quarantine_path,
    staged_tree,
    service_sha256,
    database_sha256,
    nginx_sha256,
    nginx_manifest_sha256,
    nginx_dump_sha256,
    observed_at,
):
    return {
        "schema": RECEIPT_SCHEMA,
        "state": "STAGE_RECONCILED",
        "runId": run_id,
        "recoverySourceCommit": recovery_source_commit,
        "targetReleaseId": target_release_id,
        "targetSourceCommit": target_source_commit,
        "targetStageReceiptSha256": target_stage_receipt_sha256,
        "priorRecoveryReceiptSha256":
            prior_recovery_receipt_sha256,
        "approvalReceiptSha256": approval_sha256,
        "runnerSha256": runner_sha256,
        "workerSha256": worker_sha256,
        "quarantinePath": quarantine_path,
        "quarantineNameExecutableAsRelease": False,
        "stagedTree": staged_tree,
        "serviceSnapshotSha256": service_sha256,
        "databaseSnapshotSha256": database_sha256,
        "nginxSha256": nginx_sha256,
        "nginxManifestSha256": nginx_manifest_sha256,
        "nginxDumpSha256": nginx_dump_sha256,
        "latestReceiptStateAfter": PRIOR_RECOVERY_STATE,
        "currentLinkAbsent": True,
        "deploymentReceiptAbsent": True,
        "rollbackReceiptAbsent": True,
        "applyFailureReceiptAbsent": True,
        "serviceIdentityUnchanged": True,
        "databaseIdentityUnchanged": True,
        "nginxIdentityUnchanged": True,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
        "observedAt": observed_at,
    }


def validate_root_directory(path, modes=(0o700, 0o755)):
    path = pathlib.Path(path)
    status = path.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 not in modes
    ):
        raise RuntimeError("directory custody is invalid: " + str(path))
    return path


def validate_regular_file(path, parent, modes=(0o600, 0o644)):
    path = pathlib.Path(path)
    parent = pathlib.Path(parent).resolve(strict=True)
    if path.is_symlink():
        raise RuntimeError("regular file is a symlink: " + str(path))
    resolved = path.resolve(strict=True)
    if resolved.parent != parent:
        raise RuntimeError("regular file escaped its parent: " + str(path))
    status = resolved.stat()
    if (
        not stat.S_ISREG(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink != 1
        or status.st_mode & 0o777 not in modes
    ):
        raise RuntimeError("regular file custody is invalid: " + str(path))
    return resolved


def fsync_directory(path):
    descriptor = os.open(
        pathlib.Path(path), os.O_RDONLY | os.O_DIRECTORY
    )
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def atomic_symlink(target, link):
    target = pathlib.Path(target)
    link = pathlib.Path(link)
    temporary = ADMIN_ROOT / (
        "." + link.name + ".recovery-" + uuid.uuid4().hex
    )
    os.symlink(str(target), temporary)
    try:
        os.replace(temporary, link)
        fsync_directory(link.parent)
    finally:
        if temporary.exists() or temporary.is_symlink():
            temporary.unlink()


def atomic_json_create_new(path, document):
    path = pathlib.Path(path)
    raw = (canonical_json(document) + "\n").encode("utf-8")
    if path.exists() or path.is_symlink():
        raise RuntimeError("immutable recovery receipt already exists")
    temporary = path.parent / (
        "." + path.name + ".partial-" + uuid.uuid4().hex
    )
    descriptor = os.open(
        temporary,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL
        | getattr(os, "O_NOFOLLOW", 0),
        0o600,
    )
    try:
        remaining = memoryview(raw)
        while remaining:
            written = os.write(descriptor, remaining)
            if written <= 0:
                raise RuntimeError("immutable receipt write made no progress")
            remaining = remaining[written:]
        os.fchown(descriptor, 0, 0)
        os.fchmod(descriptor, 0o600)
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    try:
        if path.exists() or path.is_symlink():
            raise RuntimeError("immutable recovery receipt raced")
        os.replace(temporary, path)
        fsync_directory(path.parent)
    finally:
        if temporary.exists() or temporary.is_symlink():
            temporary.unlink()
    return raw


def stage_tree_facts(root):
    root = validate_root_directory(root)
    entries = []
    file_count = 0
    total_bytes = 0
    for path in sorted(root.rglob("*"), key=lambda item: item.as_posix()):
        relative = path.relative_to(root).as_posix()
        status = path.lstat()
        if (
            stat.S_ISLNK(status.st_mode)
            or status.st_uid != 0
            or status.st_gid != 0
            or (
                not stat.S_ISDIR(status.st_mode)
                and not stat.S_ISREG(status.st_mode)
            )
        ):
            raise RuntimeError(
                "staged tree custody is invalid: " + relative
            )
        entry = {
            "path": relative,
            "mode": status.st_mode & 0o777,
            "uid": status.st_uid,
            "gid": status.st_gid,
        }
        if stat.S_ISDIR(status.st_mode):
            if status.st_mode & 0o022:
                raise RuntimeError(
                    "staged directory is group/world writable: "
                    + relative
                )
            entry["type"] = "directory"
        else:
            if status.st_nlink != 1 or status.st_mode & 0o022:
                raise RuntimeError(
                    "staged file custody is invalid: " + relative
                )
            entry.update({
                "type": "file",
                "sizeBytes": status.st_size,
                "sha256": sha256_file(path),
            })
            file_count += 1
            total_bytes += status.st_size
        entries.append(entry)
    return {
        "sha256": json_sha256(entries),
        "fileCount": file_count,
        "totalBytes": total_bytes,
    }


def validate_artifact_manifest(release, stage):
    release = pathlib.Path(release).resolve(strict=True)
    manifest = stage["releaseArtifactManifest"]
    if not manifest:
        raise RuntimeError("Stage artifact manifest is empty")
    for relative, expected in manifest.items():
        pure = pathlib.PurePosixPath(relative)
        if pure.is_absolute() or ".." in pure.parts:
            raise RuntimeError("Stage artifact path is unsafe")
        candidate = release.joinpath(*pure.parts)
        resolved = validate_regular_file(
            candidate, candidate.parent, modes=(0o600, 0o644)
        )
        resolved.relative_to(release)
        status = resolved.stat()
        if (
            set(expected) != {
                "mode",
                "sha256",
                "sizeBytes",
                "uid",
                "gid",
                "nlink",
            }
            or status.st_mode & 0o777 != expected["mode"]
            or status.st_size != expected["sizeBytes"]
            or status.st_uid != expected["uid"]
            or status.st_gid != expected["gid"]
            or status.st_nlink != expected["nlink"]
            or expected["uid"] != 0
            or expected["gid"] != 0
            or expected["nlink"] != 1
            or sha256_file(resolved) != expected["sha256"]
        ):
            raise RuntimeError("Stage artifact manifest drifted")


def resolved_latest():
    if not LATEST_RECEIPT.is_symlink():
        raise RuntimeError("latest receipt is not a symlink")
    resolved = LATEST_RECEIPT.resolve(strict=True)
    resolved.relative_to(RELEASE_ROOT.resolve(strict=True))
    return resolved


def lexical_latest_target():
    if not LATEST_RECEIPT.is_symlink():
        raise RuntimeError("latest receipt is not a symlink")
    raw = pathlib.Path(os.readlink(LATEST_RECEIPT))
    if not raw.is_absolute():
        raw = LATEST_RECEIPT.parent / raw
    return pathlib.Path(os.path.abspath(os.path.normpath(str(raw))))


def load_stage_worker(release, stage):
    worker = validate_regular_file(
        pathlib.Path(release) / "release-worker.py",
        release,
        modes=(0o600, 0o644),
    )
    if sha256_file(worker) != stage.get("workerSha256"):
        raise RuntimeError("staged worker digest drifted")
    module = types.ModuleType(
        "reconciled_staged_release_worker"
    )
    module.__file__ = str(worker)
    source = worker.read_bytes()
    if sha256_bytes(source) != stage.get("workerSha256"):
        raise RuntimeError("staged worker bytes drifted")
    exec(
        compile(source, str(worker), "exec"),
        module.__dict__,
    )
    return module


def validate_prior_recovery(app, expected_sha256):
    anchor = app.get("priorRecoveryAnchor")
    if not isinstance(anchor, dict):
        raise RuntimeError("prior recovery anchor is absent")
    path = pathlib.Path(str(anchor.get("receiptPath") or ""))
    expected_path = (
        RELEASE_ROOT
        / str(anchor.get("releaseId") or "")
        / "interrupted-apply-recovery-receipt.json"
    )
    receipt = validate_regular_file(
        path, expected_path.parent, modes=(0o600,)
    )
    if (
        anchor.get("schema") != PRIOR_RECOVERY_ANCHOR_SCHEMA
        or anchor.get("receiptSchema") != PRIOR_RECOVERY_SCHEMA
        or anchor.get("state") != PRIOR_RECOVERY_STATE
        or RELEASE_ID_PATTERN.fullmatch(
            str(anchor.get("releaseId") or "")
        ) is None
        or COMMIT_PATTERN.fullmatch(
            str(anchor.get("sourceCommit") or "")
        ) is None
        or receipt != expected_path
        or anchor.get("receiptSha256") != expected_sha256
        or sha256_file(receipt) != expected_sha256
    ):
        raise RuntimeError("prior recovery anchor drifted")
    document = read_json(receipt)
    if (
        document.get("schema") != PRIOR_RECOVERY_SCHEMA
        or document.get("state") != PRIOR_RECOVERY_STATE
        or document.get("releaseId") != anchor.get("releaseId")
        or document.get("sourceCommit") != anchor.get("sourceCommit")
        or document.get("applicationRestored") is not True
        or document.get("topologyRestored") is not True
        or document.get("deploymentCommitOutcome") != "NOT_COMMITTED"
        or document.get("deploymentReceiptAbsent") is not True
        or document.get("rollbackReceiptAbsent") is not True
        or document.get("currentLinkAbsent") is not True
        or document.get("releaseDropInMatched") is not True
        or document.get("allW1aFlagsExplicitFalse") is not True
        or document.get("databaseDownClaimed") is not False
        or document.get("productionDatabaseChanged") is not False
        or document.get(
            "productionDatabaseChangedThisRecoveryRun"
        ) is not False
        or document.get("productionDatabaseChangedSinceStage")
            is not False
        or type(document.get("productionServiceChanged")) is not bool
        or document.get(
            "productionServiceChangedThisRecoveryRun"
        ) is not False
        or type(document.get("productionServiceChangedSinceStage"))
            is not bool
        or document.get("productionServiceChangedSinceStage")
            != document.get("productionServiceChanged")
        or document.get("officialExpertsPackageChanged") is not False
    ):
        raise RuntimeError("prior recovery receipt identity drifted")
    return receipt


def collect_runtime_facts(release, stage, app, plan):
    module = load_stage_worker(release, stage)
    service = module.service_snapshot(require_active=False)
    database = module.database_pre_stage_facts()
    nginx_manifest, nginx_dump_sha256 = (
        module.active_nginx_manifest()
    )
    expected_service = app.get("serviceSnapshotBeforeStage")
    expected_database = stage.get("preStageRuntimeIdentity")
    expected_target = plan.get("target", {})
    if (
        service != expected_service
        or service.get("activeState") != "active"
        or database != expected_database
        or nginx_manifest
            != expected_target.get("activeNginxManifest")
        or nginx_dump_sha256
            != expected_target.get("nginxDumpSha256")
    ):
        raise RuntimeError(
            "service, database or active Nginx drifted from the Stage prestate"
        )
    nginx_path = pathlib.Path(str(app.get("previousNginxPath") or ""))
    nginx = validate_regular_file(
        nginx_path, nginx_path.parent, modes=(0o600, 0o640, 0o644)
    )
    nginx_status = nginx.stat()
    if (
        nginx != NGINX_PATH
        or sha256_file(nginx) != app.get("previousNginxSha256")
        or nginx_status.st_uid != app.get("previousNginxUid")
        or nginx_status.st_gid != app.get("previousNginxGid")
        or nginx_status.st_mode & 0o777 != app.get("previousNginxMode")
    ):
        raise RuntimeError("predecessor Nginx identity drifted")
    return {
        "service": service,
        "database": database,
        "nginxSha256": sha256_file(nginx),
        "nginxManifestSha256": json_sha256(nginx_manifest),
        "nginxDumpSha256": nginx_dump_sha256,
        "stageWorker": module,
    }


def recovery_receipt_path(args):
    return RELEASE_ROOT / (
        "stage-recovery-{}-{}.json".format(
            args.target_release_id.removeprefix("w1a-release-"),
            args.target_stage_receipt_sha[:12],
        )
    )


def inspect_stage(release, args):
    release = validate_root_directory(release, modes=(0o755,))
    stage_path = validate_regular_file(
        release / "deployment-readiness-receipt.json",
        release,
        modes=(0o600,),
    )
    if sha256_file(stage_path) != args.target_stage_receipt_sha:
        raise RuntimeError("target Stage receipt digest drifted")
    stage = validate_stage_identity(
        read_json(stage_path),
        target_release_id=args.target_release_id,
        target_source_commit=args.target_source_commit,
    )
    prohibited = [
        release / "deployment-receipt.json",
        release / "rollback-receipt.json",
        release / "rollback-verification-receipt.json",
        release / "interrupted-apply-recovery-receipt.json",
    ]
    if (
        any(path.exists() or path.is_symlink() for path in prohibited)
        or list(release.glob("apply-failure-*.json"))
    ):
        raise RuntimeError(
            "target release is no longer a Stage-only lifecycle"
        )
    if CURRENT_LINK.exists() or CURRENT_LINK.is_symlink():
        raise RuntimeError("current release link must remain absent")
    validate_artifact_manifest(release, stage)
    app_relative = "evidence/application-rollback-assembly.json"
    app_path = validate_regular_file(
        release / app_relative,
        release / "evidence",
        modes=(0o600,),
    )
    database_path = validate_regular_file(
        release / "evidence/database-rollback-safety.json",
        release / "evidence",
        modes=(0o600,),
    )
    plan_path = validate_regular_file(
        release / "evidence/release-plan.json",
        release / "evidence",
        modes=(0o600, 0o644),
    )
    if (
        stage.get("applicationRollbackAssemblyReceiptPath")
            != str(
                RELEASE_ROOT
                / args.target_release_id
                / app_relative
            )
        or sha256_file(app_path)
            != stage.get(
                "applicationRollbackAssemblyReceiptSha256"
            )
        or sha256_file(database_path)
            != stage.get("databaseRollbackSafetyReceiptSha256")
        or sha256_file(plan_path)
            != stage.get("releasePlanReceiptSha256")
    ):
        raise RuntimeError("nested Stage evidence binding drifted")
    app = read_json(app_path)
    database_safety = read_json(database_path)
    plan = read_json(plan_path)
    if (
        app.get("schema")
            != "fbsir.u3wApplicationRollbackAssemblyReceipt.v1"
        or app.get("releaseId") != args.target_release_id
        or app.get("sourceCommit") != args.target_source_commit
        or app.get("stageApprovalReceiptSha256")
            != stage.get("stageApprovalReceiptSha256")
        or app.get("assemblyVerified") is not True
        or app.get("applicationRollbackProven") is not False
        or app.get("productionDatabaseChanged") is not False
        or app.get("productionServiceChanged") is not False
        or database_safety.get("schema")
            != "fbsir.u3wDatabaseRollbackSafetyReceipt.v2"
        or database_safety.get("verified") is not True
        or database_safety.get("databaseDownClaimed") is not False
        or database_safety.get("productionDatabaseChanged") is not False
        or plan.get("schema") != "fbsir.u3wDefaultOffReleasePlan.v2"
        or plan.get("releaseId") != args.target_release_id
        or plan.get("sourceCommit") != args.target_source_commit
        or not isinstance(plan.get("target"), dict)
    ):
        raise RuntimeError("nested Stage evidence identity drifted")
    prior_receipt = validate_prior_recovery(
        app, args.prior_recovery_receipt_sha
    )
    runtime = collect_runtime_facts(release, stage, app, plan)
    return {
        "stage": stage,
        "stagePath": stage_path,
        "application": app,
        "plan": plan,
        "priorReceipt": prior_receipt,
        "tree": stage_tree_facts(release),
        "service": runtime["service"],
        "database": runtime["database"],
        "nginxSha256": runtime["nginxSha256"],
        "nginxManifestSha256": runtime["nginxManifestSha256"],
        "nginxDumpSha256": runtime["nginxDumpSha256"],
        "stageWorker": runtime["stageWorker"],
    }


def validate_existing_recovery_receipt(path, args, quarantine):
    path = validate_regular_file(path, RELEASE_ROOT, modes=(0o600,))
    document = read_json(path)
    required = recovery_receipt_document(
        run_id=args.run_id,
        recovery_source_commit=args.recovery_source_commit,
        target_release_id=args.target_release_id,
        target_source_commit=args.target_source_commit,
        target_stage_receipt_sha256=args.target_stage_receipt_sha,
        prior_recovery_receipt_sha256=
            args.prior_recovery_receipt_sha,
        approval_sha256=document.get("approvalReceiptSha256"),
        runner_sha256=args.runner_sha,
        worker_sha256=args.worker_sha,
        quarantine_path=str(quarantine),
        staged_tree=document.get("stagedTree"),
        service_sha256=document.get("serviceSnapshotSha256"),
        database_sha256=document.get("databaseSnapshotSha256"),
        nginx_sha256=document.get("nginxSha256"),
        nginx_manifest_sha256=document.get("nginxManifestSha256"),
        nginx_dump_sha256=document.get("nginxDumpSha256"),
        observed_at=document.get("observedAt"),
    )
    if document != required:
        raise RuntimeError("existing Stage recovery receipt drifted")
    return {
        "document": document,
        "path": path,
        "sha256": sha256_file(path),
    }


def inspect_recovery_state(args):
    validate_root_directory(RELEASE_ROOT, modes=(0o755,))
    target = RELEASE_ROOT / args.target_release_id
    quarantine = RELEASE_ROOT / quarantine_name(
        args.target_release_id, args.target_stage_receipt_sha
    )
    receipt_path = recovery_receipt_path(args)
    if target.exists() or target.is_symlink():
        if quarantine.exists() or quarantine.is_symlink():
            raise RuntimeError(
                "target and quarantine both exist; topology is ambiguous"
            )
        facts = inspect_stage(target, args)
        if resolved_latest() != facts["stagePath"]:
            raise RuntimeError("latest receipt is not the exact Stage")
        return {
            "state": "STAGED_RECOVERY_ELIGIBLE",
            "target": target,
            "quarantine": quarantine,
            "recoveryReceiptPath": receipt_path,
            **facts,
        }
    if not quarantine.exists() or quarantine.is_symlink():
        raise RuntimeError("neither target Stage nor quarantine exists")
    facts = inspect_stage(quarantine, args)
    latest_target = lexical_latest_target()
    expected_stage_target = (
        target / "deployment-readiness-receipt.json"
    )
    expected_prior_target = facts["priorReceipt"]
    if latest_target == expected_stage_target:
        latest_state = "QUARANTINED_LATEST_REPAIR_PENDING"
    elif latest_target == expected_prior_target:
        if resolved_latest() != expected_prior_target:
            raise RuntimeError("latest prior recovery CAS is incomplete")
        latest_state = "RECOVERY_FINALIZATION_PENDING"
    else:
        raise RuntimeError("latest receipt target is ambiguous")
    existing = None
    if receipt_path.exists() or receipt_path.is_symlink():
        if latest_state != "RECOVERY_FINALIZATION_PENDING":
            raise RuntimeError(
                "recovery receipt exists before latest repair"
            )
        existing = validate_existing_recovery_receipt(
            receipt_path, args, quarantine
        )
        document = existing["document"]
        if (
            facts["tree"] != document["stagedTree"]
            or json_sha256(facts["service"])
                != document["serviceSnapshotSha256"]
            or json_sha256(facts["database"])
                != document["databaseSnapshotSha256"]
            or facts["nginxSha256"] != document["nginxSha256"]
            or facts["nginxManifestSha256"]
                != document["nginxManifestSha256"]
            or facts["nginxDumpSha256"]
                != document["nginxDumpSha256"]
        ):
            raise RuntimeError(
                "quarantined Stage or runtime evidence drifted"
            )
    return {
        "state": (
            "ALREADY_RECONCILED"
            if existing is not None
            else latest_state
        ),
        "target": target,
        "quarantine": quarantine,
        "recoveryReceiptPath": receipt_path,
        "existingReceipt": existing,
        **facts,
    }


def validate_lock_descriptor(descriptor):
    status = os.fstat(descriptor)
    if (
        not stat.S_ISREG(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink != 1
        or status.st_mode & 0o777 != 0o600
    ):
        raise RuntimeError("global recovery lock custody drifted")


@contextlib.contextmanager
def shared_recovery_lock():
    if fcntl is None:
        raise RuntimeError("POSIX shared locking is unavailable")
    descriptor = os.open(
        GLOBAL_LOCK,
        os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
    )
    try:
        validate_lock_descriptor(descriptor)
        fcntl.flock(descriptor, fcntl.LOCK_SH | fcntl.LOCK_NB)
        yield
    finally:
        os.close(descriptor)


@contextlib.contextmanager
def exclusive_recovery_lock():
    if fcntl is None:
        raise RuntimeError("POSIX exclusive locking is unavailable")
    descriptor = os.open(
        GLOBAL_LOCK,
        os.O_RDWR | getattr(os, "O_NOFOLLOW", 0),
    )
    try:
        validate_lock_descriptor(descriptor)
        fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield
    finally:
        os.close(descriptor)


def plan_result(args, state):
    return {
        "schema": PLAN_SCHEMA,
        "mode": "Plan",
        "state": state["state"],
        "runId": args.run_id,
        "recoverySourceCommit": args.recovery_source_commit,
        "targetReleaseId": args.target_release_id,
        "targetSourceCommit": args.target_source_commit,
        "targetStageReceiptSha256":
            args.target_stage_receipt_sha,
        "priorRecoveryReceiptSha256":
            args.prior_recovery_receipt_sha,
        "quarantinePath": str(state["quarantine"]),
        "stagedTree": state["tree"],
        "serviceSnapshotSha256": json_sha256(state["service"]),
        "databaseSnapshotSha256": json_sha256(state["database"]),
        "nginxSha256": state["nginxSha256"],
        "nginxManifestSha256": state["nginxManifestSha256"],
        "nginxDumpSha256": state["nginxDumpSha256"],
        "currentLinkAbsent": True,
        "productionChanged": False,
        "officialExpertsPackageChanged": False,
        "observedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
    }


def plan_recovery(args):
    with shared_recovery_lock():
        return plan_result(args, inspect_recovery_state(args))


def reconcile_recovery(args, approval):
    with exclusive_recovery_lock():
        state = inspect_recovery_state(args)
        if state["state"] == "ALREADY_RECONCILED":
            existing = state["existingReceipt"]
            return {
                "schema": RESULT_SCHEMA,
                "mode": "Reconcile",
                "state": "ALREADY_RECONCILED",
                "runId": args.run_id,
                "targetReleaseId": args.target_release_id,
                "receiptPath": str(existing["path"]),
                "receiptSha256": existing["sha256"],
                "approvalReceiptSha256":
                    existing["document"]["approvalReceiptSha256"],
                "productionFilesystemChanged": False,
                "productionDatabaseChanged": False,
                "productionServiceChanged": False,
                "officialExpertsPackageChanged": False,
            }
        current_approval = validate_reconcile_approval(args)
        if current_approval["sha256"] != approval["sha256"]:
            raise RuntimeError(
                "recovery approval changed before commit"
            )
        target = state["target"]
        quarantine = state["quarantine"]
        if state["state"] == "STAGED_RECOVERY_ELIGIBLE":
            os.rename(target, quarantine)
            fsync_directory(RELEASE_ROOT)
        if state["state"] in {
            "STAGED_RECOVERY_ELIGIBLE",
            "QUARANTINED_LATEST_REPAIR_PENDING",
        }:
            prior_receipt = state["priorReceipt"]
            atomic_symlink(prior_receipt, LATEST_RECEIPT)
        elif state["state"] != "RECOVERY_FINALIZATION_PENDING":
            raise RuntimeError("unsupported Stage recovery state")
        if target.exists() or target.is_symlink():
            raise RuntimeError("executable target ReleaseId remains")
        if resolved_latest() != state["priorReceipt"]:
            raise RuntimeError("latest prior recovery CAS failed")
        quarantined_tree = stage_tree_facts(quarantine)
        if quarantined_tree != state["tree"]:
            raise RuntimeError("Stage tree drifted during quarantine rename")
        runtime = collect_runtime_facts(
            quarantine,
            state["stage"],
            state["application"],
            state["plan"],
        )
        if (
            runtime["service"] != state["service"]
            or runtime["database"] != state["database"]
            or runtime["nginxSha256"] != state["nginxSha256"]
            or runtime["nginxManifestSha256"]
                != state["nginxManifestSha256"]
            or runtime["nginxDumpSha256"]
                != state["nginxDumpSha256"]
        ):
            raise RuntimeError(
                "service, database or Nginx changed during recovery"
            )
        receipt_approval = validate_reconcile_approval(args)
        if receipt_approval["sha256"] != approval["sha256"]:
            raise RuntimeError(
                "recovery approval changed before receipt commit"
            )
        observed_at = dt.datetime.now(dt.timezone.utc).isoformat()
        receipt = recovery_receipt_document(
            run_id=args.run_id,
            recovery_source_commit=args.recovery_source_commit,
            target_release_id=args.target_release_id,
            target_source_commit=args.target_source_commit,
            target_stage_receipt_sha256=
                args.target_stage_receipt_sha,
            prior_recovery_receipt_sha256=
                args.prior_recovery_receipt_sha,
            approval_sha256=approval["sha256"],
            runner_sha256=args.runner_sha,
            worker_sha256=args.worker_sha,
            quarantine_path=str(quarantine),
            staged_tree=quarantined_tree,
            service_sha256=json_sha256(runtime["service"]),
            database_sha256=json_sha256(runtime["database"]),
            nginx_sha256=runtime["nginxSha256"],
            nginx_manifest_sha256=runtime[
                "nginxManifestSha256"
            ],
            nginx_dump_sha256=runtime["nginxDumpSha256"],
            observed_at=observed_at,
        )
        receipt_path = state["recoveryReceiptPath"]
        raw = atomic_json_create_new(receipt_path, receipt)
        return {
            "schema": RESULT_SCHEMA,
            "mode": "Reconcile",
            "state": "STAGE_RECONCILED",
            "runId": args.run_id,
            "targetReleaseId": args.target_release_id,
            "receiptPath": str(receipt_path),
            "receiptSha256": sha256_bytes(raw),
            "approvalReceiptSha256": approval["sha256"],
            "productionFilesystemChanged": True,
            "productionDatabaseChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
        }


def validate_args(args):
    if RUN_ID_PATTERN.fullmatch(args.run_id) is None:
        raise RuntimeError("recovery run id is invalid")
    if RELEASE_ID_PATTERN.fullmatch(args.target_release_id) is None:
        raise RuntimeError("target release id is invalid")
    validate_commit(args.recovery_source_commit, "recovery source")
    validate_commit(args.target_source_commit, "target source")
    validate_sha(args.target_stage_receipt_sha, "target Stage receipt")
    validate_sha(args.prior_recovery_receipt_sha, "prior recovery")
    validate_sha(args.runner_sha, "runner")
    validate_sha(args.worker_sha, "worker")
    if args.mode == "Reconcile":
        validate_sha(args.approval_sha, "approval")


def execute(args):
    validate_args(args)
    if getattr(os, "geteuid", lambda: -1)() != 0:
        raise RuntimeError("staged release recovery requires root")
    if args.mode == "Plan":
        return plan_recovery(args)
    approval = validate_reconcile_approval(
        args, require_current=False
    )
    return reconcile_recovery(args, approval)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode", required=True, choices=("Plan", "Reconcile")
    )
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--recovery-source-commit", required=True)
    parser.add_argument("--target-release-id", required=True)
    parser.add_argument("--target-source-commit", required=True)
    parser.add_argument("--target-stage-receipt-sha", required=True)
    parser.add_argument("--prior-recovery-receipt-sha", required=True)
    parser.add_argument("--runner-sha", required=True)
    parser.add_argument("--worker-sha", required=True)
    parser.add_argument("--approval-sha", default="0" * 64)
    parser.add_argument("--approval-json-base64", default="")
    args = parser.parse_args()
    try:
        result = execute(args)
        print(canonical_json(result))
    except Exception as error:
        envelope = {
            "schema":
                "fbsir.u3wStagedReleaseRecoveryWorkerError.v1",
            "mode": args.mode,
            "state": "WORKER_FAILED",
            "runId": args.run_id,
            "targetReleaseId": args.target_release_id,
            "errorType": type(error).__name__,
            "errorMessageSha256":
                sha256_bytes(str(error).encode("utf-8")),
            "productionDatabaseChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
        }
        print(canonical_json(envelope))
        raise SystemExit(1)


if __name__ == "__main__":
    main()
