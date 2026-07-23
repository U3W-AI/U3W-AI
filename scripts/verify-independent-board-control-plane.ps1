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

function Invoke-FrontendPortableTask {
    param(
        [string]$NodeExecutable,
        [string[]]$Arguments
    )

    if ($Arguments.Count -ne 2 -or $Arguments[0] -cne 'run') {
        throw 'portable frontend execution only supports an allowlisted npm run task'
    }
    $tasks = @{
        'verify:portal-entry' = @('scripts\verify-portal-entry.mjs')
        'verify:independent-board-ui' = @('scripts\verify-independent-board-ui.mjs')
        'verify:independent-board-admin-ui' = @('scripts\verify-independent-board-admin-ui.mjs')
        'verify:independent-board-w4b2-candidate' = @('scripts\verify-independent-board-w4b2-candidate.mjs')
        'verify:independent-board-w4b2c-runtime-mount' = @('scripts\verify-independent-board-w4b2c-runtime-mount.mjs')
        'verify:menu-components' = @('scripts\verify-menu-components.mjs')
        'build:prod' = @('node_modules\vite\bin\vite.js', 'build')
    }
    if (-not $tasks.ContainsKey($Arguments[1])) {
        throw "portable frontend execution rejects unallowlisted task: $($Arguments[1])"
    }
    $taskArguments = $tasks[$Arguments[1]]
    & $NodeExecutable @taskArguments
    if ($LASTEXITCODE -ne 0) {
        throw "portable frontend task failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Invoke-FrontendNpm {
    param([string[]]$Arguments)

    $NodeOverride = $env:U3W_NODE_EXE
    $NpmCliOverride = $env:U3W_NPM_CLI
    $HasNodeOverride = -not [string]::IsNullOrWhiteSpace($NodeOverride)
    $HasNpmCliOverride = -not [string]::IsNullOrWhiteSpace($NpmCliOverride)
    if ($HasNodeOverride -and -not $HasNpmCliOverride) {
        if (-not (Test-Path -LiteralPath $NodeOverride -PathType Leaf)) {
            throw "U3W_NODE_EXE does not exist: $NodeOverride"
        }
        if (-not (Test-Path -LiteralPath 'node_modules' -PathType Container)) {
            throw 'portable frontend execution requires preinstalled node_modules; npm CLI is required for dependency installation'
        }
        Invoke-FrontendPortableTask -NodeExecutable $NodeOverride -Arguments $Arguments
        return
    }
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

function Resolve-NodeExecutable {
    if (-not [string]::IsNullOrWhiteSpace($env:U3W_NODE_EXE)) {
        if (-not (Test-Path -LiteralPath $env:U3W_NODE_EXE -PathType Leaf)) {
            throw "U3W_NODE_EXE does not exist: $($env:U3W_NODE_EXE)"
        }
        return $env:U3W_NODE_EXE
    }
    $NodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($null -eq $NodeCommand) {
        $NodeCommand = Get-Command node -ErrorAction SilentlyContinue
    }
    if ($null -eq $NodeCommand) {
        throw 'node is unavailable; set U3W_NODE_EXE to a verified toolchain path'
    }
    return $NodeCommand.Source
}

function Invoke-NodeScript {
    param([string[]]$Arguments)
    $NodeExecutable = Resolve-NodeExecutable
    & $NodeExecutable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "node command failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
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
        'docs\independent-board\W1A-OFFICIAL-EXPERTS-ATTRIBUTION-CONTRACT.md',
        'docs\decisions\ADR-007-independent-board-official-experts-attribution-spine.md',
        'docs\independent-board\AUTHORITATIVE-ROOT.md',
        'docs\independent-board\PORTAL-PROTOTYPE-SPEC.md',
        'docs\independent-board\W3B-ENTITLEMENT-LIFECYCLE-CONTRACT.md',
        'docs\independent-board\W3J-CREDIT-CANDIDATE-RELEASE-READINESS-CONTRACT.md',
        'docs\independent-board\W3J-CREDIT-CANDIDATE-RELEASE-RECEIPT.example.json',
        'docs\independent-board\W4A-AUTHORITATIVE-CONNECTOR-BINDING-CONTRACT.md',
        'docs\independent-board\W4B-OAUTH-MCP-AUTHORIZATION-CONTRACT.md',
        'docs\independent-board\W4B-DATABASE-SUPPORT-MATRIX.md',
        'docs\independent-board\W4B-INPUT-EVIDENCE.json',
        'docs\decisions\ADR-001-independent-board-default-off-portal-read-boundary.md',
        'docs\decisions\ADR-002-independent-board-w4b2c-runtime-mount-and-attribution-boundary.md',
        'docs\independent-board\API2-INDEPENDENT-BOARD-24H-TRAFFIC-ATTRIBUTION-20260721.md',
        'docs\independent-board\HOST-UPGRADE-DEMAND-INDEPENDENT-BOARD-ATTRIBUTION.md',
        'docs\independent-board\prototypes\portals\index.html',
        'sql\update_20260720_independent_board_control_plane.sql',
        'sql\update_20260720_independent_board_me_menu.sql',
        'sql\update_20260720_independent_board_admin_menu.sql',
        'sql\update_20260720_independent_board_entitlement_lifecycle_menu.sql',
        'sql\update_20260721_independent_board_connector_binding.sql',
        'sql\update_20260721_independent_board_oauth_foundation.sql',
        'sql\update_20260721_independent_board_portal_candidate_menu.sql',
        'scripts\verify-database-manifest.ps1',
        'scripts\verify-independent-board-live-database.ps1',
        'scripts\run-independent-board-menu-migration-it.ps1',
        'scripts\verify-independent-board-mysql-concurrency.ps1',
        'scripts\run-independent-board-mysql-transaction-it.ps1',
        'scripts\run-independent-board-attribution-v1-mysql-it.ps1',
        'reports\independent-board\w1a-attribution-v1-dual-mysql-latest.json',
        'sql\update_20260723_independent_board_attribution_v1.sql',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\attribution\receipt\BoardAttributionEventV1.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\attribution\receipt\BoardAttributionEventV1Verifier.java',
        'FBSir-business\src\test\resources\independent-board-attribution-v1-golden-vector.json',
        'FBSir-business\src\test\java\com\wx\fbsir\business\board\attribution\receipt\BoardAttributionEventV1VerifierTest.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\attribution\service\IndependentBoardAttributionIngestService.java',
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\attribution\service\IndependentBoardAttributionAdminReadService.java',
        'FBSir-business\src\main\resources\mapper\board\attribution\IndependentBoardAttributionV1Mapper.xml',
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
        'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\dto\BoardPortalTenantView.java',
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
        'FBSir-ui\scripts\verify-independent-board-w4b2c-runtime-mount.mjs',
        'FBSir-ui\src\utils\independentBoardPortalCandidate.js',
        'scripts\independent-board-traffic-attribution.mjs',
        'scripts\independent-board-traffic-attribution.test.mjs',
        'reports\independent-board\w1-application-integration-verification-20260720.json',
        'reports\independent-board\w2-user-portal-verification-20260720.json',
        'reports\independent-board\w3-admin-portal-verification-20260720.json',
        'reports\independent-board\w3b-entitlement-lifecycle-verification-20260720.json',
        'reports\independent-board\w4a-authoritative-connector-binding-verification-20260721.json',
        'reports\independent-board\w4b-oauth-foundation-verification-20260721.json',
        'reports\independent-board\w4b2-backend-read-projection-verification-20260721.json',
        'reports\independent-board\w4b2c-default-off-runtime-mount-verification-20260721.json',
        'reports\independent-board\w3g-credit-admin-ui-verification-20260722.json',
        'reports\independent-board\w3j-credit-candidate-release-readiness-verification-20260722.json',
        'reports\independent-board\points-balance-cas-mysql-verification-20260723.json',
        'reports\independent-board\skill-consume-candidate-fence-verification-20260723.json',
        'reports\independent-board\skill-consume-command-verification-20260723.json',
        'reports\independent-board\skill-consume-writer-verification-20260723.json',
        'reports\independent-board\skill-consume-dual-mysql-transaction-verification-20260723.json',
        'reports\independent-board\skill-consume-host-wiring-verification-20260723.json',
        'reports\independent-board\api2-independent-board-24h-traffic-attribution-20260721.json',
        'reports\independent-board\api2-independent-board-24h-traffic-attribution-20260721.md',
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
    # W4b.2b remains immutable historical evidence. Current source bytes are
    # instead bound to the W4b.2c receipt that exercised the runtime mount.
    $canonicalNextSliceId = 'W4b_2d_api2_exact_product_binding_and_immutable_evidence_contract'
    $canonicalW3hSuccessorSliceId = 'W3i_meeting_audit_policy_lineage_and_label_authority'
    $canonicalW3iSuccessorSliceId = 'W3j_credit_candidate_release_readiness_gate'
    $canonicalW3NextSliceId = 'W3j_activation_release_receipt_collection_and_human_approval'
    $canonicalW4B5ESliceId = 'W4B5E_skill_consume_activation_safety_and_release_receipt_refresh'
    $canonicalCurrentW3SliceId = 'W4B5F_skill_consume_commercial_hub_outbox_and_gateway'
    $w4b2cReport = Read-Utf8Json -RelativePath 'reports\independent-board\w4b2c-default-off-runtime-mount-verification-20260721.json'
    Assert-ProductBrand -Product $w4b2cReport.product -Source 'W4b.2c runtime mount verification report'
    if ($w4b2cReport.schema -cne 'fbsir.independent-board.w4b2c-default-off-runtime-mount-verification/v1' `
            -or $w4b2cReport.result -cne 'VERIFIED_LOCAL_DEFAULT_OFF_RUNTIME_MOUNT_CANDIDATE' `
            -or $w4b2cReport.releaseReady -ne $false `
            -or $w4b2cReport.repository.listedPackageWriteback -ne $false `
            -or $w4b2cReport.candidate.featureFlagProperty -cne 'fbsir.independent-board.portal-candidate.enabled' `
            -or $w4b2cReport.candidate.environmentVariable -cne 'FBSIR_INDEPENDENT_BOARD_PORTAL_CANDIDATE_ENABLED' `
            -or $w4b2cReport.candidate.viteEnvironmentVariable -cne 'VITE_FBSIR_BOARD_PORTAL_CANDIDATE' `
            -or $w4b2cReport.candidate.defaultEnabled -ne $false `
            -or $w4b2cReport.candidate.menuCount -ne 5 `
            -or $w4b2cReport.candidate.defaultDisabledMenuCount -ne 5 `
            -or $w4b2cReport.candidate.meUserRoleBindingCount -ne 1 `
            -or $w4b2cReport.candidate.adminRoleBindingCount -ne 0 `
            -or $w4b2cReport.candidate.allowedRuntimePages -ne 4 `
            -or $w4b2cReport.candidate.heldRuntimePages -ne 2 `
            -or @($w4b2cReport.candidate.httpMethodAllowlist).Count -ne 1 `
            -or $w4b2cReport.candidate.httpMethodAllowlist[0] -cne 'GET' `
            -or $w4b2cReport.candidate.writeActionsAdded -ne $false `
            -or $w4b2cReport.candidate.securityEventGetAdded -ne $false `
            -or $w4b2cReport.candidate.publicOAuthOrMcpRoutesAdded -ne $false `
            -or $w4b2cReport.verification.backendAll.testcaseNodes -ne 947 `
            -or $w4b2cReport.verification.backendAll.failures -ne 0 `
            -or $w4b2cReport.verification.backendAll.errors -ne 0 `
            -or $w4b2cReport.verification.centralAll.mode -cne 'All' `
            -or $w4b2cReport.verification.centralAll.state -cne 'PASS' `
            -or @($w4b2cReport.verification.dualMysql).Count -ne 2 `
            -or @($w4b2cReport.verification.dualMysql | Where-Object { $_.directTransactionTests -eq 56 -and $_.refreshSecurityTests -eq 3 -and $_.canonicalReceipts -eq 36 -and $_.cleanupGatesPassed -eq 5 -and $_.state -ceq 'PASS' }).Count -ne 2 `
            -or $w4b2cReport.verification.frontendSourceCandidate.directAssertionCallSites -ne 122 `
            -or $w4b2cReport.verification.runtimeMountContract.fourPageAllowlist -cne 'PASS' `
            -or $w4b2cReport.verification.runtimeMountContract.heldPageDenylist -cne 'PASS' `
            -or $w4b2cReport.verification.runtimeMountContract.preCloneRouteAdmission -cne 'PASS' `
            -or $w4b2cReport.verification.browser.enabledAllowedPagesAccessible -ne 4 `
            -or $w4b2cReport.verification.browser.enabledHeldPagesHttp404 -ne 2 `
            -or $w4b2cReport.verification.browser.defaultOffCandidateRequests -ne 0 `
            -or $w4b2cReport.verification.browser.physicalTabKeyActuation -cne 'NOT_PROVEN_TOOL_LIMITATION' `
            -or $w4b2cReport.verification.trafficAttributionContract.tests -ne 42 `
            -or $w4b2cReport.verification.trafficAttributionContract.failures -ne 0 `
            -or $w4b2cReport.evidenceBoundaries.candidateRuntimeMountedLocally -ne $true `
            -or $w4b2cReport.evidenceBoundaries.defaultOffVerified -ne $true `
            -or $w4b2cReport.evidenceBoundaries.globalAdminTenantSearchVerified -ne $true `
            -or $w4b2cReport.evidenceBoundaries.existingRuoyiJwtIsolationVerified -ne $true `
            -or $w4b2cReport.evidenceBoundaries.productionDatabaseMigrated -ne $false `
            -or $w4b2cReport.evidenceBoundaries.productionDomainsDeployed -ne $false `
            -or $w4b2cReport.evidenceBoundaries.publicOAuthOrMcpRoutesEnabled -ne $false `
            -or $w4b2cReport.evidenceBoundaries.listedPackageModified -ne $false `
            -or $w4b2cReport.nextSlice.id -cne $canonicalNextSliceId) {
        throw 'W4b.2c runtime mount verification receipt drifted or exceeds its evidence boundary'
    }
    # W4b.2c remains immutable historical evidence. W3g intentionally extends
    # the shared dynamic-route gate and owns the successor bytes, so the
    # current route-gate binding must come from the W3g receipt together with
    # an explicit W4b.2c regression result. All other W4b.2c sources remain
    # pinned to the historical receipt.
    $w3gReport = Read-Utf8Json -RelativePath 'reports\independent-board\w3g-credit-admin-ui-verification-20260722.json'
    Assert-ProductBrand -Product $w3gReport.product -Source 'W3g credit admin UI verification report'
    if ($w3gReport.schema -cne 'fbsir.independent-board.w3g-credit-admin-ui-verification/v1' `
            -or $w3gReport.result -cne 'VERIFIED_LOCAL_DEFAULT_OFF_CREDIT_ADMIN_CANDIDATE' `
            -or $w3gReport.releaseReady -ne $false `
            -or $w3gReport.repository.listedPackageWriteback -ne $false `
            -or $w3gReport.candidate.defaultEnabled -ne $false `
            -or $w3gReport.verification.frontendVerifiers.w4b2cRuntimeMount -cne 'PASS' `
            -or $w3gReport.evidenceSuccession.sharedSource -cne 'FBSir-ui/src/utils/independentBoardPortalCandidate.js' `
            -or $w3gReport.evidenceSuccession.predecessorReceipt -cne 'reports/independent-board/w4b2c-default-off-runtime-mount-verification-20260721.json' `
            -or $w3gReport.evidenceSuccession.predecessorRegressionVerifier -cne 'FBSir-ui/scripts/verify-independent-board-w4b2c-runtime-mount.mjs' `
            -or $w3gReport.evidenceSuccession.predecessorRegressionState -cne 'PASS' `
            -or $w3gReport.evidenceSuccession.historicalReceiptMutated -ne $false `
            -or $w3gReport.truthBoundary.localVerifiedDoesNotProveProduction -ne $true `
            -or $w3gReport.truthBoundary.frozenExpertPackageModified -ne $false) {
        throw 'W3g successor evidence receipt drifted or exceeds its evidence boundary'
    }
    $predecessorRouteGateHash = $w4b2cReport.verification.sourceBinding.routeGateSha256
    $currentRouteGateHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot 'FBSir-ui\src\utils\independentBoardPortalCandidate.js')).Hash.ToLowerInvariant()
    if ($w3gReport.evidenceSuccession.predecessorSourceSha256 -cne $predecessorRouteGateHash `
            -or $w3gReport.evidenceSuccession.successorSourceSha256 -cne $currentRouteGateHash `
            -or $w3gReport.sourceBinding.routeGateSha256 -cne $currentRouteGateHash) {
        throw 'W3g shared route-gate evidence succession is incomplete or drifted'
    }

    # W3h changes the connector-eligibility read from mutable quota values to
    # immutable plan identity.  The historical W4b.2c receipt stays intact;
    # W3h owns the successor bytes and proves the W4b.2c runtime regression.
    $w3hGovernanceReport = Read-Utf8Json -RelativePath 'reports\independent-board\w3h-plan-policy-governance-verification-20260722.json'
    $predecessorPortalReadMapperHash = $w4b2cReport.verification.sourceBinding.portalReadMapperXmlSha256
    $currentPortalReadMapperHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot 'FBSir-business\src\main\resources\mapper\board\IndependentBoardPortalReadMapper.xml')).Hash.ToLowerInvariant()
    if ($w3hGovernanceReport.schemaVersion -ne 1 `
            -or $w3hGovernanceReport.result -cne 'PASS_LOCAL_CANDIDATE' `
            -or $w3hGovernanceReport.candidateReadyForCommit -ne $true `
            -or $w3hGovernanceReport.releaseReady -ne $false `
            -or $w3hGovernanceReport.productionChanged -ne $false `
            -or $w3hGovernanceReport.sharedEvidenceSuccession.sharedSource -cne 'FBSir-business/src/main/resources/mapper/board/IndependentBoardPortalReadMapper.xml' `
            -or $w3hGovernanceReport.sharedEvidenceSuccession.predecessorReceipt -cne 'reports/independent-board/w4b2c-default-off-runtime-mount-verification-20260721.json' `
            -or $w3hGovernanceReport.sharedEvidenceSuccession.predecessorRegressionVerifier -cne 'FBSir-ui/scripts/verify-independent-board-w4b2c-runtime-mount.mjs' `
            -or $w3hGovernanceReport.sharedEvidenceSuccession.predecessorRegressionState -cne 'PASS' `
            -or $w3hGovernanceReport.sharedEvidenceSuccession.historicalReceiptMutated -ne $false `
            -or $w3hGovernanceReport.sharedEvidenceSuccession.predecessorSourceSha256 -cne $predecessorPortalReadMapperHash `
            -or $w3hGovernanceReport.sharedEvidenceSuccession.successorSourceSha256 -cne $currentPortalReadMapperHash `
            -or $w3hGovernanceReport.sourceSha256.'FBSir-business/src/main/resources/mapper/board/IndependentBoardPortalReadMapper.xml' -cne $currentPortalReadMapperHash) {
        throw 'W3h shared portal-read mapper evidence succession is incomplete or drifted'
    }

    # W3i consumes W3h immutable receipts and changes several sources that W3h
    # pinned. The W3h historical receipt must remain immutable while this
    # successor receipt binds every changed shared byte to a current verifier.
    $w3iLineageReport = Read-Utf8Json -RelativePath 'reports\independent-board\w3i-meeting-audit-policy-lineage-verification-20260723.json'
    if ($w3iLineageReport.schemaVersion -ne 1 `
            -or $w3iLineageReport.result -cne 'PASS_LOCAL_CANDIDATE' `
            -or $w3iLineageReport.candidateReadyForCommit -ne $true `
            -or $w3iLineageReport.releaseReady -ne $false `
            -or $w3iLineageReport.productionChanged -ne $false `
            -or $w3iLineageReport.sharedEvidenceSuccession.predecessorReceipt -cne 'reports/independent-board/w3h-plan-policy-governance-verification-20260722.json' `
            -or $w3iLineageReport.sharedEvidenceSuccession.historicalReceiptMutated -ne $false `
            -or $w3iLineageReport.git.implementationCommit -cne '9998b8c1b168a56437aceb99901195f561458586' `
            -or $w3iLineageReport.frozenSurface.unchanged -ne $true `
            -or $w3iLineageReport.frozenSurface.observedSha256 -cne $w3iLineageReport.frozenSurface.requiredSha256) {
        throw 'W3i meeting-audit lineage report boundary is incomplete or drifted'
    }
    $w3iSharedSources = @(
        @{ path = 'FBSir-business/src/main/resources/mapper/board/IndependentBoardMapper.xml'; predecessor = '1390b5e1189b54f389f6d56f455c829e22882b3d8dd47c4db4ae35ac33d3a56f' },
        @{ path = 'FBSir-business/src/main/java/com/wx/fbsir/business/board/plan/controller/IndependentBoardPlanPolicyNoStoreFilter.java'; predecessor = '8dd1c41364d9f2be80848577b410b119769c7cf9860ff2e4e8dfbf4151979399' },
        @{ path = 'FBSir-business/src/test/java/com/wx/fbsir/business/board/plan/controller/IndependentBoardPlanPolicyNoStoreFilterTest.java'; predecessor = '726f9fe4277727bbb770ad35c3a2fbf0a4454df777f7fd260d46891ed8a74ab7' },
        @{ path = 'FBSir-ui/src/views/business/independentBoard/admin/model.js'; predecessor = '68395b4135b91e345aa95767258de5747645bcbc019e2956972ae54469636f75' },
        @{ path = 'scripts/run-independent-board-plan-policy-mysql-it.ps1'; predecessor = '5f43c2750edb9a429e645008145363c0379a404075199011c29f3fa66c006fba' }
    )
    foreach ($expectedSharedSource in $w3iSharedSources) {
        $lineageReceipts = @($w3iLineageReport.sharedEvidenceSuccession.sources | Where-Object {
                $_.sharedSource -ceq $expectedSharedSource.path
            })
        $boundSourceHash = $w3iLineageReport.sourceSha256.PSObject.Properties[$expectedSharedSource.path].Value
        if ($lineageReceipts.Count -ne 1 `
                -or $lineageReceipts[0].predecessorSourceSha256 -cne $expectedSharedSource.predecessor `
                -or $lineageReceipts[0].successorSourceSha256 -cne $boundSourceHash `
                -or [string]::IsNullOrWhiteSpace($lineageReceipts[0].predecessorRegressionVerifier) `
                -or $lineageReceipts[0].predecessorRegressionState -cne 'PASS' `
                -or [string]::IsNullOrWhiteSpace($boundSourceHash)) {
            throw "W3i shared-source evidence succession is incomplete or drifted: $($expectedSharedSource.path)"
        }
    }

    # W3k is the successor receipt for the shared dual-MySQL runner changed
    # after W3i.  Do not rewrite W3i's historical receipt: bind its old byte
    # to W3k's explicit predecessor and validate the new source separately.
    $w3kMonotonicReport = Read-Utf8Json -RelativePath 'reports\independent-board\w3k-plan-policy-monotonic-chain-verification-20260723.json'
    if ($w3kMonotonicReport.schemaVersion -ne 1 `
            -or $w3kMonotonicReport.result -cne 'PASS_LOCAL_CANDIDATE' `
            -or $w3kMonotonicReport.candidateReadyForCommit -ne $true `
            -or $w3kMonotonicReport.releaseReady -ne $false `
            -or $w3kMonotonicReport.productionChanged -ne $false `
            -or $w3kMonotonicReport.predecessorReceipt -cne 'reports/independent-board/w3i-meeting-audit-policy-lineage-verification-20260723.json' `
            -or $w3kMonotonicReport.historicalReceiptMutated -ne $false `
            -or $w3kMonotonicReport.frozenSurface.unchanged -ne $true `
            -or $w3kMonotonicReport.frozenSurface.observedSha256 -cne $w3kMonotonicReport.frozenSurface.requiredSha256) {
        throw 'W3k plan-policy monotonic-chain report boundary is incomplete or drifted'
    }
    $w3kExpectedArtifacts = @(
        'docs/decisions/ADR-005-independent-board-plan-policy-database-monotonic-chain.md',
        'docs/independent-board/W3K-PLAN-POLICY-MONOTONIC-CHAIN-CONTRACT.md',
        'docs/independent-board/taskboard.json',
        'docs/independent-board/implementation-status.json',
        '.fbs-engineering/contract.json',
        'sql/update_20260723_independent_board_plan_policy_monotonic_chain.sql',
        'sql/init-manifest.json',
        'scripts/init-database.ps1',
        'scripts/verify-database-manifest.ps1',
        'scripts/run-independent-board-plan-policy-mysql-it.ps1'
    )
    $w3kSupersededAuthorityArtifacts = @(
        'docs/independent-board/taskboard.json',
        'docs/independent-board/implementation-status.json',
        '.fbs-engineering/contract.json',
        'sql/init-manifest.json',
        'scripts/init-database.ps1',
        'scripts/verify-database-manifest.ps1',
        'scripts/run-independent-board-plan-policy-mysql-it.ps1'
    )
    foreach ($w3kExpectedArtifact in $w3kExpectedArtifacts) {
        if ($w3kExpectedArtifact -in $w3kSupersededAuthorityArtifacts) {
            continue
        }
        $currentW3kArtifactHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot $w3kExpectedArtifact)).Hash.ToLowerInvariant()
        $boundW3kArtifactHash = $w3kMonotonicReport.sourceSha256.PSObject.Properties[$w3kExpectedArtifact].Value
        if ($boundW3kArtifactHash -cne $currentW3kArtifactHash) {
            throw "W3k artifact binding drifted: $w3kExpectedArtifact"
        }
    }
    $w3kRunnerSuccession = @($w3kMonotonicReport.sharedEvidenceSuccession.sources | Where-Object {
            $_.sharedSource -ceq 'scripts/run-independent-board-plan-policy-mysql-it.ps1'
        })
    if ($w3kRunnerSuccession.Count -ne 1 `
            -or $w3kRunnerSuccession[0].predecessorSourceSha256 -cne '906defb4f9e35e28003b6d22c09151cd374b636824a1b6648c99aaf4981f161b' `
            -or $w3kRunnerSuccession[0].predecessorRegressionState -cne 'PASS' `
            -or [string]::IsNullOrWhiteSpace($w3kRunnerSuccession[0].predecessorRegressionVerifier)) {
        throw 'W3k shared dual-MySQL runner evidence succession is incomplete or drifted'
    }

    $w3kAuthorityReport = Read-Utf8Json -RelativePath 'reports\independent-board\w3k-plan-policy-controlled-authority-verification-20260723.json'
    if ($w3kAuthorityReport.schemaVersion -ne 1 `
            -or $w3kAuthorityReport.result -cne 'LOCAL_CANDIDATE_MYSQL_MATRIX_PENDING' `
            -or $w3kAuthorityReport.candidateReadyForCommit -ne $false `
            -or $w3kAuthorityReport.releaseReady -ne $false `
            -or $w3kAuthorityReport.productionChanged -ne $false `
            -or $w3kAuthorityReport.predecessorReceipt -cne 'reports/independent-board/w3k-plan-policy-monotonic-chain-verification-20260723.json' `
            -or $w3kAuthorityReport.historicalReceiptMutated -ne $false `
            -or $w3kAuthorityReport.frozenSurface.unchanged -ne $true `
            -or $w3kAuthorityReport.frozenSurface.observedSha256 -cne $w3kAuthorityReport.frozenSurface.requiredSha256) {
        throw 'W3k controlled-authority report boundary is incomplete or drifted'
    }
    # The pending authority receipt is historical. The local dual-MySQL run is
    # a successor record, so do not rewrite its source hashes after the runner
    # and SQL fixes that it motivated.
    $w3kAuthoritySuccessorReport = Read-Utf8Json -RelativePath 'reports\independent-board\w3k-plan-policy-authority-mysql-verification-20260723.json'
    $authorityPendingReportPath = Join-Path $RepoRoot 'reports\independent-board\w3k-plan-policy-controlled-authority-verification-20260723.json'
    $authorityPendingReportHash = (Get-FileHash -LiteralPath $authorityPendingReportPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($w3kAuthoritySuccessorReport.schemaVersion -ne 1 `
            -or $w3kAuthoritySuccessorReport.result -cne 'PASS_LOCAL_DUAL_MYSQL_AUTHORITY_MATRIX' `
            -or $w3kAuthoritySuccessorReport.candidateReadyForCommit -ne $true `
            -or $w3kAuthoritySuccessorReport.releaseReady -ne $false `
            -or $w3kAuthoritySuccessorReport.productionChanged -ne $false `
            -or $w3kAuthoritySuccessorReport.predecessorReceipt -cne 'reports/independent-board/w3k-plan-policy-controlled-authority-verification-20260723.json' `
            -or $w3kAuthoritySuccessorReport.predecessorReceiptSha256 -cne $authorityPendingReportHash `
            -or $w3kAuthoritySuccessorReport.historicalReceiptMutated -ne $false `
            -or $w3kAuthoritySuccessorReport.frozenSurface.unchanged -ne $true `
            -or $w3kAuthoritySuccessorReport.frozenSurface.observedSha256 -cne $w3kAuthoritySuccessorReport.frozenSurface.requiredSha256 `
            -or @($w3kAuthoritySuccessorReport.mysql.versions).Count -ne 2 `
            -or @($w3kAuthoritySuccessorReport.mysql.versions | Where-Object {
                $_.concurrency.tests -ne 6 -or $_.concurrency.failures -ne 0 -or $_.concurrency.errors -ne 0 `
                    -or $_.concurrency.iterationsPerCase -ne 20 -or $_.accountMatrix.executors -ne 2 `
                    -or $_.accountMatrix.denialsPerExecutor -ne 5 -or $_.productionConnectionUsed -ne $false `
                    -or $_.workDirectoryCleaned -ne $true
            }).Count -ne 0) {
        throw 'W3k controlled-authority dual-MySQL successor evidence is incomplete or drifted'
    }
    # The predecessor report hash binds its full source-hash map as one
    # immutable receipt; source-file binding resumes at the successor.  W3l
    # is the next, independent ledger-schema successor.  It may supersede
    # shared initializer/manifest verifier bytes, but it must never rewrite
    # the historical W3k authority receipt.
    $w3lCreditLedgerReport = Read-Utf8Json -RelativePath 'reports\independent-board\w3l-skill-consume-credit-ledger-v2-migration-verification-20260723.json'
    $w3lPredecessorPath = 'reports/independent-board/w3k-plan-policy-authority-mysql-verification-20260723.json'
    $w3lPredecessorHash = (Get-FileHash -LiteralPath (Join-Path $RepoRoot $w3lPredecessorPath) -Algorithm SHA256).Hash.ToLowerInvariant()
    $w3lRequiredArtifacts = @(
        'docs/independent-board/W3L-SKILL-CONSUME-CREDIT-WRITER-CONTRACT.md',
        'sql/update_20260723_skill_consume_credit_ledger_v2.sql',
        'sql/init-manifest.json',
        'scripts/init-database.ps1',
        'scripts/verify-database-manifest.ps1',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/SkillConsumeCreditLedgerV2MigrationContractTest.java'
    )
    $w3lSourceArtifacts = @($w3lCreditLedgerReport.sourceSha256.PSObject.Properties | ForEach-Object { [string]$_.Name })
    $w3lFrozenSurfacePath = 'D:/Spg719/fbsir-eight-seat-board-26.7.20.zip'
    if (-not (Test-Path -LiteralPath $w3lFrozenSurfacePath -PathType Leaf)) {
        throw "W3l frozen package evidence is missing: $w3lFrozenSurfacePath"
    }
    $w3lFrozenSurfaceHash = (Get-FileHash -LiteralPath $w3lFrozenSurfacePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $w3lAllowedAuthoritySupersession = @(
        'sql/init-manifest.json',
        'scripts/verify-independent-board-control-plane.ps1'
    )
    $w3lAuthorityOverlaps = @($w3lSourceArtifacts | Where-Object {
            $null -ne $w3kAuthoritySuccessorReport.sourceSha256.PSObject.Properties[$_]
        })
    if ($w3lCreditLedgerReport.schemaVersion -ne 1 `
            -or $w3lCreditLedgerReport.result -cne 'PASS_LOCAL_DUAL_MYSQL_MIGRATION_SMOKE' `
            -or $w3lCreditLedgerReport.candidateReadyForCommit -ne $true `
            -or $w3lCreditLedgerReport.releaseReady -ne $false `
            -or $w3lCreditLedgerReport.productionChanged -ne $false `
            -or $w3lCreditLedgerReport.historicalReceiptMutated -ne $false `
            -or $w3lCreditLedgerReport.predecessorReceipt -cne $w3lPredecessorPath `
            -or $w3lCreditLedgerReport.predecessorReceiptSha256 -cne $w3lPredecessorHash `
            -or $w3lCreditLedgerReport.frozenSurface.unchanged -ne $true `
            -or $w3lCreditLedgerReport.frozenSurface.path -cne $w3lFrozenSurfacePath `
            -or $w3lCreditLedgerReport.frozenSurface.observedSha256 -cne $w3lCreditLedgerReport.frozenSurface.requiredSha256 `
            -or $w3lCreditLedgerReport.frozenSurface.observedSha256 -cne $w3lFrozenSurfaceHash `
            -or $w3lCreditLedgerReport.migration.publicReceipt -cne 'public_init_042' `
            -or $w3lCreditLedgerReport.migration.version -cne '20260723_skill_consume_credit_ledger_v2_042' `
            -or $w3lCreditLedgerReport.verification.canonicalCurrentRead -cne 'the default-off 042 current-read passed after first apply on isolated MySQL 8.0.30, including exact column/index/FK/CHECK digests and eight trigger-body hashes' `
            -or @($w3lCreditLedgerReport.mysql.versions).Count -ne 2 `
            -or @($w3lCreditLedgerReport.mysql.versions | Where-Object { $_.version -ceq '8.0.30' }).Count -ne 1 `
            -or @($w3lCreditLedgerReport.mysql.versions | Where-Object { $_.version -ceq '8.4.8' }).Count -ne 1 `
            -or @($w3lCreditLedgerReport.mysql.versions | Where-Object {
                ($_.version -cne '8.0.30' -and $_.version -cne '8.4.8') `
                    -or $_.tableCount -ne 4 -or $_.triggerCount -ne 8 `
                    -or $_.internalReceiptCount -ne 1 -or $_.productionConnectionUsed -ne $false `
                    -or $_.firstApplyAndReplay -cne 'PASS' -or $_.rawMetadataDigests -cne 'PASS' `
                    -or $_.workDirectoryCleaned -ne $true
            }).Count -ne 0) {
        throw 'W3l default-off skill-consume credit-ledger migration successor evidence is incomplete or drifted'
    }
    foreach ($w3lRequiredArtifact in $w3lRequiredArtifacts) {
        if ($w3lRequiredArtifact -notin $w3lSourceArtifacts) {
            throw "W3l migration successor does not bind required artifact: $w3lRequiredArtifact"
        }
    }
    # The original W3l record is immutable.  A later real dual-MySQL run found
    # its CHECK-metadata hash was not executable on either declared runtime.
    # Bind the corrected bytes and runner in a dedicated successor instead of
    # rewriting that historical receipt.
    $w3lRepairSuccessorPath = 'reports/independent-board/w3l-skill-consume-credit-ledger-v2-mysql-verification-20260723.json'
    $w3lRepairSuccessorReport = Read-Utf8Json -RelativePath $w3lRepairSuccessorPath.Replace('/', '\')
    $w3lRepairPredecessorHash = (Get-FileHash -LiteralPath (Join-Path $RepoRoot 'reports\independent-board\w3l-skill-consume-credit-ledger-v2-migration-verification-20260723.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    $w3lRepairSupersededArtifacts = @(
        'docs/independent-board/W3L-SKILL-CONSUME-CREDIT-WRITER-CONTRACT.md',
        'sql/update_20260723_skill_consume_credit_ledger_v2.sql',
        'sql/init-manifest.json',
        'scripts/init-database.ps1',
        'scripts/verify-database-manifest.ps1',
        'scripts/verify-independent-board-control-plane.ps1',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/SkillConsumeCreditLedgerV2MigrationContractTest.java'
    )
    $w3lRepairRequiredArtifacts = @($w3lRepairSupersededArtifacts) + @(
        'scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1'
    )
    $w3lRepairSourceArtifacts = @($w3lRepairSuccessorReport.sourceSha256.PSObject.Properties | ForEach-Object { [string]$_.Name })
    if ($w3lRepairSuccessorReport.schemaVersion -ne 1 `
            -or $w3lRepairSuccessorReport.result -cne 'PASS_LOCAL_DUAL_MYSQL_MIGRATION_SMOKE' `
            -or $w3lRepairSuccessorReport.candidateReadyForCommit -ne $true `
            -or $w3lRepairSuccessorReport.releaseReady -ne $false `
            -or $w3lRepairSuccessorReport.productionChanged -ne $false `
            -or $w3lRepairSuccessorReport.historicalReceiptMutated -ne $false `
            -or $w3lRepairSuccessorReport.predecessorReceipt -cne 'reports/independent-board/w3l-skill-consume-credit-ledger-v2-migration-verification-20260723.json' `
            -or $w3lRepairSuccessorReport.predecessorReceiptSha256 -cne $w3lRepairPredecessorHash `
            -or $w3lRepairSuccessorReport.frozenSurface.path -cne $w3lFrozenSurfacePath `
            -or $w3lRepairSuccessorReport.frozenSurface.unchanged -ne $true `
            -or $w3lRepairSuccessorReport.frozenSurface.observedSha256 -cne $w3lRepairSuccessorReport.frozenSurface.requiredSha256 `
            -or $w3lRepairSuccessorReport.frozenSurface.observedSha256 -cne $w3lFrozenSurfaceHash `
            -or $w3lRepairSuccessorReport.correction.previousCheckMetadataDigest -cne '525f785bcfc3e65823498cc1333331c6d48eb5f023803d360f1895b54463d22c' `
            -or $w3lRepairSuccessorReport.correction.observedCheckMetadataDigest -cne '8e8eea4f21f1a262acc9384015e3be5a73dfd29abb175fa9c4686dfffb33ead8' `
            -or @($w3lRepairSuccessorReport.mysql.versions).Count -ne 2 `
            -or @($w3lRepairSuccessorReport.mysql.versions | Where-Object { $_.version -ceq '8.0.30' }).Count -ne 1 `
            -or @($w3lRepairSuccessorReport.mysql.versions | Where-Object { $_.version -ceq '8.4.8' }).Count -ne 1 `
            -or @($w3lRepairSuccessorReport.mysql.versions | Where-Object {
                $_.firstApply -cne '1|1|4|8|4|16|0|0' `
                    -or $_.boundedReplay -cne '1|1|4|8|4|16|0|0' `
                    -or $_.canonicalCurrentRead -cne '1|1|4|8' `
                    -or $_.negativeMatrix.missingPublicReceipt -cne 'PASS:0|0|0|0|3|1' `
                    -or $_.negativeMatrix.partialTargetState -cne 'PASS:1|0|1|0|3|1' `
                    -or $_.negativeMatrix.postApplyMetadataDrift -cne 'PASS:1|1|4|8|3|1' `
                    -or $_.negativeMatrix.postApplyTriggerBodyDrift -cne 'PASS:1|1|4|8|3|1' `
                    -or $_.productionConnectionUsed -ne $false -or $_.workDirectoryCleaned -ne $true
            }).Count -ne 0) {
        throw 'W3l corrected dual-MySQL successor evidence is incomplete or drifted'
    }
    if ((Compare-Object -ReferenceObject ($w3lRepairRequiredArtifacts | Sort-Object) -DifferenceObject ($w3lRepairSourceArtifacts | Sort-Object)).Count -ne 0 `
            -or @($w3lRepairSuccessorReport.predecessorSourceSha256.PSObject.Properties).Count -ne $w3lRepairSupersededArtifacts.Count) {
        throw 'W3l corrected successor artifact or predecessor-source set drifted'
    }
    foreach ($w3lRepairArtifact in $w3lRepairRequiredArtifacts) {
        # Later successors bind the current verifier and application-aware
        # runner explicitly below. The historical W3l receipt retains its
        # original byte bindings.
        if ($w3lRepairArtifact -eq 'scripts/verify-independent-board-control-plane.ps1' `
                -or $w3lRepairArtifact -eq 'scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1' `
                -or $w3lRepairArtifact -eq 'sql/init-manifest.json' `
                -or $w3lRepairArtifact -eq 'scripts/init-database.ps1' `
                -or $w3lRepairArtifact -eq 'scripts/verify-database-manifest.ps1') {
            continue
        }

        $currentPath = Join-Path $RepoRoot $w3lRepairArtifact
        $currentHash = (Get-FileHash -LiteralPath $currentPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($w3lRepairSuccessorReport.sourceSha256.PSObject.Properties[$w3lRepairArtifact].Value -cne $currentHash) {
            throw "W3l corrected successor artifact binding drifted: $w3lRepairArtifact"
        }
    }
    foreach ($w3lRepairSupersededArtifact in $w3lRepairSupersededArtifacts) {
        $predecessorHash = $w3lCreditLedgerReport.sourceSha256.PSObject.Properties[$w3lRepairSupersededArtifact].Value
        $boundHash = $w3lRepairSuccessorReport.predecessorSourceSha256.PSObject.Properties[$w3lRepairSupersededArtifact].Value
        if ([string]::IsNullOrWhiteSpace($predecessorHash) -or $boundHash -cne $predecessorHash) {
            throw "W3l corrected successor predecessor binding drifted: $w3lRepairSupersededArtifact"
        }
    }
    if (@($w3lAuthorityOverlaps | Where-Object { $_ -notin $w3lAllowedAuthoritySupersession }).Count -ne 0 `
            -or $w3lAuthorityOverlaps.Count -ne $w3lAllowedAuthoritySupersession.Count `
            -or @($w3lCreditLedgerReport.predecessorSourceSha256.PSObject.Properties).Count -ne $w3lAllowedAuthoritySupersession.Count) {
        throw 'W3l successor attempts an unapproved W3k authority artifact supersession'
    }
    foreach ($w3lSupersededAuthorityArtifact in $w3lAllowedAuthoritySupersession) {
        if ($w3lSupersededAuthorityArtifact -notin $w3lAuthorityOverlaps) {
            throw "W3l successor is missing required W3k authority supersession binding: $w3lSupersededAuthorityArtifact"
        }
        $w3lPredecessorArtifactHash = $w3lCreditLedgerReport.predecessorSourceSha256.PSObject.Properties[$w3lSupersededAuthorityArtifact].Value
        $w3kAuthorityArtifactHash = $w3kAuthoritySuccessorReport.sourceSha256.PSObject.Properties[$w3lSupersededAuthorityArtifact].Value
        if ([string]::IsNullOrWhiteSpace($w3lPredecessorArtifactHash) -or $w3lPredecessorArtifactHash -cne $w3kAuthorityArtifactHash) {
            throw "W3l successor predecessor binding drifted: $w3lSupersededAuthorityArtifact"
        }
    }
    foreach ($w3lArtifact in @($w3lCreditLedgerReport.sourceSha256.PSObject.Properties)) {
        $w3lPath = [string]$w3lArtifact.Name
        if ($w3lPath -in $w3lRepairSupersededArtifacts) {
            continue
        }
        $w3lFile = Join-Path $RepoRoot $w3lPath
        if (-not (Test-Path -LiteralPath $w3lFile -PathType Leaf)) {
            throw "W3l migration successor artifact is missing: $w3lPath"
        }
        $w3lCurrentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $w3lFile).Hash.ToLowerInvariant()
        if ($w3lArtifact.Value -cne $w3lCurrentHash) {
            throw "W3l migration successor artifact binding drifted: $w3lPath"
        }
    }
    $w4b5dAuthoritySuccessorArtifacts = @(
        '.fbs-engineering/contract.json',
        'docs/independent-board/taskboard.json',
        'docs/independent-board/implementation-status.json'
    )
    foreach ($authorityArtifact in @($w3kAuthoritySuccessorReport.sourceSha256.PSObject.Properties)) {
        $authorityPath = [string]$authorityArtifact.Name
        if ($authorityPath -in $w3lAuthorityOverlaps `
                -or $authorityPath -in $w4b5dAuthoritySuccessorArtifacts) {
            continue
        }
        $authorityFile = Join-Path $RepoRoot $authorityPath
        if (-not (Test-Path -LiteralPath $authorityFile -PathType Leaf)) {
            throw "W3k controlled-authority successor artifact is missing: $authorityPath"
        }
        $authorityCurrentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $authorityFile).Hash.ToLowerInvariant()
        if ($authorityArtifact.Value -cne $authorityCurrentHash) {
            throw "W3k controlled-authority successor artifact binding drifted: $authorityPath"
        }
    }
    foreach ($supersededArtifact in @($w3kAuthorityReport.predecessorSourceSha256.PSObject.Properties | ForEach-Object { [string]$_.Name })) {
        $priorHash = $w3kMonotonicReport.sourceSha256.PSObject.Properties[$supersededArtifact].Value
        $successorPriorHash = $w3kAuthorityReport.predecessorSourceSha256.PSObject.Properties[$supersededArtifact].Value
        if ([string]::IsNullOrWhiteSpace($priorHash) -or $successorPriorHash -cne $priorHash) {
            throw "W3k controlled-authority predecessor binding drifted: $supersededArtifact"
        }
    }

    # W4B3 hardens the legacy points writer before the default-off v2 writer
    # can be introduced.  Its central-verifier byte is a constrained successor
    # to the W3l repair receipt, rather than a rewrite of that historical run.
    $pointsCasReportPath = 'reports/independent-board/points-balance-cas-mysql-verification-20260723.json'
    $pointsCasReport = Read-Utf8Json -RelativePath $pointsCasReportPath.Replace('/', '\')
    $pointsCasArtifacts = @(
        'FBSir-business/src/main/java/com/wx/fbsir/business/point/service/impl/PointsServiceImpl.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/point/service/PointsPrecheckService.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/point/mapper/PointsMapper.java',
        'FBSir-business/src/main/resources/mapper/point/PointsMapper.xml',
        'FBSir-business/src/test/java/com/wx/fbsir/business/point/service/impl/PointsServiceImplTest.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/point/service/PointsPrecheckServiceTest.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/point/mapper/PointsMapperSqlContractTest.java',
        'scripts/run-points-balance-cas-mysql-it.ps1',
        'docs/independent-board/W4B3-POINTS-BALANCE-CAS-CONTRACT.md',
        'scripts/verify-independent-board-control-plane.ps1'
    )
    $pointsCasSourceArtifacts = @($pointsCasReport.sourceSha256.PSObject.Properties | ForEach-Object { [string]$_.Name })
    $priorCentralVerifierHash = $w3lRepairSuccessorReport.sourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value
    if ($pointsCasReport.schemaVersion -ne 1 `
            -or $pointsCasReport.result -cne 'PASS_LOCAL_DUAL_MYSQL_CAS' `
            -or $pointsCasReport.candidateReadyForCommit -ne $true `
            -or $pointsCasReport.releaseReady -ne $false `
            -or $pointsCasReport.productionChanged -ne $false `
            -or $pointsCasReport.frozenListedPackageModified -ne $false `
            -or $pointsCasReport.productionConnectionUsed -ne $false `
            -or $pointsCasReport.serviceContract.stableConflictMessage -cne 'POINTS_BALANCE_CONCURRENT_CONFLICT' `
            -or $pointsCasReport.serviceContract.outOfRangeMessage -cne 'POINTS_BALANCE_OUT_OF_RANGE' `
            -or $pointsCasReport.serviceContract.conflictWritesLedger -ne $false `
            -or $pointsCasReport.serviceContract.conflictMarksLimit -ne $false `
            -or $pointsCasReport.serviceContract.eventIdempotentReplayPrecedesBalanceUpdate -ne $true `
            -or $pointsCasReport.serviceContract.precheckUsesCommittedBalance -ne $true `
            -or $pointsCasReport.maven.testsRun -ne 17 `
            -or $pointsCasReport.maven.failures -ne 0 `
            -or $pointsCasReport.maven.errors -ne 0 `
            -or $pointsCasReport.maven.result -cne 'BUILD_SUCCESS' `
            -or @($pointsCasReport.mysql.versions).Count -ne 2 `
            -or @($pointsCasReport.mysql.versions | Where-Object { $_.version -ceq '8.0.30' }).Count -ne 1 `
            -or @($pointsCasReport.mysql.versions | Where-Object { $_.version -ceq '8.4.8' }).Count -ne 1 `
            -or @($pointsCasReport.mysql.versions | Where-Object {
                $_.concurrentAffectedRows -cne '0|1' `
                    -or $_.finalBalance -notin @('90', '110') `
                    -or $_.nullNormalizedCompareAndSet -cne '1|7' `
                    -or $_.missingUserCompareAndSet -cne '0' `
                    -or $_.productionConnectionUsed -ne $false `
                    -or $_.workDirectoryCleaned -ne $true
            }).Count -ne 0 `
            -or (Compare-Object -ReferenceObject ($pointsCasArtifacts | Sort-Object) -DifferenceObject ($pointsCasSourceArtifacts | Sort-Object)).Count -ne 0 `
            -or @($pointsCasReport.predecessorSourceSha256.PSObject.Properties).Count -ne 1 `
            -or $pointsCasReport.predecessorSourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value -cne $priorCentralVerifierHash) {
        throw 'W4B3 points balance CAS verification receipt is incomplete, overclaims evidence, or lacks verifier succession'
    }
    foreach ($pointsCasArtifact in $pointsCasArtifacts) {
        # W4B4 is the only successor permitted to advance this central verifier
        # byte.  Keep the W4B3 receipt historically exact and verify that
        # predecessor relationship explicitly below.
        if ($pointsCasArtifact -eq 'scripts/verify-independent-board-control-plane.ps1') {
            continue
        }

        $pointsCasCurrentHash = (Get-FileHash -LiteralPath (Join-Path $RepoRoot $pointsCasArtifact) -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($pointsCasReport.sourceSha256.PSObject.Properties[$pointsCasArtifact].Value -cne $pointsCasCurrentHash) {
            throw "W4B3 points balance CAS artifact binding drifted: $pointsCasArtifact"
        }
    }

    # W4B4 closes the accidental-activation gap before an actual v2 consume
    # transaction writer exists.  It is a default-off fence, not a claim that
    # the 042 candidate is deployable or authoritative.
    $skillConsumeFenceReportPath = 'reports/independent-board/skill-consume-candidate-fence-verification-20260723.json'
    $skillConsumeFenceReport = Read-Utf8Json -RelativePath $skillConsumeFenceReportPath.Replace('/', '\')
    $skillConsumeFenceArtifacts = @(
        'FBSir-admin/src/main/resources/application.yml',
        'FBSir-business/src/main/java/com/wx/fbsir/business/fbs/service/impl/SkillConsumeServiceImpl.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/fbs/service/SkillConsumeServiceTest.java',
        'docs/independent-board/W4B4-SKILL-CONSUME-CANDIDATE-FENCE-CONTRACT.md',
        'scripts/verify-independent-board-control-plane.ps1'
    )
    $skillConsumeFenceSourceArtifacts = @($skillConsumeFenceReport.sourceSha256.PSObject.Properties | ForEach-Object { [string]$_.Name })
    $w4b4PriorCentralVerifierHash = $pointsCasReport.sourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value
    if ($skillConsumeFenceReport.schemaVersion -ne 1 `
            -or $skillConsumeFenceReport.result -cne 'PASS_LOCAL_DEFAULT_OFF_SKILL_CONSUME_FENCE' `
            -or $skillConsumeFenceReport.candidateReadyForCommit -ne $true `
            -or $skillConsumeFenceReport.releaseReady -ne $false `
            -or $skillConsumeFenceReport.productionChanged -ne $false `
            -or $skillConsumeFenceReport.databaseTouched -ne $false `
            -or $skillConsumeFenceReport.frozenListedPackageModified -ne $false `
            -or $skillConsumeFenceReport.productionConnectionUsed -ne $false `
            -or $skillConsumeFenceReport.serviceContract.requiresBothFlags -ne $true `
            -or $skillConsumeFenceReport.serviceContract.pairError -cne 'SKILL_CONSUME_CREDIT_WRITER_NOT_READY' `
            -or $skillConsumeFenceReport.serviceContract.pairRejectsBeforeUsageOrLegacyPointsWrite -ne $true `
            -or $skillConsumeFenceReport.serviceContract.singleFlagRetainsLegacyPath -ne $true `
            -or $skillConsumeFenceReport.serviceContract.freeAndEnterpriseNotGated -ne $true `
            -or $skillConsumeFenceReport.serviceContract.addsHttpOrMcpWriteSurface -ne $false `
            -or $skillConsumeFenceReport.maven.testsRun -ne 35 `
            -or $skillConsumeFenceReport.maven.failures -ne 0 `
            -or $skillConsumeFenceReport.maven.errors -ne 0 `
            -or $skillConsumeFenceReport.maven.result -cne 'BUILD_SUCCESS' `
            -or $skillConsumeFenceReport.frozenPackage.sha256 -cne 'd2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd' `
            -or (Compare-Object -ReferenceObject ($skillConsumeFenceArtifacts | Sort-Object) -DifferenceObject ($skillConsumeFenceSourceArtifacts | Sort-Object)).Count -ne 0 `
            -or @($skillConsumeFenceReport.predecessorSourceSha256.PSObject.Properties).Count -ne 1 `
            -or $skillConsumeFenceReport.predecessorSourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value -cne $w4b4PriorCentralVerifierHash) {
        throw 'W4B4 skill-consume default-off fence receipt is incomplete, overclaims evidence, or lacks verifier succession'
    }
    foreach ($skillConsumeFenceArtifact in $skillConsumeFenceArtifacts) {
        # W4B5A succeeds this central-verifier byte while retaining the W4B4
        # receipt as an immutable historical fact.
        if ($skillConsumeFenceArtifact -in @(
                'FBSir-business/src/main/java/com/wx/fbsir/business/fbs/service/impl/SkillConsumeServiceImpl.java',
                'FBSir-business/src/test/java/com/wx/fbsir/business/fbs/service/SkillConsumeServiceTest.java',
                'scripts/verify-independent-board-control-plane.ps1')) {
            continue
        }

        $skillConsumeFenceCurrentHash = (Get-FileHash -LiteralPath (Join-Path $RepoRoot $skillConsumeFenceArtifact) -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($skillConsumeFenceReport.sourceSha256.PSObject.Properties[$skillConsumeFenceArtifact].Value -cne $skillConsumeFenceCurrentHash) {
            throw "W4B4 skill-consume fence artifact binding drifted: $skillConsumeFenceArtifact"
        }
    }

    # W4B5A binds the pure internal command contract before any mapper or
    # transaction writer can be introduced.  It deliberately proves no write
    # capability and cannot open the W4B4 runtime fence.
    $skillConsumeCommandReportPath = 'reports/independent-board/skill-consume-command-verification-20260723.json'
    $skillConsumeCommandReport = Read-Utf8Json -RelativePath $skillConsumeCommandReportPath.Replace('/', '\')
    $skillConsumeCommandArtifacts = @(
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommand.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommandTest.java',
        'docs/independent-board/W4B5A-SKILL-CONSUME-COMMAND-CONTRACT.md',
        'scripts/verify-independent-board-control-plane.ps1'
    )
    $skillConsumeCommandSourceArtifacts = @($skillConsumeCommandReport.sourceSha256.PSObject.Properties | ForEach-Object { [string]$_.Name })
    $w4b5aPriorCentralVerifierHash = $skillConsumeFenceReport.sourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value
    if ($skillConsumeCommandReport.schemaVersion -ne 1 `
            -or $skillConsumeCommandReport.result -cne 'PASS_LOCAL_SKILL_CONSUME_COMMAND_CONTRACT' `
            -or $skillConsumeCommandReport.candidateReadyForCommit -ne $true `
            -or $skillConsumeCommandReport.releaseReady -ne $false `
            -or $skillConsumeCommandReport.productionChanged -ne $false `
            -or $skillConsumeCommandReport.databaseTouched -ne $false `
            -or $skillConsumeCommandReport.frozenListedPackageModified -ne $false `
            -or $skillConsumeCommandReport.productionConnectionUsed -ne $false `
            -or $skillConsumeCommandReport.commandContract.protocolVersion -cne 'skill-consume-credit-v1' `
            -or $skillConsumeCommandReport.commandContract.writerAttached -ne $false `
            -or $skillConsumeCommandReport.commandContract.databaseSideEffects -ne $false `
            -or $skillConsumeCommandReport.commandContract.retainsRawHostSession -ne $false `
            -or $skillConsumeCommandReport.commandContract.enterpriseInputRejected -ne $true `
            -or $skillConsumeCommandReport.commandContract.strictPersonalHostTypeAllowlist -ne $true `
            -or $skillConsumeCommandReport.commandContract.rejectsInvalidUtf16BeforeDigest -ne $true `
            -or $skillConsumeCommandReport.commandContract.requestDigestBindsAllCommandFields -ne $true `
            -or $skillConsumeCommandReport.commandContract.sameUsageDifferentDigestSharesReplayAnchor -ne $true `
            -or $skillConsumeCommandReport.maven.testsRun -ne 2 `
            -or $skillConsumeCommandReport.maven.failures -ne 0 `
            -or $skillConsumeCommandReport.maven.errors -ne 0 `
            -or $skillConsumeCommandReport.maven.result -cne 'BUILD_SUCCESS' `
            -or $skillConsumeCommandReport.frozenPackage.sha256 -cne 'd2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd' `
            -or (Compare-Object -ReferenceObject ($skillConsumeCommandArtifacts | Sort-Object) -DifferenceObject ($skillConsumeCommandSourceArtifacts | Sort-Object)).Count -ne 0 `
            -or @($skillConsumeCommandReport.predecessorSourceSha256.PSObject.Properties).Count -ne 1 `
            -or $skillConsumeCommandReport.predecessorSourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value -cne $w4b5aPriorCentralVerifierHash) {
        throw 'W4B5A skill-consume command receipt is incomplete, overclaims evidence, or lacks verifier succession'
    }
    foreach ($skillConsumeCommandArtifact in $skillConsumeCommandArtifacts) {
        # W4B5B succeeds every W4B5A command artifact after it narrowed the
        # legacy-usage compatibility boundary and attached the internal writer.
        # The W4B5A receipt remains immutable historical evidence; W4B5B binds
        # the current bytes below.
        continue

        $skillConsumeCommandCurrentHash = (Get-FileHash -LiteralPath (Join-Path $RepoRoot $skillConsumeCommandArtifact) -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($skillConsumeCommandReport.sourceSha256.PSObject.Properties[$skillConsumeCommandArtifact].Value -cne $skillConsumeCommandCurrentHash) {
            throw "W4B5A skill-consume command artifact binding drifted: $skillConsumeCommandArtifact"
        }
    }

    # W4B5B adds an internal-only 042 writer and REQUIRES_NEW transaction
    # boundary. It remains unreachable from the legacy consume service until
    # separate real-MySQL and 038-mutual-exclusion evidence is accepted.
    $skillConsumeWriterReportPath = 'reports/independent-board/skill-consume-writer-verification-20260723.json'
    $skillConsumeWriterReport = Read-Utf8Json -RelativePath $skillConsumeWriterReportPath.Replace('/', '\')
    $skillConsumeActivationReportPath = 'reports/independent-board/skill-consume-activation-safety-verification-20260723.json'
    $skillConsumeActivationReport = Read-Utf8Json -RelativePath $skillConsumeActivationReportPath.Replace('/', '\')
    $skillConsumeWriterArtifacts = @(
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/domain/SkillCreditOperation.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/domain/SkillCreditProjectionBridge.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/mapper/SkillConsumeCreditLedgerMapper.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommand.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditDigest.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditTransactionService.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriter.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/fbs/mapper/FbsSkillUsageRecordMapper.java',
        'FBSir-business/src/main/resources/mapper/board/SkillConsumeCreditLedgerMapper.xml',
        'FBSir-business/src/main/resources/mapper/fbs/FbsSkillUsageRecordMapper.xml',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/SkillConsumeCreditLedgerMapperContractTest.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommandTest.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditTransactionServiceTest.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriterTest.java',
        'docs/independent-board/W4B5A-SKILL-CONSUME-COMMAND-CONTRACT.md',
        'docs/independent-board/W4B5B-SKILL-CONSUME-TRANSACTION-WRITER-CONTRACT.md',
        'scripts/verify-independent-board-control-plane.ps1'
    )
    $skillConsumeWriterSourceArtifacts = @($skillConsumeWriterReport.sourceSha256.PSObject.Properties | ForEach-Object { [string]$_.Name })
    $w4b5bPriorCentralVerifierHash = $skillConsumeCommandReport.sourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value
    if ($skillConsumeWriterReport.schemaVersion -ne 1 `
            -or $skillConsumeWriterReport.result -cne 'PASS_LOCAL_SKILL_CONSUME_WRITER_CONTRACT' `
            -or $skillConsumeWriterReport.candidateReadyForCommit -ne $true `
            -or $skillConsumeWriterReport.releaseReady -ne $false `
            -or $skillConsumeWriterReport.productionChanged -ne $false `
            -or $skillConsumeWriterReport.databaseTouched -ne $false `
            -or $skillConsumeWriterReport.frozenListedPackageModified -ne $false `
            -or $skillConsumeWriterReport.productionConnectionUsed -ne $false `
            -or $skillConsumeWriterReport.writerContract.writerPresent -ne $true `
            -or $skillConsumeWriterReport.writerContract.wiredIntoLegacyConsume -ne $false `
            -or $skillConsumeWriterReport.writerContract.newHttpOrMcpSurface -ne $false `
            -or $skillConsumeWriterReport.writerContract.freshTransactionPropagation -cne 'REQUIRES_NEW' `
            -or $skillConsumeWriterReport.writerContract.freshTransactionIsolation -cne 'REPEATABLE_READ' `
            -or $skillConsumeWriterReport.writerContract.replayUsesCommittedWinner -ne $true `
            -or $skillConsumeWriterReport.writerContract.legacyUsageColumnCompatible -ne $true `
            -or $skillConsumeWriterReport.writerContract.usesLegacyPointsWriter -ne $false `
            -or $skillConsumeWriterReport.writerContract.realMysqlMatrixVerified -ne $false `
            -or $skillConsumeWriterReport.writerContract.requires038MutualExclusionBeforeEnablement -ne $true `
            -or $skillConsumeWriterReport.maven.testsRun -ne 11 `
            -or $skillConsumeWriterReport.maven.failures -ne 0 `
            -or $skillConsumeWriterReport.maven.errors -ne 0 `
            -or $skillConsumeWriterReport.maven.result -cne 'BUILD_SUCCESS' `
            -or $skillConsumeWriterReport.frozenPackage.sha256 -cne 'd2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd' `
            -or (Compare-Object -ReferenceObject ($skillConsumeWriterArtifacts | Sort-Object) -DifferenceObject ($skillConsumeWriterSourceArtifacts | Sort-Object)).Count -ne 0 `
            -or @($skillConsumeWriterReport.predecessorSourceSha256.PSObject.Properties).Count -ne 1 `
            -or $skillConsumeWriterReport.predecessorSourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value -cne $w4b5bPriorCentralVerifierHash) {
        throw 'W4B5B skill-consume writer receipt is incomplete, overclaims evidence, or lacks verifier succession'
    }
    foreach ($skillConsumeWriterArtifact in $skillConsumeWriterArtifacts) {
        # W4B5C succeeds the central-verifier byte while retaining W4B5B as
        # immutable historical evidence.
        if ($skillConsumeWriterArtifact -in @(
                'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommand.java',
                'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditTransactionService.java',
                'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriter.java',
                'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommandTest.java',
                'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriterTest.java',
                'scripts/verify-independent-board-control-plane.ps1')) {
            continue
        }

        $skillConsumeWriterCurrentHash = (Get-FileHash -LiteralPath (Join-Path $RepoRoot $skillConsumeWriterArtifact) -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($skillConsumeWriterReport.sourceSha256.PSObject.Properties[$skillConsumeWriterArtifact].Value -cne $skillConsumeWriterCurrentHash) {
            throw "W4B5B skill-consume writer artifact binding drifted: $skillConsumeWriterArtifact"
        }
    }

    # W4B5C succeeds the W3l migration-only runner with a real Spring/MyBatis
    # application transaction matrix, and adds the reciprocal 038 authority
    # fence. It remains default-off and explicitly does not authorize release.
    $skillConsumeDualMysqlReportPath = 'reports/independent-board/skill-consume-dual-mysql-transaction-verification-20260723.json'
    $skillConsumeDualMysqlReport = Read-Utf8Json -RelativePath $skillConsumeDualMysqlReportPath.Replace('/', '\')
    $skillConsumeDualMysqlArtifacts = @(
        'sql/update_20260723_skill_consume_credit_ledger_v2.sql',
        'sql/update_20260722_independent_board_credit_ledger.sql',
        'scripts/init-database.ps1',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriter.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditTransactionService.java',
        'FBSir-business/src/main/resources/mapper/board/SkillConsumeCreditLedgerMapper.xml',
        'FBSir-business/src/main/resources/mapper/fbs/FbsSkillUsageRecordMapper.xml',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/IndependentBoardCreditService.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/SkillConsumeCreditLedgerV2RunnerContractTest.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditLedgerV2MysqlIT.java',
        'docs/independent-board/W4B5C-SKILL-CONSUME-DUAL-MYSQL-TRANSACTION-CONTRACT.md',
        'scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1',
        'scripts/verify-independent-board-control-plane.ps1'
    )
    $skillConsumeDualMysqlSourceArtifacts = @($skillConsumeDualMysqlReport.sourceSha256.PSObject.Properties | ForEach-Object { [string]$_.Name })
    $w4b5cPriorCentralVerifierHash = $skillConsumeWriterReport.sourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value
    $w4b5cPriorRunnerHash = $w3lRepairSuccessorReport.sourceSha256.PSObject.Properties['scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1'].Value
    $w4b5cWriterReceiptHash = (Get-FileHash -LiteralPath (Join-Path $RepoRoot $skillConsumeWriterReportPath) -Algorithm SHA256).Hash.ToLowerInvariant()
    $w4b5cMigrationReceiptHash = (Get-FileHash -LiteralPath (Join-Path $RepoRoot $w3lRepairSuccessorPath) -Algorithm SHA256).Hash.ToLowerInvariant()
    $skillConsumeDualMysqlVersions = @($skillConsumeDualMysqlReport.versions)
    if ($skillConsumeDualMysqlReport.schemaVersion -ne 1 `
            -or $skillConsumeDualMysqlReport.result -cne 'PASS_LOCAL_DUAL_MYSQL_APPLICATION_TRANSACTION_MATRIX' `
            -or $skillConsumeDualMysqlReport.candidateReadyForCommit -ne $true `
            -or $skillConsumeDualMysqlReport.releaseReady -ne $false `
            -or $skillConsumeDualMysqlReport.productionChanged -ne $false `
            -or $skillConsumeDualMysqlReport.productionConnectionUsed -ne $false `
            -or $skillConsumeDualMysqlReport.frozenListedPackageModified -ne $false `
            -or $skillConsumeDualMysqlReport.writerWiredIntoLegacyConsume -ne $false `
            -or $skillConsumeDualMysqlReport.historicalReceiptMutated -ne $false `
            -or $skillConsumeDualMysqlReport.frozenPackage.sha256 -cne 'd2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd' `
            -or $skillConsumeDualMysqlReport.matrixContract.springTransactionProxyVerified -ne $true `
            -or $skillConsumeDualMysqlReport.matrixContract.realConnectorJAndMyBatisVerified -ne $true `
            -or $skillConsumeDualMysqlReport.matrixContract.firstConsumeAndExactReplay -ne $true `
            -or $skillConsumeDualMysqlReport.matrixContract.sameCommandConcurrency -ne 32 `
            -or $skillConsumeDualMysqlReport.matrixContract.digestConflictRejected -ne $true `
            -or $skillConsumeDualMysqlReport.matrixContract.balanceRaceSingleWinner -ne $true `
            -or $skillConsumeDualMysqlReport.matrixContract.terminalUsageCasRollback -ne $true `
            -or $skillConsumeDualMysqlReport.matrixContract.legacyBlocksV2 -ne $true `
            -or $skillConsumeDualMysqlReport.matrixContract.v2BlocksLegacyGrantReverseAudit -ne $true `
            -or $skillConsumeDualMysqlReport.targetedRegression.testsRun -ne 36 `
            -or $skillConsumeDualMysqlReport.targetedRegression.failures -ne 0 `
            -or $skillConsumeDualMysqlReport.targetedRegression.errors -ne 0 `
            -or $skillConsumeDualMysqlReport.targetedRegression.skipped -ne 0 `
            -or $skillConsumeDualMysqlReport.targetedRegression.result -cne 'BUILD_SUCCESS' `
            -or $skillConsumeDualMysqlVersions.Count -ne 2 `
            -or @($skillConsumeDualMysqlVersions | Where-Object { $_.version -ceq '8.0.30' }).Count -ne 1 `
            -or @($skillConsumeDualMysqlVersions | Where-Object { $_.version -ceq '8.4.8' }).Count -ne 1 `
            -or @($skillConsumeDualMysqlVersions | Where-Object {
                $_.testsRun -ne 4 -or $_.passed -ne 4 -or $_.failures -ne 0 `
                    -or $_.errors -ne 0 -or $_.skipped -ne 0 `
                    -or $_.surefireReportSha256 -notmatch '^[0-9a-f]{64}$' `
                    -or $_.productionConnectionUsed -ne $false `
                    -or $_.workDirectoryCleaned -ne $true
            }).Count -ne 0 `
            -or $skillConsumeDualMysqlReport.predecessorReceipts.writer.path -cne $skillConsumeWriterReportPath `
            -or $skillConsumeDualMysqlReport.predecessorReceipts.writer.sha256 -cne $w4b5cWriterReceiptHash `
            -or $skillConsumeDualMysqlReport.predecessorReceipts.migrationRunner.path -cne $w3lRepairSuccessorPath `
            -or $skillConsumeDualMysqlReport.predecessorReceipts.migrationRunner.sha256 -cne $w4b5cMigrationReceiptHash `
            -or @($skillConsumeDualMysqlReport.predecessorSourceSha256.PSObject.Properties).Count -ne 2 `
            -or $skillConsumeDualMysqlReport.predecessorSourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value -cne $w4b5cPriorCentralVerifierHash `
            -or $skillConsumeDualMysqlReport.predecessorSourceSha256.PSObject.Properties['scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1'].Value -cne $w4b5cPriorRunnerHash `
            -or (Compare-Object -ReferenceObject ($skillConsumeDualMysqlArtifacts | Sort-Object) -DifferenceObject ($skillConsumeDualMysqlSourceArtifacts | Sort-Object)).Count -ne 0) {
        throw 'W4B5C dual-MySQL application transaction receipt is incomplete, overclaims evidence, or lacks predecessor succession'
    }
    foreach ($skillConsumeDualMysqlArtifact in $skillConsumeDualMysqlArtifacts) {
        if ($skillConsumeDualMysqlArtifact -in @(
                'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriter.java',
                'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditTransactionService.java',
                'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/SkillConsumeCreditLedgerV2RunnerContractTest.java',
                'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditLedgerV2MysqlIT.java',
                'scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1',
                'scripts/init-database.ps1',
                'scripts/verify-independent-board-control-plane.ps1')) {
            continue
        }
        $skillConsumeDualMysqlCurrentHash = (Get-FileHash -LiteralPath (Join-Path $RepoRoot $skillConsumeDualMysqlArtifact) -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($skillConsumeDualMysqlReport.sourceSha256.PSObject.Properties[$skillConsumeDualMysqlArtifact].Value -cne $skillConsumeDualMysqlCurrentHash) {
            throw "W4B5C dual-MySQL application transaction artifact binding drifted: $skillConsumeDualMysqlArtifact"
        }
    }

    # W4B5D is the immutable successor that wires the already-proven writer
    # into the host consume service behind the existing default-off AND gate.
    # It owns the changed host/runner/truth-source bytes without rewriting any
    # W4B4 or W4B5C historical receipt and still does not authorize release.
    $skillConsumeHostReportPath = 'reports/independent-board/skill-consume-host-wiring-verification-20260723.json'
    $skillConsumeHostReport = Read-Utf8Json -RelativePath $skillConsumeHostReportPath.Replace('/', '\')
    $skillConsumeHostArtifacts = @(
        '.gitattributes',
        '.fbs-engineering/contract.json',
        'FBSir-admin/src/main/resources/application.yml',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriter.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/fbs/service/impl/SkillConsumeServiceImpl.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/SkillConsumeCreditLedgerV2RunnerContractTest.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditLedgerV2MysqlIT.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/fbs/service/SkillConsumeServiceTest.java',
        'docs/independent-board/W4B5D-SKILL-CONSUME-HOST-WIRING-CONTRACT.md',
        'docs/independent-board/implementation-status.json',
        'docs/independent-board/taskboard.json',
        'scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1',
        'scripts/verify-independent-board-control-plane.ps1'
    )
    $skillConsumeHostSourceArtifacts = @(
        $skillConsumeHostReport.sourceSha256.PSObject.Properties |
            ForEach-Object { [string]$_.Name })
    $skillConsumeHostPredecessorSourceArtifacts = @(
        $skillConsumeHostReport.predecessorSourceSha256.PSObject.Properties |
            ForEach-Object { [string]$_.Name })
    $skillConsumeHostExpectedPredecessorSources = [ordered]@{
        'FBSir-business/src/main/java/com/wx/fbsir/business/fbs/service/impl/SkillConsumeServiceImpl.java' =
            $skillConsumeFenceReport.sourceSha256.PSObject.Properties['FBSir-business/src/main/java/com/wx/fbsir/business/fbs/service/impl/SkillConsumeServiceImpl.java'].Value
        'FBSir-business/src/test/java/com/wx/fbsir/business/fbs/service/SkillConsumeServiceTest.java' =
            $skillConsumeFenceReport.sourceSha256.PSObject.Properties['FBSir-business/src/test/java/com/wx/fbsir/business/fbs/service/SkillConsumeServiceTest.java'].Value
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriter.java' =
            $skillConsumeDualMysqlReport.sourceSha256.PSObject.Properties['FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriter.java'].Value
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/SkillConsumeCreditLedgerV2RunnerContractTest.java' =
            $skillConsumeDualMysqlReport.sourceSha256.PSObject.Properties['FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/SkillConsumeCreditLedgerV2RunnerContractTest.java'].Value
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditLedgerV2MysqlIT.java' =
            $skillConsumeDualMysqlReport.sourceSha256.PSObject.Properties['FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditLedgerV2MysqlIT.java'].Value
        'scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1' =
            $skillConsumeDualMysqlReport.sourceSha256.PSObject.Properties['scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1'].Value
        'scripts/verify-independent-board-control-plane.ps1' =
            $skillConsumeDualMysqlReport.sourceSha256.PSObject.Properties['scripts/verify-independent-board-control-plane.ps1'].Value
        '.fbs-engineering/contract.json' =
            $w3kAuthoritySuccessorReport.sourceSha256.PSObject.Properties['.fbs-engineering/contract.json'].Value
        'docs/independent-board/taskboard.json' =
            $w3kAuthoritySuccessorReport.sourceSha256.PSObject.Properties['docs/independent-board/taskboard.json'].Value
        'docs/independent-board/implementation-status.json' =
            $w3kAuthoritySuccessorReport.sourceSha256.PSObject.Properties['docs/independent-board/implementation-status.json'].Value
    }
    $skillConsumeHostExpectedPredecessorPaths = @(
        $skillConsumeHostExpectedPredecessorSources.Keys)
    $skillConsumeHostExpectedTestNames = @(
        'concurrencyDigestConflictAndBalanceRaceCommitOnlyValidWinners',
        'dualFlagHostConsumeUsesV2AndNeverCallsLegacyWriters',
        'firstConsumeAndExactReplayCommitOneImmutableResult',
        'legacyAndV2AuthorityAreMutuallyExclusiveInBothDirections',
        'terminalUsageCasZeroRollsBackEveryFinancialAndReceiptWrite'
    )
    $skillConsumeFenceReceiptHash = (Get-FileHash -LiteralPath (
        Join-Path $RepoRoot $skillConsumeFenceReportPath) -Algorithm SHA256).Hash.ToLowerInvariant()
    $skillConsumeDualMysqlReceiptHash = (Get-FileHash -LiteralPath (
        Join-Path $RepoRoot $skillConsumeDualMysqlReportPath) -Algorithm SHA256).Hash.ToLowerInvariant()
    $w3kAuthoritySuccessorReceiptHash = (Get-FileHash -LiteralPath (
        Join-Path $RepoRoot 'reports/independent-board/w3k-plan-policy-authority-mysql-verification-20260723.json'
    ) -Algorithm SHA256).Hash.ToLowerInvariant()
    $skillConsumeHostVersions = @($skillConsumeHostReport.versions)
    $skillConsumeHostApplicationConfig = Get-Content -LiteralPath (
        Join-Path $RepoRoot 'FBSir-admin/src/main/resources/application.yml') -Raw
    if ($skillConsumeHostReport.schemaVersion -ne 1 `
            -or $skillConsumeHostReport.result -cne 'PASS_LOCAL_DEFAULT_OFF_SKILL_CONSUME_HOST_WIRING' `
            -or $skillConsumeHostReport.candidateReadyForCommit -ne $true `
            -or $skillConsumeHostReport.releaseReady -ne $false `
            -or $skillConsumeHostReport.productionChanged -ne $false `
            -or $skillConsumeHostReport.productionConnectionUsed -ne $false `
            -or $skillConsumeHostReport.frozenListedPackageModified -ne $false `
            -or $skillConsumeHostReport.historicalReceiptMutated -ne $false `
            -or $skillConsumeHostReport.writerWiredIntoLegacyConsume -ne $true `
            -or $skillConsumeHostReport.hostContract.writerEnabledByDefault -ne $false `
            -or $skillConsumeHostReport.hostContract.creditLedgerCandidateEnabledByDefault -ne $false `
            -or $skillConsumeHostReport.hostContract.requiresBothFlags -ne $true `
            -or $skillConsumeHostReport.hostContract.writerBeanMissingFailsClosed -ne $true `
            -or $skillConsumeHostReport.hostContract.paidPersonalDelegatesExactlyOnce -ne $true `
            -or $skillConsumeHostReport.hostContract.writerFailureFallsBackToLegacy -ne $false `
            -or $skillConsumeHostReport.hostContract.hostTypeTrimmedAndUppercased -ne $true `
            -or $skillConsumeHostReport.hostContract.minimumIntegerRuleFailsClosed -ne $true `
            -or $skillConsumeHostReport.hostContract.hostLegacyUsageCallsOnV2 -ne 0 `
            -or $skillConsumeHostReport.hostContract.legacyPointsCallsOnV2 -ne 0 `
            -or $skillConsumeHostReport.hostContract.commercialHubSyncCallsOnV2 -ne 0 `
            -or $skillConsumeHostReport.hostContract.singleFlagRetainsLegacyPath -ne $true `
            -or $skillConsumeHostReport.hostContract.freeAndEnterpriseRemainLegacy -ne $true `
            -or $skillConsumeHostReport.hostContract.addsHttpOrMcpWriteSurface -ne $false `
            -or $skillConsumeHostApplicationConfig -notmatch [regex]::Escape(
                'FBSIR_INDEPENDENT_BOARD_CREDIT_LEDGER_CANDIDATE_ENABLED:false') `
            -or $skillConsumeHostApplicationConfig -notmatch [regex]::Escape(
                'FBSIR_INDEPENDENT_BOARD_SKILL_CONSUME_CREDIT_WRITER_ENABLED:false') `
            -or $skillConsumeHostReport.remainingReleaseBlockers.outerTransactionPoolStarvationRiskOpen -ne $true `
            -or $skillConsumeHostReport.remainingReleaseBlockers.hostSessionCompatibilityOpen -ne $true `
            -or $skillConsumeHostReport.remainingReleaseBlockers.commercialHubOutboxOpen -ne $true `
            -or $skillConsumeHostReport.remainingReleaseBlockers.exactTargetActivationReceiptOpen -ne $true `
            -or $skillConsumeHostReport.targetedRegression.testsRun -ne 76 `
            -or $skillConsumeHostReport.targetedRegression.failures -ne 0 `
            -or $skillConsumeHostReport.targetedRegression.errors -ne 0 `
            -or $skillConsumeHostReport.targetedRegression.skipped -ne 0 `
            -or $skillConsumeHostReport.targetedRegression.result -cne 'BUILD_SUCCESS' `
            -or $skillConsumeHostVersions.Count -ne 2 `
            -or @($skillConsumeHostVersions | Where-Object { $_.version -ceq '8.0.30' }).Count -ne 1 `
            -or @($skillConsumeHostVersions | Where-Object { $_.version -ceq '8.4.8' }).Count -ne 1 `
            -or @($skillConsumeHostVersions | Where-Object {
                $_.testsRun -ne 5 -or $_.passed -ne 5 -or $_.failures -ne 0 `
                    -or $_.errors -ne 0 -or $_.skipped -ne 0 `
                    -or (Compare-Object -ReferenceObject ($skillConsumeHostExpectedTestNames | Sort-Object) `
                        -DifferenceObject (@($_.testNames) | Sort-Object)).Count -ne 0 `
                    -or $_.surefireReportSha256 -notmatch '^[0-9a-f]{64}$' `
                    -or $_.productionConnectionUsed -ne $false `
                    -or $_.workDirectoryCleaned -ne $true
            }).Count -ne 0 `
            -or $skillConsumeHostReport.predecessorReceipts.fence.path -cne $skillConsumeFenceReportPath `
            -or $skillConsumeHostReport.predecessorReceipts.fence.sha256 -cne $skillConsumeFenceReceiptHash `
            -or $skillConsumeHostReport.predecessorReceipts.dualMysql.path -cne $skillConsumeDualMysqlReportPath `
            -or $skillConsumeHostReport.predecessorReceipts.dualMysql.sha256 -cne $skillConsumeDualMysqlReceiptHash `
            -or $skillConsumeHostReport.predecessorReceipts.truthSources.path -cne 'reports/independent-board/w3k-plan-policy-authority-mysql-verification-20260723.json' `
            -or $skillConsumeHostReport.predecessorReceipts.truthSources.sha256 -cne $w3kAuthoritySuccessorReceiptHash `
            -or (Compare-Object -ReferenceObject ($skillConsumeHostExpectedPredecessorPaths | Sort-Object) `
                -DifferenceObject ($skillConsumeHostPredecessorSourceArtifacts | Sort-Object)).Count -ne 0 `
            -or (Compare-Object -ReferenceObject ($skillConsumeHostArtifacts | Sort-Object) `
                -DifferenceObject ($skillConsumeHostSourceArtifacts | Sort-Object)).Count -ne 0 `
            -or $skillConsumeHostReport.frozenPackage.sha256 -cne 'd2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd') {
        throw 'W4B5D host-wiring receipt is incomplete, overclaims evidence, or lacks predecessor succession'
    }
    foreach ($skillConsumeHostPredecessorPath in $skillConsumeHostExpectedPredecessorPaths) {
        if ($skillConsumeHostReport.predecessorSourceSha256.PSObject.Properties[
                $skillConsumeHostPredecessorPath].Value -cne
                $skillConsumeHostExpectedPredecessorSources[$skillConsumeHostPredecessorPath]) {
            throw "W4B5D predecessor source binding drifted: $skillConsumeHostPredecessorPath"
        }
    }
    foreach ($skillConsumeHostArtifact in $skillConsumeHostArtifacts) {
        $skillConsumeHostCurrentHash = $skillConsumeActivationReport.predecessorSourceSha256.PSObject.Properties[
            $skillConsumeHostArtifact].Value
        if ($skillConsumeHostReport.sourceSha256.PSObject.Properties[
                $skillConsumeHostArtifact].Value -cne $skillConsumeHostCurrentHash) {
            throw "W4B5D host-wiring predecessor binding drifted: $skillConsumeHostArtifact"
        }
    }

    # W4B5E succeeds W4B5D without rewriting it. It closes the nested
    # connection and nullable-session compatibility risks, but deliberately
    # keeps Commercial Hub and exact-target activation closed.
    $skillConsumeActivationArtifacts = @(
        '.fbs-engineering/contract.json',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommand.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditTransactionService.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriter.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/fbs/service/impl/SkillConsumeLegacyTransactionExecutor.java',
        'FBSir-business/src/main/java/com/wx/fbsir/business/fbs/service/impl/SkillConsumeServiceImpl.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/SkillConsumeCreditLedgerV2RunnerContractTest.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommandTest.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditLedgerV2MysqlIT.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriterTest.java',
        'FBSir-business/src/test/java/com/wx/fbsir/business/fbs/service/SkillConsumeServiceTest.java',
        'docs/independent-board/W4B5E-SKILL-CONSUME-ACTIVATION-SAFETY-CONTRACT.md',
        'docs/independent-board/implementation-status.json',
        'docs/independent-board/taskboard.json',
        'scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1',
        'scripts/verify-independent-board-control-plane.ps1'
    )
    $skillConsumeActivationExpectedTestNames = @(
        'ambientCallerTransactionFailsClosedBeforeTheV2Writer',
        'concurrencyDigestConflictAndBalanceRaceCommitOnlyValidWinners',
        'dualFlagHostConsumeUsesV2AndNeverCallsLegacyWriters',
        'firstConsumeAndExactReplayCommitOneImmutableResult',
        'legacyAndV2AuthorityAreMutuallyExclusiveInBothDirections',
        'terminalUsageCasZeroRollsBackEveryFinancialAndReceiptWrite'
    )
    $skillConsumeActivationReceiptHash = (Get-FileHash -LiteralPath (
        Join-Path $RepoRoot $skillConsumeHostReportPath) -Algorithm SHA256).Hash.ToLowerInvariant()
    $skillConsumeActivationVersions = @($skillConsumeActivationReport.versions)
    $skillConsumeActivationSourceArtifacts = @(
        $skillConsumeActivationReport.sourceSha256.PSObject.Properties |
            ForEach-Object { [string]$_.Name })
    $skillConsumeActivationPredecessorArtifacts = @(
        $skillConsumeActivationReport.predecessorSourceSha256.PSObject.Properties |
            ForEach-Object { [string]$_.Name })
    $skillConsumeActivationExpectedPredecessors = [ordered]@{}
    foreach ($skillConsumeHostArtifact in $skillConsumeHostArtifacts) {
        $skillConsumeActivationExpectedPredecessors[$skillConsumeHostArtifact] =
            $skillConsumeHostReport.sourceSha256.PSObject.Properties[$skillConsumeHostArtifact].Value
    }
    $skillConsumeActivationExpectedPredecessors[
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommand.java'] =
        $skillConsumeWriterReport.sourceSha256.PSObject.Properties[
            'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommand.java'].Value
    $skillConsumeActivationExpectedPredecessors[
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditTransactionService.java'] =
        $skillConsumeDualMysqlReport.sourceSha256.PSObject.Properties[
            'FBSir-business/src/main/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditTransactionService.java'].Value
    $skillConsumeActivationExpectedPredecessors[
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommandTest.java'] =
        $skillConsumeWriterReport.sourceSha256.PSObject.Properties[
            'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditCommandTest.java'].Value
    $skillConsumeActivationExpectedPredecessors[
        'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriterTest.java'] =
        $skillConsumeWriterReport.sourceSha256.PSObject.Properties[
            'FBSir-business/src/test/java/com/wx/fbsir/business/board/credit/service/SkillConsumeCreditWriterTest.java'].Value
    if ($skillConsumeActivationReport.schemaVersion -ne 1 `
            -or $skillConsumeActivationReport.result -cne 'PASS_LOCAL_DEFAULT_OFF_SKILL_CONSUME_ACTIVATION_SAFETY' `
            -or $skillConsumeActivationReport.candidateReadyForCommit -ne $true `
            -or $skillConsumeActivationReport.releaseReady -ne $false `
            -or $skillConsumeActivationReport.productionChanged -ne $false `
            -or $skillConsumeActivationReport.productionConnectionUsed -ne $false `
            -or $skillConsumeActivationReport.frozenListedPackageModified -ne $false `
            -or $skillConsumeActivationReport.transactionBoundary.hostDispatcherTransactional -ne $false `
            -or $skillConsumeActivationReport.transactionBoundary.legacyExecutorRequired -ne $true `
            -or $skillConsumeActivationReport.transactionBoundary.ambientV2FailsClosed -ne $true `
            -or $skillConsumeActivationReport.hostSession.nullAccepted -ne $true `
            -or $skillConsumeActivationReport.hostSession.blankAccepted -ne $false `
            -or $skillConsumeActivationReport.hostSession.absentDigestHexLength -ne 64 `
            -or $skillConsumeActivationReport.remainingReleaseBlockers.outerTransactionPoolStarvationRiskOpen -ne $false `
            -or $skillConsumeActivationReport.remainingReleaseBlockers.hostSessionCompatibilityOpen -ne $false `
            -or $skillConsumeActivationReport.remainingReleaseBlockers.commercialHubOutboxOpen -ne $true `
            -or $skillConsumeActivationReport.remainingReleaseBlockers.externalDeliveryExactlyOnce -ne $false `
            -or $skillConsumeActivationReport.remainingReleaseBlockers.exactTargetActivationReceiptOpen -ne $true `
            -or $skillConsumeActivationReport.predecessorReceipt.path -cne $skillConsumeHostReportPath `
            -or $skillConsumeActivationReport.predecessorReceipt.sha256 -cne $skillConsumeActivationReceiptHash `
            -or $skillConsumeActivationReport.frozenPackage.sha256 -cne 'd2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd' `
            -or $skillConsumeActivationVersions.Count -ne 2 `
            -or @($skillConsumeActivationVersions | Where-Object {
                $_.testsRun -ne 6 -or $_.passed -ne 6 -or $_.failures -ne 0 `
                    -or $_.errors -ne 0 -or $_.skipped -ne 0 `
                    -or (Compare-Object -ReferenceObject ($skillConsumeActivationExpectedTestNames | Sort-Object) `
                        -DifferenceObject (@($_.testNames) | Sort-Object)).Count -ne 0 `
                    -or $_.surefireReportSha256 -notmatch '^[0-9a-f]{64}$' `
                    -or $_.productionConnectionUsed -ne $false `
                    -or $_.workDirectoryCleaned -ne $true
            }).Count -ne 0 `
            -or (Compare-Object -ReferenceObject (@($skillConsumeActivationExpectedPredecessors.Keys) | Sort-Object) `
                -DifferenceObject ($skillConsumeActivationPredecessorArtifacts | Sort-Object)).Count -ne 0 `
            -or (Compare-Object -ReferenceObject ($skillConsumeActivationArtifacts | Sort-Object) `
                -DifferenceObject ($skillConsumeActivationSourceArtifacts | Sort-Object)).Count -ne 0) {
        throw 'W4B5E activation-safety receipt is incomplete or exceeds its evidence boundary'
    }
    foreach ($skillConsumeActivationPredecessor in $skillConsumeActivationExpectedPredecessors.Keys) {
        if ($skillConsumeActivationReport.predecessorSourceSha256.PSObject.Properties[
                $skillConsumeActivationPredecessor].Value -cne
                $skillConsumeActivationExpectedPredecessors[$skillConsumeActivationPredecessor]) {
            throw "W4B5E predecessor source binding drifted: $skillConsumeActivationPredecessor"
        }
    }
    foreach ($skillConsumeActivationArtifact in $skillConsumeActivationArtifacts) {
        if ($skillConsumeActivationArtifact -in @(
                '.fbs-engineering/contract.json',
                'docs/independent-board/implementation-status.json',
                'docs/independent-board/taskboard.json',
                'scripts/verify-independent-board-control-plane.ps1')) {
            continue
        }
        $skillConsumeActivationCurrentHash = (Get-FileHash -LiteralPath (
            Join-Path $RepoRoot $skillConsumeActivationArtifact) -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($skillConsumeActivationReport.sourceSha256.PSObject.Properties[
                $skillConsumeActivationArtifact].Value -cne $skillConsumeActivationCurrentHash) {
            throw "W4B5E activation-safety artifact binding drifted: $skillConsumeActivationArtifact"
        }
    }

    # W3j is deliberately a read-only release-receipt gate. It does not claim
    # that a target is deployed; it keeps release activation closed until a
    # separately captured, exact-target receipt satisfies the verifier.
    $w3jReadinessReport = Read-Utf8Json -RelativePath 'reports\independent-board\w3j-credit-candidate-release-readiness-verification-20260722.json'
    $w3jExpectedArtifacts = @(
        @{ path = 'docs\independent-board\W3J-CREDIT-CANDIDATE-RELEASE-READINESS-CONTRACT.md'; reportName = 'contract'; hash = '67ff86cf7fabc4da95ab8639810cf77235b42c226caad9246590e469eb5e180f' },
        @{ path = 'docs\independent-board\W3J-CREDIT-CANDIDATE-RELEASE-RECEIPT.example.json'; reportName = 'receiptTemplate'; hash = '3dff4b733fa97bb146d65c800c6930a933b1a74e4a714cf7947e7c470d932c23' },
        @{ path = 'scripts\verify-independent-board-credit-candidate-release-readiness.ps1'; reportName = 'verifier'; hash = '1426882620f60f9a69d6333874c695942c3fd26d0e624644e47e8161631c0c6c' }
    )
    foreach ($expectedW3jArtifact in $w3jExpectedArtifacts) {
        $actualW3jArtifactHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot $expectedW3jArtifact.path)).Hash.ToLowerInvariant()
        if ($actualW3jArtifactHash -cne $expectedW3jArtifact.hash `
                -or $w3jReadinessReport.artifacts.$($expectedW3jArtifact.reportName).path -cne $expectedW3jArtifact.path.Replace('\', '/') `
                -or $w3jReadinessReport.artifacts.$($expectedW3jArtifact.reportName).sha256 -cne $actualW3jArtifactHash) {
            throw "W3j release-readiness artifact binding drifted: $($expectedW3jArtifact.path)"
        }
    }
    if ($w3jReadinessReport.schemaVersion -ne 1 `
            -or $w3jReadinessReport.result -cne 'PASS_LOCAL_PREPRODUCTION_RELEASE_READINESS_GATE' `
            -or $w3jReadinessReport.candidateReadyForCommit -ne $true `
            -or $w3jReadinessReport.releaseReady -ne $false `
            -or $w3jReadinessReport.productionChanged -ne $false `
            -or $w3jReadinessReport.candidateActivationPerformed -ne $false `
            -or $w3jReadinessReport.repository.implementationCommit -cne '7cad96df5f41fe61da17f2659dee27e8d72489c0' `
            -or $w3jReadinessReport.repository.listedPackageWriteback -ne $false `
            -or $w3jReadinessReport.frozenSurface.unchanged -ne $true `
            -or $w3jReadinessReport.frozenSurface.observedSha256 -cne $w3jReadinessReport.frozenSurface.requiredSha256 `
            -or $w3jReadinessReport.artifacts.receiptTemplate.isActivationEvidence -ne $false `
            -or $w3jReadinessReport.gate.executionClass -cne 'local_read_only_preflight' `
            -or $w3jReadinessReport.gate.canonicalDomainInventory -cne 'config/deployment/u3w-domain-inventory.json' `
            -or $w3jReadinessReport.gate.successMeaning -cne 'readyForHumanActivationReview_only_not_deployed_or_activated' `
            -or $w3jReadinessReport.verification.powershell51Parser -cne 'PASS' `
            -or $w3jReadinessReport.verification.selfTest.state -cne 'PASS' `
            -or $w3jReadinessReport.verification.normalModeDirtyWorktree -cne 'PASS_FAIL_CLOSED' `
            -or $w3jReadinessReport.verification.independentReview.finalP0 -ne 0 `
            -or $w3jReadinessReport.verification.independentReview.finalP1 -ne 0 `
            -or $w3jReadinessReport.evidenceBoundary.productionTargetVersionBound -ne $false `
            -or $w3jReadinessReport.evidenceBoundary.productionDatabaseMigrated -ne $false `
            -or $w3jReadinessReport.evidenceBoundary.productionCandidateFlagEnabled -ne $false `
            -or $w3jReadinessReport.nextSlice.id -cne $canonicalW3NextSliceId) {
        throw 'W3j release-readiness receipt drifted or exceeds its evidence boundary'
    }
    $expectedW3jNegativeVectors = @(
        'unknown_top_level', 'wrong_backend_host', 'missing_database_proof', 'rollback_not_proven', 'duplicate_json_key', 'non_head_expected_commit'
    )
    if ((Compare-Object -ReferenceObject $expectedW3jNegativeVectors -DifferenceObject @($w3jReadinessReport.verification.selfTest.negativeVectors)).Count -ne 0) {
        throw 'W3j release-readiness negative vector set drifted'
    }
    $windowsPowerShell = Get-Command powershell.exe -ErrorAction SilentlyContinue
    if ($null -eq $windowsPowerShell) {
        throw 'W3j release-readiness self-test requires Windows PowerShell 5.1'
    }
    $w3jSelfTestOutput = & $windowsPowerShell.Source -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot 'scripts\verify-independent-board-credit-candidate-release-readiness.ps1') -SelfTest
    if ($LASTEXITCODE -ne 0) {
        throw 'W3j release-readiness self-test failed'
    }
    $w3jSelfTest = (($w3jSelfTestOutput -join "`n") | ConvertFrom-Json)
    if ($w3jSelfTest.schema -cne 'fbsir.independent-board.credit-candidate-release-readiness-self-test/v1' `
            -or $w3jSelfTest.ok -ne $true `
            -or $w3jSelfTest.positive -cne 'PASS' `
            -or $w3jSelfTest.productionChanged -ne $false `
            -or (Compare-Object -ReferenceObject $expectedW3jNegativeVectors -DifferenceObject @($w3jSelfTest.negativeVectors)).Count -ne 0) {
        throw 'W3j release-readiness self-test receipt drifted'
    }

    $w4b2cSourcePaths = @{
        portalReadServiceSha256 = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\IndependentBoardPortalReadService.java'
        portalReadMapperSha256 = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\mapper\IndependentBoardPortalReadMapper.java'
        portalReadMapperXmlSha256 = 'FBSir-business\src\main\resources\mapper\board\IndependentBoardPortalReadMapper.xml'
        tenantViewSha256 = 'FBSir-business\src\main\java\com\wx\fbsir\business\board\portal\dto\BoardPortalTenantView.java'
        tokenServiceSha256 = 'FBSir-framework\src\main\java\com\wx\fbsir\framework\web\service\TokenService.java'
        globalExceptionHandlerSha256 = 'FBSir-framework\src\main\java\com\wx\fbsir\framework\web\exception\GlobalExceptionHandler.java'
        permissionStoreSha256 = 'FBSir-ui\src\store\modules\permission.js'
        candidateTableSha256 = 'FBSir-ui\src\views\business\independentBoard\admin\components\CandidateReadTable.vue'
        trafficAttributionSha256 = 'scripts\independent-board-traffic-attribution.mjs'
        trafficAttributionTestsSha256 = 'scripts\independent-board-traffic-attribution.test.mjs'
    }
    foreach ($sourceHashName in $w4b2cSourcePaths.Keys) {
        if ($sourceHashName -ceq 'portalReadMapperXmlSha256') {
            continue
        }
        $actualSourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot $w4b2cSourcePaths[$sourceHashName])).Hash.ToLowerInvariant()
        if ($actualSourceHash -cne $w4b2cReport.verification.sourceBinding.$sourceHashName) {
            throw "W4b.2c source changed after its evidence receipt: $sourceHashName"
        }
    }
    $actualCandidateMenuHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot 'sql\update_20260721_independent_board_portal_candidate_menu.sql')).Hash.ToLowerInvariant()
    if ($actualCandidateMenuHash -cne $w4b2cReport.candidate.dynamicMenuMigrationSha256) {
        throw 'W4b.2c candidate menu migration changed after its evidence receipt'
    }

    $trafficReport = Read-Utf8Json -RelativePath 'reports\independent-board\api2-independent-board-24h-traffic-attribution-20260721.json'
    $trafficKindTotal = $trafficReport.standardEvidenceLedger.trafficKinds.natural `
        + $trafficReport.standardEvidenceLedger.trafficKinds.probe `
        + $trafficReport.standardEvidenceLedger.trafficKinds.diagnostic `
        + $trafficReport.standardEvidenceLedger.trafficKinds.synthetic `
        + $trafficReport.standardEvidenceLedger.trafficKinds.unknown
    $selectedStatusLowerBoundTotal = $trafficReport.officialEntry.selectedStatusLowerBounds.'200' `
        + $trafficReport.officialEntry.selectedStatusLowerBounds.'499' `
        + $trafficReport.officialEntry.selectedStatusLowerBounds.'5xx' `
        + $trafficReport.officialEntry.statusUnclassifiedRemainderWithinLowerBound
    $selectedMethodLowerBoundTotal = $trafficReport.officialEntry.selectedMethodLowerBounds.GET `
        + $trafficReport.officialEntry.selectedMethodLowerBounds.POST `
        + $trafficReport.officialEntry.methodUnclassifiedRemainderWithinLowerBound
    $expectedNotProven = @(
        'actual_independent_board_usage_is_zero',
        'official_entry_target_natural_invocation',
        'same_binding_target_conversion',
        'target_conversion_by_version_channel_terminal_or_population'
    )
    $expectedCannotProve = @(
        'strict_24h_official_entry_target_attribution',
        'target_user_count_or_conversion_rate',
        'service_side_target_closure'
    )
    $notProvenDrift = @(Compare-Object -ReferenceObject $expectedNotProven -DifferenceObject @($trafficReport.evidenceBoundaries.notProven))
    $cannotProveDrift = @(Compare-Object -ReferenceObject $expectedCannotProve -DifferenceObject @($trafficReport.evidenceBoundaries.cannotProveWithCurrentProjection))
    if ($trafficReport.schema -cne 'fbsir.independent-board.api2-24h-traffic-attribution/v1' `
            -or $trafficReport.result -cne 'NO_ATTRIBUTABLE_INDEPENDENT_BOARD_SIGNAL_WITH_CONTRACT_GAPS' `
            -or $trafficReport.productContract.productId -cne 'fbsir-eight-seat-board' `
            -or $trafficReport.productContract.listedVersion -cne '26.7.20' `
            -or $trafficReport.productContract.name -cne (-join @([char]0x72EC, [char]0x8463, [char]0x4F1A)) `
            -or $trafficReport.productContract.frozenPackageModified -ne $false `
            -or $trafficReport.window.start -cne '2026-07-20T22:44:00+08:00' `
            -or $trafficReport.window.end -cne '2026-07-21T22:44:00+08:00' `
            -or $trafficReport.window.interval -cne '[start,end)' `
            -or $trafficReport.standardEvidenceLedger.fixedWindowRows -ne 733 `
            -or $null -ne $trafficReport.standardEvidenceLedger.snapshotDigest `
            -or $trafficReport.standardEvidenceLedger.snapshotDigestState -cne 'legacy_snapshot_digest_not_captured_before_local_digest_contract' `
            -or $trafficKindTotal -ne 733 `
            -or $trafficReport.standardEvidenceLedger.target.exactProductRows -ne 0 `
            -or $trafficReport.standardEvidenceLedger.target.naturalRows -ne 0 `
            -or $trafficReport.standardEvidenceLedger.target.distinctBindings -ne 0 `
            -or $trafficReport.rawApplicationEvidence.mcpEvents.targetDirectSignals -ne 0 `
            -or $trafficReport.rawApplicationEvidence.mcpAccessTsv.targetDirectSignals -ne 0 `
            -or $trafficReport.rawApplicationEvidence.businessLedger.targetDirectSignals -ne 0 `
            -or $trafficReport.rawApplicationEvidence.businessLedger.productCreditEligibleRows -ne 0 `
            -or $trafficReport.officialEntry.countSemantics -cne 'LOWER_BOUND_NOT_EXACT_FULL_RAW_REPLAY' `
            -or $trafficReport.officialEntry.requestLowerBound -ne 26344641 `
            -or $trafficReport.officialEntry.selectedStatusLowerBounds.'200' -ne 26318825 `
            -or $trafficReport.officialEntry.selectedStatusLowerBounds.'499' -ne 19466 `
            -or $trafficReport.officialEntry.selectedStatusLowerBounds.'5xx' -ne 6343 `
            -or $trafficReport.officialEntry.statusUnclassifiedRemainderWithinLowerBound -ne 7 `
            -or $selectedStatusLowerBoundTotal -ne $trafficReport.officialEntry.requestLowerBound `
            -or $trafficReport.officialEntry.selectedMethodLowerBounds.GET -ne 18380038 `
            -or $trafficReport.officialEntry.selectedMethodLowerBounds.POST -ne 7964597 `
            -or $trafficReport.officialEntry.methodUnclassifiedRemainderWithinLowerBound -ne 6 `
            -or $selectedMethodLowerBoundTotal -ne $trafficReport.officialEntry.requestLowerBound `
            -or $trafficReport.liveServiceReadback.releaseIdentityState -cne 'DRIFT' `
            -or $trafficReport.liveServiceReadback.rollingLedgerState -cne 'MUTABLE_NOT_AN_IMMUTABLE_24H_SNAPSHOT' `
            -or $trafficReport.attributionDebt.activePhase1TargetRegistrationOccurrences -ne 0 `
            -or $trafficReport.attributionDebt.structuredRawRetention -cne 'approximately_3h06m_not_24h' `
            -or $trafficReport.attributionDebt.requiredRetention -cne 'at_least_26h_plus_immutable_fixed_window_snapshot' `
            -or $trafficReport.decision.attributableTargetTraffic -ne 0 `
            -or $trafficReport.decision.actualTargetUsageIsZero -cne 'NOT_PROVEN' `
            -or $trafficReport.decision.reason -cne 'No target identity was present in the scanned currently available application evidence, while the official-entry projection drops authoritative product and binding dimensions.' `
            -or $trafficReport.decision.privateBoardHandling -cne 'confusion_signal_only_never_merged_into_independent_board' `
            -or $trafficReport.localAttributionContract.testCount -ne 42 `
            -or $trafficReport.localAttributionContract.testFailures -ne 0 `
            -or $trafficReport.localAttributionContract.candidateDefaultEnabled -ne $false `
            -or $trafficReport.localAttributionContract.authoritativeProductCreditAlwaysZeroForUnsignedInput -ne $true `
            -or -not (@($trafficReport.evidenceBoundaries.proven) -contains 'scanned_available_application_evidence_has_zero_direct_target_signature') `
            -or $notProvenDrift.Count -ne 0 `
            -or $cannotProveDrift.Count -ne 0) {
        throw 'API2 independent-board fixed-window traffic attribution receipt drifted or overclaims its evidence'
    }

    $trafficReportMarkdown = Get-Content -LiteralPath (Join-Path $RepoRoot 'reports\independent-board\api2-independent-board-24h-traffic-attribution-20260721.md') -Raw -Encoding UTF8
    $trafficConclusionMarkdown = Get-Content -LiteralPath (Join-Path $RepoRoot 'docs\independent-board\API2-INDEPENDENT-BOARD-24H-TRAFFIC-ATTRIBUTION-20260721.md') -Raw -Encoding UTF8
    $reportRequiredMarkers = @(
        [regex]::Unescape('\u4e0d\u80fd\u636e\u6b64\u65ad\u8a00\u72ec\u8463\u4f1a\u5b9e\u9645\u4f7f\u7528\u91cf\u4e3a 0'),
        [regex]::Unescape('\u81f3\u5c11 26,344,641'),
        [regex]::Unescape('\u79c1\u8463\u4f1a'),
        [regex]::Unescape('\u7ea6 3 \u5c0f\u65f6 6 \u5206\u949f'),
        [regex]::Unescape('\u4e0d\u53ef\u53d8'),
        [regex]::Unescape('\u5f53\u524d\u53ef\u5f97'),
        [regex]::Unescape('7 \u6b21\u672a\u5206\u7c7b\u4f59\u9879'),
        [regex]::Unescape('6 \u6b21\u672a\u5206\u7c7b\u4f59\u9879')
    )
    $conclusionRequiredMarkers = @(
        [regex]::Unescape('\u800c\u4e0d\u662f\u201c\u5b9e\u9645\u4f7f\u7528\u4e3a 0\u201d'),
        [regex]::Unescape('\u81f3\u5c11\u6709 26,344,641'),
        [regex]::Unescape('\u79c1\u8463\u4f1a'),
        [regex]::Unescape('\u4e0d\u5c11\u4e8e 26 \u5c0f\u65f6'),
        [regex]::Unescape('\u5f53\u524d\u53ef\u5f97'),
        [regex]::Unescape('7 \u6b21\u4f59\u9879'),
        [regex]::Unescape('6 \u6b21\u4f59\u9879')
    )
    foreach ($marker in $reportRequiredMarkers) {
        if (-not $trafficReportMarkdown.Contains($marker)) {
            throw "API2 traffic report prose is missing a required evidence-boundary marker: $marker"
        }
    }
    foreach ($marker in $conclusionRequiredMarkers) {
        if (-not $trafficConclusionMarkdown.Contains($marker)) {
            throw "API2 traffic conclusion prose is missing a required evidence-boundary marker: $marker"
        }
    }
    $evidenceOverclaimMarkers = @(
        [regex]::Unescape('\u5b8c\u6574\u91cd\u653e'),
        [regex]::Unescape('\u5b8c\u6574\u5e94\u7528\u8bc1\u636e'),
        [regex]::Unescape('\u5b8c\u6574\u8bc1\u636e'),
        'complete application evidence',
        'complete raw replay'
    )
    foreach ($marker in $evidenceOverclaimMarkers) {
        if ($trafficReport.decision.reason.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -ge 0 `
                -or $trafficReportMarkdown.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -ge 0 `
                -or $trafficConclusionMarkdown.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            throw "API2 traffic evidence overclaims complete coverage despite retention debt: $marker"
        }
    }
    Push-Location $RepoRoot
    try {
        Invoke-NodeScript -Arguments @('--test', 'scripts\independent-board-traffic-attribution.test.mjs')
    }
    finally {
        Pop-Location
    }

    $implementationStatus = Read-Utf8Json -RelativePath 'docs\independent-board\implementation-status.json'
    $taskboard = Read-Utf8Json -RelativePath 'docs\independent-board\taskboard.json'
    $engineeringContract = Read-Utf8Json -RelativePath '.fbs-engineering\contract.json'
    $w1Wave = @($taskboard.waves | Where-Object {
            $_.id -ceq 'W1_OFFICIAL_EXPERTS_SERVICE_ATTRIBUTION_INTENT_CLOSURE'
        })
    $w1NaturalChain = 'ENTRY_OBSERVED,INTENT_CLASSIFIED,FIRST_VALUE_COMPLETED'
    $w1DualMysql = Read-Utf8Json -RelativePath 'reports\independent-board\w1a-attribution-v1-dual-mysql-latest.json'
    $w1Verification = Read-Utf8Json -RelativePath 'reports\independent-board\w1a-official-experts-attribution-verification-latest.json'
    $w1ProductionReadback = Read-Utf8Json -RelativePath 'reports\independent-board\w1a-production-readonly-audit-latest.json'
    $w1ProductionReadiness = Read-Utf8Json -RelativePath 'reports\independent-board\w1a-production-readiness-latest.json'
    $w1GoldenVector = Read-Utf8Json -RelativePath 'FBSir-business\src\test\resources\independent-board-attribution-v1-golden-vector.json'
    $w1ProductionReadinessFailedGates = @($w1ProductionReadiness.failedGateIds)
    $w1ProductionReadinessStrictHead = @($w1ProductionReadiness.gates | Where-Object {
            $_.id -ceq 'strict_head'
        })
    $w1ProductionReadinessStateValid = (
        $w1ProductionReadiness.status -ceq 'NOT_READY_FOR_PRODUCTION_RELEASE' `
            -and $w1ProductionReadiness.readyForDefaultOffRelease -eq $false `
            -and $w1ProductionReadinessFailedGates.Count -gt 0
    ) -or (
        $w1ProductionReadiness.status -ceq 'READY_FOR_DEFAULT_OFF_RELEASE' `
            -and $w1ProductionReadiness.readyForDefaultOffRelease -eq $true `
            -and $w1ProductionReadinessFailedGates.Count -eq 0
    )
    $w1U3wCandidateStateAllowed = @(
        'committed_clean_live_readonly_gate_strict_head_verified_no_go',
        'committed_clean_live_readonly_gate_strict_head_verified_ready'
    )
    Push-Location $RepoRoot
    try {
        Invoke-NodeScript -Arguments @('--test', 'scripts\independent-board-production-readiness.test.mjs')
    }
    finally {
        Pop-Location
    }
    $w1MysqlVersions = @($w1DualMysql.results | ForEach-Object { $_.version })
    $w1MysqlInvalid = @($w1DualMysql.results | Where-Object {
            $_.migrationRerun -cne 'PASS' `
                -or $_.append -cne 'PASS' `
                -or $_.updateRejected -cne 'PASS' `
                -or $_.deleteRejected -cne 'PASS' `
                -or $_.aggregate -cne '1|1|1|0' `
                -or $_.authoritativeProductCredit -ne 0 `
                -or $_.schemaFingerprintSha256 -cne 'a0507f51960622d49b66c4d8b1b7382dc8bc16a904d577bac1ca942bb8748b28'
        })
    if ($w1Wave.Count -ne 1 `
            -or $implementationStatus.activeWave -cne 'W1_OFFICIAL_EXPERTS_SERVICE_ATTRIBUTION_INTENT_CLOSURE' `
            -or $implementationStatus.activeSlice -cne 'W1D_API2_CLEANROOM_PUBLISHER_AND_REAL_SAME_BINDING_PROOF' `
            -or $implementationStatus.w1a.state -cne $w1Wave[0].state `
            -or $implementationStatus.w1a.state -notlike '*real_same_binding_proof_pending' `
            -or $w1Wave[0].activeSlice -cne 'W1D_API2_CLEANROOM_PUBLISHER_AND_REAL_SAME_BINDING_PROOF' `
            -or $engineeringContract.contracts.currentMainline.activeSlice -cne 'W1D_API2_CLEANROOM_PUBLISHER_AND_REAL_SAME_BINDING_PROOF' `
            -or $implementationStatus.expertPackage.productId -cne 'fbsir-eight-seat-board' `
            -or $implementationStatus.expertPackage.packageId -cne 'fbsir-eight-seat-board' `
            -or $implementationStatus.expertPackage.agentName -cne 'board-convener' `
            -or $implementationStatus.expertPackage.marketplace -cne 'experts' `
            -or $implementationStatus.expertPackage.surface -cne 'listed_runtime_state' `
            -or $implementationStatus.expertPackage.listedManifestVersion -cne '26.7.21' `
            -or $implementationStatus.expertPackage.embeddedContractVersion -cne '26.7.20' `
            -or $w1Wave[0].officialIdentity.listedManifestVersion -cne '26.7.21' `
            -or $w1Wave[0].officialIdentity.embeddedContractVersion -cne '26.7.20' `
            -or $w1Wave[0].officialExpertsContentWriteAllowed -ne $false `
            -or $w1Wave[0].connectorRequiredForFirstValue -ne $false `
            -or $engineeringContract.contracts.currentMainline.wave -cne 'W1_OFFICIAL_EXPERTS_SERVICE_ATTRIBUTION_INTENT_CLOSURE' `
            -or $engineeringContract.contracts.currentMainline.officialExpertsContentWriteAllowed -ne $false `
            -or $engineeringContract.contracts.currentMainline.connectorRequiredForFirstValue -ne $false `
            -or $engineeringContract.contracts.frozenSurface.listedManifestVersion -cne '26.7.21' `
            -or $engineeringContract.contracts.frozenSurface.embeddedContractVersion -cne '26.7.20' `
            -or $engineeringContract.contracts.firstVerticalSlice.rawPromptStored -ne $false `
            -or $engineeringContract.contracts.firstVerticalSlice.productionCreditFailClosed -ne $true `
            -or $engineeringContract.contracts.firstVerticalSlice.conversationFailOpenOnObservabilityFailure -ne $true `
            -or (@($implementationStatus.w1a.naturalChain) -join ',') -cne $w1NaturalChain `
            -or (@($w1Wave[0].naturalChain) -join ',') -cne $w1NaturalChain `
            -or (@($engineeringContract.contracts.firstVerticalSlice.flow) -join ',') -cne $w1NaturalChain `
            -or $implementationStatus.w1a.migration -cne 'public_init_043' `
            -or $implementationStatus.w1a.productionReadbackReport -cne 'reports/independent-board/w1a-production-readonly-audit-latest.json' `
            -or $engineeringContract.artifacts.w1aProductionReadonlyAudit -cne 'reports/independent-board/w1a-production-readonly-audit-latest.json' `
            -or $implementationStatus.w1a.productionReadinessContract -cne 'docs/independent-board/W1A-PRODUCTION-READINESS-GATE.md' `
            -or $implementationStatus.w1a.productionReadinessReport -cne 'reports/independent-board/w1a-production-readiness-latest.json' `
            -or $implementationStatus.w1a.productionReadinessState -cne $w1ProductionReadiness.status `
            -or $w1U3wCandidateStateAllowed -notcontains $implementationStatus.w1a.u3wSourceTruth.candidateState `
            -or $implementationStatus.w1a.u3wSourceTruth.candidateCommit -cne '5d912fc1e5e5b1ec73658288cd8416e1d25e07df' `
            -or $implementationStatus.w1a.u3wSourceTruth.candidateCommit -cne $w1ProductionReadiness.evidence.localSourceCommit `
            -or $engineeringContract.contracts.currentMainline.u3wSourceTruth.candidateState -cne $implementationStatus.w1a.u3wSourceTruth.candidateState `
            -or $engineeringContract.contracts.currentMainline.u3wSourceTruth.candidateCommit -cne $implementationStatus.w1a.u3wSourceTruth.candidateCommit `
            -or $w1Wave[0].u3wCandidateCommit -cne $implementationStatus.w1a.u3wSourceTruth.candidateCommit `
            -or $engineeringContract.artifacts.w1aProductionReadinessContract -cne 'docs/independent-board/W1A-PRODUCTION-READINESS-GATE.md' `
            -or $engineeringContract.artifacts.w1aProductionReadinessEvaluator -cne 'scripts/independent-board-production-readiness.mjs' `
            -or $engineeringContract.artifacts.w1aProductionReadinessRunner -cne 'scripts/verify-independent-board-production-readiness.ps1' `
            -or $engineeringContract.artifacts.w1aProductionReadinessReport -cne 'reports/independent-board/w1a-production-readiness-latest.json' `
            -or $w1ProductionReadiness.schema -cne 'fbsir.independentBoardProductionReadiness.v1' `
            -or -not $w1ProductionReadinessStateValid `
            -or $w1ProductionReadiness.productionChanged -ne $false `
            -or $w1ProductionReadinessStrictHead.Count -ne 1 `
            -or $w1ProductionReadinessStrictHead[0].pass -ne $true `
            -or $w1Verification.status -notlike 'LOCAL_RELEASE_CANDIDATE*PRODUCTION_CLOSURE_PENDING' `
            -or $w1Verification.u3w.commit -cne $implementationStatus.w1a.u3wSourceTruth.candidateCommit `
            -or $w1Verification.u3w.productionReadiness.status -cne $w1ProductionReadiness.status `
            -or $w1Verification.u3w.productionReadiness.strictHeadPass -ne $true `
            -or $w1Verification.u3w.productionReadiness.productionChanged -ne $false `
            -or (@($w1Verification.u3w.productionReadiness.failedGates) -join ',') -cne (@($w1ProductionReadiness.failedGateIds) -join ',') `
            -or $w1Verification.singleNextAction -cne $implementationStatus.singleNextAction `
            -or $w1ProductionReadback.status -cne 'BLOCKED_PRODUCTION_SCHEMA_BASELINE_AND_DEPLOYMENT_CHANNEL_NOT_READY' `
            -or $w1ProductionReadback.productionChanged -ne $false `
            -or $w1ProductionReadback.database.database -cne 'fbsir' `
            -or $w1ProductionReadback.database.serverVersion -cne '8.0.45' `
            -or $w1ProductionReadback.database.totalTableCount -ne 99 `
            -or $w1ProductionReadback.database.migrationTableCount -ne 0 `
            -or $w1ProductionReadback.database.boardAttributionTableCount -ne 0 `
            -or $w1ProductionReadback.runtime.attributionClassCount -ne 0 `
            -or $w1ProductionReadback.portals.meHttpStatus -ne 404 `
            -or $w1ProductionReadback.portals.adminHttpStatus -ne 404 `
            -or $implementationStatus.w1a.featureFlags.observationWriterEnabled -ne $false `
            -or $implementationStatus.w1a.featureFlags.intentClassifierEnabled -ne $false `
            -or $implementationStatus.w1a.featureFlags.observationAdminReadEnabled -ne $false `
            -or $implementationStatus.w1a.featureFlags.productCreditEnabled -ne $false `
            -or $implementationStatus.w1a.releaseReady -ne $false `
            -or $implementationStatus.w1a.productionAuthority -ne $false `
            -or $implementationStatus.w1a.api2SourceTruth.repository -cne 'https://github.com/fubangshou/FBSAI.git' `
            -or $implementationStatus.w1a.api2SourceTruth.baselineCommit -cne 'a0834ea5d4c1c3f95be9d25d26913c2d973e09d0' `
            -or $implementationStatus.w1a.api2SourceTruth.candidateState -cne 'committed_pushed_clean_strict_head_package_verified_default_off' `
            -or $implementationStatus.w1a.api2SourceTruth.candidateCommit -cne '7b84d4721217cc4d98426b68d7efa31bc62ef5bb' `
            -or $implementationStatus.w1a.api2SourceTruth.candidateCriticalDeploySnapshotSha256 -cne '230E5C448CDADC786ADA2022EEC5CB8D36159A08560BAB0F8F2E0A877AA6AE98' `
            -or $implementationStatus.w1a.api2SourceTruth.productionDeclaredCommit -cne 'c01891a0ca3e11db0a0fe51828276fab33b862b5' `
            -or $engineeringContract.contracts.currentMainline.api2SourceTruth.baselineCommit -cne 'a0834ea5d4c1c3f95be9d25d26913c2d973e09d0' `
            -or $engineeringContract.contracts.currentMainline.api2SourceTruth.candidateState -cne 'committed_pushed_clean_strict_head_package_verified_default_off' `
            -or $engineeringContract.contracts.currentMainline.api2SourceTruth.candidateCommit -cne '7b84d4721217cc4d98426b68d7efa31bc62ef5bb' `
            -or $engineeringContract.contracts.currentMainline.api2SourceTruth.candidateCriticalDeploySnapshotSha256 -cne '230E5C448CDADC786ADA2022EEC5CB8D36159A08560BAB0F8F2E0A877AA6AE98' `
            -or @($w1Wave[0].completedSubset) -notcontains 'api2_candidate_7b84d472_committed_pushed_and_clean_strict_head_package_verified' `
            -or @($w1Wave[0].remaining) -contains 'api2_candidate_commit_push_and_strict_head_package' `
            -or $w1GoldenVector.schemaVersion -cne 'fbsir.independentBoardAttributionGoldenVector.v1' `
            -or $w1GoldenVector.testOnly -ne $true `
            -or $w1GoldenVector.event.sameBindingKey -cne '' `
            -or $w1GoldenVector.event.listedManifestVersion -cne '26.7.21' `
            -or $w1GoldenVector.expected.eventDigest -cne '2d60f3fdc6a8db56ae3f9614812bedbc38b6fc8644c8ccab01004344925b9d35' `
            -or $w1DualMysql.status -cne 'PASS' `
            -or $w1DualMysql.migration -cne 'public_init_043' `
            -or $w1DualMysql.migrationSha256 -cne '287a8b141abc80b2d95ff6a0cd97e8dbc4fa38845ae7b96c7bd8522e8f5b49b7' `
            -or $w1DualMysql.historicalPublicInit037Sha256 -cne '59e3696ff3f8d4a16b4659c94a108f35fb1badf2f4de16079229c031a0b44cce' `
            -or (@($w1MysqlVersions) -join ',') -cne '8.0.30,8.4.8' `
            -or $w1MysqlInvalid.Count -ne 0) {
        throw 'W1A official experts attribution contract, control truth or dual-MySQL evidence drifted'
    }
    $w3Wave = @($taskboard.waves | Where-Object { $_.id -ceq 'W3_ADMIN_PORTAL' })
    $w4Wave = @($taskboard.waves | Where-Object { $_.id -ceq 'W4_OAUTH_CONNECTOR' })
    if ($implementationStatus.platformVersion -cne '0.4.9-dev' `
            -or $implementationStatus.w3h.state -cne 'local_default_off_plan_policy_admin_governance_verified' `
            -or $implementationStatus.w3h.databaseMigration -cne 'public_init_039_manifested_not_applied_to_production' `
            -or $implementationStatus.w3h.nextSlice -cne $canonicalW3hSuccessorSliceId `
            -or $implementationStatus.w3i.state -cne 'local_default_off_meeting_audit_policy_lineage_verified' `
            -or $implementationStatus.w3i.verificationReport -cne 'reports/independent-board/w3i-meeting-audit-policy-lineage-verification-20260723.json' `
            -or $implementationStatus.w3i.nextSlice -cne $canonicalW3iSuccessorSliceId `
            -or $implementationStatus.w3i.productionAuthority -ne $false `
            -or $implementationStatus.w3j.state -cne 'local_read_only_credit_candidate_release_readiness_gate_verified' `
            -or $implementationStatus.w3j.verificationReport -cne 'reports/independent-board/w3j-credit-candidate-release-readiness-verification-20260722.json' `
            -or $implementationStatus.w3j.activationBoundary -cne 'ready_for_human_activation_review_only_not_deployed_or_activated' `
            -or $implementationStatus.w3j.productionAuthority -ne $false `
            -or $implementationStatus.w3j.nextSlice -cne $canonicalW3NextSliceId `
            -or $implementationStatus.w3k.state -cne 'local_default_off_plan_policy_database_monotonic_chain_verified' `
            -or $implementationStatus.w3k.contract -cne 'docs/independent-board/W3K-PLAN-POLICY-MONOTONIC-CHAIN-CONTRACT.md' `
            -or $implementationStatus.w3k.adr -cne 'docs/decisions/ADR-005-independent-board-plan-policy-database-monotonic-chain.md' `
            -or $implementationStatus.w3k.verificationReport -cne 'reports/independent-board/w3k-plan-policy-monotonic-chain-verification-20260723.json' `
            -or $implementationStatus.w3k.databaseMigration -cne 'public_init_040_manifested_not_applied_to_production' `
            -or $implementationStatus.w3k.productionAuthority -ne $false `
            -or $implementationStatus.w3k.nextSlice -cne $canonicalW3NextSliceId `
            -or $implementationStatus.w3k.controlledAuthority.state -cne 'local_default_off_dual_mysql_procedure_execution_and_two_account_rejection_matrix_verified' `
            -or $implementationStatus.w3k.controlledAuthority.verificationReport -cne 'reports/independent-board/w3k-plan-policy-authority-mysql-verification-20260723.json' `
            -or $implementationStatus.w3k.controlledAuthority.databaseMigration -cne 'public_init_041_manifested_not_applied_to_production' `
            -or $implementationStatus.w3k.controlledAuthority.productionAuthority -ne $false `
            -or $implementationStatus.w3k.controlledAuthority.nextSlice -cne $canonicalW3NextSliceId `
            -or $implementationStatus.w4b5d.state -cne 'local_default_off_skill_consume_host_wiring_dual_mysql_verified' `
            -or $implementationStatus.w4b5d.contract -cne 'docs/independent-board/W4B5D-SKILL-CONSUME-HOST-WIRING-CONTRACT.md' `
            -or $implementationStatus.w4b5d.verificationReport -cne $skillConsumeHostReportPath `
            -or $implementationStatus.w4b5d.flags.creditLedgerCandidateEnabledByDefault -ne $false `
            -or $implementationStatus.w4b5d.flags.skillConsumeCreditWriterEnabledByDefault -ne $false `
            -or $implementationStatus.w4b5d.flags.activationRequiresBoth -ne $true `
            -or $implementationStatus.w4b5d.hostSessionCompatibility -cne 'public_contract_nullable_but_v2_command_requires_non_empty_activation_blocked_pending_real_traffic_proof' `
            -or $implementationStatus.w4b5d.releaseReady -ne $false `
            -or $implementationStatus.w4b5d.productionAuthority -ne $false `
            -or $implementationStatus.w4b5d.nextSlice -cne $canonicalW4B5ESliceId `
            -or $implementationStatus.w4b5e.state -cne 'local_default_off_skill_consume_activation_safety_dual_mysql_verified' `
            -or $implementationStatus.w4b5e.verificationReport -cne $skillConsumeActivationReportPath `
            -or $implementationStatus.w4b5e.testsPerMysqlVersion -ne 6 `
            -or $implementationStatus.w4b5e.releaseReady -ne $false `
            -or $implementationStatus.w4b5e.productionAuthority -ne $false `
            -or $implementationStatus.w4b5e.historicalNextSlice -cne $canonicalCurrentW3SliceId `
            -or $taskboard.singleNextAction -cne $implementationStatus.singleNextAction `
            -or $taskboard.singleNextAction -notlike 'Close the executable U3W production-readiness gates*' `
            -or -not (@($w3Wave[0].completedSubset) -ccontains 'skill_consume_v2_default_off_host_service_wiring_dual_mysql_5_of_5_each_and_zero_legacy_fallback_verified') `
            -or -not (@($w3Wave[0].completedSubset) -ccontains 'skill_consume_v2_nontransactional_dispatcher_required_legacy_transaction_and_ambient_transaction_fail_closed_verified') `
            -or -not (@($w3Wave[0].completedSubset) -ccontains 'skill_consume_v2_nullable_host_session_domain_digest_and_dual_mysql_6_of_6_each_verified') `
            -or @($w3Wave[0].remaining) -ccontains 'skill_consume_service_final_status_cas_result_and_transaction_integration' `
            -or @($w3Wave[0].remaining) -ccontains 'skill_consume_v2_outer_transaction_connection_pool_capacity_or_dispatcher_separation' `
            -or @($w3Wave[0].remaining) -ccontains 'skill_consume_v2_nullable_host_session_compatibility_and_real_traffic_proof' `
            -or -not (@($w3Wave[0].remaining) -ccontains 'skill_consume_v2_operation_idempotent_commercial_hub_outbox_or_approved_exclusion') `
            -or $w3Wave.Count -ne 1 `
            -or $w3Wave[0].state -cne 'w3h_w3i_w3j_w3k_and_w4b5e_skill_consume_activation_safety_verified_local_default_off' `
            -or $w3Wave[0].pausedHistoricalSlice -cne $canonicalCurrentW3SliceId `
            -or $implementationStatus.w4b.runtimeMountVerificationReport -cne 'reports/independent-board/w4b2c-default-off-runtime-mount-verification-20260721.json' `
            -or $implementationStatus.w4b.api2TrafficAttributionReport -cne 'reports/independent-board/api2-independent-board-24h-traffic-attribution-20260721.json' `
            -or $implementationStatus.w4b.nextSlice -cne $canonicalNextSliceId `
            -or $w4Wave.Count -ne 1 `
            -or $w4Wave[0].state -cne 'w4a_verified_local_w4b1_internal_oauth_chain_verified_w4b2_default_off_runtime_candidate_verified_local' `
            -or $w4Wave[0].historicalPredecessorSlice -cne $canonicalNextSliceId `
            -or $w4Wave[0].nextSlice.id -cne $canonicalNextSliceId) {
        throw 'W3h/W3i/W3j or W4b.2c status and taskboard traceability drifted'
    }

    $contractW4b = $engineeringContract.contracts.uiPrototypeGate.w4bImplementation
    $contractW3h = $engineeringContract.contracts.uiPrototypeGate.w3hImplementation
    $contractW3i = $engineeringContract.contracts.uiPrototypeGate.w3iImplementation
    $contractW3j = $engineeringContract.contracts.uiPrototypeGate.w3jImplementation
    $contractW3k = $engineeringContract.contracts.uiPrototypeGate.w3kImplementation
    $contractW3kAuthority = $engineeringContract.contracts.uiPrototypeGate.w3kAuthorityImplementation
    $skillConsumeReleaseCommand = 'database-skill-consume-credit-v2'
    if ($engineeringContract.commands.$skillConsumeReleaseCommand.run -cne 'powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1 -AllowDestructiveTest' `
            -or $engineeringContract.commands.$skillConsumeReleaseCommand.timeout_ms -ne 900000 `
            -or -not (@($engineeringContract.policy.releaseBlockers) -ccontains $skillConsumeReleaseCommand) `
            -or -not (@($engineeringContract.policy.requiredReleaseBlockers) -ccontains $skillConsumeReleaseCommand) `
            -or -not (@($engineeringContract.policy.requiredWorkflowCommands.verify) -ccontains $skillConsumeReleaseCommand) `
            -or -not (@($engineeringContract.policy.applicationRuntimeEvidenceCommands) -ccontains $skillConsumeReleaseCommand) `
            -or -not (@($engineeringContract.workflows.verify) -ccontains $skillConsumeReleaseCommand) `
            -or $engineeringContract.artifacts.w3lSkillConsumeCreditWriterContract -cne 'docs/independent-board/W3L-SKILL-CONSUME-CREDIT-WRITER-CONTRACT.md' `
            -or $engineeringContract.artifacts.w3lSkillConsumeCreditLedgerV2Migration -cne 'sql/update_20260723_skill_consume_credit_ledger_v2.sql' `
            -or $engineeringContract.artifacts.w4b5cSkillConsumeDualMysqlContract -cne 'docs/independent-board/W4B5C-SKILL-CONSUME-DUAL-MYSQL-TRANSACTION-CONTRACT.md' `
            -or $engineeringContract.artifacts.w4b5cSkillConsumeDualMysqlVerificationReport -cne $skillConsumeDualMysqlReportPath `
            -or $engineeringContract.artifacts.w4b5dSkillConsumeHostWiringContract -cne 'docs/independent-board/W4B5D-SKILL-CONSUME-HOST-WIRING-CONTRACT.md' `
            -or $engineeringContract.artifacts.w4b5dSkillConsumeHostWiringVerificationReport -cne $skillConsumeHostReportPath `
            -or $engineeringContract.artifacts.skillConsumeCreditV2MysqlRunner -cne 'scripts/run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1' `
            -or $engineeringContract.artifacts.w3hPlanPolicyContract -cne 'docs/independent-board/W3H-PLAN-POLICY-REVISION-CONTRACT.md' `
            -or $engineeringContract.artifacts.w3hPlanPolicyMigration -cne 'sql/update_20260722_independent_board_plan_policy.sql' `
            -or $engineeringContract.artifacts.w3hPlanPolicyGovernanceVerificationReport -cne 'reports/independent-board/w3h-plan-policy-governance-verification-20260722.json' `
            -or $contractW3h.state -cne 'local_default_off_admin_candidate_dual_mysql_and_browser_verified' `
            -or $contractW3h.nextSlice -cne $canonicalW3hSuccessorSliceId `
            -or $contractW3h.productionAuthority -ne $false `
            -or $engineeringContract.artifacts.w3iMeetingAuditPolicyLineageContract -cne 'docs/independent-board/W3I-MEETING-AUDIT-POLICY-LINEAGE-CONTRACT.md' `
            -or $engineeringContract.artifacts.w3iMeetingAuditPolicyLineageVerificationReport -cne 'reports/independent-board/w3i-meeting-audit-policy-lineage-verification-20260723.json' `
            -or $contractW3i.state -cne 'local_default_off_meeting_audit_policy_lineage_verified' `
            -or $contractW3i.nextSlice -cne $canonicalW3iSuccessorSliceId `
            -or $contractW3i.productionAuthority -ne $false `
            -or $engineeringContract.artifacts.w3jCreditCandidateReleaseReadinessContract -cne 'docs/independent-board/W3J-CREDIT-CANDIDATE-RELEASE-READINESS-CONTRACT.md' `
            -or $engineeringContract.artifacts.w3jCreditCandidateReleaseReadinessVerificationReport -cne 'reports/independent-board/w3j-credit-candidate-release-readiness-verification-20260722.json' `
            -or $contractW3j.state -cne 'local_read_only_credit_candidate_release_readiness_gate_verified' `
            -or $contractW3j.activationBoundary -cne 'ready_for_human_activation_review_only_not_deployed_or_activated' `
            -or $contractW3j.nextSlice -cne $canonicalW3NextSliceId `
            -or $contractW3j.productionAuthority -ne $false `
            -or $engineeringContract.artifacts.w3kPlanPolicyMonotonicChainContract -cne 'docs/independent-board/W3K-PLAN-POLICY-MONOTONIC-CHAIN-CONTRACT.md' `
            -or $engineeringContract.artifacts.w3kPlanPolicyMonotonicChainAdr -cne 'docs/decisions/ADR-005-independent-board-plan-policy-database-monotonic-chain.md' `
            -or $engineeringContract.artifacts.w3kPlanPolicyMonotonicChainMigration -cne 'sql/update_20260723_independent_board_plan_policy_monotonic_chain.sql' `
            -or $engineeringContract.artifacts.w3kPlanPolicyMonotonicChainVerificationReport -cne 'reports/independent-board/w3k-plan-policy-monotonic-chain-verification-20260723.json' `
            -or $contractW3k.state -cne 'local_default_off_plan_policy_database_monotonic_chain_verified' `
            -or $contractW3k.migration -cne 'public_init_040_manifested_not_applied_to_production' `
            -or $contractW3k.productionAuthority -ne $false `
            -or $contractW3k.nextSlice -cne $canonicalW3NextSliceId `
            -or $engineeringContract.artifacts.w3kPlanPolicyAuthorityContract -cne 'docs/independent-board/W3K-PLAN-POLICY-CONTROLLED-AUTHORITY-CONTRACT.md' `
            -or $engineeringContract.artifacts.w3kPlanPolicyAuthorityAdr -cne 'docs/decisions/ADR-006-independent-board-plan-policy-controlled-procedure-authority.md' `
            -or $engineeringContract.artifacts.w3kPlanPolicyAuthorityMigration -cne 'sql/update_20260723_independent_board_plan_policy_authority.sql' `
            -or $engineeringContract.artifacts.w3kPlanPolicyAuthorityVerificationReport -cne 'reports/independent-board/w3k-plan-policy-authority-mysql-verification-20260723.json' `
            -or $contractW3kAuthority.state -cne 'local_default_off_dual_mysql_procedure_execution_and_two_account_rejection_matrix_verified' `
            -or $contractW3kAuthority.migration -cne 'public_init_041_manifested_not_applied_to_production' `
            -or $contractW3kAuthority.verificationReport -cne 'reports/independent-board/w3k-plan-policy-authority-mysql-verification-20260723.json' `
            -or $contractW3kAuthority.productionAuthority -ne $false `
            -or $contractW3kAuthority.nextSlice -cne $canonicalW3NextSliceId `
            -or $engineeringContract.artifacts.w4b2cRuntimeMountVerificationReport -cne 'reports/independent-board/w4b2c-default-off-runtime-mount-verification-20260721.json' `
            -or $engineeringContract.artifacts.w4b2cRuntimeAndAttributionAdr -cne 'docs/decisions/ADR-002-independent-board-w4b2c-runtime-mount-and-attribution-boundary.md' `
            -or $engineeringContract.artifacts.api2IndependentBoardTrafficAttributionReport -cne 'reports/independent-board/api2-independent-board-24h-traffic-attribution-20260721.json' `
            -or $engineeringContract.artifacts.hostUpgradeDemandIndependentBoardAttribution -cne 'docs/independent-board/HOST-UPGRADE-DEMAND-INDEPENDENT-BOARD-ATTRIBUTION.md' `
            -or $contractW4b.state -cne 'w4b1_internal_oauth_chain_verified_w4b2_default_off_runtime_candidate_verified_local' `
            -or $contractW4b.nextSlice -cne $canonicalNextSliceId `
            -or $contractW4b.detailedUiPrototype.implementationState -cne 'default_off_dynamic_menu_and_router_runtime_candidate_verified_local_without_production_activation_public_routes_or_write_actions' `
            -or $contractW4b.detailedUiPrototype.runtimeMountVerificationReport -cne 'reports/independent-board/w4b2c-default-off-runtime-mount-verification-20260721.json' `
            -or -not (@($engineeringContract.contracts.postListingObservationGate.noCrossLayerInference) -contains 'zero_attributable_target_signal_to_zero_actual_usage')) {
        throw 'FBS engineering contract drifted from the W3h/W3i/W3j or W4b.2c evidence boundary'
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
        Invoke-FrontendNpm -Arguments @('run', 'verify:independent-board-w4b2c-runtime-mount')
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
    releaseBlocker = 'DATABASE_TRANSACTIONS_SKILL_CONSUME_V2_AND_MENU_MIGRATIONS_ARE_SEPARATE_REQUIRED_COMMANDS'
    requiredReleaseCompanionCommands = @(
        'database-transactions',
        'database-skill-consume-credit-v2',
        'database-menu-migrations')
    scratchEvidenceCommands = @('database-live', 'database-concurrency')
    menuMigrationsVerified = $false
} | ConvertTo-Json -Depth 4 -Compress
