[CmdletBinding()]
param(
    [switch]$AllowDestructiveTest,
    [string[]]$MySqlBinDirectories = @(),
    [ValidateSet('8.0.30', '8.4.8')]
    [string[]]$Versions = @('8.0.30', '8.4.8'),
    [switch]$EmitMetadataSnapshot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AllowDestructiveTest) {
    throw 'Explicit -AllowDestructiveTest consent is required.'
}

$repoRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$workRoot = [System.IO.Path]::GetFullPath(
    (Join-Path $repoRoot 'work\independent-board-plan-policy-mysql-it'))
$controlPlanePath = Join-Path $repoRoot 'sql\update_20260720_independent_board_control_plane.sql'
$policyPath = Join-Path $repoRoot 'sql\update_20260722_independent_board_plan_policy.sql'
$initializerPath = Join-Path $repoRoot 'scripts\init-database.ps1'
$manifestPath = Join-Path $repoRoot 'sql\init-manifest.json'
$runnerPath = [System.IO.Path]::GetFullPath($MyInvocation.MyCommand.Path)
$concurrencyTestPath = Join-Path $repoRoot (
    'FBSir-business\src\test\java\com\wx\fbsir\business\board\plan\integration\' +
    'IndependentBoardPlanPolicyMysqlConcurrencyIT.java')
$supportedVersions = @('8.0.30', '8.4.8')
$requiredVersions = @($supportedVersions | Where-Object { $_ -in $Versions })
$requestedVersionSet = @($Versions | Sort-Object -Unique)
if ($requestedVersionSet.Count -ne $Versions.Count -or
    $requiredVersions.Count -ne $requestedVersionSet.Count) {
    throw 'Versions must contain unique supported MySQL versions.'
}
$expectedConcurrencyTests = 6
$expectedConcurrencyIterations = 20
$concurrencySuiteName = 'com.wx.fbsir.business.board.plan.integration.IndependentBoardPlanPolicyMysqlConcurrencyIT'
$concurrencyReport = Join-Path $repoRoot (
    'FBSir-business\target\surefire-reports\TEST-' + $concurrencySuiteName + '.xml')

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

function Resolve-MavenExecutable {
    $candidates = [System.Collections.Generic.List[string]]::new()
    $pathCommand = Get-Command 'mvn.cmd' -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $pathCommand) {
        $candidates.Add($pathCommand.Source)
    }
    foreach ($environmentName in @('MAVEN_HOME', 'M2_HOME')) {
        $root = [Environment]::GetEnvironmentVariable($environmentName, 'Process')
        if (-not [string]::IsNullOrWhiteSpace($root)) {
            $candidates.Add((Join-Path $root 'bin\mvn.cmd'))
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        $candidates.Add((Join-Path $env:USERPROFILE (
            '.cache\u3w-java-toolchain\apache-maven-3.9.16\bin\mvn.cmd')))
        $candidates.Add((Join-Path $env:USERPROFILE (
            '.codex\cache\toolchains\apache-maven-3.9.16\bin\mvn.cmd')))
    }
    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and
            (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return [System.IO.Path]::GetFullPath($candidate)
        }
    }
    throw 'Maven 3.9.16 mvn.cmd is required on PATH, MAVEN_HOME, M2_HOME, or the bundled U3W toolchain path.'
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
        $versionText = (& $mysqld '--no-defaults' '--version' 2>&1 | Out-String).Trim()
        $match = [regex]::Match($versionText, 'Ver\s+(?<version>[0-9]+\.[0-9]+\.[0-9]+)')
        if ($match.Success) {
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
    }
    $missing = @($requiredVersions | Where-Object { -not $byVersion.ContainsKey($_) })
    if ($missing.Count -ne 0) {
        throw "The exact dual MySQL matrix is required; missing: $($missing -join ', ')."
    }
    return @($requiredVersions | ForEach-Object { $byVersion[$_] })
}

function Assert-SafeRunDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)

    $full = [System.IO.Path]::GetFullPath($Path)
    $root = $workRoot.TrimEnd('\')
    if (-not $full.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing cleanup outside the dedicated plan-policy IT root: $full"
    }
    if ((Split-Path -Leaf $full) -notmatch
        '^run-mysql-(8-0-30|8-4-8)-[0-9]{8}T[0-9]{6}-[0-9]+-[0-9a-f]{8}$') {
        throw "Refusing cleanup of an unexpected plan-policy IT path: $full"
    }
}

function Invoke-MySqlText {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Database,
        [Parameter(Mandatory = $true)][string]$Sql,
        [switch]$AllowFailure
    )

    $arguments = @(
        '--no-defaults', '--protocol=TCP', '-h127.0.0.1', "-P$Port", '-uroot',
        '--default-character-set=utf8mb4', '--batch', '--skip-column-names'
    )
    if (-not [string]::IsNullOrWhiteSpace($Database)) {
        $arguments += $Database
    }
    $arguments += @('--execute', $Sql)
    $priorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = (& $Profile.mysql @arguments 2>&1 | Out-String).Trim()
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $priorPreference
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "mysql command failed with exit code ${exitCode}: $output"
    }
    return [pscustomobject]@{ exitCode = $exitCode; output = $output }
}

function Invoke-MySqlFile {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Database,
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$AllowFailure
    )

    $sourcePath = [System.IO.Path]::GetFullPath($Path).Replace('\', '/')
    return Invoke-MySqlText -Profile $Profile -Port $Port -Database $Database `
        -Sql "source $sourcePath" -AllowFailure:$AllowFailure
}

function Assert-ExactOutput {
    param(
        [Parameter(Mandatory = $true)][string]$Actual,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Stage
    )
    if (-not [string]::Equals($Actual, $Expected, [StringComparison]::Ordinal)) {
        throw "$Stage drifted: expected '$Expected', found '$Actual'."
    }
}

function Invoke-PlanPolicyConcurrencyIt {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Version,
        [Parameter(Mandatory = $true)][string]$RunRoot
    )

    $startedAt = [DateTimeOffset]::UtcNow
    $standardOutput = Join-Path $RunRoot 'plan-policy-concurrency-maven.stdout.log'
    $standardError = Join-Path $RunRoot 'plan-policy-concurrency-maven.stderr.log'
    $arguments = @(
        '-pl', 'FBSir-business',
        "-Dtest=IndependentBoardPlanPolicyMysqlConcurrencyIT",
        '-Dsurefire.failIfNoSpecifiedTests=false',
        "-Dindependent.board.plan.policy.mysql.it.url=jdbc:mysql://127.0.0.1:${Port}/w3h_policy_concurrency?useSSL=false",
        '-Dindependent.board.plan.policy.mysql.it.username=root',
        '-Dindependent.board.plan.policy.mysql.it.password=',
        "-Dindependent.board.plan.policy.mysql.it.version=$Version",
        '-Dindependent.board.plan.policy.mysql.it.allowDrop=true',
        'surefire:test'
    )
    $maven = Start-Process -FilePath $mavenExecutable -ArgumentList $arguments `
        -WorkingDirectory $repoRoot -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $standardOutput -RedirectStandardError $standardError
    if ($maven.ExitCode -ne 0) {
        $stdout = if (Test-Path -LiteralPath $standardOutput) {
            Get-Content -LiteralPath $standardOutput -Raw -Encoding UTF8
        } else { '' }
        $stderr = if (Test-Path -LiteralPath $standardError) {
            Get-Content -LiteralPath $standardError -Raw -Encoding UTF8
        } else { '' }
        throw "Plan-policy concurrency Maven IT failed for MySQL ${Version}:`n$stdout`n$stderr"
    }
    if (-not (Test-Path -LiteralPath $concurrencyReport -PathType Leaf)) {
        throw "Plan-policy concurrency Surefire report is missing: $concurrencyReport"
    }
    $reportFile = Get-Item -LiteralPath $concurrencyReport
    if ($reportFile.LastWriteTimeUtc -lt $startedAt.UtcDateTime) {
        throw "Plan-policy concurrency Surefire report is stale: $concurrencyReport"
    }
    [xml]$report = Get-Content -LiteralPath $concurrencyReport -Raw -Encoding UTF8
    $suite = $report.testsuite
    $tests = [int]$suite.tests
    $failures = [int]$suite.failures
    $errors = [int]$suite.errors
    $skipped = [int]$suite.skipped
    if ($tests -ne $expectedConcurrencyTests -or
        $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
        throw "Plan-policy concurrency IT must pass exactly $expectedConcurrencyTests/$expectedConcurrencyTests; found tests=$tests failures=$failures errors=$errors skipped=$skipped."
    }
    $expectedCases = @(
        'sameActorSameKeySamePayloadReturnsSameReceipt',
        'sameActorSameKeyDifferentPayloadHasOneWinnerAndOneIdempotencyConflict',
        'differentKeysSameExpectedVersionHaveOneWinnerAndNoOrphan',
        'insertThenForcedCasFailurePhysicallyRollsBack',
        'freeAndVipConcurrentRevisionsDoNotDeadlockAndPreserveInvariant',
        'meetingReadsAreAllOldOrAllNewAndIgnoreImmutableReceiptLocks'
    )
    $actualCases = @($suite.testcase | ForEach-Object { [string]$_.name } | Sort-Object)
    if (($actualCases -join '|') -ne (($expectedCases | Sort-Object) -join '|')) {
        throw "Plan-policy concurrency IT cases drifted: $($actualCases -join ', ')."
    }
    $caseDurationsSeconds = [ordered]@{}
    foreach ($testCase in @($suite.testcase | Sort-Object name)) {
        $caseDurationsSeconds[[string]$testCase.name] = [double]$testCase.time
    }
    return [pscustomobject]@{
        springTransactionProxy = $true
        myBatisMapper = $true
        hikariConnectionPool = $true
        lockWaitTimeoutSeconds = 2
        iterationsPerCase = $expectedConcurrencyIterations
        tests = $tests
        failures = $failures
        errors = $errors
        skipped = $skipped
        durationSeconds = [double]$suite.time
        cases = $actualCases
        caseDurationsSeconds = $caseDurationsSeconds
        report = 'FBSir-business/target/surefire-reports/' +
            [System.IO.Path]::GetFileName($concurrencyReport)
    }
}

function Invoke-PlanPolicyConcurrencyTestCompile {
    $arguments = @(
        '-q', '-pl', 'FBSir-business', '-am', '-DskipTests', 'test-compile'
    )
    $priorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = (& $mavenExecutable @arguments 2>&1 | Out-String).Trim()
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $priorPreference
    if ($exitCode -ne 0) {
        throw "Plan-policy concurrency test compilation failed:`n$output"
    }
}

if (-not (Test-Path -LiteralPath $controlPlanePath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $policyPath -PathType Leaf)) {
    throw 'Required Independent Board migration SQL is missing.'
}
if (-not (Test-Path -LiteralPath $workRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $workRoot | Out-Null
}

$mavenExecutable = Resolve-MavenExecutable
Invoke-PlanPolicyConcurrencyTestCompile
$profiles = Resolve-MySqlProfiles -Requested $MySqlBinDirectories
$results = [System.Collections.Generic.List[object]]::new()

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
    $port = Get-LoopbackEphemeralPort
    $server = $null
    $cleaned = $false
    $originalMySqlTestLoginFile = $env:MYSQL_TEST_LOGIN_FILE

    try {
        $initializeOutput = (& $profile.mysqld '--no-defaults' '--initialize-insecure' `
            "--basedir=$($profile.base)" "--datadir=$dataRoot" 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) {
            throw "mysqld --initialize-insecure failed: $initializeOutput"
        }
        $server = Start-Process -FilePath $profile.mysqld -ArgumentList @(
            '--no-defaults', "--basedir=$($profile.base)", "--datadir=$dataRoot",
            '--bind-address=127.0.0.1', "--port=$port", '--mysqlx=0',
            "--log-error=$errorLog", '--character-set-server=utf8mb4',
            '--collation-server=utf8mb4_unicode_ci'
        ) -PassThru -WindowStyle Hidden

        $ready = $false
        for ($attempt = 0; $attempt -lt 160; $attempt++) {
            $priorPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & $profile.mysqlAdmin '--no-defaults' '--protocol=TCP' '-h127.0.0.1' `
                "-P$port" '-uroot' '--connect-timeout=1' 'ping' *> $null
            $pingExitCode = $LASTEXITCODE
            $ErrorActionPreference = $priorPreference
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
            throw "MySQL did not become ready: $log"
        }

        $runtime = (Invoke-MySqlText -Profile $profile -Port $port -Database '' -Sql `
            "SELECT CONCAT(VERSION(),'|',@@version_comment,'|',@@transaction_isolation);").output
        Assert-ExactOutput -Actual $runtime `
            -Expected "$($profile.version)|MySQL Community Server - GPL|REPEATABLE-READ" `
            -Stage 'server profile'

        $loginPath = "u3w-plan-policy-it-$($profile.version.Replace('.', '-'))-$suffix"
        $env:MYSQL_TEST_LOGIN_FILE = Join-Path $runRoot '.mylogin.cnf'
        $priorPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $profile.mysqlConfigEditor 'set' "--login-path=$loginPath" `
            '--host=127.0.0.1' "--port=$port" '--user=root' *> $null
        $loginEditorExitCode = $LASTEXITCODE
        $ErrorActionPreference = $priorPreference
        if ($loginEditorExitCode -ne 0) {
            throw 'Could not create the isolated MySQL login path.'
        }

        foreach ($database in @(
            'w3h_policy_ok',
            'w3h_policy_unknown',
            'w3h_policy_drift',
            'w3h_policy_concurrency')) {
            $bootstrap = @"
CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE $database.u3w_schema_migration (
  version VARCHAR(96) NOT NULL,
  applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  description VARCHAR(255) NOT NULL,
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"@
            $null = Invoke-MySqlText -Profile $profile -Port $port -Database '' -Sql $bootstrap
            $null = Invoke-MySqlFile -Profile $profile -Port $port -Database $database `
                -Path $controlPlanePath
        }

        $historicalSql = @"
INSERT INTO fbs_usage_operation (
  operation_id,request_digest,enterprise_id,member_id,user_id,product_code,
  metric_code,bucket_date,units,status,effective_plan_code,agenda_count,
  seat_count,remaining_count,completed_at
) VALUES
('w3h-historical-free',REPEAT('1',64),1001,2001,3001,'FBSIR_INDEPENDENT_BOARD',
 'MEETING','2026-07-22',1,'COMMITTED','BOARD_FREE',2,3,0,CURRENT_TIMESTAMP(3)),
('w3h-historical-vip',REPEAT('2',64),1001,2002,3002,'FBSIR_INDEPENDENT_BOARD',
 'MEETING','2026-07-22',1,'COMMITTED','BOARD_VIP',20,50,3,CURRENT_TIMESTAMP(3));
"@
        $null = Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql $historicalSql
        $null = Invoke-MySqlFile -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Path $policyPath
        $null = Invoke-MySqlFile -Profile $profile -Port $port `
            -Database 'w3h_policy_concurrency' -Path $policyPath

        $firstApply = (Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM fbs_plan_policy_revision_receipt),
  (SELECT COUNT(*) FROM fbs_plan_policy_head),
  (SELECT COUNT(*) FROM fbs_usage_operation_policy_receipt),
  (SELECT COUNT(*) FROM information_schema.triggers
   WHERE trigger_schema=DATABASE() AND event_object_table IN
     ('fbs_plan_policy_revision_receipt','fbs_usage_operation_policy_receipt','fbs_entitlement_receipt')),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version='20260722_independent_board_plan_policy_v1'
     AND description='Independent Board immutable plan policy revisions and operation lineage'));
"@).output
        Assert-ExactOutput -Actual $firstApply -Expected '2|2|2|7|1' -Stage 'first apply'

        $invalidPlanNames = [ordered]@{
            leadingSpace = "_utf8mb4' invalid-leading'"
            trailingSpace = "_utf8mb4'invalid-trailing '"
            controlCharacter = 'CONVERT(0x610A62 USING utf8mb4)'
            formatCharacter = 'CONVERT(0x61E2808B62 USING utf8mb4)'
            nonBreakingSpace = 'CONVERT(0xC2A06E616D65 USING utf8mb4)'
            narrowNoBreakSpace = 'CONVERT(0x6E616D65E280AF USING utf8mb4)'
        }
        foreach ($name in $invalidPlanNames.Keys) {
            $planNameExpression = $invalidPlanNames[$name]
            $invalidPlanName = Invoke-MySqlText -Profile $profile -Port $port `
                -Database 'w3h_policy_ok' -AllowFailure -Sql @"
INSERT INTO fbs_plan_policy_revision_receipt (
 receipt_id,product_code,plan_code,policy_version,previous_receipt_id,
 rollback_of_receipt_id,action,actor_type,actor_user_id,idempotency_key_digest,
 command_digest,previous_policy_digest,policy_digest,plan_name,vip,
 connector_required,daily_meeting_limit,agenda_limit,seat_limit,
 secretary_enabled,status,evidence_level
)
SELECT CONCAT('plan-policy-invalid-name-', '$name'),product_code,plan_code,2,receipt_id,
 NULL,'PLAN_POLICY_REVISED','ADMIN_USER',42,SHA2(CONCAT('w3h-invalid-idem-', '$name'),256),
 SHA2(CONCAT('w3h-invalid-command-', '$name'),256),policy_digest,
 SHA2(CONCAT('w3h-invalid-policy-', '$name'),256),$planNameExpression,vip,
 connector_required,daily_meeting_limit,agenda_limit,seat_limit,
 secretary_enabled,status,'ACTION_COMPLETED'
FROM fbs_plan_policy_revision_receipt
WHERE plan_code='BOARD_FREE' AND policy_version=1;
"@
            if ($invalidPlanName.exitCode -eq 0 -or
                $invalidPlanName.output -notmatch 'chk_plan_policy_receipt_quotas') {
                throw "Invalid plan-name probe unexpectedly passed: $name / $($invalidPlanName.output)"
            }
        }
        $invalidPlanNameCount = (Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql @"
SELECT COUNT(*) FROM fbs_plan_policy_revision_receipt
WHERE receipt_id LIKE 'plan-policy-invalid-name-%';
"@).output
        Assert-ExactOutput -Actual $invalidPlanNameCount -Expected '0' `
            -Stage 'invalid plan-name rollback'

        $advanceSql = @"
INSERT INTO fbs_plan_policy_revision_receipt (
 receipt_id,product_code,plan_code,policy_version,previous_receipt_id,
 rollback_of_receipt_id,action,actor_type,actor_user_id,idempotency_key_digest,
 command_digest,previous_policy_digest,policy_digest,plan_name,vip,
 connector_required,daily_meeting_limit,agenda_limit,seat_limit,
 secretary_enabled,status,evidence_level
)
SELECT 'plan-policy-board-vip-v2-it',product_code,plan_code,2,receipt_id,
 NULL,'PLAN_POLICY_REVISED','ADMIN_USER',42,SHA2('w3h-it-idempotency',256),
 SHA2('w3h-it-command',256),policy_digest,
 'dd92e808e95a49033ca0b9029fa5c3af1a9176d530569f989f14b1c502e039f5',
 plan_name,vip,connector_required,6,agenda_limit,seat_limit,
 secretary_enabled,status,'ACTION_COMPLETED'
FROM fbs_plan_policy_revision_receipt
WHERE plan_code='BOARD_VIP' AND policy_version=1;
UPDATE fbs_plan_policy_head
SET active_receipt_id='plan-policy-board-vip-v2-it',policy_version=2
WHERE product_code='FBSIR_INDEPENDENT_BOARD' AND plan_code='BOARD_VIP'
  AND active_receipt_id='plan-policy-baseline-board-vip-v1' AND policy_version=1;
"@
        $null = Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql $advanceSql

        $null = Invoke-MySqlFile -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Path $policyPath
        $null = Invoke-MySqlFile -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Path $controlPlanePath
        $preserved = (Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql @"
SELECT CONCAT(active_receipt_id,'|',policy_version,'|',
 (SELECT COUNT(*) FROM fbs_plan_policy_revision_receipt), '|',
 (SELECT COUNT(*) FROM fbs_usage_operation_policy_receipt))
FROM fbs_plan_policy_head WHERE plan_code='BOARD_VIP';
"@).output
        Assert-ExactOutput -Actual $preserved `
            -Expected 'plan-policy-board-vip-v2-it|2|3|2' `
            -Stage 'N greater than one completed replay'

        $historicalAuditLineage = (Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql @"
SELECT CONCAT_WS('|', o.operation_id, l.policy_receipt_id, l.policy_version,
                 r.policy_version, h.policy_version, r.plan_name)
FROM fbs_usage_operation o
LEFT JOIN fbs_usage_operation_policy_receipt l
  ON l.enterprise_id = o.enterprise_id
 AND BINARY l.operation_id = BINARY o.operation_id
 AND BINARY l.product_code = BINARY o.product_code
 AND BINARY l.plan_code = BINARY o.effective_plan_code
LEFT JOIN fbs_plan_policy_revision_receipt r
  ON BINARY r.receipt_id = BINARY l.policy_receipt_id
 AND BINARY r.product_code = BINARY o.product_code
 AND BINARY r.plan_code = BINARY o.effective_plan_code
 AND r.policy_version = l.policy_version
 AND BINARY r.policy_digest = BINARY l.policy_digest
INNER JOIN fbs_plan_policy_head h
  ON BINARY h.product_code = BINARY o.product_code
 AND BINARY h.plan_code = BINARY o.effective_plan_code
WHERE o.operation_id = 'w3h-historical-vip';
"@).output
        if ($historicalAuditLineage -notmatch `
                '^w3h-historical-vip\|plan-policy-baseline-board-vip-v1\|1\|1\|2\|.+$') {
            throw "Historical operation audit lineage drifted after the current VIP head advanced: $historicalAuditLineage"
        }

        $currentReadOutput = (& powershell.exe -NoProfile -ExecutionPolicy Bypass `
            -File $initializerPath -LoginPath $loginPath -MySqlExe $profile.mysql `
            -Database 'w3h_policy_ok' -PlanPolicyCurrentReadOnly 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or
            $currentReadOutput -notmatch 'PASS Independent Board plan-policy exact current-read' -or
            $currentReadOutput -notmatch 'No database write was requested') {
            throw "Plan-policy initializer current-read failed: $currentReadOutput"
        }

        $negativeStatements = [ordered]@{
            policyUpdate = "UPDATE fbs_plan_policy_revision_receipt SET plan_name='forbidden' WHERE policy_version=1 LIMIT 1;"
            policyDelete = "DELETE FROM fbs_plan_policy_revision_receipt WHERE policy_version=1 LIMIT 1;"
            lineageUpdate = "UPDATE fbs_usage_operation_policy_receipt SET policy_version=99 LIMIT 1;"
            lineageDelete = "DELETE FROM fbs_usage_operation_policy_receipt LIMIT 1;"
        }
        foreach ($name in $negativeStatements.Keys) {
            $negative = Invoke-MySqlText -Profile $profile -Port $port `
                -Database 'w3h_policy_ok' -Sql $negativeStatements[$name] -AllowFailure
            if ($negative.exitCode -eq 0 -or $negative.output -notmatch 'immutable') {
                throw "Negative immutability probe unexpectedly passed: $name / $($negative.output)"
            }
        }

        $entitlementSetup = @"
INSERT INTO fbs_entitlement_receipt
 (receipt_id,enterprise_id,actor_user_id,target_member_id,action,payload_digest,evidence_level)
VALUES ('w3h-entitlement-receipt',1001,42,2001,'W3H_TEST',REPEAT('3',64),'ACTION_COMPLETED');
"@
        $null = Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql $entitlementSetup
        foreach ($statement in @(
            "UPDATE fbs_entitlement_receipt SET action='forbidden' WHERE receipt_id='w3h-entitlement-receipt';",
            "DELETE FROM fbs_entitlement_receipt WHERE receipt_id='w3h-entitlement-receipt';"
        )) {
            $negative = Invoke-MySqlText -Profile $profile -Port $port `
                -Database 'w3h_policy_ok' -Sql $statement -AllowFailure
            if ($negative.exitCode -eq 0 -or $negative.output -notmatch 'immutable') {
                throw "Entitlement receipt immutability probe unexpectedly passed: $($negative.output)"
            }
        }

        $guardSetup = @"
INSERT INTO fbs_usage_operation (
 operation_id,request_digest,enterprise_id,member_id,user_id,product_code,
 metric_code,bucket_date,units,status,effective_plan_code,remaining_count
) VALUES ('w3h-guard-probe',REPEAT('4',64),1001,2003,3003,
 'FBSIR_INDEPENDENT_BOARD','MEETING','2026-07-22',1,'COMMITTED','BOARD_FREE',0);
"@
        $null = Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql $guardSetup
        $guardNegative = Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -AllowFailure -Sql @"
INSERT INTO fbs_usage_operation_policy_receipt
 (enterprise_id,operation_id,product_code,plan_code,policy_receipt_id,policy_version,policy_digest)
SELECT 1001,'w3h-guard-probe',product_code,plan_code,receipt_id,policy_version,policy_digest
FROM fbs_plan_policy_revision_receipt WHERE plan_code='BOARD_VIP' AND policy_version=2;
"@
        if ($guardNegative.exitCode -eq 0 -or
            $guardNegative.output -notmatch 'must match the exact operation and receipt') {
            throw "Lineage insert guard unexpectedly passed: $($guardNegative.output)"
        }
        $guardCaseVariant = Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -AllowFailure -Sql @"
INSERT INTO fbs_usage_operation_policy_receipt
 (enterprise_id,operation_id,product_code,plan_code,policy_receipt_id,policy_version,policy_digest)
SELECT 1001,'W3H-GUARD-PROBE',product_code,plan_code,receipt_id,policy_version,policy_digest
FROM fbs_plan_policy_revision_receipt WHERE plan_code='BOARD_FREE' AND policy_version=1;
"@
        if ($guardCaseVariant.exitCode -eq 0 -or
            $guardCaseVariant.output -notmatch 'must match the exact operation and receipt') {
            throw "Case-variant lineage guard unexpectedly passed: $($guardCaseVariant.output)"
        }
        $null = Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql @"
INSERT INTO fbs_usage_operation_policy_receipt
 (enterprise_id,operation_id,product_code,plan_code,policy_receipt_id,policy_version,policy_digest)
SELECT 1001,'w3h-guard-probe',product_code,plan_code,receipt_id,policy_version,policy_digest
FROM fbs_plan_policy_revision_receipt WHERE plan_code='BOARD_FREE' AND policy_version=1;
"@

        $unknownSetup = @"
INSERT INTO fbs_usage_operation (
 operation_id,request_digest,enterprise_id,member_id,user_id,product_code,
 metric_code,bucket_date,units,status,effective_plan_code,remaining_count
) VALUES ('w3h-unknown-plan',REPEAT('5',64),9001,9002,9003,
 'FBSIR_INDEPENDENT_BOARD','MEETING','2026-07-22',1,'COMMITTED','BOARD_UNKNOWN',0);
"@
        $null = Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_unknown' -Sql $unknownSetup
        $unknownResult = Invoke-MySqlFile -Profile $profile -Port $port `
            -Database 'w3h_policy_unknown' -Path $policyPath -AllowFailure
        if ($unknownResult.exitCode -eq 0 -or
            $unknownResult.output -notmatch 'Unknown Independent Board operation plan') {
            throw "Unknown plan migration did not fail closed: $($unknownResult.output)"
        }
        $unknownState = (Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_unknown' -Sql @"
SELECT CONCAT_WS('|',
 (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
  AND table_name IN ('fbs_plan_policy_revision_receipt','fbs_plan_policy_head','fbs_usage_operation_policy_receipt')),
 (SELECT COUNT(*) FROM u3w_schema_migration
  WHERE version='20260722_independent_board_plan_policy_v1'));
"@).output
        Assert-ExactOutput -Actual $unknownState -Expected '0|0' -Stage 'unknown plan fail closed'

        $null = Invoke-MySqlFile -Profile $profile -Port $port `
            -Database 'w3h_policy_drift' -Path $policyPath
        $null = Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_drift' -Sql @"
ALTER TABLE fbs_plan_policy_head
  ADD COLUMN drift_probe VARCHAR(8) NULL;
"@
        $driftResult = Invoke-MySqlFile -Profile $profile -Port $port `
            -Database 'w3h_policy_drift' -Path $policyPath -AllowFailure
        if ($driftResult.exitCode -eq 0 -or
            $driftResult.output -notmatch '39-column name' -or
            $driftResult.output -notmatch 'drifted') {
            throw "Raw metadata drift did not fail closed: $($driftResult.output)"
        }

        $triggerSnapshot = (Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql @"
SELECT CONCAT(trigger_name,'|',event_object_table,'|',event_manipulation,'|',
 action_timing,'|',SHA2(CAST(action_statement AS BINARY),256))
FROM information_schema.triggers
WHERE trigger_schema=DATABASE()
  AND event_object_table IN
    ('fbs_plan_policy_revision_receipt','fbs_usage_operation_policy_receipt','fbs_entitlement_receipt')
ORDER BY trigger_name;
"@).output
        $metadataSnapshot = (Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_ok' -Sql @"
SET SESSION group_concat_max_len=1048576;
SELECT CONCAT_WS('|',
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
   'T:',HEX(CAST(table_name AS BINARY)),'|O:',LPAD(ordinal_position,3,'0'),
   '|N:',HEX(CAST(column_name AS BINARY)),'|Y:',HEX(CAST(column_type AS BINARY)),
   '|U:',HEX(CAST(is_nullable AS BINARY)),
   '|D:',IF(column_default IS NULL,'N',CONCAT('V:',HEX(CAST(column_default AS BINARY)))),
   '|C:',IF(character_set_name IS NULL,'N',CONCAT('V:',HEX(CAST(character_set_name AS BINARY)))),
   '|L:',IF(collation_name IS NULL,'N',CONCAT('V:',HEX(CAST(collation_name AS BINARY)))),
   '|E:',HEX(CAST(extra AS BINARY)),
   '|G:',IF(generation_expression IS NULL,'N',CONCAT('V:',HEX(CAST(generation_expression AS BINARY)))))
   ORDER BY table_name,ordinal_position SEPARATOR 0x0A),256)
  FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name IN ('fbs_plan_policy_revision_receipt','fbs_plan_policy_head','fbs_usage_operation_policy_receipt')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
   'T:',HEX(CAST(table_name AS BINARY)),'|I:',HEX(CAST(index_name AS BINARY)),
   '|U:',non_unique,'|Y:',HEX(CAST(index_type AS BINARY)),'|V:',HEX(CAST(is_visible AS BINARY)),
   '|S:',seq_in_index,'|N:',IF(column_name IS NULL,'N',CONCAT('V:',HEX(CAST(column_name AS BINARY)))),
   '|X:',IF(expression IS NULL,'N',CONCAT('V:',HEX(CAST(expression AS BINARY)))),
   '|C:',IF(collation IS NULL,'N',CONCAT('V:',HEX(CAST(collation AS BINARY)))),
   '|P:',IF(sub_part IS NULL,'N',CONCAT('V:',sub_part)),'|Q:',HEX(CAST(nullable AS BINARY)))
   ORDER BY table_name,index_name,seq_in_index SEPARATOR 0x0A),256)
  FROM information_schema.statistics WHERE table_schema=DATABASE()
   AND table_name IN ('fbs_plan_policy_revision_receipt','fbs_plan_policy_head','fbs_usage_operation_policy_receipt')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
   'T:',HEX(CAST(rc.table_name AS BINARY)),'|C:',HEX(CAST(rc.constraint_name AS BINARY)),
   '|K:',HEX(CAST(rc.unique_constraint_name AS BINARY)),'|R:',HEX(CAST(rc.referenced_table_name AS BINARY)),
   '|U:',HEX(CAST(rc.update_rule AS BINARY)),'|D:',HEX(CAST(rc.delete_rule AS BINARY)),
   '|O:',kcu.ordinal_position,'|N:',HEX(CAST(kcu.column_name AS BINARY)),
   '|P:',HEX(CAST(kcu.referenced_column_name AS BINARY)),
   '|I:',IF(kcu.position_in_unique_constraint IS NULL,'N',CONCAT('V:',kcu.position_in_unique_constraint)))
   ORDER BY rc.table_name,rc.constraint_name,kcu.ordinal_position SEPARATOR 0x0A),256)
  FROM information_schema.referential_constraints rc
  INNER JOIN information_schema.key_column_usage kcu
   ON kcu.constraint_schema=rc.constraint_schema AND kcu.table_name=rc.table_name
  AND kcu.constraint_name=rc.constraint_name
  WHERE rc.constraint_schema=DATABASE() AND kcu.referenced_table_schema=DATABASE()
   AND rc.table_name IN ('fbs_plan_policy_revision_receipt','fbs_plan_policy_head','fbs_usage_operation_policy_receipt')),
 (SELECT SHA2(GROUP_CONCAT(CONCAT(
   'T:',HEX(CAST(tc.table_name AS BINARY)),'|C:',HEX(CAST(tc.constraint_name AS BINARY)),
   '|E:',HEX(CAST(tc.enforced AS BINARY)),'|Q:',HEX(CAST(cc.check_clause AS BINARY)))
   ORDER BY tc.table_name,tc.constraint_name SEPARATOR 0x0A),256)
  FROM information_schema.table_constraints tc
  INNER JOIN information_schema.check_constraints cc
   ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
  WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
   AND tc.table_name IN ('fbs_plan_policy_revision_receipt','fbs_plan_policy_head','fbs_usage_operation_policy_receipt')));
"@).output
        Assert-ExactOutput -Actual $metadataSnapshot -Expected (
            '3861fa022a759a9f9a5f773da995273b5259e361be7a931a9ad761eca02473d7|' +
            '2d21d400829820467a0915c202fbdd5343e5d9417fe1ac062a48742ec0a6541b|' +
            '03dfe7bb006d20b768840f71a435d0233355001dc35d6c7cffda5c68ef6151de|' +
            'b97cf71e29e1bfbbb58bd9ef58ed8f9334c586de102e43b6bb231e392ac84fad'
        ) -Stage 'raw metadata matrix'

        $concurrencyEvidence = Invoke-PlanPolicyConcurrencyIt `
            -Port $port -Version $profile.version -RunRoot $runRoot
        $auditReceiptRows = [int](Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_concurrency' -Sql @"
SELECT COUNT(*) FROM fbs_plan_policy_revision_receipt
WHERE BINARY product_code = BINARY 'FBSIR_INDEPENDENT_BOARD';
"@).output
        $auditExplain = (Invoke-MySqlText -Profile $profile -Port $port `
            -Database 'w3h_policy_concurrency' -Sql @"
EXPLAIN ANALYZE
SELECT r.id AS receipt_db_id, r.receipt_id, r.product_code, r.plan_code,
       r.policy_version, r.previous_receipt_id, r.rollback_of_receipt_id,
       r.action, r.actor_type, r.actor_user_id, r.idempotency_key_digest,
       r.command_digest, r.previous_policy_digest, r.policy_digest,
       r.plan_name, r.vip, r.connector_required, r.daily_meeting_limit,
       r.agenda_limit, r.seat_limit, r.secretary_enabled, r.status,
       r.evidence_level, r.created_at
FROM fbs_plan_policy_revision_receipt r
WHERE BINARY r.product_code = BINARY 'FBSIR_INDEPENDENT_BOARD'
ORDER BY r.created_at DESC, r.id DESC
LIMIT 101;
"@).output

        $results.Add([pscustomobject]@{
            version = $profile.version
            firstApply = $firstApply
            completedRerunPreserved = $preserved
            historicalAuditLineage = $historicalAuditLineage
            historicalBackfillCount = 2
            invalidPlanNamesRejected = 6
            unknownPlanRejected = $true
            metadataDriftRejected = $true
            updateDeleteRejected = 6
            lineageGuardRejected = $true
            lineageCaseVariantRejected = $true
            triggerSnapshot = if ($EmitMetadataSnapshot) { $triggerSnapshot } else { $null }
            metadataSnapshot = if ($EmitMetadataSnapshot) { $metadataSnapshot } else { $null }
            initializerCurrentReadOnly = $true
            concurrency = $concurrencyEvidence
            auditReceiptRows = $auditReceiptRows
            auditExplainAnalyze = $auditExplain
            productionConnectionUsed = $false
            workDirectoryCleaned = $true
        })
    }
    finally {
        $env:MYSQL_TEST_LOGIN_FILE = $originalMySqlTestLoginFile
        if ($null -ne $server) {
            & $profile.mysqlAdmin '--no-defaults' '--protocol=TCP' '-h127.0.0.1' `
                "-P$port" '-uroot' 'shutdown' 2>$null | Out-Null
            if (-not $server.HasExited) {
                $server.WaitForExit(10000) | Out-Null
            }
            if (-not $server.HasExited) {
                Stop-Process -Id $server.Id -Force
                $server.WaitForExit(10000) | Out-Null
            }
        }
        if (Test-Path -LiteralPath $runRoot) {
            Assert-SafeRunDirectory -Path $runRoot
            Remove-Item -LiteralPath $runRoot -Recurse -Force
        }
        $cleaned = -not (Test-Path -LiteralPath $runRoot)
        if (-not $cleaned) {
            throw "Plan-policy IT work directory cleanup failed: $runRoot"
        }
    }
}

if ($results.Count -ne $requiredVersions.Count -or
    (($results.version | Sort-Object) -join ',') -ne
        (($requiredVersions | Sort-Object) -join ',')) {
    throw 'The requested exact MySQL plan-policy matrix did not complete.'
}
if (Test-Path -LiteralPath $workRoot -PathType Container) {
    $remainingWork = @(Get-ChildItem -LiteralPath $workRoot -Force)
    $expectedWorkRoot = [System.IO.Path]::GetFullPath(
        (Join-Path $repoRoot 'work\independent-board-plan-policy-mysql-it'))
    if ($remainingWork.Count -ne 0 -or
        -not [string]::Equals($workRoot, $expectedWorkRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to remove a non-empty or unexpected plan-policy IT root.'
    }
    Remove-Item -LiteralPath $workRoot -Force
}

[pscustomobject]@{
    schemaVersion = 2
    ok = $true
    sourceSha256 = [ordered]@{
        publicInit030 = (Get-FileHash -LiteralPath $controlPlanePath -Algorithm SHA256).Hash.ToLowerInvariant()
        publicInit039 = (Get-FileHash -LiteralPath $policyPath -Algorithm SHA256).Hash.ToLowerInvariant()
        manifest = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
        initializer = (Get-FileHash -LiteralPath $initializerPath -Algorithm SHA256).Hash.ToLowerInvariant()
        runner = (Get-FileHash -LiteralPath $runnerPath -Algorithm SHA256).Hash.ToLowerInvariant()
        concurrencyTest = (Get-FileHash -LiteralPath $concurrencyTestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    matrix = @($results)
} | ConvertTo-Json -Depth 6
