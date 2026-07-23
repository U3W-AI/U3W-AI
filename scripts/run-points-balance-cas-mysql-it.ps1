[CmdletBinding()]
param(
    [switch]$AllowDestructiveTest,
    [string[]]$MySqlBinDirectories = @(),
    [ValidateSet('8.0.30', '8.4.8')]
    [string[]]$Versions = @('8.0.30', '8.4.8'),
    [string]$IsolatedWorkRoot = 'C:\u3w-points-cas-it'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $AllowDestructiveTest) {
    throw 'Explicit -AllowDestructiveTest consent is required.'
}

$repoRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$mapperPath = Join-Path $repoRoot 'FBSir-business\src\main\resources\mapper\point\PointsMapper.xml'
$runnerPath = [System.IO.Path]::GetFullPath($MyInvocation.MyCommand.Path)
$supportedVersions = @('8.0.30', '8.4.8')
$requestedVersions = @($Versions | Sort-Object -Unique)

if ($requestedVersions.Count -ne $Versions.Count -or
    (($requestedVersions | Sort-Object) -join ',') -ne (($Versions | Sort-Object) -join ',')) {
    throw 'Versions must contain unique supported MySQL versions.'
}
if (-not (Test-Path -LiteralPath $mapperPath -PathType Leaf)) {
    throw "Points mapper XML is missing: $mapperPath"
}

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-IsolatedRoot {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ($Path -notmatch '^[A-Za-z]:\\') {
        throw 'IsolatedWorkRoot must be an explicit absolute path.'
    }
    $fullPath = [System.IO.Path]::GetFullPath($Path).TrimEnd('\\')
    if ($fullPath -match '^[A-Za-z]:$' -or $fullPath.Length -gt 48 -or
        (Split-Path -Leaf $fullPath) -notmatch '^u3w-points-cas-it(?:-[A-Za-z0-9]+)?$') {
        throw 'IsolatedWorkRoot must be a short dedicated u3w-points-cas-it directory and not a drive root.'
    }
    if (Test-Path -LiteralPath $fullPath -PathType Container) {
        $item = Get-Item -LiteralPath $fullPath -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Refusing a reparse-point isolated root: $fullPath"
        }
    }
    return $fullPath
}

function Assert-NoDescendantReparsePoint {
    param([Parameter(Mandatory = $true)][string]$Path)

    $root = Get-Item -LiteralPath $Path -Force
    $pending = [System.Collections.Generic.Stack[System.IO.DirectoryInfo]]::new()
    $pending.Push($root)
    while ($pending.Count -gt 0) {
        $directory = $pending.Pop()
        foreach ($entry in $directory.GetFileSystemInfos()) {
            if (($entry.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "Refusing cleanup below a reparse-point descendant: $($entry.FullName)"
            }
            if ($entry -is [System.IO.DirectoryInfo]) {
                $pending.Push($entry)
            }
        }
    }
}

function Assert-SafeRunDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($Root + '\', [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $fullPath) -notmatch '^run-mysql-(8-0-30|8-4-8)-[0-9]{8}T[0-9]{6}-[0-9]+-[0-9a-f]{8}$') {
        throw "Refusing cleanup outside the dedicated MySQL IT run root: $fullPath"
    }
    if (Test-Path -LiteralPath $fullPath -PathType Container) {
        $item = Get-Item -LiteralPath $fullPath -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Refusing cleanup of a reparse-point MySQL IT run: $fullPath"
        }
        Assert-NoDescendantReparsePoint -Path $fullPath
    }
}

function Remove-SafeRunDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Path
    )

    Assert-SafeRunDirectory -Root $Root -Path $Path
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        return
    }
    [System.IO.Directory]::Delete($Path, $true)
    if (Test-Path -LiteralPath $Path) {
        throw "Unable to clean the dedicated MySQL IT run directory: $Path"
    }
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

function Resolve-MySqlProfiles {
    param([Parameter(Mandatory = $true)][string[]]$Requested)

    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($candidate in $MySqlBinDirectories) {
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
    $toolchainRoot = Join-Path $env:USERPROFILE '.codex\cache\toolchains'
    $candidates.Add((Join-Path $toolchainRoot 'mysql-8.0.30-winx64\bin'))
    $candidates.Add((Join-Path $toolchainRoot 'mysql-8.4.8-winx64\bin'))

    $profiles = @{}
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        $bin = [System.IO.Path]::GetFullPath($candidate)
        $mysqld = Join-Path $bin 'mysqld.exe'
        $mysql = Join-Path $bin 'mysql.exe'
        $mysqlAdmin = Join-Path $bin 'mysqladmin.exe'
        if (-not (Test-Path -LiteralPath $mysqld -PathType Leaf) -or
            -not (Test-Path -LiteralPath $mysql -PathType Leaf) -or
            -not (Test-Path -LiteralPath $mysqlAdmin -PathType Leaf)) {
            continue
        }
        $versionOutput = (& $mysqld '--no-defaults' '--version' 2>&1 | Out-String).Trim()
        $match = [regex]::Match($versionOutput, 'Ver\s+(?<version>[0-9]+\.[0-9]+\.[0-9]+)')
        if ($match.Success -and $match.Groups['version'].Value -in $supportedVersions) {
            $version = $match.Groups['version'].Value
            if (-not $profiles.ContainsKey($version)) {
                $profiles[$version] = [pscustomobject]@{
                    version = $version
                    base = Split-Path -Parent $bin
                    mysqld = $mysqld
                    mysql = $mysql
                    mysqlAdmin = $mysqlAdmin
                }
            }
        }
    }
    $missing = @($Requested | Where-Object { -not $profiles.ContainsKey($_) })
    if ($missing.Count -ne 0) {
        throw "The exact requested MySQL matrix is unavailable: $($missing -join ', ')."
    }
    return @($Requested | ForEach-Object { $profiles[$_] })
}

function Invoke-MySqlText {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$Database = ''
    )

    $arguments = @('--no-defaults', '--protocol=TCP', '--host=127.0.0.1', "--port=$Port",
        '--user=root', '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names')
    if ($Database) {
        if ($Database -notmatch '^[A-Za-z0-9_]{1,64}$') {
            throw "Unsafe isolated database name: $Database"
        }
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
        $client.StandardInput.Write($Sql)
        $client.StandardInput.Close()
        $client.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult().Trim()
        $stderr = $stderrTask.GetAwaiter().GetResult().Trim()
        if ($client.ExitCode -ne 0) {
            throw "Isolated mysql client exited with $($client.ExitCode): $stderr"
        }
        return $stdout
    }
    finally {
        $client.Dispose()
    }
}

function Start-CompareAndSetClient {
    param(
        [Parameter(Mandatory = $true)]$Profile,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][int]$Points
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Profile.mysql
    $startInfo.Arguments = @('--no-defaults', '--protocol=TCP', '--host=127.0.0.1', "--port=$Port",
        '--user=root', "--database=$Database", '--batch', '--raw', '--skip-column-names') -join ' '
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $client = [System.Diagnostics.Process]::new()
    $client.StartInfo = $startInfo
    if (-not $client.Start()) {
        throw 'Unable to start an isolated compare-and-set mysql client.'
    }
    $stdoutTask = $client.StandardOutput.ReadToEndAsync()
    $stderrTask = $client.StandardError.ReadToEndAsync()
    $client.StandardInput.Write(@"
SET @expected_points = (SELECT IFNULL(points, 0) FROM sys_user WHERE user_id = 1);
DO SLEEP(0.75);
UPDATE sys_user
SET points = $Points
WHERE user_id = 1
  AND (points = @expected_points OR (points IS NULL AND @expected_points = 0));
SELECT ROW_COUNT();
"@)
    $client.StandardInput.Close()
    return [pscustomobject]@{ process = $client; stdoutTask = $stdoutTask; stderrTask = $stderrTask }
}

function Complete-CompareAndSetClient {
    param([Parameter(Mandatory = $true)]$Client)

    try {
        $Client.process.WaitForExit()
        $stdout = $Client.stdoutTask.GetAwaiter().GetResult().Trim()
        $stderr = $Client.stderrTask.GetAwaiter().GetResult().Trim()
        if ($Client.process.ExitCode -ne 0) {
            throw "Isolated compare-and-set client exited with $($Client.process.ExitCode): $stderr"
        }
        if ($stdout -notin @('0', '1')) {
            throw "Unexpected compare-and-set row count: '$stdout'"
        }
        return [int]$stdout
    }
    finally {
        $Client.process.Dispose()
    }
}

function Get-RunBoundMySqlProcessId {
    param(
        [Parameter(Mandatory = $true)][string]$PidFile,
        [Parameter(Mandatory = $true)][string]$DataRoot,
        [Parameter(Mandatory = $true)][int]$Port
    )

    if (-not (Test-Path -LiteralPath $PidFile -PathType Leaf)) {
        return $null
    }
    $processId = 0
    $rawPid = (Get-Content -LiteralPath $PidFile -Raw).Trim()
    if (-not [int]::TryParse($rawPid, [ref]$processId) -or $processId -le 0) {
        throw "Dedicated MySQL pid file is invalid: $PidFile"
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    if ($null -eq $process) {
        return $null
    }
    if ($process.CommandLine -notlike "*$DataRoot*" -or $process.CommandLine -notlike "*--port=$Port*") {
        throw "Refusing to manage PID $processId because it is not bound to this MySQL IT run."
    }
    return $processId
}

function Assert-LoopbackListenerOwnedBy {
    param([Parameter(Mandatory = $true)][int]$Port, [Parameter(Mandatory = $true)][int]$ProcessId)

    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop |
        Where-Object { $_.LocalAddress -in @('127.0.0.1', '::1') })
    if ($listeners.Count -ne 1 -or $listeners[0].OwningProcess -ne $ProcessId) {
        throw "Dedicated MySQL listener ownership verification failed for port $Port."
    }
}

$workRoot = Assert-IsolatedRoot -Path $IsolatedWorkRoot
if (-not (Test-Path -LiteralPath $workRoot -PathType Container)) {
    New-Item -ItemType Directory -Path $workRoot | Out-Null
}
$profiles = Resolve-MySqlProfiles -Requested $requestedVersions
$sourceSha256 = [ordered]@{ mapper = Get-Sha256 -Path $mapperPath; runner = Get-Sha256 -Path $runnerPath }
$results = [System.Collections.Generic.List[object]]::new()

foreach ($profile in $profiles) {
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmss')
    $suffix = ([Guid]::NewGuid().ToString('N')).Substring(0, 8)
    $runRoot = Join-Path $workRoot "run-mysql-$($profile.version.Replace('.', '-'))-$stamp-$PID-$suffix"
    Assert-SafeRunDirectory -Root $workRoot -Path $runRoot
    New-Item -ItemType Directory -Path $runRoot | Out-Null
    $dataRoot = Join-Path $runRoot 'data'
    New-Item -ItemType Directory -Path $dataRoot | Out-Null
    $pidFile = Join-Path $runRoot 'mysqld.pid'
    $errorLog = Join-Path $runRoot 'mysql.err'
    $port = Get-LoopbackEphemeralPort
    $database = "pcas_$($profile.version.Replace('.', ''))_$suffix"
    $server = $null
    $ownedServerProcessId = $null
    $cleaned = $false

    try {
        $initialize = (& $profile.mysqld '--no-defaults' '--initialize-insecure' `
            "--basedir=$($profile.base)" "--datadir=$dataRoot" 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) {
            throw "mysqld --initialize-insecure failed: $initialize"
        }
        $server = Start-Process -FilePath $profile.mysqld -ArgumentList @(
            '--no-defaults', "--basedir=$($profile.base)", "--datadir=$dataRoot", '--bind-address=127.0.0.1',
            "--port=$port", '--mysqlx=0', "--pid-file=$pidFile", "--log-error=$errorLog"
        ) -PassThru -WindowStyle Hidden
        $ready = $false
        for ($attempt = 0; $attempt -lt 160; $attempt++) {
            $oldPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & $profile.mysqlAdmin '--no-defaults' '--protocol=TCP' '-h127.0.0.1' "-P$port" '-uroot' `
                '--connect-timeout=1' 'ping' *> $null
            $pingExitCode = $LASTEXITCODE
            $ErrorActionPreference = $oldPreference
            if ($pingExitCode -eq 0) {
                $ready = $true
                break
            }
            Start-Sleep -Milliseconds 250
        }
        if (-not $ready) {
            throw "Isolated MySQL $($profile.version) did not become ready."
        }
        for ($attempt = 0; $attempt -lt 20; $attempt++) {
            $ownedServerProcessId = Get-RunBoundMySqlProcessId -PidFile $pidFile -DataRoot $dataRoot -Port $port
            if ($null -ne $ownedServerProcessId) {
                break
            }
            Start-Sleep -Milliseconds 250
        }
        if ($null -eq $ownedServerProcessId) {
            throw 'Ready isolated MySQL has no run-bound PID.'
        }
        Assert-LoopbackListenerOwnedBy -Port $port -ProcessId $ownedServerProcessId

        $runtime = Invoke-MySqlText -Profile $profile -Port $port -Sql 'SELECT VERSION();'
        if ($runtime -ne $profile.version) {
            throw "Unexpected isolated MySQL version: $runtime"
        }
        Invoke-MySqlText -Profile $profile -Port $port -Sql @"
CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE $database.sys_user (
  user_id BIGINT NOT NULL PRIMARY KEY,
  points INT NULL
) ENGINE=InnoDB;
INSERT INTO $database.sys_user (user_id, points) VALUES (1, 100), (2, NULL);
"@ | Out-Null

        $first = Start-CompareAndSetClient -Profile $profile -Port $port -Database $database -Points 110
        $second = Start-CompareAndSetClient -Profile $profile -Port $port -Database $database -Points 90
        $firstAffected = Complete-CompareAndSetClient -Client $first
        $secondAffected = Complete-CompareAndSetClient -Client $second
        $affectedRows = (@($firstAffected, $secondAffected) | Sort-Object) -join '|'
        if ($affectedRows -ne '0|1') {
            throw "Concurrent compare-and-set affected rows drifted: $affectedRows"
        }
        $finalBalance = Invoke-MySqlText -Profile $profile -Port $port -Database $database `
            -Sql 'SELECT points FROM sys_user WHERE user_id = 1;'
        if ($finalBalance -notin @('90', '110')) {
            throw "Concurrent compare-and-set final balance drifted: $finalBalance"
        }
        $nullNormalization = Invoke-MySqlText -Profile $profile -Port $port -Database $database -Sql @'
UPDATE sys_user
SET points = 7
WHERE user_id = 2
  AND (points = 0 OR (points IS NULL AND 0 = 0));
SELECT CONCAT_WS('|', ROW_COUNT(), (SELECT points FROM sys_user WHERE user_id = 2));
'@
        if ($nullNormalization -ne '1|7') {
            throw "NULL normalized compare-and-set drifted: $nullNormalization"
        }
        $missingUser = Invoke-MySqlText -Profile $profile -Port $port -Database $database -Sql @'
UPDATE sys_user
SET points = 3
WHERE user_id = 3
  AND (points = 0 OR (points IS NULL AND 0 = 0));
SELECT ROW_COUNT();
'@
        if ($missingUser -ne '0') {
            throw "Missing user compare-and-set drifted: $missingUser"
        }
        $results.Add([pscustomobject]@{
            version = $profile.version
            concurrentAffectedRows = $affectedRows
            finalBalance = $finalBalance
            nullNormalizedCompareAndSet = $nullNormalization
            missingUserCompareAndSet = $missingUser
            productionConnectionUsed = $false
        })
    }
    finally {
        $cleanupServerProcessId = Get-RunBoundMySqlProcessId -PidFile $pidFile -DataRoot $dataRoot -Port $port
        if ($null -eq $cleanupServerProcessId) {
            $cleanupServerProcessId = $ownedServerProcessId
        }
        if ($null -ne $cleanupServerProcessId) {
            $oldPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            $shutdownOutput = (& $profile.mysqlAdmin '--no-defaults' '--protocol=TCP' '-h127.0.0.1' `
                "-P$port" '-uroot' 'shutdown' 2>&1 | Out-String).Trim()
            $shutdownExitCode = $LASTEXITCODE
            $ErrorActionPreference = $oldPreference
            for ($attempt = 0; $attempt -lt 40; $attempt++) {
                if ($null -eq (Get-Process -Id $cleanupServerProcessId -ErrorAction SilentlyContinue)) {
                    break
                }
                Start-Sleep -Milliseconds 250
            }
            if ($null -ne (Get-Process -Id $cleanupServerProcessId -ErrorAction SilentlyContinue)) {
                $ownedServer = Get-CimInstance Win32_Process -Filter "ProcessId=$cleanupServerProcessId"
                if ($null -eq $ownedServer -or $ownedServer.CommandLine -notlike "*$dataRoot*" -or
                    $ownedServer.CommandLine -notlike "*--port=$port*") {
                    throw "Refusing to terminate a process not bound to this MySQL IT run: $cleanupServerProcessId"
                }
                Stop-Process -Id $cleanupServerProcessId -Force -ErrorAction Stop
                Start-Sleep -Milliseconds 250
                if ($null -ne (Get-Process -Id $cleanupServerProcessId -ErrorAction SilentlyContinue)) {
                    # mysqld can be hosted by a short-lived Windows launcher.  The
                    # target was bound above by PID file, data root, and port, so a
                    # tree-level termination remains constrained to this test run.
                    & taskkill.exe '/PID' "$cleanupServerProcessId" '/T' '/F' *> $null
                }
            }
            for ($attempt = 0; $attempt -lt 40; $attempt++) {
                if ($null -eq (Get-Process -Id $cleanupServerProcessId -ErrorAction SilentlyContinue)) {
                    break
                }
                Start-Sleep -Milliseconds 250
            }
            if ($null -ne (Get-Process -Id $cleanupServerProcessId -ErrorAction SilentlyContinue)) {
                throw "Dedicated MySQL server remained after shutdown and targeted termination: $cleanupServerProcessId"
            }
            if ($shutdownExitCode -ne 0 -and [string]::IsNullOrWhiteSpace($shutdownOutput)) {
                throw "Dedicated MySQL shutdown failed without diagnostic output (exit $shutdownExitCode)."
            }
        }
        if ($null -ne $server) {
            $server.Refresh()
            if (-not $server.HasExited) {
                $server.WaitForExit(10000) | Out-Null
            }
            if (-not $server.HasExited) {
                $launcher = Get-CimInstance Win32_Process -Filter "ProcessId=$($server.Id)"
                if ($null -eq $launcher -or $launcher.CommandLine -notlike "*$dataRoot*" -or
                    $launcher.CommandLine -notlike "*--port=$port*") {
                    throw "Refusing to terminate an unbound MySQL launcher: $($server.Id)"
                }
                Stop-Process -Id $server.Id -Force -ErrorAction Stop
            }
            $server.Dispose()
        }
        $remainingListeners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalAddress -in @('127.0.0.1', '::1') })
        foreach ($listener in $remainingListeners) {
            $listenerOwner = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
            if ($null -eq $listenerOwner -or $listenerOwner.CommandLine -notlike "*$dataRoot*" -or
                $listenerOwner.CommandLine -notlike "*--port=$port*") {
                throw "Refusing to terminate a listener not bound to this MySQL IT run on port $port."
            }
            Stop-Process -Id $listener.OwningProcess -Force -ErrorAction Stop
        }
        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            if (@(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue).Count -eq 0) {
                break
            }
            Start-Sleep -Milliseconds 250
        }
        if (@(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue).Count -ne 0) {
            throw "Disposable MySQL loopback port $port remained open after shutdown."
        }
        if (Test-Path -LiteralPath $runRoot -PathType Container) {
            Remove-SafeRunDirectory -Root $workRoot -Path $runRoot
            $cleaned = $true
        }
    }
    if (-not $cleaned) {
        throw "MySQL IT work directory was not cleaned: $runRoot"
    }
    $results[$results.Count - 1] | Add-Member -NotePropertyName workDirectoryCleaned -NotePropertyValue $true
}

[pscustomobject]@{
    kind = 'fbsir.points-balance-cas.mysql-it/v1'
    result = if ($requestedVersions.Count -eq 2) { 'PASS_LOCAL_DUAL_MYSQL_CAS' } else { 'PASS_LOCAL_MYSQL_CAS_MATRIX' }
    versions = @($results)
    sourceSha256 = $sourceSha256
} | ConvertTo-Json -Depth 5
