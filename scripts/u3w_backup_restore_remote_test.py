import base64
import datetime as dt
import hashlib
import importlib.util
import json
import os
import pathlib
import sys
import tempfile
import types
import unittest


HERE = pathlib.Path(__file__).resolve().parent
if "fcntl" not in sys.modules:
    sys.modules["fcntl"] = types.SimpleNamespace(
        LOCK_EX=2, LOCK_UN=8, flock=lambda *_: None
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


class RemoteSafetyTest(unittest.TestCase):
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
            runner_sha="c" * 64,
            worker_sha="d" * 64,
        )
        backup.validate_arguments(args)
        args.approval_sha = "0" * 64
        with self.assertRaises(RuntimeError):
            backup.validate_arguments(args)


if __name__ == "__main__":
    unittest.main()
