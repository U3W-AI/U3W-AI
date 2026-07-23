#!/usr/bin/env python3
"""Independent same-host, socket-only restore verifier.

The verifier distrusts runner success flags, re-hashes every artifact, starts a
fresh MySQL instance with networking disabled, restores through its Unix
socket, checks every table, then removes the isolated state before publishing
an immutable receipt.
"""

import argparse
import base64
import datetime as dt
import fcntl
import hashlib
import json
import os
import pathlib
import pwd
import re
import signal
import shutil
import subprocess
import sys
import tempfile
import threading
import time


TARGET_HOST = "api2.u3w.com"
DATABASE = "fbsir"
BACKUP_ROOT = pathlib.Path("/opt/fbsir/admin/backups/w1a")
HOST_CHANGE_LOCK = pathlib.Path("/opt/fbsir/admin/.u3w-production-change.lock")
DATA_ROOT = pathlib.Path("/var/lib/fbsir-w1a-restore")
RUNTIME_ROOT = pathlib.Path("/run/fbsir-w1a-restore")
BACKUP_KEY_PATH = pathlib.Path("/etc/u3w/fbsir-backup.key")
MYSQLD = pathlib.Path("/usr/libexec/mysqld")
BACKUP_SCHEMA = "fbsir.u3wDatabaseBackupReceipt.v2"
RESTORE_SCHEMA = "fbsir.u3wDatabaseRestoreRehearsalReceipt.v2"
BUNDLE_SCHEMA = "fbsir.u3wDatabaseBackupRestoreBundleReceipt.v1"
RUN_PATTERN = re.compile(r"w1a-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}")
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")


def canonical_json(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def utc_now():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="milliseconds").replace(
        "+00:00", "Z"
    )


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


def atomic_bytes(path, payload, mode=0o600):
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
            raise RuntimeError("unsafe stale evidence partial")
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


def atomic_json(path, value):
    atomic_bytes(path, (canonical_json(value) + "\n").encode("utf-8"))


def validate_digest(value, label):
    if not SHA_PATTERN.fullmatch(str(value or "")):
        raise RuntimeError(label + " is not a SHA-256 digest")


def validate_regular_file(path, expected_parent, mode=0o600):
    if path.is_symlink() or not path.is_file() or path.resolve().parent != expected_parent:
        raise RuntimeError("artifact path or type is invalid: " + str(path))
    status = path.stat()
    if (
        status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != mode
        or status.st_nlink != 1
    ):
        raise RuntimeError("artifact custody is invalid: " + str(path))


class SocketMysql:
    def __init__(self, socket):
        self.base = [
            "/usr/bin/mysql",
            "--batch",
            "--raw",
            "--skip-column-names",
            "--default-character-set=utf8mb4",
            "--protocol=socket",
            "--socket",
            str(socket),
            "--user",
            "root",
        ]

    def query(self, sql, database=DATABASE):
        command = list(self.base)
        if database:
            command.append(database)
        command += ["--execute", sql]
        result = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=3600,
            check=False,
        )
        if result.returncode != 0:
            raise RuntimeError(
                "isolated mysql query failed with exit {} and stderr SHA-256 {}".format(
                    result.returncode,
                    sha256_bytes(result.stderr.encode("utf-8", "replace")),
                )
            )
        return result.stdout.rstrip("\n")


def metadata_rows(mysql):
    # This deliberately duplicates the producer algorithm: no producer module or
    # producer-reported success value is imported or trusted.
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
        rows.extend(row for row in mysql.query(query).splitlines() if row)
    return sorted(rows)


def table_names(mysql):
    names = [
        name
        for name in mysql.query(
            "SELECT table_name FROM information_schema.tables "
            "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
            "ORDER BY BINARY table_name"
        ).splitlines()
        if name
    ]
    if not names or any("`" in name or "\n" in name for name in names):
        raise RuntimeError("restored table inventory is invalid")
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
    rows = sorted(
        row
        for row in mysql.query(";\n".join(statements) + ";").splitlines()
        if row
    )
    if len(rows) != len(names):
        raise RuntimeError("restored row-count manifest is incomplete")
    return {
        "totalRows": sum(int(row.rsplit("|", 1)[1]) for row in rows),
        "tableRowCountsSha256": sha256_bytes(("\n".join(rows) + "\n").encode()),
    }


def collect_facts(mysql):
    identity = mysql.query("SELECT @@version,@@version_comment").split("\t")
    counts = mysql.query(
        """SELECT SUM(table_type='BASE TABLE'),SUM(table_type='VIEW'),
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
    return identity, facts


def production_pid():
    output = subprocess.run(
        ["systemctl", "show", "-p", "MainPID", "--value", "mysqld.service"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()
    if not output.isdigit() or int(output) < 2:
        # The package can expose mysql.service instead of mysqld.service.
        output = subprocess.run(
            ["systemctl", "show", "-p", "MainPID", "--value", "mysql.service"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
        ).stdout.strip()
    if not output.isdigit() or int(output) < 2:
        raise RuntimeError("production mysqld PID is unavailable")
    return int(output)


def make_owned_directory(path, uid, gid, mode):
    path.mkdir(parents=True, exist_ok=False, mode=mode)
    os.chown(path, uid, gid)
    os.chmod(path, mode)


def ensure_root_directory(path):
    if path.exists() or path.is_symlink():
        status = path.lstat()
        if (
            path.is_symlink()
            or not path.is_dir()
            or status.st_uid != 0
            or status.st_gid != 0
            or path.resolve() != path
        ):
            raise RuntimeError("restore root custody is invalid: " + str(path))
    else:
        path.mkdir(mode=0o711, parents=False)
    os.chmod(path, 0o711)


def safe_remove_tree(path, expected_parent):
    if not path.exists():
        return
    if path.is_symlink() or path.resolve().parent != expected_parent:
        raise RuntimeError("refusing cleanup outside exact restore run root")
    shutil.rmtree(path)


def recover_stale_isolated_state(data_run, runtime_run):
    if not data_run.exists() and not runtime_run.exists():
        return
    for candidate, parent in (
        (data_run, DATA_ROOT),
        (runtime_run, RUNTIME_ROOT),
    ):
        if candidate.exists() and (
            candidate.is_symlink()
            or not candidate.is_dir()
            or candidate.resolve().parent != parent
        ):
            raise RuntimeError("stale isolated path custody is invalid")
    data_directory = data_run / "data"
    socket = runtime_run / "mysql.sock"
    pid_file = runtime_run / "mysqld.pid"
    matching_pids = []
    for process_directory in pathlib.Path("/proc").iterdir():
        if not process_directory.name.isdigit():
            continue
        command_line_path = process_directory / "cmdline"
        try:
            command_line = command_line_path.read_bytes().split(b"\0")
        except (FileNotFoundError, PermissionError, ProcessLookupError):
            continue
        arguments = [part.decode("utf-8", "replace") for part in command_line if part]
        if (
            "--datadir=" + str(data_directory) in arguments
            or "--socket=" + str(socket) in arguments
        ):
            matching_pids.append((int(process_directory.name), arguments))
    if len(matching_pids) > 1:
        raise RuntimeError("multiple stale isolated mysqld processes found")
    if matching_pids:
        pid, arguments = matching_pids[0]
        if (
            pid == production_pid()
            or "--datadir=" + str(data_directory) not in arguments
            or "--socket=" + str(socket) not in arguments
            or "--pid-file=" + str(pid_file) not in arguments
            or pathlib.Path("/proc", str(pid), "exe").resolve()
            != MYSQLD.resolve()
        ):
            raise RuntimeError("stale process is not the exact isolated mysqld")
        if socket.exists():
            subprocess.run(
                [
                    "/usr/bin/mysqladmin",
                    "--protocol=socket",
                    "--socket",
                    str(socket),
                    "--user",
                    "root",
                    "shutdown",
                ],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=30,
                check=False,
            )
        deadline = time.monotonic() + 30
        while pathlib.Path("/proc", str(pid)).exists() and time.monotonic() < deadline:
            time.sleep(0.25)
        if pathlib.Path("/proc", str(pid)).exists():
            os.kill(pid, signal.SIGTERM)
            deadline = time.monotonic() + 20
            while (
                pathlib.Path("/proc", str(pid)).exists()
                and time.monotonic() < deadline
            ):
                time.sleep(0.25)
        if pathlib.Path("/proc", str(pid)).exists():
            raise RuntimeError("stale isolated mysqld did not stop after SIGTERM")
    safe_remove_tree(runtime_run, RUNTIME_ROOT)
    safe_remove_tree(data_run, DATA_ROOT)
    fsync_directory(RUNTIME_ROOT)
    fsync_directory(DATA_ROOT)


def quarantine_unbundled_restore(run_directory, args):
    receipt_path = run_directory / "restore-receipt.json"
    if not receipt_path.exists() and not receipt_path.is_symlink():
        return
    validate_regular_file(receipt_path, run_directory)
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    if (
        receipt.get("schema") != RESTORE_SCHEMA
        or receipt.get("runId") != args.run_id
        or receipt.get("sourceCommit") != args.source_commit
        or receipt.get("approvalReceiptSha256") != args.approval_sha
        or receipt.get("backupWorkerSha256") != args.worker_sha
        or receipt.get("verifierSha256") != args.verifier_sha
        or receipt.get("isolatedDataRemoved") is not True
    ):
        raise RuntimeError("unbundled restore receipt provenance is invalid")
    artifacts = [receipt_path]
    for path_field, digest_field, filename in (
        ("restoreLogPath", "restoreLogSha256", "restore.log"),
        (
            "isolationEvidencePath",
            "isolationEvidenceSha256",
            "isolation-evidence.json",
        ),
        ("mysqlcheckPath", "mysqlcheckSha256", "mysqlcheck.log"),
    ):
        artifact = run_directory / filename
        validate_regular_file(artifact, run_directory)
        if (
            receipt.get(path_field) != str(artifact)
            or receipt.get(digest_field) != sha256_file(artifact)
        ):
            raise RuntimeError("unbundled restore artifact binding is invalid")
        artifacts.append(artifact)
    suffix = sha256_file(receipt_path)[:16]
    for artifact in artifacts:
        digest = sha256_file(artifact)
        quarantined = artifact.with_name(
            "abandoned-" + artifact.name + "." + suffix
        )
        if quarantined.exists() or quarantined.is_symlink():
            if (
                quarantined.is_symlink()
                or not quarantined.is_file()
                or sha256_file(quarantined) != digest
            ):
                raise RuntimeError("unbundled restore quarantine collision")
            artifact.unlink()
        else:
            os.replace(artifact, quarantined)
    fsync_directory(run_directory)


def wait_for_socket(socket, process):
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("isolated mysqld exited before readiness")
        result = subprocess.run(
            [
                "/usr/bin/mysqladmin",
                "--protocol=socket",
                "--socket",
                str(socket),
                "--user",
                "root",
                "ping",
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        if result.returncode == 0:
            return
        time.sleep(0.5)
    raise RuntimeError("isolated mysqld readiness timeout")


def restore_encrypted_dump(backup_path, socket):
    plaintext_digest = hashlib.sha256()
    decrypt_error = tempfile.TemporaryFile()
    restore_output = tempfile.TemporaryFile()
    restore_error = tempfile.TemporaryFile()
    decrypt = subprocess.Popen(
        [
            "/usr/bin/gpg",
            "--batch",
            "--yes",
            "--pinentry-mode",
            "loopback",
            "--passphrase-file",
            str(BACKUP_KEY_PATH),
            "--decrypt",
            str(backup_path),
        ],
        stdout=subprocess.PIPE,
        stderr=decrypt_error,
    )
    restore = subprocess.Popen(
        [
            "/usr/bin/mysql",
            "--protocol=socket",
            "--socket",
            str(socket),
            "--user",
            "root",
            "--default-character-set=utf8mb4",
        ],
        stdin=subprocess.PIPE,
        stdout=restore_output,
        stderr=restore_error,
    )
    pump_error = []

    def pump_plaintext():
        try:
            while True:
                chunk = decrypt.stdout.read(1024 * 1024)
                if not chunk:
                    break
                plaintext_digest.update(chunk)
                restore.stdin.write(chunk)
        except Exception as error:
            pump_error.append(error)
            if decrypt.poll() is None:
                decrypt.terminate()
        finally:
            try:
                restore.stdin.close()
            except BrokenPipeError:
                pass

    pump = threading.Thread(target=pump_plaintext, daemon=True)
    pump.start()
    try:
        decrypt_code = decrypt.wait(timeout=7200)
        restore_code = restore.wait(timeout=7200)
        pump.join(timeout=30)
        if pump.is_alive():
            raise RuntimeError("restore plaintext pump did not terminate")
    finally:
        if decrypt.poll() is None:
            decrypt.kill()
        if restore.poll() is None:
            restore.kill()
        pump.join(timeout=5)
    decrypt_error.seek(0)
    restore_output.seek(0)
    restore_error.seek(0)
    decrypt_stderr = decrypt_error.read()
    restore_stdout = restore_output.read()
    restore_stderr = restore_error.read()
    decrypt_error.close()
    restore_output.close()
    restore_error.close()
    if pump_error:
        raise RuntimeError("restore plaintext pump failed")
    if decrypt_code != 0:
        raise RuntimeError(
            "backup decryption failed with exit {} and stderr SHA-256 {}".format(
                decrypt_code, sha256_bytes(decrypt_stderr)
            )
        )
    if restore_code != 0:
        raise RuntimeError(
            "isolated restore failed with exit {} and stderr SHA-256 {}".format(
                restore_code, sha256_bytes(restore_stderr)
            )
        )
    return {
        "plaintextSha256": plaintext_digest.hexdigest(),
        "restoreStdoutSha256": sha256_bytes(restore_stdout),
        "restoreStderrSha256": sha256_bytes(restore_stderr),
    }


def assert_no_tcp_listener(pid):
    output = subprocess.run(
        ["/usr/sbin/ss", "-ltnp"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout
    if re.search(r"pid=" + re.escape(str(pid)) + r"(?:,|\))", output):
        raise RuntimeError("isolated mysqld has a TCP listener")


def validate_backup_receipt(args, run_directory):
    key_status = BACKUP_KEY_PATH.stat()
    if (
        not BACKUP_KEY_PATH.is_file()
        or BACKUP_KEY_PATH.is_symlink()
        or key_status.st_uid != 0
        or key_status.st_gid != 0
        or key_status.st_mode & 0o777 != 0o600
        or key_status.st_size < 32
    ):
        raise RuntimeError("backup key custody is invalid")
    receipt_path = run_directory / "backup-receipt.json"
    validate_regular_file(receipt_path, run_directory)
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    expected = {
        "schema": BACKUP_SCHEMA,
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "database": DATABASE,
        "approvalReceiptSha256": args.approval_sha,
        "runnerSha256": args.runner_sha,
        "backupWorkerSha256": args.worker_sha,
        "encryptionContract": "u3w.gnupg-aes256-symmetric.v1",
        "ddlProtectionMode": "PRE_POST_SCHEMA_STABILITY_APPROVED_NO_DDL_WINDOW",
        "sourceSnapshotExactlyMatched": False,
    }
    if any(receipt.get(key) != value for key, value in expected.items()):
        raise RuntimeError("backup receipt identity/provenance is invalid")
    for field in (
        "backupSha256",
        "backupPlaintextSha256",
        "sourceTableRowCountsSha256",
        "sourceJarSha256",
        "encryptionKeyFingerprintSha256",
    ):
        validate_digest(receipt.get(field), field)
    backup_path = run_directory / "fbsir.sql.gpg"
    validate_regular_file(backup_path, run_directory)
    if (
        receipt.get("backupPath") != str(backup_path)
        or receipt.get("backupSha256") != sha256_file(backup_path)
        or receipt.get("backupSizeBytes") != backup_path.stat().st_size
        or receipt.get("encryptionKeyFingerprintSha256")
        != sha256_file(BACKUP_KEY_PATH)
    ):
        raise RuntimeError("encrypted backup artifact binding is invalid")
    return receipt_path, receipt, backup_path


def validate_arguments(args):
    if not RUN_PATTERN.fullmatch(args.run_id):
        raise RuntimeError("invalid run id")
    if not COMMIT_PATTERN.fullmatch(args.source_commit):
        raise RuntimeError("invalid source commit")
    for value in (
        args.approval_sha,
        args.runner_sha,
        args.worker_sha,
        args.verifier_sha,
    ):
        validate_digest(value, "provenance")
    validate_approval(args)


def validate_approval(args):
    if args.approval_sha == "0" * 64 or not args.approval_json_base64:
        raise RuntimeError("restore verifier requires a non-zero approval receipt")
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


def existing_bundle(run_directory, args):
    bundle_path = run_directory / "receipt.json"
    if not bundle_path.exists():
        return None
    validate_regular_file(bundle_path, run_directory)
    bundle = json.loads(bundle_path.read_text(encoding="utf-8"))
    if (
        bundle.get("schema") != BUNDLE_SCHEMA
        or bundle.get("runId") != args.run_id
        or bundle.get("sourceCommit") != args.source_commit
        or bundle.get("verifierSha256") != args.verifier_sha
    ):
        raise RuntimeError("existing bundle provenance is invalid")
    return bundle


def repair_missing_latest(run_directory):
    latest_link = BACKUP_ROOT.parent / "latest"
    if latest_link.exists() or latest_link.is_symlink():
        if not latest_link.is_symlink():
            raise RuntimeError("latest pointer exists but is not a symlink")
        try:
            current_run = latest_link.resolve(strict=True)
            current_run.relative_to(BACKUP_ROOT.resolve(strict=True))
        except (FileNotFoundError, RuntimeError, ValueError) as error:
            raise RuntimeError("latest pointer target is invalid") from error
        if current_run == run_directory.resolve():
            return
        current_bundle_path = current_run / "receipt.json"
        candidate_bundle_path = run_directory / "receipt.json"
        validate_regular_file(current_bundle_path, current_run)
        validate_regular_file(candidate_bundle_path, run_directory)
        current_bundle = json.loads(
            current_bundle_path.read_text(encoding="utf-8")
        )
        candidate_bundle = json.loads(
            candidate_bundle_path.read_text(encoding="utf-8")
        )
        current_time = dt.datetime.fromisoformat(
            current_bundle["generatedAt"].replace("Z", "+00:00")
        )
        candidate_time = dt.datetime.fromisoformat(
            candidate_bundle["generatedAt"].replace("Z", "+00:00")
        )
        if current_time >= candidate_time:
            return
    next_link = BACKUP_ROOT.parent / (".latest-" + run_directory.name)
    if next_link.exists() or next_link.is_symlink():
        next_link.unlink()
    os.symlink(str(run_directory), next_link)
    os.replace(next_link, latest_link)
    fsync_directory(BACKUP_ROOT.parent)


def validate_existing_bundle(
    run_directory,
    args,
    backup_receipt_path,
    backup_receipt,
    backup_path,
    bundle,
):
    expected = {
        "schema": BUNDLE_SCHEMA,
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "database": DATABASE,
        "approvalReceiptSha256": args.approval_sha,
        "runnerSha256": args.runner_sha,
        "backupWorkerSha256": args.worker_sha,
        "verifierSha256": args.verifier_sha,
        "productionBusinessStateChanged": False,
        "backupReceiptPath": str(backup_receipt_path),
        "backupReceiptSha256": sha256_file(backup_receipt_path),
        "backupPath": str(backup_path),
        "backupSha256": sha256_file(backup_path),
        "backupSizeBytes": backup_path.stat().st_size,
    }
    if any(bundle.get(field) != value for field, value in expected.items()):
        raise RuntimeError("existing bundle artifact/provenance binding is invalid")
    restore_receipt_path = run_directory / "restore-receipt.json"
    validate_regular_file(restore_receipt_path, run_directory)
    if (
        bundle.get("restoreReceiptPath") != str(restore_receipt_path)
        or bundle.get("restoreReceiptSha256") != sha256_file(restore_receipt_path)
    ):
        raise RuntimeError("existing restore receipt digest is invalid")
    restore = json.loads(restore_receipt_path.read_text(encoding="utf-8"))
    restore_expected = {
        "schema": RESTORE_SCHEMA,
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "database": DATABASE,
        "sourceBackupPath": str(backup_path),
        "sourceBackupSha256": backup_receipt["backupSha256"],
        "sourceBackupPlaintextSha256": backup_receipt[
            "backupPlaintextSha256"
        ],
        "sourceBackupReceiptSha256": sha256_file(backup_receipt_path),
        "restoredFacts": backup_receipt["sourceFacts"],
        "approvalReceiptSha256": args.approval_sha,
        "backupWorkerSha256": args.worker_sha,
        "verifierSha256": args.verifier_sha,
        "isolatedTarget": True,
        "isolatedNetworkingDisabled": True,
        "isolatedDataRemoved": True,
        "businessDatabaseChanged": False,
        "serviceChanged": False,
        "officialExpertsPackageChanged": False,
    }
    if any(restore.get(field) != value for field, value in restore_expected.items()):
        raise RuntimeError("existing restore receipt evidence is invalid")
    if (
        not isinstance(restore.get("restoredTotalRows"), int)
        or restore["restoredTotalRows"] < 1
        or not SHA_PATTERN.fullmatch(
            str(restore.get("restoredTableRowCountsSha256") or "")
        )
    ):
        raise RuntimeError("existing restored row manifest is invalid")
    for field, filename, digest_field in (
        ("restoreLogPath", "restore.log", "restoreLogSha256"),
        (
            "isolationEvidencePath",
            "isolation-evidence.json",
            "isolationEvidenceSha256",
        ),
        ("mysqlcheckPath", "mysqlcheck.log", "mysqlcheckSha256"),
    ):
        artifact = run_directory / filename
        validate_regular_file(artifact, run_directory)
        if (
            restore.get(field) != str(artifact)
            or restore.get(digest_field) != sha256_file(artifact)
        ):
            raise RuntimeError("existing restore evidence artifact mismatch")
    evidence = json.loads(
        (run_directory / "isolation-evidence.json").read_text(encoding="utf-8")
    )
    runtime = evidence.get("runtime") or {}
    if (
        evidence.get("schema") != "fbsir.u3wIsolatedMysqlEvidence.v1"
        or evidence.get("runId") != args.run_id
        or evidence.get("sourceCommit") != args.source_commit
        or evidence.get("productionMysqldPidBefore")
        != evidence.get("productionMysqldPidAfter")
        or any(
            evidence.get(field) is not True
            for field in (
                "isolatedProcessExited",
                "isolatedSocketRemoved",
                "isolatedPidFileRemoved",
                "isolatedDatadirRemoved",
                "isolatedRuntimeDirectoryRemoved",
            )
        )
        or runtime.get("skipNetworking") is not True
        or runtime.get("tcpListenerAbsent") is not True
        or runtime.get("logBin") is not False
        or runtime.get("eventScheduler") != "OFF"
        or runtime.get("binarySha256") != sha256_file(MYSQLD)
    ):
        raise RuntimeError("existing isolation evidence is invalid")
    if DATA_ROOT.joinpath(args.run_id).exists() or RUNTIME_ROOT.joinpath(
        args.run_id
    ).exists():
        raise RuntimeError("existing bundle claims cleanup but isolated state remains")
    repair_missing_latest(run_directory)
    return bundle


def run_verification(args):
    if os.geteuid() != 0:
        raise RuntimeError("verifier must run as root")
    run_directory = BACKUP_ROOT / args.run_id
    if (
        run_directory.is_symlink()
        or not run_directory.is_dir()
        or run_directory.resolve().parent != BACKUP_ROOT
    ):
        raise RuntimeError("backup run directory is invalid")
    receipt_path, backup_receipt, backup_path = validate_backup_receipt(
        args, run_directory
    )
    prior = existing_bundle(run_directory, args)
    if prior is not None:
        return validate_existing_bundle(
            run_directory,
            args,
            receipt_path,
            backup_receipt,
            backup_path,
            prior,
        )
    restore_receipt_path = run_directory / "restore-receipt.json"
    quarantine_unbundled_restore(run_directory, args)
    mysql_user = pwd.getpwnam("mysql")
    enforcing = subprocess.run(
        ["getenforce"],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        check=False,
    )
    if enforcing.returncode == 0 and enforcing.stdout.strip() == "Enforcing":
        raise RuntimeError("SELinux enforcing restore policy has not been proven")
    ensure_root_directory(DATA_ROOT)
    ensure_root_directory(RUNTIME_ROOT)
    data_run = DATA_ROOT / args.run_id
    runtime_run = RUNTIME_ROOT / args.run_id
    recover_stale_isolated_state(data_run, runtime_run)
    data_directory = data_run / "data"
    socket = runtime_run / "mysql.sock"
    pid_file = runtime_run / "mysqld.pid"
    error_log = runtime_run / "mysqld.log"
    process = None
    started_at = utc_now()
    try:
        make_owned_directory(
            data_run, mysql_user.pw_uid, mysql_user.pw_gid, 0o700
        )
        make_owned_directory(
            data_directory, mysql_user.pw_uid, mysql_user.pw_gid, 0o700
        )
        make_owned_directory(
            runtime_run, mysql_user.pw_uid, mysql_user.pw_gid, 0o700
        )
        production_pid_before = production_pid()
    except Exception:
        safe_remove_tree(runtime_run, RUNTIME_ROOT)
        safe_remove_tree(data_run, DATA_ROOT)
        raise
    isolation_observation = {}
    restore_result = {}
    restored_facts = None
    restored_manifest = None
    mysqlcheck_payload = b""
    try:
        initialize = subprocess.run(
            [
                str(MYSQLD),
                "--no-defaults",
                "--initialize-insecure",
                "--user=mysql",
                "--datadir=" + str(data_directory),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=300,
            check=False,
        )
        if initialize.returncode != 0:
            raise RuntimeError(
                "isolated initialize failed with exit {} and stderr SHA-256 {}".format(
                    initialize.returncode, sha256_bytes(initialize.stderr)
                )
            )
        command = [
            str(MYSQLD),
            "--no-defaults",
            "--user=mysql",
            "--datadir=" + str(data_directory),
            "--socket=" + str(socket),
            "--pid-file=" + str(pid_file),
            "--log-error=" + str(error_log),
            "--skip-networking=ON",
            "--mysqlx=OFF",
            "--skip-log-bin",
            "--event-scheduler=OFF",
            "--local-infile=OFF",
            "--secure-file-priv=NULL",
            "--innodb-buffer-pool-size=128M",
            "--max-connections=20",
            "--max-allowed-packet=256M",
        ]
        process = subprocess.Popen(
            command,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        wait_for_socket(socket, process)
        mysql = SocketMysql(socket)
        variables = mysql.query(
            """SELECT @@datadir,@@socket,@@server_uuid,@@skip_networking,
            @@version,@@version_comment,@@log_bin,@@event_scheduler"""
            ,
            database=None,
        ).split("\t")
        if (
            len(variables) != 8
            or pathlib.Path(variables[0]).resolve() != data_directory.resolve()
            or pathlib.Path(variables[1]).resolve() != socket.resolve()
            or variables[3] != "1"
            or variables[6] != "0"
            or variables[7] != "OFF"
            or variables[4] != backup_receipt["serverVersion"]
            or variables[5] != backup_receipt["serverVersionComment"]
        ):
            raise RuntimeError("isolated runtime variables violate the contract")
        command_line = pathlib.Path("/proc") / str(process.pid) / "cmdline"
        if not command_line.is_file():
            raise RuntimeError("isolated PID evidence is unavailable")
        assert_no_tcp_listener(process.pid)
        isolation_observation = {
            "pid": process.pid,
            "binarySha256": sha256_file(MYSQLD),
            "commandLineSha256": sha256_file(command_line),
            "datadir": variables[0],
            "socket": variables[1],
            "serverUuid": variables[2],
            "skipNetworking": variables[3] == "1",
            "version": variables[4],
            "versionComment": variables[5],
            "logBin": variables[6] == "1",
            "eventScheduler": variables[7],
            "tcpListenerAbsent": True,
        }
        restore_result = restore_encrypted_dump(backup_path, socket)
        if (
            restore_result["plaintextSha256"]
            != backup_receipt["backupPlaintextSha256"]
        ):
            raise RuntimeError("decrypted backup plaintext digest mismatch")
        identity, restored_facts = collect_facts(mysql)
        restored_manifest = row_manifest(mysql, table_names(mysql))
        if identity != [
            backup_receipt["serverVersion"],
            backup_receipt["serverVersionComment"],
        ]:
            raise RuntimeError("restored server identity mismatch")
        if restored_facts != backup_receipt["sourceFacts"]:
            raise RuntimeError("restored schema facts do not match source facts")
        if restored_manifest["totalRows"] < 1:
            raise RuntimeError("restored database unexpectedly contains no rows")
        mysqlcheck = subprocess.run(
            [
                "/usr/bin/mysqlcheck",
                "--protocol=socket",
                "--socket",
                str(socket),
                "--user",
                "root",
                "--check",
                "--databases",
                DATABASE,
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=3600,
            check=False,
        )
        mysqlcheck_payload = mysqlcheck.stdout + mysqlcheck.stderr
        if mysqlcheck.returncode != 0 or b"\tOK" not in mysqlcheck.stdout:
            raise RuntimeError("mysqlcheck did not prove restored tables healthy")
        shutdown = subprocess.run(
            [
                "/usr/bin/mysqladmin",
                "--protocol=socket",
                "--socket",
                str(socket),
                "--user",
                "root",
                "shutdown",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=90,
            check=False,
        )
        if shutdown.returncode != 0:
            raise RuntimeError("isolated mysqld shutdown failed")
        process.wait(timeout=90)
        process = None
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=20)
        log_payload = error_log.read_bytes() if error_log.is_file() else b""
        safe_remove_tree(runtime_run, RUNTIME_ROOT)
        safe_remove_tree(data_run, DATA_ROOT)
    production_pid_after = production_pid()
    if (
        production_pid_after != production_pid_before
        or data_run.exists()
        or runtime_run.exists()
        or socket.exists()
        or pid_file.exists()
    ):
        raise RuntimeError("post-cleanup production/isolation invariants failed")
    restore_log_path = run_directory / "restore.log"
    mysqlcheck_path = run_directory / "mysqlcheck.log"
    atomic_bytes(restore_log_path, log_payload)
    atomic_bytes(mysqlcheck_path, mysqlcheck_payload)
    evidence_path = run_directory / "isolation-evidence.json"
    evidence = {
        "schema": "fbsir.u3wIsolatedMysqlEvidence.v1",
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "observedAt": utc_now(),
        "productionMysqldPidBefore": production_pid_before,
        "productionMysqldPidAfter": production_pid_after,
        "runtime": isolation_observation,
        "restoreStdoutSha256": restore_result["restoreStdoutSha256"],
        "restoreStderrSha256": restore_result["restoreStderrSha256"],
        "isolatedProcessExited": True,
        "isolatedSocketRemoved": True,
        "isolatedPidFileRemoved": True,
        "isolatedDatadirRemoved": True,
        "isolatedRuntimeDirectoryRemoved": True,
    }
    atomic_json(evidence_path, evidence)
    backup_receipt_sha = sha256_file(receipt_path)
    restore_receipt = {
        "schema": RESTORE_SCHEMA,
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "database": DATABASE,
        "sourceBackupPath": str(backup_path),
        "sourceBackupSha256": backup_receipt["backupSha256"],
        "sourceBackupPlaintextSha256": backup_receipt["backupPlaintextSha256"],
        "sourceBackupReceiptSha256": backup_receipt_sha,
        "startedAt": started_at,
        "completedAt": utc_now(),
        "isolatedTarget": True,
        "isolatedNetworkingDisabled": True,
        "isolatedDataRemoved": True,
        "serverVersion": backup_receipt["serverVersion"],
        "serverVersionComment": backup_receipt["serverVersionComment"],
        "restoredFacts": restored_facts,
        "restoredTotalRows": restored_manifest["totalRows"],
        "restoredTableRowCountsSha256": restored_manifest[
            "tableRowCountsSha256"
        ],
        "restoreLogPath": str(restore_log_path),
        "restoreLogSha256": sha256_file(restore_log_path),
        "isolationEvidencePath": str(evidence_path),
        "isolationEvidenceSha256": sha256_file(evidence_path),
        "mysqlcheckPath": str(mysqlcheck_path),
        "mysqlcheckSha256": sha256_file(mysqlcheck_path),
        "approvalReceiptSha256": args.approval_sha,
        "backupWorkerSha256": args.worker_sha,
        "verifierSha256": args.verifier_sha,
        "businessDatabaseChanged": False,
        "serviceChanged": False,
        "officialExpertsPackageChanged": False,
    }
    atomic_json(restore_receipt_path, restore_receipt)
    bundle = {
        "schema": BUNDLE_SCHEMA,
        "runId": args.run_id,
        "sourceCommit": args.source_commit,
        "targetHost": TARGET_HOST,
        "database": DATABASE,
        "generatedAt": utc_now(),
        "backupReceiptPath": str(receipt_path),
        "backupReceiptSha256": backup_receipt_sha,
        "restoreReceiptPath": str(restore_receipt_path),
        "restoreReceiptSha256": sha256_file(restore_receipt_path),
        "backupPath": str(backup_path),
        "backupSha256": backup_receipt["backupSha256"],
        "backupSizeBytes": backup_receipt["backupSizeBytes"],
        "approvalReceiptSha256": args.approval_sha,
        "runnerSha256": args.runner_sha,
        "backupWorkerSha256": args.worker_sha,
        "verifierSha256": args.verifier_sha,
        "productionBusinessStateChanged": False,
    }
    bundle_path = run_directory / "receipt.json"
    atomic_json(bundle_path, bundle)
    latest_link = BACKUP_ROOT.parent / "latest"
    next_link = BACKUP_ROOT.parent / (".latest-" + args.run_id)
    if next_link.exists() or next_link.is_symlink():
        next_link.unlink()
    os.symlink(str(run_directory), next_link)
    os.replace(next_link, latest_link)
    fsync_directory(BACKUP_ROOT.parent)
    return bundle


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--approval-sha", required=True)
    parser.add_argument("--approval-json-base64", required=True)
    parser.add_argument("--runner-sha", required=True)
    parser.add_argument("--worker-sha", required=True)
    parser.add_argument("--verifier-sha", required=True)
    args = parser.parse_args()
    validate_arguments(args)
    lock_path = HOST_CHANGE_LOCK
    descriptor = os.open(lock_path, os.O_RDWR | os.O_CREAT, 0o600)
    try:
        fcntl.flock(descriptor, fcntl.LOCK_EX)
        print(canonical_json(run_verification(args)))
    finally:
        fcntl.flock(descriptor, fcntl.LOCK_UN)
        os.close(descriptor)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(
            canonical_json(
                {
                    "schema": "fbsir.u3wIsolatedRestoreVerifierFailure.v1",
                    "errorType": type(error).__name__,
                    "message": str(error)[:600],
                }
            ),
            file=sys.stderr,
        )
        raise SystemExit(1)
