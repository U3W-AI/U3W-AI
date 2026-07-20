[CmdletBinding()]
param(
    [string]$LoginPath = "fbsir-local",
    [string]$MySqlExe = "mysql.exe",
    [string]$Database = "wxfbsir",
    [switch]$DryRun,
    [switch]$ManifestJson,
    [switch]$CurrentReadOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$SqlRoot = Join-Path $RepoRoot "sql"

if ($LoginPath -notmatch '^[A-Za-z0-9_.-]+$') {
    throw "LoginPath may contain only letters, numbers, dot, underscore, and dash."
}
if ($Database -notmatch '^[A-Za-z0-9_]+$' -or $Database.Length -gt 64) {
    throw "Database must be 1-64 characters and contain only letters, numbers, and underscore."
}
if ($CurrentReadOnly -and ($DryRun -or $ManifestJson)) {
    throw "CurrentReadOnly cannot be combined with DryRun or ManifestJson."
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
    New-Step "public_init_027" "Truth Spine test-state receipt ledger" (Resolve-SqlFile "update_20260712_truth_spine_test_state_receipt.sql")
    New-Step "public_init_028" "Independent Board product entitlement control plane" (Resolve-SqlFile "update_20260720_independent_board_control_plane.sql")
)

for ($index = 0; $index -lt $steps.Count; $index++) {
    $expectedVersion = "public_init_{0:D3}" -f ($index + 1)
    if ($steps[$index].Version -ne $expectedVersion) {
        throw "Database manifest order is invalid at position $($index + 1): expected '$expectedVersion', found '$($steps[$index].Version)'."
    }
}

$duplicateVersions = @($steps | Group-Object Version | Where-Object Count -ne 1)
if ($duplicateVersions.Count -gt 0) {
    throw "Database manifest contains duplicate versions: $($duplicateVersions.Name -join ', ')."
}

$managedUpdateFiles = @(Get-ChildItem -LiteralPath $SqlRoot -Filter "update_*.sql" -File)
$manifestUpdateSteps = @($steps | Where-Object { $_.File.Name -like "update_*.sql" })
$duplicateManagedFiles = @($manifestUpdateSteps | Group-Object { $_.File.FullName.ToLowerInvariant() } | Where-Object Count -ne 1)
$manifestManagedPaths = @($manifestUpdateSteps | ForEach-Object { $_.File.FullName.ToLowerInvariant() })
$missingManagedFiles = @($managedUpdateFiles | Where-Object { $manifestManagedPaths -notcontains $_.FullName.ToLowerInvariant() })
$managedPaths = @($managedUpdateFiles | ForEach-Object { $_.FullName.ToLowerInvariant() })
$unexpectedManagedSteps = @($manifestUpdateSteps | Where-Object { $managedPaths -notcontains $_.File.FullName.ToLowerInvariant() })
if ($duplicateManagedFiles.Count -gt 0 -or $missingManagedFiles.Count -gt 0 -or $unexpectedManagedSteps.Count -gt 0) {
    $details = @()
    if ($duplicateManagedFiles.Count -gt 0) {
        $details += "duplicates=$($duplicateManagedFiles.Name -join ',')"
    }
    if ($missingManagedFiles.Count -gt 0) {
        $details += "missing=$($missingManagedFiles.Name -join ',')"
    }
    if ($unexpectedManagedSteps.Count -gt 0) {
        $details += "unexpected=$($unexpectedManagedSteps.File.Name -join ',')"
    }
    throw "Database manifest must cover every managed update SQL exactly once ($($details -join '; '))."
}

if ($ManifestJson -and -not $DryRun) {
    throw "ManifestJson is a read-only output mode and must be used together with DryRun."
}
if ($ManifestJson) {
    [pscustomobject]@{
        schemaVersion = 1
        manifestStepCount = $steps.Count
        managedUpdateSqlCount = $managedUpdateFiles.Count
        coverageOk = $true
        dryRun = [bool]$DryRun
        databaseConnectionOpened = $false
        database = $Database
        steps = @($steps | ForEach-Object {
            [pscustomobject]@{
                version = $_.Version
                description = $_.Description
                file = $_.File.Name
            }
        })
    } | ConvertTo-Json -Depth 5 -Compress
}
else {
    Write-Host "FBSir public database manifest ($($steps.Count) steps):"
    for ($index = 0; $index -lt $steps.Count; $index++) {
        Write-Host ("{0,2}. {1} [{2}]" -f ($index + 1), $steps[$index].File.Name, $steps[$index].Version)
    }
}

if ($DryRun) {
    if (-not $ManifestJson) {
        Write-Host "Dry run complete. No database connection was opened."
    }
    return
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

function Get-BaseSchemaBytesForTargetDatabase {
    param([Parameter(Mandatory = $true)][System.IO.FileInfo]$File)

    $sourceBytes = [System.IO.File]::ReadAllBytes($File.FullName)
    $strictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)
    $sourceText = $strictUtf8.GetString($sourceBytes)
    $sourceCreate = 'CREATE DATABASE IF NOT EXISTS `wxfbsir` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
    $sourceUse = 'USE `wxfbsir`;'
    $createMatches = @([regex]::Matches($sourceText, [regex]::Escape($sourceCreate)))
    $useMatches = @([regex]::Matches($sourceText, [regex]::Escape($sourceUse)))
    $allCreateStatements = @([regex]::Matches($sourceText, '(?im)^\s*CREATE\s+DATABASE\b'))
    $allUseStatements = @([regex]::Matches($sourceText, '(?im)^\s*USE\s+`?[^`;\s]+`?\s*;'))
    if ($createMatches.Count -ne 1 -or $useMatches.Count -ne 1 -or
        $allCreateStatements.Count -ne 1 -or $allUseStatements.Count -ne 1) {
        throw "Base schema target rewrite requires exactly one canonical CREATE DATABASE and one canonical USE statement."
    }

    if ($Database -eq 'wxfbsir') {
        return $sourceBytes
    }
    $targetCreate = "CREATE DATABASE IF NOT EXISTS ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    $targetUse = "USE ``$Database``;"
    $rewrittenText = $sourceText.Replace($sourceCreate, $targetCreate).Replace($sourceUse, $targetUse)
    if (@([regex]::Matches($rewrittenText, [regex]::Escape($targetCreate))).Count -ne 1 -or
        @([regex]::Matches($rewrittenText, [regex]::Escape($targetUse))).Count -ne 1 -or
        $rewrittenText.Contains($sourceCreate) -or $rewrittenText.Contains($sourceUse)) {
        throw "Base schema target rewrite did not replace exactly the two reviewed database statements."
    }
    return $strictUtf8.GetBytes($rewrittenText)
}

function Assert-IndependentBoardControlPlaneCurrentState {
    $stateResponse = Invoke-MySqlText -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_product_plan','fbs_product_entitlement','fbs_usage_budget','fbs_usage_operation','fbs_entitlement_receipt')),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_product_plan','fbs_product_entitlement','fbs_usage_budget','fbs_usage_operation','fbs_entitlement_receipt')
     AND engine = 'InnoDB'),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND ((table_name = 'fbs_product_plan' AND column_name IN
           ('id','product_code','plan_code','plan_name','vip','connector_required','daily_meeting_limit','agenda_limit','seat_limit','secretary_enabled','status','version','created_at','updated_at'))
       OR (table_name = 'fbs_product_entitlement' AND column_name IN
           ('id','enterprise_id','member_id','user_id','product_code','plan_code','status','connector_binding_id','connector_verified_at','valid_from','valid_until','version','created_at','updated_at'))
       OR (table_name = 'fbs_usage_budget' AND column_name IN
           ('id','enterprise_id','member_id','product_code','metric_code','bucket_date','daily_limit','reserved_count','used_count','version','created_at','updated_at'))
       OR (table_name = 'fbs_usage_operation' AND column_name IN
           ('id','operation_id','request_digest','enterprise_id','member_id','user_id','product_code','metric_code','bucket_date','units','status','effective_plan_code','agenda_count','seat_count','remaining_count','created_at','updated_at','completed_at'))
       OR (table_name = 'fbs_entitlement_receipt' AND column_name IN
           ('id','receipt_id','enterprise_id','actor_user_id','target_member_id','action','payload_digest','evidence_level','created_at')))),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_product_plan','fbs_product_entitlement','fbs_usage_budget','fbs_usage_operation','fbs_entitlement_receipt')),
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND non_unique = 0
     AND ((table_name = 'fbs_product_plan' AND index_name = 'uk_product_plan_code'
           AND ((seq_in_index = 1 AND column_name = 'product_code') OR (seq_in_index = 2 AND column_name = 'plan_code')))
       OR (table_name = 'fbs_product_entitlement' AND index_name = 'uk_product_entitlement_scope'
           AND ((seq_in_index = 1 AND column_name = 'enterprise_id') OR (seq_in_index = 2 AND column_name = 'member_id') OR (seq_in_index = 3 AND column_name = 'product_code')))
       OR (table_name = 'fbs_usage_budget' AND index_name = 'uk_usage_budget_scope_date'
           AND ((seq_in_index = 1 AND column_name = 'enterprise_id') OR (seq_in_index = 2 AND column_name = 'member_id') OR (seq_in_index = 3 AND column_name = 'product_code') OR (seq_in_index = 4 AND column_name = 'metric_code') OR (seq_in_index = 5 AND column_name = 'bucket_date')))
       OR (table_name = 'fbs_usage_operation' AND index_name = 'uk_usage_operation_enterprise_operation'
           AND ((seq_in_index = 1 AND column_name = 'enterprise_id') OR (seq_in_index = 2 AND column_name = 'operation_id')))
       OR (table_name = 'fbs_entitlement_receipt' AND index_name = 'uk_entitlement_receipt_id'
           AND seq_in_index = 1 AND column_name = 'receipt_id'))),
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_product_plan','fbs_product_entitlement','fbs_usage_budget','fbs_usage_operation','fbs_entitlement_receipt')
     AND non_unique = 0 AND index_name <> 'PRIMARY'),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND ((table_name = 'fbs_usage_operation' AND column_name = 'request_digest')
       OR (table_name = 'fbs_entitlement_receipt' AND column_name = 'payload_digest'))
     AND column_type = 'char(64)' AND is_nullable = 'NO'
     AND character_set_name = 'ascii' AND collation_name = 'ascii_bin'),
  (SELECT COUNT(*) FROM fbs_product_plan
   WHERE product_code = 'FBSIR_INDEPENDENT_BOARD'
     AND ((plan_code = 'BOARD_FREE' AND vip = 0 AND connector_required = 0
           AND daily_meeting_limit = 1 AND agenda_limit = 5 AND seat_limit = 3
           AND secretary_enabled = 0 AND status = 'ACTIVE')
       OR (plan_code = 'BOARD_VIP' AND vip = 1 AND connector_required = 1
           AND daily_meeting_limit = 5 AND agenda_limit = 30 AND seat_limit IS NULL
           AND secretary_enabled = 1 AND status = 'ACTIVE'))),
  (SELECT COUNT(*) FROM fbs_product_plan WHERE product_code = 'FBSIR_INDEPENDENT_BOARD'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260720_independent_board_control_plane_v1'
     AND description = 'Independent Board generic product plan, entitlement, budget, operation and receipt control plane'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260720_independent_board_control_plane_v1')
);
"@
    $stateParts = @($stateResponse.Split('|'))
    $expectedState = @(5, 5, 67, 67, 13, 13, 2, 2, 2, 1, 1)
    if ($stateParts.Count -ne $expectedState.Count) {
        throw "Independent Board current-read verifier returned an invalid field count: '$stateResponse'."
    }
    for ($index = 0; $index -lt $expectedState.Count; $index++) {
        if ($stateParts[$index] -notmatch '^\d+$' -or [int]$stateParts[$index] -ne $expectedState[$index]) {
            throw "Independent Board current-read schema drift detected at field $($index + 1): expected $($expectedState[$index]), found '$($stateParts[$index])'."
        }
    }
    Write-Host "PASS Independent Board current-read audit (5 tables, 67 columns, 13 unique-index columns, 2 digest columns, 2 plan seeds, 1 internal receipt)."
}

function New-MySqlSession {
    # The mysql client buffers stdout by default when redirected. The persistent
    # advisory-lock session relies on response markers, so force a flush after
    # every statement or the caller can deadlock waiting for a marker.
    $arguments = "--login-path=$LoginPath --default-character-set=utf8mb4 --batch --skip-column-names --unbuffered --database=$Database"
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
        throw "Unable to start persistent mysql session."
    }
    return $process
}

function Invoke-MySqlSessionText {
    param(
        [Parameter(Mandatory = $true)][System.Diagnostics.Process]$Session,
        [Parameter(Mandatory = $true)][string]$Sql
    )

    if ($Session.HasExited) {
        $stderr = $Session.StandardError.ReadToEnd()
        throw "Persistent mysql session exited before command: $stderr"
    }
    $marker = "__U3W_MYSQL_EOF_$([guid]::NewGuid().ToString('N'))__"
    $Session.StandardInput.WriteLine($Sql)
    $Session.StandardInput.WriteLine("SELECT '$marker';")
    $Session.StandardInput.Flush()

    $lines = [System.Collections.Generic.List[string]]::new()
    while ($true) {
        $line = $Session.StandardOutput.ReadLine()
        if ($null -eq $line) {
            $stderr = $Session.StandardError.ReadToEnd()
            throw "Persistent mysql session ended before its response marker: $stderr"
        }
        if ($line -eq $marker) {
            break
        }
        $lines.Add($line)
    }
    return ($lines -join "`n").Trim()
}

function Stop-MySqlSession {
    param([System.Diagnostics.Process]$Session)
    if ($null -eq $Session) {
        return
    }
    try {
        if (-not $Session.HasExited) {
            $Session.StandardInput.Close()
            if (-not $Session.WaitForExit(5000)) {
                $Session.Kill()
                $Session.WaitForExit()
            }
        }
    }
    finally {
        $Session.Dispose()
    }
}

if ($CurrentReadOnly) {
    Assert-IndependentBoardControlPlaneCurrentState
    Write-Host "Independent Board current-read verification complete for '$Database'. No database write was requested."
    return
}

$databaseBootstrap = "CREATE DATABASE IF NOT EXISTS $Database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
Invoke-MySqlText -Sql $databaseBootstrap -WithoutDatabase | Out-Null

$manifestLockSession = $null
$manifestLockAcquired = $false
try {
    $manifestLockSession = New-MySqlSession
    $lockResponseText = Invoke-MySqlSessionText -Session $manifestLockSession -Sql @"
SET @u3w_manifest_lock_name = SHA2(CONCAT(DATABASE(), ':public-database-manifest:v1'), 256);
SELECT GET_LOCK(@u3w_manifest_lock_name, 30);
SELECT CONCAT_WS('|', @u3w_manifest_lock_name, CHAR_LENGTH(@u3w_manifest_lock_name), IS_USED_LOCK(@u3w_manifest_lock_name), CONNECTION_ID());
"@
    $lockResponse = @($lockResponseText -split "`n")
    if ($lockResponse.Count -ne 2 -or $lockResponse[0] -ne "1") {
        throw "Could not acquire the public database manifest advisory lock."
    }
    $manifestLockAcquired = $true
    $lockProof = $lockResponse[1].Split('|')
    if ($lockProof.Count -ne 4 -or $lockProof[0] -notmatch '^[0-9a-f]{64}$' -or $lockProof[1] -ne "64" -or $lockProof[2] -ne $lockProof[3]) {
        throw "Database manifest advisory lock owner verification failed: '$($lockResponse[1])'."
    }
    $migrationBootstrap = @"
CREATE TABLE IF NOT EXISTS $Database.u3w_schema_migration (
    version VARCHAR(96) NOT NULL,
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"@
    Invoke-MySqlText -Sql $migrationBootstrap | Out-Null

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

        try {
            Invoke-MySqlText -Sql "INSERT INTO u3w_schema_migration(version, description) VALUES ('$($step.Version)', 'RUNNING:$($step.Description)');" | Out-Null
            Write-Host "APPLY $($step.Version): $($step.File.Name)"
            if ($step.Version -eq 'public_init_001') {
                Invoke-MySqlBytes -Bytes (Get-BaseSchemaBytesForTargetDatabase -File $step.File) | Out-Null
            }
            else {
                Invoke-MySqlFile -File $step.File
            }
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

    Assert-IndependentBoardControlPlaneCurrentState

    $appliedCount = [int](Invoke-MySqlText -Sql "SELECT COUNT(*) FROM u3w_schema_migration WHERE version LIKE 'public_init_%' AND description LIKE 'APPLIED:%';")
    if ($appliedCount -ne $steps.Count) {
        throw "Expected $($steps.Count) applied public initialization receipts; found $appliedCount."
    }

    $verification = [int](Invoke-MySqlText -Sql @"
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema='$Database'
  AND table_name IN ('cv_storage','fbs_api_key','fbs_bot_binding','fbs_smartbot_input_artifact','fbs_delivery_outbox','wc_webhook_delivery',
                     'fbs_truth_spine_receipt_batch','fbs_product_plan','fbs_product_entitlement','fbs_usage_budget','fbs_usage_operation','fbs_entitlement_receipt');
"@)
    if ($verification -ne 12) {
        throw "Database verification failed: expected twelve representative current tables; found $verification."
    }

    $hostTypeColumn = [int](Invoke-MySqlText -Sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$Database' AND table_name='ws_host_whitelist' AND column_name='host_type';")
    if ($hostTypeColumn -ne 1) {
        throw "Database verification failed: ws_host_whitelist.host_type is missing."
    }

    $quartzTableCount = [int](Invoke-MySqlText -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name IN ('QRTZ_JOB_DETAILS','QRTZ_TRIGGERS','QRTZ_LOCKS');")
    if ($quartzTableCount -ne 3) {
        throw "Database verification failed: representative Quartz scheduler tables are missing."
    }

    Write-Host "Database initialization complete: $($steps.Count)/$($steps.Count) steps applied to '$Database'."
}
finally {
    try {
        if ($manifestLockAcquired -and $null -ne $manifestLockSession -and -not $manifestLockSession.HasExited) {
            $ownerResponse = Invoke-MySqlSessionText -Session $manifestLockSession -Sql "SELECT CONCAT_WS('|', IS_USED_LOCK(@u3w_manifest_lock_name), CONNECTION_ID());"
            $ownerProof = $ownerResponse.Split('|')
            if ($ownerProof.Count -ne 2 -or $ownerProof[0] -ne $ownerProof[1]) {
                throw "Refusing advisory lock release because the current session is not its owner: '$ownerResponse'."
            }
            $releaseResponseText = Invoke-MySqlSessionText -Session $manifestLockSession -Sql @"
SELECT RELEASE_LOCK(@u3w_manifest_lock_name);
SELECT COALESCE(CAST(IS_USED_LOCK(@u3w_manifest_lock_name) AS CHAR), 'NULL');
"@
            $releaseResponse = @($releaseResponseText -split "`n")
            if ($releaseResponse.Count -ne 2 -or $releaseResponse[0] -ne "1" -or $releaseResponse[1] -ne "NULL") {
                throw "Database manifest advisory lock release verification failed: '$($releaseResponse -join '|')'."
            }
        }
    }
    finally {
        Stop-MySqlSession -Session $manifestLockSession
    }
}
