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
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedAdminRootDependencyAdoptionReceiptSha256,
    [string]$PlanReceiptPath,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedPlanReceiptSha256,
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
    $derived = @(& ssh-keygen.exe -y -f $SshKeyPath 2>$null)
    if ($LASTEXITCODE -ne 0 -or $derived.Count -ne 1) {
        throw 'cannot derive public key from SSH private key'
    }
    $fingerprint = $derived |
        & ssh-keygen.exe -lf - -E sha256 2>$null
    if ($LASTEXITCODE -ne 0 -or
        $fingerprint -notlike "*$ExpectedPublicKeyFingerprint*") {
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

function Get-PlanReceiptDigest {
    if ($Mode -eq 'Plan') {
        $script:PlanReceiptBase64 = 'e30='
        return '0' * 64
    }
    if (
        -not $PlanReceiptPath -or
        -not $ExpectedPlanReceiptSha256 -or
        -not (Test-Path -LiteralPath $PlanReceiptPath -PathType Leaf)
    ) {
        throw 'mutating and verification modes require PlanReceiptPath and ExpectedPlanReceiptSha256'
    }
    $resolved = (Resolve-Path -LiteralPath $PlanReceiptPath).Path
    $bytes = [IO.File]::ReadAllBytes($resolved)
    $digest = Get-BytesSha256 $bytes
    if ($digest -cne $ExpectedPlanReceiptSha256) {
        throw 'backup Plan receipt digest drifted'
    }
    $plan = [Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json
    $generatedAt = [DateTimeOffset]::Parse($plan.generatedAt)
    $expiresAt = [DateTimeOffset]::Parse($plan.expiresAt)
    if (
        $plan.schema -cne 'fbsir.u3wDatabaseBackupPlan.v3' -or
        $plan.runId -cne $RunId -or
        $plan.sourceCommit -cne $ExpectedCommit -or
        $plan.targetHost -cne 'api2.u3w.com' -or
        $plan.database -cne 'fbsir' -or
        $plan.adminRootDependencyAdoptionReceiptSha256 -cne
            $ExpectedAdminRootDependencyAdoptionReceiptSha256 -or
        $plan.runnerSha256 -notmatch '^[0-9a-f]{64}$' -or
        $plan.backupWorkerSha256 -notmatch '^[0-9a-f]{64}$' -or
        $plan.verifierSha256 -notmatch '^[0-9a-f]{64}$' -or
        $generatedAt -gt [DateTimeOffset]::UtcNow -or
        $expiresAt -le [DateTimeOffset]::UtcNow -or
        ($expiresAt - $generatedAt).TotalHours -gt 24 -or
        $plan.businessDatabaseWouldChange -ne $false -or
        $plan.serviceWouldChange -ne $false -or
        $plan.officialExpertsPackageWouldChange -ne $false
    ) {
        throw 'backup Plan receipt identity, scope or validity is invalid'
    }
    $script:PlanReceiptBase64 = [Convert]::ToBase64String($bytes)
    return $digest
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
        'productionServiceChange', 'officialExpertsPackageChange',
        'expectedAdminRootDependencyAdoptionReceiptSha256',
        'expectedBackupPlanReceiptSha256'
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
        $approval.officialExpertsPackageChange -ne $false -or
        $approval.expectedAdminRootDependencyAdoptionReceiptSha256 -ne
            $ExpectedAdminRootDependencyAdoptionReceiptSha256 -or
        $approval.expectedBackupPlanReceiptSha256 -ne
            $ExpectedPlanReceiptSha256
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
    $planPath = Join-Path $resolvedOutput "$RunId-plan.json"
    if (
        -not (Test-Path -LiteralPath $planPath -PathType Leaf) -or
        (Get-Sha256 $planPath) -cne $ExpectedPlanReceiptSha256
    ) {
        throw 'immutable out-of-band backup Plan evidence is missing or drifted'
    }
    $temporaryPath = Join-Path $resolvedOutput (
        ".$RunId-receipt-$([Guid]::NewGuid().ToString('N')).partial"
    )
    try {
        $scpArguments = @(
            '-i', $SshKeyPath,
            '-o', 'BatchMode=yes',
            '-o', 'IdentitiesOnly=yes',
            '-o', 'IdentityAgent=none',
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
        $bundleFields = @(
            $bundle.psobject.Properties.Name | Sort-Object
        )
        $expectedBundleFields = @(
            'schema', 'runId', 'sourceCommit', 'planReceiptSha256',
            'targetHost', 'database', 'sourceDatabaseServerUuid',
            'generatedAt', 'backupReceiptPath', 'backupReceiptSha256',
            'restoreReceiptPath', 'restoreReceiptSha256', 'backupPath',
            'backupSha256', 'backupSizeBytes', 'approvalReceiptSha256',
            'runnerSha256', 'backupWorkerSha256', 'verifierSha256',
            'adminRootDependencyAdoptionReceiptSha256',
            'productionBusinessStateChanged'
        ) | Sort-Object
        if (($bundleFields -join "`n") -cne
                ($expectedBundleFields -join "`n") -or
            $bundle.schema -ne 'fbsir.u3wDatabaseBackupRestoreBundleReceipt.v3' -or
            $bundle.runId -ne $RunId -or
            $bundle.sourceCommit -ne $ExpectedCommit -or
            $bundle.planReceiptSha256 -ne
                $ExpectedPlanReceiptSha256 -or
            $bundle.adminRootDependencyAdoptionReceiptSha256 -ne
                $ExpectedAdminRootDependencyAdoptionReceiptSha256 -or
            $bundle.sourceDatabaseServerUuid -notmatch
                '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') {
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
            $anchorFields = @(
                $existingAnchor.psobject.Properties.Name | Sort-Object
            )
            $expectedAnchorFields = @(
                'schema', 'runId', 'sourceCommit', 'planReceiptSha256',
                'planReceiptPath',
                'targetHost', 'bundleReceiptPath', 'bundleReceiptSha256',
                'adminRootDependencyAdoptionReceiptSha256',
                'sourceDatabaseServerUuid', 'capturedAt'
            ) | Sort-Object
            try {
                $capturedAt = [DateTimeOffset]::Parse(
                    [string]$existingAnchor.capturedAt)
            }
            catch {
                throw 'CORRUPT_STATE: external anchor timestamp is invalid'
            }
            if (
                ($anchorFields -join "`n") -cne
                    ($expectedAnchorFields -join "`n") -or
                $existingAnchor.schema -ne
                    'fbsir.u3wDatabaseBackupRestoreExternalAnchor.v3' -or
                $existingAnchor.runId -ne $RunId -or
                $existingAnchor.sourceCommit -ne $ExpectedCommit -or
                $existingAnchor.planReceiptSha256 -ne
                    $ExpectedPlanReceiptSha256 -or
                $existingAnchor.planReceiptPath -ne $planPath -or
                $existingAnchor.targetHost -ne 'api2.u3w.com' -or
                $existingAnchor.bundleReceiptPath -ne $bundlePath -or
                $existingAnchor.bundleReceiptSha256 -ne $digest -or
                $existingAnchor.adminRootDependencyAdoptionReceiptSha256 -ne
                    $ExpectedAdminRootDependencyAdoptionReceiptSha256 -or
                $existingAnchor.sourceDatabaseServerUuid -ne
                    $bundle.sourceDatabaseServerUuid -or
                $capturedAt.Offset -ne [TimeSpan]::Zero -or
                $capturedAt -gt [DateTimeOffset]::UtcNow
            ) {
                throw 'CORRUPT_STATE: immutable external anchor changed'
            }
        }
        else {
            $anchorJson = [ordered]@{
            schema = 'fbsir.u3wDatabaseBackupRestoreExternalAnchor.v3'
            runId = $RunId
            sourceCommit = $ExpectedCommit
            planReceiptSha256 = $ExpectedPlanReceiptSha256
            planReceiptPath = $planPath
            targetHost = 'api2.u3w.com'
            bundleReceiptPath = $bundlePath
            bundleReceiptSha256 = $digest
            adminRootDependencyAdoptionReceiptSha256 =
                $ExpectedAdminRootDependencyAdoptionReceiptSha256
            sourceDatabaseServerUuid = $bundle.sourceDatabaseServerUuid
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
        [Parameter(Mandatory = $true)][string]$WorkerName,
        [Parameter(Mandatory = $true)][byte[]]$WorkerBytes,
        [Parameter(Mandatory = $true)][string]$WorkerSha256,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    if ((Get-BytesSha256 $WorkerBytes) -ne $WorkerSha256) {
        throw "in-memory worker digest mismatch: $WorkerName"
    }
    $payload = [Convert]::ToBase64String($WorkerBytes)
    $bootstrap = 'import base64,hashlib,sys;' +
        'b=base64.b64decode(sys.stdin.buffer.read());' +
        'e=sys.argv.pop(1);a=hashlib.sha256(b).hexdigest();' +
        'a==e or sys.exit("worker payload SHA-256 mismatch");' +
        'exec(compile(b,"<u3w-worker>","exec"),' +
        '{"__name__":"__main__","__file__":"<u3w-worker>"})'
    $escapedBootstrap = $bootstrap.Replace('"', '\"')
    $remoteCommand = "python3 -c '$escapedBootstrap' $WorkerSha256 " +
        ($Arguments -join ' ')
    $sshArguments = @(
        '-i', $SshKeyPath,
        '-o', 'BatchMode=yes',
        '-o', 'IdentitiesOnly=yes',
        '-o', 'IdentityAgent=none',
        '-o', 'ConnectTimeout=10',
        '-o', 'ConnectionAttempts=1',
        '-o', 'StrictHostKeyChecking=yes',
        '-o', "UserKnownHostsFile=$KnownHostsPath",
        $SshTarget,
        $remoteCommand
    )
    $output = @($payload | & ssh.exe @sshArguments)
    if ($LASTEXITCODE -ne 0) {
        throw "remote worker failed: $WorkerName"
    }
    $json = ($output -join "`n")
    if (-not $json.Trim()) {
        throw "remote worker returned no receipt: $WorkerName"
    }
    $script:LastRemoteJson = $json.Trim()
    return $json | ConvertFrom-Json
}

function Save-OutOfBandPlanReceipt {
    if (-not $script:LastRemoteJson) {
        throw 'remote backup Plan bytes are unavailable'
    }
    if (-not (Test-Path -LiteralPath $AnchorOutputDirectory)) {
        New-Item -ItemType Directory -Path $AnchorOutputDirectory -Force |
            Out-Null
    }
    $resolvedOutput = (Resolve-Path -LiteralPath $AnchorOutputDirectory).Path
    $path = Join-Path $resolvedOutput "$RunId-plan.json"
    $encoding = [Text.UTF8Encoding]::new($false)
    $bytes = $encoding.GetBytes($script:LastRemoteJson + "`n")
    $digest = Get-BytesSha256 $bytes
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        if ((Get-Sha256 $path) -cne $digest) {
            throw 'CORRUPT_STATE: immutable backup Plan receipt changed'
        }
    }
    else {
        $stream = [IO.File]::Open(
            $path,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        try {
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
        }
        finally {
            $stream.Dispose()
        }
    }
    return [ordered]@{
        path = $path
        sha256 = $digest
    }
}

if (-not $RunId) {
    $RunId = New-RunId
}
Assert-StrictHead
Assert-PrivateKey
Ensure-KnownHosts
$planReceiptSha = Get-PlanReceiptDigest
$approvalSha = Get-ApprovalDigest
$runnerBytes = Get-CommittedBlobBytes (
    'scripts/run-u3w-production-backup-restore.ps1')
$runnerSha = Get-BytesSha256 $runnerBytes
$workerBytes = Get-CommittedBlobBytes (
    'scripts/u3w-production-backup-remote.py')
$workerSha = Get-BytesSha256 $workerBytes
$verifierBytes = Get-CommittedBlobBytes (
    'scripts/u3w-isolated-restore-verifier-remote.py')
$verifierSha = Get-BytesSha256 $verifierBytes
$common = @(
    '--run-id', $RunId,
    '--source-commit', $ExpectedCommit,
    '--approval-sha', $approvalSha,
    '--approval-json-base64', $script:ApprovalBase64,
    '--plan-receipt-sha', $planReceiptSha,
    '--plan-json-base64', $script:PlanReceiptBase64,
    '--runner-sha', $runnerSha,
    '--worker-sha', $workerSha,
    '--verifier-sha', $verifierSha,
    '--admin-root-dependency-adoption-receipt-sha',
        $ExpectedAdminRootDependencyAdoptionReceiptSha256
)

$backup = $null
$restore = $null
$planEvidence = $null
if ($Mode -eq 'Plan') {
    $backup = Invoke-RemotePython -WorkerName 'production-backup' `
        -WorkerBytes $workerBytes -WorkerSha256 $workerSha `
        -Arguments (@('--mode', 'Plan') + $common)
    $planEvidence = Save-OutOfBandPlanReceipt
}
elseif ($Mode -eq 'ProvisionKey') {
    $backup = Invoke-RemotePython -WorkerName 'production-backup' `
        -WorkerBytes $workerBytes -WorkerSha256 $workerSha `
        -Arguments (@('--mode', 'ProvisionKey') + $common)
}
elseif ($Mode -eq 'Backup') {
    $backup = Invoke-RemotePython -WorkerName 'production-backup' `
        -WorkerBytes $workerBytes -WorkerSha256 $workerSha `
        -Arguments (@('--mode', 'Backup') + $common)
}
elseif ($Mode -eq 'Verify') {
    $restore = Invoke-RemotePython -WorkerName 'isolated-restore-verifier' `
        -WorkerBytes $verifierBytes -WorkerSha256 $verifierSha `
        -Arguments $common
}
elseif ($Mode -eq 'All') {
    $backup = Invoke-RemotePython -WorkerName 'production-backup' `
        -WorkerBytes $workerBytes -WorkerSha256 $workerSha `
        -Arguments (@('--mode', 'Backup') + $common)
    $restore = Invoke-RemotePython -WorkerName 'isolated-restore-verifier' `
        -WorkerBytes $verifierBytes -WorkerSha256 $verifierSha `
        -Arguments $common
}
Assert-StrictHead

$externalAnchor = if ($Mode -in @('Verify', 'All')) {
    Save-OutOfBandBundleAnchor
} else {
    $null
}

[ordered]@{
    schema = 'fbsir.u3wProductionBackupRestoreRunnerResult.v2'
    mode = $Mode
    runId = $RunId
    sourceCommit = $ExpectedCommit
    approvalReceiptSha256 = $approvalSha
    planReceiptSha256 = if ($Mode -eq 'Plan') {
        $planEvidence.sha256
    } else {
        $planReceiptSha
    }
    planEvidence = $planEvidence
    runnerSha256 = $runnerSha
    backupWorkerSha256 = $workerSha
    verifierSha256 = $verifierSha
    adminRootDependencyAdoptionReceiptSha256 =
        $ExpectedAdminRootDependencyAdoptionReceiptSha256
    backup = $backup
    restore = $restore
    externalAnchor = $externalAnchor
} | ConvertTo-Json -Depth 20
