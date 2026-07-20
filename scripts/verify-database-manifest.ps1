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
$managedFiles = @(Get-ChildItem -LiteralPath $sqlRoot -Filter "update_*.sql" -File | Sort-Object Name)
$manifestUpdateSteps = @($manifest.steps | Where-Object { $_.file -like "update_*.sql" })
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
}
if ([string]$manifest.manifestFile -ne 'sql/init-manifest.json' -or
    [string]$manifest.manifestSchema -ne 'fbsir.public-database-init-manifest/v1') {
    $errors.Add("initializer DryRun did not bind the declarative manifest identity")
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
    public_init_030 = "update_20260720_independent_board_admin_menu.sql"
    public_init_031 = "update_20260720_independent_board_entitlement_lifecycle_menu.sql"
    public_init_032 = "update_20260721_independent_board_connector_binding.sql"
    public_init_033 = "update_20260721_independent_board_oauth_foundation.sql"
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
    "Assert-IndependentBoardAdminMenuCurrentState",
    "Assert-IndependentBoardEntitlementLifecycleMenuCurrentState",
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
if (-not (Test-Path -LiteralPath $oauthFoundationSqlPath -PathType Leaf)) {
    $errors.Add("Independent Board OAuth foundation migration is missing")
    $oauthFoundationSql = ''
}
else {
    $oauthFoundationSql = Get-Content -LiteralPath $oauthFoundationSqlPath -Raw -Encoding UTF8
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
$requiredConnectorCurrentReadNeedles = @(
    '$expectedAppliedState = "APPLIED:$($step.Description)"',
    '$expectedExactState = @(3, 36, 7, 11, 3, 8, 15, 15, 2, 2)',
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
$requiredOauthCurrentReadNeedles = @(
    'Assert-IndependentBoardOauthServerProfile',
    'Assert-IndependentBoardOauthFoundationCurrentState',
    '$serverProfileResponse = Invoke-MySqlText',
    'MySQL Community Server - GPL',
    "'8.0.30' = '016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539'",
    "'8.4.8' = '3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f'",
    '$expectedState = @(6, 6, 6, 6, 144, 6, 17, 2, 52, 14, 57, 33, 33, 1, 2, 2, 1, 1)',
    '$exactDigestParts = @($exactDigestResponse.Split(''|''))',
    '89883a39d5aca493a15777ed12fee5a2c478627d7290e02c22ec0d3ffeaf0cf0',
    '1a3122b7238b444939a7981ccd759322bd168f351131e4d9c0a16346242c3d79',
    '016bed0a311d4dbd85f1d3c63e1bf46e82b9ce21527ad304b4139482f274f539',
    '3bc201df5f29e65e40024a6cf95dd2b5d46be928bef422f6934ab7c06a94368f',
    '51f71711dfc17b5a6741fbbd1bd3d31ae7d4761e9da5722c614eb199a81afb11',
    'SEPARATOR 0x0A',
    'LOCATE(CONVERT(0xe69caae9aa8ce8af81e79a84e69cace59cb0e585ace585b1e5aea2e688b7e7abaf USING utf8mb4), cc.check_clause) > 0',
    "LOCATE('WorkBuddy - ', cc.check_clause) = 0",
    "WHERE event_object_table = 'fbs_oauth_receipt'",
    '$serverProfile.ForeignKeyDigest',
    'exact current-read audit on MySQL Community $serverVersion',
    'Independent Board OAuth foundation exact current-read audit'
)
$requiredOauthInitializerIntegrationNeedles = @(
    "if (`$step.Version -eq 'public_init_033')",
    '$null = Assert-IndependentBoardOauthServerProfile',
    'records RUNNING or executes the migration file',
    'W4b creates FKs into the W1 entitlement and W4a binding contracts.',
    'Assert-IndependentBoardControlPlaneCurrentState',
    'Assert-IndependentBoardConnectorBindingCurrentState',
    'W4b exact post-commit reconciliation did not pass',
    'u3w_migrate_independent_board_oauth_foundation_20260721',
    'u3w_finalize_independent_board_oauth_foundation_20260721',
    'if ($verification -ne 21)'
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
foreach ($needle in $requiredOauthCurrentReadNeedles) {
    if (-not $oauthCurrentReadSource.Contains($needle)) {
        $errors.Add("initializer is missing exact OAuth foundation current-read or reconciliation contract: $needle")
    }
}
foreach ($needle in $requiredOauthInitializerIntegrationNeedles) {
    if (-not $initSource.Contains($needle)) {
        $errors.Add("initializer is missing W4b manifest or reconciliation contract: $needle")
    }
}
foreach ($needle in $requiredManifestCurrentReadNeedles) {
    if (-not $initSource.Contains($needle)) {
        $errors.Add("initializer is missing exact public manifest current-read contract: $needle")
    }
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
    managedUpdateSqlCount = $managedFiles.Count
    dryRunDatabaseIsolation = [bool]($manifest.dryRun -and -not $manifest.databaseConnectionOpened)
    truthSpineVersion = "public_init_027"
    controlPlaneVersion = "public_init_028"
    meMenuVersion = "public_init_029"
    adminMenuVersion = "public_init_030"
    entitlementLifecycleMenuVersion = "public_init_031"
    connectorBindingVersion = "public_init_032"
    oauthFoundationVersion = "public_init_033"
    menuMigrationReplayGate = "scripts/run-independent-board-menu-migration-it.ps1"
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
