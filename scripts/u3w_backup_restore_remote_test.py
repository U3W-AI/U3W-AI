import base64
import datetime as dt
import hashlib
import importlib.util
import inspect
import json
import os
import pathlib
import sys
import tempfile
import types
import unittest
from unittest import mock


HERE = pathlib.Path(__file__).resolve().parent
if "fcntl" not in sys.modules:
    sys.modules["fcntl"] = types.SimpleNamespace(
        LOCK_EX=2, LOCK_NB=4, LOCK_UN=8, flock=lambda *_: None
    )
if "pwd" not in sys.modules:
    sys.modules["pwd"] = types.SimpleNamespace(
        getpwnam=lambda _: types.SimpleNamespace(pw_uid=0, pw_gid=0)
    )


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, HERE / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


backup = load("u3w_backup_remote", "u3w-production-backup-remote.py")
restore = load(
    "u3w_restore_remote", "u3w-isolated-restore-verifier-remote.py"
)
release = load(
    "u3w_release_remote_contract", "u3w-default-off-release-remote.py"
)


class RemoteSafetyTest(unittest.TestCase):
    def test_backup_and_restore_share_exact_receipt_field_contracts(self):
        self.assertEqual(
            backup.BACKUP_RECEIPT_FIELDS,
            restore.BACKUP_RECEIPT_FIELDS,
        )
        self.assertEqual(backup.FACT_FIELDS, restore.FACT_FIELDS)
        self.assertIn("planReceiptSha256", backup.BACKUP_RECEIPT_FIELDS)
        self.assertIn("planReceiptSha256", restore.RESTORE_RECEIPT_FIELDS)
        self.assertIn("planReceiptSha256", restore.BUNDLE_FIELDS)
        for field in (
            "sourceTotalRows",
            "sourceTableRowCountsSha256",
            *backup.ATTRIBUTION_DATA_FACT_FIELDS,
        ):
            self.assertNotIn(field, backup.PLAN_FIELDS)
            self.assertNotIn(field, restore.PLAN_FIELDS)
        self.assertIn("sourceControlFacts", backup.PLAN_FIELDS)
        self.assertIn("sourceControlFacts", restore.PLAN_FIELDS)
        for function in (
            backup.existing_receipt,
            restore.validate_backup_receipt,
        ):
            source = inspect.getsource(function)
            for field in (
                "businessDatabaseChanged",
                "serviceChanged",
                "officialExpertsPackageChanged",
            ):
                self.assertIn(field, source)

    def test_approved_plan_is_revalidated_against_locked_live_target(self):
        target = {
            "sourceDatabaseServerUuid":
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
            "serverVersion": "8.0.45",
            "serverVersionComment": "Source distribution",
            "sourceFacts": {
                "exact": True,
                **{
                    field: 0
                    for field in backup.ATTRIBUTION_DATA_FACT_FIELDS
                },
            },
            "sourceTotalRows": 12,
            "sourceTableRowCountsSha256": "1" * 64,
            "sourceJarSha256": "2" * 64,
            "encryptionContract": "u3w.gnupg-aes256-symmetric.v1",
            "encryptionKeyFingerprintSha256": "3" * 64,
            "encryptionKeyReady": True,
            "plannedDdlProtectionMode": backup.DDL_PROTECTION_MODE,
            "databaseProtectionActive": False,
            "sourceSnapshotExactlyMatched": False,
            "dumpToolVersion": "mysqldump exact",
            "wouldWritePath": "/opt/fbsir/admin/backups/w1a/exact",
        }
        plan = {
            key: value
            for key, value in target.items()
            if key not in (
                "sourceFacts",
                "sourceTotalRows",
                "sourceTableRowCountsSha256",
            )
        }
        plan["sourceControlFacts"] = {"exact": True}
        with (
            mock.patch.object(
                backup,
                "decode_plan_receipt",
                return_value=plan,
            ),
            mock.patch.object(backup, "assert_root_and_capacity"),
            mock.patch.object(
                backup,
                "current_plan_target",
                return_value=target,
            ),
        ):
            self.assertEqual(
                backup.validate_plan_against_live(
                    object(), object(), object()
                ),
                (plan, target),
            )
            target["sourceTableRowCountsSha256"] = "4" * 64
            target["sourceFacts"]["attributionEventCount"] = 1
            self.assertEqual(
                backup.validate_plan_against_live(
                    object(), object(), object()
                ),
                (plan, target),
            )
            target["sourceFacts"]["exact"] = False
            with self.assertRaisesRegex(
                RuntimeError,
                "target drifted",
            ):
                backup.validate_plan_against_live(
                    object(), object(), object()
                )
            target["sourceFacts"]["exact"] = True
            plan["sourceJarSha256"] = "4" * 64
            with self.assertRaisesRegex(
                RuntimeError,
                "target drifted",
            ):
                backup.validate_plan_against_live(
                    object(), object(), object()
                )

    def test_backup_plan_binds_only_static_control_facts(self):
        facts = {
            "staticShape": "exact",
            **{
                field: 0
                for field in backup.ATTRIBUTION_DATA_FACT_FIELDS
            },
        }
        target = {
            "sourceDatabaseServerUuid":
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
            "serverVersion": "8.0.45",
            "serverVersionComment": "Source distribution",
            "sourceFacts": facts,
            "sourceJarSha256": "2" * 64,
            "encryptionContract": "u3w.gnupg-aes256-symmetric.v1",
            "encryptionKeyFingerprintSha256": "3" * 64,
            "encryptionKeyReady": True,
            "plannedDdlProtectionMode": backup.DDL_PROTECTION_MODE,
            "databaseProtectionActive": False,
            "sourceSnapshotExactlyMatched": False,
            "dumpToolVersion": "mysqldump exact",
            "wouldWritePath": "/opt/fbsir/admin/backups/w1a/exact",
        }
        args = types.SimpleNamespace(
            run_id="w1a-20260724T000000Z-aaaaaaaaaaaa",
            source_commit="a" * 40,
            runner_sha="b" * 64,
            worker_sha="c" * 64,
            verifier_sha="d" * 64,
            admin_root_dependency_adoption_receipt_sha="e" * 64,
        )
        with (
            mock.patch.object(
                backup,
                "assert_root_and_capacity",
                return_value=(10_000_000_000, 10_000_000_000),
            ),
            mock.patch.object(
                backup,
                "current_plan_target",
                return_value=target,
            ),
        ):
            plan = backup.run_plan(args, object(), object())
        self.assertEqual(set(plan), backup.PLAN_FIELDS)
        self.assertEqual(
            plan["sourceControlFacts"],
            {"staticShape": "exact"},
        )

    def test_workers_harden_shared_lock_and_backup_database_lease(self):
        backup_lock = inspect.getsource(backup.open_host_change_lock)
        restore_lock = inspect.getsource(restore.open_host_change_lock)
        for source in (backup_lock, restore_lock):
            self.assertIn("O_NOFOLLOW", source)
            self.assertIn("st_nlink != 1", source)
            self.assertIn("st_mode & 0o022", source)
            self.assertIn("st_mode & 0o777 != 0o600", source)
            self.assertNotIn("os.fchmod", source)
        backup_flow = inspect.getsource(backup.run_backup)
        plan_flow = inspect.getsource(backup.run_plan)
        plan_target_flow = inspect.getsource(backup.current_plan_target)
        self.assertEqual(
            backup.PLAN_SCHEMA,
            "fbsir.u3wDatabaseBackupPlan.v3",
        )
        self.assertIn('"schema": PLAN_SCHEMA', plan_flow)
        self.assertIn('"plannedDdlProtectionMode"', plan_target_flow)
        self.assertIn(
            '"databaseProtectionActive": False',
            plan_target_flow,
        )
        self.assertIn(
            '"sourceSnapshotExactlyMatched": False',
            plan_target_flow,
        )
        self.assertNotIn(
            '"ddlProtectionMode": DDL_PROTECTION_MODE',
            plan_target_flow,
        )
        self.assertLess(
            backup_flow.index("database_lease.acquire()"),
            backup_flow.index("locked_identity, locked_facts"),
        )
        self.assertLess(
            backup_flow.index("database_lease.acquire()"),
            backup_flow.index("safe_run_directory(args.run_id)"),
        )
        self.assertLess(
            backup_flow.index("validate_plan_against_live("),
            backup_flow.index("safe_run_directory(args.run_id)"),
        )
        self.assertNotIn("manifest_after", backup_flow)
        self.assertIn(
            "static_control_facts(facts_before)",
            backup_flow,
        )
        restore_flow = inspect.getsource(restore.run_verification)
        existing_bundle_flow = inspect.getsource(
            restore.validate_existing_bundle
        )
        for source in (restore_flow, existing_bundle_flow):
            self.assertIn(
                "attribution_observations_compatible(",
                source,
            )
        self.assertIn(
            "START TRANSACTION WITH CONSISTENT SNAPSHOT",
            inspect.getsource(backup.DatabaseProtectionLease.acquire),
        )
        self.assertIn(
            "SELECT 1 FROM {} LIMIT 0",
            inspect.getsource(backup.DatabaseProtectionLease.acquire),
        )
        self.assertNotIn(
            "LOCK INSTANCE FOR BACKUP",
            inspect.getsource(backup.DatabaseProtectionLease.acquire),
        )
        self.assertIn(
            "SELECT GET_LOCK(%s,%s)",
            inspect.getsource(backup.DatabaseProtectionLease.acquire),
        )
        self.assertIn(
            "deadline = monotonic() + LOCK_TIMEOUT_SECONDS",
            inspect.getsource(backup.DatabaseProtectionLease.acquire),
        )
        self.assertIn(
            "database metadata protection acquisition timed out",
            inspect.getsource(backup.DatabaseProtectionLease.acquire),
        )
        self.assertIn(
            "acquire_bounded_flock(descriptor)",
            inspect.getsource(restore.main),
        )
        self.assertEqual(
            backup.DDL_PROTECTION_MODE,
            "HOST_FLOCK_NAMED_LOCK_READ_ONLY_SNAPSHOT_FULL_OBJECT_MDL_"
            "PRE_POST_STABILITY_AND_APPROVED_NO_DDL_WINDOW",
        )

    def test_attribution_observations_are_dormant_safe_and_monotonic(self):
        recorded = {
            "w1a043State": "EXACT_043_RETAINED_DORMANT",
            **{
                field: 0
                for field in backup.ATTRIBUTION_DATA_FACT_FIELDS
            },
        }
        recorded["attributionEventCount"] = 3
        recorded["attributionProbeEventCount"] = 3
        recorded["attributionJourneyCount"] = 1
        recorded["attributionProbeJourneyCount"] = 1
        current = dict(recorded)
        current["attributionEventCount"] = 5
        current["attributionProbeEventCount"] = 5
        current["attributionJourneyCount"] = 2
        current["attributionProbeJourneyCount"] = 2
        for worker in (backup, restore):
            self.assertTrue(
                worker.attribution_observations_compatible(
                    recorded, current
                )
            )
            self.assertFalse(
                worker.attribution_observations_compatible(
                    current, recorded
                )
            )
            unsafe = dict(current)
            unsafe["attributionNaturalEventCount"] = 1
            self.assertFalse(
                worker.attribution_observations_compatible(
                    recorded, unsafe
                )
            )

    def test_database_lease_holds_every_existing_object_mdl_until_rollback(
        self,
    ):
        class FakeCursor:
            def __init__(self, connection):
                self.connection = connection

            def __enter__(self):
                return self

            def __exit__(self, *_):
                return False

            def execute(self, sql, args=None):
                self.connection.statements.append((sql, args))
                self.connection.last_sql = sql

            def fetchone(self):
                if "GET_LOCK" in self.connection.last_sql:
                    return (1,)
                if "RELEASE_LOCK" in self.connection.last_sql:
                    return (1,)
                raise AssertionError(self.connection.last_sql)

            def fetchall(self):
                if "information_schema.tables" in self.connection.last_sql:
                    return (
                        ("alpha", "BASE TABLE"),
                        ("odd`name", "VIEW"),
                    )
                raise AssertionError(self.connection.last_sql)

        class FakeConnection:
            def __init__(self):
                self.statements = []
                self.last_sql = ""
                self.rollback_count = 0
                self.closed = False

            def cursor(self):
                return FakeCursor(self)

            def rollback(self):
                self.rollback_count += 1

            def close(self):
                self.closed = True

        connection = FakeConnection()
        fake_pymysql = types.SimpleNamespace(
            connect=mock.Mock(return_value=connection)
        )
        with mock.patch.dict(
            sys.modules, {"pymysql": fake_pymysql}
        ):
            lease = backup.DatabaseProtectionLease(
                {
                    "host": "127.0.0.1",
                    "port": "3306",
                    "user": "not-serialized",
                    "password": "not-serialized",
                }
            )
            lease.acquire()
            self.assertEqual(
                lease.protected_objects,
                (
                    ("alpha", "BASE TABLE"),
                    ("odd`name", "VIEW"),
                ),
            )
            statements = [item[0] for item in connection.statements]
            self.assertIn(
                "START TRANSACTION WITH CONSISTENT SNAPSHOT",
                statements,
            )
            self.assertIn("SELECT 1 FROM `alpha` LIMIT 0", statements)
            self.assertIn(
                "SELECT 1 FROM `odd``name` LIMIT 0",
                statements,
            )
            lease.close()
        self.assertEqual(connection.rollback_count, 1)
        self.assertTrue(connection.closed)
        self.assertTrue(
            any(
                "RELEASE_LOCK" in sql
                for sql, _ in connection.statements
            )
        )
        fake_pymysql.connect.assert_called_once()
        self.assertFalse(
            fake_pymysql.connect.call_args.kwargs["autocommit"]
        )

    def test_backup_and_restore_independently_bind_dependency_adoption(self):
        source_uuid = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        source_version = "8.0.45"
        source_commit = "b" * 40
        dependency_facts = {
            "legacyAdminRootDependencyState":
                "EXACT_CONTROLLED_DEPENDENCY",
            "legacyAdminRootDependencyFactsSha256": "1" * 64,
            "legacyAdminRootDependencyVersionCount": 1,
            "legacyAdminRootDependencyReceiptCount": 1,
            "legacyAdminRootIdentityCount": 1,
            "legacyAdminRootExactCount": 1,
            "legacyAdminRootRoleBindingCount": 0,
            "legacyAdminRootPageChildCount": 0,
            "legacyForbiddenPublicInit001Through042ReceiptCount": 0,
            "publicInit043AnyReceiptCount": 0,
            "attributionInternalReceiptCount": 0,
            "attributionTableCount": 0,
            "attributionTriggerCount": 0,
            "attributionPermissionCount": 0,
            "attributionEventCount": 0,
            "attributionProbeEventCount": 0,
            "attributionNaturalEventCount": 0,
            "attributionNonProbeEventCount": 0,
            "attributionAuthoritativeProductCreditCount": 0,
            "attributionJourneyCount": 0,
            "attributionProbeJourneyCount": 0,
            "attributionNaturalJourneyCount": 0,
            "attributionNonProbeJourneyCount": 0,
            "w1aSchemaFingerprintSha256": None,
            "w1a043State": "ABSENT",
        }
        live_facts = {
            "databaseServerUuid": source_uuid,
            "serverVersion": source_version,
            "rootIdentityCount": 1,
            "exactRootCount": 1,
            "rootRoleBindingCount": 0,
            "rootPageChildCount": 0,
            "dependencyVersionCount": 1,
            "dependencyReceiptCount": 1,
            "dependencyRowsFingerprintSha256":
                dependency_facts[
                    "legacyAdminRootDependencyFactsSha256"
                ],
            "forbiddenPublicInit001Through042ReceiptCount": 0,
            "publicInit043ReceiptCount": 0,
            "attributionInternalReceiptCount": 0,
            "attributionTableCount": 0,
            "attributionTriggerCount": 0,
            "attributionPermissionCount": 0,
            "attributionEventCount": 0,
            "attributionProbeEventCount": 0,
            "attributionNaturalEventCount": 0,
            "attributionNonProbeEventCount": 0,
            "attributionAuthoritativeProductCreditCount": 0,
            "attributionJourneyCount": 0,
            "attributionProbeJourneyCount": 0,
            "attributionNaturalJourneyCount": 0,
            "attributionNonProbeJourneyCount": 0,
            "w1aSchemaFingerprintSha256": None,
            "w1a043State": "ABSENT",
        }
        receipt = {
            "schema":
                "fbsir.u3wLegacyAdminRootDependencyAdoptionReceipt.v3",
            "sourceCommit": source_commit,
            "databaseServerUuid": source_uuid,
            "serverVersion": source_version,
            "adoptionState":
                "ADOPTED_EXISTING_EXACT_DEPENDENCY",
            "liveFacts": live_facts,
            "liveFactsSha256": backup.sha256_bytes(
                backup.canonical_json(live_facts).encode("utf-8")
            ),
        }
        receipt_text = backup.canonical_json(receipt)
        digest = backup.sha256_bytes(receipt_text.encode("utf-8"))

        class ReceiptPath:
            def resolve(self, strict=False):
                return self

            def stat(self):
                return types.SimpleNamespace(
                    st_uid=0,
                    st_gid=0,
                    st_mode=0o100600,
                    st_nlink=1,
                )

            def is_symlink(self):
                return False

            def is_file(self):
                return True

            def read_text(self, encoding=None):
                return receipt_text

        receipt_path = ReceiptPath()
        args = types.SimpleNamespace(
            source_commit=source_commit,
            admin_root_dependency_adoption_receipt_sha=digest,
        )
        with (
            mock.patch.object(
                backup,
                "ADMIN_ROOT_DEPENDENCY_RECEIPT",
                receipt_path,
            ),
            mock.patch.object(
                restore,
                "ADMIN_ROOT_DEPENDENCY_RECEIPT",
                receipt_path,
            ),
            mock.patch.object(backup, "sha256_file", return_value=digest),
            mock.patch.object(restore, "sha256_file", return_value=digest),
        ):
            backup.validate_admin_root_dependency_adoption(
                args,
                [source_version, "Source distribution", source_uuid],
                dependency_facts,
            )
            restore.validate_admin_root_dependency_adoption(
                args,
                source_uuid,
                source_version,
                dependency_facts,
            )
            dependency_facts["attributionTableCount"] = 1
            with self.assertRaisesRegex(RuntimeError, "facts drifted"):
                restore.validate_admin_root_dependency_adoption(
                    args,
                    source_uuid,
                    source_version,
                    dependency_facts,
                )
            dependency_facts["attributionTableCount"] = 0
            dependency_facts["w1aSchemaFingerprintSha256"] = "9" * 64
            for worker, identity in (
                (
                    backup,
                    [source_version, "Source distribution", source_uuid],
                ),
                (restore, source_uuid),
            ):
                with self.subTest(
                    worker=worker.__name__,
                    drift="w1a-schema-fingerprint",
                ):
                    with self.assertRaisesRegex(
                        RuntimeError, "facts drifted"
                    ):
                        if worker is backup:
                            worker.validate_admin_root_dependency_adoption(
                                args,
                                identity,
                                dependency_facts,
                            )
                        else:
                            worker.validate_admin_root_dependency_adoption(
                                args,
                                identity,
                                source_version,
                                dependency_facts,
                            )

    def test_backup_and_restore_accept_only_exact_absent_or_retained_043(self):
        class FakeMysql:
            def __init__(self, state, unsafe_natural=False):
                self.state = state
                self.unsafe_natural = unsafe_natural

            def query(self, sql):
                if "SELECT row_value FROM (" in sql:
                    return "D|dependency\nR|root"
                if "FROM fbs_board_attr_event_v1" in sql:
                    if "authoritative_product_credit<>0" in sql:
                        return 0
                    if "traffic_class=BINARY 'NATURAL'" in sql:
                        return 1 if self.unsafe_natural else 0
                    if "traffic_class<>BINARY 'PROBE'" in sql:
                        return 1 if self.unsafe_natural else 0
                    if "traffic_class=BINARY 'PROBE'" in sql:
                        return 2 if self.unsafe_natural else 3
                    return 3
                if "FROM fbs_board_attr_journey_v1" in sql:
                    if "traffic_class=BINARY 'NATURAL'" in sql:
                        return 1 if self.unsafe_natural else 0
                    if "traffic_class<>BINARY 'PROBE'" in sql:
                        return 1 if self.unsafe_natural else 0
                    if "traffic_class=BINARY 'PROBE'" in sql:
                        return 0 if self.unsafe_natural else 1
                    return 1
                if "sys_role_menu" in sql:
                    return 0
                if "menu_type IN ('M','C')" in sql:
                    return 0
                if "version IN (" in sql:
                    return 0
                if "version='public_init_043'" in sql:
                    return self.state[0]
                if (
                    "20260723_independent_board_attribution_v1_043"
                    in sql
                ):
                    return self.state[1]
                if "information_schema.tables" in sql:
                    return self.state[2]
                if "information_schema.triggers" in sql:
                    return self.state[3]
                if "board:attribution:query" in sql:
                    return self.state[4]
                if (
                    "version='" + backup.DEPENDENCY_VERSION + "'"
                    in sql
                ):
                    return 1
                if "FROM sys_menu WHERE" in sql:
                    return 1
                raise AssertionError(sql)

        for worker in (backup, restore):
            with self.subTest(worker=worker.__name__, state="absent"):
                facts = worker.admin_root_dependency_facts(
                    FakeMysql((0, 0, 0, 0, 0))
                )
                self.assertEqual(facts["w1a043State"], "ABSENT")
                self.assertIsNone(
                    facts["w1aSchemaFingerprintSha256"]
                )
            with self.subTest(worker=worker.__name__, state="retained"):
                with mock.patch.object(
                    worker,
                    "w1a_schema_fingerprint",
                    return_value=worker.EXPECTED_W1A_SCHEMA_FINGERPRINT,
                ):
                    facts = worker.admin_root_dependency_facts(
                        FakeMysql((1, 1, 2, 2, 1))
                    )
                self.assertEqual(
                    facts["w1a043State"],
                    "EXACT_043_RETAINED_DORMANT",
                )
                self.assertEqual(
                    facts["w1aSchemaFingerprintSha256"],
                    worker.EXPECTED_W1A_SCHEMA_FINGERPRINT,
                )
                self.assertEqual(facts["attributionEventCount"], 3)
                self.assertEqual(facts["attributionProbeEventCount"], 3)
                self.assertEqual(facts["attributionNaturalEventCount"], 0)
                self.assertEqual(
                    facts[
                        "attributionAuthoritativeProductCreditCount"
                    ],
                    0,
                )
            with self.subTest(worker=worker.__name__, state="partial"):
                with self.assertRaisesRegex(
                    RuntimeError,
                    "dormant state is not exact",
                ):
                    worker.admin_root_dependency_facts(
                        FakeMysql((1, 1, 2, 1, 1))
                    )
            with self.subTest(worker=worker.__name__, state="natural"):
                with self.assertRaisesRegex(
                    RuntimeError,
                    "dormant state is not exact",
                ):
                    worker.admin_root_dependency_facts(
                        FakeMysql(
                            (1, 1, 2, 2, 1),
                            unsafe_natural=True,
                        )
                    )
            with self.subTest(
                worker=worker.__name__,
                state="schema-fingerprint-drift",
            ):
                with (
                    mock.patch.object(
                        worker,
                        "w1a_schema_fingerprint",
                        return_value="9" * 64,
                    ),
                    self.assertRaisesRegex(
                        RuntimeError,
                        "dormant state is not exact",
                    ),
                ):
                    worker.admin_root_dependency_facts(
                        FakeMysql((1, 1, 2, 2, 1))
                    )

    def test_backup_restore_w1a_fingerprint_matches_release_contract(self):
        class QueryMysql:
            def __init__(self):
                self.statement = None

            def query(self, sql):
                self.statement = sql
                return "A|first\nB|second"

        class RowsMysql:
            def __init__(self):
                self.statement = None

            def rows(self, sql):
                self.statement = sql
                return [("A|first",), ("B|second",)]

        release_mysql = RowsMysql()
        release_digest = release.migration_fingerprint(release_mysql)
        for worker in (backup, restore):
            with self.subTest(worker=worker.__name__):
                mysql = QueryMysql()
                self.assertEqual(
                    worker.w1a_schema_fingerprint(mysql),
                    release_digest,
                )
                self.assertEqual(
                    mysql.statement,
                    release_mysql.statement,
                )
                self.assertEqual(
                    worker.EXPECTED_W1A_SCHEMA_FINGERPRINT,
                    release.EXPECTED_W1A_SCHEMA_FINGERPRINT,
                )

    def test_atomic_writers_reject_a_stale_hardlink(self):
        for writer in (
            lambda target: backup.atomic_json(target, {"ok": True}),
            lambda target: restore.atomic_bytes(target, b"safe\n"),
        ):
            with self.subTest(writer=writer):
                with tempfile.TemporaryDirectory() as directory:
                    root = pathlib.Path(directory)
                    victim = root / "victim"
                    victim.write_text("unchanged", encoding="utf-8")
                    target = root / "receipt.json"
                    os.link(
                        victim, target.with_name(target.name + ".partial")
                    )
                    with self.assertRaises(RuntimeError):
                        writer(target)
                    self.assertEqual(
                        victim.read_text(encoding="utf-8"), "unchanged"
                    )

    def test_remote_approval_is_exactly_bound_and_zero_digest_is_rejected(self):
        now = dt.datetime.now(dt.timezone.utc)
        approval = {
            "schema": "fbsir.u3wProductionChangeApprovalReceipt.v1",
            "action": "DATABASE_BACKUP_AND_ISOLATED_RESTORE",
            "targetHost": "api2.u3w.com",
            "runId": "w1a-20260723T180000Z-aaaaaaaaaaaa",
            "sourceCommit": "b" * 40,
            "approvedAt": now.isoformat().replace("+00:00", "Z"),
            "expiresAt": (now + dt.timedelta(hours=1))
            .isoformat()
            .replace("+00:00", "Z"),
            "authorizedBy": "workspace-user",
            "concurrentDdlProhibited": True,
            "productionFilesystemWrite": True,
            "productionDatabaseWrite": False,
            "productionServiceChange": False,
            "officialExpertsPackageChange": False,
            "expectedAdminRootDependencyAdoptionReceiptSha256":
                "e" * 64,
            "expectedBackupPlanReceiptSha256": "f" * 64,
        }
        payload = (
            json.dumps(approval, separators=(",", ":")) + "\n"
        ).encode()
        args = types.SimpleNamespace(
            mode="Backup",
            run_id=approval["runId"],
            source_commit=approval["sourceCommit"],
            approval_sha=hashlib.sha256(payload).hexdigest(),
            approval_json_base64=base64.b64encode(payload).decode(),
            plan_receipt_sha="f" * 64,
            plan_json_base64="e30=",
            runner_sha="c" * 64,
            worker_sha="d" * 64,
            verifier_sha="1" * 64,
            admin_root_dependency_adoption_receipt_sha="e" * 64,
        )
        backup.validate_arguments(args)
        args.approval_sha = "0" * 64
        with self.assertRaises(RuntimeError):
            backup.validate_arguments(args)


if __name__ == "__main__":
    unittest.main()
