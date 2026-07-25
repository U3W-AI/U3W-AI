[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
        'Plan',
        'PlanTokenSecretRotation',
        'Apply',
        'Verify',
        'Recover',
        'Reconcile',
        'RotateTokenSecret')]
    [string]$Mode,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string]$ExpectedCommit,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedEnvironmentSha256 = $('0' * 64),
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedConfiguredEnvironmentSha256 = $('0' * 64),
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$OriginalApprovalReceiptSha256 = $('0' * 64),
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedPredecessorConfigurationReceiptSha256 = $('0' * 64),
    [ValidatePattern('^w1a-config-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$')]
    [string]$RunId,
    [string]$ApprovalReceiptPath,
    [string]$AnchorOutputDirectory,
    [string]$SshKeyPath = $(if ($env:U3W_SSH_KEY_PATH) { $env:U3W_SSH_KEY_PATH } else { Join-Path $env:USERPROFILE '.ssh\id_ed25519_api2' }),
    [string]$KnownHostsPath = $(Join-Path $env:TEMP 'u3w-default-off-config-known-hosts')
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$WorkerPath =
    Join-Path $PSScriptRoot 'u3w-default-off-configuration-remote.py'
$SshTarget = 'root@api2.u3w.com'
$ExpectedPublicKeyFingerprint =
    'SHA256:oCIzbO9W94qDBeS9MtetLCW0CjYMkyZqT6QkgcLkXk0'
$ExpectedRemoteHostKeyFingerprint =
    'SHA256:GkS/HJpLm48N+KaMV/WEVSBHnI8PKJsU+6ycAsn+mBA'
if (-not $AnchorOutputDirectory) {
    $handoffRoot = Split-Path (Split-Path $RepoRoot -Parent) -Parent
    $AnchorOutputDirectory =
        Join-Path $handoffRoot 'deliverables\production-evidence'
}

function New-RunId {
    $bytes = [byte[]]::new(6)
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    $suffix = -join ($bytes | ForEach-Object { $_.ToString('x2') })
    return 'w1a-config-{0}-{1}' -f (
        [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')), $suffix
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
    $head = (& git -C $RepoRoot rev-parse HEAD).Trim().ToLowerInvariant()
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
    $remote = @(
        & git -C $RepoRoot ls-remote --exit-code origin "refs/heads/$branch"
    )
    if ($LASTEXITCODE -ne 0 -or $remote.Count -ne 1) {
        throw "GitHub branch is absent or ambiguous: $branch"
    }
    if (($remote[0] -split '\s+', 2)[0].ToLowerInvariant() -ne $ExpectedCommit) {
        throw 'GitHub branch SHA differs from ExpectedCommit'
    }
    $upstream = (& git -C $RepoRoot rev-parse '@{upstream}').
        Trim().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0 -or $upstream -ne $ExpectedCommit) {
        throw 'local upstream differs from ExpectedCommit'
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
        Remove-Item -LiteralPath $scanPath -Force -ErrorAction SilentlyContinue
    }
}

function Get-ApprovalDigest {
    if ($Mode -in @('Plan', 'PlanTokenSecretRotation', 'Verify')) {
        $script:ApprovalBase64 = 'e30='
        return '0' * 64
    }
    if (-not $ApprovalReceiptPath -or
        -not (Test-Path -LiteralPath $ApprovalReceiptPath -PathType Leaf)) {
        throw "$Mode requires ApprovalReceiptPath"
    }
    $approval = Get-Content -Raw -LiteralPath $ApprovalReceiptPath |
        ConvertFrom-Json
    $expectedFields = @(
        'schema', 'action', 'targetHost', 'runId', 'sourceCommit',
        'approvedAt', 'expiresAt', 'authorizedBy',
        'concurrentDdlProhibited', 'productionFilesystemWrite',
        'productionDatabaseWrite', 'productionServiceChange',
        'officialExpertsPackageChange', 'expectedEnvironmentSha256',
        'expectedApi2EventKeyState', 'runnerSha256', 'workerSha256'
    )
    if ($Mode -eq 'Recover') {
        $expectedFields += @(
            'expectedConfiguredEnvironmentSha256',
            'originalApprovalReceiptSha256')
    }
    if ($Mode -in @('Reconcile', 'RotateTokenSecret')) {
        $expectedFields +=
            'expectedPredecessorConfigurationReceiptSha256'
    }
    $expectedFields = $expectedFields | Sort-Object
    $actualFields = @($approval.PSObject.Properties.Name | Sort-Object)
    if (($expectedFields -join "`n") -ne ($actualFields -join "`n")) {
        throw 'approval receipt has missing or unknown fields'
    }
    $approved = [DateTimeOffset]::Parse($approval.approvedAt)
    $expires = [DateTimeOffset]::Parse($approval.expiresAt)
    $approvalExpired = $expires -le [DateTimeOffset]::UtcNow
    $expiredRecoveryEligible = $Mode -in @(
        'Reconcile',
        'RotateTokenSecret')
    if (
        $approval.schema -ne
            'fbsir.u3wProductionChangeApprovalReceipt.v1' -or
        $approval.action -ne
            $(if ($Mode -eq 'Recover') {
                    'RECOVER_W1A_DEFAULT_OFF_CONFIGURATION_ANCHOR'
                } elseif ($Mode -eq 'Reconcile') {
                    'ADOPT_EXISTING_ADMIN_ENGINE_CREDENTIAL_DELTA'
                } elseif ($Mode -eq 'RotateTokenSecret') {
                    'ROTATE_FBSIR_TOKEN_SECRET_FOR_W1A_DEFAULT_OFF'
                } else {
                    'CONFIGURE_W1A_DEFAULT_OFF_CRYPTO_CUSTODY'
                }) -or
        $approval.targetHost -ne 'api2.u3w.com' -or
        $approval.runId -ne $RunId -or
        $approval.sourceCommit -ne $ExpectedCommit -or
        $approval.expectedEnvironmentSha256 -ne
            $ExpectedEnvironmentSha256 -or
        $approval.expectedApi2EventKeyState -ne
            $(if ($Mode -in @('Reconcile', 'RotateTokenSecret')) {
                    'PRESENT_ANCHORED'
                } else {
                    'ABSENT'
                }) -or
        $approval.runnerSha256 -ne $script:RunnerSha256 -or
        $approval.workerSha256 -ne $script:WorkerSha256 -or
        $approval.authorizedBy -ne 'workspace-user' -or
        $approval.concurrentDdlProhibited -ne $true -or
        $approval.productionFilesystemWrite -ne $true -or
        $approval.productionDatabaseWrite -ne $false -or
        $approval.productionServiceChange -ne $false -or
        $approval.officialExpertsPackageChange -ne $false -or
        $approved -gt [DateTimeOffset]::UtcNow -or
        ($approvalExpired -and -not $expiredRecoveryEligible) -or
        ($expires - $approved).TotalHours -gt 24
    ) {
        throw 'approval receipt identity, scope or validity is invalid'
    }
    if ($Mode -eq 'Recover' -and (
            $ExpectedConfiguredEnvironmentSha256 -eq ('0' * 64) -or
            $OriginalApprovalReceiptSha256 -eq ('0' * 64) -or
            $approval.expectedConfiguredEnvironmentSha256 -ne
                $ExpectedConfiguredEnvironmentSha256 -or
            $approval.originalApprovalReceiptSha256 -ne
                $OriginalApprovalReceiptSha256)) {
        throw 'configuration recovery approval anchors are invalid'
    }
    if ($Mode -in @('Reconcile', 'RotateTokenSecret') -and (
            $ExpectedPredecessorConfigurationReceiptSha256 -eq
                ('0' * 64) -or
            $approval.expectedPredecessorConfigurationReceiptSha256 -ne
                $ExpectedPredecessorConfigurationReceiptSha256)) {
        throw 'configuration reconciliation approval anchor is invalid'
    }
    $script:ApprovalBase64 = [Convert]::ToBase64String(
        [IO.File]::ReadAllBytes(
            (Resolve-Path -LiteralPath $ApprovalReceiptPath).Path))
    return Get-Sha256 $ApprovalReceiptPath
}

function Invoke-RemoteWorker {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][byte[]]$WorkerBytes,
        [Parameter(Mandatory = $true)][string]$WorkerSha256
    )
    if ((Get-BytesSha256 $WorkerBytes) -ne $WorkerSha256) {
        throw 'in-memory worker digest mismatch'
    }
    $payload = [Convert]::ToBase64String($WorkerBytes)
    $bootstrap = 'import base64,hashlib,sys;' +
        'b=base64.b64decode(sys.stdin.buffer.read());' +
        'e=sys.argv.pop(1);a=hashlib.sha256(b).hexdigest();' +
        'a==e or sys.exit("worker sha256 mismatch");' +
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
    $remoteExitCode = $LASTEXITCODE
    $json = $output -join "`n"
    if (-not $json.Trim()) {
        if ($remoteExitCode -ne 0) {
            throw 'remote configuration worker failed without a canonical envelope'
        }
        throw 'remote configuration worker returned no receipt'
    }
    try {
        $parsed = $json | ConvertFrom-Json
    }
    catch {
        if ($remoteExitCode -ne 0) {
            throw 'remote configuration worker failure envelope is not JSON'
        }
        throw
    }
    if ($remoteExitCode -ne 0) {
        $expectedNames = @(
            'schema',
            'mode',
            'runId',
            'sourceCommit',
            'targetHost',
            'errorType',
            'errorMessageSha256',
            'productionFilesystemChanged',
            'productionConfigurationChanged',
            'productionBusinessStateChanged',
            'productionServiceChanged',
            'configurationLoaded',
            'serviceRestarted',
            'officialExpertsPackageChanged',
            'secretsDisclosed'
        ) | Sort-Object
        $actualNames = @(
            $parsed.PSObject.Properties.Name
        ) | Sort-Object
        $nameDrift = @(Compare-Object $expectedNames $actualNames)
        if (
            $nameDrift.Count -ne 0 -or
            $parsed.schema -ne
                'fbsir.u3wDefaultOffConfigurationWorkerError.v2' -or
            $parsed.mode -ne $Mode -or
            $parsed.runId -ne $RunId -or
            $parsed.sourceCommit -ne $ExpectedCommit -or
            $parsed.targetHost -ne 'api2.u3w.com' -or
            $parsed.errorType -notmatch '^[A-Za-z][A-Za-z0-9_]{0,127}$' -or
            $parsed.errorMessageSha256 -notmatch '^[0-9a-f]{64}$' -or
            $parsed.productionFilesystemChanged -isnot [bool] -or
            $parsed.productionConfigurationChanged -isnot [bool] -or
            (
                $parsed.productionConfigurationChanged -eq $true -and
                $parsed.productionFilesystemChanged -ne $true
            ) -or
            $parsed.productionBusinessStateChanged -ne $false -or
            $parsed.productionServiceChanged -ne $false -or
            $parsed.configurationLoaded -ne $false -or
            $parsed.serviceRestarted -ne $false -or
            $parsed.officialExpertsPackageChanged -ne $false -or
            $parsed.secretsDisclosed -ne $false
        ) {
            throw 'remote configuration worker failure envelope is invalid'
        }
        return $parsed
    }
    if ($parsed.schema -eq
        'fbsir.u3wDefaultOffConfigurationWorkerError.v2') {
        throw 'remote configuration worker returned an error with exit code zero'
    }
    return $parsed
}

function Save-ExternalAnchor {
    param([Parameter(Mandatory = $true)][object]$WorkerResult)
    if (-not (Test-Path -LiteralPath $AnchorOutputDirectory)) {
        New-Item -ItemType Directory -Path $AnchorOutputDirectory -Force |
            Out-Null
    }
    $resolvedOutput = (Resolve-Path $AnchorOutputDirectory).Path
    $receiptPath = Join-Path $resolvedOutput (
        "$RunId-default-off-configuration-receipt.json")
    $temporaryPath = "$receiptPath.$PID.partial"
    try {
        $remotePath =
            "/opt/fbsir/admin/configuration/w1a/$RunId/configuration-receipt.json"
        & scp.exe -i $SshKeyPath -o BatchMode=yes -o IdentitiesOnly=yes `
            -o IdentityAgent=none -o ConnectTimeout=10 `
            -o StrictHostKeyChecking=yes `
            -o "UserKnownHostsFile=$KnownHostsPath" `
            "${SshTarget}:$remotePath" $temporaryPath
        if ($LASTEXITCODE -ne 0 -or
            -not (Test-Path -LiteralPath $temporaryPath -PathType Leaf)) {
            throw 'failed to download configuration receipt'
        }
        $digest = Get-Sha256 $temporaryPath
        $receipt = Get-Content -Raw -LiteralPath $temporaryPath |
            ConvertFrom-Json
        if (
            $receipt.schema -notin @(
                'fbsir.u3wDefaultOffConfigurationReceipt.v2',
                'fbsir.u3wDefaultOffConfigurationReceipt.v3',
                'fbsir.u3wDefaultOffConfigurationReceipt.v4') -or
            $receipt.runId -ne $RunId -or
            $receipt.sourceCommit -ne $ExpectedCommit -or
            $digest -ne $WorkerResult.configurationReceiptSha256 -or
            $receipt.serviceRestarted -ne $false -or
            $receipt.productionFilesystemChanged -ne $true -or
            $receipt.configurationLoaded -ne $false -or
            $receipt.stagedKeyMaterialMatched -ne $true -or
            $receipt.secretsDisclosed -ne $false
        ) {
            throw 'downloaded configuration receipt identity is invalid'
        }
        if (Test-Path -LiteralPath $receiptPath -PathType Leaf) {
            if ((Get-Sha256 $receiptPath) -ne $digest) {
                throw 'immutable local configuration receipt changed'
            }
            Remove-Item -LiteralPath $temporaryPath -Force
        }
        else {
            Move-Item -LiteralPath $temporaryPath -Destination $receiptPath
        }
        $anchorPath = Join-Path $resolvedOutput (
            "$RunId-default-off-configuration-anchor.json")
        $anchor = [ordered]@{
            schema = 'fbsir.u3wDefaultOffConfigurationExternalAnchor.v1'
            runId = $RunId
            sourceCommit = $ExpectedCommit
            targetHost = 'api2.u3w.com'
            configurationReceiptPath = $receiptPath
            configurationReceiptSha256 = $digest
            capturedAt = [DateTime]::UtcNow.ToString('o')
        }
        if (Test-Path -LiteralPath $anchorPath -PathType Leaf) {
            $existing = Get-Content -Raw -LiteralPath $anchorPath |
                ConvertFrom-Json
            if (
                $existing.configurationReceiptSha256 -ne $digest -or
                $existing.sourceCommit -ne $ExpectedCommit
            ) {
                throw 'immutable configuration external anchor changed'
            }
        }
        else {
            $json = $anchor | ConvertTo-Json
            $encoding = [Text.UTF8Encoding]::new($false)
            $stream = [IO.File]::Open(
                $anchorPath, [IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write, [IO.FileShare]::None)
            try {
                $bytes = $encoding.GetBytes($json + "`n")
                $stream.Write($bytes, 0, $bytes.Length)
                $stream.Flush($true)
            }
            finally {
                $stream.Dispose()
            }
        }
        return [ordered]@{
            receiptPath = $receiptPath
            receiptSha256 = $digest
            anchorPath = $anchorPath
        }
    }
    finally {
        Remove-Item -LiteralPath $temporaryPath -Force `
            -ErrorAction SilentlyContinue
    }
}

function Save-ExternalWorkerFailureAnchor {
    param([Parameter(Mandatory = $true)][object]$WorkerError)
    if (-not (Test-Path -LiteralPath $AnchorOutputDirectory)) {
        New-Item -ItemType Directory -Path $AnchorOutputDirectory -Force |
            Out-Null
    }
    $resolvedOutput = (Resolve-Path $AnchorOutputDirectory).Path
    $json = $WorkerError | ConvertTo-Json -Depth 10 -Compress
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($json + "`n")
    $digest = Get-BytesSha256 $bytes
    $anchorPath = Join-Path $resolvedOutput (
        "$RunId-default-off-configuration-worker-error-$digest.json")
    if (Test-Path -LiteralPath $anchorPath) {
        $existing = [IO.File]::ReadAllBytes(
            (Resolve-Path -LiteralPath $anchorPath).Path)
        if ((Get-BytesSha256 $existing) -ne $digest) {
            throw 'external configuration worker failure anchor changed'
        }
    }
    else {
        $stream = [IO.File]::Open(
            $anchorPath,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None)
        try {
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
        }
        finally {
            $stream.Dispose()
        }
    }
    return [ordered]@{
        anchorPath = $anchorPath
        anchorSha256 = $digest
    }
}

if (-not $RunId) {
    $RunId = New-RunId
}
Assert-StrictHead
Assert-PrivateKey
Ensure-KnownHosts
$runnerBytes = Get-CommittedBlobBytes (
    'scripts/run-u3w-default-off-configuration.ps1')
$runnerSha = Get-BytesSha256 $runnerBytes
$workerBytes = Get-CommittedBlobBytes (
    'scripts/u3w-default-off-configuration-remote.py')
$workerSha = Get-BytesSha256 $workerBytes
$script:RunnerSha256 = $runnerSha
$script:WorkerSha256 = $workerSha
$approvalSha = Get-ApprovalDigest
$result = Invoke-RemoteWorker -Arguments @(
    '--mode', $Mode,
    '--run-id', $RunId,
    '--source-commit', $ExpectedCommit,
    '--approval-sha', $approvalSha,
    '--approval-json-base64', $script:ApprovalBase64,
    '--runner-sha', $runnerSha,
    '--worker-sha', $workerSha,
    '--expected-environment-sha', $ExpectedEnvironmentSha256,
    '--expected-configured-environment-sha',
    $ExpectedConfiguredEnvironmentSha256,
    '--original-approval-sha', $OriginalApprovalReceiptSha256,
    '--expected-predecessor-configuration-receipt-sha',
    $ExpectedPredecessorConfigurationReceiptSha256
) -WorkerBytes $workerBytes -WorkerSha256 $workerSha
Assert-StrictHead
if ($result.schema -eq
    'fbsir.u3wDefaultOffConfigurationWorkerError.v2') {
    $failureAnchor = Save-ExternalWorkerFailureAnchor -WorkerError $result
    throw (
        "$Mode remote configuration worker failed; external evidence: " +
        "$($failureAnchor.anchorPath) sha256 " +
        "$($failureAnchor.anchorSha256)")
}
if ($Mode -in @('Plan', 'PlanTokenSecretRotation')) {
    $isTokenRotationPlan = $Mode -eq 'PlanTokenSecretRotation'
    $isReconciliationPlan = (
        -not $isTokenRotationPlan -and
        $ExpectedPredecessorConfigurationReceiptSha256 -ne ('0' * 64)
    )
    if (
        $result.schema -ne $(if ($isTokenRotationPlan) {
                'fbsir.u3wDefaultOffConfigurationPlan.v3'
            } elseif ($isReconciliationPlan) {
                'fbsir.u3wDefaultOffConfigurationPlan.v2'
            } else {
                'fbsir.u3wDefaultOffConfigurationPlan.v1'
            }) -or
        $result.mode -ne $Mode -or
        $result.runId -ne $RunId -or
        $result.sourceCommit -ne $ExpectedCommit -or
        $result.targetHost -ne 'api2.u3w.com' -or
        $result.environmentSha256 -notmatch '^[0-9a-f]{64}$' -or
        $result.productionFilesystemChanged -ne $false -or
        $result.productionConfigurationChanged -ne $false -or
        $result.productionBusinessStateChanged -ne $false -or
        $result.secretsDisclosed -ne $false
    ) {
        throw 'remote configuration Plan identity or safety is invalid'
    }
    if (
        $isTokenRotationPlan -and (
            $result.planPurpose -ne
                'ROTATE_UNDERSIZED_FBSIR_TOKEN_SECRET' -or
            $result.tokenSecretPresent -ne $true -or
            $result.tokenSecretBelowMinimum -ne $true -or
            $result.canonicalTokenSecretAssignment -ne $true -or
            $result.predecessorConfigurationReceiptSha256 -ne
                $ExpectedPredecessorConfigurationReceiptSha256 -or
            $result.engineCounterpartClosureClaimed -ne $false -or
            $result.api2EventKeyAlreadyProvisioned -ne $true -or
            $result.api2EventKeyCustodySecure -ne $true
        )
    ) {
        throw 'token secret rotation Plan prerequisite proof is invalid'
    }
    if (
        $isReconciliationPlan -and (
            $result.planPurpose -ne
                'RECONCILE_EXISTING_ADMIN_ENGINE_CREDENTIAL_DELTA' -or
            $result.adminEngineCredentialPresent -ne $true -or
            $result.exactExistingAdminEngineDeltaValid -ne $true -or
            $result.predecessorConfigurationReceiptSha256 -ne
                $ExpectedPredecessorConfigurationReceiptSha256 -or
            $result.engineCounterpartClosureClaimed -ne $false -or
            $result.api2EventKeyAlreadyProvisioned -ne $true -or
            $result.api2EventKeyCustodySecure -ne $true
        )
    ) {
        throw 'configuration Reconcile Plan prerequisite proof is invalid'
    }
}
$anchor = if ($Mode -in @(
        'Apply',
        'Recover',
        'Reconcile',
        'RotateTokenSecret')) {
    Save-ExternalAnchor -WorkerResult $result
} else { $null }

[ordered]@{
    schema = 'fbsir.u3wDefaultOffConfigurationRunnerResult.v2'
    mode = $Mode
    runId = $RunId
    sourceCommit = $ExpectedCommit
    approvalReceiptSha256 = $approvalSha
    runnerSha256 = $runnerSha
    workerSha256 = $workerSha
    result = $result
    externalAnchor = $anchor
} | ConvertTo-Json -Depth 20
