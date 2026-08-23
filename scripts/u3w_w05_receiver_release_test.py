import datetime as dt
import importlib.util
import json
import hashlib
import hmac
import pathlib
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("u3w-w05-receiver-release.py")
SPEC = importlib.util.spec_from_file_location("u3w_w05_release", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)
SHADOW_SCRIPT = pathlib.Path(__file__).with_name("u3w-w05-receiver-shadow.py")
SHADOW_SPEC = importlib.util.spec_from_file_location("u3w_w05_shadow", SHADOW_SCRIPT)
SHADOW = importlib.util.module_from_spec(SHADOW_SPEC)
assert SHADOW_SPEC.loader is not None
SHADOW_SPEC.loader.exec_module(SHADOW)


class W05ReleaseWorkerTest(unittest.TestCase):
    def test_shadow_signer_matches_shared_node_java_vector(self):
        vector_path = pathlib.Path(__file__).parents[1] / (
            "FBSir-business/src/test/resources/"
            "independent-board-attribution-v1-golden-vector.json"
        )
        vector = json.loads(vector_path.read_text(encoding="utf-8"))
        event = dict(vector["event"])
        supplied = event.pop("signature")
        event["signature"] = ""
        canonical = SHADOW.compact_json(SHADOW.canonical_map(event))
        computed = hmac.new(
            b"0123456789abcdef0123456789abcdef",
            canonical.encode(),
            hashlib.sha256,
        ).hexdigest()
        self.assertEqual("v1=" + computed, supplied)
        self.assertEqual(
            vector["expected"]["eventDigest"],
            SHADOW.business_digest(event),
        )

    def test_schema_state_profiles_are_strict(self):
        state_043 = {
            "receipts": [
                "20260723_independent_board_attribution_v1_043|APPLIED:x",
                "public_init_043|APPLIED:x",
            ],
            "checks": [
                "event|YES|26.7.21 26.7.20",
                "journey|YES|26.7.21 26.7.20",
            ],
            "journeyIdentityColumns":
                "contract_id,tenant_subject_digest,server_binding_id,journey_id",
        }
        self.assertTrue(MODULE.state_is_043(state_043))
        drifted = dict(state_043)
        drifted["checks"] = list(state_043["checks"]) + ["extra|YES|OR 1=1"]
        self.assertFalse(MODULE.state_is_043(drifted))

        state_044 = {
            "receipts": [
                "20260823_independent_board_attribution_identity_registry_044|APPLIED:x",
                "public_init_044|APPLIED:x",
            ],
            "checks": [
                "event|YES|WORKBUDDY WORKBUDDYAI 26.7.21 26.8.19",
                "journey|YES|26.7.21 26.8.19",
            ],
            "journeyIdentityColumns": (
                "contract_id,tenant_subject_digest,server_binding_id,journey_id,"
                "product_id,listed_manifest_version,embedded_contract_version"
            ),
        }
        self.assertTrue(MODULE.state_is_044(state_044))
        wrong_index = dict(state_044)
        wrong_index["journeyIdentityColumns"] = "contract_id"
        self.assertFalse(MODULE.state_is_044(wrong_index))

    def test_pending_set_hash_is_stable_and_outbox_is_read_only(self):
        with tempfile.TemporaryDirectory() as root:
            path = pathlib.Path(root) / "outbox.json"
            first = "1" * 64
            second = "2" * 64
            path.write_text(
                json.dumps(
                    {
                        "items": [
                            {"status": "PENDING", "eventDigest": second},
                            {"status": "DELIVERED_202", "eventDigest": "3" * 64},
                            {"status": "PENDING", "eventDigest": first},
                        ]
                    }
                ),
                encoding="utf-8",
            )
            old_outbox = MODULE.OUTBOX
            old_regular = MODULE.regular
            MODULE.OUTBOX = path
            MODULE.regular = lambda *_args, **_kwargs: None
            try:
                items, digest = MODULE.pending_items()
            finally:
                MODULE.OUTBOX = old_outbox
                MODULE.regular = old_regular
            self.assertEqual(2, len(items))
            self.assertEqual(
                MODULE.sha256_bytes((first + "\n" + second + "\n").encode()),
                digest,
            )
            source = SCRIPT.read_text(encoding="utf-8")
            self.assertNotIn("OUTBOX.write", source)
            self.assertNotIn("OUTBOX.unlink", source)
            self.assertNotIn("OUTBOX.rename", source)

    def test_replay_environment_is_exact_bounded_and_non_reported(self):
        with tempfile.TemporaryDirectory() as root:
            release = pathlib.Path(root)
            pending = [
                {"eventDigest": "b" * 64},
                {"eventDigest": "a" * 64},
            ]
            not_after = MODULE.now() + dt.timedelta(minutes=15)
            path, file_sha, set_sha = MODULE.materialize_replay_environment(
                release,
                pending,
                {"replayNotAfter": MODULE.iso(not_after)},
            )
            text = path.read_text(encoding="utf-8")
            self.assertIn("HISTORICAL_SYNTHETIC_REPLAY_ENABLED=true", text)
            self.assertIn("MAX_AGE_HOURS=168", text)
            self.assertIn("=" + "a" * 64 + "," + "b" * 64, text)
            self.assertEqual(file_sha, MODULE.sha256_file(path))
            self.assertEqual(
                MODULE.sha256_bytes(("a" * 64 + "\n" + "b" * 64 + "\n").encode()),
                set_sha,
            )

    def test_candidate_manifest_binds_every_file(self):
        with tempfile.TemporaryDirectory() as root:
            releases = pathlib.Path(root)
            release = releases / "w05-receiver-123456789abc-20260823T060000Z"
            (release / "backend").mkdir(parents=True)
            (release / "sql").mkdir()
            (release / "evidence").mkdir()
            files = {
                "backend/fbsir-admin.jar": b"jar",
                "bin/u3w-w05-receiver-release.py": b"worker",
                "bin/u3w-w05-receiver-shadow.py": b"shadow",
                "sql/public_init_043.sql": b"sql43",
                "sql/public_init_044.sql": b"sql",
                "evidence/source-manifest.json": b"{}\n",
                "shadow/sql/init-manifest.json": b"{}\n",
            }
            manifest_files = {}
            for relative, payload in files.items():
                path = release / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(payload)
                manifest_files[relative] = {
                    "sha256": MODULE.sha256_bytes(payload),
                    "sizeBytes": len(payload),
                }
            manifest = {
                "schemaVersion": "fbsir.w05ReceiverCandidateManifest.v1",
                "releaseId": release.name,
                "migration043Sha256": MODULE.EXPECTED_043_SHA,
                "expensiveGatesSkipped": False,
                "files": manifest_files,
            }
            manifest_path = release / "w05-candidate-manifest.json"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            old_releases = MODULE.RELEASES
            old_regular = MODULE.regular
            MODULE.RELEASES = releases
            MODULE.regular = lambda *_args, **_kwargs: None
            try:
                loaded, digest = MODULE.validate_candidate(release)
                self.assertEqual(release.name, loaded["releaseId"])
                self.assertEqual(MODULE.sha256_file(manifest_path), digest)
                (release / "backend/fbsir-admin.jar").write_bytes(b"drift")
                with self.assertRaises(RuntimeError):
                    MODULE.validate_candidate(release)
            finally:
                MODULE.RELEASES = old_releases
                MODULE.regular = old_regular


if __name__ == "__main__":
    unittest.main()
