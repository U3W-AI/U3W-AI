[CmdletBinding()]
param(
    [switch]$AllowDestructiveTest,
    [ValidateSet('8.0.30', '8.0.45', '8.4.8')]
    [string[]]$Versions = @('8.0.30', '8.4.8'),
    [switch]$ReadbackZeroWrite,
    [string]$ReceiptPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AllowDestructiveTest) {
    throw 'Explicit -AllowDestructiveTest consent is required.'
}

$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$migration043 = Join-Path $repoRoot 'sql\update_20260723_independent_board_attribution_v1.sql'
$migration044 = Join-Path $repoRoot 'sql\update_20260823_independent_board_attribution_identity_registry.sql'
$toolchainRoot = Join-Path $env:USERPROFILE '.cache\u3w-mysql-toolchain'
$isolatedRoot = 'C:\u3w-w05-registry-it'
if ([string]::IsNullOrWhiteSpace($ReceiptPath)) {
    $ReceiptPath = Join-Path $repoRoot 'reports\independent-board\w05-attribution-identity-registry-dual-mysql-latest.json'
}
$receiptFullPath = [IO.Path]::GetFullPath($ReceiptPath)
$null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $receiptFullPath)

foreach ($path in @($migration043, $migration044)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required migration is missing: $path"
    }
}
$migration043Sha = (Get-FileHash -LiteralPath $migration043 -Algorithm SHA256).Hash.ToLowerInvariant()
$migration044Sha = (Get-FileHash -LiteralPath $migration044 -Algorithm SHA256).Hash.ToLowerInvariant()
if ($migration043Sha -cne 'ca9c86c79617543c19bc9a6141afef18918320c65091b418f761ece19b5c6055') {
    throw "Historical public_init_043 drifted: $migration043Sha"
}

function Get-EphemeralPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally { $listener.Stop() }
}

function Resolve-ToolchainBase {
    param([Parameter(Mandatory = $true)][string]$Version)
    $candidates = @(
        (Join-Path $toolchainRoot "mysql-$Version-winx64"),
        "E:\ddh\u3w-independent-board-control-plane\work\toolchains\mysql-$Version-winx64",
        "E:\ddh\u3w-independent-board-control-plane\work\mysql-toolchains\mysql-$Version\mysql-$Version-winx64",
        "E:\ddh\u3w-independent-board-control-plane\work\mysql-$Version-runtime\mysql-$Version-winx64"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin\mysqld.exe') -PathType Leaf) {
            return [IO.Path]::GetFullPath($candidate)
        }
    }
    throw "MySQL $Version toolchain was not found in the reviewed candidate roots."
}

function Get-TextSha256 {
    param([Parameter(Mandatory = $true)][string]$Text)
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text)
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function Invoke-Client {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$Database = '',
        [switch]$ExpectFailure
    )
    $arguments = @('--no-defaults', '--protocol=TCP', '--host=127.0.0.1',
        "--port=$($Profile.Port)", '--user=root', '--default-character-set=utf8mb4',
        '--batch', '--skip-column-names')
    if ($Database) { $arguments += "--database=$Database" }
    $arguments += @('--execute', $Sql)
    $old = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Profile.MySql @arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $old }
    $text = (($output | ForEach-Object { $_.ToString() }) -join "`n").Trim()
    if ($ExpectFailure) {
        if ($exitCode -eq 0) { throw 'Expected MySQL statement to fail.' }
        return $text
    }
    if ($exitCode -ne 0) { throw "MySQL statement failed: $text" }
    return $text
}

function Invoke-MigrationFile {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Path
    )
    $source = $Path.Replace('\', '/')
    return Invoke-Client -Profile $Profile -Database $Database -Sql "source $source;"
}

function Initialize-043Database {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][string]$Database
    )
    $null = Invoke-Client -Profile $Profile -Sql @"
CREATE DATABASE $Database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE $Database;
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
    $null = Invoke-MigrationFile -Profile $Profile -Database $Database -Path $migration043
    $null = Invoke-Client -Profile $Profile -Database $Database -Sql @"
INSERT INTO u3w_schema_migration(version,description)
VALUES('public_init_043',
 'APPLIED:Independent Board exact official experts attribution v1');
"@
}

function Get-ProfileTransactionSql {
    param(
        [Parameter(Mandatory = $true)][char]$BindingChar,
        [Parameter(Mandatory = $true)][char]$EventChar,
        [Parameter(Mandatory = $true)][char]$ReceiptChar,
        [Parameter(Mandatory = $true)][char]$TenantChar,
        [Parameter(Mandatory = $true)][char]$JourneyChar,
        [Parameter(Mandatory = $true)][string]$ServerBinding,
        [Parameter(Mandatory = $true)][string]$Listed,
        [Parameter(Mandatory = $true)][string]$Embedded,
        [Parameter(Mandatory = $true)][string]$HostFamily,
        [Parameter(Mandatory = $true)][string]$Terminal,
        [Parameter(Mandatory = $true)][string]$RequestSource,
        [int]$ProductCredit = 0
    )
    $binding = ([string]$BindingChar) * 64
    $event = ([string]$EventChar) * 64
    $receipt = ([string]$ReceiptChar) * 64
    $tenant = ([string]$TenantChar) * 64
    $journey = ([string]$JourneyChar) * 64
    $canonical = 'c' * 64
    $eventDigest = ([string]$EventChar) * 64
    $signature = 'd' * 64
    $nonce = ([string]$ReceiptChar) * 64
    return @"
START TRANSACTION;
INSERT INTO fbs_board_attr_journey_v1
  (same_binding_key,contract_id,tenant_subject_digest,server_binding_id,
   journey_id,product_id,listed_manifest_version,embedded_contract_version,
   channel,terminal,host_version,traffic_class,intent_family,
   last_sequence_no,last_event_digest,head_version)
VALUES
  ('$binding','FBSIR_INDEPENDENT_BOARD_W1A_V1','$tenant','$ServerBinding',
   '$journey','fbsir-eight-seat-board','$Listed','$Embedded',
   'OFFICIAL_EXPERTS','$Terminal','UNKNOWN','SYNTHETIC','UNKNOWN',0,'',0);
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
   '$ServerBinding','$tenant','ENTRY_OBSERVED',1,'2026-08-17 14:00:00.000',
   'fbsir-eight-seat-board','fbsir-eight-seat-board','board-convener',
   'experts','listed_runtime_state','$Listed','$Embedded','$HostFamily','UNKNOWN',
   '$Terminal','OFFICIAL_EXPERTS','$RequestSource','unknown','UNKNOWN',
   'PACKAGE_SCENE_ROUTER','scene-lexicon-26.8.19-core.1','UNKNOWN','UNKNOWN',
   'SYNTHETIC','API2_SERVER_CLASSIFIER_V1','SUCCESS','','','$canonical',
   '$eventDigest','wave1-k1','$signature','$nonce',
   '2026-08-23 05:00:00.000','2026-08-23 05:02:00.000',$ProductCredit);
COMMIT;
"@
}

function Invoke-ReadbackZeroWrite {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Version
    )
    $envNames = @(
        'INDEPENDENT_BOARD_MYSQL_IT_URL',
        'INDEPENDENT_BOARD_MYSQL_IT_USERNAME',
        'INDEPENDENT_BOARD_MYSQL_IT_PASSWORD',
        'INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP',
        'INDEPENDENT_BOARD_MYSQL_IT_VERSION')
    $snapshot = @{}
    $processEnvironment = [Environment]::GetEnvironmentVariables('Process')
    foreach ($name in $envNames) {
        $snapshot[$name] = [pscustomobject]@{
            exists = $processEnvironment.Contains($name)
            value = [Environment]::GetEnvironmentVariable($name, 'Process')
        }
    }
    $reportRoot = Join-Path $repoRoot 'FBSir-business\target\surefire-reports'
    $startedAt = [DateTimeOffset]::UtcNow
    try {
        $null = Invoke-Client -Profile $Profile -Database $Database -Sql @"
CREATE TABLE w05e_readback_snapshot_probe (
  id INT NOT NULL PRIMARY KEY,
  probe_value INT NOT NULL
) ENGINE=InnoDB;
INSERT INTO w05e_readback_snapshot_probe(id,probe_value) VALUES (1,0);
"@
        $serverReadOnlyFailure = Invoke-Client -Profile $Profile `
            -Database $Database -ExpectFailure -Sql @"
START TRANSACTION READ ONLY;
UPDATE fbs_board_attr_event_v1 SET event_id=event_id LIMIT 1;
"@
        if ($serverReadOnlyFailure -notmatch '(?is)ERROR\s+1792\s*\(25006\)') {
            throw "MySQL $Version did not enforce server read-only DML with ERROR 1792 (25006): $serverReadOnlyFailure"
        }
        [Environment]::SetEnvironmentVariable(
            'INDEPENDENT_BOARD_MYSQL_IT_URL',
            "jdbc:mysql://127.0.0.1:$($Profile.Port)/${Database}?useAffectedRows=false&connectionTimeZone=Asia%2FShanghai&useSSL=false&allowPublicKeyRetrieval=true",
            'Process')
        [Environment]::SetEnvironmentVariable('INDEPENDENT_BOARD_MYSQL_IT_USERNAME', 'root', 'Process')
        [Environment]::SetEnvironmentVariable('INDEPENDENT_BOARD_MYSQL_IT_PASSWORD', '', 'Process')
        [Environment]::SetEnvironmentVariable('INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP', 'true', 'Process')
        [Environment]::SetEnvironmentVariable('INDEPENDENT_BOARD_MYSQL_IT_VERSION', $Version, 'Process')
        Write-Host "Running W05E MySQL zero-write readback IT on $Version"
        $mavenOutput = & mvn -pl FBSir-business -am `
            '-Dtest=IndependentBoardAttributionReadbackMysqlIT' `
            '-Dsurefire.failIfNoSpecifiedTests=false' test
        $mavenOutput | Write-Host
        if ($LASTEXITCODE -ne 0) {
            throw "W05E readback MySQL IT failed for ${Version} with Maven exit code $LASTEXITCODE"
        }
        $markerLines = [Collections.Generic.List[string]]::new()
        if (Test-Path -LiteralPath $reportRoot -PathType Container) {
            $reports = Get-ChildItem -LiteralPath $reportRoot -File | Where-Object {
                $_.LastWriteTimeUtc -ge $startedAt.UtcDateTime.AddSeconds(-2)
            }
            foreach ($report in $reports) {
                $content = Get-Content -LiteralPath $report.FullName -Raw -Encoding UTF8
                foreach ($match in [regex]::Matches($content, 'W05E_ZERO_WRITE_JSON=(\{.*?\})(?:\r?\n|<|$)')) {
                    $markerLines.Add($match.Groups[1].Value)
                }
            }
        }
        if ($markerLines.Count -ne 7) {
            throw "W05E readback marker count for $Version must be exactly 7, got $($markerLines.Count)."
        }
        $cases = @($markerLines | ForEach-Object {
            try { $_ | ConvertFrom-Json }
            catch { throw "W05E readback marker is invalid JSON: $_" }
        })
        $expected = @('exact', 'not_found', 'collision_cross_row', 'collision_digest',
            'invalid_signature', 'unavailable_after_first_read', 'repeatable_snapshot')
        $actual = @($cases | ForEach-Object { [string]$_.id })
        if (($actual -join '|') -cne ($expected -join '|')) {
            throw "W05E readback case order/set drifted for ${Version}: $($actual -join ',')"
        }
        foreach ($case in $cases) {
            if (-not [bool]$case.projectionEqual -or [int]$case.dmlStatementCount -ne 0 -or
                [int]$case.ddlStatementCount -ne 0 -or [bool]$case.productCreditEligible -or
                [int]$case.nonDatabase.sensitiveLogMatchCount -ne 0 -or
                [bool]$case.nonDatabase.redisCapabilityReachable -or
                -not [bool]$case.nonDatabase.filesystemStateEqual -or
                [bool]$case.nonDatabase.journalCapabilityReachable -or
                [bool]$case.nonDatabase.publisherOutboxCapabilityReachable) {
                throw "W05E zero-write assertions failed for $Version case $($case.id)."
            }
            $invalidSignature = [string]$case.id -eq 'invalid_signature'
            if ($invalidSignature) {
                if ([int]$case.eventSelectCount -ne 0 -or
                    [int]$case.receiptSelectCount -ne 0 -or
                    [bool]$case.connectionReadOnly -or
                    [bool]$case.repeatableRead) {
                    throw "Invalid-signature case reached the database for $Version."
                }
            }
            elseif ([int]$case.eventSelectCount -ne 1 -or
                [int]$case.receiptSelectCount -ne 1 -or
                -not [bool]$case.readOnlyTransactionObserved -or
                -not [bool]$case.readOnlyConnectionObserved -or
                -not [bool]$case.connectionReadOnly -or
                -not [bool]$case.repeatableRead) {
                throw "Readback transaction evidence is incomplete for $Version case $($case.id)."
            }
            if ([string]$case.id -eq 'repeatable_snapshot' -and
                (-not [bool]$case.concurrentCommitObserved -or
                 -not [bool]$case.sameSnapshotObserved -or
                 [int]$case.externalFixtureWriteCount -ne 2)) {
                throw "Repeatable-snapshot concurrency proof failed for $Version."
            }
        }
        return [ordered]@{
            schemaVersion = 'fbsir.independentBoardAuthoritativeReadbackMysqlZeroWrite.v1'
            mysqlVersion = $Version
            transactionIsolation = 'REPEATABLE-READ'
            serverReadOnlyDmlErrorCode = 1792
            serverReadOnlyDmlSqlState = '25006'
            cases = $cases
            cleanup = [ordered]@{
                ownedByOuterRunner = $true
                serverProcessStopped = $false
                portClosed = $false
                workDirectoryCleaned = $false
                processEnvironmentRestored = $true
            }
            databaseMigrationAdded = $false
            productionChanged = $false
            status = 'PASS'
        }
    }
    finally {
        foreach ($name in $envNames) {
            $entry = $snapshot[$name]
            [Environment]::SetEnvironmentVariable(
                $name,
                $(if ([bool]$entry.exists) { [string]$entry.value } else { $null }),
                'Process')
        }
        foreach ($name in $envNames) {
            $entry = $snapshot[$name]
            $actualValue = [Environment]::GetEnvironmentVariable($name, 'Process')
            if ([string]$actualValue -cne [string]$entry.value) {
                throw "W05E readback environment restoration failed for $name."
            }
        }
    }
}

$results = [Collections.Generic.List[object]]::new()
$runId = [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')

foreach ($version in $Versions) {
    $base = Resolve-ToolchainBase -Version $version
    $bin = Join-Path $base 'bin'
    $mysqld = Join-Path $bin 'mysqld.exe'
    $mysql = Join-Path $bin 'mysql.exe'
    $mysqladmin = Join-Path $bin 'mysqladmin.exe'
    foreach ($tool in @($mysqld, $mysql, $mysqladmin)) {
        if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
            throw "MySQL $version toolchain is incomplete: $tool"
        }
    }
    $runRoot = [IO.Path]::GetFullPath((Join-Path $isolatedRoot "$runId-$($version.Replace('.',''))"))
    $allowedPrefix = [IO.Path]::GetFullPath($isolatedRoot).TrimEnd('\') + '\'
    if (-not $runRoot.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe isolated root: $runRoot"
    }
    $null = New-Item -ItemType Directory -Force -Path $runRoot
    $dataRoot = Join-Path $runRoot 'data'
    $errorLog = Join-Path $runRoot 'mysql-error.log'
    $pidFile = Join-Path $runRoot 'mysql.pid'
    $port = Get-EphemeralPort
    $process = $null
    try {
        $initOutput = & $mysqld '--no-defaults' '--initialize-insecure' "--basedir=$base" "--datadir=$dataRoot" 2>&1
        if ($LASTEXITCODE -ne 0) { throw "MySQL initialize failed: $initOutput" }
        $process = Start-Process -FilePath $mysqld -PassThru -WindowStyle Hidden -ArgumentList @(
            '--no-defaults', "--basedir=$base", "--datadir=$dataRoot", "--port=$port",
            '--bind-address=127.0.0.1', '--skip-networking=0', "--pid-file=$pidFile",
            "--log-error=$errorLog", '--mysqlx=OFF', '--secure-file-priv=NULL')
        $ready = $false
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            Start-Sleep -Milliseconds 500
            & $mysqladmin '--no-defaults' '--protocol=TCP' '--host=127.0.0.1' "--port=$port" '--user=root' ping 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
            if ($process.HasExited) { break }
        }
        if (-not $ready) { throw "MySQL $version did not become ready." }
        $profile = [pscustomobject]@{ Version=$version; Port=$port; MySql=$mysql }
        $database = "w05_$($version.Replace('.',''))"
        Initialize-043Database -Profile $profile -Database $database
        $predecessorCheckSqlSha = Invoke-Client -Profile $profile `
            -Database $database -Sql @"
SELECT SHA2(CONCAT(GROUP_CONCAT(
  CONCAT(tc.table_name,'|',cc.check_clause)
  ORDER BY tc.table_name,tc.constraint_name SEPARATOR '\n'),'\n'),256)
FROM information_schema.table_constraints tc
INNER JOIN information_schema.check_constraints cc
 ON cc.constraint_schema=tc.constraint_schema
 AND cc.constraint_name=tc.constraint_name
WHERE tc.constraint_schema=DATABASE()
 AND tc.constraint_name IN
  ('chk_board_attr_journey_versions','chk_board_attr_event_versions');
"@

        $legacySql = Get-ProfileTransactionSql -BindingChar 'a' -EventChar '1' `
            -ReceiptChar '2' -TenantChar '3' -JourneyChar '4' `
            -ServerBinding 'srv_wave1Binding01' -Listed '26.7.21' `
            -Embedded '26.7.20' -HostFamily 'WORKBUDDY' -Terminal 'UNKNOWN' `
            -RequestSource 'UNKNOWN'
        $null = Invoke-Client -Profile $profile -Database $database -Sql $legacySql
        $legacyBefore = Invoke-Client -Profile $profile -Database $database -Sql @"
SELECT CONCAT_WS('|',COUNT(*),MIN(listed_manifest_version),
  MIN(embedded_contract_version),MAX(authoritative_product_credit))
FROM fbs_board_attr_event_v1;
"@

        $null = Invoke-MigrationFile -Profile $profile -Database $database -Path $migration044
        $null = Invoke-Client -Profile $profile -Database $database -Sql @"
INSERT INTO u3w_schema_migration(version,description)
VALUES('public_init_044',
 'APPLIED:Independent Board exact legacy and current attribution identity registry');
"@
        $null = Invoke-MigrationFile -Profile $profile -Database $database -Path $migration044

        $currentWorkBuddy = Get-ProfileTransactionSql -BindingChar 'b' -EventChar '5' `
            -ReceiptChar '6' -TenantChar '3' -JourneyChar '4' `
            -ServerBinding 'srv_wave1Binding01' -Listed '26.8.19' `
            -Embedded '26.8.19' -HostFamily 'WORKBUDDY' -Terminal 'UNKNOWN' `
            -RequestSource 'UNKNOWN'
        $currentWorkBuddyAi = Get-ProfileTransactionSql -BindingChar 'c' -EventChar '7' `
            -ReceiptChar '8' -TenantChar '9' -JourneyChar 'a' `
            -ServerBinding 'srv_wave1Binding02' -Listed '26.8.19' `
            -Embedded '26.8.19' -HostFamily 'WORKBUDDYAI' -Terminal 'UNKNOWN' `
            -RequestSource 'UNKNOWN'
        $null = Invoke-Client -Profile $profile -Database $database -Sql $currentWorkBuddy
        $null = Invoke-Client -Profile $profile -Database $database -Sql $currentWorkBuddyAi

        $negativeCases = [Collections.Generic.List[string]]::new()
        $negativeCases.Add((Get-ProfileTransactionSql `
                -BindingChar 'd' -EventChar 'a' -ReceiptChar 'b' `
                -TenantChar 'c' -JourneyChar 'd' -ServerBinding 'srv_wave1Binding03' `
                -Listed '26.8.19' -Embedded '26.7.20' -HostFamily 'WORKBUDDY' `
                -Terminal 'UNKNOWN' -RequestSource 'UNKNOWN'))
        $negativeCases.Add((Get-ProfileTransactionSql `
                -BindingChar 'e' -EventChar 'b' -ReceiptChar 'c' `
                -TenantChar 'd' -JourneyChar 'e' -ServerBinding 'srv_wave1Binding04' `
                -Listed '26.7.21' -Embedded '26.7.20' -HostFamily 'WORKBUDDYAI' `
                -Terminal 'UNKNOWN' -RequestSource 'UNKNOWN'))
        $negativeCases.Add((Get-ProfileTransactionSql `
                -BindingChar 'f' -EventChar 'c' -ReceiptChar 'd' `
                -TenantChar 'e' -JourneyChar 'f' -ServerBinding 'srv_wave1Binding05' `
                -Listed '26.8.20' -Embedded '26.8.20' -HostFamily 'WORKBUDDY' `
                -Terminal 'UNKNOWN' -RequestSource 'UNKNOWN'))
        $negativeCases.Add((Get-ProfileTransactionSql `
                -BindingChar '1' -EventChar 'd' -ReceiptChar 'e' `
                -TenantChar 'f' -JourneyChar '1' -ServerBinding 'srv_wave1Binding06' `
                -Listed '26.8.19' -Embedded '26.8.19' -HostFamily 'WORKBUDDY' `
                -Terminal 'UNKNOWN' -RequestSource 'UNKNOWN' -ProductCredit 1))
        foreach ($negativeSql in $negativeCases) {
            $failure = Invoke-Client -Profile $profile -Database $database `
                -Sql $negativeSql -ExpectFailure
            if ($failure -notmatch 'Check constraint') {
                throw "MySQL $version negative profile did not fail by CHECK: $failure"
            }
        }

        $shape = Invoke-Client -Profile $profile -Database $database -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM fbs_board_attr_journey_v1),
  (SELECT COUNT(*) FROM fbs_board_attr_event_v1),
  (SELECT COUNT(*) FROM fbs_board_attr_event_v1
   WHERE authoritative_product_credit=0),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version IN ('public_init_043','public_init_044',
    '20260723_independent_board_attribution_v1_043',
    '20260823_independent_board_attribution_identity_registry_044')),
  (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
   FROM information_schema.statistics
   WHERE table_schema=DATABASE()
    AND table_name='fbs_board_attr_journey_v1'
    AND index_name='uk_board_attr_journey_identity'));
"@
        $expectedColumns = 'contract_id,tenant_subject_digest,server_binding_id,journey_id,product_id,listed_manifest_version,embedded_contract_version'
        if ($shape -cne "3|3|3|4|$expectedColumns") {
            throw "MySQL $version successor shape mismatch: $shape"
        }
        $legacyAfter = Invoke-Client -Profile $profile -Database $database -Sql @"
SELECT CONCAT_WS('|',COUNT(*),MIN(listed_manifest_version),
  MIN(embedded_contract_version),MAX(authoritative_product_credit))
FROM fbs_board_attr_event_v1 WHERE event_id=REPEAT('1',64);
"@
        if ($legacyBefore -cne '1|26.7.21|26.7.20|0' -or
            $legacyAfter -cne $legacyBefore) {
            throw "MySQL $version legacy row compatibility drifted."
        }
        $checkRows = Invoke-Client -Profile $profile -Database $database -Sql @"
SELECT CONCAT(tc.table_name,'|',cc.check_clause)
FROM information_schema.table_constraints tc
INNER JOIN information_schema.check_constraints cc
 ON cc.constraint_schema=tc.constraint_schema
 AND cc.constraint_name=tc.constraint_name
WHERE tc.constraint_schema=DATABASE()
 AND tc.constraint_name IN
  ('chk_board_attr_journey_versions','chk_board_attr_event_versions')
ORDER BY tc.table_name,tc.constraint_name;
"@
        $checkCanonical = (($checkRows -split "`r?`n") -join "`n") + "`n"
        $checkSha = Get-TextSha256 $checkCanonical
        $successorCheckSqlSha = Invoke-Client -Profile $profile `
            -Database $database -Sql @"
SELECT SHA2(CONCAT(GROUP_CONCAT(
  CONCAT(tc.table_name,'|',cc.check_clause)
  ORDER BY tc.table_name,tc.constraint_name SEPARATOR '\n'),'\n'),256)
FROM information_schema.table_constraints tc
INNER JOIN information_schema.check_constraints cc
 ON cc.constraint_schema=tc.constraint_schema
 AND cc.constraint_name=tc.constraint_name
WHERE tc.constraint_schema=DATABASE()
 AND tc.constraint_name IN
  ('chk_board_attr_journey_versions','chk_board_attr_event_versions');
"@

        $partialDatabase = $database + '_partial'
        Initialize-043Database -Profile $profile -Database $partialDatabase
        $null = Invoke-Client -Profile $profile -Database $partialDatabase -Sql @"
ALTER TABLE fbs_board_attr_journey_v1
 DROP CHECK chk_board_attr_journey_versions,
 ADD CONSTRAINT chk_board_attr_journey_versions CHECK (
  (listed_manifest_version='26.7.21' AND embedded_contract_version='26.7.20')
  OR (listed_manifest_version='26.8.19' AND embedded_contract_version='26.8.19'));
ALTER TABLE fbs_board_attr_journey_v1
 DROP INDEX uk_board_attr_journey_identity,
 ADD UNIQUE KEY uk_board_attr_journey_identity
  (contract_id,tenant_subject_digest,server_binding_id,journey_id,product_id,
   listed_manifest_version,embedded_contract_version);
"@
        $partialCheckSqlShaBefore = Invoke-Client -Profile $profile `
            -Database $partialDatabase -Sql @"
SELECT SHA2(CONCAT(GROUP_CONCAT(
  CONCAT(tc.table_name,'|',cc.check_clause)
  ORDER BY tc.table_name,tc.constraint_name SEPARATOR '\n'),'\n'),256)
FROM information_schema.table_constraints tc
INNER JOIN information_schema.check_constraints cc
 ON cc.constraint_schema=tc.constraint_schema
 AND cc.constraint_name=tc.constraint_name
WHERE tc.constraint_schema=DATABASE()
 AND tc.constraint_name IN
  ('chk_board_attr_journey_versions','chk_board_attr_event_versions');
"@
        $null = Invoke-MigrationFile -Profile $profile -Database $partialDatabase -Path $migration044
        $partialProof = Invoke-Client -Profile $profile -Database $partialDatabase -Sql @"
SELECT CONCAT_WS('|',
 (SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema=DATABASE() AND enforced='YES'
   AND constraint_name IN
    ('chk_board_attr_journey_versions','chk_board_attr_event_versions')),
 (SELECT COUNT(*) FROM u3w_schema_migration
  WHERE version='20260823_independent_board_attribution_identity_registry_044'));
"@
        if ($partialProof -cne '2|1') {
            throw "MySQL $version partial-DDL recovery failed: $partialProof"
        }

        $zeroWriteReceipt = $null
        if ($ReadbackZeroWrite) {
            $zeroWriteReceipt = Invoke-ReadbackZeroWrite -Profile $profile `
                -Database $database -Version $version
        }

        $resultRecord = [pscustomobject]@{
            version = $version
            acceptedProfiles = 3
            rejectedProfiles = $negativeCases.Count
            legacyRowPreserved = $true
            duplicateApply = 'PASS'
            partialDdlRecovery = 'PASS'
            productCreditMax = 0
            exactShape = $shape
            checkClauseSha256 = $checkSha
            predecessorCheckClauseSqlSha256 = $predecessorCheckSqlSha
            successorCheckClauseSqlSha256 = $successorCheckSqlSha
            partialCheckClauseSqlSha256 = $partialCheckSqlShaBefore
            checkClauses = @($checkRows -split "`r?`n")
            readbackZeroWrite = $zeroWriteReceipt
        }
    }
    finally {
        if ($null -ne $process -and -not $process.HasExited) {
            & $mysqladmin '--no-defaults' '--protocol=TCP' '--host=127.0.0.1' "--port=$port" '--user=root' shutdown 2>$null | Out-Null
            $process.WaitForExit(15000) | Out-Null
        }
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
            $process.WaitForExit(10000) | Out-Null
        }
        if (Test-Path -LiteralPath $runRoot -PathType Container) {
            $resolved = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $runRoot).Path)
            if (-not $resolved.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
                throw "Refusing cleanup outside $isolatedRoot"
            }
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
    $processStopped = $null -eq $process -or $process.HasExited
    $portClosed = $false
    $probe = [Net.Sockets.TcpClient]::new()
    try {
        $connect = $probe.ConnectAsync('127.0.0.1', $port)
        $portClosed = -not $connect.Wait(500) -or -not $probe.Connected
    }
    catch { $portClosed = $true }
    finally { $probe.Dispose() }
    $workDirectoryCleaned = -not (Test-Path -LiteralPath $runRoot)
    if (-not $processStopped -or -not $portClosed -or
        -not $workDirectoryCleaned) {
        throw "MySQL $version cleanup failed: processStopped=$processStopped portClosed=$portClosed workDirectoryCleaned=$workDirectoryCleaned"
    }
    if ($null -ne $resultRecord.readbackZeroWrite) {
        $resultRecord.readbackZeroWrite.cleanup.serverProcessStopped = $processStopped
        $resultRecord.readbackZeroWrite.cleanup.portClosed = $portClosed
        $resultRecord.readbackZeroWrite.cleanup.workDirectoryCleaned = $workDirectoryCleaned
    }
    $resultRecord | Add-Member -NotePropertyName cleanup -NotePropertyValue ([ordered]@{
        serverProcessStopped = $processStopped
        portClosed = $portClosed
        workDirectoryCleaned = $workDirectoryCleaned
    })
    $results.Add($resultRecord)
}

$report = [ordered]@{
    schemaVersion = 'fbsir.independentBoardAttributionIdentityRegistryMysqlIt.v1'
    generatedAt = [DateTime]::UtcNow.ToString('o')
    migration = 'public_init_044'
    predecessorMigrationSha256 = $migration043Sha
    migrationSha256 = $migration044Sha
    results = @($results)
    status = if ($results.Count -eq $Versions.Count) { 'PASS' } else { 'FAIL' }
    productionChanged = $false
}
[IO.File]::WriteAllText($receiptFullPath,
    (($report | ConvertTo-Json -Depth 8) + [Environment]::NewLine),
    [Text.UTF8Encoding]::new($false))
Write-Output ($report | ConvertTo-Json -Depth 8)
