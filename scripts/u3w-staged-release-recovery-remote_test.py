import datetime as dt
import hashlib
import importlib.util
import json
import pathlib
import os
import types
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parent
MODULE_PATH = ROOT / "u3w-staged-release-recovery-remote.py"


def load_module():
    spec = importlib.util.spec_from_file_location(
        "u3w_staged_release_recovery_remote",
        MODULE_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def canonical_bytes(document):
    return (
        json.dumps(
            document,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
        )
        + "\n"
    ).encode("utf-8")


class StagedReleaseRecoveryContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.recovery = load_module()

    def approval(self):
        return {
            "schema":
                "fbsir.u3wStagedReleaseRecoveryApproval.v1",
            "action": "RECONCILE_STAGED_W1A_RELEASE",
            "targetHost": "api2.u3w.com",
            "runId":
                "w1a-stage-recovery-95eca0acb837-20260725T020000Z",
            "recoverySourceCommit": "a" * 40,
            "targetReleaseId":
                "w1a-release-95eca0acb837-20260725T005248Z",
            "targetSourceCommit": "9" * 40,
            "targetStageReceiptSha256": "3" * 64,
            "priorRecoveryReceiptSha256": "c" * 64,
            "approvedAt": "2026-07-25T02:00:00Z",
            "expiresAt": "2026-07-26T01:59:59Z",
            "authorizedBy": "workspace-user",
            "concurrentDdlProhibited": True,
            "productionFilesystemWrite": True,
            "productionDatabaseWrite": False,
            "productionServiceChange": False,
            "officialExpertsPackageChange": False,
            "runnerSha256": "1" * 64,
            "workerSha256": "2" * 64,
        }

    def args(self, approval_bytes):
        return types.SimpleNamespace(
            mode="Reconcile",
            run_id=
                "w1a-stage-recovery-95eca0acb837-20260725T020000Z",
            recovery_source_commit="a" * 40,
            target_release_id=
                "w1a-release-95eca0acb837-20260725T005248Z",
            target_source_commit="9" * 40,
            target_stage_receipt_sha="3" * 64,
            prior_recovery_receipt_sha="c" * 64,
            approval_sha=hashlib.sha256(approval_bytes).hexdigest(),
            approval_json_base64=self.recovery.base64.b64encode(
                approval_bytes
            ).decode("ascii"),
            runner_sha="1" * 64,
            worker_sha="2" * 64,
        )

    def prior_recovery(self, *, service_changed=True):
        return {
            "schema":
                "fbsir.u3wDefaultOffInterruptedApplyRecoveryReceipt.v1",
            "state":
                "INTERRUPTED_APPLY_RECOVERED_APPLICATION_"
                "DATABASE_043_RETAINED_DORMANT",
            "releaseId":
                "w1a-release-41bbd4fe4c6f-20260724T220633Z",
            "sourceCommit": "4" * 40,
            "applicationRestored": True,
            "topologyRestored": True,
            "deploymentCommitOutcome": "NOT_COMMITTED",
            "deploymentReceiptAbsent": True,
            "rollbackReceiptAbsent": True,
            "currentLinkAbsent": True,
            "releaseDropInMatched": True,
            "allW1aFlagsExplicitFalse": True,
            "databaseDownClaimed": False,
            "productionDatabaseChanged": False,
            "productionDatabaseChangedThisRecoveryRun": False,
            "productionDatabaseChangedSinceStage": False,
            "productionServiceChanged": service_changed,
            "productionServiceChangedThisRecoveryRun": False,
            "productionServiceChangedSinceStage": service_changed,
            "officialExpertsPackageChanged": False,
        }

    def validate_prior_recovery(self, document):
        release_id = document["releaseId"]
        receipt = (
            self.recovery.RELEASE_ROOT
            / release_id
            / "interrupted-apply-recovery-receipt.json"
        )
        expected_sha = "c" * 64
        app = {
            "priorRecoveryAnchor": {
                "schema":
                    "fbsir.u3wPriorInterruptedApplyRecoveryStageAnchor.v1",
                "receiptSchema": document["schema"],
                "receiptSha256": expected_sha,
                "releaseId": release_id,
                "sourceCommit": document["sourceCommit"],
                "state": document["state"],
                "receiptPath": str(receipt),
            }
        }
        with (
            mock.patch.object(
                self.recovery,
                "validate_regular_file",
                return_value=receipt,
            ),
            mock.patch.object(
                self.recovery,
                "sha256_file",
                return_value=expected_sha,
            ),
            mock.patch.object(
                self.recovery,
                "read_json",
                return_value=document,
            ),
        ):
            return self.recovery.validate_prior_recovery(
                app, expected_sha
            )

    def test_exact_reconcile_approval_is_accepted(self):
        approval = self.approval()
        raw = canonical_bytes(approval)
        args = self.args(raw)
        observed = self.recovery.validate_reconcile_approval(
            args,
            now=dt.datetime(
                2026, 7, 25, 2, 1, tzinfo=dt.timezone.utc
            ),
        )
        self.assertEqual(
            observed["sha256"], hashlib.sha256(raw).hexdigest()
        )
        self.assertEqual(observed["document"], approval)

    def test_unknown_approval_field_fails_closed(self):
        approval = self.approval()
        approval["extra"] = True
        raw = canonical_bytes(approval)
        args = self.args(raw)
        with self.assertRaisesRegex(RuntimeError, "fields"):
            self.recovery.validate_reconcile_approval(
                args,
                now=dt.datetime(
                    2026, 7, 25, 2, 1, tzinfo=dt.timezone.utc
                ),
            )

    def test_expired_approval_fails_closed(self):
        raw = canonical_bytes(self.approval())
        args = self.args(raw)
        with self.assertRaisesRegex(RuntimeError, "validity"):
            self.recovery.validate_reconcile_approval(
                args,
                now=dt.datetime(
                    2026, 7, 26, 2, 0, tzinfo=dt.timezone.utc
                ),
            )

    def test_expired_approval_can_only_enter_read_only_idempotent_check(
        self,
    ):
        raw = canonical_bytes(self.approval())
        args = self.args(raw)
        observed = self.recovery.validate_reconcile_approval(
            args,
            now=dt.datetime(
                2026, 7, 26, 2, 0, tzinfo=dt.timezone.utc
            ),
            require_current=False,
        )
        self.assertEqual(
            observed["sha256"], hashlib.sha256(raw).hexdigest()
        )

    def test_stage_v3_requires_zero_database_and_service_delta(self):
        stage = {
            "schema": "fbsir.u3wW1aDeploymentReadinessReceipt.v3",
            "state": "STAGED_FOR_SWITCH",
            "releaseId":
                "w1a-release-95eca0acb837-20260725T005248Z",
            "sourceCommit": "9" * 40,
            "targetHost": "api2.u3w.com",
            "serviceUnit": "fbsir-admin.service",
            "database": "fbsir",
            "stageApprovalReceiptSha256": "4" * 64,
            "applicationRollbackAssemblyVerified": True,
            "applicationRollbackProven": False,
            "databaseRollbackSafetyProven": True,
            "databaseDownClaimed": False,
            "actualActiveArtifactsMatched": False,
            "productionFilesystemChanged": True,
            "productionDatabaseChanged": False,
            "productionDatabaseChangedThisRun": False,
            "productionDatabaseChangedSinceStage": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
            "preStageRuntimeIdentity": {},
            "releaseArtifactManifest": {},
        }
        stage["preStageRuntimeIdentity"]["database"] = "fbsir"
        stage["releaseArtifactManifest"]["release-worker.py"] = {
            "mode": 384,
            "sha256": "5" * 64,
            "sizeBytes": 1,
        }
        self.recovery.validate_stage_identity(
            stage,
            target_release_id=stage["releaseId"],
            target_source_commit=stage["sourceCommit"],
        )
        stage["productionDatabaseChangedThisRun"] = True
        with self.assertRaisesRegex(RuntimeError, "identity"):
            self.recovery.validate_stage_identity(
                stage,
                target_release_id=stage["releaseId"],
                target_source_commit=stage["sourceCommit"],
            )

    def test_prior_recovery_accepts_historical_service_change_only(self):
        document = self.prior_recovery(service_changed=True)
        receipt = self.validate_prior_recovery(document)
        self.assertEqual(
            receipt,
            self.recovery.RELEASE_ROOT
            / document["releaseId"]
            / "interrupted-apply-recovery-receipt.json",
        )

    def test_prior_recovery_rejects_service_change_in_recovery_run(self):
        document = self.prior_recovery(service_changed=True)
        document["productionServiceChangedThisRecoveryRun"] = True
        with self.assertRaisesRegex(RuntimeError, "identity"):
            self.validate_prior_recovery(document)

    def test_prior_recovery_rejects_inconsistent_service_aggregate(self):
        document = self.prior_recovery(service_changed=False)
        document["productionServiceChangedSinceStage"] = True
        with self.assertRaisesRegex(RuntimeError, "identity"):
            self.validate_prior_recovery(document)

    def test_prior_recovery_rejects_numeric_service_since_stage(self):
        document = self.prior_recovery(service_changed=True)
        document["productionServiceChangedSinceStage"] = 1
        with self.assertRaisesRegex(RuntimeError, "identity"):
            self.validate_prior_recovery(document)

    def test_lexical_latest_target_preserves_dangling_stage_cas(self):
        original = self.recovery.LATEST_RECEIPT
        latest = pathlib.Path.cwd() / "releases" / "latest-receipt.json"
        target = (
            latest.parent
            / "w1a-release-95eca0acb837-20260725T005248Z"
            / "deployment-readiness-receipt.json"
        )
        try:
            self.recovery.LATEST_RECEIPT = latest
            with (
                mock.patch.object(pathlib.Path, "is_symlink",
                                  return_value=True),
                mock.patch.object(os, "readlink",
                                  return_value=str(target)),
            ):
                self.assertEqual(
                    self.recovery.lexical_latest_target(),
                    target,
                )
        finally:
            self.recovery.LATEST_RECEIPT = original

    def test_quarantine_name_cannot_be_executed_as_a_release_id(self):
        release_id = "w1a-release-95eca0acb837-20260725T005248Z"
        name = self.recovery.quarantine_name(release_id, "3" * 64)
        self.assertTrue(name.startswith(".quarantine-stage-"))
        self.assertIsNone(self.recovery.RELEASE_ID_PATTERN.fullmatch(name))

    def test_recovery_receipt_keeps_mutation_boundaries_explicit(self):
        document = self.recovery.recovery_receipt_document(
            run_id=
                "w1a-stage-recovery-95eca0acb837-20260725T020000Z",
            recovery_source_commit="a" * 40,
            target_release_id=
                "w1a-release-95eca0acb837-20260725T005248Z",
            target_source_commit="9" * 40,
            target_stage_receipt_sha256="3" * 64,
            prior_recovery_receipt_sha256="c" * 64,
            approval_sha256="4" * 64,
            runner_sha256="1" * 64,
            worker_sha256="2" * 64,
            quarantine_path="/opt/fbsir/admin/releases/.quarantine-stage-x",
            staged_tree={"sha256": "5" * 64, "fileCount": 4,
                         "totalBytes": 100},
            service_sha256="6" * 64,
            database_sha256="7" * 64,
            nginx_sha256="8" * 64,
            nginx_manifest_sha256="9" * 64,
            nginx_dump_sha256="a" * 64,
            observed_at="2026-07-25T02:01:00Z",
        )
        self.assertEqual(document["state"], "STAGE_RECONCILED")
        self.assertTrue(document["productionFilesystemChanged"])
        self.assertFalse(document["productionDatabaseChanged"])
        self.assertFalse(document["productionServiceChanged"])
        self.assertFalse(document["officialExpertsPackageChanged"])
        self.assertEqual(
            document["latestReceiptStateAfter"],
            "INTERRUPTED_APPLY_RECOVERED_APPLICATION_"
            "DATABASE_043_RETAINED_DORMANT",
        )


if __name__ == "__main__":
    unittest.main()
