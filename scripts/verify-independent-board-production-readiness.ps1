[CmdletBinding()]
param(
    [string]$SshKeyPath = $(if ($env:U3W_SSH_KEY_PATH) { $env:U3W_SSH_KEY_PATH } else { Join-Path $env:USERPROFILE '.ssh\id_ed25519_api2' }),
    [string]$KnownHostsPath = $(Join-Path $env:TEMP 'u3w-production-readiness-known-hosts'),
    [string]$ExpectedBackupReceiptSha256,
    [string]$ExpectedDeploymentReceiptSha256,
    [string]$ExpectedLegacyBaselineReceiptDigest,
    [string]$ExpectedCommit,
    [string]$OutputPath,
    [ValidateSet('PREPARED_FOR_STAGE', 'STAGED_FOR_SWITCH', 'DEPLOYED_DEFAULT_OFF')]
    [string]$RequiredStage,
    [switch]$RequireReady
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$SshTarget = 'root@api2.u3w.com'
$ExpectedPublicKeyFingerprint = 'SHA256:oCIzbO9W94qDBeS9MtetLCW0CjYMkyZqT6QkgcLkXk0'
$ExpectedRemoteHostKeyFingerprint = 'SHA256:GkS/HJpLm48N+KaMV/WEVSBHnI8PKJsU+6ycAsn+mBA'
$ServiceUnit = 'fbsir-admin.service'
$DatabaseBackupReceiptPath = '/opt/fbsir/admin/backups/latest/receipt.json'
$DeploymentReceiptPath = '/opt/fbsir/admin/releases/latest-receipt.json'
if (-not $OutputPath) {
    $OutputPath = Join-Path $RepoRoot 'reports\independent-board\w1a-production-readiness-latest.json'
}

function Resolve-NodeExecutable {
    $candidates = @(
        $env:U3W_NODE_EXE,
        $env:CODEX_NODE_EXE,
        (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe')
    ) | Where-Object { $_ -and $_.Trim().Length -gt 0 }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    $command = Get-Command node -ErrorAction SilentlyContinue
    if ($command -and $command.Source) {
        return $command.Source
    }
    throw 'node executable not found'
}

function Assert-SafeRemoteParameters {
    foreach ($anchor in @(
            $ExpectedBackupReceiptSha256,
            $ExpectedDeploymentReceiptSha256,
            $ExpectedLegacyBaselineReceiptDigest)) {
        if ($anchor -and $anchor -notmatch '^[0-9a-fA-F]{64}$') {
            throw 'receipt anchors must be 64-hex SHA-256 values'
        }
    }
    $effectiveStage = if ($RequiredStage) {
        $RequiredStage
    } elseif ($RequireReady) {
        'PREPARED_FOR_STAGE'
    } else {
        $null
    }
    if ($effectiveStage -and -not $ExpectedBackupReceiptSha256) {
        throw 'PREPARED_FOR_STAGE requires the backup out-of-band SHA-256 anchor'
    }
    if ($effectiveStage -and -not $ExpectedCommit) {
        throw "$effectiveStage requires an explicit 40-hex ExpectedCommit"
    }
    if ($effectiveStage -in @('STAGED_FOR_SWITCH', 'DEPLOYED_DEFAULT_OFF') -and
        -not $ExpectedDeploymentReceiptSha256) {
        throw "$effectiveStage requires the deployment out-of-band SHA-256 anchor"
    }
}

function Assert-PrivateKey {
    if (-not (Test-Path -LiteralPath $SshKeyPath -PathType Leaf)) {
        throw "SSH private key missing: $SshKeyPath"
    }
    $publicKeyPath = "$SshKeyPath.pub"
    if (-not (Test-Path -LiteralPath $publicKeyPath -PathType Leaf)) {
        throw "SSH public key missing; cannot prove private-key fingerprint: $publicKeyPath"
    }
    $fingerprint = & ssh-keygen.exe -lf $publicKeyPath -E sha256 2>$null
    if ($LASTEXITCODE -ne 0 -or $fingerprint -notlike "*$ExpectedPublicKeyFingerprint*") {
        throw "SSH public key fingerprint mismatch; expected $ExpectedPublicKeyFingerprint"
    }
}

function Ensure-KnownHosts {
    $knownHostsDirectory = Split-Path -Parent $KnownHostsPath
    if ($knownHostsDirectory -and -not (Test-Path -LiteralPath $knownHostsDirectory)) {
        New-Item -ItemType Directory -Path $knownHostsDirectory | Out-Null
    }
    $matchesFingerprint = {
        param([string]$Path, [string]$HostName)
        $matchingLines = & ssh-keygen.exe -F $HostName -f $Path 2>$null |
            Where-Object { $_ -and -not $_.StartsWith('#') }
        if ($LASTEXITCODE -ne 0 -or @($matchingLines).Count -eq 0) {
            return $false
        }
        $fingerprints = $matchingLines |
            & ssh-keygen.exe -lf - -E sha256 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "host fingerprint inspection failed: $Path"
        }
        return @($fingerprints).Count -gt 0 -and
            @($fingerprints | Where-Object {
                    $_ -notmatch [regex]::Escape($ExpectedRemoteHostKeyFingerprint)
                }).Count -eq 0
    }
    $hostName = ($SshTarget -split '@', 2)[1]
    if (Test-Path -LiteralPath $KnownHostsPath -PathType Leaf) {
        if (-not (& $matchesFingerprint $KnownHostsPath $hostName)) {
            throw "remote host key fingerprint mismatch; expected $ExpectedRemoteHostKeyFingerprint"
        }
        return
    }
    $scanPath = "$KnownHostsPath.scan-$PID"
    try {
        & ssh-keyscan.exe -T 10 -t ed25519 $hostName |
            Set-Content -LiteralPath $scanPath -NoNewline -Encoding ascii
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $scanPath -PathType Leaf)) {
            throw "ssh-keyscan failed for $hostName"
        }
        if (-not (& $matchesFingerprint $scanPath $hostName)) {
            throw "remote host key fingerprint mismatch; expected $ExpectedRemoteHostKeyFingerprint"
        }
        Move-Item -LiteralPath $scanPath -Destination $KnownHostsPath -Force
    }
    finally {
        Remove-Item -LiteralPath $scanPath -Force -ErrorAction SilentlyContinue
    }
}

function Get-GitState {
    $head = (& git -C $RepoRoot rev-parse HEAD).Trim().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-f]{40}$') {
        throw 'unable to resolve local git HEAD'
    }
    if (-not $ExpectedCommit) {
        $script:ExpectedCommit = $head
    }
    $expected = $ExpectedCommit.Trim().ToLowerInvariant()
    if ($expected -notmatch '^[0-9a-f]{40}$') {
        throw 'ExpectedCommit must be a 40-hex commit'
    }
    $status = & git -C $RepoRoot status --porcelain=v1
    if ($LASTEXITCODE -ne 0) {
        throw 'git status failed'
    }
    $migrationPath = Join-Path $RepoRoot 'sql\update_20260723_independent_board_attribution_v1.sql'
    $migrationSha256 = (
        Get-FileHash -LiteralPath $migrationPath -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $compatibilityReceiptPaths = @(
        'reports\independent-board\w1a-attribution-v1-dual-mysql-latest.json',
        'reports\independent-board\w1a-attribution-v1-mysql-8.0.45-latest.json'
    )
    $verifiedVersions = [System.Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal)
    $compatibilityReceipts = [System.Collections.Generic.List[object]]::new()
    foreach ($relativePath in $compatibilityReceiptPaths) {
        $receiptPath = Join-Path $RepoRoot $relativePath
        if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) {
            continue
        }
        $receipt = Get-Content -LiteralPath $receiptPath -Raw -Encoding UTF8 |
            ConvertFrom-Json
        if ($receipt.schema -cne 'fbsir.independent-board.attribution-v1-dual-mysql-it/v1' -or
            $receipt.status -cne 'PASS' -or
            $receipt.migration -cne 'public_init_043' -or
            $receipt.migrationSha256 -cne $migrationSha256 -or
            $receipt.officialIdentity.productId -cne 'fbsir-eight-seat-board' -or
            $receipt.officialIdentity.listedManifestVersion -cne '26.7.21') {
            throw "MySQL compatibility receipt is invalid: $relativePath"
        }
        foreach ($result in @($receipt.results)) {
            if ($result.version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$' -or
                $result.exactShape -cne "$($result.version)|2|2|1|1" -or
                $result.migrationRerun -cne 'PASS' -or
                $result.append -cne 'PASS' -or
                $result.aggregate -cne '1|1|1|0' -or
                $result.updateRejected -cne 'PASS' -or
                $result.deleteRejected -cne 'PASS' -or
                [int]$result.authoritativeProductCredit -ne 0 -or
                $result.schemaFingerprintSha256 -cne 'a0507f51960622d49b66c4d8b1b7382dc8bc16a904d577bac1ca942bb8748b28') {
                throw "MySQL compatibility result is invalid: $relativePath"
            }
            $null = $verifiedVersions.Add([string]$result.version)
        }
        $compatibilityReceipts.Add([ordered]@{
                path = $relativePath.Replace('\', '/')
                sha256 = (
                    Get-FileHash -LiteralPath $receiptPath -Algorithm SHA256
                ).Hash.ToLowerInvariant()
            })
    }
    $releasePlanRelativePath =
        'reports\independent-board\w1a-default-off-release-plan-latest.json'
    $releasePlanPath = Join-Path $RepoRoot $releasePlanRelativePath
    $releasePlanVerified = $false
    $releasePlanSourceCommit = $null
    $releaseRunnerContractVersion = $null
    $releasePlanReceiptSha256 = $null
    if (Test-Path -LiteralPath $releasePlanPath -PathType Leaf) {
        $releasePlan = Get-Content -LiteralPath $releasePlanPath -Raw -Encoding UTF8 |
            ConvertFrom-Json
        $runnerPath = Join-Path $RepoRoot 'scripts\deploy-independent-board-default-off.ps1'
        $runnerSha256 = if (Test-Path -LiteralPath $runnerPath -PathType Leaf) {
            (Get-FileHash -LiteralPath $runnerPath -Algorithm SHA256).Hash.ToLowerInvariant()
        } else { $null }
        $requiredModes = @($releasePlan.requiredModes)
        $releasePlanVerified = (
            $releasePlan.schema -ceq 'fbsir.u3wDefaultOffReleasePlan.v1' -and
            $releasePlan.runnerContractVersion -ceq 'fbsir.u3wDefaultOffReleaseRunner.v1' -and
            $releasePlan.mode -ceq 'Plan' -and
            $releasePlan.productionChanged -eq $false -and
            $releasePlan.strictHeadClean -eq $true -and
            $releasePlan.sourceCommit -ceq $head -and
            $releasePlan.expectedSourceCommit -ceq $expected -and
            $releasePlan.runnerSha256 -ceq $runnerSha256 -and
            ($requiredModes -join ',') -ceq 'Build,Plan,Stage,Apply,Rollback,Verify'
        )
        if (-not $releasePlanVerified) {
            throw "default-off release plan receipt is invalid: $releasePlanRelativePath"
        }
        $releasePlanSourceCommit = [string]$releasePlan.sourceCommit
        $releaseRunnerContractVersion = [string]$releasePlan.runnerContractVersion
        $releasePlanReceiptSha256 = (
            Get-FileHash -LiteralPath $releasePlanPath -Algorithm SHA256
        ).Hash.ToLowerInvariant()
    }
    return [ordered]@{
        clean = [string]::IsNullOrWhiteSpace(($status -join "`n"))
        sourceCommit = $head
        expectedSourceCommit = $expected
        w1a043CompatibilityVersions = @($verifiedVersions | Sort-Object)
        w1a043CompatibilityReceipts = @($compatibilityReceipts)
        # Full 035-042 canonical-chain compatibility is intentionally distinct
        # from the standalone 043 compatibility receipts above.
        canonicalBaselineCompatibilityVersions = @('8.0.30', '8.4.8')
        releasePlanVerified = $releasePlanVerified
        releasePlanSourceCommit = $releasePlanSourceCommit
        releaseRunnerContractVersion = $releaseRunnerContractVersion
        releasePlanReceiptPath = if ($releasePlanVerified) {
            $releasePlanRelativePath.Replace('\', '/')
        } else { $null }
        releasePlanReceiptSha256 = $releasePlanReceiptSha256
    }
}

function Invoke-RemoteSnapshot {
    Assert-SafeRemoteParameters
    Assert-PrivateKey
    Ensure-KnownHosts

    $remotePython = @'
import hashlib
import hmac
import base64
import json
import os
import pathlib
import re
import subprocess
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timezone

SERVICE_UNIT = __SERVICE_UNIT__
TARGET_HOST = __TARGET_HOST__
BACKUP_RECEIPT_PATH = __BACKUP_RECEIPT_PATH__
DEPLOYMENT_RECEIPT_PATH = __DEPLOYMENT_RECEIPT_PATH__
EXPECTED_BACKUP_RECEIPT_SHA256 = __EXPECTED_BACKUP_RECEIPT_SHA256__
EXPECTED_DEPLOYMENT_RECEIPT_SHA256 = __EXPECTED_DEPLOYMENT_RECEIPT_SHA256__
EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST = __EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST__
EXPECTED_SOURCE_COMMIT = __EXPECTED_SOURCE_COMMIT__
EXPECTED_BACKUP_RUNNER_SHA256 = __EXPECTED_BACKUP_RUNNER_SHA256__
EXPECTED_BACKUP_WORKER_SHA256 = __EXPECTED_BACKUP_WORKER_SHA256__
EXPECTED_RESTORE_VERIFIER_SHA256 = __EXPECTED_RESTORE_VERIFIER_SHA256__
FLAG_NAMES = [
    "FBSIR_BOARD_ATTRIBUTION_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_CREDIT_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_WRITER_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_INTENT_CLASSIFIER_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_ADMIN_READ_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED",
]

def run(args, env=None):
    return subprocess.check_output(
        args, text=True, stderr=subprocess.DEVNULL, env=env
    ).strip()

def sha256_file(filename):
    digest = hashlib.sha256()
    with open(filename, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

def decode_secret_material(encoded):
    value = str(encoded or "").strip()
    if not value:
        return None
    try:
        if value.startswith("base64:"):
            raw_base64 = value[7:]
            if len(raw_base64) % 4 == 1:
                return None
            padded_base64 = raw_base64 + ("=" * (-len(raw_base64) % 4))
            return base64.b64decode(padded_base64, validate=True)
        if value.startswith("hex:"):
            raw_hex = value[4:]
            if (
                not raw_hex
                or len(raw_hex) % 2 != 0
                or re.fullmatch(r"[0-9a-fA-F]+", raw_hex) is None
            ):
                return None
            return bytes.fromhex(raw_hex)
        if value.startswith("utf8:"):
            value = value[5:]
        return value.encode("utf-8")
    except (ValueError, UnicodeError):
        return None

def parse_env_file(filename):
    values = {}
    for raw in pathlib.Path(filename).read_text(errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            continue
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        values[key] = value
    return values

def http_status(url):
    request = urllib.request.Request(url, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code
    except Exception:
        return None

show = run([
    "systemctl", "show", SERVICE_UNIT,
    "--property=FragmentPath,EnvironmentFiles,ActiveState,SubState,ExecStart"
])
service = {}
for line in show.splitlines():
    key, _, value = line.partition("=")
    service[key] = value

environment_files = []
for match in re.finditer(r"(/[^ ;]+)", service.get("EnvironmentFiles", "")):
    candidate = match.group(1).lstrip("-")
    if pathlib.Path(candidate).is_file():
        environment_files.append(candidate)
environment = {}
for filename in environment_files:
    environment.update(parse_env_file(filename))

exec_start = service.get("ExecStart", "")
jar_match = re.search(r"(/[A-Za-z0-9._/-]+\.jar)", exec_start)
jar_path = jar_match.group(1) if jar_match else None
jar_digest = sha256_file(jar_path) if jar_path and pathlib.Path(jar_path).is_file() else None
attribution_class_count = None
if jar_path and pathlib.Path(jar_path).is_file():
    with zipfile.ZipFile(jar_path) as archive:
        attribution_class_count = sum(
            1 for name in archive.namelist()
            if "/business/board/attribution/" in name and name.endswith(".class")
        )

database = {
    "serverVersion": None,
    "database": None,
    "currentUser": None,
    "totalTableCount": None,
    "migrationTableCount": None,
    "migrationVersions": [],
    "migrationDescriptions": {},
    "boardAttributionTableCount": None,
    "boardAttributionTriggerCount": None,
    "boardAttributionPermissionCount": None,
    "boardAttributionInternalReceiptCount": None,
    "publicInit043Applied": None,
    "w1aSchemaFingerprintSha256": None,
    "schemaBaselineMode": None,
    "legacyBaselineReceiptValid": False,
    "legacyBaselineReceiptAnchorMatched": False,
    # Intentionally false until a controlled runner and this collector
    # independently recompute every receipt field from live artifacts.
    "legacyBaselineLiveFactsMatched": False,
    "legacyBaselineReceiptDigest": None,
    "legacyBaselineSourceCommit": None,
}
jdbc_url = environment.get("FBSIR_MYSQL_URL") or environment.get("WXFBSIR_MYSQL_URL")
mysql_user = environment.get("FBSIR_MYSQL_USERNAME") or environment.get("WXFBSIR_MYSQL_USERNAME")
mysql_password = environment.get("FBSIR_MYSQL_PASSWORD") or environment.get("WXFBSIR_MYSQL_PASSWORD")
if jdbc_url and mysql_user is not None and mysql_password is not None:
    match = re.match(
        r"^jdbc:mysql://(?P<host>[A-Za-z0-9._-]+)(?::(?P<port>[0-9]+))?/(?P<db>[A-Za-z0-9_]+)",
        jdbc_url,
    )
    if not match:
        raise RuntimeError("unsupported JDBC URL shape")
    host = match.group("host")
    port = match.group("port") or "3306"
    database_name = match.group("db")
    mysql_env = os.environ.copy()
    mysql_env["MYSQL_PWD"] = mysql_password

    def query(sql):
        return run([
            "mysql", "--protocol=tcp", "-h", host, "-P", port,
            "-u", mysql_user, "-D", database_name,
            "--batch", "--skip-column-names", "--raw", "-e", sql,
        ], env=mysql_env)

    identity = query("SELECT @@version, DATABASE(), CURRENT_USER()").split("\t")
    sys_menu_table_count = int(query(
        "SELECT COUNT(*) FROM information_schema.tables "
        "WHERE table_schema=DATABASE() AND table_name='sys_menu'"
    ))
    database.update({
        "serverVersion": identity[0],
        "database": identity[1],
        "currentUser": identity[2],
        "totalTableCount": int(query(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema = DATABASE()"
        )),
        "migrationTableCount": int(query(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema = DATABASE() "
            "AND table_name = 'u3w_schema_migration'"
        )),
        "boardAttributionTableCount": int(query(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema = DATABASE() AND table_name IN "
            "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
        )),
        "boardAttributionTriggerCount": int(query(
            "SELECT COUNT(*) FROM information_schema.triggers "
            "WHERE trigger_schema = DATABASE() AND trigger_name IN "
            "('trg_board_attr_event_v1_no_update',"
            "'trg_board_attr_event_v1_no_delete')"
        )),
        "boardAttributionPermissionCount": (
            int(query(
                "SELECT COUNT(*) FROM sys_menu "
                "WHERE BINARY perms=BINARY 'board:attribution:query'"
            )) if sys_menu_table_count == 1 else 0
        ),
        "boardAttributionInternalReceiptCount": 0,
    })
    if database["migrationTableCount"] == 1:
        database["boardAttributionInternalReceiptCount"] = int(query(
            "SELECT COUNT(*) FROM u3w_schema_migration WHERE version="
            "'20260723_independent_board_attribution_v1_043' AND description="
            "'APPLIED:exact WorkBuddy experts 26.7.21 attribution journey "
            "and append-only event ledger'"
        ))
        migration_rows = query(
            "SELECT version, description FROM u3w_schema_migration "
            "WHERE version LIKE 'public_init_0%' ORDER BY version"
        )
        for row in migration_rows.splitlines():
            if not row:
                continue
            version, description = row.split("\t", 1)
            database["migrationVersions"].append(version)
            database["migrationDescriptions"][version] = description
        database["publicInit043Applied"] = (
            "public_init_043" in database["migrationVersions"]
        )
        if database["publicInit043Applied"]:
            fingerprint_rows = query("""
SELECT row_value FROM (
  SELECT CONCAT_WS('|','C',HEX(table_name),LPAD(ordinal_position,3,'0'),
    HEX(column_name),HEX(column_type),is_nullable,
    HEX(COALESCE(column_default,'<NULL>')),HEX(extra),
    HEX(COALESCE(character_set_name,'')),HEX(COALESCE(collation_name,'')),
    HEX(COALESCE(generation_expression,''))) AS row_value
  FROM information_schema.columns
  WHERE table_schema=DATABASE()
    AND table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','I',HEX(table_name),HEX(index_name),non_unique,
    LPAD(seq_in_index,3,'0'),HEX(column_name),
    COALESCE(sub_part,''),HEX(COALESCE(collation,'')),HEX(index_type),
    HEX(nullable))
  FROM information_schema.statistics
  WHERE table_schema=DATABASE()
    AND table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','T',HEX(table_name),HEX(constraint_name),
    HEX(constraint_type))
  FROM information_schema.table_constraints
  WHERE table_schema=DATABASE()
    AND table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','K',HEX(table_name),HEX(constraint_name),
    HEX(column_name),LPAD(ordinal_position,3,'0'),
    HEX(COALESCE(referenced_table_name,'')),
    HEX(COALESCE(referenced_column_name,'')))
  FROM information_schema.key_column_usage
  WHERE table_schema=DATABASE()
    AND table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','H',HEX(tc.table_name),HEX(cc.constraint_name),
    HEX(cc.check_clause))
  FROM information_schema.check_constraints cc
  JOIN information_schema.table_constraints tc
    ON tc.constraint_schema=cc.constraint_schema
   AND tc.constraint_name=cc.constraint_name
   AND tc.constraint_type='CHECK'
  WHERE tc.table_schema=DATABASE()
    AND tc.table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','R',HEX(trigger_name),HEX(event_manipulation),
    HEX(event_object_table),HEX(action_timing),HEX(action_orientation),
    HEX(action_statement))
  FROM information_schema.triggers
  WHERE trigger_schema=DATABASE()
    AND trigger_name IN
      ('trg_board_attr_event_v1_no_update',
       'trg_board_attr_event_v1_no_delete')
) AS fingerprint_rows
ORDER BY BINARY row_value
""")
            database["w1aSchemaFingerprintSha256"] = hashlib.sha256(
                (fingerprint_rows + "\n").encode("utf-8")
            ).hexdigest()
        legacy_table_count = int(query(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema=DATABASE() "
            "AND table_name='u3w_legacy_schema_baseline_receipt_v1'"
        ))
        if legacy_table_count == 1:
            legacy_rows = query("""
SELECT CONCAT_WS('\t',
  baseline_id,baseline_mode,mysql_version,mysql_version_comment,
  fingerprint_algorithm,source_schema_fingerprint,base_table_count,
  view_count,trigger_count,routine_count,event_count,
  prerequisite_shape_sha256,source_jar_sha256,backup_sha256,
  backup_size_bytes,backup_receipt_sha256,restore_receipt_sha256,
  runner_sha256,source_commit,
  DATE_FORMAT(observed_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
  receipt_digest)
FROM u3w_legacy_schema_baseline_receipt_v1
ORDER BY baseline_id
""")
            rows = [row for row in legacy_rows.splitlines() if row]
            if len(rows) == 1:
                parts = rows[0].split("\t")
                if len(parts) == 21:
                    actual_digest = hashlib.sha256(
                        "|".join(parts[:-1]).encode("utf-8")
                    ).hexdigest()
                    migration_receipt_count = int(query(
                        "SELECT COUNT(*) FROM u3w_schema_migration "
                        "WHERE version='legacy_w1a_baseline_20260723_001' "
                        "AND description='APPLIED:Legacy production schema "
                        "adopted for Independent Board W1A v1 only'"
                    ))
                    valid = bool(
                        parts[1] == "LEGACY_ADOPTED_W1A_V1"
                        and parts[2] == database["serverVersion"]
                        and parts[4] == "u3w.mysql-schema-metadata.v1"
                        and all(re.fullmatch(r"[0-9a-f]{64}", value)
                                for value in (
                                    parts[0], parts[5], parts[11], parts[12],
                                    parts[13], parts[15], parts[16], parts[17],
                                    parts[20]))
                        and re.fullmatch(r"[0-9a-f]{40}", parts[18])
                        and actual_digest == parts[20]
                        and migration_receipt_count == 1
                    )
                    database.update({
                        "schemaBaselineMode": (
                            "LEGACY_ADOPTED_W1A_V1" if valid else None
                        ),
                        "legacyBaselineReceiptValid": valid,
                        "legacyBaselineReceiptAnchorMatched": bool(
                            EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST
                            and parts[20]
                                == EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST
                        ),
                        "legacyBaselineReceiptDigest": parts[20],
                        "legacyBaselineSourceCommit": parts[18],
                    })

key_names = sorted(environment.keys())
explicit_false = sorted(
    name for name in FLAG_NAMES
    if environment.get(name, "").strip().lower() == "false"
)
active_event_key_id = environment.get(
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID", ""
).strip()
active_event_key = environment.get(
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY", ""
).strip()
previous_event_key_id = environment.get(
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY_ID", ""
).strip()
previous_event_key = environment.get(
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY", ""
).strip()
active_event_key_pair_present = bool(active_event_key_id and active_event_key)
previous_event_key_pair_complete = bool(
    (not previous_event_key_id and not previous_event_key)
    or (previous_event_key_id and previous_event_key)
)
event_key_count = (
    (1 if active_event_key_pair_present else 0)
    + (1 if previous_event_key_id and previous_event_key else 0)
)
same_binding_value = environment.get(
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET", ""
).strip()
same_binding_present = bool(same_binding_value)
environment_file_custody_secure = bool(environment_files) and all(
    pathlib.Path(filename).is_file()
    and not pathlib.Path(filename).is_symlink()
    and pathlib.Path(filename).stat().st_uid == 0
    and pathlib.Path(filename).stat().st_gid == 0
    and (pathlib.Path(filename).stat().st_mode & 0o777) == 0o600
    for filename in environment_files
)
active_event_material = decode_secret_material(active_event_key)
previous_event_material = decode_secret_material(previous_event_key)
same_binding_material = decode_secret_material(same_binding_value)
key_id_pattern = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}")
resolved_event_material = {}
for key_id, material in (
    (active_event_key_id, active_event_material),
    (previous_event_key_id, previous_event_material),
):
    if key_id and material is not None:
        existing = resolved_event_material.get(key_id)
        if existing is not None and existing != material:
            resolved_event_material = {}
            break
        resolved_event_material[key_id] = material
cryptographic_shape_valid = bool(
    active_event_key_pair_present
    and previous_event_key_pair_complete
    and same_binding_present
    and key_id_pattern.fullmatch(active_event_key_id)
    and (
        not previous_event_key_id
        or key_id_pattern.fullmatch(previous_event_key_id)
    )
    and active_event_material is not None
    and len(active_event_material) >= 32
    and (
        not previous_event_key_id
        or (
            previous_event_material is not None
            and len(previous_event_material) >= 32
        )
    )
    and same_binding_material is not None
    and len(same_binding_material) >= 32
    and resolved_event_material
    and all(
        not hmac.compare_digest(material, same_binding_material)
        for material in resolved_event_material.values()
    )
)

def current_backup_schema_facts():
    if "query" not in globals():
        return None
    metadata_queries = [
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
    metadata_rows = []
    for sql in metadata_queries:
        metadata_rows.extend(row for row in query(sql).splitlines() if row)
    counts = query(
        """SELECT SUM(table_type='BASE TABLE'),SUM(table_type='VIEW'),
        SUM(table_type='BASE TABLE' AND engine<>'InnoDB')
        FROM information_schema.tables WHERE table_schema=DATABASE()"""
    ).split("\t")
    object_counts = query(
        """SELECT
        (SELECT COUNT(*) FROM information_schema.triggers
          WHERE trigger_schema=DATABASE()),
        (SELECT COUNT(*) FROM information_schema.routines
          WHERE routine_schema=DATABASE()),
        (SELECT COUNT(*) FROM information_schema.events
          WHERE event_schema=DATABASE())"""
    ).split("\t")
    root_rows = query(
        """SELECT CONCAT_WS('|',HEX(menu_name),parent_id,HEX(COALESCE(path,'')),
        HEX(COALESCE(component,'')),HEX(COALESCE(perms,'')))
        FROM sys_menu WHERE parent_id=0 AND HEX(menu_name) IN (
          'E78BACE891A3E4BC9A',
          '496E646570656E64656E7420426F617264')
        ORDER BY BINARY menu_name,BINARY path"""
    )
    sys_menu_shape = query(
        """SELECT CONCAT_WS('|',LPAD(ordinal_position,6,'0'),HEX(column_name),
        HEX(column_type),HEX(is_nullable),HEX(COALESCE(column_default,'<NULL>')),
        HEX(extra)) FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='sys_menu'
        ORDER BY ordinal_position"""
    )
    return {
        "fingerprintAlgorithm": "u3w.mysql-schema-metadata.v2",
        "schemaFingerprintSha256": hashlib.sha256(
            ("\n".join(sorted(metadata_rows)) + "\n").encode()
        ).hexdigest(),
        "prerequisiteShapeSha256": hashlib.sha256(
            (sys_menu_shape + "\n--ROOTS--\n" + root_rows + "\n").encode()
        ).hexdigest(),
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

backup = {
    "receiptPath": BACKUP_RECEIPT_PATH,
    "proven": False,
    "receiptAnchorMatched": False,
    "sha256": None,
    "sizeBytes": None,
    "restoreProcedureVerified": False,
    "restoreLiveFactsMatched": False,
}
backup_receipt = pathlib.Path(BACKUP_RECEIPT_PATH)
if backup_receipt.is_file():
    backup_receipt_sha256 = sha256_file(backup_receipt)
    bundle = json.loads(backup_receipt.read_text(encoding="utf-8"))
    run_id = bundle.get("runId")
    run_root = pathlib.Path("/opt/fbsir/admin/backups/w1a")
    run_directory = run_root / str(run_id or "")

    def run_artifact(path_value, filename):
        if not isinstance(path_value, str):
            return None
        candidate = pathlib.Path(path_value)
        try:
            resolved = candidate.resolve(strict=True)
            resolved.relative_to(run_root.resolve(strict=True))
        except (FileNotFoundError, RuntimeError, ValueError):
            return None
        status = resolved.stat()
        if (
            resolved != (run_directory / filename).resolve()
            or not resolved.is_file()
            or resolved.is_symlink()
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_mode & 0o777 != 0o600
            or status.st_nlink != 1
        ):
            return None
        return resolved

    backup_receipt_file = run_artifact(
        bundle.get("backupReceiptPath"), "backup-receipt.json"
    )
    restore_receipt_file = run_artifact(
        bundle.get("restoreReceiptPath"), "restore-receipt.json"
    )
    artifact = run_artifact(bundle.get("backupPath"), "fbsir.sql.gpg")
    restore_verified = False
    actual_digest = None
    actual_size = None
    if backup_receipt_file and restore_receipt_file and artifact:
        source_receipt = json.loads(
            backup_receipt_file.read_text(encoding="utf-8")
        )
        restore_receipt = json.loads(
            restore_receipt_file.read_text(encoding="utf-8")
        )
        evidence_file = run_artifact(
            restore_receipt.get("isolationEvidencePath"),
            "isolation-evidence.json",
        )
        restore_log = run_artifact(
            restore_receipt.get("restoreLogPath"), "restore.log"
        )
        mysqlcheck_log = run_artifact(
            restore_receipt.get("mysqlcheckPath"), "mysqlcheck.log"
        )
        evidence = (
            json.loads(evidence_file.read_text(encoding="utf-8"))
            if evidence_file else {}
        )
        runtime_evidence = evidence.get("runtime") or {}
        actual_digest = sha256_file(artifact)
        actual_size = artifact.stat().st_size
        try:
            generated_at = datetime.fromisoformat(
                str(bundle.get("generatedAt", "")).replace("Z", "+00:00")
            )
            age_seconds = (
                datetime.now(timezone.utc)
                - generated_at.astimezone(timezone.utc)
            ).total_seconds()
            receipt_fresh = 0 <= age_seconds <= 86400
        except Exception:
            receipt_fresh = False
        mysqlcheck_payload = (
            mysqlcheck_log.read_bytes() if mysqlcheck_log else b""
        )
        live_backup_facts = current_backup_schema_facts()
        restore_verified = bool(
            bundle.get("schema")
                == "fbsir.u3wDatabaseBackupRestoreBundleReceipt.v1"
            and re.fullmatch(
                r"w1a-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}",
                str(run_id or ""),
            )
            and bundle.get("sourceCommit") == EXPECTED_SOURCE_COMMIT
            and bundle.get("targetHost") == TARGET_HOST
            and bundle.get("database") == "fbsir"
            and bundle.get("runnerSha256")
                == EXPECTED_BACKUP_RUNNER_SHA256
            and bundle.get("backupWorkerSha256")
                == EXPECTED_BACKUP_WORKER_SHA256
            and bundle.get("verifierSha256")
                == EXPECTED_RESTORE_VERIFIER_SHA256
            and bundle.get("productionBusinessStateChanged") is False
            and bundle.get("backupReceiptSha256")
                == sha256_file(backup_receipt_file)
            and bundle.get("restoreReceiptSha256")
                == sha256_file(restore_receipt_file)
            and bundle.get("backupSha256") == actual_digest
            and bundle.get("backupSizeBytes") == actual_size
            and actual_size > 0
            and source_receipt.get("schema")
                == "fbsir.u3wDatabaseBackupReceipt.v2"
            and source_receipt.get("runId") == run_id
            and source_receipt.get("sourceCommit") == EXPECTED_SOURCE_COMMIT
            and source_receipt.get("runnerSha256")
                == EXPECTED_BACKUP_RUNNER_SHA256
            and source_receipt.get("backupWorkerSha256")
                == EXPECTED_BACKUP_WORKER_SHA256
            and source_receipt.get("sourceJarSha256") == jar_digest
            and source_receipt.get("backupSha256") == actual_digest
            and source_receipt.get("backupSizeBytes") == actual_size
            and source_receipt.get("encryptionContract")
                == "u3w.gnupg-aes256-symmetric.v1"
            and source_receipt.get("ddlProtectionMode")
                == "PRE_POST_SCHEMA_STABILITY_APPROVED_NO_DDL_WINDOW"
            and restore_receipt.get("schema")
                == "fbsir.u3wDatabaseRestoreRehearsalReceipt.v2"
            and restore_receipt.get("runId") == run_id
            and restore_receipt.get("sourceCommit") == EXPECTED_SOURCE_COMMIT
            and restore_receipt.get("sourceBackupSha256") == actual_digest
            and restore_receipt.get("sourceBackupReceiptSha256")
                == sha256_file(backup_receipt_file)
            and restore_receipt.get("restoredFacts")
                == source_receipt.get("sourceFacts")
            and live_backup_facts == restore_receipt.get("restoredFacts")
            and source_receipt.get("sourceSnapshotExactlyMatched") is False
            and isinstance(restore_receipt.get("restoredTotalRows"), int)
            and restore_receipt.get("restoredTotalRows") > 0
            and re.fullmatch(
                r"[0-9a-f]{64}",
                str(
                    restore_receipt.get(
                        "restoredTableRowCountsSha256"
                    ) or ""
                ),
            ) is not None
            and restore_receipt.get("isolatedTarget") is True
            and restore_receipt.get("isolatedNetworkingDisabled") is True
            and restore_receipt.get("isolatedDataRemoved") is True
            and restore_receipt.get("verifierSha256")
                == EXPECTED_RESTORE_VERIFIER_SHA256
            and evidence_file
            and restore_receipt.get("isolationEvidenceSha256")
                == sha256_file(evidence_file)
            and restore_log
            and restore_receipt.get("restoreLogSha256")
                == sha256_file(restore_log)
            and mysqlcheck_log
            and restore_receipt.get("mysqlcheckSha256")
                == sha256_file(mysqlcheck_log)
            and b"\tOK" in mysqlcheck_payload
            and evidence.get("schema")
                == "fbsir.u3wIsolatedMysqlEvidence.v1"
            and evidence.get("runId") == run_id
            and evidence.get("sourceCommit") == EXPECTED_SOURCE_COMMIT
            and evidence.get("productionMysqldPidBefore")
                == evidence.get("productionMysqldPidAfter")
            and all(
                evidence.get(field) is True
                for field in (
                    "isolatedProcessExited",
                    "isolatedSocketRemoved",
                    "isolatedPidFileRemoved",
                    "isolatedDatadirRemoved",
                    "isolatedRuntimeDirectoryRemoved",
                )
            )
            and runtime_evidence.get("skipNetworking") is True
            and runtime_evidence.get("tcpListenerAbsent") is True
            and runtime_evidence.get("logBin") is False
            and runtime_evidence.get("eventScheduler") == "OFF"
            and runtime_evidence.get("version")
                == source_receipt.get("serverVersion")
            and runtime_evidence.get("versionComment")
                == source_receipt.get("serverVersionComment")
            and not pathlib.Path(
                "/var/lib/fbsir-w1a-restore", str(run_id)
            ).exists()
            and not pathlib.Path(
                "/run/fbsir-w1a-restore", str(run_id)
            ).exists()
            and receipt_fresh
        )
    backup.update({
        "proven": restore_verified,
        "sha256": actual_digest,
        "sizeBytes": actual_size,
        "restoreProcedureVerified": restore_verified,
        "restoreLiveFactsMatched": restore_verified,
        "receiptAnchorMatched": (
            bool(EXPECTED_BACKUP_RECEIPT_SHA256)
            and backup_receipt_sha256 == EXPECTED_BACKUP_RECEIPT_SHA256
        ),
    })

deployment = {
    "state": None,
    "receiptValidated": False,
    "receiptAnchorMatched": False,
    "sourceCommit": None,
    "strictHeadBuildUploadSwitchReceiptScriptPresent": False,
    "applicationRollbackProven": False,
    "databaseRollbackProven": False,
    # Set only after current symlink/ExecStart/JAR and frontend tree are
    # independently read back against the deployed release identity.
    "actualActiveArtifactsMatched": False,
    "receiptPath": DEPLOYMENT_RECEIPT_PATH,
}
deployment_receipt = pathlib.Path(DEPLOYMENT_RECEIPT_PATH)
if deployment_receipt.is_file():
    deployment_receipt_sha256 = sha256_file(deployment_receipt)
    receipt = json.loads(deployment_receipt.read_text(encoding="utf-8"))
    deployment_state = receipt.get("state")
    source_commit = receipt.get("sourceCommit")
    backend_digest = receipt.get("backendBuildSha256")
    frontend_digest = receipt.get("frontendBuildSha256")
    runner_digest = receipt.get("runnerSha256")
    application_rollback_digest = receipt.get(
        "applicationRollbackReceiptSha256"
    )
    database_rollback_digest = receipt.get(
        "databaseRollbackReceiptSha256"
    )
    release_root = pathlib.Path("/opt/fbsir/admin/releases").resolve()

    def verified_release_file(path_value, expected_digest):
        if not isinstance(path_value, str):
            return False
        candidate = pathlib.Path(path_value).resolve()
        try:
            candidate.relative_to(release_root)
        except ValueError:
            return False
        return (
            candidate.is_file()
            and re.fullmatch(
                r"[0-9a-f]{64}", str(expected_digest or "")
            ) is not None
            and sha256_file(candidate) == expected_digest
        )

    backend_verified = verified_release_file(
        receipt.get("backendBuildPath"), backend_digest
    )
    frontend_verified = verified_release_file(
        receipt.get("frontendBuildPath"), frontend_digest
    )
    runner_verified = verified_release_file(
        receipt.get("runnerPath"), runner_digest
    )
    application_rollback_verified = verified_release_file(
        receipt.get("applicationRollbackReceiptPath"),
        application_rollback_digest,
    )
    database_rollback_verified = verified_release_file(
        receipt.get("databaseRollbackReceiptPath"),
        database_rollback_digest,
    )
    if application_rollback_verified:
        application_rollback = json.loads(pathlib.Path(
            receipt["applicationRollbackReceiptPath"]
        ).read_text(encoding="utf-8"))
        application_rollback_verified = (
            application_rollback.get("schema")
                == "fbsir.u3wApplicationRollbackRehearsalReceipt.v1"
            and application_rollback.get("sourceCommit") == source_commit
            and application_rollback.get("verified") is True
            and application_rollback.get("isolatedTarget") is True
        )
    if database_rollback_verified:
        database_rollback = json.loads(pathlib.Path(
            receipt["databaseRollbackReceiptPath"]
        ).read_text(encoding="utf-8"))
        database_rollback_verified = (
            database_rollback.get("schema")
                == "fbsir.u3wDatabaseRollbackRehearsalReceipt.v1"
            and database_rollback.get("sourceCommit") == source_commit
            and database_rollback.get("verified") is True
            and database_rollback.get("isolatedTarget") is True
        )
    try:
        generated_at = datetime.fromisoformat(
            str(receipt.get("generatedAt", "")).replace("Z", "+00:00")
        )
        receipt_age_seconds = (
            datetime.now(timezone.utc) - generated_at.astimezone(timezone.utc)
        ).total_seconds()
        receipt_is_fresh = 0 <= receipt_age_seconds <= 86400
    except Exception:
        receipt_is_fresh = False
    rollback_shape_valid = (
        deployment_state == "STAGED_FOR_SWITCH"
        or (
            deployment_state == "DEPLOYED_DEFAULT_OFF"
            and re.fullmatch(
                r"[0-9a-f]{64}", str(application_rollback_digest or "")
            ) is not None
            and re.fullmatch(
                r"[0-9a-f]{64}", str(database_rollback_digest or "")
            ) is not None
            and application_rollback_verified
            and database_rollback_verified
        )
    )
    receipt_validated = (
        receipt.get("schema") == "fbsir.u3wW1aDeploymentReadinessReceipt.v1"
        and deployment_state in {
            "STAGED_FOR_SWITCH", "DEPLOYED_DEFAULT_OFF"
        }
        and receipt.get("targetHost") == TARGET_HOST
        and receipt.get("serviceUnit") == SERVICE_UNIT
        and receipt.get("database") == "fbsir"
        and re.fullmatch(r"[0-9a-f]{40}", str(source_commit or "")) is not None
        and re.fullmatch(r"[0-9a-f]{64}", str(backend_digest or "")) is not None
        and re.fullmatch(r"[0-9a-f]{64}", str(frontend_digest or "")) is not None
        and re.fullmatch(r"[0-9a-f]{64}", str(runner_digest or "")) is not None
        and backend_verified
        and frontend_verified
        and runner_verified
        and rollback_shape_valid
        and receipt_is_fresh
    )
    deployment.update({
        "state": deployment_state if receipt_validated else None,
        "receiptValidated": receipt_validated,
        "receiptAnchorMatched": (
            bool(EXPECTED_DEPLOYMENT_RECEIPT_SHA256)
            and deployment_receipt_sha256
                == EXPECTED_DEPLOYMENT_RECEIPT_SHA256
        ),
        "sourceCommit": source_commit if receipt_validated else None,
        "strictHeadBuildUploadSwitchReceiptScriptPresent": (
            receipt.get("strictHeadBuildUploadSwitchReceiptScriptPresent") is True
            and runner_verified
        ),
        "applicationRollbackProven": (
            receipt.get("applicationRollbackProven") is True
            and application_rollback_verified
        ),
        "databaseRollbackProven": (
            receipt.get("databaseRollbackProven") is True
            and database_rollback_verified
        ),
        "actualActiveArtifactsMatched": False,
    })

print(json.dumps({
    "observedAt": datetime.now(timezone.utc).isoformat(),
    "target": {
        "host": TARGET_HOST,
        "authorityObserved": run(["id", "-un"]),
        "serviceUnit": SERVICE_UNIT,
        "serviceState": service.get("ActiveState"),
    },
    "runtime": {
        "jarPath": jar_path,
        "jarSha256": jar_digest,
        "attributionClassCount": attribution_class_count,
    },
    "database": database,
    "portals": {
        "meHttpStatus": http_status("https://me.u3w.com/"),
        "adminHttpStatus": http_status("https://admin.u3w.com/"),
        "api2FbssHealthHttpStatus": http_status(
            "https://api2.u3w.com/api/fbss/health"
        ),
    },
    "configuration": {
        "environmentKeyNames": key_names,
        "explicitFalseKeyNames": explicit_false,
        "eventKeyEntryCount": event_key_count,
        "activeEventKeyPairPresent": active_event_key_pair_present,
        "activeEventKeyId": active_event_key_id or None,
        "previousEventKeyPairComplete": previous_event_key_pair_complete,
        "sameBindingSecretPresent": same_binding_present,
        "environmentFileCustodySecure": environment_file_custody_secure,
        "cryptographicConfigurationShapeValid": cryptographic_shape_valid,
    },
    "backup": backup,
    "deploymentChannel": deployment,
}, ensure_ascii=False))
'@
    $backupAnchor = if ($ExpectedBackupReceiptSha256) {
        $ExpectedBackupReceiptSha256.ToLowerInvariant()
    } else { '' }
    $deploymentAnchor = if ($ExpectedDeploymentReceiptSha256) {
        $ExpectedDeploymentReceiptSha256.ToLowerInvariant()
    } else { '' }
    $legacyBaselineAnchor = if ($ExpectedLegacyBaselineReceiptDigest) {
        $ExpectedLegacyBaselineReceiptDigest.ToLowerInvariant()
    } else { '' }
    $backupRunnerSha256 = (
        Get-FileHash -LiteralPath (
            Join-Path $PSScriptRoot 'run-u3w-production-backup-restore.ps1'
        ) -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $backupWorkerSha256 = (
        Get-FileHash -LiteralPath (
            Join-Path $PSScriptRoot 'u3w-production-backup-remote.py'
        ) -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $restoreVerifierSha256 = (
        Get-FileHash -LiteralPath (
            Join-Path $PSScriptRoot 'u3w-isolated-restore-verifier-remote.py'
        ) -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $remotePython = $remotePython.
        Replace('__SERVICE_UNIT__', ($ServiceUnit | ConvertTo-Json -Compress)).
        Replace('__TARGET_HOST__', ((($SshTarget -split '@', 2)[1]) | ConvertTo-Json -Compress)).
        Replace('__BACKUP_RECEIPT_PATH__', ($DatabaseBackupReceiptPath | ConvertTo-Json -Compress)).
        Replace('__DEPLOYMENT_RECEIPT_PATH__', ($DeploymentReceiptPath | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_BACKUP_RECEIPT_SHA256__', ($backupAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_DEPLOYMENT_RECEIPT_SHA256__', ($deploymentAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST__', ($legacyBaselineAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_SOURCE_COMMIT__', ($ExpectedCommit.ToLowerInvariant() | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_BACKUP_RUNNER_SHA256__', ($backupRunnerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_BACKUP_WORKER_SHA256__', ($backupWorkerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_RESTORE_VERIFIER_SHA256__', ($restoreVerifierSha256 | ConvertTo-Json -Compress))
    $output = $remotePython | & ssh.exe `
        -i $SshKeyPath `
        -o BatchMode=yes `
        -o ConnectTimeout=10 `
        -o ConnectionAttempts=1 `
        -o StrictHostKeyChecking=yes `
        -o UserKnownHostsFile=$KnownHostsPath `
        $SshTarget `
        python3 -
    if ($LASTEXITCODE -ne 0) {
        throw 'remote production-readiness snapshot failed'
    }
    return (($output -join "`n") | ConvertFrom-Json)
}

$gitState = Get-GitState
$snapshot = Invoke-RemoteSnapshot
$snapshot | Add-Member -NotePropertyName local -NotePropertyValue $gitState -Force

$temporarySnapshot = Join-Path $env:TEMP "u3w-production-readiness-$PID.json"
$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
try {
    $snapshot | ConvertTo-Json -Depth 10 |
        Set-Content -LiteralPath $temporarySnapshot -Encoding UTF8
    $node = Resolve-NodeExecutable
    $evaluator = Join-Path $PSScriptRoot 'independent-board-production-readiness.mjs'
    $resultJson = & $node $evaluator --snapshot $temporarySnapshot --output $OutputPath
    if ($LASTEXITCODE -ne 0) {
        throw 'production-readiness evaluator failed'
    }
    $result = ($resultJson -join "`n") | ConvertFrom-Json
    $effectiveStage = if ($RequiredStage) {
        $RequiredStage
    } elseif ($RequireReady) {
        'PREPARED_FOR_STAGE'
    } else {
        $null
    }
    if ($effectiveStage) {
        $stageSatisfied = switch ($effectiveStage) {
            'PREPARED_FOR_STAGE' {
                $result.status -in @(
                    'PREPARED_FOR_STAGE',
                    'STAGED_FOR_SWITCH',
                    'DEPLOYED_DEFAULT_OFF'
                )
            }
            'STAGED_FOR_SWITCH' {
                $result.status -in @('STAGED_FOR_SWITCH', 'DEPLOYED_DEFAULT_OFF')
            }
            'DEPLOYED_DEFAULT_OFF' {
                $result.status -eq 'DEPLOYED_DEFAULT_OFF'
            }
        }
        if (-not $stageSatisfied) {
            $failed = @($result.failedGateIds) + @($result.postDeployFailedGateIds)
            throw "U3W_PRODUCTION_STAGE_NOT_REACHED[$effectiveStage]: $($failed -join ',')"
        }
    }
    $result | ConvertTo-Json -Depth 10
}
finally {
    Remove-Item -LiteralPath $temporarySnapshot -Force -ErrorAction SilentlyContinue
}
