param(
    [ValidateSet('Contract', 'Backend', 'Frontend', 'All')]
    [string]$Mode = 'All'
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Assert-PathExists {
    param([string]$RelativePath)
    $Path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required path is missing: $RelativePath"
    }
}

function Read-Utf8Json {
    param([string]$RelativePath)
    return Get-Content -LiteralPath (Join-Path $RepoRoot $RelativePath) -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Assert-ProductBrand {
    param(
        [object]$Product,
        [string]$Source
    )
    # Keep this script ASCII-safe for Windows PowerShell 5.1.
    $ExpectedBrandZh = -join @([char]0x798F, [char]0x5E2E, [char]0x624B)
    $ExpectedProductName = -join @([char]0x72EC, [char]0x8463, [char]0x4F1A)
    if ($null -eq $Product `
            -or $Product.brandZh -cne $ExpectedBrandZh `
            -or $Product.brandEn -cne 'FBSir' `
            -or $Product.name -cne $ExpectedProductName) {
        throw "Product brand contract mismatch: $Source"
    }
}

function Invoke-ContractChecks {
    $required = @(
        '.fbs-engineering\contract.json',
        'config\deployment\u3w-domain-inventory.json',
        'docs\independent-board\implementation-status.json',
        'docs\independent-board\taskboard.json',
        'docs\independent-board\AUTHORITATIVE-ROOT.md',
        'docs\independent-board\PORTAL-PROTOTYPE-SPEC.md',
        'docs\independent-board\W3B-ENTITLEMENT-LIFECYCLE-CONTRACT.md',
        'docs\independent-board\W4A-AUTHORITATIVE-CONNECTOR-BINDING-CONTRACT.md',
        'docs\independent-board\W4B-OAUTH-MCP-AUTHORIZATION-CONTRACT.md',
        'docs\independent-board\W4B-DATABASE-SUPPORT-MATRIX.md',
        'docs\independent-board\W4B-INPUT-EVIDENCE.json',
        'docs\independent-board\prototypes\portals\index.html',
        'sql\update_20260720_independent_board_control_plane.sql',
        'sql\update_20260720_independent_board_me_menu.sql',
        'sql\update_20260720_independent_board_admin_menu.sql',
        'sql\update_20260720_independent_board_entitlement_lifecycle_menu.sql',
        'sql\update_20260721_independent_board_connector_binding.sql',
        'sql\update_20260721_independent_board_oauth_foundation.sql',
        'scripts\verify-database-manifest.ps1',
        'scripts\verify-independent-board-live-database.ps1',
        'scripts\run-independent-board-menu-migration-it.ps1',
        'scripts\verify-independent-board-mysql-concurrency.ps1',
        'scripts\run-independent-board-mysql-transaction-it.ps1',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\service\IndependentBoardMeetingTransactionService.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\service\IndependentBoardDashboardService.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\dto\BoardEntitlementRevokeRequest.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\dto\BoardEntitlementReceiptView.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\dto\BoardEntitlementReceiptAuditEnvelope.java',
        'FBSir-business\src\test\java\com\wx\fbsir\business\board\integration\IndependentBoardMysqlTransactionIT.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthClientRegistrationService.java',
        'FBSir-business\src\main\resources\mapper\board\IndependentBoardOAuthMapper.xml',
        'FBSir-business\src\test\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthClientRegistrationServiceTest.java',
        'FBSir-admin\src\test\java\com\wx\fbsir\business\board\IndependentBoardHttpSecurityIntegrationTest.java',
        'FBSir-ui\src\api\business\independentBoard\index.js',
        'FBSir-ui\src\views\business\independentBoard\me\index.vue',
        'FBSir-ui\src\views\business\independentBoard\me\reservationSafety.js',
        'FBSir-ui\scripts\verify-independent-board-ui.mjs',
        'FBSir-ui\src\api\business\independentBoard\admin.js',
        'FBSir-ui\src\views\business\independentBoard\admin\model.js',
        'FBSir-ui\src\views\business\independentBoard\admin\entitlement\index.vue',
        'FBSir-ui\src\views\business\independentBoard\admin\entitlementReceipt\index.vue',
        'FBSir-ui\src\views\business\independentBoard\admin\meetingAudit\index.vue',
        'FBSir-ui\scripts\verify-independent-board-admin-ui.mjs',
        'FBSir-ui\src\utils\portalEntry.js',
        'FBSir-ui\scripts\verify-portal-entry.mjs',
        'reports\independent-board\w1-application-integration-verification-20260720.json',
        'reports\independent-board\w2-user-portal-verification-20260720.json',
        'reports\independent-board\w3-admin-portal-verification-20260720.json',
        'reports\independent-board\w3b-entitlement-lifecycle-verification-20260720.json',
        'reports\independent-board\w4a-authoritative-connector-binding-verification-20260721.json'
    )
    foreach ($path in $required) {
        Assert-PathExists -RelativePath $path
        if ([System.IO.Path]::GetExtension($path) -eq '.json') {
            $null = Read-Utf8Json -RelativePath $path
        }
    }

    Assert-ProductBrand -Product (Read-Utf8Json -RelativePath 'docs\independent-board\implementation-status.json').product `
        -Source 'implementation status'
    Assert-ProductBrand -Product (Read-Utf8Json -RelativePath 'reports\independent-board\w4a-authoritative-connector-binding-verification-20260721.json').product `
        -Source 'W4a verification report'
    $w4bEvidence = Read-Utf8Json -RelativePath 'docs\independent-board\W4B-INPUT-EVIDENCE.json'
    Assert-ProductBrand -Product $w4bEvidence.product -Source 'W4b input evidence'
    if ($w4bEvidence.runtimeDependency -ne $false -or $w4bEvidence.buildDependency -ne $false) {
        throw 'W4b external evidence must not become a runtime or build dependency'
    }
    if (@($w4bEvidence.sources).Count -ne 4) {
        throw 'W4b input evidence must contain the four fixed provenance records'
    }
    $expectedW4bHashes = @(
        'f7c0adf634f373f125352181b6a5a20e20c5c256751ea5ed72fc3e9b6c3fb1dc',
        '1a025b8adeda795f0c87c1169497f431b7a2fc9f2e7405e968050c20d2ae6cf3',
        'ff844050977bf99d4bffba572b1daf86a0feda74bfb44a9233dea8b853b12e76',
        'ebbac0da1c7a4d5427ef76953fba8946c1dafe354617a5b0a48c4538cfa3cdb6'
    )
    $actualW4bHashes = @($w4bEvidence.sources | ForEach-Object { $_.sha256 })
    if ((Compare-Object -ReferenceObject $expectedW4bHashes -DifferenceObject $actualW4bHashes).Count -ne 0) {
        throw 'W4b input evidence hash set drifted'
    }

    $w4bContract = Get-Content -LiteralPath (Join-Path $RepoRoot 'docs\independent-board\W4B-OAUTH-MCP-AUTHORIZATION-CONTRACT.md') -Raw -Encoding UTF8
    foreach ($marker in @(
        'contract_locked_with_w4b_1_foundation_verified_local',
        'https://api2.u3w.com/fbs-mcp/mcp',
        'https://api2.u3w.com/.well-known/oauth-protected-resource/fbs-mcp/mcp',
        'code_challenge_method=S256',
        'WorkBuddy',
        'PENDING_BINDING',
        'public_init_033',
        'N45')) {
        if (-not $w4bContract.Contains($marker)) {
            throw "W4b authorization contract is missing required marker: $marker"
        }
    }

    $ExpectedBrandZh = -join @([char]0x798F, [char]0x5E2E, [char]0x624B)
    $ExpectedProductName = -join @([char]0x72EC, [char]0x8463, [char]0x4F1A)
    $LegacyProductName = -join @(
        [char]0x8D85, [char]0x7EA7, [char]0x5408, [char]0x4F19, [char]0x4EBA,
        [char]0x72EC, [char]0x8463, [char]0x4F1A)
    foreach ($surface in @(
        'docs\independent-board\prototypes\portals\index.html',
        'FBSir-ui\src\views\business\independentBoard\me\index.vue',
        'FBSir-ui\src\views\business\independentBoard\admin\entitlement\index.vue',
        'FBSir-ui\src\views\business\independentBoard\admin\entitlementReceipt\index.vue',
        'FBSir-ui\src\views\business\independentBoard\admin\meetingAudit\index.vue')) {
        $content = Get-Content -LiteralPath (Join-Path $RepoRoot $surface) -Raw -Encoding UTF8
        foreach ($marker in @($ExpectedBrandZh, 'FBSir', $ExpectedProductName)) {
            if (-not $content.Contains($marker)) {
                throw "Product brand marker is missing from ${surface}: $marker"
            }
        }
        if ($content.Contains($LegacyProductName)) {
            throw "Legacy product name is forbidden on product surface: $surface"
        }
    }

    $prototype = Get-Content -LiteralPath (Join-Path $RepoRoot 'docs\independent-board\prototypes\portals\index.html') -Raw -Encoding UTF8
    # Keep PowerShell source ASCII-safe for Windows PowerShell 5.1. The UTF-8
    # prototype itself is checked through stable ASCII structural markers.
    foreach ($marker in @('FBSir', 'U3W-AI', 'lang="zh-CN"', 'data-mode="me"', 'data-mode="admin"', 'prototype-banner')) {
        if (-not $prototype.Contains($marker)) {
            throw "Portal prototype is missing required marker: $marker"
        }
    }

    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot 'scripts\verify-database-manifest.ps1')
    if ($LASTEXITCODE -ne 0) { throw "Database manifest verification failed with exit code $LASTEXITCODE" }
}

function Invoke-BackendChecks {
    Push-Location $RepoRoot
    try {
        & mvn.cmd -q -pl FBSir-admin -am test
        if ($LASTEXITCODE -ne 0) { throw "Backend verification failed with exit code $LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }
}

function Invoke-FrontendChecks {
    $UiRoot = Join-Path $RepoRoot 'FBSir-ui'
    Push-Location $UiRoot
    try {
        if (-not (Test-Path -LiteralPath (Join-Path $UiRoot 'node_modules'))) {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE" }
        }
        & npm.cmd run verify:portal-entry
        if ($LASTEXITCODE -ne 0) { throw "Portal entry verification failed with exit code $LASTEXITCODE" }
        & npm.cmd run verify:independent-board-ui
        if ($LASTEXITCODE -ne 0) { throw "Independent Board UI verification failed with exit code $LASTEXITCODE" }
        & npm.cmd run verify:independent-board-admin-ui
        if ($LASTEXITCODE -ne 0) { throw "Independent Board admin UI verification failed with exit code $LASTEXITCODE" }
        & npm.cmd run verify:menu-components
        if ($LASTEXITCODE -ne 0) { throw "Menu verification failed with exit code $LASTEXITCODE" }
        & npm.cmd run build:prod
        if ($LASTEXITCODE -ne 0) { throw "Frontend build failed with exit code $LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }
}

Invoke-ContractChecks

if ($Mode -in @('Backend', 'All')) {
    Invoke-BackendChecks
}

if ($Mode -in @('Frontend', 'All')) {
    Invoke-FrontendChecks
}

[pscustomobject]@{
    schema = 'fbsir.independent-board-verification/v1'
    ok = $true
    mode = $Mode
    repoRoot = $RepoRoot
    observedAt = [DateTimeOffset]::Now.ToString('o')
    databaseEvidence = 'manifest_and_dry_run_static_only_in_this_command'
    liveDatabaseVerified = $false
    boardHttpSecurityIncluded = ($Mode -in @('Backend', 'All'))
    databaseTransactionsVerified = $false
    releaseReady = $false
    releaseBlocker = 'DATABASE_TRANSACTIONS_AND_MENU_MIGRATIONS_ARE_SEPARATE_REQUIRED_COMMANDS'
    requiredReleaseCompanionCommands = @('database-transactions', 'database-menu-migrations')
    scratchEvidenceCommands = @('database-live', 'database-concurrency')
    menuMigrationsVerified = $false
} | ConvertTo-Json -Depth 4 -Compress
