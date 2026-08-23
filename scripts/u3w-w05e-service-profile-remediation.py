#!/usr/bin/env python3
"""Fail-closed W05E Java partial-receiver profile remediation worker.

The only permitted service configuration value is::

    FBSIR_WEBHOOK_SWEEP_ENABLED=false

The module exposes adapter-driven plan/apply/verify/rollback functions for pure
tests.  The CLI never uses SSH and mutating modes require Linux root, an
explicit production flag, a single-use authorization and a process lock.
"""

from __future__ import annotations

import argparse
import contextlib
import datetime as dt
import hashlib
import json
import os
import pathlib
import re
import socket
import stat
import subprocess
import sys
import tempfile
import time
from typing import Any, Mapping

try:
    import fcntl
except ModuleNotFoundError:  # pragma: no cover - Windows pure tests
    fcntl = None


PLAN_SCHEMA = "fbsir.w05eServiceProfileRemediationPlan.v1"
AUTHORIZATION_SCHEMA = "fbsir.w05eServiceProfileRemediationAuthorization.v1"
APPLY_SCHEMA = "fbsir.w05eServiceProfileRemediationApplyReceipt.v1"
VERIFY_SCHEMA = "fbsir.w05eServiceProfileRemediationVerifyReceipt.v1"
ROLLBACK_SCHEMA = "fbsir.w05eServiceProfileRemediationRollbackReceipt.v1"

TARGET_HOST = "api2.u3w.com"
JAVA_UNIT = "fbsir-admin.service"
NODE_UNIT = "fbss-phase1.service"
EXPECTED_SOURCE_COMMIT = "5d0769c2dad843b1c800c62cab8d9a5a91631b40"
ENVIRONMENT_KEY = "FBSIR_WEBHOOK_SWEEP_ENABLED"
ENVIRONMENT_VALUE = "false"
REPLAY_NOT_AFTER_KEY = (
    "FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_NOT_AFTER"
)

RELEASES = pathlib.Path("/opt/fbsir/admin/releases")
CURRENT = pathlib.Path("/opt/fbsir/admin/current")
ENV_FILE = pathlib.Path("/etc/u3w/fbsir-admin.env")
DROPIN = pathlib.Path(
    "/etc/systemd/system/fbsir-admin.service.d/30-w05-replay.conf"
)
NODE_CURRENT = pathlib.Path("/opt/fbss/phase1/current")
STATE = pathlib.Path("/opt/fbsir/admin/state/w05e-service-profile-remediation")
AUTHORIZATIONS = STATE / "authorizations"
RECEIPTS = STATE / "receipts"
BACKUPS = STATE / "backups"
LOCK = pathlib.Path("/opt/fbsir/admin/.u3w-w05e-service-profile.lock")

HEX40 = re.compile(r"[0-9a-f]{40}\Z")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
AUTHORIZATION_ID = re.compile(r"w05e-profile-[a-z0-9][a-z0-9-]{7,95}\Z")
ENV_ASSIGNMENT = re.compile(r"([A-Za-z_][A-Za-z0-9_]*)=(.*)\Z")
TARGET_ASSIGNMENT = re.compile(
    rf"[ \t]*{re.escape(ENVIRONMENT_KEY)}[ \t]*=(.*)\Z"
)
NOT_AFTER_ASSIGNMENT = re.compile(
    rf"[ \t]*{re.escape(REPLAY_NOT_AFTER_KEY)}[ \t]*=(.*)\Z"
)
JDBC = re.compile(
    r"jdbc:mysql://(?P<host>\[[^]]+\]|[^/:?]+)(?::(?P<port>[0-9]{1,5}))?"
    r"/(?P<database>[A-Za-z0-9_]{1,64})(?:\?.*)?\Z"
)


class RemediationError(RuntimeError):
    """Stable error whose public form never includes environment values."""

    def __init__(self, code: str, path: str):
        super().__init__(f"{code} at {path}")
        self.code = code
        self.path = path

    def public(self) -> dict[str, str]:
        return {"status": "FAIL_CLOSED", "code": self.code, "path": self.path}


def fail(code: str, path: str) -> None:
    raise RemediationError(code, path)


def now_utc() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def format_instant(value: dt.datetime) -> str:
    if not isinstance(value, dt.datetime) or value.tzinfo is None:
        fail("invalid_timestamp", "timestamp")
    utc = value.astimezone(dt.timezone.utc)
    timespec = "microseconds" if utc.microsecond else "seconds"
    return utc.isoformat(timespec=timespec).replace("+00:00", "Z")


def parse_instant(value: Any, path: str) -> dt.datetime:
    if not isinstance(value, str) or not value.endswith("Z"):
        fail("invalid_timestamp", path)
    try:
        parsed = dt.datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError:
        fail("invalid_timestamp", path)
    if parsed.tzinfo != dt.timezone.utc or format_instant(parsed) != value:
        fail("noncanonical_timestamp", path)
    return parsed


def canonical_json(value: Any) -> bytes:
    try:
        text = json.dumps(
            value,
            ensure_ascii=False,
            allow_nan=False,
            sort_keys=True,
            separators=(",", ":"),
        )
    except (TypeError, ValueError):
        fail("invalid_json_value", "document")
    return (text + "\n").encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_document(value: Any) -> str:
    return sha256_bytes(canonical_json(value))


def require_mapping(value: Any, path: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping) or any(not isinstance(key, str) for key in value):
        fail("invalid_object", path)
    return value


def exact_keys(value: Mapping[str, Any], expected: set[str], path: str) -> None:
    if set(value) != expected:
        fail("invalid_shape", path)


def require_hex64(value: Any, path: str) -> str:
    if not isinstance(value, str) or HEX64.fullmatch(value) is None:
        fail("invalid_sha256", path)
    return value


def line_ending(data: bytes, path: str) -> tuple[str, str]:
    if b"\x00" in data or data.startswith(b"\xef\xbb\xbf"):
        fail("unsafe_environment_encoding", path)
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        fail("unsafe_environment_encoding", path)
    crlf_count = text.count("\r\n")
    without_crlf = text.replace("\r\n", "")
    if "\r" in without_crlf or (crlf_count and "\n" in without_crlf):
        fail("mixed_line_endings", path)
    if crlf_count:
        return text, "\r\n"
    return text, "\n"


def environment_update(data: bytes) -> dict[str, Any]:
    """Return a deterministic one-key update without exposing any values."""

    text, ending = line_ending(data, "environment")
    trailing = text.endswith(ending)
    lines = text[:-len(ending)].split(ending) if trailing else text.split(ending)
    if lines == [""]:
        lines = []
    occurrences = [index for index, line in enumerate(lines) if TARGET_ASSIGNMENT.fullmatch(line)]
    if len(occurrences) > 1:
        fail("duplicate_environment_key", "environment")
    changed = list(lines)
    if occurrences:
        index = occurrences[0]
        changed[index] = f"{ENVIRONMENT_KEY}={ENVIRONMENT_VALUE}"
        before_value_state = (
            "already_false"
            if lines[index] == f"{ENVIRONMENT_KEY}={ENVIRONMENT_VALUE}"
            else "present_other"
        )
    else:
        changed.append(f"{ENVIRONMENT_KEY}={ENVIRONMENT_VALUE}")
        before_value_state = "absent"
    output = ending.join(changed)
    if trailing or not lines:
        output += ending
    output_bytes = output.encode("utf-8")
    verified_text, verified_ending = line_ending(output_bytes, "environmentAfter")
    verified_lines = verified_text.split(verified_ending)
    exact_target = [
        line for line in verified_lines if TARGET_ASSIGNMENT.fullmatch(line)
    ]
    if exact_target != [f"{ENVIRONMENT_KEY}={ENVIRONMENT_VALUE}"]:
        fail("environment_postimage_invalid", "environmentAfter")
    return {
        "bytes": output_bytes,
        "beforeSha256": sha256_bytes(data),
        "afterSha256": sha256_bytes(output_bytes),
        "lineEnding": "CRLF" if ending == "\r\n" else "LF",
        "beforeOccurrenceCount": len(occurrences),
        "beforeValueState": before_value_state,
        "afterOccurrenceCount": 1,
        "changedKey": ENVIRONMENT_KEY,
        "changedValue": ENVIRONMENT_VALUE,
        "contentChanged": output_bytes != data,
    }


def parse_nonsecret_env(data: bytes, required: set[str]) -> dict[str, str]:
    """Parse selected values; callers must never serialize the returned mapping."""

    text, ending = line_ending(data, "environmentSecrets")
    found: dict[str, str] = {}
    counts: dict[str, int] = {key: 0 for key in required}
    for raw_line in text.split(ending):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        match = ENV_ASSIGNMENT.fullmatch(line)
        if match is None:
            continue
        key, value = match.groups()
        if key in required:
            counts[key] += 1
            found[key] = value.strip().strip("\"'")
    if any(counts[key] != 1 for key in required):
        fail("database_identity_missing_or_duplicate", "environmentSecrets")
    return found


def replay_dropin_facts(
    dropin: bytes | None,
    *,
    active_release: str,
    read_referenced_file: Any,
    observed_at: dt.datetime,
    mode: int | None,
) -> dict[str, Any]:
    if dropin is None:
        return {
            "exists": False,
            "sha256": None,
            "mode": None,
            "environmentPath": None,
            "notAfter": None,
            "expired": None,
            "action": "RETAIN_ABSENT",
        }
    text, ending = line_ending(dropin, "replayDropin")
    lines = text.split(ending)
    if lines and lines[-1] == "":
        lines.pop()
    if len(lines) != 2 or lines[0] != "[Service]" or not lines[1].startswith(
        "EnvironmentFile="
    ):
        fail("unsafe_replay_dropin_shape", "replayDropin")
    environment_path = lines[1].split("=", 1)[1]
    if not environment_path.startswith("/") or any(
        character in environment_path for character in ("\"", "'", " ", "\t")
    ):
        fail("unsafe_replay_environment_path", "replayDropin")
    release = pathlib.PurePosixPath(active_release)
    referenced = pathlib.PurePosixPath(environment_path)
    if referenced.parent != release / "configuration" or referenced.name != "w05-replay.env":
        fail("replay_environment_outside_active_release", "replayDropin")
    referenced_bytes = read_referenced_file(environment_path)
    referenced_text, referenced_ending = line_ending(
        referenced_bytes, "replayEnvironment"
    )
    matches = []
    for line in referenced_text.split(referenced_ending):
        match = NOT_AFTER_ASSIGNMENT.fullmatch(line)
        if match:
            matches.append(match.group(1).strip())
    if len(matches) != 1:
        fail("replay_not_after_missing_or_duplicate", "replayEnvironment")
    not_after = parse_instant(matches[0], "replayEnvironment.notAfter")
    expired = observed_at >= not_after
    return {
        "exists": True,
        "sha256": sha256_bytes(dropin),
        "mode": mode,
        "environmentPath": environment_path,
        "notAfter": format_instant(not_after),
        "expired": expired,
        "action": "REMOVE_EXPIRED" if expired else "RETAIN_NOT_EXPIRED",
    }


def validate_database_snapshot(value: Any) -> dict[str, Any]:
    snapshot = dict(require_mapping(value, "database"))
    exact_keys(
        snapshot,
        {
            "readOnlyTransaction",
            "scopedMigrationReceipts",
            "migrationStatuses",
            "webhookTables",
            "creditBearingRows",
        },
        "database",
    )
    if snapshot["readOnlyTransaction"] is not True:
        fail("database_not_read_only", "database")
    if snapshot["scopedMigrationReceipts"] != ["public_init_043", "public_init_044"]:
        fail("database_migration_receipts_mismatch", "database")
    if snapshot["migrationStatuses"] != {
        "public_init_043": "APPLIED",
        "public_init_044": "APPLIED",
    }:
        fail("database_migration_status_mismatch", "database")
    if snapshot["webhookTables"] != []:
        fail("webhook_tables_present", "database")
    if type(snapshot["creditBearingRows"]) is not int or snapshot["creditBearingRows"] != 0:
        fail("product_credit_not_false", "database")
    return snapshot


def validate_active_snapshot(value: Any) -> dict[str, Any]:
    snapshot = dict(require_mapping(value, "active"))
    exact_keys(
        snapshot,
        {
            "currentSymlink",
            "releasePath",
            "releaseId",
            "jarSha256",
            "manifestSha256",
            "sourceManifestSha256",
            "sourceCommit",
        },
        "active",
    )
    if snapshot["currentSymlink"] != CURRENT.as_posix():
        fail("active_symlink_path_mismatch", "active")
    release = pathlib.PurePosixPath(str(snapshot["releasePath"]))
    if release.parent != pathlib.PurePosixPath(RELEASES.as_posix()) or snapshot["releaseId"] != release.name:
        fail("active_release_path_mismatch", "active")
    for key in ("jarSha256", "manifestSha256", "sourceManifestSha256"):
        require_hex64(snapshot[key], f"active.{key}")
    if snapshot["sourceCommit"] != EXPECTED_SOURCE_COMMIT:
        fail("active_source_commit_mismatch", "active.sourceCommit")
    return snapshot


def validate_service_snapshot(value: Any, unit: str, path: str) -> dict[str, Any]:
    snapshot = dict(require_mapping(value, path))
    expected = {"unit", "activeState", "subState", "mainPid", "nRestarts"}
    exact_keys(snapshot, expected, path)
    if snapshot["unit"] != unit or snapshot["activeState"] != "active" or snapshot["subState"] != "running":
        fail("service_not_active", path)
    if type(snapshot["mainPid"]) is not int or snapshot["mainPid"] <= 0:
        fail("service_pid_invalid", path)
    if type(snapshot["nRestarts"]) is not int or snapshot["nRestarts"] < 0:
        fail("service_restart_counter_invalid", path)
    return snapshot


def validate_node_snapshot(value: Any) -> dict[str, Any]:
    snapshot = dict(require_mapping(value, "node"))
    exact_keys(snapshot, {"currentTarget", "service"}, "node")
    target = pathlib.PurePosixPath(str(snapshot["currentTarget"]))
    if target.parent != pathlib.PurePosixPath("/opt/fbss/phase1/releases"):
        fail("node_current_target_invalid", "node")
    snapshot["service"] = validate_service_snapshot(
        snapshot["service"], NODE_UNIT, "node.service"
    )
    return snapshot


def collect_bindings(runtime: Any, expected_machine_id_sha256: str, observed_at: dt.datetime) -> dict[str, Any]:
    expected_machine = require_hex64(
        expected_machine_id_sha256, "expectedMachineIdSha256"
    )
    host = dict(require_mapping(runtime.host_snapshot(), "host"))
    exact_keys(host, {"logicalTarget", "machineIdSha256", "hostname"}, "host")
    if host["logicalTarget"] != TARGET_HOST or host["machineIdSha256"] != expected_machine:
        fail("target_host_mismatch", "host")
    if not isinstance(host["hostname"], str) or not host["hostname"]:
        fail("target_hostname_missing", "host")
    active = validate_active_snapshot(runtime.active_snapshot())
    java = validate_service_snapshot(runtime.service_snapshot(JAVA_UNIT), JAVA_UNIT, "javaService")
    node = validate_node_snapshot(runtime.node_snapshot())
    environment_bytes = runtime.read_environment()
    database = validate_database_snapshot(
        runtime.database_snapshot(environment_bytes)
    )
    update = environment_update(environment_bytes)
    environment = {
        key: update[key]
        for key in (
            "beforeSha256",
            "afterSha256",
            "lineEnding",
            "beforeOccurrenceCount",
            "beforeValueState",
            "afterOccurrenceCount",
            "changedKey",
            "changedValue",
            "contentChanged",
        )
    }
    environment["path"] = ENV_FILE.as_posix()
    environment["mode"] = runtime.environment_mode()
    if environment["mode"] not in (0o600, 0o640):
        fail("unsafe_environment_mode", "environment")
    dropin_bytes = runtime.read_dropin()
    dropin = replay_dropin_facts(
        dropin_bytes,
        active_release=active["releasePath"],
        read_referenced_file=runtime.read_file,
        observed_at=observed_at,
        mode=runtime.dropin_mode() if dropin_bytes is not None else None,
    )
    if dropin["mode"] not in (None, 0o644):
        fail("unsafe_dropin_mode", "replayDropin")
    return {
        "host": host,
        "active": active,
        "javaService": java,
        "node": node,
        "database": database,
        "environment": environment,
        "replayDropin": dropin,
    }


def build_plan(runtime: Any, *, expected_machine_id_sha256: str, observed_at: dt.datetime) -> dict[str, Any]:
    bindings = collect_bindings(runtime, expected_machine_id_sha256, observed_at)
    bindings_sha = sha256_document(bindings)
    generated = format_instant(observed_at)
    change = {
        "environmentKey": ENVIRONMENT_KEY,
        "environmentValue": ENVIRONMENT_VALUE,
        "maximumEnvironmentKeysChanged": 1,
        "environmentAction": (
            "SET_FALSE"
            if bindings["environment"]["contentChanged"]
            else "RETAIN_ALREADY_FALSE"
        ),
        "dropinAction": bindings["replayDropin"]["action"],
        "activeReleaseSwitch": False,
        "databaseWrites": 0,
        "nodeWrites": 0,
        "productCreditPromoted": False,
    }
    plan_id = "w05e-profile-" + sha256_document(
        {"generatedAt": generated, "bindingsSha256": bindings_sha, "change": change}
    )[:24]
    return {
        "schemaVersion": PLAN_SCHEMA,
        "planId": plan_id,
        "generatedAt": generated,
        "targetHost": TARGET_HOST,
        "targetUnit": JAVA_UNIT,
        "expectedMachineIdSha256": expected_machine_id_sha256,
        "bindings": bindings,
        "bindingsSha256": bindings_sha,
        "change": change,
        "restartBudget": {"forwardJava": 1, "rollbackJava": 1, "node": 0},
        "productionChanged": False,
        "status": "PLANNED_READ_ONLY",
    }


def validate_plan(plan: Any) -> dict[str, Any]:
    value = dict(require_mapping(plan, "plan"))
    exact_keys(
        value,
        {
            "schemaVersion",
            "planId",
            "generatedAt",
            "targetHost",
            "targetUnit",
            "expectedMachineIdSha256",
            "bindings",
            "bindingsSha256",
            "change",
            "restartBudget",
            "productionChanged",
            "status",
        },
        "plan",
    )
    if value["schemaVersion"] != PLAN_SCHEMA or value["status"] != "PLANNED_READ_ONLY":
        fail("invalid_plan_schema", "plan")
    if value["targetHost"] != TARGET_HOST or value["targetUnit"] != JAVA_UNIT:
        fail("invalid_plan_target", "plan")
    require_hex64(value["expectedMachineIdSha256"], "plan.expectedMachineIdSha256")
    if value["bindingsSha256"] != sha256_document(value["bindings"]):
        fail("plan_bindings_digest_mismatch", "plan")
    if value["restartBudget"] != {"forwardJava": 1, "rollbackJava": 1, "node": 0}:
        fail("invalid_restart_budget", "plan")
    if value["productionChanged"] is not False:
        fail("plan_claims_production_change", "plan")
    change = require_mapping(value["change"], "plan.change")
    if (
        change.get("environmentKey") != ENVIRONMENT_KEY
        or change.get("environmentValue") != ENVIRONMENT_VALUE
        or change.get("maximumEnvironmentKeysChanged") != 1
        or change.get("environmentAction")
        not in ("SET_FALSE", "RETAIN_ALREADY_FALSE")
        or change.get("activeReleaseSwitch") is not False
        or change.get("databaseWrites") != 0
        or change.get("nodeWrites") != 0
        or change.get("productCreditPromoted") is not False
    ):
        fail("invalid_planned_change", "plan.change")
    return value


def validate_authorization(
    authorization: Any,
    *,
    action: str,
    plan_sha256: str,
    plan: Mapping[str, Any],
    observed_at: dt.datetime,
) -> dict[str, Any]:
    value = dict(require_mapping(authorization, "authorization"))
    exact_keys(
        value,
        {
            "schemaVersion",
            "authorizationId",
            "action",
            "planSha256",
            "targetHost",
            "targetUnit",
            "expectedMachineIdSha256",
            "issuedAt",
            "expiresAt",
            "singleUse",
            "approvedBy",
            "status",
        },
        "authorization",
    )
    if value["schemaVersion"] != AUTHORIZATION_SCHEMA or value["status"] != "AUTHORIZED":
        fail("invalid_authorization_schema", "authorization")
    if not isinstance(value["authorizationId"], str) or AUTHORIZATION_ID.fullmatch(value["authorizationId"]) is None:
        fail("invalid_authorization_id", "authorization")
    if value["action"] != action or value["planSha256"] != plan_sha256:
        fail("authorization_binding_mismatch", "authorization")
    if (
        value["targetHost"] != TARGET_HOST
        or value["targetUnit"] != JAVA_UNIT
        or value["expectedMachineIdSha256"] != plan["expectedMachineIdSha256"]
        or value["singleUse"] is not True
        or not isinstance(value["approvedBy"], str)
        or not value["approvedBy"].strip()
    ):
        fail("authorization_target_mismatch", "authorization")
    issued = parse_instant(value["issuedAt"], "authorization.issuedAt")
    expires = parse_instant(value["expiresAt"], "authorization.expiresAt")
    if expires <= issued or expires - issued > dt.timedelta(minutes=15):
        fail("authorization_window_invalid", "authorization")
    if observed_at < issued or observed_at >= expires:
        fail("authorization_not_current", "authorization")
    return value


def verify_preimage(runtime: Any, plan: Mapping[str, Any], observed_at: dt.datetime) -> None:
    expected_environment_sha = plan["bindings"]["environment"]["beforeSha256"]
    if sha256_bytes(runtime.read_environment()) != expected_environment_sha:
        fail("preimage_drift", "environment")
    current_dropin = runtime.read_dropin()
    current_dropin_sha = (
        sha256_bytes(current_dropin) if current_dropin is not None else None
    )
    if current_dropin_sha != plan["bindings"]["replayDropin"]["sha256"]:
        fail("preimage_drift", "replayDropin")
    current = collect_bindings(
        runtime, plan["expectedMachineIdSha256"], observed_at
    )
    if sha256_document(current) != plan["bindingsSha256"]:
        fail("preimage_drift", "bindings")


def ensure_private_directory(path: pathlib.Path) -> None:
    created = not path.exists()
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    if created:
        os.chmod(path, 0o700)
    if not path.is_dir():
        fail("private_directory_invalid", "backup")
    if os.name == "posix" and stat.S_IMODE(path.stat().st_mode) != 0o700:
        fail("backup_directory_mode_invalid", "backup")


def atomic_write(
    path: pathlib.Path, data: bytes, mode: int, *, private_parent: bool = True
) -> None:
    if private_parent:
        ensure_private_directory(path.parent)
    elif not path.parent.is_dir():
        fail("output_parent_missing", "output")
    descriptor, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        os.chmod(temporary, mode)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        os.chmod(path, mode)
    except BaseException:
        with contextlib.suppress(OSError):
            os.unlink(temporary)
        raise


def atomic_json(
    path: pathlib.Path, value: Any, *, private_parent: bool = True
) -> None:
    atomic_write(
        path, canonical_json(value), 0o600, private_parent=private_parent
    )


def create_backups(runtime: Any, plan: Mapping[str, Any], state_root: pathlib.Path) -> dict[str, Any]:
    backup_dir = state_root / "backups" / plan["planId"]
    if backup_dir.exists():
        fail("backup_already_exists", "backup")
    ensure_private_directory(backup_dir)
    environment = runtime.read_environment()
    environment_path = backup_dir / "fbsir-admin.env.before"
    atomic_write(environment_path, environment, 0o600)
    dropin = runtime.read_dropin()
    dropin_path = None
    if dropin is not None:
        dropin_path = backup_dir / "30-w05-replay.conf.before"
        atomic_write(dropin_path, dropin, 0o600)
    return {
        "directory": str(backup_dir),
        "directoryMode": "0700",
        "environmentPath": str(environment_path),
        "environmentSha256": sha256_bytes(environment),
        "environmentBackupMode": "0600",
        "dropinExisted": dropin is not None,
        "dropinPath": str(dropin_path) if dropin_path else None,
        "dropinSha256": sha256_bytes(dropin) if dropin is not None else None,
        "dropinBackupMode": "0600" if dropin is not None else None,
    }


def read_backup(path_text: str, expected_sha: str, backup_root: pathlib.Path) -> bytes:
    path = pathlib.Path(path_text).resolve(strict=True)
    if path.parent != backup_root.resolve(strict=True):
        fail("backup_path_escape", "backup")
    status = path.stat()
    if not stat.S_ISREG(status.st_mode) or (
        os.name == "posix" and stat.S_IMODE(status.st_mode) != 0o600
    ):
        fail("backup_file_mode_invalid", "backup")
    data = path.read_bytes()
    if sha256_bytes(data) != expected_sha:
        fail("backup_hash_mismatch", "backup")
    return data


def verify_postconditions(
    runtime: Any,
    plan: Mapping[str, Any],
    *,
    journal_cursor: str,
) -> dict[str, Any]:
    bindings = plan["bindings"]
    if runtime.host_snapshot() != bindings["host"]:
        fail("host_drift_after_apply", "postconditions")
    if validate_active_snapshot(runtime.active_snapshot()) != bindings["active"]:
        fail("active_release_drift_after_apply", "postconditions")
    if validate_node_snapshot(runtime.node_snapshot()) != bindings["node"]:
        fail("node_drift_after_apply", "postconditions")
    current_environment = runtime.read_environment()
    if validate_database_snapshot(
        runtime.database_snapshot(current_environment)
    ) != bindings["database"]:
        fail("database_drift_after_apply", "postconditions")
    validate_service_snapshot(runtime.service_snapshot(JAVA_UNIT), JAVA_UNIT, "javaServiceAfter")
    environment = environment_update(current_environment)
    if environment["beforeSha256"] != bindings["environment"]["afterSha256"]:
        fail("environment_postimage_drift", "postconditions")
    dropin = runtime.read_dropin()
    action = bindings["replayDropin"]["action"]
    if action == "REMOVE_EXPIRED":
        if dropin is not None:
            fail("expired_dropin_not_removed", "postconditions")
    elif bindings["replayDropin"]["exists"]:
        if dropin is None or sha256_bytes(dropin) != bindings["replayDropin"]["sha256"]:
            fail("retained_dropin_drift", "postconditions")
    elif dropin is not None:
        fail("absent_dropin_created", "postconditions")
    error_count = runtime.webhook_errors_after(journal_cursor)
    if type(error_count) is not int or error_count != 0:
        fail("new_webhook_reconciliation_error", "postconditions")
    return {
        "activeSymlinkUnchanged": True,
        "activeJarSha256Unchanged": True,
        "activeSourceCommitUnchanged": True,
        "nodeSnapshotUnchanged": True,
        "databaseSnapshotUnchanged": True,
        "environmentKeyExact": True,
        "replayDropinLifecycleExact": True,
        "newWebhookReconciliationErrors": 0,
        "productCreditEligible": False,
        "productCreditPromoted": False,
        "naturalTrafficPromoted": False,
    }


def restore_from_backups(
    runtime: Any,
    plan: Mapping[str, Any],
    backups: Mapping[str, Any],
    *,
    state_root: pathlib.Path,
) -> dict[str, Any]:
    backup_root = state_root / "backups" / plan["planId"]
    environment = read_backup(
        backups["environmentPath"], backups["environmentSha256"], backup_root
    )
    current_environment_sha = sha256_bytes(runtime.read_environment())
    runtime.write_environment(
        environment,
        expected_sha256=current_environment_sha,
        mode=plan["bindings"]["environment"]["mode"],
    )
    current_dropin = runtime.read_dropin()
    current_dropin_sha = sha256_bytes(current_dropin) if current_dropin is not None else None
    if backups["dropinExisted"]:
        dropin = read_backup(backups["dropinPath"], backups["dropinSha256"], backup_root)
        runtime.restore_dropin(
            dropin,
            expected_current_sha256=current_dropin_sha,
            mode=plan["bindings"]["replayDropin"]["mode"],
        )
    elif current_dropin is not None:
        runtime.remove_dropin(expected_sha256=current_dropin_sha)
    runtime.daemon_reload()
    runtime.restart_java()
    runtime.wait_java_active()
    if sha256_bytes(runtime.read_environment()) != backups["environmentSha256"]:
        fail("rollback_environment_mismatch", "rollback")
    restored_dropin = runtime.read_dropin()
    if backups["dropinExisted"]:
        if restored_dropin is None or sha256_bytes(restored_dropin) != backups["dropinSha256"]:
            fail("rollback_dropin_mismatch", "rollback")
    elif restored_dropin is not None:
        fail("rollback_dropin_created", "rollback")
    if validate_active_snapshot(runtime.active_snapshot()) != plan["bindings"]["active"]:
        fail("rollback_active_release_drift", "rollback")
    if validate_node_snapshot(runtime.node_snapshot()) != plan["bindings"]["node"]:
        fail("rollback_node_drift", "rollback")
    if validate_database_snapshot(
        runtime.database_snapshot(runtime.read_environment())
    ) != plan["bindings"]["database"]:
        fail("rollback_database_drift", "rollback")
    return {
        "environmentRestored": True,
        "dropinRestored": True,
        "activeReleaseUnchanged": True,
        "nodeUnchanged": True,
        "databaseUnchanged": True,
        "rollbackJavaRestarts": 1,
    }


def apply_transaction(
    runtime: Any,
    *,
    plan_document: Mapping[str, Any],
    authorization_document: Mapping[str, Any],
    state_root: pathlib.Path,
    observed_at: dt.datetime,
    settle_seconds: int = 70,
    before_first_write: Any | None = None,
) -> dict[str, Any]:
    plan = validate_plan(plan_document)
    plan_sha = sha256_document(plan)
    authorization = validate_authorization(
        authorization_document,
        action="apply",
        plan_sha256=plan_sha,
        plan=plan,
        observed_at=observed_at,
    )
    if type(settle_seconds) is not int or not 0 <= settle_seconds <= 120:
        fail("invalid_settle_seconds", "apply")
    verify_preimage(runtime, plan, observed_at)
    backups = create_backups(runtime, plan, state_root)
    cursor = runtime.journal_cursor()
    modified = False
    forward_restarts = 0
    automatic_rollback = None
    try:
        current_environment = runtime.read_environment()
        update = environment_update(current_environment)
        if update["afterSha256"] != plan["bindings"]["environment"]["afterSha256"]:
            fail("environment_postimage_plan_drift", "apply")
        dropin = plan["bindings"]["replayDropin"]
        will_modify = update["contentChanged"] or dropin["action"] == "REMOVE_EXPIRED"
        if will_modify and before_first_write is not None:
            before_first_write()
        if update["contentChanged"]:
            runtime.write_environment(
                update["bytes"],
                expected_sha256=plan["bindings"]["environment"]["beforeSha256"],
                mode=plan["bindings"]["environment"]["mode"],
            )
            modified = True
        if dropin["action"] == "REMOVE_EXPIRED":
            if observed_at < parse_instant(dropin["notAfter"], "plan.replayDropin.notAfter"):
                fail("dropin_not_expired", "apply")
            runtime.remove_dropin(expected_sha256=dropin["sha256"])
            modified = True
        if modified:
            runtime.daemon_reload()
            runtime.restart_java()
            forward_restarts += 1
            if forward_restarts > 1:
                fail("forward_restart_budget_exceeded", "apply")
            runtime.wait_java_active()
        runtime.settle(settle_seconds)
        postconditions = verify_postconditions(runtime, plan, journal_cursor=cursor)
        receipt = {
            "schemaVersion": APPLY_SCHEMA,
            "receiptId": plan["planId"] + "-apply",
            "completedAt": format_instant(observed_at),
            "planId": plan["planId"],
            "planSha256": plan_sha,
            "authorizationId": authorization["authorizationId"],
            "targetHost": TARGET_HOST,
            "targetUnit": JAVA_UNIT,
            "backups": backups,
            "before": {
                "active": plan["bindings"]["active"],
                "environmentSha256": plan["bindings"]["environment"]["beforeSha256"],
                "dropinSha256": plan["bindings"]["replayDropin"]["sha256"],
                "nodeSha256": sha256_document(plan["bindings"]["node"]),
                "databaseSha256": sha256_document(plan["bindings"]["database"]),
            },
            "after": {
                "active": plan["bindings"]["active"],
                "environmentSha256": plan["bindings"]["environment"]["afterSha256"],
                "dropinAction": plan["bindings"]["replayDropin"]["action"],
                "nodeSha256": sha256_document(plan["bindings"]["node"]),
                "databaseSha256": sha256_document(plan["bindings"]["database"]),
            },
            "journalCursor": cursor,
            "forwardJavaRestarts": forward_restarts,
            "nodeRestarts": 0,
            "postconditions": postconditions,
            "automaticRollbackPerformed": False,
            "status": "PASS",
        }
        receipt_path = state_root / "receipts" / f"{plan['planId']}.apply.json"
        atomic_json(receipt_path, receipt)
        receipt["receiptPath"] = str(receipt_path)
        receipt["receiptSha256"] = sha256_bytes(canonical_json({k: v for k, v in receipt.items() if k not in {"receiptPath", "receiptSha256"}}))
        return receipt
    except BaseException as original_error:
        if modified:
            try:
                automatic_rollback = restore_from_backups(
                    runtime, plan, backups, state_root=state_root
                )
            except BaseException:
                fail("apply_failed_and_automatic_rollback_failed", "apply")
        if isinstance(original_error, RemediationError):
            raise original_error
        fail("apply_failed_automatic_rollback_completed" if automatic_rollback else "apply_failed_before_write", "apply")


def validate_apply_receipt(value: Any) -> dict[str, Any]:
    receipt = dict(require_mapping(value, "receipt"))
    required = {
        "schemaVersion", "receiptId", "completedAt", "planId", "planSha256",
        "authorizationId", "targetHost", "targetUnit", "backups", "before",
        "after", "journalCursor", "forwardJavaRestarts", "nodeRestarts",
        "postconditions", "automaticRollbackPerformed", "status"
    }
    if "receiptPath" in receipt:
        receipt.pop("receiptPath")
    if "receiptSha256" in receipt:
        receipt.pop("receiptSha256")
    exact_keys(receipt, required, "receipt")
    if (
        receipt["schemaVersion"] != APPLY_SCHEMA
        or receipt["status"] != "PASS"
        or receipt["targetHost"] != TARGET_HOST
        or receipt["targetUnit"] != JAVA_UNIT
        or type(receipt["forwardJavaRestarts"]) is not int
        or receipt["forwardJavaRestarts"] not in (0, 1)
        or receipt["nodeRestarts"] != 0
        or receipt["automaticRollbackPerformed"] is not False
    ):
        fail("invalid_apply_receipt", "receipt")
    require_hex64(receipt["planSha256"], "receipt.planSha256")
    return receipt


def verify_transaction(
    runtime: Any,
    *,
    plan_document: Mapping[str, Any],
    apply_receipt: Mapping[str, Any],
    observed_at: dt.datetime,
) -> dict[str, Any]:
    plan = validate_plan(plan_document)
    receipt = validate_apply_receipt(apply_receipt)
    if receipt["planId"] != plan["planId"] or receipt["planSha256"] != sha256_document(plan):
        fail("receipt_plan_binding_mismatch", "verify")
    postconditions = verify_postconditions(
        runtime, plan, journal_cursor=receipt["journalCursor"]
    )
    return {
        "schemaVersion": VERIFY_SCHEMA,
        "verifiedAt": format_instant(observed_at),
        "planId": plan["planId"],
        "applyReceiptId": receipt["receiptId"],
        "postconditions": postconditions,
        "productionChanged": False,
        "status": "PASS",
    }


def rollback_transaction(
    runtime: Any,
    *,
    plan_document: Mapping[str, Any],
    apply_receipt: Mapping[str, Any],
    authorization_document: Mapping[str, Any],
    state_root: pathlib.Path,
    observed_at: dt.datetime,
    settle_seconds: int = 70,
    before_first_write: Any | None = None,
) -> dict[str, Any]:
    plan = validate_plan(plan_document)
    receipt = validate_apply_receipt(apply_receipt)
    plan_sha = sha256_document(plan)
    if receipt["planId"] != plan["planId"] or receipt["planSha256"] != plan_sha:
        fail("receipt_plan_binding_mismatch", "rollback")
    authorization = validate_authorization(
        authorization_document,
        action="rollback",
        plan_sha256=plan_sha,
        plan=plan,
        observed_at=observed_at,
    )
    if type(settle_seconds) is not int or not 0 <= settle_seconds <= 120:
        fail("invalid_settle_seconds", "rollback")
    if validate_active_snapshot(runtime.active_snapshot()) != plan["bindings"]["active"]:
        fail("rollback_active_preimage_drift", "rollback")
    if validate_node_snapshot(runtime.node_snapshot()) != plan["bindings"]["node"]:
        fail("rollback_node_preimage_drift", "rollback")
    current_environment = runtime.read_environment()
    if validate_database_snapshot(
        runtime.database_snapshot(current_environment)
    ) != plan["bindings"]["database"]:
        fail("rollback_database_preimage_drift", "rollback")
    if sha256_bytes(current_environment) != plan["bindings"]["environment"]["afterSha256"]:
        fail("rollback_environment_preimage_drift", "rollback")
    if (
        plan["bindings"]["environment"]["beforeSha256"]
        == plan["bindings"]["environment"]["afterSha256"]
        and plan["bindings"]["replayDropin"]["action"] != "REMOVE_EXPIRED"
    ):
        fail("rollback_not_required", "rollback")
    cursor = runtime.journal_cursor()
    if before_first_write is not None:
        before_first_write()
    restored = restore_from_backups(runtime, plan, receipt["backups"], state_root=state_root)
    runtime.settle(settle_seconds)
    if runtime.webhook_errors_after(cursor) != 0:
        fail("new_webhook_reconciliation_error_after_rollback", "rollback")
    result = {
        "schemaVersion": ROLLBACK_SCHEMA,
        "completedAt": format_instant(observed_at),
        "planId": plan["planId"],
        "applyReceiptId": receipt["receiptId"],
        "authorizationId": authorization["authorizationId"],
        "restored": restored,
        "forwardJavaRestarts": 0,
        "rollbackJavaRestarts": 1,
        "nodeRestarts": 0,
        "productCreditPromoted": False,
        "status": "PASS",
    }
    atomic_json(state_root / "receipts" / f"{plan['planId']}.rollback.json", result)
    return result


def run_checked(arguments: list[str], *, environment: dict[str, str] | None = None, input_bytes: bytes | None = None, timeout: int = 120) -> bytes:
    try:
        completed = subprocess.run(
            arguments,
            input=input_bytes,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=environment,
            timeout=timeout,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        fail("command_execution_failed", pathlib.Path(arguments[0]).name)
    if completed.returncode != 0:
        fail("command_failed", pathlib.Path(arguments[0]).name)
    return completed.stdout


def regular_root_file(path: pathlib.Path, modes: tuple[int, ...]) -> os.stat_result:
    status = path.lstat()
    if not stat.S_ISREG(status.st_mode) or status.st_uid != 0 or stat.S_IMODE(status.st_mode) not in modes:
        fail("unsafe_root_file", path.name)
    return status


class LiveRuntime:
    """Local production-host adapter. No method invokes SSH or public HTTP."""

    def host_snapshot(self) -> dict[str, Any]:
        machine_id = pathlib.Path("/etc/machine-id").read_bytes().strip()
        if not machine_id:
            fail("machine_id_missing", "host")
        return {
            "logicalTarget": TARGET_HOST,
            "machineIdSha256": sha256_bytes(machine_id),
            "hostname": socket.getfqdn(),
        }

    def active_snapshot(self) -> dict[str, Any]:
        if not CURRENT.is_symlink():
            fail("active_symlink_missing", "active")
        release = CURRENT.resolve(strict=True)
        if release.parent != RELEASES.resolve(strict=True):
            fail("active_release_outside_root", "active")
        jar = release / "backend/fbsir-admin.jar"
        manifest_path = release / "w05-candidate-manifest.json"
        source_path = release / "evidence/source-manifest.json"
        regular_root_file(jar, (0o600, 0o640, 0o644))
        regular_root_file(manifest_path, (0o600, 0o640, 0o644))
        regular_root_file(source_path, (0o600, 0o640, 0o644))
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        source = json.loads(source_path.read_text(encoding="utf-8"))
        if (
            manifest.get("schemaVersion")
            != "fbsir.w05ReceiverCandidateManifest.v1"
            or manifest.get("releaseId") != release.name
            or manifest.get("repository")
            != "https://github.com/U3W-AI/U3W-AI.git"
            or source.get("schemaVersion") != "fbsir.w05SourceManifest.v1"
            or source.get("repository")
            != "https://github.com/U3W-AI/U3W-AI.git"
            or manifest.get("sourceCommit") != source.get("sourceCommit")
        ):
            fail("active_source_manifest_disagreement", "active")
        jar_sha = sha256_bytes(jar.read_bytes())
        manifest_files = manifest.get("files")
        jar_entry = (
            manifest_files.get("backend/fbsir-admin.jar")
            if isinstance(manifest_files, dict)
            else None
        )
        if not isinstance(jar_entry, dict) or jar_entry.get("sha256") != jar_sha:
            fail("active_jar_manifest_disagreement", "active")
        return {
            "currentSymlink": CURRENT.as_posix(),
            "releasePath": str(release),
            "releaseId": release.name,
            "jarSha256": jar_sha,
            "manifestSha256": sha256_bytes(manifest_path.read_bytes()),
            "sourceManifestSha256": sha256_bytes(source_path.read_bytes()),
            "sourceCommit": source.get("sourceCommit"),
        }

    def service_snapshot(self, unit: str) -> dict[str, Any]:
        if unit not in (JAVA_UNIT, NODE_UNIT):
            fail("unexpected_systemd_unit", "service")
        output = run_checked(
            [
                "/usr/bin/systemctl", "show", unit,
                "-p", "ActiveState", "-p", "SubState", "-p", "MainPID", "-p", "NRestarts",
            ]
        ).decode("utf-8", errors="strict")
        values: dict[str, Any] = {}
        for line in output.splitlines():
            if "=" in line:
                key, value = line.split("=", 1)
                values[key] = int(value) if value.isdigit() else value
        return {
            "unit": unit,
            "activeState": values.get("ActiveState"),
            "subState": values.get("SubState"),
            "mainPid": values.get("MainPID"),
            "nRestarts": values.get("NRestarts"),
        }

    def node_snapshot(self) -> dict[str, Any]:
        if not NODE_CURRENT.is_symlink():
            fail("node_current_symlink_missing", "node")
        return {
            "currentTarget": str(NODE_CURRENT.resolve(strict=True)),
            "service": self.service_snapshot(NODE_UNIT),
        }

    def read_environment(self) -> bytes:
        regular_root_file(ENV_FILE, (0o600, 0o640))
        return ENV_FILE.read_bytes()

    def environment_mode(self) -> int:
        return stat.S_IMODE(regular_root_file(ENV_FILE, (0o600, 0o640)).st_mode)

    def read_dropin(self) -> bytes | None:
        if not DROPIN.exists():
            return None
        regular_root_file(DROPIN, (0o644,))
        return DROPIN.read_bytes()

    def dropin_mode(self) -> int | None:
        if not DROPIN.exists():
            return None
        return stat.S_IMODE(regular_root_file(DROPIN, (0o644,)).st_mode)

    def read_file(self, path_text: str) -> bytes:
        path = pathlib.Path(path_text).resolve(strict=True)
        regular_root_file(path, (0o600, 0o640, 0o644))
        return path.read_bytes()

    def database_snapshot(self, environment_bytes: bytes) -> dict[str, Any]:
        values = parse_nonsecret_env(
            environment_bytes,
            {"FBSIR_MYSQL_URL", "FBSIR_MYSQL_USERNAME", "FBSIR_MYSQL_PASSWORD"},
        )
        match = JDBC.fullmatch(values["FBSIR_MYSQL_URL"])
        if match is None:
            fail("database_url_invalid", "database")
        port = int(match.group("port") or "3306")
        if not 1 <= port <= 65535 or not values["FBSIR_MYSQL_USERNAME"]:
            fail("database_identity_invalid", "database")
        command = [
            "/usr/bin/mysql", "--protocol=TCP",
            f"--host={match.group('host').strip('[]')}", f"--port={port}",
            f"--user={values['FBSIR_MYSQL_USERNAME']}",
            f"--database={match.group('database')}", "--default-character-set=utf8mb4",
            "--batch", "--raw", "--skip-column-names",
        ]
        environment = os.environ.copy()
        environment["MYSQL_PWD"] = values["FBSIR_MYSQL_PASSWORD"]
        sql = (
            "START TRANSACTION READ ONLY;"
            "SELECT CONCAT('M|',version,'|',IF(description LIKE 'APPLIED:%','APPLIED','OTHER')) "
            "FROM u3w_schema_migration WHERE version IN ('public_init_043','public_init_044') ORDER BY version;"
            "SELECT CONCAT('T|',table_name) FROM information_schema.tables WHERE table_schema=DATABASE() "
            "AND table_name IN ('wc_webhook_url','wc_webhook_delivery') ORDER BY table_name;"
            "SELECT CONCAT('C|',COUNT(*)) FROM fbs_board_attr_event_v1 WHERE authoritative_product_credit<>0;"
            "COMMIT;"
        ).encode("utf-8")
        output = run_checked(command, environment=environment, input_bytes=sql).decode("utf-8", errors="strict")
        migrations: list[str] = []
        statuses: dict[str, str] = {}
        tables: list[str] = []
        credit_rows: int | None = None
        for line in output.splitlines():
            parts = line.split("|")
            if len(parts) == 3 and parts[0] == "M":
                migrations.append(parts[1])
                statuses[parts[1]] = parts[2]
            elif len(parts) == 2 and parts[0] == "T":
                tables.append(parts[1])
            elif len(parts) == 2 and parts[0] == "C" and parts[1].isdigit():
                credit_rows = int(parts[1])
            else:
                fail("database_output_invalid", "database")
        return {
            "readOnlyTransaction": True,
            "scopedMigrationReceipts": migrations,
            "migrationStatuses": statuses,
            "webhookTables": tables,
            "creditBearingRows": credit_rows,
        }

    def write_environment(self, data: bytes, *, expected_sha256: str, mode: int) -> None:
        if sha256_bytes(self.read_environment()) != expected_sha256:
            fail("environment_preimage_drift", "environment")
        self._atomic_production_file(ENV_FILE, data, mode)

    def remove_dropin(self, *, expected_sha256: str) -> None:
        current = self.read_dropin()
        if current is None or sha256_bytes(current) != expected_sha256:
            fail("dropin_preimage_drift", "replayDropin")
        DROPIN.unlink()

    def restore_dropin(self, data: bytes, *, expected_current_sha256: str | None, mode: int) -> None:
        current = self.read_dropin()
        current_sha = sha256_bytes(current) if current is not None else None
        if current_sha != expected_current_sha256:
            fail("dropin_restore_preimage_drift", "replayDropin")
        self._atomic_production_file(DROPIN, data, mode)

    def _atomic_production_file(self, path: pathlib.Path, data: bytes, mode: int) -> None:
        descriptor, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
        try:
            os.chmod(temporary, mode)
            with os.fdopen(descriptor, "wb") as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, path)
        except BaseException:
            with contextlib.suppress(OSError):
                os.unlink(temporary)
            raise

    def daemon_reload(self) -> None:
        run_checked(["/usr/bin/systemctl", "daemon-reload"], timeout=60)

    def restart_java(self) -> None:
        run_checked(["/usr/bin/systemctl", "restart", JAVA_UNIT], timeout=120)

    def wait_java_active(self) -> None:
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                validate_service_snapshot(self.service_snapshot(JAVA_UNIT), JAVA_UNIT, "javaWait")
                return
            except RemediationError:
                time.sleep(2)
        fail("java_service_did_not_recover", "javaService")

    def journal_cursor(self) -> str:
        output = run_checked(
            ["/usr/bin/journalctl", "-u", JAVA_UNIT, "--show-cursor", "--no-pager", "-n", "0"]
        ).decode("utf-8", errors="strict")
        for line in output.splitlines():
            if line.startswith("-- cursor: "):
                cursor = line[len("-- cursor: "):]
                if cursor and "\x00" not in cursor:
                    return cursor
        fail("journal_cursor_missing", "journal")

    def webhook_errors_after(self, cursor: str) -> int:
        if not isinstance(cursor, str) or not cursor or len(cursor) > 4096:
            fail("journal_cursor_invalid", "journal")
        output = run_checked(
            ["/usr/bin/journalctl", "-u", JAVA_UNIT, "--after-cursor", cursor, "--no-pager", "-o", "cat"]
        ).decode("utf-8", errors="replace")
        patterns = (
            "WebhookDeliveryReconciliationTask",
            "wc_webhook_url",
            "wc_webhook_delivery",
            "BadSqlGrammar",
        )
        return sum(1 for line in output.splitlines() if any(pattern in line for pattern in patterns))

    def settle(self, seconds: int) -> None:
        if seconds:
            time.sleep(seconds)


def load_json_file(path: pathlib.Path, *, root_owned: bool = False) -> tuple[dict[str, Any], bytes]:
    if root_owned:
        regular_root_file(path, (0o600,))
    data = path.read_bytes()
    try:
        value = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        fail("invalid_json_file", path.name)
    return dict(require_mapping(value, path.name)), data


def consume_authorization(path: pathlib.Path, authorization_id: str) -> pathlib.Path:
    used = path.with_name(authorization_id + ".used.json")
    if used.exists():
        fail("authorization_already_used", "authorization")
    os.replace(path, used)
    return used


def require_mutation_gate(execute_production: bool) -> None:
    if execute_production is not True:
        fail("production_flag_required", "cli")
    if os.name != "posix" or not hasattr(os, "geteuid") or os.geteuid() != 0 or fcntl is None:
        fail("linux_root_required", "cli")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description="W05E partial-receiver profile remediation")
    commands = root.add_subparsers(dest="command", required=True)
    plan = commands.add_parser("plan")
    plan.add_argument("--expected-machine-id-sha256", required=True)
    plan.add_argument("--output")
    apply = commands.add_parser("apply")
    apply.add_argument("--plan", required=True)
    apply.add_argument("--authorization", required=True)
    apply.add_argument("--execute-production", action="store_true")
    apply.add_argument("--settle-seconds", type=int, default=70)
    verify = commands.add_parser("verify")
    verify.add_argument("--plan", required=True)
    verify.add_argument("--receipt", required=True)
    rollback = commands.add_parser("rollback")
    rollback.add_argument("--plan", required=True)
    rollback.add_argument("--receipt", required=True)
    rollback.add_argument("--authorization", required=True)
    rollback.add_argument("--execute-production", action="store_true")
    rollback.add_argument("--settle-seconds", type=int, default=70)
    return root


def main(argv: list[str] | None = None) -> int:
    try:
        args = parser().parse_args(argv)
        runtime = LiveRuntime()
        observed = now_utc()
        if args.command == "plan":
            result = build_plan(
                runtime,
                expected_machine_id_sha256=args.expected_machine_id_sha256,
                observed_at=observed,
            )
            if args.output:
                atomic_json(
                    pathlib.Path(args.output), result, private_parent=False
                )
                result = {
                    "status": "PLANNED_READ_ONLY",
                    "planId": result["planId"],
                    "planSha256": sha256_document(result),
                    "output": str(pathlib.Path(args.output).resolve()),
                    "productionChanged": False,
                }
        elif args.command == "apply":
            require_mutation_gate(args.execute_production)
            if not 65 <= args.settle_seconds <= 120:
                fail("production_settle_window_invalid", "apply")
            plan, plan_bytes = load_json_file(pathlib.Path(args.plan), root_owned=True)
            if plan_bytes != canonical_json(plan):
                fail("noncanonical_plan_file", "plan")
            authorization_path = pathlib.Path(args.authorization)
            authorization, _ = load_json_file(authorization_path, root_owned=True)
            ensure_private_directory(STATE)
            lock_descriptor = os.open(LOCK, os.O_CREAT | os.O_RDWR, 0o600)
            try:
                fcntl.flock(lock_descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
                validated = validate_authorization(
                    authorization,
                    action="apply",
                    plan_sha256=sha256_document(validate_plan(plan)),
                    plan=plan,
                    observed_at=observed,
                )
                result = apply_transaction(
                    runtime,
                    plan_document=plan,
                    authorization_document=authorization,
                    state_root=STATE,
                    observed_at=observed,
                    settle_seconds=args.settle_seconds,
                    before_first_write=lambda: consume_authorization(
                        authorization_path, validated["authorizationId"]
                    ),
                )
            finally:
                with contextlib.suppress(OSError):
                    fcntl.flock(lock_descriptor, fcntl.LOCK_UN)
                os.close(lock_descriptor)
        elif args.command == "verify":
            plan, plan_bytes = load_json_file(pathlib.Path(args.plan), root_owned=True)
            if plan_bytes != canonical_json(plan):
                fail("noncanonical_plan_file", "plan")
            receipt, _ = load_json_file(pathlib.Path(args.receipt), root_owned=True)
            result = verify_transaction(
                runtime,
                plan_document=plan,
                apply_receipt=receipt,
                observed_at=observed,
            )
        else:
            require_mutation_gate(args.execute_production)
            if not 65 <= args.settle_seconds <= 120:
                fail("production_settle_window_invalid", "rollback")
            plan, plan_bytes = load_json_file(pathlib.Path(args.plan), root_owned=True)
            if plan_bytes != canonical_json(plan):
                fail("noncanonical_plan_file", "plan")
            receipt, _ = load_json_file(pathlib.Path(args.receipt), root_owned=True)
            authorization_path = pathlib.Path(args.authorization)
            authorization, _ = load_json_file(authorization_path, root_owned=True)
            ensure_private_directory(STATE)
            lock_descriptor = os.open(LOCK, os.O_CREAT | os.O_RDWR, 0o600)
            try:
                fcntl.flock(lock_descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
                validated = validate_authorization(
                    authorization,
                    action="rollback",
                    plan_sha256=sha256_document(validate_plan(plan)),
                    plan=plan,
                    observed_at=observed,
                )
                result = rollback_transaction(
                    runtime,
                    plan_document=plan,
                    apply_receipt=receipt,
                    authorization_document=authorization,
                    state_root=STATE,
                    observed_at=observed,
                    settle_seconds=args.settle_seconds,
                    before_first_write=lambda: consume_authorization(
                        authorization_path, validated["authorizationId"]
                    ),
                )
            finally:
                with contextlib.suppress(OSError):
                    fcntl.flock(lock_descriptor, fcntl.LOCK_UN)
                os.close(lock_descriptor)
        sys.stdout.buffer.write(canonical_json(result))
        return 0
    except RemediationError as error:
        sys.stderr.buffer.write(canonical_json(error.public()))
        return 2
    except Exception:
        sys.stderr.buffer.write(
            canonical_json({"status": "FAIL_CLOSED", "code": "unhandled_failure", "path": "worker"})
        )
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
