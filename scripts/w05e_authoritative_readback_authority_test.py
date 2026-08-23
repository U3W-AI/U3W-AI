import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey


SCRIPT = Path(__file__).with_name("w05e-authoritative-readback-authority.py")
SPEC = importlib.util.spec_from_file_location("w05e_authority", SCRIPT)
AUTHORITY = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(AUTHORITY)

PRIVATE = Ed25519PrivateKey.from_private_bytes(bytes(range(32)))
OTHER_PRIVATE = Ed25519PrivateKey.from_private_bytes(bytes(range(32, 64)))
PRIVATE_BYTES = PRIVATE.private_bytes(
    serialization.Encoding.DER,
    serialization.PrivateFormat.PKCS8,
    serialization.NoEncryption(),
)
PUBLIC_BYTES = PRIVATE.public_key().public_bytes(
    serialization.Encoding.Raw,
    serialization.PublicFormat.Raw,
)
OTHER_PUBLIC_BYTES = OTHER_PRIVATE.public_key().public_bytes(
    serialization.Encoding.Raw,
    serialization.PublicFormat.Raw,
)
KEY_ID = "w05e-authority-20260823"
OTHER_KEY_ID = "w05e-authority-other"
ISSUED = datetime(2026, 8, 23, 9, 0, tzinfo=timezone.utc)
EXPIRES = ISSUED + timedelta(hours=23, minutes=59)
NOW = ISSUED + timedelta(hours=1)


def digest(character: str) -> str:
    return character * 64


def evidence() -> dict:
    request_digest = digest("1")
    response_digest = digest("2")
    combined = AUTHORITY.sha256_canonical(
        {
            "maximumRequestBytes": 16384,
            "maximumResponseBytes": 16384,
            "method": "POST",
            "path": AUTHORITY.READBACK_PATH,
            "requestContractSha256": request_digest,
            "responseContractSha256": response_digest,
        }
    )
    release_id = "w05e-receiver-0123456789ab-20260823T090000Z"
    release_path = AUTHORITY.RELEASE_ROOT + release_id
    writes = {name: 0 for name in AUTHORITY.WRITE_KEYS}
    comparisons = {name: True for name in AUTHORITY.COMPARISON_KEYS}
    return {
        "source": {
            "repository": "https://github.com/U3W-AI/U3W-AI.git",
            "commitSha": "a" * 40,
            "remoteCommitSha": "a" * 40,
            "treeSha": "b" * 40,
            "clean": True,
        },
        "reproducibleJar": {
            "build1JarSha256": digest("3"),
            "build2JarSha256": digest("3"),
            "exactJarSha256": digest("3"),
        },
        "routeContracts": {
            "requestContractSha256": request_digest,
            "responseContractSha256": response_digest,
            "combinedContractSha256": combined,
            "method": "POST",
            "path": AUTHORITY.READBACK_PATH,
            "maximumRequestBytes": 16384,
            "maximumResponseBytes": 16384,
        },
        "defaultOffConfiguration": {
            "digestSha256": digest("4"),
            "defaultEnabled": False,
            "configuredEnabled": False,
            "routeAbsentWhenDisabled": True,
        },
        "mysqlZeroWriteShadow": {
            "receiptSha256": digest("5"),
            "beforeStateSha256": digest("6"),
            "afterStateSha256": digest("6"),
            "status": "PASS",
            "isolatedMySql": True,
            "productionDatabaseUsed": False,
            "writes": writes,
            "comparisons": comparisons,
        },
        "publicDeny": {
            "configurationReadbackSha256": digest("7"),
            "route": AUTHORITY.READBACK_PATH,
            "directPublicExposureAllowed": False,
            "denyEffective": True,
            "loopbackSignatureRequired": True,
        },
        "candidateShadow": {
            "releaseId": release_id,
            "manifestSha256": digest("8"),
            "shadowReceiptSha256": digest("9"),
            "shadowStatus": "PASS",
            "jarSha256": digest("3"),
        },
        "activeTarget": {
            "releaseId": release_id,
            "releasePath": release_path,
            "activeSymlinkTarget": release_path,
            "symlinkReadbackSha256": digest("a"),
            "systemdUnit": AUTHORITY.SYSTEMD_UNIT,
            "processReadbackSha256": digest("b"),
            "serviceActive": True,
            "unexpectedRestartCount": 0,
            "jarSha256": digest("3"),
            "readbackReleaseId": release_id,
            "readbackJarSha256": digest("3"),
            "readbackStatus": "EXACT",
        },
        "invalidationBindings": {
            "routeContractSha256": combined,
            "nginxDenySha256": digest("7"),
            "databaseSchemaContractSha256": digest("c"),
            "keyringFingerprintSha256": digest("d"),
        },
        "creditBoundary": {
            "recordOnly": True,
            "productCreditEligible": False,
            "productCreditPromoted": False,
            "naturalTrafficPromoted": False,
        },
    }


def receipt() -> dict:
    return AUTHORITY.materialize_receipt(
        evidence(),
        issued_at=ISSUED,
        expires_at=EXPIRES,
        signing_key=PRIVATE_BYTES,
        signer_key_id=KEY_ID,
        signer_trust_domain="w05e.independent.release-authority",
    )


def verify(document, *, public=PUBLIC_BYTES, expected_public=PUBLIC_BYTES, key_id=KEY_ID, now=NOW):
    return AUTHORITY.verify_receipt(
        document,
        public_key=public,
        expected_signer_fingerprint=AUTHORITY.public_key_fingerprint(expected_public),
        expected_signer_key_id=key_id,
        now=now,
    )


class AuthorityReceiptTests(unittest.TestCase):
    def assert_rejected(self, document, code, *, public=PUBLIC_BYTES, expected_public=PUBLIC_BYTES, key_id=KEY_ID, now=NOW):
        with self.assertRaises(AUTHORITY.AuthorityReceiptError) as caught:
            verify(document, public=public, expected_public=expected_public, key_id=key_id, now=now)
        self.assertEqual(caught.exception.code, code)

    def test_materializes_and_verifies_with_public_key_only(self):
        document = receipt()
        result = verify(document)
        self.assertEqual(result["status"], "PASS")
        self.assertFalse(result["productCreditEligible"])
        self.assertFalse(result["productionContacted"])
        self.assertEqual(result["authorityRole"], "independent_release_authority")
        self.assertEqual(result["transportHmac"]["role"], "receiver_transport_integrity_only")
        self.assertEqual(document, json.loads(AUTHORITY.canonical_json(document)))

    def test_deterministic_materialization_and_spki_fingerprint(self):
        self.assertEqual(receipt(), receipt())
        pem = PRIVATE.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
        )
        self.assertEqual(
            AUTHORITY.public_key_fingerprint(pem),
            AUTHORITY.public_key_fingerprint(PUBLIC_BYTES),
        )
        self.assertEqual(
            AUTHORITY.signer_fingerprint(PRIVATE_BYTES),
            AUTHORITY.public_key_fingerprint(PUBLIC_BYTES),
        )

    def test_rejects_receipt_tampering_even_when_shape_remains_valid(self):
        document = receipt()
        document["defaultOffConfiguration"]["digestSha256"] = digest("e")
        self.assert_rejected(document, "evidence_digest_mismatch")

    def test_rejects_unknown_fields(self):
        document = receipt()
        document["authorityClaim"] = True
        self.assert_rejected(document, "invalid_shape")
        document = receipt()
        document["signer"]["unknown"] = True
        self.assert_rejected(document, "invalid_shape")

    def test_rejects_wrong_public_key_and_key_id(self):
        self.assert_rejected(
            receipt(), "signer_key_fingerprint_mismatch", public=OTHER_PUBLIC_BYTES,
            expected_public=PUBLIC_BYTES,
        )
        self.assert_rejected(receipt(), "invalid_invariant", key_id=OTHER_KEY_ID)

    def test_public_verifier_cannot_sign(self):
        with self.assertRaises(AUTHORITY.AuthorityReceiptError):
            AUTHORITY.materialize_receipt(
                evidence(),
                issued_at=ISSUED,
                expires_at=EXPIRES,
                signing_key=PUBLIC_BYTES,
                signer_key_id=KEY_ID,
                signer_trust_domain="w05e.independent.release-authority",
            )

    def test_materializer_rejects_receiver_keyring_as_authority_signer(self):
        value = evidence()
        value["invalidationBindings"]["keyringFingerprintSha256"] = AUTHORITY.signer_fingerprint(
            PRIVATE_BYTES
        )
        with self.assertRaises(AUTHORITY.AuthorityReceiptError) as caught:
            AUTHORITY.materialize_receipt(
                value,
                issued_at=ISSUED,
                expires_at=EXPIRES,
                signing_key=PRIVATE_BYTES,
                signer_key_id=KEY_ID,
                signer_trust_domain="w05e.independent.release-authority",
            )
        self.assertEqual(caught.exception.code, "signer_not_independent")

    def test_rejects_expired_and_not_yet_valid_receipts(self):
        self.assert_rejected(receipt(), "receipt_expired", now=EXPIRES)
        self.assert_rejected(
            receipt(), "receipt_not_yet_valid", now=ISSUED - timedelta(seconds=1)
        )

    def test_materializer_rejects_validity_over_24_hours(self):
        with self.assertRaises(AUTHORITY.AuthorityReceiptError) as caught:
            AUTHORITY.materialize_receipt(
                evidence(),
                issued_at=ISSUED,
                expires_at=ISSUED + timedelta(hours=24, microseconds=1),
                signing_key=PRIVATE_BYTES,
                signer_key_id=KEY_ID,
                signer_trust_domain="w05e.independent.release-authority",
            )
        self.assertEqual(caught.exception.code, "invalid_validity_window")

    def test_rejects_algorithm_downgrade_and_bad_signature(self):
        document = receipt()
        document["signer"]["algorithm"] = "hmac-sha256-v1"
        self.assert_rejected(document, "invalid_invariant")
        document = receipt()
        document["signature"] = "v1=" + "0" * 64
        self.assert_rejected(document, "invalid_signature")
        document = receipt()
        document["signature"] = "v2=" + "0" * 128
        self.assert_rejected(document, "signature_mismatch")

    def test_rejects_v1_hmac_schema(self):
        document = receipt()
        document["schemaVersion"] = (
            "fbsir.independentBoardAuthoritativeReadbackAuthorityReceipt.v1"
        )
        self.assert_rejected(document, "invalid_invariant")

    def test_materializer_rejects_binding_and_credit_regressions(self):
        cases = []
        value = evidence()
        value["source"]["remoteCommitSha"] = "f" * 40
        cases.append((value, "source_not_pushed"))
        value = evidence()
        value["creditBoundary"]["productCreditEligible"] = True
        cases.append((value, "invalid_invariant"))
        value = evidence()
        value["mysqlZeroWriteShadow"]["afterStateSha256"] = digest("e")
        cases.append((value, "mysql_state_changed"))
        for value, expected in cases:
            with self.subTest(expected=expected), self.assertRaises(
                AUTHORITY.AuthorityReceiptError
            ) as caught:
                AUTHORITY.materialize_receipt(
                    value,
                    issued_at=ISSUED,
                    expires_at=EXPIRES,
                    signing_key=PRIVATE_BYTES,
                    signer_key_id=KEY_ID,
                    signer_trust_domain="w05e.independent.release-authority",
                )
            self.assertEqual(caught.exception.code, expected)

    def test_cli_round_trip_uses_private_only_for_materialize_and_public_for_verify(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evidence_path = root / "evidence.json"
            private_path = root / "authority-private.key"
            public_path = root / "authority-public.key"
            receipt_path = root / "receipt.json"
            evidence_path.write_text(json.dumps(evidence()), encoding="utf-8")
            private_path.write_bytes(PRIVATE_BYTES)
            public_path.write_bytes(PUBLIC_BYTES)
            materialized = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "materialize",
                    "--evidence",
                    str(evidence_path),
                    "--private-key-file",
                    str(private_path),
                    "--issued-at",
                    "2026-08-23T09:00:00Z",
                    "--expires-at",
                    "2026-08-24T08:59:00Z",
                    "--signer-key-id",
                    KEY_ID,
                    "--signer-trust-domain",
                    "w05e.independent.release-authority",
                    "--output",
                    str(receipt_path),
                ],
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(materialized.returncode, 0, materialized.stderr)
            verified = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "verify",
                    "--receipt",
                    str(receipt_path),
                    "--public-key-file",
                    str(public_path),
                    "--expected-signer-fingerprint",
                    AUTHORITY.public_key_fingerprint(PUBLIC_BYTES),
                    "--expected-signer-key-id",
                    KEY_ID,
                    "--now",
                    "2026-08-23T10:00:00Z",
                ],
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(verified.returncode, 0, verified.stderr)
            self.assertFalse(json.loads(verified.stdout)["productionContacted"])

    def test_cli_rejects_network_shaped_input(self):
        with tempfile.TemporaryDirectory() as directory:
            public_path = Path(directory) / "authority-public.key"
            public_path.write_bytes(PUBLIC_BYTES)
            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "verify",
                    "--receipt",
                    "https://api2.u3w.com/receipt.json",
                    "--public-key-file",
                    str(public_path),
                    "--expected-signer-fingerprint",
                    AUTHORITY.public_key_fingerprint(PUBLIC_BYTES),
                    "--expected-signer-key-id",
                    KEY_ID,
                    "--now",
                    "2026-08-23T10:00:00Z",
                ],
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 2)
            self.assertEqual(json.loads(result.stderr)["code"], "nonlocal_input_forbidden")


if __name__ == "__main__":
    unittest.main(verbosity=2)
