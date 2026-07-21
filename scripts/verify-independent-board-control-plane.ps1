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

function Invoke-FrontendNpm {
    param([string[]]$Arguments)

    $NodeOverride = $env:U3W_NODE_EXE
    $NpmCliOverride = $env:U3W_NPM_CLI
    $HasNodeOverride = -not [string]::IsNullOrWhiteSpace($NodeOverride)
    $HasNpmCliOverride = -not [string]::IsNullOrWhiteSpace($NpmCliOverride)
    if ($HasNodeOverride -xor $HasNpmCliOverride) {
        throw 'U3W_NODE_EXE and U3W_NPM_CLI must be provided together'
    }

    if ($HasNodeOverride) {
        if (-not (Test-Path -LiteralPath $NodeOverride -PathType Leaf)) {
            throw "U3W_NODE_EXE does not exist: $NodeOverride"
        }
        if (-not (Test-Path -LiteralPath $NpmCliOverride -PathType Leaf)) {
            throw "U3W_NPM_CLI does not exist: $NpmCliOverride"
        }
        $NodeDirectory = Split-Path -Parent $NodeOverride
        $env:PATH = $NodeDirectory + [System.IO.Path]::PathSeparator + $env:PATH
        & $NodeOverride $NpmCliOverride @Arguments
    }
    else {
        $NpmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
        if ($null -eq $NpmCommand) {
            $NpmCommand = Get-Command npm -ErrorAction SilentlyContinue
        }
        if ($null -eq $NpmCommand) {
            throw 'npm is unavailable; set U3W_NODE_EXE and U3W_NPM_CLI to verified portable toolchain paths'
        }
        & $NpmCommand.Source @Arguments
    }

    if ($LASTEXITCODE -ne 0) {
        throw "npm command failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
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
        'docs\decisions\ADR-001-independent-board-default-off-portal-read-boundary.md',
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
        'FBSir-admin\src\main\resources\application.yml',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\service\IndependentBoardMeetingTransactionService.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\service\IndependentBoardDashboardService.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\dto\BoardEntitlementRevokeRequest.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\dto\BoardEntitlementReceiptView.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\dto\BoardEntitlementReceiptAuditEnvelope.java',
        'FBSir-business\src\test\java\com\wx\fbsir\business\board\integration\IndependentBoardMysqlTransactionIT.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthClientRegistrationService.java',
        'FBSir-business\src\main\resources\mapper\board\IndependentBoardOAuthMapper.xml',
        'FBSir-business\src\test\java\com\wx\fbsir\business\board\oauth\service\IndependentBoardOAuthClientRegistrationServiceTest.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\IndependentBoardPortalReadService.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\config\BoardPortalReadConfiguration.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\controller\BoardPortalCandidateBoundaryFilter.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\controller\IndependentBoardPortalAdminReadController.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\controller\IndependentBoardPortalMeReadController.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\controller\IndependentBoardPortalReadExceptionHandler.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\mapper\IndependentBoardPortalReadMapper.java',
        'FBSir-business\src\main\resources\mapper\board\IndependentBoardPortalReadMapper.xml',
        'FBSir-business\src\test\java\com\wx\fbsir\business\board\portal\IndependentBoardPortalReadMapperContractTest.java',
        'FBSir-business\src\test\java\com\wx\fbsir\business\board\portal\IndependentBoardPortalReadServiceTest.java',
        'FBSir-admin\src\test\java\com\wx\fbsir\business\board\IndependentBoardHttpSecurityIntegrationTest.java',
        'FBSir-admin\src\test\java\com\wx\fbsir\business\board\IndependentBoardPortalReadHttpSecurityIntegrationTest.java',
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
        'FBSir-ui\src\api\business\independentBoard\portalCandidate.js',
        'FBSir-ui\src\views\business\independentBoard\portalCandidateModel.js',
        'FBSir-ui\src\views\business\independentBoard\me\connector\index.vue',
        'FBSir-ui\src\views\business\independentBoard\me\security\index.vue',
        'FBSir-ui\src\views\business\independentBoard\admin\components\CandidateReadTable.vue',
        'FBSir-ui\src\views\business\independentBoard\admin\oauth-client\index.vue',
        'FBSir-ui\src\views\business\independentBoard\admin\oauth-family\index.vue',
        'FBSir-ui\src\views\business\independentBoard\admin\connector-binding\index.vue',
        'FBSir-ui\src\views\business\independentBoard\admin\security-event\index.vue',
        'FBSir-ui\scripts\verify-independent-board-w4b2-candidate.mjs',
        'reports\independent-board\w1-application-integration-verification-20260720.json',
        'reports\independent-board\w2-user-portal-verification-20260720.json',
        'reports\independent-board\w3-admin-portal-verification-20260720.json',
        'reports\independent-board\w3b-entitlement-lifecycle-verification-20260720.json',
        'reports\independent-board\w4a-authoritative-connector-binding-verification-20260721.json',
        'reports\independent-board\w4b-oauth-foundation-verification-20260721.json',
        'reports\independent-board\w4b2-backend-read-projection-verification-20260721.json',
        'work\diagnostics\independent-board-mysql-transaction-it\mysql-8.0.30\summary-mysql-8.0.30-utc-20260721T123307.170Z-local-20260721T203307.170+0800-pid-18500.json',
        'work\diagnostics\independent-board-mysql-transaction-it\mysql-8.4.8\summary-mysql-8.4.8-utc-20260721T123903.781Z-local-20260721T203903.781+0800-pid-13908.json'
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
    $w4bReport = Read-Utf8Json -RelativePath 'reports\independent-board\w4b-oauth-foundation-verification-20260721.json'
    Assert-ProductBrand -Product $w4bReport.product -Source 'W4b foundation verification report'
    $expectedW4bMigrationHashes = @(
        'b103ac5936ab1cb4bce865f04256f41826d90693556bc5627c09fc4ffee5de27',
        '2ba6fce7c3161b1647397485c37681974202b3a566ab370cc05587b1cfde7af3',
        '55dc772d54a4a457f00711a45266b2d0042522d63ed0d5a8e408d35a8ecaf560'
    )
    $actualW4bMigrationHashes = @($w4bReport.migrations | ForEach-Object { $_.sha256 })
    if ($w4bReport.schema -cne 'fbsir.independent-board.w4b1-verification/v2' `
            -or $w4bReport.result -cne 'VERIFIED_LOCAL_INTERNAL_OAUTH_CHAIN' `
            -or $w4bReport.repository.verificationBaseCommit -cne 'a1298b3194f62dc1708ac684f2fe7337970667c9' `
            -or (Compare-Object -ReferenceObject $expectedW4bMigrationHashes -DifferenceObject $actualW4bMigrationHashes).Count -ne 0 `
            -or @($w4bReport.verification.dualMysql).Count -ne 2 `
            -or @($w4bReport.verification.dualMysql | Where-Object { $_.tests -eq 55 -and $_.failures -eq 0 -and $_.errors -eq 0 -and $_.skipped -eq 0 -and $_.canonicalPhasesPassed -eq 3 -and $_.publicManifestReceipts -eq 35 -and $_.cleanupGatesPassed -eq 5 }).Count -ne 2 `
            -or $w4bReport.verification.targetedServiceMatrix.tests -ne 133 `
            -or $w4bReport.verification.fullRepositoryAll.tests -ne 796 `
            -or $w4bReport.internalServices.authorizationCode.state -cne 'verified_local' `
            -or $w4bReport.internalServices.tokenExchange.state -cne 'verified_local' `
            -or $w4bReport.internalServices.firstProtectedMcpRequest.state -cne 'verified_local' `
            -or $w4bReport.internalServices.refreshRotation.state -cne 'not_implemented' `
            -or $w4bReport.contract.publicRoutesEnabled -ne $false `
            -or $w4bReport.evidenceBoundaries.workBuddyHostIntegrated -ne $false `
            -or $w4bReport.evidenceBoundaries.sameSessionOfficialRuntimeInvocationVerified -ne $false `
            -or $w4bReport.evidenceBoundaries.productionDatabaseMigrated -ne $false `
            -or $w4bReport.evidenceBoundaries.businessConfirmed -ne $false) {
        throw 'W4b.1 verification receipt drifted or exceeds its evidence boundary'
    }
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

    $w4b2BackendReport = Read-Utf8Json -RelativePath 'reports\independent-board\w4b2-backend-read-projection-verification-20260721.json'
    Assert-ProductBrand -Product $w4b2BackendReport.product -Source 'W4b.2b backend verification report'
    $expectedPortalEndpoints = @(
        '/business/independent-board/oauth/clients',
        '/business/independent-board/oauth/families',
        '/business/independent-board/connector-bindings',
        '/my/independent-board/connector'
    )
    $expectedHeldPortalPaths = @(
        '/business/independent-board/oauth/security-events',
        '/my/independent-board/security-receipts'
    )
    if ($w4b2BackendReport.schema -cne 'fbsir.independent-board.w4b2-backend-read-projection-verification/v1' `
            -or $w4b2BackendReport.result -cne 'VERIFIED_LOCAL_DEFAULT_OFF_BACKEND_READ_CANDIDATE' `
            -or $w4b2BackendReport.releaseReady -ne $false `
            -or $w4b2BackendReport.repository.listedPackageWriteback -ne $false `
            -or $w4b2BackendReport.candidate.defaultEnabled -ne $false `
            -or (Compare-Object -ReferenceObject $expectedPortalEndpoints -DifferenceObject @($w4b2BackendReport.candidate.endpoints)).Count -ne 0 `
            -or (Compare-Object -ReferenceObject $expectedHeldPortalPaths -DifferenceObject @($w4b2BackendReport.candidate.heldPaths)).Count -ne 0 `
            -or @($w4b2BackendReport.verification.dualMysql).Count -ne 2 `
            -or @($w4b2BackendReport.verification.dualMysql | Where-Object { $_.directTransactionTests -eq 56 -and $_.refreshSecurityTests -eq 3 -and $_.canonicalPhasesPassed -eq 3 -and $_.canonicalReceipts -eq 36 -and $_.cleanupGatesPassed -eq 5 -and $_.state -ceq 'PASS' }).Count -ne 2 `
            -or $w4b2BackendReport.evidenceBoundaries.backendPortalReadModelsImplemented -ne $true `
            -or $w4b2BackendReport.evidenceBoundaries.jwtPortalHttpSecurityVerified -ne $true `
            -or $w4b2BackendReport.evidenceBoundaries.realMysqlPortalReadVerified -ne $true `
            -or $w4b2BackendReport.evidenceBoundaries.candidateMenuOrRouterReachable -ne $false `
            -or $w4b2BackendReport.evidenceBoundaries.publicOAuthOrMcpRoutesEnabled -ne $false `
            -or $w4b2BackendReport.evidenceBoundaries.productionDomainsDeployed -ne $false `
            -or $w4b2BackendReport.evidenceBoundaries.listedPackagePhysicalHashReverifiedThisRound -ne $false `
            -or $w4b2BackendReport.verification.fullRepositoryAll.tests -ne 877 `
            -or $w4b2BackendReport.verification.fullRepositoryAll.failures -ne 0 `
            -or $w4b2BackendReport.verification.fullRepositoryAll.errors -ne 0 `
            -or $w4b2BackendReport.verification.fullRepositoryAll.skipped -ne 0 `
            -or $w4b2BackendReport.verification.fullRepositoryAll.state -cne 'PASS' `
            -or $w4b2BackendReport.verification.centralContract.state -cne 'PASS' `
            -or $w4b2BackendReport.verification.centralAll.state -cne 'PASS' `
            -or $w4b2BackendReport.verification.gitDiffCheck -cne 'PASS' `
            -or $w4b2BackendReport.nextSlice.id -cne 'W4b.2c_default_off_runtime_mount_and_browser_gate') {
        throw 'W4b.2b backend verification receipt drifted or exceeds its evidence boundary'
    }
    foreach ($mysqlEvidence in @($w4b2BackendReport.verification.dualMysql)) {
        $summaryPath = Join-Path $RepoRoot ([string]$mysqlEvidence.summary).Replace('/', '\')
        $summaryText = [System.IO.File]::ReadAllText(
            $summaryPath, [System.Text.UTF8Encoding]::new($false)).Replace("`r`n", "`n").Replace("`r", "`n")
        $summaryBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($summaryText)
        $summaryAlgorithm = [System.Security.Cryptography.SHA256]::Create()
        try {
            $summaryHash = ([BitConverter]::ToString(
                $summaryAlgorithm.ComputeHash($summaryBytes))).Replace('-', '').ToLowerInvariant()
        }
        finally {
            $summaryAlgorithm.Dispose()
        }
        $summary = $summaryText | ConvertFrom-Json
        if ($summaryHash -cne $mysqlEvidence.summarySha256 `
                -or $summary.result -cne 'PASS' `
                -or $summary.directIntegration.tests -ne 56 `
                -or $summary.refreshSecurityIntegration.tests -ne 3 `
                -or @($summary.canonicalInitializer.phases | Where-Object { $_.ok -eq $true }).Count -ne 3 `
                -or $summary.canonicalInitializer.publicManifestReceipts -ne 36 `
                -or $summary.cleanup.serverProcessStopped -ne $true `
                -or $summary.cleanup.portClosed -ne $true `
                -or $summary.cleanup.workDirectoryCleaned -ne $true `
                -or $summary.cleanup.temporaryLoginFileCleaned -ne $true `
                -or $summary.cleanup.processEnvironmentRestored -ne $true `
                -or $summary.artifacts.portalReadService.sha256 -cne $w4b2BackendReport.verification.sourceBinding.portalReadServiceSha256 `
                -or $summary.artifacts.portalReadMapper.sha256 -cne $w4b2BackendReport.verification.sourceBinding.portalReadMapperSha256 `
                -or $summary.artifacts.portalReadMapperXml.sha256 -cne $w4b2BackendReport.verification.sourceBinding.portalReadMapperXmlSha256) {
            throw "W4b.2b MySQL evidence drifted: $($mysqlEvidence.summary)"
        }
    }
    $portalSourceHashes = @{
        portalReadServiceSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot 'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\IndependentBoardPortalReadService.java')).Hash.ToLowerInvariant()
        portalReadMapperSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot 'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\mapper\IndependentBoardPortalReadMapper.java')).Hash.ToLowerInvariant()
        portalReadMapperXmlSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot 'FBSir-business\src\main\resources\mapper\board\IndependentBoardPortalReadMapper.xml')).Hash.ToLowerInvariant()
    }
    foreach ($sourceHashName in $portalSourceHashes.Keys) {
        if ($portalSourceHashes[$sourceHashName] -cne $w4b2BackendReport.verification.sourceBinding.$sourceHashName) {
            throw "W4b.2b portal source changed after MySQL evidence: $sourceHashName"
        }
    }

    $implementationStatus = Read-Utf8Json -RelativePath 'docs\independent-board\implementation-status.json'
    $taskboard = Read-Utf8Json -RelativePath 'docs\independent-board\taskboard.json'
    $w4Wave = @($taskboard.waves | Where-Object { $_.id -ceq 'W4_OAUTH_CONNECTOR' })
    if ($implementationStatus.platformVersion -cne '0.4.3-dev' `
            -or $implementationStatus.w4b.backendCandidateVerificationReport -cne 'reports/independent-board/w4b2-backend-read-projection-verification-20260721.json' `
            -or $implementationStatus.w4b.nextSlice -cne 'w4b2c_default_off_candidate_menu_router_runtime_mount_global_admin_tenant_search_existing_ruoyi_security_chain_isolation_and_browser_gate' `
            -or $w4Wave.Count -ne 1 `
            -or $w4Wave[0].activeSlice -cne 'W4b_2c_default_off_runtime_mount_and_browser_gate') {
        throw 'W4b.2b status and taskboard traceability drifted'
    }

    $engineeringContract = Read-Utf8Json -RelativePath '.fbs-engineering\contract.json'
    $contractW4b = $engineeringContract.contracts.uiPrototypeGate.w4bImplementation
    if ($engineeringContract.artifacts.w4b2BackendCandidateVerificationReport -cne 'reports/independent-board/w4b2-backend-read-projection-verification-20260721.json' `
            -or $engineeringContract.artifacts.w4b2PortalReadBoundaryAdr -cne 'docs/decisions/ADR-001-independent-board-default-off-portal-read-boundary.md' `
            -or $contractW4b.state -cne 'w4b1_internal_oauth_chain_and_refresh_security_verified_local_w4b2a_frontend_and_w4b2b_backend_candidates_verified_default_off' `
            -or $contractW4b.nextSlice -cne 'w4b2c_default_off_candidate_menu_router_runtime_mount_global_admin_tenant_search_existing_ruoyi_security_chain_isolation_and_browser_gate' `
            -or $contractW4b.detailedUiPrototype.implementationState -cne 'frontend_source_and_backend_read_candidates_verified_default_off_without_router_menu_public_routes_or_write_actions' `
            -or $contractW4b.detailedUiPrototype.backendCandidateVerificationReport -cne 'reports/independent-board/w4b2-backend-read-projection-verification-20260721.json') {
        throw 'FBS engineering contract drifted from the W4b.2b evidence boundary'
    }

    $w4bContract = Get-Content -LiteralPath (Join-Path $RepoRoot 'docs\independent-board\W4B-OAUTH-MCP-AUTHORIZATION-CONTRACT.md') -Raw -Encoding UTF8
    foreach ($marker in @(
        'contract_locked_with_w4b_1_internal_oauth_chain_and_refresh_security_verified_local',
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
            Invoke-FrontendNpm -Arguments @('ci')
        }
        Invoke-FrontendNpm -Arguments @('run', 'verify:portal-entry')
        Invoke-FrontendNpm -Arguments @('run', 'verify:independent-board-ui')
        Invoke-FrontendNpm -Arguments @('run', 'verify:independent-board-admin-ui')
        Invoke-FrontendNpm -Arguments @('run', 'verify:independent-board-w4b2-candidate')
        Invoke-FrontendNpm -Arguments @('run', 'verify:menu-components')
        Invoke-FrontendNpm -Arguments @('run', 'build:prod')
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
