import importlib.util
import os
import pathlib
import tempfile
import unittest
from unittest import mock


SCRIPT = pathlib.Path(__file__).with_name(
    "u3w-observation-activation-remote.py"
)
SPEC = importlib.util.spec_from_file_location(
    "u3w_observation_activation_remote", SCRIPT
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def valid_environment():
    values = {
        name: "false"
        for name in MODULE.ALL_ATTRIBUTION_FLAGS
    }
    values.update({
        MODULE.EVENT_KEY_ID_NAME: "w1a-20260723-k1",
        MODULE.EVENT_KEY_NAME: "base64:" + "QQ" * 32,
        MODULE.SAME_BINDING_KEY_NAME: "base64:" + "Qg" * 32,
    })
    return "\n".join(
        "{}={}".format(name, value)
        for name, value in values.items()
    ) + "\nUNMANAGED_VALUE=preserved\n"


class ObservationActivationContractTest(unittest.TestCase):
    def test_flag_matrix_is_exactly_five_true_and_seven_false(self):
        self.assertEqual(len(MODULE.ALL_ATTRIBUTION_FLAGS), 12)
        self.assertEqual(len(MODULE.ACTIVE_TRUE_FLAGS), 5)
        self.assertEqual(len(MODULE.ACTIVE_FALSE_FLAGS), 7)
        self.assertEqual(
            set(MODULE.ALL_ATTRIBUTION_FLAGS),
            set(MODULE.ACTIVE_TRUE_FLAGS)
            | set(MODULE.ACTIVE_FALSE_FLAGS),
        )
        self.assertFalse(
            set(MODULE.ACTIVE_TRUE_FLAGS)
            & set(MODULE.ACTIVE_FALSE_FLAGS)
        )
        self.assertIn(
            "FBSIR_BOARD_ATTRIBUTION_ENABLED",
            MODULE.ACTIVE_TRUE_FLAGS,
        )
        self.assertIn(
            "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
            MODULE.ACTIVE_TRUE_FLAGS,
        )
        self.assertIn(
            "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED",
            MODULE.ACTIVE_FALSE_FLAGS,
        )

    def test_render_activation_changes_only_contract_flags(self):
        before = valid_environment()
        after = MODULE.render_activation(before)
        before_values = MODULE.parse_environment(before)
        after_values = MODULE.parse_environment(after)

        for name in MODULE.ACTIVE_TRUE_FLAGS:
            self.assertEqual(after_values[name], "true")
        for name in MODULE.ACTIVE_FALSE_FLAGS:
            self.assertEqual(after_values[name], "false")
        self.assertEqual(after_values["UNMANAGED_VALUE"], "preserved")
        self.assertEqual(
            before_values[MODULE.EVENT_KEY_NAME],
            after_values[MODULE.EVENT_KEY_NAME],
        )
        self.assertEqual(
            before_values[MODULE.SAME_BINDING_KEY_NAME],
            after_values[MODULE.SAME_BINDING_KEY_NAME],
        )

    def test_public_evidence_never_contains_secret_values_or_digests(self):
        before = MODULE.parse_environment(valid_environment())
        active = MODULE.parse_environment(
            MODULE.render_activation(valid_environment())
        )
        event_material = MODULE.decode_material(
            active[MODULE.EVENT_KEY_NAME]
        )
        evidence = MODULE.configuration_evidence(
            active,
            event_material,
            expected_active=True,
        )
        rendered = MODULE.canonical_json(evidence)

        self.assertTrue(evidence["contractSatisfied"])
        self.assertTrue(evidence["eventKeyPresent"])
        self.assertTrue(evidence["sameBindingSecretPresent"])
        self.assertNotIn(before[MODULE.EVENT_KEY_NAME], rendered)
        self.assertNotIn(before[MODULE.SAME_BINDING_KEY_NAME], rendered)
        self.assertNotIn("eventKeySha256", rendered)
        self.assertNotIn("sameBindingSecretSha256", rendered)

    def test_active_ingress_requires_w1_verifier_semantics(self):
        current_host_shape = {
            "status": 200,
            "jsonCode": 500,
            "semantic": "OFFICIAL_IDENTITY_MISMATCH",
            "cacheControlNoStore": True,
            "bodyWithinLimit": True,
        }
        contract_shape = {
            **current_host_shape,
            "status": 400,
            "jsonCode": None,
        }
        self.assertTrue(
            MODULE.ingress_state_matches(current_host_shape, True)
        )
        self.assertTrue(
            MODULE.ingress_state_matches(contract_shape, True)
        )
        for changed in (
            {"status": 500},
            {"jsonCode": 200},
            {"semantic": "OTHER"},
            {"cacheControlNoStore": False},
            {"bodyWithinLimit": False},
        ):
            candidate = {**current_host_shape, **changed}
            self.assertFalse(
                MODULE.ingress_state_matches(candidate, True)
            )
        self.assertTrue(
            MODULE.ingress_state_matches({"status": 404}, False)
        )

    def test_atomic_write_preserves_mode_and_replaces_content(self):
        with tempfile.TemporaryDirectory() as directory:
            target = pathlib.Path(directory) / "fbsir-admin.env"
            target.write_text("OLD=true\n", encoding="utf-8")
            target.chmod(0o640)
            MODULE.atomic_replace_preserving_metadata(
                target, b"NEW=true\n"
            )
            self.assertEqual(
                target.read_text(encoding="utf-8"), "NEW=true\n"
            )
            if os.name != "nt":
                self.assertEqual(
                    target.stat().st_mode & 0o777, 0o640
                )

    def test_failed_activation_restores_exact_preimage_and_restarts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            env_path = root / "fbsir-admin.env"
            env_path.write_bytes(valid_environment().encode("utf-8"))
            env_path.chmod(0o600)
            backup = root / "environment.before"
            pending = root / "pending.json"
            terminal = root / "rollback.json"
            calls = []

            def restart_then_fail(expected_active):
                calls.append(expected_active)
                if expected_active:
                    raise MODULE.RuntimeExpectationError({
                        "ingressProbe": {
                            "status": 200,
                            "semantic": "OTHER",
                        }
                    })
                return {
                    "serviceActive": True,
                    "runtimeFlagsMatch": True,
                    "ingressStateMatch": True,
                }

            with mock.patch.object(MODULE, "ENV_PATH", env_path):
                result = MODULE.mutate_with_automatic_rollback(
                    activation_id="w1a-observe-20260725T120000Z-0123456789ab",
                    source_commit="1" * 40,
                    expected_environment_sha=MODULE.sha256_bytes(
                        valid_environment().encode("utf-8")
                    ),
                    backup_path=backup,
                    pending_path=pending,
                    terminal_rollback_path=terminal,
                    runtime_transition=restart_then_fail,
                )

            self.assertEqual(
                env_path.read_text(encoding="utf-8"),
                valid_environment(),
            )
            self.assertEqual(calls, [True, False])
            self.assertEqual(result["state"], "ROLLED_BACK")
            self.assertTrue(result["exactPreimageRestored"])
            self.assertEqual(
                result["failureEvidence"]["ingressProbe"][
                    "semantic"
                ],
                "OTHER",
            )
            self.assertFalse(pending.exists())
            self.assertTrue(terminal.exists())


if __name__ == "__main__":
    unittest.main()
