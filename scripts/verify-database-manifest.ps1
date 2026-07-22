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
$declarativeManifestPath = Join-Path $sqlRoot "init-manifest.json"
if (-not (Test-Path -LiteralPath $initScript -PathType Leaf)) {
    throw "Database initializer not found: $initScript"
}
if (-not (Test-Path -LiteralPath $sqlRoot -PathType Container)) {
    throw "SQL root not found: $sqlRoot"
}
if (-not (Test-Path -LiteralPath $declarativeManifestPath -PathType Leaf)) {
    throw "Declarative database manifest not found: $declarativeManifestPath"
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
$mysqlTransactionItRunnerPath = Join-Path $resolvedRoot 'scripts\run-independent-board-mysql-transaction-it.ps1'
if (-not (Test-Path -LiteralPath $mysqlTransactionItRunnerPath -PathType Leaf)) {
    $errors.Add('Independent Board MySQL transaction runner is missing')
    $mysqlTransactionItRunnerSource = ''
}
else {
    $mysqlTransactionItRunnerSource = Get-Content -LiteralPath $mysqlTransactionItRunnerPath -Raw -Encoding UTF8
}
$managedFiles = @(Get-ChildItem -LiteralPath $sqlRoot -Filter "update_*.sql" -File | Sort-Object Name)
$manifestUpdateSteps = @($manifest.steps | Where-Object { $_.file -like "update_*.sql" })
$manifestManualMigrations = @($manifest.manualMigrations)
try {
    $declarativeManifest = Get-Content -LiteralPath $declarativeManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
catch {
    $errors.Add("declarative database manifest is invalid JSON: $($_.Exception.Message)")
    $declarativeManifest = $null
}

if ($null -ne $declarativeManifest) {
    if ([string]$declarativeManifest.schema -ne 'fbsir.public-database-init-manifest/v1') {
        $errors.Add("declarative database manifest schema is unsupported: $($declarativeManifest.schema)")
    }
    $declaredSteps = @($declarativeManifest.steps)
    if ($declaredSteps.Count -ne $manifest.steps.Count) {
        $errors.Add("declarative and executable manifest step counts differ: declared=$($declaredSteps.Count), executable=$($manifest.steps.Count)")
    }
    else {
        for ($index = 0; $index -lt $manifest.steps.Count; $index++) {
            $declared = $declaredSteps[$index]
            $executable = $manifest.steps[$index]
            if ([string]$declared.version -ne [string]$executable.version -or
                [string]$declared.description -ne [string]$executable.description -or
                [string]$declared.file -ne [string]$executable.file) {
                $errors.Add("declarative manifest drift at position $($index + 1)")
            }
        }
    }
    $declaredManualMigrations = @($declarativeManifest.manualMigrations)
    if ($declaredManualMigrations.Count -ne $manifestManualMigrations.Count) {
        $errors.Add("declarative and executable manual migration counts differ: declared=$($declaredManualMigrations.Count), executable=$($manifestManualMigrations.Count)")
    }
    else {
        for ($index = 0; $index -lt $manifestManualMigrations.Count; $index++) {
            $declared = $declaredManualMigrations[$index]
            $executable = $manifestManualMigrations[$index]
            if ([string]$declared.id -cne [string]$executable.id -or
                [string]$declared.description -cne [string]$executable.description -or
                [string]$declared.file -cne [string]$executable.file -or
                [string]$declared.execution -cne [string]$executable.execution -or
                [bool]$declared.defaultApplied -ne [bool]$executable.defaultApplied -or
                [string]$declared.optInSessionVariable -cne [string]$executable.optInSessionVariable -or
                [int]$declared.requiredValue -ne [int]$executable.requiredValue -or
                [string]$declared.sha256 -cne [string]$executable.sha256) {
                $errors.Add("declarative manual migration drift at position $($index + 1)")
            }
        }
    }
}
if ([string]$manifest.manifestFile -ne 'sql/init-manifest.json' -or
    [string]$manifest.manifestSchema -ne 'fbsir.public-database-init-manifest/v1') {
    $errors.Add("initializer DryRun did not bind the declarative manifest identity")
}

$strictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)
foreach ($step in @($manifest.steps) + @($manifestManualMigrations)) {
    $managedSqlPath = Join-Path $sqlRoot ([string]$step.file)
    try {
        $null = $strictUtf8.GetString([System.IO.File]::ReadAllBytes($managedSqlPath))
    }
    catch {
        $errors.Add("managed SQL is not strict UTF-8: $($step.file)")
    }
}

if (-not $manifest.coverageOk) {
    $errors.Add("initializer reported coverageOk=false")
}
if (-not $manifest.dryRun -or $manifest.databaseConnectionOpened) {
    $errors.Add("DryRun database isolation contract is false")
}
if ([int]$manifest.managedUpdateSqlCount -ne $managedFiles.Count) {
    $errors.Add("managed update count mismatch: manifest=$($manifest.managedUpdateSqlCount), disk=$($managedFiles.Count)")
}

$managedCatalogEntries = @($manifestUpdateSteps) + @($manifestManualMigrations)
$manifestNames = @($managedCatalogEntries | ForEach-Object { [string]$_.file })
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

$candidateMenuMigrationId = 'candidate_20260721_independent_board_portal_menu_v1'
$candidateMenuMigrationFile = 'update_20260721_independent_board_portal_candidate_menu.sql'
$candidateMenuMigrationSha256 = '3c53215020633977eff196cb12025f65f663c90b5e3d5d74239b124f32224459'
$candidateMenuEntries = @($manifestManualMigrations | Where-Object {
    [string]$_.id -ceq $candidateMenuMigrationId -and
    [string]$_.file -ceq $candidateMenuMigrationFile
})
if ($candidateMenuEntries.Count -ne 1) {
    $errors.Add('default-off candidate menu migration must occur exactly once in manualMigrations')
}
else {
    $candidateMenuEntry = $candidateMenuEntries[0]
    if ([string]$candidateMenuEntry.execution -cne 'manual_opt_in' -or
        [bool]$candidateMenuEntry.defaultApplied -or
        [string]$candidateMenuEntry.optInSessionVariable -cne '@u3w_enable_independent_board_w4b2c_candidate' -or
        [int]$candidateMenuEntry.requiredValue -ne 1 -or
        [string]$candidateMenuEntry.sha256 -cne $candidateMenuMigrationSha256) {
        $errors.Add('default-off candidate menu manual migration contract drifted')
    }
}
if (@($manifest.steps | Where-Object { [string]$_.file -ceq $candidateMenuMigrationFile }).Count -ne 0) {
    $errors.Add('default-off candidate menu migration must not be an executable public_init step')
}
$candidateMenuPath = Join-Path $sqlRoot $candidateMenuMigrationFile
if (Test-Path -LiteralPath $candidateMenuPath -PathType Leaf) {
    $actualCandidateMenuSha256 = (Get-FileHash -LiteralPath $candidateMenuPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualCandidateMenuSha256 -cne $candidateMenuMigrationSha256) {
        $errors.Add("default-off candidate menu migration byte contract drifted: expected SHA-256 $candidateMenuMigrationSha256, found $actualCandidateMenuSha256")
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
    public_init_030 = "update_20260720_independent_board_admin_menu.sql"
    public_init_031 = "update_20260720_independent_board_entitlement_lifecycle_menu.sql"
    public_init_032 = "update_20260721_independent_board_connector_binding.sql"
    public_init_033 = "update_20260721_independent_board_oauth_foundation.sql"
    public_init_034 = "update_20260721_independent_board_oauth_receipt_provenance.sql"
    public_init_035 = "update_20260721_independent_board_oauth_consent_intent_lineage.sql"
    public_init_036 = "update_20260721_independent_board_oauth_refresh_security.sql"
    public_init_037 = "update_20260722_independent_board_attribution_evidence_contract.sql"
    public_init_038 = "update_20260722_independent_board_credit_ledger.sql"
}
foreach ($version in $requiredTail.Keys) {
    $matches = @($manifest.steps | Where-Object { $_.version -eq $version -and $_.file -eq $requiredTail[$version] })
    if ($matches.Count -ne 1) {
        $errors.Add("required migration mapping is missing: $version -> $($requiredTail[$version])")
    }
}

$initSource = Get-Content -LiteralPath $initScript -Raw -Encoding UTF8
if ($initSource -match '[^\x00-\x7F]') {
    $errors.Add('initializer must remain ASCII-only for Windows PowerShell 5 compatibility')
}
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
    "Assert-IndependentBoardAdminMenuCurrentState",
    "Assert-IndependentBoardEntitlementLifecycleMenuCurrentState",
    "Assert-IndependentBoardCreditLedgerCurrentState",
    '$expectedState = @(5, 5, 67, 67, 13, 13, 2, 2, 2, 1, 1)',
    '$expectedState = @(1, 1, 1, 1, 1, 1, 1)',
    '# The four W3a identities are asserted individually above.',
    'Independent Board entitlement lifecycle menu current-read audit',
    '$expectedState = @(1, 1, 1, 1, 2, 1, 1, 1)',
    "role_id = 10 AND role_key = 'user'",
    'fbsir.public-database-init-manifest/v1',
    'init-manifest.json',
    '[string]$Database = "wxfbsir"',
    "^[A-Za-z0-9_]+$",
    "[switch]`$CurrentReadOnly",
    "Get-BaseSchemaBytesForTargetDatabase",
    "Base schema target rewrite requires exactly one canonical CREATE DATABASE and one canonical USE statement.",
    'Managed SQL must be strict UTF-8:',
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

$adminMenuSqlPath = Join-Path $sqlRoot "update_20260720_independent_board_admin_menu.sql"
$adminMenuSql = Get-Content -LiteralPath $adminMenuSqlPath -Raw -Encoding UTF8
$requiredAdminMenuNeedles = @(
    "SHA2(",
    "CHAR_LENGTH(migration_lock_name) <> 64",
    "GET_LOCK(migration_lock_name, 30)",
    "IS_USED_LOCK(migration_lock_name)",
    "migration_lock_owner <> current_connection",
    "RELEASE_LOCK(migration_lock_name)",
    "DECLARE EXIT HANDLER FOR SQLEXCEPTION",
    "START TRANSACTION",
    "COMMIT",
    "IF migration_exists = 0 THEN",
    "target_identity_count <> 0",
    "target_identity_count <> 4",
    "LAST_INSERT_ID()",
    "IndependentBoardAdmin",
    "IndependentBoardEntitlementGovernance",
    "IndependentBoardMeetingAudit",
    "business/independentBoard/admin/entitlement/index",
    "business/independentBoard/admin/meetingAudit/index",
    "board:entitlement:query",
    "board:entitlement:grant",
    "board:operation:audit",
    "20260720_independent_board_admin_menu_v1",
    "Independent Board administration directory, entitlement governance and meeting audit menus",
    "table_type = 'BASE TABLE'",
    "engine = 'InnoDB'",
    "public manifest lock first and then",
    "refuse unknown children or",
    "any sys_role_menu references",
    "Never roll back",
    "semantic role_key",
    "named lock remains held",
    "read-only completion-state audit",
    "must never be cleared or replayed automatically"
)
foreach ($needle in $requiredAdminMenuNeedles) {
    if (-not $adminMenuSql.Contains($needle)) {
        $errors.Add("Independent Board admin menu SQL is missing required contract: $needle")
    }
}
if ($adminMenuSql -match '(?im)^\s*(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM)\s+`?sys_role_menu`?\b') {
    $errors.Add("Independent Board admin menu migration must not create, update or delete role bindings")
}
if ($adminMenuSql.Contains('board:entitlement:revoke') -or
    $adminMenuSql.Contains('business/independentBoard/admin/entitlementReceipt/index') -or
    $adminMenuSql.Contains('IndependentBoardEntitlementReceipts')) {
    $errors.Add("Independent Board W3a admin menu migration must not absorb W3b lifecycle identities")
}
if ($adminMenuSql.Contains("'IndependentBoardMe'") -or
    $adminMenuSql.Contains('business/independentBoard/me/index') -or
    $adminMenuSql.Contains('my:independent-board:view')) {
    $errors.Add("Independent Board admin menu migration must not absorb or rewrite the W2 user menu identity")
}
if ($adminMenuSql -match '(?im)^\s*(?:DELETE\s+FROM|UPDATE)\s+`?sys_menu`?' -or
    $adminMenuSql.Contains('ON DUPLICATE KEY UPDATE')) {
    $errors.Add("Independent Board admin menu migration must fail closed instead of repairing menu rows")
}
$adminMenuInserts = @([regex]::Matches(
    $adminMenuSql,
    'INSERT\s+INTO\s+`sys_menu`\s*\((?<columns>[^)]*)\)',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
))
if ($adminMenuInserts.Count -ne 4) {
    $errors.Add("Independent Board admin menu migration must insert exactly four generated-id menu rows")
}
foreach ($menuInsert in $adminMenuInserts) {
    if ($menuInsert.Groups['columns'].Value -match '(?<![A-Za-z0-9_])`?menu_id`?(?![A-Za-z0-9_])') {
        $errors.Add("Independent Board admin menu migration must not insert a fixed menu_id")
    }
}
if (@([regex]::Matches($adminMenuSql, 'LAST_INSERT_ID\(\)', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)).Count -ne 4) {
    $errors.Add("Independent Board admin menu migration must capture four generated menu identifiers")
}
$adminApplyGuardIndex = $adminMenuSql.IndexOf('IF migration_exists = 0 THEN', [StringComparison]::Ordinal)
$adminStartTransactionIndex = $adminMenuSql.IndexOf('START TRANSACTION;', [StringComparison]::Ordinal)
$adminRootInsertIndex = $adminMenuSql.IndexOf("'IndependentBoardAdmin', 1, 0, 'M'", [StringComparison]::Ordinal)
$adminEntitlementInsertIndex = $adminMenuSql.IndexOf("'IndependentBoardEntitlementGovernance', 1, 0, 'C'", [StringComparison]::Ordinal)
$adminAuditInsertIndex = $adminMenuSql.IndexOf("'IndependentBoardMeetingAudit', 1, 0, 'C'", [StringComparison]::Ordinal)
$adminGrantInsertIndex = $adminMenuSql.IndexOf("'board:entitlement:grant', '#'", [StringComparison]::Ordinal)
$adminReceiptInsertIndex = $adminMenuSql.IndexOf('INSERT INTO `u3w_schema_migration`', [StringComparison]::Ordinal)
$adminStateAuditIndex = if ($adminReceiptInsertIndex -ge 0) {
    $adminMenuSql.IndexOf('SELECT COUNT(*), MIN(`menu_id`)', $adminReceiptInsertIndex, [StringComparison]::Ordinal)
} else { -1 }
$adminLockReleaseIndex = $adminMenuSql.LastIndexOf('SELECT RELEASE_LOCK(migration_lock_name)', [StringComparison]::Ordinal)
$adminCommitIndex = $adminMenuSql.LastIndexOf('COMMIT;', [StringComparison]::Ordinal)
$adminProcedureEndIndex = $adminMenuSql.IndexOf('END$$', [StringComparison]::Ordinal)
if ($adminApplyGuardIndex -lt 0 -or
    $adminStartTransactionIndex -le $adminApplyGuardIndex -or
    $adminRootInsertIndex -le $adminStartTransactionIndex -or
    $adminEntitlementInsertIndex -le $adminRootInsertIndex -or
    $adminAuditInsertIndex -le $adminEntitlementInsertIndex -or
    $adminGrantInsertIndex -le $adminAuditInsertIndex -or
    $adminReceiptInsertIndex -le $adminGrantInsertIndex -or
    $adminStateAuditIndex -le $adminReceiptInsertIndex -or
    $adminCommitIndex -le $adminStateAuditIndex -or
    $adminLockReleaseIndex -le $adminCommitIndex -or
    $adminProcedureEndIndex -le $adminLockReleaseIndex) {
    $errors.Add("Independent Board admin menu migration must write root, pages, permission and receipt, audit completion, commit while locked and only then release its lock")
}

$lifecycleMenuSqlPath = Join-Path $sqlRoot "update_20260720_independent_board_entitlement_lifecycle_menu.sql"
$lifecycleMenuSql = Get-Content -LiteralPath $lifecycleMenuSqlPath -Raw -Encoding UTF8
$requiredLifecycleMenuNeedles = @(
    "SHA2(",
    "CHAR_LENGTH(migration_lock_name) <> 64",
    "GET_LOCK(migration_lock_name, 30)",
    "IS_USED_LOCK(migration_lock_name)",
    "migration_lock_owner <> current_connection",
    "RELEASE_LOCK(migration_lock_name)",
    "DECLARE EXIT HANDLER FOR SQLEXCEPTION",
    "START TRANSACTION",
    "COMMIT",
    "IF migration_exists = 0 THEN",
    "target_identity_count <> 0",
    "target_identity_count <> 2",
    "LAST_INSERT_ID()",
    "IndependentBoardAdmin",
    "IndependentBoardEntitlementGovernance",
    "IndependentBoardEntitlementReceipts",
    "business/independentBoard/admin/entitlementReceipt/index",
    "board:entitlement:revoke",
    "board:entitlement:audit",
    "20260720_independent_board_admin_menu_v1",
    "20260720_independent_board_entitlement_lifecycle_menu_v1",
    "Independent Board controlled entitlement revoke permission and immutable receipt audit menu",
    "table_type = 'BASE TABLE'",
    "engine = 'InnoDB'",
    "public manifest lock first and then",
    "refuse unknown children or",
    "any sys_role_menu references",
    "Never roll back",
    "named lock remains held",
    "read-only completion-state audit",
    "must never be cleared or replayed automatically"
)
foreach ($needle in $requiredLifecycleMenuNeedles) {
    if (-not $lifecycleMenuSql.Contains($needle)) {
        $errors.Add("Independent Board entitlement lifecycle menu SQL is missing required contract: $needle")
    }
}
if ($lifecycleMenuSql -match '(?im)^\s*(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM)\s+`?sys_role_menu`?\b') {
    $errors.Add("Independent Board entitlement lifecycle menu migration must not create, update or delete role bindings")
}
if ($lifecycleMenuSql.Contains("'IndependentBoardMe'") -or
    $lifecycleMenuSql.Contains('business/independentBoard/me/index') -or
    $lifecycleMenuSql.Contains('my:independent-board:view')) {
    $errors.Add("Independent Board entitlement lifecycle menu migration must not absorb or rewrite the W2 user menu identity")
}
if ($lifecycleMenuSql -match '(?im)^\s*(?:DELETE\s+FROM|UPDATE)\s+`?sys_menu`?' -or
    $lifecycleMenuSql.Contains('ON DUPLICATE KEY UPDATE')) {
    $errors.Add("Independent Board entitlement lifecycle menu migration must fail closed instead of repairing menu rows")
}
$lifecycleMenuInserts = @([regex]::Matches(
    $lifecycleMenuSql,
    'INSERT\s+INTO\s+`sys_menu`\s*\((?<columns>[^)]*)\)',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
))
if ($lifecycleMenuInserts.Count -ne 2) {
    $errors.Add("Independent Board entitlement lifecycle menu migration must insert exactly two generated-id menu rows")
}
foreach ($menuInsert in $lifecycleMenuInserts) {
    if ($menuInsert.Groups['columns'].Value -match '(?<![A-Za-z0-9_])`?menu_id`?(?![A-Za-z0-9_])') {
        $errors.Add("Independent Board entitlement lifecycle menu migration must not insert a fixed menu_id")
    }
}
if (@([regex]::Matches($lifecycleMenuSql, 'LAST_INSERT_ID\(\)', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)).Count -ne 2) {
    $errors.Add("Independent Board entitlement lifecycle menu migration must capture two generated menu identifiers")
}
$lifecycleApplyGuardIndex = $lifecycleMenuSql.IndexOf('IF migration_exists = 0 THEN', [StringComparison]::Ordinal)
$lifecycleStartTransactionIndex = $lifecycleMenuSql.IndexOf('START TRANSACTION;', [StringComparison]::Ordinal)
$lifecycleRevokeInsertIndex = $lifecycleMenuSql.IndexOf("'board:entitlement:revoke', '#'", [StringComparison]::Ordinal)
$lifecycleReceiptPageInsertIndex = $lifecycleMenuSql.IndexOf("'IndependentBoardEntitlementReceipts', 1, 0, 'C'", [StringComparison]::Ordinal)
$lifecycleReceiptInsertIndex = $lifecycleMenuSql.IndexOf('INSERT INTO `u3w_schema_migration`', [StringComparison]::Ordinal)
$lifecycleStateAuditIndex = if ($lifecycleReceiptInsertIndex -ge 0) {
    $lifecycleMenuSql.IndexOf('SELECT COUNT(*), MIN(`menu_id`)', $lifecycleReceiptInsertIndex, [StringComparison]::Ordinal)
} else { -1 }
$lifecycleLockReleaseIndex = $lifecycleMenuSql.LastIndexOf('SELECT RELEASE_LOCK(migration_lock_name)', [StringComparison]::Ordinal)
$lifecycleCommitIndex = $lifecycleMenuSql.LastIndexOf('COMMIT;', [StringComparison]::Ordinal)
$lifecycleProcedureEndIndex = $lifecycleMenuSql.IndexOf('END$$', [StringComparison]::Ordinal)
if ($lifecycleApplyGuardIndex -lt 0 -or
    $lifecycleStartTransactionIndex -le $lifecycleApplyGuardIndex -or
    $lifecycleRevokeInsertIndex -le $lifecycleStartTransactionIndex -or
    $lifecycleReceiptPageInsertIndex -le $lifecycleRevokeInsertIndex -or
    $lifecycleReceiptInsertIndex -le $lifecycleReceiptPageInsertIndex -or
    $lifecycleStateAuditIndex -le $lifecycleReceiptInsertIndex -or
    $lifecycleCommitIndex -le $lifecycleStateAuditIndex -or
    $lifecycleLockReleaseIndex -le $lifecycleCommitIndex -or
    $lifecycleProcedureEndIndex -le $lifecycleLockReleaseIndex) {
    $errors.Add("Independent Board entitlement lifecycle menu migration must write permission, page and receipt, audit completion, commit while locked and only then release its lock")
}

$connectorBindingSqlPath = Join-Path $sqlRoot "update_20260721_independent_board_connector_binding.sql"
if (-not (Test-Path -LiteralPath $connectorBindingSqlPath -PathType Leaf)) {
    $errors.Add("Independent Board authoritative Connector binding migration is missing")
    $connectorBindingSql = ''
}
else {
    $connectorBindingSql = Get-Content -LiteralPath $connectorBindingSqlPath -Raw -Encoding UTF8
}
$requiredConnectorBindingNeedles = @(
    "20260721_independent_board_connector_binding_v1",
    "public_init_032",
    "Target: MySQL >= 8.0.29",
    "migration requires MySQL 8.0.29 or newer",
    "SHA2(",
    "CHAR_LENGTH(migration_lock_name) <> 64",
    "GET_LOCK(migration_lock_name, 30)",
    "IS_USED_LOCK(migration_lock_name)",
    "migration_lock_owner <> current_connection",
    "RELEASE_LOCK(migration_lock_name)",
    "DECLARE EXIT HANDLER FOR SQLEXCEPTION",
    "ROLLBACK;",
    "START TRANSACTION;",
    "COMMIT;",
    "IF migration_exists = 0 AND target_table_count <> 0 THEN",
    "IF migration_exists <> 0 AND target_table_count <> 3 THEN",
    'CREATE TABLE IF NOT EXISTS `fbs_connector_binding`',
    'CREATE TABLE IF NOT EXISTS `fbs_connector_binding_scope`',
    'CREATE TABLE IF NOT EXISTS `fbs_connector_binding_receipt`',
    "uk_connector_binding_id",
    "uk_connector_binding_receipt_scope",
    "uk_connector_binding_scope",
    "fk_connector_binding_entitlement",
    "fk_connector_binding_scope_binding",
    "fk_connector_binding_receipt_binding",
    "target_column_count <> 36",
    "target_index_count <> 11",
    "target_foreign_key_count <> 3",
    "target_foreign_key_total_column_count <> 8",
    "target_check_count <> 15",
    "target_enforced_check_count <> 15",
    "target_check_contract_count <> 15",
    "unique_constraint_schema = DATABASE()",
    "referenced_table_schema = DATABASE()",
    "tc.enforced = 'YES'",
    "CAST(normalized_clause AS BINARY) = CAST('(source_code=''workbuddy'')' AS BINARY)",
    "CAST(normalized_clause AS BINARY) = CAST('(evidence_level=''action_completed'')' AS BINARY)",
    "target_digest_column_count <> 3",
    "CHAR(64) CHARACTER SET ascii COLLATE ascii_bin",
    "``source_code`` = 'WORKBUDDY'",
    "``connector_code`` = 'fbs-connector'",
    "``resource_uri`` = 'https://api2.u3w.com/fbs-mcp/mcp'",
    "MCP_INITIALIZE",
    "MCP_TOOLS_LIST",
    "identity.read",
    "entitlement.read",
    "board.meeting.reserve",
    "board.receipt.write",
    "CONNECTOR_BINDING_VERIFIED",
    "CONNECTOR_BINDING_REVOKED",
    "ACTION_COMPLETED",
    "Independent Board authoritative Connector binding, scope and receipt tables",
    "target_internal_receipt_count <> 1",
    "lost lock ownership before trigger finalization",
    'CREATE TRIGGER IF NOT EXISTS `trg_connector_binding_receipt_no_update`',
    'CREATE TRIGGER IF NOT EXISTS `trg_connector_binding_receipt_no_delete`',
    'CALL `u3w_finalize_independent_board_connector_binding_20260721`()$$',
    "Connector binding receipts are immutable"
)
foreach ($needle in $requiredConnectorBindingNeedles) {
    if (-not $connectorBindingSql.Contains($needle)) {
        $errors.Add("Independent Board Connector binding SQL is missing required contract: $needle")
    }
}
if ($connectorBindingSql.Contains('ON DUPLICATE KEY UPDATE') -or
    $connectorBindingSql.Contains('CREATE TABLE IF NOT EXISTS `u3w_schema_migration`')) {
    $errors.Add("Independent Board Connector binding migration must fail closed and must not bootstrap or repair its migration receipt ledger")
}
if ($connectorBindingSql -match '(?im)^\s*(?:ALTER|DROP)\s+TABLE\s+`?fbs_product_entitlement`?' -or
    $connectorBindingSql -match '(?im)^\s*(?:UPDATE|INSERT\s+INTO|DELETE\s+FROM)\s+`?fbs_product_entitlement`?') {
    $errors.Add("Independent Board Connector binding migration must not mutate legacy entitlement Connector columns or entitlement rows")
}

$connectorApplyGuardIndex = $connectorBindingSql.IndexOf('IF migration_exists = 0 THEN', [StringComparison]::Ordinal)
$connectorBindingCreateIndex = $connectorBindingSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_connector_binding`', [StringComparison]::Ordinal)
$connectorScopeCreateIndex = $connectorBindingSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_connector_binding_scope`', [StringComparison]::Ordinal)
$connectorReceiptCreateIndex = $connectorBindingSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_connector_binding_receipt`', [StringComparison]::Ordinal)
$connectorApplyGuardEndIndex = if ($connectorReceiptCreateIndex -ge 0) {
    $connectorBindingSql.IndexOf('END IF;', $connectorReceiptCreateIndex, [StringComparison]::Ordinal)
} else { -1 }
$connectorCompletionAuditIndex = if ($connectorApplyGuardEndIndex -ge 0) {
    $connectorBindingSql.IndexOf('-- Exact table, engine and collation audit.', $connectorApplyGuardEndIndex, [StringComparison]::Ordinal)
} else { -1 }
$connectorFinalizerDefinitionIndex = $connectorBindingSql.IndexOf(
    'CREATE PROCEDURE `u3w_finalize_independent_board_connector_binding_20260721`()',
    [StringComparison]::Ordinal)
$connectorTransactionIndex = if ($connectorFinalizerDefinitionIndex -ge 0) {
    $connectorBindingSql.IndexOf('START TRANSACTION;', $connectorFinalizerDefinitionIndex, [StringComparison]::Ordinal)
} else { -1 }
$connectorReceiptInsertIndex = if ($connectorTransactionIndex -ge 0) {
    $connectorBindingSql.IndexOf('INSERT INTO `u3w_schema_migration`', $connectorTransactionIndex, [StringComparison]::Ordinal)
} else { -1 }
$connectorReceiptAuditIndex = if ($connectorReceiptInsertIndex -ge 0) {
    $connectorBindingSql.IndexOf('SELECT COUNT(*) INTO target_internal_receipt_count', $connectorReceiptInsertIndex, [StringComparison]::Ordinal)
} else { -1 }
$connectorCommitIndex = if ($connectorReceiptAuditIndex -ge 0) {
    $connectorBindingSql.IndexOf('COMMIT;', $connectorReceiptAuditIndex, [StringComparison]::Ordinal)
} else { -1 }
$connectorReleaseIndex = if ($connectorCommitIndex -ge 0) {
    $connectorBindingSql.IndexOf('SELECT RELEASE_LOCK(migration_lock_name)', $connectorCommitIndex, [StringComparison]::Ordinal)
} else { -1 }
$connectorFinalizerEndIndex = if ($connectorReleaseIndex -ge 0) {
    $connectorBindingSql.IndexOf('END$$', $connectorReleaseIndex, [StringComparison]::Ordinal)
} else { -1 }
$connectorPrepareCallIndex = $connectorBindingSql.LastIndexOf(
    'CALL `u3w_migrate_independent_board_connector_binding_20260721`()$$',
    [StringComparison]::Ordinal)
$connectorUpdateTriggerIndex = $connectorBindingSql.LastIndexOf(
    'CREATE TRIGGER IF NOT EXISTS `trg_connector_binding_receipt_no_update`',
    [StringComparison]::Ordinal)
$connectorDeleteTriggerIndex = $connectorBindingSql.LastIndexOf(
    'CREATE TRIGGER IF NOT EXISTS `trg_connector_binding_receipt_no_delete`',
    [StringComparison]::Ordinal)
$connectorFinalizeCallIndex = $connectorBindingSql.LastIndexOf(
    'CALL `u3w_finalize_independent_board_connector_binding_20260721`()$$',
    [StringComparison]::Ordinal)
$connectorPrepareDropIndex = $connectorBindingSql.LastIndexOf(
    'DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_connector_binding_20260721`$$',
    [StringComparison]::Ordinal)
$connectorFinalizerDropIndex = $connectorBindingSql.LastIndexOf(
    'DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_connector_binding_20260721`$$',
    [StringComparison]::Ordinal)
if ($connectorApplyGuardIndex -lt 0 -or
    $connectorBindingCreateIndex -le $connectorApplyGuardIndex -or
    $connectorScopeCreateIndex -le $connectorBindingCreateIndex -or
    $connectorReceiptCreateIndex -le $connectorScopeCreateIndex -or
    $connectorApplyGuardEndIndex -le $connectorReceiptCreateIndex -or
    $connectorCompletionAuditIndex -le $connectorApplyGuardEndIndex -or
    $connectorFinalizerDefinitionIndex -le $connectorCompletionAuditIndex -or
    $connectorTransactionIndex -le $connectorFinalizerDefinitionIndex -or
    $connectorReceiptInsertIndex -le $connectorTransactionIndex -or
    $connectorReceiptAuditIndex -le $connectorReceiptInsertIndex -or
    $connectorCommitIndex -le $connectorReceiptAuditIndex -or
    $connectorReleaseIndex -le $connectorCommitIndex -or
    $connectorFinalizerEndIndex -le $connectorReleaseIndex -or
    $connectorPrepareCallIndex -le $connectorFinalizerEndIndex -or
    $connectorUpdateTriggerIndex -le $connectorPrepareCallIndex -or
    $connectorDeleteTriggerIndex -le $connectorUpdateTriggerIndex -or
    $connectorFinalizeCallIndex -le $connectorDeleteTriggerIndex -or
    $connectorPrepareDropIndex -le $connectorFinalizeCallIndex -or
    $connectorFinalizerDropIndex -le $connectorPrepareDropIndex) {
    $errors.Add("Independent Board Connector binding migration must prepare and audit while locked, create immutable triggers at top level, finalize its receipt, release the lock and then remove both procedures")
}
$connectorTargetCreates = @([regex]::Matches(
    $connectorBindingSql,
    'CREATE TABLE IF NOT EXISTS `fbs_connector_binding(?:_scope|_receipt)?`',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
))
if ($connectorTargetCreates.Count -ne 3 -or @($connectorTargetCreates | Where-Object {
    $_.Index -le $connectorApplyGuardIndex -or $_.Index -ge $connectorApplyGuardEndIndex
}).Count -ne 0) {
    $errors.Add("all three Connector binding CREATE TABLE statements must be confined to the first-apply guard")
}

$expectedConnectorBindingColumns = [ordered]@{
    fbs_connector_binding = @(
        'id','binding_id','enterprise_id','member_id','user_id','product_code','source_code','connector_code',
        'issuer_uri','resource_uri','client_id','principal_subject_digest','status','verification_method',
        'evidence_digest','verified_at','last_seen_at','valid_until','revoked_at','version','created_at','updated_at'
    )
    fbs_connector_binding_scope = @('binding_id','scope_code','created_at')
    fbs_connector_binding_receipt = @(
        'id','receipt_id','binding_id','enterprise_id','member_id','user_id','actor_user_id','action',
        'payload_digest','evidence_level','created_at'
    )
}
foreach ($tableName in $expectedConnectorBindingColumns.Keys) {
    $escapedTableName = [regex]::Escape($tableName)
    $tableMatch = [regex]::Match(
        $connectorBindingSql,
        "CREATE TABLE IF NOT EXISTS ``$escapedTableName``\s*\((?<body>[\s\S]*?)\) ENGINE=InnoDB",
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if (-not $tableMatch.Success) {
        $errors.Add("Connector binding SQL table body is missing: $tableName")
        continue
    }
    $actualColumns = @([regex]::Matches($tableMatch.Groups['body'].Value, '(?m)^\s*`(?<name>[^`]+)`\s+') |
        ForEach-Object { $_.Groups['name'].Value })
    $expectedColumns = @($expectedConnectorBindingColumns[$tableName])
    if (($actualColumns -join '|') -ne ($expectedColumns -join '|')) {
        $errors.Add("ordered column contract mismatch for $tableName")
    }
}

$oauthFoundationSqlPath = Join-Path $sqlRoot "update_20260721_independent_board_oauth_foundation.sql"
$expectedOauthFoundationSha256 = 'b103ac5936ab1cb4bce865f04256f41826d90693556bc5627c09fc4ffee5de27'
$oauthFoundationSha256 = ''
if (-not (Test-Path -LiteralPath $oauthFoundationSqlPath -PathType Leaf)) {
    $errors.Add("Independent Board OAuth foundation migration is missing")
    $oauthFoundationSql = ''
}
else {
    $oauthFoundationSql = Get-Content -LiteralPath $oauthFoundationSqlPath -Raw -Encoding UTF8
    $oauthFoundationSha256 = (Get-FileHash -LiteralPath $oauthFoundationSqlPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not [string]::Equals(
            $oauthFoundationSha256,
            $expectedOauthFoundationSha256,
            [StringComparison]::Ordinal)) {
        $errors.Add("public_init_033 byte contract drifted: expected SHA-256 $expectedOauthFoundationSha256, found $oauthFoundationSha256")
    }
}
$neutralOauthClientName = -join (26410,39564,35777,30340,26412,22320,20844,20849,23458,25143,31471 | ForEach-Object { [char]$_ })
$requiredOauthFoundationNeedles = @(
    "20260721_independent_board_oauth_foundation_v1",
    "public_init_033",
    "Target: exact MySQL 8.0.30 or 8.4.8 metadata baselines only",
    "supports exact MySQL 8.0.30 or 8.4.8 baselines",
    "INSTR(LOWER(VERSION()), 'mariadb') > 0",
    "CAST(@@version_comment AS BINARY)",
    "MySQL Community Server - GPL",
    "SHA2(",
    "CHAR_LENGTH(migration_lock_name) <> 64",
    "GET_LOCK(migration_lock_name, 30)",
    "IS_USED_LOCK(migration_lock_name)",
    "migration_lock_owner <> current_connection",
    "RELEASE_LOCK(migration_lock_name)",
    "DECLARE EXIT HANDLER FOR SQLEXCEPTION",
    "ROLLBACK;",
    "START TRANSACTION;",
    "COMMIT;",
    "IF migration_exists = 0 AND target_table_count <> 0 THEN",
    "IF migration_exists <> 0 AND target_table_count <> 6 THEN",
    "OAuth receipt trigger names collide before first apply",
    "completed W4a binding migration",
    "external-dependency-contract-audit",
    "20260720_independent_board_control_plane_v1",
    "uk_product_entitlement_scope",
    "uk_connector_binding_receipt_scope",
    "fk_connector_binding_entitlement",
    "prerequisite_dependency_column_count <> 7",
    "prerequisite_dependency_index_count <> 2",
    "COUNT(*) AS key_part_count",
    "SUM(column_name IS NULL OR expression IS NOT NULL) AS non_column_key_parts",
    "non_column_key_parts = 0",
    "prerequisite_binding_fk_count <> 1",
    'CREATE TABLE IF NOT EXISTS `fbs_oauth_client`',
    'CREATE TABLE IF NOT EXISTS `fbs_oauth_authorization_request`',
    'CREATE TABLE IF NOT EXISTS `fbs_oauth_authorization_code`',
    'CREATE TABLE IF NOT EXISTS `fbs_oauth_token_family`',
    'CREATE TABLE IF NOT EXISTS `fbs_oauth_token`',
    'CREATE TABLE IF NOT EXISTS `fbs_oauth_receipt`',
    $neutralOauthClientName,
    "principal_subject_digest",
    "lifecycle_slot",
    "active_refresh_slot",
    "uk_oauth_family_live_slot",
    "uk_oauth_token_active_refresh",
    "351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1",
    "target_total_column_count <> 144",
    "target_digest_column_count <> 17",
    "target_generated_column_count <> 2",
    "target_index_count <> 52",
    "target_index_contract_count <> 52",
    "target_column_contract_digest IS NULL",
    "target_index_contract_digest IS NULL",
    "target_foreign_key_contract_digest IS NULL",
    "target_check_contract_digest IS NULL",
    "SEPARATOR 0x0A",
    "89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0",
    "1a3122b7238b444939a7981ccd759322bd168f351131e4d9c0a16346242c3d79",
    "016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539",
    "3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f",
    "51f71711dfc17b5a6741fbbd1bd3d31ae7d4761e9da5722c614eb199a81afb11",
    "target_foreign_key_count <> 14",
    "target_foreign_key_contract_count <> 14",
    "target_foreign_key_column_count <> 57",
    "target_foreign_key_column_contract_count <> 14",
    "target_check_count <> 33",
    "target_enforced_check_count <> 33",
    "target_check_name_count <> 33",
    "unique_constraint_schema = DATABASE()",
    "referenced_table_schema = DATABASE()",
    "enforced = 'YES'",
    "Independent Board OAuth client, authorization, token family and immutable receipt tables",
    "target_internal_receipt_count <> 1",
    "lost lock ownership before trigger finalization",
    'CREATE TRIGGER IF NOT EXISTS `trg_oauth_receipt_no_update`',
    'CREATE TRIGGER IF NOT EXISTS `trg_oauth_receipt_no_delete`',
    "WHERE event_object_table = 'fbs_oauth_receipt'",
    'CALL `u3w_finalize_independent_board_oauth_foundation_20260721`()$$',
    "OAuth receipts are immutable"
)
foreach ($needle in $requiredOauthFoundationNeedles) {
    if (-not $oauthFoundationSql.Contains($needle)) {
        $errors.Add("Independent Board OAuth foundation SQL is missing required contract: $needle")
    }
}
if ($oauthFoundationSql.Contains('WorkBuddy - ')) {
    $errors.Add("restricted DCR must retain a neutral unverified local public-client name and must not assert a WorkBuddy identity")
}
if ($oauthFoundationSql.Contains('ON DUPLICATE KEY UPDATE') -or
    $oauthFoundationSql.Contains('CREATE TABLE IF NOT EXISTS `u3w_schema_migration`')) {
    $errors.Add("Independent Board OAuth foundation migration must fail closed and must not bootstrap or repair its migration ledger")
}
if ($oauthFoundationSql -match '(?im)^\s*(?:ALTER|DROP)\s+TABLE\s+`?fbs_connector_binding(?:_scope|_receipt)?`?' -or
    $oauthFoundationSql -match '(?im)^\s*(?:UPDATE|INSERT\s+INTO|DELETE\s+FROM)\s+`?fbs_connector_binding(?:_scope|_receipt)?`?') {
    $errors.Add("Independent Board OAuth foundation migration must not mutate any W4a table or row")
}
if ($oauthFoundationSql -match '(?im)^\s*INSERT\s+INTO\s+`?fbs_oauth_') {
    $errors.Add("Independent Board OAuth foundation migration must not seed clients, authorization state, tokens or receipts")
}

$oauthApplyGuardIndex = $oauthFoundationSql.IndexOf('IF migration_exists = 0 THEN', [StringComparison]::Ordinal)
$oauthClientCreateIndex = $oauthFoundationSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_oauth_client`', [StringComparison]::Ordinal)
$oauthRequestCreateIndex = $oauthFoundationSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_oauth_authorization_request`', [StringComparison]::Ordinal)
$oauthCodeCreateIndex = $oauthFoundationSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_oauth_authorization_code`', [StringComparison]::Ordinal)
$oauthFamilyCreateIndex = $oauthFoundationSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_oauth_token_family`', [StringComparison]::Ordinal)
$oauthTokenCreateIndex = $oauthFoundationSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_oauth_token`', [StringComparison]::Ordinal)
$oauthReceiptCreateIndex = $oauthFoundationSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_oauth_receipt`', [StringComparison]::Ordinal)
$oauthApplyGuardEndIndex = if ($oauthReceiptCreateIndex -ge 0) {
    $oauthFoundationSql.IndexOf('END IF;', $oauthReceiptCreateIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthCompletionAuditIndex = if ($oauthApplyGuardEndIndex -ge 0) {
    $oauthFoundationSql.IndexOf('-- Exact table, engine and collation audit.', $oauthApplyGuardEndIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthFinalizerDefinitionIndex = $oauthFoundationSql.IndexOf(
    'CREATE PROCEDURE `u3w_finalize_independent_board_oauth_foundation_20260721`()',
    [StringComparison]::Ordinal)
$oauthTransactionIndex = if ($oauthFinalizerDefinitionIndex -ge 0) {
    $oauthFoundationSql.IndexOf('START TRANSACTION;', $oauthFinalizerDefinitionIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthReceiptInsertIndex = if ($oauthTransactionIndex -ge 0) {
    $oauthFoundationSql.IndexOf('INSERT INTO `u3w_schema_migration`', $oauthTransactionIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthReceiptAuditIndex = if ($oauthReceiptInsertIndex -ge 0) {
    $oauthFoundationSql.IndexOf('SELECT COUNT(*) INTO target_internal_receipt_count', $oauthReceiptInsertIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthCommitIndex = if ($oauthReceiptAuditIndex -ge 0) {
    $oauthFoundationSql.IndexOf('COMMIT;', $oauthReceiptAuditIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthReleaseIndex = if ($oauthCommitIndex -ge 0) {
    $oauthFoundationSql.IndexOf('SELECT RELEASE_LOCK(migration_lock_name)', $oauthCommitIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthFinalizerEndIndex = if ($oauthReleaseIndex -ge 0) {
    $oauthFoundationSql.IndexOf('END$$', $oauthReleaseIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthPrepareCallIndex = $oauthFoundationSql.LastIndexOf(
    'CALL `u3w_migrate_independent_board_oauth_foundation_20260721`()$$',
    [StringComparison]::Ordinal)
$oauthUpdateTriggerIndex = $oauthFoundationSql.LastIndexOf(
    'CREATE TRIGGER IF NOT EXISTS `trg_oauth_receipt_no_update`',
    [StringComparison]::Ordinal)
$oauthDeleteTriggerIndex = $oauthFoundationSql.LastIndexOf(
    'CREATE TRIGGER IF NOT EXISTS `trg_oauth_receipt_no_delete`',
    [StringComparison]::Ordinal)
$oauthFinalizeCallIndex = $oauthFoundationSql.LastIndexOf(
    'CALL `u3w_finalize_independent_board_oauth_foundation_20260721`()$$',
    [StringComparison]::Ordinal)
$oauthPrepareDropIndex = $oauthFoundationSql.LastIndexOf(
    'DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_oauth_foundation_20260721`$$',
    [StringComparison]::Ordinal)
$oauthFinalizerDropIndex = $oauthFoundationSql.LastIndexOf(
    'DROP PROCEDURE IF EXISTS `u3w_finalize_independent_board_oauth_foundation_20260721`$$',
    [StringComparison]::Ordinal)
if ($oauthApplyGuardIndex -lt 0 -or
    $oauthClientCreateIndex -le $oauthApplyGuardIndex -or
    $oauthRequestCreateIndex -le $oauthClientCreateIndex -or
    $oauthCodeCreateIndex -le $oauthRequestCreateIndex -or
    $oauthFamilyCreateIndex -le $oauthCodeCreateIndex -or
    $oauthTokenCreateIndex -le $oauthFamilyCreateIndex -or
    $oauthReceiptCreateIndex -le $oauthTokenCreateIndex -or
    $oauthApplyGuardEndIndex -le $oauthReceiptCreateIndex -or
    $oauthCompletionAuditIndex -le $oauthApplyGuardEndIndex -or
    $oauthFinalizerDefinitionIndex -le $oauthCompletionAuditIndex -or
    $oauthTransactionIndex -le $oauthFinalizerDefinitionIndex -or
    $oauthReceiptInsertIndex -le $oauthTransactionIndex -or
    $oauthReceiptAuditIndex -le $oauthReceiptInsertIndex -or
    $oauthCommitIndex -le $oauthReceiptAuditIndex -or
    $oauthReleaseIndex -le $oauthCommitIndex -or
    $oauthFinalizerEndIndex -le $oauthReleaseIndex -or
    $oauthPrepareCallIndex -le $oauthFinalizerEndIndex -or
    $oauthUpdateTriggerIndex -le $oauthPrepareCallIndex -or
    $oauthDeleteTriggerIndex -le $oauthUpdateTriggerIndex -or
    $oauthFinalizeCallIndex -le $oauthDeleteTriggerIndex -or
    $oauthPrepareDropIndex -le $oauthFinalizeCallIndex -or
    $oauthFinalizerDropIndex -le $oauthPrepareDropIndex) {
    $errors.Add("Independent Board OAuth migration must prepare and audit while locked, create immutable triggers at top level, finalize its receipt, release the lock and then remove both procedures")
}
$oauthTargetCreates = @([regex]::Matches(
    $oauthFoundationSql,
    'CREATE TABLE IF NOT EXISTS `fbs_oauth_(?:client|authorization_request|authorization_code|token_family|token|receipt)`',
    [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
))
if ($oauthTargetCreates.Count -ne 6 -or @($oauthTargetCreates | Where-Object {
    $_.Index -le $oauthApplyGuardIndex -or $_.Index -ge $oauthApplyGuardEndIndex
}).Count -ne 0) {
    $errors.Add("all six OAuth foundation CREATE TABLE statements must be confined to the first-apply guard")
}

$expectedOauthFoundationColumns = [ordered]@{
    fbs_oauth_client = @(
        'id','client_id','client_name','issuer_uri','resource_uri','product_code','source_code','connector_code',
        'redirect_port','redirect_uri','token_endpoint_auth_method','grant_types_canonical','response_types_canonical',
        'scope_canonical','scope_digest','metadata_digest','registration_source_digest','status','registered_at',
        'expires_at','terminated_at','version','created_at','updated_at'
    )
    fbs_oauth_authorization_request = @(
        'id','request_handle_digest','client_id','redirect_uri','code_challenge','code_challenge_method','state_digest',
        'state_key_ref','state_nonce','state_ciphertext','issuer_uri','resource_uri','product_code','source_code',
        'connector_code','scope_canonical','scope_digest','principal_subject_digest','enterprise_id','member_id','user_id',
        'status','requested_at','expires_at','approved_at','denied_at','consumed_at','version','created_at','updated_at'
    )
    fbs_oauth_authorization_code = @(
        'id','code_digest','authorization_request_id','client_id','redirect_uri','code_challenge','code_challenge_method',
        'issuer_uri','resource_uri','product_code','source_code','connector_code','scope_canonical','scope_digest',
        'principal_subject_digest','enterprise_id','member_id','user_id','status','issued_at','expires_at','used_at',
        'revoked_at','version','created_at','updated_at'
    )
    fbs_oauth_token_family = @(
        'id','family_id','origin_authorization_code_id','client_id','enterprise_id','member_id','user_id','product_code',
        'source_code','connector_code','issuer_uri','resource_uri','scope_canonical','scope_digest','principal_subject_digest',
        'binding_id','binding_version','status','lifecycle_slot','current_refresh_generation','issued_at','activated_at',
        'expires_at','terminated_at','version','created_at','updated_at'
    )
    fbs_oauth_token = @(
        'id','token_digest','family_id','token_type','generation','resource_uri','scope_canonical','scope_digest','status',
        'active_refresh_slot','issued_at','used_at','revoked_at','expires_at','version','created_at','updated_at'
    )
    fbs_oauth_receipt = @(
        'id','receipt_id','action','client_id','authorization_request_id','authorization_code_id','family_id','token_id',
        'binding_id','enterprise_id','member_id','user_id','principal_subject_digest','actor_type','actor_user_id',
        'actor_subject_digest','correlation_id','payload_digest','evidence_level','created_at'
    )
}
foreach ($tableName in $expectedOauthFoundationColumns.Keys) {
    $escapedTableName = [regex]::Escape($tableName)
    $tableMatch = [regex]::Match(
        $oauthFoundationSql,
        "CREATE TABLE IF NOT EXISTS ``$escapedTableName``\s*\((?<body>[\s\S]*?)\) ENGINE=InnoDB",
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if (-not $tableMatch.Success) {
        $errors.Add("OAuth foundation SQL table body is missing: $tableName")
        continue
    }
    $actualColumns = @([regex]::Matches($tableMatch.Groups['body'].Value, '(?m)^\s*`(?<name>[^`]+)`\s+') |
        ForEach-Object { $_.Groups['name'].Value })
    $expectedColumns = @($expectedOauthFoundationColumns[$tableName])
    if (($actualColumns -join '|') -ne ($expectedColumns -join '|')) {
        $errors.Add("ordered OAuth foundation column contract mismatch for $tableName")
    }
}

$oauthProvenanceSqlPath = Join-Path $sqlRoot 'update_20260721_independent_board_oauth_receipt_provenance.sql'
$expectedOauthProvenanceSha256 = '2ba6fce7c3161b1647397485c37681974202b3a566ab370cc05587b1cfde7af3'
$oauthProvenanceSha256 = ''
if (-not (Test-Path -LiteralPath $oauthProvenanceSqlPath -PathType Leaf)) {
    $errors.Add('Independent Board OAuth provenance migration is missing')
    $oauthProvenanceSql = ''
}
else {
    $oauthProvenanceSql = Get-Content -LiteralPath $oauthProvenanceSqlPath -Raw -Encoding UTF8
    $oauthProvenanceSha256 = (Get-FileHash -LiteralPath $oauthProvenanceSqlPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not [string]::Equals(
            $oauthProvenanceSha256,
            $expectedOauthProvenanceSha256,
            [StringComparison]::Ordinal)) {
        $errors.Add("public_init_034 byte contract drifted: expected SHA-256 $expectedOauthProvenanceSha256, found $oauthProvenanceSha256")
    }
}
$oauthSuccessorCheckDigest = 'd8878ff64c7e897ddd8d42db6f0afd1a264d2cc8c1794d155051f0cd1638103b'
$requiredOauthProvenanceNeedles = @(
    '20260721_independent_board_oauth_receipt_provenance_v1',
    'public_init_034',
    'public_init_033',
    'exact MySQL 8.0.30 or 8.4.8',
    'MySQL Community Server - GPL',
    "INSTR(LOWER(VERSION()), 'mariadb') > 0",
    'SET migration_lock_name = SHA2(',
    'CHAR_LENGTH(migration_lock_name) <> 64',
    'GET_LOCK(migration_lock_name, 30)',
    'IS_USED_LOCK(migration_lock_name)',
    'migration_lock_owner <> current_connection',
    'RELEASE_LOCK(migration_lock_name)',
    'DECLARE EXIT HANDLER FOR SQLEXCEPTION',
    'ROLLBACK;',
    'RUNNING:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness',
    'APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness',
    'TOKEN_FAMILY_CREATED receipt lineage is missing or drifted',
    'WHERE r.`action` = ''TOKEN_FAMILY_CREATED''',
    'f.`origin_authorization_code_id` <> r.`authorization_code_id`',
    'WHERE `action` = ''TOKEN_FAMILY_CREATED''',
    'HAVING COUNT(*) > 1',
    'ALTER TABLE `fbs_oauth_receipt`',
    'ADD COLUMN `family_created_slot`',
    'WHEN `action` = ''TOKEN_FAMILY_CREATED'' THEN `family_id`',
    'ELSE NULL',
    'STORED',
    'ADD UNIQUE KEY `uk_oauth_receipt_family_created_slot`',
    '(`family_created_slot`)',
    'provenance DDL is partial, orphaned or drifted',
    "SET migration_stage = 'final-metadata-current-read'",
    "AND NOT (table_name = 'fbs_oauth_receipt'",
    "AND column_name = 'family_created_slot'",
    "AND index_name = 'uk_oauth_receipt_family_created_slot'",
    '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0',
    '1a3122b7238b444939a7981ccd759322bd168f351131e4d9c0a16346242c3d79',
    '016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539',
    '3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f',
    '51f71711dfc17b5a6741fbbd1bd3d31ae7d4761e9da5722c614eb199a81afb11',
    "WHEN '8.0.30' THEN '$oauthSuccessorCheckDigest'",
    "WHEN '8.4.8' THEN '$oauthSuccessorCheckDigest'",
    'CAST(expected_successor_check_contract_digest AS BINARY)',
    'DECLARE original_group_concat_max_len BIGINT UNSIGNED DEFAULT 0',
    'DECLARE group_concat_limit_changed TINYINT DEFAULT 0',
    'SET original_group_concat_max_len = @@SESSION.group_concat_max_len',
    'IF original_group_concat_max_len < 1048576 THEN',
    'SET SESSION group_concat_max_len = 1048576',
    'SET group_concat_limit_changed = 1',
    'SET SESSION group_concat_max_len = original_group_concat_max_len',
    'SET group_concat_limit_changed = 0',
    'target_column_count <> 145',
    'target_generated_column_count <> 3',
    'target_index_count <> 53',
    'target_foreign_key_count <> 14',
    'target_check_count <> 33',
    'target_trigger_count <> 2',
    'LEFT JOIN `fbs_oauth_token_family`',
    'f.`family_id` IS NULL',
    "SET migration_stage = 'receipt-finalization'",
    'FOR UPDATE;',
    'provenance APPLIED receipt is not exact'
)
foreach ($needle in $requiredOauthProvenanceNeedles) {
    if (-not $oauthProvenanceSql.Contains($needle)) {
        $errors.Add("Independent Board OAuth provenance SQL is missing required contract: $needle")
    }
}
$escapedOauthSuccessorCheckDigest = [regex]::Escape($oauthSuccessorCheckDigest)
$oauthSuccessorCheckMappingPattern =
    "(?s)SET\s+expected_successor_check_contract_digest\s*=\s*CASE\s+server_version\s+" +
    "WHEN\s+'8\.0\.30'\s+THEN\s+'$escapedOauthSuccessorCheckDigest'\s+" +
    "WHEN\s+'8\.4\.8'\s+THEN\s+'$escapedOauthSuccessorCheckDigest'\s+" +
    "ELSE\s+NULL\s+END;"
if (-not [regex]::IsMatch($oauthProvenanceSql, $oauthSuccessorCheckMappingPattern)) {
    $errors.Add("Independent Board OAuth provenance SQL must bind both exact MySQL builds to successor CHECK digest $oauthSuccessorCheckDigest")
}
if (@([regex]::Matches(
        $oauthProvenanceSql,
        [regex]::Escape($oauthSuccessorCheckDigest))).Count -ne 2) {
    $errors.Add('Independent Board OAuth provenance successor CHECK digest must occur exactly once for each allowlisted MySQL build')
}

$groupConcatCaptureNeedle = 'SET original_group_concat_max_len = @@SESSION.group_concat_max_len;'
$groupConcatRaiseNeedle = 'SET SESSION group_concat_max_len = 1048576;'
$groupConcatRestoreNeedle = 'SET SESSION group_concat_max_len = original_group_concat_max_len;'
$groupConcatCaptureIndex = $oauthProvenanceSql.IndexOf($groupConcatCaptureNeedle, [StringComparison]::Ordinal)
$groupConcatRaiseIndex = $oauthProvenanceSql.IndexOf($groupConcatRaiseNeedle, [StringComparison]::Ordinal)
$firstGroupConcatIndex = $oauthProvenanceSql.IndexOf('GROUP_CONCAT(', [StringComparison]::Ordinal)
$lastGroupConcatIndex = $oauthProvenanceSql.LastIndexOf('GROUP_CONCAT(', [StringComparison]::Ordinal)
$successfulGroupConcatRestoreIndex = $oauthProvenanceSql.LastIndexOf($groupConcatRestoreNeedle, [StringComparison]::Ordinal)
$oauthProvenanceReleaseIndex = $oauthProvenanceSql.LastIndexOf('SELECT RELEASE_LOCK(migration_lock_name)', [StringComparison]::Ordinal)
if ($groupConcatCaptureIndex -lt 0 -or
    $groupConcatRaiseIndex -le $groupConcatCaptureIndex -or
    $firstGroupConcatIndex -le $groupConcatRaiseIndex -or
    $lastGroupConcatIndex -lt $firstGroupConcatIndex -or
    $successfulGroupConcatRestoreIndex -le $lastGroupConcatIndex -or
    $oauthProvenanceReleaseIndex -le $successfulGroupConcatRestoreIndex) {
    $errors.Add('Independent Board OAuth provenance must raise the complete GROUP_CONCAT ceiling before every aggregate and restore it after final metadata use but before lock release')
}
if (@([regex]::Matches($oauthProvenanceSql, [regex]::Escape($groupConcatRaiseNeedle))).Count -ne 1 -or
    @([regex]::Matches($oauthProvenanceSql, [regex]::Escape($groupConcatRestoreNeedle))).Count -ne 2 -or
    @([regex]::Matches($oauthProvenanceSql, [regex]::Escape('SET group_concat_limit_changed = 1;'))).Count -ne 1 -or
    @([regex]::Matches($oauthProvenanceSql, [regex]::Escape('SET group_concat_limit_changed = 0;'))).Count -ne 2) {
    $errors.Add('Independent Board OAuth provenance GROUP_CONCAT ceiling must have one raise and exact handler/success restoration branches')
}

$generationWhitelistStart = $oauthProvenanceSql.IndexOf(
    'AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(',
    [StringComparison]::Ordinal)
$generationWhitelistEnd = if ($generationWhitelistStart -ge 0) {
    $oauthProvenanceSql.IndexOf('INTO target_generated_column_count', $generationWhitelistStart, [StringComparison]::Ordinal)
} else { -1 }
$actualGenerationWhitelist = @()
if ($generationWhitelistStart -ge 0 -and $generationWhitelistEnd -gt $generationWhitelistStart) {
    $generationWhitelistBlock = $oauthProvenanceSql.Substring(
        $generationWhitelistStart,
        $generationWhitelistEnd - $generationWhitelistStart)
    $actualGenerationWhitelist = @([regex]::Matches(
        $generationWhitelistBlock,
        "(?m)^\s*(?<literal>'casewhenaction=.*')\s*,?\s*$") |
        ForEach-Object { $_.Groups['literal'].Value })
}
$expectedGenerationWhitelist = @(
    "'casewhenaction=_utf8mb4''token_family_created''thenfamily_idelsenullend'",
    "'casewhenaction=_ascii''token_family_created''thenfamily_idelsenullend'",
    "'casewhenaction=''token_family_created''thenfamily_idelsenullend'"
)
if (($actualGenerationWhitelist -join '|') -ne ($expectedGenerationWhitelist -join '|')) {
    $errors.Add('Independent Board OAuth provenance generation expression whitelist must contain exactly _utf8mb4, _ascii and no-prefix forms in canonical order')
}
if ($oauthProvenanceSql -match '__[A-Z0-9_]+__') {
    $errors.Add('Independent Board OAuth provenance SQL contains an unresolved placeholder')
}
if ($oauthProvenanceSql.Contains('ON DUPLICATE KEY UPDATE') -or
    $oauthProvenanceSql.Contains('CREATE TABLE IF NOT EXISTS `u3w_schema_migration`')) {
    $errors.Add('Independent Board OAuth provenance migration must fail closed and must not bootstrap or repair its migration ledger')
}
if ($oauthProvenanceSql -match '(?im)^\s*(?:ALTER|DROP)\s+TABLE\s+`?fbs_oauth_(?!receipt\b)[a-z_]+' -or
    $oauthProvenanceSql -match '(?im)^\s*(?:UPDATE|INSERT\s+INTO|DELETE\s+FROM)\s+`?fbs_oauth_(?:client|authorization_request|authorization_code|token_family|token)\b') {
    $errors.Add('Independent Board OAuth provenance migration must not mutate foundation tables other than fbs_oauth_receipt metadata')
}
$oauthProvenanceAlterMatches = @([regex]::Matches(
    $oauthProvenanceSql,
    '(?im)^\s*ALTER\s+TABLE\s+`fbs_oauth_receipt`'))
if ($oauthProvenanceAlterMatches.Count -ne 1) {
    $errors.Add("Independent Board OAuth provenance migration must contain exactly one additive receipt ALTER; found $($oauthProvenanceAlterMatches.Count)")
}
$oauthProvenanceServerProfileIndex = $oauthProvenanceSql.IndexOf('SET server_version = VERSION();', [StringComparison]::Ordinal)
$oauthProvenanceSuccessorDigestIndex = $oauthProvenanceSql.IndexOf('SET expected_successor_check_contract_digest = CASE server_version', [StringComparison]::Ordinal)
$oauthProvenanceLockIndex = $oauthProvenanceSql.IndexOf('SELECT GET_LOCK(migration_lock_name, 30)', [StringComparison]::Ordinal)
$oauthProvenanceFoundationAuditIndex = $oauthProvenanceSql.IndexOf("SET migration_stage = 'foundation-receipt-audit'", [StringComparison]::Ordinal)
$oauthProvenanceStateAuditIndex = $oauthProvenanceSql.IndexOf('SELECT COUNT(*), MAX(`description`)', $oauthProvenanceFoundationAuditIndex, [StringComparison]::Ordinal)
$oauthProvenancePreflightIndex = $oauthProvenanceSql.IndexOf("SET migration_stage = 'lineage-preflight'", [StringComparison]::Ordinal)
$oauthProvenanceShapeAuditIndex = $oauthProvenanceSql.IndexOf("SET migration_stage = 'shape-state-audit'", [StringComparison]::Ordinal)
$oauthProvenanceRunningInsertIndex = $oauthProvenanceSql.IndexOf('INSERT INTO `u3w_schema_migration`', [StringComparison]::Ordinal)
$oauthProvenanceAlterIndex = $oauthProvenanceSql.IndexOf('ALTER TABLE `fbs_oauth_receipt`', [StringComparison]::Ordinal)
$oauthProvenanceFinalReadIndex = $oauthProvenanceSql.IndexOf("SET migration_stage = 'final-metadata-current-read'", [StringComparison]::Ordinal)
$oauthProvenanceFinalLineageIndex = $oauthProvenanceSql.IndexOf('SELECT COUNT(*) INTO invalid_lineage_count', $oauthProvenanceFinalReadIndex, [StringComparison]::Ordinal)
$oauthProvenanceReceiptFinalizationIndex = $oauthProvenanceSql.IndexOf("SET migration_stage = 'receipt-finalization'", [StringComparison]::Ordinal)
$oauthProvenanceReceiptLockIndex = $oauthProvenanceSql.IndexOf('FOR UPDATE;', $oauthProvenanceReceiptFinalizationIndex, [StringComparison]::Ordinal)
$oauthProvenancePromotionIndex = $oauthProvenanceSql.IndexOf('SET `description` = ''APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness''', [StringComparison]::Ordinal)
$oauthProvenanceReceiptCommitIndex = $oauthProvenanceSql.IndexOf('COMMIT;', $oauthProvenancePromotionIndex, [StringComparison]::Ordinal)
$oauthProvenanceCallIndex = $oauthProvenanceSql.LastIndexOf('CALL `u3w_migrate_independent_board_oauth_receipt_provenance_20260721`()$$', [StringComparison]::Ordinal)
$oauthProvenanceDropIndex = $oauthProvenanceSql.LastIndexOf('DROP PROCEDURE IF EXISTS `u3w_migrate_independent_board_oauth_receipt_provenance_20260721`$$', [StringComparison]::Ordinal)
$oauthProvenanceOrderedIndices = @(
    $oauthProvenanceServerProfileIndex,
    $oauthProvenanceSuccessorDigestIndex,
    $groupConcatCaptureIndex,
    $groupConcatRaiseIndex,
    $oauthProvenanceLockIndex,
    $oauthProvenanceFoundationAuditIndex,
    $oauthProvenanceStateAuditIndex,
    $oauthProvenancePreflightIndex,
    $oauthProvenanceShapeAuditIndex,
    $oauthProvenanceRunningInsertIndex,
    $oauthProvenanceAlterIndex,
    $oauthProvenanceFinalReadIndex,
    $oauthProvenanceFinalLineageIndex,
    $oauthProvenanceReceiptFinalizationIndex,
    $oauthProvenanceReceiptLockIndex,
    $oauthProvenancePromotionIndex,
    $oauthProvenanceReceiptCommitIndex,
    $successfulGroupConcatRestoreIndex,
    $oauthProvenanceReleaseIndex,
    $oauthProvenanceCallIndex,
    $oauthProvenanceDropIndex
)
$oauthProvenanceOrderingValid = $oauthProvenanceOrderedIndices.Count -gt 0 -and
    -not ($oauthProvenanceOrderedIndices -contains -1)
for ($index = 1; $oauthProvenanceOrderingValid -and $index -lt $oauthProvenanceOrderedIndices.Count; $index++) {
    if ($oauthProvenanceOrderedIndices[$index] -le $oauthProvenanceOrderedIndices[$index - 1]) {
        $oauthProvenanceOrderingValid = $false
    }
}
if (-not $oauthProvenanceOrderingValid) {
    $errors.Add('Independent Board OAuth provenance order must be profile/digests, GROUP_CONCAT ceiling, lock, 033/state/lineage/shape audits, RUNNING, DDL, final metadata/lineage, locked receipt promotion, COMMIT, restore, release, CALL and DROP')
}

$oauthConsentIntentSqlPath = Join-Path $sqlRoot 'update_20260721_independent_board_oauth_consent_intent_lineage.sql'
$oauthConsentIntentManifestSteps = @($declarativeManifest.steps | Where-Object {
    [string]$_.version -eq 'public_init_035'
})
$expectedOauthConsentIntentSha256 = if ($oauthConsentIntentManifestSteps.Count -eq 1) {
    [string]$oauthConsentIntentManifestSteps[0].sha256
} else { '' }
if ($expectedOauthConsentIntentSha256 -notmatch '^[0-9a-f]{64}$') {
    $errors.Add('public_init_035 manifest SHA-256 is missing or invalid')
}
$requiredOauthTransactionRunnerNeedles = @(
    '[switch]$DirectOnly',
    'ordinal_position = 28',
    "oauthConsentIntentMigration = '$expectedOauthConsentIntentSha256'",
    "mode = if (`$DirectOnly) { 'direct_only' } else { 'direct_and_canonical' }",
    'canonicalDatabase = if ($DirectOnly) { $null } else { $canonicalDatabase }',
    'DirectOnly requested; canonical initializer phase remains intentionally unexecuted.'
)
foreach ($needle in $requiredOauthTransactionRunnerNeedles) {
    if (-not $mysqlTransactionItRunnerSource.Contains($needle)) {
        $errors.Add("MySQL transaction runner is missing the exact OAuth consent-intent gate: $needle")
    }
}
if ($mysqlTransactionItRunnerSource -match 'ordinal_position\s*=\s*29') {
    $errors.Add('MySQL transaction runner must require family consent ordinal 28, never 29')
}
$oauthConsentIntentSha256 = ''
if (-not (Test-Path -LiteralPath $oauthConsentIntentSqlPath -PathType Leaf)) {
    $errors.Add('Independent Board OAuth consent-intent lineage migration is missing')
    $oauthConsentIntentSql = ''
}
else {
    $oauthConsentIntentSql = Get-Content -LiteralPath $oauthConsentIntentSqlPath -Raw -Encoding UTF8
    $oauthConsentIntentSha256 = (Get-FileHash -LiteralPath $oauthConsentIntentSqlPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not [string]::Equals(
            $oauthConsentIntentSha256,
            $expectedOauthConsentIntentSha256,
            [StringComparison]::Ordinal)) {
        $errors.Add("public_init_035 byte contract drifted: expected SHA-256 $expectedOauthConsentIntentSha256, found $oauthConsentIntentSha256")
    }
}
$requiredOauthConsentIntentNeedles = @(
    '20260721_independent_board_oauth_consent_intent_lineage_v1',
    'public_init_035',
    'exact APPLIED public_init_033 and public_init_034 shapes',
    'exact MySQL 8.0.30 or 8.4.8 metadata baselines only',
    "INSTR(LOWER(VERSION()), 'mariadb') > 0",
    "CAST('8.0.30' AS BINARY), CAST('8.4.8' AS BINARY)",
    'MySQL Community Server - GPL',
    "WHEN '8.0.30' THEN '016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539'",
    "WHEN '8.4.8' THEN '3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f'",
    'DECLARE stage_prefix CHAR(3)',
    "WHEN '8.0.30:000' THEN '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'",
    "WHEN '8.0.30:100' THEN '85ee62cd04574bb7fe27a05c361bb8b7655144e8036b2ddb83a9837c7d88ca49'",
    "WHEN '8.0.30:110' THEN '300d34a5fcd2f6a55d121bee598edc6e6a509dd4a1c80a04cf0b5b1d788e0574'",
    "WHEN '8.0.30:111' THEN '0aca71671f1632851a45f57eeacfa18f23529d628ddaa159dc2ca7cb472a0d24'",
    "WHEN '8.0.30:111' THEN 'e222fafb27746a52bdc6e31c08a9b8cb3e34c2a8eb88bad9345d53b496d0c612'",
    "WHEN '8.4.8:000' THEN '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'",
    "WHEN '8.4.8:000' THEN 'd8878ff64c7e897ddd8d42db6f0afd1a264d2cc8c1794d155051f0cd1638103b'",
    "WHEN '8.4.8:100' THEN '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'",
    "WHEN '8.4.8:100' THEN '85ee62cd04574bb7fe27a05c361bb8b7655144e8036b2ddb83a9837c7d88ca49'",
    "WHEN '8.4.8:110' THEN '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0'",
    "WHEN '8.4.8:110' THEN '300d34a5fcd2f6a55d121bee598edc6e6a509dd4a1c80a04cf0b5b1d788e0574'",
    "WHEN '8.4.8:111' THEN 'e222fafb27746a52bdc6e31c08a9b8cb3e34c2a8eb88bad9345d53b496d0c612'",
    "WHEN '8.4.8:111' THEN '0aca71671f1632851a45f57eeacfa18f23529d628ddaa159dc2ca7cb472a0d24'",
    'OAuth consent-intent exact stage baseline is unavailable',
    'OAuth consent-intent shape must be a 000, 100, 110 or 111 prefix',
    'ordinal_position = 28',
    'SET migration_lock_name = SHA2(',
    "CONCAT(DATABASE(), ':20260721_independent_board_oauth_consent_intent_lineage_v1')",
    'CHAR_LENGTH(migration_lock_name) <> 64',
    'GET_LOCK(migration_lock_name, 30)',
    'IS_USED_LOCK(migration_lock_name)',
    'RELEASE_LOCK(migration_lock_name)',
    'migration_lock_owner <> current_connection',
    'migration_lock_owner <> CONNECTION_ID()',
    'DECLARE EXIT HANDLER FOR SQLEXCEPTION',
    'ROLLBACK;',
    '20260721_independent_board_oauth_foundation_v1',
    '20260721_independent_board_oauth_receipt_provenance_v1',
    'APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness',
    'RUNNING:Independent Board OAuth consent intent lineage',
    'APPLIED:Independent Board OAuth consent intent lineage',
    "SET migration_stage = 'legacy-data-fail-closed'",
    'Legacy OAuth data cannot be assigned a consent intent without reviewed evidence',
    "SET migration_stage = 'request-consent-intent-ddl'",
    'ALTER TABLE `fbs_oauth_authorization_request`',
    'ADD COLUMN `consent_intent`',
    'chk_oauth_request_consent_intent',
    'uk_oauth_request_id_consent',
    "SET migration_stage = 'code-consent-intent-ddl'",
    'ALTER TABLE `fbs_oauth_authorization_code`',
    'chk_oauth_code_consent_intent',
    'uk_oauth_code_id_consent',
    'idx_oauth_code_request_consent',
    'fk_oauth_code_request_consent',
    "SET migration_stage = 'family-consent-intent-ddl'",
    'ALTER TABLE `fbs_oauth_token_family`',
    'chk_oauth_family_consent_intent',
    'idx_oauth_family_code_consent',
    'fk_oauth_family_code_consent',
    'FIRST_CONNECT',
    'EXPLICIT_REAUTHORIZATION',
    "SET migration_stage = 'post-ddl-current-read'",
    'request_stage_complete <> 1 OR code_stage_complete <> 1 OR family_stage_complete <> 1',
    'CREATE TRIGGER IF NOT EXISTS `trg_oauth_request_consent_intent_once`',
    'OAuth consent intent is immutable',
    'OAuth consent intent must be set by a decision transition',
    'u3w_finalize_ib_oauth_consent_intent_20260721',
    "SET migration_stage = 'final-raw-metadata-current-read'",
    "SET migration_stage = 'final-data-current-read'",
    'OAuth APPLIED consent-intent lineage has drifted',
    "SET migration_stage = 'receipt-finalization'",
    'FOR UPDATE;',
    'OAuth consent-intent APPLIED receipt is not exact',
    'DROP PROCEDURE IF EXISTS `u3w_assert_ib_oauth_consent_intent_20260721`$$'
)
foreach ($needle in $requiredOauthConsentIntentNeedles) {
    if (-not $oauthConsentIntentSql.Contains($needle)) {
        $errors.Add("Independent Board OAuth consent-intent SQL is missing required contract: $needle")
    }
}
if ($oauthConsentIntentSql -match '__[A-Z0-9_]+__') {
    $errors.Add('Independent Board OAuth consent-intent SQL contains an unresolved placeholder')
}
if ($oauthConsentIntentSql -match 'ordinal_position\s*=\s*29') {
    $errors.Add('Independent Board OAuth consent-intent SQL must require family consent ordinal 28')
}
foreach ($baselineServerVersion in @('8.0.30', '8.4.8')) {
    foreach ($stagePrefix in @('000', '100', '110', '111')) {
        $stageNeedle = "WHEN '$baselineServerVersion`:$stagePrefix' THEN"
        if (@([regex]::Matches(
                $oauthConsentIntentSql,
                [regex]::Escape($stageNeedle))).Count -ne 2) {
            $errors.Add("Independent Board OAuth consent-intent SQL must lock both $baselineServerVersion column and CHECK digests for stage $stagePrefix")
        }
    }
}
$oauthConsentClassificationIndex = $oauthConsentIntentSql.IndexOf(
    '-- Classify every successor object before selecting a stage-bound raw baseline.',
    [StringComparison]::Ordinal)
$oauthConsentBaselineSelectionIndex = $oauthConsentIntentSql.IndexOf(
    "SET expected_column_digest = CASE CONCAT(server_version, ':', stage_prefix)",
    [StringComparison]::Ordinal)
if ($oauthConsentClassificationIndex -lt 0 -or
    $oauthConsentBaselineSelectionIndex -le $oauthConsentClassificationIndex) {
    $errors.Add('Independent Board OAuth consent-intent SQL must classify exact named successor stages before selecting raw metadata baselines')
}
if ($oauthConsentIntentSql -match '(?im)^\s*(?:CREATE|DROP)\s+TABLE\b' -or
    $oauthConsentIntentSql -match '(?im)^\s*TRUNCATE\s+TABLE\b' -or
    $oauthConsentIntentSql -match '(?im)^\s*(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM)\s+`?fbs_oauth_') {
    $errors.Add('Independent Board OAuth consent-intent migration must remain additive and must not create/drop tables or rewrite OAuth business rows')
}
$oauthConsentIntentAlterMatches = @([regex]::Matches(
    $oauthConsentIntentSql,
    '(?im)^\s*ALTER\s+TABLE\s+`(?<table>fbs_oauth_[a-z_]+)`'))
$expectedOauthConsentIntentAlterTables = @(
    'fbs_oauth_authorization_request',
    'fbs_oauth_authorization_code',
    'fbs_oauth_token_family'
)
$actualOauthConsentIntentAlterTables = @($oauthConsentIntentAlterMatches |
    ForEach-Object { $_.Groups['table'].Value })
if (($actualOauthConsentIntentAlterTables -join '|') -ne ($expectedOauthConsentIntentAlterTables -join '|')) {
    $errors.Add("Independent Board OAuth consent-intent migration must contain exactly request, code and family additive DDL stages in order; found '$($actualOauthConsentIntentAlterTables -join ',')'")
}
if (@([regex]::Matches(
        $oauthConsentIntentSql,
        '(?im)^\s*CREATE\s+TRIGGER\s+IF\s+NOT\s+EXISTS\s+`trg_oauth_request_consent_intent_once`')).Count -ne 1) {
    $errors.Add('Independent Board OAuth consent-intent migration must create exactly one guarded request immutability trigger')
}
if (@([regex]::Matches($oauthConsentIntentSql, [regex]::Escape("INSTR(LOWER(VERSION()), 'mariadb') > 0"))).Count -ne 2 -or
    @([regex]::Matches($oauthConsentIntentSql, [regex]::Escape('CAST(@@version_comment AS BINARY)'))).Count -ne 2) {
    $errors.Add('Independent Board OAuth consent-intent migration and finalizer must each enforce the exact MySQL Community profile')
}

$oauthConsentIntentMigrateDefinitionIndex = $oauthConsentIntentSql.IndexOf('CREATE PROCEDURE `u3w_migrate_ib_oauth_consent_intent_20260721`()', [StringComparison]::Ordinal)
$oauthConsentIntentProfileIndex = if ($oauthConsentIntentMigrateDefinitionIndex -ge 0) {
    $oauthConsentIntentSql.IndexOf('SET server_version = VERSION();', $oauthConsentIntentMigrateDefinitionIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthConsentIntentLockIndex = $oauthConsentIntentSql.IndexOf('SELECT GET_LOCK(migration_lock_name, 30)', [StringComparison]::Ordinal)
$oauthConsentIntentReceiptAuditIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'prerequisite-receipt-audit'", [StringComparison]::Ordinal)
$oauthConsentIntentMetadataPreflightIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'raw-metadata-preflight'", [StringComparison]::Ordinal)
$oauthConsentIntentLegacyRejectIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'legacy-data-fail-closed'", [StringComparison]::Ordinal)
$oauthConsentIntentRunningIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'running-receipt-create'", [StringComparison]::Ordinal)
$oauthConsentIntentRequestDdlIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'request-consent-intent-ddl'", [StringComparison]::Ordinal)
$oauthConsentIntentCodeDdlIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'code-consent-intent-ddl'", [StringComparison]::Ordinal)
$oauthConsentIntentFamilyDdlIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'family-consent-intent-ddl'", [StringComparison]::Ordinal)
$oauthConsentIntentPostDdlReadIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'post-ddl-current-read'", [StringComparison]::Ordinal)
$oauthConsentIntentMigrateCallIndex = $oauthConsentIntentSql.IndexOf('CALL `u3w_migrate_ib_oauth_consent_intent_20260721`()$$', [StringComparison]::Ordinal)
$oauthConsentIntentMigrateDropIndex = if ($oauthConsentIntentMigrateCallIndex -ge 0) {
    $oauthConsentIntentSql.IndexOf('DROP PROCEDURE IF EXISTS `u3w_migrate_ib_oauth_consent_intent_20260721`$$', $oauthConsentIntentMigrateCallIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthConsentIntentTriggerIndex = $oauthConsentIntentSql.IndexOf('CREATE TRIGGER IF NOT EXISTS `trg_oauth_request_consent_intent_once`', [StringComparison]::Ordinal)
$oauthConsentIntentFinalizerDefinitionIndex = $oauthConsentIntentSql.IndexOf('CREATE PROCEDURE `u3w_finalize_ib_oauth_consent_intent_20260721`()', [StringComparison]::Ordinal)
$oauthConsentIntentFinalMetadataIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'final-raw-metadata-current-read'", [StringComparison]::Ordinal)
$oauthConsentIntentFinalDataIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'final-data-current-read'", [StringComparison]::Ordinal)
$oauthConsentIntentReceiptFinalizationIndex = $oauthConsentIntentSql.IndexOf("SET migration_stage = 'receipt-finalization'", [StringComparison]::Ordinal)
$oauthConsentIntentReceiptLockIndex = if ($oauthConsentIntentReceiptFinalizationIndex -ge 0) {
    $oauthConsentIntentSql.IndexOf('FOR UPDATE;', $oauthConsentIntentReceiptFinalizationIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthConsentIntentPromotionIndex = if ($oauthConsentIntentReceiptLockIndex -ge 0) {
    $oauthConsentIntentSql.IndexOf('SET `description` = ''APPLIED:Independent Board OAuth consent intent lineage''', $oauthConsentIntentReceiptLockIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthConsentIntentCommitIndex = if ($oauthConsentIntentPromotionIndex -ge 0) {
    $oauthConsentIntentSql.IndexOf('COMMIT;', $oauthConsentIntentPromotionIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthConsentIntentReleaseIndex = if ($oauthConsentIntentCommitIndex -ge 0) {
    $oauthConsentIntentSql.IndexOf('SELECT RELEASE_LOCK(migration_lock_name)', $oauthConsentIntentCommitIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthConsentIntentFinalizerCallIndex = $oauthConsentIntentSql.IndexOf('CALL `u3w_finalize_ib_oauth_consent_intent_20260721`()$$', [StringComparison]::Ordinal)
$oauthConsentIntentFinalizerDropIndex = if ($oauthConsentIntentFinalizerCallIndex -ge 0) {
    $oauthConsentIntentSql.IndexOf('DROP PROCEDURE IF EXISTS `u3w_finalize_ib_oauth_consent_intent_20260721`$$', $oauthConsentIntentFinalizerCallIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthConsentIntentAssertDropIndex = $oauthConsentIntentSql.LastIndexOf('DROP PROCEDURE IF EXISTS `u3w_assert_ib_oauth_consent_intent_20260721`$$', [StringComparison]::Ordinal)
$oauthConsentIntentOrderedIndices = @(
    $oauthConsentIntentMigrateDefinitionIndex,
    $oauthConsentIntentProfileIndex,
    $oauthConsentIntentLockIndex,
    $oauthConsentIntentReceiptAuditIndex,
    $oauthConsentIntentMetadataPreflightIndex,
    $oauthConsentIntentLegacyRejectIndex,
    $oauthConsentIntentRunningIndex,
    $oauthConsentIntentRequestDdlIndex,
    $oauthConsentIntentCodeDdlIndex,
    $oauthConsentIntentFamilyDdlIndex,
    $oauthConsentIntentPostDdlReadIndex,
    $oauthConsentIntentMigrateCallIndex,
    $oauthConsentIntentMigrateDropIndex,
    $oauthConsentIntentTriggerIndex,
    $oauthConsentIntentFinalizerDefinitionIndex,
    $oauthConsentIntentFinalMetadataIndex,
    $oauthConsentIntentFinalDataIndex,
    $oauthConsentIntentReceiptFinalizationIndex,
    $oauthConsentIntentReceiptLockIndex,
    $oauthConsentIntentPromotionIndex,
    $oauthConsentIntentCommitIndex,
    $oauthConsentIntentReleaseIndex,
    $oauthConsentIntentFinalizerCallIndex,
    $oauthConsentIntentFinalizerDropIndex,
    $oauthConsentIntentAssertDropIndex
)
$oauthConsentIntentOrderingValid = $oauthConsentIntentOrderedIndices.Count -gt 0 -and
    -not ($oauthConsentIntentOrderedIndices -contains -1)
for ($index = 1; $oauthConsentIntentOrderingValid -and $index -lt $oauthConsentIntentOrderedIndices.Count; $index++) {
    if ($oauthConsentIntentOrderedIndices[$index] -le $oauthConsentIntentOrderedIndices[$index - 1]) {
        $oauthConsentIntentOrderingValid = $false
    }
}
if (-not $oauthConsentIntentOrderingValid) {
    $errors.Add('Independent Board OAuth consent-intent order must be profile/lock, prerequisite+metadata+legacy gates, RUNNING, request/code/family DDL, current-read, migrate cleanup, trigger, finalizer metadata/data/locked receipt, COMMIT/release and helper cleanup')
}

$oauthRefreshSecuritySqlPath = Join-Path $sqlRoot 'update_20260721_independent_board_oauth_refresh_security.sql'
$oauthRefreshSecurityManifestSteps = @($declarativeManifest.steps | Where-Object {
    [string]$_.version -eq 'public_init_036'
})
$expectedOauthRefreshSecuritySha256 = if ($oauthRefreshSecurityManifestSteps.Count -eq 1) {
    [string]$oauthRefreshSecurityManifestSteps[0].sha256
} else { '' }
if ($expectedOauthRefreshSecuritySha256 -notmatch '^[0-9a-f]{64}$') {
    $errors.Add('public_init_036 manifest SHA-256 is missing or invalid')
}
$oauthRefreshSecuritySha256 = ''
if (-not (Test-Path -LiteralPath $oauthRefreshSecuritySqlPath -PathType Leaf)) {
    $errors.Add('Independent Board OAuth refresh-security migration is missing')
    $oauthRefreshSecuritySql = ''
}
else {
    $oauthRefreshSecuritySql = Get-Content -LiteralPath $oauthRefreshSecuritySqlPath -Raw -Encoding UTF8
    $oauthRefreshSecuritySha256 = (Get-FileHash -LiteralPath $oauthRefreshSecuritySqlPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not [string]::Equals(
            $oauthRefreshSecuritySha256,
            $expectedOauthRefreshSecuritySha256,
            [StringComparison]::Ordinal)) {
        $errors.Add("public_init_036 byte contract drifted: expected SHA-256 $expectedOauthRefreshSecuritySha256, found $oauthRefreshSecuritySha256")
    }
}
$requiredOauthRefreshSecurityNeedles = @(
    '20260721_independent_board_oauth_refresh_security_v1',
    'public_init_036',
    'exact MySQL Community 8.0.30 or 8.4.8 metadata baselines only',
    '@u3w_ib_oauth_refresh_profile_guard',
    'EXECUTE u3w_ib_oauth_refresh_profile_guard_stmt',
    'Independent Board OAuth refresh security supports exact MySQL baselines only',
    "DATABASE(), ':20260721_independent_board_oauth_refresh_security_v1')",
    'GET_LOCK(migration_lock_name, 10)',
    'IS_USED_LOCK(migration_lock_name)',
    'DO RELEASE_LOCK(migration_lock_name)',
    'DECLARE EXIT HANDLER FOR SQLEXCEPTION',
    'RUNNING:Independent Board OAuth refresh security receipt v2',
    'APPLIED:Independent Board OAuth refresh security receipt v2',
    'OAuth refresh-security DDL exists without its RUNNING receipt',
    'OAuth refresh-security APPLIED receipt is ahead of its exact S3 shape',
    'OAuth request data violates strict consent-intent NULL pairing',
    'Legacy OAuth rotation or replay receipts cannot be trusted as receipt v2',
    "SET migration_stage = 's1-strict-consent-null-check'",
    'consent_intent IS NOT NULL',
    "SET migration_stage = 's2-refresh-subject-support-half'",
    'uk_oauth_token_receipt_generation_type_scope',
    'idx_oauth_token_family_lock_order',
    "SET migration_stage = 's2-causation-target-support-complete'",
    'uk_oauth_receipt_causation_scope',
    'idx_oauth_receipt_family_client_lock_order',
    "SET migration_stage = 's2-connector-receipt-lock-support-complete'",
    'idx_connector_binding_receipt_lock_order',
    'token-only and token+receipt shapes are recoverable internal S2 prefixes',
    'external S2 exists only after token, receipt and connector groups are all',
    "SET migration_stage = 's3-receipt-v2-ddl'",
    'receipt_format_version',
    'subject_generation',
    'result_generation',
    'causation_receipt_id',
    'before_state_digest',
    'after_state_digest',
    'subject_token_type',
    'security_event_slot',
    'idx_oauth_receipt_causation_scope',
    'idx_oauth_receipt_refresh_subject',
    'uk_oauth_receipt_security_event_slot',
    'fk_oauth_receipt_refresh_subject',
    'fk_oauth_receipt_causation_scope',
    'chk_oauth_receipt_format_version',
    'chk_oauth_receipt_v2_shape',
    'before_state_digest <> after_state_digest',
    'causation_receipt_id <> receipt_id',
    'subject_generation < 4294967295',
    'result_generation = subject_generation + 1',
    "action = 'REFRESH_REPLAY_DETECTED'",
    "actor_type = 'CLIENT'",
    'actor_user_id IS NULL',
    '09ac52b1ef4f2a095507a243d3a7048aa592e7b18b8057a2132d594a9d484197',
    '51075bde94d5f08b4eeef573f7c7730ba22ec596dc02e0dae929682ce8cd7952',
    '914545f8794180df710cd05a9ee3430e21a78995bfd4142e4dc3d54e9330780d',
    'd3b99d9c1923c59729166110ceeddd0c6987ef9806e1d644add9ca018ca8166a',
    '1e103fc572908bef3353dfdec2a2076a76c68a5ec9dd3f8b5f938a486d431d22',
    '504a017fe7c4a8ecc4c60619ea8beaf5e7d4aa207fcffe536ff47b8e1d9919b3',
    'd30ff1730699536a7dcfcc76ac026a2744bc10a07ea028d01cc5cfea5ddd05ad',
    "SET migration_stage = 'final-current-read'",
    "SET migration_stage = 'receipt-finalization'",
    'FOR UPDATE;',
    'OAuth refresh-security APPLIED receipt is not exact',
    'Cleanup after the final COMMIT is deliberately non-asserting',
    'DROP PROCEDURE IF EXISTS `u3w_assert_ib_oauth_refresh_security_20260721`$$'
)
foreach ($needle in $requiredOauthRefreshSecurityNeedles) {
    if (-not $oauthRefreshSecuritySql.Contains($needle)) {
        $errors.Add("Independent Board OAuth refresh-security SQL is missing required contract: $needle")
    }
}
if ($oauthRefreshSecuritySql -match '__[A-Z0-9_]+__' -or
    $oauthRefreshSecuritySql -match 'REPLACE_WITH_') {
    $errors.Add('Independent Board OAuth refresh-security SQL contains an unresolved placeholder')
}
if ($oauthRefreshSecuritySql -match '(?im)^\s*(?:CREATE|DROP)\s+TABLE\b' -or
    $oauthRefreshSecuritySql -match '(?im)^\s*TRUNCATE\s+TABLE\b' -or
    $oauthRefreshSecuritySql -match '(?im)^\s*(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM)\s+`?fbs_oauth_') {
    $errors.Add('Independent Board OAuth refresh-security migration must not create/drop tables or rewrite OAuth business rows')
}
if ($oauthRefreshSecuritySql -match '(?im)^\s*(?:CREATE|DROP)\s+TRIGGER\b') {
    $errors.Add('Independent Board OAuth refresh-security migration must preserve and audit the exact three predecessor triggers')
}
$oauthRefreshAlterMatches = @([regex]::Matches(
    $oauthRefreshSecuritySql,
    '(?im)^\s*ALTER\s+TABLE\s+(?:`)?(?<table>fbs_(?:oauth_[a-z_]+|connector_binding_receipt))(?:`)?'))
$expectedOauthRefreshAlterTables = @(
    'fbs_oauth_authorization_request',
    'fbs_oauth_token',
    'fbs_oauth_receipt',
    'fbs_connector_binding_receipt',
    'fbs_oauth_receipt'
)
$actualOauthRefreshAlterTables = @($oauthRefreshAlterMatches |
    ForEach-Object { $_.Groups['table'].Value })
if (($actualOauthRefreshAlterTables -join '|') -ne ($expectedOauthRefreshAlterTables -join '|')) {
    $errors.Add("Independent Board OAuth refresh-security migration must contain exactly request, token, causation-support receipt, connector lock support and receipt-v2 DDL in order; found '$($actualOauthRefreshAlterTables -join ',')'")
}
if (@([regex]::Matches(
        $oauthRefreshSecuritySql,
        [regex]::Escape('before_state_digest <> after_state_digest'))).Count -ne 2 -or
    @([regex]::Matches(
        $oauthRefreshSecuritySql,
        [regex]::Escape('causation_receipt_id <> receipt_id'))).Count -ne 2 -or
    @([regex]::Matches(
        $oauthRefreshSecuritySql,
        [regex]::Escape('subject_generation < 4294967295'))).Count -ne 2) {
    $errors.Add('Independent Board OAuth receipt-v2 CHECK must reject equal state digests, self-causation and UINT_MAX generations in both security actions')
}
$oauthRefreshTopGuardIndex = $oauthRefreshSecuritySql.IndexOf(
    'EXECUTE u3w_ib_oauth_refresh_profile_guard_stmt', [StringComparison]::Ordinal)
$oauthRefreshFirstPersistentDdlIndex = $oauthRefreshSecuritySql.IndexOf(
    'DROP PROCEDURE IF EXISTS `u3w_assert_ib_oauth_refresh_security_20260721`',
    [StringComparison]::Ordinal)
$oauthRefreshRunningIndex = $oauthRefreshSecuritySql.IndexOf(
    "SET migration_stage = 'running-receipt-create'", [StringComparison]::Ordinal)
$oauthRefreshS1Index = $oauthRefreshSecuritySql.IndexOf(
    "SET migration_stage = 's1-strict-consent-null-check'", [StringComparison]::Ordinal)
$oauthRefreshS2HalfIndex = $oauthRefreshSecuritySql.IndexOf(
    "SET migration_stage = 's2-refresh-subject-support-half'", [StringComparison]::Ordinal)
$oauthRefreshS2Index = $oauthRefreshSecuritySql.IndexOf(
    "SET migration_stage = 's2-causation-target-support-complete'", [StringComparison]::Ordinal)
$oauthRefreshS2ConnectorIndex = $oauthRefreshSecuritySql.IndexOf(
    "SET migration_stage = 's2-connector-receipt-lock-support-complete'", [StringComparison]::Ordinal)
$oauthRefreshS3Index = $oauthRefreshSecuritySql.IndexOf(
    "SET migration_stage = 's3-receipt-v2-ddl'", [StringComparison]::Ordinal)
$oauthRefreshFinalReadIndex = $oauthRefreshSecuritySql.IndexOf(
    "SET migration_stage = 'final-current-read'", [StringComparison]::Ordinal)
$oauthRefreshReceiptIndex = $oauthRefreshSecuritySql.IndexOf(
    "SET migration_stage = 'receipt-finalization'", [StringComparison]::Ordinal)
$oauthRefreshCommitIndex = if ($oauthRefreshReceiptIndex -ge 0) {
    $oauthRefreshSecuritySql.IndexOf('COMMIT;', $oauthRefreshReceiptIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthRefreshReleaseIndex = if ($oauthRefreshCommitIndex -ge 0) {
    $oauthRefreshSecuritySql.IndexOf('DO RELEASE_LOCK(migration_lock_name)', $oauthRefreshCommitIndex, [StringComparison]::Ordinal)
} else { -1 }
$oauthRefreshCallIndex = $oauthRefreshSecuritySql.IndexOf(
    'CALL u3w_migrate_ib_oauth_refresh_security_20260721()$$', [StringComparison]::Ordinal)
$oauthRefreshAssertDropIndex = $oauthRefreshSecuritySql.LastIndexOf(
    'DROP PROCEDURE IF EXISTS `u3w_assert_ib_oauth_refresh_security_20260721`$$',
    [StringComparison]::Ordinal)
$oauthRefreshOrderedIndices = @(
    $oauthRefreshTopGuardIndex, $oauthRefreshFirstPersistentDdlIndex,
    $oauthRefreshRunningIndex, $oauthRefreshS1Index, $oauthRefreshS2HalfIndex,
    $oauthRefreshS2Index, $oauthRefreshS2ConnectorIndex, $oauthRefreshS3Index,
    $oauthRefreshFinalReadIndex,
    $oauthRefreshReceiptIndex, $oauthRefreshCommitIndex, $oauthRefreshReleaseIndex,
    $oauthRefreshCallIndex, $oauthRefreshAssertDropIndex
)
$oauthRefreshOrderingValid = -not ($oauthRefreshOrderedIndices -contains -1)
for ($index = 1; $oauthRefreshOrderingValid -and $index -lt $oauthRefreshOrderedIndices.Count; $index++) {
    if ($oauthRefreshOrderedIndices[$index] -le $oauthRefreshOrderedIndices[$index - 1]) {
        $oauthRefreshOrderingValid = $false
    }
}
if (-not $oauthRefreshOrderingValid) {
    $errors.Add('Independent Board OAuth refresh-security order must be top-level profile guard, helpers, RUNNING, S1, token and receipt internal S2 prefixes, complete connector-backed S2, S3, final current-read, locked receipt COMMIT, non-asserting release, CALL and helper cleanup')
}
if ($oauthRefreshCommitIndex -ge 0) {
    $oauthRefreshPostCommitSql = $oauthRefreshSecuritySql.Substring(
        $oauthRefreshCommitIndex + 'COMMIT;'.Length,
        $oauthRefreshCallIndex - ($oauthRefreshCommitIndex + 'COMMIT;'.Length))
    if ($oauthRefreshPostCommitSql -match '(?im)^\s*SIGNAL\b') {
        $errors.Add('Independent Board OAuth refresh-security migration must not contain a fallible assertion after its final COMMIT')
    }
}

$creditLedgerSqlPath = Join-Path $sqlRoot 'update_20260722_independent_board_credit_ledger.sql'
$creditLedgerManifestSteps = @($declarativeManifest.steps | Where-Object {
    [string]$_.version -eq 'public_init_038'
})
$expectedCreditLedgerSha256 = '8d0b2ee9ae6c31f75d8a0339a486c19b2864e200dc76f2abd54f229f727b7c18'
if ($creditLedgerManifestSteps.Count -ne 1) {
    $errors.Add('public_init_038 manifest entry must exist exactly once')
}
else {
    $creditLedgerManifestStep = $creditLedgerManifestSteps[0]
    if ([string]$creditLedgerManifestStep.description -cne 'Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger' -or
        [string]$creditLedgerManifestStep.file -cne 'update_20260722_independent_board_credit_ledger.sql' -or
        [string]$creditLedgerManifestStep.sha256 -cne $expectedCreditLedgerSha256) {
        $errors.Add('public_init_038 manifest description, file or exact SHA-256 has drifted')
    }
}
$creditLedgerSha256 = ''
if (-not (Test-Path -LiteralPath $creditLedgerSqlPath -PathType Leaf)) {
    $errors.Add('Independent Board credit-ledger migration is missing')
    $creditLedgerSql = ''
}
else {
    $creditLedgerSql = Get-Content -LiteralPath $creditLedgerSqlPath -Raw -Encoding UTF8
    $creditLedgerSha256 = (Get-FileHash -LiteralPath $creditLedgerSqlPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not [string]::Equals(
            $creditLedgerSha256,
            $expectedCreditLedgerSha256,
            [StringComparison]::Ordinal)) {
        $errors.Add("public_init_038 byte contract drifted: expected SHA-256 $expectedCreditLedgerSha256, found $creditLedgerSha256")
    }
}
$requiredCreditLedgerNeedles = @(
    'FBSir Independent Board USER_GLOBAL/FBS_POINTS shadow ledger',
    '20260722_independent_board_credit_ledger_v1',
    'Public manifest step: public_init_038',
    'Target: exact MySQL Community 8.0.30 or 8.4.8 raw-metadata profiles',
    "VERSION() NOT IN ('8.0.30', '8.4.8')",
    "@@version_comment <> 'MySQL Community Server - GPL'",
    'Credit ledger migration requires exact MySQL Community 8.0.30 or 8.4.8',
    'SET previous_group_concat_max_len = @@SESSION.group_concat_max_len',
    'SET SESSION group_concat_max_len = 1048576',
    'SET SESSION group_concat_max_len = previous_group_concat_max_len',
    "DATABASE(), ':20260722_independent_board_credit_ledger_v1'",
    'CHAR_LENGTH(migration_lock_name) <> 64',
    'GET_LOCK(migration_lock_name, 30)',
    'IS_USED_LOCK(migration_lock_name)',
    'RELEASE_LOCK(migration_lock_name)',
    'DECLARE EXIT HANDLER FOR SQLEXCEPTION',
    "SET migration_stage = 'prerequisite-audit'",
    "table_name IN ('u3w_schema_migration', 'sys_user')",
    "column_name = 'status'",
    "column_name = 'del_flag'",
    "column_name = 'update_time'",
    "extra NOT LIKE '%GENERATED%'",
    'Credit ledger requires the five-column sys_user runtime projection',
    "SET migration_stage = 'partial-state-audit'",
    'Credit ledger objects exist without the exact migration receipt',
    'Credit ledger receipt exists but its three-table set is incomplete',
    'CREATE TABLE IF NOT EXISTS `fbs_credit_account`',
    'CREATE TABLE IF NOT EXISTS `fbs_credit_operation`',
    'CREATE TABLE IF NOT EXISTS `fbs_credit_entry`',
    "DEFAULT 'USER'",
    "DEFAULT 'USER_GLOBAL'",
    "DEFAULT 'FBS_POINTS'",
    '`opening_balance` BIGINT NOT NULL',
    '`version` BIGINT UNSIGNED NOT NULL DEFAULT 0',
    '`last_entry_sequence` BIGINT UNSIGNED NOT NULL DEFAULT 0',
    "DEFAULT '0000000000000000000000000000000000000000000000000000000000000000'",
    '`idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL',
    '`request_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL',
    "'CUSTOMER_SUPPORT','SERVICE_RECOVERY','MIGRATION_CORRECTION'",
    "'DUPLICATE_GRANT','OPERATOR_ERROR','POLICY_VIOLATION'",
    '`actor_user_id` BIGINT NOT NULL',
    "DEFAULT 'COMMITTED'",
    "DEFAULT 'credit-entry-v1'",
    'Credit ledger exact 45-column contract has drifted',
    'Credit ledger exact 20-index contract has drifted',
    'Credit ledger exact foreign-key contract has drifted',
    'Credit ledger exact 22-check clause contract has drifted',
    '4717b6466040c2b33513ef1fb92ccb9044e08a00fea41edd7ba617192c9c8d00',
    '3975985e059c133c0e91ef274702e5d270e4392b7b55b724b1d0d031b76f23cf',
    '412a5276aca76d608111eecf0f2297dce0ebe299a35c6d106a4aa09da004ea67',
    '98ec9d2165952222da7c44b8cf63dfe361856aeedecb68a44a137d09c66cb1e2',
    'd04d7ef95cac75a2205dde9ffd89cdf23f5c387c3ece90a10926bb4feab62557',
    'fe61351bc245be129eedf83daa790444022925b660ed3f2c0241f9ec15917fc4',
    '29f2ebca36c354a73509468e251b4edb61f096056d3ded28d21d16b76e7b34dc',
    'CREATE TRIGGER IF NOT EXISTS `trg_credit_account_transition`',
    'CREATE TRIGGER IF NOT EXISTS `trg_credit_account_no_delete`',
    'CREATE TRIGGER IF NOT EXISTS `trg_credit_operation_no_update`',
    'CREATE TRIGGER IF NOT EXISTS `trg_credit_operation_no_delete`',
    'CREATE TRIGGER IF NOT EXISTS `trg_credit_entry_no_update`',
    'CREATE TRIGGER IF NOT EXISTS `trg_credit_entry_no_delete`',
    'NEW.version = OLD.version + 1',
    'NEW.last_entry_sequence = OLD.last_entry_sequence + 1',
    'e.sequence_no = NEW.last_entry_sequence',
    'e.balance_before = OLD.balance',
    'e.balance_after = NEW.balance',
    'e.previous_entry_hash = OLD.last_entry_hash',
    'e.entry_hash = NEW.last_entry_hash',
    'Credit ledger exact six-trigger contract has drifted',
    'u3w_migrate_independent_board_credit_ledger_20260722',
    'u3w_finalize_independent_board_credit_ledger_20260722',
    'u3w_assert_independent_board_credit_triggers_20260722',
    'Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger'
)
foreach ($needle in $requiredCreditLedgerNeedles) {
    if (-not $creditLedgerSql.Contains($needle)) {
        $errors.Add("Independent Board credit-ledger SQL is missing required contract: $needle")
    }
}
if ($creditLedgerSql -match '__[A-Z0-9_]+__' -or
    $creditLedgerSql -match 'REPLACE_WITH_') {
    $errors.Add('Independent Board credit-ledger SQL contains an unresolved placeholder')
}
$creditLedgerCreatedTables = @([regex]::Matches(
    $creditLedgerSql,
    '(?im)^\s*CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`(?<name>fbs_credit_[a-z_]+)`') |
    ForEach-Object { $_.Groups['name'].Value })
$expectedCreditLedgerTables = @('fbs_credit_account','fbs_credit_operation','fbs_credit_entry')
if (($creditLedgerCreatedTables -join '|') -cne ($expectedCreditLedgerTables -join '|')) {
    $errors.Add("Independent Board credit-ledger migration must create exactly account, operation and entry tables in order; found '$($creditLedgerCreatedTables -join ',')'")
}
$creditLedgerIndexDefinitions = @([regex]::Matches(
    $creditLedgerSql,
    '(?im)^\s*(?:PRIMARY\s+KEY\s*\(|(?:UNIQUE\s+)?KEY\s+`[^`]+`\s*\()'))
if ($creditLedgerIndexDefinitions.Count -ne 20) {
    $errors.Add("Independent Board credit-ledger migration must define exactly 20 indexes; found $($creditLedgerIndexDefinitions.Count)")
}
$creditLedgerForeignKeyDefinitions = @([regex]::Matches(
    $creditLedgerSql,
    '(?im)^\s*CONSTRAINT\s+`(?<name>fk_credit_[^`]+)`\s*$') |
    ForEach-Object { $_.Groups['name'].Value })
$expectedCreditLedgerForeignKeys = @(
    'fk_credit_account_user',
    'fk_credit_operation_account',
    'fk_credit_operation_reversal',
    'fk_credit_entry_operation'
)
if (($creditLedgerForeignKeyDefinitions -join '|') -cne ($expectedCreditLedgerForeignKeys -join '|') -or
    @([regex]::Matches($creditLedgerSql, 'ON UPDATE RESTRICT ON DELETE RESTRICT')).Count -ne 4) {
    $errors.Add('Independent Board credit-ledger migration must define exactly four named RESTRICT/RESTRICT foreign keys')
}
$creditLedgerCheckDefinitions = @([regex]::Matches(
    $creditLedgerSql,
    '(?im)^\s*CONSTRAINT\s+`chk_credit_[^`]+`\s*$'))
if ($creditLedgerCheckDefinitions.Count -ne 22) {
    $errors.Add("Independent Board credit-ledger migration must define exactly 22 named CHECK constraints; found $($creditLedgerCheckDefinitions.Count)")
}
$creditLedgerCreatedTriggers = @([regex]::Matches(
    $creditLedgerSql,
    '(?im)^\s*CREATE\s+TRIGGER\s+IF\s+NOT\s+EXISTS\s+`(?<name>trg_credit_[a-z_]+)`') |
    ForEach-Object { $_.Groups['name'].Value })
$expectedCreditLedgerTriggers = @(
    'trg_credit_account_transition',
    'trg_credit_account_no_delete',
    'trg_credit_operation_no_update',
    'trg_credit_operation_no_delete',
    'trg_credit_entry_no_update',
    'trg_credit_entry_no_delete'
)
if (($creditLedgerCreatedTriggers -join '|') -cne ($expectedCreditLedgerTriggers -join '|')) {
    $errors.Add("Independent Board credit-ledger migration must create exactly six guarded immutable triggers in order; found '$($creditLedgerCreatedTriggers -join ',')'")
}
if ($creditLedgerSql -match '(?im)^\s*(?:DROP\s+(?:TABLE|TRIGGER)|ALTER\s+TABLE|TRUNCATE\s+TABLE)\b' -or
    $creditLedgerSql -match '(?im)^\s*(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM)\s+`?fbs_credit_' -or
    $creditLedgerSql -match '(?i)FOREIGN\s+KEY\s*\(\s*`?actor_user_id`?\s*\)' -or
    $creditLedgerSql -match '(?i)ON\s+(?:UPDATE|DELETE)\s+(?:CASCADE|SET\s+NULL)') {
    $errors.Add('Independent Board credit-ledger migration must remain additive, preserve business rows, avoid actor_user_id FK coupling and use only RESTRICT referential actions')
}
$creditLedgerOrderedIndices = @(
    $creditLedgerSql.IndexOf('CREATE PROCEDURE `u3w_assert_independent_board_credit_triggers_20260722`()', [StringComparison]::Ordinal),
    $creditLedgerSql.IndexOf('CREATE PROCEDURE `u3w_migrate_independent_board_credit_ledger_20260722`()', [StringComparison]::Ordinal),
    $creditLedgerSql.IndexOf('SELECT GET_LOCK(migration_lock_name, 30)', [StringComparison]::Ordinal),
    $creditLedgerSql.IndexOf("SET migration_stage = 'partial-state-audit'", [StringComparison]::Ordinal),
    $creditLedgerSql.IndexOf('CREATE TABLE IF NOT EXISTS `fbs_credit_account`', [StringComparison]::Ordinal),
    $creditLedgerSql.IndexOf("SET migration_stage = 'table-audit'", [StringComparison]::Ordinal),
    $creditLedgerSql.IndexOf('CREATE PROCEDURE `u3w_finalize_independent_board_credit_ledger_20260722`()', [StringComparison]::Ordinal),
    $creditLedgerSql.IndexOf('CALL `u3w_migrate_independent_board_credit_ledger_20260722`()$$', [StringComparison]::Ordinal),
    $creditLedgerSql.IndexOf('CREATE TRIGGER IF NOT EXISTS `trg_credit_account_transition`', [StringComparison]::Ordinal),
    $creditLedgerSql.IndexOf('CALL `u3w_finalize_independent_board_credit_ledger_20260722`()$$', [StringComparison]::Ordinal),
    $creditLedgerSql.LastIndexOf('DROP PROCEDURE IF EXISTS `u3w_assert_independent_board_credit_triggers_20260722`$$', [StringComparison]::Ordinal)
)
$creditLedgerOrderingValid = -not ($creditLedgerOrderedIndices -contains -1)
for ($index = 1; $creditLedgerOrderingValid -and $index -lt $creditLedgerOrderedIndices.Count; $index++) {
    if ($creditLedgerOrderedIndices[$index] -le $creditLedgerOrderedIndices[$index - 1]) {
        $creditLedgerOrderingValid = $false
    }
}
if (-not $creditLedgerOrderingValid) {
    $errors.Add('Independent Board credit-ledger order must be trigger helper, migration+lock, partial-state gate, three-table DDL, exact audits, finalizer, migration call, guarded triggers, locked receipt finalization and helper cleanup')
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
$lastAdminMenuCurrentReadCall = $initSource.LastIndexOf('Assert-IndependentBoardAdminMenuCurrentState', [StringComparison]::Ordinal)
$lastLifecycleMenuCurrentReadCall = $initSource.LastIndexOf('Assert-IndependentBoardEntitlementLifecycleMenuCurrentState', [StringComparison]::Ordinal)
$lastConnectorBindingCurrentReadCall = $initSource.LastIndexOf('Assert-IndependentBoardConnectorBindingCurrentState', [StringComparison]::Ordinal)
$lastOauthFoundationCurrentReadCall = $initSource.LastIndexOf('Assert-IndependentBoardOauthFoundationCurrentState', [StringComparison]::Ordinal)
$lastOauthConsentIntentCurrentReadCall = $initSource.LastIndexOf('Assert-IndependentBoardOauthConsentIntentCurrentState', [StringComparison]::Ordinal)
$lastOauthRefreshSecurityCurrentReadCall = $initSource.LastIndexOf('Assert-IndependentBoardOauthRefreshSecurityCurrentState', [StringComparison]::Ordinal)
$manifestLoopIndex = $initSource.IndexOf('foreach ($step in $steps)', [StringComparison]::Ordinal)
if ($lastCurrentReadCall -le $manifestLoopIndex) {
    $errors.Add("initializer must run the Independent Board current-read audit after the complete manifest loop")
}
if ($lastMeMenuCurrentReadCall -le $manifestLoopIndex) {
    $errors.Add("initializer must run the Independent Board me menu current-read audit after the complete manifest loop")
}
if ($lastAdminMenuCurrentReadCall -le $manifestLoopIndex) {
    $errors.Add("initializer must run the Independent Board admin menu current-read audit after the complete manifest loop")
}
if ($lastLifecycleMenuCurrentReadCall -le $manifestLoopIndex) {
    $errors.Add("initializer must run the Independent Board entitlement lifecycle menu current-read audit after the complete manifest loop")
}
if ($lastConnectorBindingCurrentReadCall -le $manifestLoopIndex) {
    $errors.Add("initializer must run the Independent Board Connector binding current-read audit after the complete manifest loop")
}
if ($lastOauthFoundationCurrentReadCall -le $manifestLoopIndex) {
    $errors.Add("initializer must run the Independent Board OAuth foundation current-read audit after the complete manifest loop")
}
if ($lastOauthConsentIntentCurrentReadCall -le $manifestLoopIndex) {
    $errors.Add("initializer must run the Independent Board OAuth consent-intent current-read audit after the complete manifest loop")
}
if ($lastOauthRefreshSecurityCurrentReadCall -le $manifestLoopIndex) {
    $errors.Add("initializer must run the Independent Board OAuth refresh-security current-read audit after the complete manifest loop")
}
$requiredConnectorCurrentReadNeedles = @(
    '$expectedAppliedState = "APPLIED:$($step.Description)"',
    '$connectorLockOrderExpected = 0',
    '11 + $connectorLockOrderExpected',
    'idx_connector_binding_receipt_lock_order',
    "column_type = 'varchar(191)'",
    "CAST(visibility AS BINARY) = CAST('YES' AS BINARY) AND partial_columns = 0",
    "unique_constraint_schema = DATABASE()",
    "referenced_table_schema = DATABASE()",
    "tc.enforced = 'YES'",
    "CAST(normalized_clause AS BINARY) = CAST('(source_code=''workbuddy'')' AS BINARY)",
    "CAST(normalized_clause AS BINARY) = CAST('(evidence_level=''action_completed'')' AS BINARY)",
    "trg_connector_binding_receipt_no_update",
    "trg_connector_binding_receipt_no_delete",
    'Independent Board Connector binding exact current-read audit'
)
foreach ($needle in $requiredConnectorCurrentReadNeedles) {
    if (-not $initSource.Contains($needle)) {
        $errors.Add("initializer is missing exact Connector current-read contract: $needle")
    }
}
$safeBacktickNormalizerCount = @([regex]::Matches(
        $initSource,
        "(?:cc\.check_clause|action_statement),\s*CHAR\(96\),\s*''")).Count
if ($safeBacktickNormalizerCount -ne 4) {
    $errors.Add("initializer must use exactly four SQL CHAR(96) metadata normalizers; found $safeBacktickNormalizerCount")
}
$literalBacktickSqlToken = "'" + [char]96 + "'"
if ($initSource.Contains("cc.check_clause, $literalBacktickSqlToken, ''") -or
    $initSource.Contains("action_statement, $literalBacktickSqlToken, ''")) {
    $errors.Add('initializer must not embed a literal backtick in a double-quoted SQL here-string; PowerShell removes it before MySQL execution')
}
$requiredOauthCurrentReadNeedles = @(
    'Assert-IndependentBoardOauthServerProfile',
    'Assert-IndependentBoardOauthFoundationCurrentState',
    '$serverProfileResponse = Invoke-MySqlText',
    'MySQL Community Server - GPL',
    "'8.0.30' = '016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539'",
    "'8.4.8' = '3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f'",
    '$provenanceInternalVersion = ''20260721_independent_board_oauth_receipt_provenance_v1''',
    '$provenanceAppliedDescription = ''APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness''',
    '$consentIntentInternalVersion = ''20260721_independent_board_oauth_consent_intent_lineage_v1''',
    '$consentIntentAppliedDescription = ''APPLIED:Independent Board OAuth consent intent lineage''',
    '$expectedOauthColumnCount = if ($consentIntentApplied) { 148 } elseif ($provenanceApplied) { 145 } else { 144 }',
    '$expectedOauthGeneratedColumnCount = if ($provenanceApplied) { 3 } else { 2 }',
    '$expectedOauthIndexCount = if ($consentIntentApplied) { 57 } elseif ($provenanceApplied) { 53 } else { 52 }',
    '$expectedOauthForeignKeyCount = if ($consentIntentApplied) { 16 } else { 14 }',
    '$expectedOauthForeignKeyColumnCount = if ($consentIntentApplied) { 61 } else { 57 }',
    '$expectedOauthCheckCount = if ($consentIntentApplied) { 36 } else { 33 }',
    '$expectedOauthTriggerCount = if ($consentIntentApplied) { 3 } else { 2 }',
    '$expectedProvenanceObjectCount = if ($provenanceApplied) { 1 } else { 0 }',
    'uk_oauth_receipt_family_created_slot',
    "AND NOT (table_name = 'fbs_oauth_receipt' AND column_name = 'family_created_slot')",
    "AND NOT (table_name = 'fbs_oauth_receipt' AND index_name = 'uk_oauth_receipt_family_created_slot')",
    "AND NOT (table_name = 'fbs_oauth_authorization_request' AND column_name = 'consent_intent')",
    "AND NOT (table_name = 'fbs_oauth_authorization_code' AND index_name IN ('idx_oauth_code_request_consent','uk_oauth_code_id_consent'))",
    'Assert-IndependentBoardOauthConsentIntentCurrentState',
    'Assert-IndependentBoardOauthRefreshSecurityCurrentState',
    'uk_oauth_request_id_consent',
    'fk_oauth_code_request_consent',
    'fk_oauth_family_code_consent',
    'chk_oauth_request_consent_intent',
    'trg_oauth_request_consent_intent_once',
    '$expectedState = @(1, 1, 1, 1, 6, 148, 57, 16, 61, 36, 36, 3, 1, 1, 1, 4, 2, 3, 1, 0, 0, 0)',
    '$exactDigestParts = @($exactDigestResponse.Split(''|''))',
    '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0',
    'e222fafb27746a52bdc6e31c08a9b8cb3e34c2a8eb88bad9345d53b496d0c612',
    '0aca71671f1632851a45f57eeacfa18f23529d628ddaa159dc2ca7cb472a0d24',
    '$expectedColumnDigest = if ($consentIntentApplied) {',
    '$expectedCheckDigest = if ($consentIntentApplied) {',
    'ordinal_position = 28',
    '1a3122b7238b444939a7981ccd759322bd168f351131e4d9c0a16346242c3d79',
    '016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539',
    '3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f',
    '51f71711dfc17b5a6741fbbd1bd3d31ae7d4761e9da5722c614eb199a81afb11',
    'SEPARATOR 0x0A',
    'SHOW CREATE TABLE fbs_oauth_client;',
    '$neutralClientNameMetadataProjectionHex',
    '[regex]::Matches($showCreateHex, $neutralClientNameUtf8Hex).Count -ne 1',
    '[regex]::Matches($showCreateHex, $neutralClientNameMetadataProjectionHex).Count -ne 0',
    '[regex]::Matches($showCreateHex, $legacyClientNameHex).Count -ne 0',
    'fixed-profile SHOW CREATE bytes',
    "WHERE event_object_table = 'fbs_oauth_receipt'",
    '$serverProfile.ForeignKeyDigest',
    'exact current-read audit on MySQL Community $serverVersion',
    '033 foundation plus 034 provenance successor',
    '033 foundation plus 034 provenance and 035 consent-intent successors',
    'binary foundation-subset digests'
)
$requiredOauthInitializerIntegrationNeedles = @(
    'function Invoke-MySqlRawOutputBytes',
    "if (`$step.Version -eq 'public_init_033')",
    "if (`$step.Version -eq 'public_init_034')",
    "if (`$step.Version -eq 'public_init_035')",
    "if (`$step.Version -eq 'public_init_036')",
    '$declaredSha256 = [string]$declared.sha256',
    '$($executable.Version) byte contract drifted',
    '$null = Assert-IndependentBoardOauthServerProfile',
    'records RUNNING or executes the migration file',
    'W4b creates FKs into the W1 entitlement and W4a binding contracts.',
    'Assert-IndependentBoardControlPlaneCurrentState',
    'Assert-IndependentBoardConnectorBindingCurrentState',
    'W4b exact post-commit reconciliation did not pass',
    'u3w_migrate_independent_board_oauth_foundation_20260721',
    'u3w_finalize_independent_board_oauth_foundation_20260721',
    '$resumeRunningOauthProvenance',
    '$resumeRunningOauthConsentIntent',
    '$resumeRunningOauthRefreshSecurity',
    '$resumeRunningOauthAdditive',
    'one bounded replay',
    'u3w_migrate_independent_board_oauth_receipt_provenance_20260721',
    'W4b provenance exact bounded replay did not pass',
    'request -> code -> family -> trigger prefix',
    'u3w_migrate_ib_oauth_consent_intent_20260721',
    'u3w_finalize_ib_oauth_consent_intent_20260721',
    'u3w_assert_ib_oauth_consent_intent_20260721',
    'W4b consent-intent exact bounded replay did not pass',
    'internal S2',
    'u3w_migrate_ib_oauth_refresh_security_20260721',
    'u3w_assert_ib_oauth_refresh_security_20260721',
    'idx_oauth_token_family_lock_order',
    'idx_oauth_receipt_family_client_lock_order',
    'idx_connector_binding_receipt_lock_order',
    '504a017fe7c4a8ecc4c60619ea8beaf5e7d4aa207fcffe536ff47b8e1d9919b3',
    'W4b refresh-security exact bounded replay did not pass',
    'if ($verification -ne 24)'
)
$requiredCreditLedgerInitializerNeedles = @(
    'New-Step "public_init_038" "Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger" (Resolve-SqlFile "update_20260722_independent_board_credit_ledger.sql")',
    "@('public_init_035', 'public_init_036', 'public_init_037', 'public_init_038')",
    'function Assert-IndependentBoardCreditLedgerCurrentState',
    '$serverProfile = Assert-IndependentBoardOauthServerProfile',
    '$expected = @(3,3,45,45,45,20,20,4,4,9,9,22,22,6,6,1,1,5,0)',
    'CreditLedgerCurrentReadOnly',
    'five-column sys_user dependency, zero projection mismatch',
    'PASS Independent Board credit ledger exact raw current-read on MySQL',
    '$resumeRunningCreditLedger',
    "`$step.Version -eq 'public_init_038'",
    'Assert-IndependentBoardCreditLedgerCurrentState',
    'three-table ledger plus its internal receipt',
    'u3w_migrate_independent_board_credit_ledger_20260722',
    'u3w_finalize_independent_board_credit_ledger_20260722',
    'u3w_assert_independent_board_credit_triggers_20260722',
    'credit-ledger exact bounded replay did not pass',
    "'fbs_credit_account','fbs_credit_operation','fbs_credit_entry'",
    'if ($verification -ne 24)',
    'expected twenty-four representative current tables'
)
$requiredManifestCurrentReadNeedles = @(
    'function Assert-PublicDatabaseManifestCurrentState',
    'expected exactly $($steps.Count) receipts',
    'APPLIED:$($step.Description)',
    '[StringComparison]::Ordinal',
    'PASS public database manifest exact current-read',
    'Assert-PublicDatabaseManifestCurrentState',
    '$currentReadLockSession = New-MySqlSession',
    'Could not acquire the public database manifest lock for current-read.',
    'Public database manifest current-read lock owner verification failed',
    'RELEASE_LOCK(@u3w_manifest_lock_name)',
    'Stop-MySqlSession -Session $currentReadLockSession'
)
$oauthCurrentReadStart = $initSource.IndexOf(
    'function Assert-IndependentBoardOauthServerProfile',
    [StringComparison]::Ordinal)
$oauthCurrentReadEnd = if ($oauthCurrentReadStart -ge 0) {
    $initSource.IndexOf('function New-MySqlSession', $oauthCurrentReadStart, [StringComparison]::Ordinal)
} else { -1 }
$oauthCurrentReadSource = if ($oauthCurrentReadStart -ge 0 -and $oauthCurrentReadEnd -gt $oauthCurrentReadStart) {
    $initSource.Substring($oauthCurrentReadStart, $oauthCurrentReadEnd - $oauthCurrentReadStart)
} else { '' }
if ([string]::IsNullOrEmpty($oauthCurrentReadSource)) {
    $errors.Add('initializer OAuth current-read function boundary is missing')
}
elseif ($oauthCurrentReadSource -match '(?im)^\s*(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM|ALTER\s+TABLE|CREATE\s+(?:TABLE|TRIGGER|PROCEDURE)|DROP\s+(?:TABLE|TRIGGER|PROCEDURE)|TRUNCATE\s+TABLE)\b') {
    $errors.Add('initializer OAuth current-read functions must remain database read-only')
}
foreach ($needle in $requiredOauthCurrentReadNeedles) {
    if (-not $oauthCurrentReadSource.Contains($needle)) {
        $errors.Add("initializer is missing exact OAuth foundation current-read or reconciliation contract: $needle")
    }
}
if ($oauthCurrentReadSource -match 'ordinal_position\s*=\s*29') {
    $errors.Add('initializer OAuth current-read must require family consent ordinal 28, never 29')
}
if ($oauthCurrentReadSource -match 'consent successor baseline pending' -or
    $oauthCurrentReadSource -match '\$consentIntentApplied\s+-and\s+\$serverVersion') {
    $errors.Add('initializer OAuth current-read must apply the measured consent-successor digests to both locked MySQL profiles')
}
foreach ($needle in $requiredOauthInitializerIntegrationNeedles) {
    if (-not $initSource.Contains($needle)) {
        $errors.Add("initializer is missing W4b manifest or reconciliation contract: $needle")
    }
}
foreach ($needle in $requiredCreditLedgerInitializerNeedles) {
    if (-not $initSource.Contains($needle)) {
        $errors.Add("initializer is missing public_init_038 credit-ledger manifest, recovery or verification contract: $needle")
    }
}
foreach ($needle in $requiredManifestCurrentReadNeedles) {
    if (-not $initSource.Contains($needle)) {
        $errors.Add("initializer is missing exact public manifest current-read contract: $needle")
    }
}
$creditLedgerCurrentReadStart = $initSource.IndexOf(
    'function Assert-IndependentBoardCreditLedgerCurrentState',
    [StringComparison]::Ordinal)
$creditLedgerCurrentReadEnd = if ($creditLedgerCurrentReadStart -ge 0) {
    $initSource.IndexOf('if ($CurrentReadOnly -or $CreditLedgerCurrentReadOnly) {', $creditLedgerCurrentReadStart, [StringComparison]::Ordinal)
} else { -1 }
$creditLedgerCurrentReadSource = if ($creditLedgerCurrentReadStart -ge 0 -and $creditLedgerCurrentReadEnd -gt $creditLedgerCurrentReadStart) {
    $initSource.Substring($creditLedgerCurrentReadStart, $creditLedgerCurrentReadEnd - $creditLedgerCurrentReadStart)
} else { '' }
if ([string]::IsNullOrEmpty($creditLedgerCurrentReadSource)) {
    $errors.Add('initializer credit-ledger current-read function boundary is missing')
}
elseif ($creditLedgerCurrentReadSource -match 'Invoke-MySql(?:File|Bytes)' -or
        $creditLedgerCurrentReadSource -match '(?im)^\s*(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM|ALTER\s+TABLE|CREATE\s+(?:TABLE|TRIGGER|PROCEDURE)|DROP\s+(?:TABLE|TRIGGER|PROCEDURE)|TRUNCATE\s+TABLE)\b') {
    $errors.Add('initializer credit-ledger current-read function must remain database read-only')
}
foreach ($needle in @(
    'information_schema.tables',
    'information_schema.columns',
    'information_schema.statistics',
    'information_schema.referential_constraints',
    'information_schema.key_column_usage',
    'information_schema.table_constraints',
    'information_schema.triggers',
    '$serverProfile = Assert-IndependentBoardOauthServerProfile',
    "table_name='sys_user'",
    "column_name='status'",
    "column_name='del_flag'",
    "column_name='update_time'",
    "extra NOT LIKE '%GENERATED%'",
    'LEFT JOIN sys_user u ON u.user_id=a.user_id',
    'u.user_id IS NULL OR a.balance<>COALESCE(u.points,0)',
    'fe61351bc245be129eedf83daa790444022925b660ed3f2c0241f9ec15917fc4',
    '29f2ebca36c354a73509468e251b4edb61f096056d3ded28d21d16b76e7b34dc',
    '3826d266d39d91f3faada93659bff5bf347c5f4a3c05a73ad96d70a88d7d2e92',
    'f1e99b6123b4ef1ce51b1153d506a2d983877c7b59257febb7873feb6fc764da',
    "version='20260722_independent_board_credit_ledger_v1'",
    "description='Independent Board USER_GLOBAL FBS_POINTS immutable shadow ledger'",
    '$expected = @(3,3,45,45,45,20,20,4,4,9,9,22,22,6,6,1,1,5,0)'
)) {
    if (-not $creditLedgerCurrentReadSource.Contains($needle)) {
        $errors.Add("initializer credit-ledger current-read is missing exact metadata contract: $needle")
    }
}
$currentReadOnlyBlockStart = $initSource.IndexOf('if ($CurrentReadOnly -or $CreditLedgerCurrentReadOnly) {', [StringComparison]::Ordinal)
$currentReadOnlyBlockEnd = if ($currentReadOnlyBlockStart -ge 0) {
    $initSource.IndexOf('$databaseBootstrap =', $currentReadOnlyBlockStart, [StringComparison]::Ordinal)
} else { -1 }
$currentReadOnlyBlock = if ($currentReadOnlyBlockStart -ge 0 -and $currentReadOnlyBlockEnd -gt $currentReadOnlyBlockStart) {
    $initSource.Substring($currentReadOnlyBlockStart, $currentReadOnlyBlockEnd - $currentReadOnlyBlockStart)
} else { '' }
foreach ($needle in @(
    'if ($CreditLedgerCurrentReadOnly)',
    'Assert-IndependentBoardOauthFoundationCurrentState',
    'Assert-IndependentBoardOauthConsentIntentCurrentState',
    'Assert-IndependentBoardCreditLedgerCurrentState',
    'Assert-PublicDatabaseManifestCurrentState',
    'No database write was requested.',
    'return'
)) {
    if (-not $currentReadOnlyBlock.Contains($needle)) {
        $errors.Add("initializer CurrentReadOnly block is missing required read-only successor contract: $needle")
    }
}
if ($currentReadOnlyBlock -match 'Invoke-MySql(?:File|Bytes)' -or
    $currentReadOnlyBlock -match '(?im)^\s*(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM|ALTER\s+TABLE|CREATE\s+TABLE|DROP\s+TABLE|TRUNCATE\s+TABLE)\b') {
    $errors.Add('initializer CurrentReadOnly block must not execute migration files or persistent database writes')
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

$menuMigrationItPath = Join-Path $resolvedRoot 'scripts\run-independent-board-menu-migration-it.ps1'
if (-not (Test-Path -LiteralPath $menuMigrationItPath -PathType Leaf)) {
    $errors.Add("Independent Board menu migration MySQL replay gate is missing")
}
else {
    $menuMigrationItSource = Get-Content -LiteralPath $menuMigrationItPath -Raw -Encoding UTF8
    if ($menuMigrationItSource -match '[^\x00-\x7F]') {
        $errors.Add('menu migration runner must remain ASCII-only for Windows PowerShell 5 compatibility')
    }
    $requiredMenuMigrationItNeedles = @(
        '[switch]$AllowDestructiveTest',
        "throw 'Explicit -AllowDestructiveTest consent is required.'",
        "Port 3306 is forbidden",
        "bind-address=127.0.0.1",
        "productionConnectionUsed = `$false",
        "workDirectoryCleaned = `$true",
        "w2_first_apply",
        "w3a_first_apply",
        "w3b_first_apply",
        "w3b_completed_rerun_preserves_external_binding",
        "w3a_rerun_after_w3b",
        "w3b_completed_state_drift_fail_closed",
        "w3b_prewrite_identity_collision_fail_closed",
        "prewrite-collision",
        "schemaNameLength",
        "Assert-SafeCleanupPath",
        "Get-VerifiedMySqlProcessId"
    )
    foreach ($needle in $requiredMenuMigrationItNeedles) {
        if (-not $menuMigrationItSource.Contains($needle)) {
            $errors.Add("menu migration MySQL replay gate is missing required safety or scenario contract: $needle")
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
    manualMigrationCount = [int]$manifest.manualMigrationCount
    managedUpdateSqlCount = $managedFiles.Count
    dryRunDatabaseIsolation = [bool]($manifest.dryRun -and -not $manifest.databaseConnectionOpened)
    truthSpineVersion = "public_init_027"
    controlPlaneVersion = "public_init_028"
    meMenuVersion = "public_init_029"
    adminMenuVersion = "public_init_030"
    entitlementLifecycleMenuVersion = "public_init_031"
    connectorBindingVersion = "public_init_032"
    oauthFoundationVersion = "public_init_033"
    oauthProvenanceVersion = "public_init_034"
    oauthConsentIntentVersion = "public_init_035"
    oauthRefreshSecurityVersion = "public_init_036"
    creditLedgerVersion = "public_init_038"
    oauthFoundationSha256 = $oauthFoundationSha256
    oauthProvenanceSha256 = $oauthProvenanceSha256
    oauthConsentIntentSha256 = $oauthConsentIntentSha256
    oauthRefreshSecuritySha256 = $oauthRefreshSecuritySha256
    creditLedgerSha256 = $creditLedgerSha256
    candidateMenuMigrationId = $candidateMenuMigrationId
    candidateMenuMigrationSha256 = $candidateMenuMigrationSha256
    oauthSuccessorCheckDigest = $oauthSuccessorCheckDigest
    oauthGenerationExpressionWhitelist = @('_utf8mb4', '_ascii', 'no-prefix')
    menuMigrationReplayGate = "scripts/run-independent-board-menu-migration-it.ps1"
    errors = @($errors)
}

if ($Json) {
    $result | ConvertTo-Json -Depth 5
}
else {
    if ($result.ok) {
        Write-Host "PASS database manifest: $($result.manifestStepCount) public steps plus $($result.manualMigrationCount) manual migrations; $($result.managedUpdateSqlCount) managed update SQL files covered exactly once."
    }
    else {
        $result.errors | ForEach-Object { Write-Error $_ }
    }
}

if (-not $result.ok) {
    exit 1
}
