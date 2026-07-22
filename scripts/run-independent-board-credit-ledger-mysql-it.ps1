[CmdletBinding()]
param(
    [switch]$AllowDestructiveTest,

    [string[]]$MySqlBinDirectories = @(),

    [string]$MavenCommand = '',

    [string]$JavaHome = '',

    [switch]$EmitMetadataSnapshot,

    [switch]$MetadataProbeOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AllowDestructiveTest) {
    throw 'Explicit -AllowDestructiveTest consent is required.'
}

$repoRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$workRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repoRoot 'work\independent-board-credit-ledger-mysql-it'))
$diagnosticsRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repoRoot 'work\diagnostics\independent-board-credit-ledger-mysql-it'))
$migrationRelative = 'sql\update_20260722_independent_board_credit_ledger.sql'
$initializerRelative = 'scripts\init-database.ps1'
$javaItRelative = 'FBSir-business\src\test\java\com\wx\fbsir\business\board\credit\integration\IndependentBoardCreditLedgerMysqlIT.java'
$migrationPath = Join-Path $repoRoot $migrationRelative
$initializerPath = Join-Path $repoRoot $initializerRelative
$javaItPath = Join-Path $repoRoot $javaItRelative
$surefireReport = Join-Path $repoRoot (
    'FBSir-business\target\surefire-reports\TEST-com.wx.fbsir.business.board.credit.integration.' +
    'IndependentBoardCreditLedgerMysqlIT.xml')
$expectedTests = 5
$requiredVersions = @('8.0.30', '8.4.8')
$expectedColumnMetadataDigest = '4717b6466040c2b33513ef1fb92ccb9044e08a00fea41edd7ba617192c9c8d00'
$expectedIndexMetadataDigest = '3975985e059c133c0e91ef274702e5d270e4392b7b55b724b1d0d031b76f23cf'
$expectedForeignKeyMetadataDigest = '412a5276aca76d608111eecf0f2297dce0ebe299a35c6d106a4aa09da004ea67'
$expectedCheckMetadataDigest = '3826d266d39d91f3faada93659bff5bf347c5f4a3c05a73ad96d70a88d7d2e92'
$expectedTriggerMetadataDigest = 'f1e99b6123b4ef1ce51b1153d506a2d983877c7b59257febb7873feb6fc764da'

function Get-LoopbackEphemeralPort {
    $listener = New-Object System.Net.Sockets.TcpListener(
        [System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Test-LoopbackPortListening {
    param([Parameter(Mandatory = $true)][int]$TargetPort)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect('127.0.0.1', $TargetPort, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(250)) {
            return $false
        }
        $client.EndConnect($pending)
        return $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
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

    $candidates = New-Object System.Collections.Generic.List[string]
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

    $candidates = New-Object System.Collections.Generic.List[string]
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

    $candidates = New-Object System.Collections.Generic.List[string]
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
    $candidates.Add('C:\Program Files\MySQL\MySQL Server 8.0\bin')
    $candidates.Add('C:\Program Files\MySQL\MySQL Server 8.4\bin')

    $byVersion = @{}
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $full = [System.IO.Path]::GetFullPath($candidate)
        $mysqld = Join-Path $full 'mysqld.exe'
        $mysql = Join-Path $full 'mysql.exe'
        $mysqlAdmin = Join-Path $full 'mysqladmin.exe'
        $mysqlConfigEditor = Join-Path $full 'mysql_config_editor.exe'
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
        if (-not $match.Success) {
            continue
        }
        $version = $match.Groups['version'].Value
        if ($version -in $requiredVersions -and -not $byVersion.ContainsKey($version)) {
            $byVersion[$version] = [pscustomobject]@{
                version = $version
                bin = $full
                base = Split-Path -Parent $full
                mysqld = $mysqld
                mysql = $mysql
                mysqlAdmin = $mysqlAdmin
                mysqlConfigEditor = $mysqlConfigEditor
            }
        }
    }

    $missing = @($requiredVersions | Where-Object { -not $byVersion.ContainsKey($_) })
    if ($missing.Count -ne 0) {
        throw "The exact dual MySQL matrix is required; missing: $($missing -join ', ')."
    }
    return @($requiredVersions | ForEach-Object { $byVersion[$_] })
}

function Assert-SafeRunDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$AllowedRoot
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $fullRoot = [System.IO.Path]::GetFullPath($AllowedRoot).TrimEnd('\')
    if (-not $fullPath.StartsWith(
            $fullRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing cleanup outside the dedicated credit IT root: $fullPath"
    }
    if ((Split-Path -Leaf $fullPath) -notmatch
        '^run-mysql-(8-0-30|8-4-8)-[0-9]{8}T[0-9]{6}-[0-9]+-[0-9a-f]{8}$') {
        throw "Refusing cleanup without the exact credit IT run identity: $fullPath"
    }
    return $fullPath
}

function Get-VerifiedMySqlProcessId {
    param(
        [Parameter(Mandatory = $true)][string]$ExpectedPidFile,
        [Parameter(Mandatory = $true)][string]$ExpectedDataDirectory,
        [Parameter(Mandatory = $true)][int]$ExpectedPort
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
        throw "Refusing to manage PID $processId because it is not bound to this credit IT run."
    }
    return $processId
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
        throw "Unsafe disposable database name: $Database"
    }
    $arguments = "--no-defaults --protocol=tcp --host=127.0.0.1 --port=$Port " +
        '--user=root --default-character-set=utf8mb4 --batch --raw --skip-column-names'
    if ($Database) {
        $arguments += " --database=$Database"
    }
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Profile.mysql
    $startInfo.Arguments = $arguments
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw 'Unable to start the disposable mysql client.'
    }
    try {
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.BaseStream.Write($Bytes, 0, $Bytes.Length)
        $process.StandardInput.BaseStream.Flush()
        $process.StandardInput.Close()
        $process.WaitForExit()
        $result = [pscustomobject]@{
            exitCode = $process.ExitCode
            stdout = $stdoutTask.GetAwaiter().GetResult().Trim()
            stderr = $stderrTask.GetAwaiter().GetResult().Trim()
        }
        if (-not $AllowFailure -and $result.exitCode -ne 0) {
            throw "Disposable mysql exited with code $($result.exitCode): $($result.stderr)"
        }
        return $result
    }
    finally {
        $process.Dispose()
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

function Invoke-MySqlFile {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256,
        [switch]$AllowFailure
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $allowedSqlRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'sql')).TrimEnd('\') + '\'
    if (-not $fullPath.StartsWith(
            $allowedSqlRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not [string]::Equals(
            [System.IO.Path]::GetFileName($fullPath),
            'update_20260722_independent_board_credit_ledger.sql',
            [System.StringComparison]::Ordinal)) {
        throw "Refusing to apply a migration outside the exact credit-ledger path: $fullPath"
    }
    if (-not [string]::Equals(
            (Get-Sha256 -Path $fullPath), $ExpectedSha256,
            [System.StringComparison]::Ordinal)) {
        throw 'Credit-ledger migration bytes changed during the dual-runtime run.'
    }
    return Invoke-MySqlBytes -Profile $Profile -Port $Port `
        -Bytes ([System.IO.File]::ReadAllBytes($fullPath)) -Database $Database `
        -AllowFailure:$AllowFailure
}

function Initialize-PrerequisiteSchema {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database
    )

    $sql = @"
CREATE TABLE u3w_schema_migration (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  version VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  description VARCHAR(255) NOT NULL,
  installed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_u3w_schema_migration_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE sys_user (
  user_id BIGINT NOT NULL,
  points INT DEFAULT 0,
  status CHAR(1) NOT NULL DEFAULT '0',
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  update_time DATETIME(3) NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"@
    Invoke-MySqlText -Profile $Profile -Port $Port -Database $Database -Sql $sql | Out-Null
}

function Get-CreditSchemaState {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database
    )

    $sql = @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM u3w_schema_migration
    WHERE version='20260722_independent_board_credit_ledger_v1'
      AND description='Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger'),
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_type='BASE TABLE'
      AND table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE()
      AND table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema=DATABASE() AND constraint_type='CHECK'
      AND table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
  (SELECT COUNT(*) FROM information_schema.triggers
    WHERE trigger_schema=DATABASE()
      AND event_object_table IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
  (SELECT COUNT(*) FROM information_schema.routines
    WHERE routine_schema=DATABASE() AND routine_type='PROCEDURE'
      AND routine_name IN ('u3w_migrate_independent_board_credit_ledger_20260722',
        'u3w_finalize_independent_board_credit_ledger_20260722',
        'u3w_assert_independent_board_credit_triggers_20260722'))
);
"@
    return (Invoke-MySqlText -Profile $Profile -Port $Port `
        -Database $Database -Sql $sql).stdout
}

function Get-CreditMetadataSnapshot {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database
    )

    $sql = @"
SELECT CONCAT('CHECK|',tc.table_name,'|',tc.constraint_name,'|',
              HEX(CAST(cc.check_clause AS BINARY)))
FROM information_schema.table_constraints tc
INNER JOIN information_schema.check_constraints cc
  ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK' AND tc.enforced='YES'
  AND tc.table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')
ORDER BY tc.table_name,tc.constraint_name;
SELECT CONCAT('TRIGGER|',trigger_name,'|',event_object_table,'|',event_manipulation,'|',
              action_timing,'|',action_orientation,'|',HEX(CAST(action_statement AS BINARY)))
FROM information_schema.triggers
WHERE trigger_schema=DATABASE()
  AND event_object_table IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')
  AND action_condition IS NULL
ORDER BY trigger_name;
"@
    return (Invoke-MySqlText -Profile $Profile -Port $Port `
        -Database $Database -Sql $sql).stdout
}

function Get-CreditMetadataDigests {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database
    )

    $sql = @"
SET SESSION group_concat_max_len=1048576;
SELECT CONCAT_WS('|',
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
    'T:',HEX(CAST(table_name AS BINARY)),
    '|O:',LPAD(ordinal_position,3,'0'),
    '|N:',HEX(CAST(column_name AS BINARY)),
    '|Y:',HEX(CAST(column_type AS BINARY)),
    '|U:',HEX(CAST(is_nullable AS BINARY)),
    '|D:',IF(column_default IS NULL,'N',CONCAT('V:',HEX(CAST(column_default AS BINARY)))),
    '|C:',IF(character_set_name IS NULL,'N',CONCAT('V:',HEX(CAST(character_set_name AS BINARY)))),
    '|L:',IF(collation_name IS NULL,'N',CONCAT('V:',HEX(CAST(collation_name AS BINARY)))),
    '|E:',HEX(CAST(extra AS BINARY)),
    '|G:',IF(generation_expression IS NULL,'N',CONCAT('V:',HEX(CAST(generation_expression AS BINARY)))))
    ORDER BY table_name,ordinal_position SEPARATOR 0x0A),256)
  FROM information_schema.columns
  WHERE table_schema=DATABASE()
    AND table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
    'T:',HEX(CAST(table_name AS BINARY)),
    '|I:',HEX(CAST(index_name AS BINARY)),
    '|U:',non_unique,
    '|Y:',HEX(CAST(index_type AS BINARY)),
    '|V:',HEX(CAST(is_visible AS BINARY)),
    '|S:',seq_in_index,
    '|N:',IF(column_name IS NULL,'N',CONCAT('V:',HEX(CAST(column_name AS BINARY)))),
    '|X:',IF(expression IS NULL,'N',CONCAT('V:',HEX(CAST(expression AS BINARY)))),
    '|C:',IF(collation IS NULL,'N',CONCAT('V:',HEX(CAST(collation AS BINARY)))),
    '|P:',IF(sub_part IS NULL,'N',CONCAT('V:',sub_part)),
    '|Q:',HEX(CAST(nullable AS BINARY)))
    ORDER BY table_name,index_name,seq_in_index SEPARATOR 0x0A),256)
  FROM information_schema.statistics
  WHERE table_schema=DATABASE()
    AND table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
    'T:',HEX(CAST(rc.table_name AS BINARY)),
    '|C:',HEX(CAST(rc.constraint_name AS BINARY)),
    '|S:',IF(rc.unique_constraint_schema=DATABASE(),'SAME','OTHER'),
    '|K:',HEX(CAST(rc.unique_constraint_name AS BINARY)),
    '|R:',HEX(CAST(rc.referenced_table_name AS BINARY)),
    '|U:',HEX(CAST(rc.update_rule AS BINARY)),
    '|D:',HEX(CAST(rc.delete_rule AS BINARY)),
    '|M:',HEX(CAST(rc.match_option AS BINARY)),
    '|O:',kcu.ordinal_position,
    '|N:',HEX(CAST(kcu.column_name AS BINARY)),
    '|Q:',IF(kcu.referenced_table_schema=DATABASE(),'SAME','OTHER'),
    '|P:',HEX(CAST(kcu.referenced_column_name AS BINARY)),
    '|I:',IF(kcu.position_in_unique_constraint IS NULL,'N',CONCAT('V:',kcu.position_in_unique_constraint)))
    ORDER BY rc.table_name,rc.constraint_name,kcu.ordinal_position SEPARATOR 0x0A),256)
  FROM information_schema.referential_constraints rc
  INNER JOIN information_schema.key_column_usage kcu
    ON kcu.constraint_schema=rc.constraint_schema
   AND kcu.table_name=rc.table_name
   AND kcu.constraint_name=rc.constraint_name
  WHERE rc.constraint_schema=DATABASE()
    AND rc.unique_constraint_schema=DATABASE()
    AND kcu.referenced_table_schema=DATABASE()
    AND rc.table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
    'T:',HEX(CAST(tc.table_name AS BINARY)),
    '|C:',HEX(CAST(tc.constraint_name AS BINARY)),
    '|E:',HEX(CAST(tc.enforced AS BINARY)),
    '|X:',HEX(CAST(cc.check_clause AS BINARY)))
    ORDER BY tc.table_name,tc.constraint_name SEPARATOR 0x0A),256)
  FROM information_schema.table_constraints tc
  INNER JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
    AND tc.table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
    'N:',HEX(CAST(trigger_name AS BINARY)),
    '|T:',HEX(CAST(event_object_table AS BINARY)),
    '|E:',HEX(CAST(event_manipulation AS BINARY)),
    '|M:',HEX(CAST(action_timing AS BINARY)),
    '|O:',HEX(CAST(action_orientation AS BINARY)),
    '|C:',IF(action_condition IS NULL,'N',CONCAT('V:',HEX(CAST(action_condition AS BINARY)))),
    '|A:',HEX(CAST(action_statement AS BINARY)))
    ORDER BY trigger_name SEPARATOR 0x0A),256)
  FROM information_schema.triggers
  WHERE trigger_schema=DATABASE()
    AND event_object_table IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry'))
);
"@
    return (Invoke-MySqlText -Profile $Profile -Port $Port `
        -Database $Database -Sql $sql).stdout
}

function Get-CreditRawMetadataDigests {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database
    )

    $sql = @"
SET SESSION group_concat_max_len=1048576;
SELECT CONCAT_WS('|',VERSION(),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
    'T:',HEX(CAST(table_name AS BINARY)),'|O:',LPAD(ordinal_position,3,'0'),
    '|N:',HEX(CAST(column_name AS BINARY)),'|Y:',HEX(CAST(column_type AS BINARY)),
    '|U:',HEX(CAST(is_nullable AS BINARY)),'|D:',IF(column_default IS NULL,'N',CONCAT('V:',HEX(CAST(column_default AS BINARY)))),
    '|C:',IF(character_set_name IS NULL,'N',CONCAT('V:',HEX(CAST(character_set_name AS BINARY)))),
    '|L:',IF(collation_name IS NULL,'N',CONCAT('V:',HEX(CAST(collation_name AS BINARY)))),
    '|E:',HEX(CAST(extra AS BINARY)),'|G:',IF(generation_expression IS NULL,'N',CONCAT('V:',HEX(CAST(generation_expression AS BINARY)))))
    ORDER BY table_name,ordinal_position SEPARATOR 0x0A),256)
  FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
    'T:',HEX(CAST(table_name AS BINARY)),'|I:',HEX(CAST(index_name AS BINARY)),
    '|U:',non_unique,'|Y:',HEX(CAST(index_type AS BINARY)),'|V:',HEX(CAST(is_visible AS BINARY)),
    '|S:',seq_in_index,'|N:',IF(column_name IS NULL,'N',CONCAT('V:',HEX(CAST(column_name AS BINARY)))),
    '|X:',IF(expression IS NULL,'N',CONCAT('V:',HEX(CAST(expression AS BINARY)))),
    '|C:',IF(collation IS NULL,'N',CONCAT('V:',HEX(CAST(collation AS BINARY)))),
    '|P:',IF(sub_part IS NULL,'N',CONCAT('V:',sub_part)),'|Q:',HEX(CAST(nullable AS BINARY)))
    ORDER BY table_name,index_name,seq_in_index SEPARATOR 0x0A),256)
  FROM information_schema.statistics WHERE table_schema=DATABASE()
    AND table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
    'T:',HEX(CAST(rc.table_name AS BINARY)),'|C:',HEX(CAST(rc.constraint_name AS BINARY)),
    '|S:',IF(rc.unique_constraint_schema=DATABASE(),'SAME','OTHER'),
    '|K:',HEX(CAST(rc.unique_constraint_name AS BINARY)),'|R:',HEX(CAST(rc.referenced_table_name AS BINARY)),
    '|U:',HEX(CAST(rc.update_rule AS BINARY)),'|D:',HEX(CAST(rc.delete_rule AS BINARY)),
    '|M:',HEX(CAST(rc.match_option AS BINARY)),'|O:',kcu.ordinal_position,
    '|N:',HEX(CAST(kcu.column_name AS BINARY)),'|Q:',IF(kcu.referenced_table_schema=DATABASE(),'SAME','OTHER'),
    '|P:',HEX(CAST(kcu.referenced_column_name AS BINARY)),'|I:',IF(kcu.position_in_unique_constraint IS NULL,'N',CONCAT('V:',kcu.position_in_unique_constraint)))
    ORDER BY rc.table_name,rc.constraint_name,kcu.ordinal_position SEPARATOR 0x0A),256)
  FROM information_schema.referential_constraints rc
  INNER JOIN information_schema.key_column_usage kcu
    ON kcu.constraint_schema=rc.constraint_schema AND kcu.table_name=rc.table_name AND kcu.constraint_name=rc.constraint_name
  WHERE rc.constraint_schema=DATABASE() AND rc.unique_constraint_schema=DATABASE()
    AND kcu.referenced_table_schema=DATABASE()
    AND rc.table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
    'T:',HEX(CAST(tc.table_name AS BINARY)),
    '|C:',HEX(CAST(tc.constraint_name AS BINARY)),
    '|E:',HEX(CAST(tc.enforced AS BINARY)),
    '|X:',HEX(CAST(cc.check_clause AS BINARY)))
    ORDER BY tc.table_name,tc.constraint_name SEPARATOR 0x0A),256)
  FROM information_schema.table_constraints tc
  INNER JOIN information_schema.check_constraints cc
    ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
    AND tc.table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
    'N:',HEX(CAST(trigger_name AS BINARY)),
    '|T:',HEX(CAST(event_object_table AS BINARY)),
    '|E:',HEX(CAST(event_manipulation AS BINARY)),
    '|M:',HEX(CAST(action_timing AS BINARY)),
    '|O:',HEX(CAST(action_orientation AS BINARY)),
    '|C:',IF(action_condition IS NULL,'N',CONCAT('V:',HEX(CAST(action_condition AS BINARY)))),
    '|A:',HEX(CAST(action_statement AS BINARY)))
    ORDER BY trigger_name SEPARATOR 0x0A),256)
  FROM information_schema.triggers
  WHERE trigger_schema=DATABASE()
    AND event_object_table IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry'))
);
"@
    return (Invoke-MySqlText -Profile $Profile -Port $Port `
        -Database $Database -Sql $sql).stdout
}

function Assert-CreditMetadataDigests {
    param(
        [Parameter(Mandatory = $true)][string]$State,
        [Parameter(Mandatory = $true)][string]$Stage
    )

    $parts = @($State.Split('|'))
    $expectedDigests = @(
        $expectedColumnMetadataDigest,
        $expectedIndexMetadataDigest,
        $expectedForeignKeyMetadataDigest,
        $expectedCheckMetadataDigest,
        $expectedTriggerMetadataDigest
    )
    if ($parts.Count -ne $expectedDigests.Count) {
        throw "Credit-ledger $Stage exact raw metadata digest field count drifted: '$State'."
    }
    for ($index = 0; $index -lt $expectedDigests.Count; $index++) {
        if (-not [string]::Equals(
                $parts[$index], $expectedDigests[$index],
                [System.StringComparison]::Ordinal)) {
            throw "Credit-ledger $Stage exact raw metadata digest drifted " +
                "at field $($index + 1): '$State'."
        }
    }
}

function Assert-RejectedDriftSafetyState {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Stage
    )

    $sql = @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version='20260722_independent_board_credit_ledger_v1'
     AND description='Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version='20260722_independent_board_credit_ledger_v1'),
  IS_FREE_LOCK(SHA2(CONCAT(DATABASE(),':20260722_independent_board_credit_ledger_v1'),256)),
  (SELECT COUNT(*) FROM information_schema.routines
   WHERE routine_schema=DATABASE() AND routine_type='PROCEDURE'
     AND routine_name IN (
       'u3w_migrate_independent_board_credit_ledger_20260722',
       'u3w_finalize_independent_board_credit_ledger_20260722',
       'u3w_assert_independent_board_credit_triggers_20260722'))
);
"@
    $state = (Invoke-MySqlText -Profile $Profile -Port $Port `
        -Database $Database -Sql $sql).stdout
    if (-not [string]::Equals($state, '1|1|1|3', [System.StringComparison]::Ordinal)) {
        throw "Credit-ledger $Stage rejection changed its receipt or retained its named lock: '$state'."
    }
}

function Remove-CreditMigrationHelpers {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database
    )

    Invoke-MySqlText -Profile $Profile -Port $Port -Database $Database -Sql @'
DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_credit_ledger_20260722`;
DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_credit_ledger_20260722`;
DROP PROCEDURE IF EXISTS `u3w_assert_independent_board_credit_triggers_20260722`;
'@ | Out-Null
    $remainingHelpers = (Invoke-MySqlText -Profile $Profile -Port $Port `
        -Database $Database -Sql @'
SELECT COUNT(*) FROM information_schema.routines
WHERE routine_schema=DATABASE() AND routine_type='PROCEDURE'
  AND routine_name IN (
    'u3w_migrate_independent_board_credit_ledger_20260722',
    'u3w_finalize_independent_board_credit_ledger_20260722',
    'u3w_assert_independent_board_credit_triggers_20260722');
'@).stdout
    if ($remainingHelpers -ne '0') {
        throw "Credit-ledger migration helper cleanup failed: '$remainingHelpers'."
    }
}

function Assert-RejectedPreflightSafetyState {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database
    )

    $sql = @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version='20260722_independent_board_credit_ledger_v1'),
  IS_FREE_LOCK(SHA2(CONCAT(DATABASE(),':20260722_independent_board_credit_ledger_v1'),256)),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema=DATABASE()
     AND table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
  (SELECT COUNT(*) FROM information_schema.triggers
   WHERE trigger_schema=DATABASE()
     AND event_object_table IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')),
  (SELECT COUNT(*) FROM information_schema.routines
   WHERE routine_schema=DATABASE() AND routine_type='PROCEDURE'
     AND routine_name IN (
       'u3w_migrate_independent_board_credit_ledger_20260722',
       'u3w_finalize_independent_board_credit_ledger_20260722',
       'u3w_assert_independent_board_credit_triggers_20260722'))
);
"@
    $state = (Invoke-MySqlText -Profile $Profile -Port $Port `
        -Database $Database -Sql $sql).stdout
    if (-not [string]::Equals($state, '0|1|0|0|3', [System.StringComparison]::Ordinal)) {
        throw "Credit-ledger prerequisite rejection receipt/lock/target/helper state drifted: '$state'."
    }
}

function Get-CreditRuntimeState {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database
    )

    $sql = @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM sys_user WHERE user_id BETWEEN 70001 AND 70006),
  (SELECT COUNT(*) FROM fbs_credit_account WHERE user_id BETWEEN 70001 AND 70006),
  (SELECT COUNT(*) FROM fbs_credit_operation WHERE user_id BETWEEN 70001 AND 70006),
  (SELECT COUNT(*) FROM fbs_credit_entry e INNER JOIN fbs_credit_operation o
    ON o.operation_id=e.operation_id WHERE o.user_id BETWEEN 70001 AND 70006),
  (SELECT COALESCE(SUM(version),0) FROM fbs_credit_account
    WHERE user_id BETWEEN 70001 AND 70006),
  (SELECT COUNT(*) FROM fbs_credit_account a INNER JOIN sys_user u ON u.user_id=a.user_id
    WHERE a.user_id BETWEEN 70001 AND 70006 AND a.balance=COALESCE(u.points,0)),
  (SELECT COUNT(*) FROM information_schema.triggers
    WHERE trigger_schema=DATABASE() AND event_object_table='sys_user'
      AND trigger_name='credit_it_fail_projection')
);
"@
    return (Invoke-MySqlText -Profile $Profile -Port $Port `
        -Database $Database -Sql $sql).stdout
}

function Invoke-CreditInitializerCurrentRead {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$LoginPath,
        [switch]$AllowFailure
    )

    try {
        $output = (& $initializerPath -LoginPath $LoginPath `
            -MySqlExe $Profile.mysql -Database $Database `
            -CreditLedgerCurrentReadOnly *>&1 | Out-String).Trim()
        return [pscustomobject]@{
            exitCode = 0
            output = $output
        }
    }
    catch {
        $failure = ($_ | Out-String).Trim()
        if (-not $AllowFailure) {
            throw "Credit-ledger canonical current-read failed: $failure"
        }
        return [pscustomobject]@{
            exitCode = 1
            output = $failure
        }
    }
}

function Assert-CreditInitializerFailureSafetyState {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Stage
    )

    $state = (Invoke-MySqlText -Profile $Profile -Port $Port `
        -Database $Database -Sql @"
SELECT CONCAT_WS('|',
  IS_FREE_LOCK(SHA2(CONCAT(DATABASE(),':public-database-manifest:v1'),256)),
  (SELECT COUNT(*) FROM information_schema.routines
   WHERE routine_schema=DATABASE() AND routine_type='PROCEDURE'
     AND routine_name IN (
       'u3w_migrate_independent_board_credit_ledger_20260722',
       'u3w_finalize_independent_board_credit_ledger_20260722',
       'u3w_assert_independent_board_credit_triggers_20260722')),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version='20260722_independent_board_credit_ledger_v1'
     AND description='Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger'),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema=DATABASE()
     AND table_name IN ('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')));
"@).stdout
    if (-not [string]::Equals($state, '1|0|1|3', [System.StringComparison]::Ordinal)) {
        throw "Credit-ledger canonical current-read $Stage failure changed receipt, target or lock/helper state: '$state'."
    }
}

function Read-SurefireEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$ReportPath,
        [Parameter(Mandatory = $true)][DateTimeOffset]$StartedAt
    )

    if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
        throw "Credit-ledger Surefire report is missing: $ReportPath"
    }
    $reportFile = Get-Item -LiteralPath $ReportPath
    if ($reportFile.LastWriteTimeUtc -lt $StartedAt.UtcDateTime) {
        throw "Credit-ledger Surefire report is stale: $ReportPath"
    }
    [xml]$report = Get-Content -Raw -Encoding UTF8 -LiteralPath $ReportPath
    $suite = $report.testsuite
    $tests = [int]$suite.tests
    $failures = [int]$suite.failures
    $errors = [int]$suite.errors
    $skipped = [int]$suite.skipped
    if ($tests -ne $expectedTests -or $failures -ne 0 -or
        $errors -ne 0 -or $skipped -ne 0) {
        throw "Credit-ledger MySQL IT must pass exactly $expectedTests/$expectedTests; " +
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

function Write-Evidence {
    param(
        [Parameter(Mandatory = $true)]$Evidence,
        [Parameter(Mandatory = $true)][string]$Version
    )

    $versionDirectory = Join-Path $diagnosticsRoot ("mysql-$Version")
    New-Item -ItemType Directory -Path $versionDirectory -Force | Out-Null
    $stamp = [DateTimeOffset]::UtcNow.ToString(
        'yyyyMMddTHHmmss.fffZ', [System.Globalization.CultureInfo]::InvariantCulture)
    $destination = Join-Path $versionDirectory (
        "summary-mysql-$Version-$stamp-pid-$PID.json")
    if (Test-Path -LiteralPath $destination) {
        throw "Refusing to overwrite credit-ledger evidence: $destination"
    }
    $json = $Evidence | ConvertTo-Json -Depth 12
    if ($json -match '(?i)password|secret|accessToken|refreshToken') {
        throw 'Refusing to persist credit-ledger evidence containing a sensitive property.'
    }
    [System.IO.File]::WriteAllText(
        $destination,
        $json.Replace("`r`n", "`n").TrimEnd("`n") + "`n",
        [System.Text.UTF8Encoding]::new($false))
    $relativePath = $destination.Substring($repoRoot.Length).TrimStart('\')
    return [pscustomobject]@{
        path = $relativePath.Replace('\', '/')
        sha256 = Get-Sha256 -Path $destination
    }
}

$profiles = Resolve-MySqlProfiles -Requested $MySqlBinDirectories
$resolvedMaven = Resolve-MavenCommand -Requested $MavenCommand
$resolvedJavaHome = Resolve-JavaHome -Requested $JavaHome

$artifactPaths = [ordered]@{
    migration = $migrationPath
    canonicalInitializer = $initializerPath
    javaIntegrationTest = $javaItPath
    creditService = Join-Path $repoRoot (
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\credit\service\IndependentBoardCreditService.java')
    creditTransactionService = Join-Path $repoRoot (
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\credit\service\IndependentBoardCreditTransactionService.java')
    creditMapper = Join-Path $repoRoot (
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\credit\mapper\IndependentBoardCreditMapper.java')
    creditMapperXml = Join-Path $repoRoot (
        'FBSir-business\src\main\resources\mapper\board\IndependentBoardCreditMapper.xml')
    runner = $MyInvocation.MyCommand.Path
}
$artifactHashes = [ordered]@{}
foreach ($name in $artifactPaths.Keys) {
    $artifactHashes[$name] = Get-Sha256 -Path $artifactPaths[$name]
}
$migrationSha256 = $artifactHashes.migration
$results = New-Object System.Collections.Generic.List[object]
$metadataProbeResults = New-Object System.Collections.Generic.List[object]
$originalJavaHome = $env:JAVA_HOME
$originalPath = $env:PATH
$originalMySqlTestLoginFile = $env:MYSQL_TEST_LOGIN_FILE

try {
    $env:JAVA_HOME = $resolvedJavaHome
    $env:PATH = (Join-Path $resolvedJavaHome 'bin') + ';' + $originalPath

    foreach ($profile in $profiles) {
        foreach ($name in $artifactPaths.Keys) {
            if (-not [string]::Equals(
                    (Get-Sha256 -Path $artifactPaths[$name]), $artifactHashes[$name],
                    [System.StringComparison]::Ordinal)) {
                throw "Source artifact changed during the dual-runtime run: $name"
            }
        }

        $port = Get-LoopbackEphemeralPort
        if ($port -eq 3306 -or (Test-LoopbackPortListening -TargetPort $port)) {
            throw "Refusing unsafe or occupied loopback port $port."
        }
        $versionSlug = $profile.version.Replace('.', '-')
        $runId = 'run-mysql-{0}-{1}-{2}-{3}' -f $versionSlug,
            (Get-Date -Format 'yyyyMMddTHHmmss'), $PID,
            ([Guid]::NewGuid().ToString('N').Substring(0, 8))
        $runDirectory = Join-Path $workRoot $runId
        $dataDirectory = Join-Path $runDirectory 'data'
        $pidFile = Join-Path $runDirectory 'mysql.pid'
        $errorLog = Join-Path $runDirectory 'mysql-error.log'
        $databaseSuffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
        $database = "u3w_independent_board_credit_it_$databaseSuffix"
        $driftDatabases = [ordered]@{
            prerequisite = "u3w_credit_drift_prereq_$databaseSuffix"
            postApplyDependency = "u3w_credit_drift_postdep_$databaseSuffix"
            postApplyProjection = "u3w_credit_drift_projection_$databaseSuffix"
            column = "u3w_credit_drift_column_$databaseSuffix"
            checkClause = "u3w_credit_drift_check_$databaseSuffix"
            prefixIndex = "u3w_credit_drift_prefix_$databaseSuffix"
            invisibleIndex = "u3w_credit_drift_invisible_$databaseSuffix"
            triggerBody = "u3w_credit_drift_trigger_$databaseSuffix"
        }
        $serverProcess = $null
        $ready = $false
        try {
            New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
            $loginPath = "u3w-credit-it-$versionSlug"
            $loginFile = Join-Path $runDirectory 'mysql-login.cnf'
            $env:MYSQL_TEST_LOGIN_FILE = $loginFile
            Write-Host "Initializing disposable MySQL $($profile.version) in $runDirectory"
            $priorPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            $initializeOutput = & $profile.mysqld '--no-defaults' `
                "--basedir=$($profile.base)" "--datadir=$dataDirectory" `
                '--initialize-insecure' '--console' 2>&1
            $initializeExitCode = $LASTEXITCODE
            $ErrorActionPreference = $priorPreference
            if ($initializeExitCode -ne 0) {
                $initializeOutput | Write-Host
                throw "mysqld --initialize-insecure failed with exit code $initializeExitCode"
            }

            $serverArguments = @(
                '--no-defaults',
                "--basedir=`"$($profile.base)`"",
                "--datadir=`"$dataDirectory`"",
                "--port=$port",
                '--bind-address=127.0.0.1',
                '--skip-networking=0',
                '--mysqlx=0',
                "--pid-file=`"$pidFile`"",
                "--log-error=`"$errorLog`"",
                '--character-set-server=utf8mb4',
                '--collation-server=utf8mb4_unicode_ci'
            )
            $serverProcess = Start-Process -FilePath $profile.mysqld `
                -ArgumentList $serverArguments -WorkingDirectory $runDirectory `
                -WindowStyle Hidden -PassThru

            for ($attempt = 0; $attempt -lt 80; $attempt++) {
                $priorPreference = $ErrorActionPreference
                $ErrorActionPreference = 'Continue'
                & $profile.mysqlAdmin '--no-defaults' '--protocol=tcp' `
                    '--host=127.0.0.1' "--port=$port" '--user=root' `
                    '--connect-timeout=1' 'ping' *> $null
                $pingExitCode = $LASTEXITCODE
                $ErrorActionPreference = $priorPreference
                if ($pingExitCode -eq 0) {
                    $ready = $true
                    break
                }
                Start-Sleep -Milliseconds 250
            }
            if (-not $ready) {
                throw "Disposable MySQL $($profile.version) did not become ready."
            }
            $verifiedPid = Get-VerifiedMySqlProcessId -ExpectedPidFile $pidFile `
                -ExpectedDataDirectory $dataDirectory -ExpectedPort $port
            if ($null -eq $verifiedPid) {
                throw 'The ready MySQL listener has no run-bound PID-file process.'
            }

            $runtimeResult = Invoke-MySqlText -Profile $profile -Port $port -Sql (
                "SELECT CONCAT_WS('|',VERSION(),@@version_comment," +
                "@@default_storage_engine,@@transaction_isolation);")
            $runtime = $runtimeResult.stdout
            $expectedRuntime = "$($profile.version)|MySQL Community Server - GPL|InnoDB|REPEATABLE-READ"
            if (-not [string]::Equals(
                    $runtime, $expectedRuntime, [System.StringComparison]::Ordinal)) {
                throw "Disposable MySQL runtime contract drifted: '$runtime'."
            }

            $priorPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & $profile.mysqlConfigEditor 'set' "--login-path=$loginPath" `
                '--host=127.0.0.1' "--port=$port" '--user=root' '--skip-warn' *> $null
            $loginEditorExitCode = $LASTEXITCODE
            $ErrorActionPreference = $priorPreference
            if ($loginEditorExitCode -ne 0 -or
                -not (Test-Path -LiteralPath $loginFile -PathType Leaf)) {
                throw "Unable to create an isolated mysql login-path for canonical current-read."
            }

            $allDatabases = @($database) + @($driftDatabases.Values)
            $createDatabaseSql = ($allDatabases | ForEach-Object {
                "CREATE DATABASE ``$_`` CHARACTER SET utf8mb4 " +
                    'COLLATE utf8mb4_unicode_ci;'
            }) -join ' '
            Invoke-MySqlText -Profile $profile -Port $port `
                -Sql $createDatabaseSql | Out-Null
            Initialize-PrerequisiteSchema -Profile $profile -Port $port -Database $database
            foreach ($driftDatabase in $driftDatabases.Values) {
                Initialize-PrerequisiteSchema -Profile $profile -Port $port `
                    -Database $driftDatabase
            }

            Write-Host "Applying credit migration first-install on MySQL $($profile.version)"
            Invoke-MySqlFile -Profile $profile -Port $port -Path $migrationPath `
                -Database $database -ExpectedSha256 $migrationSha256 | Out-Null
            $firstState = Get-CreditSchemaState -Profile $profile -Port $port -Database $database
            if (-not [string]::Equals(
                    $firstState, '1|3|45|22|6|0', [System.StringComparison]::Ordinal)) {
                throw "Credit migration first-install state drifted: '$firstState'."
            }
            $firstMetadataDigests = Get-CreditMetadataDigests -Profile $profile `
                -Port $port -Database $database
            Assert-CreditMetadataDigests -State $firstMetadataDigests -Stage 'first-install'
            if ($EmitMetadataSnapshot) {
                Write-Output ("METADATA_SNAPSHOT_BEGIN|" + $profile.version)
                Write-Output ('RAW_METADATA_DIGESTS|' +
                    (Get-CreditRawMetadataDigests -Profile $profile -Port $port `
                        -Database $database))
                Write-Output (Get-CreditMetadataSnapshot -Profile $profile -Port $port `
                    -Database $database)
                Write-Output ("METADATA_SNAPSHOT_END|" + $profile.version)
            }

            Write-Host "Replaying exact credit migration on MySQL $($profile.version)"
            Invoke-MySqlFile -Profile $profile -Port $port -Path $migrationPath `
                -Database $database -ExpectedSha256 $migrationSha256 | Out-Null
            $replayState = Get-CreditSchemaState -Profile $profile -Port $port -Database $database
            if (-not [string]::Equals(
                    $replayState, $firstState, [System.StringComparison]::Ordinal)) {
                throw "Credit migration replay changed current state: '$replayState'."
            }
            $replayMetadataDigests = Get-CreditMetadataDigests -Profile $profile `
                -Port $port -Database $database
            Assert-CreditMetadataDigests -State $replayMetadataDigests -Stage 'replay'
            if ($MetadataProbeOnly) {
                $rawMetadataDigests = Get-CreditRawMetadataDigests -Profile $profile `
                    -Port $port -Database $database
                Write-Output ('RAW_METADATA_PROBE|' + $rawMetadataDigests)
                $metadataProbeResults.Add([pscustomobject]@{
                    version = $profile.version
                    rawDigests = $rawMetadataDigests
                })
                continue
            }

            $prerequisiteDriftDatabase = $driftDatabases.prerequisite
            Invoke-MySqlText -Profile $profile -Port $port `
                -Database $prerequisiteDriftDatabase `
                -Sql 'ALTER TABLE sys_user DROP COLUMN update_time;' | Out-Null
            $prerequisiteDriftResult = Invoke-MySqlFile -Profile $profile -Port $port `
                -Path $migrationPath -Database $prerequisiteDriftDatabase `
                -ExpectedSha256 $migrationSha256 -AllowFailure
            if ($prerequisiteDriftResult.exitCode -eq 0 -or
                -not $prerequisiteDriftResult.stderr.Contains(
                    'Credit ledger requires the five-column sys_user runtime projection')) {
                throw "Credit migration did not reject a missing sys_user runtime column: " +
                    "exit=$($prerequisiteDriftResult.exitCode) " +
                    "stderr=$($prerequisiteDriftResult.stderr)"
            }
            Assert-RejectedPreflightSafetyState -Profile $profile -Port $port `
                -Database $prerequisiteDriftDatabase
            Remove-CreditMigrationHelpers -Profile $profile -Port $port `
                -Database $prerequisiteDriftDatabase

            foreach ($driftDatabase in @(
                    $driftDatabases.postApplyDependency,
                    $driftDatabases.postApplyProjection,
                    $driftDatabases.column,
                    $driftDatabases.checkClause,
                    $driftDatabases.prefixIndex,
                    $driftDatabases.invisibleIndex,
                    $driftDatabases.triggerBody)) {
                Invoke-MySqlFile -Profile $profile -Port $port -Path $migrationPath `
                    -Database $driftDatabase -ExpectedSha256 $migrationSha256 | Out-Null
            }

            $postApplyDependencyDatabase = $driftDatabases.postApplyDependency
            $postApplyDependencyBaseline = Invoke-CreditInitializerCurrentRead `
                -Profile $profile -Database $postApplyDependencyDatabase `
                -LoginPath $loginPath
            if ($postApplyDependencyBaseline.exitCode -ne 0 -or
                -not $postApplyDependencyBaseline.output.Contains(
                    'PASS Independent Board credit ledger exact raw current-read')) {
                throw "Canonical credit current-read did not pass before dependency drift: " +
                    $postApplyDependencyBaseline.output
            }
            Invoke-MySqlText -Profile $profile -Port $port `
                -Database $postApplyDependencyDatabase `
                -Sql 'ALTER TABLE sys_user DROP COLUMN update_time;' | Out-Null
            $postApplyDependencyResult = Invoke-CreditInitializerCurrentRead `
                -Profile $profile -Database $postApplyDependencyDatabase `
                -LoginPath $loginPath -AllowFailure
            if ($postApplyDependencyResult.exitCode -eq 0 -or
                -not $postApplyDependencyResult.output.Contains(
                    'Independent Board credit ledger current-read drift at field 18')) {
                throw "Canonical credit current-read did not reject post-apply sys_user drift: " +
                    $postApplyDependencyResult.output
            }
            Assert-CreditInitializerFailureSafetyState -Profile $profile -Port $port `
                -Database $postApplyDependencyDatabase -Stage 'post-apply-dependency-drift'

            $postApplyProjectionDatabase = $driftDatabases.postApplyProjection
            Invoke-MySqlText -Profile $profile -Port $port `
                -Database $postApplyProjectionDatabase -Sql @'
INSERT INTO sys_user(user_id,points,status,del_flag,update_time)
VALUES (72001,10,'0','0',CURRENT_TIMESTAMP(3));
INSERT INTO fbs_credit_account(account_id,user_id,opening_balance,balance)
VALUES ('72001000-0000-4000-8000-000000000001',72001,10,10);
'@ | Out-Null
            $postApplyProjectionBaseline = Invoke-CreditInitializerCurrentRead `
                -Profile $profile -Database $postApplyProjectionDatabase `
                -LoginPath $loginPath
            if ($postApplyProjectionBaseline.exitCode -ne 0 -or
                -not $postApplyProjectionBaseline.output.Contains(
                    'PASS Independent Board credit ledger exact raw current-read')) {
                throw "Canonical credit current-read did not pass before projection drift: " +
                    $postApplyProjectionBaseline.output
            }
            Invoke-MySqlText -Profile $profile -Port $port `
                -Database $postApplyProjectionDatabase `
                -Sql 'UPDATE sys_user SET points=11 WHERE user_id=72001;' | Out-Null
            $postApplyProjectionResult = Invoke-CreditInitializerCurrentRead `
                -Profile $profile -Database $postApplyProjectionDatabase `
                -LoginPath $loginPath -AllowFailure
            if ($postApplyProjectionResult.exitCode -eq 0 -or
                -not $postApplyProjectionResult.output.Contains(
                    'Independent Board credit ledger current-read drift at field 19')) {
                throw "Canonical credit current-read did not reject projection mismatch: " +
                    $postApplyProjectionResult.output
            }
            Assert-CreditInitializerFailureSafetyState -Profile $profile -Port $port `
                -Database $postApplyProjectionDatabase -Stage 'projection-mismatch'

            $columnDriftDatabase = $driftDatabases.column
            Invoke-MySqlText -Profile $profile -Port $port -Database $columnDriftDatabase `
                -Sql 'ALTER TABLE fbs_credit_entry ADD COLUMN drift_probe INT NULL;' | Out-Null
            $driftResult = Invoke-MySqlFile -Profile $profile -Port $port `
                -Path $migrationPath -Database $columnDriftDatabase `
                -ExpectedSha256 $migrationSha256 -AllowFailure
            if ($driftResult.exitCode -eq 0 -or
                -not $driftResult.stderr.Contains(
                    'Credit ledger exact 45-column contract has drifted')) {
                throw "Credit migration did not fail closed on current drift: " +
                    "exit=$($driftResult.exitCode) stderr=$($driftResult.stderr)"
            }
            Assert-RejectedDriftSafetyState -Profile $profile -Port $port `
                -Database $columnDriftDatabase -Stage 'column-drift'
            Remove-CreditMigrationHelpers -Profile $profile -Port $port `
                -Database $columnDriftDatabase

            $checkDriftDatabase = $driftDatabases.checkClause
            Invoke-MySqlText -Profile $profile -Port $port -Database $checkDriftDatabase `
                -Sql "ALTER TABLE fbs_credit_account DROP CHECK chk_credit_account_status, ADD CONSTRAINT chk_credit_account_status CHECK (status = 'active');" | Out-Null
            $checkDriftResult = Invoke-MySqlFile -Profile $profile -Port $port `
                -Path $migrationPath -Database $checkDriftDatabase `
                -ExpectedSha256 $migrationSha256 -AllowFailure
            if ($checkDriftResult.exitCode -eq 0 -or
                -not $checkDriftResult.stderr.Contains(
                    'Credit ledger exact 22-check clause contract has drifted')) {
                throw "Credit migration did not fail closed on CHECK-clause drift: " +
                    "exit=$($checkDriftResult.exitCode) stderr=$($checkDriftResult.stderr)"
            }
            Assert-RejectedDriftSafetyState -Profile $profile -Port $port `
                -Database $checkDriftDatabase -Stage 'check-clause-drift'
            Remove-CreditMigrationHelpers -Profile $profile -Port $port `
                -Database $checkDriftDatabase

            $prefixIndexDriftDatabase = $driftDatabases.prefixIndex
            Invoke-MySqlText -Profile $profile -Port $port `
                -Database $prefixIndexDriftDatabase `
                -Sql 'ALTER TABLE fbs_credit_operation DROP INDEX uk_credit_operation_idempotency, ADD UNIQUE KEY uk_credit_operation_idempotency (idempotency_key(16));' | Out-Null
            $indexDriftResult = Invoke-MySqlFile -Profile $profile -Port $port `
                -Path $migrationPath -Database $prefixIndexDriftDatabase `
                -ExpectedSha256 $migrationSha256 -AllowFailure
            if ($indexDriftResult.exitCode -eq 0 -or
                -not $indexDriftResult.stderr.Contains(
                    'Credit ledger exact 20-index contract has drifted')) {
                throw "Credit migration did not fail closed on prefix-index drift: " +
                    "exit=$($indexDriftResult.exitCode) stderr=$($indexDriftResult.stderr)"
            }
            Assert-RejectedDriftSafetyState -Profile $profile -Port $port `
                -Database $prefixIndexDriftDatabase -Stage 'prefix-index-drift'
            Remove-CreditMigrationHelpers -Profile $profile -Port $port `
                -Database $prefixIndexDriftDatabase

            $invisibleIndexDriftDatabase = $driftDatabases.invisibleIndex
            Invoke-MySqlText -Profile $profile -Port $port `
                -Database $invisibleIndexDriftDatabase `
                -Sql 'ALTER TABLE fbs_credit_operation ALTER INDEX uk_credit_operation_idempotency INVISIBLE;' | Out-Null
            $invisibleIndexDriftResult = Invoke-MySqlFile -Profile $profile -Port $port `
                -Path $migrationPath -Database $invisibleIndexDriftDatabase `
                -ExpectedSha256 $migrationSha256 -AllowFailure
            if ($invisibleIndexDriftResult.exitCode -eq 0 -or
                -not $invisibleIndexDriftResult.stderr.Contains(
                    'Credit ledger exact 20-index contract has drifted')) {
                throw "Credit migration did not fail closed on invisible-index drift: " +
                    "exit=$($invisibleIndexDriftResult.exitCode) " +
                    "stderr=$($invisibleIndexDriftResult.stderr)"
            }
            Assert-RejectedDriftSafetyState -Profile $profile -Port $port `
                -Database $invisibleIndexDriftDatabase -Stage 'invisible-index-drift'
            Remove-CreditMigrationHelpers -Profile $profile -Port $port `
                -Database $invisibleIndexDriftDatabase

            $disabledTransitionTrigger = @'
DELIMITER $$
DROP TRIGGER IF EXISTS `trg_credit_account_transition`$$
CREATE TRIGGER `trg_credit_account_transition`
    BEFORE UPDATE ON `fbs_credit_account`
    FOR EACH ROW
BEGIN
    IF NOT (
        OLD.id <=> NEW.id
        AND OLD.account_id <=> NEW.account_id
        AND OLD.subject_type <=> NEW.subject_type
        AND OLD.user_id <=> NEW.user_id
        AND OLD.account_scope <=> NEW.account_scope
        AND OLD.currency_code <=> NEW.currency_code
        AND OLD.opening_balance <=> NEW.opening_balance
        AND OLD.status <=> NEW.status
        AND OLD.created_at <=> NEW.created_at
        AND NEW.balance BETWEEN 0 AND 2147483647
        AND NEW.version = OLD.version + 1
        AND NEW.last_entry_sequence = OLD.last_entry_sequence + 1
        AND NEW.version = NEW.last_entry_sequence
        AND NEW.last_entry_hash REGEXP '^[0-9a-f]{64}$'
        AND NEW.last_entry_hash <> OLD.last_entry_hash
        AND NEW.updated_at >= OLD.updated_at
        AND EXISTS (
            SELECT 1 FROM `fbs_credit_entry` e
            WHERE e.account_id = NEW.account_id
              AND e.sequence_no = NEW.last_entry_sequence
              AND e.balance_before = OLD.balance
              AND e.balance_after = NEW.balance
              AND e.previous_entry_hash = OLD.last_entry_hash
              AND e.entry_hash = NEW.last_entry_hash
        )
    ) AND FALSE THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Credit account transition contract violated';
    END IF;
END$$
DELIMITER ;
'@
            $triggerDriftDatabase = $driftDatabases.triggerBody
            Invoke-MySqlText -Profile $profile -Port $port -Database $triggerDriftDatabase `
                -Sql $disabledTransitionTrigger | Out-Null
            $triggerDriftResult = Invoke-MySqlFile -Profile $profile -Port $port `
                -Path $migrationPath -Database $triggerDriftDatabase `
                -ExpectedSha256 $migrationSha256 -AllowFailure
            if ($triggerDriftResult.exitCode -eq 0 -or
                -not $triggerDriftResult.stderr.Contains(
                    'Credit ledger exact six-trigger contract has drifted')) {
                throw "Credit migration did not fail closed on trigger-body drift: " +
                    "exit=$($triggerDriftResult.exitCode) stderr=$($triggerDriftResult.stderr)"
            }
            Assert-RejectedDriftSafetyState -Profile $profile -Port $port `
                -Database $triggerDriftDatabase -Stage 'trigger-body-drift'
            Remove-CreditMigrationHelpers -Profile $profile -Port $port `
                -Database $triggerDriftDatabase

            if (Test-Path -LiteralPath $surefireReport -PathType Leaf) {
                Remove-Item -LiteralPath $surefireReport -Force
            }
            $jdbcUrl = "jdbc:mysql://127.0.0.1:$port/$database" +
                '?useAffectedRows=false&connectionTimeZone=Asia%2FShanghai&useSSL=false&allowPublicKeyRetrieval=true'
            # Windows PowerShell invokes mvn.cmd through cmd.exe. Preserve the
            # ampersands as one JVM system-property argument instead of letting
            # cmd.exe interpret them as command separators.
            $quotedJdbcProperty = '"-Dindependent.board.credit.mysql.it.url=' +
                $jdbcUrl + '"'
            $mavenArguments = @(
                '-pl', 'FBSir-business', '-am',
                '-Dtest=IndependentBoardCreditLedgerMysqlIT',
                '-Dsurefire.failIfNoSpecifiedTests=false',
                '-Dindependent.board.credit.mysql.it.allowDestructive=true',
                $quotedJdbcProperty,
                '-Dindependent.board.credit.mysql.it.username=root',
                '-Dindependent.board.credit.mysql.it.password=',
                'test'
            )
            Write-Host "Running real Spring/MyBatis credit IT on MySQL $($profile.version)"
            $mavenStartedAt = [DateTimeOffset]::UtcNow
            & $resolvedMaven @mavenArguments
            if ($LASTEXITCODE -ne 0) {
                throw "Credit-ledger MySQL IT failed with Maven exit code $LASTEXITCODE"
            }
            $testEvidence = Read-SurefireEvidence -ReportPath $surefireReport `
                -StartedAt $mavenStartedAt
            $runtimeState = Get-CreditRuntimeState -Profile $profile -Port $port -Database $database
            if (-not [string]::Equals(
                    $runtimeState, '6|5|7|7|7|5|0', [System.StringComparison]::Ordinal)) {
                throw "Credit-ledger Java IT final state drifted: '$runtimeState'."
            }

            $evidence = [ordered]@{
                schemaVersion = 1
                status = 'passed'
                generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
                scope = 'local_disposable_mysql_credit_shadow_ledger'
                canPromoteProductionAuthority = $false
                mysql = [ordered]@{
                    version = $profile.version
                    versionComment = 'MySQL Community Server - GPL'
                    defaultStorageEngine = 'InnoDB'
                    transactionIsolation = 'REPEATABLE-READ'
                    mysqldSha256 = Get-Sha256 -Path $profile.mysqld
                    mysqlSha256 = Get-Sha256 -Path $profile.mysql
                    loopbackNonDefaultPort = $true
                }
                migration = [ordered]@{
                    version = '20260722_independent_board_credit_ledger_v1'
                    sha256 = $migrationSha256
                    firstInstallState = $firstState
                    replayState = $replayState
                    exactMetadataDigests = [ordered]@{
                        columns = $expectedColumnMetadataDigest
                        indexes = $expectedIndexMetadataDigest
                        foreignKeys = $expectedForeignKeyMetadataDigest
                        checks = $expectedCheckMetadataDigest
                        triggers = $expectedTriggerMetadataDigest
                    }
                    prerequisiteDriftRejected = $true
                    prerequisiteDriftErrorCode = 'CREDIT_LEDGER_SYS_USER_PROJECTION_DRIFT'
                    prerequisiteReceiptLockTargetAndHelperState = '0|1|0|0|3_then_helpers_cleaned'
                    canonicalInitializerCreditCurrentReadPassed = $true
                    postApplyDependencyDriftRejected = $true
                    postApplyDependencyDriftErrorCode = 'CREDIT_LEDGER_CURRENT_READ_SYS_USER_DEPENDENCY_DRIFT'
                    projectionMismatchRejected = $true
                    projectionMismatchErrorCode = 'CREDIT_LEDGER_CURRENT_READ_PROJECTION_MISMATCH'
                    currentReadFailureState = 'manifest_lock_free_1_helpers_0_receipt_1_target_tables_3'
                    currentDriftRejected = $true
                    driftErrorCode = 'CREDIT_LEDGER_EXACT_45_COLUMN_DRIFT'
                    checkClauseDriftRejected = $true
                    checkClauseDriftErrorCode = 'CREDIT_LEDGER_EXACT_CHECK_CLAUSE_DRIFT'
                    prefixIndexDriftRejected = $true
                    prefixIndexDriftErrorCode = 'CREDIT_LEDGER_EXACT_INDEX_DRIFT'
                    invisibleIndexDriftRejected = $true
                    invisibleIndexDriftErrorCode = 'CREDIT_LEDGER_EXACT_INDEX_DRIFT'
                    triggerBodyDriftRejected = $true
                    triggerBodyDriftErrorCode = 'CREDIT_LEDGER_EXACT_TRIGGER_BODY_DRIFT'
                    rejectedDriftReceiptLockAndHelperState = '1|1|1|3_then_helpers_cleaned'
                }
                javaIntegrationTest = $testEvidence
                finalRuntimeState = $runtimeState
                artifactSha256 = $artifactHashes
            }
            $evidenceFile = Write-Evidence -Evidence $evidence -Version $profile.version
            $evidence.evidenceFile = $evidenceFile
            $results.Add([pscustomobject]$evidence)
        }
        finally {
            if ($ready) {
                $priorPreference = $ErrorActionPreference
                $ErrorActionPreference = 'Continue'
                & $profile.mysqlAdmin '--no-defaults' '--protocol=tcp' `
                    '--host=127.0.0.1' "--port=$port" '--user=root' 'shutdown' *> $null
                $ErrorActionPreference = $priorPreference
                for ($attempt = 0; $attempt -lt 40; $attempt++) {
                    if (-not (Test-LoopbackPortListening -TargetPort $port)) {
                        break
                    }
                    Start-Sleep -Milliseconds 250
                }
            }
            if ($null -ne $serverProcess) {
                $serverProcess.Refresh()
                if (-not $serverProcess.HasExited) {
                    [void]$serverProcess.WaitForExit(10000)
                }
            }
            $ownedPid = Get-VerifiedMySqlProcessId -ExpectedPidFile $pidFile `
                -ExpectedDataDirectory $dataDirectory -ExpectedPort $port
            if ($null -eq $ownedPid -and $null -ne $serverProcess) {
                $candidate = Get-CimInstance Win32_Process `
                    -Filter "ProcessId=$($serverProcess.Id)"
                if ($null -ne $candidate) {
                    if ([string]::IsNullOrWhiteSpace($candidate.CommandLine) -or
                        -not $candidate.CommandLine.Contains($dataDirectory) -or
                        -not $candidate.CommandLine.Contains("--port=$port")) {
                        throw "Refusing to manage PID $($serverProcess.Id) because it is not bound to this credit IT run."
                    }
                    $ownedPid = $serverProcess.Id
                }
            }
            if ($null -ne $ownedPid) {
                Stop-Process -Id $ownedPid -Force
                Wait-Process -Id $ownedPid -Timeout 10 -ErrorAction SilentlyContinue
            }
            if ($null -ne $serverProcess) {
                $serverProcess.Dispose()
            }
            if (Test-LoopbackPortListening -TargetPort $port) {
                throw "Disposable MySQL loopback port $port remained open after shutdown."
            }
            if (Test-Path -LiteralPath $runDirectory) {
                $safeRunDirectory = Assert-SafeRunDirectory `
                    -Path $runDirectory -AllowedRoot $workRoot
                $cleanupError = $null
                for ($attempt = 0; $attempt -lt 20; $attempt++) {
                    try {
                        Remove-Item -LiteralPath $safeRunDirectory -Recurse -Force `
                            -ErrorAction Stop
                        $cleanupError = $null
                        break
                    }
                    catch {
                        $cleanupError = $_
                        Start-Sleep -Milliseconds 250
                    }
                }
                if ($null -ne $cleanupError -or (Test-Path -LiteralPath $safeRunDirectory)) {
                    throw "Unable to clean the dedicated credit IT run directory: $safeRunDirectory"
                }
            }
        }
    }
}
finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:PATH = $originalPath
    $env:MYSQL_TEST_LOGIN_FILE = $originalMySqlTestLoginFile
}

if ($MetadataProbeOnly) {
    if ($metadataProbeResults.Count -ne 2) {
        throw 'The exact dual MySQL raw metadata probe did not complete.'
    }
    [pscustomobject]@{
        status = 'metadata_probe_passed'
        results = @($metadataProbeResults.ToArray())
    } | ConvertTo-Json -Depth 5
    exit 0
}

$completedVersions = (@($results | ForEach-Object { $_.mysql.version } | Sort-Object) -join ',')
if ($results.Count -ne 2 -or $completedVersions -ne '8.0.30,8.4.8') {
    throw 'The exact dual MySQL credit-ledger matrix did not complete.'
}

[pscustomobject]@{
    status = 'passed'
    versions = @($results | ForEach-Object { $_.mysql.version })
    testsPerVersion = $expectedTests
    productionAuthorityPromoted = $false
    evidence = @($results | ForEach-Object { $_.evidenceFile })
} | ConvertTo-Json -Depth 8
