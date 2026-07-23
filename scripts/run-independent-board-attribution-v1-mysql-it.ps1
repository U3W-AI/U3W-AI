[CmdletBinding()]
param(
    [switch]$AllowDestructiveTest,
    [ValidateSet('8.0.30', '8.4.8')]
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
  (menu_name,parent_id,order_num,path,route_name,menu_type,perms)
VALUES
  ('Independent Board Admin',0,5,'independent-board-admin',
   'IndependentBoardAdmin','M','');
"@
        $null = Invoke-Client -Profile $profile -Sql $prerequisiteSql
        Invoke-Migration -Profile $profile -Database $database
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
      AND trigger_name IN
        ('trg_board_attr_event_v1_no_update',
         'trg_board_attr_event_v1_no_delete')),
  (SELECT COUNT(*) FROM sys_menu
    WHERE BINARY perms=BINARY 'board:attribution:query'),
  (SELECT COUNT(*) FROM u3w_schema_migration
    WHERE version='20260723_independent_board_attribution_v1_043'));
"@
        $shapeParts = $shape.Split('|')
        if ($shapeParts.Count -ne 5 -or
            $shapeParts[0] -ne $version -or
            $shapeParts[1] -ne '2' -or
            $shapeParts[2] -ne '2' -or
            $shapeParts[3] -ne '1' -or
            $shapeParts[4] -ne '1') {
            throw "MySQL $version exact shape proof failed: $shape"
        }

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

        $results.Add([pscustomobject]@{
            version = $version
            exactShape = $shape
            migrationRerun = 'PASS'
            append = 'PASS'
            aggregate = $aggregate
            updateRejected = 'PASS'
            deleteRejected = 'PASS'
            authoritativeProductCredit = 0
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
