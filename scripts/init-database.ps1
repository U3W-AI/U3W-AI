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
$DeclarativeManifestPath = Join-Path $SqlRoot "init-manifest.json"

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
    New-Step "public_init_029" "Independent Board me portal menu" (Resolve-SqlFile "update_20260720_independent_board_me_menu.sql")
    New-Step "public_init_030" "Independent Board administration menu" (Resolve-SqlFile "update_20260720_independent_board_admin_menu.sql")
    New-Step "public_init_031" "Independent Board entitlement lifecycle administration menu" (Resolve-SqlFile "update_20260720_independent_board_entitlement_lifecycle_menu.sql")
    New-Step "public_init_032" "Independent Board authoritative Connector binding" (Resolve-SqlFile "update_20260721_independent_board_connector_binding.sql")
    New-Step "public_init_033" "Independent Board OAuth authorization foundation" (Resolve-SqlFile "update_20260721_independent_board_oauth_foundation.sql")
    New-Step "public_init_034" "Independent Board OAuth TOKEN_FAMILY_CREATED receipt provenance" (Resolve-SqlFile "update_20260721_independent_board_oauth_receipt_provenance.sql")
    New-Step "public_init_035" "Independent Board OAuth consent-intent lineage" (Resolve-SqlFile "update_20260721_independent_board_oauth_consent_intent_lineage.sql")
    New-Step "public_init_036" "Independent Board OAuth refresh security receipt v2" (Resolve-SqlFile "update_20260721_independent_board_oauth_refresh_security.sql")
)

if (-not (Test-Path -LiteralPath $DeclarativeManifestPath -PathType Leaf)) {
    throw "The declarative database manifest is missing: $DeclarativeManifestPath"
}
try {
    $declarativeManifest = Get-Content -LiteralPath $DeclarativeManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
catch {
    throw "The declarative database manifest is not valid JSON: $($_.Exception.Message)"
}
if ([string]$declarativeManifest.schema -ne 'fbsir.public-database-init-manifest/v1') {
    throw "The declarative database manifest schema is unsupported: '$($declarativeManifest.schema)'."
}
$declaredSteps = @($declarativeManifest.steps)
if ($declaredSteps.Count -ne $steps.Count) {
    throw "Declarative manifest step count mismatch: declared=$($declaredSteps.Count), executable=$($steps.Count)."
}
for ($index = 0; $index -lt $steps.Count; $index++) {
    $declared = $declaredSteps[$index]
    $executable = $steps[$index]
    if ([string]$declared.version -ne $executable.Version -or
        [string]$declared.description -ne $executable.Description -or
        [string]$declared.file -ne $executable.File.Name) {
        throw "Declarative manifest drift at position $($index + 1): expected '$($executable.Version)|$($executable.Description)|$($executable.File.Name)'."
    }
    if ($executable.Version -in @('public_init_035', 'public_init_036')) {
        $declaredSha256 = [string]$declared.sha256
        $actualSha256 = (Get-FileHash -LiteralPath $executable.File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($declaredSha256 -notmatch '^[0-9a-f]{64}$' -or
            -not [string]::Equals($declaredSha256, $actualSha256, [StringComparison]::Ordinal)) {
            throw "$($executable.Version) byte contract drifted: expected SHA-256 '$declaredSha256', found '$actualSha256'."
        }
    }
}

$manualMigrations = @()
foreach ($declared in @($declarativeManifest.manualMigrations)) {
    $id = [string]$declared.id
    $description = [string]$declared.description
    $fileName = [string]$declared.file
    $execution = [string]$declared.execution
    $optInSessionVariable = [string]$declared.optInSessionVariable
    $requiredValue = $declared.requiredValue
    $sha256 = [string]$declared.sha256
    $hasDefaultApplied = $declared.PSObject.Properties.Name -contains 'defaultApplied'
    if ($id -notmatch '^[a-z0-9_]{1,128}$' -or
        [string]::IsNullOrWhiteSpace($description) -or
        [System.IO.Path]::GetFileName($fileName) -ne $fileName -or
        $fileName -notlike 'update_*.sql' -or
        $execution -cne 'manual_opt_in' -or
        -not $hasDefaultApplied -or
        [bool]$declared.defaultApplied -or
        $optInSessionVariable -notmatch '^@[A-Za-z0-9_]+$' -or
        $requiredValue -isnot [int] -or
        [int]$requiredValue -ne 1 -or
        $sha256 -notmatch '^[0-9a-f]{64}$') {
        throw "Manual migration contract is invalid: '$id'."
    }
    $filePath = Join-Path $SqlRoot $fileName
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
        throw "Manual migration SQL is missing: $fileName"
    }
    $file = Get-Item -LiteralPath $filePath
    $actualSha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not [string]::Equals($sha256, $actualSha256, [StringComparison]::Ordinal)) {
        throw "Manual migration byte contract drifted for '$id': expected SHA-256 '$sha256', found '$actualSha256'."
    }
    $manualMigrations += [pscustomobject]@{
        Id = $id
        Description = $description
        File = $file
        Execution = $execution
        DefaultApplied = $false
        OptInSessionVariable = $optInSessionVariable
        RequiredValue = [int]$requiredValue
        Sha256 = $sha256
    }
}
$duplicateManualIds = @($manualMigrations | Group-Object Id | Where-Object Count -ne 1)
$duplicateManualFiles = @($manualMigrations | Group-Object { $_.File.FullName.ToLowerInvariant() } | Where-Object Count -ne 1)
if ($duplicateManualIds.Count -gt 0 -or $duplicateManualFiles.Count -gt 0) {
    throw "Manual migration catalog contains duplicate identities or files."
}

for ($index = 0; $index -lt $steps.Count; $index++) {
    $expectedVersion = "public_init_{0:D3}" -f ($index + 1)
    if ($steps[$index].Version -ne $expectedVersion) {
        throw "Database manifest order is invalid at position $($index + 1): expected '$expectedVersion', found '$($steps[$index].Version)'."
    }
}

$strictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)
foreach ($step in @($steps) + @($manualMigrations)) {
    try {
        $null = $strictUtf8.GetString([System.IO.File]::ReadAllBytes($step.File.FullName))
    }
    catch {
        throw "Managed SQL must be strict UTF-8: $($step.File.Name): $($_.Exception.Message)"
    }
}

$duplicateVersions = @($steps | Group-Object Version | Where-Object Count -ne 1)
if ($duplicateVersions.Count -gt 0) {
    throw "Database manifest contains duplicate versions: $($duplicateVersions.Name -join ', ')."
}

$managedUpdateFiles = @(Get-ChildItem -LiteralPath $SqlRoot -Filter "update_*.sql" -File)
$manifestUpdateSteps = @($steps | Where-Object { $_.File.Name -like "update_*.sql" })
$managedCatalogEntries = @($manifestUpdateSteps) + @($manualMigrations)
$duplicateManagedFiles = @($managedCatalogEntries | Group-Object { $_.File.FullName.ToLowerInvariant() } | Where-Object Count -ne 1)
$manifestManagedPaths = @($managedCatalogEntries | ForEach-Object { $_.File.FullName.ToLowerInvariant() })
$missingManagedFiles = @($managedUpdateFiles | Where-Object { $manifestManagedPaths -notcontains $_.FullName.ToLowerInvariant() })
$managedPaths = @($managedUpdateFiles | ForEach-Object { $_.FullName.ToLowerInvariant() })
$unexpectedManagedSteps = @($managedCatalogEntries | Where-Object { $managedPaths -notcontains $_.File.FullName.ToLowerInvariant() })
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
        manualMigrationCount = $manualMigrations.Count
        managedUpdateSqlCount = $managedUpdateFiles.Count
        coverageOk = $true
        dryRun = [bool]$DryRun
        databaseConnectionOpened = $false
        database = $Database
        manifestFile = "sql/init-manifest.json"
        manifestSchema = [string]$declarativeManifest.schema
        steps = @($steps | ForEach-Object {
            [pscustomobject]@{
                version = $_.Version
                description = $_.Description
                file = $_.File.Name
            }
        })
        manualMigrations = @($manualMigrations | ForEach-Object {
            [pscustomobject]@{
                id = $_.Id
                description = $_.Description
                file = $_.File.Name
                execution = $_.Execution
                defaultApplied = $_.DefaultApplied
                optInSessionVariable = $_.OptInSessionVariable
                requiredValue = $_.RequiredValue
                sha256 = $_.Sha256
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

function Invoke-MySqlRawOutputBytes {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Bytes,
        [switch]$WithoutDatabase
    )

    $arguments = "--login-path=$LoginPath --default-character-set=utf8mb4 --batch --raw --skip-column-names"
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
        throw "Unable to start mysql client for raw output."
    }

    $stdoutBuffer = [System.IO.MemoryStream]::new()
    try {
        $stdoutTask = $process.StandardOutput.BaseStream.CopyToAsync($stdoutBuffer)
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.BaseStream.Write($Bytes, 0, $Bytes.Length)
        $process.StandardInput.BaseStream.Flush()
        $process.StandardInput.Close()
        $process.WaitForExit()
        [void]$stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            throw "mysql exited with code $($process.ExitCode): $stderr"
        }
        if ($stderr.Trim()) {
            Write-Warning $stderr.Trim()
        }
        return ,$stdoutBuffer.ToArray()
    }
    finally {
        $stdoutBuffer.Dispose()
        $process.Dispose()
    }
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

function Assert-IndependentBoardMeMenuCurrentState {
    $stateResponse = Invoke-MySqlText -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM sys_menu
   WHERE HEX(CAST(menu_name AS BINARY)) = 'E78BACE891A3E4BC9A'
     AND parent_id = 0
     AND order_num = 0
     AND path = 'independent-board'
     AND component = 'business/independentBoard/me/index'
     AND query IS NULL
     AND route_name = 'IndependentBoardMe'
     AND is_frame = 1
     AND is_cache = 0
     AND menu_type = 'C'
     AND visible = '0'
     AND status = '0'
     AND perms = 'my:independent-board:view'
     AND icon = 'peoples'),
  (SELECT COUNT(*) FROM sys_menu
   WHERE path = 'independent-board'
      OR component = 'business/independentBoard/me/index'
      OR route_name = 'IndependentBoardMe'
      OR perms = 'my:independent-board:view'),
  (SELECT COUNT(*) FROM sys_role
   WHERE role_id = 10 AND role_key = 'user' AND status = '0' AND del_flag = '0'),
  (SELECT COUNT(*) FROM sys_role
   WHERE role_key = 'user' AND status = '0' AND del_flag = '0'),
  (SELECT COUNT(*) FROM sys_role_menu AS role_menu
   INNER JOIN sys_role AS role_row ON role_row.role_id = role_menu.role_id
   INNER JOIN sys_menu AS menu_row ON menu_row.menu_id = role_menu.menu_id
   WHERE role_row.role_key = 'user'
     AND role_row.role_id = 10
     AND role_row.status = '0'
     AND role_row.del_flag = '0'
     AND menu_row.perms = 'my:independent-board:view'
     AND menu_row.component = 'business/independentBoard/me/index'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260720_independent_board_me_menu_v1'
     AND description = 'Independent Board top-level me portal menu and ordinary-user role binding'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260720_independent_board_me_menu_v1')
);
"@
    $stateParts = @($stateResponse.Split('|'))
    $expectedState = @(1, 1, 1, 1, 1, 1, 1)
    if ($stateParts.Count -ne $expectedState.Count) {
        throw "Independent Board me menu current-read verifier returned an invalid field count: '$stateResponse'."
    }
    for ($index = 0; $index -lt $expectedState.Count; $index++) {
        if ($stateParts[$index] -notmatch '^\d+$' -or [int]$stateParts[$index] -ne $expectedState[$index]) {
            throw "Independent Board me menu current-read drift detected at field $($index + 1): expected $($expectedState[$index]), found '$($stateParts[$index])'."
        }
    }
    Write-Host "PASS Independent Board me menu current-read audit (1 page, 1 ordinary-user binding, 1 internal receipt)."
}

function Assert-IndependentBoardAdminMenuCurrentState {
    $stateResponse = Invoke-MySqlText -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM sys_menu
   WHERE HEX(CAST(menu_name AS BINARY)) = 'E78BACE891A3E4BC9AE7AEA1E79086'
     AND parent_id = 0
     AND order_num = 5
     AND path = 'independent-board-admin'
     AND component IS NULL
     AND query IS NULL
     AND route_name = 'IndependentBoardAdmin'
     AND is_frame = 1
     AND is_cache = 0
     AND menu_type = 'M'
     AND visible = '0'
     AND status = '0'
     AND perms = ''
     AND icon = 'peoples'),
  (SELECT COUNT(*) FROM sys_menu AS child
   INNER JOIN sys_menu AS root ON root.menu_id = child.parent_id
   WHERE root.path = 'independent-board-admin'
     AND root.route_name = 'IndependentBoardAdmin'
     AND HEX(CAST(child.menu_name AS BINARY)) = 'E69D83E79B8AE6B2BBE79086'
     AND child.order_num = 1
     AND child.path = 'entitlements'
     AND child.component = 'business/independentBoard/admin/entitlement/index'
     AND child.query IS NULL
     AND child.route_name = 'IndependentBoardEntitlementGovernance'
     AND child.is_frame = 1
     AND child.is_cache = 0
     AND child.menu_type = 'C'
     AND child.visible = '0'
     AND child.status = '0'
     AND child.perms = 'board:entitlement:query'
     AND child.icon = 'peoples'),
  (SELECT COUNT(*) FROM sys_menu AS child
   INNER JOIN sys_menu AS root ON root.menu_id = child.parent_id
   WHERE root.path = 'independent-board-admin'
     AND root.route_name = 'IndependentBoardAdmin'
     AND HEX(CAST(child.menu_name AS BINARY)) = 'E4BC9AE8AEAEE5AEA1E8AEA1'
     AND child.order_num = 2
     AND child.path = 'meeting-audit'
     AND child.component = 'business/independentBoard/admin/meetingAudit/index'
     AND child.query IS NULL
     AND child.route_name = 'IndependentBoardMeetingAudit'
     AND child.is_frame = 1
     AND child.is_cache = 0
     AND child.menu_type = 'C'
     AND child.visible = '0'
     AND child.status = '0'
     AND child.perms = 'board:operation:audit'
     AND child.icon = 'form'),
  (SELECT COUNT(*) FROM sys_menu AS grant_row
   INNER JOIN sys_menu AS page_row ON page_row.menu_id = grant_row.parent_id
   WHERE page_row.component = 'business/independentBoard/admin/entitlement/index'
     AND page_row.route_name = 'IndependentBoardEntitlementGovernance'
     AND HEX(CAST(grant_row.menu_name AS BINARY)) = 'E69D83E79B8AE68E88E4BA88'
     AND grant_row.order_num = 1
     AND grant_row.path = ''
     AND grant_row.component IS NULL
     AND grant_row.query IS NULL
     AND grant_row.route_name = ''
     AND grant_row.is_frame = 1
     AND grant_row.is_cache = 0
     AND grant_row.menu_type = 'F'
     AND grant_row.visible = '0'
     AND grant_row.status = '0'
     AND grant_row.perms = 'board:entitlement:grant'
     AND grant_row.icon = '#'),
  (SELECT COUNT(*) FROM sys_menu
   WHERE HEX(CAST(menu_name AS BINARY)) = 'E78BACE891A3E4BC9A'
     AND parent_id = 0
     AND path = 'independent-board'
     AND component = 'business/independentBoard/me/index'
     AND route_name = 'IndependentBoardMe'
     AND menu_type = 'C'
     AND perms = 'my:independent-board:view'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260720_independent_board_admin_menu_v1'
     AND description = 'Independent Board administration directory, entitlement governance and meeting audit menus'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260720_independent_board_admin_menu_v1')
);
"@
    $stateParts = @($stateResponse.Split('|'))
    # The four W3a identities are asserted individually above. Do not compare a
    # global Independent Board menu count: W3b and later lifecycle extensions
    # are valid siblings and must not invalidate this prerequisite audit.
    $expectedState = @(1, 1, 1, 1, 1, 1, 1)
    if ($stateParts.Count -ne $expectedState.Count) {
        throw "Independent Board admin menu current-read verifier returned an invalid field count: '$stateResponse'."
    }
    for ($index = 0; $index -lt $expectedState.Count; $index++) {
        if ($stateParts[$index] -notmatch '^\d+$' -or [int]$stateParts[$index] -ne $expectedState[$index]) {
            throw "Independent Board admin menu current-read drift detected at field $($index + 1): expected $($expectedState[$index]), found '$($stateParts[$index])'."
        }
    }
    Write-Host "PASS Independent Board admin menu current-read audit (1 directory, 2 pages, 1 action permission, 1 internal receipt; role bindings are externally governable)."
}

function Assert-IndependentBoardEntitlementLifecycleMenuCurrentState {
    $stateResponse = Invoke-MySqlText -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM sys_menu
   WHERE HEX(CAST(menu_name AS BINARY)) = 'E78BACE891A3E4BC9AE7AEA1E79086'
     AND parent_id = 0
     AND order_num = 5
     AND path = 'independent-board-admin'
     AND component IS NULL
     AND query IS NULL
     AND route_name = 'IndependentBoardAdmin'
     AND is_frame = 1
     AND is_cache = 0
     AND menu_type = 'M'
     AND visible = '0'
     AND status = '0'
     AND perms = ''
     AND icon = 'peoples'),
  (SELECT COUNT(*) FROM sys_menu AS child
   INNER JOIN sys_menu AS root ON root.menu_id = child.parent_id
   WHERE root.path = 'independent-board-admin'
     AND root.route_name = 'IndependentBoardAdmin'
     AND HEX(CAST(child.menu_name AS BINARY)) = 'E69D83E79B8AE6B2BBE79086'
     AND child.order_num = 1
     AND child.path = 'entitlements'
     AND child.component = 'business/independentBoard/admin/entitlement/index'
     AND child.query IS NULL
     AND child.route_name = 'IndependentBoardEntitlementGovernance'
     AND child.is_frame = 1
     AND child.is_cache = 0
     AND child.menu_type = 'C'
     AND child.visible = '0'
     AND child.status = '0'
     AND child.perms = 'board:entitlement:query'
     AND child.icon = 'peoples'),
  (SELECT COUNT(*) FROM sys_menu AS revoke_row
   INNER JOIN sys_menu AS page_row ON page_row.menu_id = revoke_row.parent_id
   WHERE page_row.component = 'business/independentBoard/admin/entitlement/index'
     AND page_row.route_name = 'IndependentBoardEntitlementGovernance'
     AND HEX(CAST(revoke_row.menu_name AS BINARY)) = 'E69D83E79B8AE692A4E99480'
     AND revoke_row.order_num = 2
     AND revoke_row.path = ''
     AND revoke_row.component IS NULL
     AND revoke_row.query IS NULL
     AND revoke_row.route_name = ''
     AND revoke_row.is_frame = 1
     AND revoke_row.is_cache = 0
     AND revoke_row.menu_type = 'F'
     AND revoke_row.visible = '0'
     AND revoke_row.status = '0'
     AND revoke_row.perms = 'board:entitlement:revoke'
     AND revoke_row.icon = '#'),
  (SELECT COUNT(*) FROM sys_menu AS receipt_row
   INNER JOIN sys_menu AS root ON root.menu_id = receipt_row.parent_id
   WHERE root.path = 'independent-board-admin'
     AND root.route_name = 'IndependentBoardAdmin'
     AND HEX(CAST(receipt_row.menu_name AS BINARY)) = 'E69D83E79B8AE59B9EE689A7'
     AND receipt_row.order_num = 3
     AND receipt_row.path = 'entitlement-receipts'
     AND receipt_row.component = 'business/independentBoard/admin/entitlementReceipt/index'
     AND receipt_row.query IS NULL
     AND receipt_row.route_name = 'IndependentBoardEntitlementReceipts'
     AND receipt_row.is_frame = 1
     AND receipt_row.is_cache = 0
     AND receipt_row.menu_type = 'C'
     AND receipt_row.visible = '0'
     AND receipt_row.status = '0'
     AND receipt_row.perms = 'board:entitlement:audit'
     AND receipt_row.icon = 'form'),
  (SELECT COUNT(*) FROM sys_menu
   WHERE perms = 'board:entitlement:revoke'
      OR path = 'entitlement-receipts'
      OR component = 'business/independentBoard/admin/entitlementReceipt/index'
      OR route_name = 'IndependentBoardEntitlementReceipts'
      OR perms = 'board:entitlement:audit'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260720_independent_board_admin_menu_v1'
     AND description = 'Independent Board administration directory, entitlement governance and meeting audit menus'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260720_independent_board_entitlement_lifecycle_menu_v1'
     AND description = 'Independent Board controlled entitlement revoke permission and immutable receipt audit menu'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260720_independent_board_entitlement_lifecycle_menu_v1')
);
"@
    $stateParts = @($stateResponse.Split('|'))
    $expectedState = @(1, 1, 1, 1, 2, 1, 1, 1)
    if ($stateParts.Count -ne $expectedState.Count) {
        throw "Independent Board entitlement lifecycle menu current-read verifier returned an invalid field count: '$stateResponse'."
    }
    for ($index = 0; $index -lt $expectedState.Count; $index++) {
        if ($stateParts[$index] -notmatch '^\d+$' -or [int]$stateParts[$index] -ne $expectedState[$index]) {
            throw "Independent Board entitlement lifecycle menu current-read drift detected at field $($index + 1): expected $($expectedState[$index]), found '$($stateParts[$index])'."
        }
    }
    Write-Host "PASS Independent Board entitlement lifecycle menu current-read audit (1 revoke permission, 1 receipt page, W3a prerequisite and 1 internal receipt; role bindings are externally governable)."
}

function Assert-IndependentBoardConnectorBindingCurrentState {
    $refreshSecurityState = Invoke-MySqlText -Sql "SELECT COALESCE((SELECT description FROM u3w_schema_migration WHERE version='20260721_independent_board_oauth_refresh_security_v1'), '');"
    $connectorLockOrderExpected = 0
    if ($refreshSecurityState) {
        if (-not [string]::Equals(
                $refreshSecurityState,
                'APPLIED:Independent Board OAuth refresh security receipt v2',
                [StringComparison]::Ordinal)) {
            throw "Independent Board Connector binding refresh-security successor is not in its exact completed state: '$refreshSecurityState'."
        }
        $connectorLockOrderExpected = 1
    }
    $stateResponse = Invoke-MySqlText -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')
     AND table_type = 'BASE TABLE'),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')
     AND engine = 'InnoDB'),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')
     AND table_collation = 'utf8mb4_unicode_ci'),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND ((table_name = 'fbs_connector_binding' AND column_name IN
           ('id','binding_id','enterprise_id','member_id','user_id','product_code','source_code','connector_code','issuer_uri','resource_uri','client_id','principal_subject_digest','status','verification_method','evidence_digest','verified_at','last_seen_at','valid_until','revoked_at','version','created_at','updated_at'))
       OR (table_name = 'fbs_connector_binding_scope' AND column_name IN
           ('binding_id','scope_code','created_at'))
       OR (table_name = 'fbs_connector_binding_receipt' AND column_name IN
           ('id','receipt_id','binding_id','enterprise_id','member_id','user_id','actor_user_id','action','payload_digest','evidence_level','created_at')))),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND ((table_name = 'fbs_connector_binding' AND column_name IN ('principal_subject_digest','evidence_digest'))
       OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'payload_digest'))
     AND column_type = 'char(64)' AND is_nullable = 'NO'
     AND character_set_name = 'ascii' AND collation_name = 'ascii_bin'),
  (SELECT COUNT(*) FROM (
       SELECT table_name, index_name
       FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')
       GROUP BY table_name, index_name
   ) target_indexes),
  (SELECT COUNT(*) FROM (
       SELECT table_name, index_name, non_unique, index_type,
              GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS column_signature
       FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')
       GROUP BY table_name, index_name, non_unique, index_type
   ) indexes_by_name
   WHERE (table_name = 'fbs_connector_binding' AND index_name = 'PRIMARY' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('id' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND index_name = 'uk_connector_binding_id' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND index_name = 'uk_connector_binding_receipt_scope' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id,enterprise_id,member_id,user_id' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND index_name = 'uk_connector_binding_scope' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('enterprise_id,member_id,product_code,source_code,connector_code' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND index_name = 'idx_connector_binding_user' AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('enterprise_id,user_id,product_code,status' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND index_name = 'idx_connector_binding_state' AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('status,valid_until' AS BINARY))
      OR (table_name = 'fbs_connector_binding_scope' AND index_name = 'PRIMARY' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id,scope_code' AS BINARY))
      OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'PRIMARY' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('id' AS BINARY))
      OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'uk_connector_binding_receipt_id' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('receipt_id' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'idx_connector_binding_receipt_binding' AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id,enterprise_id,member_id,user_id,created_at' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'idx_connector_binding_receipt_scope' AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('enterprise_id,member_id,created_at' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'idx_connector_binding_receipt_lock_order' AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id,id' AS BINARY))),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE() AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'
     AND ((table_name = 'fbs_connector_binding' AND constraint_name = 'fk_connector_binding_entitlement' AND referenced_table_name = 'fbs_product_entitlement')
       OR (table_name = 'fbs_connector_binding_scope' AND constraint_name = 'fk_connector_binding_scope_binding' AND referenced_table_name = 'fbs_connector_binding')
       OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'fk_connector_binding_receipt_binding' AND referenced_table_name = 'fbs_connector_binding'))),
  (SELECT COUNT(*) FROM information_schema.key_column_usage
   WHERE constraint_schema = DATABASE() AND referenced_table_name IS NOT NULL
     AND ((table_name = 'fbs_connector_binding' AND constraint_name = 'fk_connector_binding_entitlement'
           AND ((ordinal_position = 1 AND column_name = 'enterprise_id' AND referenced_column_name = 'enterprise_id')
             OR (ordinal_position = 2 AND column_name = 'member_id' AND referenced_column_name = 'member_id')
             OR (ordinal_position = 3 AND column_name = 'product_code' AND referenced_column_name = 'product_code')))
       OR (table_name = 'fbs_connector_binding_scope' AND constraint_name = 'fk_connector_binding_scope_binding'
           AND ordinal_position = 1 AND column_name = 'binding_id' AND referenced_column_name = 'binding_id')
       OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'fk_connector_binding_receipt_binding'
           AND ((ordinal_position = 1 AND column_name = 'binding_id' AND referenced_column_name = 'binding_id')
             OR (ordinal_position = 2 AND column_name = 'enterprise_id' AND referenced_column_name = 'enterprise_id')
             OR (ordinal_position = 3 AND column_name = 'member_id' AND referenced_column_name = 'member_id')
             OR (ordinal_position = 4 AND column_name = 'user_id' AND referenced_column_name = 'user_id'))))),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
     AND ((table_name = 'fbs_connector_binding' AND constraint_name IN
           ('chk_connector_binding_identifiers','chk_connector_binding_source','chk_connector_binding_connector','chk_connector_binding_resource','chk_connector_binding_status','chk_connector_binding_verification','chk_connector_binding_principal_digest','chk_connector_binding_evidence_digest','chk_connector_binding_lifecycle','chk_connector_binding_temporal'))
       OR (table_name = 'fbs_connector_binding_scope' AND constraint_name = 'chk_connector_binding_scope_code')
       OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name IN
           ('chk_connector_binding_receipt_id','chk_connector_binding_receipt_action','chk_connector_binding_receipt_payload_digest','chk_connector_binding_receipt_evidence')))),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_connector_binding_v1'
     AND description = 'Independent Board authoritative Connector binding, scope and receipt tables'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_connector_binding_v1')
);
"@
    $stateParts = @($stateResponse.Split('|'))
    $expectedConnectorIndexCount = 11 + $connectorLockOrderExpected
    $expectedState = @(
        3, 3, 3, 3, 36, 36, 3,
        $expectedConnectorIndexCount,
        $expectedConnectorIndexCount,
        3, 3, 8, 15, 15, 1, 1
    )
    if ($stateParts.Count -ne $expectedState.Count) {
        throw "Independent Board Connector binding current-read verifier returned an invalid field count: '$stateResponse'."
    }
    for ($index = 0; $index -lt $expectedState.Count; $index++) {
        if ($stateParts[$index] -notmatch '^\d+$' -or [int]$stateParts[$index] -ne $expectedState[$index]) {
            throw "Independent Board Connector binding current-read drift detected at field $($index + 1): expected $($expectedState[$index]), found '$($stateParts[$index])'."
        }
    }

    $exactStateResponse = Invoke-MySqlText -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM (
       SELECT table_name,
              GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ',') AS column_signature
       FROM information_schema.columns
       WHERE table_schema = DATABASE()
         AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')
       GROUP BY table_name
   ) ordered_columns
   WHERE (table_name = 'fbs_connector_binding'
          AND CAST(column_signature AS BINARY) = CAST('id,binding_id,enterprise_id,member_id,user_id,product_code,source_code,connector_code,issuer_uri,resource_uri,client_id,principal_subject_digest,status,verification_method,evidence_digest,verified_at,last_seen_at,valid_until,revoked_at,version,created_at,updated_at' AS BINARY))
      OR (table_name = 'fbs_connector_binding_scope'
          AND CAST(column_signature AS BINARY) = CAST('binding_id,scope_code,created_at' AS BINARY))
      OR (table_name = 'fbs_connector_binding_receipt'
          AND CAST(column_signature AS BINARY) = CAST('id,receipt_id,binding_id,enterprise_id,member_id,user_id,actor_user_id,action,payload_digest,evidence_level,created_at' AS BINARY))),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND (
      (table_name = 'fbs_connector_binding' AND column_name = 'id'
       AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND extra = 'auto_increment')
      OR (table_name = 'fbs_connector_binding' AND column_name IN ('enterprise_id','member_id','user_id')
          AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name = 'version'
          AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND column_default = '0' AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name = 'binding_id'
          AND column_type = 'varchar(128)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name = 'product_code'
          AND column_type = 'varchar(64)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci' AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name IN ('source_code','connector_code')
          AND column_type = 'varchar(64)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name IN ('issuer_uri','resource_uri')
          AND column_type = 'varchar(512)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name = 'client_id'
          AND column_type = 'varchar(191)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name IN ('principal_subject_digest','evidence_digest')
          AND column_type = 'char(64)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name IN ('status','verification_method')
          AND column_type = 'varchar(32)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name IN ('verified_at','last_seen_at','valid_until')
          AND column_type = 'datetime(3)' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name = 'revoked_at'
          AND column_type = 'datetime(3)' AND is_nullable = 'YES' AND column_default IS NULL AND extra = '')
      OR (table_name = 'fbs_connector_binding' AND column_name IN ('created_at','updated_at')
          AND column_type = 'datetime(3)' AND is_nullable = 'NO')
      OR (table_name = 'fbs_connector_binding_scope' AND column_name = 'binding_id'
          AND column_type = 'varchar(128)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding_scope' AND column_name = 'scope_code'
          AND column_type = 'varchar(64)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding_scope' AND column_name = 'created_at'
          AND column_type = 'datetime(3)' AND is_nullable = 'NO')
      OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'id'
          AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND extra = 'auto_increment')
      OR (table_name = 'fbs_connector_binding_receipt' AND column_name IN ('receipt_id','binding_id')
          AND column_type = 'varchar(128)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding_receipt' AND column_name IN ('enterprise_id','member_id','user_id','actor_user_id')
          AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
      OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'action'
          AND column_type = 'varchar(64)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'payload_digest'
          AND column_type = 'char(64)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'evidence_level'
          AND column_type = 'varchar(32)' AND is_nullable = 'NO' AND column_default IS NULL
          AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND extra = '')
      OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'created_at'
          AND column_type = 'datetime(3)' AND is_nullable = 'NO'))),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND ((table_name = 'fbs_connector_binding' AND column_name = 'id' AND extra = 'auto_increment')
       OR (table_name = 'fbs_connector_binding_receipt' AND column_name = 'id' AND extra = 'auto_increment')
       OR (table_name = 'fbs_connector_binding' AND column_name = 'version' AND column_default = '0' AND extra = '')
       OR (table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')
           AND column_name = 'created_at'
           AND CAST(UPPER(column_default) AS BINARY) = CAST('CURRENT_TIMESTAMP(3)' AS BINARY)
           AND extra = 'DEFAULT_GENERATED')
       OR (table_name = 'fbs_connector_binding' AND column_name = 'updated_at'
           AND CAST(UPPER(column_default) AS BINARY) = CAST('CURRENT_TIMESTAMP(3)' AS BINARY)
           AND CAST(LOWER(extra) AS BINARY) = CAST('default_generated on update current_timestamp(3)' AS BINARY)))),
  (SELECT COUNT(*) FROM (
       SELECT table_name, index_name, non_unique, index_type,
              MIN(is_visible) AS visibility,
              SUM(CASE WHEN sub_part IS NULL THEN 0 ELSE 1 END) AS partial_columns,
              GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                           ORDER BY seq_in_index SEPARATOR ',') AS column_signature
       FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')
       GROUP BY table_name, index_name, non_unique, index_type
   ) indexes_by_name
   WHERE CAST(visibility AS BINARY) = CAST('YES' AS BINARY) AND partial_columns = 0 AND (
      (table_name = 'fbs_connector_binding' AND index_name = 'PRIMARY' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('id:A' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND index_name = 'uk_connector_binding_id' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id:A' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND index_name = 'uk_connector_binding_receipt_scope' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id:A,enterprise_id:A,member_id:A,user_id:A' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND index_name = 'uk_connector_binding_scope' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('enterprise_id:A,member_id:A,product_code:A,source_code:A,connector_code:A' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND index_name = 'idx_connector_binding_user' AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('enterprise_id:A,user_id:A,product_code:A,status:A' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND index_name = 'idx_connector_binding_state' AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('status:A,valid_until:A' AS BINARY))
      OR (table_name = 'fbs_connector_binding_scope' AND index_name = 'PRIMARY' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id:A,scope_code:A' AS BINARY))
      OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'PRIMARY' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('id:A' AS BINARY))
      OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'uk_connector_binding_receipt_id' AND non_unique = 0 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('receipt_id:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'idx_connector_binding_receipt_binding' AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id:A,enterprise_id:A,member_id:A,user_id:A,created_at:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'idx_connector_binding_receipt_scope' AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('enterprise_id:A,member_id:A,created_at:A' AS BINARY))
       OR (table_name = 'fbs_connector_binding_receipt' AND index_name = 'idx_connector_binding_receipt_lock_order' AND non_unique = 1 AND index_type = 'BTREE' AND CAST(column_signature AS BINARY) = CAST('binding_id:A,id:A' AS BINARY)))),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE() AND unique_constraint_schema = DATABASE()
     AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'
     AND ((table_name = 'fbs_connector_binding' AND constraint_name = 'fk_connector_binding_entitlement' AND referenced_table_name = 'fbs_product_entitlement')
       OR (table_name = 'fbs_connector_binding_scope' AND constraint_name = 'fk_connector_binding_scope_binding' AND referenced_table_name = 'fbs_connector_binding')
       OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'fk_connector_binding_receipt_binding' AND referenced_table_name = 'fbs_connector_binding'))),
  (SELECT COUNT(*) FROM information_schema.key_column_usage
   WHERE constraint_schema = DATABASE() AND referenced_table_schema = DATABASE()
     AND referenced_table_name IS NOT NULL
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK' AND enforced = 'YES'
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')),
  (SELECT COUNT(*) FROM (
       SELECT tc.table_name, tc.constraint_name,
               REPLACE(REPLACE(REPLACE(LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                   cc.check_clause, CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''), CHAR(92), '')),
                   '_utf8mb4', ''), '_utf8mb3', ''), '_ascii', '') AS normalized_clause
       FROM information_schema.table_constraints tc
       INNER JOIN information_schema.check_constraints cc
         ON cc.constraint_schema = tc.constraint_schema AND cc.constraint_name = tc.constraint_name
       WHERE tc.constraint_schema = DATABASE() AND tc.constraint_type = 'CHECK' AND tc.enforced = 'YES'
         AND tc.table_name IN ('fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt')
   ) checks_by_name
   WHERE (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_identifiers' AND CAST(normalized_clause AS BINARY) = CAST('((char_length(binding_id)>0)and(char_length(issuer_uri)>0)and(char_length(resource_uri)>0)and(char_length(client_id)>0)and(cast(product_codeascharcharsetbinary)=cast(''fbsir_independent_board''ascharcharsetbinary)))' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_source' AND CAST(normalized_clause AS BINARY) = CAST('(source_code=''workbuddy'')' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_connector' AND CAST(normalized_clause AS BINARY) = CAST('(connector_code=''fbs-connector'')' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_resource' AND CAST(normalized_clause AS BINARY) = CAST('(resource_uri=''https://api2.u3w.com/fbs-mcp/mcp'')' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_status' AND CAST(normalized_clause AS BINARY) = CAST('(statusin(''active'',''revoked'',''compromised''))' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_verification' AND CAST(normalized_clause AS BINARY) = CAST('(verification_methodin(''mcp_initialize'',''mcp_tools_list''))' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_principal_digest' AND CAST(normalized_clause AS BINARY) = CAST('regexp_like(principal_subject_digest,''^[0-9a-f]{64}$'')' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_evidence_digest' AND CAST(normalized_clause AS BINARY) = CAST('regexp_like(evidence_digest,''^[0-9a-f]{64}$'')' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_lifecycle' AND CAST(normalized_clause AS BINARY) = CAST('(((status=''active'')and(revoked_atisnull))or((statusin(''revoked'',''compromised''))and(revoked_atisnotnull)))' AS BINARY))
      OR (table_name = 'fbs_connector_binding' AND constraint_name = 'chk_connector_binding_temporal' AND CAST(normalized_clause AS BINARY) = CAST('((last_seen_at>=verified_at)and(valid_until>last_seen_at)and((revoked_atisnull)or(revoked_at>=last_seen_at)))' AS BINARY))
      OR (table_name = 'fbs_connector_binding_scope' AND constraint_name = 'chk_connector_binding_scope_code' AND CAST(normalized_clause AS BINARY) = CAST('(scope_codein(''identity.read'',''entitlement.read'',''board.meeting.reserve'',''board.receipt.write''))' AS BINARY))
      OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'chk_connector_binding_receipt_id' AND CAST(normalized_clause AS BINARY) = CAST('(char_length(receipt_id)>0)' AS BINARY))
      OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'chk_connector_binding_receipt_action' AND CAST(normalized_clause AS BINARY) = CAST('(actionin(''connector_binding_verified'',''connector_binding_revoked''))' AS BINARY))
      OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'chk_connector_binding_receipt_payload_digest' AND CAST(normalized_clause AS BINARY) = CAST('regexp_like(payload_digest,''^[0-9a-f]{64}$'')' AS BINARY))
      OR (table_name = 'fbs_connector_binding_receipt' AND constraint_name = 'chk_connector_binding_receipt_evidence' AND CAST(normalized_clause AS BINARY) = CAST('(evidence_level=''action_completed'')' AS BINARY))),
  (SELECT COUNT(*) FROM information_schema.triggers
   WHERE trigger_schema = DATABASE()
     AND event_object_table = 'fbs_connector_binding_receipt'),
  (SELECT COUNT(*) FROM (
       SELECT trigger_name, event_object_table, event_manipulation,
              action_timing, action_orientation,
              LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                  action_statement, CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                  CHAR(13), ''), CHAR(92), '')) AS normalized_action
       FROM information_schema.triggers
       WHERE trigger_schema = DATABASE()
         AND event_object_table = 'fbs_connector_binding_receipt'
         AND action_condition IS NULL
   ) receipt_triggers
   WHERE event_object_table = 'fbs_connector_binding_receipt'
     AND action_timing = 'BEFORE' AND action_orientation = 'ROW'
     AND CAST(normalized_action AS BINARY) = CAST(
         'signalsqlstate''45000''setmessage_text=''connectorbindingreceiptsareimmutable'''
         AS BINARY)
     AND ((trigger_name = 'trg_connector_binding_receipt_no_update' AND event_manipulation = 'UPDATE')
       OR (trigger_name = 'trg_connector_binding_receipt_no_delete' AND event_manipulation = 'DELETE')))
);
"@
    $exactStateParts = @($exactStateResponse.Split('|'))
    $expectedExactState = @(3, 36, 7, $expectedConnectorIndexCount, 3, 8, 15, 15, 2, 2)
    if ($exactStateParts.Count -ne $expectedExactState.Count) {
        throw "Independent Board Connector exact current-read verifier returned an invalid field count: '$exactStateResponse'."
    }
    for ($index = 0; $index -lt $expectedExactState.Count; $index++) {
        if ($exactStateParts[$index] -notmatch '^\d+$' -or [int]$exactStateParts[$index] -ne $expectedExactState[$index]) {
            throw "Independent Board Connector exact current-read drift detected at field $($index + 1): expected $($expectedExactState[$index]), found '$($exactStateParts[$index])'."
        }
    }
    Write-Host "PASS Independent Board Connector binding exact current-read audit (ordered and typed columns/defaults, visible full indexes, same-schema foreign keys, enforced exact checks and internal receipt)."
}

function Assert-IndependentBoardOauthServerProfile {
    $serverProfileResponse = Invoke-MySqlText -Sql "SELECT CONCAT_WS('|', VERSION(), @@version_comment);"
    $serverProfileParts = @($serverProfileResponse.Split('|'))
    $supportedForeignKeyDigestByVersion = @{
        '8.0.30' = '016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539'
        '8.4.8' = '3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f'
    }
    $supportedProvenanceCheckDigestByVersion = @{
        '8.0.30' = 'd8878ff64c7e897ddd8d42db6f0afd1a264d2cc8c1794d155051f0cd1638103b'
        '8.4.8' = 'd8878ff64c7e897ddd8d42db6f0afd1a264d2cc8c1794d155051f0cd1638103b'
    }
    if ($serverProfileParts.Count -ne 2 -or
        -not [string]::Equals($serverProfileParts[1], 'MySQL Community Server - GPL', [StringComparison]::Ordinal) -or
        -not $supportedForeignKeyDigestByVersion.ContainsKey($serverProfileParts[0])) {
        throw "Independent Board OAuth current-read requires exact MySQL Community 8.0.30 or 8.4.8; found '$serverProfileResponse'."
    }
    return [pscustomobject]@{
        Version = $serverProfileParts[0]
        VersionComment = $serverProfileParts[1]
        ForeignKeyDigest = $supportedForeignKeyDigestByVersion[$serverProfileParts[0]]
        ProvenanceCheckDigest = $supportedProvenanceCheckDigestByVersion[$serverProfileParts[0]]
    }
}

function Assert-IndependentBoardOauthFoundationCurrentState {
    $serverProfile = Assert-IndependentBoardOauthServerProfile
    $refreshSecurityState = Invoke-MySqlText -Sql "SELECT COALESCE((SELECT description FROM u3w_schema_migration WHERE version='20260721_independent_board_oauth_refresh_security_v1'), '');"
    if ($refreshSecurityState) {
        if (-not [string]::Equals(
                $refreshSecurityState,
                'APPLIED:Independent Board OAuth refresh security receipt v2',
                [StringComparison]::Ordinal)) {
            throw "Independent Board OAuth refresh-security successor is not in its exact completed state: '$refreshSecurityState'."
        }
        Assert-IndependentBoardOauthRefreshSecurityCurrentState
        return
    }
    $serverVersion = $serverProfile.Version
    $neutralClientNameUtf8Hex = 'e69caae9aa8ce8af81e79a84e69cace59cb0e585ace585b1e5aea2e688b7e7abaf'
    $neutralClientNameUtf8Bytes = [byte[]]::new($neutralClientNameUtf8Hex.Length / 2)
    for ($byteIndex = 0; $byteIndex -lt $neutralClientNameUtf8Bytes.Length; $byteIndex++) {
        $neutralClientNameUtf8Bytes[$byteIndex] = [Convert]::ToByte(
            $neutralClientNameUtf8Hex.Substring($byteIndex * 2, 2), 16)
    }
    # MySQL 8.0/8.4 can serialize a correct non-ASCII CHECK literal in
    # information_schema as the UTF-8 encoding of its individual source bytes.
    # Derive that read-only metadata projection from the correct UTF-8 bytes;
    # it is never accepted as an application value.
    $neutralClientNameMetadataProjection = -join @(
        $neutralClientNameUtf8Bytes | ForEach-Object { [char][int]$_ }
    )
    $neutralClientNameMetadataProjectionHex = -join @(
        [System.Text.UTF8Encoding]::new($false).GetBytes($neutralClientNameMetadataProjection) |
            ForEach-Object { $_.ToString('x2') }
    )
    $showCreateSql = [System.Text.UTF8Encoding]::new($false).GetBytes(
        'SHOW CREATE TABLE fbs_oauth_client;')
    [byte[]]$showCreateBytes = Invoke-MySqlRawOutputBytes -Bytes $showCreateSql
    $showCreateHex = ([BitConverter]::ToString($showCreateBytes) -replace '-', '').ToLowerInvariant()
    $legacyClientNameHex = -join @(
        [System.Text.Encoding]::ASCII.GetBytes('WorkBuddy - ') |
            ForEach-Object { $_.ToString('x2') }
    )
    if ([regex]::Matches($showCreateHex, $neutralClientNameUtf8Hex).Count -ne 1 -or
        [regex]::Matches($showCreateHex, $neutralClientNameMetadataProjectionHex).Count -ne 0 -or
        [regex]::Matches($showCreateHex, $legacyClientNameHex).Count -ne 0) {
        throw 'Independent Board OAuth fixed-profile SHOW CREATE bytes do not contain exactly one correct neutral client name or retain a forbidden legacy/metadata projection.'
    }
    $provenanceInternalVersion = '20260721_independent_board_oauth_receipt_provenance_v1'
    $provenanceAppliedDescription = 'APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness'
    $provenanceState = Invoke-MySqlText -Sql "SELECT COALESCE((SELECT description FROM u3w_schema_migration WHERE version='$provenanceInternalVersion'), '');"
    if ($provenanceState -and
        -not [string]::Equals($provenanceState, $provenanceAppliedDescription, [StringComparison]::Ordinal)) {
        throw "Independent Board OAuth provenance receipt is not in its exact completed state: '$provenanceState'."
    }
    $provenanceApplied = [string]::Equals(
        $provenanceState,
        $provenanceAppliedDescription,
        [StringComparison]::Ordinal)
    $consentIntentInternalVersion = '20260721_independent_board_oauth_consent_intent_lineage_v1'
    $consentIntentAppliedDescription = 'APPLIED:Independent Board OAuth consent intent lineage'
    $consentIntentState = Invoke-MySqlText -Sql "SELECT COALESCE((SELECT description FROM u3w_schema_migration WHERE version='$consentIntentInternalVersion'), '');"
    if ($consentIntentState -and
        -not [string]::Equals($consentIntentState, $consentIntentAppliedDescription, [StringComparison]::Ordinal)) {
        throw "Independent Board OAuth consent-intent receipt is not in its exact completed state: '$consentIntentState'."
    }
    $consentIntentApplied = [string]::Equals(
        $consentIntentState,
        $consentIntentAppliedDescription,
        [StringComparison]::Ordinal)
    if ($consentIntentApplied -and -not $provenanceApplied) {
        throw 'Independent Board OAuth consent-intent receipt cannot be complete without the exact provenance predecessor.'
    }
    $requestColumnSignature = 'id,request_handle_digest,client_id,redirect_uri,code_challenge,code_challenge_method,state_digest,state_key_ref,state_nonce,state_ciphertext,issuer_uri,resource_uri,product_code,source_code,connector_code,scope_canonical,scope_digest,principal_subject_digest,enterprise_id,member_id,user_id,status,requested_at,expires_at,approved_at,denied_at,consumed_at,version,created_at,updated_at'
    $codeColumnSignature = 'id,code_digest,authorization_request_id,client_id,redirect_uri,code_challenge,code_challenge_method,issuer_uri,resource_uri,product_code,source_code,connector_code,scope_canonical,scope_digest,principal_subject_digest,enterprise_id,member_id,user_id,status,issued_at,expires_at,used_at,revoked_at,version,created_at,updated_at'
    $familyColumnSignature = 'id,family_id,origin_authorization_code_id,client_id,enterprise_id,member_id,user_id,product_code,source_code,connector_code,issuer_uri,resource_uri,scope_canonical,scope_digest,principal_subject_digest,binding_id,binding_version,status,lifecycle_slot,current_refresh_generation,issued_at,activated_at,expires_at,terminated_at,version,created_at,updated_at'
    if ($consentIntentApplied) {
        $requestColumnSignature += ',consent_intent'
        $codeColumnSignature += ',consent_intent'
        $familyColumnSignature += ',consent_intent'
    }
    $receiptColumnSignature = 'id,receipt_id,action,client_id,authorization_request_id,authorization_code_id,family_id,token_id,binding_id,enterprise_id,member_id,user_id,principal_subject_digest,actor_type,actor_user_id,actor_subject_digest,correlation_id,payload_digest,evidence_level,created_at'
    if ($provenanceApplied) {
        $receiptColumnSignature += ',family_created_slot'
    }
    $expectedOauthColumnCount = if ($consentIntentApplied) { 148 } elseif ($provenanceApplied) { 145 } else { 144 }
    $expectedOauthGeneratedColumnCount = if ($provenanceApplied) { 3 } else { 2 }
    $expectedOauthIndexCount = if ($consentIntentApplied) { 57 } elseif ($provenanceApplied) { 53 } else { 52 }
    $expectedOauthForeignKeyCount = if ($consentIntentApplied) { 16 } else { 14 }
    $expectedOauthForeignKeyColumnCount = if ($consentIntentApplied) { 61 } else { 57 }
    $expectedOauthCheckCount = if ($consentIntentApplied) { 36 } else { 33 }
    $expectedOauthTriggerCount = if ($consentIntentApplied) { 3 } else { 2 }
    $expectedProvenanceObjectCount = if ($provenanceApplied) { 1 } else { 0 }

    $stateResponse = Invoke-MySqlText -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE() AND engine = 'InnoDB'
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE() AND table_collation = 'utf8mb4_unicode_ci'
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM (
       SELECT table_name, GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ',') AS column_signature
       FROM information_schema.columns
       WHERE table_schema = DATABASE()
         AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
       GROUP BY table_name
   ) ordered_columns
   WHERE (table_name = 'fbs_oauth_client' AND CAST(column_signature AS BINARY) = CAST('id,client_id,client_name,issuer_uri,resource_uri,product_code,source_code,connector_code,redirect_port,redirect_uri,token_endpoint_auth_method,grant_types_canonical,response_types_canonical,scope_canonical,scope_digest,metadata_digest,registration_source_digest,status,registered_at,expires_at,terminated_at,version,created_at,updated_at' AS BINARY))
       OR (table_name = 'fbs_oauth_authorization_request' AND CAST(column_signature AS BINARY) = CAST('$requestColumnSignature' AS BINARY))
       OR (table_name = 'fbs_oauth_authorization_code' AND CAST(column_signature AS BINARY) = CAST('$codeColumnSignature' AS BINARY))
       OR (table_name = 'fbs_oauth_token_family' AND CAST(column_signature AS BINARY) = CAST('$familyColumnSignature' AS BINARY))
      OR (table_name = 'fbs_oauth_token' AND CAST(column_signature AS BINARY) = CAST('id,token_digest,family_id,token_type,generation,resource_uri,scope_canonical,scope_digest,status,active_refresh_slot,issued_at,used_at,revoked_at,expires_at,version,created_at,updated_at' AS BINARY))
       OR (table_name = 'fbs_oauth_receipt' AND CAST(column_signature AS BINARY) = CAST('$receiptColumnSignature' AS BINARY))),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND column_type = 'binary(32)'
     AND ((table_name = 'fbs_oauth_client' AND column_name IN ('scope_digest','metadata_digest','registration_source_digest') AND is_nullable = 'NO')
       OR (table_name = 'fbs_oauth_authorization_request' AND ((column_name IN ('request_handle_digest','state_digest','scope_digest') AND is_nullable = 'NO') OR (column_name = 'principal_subject_digest' AND is_nullable = 'YES')))
       OR (table_name = 'fbs_oauth_authorization_code' AND column_name IN ('code_digest','scope_digest','principal_subject_digest') AND is_nullable = 'NO')
       OR (table_name = 'fbs_oauth_token_family' AND column_name IN ('scope_digest','principal_subject_digest') AND is_nullable = 'NO')
       OR (table_name = 'fbs_oauth_token' AND column_name IN ('token_digest','scope_digest') AND is_nullable = 'NO')
       OR (table_name = 'fbs_oauth_receipt' AND ((column_name = 'principal_subject_digest' AND is_nullable = 'YES') OR (column_name IN ('actor_subject_digest','payload_digest') AND is_nullable = 'NO'))))),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
      AND ((table_name = 'fbs_oauth_token_family' AND column_name = 'lifecycle_slot' AND extra = 'STORED GENERATED' AND generation_expression <> '')
        OR (table_name = 'fbs_oauth_token' AND column_name = 'active_refresh_slot' AND extra = 'STORED GENERATED' AND generation_expression <> '')
        OR (table_name = 'fbs_oauth_receipt' AND column_name = 'family_created_slot'
            AND column_type = 'varchar(128)' AND is_nullable = 'YES'
            AND character_set_name = 'ascii' AND collation_name = 'ascii_bin'
            AND extra = 'STORED GENERATED'
            AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(generation_expression, CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''), CHAR(92), ''), '(', ''), ')', '')) IN
                 ('casewhenaction=_utf8mb4''token_family_created''thenfamily_idelsenullend',
                  'casewhenaction=_ascii''token_family_created''thenfamily_idelsenullend',
                  'casewhenaction=''token_family_created''thenfamily_idelsenullend')))),
  (SELECT COUNT(*) FROM (SELECT table_name, index_name FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
    GROUP BY table_name, index_name) target_indexes),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name IN ('fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
     AND unique_constraint_schema = DATABASE() AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'),
  (SELECT COUNT(*) FROM information_schema.key_column_usage
   WHERE constraint_schema = DATABASE() AND referenced_table_schema = DATABASE()
     AND referenced_table_name IS NOT NULL
     AND table_name IN ('fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK' AND enforced = 'YES'
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.check_constraints cc
   INNER JOIN information_schema.table_constraints tc
     ON tc.constraint_schema = cc.constraint_schema AND tc.constraint_name = cc.constraint_name
   WHERE tc.constraint_schema = DATABASE() AND tc.table_name = 'fbs_oauth_client'
     AND tc.constraint_name = 'chk_oauth_client_fixed_profile' AND tc.enforced = 'YES'),
  (SELECT COUNT(*) FROM information_schema.triggers
   WHERE trigger_schema = DATABASE()
     AND event_object_table IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM (
       SELECT trigger_name, event_object_table, event_manipulation,
              action_timing, action_orientation,
               LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(action_statement, CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''), CHAR(92), '')) AS normalized_action
       FROM information_schema.triggers
       WHERE trigger_schema = DATABASE()
         AND event_object_table IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
         AND action_condition IS NULL
   ) receipt_triggers
   WHERE event_object_table = 'fbs_oauth_receipt'
     AND action_timing = 'BEFORE' AND action_orientation = 'ROW'
     AND CAST(normalized_action AS BINARY) = CAST('signalsqlstate''45000''setmessage_text=''oauthreceiptsareimmutable''' AS BINARY)
     AND ((trigger_name = 'trg_oauth_receipt_no_update' AND event_manipulation = 'UPDATE')
       OR (trigger_name = 'trg_oauth_receipt_no_delete' AND event_manipulation = 'DELETE'))),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_foundation_v1'
     AND description = 'Independent Board OAuth client, authorization, token family and immutable receipt tables'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_foundation_v1'),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
     AND column_name = 'family_created_slot' AND column_type = 'varchar(128)'
     AND is_nullable = 'YES' AND character_set_name = 'ascii'
     AND collation_name = 'ascii_bin' AND extra = 'STORED GENERATED'
     AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(generation_expression, CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''), CHAR(92), ''), '(', ''), ')', '')) IN
          ('casewhenaction=_utf8mb4''token_family_created''thenfamily_idelsenullend',
           'casewhenaction=_ascii''token_family_created''thenfamily_idelsenullend',
           'casewhenaction=''token_family_created''thenfamily_idelsenullend')),
  (SELECT COUNT(*) FROM (
       SELECT table_name, index_name, non_unique, index_type,
              MIN(is_visible) AS visibility, COUNT(*) AS key_part_count,
              SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
              SUM(CASE WHEN sub_part IS NULL THEN 0 ELSE 1 END) AS partial_columns,
              GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL')) ORDER BY seq_in_index SEPARATOR ',') AS column_signature
       FROM information_schema.statistics
       WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
         AND index_name = 'uk_oauth_receipt_family_created_slot'
       GROUP BY table_name, index_name, non_unique, index_type
   ) exact_provenance_index
   WHERE non_unique = 0 AND index_type = 'BTREE' AND visibility = 'YES'
     AND key_part_count = 1 AND non_column_key_parts = 0 AND partial_columns = 0
     AND CAST(column_signature AS BINARY) = CAST('family_created_slot:A' AS BINARY)),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '$provenanceInternalVersion'
     AND description = '$provenanceAppliedDescription')
 );
"@
    $stateParts = @($stateResponse.Split('|'))
    $expectedState = @(
        6, 6, 6, 6, $expectedOauthColumnCount, 6, 17,
        $expectedOauthGeneratedColumnCount, $expectedOauthIndexCount,
        $expectedOauthForeignKeyCount, $expectedOauthForeignKeyColumnCount,
        $expectedOauthCheckCount, $expectedOauthCheckCount, 1,
        $expectedOauthTriggerCount, 2, 1, 1,
        $expectedProvenanceObjectCount, $expectedProvenanceObjectCount,
        $expectedProvenanceObjectCount
    )
    if ($stateParts.Count -ne $expectedState.Count) {
        throw "Independent Board OAuth foundation current-read verifier returned an invalid field count: '$stateResponse'."
    }
    for ($index = 0; $index -lt $expectedState.Count; $index++) {
        if ($stateParts[$index] -notmatch '^\d+$' -or [int]$stateParts[$index] -ne $expectedState[$index]) {
            throw "Independent Board OAuth foundation current-read drift detected at field $($index + 1): expected $($expectedState[$index]), found '$($stateParts[$index])'."
        }
    }

    $exactDigestResponse = Invoke-MySqlText -Sql @"
SET SESSION group_concat_max_len = 1048576;
SELECT CONCAT_WS('|',
  (SELECT SHA2(GROUP_CONCAT(CONCAT(
       'T:', HEX(CAST(table_name AS BINARY)),
       '|O:', LPAD(ordinal_position, 3, '0'),
       '|N:', HEX(CAST(column_name AS BINARY)),
       '|Y:', HEX(CAST(column_type AS BINARY)),
       '|U:', HEX(CAST(is_nullable AS BINARY)),
       '|D:', IF(column_default IS NULL, 'N', CONCAT('V:', HEX(CAST(column_default AS BINARY)))),
       '|C:', IF(character_set_name IS NULL, 'N', CONCAT('V:', HEX(CAST(character_set_name AS BINARY)))),
       '|L:', IF(collation_name IS NULL, 'N', CONCAT('V:', HEX(CAST(collation_name AS BINARY)))),
       '|E:', HEX(CAST(extra AS BINARY)),
       '|G:', IF(generation_expression IS NULL, 'N', CONCAT('V:', HEX(CAST(generation_expression AS BINARY)))))
       ORDER BY table_name, ordinal_position SEPARATOR 0x0A), 256)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
      AND NOT (table_name = 'fbs_oauth_receipt' AND column_name = 'family_created_slot')
      AND NOT (table_name = 'fbs_oauth_authorization_request' AND column_name = 'consent_intent')
      AND NOT (table_name = 'fbs_oauth_authorization_code' AND column_name = 'consent_intent')
      AND NOT (table_name = 'fbs_oauth_token_family' AND column_name = 'consent_intent')),
  (SELECT SHA2(GROUP_CONCAT(CONCAT(
       'T:', HEX(CAST(table_name AS BINARY)),
       '|I:', HEX(CAST(index_name AS BINARY)),
       '|U:', non_unique,
       '|Y:', HEX(CAST(index_type AS BINARY)),
       '|V:', HEX(CAST(is_visible AS BINARY)),
       '|S:', seq_in_index,
       '|N:', IF(column_name IS NULL, 'N', CONCAT('V:', HEX(CAST(column_name AS BINARY)))),
       '|X:', IF(expression IS NULL, 'N', CONCAT('V:', HEX(CAST(expression AS BINARY)))),
       '|C:', IF(collation IS NULL, 'N', CONCAT('V:', HEX(CAST(collation AS BINARY)))),
       '|P:', IF(sub_part IS NULL, 'N', CONCAT('V:', sub_part)),
       '|Q:', HEX(CAST(nullable AS BINARY)))
       ORDER BY table_name, index_name, seq_in_index SEPARATOR 0x0A), 256)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
      AND NOT (table_name = 'fbs_oauth_receipt' AND index_name = 'uk_oauth_receipt_family_created_slot')
      AND NOT (table_name = 'fbs_oauth_authorization_request' AND index_name = 'uk_oauth_request_id_consent')
      AND NOT (table_name = 'fbs_oauth_authorization_code' AND index_name IN ('idx_oauth_code_request_consent','uk_oauth_code_id_consent'))
      AND NOT (table_name = 'fbs_oauth_token_family' AND index_name = 'idx_oauth_family_code_consent')),
  (SELECT SHA2(GROUP_CONCAT(CONCAT(
       'T:', HEX(CAST(rc.table_name AS BINARY)),
       '|C:', HEX(CAST(rc.constraint_name AS BINARY)),
       '|S:', IF(rc.unique_constraint_schema = DATABASE(), 'SAME', 'OTHER'),
       '|K:', HEX(CAST(rc.unique_constraint_name AS BINARY)),
       '|R:', HEX(CAST(rc.referenced_table_name AS BINARY)),
       '|U:', HEX(CAST(rc.update_rule AS BINARY)),
       '|D:', HEX(CAST(rc.delete_rule AS BINARY)),
       '|M:', HEX(CAST(rc.match_option AS BINARY)),
       '|O:', kcu.ordinal_position,
       '|N:', HEX(CAST(kcu.column_name AS BINARY)),
       '|Q:', IF(kcu.referenced_table_schema = DATABASE(), 'SAME', 'OTHER'),
       '|P:', HEX(CAST(kcu.referenced_column_name AS BINARY)),
       '|I:', IF(kcu.position_in_unique_constraint IS NULL, 'N', CONCAT('V:', kcu.position_in_unique_constraint)))
       ORDER BY rc.table_name, rc.constraint_name, kcu.ordinal_position SEPARATOR 0x0A), 256)
   FROM information_schema.referential_constraints rc
   INNER JOIN information_schema.key_column_usage kcu
     ON kcu.constraint_schema = rc.constraint_schema
    AND kcu.table_name = rc.table_name
    AND kcu.constraint_name = rc.constraint_name
   WHERE rc.constraint_schema = DATABASE()
      AND rc.unique_constraint_schema = DATABASE()
      AND kcu.referenced_table_schema = DATABASE()
      AND rc.table_name IN ('fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
      AND NOT (rc.table_name = 'fbs_oauth_authorization_code' AND rc.constraint_name = 'fk_oauth_code_request_consent')
      AND NOT (rc.table_name = 'fbs_oauth_token_family' AND rc.constraint_name = 'fk_oauth_family_code_consent')),
  (SELECT SHA2(GROUP_CONCAT(CONCAT(
       'T:', HEX(CAST(table_name AS BINARY)),
       '|C:', HEX(CAST(constraint_name AS BINARY)),
       '|E:', HEX(CAST(enforced AS BINARY)),
       '|X:', HEX(CAST(check_clause AS BINARY)))
       ORDER BY table_name, constraint_name SEPARATOR 0x0A), 256)
   FROM (
       SELECT tc.table_name, tc.constraint_name, tc.enforced, cc.check_clause
       FROM information_schema.table_constraints tc
       INNER JOIN information_schema.check_constraints cc
         ON cc.constraint_schema = tc.constraint_schema
        AND cc.constraint_name = tc.constraint_name
        WHERE tc.constraint_schema = DATABASE()
          AND tc.constraint_type = 'CHECK'
          AND tc.table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
          AND NOT (tc.table_name = 'fbs_oauth_authorization_request' AND tc.constraint_name = 'chk_oauth_request_consent_intent')
          AND NOT (tc.table_name = 'fbs_oauth_authorization_code' AND tc.constraint_name = 'chk_oauth_code_consent_intent')
          AND NOT (tc.table_name = 'fbs_oauth_token_family' AND tc.constraint_name = 'chk_oauth_family_consent_intent')
   ) exact_checks)
);
"@
    $exactDigestParts = @($exactDigestResponse.Split('|'))
    if ($exactDigestParts.Count -ne 4) {
        throw "Independent Board OAuth exact metadata verifier returned an invalid digest field count: '$exactDigestResponse'."
    }
    if (-not [string]::Equals($exactDigestParts[1], '1a3122b7238b444939a7981ccd759322bd168f351131e4d9c0a16346242c3d79', [StringComparison]::Ordinal)) {
        throw "Independent Board OAuth exact index metadata digest drifted: '$($exactDigestParts[1])'."
    }
    if (-not [string]::Equals(
            $exactDigestParts[2],
            $serverProfile.ForeignKeyDigest,
            [StringComparison]::Ordinal)) {
        throw "Independent Board OAuth exact foreign-key metadata digest drifted: '$($exactDigestParts[2])'."
    }
    $expectedColumnDigest = if ($consentIntentApplied) {
        'e222fafb27746a52bdc6e31c08a9b8cb3e34c2a8eb88bad9345d53b496d0c612'
    }
    else {
        '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'
    }
    $expectedCheckDigest = if ($consentIntentApplied) {
        '0aca71671f1632851a45f57eeacfa18f23529d628ddaa159dc2ca7cb472a0d24'
    }
    elseif ($provenanceApplied) {
        $serverProfile.ProvenanceCheckDigest
    }
    else {
        '51f71711dfc17b5a6741fbbd1bd3d31ae7d4761e9da5722c614eb199a81afb11'
    }
    if (-not [string]::Equals($exactDigestParts[0], $expectedColumnDigest, [StringComparison]::Ordinal)) {
        throw "Independent Board OAuth exact column metadata digest drifted: '$($exactDigestParts[0])'."
    }
    if (-not [string]::Equals($exactDigestParts[3], $expectedCheckDigest, [StringComparison]::Ordinal)) {
        throw "Independent Board OAuth exact CHECK clause digest drifted: '$($exactDigestParts[3])'."
    }
    $oauthShapeLabel = if ($consentIntentApplied) {
        '033 foundation plus 034 provenance and 035 consent-intent successors'
    }
    elseif ($provenanceApplied) {
        '033 foundation plus 034 provenance successor'
    }
    else {
        '033 foundation'
    }
    Write-Host "PASS Independent Board OAuth $oauthShapeLabel exact current-read audit on MySQL Community $serverVersion (six tables, raw typed column/index/FK/CHECK metadata, binary foundation-subset digests, generated slots, immutable triggers and exact internal receipts)."
}

function Assert-IndependentBoardOauthConsentIntentCurrentState {
    $serverProfile = Assert-IndependentBoardOauthServerProfile
    $refreshSecurityState = Invoke-MySqlText -Sql "SELECT COALESCE((SELECT description FROM u3w_schema_migration WHERE version='20260721_independent_board_oauth_refresh_security_v1'), '');"
    if ($refreshSecurityState) {
        if (-not [string]::Equals(
                $refreshSecurityState,
                'APPLIED:Independent Board OAuth refresh security receipt v2',
                [StringComparison]::Ordinal)) {
            throw "Independent Board OAuth refresh-security successor is not in its exact completed state: '$refreshSecurityState'."
        }
        Assert-IndependentBoardOauthRefreshSecurityCurrentState
        return
    }
    $stateResponse = Invoke-MySqlText -Sql @"
SET SESSION group_concat_max_len = 1048576;
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
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_consent_intent_lineage_v1'),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM (
       SELECT table_name, index_name
       FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
       GROUP BY table_name, index_name
   ) all_indexes),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE()
     AND unique_constraint_schema = DATABASE()
     AND table_name IN ('fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.key_column_usage
   WHERE constraint_schema = DATABASE() AND referenced_table_schema = DATABASE()
     AND referenced_table_name IS NOT NULL
     AND table_name IN ('fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK' AND enforced = 'YES'
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.triggers
   WHERE trigger_schema = DATABASE()
     AND event_object_table IN ('fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_authorization_request'
     AND column_name = 'consent_intent' AND ordinal_position = 31
     AND column_type = 'varchar(32)' AND is_nullable = 'YES' AND column_default IS NULL
     AND character_set_name = 'ascii' AND collation_name = 'ascii_bin'
     AND extra = '' AND COALESCE(generation_expression, '') = ''),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_authorization_code'
     AND column_name = 'consent_intent' AND ordinal_position = 27
     AND column_type = 'varchar(32)' AND is_nullable = 'NO' AND column_default IS NULL
     AND character_set_name = 'ascii' AND collation_name = 'ascii_bin'
     AND extra = '' AND COALESCE(generation_expression, '') = ''),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_token_family'
     AND column_name = 'consent_intent' AND ordinal_position = 28
     AND column_type = 'varchar(32)' AND is_nullable = 'NO' AND column_default IS NULL
     AND character_set_name = 'ascii' AND collation_name = 'ascii_bin'
     AND extra = '' AND COALESCE(generation_expression, '') = ''),
  (SELECT COUNT(*) FROM (
       SELECT table_name, index_name, non_unique, index_type,
              MIN(is_visible) AS visibility, COUNT(*) AS key_part_count,
              SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
              SUM(sub_part IS NOT NULL) AS partial_columns,
              GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL')) ORDER BY seq_in_index SEPARATOR ',') AS column_signature
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
  (SELECT COUNT(*) FROM (
       SELECT rc.table_name, rc.constraint_name, rc.unique_constraint_schema,
              rc.unique_constraint_name, rc.referenced_table_name,
              rc.update_rule, rc.delete_rule, rc.match_option,
              COUNT(*) AS key_part_count,
              SUM(kcu.referenced_table_schema = DATABASE()) AS same_schema_part_count,
              GROUP_CONCAT(CONCAT(kcu.ordinal_position, ':', kcu.column_name, '>',
                  kcu.referenced_column_name, ':', kcu.position_in_unique_constraint)
                  ORDER BY kcu.ordinal_position SEPARATOR ',') AS key_signature
       FROM information_schema.referential_constraints rc
       INNER JOIN information_schema.key_column_usage kcu
         ON kcu.constraint_schema = rc.constraint_schema
        AND kcu.table_name = rc.table_name
        AND kcu.constraint_name = rc.constraint_name
       WHERE rc.constraint_schema = DATABASE()
         AND ((rc.table_name = 'fbs_oauth_authorization_code' AND rc.constraint_name = 'fk_oauth_code_request_consent')
           OR (rc.table_name = 'fbs_oauth_token_family' AND rc.constraint_name = 'fk_oauth_family_code_consent'))
       GROUP BY rc.table_name, rc.constraint_name, rc.unique_constraint_schema,
                rc.unique_constraint_name, rc.referenced_table_name,
                rc.update_rule, rc.delete_rule, rc.match_option
   ) named_foreign_keys
   WHERE unique_constraint_schema = DATABASE()
     AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT' AND match_option = 'NONE'
     AND key_part_count = 2 AND same_schema_part_count = 2
     AND ((table_name = 'fbs_oauth_authorization_code'
           AND CAST(unique_constraint_name AS BINARY) = CAST('uk_oauth_request_id_consent' AS BINARY)
           AND CAST(referenced_table_name AS BINARY) = CAST('fbs_oauth_authorization_request' AS BINARY)
           AND CAST(key_signature AS BINARY) = CAST('1:authorization_request_id>id:1,2:consent_intent>consent_intent:2' AS BINARY))
       OR (table_name = 'fbs_oauth_token_family'
           AND CAST(unique_constraint_name AS BINARY) = CAST('uk_oauth_code_id_consent' AS BINARY)
           AND CAST(referenced_table_name AS BINARY) = CAST('fbs_oauth_authorization_code' AS BINARY)
           AND CAST(key_signature AS BINARY) = CAST('1:origin_authorization_code_id>id:1,2:consent_intent>consent_intent:2' AS BINARY)))),
  (SELECT COUNT(*) FROM (
       SELECT tc.table_name, tc.constraint_name, tc.enforced,
              LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                  REPLACE(REPLACE(REPLACE(cc.check_clause,
                      '_utf8mb4', ''), '_utf8mb3', ''), '_ascii', ''),
                      CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                      CHAR(13), ''), CHAR(92), ''), '(', ''), ')', '')) AS normalized_clause
       FROM information_schema.table_constraints tc
       INNER JOIN information_schema.check_constraints cc
         ON cc.constraint_schema = tc.constraint_schema AND cc.constraint_name = tc.constraint_name
       WHERE tc.constraint_schema = DATABASE() AND tc.constraint_type = 'CHECK'
         AND ((tc.table_name = 'fbs_oauth_authorization_request' AND tc.constraint_name = 'chk_oauth_request_consent_intent')
           OR (tc.table_name = 'fbs_oauth_authorization_code' AND tc.constraint_name = 'chk_oauth_code_consent_intent')
           OR (tc.table_name = 'fbs_oauth_token_family' AND tc.constraint_name = 'chk_oauth_family_consent_intent'))
   ) named_checks
   WHERE enforced = 'YES'
     AND ((table_name = 'fbs_oauth_authorization_request'
           AND CAST(normalized_clause AS BINARY) = CAST('consent_intentisnullandprincipal_subject_digestisnullorconsent_intentin''first_connect'',''explicit_reauthorization''andprincipal_subject_digestisnotnull' AS BINARY))
       OR (table_name = 'fbs_oauth_authorization_code'
           AND CAST(normalized_clause AS BINARY) = CAST('consent_intentin''first_connect'',''explicit_reauthorization''' AS BINARY))
       OR (table_name = 'fbs_oauth_token_family'
           AND CAST(normalized_clause AS BINARY) = CAST('consent_intentin''first_connect'',''explicit_reauthorization''' AS BINARY)))),
  (SELECT COUNT(*) FROM (
       SELECT trigger_name, event_object_table, event_manipulation,
              action_timing, action_orientation, action_condition,
              LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                  REPLACE(REPLACE(REPLACE(action_statement,
                      '_utf8mb4', ''), '_utf8mb3', ''), '_ascii', ''),
                      CHAR(96), ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                      CHAR(13), ''), CHAR(92), ''), '(', ''), ')', '')) AS normalized_action
       FROM information_schema.triggers
       WHERE trigger_schema = DATABASE()
         AND event_object_table = 'fbs_oauth_authorization_request'
         AND trigger_name = 'trg_oauth_request_consent_intent_once'
   ) named_trigger
   WHERE event_manipulation = 'UPDATE' AND action_timing = 'BEFORE'
     AND action_orientation = 'ROW' AND action_condition IS NULL
     AND CAST(normalized_action AS BINARY) = CAST(
         'beginifold.consent_intentisnotnullandnotnew.consent_intent<=>old.consent_intentthensignalsqlstate''45000''setmessage_text=''oauthconsentintentisimmutable'';endif;ifold.consent_intentisnullandnew.consent_intentisnotnullandnotold.status=''pending''andnew.statusin''approved'',''denied''thensignalsqlstate''45000''setmessage_text=''oauthconsentintentmustbesetbyadecisiontransition'';endif;end'
         AS BINARY)),
  (SELECT COUNT(*) FROM fbs_oauth_authorization_code c
   LEFT JOIN fbs_oauth_authorization_request r
     ON r.id = c.authorization_request_id AND r.consent_intent = c.consent_intent
   WHERE r.id IS NULL),
  (SELECT COUNT(*) FROM fbs_oauth_token_family f
   LEFT JOIN fbs_oauth_authorization_code c
     ON c.id = f.origin_authorization_code_id AND c.consent_intent = f.consent_intent
   WHERE c.id IS NULL),
  (SELECT COUNT(*) FROM information_schema.routines
   WHERE routine_schema = DATABASE() AND routine_type = 'PROCEDURE'
     AND routine_name IN (
       'u3w_assert_ib_oauth_consent_intent_20260721',
       'u3w_migrate_ib_oauth_consent_intent_20260721',
       'u3w_finalize_ib_oauth_consent_intent_20260721'))
);
"@
    $stateParts = @($stateResponse.Split('|'))
    $expectedState = @(1, 1, 1, 1, 6, 148, 57, 16, 61, 36, 36, 3, 1, 1, 1, 4, 2, 3, 1, 0, 0, 0)
    if ($stateParts.Count -ne $expectedState.Count) {
        throw "Independent Board OAuth consent-intent current-read verifier returned an invalid field count: '$stateResponse'."
    }
    for ($index = 0; $index -lt $expectedState.Count; $index++) {
        if ($stateParts[$index] -notmatch '^\d+$' -or [int]$stateParts[$index] -ne $expectedState[$index]) {
            throw "Independent Board OAuth consent-intent current-read drift detected at field $($index + 1): expected $($expectedState[$index]), found '$($stateParts[$index])'."
        }
    }
    Write-Host "PASS Independent Board OAuth consent-intent exact current-read audit on MySQL Community $($serverProfile.Version) (internal receipts, three additive DDL stages, trigger, lineage and helper cleanup)."
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

function Assert-IndependentBoardOauthRefreshSecurityCurrentState {
    $serverProfile = Assert-IndependentBoardOauthServerProfile
    $expectedLegacyForeignKeyDigest = if ($serverProfile.Version -eq '8.0.30') {
        'd3b99d9c1923c59729166110ceeddd0c6987ef9806e1d644add9ca018ca8166a'
    }
    else {
        '1e103fc572908bef3353dfdec2a2076a76c68a5ec9dd3f8b5f938a486d431d22'
    }
    $stateResponse = Invoke-MySqlText -Sql @"
SET SESSION group_concat_max_len = 1048576;
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
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_refresh_security_v1'
     AND description = 'APPLIED:Independent Board OAuth refresh security receipt v2'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260721_independent_board_oauth_refresh_security_v1'),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family',
       'fbs_oauth_token','fbs_oauth_receipt')
     AND table_type = 'BASE TABLE' AND engine = 'InnoDB'
     AND table_collation = 'utf8mb4_unicode_ci'),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family',
       'fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family',
       'fbs_oauth_token','fbs_oauth_receipt')
     AND extra = 'STORED GENERATED' AND generation_expression <> ''),
  (SELECT COUNT(*) FROM (
       SELECT table_name, index_name
       FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',
           'fbs_oauth_authorization_code','fbs_oauth_token_family',
           'fbs_oauth_token','fbs_oauth_receipt')
       GROUP BY table_name, index_name
   ) exact_indexes),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE()
     AND unique_constraint_schema = DATABASE()
     AND table_name IN ('fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family',
       'fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.key_column_usage
   WHERE constraint_schema = DATABASE()
     AND referenced_table_schema = DATABASE()
     AND referenced_table_name IS NOT NULL
     AND table_name IN ('fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family',
       'fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family',
       'fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
     AND enforced = 'YES'
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family',
       'fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM information_schema.triggers
   WHERE trigger_schema = DATABASE()
     AND event_object_table IN ('fbs_oauth_client','fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family',
       'fbs_oauth_token','fbs_oauth_receipt')),
  (SELECT COUNT(*) FROM (
       SELECT tc.enforced,
              LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                  REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause,
                    '_utf8mb4',''),'_utf8mb3',''),'_ascii',''),'_gbk',''),
                    CHAR(96),''),' ',''),CHAR(9),''),CHAR(10),''),CHAR(13),''),
                    CHAR(92),''),'(',''),')','')) AS normalized_clause
       FROM information_schema.table_constraints tc
       INNER JOIN information_schema.check_constraints cc
         ON cc.constraint_schema = tc.constraint_schema
        AND cc.constraint_name = tc.constraint_name
       WHERE tc.constraint_schema = DATABASE() AND tc.constraint_type = 'CHECK'
         AND tc.table_name = 'fbs_oauth_authorization_request'
         AND tc.constraint_name = 'chk_oauth_request_consent_intent'
   ) request_check
   WHERE enforced = 'YES'
     AND CAST(normalized_clause AS BINARY) = CAST(
       'consent_intentisnullandprincipal_subject_digestisnullorconsent_intentisnotnullandconsent_intentin''first_connect'',''explicit_reauthorization''andprincipal_subject_digestisnotnull'
       AS BINARY)),
  (SELECT COUNT(*) FROM (
       SELECT index_name, non_unique, index_type, MIN(is_visible) AS visibility,
              COUNT(*) AS key_part_count,
              SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
              SUM(sub_part IS NOT NULL) AS partial_columns,
              GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                ORDER BY seq_in_index SEPARATOR ',') AS column_signature
       FROM information_schema.statistics
       WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_token'
         AND index_name IN ('uk_oauth_token_receipt_generation_type_scope',
           'idx_oauth_token_family_lock_order')
       GROUP BY table_name, index_name, non_unique, index_type
   ) token_support
   WHERE index_type = 'BTREE' AND visibility = 'YES'
     AND non_column_key_parts = 0 AND partial_columns = 0
     AND ((index_name = 'uk_oauth_token_receipt_generation_type_scope'
           AND non_unique = 0 AND key_part_count = 4
           AND CAST(column_signature AS BINARY) =
             CAST('id:A,family_id:A,generation:A,token_type:A' AS BINARY))
       OR (index_name = 'idx_oauth_token_family_lock_order'
           AND non_unique = 1 AND key_part_count = 2
           AND CAST(column_signature AS BINARY) =
             CAST('family_id:A,id:A' AS BINARY)))),
  (SELECT COUNT(*) FROM (
       SELECT index_name, non_unique, index_type, MIN(is_visible) AS visibility,
              COUNT(*) AS key_part_count,
              SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
              SUM(sub_part IS NOT NULL) AS partial_columns,
              GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                ORDER BY seq_in_index SEPARATOR ',') AS column_signature
       FROM information_schema.statistics
       WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
         AND index_name IN ('uk_oauth_receipt_causation_scope',
           'idx_oauth_receipt_family_client_lock_order')
       GROUP BY table_name, index_name, non_unique, index_type
   ) causation_support
   WHERE index_type = 'BTREE' AND visibility = 'YES'
     AND non_column_key_parts = 0 AND partial_columns = 0
     AND ((index_name = 'uk_oauth_receipt_causation_scope'
           AND non_unique = 0 AND key_part_count = 3
           AND CAST(column_signature AS BINARY) =
             CAST('receipt_id:A,family_id:A,client_id:A' AS BINARY))
       OR (index_name = 'idx_oauth_receipt_family_client_lock_order'
           AND non_unique = 1 AND key_part_count = 3
           AND CAST(column_signature AS BINARY) =
             CAST('family_id:A,client_id:A,id:A' AS BINARY)))),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope',
       'fbs_connector_binding_receipt')
     AND table_type = 'BASE TABLE' AND engine = 'InnoDB'
     AND table_collation = 'utf8mb4_unicode_ci'),
  (SELECT COUNT(*) FROM (
       SELECT table_name, index_name
       FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope',
           'fbs_connector_binding_receipt')
       GROUP BY table_name, index_name
   ) connector_indexes),
  (SELECT COUNT(*) FROM (
       SELECT non_unique, index_type, MIN(is_visible) AS visibility,
              COUNT(*) AS key_part_count,
              SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts,
              SUM(sub_part IS NOT NULL) AS partial_columns,
              GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                ORDER BY seq_in_index SEPARATOR ',') AS column_signature
       FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name = 'fbs_connector_binding_receipt'
         AND index_name = 'idx_connector_binding_receipt_lock_order'
       GROUP BY table_name, index_name, non_unique, index_type
   ) connector_support
   WHERE non_unique = 1 AND index_type = 'BTREE' AND visibility = 'YES'
     AND key_part_count = 2 AND non_column_key_parts = 0 AND partial_columns = 0
     AND CAST(column_signature AS BINARY) = CAST('binding_id:A,id:A' AS BINARY)),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
     AND column_name IN ('receipt_format_version','subject_generation',
       'result_generation','causation_receipt_id','before_state_digest',
       'after_state_digest','subject_token_type','security_event_slot')),
  (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
     AND index_name IN ('idx_oauth_receipt_causation_scope',
       'idx_oauth_receipt_refresh_subject','uk_oauth_receipt_security_event_slot')),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
     AND constraint_name IN ('fk_oauth_receipt_refresh_subject',
       'fk_oauth_receipt_causation_scope')),
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE() AND table_name = 'fbs_oauth_receipt'
     AND constraint_type = 'CHECK' AND enforced = 'YES'
     AND constraint_name IN ('chk_oauth_receipt_format_version',
       'chk_oauth_receipt_v2_shape')),
  (SELECT COUNT(*) FROM (
       SELECT trigger_name, event_manipulation, action_timing,
              action_orientation, action_condition,
              LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                action_statement,CHAR(96),''),' ',''),CHAR(9),''),CHAR(10),''),
                CHAR(13),''),CHAR(92),'')) AS normalized_action
       FROM information_schema.triggers
       WHERE trigger_schema = DATABASE()
         AND event_object_table = 'fbs_oauth_receipt'
   ) receipt_triggers
   WHERE action_condition IS NULL AND action_timing = 'BEFORE'
     AND action_orientation = 'ROW'
     AND CAST(normalized_action AS BINARY) = CAST(
       'signalsqlstate''45000''setmessage_text=''oauthreceiptsareimmutable''' AS BINARY)
     AND ((trigger_name = 'trg_oauth_receipt_no_update' AND event_manipulation = 'UPDATE')
       OR (trigger_name = 'trg_oauth_receipt_no_delete' AND event_manipulation = 'DELETE'))),
  (SELECT COUNT(*) FROM (
       SELECT event_manipulation, action_timing, action_orientation, action_condition,
              LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                REPLACE(REPLACE(REPLACE(action_statement,
                  '_utf8mb4',''),'_utf8mb3',''),'_ascii',''),CHAR(96),''),' ',''),
                  CHAR(9),''),CHAR(10),''),CHAR(13),''),CHAR(92),''),'(',''),')',''))
                AS normalized_action
       FROM information_schema.triggers
       WHERE trigger_schema = DATABASE()
         AND event_object_table = 'fbs_oauth_authorization_request'
         AND trigger_name = 'trg_oauth_request_consent_intent_once'
   ) consent_trigger
   WHERE event_manipulation = 'UPDATE' AND action_timing = 'BEFORE'
     AND action_orientation = 'ROW' AND action_condition IS NULL
     AND CAST(normalized_action AS BINARY) = CAST(
       'beginifold.consent_intentisnotnullandnotnew.consent_intent<=>old.consent_intentthensignalsqlstate''45000''setmessage_text=''oauthconsentintentisimmutable'';endif;ifold.consent_intentisnullandnew.consent_intentisnotnullandnotold.status=''pending''andnew.statusin''approved'',''denied''thensignalsqlstate''45000''setmessage_text=''oauthconsentintentmustbesetbyadecisiontransition'';endif;end'
       AS BINARY)),
  (SELECT COUNT(*) FROM information_schema.routines
   WHERE routine_schema = DATABASE() AND routine_type = 'PROCEDURE'
     AND routine_name IN ('u3w_assert_ib_oauth_refresh_security_20260721',
       'u3w_migrate_ib_oauth_refresh_security_20260721')),
  (SELECT COUNT(*) FROM fbs_oauth_authorization_request
   WHERE (consent_intent IS NULL AND principal_subject_digest IS NOT NULL)
      OR (consent_intent IS NOT NULL AND principal_subject_digest IS NULL)
      OR (consent_intent IS NOT NULL
          AND consent_intent NOT IN ('FIRST_CONNECT','EXPLICIT_REAUTHORIZATION'))),
  (SELECT COUNT(*) FROM fbs_oauth_authorization_code c
   LEFT JOIN fbs_oauth_authorization_request r
     ON r.id = c.authorization_request_id AND r.consent_intent = c.consent_intent
   WHERE r.id IS NULL),
  (SELECT COUNT(*) FROM fbs_oauth_token_family f
   LEFT JOIN fbs_oauth_authorization_code c
     ON c.id = f.origin_authorization_code_id AND c.consent_intent = f.consent_intent
   WHERE c.id IS NULL),
  (SELECT COUNT(*) FROM fbs_oauth_receipt
   WHERE COALESCE(
     (receipt_format_version = 1
      AND action NOT IN ('TOKEN_FAMILY_ROTATED','REFRESH_REPLAY_DETECTED')
      AND subject_generation IS NULL AND result_generation IS NULL
      AND causation_receipt_id IS NULL AND before_state_digest IS NULL
      AND after_state_digest IS NULL AND subject_token_type IS NULL
      AND security_event_slot IS NULL)
     OR
     (receipt_format_version = 2 AND action = 'TOKEN_FAMILY_ROTATED'
      AND authorization_request_id IS NULL AND authorization_code_id IS NULL
      AND family_id IS NOT NULL AND token_id IS NOT NULL AND binding_id IS NOT NULL
      AND subject_generation IS NOT NULL AND subject_generation < 4294967295
      AND result_generation IS NOT NULL
      AND result_generation = subject_generation + 1
      AND causation_receipt_id IS NOT NULL AND causation_receipt_id <> receipt_id
      AND before_state_digest IS NOT NULL AND after_state_digest IS NOT NULL
      AND before_state_digest <> after_state_digest
      AND subject_token_type = 'REFRESH' AND security_event_slot IS NOT NULL
      AND actor_type = 'CLIENT' AND actor_user_id IS NULL)
     OR
     (receipt_format_version = 2 AND action = 'REFRESH_REPLAY_DETECTED'
      AND authorization_request_id IS NULL AND authorization_code_id IS NULL
      AND family_id IS NOT NULL AND token_id IS NOT NULL AND binding_id IS NOT NULL
      AND subject_generation IS NOT NULL AND subject_generation < 4294967295
      AND result_generation IS NULL
      AND causation_receipt_id IS NOT NULL AND causation_receipt_id <> receipt_id
      AND before_state_digest IS NOT NULL AND after_state_digest IS NOT NULL
      AND before_state_digest <> after_state_digest
      AND subject_token_type = 'REFRESH' AND security_event_slot IS NOT NULL
      AND actor_type = 'CLIENT' AND actor_user_id IS NULL), 0) <> 1),
  (SELECT COUNT(*) FROM fbs_oauth_receipt r
   LEFT JOIN fbs_oauth_token t
     ON t.id = r.token_id AND t.family_id = r.family_id
    AND t.generation = r.subject_generation
    AND t.token_type = r.subject_token_type
   WHERE r.receipt_format_version = 2
     AND r.action IN ('TOKEN_FAMILY_ROTATED','REFRESH_REPLAY_DETECTED')
     AND t.id IS NULL),
  (SELECT COUNT(*) FROM fbs_oauth_receipt r
   LEFT JOIN fbs_oauth_receipt cause
     ON cause.receipt_id = r.causation_receipt_id
    AND cause.family_id = r.family_id AND cause.client_id = r.client_id
   WHERE r.receipt_format_version = 2
     AND r.action IN ('TOKEN_FAMILY_ROTATED','REFRESH_REPLAY_DETECTED')
     AND cause.id IS NULL)
);
"@
    $stateParts = @($stateResponse.Split('|'))
    $expectedState = @(
        1, 1, 1, 1, 1, 6, 156, 5, 64, 18, 68, 38, 38, 3,
        1, 2, 2, 3, 12, 1, 8, 3, 2, 2, 2, 1, 0, 0, 0, 0, 0, 0, 0
    )
    if ($stateParts.Count -ne $expectedState.Count) {
        throw "Independent Board OAuth refresh-security current-read returned an invalid field count: '$stateResponse'."
    }
    for ($index = 0; $index -lt $expectedState.Count; $index++) {
        if ($stateParts[$index] -notmatch '^\d+$' -or
            [int]$stateParts[$index] -ne $expectedState[$index]) {
            throw "Independent Board OAuth refresh-security current-read drift detected at field $($index + 1): expected $($expectedState[$index]), found '$($stateParts[$index])'."
        }
    }

    $digestResponse = Invoke-MySqlText -Sql @"
SET SESSION group_concat_max_len = 1048576;
SELECT CONCAT_WS('|',
  (SELECT SHA2(GROUP_CONCAT(CONCAT(
       'T:',HEX(CAST(table_name AS BINARY)),'|O:',LPAD(ordinal_position,3,'0'),
       '|N:',HEX(CAST(column_name AS BINARY)),'|Y:',HEX(CAST(column_type AS BINARY)),
       '|U:',HEX(CAST(is_nullable AS BINARY)),'|D:',IF(column_default IS NULL,'N',CONCAT('V:',HEX(CAST(column_default AS BINARY)))),
       '|C:',IF(character_set_name IS NULL,'N',CONCAT('V:',HEX(CAST(character_set_name AS BINARY)))),
       '|L:',IF(collation_name IS NULL,'N',CONCAT('V:',HEX(CAST(collation_name AS BINARY)))),
       '|E:',HEX(CAST(extra AS BINARY)),'|G:',IF(generation_expression IS NULL,'N',CONCAT('V:',HEX(CAST(generation_expression AS BINARY)))))
       ORDER BY table_name,ordinal_position SEPARATOR 0x0A),256)
   FROM information_schema.columns WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
     AND NOT (table_name = 'fbs_oauth_receipt' AND column_name IN (
       'receipt_format_version','subject_generation','result_generation','causation_receipt_id',
       'before_state_digest','after_state_digest','subject_token_type','security_event_slot'))),
  (SELECT SHA2(GROUP_CONCAT(CONCAT(
       'T:',HEX(CAST(table_name AS BINARY)),'|I:',HEX(CAST(index_name AS BINARY)),
       '|U:',non_unique,'|Y:',HEX(CAST(index_type AS BINARY)),'|V:',HEX(CAST(is_visible AS BINARY)),
       '|S:',seq_in_index,'|N:',IF(column_name IS NULL,'N',CONCAT('V:',HEX(CAST(column_name AS BINARY)))),
       '|X:',IF(expression IS NULL,'N',CONCAT('V:',HEX(CAST(expression AS BINARY)))),
       '|C:',IF(collation IS NULL,'N',CONCAT('V:',HEX(CAST(collation AS BINARY)))),
       '|P:',IF(sub_part IS NULL,'N',CONCAT('V:',sub_part)),'|Q:',HEX(CAST(nullable AS BINARY)))
       ORDER BY table_name,index_name,seq_in_index SEPARATOR 0x0A),256)
   FROM information_schema.statistics WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
      AND NOT (table_name = 'fbs_oauth_token' AND index_name IN (
        'uk_oauth_token_receipt_generation_type_scope','idx_oauth_token_family_lock_order'))
      AND NOT (table_name = 'fbs_oauth_receipt' AND index_name IN (
        'uk_oauth_receipt_causation_scope','idx_oauth_receipt_causation_scope',
        'idx_oauth_receipt_family_client_lock_order','idx_oauth_receipt_refresh_subject',
        'uk_oauth_receipt_security_event_slot'))),
  (SELECT SHA2(GROUP_CONCAT(CONCAT(
       'T:',HEX(CAST(table_name AS BINARY)),'|I:',HEX(CAST(index_name AS BINARY)),
       '|U:',non_unique,'|Y:',HEX(CAST(index_type AS BINARY)),'|V:',HEX(CAST(is_visible AS BINARY)),
       '|S:',seq_in_index,'|N:',IF(column_name IS NULL,'N',CONCAT('V:',HEX(CAST(column_name AS BINARY)))),
       '|X:',IF(expression IS NULL,'N',CONCAT('V:',HEX(CAST(expression AS BINARY)))),
       '|C:',IF(collation IS NULL,'N',CONCAT('V:',HEX(CAST(collation AS BINARY)))),
       '|P:',IF(sub_part IS NULL,'N',CONCAT('V:',sub_part)),'|Q:',HEX(CAST(nullable AS BINARY)))
       ORDER BY table_name,index_name,seq_in_index SEPARATOR 0x0A),256)
   FROM information_schema.statistics WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_connector_binding','fbs_connector_binding_scope',
       'fbs_connector_binding_receipt')
     AND NOT (table_name = 'fbs_connector_binding_receipt'
       AND index_name = 'idx_connector_binding_receipt_lock_order')),
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
     ON kcu.constraint_schema=rc.constraint_schema AND kcu.table_name=rc.table_name
    AND kcu.constraint_name=rc.constraint_name
   WHERE rc.constraint_schema=DATABASE()
     AND rc.table_name IN ('fbs_oauth_authorization_request','fbs_oauth_authorization_code',
       'fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
     AND NOT (rc.table_name='fbs_oauth_receipt' AND rc.constraint_name IN (
       'fk_oauth_receipt_refresh_subject','fk_oauth_receipt_causation_scope'))),
  (SELECT SHA2(GROUP_CONCAT(CONCAT('T:',HEX(CAST(tc.table_name AS BINARY)),
       '|C:',HEX(CAST(tc.constraint_name AS BINARY)),'|E:',HEX(CAST(tc.enforced AS BINARY)),
       '|X:',HEX(CAST(cc.check_clause AS BINARY)))
       ORDER BY tc.table_name,tc.constraint_name SEPARATOR 0x0A),256)
   FROM information_schema.table_constraints tc
   INNER JOIN information_schema.check_constraints cc
     ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
   WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
     AND tc.table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',
       'fbs_oauth_authorization_code','fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')
     AND NOT (tc.table_name='fbs_oauth_authorization_request' AND tc.constraint_name='chk_oauth_request_consent_intent')
     AND NOT (tc.table_name='fbs_oauth_receipt' AND tc.constraint_name IN (
       'chk_oauth_receipt_format_version','chk_oauth_receipt_v2_shape'))),
  (SELECT SHA2(GROUP_CONCAT(CONCAT(
       'T:',HEX(CAST(table_name AS BINARY)),'|O:',LPAD(ordinal_position,3,'0'),
       '|N:',HEX(CAST(column_name AS BINARY)),'|Y:',HEX(CAST(column_type AS BINARY)),
       '|U:',HEX(CAST(is_nullable AS BINARY)),'|D:',IF(column_default IS NULL,'N',CONCAT('V:',HEX(CAST(column_default AS BINARY)))),
       '|C:',IF(character_set_name IS NULL,'N',CONCAT('V:',HEX(CAST(character_set_name AS BINARY)))),
       '|L:',IF(collation_name IS NULL,'N',CONCAT('V:',HEX(CAST(collation_name AS BINARY)))),
       '|E:',HEX(CAST(extra AS BINARY)),'|G:',IF(generation_expression IS NULL,'N',CONCAT('V:',HEX(CAST(generation_expression AS BINARY)))))
       ORDER BY ordinal_position SEPARATOR 0x0A),256)
   FROM information_schema.columns WHERE table_schema=DATABASE()
     AND table_name='fbs_oauth_receipt' AND column_name IN (
       'receipt_format_version','subject_generation','result_generation','causation_receipt_id',
       'before_state_digest','after_state_digest','subject_token_type','security_event_slot')),
  (SELECT SHA2(GROUP_CONCAT(CONCAT(
       'T:',HEX(CAST(table_name AS BINARY)),'|I:',HEX(CAST(index_name AS BINARY)),
       '|U:',non_unique,'|Y:',HEX(CAST(index_type AS BINARY)),'|V:',HEX(CAST(is_visible AS BINARY)),
       '|S:',seq_in_index,'|N:',IF(column_name IS NULL,'N',CONCAT('V:',HEX(CAST(column_name AS BINARY)))),
       '|X:',IF(expression IS NULL,'N',CONCAT('V:',HEX(CAST(expression AS BINARY)))),
       '|C:',IF(collation IS NULL,'N',CONCAT('V:',HEX(CAST(collation AS BINARY)))),
       '|P:',IF(sub_part IS NULL,'N',CONCAT('V:',sub_part)),'|Q:',HEX(CAST(nullable AS BINARY)))
       ORDER BY index_name,seq_in_index SEPARATOR 0x0A),256)
   FROM information_schema.statistics WHERE table_schema=DATABASE()
     AND table_name='fbs_oauth_receipt' AND index_name IN (
       'idx_oauth_receipt_causation_scope','idx_oauth_receipt_refresh_subject',
       'uk_oauth_receipt_security_event_slot')),
  (SELECT SHA2(GROUP_CONCAT(CONCAT(
       'T:',HEX(CAST(rc.table_name AS BINARY)),'|C:',HEX(CAST(rc.constraint_name AS BINARY)),
       '|S:',IF(rc.unique_constraint_schema=DATABASE(),'SAME','OTHER'),
       '|K:',HEX(CAST(rc.unique_constraint_name AS BINARY)),'|R:',HEX(CAST(rc.referenced_table_name AS BINARY)),
       '|U:',HEX(CAST(rc.update_rule AS BINARY)),'|D:',HEX(CAST(rc.delete_rule AS BINARY)),
       '|M:',HEX(CAST(rc.match_option AS BINARY)),'|O:',kcu.ordinal_position,
       '|N:',HEX(CAST(kcu.column_name AS BINARY)),'|Q:',IF(kcu.referenced_table_schema=DATABASE(),'SAME','OTHER'),
       '|P:',HEX(CAST(kcu.referenced_column_name AS BINARY)),'|I:',IF(kcu.position_in_unique_constraint IS NULL,'N',CONCAT('V:',kcu.position_in_unique_constraint)))
       ORDER BY rc.constraint_name,kcu.ordinal_position SEPARATOR 0x0A),256)
   FROM information_schema.referential_constraints rc
   INNER JOIN information_schema.key_column_usage kcu
     ON kcu.constraint_schema=rc.constraint_schema AND kcu.table_name=rc.table_name
    AND kcu.constraint_name=rc.constraint_name
   WHERE rc.constraint_schema=DATABASE() AND rc.table_name='fbs_oauth_receipt'
     AND rc.constraint_name IN ('fk_oauth_receipt_refresh_subject','fk_oauth_receipt_causation_scope')),
  (SELECT SHA2(GROUP_CONCAT(CONCAT('T:',HEX(CAST(tc.table_name AS BINARY)),
       '|C:',HEX(CAST(tc.constraint_name AS BINARY)),'|E:',HEX(CAST(tc.enforced AS BINARY)),
       '|X:',HEX(CAST(cc.check_clause AS BINARY)))
       ORDER BY tc.constraint_name SEPARATOR 0x0A),256)
   FROM information_schema.table_constraints tc
   INNER JOIN information_schema.check_constraints cc
     ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
   WHERE tc.constraint_schema=DATABASE() AND tc.table_name='fbs_oauth_receipt'
     AND tc.constraint_name IN ('chk_oauth_receipt_format_version','chk_oauth_receipt_v2_shape'))
);
"@
    $digestParts = @($digestResponse.Split('|'))
    $expectedDigests = @(
        '09ac52b1ef4f2a095507a243d3a7048aa592e7b18b8057a2132d594a9d484197',
        '4d19566ba4b4c3922ee41e1a09b4f8d2f9adb6f57a9788159303a5dcac0d0a00',
        '504a017fe7c4a8ecc4c60619ea8beaf5e7d4aa207fcffe536ff47b8e1d9919b3',
        $expectedLegacyForeignKeyDigest,
        '11daf3aed2f9a13b314ab60fda6b28620d6f04dd3fb3ca992509ee64c7c7e503',
        'b10e1732683f4590814595d37bbbc6b956d25d7647d9f6e8daf5c62e35898724',
        '45b458a075c8906b94e09a5b2bf9f9920cd7586e2642d2bb210d817e8ec7e656',
        '00ea1783aea41e0875c043f00bc85bffa7418f744e12060671baa6ff55150905',
        'd30ff1730699536a7dcfcc76ac026a2744bc10a07ea028d01cc5cfea5ddd05ad'
    )
    if ($digestParts.Count -ne $expectedDigests.Count) {
        throw "Independent Board OAuth refresh-security digest verifier returned an invalid field count: '$digestResponse'."
    }
    for ($index = 0; $index -lt $expectedDigests.Count; $index++) {
        if (-not [string]::Equals(
                $digestParts[$index], $expectedDigests[$index],
                [StringComparison]::Ordinal)) {
            throw "Independent Board OAuth refresh-security metadata digest drifted at field $($index + 1): '$($digestParts[$index])'."
        }
    }
    Write-Host "PASS Independent Board OAuth refresh-security exact S3 current-read on MySQL Community $($serverProfile.Version) (strict NULL pairing, receipt-v2 columns/generated slots/indexes/FKs/CHECKs, exact lock-order support, immutable triggers, data lineage and helper cleanup)."
}

function Assert-PublicDatabaseManifestCurrentState {
    $publicReceiptCount = [int](Invoke-MySqlText -Sql "SELECT COUNT(*) FROM u3w_schema_migration WHERE version LIKE 'public_init_%';")
    if ($publicReceiptCount -ne $steps.Count) {
        throw "Public database manifest current-read expected exactly $($steps.Count) receipts; found $publicReceiptCount."
    }
    foreach ($step in $steps) {
        $escapedVersion = $step.Version.Replace("'", "''")
        $state = Invoke-MySqlText -Sql "SELECT description FROM u3w_schema_migration WHERE version='$escapedVersion' LIMIT 1;"
        $expectedAppliedState = "APPLIED:$($step.Description)"
        if (-not [string]::Equals($state, $expectedAppliedState, [StringComparison]::Ordinal)) {
            throw "Public database manifest current-read drift for $($step.Version): expected '$expectedAppliedState', found '$state'."
        }
    }
    Write-Host "PASS public database manifest exact current-read ($($steps.Count) APPLIED receipts with exact descriptions)."
}

if ($CurrentReadOnly) {
    $currentReadLockSession = $null
    $currentReadLockAcquired = $false
    try {
        $currentReadLockSession = New-MySqlSession
        $currentReadLockResponseText = Invoke-MySqlSessionText -Session $currentReadLockSession -Sql @"
SET @u3w_manifest_lock_name = SHA2(CONCAT(DATABASE(), ':public-database-manifest:v1'), 256);
SELECT GET_LOCK(@u3w_manifest_lock_name, 30);
SELECT CONCAT_WS('|', @u3w_manifest_lock_name, CHAR_LENGTH(@u3w_manifest_lock_name), IS_USED_LOCK(@u3w_manifest_lock_name), CONNECTION_ID());
"@
        $currentReadLockResponse = @($currentReadLockResponseText -split "`n")
        if ($currentReadLockResponse.Count -ne 2 -or $currentReadLockResponse[0] -ne '1') {
            throw 'Could not acquire the public database manifest lock for current-read.'
        }
        $currentReadLockAcquired = $true
        $currentReadLockProof = $currentReadLockResponse[1].Split('|')
        if ($currentReadLockProof.Count -ne 4 -or
            $currentReadLockProof[0] -notmatch '^[0-9a-f]{64}$' -or
            $currentReadLockProof[1] -ne '64' -or
            $currentReadLockProof[2] -ne $currentReadLockProof[3]) {
            throw "Public database manifest current-read lock owner verification failed: '$($currentReadLockResponse[1])'."
        }

        Assert-IndependentBoardControlPlaneCurrentState
        Assert-IndependentBoardMeMenuCurrentState
        Assert-IndependentBoardAdminMenuCurrentState
        Assert-IndependentBoardEntitlementLifecycleMenuCurrentState
        Assert-IndependentBoardConnectorBindingCurrentState
        Assert-IndependentBoardOauthFoundationCurrentState
        Assert-IndependentBoardOauthConsentIntentCurrentState
        Assert-IndependentBoardOauthRefreshSecurityCurrentState
        Assert-PublicDatabaseManifestCurrentState
        Write-Host "Independent Board current-read verification complete for '$Database'. No database write was requested."
    }
    finally {
        if ($null -ne $currentReadLockSession) {
            if ($currentReadLockAcquired) {
                $currentReadRelease = Invoke-MySqlSessionText -Session $currentReadLockSession -Sql @"
SELECT IF(IS_USED_LOCK(@u3w_manifest_lock_name) = CONNECTION_ID(), RELEASE_LOCK(@u3w_manifest_lock_name), 0);
"@
                if ($currentReadRelease -ne '1') {
                    Write-Warning "Public database manifest current-read lock release was not confirmed: '$currentReadRelease'."
                }
            }
            Stop-MySqlSession -Session $currentReadLockSession
        }
    }
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
        $expectedAppliedState = "APPLIED:$($step.Description)"
        if ($state -eq $expectedAppliedState) {
            Write-Host "SKIP $($step.Version) (already applied)"
            continue
        }
        $resumeRunningOauthProvenance =
            $step.Version -eq 'public_init_034' -and
            $state -eq "RUNNING:$($step.Description)"
        $resumeRunningOauthConsentIntent =
            $step.Version -eq 'public_init_035' -and
            $state -eq "RUNNING:$($step.Description)"
        $resumeRunningOauthRefreshSecurity =
            $step.Version -eq 'public_init_036' -and
            $state -eq "RUNNING:$($step.Description)"
        $resumeRunningOauthAdditive =
            $resumeRunningOauthProvenance -or
            $resumeRunningOauthConsentIntent -or
            $resumeRunningOauthRefreshSecurity
        if ($state -and -not $resumeRunningOauthAdditive) {
            throw "Step $($step.Version) is in state '$state'. Do not retry a partially applied DDL step; use a fresh database or reviewed recovery."
        }

        if ($step.Version -eq 'public_init_033') {
            # Canonical initialization rejects an unsupported database before it
            # records RUNNING or executes the migration file. The SQL file also
            # defends direct execution before its first persistent target table.
            $null = Assert-IndependentBoardOauthServerProfile
            # W4b creates FKs into the W1 entitlement and W4a binding contracts.
            # Verify both complete dependency surfaces before recording RUNNING or
            # executing the first implicitly committed W4b DDL statement.
            Assert-IndependentBoardControlPlaneCurrentState
            Assert-IndependentBoardConnectorBindingCurrentState
        }
        if ($step.Version -eq 'public_init_034') {
            # The additive provenance migration owns exact 033/successor shape,
            # internal RUNNING/APPLIED and interrupted-DDL recovery checks. Keep
            # the canonical runner's preflight to the exact server allowlist so
            # an internal RUNNING receipt remains recoverable by the SQL file.
            $null = Assert-IndependentBoardOauthServerProfile
        }
        if ($step.Version -eq 'public_init_035') {
            # The consent-intent migration owns exact 033 -> 034 predecessor
            # receipt/shape checks and its three interrupted-DDL recovery stages.
            # Keep RUNNING recoverable and reject unsupported server profiles
            # before the public runner creates or changes its receipt.
            $null = Assert-IndependentBoardOauthServerProfile
        }
        if ($step.Version -eq 'public_init_036') {
            # Reject an unsupported build before the public RUNNING receipt.
            # The SQL migration owns exact S0-S3 and the bounded internal S2
            # token-only and token+receipt support-prefix recovery contract.
            $null = Assert-IndependentBoardOauthServerProfile
        }

        try {
            if (-not $resumeRunningOauthAdditive) {
                Invoke-MySqlText -Sql "INSERT INTO u3w_schema_migration(version, description) VALUES ('$($step.Version)', 'RUNNING:$($step.Description)');" | Out-Null
            }
            else {
                Write-Warning "Resuming $($step.Version) from its exact public RUNNING receipt; the migration will fail closed unless its internal state and DDL shape are recoverable."
            }
            Write-Host "APPLY $($step.Version): $($step.File.Name)"
            if ($step.Version -eq 'public_init_001') {
                Invoke-MySqlBytes -Bytes (Get-BaseSchemaBytesForTargetDatabase -File $step.File) | Out-Null
            }
            else {
                Invoke-MySqlFile -File $step.File
            }
            if ($step.Version -eq 'public_init_034') {
                # Promote the public manifest receipt only after a separate,
                # successor-aware full metadata current-read has passed.
                Assert-IndependentBoardOauthFoundationCurrentState
            }
            if ($step.Version -eq 'public_init_035') {
                # Preserve the internal/public receipt boundary: the SQL file
                # completes its internal APPLIED receipt first; the public
                # receipt is promoted only after independent read-only audits.
                Assert-IndependentBoardOauthFoundationCurrentState
                Assert-IndependentBoardOauthConsentIntentCurrentState
            }
            if ($step.Version -eq 'public_init_036') {
                # The internal receipt is complete before the public receipt is
                # promoted, and this independent read proves exact S3 plus data.
                Assert-IndependentBoardOauthRefreshSecurityCurrentState
            }
            Invoke-MySqlText -Sql "UPDATE u3w_schema_migration SET description='APPLIED:$($step.Description)', applied_at=CURRENT_TIMESTAMP WHERE version='$($step.Version)';" | Out-Null
        }
        catch {
            $migrationFailure = $_
            if ($step.Version -eq 'public_init_032') {
                try {
                    # The W4a migration commits its internal receipt before best-effort
                    # lock/procedure cleanup. Reconcile only from a complete, exact
                    # current-read; any earlier or drifted state still fails closed.
                    Assert-IndependentBoardConnectorBindingCurrentState
                    Invoke-MySqlText -Sql @"
DROP PROCEDURE IF EXISTS u3w_migrate_independent_board_connector_binding_20260721;
DROP PROCEDURE IF EXISTS u3w_finalize_independent_board_connector_binding_20260721;
"@ | Out-Null
                    Invoke-MySqlText -Sql "UPDATE u3w_schema_migration SET description='APPLIED:$($step.Description)', applied_at=CURRENT_TIMESTAMP WHERE version='$($step.Version)';" | Out-Null
                    Write-Warning "Reconciled $($step.Version) from its exact committed current state after a post-commit runner error."
                    continue
                }
                catch {
                    Write-Warning "W4a exact post-commit reconciliation did not pass; recording FAILED."
                }
            }
            if ($step.Version -eq 'public_init_033') {
                try {
                    # W4b follows the same prepare/trigger/finalize pattern as
                    # W4a. Reconcile only an exact committed six-table state.
                    Assert-IndependentBoardOauthFoundationCurrentState
                    Invoke-MySqlText -Sql @"
DROP PROCEDURE IF EXISTS u3w_migrate_independent_board_oauth_foundation_20260721;
DROP PROCEDURE IF EXISTS u3w_finalize_independent_board_oauth_foundation_20260721;
"@ | Out-Null
                    Invoke-MySqlText -Sql "UPDATE u3w_schema_migration SET description='APPLIED:$($step.Description)', applied_at=CURRENT_TIMESTAMP WHERE version='$($step.Version)';" | Out-Null
                    Write-Warning "Reconciled $($step.Version) from its exact committed current state after a post-commit runner error."
                    continue
                }
                catch {
                    Write-Warning "W4b exact post-commit reconciliation did not pass; recording FAILED."
                }
            }
            if ($step.Version -eq 'public_init_034') {
                try {
                    # One bounded replay lets the migration reconcile an exact
                    # internal RUNNING receipt with either pre-DDL or complete
                    # post-DDL metadata. It rejects partial/orphaned shapes.
                    Invoke-MySqlFile -File $step.File
                    Assert-IndependentBoardOauthFoundationCurrentState
                    Invoke-MySqlText -Sql @"
DROP PROCEDURE IF EXISTS u3w_migrate_independent_board_oauth_receipt_provenance_20260721;
"@ | Out-Null
                    Invoke-MySqlText -Sql "UPDATE u3w_schema_migration SET description='APPLIED:$($step.Description)', applied_at=CURRENT_TIMESTAMP WHERE version='$($step.Version)';" | Out-Null
                    Write-Warning "Reconciled $($step.Version) from its exact committed successor state after one bounded replay."
                    continue
                }
                catch {
                    Write-Warning "W4b provenance exact bounded replay did not pass; recording FAILED."
                }
            }
            if ($step.Version -eq 'public_init_035') {
                try {
                    # One bounded replay lets the migration resume only an exact
                    # request -> code -> family -> trigger prefix. Its own raw
                    # metadata and legacy-data gates reject every other shape.
                    Invoke-MySqlFile -File $step.File
                    Assert-IndependentBoardOauthFoundationCurrentState
                    Assert-IndependentBoardOauthConsentIntentCurrentState
                    Invoke-MySqlText -Sql @"
DROP PROCEDURE IF EXISTS u3w_migrate_ib_oauth_consent_intent_20260721;
DROP PROCEDURE IF EXISTS u3w_finalize_ib_oauth_consent_intent_20260721;
DROP PROCEDURE IF EXISTS u3w_assert_ib_oauth_consent_intent_20260721;
"@ | Out-Null
                    Invoke-MySqlText -Sql "UPDATE u3w_schema_migration SET description='APPLIED:$($step.Description)', applied_at=CURRENT_TIMESTAMP WHERE version='$($step.Version)';" | Out-Null
                    Write-Warning "Reconciled $($step.Version) from its exact committed consent-intent successor state after one bounded replay."
                    continue
                }
                catch {
                    Write-Warning "W4b consent-intent exact bounded replay did not pass; recording FAILED."
                }
            }
            if ($step.Version -eq 'public_init_036') {
                try {
                    # One bounded replay may resume S0, S1, either exact internal
                    # S2 support prefix, external S2 or complete S3. The migration
                    # rejects every other partial or drifted shape.
                    Invoke-MySqlFile -File $step.File
                    Assert-IndependentBoardOauthRefreshSecurityCurrentState
                    Invoke-MySqlText -Sql @"
DROP PROCEDURE IF EXISTS u3w_migrate_ib_oauth_refresh_security_20260721;
DROP PROCEDURE IF EXISTS u3w_assert_ib_oauth_refresh_security_20260721;
"@ | Out-Null
                    Invoke-MySqlText -Sql "UPDATE u3w_schema_migration SET description='APPLIED:$($step.Description)', applied_at=CURRENT_TIMESTAMP WHERE version='$($step.Version)';" | Out-Null
                    Write-Warning "Reconciled $($step.Version) from its exact committed refresh-security S3 state after one bounded replay."
                    continue
                }
                catch {
                    Write-Warning "W4b refresh-security exact bounded replay did not pass; recording FAILED."
                }
            }
            try {
                Invoke-MySqlText -Sql "UPDATE u3w_schema_migration SET description='FAILED:$($step.Description)', applied_at=CURRENT_TIMESTAMP WHERE version='$($step.Version)';" | Out-Null
            }
            catch {
                Write-Warning "Could not record the FAILED state."
            }
            throw $migrationFailure
        }
    }

    Assert-IndependentBoardControlPlaneCurrentState
    Assert-IndependentBoardMeMenuCurrentState
    Assert-IndependentBoardAdminMenuCurrentState
    Assert-IndependentBoardEntitlementLifecycleMenuCurrentState
    Assert-IndependentBoardConnectorBindingCurrentState
    Assert-IndependentBoardOauthFoundationCurrentState
    Assert-IndependentBoardOauthConsentIntentCurrentState
    Assert-IndependentBoardOauthRefreshSecurityCurrentState

    Assert-PublicDatabaseManifestCurrentState

    $verification = [int](Invoke-MySqlText -Sql @"
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema='$Database'
  AND table_name IN ('cv_storage','fbs_api_key','fbs_bot_binding','fbs_smartbot_input_artifact','fbs_delivery_outbox','wc_webhook_delivery',
                     'fbs_truth_spine_receipt_batch','fbs_product_plan','fbs_product_entitlement','fbs_usage_budget','fbs_usage_operation','fbs_entitlement_receipt',
                      'fbs_connector_binding','fbs_connector_binding_scope','fbs_connector_binding_receipt',
                      'fbs_oauth_client','fbs_oauth_authorization_request','fbs_oauth_authorization_code',
                      'fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt');
"@)
    if ($verification -ne 21) {
        throw "Database verification failed: expected twenty-one representative current tables; found $verification."
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
