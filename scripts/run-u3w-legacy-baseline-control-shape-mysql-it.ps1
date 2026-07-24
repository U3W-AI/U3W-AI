[CmdletBinding()]
param(
    [switch]$AllowDestructiveTest,
    [string]$OutputPath = '',
    [string]$PythonPath = $(Join-Path $env:USERPROFILE (
        '.cache\codex-runtimes\codex-primary-runtime\dependencies' +
        '\python\python.exe')),
    [string]$PythonDependencyRoot = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AllowDestructiveTest) {
    throw 'Explicit -AllowDestructiveTest consent is required.'
}

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $PythonDependencyRoot) {
    $PythonDependencyRoot = Join-Path $repoRoot 'work\python-deps'
}
$workerPath = Join-Path $PSScriptRoot 'u3w-legacy-baseline-remote.py'
$toolRoot = Join-Path $env:USERPROFILE (
    '.cache\u3w-mysql-toolchain\mysql-8.0.45-winx64\bin')
$mysqld = Join-Path $toolRoot 'mysqld.exe'
$mysql = Join-Path $toolRoot 'mysql.exe'
$mysqladmin = Join-Path $toolRoot 'mysqladmin.exe'
$testRoot = 'C:\u3w-w1a-baseline-shape-it'
$runRoot = Join-Path $testRoot ([guid]::NewGuid().ToString('N'))
$dataRoot = Join-Path $runRoot 'data'
$process = $null

foreach ($required in @(
        $workerPath, $mysqld, $mysql, $mysqladmin, $PythonPath)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "required integration-test dependency is absent: $required"
    }
}

function Invoke-Client {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $output = @(
        & $mysql '--no-defaults' '--protocol=TCP' '--host=127.0.0.1' `
            "--port=$port" '--user=root' '--batch' '--skip-column-names' `
            '--raw' '--default-character-set=utf8mb4' '--execute' $Sql 2>&1
    )
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL client failed: $($output -join "`n")"
    }
    return $output
}

function Get-DdlLiteral {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Name
    )
    $pattern = '(?s)' + [regex]::Escape($Name) + '\s*=\s*"""(.*?)"""'
    $match = [regex]::Match($Source, $pattern)
    if (-not $match.Success) {
        throw "cannot extract $Name from baseline worker"
    }
    return $match.Groups[1].Value.Trim()
}

$listener = [Net.Sockets.TcpListener]::new(
    [Net.IPAddress]::Loopback, 0)
try {
    $listener.Start()
    $port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
}
finally {
    $listener.Stop()
}

New-Item -ItemType Directory -Force -Path $dataRoot | Out-Null
try {
    $priorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $initialize = @(
            & $mysqld '--no-defaults' '--initialize-insecure' `
                "--datadir=$dataRoot" '--console' 2>&1
        )
        $initializeExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $priorPreference
    }
    if ($initializeExitCode -ne 0) {
        throw "mysqld initialization failed: $($initialize -join "`n")"
    }
    $stdoutPath = Join-Path $runRoot 'mysql.stdout.log'
    $stderrPath = Join-Path $runRoot 'mysql.stderr.log'
    $process = Start-Process -FilePath $mysqld -ArgumentList @(
        '--no-defaults',
        "--datadir=$dataRoot",
        '--bind-address=127.0.0.1',
        "--port=$port",
        '--skip-networking=0',
        '--console'
    ) -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath `
        -WindowStyle Hidden -PassThru
    $ready = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        $priorPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & $mysqladmin '--no-defaults' '--protocol=TCP' `
                '--host=127.0.0.1' "--port=$port" '--user=root' ping `
                2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $ready = $true
                break
            }
        }
        finally {
            $ErrorActionPreference = $priorPreference
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) {
        throw "isolated MySQL did not start: $(
            Get-Content -Raw -LiteralPath $stderrPath)"
    }

    $workerSource = Get-Content -Raw -Encoding UTF8 -LiteralPath $workerPath
    $migrationDdl = Get-DdlLiteral $workerSource 'MIGRATION_TABLE_SQL'
    $receiptDdl = Get-DdlLiteral $workerSource 'RECEIPT_TABLE_SQL'
    Invoke-Client @"
CREATE DATABASE fbsir CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fbsir;
$migrationDdl;
$receiptDdl;
"@ | Out-Null

    $metadata = Invoke-Client @"
SELECT CONCAT_WS(CHAR(9),'COL',table_name,ordinal_position,column_name,
  column_type,is_nullable,column_default IS NULL,
  COALESCE(column_default,'<NULL>'),extra,
  COALESCE(character_set_name,''),COALESCE(collation_name,''),
  COALESCE(generation_expression,''))
FROM information_schema.columns
WHERE table_schema='fbsir'
ORDER BY BINARY table_name,ordinal_position;
SELECT CONCAT_WS(CHAR(9),'IDX',table_name,index_name,non_unique,seq_in_index,
  IF(column_name IS NULL,'<NULL>',column_name),
  IF(collation IS NULL,'<NULL>',collation),
  IF(sub_part IS NULL,'<NULL>',sub_part),
  IF(packed IS NULL,'<NULL>',packed),COALESCE(nullable,''),index_type,
  comment,index_comment,is_visible,
  IF(expression IS NULL,'<NULL>',expression))
FROM information_schema.statistics
WHERE table_schema='fbsir'
ORDER BY BINARY table_name,BINARY index_name,seq_in_index;
SELECT CONCAT_WS(CHAR(9),'CON',table_name,constraint_name,constraint_type,
  enforced)
FROM information_schema.table_constraints
WHERE table_schema='fbsir'
ORDER BY BINARY table_name,BINARY constraint_name;
SELECT CONCAT_WS(CHAR(9),'CHK',tc.table_name,cc.constraint_name,
  HEX(cc.check_clause))
FROM information_schema.check_constraints cc
JOIN information_schema.table_constraints tc
  ON tc.constraint_schema=cc.constraint_schema
 AND tc.constraint_name=cc.constraint_name
 AND tc.constraint_type='CHECK'
WHERE tc.table_schema='fbsir'
ORDER BY BINARY tc.table_name,BINARY cc.constraint_name;
SELECT CONCAT_WS(CHAR(9),'TAB',table_name,table_type,engine,table_collation,
  COALESCE(create_options,''),HEX(table_comment))
FROM information_schema.tables
WHERE table_schema='fbsir'
ORDER BY BINARY table_name;
"@
    $showCreate = Invoke-Client @"
SHOW CREATE TABLE fbsir.u3w_schema_migration;
SHOW CREATE TABLE fbsir.u3w_legacy_schema_baseline_receipt_v2;
"@
    $pythonProgram = @'
import importlib.util
import json
import pathlib
import sys
import types

worker_path = pathlib.Path(sys.argv[1])
port = int(sys.argv[2])
sys.modules["fcntl"] = types.SimpleNamespace()
spec = importlib.util.spec_from_file_location("baseline_worker", worker_path)
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)
mysql = worker.Mysql({
    "host": "127.0.0.1",
    "port": port,
    "user": "root",
    "password": "",
})
try:
    worker.assert_control_shapes(mysql)
    result = {
        "exactShapeVerified": True,
        "controlOverlaySha256": worker.control_overlay_sha256(mysql),
    }
finally:
    mysql.close()
print(json.dumps(result, sort_keys=True, separators=(",", ":")))
'@
    $priorPythonPath = $env:PYTHONPATH
    try {
        $env:PYTHONPATH = $PythonDependencyRoot
        $shapeResultRaw = @(
            $pythonProgram | & $PythonPath - $workerPath $port 2>&1
        )
        if ($LASTEXITCODE -ne 0) {
            throw "worker exact-shape verification failed: $(
                $shapeResultRaw -join "`n")"
        }
        $shapeResult = ($shapeResultRaw -join "`n") | ConvertFrom-Json
    }
    finally {
        $env:PYTHONPATH = $priorPythonPath
    }
    $receipt = [ordered]@{
        schema = 'fbsir.u3wLegacyBaselineControlShapeMysqlItReceipt.v1'
        serverVersion = @(Invoke-Client 'SELECT @@version;')[0]
        workerSha256 = (Get-FileHash -LiteralPath $workerPath `
            -Algorithm SHA256).Hash.ToLowerInvariant()
        metadataRows = @($metadata)
        showCreateRows = @($showCreate)
        exactShapeVerified = $shapeResult.exactShapeVerified
        controlOverlaySha256 = $shapeResult.controlOverlaySha256
        isolatedNetworking = 'loopback-only'
        isolatedDataRemoved = $true
        generatedAt = [DateTime]::UtcNow.ToString('o')
    }
    $json = $receipt | ConvertTo-Json -Depth 8
    if ($OutputPath) {
        $resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
        $outputDirectory = Split-Path -Parent $resolvedOutput
        New-Item -ItemType Directory -Force -Path $outputDirectory |
            Out-Null
        [IO.File]::WriteAllText(
            $resolvedOutput, $json + "`n", [Text.UTF8Encoding]::new($false))
    }
    $json
}
finally {
    if ($process -and -not $process.HasExited) {
        $priorPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & $mysqladmin '--no-defaults' '--protocol=TCP' `
                '--host=127.0.0.1' "--port=$port" '--user=root' shutdown `
                2>$null | Out-Null
        }
        finally {
            $ErrorActionPreference = $priorPreference
        }
        $process.WaitForExit(10000) | Out-Null
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
        }
    }
    $resolvedRun = [IO.Path]::GetFullPath($runRoot)
    $resolvedRoot = [IO.Path]::GetFullPath($testRoot) +
        [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedRun.StartsWith(
            $resolvedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'refusing unsafe isolated integration-test cleanup'
    }
    if (Test-Path -LiteralPath $resolvedRun) {
        Remove-Item -LiteralPath $resolvedRun -Recurse -Force
    }
}
