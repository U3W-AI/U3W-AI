param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

function Read-Json([string]$RelativePath) {
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "required observation artifact is missing: $RelativePath"
    }
    return Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Read-Text([string]$RelativePath) {
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "required observation artifact is missing: $RelativePath"
    }
    return Get-Content -LiteralPath $path -Raw -Encoding UTF8
}

function Get-FileSha256([string]$RelativePath) {
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "required observation artifact is missing: $RelativePath"
    }
    return (Get-FileHash -LiteralPath $path -Algorithm SHA256).
        Hash.ToLowerInvariant()
}

function Get-BytesSha256([byte[]]$Bytes) {
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

function Expand-GzipBase64([string]$Value, [int]$MaxRawBytes) {
    [byte[]]$stored = [Convert]::FromBase64String($Value)
    $input = [IO.MemoryStream]::new($stored, $false)
    $output = [IO.MemoryStream]::new()
    [byte[]]$buffer = [byte[]]::new(8192)
    [int]$total = 0
    try {
        $gzip = [IO.Compression.GzipStream]::new(
            $input,
            [IO.Compression.CompressionMode]::Decompress,
            $false
        )
        try {
            while (($read = $gzip.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $total += $read
                if ($total -gt $MaxRawBytes) {
                    throw 'decompressed body exceeds the size limit'
                }
                $output.Write($buffer, 0, $read)
            }
        }
        finally {
            $gzip.Dispose()
        }
        return [pscustomobject]@{
            stored = $stored
            raw = $output.ToArray()
        }
    }
    finally {
        $output.Dispose()
        $input.Dispose()
    }
}

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

$status = Read-Json 'docs/independent-board/implementation-status.json'
$contract = Read-Json '.fbs-engineering/contract.json'
$taskboard = Read-Json 'docs/independent-board/taskboard.json'
$w1Wave = @(
    $taskboard.waves |
        Where-Object {
            $_.id -ceq 'W1_OFFICIAL_EXPERTS_SERVICE_ATTRIBUTION_INTENT_CLOSURE'
        } |
        Select-Object -First 1
)
Require ($w1Wave.Count -eq 1) 'taskboard W1 official experts wave is missing'
$reportPath = [string]$status.w1a.currentObservationReport
Require (-not [string]::IsNullOrWhiteSpace($reportPath)) 'status has no currentObservationReport'
Require ([string]$contract.artifacts.w1aCurrentObservationReport -ceq $reportPath) 'contract/status current observation path drifted'
Require ([string]$w1Wave[0].currentObservationReport -ceq $reportPath) 'taskboard/status current observation path drifted'
$observation = Read-Json $reportPath
$auditPath = [string]$status.w1a.crossServiceSignalAudit
Require (-not [string]::IsNullOrWhiteSpace($auditPath)) 'status has no crossServiceSignalAudit'
Require ([string]$contract.artifacts.w1aCrossServiceSignalAudit -ceq $auditPath) 'contract/status cross-service audit path drifted'
Require ([string]$w1Wave[0].crossServiceSignalAudit -ceq $auditPath) 'taskboard/status cross-service audit path drifted'
$audit = Read-Json $auditPath
$capturePath = [string]$status.w1a.api2LiveSignalCapture
Require (-not [string]::IsNullOrWhiteSpace($capturePath)) 'status has no api2LiveSignalCapture'
Require ([string]$contract.artifacts.w1aApi2LiveSignalCapture -ceq $capturePath) 'contract/status API2 capture path drifted'
Require ([string]$w1Wave[0].api2LiveSignalCapture -ceq $capturePath) 'taskboard/status API2 capture path drifted'
$capture = Read-Json $capturePath
$captureSha256 = Get-FileSha256 $capturePath
$reviewCandidatePath = [string]$status.w1a.api2ReviewCandidateReceipt
Require (-not [string]::IsNullOrWhiteSpace($reviewCandidatePath)) 'status has no API2 review-candidate receipt'
Require ([string]$contract.artifacts.w1eApi2ConnectorReviewCandidateReceipt -ceq $reviewCandidatePath) 'contract/status API2 review-candidate receipt path drifted'
Require ([string]$w1Wave[0].api2ReviewCandidateReceipt -ceq $reviewCandidatePath) 'taskboard/status API2 review-candidate receipt path drifted'
$reviewCandidate = Read-Json $reviewCandidatePath
$adminReceiptCandidatePath = [string]$status.w1a.adminReceiptReadbackCandidate.receipt
Require (-not [string]::IsNullOrWhiteSpace($adminReceiptCandidatePath)) 'status has no admin receipt-readback candidate receipt'
Require ([string]$contract.artifacts.w1eAdminReceiptReadbackCandidateReceipt -ceq $adminReceiptCandidatePath) 'contract/status admin receipt-readback candidate path drifted'
Require ([string]$w1Wave[0].adminReceiptReadbackCandidate.receipt -ceq $adminReceiptCandidatePath) 'taskboard/status admin receipt-readback candidate path drifted'
$adminReceiptCandidate = Read-Json $adminReceiptCandidatePath
$memoPath = [string]$status.w1a.currentObservationStatusMemo
Require (-not [string]::IsNullOrWhiteSpace($memoPath)) 'status has no current observation memo'
$memo = Read-Text $memoPath

Require ($observation.schema -ceq 'fbsir.independentBoardW1aCurrentObservation.v1') 'current observation schema mismatch'
Require ($observation.authorization -ceq 'read_only_production_and_repo_facts') 'current observation is not read-only'
Require ($observation.officialIdentity.productId -ceq 'fbsir-eight-seat-board') 'official product identity mismatch'
Require ($observation.officialIdentity.listedManifestVersion -ceq '26.7.21') 'listed manifest version mismatch'
Require ($observation.officialIdentity.embeddedContractVersion -ceq '26.7.20') 'embedded contract version mismatch'
Require ($observation.officialIdentity.packageFrozen -eq $true) 'official package freeze is not asserted'

Require ($reviewCandidate.schema -ceq 'fbsir.independentBoardApi2ConnectorReviewCandidate.v1') 'API2 review-candidate schema mismatch'
Require ($reviewCandidate.status -ceq 'PACKAGED_REVIEW_CANDIDATE_NOT_DEPLOYED') 'API2 review candidate status drifted'
Require ($reviewCandidate.officialIdentity.productId -ceq 'fbsir-eight-seat-board') 'API2 review candidate product identity mismatch'
Require ($reviewCandidate.officialIdentity.packageVersion -ceq '26.7.21') 'API2 review candidate package version mismatch'
Require ($reviewCandidate.source.branch -ceq 'codex/w1-trusted-ingress-attestation') 'API2 review candidate branch drifted'
Require ($reviewCandidate.source.commit -ceq '53b337ac3b65c766ba4dabc460ce59ec7fdf2de8') 'API2 review candidate commit drifted'
Require ($reviewCandidate.source.remoteHead -ceq $reviewCandidate.source.commit) 'API2 review candidate remote HEAD drifted'
Require ($reviewCandidate.source.remoteAligned -eq $true) 'API2 review candidate remote alignment is missing'
Require ($reviewCandidate.reviewBuild.recommendedSubmissionZip.path -ceq 'dist/review/fbs-connector-26.7.2-R.zip') 'API2 review candidate submission ZIP drifted'
Require ($reviewCandidate.reviewBuild.connectorIconSha256 -ceq '060a6394371864e29689116a087fc5f0e66ecd3cf50d0b67b6e593633a53310e') 'API2 review candidate icon digest drifted'
Require ($reviewCandidate.releaseBoundary.deploymentState -ceq 'not_deployed') 'API2 review candidate was promoted'
Require ($reviewCandidate.releaseBoundary.productionReleaseCommit -ceq [string]$status.w1a.api2SourceTruth.productionDeclaredCommit) 'API2 review candidate production commit drifted'
Require ($reviewCandidate.releaseBoundary.officialExpertsPackageChanged -eq $false) 'API2 review candidate changed the official experts package'
Require ($reviewCandidate.releaseBoundary.hostChanged -eq $false) 'API2 review candidate changed the host'
Require ($reviewCandidate.releaseBoundary.productCreditPromotion -ceq 'blocked') 'API2 review candidate promoted product credit'
Require ($reviewCandidate.verification.localApi2ToMockU3w.status -ceq 'pass') 'API2 review candidate mock U3W verification failed'
Require ($reviewCandidate.verification.localApi2ToMockU3w.productCreditEligible -eq $false) 'API2 review candidate mock U3W verification promoted credit'
Require ([string]$status.w1a.api2SourceTruth.reviewCandidate.receipt -ceq $reviewCandidatePath) 'status review-candidate receipt drifted'
Require ([string]$contract.contracts.currentMainline.api2SourceTruth.reviewCandidate.receipt -ceq $reviewCandidatePath) 'contract review-candidate receipt drifted'
Require ([string]$status.w1a.api2SourceTruth.reviewCandidate.commit -ceq [string]$reviewCandidate.source.commit) 'status review-candidate commit drifted'
Require ([string]$contract.contracts.currentMainline.api2SourceTruth.reviewCandidate.commit -ceq [string]$reviewCandidate.source.commit) 'contract review-candidate commit drifted'

Require ($adminReceiptCandidate.schema -ceq 'fbsir.independentBoardAdminReceiptReadbackCandidate.v1') 'admin receipt candidate schema mismatch'
Require ($adminReceiptCandidate.status -ceq 'LOCAL_SOURCE_CANDIDATE_NOT_DEPLOYED') 'admin receipt candidate status drifted'
Require ($adminReceiptCandidate.scope -ceq 'fbs_service_side') 'admin receipt candidate owner surface drifted'
Require ($adminReceiptCandidate.officialIdentity.productId -ceq 'fbsir-eight-seat-board') 'admin receipt candidate product identity mismatch'
Require ($adminReceiptCandidate.officialIdentity.packageVersion -ceq '26.7.21') 'admin receipt candidate package version mismatch'
Require ($adminReceiptCandidate.source.branch -ceq 'codex/w1-natural-ingress-fence') 'admin receipt candidate branch drifted'
Require ($adminReceiptCandidate.source.commit -ceq '54bf918034e96fb7de483e371757cb8f51fe40b1') 'admin receipt candidate commit drifted'
Require ($adminReceiptCandidate.releaseBoundary.deploymentState -ceq 'not_deployed') 'admin receipt candidate was promoted'
Require ($adminReceiptCandidate.releaseBoundary.officialExpertsPackageChanged -eq $false) 'admin receipt candidate changed the official experts package'
Require ($adminReceiptCandidate.releaseBoundary.hostChanged -eq $false) 'admin receipt candidate changed the host'
Require ($adminReceiptCandidate.releaseBoundary.productCreditPromotion -ceq 'blocked') 'admin receipt candidate promoted product credit'
Require ($adminReceiptCandidate.readContract.transaction -ceq 'read_only') 'admin receipt candidate is not read-only'
Require ($adminReceiptCandidate.readContract.cacheControl -ceq 'no-store') 'admin receipt candidate cache boundary drifted'
Require ($adminReceiptCandidate.readContract.neverExposed -contains 'sameBindingKey') 'admin receipt candidate exposes raw binding key'
Require ([string]$contract.contracts.w1eAdminReceiptReadbackCandidate.receipt -ceq $adminReceiptCandidatePath) 'contract admin receipt candidate receipt drifted'
Require ([string]$status.w1a.adminReceiptReadbackCandidate.commit -ceq [string]$adminReceiptCandidate.source.commit) 'status admin receipt candidate commit drifted'
Require ([string]$contract.contracts.w1eAdminReceiptReadbackCandidate.commit -ceq [string]$adminReceiptCandidate.source.commit) 'contract admin receipt candidate commit drifted'

Require ($observation.u3w.serviceState -ceq 'active') 'U3W service is not active in current observation'
# The timestamped observation is immutable. A later legitimate cutover may advance
# current source truth, so the snapshot must be joined to its own audit/capture
# below instead of being forced to equal the mutable current HEAD.
Require ([string]$observation.u3w.jarSha256 -cmatch '^[0-9a-f]{64}$') 'U3W observation JAR hash is invalid'
Require ($observation.u3w.observationFlags.writer -eq $true) 'U3W observation writer is not active'
Require ($observation.u3w.observationFlags.intentClassifier -eq $true) 'U3W intent classifier is not active'
Require ($observation.u3w.observationFlags.adminRead -eq $true) 'U3W admin read is not active'
Require ($observation.u3w.observationFlags.productCredit -eq $false) 'U3W product credit gate is open'
Require ($observation.u3w.observationFlags.publicRoute -eq $false) 'U3W public route gate is open'
Require ($observation.u3w.ledger.journeyCount -eq 1) 'unexpected U3W journey count'
Require ($observation.u3w.ledger.eventCount -eq 3) 'unexpected U3W event count'
Require ($observation.u3w.ledger.naturalJourneyCount -eq 0) 'U3W natural journey denominator is nonzero'
Require ($observation.u3w.ledger.naturalEventCount -eq 0) 'U3W natural event denominator is nonzero'
Require ($observation.u3w.ledger.probeJourneyCount -eq 1) 'U3W probe journey count drifted'
Require ($observation.u3w.ledger.probeEventCount -eq 3) 'U3W probe event count drifted'
Require ((@($observation.u3w.ledger.sequence) -join ',') -ceq 'ENTRY_OBSERVED,INTENT_CLASSIFIED,FIRST_VALUE_COMPLETED') 'U3W event sequence mismatch'
Require ($observation.u3w.adminReadback.summaryRoute -ceq 'available_but_unauthenticated_read_returns_401_no_store') 'U3W admin readback boundary drifted'
Require ($observation.u3w.adminReadback.realPostAppendObserved -eq $false) 'U3W current observation unexpectedly contains a write'

Require ($observation.api2.healthHttpStatus -eq 200) 'API2 health is not HTTP 200'
Require ([string]$observation.api2.sourceGitHead -cmatch '^[0-9a-f]{40}$') 'API2 observation source head is invalid'
Require ($observation.api2.publisher.status -ceq 'ready') 'API2 publisher is not ready'
Require ($observation.api2.publisher.enabled -eq $true) 'API2 publisher is disabled'
Require ($observation.api2.publisher.workerRunning -eq $true) 'API2 publisher worker is not running'
Require ($observation.api2.publisher.exactOfficialIdentityConfigured -eq $true) 'API2 official identity is not configured'
Require ($observation.api2.publisher.strictOutboxDurabilityConfirmed -eq $false) 'API2 strict outbox state unexpectedly changed'
Require ($observation.api2.publisher.pendingRecords -eq 0) 'API2 publisher has pending records'
Require ($observation.api2.publisher.deliveredRecords -eq 0) 'API2 publisher has delivered records in current observation'
Require ($observation.api2.publisher.productCreditPromoted -eq $false) 'API2 product credit was promoted'
Require ($observation.api2.hostListingReceipt.status -ceq 'not_ready') 'Host listing receipt gate unexpectedly opened'
Require ($observation.api2.hostForwardingAck.status -ceq 'observe_only_not_ready') 'Host forwarding gate unexpectedly opened'
Require ($observation.api2.hostForwardingAck.productionReadyFlag -eq $false) 'Host forwarding production-ready flag is open'

Require ($observation.api2.independentBoardDiagnosticSignal.observed -eq $false) 'record-only runtime projection was promoted'
Require ($observation.api2.independentBoardDiagnosticSignal.collectorClaimRecorded -eq $true) 'API2 runtime collector claim is missing'
Require ([string]$observation.api2.independentBoardDiagnosticSignal.authority -ceq 'collector_claim_record_only') 'API2 diagnostic collector authority drifted'
Require ($observation.api2.independentBoardDiagnosticSignal.trafficClass -ceq 'DIAGNOSTIC') 'API2 diagnostic signal traffic class drifted'
Require ($observation.api2.independentBoardDiagnosticSignal.isSynthetic -eq $true) 'API2 diagnostic signal lost its synthetic quarantine'
Require ($observation.api2.independentBoardDiagnosticSignal.effectiveEvidenceClass -ceq 'SYNTHETIC') 'API2 diagnostic signal fail-closed precedence drifted'
Require ($observation.api2.independentBoardDiagnosticSignal.productIdSource -ceq 'tool_argument_untrusted') 'API2 diagnostic product identity was incorrectly promoted'
Require ($observation.api2.independentBoardDiagnosticSignal.packageVersion -ceq '26.7.2') 'API2 diagnostic package version changed'
Require ($observation.api2.independentBoardDiagnosticSignal.exactOfficialIdentityComplete -eq $false) 'API2 diagnostic signal was incorrectly treated as exact official identity'
Require ($observation.api2.independentBoardDiagnosticSignal.officialEntryMarkerCount -eq 0) 'API2 diagnostic signal contains an unverified official-entry marker'
Require ((@($observation.api2.independentBoardDiagnosticSignal.sequence) -join ',') -ceq 'WHOAMI_EMITTED,SCENE_PACK_RESOLVED,FIRST_VALUE_COMPLETED') 'API2 diagnostic signal sequence mismatch'
Require ($observation.crossServiceJoin.collectorClaimMatchesU3wProbeBinding -eq $false) 'API2 collector-claimed and U3W probe digests unexpectedly match'
Require ($observation.crossServiceJoin.sameBindingProven -eq $false) 'record-only runtime projection was promoted'
Require ($observation.crossServiceJoin.differentBindingProven -eq $false) 'record-only runtime projection was promoted'
Require ($observation.crossServiceJoin.status -ceq 'not_proven_record_only_collector_claim') 'cross-service collector-claim boundary drifted'
Require ($observation.crossServiceJoin.publisherDeliveredRecords -eq 0) 'API2 diagnostic signal unexpectedly entered the publisher delivery count'
Require ($observation.crossServiceJoin.u3wSuccessfulPostAppendOrReceiptObserved -eq $false) 'U3W diagnostic-chain receipt was incorrectly asserted'

Require ($observation.decisionBoard.status -ceq 'decision_withheld_no_trusted_natural_denominator') 'decision board was not withheld'
Require ($observation.decisionBoard.window24h.natural -eq 0) 'decision board natural rows are nonzero'
Require ($observation.decisionBoard.window24h.eligibleNaturalSampleCount -eq 0) 'eligible natural sample count is nonzero'
Require ($observation.decisionBoard.window24h.productCreditAllowed -eq $false) 'decision board product credit is allowed'
Require ($observation.connector.boardBindingObserved -eq $false) 'connector was incorrectly treated as board binding'
$observationProves = @($observation.proves | ForEach-Object { [string]$_ })
$recordOnlyClaims = @(
    $observation.recordOnlyCollectorClaims |
        ForEach-Object { [string]$_ }
)
$completedSubset = @(
    $w1Wave[0].completedSubset |
        ForEach-Object { [string]$_ }
)
Require (
    @($observationProves | Where-Object {
        $_ -match 'diagnostic_three_stage_signal|bindings_are_distinct'
    }).Count -eq 0
) 'record-only runtime projection was promoted'
Require (
    $recordOnlyClaims -ccontains
        'api2_independent_board_diagnostic_three_stage_claim_recorded_and_synthetic_quarantined'
) 'record-only API2 diagnostic collector claim is missing'
Require (
    $recordOnlyClaims -ccontains
        'collector_claimed_api2_binding_digest_differs_from_u3w_probe_digest_not_cross_service_proof'
) 'record-only binding comparison claim is missing'
Require (
    @($completedSubset | Where-Object {
        $_ -match 'signal_observed|audit_proves'
    }).Count -eq 0
) 'record-only runtime projection was promoted'
Require (
    $completedSubset -ccontains
        'api2_runtime_collector_claim_records_same_binding_three_stage_diagnostic_but_is_synthetic_untrusted_and_record_only'
) 'taskboard runtime collector-claim completion is missing'
Require (
    $completedSubset -ccontains
        'cross_service_audit_records_collector_claimed_api2_digest_differs_from_u3w_probe_digest_not_cross_service_proof'
) 'taskboard binding collector-claim completion is missing'

Require ($audit.schema -ceq 'fbsir.independentBoardCrossServiceSignalAudit.v1') 'cross-service audit schema mismatch'
Require ($audit.authorization -ceq 'read_only_production_and_repo_facts') 'cross-service audit is not read-only'
Require ($audit.observedAt -ceq $observation.observedAt) 'cross-service audit observation time drifted'
Require ($audit.targetSurface.packageFrozen -eq $true) 'cross-service audit package freeze is not asserted'
Require ($audit.officialIdentity.productId -ceq $observation.officialIdentity.productId) 'cross-service audit product identity drifted'
Require ($audit.officialIdentity.listedManifestVersion -ceq $observation.officialIdentity.listedManifestVersion) 'cross-service audit listed version drifted'
Require ([string]$audit.u3wAttributionService.evidencePath -ceq [string]$observation.u3w.evidencePath) 'cross-service audit U3W evidence path drifted'
Require ([string]$audit.u3wAttributionService.releaseId -ceq [string]$observation.u3w.releaseId) 'cross-service audit U3W release drifted'
Require ([string]$audit.u3wAttributionService.deployedSourceCommit -ceq [string]$observation.u3w.deployedSourceCommit) 'cross-service audit U3W source drifted'
Require ([string]$audit.u3wAttributionService.ledger.bindingDigestSha256Prefix -ceq [string]$observation.u3w.ledger.bindingDigestSha256Prefix) 'cross-service audit U3W binding digest drifted'
Require ([string]$audit.api2Service.release.releaseId -ceq [string]$observation.api2.releaseId) 'cross-service audit API2 release drifted'
Require ([string]$audit.api2Service.release.sourceGitHead -ceq [string]$observation.api2.sourceGitHead) 'cross-service audit API2 source drifted'
Require ([string]$audit.api2Service.runtimeMarkerScan.uniqueDiagnosticChain.bindingDigestSha256Prefix -ceq [string]$observation.api2.independentBoardDiagnosticSignal.bindingDigestSha256Prefix) 'cross-service audit API2 diagnostic binding drifted'
Require ([string]$audit.api2Service.runtimeMarkerScan.uniqueDiagnosticChain.effectiveEvidenceClass -ceq 'synthetic') 'cross-service audit synthetic precedence drifted'
Require ($audit.crossServiceJoin.collectorClaimMatchesU3wProbeBinding -eq $false) 'cross-service audit collector-claimed digest unexpectedly matches'
Require ($audit.crossServiceJoin.sameBindingProven -eq $false) 'record-only runtime projection was promoted'
Require ($audit.crossServiceJoin.differentBindingProven -eq $false) 'record-only runtime projection was promoted'
Require ($audit.crossServiceJoin.status -ceq 'not_proven_record_only_collector_claim') 'cross-service audit join status drifted'
Require ($audit.crossServiceJoin.api2PublisherDeliveredRecords -eq 0) 'cross-service audit publisher delivery count drifted'
Require ($audit.crossServiceJoin.u3wSuccessfulPostAppendOrReceiptObserved -eq $false) 'cross-service audit incorrectly asserts a U3W receipt'
Require ($audit.signalVerdict.independentBoardMarkerObserved -eq $false) 'record-only runtime projection was promoted'
Require ($audit.signalVerdict.diagnosticThreeStageChainObserved -eq $false) 'record-only runtime projection was promoted'
Require ($audit.signalVerdict.collectorClaimIndependentBoardMarkerRecorded -eq $true) 'audit independent-board collector claim is missing'
Require ($audit.signalVerdict.collectorClaimDiagnosticThreeStageChainRecorded -eq $true) 'audit diagnostic-chain collector claim is missing'
Require ($audit.signalVerdict.officialListedNaturalSignalObserved -eq $false) 'cross-service audit promoted an official natural signal'
Require ($audit.signalVerdict.productCreditAllowed -eq $false) 'cross-service audit promoted product credit'
Require ($capture.schema -ceq 'fbsir.api2IndependentBoardLiveSignalCapture.v1') 'API2 capture schema mismatch'
Require ($capture.authorization -ceq 'read_only_production_capture') 'API2 capture is not read-only'
Require ([string]$observation.api2.immutableCapture.path -ceq $capturePath) 'current observation API2 capture path drifted'
Require ([string]$observation.api2.immutableCapture.sha256 -ceq $captureSha256) 'current observation API2 capture file digest drifted'
Require ([string]$audit.api2Service.immutableCapture.path -ceq $capturePath) 'cross-service audit API2 capture path drifted'
Require ([string]$audit.api2Service.immutableCapture.sha256 -ceq $captureSha256) 'cross-service audit API2 capture file digest drifted'
Require ($capture.captureBoundary.publicHttpBodiesStoredLosslessly -eq $true) 'API2 public HTTP bodies are not retained losslessly'
Require ($capture.captureBoundary.runtimeStateWholeFileSha256Stored -eq $true) 'API2 runtime-state digest is missing'
Require ($capture.captureBoundary.rawIdentityValuesStored -eq $false) 'API2 capture contains raw identity values'
Require ($capture.captureBoundary.productCreditAllowed -eq $false) 'API2 capture promoted product credit'
Require ([string]$capture.captureBoundary.runtimeStateProjectionAuthority -ceq 'collector_claim_record_only') 'API2 runtime-state projection authority was overstated'
Require ($capture.captureBoundary.runtimeStateProjectionDerivationProofStored -eq $false) 'API2 runtime-state projection derivation proof was overstated'

$healthCapture = $capture.httpCaptures.health
$boardCapture = $capture.httpCaptures.decisionBoard
$maxStoredBodyBase64Chars = 2 * 1024 * 1024
$maxStoredBodyBytes = 1024 * 1024
$maxRawBodyBytes = 5 * 1024 * 1024
Require ($healthCapture.httpStatus -eq 200) 'captured API2 health status is not 200'
Require ($boardCapture.httpStatus -eq 200) 'captured API2 decision-board status is not 200'
Require ($healthCapture.storedBodyEncoding -ceq 'gzip+base64') 'captured API2 health encoding drifted'
Require ($boardCapture.storedBodyEncoding -ceq 'gzip+base64') 'captured API2 decision-board encoding drifted'
Require (([string]$healthCapture.storedBodyBase64).Length -le $maxStoredBodyBase64Chars) 'captured API2 health body exceeds the encoded size limit'
Require (([string]$boardCapture.storedBodyBase64).Length -le $maxStoredBodyBase64Chars) 'captured API2 decision-board body exceeds the encoded size limit'
Require ($healthCapture.storedBodyBytes -gt 0 -and $healthCapture.storedBodyBytes -le $maxStoredBodyBytes) 'captured API2 health compressed size is invalid'
Require ($boardCapture.storedBodyBytes -gt 0 -and $boardCapture.storedBodyBytes -le $maxStoredBodyBytes) 'captured API2 decision-board compressed size is invalid'
Require ($healthCapture.rawBodyBytes -gt 0 -and $healthCapture.rawBodyBytes -le $maxRawBodyBytes) 'captured API2 health raw size is invalid'
Require ($boardCapture.rawBodyBytes -gt 0 -and $boardCapture.rawBodyBytes -le $maxRawBodyBytes) 'captured API2 decision-board raw size is invalid'
$expandedHealth = Expand-GzipBase64 (
    [string]$healthCapture.storedBodyBase64) $maxRawBodyBytes
$expandedBoard = Expand-GzipBase64 (
    [string]$boardCapture.storedBodyBase64) $maxRawBodyBytes
Require ($expandedHealth.stored.Length -eq $healthCapture.storedBodyBytes) 'captured API2 health compressed byte count drifted'
Require ((Get-BytesSha256 $expandedHealth.stored) -ceq [string]$healthCapture.storedBodySha256) 'captured API2 health compressed digest drifted'
Require ($expandedHealth.raw.Length -eq $healthCapture.rawBodyBytes) 'captured API2 health raw byte count drifted'
Require ((Get-BytesSha256 $expandedHealth.raw) -ceq [string]$healthCapture.rawBodySha256) 'captured API2 health raw digest drifted'
Require ($expandedBoard.stored.Length -eq $boardCapture.storedBodyBytes) 'captured API2 decision-board compressed byte count drifted'
Require ((Get-BytesSha256 $expandedBoard.stored) -ceq [string]$boardCapture.storedBodySha256) 'captured API2 decision-board compressed digest drifted'
Require ($expandedBoard.raw.Length -eq $boardCapture.rawBodyBytes) 'captured API2 decision-board raw byte count drifted'
Require ((Get-BytesSha256 $expandedBoard.raw) -ceq [string]$boardCapture.rawBodySha256) 'captured API2 decision-board raw digest drifted'
$capturedHealth = [Text.Encoding]::UTF8.GetString(
    $expandedHealth.raw) | ConvertFrom-Json
$capturedBoardEnvelope = [Text.Encoding]::UTF8.GetString(
    $expandedBoard.raw) | ConvertFrom-Json
$capturedBoard = $capturedBoardEnvelope.attributionDecisionBoard
Require ([string]$capturedHealth.serviceRelease.releaseId -ceq [string]$observation.api2.releaseId) 'captured API2 health release drifted'
Require ([string]$capturedHealth.serviceRelease.sourceGitHead -ceq [string]$observation.api2.sourceGitHead) 'captured API2 health source drifted'
Require ($capturedHealth.independentBoardAttributionPublisher.deliveredRecords -eq 0) 'captured API2 publisher delivered records are nonzero'
Require ([string]$capturedHealth.hostListingReceipt.status -ceq [string]$observation.api2.hostListingReceipt.status) 'captured host listing receipt status drifted'
Require ([string]$capturedHealth.hostForwardingAck.status -ceq [string]$observation.api2.hostForwardingAck.status) 'captured host forwarding status drifted'
Require ([string]$capturedBoard.generatedAt -ceq [string]$observation.decisionBoard.generatedAt) 'captured decision-board generation time drifted'
Require ([string]$capturedBoard.status -ceq [string]$observation.decisionBoard.status) 'captured decision-board status drifted'
Require ($capturedBoard.sourceSnapshot.rowCount -eq $observation.decisionBoard.sourceSnapshot.rowCount) 'captured decision-board row count drifted'
$capturedWindows = @{}
foreach ($window in $capturedBoard.windows) {
    if ($window.id -in @('15m', '1h', '24h')) {
        $capturedWindows[[string]$window.id] = $window
    }
}
Require ($capturedWindows.Count -eq 3) 'captured decision-board required windows are missing'
Require ($capturedWindows['24h'].evidenceClassBreakdown.natural -eq 0) 'captured decision-board natural rows are nonzero'
Require ($capturedWindows['24h'].rowCount -eq $observation.decisionBoard.window24h.rowCount) 'captured decision-board 24h row count drifted'

$projection = $capture.selectedProjection
Require ([string]$projection.api2ReleaseId -ceq [string]$capturedHealth.serviceRelease.releaseId) 'API2 capture projection release drifted'
Require ([string]$projection.api2SourceGitHead -ceq [string]$capturedHealth.serviceRelease.sourceGitHead) 'API2 capture projection source drifted'
Require ([string]$projection.publisherStatus -ceq [string]$capturedHealth.independentBoardAttributionPublisher.status) 'API2 capture projection publisher status drifted'
Require ($projection.publisherDeliveredRecords -eq $capturedHealth.independentBoardAttributionPublisher.deliveredRecords) 'API2 capture projection publisher delivery count drifted'
Require ([string]$projection.hostListingReceiptStatus -ceq [string]$capturedHealth.hostListingReceipt.status) 'API2 capture projection listing receipt drifted'
Require ([string]$projection.hostForwardingAckStatus -ceq [string]$capturedHealth.hostForwardingAck.status) 'API2 capture projection forwarding status drifted'
Require ([string]$projection.decisionBoardGeneratedAt -ceq [string]$capturedBoard.generatedAt) 'API2 capture projection board time drifted'
Require ([string]$projection.decisionBoardCutoffAt -ceq [string]$capturedBoard.sourceSnapshot.cutoffAt) 'API2 capture projection cutoff drifted'
Require ([string]$projection.decisionBoardWindowStart24h -ceq [string]$capturedWindows['24h'].startAt) 'API2 capture projection 24h start drifted'
Require ([string]$projection.decisionBoardWindowEnd24h -ceq [string]$capturedWindows['24h'].endAt) 'API2 capture projection 24h end drifted'
Require ([string]$projection.sourceSnapshotId -ceq [string]$capturedBoard.sourceSnapshot.snapshotId) 'API2 capture projection snapshot id drifted'
Require ([string]$projection.sourceSnapshotAuthority -ceq [string]$capturedBoard.sourceSnapshot.snapshotAuthority) 'API2 capture projection snapshot authority drifted'
Require ($projection.sourceRowCount -eq $capturedBoard.sourceSnapshot.rowCount) 'API2 capture projection source row count drifted'
Require ($projection.mixedReleaseRowCount -eq $capturedBoard.sourceSnapshot.mixedReleaseRowCount) 'API2 capture projection mixed-release count drifted'
Require ($projection.transportCoverageHours -eq $capturedBoard.sourceSnapshot.transportCoverageHours) 'API2 capture projection transport coverage drifted'
Require ($projection.crossLayerJoinRate -eq $capturedBoard.sourceSnapshot.crossLayerJoinRate) 'API2 capture projection cross-layer join drifted'
Require ($projection.retentionComplete -eq $capturedBoard.sourceSnapshot.retentionComplete) 'API2 capture projection retention boundary drifted'
Require ($projection.classificationAuthorityCoverage -eq $capturedBoard.dataQuality.classificationAuthorityCoverage) 'API2 capture projection classification authority drifted'
Require ($projection.coreAttributionFieldCoverage -eq $capturedBoard.dataQuality.coreAttributionFieldCoverage) 'API2 capture projection core coverage drifted'
foreach ($windowId in @('15m', '1h', '24h')) {
    $projectionWindow = $projection.("window$windowId")
    $capturedWindow = $capturedWindows[$windowId]
    Require ($projectionWindow.rows -eq $capturedWindow.rowCount) "API2 capture projection $windowId row count drifted"
    foreach ($evidenceClass in @(
        'natural',
        'unknown',
        'diagnostic',
        'probe',
        'synthetic'
    )) {
        Require (
            $projectionWindow.$evidenceClass -eq
                $capturedWindow.evidenceClassBreakdown.$evidenceClass
        ) "API2 capture projection $windowId $evidenceClass count drifted"
    }
}
Require ([string]$capture.runtimeStateCapture.sha256 -ceq [string]$observation.api2.immutableCapture.runtimeStateSha256) 'captured runtime-state digest drifted from observation'
Require ([string]$capture.runtimeStateCapture.sha256 -ceq [string]$audit.api2Service.runtimeMarkerScan.runtimeStateSha256) 'captured runtime-state digest drifted from audit'
Require ([string]$capture.runtimeStateCapture.projectionAuthority -ceq 'collector_claim_record_only') 'captured runtime-state projection authority was overstated'
Require ([string]$observation.api2.immutableCapture.runtimeStateProjectionAuthority -ceq 'collector_claim_record_only') 'observation runtime-state projection authority was overstated'
Require ([string]$audit.api2Service.runtimeMarkerScan.projectionAuthority -ceq 'collector_claim_record_only') 'audit runtime-state projection authority was overstated'
Require ($observation.api2.immutableCapture.runtimeStateProjectionDerivationProofStored -eq $false) 'observation runtime-state projection proof was overstated'
Require ($audit.api2Service.runtimeMarkerScan.projectionDerivationProofStored -eq $false) 'audit runtime-state projection proof was overstated'
Require ([string]$capture.runtimeStateCapture.derivationProof.status -ceq 'not_captured') 'captured runtime-state derivation proof status drifted'
Require ([string]$capture.runtimeStateCapture.derivationProof.collectorDigest -ceq '') 'captured runtime-state collector digest was invented'
Require ([string]$capture.runtimeStateCapture.derivationProof.targetSignedReceipt -ceq '') 'captured runtime-state target receipt was invented'
Require ($capture.runtimeStateCapture.markerCounts.officialRequestSource -eq 0) 'captured runtime-state has an unverified official-entry marker'
Require ($capture.runtimeStateCapture.markerCounts.officialChannel -eq 0) 'captured runtime-state has an unverified official channel marker'
Require ($capture.runtimeStateCapture.outboxExists -eq $false) 'captured API2 publisher outbox unexpectedly exists'
$runtimeStages = @($capture.runtimeStateCapture.uniqueDiagnosticStages)
Require ($runtimeStages.Count -eq 3) 'captured runtime-state diagnostic stage count drifted'
Require (
    (@($runtimeStages.eventType | ForEach-Object {
        ([string]$_).ToUpperInvariant()
    }) -join ',') -ceq
        (@($observation.api2.independentBoardDiagnosticSignal.sequence) -join ',')
) 'captured runtime-state diagnostic sequence drifted'
Require (
    @($runtimeStages.serverBindingIdDigest | Select-Object -Unique).Count -eq 1 -and
    [string]$runtimeStages[0].serverBindingIdDigest -ceq
        [string]$observation.api2.independentBoardDiagnosticSignal.bindingDigestSha256Prefix
) 'captured runtime-state diagnostic binding drifted'
Require (
    @($runtimeStages.traceIdDigest | Select-Object -Unique).Count -eq 1 -and
    [string]$runtimeStages[0].traceIdDigest -ceq
        [string]$observation.api2.independentBoardDiagnosticSignal.traceDigestSha256Prefix
) 'captured runtime-state diagnostic trace drifted'
Require (
    @($runtimeStages | Where-Object {
        $_.trafficClass -cne 'diagnostic' -or
        $_.isSynthetic -ne $true -or
        $_.productIdSource -cne 'tool_argument_untrusted'
    }).Count -eq 0
) 'captured runtime-state diagnostic quarantine drifted'
Require ($memo.Contains($reportPath)) 'current observation memo does not reference the current report'
Require ($memo.Contains($auditPath)) 'current observation memo does not reference the cross-service audit'
Require ($memo.Contains($capturePath)) 'current observation memo does not reference the API2 capture'
Require ($memo.Contains('isSynthetic=true')) 'current observation memo lost the synthetic quarantine'
Require ($memo.Contains('collector_claim_record_only')) 'current observation memo overstated runtime projection authority'
Require ($memo.Contains('publisher delivered=0')) 'current observation memo lost the zero-delivery boundary'

[pscustomobject]@{
    schema = 'fbsir.w1aCurrentObservationVerification.v1'
    status = 'PASS'
    observedAt = $observation.observedAt
    currentObservationReport = $reportPath
    natural = 0
    probeEvents = $observation.u3w.ledger.probeEventCount
    diagnosticSignalIndependentlyObserved = $observation.api2.independentBoardDiagnosticSignal.observed
    runtimeCollectorClaimRecorded = $observation.api2.independentBoardDiagnosticSignal.collectorClaimRecorded
    runtimeCollectorClaimQuarantined = $observation.api2.independentBoardDiagnosticSignal.isSynthetic
    crossServiceSameBindingProven = $observation.crossServiceJoin.sameBindingProven
    crossServiceAudit = $auditPath
    api2LiveSignalCapture = $capturePath
    api2LiveSignalCaptureSha256 = $captureSha256
    api2ReleaseId = $observation.api2.releaseId
    u3wReleaseId = $observation.u3w.releaseId
    hostListingReceipt = $observation.api2.hostListingReceipt.status
    hostForwardingAck = $observation.api2.hostForwardingAck.status
} | ConvertTo-Json -Depth 4
