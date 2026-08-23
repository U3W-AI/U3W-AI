#!/usr/bin/env python3
"""Versioned W0.5 receiver release worker.

The worker never edits the API2 outbox. It applies the forward-only 044 schema,
materializes an expiring digest allowlist, atomically switches the application
release, and restores the previous application/configuration on failure while
retaining 044.
"""

from __future__ import annotations

import argparse
import contextlib
import datetime as dt
import hashlib
import json
import os
import pathlib
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import urllib.request

try:
    import fcntl
except ModuleNotFoundError:  # Windows contract tests only; apply remains Linux-only.
    fcntl = None


RELEASES = pathlib.Path("/opt/fbsir/admin/releases")
CURRENT = pathlib.Path("/opt/fbsir/admin/current")
STATE = pathlib.Path("/opt/fbsir/admin/state/w05")
AUTHORIZATIONS = STATE / "authorizations"
RECEIPTS = STATE / "receipts"
LOCK = pathlib.Path("/opt/fbsir/admin/.u3w-w05-production-change.lock")
SERVICE = "fbsir-admin.service"
DROPIN = pathlib.Path(
    "/etc/systemd/system/fbsir-admin.service.d/30-w05-replay.conf"
)
ENV_FILE = pathlib.Path("/etc/u3w/fbsir-admin.env")
OUTBOX = pathlib.Path(
    "/var/lib/fbss-phase1/independent-board-attribution-outbox.json"
)
EXPECTED_043_SHA = (
    "ca9c86c79617543c19bc9a6141afef18918320c65091b418f761ece19b5c6055"
)
HEX64 = re.compile(r"^[0-9a-f]{64}$")
RELEASE_ID = re.compile(r"^w05-receiver-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z$")
AUTH_ID = re.compile(r"^w05-[a-z0-9-]{8,96}$")
JDBC = re.compile(
    r"^jdbc:mysql://(?P<host>\[[^]]+\]|[^/:?]+)(?::(?P<port>[0-9]{1,5}))?"
    r"/(?P<database>[A-Za-z0-9_]{1,64})(?:\?.*)?$"
)


def now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def iso(value: dt.datetime | None = None) -> str:
    return (value or now()).isoformat().replace("+00:00", "Z")


def parse_instant(value: object) -> dt.datetime:
    text = str(value or "")
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    parsed = dt.datetime.fromisoformat(text)
    if parsed.tzinfo is None:
        raise ValueError("timestamp must be timezone-aware")
    return parsed.astimezone(dt.timezone.utc)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical_json(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")


def atomic_json(path: pathlib.Path, value: object, mode: int = 0o600) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    payload = canonical_json(value)
    descriptor, temporary = tempfile.mkstemp(
        prefix=f".{path.name}.", dir=str(path.parent)
    )
    try:
        os.chmod(temporary, mode)
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    except BaseException:
        with contextlib.suppress(FileNotFoundError):
            os.unlink(temporary)
        raise


def atomic_text(path: pathlib.Path, text: str, mode: int = 0o600) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    descriptor, temporary = tempfile.mkstemp(
        prefix=f".{path.name}.", dir=str(path.parent)
    )
    try:
        os.chmod(temporary, mode)
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(text)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    except BaseException:
        with contextlib.suppress(FileNotFoundError):
            os.unlink(temporary)
        raise


def regular(path: pathlib.Path, modes: tuple[int, ...] = (0o600, 0o644)) -> None:
    status = path.lstat()
    if not stat.S_ISREG(status.st_mode) or status.st_uid != 0:
        raise RuntimeError(f"unsafe regular file: {path}")
    if stat.S_IMODE(status.st_mode) not in modes:
        raise RuntimeError(f"unsafe file mode for {path}")


def run(
    arguments: list[str],
    *,
    input_bytes: bytes | None = None,
    environment: dict[str, str] | None = None,
    timeout: int = 120,
    check: bool = True,
) -> subprocess.CompletedProcess[bytes]:
    completed = subprocess.run(
        arguments,
        input=input_bytes,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=environment,
        timeout=timeout,
        check=False,
    )
    if check and completed.returncode != 0:
        message = completed.stderr.decode("utf-8", errors="replace")[-4000:]
        raise RuntimeError(f"command failed ({arguments[0]}): {message}")
    return completed


def read_env(path: pathlib.Path) -> dict[str, str]:
    regular(path, (0o600, 0o640, 0o644))
    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        result[key] = value
    return result


class Mysql:
    def __init__(self, environment_path: pathlib.Path = ENV_FILE):
        values = read_env(environment_path)
        match = JDBC.fullmatch(values.get("FBSIR_MYSQL_URL", ""))
        if match is None:
            raise RuntimeError("production JDBC URL is invalid")
        self.host = match.group("host").strip("[]")
        self.port = int(match.group("port") or "3306")
        self.database = match.group("database")
        self.user = values.get("FBSIR_MYSQL_USERNAME", "")
        self.password = values.get("FBSIR_MYSQL_PASSWORD", "")
        if not self.user or not (1 <= self.port <= 65535):
            raise RuntimeError("production MySQL identity is incomplete")

    def command(self) -> list[str]:
        return [
            "/usr/bin/mysql",
            "--protocol=TCP",
            f"--host={self.host}",
            f"--port={self.port}",
            f"--user={self.user}",
            f"--database={self.database}",
            "--default-character-set=utf8mb4",
            "--batch",
            "--raw",
            "--skip-column-names",
        ]

    def environment(self) -> dict[str, str]:
        result = os.environ.copy()
        result["MYSQL_PWD"] = self.password
        return result

    def query(self, sql: str) -> str:
        completed = run(
            self.command() + ["--execute", sql],
            environment=self.environment(),
            timeout=120,
        )
        return completed.stdout.decode("utf-8").strip()

    def source(self, path: pathlib.Path) -> None:
        regular(path, (0o600, 0o640, 0o644))
        run(
            self.command(),
            input_bytes=path.read_bytes(),
            environment=self.environment(),
            timeout=300,
        )


def load_json(path: pathlib.Path) -> object:
    regular(path)
    return json.loads(path.read_text(encoding="utf-8"))


def validate_candidate(release: pathlib.Path) -> tuple[dict, str]:
    resolved = release.resolve(strict=True)
    if resolved.parent != RELEASES.resolve() or not RELEASE_ID.fullmatch(resolved.name):
        raise RuntimeError("candidate release path is outside the versioned release root")
    manifest_path = resolved / "w05-candidate-manifest.json"
    manifest = load_json(manifest_path)
    if not isinstance(manifest, dict) or manifest.get("schemaVersion") != (
        "fbsir.w05ReceiverCandidateManifest.v1"
    ):
        raise RuntimeError("candidate manifest schema is invalid")
    if manifest.get("releaseId") != resolved.name:
        raise RuntimeError("candidate release id mismatch")
    if manifest.get("expensiveGatesSkipped") is not False:
        raise RuntimeError("candidate was built with skipped release gates")
    files = manifest.get("files")
    if not isinstance(files, dict) or not files:
        raise RuntimeError("candidate file manifest is empty")
    required = {
        "backend/fbsir-admin.jar",
        "bin/u3w-w05-receiver-release.py",
        "bin/u3w-w05-receiver-shadow.py",
        "sql/public_init_043.sql",
        "sql/public_init_044.sql",
        "evidence/source-manifest.json",
        "shadow/sql/init-manifest.json",
    }
    if not required.issubset(files):
        raise RuntimeError("candidate required files are missing")
    for relative, expected in files.items():
        if (
            not isinstance(relative, str)
            or relative.startswith("/")
            or ".." in pathlib.PurePosixPath(relative).parts
            or not isinstance(expected, dict)
        ):
            raise RuntimeError("candidate file manifest entry is invalid")
        path = resolved / pathlib.PurePosixPath(relative)
        regular(path)
        if (
            expected.get("sha256") != sha256_file(path)
            or expected.get("sizeBytes") != path.stat().st_size
        ):
            raise RuntimeError(f"candidate file drifted: {relative}")
    if manifest.get("migration043Sha256") != EXPECTED_043_SHA:
        raise RuntimeError("candidate 043 predecessor binding drifted")
    manifest_sha = sha256_file(manifest_path)
    return manifest, manifest_sha


def current_target() -> pathlib.Path:
    if not CURRENT.is_symlink():
        raise RuntimeError("production current is not a symlink")
    target = CURRENT.resolve(strict=True)
    if target.parent != RELEASES.resolve():
        raise RuntimeError("production current target is outside releases")
    return target


def service_snapshot() -> dict[str, object]:
    output = run(
        [
            "/usr/bin/systemctl",
            "show",
            SERVICE,
            "-p",
            "MainPID",
            "-p",
            "NRestarts",
            "-p",
            "ActiveState",
            "-p",
            "SubState",
            "-p",
            "ExecMainStatus",
        ]
    ).stdout.decode("utf-8")
    values: dict[str, object] = {}
    for line in output.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = int(value) if value.isdigit() else value
    return values


def pending_items() -> tuple[list[dict], str]:
    regular(OUTBOX)
    value = json.loads(OUTBOX.read_text(encoding="utf-8"))
    items = value.get("items") if isinstance(value, dict) else None
    if not isinstance(items, list):
        raise RuntimeError("API2 outbox items are missing")
    pending = [item for item in items if isinstance(item, dict) and item.get("status") == "PENDING"]
    digests = sorted(str(item.get("eventDigest", "")) for item in pending)
    if any(HEX64.fullmatch(item) is None for item in digests):
        raise RuntimeError("pending event digest is invalid")
    if len(digests) != len(set(digests)):
        raise RuntimeError("pending event digests are not distinct")
    set_sha = sha256_bytes((("\n".join(digests) + "\n") if digests else "").encode())
    return pending, set_sha


def outbox_identity_values(pending: list[dict], field: str) -> list[str]:
    values: list[str] = []
    for item in pending:
        if field == "event_digest":
            value = item.get("eventDigest")
        else:
            candidate = item.get("candidate")
            value = candidate.get(field) if isinstance(candidate, dict) else None
        value = str(value or "")
        if HEX64.fullmatch(value) is None:
            raise RuntimeError(f"pending {field} is invalid")
        values.append(value)
    return values


def sql_in(values: list[str]) -> str:
    if not values:
        return "('')"
    if any(HEX64.fullmatch(value) is None for value in values):
        raise RuntimeError("unsafe SQL digest value")
    return "(" + ",".join(f"'{value}'" for value in values) + ")"


def database_pending_matches(mysql: Mysql, pending: list[dict]) -> dict[str, int]:
    mapping = {
        "event_id": outbox_identity_values(pending, "eventId"),
        "receipt_id": outbox_identity_values(pending, "receiptId"),
        "event_digest": outbox_identity_values(pending, "event_digest"),
        "journey_id": outbox_identity_values(pending, "journeyId"),
    }
    result: dict[str, int] = {}
    for column, values in mapping.items():
        table = "fbs_board_attr_event_v1" if column != "journey_id" else "fbs_board_attr_journey_v1"
        result[column] = int(
            mysql.query(f"SELECT COUNT(*) FROM {table} WHERE {column} IN {sql_in(values)};")
        )
    return result


def database_state(mysql: Mysql) -> dict[str, object]:
    receipts = mysql.query(
        "SELECT CONCAT(version,'|',description) FROM u3w_schema_migration "
        "WHERE version IN ('public_init_043','20260723_independent_board_attribution_v1_043',"
        "'public_init_044','20260823_independent_board_attribution_identity_registry_044') "
        "ORDER BY version;"
    ).splitlines()
    checks = mysql.query(
        "SELECT CONCAT(tc.table_name,'|',tc.enforced,'|',cc.check_clause) "
        "FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc "
        "ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name "
        "WHERE tc.constraint_schema=DATABASE() AND tc.constraint_name IN "
        "('chk_board_attr_journey_versions','chk_board_attr_event_versions') "
        "ORDER BY tc.table_name;"
    ).splitlines()
    index_columns = mysql.query(
        "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') "
        "FROM information_schema.statistics WHERE table_schema=DATABASE() "
        "AND table_name='fbs_board_attr_journey_v1' "
        "AND index_name='uk_board_attr_journey_identity' AND non_unique=0;"
    )
    return {
        "server": mysql.query("SELECT CONCAT_WS('|',VERSION(),@@version_comment);"),
        "receipts": receipts,
        "checksSha256": sha256_bytes(("\n".join(checks) + "\n").encode()),
        "checks": checks,
        "journeyIdentityColumns": index_columns,
    }


def state_is_043(state: dict[str, object]) -> bool:
    receipts = state["receipts"]
    checks = state["checks"]
    return bool(
        any(str(line).startswith("public_init_043|APPLIED:") for line in receipts)
        and any(str(line).startswith("20260723_independent_board_attribution_v1_043|APPLIED:") for line in receipts)
        and not any(str(line).startswith("public_init_044|") for line in receipts)
        and len(checks) == 2
        and all("|YES|" in str(line) and "26.7.21" in str(line) and "26.8.19" not in str(line) for line in checks)
        and state["journeyIdentityColumns"]
        == "contract_id,tenant_subject_digest,server_binding_id,journey_id"
    )


def state_is_044(state: dict[str, object]) -> bool:
    receipts = state["receipts"]
    checks = state["checks"]
    return bool(
        any(str(line).startswith("public_init_044|APPLIED:") for line in receipts)
        and any(str(line).startswith("20260823_independent_board_attribution_identity_registry_044|APPLIED:") for line in receipts)
        and len(checks) == 2
        and all("|YES|" in str(line) and "26.7.21" in str(line) and "26.8.19" in str(line) for line in checks)
        and any("WORKBUDDYAI" in str(line) for line in checks)
        and state["journeyIdentityColumns"]
        == (
            "contract_id,tenant_subject_digest,server_binding_id,journey_id,"
            "product_id,listed_manifest_version,embedded_contract_version"
        )
    )


def validate_authorization(
    path: pathlib.Path, action: str, manifest_sha: str
) -> tuple[dict, pathlib.Path]:
    resolved = path.resolve(strict=True)
    if resolved.parent != AUTHORIZATIONS.resolve() or not resolved.name.endswith(".json"):
        raise RuntimeError("authorization is outside the authorization ledger")
    value = load_json(resolved)
    if not isinstance(value, dict) or value.get("schemaVersion") != "fbsir.w05Authorization.v1":
        raise RuntimeError("authorization schema is invalid")
    authorization_id = value.get("authorizationId")
    if not isinstance(authorization_id, str) or not AUTH_ID.fullmatch(authorization_id):
        raise RuntimeError("authorization id is invalid")
    if value.get("action") != action or value.get("candidateManifestSha256") != manifest_sha:
        raise RuntimeError("authorization target binding mismatch")
    observed = now()
    if not (parse_instant(value.get("notBefore")) <= observed <= parse_instant(value.get("notAfter"))):
        raise RuntimeError("authorization is outside its validity window")
    used = resolved.with_name(resolved.stem + ".used.json")
    if used.exists():
        raise RuntimeError("authorization was already consumed")
    return value, used


def consume_authorization(path: pathlib.Path, used: pathlib.Path) -> None:
    os.replace(path, used)
    directory = os.open(used.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def verify_preimage(
    authorization: dict,
    manifest_sha: str,
    mysql: Mysql,
) -> tuple[pathlib.Path, list[dict], str, dict[str, object]]:
    target = current_target()
    jar = target / "backend/fbsir-admin.jar"
    regular(jar)
    if (
        authorization.get("candidateManifestSha256") != manifest_sha
        or authorization.get("expectedCurrentTarget") != str(target)
        or authorization.get("expectedCurrentJarSha256") != sha256_file(jar)
    ):
        raise RuntimeError("production application preimage drifted")
    pending, set_sha = pending_items()
    if (
        authorization.get("expectedPendingCount") != len(pending)
        or authorization.get("expectedPendingDigestSetSha256") != set_sha
    ):
        raise RuntimeError("pending digest set drifted")
    matches = database_pending_matches(mysql, pending)
    if any(matches.values()):
        raise RuntimeError("one or more pending identities already exist in the receiver DB")
    state = database_state(mysql)
    if not (state_is_043(state) or state_is_044(state)):
        raise RuntimeError("production attribution schema preimage is neither exact 043 nor 044")
    return target, pending, set_sha, state


def apply_044(mysql: Mysql, release: pathlib.Path) -> dict[str, object]:
    state = database_state(mysql)
    if state_is_044(state):
        return state
    if not state_is_043(state) and not any(
        line.startswith("public_init_044|RUNNING:") for line in state["receipts"]
    ):
        raise RuntimeError("044 cannot start from the observed schema state")
    public_state = mysql.query(
        "SELECT COALESCE((SELECT description FROM u3w_schema_migration "
        "WHERE version='public_init_044'),'');"
    )
    if not public_state:
        mysql.query(
            "INSERT INTO u3w_schema_migration(version,description) VALUES "
            "('public_init_044','RUNNING:Independent Board exact legacy and current attribution identity registry');"
        )
    elif public_state != (
        "RUNNING:Independent Board exact legacy and current attribution identity registry"
    ):
        raise RuntimeError("public_init_044 receipt is not recoverable")
    mysql.source(release / "sql/public_init_044.sql")
    running = database_state(mysql)
    if not (
        any(
            line.startswith(
                "20260823_independent_board_attribution_identity_registry_044|APPLIED:"
            )
            for line in running["receipts"]
        )
        and len(running["checks"]) == 2
        and all("26.8.19" in line for line in running["checks"])
    ):
        raise RuntimeError("044 internal successor did not complete")
    mysql.query(
        "UPDATE u3w_schema_migration SET description="
        "'APPLIED:Independent Board exact legacy and current attribution identity registry',"
        "applied_at=CURRENT_TIMESTAMP WHERE version='public_init_044' AND description="
        "'RUNNING:Independent Board exact legacy and current attribution identity registry';"
    )
    final = database_state(mysql)
    if not state_is_044(final):
        raise RuntimeError("044 final current-read failed")
    return final


def materialize_replay_environment(
    release: pathlib.Path,
    pending: list[dict],
    authorization: dict,
) -> tuple[pathlib.Path, str, str]:
    not_after = parse_instant(authorization.get("replayNotAfter"))
    observed = now()
    if not (observed + dt.timedelta(minutes=5) <= not_after <= observed + dt.timedelta(hours=1)):
        raise RuntimeError("replay notAfter must be 5-60 minutes in the future")
    digests = sorted(str(item["eventDigest"]) for item in pending)
    set_sha = sha256_bytes(("\n".join(digests) + "\n").encode())
    environment_path = release / "configuration/w05-replay.env"
    lines = [
        "FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_ENABLED=true",
        "FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_MAX_AGE_HOURS=168",
        f"FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_NOT_AFTER={iso(not_after)}",
        "FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_EVENT_DIGESTS="
        + ",".join(digests),
    ]
    atomic_text(environment_path, "\n".join(lines) + "\n", 0o600)
    return environment_path, sha256_file(environment_path), set_sha


def switch_current(target: pathlib.Path) -> None:
    temporary = CURRENT.with_name(".current.w05.tmp")
    with contextlib.suppress(FileNotFoundError):
        temporary.unlink()
    os.symlink(target, temporary)
    os.replace(temporary, CURRENT)


def install_dropin(environment_path: pathlib.Path) -> str:
    text = "[Service]\nEnvironmentFile=" + str(environment_path) + "\n"
    atomic_text(DROPIN, text, 0o644)
    return sha256_file(DROPIN)


def restart_service() -> dict[str, object]:
    run(["/usr/bin/systemctl", "daemon-reload"], timeout=60)
    run(["/usr/bin/systemctl", "restart", SERVICE], timeout=120)
    deadline = time.time() + 120
    while time.time() < deadline:
        snapshot = service_snapshot()
        if snapshot.get("ActiveState") == "active" and snapshot.get("SubState") == "running":
            return snapshot
        time.sleep(2)
    raise RuntimeError("receiver service did not become active")


def api2_publisher_health() -> dict:
    with urllib.request.urlopen(
        "http://127.0.0.1:4173/api/fbss/health", timeout=10
    ) as response:
        value = json.load(response)
    publisher = value.get("independentBoardAttributionPublisher")
    if not isinstance(publisher, dict):
        raise RuntimeError("API2 publisher health is missing")
    return publisher


def wait_for_drain(timeout_seconds: int = 240) -> dict:
    deadline = time.time() + timeout_seconds
    latest: dict = {}
    while time.time() < deadline:
        latest = api2_publisher_health()
        if (
            latest.get("pendingRecords") == 0
            and latest.get("deadRecords") == 0
            and latest.get("status") == "ready"
            and latest.get("productCreditPromoted") is False
        ):
            return latest
        if int(latest.get("deadRecords", 0)) > 0:
            raise RuntimeError("publisher moved one or more records to dead")
        time.sleep(5)
    raise RuntimeError("publisher did not drain within the bounded window")


def verify_inserted_rows(mysql: Mysql, pending: list[dict]) -> dict[str, int]:
    digests = outbox_identity_values(pending, "event_digest")
    row = mysql.query(
        "SELECT CONCAT_WS('|',COUNT(*),"
        "SUM(traffic_class='SYNTHETIC'),SUM(authoritative_product_credit=0)) "
        "FROM fbs_board_attr_event_v1 WHERE event_digest IN " + sql_in(digests) + ";"
    )
    parts = [int(item or "0") for item in row.split("|")]
    if parts != [len(digests), len(digests), len(digests)]:
        raise RuntimeError("drained receiver rows are incomplete or credit-bearing")
    return {"rows": parts[0], "syntheticRows": parts[1], "zeroCreditRows": parts[2]}


def restore_application(
    previous: pathlib.Path,
    previous_dropin: bytes | None,
) -> dict[str, object]:
    switch_current(previous)
    if previous_dropin is None:
        with contextlib.suppress(FileNotFoundError):
            DROPIN.unlink()
    else:
        atomic_text(DROPIN, previous_dropin.decode("utf-8"), 0o644)
    return restart_service()


def inspect(args: argparse.Namespace) -> dict:
    release = pathlib.Path(args.release)
    manifest, manifest_sha = validate_candidate(release)
    return {
        "schemaVersion": "fbsir.w05Inspection.v1",
        "observedAt": iso(),
        "releaseId": manifest["releaseId"],
        "manifestSha256": manifest_sha,
        "fileCount": len(manifest["files"]),
        "productionChanged": False,
        "status": "PASS",
    }


def preflight(args: argparse.Namespace) -> dict:
    release = pathlib.Path(args.release).resolve(strict=True)
    manifest, manifest_sha = validate_candidate(release)
    authorization, _ = validate_authorization(
        pathlib.Path(args.authorization), "apply", manifest_sha
    )
    mysql = Mysql()
    target, pending, set_sha, database = verify_preimage(
        authorization, manifest_sha, mysql
    )
    return {
        "schemaVersion": "fbsir.w05PreflightReceipt.v1",
        "observedAt": iso(),
        "releaseId": manifest["releaseId"],
        "manifestSha256": manifest_sha,
        "currentTarget": str(target),
        "pendingCount": len(pending),
        "pendingDigestSetSha256": set_sha,
        "databaseState": {
            "server": database["server"],
            "checksSha256": database["checksSha256"],
            "journeyIdentityColumns": database["journeyIdentityColumns"],
            "state": "044" if state_is_044(database) else "043",
        },
        "databasePendingMatches": database_pending_matches(mysql, pending),
        "productionChanged": False,
        "status": "PASS",
    }


def apply(args: argparse.Namespace) -> dict:
    if fcntl is None:
        raise RuntimeError("production apply requires Linux fcntl locking")
    release = pathlib.Path(args.release).resolve(strict=True)
    manifest, manifest_sha = validate_candidate(release)
    authorization_path = pathlib.Path(args.authorization)
    authorization, used = validate_authorization(
        authorization_path, "apply", manifest_sha
    )
    STATE.mkdir(parents=True, exist_ok=True, mode=0o700)
    AUTHORIZATIONS.mkdir(parents=True, exist_ok=True, mode=0o700)
    RECEIPTS.mkdir(parents=True, exist_ok=True, mode=0o700)
    lock_descriptor = os.open(LOCK, os.O_CREAT | os.O_RDWR, 0o600)
    previous: pathlib.Path | None = None
    previous_dropin: bytes | None = None
    switched = False
    started = iso()
    try:
        fcntl.flock(lock_descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        mysql = Mysql()
        previous, pending, set_sha, before_database = verify_preimage(
            authorization, manifest_sha, mysql
        )
        previous_dropin = DROPIN.read_bytes() if DROPIN.exists() else None
        consume_authorization(authorization_path, used)
        rollback_directory = release / "rollback"
        rollback_directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        previous_dropin_path = rollback_directory / "30-w05-replay.conf"
        if previous_dropin is not None:
            atomic_text(
                previous_dropin_path,
                previous_dropin.decode("utf-8"),
                0o600,
            )
        after_database = apply_044(mysql, release)
        replay_env, replay_env_sha, replay_set_sha = materialize_replay_environment(
            release, pending, authorization
        )
        if replay_set_sha != set_sha:
            raise RuntimeError("replay environment digest set drifted")
        dropin_sha = install_dropin(replay_env)
        switch_current(release)
        switched = True
        service = restart_service()
        if current_target() != release:
            raise RuntimeError("candidate current symlink did not activate")
        if sha256_file(release / "backend/fbsir-admin.jar") != manifest["files"][
            "backend/fbsir-admin.jar"
        ]["sha256"]:
            raise RuntimeError("active candidate JAR drifted")
        publisher = wait_for_drain()
        inserted = verify_inserted_rows(mysql, pending)
        receipt = {
            "schemaVersion": "fbsir.w05ApplyReceipt.v1",
            "startedAt": started,
            "completedAt": iso(),
            "releaseId": manifest["releaseId"],
            "manifestSha256": manifest_sha,
            "authorizationId": authorization["authorizationId"],
            "authorizationLedger": str(used),
            "previousTarget": str(previous),
            "newTarget": str(release),
            "previousDropin": {
                "existed": previous_dropin is not None,
                "backupPath": (
                    str(previous_dropin_path)
                    if previous_dropin is not None
                    else None
                ),
                "sha256": (
                    sha256_bytes(previous_dropin)
                    if previous_dropin is not None
                    else None
                ),
            },
            "schemaBefore": "044" if state_is_044(before_database) else "043",
            "schemaAfter": "044" if state_is_044(after_database) else "invalid",
            "pendingCountBefore": len(pending),
            "pendingDigestSetSha256": set_sha,
            "replayConfiguration": {
                "environmentSha256": replay_env_sha,
                "dropinSha256": dropin_sha,
                "notAfter": authorization["replayNotAfter"],
                "maximumAgeHours": 168,
                "allowlistCount": len(pending),
                "allowlistSetSha256": set_sha,
            },
            "service": service,
            "publisher": {
                "status": publisher.get("status"),
                "pendingRecords": publisher.get("pendingRecords"),
                "deadRecords": publisher.get("deadRecords"),
                "productCreditPromoted": publisher.get("productCreditPromoted"),
            },
            "inserted": inserted,
            "schemaRollback": "retain_044_restore_previous_application_and_dropin",
            "applicationRollbackPerformed": False,
            "productCreditPromoted": False,
            "status": "PASS",
        }
        receipt_path = RECEIPTS / f"{manifest['releaseId']}.apply.json"
        atomic_json(receipt_path, receipt)
        receipt["receiptPath"] = str(receipt_path)
        receipt["receiptSha256"] = sha256_file(receipt_path)
        return receipt
    except BaseException as error:
        rollback = None
        if switched and previous is not None:
            with contextlib.suppress(BaseException):
                rollback = restore_application(previous, previous_dropin)
        failure = {
            "schemaVersion": "fbsir.w05ApplyFailure.v1",
            "failedAt": iso(),
            "releaseId": release.name,
            "manifestSha256": manifest_sha,
            "authorizationId": authorization.get("authorizationId"),
            "errorType": type(error).__name__,
            "error": str(error)[:1000],
            "applicationRollbackPerformed": rollback is not None,
            "rollbackService": rollback,
            "schemaRollback": "not_attempted_044_is_forward_only",
            "status": "FAIL",
        }
        with contextlib.suppress(BaseException):
            atomic_json(RECEIPTS / f"{release.name}.apply-failure.json", failure)
        raise
    finally:
        with contextlib.suppress(OSError):
            fcntl.flock(lock_descriptor, fcntl.LOCK_UN)
        os.close(lock_descriptor)


def rollback(args: argparse.Namespace) -> dict:
    if fcntl is None:
        raise RuntimeError("production rollback requires Linux fcntl locking")
    release = pathlib.Path(args.release).resolve(strict=True)
    manifest, manifest_sha = validate_candidate(release)
    authorization_path = pathlib.Path(args.authorization)
    authorization, used = validate_authorization(
        authorization_path, "rollback", manifest_sha
    )
    apply_receipt_path = RECEIPTS / f"{release.name}.apply.json"
    apply_receipt = load_json(apply_receipt_path)
    if (
        not isinstance(apply_receipt, dict)
        or apply_receipt.get("schemaVersion") != "fbsir.w05ApplyReceipt.v1"
        or apply_receipt.get("manifestSha256") != manifest_sha
        or apply_receipt.get("newTarget") != str(release)
    ):
        raise RuntimeError("apply receipt is missing or not bound to the candidate")
    previous = pathlib.Path(str(apply_receipt.get("previousTarget", ""))).resolve(
        strict=True
    )
    if previous.parent != RELEASES.resolve() or current_target() != release:
        raise RuntimeError("rollback application preimage drifted")
    if authorization.get("expectedCurrentTarget") != str(release):
        raise RuntimeError("rollback authorization current target mismatch")
    dropin_meta = apply_receipt.get("previousDropin")
    if not isinstance(dropin_meta, dict):
        raise RuntimeError("rollback drop-in metadata is missing")
    previous_dropin = None
    if dropin_meta.get("existed") is True:
        backup = pathlib.Path(str(dropin_meta.get("backupPath", "")))
        regular(backup, (0o600,))
        previous_dropin = backup.read_bytes()
        if sha256_bytes(previous_dropin) != dropin_meta.get("sha256"):
            raise RuntimeError("rollback drop-in backup drifted")
    mysql = Mysql()
    if not state_is_044(database_state(mysql)):
        raise RuntimeError("rollback requires retained exact 044 schema")
    lock_descriptor = os.open(LOCK, os.O_CREAT | os.O_RDWR, 0o600)
    try:
        fcntl.flock(lock_descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if current_target() != release:
            raise RuntimeError("candidate stopped being active before rollback")
        consume_authorization(authorization_path, used)
        service = restore_application(previous, previous_dropin)
        if current_target() != previous:
            raise RuntimeError("previous application did not become current")
        receipt = {
            "schemaVersion": "fbsir.w05RollbackReceipt.v1",
            "completedAt": iso(),
            "releaseId": manifest["releaseId"],
            "manifestSha256": manifest_sha,
            "authorizationId": authorization["authorizationId"],
            "authorizationLedger": str(used),
            "rolledBackFrom": str(release),
            "restoredTarget": str(previous),
            "restoredDropin": dropin_meta,
            "schemaState": "044_retained",
            "outboxModified": False,
            "service": service,
            "productCreditPromoted": False,
            "status": "PASS",
        }
        receipt_path = RECEIPTS / f"{release.name}.rollback.json"
        atomic_json(receipt_path, receipt)
        receipt["receiptPath"] = str(receipt_path)
        receipt["receiptSha256"] = sha256_file(receipt_path)
        return receipt
    finally:
        with contextlib.suppress(OSError):
            fcntl.flock(lock_descriptor, fcntl.LOCK_UN)
        os.close(lock_descriptor)


def verify(args: argparse.Namespace) -> dict:
    release = pathlib.Path(args.release).resolve(strict=True)
    manifest, manifest_sha = validate_candidate(release)
    mysql = Mysql()
    target = current_target()
    pending, set_sha = pending_items()
    database = database_state(mysql)
    publisher = api2_publisher_health()
    result = {
        "schemaVersion": "fbsir.w05PostVerifyReceipt.v1",
        "observedAt": iso(),
        "releaseId": manifest["releaseId"],
        "manifestSha256": manifest_sha,
        "activeTarget": str(target),
        "activeCandidate": target == release,
        "activeJarSha256": sha256_file(target / "backend/fbsir-admin.jar"),
        "schemaState": "044" if state_is_044(database) else "invalid",
        "pendingCount": len(pending),
        "pendingDigestSetSha256": set_sha,
        "publisher": publisher,
        "service": service_snapshot(),
        "productCreditPromoted": False,
    }
    result["status"] = "PASS" if (
        result["activeCandidate"]
        and result["schemaState"] == "044"
        and len(pending) == 0
        and publisher.get("status") == "ready"
        and publisher.get("deadRecords") == 0
        and publisher.get("productCreditPromoted") is False
    ) else "FAIL"
    if result["status"] != "PASS":
        raise RuntimeError("W0.5 post verification is not closed")
    return result


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    sub = root.add_subparsers(dest="command", required=True)
    for name in ("inspect", "verify"):
        item = sub.add_parser(name)
        item.add_argument("--release", required=True)
    for name in ("preflight", "apply", "rollback"):
        item = sub.add_parser(name)
        item.add_argument("--release", required=True)
        item.add_argument("--authorization", required=True)
    return root


def main() -> int:
    args = parser().parse_args()
    handlers = {
        "inspect": inspect,
        "preflight": preflight,
        "apply": apply,
        "rollback": rollback,
        "verify": verify,
    }
    try:
        value = handlers[args.command](args)
        print(json.dumps(value, ensure_ascii=False, sort_keys=True))
        return 0
    except BaseException as error:
        print(
            json.dumps(
                {
                    "status": "FAIL",
                    "errorType": type(error).__name__,
                    "error": str(error)[:1000],
                },
                ensure_ascii=False,
                sort_keys=True,
            ),
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
