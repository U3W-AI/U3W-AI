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
        $currentSharedSourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $RepoRoot $expectedSharedSource.path)).Hash.ToLowerInvariant()
        $boundSourceHash = $w3iLineageReport.sourceSha256.PSObject.Properties[$expectedSharedSource.path].Value
        if ($lineageReceipts.Count -ne 1 `
                -or $lineageReceipts[0].predecessorSourceSha256 -cne $expectedSharedSource.predecessor `
                -or $lineageReceipts[0].successorSourceSha256 -cne $currentSharedSourceHash `
                -or [string]::IsNullOrWhiteSpace($lineageReceipts[0].predecessorRegressionVerifier) `
                -or $lineageReceipts[0].predecessorRegressionState -cne 'PASS' `
                -or $boundSourceHash -cne $currentSharedSourceHash) {
            throw "W3i shared-source evidence succession is incomplete or drifted: $($expectedSharedSource.path)"
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
    $w3Wave = @($taskboard.waves | Where-Object { $_.id -ceq 'W3_ADMIN_PORTAL' })
    $w4Wave = @($taskboard.waves | Where-Object { $_.id -ceq 'W4_OAUTH_CONNECTOR' })
    if ($implementationStatus.platformVersion -cne '0.4.7-dev' `
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
            -or $w3Wave.Count -ne 1 `
            -or $w3Wave[0].state -cne 'w3h_plan_policy_w3i_meeting_audit_and_w3j_credit_activation_readiness_verified_local_default_off' `
            -or $w3Wave[0].activeSlice -cne $canonicalW3NextSliceId `
            -or $implementationStatus.w4b.runtimeMountVerificationReport -cne 'reports/independent-board/w4b2c-default-off-runtime-mount-verification-20260721.json' `
            -or $implementationStatus.w4b.api2TrafficAttributionReport -cne 'reports/independent-board/api2-independent-board-24h-traffic-attribution-20260721.json' `
            -or $implementationStatus.w4b.nextSlice -cne $canonicalNextSliceId `
            -or $w4Wave.Count -ne 1 `
            -or $w4Wave[0].state -cne 'w4a_verified_local_w4b1_internal_oauth_chain_verified_w4b2_default_off_runtime_candidate_verified_local' `
            -or $w4Wave[0].activeSlice -cne $canonicalNextSliceId `
            -or $w4Wave[0].nextSlice.id -cne $canonicalNextSliceId) {
        throw 'W3h/W3i/W3j or W4b.2c status and taskboard traceability drifted'
    }

    $engineeringContract = Read-Utf8Json -RelativePath '.fbs-engineering\contract.json'
    $contractW4b = $engineeringContract.contracts.uiPrototypeGate.w4bImplementation
    $contractW3h = $engineeringContract.contracts.uiPrototypeGate.w3hImplementation
    $contractW3i = $engineeringContract.contracts.uiPrototypeGate.w3iImplementation
    $contractW3j = $engineeringContract.contracts.uiPrototypeGate.w3jImplementation
    if ($engineeringContract.artifacts.w3hPlanPolicyContract -cne 'docs/independent-board/W3H-PLAN-POLICY-REVISION-CONTRACT.md' `
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
    releaseBlocker = 'DATABASE_TRANSACTIONS_AND_MENU_MIGRATIONS_ARE_SEPARATE_REQUIRED_COMMANDS'
    requiredReleaseCompanionCommands = @('database-transactions', 'database-menu-migrations')
    scratchEvidenceCommands = @('database-live', 'database-concurrency')
    menuMigrationsVerified = $false
} | ConvertTo-Json -Depth 4 -Compress
