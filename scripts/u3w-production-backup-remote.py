#!/usr/bin/env python3
"""Fail-closed production logical-backup worker.

This file is streamed over the pinned SSH channel by the local orchestrator.
It never accepts credentials on argv and never changes the business database.
"""

import argparse
import base64
import datetime as dt
import errno
import fcntl
import hashlib
import json
import math
import os
import pathlib
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import threading
import time


TARGET_HOST = "api2.u3w.com"
DATABASE = "fbsir"
BACKUP_ROOT = pathlib.Path("/opt/fbsir/admin/backups/w1a")
HOST_CHANGE_LOCK = pathlib.Path("/opt/fbsir/admin/.u3w-production-change.lock")
ENV_PATH = pathlib.Path("/etc/u3w/fbsir-admin.env")
BACKUP_KEY_PATH = pathlib.Path("/etc/u3w/fbsir-backup.key")
JAR_PATH = pathlib.Path("/opt/fbsir/admin/Fbsir-admin/target/fbsir-admin.jar")
BACKUP_SCHEMA = "fbsir.u3wDatabaseBackupReceipt.v4"
PLAN_SCHEMA = "fbsir.u3wDatabaseBackupPlan.v3"
ADMIN_ROOT_DEPENDENCY_RECEIPT = pathlib.Path(
    "/opt/fbsir/admin/dependencies/latest/adoption-receipt.json"
)
ADMIN_ROOT_DEPENDENCY_SCHEMA = (
    "fbsir.u3wLegacyAdminRootDependencyAdoptionReceipt.v3"
)
DEPENDENCY_VERSION = (
    "w1a_043_legacy_admin_root_dependency_20260724_001"
)
DEPENDENCY_DESCRIPTION = (
    "APPLIED:W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY_V1"
)
PUBLIC_043_DESCRIPTION = (
    "APPLIED:Independent Board exact official experts attribution v1"
)
INTERNAL_043_DESCRIPTION = (
    "APPLIED:exact WorkBuddy experts 26.7.21 attribution journey "
    "and append-only event ledger"
)
EXPECTED_W1A_SCHEMA_FINGERPRINT = (
    "fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d"
)
RUN_PATTERN = re.compile(r"w1a-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}")
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
UUID_PATTERN = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
    r"[0-9a-f]{4}-[0-9a-f]{12}"
)
MYSQL_VERSION_PATTERN = re.compile(r"([0-9]+\.[0-9]+\.[0-9]+)")
MIN_FREE_BYTES = 25 * 1024**3
MIN_AVAILABLE_MEMORY_BYTES = 2 * 1024**3
LOCK_TIMEOUT_SECONDS = 30
MIGRATION_LOCK_NAME = "u3w:w1a:public_init_043"
DDL_PROTECTION_MODE = (
    "HOST_FLOCK_NAMED_LOCK_READ_ONLY_SNAPSHOT_FULL_OBJECT_MDL_"
    "PRE_POST_STABILITY_AND_APPROVED_NO_DDL_WINDOW"
)
ATTRIBUTION_DATA_FACT_FIELDS = (
    "attributionEventCount",
    "attributionProbeEventCount",
    "attributionNaturalEventCount",
    "attributionNonProbeEventCount",
    "attributionAuthoritativeProductCreditCount",
    "attributionJourneyCount",
    "attributionProbeJourneyCount",
    "attributionNaturalJourneyCount",
    "attributionNonProbeJourneyCount",
)
PLAN_FIELDS = frozenset(
    {
        "schema",
        "runId",
        "sourceCommit",
        "targetHost",
        "database",
        "sourceDatabaseServerUuid",
        "serverVersion",
        "serverVersionComment",
        "sourceControlFacts",
        "sourceJarSha256",
        "encryptionContract",
        "encryptionKeyFingerprintSha256",
        "encryptionKeyReady",
        "plannedDdlProtectionMode",
        "databaseProtectionActive",
        "sourceSnapshotExactlyMatched",
        "dumpToolVersion",
        "freeBytes",
        "availableMemoryBytes",
        "wouldWritePath",
        "businessDatabaseWouldChange",
        "serviceWouldChange",
        "officialExpertsPackageWouldChange",
        "runnerSha256",
        "backupWorkerSha256",
        "verifierSha256",
        "adminRootDependencyAdoptionReceiptSha256",
        "generatedAt",
        "expiresAt",
    }
)
FACT_FIELDS = frozenset(
    {
        "fingerprintAlgorithm",
        "schemaFingerprintSha256",
        "prerequisiteShapeSha256",
        "baseTableCount",
        "viewCount",
        "triggerCount",
        "routineCount",
        "eventCount",
        "independentBoardAdminRootCount",
        "legacyAdminRootDependencyState",
        "legacyAdminRootDependencyFactsSha256",
        "legacyAdminRootDependencyVersionCount",
        "legacyAdminRootDependencyReceiptCount",
        "legacyAdminRootIdentityCount",
        "legacyAdminRootExactCount",
        "legacyAdminRootRoleBindingCount",
        "legacyAdminRootPageChildCount",
        "legacyForbiddenPublicInit001Through042ReceiptCount",
        "publicInit043AnyReceiptCount",
        "attributionInternalReceiptCount",
        "attributionTableCount",
        "attributionTriggerCount",
        "attributionPermissionCount",
        *ATTRIBUTION_DATA_FACT_FIELDS,
        "w1aSchemaFingerprintSha256",
        "w1a043State",
        "allBaseTablesInnoDB",
    }
)
BACKUP_RECEIPT_FIELDS = frozenset(
    {
        "schema",
        "runId",
        "sourceCommit",
        "planReceiptSha256",
        "targetHost",
        "database",
        "sourceDatabaseServerUuid",
        "serverVersion",
        "serverVersionComment",
        "generatedAt",
        "backupPath",
        "backupSha256",
        "backupSizeBytes",
        "backupPlaintextSha256",
        "encryptionContract",
        "encryptionKeyFingerprintSha256",
        "dumpToolVersion",
        "dumpOptionsContract",
        "ddlProtectionMode",
        "sourceFacts",
        "sourceTotalRows",
        "sourceTableRowCountsSha256",
        "sourceSnapshotExactlyMatched",
        "sourceJarSha256",
        "adminRootDependencyAdoptionReceiptSha256",
        "approvalReceiptSha256",
        "runnerSha256",
        "backupWorkerSha256",
        "businessDatabaseChanged",
        "serviceChanged",
        "officialExpertsPackageChanged",
    }
)


def utc_now():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="milliseconds").replace(
        "+00:00", "Z"
    )


def canonical_json(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def static_control_facts(facts):
    return {
        field: value
        for field, value in facts.items()
        if field not in ATTRIBUTION_DATA_FACT_FIELDS
    }


def attribution_observations_compatible(recorded_facts, current_facts):
    if not isinstance(recorded_facts, dict) or not isinstance(
        current_facts, dict
    ):
        return False
    if recorded_facts.get("w1a043State") != current_facts.get(
        "w1a043State"
    ):
        return False
    state = current_facts.get("w1a043State")
    if any(
        type(recorded_facts.get(field)) is not int
        or recorded_facts[field] < 0
        or type(current_facts.get(field)) is not int
        or current_facts[field] < recorded_facts[field]
        for field in ATTRIBUTION_DATA_FACT_FIELDS
    ):
        return False
    if state == "ABSENT":
        return all(
            recorded_facts[field] == 0 and current_facts[field] == 0
            for field in ATTRIBUTION_DATA_FACT_FIELDS
        )
    if state != "EXACT_043_RETAINED_DORMANT":
        return False
    return all(
        facts["attributionEventCount"]
        == facts["attributionProbeEventCount"]
        and facts["attributionNaturalEventCount"] == 0
        and facts["attributionNonProbeEventCount"] == 0
        and facts["attributionAuthoritativeProductCreditCount"] == 0
        and facts["attributionJourneyCount"]
        == facts["attributionProbeJourneyCount"]
        and facts["attributionNaturalJourneyCount"] == 0
        and facts["attributionNonProbeJourneyCount"] == 0
        for facts in (recorded_facts, current_facts)
    )


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


def open_host_change_lock():
    current = pathlib.Path(HOST_CHANGE_LOCK.anchor)
    for part in HOST_CHANGE_LOCK.parent.parts[1:]:
        current = current / part
        status = current.lstat()
        if (
            stat.S_ISLNK(status.st_mode)
            or not stat.S_ISDIR(status.st_mode)
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_mode & 0o022
        ):
            raise RuntimeError("production lock ancestry is untrusted")
    descriptor = os.open(
        HOST_CHANGE_LOCK,
        os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0),
        0o600,
    )
    status = os.fstat(descriptor)
    if (
        not stat.S_ISREG(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o600
        or status.st_nlink != 1
    ):
        os.close(descriptor)
        raise RuntimeError("production lock custody is invalid")
    return descriptor


def acquire_bounded_flock(
    descriptor,
    timeout_seconds=LOCK_TIMEOUT_SECONDS,
    monotonic=time.monotonic,
    sleeper=time.sleep,
):
    deadline = monotonic() + timeout_seconds
    while True:
        try:
            fcntl.flock(
                descriptor,
                fcntl.LOCK_EX | fcntl.LOCK_NB,
            )
            return
        except OSError as error:
            if error.errno not in (errno.EACCES, errno.EAGAIN):
                raise
            if monotonic() >= deadline:
                raise RuntimeError(
                    "production change lock acquisition timed out"
                ) from error
            sleeper(0.05)


class DatabaseProtectionLease:
    def __init__(self, connection):
        try:
            import pymysql
        except ImportError as error:
            raise RuntimeError(
                "PyMySQL is required for the database protection lease"
            ) from error
        self.connection = pymysql.connect(
            host=connection["host"],
            port=int(connection["port"]),
            user=connection["user"],
            password=connection["password"],
            database=DATABASE,
            charset="utf8mb4",
            autocommit=False,
            connect_timeout=10,
            read_timeout=120,
            write_timeout=120,
        )
        self.named_lock = False
        self.metadata_transaction = False
        self.protected_objects = ()

    def acquire(self, monotonic=time.monotonic):
        deadline = monotonic() + LOCK_TIMEOUT_SECONDS
        with self.connection.cursor() as cursor:
            cursor.execute(
                "SET SESSION lock_wait_timeout=%s",
                (LOCK_TIMEOUT_SECONDS,),
            )
            cursor.execute(
                "SELECT GET_LOCK(%s,%s)",
                (MIGRATION_LOCK_NAME, LOCK_TIMEOUT_SECONDS),
            )
            row = cursor.fetchone()
            if not row or int(row[0] or 0) != 1:
                raise RuntimeError(
                    "W1A migration named lock is unavailable"
                )
            self.named_lock = True
            cursor.execute(
                "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ"
            )
            cursor.execute("SET TRANSACTION READ ONLY")
            cursor.execute(
                "START TRANSACTION WITH CONSISTENT SNAPSHOT"
            )
            self.metadata_transaction = True
            cursor.execute(
                "SELECT table_name,table_type "
                "FROM information_schema.tables "
                "WHERE table_schema=DATABASE() "
                "ORDER BY BINARY table_name"
            )
            objects = tuple(cursor.fetchall())
            if not objects:
                raise RuntimeError(
                    "database metadata protection found no objects"
                )
            for name, table_type in objects:
                remaining = deadline - monotonic()
                if remaining <= 0:
                    raise RuntimeError(
                        "database metadata protection acquisition timed out"
                    )
                if (
                    not isinstance(name, str)
                    or table_type not in ("BASE TABLE", "VIEW")
                ):
                    raise RuntimeError(
                        "database metadata protection object is invalid"
                    )
                quoted = "`{}`".format(name.replace("`", "``"))
                cursor.execute(
                    "SET SESSION lock_wait_timeout=%s",
                    (max(1, math.ceil(remaining)),),
                )
                cursor.execute(
                    "SELECT 1 FROM {} LIMIT 0".format(quoted)
                )
            self.protected_objects = objects

    def close(self):
        pending_error = None
        try:
            if self.metadata_transaction:
                try:
                    self.connection.rollback()
                except Exception as error:
                    pending_error = error
                finally:
                    self.metadata_transaction = False
            if self.named_lock:
                try:
                    with self.connection.cursor() as cursor:
                        cursor.execute(
                            "SELECT RELEASE_LOCK(%s)",
                            (MIGRATION_LOCK_NAME,),
                        )
                        row = cursor.fetchone()
                        if not row or int(row[0] or 0) != 1:
                            raise RuntimeError(
                                "W1A migration named lock release failed"
                            )
                except Exception as error:
                    if pending_error is None:
                        pending_error = error
                finally:
                    self.named_lock = False
        finally:
            self.connection.close()
        if pending_error is not None:
            raise pending_error


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


def w1a_schema_fingerprint(mysql):
    statement = """SELECT row_value FROM (
      SELECT CONCAT_WS('|','C',HEX(table_name),LPAD(ordinal_position,3,'0'),
        HEX(column_name),HEX(column_type),is_nullable,
        HEX(COALESCE(column_default,'<NULL>')),HEX(extra),
        HEX(COALESCE(character_set_name,'')),HEX(COALESCE(collation_name,'')),
        HEX(COALESCE(generation_expression,''))) AS row_value
      FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','I',HEX(table_name),HEX(index_name),non_unique,
        LPAD(seq_in_index,3,'0'),HEX(column_name),COALESCE(sub_part,''),
        HEX(COALESCE(collation,'')),HEX(index_type),HEX(nullable))
      FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','T',HEX(table_name),HEX(constraint_name),
        HEX(constraint_type))
      FROM information_schema.table_constraints
      WHERE table_schema=DATABASE() AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','K',HEX(table_name),HEX(constraint_name),
        HEX(column_name),LPAD(ordinal_position,3,'0'),
        HEX(COALESCE(referenced_table_name,'')),
        HEX(COALESCE(referenced_column_name,'')))
      FROM information_schema.key_column_usage
      WHERE table_schema=DATABASE() AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','F',HEX(constraint_name),HEX(table_name),
        HEX(referenced_table_name),HEX(update_rule),HEX(delete_rule),
        HEX(match_option))
      FROM information_schema.referential_constraints
      WHERE constraint_schema=DATABASE() AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','H',HEX(tc.table_name),HEX(cc.constraint_name),
        HEX(cc.check_clause))
      FROM information_schema.check_constraints cc
      JOIN information_schema.table_constraints tc
        ON tc.constraint_schema=cc.constraint_schema
       AND tc.constraint_name=cc.constraint_name
       AND tc.constraint_type='CHECK'
      WHERE tc.table_schema=DATABASE() AND tc.table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','R',HEX(trigger_name),HEX(event_manipulation),
        HEX(event_object_table),HEX(action_timing),HEX(action_orientation),
        HEX(REGEXP_REPLACE(TRIM(action_statement),'[[:space:]]+',' ')))
      FROM information_schema.triggers
      WHERE trigger_schema=DATABASE() AND event_object_table IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      UNION ALL
      SELECT CONCAT_WS('|','M',HEX(permission.menu_name),
        LPAD(permission.order_num,6,'0'),HEX(COALESCE(permission.path,'')),
        HEX(COALESCE(permission.component,'<NULL>')),
        HEX(COALESCE(permission.query,'<NULL>')),
        HEX(COALESCE(permission.route_name,'')),permission.is_frame,
        permission.is_cache,HEX(permission.menu_type),HEX(permission.visible),
        HEX(permission.status),HEX(permission.perms),HEX(permission.icon),
        HEX(COALESCE(root.menu_name,'<NULL>')),
        LPAD(COALESCE(root.order_num,-1),6,'0'),
        HEX(COALESCE(root.path,'<NULL>')),
        HEX(COALESCE(root.component,'<NULL>')),
        HEX(COALESCE(root.query,'<NULL>')),
        HEX(COALESCE(root.route_name,'<NULL>')),
        COALESCE(root.is_frame,-1),COALESCE(root.is_cache,-1),
        HEX(COALESCE(root.menu_type,'<NULL>')),
        HEX(COALESCE(root.visible,'<NULL>')),
        HEX(COALESCE(root.status,'<NULL>')),
        HEX(COALESCE(root.perms,'<NULL>')),
        HEX(COALESCE(root.icon,'<NULL>')),
        IF(root.parent_id=0,'ROOT','NONROOT'))
      FROM sys_menu permission
      LEFT JOIN sys_menu root ON root.menu_id=permission.parent_id
      WHERE BINARY permission.perms=BINARY 'board:attribution:query'
    ) AS fingerprint_rows
    ORDER BY BINARY row_value"""
    rows = mysql.query(statement)
    return sha256_bytes((rows + "\n").encode("utf-8"))


def admin_root_dependency_facts(mysql):
    forbidden_versions = ",".join(
        "'public_init_{:03d}'".format(index)
        for index in range(1, 43)
    )
    root_identity = (
        "(HEX(menu_name)='E78BACE891A3E4BC9AE7AEA1E79086' "
        "OR BINARY path=BINARY 'independent-board-admin' "
        "OR BINARY route_name=BINARY 'IndependentBoardAdmin')"
    )
    exact_root = (
        "HEX(menu_name)='E78BACE891A3E4BC9AE7AEA1E79086' "
        "AND parent_id=0 AND order_num=5 "
        "AND BINARY path=BINARY 'independent-board-admin' "
        "AND component IS NULL AND query IS NULL "
        "AND BINARY route_name=BINARY 'IndependentBoardAdmin' "
        "AND is_frame=1 AND is_cache=0 "
        "AND BINARY menu_type=BINARY 'M' "
        "AND BINARY visible=BINARY '0' "
        "AND BINARY status=BINARY '0' "
        "AND BINARY COALESCE(perms,'')=BINARY '' "
        "AND BINARY icon=BINARY 'peoples'"
    )
    scalar = lambda sql: int(mysql.query(sql))
    version_count = scalar(
        "SELECT COUNT(*) FROM u3w_schema_migration WHERE version='"
        + DEPENDENCY_VERSION + "'"
    )
    receipt_count = scalar(
        "SELECT COUNT(*) FROM u3w_schema_migration WHERE version='"
        + DEPENDENCY_VERSION + "' AND description='"
        + DEPENDENCY_DESCRIPTION + "'"
    )
    identity_count = scalar(
        "SELECT COUNT(*) FROM sys_menu WHERE " + root_identity
    )
    exact_count = scalar(
        "SELECT COUNT(*) FROM sys_menu WHERE " + exact_root
    )
    role_count = scalar(
        "SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN "
        "(SELECT menu_id FROM sys_menu WHERE " + exact_root + ")"
    )
    child_count = scalar(
        "SELECT COUNT(*) FROM sys_menu WHERE menu_type IN ('M','C') "
        "AND parent_id IN (SELECT menu_id FROM sys_menu WHERE "
        + exact_root + ")"
    )
    forbidden_count = scalar(
        "SELECT COUNT(*) FROM u3w_schema_migration WHERE version IN ("
        + forbidden_versions + ")"
    )
    public_043_count = scalar(
        "SELECT COUNT(*) FROM u3w_schema_migration "
        "WHERE version='public_init_043'"
    )
    public_043_exact_count = scalar(
        "SELECT COUNT(*) FROM u3w_schema_migration "
        "WHERE version='public_init_043' AND description='"
        + PUBLIC_043_DESCRIPTION + "'"
    )
    internal_043_count = scalar(
        "SELECT COUNT(*) FROM u3w_schema_migration WHERE version="
        "'20260723_independent_board_attribution_v1_043'"
    )
    internal_043_exact_count = scalar(
        "SELECT COUNT(*) FROM u3w_schema_migration WHERE version="
        "'20260723_independent_board_attribution_v1_043' "
        "AND description='" + INTERNAL_043_DESCRIPTION + "'"
    )
    table_count = scalar(
        "SELECT COUNT(*) FROM information_schema.tables "
        "WHERE table_schema=DATABASE() AND table_name IN "
        "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
    )
    trigger_count = scalar(
        "SELECT COUNT(*) FROM information_schema.triggers "
        "WHERE trigger_schema=DATABASE() AND event_object_table IN "
        "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
    )
    permission_count = scalar(
        "SELECT COUNT(*) FROM sys_menu WHERE "
        "BINARY perms=BINARY 'board:attribution:query'"
    )
    ledger_facts = {
        field: 0 for field in ATTRIBUTION_DATA_FACT_FIELDS
    }
    w1a_fingerprint = None
    if table_count == 2:
        ledger_facts = {
            "attributionEventCount": scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1"
            ),
            "attributionProbeEventCount": scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE BINARY traffic_class=BINARY 'PROBE'"
            ),
            "attributionNaturalEventCount": scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE BINARY traffic_class=BINARY 'NATURAL'"
            ),
            "attributionNonProbeEventCount": scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE BINARY traffic_class<>BINARY 'PROBE'"
            ),
            "attributionAuthoritativeProductCreditCount": scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE authoritative_product_credit<>0"
            ),
            "attributionJourneyCount": scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1"
            ),
            "attributionProbeJourneyCount": scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
                "WHERE BINARY traffic_class=BINARY 'PROBE'"
            ),
            "attributionNaturalJourneyCount": scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
                "WHERE BINARY traffic_class=BINARY 'NATURAL'"
            ),
            "attributionNonProbeJourneyCount": scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
                "WHERE BINARY traffic_class<>BINARY 'PROBE'"
            ),
        }
        w1a_fingerprint = w1a_schema_fingerprint(mysql)
    fingerprint_rows = mysql.query(
        "SELECT row_value FROM ("
        "SELECT CONCAT_WS('|','D',HEX(version),HEX(description)) row_value "
        "FROM u3w_schema_migration WHERE version='"
        + DEPENDENCY_VERSION
        + "' UNION ALL SELECT CONCAT_WS('|','R',menu_id,HEX(menu_name),"
        "parent_id,order_num,HEX(COALESCE(path,'')),"
        "HEX(COALESCE(component,'<NULL>')),"
        "HEX(COALESCE(query,'<NULL>')),"
        "HEX(COALESCE(route_name,'')),is_frame,is_cache,HEX(menu_type),"
        "HEX(visible),HEX(status),HEX(COALESCE(perms,'')),HEX(icon)) "
        "FROM sys_menu WHERE " + exact_root + ") rows_ "
        "ORDER BY BINARY row_value"
    )
    facts_sha = sha256_bytes(
        (fingerprint_rows + "\n").encode("utf-8")
    )
    exact_dependency = bool(
        version_count == 1
        and receipt_count == 1
        and identity_count == 1
        and exact_count == 1
        and role_count == 0
        and child_count == 0
        and forbidden_count == 0
        and len([row for row in fingerprint_rows.splitlines() if row]) == 2
    )
    absent_043 = bool(
        public_043_count == 0
        and internal_043_count == 0
        and table_count == 0
        and trigger_count == 0
        and permission_count == 0
        and all(value == 0 for value in ledger_facts.values())
        and w1a_fingerprint is None
    )
    retained_043 = bool(
        public_043_count == 1
        and public_043_exact_count == 1
        and internal_043_count == 1
        and internal_043_exact_count == 1
        and table_count == 2
        and trigger_count == 2
        and permission_count == 1
        and ledger_facts["attributionEventCount"]
            == ledger_facts["attributionProbeEventCount"]
        and ledger_facts["attributionNaturalEventCount"] == 0
        and ledger_facts["attributionNonProbeEventCount"] == 0
        and ledger_facts[
            "attributionAuthoritativeProductCreditCount"
        ] == 0
        and ledger_facts["attributionJourneyCount"]
            == ledger_facts["attributionProbeJourneyCount"]
        and ledger_facts["attributionNaturalJourneyCount"] == 0
        and ledger_facts["attributionNonProbeJourneyCount"] == 0
        and w1a_fingerprint == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )
    if not exact_dependency or not (absent_043 or retained_043):
        raise RuntimeError(
            "admin root dependency or W1A 043 dormant state is not exact"
        )
    return {
        "legacyAdminRootDependencyState":
            "EXACT_CONTROLLED_DEPENDENCY",
        "legacyAdminRootDependencyFactsSha256": facts_sha,
        "legacyAdminRootDependencyVersionCount": version_count,
        "legacyAdminRootDependencyReceiptCount": receipt_count,
        "legacyAdminRootIdentityCount": identity_count,
        "legacyAdminRootExactCount": exact_count,
        "legacyAdminRootRoleBindingCount": role_count,
        "legacyAdminRootPageChildCount": child_count,
        "legacyForbiddenPublicInit001Through042ReceiptCount":
            forbidden_count,
        "publicInit043AnyReceiptCount": public_043_count,
        "attributionInternalReceiptCount": internal_043_count,
        "attributionTableCount": table_count,
        "attributionTriggerCount": trigger_count,
        "attributionPermissionCount": permission_count,
        **ledger_facts,
        "w1aSchemaFingerprintSha256": w1a_fingerprint,
        "w1a043State": (
            "ABSENT"
            if absent_043
            else "EXACT_043_RETAINED_DORMANT"
        ),
    }


def validate_admin_root_dependency_adoption(
    args, source_identity, dependency_facts
):
    resolved = ADMIN_ROOT_DEPENDENCY_RECEIPT.resolve(strict=True)
    status = resolved.stat()
    if (
        resolved.is_symlink()
        or not resolved.is_file()
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o600
        or status.st_nlink != 1
        or sha256_file(resolved)
            != args.admin_root_dependency_adoption_receipt_sha
    ):
        raise RuntimeError(
            "admin root dependency adoption receipt custody drifted"
        )
    receipt = json.loads(resolved.read_text(encoding="utf-8"))
    if (
        receipt.get("schema") != ADMIN_ROOT_DEPENDENCY_SCHEMA
        or receipt.get("sourceCommit") != args.source_commit
        or receipt.get("databaseServerUuid") != source_identity[2]
        or receipt.get("serverVersion") != source_identity[0]
        or receipt.get("adoptionState")
            != "ADOPTED_EXISTING_EXACT_DEPENDENCY"
        or receipt.get("liveFactsSha256")
            != sha256_bytes(
                canonical_json(receipt.get("liveFacts")).encode("utf-8")
            )
    ):
        raise RuntimeError(
            "admin root dependency adoption receipt identity drifted"
        )
    live_facts = receipt.get("liveFacts")
    if (
        not isinstance(live_facts, dict)
        or live_facts.get("databaseServerUuid") != source_identity[2]
        or live_facts.get("serverVersion") != source_identity[0]
        or live_facts.get("rootIdentityCount")
            != dependency_facts["legacyAdminRootIdentityCount"]
        or live_facts.get("exactRootCount")
            != dependency_facts["legacyAdminRootExactCount"]
        or live_facts.get("rootRoleBindingCount")
            != dependency_facts["legacyAdminRootRoleBindingCount"]
        or live_facts.get("rootPageChildCount")
            != dependency_facts["legacyAdminRootPageChildCount"]
        or live_facts.get("dependencyVersionCount")
            != dependency_facts[
                "legacyAdminRootDependencyVersionCount"
            ]
        or live_facts.get("dependencyReceiptCount")
            != dependency_facts[
                "legacyAdminRootDependencyReceiptCount"
            ]
        or live_facts.get("dependencyRowsFingerprintSha256")
            != dependency_facts[
                "legacyAdminRootDependencyFactsSha256"
            ]
        or live_facts.get(
            "forbiddenPublicInit001Through042ReceiptCount"
        )
            != dependency_facts[
                "legacyForbiddenPublicInit001Through042ReceiptCount"
            ]
        or live_facts.get("publicInit043ReceiptCount")
            != dependency_facts["publicInit043AnyReceiptCount"]
        or live_facts.get("attributionInternalReceiptCount")
            != dependency_facts["attributionInternalReceiptCount"]
        or live_facts.get("attributionTableCount")
            != dependency_facts["attributionTableCount"]
        or live_facts.get("attributionTriggerCount")
            != dependency_facts["attributionTriggerCount"]
        or live_facts.get("attributionPermissionCount")
            != dependency_facts["attributionPermissionCount"]
        or live_facts.get("w1aSchemaFingerprintSha256")
            != dependency_facts["w1aSchemaFingerprintSha256"]
        or live_facts.get("w1a043State")
            != dependency_facts["w1a043State"]
        or not attribution_observations_compatible(
            live_facts, dependency_facts
        )
    ):
        raise RuntimeError(
            "admin root dependency adoption live facts drifted"
        )
    return receipt


def collect_facts(mysql):
    identity = mysql.query(
        "SELECT @@version,@@version_comment,@@server_uuid"
    ).split("\t")
    if (
        len(identity) != 3
        or not MYSQL_VERSION_PATTERN.fullmatch(identity[0])
        or not UUID_PATTERN.fullmatch(identity[2])
    ):
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
        WHERE HEX(menu_name)='E78BACE891A3E4BC9AE7AEA1E79086'
           OR BINARY path=BINARY 'independent-board-admin'
           OR BINARY route_name=BINARY 'IndependentBoardAdmin'
        ORDER BY BINARY menu_name,BINARY path"""
    )
    sys_menu_shape = mysql.query(
        """SELECT CONCAT_WS('|',LPAD(ordinal_position,6,'0'),HEX(column_name),
        HEX(column_type),HEX(is_nullable),HEX(COALESCE(column_default,'<NULL>')),
        HEX(extra)) FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='sys_menu'
        ORDER BY ordinal_position"""
    )
    dependency_facts = admin_root_dependency_facts(mysql)
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
    facts.update(dependency_facts)
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
        args.plan_receipt_sha,
        args.runner_sha,
        args.worker_sha,
        args.verifier_sha,
        args.admin_root_dependency_adoption_receipt_sha,
    ):
        if not SHA_PATTERN.fullmatch(value):
            raise RuntimeError("invalid provenance digest")
    if args.admin_root_dependency_adoption_receipt_sha == "0" * 64:
        raise RuntimeError(
            "admin root dependency adoption receipt is required"
        )
    if args.mode in ("ProvisionKey", "Backup"):
        if (
            args.plan_receipt_sha == "0" * 64
            or not args.plan_json_base64
        ):
            raise RuntimeError(
                "mutating backup requires an immutable Plan receipt"
            )
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
        "expectedAdminRootDependencyAdoptionReceiptSha256",
        "expectedBackupPlanReceiptSha256",
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
        or approval[
            "expectedAdminRootDependencyAdoptionReceiptSha256"
        ] != args.admin_root_dependency_adoption_receipt_sha
        or approval["expectedBackupPlanReceiptSha256"]
            != args.plan_receipt_sha
        or approved_at > now
        or expires_at <= now
        or expires_at - approved_at > dt.timedelta(hours=24)
    ):
        raise RuntimeError("approval receipt identity, scope or expiry is invalid")


def current_plan_target(
    args,
    mysql,
    connection,
    identity=None,
    facts=None,
):
    if identity is None or facts is None:
        identity, facts = collect_facts(mysql)
    validate_admin_root_dependency_adoption(args, identity, facts)
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
        "sourceDatabaseServerUuid": identity[2],
        "serverVersion": identity[0],
        "serverVersionComment": identity[1],
        "sourceFacts": facts,
        "sourceTotalRows": manifest["totalRows"],
        "sourceTableRowCountsSha256": manifest["tableRowCountsSha256"],
        "sourceJarSha256": jar_sha,
        "encryptionContract": "u3w.gnupg-aes256-symmetric.v1",
        "encryptionKeyFingerprintSha256": key_fingerprint,
        "encryptionKeyReady": key_ready,
        "plannedDdlProtectionMode": DDL_PROTECTION_MODE,
        "databaseProtectionActive": False,
        "sourceSnapshotExactlyMatched": False,
        "dumpToolVersion": dump_version,
        "wouldWritePath": str(BACKUP_ROOT / args.run_id),
        "names": names,
        "identity": identity,
        "facts": facts,
        "manifest": manifest,
        "jarSha256": jar_sha,
    }


def run_plan(args, mysql, connection):
    free_bytes, memory_bytes = assert_root_and_capacity()
    target = current_plan_target(args, mysql, connection)
    generated_at = dt.datetime.now(dt.timezone.utc)
    plan = {
        "schema": PLAN_SCHEMA,
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "database": DATABASE,
        "sourceControlFacts": static_control_facts(
            target["sourceFacts"]
        ),
        **{
            field: target[field]
            for field in (
                "sourceDatabaseServerUuid",
                "serverVersion",
                "serverVersionComment",
                "sourceJarSha256",
                "encryptionContract",
                "encryptionKeyFingerprintSha256",
                "encryptionKeyReady",
                "plannedDdlProtectionMode",
                "databaseProtectionActive",
                "sourceSnapshotExactlyMatched",
                "dumpToolVersion",
                "wouldWritePath",
            )
        },
        "freeBytes": free_bytes,
        "availableMemoryBytes": memory_bytes,
        "businessDatabaseWouldChange": False,
        "serviceWouldChange": False,
        "officialExpertsPackageWouldChange": False,
        "runnerSha256": args.runner_sha,
        "backupWorkerSha256": args.worker_sha,
        "verifierSha256": args.verifier_sha,
        "adminRootDependencyAdoptionReceiptSha256":
            args.admin_root_dependency_adoption_receipt_sha,
        "generatedAt": generated_at.isoformat().replace("+00:00", "Z"),
        "expiresAt": (
            generated_at + dt.timedelta(hours=24)
        ).isoformat().replace("+00:00", "Z"),
    }
    if set(plan) != PLAN_FIELDS:
        raise RuntimeError("backup Plan field contract drifted")
    return plan


def decode_plan_receipt(args):
    try:
        payload = base64.b64decode(
            args.plan_json_base64,
            validate=True,
        )
        plan = json.loads(payload.decode("utf-8"))
    except Exception as error:
        raise RuntimeError("backup Plan receipt cannot be decoded") from error
    if (
        sha256_bytes(payload) != args.plan_receipt_sha
        or payload != (canonical_json(plan) + "\n").encode("utf-8")
        or not isinstance(plan, dict)
        or set(plan) != PLAN_FIELDS
        or not isinstance(plan.get("sourceControlFacts"), dict)
        or set(plan["sourceControlFacts"])
            != FACT_FIELDS.difference(ATTRIBUTION_DATA_FACT_FIELDS)
    ):
        raise RuntimeError("backup Plan receipt bytes are not immutable exact")
    try:
        generated_at = dt.datetime.fromisoformat(
            plan["generatedAt"].replace("Z", "+00:00")
        )
        expires_at = dt.datetime.fromisoformat(
            plan["expiresAt"].replace("Z", "+00:00")
        )
    except Exception as error:
        raise RuntimeError("backup Plan validity window is invalid") from error
    now = dt.datetime.now(dt.timezone.utc)
    if (
        plan.get("schema") != PLAN_SCHEMA
        or plan.get("runId") != args.run_id
        or plan.get("sourceCommit") != args.source_commit
        or plan.get("targetHost") != TARGET_HOST
        or plan.get("database") != DATABASE
        or plan.get("runnerSha256") != args.runner_sha
        or plan.get("backupWorkerSha256") != args.worker_sha
        or plan.get("verifierSha256") != args.verifier_sha
        or plan.get(
            "adminRootDependencyAdoptionReceiptSha256"
        ) != args.admin_root_dependency_adoption_receipt_sha
        or generated_at > now
        or expires_at <= now
        or expires_at - generated_at > dt.timedelta(hours=24)
        or type(plan.get("freeBytes")) is not int
        or plan["freeBytes"] < MIN_FREE_BYTES
        or type(plan.get("availableMemoryBytes")) is not int
        or plan["availableMemoryBytes"] < MIN_AVAILABLE_MEMORY_BYTES
        or plan.get("businessDatabaseWouldChange") is not False
        or plan.get("serviceWouldChange") is not False
        or plan.get("officialExpertsPackageWouldChange") is not False
    ):
        raise RuntimeError("backup Plan identity, scope or expiry is invalid")
    return plan


def validate_plan_against_live(
    args,
    mysql,
    connection,
    identity=None,
    facts=None,
):
    plan = decode_plan_receipt(args)
    assert_root_and_capacity()
    target = current_plan_target(
        args,
        mysql,
        connection,
        identity=identity,
        facts=facts,
    )
    target_fields = (
        "sourceDatabaseServerUuid",
        "serverVersion",
        "serverVersionComment",
        "sourceJarSha256",
        "encryptionContract",
        "encryptionKeyFingerprintSha256",
        "encryptionKeyReady",
        "plannedDdlProtectionMode",
        "databaseProtectionActive",
        "sourceSnapshotExactlyMatched",
        "dumpToolVersion",
        "wouldWritePath",
    )
    if (
        plan.get("sourceControlFacts")
            != static_control_facts(target["sourceFacts"])
        or any(plan.get(field) != target[field] for field in target_fields)
    ):
        raise RuntimeError("backup target drifted after approved Plan")
    return plan, target


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
    if (
        set(receipt) != BACKUP_RECEIPT_FIELDS
        or not isinstance(receipt.get("sourceFacts"), dict)
        or set(receipt["sourceFacts"]) != FACT_FIELDS
        or not isinstance(receipt.get("sourceTotalRows"), int)
        or receipt["sourceTotalRows"] < 0
        or not SHA_PATTERN.fullmatch(
            str(receipt.get("sourceTableRowCountsSha256") or "")
        )
    ):
        raise RuntimeError("existing receipt has missing or unknown fields")
    backup_path = receipt_path.parent / "fbsir.sql.gpg"
    required = {
        "schema": BACKUP_SCHEMA,
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "planReceiptSha256": args.plan_receipt_sha,
        "runnerSha256": args.runner_sha,
        "backupWorkerSha256": args.worker_sha,
        "adminRootDependencyAdoptionReceiptSha256":
            args.admin_root_dependency_adoption_receipt_sha,
        "encryptionContract": "u3w.gnupg-aes256-symmetric.v1",
        "encryptionKeyFingerprintSha256": backup_key_fingerprint(),
        "ddlProtectionMode": DDL_PROTECTION_MODE,
        "sourceSnapshotExactlyMatched": False,
        "businessDatabaseChanged": False,
        "serviceChanged": False,
        "officialExpertsPackageChanged": False,
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
    lock_descriptor = open_host_change_lock()
    database_lease = None
    try:
        acquire_bounded_flock(lock_descriptor)
        validate_approval(args)
        database_lease = DatabaseProtectionLease(connection)
        database_lease.acquire()
        validate_approval(args)
        locked_identity, locked_facts = collect_facts(mysql)
        validate_admin_root_dependency_adoption(
            args, locked_identity, locked_facts
        )
        validate_approval(args)
        _, plan_target = validate_plan_against_live(
            args,
            mysql,
            connection,
            identity=locked_identity,
            facts=locked_facts,
        )
        validate_approval(args)
        run_directory = safe_run_directory(args.run_id)
        receipt_path = run_directory / "backup-receipt.json"
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
        identity_before = plan_target["identity"]
        facts_before = plan_target["facts"]
        jar_sha_before = plan_target["jarSha256"]
        names = plan_target["names"]
        manifest_before = plan_target["manifest"]
        dump_version = plan_target["dumpToolVersion"]
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
        validate_admin_root_dependency_adoption(
            args, identity_after, facts_after
        )
        jar_sha_after = sha256_file(JAR_PATH)
        if (
            identity_before != identity_after
            or static_control_facts(facts_before)
                != static_control_facts(facts_after)
            or jar_sha_before != jar_sha_after
        ):
            raise RuntimeError(
                "source identity, static control facts or active JAR "
                "changed during backup window"
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
            "sourceDatabaseServerUuid": identity_before[2],
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
            "ddlProtectionMode": DDL_PROTECTION_MODE,
            "sourceFacts": facts_before,
            "sourceTotalRows": manifest_before["totalRows"],
            "sourceTableRowCountsSha256": manifest_before[
                "tableRowCountsSha256"
            ],
            "sourceSnapshotExactlyMatched": False,
            "sourceJarSha256": jar_sha_before,
            "approvalReceiptSha256": args.approval_sha,
            "planReceiptSha256": args.plan_receipt_sha,
            "runnerSha256": args.runner_sha,
            "backupWorkerSha256": args.worker_sha,
            "adminRootDependencyAdoptionReceiptSha256":
                args.admin_root_dependency_adoption_receipt_sha,
            "businessDatabaseChanged": False,
            "serviceChanged": False,
            "officialExpertsPackageChanged": False,
        }
        if (
            set(receipt) != BACKUP_RECEIPT_FIELDS
            or set(receipt["sourceFacts"]) != FACT_FIELDS
        ):
            raise RuntimeError("backup receipt field contract drifted")
        atomic_json(receipt_path, receipt)
        return receipt
    finally:
        try:
            if database_lease is not None:
                database_lease.close()
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
    parser.add_argument("--plan-receipt-sha", required=True)
    parser.add_argument("--plan-json-base64", default="")
    parser.add_argument("--runner-sha", required=True)
    parser.add_argument("--worker-sha", required=True)
    parser.add_argument("--verifier-sha", required=True)
    parser.add_argument(
        "--admin-root-dependency-adoption-receipt-sha",
        required=True,
    )
    args = parser.parse_args()
    validate_arguments(args)
    connection = load_environment()
    mysql = MysqlClient(connection)
    if args.mode == "Plan":
        result = run_plan(args, mysql, connection)
    elif args.mode == "ProvisionKey":
        lock_descriptor = open_host_change_lock()
        try:
            acquire_bounded_flock(lock_descriptor)
            validate_approval(args)
            identity, facts = collect_facts(mysql)
            validate_admin_root_dependency_adoption(
                args, identity, facts
            )
            validate_plan_against_live(
                args,
                mysql,
                connection,
                identity=identity,
                facts=facts,
            )
            validate_approval(args)
            result = provision_backup_key(args)
        finally:
            try:
                fcntl.flock(lock_descriptor, fcntl.LOCK_UN)
            finally:
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
