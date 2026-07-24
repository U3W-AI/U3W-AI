#!/usr/bin/env python3
"""Adopt the exact live legacy admin-root dependency without changing MySQL.

This worker records a current-state adoption receipt.  It does not claim to
have executed the dependency migration and it never writes production database
or service state.
"""

import argparse
import base64
import datetime as dt
import errno
import fcntl
import hashlib
import json
import os
import pathlib
import re
import secrets
import stat
import sys
import time


TARGET_HOST = "api2.u3w.com"
DATABASE = "fbsir"
ENV_PATH = pathlib.Path("/etc/u3w/fbsir-admin.env")
HOST_CHANGE_LOCK = pathlib.Path(
    "/opt/fbsir/admin/.u3w-production-change.lock"
)
BASELINE_ROOT = pathlib.Path("/opt/fbsir/admin/baselines/w1a")
BASELINE_LATEST = pathlib.Path("/opt/fbsir/admin/baselines/latest")
BACKUP_ROOT = pathlib.Path("/opt/fbsir/admin/backups/w1a")
BACKUP_LATEST = pathlib.Path("/opt/fbsir/admin/backups/latest")
ADOPTION_ROOT = pathlib.Path("/opt/fbsir/admin/dependencies/w1a")
ADOPTION_LATEST = pathlib.Path(
    "/opt/fbsir/admin/dependencies/latest"
)

DEPENDENCY_MIGRATION_VERSION = (
    "w1a_043_legacy_admin_root_dependency_20260724_001"
)
DEPENDENCY_MIGRATION_DESCRIPTION = (
    "APPLIED:W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY_V1"
)
PUBLIC_INIT_043_VERSION = "public_init_043"
PUBLIC_INIT_043_DESCRIPTION = (
    "APPLIED:Independent Board exact official experts attribution v1"
)
ATTRIBUTION_INTERNAL_043_VERSION = (
    "20260723_independent_board_attribution_v1_043"
)
ATTRIBUTION_INTERNAL_043_DESCRIPTION = (
    "APPLIED:exact WorkBuddy experts 26.7.21 attribution journey "
    "and append-only event ledger"
)
EXPECTED_W1A_SCHEMA_FINGERPRINT = (
    "fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d"
)
ATTRIBUTION_TABLES = frozenset(
    {
        "fbs_board_attr_journey_v1",
        "fbs_board_attr_event_v1",
    }
)
ATTRIBUTION_PERMISSION = "board:attribution:query"
MIGRATION_LOCK_NAME = "u3w:w1a:public_init_043"
DATABASE_PROTECTION_MODE = (
    "HOST_FLOCK_NAMED_LOCK_REPEATABLE_READ_FULL_CONTROL_RANGE_MDL_"
    "AND_APPROVED_NO_DDL_WINDOW"
)
RECEIPT_SCHEMA = (
    "fbsir.u3wLegacyAdminRootDependencyAdoptionReceipt.v3"
)
PLAN_SCHEMA = "fbsir.u3wAdminRootDependencyPlan.v2"
ADOPTION_CLAIM = "CURRENT_STATE_ONLY_NOT_ORIGINAL_EXECUTION"

RUN_PATTERN = re.compile(
    r"w1a-admin-root-dependency-"
    r"[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}"
)
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
UUID_PATTERN = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
    r"[0-9a-f]{4}-[0-9a-f]{12}"
)

RECEIPT_FIELDS = frozenset(
    {
        "schema",
        "adoptionState",
        "adoptionClaim",
        "runId",
        "sourceCommit",
        "targetHost",
        "database",
        "databaseEndpoint",
        "databaseServerUuid",
        "serverVersion",
        "databaseProtectionMode",
        "dependencyMigrationVersion",
        "dependencyMigrationDescription",
        "publicInit043Version",
        "attributionInternal043Version",
        "liveFacts",
        "liveFactsSha256",
        "baselineLatestReceiptPath",
        "baselineLatestReceiptSha256",
        "backupLatestReceiptPath",
        "backupLatestReceiptSha256",
        "planReceiptSha256",
        "approvalReceiptSha256",
        "runnerSha256",
        "workerSha256",
        "originalExecutionClaimed",
        "productionFilesystemChanged",
        "productionDatabaseChanged",
        "productionServiceChanged",
        "officialExpertsPackageChanged",
        "secretsDisclosed",
        "observedAt",
    }
)
MUTATION_STATE = {
    "productionFilesystemChanged": False,
}
LIVE_FACT_FIELDS = frozenset(
    {
        "databaseServerUuid",
        "serverVersion",
        "rootIdentityCount",
        "exactRootCount",
        "rootProjection",
        "rootRoleBindingCount",
        "rootPageChildCount",
        "dependencyVersionCount",
        "dependencyReceiptCount",
        "dependencyRowsFingerprintSha256",
        "forbiddenPublicInit001Through042ReceiptCount",
        "publicInit043ReceiptCount",
        "attributionInternalReceiptCount",
        "attributionTableCount",
        "attributionTriggerCount",
        "attributionPermissionCount",
        "attributionEventCount",
        "attributionProbeEventCount",
        "attributionNaturalEventCount",
        "attributionNonProbeEventCount",
        "attributionAuthoritativeProductCreditCount",
        "attributionJourneyCount",
        "attributionProbeJourneyCount",
        "attributionNaturalJourneyCount",
        "attributionNonProbeJourneyCount",
        "w1aSchemaFingerprintSha256",
        "w1a043State",
    }
)
ROOT_PROJECTION_FIELDS = frozenset(
    {
        "menuId",
        "menuName",
        "parentId",
        "orderNum",
        "path",
        "component",
        "query",
        "routeName",
        "isFrame",
        "isCache",
        "menuType",
        "visible",
        "status",
        "perms",
        "icon",
    }
)
PLAN_FIELDS = frozenset(
    {
        "schema",
        "mode",
        "runId",
        "sourceCommit",
        "targetHost",
        "database",
        "databaseEndpoint",
        "databaseServerUuid",
        "serverVersion",
        "databaseProtectionMode",
        "dependencyMigrationVersion",
        "dependencyMigrationDescription",
        "publicInit043Version",
        "attributionInternal043Version",
        "liveFacts",
        "liveFactsSha256",
        "baselineLatestReceiptPath",
        "baselineLatestReceiptSha256",
        "backupLatestReceiptPath",
        "backupLatestReceiptSha256",
        "runnerSha256",
        "workerSha256",
        "actionRequired",
        "approvalRequired",
        "approvalReceiptSchema",
        "concurrentDdlProhibited",
        "wouldWriteProductionFilesystem",
        "wouldWriteProductionDatabase",
        "wouldChangeProductionService",
        "wouldChangeOfficialExpertsPackage",
        "productionFilesystemChanged",
        "productionDatabaseChanged",
        "productionServiceChanged",
        "officialExpertsPackageChanged",
        "originalExecutionClaimed",
        "secretsDisclosed",
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


def utc_now():
    return (
        dt.datetime.now(dt.timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def validate_approval(args, connection, server_uuid, now=None):
    if args.approval_sha == "0" * 64 or not args.approval_json_base64:
        raise RuntimeError("adoption requires an approval receipt")
    try:
        raw = base64.b64decode(
            args.approval_json_base64, validate=True
        )
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
        "expectedLiveFactsSha256",
        "expectedDatabaseProtectionMode",
        "expectedPlanReceiptSha256",
        "expectedBaselineReceiptSha256",
        "expectedBackupReceiptSha256",
        "runnerSha256",
        "workerSha256",
    }
    if not isinstance(approval, dict) or set(approval) != expected_fields:
        raise RuntimeError("approval receipt has missing or unknown fields")
    try:
        approved_at = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        expires_at = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
    except (AttributeError, TypeError, ValueError) as error:
        raise RuntimeError("approval receipt time is invalid") from error
    current = now or dt.datetime.now(dt.timezone.utc)
    endpoint = "{}:{}".format(connection["host"], connection["port"])
    if (
        sha256_bytes(raw) != args.approval_sha
        or approval["schema"]
        != "fbsir.u3wProductionChangeApprovalReceipt.v1"
        or approval["action"]
        != "ADOPT_W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY"
        or approval["targetHost"] != TARGET_HOST
        or approval["runId"] != args.run_id
        or approval["sourceCommit"] != args.source_commit
        or approval["database"] != DATABASE
        or approval["databaseEndpoint"] != endpoint
        or approval["databaseServerUuid"] != server_uuid
        or not SHA_PATTERN.fullmatch(
            str(approval["expectedLiveFactsSha256"])
        )
        or approval["expectedDatabaseProtectionMode"]
        != DATABASE_PROTECTION_MODE
        or approval["expectedPlanReceiptSha256"] != args.plan_sha
        or approval["expectedBaselineReceiptSha256"]
        != args.expected_baseline_receipt_sha
        or approval["expectedBackupReceiptSha256"]
        != args.expected_backup_receipt_sha
        or approval["runnerSha256"] != args.runner_sha
        or approval["workerSha256"] != args.worker_sha
        or approval["authorizedBy"] != "workspace-user"
        or approval["concurrentDdlProhibited"] is not True
        or approval["productionFilesystemWrite"] is not True
        or approval["productionDatabaseWrite"] is not False
        or approval["productionServiceChange"] is not False
        or approval["officialExpertsPackageChange"] is not False
        or approved_at.tzinfo is None
        or expires_at.tzinfo is None
        or approved_at > current
        or expires_at <= current
        or expires_at - approved_at > dt.timedelta(hours=24)
    ):
        raise RuntimeError("approval receipt identity or scope is invalid")
    return approval


def validate_adopt_plan(
    args,
    connection,
    server_uuid,
    baseline_path,
    backup_path,
):
    if args.plan_sha == "0" * 64 or not args.plan_json_base64:
        raise RuntimeError("adoption requires an immutable Plan receipt")
    try:
        raw = base64.b64decode(
            args.plan_json_base64, validate=True
        )
        payload = json.loads(raw.decode("utf-8-sig"))
    except (ValueError, UnicodeError, json.JSONDecodeError) as error:
        raise RuntimeError(
            "admin-root dependency Plan receipt cannot be decoded"
        ) from error
    endpoint = "{}:{}".format(connection["host"], connection["port"])
    validate_plan(payload)
    if (
        sha256_bytes(raw) != args.plan_sha
        or payload["runId"] != args.run_id
        or payload["sourceCommit"] != args.source_commit
        or payload["databaseEndpoint"] != endpoint
        or payload["databaseServerUuid"] != server_uuid
        or payload["baselineLatestReceiptPath"]
        != str(baseline_path)
        or payload["baselineLatestReceiptSha256"]
        != args.expected_baseline_receipt_sha
        or payload["backupLatestReceiptPath"] != str(backup_path)
        or payload["backupLatestReceiptSha256"]
        != args.expected_backup_receipt_sha
        or payload["runnerSha256"] != args.runner_sha
        or payload["workerSha256"] != args.worker_sha
    ):
        raise RuntimeError(
            "admin-root dependency Plan receipt identity changed"
        )
    return payload


def parse_environment():
    path = validate_regular_file(ENV_PATH, modes=(0o600,))
    values = {}
    for number, raw in enumerate(
        path.read_text(encoding="utf-8").splitlines(), 1
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
        if (
            re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name) is None
            or name in values
        ):
            raise RuntimeError("invalid or duplicate environment key")
        if (
            len(value) >= 2
            and value[0] == value[-1]
            and value[0] in "\"'"
        ):
            value = value[1:-1]
        values[name] = value
    control_plane = (
        "FBSIR_MYSQL_URL",
        "FBSIR_MYSQL_USERNAME",
        "FBSIR_MYSQL_PASSWORD",
    )
    if any(not values.get(name) for name in control_plane):
        raise RuntimeError(
            "control-plane database credentials are absent"
        )
    match = re.fullmatch(
        r"jdbc:mysql://([^/:?]+)(?::([0-9]{1,5}))?"
        r"/([^?]+)(?:\?.*)?",
        values[control_plane[0]],
    )
    if not match or match.group(3) != DATABASE:
        raise RuntimeError("database URL is not the fixed fbsir target")
    return {
        "host": match.group(1),
        "port": int(match.group(2) or "3306"),
        "user": values[control_plane[1]],
        "password": values[control_plane[2]],
    }


class Mysql:
    def __init__(self, connection):
        try:
            import pymysql
        except ImportError as error:
            raise RuntimeError(
                "PyMySQL is required on the target host"
            ) from error
        self.connection = pymysql.connect(
            host=connection["host"],
            port=connection["port"],
            user=connection["user"],
            password=connection["password"],
            database=DATABASE,
            charset="utf8mb4",
            autocommit=False,
            connect_timeout=10,
            read_timeout=120,
            write_timeout=120,
        )

    def close(self):
        self.connection.close()

    def commit(self):
        self.connection.commit()

    def rollback(self):
        self.connection.rollback()

    def execute(self, sql, args=None):
        with self.connection.cursor() as cursor:
            cursor.execute(sql, args)
            return cursor.rowcount

    def rows(self, sql, args=None):
        with self.connection.cursor() as cursor:
            cursor.execute(sql, args)
            return list(cursor.fetchall())

    def scalar(self, sql, args=None):
        rows = self.rows(sql, args)
        if len(rows) != 1 or len(rows[0]) != 1:
            raise RuntimeError("database scalar result is invalid")
        return rows[0][0]


def _exact_root(row):
    return bool(
        len(row) == 15
        and row[1] == "独董会管理"
        and int(row[2]) == 0
        and int(row[3]) == 5
        and row[4] == "independent-board-admin"
        and row[5] is None
        and row[6] is None
        and row[7] == "IndependentBoardAdmin"
        and int(row[8]) == 1
        and int(row[9]) == 0
        and row[10] == "M"
        and str(row[11]) == "0"
        and str(row[12]) == "0"
        and str(row[13] or "") == ""
        and row[14] == "peoples"
    )


def _root_identity(row):
    return bool(
        len(row) == 15
        and (
            row[1] == "独董会管理"
            or row[4] == "independent-board-admin"
            or row[7] == "IndependentBoardAdmin"
        )
    )


def hex_text(value):
    return str(value).encode("utf-8").hex().upper()


def dependency_rows_fingerprint(exact_roots, dependency_receipts):
    rows = []
    for version, description in dependency_receipts:
        rows.append(
            "D|{}|{}".format(
                hex_text(version),
                hex_text(description),
            )
        )
    for row in exact_roots:
        rows.append(
            "|".join(
                (
                    "R",
                    str(int(row[0])),
                    hex_text(row[1]),
                    str(int(row[2])),
                    str(int(row[3])),
                    hex_text(row[4] or ""),
                    hex_text(
                        row[5] if row[5] is not None else "<NULL>"
                    ),
                    hex_text(
                        row[6] if row[6] is not None else "<NULL>"
                    ),
                    hex_text(row[7] or ""),
                    str(int(row[8])),
                    str(int(row[9])),
                    hex_text(row[10]),
                    hex_text(str(row[11])),
                    hex_text(str(row[12])),
                    hex_text(row[13] or ""),
                    hex_text(row[14]),
                )
            )
        )
    rows.sort(key=lambda value: value.encode("utf-8"))
    return sha256_bytes(("\n".join(rows) + "\n").encode("utf-8"))


def w1a_schema_fingerprint_rows(mysql):
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
    return [str(row[0]) for row in mysql.rows(statement)]


def w1a_schema_fingerprint(rows):
    return sha256_bytes(
        ("\n".join(rows) + "\n").encode("utf-8")
    )


def facts_from_locked_rows(
    server_uuid,
    server_version,
    menu_rows,
    role_menu_rows,
    migration_rows,
    attribution_table_count=0,
    attribution_trigger_count=0,
    attribution_permission_count=0,
    attribution_event_count=0,
    attribution_probe_event_count=0,
    attribution_natural_event_count=0,
    attribution_non_probe_event_count=0,
    attribution_authoritative_product_credit_count=0,
    attribution_journey_count=0,
    attribution_probe_journey_count=0,
    attribution_natural_journey_count=0,
    attribution_non_probe_journey_count=0,
    w1a_schema_fingerprint_sha256=None,
):
    menu_rows = list(menu_rows)
    role_menu_rows = list(role_menu_rows)
    migration_rows = list(migration_rows)
    identity_roots = [row for row in menu_rows if _root_identity(row)]
    exact_roots = [row for row in identity_roots if _exact_root(row)]
    root_ids = {int(row[0]) for row in exact_roots}
    dependency_versions = [
        row
        for row in migration_rows
        if row[0] == DEPENDENCY_MIGRATION_VERSION
    ]
    dependency_receipts = [
        row
        for row in dependency_versions
        if row[1] == DEPENDENCY_MIGRATION_DESCRIPTION
    ]
    forbidden_public = 0
    for version, _ in migration_rows:
        match = re.fullmatch(r"public_init_([0-9]{3})", str(version))
        if match and 1 <= int(match.group(1)) <= 42:
            forbidden_public += 1
    role_count = sum(
        1
        for row in role_menu_rows
        if len(row) >= 2 and int(row[1]) in root_ids
    )
    child_count = sum(
        1
        for row in menu_rows
        if len(row) == 15
        and int(row[2]) in root_ids
        and row[10] in {"M", "C"}
    )
    public_043_rows = [
        row for row in migration_rows
        if row[0] == PUBLIC_INIT_043_VERSION
    ]
    internal_043_rows = [
        row for row in migration_rows
        if row[0] == ATTRIBUTION_INTERNAL_043_VERSION
    ]
    public_043_count = len(public_043_rows)
    internal_043_count = len(internal_043_rows)
    retained_043 = (
        public_043_count == 1
        and public_043_rows[0][1] == PUBLIC_INIT_043_DESCRIPTION
        and internal_043_count == 1
        and internal_043_rows[0][1]
            == ATTRIBUTION_INTERNAL_043_DESCRIPTION
        and int(attribution_table_count) == 2
        and int(attribution_trigger_count) == 2
        and int(attribution_permission_count) == 1
        and int(attribution_event_count)
            == int(attribution_probe_event_count)
        and int(attribution_natural_event_count) == 0
        and int(attribution_non_probe_event_count) == 0
        and int(attribution_authoritative_product_credit_count) == 0
        and int(attribution_journey_count)
            == int(attribution_probe_journey_count)
        and int(attribution_natural_journey_count) == 0
        and int(attribution_non_probe_journey_count) == 0
        and w1a_schema_fingerprint_sha256
            == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )
    absent_043 = (
        public_043_count == 0
        and internal_043_count == 0
        and int(attribution_table_count) == 0
        and int(attribution_trigger_count) == 0
        and int(attribution_permission_count) == 0
        and int(attribution_event_count) == 0
        and int(attribution_probe_event_count) == 0
        and int(attribution_natural_event_count) == 0
        and int(attribution_non_probe_event_count) == 0
        and int(attribution_authoritative_product_credit_count) == 0
        and int(attribution_journey_count) == 0
        and int(attribution_probe_journey_count) == 0
        and int(attribution_natural_journey_count) == 0
        and int(attribution_non_probe_journey_count) == 0
        and w1a_schema_fingerprint_sha256 is None
    )
    root_projection = (
        {
            "menuId": int(exact_roots[0][0]),
            "menuName": exact_roots[0][1],
            "parentId": int(exact_roots[0][2]),
            "orderNum": int(exact_roots[0][3]),
            "path": exact_roots[0][4],
            "component": exact_roots[0][5],
            "query": exact_roots[0][6],
            "routeName": exact_roots[0][7],
            "isFrame": int(exact_roots[0][8]),
            "isCache": int(exact_roots[0][9]),
            "menuType": exact_roots[0][10],
            "visible": str(exact_roots[0][11]),
            "status": str(exact_roots[0][12]),
            "perms": exact_roots[0][13],
            "icon": exact_roots[0][14],
        }
        if len(exact_roots) == 1
        else None
    )
    return {
        "databaseServerUuid": str(server_uuid).lower(),
        "serverVersion": str(server_version),
        "rootIdentityCount": len(identity_roots),
        "exactRootCount": len(exact_roots),
        "rootProjection": root_projection,
        "rootRoleBindingCount": role_count,
        "rootPageChildCount": child_count,
        "dependencyVersionCount": len(dependency_versions),
        "dependencyReceiptCount": len(dependency_receipts),
        "dependencyRowsFingerprintSha256":
            dependency_rows_fingerprint(
                exact_roots,
                dependency_receipts,
            ),
        "forbiddenPublicInit001Through042ReceiptCount": forbidden_public,
        "publicInit043ReceiptCount": public_043_count,
        "attributionInternalReceiptCount": internal_043_count,
        "attributionTableCount": int(attribution_table_count),
        "attributionTriggerCount": int(attribution_trigger_count),
        "attributionPermissionCount": int(attribution_permission_count),
        "attributionEventCount": int(attribution_event_count),
        "attributionProbeEventCount":
            int(attribution_probe_event_count),
        "attributionNaturalEventCount":
            int(attribution_natural_event_count),
        "attributionNonProbeEventCount":
            int(attribution_non_probe_event_count),
        "attributionAuthoritativeProductCreditCount":
            int(attribution_authoritative_product_credit_count),
        "attributionJourneyCount": int(attribution_journey_count),
        "attributionProbeJourneyCount":
            int(attribution_probe_journey_count),
        "attributionNaturalJourneyCount":
            int(attribution_natural_journey_count),
        "attributionNonProbeJourneyCount":
            int(attribution_non_probe_journey_count),
        "w1aSchemaFingerprintSha256":
            w1a_schema_fingerprint_sha256,
        "w1a043State": (
            "EXACT_043_RETAINED_DORMANT"
            if retained_043
            else "ABSENT" if absent_043 else "INVALID"
        ),
    }


def assert_exact_live_facts(facts):
    exact_043_state = (
        isinstance(facts, dict)
        and facts.get("w1a043State") == "ABSENT"
        and facts.get("publicInit043ReceiptCount") == 0
        and facts.get("attributionInternalReceiptCount") == 0
        and facts.get("attributionTableCount") == 0
        and facts.get("attributionTriggerCount") == 0
        and facts.get("attributionPermissionCount") == 0
        and facts.get("attributionEventCount") == 0
        and facts.get("attributionProbeEventCount") == 0
        and facts.get("attributionNaturalEventCount") == 0
        and facts.get("attributionNonProbeEventCount") == 0
        and facts.get(
            "attributionAuthoritativeProductCreditCount"
        ) == 0
        and facts.get("attributionJourneyCount") == 0
        and facts.get("attributionProbeJourneyCount") == 0
        and facts.get("attributionNaturalJourneyCount") == 0
        and facts.get("attributionNonProbeJourneyCount") == 0
        and facts.get("w1aSchemaFingerprintSha256") is None
    ) or (
        isinstance(facts, dict)
        and facts.get("w1a043State")
        == "EXACT_043_RETAINED_DORMANT"
        and facts.get("publicInit043ReceiptCount") == 1
        and facts.get("attributionInternalReceiptCount") == 1
        and facts.get("attributionTableCount") == 2
        and facts.get("attributionTriggerCount") == 2
        and facts.get("attributionPermissionCount") == 1
        and facts.get("attributionEventCount")
            == facts.get("attributionProbeEventCount")
        and facts.get("attributionNaturalEventCount") == 0
        and facts.get("attributionNonProbeEventCount") == 0
        and facts.get(
            "attributionAuthoritativeProductCreditCount"
        ) == 0
        and facts.get("attributionJourneyCount")
            == facts.get("attributionProbeJourneyCount")
        and facts.get("attributionNaturalJourneyCount") == 0
        and facts.get("attributionNonProbeJourneyCount") == 0
        and facts.get("w1aSchemaFingerprintSha256")
            == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )
    if (
        not isinstance(facts, dict)
        or set(facts) != LIVE_FACT_FIELDS
        or not UUID_PATTERN.fullmatch(
            str(facts.get("databaseServerUuid", ""))
        )
        or re.fullmatch(
            r"[0-9]+\.[0-9]+\.[0-9]+",
            str(facts.get("serverVersion", "")),
        )
        is None
        or facts.get("rootIdentityCount") != 1
        or facts.get("exactRootCount") != 1
        or not isinstance(facts.get("rootProjection"), dict)
        or set(facts["rootProjection"]) != ROOT_PROJECTION_FIELDS
        or facts.get("rootRoleBindingCount") != 0
        or facts.get("rootPageChildCount") != 0
        or facts.get("dependencyVersionCount") != 1
        or facts.get("dependencyReceiptCount") != 1
        or not SHA_PATTERN.fullmatch(
            str(facts.get("dependencyRowsFingerprintSha256", ""))
        )
        or facts.get(
            "forbiddenPublicInit001Through042ReceiptCount"
        )
        != 0
        or not exact_043_state
    ):
        raise RuntimeError(
            "legacy admin-root dependency state is not adoptable"
        )
    return facts


def collect_locked_live_facts(mysql):
    mysql.execute(
        "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ"
    )
    mysql.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT")
    identity = mysql.rows(
        "SELECT LOWER(@@server_uuid),DATABASE(),@@version"
    )
    if (
        len(identity) != 1
        or len(identity[0]) != 3
        or identity[0][1] != DATABASE
    ):
        raise RuntimeError("database identity is invalid")
    menu_rows = mysql.rows(
        "SELECT menu_id,menu_name,parent_id,order_num,path,component,"
        "query,route_name,is_frame,is_cache,menu_type,visible,status,"
        "perms,icon FROM sys_menu ORDER BY menu_id FOR SHARE"
    )
    role_menu_rows = mysql.rows(
        "SELECT role_id,menu_id FROM sys_role_menu "
        "ORDER BY role_id,menu_id FOR SHARE"
    )
    migration_rows = mysql.rows(
        "SELECT version,description FROM u3w_schema_migration "
        "ORDER BY version FOR SHARE"
    )
    attribution_counts = mysql.rows(
        "SELECT "
        "(SELECT COUNT(*) FROM information_schema.tables "
        "WHERE table_schema=DATABASE() AND table_name IN "
        "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')),"
        "(SELECT COUNT(*) FROM information_schema.triggers "
        "WHERE trigger_schema=DATABASE() AND event_object_table IN "
        "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')),"
        "(SELECT COUNT(*) FROM sys_menu WHERE "
        "BINARY perms=BINARY 'board:attribution:query')"
    )
    if (
        len(attribution_counts) != 1
        or len(attribution_counts[0]) != 3
    ):
        raise RuntimeError("attribution object counts are invalid")
    ledger_counts = (0,) * 9
    w1a_fingerprint = None
    if int(attribution_counts[0][0]) == 2:
        ledger_rows = mysql.rows(
            "SELECT "
            "(SELECT COUNT(*) FROM fbs_board_attr_event_v1),"
            "(SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE BINARY traffic_class=BINARY 'PROBE'),"
            "(SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE BINARY traffic_class=BINARY 'NATURAL'),"
            "(SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE BINARY traffic_class<>BINARY 'PROBE'),"
            "(SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE authoritative_product_credit<>0),"
            "(SELECT COUNT(*) FROM fbs_board_attr_journey_v1),"
            "(SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
            "WHERE BINARY traffic_class=BINARY 'PROBE'),"
            "(SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
            "WHERE BINARY traffic_class=BINARY 'NATURAL'),"
            "(SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
            "WHERE BINARY traffic_class<>BINARY 'PROBE')"
        )
        if len(ledger_rows) != 1 or len(ledger_rows[0]) != 9:
            raise RuntimeError("attribution ledger counts are invalid")
        ledger_counts = ledger_rows[0]
        w1a_fingerprint = w1a_schema_fingerprint(
            w1a_schema_fingerprint_rows(mysql)
        )
    return facts_from_locked_rows(
        identity[0][0],
        identity[0][2],
        menu_rows,
        role_menu_rows,
        migration_rows,
        *attribution_counts[0],
        *ledger_counts,
        w1a_fingerprint,
    )


def build_receipt(
    args,
    connection,
    facts,
    observed_at=None,
    baseline_path="/opt/fbsir/admin/baselines/latest/adoption-receipt.json",
    backup_path="/opt/fbsir/admin/backups/latest/receipt.json",
):
    assert_exact_live_facts(facts)
    payload = {
        "schema": RECEIPT_SCHEMA,
        "adoptionState": "ADOPTED_EXISTING_EXACT_DEPENDENCY",
        "adoptionClaim": ADOPTION_CLAIM,
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "database": DATABASE,
        "databaseEndpoint": "{}:{}".format(
            connection["host"], connection["port"]
        ),
        "databaseServerUuid": facts["databaseServerUuid"],
        "serverVersion": facts["serverVersion"],
        "databaseProtectionMode": DATABASE_PROTECTION_MODE,
        "dependencyMigrationVersion": DEPENDENCY_MIGRATION_VERSION,
        "dependencyMigrationDescription":
            DEPENDENCY_MIGRATION_DESCRIPTION,
        "publicInit043Version": PUBLIC_INIT_043_VERSION,
        "attributionInternal043Version":
            ATTRIBUTION_INTERNAL_043_VERSION,
        "liveFacts": facts,
        "liveFactsSha256": sha256_bytes(
            canonical_json(facts).encode("utf-8")
        ),
        "baselineLatestReceiptPath": str(baseline_path),
        "baselineLatestReceiptSha256":
            args.expected_baseline_receipt_sha,
        "backupLatestReceiptPath": str(backup_path),
        "backupLatestReceiptSha256":
            args.expected_backup_receipt_sha,
        "planReceiptSha256": args.plan_sha,
        "approvalReceiptSha256": args.approval_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "originalExecutionClaimed": False,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
        "secretsDisclosed": False,
        "observedAt": observed_at or utc_now(),
    }
    validate_receipt(payload)
    return payload


def build_plan(
    args,
    connection,
    facts,
    baseline_path,
    backup_path,
    observed_at=None,
):
    assert_exact_live_facts(facts)
    payload = {
        "schema": PLAN_SCHEMA,
        "mode": "Plan",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "database": DATABASE,
        "databaseEndpoint": "{}:{}".format(
            connection["host"], connection["port"]
        ),
        "databaseServerUuid": facts["databaseServerUuid"],
        "serverVersion": facts["serverVersion"],
        "databaseProtectionMode": DATABASE_PROTECTION_MODE,
        "dependencyMigrationVersion": DEPENDENCY_MIGRATION_VERSION,
        "dependencyMigrationDescription":
            DEPENDENCY_MIGRATION_DESCRIPTION,
        "publicInit043Version": PUBLIC_INIT_043_VERSION,
        "attributionInternal043Version":
            ATTRIBUTION_INTERNAL_043_VERSION,
        "liveFacts": facts,
        "liveFactsSha256": sha256_bytes(
            canonical_json(facts).encode("utf-8")
        ),
        "baselineLatestReceiptPath": str(baseline_path),
        "baselineLatestReceiptSha256":
            args.expected_baseline_receipt_sha,
        "backupLatestReceiptPath": str(backup_path),
        "backupLatestReceiptSha256":
            args.expected_backup_receipt_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "actionRequired":
            "ADOPT_W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY",
        "approvalRequired": True,
        "approvalReceiptSchema":
            "fbsir.u3wProductionChangeApprovalReceipt.v1",
        "concurrentDdlProhibited": True,
        "wouldWriteProductionFilesystem": True,
        "wouldWriteProductionDatabase": False,
        "wouldChangeProductionService": False,
        "wouldChangeOfficialExpertsPackage": False,
        "productionFilesystemChanged": False,
        "productionDatabaseChanged": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
        "originalExecutionClaimed": False,
        "secretsDisclosed": False,
        "observedAt": observed_at or utc_now(),
    }
    validate_plan(payload)
    return payload


def validate_plan(payload):
    if not isinstance(payload, dict) or set(payload) != PLAN_FIELDS:
        raise RuntimeError("admin-root dependency plan fields are invalid")
    digest_names = (
        "liveFactsSha256",
        "baselineLatestReceiptSha256",
        "backupLatestReceiptSha256",
        "runnerSha256",
        "workerSha256",
    )
    if (
        payload["schema"] != PLAN_SCHEMA
        or payload["mode"] != "Plan"
        or not RUN_PATTERN.fullmatch(str(payload["runId"]))
        or not COMMIT_PATTERN.fullmatch(str(payload["sourceCommit"]))
        or payload["targetHost"] != TARGET_HOST
        or payload["database"] != DATABASE
        or payload["databaseProtectionMode"]
        != DATABASE_PROTECTION_MODE
        or re.fullmatch(
            r"[^:\s]+:[0-9]{1,5}",
            str(payload["databaseEndpoint"]),
        )
        is None
        or not UUID_PATTERN.fullmatch(
            str(payload["databaseServerUuid"])
        )
        or payload["dependencyMigrationVersion"]
        != DEPENDENCY_MIGRATION_VERSION
        or payload["dependencyMigrationDescription"]
        != DEPENDENCY_MIGRATION_DESCRIPTION
        or payload["publicInit043Version"] != PUBLIC_INIT_043_VERSION
        or payload["attributionInternal043Version"]
        != ATTRIBUTION_INTERNAL_043_VERSION
        or any(
            not SHA_PATTERN.fullmatch(str(payload.get(name, "")))
            for name in digest_names
        )
        or payload["liveFactsSha256"]
        != sha256_bytes(
            canonical_json(payload["liveFacts"]).encode("utf-8")
        )
        or payload["liveFacts"].get("databaseServerUuid")
        != payload["databaseServerUuid"]
        or payload["liveFacts"].get("serverVersion")
        != payload["serverVersion"]
        or payload["actionRequired"]
        != "ADOPT_W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY"
        or payload["approvalRequired"] is not True
        or payload["approvalReceiptSchema"]
        != "fbsir.u3wProductionChangeApprovalReceipt.v1"
        or payload["concurrentDdlProhibited"] is not True
        or payload["wouldWriteProductionFilesystem"] is not True
        or payload["wouldWriteProductionDatabase"] is not False
        or payload["wouldChangeProductionService"] is not False
        or payload["wouldChangeOfficialExpertsPackage"] is not False
        or payload["productionFilesystemChanged"] is not False
        or payload["productionDatabaseChanged"] is not False
        or payload["productionServiceChanged"] is not False
        or payload["officialExpertsPackageChanged"] is not False
        or payload["originalExecutionClaimed"] is not False
        or payload["secretsDisclosed"] is not False
    ):
        raise RuntimeError(
            "admin-root dependency plan identity or safety is invalid"
        )
    assert_exact_live_facts(payload["liveFacts"])
    try:
        observed_at = dt.datetime.fromisoformat(
            payload["observedAt"].replace("Z", "+00:00")
        )
    except (AttributeError, TypeError, ValueError) as error:
        raise RuntimeError(
            "admin-root dependency plan time is invalid"
        ) from error
    if observed_at.tzinfo is None:
        raise RuntimeError("admin-root dependency plan time is naive")
    return payload


def validate_receipt(payload):
    if not isinstance(payload, dict) or set(payload) != RECEIPT_FIELDS:
        raise RuntimeError("adoption receipt fields are invalid")
    digest_names = (
        "liveFactsSha256",
        "baselineLatestReceiptSha256",
        "backupLatestReceiptSha256",
        "planReceiptSha256",
        "approvalReceiptSha256",
        "runnerSha256",
        "workerSha256",
    )
    if (
        payload["schema"] != RECEIPT_SCHEMA
        or payload["adoptionState"]
        != "ADOPTED_EXISTING_EXACT_DEPENDENCY"
        or payload["adoptionClaim"] != ADOPTION_CLAIM
        or payload["targetHost"] != TARGET_HOST
        or payload["database"] != DATABASE
        or payload["databaseProtectionMode"]
        != DATABASE_PROTECTION_MODE
        or payload["dependencyMigrationVersion"]
        != DEPENDENCY_MIGRATION_VERSION
        or payload["dependencyMigrationDescription"]
        != DEPENDENCY_MIGRATION_DESCRIPTION
        or payload["publicInit043Version"] != PUBLIC_INIT_043_VERSION
        or payload["attributionInternal043Version"]
        != ATTRIBUTION_INTERNAL_043_VERSION
        or not RUN_PATTERN.fullmatch(str(payload["runId"]))
        or not COMMIT_PATTERN.fullmatch(str(payload["sourceCommit"]))
        or not UUID_PATTERN.fullmatch(
            str(payload["databaseServerUuid"])
        )
        or re.fullmatch(
            r"[^:\s]+:[0-9]{1,5}",
            str(payload["databaseEndpoint"]),
        )
        is None
        or any(
            not SHA_PATTERN.fullmatch(str(payload.get(name, "")))
            for name in digest_names
        )
        or payload["liveFactsSha256"]
        != sha256_bytes(
            canonical_json(payload["liveFacts"]).encode("utf-8")
        )
        or payload["originalExecutionClaimed"] is not False
        or payload["productionFilesystemChanged"] is not True
        or payload["productionDatabaseChanged"] is not False
        or payload["productionServiceChanged"] is not False
        or payload["officialExpertsPackageChanged"] is not False
        or payload["secretsDisclosed"] is not False
    ):
        raise RuntimeError("adoption receipt identity or safety is invalid")
    assert_exact_live_facts(payload["liveFacts"])
    try:
        observed_at = dt.datetime.fromisoformat(
            payload["observedAt"].replace("Z", "+00:00")
        )
    except (AttributeError, TypeError, ValueError) as error:
        raise RuntimeError("adoption receipt time is invalid") from error
    if observed_at.tzinfo is None:
        raise RuntimeError("adoption receipt time is naive")
    return payload


def safe_directory(path, mode=0o700):
    path = pathlib.Path(path)
    if not path.is_absolute():
        raise RuntimeError("adoption directory must be absolute")
    current = pathlib.Path(path.anchor)
    for part in path.parts[1:]:
        current = current / part
        created = False
        try:
            current.mkdir(mode=mode)
            created = True
        except FileExistsError:
            pass
        status = current.lstat()
        if (
            stat.S_ISLNK(status.st_mode)
            or not stat.S_ISDIR(status.st_mode)
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_mode & 0o022
        ):
            raise RuntimeError("adoption directory custody is invalid")
        if created:
            MUTATION_STATE["productionFilesystemChanged"] = True
    return path


def validate_regular_file(path, modes=(0o600,)):
    path = pathlib.Path(path)
    status = path.lstat()
    if (
        stat.S_ISLNK(status.st_mode)
        or not stat.S_ISREG(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 not in modes
        or status.st_nlink != 1
    ):
        raise RuntimeError("adoption artifact custody is invalid")
    return path


def fsync_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def resolve_latest_receipt(latest, root, filename, expected_sha):
    latest = pathlib.Path(latest)
    root = pathlib.Path(root).resolve(strict=True)
    if not latest.is_symlink():
        raise RuntimeError("latest receipt pointer is invalid")
    pointer = latest.lstat()
    if pointer.st_uid != 0 or pointer.st_gid != 0:
        raise RuntimeError("latest receipt pointer custody is invalid")
    target = latest.resolve(strict=True)
    status = target.stat()
    if (
        target.parent != root
        or target.is_symlink()
        or not target.is_dir()
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o022
    ):
        raise RuntimeError("latest receipt target is invalid")
    receipt = validate_regular_file(target / filename, modes=(0o600,))
    if sha256_file(receipt) != expected_sha:
        raise RuntimeError("latest receipt digest drifted")
    return receipt


def open_host_change_lock(create=True):
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
    flags = os.O_RDWR | getattr(os, "O_NOFOLLOW", 0)
    if create:
        flags |= os.O_CREAT
    descriptor = os.open(HOST_CHANGE_LOCK, flags, 0o600)
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
    timeout_seconds,
    monotonic=time.monotonic,
    sleeper=time.sleep,
):
    if not isinstance(timeout_seconds, (int, float)):
        raise RuntimeError("lock timeout is invalid")
    timeout_seconds = float(timeout_seconds)
    if timeout_seconds <= 0 or timeout_seconds > 30:
        raise RuntimeError("lock timeout is outside the bounded range")
    deadline = monotonic() + timeout_seconds
    while True:
        try:
            fcntl.flock(
                descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB
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


def atomic_create_exact(path, payload, mode=0o600):
    path = pathlib.Path(path)
    if path.exists() or path.is_symlink():
        existing = validate_regular_file(path, modes=(mode,))
        if existing.read_bytes() != payload:
            raise RuntimeError("immutable adoption receipt changed")
        return False
    descriptor = os.open(
        path,
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | getattr(os, "O_NOFOLLOW", 0),
        mode,
    )
    MUTATION_STATE["productionFilesystemChanged"] = True
    try:
        with os.fdopen(descriptor, "wb", closefd=False) as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
    finally:
        os.close(descriptor)
    os.chown(path, 0, 0)
    os.chmod(path, mode)
    validate_regular_file(path, modes=(mode,))
    fsync_directory(path.parent)
    return True


def validate_existing_directory_ancestry(path):
    path = pathlib.Path(os.path.abspath(path))
    if not path.is_absolute():
        raise RuntimeError("trusted directory must be absolute")
    current = pathlib.Path(path.anchor)
    for part in path.parts[1:]:
        current = current / part
        status = current.lstat()
        if (
            stat.S_ISLNK(status.st_mode)
            or not stat.S_ISDIR(status.st_mode)
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_mode & 0o022
        ):
            raise RuntimeError("trusted directory ancestry is invalid")
    return path.resolve(strict=True)


def validate_adoption_run_directory(path):
    root = validate_existing_directory_ancestry(ADOPTION_ROOT)
    path = validate_existing_directory_ancestry(path)
    status = path.stat()
    if (
        path.parent != root
        or RUN_PATTERN.fullmatch(path.name) is None
        or path.is_symlink()
        or not path.is_dir()
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o022
    ):
        raise RuntimeError("adoption run directory custody is invalid")
    return path


def latest_target():
    if not ADOPTION_LATEST.exists() and not ADOPTION_LATEST.is_symlink():
        return None
    if not ADOPTION_LATEST.is_symlink():
        raise RuntimeError("adoption latest pointer is invalid")
    status = ADOPTION_LATEST.lstat()
    if status.st_uid != 0 or status.st_gid != 0:
        raise RuntimeError("adoption latest pointer custody is invalid")
    if status.st_nlink != 1:
        raise RuntimeError("adoption latest pointer custody is invalid")
    target = pathlib.Path(os.readlink(ADOPTION_LATEST))
    if not target.is_absolute():
        target = ADOPTION_LATEST.parent / target
    return validate_adoption_run_directory(target)


def publish_latest_cas(run_directory, expected_before):
    run_directory = validate_adoption_run_directory(run_directory)
    parent = safe_directory(ADOPTION_LATEST.parent)
    observed = latest_target()
    if observed != expected_before:
        raise RuntimeError("adoption latest compare-and-swap changed")
    if observed == run_directory:
        return False
    if observed is not None and observed.name > run_directory.name:
        raise RuntimeError("adoption latest pointer would regress")
    temporary = parent / (
        ".latest-{}-{}".format(os.getpid(), secrets.token_hex(6))
    )
    os.symlink(str(run_directory), temporary)
    MUTATION_STATE["productionFilesystemChanged"] = True
    try:
        if latest_target() != expected_before:
            raise RuntimeError("adoption latest compare-and-swap changed")
        os.replace(temporary, ADOPTION_LATEST)
        fsync_directory(parent)
        MUTATION_STATE["productionFilesystemChanged"] = True
    finally:
        if temporary.exists() or temporary.is_symlink():
            temporary.unlink()
    return True


def plan(args):
    if os.geteuid() != 0:
        raise RuntimeError("admin-root dependency plan requires root")
    descriptor = open_host_change_lock(create=False)
    mysql = None
    named_lock = False
    try:
        acquire_bounded_flock(
            descriptor, args.lock_timeout_seconds
        )
        baseline_receipt = resolve_latest_receipt(
            BASELINE_LATEST,
            BASELINE_ROOT,
            "adoption-receipt.json",
            args.expected_baseline_receipt_sha,
        )
        backup_receipt = resolve_latest_receipt(
            BACKUP_LATEST,
            BACKUP_ROOT,
            "receipt.json",
            args.expected_backup_receipt_sha,
        )
        connection = parse_environment()
        mysql = Mysql(connection)
        server_uuid = str(
            mysql.scalar("SELECT LOWER(@@server_uuid)")
        )
        if not UUID_PATTERN.fullmatch(server_uuid):
            raise RuntimeError("database server UUID is invalid")
        if int(
            mysql.scalar(
                "SELECT GET_LOCK(%s,%s)",
                (MIGRATION_LOCK_NAME, args.lock_timeout_seconds),
            )
            or 0
        ) != 1:
            raise RuntimeError("W1A migration named lock is unavailable")
        named_lock = True
        facts = collect_locked_live_facts(mysql)
        assert_exact_live_facts(facts)
        if facts["databaseServerUuid"] != server_uuid:
            raise RuntimeError("database server UUID changed")
        result = build_plan(
            args,
            connection,
            facts,
            baseline_path=str(baseline_receipt),
            backup_path=str(backup_receipt),
        )
        mysql.rollback()
        if int(
            mysql.scalar(
                "SELECT RELEASE_LOCK(%s)",
                (MIGRATION_LOCK_NAME,),
            )
            or 0
        ) != 1:
            raise RuntimeError("W1A migration named lock release failed")
        named_lock = False
        return result
    except Exception:
        if mysql is not None:
            mysql.rollback()
        raise
    finally:
        if mysql is not None:
            if named_lock:
                try:
                    mysql.scalar(
                        "SELECT RELEASE_LOCK(%s)",
                        (MIGRATION_LOCK_NAME,),
                    )
                except Exception:
                    pass
            mysql.close()
        try:
            fcntl.flock(descriptor, fcntl.LOCK_UN)
        finally:
            os.close(descriptor)


def adopt(args):
    if os.geteuid() != 0:
        raise RuntimeError("admin-root dependency adoption requires root")
    descriptor = open_host_change_lock()
    mysql = None
    named_lock = False
    try:
        acquire_bounded_flock(
            descriptor, args.lock_timeout_seconds
        )
        baseline_receipt = resolve_latest_receipt(
            BASELINE_LATEST,
            BASELINE_ROOT,
            "adoption-receipt.json",
            args.expected_baseline_receipt_sha,
        )
        backup_receipt = resolve_latest_receipt(
            BACKUP_LATEST,
            BACKUP_ROOT,
            "receipt.json",
            args.expected_backup_receipt_sha,
        )
        connection = parse_environment()
        mysql = Mysql(connection)
        server_uuid = str(
            mysql.scalar("SELECT LOWER(@@server_uuid)")
        )
        if not UUID_PATTERN.fullmatch(server_uuid):
            raise RuntimeError("database server UUID is invalid")
        approved_plan = validate_adopt_plan(
            args,
            connection,
            server_uuid,
            baseline_receipt,
            backup_receipt,
        )
        validate_approval(
            args,
            connection,
            server_uuid,
        )
        if int(
            mysql.scalar(
                "SELECT GET_LOCK(%s,%s)",
                (MIGRATION_LOCK_NAME, args.lock_timeout_seconds),
            )
            or 0
        ) != 1:
            raise RuntimeError("W1A migration named lock is unavailable")
        named_lock = True
        validate_approval(
            args,
            connection,
            server_uuid,
        )
        facts = collect_locked_live_facts(mysql)
        assert_exact_live_facts(facts)
        if facts["databaseServerUuid"] != server_uuid:
            raise RuntimeError("database server UUID changed")
        approval = validate_approval(
            args,
            connection,
            server_uuid,
        )
        locked_facts_sha = sha256_bytes(
            canonical_json(facts).encode("utf-8")
        )
        if (
            approval["expectedLiveFactsSha256"]
            != locked_facts_sha
            or approved_plan["liveFactsSha256"]
            != locked_facts_sha
            or approved_plan["liveFacts"] != facts
        ):
            raise RuntimeError(
                "approved admin-root dependency live facts drifted"
            )
        latest_before = latest_target()
        run_directory = safe_directory(
            ADOPTION_ROOT / args.run_id
        )
        receipt_path = run_directory / "adoption-receipt.json"
        if receipt_path.exists() or receipt_path.is_symlink():
            raw = validate_regular_file(
                receipt_path,
                modes=(0o600,),
            ).read_bytes()
            try:
                payload = json.loads(raw.decode("utf-8"))
            except (UnicodeError, json.JSONDecodeError) as error:
                raise RuntimeError(
                    "existing adoption receipt cannot be decoded"
                ) from error
            validate_receipt(payload)
            if (
                payload["runId"] != args.run_id
                or payload["sourceCommit"] != args.source_commit
                or payload["databaseServerUuid"] != server_uuid
                or payload["databaseEndpoint"]
                != "{}:{}".format(
                    connection["host"],
                    connection["port"],
                )
                or payload["liveFacts"] != facts
                or payload["baselineLatestReceiptPath"]
                != str(baseline_receipt)
                or payload["baselineLatestReceiptSha256"]
                != args.expected_baseline_receipt_sha
                or payload["backupLatestReceiptPath"]
                != str(backup_receipt)
                or payload["backupLatestReceiptSha256"]
                != args.expected_backup_receipt_sha
                or payload["planReceiptSha256"] != args.plan_sha
                or payload["approvalReceiptSha256"]
                != args.approval_sha
                or payload["runnerSha256"] != args.runner_sha
                or payload["workerSha256"] != args.worker_sha
            ):
                raise RuntimeError(
                    "existing adoption receipt identity changed"
                )
            created = False
        else:
            payload = build_receipt(
                args,
                connection,
                facts,
                baseline_path=str(baseline_receipt),
                backup_path=str(backup_receipt),
            )
            raw = (canonical_json(payload) + "\n").encode("utf-8")
            created = atomic_create_exact(receipt_path, raw)
        latest_changed = publish_latest_cas(
            run_directory,
            latest_before,
        )
        mysql.rollback()
        if int(
            mysql.scalar(
                "SELECT RELEASE_LOCK(%s)",
                (MIGRATION_LOCK_NAME,),
            )
            or 0
        ) != 1:
            raise RuntimeError("W1A migration named lock release failed")
        named_lock = False
        return {
            "schema":
                "fbsir.u3wAdminRootDependencyWorkerResult.v3",
            "adoptionState": payload["adoptionState"],
            "runId": args.run_id,
            "sourceCommit": args.source_commit,
            "adoptionReceiptPath": str(receipt_path),
            "adoptionReceiptSha256": sha256_file(receipt_path),
            "planReceiptSha256": args.plan_sha,
            "liveFactsSha256": payload["liveFactsSha256"],
            "productionFilesystemChanged":
                bool(created or latest_changed),
            "productionDatabaseChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
            "originalExecutionClaimed": False,
            "secretsDisclosed": False,
            "idempotentReplay": not created,
        }
    except Exception:
        if mysql is not None:
            mysql.rollback()
        raise
    finally:
        if mysql is not None:
            if named_lock:
                try:
                    mysql.scalar(
                        "SELECT RELEASE_LOCK(%s)",
                        (MIGRATION_LOCK_NAME,),
                    )
                except Exception:
                    pass
            mysql.close()
        try:
            fcntl.flock(descriptor, fcntl.LOCK_UN)
        finally:
            os.close(descriptor)


def parse_args(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode", choices=("Plan", "Adopt"), default="Adopt"
    )
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--approval-sha", default="0" * 64)
    parser.add_argument("--approval-json-base64", default="")
    parser.add_argument("--plan-sha", default="0" * 64)
    parser.add_argument("--plan-json-base64", default="")
    parser.add_argument("--runner-sha", required=True)
    parser.add_argument("--worker-sha", required=True)
    parser.add_argument(
        "--expected-baseline-receipt-sha", required=True
    )
    parser.add_argument(
        "--expected-backup-receipt-sha", required=True
    )
    parser.add_argument(
        "--lock-timeout-seconds", type=int, default=15
    )
    args = parser.parse_args(argv)
    if (
        not RUN_PATTERN.fullmatch(args.run_id)
        or not COMMIT_PATTERN.fullmatch(args.source_commit)
        or any(
            not SHA_PATTERN.fullmatch(str(value))
            for value in (
                args.approval_sha,
                args.plan_sha,
                args.runner_sha,
                args.worker_sha,
                args.expected_baseline_receipt_sha,
                args.expected_backup_receipt_sha,
            )
        )
        or args.lock_timeout_seconds < 1
        or args.lock_timeout_seconds > 30
    ):
        parser.error("run, commit, digest or lock argument is invalid")
    return args


def main(argv=None):
    args = parse_args(argv)
    result = plan(args) if args.mode == "Plan" else adopt(args)
    print(canonical_json(result))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(
            canonical_json(
                {
                    "schema":
                        "fbsir.u3wAdminRootDependencyWorkerError.v1",
                    "errorType": type(error).__name__,
                    "error": str(error),
                    "productionFilesystemChanged":
                        MUTATION_STATE[
                            "productionFilesystemChanged"
                        ],
                    "productionDatabaseChanged": False,
                    "productionServiceChanged": False,
                    "officialExpertsPackageChanged": False,
                    "originalExecutionClaimed": False,
                    "secretsDisclosed": False,
                }
            ),
            file=sys.stderr,
        )
        raise
