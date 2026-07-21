[CmdletBinding()]
param(
    [switch]$AllowDestructiveTest,

    [switch]$DirectOnly,

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
$workRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'work\independent-board-mysql-it'))
$diagnosticsRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repoRoot 'work\diagnostics\independent-board-mysql-transaction-it'))
$database = 'u3w_independent_board_it'
$canonicalDatabase = 'u3w_scratch_board_canonical_it'
$canonicalLoginPath = 'u3w-board-canonical-it'
$canonicalVerifier = Join-Path $PSScriptRoot 'verify-independent-board-live-database.ps1'
$expectedDirectTests = 56
$expectedRefreshSecurityTests = 3
$refreshSecuritySuiteName = 'com.wx.fbsir.business.board.oauth.service.IndependentBoardOAuthRefreshSecurityServiceTest'

function Resolve-MySqlBinDirectory {
    param([string]$Requested)

    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($Requested)) {
        $candidates.Add($Requested)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:U3W_MYSQL_BIN)) {
        $candidates.Add($env:U3W_MYSQL_BIN)
    }
    $pathMysql = Get-Command mysql.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $pathMysql) {
        $candidates.Add((Split-Path -Parent $pathMysql.Source))
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
            (Test-Path -LiteralPath (Join-Path $full 'mysqladmin.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $full 'mysql_config_editor.exe') -PathType Leaf)) {
            return $full
        }
    }
    throw 'A local MySQL 8 bin directory containing mysqld.exe, mysql.exe, mysqladmin.exe and mysql_config_editor.exe is required. Pass -MySqlBinDirectory, set U3W_MYSQL_BIN, add MySQL to PATH, or install it under Program Files.'
}

function Get-LoopbackEphemeralPort {
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 0)
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
    $requiredPrefix = $fullRoot + '\'
    if (-not $fullPath.StartsWith($requiredPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing cleanup outside the dedicated work root: $fullPath"
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
        throw "Refusing to manage PID $processId because its command line is not bound to this test run."
    }
    return $processId
}

function Get-FileEvidence {
    param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$RelativePath)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required evidence file is missing: $Path"
    }
    $file = Get-Item -LiteralPath $Path
    [pscustomobject]@{
        path = $RelativePath.Replace('\', '/')
        bytes = [long]$file.Length
        sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

function Get-StringSha256 {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($Value)
        return -join ($algorithm.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') })
    }
    finally {
        $algorithm.Dispose()
    }
}

function Write-AtomicSummaryEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$EvidenceRoot,
        [Parameter(Mandatory = $true)][string]$RuntimeVersion,
        [Parameter(Mandatory = $true)][string]$SummaryJson,
        [Parameter(Mandatory = $true)][DateTimeOffset]$CompletedAt
    )

    if ($RuntimeVersion -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
        throw "Refusing to persist evidence for an unsafe MySQL runtime version: '$RuntimeVersion'."
    }
    if ($SummaryJson -match '(?i)"(?:password|clientSecret|accessToken|refreshToken)"\s*:') {
        throw 'Refusing to persist a summary containing a sensitive JSON property.'
    }

    $fullEvidenceRoot = [System.IO.Path]::GetFullPath($EvidenceRoot).TrimEnd('\')
    $runtimeDirectory = [System.IO.Path]::GetFullPath(
        (Join-Path $fullEvidenceRoot ("mysql-{0}" -f $RuntimeVersion)))
    if (-not $runtimeDirectory.StartsWith(
            $fullEvidenceRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to persist evidence outside the dedicated diagnostics root: $runtimeDirectory"
    }

    $utcStamp = $CompletedAt.ToUniversalTime().ToString(
        'yyyyMMddTHHmmss.fffZ', [System.Globalization.CultureInfo]::InvariantCulture)
    $localStamp = $CompletedAt.ToString(
        'yyyyMMddTHHmmss.fffzzz', [System.Globalization.CultureInfo]::InvariantCulture).Replace(':', '')
    $fileName = 'summary-mysql-{0}-utc-{1}-local-{2}-pid-{3}.json' -f `
        $RuntimeVersion, $utcStamp, $localStamp, $PID
    $destination = Join-Path $runtimeDirectory $fileName
    # Keep the atomic staging name short. Handoff worktrees can have long roots;
    # repeating the full evidence filename here can exceed Windows MAX_PATH even
    # when the final immutable summary path itself is valid.
    $temporary = Join-Path $runtimeDirectory (
        '.summary-{0}-{1}.tmp' -f $PID, [Guid]::NewGuid().ToString('N').Substring(0, 8))
    if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT -and
        ($destination.Length -ge 260 -or $temporary.Length -ge 260)) {
        throw "Refusing evidence paths that exceed the Windows legacy path boundary: destination=$($destination.Length), temporary=$($temporary.Length)"
    }
    # Git stores this evidence as UTF-8/LF. Write those exact bytes up front so
    # the reported SHA remains valid after commit, clone and checkout.
    $content = $SummaryJson.Replace("`r`n", "`n").Replace("`r", "`n").TrimEnd("`n") + "`n"
    $encoding = [System.Text.UTF8Encoding]::new($false)

    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
    if (Test-Path -LiteralPath $destination) {
        throw "Refusing to overwrite an existing MySQL evidence summary: $destination"
    }
    try {
        [System.IO.File]::WriteAllText($temporary, $content, $encoding)
        $temporaryContent = [System.IO.File]::ReadAllText($temporary, $encoding)
        if (-not [string]::Equals($temporaryContent, $content, [StringComparison]::Ordinal)) {
            throw "Atomic MySQL evidence temporary-file verification failed: $temporary"
        }
        Move-Item -LiteralPath $temporary -Destination $destination
        if (-not (Test-Path -LiteralPath $destination -PathType Leaf) -or
            -not [string]::Equals(
                [System.IO.File]::ReadAllText($destination, $encoding),
                $content,
                [StringComparison]::Ordinal)) {
            throw "Atomic MySQL evidence destination verification failed: $destination"
        }
    }
    finally {
        if (Test-Path -LiteralPath $temporary) {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
}

function Get-ProcessEnvironmentSnapshot {
    param([Parameter(Mandatory = $true)][string[]]$Names)

    $current = [Environment]::GetEnvironmentVariables('Process')
    $snapshot = @{}
    foreach ($name in $Names) {
        $snapshot[$name] = [pscustomobject]@{
            Exists = $current.Contains($name)
            Value = if ($current.Contains($name)) { [string]$current[$name] } else { $null }
        }
    }
    return $snapshot
}

function Restore-ProcessEnvironment {
    param([Parameter(Mandatory = $true)]$Snapshot)

    foreach ($name in $Snapshot.Keys) {
        $entry = $Snapshot[$name]
        if ($entry.Exists) {
            [Environment]::SetEnvironmentVariable($name, $entry.Value, 'Process')
        }
        else {
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
    }
}

function Test-ProcessEnvironmentMatchesSnapshot {
    param([Parameter(Mandatory = $true)]$Snapshot)

    $current = [Environment]::GetEnvironmentVariables('Process')
    foreach ($name in $Snapshot.Keys) {
        $entry = $Snapshot[$name]
        $exists = $current.Contains($name)
        if ($exists -ne $entry.Exists) {
            return $false
        }
        if ($exists -and -not [string]::Equals(
                [string]$current[$name], [string]$entry.Value, [StringComparison]::Ordinal)) {
            return $false
        }
    }
    return $true
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

function Read-DirectTestEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$ReportPath,
        [Parameter(Mandatory = $true)][DateTimeOffset]$StartedAt
    )

    if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
        throw "Independent Board MySQL Surefire report is missing: $ReportPath"
    }
    $reportFile = Get-Item -LiteralPath $ReportPath
    if ($reportFile.LastWriteTimeUtc -lt $StartedAt.UtcDateTime) {
        throw "Independent Board MySQL Surefire report is stale: $ReportPath"
    }
    $reportText = Get-Content -LiteralPath $ReportPath -Raw -Encoding UTF8
    try {
        [xml]$report = $reportText
    }
    catch {
        throw "Independent Board MySQL Surefire report is invalid XML: $($_.Exception.Message)"
    }
    $suite = $report.testsuite
    $tests = [int]$suite.tests
    $failures = [int]$suite.failures
    $errors = [int]$suite.errors
    $skipped = [int]$suite.skipped
    if ($tests -ne $expectedDirectTests -or
        $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
        throw "Independent Board MySQL direct IT must pass exactly $expectedDirectTests/$expectedDirectTests; found tests=$tests failures=$failures errors=$errors skipped=$skipped."
    }

    $classpathProperty = @($suite.properties.property | Where-Object { $_.name -eq 'java.class.path' })
    if ($classpathProperty.Count -ne 1) {
        throw 'Independent Board MySQL Surefire report does not contain exactly one java.class.path property.'
    }
    $connectorVersionMatch = [regex]::Match(
        [string]$classpathProperty[0].value,
        'com[\\/]mysql[\\/]mysql-connector-j[\\/](?<version>[^\\/;]+)[\\/]mysql-connector-j-')
    if (-not $connectorVersionMatch.Success) {
        throw 'Independent Board MySQL Surefire report does not identify the Connector/J artifact version.'
    }
    $connectorVersion = $connectorVersionMatch.Groups['version'].Value
    $systemOutNode = $report.SelectSingleNode(
        '/*[local-name()="testsuite"]/*[local-name()="system-out"]')
    $systemOut = if ($null -ne $systemOutNode) { $systemOutNode.InnerText } else { '' }
    $connectorIdentityMatch = [regex]::Match(
        $systemOut,
        'connector=(?<identity>MySQL Connector/J mysql-connector-j-[^\r\n<]+)')
    if (-not $connectorIdentityMatch.Success) {
        $connectorIdentityMatch = [regex]::Match(
            $reportText,
            'connector=(?<identity>MySQL Connector/J mysql-connector-j-[^\r\n<]+)')
    }
    if (-not $connectorIdentityMatch.Success -or
        -not $connectorIdentityMatch.Groups['identity'].Value.Contains($connectorVersion)) {
        throw 'Independent Board MySQL Surefire report does not contain the exact runtime Connector/J identity.'
    }

    return [pscustomobject]@{
        test = 'IndependentBoardMysqlTransactionIT'
        expectedTests = $expectedDirectTests
        tests = $tests
        passed = $tests - $failures - $errors - $skipped
        failures = $failures
        errors = $errors
        skipped = $skipped
        connectorVersion = $connectorVersion
        connectorIdentity = $connectorIdentityMatch.Groups['identity'].Value.Trim()
        report = 'FBSir-business/target/surefire-reports/' + [IO.Path]::GetFileName($ReportPath)
        reportLastWriteTimeUtc = $reportFile.LastWriteTimeUtc.ToString('o')
        reportSha256 = (Get-FileHash -LiteralPath $ReportPath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

function Read-RefreshSecurityTestEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$ReportPath,
        [Parameter(Mandatory = $true)][DateTimeOffset]$StartedAt
    )

    if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
        throw "Independent Board OAuth refresh-security Surefire report is missing: $ReportPath"
    }
    $reportFile = Get-Item -LiteralPath $ReportPath
    if ($reportFile.LastWriteTimeUtc -lt $StartedAt.UtcDateTime) {
        throw "Independent Board OAuth refresh-security Surefire report is stale: $ReportPath"
    }
    $reportText = Get-Content -LiteralPath $ReportPath -Raw -Encoding UTF8
    try {
        [xml]$report = $reportText
    }
    catch {
        throw "Independent Board OAuth refresh-security Surefire report is invalid XML: $($_.Exception.Message)"
    }
    $suite = $report.testsuite
    if ($null -eq $suite -or
        -not [string]::Equals([string]$suite.name, $refreshSecuritySuiteName, [StringComparison]::Ordinal)) {
        throw 'Independent Board OAuth refresh-security Surefire report has the wrong suite identity.'
    }
    foreach ($attributeName in @('tests', 'failures', 'errors', 'skipped')) {
        if ([string]$suite.$attributeName -notmatch '^\d+$') {
            throw "Independent Board OAuth refresh-security Surefire report has an invalid $attributeName count."
        }
    }
    $tests = [int]$suite.tests
    $failures = [int]$suite.failures
    $errors = [int]$suite.errors
    $skipped = [int]$suite.skipped
    $testCases = @($suite.testcase | Where-Object { $null -ne $_ })
    if ($tests -ne $expectedRefreshSecurityTests -or
        $testCases.Count -ne $expectedRefreshSecurityTests -or
        $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
        throw "Independent Board OAuth refresh-security MySQL IT must pass exactly $expectedRefreshSecurityTests/$expectedRefreshSecurityTests; found tests=$tests testcases=$($testCases.Count) failures=$failures errors=$errors skipped=$skipped."
    }

    return [pscustomobject]@{
        test = 'IndependentBoardOAuthRefreshSecurityServiceTest'
        expectedTests = $expectedRefreshSecurityTests
        tests = $tests
        passed = $tests - $failures - $errors - $skipped
        failures = $failures
        errors = $errors
        skipped = $skipped
        report = 'FBSir-business/target/surefire-reports/' + [IO.Path]::GetFileName($ReportPath)
        reportLastWriteTimeUtc = $reportFile.LastWriteTimeUtc.ToString('o')
        reportSha256 = (Get-FileHash -LiteralPath $ReportPath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

$mysqlBin = Resolve-MySqlBinDirectory -Requested $MySqlBinDirectory
$mysqld = Join-Path $mysqlBin 'mysqld.exe'
$mysql = Join-Path $mysqlBin 'mysql.exe'
$mysqlAdmin = Join-Path $mysqlBin 'mysqladmin.exe'
$mysqlConfigEditor = Join-Path $mysqlBin 'mysql_config_editor.exe'
$baseDirectory = Split-Path -Parent $mysqlBin

function Invoke-DisposableMySqlText {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$TargetDatabase = ''
    )

    if ($TargetDatabase -and ($TargetDatabase -notmatch '^[A-Za-z0-9_]{1,64}$')) {
        throw "Unsafe disposable database name: $TargetDatabase"
    }
    $arguments = "--protocol=tcp --host=127.0.0.1 --port=$Port --user=root --default-character-set=utf8mb4 --batch --raw --skip-column-names"
    if ($TargetDatabase) {
        $arguments += " --database=$TargetDatabase"
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
        throw 'Unable to start the disposable mysql client.'
    }
    try {
        $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($Sql)
        $process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
        $process.StandardInput.BaseStream.Flush()
        $process.StandardInput.Close()
        $stdout = $process.StandardOutput.ReadToEnd().Trim()
        $stderr = $process.StandardError.ReadToEnd().Trim()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "Disposable mysql exited with code $($process.ExitCode): $stderr"
        }
        return $stdout
    }
    finally {
        $process.Dispose()
    }
}

function Invoke-DisposableMySqlFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$TargetDatabase,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256
    )

    if ($TargetDatabase -notmatch '^[A-Za-z0-9_]{1,64}$') {
        throw "Unsafe disposable database name: $TargetDatabase"
    }
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $allowedSqlRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'sql')).TrimEnd('\') + '\'
    if (-not $fullPath.StartsWith($allowedSqlRoot, [StringComparison]::OrdinalIgnoreCase) -or
        -not [string]::Equals(
            [System.IO.Path]::GetFileName($fullPath),
            'update_20260721_independent_board_oauth_refresh_security.sql',
            [StringComparison]::Ordinal)) {
        throw "Refusing to execute a SQL file outside the exact refresh-security migration boundary: $fullPath"
    }
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "Required refresh-security migration is missing: $fullPath"
    }
    if ($ExpectedSha256 -notmatch '^[0-9a-f]{64}$') {
        throw "Invalid expected refresh-security migration SHA-256: '$ExpectedSha256'."
    }

    $sqlBytes = [System.IO.File]::ReadAllBytes($fullPath)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        $actualSha256 = -join ($algorithm.ComputeHash($sqlBytes) |
            ForEach-Object { $_.ToString('x2') })
    }
    finally {
        $algorithm.Dispose()
    }
    if (-not [string]::Equals($actualSha256, $ExpectedSha256, [StringComparison]::Ordinal)) {
        throw "Refresh-security migration bytes do not match the public_init_036 manifest: expected $ExpectedSha256, found $actualSha256."
    }

    $arguments = "--protocol=tcp --host=127.0.0.1 --port=$Port --user=root --default-character-set=utf8mb4 --batch --raw --skip-column-names --database=$TargetDatabase"
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
        throw 'Unable to start the disposable mysql client for public_init_036.'
    }
    try {
        $process.StandardInput.BaseStream.Write($sqlBytes, 0, $sqlBytes.Length)
        $process.StandardInput.BaseStream.Flush()
        $process.StandardInput.Close()
        $stdout = $process.StandardOutput.ReadToEnd().Trim()
        $stderr = $process.StandardError.ReadToEnd().Trim()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "Direct public_init_036 migration exited with code $($process.ExitCode): $stderr"
        }
        return $stdout
    }
    finally {
        $process.Dispose()
    }
}

function Get-OauthSuccessorState {
    param([Parameter(Mandatory = $true)][string]$TargetDatabase)

    Invoke-DisposableMySqlText -TargetDatabase $TargetDatabase -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_foundation_v1'
     AND description = 'Independent Board OAuth client, authorization, token family and immutable receipt tables'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_receipt_provenance_v1'
     AND description = 'APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_consent_intent_lineage_v1'
     AND description = 'APPLIED:Independent Board OAuth consent intent lineage'),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
     AND column_name = 'family_created_slot' AND column_type = 'varchar(128)'
     AND is_nullable = 'YES' AND character_set_name = 'ascii'
     AND collation_name = 'ascii_bin' AND extra = 'STORED GENERATED'),
  (SELECT COUNT(*) FROM (
       SELECT index_name, non_unique, index_type, MIN(is_visible) AS visibility,
              COUNT(*) AS key_part_count,
              GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                           ORDER BY seq_in_index SEPARATOR ',') AS column_signature
       FROM information_schema.statistics
       WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
         AND index_name = 'uk_oauth_receipt_family_created_slot'
       GROUP BY index_name, non_unique, index_type
   ) exact_provenance_index
   WHERE non_unique = 0 AND index_type = 'BTREE' AND visibility = 'YES'
     AND key_part_count = 1
     AND CAST(column_signature AS BINARY) = CAST('family_created_slot:A' AS BINARY)),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND column_name = 'consent_intent'
     AND column_type = 'varchar(32)' AND column_default IS NULL
     AND character_set_name = 'ascii' AND collation_name = 'ascii_bin'
     AND extra = '' AND COALESCE(generation_expression, '') = '' AND (
       (table_name = 'fbs_oauth_authorization_request' AND ordinal_position = 31 AND is_nullable = 'YES')
       OR (table_name = 'fbs_oauth_authorization_code' AND ordinal_position = 27 AND is_nullable = 'NO')
       OR (table_name = 'fbs_oauth_token_family' AND ordinal_position = 28 AND is_nullable = 'NO'))),
  (SELECT COUNT(*) FROM (
       SELECT table_name, index_name, non_unique, index_type,
              MIN(is_visible) AS visibility, COUNT(*) AS key_part_count,
              SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
              SUM(sub_part IS NOT NULL) AS partial_columns,
              GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                           ORDER BY seq_in_index SEPARATOR ',') AS column_signature
       FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND ((table_name = 'fbs_oauth_authorization_request' AND index_name = 'uk_oauth_request_id_consent')
           OR (table_name = 'fbs_oauth_authorization_code' AND index_name IN ('idx_oauth_code_request_consent','uk_oauth_code_id_consent'))
           OR (table_name = 'fbs_oauth_token_family' AND index_name = 'idx_oauth_family_code_consent'))
       GROUP BY table_name, index_name, non_unique, index_type
   ) named_indexes
   WHERE visibility = 'YES' AND index_type = 'BTREE'
     AND non_column_key_parts = 0 AND partial_columns = 0
     AND ((table_name = 'fbs_oauth_authorization_request' AND index_name = 'uk_oauth_request_id_consent'
           AND non_unique = 0 AND key_part_count = 2 AND CAST(column_signature AS BINARY) = CAST('id:A,consent_intent:A' AS BINARY))
       OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'idx_oauth_code_request_consent'
           AND non_unique = 1 AND key_part_count = 2 AND CAST(column_signature AS BINARY) = CAST('authorization_request_id:A,consent_intent:A' AS BINARY))
       OR (table_name = 'fbs_oauth_authorization_code' AND index_name = 'uk_oauth_code_id_consent'
           AND non_unique = 0 AND key_part_count = 2 AND CAST(column_signature AS BINARY) = CAST('id:A,consent_intent:A' AS BINARY))
       OR (table_name = 'fbs_oauth_token_family' AND index_name = 'idx_oauth_family_code_consent'
           AND non_unique = 1 AND key_part_count = 2 AND CAST(column_signature AS BINARY) = CAST('origin_authorization_code_id:A,consent_intent:A' AS BINARY)))),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE() AND unique_constraint_schema = DATABASE()
     AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT' AND match_option = 'NONE'
     AND ((table_name = 'fbs_oauth_authorization_code'
           AND constraint_name = 'fk_oauth_code_request_consent'
           AND unique_constraint_name = 'uk_oauth_request_id_consent'
           AND referenced_table_name = 'fbs_oauth_authorization_request')
       OR (table_name = 'fbs_oauth_token_family'
           AND constraint_name = 'fk_oauth_family_code_consent'
           AND unique_constraint_name = 'uk_oauth_code_id_consent'
           AND referenced_table_name = 'fbs_oauth_authorization_code'))),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK' AND enforced = 'YES'
     AND ((table_name = 'fbs_oauth_authorization_request' AND constraint_name = 'chk_oauth_request_consent_intent')
       OR (table_name = 'fbs_oauth_authorization_code' AND constraint_name = 'chk_oauth_code_consent_intent')
       OR (table_name = 'fbs_oauth_token_family' AND constraint_name = 'chk_oauth_family_consent_intent'))),
  (SELECT COUNT(*) FROM information_schema.triggers
   WHERE trigger_schema = DATABASE()
     AND trigger_name = 'trg_oauth_request_consent_intent_once'
     AND event_object_table = 'fbs_oauth_authorization_request'
     AND event_manipulation = 'UPDATE' AND action_timing = 'BEFORE'
     AND action_orientation = 'ROW' AND action_condition IS NULL),
  (SELECT COUNT(*) FROM information_schema.routines
   WHERE routine_schema = DATABASE() AND routine_type = 'PROCEDURE'
     AND routine_name IN (
       'u3w_assert_ib_oauth_consent_intent_20260721',
       'u3w_migrate_ib_oauth_consent_intent_20260721',
       'u3w_finalize_ib_oauth_consent_intent_20260721'))
);
"@
}

function Get-OauthRefreshSecurityState {
    param([Parameter(Mandatory = $true)][string]$TargetDatabase)

    Invoke-DisposableMySqlText -TargetDatabase $TargetDatabase -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_refresh_security_v1'
     AND description = 'APPLIED:Independent Board OAuth refresh security receipt v2'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_refresh_security_v1'),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
     AND column_name IN ('receipt_format_version','subject_generation',
       'result_generation','causation_receipt_id','before_state_digest',
       'after_state_digest','subject_token_type','security_event_slot')),
  (SELECT COUNT(DISTINCT CONCAT(table_name, ':', index_name))
   FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND ((table_name = 'fbs_oauth_token'
           AND index_name = 'idx_oauth_token_family_lock_order')
       OR (table_name = 'fbs_oauth_receipt'
           AND index_name = 'idx_oauth_receipt_family_client_lock_order')
       OR (table_name = 'fbs_connector_binding_receipt'
           AND index_name = 'idx_connector_binding_receipt_lock_order'))),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name = 'fbs_oauth_receipt'
     AND constraint_name IN ('fk_oauth_receipt_refresh_subject',
       'fk_oauth_receipt_causation_scope')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name = 'fbs_oauth_receipt' AND constraint_type = 'CHECK'
     AND enforced = 'YES'
     AND constraint_name IN ('chk_oauth_receipt_format_version',
       'chk_oauth_receipt_v2_shape')),
  (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
     AND index_name = 'uk_oauth_receipt_security_event_slot'
     AND non_unique = 0),
  (SELECT COUNT(*) FROM information_schema.routines
   WHERE routine_schema = DATABASE() AND routine_type = 'PROCEDURE'
     AND routine_name IN ('u3w_assert_ib_oauth_refresh_security_20260721',
       'u3w_migrate_ib_oauth_refresh_security_20260721')),
  (SELECT COUNT(*) FROM information_schema.triggers
   WHERE trigger_schema = DATABASE()
     AND trigger_name IN ('independent_board_refresh_it_delay_rotation',
       'independent_board_refresh_it_fail_receipt'))
);
"@
}

if ($Port -eq 0) {
    $Port = Get-LoopbackEphemeralPort
}
if ($Port -eq 3306) {
    throw 'Port 3306 is forbidden for the destructive integration test.'
}
if (Test-LoopbackPortListening -TargetPort $Port) {
    throw "Refusing to use loopback port $Port because it is already listening."
}

$runId = 'run-{0}-{1}-{2}' -f (Get-Date -Format 'yyyyMMddTHHmmss'), $PID,
    ([Guid]::NewGuid().ToString('N').Substring(0, 8))
$runDirectory = Join-Path $workRoot $runId
$dataDirectory = Join-Path $runDirectory 'data'
$errorLog = Join-Path $runDirectory 'mysql-error.log'
$pidFile = Join-Path $runDirectory 'mysql.pid'
$temporaryLoginFile = Join-Path $runDirectory 'mysql-login.cnf'
$surefireReport = Join-Path $repoRoot 'FBSir-business\target\surefire-reports\TEST-com.wx.fbsir.business.board.integration.IndependentBoardMysqlTransactionIT.xml'
$refreshSecuritySurefireReport = Join-Path $repoRoot 'FBSir-business\target\surefire-reports\TEST-com.wx.fbsir.business.board.oauth.service.IndependentBoardOAuthRefreshSecurityServiceTest.xml'
$artifactPathMap = [ordered]@{
    oauthFoundationMigration = 'sql\update_20260721_independent_board_oauth_foundation.sql'
    oauthProvenanceMigration = 'sql\update_20260721_independent_board_oauth_receipt_provenance.sql'
    oauthConsentIntentMigration = 'sql\update_20260721_independent_board_oauth_consent_intent_lineage.sql'
    oauthRefreshSecurityMigration = 'sql\update_20260721_independent_board_oauth_refresh_security.sql'
    manifest = 'sql\init-manifest.json'
    initializer = 'scripts\init-database.ps1'
    canonicalVerifier = 'scripts\verify-independent-board-live-database.ps1'
    manifestVerifier = 'scripts\verify-database-manifest.ps1'
    directIntegrationTest = 'FBSir-business\src\test\java\com\wx\fbsir\business\board\integration\IndependentBoardMysqlTransactionIT.java'
    tokenExchangeMysqlTestConfiguration = 'FBSir-business\src\test\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthTokenExchangeMysqlTestConfiguration.java'
    refreshSecurityMysqlTestConfiguration = 'FBSir-business\src\test\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthRefreshMysqlTestConfiguration.java'
    refreshSecurityIntegrationTest = 'FBSir-business\src\test\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthRefreshSecurityServiceTest.java'
    firstProtectedMysqlTestConfiguration = 'FBSir-business\src\test\java\com\wx\fbsir\business\board\service\IndependentBoardOAuthFirstProtectedRequestMysqlTestConfiguration.java'
    independentBoardMapper = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\mapper\IndependentBoardMapper.java'
    independentBoardMapperXml = 'FBSir-business\src\main\resources\mapper\board\IndependentBoardMapper.xml'
    independentBoardOAuthMapper = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\mapper\IndependentBoardOAuthMapper.java'
    independentBoardOAuthMapperXml = 'FBSir-business\src\main\resources\mapper\board\IndependentBoardOAuthMapper.xml'
    portalReadService = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\IndependentBoardPortalReadService.java'
    portalReadMapper = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\mapper\IndependentBoardPortalReadMapper.java'
    portalReadMapperXml = 'FBSir-business\src\main\resources\mapper\board\IndependentBoardPortalReadMapper.xml'
    connectorBindingService = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\service\IndependentBoardConnectorBindingService.java'
    tokenExchangeAuthorityPort = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\service\BoardOAuthTokenExchangeAuthorityPort.java'
    tokenExchangeService = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthTokenExchangeService.java'
    tokenExchangeTransactionRunner = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthTokenExchangeTransactionRunner.java'
    tokenExchangeFacade = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthTokenExchangeFacade.java'
    refreshReceiptFactory = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\BoardOAuthRefreshReceiptFactory.java'
    refreshStateDigest = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\BoardOAuthRefreshStateDigest.java'
    refreshAuthorityPort = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\service\BoardOAuthRefreshAuthorityPort.java'
    refreshService = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthRefreshService.java'
    refreshTransactionRunner = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthRefreshTransactionRunner.java'
    refreshFacade = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthRefreshFacade.java'
    refreshAuthorityLease = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\service\BoardOAuthRefreshAuthorityLease.java'
    firstProtectedTransactionRunner = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\service\IndependentBoardOAuthFirstProtectedRequestTransactionRunner.java'
    firstProtectedFacade = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\service\IndependentBoardOAuthFirstProtectedRequestFacade.java'
}
$expectedArtifactSha256 = [ordered]@{
    oauthConsentIntentMigration = '55dc772d54a4a457f00711a45266b2d0042522d63ed0d5a8e408d35a8ecaf560'
}
$artifactEvidence = [ordered]@{}
foreach ($artifactName in $artifactPathMap.Keys) {
    $relativePath = $artifactPathMap[$artifactName]
    $artifactEvidence[$artifactName] = Get-FileEvidence `
        -Path (Join-Path $repoRoot $relativePath) -RelativePath $relativePath
    if ($expectedArtifactSha256.Contains($artifactName) -and
        -not [string]::Equals(
            $artifactEvidence[$artifactName].sha256,
            $expectedArtifactSha256[$artifactName],
            [StringComparison]::Ordinal)) {
        throw "Frozen artifact SHA-256 drifted for $relativePath."
    }
}
$manifestContent = Get-Content -LiteralPath (Join-Path $repoRoot $artifactPathMap.manifest) `
    -Raw -Encoding UTF8
try {
    $manifest = $manifestContent | ConvertFrom-Json
}
catch {
    throw "Public database manifest is invalid JSON: $($_.Exception.Message)"
}
$refreshSecurityManifestEntries = @($manifest.steps | Where-Object {
    [string]$_.version -eq 'public_init_036'
})
if ($refreshSecurityManifestEntries.Count -ne 1) {
    throw "Public database manifest must contain exactly one public_init_036 entry; found $($refreshSecurityManifestEntries.Count)."
}
$refreshSecurityManifestEntry = $refreshSecurityManifestEntries[0]
if (-not [string]::Equals(
        [string]$refreshSecurityManifestEntry.file,
        'update_20260721_independent_board_oauth_refresh_security.sql',
        [StringComparison]::Ordinal) -or
    [string]$refreshSecurityManifestEntry.sha256 -notmatch '^[0-9a-f]{64}$' -or
    -not [string]::Equals(
        [string]$refreshSecurityManifestEntry.sha256,
        [string]$artifactEvidence.oauthRefreshSecurityMigration.sha256,
        [StringComparison]::Ordinal)) {
    throw 'public_init_036 manifest file or SHA-256 does not match the captured refresh-security migration bytes.'
}
$refreshSecurityMigrationSha256 = [string]$refreshSecurityManifestEntry.sha256
$managedEnvironmentNames = @(
    'INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP',
    'INDEPENDENT_BOARD_MYSQL_IT_URL',
    'INDEPENDENT_BOARD_MYSQL_IT_USERNAME',
    'INDEPENDENT_BOARD_MYSQL_IT_PASSWORD',
    'MYSQL_TEST_LOGIN_FILE'
)
$environmentSnapshot = Get-ProcessEnvironmentSnapshot -Names $managedEnvironmentNames
$serverProcess = $null
$ready = $false
$testPassed = $false
$runtimeProfile = $null
$directEvidence = $null
$refreshSecurityEvidence = $null
$canonicalEvidence = $null
$serverProcessStopped = $false
$workDirectoryCleaned = $false
$portClosed = $false
$environmentRestored = $false

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
    # On Windows mysqld may hand off from the launcher process. The server PID
    # file plus the exact run datadir and port is the authoritative identity.
    $readyServerPid = Get-VerifiedMySqlProcessId -ExpectedPidFile $pidFile `
        -ExpectedDataDirectory $dataDirectory -ExpectedPort $Port
    if ($null -eq $readyServerPid) {
        throw 'The ready MySQL listener has no PID-file process bound to this run directory and port.'
    }

    $runtimeResponse = Invoke-DisposableMySqlText -Sql `
        "SELECT CONCAT_WS('|', VERSION(), @@version_comment, @@default_storage_engine, @@transaction_isolation);"
    $runtimeParts = @($runtimeResponse.Split('|'))
    if ($runtimeParts.Count -ne 4 -or
        $runtimeParts[0] -notin @('8.0.30', '8.4.8') -or
        -not [string]::Equals($runtimeParts[1], 'MySQL Community Server - GPL', [StringComparison]::Ordinal) -or
        -not [string]::Equals($runtimeParts[2], 'InnoDB', [StringComparison]::OrdinalIgnoreCase) -or
        -not [string]::Equals($runtimeParts[3], 'REPEATABLE-READ', [StringComparison]::Ordinal)) {
        throw "Disposable MySQL runtime is outside the exact dual-build contract: '$runtimeResponse'."
    }
    $runtimeProfile = [pscustomobject]@{
        version = $runtimeParts[0]
        versionComment = $runtimeParts[1]
        defaultStorageEngine = $runtimeParts[2]
        transactionIsolation = $runtimeParts[3]
        mysqldSha256 = (Get-FileHash -LiteralPath $mysqld -Algorithm SHA256).Hash.ToLowerInvariant()
        mysqlSha256 = (Get-FileHash -LiteralPath $mysql -Algorithm SHA256).Hash.ToLowerInvariant()
    }

    Invoke-DisposableMySqlText -Sql `
        "CREATE DATABASE ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" | Out-Null

    [Environment]::SetEnvironmentVariable('INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP', 'true', 'Process')
    [Environment]::SetEnvironmentVariable(
        'INDEPENDENT_BOARD_MYSQL_IT_URL',
        "jdbc:mysql://127.0.0.1:$Port/$database" +
            '?useAffectedRows=false&connectionTimeZone=Asia%2FShanghai&useSSL=false&allowPublicKeyRetrieval=true',
        'Process')
    [Environment]::SetEnvironmentVariable('INDEPENDENT_BOARD_MYSQL_IT_USERNAME', 'root', 'Process')
    [Environment]::SetEnvironmentVariable('INDEPENDENT_BOARD_MYSQL_IT_PASSWORD', '', 'Process')

    Write-Host "Running Spring/MyBatis Independent Board MySQL IT on loopback port $Port"
    $mavenStartedAt = [DateTimeOffset]::UtcNow
    & mvn -pl FBSir-business -am '-Dtest=IndependentBoardMysqlTransactionIT' `
        '-Dsurefire.failIfNoSpecifiedTests=false' test
    if ($LASTEXITCODE -ne 0) {
        throw "Independent Board MySQL transaction IT failed with Maven exit code $LASTEXITCODE"
    }

    $directEvidence = Read-DirectTestEvidence -ReportPath $surefireReport -StartedAt $mavenStartedAt
    $directStateResponse = Get-OauthSuccessorState -TargetDatabase $database
    if (-not [string]::Equals(
            $directStateResponse, '1|1|1|1|1|3|4|2|3|1|0', [StringComparison]::Ordinal)) {
        throw "Independent Board MySQL direct IT did not leave the exact 033 -> 034 -> 035 state: '$directStateResponse'."
    }
    $directEvidence | Add-Member -NotePropertyName oauthFoundationReceipt -NotePropertyValue 1
    $directEvidence | Add-Member -NotePropertyName oauthProvenanceReceipt -NotePropertyValue 1
    $directEvidence | Add-Member -NotePropertyName oauthConsentIntentReceipt -NotePropertyValue 1
    $directEvidence | Add-Member -NotePropertyName familyCreatedSlot -NotePropertyValue 1
    $directEvidence | Add-Member -NotePropertyName familyCreatedUniqueIndex -NotePropertyValue 1
    $directEvidence | Add-Member -NotePropertyName consentIntentColumns -NotePropertyValue 3
    $directEvidence | Add-Member -NotePropertyName consentIntentIndexes -NotePropertyValue 4
    $directEvidence | Add-Member -NotePropertyName consentIntentForeignKeys -NotePropertyValue 2
    $directEvidence | Add-Member -NotePropertyName consentIntentChecks -NotePropertyValue 3
    $directEvidence | Add-Member -NotePropertyName consentIntentTrigger -NotePropertyValue 1
    $directEvidence | Add-Member -NotePropertyName consentIntentHelperProcedures -NotePropertyValue 0

    Write-Host 'Applying manifest-bound public_init_036 bytes directly to the post-035 disposable database'
    $refreshSecurityMigrationOutput = Invoke-DisposableMySqlFile `
        -Path (Join-Path $repoRoot $artifactPathMap.oauthRefreshSecurityMigration) `
        -TargetDatabase $database -ExpectedSha256 $refreshSecurityMigrationSha256
    $refreshSecurityStateBeforeTest = Get-OauthRefreshSecurityState -TargetDatabase $database
    if (-not [string]::Equals(
            $refreshSecurityStateBeforeTest, '1|1|8|3|2|2|1|0|0',
            [StringComparison]::Ordinal)) {
        throw "Direct public_init_036 current-read failed before refresh-security tests: '$refreshSecurityStateBeforeTest'."
    }

    Write-Host "Running three OAuth refresh-security MySQL scenarios on the same loopback database and four-variable test contract"
    $refreshSecurityMavenStartedAt = [DateTimeOffset]::UtcNow
    & mvn -pl FBSir-business -am `
        '-Dtest=IndependentBoardOAuthRefreshSecurityServiceTest' `
        '-Dsurefire.failIfNoSpecifiedTests=false' test
    if ($LASTEXITCODE -ne 0) {
        throw "Independent Board OAuth refresh-security MySQL IT failed with Maven exit code $LASTEXITCODE"
    }
    $refreshSecurityEvidence = Read-RefreshSecurityTestEvidence `
        -ReportPath $refreshSecuritySurefireReport -StartedAt $refreshSecurityMavenStartedAt
    $refreshSecurityStateAfterTest = Get-OauthRefreshSecurityState -TargetDatabase $database
    if (-not [string]::Equals(
            $refreshSecurityStateAfterTest, $refreshSecurityStateBeforeTest,
            [StringComparison]::Ordinal)) {
        throw "OAuth refresh-security MySQL tests changed the exact public_init_036 schema/helper state: before='$refreshSecurityStateBeforeTest' after='$refreshSecurityStateAfterTest'."
    }
    $refreshSecurityEvidence | Add-Member -NotePropertyName publicInit036BytesAppliedBeforeTest -NotePropertyValue $true
    $refreshSecurityEvidence | Add-Member -NotePropertyName migrationSha256 -NotePropertyValue $refreshSecurityMigrationSha256
    $refreshSecurityEvidence | Add-Member -NotePropertyName migrationOutputSha256 `
        -NotePropertyValue (Get-StringSha256 -Value ([string]$refreshSecurityMigrationOutput))
    $refreshSecurityEvidence | Add-Member -NotePropertyName oauthRefreshSecurityReceipt -NotePropertyValue 1
    $refreshSecurityEvidence | Add-Member -NotePropertyName receiptV2Columns -NotePropertyValue 8
    $refreshSecurityEvidence | Add-Member -NotePropertyName lockOrderIndexes -NotePropertyValue 3
    $refreshSecurityEvidence | Add-Member -NotePropertyName refreshForeignKeys -NotePropertyValue 2
    $refreshSecurityEvidence | Add-Member -NotePropertyName refreshChecks -NotePropertyValue 2
    $refreshSecurityEvidence | Add-Member -NotePropertyName securityEventUniqueIndex -NotePropertyValue 1
    $refreshSecurityEvidence | Add-Member -NotePropertyName helperProcedures -NotePropertyValue 0
    $refreshSecurityEvidence | Add-Member -NotePropertyName testTriggers -NotePropertyValue 0

    if (-not $DirectOnly) {
    [Environment]::SetEnvironmentVariable('MYSQL_TEST_LOGIN_FILE', $temporaryLoginFile, 'Process')
    $priorErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $loginEditorOutput = & $mysqlConfigEditor 'set' "--login-path=$canonicalLoginPath" `
        '--host=127.0.0.1' "--port=$Port" '--user=root' 2>&1
    $loginEditorExitCode = $LASTEXITCODE
    $ErrorActionPreference = $priorErrorActionPreference
    if ($loginEditorExitCode -ne 0 -or
        -not (Test-Path -LiteralPath $temporaryLoginFile -PathType Leaf)) {
        throw "Could not create the isolated MySQL login file; exit=$loginEditorExitCode output=$($loginEditorOutput -join ' ')"
    }

    Write-Host "Running canonical public_init first apply, completed rerun and CurrentReadOnly on $canonicalDatabase"
    $shell = (Get-Process -Id $PID).Path
    $priorErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $canonicalOutput = & $shell -NoProfile -ExecutionPolicy Bypass -File $canonicalVerifier `
            -Database $canonicalDatabase -LoginPath $canonicalLoginPath -MySqlExe $mysql -Json 2>&1
        $canonicalExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $priorErrorActionPreference
    }
    $canonicalText = (($canonicalOutput | ForEach-Object { $_.ToString() }) -join "`n").Trim()
    if ($canonicalExitCode -ne 0) {
        $connectorCheckDiagnostics = ''
        try {
            $connectorCheckDiagnostics = Invoke-DisposableMySqlText `
                -TargetDatabase $canonicalDatabase -Sql @"
SELECT CONCAT_WS(CHAR(9),
  tc.table_name,
  tc.constraint_name,
  HEX(cc.check_clause),
  cc.check_clause,
  REPLACE(REPLACE(LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
      cc.check_clause, CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''), CHAR(92), '')),
      '_utf8mb4', ''), '_utf8mb3', ''),
  HEX(REPLACE(REPLACE(LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
      cc.check_clause, CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''), CHAR(92), '')),
      '_utf8mb4', ''), '_utf8mb3', '')),
  same_name.same_name_count)
FROM information_schema.table_constraints tc
INNER JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
INNER JOIN (
  SELECT constraint_schema, constraint_name, COUNT(*) AS same_name_count
  FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
  GROUP BY constraint_schema, constraint_name
) same_name
  ON same_name.constraint_schema = tc.constraint_schema
 AND same_name.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.constraint_type = 'CHECK'
  AND tc.table_name IN (
      'fbs_connector_binding',
      'fbs_connector_binding_scope',
      'fbs_connector_binding_receipt')
ORDER BY tc.table_name, tc.constraint_name;
"@
        }
        catch {
            $connectorCheckDiagnostics = "diagnostic query failed: $($_.Exception.Message)"
        }
        throw "Canonical Independent Board live database gate failed with exit code $canonicalExitCode.`n$canonicalText`nConnector CHECK diagnostics (table, constraint, HEX(raw), raw, current normalized, HEX(normalized), same-name count):`n$connectorCheckDiagnostics"
    }
    try {
        $canonicalResult = $canonicalText | ConvertFrom-Json
    }
    catch {
        throw "Canonical Independent Board live database gate did not return valid JSON: $canonicalText"
    }
    $expectedPhaseNames = @('first_apply', 'completed_rerun', 'readonly_current_read')
    $canonicalPhases = @($canonicalResult.phases)
    if (-not $canonicalResult.ok -or $canonicalPhases.Count -ne $expectedPhaseNames.Count) {
        throw 'Canonical Independent Board live database gate did not return three passing phases.'
    }
    for ($index = 0; $index -lt $expectedPhaseNames.Count; $index++) {
        $phase = $canonicalPhases[$index]
        if ([string]$phase.name -ne $expectedPhaseNames[$index] -or
            -not [bool]$phase.ok -or [int]$phase.exitCode -ne 0) {
            throw "Canonical Independent Board phase drifted at position $($index + 1)."
        }
    }
    if (-not ([string]$canonicalPhases[0].output).Contains('APPLY public_init_036:') -or
        -not ([string]$canonicalPhases[1].output).Contains('SKIP public_init_036 (already applied)') -or
        -not ([string]$canonicalPhases[2].output).Contains(
            'PASS Independent Board OAuth refresh-security exact S3 current-read') -or
        -not ([string]$canonicalPhases[2].output).Contains(
            'PASS public database manifest exact current-read (36 APPLIED receipts with exact descriptions).')) {
        throw 'Canonical Independent Board phases did not prove public_init_036 first apply, completed rerun and exact 36-step read-only current-read.'
    }

    $canonicalReceiptState = Invoke-DisposableMySqlText -TargetDatabase $canonicalDatabase -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = 'public_init_033'
     AND description = 'APPLIED:Independent Board OAuth authorization foundation'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = 'public_init_034'
     AND description = 'APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED receipt provenance'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = 'public_init_035'
     AND description = 'APPLIED:Independent Board OAuth consent-intent lineage'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = 'public_init_036'
     AND description = 'APPLIED:Independent Board OAuth refresh security receipt v2'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_refresh_security_v1'
     AND description = 'APPLIED:Independent Board OAuth refresh security receipt v2'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version REGEXP '^public_init_[0-9]{3}$'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version REGEXP '^public_init_[0-9]{3}$' AND description LIKE 'APPLIED:%')
);
"@
    if (-not [string]::Equals($canonicalReceiptState, '1|1|1|1|1|36|36', [StringComparison]::Ordinal)) {
        throw "Canonical Independent Board public_init receipt state drifted: '$canonicalReceiptState'."
    }
    $canonicalSuccessorState = Get-OauthSuccessorState -TargetDatabase $canonicalDatabase
    if (-not [string]::Equals(
            $canonicalSuccessorState, '1|1|1|1|1|3|4|2|3|1|0', [StringComparison]::Ordinal)) {
        throw "Canonical Independent Board 033 -> 034 -> 035 successor state drifted: '$canonicalSuccessorState'."
    }
    $canonicalRefreshSecurityState = Get-OauthRefreshSecurityState -TargetDatabase $canonicalDatabase
    if (-not [string]::Equals(
            $canonicalRefreshSecurityState, '1|1|8|3|2|2|1|0|0',
            [StringComparison]::Ordinal)) {
        throw "Canonical Independent Board public_init_036 successor state drifted: '$canonicalRefreshSecurityState'."
    }
    $canonicalEvidence = [pscustomobject]@{
        database = $canonicalDatabase
        preexistingTableCount = [int]$canonicalResult.preexistingTableCount
        phases = @($canonicalPhases | ForEach-Object {
            [pscustomobject]@{
                name = [string]$_.name
                ok = [bool]$_.ok
                exitCode = [int]$_.exitCode
                startedAt = [string]$_.startedAt
                finishedAt = [string]$_.finishedAt
                outputSha256 = Get-StringSha256 -Value ([string]$_.output)
            }
        })
        publicManifestReceipts = 36
        publicAppliedReceipts = 36
        oauthFoundationReceipt = 1
        oauthProvenanceReceipt = 1
        oauthConsentIntentReceipt = 1
        oauthRefreshSecurityReceipt = 1
        familyCreatedSlot = 1
        familyCreatedUniqueIndex = 1
        consentIntentColumns = 3
        consentIntentIndexes = 4
        consentIntentForeignKeys = 2
        consentIntentChecks = 3
        consentIntentTrigger = 1
        consentIntentHelperProcedures = 0
        receiptV2Columns = 8
        refreshLockOrderIndexes = 3
        refreshForeignKeys = 2
        refreshChecks = 2
        securityEventUniqueIndex = 1
        refreshHelperProcedures = 0
        refreshTestTriggers = 0
    }
    }
    else {
        Write-Host 'DirectOnly requested; canonical initializer phase remains intentionally unexecuted.'
    }

    foreach ($artifactName in $artifactPathMap.Keys) {
        $relativePath = $artifactPathMap[$artifactName]
        $after = Get-FileEvidence -Path (Join-Path $repoRoot $relativePath) -RelativePath $relativePath
        if (-not [string]::Equals(
                $after.sha256, $artifactEvidence[$artifactName].sha256, [StringComparison]::Ordinal)) {
            throw "Evidence file changed during the MySQL run: $relativePath"
        }
    }
    $testPassed = $true
}
finally {
    try {
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
    }
    finally {
        Restore-ProcessEnvironment -Snapshot $environmentSnapshot
        $environmentRestored = Test-ProcessEnvironmentMatchesSnapshot -Snapshot $environmentSnapshot
        if ($null -eq $serverProcess) {
            $serverProcessStopped = $true
        }
        else {
            $serverProcessStopped = $null -eq (
                Get-Process -Id $serverProcess.Id -ErrorAction SilentlyContinue)
        }
        $workDirectoryCleaned = -not (Test-Path -LiteralPath $runDirectory)
        $portClosed = -not (Test-LoopbackPortListening -TargetPort $Port)
    }
}

if ($testPassed) {
    if (-not $serverProcessStopped -or -not $workDirectoryCleaned -or
        -not $portClosed -or -not $environmentRestored) {
        throw "Disposable MySQL cleanup did not close every boundary: processStopped=$serverProcessStopped workDirectoryCleaned=$workDirectoryCleaned portClosed=$portClosed environmentRestored=$environmentRestored"
    }
    $summary = [ordered]@{
        schemaVersion = 3
        test = 'IndependentBoardMysqlTransactionIT'
        result = 'PASS'
        mode = if ($DirectOnly) { 'direct_only' } else { 'direct_and_canonical' }
        host = '127.0.0.1'
        port = $Port
        database = $database
        canonicalDatabase = if ($DirectOnly) { $null } else { $canonicalDatabase }
        runtime = $runtimeProfile
        connector = [ordered]@{
            version = $directEvidence.connectorVersion
            identity = $directEvidence.connectorIdentity
        }
        artifacts = $artifactEvidence
        directIntegration = $directEvidence
        refreshSecurityIntegration = $refreshSecurityEvidence
        canonicalInitializer = $canonicalEvidence
        destructiveTestConsent = $true
        productionConnectionUsed = $false
        cleanup = [ordered]@{
            serverProcessStopped = $serverProcessStopped
            portClosed = $portClosed
            workDirectoryCleaned = $workDirectoryCleaned
            temporaryLoginFileCleaned = -not (Test-Path -LiteralPath $temporaryLoginFile)
            processEnvironmentRestored = $environmentRestored
        }
    }
    $summaryJson = $summary | ConvertTo-Json -Depth 8
    Write-AtomicSummaryEvidence -EvidenceRoot $diagnosticsRoot `
        -RuntimeVersion ([string]$runtimeProfile.version) -SummaryJson $summaryJson `
        -CompletedAt ([DateTimeOffset]::Now)
    Write-Output $summaryJson
}
