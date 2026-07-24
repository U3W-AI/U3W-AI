import base64
import datetime as dt
import importlib.util
import inspect
import io
import json
import pathlib
import sys
import tarfile
import tempfile
import types
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parent
if "fcntl" not in sys.modules:
    sys.modules["fcntl"] = types.SimpleNamespace(
        LOCK_EX=2, LOCK_SH=1, LOCK_UN=8, flock=lambda *_: None
    )


def load_module():
    spec = importlib.util.spec_from_file_location(
        "u3w_default_off_release_remote",
        ROOT / "u3w-default-off-release-remote.py",
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


release = load_module()


def release_args(mode="FinalizeStage"):
    now = dt.datetime.now(dt.timezone.utc)
    args = types.SimpleNamespace(
        mode=mode,
        release_id="w1a-release-0123456789ab-20260723T180000Z",
        source_commit="a" * 40,
        approval_sha="",
        approval_json_base64="",
        runner_sha="b" * 64,
        worker_sha="c" * 64,
        build_receipt_sha="d" * 64,
        plan_receipt_sha="e" * 64,
        backup_receipt_sha="f" * 64,
        baseline_receipt_sha="1" * 64,
        configuration_receipt_sha="2" * 64,
        stage_receipt_sha="0" * 64,
        deployment_receipt_sha="0" * 64,
        backend_sha="3" * 64,
        frontend_tree_sha="4" * 64,
        migration_sha="5" * 64,
    )
    normalized_mode = (
        "Stage"
        if mode in ("PrepareStage", "FinalizeStage")
        else mode
    )
    actions = {
        "Stage": "STAGE_W1A_DEFAULT_OFF_RELEASE",
        "Apply": "APPLY_W1A_DEFAULT_OFF_RELEASE",
        "Rollback": "ROLLBACK_W1A_DEFAULT_OFF_RELEASE",
        "Verify": "VERIFY_W1A_DEFAULT_OFF_RELEASE",
    }
    payload = {
        "schema": "fbsir.u3wProductionChangeApprovalReceipt.v1",
        "action": actions[normalized_mode],
        "targetHost": "api2.u3w.com",
        "runId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvedAt": (now - dt.timedelta(minutes=1))
        .isoformat()
        .replace("+00:00", "Z"),
        "expiresAt": (now + dt.timedelta(hours=1))
        .isoformat()
        .replace("+00:00", "Z"),
        "authorizedBy": "workspace-user",
        "concurrentDdlProhibited": True,
        "productionFilesystemWrite": normalized_mode
        in ("Stage", "Apply", "Rollback"),
        "productionDatabaseWrite": normalized_mode == "Apply",
        "productionServiceChange": normalized_mode
        in ("Apply", "Rollback"),
        "officialExpertsPackageChange": False,
        "expectedBuildReceiptSha256": args.build_receipt_sha,
        "expectedReleasePlanReceiptSha256": args.plan_receipt_sha,
        "expectedBackupReceiptSha256": args.backup_receipt_sha,
        "expectedLegacyBaselineReceiptSha256": args.baseline_receipt_sha,
        "expectedConfigurationReceiptSha256":
            args.configuration_receipt_sha,
        "expectedStageReceiptSha256": args.stage_receipt_sha,
        "expectedDeploymentReceiptSha256":
            args.deployment_receipt_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
    }
    raw = json.dumps(payload, separators=(",", ":")).encode()
    args.approval_sha = release.sha256_bytes(raw)
    args.approval_json_base64 = base64.b64encode(raw).decode()
    return args, payload


def exact_migration_facts(event_count=0, journey_count=0):
    return {
        "publicReceiptCount": 1,
        "internalReceiptCount": 1,
        "tableCount": 2,
        "triggerCount": 2,
        "permissionCount": 1,
        "eventCount": event_count,
        "journeyCount": journey_count,
        "schemaFingerprintSha256":
            release.EXPECTED_W1A_SCHEMA_FINGERPRINT,
    }


def runtime_identity(state="ABSENT", event_count=0, journey_count=0):
    retained = state == "EXACT_043_RETAINED_DORMANT"
    return {
        "environmentSha256": "b" * 64,
        "api2EventKeySha256": "c" * 64,
        "additionalConfigSha256": "e" * 64,
        "databaseEndpoint": "127.0.0.1:3306",
        "database": "fbsir",
        "databaseServerUuid": "uuid",
        "databaseServerVersion": "8.0.45",
        "legacyBaselineReceiptSha256": "a" * 64,
        "legacyBaselineMigrationCount": 1,
        "w1aDatabaseState": state,
        "public043AnyReceiptCount": 1 if retained else 0,
        "public043ReceiptCount": 1 if retained else 0,
        "attributionInternalReceiptCount": 1 if retained else 0,
        "attributionTableCount": 2 if retained else 0,
        "attributionTriggerCount": 2 if retained else 0,
        "attributionPermissionCount": 1 if retained else 0,
        "attributionEventCount": event_count if retained else 0,
        "attributionJourneyCount": journey_count if retained else 0,
        "w1aSchemaFingerprintSha256": (
            release.EXPECTED_W1A_SCHEMA_FINGERPRINT if retained else None
        ),
    }


def configured_environment_values():
    event_material = b"independent-secret-hmac-key"
    binding_material = b"independent-same-binding-key"
    values = {
        name: "false" for name in release.FALSE_FLAGS
    }
    values.update({
        release.EVENT_KEY_ID_NAME: "w1a-active-key",
        release.EVENT_KEY_NAME: "base64:" + base64.b64encode(
            event_material
        ).decode("ascii"),
        release.PREVIOUS_EVENT_KEY_ID_NAME: "",
        release.PREVIOUS_EVENT_KEY_NAME: "",
        release.SAME_BINDING_KEY_NAME: "base64:" + base64.b64encode(
            binding_material
        ).decode("ascii"),
        "WXFBSIR_MYSQL_URL": "jdbc:mysql://db/fbsir",
        "WXFBSIR_MYSQL_USERNAME": "sensitive-user",
        "WXFBSIR_MYSQL_PASSWORD": "sensitive-password",
        "FBSIR_MYSQL_URL": "jdbc:mysql://db/fbsir",
        "FBSIR_MYSQL_USERNAME": "sensitive-user",
        "FBSIR_MYSQL_PASSWORD": "sensitive-password",
    })
    return values, event_material


def plan_target_fixture():
    target = {field: None for field in release.PLAN_TARGET_FIELDS}
    target.update({
        "schema": "fbsir.u3wDefaultOffRemotePlanSnapshot.v1",
        "targetHost": release.TARGET_HOST,
        "serviceUnit": release.SERVICE_UNIT,
        "service": {"ActiveState": "active"},
        "unitSha256": "1" * 64,
        "fragmentFileManifest": {"sha256": "1" * 64},
        "dropInManifest": [],
        "unitFiles": [],
        "environmentCustody": {"mode": "0o600"},
        "environmentSha256": "2" * 64,
        "environmentFilePaths": [release.ENV_PATH_TEXT],
        "environmentFileManifest": [{
            "path": release.ENV_PATH_TEXT,
            "ignoreErrors": False,
            "sha256": "2" * 64,
        }],
        "additionalConfigCustody": {"mode": "0o644"},
        "additionalConfigSha256": "3" * 64,
        "externalConfigManifest": [{"sha256": "3" * 64}],
        "activeJarPath": "/opt/fbsir/admin/fbsir-admin.jar",
        "activeJarSha256": "4" * 64,
        "configuredJarPath": "/opt/fbsir/admin/fbsir-admin.jar",
        "configuredJarSha256": "4" * 64,
        "processJarPath": "/opt/fbsir/admin/fbsir-admin.jar",
        "processJarSha256": "4" * 64,
        "processArgvSha256": "5" * 64,
        "processEnvironmentNamesSha256": "6" * 64,
        "processFlagValues": {
            name: None for name in release.FALSE_FLAGS
        },
        "configuredFlagValues": {
            name: "false" for name in release.FALSE_FLAGS
        },
        "processForbiddenOverrideNames": [],
        "api2EventKeyManifest": {"sha256": "7" * 64},
        "processSecurityConfigurationNames": [],
        "processSecurityConfigurationHmacSha256": "8" * 64,
        "expectedSecurityConfigurationNames": [],
        "expectedSecurityConfigurationHmacSha256": "8" * 64,
        "processDatabaseBindingMatched": True,
        "configuredEnvironmentSha256": "2" * 64,
        "configuredEnvironmentNames": [],
        "configuredEnvironmentHmacSha256": "9" * 64,
        "processConfiguredEnvironmentHmacSha256": "9" * 64,
        "processConfiguredEnvironmentMatched": False,
        "processConfiguredEnvironmentMismatchNames": [],
        "processPendingRestartEnvironmentNames":
            sorted(release.MANAGED_W1A_ENVIRONMENT_NAMES),
        "processConfiguredEnvironmentLoadState":
            "LEGACY_W1A_PENDING_RESTART",
        "processConfiguredEnvironmentPreStageCompatible": True,
        "nginxConfigs": [{"path": "/etc/nginx/nginx.conf"}],
        "activeNginxManifest": [{
            "path": "/etc/nginx/nginx.conf",
        }],
        "nginxDumpSha256": "a" * 64,
        "releaseRootExists": False,
        "releaseRootEntryManifest": [],
        "currentLinkExists": False,
        "currentLinkResolved": None,
        "currentLifecycleState": "UNTOUCHED_LEGACY",
        "stageEntryTopology": {
            "state": "UNTOUCHED_LEGACY",
            "priorRollbackAnchor": None,
        },
        "productionChanged": False,
        "productionChangedByPlan": False,
    })
    return target


class ReleaseWorkerContractTest(unittest.TestCase):
    def test_migration_parser_preserves_routines_on_one_locked_session(self):
        migration = (
            ROOT.parent
            / "sql/update_20260723_independent_board_attribution_v1.sql"
        ).read_text(encoding="utf-8")
        statements = release.parse_mysql_script(migration)
        self.assertEqual(len(statements), 12)
        self.assertEqual(
            sum("CREATE TRIGGER" in statement for statement in statements),
            2,
        )
        self.assertEqual(
            sum("CREATE PROCEDURE" in statement for statement in statements),
            1,
        )
        self.assertTrue(
            all("DELIMITER" not in statement for statement in statements)
        )
        self.assertIn(
            "SIGNAL SQLSTATE '45000'",
            next(
                statement
                for statement in statements
                if "CREATE TRIGGER" in statement
            ),
        )
        with self.assertRaisesRegex(RuntimeError, "unterminated"):
            release.parse_mysql_script("SELECT 1")
        with self.assertRaisesRegex(RuntimeError, "inside a statement"):
            release.parse_mysql_script(
                "SELECT 1\nDELIMITER $$\n"
            )

    def test_apply_migration_never_opens_a_second_mysql_connection(self):
        source = inspect.getsource(release.apply_migration)
        self.assertIn("mysql.execute_script", source)
        self.assertIn("return MigrationLease", source)
        self.assertNotIn("/usr/bin/mysql", source)
        self.assertNotIn("subprocess.run", source)

    def test_migration_structure_is_exact_but_ledger_counts_are_monotonic(self):
        baseline = exact_migration_facts(4, 2)
        grown = exact_migration_facts(7, 3)
        self.assertTrue(release.migration_structure_matches(baseline))
        self.assertTrue(
            release.migration_counts_are_monotonic(grown, baseline)
        )
        self.assertFalse(
            release.migration_counts_are_monotonic(baseline, grown)
        )
        boolean_count = dict(baseline)
        boolean_count["eventCount"] = True
        self.assertFalse(
            release.migration_structure_matches(boolean_count)
        )

    def test_exact_schema_enumerates_all_w1a_triggers_and_fk_rules(self):
        fingerprint = inspect.getsource(
            release.migration_fingerprint_rows
        )
        exact = inspect.getsource(release.exact_migration_facts)
        state = inspect.getsource(release.w1a_database_state)
        for source in (fingerprint, exact, state):
            self.assertIn("event_object_table IN", source)
        self.assertIn(
            "information_schema.referential_constraints", fingerprint
        )
        self.assertIn("HEX(update_rule)", fingerprint)
        self.assertIn("HEX(delete_rule)", fingerprint)
        self.assertNotIn("trigger_name IN", fingerprint)
        self.assertNotIn("trigger_name IN", exact)
        self.assertNotIn("trigger_name IN", state)

    def test_verify_and_apply_reentry_allow_append_only_ledger_growth(self):
        verify = inspect.getsource(release.verify_release)
        apply = inspect.getsource(release.apply_release)
        self.assertIn("deployed_migration_lease", verify)
        self.assertIn("migration_counts_are_monotonic", verify)
        self.assertNotIn("validate_stage_runtime_identity", verify)
        existing_branch = apply[
            apply.index("if deployment_path.exists():"):
            apply.index("recovered_existing_side_effects = False")
        ]
        self.assertIn("deployed_migration_lease", existing_branch)
        self.assertIn("migration_counts_are_monotonic", existing_branch)
        self.assertNotIn("apply_migration(", existing_branch)
        self.assertNotIn("restore_application(", existing_branch)
        self.assertNotIn(
            "assert_release_owned_recovery_topology", existing_branch
        )
        self.assertIn(
            "existing deployment validation failed", existing_branch
        )
        self.assertNotIn('get("eventCount") == 0', verify)
        self.assertNotIn('get("journeyCount") == 0', verify)

    def test_apply_reentry_db_observation_failure_never_restores_service(self):
        args, _ = release_args("Apply")
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            stage_path = (
                release_dir / "deployment-readiness-receipt.json"
            )
            deployment_path = release_dir / "deployment-receipt.json"
            stage_path.write_text("{}\n", encoding="utf-8")
            deployment_path.write_text("{}\n", encoding="utf-8")
            existing = {
                "schema": "fbsir.u3wW1aDeploymentReadinessReceipt.v2",
                "state": "DEPLOYED_DEFAULT_OFF",
                "releaseId": args.release_id,
                "sourceCommit": args.source_commit,
                "stageReceiptSha256": "f" * 64,
                "applyApprovalReceiptSha256": "a" * 64,
                "productionDatabaseChanged": True,
                "productionDatabaseChangedThisRun": True,
                "productionDatabaseChangedSinceStage": True,
            }
            with (
                mock.patch.object(
                    release, "validate_preparation_anchors"
                ),
                mock.patch.object(
                    release, "release_directory", return_value=release_dir
                ),
                mock.patch.object(
                    release,
                    "validate_stage_receipt",
                    return_value=(stage_path, {}),
                ),
                mock.patch.object(
                    release,
                    "service_snapshot",
                    return_value={"activeState": "active"},
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=deployment_path,
                ),
                mock.patch.object(
                    release, "read_json", return_value=existing
                ),
                mock.patch.object(
                    release, "sha256_file", return_value="f" * 64
                ),
                mock.patch.object(
                    release,
                    "deployed_migration_lease",
                    side_effect=RuntimeError("named lock unavailable"),
                ),
                mock.patch.object(
                    release,
                    "write_apply_failure_receipt",
                    return_value=release_dir / (
                        "apply-failure-20260723T180000000000Z-"
                        "0123456789ab.json"
                    ),
                ) as failure_receipt,
                mock.patch.object(
                    release, "restore_application"
                ) as restore,
            ):
                with self.assertRaisesRegex(
                    RuntimeError, "without topology mutation"
                ):
                    release.apply_release(args)
            failure_receipt.assert_called_once()
            restore.assert_not_called()

    def test_apply_reentry_repairs_latest_only_when_needed(self):
        args, _ = release_args("Apply")
        facts = exact_migration_facts(3, 2)
        for latest_matched in (False, True):
            with self.subTest(latest_matched=latest_matched):
                with tempfile.TemporaryDirectory() as temporary:
                    release_dir = pathlib.Path(temporary)
                    stage_path = (
                        release_dir
                        / "deployment-readiness-receipt.json"
                    )
                    deployment_path = (
                        release_dir / "deployment-receipt.json"
                    )
                    stage_path.write_text("{}\n", encoding="utf-8")
                    deployment_path.write_text("{}\n", encoding="utf-8")
                    existing = {
                        "schema":
                            "fbsir.u3wW1aDeploymentReadinessReceipt.v2",
                        "state": "DEPLOYED_DEFAULT_OFF",
                        "releaseId": args.release_id,
                        "sourceCommit": args.source_commit,
                        "stageReceiptSha256": "f" * 64,
                        "applyApprovalReceiptSha256": "a" * 64,
                        "productionDatabaseChanged": False,
                        "productionDatabaseChangedThisRun": False,
                        "productionDatabaseChangedSinceStage": False,
                    }
                    lease = types.SimpleNamespace(
                        mysql=object(),
                        connection={},
                        facts=facts,
                        close=mock.Mock(),
                    )
                    with (
                        mock.patch.object(
                            release, "validate_preparation_anchors"
                        ),
                        mock.patch.object(
                            release,
                            "release_directory",
                            return_value=release_dir,
                        ),
                        mock.patch.object(
                            release,
                            "validate_stage_receipt",
                            return_value=(
                                stage_path,
                                {
                                    "preStageRuntimeIdentity":
                                        runtime_identity()
                                },
                            ),
                        ),
                        mock.patch.object(
                            release,
                            "service_snapshot",
                            return_value={"activeState": "active"},
                        ),
                        mock.patch.object(
                            release,
                            "validate_regular_file",
                            return_value=deployment_path,
                        ),
                        mock.patch.object(
                            release, "read_json", return_value=existing
                        ),
                        mock.patch.object(
                            release, "sha256_file", return_value="f" * 64
                        ),
                        mock.patch.object(
                            release,
                            "deployed_migration_lease",
                            return_value=lease,
                        ),
                        mock.patch.object(
                            release, "validate_release_evidence"
                        ),
                        mock.patch.object(
                            release, "assert_candidate_active"
                        ),
                        mock.patch.object(
                            release, "validate_apply_session_identity"
                        ),
                        mock.patch.object(
                            release,
                            "exact_migration_facts",
                            return_value=facts,
                        ),
                        mock.patch.object(
                            release,
                            "migration_counts_are_monotonic",
                            return_value=True,
                        ),
                        mock.patch.object(
                            release,
                            "advance_latest_receipt",
                            return_value=not latest_matched,
                        ) as repair,
                        mock.patch.object(
                            release,
                            "apply_worker_result",
                            side_effect=lambda *a, **kw: kw,
                        ),
                    ):
                        result = release.apply_release(args)
                    self.assertEqual(
                        result["filesystem_changed"],
                        not latest_matched,
                    )
                    self.assertFalse(
                        result["database_changed_this_run"]
                    )
                    self.assertFalse(
                        result["database_changed_since_stage"]
                    )
                    self.assertFalse(result["service_changed"])
                    self.assertFalse(result["deployment_committed"])
                    self.assertEqual(
                        result["latest_repaired"], not latest_matched
                    )
                    repair.assert_called_once_with(
                        deployment_path, [stage_path]
                    )

    def test_process_database_binding_is_secret_safe_and_value_sensitive(self):
        expected, event_material = configured_environment_values()
        event_key_path = mock.Mock()
        event_key_path.parent = pathlib.Path("/etc/u3w/secrets")
        event_key_path.read_bytes.return_value = event_material
        with (
            mock.patch.object(release, "validate_regular_file"),
            mock.patch.object(
                release, "sha256_file", return_value="a" * 64
            ),
            mock.patch.object(
                release,
                "root_regular_manifest",
                return_value={
                    "path": "/etc/u3w/secrets/"
                    "independent-board-event-hmac.key",
                    "sha256": "b" * 64,
                    "mode": 0o600,
                    "uid": 0,
                    "gid": 0,
                    "nlink": 1,
                },
            ),
            mock.patch.object(
                release, "API2_EVENT_KEY_PATH", event_key_path
            ),
        ):
            matched = release.security_configuration_evidence(
                dict(expected), expected
            )
            drifted_values = dict(expected)
            drifted_values["WXFBSIR_MYSQL_PASSWORD"] = "wrong-password"
            drifted = release.security_configuration_evidence(
                drifted_values, expected
            )
        self.assertTrue(matched["processDatabaseBindingMatched"])
        self.assertTrue(
            matched["processConfiguredEnvironmentMatched"]
        )
        self.assertEqual(
            matched["processConfiguredEnvironmentLoadState"],
            "EXACT_CONFIGURED",
        )
        self.assertFalse(drifted["processDatabaseBindingMatched"])
        self.assertFalse(
            drifted["processConfiguredEnvironmentMatched"]
        )
        self.assertEqual(
            matched["processSecurityConfigurationNames"],
            drifted["processSecurityConfigurationNames"],
        )
        self.assertNotEqual(
            matched["processSecurityConfigurationHmacSha256"],
            drifted["processSecurityConfigurationHmacSha256"],
        )
        serialized = json.dumps([matched, drifted])
        for secret in (
            "sensitive-user",
            "sensitive-password",
            "wrong-password",
            "jdbc:mysql://db/fbsir",
            "independent-secret-hmac-key",
        ):
            self.assertNotIn(secret, serialized)

    def test_process_environment_accepts_only_exact_or_full_pending_states(self):
        expected, event_material = configured_environment_values()
        legacy_process = {
            name: value
            for name, value in expected.items()
            if name not in release.MANAGED_W1A_ENVIRONMENT_NAMES
        }
        partial_process = dict(legacy_process)
        partial_process[release.FALSE_FLAGS[0]] = "false"
        event_key_path = mock.Mock()
        event_key_path.parent = pathlib.Path("/etc/u3w/secrets")
        event_key_path.read_bytes.return_value = event_material
        with (
            mock.patch.object(
                release,
                "root_regular_manifest",
                return_value={"sha256": "b" * 64},
            ),
            mock.patch.object(
                release, "sha256_file", return_value="a" * 64
            ),
            mock.patch.object(
                release, "API2_EVENT_KEY_PATH", event_key_path
            ),
        ):
            legacy = release.security_configuration_evidence(
                legacy_process, expected
            )
            partial = release.security_configuration_evidence(
                partial_process, expected
            )
            exact = release.security_configuration_evidence(
                dict(expected), expected
            )
        self.assertEqual(
            legacy["processConfiguredEnvironmentLoadState"],
            "LEGACY_W1A_PENDING_RESTART",
        )
        self.assertEqual(
            legacy["processPendingRestartEnvironmentNames"],
            sorted(release.MANAGED_W1A_ENVIRONMENT_NAMES),
        )
        self.assertTrue(
            legacy["processConfiguredEnvironmentPreStageCompatible"]
        )
        self.assertEqual(
            partial["processConfiguredEnvironmentLoadState"],
            "INVALID_PARTIAL_OR_DRIFTED",
        )
        self.assertFalse(
            partial["processConfiguredEnvironmentPreStageCompatible"]
        )
        self.assertEqual(
            exact["processConfiguredEnvironmentLoadState"],
            "EXACT_CONFIGURED",
        )
        self.assertEqual(
            exact["processPendingRestartEnvironmentNames"], []
        )

    def test_candidate_runtime_rejects_base_unit_content_drift(self):
        args, _ = release_args("Apply")
        release_dir = pathlib.Path("/opt/fbsir/admin/releases/test")
        environment_manifest = [{
            "path": str(release.ENV_PATH),
            "ignoreErrors": False,
            "sha256": "f" * 64,
            "mode": 0o600,
            "uid": 0,
            "gid": 0,
            "nlink": 1,
        }]
        previous = {
            "environmentFiles": str(release.ENV_PATH),
            "environmentFilePaths": [str(release.ENV_PATH)],
            "environmentFileManifest": environment_manifest,
            "fragmentFileManifest": {
                "path": "/etc/systemd/system/fbsir-admin.service",
                "sha256": "a" * 64,
            },
            "processEnvironmentNamesSha256": "b" * 64,
            "processSecurityConfigurationNames":
                list(release.PROCESS_SECURITY_ENVIRONMENT_NAMES),
            "processSecurityConfigurationHmacSha256": "c" * 64,
            "expectedSecurityConfigurationNames":
                list(release.PROCESS_SECURITY_ENVIRONMENT_NAMES),
            "expectedSecurityConfigurationHmacSha256": "c" * 64,
            "configuredEnvironmentSha256": "f" * 64,
            "configuredEnvironmentNames":
                list(release.PROCESS_SECURITY_ENVIRONMENT_NAMES),
            "configuredEnvironmentHmacSha256": "1" * 64,
            "processConfiguredEnvironmentHmacSha256": "1" * 64,
            "processConfiguredEnvironmentMatched": True,
            "externalConfigManifest": [{"sha256": "d" * 64}],
            "additionalConfigSha256": "e" * 64,
        }
        expected_argv = release.candidate_process_arguments()
        current = dict(previous)
        current.update({
            "processArgvSha256": release.sha256_bytes(
                ("\0".join(expected_argv) + "\0").encode("utf-8")
            ),
            "processJarPath": str(
                release.CURRENT_LINK / "backend/fbsir-admin.jar"
            ),
            "processDatabaseBindingMatched": True,
            "dropInManifest": [],
            "processFlagValues": {
                name: "false" for name in release.FALSE_FLAGS
            },
            "processForbiddenOverrideNames": [],
            "fragmentFileManifest": {
                "path": "/etc/systemd/system/fbsir-admin.service",
                "sha256": "f" * 64,
            },
        })
        with (
            mock.patch.object(
                release,
                "predecessor_facts",
                return_value={"serviceSnapshotBeforeStage": previous},
            ),
            mock.patch.object(
                release, "release_dropin_manifest", return_value=[]
            ),
        ):
            with self.assertRaisesRegex(
                RuntimeError, "effective default-off contract drifted"
            ):
                release.assert_candidate_runtime_contract(
                    args, release_dir, current
                )

    def test_environment_files_accepts_one_fixed_file_in_systemd_forms(self):
        expected_required = [{
            "path": release.ENV_PATH_TEXT,
            "ignoreErrors": False,
        }]
        self.assertEqual(
            release.normalized_environment_files(
                "  /etc/u3w/fbsir-admin.env   (ignore_errors=no)  "
            ),
            expected_required,
        )
        with self.assertRaisesRegex(RuntimeError, "mandatory fixed"):
            release.normalized_environment_files(
                '  -"/etc/u3w/fbsir-admin.env" '
                "  (ignore_errors=yes)   "
            )

    def test_environment_files_rejects_a_second_file(self):
        with self.assertRaisesRegex(
            RuntimeError, "mandatory fixed EnvironmentFile"
        ):
            release.normalized_environment_files(
                "/etc/u3w/fbsir-admin.env (ignore_errors=no) "
                "/etc/u3w/hidden-override.env (ignore_errors=no)"
            )

    def test_finalize_stage_binds_the_complete_plan_target(self):
        args, _ = release_args("FinalizeStage")
        planned = plan_target_fixture()
        live = json.loads(json.dumps(planned))
        live["releaseRootExists"] = True
        with mock.patch.object(
            release,
            "stage_owned_release_root_delta_verified",
            return_value=True,
        ):
            binding = release.assert_stage_plan_target_matches(
                args, planned, live
            )
        self.assertEqual(
            binding["releasePlanTargetComparableSha256"],
            binding["finalizeStageLiveTargetComparableSha256"],
        )
        self.assertTrue(binding["stageOwnedReleaseRootDeltaVerified"])
        self.assertRegex(
            binding["releasePlanTargetSha256"], r"^[0-9a-f]{64}$"
        )

        for field, drift in (
            ("nginxDumpSha256", "b" * 64),
            ("environmentSha256", "c" * 64),
            ("processArgvSha256", "d" * 64),
        ):
            drifted = json.loads(json.dumps(live))
            drifted[field] = drift
            with (
                mock.patch.object(
                    release,
                    "stage_owned_release_root_delta_verified",
                    return_value=True,
                ),
                self.assertRaisesRegex(RuntimeError, "drifted"),
            ):
                release.assert_stage_plan_target_matches(
                    args, planned, drifted
                )

        unexpected = dict(planned)
        unexpected["collectorSha256"] = "e" * 64
        with self.assertRaisesRegex(RuntimeError, "contract"):
            release.assert_stage_plan_target_matches(
                args, unexpected, live
            )

    def test_stage_artifact_failure_precedes_every_apply_mutation(self):
        args, _ = release_args("Apply")
        with (
            mock.patch.object(
                release,
                "release_directory",
                return_value=pathlib.Path("/opt/fbsir/admin/releases/test"),
            ),
            mock.patch.object(
                release,
                "validate_stage_receipt",
                side_effect=RuntimeError(
                    "staged release artifact manifest drifted"
                ),
            ),
            mock.patch.object(release, "apply_migration") as migration,
            mock.patch.object(
                release, "install_candidate_application"
            ) as install,
        ):
            with self.assertRaisesRegex(RuntimeError, "artifact"):
                release.apply_release(args)
        migration.assert_not_called()
        install.assert_not_called()

    def test_latest_receipt_cannot_regress_across_release_lifecycles(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            target_release = root / (
                "w1a-release-0123456789ab-20260723T180000Z"
            )
            newer_release = root / (
                "w1a-release-fedcba987654-20260723T190000Z"
            )
            target_release.mkdir()
            newer_release.mkdir()
            target = target_release / "deployment-readiness-receipt.json"
            newer = newer_release / "deployment-receipt.json"
            target.write_text("{}\n", encoding="utf-8")
            newer.write_text("{}\n", encoding="utf-8")
            with (
                mock.patch.object(
                    release, "validate_regular_file"
                ),
                mock.patch.object(
                    release,
                    "validated_latest_receipt_target",
                    return_value=newer.resolve(),
                ),
                mock.patch.object(
                    release, "atomic_symlink"
                ) as replace_latest,
            ):
                with self.assertRaisesRegex(
                    RuntimeError, "another lifecycle"
                ):
                    release.advance_latest_receipt(target)
                replace_latest.assert_not_called()
                changed = release.advance_latest_receipt(
                    target, [newer.resolve()]
                )
            self.assertTrue(changed)
            replace_latest.assert_called_once_with(
                target.resolve(), release.LATEST_RECEIPT
            )

    def test_finalize_stage_recollects_all_active_nginx_files(self):
        nginx_source = inspect.getsource(release.active_nginx_manifest)
        dump_source = inspect.getsource(release.nginx_dump_bytes)
        manifest_source = inspect.getsource(
            release.stable_root_regular_manifest
        )
        target_source = inspect.getsource(release.stage_live_plan_target)
        finalize_source = inspect.getsource(release.finalize_stage)
        self.assertIn('["nginx", "-T"]', dump_source)
        self.assertEqual(nginx_source.count("nginx_dump_bytes()"), 2)
        self.assertIn("stable_root_regular_manifest(path)", nginx_source)
        self.assertIn("os.fstat(descriptor)", manifest_source)
        self.assertIn('"sizeBytes"', manifest_source)
        self.assertNotIn('"fbsir"', nginx_source)
        self.assertNotIn('"u3w.com"', nginx_source)
        self.assertIn('"activeNginxManifest"', target_source)
        self.assertIn('"nginxDumpSha256"', target_source)
        self.assertIn("assert_stage_plan_target_matches", finalize_source)
        self.assertIn("**plan_target_binding", finalize_source)

    def test_apply_holds_migration_lease_through_receipt_commit(self):
        source = inspect.getsource(release.apply_release)
        first_commit = source.index(
            "atomic_json(deployment_path, receipt)"
        )
        final_read = source.index(
            "043 final current-read drifted before receipt commit"
        )
        lease_close = source.index(
            "migration_lease.close()", first_commit
        )
        committed = source.index(
            "deployment_committed = True", first_commit
        )
        self.assertLess(final_read, first_commit)
        self.assertLess(first_commit, committed)
        self.assertLess(committed, lease_close)
        self.assertEqual(
            source[first_commit:committed].count("\n"),
            1,
        )
        self.assertIn(
            "exact_migration_facts(migration_lease.mysql)",
            source,
        )

    def test_nested_rollback_evidence_is_not_self_reported(self):
        source = inspect.getsource(release.validate_release_evidence)
        for name in (
            "application-rollback-assembly.json",
            "database-rollback-safety.json",
            "application-rollback-execution.json",
        ):
            self.assertIn(name, source)
        self.assertIn("anchored_evidence_json", source)
        self.assertIn("assert_candidate_snapshot_evidence", source)
        self.assertIn(
            "assert_restored_predecessor_runtime_contract", source
        )
        self.assertIn("len(invocation_ids) != 3", source)

    def test_recovery_topology_is_enumerated_and_runtime_bound(self):
        source = inspect.getsource(
            release.assert_release_owned_recovery_topology
        )
        self.assertIn("allowed_topologies", source)
        self.assertIn("allowed_loaded_dropins", source)
        self.assertIn("allowed_argv_sha256", source)
        self.assertIn("allowed_jar_paths", source)
        self.assertIn("exact_loaded_environment_matches", source)
        exact_source = inspect.getsource(
            release.exact_loaded_environment_matches
        )
        self.assertIn(
            "processConfiguredEnvironmentLoadState", exact_source
        )
        self.assertIn("EXACT_CONFIGURED", exact_source)
        self.assertNotIn(
            "in {args.backend_sha, previous[", source
        )

    def test_all_relaxed_binding_default_off_aliases_are_managed(self):
        self.assertEqual(len(release.FALSE_FLAGS), 12)
        self.assertIn(
            "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
            release.FALSE_FLAGS,
        )
        self.assertIn(
            "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_"
            "AUTHORITATIVE_CREDIT_ENABLED",
            release.FALSE_FLAGS,
        )

    def test_approval_is_exact_and_binds_every_receipt(self):
        args, _ = release_args()
        parsed = release.validate_approval(args)
        self.assertEqual(parsed["expectedBackupReceiptSha256"], "f" * 64)
        self.assertEqual(parsed["workerSha256"], "c" * 64)

        args, payload = release_args()
        payload["unknown"] = True
        raw = json.dumps(payload, separators=(",", ":")).encode()
        args.approval_sha = release.sha256_bytes(raw)
        args.approval_json_base64 = base64.b64encode(raw).decode()
        with self.assertRaisesRegex(RuntimeError, "unknown fields"):
            release.validate_approval(args)

    def test_portal_config_is_bound_to_candidate_and_u3w_backend(self):
        candidate = pathlib.Path(
            "/opt/fbsir/admin/releases/w1a-release-test"
        )
        rendered = release.portal_nginx_config(candidate)
        self.assertEqual(rendered.count("server_name admin.u3w.com"), 2)
        self.assertEqual(rendered.count("server_name me.u3w.com"), 2)
        self.assertEqual(
            rendered.count("proxy_pass http://127.0.0.1:8080/"), 2
        )
        self.assertIn(str(candidate / "frontend"), rendered)
        self.assertNotIn("fbss/health", rendered)

    def test_dropin_points_only_at_the_current_candidate(self):
        rendered = release.systemd_dropin()
        self.assertIn(
            "/opt/fbsir/admin/current/backend/fbsir-admin.jar",
            rendered,
        )
        self.assertNotIn("/opt/fbss/", rendered)
        self.assertLess(rendered.index("-Dspring."), rendered.index("-jar"))
        rollback = release.rollback_systemd_dropin(
            pathlib.Path("/opt/fbsir/admin/releases/test")
        )
        self.assertIn(
            "/opt/fbsir/admin/releases/test/rollback/previous-admin.jar",
            rollback,
        )
        self.assertLess(rollback.index("-Dspring."), rollback.index("-jar"))

    def test_release_dropin_manifest_replaces_prior_release_dropin(self):
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            evidence = release_dir / "evidence"
            evidence.mkdir()
            candidate = evidence / "systemd-dropin.conf"
            candidate.write_text("[Service]\n", encoding="utf-8")
            other = {
                "path": "/etc/systemd/system/fbsir-admin.service.d/10-base.conf",
                "sha256": "1" * 64,
                "mode": 0o644,
            }
            prior = {
                "path": str(release.DROPIN_PATH),
                "sha256": "2" * 64,
                "mode": 0o644,
            }
            predecessor = {
                "serviceSnapshotBeforeStage": {
                    "dropInManifest": [other, prior]
                }
            }
            with (
                mock.patch.object(
                    release,
                    "predecessor_facts",
                    return_value=predecessor,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=candidate,
                ),
            ):
                manifest = release.release_dropin_manifest(
                    release_dir, "systemd-dropin.conf"
                )
            self.assertEqual(
                sum(
                    item["path"] == str(release.DROPIN_PATH)
                    for item in manifest
                ),
                1,
            )
            self.assertIn(other, manifest)
            active = next(
                item
                for item in manifest
                if item["path"] == str(release.DROPIN_PATH)
            )
            self.assertEqual(
                active["sha256"], release.sha256_file(candidate)
            )
            self.assertNotEqual(active["sha256"], prior["sha256"])

    def test_unavailable_rollback_can_only_gain_immutable_db_attestation(self):
        args, _ = release_args("Rollback")
        facts = exact_migration_facts(8, 4)
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            rollback_path = release_dir / "rollback-receipt.json"
            rollback_path.write_text("{}\n", encoding="utf-8")
            rollback = {
                "deploymentReceiptPath":
                    str(release_dir / "deployment-receipt.json"),
                "deploymentReceiptSha256": "9" * 64,
            }
            captured = {}

            def write_receipt(path, value):
                captured["path"] = pathlib.Path(path)
                captured["value"] = value

            with mock.patch.object(
                release, "atomic_json", side_effect=write_receipt
            ):
                path, receipt, changed = (
                    release.ensure_rollback_verification_receipt(
                        args,
                        release_dir,
                        rollback_path,
                        rollback,
                        facts,
                    )
                )
            self.assertTrue(changed)
            self.assertEqual(
                path.name, "rollback-verification-receipt.json"
            )
            self.assertEqual(captured["path"], path)
            self.assertEqual(
                receipt["rollbackReceiptSha256"],
                release.sha256_file(rollback_path),
            )
            self.assertEqual(receipt["retainedMigrationFacts"], facts)
            self.assertEqual(
                receipt["state"],
                "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT",
            )
            self.assertFalse(receipt["databaseDownClaimed"])
            self.assertFalse(receipt["productionDatabaseChanged"])

    def test_candidate_health_uses_environment_values_not_connection(self):
        args = types.SimpleNamespace(
            release_id="w1a-release-test",
            source_commit="a" * 40,
        )
        candidate = pathlib.Path("/opt/fbsir/admin/releases/test")
        environment = {name: "false" for name in release.FALSE_FLAGS}

        def http_result(url, timeout=10):
            if url.endswith("/w1a-release.json"):
                return (
                    200,
                    {
                        "schema": "fbsir.u3wReleaseMarker.v1",
                        "releaseId": args.release_id,
                        "sourceCommit": args.source_commit,
                        "officialExpertsPackageChanged": False,
                    },
                )
            return 200, {"code": 200}

        with (
            mock.patch.object(
                release,
                "assert_candidate_owned_topology",
                return_value={"activeState": "active"},
            ),
            mock.patch.object(release, "assert_candidate_runtime_contract"),
            mock.patch.object(release, "wait_for_u3w_health"),
            mock.patch.object(
                release,
                "http_json",
                side_effect=http_result,
            ),
            mock.patch.object(
                release,
                "parse_environment",
                return_value=(environment, {"host": "127.0.0.1"}),
            ),
        ):
            self.assertEqual(
                release.assert_candidate_active(args, candidate)[
                    "activeState"
                ],
                "active",
            )
            environment[release.FALSE_FLAGS[0]] = "true"
            with self.assertRaisesRegex(RuntimeError, "flags"):
                release.assert_candidate_active(args, candidate)

    def test_atomic_write_preserves_existing_system_parent_mode(self):
        source = inspect.getsource(release.atomic_bytes)
        self.assertIn("ensure_parent_directory(path.parent)", source)
        self.assertNotIn("safe_directory(path.parent", source)
        parent_source = inspect.getsource(release.ensure_parent_directory)
        self.assertIn("if not path.exists()", parent_source)
        self.assertNotIn("os.chmod", parent_source)
        safe_source = inspect.getsource(release.safe_directory)
        self.assertIn("if created:", safe_source)
        self.assertIn("not created", safe_source)

    def test_service_snapshot_binds_loaded_config_and_actual_process(self):
        source = inspect.getsource(release.service_snapshot)
        self.assertIn('pathlib.Path("/proc")', source)
        self.assertIn('"configuredJarSha256"', source)
        self.assertIn('"processJarSha256"', source)
        self.assertIn('"processArgvSha256"', source)
        self.assertIn('"processFlagValues"', source)
        self.assertIn('"dropInManifest"', source)
        self.assertIn('"externalConfigManifest"', source)
        self.assertIn(
            "process_jar.resolve() != configured_jar.resolve()",
            source,
        )

    def test_frontend_archive_rejects_parent_escape(self):
        with tempfile.TemporaryDirectory() as temporary:
            incoming = pathlib.Path(temporary)
            (incoming / "evidence").mkdir()
            (incoming / "frontend").mkdir()
            archive_path = incoming / "evidence/frontend.tar"
            with tarfile.open(archive_path, "w") as archive:
                info = tarfile.TarInfo("../escape.txt")
                payload = b"escape"
                info.size = len(payload)
                archive.addfile(info, io.BytesIO(payload))

            def safe_dir(path, mode=0o700):
                pathlib.Path(path).mkdir(parents=True, exist_ok=True)
                return pathlib.Path(path)

            with (
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=archive_path,
                ),
                mock.patch.object(release, "safe_directory", safe_dir),
            ):
                with self.assertRaisesRegex(RuntimeError, "unsafe"):
                    release.extract_frontend_archive(incoming)

    def test_database_rollback_language_is_forward_only(self):
        worker = (ROOT / "u3w-default-off-release-remote.py").read_text(
            encoding="utf-8"
        )
        self.assertIn("databaseRollbackSafetyProven", worker)
        self.assertIn("RETAIN_ADDITIVE_043_DORMANT_NO_DOWN", worker)
        self.assertNotIn('"databaseRollbackProven": True', worker)

    def test_apply_runtime_identity_fails_closed_on_environment_drift(self):
        args = types.SimpleNamespace(baseline_receipt_sha="a" * 64)
        identity = {
            "environmentSha256": "b" * 64,
            "api2EventKeySha256": "c" * 64,
            "additionalConfigSha256": "e" * 64,
            "databaseEndpoint": "127.0.0.1:3306",
            "database": "fbsir",
            "databaseServerUuid": "uuid",
            "databaseServerVersion": "8.0.45",
            "legacyBaselineReceiptSha256": "a" * 64,
            "legacyBaselineMigrationCount": 1,
            "w1aDatabaseState": "ABSENT",
            "public043AnyReceiptCount": 0,
            "public043ReceiptCount": 0,
            "attributionInternalReceiptCount": 0,
            "attributionTableCount": 0,
            "attributionTriggerCount": 0,
            "attributionPermissionCount": 0,
            "attributionEventCount": 0,
            "attributionJourneyCount": 0,
            "w1aSchemaFingerprintSha256": None,
        }
        stage = {"preStageRuntimeIdentity": dict(identity)}
        with mock.patch.object(
            release,
            "database_pre_stage_facts",
            return_value=dict(identity),
        ):
            self.assertEqual(
                release.validate_stage_runtime_identity(args, stage),
                identity,
            )
        drifted = dict(identity)
        drifted["environmentSha256"] = "d" * 64
        with mock.patch.object(
            release,
            "database_pre_stage_facts",
            return_value=drifted,
        ):
            with self.assertRaisesRegex(RuntimeError, "identity drifted"):
                release.validate_stage_runtime_identity(args, stage)

    def test_stage_accepts_exact_absent_or_retained_prestate_only(self):
        args = types.SimpleNamespace(baseline_receipt_sha="a" * 64)
        absent = runtime_identity()
        with mock.patch.object(
            release, "database_pre_stage_facts", return_value=dict(absent)
        ):
            self.assertEqual(
                release.validate_stage_runtime_identity(
                    args, {"preStageRuntimeIdentity": absent}
                ),
                absent,
            )
        completed_retry = runtime_identity(
            "EXACT_043_RETAINED_DORMANT", 0, 0
        )
        with mock.patch.object(
            release,
            "database_pre_stage_facts",
            return_value=completed_retry,
        ):
            self.assertEqual(
                release.validate_stage_runtime_identity(
                    args, {"preStageRuntimeIdentity": absent}
                ),
                completed_retry,
            )
        retained = runtime_identity(
            "EXACT_043_RETAINED_DORMANT", 5, 3
        )
        with mock.patch.object(
            release, "database_pre_stage_facts", return_value=dict(retained)
        ):
            self.assertEqual(
                release.validate_stage_runtime_identity(
                    args, {"preStageRuntimeIdentity": retained}
                ),
                retained,
            )
        grown = runtime_identity(
            "EXACT_043_RETAINED_DORMANT", 6, 3
        )
        with mock.patch.object(
            release, "database_pre_stage_facts", return_value=grown
        ):
            with self.assertRaisesRegex(RuntimeError, "counts drifted"):
                release.validate_stage_runtime_identity(
                    args, {"preStageRuntimeIdentity": retained}
                )

    def test_emergency_rollback_does_not_require_stage_runtime_identity(self):
        source = inspect.getsource(release._rollback_release)
        self.assertNotIn("validate_stage_runtime_identity", source)
        self.assertIn("databaseSafetyCurrentRead", source)
        self.assertIn("rollback_transition", source)
        self.assertIn(
            "assert_candidate_owned_topology",
            inspect.getsource(release.rollback_transition),
        )

    def test_apply_reentry_classifies_inactive_and_proves_live_rollback(self):
        source = inspect.getsource(release.apply_release)
        self.assertIn(
            "service_snapshot(require_active=False)",
            source,
        )
        self.assertIn("install_candidate_application", source)
        self.assertIn("restore_application", source)
        self.assertIn(
            "application_rollback_execution_receipt",
            source,
        )
        self.assertNotIn(
            'if before["jarSha256"] == args.backend_sha',
            source,
        )
        self.assertLess(
            source.index("if deployment_path.exists():"),
            source.index("validate_stage_runtime_identity(args, stage)"),
        )

    def test_deployment_receipt_is_the_apply_commit_point(self):
        source = inspect.getsource(release.apply_release)
        self.assertNotIn(
            "migration_lease.database_changed,", source
        )
        self.assertIn(
            "migration_lease.database_changed_this_run", source
        )
        self.assertIn(
            "migration_lease.database_changed_since_stage", source
        )
        self.assertIn("deployment_committed = False", source)
        first_time_apply = source.index("deployment_committed = False")
        self.assertLess(
            source.index(
                "atomic_json(deployment_path, receipt)", first_time_apply
            ),
            source.index("deployment_committed = True", first_time_apply),
        )
        self.assertLess(
            source.index("deployment_committed = True", first_time_apply),
            source.index(
                "advance_latest_receipt(\n"
                "            deployment_path, [stage_path]",
                first_time_apply,
            ),
        )
        self.assertIn(
            "remove_exact_uncommitted_deployment_receipt",
            source,
        )

    def test_database_change_receipt_distinguishes_this_run_from_stage(self):
        args, _ = release_args("Apply")
        stage = {"schema": "stage"}
        current_link = mock.Mock()
        current_link.resolve.return_value = pathlib.Path(
            "/opt/fbsir/admin/releases/test"
        )
        with (
            mock.patch.object(
                release, "sha256_file", return_value="a" * 64
            ),
            mock.patch.object(release, "CURRENT_LINK", current_link),
            mock.patch.object(
                release,
                "active_nginx_manifest",
                return_value=([], "b" * 64),
            ),
        ):
            receipt = release.deployment_receipt(
                args,
                pathlib.Path("/stage.json"),
                stage,
                exact_migration_facts(),
                {},
                {},
                pathlib.Path("/rollback.json"),
                False,
                True,
            )
        self.assertFalse(
            receipt["productionDatabaseChangedThisRun"]
        )
        self.assertTrue(
            receipt["productionDatabaseChangedSinceStage"]
        )
        self.assertTrue(receipt["productionDatabaseChanged"])
        lease = release.MigrationLease(
            mock.Mock(),
            {},
            exact_migration_facts(),
            database_changed_this_run=False,
            database_changed_since_stage=True,
        )
        self.assertFalse(lease.database_changed_this_run)
        self.assertTrue(lease.database_changed_since_stage)

    def test_post_commit_failure_receipt_never_claims_pre_switch(self):
        args, _ = release_args("Apply")
        captured = {}
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            deployment = release_dir / "deployment-receipt.json"
            deployment.write_text("{}\n", encoding="utf-8")
            with (
                mock.patch.object(
                    release, "release_directory", return_value=release_dir
                ),
                mock.patch.object(release, "validate_regular_file"),
                mock.patch.object(
                    release, "sha256_file", return_value="a" * 64
                ),
                mock.patch.object(
                    release,
                    "atomic_json",
                    side_effect=lambda path, value: captured.update(
                        {"path": path, "value": value}
                    ),
                ),
            ):
                release.write_apply_failure_receipt(
                    args,
                    RuntimeError("read failed"),
                    None,
                    "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
                    application_started=False,
                    topology_restored=False,
                    deployment_commit_outcome="COMMITTED",
                    service_changed_this_run=False,
                    database_changed_this_run=False,
                )
        receipt = captured["value"]
        self.assertEqual(
            receipt["state"],
            "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
        )
        self.assertTrue(receipt["applicationAlreadyCommitted"])
        self.assertEqual(
            receipt["deploymentCommitOutcome"], "COMMITTED"
        )
        self.assertEqual(
            receipt["deploymentReceiptSha256"], "a" * 64
        )
        self.assertNotEqual(
            receipt["state"], "FAIL_CLOSED_BEFORE_APPLICATION_SWITCH"
        )

    def test_failure_envelope_binds_exact_release_owned_receipt_without_message(self):
        args, _ = release_args("Apply")
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            path = release_dir / (
                "apply-failure-20260723T180000000000Z-"
                "0123456789ab.json"
            )
            receipt = {
                "schema": "fbsir.u3wDefaultOffReleaseFailureReceipt.v1",
                "state": "FAIL_CLOSED_BEFORE_APPLICATION_SWITCH",
                "releaseId": args.release_id,
                "sourceCommit": args.source_commit,
                "applyApprovalReceiptSha256": args.approval_sha,
                "officialExpertsPackageChanged": False,
            }
            path.write_text(
                release.canonical_json(receipt) + "\n",
                encoding="utf-8",
            )
            error = release.ReleaseMutationFailure(
                "sensitive failure detail", path
            )
            with (
                mock.patch.object(
                    release,
                    "release_directory",
                    return_value=release_dir,
                ),
                mock.patch.object(release, "validate_regular_file"),
            ):
                envelope = release.worker_error_envelope(args, error)
        self.assertEqual(
            envelope["schema"],
            "fbsir.u3wDefaultOffReleaseWorkerError.v2",
        )
        self.assertTrue(envelope["failureReceiptEvidenceValid"])
        self.assertEqual(envelope["failureReceiptPath"], str(path))
        self.assertRegex(
            envelope["failureReceiptSha256"], r"^[0-9a-f]{64}$"
        )
        self.assertNotIn("message", envelope)
        self.assertNotIn(
            "sensitive failure detail",
            json.dumps(envelope),
        )

    def test_rollback_reentry_does_not_rewrite_exact_latest_link(self):
        args, _ = release_args("Rollback")
        facts = exact_migration_facts()
        receipt = {
            "schema":
                "fbsir.u3wDefaultOffReleaseRollbackReceipt.v1",
            "state":
                "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT",
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "deploymentReceiptSha256": "a" * 64,
            "retainedMigrationFacts": facts,
        }
        with tempfile.TemporaryDirectory() as temporary:
            release_dir = pathlib.Path(temporary)
            rollback_path = release_dir / "rollback-receipt.json"
            deployment_path = release_dir / "deployment-receipt.json"
            rollback_path.write_text("{}\n", encoding="utf-8")
            deployment_path.write_text("{}\n", encoding="utf-8")
            with (
                mock.patch.object(
                    release, "release_directory", return_value=release_dir
                ),
                mock.patch.object(
                    release,
                    "validate_deployment_receipt",
                    return_value=(deployment_path, {}),
                ),
                mock.patch.object(release, "validate_regular_file"),
                mock.patch.object(
                    release, "read_json", return_value=receipt
                ),
                mock.patch.object(
                    release, "sha256_file", return_value="a" * 64
                ),
                mock.patch.object(
                    release, "validate_rollback_receipt_base"
                ),
                mock.patch.object(
                    release, "assert_predecessor_active"
                ),
                mock.patch.object(
                    release, "read_exact_migration_facts",
                    return_value=facts,
                ),
                mock.patch.object(
                    release, "validated_rollback_receipt_chain"
                ),
                mock.patch.object(
                    release,
                    "advance_latest_receipt",
                    return_value=False,
                ) as rewrite,
                mock.patch.object(
                    release, "validate_release_evidence", return_value=[]
                ),
            ):
                result = release.rollback_release(args)
        rewrite.assert_called_once()
        self.assertFalse(result["productionFilesystemChanged"])

    def test_uncommitted_exact_receipt_cleanup_never_unlinks_drift(self):
        receipt = {"schema": "test", "state": "candidate"}
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "deployment-receipt.json"
            path.write_bytes(
                (release.canonical_json(receipt) + "\n").encode("utf-8")
            )
            with (
                mock.patch.object(
                    release, "validate_regular_file", return_value=path
                ),
                mock.patch.object(release, "fsync_directory"),
            ):
                self.assertTrue(
                    release.remove_exact_uncommitted_deployment_receipt(
                        path, receipt
                    )
                )
                self.assertFalse(path.exists())
                path.write_bytes(b'{"schema":"drift"}\n')
                with self.assertRaisesRegex(RuntimeError, "ambiguous"):
                    release.remove_exact_uncommitted_deployment_receipt(
                        path, receipt
                    )
                self.assertTrue(path.exists())

    def test_stage_only_claims_rollback_assembly(self):
        source = inspect.getsource(release.finalize_stage)
        self.assertIn(
            "fbsir.u3wApplicationRollbackAssemblyReceipt.v1",
            source,
        )
        self.assertIn('"applicationRollbackProven": False', source)
        self.assertNotIn(
            "fbsir.u3wApplicationRollbackRehearsalReceipt.v1",
            source,
        )
        self.assertIn("environment_flags_explicit_false()", source)
        self.assertIn("processFlagValues", source)

    def test_stage_reentry_repairs_latest_after_commit_point_crash(self):
        args, _ = release_args("FinalizeStage")
        staged = {
            "schema": "fbsir.u3wW1aDeploymentReadinessReceipt.v2",
            "state": "STAGED_FOR_SWITCH",
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "backendBuildSha256": args.backend_sha,
            "frontendBuildSha256": args.frontend_tree_sha,
            "runnerSha256": args.runner_sha,
            "workerSha256": args.worker_sha,
            "stageApprovalReceiptSha256": "7" * 64,
        }
        with tempfile.TemporaryDirectory() as temporary:
            final = pathlib.Path(temporary) / args.release_id
            final.mkdir()
            receipt = final / "deployment-readiness-receipt.json"
            receipt.write_text("{}\n", encoding="utf-8")
            expected_result = {
                "productionFilesystemChanged": False,
                "receiptPath": str(receipt),
            }
            with (
                mock.patch.object(
                    release, "validate_preparation_anchors"
                ),
                mock.patch.object(
                    release, "release_directory", return_value=final
                ),
                mock.patch.object(
                    release,
                    "incoming_directory",
                    return_value=pathlib.Path(temporary) / "incoming",
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=receipt,
                ),
                mock.patch.object(
                    release, "read_json", return_value=staged
                ),
                mock.patch.object(
                    release, "sha256_file", return_value="6" * 64
                ),
                mock.patch.object(
                    release,
                    "staged_worker_result",
                    return_value=expected_result,
                ),
                mock.patch.object(
                    release,
                    "advance_latest_receipt",
                    return_value=True,
                ) as repair,
            ):
                result = release.finalize_stage(args)
            repair.assert_called_once_with(receipt)
            self.assertTrue(result["productionFilesystemChanged"])

    def test_rollback_catches_timeout_and_writes_failure_receipt(self):
        transition = inspect.getsource(release.rollback_transition)
        rollback = inspect.getsource(release._rollback_release)
        wrapper = inspect.getsource(release.rollback_release)
        self.assertIn("except Exception", transition)
        self.assertIn("except Exception as initial_error", rollback)
        self.assertIn("write_rollback_failure_receipt", rollback)
        self.assertIn("EVIDENCE_FINALIZATION_FAILED", wrapper)
        self.assertIn("no longer matches current", rollback)
        self.assertIn("database safety read", rollback)

    def test_actual_process_default_off_contract_is_exact(self):
        source = inspect.getsource(release.assert_candidate_runtime_contract)
        environment_source = inspect.getsource(
            release.exact_loaded_environment_matches
        )
        self.assertIn("candidate_process_arguments", source)
        self.assertIn("processFlagValues", environment_source)
        self.assertIn("processForbiddenOverrideNames", environment_source)
        self.assertIn("dropInManifest", source)
        self.assertIn("externalConfigManifest", source)
        self.assertIn("additionalConfigSha256", source)

    def test_runtime_override_detection_covers_relaxed_aliases_and_spring(self):
        source = inspect.getsource(release.service_snapshot)
        self.assertIn("normalized_flag_names", source)
        self.assertIn(".startswith(", source)
        self.assertIn('"spring"', source)
        self.assertIn("normalized_java_override_names", source)
        self.assertIn("external_config_manifest()", source)

    def test_external_config_surface_is_strictly_allowlisted(self):
        source = inspect.getsource(release.external_config_manifest)
        self.assertIn("ADMIN_ROOT.iterdir()", source)
        self.assertIn('ADMIN_ROOT / "config"', source)
        self.assertIn("followlinks=False", source)
        self.assertIn("unreviewed Spring external config", source)
        self.assertIn("resolved_allowed", source)

    def test_additional_config_rejects_nested_w1a_override(self):
        content = (
            "fbsir:\n"
            "  independent-board:\n"
            "    attribution:\n"
            "      observation-writer-enabled: true\n"
        )
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "connector.yml"
            path.write_text(content, encoding="utf-8")
            with (
                mock.patch.object(
                    release,
                    "ADDITIONAL_CONFIG_PATH",
                    path,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=path,
                ),
                mock.patch.object(
                    release,
                    "load_additional_yaml_documents",
                    return_value=[
                        {
                            "fbsir": {
                                "independent-board": {
                                    "attribution": {
                                        "observation-writer-enabled": True
                                    }
                                }
                            }
                        }
                    ],
                ),
            ):
                with self.assertRaisesRegex(RuntimeError, "allowlist"):
                    release.validate_additional_config()

    def test_additional_config_rejects_inline_spring_import(self):
        content = (
            "spring: {config: {import: file:/tmp/override.yml}}\n"
        )
        with tempfile.TemporaryDirectory() as temporary:
            path = pathlib.Path(temporary) / "connector.yml"
            path.write_text(content, encoding="utf-8")
            with (
                mock.patch.object(
                    release,
                    "ADDITIONAL_CONFIG_PATH",
                    path,
                ),
                mock.patch.object(
                    release,
                    "validate_regular_file",
                    return_value=path,
                ),
                mock.patch.object(
                    release,
                    "load_additional_yaml_documents",
                    return_value=[
                        {
                            "spring": {
                                "config": {
                                    "import": "file:/tmp/override.yml"
                                }
                            }
                        }
                    ],
                ),
            ):
                with self.assertRaisesRegex(RuntimeError, "allowlist"):
                    release.validate_additional_config()

    def test_additional_config_safe_loader_is_version_and_duplicate_pinned(self):
        source = inspect.getsource(release.load_additional_yaml_documents)
        self.assertIn("EXPECTED_PYYAML_VERSION", source)
        self.assertIn("yaml.SafeLoader", source)
        self.assertIn("duplicate key", source)
        self.assertIn("yaml.load_all", source)


if __name__ == "__main__":
    unittest.main()
