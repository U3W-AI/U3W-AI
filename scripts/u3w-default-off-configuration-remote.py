#!/usr/bin/env python3
"""Atomically provision W1A default-off configuration and key custody.

Secrets are generated on the target host.  Their values and individual
digests are never written to stdout, stderr, or the receipt.
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
import secrets
import stat
import subprocess
import sys
import time


TARGET_HOST = "api2.u3w.com"
SERVICE_UNIT = "fbsir-admin.service"
ENV_PATH = pathlib.Path("/etc/u3w/fbsir-admin.env")
API2_EVENT_KEY_PATH = pathlib.Path(
    "/etc/u3w/secrets/independent-board-attribution-event-key"
)
CONFIG_ROOT = pathlib.Path("/opt/fbsir/admin/configuration/w1a")
CONFIG_LATEST = pathlib.Path("/opt/fbsir/admin/configuration/latest")
ENV_BACKUP_ROOT = pathlib.Path("/etc/u3w/backups/fbsir-admin-env")
HOST_CHANGE_LOCK = pathlib.Path(
    "/opt/fbsir/admin/.u3w-production-change.lock"
)
RUN_PATTERN = re.compile(
    r"w1a-config-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}"
)
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
KEY_ID_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}")
ENGINE_TOKEN_PATTERN = re.compile(r"[A-Za-z0-9_-]{43,128}")

REQUIRED_FALSE_FLAGS = (
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
MANAGED_KEYS = REQUIRED_FALSE_FLAGS + (
    EVENT_KEY_ID_NAME,
    EVENT_KEY_NAME,
    PREVIOUS_EVENT_KEY_ID_NAME,
    PREVIOUS_EVENT_KEY_NAME,
    SAME_BINDING_KEY_NAME,
)
MUTATION_STATE = {
    "productionFilesystemChanged": False,
    "productionConfigurationChanged": False,
}


def canonical_json(value):
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with pathlib.Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def utc_now():
    return (
        dt.datetime.now(dt.timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def parse_environment(text):
    values = {}
    for number, raw in enumerate(str(text).splitlines(), 1):
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
            raise RuntimeError(
                "invalid environment key at line {}".format(number)
            )
        if name in values:
            raise RuntimeError("duplicate environment key: " + name)
        if (
            len(value) >= 2
            and value[0] == value[-1]
            and value[0] in "\"'"
        ):
            value = value[1:-1]
        values[name] = value
    normalized_flags = {
        re.sub(r"[^a-z0-9]", "", name.lower())
        for name in REQUIRED_FALSE_FLAGS
    }
    normalized_java_overrides = {
        re.sub(r"[^a-z0-9]", "", name.lower())
        for name in (
            "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS",
            "JDK_JAVA_OPTIONS",
        )
    }
    forbidden = [
        name
        for name in values
        if (
            (
                name not in REQUIRED_FALSE_FLAGS
                and re.sub(r"[^a-z0-9]", "", name.lower())
                    in normalized_flags
            )
            or re.sub(r"[^a-z0-9]", "", name.lower()).startswith(
                "spring"
            )
            or re.sub(r"[^a-z0-9]", "", name.lower())
                in normalized_java_overrides
        )
    ]
    if forbidden:
        raise RuntimeError(
            "environment contains a high-priority or relaxed-binding override"
        )
    return values


def decode_material(value):
    value = str(value or "").strip()
    if value.startswith("base64:"):
        raw = value[7:]
        try:
            return base64.b64decode(raw, validate=True)
        except ValueError:
            return None
    if value.startswith("hex:"):
        raw = value[4:]
        if not raw or len(raw) % 2 or not re.fullmatch(
            r"[0-9a-fA-F]+", raw
        ):
            return None
        return bytes.fromhex(raw)
    if value.startswith("utf8:"):
        value = value[5:]
    return value.encode("utf-8") if value else None


def configuration_evidence(values, api2_event_material=None):
    present = [name for name in MANAGED_KEYS if name in values]
    active_id = values.get(EVENT_KEY_ID_NAME, "").strip()
    active = decode_material(values.get(EVENT_KEY_NAME, ""))
    previous_id = values.get(PREVIOUS_EVENT_KEY_ID_NAME, "").strip()
    previous = decode_material(values.get(PREVIOUS_EVENT_KEY_NAME, ""))
    binding = decode_material(values.get(SAME_BINDING_KEY_NAME, ""))
    flags_false = all(
        values.get(name, "").strip() == "false"
        for name in REQUIRED_FALSE_FLAGS
    )
    previous_complete = (
        not previous_id
        and not values.get(PREVIOUS_EVENT_KEY_NAME, "").strip()
    ) or (
        bool(previous_id)
        and bool(values.get(PREVIOUS_EVENT_KEY_NAME, "").strip())
    )
    active_valid = bool(
        KEY_ID_PATTERN.fullmatch(active_id)
        and active is not None
        and len(active) >= 32
    )
    previous_valid = bool(
        previous_complete
        and (
            not previous_id
            or (
                KEY_ID_PATTERN.fullmatch(previous_id)
                and previous is not None
                and len(previous) >= 32
                and previous_id != active_id
                and not hmac.compare_digest(previous, active)
            )
        )
    )
    binding_valid = bool(
        binding is not None
        and len(binding) >= 32
        and active is not None
        and not hmac.compare_digest(binding, active)
        and (
            previous is None
            or not hmac.compare_digest(binding, previous)
        )
    )
    api2_match = (
        api2_event_material is not None
        and active is not None
        and hmac.compare_digest(active, api2_event_material)
    )
    return {
        "managedKeyCount": len(present),
        "allManagedKeysPresent": len(present) == len(MANAGED_KEYS),
        "allDefaultOffFlagsExplicitFalse": flags_false,
        "activeEventKeyPairValid": active_valid,
        "previousEventKeyPairCompleteAndValid": previous_valid,
        "sameBindingSecretValidAndIndependent": binding_valid,
        "api2RawEventKeyMatchesU3wActiveMaterial": api2_match,
        "activeEventKeyId": active_id if active_valid else None,
        "eventKeyMaterialMinimumBytes": (
            len(active) if active_valid else 0
        ),
        "sameBindingMaterialMinimumBytes": (
            len(binding) if binding_valid else 0
        ),
        "secretsDisclosed": False,
    }


def assert_configuration_evidence(evidence):
    required_true = (
        "allManagedKeysPresent",
        "allDefaultOffFlagsExplicitFalse",
        "activeEventKeyPairValid",
        "previousEventKeyPairCompleteAndValid",
        "sameBindingSecretValidAndIndependent",
        "api2RawEventKeyMatchesU3wActiveMaterial",
    )
    if (
        evidence.get("managedKeyCount") != len(MANAGED_KEYS)
        or any(evidence.get(name) is not True for name in required_true)
        or evidence.get("secretsDisclosed") is not False
    ):
        raise RuntimeError("default-off configuration shape is invalid")


def configuration_v3_evidence(values, api2_event_material=None):
    evidence = configuration_evidence(values, api2_event_material)
    credential = str(values.get(ADMIN_ENGINE_TOKEN_NAME, "")).strip()
    credential_bytes = credential.encode("utf-8")
    comparison_material = [
        decode_material(values.get(EVENT_KEY_NAME, "")),
        decode_material(values.get(PREVIOUS_EVENT_KEY_NAME, "")),
        decode_material(values.get(SAME_BINDING_KEY_NAME, "")),
        decode_material(values.get("FBSIR_TOKEN_SECRET", "")),
        decode_material(values.get("WXFBSIR_TOKEN_SECRET", "")),
    ]
    independent = bool(
        credential_bytes
        and all(
            material is None
            or not hmac.compare_digest(credential_bytes, material)
            for material in comparison_material
        )
    )
    credential_valid = bool(ENGINE_TOKEN_PATTERN.fullmatch(credential))
    evidence.update(
        {
            "adminEngineCredentialValid": credential_valid,
            "adminEngineCredentialIndependent": independent,
            "adminEngineCredentialMinimumCharacters": (
                len(credential) if credential_valid else 0
            ),
        }
    )
    return evidence


def assert_configuration_v3_evidence(evidence):
    assert_configuration_evidence(evidence)
    if (
        evidence.get("adminEngineCredentialValid") is not True
        or evidence.get("adminEngineCredentialIndependent") is not True
        or int(evidence.get("adminEngineCredentialMinimumCharacters") or 0)
        < 43
        or evidence.get("secretsDisclosed") is not False
    ):
        raise RuntimeError(
            "default-off configuration engine credential shape is invalid"
        )


def reconcile_admin_engine_credential(
    original,
    credential,
    api2_event_material,
):
    values = parse_environment(original)
    assert_configuration_evidence(
        configuration_evidence(values, api2_event_material)
    )
    existing = str(values.get(ADMIN_ENGINE_TOKEN_NAME, "")).strip()
    if existing:
        evidence = configuration_v3_evidence(
            values, api2_event_material
        )
        assert_configuration_v3_evidence(evidence)
        return original, "PRESERVED_EXISTING"
    if (
        not isinstance(credential, str)
        or not ENGINE_TOKEN_PATTERN.fullmatch(credential)
    ):
        raise RuntimeError("generated admin Engine credential shape is invalid")
    prefix = original
    if prefix and not prefix.endswith("\n"):
        prefix += "\n"
    rendered = prefix + "{}={}\n".format(
        ADMIN_ENGINE_TOKEN_NAME, credential
    )
    reconciled = parse_environment(rendered)
    for name in MANAGED_KEYS:
        if reconciled.get(name) != values.get(name):
            raise RuntimeError(
                "default-off configuration changed during reconciliation"
            )
    assert_configuration_v3_evidence(
        configuration_v3_evidence(
            reconciled, api2_event_material
        )
    )
    return rendered, "CREATED_BY_RUN"


def validate_exact_existing_admin_engine_delta(
    current,
    predecessor_environment_sha,
    api2_event_material,
):
    values = parse_environment(current)
    credential = str(values.get(ADMIN_ENGINE_TOKEN_NAME, "")).strip()
    evidence = configuration_v3_evidence(values, api2_event_material)
    assert_configuration_v3_evidence(evidence)
    suffix = "{}={}\n".format(
        ADMIN_ENGINE_TOKEN_NAME,
        credential,
    )
    if not current.endswith(suffix):
        raise RuntimeError(
            "existing admin Engine credential is not one canonical "
            "append-only environment delta"
        )
    predecessor = current[: -len(suffix)]
    if sha256_bytes(predecessor.encode("utf-8")) != predecessor_environment_sha:
        raise RuntimeError(
            "existing admin Engine credential predecessor anchor drifted"
        )
    predecessor_values = parse_environment(predecessor)
    predecessor_evidence = configuration_evidence(
        predecessor_values,
        api2_event_material,
    )
    assert_configuration_evidence(predecessor_evidence)
    expected, state = reconcile_admin_engine_credential(
        predecessor,
        credential,
        api2_event_material,
    )
    if state != "CREATED_BY_RUN" or expected != current:
        raise RuntimeError(
            "existing admin Engine credential delta is not reproducible"
        )
    return predecessor, predecessor_evidence


def render_configuration(
    original,
    event_key_id,
    event_material,
    same_binding_material,
):
    values = parse_environment(original)
    present = [name for name in MANAGED_KEYS if name in values]
    if present and len(present) != len(MANAGED_KEYS):
        raise RuntimeError(
            "partial existing W1A configuration requires manual review"
        )
    if len(present) == len(MANAGED_KEYS):
        return original
    if (
        not KEY_ID_PATTERN.fullmatch(str(event_key_id))
        or not isinstance(event_material, bytes)
        or not isinstance(same_binding_material, bytes)
        or len(event_material) < 32
        or len(same_binding_material) < 32
        or hmac.compare_digest(event_material, same_binding_material)
    ):
        raise RuntimeError("generated key material shape is invalid")
    block = [
        "# W1A Independent Board default-off configuration; managed by Codex.",
    ]
    block.extend("{}=false".format(name) for name in REQUIRED_FALSE_FLAGS)
    block.extend(
        [
            "{}={}".format(EVENT_KEY_ID_NAME, event_key_id),
            "{}=base64:{}".format(
                EVENT_KEY_NAME,
                base64.b64encode(event_material).decode("ascii"),
            ),
            "{}=".format(PREVIOUS_EVENT_KEY_ID_NAME),
            "{}=".format(PREVIOUS_EVENT_KEY_NAME),
            "{}=base64:{}".format(
                SAME_BINDING_KEY_NAME,
                base64.b64encode(same_binding_material).decode("ascii"),
            ),
        ]
    )
    prefix = original
    if prefix and not prefix.endswith("\n"):
        prefix += "\n"
    if prefix and not prefix.endswith("\n\n"):
        prefix += "\n"
    return prefix + "\n".join(block) + "\n"


def validate_regular_file(path, mode=0o600):
    path = pathlib.Path(path)
    allowed_modes = (
        (mode,)
        if isinstance(mode, int)
        else tuple(mode)
    )
    if path.is_symlink() or not path.is_file():
        raise RuntimeError("configuration artifact type is invalid")
    status = path.stat()
    if (
        status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 not in allowed_modes
        or status.st_nlink != 1
    ):
        raise RuntimeError("configuration artifact custody is invalid")
    return path


def open_host_change_lock():
    current = pathlib.Path(HOST_CHANGE_LOCK.anchor)
    for part in HOST_CHANGE_LOCK.parent.parts[1:]:
        current = current / part
        status = current.lstat()
        if (
            stat.S_ISLNK(status.st_mode)
            or not stat.S_ISDIR(status.st_mode)
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_mode & 0o022
        ):
            raise RuntimeError("production change lock ancestry is untrusted")
    flags = os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(HOST_CHANGE_LOCK, flags, 0o600)
    status = os.fstat(descriptor)
    if (
        not stat.S_ISREG(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink != 1
    ):
        os.close(descriptor)
        raise RuntimeError("production change lock custody is invalid")
    os.fchmod(descriptor, 0o600)
    return descriptor


def acquire_host_change_lock(descriptor, timeout_seconds=30):
    deadline = time.monotonic() + timeout_seconds
    operation = fcntl.LOCK_EX | getattr(fcntl, "LOCK_NB", 4)
    while True:
        try:
            fcntl.flock(descriptor, operation)
            return
        except BlockingIOError as error:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise RuntimeError(
                    "production change lock acquisition timed out"
                ) from error
            time.sleep(min(0.1, remaining))


def fsync_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def atomic_bytes(path, payload, mode=0o600):
    path = pathlib.Path(path)
    partial = path.with_name(
        ".{}.{}-{}.partial".format(
            path.name, os.getpid(), secrets.token_hex(6)
        )
    )
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
    os.chmod(partial, mode)
    os.replace(partial, path)
    fsync_directory(path.parent)


def service_snapshot():
    output = subprocess.run(
        [
            "systemctl",
            "show",
            SERVICE_UNIT,
            (
                "--property=ActiveState,MainPID,ExecStart,InvocationID,"
                "ExecMainStartTimestampMonotonic,NRestarts"
            ),
        ],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=15,
    ).stdout
    values = {}
    for line in output.splitlines():
        name, _, value = line.partition("=")
        values[name] = value
    jar_match = re.search(r"(/[A-Za-z0-9._/-]+\.jar)", values["ExecStart"])
    if (
        values.get("ActiveState") != "active"
        or not values.get("MainPID", "").isdigit()
        or int(values["MainPID"]) <= 0
        or not jar_match
        or not re.fullmatch(
            r"[0-9a-f]{32}", values.get("InvocationID", "").lower()
        )
        or not values.get(
            "ExecMainStartTimestampMonotonic", ""
        ).isdigit()
        or not values.get("NRestarts", "").isdigit()
    ):
        raise RuntimeError("active fbsir service identity is invalid")
    jar = validate_regular_file(
        jar_match.group(1),
        mode=(0o600, 0o640, 0o644),
    )
    return {
        "activeState": values["ActiveState"],
        "mainPid": int(values["MainPID"]),
        "jarPath": str(jar),
        "jarSha256": sha256_file(jar),
        "invocationId": values["InvocationID"].lower(),
        "execMainStartTimestampMonotonic": int(
            values["ExecMainStartTimestampMonotonic"]
        ),
        "nRestarts": int(values["NRestarts"]),
    }


def validate_approval(args, now=None, allow_expired=False):
    if args.approval_sha == "0" * 64 or not args.approval_json_base64:
        raise RuntimeError("mutating configuration requires approval")
    try:
        raw = base64.b64decode(args.approval_json_base64, validate=True)
        approval = json.loads(raw.decode("utf-8-sig"))
    except (ValueError, UnicodeError, json.JSONDecodeError) as error:
        raise RuntimeError("approval receipt cannot be decoded") from error
    expected_fields = {
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
        "expectedEnvironmentSha256",
        "expectedApi2EventKeyState",
        "runnerSha256",
        "workerSha256",
    }
    recovery = args.mode == "Recover"
    reconcile = args.mode == "Reconcile"
    if recovery:
        expected_fields.update(
            {
                "expectedConfiguredEnvironmentSha256",
                "originalApprovalReceiptSha256",
            }
        )
    if reconcile:
        expected_fields.add(
            "expectedPredecessorConfigurationReceiptSha256"
        )
    if set(approval) != expected_fields:
        raise RuntimeError("approval receipt has missing or unknown fields")
    try:
        approved_at = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        expires_at = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
    except (TypeError, ValueError) as error:
        raise RuntimeError("approval receipt time is invalid") from error
    current = now or dt.datetime.now(dt.timezone.utc)
    if (
        sha256_bytes(raw) != args.approval_sha
        or approval["schema"]
        != "fbsir.u3wProductionChangeApprovalReceipt.v1"
        or approval["action"]
        != (
            "RECOVER_W1A_DEFAULT_OFF_CONFIGURATION_ANCHOR"
            if recovery
            else (
                "ADOPT_EXISTING_ADMIN_ENGINE_CREDENTIAL_DELTA"
                if reconcile
                else "CONFIGURE_W1A_DEFAULT_OFF_CRYPTO_CUSTODY"
            )
        )
        or approval["targetHost"] != TARGET_HOST
        or approval["runId"] != args.run_id
        or approval["sourceCommit"] != args.source_commit
        or approval["expectedEnvironmentSha256"]
        != args.expected_environment_sha
        or approval["expectedApi2EventKeyState"]
        != ("PRESENT_ANCHORED" if reconcile else "ABSENT")
        or approval["runnerSha256"] != args.runner_sha
        or approval["workerSha256"] != args.worker_sha
        or approval["authorizedBy"] != "workspace-user"
        or approval["concurrentDdlProhibited"] is not True
        or approval["productionFilesystemWrite"] is not True
        or approval["productionDatabaseWrite"] is not False
        or approval["productionServiceChange"] is not False
        or approval["officialExpertsPackageChange"] is not False
        or approved_at.tzinfo is None
        or expires_at.tzinfo is None
        or approved_at > current
        or expires_at <= approved_at
        or (not allow_expired and expires_at <= current)
        or expires_at - approved_at > dt.timedelta(hours=24)
        or (
            recovery
            and (
                approval["expectedConfiguredEnvironmentSha256"]
                != args.expected_configured_environment_sha
                or approval["originalApprovalReceiptSha256"]
                != args.original_approval_sha
            )
        )
        or (
            reconcile
            and (
                args.expected_predecessor_configuration_receipt_sha
                == "0" * 64
                or approval[
                    "expectedPredecessorConfigurationReceiptSha256"
                ]
                != args.expected_predecessor_configuration_receipt_sha
            )
        )
    ):
        raise RuntimeError("approval receipt identity or scope is invalid")
    return approval


def approval_is_current(approval, now=None):
    try:
        approved_at = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        expires_at = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
    except (KeyError, TypeError, ValueError) as error:
        raise RuntimeError("approval receipt time is invalid") from error
    current = now or dt.datetime.now(dt.timezone.utc)
    return bool(
        approved_at.tzinfo is not None
        and expires_at.tzinfo is not None
        and current.tzinfo is not None
        and approved_at <= current < expires_at
    )


def expired_reconciliation_finalization_authorized(
    approval,
    journal,
    current_environment_sha,
):
    if approval_is_current(approval):
        return True
    try:
        approved_at = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        expires_at = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
        observed_at = dt.datetime.fromisoformat(
            journal["receiptObservedAt"].replace("Z", "+00:00")
        )
    except (KeyError, TypeError, ValueError) as error:
        raise RuntimeError(
            "expired reconciliation recovery time is invalid"
        ) from error
    credential_existed = journal.get(
        "adminEngineCredentialExistedBefore"
    )
    before_sha = journal.get("environmentBeforeSha256")
    return bool(
        approved_at <= observed_at < expires_at
        and isinstance(credential_existed, bool)
        and (
            current_environment_sha == before_sha
            if credential_existed
            else current_environment_sha != before_sha
        )
    )


def safe_directory(path, mode=0o700):
    path = pathlib.Path(path)
    if not path.is_absolute():
        raise RuntimeError("configuration directory must be absolute")
    current = pathlib.Path(path.anchor)
    for part in path.parts[1:]:
        current = current / part
        if current.exists() or current.is_symlink():
            status = current.lstat()
            if (
                stat.S_ISLNK(status.st_mode)
                or not stat.S_ISDIR(status.st_mode)
                or status.st_uid != 0
                or status.st_gid != 0
                or (
                    current != path
                    and status.st_mode & 0o022
                )
            ):
                raise RuntimeError(
                    "configuration directory ancestry is untrusted"
                )
        else:
            os.mkdir(current, mode if current == path else 0o700)
    status = path.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
    ):
        raise RuntimeError("configuration directory custody is invalid")
    os.chmod(path, mode)
    return path


def read_or_create_api2_event_key():
    safe_directory(API2_EVENT_KEY_PATH.parent)
    if API2_EVENT_KEY_PATH.exists() or API2_EVENT_KEY_PATH.is_symlink():
        validate_regular_file(API2_EVENT_KEY_PATH)
        material = API2_EVENT_KEY_PATH.read_bytes()
        if len(material) < 32:
            raise RuntimeError("API2 event key material is too short")
        return material, False
    material = secrets.token_bytes(32)
    descriptor = os.open(
        API2_EVENT_KEY_PATH,
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | getattr(os, "O_NOFOLLOW", 0),
        0o600,
    )
    try:
        with os.fdopen(descriptor, "wb", closefd=False) as handle:
            handle.write(material)
            handle.flush()
            os.fsync(handle.fileno())
    finally:
        os.close(descriptor)
    os.chmod(API2_EVENT_KEY_PATH, 0o600)
    fsync_directory(API2_EVENT_KEY_PATH.parent)
    validate_regular_file(API2_EVENT_KEY_PATH)
    return material, True


def safe_run_directory(run_id):
    safe_directory(CONFIG_ROOT)
    run_directory = CONFIG_ROOT / run_id
    safe_directory(run_directory)
    if run_directory.resolve().parent != CONFIG_ROOT:
        raise RuntimeError("configuration run directory escapes root")
    return run_directory


def publish_latest(run_directory):
    parent = CONFIG_LATEST.parent
    safe_directory(parent)
    temporary = parent / (
        ".latest-{}-{}".format(os.getpid(), secrets.token_hex(6))
    )
    os.symlink(str(run_directory), temporary)
    os.replace(temporary, CONFIG_LATEST)
    fsync_directory(parent)


def validate_receipt(payload):
    base_fields = {
        "schema",
        "mode",
        "state",
        "runId",
        "sourceCommit",
        "targetHost",
        "serviceUnit",
        "environmentPath",
        "environmentBackupPath",
        "environmentBackupSha256",
        "environmentBeforeSha256",
        "environmentAfterSha256",
        "api2EventKeyPath",
        "api2EventKeyProvisioningState",
        "stagedKeyMaterialMatched",
        "configurationEvidence",
        "environmentCustodySecure",
        "api2EventKeyCustodySecure",
        "environmentBackupCustodySecure",
        "serviceSnapshotBefore",
        "serviceSnapshotAfter",
        "approvalReceiptSha256",
        "runnerSha256",
        "workerSha256",
        "serviceRestarted",
        "productionDatabaseChanged",
        "productionFilesystemChanged",
        "productionConfigurationChanged",
        "productionServiceChanged",
        "configurationLoaded",
        "officialExpertsPackageChanged",
        "secretsDisclosed",
        "observedAt",
    }
    if not isinstance(payload, dict):
        raise RuntimeError("configuration receipt fields are invalid")
    schema = payload.get("schema")
    expected_fields = set(base_fields)
    if schema == "fbsir.u3wDefaultOffConfigurationReceipt.v3":
        expected_fields.update(
            {
                "predecessorConfigurationReceiptSha256",
                "adminEngineCredentialProvisioningState",
                "engineCounterpartClosureClaimed",
            }
        )
    if set(payload) != expected_fields:
        raise RuntimeError("configuration receipt fields are invalid")
    api2_state = (
        "REUSED_FROM_PREDECESSOR_RECEIPT"
        if schema == "fbsir.u3wDefaultOffConfigurationReceipt.v3"
        else "CREATED_BY_RUN"
    )
    credential_state = payload.get(
        "adminEngineCredentialProvisioningState"
    )
    configuration_changed = (
        credential_state == "CREATED_BY_RUN"
        if schema == "fbsir.u3wDefaultOffConfigurationReceipt.v3"
        else True
    )
    if (
        schema
        not in {
            "fbsir.u3wDefaultOffConfigurationReceipt.v2",
            "fbsir.u3wDefaultOffConfigurationReceipt.v3",
        }
        or payload["mode"] != "Apply"
        or payload["state"] != "CONFIGURED_NOT_LOADED"
        or payload["targetHost"] != TARGET_HOST
        or payload["serviceUnit"] != SERVICE_UNIT
        or payload["environmentPath"] != str(ENV_PATH)
        or payload["api2EventKeyPath"] != str(API2_EVENT_KEY_PATH)
        or payload["api2EventKeyProvisioningState"] != api2_state
        or payload["environmentBackupSha256"]
        != payload["environmentBeforeSha256"]
        or not RUN_PATTERN.fullmatch(str(payload["runId"]))
        or not COMMIT_PATTERN.fullmatch(str(payload["sourceCommit"]))
        or any(
            not SHA_PATTERN.fullmatch(str(payload.get(name, "")))
            for name in (
                "environmentBeforeSha256",
                "environmentAfterSha256",
                "environmentBackupSha256",
                "approvalReceiptSha256",
                "runnerSha256",
                "workerSha256",
            )
        )
        or payload["serviceRestarted"] is not False
        or payload["stagedKeyMaterialMatched"] is not True
        or payload["environmentBackupCustodySecure"] is not True
        or payload["productionDatabaseChanged"] is not False
        or payload["productionFilesystemChanged"] is not True
        or payload["productionConfigurationChanged"]
        is not configuration_changed
        or payload["productionServiceChanged"] is not False
        or payload["configurationLoaded"] is not False
        or payload["officialExpertsPackageChanged"] is not False
        or payload["secretsDisclosed"] is not False
    ):
        raise RuntimeError("configuration receipt identity is invalid")
    if schema == "fbsir.u3wDefaultOffConfigurationReceipt.v3":
        if (
            not SHA_PATTERN.fullmatch(
                str(
                    payload.get(
                        "predecessorConfigurationReceiptSha256", ""
                    )
                )
            )
            or payload["predecessorConfigurationReceiptSha256"]
            == "0" * 64
            or credential_state != "ADOPTED_EXISTING_EXACT_DELTA"
            or payload.get("engineCounterpartClosureClaimed") is not False
        ):
            raise RuntimeError(
                "configuration reconciliation receipt identity is invalid"
            )
        assert_configuration_v3_evidence(
            payload["configurationEvidence"]
        )
    else:
        assert_configuration_evidence(payload["configurationEvidence"])
    if payload["serviceSnapshotBefore"] != payload["serviceSnapshotAfter"]:
        raise RuntimeError("service changed while preparing configuration")
    try:
        observed_at = dt.datetime.fromisoformat(
            payload["observedAt"].replace("Z", "+00:00")
        )
    except (AttributeError, ValueError) as error:
        raise RuntimeError("configuration receipt time is invalid") from error
    if observed_at.tzinfo is None:
        raise RuntimeError("configuration receipt time is naive")
    backup_path = pathlib.Path(payload["environmentBackupPath"])
    expected_backup_path = pathlib.Path(
        ENV_BACKUP_ROOT,
        "{}-{}.env".format(
            payload["runId"], payload["environmentBeforeSha256"][:16]
        ),
    )
    if backup_path != expected_backup_path:
        raise RuntimeError("configuration backup path is invalid")
    return payload


def existing_receipt(
    run_directory,
    args,
    expected_original_approval_sha=None,
):
    path = run_directory / "configuration-receipt.json"
    if not path.exists() and not path.is_symlink():
        return None
    validate_regular_file(path)
    payload = json.loads(path.read_text(encoding="utf-8"))
    validate_receipt(payload)
    if (
        payload["runId"] != args.run_id
        or payload["sourceCommit"] != args.source_commit
        or payload["approvalReceiptSha256"]
        != (expected_original_approval_sha or args.approval_sha)
        or payload["runnerSha256"] != args.runner_sha
        or payload["workerSha256"] != args.worker_sha
        or sha256_file(ENV_PATH) != payload["environmentAfterSha256"]
    ):
        raise RuntimeError("existing configuration receipt identity changed")
    backup_path = validate_regular_file(payload["environmentBackupPath"])
    if (
        sha256_file(backup_path) != payload["environmentBackupSha256"]
        or payload["environmentBackupSha256"]
        != payload["environmentBeforeSha256"]
    ):
        raise RuntimeError("existing configuration backup anchor changed")
    event_material = validate_regular_file(API2_EVENT_KEY_PATH).read_bytes()
    values = parse_environment(ENV_PATH.read_text(encoding="utf-8"))
    evidence = (
        configuration_v3_evidence(values, event_material)
        if payload["schema"]
        == "fbsir.u3wDefaultOffConfigurationReceipt.v3"
        else configuration_evidence(values, event_material)
    )
    if payload["schema"] == "fbsir.u3wDefaultOffConfigurationReceipt.v3":
        assert_configuration_v3_evidence(evidence)
    else:
        assert_configuration_evidence(evidence)
    if evidence != payload["configurationEvidence"]:
        raise RuntimeError("existing configuration live readback changed")
    publish_latest(run_directory)
    return payload, path


def predecessor_configuration_receipt(args, require_live_environment=True):
    if not CONFIG_LATEST.is_symlink():
        raise RuntimeError(
            "predecessor configuration latest pointer is invalid"
        )
    pointer_status = CONFIG_LATEST.lstat()
    if (
        pointer_status.st_uid != 0
        or pointer_status.st_gid != 0
    ):
        raise RuntimeError(
            "predecessor configuration latest pointer custody is invalid"
        )
    target = CONFIG_LATEST.resolve(strict=True)
    expected_root = CONFIG_ROOT.resolve(strict=True)
    if (
        target.parent != expected_root
        or not RUN_PATTERN.fullmatch(target.name)
        or target.is_symlink()
        or not target.is_dir()
    ):
        raise RuntimeError(
            "predecessor configuration latest target is invalid"
        )
    target_status = target.stat()
    if (
        target_status.st_uid != 0
        or target_status.st_gid != 0
        or target_status.st_mode & 0o022
    ):
        raise RuntimeError(
            "predecessor configuration latest target custody is invalid"
        )
    receipt_path = validate_regular_file(
        target / "configuration-receipt.json"
    )
    if (
        sha256_file(receipt_path)
        != args.expected_predecessor_configuration_receipt_sha
    ):
        raise RuntimeError(
            "predecessor configuration receipt digest drifted"
        )
    payload = json.loads(receipt_path.read_text(encoding="utf-8"))
    validate_receipt(payload)
    if payload["schema"] != "fbsir.u3wDefaultOffConfigurationReceipt.v2":
        raise RuntimeError(
            "predecessor configuration is not eligible for reconciliation"
        )
    event_material = validate_regular_file(
        API2_EVENT_KEY_PATH
    ).read_bytes()
    if require_live_environment:
        if sha256_file(ENV_PATH) != payload["environmentAfterSha256"]:
            raise RuntimeError(
                "predecessor configuration live environment drifted"
            )
        evidence = configuration_evidence(
            parse_environment(ENV_PATH.read_text(encoding="utf-8")),
            event_material,
        )
        assert_configuration_evidence(evidence)
        if evidence != payload["configurationEvidence"]:
            raise RuntimeError(
                "predecessor configuration live evidence drifted"
            )
    return payload, receipt_path, event_material


def load_or_create_reconciliation_journal(
    run_directory,
    args,
    environment_before_sha,
    service_before=None,
    admin_engine_credential_existed_before=False,
):
    journal_path = run_directory / "configuration-journal.json"
    backup_path = pathlib.Path(
        ENV_BACKUP_ROOT,
        "{}-{}.env".format(
            args.run_id, args.expected_environment_sha[:16]
        ),
    )
    expected_fields = {
        "schema",
        "runId",
        "sourceCommit",
        "targetHost",
        "serviceUnit",
        "environmentPath",
        "environmentBeforeSha256",
        "environmentBackupPath",
        "api2EventKeyPath",
        "api2EventKeyExistedBefore",
        "predecessorConfigurationReceiptSha256",
        "adminEngineCredentialExistedBefore",
        "receiptObservedAt",
        "approvalReceiptSha256",
        "runnerSha256",
        "workerSha256",
        "serviceSnapshotBefore",
        "serviceRestartAuthorized",
        "productionDatabaseWriteAuthorized",
        "officialExpertsPackageChangeAuthorized",
    }
    if journal_path.exists() or journal_path.is_symlink():
        validate_regular_file(journal_path)
        journal = json.loads(journal_path.read_text(encoding="utf-8"))
        if (
            set(journal) != expected_fields
            or journal["schema"]
            != "fbsir.u3wDefaultOffConfigurationReconciliationJournal.v1"
            or journal["runId"] != args.run_id
            or journal["sourceCommit"] != args.source_commit
            or journal["targetHost"] != TARGET_HOST
            or journal["serviceUnit"] != SERVICE_UNIT
            or journal["environmentPath"] != str(ENV_PATH)
            or journal["environmentBeforeSha256"]
            != args.expected_environment_sha
            or journal["environmentBackupPath"] != str(backup_path)
            or journal["api2EventKeyPath"] != str(API2_EVENT_KEY_PATH)
            or journal["api2EventKeyExistedBefore"] is not True
            or journal[
                "predecessorConfigurationReceiptSha256"
            ]
            != args.expected_predecessor_configuration_receipt_sha
            or not isinstance(
                journal["adminEngineCredentialExistedBefore"],
                bool,
            )
            or journal["approvalReceiptSha256"] != args.approval_sha
            or journal["runnerSha256"] != args.runner_sha
            or journal["workerSha256"] != args.worker_sha
            or journal["serviceRestartAuthorized"] is not False
            or journal["productionDatabaseWriteAuthorized"] is not False
            or journal[
                "officialExpertsPackageChangeAuthorized"
            ] is not False
        ):
            raise RuntimeError(
                "configuration reconciliation journal changed"
            )
        try:
            observed_at = dt.datetime.fromisoformat(
                journal["receiptObservedAt"].replace("Z", "+00:00")
            )
        except (AttributeError, ValueError) as error:
            raise RuntimeError(
                "configuration reconciliation journal time is invalid"
            ) from error
        if observed_at.tzinfo is None:
            raise RuntimeError(
                "configuration reconciliation journal time is naive"
            )
        return journal, journal_path
    if environment_before_sha != args.expected_environment_sha:
        raise RuntimeError(
            "environment changed without a reconciliation journal"
        )
    if not isinstance(admin_engine_credential_existed_before, bool):
        raise RuntimeError(
            "admin Engine credential predecessor state is invalid"
        )
    if service_before is None:
        raise RuntimeError(
            "service snapshot is required for reconciliation"
        )
    journal = {
        "schema":
            "fbsir.u3wDefaultOffConfigurationReconciliationJournal.v1",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "environmentPath": str(ENV_PATH),
        "environmentBeforeSha256": environment_before_sha,
        "environmentBackupPath": str(backup_path),
        "api2EventKeyPath": str(API2_EVENT_KEY_PATH),
        "api2EventKeyExistedBefore": True,
        "predecessorConfigurationReceiptSha256":
            args.expected_predecessor_configuration_receipt_sha,
        "adminEngineCredentialExistedBefore":
            admin_engine_credential_existed_before,
        "receiptObservedAt": utc_now(),
        "approvalReceiptSha256": args.approval_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "serviceSnapshotBefore": service_before,
        "serviceRestartAuthorized": False,
        "productionDatabaseWriteAuthorized": False,
        "officialExpertsPackageChangeAuthorized": False,
    }
    atomic_bytes(
        journal_path, canonical_json(journal).encode("utf-8")
    )
    return journal, journal_path


def load_or_create_journal(
    run_directory,
    args,
    environment_before_sha,
    service_before=None,
    expected_original_approval_sha=None,
):
    journal_path = run_directory / "configuration-journal.json"
    expected_backup_path = pathlib.Path(
        ENV_BACKUP_ROOT,
        "{}-{}.env".format(
            args.run_id, args.expected_environment_sha[:16]
        ),
    )
    if journal_path.exists() or journal_path.is_symlink():
        validate_regular_file(journal_path)
        journal = json.loads(journal_path.read_text(encoding="utf-8"))
        expected_fields = {
            "schema",
            "runId",
            "sourceCommit",
            "targetHost",
            "serviceUnit",
            "environmentPath",
            "environmentBeforeSha256",
            "environmentBackupPath",
            "api2EventKeyPath",
            "api2EventKeyExistedBefore",
            "receiptObservedAt",
            "approvalReceiptSha256",
            "runnerSha256",
            "workerSha256",
            "serviceSnapshotBefore",
            "serviceRestartAuthorized",
            "productionDatabaseWriteAuthorized",
            "officialExpertsPackageChangeAuthorized",
        }
        if (
            set(journal) != expected_fields
            or journal["schema"]
            != "fbsir.u3wDefaultOffConfigurationJournal.v1"
            or journal["runId"] != args.run_id
            or journal["sourceCommit"] != args.source_commit
            or journal["targetHost"] != TARGET_HOST
            or journal["serviceUnit"] != SERVICE_UNIT
            or journal["environmentPath"] != str(ENV_PATH)
            or journal["environmentBeforeSha256"]
            != args.expected_environment_sha
            or journal["environmentBackupPath"]
            != str(expected_backup_path)
            or journal["api2EventKeyPath"] != str(API2_EVENT_KEY_PATH)
            or journal["api2EventKeyExistedBefore"] is not False
            or journal["approvalReceiptSha256"]
            != (expected_original_approval_sha or args.approval_sha)
            or journal["runnerSha256"] != args.runner_sha
            or journal["workerSha256"] != args.worker_sha
            or journal["serviceRestartAuthorized"] is not False
            or journal["productionDatabaseWriteAuthorized"] is not False
            or journal[
                "officialExpertsPackageChangeAuthorized"
            ] is not False
        ):
            raise RuntimeError("configuration recovery journal changed")
        try:
            observed_at = dt.datetime.fromisoformat(
                journal["receiptObservedAt"].replace("Z", "+00:00")
            )
        except (AttributeError, ValueError) as error:
            raise RuntimeError(
                "configuration journal receipt time is invalid"
            ) from error
        if observed_at.tzinfo is None:
            raise RuntimeError("configuration journal receipt time is naive")
        return journal, journal_path
    if environment_before_sha != args.expected_environment_sha:
        raise RuntimeError(
            "environment changed without a same-run recovery journal"
        )
    if service_before is None:
        raise RuntimeError("service snapshot is required for a new journal")
    journal = {
        "schema": "fbsir.u3wDefaultOffConfigurationJournal.v1",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "environmentPath": str(ENV_PATH),
        "environmentBeforeSha256": environment_before_sha,
        "environmentBackupPath": str(expected_backup_path),
        "api2EventKeyPath": str(API2_EVENT_KEY_PATH),
        "api2EventKeyExistedBefore": False,
        "receiptObservedAt": utc_now(),
        "approvalReceiptSha256": args.approval_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "serviceSnapshotBefore": service_before,
        "serviceRestartAuthorized": False,
        "productionDatabaseWriteAuthorized": False,
        "officialExpertsPackageChangeAuthorized": False,
    }
    atomic_bytes(
        journal_path, canonical_json(journal).encode("utf-8")
    )
    return journal, journal_path


def plan(args):
    validate_regular_file(ENV_PATH)
    original = ENV_PATH.read_bytes()
    original_text = original.decode("utf-8")
    values = parse_environment(original_text)
    present = [name for name in MANAGED_KEYS if name in values]
    key_exists = API2_EVENT_KEY_PATH.exists()
    key_custody = False
    if key_exists:
        validate_regular_file(API2_EVENT_KEY_PATH)
        key_custody = True
    recovery_candidate = None
    journal_path = CONFIG_ROOT / args.run_id / "configuration-journal.json"
    if journal_path.exists() or journal_path.is_symlink():
        validate_regular_file(journal_path)
        journal = json.loads(journal_path.read_text(encoding="utf-8"))
        if (
            journal.get("schema")
            != "fbsir.u3wDefaultOffConfigurationJournal.v1"
            or journal.get("runId") != args.run_id
            or journal.get("sourceCommit") != args.source_commit
        ):
            raise RuntimeError("configuration recovery journal identity changed")
        recovery_candidate = {
            "journalSha256": sha256_file(journal_path),
            "originalApprovalReceiptSha256":
                journal.get("approvalReceiptSha256"),
            "environmentBeforeSha256":
                journal.get("environmentBeforeSha256"),
            "configuredEnvironmentSha256": sha256_bytes(original),
            "serviceSnapshotBefore": journal.get("serviceSnapshotBefore"),
            "secretsDisclosed": False,
        }
    reconciliation_plan = (
        args.expected_predecessor_configuration_receipt_sha
        != "0" * 64
    )
    reconciliation_evidence = None
    if reconciliation_plan:
        predecessor, predecessor_path, event_material = (
            predecessor_configuration_receipt(
                args,
                require_live_environment=False,
            )
        )
        _, predecessor_evidence = (
            validate_exact_existing_admin_engine_delta(
                original_text,
                predecessor["environmentAfterSha256"],
                event_material,
            )
        )
        if predecessor_evidence != predecessor["configurationEvidence"]:
            raise RuntimeError(
                "existing admin Engine credential predecessor evidence "
                "changed"
            )
        reconciliation_evidence = {
            "planPurpose":
                "RECONCILE_EXISTING_ADMIN_ENGINE_CREDENTIAL_DELTA",
            "adminEngineCredentialPresent": True,
            "exactExistingAdminEngineDeltaValid": True,
            "predecessorConfigurationReceiptPath":
                str(predecessor_path),
            "predecessorConfigurationReceiptSha256":
                args.expected_predecessor_configuration_receipt_sha,
            "engineCounterpartClosureClaimed": False,
        }
    result = {
        "schema": (
            "fbsir.u3wDefaultOffConfigurationPlan.v2"
            if reconciliation_plan
            else "fbsir.u3wDefaultOffConfigurationPlan.v1"
        ),
        "mode": "Plan",
        "state": "PLANNED",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "environmentPath": str(ENV_PATH),
        "environmentSha256": sha256_bytes(original),
        "managedKeyCount": len(present),
        "partialManagedConfiguration": bool(
            present and len(present) != len(MANAGED_KEYS)
        ),
        "api2EventKeyPath": str(API2_EVENT_KEY_PATH),
        "api2EventKeyAlreadyProvisioned": key_exists,
        "api2EventKeyCustodySecure": key_custody,
        "recoveryCandidate": recovery_candidate,
        "serviceRestartAuthorized": False,
        "productionFilesystemChanged": False,
        "productionConfigurationChanged": False,
        "productionBusinessStateChanged": False,
        "secretsDisclosed": False,
    }
    if reconciliation_evidence is not None:
        result.update(reconciliation_evidence)
    return result


def verify(args):
    validate_regular_file(ENV_PATH)
    event_material = validate_regular_file(API2_EVENT_KEY_PATH).read_bytes()
    values = parse_environment(ENV_PATH.read_text(encoding="utf-8"))
    engine_credential_present = bool(
        str(values.get(ADMIN_ENGINE_TOKEN_NAME, "")).strip()
    )
    evidence = (
        configuration_v3_evidence(values, event_material)
        if engine_credential_present
        else configuration_evidence(values, event_material)
    )
    if engine_credential_present:
        assert_configuration_v3_evidence(evidence)
    else:
        assert_configuration_evidence(evidence)
    return {
        "schema": (
            "fbsir.u3wDefaultOffConfigurationVerify.v2"
            if engine_credential_present
            else "fbsir.u3wDefaultOffConfigurationVerify.v1"
        ),
        "mode": "Verify",
        "state": "CONFIGURED_NOT_LOADED",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "configurationEvidence": evidence,
        "environmentCustodySecure": True,
        "api2EventKeyCustodySecure": True,
        "productionFilesystemChanged": False,
        "productionConfigurationChanged": False,
        "productionBusinessStateChanged": False,
        "serviceRestarted": False,
        "secretsDisclosed": False,
    }


def build_configuration_receipt(
    args,
    journal,
    backup_path,
    after_sha,
    evidence,
    after_service,
):
    payload = {
        "schema": "fbsir.u3wDefaultOffConfigurationReceipt.v2",
        "mode": "Apply",
        "state": "CONFIGURED_NOT_LOADED",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "environmentPath": str(ENV_PATH),
        "environmentBackupPath": str(backup_path),
        "environmentBackupSha256": sha256_file(backup_path),
        "environmentBeforeSha256": journal["environmentBeforeSha256"],
        "environmentAfterSha256": after_sha,
        "api2EventKeyPath": str(API2_EVENT_KEY_PATH),
        "api2EventKeyProvisioningState": "CREATED_BY_RUN",
        "stagedKeyMaterialMatched": True,
        "configurationEvidence": evidence,
        "environmentCustodySecure": True,
        "api2EventKeyCustodySecure": True,
        "environmentBackupCustodySecure": True,
        "serviceSnapshotBefore": journal["serviceSnapshotBefore"],
        "serviceSnapshotAfter": after_service,
        "approvalReceiptSha256": journal["approvalReceiptSha256"],
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "serviceRestarted": False,
        "productionDatabaseChanged": False,
        "productionFilesystemChanged": True,
        "productionConfigurationChanged": True,
        "productionServiceChanged": False,
        "configurationLoaded": False,
        "officialExpertsPackageChanged": False,
        "secretsDisclosed": False,
        "observedAt": journal["receiptObservedAt"],
    }
    validate_receipt(payload)
    return payload


def build_reconciliation_receipt(
    args,
    journal,
    backup_path,
    after_sha,
    evidence,
    after_service,
    credential_state,
):
    configuration_changed = credential_state == "CREATED_BY_RUN"
    payload = {
        "schema": "fbsir.u3wDefaultOffConfigurationReceipt.v3",
        "mode": "Apply",
        "state": "CONFIGURED_NOT_LOADED",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "environmentPath": str(ENV_PATH),
        "environmentBackupPath": str(backup_path),
        "environmentBackupSha256": sha256_file(backup_path),
        "environmentBeforeSha256": journal["environmentBeforeSha256"],
        "environmentAfterSha256": after_sha,
        "api2EventKeyPath": str(API2_EVENT_KEY_PATH),
        "api2EventKeyProvisioningState":
            "REUSED_FROM_PREDECESSOR_RECEIPT",
        "stagedKeyMaterialMatched": True,
        "configurationEvidence": evidence,
        "environmentCustodySecure": True,
        "api2EventKeyCustodySecure": True,
        "environmentBackupCustodySecure": True,
        "serviceSnapshotBefore": journal["serviceSnapshotBefore"],
        "serviceSnapshotAfter": after_service,
        "approvalReceiptSha256": journal["approvalReceiptSha256"],
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "serviceRestarted": False,
        "productionDatabaseChanged": False,
        "productionFilesystemChanged": True,
        "productionConfigurationChanged": configuration_changed,
        "productionServiceChanged": False,
        "configurationLoaded": False,
        "officialExpertsPackageChanged": False,
        "secretsDisclosed": False,
        "observedAt": journal["receiptObservedAt"],
        "predecessorConfigurationReceiptSha256":
            journal["predecessorConfigurationReceiptSha256"],
        "adminEngineCredentialProvisioningState": credential_state,
        "engineCounterpartClosureClaimed": False,
    }
    validate_receipt(payload)
    return payload


def reconcile(args):
    approval = validate_approval(args, allow_expired=True)
    if os.geteuid() != 0:
        raise RuntimeError(
            "configuration reconciliation requires root"
        )
    lock_descriptor = open_host_change_lock()
    try:
        acquire_host_change_lock(lock_descriptor)
        approval = validate_approval(args, allow_expired=True)
        approval_current = approval_is_current(approval)
        if approval_current:
            validate_regular_file(ENV_PATH)
            preflight_bytes = ENV_PATH.read_bytes()
            preflight_text = preflight_bytes.decode("utf-8")
            preflight_sha = sha256_bytes(preflight_bytes)
            if preflight_sha != args.expected_environment_sha:
                raise RuntimeError(
                    "approved reconciliation environment anchor drifted"
                )
            preflight_values = parse_environment(preflight_text)
            if not str(
                preflight_values.get(ADMIN_ENGINE_TOKEN_NAME, "")
            ).strip():
                raise RuntimeError(
                    "unpaired admin Engine credential creation is "
                    "prohibited; this wave can only adopt the exact "
                    "pre-existing delta"
                )
            predecessor, _, event_material = (
                predecessor_configuration_receipt(
                    args,
                    require_live_environment=False,
                )
            )
            _, predecessor_evidence = (
                validate_exact_existing_admin_engine_delta(
                    preflight_text,
                    predecessor["environmentAfterSha256"],
                    event_material,
                )
            )
            if predecessor_evidence != predecessor[
                "configurationEvidence"
            ]:
                raise RuntimeError(
                    "preflight admin Engine credential predecessor "
                    "evidence changed"
                )
            run_directory = safe_run_directory(args.run_id)
        else:
            run_directory = CONFIG_ROOT / args.run_id
            if (
                run_directory.is_symlink()
                or not run_directory.is_dir()
                or run_directory.resolve().parent
                    != CONFIG_ROOT.resolve(strict=True)
            ):
                raise RuntimeError(
                    "expired reconciliation approval has no existing "
                    "recovery journal directory"
                )
            run_status = run_directory.stat()
            if (
                run_status.st_uid != 0
                or run_status.st_gid != 0
                or run_status.st_mode & 0o022
            ):
                raise RuntimeError(
                    "expired reconciliation recovery directory custody "
                    "is invalid"
                )
        receipt_path = run_directory / "configuration-receipt.json"
        if (
            not approval_current
            and (receipt_path.exists() or receipt_path.is_symlink())
        ):
            raise RuntimeError(
                "expired reconciliation approval cannot replay a "
                "completed receipt"
            )
        prior = existing_receipt(run_directory, args)
        if prior is not None:
            payload, path = prior
            if (
                payload["schema"]
                != "fbsir.u3wDefaultOffConfigurationReceipt.v3"
                or payload["predecessorConfigurationReceiptSha256"]
                != args.expected_predecessor_configuration_receipt_sha
            ):
                raise RuntimeError(
                    "reconciliation replay receipt identity changed"
                )
            return {
                "schema":
                    "fbsir.u3wDefaultOffConfigurationWorkerResult.v3",
                "mode": "Reconcile",
                "state": payload["state"],
                "runId": args.run_id,
                "sourceCommit": args.source_commit,
                "configurationReceiptPath": str(path),
                "configurationReceiptSha256": sha256_file(path),
                "productionFilesystemChanged": False,
                "productionConfigurationChanged": False,
                "productionBusinessStateChanged": False,
                "configurationLoaded": False,
                "serviceRestarted": False,
                "secretsDisclosed": False,
                "idempotentReplay": True,
            }
        validate_regular_file(ENV_PATH)
        current = ENV_PATH.read_bytes()
        current_text = current.decode("utf-8")
        current_sha = sha256_bytes(current)
        current_values = parse_environment(current_text)
        current_credential_present = bool(
            str(
                current_values.get(ADMIN_ENGINE_TOKEN_NAME, "")
            ).strip()
        )
        if not current_credential_present:
            raise RuntimeError(
                "unpaired admin Engine credential creation is prohibited; "
                "this wave can only adopt the exact pre-existing delta"
            )
        journal_path = run_directory / "configuration-journal.json"
        journal_preexisting = (
            journal_path.exists() or journal_path.is_symlink()
        )
        if not approval_current and not journal_preexisting:
            raise RuntimeError(
                "expired reconciliation approval has no existing journal"
            )
        predecessor, _, event_material = (
            predecessor_configuration_receipt(
                args,
                require_live_environment=(
                    not journal_preexisting
                    and not current_credential_present
                ),
            )
        )
        initial_service = (
            service_snapshot()
            if current_sha == args.expected_environment_sha
            else None
        )
        credential_existed_before = False
        if not journal_preexisting:
            if current_sha != args.expected_environment_sha:
                raise RuntimeError(
                    "approved reconciliation environment anchor drifted"
                )
            if current_credential_present:
                (
                    _,
                    evidence_before,
                ) = validate_exact_existing_admin_engine_delta(
                    current_text,
                    predecessor["environmentAfterSha256"],
                    event_material,
                )
                credential_existed_before = True
            else:
                if (
                    predecessor["environmentAfterSha256"]
                    != current_sha
                ):
                    raise RuntimeError(
                        "predecessor configuration environment anchor "
                        "drifted"
                    )
                evidence_before = configuration_evidence(
                    current_values,
                    event_material,
                )
                assert_configuration_evidence(evidence_before)
            if evidence_before != predecessor["configurationEvidence"]:
                raise RuntimeError(
                    "predecessor configuration evidence changed"
                )
        journal, _ = load_or_create_reconciliation_journal(
            run_directory,
            args,
            current_sha,
            service_before=initial_service,
            admin_engine_credential_existed_before=
                credential_existed_before,
        )
        if (
            not approval_current
            and (
                not journal_preexisting
                or not expired_reconciliation_finalization_authorized(
                    approval,
                    journal,
                    current_sha,
                )
            )
        ):
            raise RuntimeError(
                "expired reconciliation approval cannot authorize a new "
                "configuration mutation"
            )
        before_sha = journal["environmentBeforeSha256"]
        before_service = journal["serviceSnapshotBefore"]
        credential_state = (
            "ADOPTED_EXISTING_EXACT_DELTA"
            if journal["adminEngineCredentialExistedBefore"]
            else "CREATED_BY_RUN"
        )
        if credential_state != "ADOPTED_EXISTING_EXACT_DELTA":
            raise RuntimeError(
                "reconciliation cannot create an unpaired Engine token"
            )
        if credential_state == "CREATED_BY_RUN":
            if predecessor["environmentAfterSha256"] != before_sha:
                raise RuntimeError(
                    "predecessor configuration environment anchor drifted"
                )
        else:
            if current_sha != before_sha:
                raise RuntimeError(
                    "adopted admin Engine credential environment changed"
                )
            _, predecessor_evidence = (
                validate_exact_existing_admin_engine_delta(
                    current_text,
                    predecessor["environmentAfterSha256"],
                    event_material,
                )
            )
            if predecessor_evidence != predecessor["configurationEvidence"]:
                raise RuntimeError(
                    "adopted admin Engine credential predecessor "
                    "evidence changed"
                )
        backup_directory = safe_directory(ENV_BACKUP_ROOT)
        backup_path = pathlib.Path(journal["environmentBackupPath"])
        if current_sha == before_sha:
            original = current
            if backup_path.exists() or backup_path.is_symlink():
                validate_regular_file(backup_path)
                if (
                    sha256_file(backup_path) != before_sha
                    or backup_path.read_bytes() != original
                ):
                    raise RuntimeError(
                        "immutable reconciliation backup changed"
                    )
            else:
                descriptor = os.open(
                    backup_path,
                    os.O_WRONLY
                    | os.O_CREAT
                    | os.O_EXCL
                    | getattr(os, "O_NOFOLLOW", 0),
                    0o600,
                )
                try:
                    with os.fdopen(
                        descriptor, "wb", closefd=False
                    ) as handle:
                        handle.write(original)
                        handle.flush()
                        os.fsync(handle.fileno())
                finally:
                    os.close(descriptor)
                os.chmod(backup_path, 0o600)
                fsync_directory(backup_directory)
            validate_regular_file(backup_path)
            if sha256_file(backup_path) != before_sha:
                raise RuntimeError(
                    "reconciliation backup writeback mismatch"
                )
            if credential_state == "CREATED_BY_RUN":
                credential = secrets.token_urlsafe(48)
                rendered, generated_state = (
                    reconcile_admin_engine_credential(
                        original.decode("utf-8"),
                        credential,
                        api2_event_material=event_material,
                    )
                )
                if generated_state != "CREATED_BY_RUN":
                    raise RuntimeError(
                        "admin Engine credential reconciliation state "
                        "drifted"
                    )
                atomic_bytes(ENV_PATH, rendered.encode("utf-8"))
                MUTATION_STATE["productionConfigurationChanged"] = True
        else:
            if credential_state != "CREATED_BY_RUN":
                raise RuntimeError(
                    "adopted admin Engine credential changed during "
                    "reconciliation"
                )
            validate_regular_file(backup_path)
            if sha256_file(backup_path) != before_sha:
                raise RuntimeError(
                    "reconciliation recovery backup anchor mismatch"
                )
        after_bytes = validate_regular_file(ENV_PATH).read_bytes()
        after_values = parse_environment(after_bytes.decode("utf-8"))
        after_evidence = configuration_v3_evidence(
            after_values,
            validate_regular_file(API2_EVENT_KEY_PATH).read_bytes(),
        )
        assert_configuration_v3_evidence(after_evidence)
        backup_text = backup_path.read_text(encoding="utf-8")
        if credential_state == "CREATED_BY_RUN":
            predecessor_text = backup_text
            expected_after, expected_credential_state = (
                reconcile_admin_engine_credential(
                    backup_text,
                    str(
                        after_values.get(ADMIN_ENGINE_TOKEN_NAME, "")
                    ).strip(),
                    api2_event_material=event_material,
                )
            )
            if (
                expected_credential_state != "CREATED_BY_RUN"
                or expected_after.encode("utf-8") != after_bytes
            ):
                raise RuntimeError(
                    "reconciliation recovery contains an unauthorized "
                    "environment delta"
                )
        else:
            if backup_text.encode("utf-8") != after_bytes:
                raise RuntimeError(
                    "adopted admin Engine credential environment drifted"
                )
            (
                predecessor_text,
                predecessor_evidence,
            ) = validate_exact_existing_admin_engine_delta(
                backup_text,
                predecessor["environmentAfterSha256"],
                event_material,
            )
            if predecessor_evidence != predecessor["configurationEvidence"]:
                raise RuntimeError(
                    "adopted admin Engine credential predecessor "
                    "evidence changed"
                )
        predecessor_values = parse_environment(predecessor_text)
        for name in MANAGED_KEYS:
            if after_values.get(name) != predecessor_values.get(name):
                raise RuntimeError(
                    "managed W1A configuration changed during reconciliation"
                )
        after_service = service_snapshot()
        if before_service != after_service:
            raise RuntimeError(
                "service changed during zero-restart reconciliation"
            )
        after_sha = sha256_file(ENV_PATH)
        payload = build_reconciliation_receipt(
            args,
            journal,
            backup_path,
            after_sha,
            after_evidence,
            after_service,
            credential_state,
        )
        receipt_path = run_directory / "configuration-receipt.json"
        atomic_bytes(
            receipt_path, canonical_json(payload).encode("utf-8")
        )
        publish_latest(run_directory)
        return {
            "schema":
                "fbsir.u3wDefaultOffConfigurationWorkerResult.v3",
            "mode": "Reconcile",
            "state": "CONFIGURED_NOT_LOADED",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "configurationReceiptPath": str(receipt_path),
            "configurationReceiptSha256": sha256_file(receipt_path),
            "productionFilesystemChanged": True,
            "productionConfigurationChanged":
                credential_state == "CREATED_BY_RUN",
            "productionBusinessStateChanged": False,
            "configurationLoaded": False,
            "serviceRestarted": False,
            "secretsDisclosed": False,
            "idempotentReplay": False,
        }
    finally:
        os.close(lock_descriptor)


def apply(args):
    validate_approval(args)
    if os.geteuid() != 0:
        raise RuntimeError("configuration provisioning requires root")
    lock_descriptor = open_host_change_lock()
    try:
        acquire_host_change_lock(lock_descriptor)
        validate_approval(args)
        run_directory = safe_run_directory(args.run_id)
        prior = existing_receipt(run_directory, args)
        if prior is not None:
            payload, path = prior
            return {
                "schema": "fbsir.u3wDefaultOffConfigurationWorkerResult.v2",
                "mode": "Apply",
                "state": payload["state"],
                "runId": args.run_id,
                "sourceCommit": args.source_commit,
                "configurationReceiptPath": str(path),
                "configurationReceiptSha256": sha256_file(path),
                "productionFilesystemChanged": False,
                "productionConfigurationChanged": False,
                "productionBusinessStateChanged": False,
                "configurationLoaded": False,
                "serviceRestarted": False,
                "secretsDisclosed": False,
                "idempotentReplay": True,
            }
        MUTATION_STATE["productionFilesystemChanged"] = True
        validate_regular_file(ENV_PATH)
        current = ENV_PATH.read_bytes()
        current_sha = sha256_bytes(current)
        journal_path = run_directory / "configuration-journal.json"
        journal_preexisting = (
            journal_path.exists() or journal_path.is_symlink()
        )
        if (
            not journal_preexisting
            and (
                API2_EVENT_KEY_PATH.exists()
                or API2_EVENT_KEY_PATH.is_symlink()
            )
        ):
            raise RuntimeError(
                "unanchored pre-existing API2 event key is forbidden"
            )
        initial_service = (
            service_snapshot()
            if current_sha == args.expected_environment_sha
            else None
        )
        journal, _ = load_or_create_journal(
            run_directory,
            args,
            current_sha,
            service_before=initial_service,
        )
        before_sha = journal["environmentBeforeSha256"]
        before_service = journal["serviceSnapshotBefore"]
        backup_directory = safe_directory(ENV_BACKUP_ROOT)
        backup_path = pathlib.Path(journal["environmentBackupPath"])
        event_material, event_key_created_now = (
            read_or_create_api2_event_key()
        )
        if not event_key_created_now and not journal_preexisting:
            raise RuntimeError("API2 event key provenance is not anchored")
        if current_sha == before_sha:
            original = current
            existing_values = parse_environment(
                original.decode("utf-8")
            )
            present = [
                name for name in MANAGED_KEYS if name in existing_values
            ]
            if present:
                raise RuntimeError(
                    "pre-change environment contains managed W1A keys"
                )
            binding_material = secrets.token_bytes(32)
            while hmac.compare_digest(
                event_material, binding_material
            ):
                binding_material = secrets.token_bytes(32)
            rendered = render_configuration(
                original.decode("utf-8"),
                event_key_id="w1a-20260723-k1",
                event_material=event_material,
                same_binding_material=binding_material,
            ).encode("utf-8")
            values = parse_environment(rendered.decode("utf-8"))
            evidence = configuration_evidence(values, event_material)
            assert_configuration_evidence(evidence)
            if backup_path.exists() or backup_path.is_symlink():
                validate_regular_file(backup_path)
                if (
                    sha256_file(backup_path) != before_sha
                    or backup_path.read_bytes() != original
                ):
                    raise RuntimeError(
                        "immutable environment backup changed"
                    )
            else:
                descriptor = os.open(
                    backup_path,
                    os.O_WRONLY
                    | os.O_CREAT
                    | os.O_EXCL
                    | getattr(os, "O_NOFOLLOW", 0),
                    0o600,
                )
                try:
                    with os.fdopen(
                        descriptor, "wb", closefd=False
                    ) as handle:
                        handle.write(original)
                        handle.flush()
                        os.fsync(handle.fileno())
                finally:
                    os.close(descriptor)
                os.chmod(backup_path, 0o600)
                fsync_directory(backup_directory)
            validate_regular_file(backup_path)
            if sha256_file(backup_path) != before_sha:
                raise RuntimeError("environment backup writeback mismatch")
            atomic_bytes(ENV_PATH, rendered)
            MUTATION_STATE["productionConfigurationChanged"] = True
        else:
            validate_regular_file(backup_path)
            if sha256_file(backup_path) != before_sha:
                raise RuntimeError(
                    "recovery environment backup anchor mismatch"
                )
            recovery_values = parse_environment(
                current.decode("utf-8")
            )
            recovery_evidence = configuration_evidence(
                recovery_values, event_material
            )
            assert_configuration_evidence(recovery_evidence)
            evidence = recovery_evidence
        validate_regular_file(ENV_PATH)
        after_values = parse_environment(
            ENV_PATH.read_text(encoding="utf-8")
        )
        after_evidence = configuration_evidence(
            after_values,
            validate_regular_file(API2_EVENT_KEY_PATH).read_bytes(),
        )
        assert_configuration_evidence(after_evidence)
        if after_evidence != evidence:
            raise RuntimeError("configuration atomic readback changed")
        after_service = service_snapshot()
        if before_service != after_service:
            raise RuntimeError("service changed during zero-restart provisioning")
        after_sha = sha256_file(ENV_PATH)
        payload = build_configuration_receipt(
            args,
            journal,
            backup_path,
            after_sha,
            after_evidence,
            after_service,
        )
        receipt_path = run_directory / "configuration-receipt.json"
        atomic_bytes(
            receipt_path, canonical_json(payload).encode("utf-8")
        )
        publish_latest(run_directory)
        return {
            "schema": "fbsir.u3wDefaultOffConfigurationWorkerResult.v2",
            "mode": "Apply",
            "state": "CONFIGURED_NOT_LOADED",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "configurationReceiptPath": str(receipt_path),
            "configurationReceiptSha256": sha256_file(receipt_path),
            "productionFilesystemChanged": True,
            "productionConfigurationChanged": True,
            "productionBusinessStateChanged": False,
            "configurationLoaded": False,
            "serviceRestarted": False,
            "secretsDisclosed": False,
            "idempotentReplay": False,
        }
    finally:
        os.close(lock_descriptor)


def recover(args):
    validate_approval(args)
    if os.geteuid() != 0:
        raise RuntimeError("configuration anchor recovery requires root")
    lock_descriptor = open_host_change_lock()
    try:
        acquire_host_change_lock(lock_descriptor)
        validate_approval(args)
        run_directory = CONFIG_ROOT / args.run_id
        if not run_directory.is_dir() or run_directory.is_symlink():
            raise RuntimeError("configuration recovery journal directory is absent")
        safe_directory(run_directory)
        prior = existing_receipt(
            run_directory,
            args,
            expected_original_approval_sha=args.original_approval_sha,
        )
        current = validate_regular_file(ENV_PATH).read_bytes()
        current_sha = sha256_bytes(current)
        if current_sha != args.expected_configured_environment_sha:
            raise RuntimeError("configured environment recovery anchor mismatch")
        journal, _ = load_or_create_journal(
            run_directory,
            args,
            args.expected_environment_sha,
            expected_original_approval_sha=args.original_approval_sha,
        )
        if journal["approvalReceiptSha256"] != args.original_approval_sha:
            raise RuntimeError("original configuration approval anchor changed")
        backup_path = validate_regular_file(
            journal["environmentBackupPath"]
        )
        if sha256_file(backup_path) != args.expected_environment_sha:
            raise RuntimeError("configuration recovery backup anchor mismatch")
        event_material = validate_regular_file(
            API2_EVENT_KEY_PATH
        ).read_bytes()
        evidence = configuration_evidence(
            parse_environment(current.decode("utf-8")),
            event_material,
        )
        assert_configuration_evidence(evidence)
        after_service = service_snapshot()
        if after_service != journal["serviceSnapshotBefore"]:
            raise RuntimeError(
                "service changed before configuration anchor recovery"
            )
        payload = build_configuration_receipt(
            args,
            journal,
            backup_path,
            current_sha,
            evidence,
            after_service,
        )
        encoded = canonical_json(payload).encode("utf-8")
        receipt_path = run_directory / "configuration-receipt.json"
        if prior is not None:
            prior_payload, prior_path = prior
            if prior_payload != payload or prior_path != receipt_path:
                raise RuntimeError(
                    "existing configuration recovery receipt changed"
                )
        else:
            MUTATION_STATE["productionFilesystemChanged"] = True
            atomic_bytes(receipt_path, encoded)
        publish_latest(run_directory)
        MUTATION_STATE["productionFilesystemChanged"] = True
        return {
            "schema": "fbsir.u3wDefaultOffConfigurationWorkerResult.v2",
            "mode": "Recover",
            "state": "ANCHOR_RECOVERED",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "configurationReceiptPath": str(receipt_path),
            "configurationReceiptSha256": sha256_file(receipt_path),
            "originalApprovalReceiptSha256": args.original_approval_sha,
            "recoveryApprovalReceiptSha256": args.approval_sha,
            "productionFilesystemChanged": True,
            "productionConfigurationChanged": False,
            "productionBusinessStateChanged": False,
            "productionServiceChanged": False,
            "configurationLoaded": False,
            "serviceRestarted": False,
            "secretsDisclosed": False,
        }
    finally:
        os.close(lock_descriptor)


def parse_args(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode",
        choices=("Plan", "Apply", "Verify", "Recover", "Reconcile"),
        required=True,
    )
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--approval-sha", required=True)
    parser.add_argument("--approval-json-base64", default="")
    parser.add_argument("--runner-sha", required=True)
    parser.add_argument("--worker-sha", required=True)
    parser.add_argument("--expected-environment-sha", required=True)
    parser.add_argument(
        "--expected-configured-environment-sha", default="0" * 64
    )
    parser.add_argument("--original-approval-sha", default="0" * 64)
    parser.add_argument(
        "--expected-predecessor-configuration-receipt-sha",
        default="0" * 64,
    )
    args = parser.parse_args(argv)
    if (
        not RUN_PATTERN.fullmatch(args.run_id)
        or not COMMIT_PATTERN.fullmatch(args.source_commit)
        or not SHA_PATTERN.fullmatch(args.approval_sha)
        or not SHA_PATTERN.fullmatch(args.runner_sha)
        or not SHA_PATTERN.fullmatch(args.worker_sha)
        or not SHA_PATTERN.fullmatch(args.expected_environment_sha)
        or not SHA_PATTERN.fullmatch(
            args.expected_configured_environment_sha
        )
        or not SHA_PATTERN.fullmatch(args.original_approval_sha)
        or not SHA_PATTERN.fullmatch(
            args.expected_predecessor_configuration_receipt_sha
        )
        or (
            args.mode == "Recover"
            and (
                args.expected_configured_environment_sha == "0" * 64
                or args.original_approval_sha == "0" * 64
            )
        )
        or (
            args.mode == "Reconcile"
            and args.expected_predecessor_configuration_receipt_sha
            == "0" * 64
        )
    ):
        parser.error("run, source or digest argument shape is invalid")
    return args


def main(argv=None):
    args = parse_args(argv)
    if args.mode == "Plan":
        result = plan(args)
    elif args.mode == "Verify":
        result = verify(args)
    elif args.mode == "Recover":
        result = recover(args)
    elif args.mode == "Reconcile":
        result = reconcile(args)
    else:
        result = apply(args)
    print(canonical_json(result))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(
            canonical_json(
                {
                    "schema": "fbsir.u3wDefaultOffConfigurationWorkerError.v1",
                    "errorType": type(error).__name__,
                    "error": str(error),
                    "productionFilesystemChanged": MUTATION_STATE[
                        "productionFilesystemChanged"
                    ],
                    "productionConfigurationChanged": MUTATION_STATE[
                        "productionConfigurationChanged"
                    ],
                    "productionBusinessStateChanged": False,
                    "productionServiceChanged": False,
                    "configurationLoaded": False,
                    "serviceRestarted": False,
                    "secretsDisclosed": False,
                }
            ),
            file=sys.stderr,
        )
        raise
