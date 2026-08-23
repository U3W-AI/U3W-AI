#!/usr/bin/env python3
"""Pure adapter and negative tests for w05e-nginx-public-deny.py."""

from __future__ import annotations

import datetime as dt
import importlib.util
import json
import pathlib
import stat
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "w05e_nginx_public_deny", ROOT / "scripts/w05e-nginx-public-deny.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


NOW = dt.datetime(2026, 8, 23, 12, 0, 0, tzinfo=dt.timezone.utc)


def service(unit: str, pid: int) -> dict[str, object]:
    return {"unit": unit, "activeState": "active", "subState": "running", "mainPid": pid, "nRestarts": 0}


def identity() -> dict[str, object]:
    return {
        "host": {"targetHost": MODULE.TARGET_HOST, "machineIdSha256": "a" * 64, "hostname": "api2"},
        "nginx": {"currentTarget": "/etc/nginx/snippets/api2-fbss.conf", "service": service(MODULE.NGINX_UNIT, 101)},
        "node": {"currentTarget": "/opt/fbss/phase1/releases/node-a", "service": service(MODULE.NODE_UNIT, 102)},
        "java": {"currentTarget": "/opt/fbsir/admin/releases/java-a", "service": service(MODULE.JAVA_UNIT, 103)},
    }


def authorization(plan, action: str, *, issued: dt.datetime = NOW - dt.timedelta(seconds=1), expires: dt.datetime = NOW + dt.timedelta(seconds=899), suffix: str = "abcdefgh") -> dict[str, object]:
    return {
        "schemaVersion": MODULE.AUTH_SCHEMA,
        "authorizationId": f"w05e-nginx-{action}-{suffix}",
        "action": action,
        "planSha256": MODULE.sha256_document(plan),
        "issuedAt": MODULE.format_time(issued),
        "expiresAt": MODULE.format_time(expires),
        "targetHost": MODULE.TARGET_HOST,
    }


class FakeRuntime:
    def __init__(self):
        self.target = MODULE.CANDIDATE_PATH.read_bytes()[:MODULE.PREIMAGE_BYTES]
        self.ident = identity()
        self.calls: list[str] = []
        self.nginx_failures = 0
        self.reload_failures = 0
        self.probe_failures = set()

    def read_target(self):
        return self.target

    def target_mode(self):
        return 0o644

    def write_target(self, data, mode):
        self.calls.append("write")
        self.target = data

    def restore_target(self, data, mode):
        self.calls.append("restore")
        self.target = data

    def identity_snapshot(self):
        return json.loads(json.dumps(self.ident))

    def nginx_test(self):
        self.calls.append("nginx-test")
        if self.nginx_failures:
            self.nginx_failures -= 1
            raise MODULE.RunnerError("nginx_test_failed", "nginx")

    def reload_nginx(self):
        self.calls.append("reload")
        if self.reload_failures:
            self.reload_failures -= 1
            raise MODULE.RunnerError("nginx_reload_failed", "nginx")

    def effective_dump(self):
        return MODULE.CANDIDATE_PATH.read_bytes()

    def probe(self, method, path):
        self.calls.append(f"probe-{method}")
        if method in self.probe_failures:
            return {"status": 200, "cacheControlNoStore": False}
        return {"status": 404, "cacheControlNoStore": True}


class W05ENginxPublicDenyTests(unittest.TestCase):
    def setUp(self):
        self.runtime = FakeRuntime()
        self.candidate = MODULE.CANDIDATE_PATH.read_bytes()
        self.temp = tempfile.TemporaryDirectory()
        self.state = pathlib.Path(self.temp.name)
        self.plan = MODULE.build_plan(self.runtime, candidate=self.candidate, observed_at=NOW)

    def tearDown(self):
        self.temp.cleanup()

    def test_candidate_hash_and_static_contract(self):
        descriptor = MODULE.validate_candidate(self.candidate)
        self.assertEqual(descriptor["sha256"], MODULE.CANDIDATE_SHA256)
        self.assertEqual(descriptor["httpStatus"], 404)
        self.assertTrue(descriptor["proxyPassForbidden"])

    def test_candidate_bom_crlf_and_mutated_bytes_fail_closed(self):
        for candidate in (b"\xef\xbb\xbf" + self.candidate, self.candidate.replace(b"\n", b"\r\n", 1), self.candidate + b"\n"):
            with self.assertRaises(MODULE.RunnerError) as context:
                MODULE.validate_candidate(candidate)
            self.assertIn(context.exception.code, {"candidate_hash_mismatch", "candidate_encoding_invalid"})

    def test_effective_dump_duplicate_wildcard_and_proxy_fail(self):
        with self.assertRaises(MODULE.RunnerError):
            MODULE.validate_effective_dump(self.candidate + self.candidate[self.candidate.find(b"location = /prod-api/internal"):])
        with self.assertRaises(MODULE.RunnerError):
            MODULE.validate_effective_dump(self.candidate.replace(b"location = /prod-api/internal", b"location ~ /prod-api/internal", 1))
        with self.assertRaises(MODULE.RunnerError):
            MODULE.validate_effective_dump(self.candidate.replace(b"return 404;", b"proxy_pass http://127.0.0.1:1;", 1))

    def test_live_probe_is_loopback_bound_and_uses_last_http_status(self):
        source = pathlib.Path(MODULE.__file__).read_text(encoding="utf-8")
        self.assertIn('"--noproxy", "*"', source)
        self.assertIn('f"{TARGET_HOST}:443:127.0.0.1"', source)
        self.assertIn("statuses[-1]", source)

    def test_preimage_drift_and_identity_drift_stop_before_write(self):
        self.runtime.target = b"drift"
        with self.assertRaises(MODULE.RunnerError) as context:
            MODULE.build_plan(self.runtime, candidate=self.candidate, observed_at=NOW)
        self.assertEqual(context.exception.code, "target_preimage_drift")
        self.runtime = FakeRuntime()
        self.runtime.ident["node"]["service"]["mainPid"] = 999
        with self.assertRaises(MODULE.RunnerError) as context:
            MODULE.apply_transaction(self.runtime, plan=self.plan, candidate=self.candidate, authorization=authorization(self.plan, "apply"), observed_at=NOW, state_root=self.state, consume_authorization=lambda: None)
        self.assertEqual(context.exception.code, "identity_drift_before_write")
        self.assertNotIn("write", self.runtime.calls)

    def test_apply_writes_atomic_backup_0600_and_preserves_runtime_identity(self):
        consumed = []
        receipt = MODULE.apply_transaction(self.runtime, plan=self.plan, candidate=self.candidate, authorization=authorization(self.plan, "apply"), observed_at=NOW, state_root=self.state, consume_authorization=lambda: consumed.append(True))
        self.assertEqual(receipt["status"], "PASS")
        self.assertEqual(consumed, [True])
        backup = pathlib.Path(receipt["backupPath"])
        if MODULE.os.name == "posix":
            self.assertEqual(stat.S_IMODE(backup.stat().st_mode), 0o600)
            self.assertEqual(stat.S_IMODE(backup.parent.stat().st_mode), 0o700)
        self.assertFalse(receipt["productCreditPromoted"])
        self.assertNotIn("restart", self.runtime.calls)

    def test_nginx_test_failure_reload_failure_and_probe_failure_restore_preimage(self):
        for setup in ("test", "reload", "probe"):
            runtime = FakeRuntime()
            if setup == "test": runtime.nginx_failures = 1
            if setup == "reload": runtime.reload_failures = 1
            if setup == "probe": runtime.probe_failures = {"GET"}
            with self.assertRaises(MODULE.RunnerError):
                MODULE.apply_transaction(runtime, plan=self.plan, candidate=self.candidate, authorization=authorization(self.plan, "apply", suffix=f"{setup}abcde"), observed_at=NOW, state_root=self.state / setup, consume_authorization=lambda: None)
            self.assertEqual(MODULE.sha256_bytes(runtime.target), MODULE.PREIMAGE_SHA256)
            self.assertIn("restore", runtime.calls)
            self.assertIn("reload", runtime.calls)

    def test_verify_is_read_only_and_rollback_requires_separate_auth(self):
        receipt = MODULE.apply_transaction(self.runtime, plan=self.plan, candidate=self.candidate, authorization=authorization(self.plan, "apply", suffix="applyabc"), observed_at=NOW, state_root=self.state, consume_authorization=lambda: None)
        before = list(self.runtime.calls)
        verified = MODULE.verify_transaction(self.runtime, plan=self.plan, receipt=receipt, observed_at=NOW)
        self.assertEqual(verified["status"], "PASS")
        self.assertNotIn("write", self.runtime.calls[len(before):])
        with self.assertRaises(MODULE.RunnerError) as context:
            MODULE.validate_authorization(authorization(self.plan, "apply"), action="rollback", plan_sha256=MODULE.sha256_document(self.plan), observed_at=NOW)
        self.assertEqual(context.exception.code, "authorization_shape_invalid")
        result = MODULE.rollback_transaction(self.runtime, plan=self.plan, receipt=receipt, authorization=authorization(self.plan, "rollback", suffix="rollbacx"), observed_at=NOW, state_root=self.state, consume_authorization=lambda: None)
        self.assertEqual(result["restoredSha256"], MODULE.PREIMAGE_SHA256)

    def test_expired_authorization_and_consumed_authorization_fail(self):
        with self.assertRaises(MODULE.RunnerError) as context:
            MODULE.validate_authorization(authorization(self.plan, "apply", issued=NOW - dt.timedelta(minutes=16), expires=NOW - dt.timedelta(minutes=1)), action="apply", plan_sha256=MODULE.sha256_document(self.plan), observed_at=NOW)
        self.assertEqual(context.exception.code, "authorization_expired")
        path = self.state / "authorization.json"
        path.write_bytes(MODULE.canonical_json(authorization(self.plan, "apply")))
        used = path.with_name("w05e-nginx-apply-abcdefgh.used.json")
        used.write_bytes(b"used")
        with self.assertRaises(MODULE.RunnerError) as context:
            MODULE.consume_auth_file(path, "w05e-nginx-apply-abcdefgh")
        self.assertEqual(context.exception.code, "authorization_already_used")

    def test_cli_mutation_requires_explicit_production_flag(self):
        with self.assertRaises(MODULE.RunnerError) as context:
            MODULE.require_mutation_gate(False)
        self.assertEqual(context.exception.code, "production_flag_required")


if __name__ == "__main__":
    unittest.main()
