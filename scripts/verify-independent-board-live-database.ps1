[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Database,
    [string]$LoginPath = "fbsir-local",
    [string]$MySqlExe = "mysql.exe",
    [switch]$AllowNonScratchDatabaseName,
    [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Database -notmatch '^[A-Za-z0-9_]+$' -or $Database.Length -gt 64) {
    throw "Database must be 1-64 characters and contain only letters, numbers, and underscore."
}
if ($LoginPath -notmatch '^[A-Za-z0-9_.-]+$') {
    throw "LoginPath may contain only letters, numbers, dot, underscore, and dash."
}
$recommendedScratchName = $Database -match '^(u3w|fbsir)_(scratch|test|ci)(_|$)'
if (-not $recommendedScratchName -and -not $AllowNonScratchDatabaseName) {
    throw "Live database verification requires a scratch-style database name such as 'u3w_scratch_board_001'. Use AllowNonScratchDatabaseName only after reviewing the target."
}

$mysqlCommand = Get-Command $MySqlExe -ErrorAction SilentlyContinue
if (-not $mysqlCommand) {
    throw "Live database verification requires a real mysql client; '$MySqlExe' was not found. Static verification is not accepted as a substitute."
}

function Invoke-MySqlReadOnlyScalar {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $arguments = "--login-path=$LoginPath --default-character-set=utf8mb4 --batch --skip-column-names"
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlCommand.Source
    $startInfo.Arguments = $arguments
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Unable to start mysql for the live database preflight."
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
            throw "mysql live database preflight failed with exit code $($process.ExitCode): $stderr"
        }
        return $stdout
    }
    finally {
        $process.Dispose()
    }
}

$preexistingTableCountText = Invoke-MySqlReadOnlyScalar -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database';"
if ($preexistingTableCountText -notmatch '^\d+$') {
    throw "Live database preflight returned an invalid table count: '$preexistingTableCountText'."
}
$preexistingTableCount = [int]$preexistingTableCountText
if ($preexistingTableCount -ne 0) {
    throw "Live database first-apply proof requires an absent or empty scratch database; '$Database' already has $preexistingTableCount tables."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$initScript = Join-Path $PSScriptRoot "init-database.ps1"
if (-not (Test-Path -LiteralPath $initScript -PathType Leaf)) {
    throw "Canonical database initializer not found: $initScript"
}

$shell = (Get-Process -Id $PID).Path
$commonArguments = @(
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-File', $initScript,
    '-Database', $Database,
    '-LoginPath', $LoginPath,
    '-MySqlExe', $mysqlCommand.Source
)
$phaseResults = [System.Collections.Generic.List[object]]::new()

function Invoke-LiveDatabasePhase {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [string[]]$AdditionalArguments = @()
    )
    $startedAt = [DateTimeOffset]::UtcNow
    $output = & $shell @commonArguments @AdditionalArguments 2>&1
    $exitCode = $LASTEXITCODE
    $phase = [pscustomobject]@{
        name = $Name
        ok = ($exitCode -eq 0)
        exitCode = $exitCode
        startedAt = $startedAt.ToString('o')
        finishedAt = [DateTimeOffset]::UtcNow.ToString('o')
        output = (($output | ForEach-Object { $_.ToString() }) -join "`n").Trim()
    }
    $phaseResults.Add($phase)
    if (-not $phase.ok) {
        throw "Live database phase '$Name' failed with exit code $exitCode.`n$($phase.output)"
    }
}

Invoke-LiveDatabasePhase -Name 'first_apply'
Invoke-LiveDatabasePhase -Name 'completed_rerun'
Invoke-LiveDatabasePhase -Name 'readonly_current_read' -AdditionalArguments @('-CurrentReadOnly')

$result = [pscustomobject]@{
    schemaVersion = 1
    ok = $true
    root = $repoRoot
    database = $Database
    recommendedScratchName = $recommendedScratchName
    preexistingTableCount = $preexistingTableCount
    mysqlPath = $mysqlCommand.Source
    phases = @($phaseResults)
}
if ($Json) {
    $result | ConvertTo-Json -Depth 6
}
else {
    Write-Host "PASS Independent Board live database gate for '$Database': first apply, completed rerun, and read-only current-read."
}
