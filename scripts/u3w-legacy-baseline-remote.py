#!/usr/bin/env python3
"""Controlled, record-only adoption of the live legacy U3W schema.

The worker is streamed over the pinned SSH channel.  It never fabricates the
public_init_035..042 history and never changes a business table.  Its only
database writes are the two control tables and one immutable adoption receipt.
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
import secrets
import stat
import sys


TARGET_HOST = "api2.u3w.com"
SERVICE_UNIT = "fbsir-admin.service"
DATABASE = "fbsir"
ENV_PATH = pathlib.Path("/etc/u3w/fbsir-admin.env")
JAR_PATH = pathlib.Path("/opt/fbsir/admin/Fbsir-admin/target/fbsir-admin.jar")
BACKUP_BUNDLE_PATH = pathlib.Path(
    "/opt/fbsir/admin/backups/latest/receipt.json"
)
BASELINE_ROOT = pathlib.Path("/opt/fbsir/admin/baselines/w1a")
BASELINE_LATEST = pathlib.Path("/opt/fbsir/admin/baselines/latest")
HOST_CHANGE_LOCK = pathlib.Path(
    "/opt/fbsir/admin/.u3w-production-change.lock"
)
CONTROL_TABLES = (
    "u3w_schema_migration",
    "u3w_legacy_schema_baseline_receipt_v2",
)
BASELINE_MODE = "LEGACY_ADOPTED_W1A_V2"
MIGRATION_VERSION = "legacy_w1a_baseline_20260724_001"
RECEIPT_SCHEMA = "fbsir.u3wLegacyBaselineAdoptionReceipt.v2"
FINGERPRINT_ALGORITHM = "u3w.mysql-schema-metadata.v2"
EXPECTED_CONTROL_OVERLAY_SHA256 = (
    "2dea9bedb7341ea61c8d8f0828e4eee2ac9dba0f5f60fe9094bde589ce896e66"
)
RUN_PATTERN = re.compile(
    r"w1a-baseline-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}"
)
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
UUID_PATTERN = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
)

RECEIPT_FIELDS = frozenset(
    {
        "schema",
        "baselineMode",
        "migrationVersion",
        "migrationDescription",
        "runId",
        "sourceCommit",
        "targetHost",
        "serviceUnit",
        "database",
        "databaseEndpoint",
        "databaseServerUuid",
        "serverVersion",
        "serverVersionComment",
        "fingerprintAlgorithm",
        "sourceFacts",
        "sourceFactsSha256",
        "preAdoptionLiveFacts",
        "postAdoptionBusinessProjectionFacts",
        "controlOverlaySha256",
        "backupRunId",
        "backupSourceCommit",
        "backupBundleReceiptSha256",
        "backupReceiptSha256",
        "restoreReceiptSha256",
        "backupSha256",
        "backupSizeBytes",
        "sourceJarSha256",
        "approvalReceiptSha256",
        "runnerSha256",
        "workerSha256",
        "canonicalHistoryClaimed",
        "publicInit035Through042ReceiptsWritten",
        "productionBusinessStateChanged",
        "productionServiceChanged",
        "officialExpertsPackageChanged",
        "observedAt",
    }
)


def canonical_json(value):
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with pathlib.Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def receipt_digest(payload):
    return sha256_bytes(canonical_json(payload).encode("utf-8"))


def utc_now():
    return (
        dt.datetime.now(dt.timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def validate_receipt_shape(payload):
    if not isinstance(payload, dict) or set(payload) != RECEIPT_FIELDS:
        raise RuntimeError("legacy adoption receipt fields are invalid")
    if (
        payload["schema"] != RECEIPT_SCHEMA
        or payload["baselineMode"] != BASELINE_MODE
        or payload["migrationVersion"] != MIGRATION_VERSION
        or payload["targetHost"] != TARGET_HOST
        or payload["serviceUnit"] != SERVICE_UNIT
        or payload["database"] != DATABASE
        or not re.fullmatch(r"[^:\\s]+:[0-9]{1,5}", payload["databaseEndpoint"])
        or not UUID_PATTERN.fullmatch(payload["databaseServerUuid"])
        or payload["fingerprintAlgorithm"] != FINGERPRINT_ALGORITHM
        or not RUN_PATTERN.fullmatch(str(payload["runId"]))
        or not COMMIT_PATTERN.fullmatch(str(payload["sourceCommit"]))
        or not COMMIT_PATTERN.fullmatch(str(payload["backupSourceCommit"]))
    ):
        raise RuntimeError("legacy adoption receipt identity is invalid")
    digest_fields = (
        "sourceFactsSha256",
        "controlOverlaySha256",
        "backupBundleReceiptSha256",
        "backupReceiptSha256",
        "restoreReceiptSha256",
        "backupSha256",
        "sourceJarSha256",
        "approvalReceiptSha256",
        "runnerSha256",
        "workerSha256",
    )
    if any(
        not SHA_PATTERN.fullmatch(str(payload.get(name, "")))
        for name in digest_fields
    ):
        raise RuntimeError("legacy adoption receipt digest shape is invalid")
    if (
        not isinstance(payload["backupSizeBytes"], int)
        or isinstance(payload["backupSizeBytes"], bool)
        or payload["backupSizeBytes"] <= 0
        or payload["canonicalHistoryClaimed"] is not False
        or payload["publicInit035Through042ReceiptsWritten"] is not False
        or payload["productionBusinessStateChanged"] is not False
        or payload["productionServiceChanged"] is not False
        or payload["officialExpertsPackageChanged"] is not False
    ):
        raise RuntimeError("legacy adoption receipt safety claims are invalid")
    for name in (
        "sourceFacts",
        "preAdoptionLiveFacts",
        "postAdoptionBusinessProjectionFacts",
    ):
        if not isinstance(payload[name], dict):
            raise RuntimeError("legacy adoption receipt fact shape is invalid")
    if payload["sourceFactsSha256"] != sha256_bytes(
        canonical_json(payload["sourceFacts"]).encode("utf-8")
    ):
        raise RuntimeError("legacy adoption source facts digest is invalid")
    return payload


def validate_approval(args, connection, server_uuid, now=None):
    if args.approval_sha == "0" * 64 or not args.approval_json_base64:
        raise RuntimeError("mutating legacy adoption requires approval")
    try:
        raw = base64.b64decode(args.approval_json_base64, validate=True)
        approval = json.loads(raw.decode("utf-8-sig"))
    except (ValueError, UnicodeError, json.JSONDecodeError) as error:
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
        "database",
        "databaseEndpoint",
        "databaseServerUuid",
        "expectedBackupBundleReceiptSha256",
        "runnerSha256",
        "workerSha256",
    }
    recovery = args.mode == "Recover"
    if recovery:
        expected_fields.update(
            {
                "expectedAdoptionReceiptSha256",
                "originalApprovalReceiptSha256",
            }
        )
    if set(approval) != expected_fields:
        raise RuntimeError("approval receipt has missing or unknown fields")
    try:
        approved_at = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        expires_at = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
    except (TypeError, ValueError) as error:
        raise RuntimeError("approval receipt time is invalid") from error
    current = now or dt.datetime.now(dt.timezone.utc)
    if (
        sha256_bytes(raw) != args.approval_sha
        or approval["schema"]
        != "fbsir.u3wProductionChangeApprovalReceipt.v1"
        or approval["action"]
        != (
            "RECOVER_LEGACY_SCHEMA_BASELINE_ANCHOR"
            if recovery
            else "ADOPT_LEGACY_SCHEMA_BASELINE"
        )
        or approval["targetHost"] != TARGET_HOST
        or approval["runId"] != args.run_id
        or approval["sourceCommit"] != args.source_commit
        or approval["database"] != DATABASE
        or approval["databaseEndpoint"]
        != "{}:{}".format(connection["host"], connection["port"])
        or approval["databaseServerUuid"] != server_uuid
        or approval["expectedBackupBundleReceiptSha256"]
        != args.expected_backup_bundle_sha
        or approval["runnerSha256"] != args.runner_sha
        or approval["workerSha256"] != args.worker_sha
        or approval["authorizedBy"] != "workspace-user"
        or approval["concurrentDdlProhibited"] is not True
        or approval["productionFilesystemWrite"] is not True
        or approval["productionDatabaseWrite"] is not (not recovery)
        or approval["productionServiceChange"] is not False
        or approval["officialExpertsPackageChange"] is not False
        or approved_at.tzinfo is None
        or expires_at.tzinfo is None
        or approved_at > current
        or expires_at <= current
        or expires_at - approved_at > dt.timedelta(hours=24)
        or (
            recovery
            and (
                approval["expectedAdoptionReceiptSha256"]
                != args.expected_adoption_receipt_sha
                or approval["originalApprovalReceiptSha256"]
                != args.original_approval_sha
            )
        )
    ):
        raise RuntimeError("approval receipt identity or scope is invalid")
    return approval


def parse_environment():
    status = ENV_PATH.stat()
    if (
        not ENV_PATH.is_file()
        or ENV_PATH.is_symlink()
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o600
        or status.st_nlink != 1
    ):
        raise RuntimeError("database environment custody is invalid")
    values = {}
    for number, raw in enumerate(
        ENV_PATH.read_text(encoding="utf-8").splitlines(), 1
    ):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            raise RuntimeError(
                "invalid environment assignment at line {}".format(number)
            )
        name, value = line.split("=", 1)
        name = name.strip()
        value = value.strip()
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
            raise RuntimeError("invalid environment key")
        if name in values:
            raise RuntimeError("duplicate environment key")
        if (
            len(value) >= 2
            and value[0] == value[-1]
            and value[0] in "\"'"
        ):
            value = value[1:-1]
        values[name] = value
    required = (
        "FBSIR_MYSQL_URL",
        "FBSIR_MYSQL_USERNAME",
        "FBSIR_MYSQL_PASSWORD",
    )
    if any(not values.get(name) for name in required):
        raise RuntimeError("database credentials are absent")
    match = re.fullmatch(
        r"jdbc:mysql://([^/:?]+)(?::([0-9]{1,5}))?/([^?]+)(?:\?.*)?",
        values["FBSIR_MYSQL_URL"],
    )
    if not match or match.group(3) != DATABASE:
        raise RuntimeError("database URL is not the fixed fbsir target")
    return {
        "host": match.group(1),
        "port": int(match.group(2) or "3306"),
        "user": values["FBSIR_MYSQL_USERNAME"],
        "password": values["FBSIR_MYSQL_PASSWORD"],
    }


class Mysql:
    def __init__(self, connection):
        try:
            import pymysql
        except ImportError as error:
            raise RuntimeError("PyMySQL is required on the target host") from error
        self.connection = pymysql.connect(
            host=connection["host"],
            port=connection["port"],
            user=connection["user"],
            password=connection["password"],
            database=DATABASE,
            charset="utf8mb4",
            autocommit=True,
            connect_timeout=10,
            read_timeout=120,
            write_timeout=120,
        )

    def close(self):
        self.connection.close()

    def execute(self, sql, args=None):
        with self.connection.cursor() as cursor:
            cursor.execute(sql, args)
            return cursor.rowcount

    def rows(self, sql, args=None):
        with self.connection.cursor() as cursor:
            cursor.execute(sql, args)
            return list(cursor.fetchall())

    def lines(self, sql, args=None):
        return [str(row[0]) for row in self.rows(sql, args) if row[0] is not None]

    def scalar(self, sql, args=None):
        rows = self.rows(sql, args)
        if len(rows) != 1 or len(rows[0]) != 1:
            raise RuntimeError("expected exactly one scalar database result")
        return rows[0][0]


def projection_predicate(alias=""):
    prefix = alias + "." if alias else ""
    quoted = ",".join("'{}'".format(name) for name in CONTROL_TABLES)
    return "{}table_name NOT IN ({})".format(prefix, quoted)


def metadata_queries():
    table_filter = projection_predicate()
    tc_filter = projection_predicate("tc")
    control_csv = ",".join("'{}'".format(name) for name in CONTROL_TABLES)
    return [
        """SELECT CONCAT_WS('|','T',HEX(table_name),HEX(table_type),
        HEX(COALESCE(engine,'')),HEX(COALESCE(table_collation,'')))
        FROM information_schema.tables WHERE table_schema=DATABASE()
        AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','C',HEX(table_name),LPAD(ordinal_position,6,'0'),
        HEX(column_name),HEX(column_type),HEX(is_nullable),
        HEX(COALESCE(column_default,'<NULL>')),HEX(extra),
        HEX(COALESCE(character_set_name,'')),HEX(COALESCE(collation_name,'')),
        HEX(COALESCE(generation_expression,'')))
        FROM information_schema.columns WHERE table_schema=DATABASE()
        AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','I',HEX(table_name),HEX(index_name),
        LPAD(seq_in_index,6,'0'),non_unique,HEX(COALESCE(column_name,'')),
        HEX(COALESCE(collation,'')),COALESCE(sub_part,-1),
        HEX(COALESCE(index_type,'')),HEX(COALESCE(expression,'')))
        FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','K',HEX(table_name),HEX(constraint_name),
        HEX(constraint_type)) FROM information_schema.table_constraints
        WHERE table_schema=DATABASE() AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','U',HEX(table_name),HEX(constraint_name),
        LPAD(ordinal_position,6,'0'),HEX(COALESCE(column_name,'')),
        HEX(COALESCE(referenced_table_name,'')),
        HEX(COALESCE(referenced_column_name,'')))
        FROM information_schema.key_column_usage
        WHERE table_schema=DATABASE() AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','F',HEX(constraint_name),HEX(table_name),
        HEX(referenced_table_name),HEX(update_rule),HEX(delete_rule),
        HEX(match_option)) FROM information_schema.referential_constraints
        WHERE constraint_schema=DATABASE() AND {} AND
        referenced_table_name NOT IN ({})""".format(table_filter, control_csv),
        """SELECT CONCAT_WS('|','H',HEX(tc.table_name),HEX(cc.constraint_name),
        HEX(cc.check_clause)) FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema=cc.constraint_schema
         AND tc.constraint_name=cc.constraint_name
         AND tc.constraint_type='CHECK'
        WHERE tc.table_schema=DATABASE() AND {}""".format(tc_filter),
        """SELECT CONCAT_WS('|','V',HEX(table_name),HEX(view_definition),
        HEX(check_option),HEX(is_updatable),HEX(definer),HEX(security_type),
        HEX(character_set_client),HEX(collation_connection))
        FROM information_schema.views WHERE table_schema=DATABASE()
        AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','G',HEX(trigger_name),HEX(event_manipulation),
        HEX(event_object_table),HEX(action_timing),HEX(action_orientation),
        HEX(action_statement),HEX(definer))
        FROM information_schema.triggers WHERE trigger_schema=DATABASE()
        AND event_object_table NOT IN ({})""".format(control_csv),
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
        FROM information_schema.partitions WHERE table_schema=DATABASE()
        AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','A',
        IF(specific_name IS NULL,'N',CONCAT('V',HEX(specific_name))),
        LPAD(ordinal_position,6,'0'),
        IF(parameter_mode IS NULL,'N',CONCAT('V',HEX(parameter_mode))),
        IF(parameter_name IS NULL,'N',CONCAT('V',HEX(parameter_name))),
        HEX(data_type),HEX(dtd_identifier))
        FROM information_schema.parameters WHERE specific_schema=DATABASE()""",
    ]


def metadata_rows(mysql):
    rows = []
    for query in metadata_queries():
        rows.extend(mysql.lines(query))
    return sorted(rows)


def collect_projection_facts(mysql):
    identity = mysql.rows("SELECT @@version,@@version_comment")
    if len(identity) != 1 or len(identity[0]) != 2:
        raise RuntimeError("unexpected MySQL identity")
    version, version_comment = map(str, identity[0])
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise RuntimeError("unexpected MySQL version")
    controls = ",".join("'{}'".format(name) for name in CONTROL_TABLES)
    counts = mysql.rows(
        """SELECT
        SUM(table_type='BASE TABLE'),SUM(table_type='VIEW'),
        SUM(table_type='BASE TABLE' AND engine<>'InnoDB')
        FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name NOT IN ({})""".format(controls)
    )[0]
    object_counts = mysql.rows(
        """SELECT
        (SELECT COUNT(*) FROM information_schema.triggers
          WHERE trigger_schema=DATABASE()
          AND event_object_table NOT IN ({})),
        (SELECT COUNT(*) FROM information_schema.routines
          WHERE routine_schema=DATABASE()),
        (SELECT COUNT(*) FROM information_schema.events
          WHERE event_schema=DATABASE())""".format(controls)
    )[0]
    root_rows = "\n".join(
        mysql.lines(
            """SELECT CONCAT_WS('|',HEX(menu_name),parent_id,
            HEX(COALESCE(path,'')),HEX(COALESCE(component,'')),
            HEX(COALESCE(perms,''))) FROM sys_menu
            WHERE parent_id=0 AND HEX(menu_name) IN (
              'E78BACE891A3E4BC9A',
              '496E646570656E64656E7420426F617264')
            ORDER BY BINARY menu_name,BINARY path"""
        )
    )
    sys_menu_shape = "\n".join(
        mysql.lines(
            """SELECT CONCAT_WS('|',LPAD(ordinal_position,6,'0'),
            HEX(column_name),HEX(column_type),HEX(is_nullable),
            HEX(COALESCE(column_default,'<NULL>')),HEX(extra))
            FROM information_schema.columns
            WHERE table_schema=DATABASE() AND table_name='sys_menu'
            ORDER BY ordinal_position"""
        )
    )
    facts = {
        "fingerprintAlgorithm": FINGERPRINT_ALGORITHM,
        "schemaFingerprintSha256": sha256_bytes(
            ("\n".join(metadata_rows(mysql)) + "\n").encode()
        ),
        "prerequisiteShapeSha256": sha256_bytes(
            (
                sys_menu_shape
                + "\n--ROOTS--\n"
                + root_rows
                + "\n"
            ).encode()
        ),
        "baseTableCount": int(counts[0] or 0),
        "viewCount": int(counts[1] or 0),
        "triggerCount": int(object_counts[0] or 0),
        "routineCount": int(object_counts[1] or 0),
        "eventCount": int(object_counts[2] or 0),
        "independentBoardAdminRootCount": len(
            [row for row in root_rows.splitlines() if row]
        ),
        "allBaseTablesInnoDB": int(counts[2] or 0) == 0,
    }
    if facts["baseTableCount"] < 1 or not facts["allBaseTablesInnoDB"]:
        raise RuntimeError("legacy projection must be non-empty and all InnoDB")
    return (version, version_comment), facts


def control_overlay_rows(mysql):
    controls = ",".join("'{}'".format(name) for name in CONTROL_TABLES)
    queries = [
        """SELECT CONCAT_WS('|','T',HEX(table_name),HEX(table_type),
        HEX(COALESCE(engine,'')),HEX(COALESCE(table_collation,'')),
        HEX(COALESCE(create_options,'')),HEX(COALESCE(table_comment,'')))
        FROM information_schema.tables WHERE table_schema=DATABASE()
        AND table_name IN ({})""".format(controls),
        """SELECT CONCAT_WS('|','C',HEX(table_name),LPAD(ordinal_position,6,'0'),
        HEX(column_name),HEX(column_type),HEX(is_nullable),
        HEX(COALESCE(column_default,'<NULL>')),HEX(extra),
        HEX(COALESCE(character_set_name,'')),HEX(COALESCE(collation_name,'')),
        HEX(COALESCE(generation_expression,'')))
        FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name IN ({})""".format(controls),
        """SELECT CONCAT_WS('|','I',HEX(table_name),HEX(index_name),
        LPAD(seq_in_index,6,'0'),non_unique,HEX(COALESCE(column_name,'')),
        HEX(COALESCE(collation,'')),COALESCE(sub_part,-1),
        HEX(COALESCE(index_type,'')),HEX(COALESCE(expression,'')))
        FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name IN ({})""".format(controls),
        """SELECT CONCAT_WS('|','K',HEX(table_name),HEX(constraint_name),
        HEX(constraint_type),HEX(COALESCE(enforced,'')))
        FROM information_schema.table_constraints
        WHERE table_schema=DATABASE() AND table_name IN ({})""".format(
            controls
        ),
        """SELECT CONCAT_WS('|','U',HEX(table_name),HEX(constraint_name),
        LPAD(ordinal_position,6,'0'),HEX(COALESCE(column_name,'')),
        COALESCE(position_in_unique_constraint,-1),
        HEX(COALESCE(referenced_table_schema,'')),
        HEX(COALESCE(referenced_table_name,'')),
        HEX(COALESCE(referenced_column_name,'')))
        FROM information_schema.key_column_usage
        WHERE table_schema=DATABASE() AND table_name IN ({})""".format(
            controls
        ),
        """SELECT CONCAT_WS('|','F',HEX(constraint_name),HEX(table_name),
        HEX(COALESCE(unique_constraint_schema,'')),
        HEX(COALESCE(unique_constraint_name,'')),
        HEX(COALESCE(referenced_table_name,'')),HEX(update_rule),
        HEX(delete_rule),HEX(match_option))
        FROM information_schema.referential_constraints
        WHERE constraint_schema=DATABASE() AND table_name IN ({})""".format(
            controls
        ),
        """SELECT CONCAT_WS('|','H',HEX(tc.table_name),HEX(cc.constraint_name),
        HEX(cc.check_clause)) FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema=cc.constraint_schema
         AND tc.constraint_name=cc.constraint_name
         AND tc.constraint_type='CHECK'
        WHERE tc.table_schema=DATABASE() AND tc.table_name IN ({})""".format(
            controls
        ),
        """SELECT CONCAT_WS('|','G',HEX(trigger_name),HEX(event_manipulation),
        HEX(event_object_table),HEX(action_timing),HEX(action_orientation),
        HEX(action_statement),HEX(definer))
        FROM information_schema.triggers WHERE trigger_schema=DATABASE()
        AND event_object_table IN ({})""".format(controls),
        """SELECT CONCAT_WS('|','P',HEX(table_name),
        IF(partition_name IS NULL,'N',CONCAT('V',HEX(partition_name))),
        IF(subpartition_name IS NULL,'N',CONCAT('V',HEX(subpartition_name))),
        LPAD(partition_ordinal_position,6,'0'),
        IF(subpartition_ordinal_position IS NULL,'N',
          CONCAT('V',LPAD(subpartition_ordinal_position,6,'0'))),
        IF(partition_method IS NULL,'N',CONCAT('V',HEX(partition_method))),
        IF(subpartition_method IS NULL,'N',
          CONCAT('V',HEX(subpartition_method))),
        IF(partition_expression IS NULL,'N',
          CONCAT('V',HEX(partition_expression))),
        IF(subpartition_expression IS NULL,'N',
          CONCAT('V',HEX(subpartition_expression))),
        IF(partition_description IS NULL,'N',
          CONCAT('V',HEX(partition_description))))
        FROM information_schema.partitions WHERE table_schema=DATABASE()
        AND table_name IN ({})""".format(controls),
    ]
    rows = []
    for query in queries:
        rows.extend(mysql.lines(query))
    return sorted(rows)


def control_overlay_sha256(mysql):
    rows = control_overlay_rows(mysql)
    if not rows:
        raise RuntimeError("control overlay is absent")
    return sha256_bytes(("\n".join(rows) + "\n").encode())


def validate_regular_file(path, expected_parent=None, mode=0o600):
    path = pathlib.Path(path)
    if path.is_symlink() or not path.is_file():
        raise RuntimeError("artifact path or type is invalid")
    status = path.stat()
    if (
        status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != mode
        or status.st_nlink != 1
    ):
        raise RuntimeError("artifact custody is invalid")
    if expected_parent is not None and path.resolve().parent != pathlib.Path(
        expected_parent
    ).resolve():
        raise RuntimeError("artifact parent is invalid")
    return path


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
            raise RuntimeError("production change lock ancestry is untrusted")
    flags = os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(HOST_CHANGE_LOCK, flags, 0o600)
    status = os.fstat(descriptor)
    if (
        not stat.S_ISREG(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink != 1
    ):
        os.close(descriptor)
        raise RuntimeError("production change lock custody is invalid")
    os.fchmod(descriptor, 0o600)
    return descriptor


def secure_directory(path, mode=0o700):
    path = pathlib.Path(path)
    if not path.is_absolute():
        raise RuntimeError("baseline directory must be absolute")
    current = pathlib.Path(path.anchor)
    for part in path.parts[1:]:
        current = current / part
        if current.exists() or current.is_symlink():
            status = current.lstat()
            if (
                stat.S_ISLNK(status.st_mode)
                or not stat.S_ISDIR(status.st_mode)
                or status.st_uid != 0
                or status.st_gid != 0
                or (
                    current != path
                    and status.st_mode & 0o022
                )
            ):
                raise RuntimeError("baseline directory ancestry is untrusted")
        else:
            os.mkdir(current, mode if current == path else 0o700)
    status = path.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
    ):
        raise RuntimeError("baseline directory custody is invalid")
    os.chmod(path, mode)
    return path


def validate_backup_bundle(expected_sha, bundle_path=BACKUP_BUNDLE_PATH):
    if not SHA_PATTERN.fullmatch(expected_sha):
        raise RuntimeError("backup bundle anchor is invalid")
    resolved = pathlib.Path(bundle_path).resolve(strict=True)
    validate_regular_file(resolved)
    if sha256_file(resolved) != expected_sha:
        raise RuntimeError("backup bundle receipt anchor mismatch")
    bundle = json.loads(resolved.read_text(encoding="utf-8"))
    expected_bundle_fields = {
        "schema",
        "runId",
        "sourceCommit",
        "targetHost",
        "database",
        "runnerSha256",
        "backupWorkerSha256",
        "verifierSha256",
        "approvalReceiptSha256",
        "backupPath",
        "backupSha256",
        "backupSizeBytes",
        "backupReceiptPath",
        "backupReceiptSha256",
        "restoreReceiptPath",
        "restoreReceiptSha256",
        "productionBusinessStateChanged",
        "generatedAt",
    }
    if (
        set(bundle) != expected_bundle_fields
        or bundle["schema"]
        != "fbsir.u3wDatabaseBackupRestoreBundleReceipt.v1"
        or bundle["targetHost"] != TARGET_HOST
        or bundle["database"] != DATABASE
        or bundle["productionBusinessStateChanged"] is not False
        or not COMMIT_PATTERN.fullmatch(str(bundle["sourceCommit"]))
    ):
        raise RuntimeError("backup bundle receipt identity is invalid")
    if not re.fullmatch(
        r"w1a-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}",
        str(bundle["runId"]),
    ):
        raise RuntimeError("backup run identity is invalid")
    run_directory = pathlib.Path(
        "/opt/fbsir/admin/backups/w1a", bundle["runId"]
    ).resolve()
    if resolved.parent != run_directory:
        raise RuntimeError("backup bundle directory binding is invalid")
    backup_path = validate_regular_file(
        bundle["backupPath"], run_directory
    )
    backup_receipt_path = validate_regular_file(
        bundle["backupReceiptPath"], run_directory
    )
    restore_receipt_path = validate_regular_file(
        bundle["restoreReceiptPath"], run_directory
    )
    if (
        sha256_file(backup_path) != bundle["backupSha256"]
        or backup_path.stat().st_size != bundle["backupSizeBytes"]
        or sha256_file(backup_receipt_path)
        != bundle["backupReceiptSha256"]
        or sha256_file(restore_receipt_path)
        != bundle["restoreReceiptSha256"]
    ):
        raise RuntimeError("backup bundle artifact binding is invalid")
    source = json.loads(backup_receipt_path.read_text(encoding="utf-8"))
    restore = json.loads(restore_receipt_path.read_text(encoding="utf-8"))
    source_facts = source.get("sourceFacts")
    if (
        source.get("schema") != "fbsir.u3wDatabaseBackupReceipt.v2"
        or source.get("runId") != bundle["runId"]
        or source.get("sourceCommit") != bundle["sourceCommit"]
        or source.get("sourceJarSha256") != sha256_file(JAR_PATH)
        or source.get("backupSha256") != bundle["backupSha256"]
        or source.get("backupSizeBytes") != bundle["backupSizeBytes"]
        or not isinstance(source_facts, dict)
        or restore.get("schema")
        != "fbsir.u3wDatabaseRestoreRehearsalReceipt.v2"
        or restore.get("runId") != bundle["runId"]
        or restore.get("sourceCommit") != bundle["sourceCommit"]
        or restore.get("sourceBackupSha256") != bundle["backupSha256"]
        or restore.get("sourceBackupReceiptSha256")
        != bundle["backupReceiptSha256"]
        or restore.get("restoredFacts") != source_facts
        or restore.get("isolatedTarget") is not True
        or restore.get("isolatedNetworkingDisabled") is not True
        or restore.get("isolatedDataRemoved") is not True
    ):
        raise RuntimeError("backup and isolated restore evidence is invalid")
    return bundle, source, restore


MIGRATION_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `u3w_schema_migration` (
  `version` VARCHAR(96) NOT NULL,
  `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `description` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='U3W可重入数据库迁移记录'
"""

RECEIPT_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS `u3w_legacy_schema_baseline_receipt_v2` (
  `baseline_id` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `adoption_run_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `baseline_mode` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_commit` CHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `backup_run_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `backup_bundle_receipt_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `backup_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `backup_size_bytes` BIGINT UNSIGNED NOT NULL,
  `source_facts_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `approval_receipt_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_jar_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `adoption_runner_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `adoption_worker_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `adoption_receipt_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `canonical_history_claimed` TINYINT NOT NULL,
  `public_init_035_through_042_receipts_written` TINYINT NOT NULL,
  `production_business_state_changed` TINYINT NOT NULL,
  `receipt_json` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `applied_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`baseline_id`),
  UNIQUE KEY `uk_u3w_legacy_baseline_run_v2` (`adoption_run_id`),
  UNIQUE KEY `uk_u3w_legacy_baseline_receipt_v2` (`adoption_receipt_sha256`),
  CONSTRAINT `chk_u3w_legacy_baseline_mode_v2`
    CHECK (`baseline_mode` = 'LEGACY_ADOPTED_W1A_V2'),
  CONSTRAINT `chk_u3w_legacy_baseline_false_claims_v2`
    CHECK (`canonical_history_claimed` = 0
      AND `public_init_035_through_042_receipts_written` = 0
      AND `production_business_state_changed` = 0),
  CONSTRAINT `chk_u3w_legacy_baseline_json_v2`
    CHECK (JSON_VALID(`receipt_json`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='W1A旧库基线采纳不可变回执V2'
"""


EXPECTED_CONTROL_COLUMNS = {
    "u3w_schema_migration": [
        (
            "version",
            "varchar(96)",
            "NO",
            "<NULL>",
            "",
            "utf8mb4",
            "utf8mb4_unicode_ci",
            "",
        ),
        (
            "applied_at",
            "datetime",
            "NO",
            "CURRENT_TIMESTAMP",
            "DEFAULT_GENERATED",
            "",
            "",
            "",
        ),
        (
            "description",
            "varchar(255)",
            "NO",
            "<NULL>",
            "",
            "utf8mb4",
            "utf8mb4_unicode_ci",
            "",
        ),
    ],
    "u3w_legacy_schema_baseline_receipt_v2": [
        ("baseline_id", "char(64)", "NO", "<NULL>", "", "ascii", "ascii_bin", ""),
        (
            "adoption_run_id",
            "varchar(64)",
            "NO",
            "<NULL>",
            "",
            "ascii",
            "ascii_bin",
            "",
        ),
        (
            "baseline_mode",
            "varchar(48)",
            "NO",
            "<NULL>",
            "",
            "ascii",
            "ascii_bin",
            "",
        ),
        ("source_commit", "char(40)", "NO", "<NULL>", "", "ascii", "ascii_bin", ""),
        (
            "backup_run_id",
            "varchar(64)",
            "NO",
            "<NULL>",
            "",
            "ascii",
            "ascii_bin",
            "",
        ),
        (
            "backup_bundle_receipt_sha256",
            "char(64)",
            "NO",
            "<NULL>",
            "",
            "ascii",
            "ascii_bin",
            "",
        ),
        ("backup_sha256", "char(64)", "NO", "<NULL>", "", "ascii", "ascii_bin", ""),
        (
            "backup_size_bytes",
            "bigint unsigned",
            "NO",
            "<NULL>",
            "",
            "",
            "",
            "",
        ),
        (
            "source_facts_sha256",
            "char(64)",
            "NO",
            "<NULL>",
            "",
            "ascii",
            "ascii_bin",
            "",
        ),
        (
            "approval_receipt_sha256",
            "char(64)",
            "NO",
            "<NULL>",
            "",
            "ascii",
            "ascii_bin",
            "",
        ),
        (
            "source_jar_sha256",
            "char(64)",
            "NO",
            "<NULL>",
            "",
            "ascii",
            "ascii_bin",
            "",
        ),
        (
            "adoption_runner_sha256",
            "char(64)",
            "NO",
            "<NULL>",
            "",
            "ascii",
            "ascii_bin",
            "",
        ),
        (
            "adoption_worker_sha256",
            "char(64)",
            "NO",
            "<NULL>",
            "",
            "ascii",
            "ascii_bin",
            "",
        ),
        (
            "adoption_receipt_sha256",
            "char(64)",
            "NO",
            "<NULL>",
            "",
            "ascii",
            "ascii_bin",
            "",
        ),
        (
            "canonical_history_claimed",
            "tinyint",
            "NO",
            "<NULL>",
            "",
            "",
            "",
            "",
        ),
        (
            "public_init_035_through_042_receipts_written",
            "tinyint",
            "NO",
            "<NULL>",
            "",
            "",
            "",
            "",
        ),
        (
            "production_business_state_changed",
            "tinyint",
            "NO",
            "<NULL>",
            "",
            "",
            "",
            "",
        ),
        (
            "receipt_json",
            "longtext",
            "NO",
            "<NULL>",
            "",
            "utf8mb4",
            "utf8mb4_bin",
            "",
        ),
        (
            "applied_at",
            "datetime(6)",
            "NO",
            "<NULL>",
            "",
            "",
            "",
            "",
        ),
    ],
}

EXPECTED_CONTROL_INDEXES = [
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "PRIMARY",
        0,
        1,
        "baseline_id",
        "A",
        "<NULL>",
        "<NULL>",
        "",
        "BTREE",
        "",
        "",
        "YES",
        "<NULL>",
    ),
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "uk_u3w_legacy_baseline_receipt_v2",
        0,
        1,
        "adoption_receipt_sha256",
        "A",
        "<NULL>",
        "<NULL>",
        "",
        "BTREE",
        "",
        "",
        "YES",
        "<NULL>",
    ),
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "uk_u3w_legacy_baseline_run_v2",
        0,
        1,
        "adoption_run_id",
        "A",
        "<NULL>",
        "<NULL>",
        "",
        "BTREE",
        "",
        "",
        "YES",
        "<NULL>",
    ),
    (
        "u3w_schema_migration",
        "PRIMARY",
        0,
        1,
        "version",
        "A",
        "<NULL>",
        "<NULL>",
        "",
        "BTREE",
        "",
        "",
        "YES",
        "<NULL>",
    ),
]

EXPECTED_CONTROL_CONSTRAINTS = [
    ("u3w_legacy_schema_baseline_receipt_v2", "PRIMARY", "PRIMARY KEY", "YES"),
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "chk_u3w_legacy_baseline_false_claims_v2",
        "CHECK",
        "YES",
    ),
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "chk_u3w_legacy_baseline_json_v2",
        "CHECK",
        "YES",
    ),
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "chk_u3w_legacy_baseline_mode_v2",
        "CHECK",
        "YES",
    ),
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "uk_u3w_legacy_baseline_receipt_v2",
        "UNIQUE",
        "YES",
    ),
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "uk_u3w_legacy_baseline_run_v2",
        "UNIQUE",
        "YES",
    ),
    ("u3w_schema_migration", "PRIMARY", "PRIMARY KEY", "YES"),
]

EXPECTED_CONTROL_CHECK_CLAUSES_HEX = [
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "chk_u3w_legacy_baseline_false_claims_v2",
        "28286063616E6F6E6963616C5F686973746F72795F636C61696D656460203D203029"
        "20616E642028607075626C69635F696E69745F3033355F7468726F7567685F303432"
        "5F72656365697074735F7772697474656E60203D20302920616E6420286070726F6475"
        "6374696F6E5F627573696E6573735F73746174655F6368616E67656460203D20302929",
    ),
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "chk_u3w_legacy_baseline_json_v2",
        "6A736F6E5F76616C69642860726563656970745F6A736F6E6029",
    ),
    (
        "u3w_legacy_schema_baseline_receipt_v2",
        "chk_u3w_legacy_baseline_mode_v2",
        "2860626173656C696E655F6D6F646560203D205F757466386D62345C274C45474143"
        "595F41444F505445445F5731415F56325C2729",
    ),
]


def table_count(mysql, table):
    return int(
        mysql.scalar(
            """SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema=DATABASE() AND table_name=%s""",
            (table,),
        )
    )


def assert_control_shapes(mysql):
    if any(table_count(mysql, table) != 1 for table in CONTROL_TABLES):
        raise RuntimeError("control table creation is incomplete")
    for table, expected in EXPECTED_CONTROL_COLUMNS.items():
        actual = mysql.rows(
            """SELECT column_name,column_type,is_nullable,
            COALESCE(column_default,'<NULL>'),column_default IS NULL,extra,
            COALESCE(character_set_name,''),COALESCE(collation_name,''),
            COALESCE(generation_expression,'')
        FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name=%s
        ORDER BY ordinal_position""",
            (table,),
        )
        expected_with_null_marker = [
            row[:4] + (1 if row[3] == "<NULL>" else 0,) + row[4:]
            for row in expected
        ]
        if actual != expected_with_null_marker:
            raise RuntimeError("{} exact column shape is invalid".format(table))
    tables = mysql.rows(
        """SELECT table_name,table_type,engine,table_collation,
        COALESCE(create_options,''),COALESCE(table_comment,'')
        FROM information_schema.tables
        WHERE table_schema=DATABASE() AND table_name IN
          ('u3w_schema_migration','u3w_legacy_schema_baseline_receipt_v2')
        ORDER BY BINARY table_name"""
    )
    if tables != [
        (
            "u3w_legacy_schema_baseline_receipt_v2",
            "BASE TABLE",
            "InnoDB",
            "utf8mb4_unicode_ci",
            "",
            "W1A旧库基线采纳不可变回执V2",
        ),
        (
            "u3w_schema_migration",
            "BASE TABLE",
            "InnoDB",
            "utf8mb4_unicode_ci",
            "",
            "U3W可重入数据库迁移记录",
        ),
    ]:
        raise RuntimeError("control table engine, collation or options are invalid")
    indexes = mysql.rows(
        """SELECT table_name,index_name,non_unique,seq_in_index,column_name,
        IF(collation IS NULL,'<NULL>',collation),
        IF(sub_part IS NULL,'<NULL>',sub_part),
        IF(packed IS NULL,'<NULL>',packed),COALESCE(nullable,''),index_type,
        comment,index_comment,is_visible,
        IF(expression IS NULL,'<NULL>',expression)
        FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name IN
          ('u3w_schema_migration','u3w_legacy_schema_baseline_receipt_v2')
        ORDER BY BINARY table_name,BINARY index_name,seq_in_index"""
    )
    if indexes != EXPECTED_CONTROL_INDEXES:
        raise RuntimeError("control table exact index shape is invalid")
    constraints = mysql.rows(
        """SELECT table_name,constraint_name,constraint_type,enforced
        FROM information_schema.table_constraints
        WHERE table_schema=DATABASE() AND table_name IN
          ('u3w_schema_migration','u3w_legacy_schema_baseline_receipt_v2')
        ORDER BY BINARY table_name,BINARY constraint_name"""
    )
    if constraints != EXPECTED_CONTROL_CONSTRAINTS:
        raise RuntimeError("control table exact constraint shape is invalid")
    check_clauses = mysql.rows(
        """SELECT tc.table_name,cc.constraint_name,HEX(cc.check_clause)
        FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema=cc.constraint_schema
         AND tc.constraint_name=cc.constraint_name
         AND tc.constraint_type='CHECK'
        WHERE tc.table_schema=DATABASE() AND tc.table_name IN
          ('u3w_schema_migration','u3w_legacy_schema_baseline_receipt_v2')
        ORDER BY BINARY tc.table_name,BINARY cc.constraint_name"""
    )
    if check_clauses != EXPECTED_CONTROL_CHECK_CLAUSES_HEX:
        raise RuntimeError("control table exact check-clause shape is invalid")
    foreign_keys = int(
        mysql.scalar(
            """SELECT COUNT(*) FROM information_schema.referential_constraints
            WHERE constraint_schema=DATABASE() AND
              (table_name IN
                ('u3w_schema_migration',
                 'u3w_legacy_schema_baseline_receipt_v2')
               OR referenced_table_name IN
                ('u3w_schema_migration',
                 'u3w_legacy_schema_baseline_receipt_v2'))"""
        )
    )
    triggers = int(
        mysql.scalar(
            """SELECT COUNT(*) FROM information_schema.triggers
            WHERE trigger_schema=DATABASE() AND event_object_table IN
              ('u3w_schema_migration',
               'u3w_legacy_schema_baseline_receipt_v2')"""
        )
    )
    if foreign_keys != 0 or triggers != 0:
        raise RuntimeError("control tables cannot have foreign keys or triggers")
    if control_overlay_sha256(mysql) != EXPECTED_CONTROL_OVERLAY_SHA256:
        raise RuntimeError("control table canonical overlay digest is invalid")


def read_existing_receipt(mysql):
    rows = mysql.rows(
        """SELECT receipt_json,adoption_receipt_sha256
        FROM u3w_legacy_schema_baseline_receipt_v2
        ORDER BY baseline_id"""
    )
    if len(rows) > 1:
        raise RuntimeError("multiple legacy baseline receipts are forbidden")
    if not rows:
        return None, None
    raw, digest = rows[0]
    payload = json.loads(raw)
    validate_receipt_shape(payload)
    if (
        canonical_json(payload) != raw
        or receipt_digest(payload) != digest
    ):
        raise RuntimeError("stored legacy receipt canonical binding is invalid")
    return payload, digest


def assert_no_fabricated_history(mysql):
    count = int(
        mysql.scalar(
            """SELECT COUNT(*) FROM u3w_schema_migration
            WHERE version IN
              ('public_init_035','public_init_036','public_init_037',
               'public_init_038','public_init_039','public_init_040',
               'public_init_041','public_init_042')"""
        )
    )
    if count != 0:
        raise RuntimeError("legacy adoption cannot coexist with claimed 035-042")


def fsync_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def atomic_bytes(path, payload, mode=0o600):
    path = pathlib.Path(path)
    partial = path.with_name(path.name + ".partial")
    if partial.exists() or partial.is_symlink():
        status = partial.lstat()
        if (
            partial.is_symlink()
            or not partial.is_file()
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_nlink != 1
        ):
            raise RuntimeError("unsafe stale baseline partial")
        partial.unlink()
    descriptor = os.open(
        partial,
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | getattr(os, "O_NOFOLLOW", 0),
        mode,
    )
    try:
        with os.fdopen(descriptor, "wb", closefd=False) as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
    finally:
        os.close(descriptor)
    os.chmod(partial, mode)
    os.replace(partial, path)
    fsync_directory(path.parent)


def safe_run_directory(run_id):
    secure_directory(BASELINE_ROOT)
    run_directory = BASELINE_ROOT / run_id
    secure_directory(run_directory)
    if run_directory.resolve().parent != BASELINE_ROOT:
        raise RuntimeError("baseline run directory escapes root")
    return run_directory


def publish_latest(run_directory):
    parent = BASELINE_LATEST.parent
    secure_directory(parent)
    temporary = parent / (
        ".latest-{}-{}".format(os.getpid(), secrets.token_hex(6))
    )
    os.symlink(str(run_directory), temporary)
    os.replace(temporary, BASELINE_LATEST)
    fsync_directory(parent)


def write_or_validate_receipt_file(run_directory, payload, digest):
    path = run_directory / "adoption-receipt.json"
    encoded = canonical_json(payload).encode("utf-8")
    if sha256_bytes(encoded) != digest:
        raise RuntimeError("receipt encoding digest changed")
    if path.exists() or path.is_symlink():
        validate_regular_file(path, run_directory)
        if sha256_file(path) != digest or path.read_bytes() != encoded:
            raise RuntimeError("immutable adoption receipt file changed")
    else:
        atomic_bytes(path, encoded)
    publish_latest(run_directory)
    return path


def existing_result(
    mysql,
    args,
    expected_backup_sha,
    expected_original_approval_sha=None,
    expected_receipt_sha=None,
):
    if table_count(mysql, CONTROL_TABLES[0]) == 0:
        return None
    if table_count(mysql, CONTROL_TABLES[1]) == 0:
        return None
    assert_control_shapes(mysql)
    assert_no_fabricated_history(mysql)
    payload, digest = read_existing_receipt(mysql)
    if payload is None:
        return None
    expected_description = "APPLIED:{}:{}".format(BASELINE_MODE, digest)
    count = int(
        mysql.scalar(
            """SELECT COUNT(*) FROM u3w_schema_migration
            WHERE version=%s AND description=%s""",
            (MIGRATION_VERSION, expected_description),
        )
    )
    if (
        count != 1
        or payload["runId"] != args.run_id
        or payload["sourceCommit"] != args.source_commit
        or payload["approvalReceiptSha256"]
        != (expected_original_approval_sha or args.approval_sha)
        or payload["runnerSha256"] != args.runner_sha
        or payload["workerSha256"] != args.worker_sha
        or payload["backupBundleReceiptSha256"] != expected_backup_sha
        or (expected_receipt_sha is not None and digest != expected_receipt_sha)
    ):
        raise RuntimeError("existing legacy adoption belongs to another identity")
    return payload, digest


def create_payload(
    args,
    identity,
    source_facts,
    pre_facts,
    post_facts,
    overlay_sha,
    bundle,
    database_endpoint,
    database_server_uuid,
):
    description_prefix = "APPLIED:{}:".format(BASELINE_MODE)
    payload = {
        "schema": RECEIPT_SCHEMA,
        "baselineMode": BASELINE_MODE,
        "migrationVersion": MIGRATION_VERSION,
        "migrationDescription": description_prefix + "<receipt-sha256>",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "database": DATABASE,
        "databaseEndpoint": database_endpoint,
        "databaseServerUuid": database_server_uuid,
        "serverVersion": identity[0],
        "serverVersionComment": identity[1],
        "fingerprintAlgorithm": FINGERPRINT_ALGORITHM,
        "sourceFacts": source_facts,
        "sourceFactsSha256": sha256_bytes(
            canonical_json(source_facts).encode("utf-8")
        ),
        "preAdoptionLiveFacts": pre_facts,
        "postAdoptionBusinessProjectionFacts": post_facts,
        "controlOverlaySha256": overlay_sha,
        "backupRunId": bundle["runId"],
        "backupSourceCommit": bundle["sourceCommit"],
        "backupBundleReceiptSha256": args.expected_backup_bundle_sha,
        "backupReceiptSha256": bundle["backupReceiptSha256"],
        "restoreReceiptSha256": bundle["restoreReceiptSha256"],
        "backupSha256": bundle["backupSha256"],
        "backupSizeBytes": bundle["backupSizeBytes"],
        "sourceJarSha256": sha256_file(JAR_PATH),
        "approvalReceiptSha256": args.approval_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "canonicalHistoryClaimed": False,
        "publicInit035Through042ReceiptsWritten": False,
        "productionBusinessStateChanged": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
        "observedAt": utc_now(),
    }
    # The human-readable description does not self-contain the final digest.
    # Its fixed placeholder is part of the canonical payload; the DB ledger
    # stores the actual digest alongside the mode.
    validate_receipt_shape(payload)
    return payload


def insert_receipt_in_transaction(mysql, payload, digest):
    raw = canonical_json(payload)
    description = "APPLIED:{}:{}".format(BASELINE_MODE, digest)
    with mysql.connection.cursor() as cursor:
        cursor.execute(
            """INSERT INTO u3w_schema_migration
                  (version,applied_at,description)
            VALUES (%s,CURRENT_TIMESTAMP,%s)""",
            (MIGRATION_VERSION, "RUNNING:{}:{}".format(BASELINE_MODE, digest)),
        )
        cursor.execute(
            """INSERT INTO u3w_legacy_schema_baseline_receipt_v2
                (baseline_id,adoption_run_id,baseline_mode,source_commit,
                 backup_run_id,backup_bundle_receipt_sha256,backup_sha256,
                 backup_size_bytes,source_facts_sha256,
                 approval_receipt_sha256,source_jar_sha256,
                 adoption_runner_sha256,adoption_worker_sha256,
                 adoption_receipt_sha256,canonical_history_claimed,
                 public_init_035_through_042_receipts_written,
                 production_business_state_changed,receipt_json,applied_at)
                VALUES
                (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,
                 0,0,0,%s,CURRENT_TIMESTAMP(6))""",
            (
                digest,
                payload["runId"],
                BASELINE_MODE,
                payload["sourceCommit"],
                payload["backupRunId"],
                payload["backupBundleReceiptSha256"],
                payload["backupSha256"],
                payload["backupSizeBytes"],
                payload["sourceFactsSha256"],
                payload["approvalReceiptSha256"],
                payload["sourceJarSha256"],
                payload["runnerSha256"],
                payload["workerSha256"],
                digest,
                raw,
            ),
        )
        cursor.execute(
            """UPDATE u3w_schema_migration SET description=%s,
                applied_at=CURRENT_TIMESTAMP
                WHERE version=%s AND description=%s""",
            (
                description,
                MIGRATION_VERSION,
                "RUNNING:{}:{}".format(BASELINE_MODE, digest),
            ),
        )
        if cursor.rowcount != 1:
            raise RuntimeError("legacy adoption state transition failed")


def begin_locked_adoption_transaction(mysql):
    mysql.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
    mysql.connection.begin()
    try:
        isolation = str(mysql.scalar("SELECT @@transaction_isolation"))
        if isolation.upper() != "REPEATABLE-READ":
            raise RuntimeError("legacy adoption requires REPEATABLE READ")
        with mysql.connection.cursor() as cursor:
            # Full primary-index next-key scans keep every current row and both
            # edge gaps stable for the complete adoption transaction.
            cursor.execute(
                "SELECT menu_id FROM sys_menu ORDER BY menu_id FOR UPDATE"
            )
            cursor.fetchall()
            cursor.execute(
                """SELECT version FROM u3w_schema_migration
                ORDER BY version FOR UPDATE"""
            )
            cursor.fetchall()
            cursor.execute(
                """SELECT baseline_id
                FROM u3w_legacy_schema_baseline_receipt_v2
                ORDER BY baseline_id FOR UPDATE"""
            )
            cursor.fetchall()
    except Exception:
        mysql.connection.rollback()
        raise


def apply(args):
    if os.geteuid() != 0:
        raise RuntimeError("legacy adoption requires root")
    connection = parse_environment()
    lock_descriptor = open_host_change_lock()
    mysql = None
    named_lock = False
    instance_lock = False
    try:
        fcntl.flock(lock_descriptor, fcntl.LOCK_EX)
        mysql = Mysql(connection)
        server_uuid = str(mysql.scalar("SELECT @@server_uuid")).lower()
        if not UUID_PATTERN.fullmatch(server_uuid):
            raise RuntimeError("database server UUID is invalid")
        validate_approval(args, connection, server_uuid)
        if int(
            mysql.scalar(
                "SELECT GET_LOCK(%s,30)",
                ("fbsir:legacy_w1a_baseline_v2",),
            )
            or 0
        ) != 1:
            raise RuntimeError("database legacy-adoption lock unavailable")
        named_lock = True
        initial_control_counts = {
            table: table_count(mysql, table) for table in CONTROL_TABLES
        }
        if any(count not in (0, 1) for count in initial_control_counts.values()):
            raise RuntimeError("ambiguous control-table state")
        prior = None
        if all(count == 1 for count in initial_control_counts.values()):
            mysql.execute("LOCK INSTANCE FOR BACKUP")
            instance_lock = True
            prior = existing_result(
                mysql, args, args.expected_backup_bundle_sha
            )
        else:
            # Prove BACKUP_ADMIN before the first DDL so a missing privilege
            # cannot strand a newly-created partial control-table prefix.
            mysql.execute("LOCK INSTANCE FOR BACKUP")
            mysql.execute("UNLOCK INSTANCE")
        run_directory = safe_run_directory(args.run_id)
        if prior is not None:
            payload, digest = prior
            path = write_or_validate_receipt_file(
                run_directory, payload, digest
            )
            return {
                "schema": "fbsir.u3wLegacyBaselineWorkerResult.v2",
                "mode": "Apply",
                "state": "APPLIED",
                "runId": args.run_id,
                "sourceCommit": args.source_commit,
                "adoptionReceiptPath": str(path),
                "adoptionReceiptSha256": digest,
                "productionBusinessStateChanged": False,
                "productionServiceChanged": False,
                "officialExpertsPackageChanged": False,
                "idempotentReplay": True,
            }
        bundle, source, _ = validate_backup_bundle(
            args.expected_backup_bundle_sha
        )
        identity_before, facts_before = collect_projection_facts(mysql)
        if (
            identity_before[0] != source.get("serverVersion")
            or facts_before != source.get("sourceFacts")
        ):
            raise RuntimeError(
                "live legacy projection does not match restored backup facts"
            )
        migration_exists = initial_control_counts[CONTROL_TABLES[0]]
        receipt_exists = initial_control_counts[CONTROL_TABLES[1]]
        if migration_exists not in (0, 1) or receipt_exists not in (0, 1):
            raise RuntimeError("ambiguous control-table state")
        if migration_exists == 1:
            # A legacy table prefix is recoverable only when it is empty.
            if int(
                mysql.scalar("SELECT COUNT(*) FROM u3w_schema_migration")
            ) != 0:
                raise RuntimeError("unexpected pre-existing migration rows")
        if not instance_lock:
            mysql.execute(MIGRATION_TABLE_SQL)
            mysql.execute(RECEIPT_TABLE_SQL)
            mysql.execute("LOCK INSTANCE FOR BACKUP")
            instance_lock = True
        assert_control_shapes(mysql)
        assert_no_fabricated_history(mysql)
        payload_existing, _ = read_existing_receipt(mysql)
        if payload_existing is not None:
            raise RuntimeError("unexpected pre-existing adoption receipt")

        begin_locked_adoption_transaction(mysql)
        try:
            assert_control_shapes(mysql)
            assert_no_fabricated_history(mysql)
            identity_locked, facts_locked = collect_projection_facts(mysql)
            if (
                identity_locked != identity_before
                or facts_locked != facts_before
                or facts_locked != source.get("sourceFacts")
            ):
                raise RuntimeError(
                    "legacy projection drifted before the locked transaction"
                )
            overlay_sha = control_overlay_sha256(mysql)
            payload = create_payload(
                args,
                identity_locked,
                source["sourceFacts"],
                facts_locked,
                facts_locked,
                overlay_sha,
                bundle,
                "{}:{}".format(connection["host"], connection["port"]),
                server_uuid,
            )
            digest = receipt_digest(payload)
            insert_receipt_in_transaction(mysql, payload, digest)
            assert_control_shapes(mysql)
            assert_no_fabricated_history(mysql)
            identity_after, facts_after = collect_projection_facts(mysql)
            if (
                identity_after != identity_locked
                or facts_after != facts_locked
                or control_overlay_sha256(mysql) != overlay_sha
            ):
                raise RuntimeError(
                    "legacy projection or control overlay drifted in transaction"
                )
            mysql.connection.commit()
        except Exception:
            mysql.connection.rollback()
            raise
        assert_control_shapes(mysql)
        assert_no_fabricated_history(mysql)
        identity_committed, facts_committed = collect_projection_facts(mysql)
        if (
            identity_committed != identity_locked
            or facts_committed != facts_locked
            or control_overlay_sha256(mysql) != overlay_sha
        ):
            raise RuntimeError(
                "legacy projection or control overlay drifted after commit"
            )
        mysql.execute("UNLOCK INSTANCE")
        instance_lock = False
        stored, stored_digest = read_existing_receipt(mysql)
        if stored != payload or stored_digest != digest:
            raise RuntimeError("legacy adoption database readback mismatch")
        final_description = "APPLIED:{}:{}".format(BASELINE_MODE, digest)
        if int(
            mysql.scalar(
                """SELECT COUNT(*) FROM u3w_schema_migration
                WHERE version=%s AND description=%s""",
                (MIGRATION_VERSION, final_description),
            )
        ) != 1:
            raise RuntimeError("legacy adoption ledger readback mismatch")
        path = write_or_validate_receipt_file(
            run_directory, payload, digest
        )
        return {
            "schema": "fbsir.u3wLegacyBaselineWorkerResult.v2",
            "mode": "Apply",
            "state": "APPLIED",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "adoptionReceiptPath": str(path),
            "adoptionReceiptSha256": digest,
            "backupBundleReceiptSha256": args.expected_backup_bundle_sha,
            "productionBusinessStateChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
            "idempotentReplay": False,
        }
    finally:
        if mysql is not None:
            if instance_lock:
                try:
                    mysql.execute("UNLOCK INSTANCE")
                except Exception:
                    pass
            if named_lock:
                try:
                    mysql.scalar(
                        "SELECT RELEASE_LOCK(%s)",
                        ("fbsir:legacy_w1a_baseline_v2",),
                    )
                except Exception:
                    pass
            mysql.close()
        os.close(lock_descriptor)


def recover(args):
    if os.geteuid() != 0:
        raise RuntimeError("legacy baseline recovery requires root")
    connection = parse_environment()
    lock_descriptor = open_host_change_lock()
    mysql = None
    named_lock = False
    instance_lock = False
    try:
        fcntl.flock(lock_descriptor, fcntl.LOCK_EX)
        mysql = Mysql(connection)
        server_uuid = str(mysql.scalar("SELECT @@server_uuid")).lower()
        if not UUID_PATTERN.fullmatch(server_uuid):
            raise RuntimeError("database server UUID is invalid")
        approval = validate_approval(args, connection, server_uuid)
        if int(
            mysql.scalar(
                "SELECT GET_LOCK(%s,30)",
                ("fbsir:legacy_w1a_baseline_v2",),
            )
            or 0
        ) != 1:
            raise RuntimeError("database legacy-recovery lock unavailable")
        named_lock = True
        mysql.execute("LOCK INSTANCE FOR BACKUP")
        instance_lock = True
        prior = existing_result(
            mysql,
            args,
            args.expected_backup_bundle_sha,
            expected_original_approval_sha=args.original_approval_sha,
            expected_receipt_sha=args.expected_adoption_receipt_sha,
        )
        if prior is None:
            raise RuntimeError("legacy baseline receipt is absent")
        payload, digest = prior
        if (
            approval["originalApprovalReceiptSha256"]
            != payload["approvalReceiptSha256"]
        ):
            raise RuntimeError("recovery approval original anchor mismatch")
        bundle_path = pathlib.Path(
            "/opt/fbsir/admin/backups/w1a",
            payload["backupRunId"],
            "receipt.json",
        )
        validate_backup_bundle(
            args.expected_backup_bundle_sha,
            bundle_path=bundle_path,
        )
        identity, facts = collect_projection_facts(mysql)
        if (
            identity[0] != payload["serverVersion"]
            or facts != payload["postAdoptionBusinessProjectionFacts"]
            or control_overlay_sha256(mysql)
            != payload["controlOverlaySha256"]
        ):
            raise RuntimeError("live baseline state drifted before recovery")
        run_directory = safe_run_directory(args.run_id)
        path = write_or_validate_receipt_file(
            run_directory, payload, digest
        )
        return {
            "schema": "fbsir.u3wLegacyBaselineWorkerResult.v2",
            "mode": "Recover",
            "state": "ANCHOR_RECOVERED",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "adoptionReceiptPath": str(path),
            "adoptionReceiptSha256": digest,
            "originalApprovalReceiptSha256":
                payload["approvalReceiptSha256"],
            "recoveryApprovalReceiptSha256": args.approval_sha,
            "productionFilesystemChanged": True,
            "productionBusinessStateChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
        }
    finally:
        if mysql is not None:
            if instance_lock:
                try:
                    mysql.execute("UNLOCK INSTANCE")
                except Exception:
                    pass
            if named_lock:
                try:
                    mysql.scalar(
                        "SELECT RELEASE_LOCK(%s)",
                        ("fbsir:legacy_w1a_baseline_v2",),
                    )
                except Exception:
                    pass
            mysql.close()
        os.close(lock_descriptor)


def plan(args):
    connection = parse_environment()
    mysql = Mysql(connection)
    try:
        bundle, source, _ = validate_backup_bundle(
            args.expected_backup_bundle_sha
        )
        identity, facts = collect_projection_facts(mysql)
        server_uuid = str(mysql.scalar("SELECT @@server_uuid")).lower()
        if not UUID_PATTERN.fullmatch(server_uuid):
            raise RuntimeError("database server UUID is invalid")
        controls = {
            table: table_count(mysql, table) for table in CONTROL_TABLES
        }
        existing_receipt_sha = None
        existing_original_approval_sha = None
        if all(count == 1 for count in controls.values()):
            assert_control_shapes(mysql)
            assert_no_fabricated_history(mysql)
            existing_payload, existing_receipt_sha = (
                read_existing_receipt(mysql)
            )
            if existing_payload is not None:
                existing_original_approval_sha = existing_payload[
                    "approvalReceiptSha256"
                ]
        return {
            "schema": "fbsir.u3wLegacyBaselineWorkerResult.v2",
            "mode": "Plan",
            "state": "PLANNED",
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "serverVersion": identity[0],
            "database": DATABASE,
            "databaseEndpoint": "{}:{}".format(
                connection["host"], connection["port"]
            ),
            "databaseServerUuid": server_uuid,
            "liveProjectionMatchesBackup": facts == source.get("sourceFacts"),
            "backupRunId": bundle["runId"],
            "backupBundleReceiptSha256": args.expected_backup_bundle_sha,
            "controlTableCounts": controls,
            "existingAdoptionReceiptSha256": existing_receipt_sha,
            "existingOriginalApprovalReceiptSha256":
                existing_original_approval_sha,
            "writesAllowed": [
                "CREATE u3w_schema_migration",
                "CREATE u3w_legacy_schema_baseline_receipt_v2",
                "INSERT one legacy adoption receipt",
                "INSERT one legacy adoption migration row",
            ],
            "publicInit035Through042ReceiptsWritten": False,
            "productionBusinessStateChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
        }
    finally:
        mysql.close()


def parse_args(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode", choices=("Plan", "Apply", "Recover"), required=True
    )
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--approval-sha", required=True)
    parser.add_argument("--approval-json-base64", default="")
    parser.add_argument("--runner-sha", required=True)
    parser.add_argument("--worker-sha", required=True)
    parser.add_argument("--expected-backup-bundle-sha", required=True)
    parser.add_argument(
        "--expected-adoption-receipt-sha", default="0" * 64
    )
    parser.add_argument("--original-approval-sha", default="0" * 64)
    args = parser.parse_args(argv)
    if (
        not RUN_PATTERN.fullmatch(args.run_id)
        or not COMMIT_PATTERN.fullmatch(args.source_commit)
        or not SHA_PATTERN.fullmatch(args.approval_sha)
        or not SHA_PATTERN.fullmatch(args.runner_sha)
        or not SHA_PATTERN.fullmatch(args.worker_sha)
        or not SHA_PATTERN.fullmatch(args.expected_backup_bundle_sha)
        or not SHA_PATTERN.fullmatch(args.expected_adoption_receipt_sha)
        or not SHA_PATTERN.fullmatch(args.original_approval_sha)
        or (
            args.mode == "Recover"
            and (
                args.expected_adoption_receipt_sha == "0" * 64
                or args.original_approval_sha == "0" * 64
            )
        )
    ):
        parser.error("run, source or digest argument shape is invalid")
    return args


def main(argv=None):
    args = parse_args(argv)
    if args.mode == "Plan":
        result = plan(args)
    elif args.mode == "Recover":
        result = recover(args)
    else:
        result = apply(args)
    print(canonical_json(result))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(
            canonical_json(
                {
                    "schema": "fbsir.u3wLegacyBaselineWorkerError.v1",
                    "errorType": type(error).__name__,
                    "error": str(error),
                    "productionBusinessStateChanged": False,
                }
            ),
            file=sys.stderr,
        )
        raise
