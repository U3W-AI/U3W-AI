[CmdletBinding()]
param(
    [switch]$AllowDestructiveTest,
    [ValidateSet('8.0.30', '8.0.45', '8.4.8')]
    [string[]]$Versions = @('8.0.30', '8.4.8'),
    [string]$ReceiptPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AllowDestructiveTest) {
    throw 'Explicit -AllowDestructiveTest consent is required.'
}

$repoRoot = [System.IO.Path]::GetFullPath(
    (Split-Path -Parent $PSScriptRoot))
$migrationPath = Join-Path $repoRoot (
    'sql\update_20260723_independent_board_attribution_v1.sql')
$historicalPath = Join-Path $repoRoot (
    'sql\update_20260722_independent_board_attribution_evidence_contract.sql')
$toolchainRoot = Join-Path $env:USERPROFILE '.cache\u3w-mysql-toolchain'
$isolatedRoot = 'C:\u3w-w1a-it'
if ([string]::IsNullOrWhiteSpace($ReceiptPath)) {
    $ReceiptPath = Join-Path $repoRoot (
        'reports\independent-board\w1a-attribution-v1-dual-mysql-latest.json')
}
$receiptFullPath = [System.IO.Path]::GetFullPath($ReceiptPath)
$reportDirectory = Split-Path -Parent $receiptFullPath
$null = New-Item -ItemType Directory -Force -Path $reportDirectory

if (-not (Test-Path -LiteralPath $migrationPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $historicalPath -PathType Leaf)) {
    throw 'Wave 1 migration or historical 037 contract is missing.'
}

function Get-EphemeralPort {
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Invoke-Client {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$Database = '',
        [switch]$ExpectFailure
    )
    $arguments = @(
        '--no-defaults',
        '--protocol=TCP',
        '--host=127.0.0.1',
        "--port=$($Profile.Port)",
        '--user=root',
        '--default-character-set=utf8mb4',
        '--batch',
        '--skip-column-names'
    )
    if (-not [string]::IsNullOrWhiteSpace($Database)) {
        $arguments += "--database=$Database"
    }
    $arguments += @('--execute', $Sql)
    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Profile.MySql @arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($ExpectFailure) {
        if ($exitCode -eq 0) {
            throw "Expected MySQL statement to fail on $($Profile.Version)."
        }
        return ($output | ForEach-Object { $_.ToString() }) -join "`n"
    }
    if ($exitCode -ne 0) {
        throw "MySQL $($Profile.Version) statement failed: $(
            ($output | ForEach-Object { $_.ToString() }) -join "`n")"
    }
    return (($output | ForEach-Object { $_.ToString() }) -join "`n").Trim()
}

function Invoke-Migration {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][string]$Database
    )
    $source = $migrationPath.Replace('\', '/')
    $null = Invoke-Client -Profile $Profile -Database $Database `
        -Sql "source $source;"
}

function Get-Sha256Text {
    param([Parameter(Mandatory = $true)][string]$Value)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString(
                $hasher.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $hasher.Dispose()
    }
}

function ConvertTo-CanonicalRows {
    param([Parameter(Mandatory = $true)][object[]]$Rows)
    $result = [System.Collections.Generic.List[string]]::new()
    foreach ($value in @($Rows)) {
        foreach ($line in ([string]$value -split "`r?`n")) {
            if ($line.Length -gt 0) {
                $result.Add($line)
            }
        }
    }
    return [string[]]$result
}

function Get-CanonicalRowsSha256 {
    param([Parameter(Mandatory = $true)][object[]]$Rows)
    $canonical = (@(ConvertTo-CanonicalRows $Rows) -join "`n") + "`n"
    return Get-Sha256Text $canonical
}

function Get-FingerprintCategorySha256 {
    param([Parameter(Mandatory = $true)][object[]]$Rows)
    $result = [ordered]@{}
    foreach ($group in (@(ConvertTo-CanonicalRows $Rows) |
            Group-Object { $_.Split('|')[0] })) {
        $result[[string]$group.Name] = Get-CanonicalRowsSha256 (
            @($group.Group))
    }
    return [pscustomobject]$result
}

function Resolve-PythonExecutable {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace(
            $env:U3W_PYTHON_EXECUTABLE)) {
        $candidates.Add($env:U3W_PYTHON_EXECUTABLE)
    }
    $candidates.Add((Join-Path $env:USERPROFILE (
        '.cache\codex-runtimes\codex-primary-runtime\' +
        'dependencies\python\python.exe')))
    foreach ($name in @('python3.exe', 'python.exe')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) {
            $candidates.Add($command.Source)
        }
    }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return [System.IO.Path]::GetFullPath($candidate)
        }
    }
    throw 'Python 3 is required for the real PyMySQL worker integration.'
}

function Invoke-WorkerOrchestrationIt {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][string]$ExactDatabase,
        [Parameter(Mandatory = $true)][string]$RunningDatabase,
        [Parameter(Mandatory = $true)][string]$FixtureRoot,
        [Parameter(Mandatory = $true)][string]$PythonExecutable,
        [Parameter(Mandatory = $true)][string]$ExpectedFingerprint,
        [Parameter(Mandatory = $true)]$ExpectedFingerprintCategories,
        [Parameter(Mandatory = $true)][object[]]$ExpectedCheckRows
    )
    $workerPath = Join-Path $PSScriptRoot (
        'u3w-default-off-release-remote.py')
    $fixtureSql = Join-Path $FixtureRoot 'sql\public_init_043.sql'
    $null = New-Item -ItemType Directory -Force -Path (
        Split-Path -Parent $fixtureSql)
    Copy-Item -LiteralPath $migrationPath -Destination $fixtureSql -Force
    $configuration = [ordered]@{
        workerPath = $workerPath
        releasePath = $FixtureRoot
        migrationPath = $fixtureSql
        migrationSha256 = $migrationSha
        host = '127.0.0.1'
        port = $Profile.Port
        user = 'root'
        password = ''
        exactDatabase = $ExactDatabase
        runningDatabase = $RunningDatabase
        expectedFingerprint = $ExpectedFingerprint
        expectedFingerprintCategories = $ExpectedFingerprintCategories
        expectedCheckRows = @($ExpectedCheckRows)
    }
    $encoded = [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes(
            ($configuration | ConvertTo-Json -Compress)))
    $python = @'
import base64
import hashlib
import importlib.metadata
import importlib.util
import json
import pathlib
import sys
import types

import pymysql

configuration = json.loads(
    base64.b64decode(sys.argv[1]).decode("utf-8")
)
sys.modules["fcntl"] = types.SimpleNamespace(
    LOCK_EX=2, LOCK_SH=1, LOCK_UN=8, flock=lambda *_: None
)
spec = importlib.util.spec_from_file_location(
    "u3w_worker_it", configuration["workerPath"]
)
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)
worker.validate_regular_file = (
    lambda path, parent=None, modes=None: pathlib.Path(path)
)
worker.validate_apply_session_identity = lambda *args, **kwargs: {}
if worker.EXPECTED_W1A_SCHEMA_FINGERPRINT != configuration[
    "expectedFingerprint"
]:
    raise RuntimeError("committed worker schema fingerprint drifted")
args = types.SimpleNamespace(
    migration_sha=configuration["migrationSha256"]
)
stage = {
    "w1aDatabaseState": "ABSENT",
    "attributionEventCount": 0,
    "attributionJourneyCount": 0,
}
release_path = pathlib.Path(configuration["releasePath"])

def connection(database):
    return {
        "host": configuration["host"],
        "port": int(configuration["port"]),
        "user": configuration["user"],
        "password": configuration["password"],
        "database": database,
    }

def prove_lock_and_close(lease, database):
    second = pymysql.connect(
        host=configuration["host"],
        port=int(configuration["port"]),
        user=configuration["user"],
        password=configuration["password"],
        database=database,
        charset="utf8mb4",
        autocommit=True,
    )
    try:
        with second.cursor() as cursor:
            cursor.execute(
                "SELECT GET_LOCK('u3w:w1a:public_init_043',0)"
            )
            blocked = int(cursor.fetchone()[0])
        if blocked != 0:
            raise RuntimeError("worker migration lease did not retain lock")
        lease.close()
        with second.cursor() as cursor:
            cursor.execute(
                "SELECT GET_LOCK('u3w:w1a:public_init_043',0)"
            )
            released = int(cursor.fetchone()[0])
            cursor.execute(
                "SELECT RELEASE_LOCK('u3w:w1a:public_init_043')"
            )
        if released != 1:
            raise RuntimeError("worker migration lease did not release lock")
    finally:
        if not lease.closed:
            lease.close()
        second.close()
    return {"blockedWhileLeaseOpen": blocked, "acquiredAfterClose": released}

def apply(database):
    selected = connection(database)
    worker.DATABASE = database
    worker.parse_environment = lambda: ({}, selected)
    try:
        lease = worker.apply_migration(args, release_path, stage)
    except RuntimeError as error:
        mysql = worker.Mysql(selected)
        try:
            actual_rows = worker.migration_fingerprint_rows(mysql)
        finally:
            mysql.close()
        def category_hashes(rows):
            categories = {}
            for row in rows:
                categories.setdefault(row.split("|", 1)[0], []).append(row)
            return {
                name: hashlib.sha256(
                    ("\n".join(sorted(values)) + "\n").encode("utf-8")
                ).hexdigest()
                for name, values in categories.items()
            }
        actual_check_rows = [
            row for row in actual_rows if row.startswith("H|")
        ]
        raise RuntimeError(json.dumps({
            "workerError": str(error),
            "expectedFingerprintCategories":
                configuration["expectedFingerprintCategories"],
            "actualFingerprintCategories": category_hashes(actual_rows),
            "missingCheckRows": sorted(
                set(configuration["expectedCheckRows"]) -
                set(actual_check_rows)
            ),
            "extraCheckRows": sorted(
                set(actual_check_rows) -
                set(configuration["expectedCheckRows"])
            ),
        }, sort_keys=True)) from error
    facts = lease.facts
    if facts.get("schemaFingerprintSha256") != configuration[
        "expectedFingerprint"
    ]:
        lease.close()
        raise RuntimeError("worker schema fingerprint drifted")
    lock = prove_lock_and_close(lease, database)
    return {
        "databaseChangedThisRun": lease.database_changed_this_run,
        "databaseChangedSinceStage": lease.database_changed_since_stage,
        "facts": facts,
        "lock": lock,
    }

first = apply(configuration["exactDatabase"])
applied_reentry = apply(configuration["exactDatabase"])
running_recovery = apply(configuration["runningDatabase"])
if (
    first["databaseChangedThisRun"] is not True
    or first["databaseChangedSinceStage"] is not True
    or applied_reentry["databaseChangedThisRun"] is not False
    or applied_reentry["databaseChangedSinceStage"] is not True
    or running_recovery["databaseChangedThisRun"] is not True
    or running_recovery["databaseChangedSinceStage"] is not True
):
    raise RuntimeError("worker database change semantics drifted")
print(json.dumps({
    "status": "PASS",
    "pymysqlVersion": getattr(pymysql, "__version__", None),
    "pymysqlDistributionVersion":
        importlib.metadata.version("PyMySQL"),
    "firstApply": first,
    "exactAppliedRecovery": applied_reentry,
    "runningRecovery": running_recovery,
}, sort_keys=True))
'@
    $pythonPayload = [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes($python))
    $bootstrap = (
        "import base64,sys;" +
        "payload=base64.b64decode(sys.argv.pop(1));" +
        "exec(compile(payload," +
        "'<worker-mysql-it>','exec'))")
    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $PythonExecutable -c $bootstrap `
            $pythonPayload $encoded 2>&1
        $pythonExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($pythonExitCode -ne 0) {
        throw "MySQL $($Profile.Version) real worker integration failed: $(
            ($output | ForEach-Object { $_.ToString() }) -join "`n")"
    }
    $document = (($output | ForEach-Object {
                $_.ToString()
            }) -join "`n") | ConvertFrom-Json
    if (
        $document.status -cne 'PASS' -or
        $document.firstApply.lock.blockedWhileLeaseOpen -ne 0 -or
        $document.firstApply.lock.acquiredAfterClose -ne 1 -or
        $document.exactAppliedRecovery.lock.blockedWhileLeaseOpen -ne 0 -or
        $document.runningRecovery.lock.blockedWhileLeaseOpen -ne 0 -or
        $document.firstApply.facts.schemaFingerprintSha256 -cne
            $ExpectedFingerprint -or
        $document.exactAppliedRecovery.facts.schemaFingerprintSha256 -cne
            $ExpectedFingerprint -or
        $document.runningRecovery.facts.schemaFingerprintSha256 -cne
            $ExpectedFingerprint
    ) {
        throw "MySQL $($Profile.Version) real worker proof drifted."
    }
    return $document
}

$schemaFingerprintSql = @"
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
  SELECT CONCAT_WS('|','F',HEX(constraint_name),HEX(table_name),
    HEX(referenced_table_name),HEX(update_rule),HEX(delete_rule),
    HEX(match_option))
  FROM information_schema.referential_constraints
  WHERE constraint_schema=DATABASE()
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
    HEX(REGEXP_REPLACE(TRIM(action_statement),'[[:space:]]+',' ')))
  FROM information_schema.triggers
  WHERE trigger_schema=DATABASE()
    AND event_object_table IN
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
ORDER BY BINARY row_value;
"@

$migrationSha = (
    Get-FileHash -LiteralPath $migrationPath -Algorithm SHA256
).Hash.ToLowerInvariant()
$historicalSha = (
    Get-FileHash -LiteralPath $historicalPath -Algorithm SHA256
).Hash.ToLowerInvariant()
if ($historicalSha -cne
    '59e3696ff3f8d4a16b4659c94a108f35fb1badf2f4de16079229c031a0b44cce') {
    throw "Historical public_init_037 SHA-256 drifted: $historicalSha"
}

$pythonExecutable = Resolve-PythonExecutable
$pythonPackageRoot = Join-Path $toolchainRoot 'python-pymysql-1.1.2'
$originalPythonPath = $env:PYTHONPATH
$env:PYTHONPATH = if ([string]::IsNullOrWhiteSpace(
        $originalPythonPath)) {
    $pythonPackageRoot
}
else {
    $pythonPackageRoot + [IO.Path]::PathSeparator + $originalPythonPath
}
$previousErrorAction = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $importOutput = & $pythonExecutable -c 'import pymysql' 2>&1
    $importExitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previousErrorAction
}
if ($importExitCode -ne 0) {
    $null = New-Item -ItemType Directory -Force -Path $pythonPackageRoot
    try {
        $ErrorActionPreference = 'Continue'
        $pipOutput = & $pythonExecutable -m pip install `
            --disable-pip-version-check --no-input --no-compile `
            --target $pythonPackageRoot 'PyMySQL==1.1.2' 2>&1
        $pipExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($pipExitCode -ne 0) {
        throw "Pinned PyMySQL installation failed: $(
            ($pipOutput | ForEach-Object { $_.ToString() }) -join "`n")"
    }
    try {
        $ErrorActionPreference = 'Continue'
        $importOutput = & $pythonExecutable -c 'import pymysql' 2>&1
        $importExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($importExitCode -ne 0) {
        throw 'Pinned PyMySQL 1.1.2 is not importable.'
    }
}

$results = [System.Collections.Generic.List[object]]::new()
$runId = [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')

foreach ($version in $Versions) {
    $base = Join-Path $toolchainRoot "mysql-$version-winx64"
    $bin = Join-Path $base 'bin'
    $mysqld = Join-Path $bin 'mysqld.exe'
    $mysql = Join-Path $bin 'mysql.exe'
    $mysqladmin = Join-Path $bin 'mysqladmin.exe'
    if (-not (Test-Path -LiteralPath $mysqld -PathType Leaf) -or
        -not (Test-Path -LiteralPath $mysql -PathType Leaf) -or
        -not (Test-Path -LiteralPath $mysqladmin -PathType Leaf)) {
        throw "MySQL $version toolchain is incomplete below $bin"
    }

    $runRoot = [System.IO.Path]::GetFullPath(
        (Join-Path $isolatedRoot "$runId-$($version.Replace('.', ''))"))
    $expectedPrefix = [System.IO.Path]::GetFullPath($isolatedRoot).TrimEnd('\') + '\'
    if (-not $runRoot.StartsWith(
            $expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing isolated MySQL path outside $isolatedRoot"
    }
    $dataRoot = Join-Path $runRoot 'data'
    $errorLog = Join-Path $runRoot 'mysql-error.log'
    $pidFile = Join-Path $runRoot 'mysql.pid'
    $null = New-Item -ItemType Directory -Force -Path $runRoot
    $port = Get-EphemeralPort
    $process = $null
    $database = "w1a_$($version.Replace('.', ''))"
    try {
        $initializeOutput = & $mysqld '--no-defaults' '--initialize-insecure' `
            "--basedir=$base" "--datadir=$dataRoot" 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL $version initialize failed: $initializeOutput"
        }
        $arguments = @(
            '--no-defaults',
            "--basedir=$base",
            "--datadir=$dataRoot",
            "--port=$port",
            '--bind-address=127.0.0.1',
            '--skip-networking=0',
            "--pid-file=$pidFile",
            "--log-error=$errorLog",
            '--mysqlx=OFF',
            '--secure-file-priv=NULL'
        )
        $process = Start-Process -FilePath $mysqld -ArgumentList $arguments `
            -PassThru -WindowStyle Hidden
        $ready = $false
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            Start-Sleep -Milliseconds 500
            & $mysqladmin '--no-defaults' '--protocol=TCP' `
                '--host=127.0.0.1' "--port=$port" '--user=root' ping `
                2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $ready = $true
                break
            }
            if ($process.HasExited) {
                break
            }
        }
        if (-not $ready) {
            $tail = if (Test-Path -LiteralPath $errorLog) {
                (Get-Content -LiteralPath $errorLog -Tail 80) -join "`n"
            } else { 'no error log' }
            throw "MySQL $version did not become ready: $tail"
        }

        $profile = [pscustomobject]@{
            Version = $version
            Port = $port
            MySql = $mysql
        }
        $prerequisiteSql = @"
CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE $database;
CREATE TABLE u3w_schema_migration (
  version VARCHAR(128) NOT NULL,
  description VARCHAR(512) NOT NULL,
  applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (version)
) ENGINE=InnoDB;
CREATE TABLE sys_menu (
  menu_id BIGINT NOT NULL AUTO_INCREMENT,
  menu_name VARCHAR(64) NOT NULL,
  parent_id BIGINT NOT NULL DEFAULT 0,
  order_num INT NOT NULL DEFAULT 0,
  path VARCHAR(200) NOT NULL DEFAULT '',
  component VARCHAR(255) NULL,
  query VARCHAR(255) NULL,
  route_name VARCHAR(64) NULL,
  is_frame INT NOT NULL DEFAULT 1,
  is_cache INT NOT NULL DEFAULT 0,
  menu_type CHAR(1) NOT NULL DEFAULT '',
  visible CHAR(1) NOT NULL DEFAULT '0',
  status CHAR(1) NOT NULL DEFAULT '0',
  perms VARCHAR(100) NULL,
  icon VARCHAR(100) NOT NULL DEFAULT '#',
  create_by VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME NULL,
  update_by VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (menu_id)
) ENGINE=InnoDB;
INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,query,route_name,
   is_frame,is_cache,menu_type,visible,status,perms,icon)
VALUES
  (CONVERT(0xE78BACE891A3E4BC9AE7AEA1E79086 USING utf8mb4),
   0,5,'independent-board-admin',NULL,NULL,
   'IndependentBoardAdmin',1,0,'M','0','0','','peoples');
"@
        $null = Invoke-Client -Profile $profile -Sql $prerequisiteSql
        Invoke-Migration -Profile $profile -Database $database
        $firstMigrationFingerprintRows = Invoke-Client -Profile $profile `
            -Database $database -Sql $schemaFingerprintSql
        $firstMigrationFingerprintSha256 = Get-CanonicalRowsSha256 (
            $firstMigrationFingerprintRows)
        Invoke-Migration -Profile $profile -Database $database

        $shape = Invoke-Client -Profile $profile -Database $database -Sql @"
SELECT CONCAT_WS('|',
  VERSION(),
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema=DATABASE()
      AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')),
  (SELECT COUNT(*) FROM information_schema.triggers
    WHERE trigger_schema=DATABASE()
      AND event_object_table IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
    WHERE constraint_schema=DATABASE()
      AND table_name IN
        ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
      AND update_rule IN ('RESTRICT','NO ACTION')
      AND delete_rule IN ('RESTRICT','NO ACTION')),
  (SELECT COUNT(*) FROM sys_menu
    WHERE BINARY perms=BINARY 'board:attribution:query'),
  (SELECT COUNT(*) FROM u3w_schema_migration
    WHERE version='20260723_independent_board_attribution_v1_043'));
"@
        $shapeParts = $shape.Split('|')
        if ($shapeParts.Count -ne 6 -or
            $shapeParts[0] -ne $version -or
            $shapeParts[1] -ne '2' -or
            $shapeParts[2] -ne '2' -or
            $shapeParts[3] -ne '1' -or
            $shapeParts[4] -ne '1' -or
            $shapeParts[5] -ne '1') {
            throw "MySQL $version exact shape proof failed: $shape"
        }
        $schemaFingerprintRows = Invoke-Client -Profile $profile `
            -Database $database -Sql $schemaFingerprintSql
        $schemaFingerprintSha256 = Get-CanonicalRowsSha256 (
            $schemaFingerprintRows)
        $schemaFingerprintCategorySha256 =
            Get-FingerprintCategorySha256 $schemaFingerprintRows
        $schemaFingerprintCheckRows = @(
            ConvertTo-CanonicalRows $schemaFingerprintRows |
                Where-Object { $_.StartsWith('H|') })
        if ($firstMigrationFingerprintSha256 -cne
            $schemaFingerprintSha256) {
            throw (
                "MySQL $version one-pass fingerprint drifted: " +
                "$firstMigrationFingerprintSha256 -> " +
                $schemaFingerprintSha256)
        }
        $workerExactDatabase = $database + '_worker_exact'
        $workerRunningDatabase = $database + '_worker_running'
        foreach ($workerDatabase in @(
                $workerExactDatabase, $workerRunningDatabase)) {
            $workerPrerequisiteSql = $prerequisiteSql.
                Replace(
                    "CREATE DATABASE $database ",
                    "CREATE DATABASE $workerDatabase ").
                Replace("USE $database;", "USE $workerDatabase;")
            $null = Invoke-Client -Profile $profile `
                -Sql $workerPrerequisiteSql
        }
        $null = Invoke-Client -Profile $profile `
            -Database $workerRunningDatabase -Sql @"
INSERT INTO u3w_schema_migration(version,description)
VALUES(
  'public_init_043',
  'RUNNING:Independent Board exact official experts attribution v1'
);
"@
        $workerProof = Invoke-WorkerOrchestrationIt `
            -Profile $profile `
            -ExactDatabase $workerExactDatabase `
            -RunningDatabase $workerRunningDatabase `
            -FixtureRoot (Join-Path $runRoot 'worker-release') `
            -PythonExecutable $pythonExecutable `
            -ExpectedFingerprint $schemaFingerprintSha256 `
            -ExpectedFingerprintCategories $schemaFingerprintCategorySha256 `
            -ExpectedCheckRows $schemaFingerprintCheckRows

        $binding = ''.PadLeft(64, 'b')
        $event = ''.PadLeft(64, '1')
        $receipt = ''.PadLeft(64, '2')
        $tenant = ''.PadLeft(64, '3')
        $journey = ''.PadLeft(64, '4')
        $canonical = ''.PadLeft(64, '5')
        $eventDigest = ''.PadLeft(64, '6')
        $signature = ''.PadLeft(64, '7')
        $nonce = ''.PadLeft(64, '8')
        $null = Invoke-Client -Profile $profile -Database $database -Sql @"
INSERT INTO fbs_board_attr_journey_v1
  (same_binding_key,contract_id,tenant_subject_digest,server_binding_id,
   journey_id,product_id,listed_manifest_version,embedded_contract_version,
   channel,terminal,host_version,traffic_class,intent_family,
   last_sequence_no,last_event_digest,head_version)
VALUES
  ('$binding','FBSIR_INDEPENDENT_BOARD_W1A_V1','$tenant',
   'srv_wave1Binding01','$journey','fbsir-eight-seat-board','26.7.21',
   '26.7.20','OFFICIAL_EXPERTS','WORKBUDDY_WINDOWS','5.3.3.0',
   'NATURAL','UNKNOWN',0,'',0);
INSERT INTO fbs_board_attr_event_v1
  (event_id,receipt_id,same_binding_key,contract_id,journey_id,
   server_binding_id,tenant_subject_digest,event_type,sequence_no,occurred_at,
   product_id,package_id,agent_name,marketplace,listed_surface,
   listed_manifest_version,embedded_contract_version,host_client_family,
   host_version,terminal,channel,request_source,intent_signal,intent_family,
   classification_source,classifier_version,confidence_bucket,review_mode,
   traffic_class,traffic_authority,outcome,previous_event_digest,traceparent,
   canonical_digest,event_digest,signer_key_id,signature_hex,nonce_hash,
   issued_at,expires_at,authoritative_product_credit)
VALUES
  ('$event','$receipt','$binding','FBSIR_INDEPENDENT_BOARD_W1A_V1','$journey',
   'srv_wave1Binding01','$tenant','ENTRY_OBSERVED',1,
   '2026-07-23 10:00:00.000','fbsir-eight-seat-board',
   'fbsir-eight-seat-board','board-convener','experts','listed_runtime_state',
   '26.7.21','26.7.20','WORKBUDDY','5.3.3.0','WORKBUDDY_WINDOWS',
   'OFFICIAL_EXPERTS','WORKBUDDY_OFFICIAL_ENTRY','unknown','UNKNOWN',
   'PACKAGE_SCENE_ROUTER','scene-lexicon-26.7.20-core.1','UNKNOWN',
   'STANDARD_REVIEW','NATURAL','API2_SERVER_CLASSIFIER_V1','SUCCESS','','',
   '$canonical','$eventDigest','wave1-k1','$signature','$nonce',
   '2026-07-23 10:00:00.000','2026-07-23 10:02:00.000',0);
UPDATE fbs_board_attr_journey_v1
SET last_sequence_no=1,last_event_digest='$eventDigest',
    head_version=1,updated_at=CURRENT_TIMESTAMP(3)
WHERE same_binding_key='$binding' AND head_version=0
  AND last_sequence_no=0;
"@
        $aggregate = Invoke-Client -Profile $profile -Database $database -Sql @"
SELECT CONCAT_WS('|',
  SUM(CASE WHEN event_type='ENTRY_OBSERVED' THEN 1 ELSE 0 END),
  COUNT(DISTINCT same_binding_key),
  MAX(event_watermark),
  MAX(authoritative_product_credit))
FROM fbs_board_attr_event_v1
WHERE occurred_at >= '2026-07-23 00:00:00'
  AND occurred_at < '2026-07-24 00:00:00'
  AND traffic_class='NATURAL';
"@
        if ($aggregate -ne '1|1|1|0') {
            throw "MySQL $version aggregate proof failed: $aggregate"
        }
        $updateFailure = Invoke-Client -Profile $profile -Database $database `
            -ExpectFailure -Sql (
                "UPDATE fbs_board_attr_event_v1 SET outcome='FAILED' " +
                "WHERE event_id='$event';")
        $deleteFailure = Invoke-Client -Profile $profile -Database $database `
            -ExpectFailure -Sql (
                "DELETE FROM fbs_board_attr_event_v1 " +
                "WHERE event_id='$event';")
        if ($updateFailure -notmatch 'append-only' -or
            $deleteFailure -notmatch 'cannot be deleted') {
            throw "MySQL $version immutable trigger proof failed."
        }
        $null = Invoke-Client -Profile $profile -Database $database -Sql @"
CREATE TRIGGER trg_board_attr_journey_v1_it_extra
BEFORE INSERT ON fbs_board_attr_journey_v1
FOR EACH ROW SET NEW.head_version=NEW.head_version;
"@
        $extraTriggerRows = Invoke-Client -Profile $profile `
            -Database $database -Sql $schemaFingerprintSql
        $extraTriggerSha256 = Get-CanonicalRowsSha256 $extraTriggerRows
        $extraTriggerCount = Invoke-Client -Profile $profile `
            -Database $database -Sql @"
SELECT COUNT(*) FROM information_schema.triggers
WHERE trigger_schema=DATABASE()
  AND event_object_table IN
    ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1');
"@
        if ($extraTriggerCount -ne '3' -or
            $extraTriggerSha256 -ceq $schemaFingerprintSha256) {
            throw "MySQL $version extra W1A trigger drift was not detected."
        }
        $null = Invoke-Client -Profile $profile -Database $database -Sql (
            'DROP TRIGGER trg_board_attr_journey_v1_it_extra;')
        $restoredTriggerRows = Invoke-Client -Profile $profile `
            -Database $database -Sql $schemaFingerprintSql
        if ((Get-CanonicalRowsSha256 $restoredTriggerRows) -cne
            $schemaFingerprintSha256) {
            throw "MySQL $version trigger fingerprint did not restore."
        }
        $permissionRootId = Invoke-Client -Profile $profile `
            -Database $database -Sql @"
SELECT parent_id FROM sys_menu
WHERE BINARY perms=BINARY 'board:attribution:query';
"@
        if ($permissionRootId -notmatch '^[0-9]+$') {
            throw "MySQL $version permission root identity is invalid."
        }
        $null = Invoke-Client -Profile $profile -Database $database -Sql @"
UPDATE sys_menu SET status='1'
WHERE BINARY perms=BINARY 'board:attribution:query';
"@
        $disabledPermissionRows = Invoke-Client -Profile $profile `
            -Database $database -Sql $schemaFingerprintSql
        if ((Get-CanonicalRowsSha256 $disabledPermissionRows) -ceq
            $schemaFingerprintSha256) {
            throw "MySQL $version disabled permission drift was not detected."
        }
        $null = Invoke-Client -Profile $profile -Database $database -Sql @"
UPDATE sys_menu SET status='0',parent_id=0
WHERE BINARY perms=BINARY 'board:attribution:query';
"@
        $wrongParentRows = Invoke-Client -Profile $profile `
            -Database $database -Sql $schemaFingerprintSql
        if ((Get-CanonicalRowsSha256 $wrongParentRows) -ceq
            $schemaFingerprintSha256) {
            throw "MySQL $version permission parent drift was not detected."
        }
        $null = Invoke-Client -Profile $profile -Database $database -Sql @"
UPDATE sys_menu SET parent_id=$permissionRootId
WHERE BINARY perms=BINARY 'board:attribution:query';
"@
        $restoredPermissionRows = Invoke-Client -Profile $profile `
            -Database $database -Sql $schemaFingerprintSql
        if ((Get-CanonicalRowsSha256 $restoredPermissionRows) -cne
            $schemaFingerprintSha256) {
            throw "MySQL $version permission fingerprint did not restore."
        }
        $rootSemanticDrifts = @(
            @{
                Name = 'status'
                Drift = "UPDATE sys_menu SET status='1' WHERE menu_id=$permissionRootId;"
                Restore = "UPDATE sys_menu SET status='0' WHERE menu_id=$permissionRootId;"
            },
            @{
                Name = 'menu_type'
                Drift = "UPDATE sys_menu SET menu_type='C' WHERE menu_id=$permissionRootId;"
                Restore = "UPDATE sys_menu SET menu_type='M' WHERE menu_id=$permissionRootId;"
            },
            @{
                Name = 'menu_name'
                Drift = "UPDATE sys_menu SET menu_name='Drifted root' WHERE menu_id=$permissionRootId;"
                Restore = "UPDATE sys_menu SET menu_name=CONVERT(0xE78BACE891A3E4BC9AE7AEA1E79086 USING utf8mb4) WHERE menu_id=$permissionRootId;"
            },
            @{
                Name = 'parent_id'
                Drift = "UPDATE sys_menu SET parent_id=99 WHERE menu_id=$permissionRootId;"
                Restore = "UPDATE sys_menu SET parent_id=0 WHERE menu_id=$permissionRootId;"
            }
        )
        foreach ($rootDrift in $rootSemanticDrifts) {
            $null = Invoke-Client -Profile $profile -Database $database `
                -Sql $rootDrift.Drift
            $driftedRootRows = Invoke-Client -Profile $profile `
                -Database $database -Sql $schemaFingerprintSql
            if ((Get-CanonicalRowsSha256 $driftedRootRows) -ceq
                $schemaFingerprintSha256) {
                throw "MySQL $version permission root $($rootDrift.Name) drift was not detected."
            }
            $null = Invoke-Client -Profile $profile -Database $database `
                -Sql $rootDrift.Restore
            $restoredRootRows = Invoke-Client -Profile $profile `
                -Database $database -Sql $schemaFingerprintSql
            if ((Get-CanonicalRowsSha256 $restoredRootRows) -cne
                $schemaFingerprintSha256) {
                throw "MySQL $version permission root $($rootDrift.Name) fingerprint did not restore."
            }
        }

        $null = Invoke-Client -Profile $profile -Database $database -Sql @"
ALTER TABLE fbs_board_attr_event_v1
  DROP FOREIGN KEY fk_board_attr_event_journey;
ALTER TABLE fbs_board_attr_event_v1
  ADD CONSTRAINT fk_board_attr_event_journey
  FOREIGN KEY (same_binding_key)
  REFERENCES fbs_board_attr_journey_v1 (same_binding_key)
  ON DELETE CASCADE ON UPDATE RESTRICT;
"@
        $cascadeRows = Invoke-Client -Profile $profile `
            -Database $database -Sql $schemaFingerprintSql
        $cascadeSha256 = Get-CanonicalRowsSha256 $cascadeRows
        $cascadeRule = Invoke-Client -Profile $profile `
            -Database $database -Sql @"
SELECT CONCAT_WS('|',update_rule,delete_rule,match_option)
FROM information_schema.referential_constraints
WHERE constraint_schema=DATABASE()
  AND constraint_name='fk_board_attr_event_journey';
"@
        if ($cascadeRule -notmatch '^(RESTRICT|NO ACTION)\|CASCADE\|' -or
            $cascadeSha256 -ceq $schemaFingerprintSha256) {
            throw "MySQL $version cascading FK drift was not detected."
        }
        $null = Invoke-Client -Profile $profile -Database $database -Sql @"
ALTER TABLE fbs_board_attr_event_v1
  DROP FOREIGN KEY fk_board_attr_event_journey;
ALTER TABLE fbs_board_attr_event_v1
  ADD CONSTRAINT fk_board_attr_event_journey
  FOREIGN KEY (same_binding_key)
  REFERENCES fbs_board_attr_journey_v1 (same_binding_key);
"@
        $restoredRule = Invoke-Client -Profile $profile `
            -Database $database -Sql @"
SELECT CONCAT_WS('|',update_rule,delete_rule,match_option)
FROM information_schema.referential_constraints
WHERE constraint_schema=DATABASE()
  AND constraint_name='fk_board_attr_event_journey';
"@
        if ($restoredRule -notmatch
            '^(RESTRICT|NO ACTION)\|(RESTRICT|NO ACTION)\|') {
            throw "MySQL $version restrictive FK rule did not restore."
        }

        $results.Add([pscustomobject]@{
            version = $version
            exactShape = $shape
            migrationRerun = 'PASS'
            append = 'PASS'
            aggregate = $aggregate
            updateRejected = 'PASS'
            deleteRejected = 'PASS'
            extraTriggerFingerprintRejected = 'PASS'
            cascadingFkFingerprintRejected = 'PASS'
            permissionSemanticFingerprintRejected = 'PASS'
            workerPyMySqlOrchestration = $workerProof
            authoritativeProductCredit = 0
            schemaFingerprintSha256 = $schemaFingerprintSha256
        })
    }
    finally {
        if ($null -ne $process -and -not $process.HasExited) {
            & $mysqladmin '--no-defaults' '--protocol=TCP' `
                '--host=127.0.0.1' "--port=$port" '--user=root' shutdown `
                2>$null | Out-Null
            $process.WaitForExit(15000) | Out-Null
        }
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
            $process.WaitForExit(10000) | Out-Null
        }
        if (Test-Path -LiteralPath $runRoot -PathType Container) {
            $resolvedRunRoot = [System.IO.Path]::GetFullPath(
                (Resolve-Path -LiteralPath $runRoot).Path)
            if (-not $resolvedRunRoot.StartsWith(
                    $expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing cleanup outside $isolatedRoot"
            }
            Remove-Item -LiteralPath $resolvedRunRoot -Recurse -Force
        }
    }
}

$report = [ordered]@{
    schema = 'fbsir.independent-board.attribution-v1-dual-mysql-it/v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    migration = 'public_init_043'
    migrationSha256 = $migrationSha
    historicalPublicInit037Sha256 = $historicalSha
    officialIdentity = [ordered]@{
        productId = 'fbsir-eight-seat-board'
        packageId = 'fbsir-eight-seat-board'
        agentName = 'board-convener'
        marketplace = 'experts'
        listedManifestVersion = '26.7.21'
        embeddedContractVersion = '26.7.20'
    }
    results = @($results)
    status = if ($results.Count -eq $Versions.Count) { 'PASS' } else { 'FAIL' }
}
[System.IO.File]::WriteAllText(
    $receiptFullPath,
    (($report | ConvertTo-Json -Depth 8) + [Environment]::NewLine),
    [System.Text.UTF8Encoding]::new($false))
Write-Output ($report | ConvertTo-Json -Depth 8)
