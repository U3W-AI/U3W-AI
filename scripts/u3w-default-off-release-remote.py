#!/usr/bin/env python3
"""Target-side W1A default-off release state machine.

The worker is executed from an exact committed Git blob. Stage only writes an
isolated release directory. Apply performs the additive 043 migration, an
atomic current-link switch, a systemd drop-in switch, and the two portal Nginx
cutover. Rollback restores the pre-existing unit and placeholder site while
retaining the additive, default-off database objects.
"""

import argparse
import base64
import datetime as dt
import fcntl
import hashlib
import hmac
import json
import os
import pathlib
import re
import shutil
import stat
import subprocess
import tarfile
import time
import urllib.error
import urllib.request
import zipfile


TARGET_HOST = "api2.u3w.com"
SERVICE_UNIT = "fbsir-admin.service"
DATABASE = "fbsir"
ADMIN_ROOT = pathlib.Path("/opt/fbsir/admin")
RELEASE_ROOT = ADMIN_ROOT / "releases"
CURRENT_LINK = ADMIN_ROOT / "current"
LOCK_PATH = ADMIN_ROOT / ".u3w-production-change.lock"
ENV_PATH = pathlib.Path("/etc/u3w/fbsir-admin.env")
ENV_PATH_TEXT = "/etc/u3w/fbsir-admin.env"
ADDITIONAL_CONFIG_PATH = pathlib.Path(
    "/opt/fbsir/admin/application-connector.yml"
)
DROPIN_DIRECTORY = pathlib.Path("/etc/systemd/system/fbsir-admin.service.d")
DROPIN_PATH = DROPIN_DIRECTORY / "20-u3w-default-off-release.conf"
NGINX_PATH = pathlib.Path("/etc/nginx/conf.d/u3w-placeholder-sites.conf")
LATEST_RECEIPT = RELEASE_ROOT / "latest-receipt.json"
BACKUP_RECEIPT = ADMIN_ROOT / "backups/latest/receipt.json"
BASELINE_RECEIPT = ADMIN_ROOT / "baselines/latest/adoption-receipt.json"
CONFIGURATION_RECEIPT = (
    ADMIN_ROOT / "configuration/latest/configuration-receipt.json"
)
API2_EVENT_KEY_PATH = pathlib.Path(
    "/etc/u3w/secrets/independent-board-attribution-event-key"
)
RUN_PATTERN = re.compile(
    r"w1a-release-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z"
)
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
MIGRATION_PUBLIC_VERSION = "public_init_043"
MIGRATION_PUBLIC_DESCRIPTION = (
    "APPLIED:Independent Board exact official experts attribution v1"
)
MIGRATION_INTERNAL_VERSION = (
    "20260723_independent_board_attribution_v1_043"
)
MIGRATION_INTERNAL_DESCRIPTION = (
    "APPLIED:exact WorkBuddy experts 26.7.21 attribution journey "
    "and append-only event ledger"
)
EXPECTED_W1A_SCHEMA_FINGERPRINT = (
    "fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d"
)
FALSE_FLAGS = (
    "FBSIR_BOARD_ATTRIBUTION_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_CREDIT_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_CREDIT_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_WRITER_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_INTENT_CLASSIFIER_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_ADMIN_READ_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED",
)
EVENT_KEY_ID_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID"
)
EVENT_KEY_NAME = "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY"
PREVIOUS_EVENT_KEY_ID_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY_ID"
)
PREVIOUS_EVENT_KEY_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY"
)
SAME_BINDING_KEY_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET"
)
MANAGED_W1A_ENVIRONMENT_NAMES = FALSE_FLAGS + (
    EVENT_KEY_ID_NAME,
    EVENT_KEY_NAME,
    PREVIOUS_EVENT_KEY_ID_NAME,
    PREVIOUS_EVENT_KEY_NAME,
    SAME_BINDING_KEY_NAME,
)
DATABASE_ENVIRONMENT_NAMES = (
    "WXFBSIR_MYSQL_URL",
    "WXFBSIR_MYSQL_USERNAME",
    "WXFBSIR_MYSQL_PASSWORD",
)
DATABASE_ENVIRONMENT_ALIAS_NAMES = (
    "FBSIR_MYSQL_URL",
    "FBSIR_MYSQL_USERNAME",
    "FBSIR_MYSQL_PASSWORD",
)
PROCESS_SECURITY_ENVIRONMENT_NAMES = (
    DATABASE_ENVIRONMENT_NAMES
    + DATABASE_ENVIRONMENT_ALIAS_NAMES
    + FALSE_FLAGS
)
FORBIDDEN_RUNTIME_OVERRIDE_NAMES = frozenset(
    (
        "SPRING_APPLICATION_JSON",
        "SPRING_CONFIG_IMPORT",
        "SPRING_CONFIG_LOCATION",
        "SPRING_CONFIG_ADDITIONAL_LOCATION",
        "SPRING_CONFIG_NAME",
        "SPRING_CONFIG_ON_NOT_FOUND",
        "SPRING_PROFILES_ACTIVE",
        "SPRING_PROFILES_INCLUDE",
        "JAVA_TOOL_OPTIONS",
        "_JAVA_OPTIONS",
        "JDK_JAVA_OPTIONS",
    )
)
EXPECTED_ADDITIONAL_CONFIG_PATHS = frozenset(
    (
        "fbsir",
        "fbsir.connector-insights",
        "fbsir.connector-insights.ingest-enabled",
    )
)
EXPECTED_PYYAML_VERSION = "6.0.1"
EXTERNAL_CONFIG_NAME_PATTERN = re.compile(
    r"application(?:-[A-Za-z0-9._-]+)?\.(?:yml|yaml|properties)",
    re.IGNORECASE,
)
PLAN_TARGET_FIELDS = frozenset(
    (
        "schema",
        "targetHost",
        "serviceUnit",
        "service",
        "unitSha256",
        "fragmentFileManifest",
        "dropInManifest",
        "unitFiles",
        "environmentCustody",
        "environmentSha256",
        "environmentFilePaths",
        "environmentFileManifest",
        "additionalConfigCustody",
        "additionalConfigSha256",
        "externalConfigManifest",
        "activeJarPath",
        "activeJarSha256",
        "configuredJarPath",
        "configuredJarSha256",
        "processJarPath",
        "processJarSha256",
        "processArgvSha256",
        "processEnvironmentNamesSha256",
        "processFlagValues",
        "configuredFlagValues",
        "processForbiddenOverrideNames",
        "api2EventKeyManifest",
        "processSecurityConfigurationNames",
        "processSecurityConfigurationHmacSha256",
        "expectedSecurityConfigurationNames",
        "expectedSecurityConfigurationHmacSha256",
        "processDatabaseBindingMatched",
        "configuredEnvironmentSha256",
        "configuredEnvironmentNames",
        "configuredEnvironmentHmacSha256",
        "processConfiguredEnvironmentHmacSha256",
        "processConfiguredEnvironmentMatched",
        "processConfiguredEnvironmentMismatchNames",
        "processPendingRestartEnvironmentNames",
        "processConfiguredEnvironmentLoadState",
        "processConfiguredEnvironmentPreStageCompatible",
        "nginxConfigs",
        "activeNginxManifest",
        "nginxDumpSha256",
        "releaseRootExists",
        "releaseRootEntryManifest",
        "currentLinkExists",
        "currentLinkResolved",
        "currentLifecycleState",
        "stageEntryTopology",
        "productionChanged",
        "productionChangedByPlan",
    )
)
RELEASE_ARTIFACT_RELATIVE_PATHS = (
    "backend/fbsir-admin.jar",
    "sql/public_init_043.sql",
    "release-runner.ps1",
    "release-worker.py",
    "evidence/build-receipt.json",
    "evidence/release-plan.json",
    "evidence/frontend-manifest.txt",
    "evidence/systemd-dropin.conf",
    "evidence/rollback-systemd-dropin.conf",
    "evidence/u3w-portal-sites.conf",
    "evidence/application-rollback-assembly.json",
    "evidence/database-rollback-safety.json",
    "rollback/previous-admin.jar",
    "rollback/placeholder-sites.conf",
)
APPLY_FAILURE_NAME_PATTERN = re.compile(
    r"apply-failure-[0-9]{8}T[0-9]{12}Z-[0-9a-f]{12}\.json"
)
ROLLBACK_FAILURE_NAME_PATTERN = re.compile(
    r"rollback-failure-[0-9]{8}T[0-9]{12}Z-[0-9a-f]{12}\.json"
)


class ReleaseMutationFailure(RuntimeError):
    """A failed mutation with an immutable, release-owned failure receipt."""

    def __init__(self, message, failure_receipt_path):
        super().__init__(message)
        self.failure_receipt_path = pathlib.Path(failure_receipt_path)


def canonical_json(value):
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )


def utc_now():
    return (
        dt.datetime.now(dt.timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with pathlib.Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def fsync_directory(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def validate_sha(value, label, allow_zero=False):
    text = str(value or "")
    if not SHA_PATTERN.fullmatch(text):
        raise RuntimeError(label + " is not a SHA-256 digest")
    if not allow_zero and text == "0" * 64:
        raise RuntimeError(label + " cannot be zero")
    return text


def validate_ancestor_chain(path):
    candidate = pathlib.Path(path)
    chain = [candidate]
    while candidate != candidate.parent:
        candidate = candidate.parent
        chain.append(candidate)
    for entry in reversed(chain):
        if not entry.exists():
            continue
        status = entry.lstat()
        if stat.S_ISLNK(status.st_mode):
            raise RuntimeError("unsafe symlink ancestor: " + str(entry))
        if status.st_uid != 0 or status.st_gid != 0:
            raise RuntimeError("unsafe ancestor owner: " + str(entry))
        if stat.S_ISDIR(status.st_mode) and status.st_mode & 0o022:
            raise RuntimeError("unsafe writable ancestor: " + str(entry))


def safe_directory(path, mode=0o700):
    path = pathlib.Path(path)
    validate_ancestor_chain(path.parent)
    created = False
    if not path.exists():
        path.mkdir(mode=mode)
        fsync_directory(path.parent)
        created = True
    status = path.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink < 2
        or status.st_mode & 0o022
        or (not created and status.st_mode & 0o777 != mode)
    ):
        raise RuntimeError("unsafe release directory: " + str(path))
    if created:
        os.chmod(path, mode)
    return path


def ensure_parent_directory(path, create_mode=0o700):
    """Validate an existing parent without rewriting system directory modes."""
    path = pathlib.Path(path)
    validate_ancestor_chain(path.parent)
    if not path.exists():
        return safe_directory(path, create_mode)
    status = path.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink < 2
        or status.st_mode & 0o022
    ):
        raise RuntimeError("unsafe parent directory: " + str(path))
    return path


def validate_regular_file(path, parent=None, modes=(0o600, 0o640, 0o644)):
    path = pathlib.Path(path)
    if path.is_symlink() or not path.is_file():
        raise RuntimeError("required regular file is absent: " + str(path))
    if parent is not None and path.resolve().parent != pathlib.Path(parent).resolve():
        raise RuntimeError("file escaped its expected parent: " + str(path))
    status = path.stat()
    if (
        status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink != 1
        or status.st_mode & 0o777 not in modes
    ):
        raise RuntimeError("file custody is invalid: " + str(path))
    return path


def atomic_bytes(path, payload, mode=0o600):
    path = pathlib.Path(path)
    ensure_parent_directory(path.parent)
    if path.exists() or path.is_symlink():
        status = path.lstat()
        if (
            not stat.S_ISREG(status.st_mode)
            or stat.S_ISLNK(status.st_mode)
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_nlink != 1
            or status.st_mode & 0o022
        ):
            raise RuntimeError("unsafe existing target: " + str(path))
    partial = path.with_name(path.name + ".partial")
    if partial.exists() or partial.is_symlink():
        status = partial.lstat()
        if (
            not partial.is_file()
            or partial.is_symlink()
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_nlink != 1
        ):
            raise RuntimeError("unsafe stale partial: " + str(partial))
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
    os.chown(partial, 0, 0)
    os.chmod(partial, mode)
    os.replace(partial, path)
    fsync_directory(path.parent)


def atomic_json(path, value):
    atomic_bytes(
        path, (canonical_json(value) + "\n").encode("utf-8"), 0o600
    )


def atomic_symlink(target, link):
    link = pathlib.Path(link)
    ensure_parent_directory(link.parent)
    if link.exists() and not link.is_symlink():
        raise RuntimeError("symlink target path is occupied: " + str(link))
    if link.is_symlink():
        status = link.lstat()
        if status.st_uid != 0 or status.st_gid != 0:
            raise RuntimeError("existing symlink custody is invalid: " + str(link))
    partial = link.with_name("." + link.name + ".next")
    if partial.exists() or partial.is_symlink():
        status = partial.lstat()
        if (
            not stat.S_ISLNK(status.st_mode)
            or status.st_uid != 0
            or status.st_gid != 0
        ):
            raise RuntimeError("unsafe stale symlink partial: " + str(partial))
        partial.unlink()
    os.symlink(str(target), partial)
    os.replace(partial, link)
    fsync_directory(link.parent)


def validated_latest_receipt_target():
    if not LATEST_RECEIPT.exists() and not LATEST_RECEIPT.is_symlink():
        return None
    if not LATEST_RECEIPT.is_symlink():
        raise RuntimeError("latest receipt is not a symlink")
    resolved = LATEST_RECEIPT.resolve(strict=True)
    if (
        resolved.parent.parent != RELEASE_ROOT
        or not RUN_PATTERN.fullmatch(resolved.parent.name)
        or resolved.name not in {
            "deployment-readiness-receipt.json",
            "deployment-receipt.json",
            "rollback-receipt.json",
            "rollback-verification-receipt.json",
        }
    ):
        raise RuntimeError("latest receipt target escaped its lifecycle")
    validate_regular_file(
        resolved, resolved.parent, modes=(0o600,)
    )
    receipt = read_json(resolved)
    if (
        receipt.get("releaseId") != resolved.parent.name
        or not COMMIT_PATTERN.fullmatch(
            str(receipt.get("sourceCommit") or "")
        )
    ):
        raise RuntimeError("latest receipt target identity is invalid")
    return resolved


def advance_latest_receipt(target, allowed_predecessors=()):
    target = pathlib.Path(target)
    validate_regular_file(target, target.parent, modes=(0o600,))
    target = target.resolve(strict=True)
    current = validated_latest_receipt_target()
    if current == target:
        return False
    allowed = {
        pathlib.Path(value)
        for value in allowed_predecessors
        if value is not None
    }
    if current is not None and current not in allowed:
        raise RuntimeError(
            "latest receipt points to another lifecycle transition"
        )
    atomic_symlink(target, LATEST_RECEIPT)
    return True


def read_json(path):
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))


def file_anchor_matches(path, expected):
    path = pathlib.Path(path)
    try:
        resolved = path.resolve(strict=True)
        status = resolved.stat()
        return bool(
            resolved.is_file()
            and not resolved.is_symlink()
            and status.st_uid == 0
            and status.st_gid == 0
            and status.st_nlink == 1
            and status.st_mode & 0o777 == 0o600
            and sha256_file(resolved) == expected
        )
    except OSError:
        return False


def parse_environment():
    validate_regular_file(ENV_PATH, ENV_PATH.parent, modes=(0o600,))
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
    if any(values.get(name) != "false" for name in FALSE_FLAGS):
        raise RuntimeError("all W1A flags must remain explicitly false")
    required = DATABASE_ENVIRONMENT_NAMES
    if any(not values.get(name) for name in required):
        raise RuntimeError("application database credentials are absent")
    aliases = DATABASE_ENVIRONMENT_ALIAS_NAMES
    alias_values = tuple(values.get(name) for name in aliases)
    if any(alias_values) and (
        not all(alias_values)
        or alias_values
        != tuple(values[name] for name in required)
    ):
        raise RuntimeError("database credential aliases drifted")
    match = re.fullmatch(
        r"jdbc:mysql://([^/:?]+)(?::([0-9]{1,5}))?/([^?]+)(?:\?.*)?",
        values["WXFBSIR_MYSQL_URL"],
    )
    if not match or match.group(3) != DATABASE:
        raise RuntimeError("database URL is not the fixed fbsir target")
    return values, {
        "host": match.group(1),
        "port": int(match.group(2) or "3306"),
        "user": values["WXFBSIR_MYSQL_USERNAME"],
        "password": values["WXFBSIR_MYSQL_PASSWORD"],
    }


def environment_flags_explicit_false():
    try:
        validate_regular_file(ENV_PATH, ENV_PATH.parent, modes=(0o600,))
        values = {}
        for raw in ENV_PATH.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            if line.startswith("export "):
                line = line[7:].lstrip()
            name, value = line.split("=", 1)
            name = name.strip()
            value = value.strip()
            if (
                len(value) >= 2
                and value[0] == value[-1]
                and value[0] in "\"'"
            ):
                value = value[1:-1]
            if name in values:
                return False
            values[name] = value
        return all(values.get(name) == "false" for name in FALSE_FLAGS)
    except (OSError, UnicodeError, RuntimeError, ValueError):
        return None


class Mysql:
    def __init__(self, connection):
        try:
            import pymysql
        except ImportError as error:
            raise RuntimeError("PyMySQL is required on the target") from error
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

    def execute_script(self, statements):
        """Execute every parsed statement on this exact locked connection."""
        with self.connection.cursor() as cursor:
            for statement in statements:
                cursor.execute(statement)
                while cursor.nextset():
                    pass

    def rows(self, sql, args=None):
        with self.connection.cursor() as cursor:
            cursor.execute(sql, args)
            return list(cursor.fetchall())

    def scalar(self, sql, args=None):
        rows = self.rows(sql, args)
        if len(rows) != 1 or len(rows[0]) != 1:
            raise RuntimeError("database scalar query shape is invalid")
        return rows[0][0]


def parse_mysql_script(text):
    """Parse the reviewed mysql-client DELIMITER subset deterministically."""
    if not isinstance(text, str) or "\x00" in text:
        raise RuntimeError("migration SQL text is invalid")
    delimiter = ";"
    buffered = []
    statements = []
    for line in text.splitlines(keepends=True):
        directive = re.fullmatch(
            r"\s*DELIMITER\s+(\S+)\s*(?:\r?\n)?",
            line,
            flags=re.IGNORECASE,
        )
        if directive:
            if "".join(buffered).strip():
                raise RuntimeError(
                    "DELIMITER directive appeared inside a statement"
                )
            delimiter = directive.group(1)
            if (
                len(delimiter) > 16
                or delimiter.startswith("--")
                or any(character.isspace() for character in delimiter)
            ):
                raise RuntimeError("migration DELIMITER is invalid")
            buffered = []
            continue
        if re.match(r"\s*DELIMITER\b", line, flags=re.IGNORECASE):
            raise RuntimeError("migration DELIMITER directive is invalid")
        buffered.append(line)
        candidate = "".join(buffered).rstrip()
        if candidate.endswith(delimiter):
            statement = candidate[:-len(delimiter)].strip()
            if statement:
                statements.append(statement)
            buffered = []
    if "".join(buffered).strip():
        raise RuntimeError("migration SQL has an unterminated statement")
    if not statements:
        raise RuntimeError("migration SQL contains no statements")
    return statements


class MigrationLease:
    """Own the exact MySQL session and named lock through receipt commit."""

    def __init__(
        self,
        mysql,
        connection,
        facts,
        database_changed_this_run=False,
        database_changed_since_stage=False,
    ):
        self.mysql = mysql
        self.connection = connection
        self.facts = facts
        self.database_changed_this_run = database_changed_this_run
        self.database_changed_since_stage = database_changed_since_stage
        self.closed = False

    def close(self):
        if self.closed:
            return
        try:
            self.mysql.scalar(
                "SELECT RELEASE_LOCK('u3w:w1a:public_init_043')"
            )
        except Exception:
            pass
        finally:
            self.mysql.close()
            self.closed = True


def load_additional_yaml_documents(text):
    """Parse with the exact target-side safe loader and reject duplicate keys."""
    try:
        import yaml
    except ImportError as error:
        raise RuntimeError(
            "target PyYAML dependency is absent"
        ) from error
    if getattr(yaml, "__version__", "") != EXPECTED_PYYAML_VERSION:
        raise RuntimeError("target PyYAML version drifted")

    class NoDuplicateSafeLoader(yaml.SafeLoader):
        pass

    def construct_mapping(loader, node, deep=False):
        loader.flatten_mapping(node)
        pairs = []
        seen = set()
        for key_node, value_node in node.value:
            key = loader.construct_object(key_node, deep=deep)
            try:
                duplicate = key in seen
            except TypeError as error:
                raise RuntimeError(
                    "additional config mapping key is not scalar"
                ) from error
            if duplicate:
                raise RuntimeError(
                    "additional config contains a duplicate key"
                )
            seen.add(key)
            pairs.append(
                (key, loader.construct_object(value_node, deep=deep))
            )
        return dict(pairs)

    NoDuplicateSafeLoader.add_constructor(
        yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
        construct_mapping,
    )
    try:
        return list(yaml.load_all(text, Loader=NoDuplicateSafeLoader))
    except RuntimeError:
        raise
    except yaml.YAMLError as error:
        raise RuntimeError("additional config YAML is invalid") from error


def flattened_yaml_paths(documents):
    paths = set()
    active = set()

    def walk(value, prefix=()):
        if isinstance(value, dict):
            identity = id(value)
            if identity in active:
                raise RuntimeError(
                    "additional config contains a recursive YAML alias"
                )
            active.add(identity)
            try:
                for key, nested in value.items():
                    if not isinstance(key, str) or not re.fullmatch(
                        r"[A-Za-z0-9][A-Za-z0-9-]*", key
                    ):
                        raise RuntimeError(
                            "additional config key is outside the allowlist"
                        )
                    path = prefix + (key.lower(),)
                    paths.add(".".join(path))
                    walk(nested, path)
            finally:
                active.remove(identity)
        elif isinstance(value, list):
            raise RuntimeError(
                "additional config sequences are outside the allowlist"
            )

    for document in documents:
        if document is None:
            continue
        if not isinstance(document, dict):
            raise RuntimeError(
                "additional config document root must be a mapping"
            )
        walk(document)
    return paths


def validate_additional_config():
    """Accept only the reviewed connector-insights YAML semantic surface."""
    validate_regular_file(
        ADDITIONAL_CONFIG_PATH,
        ADDITIONAL_CONFIG_PATH.parent,
        modes=(0o600, 0o640, 0o644),
    )
    raw = ADDITIONAL_CONFIG_PATH.read_bytes()
    text = raw.decode("utf-8", errors="strict")
    documents = load_additional_yaml_documents(text)
    paths = flattened_yaml_paths(documents)
    if paths != EXPECTED_ADDITIONAL_CONFIG_PATHS:
        raise RuntimeError(
            "additional config semantic surface is outside the allowlist"
        )
    root = documents[0] if len(documents) == 1 else None
    try:
        ingest_enabled = root["fbsir"]["connector-insights"][
            "ingest-enabled"
        ]
    except (KeyError, TypeError) as error:
        raise RuntimeError(
            "additional config allowlisted leaf is absent"
        ) from error
    if not isinstance(ingest_enabled, bool):
        raise RuntimeError(
            "additional config allowlisted leaf must be boolean"
        )
    return sha256_bytes(raw)


def external_config_manifest():
    """Reject every Spring default-search config except the reviewed file."""
    candidates = []
    for entry in ADMIN_ROOT.iterdir():
        if EXTERNAL_CONFIG_NAME_PATTERN.fullmatch(entry.name):
            candidates.append(entry)
    config_root = ADMIN_ROOT / "config"
    if config_root.exists() or config_root.is_symlink():
        if config_root.is_symlink() or not config_root.is_dir():
            raise RuntimeError(
                "Spring default config directory custody is invalid"
            )
        for current, directories, files in os.walk(
            config_root, followlinks=False
        ):
            current_path = pathlib.Path(current)
            current_status = current_path.lstat()
            if (
                not stat.S_ISDIR(current_status.st_mode)
                or current_status.st_uid != 0
                or current_status.st_gid != 0
                or current_status.st_mode & 0o022
            ):
                raise RuntimeError(
                    "Spring default config directory custody is invalid"
                )
            for name in directories:
                if (current_path / name).is_symlink():
                    raise RuntimeError(
                        "Spring default config tree contains a symlink"
                    )
            for name in files:
                if EXTERNAL_CONFIG_NAME_PATTERN.fullmatch(name):
                    candidates.append(current_path / name)
    resolved_allowed = ADDITIONAL_CONFIG_PATH.resolve()
    manifest = []
    seen = set()
    for candidate in candidates:
        validate_regular_file(
            candidate,
            candidate.parent,
            modes=(0o600, 0o640, 0o644),
        )
        resolved = candidate.resolve()
        if resolved != resolved_allowed or resolved in seen:
            raise RuntimeError(
                "unreviewed Spring external config is present"
            )
        seen.add(resolved)
        manifest.append(
            {
                "path": str(candidate),
                "sha256": validate_additional_config(),
                "mode": candidate.stat().st_mode & 0o777,
            }
        )
    if seen != {resolved_allowed}:
        raise RuntimeError(
            "reviewed Spring additional config is absent"
        )
    return sorted(manifest, key=lambda item: item["path"])


def candidate_process_arguments():
    return [
        "/usr/bin/java",
        "-Dspring.config.additional-location=file:"
        + str(ADDITIONAL_CONFIG_PATH),
        "-jar",
        str(CURRENT_LINK / "backend/fbsir-admin.jar"),
    ]


def process_environment(main_pid, require_active):
    values = {}
    if main_pid <= 0:
        if require_active:
            raise RuntimeError("active U3W process environment is absent")
        return values
    path = pathlib.Path("/proc") / str(main_pid) / "environ"
    try:
        status = path.stat()
        raw = path.read_bytes()
        if status.st_uid != 0:
            raise RuntimeError("active U3W process environment has unsafe owner")
        for item in raw.split(b"\0"):
            if not item:
                continue
            name_bytes, separator, value_bytes = item.partition(b"=")
            if not separator:
                raise RuntimeError("active U3W process environment is invalid")
            name = name_bytes.decode("utf-8", errors="strict")
            value = value_bytes.decode("utf-8", errors="strict")
            if name in values:
                raise RuntimeError(
                    "active U3W process environment has duplicate keys"
                )
            values[name] = value
    except (OSError, UnicodeError) as error:
        if require_active:
            raise RuntimeError(
                "active U3W process environment is unreadable"
            ) from error
    return values


def decode_secret_material(encoded):
    value = str(encoded or "").strip()
    if not value:
        return None
    try:
        if value.startswith("base64:"):
            raw = value[7:]
            if len(raw) % 4 == 1:
                return None
            padded = raw + ("=" * (-len(raw) % 4))
            return base64.b64decode(padded, validate=True)
        if value.startswith("hex:"):
            raw = value[4:]
            if (
                not raw
                or len(raw) % 2
                or re.fullmatch(r"[0-9a-fA-F]+", raw) is None
            ):
                return None
            return bytes.fromhex(raw)
        if value.startswith("utf8:"):
            value = value[5:]
        return value.encode("utf-8")
    except (ValueError, UnicodeError):
        return None


def security_configuration_evidence(process_values, expected_values):
    """Bind effective process credentials without serializing their values."""
    event_key_manifest = root_regular_manifest(
        API2_EVENT_KEY_PATH, modes=(0o600,)
    )
    key = API2_EVENT_KEY_PATH.read_bytes()
    if not key:
        raise RuntimeError("API2 event key material is empty")
    configured_event_key = decode_secret_material(
        expected_values.get(EVENT_KEY_NAME)
    )
    if (
        configured_event_key is None
        or not hmac.compare_digest(configured_event_key, key)
    ):
        raise RuntimeError("configured event key does not match API2 material")
    expected_names = sorted(
        name
        for name in PROCESS_SECURITY_ENVIRONMENT_NAMES
        if name in expected_values
    )
    actual_names = sorted(
        name
        for name in PROCESS_SECURITY_ENVIRONMENT_NAMES
        if name in process_values
    )

    def configuration_hmac(values, names):
        payload = canonical_json(
            [[name, values[name]] for name in names]
        ).encode("utf-8")
        return hmac.new(
            key,
            b"fbsir.u3wProcessSecurityConfiguration.v1\0" + payload,
            hashlib.sha256,
        ).hexdigest()

    expected_hmac = configuration_hmac(expected_values, expected_names)
    actual_hmac = (
        configuration_hmac(process_values, actual_names)
        if actual_names
        else None
    )
    expected_database_names = sorted(
        name
        for name in (
            DATABASE_ENVIRONMENT_NAMES
            + DATABASE_ENVIRONMENT_ALIAS_NAMES
        )
        if name in expected_values
    )
    actual_database_names = sorted(
        name
        for name in expected_database_names
        if name in process_values
    )
    expected_database_hmac = configuration_hmac(
        expected_values, expected_database_names
    )
    actual_database_hmac = (
        configuration_hmac(process_values, actual_database_names)
        if actual_database_names else None
    )
    matched = bool(
        actual_database_hmac is not None
        and actual_database_names == expected_database_names
        and hmac.compare_digest(
            actual_database_hmac, expected_database_hmac
        )
        and all(
            name in process_values
            and hmac.compare_digest(
                process_values[name], expected_values[name]
            )
            for name in DATABASE_ENVIRONMENT_NAMES
        )
    )
    configured_names = sorted(expected_values)
    process_configured_names = sorted(
        name for name in configured_names if name in process_values
    )
    configured_hmac = configuration_hmac(
        expected_values, configured_names
    )
    process_configured_hmac = (
        configuration_hmac(process_values, process_configured_names)
        if process_configured_names
        else None
    )
    configured_matched = bool(
        process_configured_hmac is not None
        and process_configured_names == configured_names
        and hmac.compare_digest(
            process_configured_hmac, configured_hmac
        )
    )
    mismatch_names = sorted(
        name
        for name in process_configured_names
        if not hmac.compare_digest(
            process_values[name], expected_values[name]
        )
    )
    pending_names = sorted(
        set(configured_names) - set(process_configured_names)
    )
    configured_flags = {
        name: expected_values.get(name) for name in FALSE_FLAGS
    }
    all_managed_configured = all(
        name in expected_values for name in MANAGED_W1A_ENVIRONMENT_NAMES
    )
    flags_configured_false = all(
        configured_flags[name] == "false" for name in FALSE_FLAGS
    )
    if configured_matched and not pending_names and not mismatch_names:
        load_state = "EXACT_CONFIGURED"
    elif (
        pending_names == sorted(MANAGED_W1A_ENVIRONMENT_NAMES)
        and not mismatch_names
        and all_managed_configured
        and flags_configured_false
        and matched
    ):
        load_state = "LEGACY_W1A_PENDING_RESTART"
    else:
        load_state = "INVALID_PARTIAL_OR_DRIFTED"
    pre_stage_compatible = load_state in {
        "EXACT_CONFIGURED",
        "LEGACY_W1A_PENDING_RESTART",
    }
    return {
        "api2EventKeyManifest": event_key_manifest,
        "processSecurityConfigurationNames": actual_names,
        "processSecurityConfigurationHmacSha256": actual_hmac,
        "expectedSecurityConfigurationNames": expected_names,
        "expectedSecurityConfigurationHmacSha256": expected_hmac,
        "processDatabaseBindingMatched": matched,
        "configuredEnvironmentSha256": sha256_file(ENV_PATH),
        "configuredEnvironmentNames": configured_names,
        "configuredEnvironmentHmacSha256": configured_hmac,
        "processConfiguredEnvironmentHmacSha256":
            process_configured_hmac,
        "processConfiguredEnvironmentMatched": configured_matched,
        "configuredFlagValues": configured_flags,
        "processConfiguredEnvironmentMismatchNames": mismatch_names,
        "processPendingRestartEnvironmentNames": pending_names,
        "processConfiguredEnvironmentLoadState": load_state,
        "processConfiguredEnvironmentPreStageCompatible":
            pre_stage_compatible,
    }


def unit_fragment_manifest(raw_path):
    path = pathlib.Path(str(raw_path or ""))
    if not path.is_absolute():
        raise RuntimeError("systemd unit fragment path is invalid")
    validate_regular_file(
        path, path.parent, modes=(0o600, 0o640, 0o644)
    )
    status = path.stat()
    return {
        "path": str(path),
        "sha256": sha256_file(path),
        "mode": status.st_mode & 0o777,
        "uid": status.st_uid,
        "gid": status.st_gid,
        "nlink": status.st_nlink,
    }


def root_regular_manifest(path, modes=(0o600, 0o640, 0o644)):
    candidate = pathlib.Path(path)
    if not candidate.is_absolute():
        raise RuntimeError("manifest path is not absolute")
    validate_regular_file(candidate, candidate.parent, modes=modes)
    status = candidate.stat()
    return {
        "path": str(candidate),
        "sha256": sha256_file(candidate),
        "mode": status.st_mode & 0o777,
        "uid": status.st_uid,
        "gid": status.st_gid,
        "nlink": status.st_nlink,
    }


def root_file_custody(path, modes=(0o600, 0o640, 0o644)):
    manifest = root_regular_manifest(path, modes=modes)
    return {
        "path": manifest["path"],
        "uid": manifest["uid"],
        "gid": manifest["gid"],
        "mode": oct(manifest["mode"]),
        "nlink": manifest["nlink"],
    }


def plan_dropin_manifest(raw_paths):
    return sorted(
        [
            root_regular_manifest(path)
            for path in str(raw_paths or "").split()
            if path
        ],
        key=lambda item: item["path"],
    )


def stable_root_regular_manifest(path, modes=(0o600, 0o640, 0o644)):
    candidate = pathlib.Path(path)
    if not candidate.is_absolute():
        raise RuntimeError("stable manifest path is not absolute")
    descriptor = os.open(
        candidate, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    )
    try:
        before = os.fstat(descriptor)
        if (
            not stat.S_ISREG(before.st_mode)
            or before.st_uid != 0
            or before.st_gid != 0
            or before.st_nlink != 1
            or before.st_mode & 0o777 not in modes
        ):
            raise RuntimeError("stable manifest custody is invalid")
        digest = hashlib.sha256()
        size = 0
        while True:
            block = os.read(descriptor, 1024 * 1024)
            if not block:
                break
            digest.update(block)
            size += len(block)
        after = os.fstat(descriptor)
        identity = lambda value: (
            value.st_dev,
            value.st_ino,
            value.st_mode,
            value.st_uid,
            value.st_gid,
            value.st_nlink,
            value.st_size,
            value.st_mtime_ns,
            value.st_ctime_ns,
        )
        if identity(before) != identity(after) or size != after.st_size:
            raise RuntimeError("stable manifest changed while reading")
        return {
            "path": str(candidate),
            "sha256": digest.hexdigest(),
            "mode": after.st_mode & 0o777,
            "uid": after.st_uid,
            "gid": after.st_gid,
            "nlink": after.st_nlink,
            "sizeBytes": size,
        }
    finally:
        os.close(descriptor)


def nginx_dump_bytes():
    result = subprocess.run(
        ["nginx", "-T"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    return result.stdout


def active_nginx_manifest():
    dump_bytes = nginx_dump_bytes()
    dump = dump_bytes.decode("utf-8", errors="strict")
    paths = sorted(set(re.findall(
        r"^# configuration file ([^:]+):$", dump, re.MULTILINE
    )))
    manifests = []
    for path in paths:
        manifests.append(stable_root_regular_manifest(path))
    if not manifests:
        raise RuntimeError("active Nginx configuration manifest is empty")
    if not hmac.compare_digest(dump_bytes, nginx_dump_bytes()):
        raise RuntimeError("active Nginx configuration changed during snapshot")
    return manifests, sha256_bytes(dump_bytes)


def release_root_entry_manifest():
    if not RELEASE_ROOT.exists() and not RELEASE_ROOT.is_symlink():
        return []
    status = RELEASE_ROOT.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o755
    ):
        raise RuntimeError("release root custody is invalid")
    result = []
    for entry in sorted(RELEASE_ROOT.iterdir(), key=lambda item: item.name):
        entry_status = entry.lstat()
        item = {
            "name": entry.name,
            "uid": entry_status.st_uid,
            "gid": entry_status.st_gid,
            "mode": entry_status.st_mode & 0o777,
            "nlink": entry_status.st_nlink,
        }
        if (
            entry_status.st_uid != 0
            or entry_status.st_gid != 0
            or (
                not stat.S_ISLNK(entry_status.st_mode)
                and entry_status.st_mode & 0o022
            )
        ):
            raise RuntimeError("release root entry custody is invalid")
        if stat.S_ISDIR(entry_status.st_mode):
            item["type"] = "directory"
        elif stat.S_ISLNK(entry_status.st_mode):
            if entry != LATEST_RECEIPT:
                raise RuntimeError("unexpected release root symlink")
            resolved = entry.resolve(strict=True)
            if RELEASE_ROOT.resolve(strict=True) not in resolved.parents:
                raise RuntimeError("latest release receipt escaped release root")
            validate_regular_file(
                resolved, resolved.parent, modes=(0o600,)
            )
            item.update({
                "type": "symlink",
                "target": os.readlink(entry),
                "targetSha256": sha256_file(resolved),
            })
        elif stat.S_ISREG(entry_status.st_mode):
            item.update({
                "type": "file",
                "sha256": stable_root_regular_manifest(entry)["sha256"],
            })
        else:
            raise RuntimeError("unsupported release root entry type")
        result.append(item)
    return result


def normalized_environment_files(raw_value):
    """Parse systemd EnvironmentFiles without exposing file contents."""
    value = str(raw_value or "")
    declarations = []
    position = 0
    item_pattern = re.compile(
        r"""\s*
        (?P<token>
            -?
            (?:
                "(?:[^"\\]|\\.)*"
                |
                '(?:[^'\\]|\\.)*'
                |
                [^\s()]+
            )
        )
        (?:\s+\(ignore_errors=(?P<ignore_errors>yes|no)\))?
        """,
        re.VERBOSE,
    )
    while position < len(value):
        if not value[position:].strip():
            break
        match = item_pattern.match(value, position)
        if match is None:
            raise RuntimeError(
                "systemd EnvironmentFiles declaration is invalid"
            )
        token = match.group("token")
        optional_prefix = token.startswith("-")
        if optional_prefix:
            token = token[1:]
        if (
            len(token) >= 2
            and token[0] in ("'", '"')
            and token[-1] == token[0]
        ):
            token = token[1:-1]
        if not token or "\\" in token:
            raise RuntimeError(
                "systemd EnvironmentFiles path is unsupported"
            )
        marker = match.group("ignore_errors")
        marker_optional = marker == "yes"
        if optional_prefix and marker == "no":
            raise RuntimeError(
                "systemd EnvironmentFiles optional marker conflicts"
            )
        declarations.append(
            {
                "path": token,
                "ignoreErrors": (
                    optional_prefix or marker_optional
                ),
            }
        )
        position = match.end()
    if declarations != [{
        "path": ENV_PATH_TEXT,
        "ignoreErrors": False,
    }]:
        raise RuntimeError(
            "fbsir-admin requires exactly one mandatory fixed EnvironmentFile"
        )
    return declarations


def environment_file_manifest(raw_value):
    declarations = normalized_environment_files(raw_value)
    validate_regular_file(ENV_PATH, ENV_PATH.parent, modes=(0o600,))
    status = ENV_PATH.stat()
    return [
        {
            "path": declarations[0]["path"],
            "ignoreErrors": declarations[0]["ignoreErrors"],
            "sha256": sha256_file(ENV_PATH),
            "mode": status.st_mode & 0o777,
            "uid": status.st_uid,
            "gid": status.st_gid,
            "nlink": status.st_nlink,
        }
    ]


def dropin_manifest(raw_paths):
    manifest = []
    for raw_path in str(raw_paths or "").split():
        path = pathlib.Path(raw_path)
        validate_regular_file(
            path, DROPIN_DIRECTORY, modes=(0o600, 0o640, 0o644)
        )
        manifest.append(
            {
                "path": str(path),
                "sha256": sha256_file(path),
                "mode": path.stat().st_mode & 0o777,
            }
        )
    return sorted(manifest, key=lambda item: item["path"])


def service_snapshot(require_active=True):
    fields = (
        "ActiveState,MainPID,User,Group,FragmentPath,DropInPaths,"
        "EnvironmentFiles,ExecStart,WorkingDirectory,InvocationID,"
        "ExecMainStartTimestampMonotonic,NRestarts"
    )
    result = subprocess.run(
        ["systemctl", "show", SERVICE_UNIT, "--property=" + fields],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError("systemctl show failed")
    values = {}
    for line in result.stdout.splitlines():
        key, _, value = line.partition("=")
        values[key] = value
    jar_match = re.search(
        r"(/[A-Za-z0-9._/-]+\.jar)", values.get("ExecStart", "")
    )
    configured_jar = (
        pathlib.Path(jar_match.group(1)) if jar_match else None
    )
    main_pid = int(values.get("MainPID") or "0")
    process_jar = None
    process_arguments = []
    if main_pid > 0:
        cmdline_path = pathlib.Path("/proc") / str(main_pid) / "cmdline"
        cmdline_status = None
        try:
            cmdline_status = cmdline_path.stat()
            raw_cmdline = cmdline_path.read_bytes()
            process_arguments = [
                value.decode("utf-8", errors="strict")
                for value in raw_cmdline.split(b"\0")
                if value
            ]
        except (OSError, UnicodeError) as error:
            if require_active:
                raise RuntimeError(
                    "active U3W process command line is unreadable"
                ) from error
        if (
            cmdline_status is None
            or cmdline_status.st_uid != 0
            or not process_arguments
            or "-jar" not in process_arguments
        ):
            if require_active:
                raise RuntimeError(
                    "active U3W process command line is invalid"
                )
        else:
            jar_index = process_arguments.index("-jar") + 1
            for argument in process_arguments[jar_index:]:
                if argument.startswith("/") and argument.endswith(".jar"):
                    process_jar = pathlib.Path(argument)
                    break
    process_values = process_environment(main_pid, require_active)
    expected_environment, _ = parse_environment()
    security_evidence = security_configuration_evidence(
        process_values, expected_environment
    )
    process_flag_values = {
        name: process_values.get(name) for name in FALSE_FLAGS
    }
    process_environment_names = sorted(process_values)
    normalized_flag_names = {
        re.sub(r"[^a-z0-9]", "", name.lower())
        for name in FALSE_FLAGS
    }
    normalized_java_override_names = {
        re.sub(r"[^a-z0-9]", "", name.lower())
        for name in (
            "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS",
            "JDK_JAVA_OPTIONS",
        )
    }
    forbidden_override_names = sorted(
        name
        for name in process_values
        if (
            name in FORBIDDEN_RUNTIME_OVERRIDE_NAMES
            or (
                name not in FALSE_FLAGS
                and re.sub(r"[^a-z0-9]", "", name.lower())
                in normalized_flag_names
            )
            or re.sub(r"[^a-z0-9]", "", name.lower()).startswith(
                "spring"
            )
            or re.sub(r"[^a-z0-9]", "", name.lower())
                in normalized_java_override_names
        )
    )
    manifest = dropin_manifest(values.get("DropInPaths"))
    fragment_manifest = unit_fragment_manifest(values.get("FragmentPath"))
    environment_manifest = environment_file_manifest(
        values.get("EnvironmentFiles")
    )
    external_configs = external_config_manifest()
    additional_config_sha256 = external_configs[0]["sha256"]
    effective_jar = process_jar or configured_jar
    if (
        (require_active and values.get("ActiveState") != "active")
        or values.get("User") not in ("", "root")
        or values.get("Group") not in ("", "root")
        or not configured_jar
        or not configured_jar.is_file()
        or not effective_jar
        or not effective_jar.is_file()
        or (
            require_active
            and (
                not process_jar
                or process_jar.resolve() != configured_jar.resolve()
            )
        )
    ):
        raise RuntimeError("active U3W service identity is invalid")
    snapshot = {
        "activeState": values["ActiveState"],
        "mainPid": main_pid,
        "user": values.get("User") or "root",
        "group": values.get("Group") or "root",
        "fragmentPath": values.get("FragmentPath"),
        "fragmentFileManifest": fragment_manifest,
        "dropInPaths": values.get("DropInPaths"),
        "environmentFiles": values.get("EnvironmentFiles"),
        "environmentFilePaths": [
            item["path"] for item in environment_manifest
        ],
        "environmentFileManifest": environment_manifest,
        "execStart": values.get("ExecStart"),
        "workingDirectory": values.get("WorkingDirectory"),
        "invocationId": values.get("InvocationID", "").lower(),
        "execMainStartTimestampMonotonic": int(
            values.get("ExecMainStartTimestampMonotonic") or "0"
        ),
        "nRestarts": int(values.get("NRestarts") or "0"),
        "configuredJarPath": str(configured_jar),
        "configuredJarSha256": sha256_file(configured_jar),
        "processJarPath": str(process_jar) if process_jar else None,
        "processJarSha256": (
            sha256_file(process_jar) if process_jar else None
        ),
        "processArgvSha256": sha256_bytes(
            ("\0".join(process_arguments) + "\0").encode("utf-8")
        ) if process_arguments else None,
        "processEnvironmentNamesSha256": sha256_bytes(
            ("\n".join(process_environment_names) + "\n").encode("utf-8")
        ) if process_environment_names else None,
        "processFlagValues": process_flag_values,
        "processForbiddenOverrideNames": forbidden_override_names,
        "dropInManifest": manifest,
        "externalConfigManifest": external_configs,
        "additionalConfigSha256": additional_config_sha256,
        "jarPath": str(effective_jar),
        "jarSha256": sha256_file(effective_jar),
    }
    snapshot.update(security_evidence)
    return snapshot


def stage_plan_anchor(prior_rollback_anchor):
    if prior_rollback_anchor is None:
        return None
    fields = (
        "releaseId",
        "sourceCommit",
        "receiptPath",
        "receiptSha256",
        "receiptSchema",
        "state",
    )
    if (
        not isinstance(prior_rollback_anchor, dict)
        or any(field not in prior_rollback_anchor for field in fields)
    ):
        raise RuntimeError("prior rollback Plan anchor is invalid")
    return {
        field: prior_rollback_anchor[field]
        for field in fields
    }


def stage_live_plan_target(before_service, prior_rollback_anchor):
    nginx_manifest, nginx_dump_sha256 = active_nginx_manifest()
    dropins = plan_dropin_manifest(before_service.get("dropInPaths"))
    fragment = before_service["fragmentFileManifest"]
    unit_files = sorted(
        [
            {"path": item["path"], "sha256": item["sha256"]}
            for item in [fragment] + dropins
        ],
        key=lambda item: item["path"],
    )
    plan_anchor = stage_plan_anchor(prior_rollback_anchor)
    topology = {
        "state": (
            "EXACT_PRIOR_ROLLBACK_PREDECESSOR"
            if plan_anchor is not None
            else "UNTOUCHED_LEGACY"
        ),
        "priorRollbackAnchor": plan_anchor,
    }
    current_exists = CURRENT_LINK.exists() or CURRENT_LINK.is_symlink()
    current_resolved = None
    if CURRENT_LINK.is_symlink():
        current_resolved = str(CURRENT_LINK.resolve(strict=True))
    service = {
        "ActiveState": before_service["activeState"],
        "MainPID": str(before_service["mainPid"]),
        "User": before_service["user"],
        "Group": before_service["group"],
        "FragmentPath": before_service["fragmentPath"],
        "DropInPaths": before_service["dropInPaths"],
        "EnvironmentFiles": before_service["environmentFiles"],
        "ExecStart": before_service["execStart"],
        "WorkingDirectory": before_service["workingDirectory"],
        "InvocationID": before_service["invocationId"],
        "ExecMainStartTimestampMonotonic": str(
            before_service["execMainStartTimestampMonotonic"]
        ),
        "NRestarts": str(before_service["nRestarts"]),
    }
    target = {
        "schema": "fbsir.u3wDefaultOffRemotePlanSnapshot.v1",
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "service": service,
        "unitSha256": fragment["sha256"],
        "fragmentFileManifest": fragment,
        "dropInManifest": dropins,
        "unitFiles": unit_files,
        "environmentCustody": root_file_custody(
            ENV_PATH, modes=(0o600,)
        ),
        "environmentSha256":
            before_service["configuredEnvironmentSha256"],
        "environmentFilePaths":
            before_service["environmentFilePaths"],
        "environmentFileManifest":
            before_service["environmentFileManifest"],
        "additionalConfigCustody":
            root_file_custody(ADDITIONAL_CONFIG_PATH),
        "additionalConfigSha256":
            before_service["additionalConfigSha256"],
        "externalConfigManifest":
            before_service["externalConfigManifest"],
        "activeJarPath": before_service["processJarPath"],
        "activeJarSha256": before_service["processJarSha256"],
        "configuredJarPath": before_service["configuredJarPath"],
        "configuredJarSha256": before_service["configuredJarSha256"],
        "processJarPath": before_service["processJarPath"],
        "processJarSha256": before_service["processJarSha256"],
        "processArgvSha256": before_service["processArgvSha256"],
        "processEnvironmentNamesSha256":
            before_service["processEnvironmentNamesSha256"],
        "processFlagValues": before_service["processFlagValues"],
        "configuredFlagValues": before_service["configuredFlagValues"],
        "processForbiddenOverrideNames":
            before_service["processForbiddenOverrideNames"],
        "api2EventKeyManifest":
            before_service["api2EventKeyManifest"],
        "processSecurityConfigurationNames":
            before_service["processSecurityConfigurationNames"],
        "processSecurityConfigurationHmacSha256":
            before_service[
                "processSecurityConfigurationHmacSha256"
            ],
        "expectedSecurityConfigurationNames":
            before_service["expectedSecurityConfigurationNames"],
        "expectedSecurityConfigurationHmacSha256":
            before_service[
                "expectedSecurityConfigurationHmacSha256"
            ],
        "processDatabaseBindingMatched":
            before_service["processDatabaseBindingMatched"],
        "configuredEnvironmentSha256":
            before_service["configuredEnvironmentSha256"],
        "configuredEnvironmentNames":
            before_service["configuredEnvironmentNames"],
        "configuredEnvironmentHmacSha256":
            before_service["configuredEnvironmentHmacSha256"],
        "processConfiguredEnvironmentHmacSha256":
            before_service[
                "processConfiguredEnvironmentHmacSha256"
            ],
        "processConfiguredEnvironmentMatched":
            before_service["processConfiguredEnvironmentMatched"],
        "processConfiguredEnvironmentMismatchNames":
            before_service[
                "processConfiguredEnvironmentMismatchNames"
            ],
        "processPendingRestartEnvironmentNames":
            before_service[
                "processPendingRestartEnvironmentNames"
            ],
        "processConfiguredEnvironmentLoadState":
            before_service[
                "processConfiguredEnvironmentLoadState"
            ],
        "processConfiguredEnvironmentPreStageCompatible":
            before_service[
                "processConfiguredEnvironmentPreStageCompatible"
            ],
        "nginxConfigs": nginx_manifest,
        "activeNginxManifest": nginx_manifest,
        "nginxDumpSha256": nginx_dump_sha256,
        "releaseRootExists": RELEASE_ROOT.exists(),
        "releaseRootEntryManifest": release_root_entry_manifest(),
        "currentLinkExists": current_exists,
        "currentLinkResolved": current_resolved,
        "currentLifecycleState": (
            plan_anchor["state"]
            if plan_anchor is not None
            else "UNTOUCHED_LEGACY"
        ),
        "stageEntryTopology": topology,
        "productionChanged": plan_anchor is not None,
        "productionChangedByPlan": False,
    }
    if set(target) != PLAN_TARGET_FIELDS:
        raise RuntimeError("live Stage Plan target field set is invalid")
    return target


def stage_owned_release_root_delta_verified(
    args, planned_target, live_target
):
    planned_exists = planned_target.get("releaseRootExists")
    planned_entries = planned_target.get("releaseRootEntryManifest")
    live_entries = live_target.get("releaseRootEntryManifest")
    if (
        type(planned_exists) is not bool
        or not isinstance(planned_entries, list)
        or not isinstance(live_entries, list)
    ):
        return False
    status = RELEASE_ROOT.lstat()
    incoming = incoming_directory(args)
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o755
        or not incoming.is_dir()
        or incoming.is_symlink()
    ):
        return False
    incoming_entries = [
        item
        for item in live_entries
        if isinstance(item, dict) and item.get("name") == incoming.name
    ]
    if (
        len(incoming_entries) != 1
        or incoming_entries[0].get("type") != "directory"
        or incoming_entries[0].get("uid") != 0
        or incoming_entries[0].get("gid") != 0
        or incoming_entries[0].get("mode") != 0o700
    ):
        return False
    retained_entries = [
        item for item in live_entries
        if item.get("name") != incoming.name
    ]
    if planned_exists:
        return bool(
            planned_target.get("stageEntryTopology", {}).get("state")
            == "EXACT_PRIOR_ROLLBACK_PREDECESSOR"
            and live_target.get("stageEntryTopology", {}).get("state")
            == "EXACT_PRIOR_ROLLBACK_PREDECESSOR"
            and retained_entries == planned_entries
        )
    return planned_entries == [] and retained_entries == []


def assert_stage_plan_target_matches(
    args, planned_target, live_target
):
    topology_state = (
        planned_target.get("stageEntryTopology", {}).get("state")
        if isinstance(planned_target, dict) else None
    )
    expected_load_state = {
        "UNTOUCHED_LEGACY": "LEGACY_W1A_PENDING_RESTART",
        "EXACT_PRIOR_ROLLBACK_PREDECESSOR": "EXACT_CONFIGURED",
    }.get(topology_state)
    expected_pending_names = (
        sorted(MANAGED_W1A_ENVIRONMENT_NAMES)
        if expected_load_state == "LEGACY_W1A_PENDING_RESTART"
        else []
    )
    if (
        not isinstance(planned_target, dict)
        or not isinstance(live_target, dict)
        or set(planned_target) != PLAN_TARGET_FIELDS
        or set(live_target) != PLAN_TARGET_FIELDS
        or planned_target.get("schema")
            != "fbsir.u3wDefaultOffRemotePlanSnapshot.v1"
        or planned_target.get("targetHost") != TARGET_HOST
        or planned_target.get("serviceUnit") != SERVICE_UNIT
        or planned_target.get("productionChangedByPlan") is not False
        or planned_target.get("currentLinkExists") is not False
        or planned_target.get("currentLinkResolved") is not None
        or planned_target.get("processDatabaseBindingMatched") is not True
        or planned_target.get(
            "processConfiguredEnvironmentPreStageCompatible"
        ) is not True
        or planned_target.get(
            "processConfiguredEnvironmentMismatchNames"
        ) != []
        or expected_load_state is None
        or planned_target.get(
            "processConfiguredEnvironmentLoadState"
        ) != expected_load_state
        or planned_target.get(
            "processPendingRestartEnvironmentNames"
        ) != expected_pending_names
        or planned_target.get("processForbiddenOverrideNames") != []
        or any(
            planned_target.get("configuredFlagValues", {}).get(name)
                != "false"
            or planned_target.get("processFlagValues", {}).get(name)
                not in (None, "false")
            for name in FALSE_FLAGS
        )
    ):
        raise RuntimeError("release Plan target contract is invalid")
    planned_comparable = dict(planned_target)
    live_comparable = dict(live_target)
    for field in ("releaseRootExists", "releaseRootEntryManifest"):
        planned_comparable.pop(field)
        live_comparable.pop(field)
    planned_comparable_sha256 = sha256_bytes(
        canonical_json(planned_comparable).encode("utf-8")
    )
    live_comparable_sha256 = sha256_bytes(
        canonical_json(live_comparable).encode("utf-8")
    )
    release_root_delta_verified = (
        stage_owned_release_root_delta_verified(
            args, planned_target, live_target
        )
    )
    if (
        not release_root_delta_verified
        or not hmac.compare_digest(
            planned_comparable_sha256, live_comparable_sha256
        )
    ):
        raise RuntimeError("release Plan target drifted before FinalizeStage")
    return {
        "releasePlanTargetSha256": sha256_bytes(
            canonical_json(planned_target).encode("utf-8")
        ),
        "releasePlanTargetComparableSha256":
            planned_comparable_sha256,
        "finalizeStageLiveTargetComparableSha256":
            live_comparable_sha256,
        "stageOwnedReleaseRootDeltaVerified":
            release_root_delta_verified,
    }


def run_checked(arguments, timeout=120):
    result = subprocess.run(
        arguments,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "{} failed with stderr SHA-256 {}".format(
                arguments[0], sha256_bytes(result.stderr)
            )
        )
    return result


def tree_manifest(root):
    root = pathlib.Path(root).resolve(strict=True)
    if not root.is_dir() or root.is_symlink():
        raise RuntimeError("frontend tree root is invalid")
    lines = []
    total = 0
    count = 0
    for path in sorted(root.rglob("*"), key=lambda item: item.as_posix()):
        relative = path.relative_to(root).as_posix()
        if path.is_symlink():
            raise RuntimeError("frontend tree contains a symlink")
        if path.is_dir():
            continue
        if not path.is_file() or "\n" in relative or relative.startswith("../"):
            raise RuntimeError("frontend tree contains an unsafe entry")
        status = path.stat()
        if status.st_uid != 0 or status.st_gid != 0 or status.st_nlink != 1:
            raise RuntimeError("frontend file custody is invalid")
        lines.append("{}  {}".format(sha256_file(path), relative))
        total += status.st_size
        count += 1
    if count == 0:
        raise RuntimeError("frontend tree is empty")
    manifest = ("\n".join(lines) + "\n").encode("utf-8")
    return {
        "algorithm": "u3w.sorted-posix-tree-sha256.v1",
        "sha256": sha256_bytes(manifest),
        "fileCount": count,
        "totalBytes": total,
        "manifest": manifest,
    }


def extract_frontend_archive(incoming):
    """Extract a regular-file-only tar into the isolated candidate tree."""
    incoming = pathlib.Path(incoming)
    archive_path = validate_regular_file(
        incoming / "evidence/frontend.tar",
        incoming / "evidence",
        modes=(0o600, 0o644),
    )
    frontend = incoming / "frontend"
    safe_directory(frontend, 0o700)
    if any(frontend.iterdir()):
        raise RuntimeError("frontend destination is not empty")
    entries = set()
    total_size = 0
    with tarfile.open(archive_path, mode="r:") as archive:
        members = archive.getmembers()
        if len(members) > 10000:
            raise RuntimeError("frontend archive has too many entries")
        for member in members:
            name = member.name.replace("\\", "/")
            while name.startswith("./"):
                name = name[2:]
            if not name or name == ".":
                continue
            relative = pathlib.PurePosixPath(name)
            if (
                relative.is_absolute()
                or ".." in relative.parts
                or "\n" in name
                or "\x00" in name
                or name in entries
            ):
                raise RuntimeError("frontend archive path is unsafe")
            entries.add(name)
            target = frontend.joinpath(*relative.parts)
            if member.isdir():
                target.mkdir(mode=0o700, parents=True, exist_ok=True)
                continue
            if not member.isfile():
                raise RuntimeError("frontend archive contains a non-file entry")
            if member.size < 0 or member.size > 256 * 1024 * 1024:
                raise RuntimeError("frontend archive member size is invalid")
            total_size += member.size
            if total_size > 1024 * 1024 * 1024:
                raise RuntimeError("frontend archive is too large")
            target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            source = archive.extractfile(member)
            if source is None:
                raise RuntimeError("frontend archive member is unreadable")
            payload = source.read(member.size + 1)
            if len(payload) != member.size:
                raise RuntimeError("frontend archive member length drifted")
            atomic_bytes(target, payload, 0o644)
    if not entries:
        raise RuntimeError("frontend archive is empty")
    return frontend


def make_frontend_public(release):
    """Give Nginx read/traverse access without exposing private evidence."""
    release = pathlib.Path(release)
    frontend = release / "frontend"
    for path in sorted(
        frontend.rglob("*"), key=lambda item: item.as_posix(), reverse=True
    ):
        if path.is_symlink():
            raise RuntimeError("frontend tree contains a symlink")
        if path.is_dir():
            os.chmod(path, 0o755)
        elif path.is_file():
            os.chmod(path, 0o644)
        else:
            raise RuntimeError("frontend tree contains an unsafe entry")
    os.chmod(frontend, 0o755)
    os.chmod(release, 0o755)


def portal_nginx_config(release_directory):
    frontend = release_directory / "frontend"
    return """server {{
    listen 80;
    server_name admin.u3w.com;
    return 301 https://$host$request_uri;
}}

server {{
    listen 443 ssl;
    http2 on;
    server_name admin.u3w.com;
    ssl_certificate /etc/nginx/ssl/admin.u3w.com/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/admin.u3w.com/privkey.pem;
    root {frontend};
    location / {{
        try_files $uri $uri/ /index.html;
    }}
    location /prod-api/ {{
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_pass http://127.0.0.1:8080/;
    }}
}}

server {{
    listen 80;
    server_name me.u3w.com;
    return 301 https://$host$request_uri;
}}

server {{
    listen 443 ssl;
    http2 on;
    server_name me.u3w.com;
    ssl_certificate /etc/nginx/ssl/me.u3w.com/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/me.u3w.com/privkey.pem;
    root {frontend};
    location / {{
        try_files $uri $uri/ /index.html;
    }}
    location /prod-api/ {{
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_pass http://127.0.0.1:8080/;
    }}
}}
""".format(frontend=frontend)


def systemd_dropin():
    return """[Service]
ExecStart=
ExecStart=/usr/bin/java -Dspring.config.additional-location=file:/opt/fbsir/admin/application-connector.yml -jar /opt/fbsir/admin/current/backend/fbsir-admin.jar
WorkingDirectory=/opt/fbsir/admin
"""


def rollback_systemd_dropin(release_directory):
    predecessor = pathlib.PurePosixPath(
        str(release_directory).replace("\\", "/")
    ) / "rollback/previous-admin.jar"
    return """[Service]
ExecStart=
ExecStart=/usr/bin/java -Dspring.config.additional-location=file:/opt/fbsir/admin/application-connector.yml -jar {}
WorkingDirectory=/opt/fbsir/admin
""".format(predecessor)


def release_artifact_manifest(root):
    root = pathlib.Path(root).resolve(strict=True)
    result = {}
    for relative in RELEASE_ARTIFACT_RELATIVE_PATHS:
        path = root.joinpath(*pathlib.PurePosixPath(relative).parts)
        if root not in path.resolve(strict=True).parents:
            raise RuntimeError("release artifact escaped release root")
        manifest = stable_root_regular_manifest(path)
        result[relative] = {
            key: manifest[key]
            for key in (
                "sha256",
                "mode",
                "uid",
                "gid",
                "nlink",
                "sizeBytes",
            )
        }
    return result


def frontend_tree_evidence(root):
    root = pathlib.Path(root)
    first = tree_manifest(root / "frontend")
    second = tree_manifest(root / "frontend")
    comparable_fields = (
        "algorithm",
        "sha256",
        "fileCount",
        "totalBytes",
        "manifest",
    )
    if any(first[field] != second[field] for field in comparable_fields):
        raise RuntimeError("frontend tree changed while reading")
    manifest_path = root / "evidence/frontend-manifest.txt"
    validate_regular_file(
        manifest_path, manifest_path.parent, modes=(0o600,)
    )
    if manifest_path.read_bytes() != first["manifest"]:
        raise RuntimeError("frontend manifest no longer matches the tree")
    return {
        field: first[field]
        for field in (
            "algorithm",
            "sha256",
            "fileCount",
            "totalBytes",
        )
    }


def validate_release_marker(path, args):
    validate_regular_file(path, path.parent, modes=(0o600, 0o644))
    marker = read_json(path)
    if (
        set(marker)
        != {
            "schema",
            "releaseId",
            "sourceCommit",
            "officialExpertsPackageChanged",
        }
        or marker.get("schema") != "fbsir.u3wReleaseMarker.v1"
        or marker.get("releaseId") != args.release_id
        or marker.get("sourceCommit") != args.source_commit
        or marker.get("officialExpertsPackageChanged") is not False
    ):
        raise RuntimeError("frontend release marker identity drifted")
    return marker


def http_json(url, timeout=5):
    class RejectRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(
            self, request, file_pointer, code, message, headers, new_url
        ):
            return None

    request = urllib.request.Request(
        url, headers={"User-Agent": "u3w-release-verifier/1"}
    )
    opener = urllib.request.build_opener(RejectRedirect())
    try:
        with opener.open(request, timeout=timeout) as response:
            payload = response.read(1024 * 1024)
            status = response.status
    except urllib.error.HTTPError as error:
        payload = error.read(1024 * 1024)
        status = error.code
    try:
        parsed = json.loads(payload.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError):
        parsed = None
    return status, parsed


def wait_for_u3w_health(timeout=180):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            last = http_json("http://127.0.0.1:8080/captchaImage")
            if (
                last[0] == 200
                and isinstance(last[1], dict)
                and last[1].get("code") == 200
            ):
                return True
        except (OSError, ValueError):
            pass
        time.sleep(2)
    raise RuntimeError("U3W captcha readiness did not become healthy")


def validate_approval(args):
    if args.approval_sha == "0" * 64 or not args.approval_json_base64:
        raise RuntimeError("release mutation requires approval")
    try:
        raw = base64.b64decode(args.approval_json_base64, validate=True)
        approval = json.loads(raw.decode("utf-8-sig"))
    except (ValueError, UnicodeError, json.JSONDecodeError) as error:
        raise RuntimeError("approval receipt cannot be decoded") from error
    fields = {
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
        "expectedBuildReceiptSha256",
        "expectedReleasePlanReceiptSha256",
        "expectedBackupReceiptSha256",
        "expectedLegacyBaselineReceiptSha256",
        "expectedConfigurationReceiptSha256",
        "expectedStageReceiptSha256",
        "expectedDeploymentReceiptSha256",
        "runnerSha256",
        "workerSha256",
    }
    if set(approval) != fields:
        raise RuntimeError("approval receipt has missing or unknown fields")
    mode = "Stage" if args.mode in ("PrepareStage", "FinalizeStage") else args.mode
    actions = {
        "Stage": "STAGE_W1A_DEFAULT_OFF_RELEASE",
        "Apply": "APPLY_W1A_DEFAULT_OFF_RELEASE",
        "Rollback": "ROLLBACK_W1A_DEFAULT_OFF_RELEASE",
        "Verify": "VERIFY_W1A_DEFAULT_OFF_RELEASE",
    }
    filesystem_write = mode in ("Stage", "Apply", "Rollback")
    database_write = mode == "Apply"
    service_change = mode in ("Apply", "Rollback")
    try:
        approved = dt.datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        expires = dt.datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
    except (TypeError, ValueError) as error:
        raise RuntimeError("approval time is invalid") from error
    current = dt.datetime.now(dt.timezone.utc)
    expected = {
        "expectedBuildReceiptSha256": args.build_receipt_sha,
        "expectedReleasePlanReceiptSha256": args.plan_receipt_sha,
        "expectedBackupReceiptSha256": args.backup_receipt_sha,
        "expectedLegacyBaselineReceiptSha256": args.baseline_receipt_sha,
        "expectedConfigurationReceiptSha256": args.configuration_receipt_sha,
        "expectedStageReceiptSha256": args.stage_receipt_sha,
        "expectedDeploymentReceiptSha256": args.deployment_receipt_sha,
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
    }
    invalid = (
        sha256_bytes(raw) != args.approval_sha
        or approval["schema"]
        != "fbsir.u3wProductionChangeApprovalReceipt.v1"
        or approval["action"] != actions[mode]
        or approval["targetHost"] != TARGET_HOST
        or approval["runId"] != args.release_id
        or approval["sourceCommit"] != args.source_commit
        or approval["authorizedBy"] != "workspace-user"
        or approval["concurrentDdlProhibited"] is not True
        or approval["productionFilesystemWrite"] is not filesystem_write
        or approval["productionDatabaseWrite"] is not database_write
        or approval["productionServiceChange"] is not service_change
        or approval["officialExpertsPackageChange"] is not False
        or approved.tzinfo is None
        or expires.tzinfo is None
        or approved > current
        or expires <= current
        or expires - approved > dt.timedelta(hours=24)
        or any(approval[key] != value for key, value in expected.items())
    )
    if invalid:
        raise RuntimeError("release approval identity or scope is invalid")
    return approval


def validate_arguments(args):
    if not RUN_PATTERN.fullmatch(args.release_id):
        raise RuntimeError("release id is invalid")
    if not COMMIT_PATTERN.fullmatch(args.source_commit):
        raise RuntimeError("source commit is invalid")
    for name in (
        "approval_sha",
        "runner_sha",
        "worker_sha",
        "build_receipt_sha",
        "plan_receipt_sha",
        "backup_receipt_sha",
        "baseline_receipt_sha",
        "configuration_receipt_sha",
        "backend_sha",
        "frontend_tree_sha",
        "migration_sha",
    ):
        validate_sha(getattr(args, name), name)
    validate_sha(args.stage_receipt_sha, "stage_receipt_sha", allow_zero=True)
    validate_sha(
        args.deployment_receipt_sha,
        "deployment_receipt_sha",
        allow_zero=True,
    )
    validate_approval(args)


def incoming_directory(args):
    return RELEASE_ROOT / (".incoming-" + args.release_id)


def release_directory(args):
    return RELEASE_ROOT / args.release_id


def validate_preparation_anchors(args):
    checks = (
        (BACKUP_RECEIPT, args.backup_receipt_sha, "backup"),
        (BASELINE_RECEIPT, args.baseline_receipt_sha, "baseline"),
        (
            CONFIGURATION_RECEIPT,
            args.configuration_receipt_sha,
            "configuration",
        ),
    )
    for path, expected, label in checks:
        if not file_anchor_matches(path, expected):
            raise RuntimeError(label + " receipt anchor drifted")


def prepare_stage(args):
    validate_preparation_anchors(args)
    safe_directory(RELEASE_ROOT, 0o755)
    final = release_directory(args)
    incoming = incoming_directory(args)
    if final.exists() or final.is_symlink():
        receipt = final / "deployment-readiness-receipt.json"
        if (
            receipt.is_file()
            and read_json(receipt).get("state") == "STAGED_FOR_SWITCH"
            and not (final / "deployment-receipt.json").exists()
            and not (final / "rollback-receipt.json").exists()
        ):
            return {
                "schema": "fbsir.u3wDefaultOffReleaseWorkerResult.v1",
                "mode": "PrepareStage",
                "state": "ALREADY_STAGED",
                "releaseId": args.release_id,
                "sourceCommit": args.source_commit,
                "approvalReceiptSha256": args.approval_sha,
                "transitionApprovalReceiptSha256":
                    read_json(receipt).get(
                        "stageApprovalReceiptSha256"
                    ),
                "incomingPath": None,
                "productionFilesystemChanged": False,
                "productionDatabaseChanged": False,
                "productionServiceChanged": False,
                "officialExpertsPackageChanged": False,
            }
        raise RuntimeError("release destination already exists")
    if incoming.exists() or incoming.is_symlink():
        status = incoming.lstat()
        if (
            not incoming.is_dir()
            or incoming.is_symlink()
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_mode & 0o022
        ):
            raise RuntimeError("unsafe existing incoming directory")
        shutil.rmtree(incoming)
    safe_directory(incoming, 0o700)
    for relative in ("backend", "frontend", "sql", "evidence", "rollback"):
        safe_directory(incoming / relative, 0o700)
    return {
        "schema": "fbsir.u3wDefaultOffReleaseWorkerResult.v1",
        "mode": "PrepareStage",
        "state": "READY_FOR_UPLOAD",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "incomingPath": str(incoming),
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionDatabaseChangedThisRun": False,
        "productionDatabaseChangedSinceStage": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
    }


def jar_attribution_class_count(path):
    with zipfile.ZipFile(path) as archive:
        return sum(
            1
            for name in archive.namelist()
            if (
                "BoardAttribution" in name
                or "BoardIntentClassifier" in name
                or "BoardSameBinding" in name
            )
        )


def migration_structure_matches(facts, public_receipt_count=1):
    return bool(
        isinstance(facts, dict)
        and facts.get("publicReceiptCount") == public_receipt_count
        and facts.get("internalReceiptCount") == 1
        and facts.get("tableCount") == 2
        and facts.get("triggerCount") == 2
        and facts.get("permissionCount") == 1
        and type(facts.get("eventCount")) is int
        and facts.get("eventCount") >= 0
        and type(facts.get("journeyCount")) is int
        and facts.get("journeyCount") >= 0
        and facts.get("schemaFingerprintSha256")
            == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )


def migration_counts_are_monotonic(current, baseline):
    return bool(
        migration_structure_matches(current)
        and migration_structure_matches(baseline)
        and current["eventCount"] >= baseline["eventCount"]
        and current["journeyCount"] >= baseline["journeyCount"]
    )


def migration_facts_from_runtime_identity(identity):
    if not isinstance(identity, dict):
        raise RuntimeError("runtime migration identity is invalid")
    facts = {
        "publicReceiptCount": identity.get("public043ReceiptCount"),
        "internalReceiptCount":
            identity.get("attributionInternalReceiptCount"),
        "tableCount": identity.get("attributionTableCount"),
        "triggerCount": identity.get("attributionTriggerCount"),
        "permissionCount": identity.get("attributionPermissionCount"),
        "eventCount": identity.get("attributionEventCount"),
        "journeyCount": identity.get("attributionJourneyCount"),
        "schemaFingerprintSha256":
            identity.get("w1aSchemaFingerprintSha256"),
    }
    if not migration_structure_matches(facts):
        raise RuntimeError("runtime migration structure is not exact")
    return facts


def w1a_database_state(mysql):
    any_public_receipts = int(
        mysql.scalar(
            "SELECT COUNT(*) FROM u3w_schema_migration WHERE version=%s",
            (MIGRATION_PUBLIC_VERSION,),
        )
    )
    internal_receipts = int(
        mysql.scalar(
            "SELECT COUNT(*) FROM u3w_schema_migration "
            "WHERE version=%s AND description=%s",
            (MIGRATION_INTERNAL_VERSION, MIGRATION_INTERNAL_DESCRIPTION),
        )
    )
    table_count = int(
        mysql.scalar(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema=DATABASE() AND table_name IN "
            "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
        )
    )
    trigger_count = int(
        mysql.scalar(
            "SELECT COUNT(*) FROM information_schema.triggers "
            "WHERE trigger_schema=DATABASE() AND event_object_table IN "
            "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
        )
    )
    permission_count = int(
        mysql.scalar(
            "SELECT COUNT(*) FROM sys_menu WHERE "
            "BINARY perms=BINARY 'board:attribution:query'"
        )
    )
    if (
        any_public_receipts == 0
        and internal_receipts == 0
        and table_count == 0
        and trigger_count == 0
        and permission_count == 0
    ):
        return {
            "w1aDatabaseState": "ABSENT",
            "public043AnyReceiptCount": 0,
            "public043ReceiptCount": 0,
            "attributionInternalReceiptCount": 0,
            "attributionTableCount": 0,
            "attributionTriggerCount": 0,
            "attributionPermissionCount": 0,
            "attributionEventCount": 0,
            "attributionJourneyCount": 0,
            "w1aSchemaFingerprintSha256": None,
        }
    if table_count == 2:
        exact = exact_migration_facts(mysql)
        if (
            any_public_receipts == 1
            and migration_structure_matches(exact)
        ):
            return {
                "w1aDatabaseState": "EXACT_043_RETAINED_DORMANT",
                "public043AnyReceiptCount": any_public_receipts,
                "public043ReceiptCount": exact["publicReceiptCount"],
                "attributionInternalReceiptCount":
                    exact["internalReceiptCount"],
                "attributionTableCount": exact["tableCount"],
                "attributionTriggerCount": exact["triggerCount"],
                "attributionPermissionCount": exact["permissionCount"],
                "attributionEventCount": exact["eventCount"],
                "attributionJourneyCount": exact["journeyCount"],
                "w1aSchemaFingerprintSha256":
                    exact["schemaFingerprintSha256"],
            }
    raise RuntimeError("W1A database state is partial or drifted")


def database_pre_stage_facts():
    _, connection = parse_environment()
    mysql = Mysql(connection)
    try:
        identity_rows = mysql.rows(
            "SELECT LOWER(@@server_uuid),DATABASE(),@@version"
        )
        if len(identity_rows) != 1 or len(identity_rows[0]) != 3:
            raise RuntimeError("database identity query shape is invalid")
        legacy_rows = mysql.rows(
            "SELECT adoption_receipt_sha256 FROM "
            "u3w_legacy_schema_baseline_receipt_v2"
        )
        if len(legacy_rows) != 1 or len(legacy_rows[0]) != 1:
            raise RuntimeError("legacy baseline database anchor is invalid")
        configuration = read_json(CONFIGURATION_RECEIPT)
        validate_regular_file(
            API2_EVENT_KEY_PATH,
            API2_EVENT_KEY_PATH.parent,
            modes=(0o600,),
        )
        if (
            configuration.get("schema")
            != "fbsir.u3wDefaultOffConfigurationReceipt.v2"
            or configuration.get("environmentAfterSha256")
            != sha256_file(ENV_PATH)
            or configuration.get("api2EventKeyPath")
            != str(API2_EVENT_KEY_PATH)
            or configuration.get("stagedKeyMaterialMatched") is not True
        ):
            raise RuntimeError("configuration runtime anchor is invalid")
        facts = {
            "environmentSha256": sha256_file(ENV_PATH),
            "api2EventKeySha256": sha256_file(API2_EVENT_KEY_PATH),
            "additionalConfigSha256": validate_additional_config(),
            "databaseEndpoint": "{}:{}".format(
                connection["host"], connection["port"]
            ),
            "database": identity_rows[0][1],
            "databaseServerUuid": identity_rows[0][0],
            "databaseServerVersion": identity_rows[0][2],
            "legacyBaselineReceiptSha256": legacy_rows[0][0],
            "legacyBaselineMigrationCount": int(
                mysql.scalar(
                    "SELECT COUNT(*) FROM u3w_schema_migration "
                    "WHERE version='legacy_w1a_baseline_20260724_001' "
                    "AND description=CONCAT("
                    "'APPLIED:LEGACY_ADOPTED_W1A_V2:',%s)",
                    (legacy_rows[0][0],),
                )
            ),
        }
        facts.update(w1a_database_state(mysql))
        return facts
    finally:
        mysql.close()


def validate_stage_runtime_identity(args, stage):
    expected = stage.get("preStageRuntimeIdentity")
    if not isinstance(expected, dict):
        raise RuntimeError("staged runtime identity is absent")
    current = database_pre_stage_facts()
    validate_runtime_identity_anchors(args, expected, current)
    expected_state = expected.get("w1aDatabaseState")
    current_state = current.get("w1aDatabaseState")
    if expected_state == "ABSENT":
        retry_after_additive_043 = bool(
            current_state == "EXACT_043_RETAINED_DORMANT"
            and current.get("attributionEventCount") == 0
            and current.get("attributionJourneyCount") == 0
        )
        if current_state != "ABSENT" and not retry_after_additive_043:
            raise RuntimeError(
                "W1A database state drifted from the staged absent prestate"
            )
    elif expected_state == "EXACT_043_RETAINED_DORMANT":
        if (
            current_state != expected_state
            or current.get("attributionEventCount")
                != expected.get("attributionEventCount")
            or current.get("attributionJourneyCount")
                != expected.get("attributionJourneyCount")
        ):
            raise RuntimeError(
                "retained 043 ledger counts drifted after Stage"
            )
    else:
        raise RuntimeError("staged W1A database prestate is invalid")
    return current


def validate_runtime_identity_anchors(args, expected, current):
    stable_fields = (
        "environmentSha256",
        "api2EventKeySha256",
        "additionalConfigSha256",
        "databaseEndpoint",
        "database",
        "databaseServerUuid",
        "databaseServerVersion",
        "legacyBaselineReceiptSha256",
        "legacyBaselineMigrationCount",
    )
    if any(current.get(key) != expected.get(key) for key in stable_fields):
        raise RuntimeError("runtime identity drifted after Stage")
    if (
        current["legacyBaselineReceiptSha256"]
        != args.baseline_receipt_sha
        or current["legacyBaselineMigrationCount"] != 1
    ):
        raise RuntimeError("legacy baseline runtime anchor drifted")
    return current


def read_staged_release_plan(args, release):
    plan_path = validate_regular_file(
        release / "evidence/release-plan.json",
        release / "evidence",
        modes=(0o600, 0o644),
    )
    if sha256_file(plan_path) != args.plan_receipt_sha:
        raise RuntimeError("staged release Plan receipt anchor drifted")
    plan = read_json(plan_path)
    target = plan.get("target")
    if (
        plan.get("schema") != "fbsir.u3wDefaultOffReleasePlan.v1"
        or plan.get("sourceCommit") != args.source_commit
        or plan.get("releaseId") != args.release_id
        or plan.get("runnerSha256") != args.runner_sha
        or plan.get("workerSha256") != args.worker_sha
        or not isinstance(target, dict)
        or set(target) != PLAN_TARGET_FIELDS
    ):
        raise RuntimeError("staged release Plan identity drifted")
    return plan, target


def validate_staged_plan_target_binding(args, release, receipt):
    _, target = read_staged_release_plan(args, release)
    target_sha256 = sha256_bytes(
        canonical_json(target).encode("utf-8")
    )
    comparable = dict(target)
    for field in ("releaseRootExists", "releaseRootEntryManifest"):
        comparable.pop(field)
    comparable_sha256 = sha256_bytes(
        canonical_json(comparable).encode("utf-8")
    )
    if (
        receipt.get("releasePlanTargetSha256") != target_sha256
        or receipt.get("releasePlanTargetComparableSha256")
            != comparable_sha256
        or receipt.get("finalizeStageLiveTargetComparableSha256")
            != comparable_sha256
        or receipt.get("stageOwnedReleaseRootDeltaVerified") is not True
    ):
        raise RuntimeError("staged release Plan target binding drifted")
    return {
        "releasePlanTargetSha256": target_sha256,
        "releasePlanTargetComparableSha256": comparable_sha256,
        "finalizeStageLiveTargetComparableSha256":
            comparable_sha256,
        "stageOwnedReleaseRootDeltaVerified": True,
    }


def validate_pre_apply_nginx_target(args, release):
    _, target = read_staged_release_plan(args, release)
    manifest, dump_sha256 = active_nginx_manifest()
    if (
        manifest != target.get("activeNginxManifest")
        or manifest != target.get("nginxConfigs")
        or dump_sha256 != target.get("nginxDumpSha256")
    ):
        raise RuntimeError("active Nginx drifted after Stage")
    return {
        "activeNginxManifest": manifest,
        "nginxDumpSha256": dump_sha256,
    }


def staged_worker_result(args, receipt_path, changed):
    receipt = read_json(receipt_path)
    validate_staged_plan_target_binding(
        args, receipt_path.parent, receipt
    )
    validate_staged_release_artifacts(
        args, receipt_path.parent, receipt
    )
    return {
        "schema": "fbsir.u3wDefaultOffReleaseWorkerResult.v1",
        "mode": "FinalizeStage",
        "state": "STAGED_FOR_SWITCH",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "transitionApprovalReceiptSha256":
            receipt.get("stageApprovalReceiptSha256"),
        "receiptPath": str(receipt_path),
        "receiptSha256": sha256_file(receipt_path),
        "evidenceReceipts": validate_release_evidence(
            args,
            release_directory(args),
            receipt,
            require_execution=False,
        ),
        "productionFilesystemChanged": changed,
        "productionDatabaseChanged": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
    }


def finalize_stage(args):
    validate_preparation_anchors(args)
    incoming = incoming_directory(args)
    final = release_directory(args)
    if final.exists():
        if (
            (final / "deployment-receipt.json").exists()
            or (final / "rollback-receipt.json").exists()
        ):
            raise RuntimeError("release lifecycle already progressed past Stage")
        receipt = final / "deployment-readiness-receipt.json"
        validate_regular_file(receipt, final, modes=(0o600,))
        staged = read_json(receipt)
        digest = sha256_file(receipt)
        expected_stage = args.stage_receipt_sha
        if (
            staged.get("schema")
            != "fbsir.u3wW1aDeploymentReadinessReceipt.v2"
            or staged.get("state") != "STAGED_FOR_SWITCH"
            or staged.get("releaseId") != args.release_id
            or staged.get("sourceCommit") != args.source_commit
            or staged.get("backendBuildSha256") != args.backend_sha
            or staged.get("frontendBuildSha256")
            != args.frontend_tree_sha
            or staged.get("runnerSha256") != args.runner_sha
            or staged.get("workerSha256") != args.worker_sha
            or not SHA_PATTERN.fullmatch(
                str(staged.get("stageApprovalReceiptSha256") or "")
            )
            or (
                expected_stage != "0" * 64
                and digest != expected_stage
            )
        ):
            raise RuntimeError("release was staged with a different identity")
        result = staged_worker_result(args, receipt, False)
        latest_changed = advance_latest_receipt(receipt)
        if latest_changed:
            result["productionFilesystemChanged"] = True
        return result
    safe_directory(incoming, 0o700)
    backend = validate_regular_file(
        incoming / "backend/fbsir-admin.jar",
        incoming / "backend",
        modes=(0o600, 0o644),
    )
    migration = validate_regular_file(
        incoming / "sql/public_init_043.sql",
        incoming / "sql",
        modes=(0o600, 0o644),
    )
    runner = validate_regular_file(
        incoming / "release-runner.ps1", incoming, modes=(0o600, 0o644)
    )
    worker = validate_regular_file(
        incoming / "release-worker.py", incoming, modes=(0o600, 0o644)
    )
    build_receipt = validate_regular_file(
        incoming / "evidence/build-receipt.json",
        incoming / "evidence",
        modes=(0o600, 0o644),
    )
    plan_receipt = validate_regular_file(
        incoming / "evidence/release-plan.json",
        incoming / "evidence",
        modes=(0o600, 0o644),
    )
    extract_frontend_archive(incoming)
    validate_release_marker(
        incoming / "frontend/w1a-release.json", args
    )
    frontend = tree_manifest(incoming / "frontend")
    actual = {
        "backend": sha256_file(backend),
        "migration": sha256_file(migration),
        "runner": sha256_file(runner),
        "worker": sha256_file(worker),
        "buildReceipt": sha256_file(build_receipt),
        "planReceipt": sha256_file(plan_receipt),
        "frontend": frontend["sha256"],
    }
    expected = {
        "backend": args.backend_sha,
        "migration": args.migration_sha,
        "runner": args.runner_sha,
        "worker": args.worker_sha,
        "buildReceipt": args.build_receipt_sha,
        "planReceipt": args.plan_receipt_sha,
        "frontend": args.frontend_tree_sha,
    }
    if actual != expected:
        raise RuntimeError("uploaded release artifacts drifted")
    build = read_json(build_receipt)
    plan = read_json(plan_receipt)
    if (
        build.get("schema") != "fbsir.u3wDefaultOffBuildReceipt.v1"
        or build.get("sourceCommit") != args.source_commit
        or build.get("releaseId") != args.release_id
        or build.get("runnerSha256") != args.runner_sha
        or build.get("backend", {}).get("sha256") != args.backend_sha
        or build.get("frontend", {}).get("treeSha256")
        != args.frontend_tree_sha
        or build.get("migrationSha256") != args.migration_sha
        or plan.get("schema") != "fbsir.u3wDefaultOffReleasePlan.v1"
        or plan.get("sourceCommit") != args.source_commit
        or plan.get("releaseId") != args.release_id
        or plan.get("buildReceiptSha256") != args.build_receipt_sha
        or plan.get("runnerSha256") != args.runner_sha
        or plan.get("workerSha256") != args.worker_sha
        or not SHA_PATTERN.fullmatch(
            str(plan.get("collectorSha256") or "")
        )
        or plan.get("productionChanged") is not False
        or not isinstance(plan.get("target"), dict)
    ):
        raise RuntimeError("build or plan receipt identity drifted")
    atomic_bytes(
        incoming / "evidence/frontend-manifest.txt",
        frontend["manifest"],
        0o600,
    )
    before_service = service_snapshot()
    prior_rollback_anchor = None
    if DROPIN_PATH.exists() or DROPIN_PATH.is_symlink():
        prior_rollback_anchor = validate_prior_rollback_stage_entry(
            before_service
        )
    if (
        CURRENT_LINK.exists()
        or CURRENT_LINK.is_symlink()
        or (
            (DROPIN_PATH.exists() or DROPIN_PATH.is_symlink())
            and prior_rollback_anchor is None
        )
        or before_service.get("processForbiddenOverrideNames") != []
        or before_service.get("processDatabaseBindingMatched") is not True
        or before_service.get(
            "processConfiguredEnvironmentPreStageCompatible"
        ) is not True
        or before_service.get(
            "processConfiguredEnvironmentMismatchNames"
        ) != []
        or before_service.get("environmentFilePaths")
            != [str(ENV_PATH)]
        or len(before_service.get("environmentFileManifest", [])) != 1
        or any(
            before_service.get("configuredFlagValues", {}).get(name)
                != "false"
            or before_service.get("processFlagValues", {}).get(name)
                not in (None, "false")
            for name in FALSE_FLAGS
        )
        or environment_flags_explicit_false() is not True
    ):
        raise RuntimeError(
            "Stage requires an untouched or exactly anchored rollback "
            "predecessor topology"
        )
    live_plan_target = stage_live_plan_target(
        before_service, prior_rollback_anchor
    )
    plan_target_binding = assert_stage_plan_target_matches(
        args, plan["target"], live_plan_target
    )
    if jar_attribution_class_count(before_service["jarPath"]) != 0:
        raise RuntimeError("legacy rollback JAR unexpectedly contains W1A classes")
    shutil.copy2(
        before_service["jarPath"], incoming / "rollback/previous-admin.jar"
    )
    os.chown(incoming / "rollback/previous-admin.jar", 0, 0)
    os.chmod(incoming / "rollback/previous-admin.jar", 0o600)
    validate_regular_file(NGINX_PATH, NGINX_PATH.parent, modes=(0o600, 0o644))
    shutil.copy2(NGINX_PATH, incoming / "rollback/placeholder-sites.conf")
    os.chown(incoming / "rollback/placeholder-sites.conf", 0, 0)
    os.chmod(incoming / "rollback/placeholder-sites.conf", 0o600)
    nginx_status = NGINX_PATH.stat()
    pre_database = database_pre_stage_facts()
    if (
        pre_database["w1aDatabaseState"] not in {
            "ABSENT",
            "EXACT_043_RETAINED_DORMANT",
        }
        or pre_database["legacyBaselineReceiptSha256"]
            != args.baseline_receipt_sha
        or pre_database["legacyBaselineMigrationCount"] != 1
        or before_service.get("configuredEnvironmentSha256")
            != pre_database.get("environmentSha256")
    ):
        raise RuntimeError("Stage database identity or W1A prestate drifted")
    rehearsal_root = incoming / "evidence/application-rehearsal"
    safe_directory(rehearsal_root, 0o700)
    rehearsal_link = rehearsal_root / "current"
    atomic_symlink(incoming, rehearsal_link)
    if rehearsal_link.resolve() != incoming.resolve():
        raise RuntimeError("candidate symlink rehearsal failed")
    atomic_symlink(incoming / "rollback", rehearsal_link)
    if rehearsal_link.resolve() != (incoming / "rollback").resolve():
        raise RuntimeError("rollback symlink rehearsal failed")
    rehearsal_link.unlink()
    application_rollback = {
        "schema": "fbsir.u3wApplicationRollbackAssemblyReceipt.v1",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "stageApprovalReceiptSha256": args.approval_sha,
        "assemblyVerified": True,
        "applicationRollbackProven": False,
        "strategy": (
            "IMMUTABLE_PREDECESSOR_DROPIN_RESTORE_NGINX_AND_RESTART"
        ),
        "previousJarPath": before_service["jarPath"],
        "previousJarSha256": before_service["jarSha256"],
        "immutablePreviousJarPath": str(
            final / "rollback/previous-admin.jar"
        ),
        "serviceSnapshotBeforeStage": before_service,
        "preStageApplicationState": (
            "EXACT_PRIOR_ROLLBACK_PREDECESSOR"
            if prior_rollback_anchor is not None
            else "UNTOUCHED_LEGACY"
        ),
        "priorRollbackAnchor": prior_rollback_anchor,
        **plan_target_binding,
        "previousNginxPath": str(NGINX_PATH),
        "previousNginxSha256": sha256_file(NGINX_PATH),
        "previousNginxMode": nginx_status.st_mode & 0o777,
        "previousNginxUid": nginx_status.st_uid,
        "previousNginxGid": nginx_status.st_gid,
        "symlinkForwardAndReverseVerified": True,
        "productionServiceChanged": False,
        "productionDatabaseChanged": False,
        "observedAt": utc_now(),
    }
    application_path = (
        incoming / "evidence/application-rollback-assembly.json"
    )
    atomic_json(application_path, application_rollback)
    database_safety = {
        "schema": "fbsir.u3wDatabaseRollbackSafetyReceipt.v1",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "stageApprovalReceiptSha256": args.approval_sha,
        "verified": True,
        "strategy": "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
        "migrationSha256": args.migration_sha,
        "preDeploymentDatabaseState":
            pre_database["w1aDatabaseState"],
        "preDeploymentPublic043Absent": (
            pre_database["w1aDatabaseState"] == "ABSENT"
        ),
        "preDeploymentAttributionTablesAbsent": (
            pre_database["w1aDatabaseState"] == "ABSENT"
        ),
        "preDeploymentRetainedMigrationFacts": (
            {
                "publicReceiptCount":
                    pre_database["public043ReceiptCount"],
                "internalReceiptCount":
                    pre_database["attributionInternalReceiptCount"],
                "tableCount": pre_database["attributionTableCount"],
                "triggerCount": pre_database["attributionTriggerCount"],
                "permissionCount":
                    pre_database["attributionPermissionCount"],
                "eventCount": pre_database["attributionEventCount"],
                "journeyCount": pre_database["attributionJourneyCount"],
                "schemaFingerprintSha256":
                    pre_database["w1aSchemaFingerprintSha256"],
            }
            if pre_database["w1aDatabaseState"]
                == "EXACT_043_RETAINED_DORMANT"
            else None
        ),
        "legacyJarAttributionClassCount": 0,
        "allW1aFlagsExplicitFalse": True,
        "databaseDownClaimed": False,
        "productionDatabaseChanged": False,
        "observedAt": utc_now(),
    }
    database_path = (
        incoming / "evidence/database-rollback-safety.json"
    )
    atomic_json(database_path, database_safety)
    candidate_nginx = portal_nginx_config(final).encode("utf-8")
    atomic_bytes(
        incoming / "evidence/u3w-portal-sites.conf",
        candidate_nginx,
        0o600,
    )
    atomic_bytes(
        incoming / "evidence/systemd-dropin.conf",
        systemd_dropin().encode("utf-8"),
        0o600,
    )
    atomic_bytes(
        incoming / "evidence/rollback-systemd-dropin.conf",
        rollback_systemd_dropin(final).encode("utf-8"),
        0o600,
    )
    final_backend = final / "backend/fbsir-admin.jar"
    final_manifest = final / "evidence/frontend-manifest.txt"
    final_runner = final / "release-runner.ps1"
    final_application = (
        final / "evidence/application-rollback-assembly.json"
    )
    final_database = final / "evidence/database-rollback-safety.json"
    frontend_tree_receipt = frontend_tree_evidence(incoming)
    artifact_manifest = release_artifact_manifest(incoming)
    receipt = {
        "schema": "fbsir.u3wW1aDeploymentReadinessReceipt.v2",
        "state": "STAGED_FOR_SWITCH",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "stageApprovalReceiptSha256": args.approval_sha,
        "targetHost": TARGET_HOST,
        "serviceUnit": SERVICE_UNIT,
        "database": DATABASE,
        "buildReceiptSha256": args.build_receipt_sha,
        "releasePlanReceiptSha256": args.plan_receipt_sha,
        **plan_target_binding,
        "backupReceiptSha256": args.backup_receipt_sha,
        "legacyBaselineReceiptSha256": args.baseline_receipt_sha,
        "configurationReceiptSha256": args.configuration_receipt_sha,
        "preStageRuntimeIdentity": pre_database,
        "backendBuildPath": str(final_backend),
        "backendBuildSha256": args.backend_sha,
        "frontendBuildPath": str(final_manifest),
        "frontendBuildSha256": args.frontend_tree_sha,
        "migrationSha256": args.migration_sha,
        "runnerPath": str(final_runner),
        "runnerSha256": args.runner_sha,
        "workerSha256": args.worker_sha,
        "releaseArtifactManifest": artifact_manifest,
        "frontendTreeEvidence": frontend_tree_receipt,
        "applicationRollbackAssemblyReceiptPath": str(final_application),
        "applicationRollbackAssemblyReceiptSha256": sha256_file(
            application_path
        ),
        "applicationRollbackAssemblyVerified": True,
        "applicationRollbackProven": False,
        "databaseRollbackSafetyReceiptPath": str(final_database),
        "databaseRollbackSafetyReceiptSha256": sha256_file(database_path),
        "databaseRollbackSafetyProven": True,
        "databaseDownClaimed": False,
        "strictHeadBuildUploadSwitchReceiptScriptPresent": True,
        "actualActiveArtifactsMatched": False,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
        "generatedAt": utc_now(),
    }
    receipt_path = incoming / "deployment-readiness-receipt.json"
    atomic_json(receipt_path, receipt)
    make_frontend_public(incoming)
    os.replace(incoming, final)
    fsync_directory(RELEASE_ROOT)
    allowed_latest_predecessors = (
        [prior_rollback_anchor["receiptPath"]]
        if prior_rollback_anchor is not None
        else []
    )
    advance_latest_receipt(
        final / "deployment-readiness-receipt.json",
        allowed_latest_predecessors,
    )
    return staged_worker_result(
        args, final / "deployment-readiness-receipt.json", True
    )


def migration_fingerprint_rows(mysql):
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


def migration_fingerprint(mysql):
    rows = migration_fingerprint_rows(mysql)
    return sha256_bytes(("\n".join(rows) + "\n").encode("utf-8"))


def exact_migration_facts(mysql):
    return {
        "publicReceiptCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s AND description=%s",
                (MIGRATION_PUBLIC_VERSION, MIGRATION_PUBLIC_DESCRIPTION),
            )
        ),
        "internalReceiptCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s AND description=%s",
                (MIGRATION_INTERNAL_VERSION, MIGRATION_INTERNAL_DESCRIPTION),
            )
        ),
        "tableCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM information_schema.tables "
                "WHERE table_schema=DATABASE() AND table_name IN "
                "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
            )
        ),
        "triggerCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM information_schema.triggers "
                "WHERE trigger_schema=DATABASE() AND event_object_table IN "
                "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
            )
        ),
        "permissionCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM sys_menu WHERE "
                "BINARY perms=BINARY 'board:attribution:query'"
            )
        ),
        "eventCount": int(
            mysql.scalar("SELECT COUNT(*) FROM fbs_board_attr_event_v1")
        ),
        "journeyCount": int(
            mysql.scalar("SELECT COUNT(*) FROM fbs_board_attr_journey_v1")
        ),
        "schemaFingerprintSha256": migration_fingerprint(mysql),
    }


def validate_apply_session_identity(mysql, connection, expected):
    if not isinstance(expected, dict):
        raise RuntimeError("Apply session identity anchor is absent")
    identity = mysql.rows("SELECT LOWER(@@server_uuid),DATABASE(),@@version")
    if len(identity) != 1 or len(identity[0]) != 3:
        raise RuntimeError("Apply database identity query shape is invalid")
    actual = {
        "environmentSha256": sha256_file(ENV_PATH),
        "api2EventKeySha256": sha256_file(API2_EVENT_KEY_PATH),
        "additionalConfigSha256": validate_additional_config(),
        "databaseEndpoint": "{}:{}".format(
            connection["host"], connection["port"]
        ),
        "database": identity[0][1],
        "databaseServerUuid": identity[0][0],
        "databaseServerVersion": identity[0][2],
        "legacyBaselineReceiptSha256": mysql.scalar(
            "SELECT adoption_receipt_sha256 FROM "
            "u3w_legacy_schema_baseline_receipt_v2"
        ),
        "legacyBaselineMigrationCount": int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version='legacy_w1a_baseline_20260724_001' "
                "AND description=CONCAT("
                "'APPLIED:LEGACY_ADOPTED_W1A_V2:',%s)",
                (expected.get("legacyBaselineReceiptSha256"),),
            )
        ),
    }
    if any(actual.get(key) != expected.get(key) for key in actual):
        raise RuntimeError("Apply session identity drifted")
    return actual


def apply_migration(args, release, expected_runtime_identity):
    _, connection = parse_environment()
    mysql = Mysql(connection)
    lock_acquired = False
    database_changed_this_run = False
    migration = release / "sql/public_init_043.sql"
    validate_regular_file(migration, migration.parent, modes=(0o600, 0o644))
    if sha256_file(migration) != args.migration_sha:
        raise RuntimeError("migration digest drifted")
    prestate = expected_runtime_identity.get("w1aDatabaseState")
    if prestate not in {"ABSENT", "EXACT_043_RETAINED_DORMANT"}:
        raise RuntimeError("staged W1A migration prestate is invalid")
    expected_event_count = (
        int(expected_runtime_identity.get("attributionEventCount", -1))
        if prestate == "EXACT_043_RETAINED_DORMANT"
        else 0
    )
    expected_journey_count = (
        int(expected_runtime_identity.get("attributionJourneyCount", -1))
        if prestate == "EXACT_043_RETAINED_DORMANT"
        else 0
    )
    if expected_event_count < 0 or expected_journey_count < 0:
        raise RuntimeError("staged W1A ledger counts are invalid")
    try:
        if int(mysql.scalar(
            "SELECT GET_LOCK('u3w:w1a:public_init_043',0)"
        )) != 1:
            raise RuntimeError("W1A migration named lock is unavailable")
        lock_acquired = True
        validate_apply_session_identity(
            mysql, connection, expected_runtime_identity
        )
        existing = mysql.rows(
            "SELECT description FROM u3w_schema_migration WHERE version=%s",
            (MIGRATION_PUBLIC_VERSION,),
        )
        if len(existing) == 1 and existing[0][0] == MIGRATION_PUBLIC_DESCRIPTION:
            facts = exact_migration_facts(mysql)
        else:
            if prestate != "ABSENT":
                raise RuntimeError(
                    "retained 043 prestate lost its exact migration receipt"
                )
            if existing and existing[0][0] != (
                "RUNNING:Independent Board exact official experts attribution v1"
            ):
                raise RuntimeError("public 043 receipt is in an unsafe state")
            if not existing:
                database_changed_this_run = True
                mysql.execute(
                    "INSERT INTO u3w_schema_migration(version,description) "
                    "VALUES(%s,%s)",
                    (
                        MIGRATION_PUBLIC_VERSION,
                        "RUNNING:Independent Board exact official experts "
                        "attribution v1",
                    ),
                )
            database_changed_this_run = True
            mysql.execute_script(
                parse_mysql_script(
                    migration.read_text(encoding="utf-8")
                )
            )
            validate_apply_session_identity(
                mysql, connection, expected_runtime_identity
            )
            facts = exact_migration_facts(mysql)
            if (
                not migration_structure_matches(
                    facts, public_receipt_count=0
                )
                or facts.get("eventCount") != 0
                or facts.get("journeyCount") != 0
            ):
                raise RuntimeError(
                    "043 exact current-read failed: {}".format(
                        json.dumps(
                            {
                                "facts": facts,
                                "expectedSchemaFingerprintSha256":
                                    EXPECTED_W1A_SCHEMA_FINGERPRINT,
                            },
                            sort_keys=True,
                        )
                    )
                )
            mysql.execute(
                "UPDATE u3w_schema_migration SET description=%s,"
                "applied_at=CURRENT_TIMESTAMP WHERE version=%s AND "
                "description=%s",
                (
                    MIGRATION_PUBLIC_DESCRIPTION,
                    MIGRATION_PUBLIC_VERSION,
                    "RUNNING:Independent Board exact official experts "
                    "attribution v1",
                ),
            )
            facts = exact_migration_facts(mysql)
        if (
            not migration_structure_matches(facts)
            or facts.get("eventCount") != expected_event_count
            or facts.get("journeyCount") != expected_journey_count
        ):
            raise RuntimeError("043 final current-read failed")
        validate_apply_session_identity(
            mysql, connection, expected_runtime_identity
        )
        return MigrationLease(
            mysql,
            connection,
            facts,
            database_changed_this_run=database_changed_this_run,
            database_changed_since_stage=(prestate == "ABSENT"),
        )
    except Exception:
        if lock_acquired:
            try:
                mysql.scalar(
                    "SELECT RELEASE_LOCK('u3w:w1a:public_init_043')"
                )
            except Exception:
                pass
        mysql.close()
        raise


def deployed_migration_lease(args, release, deployment):
    expected_runtime_identity = deployment.get("preStageRuntimeIdentity")
    baseline = deployment.get("migrationFacts")
    if (
        not isinstance(expected_runtime_identity, dict)
        or not migration_structure_matches(baseline)
    ):
        raise RuntimeError("deployment migration baseline is invalid")
    migration = release / "sql/public_init_043.sql"
    validate_regular_file(migration, migration.parent, modes=(0o600, 0o644))
    if sha256_file(migration) != args.migration_sha:
        raise RuntimeError("migration digest drifted")
    _, connection = parse_environment()
    mysql = Mysql(connection)
    lock_acquired = False
    try:
        if int(mysql.scalar(
            "SELECT GET_LOCK('u3w:w1a:public_init_043',0)"
        )) != 1:
            raise RuntimeError("W1A migration named lock is unavailable")
        lock_acquired = True
        validate_apply_session_identity(
            mysql, connection, expected_runtime_identity
        )
        facts = exact_migration_facts(mysql)
        if not migration_counts_are_monotonic(facts, baseline):
            raise RuntimeError(
                "deployed W1A ledger structure or monotonic counts drifted"
            )
        return MigrationLease(mysql, connection, facts)
    except Exception:
        if lock_acquired:
            try:
                mysql.scalar(
                    "SELECT RELEASE_LOCK('u3w:w1a:public_init_043')"
                )
            except Exception:
                pass
        mysql.close()
        raise


def validate_staged_release_artifacts(args, release, receipt):
    expected_manifest = receipt.get("releaseArtifactManifest")
    if (
        not isinstance(expected_manifest, dict)
        or set(expected_manifest) != set(RELEASE_ARTIFACT_RELATIVE_PATHS)
    ):
        raise RuntimeError("staged release artifact manifest is invalid")
    actual_manifest = release_artifact_manifest(release)
    if actual_manifest != expected_manifest:
        raise RuntimeError("staged release artifact manifest drifted")
    frontend = frontend_tree_evidence(release)
    if (
        frontend != receipt.get("frontendTreeEvidence")
        or frontend.get("sha256") != args.frontend_tree_sha
    ):
        raise RuntimeError("staged frontend tree evidence drifted")
    predecessor = predecessor_facts(release)
    expected_digests = {
        "backend/fbsir-admin.jar": args.backend_sha,
        "sql/public_init_043.sql": args.migration_sha,
        "release-runner.ps1": args.runner_sha,
        "release-worker.py": args.worker_sha,
        "evidence/build-receipt.json": args.build_receipt_sha,
        "evidence/release-plan.json": args.plan_receipt_sha,
        "evidence/frontend-manifest.txt": sha256_bytes(
            tree_manifest(release / "frontend")["manifest"]
        ),
        "evidence/systemd-dropin.conf": sha256_bytes(
            systemd_dropin().encode("utf-8")
        ),
        "evidence/rollback-systemd-dropin.conf": sha256_bytes(
            rollback_systemd_dropin(release).encode("utf-8")
        ),
        "evidence/u3w-portal-sites.conf": sha256_bytes(
            portal_nginx_config(release).encode("utf-8")
        ),
        "evidence/application-rollback-assembly.json":
            receipt.get(
                "applicationRollbackAssemblyReceiptSha256"
            ),
        "evidence/database-rollback-safety.json":
            receipt.get("databaseRollbackSafetyReceiptSha256"),
        "rollback/previous-admin.jar":
            predecessor.get("previousJarSha256"),
        "rollback/placeholder-sites.conf":
            predecessor.get("previousNginxSha256"),
    }
    if any(
        expected_manifest[relative].get("sha256") != expected
        or not SHA_PATTERN.fullmatch(str(expected or ""))
        for relative, expected in expected_digests.items()
    ):
        raise RuntimeError("staged release artifact digest binding drifted")
    validate_release_marker(release / "frontend/w1a-release.json", args)
    return {
        "releaseArtifactManifest": actual_manifest,
        "frontendTreeEvidence": frontend,
    }


def validate_stage_receipt(args, release):
    path = release / "deployment-readiness-receipt.json"
    validate_regular_file(path, release, modes=(0o600,))
    digest = sha256_file(path)
    if digest != args.stage_receipt_sha:
        raise RuntimeError("staged receipt anchor drifted")
    receipt = read_json(path)
    if (
        receipt.get("schema") != "fbsir.u3wW1aDeploymentReadinessReceipt.v2"
        or receipt.get("state") != "STAGED_FOR_SWITCH"
        or receipt.get("releaseId") != args.release_id
        or receipt.get("sourceCommit") != args.source_commit
        or receipt.get("backendBuildSha256") != args.backend_sha
        or receipt.get("frontendBuildSha256") != args.frontend_tree_sha
        or receipt.get("runnerSha256") != args.runner_sha
        or receipt.get("workerSha256") != args.worker_sha
        or not SHA_PATTERN.fullmatch(
            str(receipt.get("stageApprovalReceiptSha256") or "")
        )
        or receipt.get("databaseDownClaimed") is not False
        or receipt.get("databaseRollbackSafetyProven") is not True
    ):
        raise RuntimeError("staged receipt identity is invalid")
    validate_staged_plan_target_binding(args, release, receipt)
    validate_staged_release_artifacts(args, release, receipt)
    validate_release_evidence(
        args, release, receipt, require_execution=False
    )
    return path, receipt


def restore_application(release, remove_current=True):
    rollback_config = release / "rollback/placeholder-sites.conf"
    rollback_dropin = release / "evidence/rollback-systemd-dropin.conf"
    validate_regular_file(
        rollback_config, rollback_config.parent, modes=(0o600,)
    )
    validate_regular_file(
        rollback_dropin, rollback_dropin.parent, modes=(0o600,)
    )
    predecessor = predecessor_facts(release)
    if (
        predecessor.get("previousNginxUid") != 0
        or predecessor.get("previousNginxGid") != 0
        or predecessor.get("previousNginxMode") not in (0o600, 0o640, 0o644)
    ):
        raise RuntimeError("predecessor Nginx custody evidence is invalid")
    atomic_bytes(
        NGINX_PATH,
        rollback_config.read_bytes(),
        predecessor["previousNginxMode"],
    )
    ensure_parent_directory(DROPIN_DIRECTORY)
    atomic_bytes(DROPIN_PATH, rollback_dropin.read_bytes(), 0o644)
    run_checked(["systemctl", "daemon-reload"], timeout=60)
    run_checked(["systemctl", "restart", SERVICE_UNIT], timeout=180)
    wait_for_u3w_health()
    run_checked(["nginx", "-t"], timeout=60)
    run_checked(["systemctl", "reload", "nginx.service"], timeout=60)
    if remove_current and (CURRENT_LINK.exists() or CURRENT_LINK.is_symlink()):
        CURRENT_LINK.unlink()
        fsync_directory(CURRENT_LINK.parent)


def predecessor_facts(release):
    path = release / "evidence/application-rollback-assembly.json"
    validate_regular_file(path, path.parent, modes=(0o600,))
    facts = read_json(path)
    predecessor = release / "rollback/previous-admin.jar"
    rollback_nginx = release / "rollback/placeholder-sites.conf"
    rollback_dropin = release / "evidence/rollback-systemd-dropin.conf"
    if (
        facts.get("schema")
        != "fbsir.u3wApplicationRollbackAssemblyReceipt.v1"
        or facts.get("releaseId") != release.name
        or not COMMIT_PATTERN.fullmatch(
            str(facts.get("sourceCommit") or "")
        )
        or facts.get("assemblyVerified") is not True
        or facts.get("applicationRollbackProven") is not False
        or facts.get("strategy")
        != "IMMUTABLE_PREDECESSOR_DROPIN_RESTORE_NGINX_AND_RESTART"
        or facts.get("immutablePreviousJarPath") != str(predecessor)
        or not validate_regular_file(
            predecessor, predecessor.parent, modes=(0o600,)
        )
        or sha256_file(predecessor) != facts.get("previousJarSha256")
        or not validate_regular_file(
            rollback_dropin, rollback_dropin.parent, modes=(0o600,)
        )
        or sha256_file(rollback_dropin) != sha256_bytes(
            rollback_systemd_dropin(release).encode("utf-8")
        )
        or not validate_regular_file(
            rollback_nginx, rollback_nginx.parent, modes=(0o600,)
        )
        or sha256_file(rollback_nginx)
            != facts.get("previousNginxSha256")
    ):
        raise RuntimeError("immutable predecessor evidence is invalid")
    return facts


def rollback_process_arguments(release):
    return [
        "/usr/bin/java",
        "-Dspring.config.additional-location=file:"
        + str(ADDITIONAL_CONFIG_PATH),
        "-jar",
        str(release / "rollback/previous-admin.jar"),
    ]


def release_dropin_manifest(release, file_name):
    previous = predecessor_facts(release)["serviceSnapshotBeforeStage"]
    path = release / "evidence" / file_name
    validate_regular_file(path, path.parent, modes=(0o600,))
    release_dropin = {
        "path": str(DROPIN_PATH),
        "sha256": sha256_file(path),
        "mode": 0o644,
    }
    return sorted(
        [
            item
            for item in previous.get("dropInManifest", [])
            if item.get("path") != str(DROPIN_PATH)
        ] + [release_dropin],
        key=lambda item: item["path"],
    )


def exact_loaded_environment_matches(current, previous):
    static_fields = (
        "environmentFilePaths",
        "environmentFileManifest",
        "api2EventKeyManifest",
        "expectedSecurityConfigurationNames",
        "expectedSecurityConfigurationHmacSha256",
        "configuredEnvironmentSha256",
        "configuredEnvironmentNames",
        "configuredEnvironmentHmacSha256",
        "configuredFlagValues",
    )
    return bool(
        all(
            current.get(field) == previous.get(field)
            for field in static_fields
        )
        and current.get("processDatabaseBindingMatched") is True
        and current.get("processConfiguredEnvironmentMatched") is True
        and current.get(
            "processConfiguredEnvironmentPreStageCompatible"
        ) is True
        and current.get("processConfiguredEnvironmentLoadState")
            == "EXACT_CONFIGURED"
        and current.get("processPendingRestartEnvironmentNames") == []
        and current.get(
            "processConfiguredEnvironmentMismatchNames"
        ) == []
        and current.get("processConfiguredEnvironmentHmacSha256")
            == current.get("configuredEnvironmentHmacSha256")
        and current.get("processSecurityConfigurationNames")
            == current.get("expectedSecurityConfigurationNames")
        and current.get("processSecurityConfigurationHmacSha256")
            == current.get("expectedSecurityConfigurationHmacSha256")
        and current.get("processForbiddenOverrideNames") == []
        and all(
            current.get("processFlagValues", {}).get(name) == "false"
            and current.get("configuredFlagValues", {}).get(name)
                == "false"
            for name in FALSE_FLAGS
        )
    )


def assert_legacy_runtime_contract(release, current):
    previous = predecessor_facts(release)["serviceSnapshotBeforeStage"]
    exact_fields = (
        "user",
        "group",
        "fragmentPath",
        "fragmentFileManifest",
        "environmentFilePaths",
        "environmentFileManifest",
        "workingDirectory",
        "configuredJarPath",
        "configuredJarSha256",
        "processJarPath",
        "processJarSha256",
        "processArgvSha256",
        "processEnvironmentNamesSha256",
        "configuredFlagValues",
        "api2EventKeyManifest",
        "processSecurityConfigurationNames",
        "processSecurityConfigurationHmacSha256",
        "expectedSecurityConfigurationNames",
        "expectedSecurityConfigurationHmacSha256",
        "processDatabaseBindingMatched",
        "configuredEnvironmentSha256",
        "configuredEnvironmentNames",
        "configuredEnvironmentHmacSha256",
        "processConfiguredEnvironmentHmacSha256",
        "processConfiguredEnvironmentMatched",
        "processConfiguredEnvironmentMismatchNames",
        "processPendingRestartEnvironmentNames",
        "processConfiguredEnvironmentLoadState",
        "processConfiguredEnvironmentPreStageCompatible",
        "processFlagValues",
        "processForbiddenOverrideNames",
        "dropInManifest",
        "externalConfigManifest",
        "additionalConfigSha256",
        "jarPath",
        "jarSha256",
    )
    if any(current.get(field) != previous.get(field) for field in exact_fields):
        raise RuntimeError("untouched legacy runtime contract drifted")
    return current


def assert_restored_predecessor_runtime_contract(release, current):
    previous = predecessor_facts(release)["serviceSnapshotBeforeStage"]
    predecessor_path = release / "rollback/previous-admin.jar"
    expected_argv = rollback_process_arguments(release)
    expected_argv_sha256 = sha256_bytes(
        ("\0".join(expected_argv) + "\0").encode("utf-8")
    )
    if (
        current.get("user") != previous.get("user")
        or current.get("group") != previous.get("group")
        or current.get("fragmentPath") != previous.get("fragmentPath")
        or current.get("fragmentFileManifest")
            != previous.get("fragmentFileManifest")
        or current.get("environmentFilePaths")
            != previous.get("environmentFilePaths")
        or current.get("environmentFileManifest")
            != previous.get("environmentFileManifest")
        or current.get("workingDirectory") != str(ADMIN_ROOT)
        or current.get("configuredJarPath") != str(predecessor_path)
        or current.get("processJarPath") != str(predecessor_path)
        or current.get("processArgvSha256") != expected_argv_sha256
        or not exact_loaded_environment_matches(current, previous)
        or current.get("dropInManifest")
            != release_dropin_manifest(
                release, "rollback-systemd-dropin.conf"
            )
        or current.get("externalConfigManifest")
            != previous.get("externalConfigManifest")
        or current.get("additionalConfigSha256")
            != previous.get("additionalConfigSha256")
        or current.get("configuredJarSha256")
            != previous.get("jarSha256")
        or current.get("processJarSha256")
            != previous.get("jarSha256")
        or current.get("jarSha256") != previous.get("jarSha256")
    ):
        raise RuntimeError("restored predecessor runtime contract drifted")
    return current


def assert_predecessor_topology(release, current):
    predecessor = predecessor_facts(release)
    previous_snapshot = predecessor["serviceSnapshotBeforeStage"]
    if (
        current["jarSha256"] != predecessor["previousJarSha256"]
        or current["configuredJarSha256"]
            != predecessor["previousJarSha256"]
        or sha256_file(NGINX_PATH) != predecessor["previousNginxSha256"]
        or CURRENT_LINK.exists()
        or CURRENT_LINK.is_symlink()
    ):
        raise RuntimeError("predecessor topology drifted after Stage")
    baseline_release_dropins = [
        item
        for item in previous_snapshot.get("dropInManifest", [])
        if item.get("path") == str(DROPIN_PATH)
    ]
    if baseline_release_dropins:
        expected_dropin = baseline_release_dropins[0]
        if (
            len(baseline_release_dropins) != 1
            or DROPIN_PATH.is_symlink()
            or not DROPIN_PATH.is_file()
            or sha256_file(DROPIN_PATH)
                != expected_dropin.get("sha256")
            or DROPIN_PATH.stat().st_mode & 0o777
                != expected_dropin.get("mode")
        ):
            raise RuntimeError("staged predecessor drop-in drifted")
    elif DROPIN_PATH.exists() or DROPIN_PATH.is_symlink():
        raise RuntimeError("unexpected staged predecessor drop-in")
    if current.get("activeState") == "active":
        assert_legacy_runtime_contract(release, current)
    return predecessor


def assert_candidate_owned_topology(args, release):
    current = service_snapshot(require_active=False)
    candidate_dropin = release / "evidence/systemd-dropin.conf"
    candidate_nginx = release / "evidence/u3w-portal-sites.conf"
    if (
        not CURRENT_LINK.is_symlink()
        or CURRENT_LINK.resolve() != release.resolve()
        or current["jarSha256"] != args.backend_sha
        or current["configuredJarSha256"] != args.backend_sha
        or not DROPIN_PATH.is_file()
        or DROPIN_PATH.is_symlink()
        or sha256_file(DROPIN_PATH) != sha256_file(candidate_dropin)
        or not NGINX_PATH.is_file()
        or NGINX_PATH.is_symlink()
        or sha256_file(NGINX_PATH) != sha256_file(candidate_nginx)
    ):
        raise RuntimeError("candidate active topology is incomplete or drifted")
    return current


def assert_candidate_runtime_contract(args, release, current):
    predecessor = predecessor_facts(release)
    previous_snapshot = predecessor["serviceSnapshotBeforeStage"]
    expected_argv = candidate_process_arguments()
    expected_argv_sha256 = sha256_bytes(
        ("\0".join(expected_argv) + "\0").encode("utf-8")
    )
    expected_dropins = release_dropin_manifest(
        release, "systemd-dropin.conf"
    )
    if (
        current.get("processArgvSha256") != expected_argv_sha256
        or current.get("processJarPath")
        != str(CURRENT_LINK / "backend/fbsir-admin.jar")
        or current.get("environmentFilePaths")
        != previous_snapshot.get("environmentFilePaths")
        or current.get("environmentFileManifest")
        != previous_snapshot.get("environmentFileManifest")
        or current.get("fragmentFileManifest")
            != previous_snapshot.get("fragmentFileManifest")
        or not exact_loaded_environment_matches(
            current, previous_snapshot
        )
        or current.get("dropInManifest") != expected_dropins
        or current.get("externalConfigManifest")
            != previous_snapshot.get("externalConfigManifest")
        or current.get("additionalConfigSha256")
            != previous_snapshot.get("additionalConfigSha256")
    ):
        raise RuntimeError(
            "candidate actual process or effective default-off contract drifted"
        )
    return current


def assert_candidate_active(args, release):
    current = assert_candidate_owned_topology(args, release)
    if current["activeState"] != "active":
        raise RuntimeError("candidate service is not active")
    assert_candidate_runtime_contract(args, release, current)
    wait_for_u3w_health()
    for host in ("me.u3w.com", "admin.u3w.com"):
        status, body = http_json(
            "https://{}/prod-api/captchaImage".format(host), timeout=10
        )
        if (
            status != 200
            or not isinstance(body, dict)
            or body.get("code") != 200
        ):
            raise RuntimeError(host + " API portal readiness failed")
        marker_status, marker = http_json(
            "https://{}/w1a-release.json".format(host), timeout=10
        )
        if (
            marker_status != 200
            or not isinstance(marker, dict)
            or marker.get("schema") != "fbsir.u3wReleaseMarker.v1"
            or marker.get("releaseId") != args.release_id
            or marker.get("sourceCommit") != args.source_commit
            or marker.get("officialExpertsPackageChanged") is not False
        ):
            raise RuntimeError(host + " frontend release marker drifted")
    environment, _ = parse_environment()
    if any(environment.get(name, "").strip() != "false" for name in FALSE_FLAGS):
        raise RuntimeError("candidate flags are not explicitly false")
    return current


def deployment_receipt(
    args,
    stage_path,
    stage,
    migration_facts,
    before,
    after,
    rollback_execution_path,
    database_changed_this_run,
    database_changed_since_stage,
):
    active_nginx, nginx_dump_sha256 = active_nginx_manifest()
    receipt = dict(stage)
    receipt.update(
        {
            "state": "DEPLOYED_DEFAULT_OFF",
            "stageReceiptSha256": sha256_file(stage_path),
            "applyApprovalReceiptSha256": args.approval_sha,
            "migrationFacts": migration_facts,
            "serviceBefore": before,
            "serviceAfter": after,
            "currentLinkResolved": str(CURRENT_LINK.resolve()),
            "nginxPath": str(NGINX_PATH),
            "nginxSha256": sha256_file(NGINX_PATH),
            "activeNginxManifestAfterApply": active_nginx,
            "nginxDumpSha256AfterApply": nginx_dump_sha256,
            "applicationRollbackReceiptPath": str(
                rollback_execution_path
            ),
            "applicationRollbackReceiptSha256": sha256_file(
                rollback_execution_path
            ),
            "applicationRollbackProven": True,
            "databaseRollbackSafetyProven": True,
            "databaseDownClaimed": False,
            "actualActiveArtifactsMatched": True,
            "productionFilesystemChanged": True,
            "productionDatabaseChanged":
                database_changed_since_stage,
            "productionDatabaseChangedThisRun":
                database_changed_this_run,
            "productionDatabaseChangedSinceStage":
                database_changed_since_stage,
            "productionServiceChanged": True,
            "configurationLoaded": True,
            "generatedAt": utc_now(),
        }
    )
    return receipt


def install_candidate_application(args, release):
    safe_directory(DROPIN_DIRECTORY, 0o755)
    atomic_symlink(release, CURRENT_LINK)
    atomic_bytes(
        DROPIN_PATH,
        (release / "evidence/systemd-dropin.conf").read_bytes(),
        0o644,
    )
    atomic_bytes(
        NGINX_PATH,
        (release / "evidence/u3w-portal-sites.conf").read_bytes(),
        0o644,
    )
    run_checked(["nginx", "-t"], timeout=60)
    run_checked(["systemctl", "daemon-reload"], timeout=60)
    run_checked(["systemctl", "restart", SERVICE_UNIT], timeout=180)
    wait_for_u3w_health()
    run_checked(["systemctl", "reload", "nginx.service"], timeout=60)
    return assert_candidate_active(args, release)


def application_rollback_execution_receipt(
    args,
    release,
    migration_facts,
    candidate_before,
    predecessor_after,
    candidate_after,
):
    if (
        candidate_before["invocationId"] == predecessor_after["invocationId"]
        or predecessor_after["invocationId"] == candidate_after["invocationId"]
        or candidate_before["jarSha256"] != args.backend_sha
        or candidate_after["jarSha256"] != args.backend_sha
        or predecessor_after["jarSha256"]
        != predecessor_facts(release)["previousJarSha256"]
    ):
        raise RuntimeError("application rollback execution proof is invalid")
    receipt = {
        "schema": "fbsir.u3wApplicationRollbackExecutionReceipt.v1",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "applyApprovalReceiptSha256": args.approval_sha,
        "strategy": (
            "LIVE_CANDIDATE_TO_IMMUTABLE_PREDECESSOR_TO_CANDIDATE"
        ),
        "verified": True,
        "candidateBeforeRollback": candidate_before,
        "predecessorAfterRollback": predecessor_after,
        "candidateAfterReapply": candidate_after,
        "retainedMigrationFacts": migration_facts,
        "database043Retained": True,
        "productionServiceChanged": True,
        "productionDatabaseChanged": False,
        "officialExpertsPackageChanged": False,
        "observedAt": utc_now(),
    }
    path = release / "evidence/application-rollback-execution.json"
    atomic_json(path, receipt)
    return path


def apply_worker_result(
    args,
    deployment_path,
    filesystem_changed,
    database_changed_this_run,
    database_changed_since_stage,
    service_changed,
    deployment_committed,
    latest_repaired,
    recovered=False,
):
    receipt = read_json(deployment_path)
    return {
        "schema": "fbsir.u3wDefaultOffReleaseWorkerResult.v1",
        "mode": "Apply",
        "state": "DEPLOYED_DEFAULT_OFF",
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "transitionApprovalReceiptSha256":
            receipt.get("applyApprovalReceiptSha256"),
        "receiptPath": str(deployment_path),
        "receiptSha256": sha256_file(deployment_path),
        "evidenceReceipts": validate_release_evidence(
            args,
            release_directory(args),
            receipt,
            require_execution=True,
        ),
        "recoveredExistingSideEffects": recovered,
        "deploymentCommittedThisRun": deployment_committed,
        "deploymentAlreadyCommitted": not deployment_committed,
        "latestReceiptLinkRepaired": latest_repaired,
        "changeKind": (
            "DEPLOYMENT_COMMIT"
            if deployment_committed
            else (
                "RECEIPT_LINK_REPAIR"
                if latest_repaired
                else "IDEMPOTENT_REVALIDATION"
            )
        ),
        "productionFilesystemChanged": filesystem_changed,
        "productionDatabaseChanged": database_changed_this_run,
        "productionDatabaseChangedThisRun":
            database_changed_this_run,
        "productionDatabaseChangedSinceStage":
            database_changed_since_stage,
        "productionServiceChanged": service_changed,
        "officialExpertsPackageChanged": False,
    }


APPLY_FAILURE_STATES = frozenset(
    (
        "FAIL_CLOSED_BEFORE_APPLICATION_SWITCH",
        "PRE_APPLY_RECOVERY_FAILED_TOPOLOGY_UNCERTAIN",
        "APPLICATION_RESTORED_DATABASE_043_RETAINED_OR_FAIL_CLOSED",
        "APPLICATION_RESTORE_FAILED_TOPOLOGY_UNCERTAIN",
        "DEPLOYMENT_COMMIT_AMBIGUOUS_NO_AUTOMATIC_RESTORE",
        "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
    )
)


def write_apply_failure_receipt(
    args,
    error,
    migration_facts,
    state,
    *,
    application_started,
    topology_restored,
    deployment_commit_outcome,
    service_changed_this_run,
    database_changed_this_run,
):
    if (
        state not in APPLY_FAILURE_STATES
        or type(application_started) is not bool
        or type(topology_restored) is not bool
        or deployment_commit_outcome
            not in {"NOT_ATTEMPTED", "NOT_COMMITTED", "COMMITTED", "AMBIGUOUS"}
        or type(service_changed_this_run) not in {bool, type(None)}
        or type(database_changed_this_run) not in {bool, type(None)}
    ):
        raise RuntimeError("Apply failure receipt semantics are invalid")
    error_identity = "{}:{}".format(
        type(error).__name__, sha256_bytes(str(error).encode("utf-8"))
    )
    timestamp = dt.datetime.now(dt.timezone.utc).strftime(
        "%Y%m%dT%H%M%S%fZ"
    )
    path = release_directory(args) / (
        "apply-failure-{}-{}.json".format(
            timestamp, sha256_bytes(error_identity.encode("utf-8"))[:12]
        )
    )
    deployment_path = release_directory(args) / "deployment-receipt.json"
    deployment_receipt_sha256 = None
    if deployment_path.is_file() and not deployment_path.is_symlink():
        validate_regular_file(
            deployment_path, deployment_path.parent, modes=(0o600,)
        )
        deployment_receipt_sha256 = sha256_file(deployment_path)
    receipt = {
        "schema": "fbsir.u3wDefaultOffReleaseFailureReceipt.v1",
        "state": state,
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "applyApprovalReceiptSha256": args.approval_sha,
        "errorType": type(error).__name__,
        "errorMessageSha256": sha256_bytes(str(error).encode("utf-8")),
        "migrationFacts": migration_facts,
        "applicationStarted": application_started,
        "applicationAlreadyCommitted": (
            True
            if deployment_commit_outcome == "COMMITTED"
            else (
                None
                if deployment_commit_outcome == "AMBIGUOUS"
                else False
            )
        ),
        "applicationRestored": topology_restored,
        "topologyRestored": topology_restored,
        "deploymentCommitOutcome": deployment_commit_outcome,
        "deploymentReceiptPath": (
            str(deployment_path)
            if deployment_receipt_sha256 is not None else None
        ),
        "deploymentReceiptSha256": deployment_receipt_sha256,
        "productionFilesystemChanged": True,
        "productionServiceChangedThisRun": service_changed_this_run,
        "productionDatabaseChangedThisRun": database_changed_this_run,
        "databaseRollbackStrategy": "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
        "databaseDownClaimed": False,
        "officialExpertsPackageChanged": False,
        "observedAt": utc_now(),
    }
    atomic_json(path, receipt)
    return path


def remove_exact_uncommitted_deployment_receipt(path, receipt):
    """Remove only the exact release-owned marker from an interrupted commit."""
    path = pathlib.Path(path)
    if not path.exists() and not path.is_symlink():
        return False
    validate_regular_file(path, path.parent, modes=(0o600,))
    expected = (canonical_json(receipt) + "\n").encode("utf-8")
    if path.read_bytes() != expected:
        raise RuntimeError(
            "deployment receipt commit outcome is ambiguous"
        )
    path.unlink()
    fsync_directory(path.parent)
    return True


def validate_recoverable_running_migration(stage):
    expected = stage.get("preStageRuntimeIdentity", {})
    if expected.get("w1aDatabaseState") != "ABSENT":
        raise RuntimeError(
            "only an absent staged prestate can recover interrupted 043"
        )
    _, connection = parse_environment()
    mysql = Mysql(connection)
    try:
        validate_apply_session_identity(mysql, connection, expected)
        running = int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s AND description=%s",
                (
                    MIGRATION_PUBLIC_VERSION,
                    "RUNNING:Independent Board exact official experts "
                    "attribution v1",
                ),
            )
        )
        any_public = int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s",
                (MIGRATION_PUBLIC_VERSION,),
            )
        )
        applied = int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s AND description=%s",
                (
                    MIGRATION_PUBLIC_VERSION,
                    MIGRATION_PUBLIC_DESCRIPTION,
                ),
            )
        )
        internal = int(
            mysql.scalar(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                "WHERE version=%s AND description=%s",
                (
                    MIGRATION_INTERNAL_VERSION,
                    MIGRATION_INTERNAL_DESCRIPTION,
                ),
            )
        )
        tables = {
            str(row[0])
            for row in mysql.rows(
                "SELECT table_name FROM information_schema.tables "
                "WHERE table_schema=DATABASE() AND table_name IN "
                "('fbs_board_attr_journey_v1',"
                "'fbs_board_attr_event_v1')"
            )
        }
        triggers = {
            str(row[0])
            for row in mysql.rows(
                "SELECT trigger_name FROM information_schema.triggers "
                "WHERE trigger_schema=DATABASE() AND event_object_table IN "
                "('fbs_board_attr_journey_v1',"
                "'fbs_board_attr_event_v1')"
            )
        }
        allowed_tables = {
            "fbs_board_attr_journey_v1",
            "fbs_board_attr_event_v1",
        }
        allowed_triggers = {
            "trg_board_attr_event_v1_no_update",
            "trg_board_attr_event_v1_no_delete",
        }
        event_count = (
            int(mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1"
            ))
            if "fbs_board_attr_event_v1" in tables
            else 0
        )
        journey_count = (
            int(mysql.scalar(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1"
            ))
            if "fbs_board_attr_journey_v1" in tables
            else 0
        )
        permission_count = int(
            mysql.scalar(
                "SELECT COUNT(*) FROM sys_menu WHERE "
                "BINARY perms=BINARY 'board:attribution:query'"
            )
        )
        if applied == 1 and any_public == 1 and running == 0:
            facts = exact_migration_facts(mysql)
            if (
                not migration_structure_matches(facts)
                or facts.get("eventCount") != 0
                or facts.get("journeyCount") != 0
            ):
                raise RuntimeError(
                    "applied 043 recovery shape is unsafe"
                )
            return "EXACT_APPLIED"
        if (
            running != 1
            or applied != 0
            or any_public != 1
            or internal not in (0, 1)
            or not tables.issubset(allowed_tables)
            or not triggers.issubset(allowed_triggers)
            or event_count != 0
            or journey_count != 0
            or permission_count not in (0, 1)
        ):
            raise RuntimeError("running 043 recovery shape is unsafe")
        return "RUNNING_PARTIAL"
    finally:
        mysql.close()


def apply_release(args):
    release = release_directory(args)
    stage_path, stage = validate_stage_receipt(args, release)
    observed = service_snapshot(require_active=False)
    deployment_path = release / "deployment-receipt.json"
    if deployment_path.exists():
        validate_regular_file(deployment_path, release, modes=(0o600,))
        existing = read_json(deployment_path)
        if (
            existing.get("schema")
            != "fbsir.u3wW1aDeploymentReadinessReceipt.v2"
            or existing.get("state") != "DEPLOYED_DEFAULT_OFF"
            or existing.get("releaseId") != args.release_id
            or existing.get("sourceCommit") != args.source_commit
            or existing.get("stageReceiptSha256")
            != sha256_file(stage_path)
            or not SHA_PATTERN.fullmatch(
                str(existing.get("applyApprovalReceiptSha256") or "")
            )
            or type(
                existing.get("productionDatabaseChangedThisRun")
            ) is not bool
            or type(
                existing.get("productionDatabaseChangedSinceStage")
            ) is not bool
            or existing.get("productionDatabaseChanged")
                != existing.get(
                    "productionDatabaseChangedSinceStage"
                )
        ):
            raise RuntimeError("existing deployment receipt identity drifted")
        migration_lease = None
        try:
            migration_lease = deployed_migration_lease(
                args, release, existing
            )
            validate_release_evidence(
                args, release, existing, require_execution=True
            )
            assert_candidate_active(args, release)
            validate_apply_session_identity(
                migration_lease.mysql,
                migration_lease.connection,
                stage["preStageRuntimeIdentity"],
            )
            current_migration = exact_migration_facts(
                migration_lease.mysql
            )
            if not migration_counts_are_monotonic(
                current_migration, migration_lease.facts
            ):
                raise RuntimeError(
                    "existing deployment database evidence drifted"
                )
        except Exception as error:
            try:
                failure_path = write_apply_failure_receipt(
                    args,
                    error,
                    None,
                    "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
                    application_started=False,
                    topology_restored=False,
                    deployment_commit_outcome="COMMITTED",
                    service_changed_this_run=False,
                    database_changed_this_run=False,
                )
            except Exception as receipt_error:
                raise RuntimeError(
                    "existing deployment validation failed without topology "
                    "mutation and failure receipt write also failed: "
                    + type(receipt_error).__name__
                ) from error
            raise ReleaseMutationFailure(
                "existing deployment validation failed without topology "
                "mutation; use explicit Rollback for any service switch",
                failure_path,
            ) from error
        finally:
            if migration_lease is not None:
                migration_lease.close()
        try:
            latest_changed = advance_latest_receipt(
                deployment_path, [stage_path]
            )
        except Exception as error:
            failure_path = write_apply_failure_receipt(
                args,
                error,
                None,
                "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
                application_started=False,
                topology_restored=False,
                deployment_commit_outcome="COMMITTED",
                service_changed_this_run=False,
                database_changed_this_run=False,
            )
            raise ReleaseMutationFailure(
                "deployment receipt is valid but latest receipt link "
                "repair failed",
                failure_path,
            ) from error
        return apply_worker_result(
            args,
            deployment_path,
            filesystem_changed=latest_changed,
            database_changed_this_run=False,
            database_changed_since_stage=bool(
                existing.get(
                    "productionDatabaseChangedSinceStage",
                    existing.get("productionDatabaseChanged"),
                )
            ),
            service_changed=False,
            deployment_committed=False,
            latest_repaired=latest_changed,
            recovered=True,
        )
    validate_preparation_anchors(args)
    validate_pre_apply_nginx_target(args, release)
    recovered_existing_side_effects = False
    try:
        validate_stage_runtime_identity(args, stage)
    except RuntimeError:
        validate_recoverable_running_migration(stage)
        recovered_existing_side_effects = True
    try:
        assert_predecessor_topology(release, observed)
        if observed["activeState"] != "active":
            restore_application(release)
            recovered_existing_side_effects = True
        before = assert_predecessor_active(release)
    except Exception as topology_error:
        try:
            assert_release_owned_recovery_topology(args, release)
            restore_application(release)
            before = assert_predecessor_active(release)
            recovered_existing_side_effects = True
        except Exception as restore_error:
            failure_path = write_apply_failure_receipt(
                args,
                restore_error,
                None,
                "PRE_APPLY_RECOVERY_FAILED_TOPOLOGY_UNCERTAIN",
                application_started=False,
                topology_restored=False,
                deployment_commit_outcome="NOT_ATTEMPTED",
                service_changed_this_run=None,
                database_changed_this_run=False,
            )
            raise ReleaseMutationFailure(
                "release-owned recovery classification or immutable "
                "predecessor restore failed",
                failure_path,
            ) from topology_error
    application_started = False
    migration_facts = None
    receipt = None
    deployment_committed = False
    migration_lease = None
    try:
        migration_lease = apply_migration(
            args, release, stage["preStageRuntimeIdentity"]
        )
        migration_facts = migration_lease.facts
        validate_apply_session_identity(
            migration_lease.mysql,
            migration_lease.connection,
            stage["preStageRuntimeIdentity"],
        )
        application_started = True
        candidate_before_rollback = install_candidate_application(
            args, release
        )
        if candidate_before_rollback["invocationId"] == before["invocationId"]:
            raise RuntimeError("candidate service invocation did not change")
        restore_application(release)
        predecessor_after_rollback = assert_predecessor_active(release)
        retained_after_rollback = exact_migration_facts(
            migration_lease.mysql
        )
        if retained_after_rollback != migration_facts:
            raise RuntimeError(
                "043 current-read drifted during application rollback"
            )
        after = install_candidate_application(args, release)
        rollback_execution_path = application_rollback_execution_receipt(
            args,
            release,
            migration_facts,
            candidate_before_rollback,
            predecessor_after_rollback,
            after,
        )
        receipt = deployment_receipt(
            args,
            stage_path,
            stage,
            migration_facts,
            before,
            after,
            rollback_execution_path,
            migration_lease.database_changed_this_run,
            migration_lease.database_changed_since_stage,
        )
        validate_release_evidence(
            args, release, receipt, require_execution=True
        )
        validate_apply_session_identity(
            migration_lease.mysql,
            migration_lease.connection,
            stage["preStageRuntimeIdentity"],
        )
        if exact_migration_facts(migration_lease.mysql) != migration_facts:
            raise RuntimeError(
                "043 final current-read drifted before receipt commit"
            )
        atomic_json(deployment_path, receipt)
        deployment_committed = True
    except Exception as error:
        if (
            not deployment_committed
            and receipt is not None
            and (deployment_path.exists() or deployment_path.is_symlink())
        ):
            try:
                remove_exact_uncommitted_deployment_receipt(
                    deployment_path, receipt
                )
            except Exception as cleanup_error:
                failure_path = write_apply_failure_receipt(
                    args,
                    cleanup_error,
                    migration_facts,
                    "DEPLOYMENT_COMMIT_AMBIGUOUS_NO_AUTOMATIC_RESTORE",
                    application_started=True,
                    topology_restored=False,
                    deployment_commit_outcome="AMBIGUOUS",
                    service_changed_this_run=True,
                    database_changed_this_run=(
                        migration_lease.database_changed_this_run
                        if migration_lease is not None else None
                    ),
                )
                raise ReleaseMutationFailure(
                    "deployment commit outcome is ambiguous; automatic "
                    "application restore was refused",
                    failure_path,
                ) from error
        restored = False
        if application_started:
            try:
                assert_release_owned_recovery_topology(args, release)
                restore_application(release)
                assert_predecessor_active(release)
                restored = True
            except Exception as restore_error:
                failure_path = write_apply_failure_receipt(
                    args,
                    restore_error,
                    migration_facts,
                    "APPLICATION_RESTORE_FAILED_TOPOLOGY_UNCERTAIN",
                    application_started=True,
                    topology_restored=False,
                    deployment_commit_outcome="NOT_COMMITTED",
                    service_changed_this_run=True,
                    database_changed_this_run=(
                        migration_lease.database_changed_this_run
                        if migration_lease is not None else None
                    ),
                )
                raise ReleaseMutationFailure(
                    "release failed and immutable predecessor restore failed: "
                    + type(restore_error).__name__,
                    failure_path,
                ) from error
        failure_path = write_apply_failure_receipt(
            args,
            error,
            migration_facts,
            (
                "APPLICATION_RESTORED_DATABASE_043_"
                "RETAINED_OR_FAIL_CLOSED"
                if restored
                else "FAIL_CLOSED_BEFORE_APPLICATION_SWITCH"
            ),
            application_started=application_started,
            topology_restored=restored,
            deployment_commit_outcome="NOT_COMMITTED",
            service_changed_this_run=(
                True if application_started else False
            ),
            database_changed_this_run=(
                migration_lease.database_changed_this_run
                if migration_lease is not None else None
            ),
        )
        raise ReleaseMutationFailure(
            "Apply failed with a release-owned failure receipt",
            failure_path,
        ) from error
    finally:
        if migration_lease is not None:
            migration_lease.close()
    try:
        latest_changed = advance_latest_receipt(
            deployment_path, [stage_path]
        )
    except Exception as error:
        failure_path = write_apply_failure_receipt(
            args,
            error,
            migration_lease.facts,
            "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
            application_started=True,
            topology_restored=False,
            deployment_commit_outcome="COMMITTED",
            service_changed_this_run=True,
            database_changed_this_run=
                migration_lease.database_changed_this_run,
        )
        raise ReleaseMutationFailure(
            "deployment receipt committed but latest receipt link repair "
            "failed",
            failure_path,
        ) from error
    return apply_worker_result(
        args,
        deployment_path,
        filesystem_changed=True,
        database_changed_this_run=
            migration_lease.database_changed_this_run,
        database_changed_since_stage=
            migration_lease.database_changed_since_stage,
        service_changed=True,
        deployment_committed=True,
        latest_repaired=latest_changed,
        recovered=recovered_existing_side_effects,
    )


def anchored_evidence_json(
    release,
    receipt,
    path_field,
    digest_field,
    relative_path,
):
    expected = release / relative_path
    if receipt.get(path_field) != str(expected):
        raise RuntimeError("nested release evidence path drifted")
    validate_regular_file(expected, expected.parent, modes=(0o600,))
    digest = receipt.get(digest_field)
    if (
        not isinstance(digest, str)
        or not SHA_PATTERN.fullmatch(digest)
        or sha256_file(expected) != digest
    ):
        raise RuntimeError("nested release evidence digest drifted")
    return expected, read_json(expected)


def assert_candidate_snapshot_evidence(args, release, snapshot):
    candidate_jar = str(CURRENT_LINK / "backend/fbsir-admin.jar")
    if (
        not isinstance(snapshot, dict)
        or snapshot.get("configuredJarPath") != candidate_jar
        or snapshot.get("processJarPath") != candidate_jar
        or snapshot.get("configuredJarSha256") != args.backend_sha
        or snapshot.get("processJarSha256") != args.backend_sha
        or snapshot.get("jarSha256") != args.backend_sha
        or snapshot.get("activeState") != "active"
    ):
        raise RuntimeError("candidate rollback snapshot identity drifted")
    return assert_candidate_runtime_contract(args, release, snapshot)


def validate_release_evidence(
    args, release, receipt, require_execution
):
    assembly_path, assembly = anchored_evidence_json(
        release,
        receipt,
        "applicationRollbackAssemblyReceiptPath",
        "applicationRollbackAssemblyReceiptSha256",
        "evidence/application-rollback-assembly.json",
    )
    predecessor = predecessor_facts(release)
    if (
        assembly != predecessor
        or assembly.get("releaseId") != args.release_id
        or assembly.get("sourceCommit") != args.source_commit
        or assembly.get("symlinkForwardAndReverseVerified") is not True
        or assembly.get("productionServiceChanged") is not False
        or assembly.get("productionDatabaseChanged") is not False
        or assembly.get("stageApprovalReceiptSha256")
            != receipt.get("stageApprovalReceiptSha256")
        or assembly.get("releasePlanTargetSha256")
            != receipt.get("releasePlanTargetSha256")
        or assembly.get("releasePlanTargetComparableSha256")
            != receipt.get("releasePlanTargetComparableSha256")
        or assembly.get(
            "finalizeStageLiveTargetComparableSha256"
        ) != receipt.get(
            "finalizeStageLiveTargetComparableSha256"
        )
        or assembly.get(
            "stageOwnedReleaseRootDeltaVerified"
        ) is not True
        or any(
            not SHA_PATTERN.fullmatch(
                str(receipt.get(field) or "")
            )
            for field in (
                "releasePlanTargetSha256",
                "releasePlanTargetComparableSha256",
                "finalizeStageLiveTargetComparableSha256",
            )
        )
        or receipt.get("releasePlanTargetComparableSha256")
            != receipt.get(
                "finalizeStageLiveTargetComparableSha256"
            )
        or receipt.get(
            "stageOwnedReleaseRootDeltaVerified"
        ) is not True
        or assembly.get("preStageApplicationState") not in {
            "UNTOUCHED_LEGACY",
            "EXACT_PRIOR_ROLLBACK_PREDECESSOR",
        }
        or (
            assembly.get("preStageApplicationState")
                == "UNTOUCHED_LEGACY"
            and assembly.get("priorRollbackAnchor") is not None
        )
        or (
            assembly.get("preStageApplicationState")
                == "EXACT_PRIOR_ROLLBACK_PREDECESSOR"
            and (
                not isinstance(
                    assembly.get("priorRollbackAnchor"), dict
                )
                or assembly["priorRollbackAnchor"].get("schema")
                    != "fbsir.u3wPriorRollbackStageAnchor.v1"
                or assembly["priorRollbackAnchor"].get("state")
                    != (
                        "ROLLED_BACK_APPLICATION_DATABASE_043_"
                        "RETAINED_DORMANT"
                    )
            )
        )
    ):
        raise RuntimeError("application rollback assembly evidence drifted")
    database_path, database = anchored_evidence_json(
        release,
        receipt,
        "databaseRollbackSafetyReceiptPath",
        "databaseRollbackSafetyReceiptSha256",
        "evidence/database-rollback-safety.json",
    )
    database_prestate = database.get("preDeploymentDatabaseState")
    retained_prestate = database.get(
        "preDeploymentRetainedMigrationFacts"
    )
    staged_runtime = receipt.get("preStageRuntimeIdentity", {})
    database_prestate_valid = bool(
        (
            database_prestate == "ABSENT"
            and database.get("preDeploymentPublic043Absent") is True
            and database.get(
                "preDeploymentAttributionTablesAbsent"
            ) is True
            and retained_prestate is None
        )
        or (
            database_prestate == "EXACT_043_RETAINED_DORMANT"
            and database.get("preDeploymentPublic043Absent") is False
            and database.get(
                "preDeploymentAttributionTablesAbsent"
            ) is False
            and migration_structure_matches(retained_prestate)
            and retained_prestate.get("eventCount")
                == staged_runtime.get("attributionEventCount")
            and retained_prestate.get("journeyCount")
                == staged_runtime.get("attributionJourneyCount")
            and retained_prestate.get("schemaFingerprintSha256")
                == staged_runtime.get("w1aSchemaFingerprintSha256")
        )
    )
    if (
        database.get("schema")
            != "fbsir.u3wDatabaseRollbackSafetyReceipt.v1"
        or database.get("releaseId") != args.release_id
        or database.get("sourceCommit") != args.source_commit
        or database.get("stageApprovalReceiptSha256")
            != receipt.get("stageApprovalReceiptSha256")
        or database.get("verified") is not True
        or database.get("strategy")
            != "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
        or database.get("migrationSha256") != args.migration_sha
        or not database_prestate_valid
        or staged_runtime.get("w1aDatabaseState") != database_prestate
        or database.get("legacyJarAttributionClassCount") != 0
        or database.get("allW1aFlagsExplicitFalse") is not True
        or database.get("databaseDownClaimed") is not False
        or database.get("productionDatabaseChanged") is not False
    ):
        raise RuntimeError("database rollback safety evidence drifted")
    anchors = [
        {
            "name": "application-rollback-assembly",
            "path": str(assembly_path),
            "sha256": sha256_file(assembly_path),
            "schema": assembly["schema"],
        },
        {
            "name": "database-rollback-safety",
            "path": str(database_path),
            "sha256": sha256_file(database_path),
            "schema": database["schema"],
        },
    ]
    if not require_execution:
        if receipt.get("applicationRollbackProven") is not False:
            raise RuntimeError(
                "Stage cannot claim application rollback execution"
            )
        return anchors
    active_nginx, nginx_dump_sha256 = active_nginx_manifest()
    if (
        receipt.get("activeNginxManifestAfterApply") != active_nginx
        or receipt.get("nginxDumpSha256AfterApply")
            != nginx_dump_sha256
    ):
        raise RuntimeError("deployed active Nginx evidence drifted")
    execution_path, execution = anchored_evidence_json(
        release,
        receipt,
        "applicationRollbackReceiptPath",
        "applicationRollbackReceiptSha256",
        "evidence/application-rollback-execution.json",
    )
    candidate_before = execution.get("candidateBeforeRollback")
    predecessor_after = execution.get("predecessorAfterRollback")
    candidate_after = execution.get("candidateAfterReapply")
    assert_candidate_snapshot_evidence(
        args, release, candidate_before
    )
    assert_restored_predecessor_runtime_contract(
        release, predecessor_after
    )
    assert_candidate_snapshot_evidence(args, release, candidate_after)
    invocation_ids = {
        candidate_before.get("invocationId"),
        predecessor_after.get("invocationId"),
        candidate_after.get("invocationId"),
    }
    if (
        execution.get("schema")
            != "fbsir.u3wApplicationRollbackExecutionReceipt.v1"
        or execution.get("releaseId") != args.release_id
        or execution.get("sourceCommit") != args.source_commit
        or execution.get("applyApprovalReceiptSha256")
            != receipt.get("applyApprovalReceiptSha256")
        or execution.get("strategy")
            != "LIVE_CANDIDATE_TO_IMMUTABLE_PREDECESSOR_TO_CANDIDATE"
        or execution.get("verified") is not True
        or execution.get("database043Retained") is not True
        or execution.get("retainedMigrationFacts")
            != receipt.get("migrationFacts")
        or execution.get("productionServiceChanged") is not True
        or execution.get("productionDatabaseChanged") is not False
        or execution.get("officialExpertsPackageChanged") is not False
        or len(invocation_ids) != 3
        or any(
            not re.fullmatch(r"[0-9a-f]{32}", str(value or ""))
            for value in invocation_ids
        )
    ):
        raise RuntimeError(
            "application rollback execution evidence drifted"
        )
    anchors.append(
        {
            "name": "application-rollback-execution",
            "path": str(execution_path),
            "sha256": sha256_file(execution_path),
            "schema": execution["schema"],
        }
    )
    return anchors


def validate_deployment_receipt(args, release):
    path = release / "deployment-receipt.json"
    validate_regular_file(path, release, modes=(0o600,))
    if sha256_file(path) != args.deployment_receipt_sha:
        raise RuntimeError("deployment receipt anchor drifted")
    receipt = read_json(path)
    if (
        receipt.get("schema") != "fbsir.u3wW1aDeploymentReadinessReceipt.v2"
        or receipt.get("state") != "DEPLOYED_DEFAULT_OFF"
        or receipt.get("releaseId") != args.release_id
        or receipt.get("sourceCommit") != args.source_commit
        or receipt.get("applicationRollbackProven") is not True
        or receipt.get("databaseRollbackSafetyProven") is not True
        or receipt.get("databaseDownClaimed") is not False
        or not SHA_PATTERN.fullmatch(
            str(receipt.get("applyApprovalReceiptSha256") or "")
        )
        or type(
            receipt.get("productionDatabaseChangedThisRun")
        ) is not bool
        or type(
            receipt.get("productionDatabaseChangedSinceStage")
        ) is not bool
        or receipt.get("productionDatabaseChanged")
            != receipt.get("productionDatabaseChangedSinceStage")
    ):
        raise RuntimeError("deployment receipt identity is invalid")
    validate_staged_release_artifacts(args, release, receipt)
    validate_release_evidence(
        args, release, receipt, require_execution=True
    )
    return path, receipt


def verify_release(args):
    release = release_directory(args)
    deployment_path, deployment = validate_deployment_receipt(args, release)
    current = assert_candidate_active(args, release)
    migration_lease = None
    try:
        migration_lease = deployed_migration_lease(
            args, release, deployment
        )
        migration = migration_lease.facts
        frontend = tree_manifest(release / "frontend")
        verified = bool(
            CURRENT_LINK.resolve() == release.resolve()
            and current["jarSha256"] == args.backend_sha
            and frontend["sha256"] == args.frontend_tree_sha
            and migration_counts_are_monotonic(
                migration, deployment.get("migrationFacts")
            )
            and sha256_file(
                release / "evidence/frontend-manifest.txt"
            )
            == args.frontend_tree_sha
            and sha256_file(NGINX_PATH) == deployment.get("nginxSha256")
        )
        if not verified:
            raise RuntimeError(
                "deployed default-off release verification failed"
            )
        return {
            "schema": "fbsir.u3wDefaultOffReleaseWorkerResult.v1",
            "mode": "Verify",
            "state": "DEPLOYED_DEFAULT_OFF",
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "approvalReceiptSha256": args.approval_sha,
            "transitionApprovalReceiptSha256":
                deployment.get("applyApprovalReceiptSha256"),
            "deploymentReceiptPath": str(deployment_path),
            "deploymentReceiptSha256": sha256_file(deployment_path),
            "evidenceReceipts": validate_release_evidence(
                args, release, deployment, require_execution=True
            ),
            "migrationFacts": migration,
            "verified": True,
            "actualActiveArtifactsMatched": True,
            "databaseRollbackSafetyProven": True,
            "databaseDownClaimed": False,
            "productionFilesystemChanged": False,
            "productionDatabaseChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
            "observedAt": utc_now(),
        }
    finally:
        if migration_lease is not None:
            migration_lease.close()


def read_exact_migration_facts():
    _, connection = parse_environment()
    mysql = Mysql(connection)
    try:
        facts = exact_migration_facts(mysql)
    finally:
        mysql.close()
    expected = {
        "publicReceiptCount": 1,
        "internalReceiptCount": 1,
        "tableCount": 2,
        "triggerCount": 2,
        "permissionCount": 1,
        "schemaFingerprintSha256": EXPECTED_W1A_SCHEMA_FINGERPRINT,
    }
    if any(facts.get(key) != value for key, value in expected.items()):
        raise RuntimeError("retained 043 current-read failed")
    return facts


def assert_predecessor_owned_topology(release):
    previous = predecessor_facts(release)
    after = service_snapshot(require_active=False)
    rollback_dropin = release / "evidence/rollback-systemd-dropin.conf"
    if (
        after["jarSha256"] != previous["previousJarSha256"]
        or after["configuredJarSha256"]
            != previous["previousJarSha256"]
        or CURRENT_LINK.exists()
        or CURRENT_LINK.is_symlink()
        or not DROPIN_PATH.is_file()
        or DROPIN_PATH.is_symlink()
        or sha256_file(DROPIN_PATH) != sha256_file(rollback_dropin)
        or sha256_file(NGINX_PATH) != previous["previousNginxSha256"]
    ):
        raise RuntimeError("immutable predecessor current-read failed")
    return after


def assert_predecessor_active(release):
    after = assert_predecessor_owned_topology(release)
    if after["activeState"] != "active":
        raise RuntimeError("immutable predecessor service is not active")
    assert_restored_predecessor_runtime_contract(release, after)
    wait_for_u3w_health()
    return after


def validate_rollback_receipt_base(original, release):
    deployment_path = release / "deployment-receipt.json"
    validate_regular_file(deployment_path, release, modes=(0o600,))
    state = original.get("state")
    retained = original.get("retainedMigrationFacts")
    if state == "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT":
        database_evidence_valid = bool(
            original.get("databaseSafetyCurrentRead") == "VERIFIED"
            and migration_structure_matches(retained)
        )
    elif (
        state
        == "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE"
    ):
        database_evidence_valid = bool(
            isinstance(original.get("databaseSafetyCurrentRead"), str)
            and original["databaseSafetyCurrentRead"].startswith(
                "UNAVAILABLE:"
            )
            and retained is None
        )
    else:
        database_evidence_valid = False
    if (
        original.get("schema")
            != "fbsir.u3wDefaultOffReleaseRollbackReceipt.v1"
        or original.get("releaseId") != release.name
        or not COMMIT_PATTERN.fullmatch(
            str(original.get("sourceCommit") or "")
        )
        or original.get("deploymentReceiptPath")
            != str(deployment_path)
        or original.get("deploymentReceiptSha256")
            != sha256_file(deployment_path)
        or not SHA_PATTERN.fullmatch(
            str(original.get("rollbackApprovalReceiptSha256") or "")
        )
        or original.get("applicationRollbackVerified") is not True
        or original.get("retainedRollbackDropIn") is not True
        or original.get("databaseRollbackStrategy")
            != "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
        or original.get("databaseDownClaimed") is not False
        or original.get("allW1aFlagsExplicitFalse") is not True
        or original.get("productionFilesystemChanged") is not True
        or original.get("productionDatabaseChanged") is not False
        or type(original.get("productionServiceChanged")) is not bool
        or original.get("officialExpertsPackageChanged") is not False
        or type(original.get("recoveredExistingSideEffects")) is not bool
        or not isinstance(original.get("serviceBefore"), dict)
        or not isinstance(original.get("serviceAfter"), dict)
        or not database_evidence_valid
    ):
        raise RuntimeError("rollback receipt chain identity drifted")
    return deployment_path


def validated_rollback_receipt_chain(receipt_path):
    receipt_path = pathlib.Path(receipt_path)
    validate_regular_file(
        receipt_path, receipt_path.parent, modes=(0o600,)
    )
    release = receipt_path.parent
    if (
        release.parent != RELEASE_ROOT
        or not RUN_PATTERN.fullmatch(release.name)
    ):
        raise RuntimeError("rollback receipt escaped the release root")
    latest = read_json(receipt_path)
    verification = None
    if (
        latest.get("schema")
        == "fbsir.u3wDefaultOffRollbackVerificationReceipt.v1"
    ):
        if receipt_path.name != "rollback-verification-receipt.json":
            raise RuntimeError("rollback verification path is invalid")
        original_path = release / "rollback-receipt.json"
        validate_regular_file(
            original_path, release, modes=(0o600,)
        )
        if (
            latest.get("rollbackReceiptPath") != str(original_path)
            or latest.get("rollbackReceiptSha256")
                != sha256_file(original_path)
        ):
            raise RuntimeError(
                "rollback verification original anchor drifted"
            )
        original = read_json(original_path)
        verification = latest
    else:
        if receipt_path.name != "rollback-receipt.json":
            raise RuntimeError("rollback receipt path is invalid")
        original_path = receipt_path
        original = latest
    deployment_path = validate_rollback_receipt_base(original, release)
    effective_state = original.get("state")
    retained_facts = original.get("retainedMigrationFacts")
    if verification is not None:
        if (
            original.get("state")
            != "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE"
            or verification.get("state")
            != "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
            or verification.get("releaseId") != release.name
            or verification.get("sourceCommit")
                != original.get("sourceCommit")
            or verification.get("deploymentReceiptPath")
                != str(deployment_path)
            or verification.get("deploymentReceiptSha256")
                != sha256_file(deployment_path)
            or not SHA_PATTERN.fullmatch(
                str(
                    verification.get(
                        "rollbackApprovalReceiptSha256"
                    ) or ""
                )
            )
            or verification.get("databaseSafetyCurrentRead")
                != "VERIFIED"
            or verification.get("databaseRollbackStrategy")
                != "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
            or verification.get("databaseDownClaimed") is not False
            or verification.get("productionFilesystemChanged") is not True
            or verification.get("productionDatabaseChanged") is not False
            or verification.get("productionServiceChanged") is not False
            or verification.get("officialExpertsPackageChanged") is not False
        ):
            raise RuntimeError("rollback verification receipt drifted")
        effective_state = verification["state"]
        retained_facts = verification.get("retainedMigrationFacts")
    if (
        effective_state
        != "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
        or not migration_structure_matches(retained_facts)
    ):
        raise RuntimeError("rollback chain does not prove retained 043")
    return {
        "release": release,
        "receiptPath": receipt_path,
        "receiptSha256": sha256_file(receipt_path),
        "receiptSchema": latest["schema"],
        "sourceCommit": original["sourceCommit"],
        "state": effective_state,
        "retainedMigrationFacts": retained_facts,
        "originalReceiptPath": original_path,
        "originalReceiptSha256": sha256_file(original_path),
    }


def validate_prior_rollback_stage_entry(current):
    if (
        not LATEST_RECEIPT.is_symlink()
        or CURRENT_LINK.exists()
        or CURRENT_LINK.is_symlink()
    ):
        raise RuntimeError("prior rollback latest topology is invalid")
    chain = validated_rollback_receipt_chain(
        LATEST_RECEIPT.resolve(strict=True)
    )
    release = chain["release"]
    previous = predecessor_facts(release)
    rollback_dropin = release / "evidence/rollback-systemd-dropin.conf"
    if (
        not DROPIN_PATH.is_file()
        or DROPIN_PATH.is_symlink()
        or sha256_file(DROPIN_PATH) != sha256_file(rollback_dropin)
        or not NGINX_PATH.is_file()
        or NGINX_PATH.is_symlink()
        or sha256_file(NGINX_PATH) != previous["previousNginxSha256"]
        or current.get("activeState") != "active"
        or current.get("jarSha256") != previous["previousJarSha256"]
    ):
        raise RuntimeError("prior rollback predecessor topology drifted")
    assert_restored_predecessor_runtime_contract(release, current)
    migration = read_exact_migration_facts()
    if migration != chain["retainedMigrationFacts"]:
        raise RuntimeError("prior rollback retained 043 facts drifted")
    wait_for_u3w_health()
    return {
        "schema": "fbsir.u3wPriorRollbackStageAnchor.v1",
        "releaseId": release.name,
        "sourceCommit": chain["sourceCommit"],
        "receiptPath": str(chain["receiptPath"]),
        "receiptSha256": chain["receiptSha256"],
        "receiptSchema": chain["receiptSchema"],
        "state": chain["state"],
    }


def assert_release_owned_recovery_topology(args, release):
    previous = predecessor_facts(release)
    previous_snapshot = previous["serviceSnapshotBeforeStage"]
    current = service_snapshot(require_active=False)
    candidate_dropin = sha256_file(
        release / "evidence/systemd-dropin.conf"
    )
    rollback_dropin = sha256_file(
        release / "evidence/rollback-systemd-dropin.conf"
    )
    baseline_dropin_entries = [
        item
        for item in previous_snapshot.get("dropInManifest", [])
        if item.get("path") == str(DROPIN_PATH)
    ]
    baseline_dropin = (
        baseline_dropin_entries[0].get("sha256")
        if len(baseline_dropin_entries) == 1
        else None
    )
    if len(baseline_dropin_entries) > 1:
        raise RuntimeError("staged predecessor drop-in manifest is ambiguous")
    candidate_nginx = sha256_file(
        release / "evidence/u3w-portal-sites.conf"
    )
    allowed_loaded_dropins = (
        previous_snapshot.get("dropInManifest", []),
        release_dropin_manifest(release, "systemd-dropin.conf"),
        release_dropin_manifest(
            release, "rollback-systemd-dropin.conf"
        ),
    )
    allowed_argv_sha256 = {
        previous_snapshot.get("processArgvSha256"),
        sha256_bytes(
            ("\0".join(candidate_process_arguments()) + "\0").encode(
                "utf-8"
            )
        ),
        sha256_bytes(
            ("\0".join(rollback_process_arguments(release)) + "\0").encode(
                "utf-8"
            )
        ),
    }
    allowed_jar_paths = {
        previous_snapshot.get("configuredJarPath"),
        previous_snapshot.get("processJarPath"),
        str(CURRENT_LINK / "backend/fbsir-admin.jar"),
        str(release / "rollback/previous-admin.jar"),
    }
    if (
        current.get("user") != previous_snapshot.get("user")
        or current.get("group") != previous_snapshot.get("group")
        or current.get("fragmentPath")
            != previous_snapshot.get("fragmentPath")
        or current.get("fragmentFileManifest")
            != previous_snapshot.get("fragmentFileManifest")
        or current.get("environmentFilePaths")
            != previous_snapshot.get("environmentFilePaths")
        or current.get("environmentFileManifest")
            != previous_snapshot.get("environmentFileManifest")
        or current.get("api2EventKeyManifest")
            != previous_snapshot.get("api2EventKeyManifest")
        or current.get("dropInManifest") not in allowed_loaded_dropins
        or current.get("externalConfigManifest")
            != previous_snapshot.get("externalConfigManifest")
        or current.get("additionalConfigSha256")
            != previous_snapshot.get("additionalConfigSha256")
        or current.get("configuredJarPath") not in allowed_jar_paths
        or (
            current.get("processJarPath") is not None
            and current.get("processJarPath") not in allowed_jar_paths
        )
        or current.get("workingDirectory") not in {
            previous_snapshot.get("workingDirectory"),
            str(ADMIN_ROOT),
        }
        or (
            current.get("activeState") == "active"
            and current.get("processArgvSha256")
                not in allowed_argv_sha256
        )
    ):
        raise RuntimeError(
            "rollback recovery runtime contract is not release-owned"
        )
    if current.get("activeState") == "active":
        if (
            current.get("processArgvSha256")
            == previous_snapshot.get("processArgvSha256")
        ):
            assert_legacy_runtime_contract(release, current)
        elif not exact_loaded_environment_matches(
            current, previous_snapshot
        ):
            raise RuntimeError(
                "rollback recovery environment is partial or drifted"
            )
    if not CURRENT_LINK.exists() and not CURRENT_LINK.is_symlink():
        link_state = "ABSENT"
    elif (
        CURRENT_LINK.is_symlink()
        and CURRENT_LINK.resolve() == release.resolve()
    ):
        link_state = "CANDIDATE"
    else:
        raise RuntimeError("rollback recovery current link is unowned")
    if not DROPIN_PATH.exists() and not DROPIN_PATH.is_symlink():
        dropin_state = "ABSENT"
    elif DROPIN_PATH.is_file() and not DROPIN_PATH.is_symlink():
        dropin_sha = sha256_file(DROPIN_PATH)
        if dropin_sha == candidate_dropin:
            dropin_state = "CANDIDATE"
        elif dropin_sha == rollback_dropin:
            dropin_state = "ROLLBACK"
        elif baseline_dropin and dropin_sha == baseline_dropin:
            dropin_state = "BASELINE"
        else:
            raise RuntimeError("rollback recovery drop-in is unowned")
    else:
        raise RuntimeError("rollback recovery drop-in is unsafe")
    if (
        NGINX_PATH.is_file()
        and not NGINX_PATH.is_symlink()
        and sha256_file(NGINX_PATH) == candidate_nginx
    ):
        nginx_state = "CANDIDATE"
    elif (
        NGINX_PATH.is_file()
        and not NGINX_PATH.is_symlink()
        and sha256_file(NGINX_PATH) == previous["previousNginxSha256"]
    ):
        nginx_state = "PREDECESSOR"
    else:
        raise RuntimeError("rollback recovery Nginx file is unowned")
    previous_sha = previous["previousJarSha256"]
    jar_state = (
        current["configuredJarSha256"],
        current["jarSha256"],
    )
    topology = (link_state, dropin_state, nginx_state, jar_state)
    initial_dropin_state = (
        "BASELINE" if baseline_dropin is not None else "ABSENT"
    )
    allowed_topologies = {
        ("ABSENT", initial_dropin_state, "PREDECESSOR",
         (previous_sha, previous_sha)),
        ("CANDIDATE", initial_dropin_state, "PREDECESSOR",
         (previous_sha, previous_sha)),
        ("CANDIDATE", "CANDIDATE", "PREDECESSOR",
         (previous_sha, previous_sha)),
        ("CANDIDATE", "CANDIDATE", "PREDECESSOR",
         (args.backend_sha, previous_sha)),
        ("CANDIDATE", "CANDIDATE", "CANDIDATE",
         (previous_sha, previous_sha)),
        ("CANDIDATE", "CANDIDATE", "CANDIDATE",
         (args.backend_sha, previous_sha)),
        ("CANDIDATE", "CANDIDATE", "CANDIDATE",
         (args.backend_sha, args.backend_sha)),
        ("CANDIDATE", "CANDIDATE", "PREDECESSOR",
         (args.backend_sha, args.backend_sha)),
        ("CANDIDATE", "ROLLBACK", "PREDECESSOR",
         (args.backend_sha, args.backend_sha)),
        ("CANDIDATE", "ROLLBACK", "PREDECESSOR",
         (previous_sha, args.backend_sha)),
        ("CANDIDATE", "ROLLBACK", "PREDECESSOR",
         (previous_sha, previous_sha)),
        ("ABSENT", "ROLLBACK", "PREDECESSOR",
         (previous_sha, previous_sha)),
    }
    if topology not in allowed_topologies:
        raise RuntimeError("rollback recovery topology is not release-owned")
    return current


def rollback_transition(args, release):
    restore_performed = False
    recovered_existing_side_effects = False
    try:
        before = assert_candidate_owned_topology(args, release)
        restore_application(release)
        restore_performed = True
    except Exception:
        try:
            before = assert_predecessor_owned_topology(release)
            recovered_existing_side_effects = True
            if before["activeState"] != "active":
                restore_application(release)
                restore_performed = True
        except Exception:
            before = assert_release_owned_recovery_topology(args, release)
            restore_application(release)
            restore_performed = True
            recovered_existing_side_effects = True
    after = assert_predecessor_active(release)
    return (
        before,
        after,
        restore_performed,
        recovered_existing_side_effects,
    )


ROLLBACK_FAILURE_STATES = frozenset((
    "ROLLBACK_FAILED_AFTER_BOUNDED_RELEASE_OWNED_RECOVERY",
    "ROLLBACK_APPLICATION_RESTORED_EVIDENCE_FINALIZATION_FAILED",
))


def write_rollback_failure_receipt(
    args,
    initial_error,
    recovery_error,
    *,
    state="ROLLBACK_FAILED_AFTER_BOUNDED_RELEASE_OWNED_RECOVERY",
    application_restored=False,
):
    if (
        state not in ROLLBACK_FAILURE_STATES
        or type(application_restored) is not bool
    ):
        raise RuntimeError("rollback failure receipt semantics are invalid")
    release = release_directory(args)
    timestamp = dt.datetime.now(dt.timezone.utc).strftime(
        "%Y%m%dT%H%M%S%fZ"
    )
    identity = "{}:{}:{}".format(
        type(initial_error).__name__,
        type(recovery_error).__name__,
        timestamp,
    )
    path = release / (
        "rollback-failure-{}-{}.json".format(
            timestamp, sha256_bytes(identity.encode("utf-8"))[:12]
        )
    )
    receipt = {
        "schema": "fbsir.u3wDefaultOffRollbackFailureReceipt.v1",
        "state": state,
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "rollbackApprovalReceiptSha256": args.approval_sha,
        "initialErrorType": type(initial_error).__name__,
        "initialErrorMessageSha256": sha256_bytes(
            str(initial_error).encode("utf-8")
        ),
        "recoveryErrorType": type(recovery_error).__name__,
        "recoveryErrorMessageSha256": sha256_bytes(
            str(recovery_error).encode("utf-8")
        ),
        "applicationRestored": application_restored,
        "databaseDownClaimed": False,
        "officialExpertsPackageChanged": False,
        "observedAt": utc_now(),
    }
    atomic_json(path, receipt)
    return path


def ensure_rollback_verification_receipt(
    args, release, rollback_path, rollback, migration_facts
):
    if not migration_structure_matches(migration_facts):
        raise RuntimeError("rollback verification migration facts drifted")
    path = release / "rollback-verification-receipt.json"
    expected = {
        "schema": "fbsir.u3wDefaultOffRollbackVerificationReceipt.v1",
        "state": (
            "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
        ),
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "rollbackReceiptPath": str(rollback_path),
        "rollbackReceiptSha256": sha256_file(rollback_path),
        "deploymentReceiptPath": rollback["deploymentReceiptPath"],
        "deploymentReceiptSha256":
            rollback["deploymentReceiptSha256"],
        "databaseSafetyCurrentRead": "VERIFIED",
        "retainedMigrationFacts": migration_facts,
        "databaseRollbackStrategy": "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
        "databaseDownClaimed": False,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionServiceChanged": False,
        "officialExpertsPackageChanged": False,
    }
    if path.exists() or path.is_symlink():
        validate_regular_file(path, release, modes=(0o600,))
        receipt = read_json(path)
        if (
            any(receipt.get(key) != value for key, value in expected.items())
            or not SHA_PATTERN.fullmatch(
                str(
                    receipt.get(
                        "rollbackApprovalReceiptSha256"
                    ) or ""
                )
            )
        ):
            raise RuntimeError(
                "existing rollback verification receipt drifted"
            )
        return path, receipt, False
    receipt = dict(expected)
    receipt["rollbackApprovalReceiptSha256"] = args.approval_sha
    receipt["observedAt"] = utc_now()
    atomic_json(path, receipt)
    return path, receipt, True


def _rollback_release(args):
    release = release_directory(args)
    deployment_path, deployment = validate_deployment_receipt(args, release)
    path = release / "rollback-receipt.json"
    if path.exists():
        validate_regular_file(path, release, modes=(0o600,))
        receipt = read_json(path)
        if (
            receipt.get("schema")
            != "fbsir.u3wDefaultOffReleaseRollbackReceipt.v1"
            or receipt.get("releaseId") != args.release_id
            or receipt.get("sourceCommit") != args.source_commit
            or receipt.get("deploymentReceiptSha256")
            != sha256_file(deployment_path)
        ):
            raise RuntimeError("existing rollback receipt identity drifted")
        validate_rollback_receipt_base(receipt, release)
        assert_predecessor_active(release)
        migration_facts = None
        try:
            migration_facts = read_exact_migration_facts()
            current_state = (
                "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
            )
        except Exception:
            current_state = (
                "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE"
            )
        result_path = path
        result_receipt = receipt
        verification_created = False
        if receipt.get("state") == current_state:
            if (
                current_state
                == "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
                and migration_facts
                    != receipt.get("retainedMigrationFacts")
            ):
                raise RuntimeError(
                    "existing rollback retained ledger facts drifted"
                )
        elif (
            receipt.get("state")
                == (
                    "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_"
                    "UNAVAILABLE"
                )
            and current_state
                == (
                    "ROLLED_BACK_APPLICATION_DATABASE_043_"
                    "RETAINED_DORMANT"
                )
        ):
            (
                result_path,
                result_receipt,
                verification_created,
            ) = ensure_rollback_verification_receipt(
                args, release, path, receipt, migration_facts
            )
            validated_rollback_receipt_chain(result_path)
        else:
            raise RuntimeError(
                "existing rollback receipt no longer matches current "
                "database safety read"
            )
        if (
            result_receipt.get("state")
            == "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
        ):
            validated_rollback_receipt_chain(result_path)
        latest_changed = advance_latest_receipt(
            result_path,
            [
                deployment_path,
                path,
                release / "rollback-verification-receipt.json",
            ],
        )
        return {
            "schema": "fbsir.u3wDefaultOffReleaseWorkerResult.v1",
            "mode": "Rollback",
            "state": result_receipt["state"],
            "releaseId": args.release_id,
            "sourceCommit": args.source_commit,
            "approvalReceiptSha256": args.approval_sha,
            "transitionApprovalReceiptSha256":
                result_receipt.get(
                    "rollbackApprovalReceiptSha256"
                ),
            "receiptPath": str(result_path),
            "receiptSha256": sha256_file(result_path),
            "evidenceReceipts": validate_release_evidence(
                args, release, deployment, require_execution=True
            ),
            "productionFilesystemChanged": bool(
                verification_created or latest_changed
            ),
            "productionDatabaseChanged": False,
            "productionServiceChanged": False,
            "officialExpertsPackageChanged": False,
        }
    try:
        (
            before,
            after,
            restore_performed,
            recovered_existing_side_effects,
        ) = rollback_transition(args, release)
    except Exception as initial_error:
        try:
            before = assert_release_owned_recovery_topology(args, release)
            restore_application(release)
            after = assert_predecessor_active(release)
            restore_performed = True
            recovered_existing_side_effects = True
        except Exception as recovery_error:
            failure_path = write_rollback_failure_receipt(
                args, initial_error, recovery_error
            )
            raise ReleaseMutationFailure(
                "rollback and bounded release-owned recovery failed; "
                "failure receipt was committed",
                failure_path,
            ) from initial_error
    previous = predecessor_facts(release)
    try:
        migration_facts = read_exact_migration_facts()
        database_safety_current_read = "VERIFIED"
    except Exception as database_error:
        migration_facts = None
        database_safety_current_read = (
            "UNAVAILABLE:" + type(database_error).__name__
        )
    database_current_read_verified = (
        database_safety_current_read == "VERIFIED"
    )
    flags_false = environment_flags_explicit_false()
    verified = bool(
        after["jarSha256"] == previous["previousJarSha256"]
        and (
            before["jarSha256"] != args.backend_sha
            or before["activeState"] != "active"
            or after["invocationId"] != before["invocationId"]
        )
        and DROPIN_PATH.is_file()
        and not DROPIN_PATH.is_symlink()
        and not CURRENT_LINK.exists()
        and not CURRENT_LINK.is_symlink()
        and sha256_file(NGINX_PATH) == previous["previousNginxSha256"]
        and flags_false is True
    )
    if not verified:
        raise RuntimeError("application rollback current-read failed")
    rollback_state = (
        "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT"
        if database_current_read_verified
        else "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE"
    )
    receipt = {
        "schema": "fbsir.u3wDefaultOffReleaseRollbackReceipt.v1",
        "state": rollback_state,
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "rollbackApprovalReceiptSha256": args.approval_sha,
        "deploymentReceiptPath": str(deployment_path),
        "deploymentReceiptSha256": sha256_file(deployment_path),
        "serviceBefore": (
            deployment.get("serviceAfter")
            if recovered_existing_side_effects
            else before
        ),
        "serviceAfter": after,
        "applicationRollbackVerified": True,
        "retainedRollbackDropIn": True,
        "recoveredExistingSideEffects":
            recovered_existing_side_effects,
        "databaseRollbackStrategy": "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN",
        "retainedMigrationFacts": migration_facts,
        "databaseSafetyCurrentRead": database_safety_current_read,
        "databaseDownClaimed": False,
        "allW1aFlagsExplicitFalse": flags_false,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionServiceChanged": restore_performed,
        "officialExpertsPackageChanged": False,
        "observedAt": utc_now(),
    }
    atomic_json(path, receipt)
    advance_latest_receipt(path, [deployment_path])
    return {
        "schema": "fbsir.u3wDefaultOffReleaseWorkerResult.v1",
        "mode": "Rollback",
        "state": receipt["state"],
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "transitionApprovalReceiptSha256":
            receipt.get("rollbackApprovalReceiptSha256"),
        "receiptPath": str(path),
        "receiptSha256": sha256_file(path),
        "evidenceReceipts": validate_release_evidence(
            args, release, deployment, require_execution=True
        ),
        "recoveredExistingSideEffects":
            recovered_existing_side_effects,
        "productionFilesystemChanged": True,
        "productionDatabaseChanged": False,
        "productionServiceChanged": restore_performed,
        "officialExpertsPackageChanged": False,
    }


def rollback_release(args):
    try:
        return _rollback_release(args)
    except ReleaseMutationFailure:
        raise
    except Exception as error:
        release = release_directory(args)
        try:
            current = assert_predecessor_owned_topology(release)
            if current.get("activeState") != "active":
                raise RuntimeError(
                    "predecessor is not active after rollback failure"
                )
            assert_restored_predecessor_runtime_contract(
                release, current
            )
            failure_path = write_rollback_failure_receipt(
                args,
                error,
                error,
                state=(
                    "ROLLBACK_APPLICATION_RESTORED_"
                    "EVIDENCE_FINALIZATION_FAILED"
                ),
                application_restored=True,
            )
        except Exception as evidence_error:
            raise RuntimeError(
                "rollback failed and post-failure predecessor state "
                "could not be safely evidenced: "
                + type(evidence_error).__name__
            ) from error
        raise ReleaseMutationFailure(
            "rollback application restore succeeded but evidence "
            "finalization failed",
            failure_path,
        ) from error


def execute(args):
    if args.mode == "PrepareStage":
        return prepare_stage(args)
    if args.mode == "FinalizeStage":
        return finalize_stage(args)
    if args.mode == "Apply":
        return apply_release(args)
    if args.mode == "Rollback":
        return rollback_release(args)
    if args.mode == "Verify":
        return verify_release(args)
    raise RuntimeError("unsupported release mode")


def validated_failure_receipt_evidence(args, error):
    if not isinstance(error, ReleaseMutationFailure):
        return None
    path = error.failure_receipt_path
    release = release_directory(args)
    if path.parent != release:
        raise RuntimeError("failure receipt escaped the exact release root")
    if args.mode == "Apply":
        name_matches = APPLY_FAILURE_NAME_PATTERN.fullmatch(path.name)
        expected_schema = "fbsir.u3wDefaultOffReleaseFailureReceipt.v1"
        approval_field = "applyApprovalReceiptSha256"
        allowed_states = APPLY_FAILURE_STATES
    elif args.mode == "Rollback":
        name_matches = ROLLBACK_FAILURE_NAME_PATTERN.fullmatch(path.name)
        expected_schema = "fbsir.u3wDefaultOffRollbackFailureReceipt.v1"
        approval_field = "rollbackApprovalReceiptSha256"
        allowed_states = ROLLBACK_FAILURE_STATES
    else:
        raise RuntimeError(
            "non-mutating worker mode cannot attach a failure receipt"
        )
    if not name_matches:
        raise RuntimeError("failure receipt name is invalid")
    validate_regular_file(path, release, modes=(0o600,))
    receipt = read_json(path)
    if (
        receipt.get("schema") != expected_schema
        or receipt.get("state") not in allowed_states
        or receipt.get("releaseId") != args.release_id
        or receipt.get("sourceCommit") != args.source_commit
        or receipt.get(approval_field) != args.approval_sha
        or receipt.get("officialExpertsPackageChanged") is not False
    ):
        raise RuntimeError("failure receipt identity is invalid")
    return {
        "failureReceiptPath": str(path),
        "failureReceiptSha256": sha256_file(path),
        "failureReceiptSchema": receipt["schema"],
        "failureReceiptState": receipt["state"],
        "failureReceiptEvidenceValid": True,
    }


def worker_error_envelope(args, error):
    result = {
        "schema": "fbsir.u3wDefaultOffReleaseWorkerError.v2",
        "state": "WORKER_FAILED",
        "mode": args.mode,
        "releaseId": args.release_id,
        "sourceCommit": args.source_commit,
        "approvalReceiptSha256": args.approval_sha,
        "errorType": type(error).__name__,
        "errorMessageSha256": sha256_bytes(str(error).encode("utf-8")),
        "failureReceiptPath": None,
        "failureReceiptSha256": None,
        "failureReceiptSchema": None,
        "failureReceiptState": None,
        "failureReceiptEvidenceValid": False,
        "officialExpertsPackageChanged": False,
    }
    try:
        evidence = validated_failure_receipt_evidence(args, error)
        if evidence is not None:
            result.update(evidence)
    except Exception as evidence_error:
        result["failureReceiptValidationErrorType"] = type(
            evidence_error
        ).__name__
        result["failureReceiptValidationErrorMessageSha256"] = (
            sha256_bytes(str(evidence_error).encode("utf-8"))
        )
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode",
        required=True,
        choices=(
            "PrepareStage",
            "FinalizeStage",
            "Apply",
            "Rollback",
            "Verify",
        ),
    )
    parser.add_argument("--release-id", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--approval-sha", required=True)
    parser.add_argument("--approval-json-base64", required=True)
    parser.add_argument("--runner-sha", required=True)
    parser.add_argument("--worker-sha", required=True)
    parser.add_argument("--build-receipt-sha", required=True)
    parser.add_argument("--plan-receipt-sha", required=True)
    parser.add_argument("--backup-receipt-sha", required=True)
    parser.add_argument("--baseline-receipt-sha", required=True)
    parser.add_argument("--configuration-receipt-sha", required=True)
    parser.add_argument("--stage-receipt-sha", required=True)
    parser.add_argument("--deployment-receipt-sha", required=True)
    parser.add_argument("--backend-sha", required=True)
    parser.add_argument("--frontend-tree-sha", required=True)
    parser.add_argument("--migration-sha", required=True)
    args = parser.parse_args()
    try:
        validate_arguments(args)
        if args.mode == "Verify":
            ensure_parent_directory(ADMIN_ROOT)
            descriptor = os.open(
                LOCK_PATH,
                os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
            )
            lock_mode = fcntl.LOCK_SH
        else:
            safe_directory(ADMIN_ROOT, 0o755)
            descriptor = os.open(
                LOCK_PATH,
                os.O_RDWR
                | os.O_CREAT
                | getattr(os, "O_NOFOLLOW", 0),
                0o600,
            )
            lock_mode = fcntl.LOCK_EX
        try:
            status = os.fstat(descriptor)
            if (
                not stat.S_ISREG(status.st_mode)
                or status.st_uid != 0
                or status.st_gid != 0
                or status.st_nlink != 1
            ):
                raise RuntimeError("global release lock custody is invalid")
            if args.mode != "Verify":
                os.fchmod(descriptor, 0o600)
            elif status.st_mode & 0o777 != 0o600:
                raise RuntimeError("global release lock mode is invalid")
            fcntl.flock(descriptor, lock_mode)
            print(canonical_json(execute(args)))
        finally:
            fcntl.flock(descriptor, fcntl.LOCK_UN)
            os.close(descriptor)
        return 0
    except Exception as error:
        print(canonical_json(worker_error_envelope(args, error)))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
