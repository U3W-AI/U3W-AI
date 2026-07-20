[CmdletBinding()]
param(
    [string]$Root = "",
    [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $Root) {
    $Root = Split-Path -Parent $PSScriptRoot
}
$resolvedRoot = (Resolve-Path -LiteralPath $Root).Path
$initScript = Join-Path $resolvedRoot "scripts\init-database.ps1"
$sqlRoot = Join-Path $resolvedRoot "sql"
if (-not (Test-Path -LiteralPath $initScript -PathType Leaf)) {
    throw "Database initializer not found: $initScript"
}
if (-not (Test-Path -LiteralPath $sqlRoot -PathType Container)) {
    throw "SQL root not found: $sqlRoot"
}

$shell = (Get-Process -Id $PID).Path
$output = & $shell -NoProfile -ExecutionPolicy Bypass -File $initScript `
    -DryRun -ManifestJson -MySqlExe "__dry_run_must_not_resolve_mysql__" 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Database manifest DryRun failed: $($output -join [Environment]::NewLine)"
}

$manifestText = ($output | ForEach-Object { $_.ToString() }) -join "`n"
try {
    $manifest = $manifestText | ConvertFrom-Json
}
catch {
    throw "Database manifest DryRun did not return valid JSON: $manifestText"
}

$errors = [System.Collections.Generic.List[string]]::new()
$managedFiles = @(Get-ChildItem -LiteralPath $sqlRoot -Filter "update_*.sql" -File | Sort-Object Name)
$manifestUpdateSteps = @($manifest.steps | Where-Object { $_.file -like "update_*.sql" })

if (-not $manifest.coverageOk) {
    $errors.Add("initializer reported coverageOk=false")
}
if (-not $manifest.dryRun -or $manifest.databaseConnectionOpened) {
    $errors.Add("DryRun database isolation contract is false")
}
if ([int]$manifest.managedUpdateSqlCount -ne $managedFiles.Count) {
    $errors.Add("managed update count mismatch: manifest=$($manifest.managedUpdateSqlCount), disk=$($managedFiles.Count)")
}

$manifestNames = @($manifestUpdateSteps | ForEach-Object { [string]$_.file })
foreach ($file in $managedFiles) {
    $occurrences = @($manifestNames | Where-Object { $_ -eq $file.Name }).Count
    if ($occurrences -ne 1) {
        $errors.Add("managed SQL must occur exactly once: $($file.Name) occurs $occurrences times")
    }
}
foreach ($name in @($manifestNames | Sort-Object -Unique)) {
    if (@($managedFiles | Where-Object Name -eq $name).Count -ne 1) {
        $errors.Add("manifest references unmanaged update SQL: $name")
    }
}

for ($index = 0; $index -lt $manifest.steps.Count; $index++) {
    $expectedVersion = "public_init_{0:D3}" -f ($index + 1)
    if ([string]$manifest.steps[$index].version -ne $expectedVersion) {
        $errors.Add("manifest version order mismatch at $($index + 1): expected $expectedVersion")
    }
}

$requiredTail = @{
    public_init_027 = "update_20260712_truth_spine_test_state_receipt.sql"
    public_init_028 = "update_20260720_independent_board_control_plane.sql"
    public_init_029 = "update_20260720_independent_board_me_menu.sql"
}
foreach ($version in $requiredTail.Keys) {
    $matches = @($manifest.steps | Where-Object { $_.version -eq $version -and $_.file -eq $requiredTail[$version] })
    if ($matches.Count -ne 1) {
        $errors.Add("required migration mapping is missing: $version -> $($requiredTail[$version])")
    }
}

$initSource = Get-Content -LiteralPath $initScript -Raw -Encoding UTF8
if ($initSource -match '\$steps\.Count\s+-ne\s+\d+' -or
    $initSource -match 'Expected\s+\d+\s+applied public initialization receipts' -or
    $initSource -match 'Database initialization complete:\s+\d+/\d+') {
    $errors.Add("initializer contains a fixed manifest-count assertion")
}
$requiredInitNeedles = @(
    "SHA2(CONCAT(DATABASE(), ':public-database-manifest:v1'), 256)",
    "CHAR_LENGTH(@u3w_manifest_lock_name)",
    "GET_LOCK(@u3w_manifest_lock_name, 30)",
    "IS_USED_LOCK(@u3w_manifest_lock_name)",
    "RELEASE_LOCK(@u3w_manifest_lock_name)",
    "--unbuffered --database=`$Database",
    "finally {",
    "Assert-IndependentBoardControlPlaneCurrentState",
    "Assert-IndependentBoardMeMenuCurrentState",
    '$expectedState = @(5, 5, 67, 67, 13, 13, 2, 2, 2, 1, 1)',
    '$expectedState = @(1, 1, 1, 1, 1, 1, 1)',
    "role_id = 10 AND role_key = 'user'",
    '[string]$Database = "wxfbsir"',
    "^[A-Za-z0-9_]+$",
    "[switch]`$CurrentReadOnly",
    "Get-BaseSchemaBytesForTargetDatabase",
    "Base schema target rewrite requires exactly one canonical CREATE DATABASE and one canonical USE statement.",
    "if (`$step.Version -eq 'public_init_001')"
)
foreach ($needle in $requiredInitNeedles) {
    if (-not $initSource.Contains($needle)) {
        $errors.Add("initializer is missing advisory-lock contract: $needle")
    }
}

$baseSchemaPath = Join-Path $sqlRoot 'wxfbsir.sql'
$baseSchemaSource = Get-Content -LiteralPath $baseSchemaPath -Raw -Encoding UTF8
$canonicalBaseCreate = 'CREATE DATABASE IF NOT EXISTS `wxfbsir` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
$canonicalBaseUse = 'USE `wxfbsir`;'
if (@([regex]::Matches($baseSchemaSource, [regex]::Escape($canonicalBaseCreate))).Count -ne 1 -or
    @([regex]::Matches($baseSchemaSource, [regex]::Escape($canonicalBaseUse))).Count -ne 1 -or
    @([regex]::Matches($baseSchemaSource, '(?im)^\s*CREATE\s+DATABASE\b')).Count -ne 1 -or
    @([regex]::Matches($baseSchemaSource, '(?im)^\s*USE\s+`?[^`;\s]+`?\s*;')).Count -ne 1) {
    $errors.Add("base schema must retain exactly one reviewed wxfbsir CREATE DATABASE and USE statement for safe target rewriting")
}

$controlPlaneSqlPath = Join-Path $sqlRoot "update_20260720_independent_board_control_plane.sql"
$controlPlaneSql = Get-Content -LiteralPath $controlPlaneSqlPath -Raw -Encoding UTF8
$requiredSqlNeedles = @(
    "SHA2(",
    "CHAR_LENGTH(migration_lock_name) <> 64",
    "GET_LOCK(migration_lock_name, 30)",
    "IS_USED_LOCK(migration_lock_name)",
    "migration_lock_owner <> current_connection",
    "RELEASE_LOCK(migration_lock_name)",
    "DECLARE EXIT HANDLER FOR SQLEXCEPTION",
    "fbs_product_plan",
    "fbs_product_entitlement",
    "fbs_usage_budget",
    "fbs_usage_operation",
    "fbs_entitlement_receipt"
    "FBSIR_INDEPENDENT_BOARD"
    "BOARD_FREE"
    "BOARD_VIP"
    "daily_meeting_limit"
    "connector_verified_at"
    "uk_usage_operation_enterprise_operation"
    "ACTION_COMPLETED"
    "IF migration_exists = 0 THEN"
    "target_total_column_count <> 67"
    "target_total_unique_index_columns <> 13"
    "target_product_plan_count <> 2"
    "target_internal_receipt_count <> 1"
)
foreach ($needle in $requiredSqlNeedles) {
    if (-not $controlPlaneSql.Contains($needle)) {
        $errors.Add("control-plane SQL is missing required contract: $needle")
    }
}

$meMenuSqlPath = Join-Path $sqlRoot "update_20260720_independent_board_me_menu.sql"
$meMenuSql = Get-Content -LiteralPath $meMenuSqlPath -Raw -Encoding UTF8
$requiredMeMenuNeedles = @(
    "SHA2(",
    "CHAR_LENGTH(migration_lock_name) <> 64",
    "GET_LOCK(migration_lock_name, 30)",
    "IS_USED_LOCK(migration_lock_name)",
    "RELEASE_LOCK(migration_lock_name)",
    "DECLARE EXIT HANDLER FOR SQLEXCEPTION",
    "START TRANSACTION",
    "COMMIT",
    "IF migration_exists = 0 THEN",
    "target_identity_count <> 0",
    "business/independentBoard/me/index",
    "my:independent-board:view",
    "IndependentBoardMe",
    'WHERE `role_key` = ''user''',
    'target_user_role_id <> 10',
    'AND role_row.`role_id` = target_user_role_id',
    "same named lock and use one transaction",
    "delete only the exact version+description receipt",
    "leaving its receipt behind",
    "table_type = 'BASE TABLE'",
    "engine = 'InnoDB'",
    'INSERT INTO `sys_role_menu`',
    "20260720_independent_board_me_menu_v1",
    "ordinary-user role binding"
)
foreach ($needle in $requiredMeMenuNeedles) {
    if (-not $meMenuSql.Contains($needle)) {
        $errors.Add("Independent Board me menu SQL is missing required contract: $needle")
    }
}
if ($meMenuSql -match '(?im)^\s*DELETE\s+FROM\s+`?sys_menu`?' -or
    $meMenuSql -match '(?im)^\s*UPDATE\s+`?sys_menu`?') {
    $errors.Add("Independent Board me menu migration must not delete or repair an existing menu row")
}
$menuInsertIndex = $meMenuSql.IndexOf('INSERT INTO `sys_menu`', [StringComparison]::Ordinal)
$roleBindingInsertIndex = $meMenuSql.IndexOf('INSERT INTO `sys_role_menu`', [StringComparison]::Ordinal)
$menuReceiptInsertIndex = $meMenuSql.IndexOf('INSERT INTO `u3w_schema_migration`', [StringComparison]::Ordinal)
$menuStateAuditIndex = $meMenuSql.IndexOf('SELECT COUNT(*), MIN(`menu_id`)', [StringComparison]::Ordinal)
$roleBindingAuditIndex = $meMenuSql.IndexOf('SELECT COUNT(*) INTO user_role_binding_count', [StringComparison]::Ordinal)
$receiptAuditIndex = $meMenuSql.IndexOf('SELECT COUNT(*) INTO exact_receipt_count', [StringComparison]::Ordinal)
$menuLockReleaseIndex = $meMenuSql.LastIndexOf('SELECT RELEASE_LOCK(migration_lock_name)', [StringComparison]::Ordinal)
$menuCommitIndex = $meMenuSql.LastIndexOf('COMMIT;', [StringComparison]::Ordinal)
$menuProcedureEndIndex = $meMenuSql.IndexOf('END$$', [StringComparison]::Ordinal)
if ($menuInsertIndex -lt 0 -or
    $roleBindingInsertIndex -le $menuInsertIndex -or
    $menuReceiptInsertIndex -le $roleBindingInsertIndex -or
    $menuStateAuditIndex -le $menuReceiptInsertIndex -or
    $roleBindingAuditIndex -le $menuStateAuditIndex -or
    $receiptAuditIndex -le $roleBindingAuditIndex -or
    $menuLockReleaseIndex -le $receiptAuditIndex -or
    $menuCommitIndex -le $menuLockReleaseIndex -or
    $menuProcedureEndIndex -le $menuCommitIndex) {
    $errors.Add("Independent Board me menu migration must audit menu, role binding and receipt before releasing its lock and committing")
}
if ($menuCommitIndex -ge 0 -and $menuProcedureEndIndex -gt $menuCommitIndex) {
    $postCommitBody = $meMenuSql.Substring($menuCommitIndex + 'COMMIT;'.Length,
        $menuProcedureEndIndex - ($menuCommitIndex + 'COMMIT;'.Length))
    if ($postCommitBody -match '(?i)\bSIGNAL\b') {
        $errors.Add("Independent Board me menu migration must not contain a failing state assertion after COMMIT")
    }
}

$truthSpineSqlPath = Join-Path $sqlRoot 'update_20260712_truth_spine_test_state_receipt.sql'
$truthSpineSql = Get-Content -LiteralPath $truthSpineSqlPath -Raw -Encoding UTF8
$requiredTruthSpineLockNeedles = @(
    "SET migration_lock_name = SHA2(CONCAT(DATABASE(), ':20260712_truth_spine_test_state_v1'), 256)",
    'CHAR_LENGTH(migration_lock_name) <> 64',
    'GET_LOCK(migration_lock_name, 30)',
    'IS_USED_LOCK(migration_lock_name)',
    'migration_lock_owner <> current_connection',
    'RELEASE_LOCK(migration_lock_name)',
    'DECLARE EXIT HANDLER FOR SQLEXCEPTION',
    'migration_lock_owner = CONNECTION_ID()'
)
foreach ($needle in $requiredTruthSpineLockNeedles) {
    if (-not $truthSpineSql.Contains($needle)) {
        $errors.Add("Truth Spine migration is missing stable advisory-lock semantics: $needle")
    }
}
if ($truthSpineSql -match 'GET_LOCK\s*\(\s*CONCAT\s*\(\s*DATABASE\(\)' -or
    $truthSpineSql -match 'RELEASE_LOCK\s*\(\s*CONCAT\s*\(\s*DATABASE\(\)') {
    $errors.Add("Truth Spine migration must not use the raw database name as an advisory-lock name")
}
if ($controlPlaneSql.Contains('ON DUPLICATE KEY UPDATE')) {
    $errors.Add("control-plane completion rerun must not contain an ON DUPLICATE repair path")
}
if ($controlPlaneSql.Contains('CREATE TABLE IF NOT EXISTS `u3w_schema_migration`')) {
    $errors.Add("control-plane migration must not issue migration-ledger DDL before checking its internal receipt")
}

$applyGuardNeedle = 'IF migration_exists = 0 THEN'
$applyGuardIndex = $controlPlaneSql.IndexOf($applyGuardNeedle, [StringComparison]::Ordinal)
$firstTargetCreateIndex = $controlPlaneSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_product_plan`', [StringComparison]::Ordinal)
$seedInsertIndex = $controlPlaneSql.IndexOf('INSERT INTO `fbs_product_plan`', [StringComparison]::Ordinal)
$applyGuardEndIndex = if ($seedInsertIndex -ge 0) {
    $controlPlaneSql.IndexOf('END IF;', $seedInsertIndex, [StringComparison]::Ordinal)
} else { -1 }
$completionAuditIndex = if ($applyGuardEndIndex -ge 0) {
    $controlPlaneSql.IndexOf('SELECT COUNT(*) INTO target_total_column_count', $applyGuardEndIndex, [StringComparison]::Ordinal)
} else { -1 }
$receiptGuardIndex = if ($applyGuardEndIndex -ge 0) {
    $controlPlaneSql.IndexOf($applyGuardNeedle, $applyGuardEndIndex + 1, [StringComparison]::Ordinal)
} else { -1 }
$receiptInsertIndex = $controlPlaneSql.IndexOf('INSERT INTO `u3w_schema_migration`', [StringComparison]::Ordinal)
$receiptGuardEndIndex = if ($receiptInsertIndex -ge 0) {
    $controlPlaneSql.IndexOf('END IF;', $receiptInsertIndex, [StringComparison]::Ordinal)
} else { -1 }
if ($applyGuardIndex -lt 0 -or $firstTargetCreateIndex -le $applyGuardIndex -or
    $seedInsertIndex -le $firstTargetCreateIndex -or $applyGuardEndIndex -le $seedInsertIndex -or
    $completionAuditIndex -le $applyGuardEndIndex) {
    $errors.Add("control-plane target DDL and seed must be enclosed by the migration_exists=0 guard, followed by completion-state audit")
}
if ($receiptGuardIndex -le $applyGuardEndIndex -or $receiptInsertIndex -le $receiptGuardIndex -or
    $receiptGuardEndIndex -le $receiptInsertIndex) {
    $errors.Add("control-plane internal migration receipt insert must be enclosed by its migration_exists=0 guard")
}
$targetCreateMatches = @([regex]::Matches($controlPlaneSql, 'CREATE TABLE IF NOT EXISTS `fbs_[a-z_]+`'))
if ($targetCreateMatches.Count -ne 5 -or @($targetCreateMatches | Where-Object {
    $_.Index -le $applyGuardIndex -or $_.Index -ge $applyGuardEndIndex
}).Count -ne 0) {
    $errors.Add("all five target CREATE TABLE statements must be confined to the first-apply guard")
}
$lastCurrentReadCall = $initSource.LastIndexOf('Assert-IndependentBoardControlPlaneCurrentState', [StringComparison]::Ordinal)
$lastMeMenuCurrentReadCall = $initSource.LastIndexOf('Assert-IndependentBoardMeMenuCurrentState', [StringComparison]::Ordinal)
$manifestLoopIndex = $initSource.IndexOf('foreach ($step in $steps)', [StringComparison]::Ordinal)
if ($lastCurrentReadCall -le $manifestLoopIndex) {
    $errors.Add("initializer must run the Independent Board current-read audit after the complete manifest loop")
}
if ($lastMeMenuCurrentReadCall -le $manifestLoopIndex) {
    $errors.Add("initializer must run the Independent Board me menu current-read audit after the complete manifest loop")
}

$liveVerifierPath = Join-Path $resolvedRoot 'scripts\verify-independent-board-live-database.ps1'
if (-not (Test-Path -LiteralPath $liveVerifierPath -PathType Leaf)) {
    $errors.Add("Independent Board live database verifier is missing")
}
else {
    $liveVerifierSource = Get-Content -LiteralPath $liveVerifierPath -Raw -Encoding UTF8
    $requiredLiveNeedles = @(
        '[Parameter(Mandatory = $true)][string]$Database',
        'first_apply',
        'completed_rerun',
        'readonly_current_read',
        '-CurrentReadOnly',
        'Static verification is not accepted as a substitute',
        'u3w_scratch_board_001',
        'preexistingTableCount',
        'first-apply proof requires an absent or empty scratch database'
    )
    foreach ($needle in $requiredLiveNeedles) {
        if (-not $liveVerifierSource.Contains($needle)) {
            $errors.Add("live database verifier is missing required gate semantics: $needle")
        }
    }
}

$expectedTableColumns = [ordered]@{
    fbs_product_plan = @(
        'id','product_code','plan_code','plan_name','vip','connector_required','daily_meeting_limit',
        'agenda_limit','seat_limit','secretary_enabled','status','version','created_at','updated_at'
    )
    fbs_product_entitlement = @(
        'id','enterprise_id','member_id','user_id','product_code','plan_code','status','connector_binding_id',
        'connector_verified_at','valid_from','valid_until','version','created_at','updated_at'
    )
    fbs_usage_budget = @(
        'id','enterprise_id','member_id','product_code','metric_code','bucket_date','daily_limit',
        'reserved_count','used_count','version','created_at','updated_at'
    )
    fbs_usage_operation = @(
        'id','operation_id','request_digest','enterprise_id','member_id','user_id','product_code','metric_code',
        'bucket_date','units','status','effective_plan_code','agenda_count','seat_count','remaining_count',
        'created_at','updated_at','completed_at'
    )
    fbs_entitlement_receipt = @(
        'id','receipt_id','enterprise_id','actor_user_id','target_member_id','action','payload_digest',
        'evidence_level','created_at'
    )
}
foreach ($tableName in $expectedTableColumns.Keys) {
    $escapedTableName = [regex]::Escape($tableName)
    $tableMatch = [regex]::Match(
        $controlPlaneSql,
        "CREATE TABLE IF NOT EXISTS ``$escapedTableName``\s*\((?<body>[\s\S]*?)\) ENGINE=InnoDB",
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if (-not $tableMatch.Success) {
        $errors.Add("control-plane SQL table body is missing: $tableName")
        continue
    }
    $actualColumns = @([regex]::Matches($tableMatch.Groups['body'].Value, '(?m)^\s*`(?<name>[^`]+)`\s+') |
        ForEach-Object { $_.Groups['name'].Value })
    $expectedColumns = @($expectedTableColumns[$tableName])
    $missingColumns = @($expectedColumns | Where-Object { $actualColumns -notcontains $_ })
    $unexpectedColumns = @($actualColumns | Where-Object { $expectedColumns -notcontains $_ })
    $duplicateColumns = @($actualColumns | Group-Object | Where-Object Count -ne 1)
    if ($missingColumns.Count -gt 0 -or $unexpectedColumns.Count -gt 0 -or $duplicateColumns.Count -gt 0) {
        $errors.Add("column contract mismatch for $tableName (missing=$($missingColumns -join ','), unexpected=$($unexpectedColumns -join ','), duplicates=$($duplicateColumns.Name -join ','))")
    }
}

$result = [pscustomobject]@{
    schemaVersion = 1
    ok = ($errors.Count -eq 0)
    root = $resolvedRoot
    manifestStepCount = [int]$manifest.manifestStepCount
    managedUpdateSqlCount = $managedFiles.Count
    dryRunDatabaseIsolation = [bool]($manifest.dryRun -and -not $manifest.databaseConnectionOpened)
    truthSpineVersion = "public_init_027"
    controlPlaneVersion = "public_init_028"
    meMenuVersion = "public_init_029"
    errors = @($errors)
}

if ($Json) {
    $result | ConvertTo-Json -Depth 5
}
else {
    if ($result.ok) {
        Write-Host "PASS database manifest: $($result.manifestStepCount) steps; $($result.managedUpdateSqlCount) managed update SQL files covered exactly once."
    }
    else {
        $result.errors | ForEach-Object { Write-Error $_ }
    }
}

if (-not $result.ok) {
    exit 1
}
