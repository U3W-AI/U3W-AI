[CmdletBinding()]
param(
    [string]$LoginPath = "fbsir-local",
    [string]$MySqlExe = "mysql.exe",
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Database = "wxfbsir"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$SqlRoot = Join-Path $RepoRoot "sql"

if ($LoginPath -notmatch '^[A-Za-z0-9_.-]+$') {
    throw "LoginPath may contain only letters, numbers, dot, underscore, and dash."
}

function Resolve-SqlFile {
    param(
        [Parameter(Mandatory = $true)][string]$NamePattern,
        [string]$ContentMarker = ""
    )

    $matches = @(Get-ChildItem -LiteralPath $SqlRoot -Filter $NamePattern -File)
    if ($ContentMarker) {
        $matches = @($matches | Where-Object {
            (Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8).Contains($ContentMarker)
        })
    }
    if ($matches.Count -ne 1) {
        throw "Expected one SQL file for pattern '$NamePattern' and marker '$ContentMarker'; found $($matches.Count)."
    }
    return $matches[0]
}

function New-Step {
    param(
        [Parameter(Mandatory = $true)][string]$Version,
        [Parameter(Mandatory = $true)][string]$Description,
        [Parameter(Mandatory = $true)][System.IO.FileInfo]$File
    )
    [pscustomobject]@{
        Version = $Version
        Description = $Description
        File = $File
    }
}

$steps = @(
    New-Step "public_init_001" "base schema" (Resolve-SqlFile "wxfbsir.sql")
    New-Step "public_init_002" "Quartz scheduler schema" (Resolve-SqlFile "quartz.sql")
    New-Step "public_init_003" "resume storage" (Resolve-SqlFile "Resume.sql")
    New-Step "public_init_004" "gitee assistant" (Resolve-SqlFile "update_20260129_*.sql")
    New-Step "public_init_005" "prompts and webhook targets" (Resolve-SqlFile "update_20260303_*.sql")
    New-Step "public_init_006" "host whitelist runtime fields" (Resolve-SqlFile "update_20260316_*.sql")
    New-Step "public_init_007" "output permissions" (Resolve-SqlFile "update_20260321_*.sql")
    New-Step "public_init_008" "FBS entitlement base" (Resolve-SqlFile "update_20260408_*.sql")
    New-Step "public_init_009" "enterprise scene pack tables" (Resolve-SqlFile "update_20260409_*.sql" 'CREATE TABLE IF NOT EXISTS `fbs_enterprise`')
    New-Step "public_init_010" "platform scene pack menus" (Resolve-SqlFile "update_20260409_*.sql" "DELETE FROM sys_menu WHERE menu_name IN")
    New-Step "public_init_011" "user pack audit fields" (Resolve-SqlFile "update_20260410_*.sql" "ADD COLUMN operator BIGINT")
    New-Step "public_init_012" "enterprise scene pack menus" (Resolve-SqlFile "update_20260410_*.sql" "business:fbs:enterprise:list")
    New-Step "public_init_013" "skill API gateway" (Resolve-SqlFile "update_20260411_*.sql" "fbs_api_key")
    New-Step "public_init_014" "user self service menus" (Resolve-SqlFile "update_20260411_*.sql" "my:fbs:packs:list")
    New-Step "public_init_015" "WeCom CLI sync log" (Resolve-SqlFile "update_20260413_*.sql")
    New-Step "public_init_016" "points event id" (Resolve-SqlFile "update_20260416_*.sql")
    New-Step "public_init_017" "API key user fields" (Resolve-SqlFile "update_20260417_*.sql" "add_column_if_not_exists")
    New-Step "public_init_018" "user API key menus" (Resolve-SqlFile "update_20260417_*.sql" "my:apikey:list")
    New-Step "public_init_019" "skill API key expansion" (Resolve-SqlFile "update_20260420_*.sql")
    New-Step "public_init_020" "FBS menu parent repair" (Resolve-SqlFile "update_20260710_*.sql")
    New-Step "public_init_021" "Quartz task brand compatibility" (Resolve-SqlFile "update_20260711_*.sql" "WxFbsirTask.WxFbsir")
    New-Step "public_init_022" "smart bot control plane" (Resolve-SqlFile "update_20260711_*.sql" "fbs_bot_binding")
    New-Step "public_init_023" "smart bot encrypted artifacts" (Resolve-SqlFile "update_20260711_*.sql" "fbs_smartbot_input_artifact")
    New-Step "public_init_024" "smart bot outbox leases" (Resolve-SqlFile "update_20260711_*.sql" "lease_token")
    New-Step "public_init_025" "webhook hub security ledger" (Resolve-SqlFile "update_20260711_*.sql" "u3w_migrate_webhook_hub_20260711")
    New-Step "public_init_026" "smart bot scheduler jobs" (Resolve-SqlFile "update_20260711_*.sql" "smartBotInternalDispatcherTask")
)

if ($steps.Count -ne 26) {
    throw "The public database manifest must contain exactly 26 steps."
}

Write-Host "FBSir public database manifest ($($steps.Count) steps):"
for ($index = 0; $index -lt $steps.Count; $index++) {
    Write-Host ("{0,2}. {1} [{2}]" -f ($index + 1), $steps[$index].File.Name, $steps[$index].Version)
}

if ($DryRun) {
    Write-Host "Dry run complete. No database connection was opened."
    exit 0
}

$mysqlCommand = Get-Command $MySqlExe -ErrorAction Stop
$mysqlPath = $mysqlCommand.Source

function Invoke-MySqlBytes {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Bytes,
        [switch]$WithoutDatabase
    )

    $arguments = "--login-path=$LoginPath --default-character-set=utf8mb4 --batch --skip-column-names"
    if (-not $WithoutDatabase) {
        $arguments += " --database=$Database"
    }

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlPath
    $startInfo.Arguments = $arguments
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Unable to start mysql client."
    }

    try {
        $process.StandardInput.BaseStream.Write($Bytes, 0, $Bytes.Length)
        $process.StandardInput.BaseStream.Flush()
        $process.StandardInput.Close()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "mysql exited with code $($process.ExitCode): $stderr"
        }
        if ($stderr.Trim()) {
            Write-Warning $stderr.Trim()
        }
        return $stdout.Trim()
    }
    finally {
        $process.Dispose()
    }
}

function Invoke-MySqlText {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [switch]$WithoutDatabase
    )
    $encoding = [System.Text.UTF8Encoding]::new($false)
    Invoke-MySqlBytes -Bytes $encoding.GetBytes($Sql) -WithoutDatabase:$WithoutDatabase
}

function Invoke-MySqlFile {
    param([Parameter(Mandatory = $true)][System.IO.FileInfo]$File)
    Invoke-MySqlBytes -Bytes ([System.IO.File]::ReadAllBytes($File.FullName)) | Out-Null
}

$bootstrap = @"
CREATE DATABASE IF NOT EXISTS $Database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS $Database.u3w_schema_migration (
    version VARCHAR(96) NOT NULL,
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"@
Invoke-MySqlText -Sql $bootstrap -WithoutDatabase | Out-Null

$baseState = Invoke-MySqlText -Sql "SELECT description FROM u3w_schema_migration WHERE version='public_init_001' LIMIT 1;"
if (-not $baseState) {
    $existingTableCount = [int](Invoke-MySqlText -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name <> 'u3w_schema_migration';")
    if ($existingTableCount -gt 0) {
        throw "Database '$Database' is not empty and has no public_init_001 receipt. Use a fresh database or a reviewed upgrade procedure."
    }
}

foreach ($step in $steps) {
    $state = Invoke-MySqlText -Sql "SELECT description FROM u3w_schema_migration WHERE version='$($step.Version)' LIMIT 1;"
    if ($state -like "APPLIED:*") {
        Write-Host "SKIP $($step.Version) (already applied)"
        continue
    }
    if ($state) {
        throw "Step $($step.Version) is in state '$state'. Do not retry a partially applied DDL step; use a fresh database or reviewed recovery."
    }

    Invoke-MySqlText -Sql "INSERT INTO u3w_schema_migration(version, description) VALUES ('$($step.Version)', 'RUNNING:$($step.Description)');" | Out-Null
    Write-Host "APPLY $($step.Version): $($step.File.Name)"
    try {
        Invoke-MySqlFile -File $step.File
        Invoke-MySqlText -Sql "UPDATE u3w_schema_migration SET description='APPLIED:$($step.Description)', applied_at=CURRENT_TIMESTAMP WHERE version='$($step.Version)';" | Out-Null
    }
    catch {
        try {
            Invoke-MySqlText -Sql "UPDATE u3w_schema_migration SET description='FAILED:$($step.Description)', applied_at=CURRENT_TIMESTAMP WHERE version='$($step.Version)';" | Out-Null
        }
        catch {
            Write-Warning "Could not record the FAILED state."
        }
        throw
    }
}

$appliedCount = [int](Invoke-MySqlText -Sql "SELECT COUNT(*) FROM u3w_schema_migration WHERE version LIKE 'public_init_%' AND description LIKE 'APPLIED:%';")
if ($appliedCount -ne 26) {
    throw "Expected 26 applied public initialization receipts; found $appliedCount."
}

$verification = [int](Invoke-MySqlText -Sql @"
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema='$Database'
  AND table_name IN ('cv_storage','fbs_api_key','fbs_bot_binding','fbs_smartbot_input_artifact','fbs_delivery_outbox','wc_webhook_delivery');
"@)
if ($verification -ne 6) {
    throw "Database verification failed: expected six representative current tables; found $verification."
}

$hostTypeColumn = [int](Invoke-MySqlText -Sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$Database' AND table_name='ws_host_whitelist' AND column_name='host_type';")
if ($hostTypeColumn -ne 1) {
    throw "Database verification failed: ws_host_whitelist.host_type is missing."
}

$quartzTableCount = [int](Invoke-MySqlText -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name IN ('QRTZ_JOB_DETAILS','QRTZ_TRIGGERS','QRTZ_LOCKS');")
if ($quartzTableCount -ne 3) {
    throw "Database verification failed: representative Quartz scheduler tables are missing."
}

Write-Host "Database initialization complete: 26/26 steps applied to '$Database'."
