[CmdletBinding()]
param(
    [string]$SshKeyPath = $(if ($env:U3W_SSH_KEY_PATH) { $env:U3W_SSH_KEY_PATH } else { Join-Path $env:USERPROFILE '.ssh\id_ed25519_api2' }),
    [string]$KnownHostsPath = $(Join-Path $env:TEMP 'u3w-production-readiness-known-hosts'),
    [string]$ExpectedBackupReceiptSha256,
    [string]$ExpectedDeploymentReceiptSha256,
    [string]$ExpectedCommit,
    [string]$OutputPath,
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
    foreach ($anchor in @($ExpectedBackupReceiptSha256, $ExpectedDeploymentReceiptSha256)) {
        if ($anchor -and $anchor -notmatch '^[0-9a-fA-F]{64}$') {
            throw 'receipt anchors must be 64-hex SHA-256 values'
        }
    }
    if ($RequireReady -and (
            -not $ExpectedBackupReceiptSha256 -or
            -not $ExpectedDeploymentReceiptSha256)) {
        throw 'RequireReady requires both out-of-band receipt SHA-256 anchors'
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
    return [ordered]@{
        clean = [string]::IsNullOrWhiteSpace(($status -join "`n"))
        sourceCommit = $head
        expectedSourceCommit = $expected
    }
}

function Invoke-RemoteSnapshot {
    Assert-SafeRemoteParameters
    Assert-PrivateKey
    Ensure-KnownHosts

    $remotePython = @'
import hashlib
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
FLAG_NAMES = [
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_CREDIT_ENABLED",
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
    "publicInit043Applied": None,
    "w1aSchemaFingerprintVerified": False,
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
    })
    if database["migrationTableCount"] == 1:
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
            trigger_count = int(query(
                "SELECT COUNT(*) FROM information_schema.triggers "
                "WHERE trigger_schema = DATABASE() AND trigger_name IN "
                "('trg_board_attr_event_v1_no_update',"
                "'trg_board_attr_event_v1_no_delete')"
            ))
            required_unique_index_count = int(query(
                "SELECT COUNT(DISTINCT index_name) "
                "FROM information_schema.statistics "
                "WHERE table_schema = DATABASE() "
                "AND table_name = 'fbs_board_attr_event_v1' "
                "AND index_name IN "
                "('uk_board_attr_event_receipt','uk_board_attr_event_nonce',"
                "'uk_board_attr_event_digest','uk_board_attr_event_watermark',"
                "'uk_board_attr_event_binding_sequence') "
                "AND non_unique = 0"
            ))
            database["w1aSchemaFingerprintVerified"] = (
                database["boardAttributionTableCount"] == 2
                and trigger_count == 2
                and required_unique_index_count == 5
            )

key_names = sorted(environment.keys())
explicit_false = sorted(
    name for name in FLAG_NAMES
    if environment.get(name, "").strip().lower() == "false"
)
event_key_prefix = "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEYS_"
event_key_count = sum(
    1 for name, value in environment.items()
    if name.startswith(event_key_prefix) and bool(value.strip())
)
same_binding_present = bool(
    environment.get(
        "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET", ""
    ).strip()
)

backup = {
    "receiptPath": BACKUP_RECEIPT_PATH,
    "proven": False,
    "receiptAnchorMatched": False,
    "sha256": None,
    "sizeBytes": None,
    "restoreProcedureVerified": False,
}
backup_receipt = pathlib.Path(BACKUP_RECEIPT_PATH)
if backup_receipt.is_file():
    backup_receipt_sha256 = sha256_file(backup_receipt)
    receipt = json.loads(backup_receipt.read_text(encoding="utf-8"))
    artifact_path = receipt.get("backupPath")
    artifact = pathlib.Path(artifact_path) if isinstance(artifact_path, str) else None
    expected_digest = receipt.get("sha256")
    expected_size = receipt.get("sizeBytes")
    restore_receipt_path = receipt.get("restoreReceiptPath")
    restore_receipt_digest = receipt.get("restoreReceiptSha256")
    restore_receipt_file = (
        pathlib.Path(restore_receipt_path)
        if isinstance(restore_receipt_path, str) else None
    )
    restore_verified = False
    if restore_receipt_file and restore_receipt_file.is_file():
        actual_restore_digest = sha256_file(restore_receipt_file)
        restore_receipt = json.loads(
            restore_receipt_file.read_text(encoding="utf-8")
        )
        restore_verified = (
            receipt.get("schema") == "fbsir.u3wDatabaseBackupReceipt.v1"
            and restore_receipt.get("schema")
                == "fbsir.u3wDatabaseRestoreRehearsalReceipt.v1"
            and re.fullmatch(
                r"[0-9a-f]{64}", str(restore_receipt_digest or "")
            ) is not None
            and restore_receipt_digest == actual_restore_digest
            and restore_receipt.get("sourceBackupSha256") == expected_digest
            and restore_receipt.get("verified") is True
            and restore_receipt.get("isolatedTarget") is True
        )
    if artifact and artifact.is_file():
        actual_size = artifact.stat().st_size
        actual_digest = sha256_file(artifact)
        backup.update({
            "proven": (
                re.fullmatch(r"[0-9a-f]{64}", str(expected_digest or "")) is not None
                and expected_digest == actual_digest
                and expected_size == actual_size
                and actual_size > 0
                and restore_verified
            ),
            "sha256": actual_digest,
            "sizeBytes": actual_size,
            "restoreProcedureVerified": restore_verified,
            "receiptAnchorMatched": (
                bool(EXPECTED_BACKUP_RECEIPT_SHA256)
                and backup_receipt_sha256 == EXPECTED_BACKUP_RECEIPT_SHA256
            ),
        })

deployment = {
    "receiptValidated": False,
    "receiptAnchorMatched": False,
    "sourceCommit": None,
    "strictHeadBuildUploadSwitchReceiptScriptPresent": False,
    "applicationRollbackProven": False,
    "databaseRollbackProven": False,
    "receiptPath": DEPLOYMENT_RECEIPT_PATH,
}
deployment_receipt = pathlib.Path(DEPLOYMENT_RECEIPT_PATH)
if deployment_receipt.is_file():
    deployment_receipt_sha256 = sha256_file(deployment_receipt)
    receipt = json.loads(deployment_receipt.read_text(encoding="utf-8"))
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
    receipt_validated = (
        receipt.get("schema") == "fbsir.u3wW1aDeploymentReadinessReceipt.v1"
        and receipt.get("targetHost") == TARGET_HOST
        and receipt.get("serviceUnit") == SERVICE_UNIT
        and receipt.get("database") == "fbsir"
        and re.fullmatch(r"[0-9a-f]{40}", str(source_commit or "")) is not None
        and re.fullmatch(r"[0-9a-f]{64}", str(backend_digest or "")) is not None
        and re.fullmatch(r"[0-9a-f]{64}", str(frontend_digest or "")) is not None
        and re.fullmatch(r"[0-9a-f]{64}", str(runner_digest or "")) is not None
        and re.fullmatch(
            r"[0-9a-f]{64}", str(application_rollback_digest or "")
        ) is not None
        and re.fullmatch(
            r"[0-9a-f]{64}", str(database_rollback_digest or "")
        ) is not None
        and receipt_is_fresh
    )
    deployment.update({
        "receiptValidated": receipt_validated,
        "receiptAnchorMatched": (
            bool(EXPECTED_DEPLOYMENT_RECEIPT_SHA256)
            and deployment_receipt_sha256
                == EXPECTED_DEPLOYMENT_RECEIPT_SHA256
        ),
        "sourceCommit": source_commit if receipt_validated else None,
        "strictHeadBuildUploadSwitchReceiptScriptPresent": (
            receipt.get("strictHeadBuildUploadSwitchReceiptScriptPresent") is True
        ),
        "applicationRollbackProven": (
            receipt.get("applicationRollbackProven") is True
        ),
        "databaseRollbackProven": (
            receipt.get("databaseRollbackProven") is True
        ),
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
        "sameBindingSecretPresent": same_binding_present,
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
    $remotePython = $remotePython.
        Replace('__SERVICE_UNIT__', ($ServiceUnit | ConvertTo-Json -Compress)).
        Replace('__TARGET_HOST__', ((($SshTarget -split '@', 2)[1]) | ConvertTo-Json -Compress)).
        Replace('__BACKUP_RECEIPT_PATH__', ($DatabaseBackupReceiptPath | ConvertTo-Json -Compress)).
        Replace('__DEPLOYMENT_RECEIPT_PATH__', ($DeploymentReceiptPath | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_BACKUP_RECEIPT_SHA256__', ($backupAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_DEPLOYMENT_RECEIPT_SHA256__', ($deploymentAnchor | ConvertTo-Json -Compress))
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
    if ($RequireReady -and -not $result.readyForDefaultOffRelease) {
        throw "U3W_PRODUCTION_NOT_READY: $(@($result.failedGateIds) -join ',')"
    }
    $result | ConvertTo-Json -Depth 10
}
finally {
    Remove-Item -LiteralPath $temporarySnapshot -Force -ErrorAction SilentlyContinue
}
