import base64
import datetime as dt
import errno
import importlib.util
import inspect
import json
import pathlib
import sys
import types
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parent
if "fcntl" not in sys.modules:
    sys.modules["fcntl"] = types.SimpleNamespace(
        LOCK_EX=2,
        LOCK_NB=4,
        LOCK_UN=8,
        flock=lambda *_: None,
    )


def load_worker():
    path = ROOT / "u3w-admin-root-dependency-remote.py"
    spec = importlib.util.spec_from_file_location(
        "u3w_admin_root_dependency_remote",
        path,
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


worker = load_worker()


class Args:
    run_id = (
        "w1a-admin-root-dependency-"
        "20260724T190000Z-0123456789ab"
    )
    source_commit = "a" * 40
    approval_sha = ""
    approval_json_base64 = ""
    plan_sha = "1" * 64
    plan_json_base64 = ""
    runner_sha = "b" * 64
    worker_sha = "c" * 64
    expected_baseline_receipt_sha = "d" * 64
    expected_backup_receipt_sha = "e" * 64
    lock_timeout_seconds = 15


def approval_payload():
    return {
        "schema": "fbsir.u3wProductionChangeApprovalReceipt.v1",
        "action": "ADOPT_W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY",
        "targetHost": "api2.u3w.com",
        "runId": Args.run_id,
        "sourceCommit": Args.source_commit,
        "approvedAt": "2026-07-24T18:50:00Z",
        "expiresAt": "2026-07-24T19:50:00Z",
        "authorizedBy": "workspace-user",
        "concurrentDdlProhibited": True,
        "productionFilesystemWrite": True,
        "productionDatabaseWrite": False,
        "productionServiceChange": False,
        "officialExpertsPackageChange": False,
        "database": "fbsir",
        "databaseEndpoint": "127.0.0.1:3306",
        "databaseServerUuid": "01234567-89ab-cdef-0123-456789abcdef",
        "expectedLiveFactsSha256": "f" * 64,
        "expectedDatabaseProtectionMode":
            worker.DATABASE_PROTECTION_MODE,
        "expectedPlanReceiptSha256": Args.plan_sha,
        "expectedBaselineReceiptSha256":
            Args.expected_baseline_receipt_sha,
        "expectedBackupReceiptSha256":
            Args.expected_backup_receipt_sha,
        "runnerSha256": Args.runner_sha,
        "workerSha256": Args.worker_sha,
    }


def approval_args(payload=None):
    payload = payload or approval_payload()
    raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    args = types.SimpleNamespace(
        run_id=Args.run_id,
        source_commit=Args.source_commit,
        approval_sha=worker.sha256_bytes(raw),
        approval_json_base64=base64.b64encode(raw).decode("ascii"),
        plan_sha=Args.plan_sha,
        plan_json_base64="",
        runner_sha=Args.runner_sha,
        worker_sha=Args.worker_sha,
        expected_baseline_receipt_sha=Args.expected_baseline_receipt_sha,
        expected_backup_receipt_sha=Args.expected_backup_receipt_sha,
        lock_timeout_seconds=Args.lock_timeout_seconds,
    )
    return args


def exact_rows():
    menus = [
        (
            9001,
            "独董会管理",
            0,
            5,
            "independent-board-admin",
            None,
            None,
            "IndependentBoardAdmin",
            1,
            0,
            "M",
            "0",
            "0",
            None,
            "peoples",
        )
    ]
    role_menus = []
    migrations = [
        (
            worker.DEPENDENCY_MIGRATION_VERSION,
            worker.DEPENDENCY_MIGRATION_DESCRIPTION,
        ),
        ("legacy_w1a_baseline_20260724_001", "baseline"),
    ]
    return menus, role_menus, migrations


class ApprovalContractTest(unittest.TestCase):
    def test_approval_is_exact_and_read_only(self):
        args = approval_args()
        result = worker.validate_approval(
            args,
            {
                "host": "127.0.0.1",
                "port": 3306,
            },
            "01234567-89ab-cdef-0123-456789abcdef",
            now=dt.datetime(
                2026, 7, 24, 19, 0, tzinfo=dt.timezone.utc
            ),
        )
        self.assertEqual(
            result["action"],
            "ADOPT_W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY",
        )
        self.assertFalse(result["productionDatabaseWrite"])
        self.assertEqual(
            result["expectedDatabaseProtectionMode"],
            worker.DATABASE_PROTECTION_MODE,
        )
        self.assertEqual(
            result["expectedPlanReceiptSha256"],
            Args.plan_sha,
        )

        drifted = approval_payload()
        drifted["productionDatabaseWrite"] = True
        with self.assertRaisesRegex(RuntimeError, "scope"):
            worker.validate_approval(
                approval_args(drifted),
                {"host": "127.0.0.1", "port": 3306},
                "01234567-89ab-cdef-0123-456789abcdef",
                now=dt.datetime(
                    2026, 7, 24, 19, 0, tzinfo=dt.timezone.utc
                ),
            )
        drifted = approval_payload()
        drifted["expectedDatabaseProtectionMode"] = "LOCK_INSTANCE"
        with self.assertRaisesRegex(RuntimeError, "scope"):
            worker.validate_approval(
                approval_args(drifted),
                {"host": "127.0.0.1", "port": 3306},
                "01234567-89ab-cdef-0123-456789abcdef",
                now=dt.datetime(
                    2026, 7, 24, 19, 0, tzinfo=dt.timezone.utc
                ),
            )


class FactsContractTest(unittest.TestCase):
    def test_exact_existing_dependency_is_adoptable(self):
        menus, role_menus, migrations = exact_rows()
        facts = worker.facts_from_locked_rows(
            "01234567-89ab-cdef-0123-456789abcdef",
            "8.0.45",
            menus,
            role_menus,
            migrations,
        )
        worker.assert_exact_live_facts(facts)
        self.assertEqual(facts["exactRootCount"], 1)
        self.assertEqual(facts["dependencyReceiptCount"], 1)
        self.assertRegex(
            facts["dependencyRowsFingerprintSha256"],
            r"^[0-9a-f]{64}$",
        )
        self.assertEqual(facts["publicInit043ReceiptCount"], 0)
        self.assertEqual(facts["attributionInternalReceiptCount"], 0)
        self.assertEqual(facts["attributionTableCount"], 0)
        self.assertEqual(facts["attributionTriggerCount"], 0)
        self.assertEqual(facts["attributionPermissionCount"], 0)
        self.assertEqual(facts["rootRoleBindingCount"], 0)
        self.assertEqual(facts["rootPageChildCount"], 0)

    def test_rejects_absent_ambiguous_bound_or_post_043_states(self):
        menus, role_menus, migrations = exact_rows()
        cases = []
        cases.append(([], role_menus, migrations))
        cases.append((menus + menus, role_menus, migrations))
        cases.append((menus, [(1, 9001)], migrations))
        child = (
            9002,
            "child",
            9001,
            1,
            "child",
            "child/index",
            None,
            "Child",
            1,
            0,
            "C",
            "0",
            "0",
            None,
            "#",
        )
        cases.append((menus + [child], role_menus, migrations))
        cases.append(
            (
                menus,
                role_menus,
                migrations
                + [(worker.PUBLIC_INIT_043_VERSION, "already applied")],
            )
        )
        cases.append(
            (
                menus,
                role_menus,
                migrations
                + [
                    (
                        worker.ATTRIBUTION_INTERNAL_043_VERSION,
                        "already applied",
                    )
                ],
            )
        )
        cases.append(
            (
                menus,
                role_menus,
                migrations + [("public_init_042", "fabricated")],
            )
        )
        for case in cases:
            with self.subTest(case=case):
                facts = worker.facts_from_locked_rows(
                    "01234567-89ab-cdef-0123-456789abcdef",
                    "8.0.45",
                    *case,
                )
                with self.assertRaisesRegex(RuntimeError, "not adoptable"):
                    worker.assert_exact_live_facts(facts)
        for object_counts in ((1, 0, 0), (0, 1, 0), (0, 0, 1)):
            with self.subTest(object_counts=object_counts):
                facts = worker.facts_from_locked_rows(
                    "01234567-89ab-cdef-0123-456789abcdef",
                    "8.0.45",
                    menus,
                    role_menus,
                    migrations,
                    *object_counts,
                )
                with self.assertRaisesRegex(RuntimeError, "not adoptable"):
                    worker.assert_exact_live_facts(facts)

    def test_collector_uses_repeatable_read_and_full_range_share_locks(self):
        menus, role_menus, migrations = exact_rows()

        class FakeMysql:
            def __init__(self):
                self.statements = []

            def execute(self, sql, args=None):
                self.statements.append(sql)

            def rows(self, sql, args=None):
                self.statements.append(sql)
                if "@@server_uuid" in sql:
                    return [
                        (
                            "01234567-89ab-cdef-0123-456789abcdef",
                            "fbsir",
                            "8.0.45",
                        )
                    ]
                if "information_schema.tables" in sql:
                    return [(0, 0, 0)]
                if "FROM sys_role_menu" in sql:
                    return role_menus
                if "FROM sys_menu" in sql:
                    return menus
                if "FROM u3w_schema_migration" in sql:
                    return migrations
                raise AssertionError(sql)

        mysql = FakeMysql()
        facts = worker.collect_locked_live_facts(mysql)
        worker.assert_exact_live_facts(facts)
        self.assertIn(
            "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ",
            mysql.statements,
        )
        locked = [
            statement
            for statement in mysql.statements
            if statement.rstrip().endswith("FOR SHARE")
        ]
        self.assertEqual(len(locked), 3)
        self.assertTrue(
            all(statement.rstrip().endswith("FOR SHARE") for statement in locked)
        )


class LockAndReceiptContractTest(unittest.TestCase):
    def test_historical_latest_roots_match_real_run_topology(self):
        self.assertEqual(
            worker.BASELINE_ROOT,
            pathlib.Path("/opt/fbsir/admin/baselines/w1a"),
        )
        self.assertEqual(
            worker.BACKUP_ROOT,
            pathlib.Path("/opt/fbsir/admin/backups/w1a"),
        )
        source = inspect.getsource(worker.resolve_latest_receipt)
        self.assertIn("target.parent != root", source)

    def test_host_lock_wait_is_bounded(self):
        descriptor = 91
        clock = iter((0.0, 0.0, 0.4, 1.1))
        with (
            mock.patch.object(
                worker.fcntl,
                "flock",
                side_effect=BlockingIOError(errno.EAGAIN, "busy"),
            ),
            self.assertRaisesRegex(RuntimeError, "timed out"),
        ):
            worker.acquire_bounded_flock(
                descriptor,
                timeout_seconds=1.0,
                monotonic=lambda: next(clock),
                sleeper=lambda _: None,
            )

    def test_plan_is_live_fact_bound_and_zero_write(self):
        menus, role_menus, migrations = exact_rows()
        facts = worker.facts_from_locked_rows(
            "01234567-89ab-cdef-0123-456789abcdef",
            "8.0.45",
            menus,
            role_menus,
            migrations,
        )

        class FakeMysql:
            def __init__(self):
                self.statements = []
                self.rollbacks = 0
                self.closed = False

            def scalar(self, sql, args=None):
                self.statements.append(sql)
                if "LOWER(@@server_uuid)" in sql:
                    return "01234567-89ab-cdef-0123-456789abcdef"
                if "GET_LOCK" in sql or "RELEASE_LOCK" in sql:
                    return 1
                raise AssertionError(sql)

            def execute(self, sql, args=None):
                self.statements.append(sql)

            def rollback(self):
                self.rollbacks += 1

            def close(self):
                self.closed = True

        mysql = FakeMysql()
        args = types.SimpleNamespace(
            run_id=Args.run_id,
            source_commit=Args.source_commit,
            approval_sha="0" * 64,
            approval_json_base64="",
            runner_sha=Args.runner_sha,
            worker_sha=Args.worker_sha,
            expected_baseline_receipt_sha=
                Args.expected_baseline_receipt_sha,
            expected_backup_receipt_sha=
                Args.expected_backup_receipt_sha,
            lock_timeout_seconds=Args.lock_timeout_seconds,
        )
        baseline = pathlib.Path(
            "/opt/fbsir/admin/baselines/w1a/b/adoption-receipt.json"
        )
        backup = pathlib.Path(
            "/opt/fbsir/admin/backups/w1a/b/receipt.json"
        )
        worker.MUTATION_STATE["productionFilesystemChanged"] = False
        with (
            mock.patch.object(
                worker.os, "geteuid", return_value=0, create=True
            ),
            mock.patch.object(
                worker, "open_host_change_lock", return_value=71
            ) as open_lock,
            mock.patch.object(worker, "acquire_bounded_flock"),
            mock.patch.object(
                worker,
                "resolve_latest_receipt",
                side_effect=(baseline, backup),
            ),
            mock.patch.object(
                worker,
                "parse_environment",
                return_value={
                    "host": "127.0.0.1",
                    "port": 3306,
                    "user": "not-serialized",
                    "password": "not-serialized",
                },
            ),
            mock.patch.object(worker, "Mysql", return_value=mysql),
            mock.patch.object(
                worker,
                "collect_locked_live_facts",
                return_value=facts,
            ),
            mock.patch.object(
                worker,
                "validate_approval",
                side_effect=AssertionError(
                    "Plan must not consume an approval"
                ),
            ),
            mock.patch.object(worker.fcntl, "flock"),
            mock.patch.object(worker.os, "close"),
        ):
            result = worker.plan(args)
        open_lock.assert_called_once_with(create=False)
        worker.validate_plan(result)
        self.assertEqual(
            result["schema"],
            "fbsir.u3wAdminRootDependencyPlan.v1",
        )
        self.assertEqual(result["mode"], "Plan")
        self.assertEqual(
            result["databaseProtectionMode"],
            worker.DATABASE_PROTECTION_MODE,
        )
        self.assertTrue(result["approvalRequired"])
        self.assertTrue(result["wouldWriteProductionFilesystem"])
        self.assertFalse(result["productionFilesystemChanged"])
        self.assertFalse(result["productionDatabaseChanged"])
        self.assertFalse(result["productionServiceChanged"])
        self.assertFalse(result["officialExpertsPackageChanged"])
        self.assertEqual(
            result["baselineLatestReceiptPath"], str(baseline)
        )
        self.assertEqual(
            result["backupLatestReceiptPath"], str(backup)
        )
        self.assertNotIn("LOCK INSTANCE FOR BACKUP", mysql.statements)
        self.assertNotIn("UNLOCK INSTANCE", mysql.statements)
        self.assertTrue(
            any("GET_LOCK" in value for value in mysql.statements)
        )
        self.assertTrue(
            any("RELEASE_LOCK" in value for value in mysql.statements)
        )
        self.assertGreaterEqual(mysql.rollbacks, 1)
        self.assertTrue(mysql.closed)
        self.assertFalse(
            worker.MUTATION_STATE["productionFilesystemChanged"]
        )
        serialized = worker.canonical_json(result).lower()
        self.assertNotIn("not-serialized", serialized)
        self.assertNotIn("approvaljson", serialized)

    def test_plan_arguments_do_not_require_an_approval(self):
        args = worker.parse_args(
            [
                "--mode",
                "Plan",
                "--run-id",
                Args.run_id,
                "--source-commit",
                Args.source_commit,
                "--runner-sha",
                Args.runner_sha,
                "--worker-sha",
                Args.worker_sha,
                "--expected-baseline-receipt-sha",
                Args.expected_baseline_receipt_sha,
                "--expected-backup-receipt-sha",
                Args.expected_backup_receipt_sha,
            ]
        )
        self.assertEqual(args.mode, "Plan")
        self.assertEqual(args.approval_sha, "0" * 64)
        self.assertEqual(args.approval_json_base64, "")
        self.assertEqual(args.plan_sha, "0" * 64)
        self.assertEqual(args.plan_json_base64, "")

    def test_adopt_is_bound_to_exact_plan_bytes(self):
        menus, role_menus, migrations = exact_rows()
        facts = worker.facts_from_locked_rows(
            "01234567-89ab-cdef-0123-456789abcdef",
            "8.0.45",
            menus,
            role_menus,
            migrations,
        )
        baseline = pathlib.Path(
            "/opt/fbsir/admin/baselines/w1a/b/adoption-receipt.json"
        )
        backup = pathlib.Path(
            "/opt/fbsir/admin/backups/w1a/b/receipt.json"
        )
        plan = worker.build_plan(
            approval_args(),
            {"host": "127.0.0.1", "port": 3306},
            facts,
            str(baseline),
            str(backup),
            observed_at="2026-07-24T18:45:00.000Z",
        )
        raw = (
            json.dumps(plan, indent=2, ensure_ascii=False) + "\n"
        ).encode("utf-8")
        args = approval_args()
        args.plan_sha = worker.sha256_bytes(raw)
        args.plan_json_base64 = base64.b64encode(raw).decode("ascii")
        self.assertEqual(
            worker.validate_adopt_plan(
                args,
                {"host": "127.0.0.1", "port": 3306},
                "01234567-89ab-cdef-0123-456789abcdef",
                baseline,
                backup,
            ),
            plan,
        )
        args.plan_sha = "9" * 64
        with self.assertRaisesRegex(RuntimeError, "identity changed"):
            worker.validate_adopt_plan(
                args,
                {"host": "127.0.0.1", "port": 3306},
                "01234567-89ab-cdef-0123-456789abcdef",
                baseline,
                backup,
            )

    def test_latest_pointer_is_custody_checked_and_monotonic(self):
        validator = inspect.getsource(
            worker.validate_adoption_run_directory
        )
        publisher = inspect.getsource(worker.publish_latest_cas)
        opener = inspect.getsource(worker.open_host_change_lock)
        adopter = inspect.getsource(worker.adopt)
        self.assertIn("path.parent != root", validator)
        self.assertIn("RUN_PATTERN.fullmatch(path.name)", validator)
        self.assertIn("status.st_uid != 0", validator)
        self.assertIn("observed.name > run_directory.name", publisher)
        self.assertIn("adoption latest pointer would regress", publisher)
        self.assertIn("create=False", inspect.getsource(worker.plan))
        self.assertIn("O_NOFOLLOW", opener)
        self.assertIn("status.st_nlink != 1", opener)
        self.assertIn("0o600", opener)
        self.assertIn(
            'approval["expectedLiveFactsSha256"]',
            adopter,
        )
        self.assertIn(
            "approved admin-root dependency live facts drifted",
            adopter,
        )
        self.assertIn("validate_adopt_plan", adopter)

    def test_receipt_is_exact_secret_free_and_current_state_only(self):
        menus, role_menus, migrations = exact_rows()
        facts = worker.facts_from_locked_rows(
            "01234567-89ab-cdef-0123-456789abcdef",
            "8.0.45",
            menus,
            role_menus,
            migrations,
        )
        receipt = worker.build_receipt(
            approval_args(),
            {"host": "127.0.0.1", "port": 3306},
            facts,
            observed_at="2026-07-24T19:00:00.000Z",
        )
        worker.validate_receipt(receipt)
        serialized = worker.canonical_json(receipt)
        self.assertEqual(
            receipt["schema"],
            "fbsir.u3wLegacyAdminRootDependencyAdoptionReceipt.v2",
        )
        self.assertEqual(
            receipt["adoptionState"],
            "ADOPTED_EXISTING_EXACT_DEPENDENCY",
        )
        self.assertEqual(
            receipt["adoptionClaim"],
            "CURRENT_STATE_ONLY_NOT_ORIGINAL_EXECUTION",
        )
        self.assertEqual(
            receipt["databaseProtectionMode"],
            worker.DATABASE_PROTECTION_MODE,
        )
        self.assertEqual(receipt["planReceiptSha256"], Args.plan_sha)
        self.assertFalse(receipt["originalExecutionClaimed"])
        self.assertFalse(receipt["productionDatabaseChanged"])
        self.assertNotIn("password", serialized.lower())
        self.assertNotIn("approvalJson", serialized)


class RunnerContractTest(unittest.TestCase):
    def test_runner_is_strict_head_pinned_and_immutable(self):
        runner = (
            ROOT / "run-u3w-admin-root-dependency.ps1"
        ).read_text(encoding="utf-8")
        required = (
            "Get-CommittedBlobBytes",
            "cat-file blob",
            "git -C $RepoRoot status --porcelain=v1",
            "git -C $RepoRoot ls-remote --exit-code origin",
            "StrictHostKeyChecking=yes",
            "SHA256:oCIzbO9W94qDBeS9MtetLCW0CjYMkyZqT6QkgcLkXk0",
            "SHA256:GkS/HJpLm48N+KaMV/WEVSBHnI8PKJsU+6ycAsn+mBA",
            "u3w-admin-root-dependency-remote.py",
            "adoption-receipt.json",
            "[IO.FileMode]::CreateNew",
            "expected-baseline-receipt-sha",
            "expected-backup-receipt-sha",
            "[ValidateSet('Plan', 'Adopt')]",
            "'--mode', $Mode",
            "fbsir.u3wAdminRootDependencyPlan.v1",
            "fbsir.u3wAdminRootDependencyRunnerResult.v3",
            "if ($Mode -eq 'Plan')",
            "expectedLiveFactsSha256",
            "expectedDatabaseProtectionMode",
            "expectedPlanReceiptSha256",
            "PlanReceiptPath",
            "ExpectedPlanReceiptSha256",
            "'--plan-sha', $script:PlanReceiptSha256",
            "'--plan-json-base64', $script:PlanBase64",
        )
        for needle in required:
            with self.subTest(needle=needle):
                self.assertIn(needle, runner)


if __name__ == "__main__":
    unittest.main()
