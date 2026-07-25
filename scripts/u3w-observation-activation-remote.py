#!/usr/bin/env python3
"""Activate only the W1A observation spine and fail back to default-off.

The worker runs on the U3W host.  It never emits secret values or per-secret
digests.  A durable pending journal is written before the environment changes;
an interrupted transaction is recovered by the explicit Recover mode.
"""

import argparse
import base64
import datetime as dt
import hashlib
import hmac
import http.client
import json
import os
import pathlib
import re
import stat
import subprocess
import sys
import time

try:
    import fcntl
except ImportError:  # pragma: no cover - Windows unit tests
    fcntl = None


TARGET_HOST = "api2.u3w.com"
SERVICE_UNIT = "fbsir-admin.service"
ENV_PATH = pathlib.Path("/etc/u3w/fbsir-admin.env")
API2_EVENT_KEY_PATH = pathlib.Path(
    "/etc/u3w/secrets/independent-board-attribution-event-key"
)
CURRENT_ROOT = pathlib.Path("/opt/fbsir/admin/current")
ACTIVATION_ROOT = pathlib.Path(
    "/opt/fbsir/admin/observation-activation"
)
PENDING_PATH = ACTIVATION_ROOT / "pending.json"
HOST_CHANGE_LOCK = pathlib.Path(
    "/opt/fbsir/admin/.u3w-production-change.lock"
)
ACTIVATION_PATTERN = re.compile(
    r"w1a-observe-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}"
)
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
RELEASE_PATTERN = re.compile(
    r"w1a-release-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z"
)
KEY_ID_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}")

ACTIVE_TRUE_FLAGS = (
    "FBSIR_BOARD_ATTRIBUTION_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_WRITER_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_INTENT_CLASSIFIER_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_ADMIN_READ_ENABLED",
)
ACTIVE_FALSE_FLAGS = (
    "FBSIR_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_CREDIT_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_CREDIT_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED",
)
ALL_ATTRIBUTION_FLAGS = ACTIVE_TRUE_FLAGS + ACTIVE_FALSE_FLAGS
EVENT_KEY_ID_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID"
)
EVENT_KEY_NAME = "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY"
SAME_BINDING_KEY_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET"
)
SECRET_NAMES = (EVENT_KEY_NAME, SAME_BINDING_KEY_NAME)
ZERO_SHA = "0" * 64


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
        for name in ALL_ATTRIBUTION_FLAGS
    }
    forbidden = [
        name
        for name in values
        if (
            name not in ALL_ATTRIBUTION_FLAGS
            and re.sub(r"[^a-z0-9]", "", name.lower())
            in normalized_flags
        )
        or re.sub(r"[^a-z0-9]", "", name.lower()).startswith(
            "spring"
        )
        or re.sub(r"[^a-z0-9]", "", name.lower())
        in {"javatooloptions", "jdkjavaoptions"}
    ]
    if forbidden:
        raise RuntimeError(
            "environment contains a relaxed-binding override"
        )
    return values


def decode_material(value):
    value = str(value or "").strip()
    try:
        if value.startswith("base64:"):
            return base64.b64decode(value[7:], validate=True)
        if value.startswith("hex:"):
            raw = value[4:]
            if (
                not raw
                or len(raw) % 2
                or not re.fullmatch(r"[0-9a-fA-F]+", raw)
            ):
                return None
            return bytes.fromhex(raw)
        if value.startswith("utf8:"):
            value = value[5:]
        return value.encode("utf-8") if value else None
    except (ValueError, TypeError):
        return None


def expected_flag_values(active):
    if active:
        return {
            **{name: "true" for name in ACTIVE_TRUE_FLAGS},
            **{name: "false" for name in ACTIVE_FALSE_FLAGS},
        }
    return {name: "false" for name in ALL_ATTRIBUTION_FLAGS}


def configuration_evidence(
    values, api2_event_material, expected_active
):
    expected = expected_flag_values(expected_active)
    actual = {name: values.get(name) for name in ALL_ATTRIBUTION_FLAGS}
    active_id = str(values.get(EVENT_KEY_ID_NAME, "")).strip()
    event_material = decode_material(values.get(EVENT_KEY_NAME, ""))
    binding_material = decode_material(
        values.get(SAME_BINDING_KEY_NAME, "")
    )
    event_valid = bool(
        KEY_ID_PATTERN.fullmatch(active_id)
        and event_material is not None
        and len(event_material) >= 32
    )
    binding_valid = bool(
        binding_material is not None
        and len(binding_material) >= 32
        and event_material is not None
        and not hmac.compare_digest(
            binding_material, event_material
        )
    )
    api2_match = bool(
        api2_event_material is not None
        and event_material is not None
        and hmac.compare_digest(
            api2_event_material, event_material
        )
    )
    return {
        "expectedState": "ACTIVE_OBSERVATION"
        if expected_active
        else "DEFAULT_OFF",
        "expectedFlagValues": expected,
        "actualFlagValues": actual,
        "flagsMatch": actual == expected,
        "eventKeyId": active_id,
        "eventKeyPresent": event_valid,
        "sameBindingSecretPresent": binding_valid,
        "eventAndBindingSecretsIndependent": binding_valid,
        "api2EventKeyMatches": api2_match,
        "productCreditEnabled": (
            actual.get(
                "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_"
                "PRODUCT_CREDIT_ENABLED"
            )
            == "true"
        ),
        "contractSatisfied": bool(
            actual == expected
            and event_valid
            and binding_valid
            and api2_match
        ),
        "secretsDisclosed": False,
    }


def assert_configuration(values, api2_material, expected_active):
    evidence = configuration_evidence(
        values, api2_material, expected_active
    )
    if not evidence["contractSatisfied"]:
        raise RuntimeError(
            "observation activation configuration contract failed"
        )
    return evidence


def render_activation(text):
    values = parse_environment(text)
    for name in ALL_ATTRIBUTION_FLAGS:
        if values.get(name) != "false":
            raise RuntimeError(
                "activation requires exact default-off prestate"
            )
    replacements = expected_flag_values(True)
    seen = set()
    output = []
    for raw in str(text).splitlines(keepends=True):
        ending = (
            "\r\n"
            if raw.endswith("\r\n")
            else "\n"
            if raw.endswith("\n")
            else ""
        )
        content = raw[: -len(ending)] if ending else raw
        candidate = content.strip()
        if candidate.startswith("export "):
            candidate = candidate[7:].lstrip()
        name = (
            candidate.split("=", 1)[0].strip()
            if "=" in candidate
            else ""
        )
        if name in replacements:
            if name in seen:
                raise RuntimeError(
                    "duplicate managed environment key"
                )
            output.append(
                "{}={}{}".format(name, replacements[name], ending)
            )
            seen.add(name)
        else:
            output.append(raw)
    if seen != set(ALL_ATTRIBUTION_FLAGS):
        raise RuntimeError("managed environment keys are incomplete")
    rendered = "".join(output)
    assert_configuration(
        parse_environment(rendered),
        decode_material(values[EVENT_KEY_NAME]),
        True,
    )
    return rendered


def validate_regular_file(path, allowed_modes=(0o600, 0o640, 0o644)):
    path = pathlib.Path(path)
    status = path.lstat()
    if (
        not stat.S_ISREG(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_mode & 0o777 not in allowed_modes
    ):
        raise RuntimeError("unsafe regular file: " + str(path))
    return path


def safe_directory(path, mode=0o700):
    path = pathlib.Path(path)
    path.mkdir(mode=mode, parents=True, exist_ok=True)
    status = path.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_mode & 0o022
    ):
        raise RuntimeError("unsafe directory: " + str(path))
    if status.st_mode & 0o777 != mode:
        os.chmod(path, mode)
    return path


def fsync_directory(path):
    if os.name == "nt":  # Directory handles are not fsync-able on Windows.
        return
    descriptor = os.open(str(pathlib.Path(path)), os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def atomic_replace_preserving_metadata(path, payload):
    path = pathlib.Path(path)
    status = path.stat()
    partial = path.with_name(
        ".{}.{}.partial".format(path.name, os.getpid())
    )
    descriptor = os.open(
        str(partial),
        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
        status.st_mode & 0o777,
    )
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        descriptor = -1
        if hasattr(os, "chown"):
            os.chown(partial, status.st_uid, status.st_gid)
        os.chmod(partial, status.st_mode & 0o777)
        os.replace(partial, path)
        fsync_directory(path.parent)
    finally:
        if descriptor >= 0:
            try:
                os.close(descriptor)
            except OSError:
                pass
        if partial.exists():
            partial.unlink()


def atomic_create(path, payload, mode=0o600):
    path = pathlib.Path(path)
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    descriptor = os.open(
        str(path),
        os.O_WRONLY | os.O_CREAT | os.O_EXCL,
        mode,
    )
    with os.fdopen(descriptor, "wb") as handle:
        handle.write(payload)
        handle.flush()
        os.fsync(handle.fileno())
    os.chmod(path, mode)
    fsync_directory(path.parent)


def atomic_json_replace(path, payload):
    path = pathlib.Path(path)
    encoded = canonical_json(payload).encode("utf-8")
    if path.exists():
        atomic_replace_preserving_metadata(path, encoded)
    else:
        atomic_create(path, encoded)


def run_checked(command):
    process = subprocess.run(
        command, text=True, capture_output=True, timeout=30
    )
    if process.returncode:
        raise RuntimeError(
            "command failed: {}".format(command[0])
        )
    return process.stdout.strip()


def service_snapshot():
    raw = run_checked([
        "systemctl",
        "show",
        SERVICE_UNIT,
        "--property=ActiveState,SubState,MainPID,ExecMainStatus",
    ])
    values = {}
    for line in raw.splitlines():
        if "=" in line:
            name, value = line.split("=", 1)
            values[name] = value
    pid = values.get("MainPID", "")
    if (
        values.get("ActiveState") != "active"
        or values.get("SubState") != "running"
        or not pid.isdigit()
        or int(pid) <= 0
        or values.get("ExecMainStatus") != "0"
    ):
        raise RuntimeError("U3W service is not active")
    return {
        "activeState": values["ActiveState"],
        "subState": values["SubState"],
        "mainPid": int(pid),
        "execMainStatus": int(values["ExecMainStatus"]),
    }


def process_environment(pid):
    raw = pathlib.Path(
        "/proc", str(pid), "environ"
    ).read_bytes()
    return parse_environment(
        raw.replace(b"\0", b"\n").decode(
            "utf-8", errors="strict"
        )
    )


class RuntimeExpectationError(RuntimeError):
    def __init__(self, evidence):
        super().__init__(
            "U3W observation runtime did not reach expected state"
        )
        self.evidence = evidence


def http_probe(path, method="GET", body=None):
    connection = http.client.HTTPConnection(
        "127.0.0.1", 8080, timeout=5
    )
    try:
        headers = (
            {"Content-Type": "application/json"}
            if body is not None
            else {}
        )
        connection.request(method, path, body=body, headers=headers)
        response = connection.getresponse()
        return bounded_http_evidence(response, response.status)
    finally:
        connection.close()


def bounded_http_evidence(response, status):
    raw = response.read(4097)
    too_large = len(raw) > 4096
    payload = {}
    if not too_large:
        try:
            decoded = json.loads(raw.decode("utf-8"))
            payload = decoded if isinstance(decoded, dict) else {}
        except (UnicodeDecodeError, ValueError):
            payload = {}
    reason = payload.get("reason", payload.get("msg"))
    semantic = (
        "OFFICIAL_IDENTITY_MISMATCH"
        if reason == "official_identity_mismatch"
        else "OTHER"
    )
    cache_control = str(
        response.headers.get("Cache-Control", "")
    ).lower()
    pragma = str(response.headers.get("Pragma", "")).lower()
    code = payload.get("code")
    return {
        "status": int(status),
        "jsonCode": code if isinstance(code, int) else None,
        "semantic": semantic,
        "cacheControlNoStore": "no-store" in cache_control,
        "pragmaNoCache": "no-cache" in pragma,
        "bodyWithinLimit": not too_large,
    }


def ingress_state_matches(probe, expected_active):
    if not expected_active:
        return probe.get("status") == 404
    transport = (
        probe.get("status") == 400
        or (
            probe.get("status") == 200
            and probe.get("jsonCode") == 500
        )
    )
    return bool(
        transport
        and probe.get("semantic")
        == "OFFICIAL_IDENTITY_MISMATCH"
        and probe.get("cacheControlNoStore") is True
        and probe.get("bodyWithinLimit") is True
    )


def wait_for_runtime(expected_active, timeout=180):
    deadline = time.monotonic() + timeout
    last = {}
    while time.monotonic() < deadline:
        try:
            snapshot = service_snapshot()
            captcha = http_probe("/captchaImage")
            ingress = http_probe(
                "/internal/independent-board/attribution/events",
                method="POST",
                body=b"{}",
            )
            environment = process_environment(
                snapshot["mainPid"]
            )
            flags_match = all(
                environment.get(name) == value
                for name, value in expected_flag_values(
                    expected_active
                ).items()
            )
            ingress_match = ingress_state_matches(
                ingress, expected_active
            )
            last = {
                "service": snapshot,
                "captchaStatus": captcha["status"],
                "ingressProbe": ingress,
                "ingressTransportContractCompliant": (
                    ingress["status"] == 400
                ),
                "runtimeFlagsMatch": flags_match,
                "ingressStateMatch": ingress_match,
                "probeDatabaseWriteAuthorized": False,
            }
            if (
                captcha["status"] == 200
                and flags_match
                and ingress_match
            ):
                return last
        except Exception as error:  # bounded readiness retry
            last = {"errorType": type(error).__name__}
        time.sleep(1)
    raise RuntimeExpectationError(last)


def restart_and_verify(expected_active):
    run_checked(["systemctl", "restart", SERVICE_UNIT])
    return wait_for_runtime(
        expected_active,
        timeout=60 if expected_active else 180,
    )


def remove_pending(path):
    path = pathlib.Path(path)
    if path.exists():
        path.unlink()
        fsync_directory(path.parent)


def mutate_with_automatic_rollback(
    *,
    activation_id,
    source_commit,
    expected_environment_sha,
    backup_path,
    pending_path,
    terminal_rollback_path,
    runtime_transition,
):
    before = pathlib.Path(ENV_PATH).read_bytes()
    before_sha = sha256_bytes(before)
    if before_sha != expected_environment_sha:
        raise RuntimeError("environment preimage digest changed")
    values = parse_environment(before.decode("utf-8"))
    assert_configuration(
        values,
        decode_material(values.get(EVENT_KEY_NAME, "")),
        False,
    )
    if pathlib.Path(pending_path).exists():
        raise RuntimeError(
            "an observation activation transaction is pending"
        )
    atomic_create(backup_path, before)
    journal = {
        "schema": "fbsir.u3wObservationActivationJournal.v1",
        "activationId": activation_id,
        "sourceCommit": source_commit,
        "environmentPath": str(ENV_PATH),
        "environmentBeforeSha256": before_sha,
        "environmentBackupPath": str(backup_path),
        "environmentBackupSha256": sha256_file(backup_path),
        "state": "PREPARED",
        "updatedAt": utc_now(),
        "officialExpertsPackageChanged": False,
        "secretsDisclosed": False,
    }
    atomic_create(
        pending_path, canonical_json(journal).encode("utf-8")
    )
    try:
        rendered = render_activation(before.decode("utf-8")).encode(
            "utf-8"
        )
        atomic_replace_preserving_metadata(ENV_PATH, rendered)
        journal["state"] = "CONFIGURATION_WRITTEN"
        journal["environmentAfterSha256"] = sha256_bytes(rendered)
        journal["updatedAt"] = utc_now()
        atomic_json_replace(pending_path, journal)
        runtime = runtime_transition(True)
        journal["state"] = "RUNTIME_ACTIVE"
        journal["updatedAt"] = utc_now()
        atomic_json_replace(pending_path, journal)
        return {
            "state": "ACTIVE",
            "environmentBeforeSha256": before_sha,
            "environmentAfterSha256": sha256_bytes(rendered),
            "environmentBackupSha256": sha256_file(backup_path),
            "runtime": runtime,
            "journal": journal,
        }
    except Exception as error:
        atomic_replace_preserving_metadata(
            ENV_PATH, pathlib.Path(backup_path).read_bytes()
        )
        rollback_runtime = runtime_transition(False)
        exact = (
            sha256_file(ENV_PATH) == before_sha
            and pathlib.Path(ENV_PATH).read_bytes() == before
        )
        rollback = {
            "schema": "fbsir.u3wObservationActivationRollback.v1",
            "activationId": activation_id,
            "sourceCommit": source_commit,
            "state": "ROLLED_BACK",
            "rolledBackAt": utc_now(),
            "failureType": type(error).__name__,
            "failureMessageSha256": sha256_bytes(
                str(error).encode("utf-8", errors="replace")
            ),
            "failureEvidence": (
                error.evidence
                if isinstance(error, RuntimeExpectationError)
                else None
            ),
            "environmentBeforeSha256": before_sha,
            "environmentBackupSha256": sha256_file(backup_path),
            "exactPreimageRestored": exact,
            "runtimeDefaultOffVerified": rollback_runtime,
            "officialExpertsPackageChanged": False,
            "secretsDisclosed": False,
        }
        atomic_create(
            terminal_rollback_path,
            canonical_json(rollback).encode("utf-8"),
        )
        remove_pending(pending_path)
        return rollback


def open_lock():
    if fcntl is None:
        raise RuntimeError("host lock is unavailable")
    flags = os.O_RDWR | os.O_CREAT
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor = os.open(
        str(HOST_CHANGE_LOCK), flags, 0o600
    )
    status = os.fstat(descriptor)
    if (
        not stat.S_ISREG(status.st_mode)
        or status.st_uid != 0
        or status.st_mode & 0o077
    ):
        os.close(descriptor)
        raise RuntimeError("host change lock is unsafe")
    fcntl.flock(descriptor, fcntl.LOCK_EX)
    return descriptor


def deployed_release_evidence(args):
    receipt_path = CURRENT_ROOT / "deployment-receipt.json"
    validate_regular_file(receipt_path)
    receipt_sha = sha256_file(receipt_path)
    if receipt_sha != args.expected_deployment_receipt_sha:
        raise RuntimeError("deployment receipt digest drifted")
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    resolved = CURRENT_ROOT.resolve()
    expected_root = pathlib.Path(
        "/opt/fbsir/admin/releases"
    ) / args.expected_release_id
    if (
        receipt.get("state") != "DEPLOYED_DEFAULT_OFF"
        or receipt.get("sourceCommit")
        != args.expected_deployed_commit
        or receipt.get("releaseId") != args.expected_release_id
        or receipt.get("targetHost") != TARGET_HOST
        or receipt.get("serviceUnit") != SERVICE_UNIT
        or receipt.get("officialExpertsPackageChanged") is not False
        or resolved != expected_root
    ):
        raise RuntimeError("deployed default-off release drifted")
    return {
        "releaseId": args.expected_release_id,
        "deployedSourceCommit": args.expected_deployed_commit,
        "deploymentReceiptSha256": receipt_sha,
        "currentResolved": str(resolved),
        "officialExpertsPackageChanged": False,
    }


def host_configuration(expected_active):
    validate_regular_file(ENV_PATH)
    validate_regular_file(API2_EVENT_KEY_PATH)
    raw = ENV_PATH.read_text(encoding="utf-8")
    values = parse_environment(raw)
    event_material = API2_EVENT_KEY_PATH.read_bytes()
    evidence = assert_configuration(
        values, event_material, expected_active
    )
    return raw, evidence


def plan(args):
    release = deployed_release_evidence(args)
    raw, configuration = host_configuration(False)
    runtime = wait_for_runtime(False, timeout=5)
    return {
        "schema": "fbsir.u3wObservationActivationResult.v1",
        "mode": "Plan",
        "state": "READY_TO_ACTIVATE",
        "activationId": args.activation_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "environmentPath": str(ENV_PATH),
        "environmentBeforeSha256": sha256_bytes(
            raw.encode("utf-8")
        ),
        "release": release,
        "configuration": configuration,
        "runtime": runtime,
        "databaseWriteAuthorized": False,
        "officialExpertsPackageSha256": (
            args.official_package_sha
        ),
        "officialExpertsPackageChanged": False,
        "secretsDisclosed": False,
    }


def apply(args):
    if os.geteuid() != 0:
        raise RuntimeError("observation activation requires root")
    if args.expected_environment_sha == ZERO_SHA:
        raise RuntimeError("Apply requires a Plan environment digest")
    release = deployed_release_evidence(args)
    host_configuration(False)
    safe_directory(ACTIVATION_ROOT)
    transaction_root = safe_directory(
        ACTIVATION_ROOT / args.activation_id
    )
    backup_path = transaction_root / "environment.before"
    rollback_path = transaction_root / "rollback-receipt.json"
    receipt_path = transaction_root / "activation-receipt.json"
    if receipt_path.exists():
        raise RuntimeError("activation receipt already exists")
    result = mutate_with_automatic_rollback(
        activation_id=args.activation_id,
        source_commit=args.source_commit,
        expected_environment_sha=args.expected_environment_sha,
        backup_path=backup_path,
        pending_path=PENDING_PATH,
        terminal_rollback_path=rollback_path,
        runtime_transition=restart_and_verify,
    )
    if result["state"] != "ACTIVE":
        return result
    _, configuration = host_configuration(True)
    receipt = {
        "schema": "fbsir.u3wObservationActivationReceipt.v1",
        "mode": "Apply",
        "state": "OBSERVATION_ACTIVE",
        "activationId": args.activation_id,
        "activatedAt": utc_now(),
        "sourceCommit": args.source_commit,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "environmentPath": str(ENV_PATH),
        "environmentBeforeSha256": result[
            "environmentBeforeSha256"
        ],
        "environmentAfterSha256": result[
            "environmentAfterSha256"
        ],
        "environmentBackupPath": str(backup_path),
        "environmentBackupSha256": result[
            "environmentBackupSha256"
        ],
        "release": release,
        "configuration": configuration,
        "runtime": result["runtime"],
        "authorization": args.authorization,
        "productionConfigurationChanged": True,
        "productionServiceChanged": True,
        "productionDatabaseChanged": False,
        "productCreditEnabled": False,
        "publicRouteEnabled": False,
        "officialExpertsPackageSha256": (
            args.official_package_sha
        ),
        "officialExpertsPackageChanged": False,
        "secretsDisclosed": False,
    }
    atomic_create(
        receipt_path, canonical_json(receipt).encode("utf-8")
    )
    remove_pending(PENDING_PATH)
    return {
        "schema": "fbsir.u3wObservationActivationResult.v1",
        "mode": "Apply",
        "state": "OBSERVATION_ACTIVE",
        "activationId": args.activation_id,
        "sourceCommit": args.source_commit,
        "activationReceiptPath": str(receipt_path),
        "activationReceiptSha256": sha256_file(receipt_path),
        "environmentAfterSha256": result[
            "environmentAfterSha256"
        ],
        "runtime": result["runtime"],
        "officialExpertsPackageChanged": False,
        "secretsDisclosed": False,
    }


def verify(args):
    release = deployed_release_evidence(args)
    raw, configuration = host_configuration(True)
    runtime = wait_for_runtime(True, timeout=5)
    receipt_path = (
        ACTIVATION_ROOT
        / args.activation_id
        / "activation-receipt.json"
    )
    validate_regular_file(receipt_path)
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    if (
        receipt.get("state") != "OBSERVATION_ACTIVE"
        or receipt.get("activationId") != args.activation_id
        or receipt.get("sourceCommit") != args.source_commit
        or receipt.get("workerSha256") != args.worker_sha
        or receipt.get("runnerSha256") != args.runner_sha
        or receipt.get("environmentAfterSha256")
        != sha256_bytes(raw.encode("utf-8"))
        or receipt.get("officialExpertsPackageChanged") is not False
        or PENDING_PATH.exists()
    ):
        raise RuntimeError("activation receipt or pending state drifted")
    return {
        "schema": "fbsir.u3wObservationActivationResult.v1",
        "mode": "Verify",
        "state": "OBSERVATION_ACTIVE_VERIFIED",
        "activationId": args.activation_id,
        "sourceCommit": args.source_commit,
        "activationReceiptSha256": sha256_file(receipt_path),
        "environmentAfterSha256": sha256_bytes(
            raw.encode("utf-8")
        ),
        "release": release,
        "configuration": configuration,
        "runtime": runtime,
        "officialExpertsPackageChanged": False,
        "secretsDisclosed": False,
    }


def recover(args):
    if os.geteuid() != 0:
        raise RuntimeError("observation recovery requires root")
    deployed_release_evidence(args)
    validate_regular_file(PENDING_PATH)
    pending = json.loads(PENDING_PATH.read_text(encoding="utf-8"))
    if (
        pending.get("schema")
        != "fbsir.u3wObservationActivationJournal.v1"
        or pending.get("activationId") != args.activation_id
        or pending.get("sourceCommit") != args.source_commit
    ):
        raise RuntimeError("pending activation identity drifted")
    backup_path = validate_regular_file(
        pending["environmentBackupPath"]
    )
    if (
        sha256_file(backup_path)
        != pending["environmentBackupSha256"]
        or sha256_file(backup_path)
        != pending["environmentBeforeSha256"]
    ):
        raise RuntimeError("pending activation backup drifted")
    activation_receipt_path = (
        ACTIVATION_ROOT
        / args.activation_id
        / "activation-receipt.json"
    )
    committed_recovery = None
    if activation_receipt_path.exists():
        try:
            validate_regular_file(activation_receipt_path)
            activation_receipt = json.loads(
                activation_receipt_path.read_text(encoding="utf-8")
            )
            raw, configuration = host_configuration(True)
            runtime = wait_for_runtime(True, timeout=5)
            if (
                activation_receipt.get("state")
                != "OBSERVATION_ACTIVE"
                or activation_receipt.get("activationId")
                != args.activation_id
                or activation_receipt.get("sourceCommit")
                != args.source_commit
                or activation_receipt.get(
                    "environmentAfterSha256"
                )
                != sha256_bytes(raw.encode("utf-8"))
                or activation_receipt.get(
                    "officialExpertsPackageChanged"
                )
                is not False
            ):
                raise RuntimeError(
                    "committed activation receipt drifted"
                )
            committed_recovery = {
                "runtime": runtime,
                "configuration": configuration,
                "activationReceiptSha256": sha256_file(
                    activation_receipt_path
                ),
                "environmentSha256": sha256_file(ENV_PATH),
            }
        except (
            OSError,
            ValueError,
            KeyError,
            TypeError,
            RuntimeError,
        ):
            # A commit is authoritative only when receipt, config and live
            # runtime all agree.  Otherwise recovery conservatively restores
            # the exact default-off preimage below.
            committed_recovery = None
    if committed_recovery is not None:
        commit_recovery_path = (
            ACTIVATION_ROOT
            / args.activation_id
            / "commit-recovery-receipt.json"
        )
        if commit_recovery_path.exists():
            validate_regular_file(commit_recovery_path)
            commit_recovery = json.loads(
                commit_recovery_path.read_text(encoding="utf-8")
            )
            if (
                commit_recovery.get("schema")
                != "fbsir.u3wObservationActivationRecovery.v1"
                or commit_recovery.get("state")
                != "OBSERVATION_ACTIVE_RECOVERED"
                or commit_recovery.get("activationId")
                != args.activation_id
                or commit_recovery.get("sourceCommit")
                != args.source_commit
                or commit_recovery.get(
                    "activationReceiptSha256"
                )
                != committed_recovery[
                    "activationReceiptSha256"
                ]
                or commit_recovery.get("environmentSha256")
                != committed_recovery["environmentSha256"]
                or commit_recovery.get(
                    "officialExpertsPackageChanged"
                )
                is not False
            ):
                raise RuntimeError(
                    "commit recovery receipt drifted"
                )
        else:
            commit_recovery = {
                "schema": (
                    "fbsir.u3wObservationActivationRecovery.v1"
                ),
                "mode": "Recover",
                "state": "OBSERVATION_ACTIVE_RECOVERED",
                "activationId": args.activation_id,
                "sourceCommit": args.source_commit,
                "recoveredAt": utc_now(),
                **committed_recovery,
                "officialExpertsPackageChanged": False,
                "secretsDisclosed": False,
            }
            atomic_create(
                commit_recovery_path,
                canonical_json(commit_recovery).encode("utf-8"),
            )
        remove_pending(PENDING_PATH)
        return commit_recovery
    atomic_replace_preserving_metadata(
        ENV_PATH, backup_path.read_bytes()
    )
    runtime = restart_and_verify(False)
    _, configuration = host_configuration(False)
    recovery_path = (
        ACTIVATION_ROOT
        / args.activation_id
        / "recovery-receipt.json"
    )
    recovery = {
        "schema": "fbsir.u3wObservationActivationRecovery.v1",
        "mode": "Recover",
        "state": "DEFAULT_OFF_RECOVERED",
        "activationId": args.activation_id,
        "sourceCommit": args.source_commit,
        "recoveredAt": utc_now(),
        "environmentSha256": sha256_file(ENV_PATH),
        "runtime": runtime,
        "configuration": configuration,
        "officialExpertsPackageChanged": False,
        "secretsDisclosed": False,
    }
    atomic_create(
        recovery_path, canonical_json(recovery).encode("utf-8")
    )
    remove_pending(PENDING_PATH)
    return recovery


def parse_args(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode",
        choices=("Plan", "Apply", "Verify", "Recover"),
        required=True,
    )
    parser.add_argument("--activation-id", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--runner-sha", required=True)
    parser.add_argument("--worker-sha", required=True)
    parser.add_argument(
        "--expected-environment-sha", default=ZERO_SHA
    )
    parser.add_argument("--expected-deployed-commit", required=True)
    parser.add_argument("--expected-release-id", required=True)
    parser.add_argument(
        "--expected-deployment-receipt-sha", required=True
    )
    parser.add_argument("--official-package-sha", required=True)
    parser.add_argument(
        "--authorization",
        choices=("explicit-user-authority", "read-only"),
        default="read-only",
    )
    args = parser.parse_args(argv)
    digests = (
        args.runner_sha,
        args.worker_sha,
        args.expected_environment_sha,
        args.expected_deployment_receipt_sha,
        args.official_package_sha,
    )
    if (
        not ACTIVATION_PATTERN.fullmatch(args.activation_id)
        or not COMMIT_PATTERN.fullmatch(args.source_commit)
        or not COMMIT_PATTERN.fullmatch(
            args.expected_deployed_commit
        )
        or not RELEASE_PATTERN.fullmatch(args.expected_release_id)
        or any(not SHA_PATTERN.fullmatch(value) for value in digests)
        or (
            args.mode == "Apply"
            and args.authorization != "explicit-user-authority"
        )
    ):
        parser.error("activation identity or digest shape is invalid")
    return args


def worker_error(args, error):
    return {
        "schema": "fbsir.u3wObservationActivationError.v1",
        "mode": args.mode,
        "activationId": args.activation_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "state": "FAILED_CLOSED",
        "errorType": type(error).__name__,
        "errorMessageSha256": sha256_bytes(
            str(error).encode("utf-8", errors="replace")
        ),
        "pendingRecoveryRequired": PENDING_PATH.exists(),
        "officialExpertsPackageChanged": False,
        "secretsDisclosed": False,
    }


def main(argv=None):
    args = parse_args(argv)
    descriptor = None
    try:
        if args.mode in {"Apply", "Recover"}:
            descriptor = open_lock()
        if args.mode == "Plan":
            result = plan(args)
        elif args.mode == "Apply":
            result = apply(args)
        elif args.mode == "Verify":
            result = verify(args)
        else:
            result = recover(args)
        print(canonical_json(result))
        return 0 if result.get("state") != "ROLLED_BACK" else 2
    except Exception as error:
        print(canonical_json(worker_error(args, error)))
        return 1
    finally:
        if descriptor is not None:
            os.close(descriptor)


if __name__ == "__main__":
    sys.exit(main())
