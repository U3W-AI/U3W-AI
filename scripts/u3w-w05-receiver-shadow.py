#!/usr/bin/env python3
"""Production-equivalent loopback shadow for the W0.5 receiver candidate."""

from __future__ import annotations

import argparse
import contextlib
import datetime as dt
import hashlib
import hmac
import importlib.util
import json
import os
import pathlib
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request


HERE = pathlib.Path(__file__).resolve().parent
WORKER_PATH = HERE / "u3w-w05-receiver-release.py"
SPEC = importlib.util.spec_from_file_location("u3w_w05_worker", WORKER_PATH)
WORKER = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(WORKER)

SHADOW_ROOT = pathlib.Path("/var/tmp/fbsir-admin-w05-shadow")
MYSQL_PORT = 13306
APP_PORT = 14175
REDIS_PORT = 16379
EVENT_KEY_ID = "w05-shadow-k1"
EVENT_SECRET = b"shadow-event-secret-material-at-least-32-bytes"
BINDING_SECRET = "shadow-binding-secret-material-at-least-32-bytes"
SIGNED_FIELDS = (
    "agentName", "channel", "classificationSource", "classifierVersion",
    "confidenceBucket", "contractId", "embeddedContractVersion", "eventId",
    "eventType", "expiresAt", "hostClientFamily", "hostVersion",
    "intentSignal", "issuedAt", "journeyId", "keyId",
    "listedManifestVersion", "listedSurface", "marketplace", "nonce",
    "occurredAt", "outcome", "packageId", "previousEventDigest", "productId",
    "rawContentStored", "receiptId", "requestSource", "reviewMode",
    "sameBindingKey", "schemaVersion", "sequenceNo", "serverBindingId",
    "signatureAlgorithm", "tenantSubjectDigest", "terminal", "traceparent",
    "trafficAuthority", "trafficClass",
)


def utcnow() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def instant(value: dt.datetime) -> str:
    return value.isoformat(timespec="milliseconds").replace("+00:00", "Z")


def port_free(port: int) -> bool:
    with socket.socket() as probe:
        return probe.connect_ex(("127.0.0.1", port)) != 0


def wait_port(port: int, process: subprocess.Popen, timeout: int = 120) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"process exited before port {port} opened")
        if not port_free(port):
            return
        time.sleep(0.5)
    raise RuntimeError(f"port {port} did not open")


def run(arguments: list[str], *, env=None, input_bytes=None, timeout=300):
    result = subprocess.run(
        arguments,
        input=input_bytes,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
        timeout=timeout,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"command failed {arguments[0]}: "
            + result.stderr.decode("utf-8", errors="replace")[-4000:]
        )
    return result


class ShadowMysql:
    def __init__(self, root: pathlib.Path):
        self.root = root
        self.data = root / "mysql-data"
        self.socket = root / "mysql.sock"
        self.pid = root / "mysql.pid"
        self.log = root / "mysql.log"
        self.process: subprocess.Popen | None = None
        self.mysql = shutil.which("mysql") or "/usr/bin/mysql"
        self.mysqladmin = shutil.which("mysqladmin") or "/usr/bin/mysqladmin"
        self.mysqld = shutil.which("mysqld") or "/usr/sbin/mysqld"

    def start(self):
        self.data.mkdir(parents=True, mode=0o700)
        shutil.chown(self.root, user="mysql", group="mysql")
        shutil.chown(self.data, user="mysql", group="mysql")
        run([
            self.mysqld, "--no-defaults", "--initialize-insecure",
            "--user=mysql", f"--datadir={self.data}",
        ], timeout=180)
        self.process = subprocess.Popen(
            [
                "/usr/sbin/runuser", "-u", "mysql", "--", self.mysqld,
                "--no-defaults", f"--datadir={self.data}",
                "--bind-address=127.0.0.1", f"--port={MYSQL_PORT}",
                "--skip-networking=0", "--mysqlx=OFF",
                f"--socket={self.socket}", f"--pid-file={self.pid}",
                f"--log-error={self.log}", "--secure-file-priv=NULL",
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        wait_port(MYSQL_PORT, self.process, 120)

    def args(self, database: str | None = None) -> list[str]:
        result = [
            self.mysql, "--no-defaults", "--protocol=TCP",
            "--host=127.0.0.1", f"--port={MYSQL_PORT}", "--user=root",
            "--default-character-set=utf8mb4", "--batch", "--raw",
            "--skip-column-names",
        ]
        if database:
            result.append(f"--database={database}")
        return result

    def query(self, sql: str, database: str | None = None) -> str:
        return run(self.args(database) + ["--execute", sql]).stdout.decode().strip()

    def source(self, path: pathlib.Path, database: str = "wxfbsir"):
        run(self.args(database), input_bytes=path.read_bytes(), timeout=600)

    def stop(self):
        if self.process is None:
            return
        with contextlib.suppress(Exception):
            run([
                self.mysqladmin, "--no-defaults", "--protocol=TCP",
                "--host=127.0.0.1", f"--port={MYSQL_PORT}", "--user=root",
                "shutdown",
            ], timeout=30)
        with contextlib.suppress(Exception):
            self.process.wait(timeout=15)
        if self.process.poll() is None:
            self.process.kill()
            self.process.wait(timeout=10)


def initialize_from_production_schema(
    mysql: ShadowMysql, release: pathlib.Path
):
    production = WORKER.Mysql()
    dump_arguments = [
        "/usr/bin/mysqldump",
        "--protocol=TCP",
        f"--host={production.host}",
        f"--port={production.port}",
        f"--user={production.user}",
        "--default-character-set=utf8mb4",
        "--no-data",
        "--skip-lock-tables",
        "--triggers",
        "--no-tablespaces",
        "--set-gtid-purged=OFF",
        production.database,
    ]
    schema = run(
        dump_arguments,
        env=production.environment(),
        timeout=300,
    ).stdout
    lowered = schema.lower()
    if b"insert into" in lowered or len(schema) < 100_000:
        raise RuntimeError("production schema-only dump is invalid or contains data")
    mysql.query("CREATE DATABASE wxfbsir CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
    run(mysql.args("wxfbsir"), input_bytes=schema, timeout=600)
    mysql.query("DELETE FROM u3w_schema_migration;", "wxfbsir")
    mysql.query(
        "INSERT INTO u3w_schema_migration(version,description) VALUES "
        "('public_init_043','APPLIED:Independent Board exact official experts attribution v1'),"
        "('20260723_independent_board_attribution_v1_043',"
        "'APPLIED:exact WorkBuddy experts 26.7.21 attribution journey and append-only event ledger'),"
        "('public_init_044','RUNNING:Independent Board exact legacy and current attribution identity registry');",
        "wxfbsir",
    )
    predecessor = mysql.query(
        "SELECT CONCAT_WS('|',"
        "(SELECT COUNT(*) FROM information_schema.table_constraints "
        "WHERE constraint_schema=DATABASE() AND enforced='YES' AND constraint_name IN "
        "('chk_board_attr_journey_versions','chk_board_attr_event_versions')) ,"
        "(SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') "
        "FROM information_schema.statistics WHERE table_schema=DATABASE() "
        "AND table_name='fbs_board_attr_journey_v1' "
        "AND index_name='uk_board_attr_journey_identity'));",
        "wxfbsir",
    )
    if predecessor != (
        "2|contract_id,tenant_subject_digest,server_binding_id,journey_id"
    ):
        raise RuntimeError("schema-only shadow is not the exact 043 predecessor")
    migration = release / "sql/public_init_044.sql"
    if WORKER.sha256_file(migration) != release_manifest(release)[
        "migration044Sha256"
    ]:
        raise RuntimeError("shadow 044 migration hash drifted")
    mysql.source(migration)
    mysql.query(
        "UPDATE u3w_schema_migration SET description="
        "'APPLIED:Independent Board exact legacy and current attribution identity registry',"
        "applied_at=CURRENT_TIMESTAMP WHERE version='public_init_044' AND description="
        "'RUNNING:Independent Board exact legacy and current attribution identity registry';",
        "wxfbsir",
    )
    successor = mysql.query(
        "SELECT CONCAT_WS('|',"
        "(SELECT COUNT(*) FROM u3w_schema_migration WHERE version IN "
        "('public_init_044','20260823_independent_board_attribution_identity_registry_044') "
        "AND description LIKE 'APPLIED:%'),"
        "(SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') "
        "FROM information_schema.statistics WHERE table_schema=DATABASE() "
        "AND table_name='fbs_board_attr_journey_v1' "
        "AND index_name='uk_board_attr_journey_identity'));",
        "wxfbsir",
    )
    if successor != (
        "2|contract_id,tenant_subject_digest,server_binding_id,journey_id,"
        "product_id,listed_manifest_version,embedded_contract_version"
    ):
        raise RuntimeError("schema-only shadow did not reach exact 044")


def release_manifest(release: pathlib.Path) -> dict:
    return json.loads((release / "w05-candidate-manifest.json").read_text())


def canonical_map(event: dict, business: bool = False) -> dict[str, str]:
    values: dict[str, str] = {}
    for field in SIGNED_FIELDS:
        value = event.get(field)
        if isinstance(value, bool):
            values[field] = "true" if value else "false"
        elif field == "sequenceNo":
            values[field] = str(int(value))
        else:
            values[field] = str(value or "").strip()
    if business:
        for field in ("issuedAt", "expiresAt", "nonce", "keyId"):
            values.pop(field)
    return dict(sorted(values.items()))


def compact_json(value: dict) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def business_digest(event: dict) -> str:
    prepared = dict(event)
    prepared.setdefault("signatureAlgorithm", "hmac-sha256-v1")
    canonical = compact_json(canonical_map(prepared, business=True))
    return hashlib.sha256(
        ("FBSIR_INDEPENDENT_BOARD_EVENT_DIGEST_V1\n" + canonical).encode()
    ).hexdigest()


def sign_event(event: dict, transport_seed: str) -> dict:
    observed = utcnow()
    signed = dict(event)
    signed.update(
        issuedAt=instant(observed),
        expiresAt=instant(observed + dt.timedelta(seconds=120)),
        nonce=f"w05-shadow-{transport_seed}",
        keyId=EVENT_KEY_ID,
        signatureAlgorithm="hmac-sha256-v1",
        signature="",
    )
    canonical = compact_json(canonical_map(signed))
    signature = hmac.new(EVENT_SECRET, canonical.encode(), hashlib.sha256).hexdigest()
    signed["signature"] = "v1=" + signature
    return signed


def event(profile: tuple[str, str, str], seed: str, occurred: dt.datetime) -> dict:
    host, listed, embedded = profile
    hex_seed = hashlib.sha256(seed.encode()).hexdigest()
    other = hashlib.sha256((seed + "-receipt").encode()).hexdigest()
    journey = hashlib.sha256((seed + "-journey").encode()).hexdigest()
    tenant = hashlib.sha256((seed + "-tenant").encode()).hexdigest()
    return {
        "schemaVersion": "fbsir.independentBoardAttributionEvent.v1",
        "eventId": hex_seed,
        "receiptId": other,
        "contractId": "FBSIR_INDEPENDENT_BOARD_W1A_V1",
        "eventType": "ENTRY_OBSERVED",
        "sequenceNo": 1,
        "occurredAt": instant(occurred),
        "productId": "fbsir-eight-seat-board",
        "packageId": "fbsir-eight-seat-board",
        "agentName": "board-convener",
        "marketplace": "experts",
        "listedSurface": "listed_runtime_state",
        "listedManifestVersion": listed,
        "embeddedContractVersion": embedded,
        "hostClientFamily": host,
        "hostVersion": "5.3.3.0",
        "terminal": "UNKNOWN",
        "channel": "OFFICIAL_EXPERTS",
        "requestSource": "UNKNOWN",
        "intentSignal": "operating_diagnosis",
        "classificationSource": "PACKAGE_SCENE_ROUTER",
        "classifierVersion": "scene-lexicon-shadow-core.1",
        "confidenceBucket": "UNKNOWN",
        "reviewMode": "UNKNOWN",
        "journeyId": journey,
        "serverBindingId": "srv_shadow" + seed.replace("-", "")[:24],
        "sameBindingKey": "",
        "tenantSubjectDigest": tenant,
        "trafficClass": "SYNTHETIC",
        "trafficAuthority": "API2_SERVER_CLASSIFIER_V1",
        "outcome": "SUCCESS",
        "previousEventDigest": "",
        "traceparent": "",
        "rawContentStored": False,
    }


def http(method: str, path: str, body=None, headers=None) -> tuple[int, dict]:
    payload = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(
        f"http://127.0.0.1:{APP_PORT}{path}",
        data=payload,
        method=method,
        headers=headers or {},
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            status = response.status
            raw = response.read()
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read()
    try:
        value = json.loads(raw or b"{}")
    except json.JSONDecodeError:
        value = {"raw": raw.decode(errors="replace")[:500]}
    return status, value


def app_environment(mysql_port: int, redis_port: int, allowed: list[str], not_after: dt.datetime):
    result = os.environ.copy()
    result.update(
        FBSIR_MYSQL_URL=f"jdbc:mysql://127.0.0.1:{mysql_port}/wxfbsir?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC",
        FBSIR_MYSQL_USERNAME="root",
        FBSIR_MYSQL_PASSWORD="",
        FBSIR_REDIS_HOST="127.0.0.1",
        FBSIR_REDIS_PORT=str(redis_port),
        FBSIR_REDIS_DATABASE="15",
        FBSIR_TOKEN_SECRET="shadow-token-secret-material-at-least-32-bytes",
        FBSIR_AES_SECRET_KEY="shadow-aes-secret-material-32bytes",
        FBSIR_DRUID_USERNAME="shadow",
        FBSIR_DRUID_PASSWORD="shadow-password",
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED="true",
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_WRITER_ENABLED="true",
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_INTENT_CLASSIFIER_ENABLED="true",
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_ADMIN_READ_ENABLED="true",
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_CANDIDATE_ENABLED="false",
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED="false",
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_CREDIT_ENABLED="false",
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED="false",
        FBSIR_BOARD_ATTRIBUTION_ENABLED="true",
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID=EVENT_KEY_ID,
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY="utf8:" + EVENT_SECRET.decode(),
        FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET="utf8:" + BINDING_SECRET,
        FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_ENABLED="true",
        FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_MAX_AGE_HOURS="168",
        FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_NOT_AFTER=instant(not_after),
        FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_EVENT_DIGESTS=",".join(sorted(allowed)),
    )
    return result


def start_app(jar: pathlib.Path, root: pathlib.Path, environment: dict) -> subprocess.Popen:
    log = (root / (jar.parent.parent.name + "-app.log")).open("ab")
    process = subprocess.Popen(
        [
            "/usr/bin/java",
            "-jar",
            str(jar),
            f"--server.address=127.0.0.1",
            f"--server.port={APP_PORT}",
            "--spring.main.lazy-initialization=true",
            "--spring.task.scheduling.enabled=false",
            "--spring.quartz.auto-startup=false",
            "--spring.main.banner-mode=off",
        ],
        stdout=log,
        stderr=subprocess.STDOUT,
        env=environment,
        cwd=str(root),
    )
    wait_port(APP_PORT, process, 180)
    return process


def stop_process(process: subprocess.Popen | None):
    if process is None or process.poll() is not None:
        return
    process.send_signal(signal.SIGTERM)
    with contextlib.suppress(subprocess.TimeoutExpired):
        process.wait(timeout=30)
    if process.poll() is None:
        process.kill()
        process.wait(timeout=10)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--release", required=True)
    parser.add_argument("--old-jar", required=True)
    args = parser.parse_args()
    release = pathlib.Path(args.release).resolve(strict=True)
    old_jar = pathlib.Path(args.old_jar).resolve(strict=True)
    manifest, manifest_sha = WORKER.validate_candidate(release)
    for port in (MYSQL_PORT, APP_PORT, REDIS_PORT):
        if not port_free(port):
            raise RuntimeError(f"shadow port is occupied: {port}")
    root = SHADOW_ROOT / (release.name + "-" + str(os.getpid()))
    if root.exists():
        raise RuntimeError("shadow root already exists")
    root.mkdir(parents=True, mode=0o700)
    mysql = ShadowMysql(root)
    redis = None
    app = None
    old_app = None
    started = WORKER.iso()
    try:
        mysql.start()
        initialize_from_production_schema(mysql, release)
        redis_log = (root / "redis.log").open("ab")
        redis = subprocess.Popen(
            [
                "/usr/bin/redis-server", "--bind", "127.0.0.1",
                "--port", str(REDIS_PORT), "--save", "", "--appendonly", "no",
                "--dir", str(root),
            ],
            stdout=redis_log,
            stderr=subprocess.STDOUT,
        )
        wait_port(REDIS_PORT, redis, 30)

        observed = utcnow()
        profiles = [
            ("WORKBUDDY", "26.7.21", "26.7.20"),
            ("WORKBUDDY", "26.8.19", "26.8.19"),
            ("WORKBUDDYAI", "26.8.19", "26.8.19"),
        ]
        ordinary = [event(profile, f"profile-{index}", observed) for index, profile in enumerate(profiles)]
        historical = event(profiles[2], "historical-authorized", observed - dt.timedelta(hours=136))
        allowed = [business_digest(historical)]
        environment = app_environment(MYSQL_PORT, REDIS_PORT, allowed, observed + dt.timedelta(minutes=20))
        app = start_app(release / "backend/fbsir-admin.jar", root, environment)
        path = "/internal/independent-board/attribution/events"
        checks = []
        for index, candidate in enumerate(ordinary + [historical]):
            signed = sign_event(candidate, f"append-{index}")
            status, body = http(
                "POST", path, signed,
                {"Content-Type": "application/json", "Accept": "application/json"},
            )
            if status != 202 or body.get("status") != "APPENDED_REPORT_ONLY" or body.get("productCreditEligible") is not False:
                raise RuntimeError(f"shadow append failed at profile {index}: {status} {body}")
            replay = sign_event(candidate, f"replay-{index}")
            status, body = http(
                "POST", path, replay,
                {"Content-Type": "application/json", "Accept": "application/json"},
            )
            if status != 200 or body.get("status") != "IDEMPOTENT_REPLAY" or body.get("productCreditEligible") is not False:
                raise RuntimeError(f"shadow replay failed at profile {index}: {status} {body}")
            checks.append({"profile": index, "append": 202, "replay": 200})

        unlisted = event(profiles[2], "historical-unlisted", observed - dt.timedelta(hours=136))
        status, _ = http("POST", path, sign_event(unlisted, "unlisted"), {"Content-Type": "application/json", "Accept": "application/json"})
        if status != 400:
            raise RuntimeError("unlisted historical digest was not rejected")
        wrong = event(("WORKBUDDYAI", "26.7.21", "26.7.20"), "wrong-profile", observed)
        status, _ = http("POST", path, sign_event(wrong, "wrong"), {"Content-Type": "application/json", "Accept": "application/json"})
        if status != 409:
            raise RuntimeError("wrong profile was not rejected with 409")
        tampered = sign_event(event(profiles[1], "tampered", observed), "tampered")
        tampered["outcome"] = "FAILED"
        status, _ = http("POST", path, tampered, {"Content-Type": "application/json", "Accept": "application/json"})
        if status != 400:
            raise RuntimeError("tampered signature was not rejected with 400")
        protocol = []
        for method, headers, expected in (
            ("GET", {}, 405),
            ("POST", {"Content-Type": "text/plain"}, 415),
            ("POST", {"Content-Type": "application/json", "Accept": "text/plain"}, 406),
        ):
            status, _ = http(method, path, {} if method == "POST" else None, headers)
            if status != expected:
                raise RuntimeError(f"protocol status drifted: {method} {status} != {expected}")
            protocol.append(expected)
        row_state = mysql.query(
            "SELECT CONCAT_WS('|',COUNT(*),SUM(traffic_class='SYNTHETIC'),"
            "SUM(authoritative_product_credit=0)) FROM fbs_board_attr_event_v1;",
            "wxfbsir",
        )
        if row_state != "4|4|4":
            raise RuntimeError(f"shadow rows drifted: {row_state}")
        stop_process(app)
        app = None
        old_environment = dict(environment)
        old_environment["FBSIR_BOARD_ATTRIBUTION_HISTORICAL_SYNTHETIC_REPLAY_ENABLED"] = "false"
        old_app = start_app(old_jar, root, old_environment)
        legacy_replay = sign_event(ordinary[0], "old-jar-replay")
        status, body = http("POST", path, legacy_replay, {"Content-Type": "application/json", "Accept": "application/json"})
        if status != 200 or body.get("status") != "IDEMPOTENT_REPLAY":
            raise RuntimeError("old JAR did not replay legacy data on retained 044")
        row_state_after_old = mysql.query(
            "SELECT CONCAT_WS('|',COUNT(*),SUM(authoritative_product_credit=0)) "
            "FROM fbs_board_attr_event_v1;", "wxfbsir"
        )
        if row_state_after_old != "4|4":
            raise RuntimeError("old JAR changed candidate rows")
        receipt = {
            "schemaVersion": "fbsir.w05ShadowReceipt.v1",
            "startedAt": started,
            "completedAt": WORKER.iso(),
            "releaseId": manifest["releaseId"],
            "manifestSha256": manifest_sha,
            "mysql": {"version": mysql.query("SELECT VERSION();"), "port": MYSQL_PORT, "production": False},
            "applicationPort": APP_PORT,
            "redisPort": REDIS_PORT,
            "profiles": checks,
            "historicalAllowlistCount": 1,
            "historicalAllowlistValuesEmitted": False,
            "negative": {"unlistedDigest": 400, "wrongProfile": 409, "tamperedSignature": 400},
            "protocol": protocol,
            "rows": 4,
            "syntheticRows": 4,
            "zeroCreditRows": 4,
            "oldJarRetained044LegacyReplay": True,
            "oldJarSha256": WORKER.sha256_file(old_jar),
            "candidateJarSha256": WORKER.sha256_file(release / "backend/fbsir-admin.jar"),
            "productionDatabaseDataUsed": False,
            "productionDatabaseSchemaReadOnly": True,
            "productionOutboxModified": False,
            "productCreditPromoted": False,
            "status": "PASS",
        }
        output = pathlib.Path("/opt/fbsir/admin/state/w05/receipts") / f"{release.name}.shadow.json"
        WORKER.atomic_json(output, receipt)
        receipt["receiptPath"] = str(output)
        receipt["receiptSha256"] = WORKER.sha256_file(output)
        print(json.dumps(receipt, sort_keys=True))
        return 0
    finally:
        stop_process(old_app)
        stop_process(app)
        stop_process(redis)
        mysql.stop()
        resolved = root.resolve()
        if resolved.parent != SHADOW_ROOT.resolve():
            raise RuntimeError("refusing shadow cleanup outside root")
        shutil.rmtree(resolved, ignore_errors=False)


if __name__ == "__main__":
    raise SystemExit(main())
