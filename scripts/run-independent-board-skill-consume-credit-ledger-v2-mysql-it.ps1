[CmdletBinding()]
param(
    [switch]$AllowDestructiveTest,
    [string[]]$MySqlBinDirectories = @(),
    [string]$MavenCommand = '',
    [string]$JavaHome = '',
    [ValidateSet('8.0.30', '8.4.8')]
    [string[]]$Versions = @('8.0.30', '8.4.8'),
    # v2's longest table name exceeds MySQL for Windows' practical path budget
    # when it is stored below this candidate workspace.  Keep disposable data
    # under a deliberately short, explicit root instead.
    [string]$IsolatedWorkRoot = 'C:\u3w-w3l-it'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AllowDestructiveTest) {
    throw 'Explicit -AllowDestructiveTest consent is required.'
}

$repoRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
# The isolated data path must stay below MySQL's Windows table-file limit even
# for the longest v2 table name.  It is intentionally outside the candidate
# worktree and must never be a drive root or an ambiguous broad directory.
# Windows PowerShell 5.1 targets .NET Framework, which does not expose the
# newer Path.IsPathFullyQualified API.  This runner is Windows-only (.exe
# toolchain), so an explicit drive-qualified path is the compatible guard.
if ($IsolatedWorkRoot -notmatch '^[A-Za-z]:\\') {
    throw 'IsolatedWorkRoot must be an explicit absolute path.'
}
$workRoot = [System.IO.Path]::GetFullPath($IsolatedWorkRoot).TrimEnd('\\')
if ($workRoot -match '^[A-Za-z]:$' -or $workRoot.Length -gt 48 -or
    (Split-Path -Leaf $workRoot) -notmatch '^u3w-w3l-it(?:-[A-Za-z0-9]+)?$') {
    throw 'IsolatedWorkRoot must be a short, dedicated u3w-w3l-it directory and not a drive root.'
}
$migrationPath = Join-Path $repoRoot 'sql\update_20260723_skill_consume_credit_ledger_v2.sql'
$legacyMigrationPath = Join-Path $repoRoot 'sql\update_20260722_independent_board_credit_ledger.sql'
$initializerPath = Join-Path $repoRoot 'scripts\init-database.ps1'
$runnerPath = [System.IO.Path]::GetFullPath($MyInvocation.MyCommand.Path)
$javaItPath = Join-Path $repoRoot (
    'FBSir-business\src\test\java\com\wx\fbsir\business\board\credit\service\' +
    'SkillConsumeCreditLedgerV2MysqlIT.java')
$surefireReport = Join-Path $repoRoot (
    'FBSir-business\target\surefire-reports\TEST-com.wx.fbsir.business.board.credit.service.' +
    'SkillConsumeCreditLedgerV2MysqlIT.xml')
$expectedTests = 4
$requiredVersions = @('8.0.30', '8.4.8')
$requestedVersions = @($Versions | Sort-Object -Unique)

if ($requestedVersions.Count -ne $Versions.Count -or
    (($requestedVersions | Sort-Object) -join ',') -ne (($Versions | Sort-Object) -join ',')) {
    throw 'Versions must contain unique supported MySQL versions.'
}
if (-not (Test-Path -LiteralPath $migrationPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $legacyMigrationPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $initializerPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $javaItPath -PathType Leaf)) {
    throw 'Required 038/042 migration, canonical initializer or Java IT is missing.'
}

function Get-LoopbackEphemeralPort {
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

function Get-RunBoundMySqlProcessId {
    param(
        [Parameter(Mandatory = $true)][string]$ExpectedPidFile,
        [Parameter(Mandatory = $true)][string]$ExpectedDataRoot,
        [Parameter(Mandatory = $true)][string]$ExpectedPort
    )

    if (-not (Test-Path -LiteralPath $ExpectedPidFile -PathType Leaf)) {
        return $null
    }
    $rawPid = (Get-Content -LiteralPath $ExpectedPidFile -Raw).Trim()
    $processId = 0
    if (-not [int]::TryParse($rawPid, [ref]$processId) -or $processId -le 0) {
        throw "Dedicated 042 MySQL pid file is invalid: $ExpectedPidFile"
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    if ($null -eq $process) {
        return $null
    }
    if ([string]::IsNullOrWhiteSpace($process.CommandLine) -or
        -not $process.CommandLine.Contains($ExpectedDataRoot) -or
        -not $process.CommandLine.Contains("--port=$ExpectedPort")) {
        throw "Refusing to manage PID $processId because it is not bound to this 042 MySQL IT run."
    }
    return $processId
}

function Assert-LoopbackListenerOwnedBy {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][int]$ExpectedProcessId
    )

    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop |
        Where-Object { $_.LocalAddress -in @('127.0.0.1', '::1') })
    if ($listeners.Count -ne 1 -or $listeners[0].OwningProcess -ne $ExpectedProcessId) {
        $owners = @($listeners | ForEach-Object { $_.OwningProcess }) -join ','
        throw "Refusing MySQL IT continuation: loopback port $Port is not exclusively owned by PID $ExpectedProcessId (observed: $owners)."
    }
}

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required artifact is missing: $Path"
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Resolve-JavaHome {
    param([string]$Requested)

    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($Requested)) {
        $candidates.Add($Requested)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates.Add($env:JAVA_HOME)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        $candidates.Add((Join-Path $env:USERPROFILE '.codex\cache\toolchains\jdk-17.0.19+10'))
    }
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $full = [System.IO.Path]::GetFullPath($candidate)
        if (Test-Path -LiteralPath (Join-Path $full 'bin\java.exe') -PathType Leaf) {
            return $full
        }
    }
    throw 'A Java 17 home is required. Pass -JavaHome or set JAVA_HOME.'
}

function Resolve-MavenCommand {
    param([string]$Requested)

    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($Requested)) {
        $candidates.Add($Requested)
    }
    $pathMaven = Get-Command mvn.cmd -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $pathMaven) {
        $candidates.Add($pathMaven.Source)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        $candidates.Add((Join-Path $env:USERPROFILE (
            '.codex\cache\toolchains\apache-maven-3.9.16\bin\mvn.cmd')))
    }
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $full = [System.IO.Path]::GetFullPath($candidate)
        if (Test-Path -LiteralPath $full -PathType Leaf) {
            return $full
        }
    }
    throw 'A Maven command is required. Pass -MavenCommand or add mvn.cmd to PATH.'
}

function Resolve-MySqlProfiles {
    param([string[]]$Requested)

    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($candidate in $Requested) {
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            $candidates.Add($candidate)
        }
    }
    foreach ($environmentName in @('U3W_MYSQL_8030_BIN', 'U3W_MYSQL_848_BIN')) {
        $candidate = [Environment]::GetEnvironmentVariable($environmentName, 'Process')
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            $candidates.Add($candidate)
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        $toolchainRoot = Join-Path $env:USERPROFILE '.codex\cache\toolchains'
        $candidates.Add((Join-Path $toolchainRoot 'mysql-8.0.30-winx64\bin'))
        $candidates.Add((Join-Path $toolchainRoot 'mysql-8.4.8-winx64\bin'))
    }

    $profiles = @{}
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $bin = [System.IO.Path]::GetFullPath($candidate)
        $mysqld = Join-Path $bin 'mysqld.exe'
        $mysql = Join-Path $bin 'mysql.exe'
        $mysqlAdmin = Join-Path $bin 'mysqladmin.exe'
        $mysqlConfigEditor = Join-Path $bin 'mysql_config_editor.exe'
        if (-not (Test-Path -LiteralPath $mysqld -PathType Leaf) -or
            -not (Test-Path -LiteralPath $mysql -PathType Leaf) -or
            -not (Test-Path -LiteralPath $mysqlAdmin -PathType Leaf) -or
            -not (Test-Path -LiteralPath $mysqlConfigEditor -PathType Leaf)) {
            continue
        }
        $versionOutput = (& $mysqld '--no-defaults' '--version' 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) {
            continue
        }
        $match = [regex]::Match($versionOutput, 'Ver\s+(?<version>[0-9]+\.[0-9]+\.[0-9]+)')
        if ($match.Success -and $match.Groups['version'].Value -in $requiredVersions) {
            $version = $match.Groups['version'].Value
            if (-not $profiles.ContainsKey($version)) {
                $profiles[$version] = [pscustomobject]@{
                    version = $version
                    bin = $bin
                    base = Split-Path -Parent $bin
                    mysqld = $mysqld
                    mysql = $mysql
                    mysqlAdmin = $mysqlAdmin
                    mysqlConfigEditor = $mysqlConfigEditor
                }
            }
        }
    }

    $missing = @($requestedVersions | Where-Object { -not $profiles.ContainsKey($_) })
    if ($missing.Count -ne 0) {
        throw "The exact requested MySQL matrix is unavailable: $($missing -join ', ')."
    }
    return @($requestedVersions | ForEach-Object { $profiles[$_] })
}

function Assert-SafeRunDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $fullRoot = $workRoot.TrimEnd('\')
    if (Test-Path -LiteralPath $fullRoot -PathType Container) {
        $rootItem = Get-Item -LiteralPath $fullRoot -Force
        if (($rootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Refusing cleanup below a reparse-point 042 MySQL IT root: $fullRoot"
        }
    }
    if (-not $fullPath.StartsWith($fullRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing cleanup outside the dedicated 042 MySQL IT root: $fullPath"
    }
    if ((Split-Path -Leaf $fullPath) -notmatch
        '^run-mysql-(8-0-30|8-4-8)-[0-9]{8}T[0-9]{6}-[0-9]+-[0-9a-f]{8}$') {
        throw "Refusing cleanup without an exact 042 MySQL IT run identity: $fullPath"
    }
    if (Test-Path -LiteralPath $fullPath -PathType Container) {
        $runItem = Get-Item -LiteralPath $fullPath -Force
        if (($runItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Refusing cleanup of a reparse-point 042 MySQL IT run: $fullPath"
        }
    }
}

function Remove-SafeRunDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)

    Assert-SafeRunDirectory -Path $Path
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        return
    }
    $cleanupError = $null
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        try {
            # The exact descendant identity was checked above.  .NET deletion
            # avoids wildcard expansion and keeps this cleanup self-contained.
            [System.IO.Directory]::Delete($Path, $true)
            $cleanupError = $null
            break
        }
        catch {
            $cleanupError = $_
            Start-Sleep -Milliseconds 250
        }
    }
    if ($null -ne $cleanupError -or (Test-Path -LiteralPath $Path)) {
        throw "Unable to clean the dedicated 042 MySQL IT run directory: $Path"
    }
}

function Invoke-MySqlBytes {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][byte[]]$Bytes,
        [string]$Database = '',
        [switch]$AllowFailure
    )

    if ($Database -and $Database -notmatch '^[A-Za-z0-9_]{1,64}$') {
        throw "Unsafe isolated database name: $Database"
    }
    $arguments = @(
        '--no-defaults', '--protocol=TCP', '--host=127.0.0.1', "--port=$Port",
        '--user=root', '--default-character-set=utf8mb4', '--batch', '--raw',
        '--skip-column-names'
    )
    if ($Database) {
        $arguments += "--database=$Database"
    }
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Profile.mysql
    $startInfo.Arguments = ($arguments -join ' ')
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $client = [System.Diagnostics.Process]::new()
    $client.StartInfo = $startInfo
    if (-not $client.Start()) {
        throw 'Unable to start the isolated mysql client.'
    }
    try {
        $stdoutTask = $client.StandardOutput.ReadToEndAsync()
        $stderrTask = $client.StandardError.ReadToEndAsync()
        $client.StandardInput.BaseStream.Write($Bytes, 0, $Bytes.Length)
        $client.StandardInput.BaseStream.Flush()
        $client.StandardInput.Close()
        $client.WaitForExit()
        $result = [pscustomobject]@{
            exitCode = $client.ExitCode
            stdout = $stdoutTask.GetAwaiter().GetResult().Trim()
            stderr = $stderrTask.GetAwaiter().GetResult().Trim()
        }
        if (-not $AllowFailure -and $result.exitCode -ne 0) {
            throw "Isolated mysql database '$Database' exited with code $($result.exitCode): $($result.stderr)"
        }
        return $result
    }
    finally {
        $client.Dispose()
    }
}

function Invoke-MySqlText {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$Database = '',
        [switch]$AllowFailure
    )

    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($Sql)
    return Invoke-MySqlBytes -Profile $Profile -Port $Port -Bytes $bytes `
        -Database $Database -AllowFailure:$AllowFailure
}

function Invoke-042Migration {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256,
        [switch]$AllowFailure
    )

    if ((Get-Sha256 -Path $migrationPath) -cne $ExpectedSha256) {
        throw '042 migration bytes changed during the dual-runtime run.'
    }
    return Invoke-MySqlBytes -Profile $Profile -Port $Port -Database $Database `
        -Bytes ([System.IO.File]::ReadAllBytes($migrationPath)) -AllowFailure:$AllowFailure
}

function Assert-ExactOutput {
    param(
        [Parameter(Mandatory = $true)][string]$Actual,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Stage
    )

    if (-not [string]::Equals($Actual, $Expected, [StringComparison]::Ordinal)) {
        throw "042 MySQL IT $Stage drifted: expected '$Expected', observed '$Actual'."
    }
}

function Invoke-CanonicalCurrentRead {
    param(
        [Parameter(Mandatory = $true)][string]$LoginPath,
        [Parameter(Mandatory = $true)][string]$MySqlExe,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$ExpectedInitializerSha256
    )

    if ((Get-Sha256 -Path $initializerPath) -cne $ExpectedInitializerSha256) {
        throw 'Canonical initializer bytes changed during the dual-runtime run.'
    }

    $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $initializerPath `
        -LoginPath $LoginPath -MySqlExe $MySqlExe -Database $Database `
        -SkillConsumeCreditLedgerV2CurrentReadOnly 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or
        $output -notmatch 'PASS Independent Board skill-consume v2 ledger current-read') {
        throw "Canonical 042 current-read failed: $output"
    }
}

function Initialize-042Prerequisites {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [bool]$CreatePublicReceipt = $true
    )

    $publicReceipt = if ($CreatePublicReceipt) {
@"
INSERT INTO $Database.u3w_schema_migration (version,description) VALUES
  ('public_init_042','RUNNING:Independent Board default-off skill-consume v2 credit ledger');
"@
    } else { '' }

    $sql = @"
CREATE DATABASE $Database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE $Database.u3w_schema_migration (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  version VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  description VARCHAR(255) NOT NULL,
  installed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_u3w_schema_migration_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE $Database.sys_user (
  user_id BIGINT NOT NULL,
  points INT DEFAULT 0,
  status CHAR(1) NOT NULL DEFAULT '0',
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  update_time DATETIME(3) NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE $Database.fbs_skill_usage_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  usage_record_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  host_type VARCHAR(32) NOT NULL,
  host_session_id VARCHAR(128) DEFAULT NULL,
  skill_code VARCHAR(64) NOT NULL,
  pack_id BIGINT DEFAULT NULL,
  pack_version VARCHAR(32) DEFAULT NULL,
  points_amount INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 0,
  start_time DATETIME NOT NULL,
  end_time DATETIME DEFAULT NULL,
  duration_seconds INT DEFAULT NULL,
  error_message TEXT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_usage_record_id (usage_record_id),
  KEY idx_user (user_id),
  KEY idx_status (status),
  KEY idx_pack (pack_id),
  KEY idx_created (created_at),
  KEY idx_host_type (host_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
$publicReceipt
"@
    Invoke-MySqlText -Profile $Profile -Port $Port -Sql $sql | Out-Null
    if ((Get-Sha256 -Path $legacyMigrationPath) -cne $legacyMigrationSha256) {
        throw '038 migration bytes changed during the 042 application matrix.'
    }
    Invoke-MySqlBytes -Profile $Profile -Port $Port -Database $Database `
        -Bytes ([System.IO.File]::ReadAllBytes($legacyMigrationPath)) | Out-Null
}

function Read-SurefireEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$ReportPath,
        [Parameter(Mandatory = $true)][DateTimeOffset]$StartedAt
    )

    if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
        throw "042 writer Surefire report is missing: $ReportPath"
    }
    $reportFile = Get-Item -LiteralPath $ReportPath
    if ($reportFile.LastWriteTimeUtc -lt $StartedAt.UtcDateTime) {
        throw "042 writer Surefire report is stale: $ReportPath"
    }
    [xml]$report = Get-Content -Raw -Encoding UTF8 -LiteralPath $ReportPath
    $suite = $report.testsuite
    $tests = [int]$suite.tests
    $failures = [int]$suite.failures
    $errors = [int]$suite.errors
    $skipped = [int]$suite.skipped
    if ($tests -ne $expectedTests -or $failures -ne 0 -or
        $errors -ne 0 -or $skipped -ne 0) {
        throw "042 writer MySQL IT must pass exactly $expectedTests/$expectedTests; " +
            "found tests=$tests failures=$failures errors=$errors skipped=$skipped."
    }
    return [ordered]@{
        tests = $tests
        passed = $tests - $failures - $errors - $skipped
        failures = $failures
        errors = $errors
        skipped = $skipped
        report = 'FBSir-business/target/surefire-reports/' +
            [System.IO.Path]::GetFileName($ReportPath)
        reportSha256 = Get-Sha256 -Path $ReportPath
        reportLastWriteTimeUtc = $reportFile.LastWriteTimeUtc.ToString('o')
    }
}

function Invoke-ExpectedMigrationFailure {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256,
        [Parameter(Mandatory = $true)][string]$ExpectedError,
        [Parameter(Mandatory = $true)][string]$Stage
    )

    $result = Invoke-042Migration -Profile $Profile -Port $Port -Database $Database `
        -ExpectedSha256 $ExpectedSha256 -AllowFailure
    if ($result.exitCode -eq 0 -or -not $result.stderr.Contains($ExpectedError)) {
        throw "042 MySQL IT $Stage did not fail closed: exit=$($result.exitCode) stderr=$($result.stderr)"
    }
}

function Assert-RejectedMigrationState {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Stage
    )

    $actual = (Invoke-MySqlText -Profile $Profile -Port $Port -Database $Database -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM u3w_schema_migration WHERE version='public_init_042'
    AND description='RUNNING:Independent Board default-off skill-consume v2 credit ledger'),
  (SELECT COUNT(*) FROM u3w_schema_migration WHERE version='20260723_skill_consume_credit_ledger_v2_042'
    AND description='Independent Board default-off skill-consume v2 credit ledger'),
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()
    AND event_object_table IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema=DATABASE()
    AND routine_name IN ('u3w_assert_skill_consume_credit_ledger_v2_triggers_20260723','u3w_migrate_skill_consume_credit_ledger_v2_20260723','u3w_finalize_skill_consume_credit_ledger_v2_20260723')),
  IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), ':20260723_skill_consume_credit_ledger_v2_042'), 256)));
"@).stdout
    Assert-ExactOutput -Actual $actual -Expected $Expected -Stage $Stage
}

function Get-042RawMetadataDigests {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database
    )

    return (Invoke-MySqlText -Profile $Profile -Port $Port -Database $Database -Sql @"
SET SESSION group_concat_max_len=1048576;
SELECT CONCAT_WS('|',
 (SELECT SHA2(GROUP_CONCAT(CONCAT('T:',HEX(CAST(table_name AS BINARY)),'|O:',LPAD(ordinal_position,3,'0'),'|N:',HEX(CAST(column_name AS BINARY)),'|Y:',HEX(CAST(column_type AS BINARY)),'|U:',HEX(CAST(is_nullable AS BINARY)),'|D:',IF(column_default IS NULL,'N',CONCAT('V:',HEX(CAST(column_default AS BINARY)))),'|C:',IF(character_set_name IS NULL,'N',CONCAT('V:',HEX(CAST(character_set_name AS BINARY)))),'|L:',IF(collation_name IS NULL,'N',CONCAT('V:',HEX(CAST(collation_name AS BINARY)))),'|E:',HEX(CAST(extra AS BINARY)),'|G:',IF(generation_expression IS NULL,'N',CONCAT('V:',HEX(CAST(generation_expression AS BINARY))))) ORDER BY table_name,ordinal_position SEPARATOR 0x0A),256) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT('T:',HEX(CAST(table_name AS BINARY)),'|I:',HEX(CAST(index_name AS BINARY)),'|U:',non_unique,'|Y:',HEX(CAST(index_type AS BINARY)),'|V:',HEX(CAST(is_visible AS BINARY)),'|S:',seq_in_index,'|N:',IF(column_name IS NULL,'N',CONCAT('V:',HEX(CAST(column_name AS BINARY)))),'|X:',IF(expression IS NULL,'N',CONCAT('V:',HEX(CAST(expression AS BINARY)))),'|C:',IF(collation IS NULL,'N',CONCAT('V:',HEX(CAST(collation AS BINARY)))),'|P:',IF(sub_part IS NULL,'N',CONCAT('V:',sub_part)),'|Q:',HEX(CAST(nullable AS BINARY))) ORDER BY table_name,index_name,seq_in_index SEPARATOR 0x0A),256) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT('T:',HEX(CAST(rc.table_name AS BINARY)),'|C:',HEX(CAST(rc.constraint_name AS BINARY)),'|S:',IF(rc.unique_constraint_schema=DATABASE(),'SAME','OTHER'),'|K:',HEX(CAST(rc.unique_constraint_name AS BINARY)),'|R:',HEX(CAST(rc.referenced_table_name AS BINARY)),'|U:',HEX(CAST(rc.update_rule AS BINARY)),'|D:',HEX(CAST(rc.delete_rule AS BINARY)),'|M:',HEX(CAST(rc.match_option AS BINARY)),'|O:',kcu.ordinal_position,'|N:',HEX(CAST(kcu.column_name AS BINARY)),'|Q:',IF(kcu.referenced_table_schema=DATABASE(),'SAME','OTHER'),'|P:',HEX(CAST(kcu.referenced_column_name AS BINARY)),'|I:',IF(kcu.position_in_unique_constraint IS NULL,'N',CONCAT('V:',kcu.position_in_unique_constraint))) ORDER BY rc.table_name,rc.constraint_name,kcu.ordinal_position SEPARATOR 0x0A),256) FROM information_schema.referential_constraints rc INNER JOIN information_schema.key_column_usage kcu ON kcu.constraint_schema=rc.constraint_schema AND kcu.table_name=rc.table_name AND kcu.constraint_name=rc.constraint_name WHERE rc.constraint_schema=DATABASE() AND rc.unique_constraint_schema=DATABASE() AND kcu.referenced_table_schema=DATABASE() AND rc.table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT('T:',HEX(CAST(tc.table_name AS BINARY)),'|C:',HEX(CAST(tc.constraint_name AS BINARY)),'|E:',HEX(CAST(tc.enforced AS BINARY)),'|X:',HEX(CAST(cc.check_clause AS BINARY))) ORDER BY tc.table_name,tc.constraint_name SEPARATOR 0x0A),256) FROM information_schema.table_constraints tc INNER JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK' AND tc.table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')));
"@).stdout
}

$migrationSha256 = Get-Sha256 -Path $migrationPath
$legacyMigrationSha256 = Get-Sha256 -Path $legacyMigrationPath
$resolvedJavaHome = Resolve-JavaHome -Requested $JavaHome
$resolvedMavenCommand = Resolve-MavenCommand -Requested $MavenCommand
$profiles = Resolve-MySqlProfiles -Requested $MySqlBinDirectories
$sourceSha256 = [ordered]@{
    migration = $migrationSha256
    legacyMigration038 = $legacyMigrationSha256
    initializer = Get-Sha256 -Path $initializerPath
    javaIntegrationTest = Get-Sha256 -Path $javaItPath
    writer = Get-Sha256 -Path (Join-Path $repoRoot (
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\credit\service\' +
        'SkillConsumeCreditWriter.java'))
    transactionService = Get-Sha256 -Path (Join-Path $repoRoot (
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\credit\service\' +
        'SkillConsumeCreditTransactionService.java'))
    ledgerMapperXml = Get-Sha256 -Path (Join-Path $repoRoot (
        'FBSir-business\src\main\resources\mapper\board\SkillConsumeCreditLedgerMapper.xml'))
    usageMapperXml = Get-Sha256 -Path (Join-Path $repoRoot (
        'FBSir-business\src\main\resources\mapper\fbs\FbsSkillUsageRecordMapper.xml'))
    legacyAuthorityFence = Get-Sha256 -Path (Join-Path $repoRoot (
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\credit\service\' +
        'IndependentBoardCreditService.java'))
    runner = Get-Sha256 -Path $runnerPath
}
$results = [System.Collections.Generic.List[object]]::new()
if (-not (Test-Path -LiteralPath $workRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $workRoot | Out-Null
}

foreach ($profile in $profiles) {
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmss')
    $suffix = ([Guid]::NewGuid().ToString('N')).Substring(0, 8)
    $runName = "run-mysql-$($profile.version.Replace('.', '-'))-$stamp-$PID-$suffix"
    $runRoot = Join-Path $workRoot $runName
    Assert-SafeRunDirectory -Path $runRoot
    New-Item -ItemType Directory -Path $runRoot | Out-Null
    $dataRoot = Join-Path $runRoot 'data'
    New-Item -ItemType Directory -Path $dataRoot | Out-Null
    $errorLog = Join-Path $runRoot 'mysql.err'
    $pidFile = Join-Path $runRoot 'mysqld.pid'
    $port = Get-LoopbackEphemeralPort
    # Keep the isolated schema name deliberately short: MySQL on Windows folds
    # database and table identifiers into a filesystem path beneath this already
    # deep candidate workspace.
    $database = "w3l_$($profile.version.Replace('.', ''))_$suffix"
    $server = $null
    $ownedServerProcessId = $null
    $cleaned = $false
    $priorLoginFile = $env:MYSQL_TEST_LOGIN_FILE
    $priorJavaHome = $env:JAVA_HOME
    $priorPath = $env:PATH
    $stage = 'server-initialize'

    try {
        $initialize = (& $profile.mysqld '--no-defaults' '--initialize-insecure' `
            "--basedir=$($profile.base)" "--datadir=$dataRoot" 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) {
            throw "mysqld --initialize-insecure failed: $initialize"
        }
        $server = Start-Process -FilePath $profile.mysqld -ArgumentList @(
            '--no-defaults', "--basedir=$($profile.base)", "--datadir=$dataRoot",
            '--bind-address=127.0.0.1', "--port=$port", '--mysqlx=0',
            "--pid-file=$pidFile", "--log-error=$errorLog", '--character-set-server=utf8mb4',
            '--collation-server=utf8mb4_unicode_ci'
        ) -PassThru -WindowStyle Hidden

        $ready = $false
        for ($attempt = 0; $attempt -lt 160; $attempt++) {
            $oldPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & $profile.mysqlAdmin '--no-defaults' '--protocol=TCP' '-h127.0.0.1' `
                "-P$port" '-uroot' '--connect-timeout=1' 'ping' *> $null
            $pingExitCode = $LASTEXITCODE
            $ErrorActionPreference = $oldPreference
            if ($pingExitCode -eq 0) {
                $ready = $true
                break
            }
            Start-Sleep -Milliseconds 250
        }
        if (-not $ready) {
            $log = if (Test-Path -LiteralPath $errorLog) {
                Get-Content -LiteralPath $errorLog -Raw -Encoding UTF8
            } else { '' }
            throw "Isolated MySQL $($profile.version) did not become ready: $log"
        }
        $runBoundProcessId = $null
        for ($attempt = 0; $attempt -lt 20; $attempt++) {
            $runBoundProcessId = Get-RunBoundMySqlProcessId -ExpectedPidFile $pidFile `
                -ExpectedDataRoot $dataRoot -ExpectedPort $port
            if ($null -ne $runBoundProcessId) {
                break
            }
            Start-Sleep -Milliseconds 250
        }
        if ($null -eq $runBoundProcessId) {
            $pidState = if (Test-Path -LiteralPath $pidFile -PathType Leaf) {
                Get-Content -LiteralPath $pidFile -Raw
            } else { 'missing' }
            $errorTail = if (Test-Path -LiteralPath $errorLog -PathType Leaf) {
                (Get-Content -LiteralPath $errorLog -Tail 20 -Encoding UTF8) -join ' '
            } else { 'missing' }
            throw "The ready MySQL listener has no process identity bound to this 042 IT run (pid file: $pidState; error: $errorTail)."
        }
        # mysqld may detach a Windows launcher process.  The pid file plus
        # command-line binding identifies the actual server process instead.
        $ownedServerProcessId = $runBoundProcessId
        Assert-LoopbackListenerOwnedBy -Port $port -ExpectedProcessId $ownedServerProcessId

        $runtime = (Invoke-MySqlText -Profile $profile -Port $port -Sql `
            "SELECT CONCAT_WS('|',VERSION(),@@version_comment,@@default_storage_engine,@@transaction_isolation);").stdout
        Assert-ExactOutput -Actual $runtime `
            -Expected "$($profile.version)|MySQL Community Server - GPL|InnoDB|REPEATABLE-READ" `
            -Stage 'server profile'

        $loginPath = "u3w-w3l-credit-v2-$($profile.version.Replace('.', '-'))-$suffix"
        $env:MYSQL_TEST_LOGIN_FILE = Join-Path $runRoot '.mylogin.cnf'
        $oldPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $profile.mysqlConfigEditor 'set' "--login-path=$loginPath" '--host=127.0.0.1' `
            "--port=$port" '--user=root' '--skip-warn' *> $null
        $loginExitCode = $LASTEXITCODE
        $ErrorActionPreference = $oldPreference
        if ($loginExitCode -ne 0 -or -not (Test-Path -LiteralPath $env:MYSQL_TEST_LOGIN_FILE -PathType Leaf)) {
            throw 'Could not create the isolated MySQL login path for canonical current-read.'
        }

        $stage = 'canonical-first-apply'
        Initialize-042Prerequisites -Profile $profile -Port $port -Database $database
        Invoke-042Migration -Profile $profile -Port $port -Database $database `
            -ExpectedSha256 $migrationSha256 | Out-Null
        $firstState = (Invoke-MySqlText -Profile $profile -Port $port -Database $database -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM u3w_schema_migration WHERE version='public_init_042'
    AND description='RUNNING:Independent Board default-off skill-consume v2 credit ledger'),
  (SELECT COUNT(*) FROM u3w_schema_migration WHERE version='20260723_skill_consume_credit_ledger_v2_042'
    AND description='Independent Board default-off skill-consume v2 credit ledger'),
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()
    AND event_object_table IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()
    AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
    AND constraint_type='CHECK' AND enforced='YES' AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE()
    AND table_name='fbs_skill_credit_operation_v2' AND referenced_table_name='fbs_skill_usage_record'),
  (SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema=DATABASE()
    AND routine_name IN ('u3w_assert_skill_consume_credit_ledger_v2_triggers_20260723','u3w_migrate_skill_consume_credit_ledger_v2_20260723','u3w_finalize_skill_consume_credit_ledger_v2_20260723')));
"@).stdout
        Assert-ExactOutput -Actual $firstState -Expected '1|1|4|8|4|16|0|0' -Stage 'first apply'

        $stage = 'canonical-bounded-replay'
        Invoke-042Migration -Profile $profile -Port $port -Database $database `
            -ExpectedSha256 $migrationSha256 | Out-Null
        $replayState = (Invoke-MySqlText -Profile $profile -Port $port -Database $database -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM u3w_schema_migration WHERE version='public_init_042'
    AND description='RUNNING:Independent Board default-off skill-consume v2 credit ledger'),
  (SELECT COUNT(*) FROM u3w_schema_migration WHERE version='20260723_skill_consume_credit_ledger_v2_042'
    AND description='Independent Board default-off skill-consume v2 credit ledger'),
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()
    AND event_object_table IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()
    AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
    AND constraint_type='CHECK' AND enforced='YES' AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE()
    AND table_name='fbs_skill_credit_operation_v2' AND referenced_table_name='fbs_skill_usage_record'),
  (SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema=DATABASE()
    AND routine_name IN ('u3w_assert_skill_consume_credit_ledger_v2_triggers_20260723','u3w_migrate_skill_consume_credit_ledger_v2_20260723','u3w_finalize_skill_consume_credit_ledger_v2_20260723')));
"@).stdout
        Assert-ExactOutput -Actual $replayState -Expected $firstState -Stage 'bounded replay'

        # Each rejected vector receives its own disposable schema.  That keeps
        # failure residue observable (including helper routines) without
        # compromising the canonical current-read proof.
        $stage = 'missing-public-receipt'
        $missingReceiptDatabase = "${database}_nr"
        Initialize-042Prerequisites -Profile $profile -Port $port `
            -Database $missingReceiptDatabase -CreatePublicReceipt $false
        Invoke-ExpectedMigrationFailure -Profile $profile -Port $port `
            -Database $missingReceiptDatabase -ExpectedSha256 $migrationSha256 `
            -ExpectedError 'Skill consume v2 ledger requires one exact public RUNNING receipt' `
            -Stage 'missing-public-receipt'
        Assert-RejectedMigrationState -Profile $profile -Port $port `
            -Database $missingReceiptDatabase -Expected '0|0|0|0|3|1' `
            -Stage 'missing-public-receipt safety state'

        $stage = 'partial-target-state'
        $partialStateDatabase = "${database}_pt"
        Initialize-042Prerequisites -Profile $profile -Port $port -Database $partialStateDatabase
        Invoke-MySqlText -Profile $profile -Port $port -Database $partialStateDatabase -Sql @"
CREATE TABLE fbs_skill_credit_account_v2 (account_id CHAR(36) NOT NULL PRIMARY KEY)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"@ | Out-Null
        Invoke-ExpectedMigrationFailure -Profile $profile -Port $port `
            -Database $partialStateDatabase -ExpectedSha256 $migrationSha256 `
            -ExpectedError 'Skill consume v2 ledger partial or receipt-drifted state is not recoverable' `
            -Stage 'partial-target-state'
        Assert-RejectedMigrationState -Profile $profile -Port $port `
            -Database $partialStateDatabase -Expected '1|0|1|0|3|1' `
            -Stage 'partial-target-state safety state'

        $stage = 'post-apply-metadata-drift'
        $metadataDriftDatabase = "${database}_md"
        Initialize-042Prerequisites -Profile $profile -Port $port -Database $metadataDriftDatabase
        Invoke-042Migration -Profile $profile -Port $port -Database $metadataDriftDatabase `
            -ExpectedSha256 $migrationSha256 | Out-Null
        Invoke-MySqlText -Profile $profile -Port $port -Database $metadataDriftDatabase `
            -Sql 'ALTER TABLE fbs_skill_credit_account_v2 ADD COLUMN runner_drift_probe INT NULL;' | Out-Null
        Invoke-ExpectedMigrationFailure -Profile $profile -Port $port `
            -Database $metadataDriftDatabase -ExpectedSha256 $migrationSha256 `
            -ExpectedError 'Skill consume v2 ledger exact current-read contract has drifted' `
            -Stage 'post-apply-metadata-drift'
        Assert-RejectedMigrationState -Profile $profile -Port $port `
            -Database $metadataDriftDatabase -Expected '1|1|4|8|3|1' `
            -Stage 'post-apply-metadata-drift safety state'

        $stage = 'post-apply-trigger-body-drift'
        $triggerDriftDatabase = "${database}_td"
        Initialize-042Prerequisites -Profile $profile -Port $port -Database $triggerDriftDatabase
        Invoke-042Migration -Profile $profile -Port $port -Database $triggerDriftDatabase `
            -ExpectedSha256 $migrationSha256 | Out-Null
        Invoke-MySqlText -Profile $profile -Port $port -Database $triggerDriftDatabase -Sql @"
DROP TRIGGER trg_skill_credit_account_v2_transition;
CREATE TRIGGER trg_skill_credit_account_v2_transition
BEFORE UPDATE ON fbs_skill_credit_account_v2
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'runner trigger drift';
"@ | Out-Null
        Invoke-ExpectedMigrationFailure -Profile $profile -Port $port `
            -Database $triggerDriftDatabase -ExpectedSha256 $migrationSha256 `
            -ExpectedError 'Skill consume v2 ledger trigger body contract has drifted' `
            -Stage 'post-apply-trigger-body-drift'
        Assert-RejectedMigrationState -Profile $profile -Port $port `
            -Database $triggerDriftDatabase -Expected '1|1|4|8|3|1' `
            -Stage 'post-apply-trigger-body-drift safety state'

        $stage = 'canonical-current-read'
        Invoke-MySqlText -Profile $profile -Port $port -Database $database -Sql @"
UPDATE u3w_schema_migration
SET description='APPLIED:Independent Board default-off skill-consume v2 credit ledger'
WHERE version='public_init_042'
  AND description='RUNNING:Independent Board default-off skill-consume v2 credit ledger';
"@ | Out-Null
        Invoke-CanonicalCurrentRead -LoginPath $loginPath -MySqlExe $profile.mysql `
            -Database $database -ExpectedInitializerSha256 $sourceSha256.initializer
        $canonicalState = (Invoke-MySqlText -Profile $profile -Port $port -Database $database -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM u3w_schema_migration WHERE version='public_init_042'
    AND description='APPLIED:Independent Board default-off skill-consume v2 credit ledger'),
  (SELECT COUNT(*) FROM u3w_schema_migration WHERE version='20260723_skill_consume_credit_ledger_v2_042'
    AND description='Independent Board default-off skill-consume v2 credit ledger'),
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')),
  (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()
    AND event_object_table IN ('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2','fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')));
"@).stdout
        Assert-ExactOutput -Actual $canonicalState -Expected '1|1|4|8' -Stage 'canonical current-read'

        $stage = 'application-transaction-matrix'
        if (Test-Path -LiteralPath $surefireReport -PathType Leaf) {
            Remove-Item -LiteralPath $surefireReport -Force
        }
        $env:JAVA_HOME = $resolvedJavaHome
        $env:PATH = (Join-Path $resolvedJavaHome 'bin') + ';' + $priorPath
        $jdbcUrl = "jdbc:mysql://127.0.0.1:$port/$database" +
            '?useAffectedRows=false&connectionTimeZone=Asia%2FShanghai&useSSL=false&allowPublicKeyRetrieval=true'
        $quotedJdbcProperty = '"-Dindependent.board.skill.consume.credit.mysql.it.url=' +
            $jdbcUrl + '"'
        $mavenArguments = @(
            '-o', '-pl', 'FBSir-business', '-am',
            '-Dtest=SkillConsumeCreditLedgerV2MysqlIT',
            '-Dsurefire.failIfNoSpecifiedTests=false',
            '-Dindependent.board.skill.consume.credit.mysql.it.allowDestructive=true',
            $quotedJdbcProperty,
            '-Dindependent.board.skill.consume.credit.mysql.it.username=root',
            '-Dindependent.board.skill.consume.credit.mysql.it.password=',
            'test'
        )
        $testStartedAt = [DateTimeOffset]::UtcNow
        & $resolvedMavenCommand @mavenArguments
        if ($LASTEXITCODE -ne 0) {
            throw "042 writer MySQL IT failed with Maven exit code $LASTEXITCODE"
        }
        $testEvidence = Read-SurefireEvidence -ReportPath $surefireReport `
            -StartedAt $testStartedAt

        $results.Add([pscustomobject]@{
            version = $profile.version
            firstApply = $firstState
            boundedReplay = $replayState
            canonicalCurrentRead = $canonicalState
            negativeMatrix = [ordered]@{
                missingPublicReceipt = 'PASS:0|0|0|0|3|1'
                partialTargetState = 'PASS:1|0|1|0|3|1'
                postApplyMetadataDrift = 'PASS:1|1|4|8|3|1'
                postApplyTriggerBodyDrift = 'PASS:1|1|4|8|3|1'
            }
            applicationTransactionMatrix = $testEvidence
            productionConnectionUsed = $false
        })
    }
    catch {
        $rawMetadataDigests = ''
        try {
            $rawMetadataDigests = Get-042RawMetadataDigests -Profile $profile `
                -Port $port -Database $database
        }
        catch {
            $rawMetadataDigests = "UNAVAILABLE:$($_.Exception.Message)"
        }
        $failureReceipt = [ordered]@{
            kind = 'fbsir.independent-board.skill-consume-credit-ledger-v2.mysql-it.failure/v1'
            mysqlVersion = $profile.version
            stage = $stage
            migrationSha256 = $migrationSha256
            initializerSha256 = $sourceSha256.initializer
            runnerSha256 = $sourceSha256.runner
            productionConnectionUsed = $false
            rawMetadataDigests = $rawMetadataDigests
            error = $_.Exception.Message
        }
        Write-Error ($failureReceipt | ConvertTo-Json -Compress)
        throw
    }
    finally {
        if ($null -ne $ownedServerProcessId -or ($null -ne $server -and -not $server.HasExited)) {
            $oldPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & $profile.mysqlAdmin '--no-defaults' '--protocol=TCP' '-h127.0.0.1' `
                "-P$port" '-uroot' 'shutdown' *> $null
            $ErrorActionPreference = $oldPreference
        }
        if ($null -ne $ownedServerProcessId) {
            for ($attempt = 0; $attempt -lt 40; $attempt++) {
                if ($null -eq (Get-Process -Id $ownedServerProcessId -ErrorAction SilentlyContinue)) {
                    break
                }
                Start-Sleep -Milliseconds 250
            }
            if ($null -ne (Get-Process -Id $ownedServerProcessId -ErrorAction SilentlyContinue)) {
                $ownedServer = Get-CimInstance Win32_Process -Filter "ProcessId=$ownedServerProcessId"
                if ($null -eq $ownedServer -or [string]::IsNullOrWhiteSpace($ownedServer.CommandLine) -or
                    -not $ownedServer.CommandLine.Contains($dataRoot) -or
                    -not $ownedServer.CommandLine.Contains("--port=$port")) {
                    throw "Refusing to terminate a process not bound to this 042 MySQL IT run: $ownedServerProcessId"
                }
                Stop-Process -Id $ownedServerProcessId -Force -ErrorAction Stop
            }
        }
        if ($null -ne $server) {
            $server.Refresh()
            if (-not $server.HasExited) {
                $server.WaitForExit(10000) | Out-Null
            }
            if (-not $server.HasExited) {
                # This is the exact launcher process created by this runner;
                # stop it only after the run-bound mysqld has been stopped.
                Stop-Process -Id $server.Id -Force -ErrorAction Stop
            }
            $server.Dispose()
        }
        if (@(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue).Count -ne 0) {
            throw "Disposable 042 MySQL loopback port $port remained open after shutdown."
        }
        if ($null -eq $priorLoginFile) {
            [Environment]::SetEnvironmentVariable('MYSQL_TEST_LOGIN_FILE', $null, 'Process')
        }
        else {
            $env:MYSQL_TEST_LOGIN_FILE = $priorLoginFile
        }
        if ($null -eq $priorJavaHome) {
            [Environment]::SetEnvironmentVariable('JAVA_HOME', $null, 'Process')
        }
        else {
            $env:JAVA_HOME = $priorJavaHome
        }
        $env:PATH = $priorPath
        if (Test-Path -LiteralPath $runRoot -PathType Container) {
            Remove-SafeRunDirectory -Path $runRoot
            $cleaned = $true
        }
    }
    if (-not $cleaned) {
        throw "042 MySQL IT work directory was not cleaned: $runRoot"
    }
    $results[$results.Count - 1] | Add-Member -NotePropertyName workDirectoryCleaned -NotePropertyValue $true
}

if ($results.Count -ne $requestedVersions.Count -or
    (($results.version | Sort-Object) -join ',') -ne (($requestedVersions | Sort-Object) -join ',')) {
    throw '042 MySQL IT did not complete the requested exact version matrix.'
}

$result = if ($requestedVersions.Count -eq 2) {
    'PASS_LOCAL_DUAL_MYSQL_APPLICATION_TRANSACTION_MATRIX'
} else {
    'PASS_LOCAL_MYSQL_APPLICATION_TRANSACTION_MATRIX'
}
[pscustomobject]@{
    kind = 'fbsir.independent-board.skill-consume-credit-ledger-v2.mysql-it/v1'
    result = $result
    versions = @($results)
    sourceSha256 = $sourceSha256
} | ConvertTo-Json -Depth 6
