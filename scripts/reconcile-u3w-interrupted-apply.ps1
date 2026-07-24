[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Plan', 'Reconcile')]
    [string]$Mode,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string]$ExpectedCommit,
    [Parameter(Mandatory = $true)]
    [ValidatePattern(
        '^w1a-release-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z$')]
    [string]$RecoveryRunId,
    [Parameter(Mandatory = $true)]
    [ValidatePattern(
        '^w1a-release-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z$')]
    [string]$TargetReleaseId,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string]$TargetSourceCommit,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedStageReceiptSha256,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedApplyFailureReceiptSha256,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedApplyFailureManifestSha256,
    [string]$RecoveryPlanReceiptPath,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedRecoveryPlanReceiptSha256,
    [string]$ApprovalReceiptPath,
    [string]$PlanOutputPath,
    [string]$ExternalEvidenceDirectory,
    [string]$SshKeyPath = $(if ($env:U3W_SSH_KEY_PATH) {
            $env:U3W_SSH_KEY_PATH
        } else {
            Join-Path $env:USERPROFILE '.ssh\id_ed25519_api2'
        }),
    [string]$KnownHostsPath = $(Join-Path $env:TEMP (
            'u3w-interrupted-apply-recovery-known-hosts'))
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$SshTarget = 'root@api2.u3w.com'
$ExpectedPublicKeyFingerprint =
    'SHA256:oCIzbO9W94qDBeS9MtetLCW0CjYMkyZqT6QkgcLkXk0'
$ExpectedRemoteHostKeyFingerprint =
    'SHA256:GkS/HJpLm48N+KaMV/WEVSBHnI8PKJsU+6ycAsn+mBA'
$ZeroSha256 = '0' * 64
$RecoveryReceiptSchema =
    'fbsir.u3wDefaultOffInterruptedApplyRecoveryReceipt.v1'
$RecoveryState =
    'INTERRUPTED_APPLY_RECOVERED_APPLICATION_DATABASE_043_RETAINED_DORMANT'
$RecoveryPlanSchema = 'fbsir.u3wInterruptedApplyRecoveryPlan.v1'
$RecoveryPlanState =
    'INTERRUPTED_APPLY_RECOVERY_CANONICALIZATION_PLANNED'
$WorkerResultSchema = 'fbsir.u3wDefaultOffReleaseWorkerResult.v2'

if (-not $PlanOutputPath) {
    $PlanOutputPath = Join-Path $RepoRoot (
        'work\recovery-plans\interrupted-apply-recovery-plan-latest.json')
}
if (-not $RecoveryPlanReceiptPath) {
    $RecoveryPlanReceiptPath = $PlanOutputPath
}
if (-not $ExternalEvidenceDirectory) {
    $handoffRoot = Split-Path (
        Split-Path $RepoRoot -Parent) -Parent
    $ExternalEvidenceDirectory = Join-Path $handoffRoot (
        "deliverables\production-evidence\$RecoveryRunId-recovery")
}

function Get-BytesSha256 {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)
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

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "required file is absent: $Path"
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).
        Hash.ToLowerInvariant()
}

function Get-CommittedBlobBytes {
    param([Parameter(Mandatory = $true)][string]$GitPath)
    $objectId = (& git -C $RepoRoot rev-parse (
        "$ExpectedCommit`:$GitPath")).Trim()
    if ($LASTEXITCODE -ne 0 -or
        $objectId -notmatch '^[0-9a-f]{40,64}$') {
        throw "cannot resolve committed blob: $GitPath"
    }
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Command git.exe).Source
    $start.Arguments = "-C `"$RepoRoot`" cat-file blob $objectId"
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    $memory = [IO.MemoryStream]::new()
    try {
        if (-not $process.Start()) {
            throw 'failed to start git cat-file'
        }
        $process.StandardOutput.BaseStream.CopyTo($memory)
        $errorText = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw "git cat-file failed: $errorText"
        }
        return ,$memory.ToArray()
    }
    finally {
        $memory.Dispose()
        $process.Dispose()
    }
}

function Get-RunnerSha256 {
    return Get-BytesSha256 (Get-CommittedBlobBytes (
            'scripts/reconcile-u3w-interrupted-apply.ps1'))
}

function Get-WorkerSha256 {
    return Get-BytesSha256 (Get-CommittedBlobBytes (
            'scripts/u3w-default-off-release-remote.py'))
}

function Assert-StrictHead {
    $head = (& git -C $RepoRoot rev-parse HEAD).Trim().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0 -or $head -cne $ExpectedCommit) {
        throw 'strict HEAD does not match ExpectedCommit'
    }
    if (@(& git -C $RepoRoot status --porcelain=v1).Count -ne 0) {
        throw 'strict HEAD requires a clean worktree'
    }
    $branch = (& git -C $RepoRoot branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $branch) {
        throw 'strict HEAD requires a named branch'
    }
    $upstream = (& git -C $RepoRoot rev-parse '@{upstream}').
        Trim().ToLowerInvariant()
    $remote = @(
        & git -C $RepoRoot ls-remote --exit-code origin "refs/heads/$branch"
    )
    if ($LASTEXITCODE -ne 0 -or $remote.Count -ne 1) {
        throw 'origin branch is absent or ambiguous'
    }
    $originHead = ($remote[0] -split '\s+', 2)[0].ToLowerInvariant()
    if ($upstream -cne $ExpectedCommit -or
        $originHead -cne $ExpectedCommit) {
        throw 'HEAD, upstream and origin branch must be identical'
    }
    $runnerBytes = Get-CommittedBlobBytes (
        'scripts/reconcile-u3w-interrupted-apply.ps1')
    if ((Get-BytesSha256 ([IO.File]::ReadAllBytes($PSCommandPath))) -cne
        (Get-BytesSha256 $runnerBytes)) {
        throw 'recovery runner differs from its committed blob'
    }
    return [ordered]@{
        branch = $branch
        originHead = $originHead
    }
}

function Assert-PrivateKey {
    if (-not (Test-Path -LiteralPath $SshKeyPath -PathType Leaf)) {
        throw 'SSH private key is absent'
    }
    $derived = @(& ssh-keygen.exe -y -f $SshKeyPath 2>$null)
    if ($LASTEXITCODE -ne 0 -or $derived.Count -ne 1) {
        throw 'cannot derive SSH public key'
    }
    $fingerprint = $derived |
        & ssh-keygen.exe -lf - -E sha256 2>$null
    if ($LASTEXITCODE -ne 0 -or
        $fingerprint -notlike "*$ExpectedPublicKeyFingerprint*") {
        throw 'SSH public-key fingerprint mismatch'
    }
}

function Test-KnownHostFingerprint {
    param([Parameter(Mandatory = $true)][string]$Path)
    $hostName = ($SshTarget -split '@', 2)[1]
    $matching = @(
        & ssh-keygen.exe -F $hostName -f $Path 2>$null |
            Where-Object { $_ -and -not $_.StartsWith('#') }
    )
    if ($LASTEXITCODE -ne 0 -or $matching.Count -eq 0) {
        return $false
    }
    $fingerprints = @(
        $matching | & ssh-keygen.exe -lf - -E sha256 2>$null
    )
    return $LASTEXITCODE -eq 0 -and
        $fingerprints.Count -gt 0 -and
        @($fingerprints | Where-Object {
                $_ -notmatch [regex]::Escape(
                    $ExpectedRemoteHostKeyFingerprint)
            }).Count -eq 0
}

function Ensure-KnownHosts {
    $directory = Split-Path -Parent $KnownHostsPath
    if ($directory -and -not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }
    if (Test-Path -LiteralPath $KnownHostsPath -PathType Leaf) {
        if (-not (Test-KnownHostFingerprint $KnownHostsPath)) {
            throw 'existing known_hosts fingerprint mismatch'
        }
        return
    }
    $scanPath = "$KnownHostsPath.scan-$PID"
    try {
        $prior = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $scan = @(
                & ssh-keyscan.exe -T 10 -t ed25519 (
                    ($SshTarget -split '@', 2)[1]) 2>$null
            )
        }
        finally {
            $ErrorActionPreference = $prior
        }
        if ($LASTEXITCODE -ne 0 -or $scan.Count -eq 0) {
            throw 'ssh-keyscan failed'
        }
        $scan | Set-Content -LiteralPath $scanPath -Encoding ascii
        if (-not (Test-KnownHostFingerprint $scanPath)) {
            throw 'scanned host-key fingerprint mismatch'
        }
        Move-Item -LiteralPath $scanPath -Destination $KnownHostsPath
    }
    finally {
        Remove-Item -LiteralPath $scanPath -Force `
            -ErrorAction SilentlyContinue
    }
}

function Get-SshOptions {
    return @(
        '-i', $SshKeyPath,
        '-o', 'BatchMode=yes',
        '-o', 'IdentitiesOnly=yes',
        '-o', 'IdentityAgent=none',
        '-o', 'ConnectTimeout=10',
        '-o', 'ConnectionAttempts=1',
        '-o', 'StrictHostKeyChecking=yes',
        '-o', "UserKnownHostsFile=$KnownHostsPath"
    )
}

function Write-Utf8NoBomAtomic {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )
    $directory = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    $partial = "$Path.$PID.partial"
    try {
        [IO.File]::WriteAllText(
            $partial,
            $Content,
            [Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $partial -Destination $Path -Force
    }
    finally {
        Remove-Item -LiteralPath $partial -Force `
            -ErrorAction SilentlyContinue
    }
}

function Write-ContentAddressedRecoveryPlan {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)
    $digest = Get-BytesSha256 $Bytes
    $directory = Join-Path $RepoRoot 'work\recovery-plans\by-sha256'
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    $path = Join-Path $directory "$digest.json"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $stream = [IO.File]::Open(
            $path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write,
            [IO.FileShare]::None)
        try {
            $stream.Write($Bytes, 0, $Bytes.Length)
            $stream.Flush($true)
        }
        finally {
            $stream.Dispose()
        }
    }
    if ((Get-FileSha256 $path) -cne $digest) {
        throw 'content-addressed recovery Plan drifted'
    }
    $item = Get-Item -LiteralPath $path -Force
    $item.IsReadOnly = $true
    return [ordered]@{
        path = $item.FullName
        sha256 = $digest
    }
}

function Get-ExactJson {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Bytes,
        [Parameter(Mandatory = $true)][string[]]$Fields,
        [Parameter(Mandatory = $true)][string]$Label
    )
    $value = [Text.UTF8Encoding]::new(
        $false, $true).GetString($Bytes) | ConvertFrom-Json
    $actual = @($value.PSObject.Properties.Name | Sort-Object)
    $expected = @($Fields | Sort-Object)
    if (($actual -join "`n") -cne ($expected -join "`n")) {
        throw "$Label has missing or unknown fields"
    }
    return $value
}

function Invoke-RecoveryWorker {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet(
            'InspectInterruptedApplyRecovery',
            'CanonicalizeInterruptedApplyRecovery')]
        [string]$WorkerMode,
        [AllowEmptyCollection()]
        [byte[]]$ApprovalBytes,
        [AllowEmptyCollection()]
        [byte[]]$PlanBytes
    )
    Assert-PrivateKey
    Ensure-KnownHosts
    $workerBytes = Get-CommittedBlobBytes (
        'scripts/u3w-default-off-release-remote.py')
    $workerSha = Get-BytesSha256 $workerBytes
    $approvalSha = if ($ApprovalBytes.Count -gt 0) {
        Get-BytesSha256 $ApprovalBytes
    } else {
        $ZeroSha256
    }
    $planSha = if ($PlanBytes.Count -gt 0) {
        Get-BytesSha256 $PlanBytes
    } else {
        $ZeroSha256
    }
    $approvalBase64 = if ($ApprovalBytes.Count -gt 0) {
        [Convert]::ToBase64String($ApprovalBytes)
    } else {
        ''
    }
    $planBase64 = if ($PlanBytes.Count -gt 0) {
        [Convert]::ToBase64String($PlanBytes)
    } else {
        ''
    }
    $manifestSha = if ($ExpectedApplyFailureManifestSha256) {
        $ExpectedApplyFailureManifestSha256
    } else {
        $ZeroSha256
    }
    $arguments = @(
        'u3w-default-off-release-remote.py',
        '--mode', $WorkerMode,
        '--release-id', $RecoveryRunId,
        '--source-commit', $ExpectedCommit,
        '--approval-sha', $approvalSha,
        '--approval-json-base64', $approvalBase64,
        '--runner-sha', (Get-RunnerSha256),
        '--worker-sha', $workerSha,
        '--build-receipt-sha', $ZeroSha256,
        '--plan-receipt-sha', $ZeroSha256,
        '--backup-receipt-sha', $ZeroSha256,
        '--baseline-receipt-sha', $ZeroSha256,
        '--admin-root-dependency-adoption-receipt-sha',
            $ZeroSha256,
        '--configuration-receipt-sha', $ZeroSha256,
        '--stage-receipt-sha', $ExpectedStageReceiptSha256,
        '--deployment-receipt-sha', $ZeroSha256,
        '--backend-sha', $ZeroSha256,
        '--frontend-tree-sha', $ZeroSha256,
        '--migration-sha', $ZeroSha256,
        '--target-release-id', $TargetReleaseId,
        '--target-source-commit', $TargetSourceCommit,
        '--apply-failure-receipt-sha',
            $ExpectedApplyFailureReceiptSha256,
        '--apply-failure-manifest-sha', $manifestSha,
        '--recovery-plan-receipt-sha', $planSha,
        '--recovery-plan-json-base64', $planBase64
    )
    $payloadObject = [ordered]@{
        workerBase64 = [Convert]::ToBase64String($workerBytes)
        workerSha256 = $workerSha
        argv = $arguments
    }
    $payloadBytes = [Text.Encoding]::UTF8.GetBytes(
        ($payloadObject | ConvertTo-Json -Depth 8 -Compress))
    $payload = [Convert]::ToBase64String($payloadBytes)
    $bootstrap = 'import base64,hashlib,json,sys;' +
        'p=json.loads(base64.b64decode(sys.stdin.buffer.read()));' +
        'b=base64.b64decode(p["workerBase64"],validate=True);' +
        'a=hashlib.sha256(b).hexdigest();' +
        'a==p["workerSha256"] or sys.exit("worker sha256 mismatch");' +
        'sys.argv=p["argv"];' +
        'exec(compile(b,"<u3w-recovery-worker>","exec"),' +
        '{"__name__":"__main__"})'
    $remoteCommand = "python3 -c '$($bootstrap.Replace('"', '\"'))'"
    $sshArguments = @()
    $sshArguments += Get-SshOptions
    $sshArguments += @($SshTarget, $remoteCommand)
    $output = @($payload | & ssh.exe @sshArguments)
    $exitCode = $LASTEXITCODE
    if ($output.Count -eq 0) {
        throw "$WorkerMode returned no result"
    }
    $raw = ($output -join "`n").Trim()
    try {
        $result = $raw | ConvertFrom-Json
    }
    catch {
        throw "$WorkerMode returned invalid JSON"
    }
    if ($exitCode -ne 0) {
        $failureBytes = [Text.UTF8Encoding]::new($false).GetBytes(
            $raw + "`n")
        $failureSha = Get-BytesSha256 $failureBytes
        $failureEvidence = Write-ImmutableEvidence `
            -Name "worker-failure-$failureSha.json" `
            -Bytes $failureBytes
        $failureFields = @(
            'schema', 'state', 'mode', 'releaseId', 'sourceCommit',
            'approvalReceiptSha256', 'errorType',
            'errorMessageSha256', 'failureReceiptPath',
            'failureReceiptSha256', 'failureReceiptSchema',
            'failureReceiptState', 'failureReceiptEvidenceValid',
            'officialExpertsPackageChanged'
        )
        $actualFailureFields = @(
            $result.PSObject.Properties.Name | Sort-Object)
        if (
            (($failureFields | Sort-Object) -join "`n") -cne
                ($actualFailureFields -join "`n") -or
            $result.schema -cne
                'fbsir.u3wDefaultOffReleaseWorkerError.v2' -or
            $result.state -cne 'WORKER_FAILED' -or
            $result.mode -cne $WorkerMode -or
            $result.releaseId -cne $RecoveryRunId -or
            $result.sourceCommit -cne $ExpectedCommit -or
            $result.approvalReceiptSha256 -cne $approvalSha -or
            [string]$result.errorType -notmatch
                '^[A-Za-z_][A-Za-z0-9_]{0,127}$' -or
            [string]$result.errorMessageSha256 -notmatch
                '^[0-9a-f]{64}$' -or
            $null -ne $result.failureReceiptPath -or
            $null -ne $result.failureReceiptSha256 -or
            $null -ne $result.failureReceiptSchema -or
            $null -ne $result.failureReceiptState -or
            $result.failureReceiptEvidenceValid -ne $false -or
            $result.officialExpertsPackageChanged -ne $false
        ) {
            throw "$WorkerMode returned an invalid failure envelope; " +
                "raw evidence persisted at $failureEvidence"
        }
        throw "$WorkerMode failed; immutable evidence persisted at " +
            "$failureEvidence (sha256 $failureSha, message $(
                $result.errorMessageSha256))"
    }
    return [ordered]@{
        result = $result
        raw = $raw
    }
}

function Resolve-RecoveryPlan {
    if (-not (Test-Path -LiteralPath $RecoveryPlanReceiptPath `
            -PathType Leaf)) {
        throw 'Reconcile requires RecoveryPlanReceiptPath'
    }
    $resolved = (Resolve-Path -LiteralPath (
            $RecoveryPlanReceiptPath)).Path
    $item = Get-Item -LiteralPath $resolved -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'recovery Plan cannot be a link or reparse point'
    }
    $bytes = [IO.File]::ReadAllBytes($resolved)
    $digest = Get-BytesSha256 $bytes
    if (-not $ExpectedRecoveryPlanReceiptSha256 -or
        $digest -cne $ExpectedRecoveryPlanReceiptSha256) {
        throw 'recovery Plan digest mismatch'
    }
    $fields = @(
        'schema', 'mode', 'state', 'targetHost', 'serviceUnit',
        'recoveryRunId', 'executorSourceCommit', 'targetReleaseId',
        'targetSourceCommit', 'stageReceiptSha256',
        'applyFailureReceiptSha256', 'applyFailureManifestSha256',
        'runnerSha256', 'workerSha256', 'productionFilesystemWrite',
        'productionDatabaseWrite', 'productionServiceChange',
        'officialExpertsPackageChange', 'generatedAt', 'expiresAt'
    )
    $plan = Get-ExactJson -Bytes $bytes -Fields $fields `
        -Label 'recovery Plan'
    $generated = [DateTimeOffset]::Parse($plan.generatedAt)
    $expires = [DateTimeOffset]::Parse($plan.expiresAt)
    if (
        $plan.schema -cne $RecoveryPlanSchema -or
        $plan.mode -cne 'RecoveryPlan' -or
        $plan.state -cne $RecoveryPlanState -or
        $plan.targetHost -cne 'api2.u3w.com' -or
        $plan.serviceUnit -cne 'fbsir-admin.service' -or
        $plan.recoveryRunId -cne $RecoveryRunId -or
        $plan.executorSourceCommit -cne $ExpectedCommit -or
        $plan.targetReleaseId -cne $TargetReleaseId -or
        $plan.targetSourceCommit -cne $TargetSourceCommit -or
        $plan.stageReceiptSha256 -cne
            $ExpectedStageReceiptSha256 -or
        $plan.applyFailureReceiptSha256 -cne
            $ExpectedApplyFailureReceiptSha256 -or
        $plan.applyFailureManifestSha256 -cne
            $ExpectedApplyFailureManifestSha256 -or
        $plan.runnerSha256 -cne (Get-RunnerSha256) -or
        $plan.workerSha256 -cne (Get-WorkerSha256) -or
        $plan.productionFilesystemWrite -ne $true -or
        $plan.productionDatabaseWrite -ne $false -or
        $plan.productionServiceChange -ne $false -or
        $plan.officialExpertsPackageChange -ne $false -or
        $generated -gt [DateTimeOffset]::UtcNow -or
        $expires -le $generated -or
        ($expires - $generated).TotalHours -gt 24
    ) {
        throw 'recovery Plan identity or validity is invalid'
    }
    return [ordered]@{
        bytes = $bytes
        sha256 = $digest
        receipt = $plan
    }
}

function Resolve-RecoveryApproval {
    param([Parameter(Mandatory = $true)]$Plan)
    if (-not $ApprovalReceiptPath -or
        -not (Test-Path -LiteralPath $ApprovalReceiptPath `
            -PathType Leaf)) {
        throw 'Reconcile requires ApprovalReceiptPath'
    }
    $bytes = [IO.File]::ReadAllBytes(
        (Resolve-Path -LiteralPath $ApprovalReceiptPath).Path)
    $fields = @(
        'schema', 'action', 'targetHost', 'runId',
        'executorSourceCommit', 'targetReleaseId',
        'targetSourceCommit', 'approvedAt', 'expiresAt',
        'authorizedBy', 'concurrentDdlProhibited',
        'productionFilesystemWrite', 'productionDatabaseWrite',
        'productionServiceChange', 'officialExpertsPackageChange',
        'expectedStageReceiptSha256',
        'expectedApplyFailureReceiptSha256',
        'expectedApplyFailureManifestSha256', 'requestDigest',
        'approvalNonce', 'runnerSha256', 'workerSha256'
    )
    $approval = Get-ExactJson -Bytes $bytes -Fields $fields `
        -Label 'recovery approval'
    $approved = [DateTimeOffset]::Parse($approval.approvedAt)
    $expires = [DateTimeOffset]::Parse($approval.expiresAt)
    if (
        $approval.schema -cne
            'fbsir.u3wProductionChangeApprovalReceipt.v2' -or
        $approval.action -cne
            'CANONICALIZE_W1A_INTERRUPTED_APPLY_RECOVERY' -or
        $approval.targetHost -cne 'api2.u3w.com' -or
        $approval.runId -cne $RecoveryRunId -or
        $approval.executorSourceCommit -cne $ExpectedCommit -or
        $approval.targetReleaseId -cne $TargetReleaseId -or
        $approval.targetSourceCommit -cne $TargetSourceCommit -or
        $approval.authorizedBy -cne 'workspace-user' -or
        $approval.concurrentDdlProhibited -ne $true -or
        $approval.productionFilesystemWrite -ne $true -or
        $approval.productionDatabaseWrite -ne $false -or
        $approval.productionServiceChange -ne $false -or
        $approval.officialExpertsPackageChange -ne $false -or
        $approval.expectedStageReceiptSha256 -cne
            $ExpectedStageReceiptSha256 -or
        $approval.expectedApplyFailureReceiptSha256 -cne
            $ExpectedApplyFailureReceiptSha256 -or
        $approval.expectedApplyFailureManifestSha256 -cne
            $ExpectedApplyFailureManifestSha256 -or
        $approval.requestDigest -cne $Plan.sha256 -or
        [string]$approval.approvalNonce -notmatch
            '^[0-9a-f]{32}$' -or
        $approval.runnerSha256 -cne (Get-RunnerSha256) -or
        $approval.workerSha256 -cne (Get-WorkerSha256) -or
        $approved -gt [DateTimeOffset]::UtcNow -or
        $expires -le $approved -or
        ($expires - $approved).TotalHours -gt 24
    ) {
        throw 'recovery approval identity or validity is invalid'
    }
    return [ordered]@{
        bytes = $bytes
        sha256 = Get-BytesSha256 $bytes
        receipt = $approval
    }
}

function Assert-NoReparsePointChain {
    param([Parameter(Mandatory = $true)][string]$Path)
    $current = [IO.Path]::GetFullPath($Path)
    while ($current) {
        $item = Get-Item -LiteralPath $current -Force `
            -ErrorAction SilentlyContinue
        if ($null -ne $item -and
            (
                ($item.Attributes -band
                    [IO.FileAttributes]::ReparsePoint) -ne 0 -or
                -not [string]::IsNullOrWhiteSpace(
                    [string]$item.LinkType)
            )) {
            throw "evidence path contains a link: $current"
        }
        $parent = [IO.Directory]::GetParent($current)
        if ($null -eq $parent) {
            break
        }
        $current = $parent.FullName
    }
}

function Assert-EvidenceDirectory {
    $full = [IO.Path]::GetFullPath($ExternalEvidenceDirectory)
    $repo = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\') + '\'
    if (($full.TrimEnd('\') + '\').StartsWith(
            $repo, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'external recovery evidence must be outside the repository'
    }
    if (-not (Test-Path -LiteralPath $full -PathType Container)) {
        New-Item -ItemType Directory -Path $full -Force | Out-Null
    }
    Assert-NoReparsePointChain $full
    return $full
}

function Write-ImmutableEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][byte[]]$Bytes
    )
    $directory = Assert-EvidenceDirectory
    $path = Join-Path $directory $Name
    if ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($path)) -cne
        [IO.Path]::GetFullPath($directory).TrimEnd('\')) {
        throw 'external recovery evidence escaped its directory'
    }
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $item = Get-Item -LiteralPath $path -Force
        if (($item.Attributes -band
                [IO.FileAttributes]::ReparsePoint) -ne 0 -or
            -not $item.IsReadOnly -or
            (Get-BytesSha256 ([IO.File]::ReadAllBytes($path))) -cne
                (Get-BytesSha256 $Bytes)) {
            throw 'existing external recovery evidence drifted'
        }
        return $path
    }
    $stream = [IO.File]::Open(
        $path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write,
        [IO.FileShare]::None)
    try {
        $stream.Write($Bytes, 0, $Bytes.Length)
        $stream.Flush($true)
    }
    finally {
        $stream.Dispose()
    }
    (Get-Item -LiteralPath $path -Force).IsReadOnly = $true
    return $path
}

function Get-RemoteRecoveryFile {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256
    )
    if ($Name -notin @(
            'interrupted-apply-recovery-receipt.json',
            'interrupted-apply-recovery-approval.json',
            'interrupted-apply-recovery-plan.json')) {
        throw 'remote recovery file name is not allowed'
    }
    $temporary = Join-Path $env:TEMP (
        "u3w-recovery-$PID-$([guid]::NewGuid().ToString('N')).json")
    try {
        $remote = (
            "$SshTarget`:/opt/fbsir/admin/releases/" +
            "$TargetReleaseId/$Name")
        $arguments = @()
        $arguments += Get-SshOptions
        $arguments += @($remote, $temporary)
        & scp.exe @arguments
        if ($LASTEXITCODE -ne 0 -or
            -not (Test-Path -LiteralPath $temporary -PathType Leaf)) {
            throw "remote recovery file download failed: $Name"
        }
        $bytes = [IO.File]::ReadAllBytes($temporary)
        if ((Get-BytesSha256 $bytes) -cne $ExpectedSha256) {
            throw "remote recovery file digest drifted: $Name"
        }
        return ,$bytes
    }
    finally {
        Remove-Item -LiteralPath $temporary -Force `
            -ErrorAction SilentlyContinue
    }
}

function Invoke-RecoveryPlan {
    $git = Assert-StrictHead
    $worker = Invoke-RecoveryWorker `
        -WorkerMode InspectInterruptedApplyRecovery `
        -ApprovalBytes ([byte[]]@()) -PlanBytes ([byte[]]@())
    $result = $worker.result
    if (
        $result.schema -cne $WorkerResultSchema -or
        $result.mode -cne 'InspectInterruptedApplyRecovery' -or
        $result.state -cne
            'INTERRUPTED_APPLY_RECOVERY_PLAN_READY' -or
        $result.releaseId -cne $RecoveryRunId -or
        $result.sourceCommit -cne $ExpectedCommit -or
        $result.targetReleaseId -cne $TargetReleaseId -or
        $result.targetSourceCommit -cne $TargetSourceCommit -or
        $result.stageReceiptSha256 -cne
            $ExpectedStageReceiptSha256 -or
        $result.applyFailureReceiptSha256 -cne
            $ExpectedApplyFailureReceiptSha256 -or
        [string]$result.applyFailureManifestSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        $result.productionFilesystemChanged -ne $false -or
        $result.productionDatabaseChanged -ne $false -or
        $result.productionServiceChanged -ne $false -or
        $result.officialExpertsPackageChanged -ne $false
    ) {
        throw 'recovery inspection result is invalid'
    }
    if ($ExpectedApplyFailureManifestSha256 -and
        $result.applyFailureManifestSha256 -cne
            $ExpectedApplyFailureManifestSha256) {
        throw 'approved recovery failure manifest drifted'
    }
    $script:ExpectedApplyFailureManifestSha256 =
        [string]$result.applyFailureManifestSha256
    $generated = [DateTimeOffset]::UtcNow
    $plan = [ordered]@{
        schema = $RecoveryPlanSchema
        mode = 'RecoveryPlan'
        state = $RecoveryPlanState
        targetHost = 'api2.u3w.com'
        serviceUnit = 'fbsir-admin.service'
        recoveryRunId = $RecoveryRunId
        executorSourceCommit = $ExpectedCommit
        targetReleaseId = $TargetReleaseId
        targetSourceCommit = $TargetSourceCommit
        stageReceiptSha256 = $ExpectedStageReceiptSha256
        applyFailureReceiptSha256 =
            $ExpectedApplyFailureReceiptSha256
        applyFailureManifestSha256 =
            $ExpectedApplyFailureManifestSha256
        runnerSha256 = Get-RunnerSha256
        workerSha256 = Get-WorkerSha256
        productionFilesystemWrite = $true
        productionDatabaseWrite = $false
        productionServiceChange = $false
        officialExpertsPackageChange = $false
        generatedAt = $generated.ToString('o')
        expiresAt = $generated.AddHours(24).ToString('o')
    }
    $content = ($plan | ConvertTo-Json -Depth 12) + "`n"
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($content)
    $immutable = Write-ContentAddressedRecoveryPlan -Bytes $bytes
    Write-Utf8NoBomAtomic -Path $PlanOutputPath -Content $content
    $inspectionBytes = [Text.UTF8Encoding]::new($false).GetBytes(
        $worker.raw + "`n")
    $inspectionSha = Get-BytesSha256 $inspectionBytes
    $inspectionPath = Join-Path $RepoRoot (
        "work\recovery-plans\inspection-$inspectionSha.json")
    Write-Utf8NoBomAtomic -Path $inspectionPath -Content (
        $worker.raw + "`n")
    $null = Assert-StrictHead
    return [ordered]@{
        schema = 'fbsir.u3wInterruptedApplyRecoveryRunnerResult.v1'
        mode = 'Plan'
        state = $RecoveryPlanState
        recoveryRunId = $RecoveryRunId
        executorSourceCommit = $ExpectedCommit
        branch = $git.branch
        planReceiptPath = $immutable.path
        planReceiptSha256 = $immutable.sha256
        applyFailureManifestSha256 =
            $ExpectedApplyFailureManifestSha256
        inspectionReceiptPath = $inspectionPath
        inspectionReceiptSha256 = $inspectionSha
        productionChanged = $false
    }
}

function Invoke-Reconcile {
    $null = Assert-StrictHead
    $plan = Resolve-RecoveryPlan
    $approval = Resolve-RecoveryApproval -Plan $plan
    $approvalName = "approval-$($approval.sha256).json"
    $approvalEvidence = Write-ImmutableEvidence `
        -Name $approvalName -Bytes $approval.bytes
    $planEvidence = Write-ImmutableEvidence `
        -Name "plan-$($plan.sha256).json" -Bytes $plan.bytes
    $worker = Invoke-RecoveryWorker `
        -WorkerMode CanonicalizeInterruptedApplyRecovery `
        -ApprovalBytes $approval.bytes -PlanBytes $plan.bytes
    $result = $worker.result
    $allowedDispositions = @(
        'CANONICALIZED', 'RECEIPT_LINK_REPAIRED', 'ALREADY_EXACT')
    if (
        $result.schema -cne $WorkerResultSchema -or
        $result.mode -cne 'CanonicalizeInterruptedApplyRecovery' -or
        $result.state -cne $RecoveryState -or
        $allowedDispositions -cnotcontains
            [string]$result.recoveryDisposition -or
        $result.releaseId -cne $RecoveryRunId -or
        $result.sourceCommit -cne $ExpectedCommit -or
        $result.targetReleaseId -cne $TargetReleaseId -or
        $result.targetSourceCommit -cne $TargetSourceCommit -or
        [string]$result.receiptSha256 -notmatch '^[0-9a-f]{64}$' -or
        $result.productionFilesystemChanged -isnot [bool] -or
        $result.productionDatabaseChanged -ne $false -or
        $result.productionServiceChanged -ne $false -or
        $result.officialExpertsPackageChanged -ne $false
    ) {
        throw 'recovery canonicalization result is invalid'
    }
    $receiptBytes = Get-RemoteRecoveryFile `
        -Name 'interrupted-apply-recovery-receipt.json' `
        -ExpectedSha256 $result.receiptSha256
    $remoteApprovalBytes = Get-RemoteRecoveryFile `
        -Name 'interrupted-apply-recovery-approval.json' `
        -ExpectedSha256 $approval.sha256
    $remotePlanBytes = Get-RemoteRecoveryFile `
        -Name 'interrupted-apply-recovery-plan.json' `
        -ExpectedSha256 $plan.sha256
    if (
        (Get-BytesSha256 $remoteApprovalBytes) -cne
            (Get-BytesSha256 $approval.bytes) -or
        (Get-BytesSha256 $remotePlanBytes) -cne
            (Get-BytesSha256 $plan.bytes)
    ) {
        throw 'remote recovery approval or Plan bytes drifted'
    }
    $receiptFields = @(
        'schema', 'state', 'releaseId', 'sourceCommit',
        'recoveryRunId', 'executorSourceCommit',
        'approvalReceiptSha256', 'approvalNonce', 'runnerSha256',
        'workerSha256', 'recoveryPlanReceiptSha256',
        'stageReceiptPath', 'stageReceiptSha256',
        'applyFailureReceiptPath', 'applyFailureReceiptSha256',
        'applyFailureReceiptSchema', 'applyFailureReceiptState',
        'applyFailureReceiptManifest',
        'applyFailureReceiptManifestSha256', 'applicationRestored',
        'topologyRestored', 'deploymentCommitOutcome',
        'deploymentReceiptAbsent', 'rollbackReceiptAbsent',
        'currentLinkAbsent', 'releaseDropInMatched',
        'retainedMigrationFacts', 'allW1aFlagsExplicitFalse',
        'databaseDownClaimed', 'productionFilesystemChanged',
        'productionDatabaseChanged',
        'productionDatabaseChangedThisRecoveryRun',
        'productionDatabaseChangedSinceStage',
        'productionServiceChanged',
        'productionServiceChangedThisRecoveryRun',
        'productionServiceChangedSinceStage',
        'officialExpertsPackageChanged', 'observedAt'
    )
    $receipt = Get-ExactJson -Bytes $receiptBytes `
        -Fields $receiptFields -Label 'recovery receipt'
    if (
        $receipt.schema -cne $RecoveryReceiptSchema -or
        $receipt.state -cne $RecoveryState -or
        $receipt.releaseId -cne $TargetReleaseId -or
        $receipt.sourceCommit -cne $TargetSourceCommit -or
        $receipt.recoveryRunId -cne $RecoveryRunId -or
        $receipt.executorSourceCommit -cne $ExpectedCommit -or
        $receipt.approvalReceiptSha256 -cne $approval.sha256 -or
        $receipt.approvalNonce -cne
            $approval.receipt.approvalNonce -or
        $receipt.runnerSha256 -cne (Get-RunnerSha256) -or
        $receipt.workerSha256 -cne (Get-WorkerSha256) -or
        $receipt.recoveryPlanReceiptSha256 -cne $plan.sha256 -or
        $receipt.stageReceiptSha256 -cne
            $ExpectedStageReceiptSha256 -or
        $receipt.applyFailureReceiptSha256 -cne
            $ExpectedApplyFailureReceiptSha256 -or
        $receipt.applyFailureReceiptManifestSha256 -cne
            $ExpectedApplyFailureManifestSha256 -or
        $receipt.applicationRestored -ne $true -or
        $receipt.topologyRestored -ne $true -or
        $receipt.deploymentCommitOutcome -cne 'NOT_COMMITTED' -or
        $receipt.deploymentReceiptAbsent -ne $true -or
        $receipt.rollbackReceiptAbsent -ne $true -or
        $receipt.currentLinkAbsent -ne $true -or
        $receipt.releaseDropInMatched -ne $true -or
        $receipt.allW1aFlagsExplicitFalse -ne $true -or
        $receipt.productionDatabaseChangedThisRecoveryRun -ne $false -or
        $receipt.productionServiceChangedThisRecoveryRun -ne $false -or
        $receipt.officialExpertsPackageChanged -ne $false
    ) {
        throw 'downloaded recovery receipt is invalid'
    }
    $workerBytes = [Text.UTF8Encoding]::new($false).GetBytes(
        $worker.raw + "`n")
    $workerSha = Get-BytesSha256 $workerBytes
    $workerEvidence = Write-ImmutableEvidence `
        -Name "reconcile-$workerSha-worker-result.json" `
        -Bytes $workerBytes
    $receiptEvidence = Write-ImmutableEvidence `
        -Name "recovery-$($result.receiptSha256)-receipt.json" `
        -Bytes $receiptBytes
    $null = Assert-StrictHead
    return [ordered]@{
        schema = 'fbsir.u3wInterruptedApplyRecoveryRunnerResult.v1'
        mode = 'Reconcile'
        state = $RecoveryState
        recoveryDisposition = $result.recoveryDisposition
        recoveryRunId = $RecoveryRunId
        executorSourceCommit = $ExpectedCommit
        targetReleaseId = $TargetReleaseId
        targetSourceCommit = $TargetSourceCommit
        recoveryReceiptPath = $result.receiptPath
        recoveryReceiptSha256 = $result.receiptSha256
        approvalEvidencePath = $approvalEvidence
        planEvidencePath = $planEvidence
        receiptEvidencePath = $receiptEvidence
        workerEvidencePath = $workerEvidence
        productionFilesystemChanged =
            $result.productionFilesystemChanged
        productionDatabaseChanged = $false
        productionServiceChanged = $false
        officialExpertsPackageChanged = $false
    }
}

if ($Mode -eq 'Reconcile' -and
    (-not $ExpectedApplyFailureManifestSha256 -or
        -not $ExpectedRecoveryPlanReceiptSha256)) {
    throw 'Reconcile requires failure manifest and recovery Plan digests'
}

$result = if ($Mode -eq 'Plan') {
    Invoke-RecoveryPlan
} else {
    Invoke-Reconcile
}
$result | ConvertTo-Json -Depth 20
