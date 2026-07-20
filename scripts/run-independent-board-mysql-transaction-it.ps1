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
$workRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'work\independent-board-mysql-it'))
$database = 'u3w_independent_board_it'

function Resolve-MySqlBinDirectory {
    param([string]$Requested)

    $candidates = New-Object System.Collections.Generic.List[string]
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

$mysqlBin = Resolve-MySqlBinDirectory -Requested $MySqlBinDirectory
$mysqld = Join-Path $mysqlBin 'mysqld.exe'
$mysql = Join-Path $mysqlBin 'mysql.exe'
$mysqlAdmin = Join-Path $mysqlBin 'mysqladmin.exe'
$baseDirectory = Split-Path -Parent $mysqlBin

if ($Port -eq 0) {
    $Port = Get-LoopbackEphemeralPort
}
if ($Port -eq 3306) {
    throw 'Port 3306 is forbidden for the destructive integration test.'
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

    $priorErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $mysql '--protocol=tcp' '--host=127.0.0.1' "--port=$Port" '--user=root' `
        '--default-character-set=utf8mb4' '--execute' `
        "CREATE DATABASE ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    $createDatabaseExitCode = $LASTEXITCODE
    $ErrorActionPreference = $priorErrorActionPreference
    if ($createDatabaseExitCode -ne 0) {
        throw "Could not create the dedicated database; mysql exited with $createDatabaseExitCode"
    }

    $env:INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP = 'true'
    $env:INDEPENDENT_BOARD_MYSQL_IT_URL = "jdbc:mysql://127.0.0.1:$Port/$database" +
        '?useAffectedRows=false&connectionTimeZone=Asia%2FShanghai&useSSL=false&allowPublicKeyRetrieval=true'
    $env:INDEPENDENT_BOARD_MYSQL_IT_USERNAME = 'root'
    $env:INDEPENDENT_BOARD_MYSQL_IT_PASSWORD = ''

    Write-Host "Running Spring/MyBatis Independent Board MySQL IT on loopback port $Port"
    & mvn -pl FBSir-business -am '-Dtest=IndependentBoardMysqlTransactionIT' `
        '-Dsurefire.failIfNoSpecifiedTests=false' test
    if ($LASTEXITCODE -ne 0) {
        throw "Independent Board MySQL transaction IT failed with Maven exit code $LASTEXITCODE"
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
}

if ($testPassed) {
    [ordered]@{
        schemaVersion = 1
        test = 'IndependentBoardMysqlTransactionIT'
        result = 'PASS'
        host = '127.0.0.1'
        port = $Port
        database = $database
        destructiveTestConsent = $true
        productionConnectionUsed = $false
        workDirectoryCleaned = $true
    } | ConvertTo-Json -Depth 4
}
