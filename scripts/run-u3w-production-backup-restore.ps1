[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Plan', 'ProvisionKey', 'Backup', 'Verify', 'All')]
    [string]$Mode,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string]$ExpectedCommit,
    [ValidatePattern('^w1a-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$')]
    [string]$RunId,
    [string]$ApprovalReceiptPath,
    [string]$AnchorOutputDirectory,
    [string]$SshKeyPath = $(if ($env:U3W_SSH_KEY_PATH) { $env:U3W_SSH_KEY_PATH } else { Join-Path $env:USERPROFILE '.ssh\id_ed25519_api2' }),
    [string]$KnownHostsPath = $(Join-Path $env:TEMP 'u3w-production-backup-known-hosts')
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$SshTarget = 'root@api2.u3w.com'
$ExpectedPublicKeyFingerprint = 'SHA256:oCIzbO9W94qDBeS9MtetLCW0CjYMkyZqT6QkgcLkXk0'
$ExpectedRemoteHostKeyFingerprint = 'SHA256:GkS/HJpLm48N+KaMV/WEVSBHnI8PKJsU+6ycAsn+mBA'
$BackupWorkerPath = Join-Path $PSScriptRoot 'u3w-production-backup-remote.py'
$VerifierPath = Join-Path $PSScriptRoot 'u3w-isolated-restore-verifier-remote.py'
if (-not $AnchorOutputDirectory) {
    $handoffRoot = Split-Path (Split-Path $RepoRoot -Parent) -Parent
    $AnchorOutputDirectory = Join-Path $handoffRoot 'deliverables\production-evidence'
}

function New-RunId {
    $bytes = [byte[]]::new(6)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    $suffix = -join ($bytes | ForEach-Object { $_.ToString('x2') })
    return 'w1a-{0}-{1}' -f ([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')), $suffix
}

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "required file missing: $Path"
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-StrictHead {
    Push-Location $RepoRoot
    try {
        $head = (& git rev-parse HEAD).Trim().ToLowerInvariant()
        if ($LASTEXITCODE -ne 0 -or $head -ne $ExpectedCommit) {
            throw "strict HEAD mismatch: expected $ExpectedCommit, actual $head"
        }
        $dirty = @(& git status --porcelain=v1)
        if ($LASTEXITCODE -ne 0 -or $dirty.Count -ne 0) {
            throw 'strict HEAD requires a clean worktree, including no untracked files'
        }
        $branch = (& git branch --show-current).Trim()
        if ($LASTEXITCODE -ne 0 -or -not $branch) {
            throw 'strict HEAD requires a named branch'
        }
        $remoteLine = @(& git ls-remote --exit-code origin "refs/heads/$branch")
        if ($LASTEXITCODE -ne 0 -or $remoteLine.Count -ne 1) {
            throw "remote branch is absent or ambiguous: $branch"
        }
        $remoteHead = ($remoteLine[0] -split '\s+', 2)[0].ToLowerInvariant()
        if ($remoteHead -ne $ExpectedCommit) {
            throw "GitHub branch SHA mismatch: $remoteHead"
        }
        $upstream = (& git rev-parse '@{upstream}').Trim().ToLowerInvariant()
        if ($LASTEXITCODE -ne 0 -or $upstream -ne $ExpectedCommit) {
            throw 'local upstream is not aligned to strict HEAD'
        }
    }
    finally {
        Pop-Location
    }
}

function Assert-PrivateKey {
    if (-not (Test-Path -LiteralPath $SshKeyPath -PathType Leaf)) {
        throw "SSH private key missing: $SshKeyPath"
    }
    $publicKeyPath = "$SshKeyPath.pub"
    if (-not (Test-Path -LiteralPath $publicKeyPath -PathType Leaf)) {
        throw "SSH public key missing: $publicKeyPath"
    }
    $fingerprint = & ssh-keygen.exe -lf $publicKeyPath -E sha256 2>$null
    if ($LASTEXITCODE -ne 0 -or $fingerprint -notlike "*$ExpectedPublicKeyFingerprint*") {
        throw "SSH public key fingerprint mismatch; expected $ExpectedPublicKeyFingerprint"
    }
}

function Test-KnownHostFingerprint {
    param([Parameter(Mandatory = $true)][string]$Path)
    $hostName = ($SshTarget -split '@', 2)[1]
    $matchingLines = @(& ssh-keygen.exe -F $hostName -f $Path 2>$null |
        Where-Object { $_ -and -not $_.StartsWith('#') })
    if ($LASTEXITCODE -ne 0 -or $matchingLines.Count -eq 0) {
        return $false
    }
    $fingerprints = @($matchingLines | & ssh-keygen.exe -lf - -E sha256 2>$null)
    return $LASTEXITCODE -eq 0 -and
        $fingerprints.Count -gt 0 -and
        @($fingerprints | Where-Object {
                $_ -notmatch [regex]::Escape($ExpectedRemoteHostKeyFingerprint)
            }).Count -eq 0
}

function Ensure-KnownHosts {
    $directory = Split-Path -Parent $KnownHostsPath
    if ($directory -and -not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }
    if (Test-Path -LiteralPath $KnownHostsPath -PathType Leaf) {
        if (-not (Test-KnownHostFingerprint $KnownHostsPath)) {
            throw "remote host key fingerprint mismatch; expected $ExpectedRemoteHostKeyFingerprint"
        }
        return
    }
    $scanPath = "$KnownHostsPath.scan-$PID"
    try {
        $priorErrorPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $scan = @(
                & ssh-keyscan.exe -T 10 -t ed25519 (
                    ($SshTarget -split '@', 2)[1]
                ) 2>$null
            )
        }
        finally {
            $ErrorActionPreference = $priorErrorPreference
        }
        if ($LASTEXITCODE -ne 0 -or $scan.Count -eq 0) {
            throw 'ssh-keyscan failed'
        }
        $scan | Set-Content -LiteralPath $scanPath -Encoding ascii
        if (-not (Test-KnownHostFingerprint $scanPath)) {
            throw "scanned host key mismatch; expected $ExpectedRemoteHostKeyFingerprint"
        }
        Move-Item -LiteralPath $scanPath -Destination $KnownHostsPath
    }
    finally {
        Remove-Item -LiteralPath $scanPath -Force -ErrorAction SilentlyContinue
    }
}

function Get-ApprovalDigest {
    if ($Mode -eq 'Plan') {
        $script:ApprovalBase64 = 'e30='
        return '0' * 64
    }
    if (-not $ApprovalReceiptPath -or
        -not (Test-Path -LiteralPath $ApprovalReceiptPath -PathType Leaf)) {
        throw 'mutating modes require ApprovalReceiptPath'
    }
    $approval = Get-Content -Raw -LiteralPath $ApprovalReceiptPath | ConvertFrom-Json
    $expectedFields = @(
        'schema', 'action', 'targetHost', 'runId', 'sourceCommit',
        'approvedAt', 'expiresAt', 'authorizedBy',
        'concurrentDdlProhibited',
        'productionFilesystemWrite', 'productionDatabaseWrite',
        'productionServiceChange', 'officialExpertsPackageChange'
    ) | Sort-Object
    $actualFields = @($approval.PSObject.Properties.Name | Sort-Object)
    if (($actualFields -join "`n") -ne ($expectedFields -join "`n")) {
        throw 'approval receipt has missing or unknown fields'
    }
    $expiresAt = [DateTimeOffset]::Parse($approval.expiresAt)
    $approvedAt = [DateTimeOffset]::Parse($approval.approvedAt)
    if (
        $approval.schema -ne 'fbsir.u3wProductionChangeApprovalReceipt.v1' -or
        $approval.action -ne 'DATABASE_BACKUP_AND_ISOLATED_RESTORE' -or
        $approval.targetHost -ne 'api2.u3w.com' -or
        $approval.runId -ne $RunId -or
        $approval.sourceCommit -ne $ExpectedCommit -or
        $approval.authorizedBy -ne 'workspace-user' -or
        $approval.concurrentDdlProhibited -ne $true -or
        $approvedAt -gt [DateTimeOffset]::UtcNow -or
        $expiresAt -le [DateTimeOffset]::UtcNow -or
        ($expiresAt - $approvedAt).TotalHours -gt 24 -or
        $approval.productionFilesystemWrite -ne $true -or
        $approval.productionDatabaseWrite -ne $false -or
        $approval.productionServiceChange -ne $false -or
        $approval.officialExpertsPackageChange -ne $false
    ) {
        throw 'approval receipt scope, identity or validity window is invalid'
    }
    $script:ApprovalBase64 = [Convert]::ToBase64String(
        [IO.File]::ReadAllBytes(
            (Resolve-Path -LiteralPath $ApprovalReceiptPath).Path
        )
    )
    return Get-Sha256 $ApprovalReceiptPath
}

function Save-OutOfBandBundleAnchor {
    if (-not (Test-Path -LiteralPath $AnchorOutputDirectory)) {
        New-Item -ItemType Directory -Path $AnchorOutputDirectory -Force | Out-Null
    }
    $resolvedOutput = (Resolve-Path -LiteralPath $AnchorOutputDirectory).Path
    $bundlePath = Join-Path $resolvedOutput "$RunId-receipt.json"
    $temporaryPath = Join-Path $resolvedOutput (
        ".$RunId-receipt-$([Guid]::NewGuid().ToString('N')).partial"
    )
    try {
        $scpArguments = @(
            '-i', $SshKeyPath,
            '-o', 'BatchMode=yes',
            '-o', 'ConnectTimeout=10',
            '-o', 'StrictHostKeyChecking=yes',
            '-o', "UserKnownHostsFile=$KnownHostsPath",
            "${SshTarget}:/opt/fbsir/admin/backups/w1a/$RunId/receipt.json",
            $temporaryPath
        )
        & scp.exe @scpArguments
        if ($LASTEXITCODE -ne 0 -or
            -not (Test-Path -LiteralPath $temporaryPath -PathType Leaf)) {
            throw 'failed to download the immutable bundle receipt'
        }
        $bundle = Get-Content -Raw -LiteralPath $temporaryPath | ConvertFrom-Json
        if ($bundle.schema -ne 'fbsir.u3wDatabaseBackupRestoreBundleReceipt.v1' -or
            $bundle.runId -ne $RunId -or
            $bundle.sourceCommit -ne $ExpectedCommit) {
            throw 'downloaded bundle identity is invalid'
        }
        $downloadedDigest = Get-Sha256 $temporaryPath
        if (Test-Path -LiteralPath $bundlePath -PathType Leaf) {
            $existingDigest = Get-Sha256 $bundlePath
            if ($existingDigest -ne $downloadedDigest) {
                throw 'CORRUPT_STATE: immutable local bundle receipt digest changed'
            }
            Remove-Item -LiteralPath $temporaryPath -Force
        }
        else {
            Move-Item -LiteralPath $temporaryPath -Destination $bundlePath
        }
        $digest = Get-Sha256 $bundlePath
        $anchorPath = Join-Path $resolvedOutput "$RunId-anchor.json"
        if (Test-Path -LiteralPath $anchorPath -PathType Leaf) {
            $existingAnchor = Get-Content -Raw -LiteralPath $anchorPath |
                ConvertFrom-Json
            if (
                $existingAnchor.schema -ne
                    'fbsir.u3wDatabaseBackupRestoreExternalAnchor.v1' -or
                $existingAnchor.runId -ne $RunId -or
                $existingAnchor.sourceCommit -ne $ExpectedCommit -or
                $existingAnchor.targetHost -ne 'api2.u3w.com' -or
                $existingAnchor.bundleReceiptPath -ne $bundlePath -or
                $existingAnchor.bundleReceiptSha256 -ne $digest
            ) {
                throw 'CORRUPT_STATE: immutable external anchor changed'
            }
        }
        else {
            $anchorJson = [ordered]@{
            schema = 'fbsir.u3wDatabaseBackupRestoreExternalAnchor.v1'
            runId = $RunId
            sourceCommit = $ExpectedCommit
            targetHost = 'api2.u3w.com'
            bundleReceiptPath = $bundlePath
            bundleReceiptSha256 = $digest
            capturedAt = [DateTime]::UtcNow.ToString('o')
            } | ConvertTo-Json
            $encoding = [Text.UTF8Encoding]::new($false)
            $stream = [IO.File]::Open(
                $anchorPath,
                [IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write,
                [IO.FileShare]::None
            )
            try {
                $bytes = $encoding.GetBytes($anchorJson + "`n")
                $stream.Write($bytes, 0, $bytes.Length)
                $stream.Flush($true)
            }
            finally {
                $stream.Dispose()
            }
        }
        return [ordered]@{
            bundleReceiptPath = $bundlePath
            bundleReceiptSha256 = $digest
            anchorPath = $anchorPath
        }
    }
    finally {
        Remove-Item -LiteralPath $temporaryPath -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-RemotePython {
    param(
        [Parameter(Mandatory = $true)][string]$ScriptPath,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    $payload = Get-Content -Raw -LiteralPath $ScriptPath
    $sshArguments = @(
        '-i', $SshKeyPath,
        '-o', 'BatchMode=yes',
        '-o', 'ConnectTimeout=10',
        '-o', 'ConnectionAttempts=1',
        '-o', 'StrictHostKeyChecking=yes',
        '-o', "UserKnownHostsFile=$KnownHostsPath",
        $SshTarget,
        'python3', '-'
    ) + $Arguments
    $output = @($payload | & ssh.exe @sshArguments)
    if ($LASTEXITCODE -ne 0) {
        throw "remote worker failed: $ScriptPath"
    }
    $json = ($output -join "`n")
    if (-not $json.Trim()) {
        throw "remote worker returned no receipt: $ScriptPath"
    }
    return $json | ConvertFrom-Json
}

if (-not $RunId) {
    $RunId = New-RunId
}
Assert-StrictHead
Assert-PrivateKey
Ensure-KnownHosts
$approvalSha = Get-ApprovalDigest
$runnerSha = Get-Sha256 $PSCommandPath
$workerSha = Get-Sha256 $BackupWorkerPath
$verifierSha = Get-Sha256 $VerifierPath
$common = @(
    '--run-id', $RunId,
    '--source-commit', $ExpectedCommit,
    '--approval-sha', $approvalSha,
    '--approval-json-base64', $script:ApprovalBase64,
    '--runner-sha', $runnerSha,
    '--worker-sha', $workerSha
)

$backup = $null
$restore = $null
if ($Mode -eq 'Plan') {
    $backup = Invoke-RemotePython -ScriptPath $BackupWorkerPath -Arguments (
        @('--mode', 'Plan') + $common
    )
}
elseif ($Mode -eq 'ProvisionKey') {
    $backup = Invoke-RemotePython -ScriptPath $BackupWorkerPath -Arguments (
        @('--mode', 'ProvisionKey') + $common
    )
}
elseif ($Mode -eq 'Backup') {
    $backup = Invoke-RemotePython -ScriptPath $BackupWorkerPath -Arguments (
        @('--mode', 'Backup') + $common
    )
}
elseif ($Mode -eq 'Verify') {
    $restore = Invoke-RemotePython -ScriptPath $VerifierPath -Arguments (
        $common + @('--verifier-sha', $verifierSha)
    )
}
elseif ($Mode -eq 'All') {
    $backup = Invoke-RemotePython -ScriptPath $BackupWorkerPath -Arguments (
        @('--mode', 'Backup') + $common
    )
    $restore = Invoke-RemotePython -ScriptPath $VerifierPath -Arguments (
        $common + @('--verifier-sha', $verifierSha)
    )
}

$externalAnchor = if ($Mode -in @('Verify', 'All')) {
    Save-OutOfBandBundleAnchor
} else {
    $null
}

[ordered]@{
    schema = 'fbsir.u3wProductionBackupRestoreRunnerResult.v1'
    mode = $Mode
    runId = $RunId
    sourceCommit = $ExpectedCommit
    approvalReceiptSha256 = $approvalSha
    runnerSha256 = $runnerSha
    backupWorkerSha256 = $workerSha
    verifierSha256 = $verifierSha
    backup = $backup
    restore = $restore
    externalAnchor = $externalAnchor
} | ConvertTo-Json -Depth 20
