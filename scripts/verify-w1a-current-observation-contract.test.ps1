param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$tempRoot = Join-Path $tempBase (
    'w1a-observation-verifier-{0}' -f [Guid]::NewGuid().ToString('N'))

function Copy-ContractFile([string]$RelativePath) {
    $source = Join-Path $RepoRoot $RelativePath
    $target = Join-Path $tempRoot $RelativePath
    $targetDirectory = Split-Path $target -Parent
    New-Item -ItemType Directory -Path $targetDirectory -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
}

function Get-TestBytesSha256([byte[]]$Bytes) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        return -join (
            $algorithm.ComputeHash($Bytes) |
                ForEach-Object { $_.ToString('x2') })
    }
    finally {
        $algorithm.Dispose()
    }
}

try {
    $status = Get-Content -Raw -Encoding UTF8 -LiteralPath (
        Join-Path $RepoRoot 'docs/independent-board/implementation-status.json'
    ) | ConvertFrom-Json
    $observationPath = [string]$status.w1a.currentObservationReport
    $auditPath = [string]$status.w1a.crossServiceSignalAudit
    $capturePath = [string]$status.w1a.api2LiveSignalCapture
    $reviewCandidatePath = [string]$status.w1a.api2ReviewCandidateReceipt
    $probeReleaseGatePlanPath = [string]$status.w1a.api2ProbeReleaseGatePlanReceipt
    $adminReceiptCandidatePath = [string]$status.w1a.adminReceiptReadbackCandidate.receipt
    if ([string]::IsNullOrWhiteSpace($reviewCandidatePath)) {
        throw 'status is missing the API2 review-candidate receipt path'
    }
    if ([string]::IsNullOrWhiteSpace($probeReleaseGatePlanPath)) {
        throw 'status is missing the API2 probe release-gate plan receipt path'
    }
    if ([string]::IsNullOrWhiteSpace($adminReceiptCandidatePath)) {
        throw 'status is missing the admin receipt-readback candidate path'
    }

    @(
        '.fbs-engineering/contract.json',
        'docs/independent-board/implementation-status.json',
        'docs/independent-board/taskboard.json',
        'docs/independent-board/W1-CURRENT-OBSERVATION-STATUS-20260725.md',
        'scripts/verify-w1a-current-observation.ps1',
        $observationPath,
        $auditPath,
        $capturePath,
        $reviewCandidatePath,
        $probeReleaseGatePlanPath,
        $adminReceiptCandidatePath
    ) | ForEach-Object { Copy-ContractFile $_ }

    $verifier = Join-Path $tempRoot (
        'scripts/verify-w1a-current-observation.ps1')
    $baselineOutput = @(
        & powershell -NoProfile -ExecutionPolicy Bypass -File $verifier `
            -RepoRoot $tempRoot 2>&1
    )
    if ($LASTEXITCODE -ne 0) {
        throw "baseline verifier failed: $($baselineOutput -join [Environment]::NewLine)"
    }

    $reviewCandidateTarget = Join-Path $tempRoot $reviewCandidatePath
    $reviewCandidate = Get-Content -Raw -Encoding UTF8 -LiteralPath $reviewCandidateTarget |
        ConvertFrom-Json
    $reviewCandidate.releaseBoundary.deploymentState = 'deployed'
    $reviewCandidate | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $reviewCandidateTarget
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $candidatePromotionOutput = @(
            & powershell -NoProfile -ExecutionPolicy Bypass -File $verifier `
                -RepoRoot $tempRoot 2>&1
        )
        $candidatePromotionExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if (
        $candidatePromotionExitCode -eq 0 -or
        ($candidatePromotionOutput -join [Environment]::NewLine) -notmatch
            'review candidate was promoted'
    ) {
        throw 'verifier accepted a packaged API2 review candidate as deployed'
    }
    Copy-Item -LiteralPath (Join-Path $RepoRoot $reviewCandidatePath) `
        -Destination $reviewCandidateTarget -Force

    $probeReleaseGatePlanTarget = Join-Path $tempRoot $probeReleaseGatePlanPath
    $probeReleaseGatePlan = Get-Content -Raw -Encoding UTF8 -LiteralPath `
        $probeReleaseGatePlanTarget | ConvertFrom-Json
    $probeReleaseGatePlan.releaseBoundary.candidateDeployed = $true
    $probeReleaseGatePlan | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $probeReleaseGatePlanTarget
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $probeReleaseGatePromotionOutput = @(
            & powershell -NoProfile -ExecutionPolicy Bypass -File $verifier `
                -RepoRoot $tempRoot 2>&1
        )
        $probeReleaseGatePromotionExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if (
        $probeReleaseGatePromotionExitCode -eq 0 -or
        ($probeReleaseGatePromotionOutput -join [Environment]::NewLine) -notmatch
            'probe release-gate candidate was promoted'
    ) {
        throw 'verifier accepted a probe release-gate candidate as deployed'
    }
    Copy-Item -LiteralPath (Join-Path $RepoRoot $probeReleaseGatePlanPath) `
        -Destination $probeReleaseGatePlanTarget -Force

    $adminReceiptCandidateTarget = Join-Path $tempRoot $adminReceiptCandidatePath
    $adminReceiptCandidate = Get-Content -Raw -Encoding UTF8 -LiteralPath `
        $adminReceiptCandidateTarget | ConvertFrom-Json
    $adminReceiptCandidate.releaseBoundary.productCreditPromotion = 'eligible'
    $adminReceiptCandidate | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $adminReceiptCandidateTarget
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $adminReceiptPromotionOutput = @(
            & powershell -NoProfile -ExecutionPolicy Bypass -File $verifier `
                -RepoRoot $tempRoot 2>&1
        )
        $adminReceiptPromotionExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if (
        $adminReceiptPromotionExitCode -eq 0 -or
        ($adminReceiptPromotionOutput -join [Environment]::NewLine) -notmatch
            'admin receipt candidate promoted product credit'
    ) {
        throw 'verifier accepted an admin receipt candidate with promoted credit'
    }
    Copy-Item -LiteralPath (Join-Path $RepoRoot $adminReceiptCandidatePath) `
        -Destination $adminReceiptCandidateTarget -Force

    $observationTarget = Join-Path $tempRoot $observationPath
    $auditTarget = Join-Path $tempRoot $auditPath
    $taskboardTarget = Join-Path $tempRoot (
        'docs/independent-board/taskboard.json')
    $observation = Get-Content -Raw -Encoding UTF8 `
        -LiteralPath $observationTarget | ConvertFrom-Json
    $observation.api2.independentBoardDiagnosticSignal.observed = $true
    $observation.proves += (
        'api2_independent_board_diagnostic_three_stage_signal_quarantined')
    $observation | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $observationTarget
    $audit = Get-Content -Raw -Encoding UTF8 -LiteralPath $auditTarget |
        ConvertFrom-Json
    $audit.signalVerdict.independentBoardMarkerObserved = $true
    $audit.signalVerdict.diagnosticThreeStageChainObserved = $true
    $audit | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $auditTarget
    $taskboard = Get-Content -Raw -Encoding UTF8 `
        -LiteralPath $taskboardTarget | ConvertFrom-Json
    $taskboardWave = @(
        $taskboard.waves |
            Where-Object {
                $_.id -ceq
                    'W1_OFFICIAL_EXPERTS_SERVICE_ATTRIBUTION_INTENT_CLOSURE'
            } |
            Select-Object -First 1
    )[0]
    $taskboardWave.completedSubset += (
        'cross_service_signal_audit_proves_api2_diagnostic_binding_differs_from_u3w_probe_binding')
    $taskboard | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $taskboardTarget

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $promotionOutput = @(
            & powershell -NoProfile -ExecutionPolicy Bypass -File $verifier `
                -RepoRoot $tempRoot 2>&1
        )
        $promotionExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if (
        $promotionExitCode -eq 0 -or
        ($promotionOutput -join [Environment]::NewLine) -notmatch
            'record-only runtime projection was promoted'
    ) {
        throw 'verifier accepted promotion of a record-only runtime projection'
    }

    Copy-Item -LiteralPath (Join-Path $RepoRoot $observationPath) `
        -Destination $observationTarget -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot $auditPath) `
        -Destination $auditTarget -Force
    Copy-Item -LiteralPath (
        Join-Path $RepoRoot 'docs/independent-board/taskboard.json'
    ) -Destination $taskboardTarget -Force

    $audit = Get-Content -Raw -Encoding UTF8 -LiteralPath $auditTarget |
        ConvertFrom-Json
    $audit.crossServiceJoin.sameBindingProven = $true
    $audit.crossServiceJoin.status = 'joined'
    $audit | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $auditTarget

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $driftOutput = @(
            & powershell -NoProfile -ExecutionPolicy Bypass -File $verifier `
                -RepoRoot $tempRoot 2>&1
        )
        $driftExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($driftExitCode -eq 0) {
        throw 'verifier accepted a cross-service audit with sameBindingProven=true'
    }

    $audit.crossServiceJoin.sameBindingProven = $false
    $audit.crossServiceJoin.status = 'not_proven_record_only_collector_claim'
    $audit | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $auditTarget
    $captureTarget = Join-Path $tempRoot $capturePath
    $capture = Get-Content -Raw -Encoding UTF8 -LiteralPath $captureTarget |
        ConvertFrom-Json
    $capture.httpCaptures.health.rawBodySha256 = ('0' * 64)
    $capture | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $captureTarget

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $captureDriftOutput = @(
            & powershell -NoProfile -ExecutionPolicy Bypass -File $verifier `
                -RepoRoot $tempRoot 2>&1
        )
        $captureDriftExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($captureDriftExitCode -eq 0) {
        throw 'verifier accepted a capture with a drifted health body digest'
    }

    Copy-Item -LiteralPath (Join-Path $RepoRoot $capturePath) `
        -Destination $captureTarget -Force
    $capture = Get-Content -Raw -Encoding UTF8 -LiteralPath $captureTarget |
        ConvertFrom-Json
    $capture.selectedProjection.sourceRowCount = (
        [int]$capture.selectedProjection.sourceRowCount + 1)
    $capture | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $captureTarget
    $driftedCaptureSha = (
        Get-FileHash -LiteralPath $captureTarget -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $observation = Get-Content -Raw -Encoding UTF8 `
        -LiteralPath $observationTarget | ConvertFrom-Json
    $observation.api2.immutableCapture.sha256 = $driftedCaptureSha
    $observation | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $observationTarget
    $audit = Get-Content -Raw -Encoding UTF8 -LiteralPath $auditTarget |
        ConvertFrom-Json
    $audit.api2Service.immutableCapture.sha256 = $driftedCaptureSha
    $audit | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $auditTarget

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $projectionDriftOutput = @(
            & powershell -NoProfile -ExecutionPolicy Bypass -File $verifier `
                -RepoRoot $tempRoot 2>&1
        )
        $projectionDriftExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($projectionDriftExitCode -eq 0) {
        throw 'verifier accepted a capture with a drifted selected projection'
    }

    Copy-Item -LiteralPath (Join-Path $RepoRoot $capturePath) `
        -Destination $captureTarget -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot $observationPath) `
        -Destination $observationTarget -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot $auditPath) `
        -Destination $auditTarget -Force
    $capture = Get-Content -Raw -Encoding UTF8 -LiteralPath $captureTarget |
        ConvertFrom-Json
    $capture.captureBoundary.runtimeStateProjectionAuthority = (
        'independently_attested')
    $capture.runtimeStateCapture.projectionAuthority = (
        'independently_attested')
    $capture | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $captureTarget
    $authorityCaptureSha = (
        Get-FileHash -LiteralPath $captureTarget -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $observation = Get-Content -Raw -Encoding UTF8 `
        -LiteralPath $observationTarget | ConvertFrom-Json
    $observation.api2.immutableCapture.sha256 = $authorityCaptureSha
    $observation.api2.immutableCapture.runtimeStateProjectionAuthority = (
        'independently_attested')
    $observation | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $observationTarget
    $audit = Get-Content -Raw -Encoding UTF8 -LiteralPath $auditTarget |
        ConvertFrom-Json
    $audit.api2Service.immutableCapture.sha256 = $authorityCaptureSha
    $audit.api2Service.runtimeMarkerScan.projectionAuthority = (
        'independently_attested')
    $audit | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $auditTarget

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $authorityOutput = @(
            & powershell -NoProfile -ExecutionPolicy Bypass -File $verifier `
                -RepoRoot $tempRoot 2>&1
        )
        $authorityExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if (
        $authorityExitCode -eq 0 -or
        ($authorityOutput -join [Environment]::NewLine) -notmatch
            'projection authority was overstated'
    ) {
        throw 'verifier accepted an overstated runtime projection authority'
    }

    Copy-Item -LiteralPath (Join-Path $RepoRoot $capturePath) `
        -Destination $captureTarget -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot $observationPath) `
        -Destination $observationTarget -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot $auditPath) `
        -Destination $auditTarget -Force
    [byte[]]$expandedBomb = [byte[]]::new((5 * 1024 * 1024) + 1)
    $bombStream = [IO.MemoryStream]::new()
    try {
        $gzip = [IO.Compression.GzipStream]::new(
            $bombStream,
            [IO.Compression.CompressionLevel]::Optimal,
            $true
        )
        try {
            $gzip.Write($expandedBomb, 0, $expandedBomb.Length)
        }
        finally {
            $gzip.Dispose()
        }
        [byte[]]$compressedBomb = $bombStream.ToArray()
    }
    finally {
        $bombStream.Dispose()
    }
    $capture = Get-Content -Raw -Encoding UTF8 -LiteralPath $captureTarget |
        ConvertFrom-Json
    $capture.httpCaptures.health.storedBodyBase64 = (
        [Convert]::ToBase64String($compressedBomb))
    $capture.httpCaptures.health.storedBodyBytes = $compressedBomb.Length
    $capture.httpCaptures.health.storedBodySha256 = (
        Get-TestBytesSha256 $compressedBomb)
    $capture.httpCaptures.health.rawBodyBytes = 1
    $capture | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $captureTarget
    $bombCaptureSha = (
        Get-FileHash -LiteralPath $captureTarget -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $observation = Get-Content -Raw -Encoding UTF8 `
        -LiteralPath $observationTarget | ConvertFrom-Json
    $observation.api2.immutableCapture.sha256 = $bombCaptureSha
    $observation | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $observationTarget
    $audit = Get-Content -Raw -Encoding UTF8 -LiteralPath $auditTarget |
        ConvertFrom-Json
    $audit.api2Service.immutableCapture.sha256 = $bombCaptureSha
    $audit | ConvertTo-Json -Depth 100 |
        Set-Content -Encoding UTF8 -LiteralPath $auditTarget

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $bombOutput = @(
            & powershell -NoProfile -ExecutionPolicy Bypass -File $verifier `
                -RepoRoot $tempRoot 2>&1
        )
        $bombExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if (
        $bombExitCode -eq 0 -or
        ($bombOutput -join [Environment]::NewLine) -notmatch
            'decompressed body exceeds the size limit'
    ) {
        throw 'verifier did not stop a gzip expansion at the decompression limit'
    }

    [pscustomobject]@{
        schema = 'fbsir.w1aCurrentObservationVerifierContractTest.v1'
        status = 'PASS'
        baselineAccepted = $true
        recordOnlyProjectionPromotionRejected = $true
        crossServiceBindingDriftRejected = $true
        captureBodyDigestDriftRejected = $true
        captureProjectionDriftRejected = $true
        runtimeProjectionAuthorityEscalationRejected = $true
        gzipExpansionStoppedAtLimit = $true
        packagedReviewCandidatePromotionRejected = $true
        probeReleaseGateCandidatePromotionRejected = $true
        adminReceiptCandidateCreditPromotionRejected = $true
    } | ConvertTo-Json -Depth 4
}
finally {
    $resolvedTemp = [IO.Path]::GetFullPath($tempRoot)
    if (
        $resolvedTemp.StartsWith(
            $tempBase,
            [StringComparison]::OrdinalIgnoreCase
        ) -and
        (Split-Path $resolvedTemp -Leaf).StartsWith(
            'w1a-observation-verifier-',
            [StringComparison]::Ordinal
        )
    ) {
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force `
            -ErrorAction SilentlyContinue
    }
}
