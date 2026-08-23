#!/usr/bin/env python3
"""W05E exact public readback deny plan/apply/verify/rollback runner.

The default mode is read-only.  Production mutation requires Linux root,
``--execute-production`` and a one-time authorization valid for at most
15 minutes.  The runner never prints an nginx effective dump or response
bodies; only hashes, statuses and bounded identity facts enter receipts.
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
import shutil
import socket
import stat
import subprocess
import sys
import tempfile
import time
from typing import Any, Callable, Mapping

try:
    import fcntl
except ModuleNotFoundError:  # pragma: no cover - Windows pure tests
    fcntl = None


PLAN_SCHEMA = "fbsir.w05eNginxPublicDenyPlan.v1"
AUTH_SCHEMA = "fbsir.w05eNginxPublicDenyAuthorization.v1"
APPLY_SCHEMA = "fbsir.w05eNginxPublicDenyApplyReceipt.v1"
VERIFY_SCHEMA = "fbsir.w05eNginxPublicDenyVerifyReceipt.v1"
ROLLBACK_SCHEMA = "fbsir.w05eNginxPublicDenyRollbackReceipt.v1"
TARGET_HOST = "api2.u3w.com"
TARGET_PATH = pathlib.Path("/etc/nginx/snippets/api2-fbss.conf")
CANDIDATE_PATH = pathlib.Path(__file__).resolve().parents[1] / "ops/api2/nginx/api2-fbss-w05e.conf"
PREIMAGE_SHA256 = "21f7f6d12c94d2e7437bc5878a8fbd48073acad17b37cf6a8d8fb047180b5cd5"
PREIMAGE_BYTES = 3591
CANDIDATE_SHA256 = "357cfadc900f576ce0f0ab8ddc93f8e79e79469d075bb0203ea012d398e5cb32"
CANDIDATE_BYTES = 3896
NODE_UNIT = "fbss-phase1.service"
JAVA_UNIT = "fbsir-admin.service"
NGINX_UNIT = "nginx.service"
STATE_ROOT = pathlib.Path("/var/lib/u3w/w05e-nginx-public-deny")
LOCK_PATH = STATE_ROOT / ".lock"
AUTH_ID_RE = re.compile(r"w05e-nginx-(?:apply|rollback)-[a-z0-9][a-z0-9-]{7,95}\Z")
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z")
PATH_RE = re.escape("/prod-api/internal/independent-board/attribution/events/readback")


class RunnerError(RuntimeError):
    def __init__(self, code: str, path: str, detail: str | None = None):
        super().__init__(f"{code} at {path}")
        self.code = code
        self.path = path
        self.detail = detail

    def public(self) -> dict[str, str]:
        return {"status": "FAIL_CLOSED", "code": self.code, "path": self.path}


def fail(code: str, path: str, detail: str | None = None) -> None:
    raise RunnerError(code, path, detail)


def now_utc() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def format_time(value: dt.datetime) -> str:
    if not isinstance(value, dt.datetime) or value.tzinfo is None:
        fail("invalid_timestamp", "timestamp")
    value = value.astimezone(dt.timezone.utc)
    return value.isoformat(timespec="seconds").replace("+00:00", "Z")


def parse_time(value: Any, path: str) -> dt.datetime:
    if not isinstance(value, str) or not value.endswith("Z"):
        fail("invalid_timestamp", path)
    try:
        parsed = dt.datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError:
        fail("invalid_timestamp", path)
    if parsed.tzinfo != dt.timezone.utc or format_time(parsed) != value:
        fail("noncanonical_timestamp", path)
    return parsed


def canonical_json(value: Any) -> bytes:
    try:
        return (json.dumps(value, ensure_ascii=False, allow_nan=False,
                           sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
    except (TypeError, ValueError):
        fail("invalid_json_value", "document")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_document(value: Any) -> str:
    return sha256_bytes(canonical_json(value))


def require_mapping(value: Any, path: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping) or any(not isinstance(key, str) for key in value):
        fail("invalid_object", path)
    return value


def require_sha(value: Any, path: str) -> str:
    if not isinstance(value, str) or SHA256_RE.fullmatch(value) is None:
        fail("invalid_sha256", path)
    return value


def validate_candidate(candidate: bytes) -> dict[str, Any]:
    if not isinstance(candidate, bytes):
        fail("candidate_not_bytes", "candidate")
    if len(candidate) != CANDIDATE_BYTES or sha256_bytes(candidate) != CANDIDATE_SHA256:
        fail("candidate_hash_mismatch", "candidate")
    if len(candidate) < PREIMAGE_BYTES or sha256_bytes(candidate[:PREIMAGE_BYTES]) != PREIMAGE_SHA256:
        fail("candidate_preimage_prefix_mismatch", "candidate")
    if candidate.startswith(b"\xef\xbb\xbf") or b"\r" in candidate or b"\x00" in candidate:
        fail("candidate_encoding_invalid", "candidate")
    try:
        text = candidate.decode("utf-8")
    except UnicodeDecodeError:
        fail("candidate_encoding_invalid", "candidate")
    path_count = len(re.findall(PATH_RE, text))
    exact_locations = re.findall(rf"location\s*=\s*{PATH_RE}\s*\{{", text)
    wildcard_locations = re.findall(rf"location\s+[~^]?\s*{PATH_RE}", text)
    if path_count != 1 or len(exact_locations) != 1:
        fail("candidate_duplicate_or_missing_exact_location", "candidate")
    if wildcard_locations:
        fail("candidate_wildcard_location", "candidate")
    start = text.index(exact_locations[0])
    block = text[start:]
    close = block.find("\n}")
    if close < 0:
        fail("candidate_location_unclosed", "candidate")
    block = block[:close + 2]
    if not re.search(r'add_header\s+Cache-Control\s+"no-store"\s+always\s*;', block):
        fail("candidate_no_store_missing", "candidate")
    if not re.search(r"return\s+404\s*;", block):
        fail("candidate_404_missing", "candidate")
    if re.search(r"(?:proxy_pass|proxy_|alias|\broot\s)", block, re.IGNORECASE):
        fail("candidate_upstream_or_filesystem_directive", "candidate")
    return {
        "path": TARGET_PATH.as_posix(),
        "sha256": CANDIDATE_SHA256,
        "bytes": CANDIDATE_BYTES,
        "method": "all",
        "publicPath": "/prod-api/internal/independent-board/attribution/events/readback",
        "httpStatus": 404,
        "cacheControl": "no-store",
        "locationMatch": "exact",
        "proxyPassForbidden": True,
    }


def exact_dict(value: Mapping[str, Any], keys: set[str], path: str) -> None:
    if set(value) != keys:
        fail("invalid_shape", path)


def service_identity(value: Any, unit: str, path: str) -> dict[str, Any]:
    value = dict(require_mapping(value, path))
    exact_dict(value, {"unit", "activeState", "subState", "mainPid", "nRestarts"}, path)
    if value["unit"] != unit or value["activeState"] != "active" or value["subState"] != "running":
        fail("service_not_active", path)
    if type(value["mainPid"]) is not int or value["mainPid"] <= 0:
        fail("service_pid_invalid", path)
    if type(value["nRestarts"]) is not int or value["nRestarts"] < 0:
        fail("service_restart_counter_invalid", path)
    return value


def validate_identity(value: Any, path: str = "identity") -> dict[str, Any]:
    identity = dict(require_mapping(value, path))
    exact_dict(identity, {"host", "nginx", "node", "java"}, path)
    host = dict(require_mapping(identity["host"], f"{path}.host"))
    exact_dict(host, {"targetHost", "machineIdSha256", "hostname"}, f"{path}.host")
    if host["targetHost"] != TARGET_HOST:
        fail("identity_target_host_mismatch", f"{path}.host")
    require_sha(host["machineIdSha256"], f"{path}.host.machineIdSha256")
    if not isinstance(host["hostname"], str) or not host["hostname"]:
        fail("identity_hostname_missing", f"{path}.host.hostname")
    for key, unit in (("nginx", NGINX_UNIT), ("node", NODE_UNIT), ("java", JAVA_UNIT)):
        service_identity(identity[key]["service"], unit, f"{path}.{key}.service")
        target = identity[key].get("currentTarget")
        if key != "nginx" and (not isinstance(target, str) or not target.startswith("/")):
            fail("identity_current_target_invalid", f"{path}.{key}.currentTarget")
    return identity


def identities_equal(before: Mapping[str, Any], after: Mapping[str, Any]) -> bool:
    return json.loads(canonical_json(before)) == json.loads(canonical_json(after))


def validate_plan(value: Any) -> dict[str, Any]:
    plan = dict(require_mapping(value, "plan"))
    exact_dict(plan, {"schemaVersion", "planId", "createdAt", "candidate", "target", "identity", "policy"}, "plan")
    if plan["schemaVersion"] != PLAN_SCHEMA or not isinstance(plan["planId"], str) or not plan["planId"].startswith("w05e-nginx-deny-"):
        fail("plan_identity_invalid", "plan")
    parse_time(plan["createdAt"], "plan.createdAt")
    validate_candidate_descriptor(plan["candidate"])
    target = dict(require_mapping(plan["target"], "plan.target"))
    exact_dict(target, {"path", "preimageSha256", "preimageBytes"}, "plan.target")
    if target["path"] != TARGET_PATH.as_posix() or target["preimageBytes"] != PREIMAGE_BYTES:
        fail("plan_target_invalid", "plan.target")
    require_sha(target["preimageSha256"], "plan.target.preimageSha256")
    validate_identity(plan["identity"], "plan.identity")
    policy = dict(require_mapping(plan["policy"], "plan.policy"))
    if policy != {"productionReleaseAllowed": False, "productCreditPromoted": False}:
        fail("plan_policy_invalid", "plan.policy")
    return plan


def validate_candidate_descriptor(value: Any) -> dict[str, Any]:
    descriptor = dict(require_mapping(value, "candidate"))
    expected = {"path", "sha256", "bytes", "method", "publicPath", "httpStatus", "cacheControl", "locationMatch", "proxyPassForbidden"}
    exact_dict(descriptor, expected, "candidate")
    if descriptor["path"] != "ops/api2/nginx/api2-fbss-w05e.conf" or descriptor["bytes"] != CANDIDATE_BYTES:
        fail("candidate_descriptor_invalid", "candidate")
    if descriptor["method"] != "all" or descriptor["publicPath"] != "/prod-api/internal/independent-board/attribution/events/readback" or descriptor["httpStatus"] != 404 or descriptor["cacheControl"] != "no-store" or descriptor["locationMatch"] != "exact" or descriptor["proxyPassForbidden"] is not True:
        fail("candidate_descriptor_invalid", "candidate")
    require_sha(descriptor["sha256"], "candidate.sha256")
    return descriptor


def build_plan(runtime: Any, *, candidate: bytes, observed_at: dt.datetime) -> dict[str, Any]:
    descriptor = validate_candidate(candidate)
    target = runtime.read_target()
    if len(target) != PREIMAGE_BYTES or sha256_bytes(target) != PREIMAGE_SHA256:
        fail("target_preimage_drift", "target")
    identity = validate_identity(runtime.identity_snapshot())
    plan = {
        "schemaVersion": PLAN_SCHEMA,
        "planId": f"w05e-nginx-deny-{sha256_bytes(candidate + target)[:24]}",
        "createdAt": format_time(observed_at),
        "candidate": {**descriptor, "path": "ops/api2/nginx/api2-fbss-w05e.conf"},
        "target": {"path": TARGET_PATH.as_posix(), "preimageSha256": PREIMAGE_SHA256, "preimageBytes": PREIMAGE_BYTES},
        "identity": identity,
        "policy": {"productionReleaseAllowed": False, "productCreditPromoted": False},
    }
    return validate_plan(plan)


def validate_authorization(value: Any, *, action: str, plan_sha256: str, observed_at: dt.datetime) -> dict[str, Any]:
    authorization = dict(require_mapping(value, "authorization"))
    exact_dict(authorization, {"schemaVersion", "authorizationId", "action", "planSha256", "issuedAt", "expiresAt", "targetHost"}, "authorization")
    if authorization["schemaVersion"] != AUTH_SCHEMA or authorization["action"] != action or authorization["targetHost"] != TARGET_HOST:
        fail("authorization_shape_invalid", "authorization")
    if not isinstance(authorization["authorizationId"], str) or AUTH_ID_RE.fullmatch(authorization["authorizationId"]) is None:
        fail("authorization_id_invalid", "authorization.authorizationId")
    if authorization["planSha256"] != plan_sha256:
        fail("authorization_plan_mismatch", "authorization.planSha256")
    issued = parse_time(authorization["issuedAt"], "authorization.issuedAt")
    expires = parse_time(authorization["expiresAt"], "authorization.expiresAt")
    if expires <= issued or (expires - issued).total_seconds() > 900:
        fail("authorization_window_invalid", "authorization")
    if observed_at < issued or observed_at >= expires:
        fail("authorization_expired", "authorization")
    return authorization


def ensure_private_dir(path: pathlib.Path, *, enforce_mode: bool = True) -> None:
    path.mkdir(parents=True, exist_ok=True)
    if enforce_mode:
        os.chmod(path, 0o700)
    for parent in [path.parent]:
        try:
            descriptor = os.open(parent, os.O_RDONLY)
        except OSError:
            if os.name == "posix":
                raise
            continue
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)


def write_private(path: pathlib.Path, data: bytes, mode: int = 0o600, *, private_parent: bool = True) -> None:
    ensure_private_dir(path.parent, enforce_mode=private_parent)
    fd, temp = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        os.chmod(temp, mode)
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp, path)
        try:
            descriptor = os.open(path.parent, os.O_RDONLY)
        except OSError:
            if os.name == "posix":
                raise
        else:
            try:
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
    finally:
        with contextlib.suppress(FileNotFoundError):
            os.unlink(temp)


def read_canonical_json(path: pathlib.Path, *, mode: int = 0o600) -> tuple[dict[str, Any], bytes]:
    status = path.lstat()
    if not stat.S_ISREG(status.st_mode) or stat.S_IMODE(status.st_mode) != mode:
        fail("private_file_mode_invalid", path.name)
    data = path.read_bytes()
    try:
        value = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        fail("invalid_json_file", path.name)
    if canonical_json(value) != data:
        fail("noncanonical_json_file", path.name)
    return dict(require_mapping(value, path.name)), data


def target_backup_path(state_root: pathlib.Path, plan_id: str) -> pathlib.Path:
    return state_root / "backups" / f"{plan_id}.preimage"


def validate_effective_dump(dump: bytes) -> dict[str, Any]:
    if not isinstance(dump, bytes):
        fail("effective_dump_not_bytes", "nginxDump")
    try:
        text = dump.decode("utf-8")
    except UnicodeDecodeError:
        fail("effective_dump_encoding_invalid", "nginxDump")
    exact_count = len(re.findall(rf"location\s*=\s*{PATH_RE}\s*\{{", text))
    if exact_count != 1:
        fail("effective_dump_exact_location_count_invalid", "nginxDump")
    start = text.find(f"location = /prod-api/internal/independent-board/attribution/events/readback")
    if start < 0:
        fail("effective_dump_exact_location_missing", "nginxDump")
    block = text[start:start + 1000]
    if re.search(r"(?:proxy_pass|proxy_|alias|\broot\s)", block, re.IGNORECASE):
        fail("effective_dump_readback_proxy_present", "nginxDump")
    if not re.search(r"add_header\s+Cache-Control\s+\"no-store\"\s+always\s*;", block):
        fail("effective_dump_no_store_missing", "nginxDump")
    if not re.search(r"return\s+404\s*;", block):
        fail("effective_dump_404_missing", "nginxDump")
    return {"sha256": sha256_bytes(dump), "bytes": len(dump), "exactLocationCount": exact_count}


def validate_probes(probes: Any) -> dict[str, Any]:
    if not isinstance(probes, Mapping) or set(probes) != {"GET", "POST"}:
        fail("probe_shape_invalid", "probes")
    result = {}
    for method in ("GET", "POST"):
        item = dict(require_mapping(probes[method], f"probes.{method}"))
        exact_dict(item, {"status", "cacheControlNoStore"}, f"probes.{method}")
        if item["status"] != 404 or item["cacheControlNoStore"] is not True:
            fail("public_deny_probe_failed", f"probes.{method}")
        result[method] = item
    return result


def apply_transaction(runtime: Any, *, plan: Mapping[str, Any], candidate: bytes,
                      authorization: Mapping[str, Any], observed_at: dt.datetime,
                      state_root: pathlib.Path, consume_authorization: Callable[[], None],
                      settle_seconds: int = 0) -> dict[str, Any]:
    plan = validate_plan(plan)
    validate_candidate(candidate)
    validate_authorization(authorization, action="apply", plan_sha256=sha256_document(plan), observed_at=observed_at)
    if not identities_equal(plan["identity"], validate_identity(runtime.identity_snapshot())):
        fail("identity_drift_before_write", "identity")
    preimage = runtime.read_target()
    if len(preimage) != PREIMAGE_BYTES or sha256_bytes(preimage) != PREIMAGE_SHA256:
        fail("target_preimage_drift", "target")
    backup = target_backup_path(state_root, plan["planId"])
    write_private(backup, preimage, 0o600)
    original_identity = plan["identity"]
    try:
        consume_authorization()
        runtime.write_target(candidate, runtime.target_mode())
        runtime.nginx_test()
        runtime.reload_nginx()
        if settle_seconds:
            time.sleep(settle_seconds)
        effective = validate_effective_dump(runtime.effective_dump())
        probes = validate_probes({method: runtime.probe(method, plan["candidate"]["publicPath"]) for method in ("GET", "POST")})
        after = validate_identity(runtime.identity_snapshot())
        if not identities_equal(original_identity, after):
            fail("identity_drift_after_reload", "identity")
        receipt = {
            "schemaVersion": APPLY_SCHEMA,
            "status": "PASS",
            "completedAt": format_time(observed_at),
            "planId": plan["planId"],
            "planSha256": sha256_document(plan),
            "candidateSha256": CANDIDATE_SHA256,
            "preimageSha256": PREIMAGE_SHA256,
            "backupPath": backup.as_posix(),
            "backupSha256": sha256_bytes(preimage),
            "effectiveDump": effective,
            "probes": probes,
            "identityBefore": original_identity,
            "identityAfter": after,
            "nodeJavaRestartsChanged": False,
            "nginxReloaded": True,
            "naturalTrafficPromoted": False,
            "productCreditPromoted": False,
        }
        write_private(state_root / "receipts" / f"{plan['planId']}.apply.json", canonical_json(receipt))
        return receipt
    except RunnerError:
        _automatic_restore(runtime, preimage)
        raise
    except Exception:
        _automatic_restore(runtime, preimage)
        fail("apply_failed_rolled_back", "production")


def _automatic_restore(runtime: Any, preimage: bytes) -> None:
    try:
        runtime.restore_target(preimage, runtime.target_mode())
        runtime.nginx_test()
        runtime.reload_nginx()
    except Exception:
        fail("automatic_rollback_failed", "target")


def verify_transaction(runtime: Any, *, plan: Mapping[str, Any], receipt: Mapping[str, Any], observed_at: dt.datetime) -> dict[str, Any]:
    plan = validate_plan(plan)
    receipt = dict(require_mapping(receipt, "receipt"))
    if receipt.get("schemaVersion") != APPLY_SCHEMA or receipt.get("status") != "PASS" or receipt.get("planId") != plan["planId"] or receipt.get("planSha256") != sha256_document(plan):
        fail("apply_receipt_binding_invalid", "receipt")
    current = runtime.read_target()
    if sha256_bytes(current) != CANDIDATE_SHA256:
        fail("candidate_not_installed", "target")
    effective = validate_effective_dump(runtime.effective_dump())
    if effective["sha256"] != receipt.get("effectiveDump", {}).get("sha256"):
        fail("effective_dump_hash_drift", "nginxDump")
    probes = validate_probes({method: runtime.probe(method, plan["candidate"]["publicPath"]) for method in ("GET", "POST")})
    identity = validate_identity(runtime.identity_snapshot())
    if not identities_equal(receipt["identityAfter"], identity):
        fail("identity_drift_after_apply", "identity")
    return {
        "schemaVersion": VERIFY_SCHEMA,
        "status": "PASS",
        "verifiedAt": format_time(observed_at),
        "planId": plan["planId"],
        "candidateSha256": CANDIDATE_SHA256,
        "effectiveDump": effective,
        "probes": probes,
        "identity": identity,
        "productionChanged": False,
        "naturalTrafficPromoted": False,
        "productCreditPromoted": False,
    }


def rollback_transaction(runtime: Any, *, plan: Mapping[str, Any], receipt: Mapping[str, Any], authorization: Mapping[str, Any], observed_at: dt.datetime, state_root: pathlib.Path, consume_authorization: Callable[[], None]) -> dict[str, Any]:
    plan = validate_plan(plan)
    receipt = dict(require_mapping(receipt, "receipt"))
    if receipt.get("schemaVersion") != APPLY_SCHEMA or receipt.get("status") != "PASS" or receipt.get("planId") != plan["planId"]:
        fail("apply_receipt_binding_invalid", "receipt")
    validate_authorization(authorization, action="rollback", plan_sha256=sha256_document(plan), observed_at=observed_at)
    before = validate_identity(runtime.identity_snapshot())
    if not identities_equal(receipt["identityAfter"], before):
        fail("identity_drift_before_rollback", "identity")
    current = runtime.read_target()
    if sha256_bytes(current) != CANDIDATE_SHA256:
        fail("rollback_candidate_preimage_drift", "target")
    backup = pathlib.Path(receipt["backupPath"])
    if backup != target_backup_path(state_root, plan["planId"]):
        fail("rollback_backup_path_invalid", "backup")
    try:
        backup_status = backup.lstat()
    except FileNotFoundError:
        fail("rollback_backup_missing", "backup")
    if not stat.S_ISREG(backup_status.st_mode) or (os.name == "posix" and stat.S_IMODE(backup_status.st_mode) != 0o600):
        fail("rollback_backup_mode_invalid", "backup")
    preimage = backup.read_bytes()
    if sha256_bytes(preimage) != PREIMAGE_SHA256:
        fail("rollback_backup_hash_invalid", "backup")
    consume_authorization()
    runtime.restore_target(preimage, runtime.target_mode())
    runtime.nginx_test()
    runtime.reload_nginx()
    after = validate_identity(runtime.identity_snapshot())
    if not identities_equal(before, after):
        fail("identity_drift_after_rollback", "identity")
    result = {
        "schemaVersion": ROLLBACK_SCHEMA,
        "status": "PASS",
        "completedAt": format_time(observed_at),
        "planId": plan["planId"],
        "restoredSha256": PREIMAGE_SHA256,
        "identityBefore": before,
        "identityAfter": after,
        "nodeJavaRestartsChanged": False,
        "nginxReloaded": True,
        "naturalTrafficPromoted": False,
        "productCreditPromoted": False,
    }
    write_private(state_root / "receipts" / f"{plan['planId']}.rollback.json", canonical_json(result))
    return result


class LiveRuntime:
    def _run(self, args: list[str], *, input_bytes: bytes | None = None, timeout: int = 60) -> subprocess.CompletedProcess[bytes]:
        try:
            result = subprocess.run(args, input=input_bytes, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout, check=False)
        except (OSError, subprocess.SubprocessError):
            fail("command_execution_failed", pathlib.Path(args[0]).name)
        if result.returncode != 0:
            fail("command_failed", pathlib.Path(args[0]).name)
        return result

    def read_target(self) -> bytes:
        return TARGET_PATH.read_bytes()

    def target_mode(self) -> int:
        return stat.S_IMODE(TARGET_PATH.stat().st_mode)

    def write_target(self, data: bytes, mode: int) -> None:
        self._atomic_target(data, mode)

    def restore_target(self, data: bytes, mode: int) -> None:
        self._atomic_target(data, mode)

    def _atomic_target(self, data: bytes, mode: int) -> None:
        fd, temporary = tempfile.mkstemp(prefix=f".{TARGET_PATH.name}.", dir=TARGET_PATH.parent)
        try:
            os.chmod(temporary, mode)
            with os.fdopen(fd, "wb") as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, TARGET_PATH)
            descriptor = os.open(TARGET_PATH.parent, os.O_RDONLY)
            try:
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
        finally:
            with contextlib.suppress(FileNotFoundError):
                os.unlink(temporary)

    def service(self, unit: str) -> dict[str, Any]:
        result = self._run(["/usr/bin/systemctl", "show", unit, "-p", "ActiveState", "-p", "SubState", "-p", "MainPID", "-p", "NRestarts"])
        values: dict[str, Any] = {}
        for line in result.stdout.decode("utf-8", "strict").splitlines():
            if "=" in line:
                key, value = line.split("=", 1)
                values[key] = int(value) if value.isdigit() else value
        return {"unit": unit, "activeState": values.get("ActiveState"), "subState": values.get("SubState"), "mainPid": values.get("MainPID"), "nRestarts": values.get("NRestarts")}

    def identity_snapshot(self) -> dict[str, Any]:
        machine_id = pathlib.Path("/etc/machine-id").read_bytes().strip()
        node_target = pathlib.Path("/opt/fbss/phase1/current").resolve(strict=True)
        java_target = pathlib.Path("/opt/fbsir/admin/current").resolve(strict=True)
        return {
            "host": {"targetHost": TARGET_HOST, "machineIdSha256": sha256_bytes(machine_id), "hostname": socket.getfqdn()},
            "nginx": {"service": self.service(NGINX_UNIT), "currentTarget": "/etc/nginx/snippets/api2-fbss.conf"},
            "node": {"currentTarget": str(node_target), "service": self.service(NODE_UNIT)},
            "java": {"currentTarget": str(java_target), "service": self.service(JAVA_UNIT)},
        }

    def nginx_test(self) -> None:
        self._run(["/usr/sbin/nginx", "-t"], timeout=60)

    def reload_nginx(self) -> None:
        self._run(["/usr/bin/systemctl", "reload", NGINX_UNIT], timeout=60)
        # systemctl confirms the master accepted the reload signal, but old
        # workers may still win an immediate connection. Give the new worker
        # generation a bounded handover interval before public probes.
        time.sleep(2)

    def effective_dump(self) -> bytes:
        result = self._run(["/usr/sbin/nginx", "-T"], timeout=60)
        return result.stdout + result.stderr

    def probe(self, method: str, path: str) -> dict[str, Any]:
        result = self._run([
            "/usr/bin/curl", "--silent", "--show-error", "--insecure",
            "--noproxy", "*", "--resolve", f"{TARGET_HOST}:443:127.0.0.1",
            "--max-time", "15", "--dump-header", "-", "--output", "/dev/null",
            "--request", method, f"https://{TARGET_HOST}{path}",
        ], timeout=30)
        text = result.stdout.decode("iso-8859-1", "strict")
        statuses = re.findall(r"HTTP/\d(?:\.\d)?\s+(\d{3})", text)
        cache = re.search(r"^Cache-Control:\s*([^\r\n]+)", text, re.IGNORECASE | re.MULTILINE)
        return {"status": int(statuses[-1]) if statuses else None, "cacheControlNoStore": bool(cache and "no-store" in cache.group(1).lower())}


def consume_auth_file(path: pathlib.Path, authorization_id: str) -> None:
    used = path.with_name(f"{authorization_id}.used.json")
    if used.exists():
        fail("authorization_already_used", "authorization")
    try:
        os.replace(path, used)
    except FileNotFoundError:
        fail("authorization_missing_or_used", "authorization")


def require_mutation_gate(execute: bool) -> None:
    if execute is not True:
        fail("production_flag_required", "cli")
    if os.name != "posix" or not hasattr(os, "geteuid") or os.geteuid() != 0 or fcntl is None:
        fail("linux_root_required", "cli")


def load_json(path: pathlib.Path) -> tuple[dict[str, Any], bytes]:
    status = path.lstat()
    if not stat.S_ISREG(status.st_mode) or stat.S_IMODE(status.st_mode) != 0o600:
        fail("private_file_mode_invalid", path.name)
    data = path.read_bytes()
    try:
        value = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        fail("invalid_json_file", path.name)
    if canonical_json(value) != data:
        fail("noncanonical_json_file", path.name)
    return dict(require_mapping(value, path.name)), data


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="W05E Nginx public deny runner")
    commands = parser.add_subparsers(dest="command", required=True)
    plan = commands.add_parser("plan")
    plan.add_argument("--output", required=True)
    apply = commands.add_parser("apply")
    apply.add_argument("--plan", required=True)
    apply.add_argument("--authorization", required=True)
    apply.add_argument("--execute-production", action="store_true")
    verify = commands.add_parser("verify")
    verify.add_argument("--plan", required=True)
    verify.add_argument("--receipt", required=True)
    rollback = commands.add_parser("rollback")
    rollback.add_argument("--plan", required=True)
    rollback.add_argument("--receipt", required=True)
    rollback.add_argument("--authorization", required=True)
    rollback.add_argument("--execute-production", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    try:
        args = build_parser().parse_args(argv)
        runtime = LiveRuntime()
        observed = now_utc()
        if args.command == "plan":
            plan = build_plan(runtime, candidate=CANDIDATE_PATH.read_bytes(), observed_at=observed)
            output = pathlib.Path(args.output).resolve()
            write_private(output, canonical_json(plan), 0o600, private_parent=False)
            result = {"status": "PLANNED_READ_ONLY", "planId": plan["planId"], "planSha256": sha256_document(plan), "output": output.as_posix(), "productionChanged": False}
        elif args.command == "verify":
            plan, _ = load_json(pathlib.Path(args.plan))
            receipt, _ = load_json(pathlib.Path(args.receipt))
            result = verify_transaction(runtime, plan=plan, receipt=receipt, observed_at=observed)
        else:
            require_mutation_gate(args.execute_production)
            plan, _ = load_json(pathlib.Path(args.plan))
            authorization_path = pathlib.Path(args.authorization)
            authorization, _ = load_json(authorization_path)
            ensure_private_dir(STATE_ROOT)
            descriptor = os.open(LOCK_PATH, os.O_CREAT | os.O_RDWR, 0o600)
            try:
                fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
                validated = validate_authorization(authorization, action=args.command, plan_sha256=sha256_document(validate_plan(plan)), observed_at=observed)
                if args.command == "apply":
                    result = apply_transaction(runtime, plan=plan, candidate=CANDIDATE_PATH.read_bytes(), authorization=authorization, observed_at=observed, state_root=STATE_ROOT, consume_authorization=lambda: consume_auth_file(authorization_path, validated["authorizationId"]))
                else:
                    receipt, _ = load_json(pathlib.Path(args.receipt))
                    result = rollback_transaction(runtime, plan=plan, receipt=receipt, authorization=authorization, observed_at=observed, state_root=STATE_ROOT, consume_authorization=lambda: consume_auth_file(authorization_path, validated["authorizationId"]))
            finally:
                with contextlib.suppress(OSError):
                    fcntl.flock(descriptor, fcntl.LOCK_UN)
                os.close(descriptor)
        sys.stdout.buffer.write(canonical_json(result))
        return 0
    except RunnerError as error:
        sys.stderr.buffer.write(canonical_json(error.public()))
        return 2
    except Exception:
        sys.stderr.buffer.write(canonical_json({"status": "FAIL_CLOSED", "code": "unhandled_failure", "path": "runner"}))
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
