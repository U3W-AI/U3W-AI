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
        '^w1a-stage-recovery-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z$')]
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
    [string]$ExpectedTargetStageReceiptSha256,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedPriorRecoveryReceiptSha256,
    [string]$ApprovalReceiptPath,
    [string]$PlanOutputPath,
    [string]$ExternalEvidenceDirectory,
    [string]$SshKeyPath = $(if ($env:U3W_SSH_KEY_PATH) {
            $env:U3W_SSH_KEY_PATH
        } else {
            Join-Path $env:USERPROFILE '.ssh\id_ed25519_api2'
        }),
    [string]$KnownHostsPath = $(Join-Path $env:TEMP (
            'u3w-staged-release-recovery-known-hosts'))
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$SshTarget = 'root@api2.u3w.com'
$ExpectedPublicKeyFingerprint =
    'SHA256:oCIzbO9W94qDBeS9MtetLCW0CjYMkyZqT6QkgcLkXk0'
$ExpectedRemoteHostKeyFingerprint =
    'SHA256:GkS/HJpLm48N+KaMV/WEVSBHnI8PKJsU+6ycAsn+mBA'
$ZeroSha256 = '0' * 64
$RunnerSchema = 'fbsir.u3wStagedReleaseRecoveryRunnerResult.v1'
$PlanSchema = 'fbsir.u3wStagedReleaseRecoveryPlan.v1'
$ApprovalSchema = 'fbsir.u3wStagedReleaseRecoveryApproval.v1'
$WorkerResultSchema =
    'fbsir.u3wStagedReleaseRecoveryWorkerResult.v1'
$ReceiptSchema = 'fbsir.u3wStagedReleaseRecoveryReceipt.v1'
$PriorRecoveryState =
    'INTERRUPTED_APPLY_RECOVERED_APPLICATION_DATABASE_043_RETAINED_DORMANT'
$TargetSuffix = $TargetReleaseId -replace '^w1a-release-', ''
$QuarantineName = (
    ".quarantine-stage-$TargetSuffix-" +
    $ExpectedTargetStageReceiptSha256.Substring(0, 12))
$ExpectedQuarantinePath =
    "/opt/fbsir/admin/releases/$QuarantineName"
$ExpectedRemoteReceiptPath = (
    "/opt/fbsir/admin/releases/stage-recovery-$TargetSuffix-" +
    $ExpectedTargetStageReceiptSha256.Substring(0, 12) + '.json')

if (-not $PlanOutputPath) {
    $PlanOutputPath = Join-Path $RepoRoot (
        'work\staged-release-recovery-plans\latest.json')
}
if (-not $ExternalEvidenceDirectory) {
    $handoffRoot = Split-Path (
        Split-Path $RepoRoot -Parent) -Parent
    $ExternalEvidenceDirectory = Join-Path $handoffRoot (
        "deliverables\production-evidence\$RecoveryRunId")
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
            'scripts/reconcile-u3w-staged-release.ps1'))
}

function Get-WorkerSha256 {
    return Get-BytesSha256 (Get-CommittedBlobBytes (
            'scripts/u3w-staged-release-recovery-remote.py'))
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
        'scripts/reconcile-u3w-staged-release.ps1')
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

function Write-Utf8NoBomAtomic {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )
    $directory = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force |
            Out-Null
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

function Write-ContentAddressedPlan {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)
    $digest = Get-BytesSha256 $Bytes
    $directory = Join-Path $RepoRoot (
        'work\staged-release-recovery-plans\by-sha256')
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        New-Item -ItemType Directory -Path $directory -Force |
            Out-Null
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
    if ((Get-BytesSha256 ([IO.File]::ReadAllBytes($path))) -cne
        $digest) {
        throw 'content-addressed Stage recovery Plan drifted'
    }
    (Get-Item -LiteralPath $path -Force).IsReadOnly = $true
    return [ordered]@{ path = $path; sha256 = $digest }
}

function Invoke-RemoteWorker {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('Plan', 'Reconcile')]
        [string]$RemoteMode,
        [AllowEmptyCollection()]
        [byte[]]$ApprovalBytes
    )
    Assert-PrivateKey
    Ensure-KnownHosts
    $workerBytes = Get-CommittedBlobBytes (
        'scripts/u3w-staged-release-recovery-remote.py')
    $workerSha = Get-BytesSha256 $workerBytes
    if ($workerSha -cne (Get-WorkerSha256)) {
        throw 'worker sha256 mismatch'
    }
    $approvalSha = if ($ApprovalBytes.Count -gt 0) {
        Get-BytesSha256 $ApprovalBytes
    } else {
        $ZeroSha256
    }
    $approvalBase64 = if ($ApprovalBytes.Count -gt 0) {
        [Convert]::ToBase64String($ApprovalBytes)
    } else {
        ''
    }
    $arguments = @(
        'u3w-staged-release-recovery-remote.py',
        '--mode', $RemoteMode,
        '--run-id', $RecoveryRunId,
        '--recovery-source-commit', $ExpectedCommit,
        '--target-release-id', $TargetReleaseId,
        '--target-source-commit', $TargetSourceCommit,
        '--target-stage-receipt-sha',
            $ExpectedTargetStageReceiptSha256,
        '--prior-recovery-receipt-sha',
            $ExpectedPriorRecoveryReceiptSha256,
        '--runner-sha', (Get-RunnerSha256),
        '--worker-sha', $workerSha,
        '--approval-sha', $approvalSha,
        '--approval-json-base64', $approvalBase64
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
        'exec(compile(b,"<u3w-stage-recovery-worker>","exec"),' +
        '{"__name__":"__main__"})'
    $remoteCommand = "python3 -c '$($bootstrap.Replace('"', '\"'))'"
    $sshArguments = @()
    $sshArguments += Get-SshOptions
    $sshArguments += @($SshTarget, $remoteCommand)
    $output = @($payload | & ssh.exe @sshArguments)
    $exitCode = $LASTEXITCODE
    if ($output.Count -eq 0) {
        throw "$RemoteMode returned no result"
    }
    $raw = ($output -join "`n").Trim()
    try {
        $result = $raw | ConvertFrom-Json
    }
    catch {
        throw "$RemoteMode returned invalid JSON"
    }
    if ($exitCode -ne 0) {
        $failureBytes = [Text.UTF8Encoding]::new($false).GetBytes(
            $raw + "`n")
        $failureSha = Get-BytesSha256 $failureBytes
        $failurePath = Write-ImmutableEvidence `
            -Name "worker-failure-$failureSha.json" `
            -Bytes $failureBytes
        throw "$RemoteMode failed; immutable evidence: $failurePath"
    }
    return [ordered]@{ result = $result; raw = $raw }
}

function Assert-PlanResult {
    param([Parameter(Mandatory = $true)]$Worker)
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes(
        $Worker.raw + "`n")
    $fields = @(
        'schema', 'mode', 'state', 'runId',
        'recoverySourceCommit', 'targetReleaseId',
        'targetSourceCommit', 'targetStageReceiptSha256',
        'priorRecoveryReceiptSha256', 'quarantinePath',
        'stagedTree', 'serviceSnapshotSha256',
        'databaseSnapshotSha256', 'nginxSha256',
        'nginxManifestSha256', 'nginxDumpSha256',
        'currentLinkAbsent', 'productionChanged',
        'officialExpertsPackageChanged', 'observedAt'
    )
    $plan = Get-ExactJson -Bytes $bytes -Fields $fields `
        -Label 'Stage recovery Plan'
    $allowedStates = @(
        'STAGED_RECOVERY_ELIGIBLE',
        'QUARANTINED_LATEST_REPAIR_PENDING',
        'RECOVERY_FINALIZATION_PENDING',
        'ALREADY_RECONCILED'
    )
    if (
        $plan.schema -cne $PlanSchema -or
        $plan.mode -cne 'Plan' -or
        $allowedStates -cnotcontains [string]$plan.state -or
        $plan.runId -cne $RecoveryRunId -or
        $plan.recoverySourceCommit -cne $ExpectedCommit -or
        $plan.targetReleaseId -cne $TargetReleaseId -or
        $plan.targetSourceCommit -cne $TargetSourceCommit -or
        $plan.targetStageReceiptSha256 -cne
            $ExpectedTargetStageReceiptSha256 -or
        $plan.priorRecoveryReceiptSha256 -cne
            $ExpectedPriorRecoveryReceiptSha256 -or
        $plan.quarantinePath -cne $ExpectedQuarantinePath -or
        [string]$plan.stagedTree.sha256 -notmatch
            '^[0-9a-f]{64}$' -or
        $plan.stagedTree.fileCount -isnot [long] -and
            $plan.stagedTree.fileCount -isnot [int] -or
        $plan.stagedTree.fileCount -le 0 -or
        $plan.stagedTree.totalBytes -le 0 -or
        [string]$plan.serviceSnapshotSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        [string]$plan.databaseSnapshotSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        [string]$plan.nginxSha256 -notmatch '^[0-9a-f]{64}$' -or
        [string]$plan.nginxManifestSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        [string]$plan.nginxDumpSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        $plan.currentLinkAbsent -ne $true -or
        $plan.productionChanged -ne $false -or
        $plan.officialExpertsPackageChanged -ne $false
    ) {
        throw 'Stage recovery Plan identity is invalid'
    }
    return [ordered]@{
        receipt = $plan
        bytes = $bytes
        sha256 = Get-BytesSha256 $bytes
    }
}

function Resolve-RecoveryApproval {
    param([bool]$RequireCurrent = $true)
    if (-not $ApprovalReceiptPath -or
        -not (Test-Path -LiteralPath $ApprovalReceiptPath `
            -PathType Leaf)) {
        throw 'Reconcile requires ApprovalReceiptPath'
    }
    $resolved = (Resolve-Path -LiteralPath $ApprovalReceiptPath).Path
    $item = Get-Item -LiteralPath $resolved -Force
    if (($item.Attributes -band
            [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'recovery approval cannot be a link or reparse point'
    }
    $bytes = [IO.File]::ReadAllBytes($resolved)
    $fields = @(
        'schema', 'action', 'targetHost', 'runId',
        'recoverySourceCommit', 'targetReleaseId',
        'targetSourceCommit', 'targetStageReceiptSha256',
        'priorRecoveryReceiptSha256', 'approvedAt', 'expiresAt',
        'authorizedBy', 'concurrentDdlProhibited',
        'productionFilesystemWrite', 'productionDatabaseWrite',
        'productionServiceChange', 'officialExpertsPackageChange',
        'runnerSha256', 'workerSha256'
    )
    $approval = Get-ExactJson -Bytes $bytes -Fields $fields `
        -Label 'Stage recovery approval'
    $approved = [DateTimeOffset]::Parse($approval.approvedAt)
    $expires = [DateTimeOffset]::Parse($approval.expiresAt)
    $now = [DateTimeOffset]::UtcNow
    if (
        $approval.schema -cne $ApprovalSchema -or
        $approval.action -cne 'RECONCILE_STAGED_W1A_RELEASE' -or
        $approval.targetHost -cne 'api2.u3w.com' -or
        $approval.runId -cne $RecoveryRunId -or
        $approval.recoverySourceCommit -cne $ExpectedCommit -or
        $approval.targetReleaseId -cne $TargetReleaseId -or
        $approval.targetSourceCommit -cne $TargetSourceCommit -or
        $approval.targetStageReceiptSha256 -cne
            $ExpectedTargetStageReceiptSha256 -or
        $approval.priorRecoveryReceiptSha256 -cne
            $ExpectedPriorRecoveryReceiptSha256 -or
        $approval.authorizedBy -cne 'workspace-user' -or
        $approval.concurrentDdlProhibited -ne $true -or
        $approval.productionFilesystemWrite -ne $true -or
        $approval.productionDatabaseWrite -ne $false -or
        $approval.productionServiceChange -ne $false -or
        $approval.officialExpertsPackageChange -ne $false -or
        $approval.runnerSha256 -cne (Get-RunnerSha256) -or
        $approval.workerSha256 -cne (Get-WorkerSha256) -or
        $approved -gt $now -or
        $expires -le $approved -or
        ($expires - $approved).TotalHours -gt 24 -or
        (
            $RequireCurrent -and
            $expires -le $now
        )
    ) {
        throw 'recovery approval identity, scope or validity is invalid'
    }
    return [ordered]@{
        receipt = $approval
        bytes = $bytes
        sha256 = Get-BytesSha256 $bytes
    }
}

function Get-RemoteReceiptBytes {
    param([Parameter(Mandatory = $true)][string]$ExpectedSha256)
    Assert-PrivateKey
    Ensure-KnownHosts
    $temporary = Join-Path $env:TEMP (
        "u3w-stage-recovery-$PID-" +
        "$([guid]::NewGuid().ToString('N')).json")
    try {
        $remote = "$SshTarget`:$ExpectedRemoteReceiptPath"
        $arguments = @()
        $arguments += Get-SshOptions
        $arguments += @($remote, $temporary)
        & scp.exe @arguments
        if ($LASTEXITCODE -ne 0 -or
            -not (Test-Path -LiteralPath $temporary -PathType Leaf)) {
            throw 'remote Stage recovery receipt download failed'
        }
        $bytes = [IO.File]::ReadAllBytes($temporary)
        if ((Get-BytesSha256 $bytes) -cne $ExpectedSha256) {
            throw 'remote Stage recovery receipt digest drifted'
        }
        return ,$bytes
    }
    finally {
        Remove-Item -LiteralPath $temporary -Force `
            -ErrorAction SilentlyContinue
    }
}

function Assert-RecoveryReceipt {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Bytes,
        [Parameter(Mandatory = $true)][string]$ApprovalSha256
    )
    $fields = @(
        'schema', 'state', 'runId', 'recoverySourceCommit',
        'targetReleaseId', 'targetSourceCommit',
        'targetStageReceiptSha256', 'priorRecoveryReceiptSha256',
        'approvalReceiptSha256', 'runnerSha256', 'workerSha256',
        'quarantinePath', 'quarantineNameExecutableAsRelease',
        'stagedTree', 'serviceSnapshotSha256',
        'databaseSnapshotSha256', 'nginxSha256',
        'nginxManifestSha256', 'nginxDumpSha256',
        'latestReceiptStateAfter', 'currentLinkAbsent',
        'deploymentReceiptAbsent', 'rollbackReceiptAbsent',
        'applyFailureReceiptAbsent', 'serviceIdentityUnchanged',
        'databaseIdentityUnchanged', 'nginxIdentityUnchanged',
        'productionFilesystemChanged', 'productionDatabaseChanged',
        'productionServiceChanged', 'officialExpertsPackageChanged',
        'observedAt'
    )
    $receipt = Get-ExactJson -Bytes $Bytes -Fields $fields `
        -Label 'Stage recovery receipt'
    if (
        $receipt.schema -cne $ReceiptSchema -or
        $receipt.state -cne 'STAGE_RECONCILED' -or
        $receipt.runId -cne $RecoveryRunId -or
        $receipt.recoverySourceCommit -cne $ExpectedCommit -or
        $receipt.targetReleaseId -cne $TargetReleaseId -or
        $receipt.targetSourceCommit -cne $TargetSourceCommit -or
        $receipt.targetStageReceiptSha256 -cne
            $ExpectedTargetStageReceiptSha256 -or
        $receipt.priorRecoveryReceiptSha256 -cne
            $ExpectedPriorRecoveryReceiptSha256 -or
        $receipt.approvalReceiptSha256 -cne $ApprovalSha256 -or
        $receipt.runnerSha256 -cne (Get-RunnerSha256) -or
        $receipt.workerSha256 -cne (Get-WorkerSha256) -or
        $receipt.quarantinePath -cne $ExpectedQuarantinePath -or
        $receipt.quarantineNameExecutableAsRelease -ne $false -or
        [string]$receipt.stagedTree.sha256 -notmatch
            '^[0-9a-f]{64}$' -or
        [string]$receipt.serviceSnapshotSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        [string]$receipt.databaseSnapshotSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        [string]$receipt.nginxSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        [string]$receipt.nginxManifestSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        [string]$receipt.nginxDumpSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        $receipt.latestReceiptStateAfter -cne
            $PriorRecoveryState -or
        $receipt.currentLinkAbsent -ne $true -or
        $receipt.deploymentReceiptAbsent -ne $true -or
        $receipt.rollbackReceiptAbsent -ne $true -or
        $receipt.applyFailureReceiptAbsent -ne $true -or
        $receipt.serviceIdentityUnchanged -ne $true -or
        $receipt.databaseIdentityUnchanged -ne $true -or
        $receipt.nginxIdentityUnchanged -ne $true -or
        $receipt.productionFilesystemChanged -ne $true -or
        $receipt.productionDatabaseChanged -ne $false -or
        $receipt.productionServiceChanged -ne $false -or
        $receipt.officialExpertsPackageChanged -ne $false
    ) {
        throw 'Stage recovery receipt identity is invalid'
    }
    return $receipt
}

function Invoke-Plan {
    $git = Assert-StrictHead
    $worker = Invoke-RemoteWorker -RemoteMode Plan `
        -ApprovalBytes ([byte[]]@())
    $plan = Assert-PlanResult -Worker $worker
    $immutable = Write-ContentAddressedPlan -Bytes $plan.bytes
    Write-Utf8NoBomAtomic -Path $PlanOutputPath -Content (
        $worker.raw + "`n")
    $null = Assert-StrictHead
    return [ordered]@{
        schema = $RunnerSchema
        mode = 'Plan'
        state = $plan.receipt.state
        recoveryRunId = $RecoveryRunId
        recoverySourceCommit = $ExpectedCommit
        branch = $git.branch
        targetReleaseId = $TargetReleaseId
        targetSourceCommit = $TargetSourceCommit
        targetStageReceiptSha256 =
            $ExpectedTargetStageReceiptSha256
        priorRecoveryReceiptSha256 =
            $ExpectedPriorRecoveryReceiptSha256
        planReceiptPath = $immutable.path
        planReceiptSha256 = $immutable.sha256
        productionChanged = $false
    }
}

function Invoke-Reconcile {
    $null = Assert-StrictHead
    $preflightWorker = Invoke-RemoteWorker -RemoteMode Plan `
        -ApprovalBytes ([byte[]]@())
    $preflight = Assert-PlanResult -Worker $preflightWorker
    $approval = Resolve-RecoveryApproval -RequireCurrent (
        $preflight.receipt.state -cne 'ALREADY_RECONCILED')
    $planEvidence = Write-ImmutableEvidence `
        -Name "plan-$($preflight.sha256).json" `
        -Bytes $preflight.bytes
    $approvalEvidence = Write-ImmutableEvidence `
        -Name "approval-$($approval.sha256).json" `
        -Bytes $approval.bytes
    $worker = Invoke-RemoteWorker -RemoteMode Reconcile `
        -ApprovalBytes $approval.bytes
    $resultBytes = [Text.UTF8Encoding]::new($false).GetBytes(
        $worker.raw + "`n")
    $resultFields = @(
        'schema', 'mode', 'state', 'runId', 'targetReleaseId',
        'receiptPath', 'receiptSha256', 'approvalReceiptSha256',
        'productionFilesystemChanged', 'productionDatabaseChanged',
        'productionServiceChanged', 'officialExpertsPackageChanged'
    )
    $result = Get-ExactJson -Bytes $resultBytes `
        -Fields $resultFields -Label 'Stage recovery worker result'
    $allowedStates = @('STAGE_RECONCILED', 'ALREADY_RECONCILED')
    if (
        $result.schema -cne $WorkerResultSchema -or
        $result.mode -cne 'Reconcile' -or
        $allowedStates -cnotcontains [string]$result.state -or
        $result.runId -cne $RecoveryRunId -or
        $result.targetReleaseId -cne $TargetReleaseId -or
        $result.receiptPath -cne $ExpectedRemoteReceiptPath -or
        [string]$result.receiptSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        [string]$result.approvalReceiptSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        $result.productionFilesystemChanged -isnot [bool] -or
        $result.productionDatabaseChanged -ne $false -or
        $result.productionServiceChanged -ne $false -or
        $result.officialExpertsPackageChanged -ne $false
    ) {
        throw 'Stage recovery worker result is invalid'
    }
    if (
        ($result.state -ceq 'STAGE_RECONCILED' -and
            (
                $result.productionFilesystemChanged -ne $true -or
                $result.approvalReceiptSha256 -cne $approval.sha256
            )) -or
        ($result.state -ceq 'ALREADY_RECONCILED' -and
            $result.productionFilesystemChanged -ne $false)
    ) {
        throw 'Stage recovery mutation disposition is invalid'
    }
    $receiptBytes = Get-RemoteReceiptBytes `
        -ExpectedSha256 $result.receiptSha256
    $null = Assert-RecoveryReceipt -Bytes $receiptBytes `
        -ApprovalSha256 $result.approvalReceiptSha256
    $receiptEvidence = Write-ImmutableEvidence `
        -Name "recovery-$($result.receiptSha256)-receipt.json" `
        -Bytes $receiptBytes
    $workerSha = Get-BytesSha256 $resultBytes
    $workerEvidence = Write-ImmutableEvidence `
        -Name "reconcile-$workerSha-worker-result.json" `
        -Bytes $resultBytes
    $null = Assert-StrictHead
    return [ordered]@{
        schema = $RunnerSchema
        mode = 'Reconcile'
        state = $result.state
        recoveryRunId = $RecoveryRunId
        recoverySourceCommit = $ExpectedCommit
        targetReleaseId = $TargetReleaseId
        targetSourceCommit = $TargetSourceCommit
        recoveryReceiptPath = $result.receiptPath
        recoveryReceiptSha256 = $result.receiptSha256
        planEvidencePath = $planEvidence
        approvalEvidencePath = $approvalEvidence
        receiptEvidencePath = $receiptEvidence
        workerEvidencePath = $workerEvidence
        productionFilesystemChanged =
            $result.productionFilesystemChanged
        productionDatabaseChanged = $false
        productionServiceChanged = $false
        officialExpertsPackageChanged = $false
    }
}

if ($Mode -eq 'Reconcile' -and -not $ApprovalReceiptPath) {
    throw 'Reconcile requires ApprovalReceiptPath'
}

$result = if ($Mode -eq 'Plan') {
    Invoke-Plan
} else {
    Invoke-Reconcile
}
$result | ConvertTo-Json -Depth 20
