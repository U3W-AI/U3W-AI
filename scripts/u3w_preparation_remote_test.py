import base64
import datetime as dt
import importlib.util
import inspect
import json
import pathlib
import re
import sys
import types
import unittest


ROOT = pathlib.Path(__file__).resolve().parent
if "fcntl" not in sys.modules:
    sys.modules["fcntl"] = types.SimpleNamespace(
        LOCK_EX=2, LOCK_UN=8, flock=lambda *_: None
    )


def load_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


baseline = load_module(
    "u3w_legacy_baseline_remote",
    "u3w-legacy-baseline-remote.py",
)
configuration = load_module(
    "u3w_default_off_configuration_remote",
    "u3w-default-off-configuration-remote.py",
)


class Args:
    mode = "Apply"
    run_id = "w1a-baseline-20260723T180000Z-0123456789ab"
    source_commit = "a" * 40
    approval_sha = ""
    approval_json_base64 = ""
    runner_sha = "b" * 64
    worker_sha = "c" * 64
    expected_backup_bundle_sha = "d" * 64
    expected_adoption_receipt_sha = "0" * 64
    original_approval_sha = "0" * 64


def approval(
    action,
    database_write,
    run_id=Args.run_id,
    baseline_bindings=False,
):
    payload = {
        "schema": "fbsir.u3wProductionChangeApprovalReceipt.v1",
        "action": action,
        "targetHost": "api2.u3w.com",
        "runId": run_id,
        "sourceCommit": Args.source_commit,
        "approvedAt": "2026-07-23T17:50:00Z",
        "expiresAt": "2026-07-23T18:50:00Z",
        "authorizedBy": "workspace-user",
        "concurrentDdlProhibited": True,
        "productionFilesystemWrite": True,
        "productionDatabaseWrite": database_write,
        "productionServiceChange": False,
        "officialExpertsPackageChange": False,
    }
    if baseline_bindings:
        payload.update(
            {
                "database": "fbsir",
                "databaseEndpoint": "127.0.0.1:3306",
                "databaseServerUuid": (
                    "01234567-89ab-cdef-0123-456789abcdef"
                ),
                "expectedBackupBundleReceiptSha256": (
                    Args.expected_backup_bundle_sha
                ),
                "runnerSha256": Args.runner_sha,
                "workerSha256": Args.worker_sha,
            }
        )
    raw = json.dumps(payload, separators=(",", ":")).encode()
    return payload, raw


class LegacyBaselineContractTest(unittest.TestCase):
    def test_approval_is_exact_and_bound_to_invocation(self):
        _, raw = approval(
            "ADOPT_LEGACY_SCHEMA_BASELINE",
            True,
            baseline_bindings=True,
        )
        args = Args()
        args.approval_sha = baseline.sha256_bytes(raw)
        args.approval_json_base64 = base64.b64encode(raw).decode()
        parsed = baseline.validate_approval(
            args,
            {"host": "127.0.0.1", "port": 3306},
            "01234567-89ab-cdef-0123-456789abcdef",
            now=dt.datetime(2026, 7, 23, 18, 0, tzinfo=dt.timezone.utc),
        )
        self.assertEqual(parsed["runId"], args.run_id)

        tampered = json.loads(raw)
        tampered["productionDatabaseWrite"] = False
        tampered_raw = json.dumps(tampered, separators=(",", ":")).encode()
        args.approval_sha = baseline.sha256_bytes(tampered_raw)
        args.approval_json_base64 = base64.b64encode(tampered_raw).decode()
        with self.assertRaisesRegex(RuntimeError, "scope"):
            baseline.validate_approval(
                args,
                {"host": "127.0.0.1", "port": 3306},
                "01234567-89ab-cdef-0123-456789abcdef",
                now=dt.datetime(
                    2026, 7, 23, 18, 0, tzinfo=dt.timezone.utc
                ),
            )

    def test_receipt_digest_is_canonical_and_rejects_unknown_shape(self):
        payload = {
            "schema": baseline.RECEIPT_SCHEMA,
            "sourceCommit": "a" * 40,
            "productionBusinessStateChanged": False,
        }
        forward = baseline.receipt_digest(payload)
        reverse = baseline.receipt_digest(dict(reversed(list(payload.items()))))
        self.assertEqual(forward, reverse)
        with self.assertRaisesRegex(RuntimeError, "fields"):
            baseline.validate_receipt_shape({**payload, "unknown": True})

    def test_control_tables_are_excluded_from_legacy_fingerprint(self):
        queries = "\n".join(baseline.metadata_queries())
        self.assertGreaterEqual(queries.count("NOT IN"), 8)
        for table in baseline.CONTROL_TABLES:
            self.assertGreaterEqual(queries.count(table), 8)

    def test_recovery_uses_a_fresh_filesystem_only_approval(self):
        args = Args()
        args.mode = "Recover"
        args.expected_adoption_receipt_sha = "e" * 64
        args.original_approval_sha = "f" * 64
        payload, _ = approval(
            "RECOVER_LEGACY_SCHEMA_BASELINE_ANCHOR",
            False,
            baseline_bindings=True,
        )
        payload.update(
            {
                "expectedAdoptionReceiptSha256":
                    args.expected_adoption_receipt_sha,
                "originalApprovalReceiptSha256":
                    args.original_approval_sha,
            }
        )
        raw = json.dumps(payload, separators=(",", ":")).encode()
        args.approval_sha = baseline.sha256_bytes(raw)
        args.approval_json_base64 = base64.b64encode(raw).decode()
        parsed = baseline.validate_approval(
            args,
            {"host": "127.0.0.1", "port": 3306},
            "01234567-89ab-cdef-0123-456789abcdef",
            now=dt.datetime(2026, 7, 23, 18, 0, tzinfo=dt.timezone.utc),
        )
        self.assertFalse(parsed["productionDatabaseWrite"])

    def test_worker_and_readiness_share_the_canonical_overlay_contract(self):
        readiness = (ROOT / "verify-independent-board-production-readiness.ps1").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            baseline.EXPECTED_CONTROL_OVERLAY_SHA256,
            readiness,
        )
        readiness_overlay = re.search(
            r"def legacy_control_overlay_sha256\(\):(.*?)\ndef http_status",
            readiness,
            re.S,
        )
        self.assertIsNotNone(readiness_overlay)
        worker_overlay = inspect.getsource(baseline.control_overlay_rows)
        for marker in (
            "'T'", "'C'", "'I'", "'K'", "'U'", "'F'", "'H'", "'G'", "'P'"
        ):
            self.assertIn(marker, worker_overlay)
            self.assertIn(marker, readiness_overlay.group(1))


class DefaultOffConfigurationContractTest(unittest.TestCase):
    def test_parser_rejects_duplicates_and_invalid_lines(self):
        with self.assertRaisesRegex(RuntimeError, "duplicate"):
            configuration.parse_environment("A=1\nA=2\n")
        with self.assertRaisesRegex(RuntimeError, "invalid"):
            configuration.parse_environment("A=1\nnot-an-assignment\n")
        with self.assertRaisesRegex(RuntimeError, "relaxed-binding"):
            configuration.parse_environment(
                "fbsir_independent_board_attribution_enabled=true\n"
            )
        with self.assertRaisesRegex(RuntimeError, "high-priority"):
            configuration.parse_environment(
                "SPRING_APPLICATION_JSON={}\n"
            )

    def test_render_appends_exact_false_flags_and_independent_secrets(self):
        self.assertEqual(len(configuration.REQUIRED_FALSE_FLAGS), 12)
        original = "# existing\nA=1\n"
        event_material = bytes(range(32))
        binding_material = bytes(reversed(range(32)))
        rendered = configuration.render_configuration(
            original,
            event_key_id="w1a-20260723-k1",
            event_material=event_material,
            same_binding_material=binding_material,
        )
        values = configuration.parse_environment(rendered)
        for name in configuration.REQUIRED_FALSE_FLAGS:
            self.assertEqual(values[name], "false")
        self.assertNotEqual(
            values[configuration.EVENT_KEY_NAME],
            values[configuration.SAME_BINDING_KEY_NAME],
        )
        self.assertTrue(
            values[configuration.EVENT_KEY_NAME].startswith("base64:")
        )
        evidence = configuration.configuration_evidence(values)
        serialized = json.dumps(evidence)
        self.assertNotIn(base64.b64encode(event_material).decode(), serialized)
        self.assertNotIn(
            base64.b64encode(binding_material).decode(),
            serialized,
        )

    def test_partial_existing_configuration_fails_closed(self):
        original = "FBSIR_BOARD_ATTRIBUTION_ENABLED=false\n"
        with self.assertRaisesRegex(RuntimeError, "partial"):
            configuration.render_configuration(
                original,
                event_key_id="w1a-20260723-k1",
                event_material=b"x" * 32,
                same_binding_material=b"y" * 32,
            )

    def test_configuration_approval_binds_prestate_and_executed_code(self):
        args = types.SimpleNamespace(
            mode="Apply",
            run_id="w1a-config-20260723T180000Z-0123456789ab",
            source_commit="a" * 40,
            approval_sha="",
            approval_json_base64="",
            expected_environment_sha="d" * 64,
            expected_configured_environment_sha="0" * 64,
            original_approval_sha="0" * 64,
            runner_sha="b" * 64,
            worker_sha="c" * 64,
        )
        payload = {
            "schema": "fbsir.u3wProductionChangeApprovalReceipt.v1",
            "action": "CONFIGURE_W1A_DEFAULT_OFF_CRYPTO_CUSTODY",
            "targetHost": "api2.u3w.com",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "approvedAt": "2026-07-23T17:50:00Z",
            "expiresAt": "2026-07-23T18:50:00Z",
            "authorizedBy": "workspace-user",
            "concurrentDdlProhibited": True,
            "productionFilesystemWrite": True,
            "productionDatabaseWrite": False,
            "productionServiceChange": False,
            "officialExpertsPackageChange": False,
            "expectedEnvironmentSha256": args.expected_environment_sha,
            "expectedApi2EventKeyState": "ABSENT",
            "runnerSha256": args.runner_sha,
            "workerSha256": args.worker_sha,
        }
        raw = json.dumps(payload, separators=(",", ":")).encode()
        args.approval_sha = configuration.sha256_bytes(raw)
        args.approval_json_base64 = base64.b64encode(raw).decode()
        parsed = configuration.validate_approval(
            args,
            now=dt.datetime(2026, 7, 23, 18, 0, tzinfo=dt.timezone.utc),
        )
        self.assertEqual(
            parsed["expectedEnvironmentSha256"],
            args.expected_environment_sha,
        )

    def test_configuration_recovery_has_a_new_scoped_approval(self):
        args = types.SimpleNamespace(
            mode="Recover",
            run_id="w1a-config-20260723T180000Z-0123456789ab",
            source_commit="a" * 40,
            approval_sha="",
            approval_json_base64="",
            expected_environment_sha="d" * 64,
            expected_configured_environment_sha="e" * 64,
            original_approval_sha="f" * 64,
            runner_sha="b" * 64,
            worker_sha="c" * 64,
        )
        payload = {
            "schema": "fbsir.u3wProductionChangeApprovalReceipt.v1",
            "action": "RECOVER_W1A_DEFAULT_OFF_CONFIGURATION_ANCHOR",
            "targetHost": "api2.u3w.com",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "approvedAt": "2026-07-23T17:50:00Z",
            "expiresAt": "2026-07-23T18:50:00Z",
            "authorizedBy": "workspace-user",
            "concurrentDdlProhibited": True,
            "productionFilesystemWrite": True,
            "productionDatabaseWrite": False,
            "productionServiceChange": False,
            "officialExpertsPackageChange": False,
            "expectedEnvironmentSha256": args.expected_environment_sha,
            "expectedConfiguredEnvironmentSha256":
                args.expected_configured_environment_sha,
            "expectedApi2EventKeyState": "ABSENT",
            "originalApprovalReceiptSha256": args.original_approval_sha,
            "runnerSha256": args.runner_sha,
            "workerSha256": args.worker_sha,
        }
        raw = json.dumps(payload, separators=(",", ":")).encode()
        args.approval_sha = configuration.sha256_bytes(raw)
        args.approval_json_base64 = base64.b64encode(raw).decode()
        parsed = configuration.validate_approval(
            args,
            now=dt.datetime(2026, 7, 23, 18, 0, tzinfo=dt.timezone.utc),
        )
        self.assertEqual(
            parsed["originalApprovalReceiptSha256"],
            args.original_approval_sha,
        )

    def test_embedded_readiness_collector_compiles(self):
        source = (ROOT / "verify-independent-board-production-readiness.ps1").read_text(
            encoding="utf-8"
        )
        match = re.search(
            r"\$remotePython\s*=\s*@'\r?\n(.*?)\r?\n'@",
            source,
            re.S,
        )
        self.assertIsNotNone(match)
        collector = match.group(1)
        replacements = {
            token: json.dumps(value)
            for token, value in {
                "__SERVICE_UNIT__": "fbsir-admin.service",
                "__TARGET_HOST__": "api2.u3w.com",
                "__BACKUP_RECEIPT_PATH__": "/tmp/backup.json",
                "__DEPLOYMENT_RECEIPT_PATH__": "/tmp/deploy.json",
                "__LEGACY_BASELINE_RECEIPT_PATH__": "/tmp/baseline.json",
                "__CONFIGURATION_RECEIPT_PATH__": "/tmp/config.json",
                "__API2_EVENT_KEY_PATH__": "/tmp/key",
                "__EXPECTED_BACKUP_RECEIPT_SHA256__": "",
                "__EXPECTED_DEPLOYMENT_RECEIPT_SHA256__": "",
                "__EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST__": "",
                "__EXPECTED_CONFIGURATION_RECEIPT_SHA256__": "",
                "__EXPECTED_SOURCE_COMMIT__": "a" * 40,
                "__EXPECTED_BACKUP_RUNNER_SHA256__": "b" * 64,
                "__EXPECTED_BACKUP_WORKER_SHA256__": "c" * 64,
                "__EXPECTED_RESTORE_VERIFIER_SHA256__": "d" * 64,
                "__EXPECTED_BASELINE_RUNNER_SHA256__": "e" * 64,
                "__EXPECTED_BASELINE_WORKER_SHA256__": "f" * 64,
                "__EXPECTED_CONFIGURATION_RUNNER_SHA256__": "1" * 64,
                "__EXPECTED_CONFIGURATION_WORKER_SHA256__": "2" * 64,
            }.items()
        }
        for token, value in replacements.items():
            collector = collector.replace(token, value)
        compile(collector, "<readiness-collector>", "exec")


if __name__ == "__main__":
    unittest.main()
