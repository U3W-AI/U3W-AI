[CmdletBinding()]
param(
    [switch]$AllowDestructiveTest,

    [ValidateRange(0, 65535)]
    [int]$Port = 0,

    [string]$MySqlBinDirectory = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AllowDestructiveTest) {
    throw 'Explicit -AllowDestructiveTest consent is required.'
}

$repoRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$workRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'work\independent-board-menu-migration-it'))
$primaryDatabase = 'u3w_independent_board_menu_it'
$collisionPrefix = 'u3w_menu_collision_'
$collisionDatabase = $collisionPrefix + ('x' * (64 - $collisionPrefix.Length))

$w2MigrationPath = Join-Path $repoRoot 'sql\update_20260720_independent_board_me_menu.sql'
$w3aMigrationPath = Join-Path $repoRoot 'sql\update_20260720_independent_board_admin_menu.sql'
$w3bMigrationPath = Join-Path $repoRoot 'sql\update_20260720_independent_board_entitlement_lifecycle_menu.sql'
$w2Procedure = 'u3w_migrate_independent_board_me_menu_20260720'
$w3aProcedure = 'u3w_migrate_independent_board_admin_menu_20260720'
$w3bProcedure = 'u3w_migrate_board_entitlement_lifecycle_menu_20260720'
$w3bLockSuffix = '20260720_independent_board_entitlement_lifecycle_menu_v1'

function Resolve-MySqlBinDirectory {
    param([string]$Requested)

    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($Requested)) {
        $candidates.Add($Requested)
    }
    $candidates.Add('C:\Program Files\MySQL\MySQL Server 8.4\bin')
    $candidates.Add('C:\Program Files\MySQL\MySQL Server 8.0\bin')

    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $full = [System.IO.Path]::GetFullPath($candidate)
        if ((Test-Path -LiteralPath (Join-Path $full 'mysqld.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $full 'mysql.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $full 'mysqladmin.exe') -PathType Leaf)) {
            return $full
        }
    }
    throw 'A local MySQL 8 bin directory containing mysqld.exe, mysql.exe and mysqladmin.exe is required.'
}

function Get-LoopbackEphemeralPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Assert-SafeCleanupPath {
    param([string]$Path, [string]$AllowedRoot)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $fullRoot = [System.IO.Path]::GetFullPath($AllowedRoot).TrimEnd('\')
    if (-not $fullPath.StartsWith($fullRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing cleanup outside the dedicated menu-migration work root: $fullPath"
    }
    if ((Split-Path -Leaf $fullPath) -notmatch '^run-[0-9]{8}T[0-9]{6}-[0-9]+-[0-9a-f]{8}$') {
        throw "Refusing cleanup of a path without the expected run identity: $fullPath"
    }
    return $fullPath
}

function Get-VerifiedMySqlProcessId {
    param(
        [string]$ExpectedPidFile,
        [string]$ExpectedDataDirectory,
        [int]$ExpectedPort
    )

    if (-not (Test-Path -LiteralPath $ExpectedPidFile -PathType Leaf)) {
        return $null
    }
    $rawPid = (Get-Content -Raw -LiteralPath $ExpectedPidFile).Trim()
    $processId = 0
    if (-not [int]::TryParse($rawPid, [ref]$processId) -or $processId -le 0) {
        throw "Dedicated MySQL pid file is invalid: $ExpectedPidFile"
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    if ($null -eq $process) {
        return $null
    }
    if ([string]::IsNullOrWhiteSpace($process.CommandLine) -or
        -not $process.CommandLine.Contains($ExpectedDataDirectory) -or
        -not $process.CommandLine.Contains("--port=$ExpectedPort")) {
        throw "Refusing to manage PID $processId because its command line is not bound to this menu-migration test run."
    }
    return $processId
}

function Get-Sha256 {
    param([byte[]]$Bytes)

    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

foreach ($migrationPath in @($w2MigrationPath, $w3aMigrationPath, $w3bMigrationPath)) {
    if (-not (Test-Path -LiteralPath $migrationPath -PathType Leaf)) {
        throw "Required menu migration is missing: $migrationPath"
    }
}
$w2Bytes = [System.IO.File]::ReadAllBytes($w2MigrationPath)
$w3aBytes = [System.IO.File]::ReadAllBytes($w3aMigrationPath)
$w3bBytes = [System.IO.File]::ReadAllBytes($w3bMigrationPath)
$w2Sha256 = Get-Sha256 -Bytes $w2Bytes
$w3aSha256 = Get-Sha256 -Bytes $w3aBytes
$w3bSha256 = Get-Sha256 -Bytes $w3bBytes

$mysqlBin = Resolve-MySqlBinDirectory -Requested $MySqlBinDirectory
$mysqld = Join-Path $mysqlBin 'mysqld.exe'
$mysql = Join-Path $mysqlBin 'mysql.exe'
$mysqlAdmin = Join-Path $mysqlBin 'mysqladmin.exe'
$baseDirectory = Split-Path -Parent $mysqlBin

if ($Port -eq 0) {
    $Port = Get-LoopbackEphemeralPort
}
if ($Port -eq 3306) {
    throw 'Port 3306 is forbidden for the destructive menu-migration integration test.'
}

$runId = 'run-{0}-{1}-{2}' -f (Get-Date -Format 'yyyyMMddTHHmmss'), $PID,
    ([Guid]::NewGuid().ToString('N').Substring(0, 8))
$runDirectory = Join-Path $workRoot $runId
$dataDirectory = Join-Path $runDirectory 'data'
$errorLog = Join-Path $runDirectory 'mysql-error.log'
$pidFile = Join-Path $runDirectory 'mysql.pid'
$serverProcess = $null
$ready = $false
$testPassed = $false
$mysqlVersion = $null
$transactionIsolation = $null
$primaryCounts = $null
$collisionCounts = $null
$phaseResults = [System.Collections.Generic.List[object]]::new()

function Invoke-MySqlBytesResult {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Bytes,
        [string]$Database = ''
    )

    if ($Database -and ($Database -notmatch '^[A-Za-z0-9_]+$' -or $Database.Length -gt 64)) {
        throw "Unsafe disposable database name: $Database"
    }
    $arguments = "--protocol=tcp --host=127.0.0.1 --port=$Port --user=root --default-character-set=utf8mb4 --batch --skip-column-names"
    if ($Database) {
        $arguments += " --database=$Database"
    }
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysql
    $startInfo.Arguments = $arguments
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw 'Unable to start mysql client for the menu-migration test.'
    }
    try {
        $process.StandardInput.BaseStream.Write($Bytes, 0, $Bytes.Length)
        $process.StandardInput.BaseStream.Flush()
        $process.StandardInput.Close()
        $stdout = $process.StandardOutput.ReadToEnd().Trim()
        $stderr = $process.StandardError.ReadToEnd().Trim()
        $process.WaitForExit()
        return [pscustomobject]@{
            exitCode = $process.ExitCode
            stdout = $stdout
            stderr = $stderr
        }
    }
    finally {
        $process.Dispose()
    }
}

function Invoke-MySqlText {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$Database = ''
    )

    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($Sql)
    $result = Invoke-MySqlBytesResult -Bytes $bytes -Database $Database
    if ($result.exitCode -ne 0) {
        throw "mysql exited with code $($result.exitCode): $($result.stderr)"
    }
    return $result.stdout
}

function Invoke-Migration {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][byte[]]$Bytes,
        [Parameter(Mandatory = $true)][string]$Database
    )

    $startedAt = [DateTimeOffset]::UtcNow
    $result = Invoke-MySqlBytesResult -Bytes $Bytes -Database $Database
    $phaseResults.Add([pscustomobject]@{
        name = $Name
        expected = 'success'
        ok = ($result.exitCode -eq 0)
        exitCode = $result.exitCode
        startedAt = $startedAt.ToString('o')
        finishedAt = [DateTimeOffset]::UtcNow.ToString('o')
    })
    if ($result.exitCode -ne 0) {
        throw "Menu migration phase '$Name' failed with code $($result.exitCode): $($result.stderr)"
    }
}

function Invoke-ExpectedMigrationFailure {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][byte[]]$Bytes,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$ExpectedMessage
    )

    $startedAt = [DateTimeOffset]::UtcNow
    $result = Invoke-MySqlBytesResult -Bytes $Bytes -Database $Database
    $combined = ($result.stdout + "`n" + $result.stderr).Trim()
    $ok = $result.exitCode -ne 0 -and $combined.Contains($ExpectedMessage)
    $phaseResults.Add([pscustomobject]@{
        name = $Name
        expected = 'fail_closed'
        ok = $ok
        exitCode = $result.exitCode
        expectedMessageObserved = $combined.Contains($ExpectedMessage)
        startedAt = $startedAt.ToString('o')
        finishedAt = [DateTimeOffset]::UtcNow.ToString('o')
    })
    if (-not $ok) {
        throw "Menu migration phase '$Name' did not fail closed with '$ExpectedMessage'. exit=$($result.exitCode), output=$combined"
    }
}

function Assert-Scalar {
    param(
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Sql,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Label
    )

    $actual = (Invoke-MySqlText -Sql $Sql -Database $Database).Trim()
    if ($actual -ne $Expected) {
        throw "$Label mismatch: expected '$Expected', found '$actual'."
    }
    return $actual
}

function Initialize-TestDatabase {
    param([Parameter(Mandatory = $true)][string]$Database)

    Invoke-MySqlText -Sql "CREATE DATABASE ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" | Out-Null
    Invoke-MySqlText -Database $Database -Sql @"
CREATE TABLE u3w_schema_migration (
    version VARCHAR(96) NOT NULL,
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE sys_menu (
    menu_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    menu_name VARCHAR(64) NOT NULL,
    parent_id BIGINT NOT NULL,
    order_num INT NOT NULL,
    path VARCHAR(128) NOT NULL,
    component VARCHAR(255),
    query VARCHAR(255),
    route_name VARCHAR(128),
    is_frame TINYINT NOT NULL,
    is_cache TINYINT NOT NULL,
    menu_type CHAR(1) NOT NULL,
    visible CHAR(1) NOT NULL,
    status CHAR(1) NOT NULL,
    perms VARCHAR(128),
    icon VARCHAR(128),
    create_by VARCHAR(64),
    create_time DATETIME,
    update_by VARCHAR(64),
    update_time DATETIME,
    remark VARCHAR(512)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE sys_role (
    role_id BIGINT NOT NULL PRIMARY KEY,
    role_key VARCHAR(64) NOT NULL,
    status CHAR(1) NOT NULL,
    del_flag CHAR(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO sys_role (role_id, role_key, status, del_flag) VALUES (10, 'user', '0', '0');
"@ | Out-Null
}

function Assert-W3bLockReleased {
    param([Parameter(Mandatory = $true)][string]$Database)

    Assert-Scalar -Database $Database -Expected '1' -Label 'W3b named lock release' -Sql @"
SELECT IF(IS_USED_LOCK(SHA2(CONCAT(DATABASE(), ':$w3bLockSuffix'), 256)) IS NULL, 1, 0);
"@ | Out-Null
}

function Remove-LeftoverProcedure {
    param(
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Procedure
    )

    Invoke-MySqlText -Database $Database -Sql "DROP PROCEDURE IF EXISTS ``$Procedure``;" | Out-Null
    Assert-Scalar -Database $Database -Expected '0' -Label "procedure cleanup $Procedure" -Sql `
        "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = DATABASE() AND routine_name = '$Procedure';" | Out-Null
}

try {
    New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null

    Write-Host "Initializing disposable MySQL in $runDirectory"
    $priorErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $initializeOutput = & $mysqld '--no-defaults' "--basedir=$baseDirectory" `
        "--datadir=$dataDirectory" '--initialize-insecure' '--console' 2>&1
    $initializeExitCode = $LASTEXITCODE
    $ErrorActionPreference = $priorErrorActionPreference
    if ($initializeExitCode -ne 0) {
        $initializeOutput | Write-Host
        throw "mysqld --initialize-insecure failed with exit code $initializeExitCode"
    }

    $serverArguments = @(
        '--no-defaults',
        "--basedir=`"$baseDirectory`"",
        "--datadir=`"$dataDirectory`"",
        "--port=$Port",
        '--bind-address=127.0.0.1',
        '--skip-networking=0',
        '--mysqlx=0',
        "--pid-file=`"$pidFile`"",
        "--log-error=`"$errorLog`"",
        '--character-set-server=utf8mb4',
        '--collation-server=utf8mb4_unicode_ci'
    )
    $serverProcess = Start-Process -FilePath $mysqld -ArgumentList $serverArguments `
        -WorkingDirectory $runDirectory -WindowStyle Hidden -PassThru

    for ($attempt = 0; $attempt -lt 80; $attempt++) {
        $priorErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$Port" '--user=root' `
            '--connect-timeout=1' 'ping' *> $null
        $pingExitCode = $LASTEXITCODE
        $ErrorActionPreference = $priorErrorActionPreference
        if ($pingExitCode -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) {
        throw 'Disposable MySQL did not become ready within 20 seconds.'
    }

    $mysqlVersion = Invoke-MySqlText -Sql 'SELECT VERSION();'
    if ($mysqlVersion -notmatch '^8\.') {
        throw "The menu-migration gate requires MySQL 8; found '$mysqlVersion'."
    }
    $transactionIsolation = Invoke-MySqlText -Sql 'SELECT @@transaction_isolation;'

    Initialize-TestDatabase -Database $primaryDatabase
    Invoke-Migration -Name 'w2_first_apply' -Bytes $w2Bytes -Database $primaryDatabase
    Invoke-Migration -Name 'w2_completed_rerun' -Bytes $w2Bytes -Database $primaryDatabase
    Invoke-Migration -Name 'w3a_first_apply' -Bytes $w3aBytes -Database $primaryDatabase
    Invoke-Migration -Name 'w3a_completed_rerun' -Bytes $w3aBytes -Database $primaryDatabase
    Invoke-Migration -Name 'w3b_first_apply' -Bytes $w3bBytes -Database $primaryDatabase

    Assert-Scalar -Database $primaryDatabase -Expected '7' -Label 'first-apply menu count' -Sql 'SELECT COUNT(*) FROM sys_menu;' | Out-Null
    Assert-Scalar -Database $primaryDatabase -Expected '3' -Label 'internal receipt count' -Sql @"
SELECT COUNT(*) FROM u3w_schema_migration
WHERE version IN (
  '20260720_independent_board_me_menu_v1',
  '20260720_independent_board_admin_menu_v1',
  '20260720_independent_board_entitlement_lifecycle_menu_v1'
);
"@ | Out-Null
    Assert-Scalar -Database $primaryDatabase -Expected '2' -Label 'W3b exact identity count' -Sql @"
SELECT COUNT(*) FROM sys_menu
WHERE perms = 'board:entitlement:revoke'
   OR component = 'business/independentBoard/admin/entitlementReceipt/index';
"@ | Out-Null

    Invoke-MySqlText -Database $primaryDatabase -Sql @"
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 999, menu_id FROM sys_menu
WHERE component = 'business/independentBoard/admin/entitlementReceipt/index'
  AND route_name = 'IndependentBoardEntitlementReceipts';
"@ | Out-Null
    Invoke-Migration -Name 'w3b_completed_rerun_preserves_external_binding' -Bytes $w3bBytes -Database $primaryDatabase
    Invoke-Migration -Name 'w2_rerun_after_w3b' -Bytes $w2Bytes -Database $primaryDatabase
    Invoke-Migration -Name 'w3a_rerun_after_w3b' -Bytes $w3aBytes -Database $primaryDatabase
    Assert-Scalar -Database $primaryDatabase -Expected '1' -Label 'externally governed W3b role binding' -Sql @"
SELECT COUNT(*) FROM sys_role_menu AS role_menu
INNER JOIN sys_menu AS menu_row ON menu_row.menu_id = role_menu.menu_id
WHERE role_menu.role_id = 999
  AND menu_row.component = 'business/independentBoard/admin/entitlementReceipt/index';
"@ | Out-Null
    Assert-W3bLockReleased -Database $primaryDatabase

    Invoke-MySqlText -Database $primaryDatabase -Sql @"
UPDATE sys_menu SET icon = 'drifted-by-disposable-gate'
WHERE component = 'business/independentBoard/admin/entitlementReceipt/index'
  AND route_name = 'IndependentBoardEntitlementReceipts';
"@ | Out-Null
    Invoke-ExpectedMigrationFailure -Name 'w3b_completed_state_drift_fail_closed' -Bytes $w3bBytes `
        -Database $primaryDatabase -ExpectedMessage 'receipt audit menu current state is missing or drifted'
    Assert-Scalar -Database $primaryDatabase -Expected '1' -Label 'drift was not silently repaired' -Sql @"
SELECT COUNT(*) FROM sys_menu
WHERE component = 'business/independentBoard/admin/entitlementReceipt/index'
  AND icon = 'drifted-by-disposable-gate';
"@ | Out-Null
    Assert-W3bLockReleased -Database $primaryDatabase
    Remove-LeftoverProcedure -Database $primaryDatabase -Procedure $w3bProcedure
    Invoke-MySqlText -Database $primaryDatabase -Sql @"
UPDATE sys_menu SET icon = 'form'
WHERE component = 'business/independentBoard/admin/entitlementReceipt/index'
  AND route_name = 'IndependentBoardEntitlementReceipts';
"@ | Out-Null
    Invoke-Migration -Name 'w3b_recovered_current_state_rerun' -Bytes $w3bBytes -Database $primaryDatabase

    Initialize-TestDatabase -Database $collisionDatabase
    Invoke-Migration -Name 'collision_w2_prerequisite' -Bytes $w2Bytes -Database $collisionDatabase
    Invoke-Migration -Name 'collision_w3a_prerequisite' -Bytes $w3aBytes -Database $collisionDatabase
    Invoke-MySqlText -Database $collisionDatabase -Sql @"
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon,
     create_by, create_time, update_by, update_time, remark)
SELECT 'prewrite-collision', menu_id, 2, '', NULL, NULL, '', 1, 0, 'F', '0', '0',
       'board:entitlement:revoke', '#', 'gate', CURRENT_TIMESTAMP, '', NULL,
       'disposable collision fixture'
FROM sys_menu
WHERE component = 'business/independentBoard/admin/entitlement/index'
  AND route_name = 'IndependentBoardEntitlementGovernance';
"@ | Out-Null
    Invoke-ExpectedMigrationFailure -Name 'w3b_prewrite_identity_collision_fail_closed' -Bytes $w3bBytes `
        -Database $collisionDatabase -ExpectedMessage 'identity exists without its migration receipt'
    Assert-Scalar -Database $collisionDatabase -Expected '0' -Label 'collision internal receipt count' -Sql @"
SELECT COUNT(*) FROM u3w_schema_migration
WHERE version = '20260720_independent_board_entitlement_lifecycle_menu_v1';
"@ | Out-Null
    Assert-Scalar -Database $collisionDatabase -Expected '0' -Label 'collision receipt page count' -Sql @"
SELECT COUNT(*) FROM sys_menu
WHERE component = 'business/independentBoard/admin/entitlementReceipt/index';
"@ | Out-Null
    Assert-W3bLockReleased -Database $collisionDatabase
    Remove-LeftoverProcedure -Database $collisionDatabase -Procedure $w3bProcedure

    $primaryCounts = [ordered]@{
        menus = [int](Invoke-MySqlText -Database $primaryDatabase -Sql 'SELECT COUNT(*) FROM sys_menu;')
        roleBindings = [int](Invoke-MySqlText -Database $primaryDatabase -Sql 'SELECT COUNT(*) FROM sys_role_menu;')
        internalReceipts = [int](Invoke-MySqlText -Database $primaryDatabase -Sql @"
SELECT COUNT(*) FROM u3w_schema_migration
WHERE version LIKE '20260720_independent_board_%_menu_v1';
"@)
        w3bIdentities = [int](Invoke-MySqlText -Database $primaryDatabase -Sql @"
SELECT COUNT(*) FROM sys_menu
WHERE perms = 'board:entitlement:revoke'
   OR component = 'business/independentBoard/admin/entitlementReceipt/index';
"@)
    }
    $collisionCounts = [ordered]@{
        schemaNameLength = $collisionDatabase.Length
        w3bInternalReceipts = [int](Invoke-MySqlText -Database $collisionDatabase -Sql @"
SELECT COUNT(*) FROM u3w_schema_migration
WHERE version = '20260720_independent_board_entitlement_lifecycle_menu_v1';
"@)
        prewrittenIdentities = [int](Invoke-MySqlText -Database $collisionDatabase -Sql @"
SELECT COUNT(*) FROM sys_menu WHERE perms = 'board:entitlement:revoke';
"@)
    }

    foreach ($binding in @(
        @{ Path = $w2MigrationPath; Sha = $w2Sha256 },
        @{ Path = $w3aMigrationPath; Sha = $w3aSha256 },
        @{ Path = $w3bMigrationPath; Sha = $w3bSha256 }
    )) {
        $latestSha = Get-Sha256 -Bytes ([System.IO.File]::ReadAllBytes($binding.Path))
        if ($latestSha -ne $binding.Sha) {
            throw "Migration bytes changed during the gate: $($binding.Path)"
        }
    }
    $testPassed = $true
}
finally {
    $dedicatedServerPid = Get-VerifiedMySqlProcessId -ExpectedPidFile $pidFile `
        -ExpectedDataDirectory $dataDirectory -ExpectedPort $Port
    if ($null -ne $dedicatedServerPid) {
        if ($ready) {
            $priorErrorActionPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & $mysqlAdmin '--protocol=tcp' '--host=127.0.0.1' "--port=$Port" '--user=root' `
                '--connect-timeout=2' 'shutdown' *> $null
            $ErrorActionPreference = $priorErrorActionPreference
        }
        for ($attempt = 0; $attempt -lt 40 -and
                (Get-Process -Id $dedicatedServerPid -ErrorAction SilentlyContinue); $attempt++) {
            Start-Sleep -Milliseconds 250
        }
        if (Get-Process -Id $dedicatedServerPid -ErrorAction SilentlyContinue) {
            Stop-Process -Id $dedicatedServerPid -Force
            for ($attempt = 0; $attempt -lt 20 -and
                    (Get-Process -Id $dedicatedServerPid -ErrorAction SilentlyContinue); $attempt++) {
                Start-Sleep -Milliseconds 250
            }
        }
    }

    if (Test-Path -LiteralPath $runDirectory) {
        $safeCleanup = Assert-SafeCleanupPath -Path $runDirectory -AllowedRoot $workRoot
        Remove-Item -LiteralPath $safeCleanup -Recurse -Force
    }
    if ((Test-Path -LiteralPath $workRoot -PathType Container) -and
        @(Get-ChildItem -LiteralPath $workRoot -Force).Count -eq 0) {
        Remove-Item -LiteralPath $workRoot -Force
    }
}

if ($testPassed) {
    [ordered]@{
        schemaVersion = 1
        test = 'IndependentBoardMenuMigrationIT'
        result = 'PASS'
        host = '127.0.0.1'
        port = $Port
        mysqlVersion = $mysqlVersion
        engine = 'InnoDB'
        transactionIsolation = $transactionIsolation
        migrations = [ordered]@{
            w2 = [ordered]@{ version = '20260720_independent_board_me_menu_v1'; sha256 = $w2Sha256 }
            w3a = [ordered]@{ version = '20260720_independent_board_admin_menu_v1'; sha256 = $w3aSha256 }
            w3b = [ordered]@{ version = '20260720_independent_board_entitlement_lifecycle_menu_v1'; sha256 = $w3bSha256 }
        }
        phases = @($phaseResults)
        primary = $primaryCounts
        collision = $collisionCounts
        destructiveTestConsent = $true
        productionConnectionUsed = $false
        workDirectoryCleaned = $true
    } | ConvertTo-Json -Depth 8
}
