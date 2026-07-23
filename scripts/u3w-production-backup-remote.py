#!/usr/bin/env python3
"""Fail-closed production logical-backup worker.

This file is streamed over the pinned SSH channel by the local orchestrator.
It never accepts credentials on argv and never changes the business database.
"""

import argparse
import base64
import datetime as dt
import fcntl
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import threading


TARGET_HOST = "api2.u3w.com"
DATABASE = "fbsir"
BACKUP_ROOT = pathlib.Path("/opt/fbsir/admin/backups/w1a")
HOST_CHANGE_LOCK = pathlib.Path("/opt/fbsir/admin/.u3w-production-change.lock")
ENV_PATH = pathlib.Path("/etc/u3w/fbsir-admin.env")
BACKUP_KEY_PATH = pathlib.Path("/etc/u3w/fbsir-backup.key")
JAR_PATH = pathlib.Path("/opt/fbsir/admin/Fbsir-admin/target/fbsir-admin.jar")
BACKUP_SCHEMA = "fbsir.u3wDatabaseBackupReceipt.v2"
RUN_PATTERN = re.compile(r"w1a-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}")
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
MYSQL_VERSION_PATTERN = re.compile(r"([0-9]+\.[0-9]+\.[0-9]+)")
MIN_FREE_BYTES = 25 * 1024**3
MIN_AVAILABLE_MEMORY_BYTES = 2 * 1024**3


def utc_now():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="milliseconds").replace(
        "+00:00", "Z"
    )


def canonical_json(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fsync_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def atomic_json(path, value):
    partial = path.with_name(path.name + ".partial")
    payload = (canonical_json(value) + "\n").encode("utf-8")
    if partial.exists() or partial.is_symlink():
        status = partial.lstat()
        if (
            not partial.is_file()
            or partial.is_symlink()
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_nlink != 1
        ):
            raise RuntimeError("unsafe stale receipt partial")
        partial.unlink()
    descriptor = os.open(
        partial,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o600,
    )
    try:
        with os.fdopen(descriptor, "wb", closefd=False) as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
    finally:
        os.close(descriptor)
    os.chmod(partial, 0o600)
    os.replace(partial, path)
    fsync_directory(path.parent)


def load_environment():
    status = ENV_PATH.stat()
    if (
        not ENV_PATH.is_file()
        or ENV_PATH.is_symlink()
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o600
    ):
        raise RuntimeError("database credential file custody is not root:root 0600")
    values = {}
    for raw_line in ENV_PATH.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[name.strip()] = value
    required = (
        "FBSIR_MYSQL_URL",
        "FBSIR_MYSQL_USERNAME",
        "FBSIR_MYSQL_PASSWORD",
    )
    if any(not values.get(name) for name in required):
        raise RuntimeError("required database credential keys are absent")
    match = re.fullmatch(
        r"jdbc:mysql://([^/:?]+)(?::([0-9]{1,5}))?/([^?]+)(?:\?.*)?",
        values["FBSIR_MYSQL_URL"],
    )
    if not match or match.group(3) != DATABASE:
        raise RuntimeError("database URL is not the fixed fbsir target")
    return {
        "host": match.group(1),
        "port": match.group(2) or "3306",
        "user": values["FBSIR_MYSQL_USERNAME"],
        "password": values["FBSIR_MYSQL_PASSWORD"],
    }


class MysqlClient:
    def __init__(self, connection):
        self.connection = connection
        self.environment = os.environ.copy()
        self.environment["MYSQL_PWD"] = connection["password"]
        self.base = [
            "/usr/bin/mysql",
            "--batch",
            "--raw",
            "--skip-column-names",
            "--default-character-set=utf8mb4",
            "--host",
            connection["host"],
            "--port",
            connection["port"],
            "--user",
            connection["user"],
            DATABASE,
        ]

    def query(self, sql):
        result = subprocess.run(
            self.base + ["--execute", sql],
            env=self.environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=3600,
            check=False,
        )
        if result.returncode != 0:
            raise RuntimeError(
                "mysql query failed with exit {} and stderr SHA-256 {}".format(
                    result.returncode,
                    sha256_bytes(result.stderr.encode("utf-8", "replace")),
                )
            )
        return result.stdout.rstrip("\n")


def metadata_rows(mysql):
    queries = [
        """SELECT CONCAT_WS('|','T',HEX(table_name),HEX(table_type),
        HEX(COALESCE(engine,'')),HEX(COALESCE(table_collation,'')))
        FROM information_schema.tables WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','C',HEX(table_name),LPAD(ordinal_position,6,'0'),
        HEX(column_name),HEX(column_type),HEX(is_nullable),
        HEX(COALESCE(column_default,'<NULL>')),HEX(extra),
        HEX(COALESCE(character_set_name,'')),HEX(COALESCE(collation_name,'')),
        HEX(COALESCE(generation_expression,'')))
        FROM information_schema.columns WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','I',HEX(table_name),HEX(index_name),
        LPAD(seq_in_index,6,'0'),non_unique,HEX(COALESCE(column_name,'')),
        HEX(COALESCE(collation,'')),COALESCE(sub_part,-1),
        HEX(COALESCE(index_type,'')),HEX(COALESCE(expression,'')))
        FROM information_schema.statistics WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','K',HEX(table_name),HEX(constraint_name),
        HEX(constraint_type)) FROM information_schema.table_constraints
        WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','U',HEX(table_name),HEX(constraint_name),
        LPAD(ordinal_position,6,'0'),HEX(COALESCE(column_name,'')),
        HEX(COALESCE(referenced_table_name,'')),
        HEX(COALESCE(referenced_column_name,'')))
        FROM information_schema.key_column_usage
        WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','F',HEX(constraint_name),HEX(table_name),
        HEX(referenced_table_name),HEX(update_rule),HEX(delete_rule),
        HEX(match_option)) FROM information_schema.referential_constraints
        WHERE constraint_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','H',HEX(tc.table_name),HEX(cc.constraint_name),
        HEX(cc.check_clause)) FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema=cc.constraint_schema
         AND tc.constraint_name=cc.constraint_name
         AND tc.constraint_type='CHECK'
        WHERE tc.table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','V',HEX(table_name),HEX(view_definition),
        HEX(check_option),HEX(is_updatable),HEX(definer),HEX(security_type),
        HEX(character_set_client),HEX(collation_connection))
        FROM information_schema.views WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','G',HEX(trigger_name),HEX(event_manipulation),
        HEX(event_object_table),HEX(action_timing),HEX(action_orientation),
        HEX(action_statement),HEX(definer))
        FROM information_schema.triggers WHERE trigger_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','R',HEX(routine_name),HEX(routine_type),
        HEX(COALESCE(data_type,'')),HEX(COALESCE(routine_definition,'')),
        HEX(is_deterministic),HEX(sql_data_access),HEX(security_type),HEX(definer))
        FROM information_schema.routines WHERE routine_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','E',HEX(event_name),HEX(event_definition),
        HEX(event_type),HEX(COALESCE(execute_at,'')),
        HEX(COALESCE(interval_value,'')),HEX(COALESCE(interval_field,'')),
        HEX(status),HEX(definer))
        FROM information_schema.events WHERE event_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','P',HEX(table_name),
        IF(partition_name IS NULL,'N',CONCAT('V',HEX(partition_name))),
        IF(subpartition_name IS NULL,'N',CONCAT('V',HEX(subpartition_name))),
        LPAD(partition_ordinal_position,6,'0'),
        IF(subpartition_ordinal_position IS NULL,'N',
          CONCAT('V',LPAD(subpartition_ordinal_position,6,'0'))),
        IF(partition_method IS NULL,'N',CONCAT('V',HEX(partition_method))),
        IF(subpartition_method IS NULL,'N',CONCAT('V',HEX(subpartition_method))),
        IF(partition_expression IS NULL,'N',CONCAT('V',HEX(partition_expression))),
        IF(subpartition_expression IS NULL,'N',
          CONCAT('V',HEX(subpartition_expression))),
        IF(partition_description IS NULL,'N',
          CONCAT('V',HEX(partition_description))))
        FROM information_schema.partitions WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','A',
        IF(specific_name IS NULL,'N',CONCAT('V',HEX(specific_name))),
        LPAD(ordinal_position,6,'0'),
        IF(parameter_mode IS NULL,'N',CONCAT('V',HEX(parameter_mode))),
        IF(parameter_name IS NULL,'N',CONCAT('V',HEX(parameter_name))),
        HEX(data_type),HEX(dtd_identifier))
        FROM information_schema.parameters WHERE specific_schema=DATABASE()""",
    ]
    rows = []
    for query in queries:
        output = mysql.query(query)
        rows.extend(row for row in output.splitlines() if row)
    return sorted(rows)


def table_names(mysql):
    output = mysql.query(
        "SELECT table_name FROM information_schema.tables "
        "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
        "ORDER BY BINARY table_name"
    )
    names = [name for name in output.splitlines() if name]
    if not names or any("`" in name or "\n" in name for name in names):
        raise RuntimeError("database table inventory is invalid")
    return names


def row_manifest(mysql, names):
    statements = [
        "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ",
        "START TRANSACTION WITH CONSISTENT SNAPSHOT",
    ]
    statements.extend(
        "SELECT CONCAT('{}','|',COUNT(*)) FROM `{}`".format(
            name.encode("utf-8").hex().upper(), name
        )
        for name in names
    )
    statements.append("COMMIT")
    output = mysql.query(";\n".join(statements) + ";")
    rows = sorted(row for row in output.splitlines() if row)
    if len(rows) != len(names):
        raise RuntimeError("row-count manifest is incomplete")
    total = 0
    for row in rows:
        _, count = row.rsplit("|", 1)
        total += int(count)
    return {
        "totalRows": total,
        "tableRowCountsSha256": sha256_bytes(("\n".join(rows) + "\n").encode()),
    }


def collect_facts(mysql):
    identity = mysql.query("SELECT @@version,@@version_comment").split("\t")
    if len(identity) != 2 or not MYSQL_VERSION_PATTERN.fullmatch(identity[0]):
        raise RuntimeError("unexpected MySQL identity")
    counts = mysql.query(
        """SELECT
        SUM(table_type='BASE TABLE'),SUM(table_type='VIEW'),
        SUM(table_type='BASE TABLE' AND engine<>'InnoDB')
        FROM information_schema.tables WHERE table_schema=DATABASE()"""
    ).split("\t")
    object_counts = mysql.query(
        """SELECT
        (SELECT COUNT(*) FROM information_schema.triggers
          WHERE trigger_schema=DATABASE()),
        (SELECT COUNT(*) FROM information_schema.routines
          WHERE routine_schema=DATABASE()),
        (SELECT COUNT(*) FROM information_schema.events
          WHERE event_schema=DATABASE())"""
    ).split("\t")
    root_rows = mysql.query(
        """SELECT CONCAT_WS('|',HEX(menu_name),parent_id,HEX(COALESCE(path,'')),
        HEX(COALESCE(component,'')),HEX(COALESCE(perms,'')))
        FROM sys_menu
        WHERE parent_id=0 AND HEX(menu_name) IN (
          'E78BACE891A3E4BC9A',
          '496E646570656E64656E7420426F617264')
        ORDER BY BINARY menu_name,BINARY path"""
    )
    sys_menu_shape = mysql.query(
        """SELECT CONCAT_WS('|',LPAD(ordinal_position,6,'0'),HEX(column_name),
        HEX(column_type),HEX(is_nullable),HEX(COALESCE(column_default,'<NULL>')),
        HEX(extra)) FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='sys_menu'
        ORDER BY ordinal_position"""
    )
    facts = {
        "fingerprintAlgorithm": "u3w.mysql-schema-metadata.v2",
        "schemaFingerprintSha256": sha256_bytes(
            ("\n".join(metadata_rows(mysql)) + "\n").encode()
        ),
        "prerequisiteShapeSha256": sha256_bytes(
            (sys_menu_shape + "\n--ROOTS--\n" + root_rows + "\n").encode()
        ),
        "baseTableCount": int(counts[0]),
        "viewCount": int(counts[1]),
        "triggerCount": int(object_counts[0]),
        "routineCount": int(object_counts[1]),
        "eventCount": int(object_counts[2]),
        "independentBoardAdminRootCount": len(
            [row for row in root_rows.splitlines() if row]
        ),
        "allBaseTablesInnoDB": int(counts[2]) == 0,
    }
    if facts["baseTableCount"] < 1 or not facts["allBaseTablesInnoDB"]:
        raise RuntimeError("logical backup requires a non-empty all-InnoDB schema")
    return identity, facts


def available_memory_bytes():
    for line in pathlib.Path("/proc/meminfo").read_text().splitlines():
        if line.startswith("MemAvailable:"):
            return int(line.split()[1]) * 1024
    raise RuntimeError("MemAvailable is unavailable")


def assert_root_and_capacity():
    if os.geteuid() != 0:
        raise RuntimeError("worker must run as root")
    free_bytes = shutil.disk_usage("/opt/fbsir/admin").free
    memory_bytes = available_memory_bytes()
    if free_bytes < MIN_FREE_BYTES:
        raise RuntimeError("less than 25 GiB free space")
    if memory_bytes < MIN_AVAILABLE_MEMORY_BYTES:
        raise RuntimeError("less than 2 GiB available memory")
    return free_bytes, memory_bytes


def backup_key_fingerprint():
    status = BACKUP_KEY_PATH.stat()
    if (
        not BACKUP_KEY_PATH.is_file()
        or BACKUP_KEY_PATH.is_symlink()
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o600
        or status.st_size < 32
    ):
        raise RuntimeError("backup key custody is not root:root 0600 with >=32 bytes")
    return sha256_file(BACKUP_KEY_PATH)


def provision_backup_key(args):
    if BACKUP_KEY_PATH.exists() or BACKUP_KEY_PATH.is_symlink():
        return {
            "schema": "fbsir.u3wBackupEncryptionKeyProvisionReceipt.v1",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "targetHost": TARGET_HOST,
            "created": False,
            "keyFingerprintSha256": backup_key_fingerprint(),
            "custody": "root:root-0600",
        }
    parent = BACKUP_KEY_PATH.parent
    status = parent.stat()
    if (
        parent.is_symlink()
        or not parent.is_dir()
        or status.st_uid != 0
        or status.st_gid != 0
    ):
        raise RuntimeError("backup key parent custody is invalid")
    payload = base64.urlsafe_b64encode(os.urandom(48)) + b"\n"
    descriptor = os.open(
        BACKUP_KEY_PATH,
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | getattr(os, "O_NOFOLLOW", 0),
        0o600,
    )
    try:
        with os.fdopen(descriptor, "wb", closefd=False) as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
    finally:
        os.close(descriptor)
    os.chown(BACKUP_KEY_PATH, 0, 0)
    os.chmod(BACKUP_KEY_PATH, 0o600)
    fsync_directory(parent)
    return {
        "schema": "fbsir.u3wBackupEncryptionKeyProvisionReceipt.v1",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "created": True,
        "keyFingerprintSha256": backup_key_fingerprint(),
        "custody": "root:root-0600",
    }


def validate_arguments(args):
    if not RUN_PATTERN.fullmatch(args.run_id):
        raise RuntimeError("invalid run id")
    if not COMMIT_PATTERN.fullmatch(args.source_commit):
        raise RuntimeError("invalid source commit")
    for value in (
        args.approval_sha,
        args.runner_sha,
        args.worker_sha,
    ):
        if not SHA_PATTERN.fullmatch(value):
            raise RuntimeError("invalid provenance digest")
    if args.mode in ("ProvisionKey", "Backup"):
        validate_approval(args)


def validate_approval(args):
    if args.approval_sha == "0" * 64 or not args.approval_json_base64:
        raise RuntimeError("mutating backup requires a non-zero approval receipt")
    try:
        payload = base64.b64decode(args.approval_json_base64, validate=True)
        approval = json.loads(payload.decode("utf-8-sig"))
    except Exception as error:
        raise RuntimeError("approval receipt cannot be decoded") from error
    expected_fields = {
        "schema",
        "action",
        "targetHost",
        "runId",
        "sourceCommit",
        "approvedAt",
        "expiresAt",
        "authorizedBy",
        "concurrentDdlProhibited",
        "productionFilesystemWrite",
        "productionDatabaseWrite",
        "productionServiceChange",
        "officialExpertsPackageChange",
    }
    if set(approval) != expected_fields:
        raise RuntimeError("approval receipt has missing or unknown fields")
    try:
        approved_at = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        expires_at = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
    except Exception as error:
        raise RuntimeError("approval receipt time is invalid") from error
    now = dt.datetime.now(dt.timezone.utc)
    if (
        sha256_bytes(payload) != args.approval_sha
        or approval["schema"]
        != "fbsir.u3wProductionChangeApprovalReceipt.v1"
        or approval["action"]
        != "DATABASE_BACKUP_AND_ISOLATED_RESTORE"
        or approval["targetHost"] != TARGET_HOST
        or approval["runId"] != args.run_id
        or approval["sourceCommit"] != args.source_commit
        or approval["authorizedBy"] != "workspace-user"
        or approval["concurrentDdlProhibited"] is not True
        or approval["productionFilesystemWrite"] is not True
        or approval["productionDatabaseWrite"] is not False
        or approval["productionServiceChange"] is not False
        or approval["officialExpertsPackageChange"] is not False
        or approved_at > now
        or expires_at <= now
        or expires_at - approved_at > dt.timedelta(hours=24)
    ):
        raise RuntimeError("approval receipt identity, scope or expiry is invalid")


def run_plan(args, mysql, connection):
    free_bytes, memory_bytes = assert_root_and_capacity()
    identity, facts = collect_facts(mysql)
    names = table_names(mysql)
    manifest = row_manifest(mysql, names)
    jar_sha = sha256_file(JAR_PATH)
    try:
        key_fingerprint = backup_key_fingerprint()
        key_ready = True
    except (FileNotFoundError, RuntimeError):
        key_fingerprint = None
        key_ready = False
    dump_version = subprocess.run(
        ["/usr/bin/mysqldump", "--version"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()
    return {
        "schema": "fbsir.u3wDatabaseBackupPlan.v1",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "database": DATABASE,
        "serverVersion": identity[0],
        "serverVersionComment": identity[1],
        "sourceFacts": facts,
        "sourceTotalRows": manifest["totalRows"],
        "sourceTableRowCountsSha256": manifest["tableRowCountsSha256"],
        "sourceJarSha256": jar_sha,
        "encryptionContract": "u3w.gnupg-aes256-symmetric.v1",
        "encryptionKeyFingerprintSha256": key_fingerprint,
        "encryptionKeyReady": key_ready,
        "ddlProtectionMode": "PRE_POST_SCHEMA_STABILITY_APPROVED_NO_DDL_WINDOW",
        "dumpToolVersion": dump_version,
        "freeBytes": free_bytes,
        "availableMemoryBytes": memory_bytes,
        "wouldWritePath": str(BACKUP_ROOT / args.run_id),
        "businessDatabaseWouldChange": False,
        "serviceWouldChange": False,
        "officialExpertsPackageWouldChange": False,
    }


def safe_run_directory(run_id):
    root_parent = BACKUP_ROOT.parent
    parent_status = root_parent.stat()
    if (
        root_parent.is_symlink()
        or not root_parent.is_dir()
        or parent_status.st_uid != 0
        or parent_status.st_gid != 0
    ):
        raise RuntimeError("backup parent custody is invalid")
    BACKUP_ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    root_stat = BACKUP_ROOT.stat()
    if BACKUP_ROOT.is_symlink() or root_stat.st_uid != 0 or root_stat.st_gid != 0:
        raise RuntimeError("backup root custody is invalid")
    os.chmod(BACKUP_ROOT, 0o700)
    run_directory = BACKUP_ROOT / run_id
    run_directory.mkdir(mode=0o700, exist_ok=True)
    if run_directory.is_symlink() or run_directory.resolve().parent != BACKUP_ROOT:
        raise RuntimeError("run directory escapes backup root")
    os.chmod(run_directory, 0o700)
    return run_directory


def existing_receipt(receipt_path, args):
    if not receipt_path.is_file() or receipt_path.is_symlink():
        return None
    receipt_status = receipt_path.stat()
    if (
        receipt_status.st_uid != 0
        or receipt_status.st_gid != 0
        or receipt_status.st_mode & 0o777 != 0o600
        or receipt_status.st_nlink != 1
    ):
        raise RuntimeError("existing receipt custody is invalid")
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    backup_path = receipt_path.parent / "fbsir.sql.gpg"
    required = {
        "schema": BACKUP_SCHEMA,
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "runnerSha256": args.runner_sha,
        "backupWorkerSha256": args.worker_sha,
        "encryptionContract": "u3w.gnupg-aes256-symmetric.v1",
        "encryptionKeyFingerprintSha256": backup_key_fingerprint(),
        "ddlProtectionMode": "PRE_POST_SCHEMA_STABILITY_APPROVED_NO_DDL_WINDOW",
        "sourceSnapshotExactlyMatched": False,
    }
    if any(receipt.get(name) != value for name, value in required.items()):
        raise RuntimeError("existing receipt provenance does not match this invocation")
    backup_status = backup_path.stat() if backup_path.is_file() else None
    if (
        not backup_path.is_file()
        or backup_path.is_symlink()
        or backup_status.st_uid != 0
        or backup_status.st_gid != 0
        or backup_status.st_mode & 0o777 != 0o600
        or backup_status.st_nlink != 1
        or receipt.get("backupPath") != str(backup_path)
        or receipt.get("backupSha256") != sha256_file(backup_path)
        or receipt.get("backupSizeBytes") != backup_path.stat().st_size
    ):
        raise RuntimeError("existing receipt artifact binding is invalid")
    return receipt


def quarantine_unreceipted_backup(backup_path):
    if not backup_path.exists() and not backup_path.is_symlink():
        return
    status = backup_path.lstat()
    if (
        backup_path.is_symlink()
        or not backup_path.is_file()
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o600
        or status.st_nlink != 1
    ):
        raise RuntimeError("unreceipted backup artifact custody is invalid")
    digest = sha256_file(backup_path)
    quarantined = backup_path.with_name(
        "abandoned-" + backup_path.name + "." + digest[:16]
    )
    if quarantined.exists() or quarantined.is_symlink():
        if (
            quarantined.is_symlink()
            or not quarantined.is_file()
            or sha256_file(quarantined) != digest
        ):
            raise RuntimeError("unreceipted backup quarantine collision")
        backup_path.unlink()
    else:
        os.replace(backup_path, quarantined)
    fsync_directory(backup_path.parent)


def run_backup(args, mysql, connection):
    assert_root_and_capacity()
    run_directory = safe_run_directory(args.run_id)
    receipt_path = run_directory / "backup-receipt.json"
    prior = existing_receipt(receipt_path, args)
    if prior is not None:
        return prior
    lock_path = HOST_CHANGE_LOCK
    lock_descriptor = os.open(lock_path, os.O_RDWR | os.O_CREAT, 0o600)
    try:
        fcntl.flock(lock_descriptor, fcntl.LOCK_EX)
        prior = existing_receipt(receipt_path, args)
        if prior is not None:
            return prior
        partial_path = run_directory / "fbsir.sql.gpg.partial"
        backup_path = run_directory / "fbsir.sql.gpg"
        quarantine_unreceipted_backup(backup_path)
        if partial_path.exists():
            status = partial_path.lstat()
            if (
                partial_path.is_symlink()
                or not partial_path.is_file()
                or status.st_uid != 0
                or status.st_gid != 0
                or status.st_nlink != 1
            ):
                raise RuntimeError("partial backup custody is invalid")
            partial_path.unlink()
        identity_before, facts_before = collect_facts(mysql)
        jar_sha_before = sha256_file(JAR_PATH)
        names = table_names(mysql)
        manifest_before = row_manifest(mysql, names)
        dump_version = subprocess.run(
            ["/usr/bin/mysqldump", "--version"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
        ).stdout.strip()
        dump_args = [
            "/usr/bin/mysqldump",
            "--single-transaction",
            "--quick",
            "--skip-lock-tables",
            "--routines",
            "--events",
            "--triggers",
            "--hex-blob",
            "--set-gtid-purged=OFF",
            "--no-tablespaces",
            "--column-statistics=0",
            "--default-character-set=utf8mb4",
            "--host",
            connection["host"],
            "--port",
            connection["port"],
            "--user",
            connection["user"],
            "--databases",
            DATABASE,
        ]
        environment = os.environ.copy()
        environment["MYSQL_PWD"] = connection["password"]
        descriptor = os.open(
            partial_path,
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        plaintext_digest = hashlib.sha256()
        with os.fdopen(descriptor, "wb") as encrypted_output:
            dump_error = tempfile.TemporaryFile()
            encrypt_error = tempfile.TemporaryFile()
            dump_process = subprocess.Popen(
                dump_args,
                env=environment,
                stdout=subprocess.PIPE,
                stderr=dump_error,
            )
            encrypt_process = subprocess.Popen(
                [
                    "/usr/bin/gpg",
                    "--batch",
                    "--yes",
                    "--pinentry-mode",
                    "loopback",
                    "--passphrase-file",
                    str(BACKUP_KEY_PATH),
                    "--symmetric",
                    "--cipher-algo",
                    "AES256",
                    "--compress-algo",
                    "none",
                    "--output",
                    "-",
                ],
                stdin=subprocess.PIPE,
                stdout=encrypted_output,
                stderr=encrypt_error,
            )
            pump_error = []

            def pump_dump():
                try:
                    while True:
                        chunk = dump_process.stdout.read(1024 * 1024)
                        if not chunk:
                            break
                        plaintext_digest.update(chunk)
                        encrypt_process.stdin.write(chunk)
                except Exception as error:
                    pump_error.append(error)
                    if dump_process.poll() is None:
                        dump_process.terminate()
                finally:
                    try:
                        encrypt_process.stdin.close()
                    except BrokenPipeError:
                        pass

            pump = threading.Thread(target=pump_dump, daemon=True)
            pump.start()
            try:
                dump_code = dump_process.wait(timeout=7200)
                encrypt_code = encrypt_process.wait(timeout=7200)
                pump.join(timeout=30)
                if pump.is_alive():
                    raise RuntimeError("backup encryption pump did not terminate")
            finally:
                if dump_process.poll() is None:
                    dump_process.kill()
                if encrypt_process.poll() is None:
                    encrypt_process.kill()
                pump.join(timeout=5)
            dump_error.seek(0)
            encrypt_error.seek(0)
            dump_stderr = dump_error.read()
            encrypt_stderr = encrypt_error.read()
            dump_error.close()
            encrypt_error.close()
            encrypted_output.flush()
            os.fsync(encrypted_output.fileno())
        if pump_error:
            raise RuntimeError("backup encryption pump failed")
        if dump_code != 0:
            raise RuntimeError(
                "mysqldump failed with exit {} and stderr SHA-256 {}".format(
                    dump_code, sha256_bytes(dump_stderr)
                )
            )
        if encrypt_code != 0:
            raise RuntimeError(
                "backup encryption failed with exit {} and stderr SHA-256 {}".format(
                    encrypt_code, sha256_bytes(encrypt_stderr)
                )
            )
        identity_after, facts_after = collect_facts(mysql)
        jar_sha_after = sha256_file(JAR_PATH)
        if (
            identity_before != identity_after
            or facts_before != facts_after
            or jar_sha_before != jar_sha_after
        ):
            raise RuntimeError(
                "source identity, schema metadata or active JAR changed during backup window"
            )
        os.replace(partial_path, backup_path)
        os.chmod(backup_path, 0o600)
        fsync_directory(run_directory)
        receipt = {
            "schema": BACKUP_SCHEMA,
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "targetHost": TARGET_HOST,
            "database": DATABASE,
            "serverVersion": identity_before[0],
            "serverVersionComment": identity_before[1],
            "generatedAt": utc_now(),
            "backupPath": str(backup_path),
            "backupSha256": sha256_file(backup_path),
            "backupSizeBytes": backup_path.stat().st_size,
            "backupPlaintextSha256": plaintext_digest.hexdigest(),
            "encryptionContract": "u3w.gnupg-aes256-symmetric.v1",
            "encryptionKeyFingerprintSha256": backup_key_fingerprint(),
            "dumpToolVersion": dump_version,
            "dumpOptionsContract": "u3w.mysqldump.innodb-consistent.v1",
            "ddlProtectionMode": "PRE_POST_SCHEMA_STABILITY_APPROVED_NO_DDL_WINDOW",
            "sourceFacts": facts_before,
            "sourceTotalRows": manifest_before["totalRows"],
            "sourceTableRowCountsSha256": manifest_before[
                "tableRowCountsSha256"
            ],
            "sourceSnapshotExactlyMatched": False,
            "sourceJarSha256": jar_sha_before,
            "approvalReceiptSha256": args.approval_sha,
            "runnerSha256": args.runner_sha,
            "backupWorkerSha256": args.worker_sha,
            "businessDatabaseChanged": False,
            "serviceChanged": False,
            "officialExpertsPackageChanged": False,
        }
        atomic_json(receipt_path, receipt)
        return receipt
    finally:
        try:
            fcntl.flock(lock_descriptor, fcntl.LOCK_UN)
        finally:
            os.close(lock_descriptor)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode", choices=("Plan", "ProvisionKey", "Backup"), required=True
    )
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--approval-sha", required=True)
    parser.add_argument("--approval-json-base64", default="")
    parser.add_argument("--runner-sha", required=True)
    parser.add_argument("--worker-sha", required=True)
    args = parser.parse_args()
    validate_arguments(args)
    connection = load_environment()
    mysql = MysqlClient(connection)
    if args.mode == "Plan":
        result = run_plan(args, mysql, connection)
    elif args.mode == "ProvisionKey":
        lock_descriptor = os.open(
            HOST_CHANGE_LOCK, os.O_RDWR | os.O_CREAT, 0o600
        )
        try:
            fcntl.flock(lock_descriptor, fcntl.LOCK_EX)
            result = provision_backup_key(args)
        finally:
            fcntl.flock(lock_descriptor, fcntl.LOCK_UN)
            os.close(lock_descriptor)
    else:
        result = run_backup(args, mysql, connection)
    print(canonical_json(result))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(
            canonical_json(
                {
                    "schema": "fbsir.u3wDatabaseBackupWorkerFailure.v1",
                    "errorType": type(error).__name__,
                    "message": str(error)[:600],
                }
            ),
            file=sys.stderr,
        )
        raise SystemExit(1)
