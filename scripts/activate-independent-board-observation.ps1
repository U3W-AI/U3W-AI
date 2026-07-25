[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Plan', 'Apply', 'Verify', 'Recover')]
    [string]$Mode,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string]$ExpectedCommit,
    [ValidatePattern(
        '^w1a-observe-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$')]
    [string]$ActivationId,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedEnvironmentSha256 = ('0' * 64),
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string]$ExpectedDeployedCommit =
        '13c203a5c42d8a8f977be8a9834c2ad147b62bb8',
    [ValidatePattern(
        '^w1a-release-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z$')]
    [string]$ExpectedReleaseId =
        'w1a-release-13c203a5c42d-20260725T040706Z',
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedDeploymentReceiptSha256 =
        '8f7167018a99ba5740418e7693f302a6dbadaae1ac84efe1510bcaab7b1e7731',
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$OfficialExpertsPackageSha256 =
        '57443e8fbcbcd2620736bce35edccc8001e578918e13ee6521f618d983348510',
    [string]$OfficialExpertsPackagePath,
    [switch]$ConfirmProductionActivation,
    [string]$ExternalEvidenceDirectory,
    [string]$SshKeyPath = $(if ($env:U3W_SSH_KEY_PATH) {
            $env:U3W_SSH_KEY_PATH
        } else {
            Join-Path $env:USERPROFILE '.ssh\id_ed25519_api2'
        }),
    [string]$KnownHostsPath = $(
        Join-Path $env:TEMP 'u3w-default-off-release-known-hosts'
    )
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$WorkerPath = Join-Path $PSScriptRoot (
    'u3w-observation-activation-remote.py')
$SshTarget = 'root@api2.u3w.com'
$ExpectedPublicKeyFingerprint =
    'SHA256:oCIzbO9W94qDBeS9MtetLCW0CjYMkyZqT6QkgcLkXk0'
$ExpectedRemoteHostKeyFingerprint =
    'SHA256:GkS/HJpLm48N+KaMV/WEVSBHnI8PKJsU+6ycAsn+mBA'

if (-not $ActivationId) {
    $ActivationId = 'w1a-observe-{0}-{1}' -f
        [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'),
        $ExpectedCommit.Substring(0, 12)
}
if (-not $OfficialExpertsPackagePath) {
    $handoffRoot = Split-Path (Split-Path $RepoRoot -Parent) -Parent
    $OfficialExpertsPackagePath = Join-Path $handoffRoot (
        'deliverables\packages\' +
        'fbsir-eight-seat-board-26.7.21-official-experts-baseline.zip')
}
if (-not $ExternalEvidenceDirectory) {
    $handoffRoot = Split-Path (Split-Path $RepoRoot -Parent) -Parent
    $ExternalEvidenceDirectory = Join-Path $handoffRoot (
        "deliverables\production-evidence\$ActivationId")
}

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "required file missing: $Path"
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).
        Hash.ToLowerInvariant()
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

function Write-CreateNewUtf8 {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )
    $stream = [IO.File]::Open(
        $Path,
        [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write,
        [IO.FileShare]::None
    )
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Content)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    }
    finally {
        $stream.Dispose()
    }
}

function Assert-StrictHead {
    $head = (& git -C $RepoRoot rev-parse HEAD).
        Trim().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0 -or $head -ne $ExpectedCommit) {
        throw "strict HEAD mismatch: expected $ExpectedCommit, actual $head"
    }
    $dirty = @(& git -C $RepoRoot status --porcelain=v1)
    if ($LASTEXITCODE -ne 0 -or $dirty.Count -ne 0) {
        throw 'strict HEAD requires a clean worktree'
    }
    $branch = (& git -C $RepoRoot branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $branch) {
        throw 'strict HEAD requires a named branch'
    }
    $upstream = (& git -C $RepoRoot rev-parse '@{upstream}').
        Trim().ToLowerInvariant()
    $remoteRows = @(
        & git -C $RepoRoot ls-remote --exit-code origin (
            "refs/heads/$branch")
    )
    if ($LASTEXITCODE -ne 0 -or $remoteRows.Count -ne 1) {
        throw "origin branch is absent or ambiguous: $branch"
    }
    $remoteHead = ($remoteRows[0] -split '\s+', 2)[0].
        ToLowerInvariant()
    if ($upstream -ne $ExpectedCommit -or
        $remoteHead -ne $ExpectedCommit) {
        throw 'HEAD, upstream and origin must be byte-aligned'
    }
    return [ordered]@{
        head = $head
        branch = $branch
        upstream = $upstream
        originHead = $remoteHead
    }
}

function Assert-SshTrust {
    if (-not (Test-Path -LiteralPath $SshKeyPath -PathType Leaf)) {
        throw "SSH private key missing: $SshKeyPath"
    }
    if (-not (Test-Path -LiteralPath $KnownHostsPath -PathType Leaf)) {
        throw "pinned known-hosts file missing: $KnownHostsPath"
    }
    $derived = @(
        & ssh-keygen.exe -y -f $SshKeyPath 2>$null |
            & ssh-keygen.exe -lf - -E sha256 2>$null
    ) -join "`n"
    if ($LASTEXITCODE -ne 0 -or
        $derived -notmatch [regex]::Escape(
            $ExpectedPublicKeyFingerprint)) {
        throw 'SSH client key fingerprint mismatch'
    }
    $hostRows = @(
        & ssh-keygen.exe -F 'api2.u3w.com' -f (
            $KnownHostsPath) 2>$null |
            Where-Object { $_ -notmatch '^#' }
    )
    if ($hostRows.Count -ne 1) {
        throw 'pinned host key is absent or ambiguous'
    }
    $hostFingerprint = @(
        $hostRows | & ssh-keygen.exe -lf - -E sha256 2>$null
    ) -join "`n"
    if ($LASTEXITCODE -ne 0 -or
        $hostFingerprint -notmatch [regex]::Escape(
            $ExpectedRemoteHostKeyFingerprint)) {
        throw 'remote host key fingerprint mismatch'
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

if (($Mode -eq 'Apply' -or $Mode -eq 'Recover') -and
    -not $ConfirmProductionActivation) {
    throw "$Mode requires -ConfirmProductionActivation"
}
if ($Mode -eq 'Apply' -and
    $ExpectedEnvironmentSha256 -eq ('0' * 64)) {
    throw 'Apply requires the exact environment digest returned by Plan'
}

$headEvidence = Assert-StrictHead
Assert-SshTrust
$observedOfficialPackageSha = Get-Sha256 $OfficialExpertsPackagePath
if ($observedOfficialPackageSha -ne $OfficialExpertsPackageSha256) {
    throw 'official experts package digest drifted'
}
$runnerSha = Get-Sha256 $PSCommandPath
$workerBytes = [IO.File]::ReadAllBytes($WorkerPath)
$workerSha = Get-BytesSha256 $workerBytes
$workerBase64 = [Convert]::ToBase64String($workerBytes)
$authorization = if ($Mode -eq 'Apply' -or $Mode -eq 'Recover') {
    'explicit-user-authority'
} else {
    'read-only'
}
$attemptId = [Guid]::NewGuid().ToString('N')
New-Item -ItemType Directory -Path $ExternalEvidenceDirectory -Force |
    Out-Null
$attemptIntentPath = Join-Path $ExternalEvidenceDirectory (
    '{0}-attempt-{1}-intent.json' -f
        $Mode.ToLowerInvariant(), $attemptId)
$attemptIntent = [ordered]@{
    schema = 'fbsir.u3wObservationActivationAttempt.v1'
    attemptId = $attemptId
    mode = $Mode
    activationId = $ActivationId
    sourceCommit = $ExpectedCommit
    expectedEnvironmentSha256 = $ExpectedEnvironmentSha256
    runnerSha256 = $runnerSha
    workerSha256 = $workerSha
    productionMutationRequested = (
        $Mode -eq 'Apply' -or $Mode -eq 'Recover')
    recordedAt = [DateTime]::UtcNow.ToString(
        'yyyy-MM-ddTHH:mm:ss.fffZ')
}
Write-CreateNewUtf8 -Path $attemptIntentPath -Content (
    ($attemptIntent | ConvertTo-Json -Depth 20 -Compress) +
        [Environment]::NewLine)
$remoteCommand = @(
    "tr -d '\r\n' | base64 -d | python3 -",
    '--mode', $Mode,
    '--activation-id', $ActivationId,
    '--source-commit', $ExpectedCommit,
    '--runner-sha', $runnerSha,
    '--worker-sha', $workerSha,
    '--expected-environment-sha', $ExpectedEnvironmentSha256,
    '--expected-deployed-commit', $ExpectedDeployedCommit,
    '--expected-release-id', $ExpectedReleaseId,
    '--expected-deployment-receipt-sha',
    $ExpectedDeploymentReceiptSha256,
    '--official-package-sha', $OfficialExpertsPackageSha256,
    '--authorization', $authorization
) -join ' '
$sshArguments = @()
$sshArguments += Get-SshOptions
$sshArguments += @($SshTarget, $remoteCommand)
$output = @($workerBase64 | & ssh.exe @sshArguments)
$remoteExitCode = $LASTEXITCODE
$transportOutputPath = Join-Path $ExternalEvidenceDirectory (
    '{0}-attempt-{1}-transport-output.txt' -f
        $Mode.ToLowerInvariant(), $attemptId)
Write-CreateNewUtf8 -Path $transportOutputPath -Content (
    (($output | ForEach-Object { [string]$_ }) -join
        [Environment]::NewLine) + [Environment]::NewLine)
$jsonRows = @($output | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_) -and
        $_.TrimStart().StartsWith('{')
    })
if ($jsonRows.Count -ne 1) {
    throw 'remote worker did not emit exactly one JSON result'
}
$remoteJson = $jsonRows[0].Trim()
try {
    $result = $remoteJson | ConvertFrom-Json
}
catch {
    throw 'remote worker result is not valid JSON'
}

$localReceiptPath = Join-Path $ExternalEvidenceDirectory (
    '{0}-attempt-{1}-worker-result.json' -f
        $Mode.ToLowerInvariant(), $attemptId)
Write-CreateNewUtf8 -Path $localReceiptPath -Content (
    $remoteJson + [Environment]::NewLine)

$expectedStates = @{
    Plan = @('READY_TO_ACTIVATE')
    Apply = @('OBSERVATION_ACTIVE')
    Verify = @('OBSERVATION_ACTIVE_VERIFIED')
    Recover = @(
        'DEFAULT_OFF_RECOVERED',
        'OBSERVATION_ACTIVE_RECOVERED'
    )
}
if ($remoteExitCode -ne 0 -or
    $expectedStates[$Mode] -notcontains $result.state -or
    $result.officialExpertsPackageChanged -ne $false -or
    $result.secretsDisclosed -ne $false) {
    throw (
        "remote $Mode failed closed; receipt: $localReceiptPath")
}

[ordered]@{
    schema = 'fbsir.u3wObservationActivationRunnerResult.v1'
    mode = $Mode
    state = $result.state
    activationId = $ActivationId
    sourceCommit = $ExpectedCommit
    headEvidence = $headEvidence
    runnerSha256 = $runnerSha
    workerSha256 = $workerSha
    remoteResult = $result
    localReceiptPath = $localReceiptPath
    localReceiptSha256 = Get-Sha256 $localReceiptPath
    attemptIntentPath = $attemptIntentPath
    transportOutputPath = $transportOutputPath
    officialExpertsPackageSha256 = $OfficialExpertsPackageSha256
    officialExpertsPackagePath = $OfficialExpertsPackagePath
    officialExpertsPackageChanged = $false
    secretsDisclosed = $false
} | ConvertTo-Json -Depth 100 -Compress
