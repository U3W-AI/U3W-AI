import base64
import contextlib
import datetime as dt
import hashlib
import importlib.util
import inspect
import io
import json
import os
import pathlib
import re
import sys
import tempfile
import types
import unittest
from unittest import mock


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


@contextlib.contextmanager
def reconciliation_harness(
    existing_credential=None,
    token_secret="utf8:jwt-test-only-secret-material",
    predecessor_schema="v2",
):
    with tempfile.TemporaryDirectory() as temporary:
        root = pathlib.Path(temporary).resolve()
        environment_path = root / "etc" / "fbsir-admin.env"
        event_key_path = root / "etc" / "event-key"
        configuration_root = root / "configuration" / "w1a"
        configuration_latest = root / "configuration" / "latest"
        backup_root = root / "backups"
        lock_path = root / "production-change.lock"
        for directory in (
            environment_path.parent,
            configuration_root,
            backup_root,
        ):
            directory.mkdir(parents=True, exist_ok=True)
        event_material = b"event-material-" + (b"e" * 40)
        binding_material = b"binding-material-" + (b"b" * 40)
        predecessor_environment = configuration.render_configuration(
            (
                "WXFBSIR_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/fbsir\n"
                "WXFBSIR_MYSQL_USERNAME=fbsir\n"
                "WXFBSIR_MYSQL_PASSWORD=test-only\n"
                "FBSIR_TOKEN_SECRET={}\n".format(token_secret)
            ),
            event_key_id="w1a-20260723-k1",
            event_material=event_material,
            same_binding_material=binding_material,
        ).encode("utf-8")
        original = predecessor_environment
        if existing_credential is not None:
            rendered, state = (
                configuration.reconcile_admin_engine_credential(
                    predecessor_environment.decode("utf-8"),
                    existing_credential,
                    api2_event_material=event_material,
                )
            )
            if state != "CREATED_BY_RUN":
                raise RuntimeError(
                    "test existing credential fixture is invalid"
                )
            original = rendered.encode("utf-8")
        if predecessor_schema == "v3":
            if existing_credential is None:
                raise RuntimeError(
                    "v3 predecessor requires an Engine credential"
                )
            predecessor_environment = original
        elif predecessor_schema != "v2":
            raise RuntimeError("unsupported test predecessor schema")
        environment_path.write_bytes(original)
        event_key_path.write_bytes(event_material)
        before_sha = configuration.sha256_bytes(original)
        predecessor_environment_sha = configuration.sha256_bytes(
            predecessor_environment
        )
        predecessor_sha = "e" * 64
        predecessor_values = configuration.parse_environment(
            predecessor_environment.decode("utf-8")
        )
        predecessor = {
            "schema": (
                "fbsir.u3wDefaultOffConfigurationReceipt.v3"
                if predecessor_schema == "v3"
                else "fbsir.u3wDefaultOffConfigurationReceipt.v2"
            ),
            "environmentAfterSha256": predecessor_environment_sha,
            "configurationEvidence": (
                configuration.configuration_v3_evidence(
                    predecessor_values,
                    event_material,
                )
                if predecessor_schema == "v3"
                else configuration.configuration_evidence(
                    predecessor_values,
                    event_material,
                )
            ),
        }
        service = {
            "activeState": "active",
            "mainPid": 123,
            "jarPath": "/opt/fbsir/admin/releases/old/fbsir-admin.jar",
            "jarSha256": "1" * 64,
            "invocationId": "2" * 32,
            "execMainStartTimestampMonotonic": 456,
            "nRestarts": 0,
        }
        args = types.SimpleNamespace(
            mode="Reconcile",
            run_id="w1a-config-20260724T180000Z-0123456789ab",
            source_commit="a" * 40,
            approval_sha="d" * 64,
            approval_json_base64="",
            expected_environment_sha=before_sha,
            expected_configured_environment_sha="0" * 64,
            original_approval_sha="0" * 64,
            expected_predecessor_configuration_receipt_sha=predecessor_sha,
            runner_sha="b" * 64,
            worker_sha="c" * 64,
        )
        controls = {
            "approvalValidations": 0,
            "failAfterEnvironmentWrite": False,
            "published": [],
            "predecessorRequireLive": [],
            "serviceSnapshots": [],
        }

        def validate_file(path, mode=0o600):
            candidate = pathlib.Path(path)
            if candidate.is_symlink() or not candidate.is_file():
                raise RuntimeError("test artifact type is invalid")
            return candidate

        def safe_directory(path, mode=0o700):
            candidate = pathlib.Path(path)
            candidate.mkdir(parents=True, exist_ok=True)
            return candidate

        def safe_run_directory(run_id):
            candidate = configuration_root / run_id
            candidate.mkdir(parents=True, exist_ok=True)
            return candidate

        def atomic_bytes(path, payload, mode=0o600):
            candidate = pathlib.Path(path)
            candidate.parent.mkdir(parents=True, exist_ok=True)
            candidate.write_bytes(payload)
            configuration.record_filesystem_mutation(
                configuration_changed=candidate == environment_path
            )
            if (
                candidate == environment_path
                and controls["failAfterEnvironmentWrite"]
            ):
                controls["failAfterEnvironmentWrite"] = False
                raise RuntimeError(
                    "injected crash after environment replacement"
                )

        def predecessor_receipt(
            invocation_args,
            require_live_environment=True,
            expected_schema=None,
        ):
            controls["predecessorRequireLive"].append(
                require_live_environment
            )
            if (
                invocation_args
                .expected_predecessor_configuration_receipt_sha
                != predecessor_sha
            ):
                raise RuntimeError("test predecessor anchor drifted")
            if (
                expected_schema is not None
                and predecessor["schema"] != expected_schema
            ):
                raise RuntimeError("test predecessor schema drifted")
            return predecessor, root / "predecessor.json", event_material

        def service_snapshot():
            if controls["serviceSnapshots"]:
                return controls["serviceSnapshots"].pop(0)
            return dict(service)

        def open_lock():
            return os.open(lock_path, os.O_RDWR | os.O_CREAT, 0o600)

        def validate_approval(_args, **_kwargs):
            controls["approvalValidations"] += 1
            now = dt.datetime.now(dt.timezone.utc)
            return {
                "approvedAt": (
                    now - dt.timedelta(minutes=5)
                ).isoformat(),
                "expiresAt": (
                    now + dt.timedelta(minutes=55)
                ).isoformat(),
            }

        original_mutation_state = dict(configuration.MUTATION_STATE)
        configuration.MUTATION_STATE.update({
            "productionFilesystemChanged": False,
            "productionConfigurationChanged": False,
        })
        with contextlib.ExitStack() as stack:
            for name, value in (
                ("ENV_PATH", environment_path),
                ("API2_EVENT_KEY_PATH", event_key_path),
                ("CONFIG_ROOT", configuration_root),
                ("CONFIG_LATEST", configuration_latest),
                ("ENV_BACKUP_ROOT", backup_root),
                ("HOST_CHANGE_LOCK", lock_path),
            ):
                stack.enter_context(mock.patch.object(configuration, name, value))
            stack.enter_context(
                mock.patch.object(
                    configuration,
                    "validate_approval",
                    side_effect=validate_approval,
                )
            )
            stack.enter_context(
                mock.patch.object(
                    configuration,
                    "validate_regular_file",
                    side_effect=validate_file,
                )
            )
            stack.enter_context(
                mock.patch.object(
                    configuration,
                    "safe_directory",
                    side_effect=safe_directory,
                )
            )
            stack.enter_context(
                mock.patch.object(
                    configuration,
                    "safe_run_directory",
                    side_effect=safe_run_directory,
                )
            )
            stack.enter_context(
                mock.patch.object(
                    configuration,
                    "atomic_bytes",
                    side_effect=atomic_bytes,
                )
            )
            stack.enter_context(
                mock.patch.object(
                    configuration,
                    "predecessor_configuration_receipt",
                    side_effect=predecessor_receipt,
                )
            )
            stack.enter_context(
                mock.patch.object(
                    configuration,
                    "service_snapshot",
                    side_effect=service_snapshot,
                )
            )
            stack.enter_context(
                mock.patch.object(
                    configuration,
                    "open_host_change_lock",
                    side_effect=open_lock,
                )
            )
            stack.enter_context(
                mock.patch.object(
                    configuration,
                    "publish_latest",
                    side_effect=lambda path: controls["published"].append(path),
                )
            )
            stack.enter_context(
                mock.patch.object(configuration, "fsync_directory")
            )
            stack.enter_context(
                mock.patch.object(
                    configuration.os,
                    "geteuid",
                    create=True,
                    return_value=0,
                )
            )
            try:
                yield types.SimpleNamespace(
                    root=root,
                    args=args,
                    controls=controls,
                    environment_path=environment_path,
                    event_key_path=event_key_path,
                    configuration_root=configuration_root,
                    backup_root=backup_root,
                    original=original,
                    predecessor_environment=predecessor_environment,
                    predecessor=predecessor,
                    predecessor_sha=predecessor_sha,
                    service=service,
                )
            finally:
                configuration.MUTATION_STATE.clear()
                configuration.MUTATION_STATE.update(original_mutation_state)


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
    def test_worker_error_is_single_bounded_stdout_envelope(self):
        args = types.SimpleNamespace(
            mode="RotateTokenSecret",
            run_id="w1a-config-20260724T180000Z-0123456789ab",
            source_commit="a" * 40,
        )
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            mock.patch.object(
                configuration,
                "parse_args",
                return_value=args,
            ),
            mock.patch.object(
                configuration,
                "rotate_token_secret",
                side_effect=RuntimeError("sensitive diagnostic text"),
            ),
            contextlib.redirect_stdout(stdout),
            contextlib.redirect_stderr(stderr),
        ):
            exit_code = configuration.main([])
        payload = json.loads(stdout.getvalue())
        self.assertEqual(exit_code, 1)
        self.assertEqual(stderr.getvalue(), "")
        self.assertEqual(
            payload["schema"],
            "fbsir.u3wDefaultOffConfigurationWorkerError.v2",
        )
        self.assertEqual(payload["mode"], args.mode)
        self.assertEqual(payload["runId"], args.run_id)
        self.assertEqual(payload["sourceCommit"], args.source_commit)
        self.assertRegex(payload["errorMessageSha256"], r"^[0-9a-f]{64}$")
        self.assertNotIn("sensitive diagnostic text", stdout.getvalue())
        self.assertFalse(payload["secretsDisclosed"])

    def test_atomic_environment_mutation_flags_follow_actual_syscalls(self):
        original_state = dict(configuration.MUTATION_STATE)
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            environment = root / "fbsir-admin.env"
            environment.write_bytes(b"before")
            configuration.MUTATION_STATE.update({
                "productionFilesystemChanged": False,
                "productionConfigurationChanged": False,
            })
            with (
                mock.patch.object(configuration, "ENV_PATH", environment),
                mock.patch.object(
                    configuration.os,
                    "open",
                    side_effect=OSError("open failed"),
                ),
                self.assertRaisesRegex(OSError, "open failed"),
            ):
                configuration.atomic_bytes(environment, b"after")
            self.assertFalse(
                configuration.MUTATION_STATE[
                    "productionFilesystemChanged"
                ]
            )
            self.assertFalse(
                configuration.MUTATION_STATE[
                    "productionConfigurationChanged"
                ]
            )
            self.assertEqual(
                list(root.glob("*.partial")),
                [],
            )

            configuration.MUTATION_STATE.update({
                "productionFilesystemChanged": False,
                "productionConfigurationChanged": False,
            })
            with (
                mock.patch.object(configuration, "ENV_PATH", environment),
                mock.patch.object(
                    configuration.os,
                    "replace",
                    side_effect=OSError("replace failed"),
                ),
                mock.patch.object(configuration, "fsync_directory"),
                self.assertRaisesRegex(OSError, "replace failed"),
            ):
                configuration.atomic_bytes(environment, b"after")
            self.assertTrue(
                configuration.MUTATION_STATE[
                    "productionFilesystemChanged"
                ]
            )
            self.assertFalse(
                configuration.MUTATION_STATE[
                    "productionConfigurationChanged"
                ]
            )
            self.assertEqual(
                list(root.glob("*.partial")),
                [],
            )
        configuration.MUTATION_STATE.clear()
        configuration.MUTATION_STATE.update(original_state)

    def test_host_change_lock_is_bounded(self):
        with (
            mock.patch.object(
                configuration.fcntl,
                "flock",
                side_effect=BlockingIOError(),
            ),
            mock.patch.object(
                configuration.time,
                "monotonic",
                side_effect=[0.0, 31.0],
            ),
            mock.patch.object(configuration.time, "sleep"),
        ):
            with self.assertRaisesRegex(RuntimeError, "timed out"):
                configuration.acquire_host_change_lock(
                    123,
                    timeout_seconds=30,
                )

    def test_expired_reconcile_only_finalizes_an_already_applied_delta(self):
        approval = {
            "approvedAt": "2020-01-01T00:00:00Z",
            "expiresAt": "2020-01-01T01:00:00Z",
        }
        journal = {
            "receiptObservedAt": "2020-01-01T00:30:00Z",
            "environmentBeforeSha256": "a" * 64,
            "adminEngineCredentialExistedBefore": False,
        }
        self.assertTrue(
            configuration
            .expired_reconciliation_finalization_authorized(
                approval,
                journal,
                "b" * 64,
            )
        )
        self.assertFalse(
            configuration
            .expired_reconciliation_finalization_authorized(
                approval,
                journal,
                "a" * 64,
            )
        )
        adopted = {
            **journal,
            "adminEngineCredentialExistedBefore": True,
        }
        self.assertTrue(
            configuration
            .expired_reconciliation_finalization_authorized(
                approval,
                adopted,
                "a" * 64,
            )
        )

    def test_configuration_accepts_release_owned_rollback_jar_modes(self):
        source = inspect.getsource(configuration.service_snapshot)
        self.assertIn(
            "mode=(0o600, 0o640, 0o644)",
            source,
        )

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

    def test_engine_credential_reconciliation_preserves_w1a_material(self):
        event_material = bytes(range(32))
        binding_material = bytes(reversed(range(32)))
        original = configuration.render_configuration(
            "# existing\nA=1\n",
            event_key_id="w1a-20260723-k1",
            event_material=event_material,
            same_binding_material=binding_material,
        )
        credential = "engine-credential-" + ("z" * 40)

        rendered, state = configuration.reconcile_admin_engine_credential(
            original,
            credential,
            api2_event_material=event_material,
        )

        before = configuration.parse_environment(original)
        after = configuration.parse_environment(rendered)
        self.assertEqual(state, "CREATED_BY_RUN")
        self.assertEqual(
            after[configuration.ADMIN_ENGINE_TOKEN_NAME],
            credential,
        )
        for name in configuration.MANAGED_KEYS:
            self.assertEqual(after[name], before[name])
        evidence = configuration.configuration_v3_evidence(
            after,
            event_material,
        )
        configuration.assert_configuration_v3_evidence(evidence)
        serialized = json.dumps(evidence)
        self.assertNotIn(credential, serialized)

    def test_engine_credential_reconciliation_rejects_partial_or_short_state(self):
        with self.assertRaisesRegex(RuntimeError, "default-off"):
            configuration.reconcile_admin_engine_credential(
                "FBSIR_BOARD_ATTRIBUTION_ENABLED=false\n",
                "engine-credential-" + ("z" * 40),
                api2_event_material=b"x" * 32,
            )
        original = configuration.render_configuration(
            "",
            event_key_id="w1a-20260723-k1",
            event_material=b"x" * 32,
            same_binding_material=b"y" * 32,
        )
        with self.assertRaisesRegex(RuntimeError, "credential"):
            configuration.reconcile_admin_engine_credential(
                original + "FBSIR_ENGINE_TOKEN=short\n",
                "engine-credential-" + ("z" * 40),
                api2_event_material=b"x" * 32,
            )

    def test_token_secret_rotation_replaces_only_undersized_secret(self):
        event_material = b"event-material-" + (b"e" * 40)
        binding_material = b"binding-material-" + (b"b" * 40)
        original = configuration.render_configuration(
            (
                "A=1\n"
                "FBSIR_TOKEN_SECRET=legacy-token-secret-26-char\n"
            ),
            event_key_id="w1a-20260723-k1",
            event_material=event_material,
            same_binding_material=binding_material,
        )
        original, _ = configuration.reconcile_admin_engine_credential(
            original,
            "engine-credential-" + ("e" * 40),
            api2_event_material=event_material,
        )
        new_secret = "rotated-token-secret-" + ("r" * 48)

        rendered, state = (
            configuration.rotate_token_secret_environment(
                original,
                new_secret,
                api2_event_material=event_material,
            )
        )

        before = configuration.parse_environment(original)
        after = configuration.parse_environment(rendered)
        self.assertEqual(state, "ROTATED_BY_RUN")
        self.assertEqual(
            after[configuration.TOKEN_SECRET_NAME],
            new_secret,
        )
        self.assertNotEqual(
            before[configuration.TOKEN_SECRET_NAME],
            after[configuration.TOKEN_SECRET_NAME],
        )
        for name, value in before.items():
            if name != configuration.TOKEN_SECRET_NAME:
                self.assertEqual(after[name], value)
        evidence = configuration.configuration_v4_evidence(
            after,
            event_material,
        )
        configuration.assert_configuration_v4_evidence(evidence)
        serialized = json.dumps(evidence)
        self.assertNotIn(new_secret, serialized)
        self.assertNotIn(
            before[configuration.TOKEN_SECRET_NAME],
            serialized,
        )
        self.assertNotIn(
            configuration.sha256_bytes(new_secret.encode()),
            serialized,
        )

    def test_token_secret_rotation_rejects_strong_or_reused_material(self):
        event_material = b"event-material-" + (b"e" * 40)
        binding_material = b"binding-material-" + (b"b" * 40)
        original = configuration.render_configuration(
            "FBSIR_TOKEN_SECRET=legacy-token-secret-26-char\n",
            event_key_id="w1a-20260723-k1",
            event_material=event_material,
            same_binding_material=binding_material,
        )
        engine = "engine-credential-" + ("e" * 40)
        original, _ = configuration.reconcile_admin_engine_credential(
            original,
            engine,
            api2_event_material=event_material,
        )
        with self.assertRaisesRegex(RuntimeError, "independent"):
            configuration.rotate_token_secret_environment(
                original,
                engine,
                api2_event_material=event_material,
            )

        strong = original.replace(
            "legacy-token-secret-26-char",
            "already-strong-token-secret-" + ("s" * 48),
        )
        with self.assertRaisesRegex(RuntimeError, "undersized"):
            configuration.rotate_token_secret_environment(
                strong,
                "rotated-token-secret-" + ("r" * 48),
                api2_event_material=event_material,
            )

    def test_token_secret_rotation_approval_is_explicit_and_bound(self):
        args = types.SimpleNamespace(
            mode="RotateTokenSecret",
            run_id="w1a-config-20260724T180000Z-0123456789ab",
            source_commit="a" * 40,
            approval_sha="",
            approval_json_base64="",
            expected_environment_sha="d" * 64,
            expected_configured_environment_sha="0" * 64,
            original_approval_sha="0" * 64,
            expected_predecessor_configuration_receipt_sha="e" * 64,
            runner_sha="b" * 64,
            worker_sha="c" * 64,
        )
        payload = {
            "schema": "fbsir.u3wProductionChangeApprovalReceipt.v1",
            "action": "ROTATE_FBSIR_TOKEN_SECRET_FOR_W1A_DEFAULT_OFF",
            "targetHost": "api2.u3w.com",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "approvedAt": "2026-07-24T17:50:00Z",
            "expiresAt": "2026-07-24T18:50:00Z",
            "authorizedBy": "workspace-user",
            "concurrentDdlProhibited": True,
            "productionFilesystemWrite": True,
            "productionDatabaseWrite": False,
            "productionServiceChange": False,
            "officialExpertsPackageChange": False,
            "expectedEnvironmentSha256": args.expected_environment_sha,
            "expectedApi2EventKeyState": "PRESENT_ANCHORED",
            "expectedPredecessorConfigurationReceiptSha256":
                args.expected_predecessor_configuration_receipt_sha,
            "runnerSha256": args.runner_sha,
            "workerSha256": args.worker_sha,
        }
        raw = json.dumps(payload, separators=(",", ":")).encode()
        args.approval_sha = configuration.sha256_bytes(raw)
        args.approval_json_base64 = base64.b64encode(raw).decode()

        parsed = configuration.validate_approval(
            args,
            now=dt.datetime(2026, 7, 24, 18, 0, tzinfo=dt.timezone.utc),
        )

        self.assertEqual(
            parsed["action"],
            "ROTATE_FBSIR_TOKEN_SECRET_FOR_W1A_DEFAULT_OFF",
        )

    def test_token_secret_rotation_plan_and_receipt_chain_v3_to_v4(self):
        engine = "engine-credential-" + ("e" * 40)
        old_secret = "legacy-token-secret-26-char"
        with reconciliation_harness(
            existing_credential=engine,
            token_secret=old_secret,
            predecessor_schema="v3",
        ) as harness:
            harness.args.mode = "PlanTokenSecretRotation"
            plan = configuration.plan(harness.args)
            self.assertEqual(
                plan["schema"],
                "fbsir.u3wDefaultOffConfigurationPlan.v3",
            )
            self.assertEqual(
                plan["mode"],
                "PlanTokenSecretRotation",
            )
            self.assertEqual(
                plan["planPurpose"],
                "ROTATE_UNDERSIZED_FBSIR_TOKEN_SECRET",
            )
            self.assertTrue(plan["tokenSecretPresent"])
            self.assertTrue(plan["tokenSecretBelowMinimum"])
            self.assertTrue(plan["canonicalTokenSecretAssignment"])
            self.assertFalse(plan["productionFilesystemChanged"])

            harness.args.mode = "RotateTokenSecret"
            result = configuration.rotate_token_secret(harness.args)
            receipt_path = (
                harness.configuration_root
                / harness.args.run_id
                / "configuration-receipt.json"
            )
            receipt_raw = receipt_path.read_text(encoding="utf-8")
            receipt = json.loads(receipt_raw)
            after_values = configuration.parse_environment(
                harness.environment_path.read_text(encoding="utf-8")
            )
            new_secret = after_values[configuration.TOKEN_SECRET_NAME]

            self.assertEqual(
                receipt["schema"],
                "fbsir.u3wDefaultOffConfigurationReceipt.v4",
            )
            self.assertEqual(
                receipt["predecessorConfigurationReceiptSha256"],
                harness.predecessor_sha,
            )
            self.assertEqual(
                receipt["tokenSecretProvisioningState"],
                "ROTATED_BY_RUN",
            )
            self.assertEqual(
                receipt["adminEngineCredentialProvisioningState"],
                "REUSED_FROM_PREDECESSOR_RECEIPT",
            )
            self.assertEqual(
                receipt["environmentBeforeSha256"],
                configuration.sha256_bytes(harness.original),
            )
            self.assertEqual(
                receipt["environmentAfterSha256"],
                configuration.sha256_file(harness.environment_path),
            )
            self.assertTrue(receipt["productionConfigurationChanged"])
            self.assertFalse(receipt["serviceRestarted"])
            self.assertNotEqual(new_secret, old_secret)
            self.assertGreaterEqual(len(new_secret), 43)
            self.assertNotIn(old_secret, receipt_raw)
            self.assertNotIn(new_secret, receipt_raw)
            self.assertNotIn(
                configuration.sha256_bytes(new_secret.encode()),
                receipt_raw,
            )
            self.assertTrue(result["productionConfigurationChanged"])

            receipt_before = receipt_path.read_bytes()
            replay = configuration.rotate_token_secret(harness.args)
            self.assertTrue(replay["idempotentReplay"])
            self.assertEqual(receipt_path.read_bytes(), receipt_before)

    def test_token_secret_rotation_recovers_after_atomic_environment_write(self):
        with reconciliation_harness(
            existing_credential="engine-credential-" + ("e" * 40),
            token_secret="legacy-token-secret-26-char",
            predecessor_schema="v3",
        ) as harness:
            harness.args.mode = "RotateTokenSecret"
            harness.controls["failAfterEnvironmentWrite"] = True
            with self.assertRaisesRegex(
                RuntimeError,
                "injected crash after environment replacement",
            ):
                configuration.rotate_token_secret(harness.args)
            self.assertTrue(
                configuration.MUTATION_STATE[
                    "productionFilesystemChanged"
                ]
            )
            self.assertTrue(
                configuration.MUTATION_STATE[
                    "productionConfigurationChanged"
                ]
            )
            run_directory = (
                harness.configuration_root / harness.args.run_id
            )
            self.assertTrue(
                (run_directory / "configuration-journal.json").is_file()
            )
            self.assertFalse(
                (run_directory / "configuration-receipt.json").exists()
            )
            rotated = configuration.parse_environment(
                harness.environment_path.read_text(encoding="utf-8")
            )[configuration.TOKEN_SECRET_NAME]
            self.assertGreaterEqual(len(rotated), 43)

            with mock.patch.object(
                configuration,
                "approval_is_current",
                return_value=False,
            ), mock.patch.object(
                configuration,
                "token_secret_rotation_run_directory",
                return_value=run_directory,
            ):
                recovered = configuration.rotate_token_secret(
                    harness.args
                )
            self.assertFalse(recovered["idempotentReplay"])
            self.assertTrue(
                (run_directory / "configuration-receipt.json").is_file()
            )
            receipt_raw = (
                run_directory / "configuration-receipt.json"
            ).read_text(encoding="utf-8")
            self.assertNotIn(rotated, receipt_raw)

    def test_expired_token_secret_rotation_cannot_start_mutation(self):
        with reconciliation_harness(
            existing_credential="engine-credential-" + ("e" * 40),
            token_secret="legacy-token-secret-26-char",
            predecessor_schema="v3",
        ) as harness:
            harness.args.mode = "RotateTokenSecret"
            with mock.patch.object(
                configuration,
                "approval_is_current",
                return_value=False,
            ):
                with self.assertRaisesRegex(
                    RuntimeError,
                    "expired token rotation cannot create",
                ):
                    configuration.rotate_token_secret(harness.args)
            self.assertEqual(
                harness.environment_path.read_bytes(),
                harness.original,
            )
            self.assertFalse(
                (
                    harness.configuration_root / harness.args.run_id
                ).exists()
            )

    def test_engine_reconcile_approval_binds_predecessor_receipt(self):
        args = types.SimpleNamespace(
            mode="Reconcile",
            run_id="w1a-config-20260724T180000Z-0123456789ab",
            source_commit="a" * 40,
            approval_sha="",
            approval_json_base64="",
            expected_environment_sha="d" * 64,
            expected_configured_environment_sha="0" * 64,
            original_approval_sha="0" * 64,
            expected_predecessor_configuration_receipt_sha="e" * 64,
            runner_sha="b" * 64,
            worker_sha="c" * 64,
        )
        payload = {
            "schema": "fbsir.u3wProductionChangeApprovalReceipt.v1",
            "action": "ADOPT_EXISTING_ADMIN_ENGINE_CREDENTIAL_DELTA",
            "targetHost": "api2.u3w.com",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "approvedAt": "2026-07-24T17:50:00Z",
            "expiresAt": "2026-07-24T18:50:00Z",
            "authorizedBy": "workspace-user",
            "concurrentDdlProhibited": True,
            "productionFilesystemWrite": True,
            "productionDatabaseWrite": False,
            "productionServiceChange": False,
            "officialExpertsPackageChange": False,
            "expectedEnvironmentSha256": args.expected_environment_sha,
            "expectedApi2EventKeyState": "PRESENT_ANCHORED",
            "expectedPredecessorConfigurationReceiptSha256":
                args.expected_predecessor_configuration_receipt_sha,
            "runnerSha256": args.runner_sha,
            "workerSha256": args.worker_sha,
        }
        raw = json.dumps(payload, separators=(",", ":")).encode()
        args.approval_sha = configuration.sha256_bytes(raw)
        args.approval_json_base64 = base64.b64encode(raw).decode()

        parsed = configuration.validate_approval(
            args,
            now=dt.datetime(2026, 7, 24, 18, 0, tzinfo=dt.timezone.utc),
        )

        self.assertEqual(
            parsed["expectedPredecessorConfigurationReceiptSha256"],
            args.expected_predecessor_configuration_receipt_sha,
        )

    def test_engine_reconcile_plan_proves_exact_existing_delta_read_only(self):
        credential = "engine-credential-" + ("p" * 40)
        with reconciliation_harness(
            existing_credential=credential
        ) as harness:
            plan = configuration.plan(harness.args)
            self.assertEqual(
                plan["schema"],
                "fbsir.u3wDefaultOffConfigurationPlan.v2",
            )
            self.assertEqual(
                plan["planPurpose"],
                "RECONCILE_EXISTING_ADMIN_ENGINE_CREDENTIAL_DELTA",
            )
            self.assertTrue(plan["adminEngineCredentialPresent"])
            self.assertTrue(plan["exactExistingAdminEngineDeltaValid"])
            self.assertEqual(
                plan["predecessorConfigurationReceiptSha256"],
                harness.predecessor_sha,
            )
            self.assertFalse(plan["engineCounterpartClosureClaimed"])
            self.assertFalse(plan["productionFilesystemChanged"])
            self.assertFalse(
                (
                    harness.configuration_root
                    / harness.args.run_id
                ).exists()
            )

    def test_engine_reconcile_prohibits_unpaired_token_creation(self):
        with reconciliation_harness() as harness:
            with self.assertRaisesRegex(
                RuntimeError,
                "unpaired admin Engine credential creation is prohibited",
            ):
                configuration.reconcile(harness.args)
            self.assertEqual(
                harness.environment_path.read_bytes(),
                harness.original,
            )
            self.assertEqual(harness.controls["approvalValidations"], 2)
            self.assertEqual(harness.controls["predecessorRequireLive"], [])
            self.assertFalse(
                (
                    harness.configuration_root
                    / harness.args.run_id
                ).exists()
            )
            self.assertFalse(
                (
                    harness.configuration_root
                    / harness.args.run_id
                    / "configuration-receipt.json"
                ).exists()
            )

    def test_engine_reconcile_adopts_exact_preexisting_credential_delta(self):
        credential = "engine-credential-" + ("a" * 40)
        with reconciliation_harness(
            existing_credential=credential
        ) as harness:
            before = harness.environment_path.read_bytes()
            result = configuration.reconcile(harness.args)
            receipt_path = (
                harness.configuration_root
                / harness.args.run_id
                / "configuration-receipt.json"
            )
            receipt_raw = receipt_path.read_text(encoding="utf-8")
            receipt = json.loads(receipt_raw)

            self.assertEqual(harness.environment_path.read_bytes(), before)
            self.assertEqual(
                receipt["adminEngineCredentialProvisioningState"],
                "ADOPTED_EXISTING_EXACT_DELTA",
            )
            self.assertFalse(receipt["engineCounterpartClosureClaimed"])
            self.assertEqual(
                receipt["environmentBeforeSha256"],
                configuration.sha256_bytes(before),
            )
            self.assertEqual(
                receipt["environmentAfterSha256"],
                configuration.sha256_bytes(before),
            )
            self.assertFalse(receipt["productionConfigurationChanged"])
            self.assertNotIn(credential, receipt_raw)
            self.assertFalse(result["productionConfigurationChanged"])

            receipt_before = receipt_path.read_bytes()
            replay = configuration.reconcile(harness.args)
            self.assertTrue(replay["idempotentReplay"])
            self.assertEqual(receipt_path.read_bytes(), receipt_before)
            self.assertEqual(harness.environment_path.read_bytes(), before)

    def test_engine_reconcile_does_not_enter_creation_crash_path(self):
        with reconciliation_harness() as harness:
            harness.controls["failAfterEnvironmentWrite"] = True
            with self.assertRaisesRegex(RuntimeError, "unpaired"):
                configuration.reconcile(harness.args)
            run_directory = (
                harness.configuration_root / harness.args.run_id
            )
            self.assertFalse(
                (run_directory / "configuration-journal.json").exists()
            )
            self.assertFalse(
                (run_directory / "configuration-receipt.json").exists()
            )
            self.assertEqual(
                harness.environment_path.read_bytes(),
                harness.original,
            )

    def test_engine_reconcile_rejects_unrelated_existing_delta(self):
        with reconciliation_harness(
            existing_credential="engine-credential-" + ("b" * 40)
        ) as harness:
            with harness.environment_path.open("ab") as stream:
                stream.write(b"UNRELATED_CONFIGURATION=changed\n")
            with self.assertRaisesRegex(
                RuntimeError,
                "approved reconciliation environment anchor drifted",
            ):
                configuration.reconcile(harness.args)
            self.assertFalse(
                (
                    harness.configuration_root
                    / harness.args.run_id
                    / "configuration-receipt.json"
                ).exists()
            )

    def test_engine_reconcile_rejects_predecessor_environment_drift(self):
        with reconciliation_harness(
            existing_credential="engine-credential-" + ("c" * 40)
        ) as harness:
            harness.predecessor["environmentAfterSha256"] = "f" * 64
            with self.assertRaisesRegex(
                RuntimeError,
                "existing admin Engine credential predecessor anchor drifted",
            ):
                configuration.reconcile(harness.args)
            self.assertEqual(
                harness.environment_path.read_bytes(),
                harness.original,
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
            expected_predecessor_configuration_receipt_sha="0" * 64,
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
            expected_predecessor_configuration_receipt_sha="0" * 64,
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
