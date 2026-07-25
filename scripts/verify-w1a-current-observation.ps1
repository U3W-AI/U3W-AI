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

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

$status = Read-Json 'docs/independent-board/implementation-status.json'
$contract = Read-Json '.fbs-engineering/contract.json'
$reportPath = [string]$status.w1a.currentObservationReport
Require (-not [string]::IsNullOrWhiteSpace($reportPath)) 'status has no currentObservationReport'
Require ([string]$contract.artifacts.w1aCurrentObservationReport -ceq $reportPath) 'contract/status current observation path drifted'
$observation = Read-Json $reportPath

Require ($observation.schema -ceq 'fbsir.independentBoardW1aCurrentObservation.v1') 'current observation schema mismatch'
Require ($observation.authorization -ceq 'read_only_production_and_repo_facts') 'current observation is not read-only'
Require ($observation.officialIdentity.productId -ceq 'fbsir-eight-seat-board') 'official product identity mismatch'
Require ($observation.officialIdentity.listedManifestVersion -ceq '26.7.21') 'listed manifest version mismatch'
Require ($observation.officialIdentity.embeddedContractVersion -ceq '26.7.20') 'embedded contract version mismatch'
Require ($observation.officialIdentity.packageFrozen -eq $true) 'official package freeze is not asserted'

Require ($observation.u3w.serviceState -ceq 'active') 'U3W service is not active in current observation'
Require ($observation.u3w.jarSha256 -ceq [string]$status.w1a.u3wSourceTruth.productionJarSha256) 'U3W production JAR hash drifted from status truth'
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
Require ($observation.api2.sourceGitHead -ceq [string]$status.w1a.api2SourceTruth.productionDeclaredCommit) 'API2 production source head drifted from status truth'
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

Require ($observation.decisionBoard.status -ceq 'decision_withheld_no_trusted_natural_denominator') 'decision board was not withheld'
Require ($observation.decisionBoard.window24h.natural -eq 0) 'decision board natural rows are nonzero'
Require ($observation.decisionBoard.window24h.eligibleNaturalSampleCount -eq 0) 'eligible natural sample count is nonzero'
Require ($observation.decisionBoard.window24h.productCreditAllowed -eq $false) 'decision board product credit is allowed'
Require ($observation.connector.boardBindingObserved -eq $false) 'connector was incorrectly treated as board binding'

[pscustomobject]@{
    schema = 'fbsir.w1aCurrentObservationVerification.v1'
    status = 'PASS'
    observedAt = $observation.observedAt
    currentObservationReport = $reportPath
    natural = 0
    probeEvents = $observation.u3w.ledger.probeEventCount
    api2ReleaseId = $observation.api2.releaseId
    u3wReleaseId = $observation.u3w.releaseId
    hostListingReceipt = $observation.api2.hostListingReceipt.status
    hostForwardingAck = $observation.api2.hostForwardingAck.status
} | ConvertTo-Json -Depth 4
