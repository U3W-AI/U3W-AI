#!/usr/bin/env python3
"""Offline materializer and verifier for the W05E readback authority receipt.

This module intentionally has no networking, database, subprocess, or production
runtime integration.  It consumes already-produced evidence, binds it into one
canonical receipt, and authenticates that receipt with an independent Ed25519
authority key.  Receiver HMACs remain transport integrity only; they are not
accepted as authority receipts.

The functions are pure with respect to their arguments.  Only ``main`` performs
explicit local file reads/writes requested by the caller.
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import re
import sys
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Mapping
from urllib.parse import urlsplit

try:
    from cryptography.exceptions import InvalidSignature, UnsupportedAlgorithm
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric.ed25519 import (
        Ed25519PrivateKey,
        Ed25519PublicKey,
    )
except ImportError as error:  # pragma: no cover - exercised by dependency preflight
    raise RuntimeError(
        "cryptography>=41 with Ed25519 support is required for authority v2"
    ) from error


SCHEMA_VERSION = "fbsir.independentBoardAuthoritativeReadbackAuthorityReceipt.v2"
SIGNATURE_ALGORITHM = "ed25519"
TRANSPORT_HMAC_ALGORITHM = "hmac-sha256-v1"
TRANSPORT_HMAC_DOMAIN = "FBSIR_INDEPENDENT_BOARD_READBACK_RESPONSE_V1"
AUTHORITY_DOMAIN = b"FBSIR_INDEPENDENT_BOARD_AUTHORITY_RECEIPT_V2"
MAXIMUM_VALIDITY = timedelta(hours=24)
ED25519_RAW_KEY_BYTES = 32
ED25519_SIGNATURE_BYTES = 64
MAXIMUM_LOCAL_INPUT_BYTES = 16 * 1024 * 1024
READBACK_PATH = "/internal/independent-board/attribution/events/readback"
SYSTEMD_UNIT = "fbsir-admin.service"
RELEASE_ROOT = "/opt/fbsir/admin/releases/"

HEX40_OR_64 = re.compile(r"(?:[0-9a-f]{40}|[0-9a-f]{64})\Z")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
RELEASE_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,159}\Z")

TOP_LEVEL_KEYS = frozenset(
    {
        "schemaVersion",
        "receiptId",
        "issuedAt",
        "expiresAt",
        "source",
        "reproducibleJar",
        "routeContracts",
        "defaultOffConfiguration",
        "mysqlZeroWriteShadow",
        "publicDeny",
        "candidateShadow",
        "activeTarget",
        "invalidationBindings",
        "creditBoundary",
        "signer",
        "evidenceDigestSha256",
        "signature",
    }
)

EVIDENCE_KEYS = frozenset(
    {
        "source",
        "reproducibleJar",
        "routeContracts",
        "defaultOffConfiguration",
        "mysqlZeroWriteShadow",
        "publicDeny",
        "candidateShadow",
        "activeTarget",
        "invalidationBindings",
        "creditBoundary",
    }
)

SECTION_KEYS = {
    "source": frozenset(
        {"repository", "commitSha", "remoteCommitSha", "treeSha", "clean"}
    ),
    "reproducibleJar": frozenset(
        {"build1JarSha256", "build2JarSha256", "exactJarSha256"}
    ),
    "routeContracts": frozenset(
        {
            "requestContractSha256",
            "responseContractSha256",
            "combinedContractSha256",
            "method",
            "path",
            "maximumRequestBytes",
            "maximumResponseBytes",
        }
    ),
    "defaultOffConfiguration": frozenset(
        {
            "digestSha256",
            "defaultEnabled",
            "configuredEnabled",
            "routeAbsentWhenDisabled",
        }
    ),
    "mysqlZeroWriteShadow": frozenset(
        {
            "receiptSha256",
            "beforeStateSha256",
            "afterStateSha256",
            "status",
            "isolatedMySql",
            "productionDatabaseUsed",
            "writes",
            "comparisons",
        }
    ),
    "publicDeny": frozenset(
        {
            "configurationReadbackSha256",
            "route",
            "directPublicExposureAllowed",
            "denyEffective",
            "loopbackSignatureRequired",
        }
    ),
    "candidateShadow": frozenset(
        {
            "releaseId",
            "manifestSha256",
            "shadowReceiptSha256",
            "shadowStatus",
            "jarSha256",
        }
    ),
    "activeTarget": frozenset(
        {
            "releaseId",
            "releasePath",
            "activeSymlinkTarget",
            "symlinkReadbackSha256",
            "systemdUnit",
            "processReadbackSha256",
            "serviceActive",
            "unexpectedRestartCount",
            "jarSha256",
            "readbackReleaseId",
            "readbackJarSha256",
            "readbackStatus",
        }
    ),
    "invalidationBindings": frozenset(
        {
            "routeContractSha256",
            "nginxDenySha256",
            "databaseSchemaContractSha256",
            "keyringFingerprintSha256",
        }
    ),
    "creditBoundary": frozenset(
        {
            "recordOnly",
            "productCreditEligible",
            "productCreditPromoted",
            "naturalTrafficPromoted",
        }
    ),
}

WRITE_KEYS = frozenset(
    {
        "databaseWrites",
        "journeyHeadWrites",
        "eventLedgerWrites",
        "creditWrites",
        "schemaMigrationWrites",
        "redisWrites",
        "filesystemStateWrites",
        "publisherOutboxWrites",
        "businessOrRuntimeJournalWrites",
    }
)

COMPARISON_KEYS = frozenset(
    {
        "rowCountsUnchanged",
        "journeyHeadVersionsUnchanged",
        "eventHighWatermarkUnchanged",
        "authoritativeProductCreditBitsUnchanged",
        "schemaMigrationReceiptsUnchanged",
    }
)

SIGNER_KEYS = frozenset(
    {
        "algorithm",
        "fingerprintSha256",
        "independentOfReceiver",
        "keyId",
        "role",
        "trustDomain",
    }
)


class AuthorityReceiptError(ValueError):
    """Stable fail-closed validation error."""

    def __init__(self, code: str, path: str, detail: str):
        super().__init__(f"{code} at {path}: {detail}")
        self.code = code
        self.path = path
        self.detail = detail

    def as_dict(self) -> dict[str, str]:
        return {"status": "FAIL_CLOSED", "code": self.code, "path": self.path}


def canonical_json(value: Any) -> bytes:
    """Return deterministic UTF-8 JSON bytes."""

    try:
        return json.dumps(
            value,
            ensure_ascii=False,
            allow_nan=False,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
    except (TypeError, ValueError):
        _fail("invalid_json_value", "document", "canonical JSON value required")


def sha256_canonical(value: Any) -> str:
    return hashlib.sha256(canonical_json(value)).hexdigest()


def _load_private_key(material: bytes) -> Ed25519PrivateKey:
    if not isinstance(material, bytes):
        _fail("invalid_private_key", "privateKey", "bytes required")
    try:
        if material.startswith(b"-----BEGIN"):
            key = serialization.load_pem_private_key(material, password=None)
        else:
            key = serialization.load_der_private_key(material, password=None)
    except (ValueError, TypeError, UnsupportedAlgorithm) as error:
        _fail("invalid_private_key", "privateKey", "Ed25519 PKCS#8 private key required")
    if not isinstance(key, Ed25519PrivateKey):
        _fail("invalid_private_key", "privateKey", "Ed25519 private key required")
    return key


def _load_public_key(material: bytes) -> Ed25519PublicKey:
    if not isinstance(material, bytes):
        _fail("invalid_public_key", "publicKey", "bytes required")
    try:
        if material.startswith(b"-----BEGIN"):
            key = serialization.load_pem_public_key(material)
        elif len(material) == ED25519_RAW_KEY_BYTES:
            key = Ed25519PublicKey.from_public_bytes(material)
        else:
            key = serialization.load_der_public_key(material)
    except (ValueError, TypeError, UnsupportedAlgorithm) as error:
        _fail("invalid_public_key", "publicKey", "Ed25519 raw key or SPKI public key required")
    if not isinstance(key, Ed25519PublicKey):
        _fail("invalid_public_key", "publicKey", "Ed25519 public key required")
    return key


def _public_key_der(key: Ed25519PublicKey) -> bytes:
    return key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )


def public_key_fingerprint(public_key: bytes) -> str:
    """Return SHA-256(SPKI DER), the sole v2 public-key fingerprint form."""

    return hashlib.sha256(_public_key_der(_load_public_key(public_key))).hexdigest()


def signer_fingerprint(private_key: bytes) -> str:
    """Return the SPKI fingerprint derived from an authority private key.

    This helper exists for offline materializer callers and never exposes or
    accepts a shared HMAC secret.  Verifiers must use ``public_key_fingerprint``
    with a public key instead.
    """

    private = _load_private_key(private_key)
    public = private.public_key()
    return hashlib.sha256(_public_key_der(public)).hexdigest()


def _fail(code: str, path: str, detail: str) -> None:
    raise AuthorityReceiptError(code, path, detail)


def _mapping(value: Any, path: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        _fail("invalid_type", path, "object required")
    if any(not isinstance(key, str) for key in value):
        _fail("invalid_shape", path, "string object keys required")
    return value


def _exact_keys(value: Mapping[str, Any], expected: frozenset[str], path: str) -> None:
    actual = frozenset(value.keys())
    if actual != expected:
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        _fail("invalid_shape", path, f"missing={missing};extra={extra}")


def _expect_bool(value: Any, expected: bool, path: str) -> None:
    if type(value) is not bool or value is not expected:
        _fail("invalid_invariant", path, f"must be {str(expected).lower()}")


def _expect_int(value: Any, expected: int, path: str) -> None:
    if type(value) is not int or value != expected:
        _fail("invalid_invariant", path, f"must equal {expected}")


def _expect_literal(value: Any, expected: str, path: str) -> None:
    if not isinstance(value, str) or value != expected:
        _fail("invalid_invariant", path, f"must equal {expected!r}")


def _hex64(value: Any, path: str) -> str:
    if not isinstance(value, str) or HEX64.fullmatch(value) is None:
        _fail("invalid_digest", path, "lowercase SHA-256 hex required")
    return value


def _git_oid(value: Any, path: str) -> str:
    if not isinstance(value, str) or HEX40_OR_64.fullmatch(value) is None:
        _fail("invalid_git_object_id", path, "lowercase 40- or 64-hex object id required")
    return value


def _validate_key_id(value: Any, path: str = "signer.keyId") -> str:
    if not isinstance(value, str) or re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,127}", value) is None:
        _fail("invalid_key_id", path, "stable lowercase key id required")
    return value


def _authority_signing_bytes(receipt: Mapping[str, Any]) -> bytes:
    """Domain-separated bytes signed by the independent authority."""

    return AUTHORITY_DOMAIN + b"\n" + canonical_json(_unsigned_receipt(receipt))


def _parse_utc_timestamp(value: Any, path: str) -> datetime:
    if not isinstance(value, str) or not value.endswith("Z"):
        _fail("invalid_timestamp", path, "canonical UTC timestamp ending in Z required")
    try:
        parsed = datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError:
        _fail("invalid_timestamp", path, "valid ISO-8601 timestamp required")
    if parsed.tzinfo != timezone.utc:
        _fail("invalid_timestamp", path, "UTC required")
    if _format_utc_timestamp(parsed) != value:
        _fail("noncanonical_timestamp", path, "use canonical second-or-microsecond UTC form")
    return parsed


def _format_utc_timestamp(value: datetime) -> str:
    if not isinstance(value, datetime) or value.tzinfo is None:
        _fail("invalid_timestamp", "timestamp", "timezone-aware datetime required")
    value = value.astimezone(timezone.utc)
    timespec = "microseconds" if value.microsecond else "seconds"
    return value.isoformat(timespec=timespec).replace("+00:00", "Z")


def _validate_repository(value: Any) -> None:
    if not isinstance(value, str):
        _fail("invalid_repository", "source.repository", "string required")
    parsed = urlsplit(value)
    if (
        parsed.scheme != "https"
        or parsed.hostname != "github.com"
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
        or not parsed.path.endswith(".git")
        or parsed.path.count("/") != 2
    ):
        _fail(
            "invalid_repository",
            "source.repository",
            "credential-free canonical https://github.com/<owner>/<repo>.git required",
        )


def _validate_evidence(evidence: Any) -> dict[str, Any]:
    root = _mapping(evidence, "evidence")
    _exact_keys(root, EVIDENCE_KEYS, "evidence")
    sections: dict[str, Mapping[str, Any]] = {}
    for name in sorted(EVIDENCE_KEYS):
        section = _mapping(root[name], name)
        _exact_keys(section, SECTION_KEYS[name], name)
        sections[name] = section

    source = sections["source"]
    _validate_repository(source["repository"])
    commit = _git_oid(source["commitSha"], "source.commitSha")
    remote_commit = _git_oid(source["remoteCommitSha"], "source.remoteCommitSha")
    _git_oid(source["treeSha"], "source.treeSha")
    if not hmac.compare_digest(commit, remote_commit):
        _fail("source_not_pushed", "source.remoteCommitSha", "must equal source commit")
    _expect_bool(source["clean"], True, "source.clean")

    jars = sections["reproducibleJar"]
    jar_values = [
        _hex64(jars[name], f"reproducibleJar.{name}")
        for name in ("build1JarSha256", "build2JarSha256", "exactJarSha256")
    ]
    if len(set(jar_values)) != 1:
        _fail("nonreproducible_jar", "reproducibleJar", "both builds and exact JAR must match")
    exact_jar = jar_values[2]

    route = sections["routeContracts"]
    request_digest = _hex64(
        route["requestContractSha256"], "routeContracts.requestContractSha256"
    )
    response_digest = _hex64(
        route["responseContractSha256"], "routeContracts.responseContractSha256"
    )
    combined_digest = _hex64(
        route["combinedContractSha256"], "routeContracts.combinedContractSha256"
    )
    _expect_literal(route["method"], "POST", "routeContracts.method")
    _expect_literal(route["path"], READBACK_PATH, "routeContracts.path")
    _expect_int(route["maximumRequestBytes"], 16384, "routeContracts.maximumRequestBytes")
    _expect_int(route["maximumResponseBytes"], 16384, "routeContracts.maximumResponseBytes")
    expected_combined = sha256_canonical(
        {
            "maximumRequestBytes": 16384,
            "maximumResponseBytes": 16384,
            "method": "POST",
            "path": READBACK_PATH,
            "requestContractSha256": request_digest,
            "responseContractSha256": response_digest,
        }
    )
    if not hmac.compare_digest(combined_digest, expected_combined):
        _fail(
            "contract_digest_mismatch",
            "routeContracts.combinedContractSha256",
            "must bind the request, response, method, path, and size limits",
        )

    config = sections["defaultOffConfiguration"]
    _hex64(config["digestSha256"], "defaultOffConfiguration.digestSha256")
    _expect_bool(config["defaultEnabled"], False, "defaultOffConfiguration.defaultEnabled")
    _expect_bool(
        config["configuredEnabled"], False, "defaultOffConfiguration.configuredEnabled"
    )
    _expect_bool(
        config["routeAbsentWhenDisabled"],
        True,
        "defaultOffConfiguration.routeAbsentWhenDisabled",
    )

    mysql = sections["mysqlZeroWriteShadow"]
    _hex64(mysql["receiptSha256"], "mysqlZeroWriteShadow.receiptSha256")
    before = _hex64(mysql["beforeStateSha256"], "mysqlZeroWriteShadow.beforeStateSha256")
    after = _hex64(mysql["afterStateSha256"], "mysqlZeroWriteShadow.afterStateSha256")
    if not hmac.compare_digest(before, after):
        _fail("mysql_state_changed", "mysqlZeroWriteShadow", "before and after digests differ")
    _expect_literal(mysql["status"], "PASS", "mysqlZeroWriteShadow.status")
    _expect_bool(mysql["isolatedMySql"], True, "mysqlZeroWriteShadow.isolatedMySql")
    _expect_bool(
        mysql["productionDatabaseUsed"], False, "mysqlZeroWriteShadow.productionDatabaseUsed"
    )
    writes = _mapping(mysql["writes"], "mysqlZeroWriteShadow.writes")
    _exact_keys(writes, WRITE_KEYS, "mysqlZeroWriteShadow.writes")
    for name in sorted(WRITE_KEYS):
        _expect_int(writes[name], 0, f"mysqlZeroWriteShadow.writes.{name}")
    comparisons = _mapping(mysql["comparisons"], "mysqlZeroWriteShadow.comparisons")
    _exact_keys(comparisons, COMPARISON_KEYS, "mysqlZeroWriteShadow.comparisons")
    for name in sorted(COMPARISON_KEYS):
        _expect_bool(comparisons[name], True, f"mysqlZeroWriteShadow.comparisons.{name}")

    deny = sections["publicDeny"]
    deny_digest = _hex64(
        deny["configurationReadbackSha256"], "publicDeny.configurationReadbackSha256"
    )
    _expect_literal(deny["route"], READBACK_PATH, "publicDeny.route")
    _expect_bool(
        deny["directPublicExposureAllowed"], False, "publicDeny.directPublicExposureAllowed"
    )
    _expect_bool(deny["denyEffective"], True, "publicDeny.denyEffective")
    _expect_bool(
        deny["loopbackSignatureRequired"], True, "publicDeny.loopbackSignatureRequired"
    )

    candidate = sections["candidateShadow"]
    candidate_release = candidate["releaseId"]
    if not isinstance(candidate_release, str) or RELEASE_ID.fullmatch(candidate_release) is None:
        _fail("invalid_release_id", "candidateShadow.releaseId", "safe release id required")
    _hex64(candidate["manifestSha256"], "candidateShadow.manifestSha256")
    _hex64(candidate["shadowReceiptSha256"], "candidateShadow.shadowReceiptSha256")
    _expect_literal(candidate["shadowStatus"], "PASS", "candidateShadow.shadowStatus")
    candidate_jar = _hex64(candidate["jarSha256"], "candidateShadow.jarSha256")
    if not hmac.compare_digest(candidate_jar, exact_jar):
        _fail("candidate_jar_mismatch", "candidateShadow.jarSha256", "must equal exact JAR")

    active = sections["activeTarget"]
    active_release = active["releaseId"]
    if active_release != candidate_release:
        _fail("active_release_mismatch", "activeTarget.releaseId", "must equal candidate release")
    expected_release_path = RELEASE_ROOT + candidate_release
    _expect_literal(active["releasePath"], expected_release_path, "activeTarget.releasePath")
    _expect_literal(
        active["activeSymlinkTarget"], expected_release_path, "activeTarget.activeSymlinkTarget"
    )
    _hex64(active["symlinkReadbackSha256"], "activeTarget.symlinkReadbackSha256")
    _expect_literal(active["systemdUnit"], SYSTEMD_UNIT, "activeTarget.systemdUnit")
    _hex64(active["processReadbackSha256"], "activeTarget.processReadbackSha256")
    _expect_bool(active["serviceActive"], True, "activeTarget.serviceActive")
    _expect_int(active["unexpectedRestartCount"], 0, "activeTarget.unexpectedRestartCount")
    active_jar = _hex64(active["jarSha256"], "activeTarget.jarSha256")
    if not hmac.compare_digest(active_jar, exact_jar):
        _fail("active_jar_mismatch", "activeTarget.jarSha256", "must equal exact JAR")
    _expect_literal(
        active["readbackReleaseId"], candidate_release, "activeTarget.readbackReleaseId"
    )
    readback_jar = _hex64(active["readbackJarSha256"], "activeTarget.readbackJarSha256")
    if not hmac.compare_digest(readback_jar, exact_jar):
        _fail(
            "readback_jar_mismatch", "activeTarget.readbackJarSha256", "must equal exact JAR"
        )
    _expect_literal(active["readbackStatus"], "EXACT", "activeTarget.readbackStatus")

    bindings = sections["invalidationBindings"]
    bound_route = _hex64(
        bindings["routeContractSha256"], "invalidationBindings.routeContractSha256"
    )
    if not hmac.compare_digest(bound_route, combined_digest):
        _fail(
            "route_binding_mismatch",
            "invalidationBindings.routeContractSha256",
            "must equal combined route contract digest",
        )
    bound_deny = _hex64(bindings["nginxDenySha256"], "invalidationBindings.nginxDenySha256")
    if not hmac.compare_digest(bound_deny, deny_digest):
        _fail(
            "deny_binding_mismatch",
            "invalidationBindings.nginxDenySha256",
            "must equal public deny readback digest",
        )
    _hex64(
        bindings["databaseSchemaContractSha256"],
        "invalidationBindings.databaseSchemaContractSha256",
    )
    _hex64(
        bindings["keyringFingerprintSha256"],
        "invalidationBindings.keyringFingerprintSha256",
    )

    credit = sections["creditBoundary"]
    _expect_bool(credit["recordOnly"], True, "creditBoundary.recordOnly")
    _expect_bool(
        credit["productCreditEligible"], False, "creditBoundary.productCreditEligible"
    )
    _expect_bool(
        credit["productCreditPromoted"], False, "creditBoundary.productCreditPromoted"
    )
    _expect_bool(
        credit["naturalTrafficPromoted"], False, "creditBoundary.naturalTrafficPromoted"
    )

    return json.loads(canonical_json(root))


def _evidence_from_receipt(receipt: Mapping[str, Any]) -> dict[str, Any]:
    return {name: receipt[name] for name in sorted(EVIDENCE_KEYS)}


def _unsigned_receipt(receipt: Mapping[str, Any]) -> dict[str, Any]:
    return {key: receipt[key] for key in sorted(TOP_LEVEL_KEYS - {"signature"})}


def materialize_receipt(
    evidence: Mapping[str, Any],
    *,
    issued_at: datetime,
    expires_at: datetime,
    signing_key: bytes,
    signer_key_id: str,
    signer_trust_domain: str,
) -> dict[str, Any]:
    """Validate evidence and return one signed canonical authority receipt."""

    normalized = _validate_evidence(evidence)
    private_key = _load_private_key(signing_key)
    key_id = _validate_key_id(signer_key_id)
    if not isinstance(signer_trust_domain, str) or not re.fullmatch(
        r"[a-z0-9][a-z0-9._-]{2,127}", signer_trust_domain
    ):
        _fail("invalid_trust_domain", "signer.trustDomain", "stable lowercase domain required")
    issued_text = _format_utc_timestamp(issued_at)
    expires_text = _format_utc_timestamp(expires_at)
    issued = _parse_utc_timestamp(issued_text, "issuedAt")
    expires = _parse_utc_timestamp(expires_text, "expiresAt")
    validity = expires - issued
    if validity <= timedelta(0) or validity > MAXIMUM_VALIDITY:
        _fail("invalid_validity_window", "expiresAt", "window must be greater than 0 and at most 24h")

    evidence_digest = sha256_canonical(normalized)
    fingerprint = signer_fingerprint(signing_key)
    receiver_keyring_fingerprint = normalized["invalidationBindings"][
        "keyringFingerprintSha256"
    ]
    if hmac.compare_digest(fingerprint, receiver_keyring_fingerprint):
        _fail(
            "signer_not_independent",
            "signer.fingerprintSha256",
            "authority signer must differ from the receiver keyring",
        )
    receipt_id = sha256_canonical(
        {
            "evidenceDigestSha256": evidence_digest,
            "expiresAt": expires_text,
            "issuedAt": issued_text,
            "schemaVersion": SCHEMA_VERSION,
            "signerFingerprintSha256": fingerprint,
            "signerKeyId": key_id,
        }
    )
    receipt: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "receiptId": receipt_id,
        "issuedAt": issued_text,
        "expiresAt": expires_text,
        **normalized,
        "signer": {
            "algorithm": SIGNATURE_ALGORITHM,
            "fingerprintSha256": fingerprint,
            "independentOfReceiver": True,
            "keyId": key_id,
            "role": "independent_release_authority",
            "trustDomain": signer_trust_domain,
        },
        "evidenceDigestSha256": evidence_digest,
    }
    signature = private_key.sign(_authority_signing_bytes(receipt))
    receipt["signature"] = "v2=" + signature.hex()
    return json.loads(canonical_json(receipt))


def verify_receipt(
    receipt: Mapping[str, Any],
    *,
    public_key: bytes,
    expected_signer_fingerprint: str,
    expected_signer_key_id: str,
    now: datetime,
) -> dict[str, Any]:
    """Fail closed unless every binding, invariant, time and signature is valid."""

    root = _mapping(receipt, "receipt")
    _exact_keys(root, TOP_LEVEL_KEYS, "receipt")
    _expect_literal(root["schemaVersion"], SCHEMA_VERSION, "schemaVersion")
    receipt_id = _hex64(root["receiptId"], "receiptId")
    evidence_digest = _hex64(root["evidenceDigestSha256"], "evidenceDigestSha256")
    key = _load_public_key(public_key)
    expected_fingerprint = _hex64(expected_signer_fingerprint, "expectedSignerFingerprint")
    expected_key_id = _validate_key_id(expected_signer_key_id, "expectedSignerKeyId")
    actual_key_fingerprint = public_key_fingerprint(public_key)
    if not hmac.compare_digest(expected_fingerprint, actual_key_fingerprint):
        _fail("signer_key_fingerprint_mismatch", "expectedSignerFingerprint", "key differs")

    signer = _mapping(root["signer"], "signer")
    _exact_keys(signer, SIGNER_KEYS, "signer")
    _expect_literal(signer["algorithm"], SIGNATURE_ALGORITHM, "signer.algorithm")
    claimed_fingerprint = _hex64(signer["fingerprintSha256"], "signer.fingerprintSha256")
    if not hmac.compare_digest(claimed_fingerprint, expected_fingerprint):
        _fail("signer_fingerprint_mismatch", "signer.fingerprintSha256", "unexpected signer")
    _expect_bool(signer["independentOfReceiver"], True, "signer.independentOfReceiver")
    _expect_literal(signer["keyId"], expected_key_id, "signer.keyId")
    _expect_literal(
        signer["role"], "independent_release_authority", "signer.role"
    )
    if not isinstance(signer["trustDomain"], str) or not re.fullmatch(
        r"[a-z0-9][a-z0-9._-]{2,127}", signer["trustDomain"]
    ):
        _fail("invalid_trust_domain", "signer.trustDomain", "stable lowercase domain required")

    issued = _parse_utc_timestamp(root["issuedAt"], "issuedAt")
    expires = _parse_utc_timestamp(root["expiresAt"], "expiresAt")
    validity = expires - issued
    if validity <= timedelta(0) or validity > MAXIMUM_VALIDITY:
        _fail("invalid_validity_window", "expiresAt", "window must be greater than 0 and at most 24h")
    if not isinstance(now, datetime) or now.tzinfo is None:
        _fail("invalid_timestamp", "now", "timezone-aware datetime required")
    current = now.astimezone(timezone.utc)
    if current < issued:
        _fail("receipt_not_yet_valid", "issuedAt", "verification time precedes issuance")
    if current >= expires:
        _fail("receipt_expired", "expiresAt", "verification time is outside the half-open window")

    normalized = _validate_evidence(_evidence_from_receipt(root))
    if hmac.compare_digest(
        claimed_fingerprint,
        normalized["invalidationBindings"]["keyringFingerprintSha256"],
    ):
        _fail(
            "signer_not_independent",
            "signer.fingerprintSha256",
            "authority signer must differ from the receiver keyring",
        )
    recomputed_evidence = sha256_canonical(normalized)
    if not hmac.compare_digest(recomputed_evidence, evidence_digest):
        _fail("evidence_digest_mismatch", "evidenceDigestSha256", "evidence was changed")
    recomputed_id = sha256_canonical(
        {
            "evidenceDigestSha256": evidence_digest,
            "expiresAt": root["expiresAt"],
            "issuedAt": root["issuedAt"],
            "schemaVersion": SCHEMA_VERSION,
            "signerFingerprintSha256": claimed_fingerprint,
            "signerKeyId": signer["keyId"],
        }
    )
    if not hmac.compare_digest(recomputed_id, receipt_id):
        _fail("receipt_id_mismatch", "receiptId", "identity binding was changed")

    supplied = root["signature"]
    if not isinstance(supplied, str) or re.fullmatch(
        rf"v2=[0-9a-f]{{{ED25519_SIGNATURE_BYTES * 2}}}", supplied
    ) is None:
        _fail("invalid_signature", "signature", "v2 lowercase Ed25519 signature required")
    try:
        key.verify(bytes.fromhex(supplied[3:]), _authority_signing_bytes(root))
    except (InvalidSignature, ValueError):
        _fail("signature_mismatch", "signature", "receipt authentication failed")

    return {
        "status": "PASS",
        "schemaVersion": SCHEMA_VERSION,
        "receiptId": receipt_id,
        "evidenceDigestSha256": evidence_digest,
        "signerFingerprintSha256": claimed_fingerprint,
        "signerKeyId": signer["keyId"],
        "issuedAt": root["issuedAt"],
        "expiresAt": root["expiresAt"],
        "productCreditEligible": False,
        "productionContacted": False,
        "authorityRole": "independent_release_authority",
        "transportHmac": {
            "algorithm": TRANSPORT_HMAC_ALGORITHM,
            "domain": TRANSPORT_HMAC_DOMAIN,
            "role": "receiver_transport_integrity_only",
            "independentAuthority": False,
        },
    }


def _read_local_bytes(path_text: str, purpose: str) -> bytes:
    if "://" in path_text or path_text.startswith(("\\\\", "//")):
        _fail("nonlocal_input_forbidden", purpose, "URL and UNC inputs are forbidden")
    path = Path(path_text)
    try:
        if not path.is_file():
            _fail("local_input_missing", purpose, "regular local file required")
        size = path.stat().st_size
        if size > MAXIMUM_LOCAL_INPUT_BYTES:
            _fail("local_input_too_large", purpose, "input exceeds 16 MiB")
        return path.read_bytes()
    except OSError:
        _fail("local_input_unreadable", purpose, "could not read local file")


def _read_json(path_text: str, purpose: str) -> Mapping[str, Any]:
    raw = _read_local_bytes(path_text, purpose)
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        _fail("invalid_json", purpose, "strict UTF-8 JSON required")
    return _mapping(value, purpose)


def _write_json_atomic(path_text: str, value: Mapping[str, Any]) -> None:
    if "://" in path_text or path_text.startswith(("\\\\", "//")):
        _fail("nonlocal_output_forbidden", "output", "URL and UNC outputs are forbidden")
    target = Path(path_text)
    target.parent.mkdir(parents=True, exist_ok=True)
    payload = canonical_json(value) + b"\n"
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=target.name + ".", suffix=".tmp", dir=str(target.parent)
    )
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_name, target)
    except BaseException:
        try:
            os.unlink(temporary_name)
        except OSError:
            pass
        raise


def _argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Offline W05E authority receipt materializer/verifier; never contacts production."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    materialize = subparsers.add_parser("materialize")
    materialize.add_argument("--evidence", required=True, help="local UTF-8 evidence JSON")
    materialize.add_argument(
        "--private-key-file",
        required=True,
        help="local Ed25519 PKCS#8 private key; materialize only",
    )
    materialize.add_argument("--issued-at", required=True, help="canonical UTC timestamp")
    materialize.add_argument("--expires-at", required=True, help="canonical UTC timestamp")
    materialize.add_argument("--signer-key-id", required=True)
    materialize.add_argument("--signer-trust-domain", required=True)
    materialize.add_argument("--output", help="optional local receipt path; otherwise stdout")
    verify = subparsers.add_parser("verify")
    verify.add_argument("--receipt", required=True, help="local UTF-8 receipt JSON")
    verify.add_argument(
        "--public-key-file",
        required=True,
        help="local Ed25519 raw public key or SPKI public key; verify only",
    )
    verify.add_argument("--expected-signer-fingerprint", required=True)
    verify.add_argument("--expected-signer-key-id", required=True)
    verify.add_argument("--now", required=True, help="explicit canonical UTC cutoff")
    return parser


def main(argv: list[str] | None = None) -> int:
    try:
        args = _argument_parser().parse_args(argv)
        if args.command == "materialize":
            key = _read_local_bytes(args.private_key_file, "privateKeyFile")
            evidence = _read_json(args.evidence, "evidenceFile")
            receipt = materialize_receipt(
                evidence,
                issued_at=_parse_utc_timestamp(args.issued_at, "issuedAt"),
                expires_at=_parse_utc_timestamp(args.expires_at, "expiresAt"),
                signing_key=key,
                signer_key_id=args.signer_key_id,
                signer_trust_domain=args.signer_trust_domain,
            )
            if args.output:
                _write_json_atomic(args.output, receipt)
                result: Mapping[str, Any] = {
                    "status": "MATERIALIZED",
                    "receiptId": receipt["receiptId"],
                    "evidenceDigestSha256": receipt["evidenceDigestSha256"],
                    "signerFingerprintSha256": receipt["signer"]["fingerprintSha256"],
                    "output": str(Path(args.output).resolve()),
                    "productionContacted": False,
                }
            else:
                result = receipt
        else:
            key = _read_local_bytes(args.public_key_file, "publicKeyFile")
            result = verify_receipt(
                _read_json(args.receipt, "receiptFile"),
                public_key=key,
                expected_signer_fingerprint=args.expected_signer_fingerprint,
                expected_signer_key_id=args.expected_signer_key_id,
                now=_parse_utc_timestamp(args.now, "now"),
            )
        sys.stdout.buffer.write(canonical_json(result) + b"\n")
        return 0
    except AuthorityReceiptError as error:
        sys.stderr.buffer.write(canonical_json(error.as_dict()) + b"\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
