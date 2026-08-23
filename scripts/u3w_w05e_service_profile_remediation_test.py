import copy
import datetime as dt
import importlib.util
import json
import os
import pathlib
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("u3w-w05e-service-profile-remediation.py")
SPEC = importlib.util.spec_from_file_location("w05e_profile_remediation", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)

OBSERVED = dt.datetime(2026, 8, 23, 12, 0, tzinfo=dt.timezone.utc)
MACHINE = "1" * 64
SECRET = "do-not-print-this-database-secret"


def replay_environment(not_after: dt.datetime, ending: str = "\n") -> bytes:
    return ending.join(
        [
            "FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_ENABLED=true",
            "FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_MAX_AGE_HOURS=168",
            "FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_NOT_AFTER="
            + MODULE.format_instant(not_after),
            "FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_EVENT_DIGESTS="
            + "a" * 64,
            "",
        ]
    ).encode("utf-8")


class FakeRuntime:
    def __init__(
        self,
        *,
        ending: str = "\n",
        target_present: bool = False,
        target_value: str = "true",
        dropin: str = "expired",
    ):
        lines = [
            "FBSIR_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/fbsir",
            "FBSIR_MYSQL_USERNAME=fbsir",
            "FBSIR_MYSQL_PASSWORD=" + SECRET,
            "UNRELATED_PROFILE_KEY=preserve-me",
        ]
        if target_present:
            lines.insert(3, f"{MODULE.ENVIRONMENT_KEY}={target_value}")
        self.environment = (ending.join(lines) + ending).encode("utf-8")
        self.environment_file_mode = 0o600
        self.release = "/opt/fbsir/admin/releases/w05-receiver-5d0769c2dad8-20260823T074133Z"
        self.replay_path = self.release + "/configuration/w05-replay.env"
        self.files = {}
        if dropin == "absent":
            self.dropin = None
        else:
            self.dropin = (
                "[Service]\nEnvironmentFile=" + self.replay_path + "\n"
            ).encode("utf-8")
            not_after = (
                OBSERVED - dt.timedelta(minutes=1)
                if dropin == "expired"
                else OBSERVED + dt.timedelta(minutes=10)
            )
            self.files[self.replay_path] = replay_environment(not_after)
        self.dropin_file_mode = 0o644 if self.dropin is not None else None
        self.active = {
            "currentSymlink": MODULE.CURRENT.as_posix(),
            "releasePath": self.release,
            "releaseId": pathlib.PurePosixPath(self.release).name,
            "jarSha256": "2" * 64,
            "manifestSha256": "3" * 64,
            "sourceManifestSha256": "4" * 64,
            "sourceCommit": MODULE.EXPECTED_SOURCE_COMMIT,
        }
        self.java = {
            "unit": MODULE.JAVA_UNIT,
            "activeState": "active",
            "subState": "running",
            "mainPid": 100,
            "nRestarts": 0,
        }
        self.node = {
            "currentTarget": "/opt/fbss/phase1/releases/20260823-053000-w0-service-hardening-v10",
            "service": {
                "unit": MODULE.NODE_UNIT,
                "activeState": "active",
                "subState": "running",
                "mainPid": 200,
                "nRestarts": 0,
            },
        }
        self.database = {
            "readOnlyTransaction": True,
            "scopedMigrationReceipts": ["public_init_043", "public_init_044"],
            "migrationStatuses": {
                "public_init_043": "APPLIED",
                "public_init_044": "APPLIED",
            },
            "webhookTables": [],
            "creditBearingRows": 0,
        }
        self.reload_calls = 0
        self.restart_calls = 0
        self.node_restart_calls = 0
        self.write_calls = 0
        self.remove_calls = 0
        self.restore_calls = 0
        self.settled = []
        self.webhook_error_count = 0
        self.drift_node_after_restart = False

    def host_snapshot(self):
        return {
            "logicalTarget": MODULE.TARGET_HOST,
            "machineIdSha256": MACHINE,
            "hostname": "api2-production-host",
        }

    def active_snapshot(self):
        return copy.deepcopy(self.active)

    def service_snapshot(self, unit):
        if unit == MODULE.JAVA_UNIT:
            return copy.deepcopy(self.java)
        return copy.deepcopy(self.node["service"])

    def node_snapshot(self):
        value = copy.deepcopy(self.node)
        if self.drift_node_after_restart and self.restart_calls:
            value["service"]["mainPid"] += 1
        return value

    def database_snapshot(self, _environment_bytes):
        return copy.deepcopy(self.database)

    def read_environment(self):
        return self.environment

    def environment_mode(self):
        return self.environment_file_mode

    def read_dropin(self):
        return self.dropin

    def dropin_mode(self):
        return self.dropin_file_mode

    def read_file(self, path):
        if path not in self.files:
            MODULE.fail("fake_file_missing", "fake")
        return self.files[path]

    def write_environment(self, data, *, expected_sha256, mode):
        if MODULE.sha256_bytes(self.environment) != expected_sha256:
            MODULE.fail("environment_preimage_drift", "environment")
        self.environment = data
        self.environment_file_mode = mode
        self.write_calls += 1

    def remove_dropin(self, *, expected_sha256):
        if self.dropin is None or MODULE.sha256_bytes(self.dropin) != expected_sha256:
            MODULE.fail("dropin_preimage_drift", "replayDropin")
        self.dropin = None
        self.dropin_file_mode = None
        self.remove_calls += 1

    def restore_dropin(self, data, *, expected_current_sha256, mode):
        current = MODULE.sha256_bytes(self.dropin) if self.dropin is not None else None
        if current != expected_current_sha256:
            MODULE.fail("dropin_restore_preimage_drift", "replayDropin")
        self.dropin = data
        self.dropin_file_mode = mode
        self.restore_calls += 1

    def daemon_reload(self):
        self.reload_calls += 1

    def restart_java(self):
        self.restart_calls += 1
        self.java["mainPid"] += 1

    def wait_java_active(self):
        return None

    def journal_cursor(self):
        return "s=fake-cursor"

    def webhook_errors_after(self, _cursor):
        return self.webhook_error_count

    def settle(self, seconds):
        self.settled.append(seconds)


def authorization(plan, action="apply", observed=OBSERVED):
    return {
        "schemaVersion": MODULE.AUTHORIZATION_SCHEMA,
        "authorizationId": f"w05e-profile-{action}-transaction-0001",
        "action": action,
        "planSha256": MODULE.sha256_document(plan),
        "targetHost": MODULE.TARGET_HOST,
        "targetUnit": MODULE.JAVA_UNIT,
        "expectedMachineIdSha256": MACHINE,
        "issuedAt": MODULE.format_instant(observed - dt.timedelta(minutes=1)),
        "expiresAt": MODULE.format_instant(observed + dt.timedelta(minutes=10)),
        "singleUse": True,
        "approvedBy": "independent-release-owner",
        "status": "AUTHORIZED",
    }


class EnvironmentEditingTests(unittest.TestCase):
    def test_lf_and_crlf_change_only_target_key_and_preserve_secrets(self):
        for ending in ("\n", "\r\n"):
            with self.subTest(ending=repr(ending)):
                runtime = FakeRuntime(ending=ending, target_present=True)
                before = runtime.environment
                update = MODULE.environment_update(before)
                self.assertIn((MODULE.ENVIRONMENT_KEY + "=false").encode(), update["bytes"])
                self.assertIn(SECRET.encode(), update["bytes"])
                self.assertIn(b"UNRELATED_PROFILE_KEY=preserve-me", update["bytes"])
                self.assertEqual("CRLF" if ending == "\r\n" else "LF", update["lineEnding"])
                normalized_before = before.replace(
                    (MODULE.ENVIRONMENT_KEY + "=true").encode(),
                    (MODULE.ENVIRONMENT_KEY + "=false").encode(),
                )
                self.assertEqual(normalized_before, update["bytes"])

    def test_absent_target_is_added_once(self):
        runtime = FakeRuntime(target_present=False)
        update = MODULE.environment_update(runtime.environment)
        self.assertEqual(update["bytes"].count(b"FBSIR_WEBHOOK_SWEEP_ENABLED="), 1)
        self.assertEqual(update["beforeOccurrenceCount"], 0)

    def test_duplicate_target_and_mixed_line_endings_fail_closed(self):
        duplicate = b"FBSIR_WEBHOOK_SWEEP_ENABLED=true\nFBSIR_WEBHOOK_SWEEP_ENABLED=false\n"
        with self.assertRaises(MODULE.RemediationError) as caught:
            MODULE.environment_update(duplicate)
        self.assertEqual(caught.exception.code, "duplicate_environment_key")
        with self.assertRaises(MODULE.RemediationError) as caught:
            MODULE.environment_update(b"A=1\r\nB=2\n")
        self.assertEqual(caught.exception.code, "mixed_line_endings")


class TransactionTests(unittest.TestCase):
    def make_plan(self, runtime):
        return MODULE.build_plan(
            runtime, expected_machine_id_sha256=MACHINE, observed_at=OBSERVED
        )

    def test_plan_binds_truth_without_secret_or_write(self):
        runtime = FakeRuntime(dropin="expired")
        plan = self.make_plan(runtime)
        rendered = MODULE.canonical_json(plan).decode("utf-8")
        self.assertNotIn(SECRET, rendered)
        self.assertEqual(plan["bindings"]["replayDropin"]["action"], "REMOVE_EXPIRED")
        self.assertEqual(runtime.write_calls, 0)
        self.assertEqual(runtime.restart_calls, 0)
        self.assertFalse(plan["productionChanged"])
        self.assertFalse(plan["change"]["productCreditPromoted"])

    def test_not_expired_or_absent_dropin_is_retained(self):
        for state, expected in (
            ("not_expired", "RETAIN_NOT_EXPIRED"),
            ("absent", "RETAIN_ABSENT"),
        ):
            with self.subTest(state=state):
                runtime = FakeRuntime(dropin=state)
                plan = self.make_plan(runtime)
                self.assertEqual(plan["bindings"]["replayDropin"]["action"], expected)

    def test_expired_dropin_apply_is_single_restart_and_zero_credit(self):
        runtime = FakeRuntime(dropin="expired")
        original_environment = runtime.environment
        with tempfile.TemporaryDirectory() as root:
            plan = self.make_plan(runtime)
            receipt = MODULE.apply_transaction(
                runtime,
                plan_document=plan,
                authorization_document=authorization(plan),
                state_root=pathlib.Path(root),
                observed_at=OBSERVED,
                settle_seconds=0,
            )
            self.assertEqual(runtime.reload_calls, 1)
            self.assertEqual(runtime.restart_calls, 1)
            self.assertEqual(runtime.node_restart_calls, 0)
            self.assertEqual(runtime.remove_calls, 1)
            self.assertIsNone(runtime.dropin)
            self.assertNotEqual(runtime.environment, original_environment)
            self.assertIn(b"FBSIR_WEBHOOK_SWEEP_ENABLED=false", runtime.environment)
            self.assertFalse(receipt["postconditions"]["productCreditEligible"])
            backup_dir = pathlib.Path(receipt["backups"]["directory"])
            self.assertEqual(receipt["backups"]["directoryMode"], "0700")
            self.assertEqual(receipt["backups"]["environmentBackupMode"], "0600")
            if os.name == "posix":
                self.assertEqual(backup_dir.stat().st_mode & 0o777, 0o700)
                self.assertEqual(
                    pathlib.Path(receipt["backups"]["environmentPath"]).stat().st_mode & 0o777,
                    0o600,
                )
            serialized = json.dumps(receipt)
            self.assertNotIn(SECRET, serialized)

    def test_nonexpired_dropin_apply_keeps_exact_bytes(self):
        runtime = FakeRuntime(dropin="not_expired")
        original_dropin = runtime.dropin
        with tempfile.TemporaryDirectory() as root:
            plan = self.make_plan(runtime)
            MODULE.apply_transaction(
                runtime,
                plan_document=plan,
                authorization_document=authorization(plan),
                state_root=pathlib.Path(root),
                observed_at=OBSERVED,
                settle_seconds=0,
            )
        self.assertEqual(runtime.dropin, original_dropin)
        self.assertEqual(runtime.remove_calls, 0)

    def test_already_false_without_expired_dropin_is_idempotent_noop(self):
        runtime = FakeRuntime(
            target_present=True, target_value="false", dropin="not_expired"
        )
        with tempfile.TemporaryDirectory() as root:
            plan = self.make_plan(runtime)
            self.assertEqual(plan["change"]["environmentAction"], "RETAIN_ALREADY_FALSE")
            receipt = MODULE.apply_transaction(
                runtime,
                plan_document=plan,
                authorization_document=authorization(plan),
                state_root=pathlib.Path(root),
                observed_at=OBSERVED,
                settle_seconds=0,
            )
        self.assertEqual(runtime.write_calls, 0)
        self.assertEqual(runtime.remove_calls, 0)
        self.assertEqual(runtime.reload_calls, 0)
        self.assertEqual(runtime.restart_calls, 0)
        self.assertEqual(receipt["forwardJavaRestarts"], 0)

    def test_authorization_callback_occurs_after_backup_before_first_write(self):
        runtime = FakeRuntime(dropin="expired")
        observations = []
        with tempfile.TemporaryDirectory() as root:
            root_path = pathlib.Path(root)
            plan = self.make_plan(runtime)

            def consume():
                observations.append(
                    {
                        "backupExists": (root_path / "backups" / plan["planId"]).is_dir(),
                        "writeCalls": runtime.write_calls,
                        "removeCalls": runtime.remove_calls,
                    }
                )

            MODULE.apply_transaction(
                runtime,
                plan_document=plan,
                authorization_document=authorization(plan),
                state_root=root_path,
                observed_at=OBSERVED,
                settle_seconds=0,
                before_first_write=consume,
            )
        self.assertEqual(
            observations,
            [{"backupExists": True, "writeCalls": 0, "removeCalls": 0}],
        )

    def test_environment_or_dropin_preimage_drift_stops_before_write(self):
        for drift in ("environment", "dropin"):
            with self.subTest(drift=drift), tempfile.TemporaryDirectory() as root:
                runtime = FakeRuntime(dropin="expired")
                plan = self.make_plan(runtime)
                if drift == "environment":
                    runtime.environment += b"DRIFT=1\n"
                else:
                    runtime.dropin += b"# drift\n"
                with self.assertRaises(MODULE.RemediationError) as caught:
                    MODULE.apply_transaction(
                        runtime,
                        plan_document=plan,
                        authorization_document=authorization(plan),
                        state_root=pathlib.Path(root),
                        observed_at=OBSERVED,
                        settle_seconds=0,
                    )
                self.assertEqual(caught.exception.code, "preimage_drift")
                self.assertEqual(runtime.write_calls, 0)
                self.assertEqual(runtime.restart_calls, 0)

    def test_postcondition_failure_automatically_restores_preimages(self):
        runtime = FakeRuntime(dropin="expired")
        original_environment = runtime.environment
        original_dropin = runtime.dropin
        runtime.webhook_error_count = 1
        with tempfile.TemporaryDirectory() as root:
            plan = self.make_plan(runtime)
            with self.assertRaises(MODULE.RemediationError) as caught:
                MODULE.apply_transaction(
                    runtime,
                    plan_document=plan,
                    authorization_document=authorization(plan),
                    state_root=pathlib.Path(root),
                    observed_at=OBSERVED,
                    settle_seconds=0,
                )
            self.assertEqual(caught.exception.code, "new_webhook_reconciliation_error")
        self.assertEqual(runtime.environment, original_environment)
        self.assertEqual(runtime.dropin, original_dropin)
        self.assertEqual(runtime.restart_calls, 2)
        self.assertEqual(runtime.reload_calls, 2)

    def test_manual_rollback_is_one_additional_restart_and_restores_bytes(self):
        runtime = FakeRuntime(dropin="expired")
        original_environment = runtime.environment
        original_dropin = runtime.dropin
        with tempfile.TemporaryDirectory() as root:
            root_path = pathlib.Path(root)
            plan = self.make_plan(runtime)
            apply_receipt = MODULE.apply_transaction(
                runtime,
                plan_document=plan,
                authorization_document=authorization(plan),
                state_root=root_path,
                observed_at=OBSERVED,
                settle_seconds=0,
            )
            result = MODULE.rollback_transaction(
                runtime,
                plan_document=plan,
                apply_receipt=apply_receipt,
                authorization_document=authorization(plan, "rollback"),
                state_root=root_path,
                observed_at=OBSERVED,
                settle_seconds=0,
            )
        self.assertEqual(runtime.environment, original_environment)
        self.assertEqual(runtime.dropin, original_dropin)
        self.assertEqual(runtime.restart_calls, 2)
        self.assertEqual(result["forwardJavaRestarts"], 0)
        self.assertEqual(result["rollbackJavaRestarts"], 1)
        self.assertEqual(result["nodeRestarts"], 0)

    def test_verify_detects_node_drift(self):
        runtime = FakeRuntime(dropin="absent")
        with tempfile.TemporaryDirectory() as root:
            plan = self.make_plan(runtime)
            apply_receipt = MODULE.apply_transaction(
                runtime,
                plan_document=plan,
                authorization_document=authorization(plan),
                state_root=pathlib.Path(root),
                observed_at=OBSERVED,
                settle_seconds=0,
            )
            runtime.node["service"]["mainPid"] += 1
            with self.assertRaises(MODULE.RemediationError) as caught:
                MODULE.verify_transaction(
                    runtime,
                    plan_document=plan,
                    apply_receipt=apply_receipt,
                    observed_at=OBSERVED,
                )
            self.assertEqual(caught.exception.code, "node_drift_after_apply")

    def test_invalid_restart_budget_fails_before_write(self):
        runtime = FakeRuntime(dropin="absent")
        plan = self.make_plan(runtime)
        plan["restartBudget"]["forwardJava"] = 2
        with tempfile.TemporaryDirectory() as root, self.assertRaises(
            MODULE.RemediationError
        ) as caught:
            MODULE.apply_transaction(
                runtime,
                plan_document=plan,
                authorization_document=authorization(plan),
                state_root=pathlib.Path(root),
                observed_at=OBSERVED,
                settle_seconds=0,
            )
        self.assertEqual(caught.exception.code, "invalid_restart_budget")
        self.assertEqual(runtime.write_calls, 0)

    def test_authorization_expiry_fails_before_write(self):
        runtime = FakeRuntime(dropin="absent")
        plan = self.make_plan(runtime)
        auth = authorization(plan, observed=OBSERVED - dt.timedelta(hours=1))
        with tempfile.TemporaryDirectory() as root, self.assertRaises(
            MODULE.RemediationError
        ) as caught:
            MODULE.apply_transaction(
                runtime,
                plan_document=plan,
                authorization_document=auth,
                state_root=pathlib.Path(root),
                observed_at=OBSERVED,
                settle_seconds=0,
            )
        self.assertEqual(caught.exception.code, "authorization_not_current")
        self.assertEqual(runtime.write_calls, 0)

    def test_wrong_database_shape_or_source_fails_plan(self):
        cases = []
        runtime = FakeRuntime()
        runtime.database["webhookTables"] = ["wc_webhook_delivery"]
        cases.append((runtime, "webhook_tables_present"))
        runtime = FakeRuntime()
        runtime.active["sourceCommit"] = "f" * 40
        cases.append((runtime, "active_source_commit_mismatch"))
        for runtime, expected in cases:
            with self.subTest(expected=expected), self.assertRaises(
                MODULE.RemediationError
            ) as caught:
                self.make_plan(runtime)
            self.assertEqual(caught.exception.code, expected)

    def test_errors_and_receipts_never_expose_secret(self):
        runtime = FakeRuntime()
        plan = self.make_plan(runtime)
        runtime.environment += b"DRIFT=1\n"
        with tempfile.TemporaryDirectory() as root, self.assertRaises(
            MODULE.RemediationError
        ) as caught:
            MODULE.apply_transaction(
                runtime,
                plan_document=plan,
                authorization_document=authorization(plan),
                state_root=pathlib.Path(root),
                observed_at=OBSERVED,
                settle_seconds=0,
            )
        self.assertNotIn(SECRET, str(caught.exception))
        self.assertNotIn(SECRET, json.dumps(caught.exception.public()))


if __name__ == "__main__":
    unittest.main(verbosity=2)
