[CmdletBinding()]
param(
    [ValidateSet('Plan', 'Adopt')]
    [string]$Mode = 'Adopt',
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string]$ExpectedCommit,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedBaselineReceiptSha256,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedBackupReceiptSha256,
    [string]$ApprovalReceiptPath,
    [ValidatePattern(
        '^w1a-admin-root-dependency-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$'
    )]
    [string]$RunId,
    [string]$AnchorOutputDirectory,
    [string]$PlanReceiptPath,
    [string]$ExpectedPlanReceiptSha256 = $('0' * 64),
    [ValidateRange(1, 30)]
    [int]$LockTimeoutSeconds = 15,
    [string]$SshKeyPath = $(if ($env:U3W_SSH_KEY_PATH) {
            $env:U3W_SSH_KEY_PATH
        } else {
            Join-Path $env:USERPROFILE '.ssh\id_ed25519_api2'
        }),
    [string]$KnownHostsPath = $(
        Join-Path $env:TEMP 'u3w-admin-root-dependency-known-hosts'
    )
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
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
    $suffix = -join (
        $bytes | ForEach-Object { $_.ToString('x2') }
    )
    return 'w1a-admin-root-dependency-{0}-{1}' -f (
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
                ForEach-Object { $_.ToString('x2') }
        )
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
    if (($remote[0] -split '\s+', 2)[0].ToLowerInvariant() -ne
        $ExpectedCommit) {
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
                    $ExpectedRemoteHostKeyFingerprint
                )
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
                    ($SshTarget -split '@', 2)[1]
                ) 2>$null
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

function Get-ApprovalDigest {
    if (-not (Test-Path -LiteralPath $ApprovalReceiptPath -PathType Leaf)) {
        throw 'adoption requires ApprovalReceiptPath'
    }
    $approval = Get-Content -Raw -LiteralPath $ApprovalReceiptPath |
        ConvertFrom-Json
    $expectedFields = @(
        'schema', 'action', 'targetHost', 'runId', 'sourceCommit',
        'approvedAt', 'expiresAt', 'authorizedBy',
        'concurrentDdlProhibited', 'productionFilesystemWrite',
        'productionDatabaseWrite', 'productionServiceChange',
        'officialExpertsPackageChange', 'database', 'databaseEndpoint',
        'databaseServerUuid', 'expectedLiveFactsSha256',
        'expectedDatabaseProtectionMode',
        'expectedPlanReceiptSha256',
        'expectedBaselineReceiptSha256',
        'expectedBackupReceiptSha256', 'runnerSha256', 'workerSha256'
    ) | Sort-Object
    $actualFields = @(
        $approval.PSObject.Properties.Name | Sort-Object
    )
    if (($expectedFields -join "`n") -ne ($actualFields -join "`n")) {
        throw 'approval receipt has missing or unknown fields'
    }
    try {
        $approved = [DateTimeOffset]::Parse($approval.approvedAt)
        $expires = [DateTimeOffset]::Parse($approval.expiresAt)
    }
    catch {
        throw 'approval receipt time is invalid'
    }
    if (
        $approval.schema -ne
            'fbsir.u3wProductionChangeApprovalReceipt.v1' -or
        $approval.action -ne
            'ADOPT_W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY' -or
        $approval.targetHost -ne 'api2.u3w.com' -or
        $approval.runId -ne $RunId -or
        $approval.sourceCommit -ne $ExpectedCommit -or
        $approval.database -ne 'fbsir' -or
        $approval.databaseEndpoint -notmatch '^[^:\s]+:[0-9]{1,5}$' -or
        $approval.databaseServerUuid -notmatch (
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-' +
            '[0-9a-f]{4}-[0-9a-f]{12}$'
        ) -or
        $approval.expectedLiveFactsSha256 -notmatch
            '^[0-9a-f]{64}$' -or
        $approval.expectedDatabaseProtectionMode -ne (
            'HOST_FLOCK_NAMED_LOCK_REPEATABLE_READ_' +
            'FULL_CONTROL_RANGE_MDL_AND_APPROVED_NO_DDL_WINDOW'
        ) -or
        $approval.expectedPlanReceiptSha256 -ne
            $ExpectedPlanReceiptSha256 -or
        $approval.expectedBaselineReceiptSha256 -ne
            $ExpectedBaselineReceiptSha256 -or
        $approval.expectedBackupReceiptSha256 -ne
            $ExpectedBackupReceiptSha256 -or
        $approval.runnerSha256 -ne $script:RunnerSha256 -or
        $approval.workerSha256 -ne $script:WorkerSha256 -or
        $approval.authorizedBy -ne 'workspace-user' -or
        $approval.concurrentDdlProhibited -ne $true -or
        $approval.productionFilesystemWrite -ne $true -or
        $approval.productionDatabaseWrite -ne $false -or
        $approval.productionServiceChange -ne $false -or
        $approval.officialExpertsPackageChange -ne $false -or
        $approved -gt [DateTimeOffset]::UtcNow -or
        $expires -le [DateTimeOffset]::UtcNow -or
        ($expires - $approved).TotalHours -gt 24
    ) {
        throw 'approval receipt identity, scope or validity is invalid'
    }
    $script:ApprovalBase64 = [Convert]::ToBase64String(
        [IO.File]::ReadAllBytes(
            (Resolve-Path -LiteralPath $ApprovalReceiptPath).Path
        )
    )
    if (
        $script:ExpectedLiveFactsSha256 -and
        $script:ExpectedLiveFactsSha256 -ne
            [string]$approval.expectedLiveFactsSha256
    ) {
        throw 'approval is not bound to the immutable Plan live facts'
    }
    $script:ExpectedLiveFactsSha256 =
        [string]$approval.expectedLiveFactsSha256
    return Get-Sha256 $ApprovalReceiptPath
}

function Get-PlanDigest {
    if (
        -not $PlanReceiptPath -or
        -not (Test-Path -LiteralPath $PlanReceiptPath -PathType Leaf) -or
        $ExpectedPlanReceiptSha256 -eq ('0' * 64)
    ) {
        throw 'Adopt requires an immutable PlanReceiptPath and digest'
    }
    $expectedPlanPath = Join-Path (
        (Resolve-Path -LiteralPath $AnchorOutputDirectory).Path
    ) "$RunId-admin-root-dependency-plan.json"
    if (
        (Resolve-Path -LiteralPath $PlanReceiptPath).Path -cne
            $expectedPlanPath
    ) {
        throw 'admin-root dependency Plan must use the fixed evidence path'
    }
    $digest = Get-Sha256 $PlanReceiptPath
    if ($digest -ne $ExpectedPlanReceiptSha256) {
        throw 'admin-root dependency Plan receipt digest mismatch'
    }
    $plan = Get-Content -Raw -LiteralPath $PlanReceiptPath |
        ConvertFrom-Json
    if (
        $plan.schema -ne 'fbsir.u3wAdminRootDependencyPlan.v2' -or
        $plan.mode -ne 'Plan' -or
        $plan.runId -ne $RunId -or
        $plan.sourceCommit -ne $ExpectedCommit -or
        $plan.targetHost -ne 'api2.u3w.com' -or
        $plan.database -ne 'fbsir' -or
        $plan.databaseProtectionMode -ne (
            'HOST_FLOCK_NAMED_LOCK_REPEATABLE_READ_' +
            'FULL_CONTROL_RANGE_MDL_AND_APPROVED_NO_DDL_WINDOW'
        ) -or
        $plan.liveFactsSha256 -notmatch '^[0-9a-f]{64}$' -or
        $plan.baselineLatestReceiptSha256 -ne
            $ExpectedBaselineReceiptSha256 -or
        $plan.backupLatestReceiptSha256 -ne
            $ExpectedBackupReceiptSha256 -or
        $plan.runnerSha256 -ne $script:RunnerSha256 -or
        $plan.workerSha256 -ne $script:WorkerSha256 -or
        $plan.actionRequired -ne
            'ADOPT_W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY' -or
        $plan.approvalRequired -ne $true -or
        $plan.productionFilesystemChanged -ne $false -or
        $plan.productionDatabaseChanged -ne $false -or
        $plan.productionServiceChanged -ne $false -or
        $plan.officialExpertsPackageChanged -ne $false
    ) {
        throw 'admin-root dependency Plan receipt identity is invalid'
    }
    $script:PlanBase64 = [Convert]::ToBase64String(
        [IO.File]::ReadAllBytes(
            (Resolve-Path -LiteralPath $PlanReceiptPath).Path
        )
    )
    $script:ExpectedLiveFactsSha256 =
        [string]$plan.liveFactsSha256
    return $digest
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
    if ($LASTEXITCODE -ne 0) {
        throw 'remote admin-root dependency worker failed'
    }
    $json = $output -join "`n"
    if (-not $json.Trim()) {
        throw 'remote admin-root dependency worker returned no result'
    }
    return $json | ConvertFrom-Json
}

function Write-ImmutableJson {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][object]$Value
    )
    $json = $Value | ConvertTo-Json -Depth 20
    $encoding = [Text.UTF8Encoding]::new($false)
    $stream = [IO.File]::Open(
        $Path, [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write, [IO.FileShare]::None
    )
    try {
        $bytes = $encoding.GetBytes($json + "`n")
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    }
    finally {
        $stream.Dispose()
    }
}

function Save-ExternalAnchor {
    param([Parameter(Mandatory = $true)][object]$WorkerResult)
    if (-not (Test-Path -LiteralPath $AnchorOutputDirectory)) {
        New-Item -ItemType Directory -Path $AnchorOutputDirectory -Force |
            Out-Null
    }
    $resolvedOutput = (Resolve-Path $AnchorOutputDirectory).Path
    $receiptPath = Join-Path $resolvedOutput (
        "$RunId-admin-root-dependency-adoption-receipt.json"
    )
    $temporaryPath = "$receiptPath.$PID.partial"
    try {
        $remotePath =
            "/opt/fbsir/admin/dependencies/w1a/$RunId/adoption-receipt.json"
        & scp.exe -i $SshKeyPath -o BatchMode=yes -o IdentitiesOnly=yes `
            -o IdentityAgent=none -o ConnectTimeout=10 `
            -o StrictHostKeyChecking=yes `
            -o "UserKnownHostsFile=$KnownHostsPath" `
            "${SshTarget}:$remotePath" $temporaryPath
        if ($LASTEXITCODE -ne 0 -or
            -not (Test-Path -LiteralPath $temporaryPath -PathType Leaf)) {
            throw 'failed to download admin-root adoption receipt'
        }
        $digest = Get-Sha256 $temporaryPath
        $receipt = Get-Content -Raw -LiteralPath $temporaryPath |
            ConvertFrom-Json
        if (
            $receipt.schema -ne
                'fbsir.u3wLegacyAdminRootDependencyAdoptionReceipt.v3' -or
            $receipt.adoptionState -ne
                'ADOPTED_EXISTING_EXACT_DEPENDENCY' -or
            $receipt.runId -ne $RunId -or
            $receipt.sourceCommit -ne $ExpectedCommit -or
            $receipt.targetHost -ne 'api2.u3w.com' -or
            $receipt.database -ne 'fbsir' -or
            $receipt.databaseProtectionMode -ne (
                'HOST_FLOCK_NAMED_LOCK_REPEATABLE_READ_' +
                'FULL_CONTROL_RANGE_MDL_AND_APPROVED_NO_DDL_WINDOW'
            ) -or
            $receipt.databaseServerUuid -notmatch (
                '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-' +
                '[0-9a-f]{4}-[0-9a-f]{12}$'
            ) -or
            $receipt.liveFacts.databaseServerUuid -ne
                $receipt.databaseServerUuid -or
            $receipt.liveFactsSha256 -ne $WorkerResult.liveFactsSha256 -or
            $receipt.liveFactsSha256 -ne
                $script:ExpectedLiveFactsSha256 -or
            $digest -ne $WorkerResult.adoptionReceiptSha256 -or
            $receipt.baselineLatestReceiptSha256 -ne
                $ExpectedBaselineReceiptSha256 -or
            $receipt.backupLatestReceiptSha256 -ne
                $ExpectedBackupReceiptSha256 -or
            $receipt.planReceiptSha256 -ne
                $script:PlanReceiptSha256 -or
            $receipt.approvalReceiptSha256 -ne
                $script:ApprovalReceiptSha256 -or
            $receipt.runnerSha256 -ne $script:RunnerSha256 -or
            $receipt.workerSha256 -ne $script:WorkerSha256 -or
            $receipt.originalExecutionClaimed -ne $false -or
            $receipt.productionDatabaseChanged -ne $false -or
            $receipt.productionServiceChanged -ne $false -or
            $receipt.officialExpertsPackageChanged -ne $false -or
            $receipt.secretsDisclosed -ne $false
        ) {
            throw 'downloaded admin-root adoption receipt is invalid'
        }
        if (Test-Path -LiteralPath $receiptPath -PathType Leaf) {
            if ((Get-Sha256 $receiptPath) -ne $digest) {
                throw 'immutable local admin-root adoption receipt changed'
            }
            Remove-Item -LiteralPath $temporaryPath -Force
        }
        else {
            Move-Item -LiteralPath $temporaryPath -Destination $receiptPath
        }
        $anchorPath = Join-Path $resolvedOutput (
            "$RunId-admin-root-dependency-external-anchor.json"
        )
        $anchor = [ordered]@{
            schema =
                'fbsir.u3wAdminRootDependencyExternalAnchor.v3'
            runId = $RunId
            sourceCommit = $ExpectedCommit
            targetHost = 'api2.u3w.com'
            adoptionReceiptPath = $receiptPath
            adoptionReceiptSha256 = $digest
            databaseServerUuid = $receipt.databaseServerUuid
            liveFactsSha256 = $receipt.liveFactsSha256
            baselineLatestReceiptSha256 =
                $ExpectedBaselineReceiptSha256
            backupLatestReceiptSha256 =
                $ExpectedBackupReceiptSha256
            planReceiptPath =
                (Resolve-Path -LiteralPath $PlanReceiptPath).Path
            planReceiptSha256 = $script:PlanReceiptSha256
            approvalReceiptSha256 = $script:ApprovalReceiptSha256
            runnerSha256 = $script:RunnerSha256
            workerSha256 = $script:WorkerSha256
            capturedAt = [DateTime]::UtcNow.ToString('o')
        }
        if (Test-Path -LiteralPath $anchorPath -PathType Leaf) {
            $existing = Get-Content -Raw -LiteralPath $anchorPath |
                ConvertFrom-Json
            $existingFields = @(
                $existing.psobject.Properties.Name | Sort-Object
            )
            $expectedFields = @(
                'schema', 'runId', 'sourceCommit', 'targetHost',
                'adoptionReceiptPath', 'adoptionReceiptSha256',
                'databaseServerUuid', 'liveFactsSha256',
                'baselineLatestReceiptSha256',
                'backupLatestReceiptSha256', 'planReceiptPath',
                'planReceiptSha256', 'approvalReceiptSha256',
                'runnerSha256', 'workerSha256', 'capturedAt'
            ) | Sort-Object
            if (
                ($existingFields -join "`n") -cne
                    ($expectedFields -join "`n") -or
                $existing.schema -cne
                    'fbsir.u3wAdminRootDependencyExternalAnchor.v3' -or
                $existing.runId -cne $RunId -or
                $existing.targetHost -cne 'api2.u3w.com' -or
                $existing.adoptionReceiptPath -cne $receiptPath -or
                $existing.adoptionReceiptSha256 -ne $digest -or
                $existing.sourceCommit -ne $ExpectedCommit -or
                $existing.databaseServerUuid -cne
                    [string]$receipt.databaseServerUuid -or
                $existing.liveFactsSha256 -cne
                    [string]$receipt.liveFactsSha256 -or
                $existing.baselineLatestReceiptSha256 -cne
                    $ExpectedBaselineReceiptSha256 -or
                $existing.backupLatestReceiptSha256 -cne
                    $ExpectedBackupReceiptSha256 -or
                $existing.planReceiptPath -cne
                    (Resolve-Path -LiteralPath $PlanReceiptPath).Path -or
                $existing.planReceiptSha256 -cne
                    $script:PlanReceiptSha256 -or
                $existing.approvalReceiptSha256 -ne
                    $script:ApprovalReceiptSha256 -or
                $existing.runnerSha256 -ne $script:RunnerSha256 -or
                $existing.workerSha256 -ne $script:WorkerSha256 -or
                [string]$existing.capturedAt -notmatch
                    '^[0-9]{4}-[0-9]{2}-[0-9]{2}T'
            ) {
                throw 'immutable admin-root external anchor changed'
            }
        }
        else {
            Write-ImmutableJson -Path $anchorPath -Value $anchor
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

if (-not $RunId) {
    $RunId = New-RunId
}
if ([string]::IsNullOrWhiteSpace($ExpectedPlanReceiptSha256)) {
    $ExpectedPlanReceiptSha256 = '0' * 64
}
if ($ExpectedPlanReceiptSha256 -notmatch '^[0-9a-f]{64}$') {
    throw 'ExpectedPlanReceiptSha256 is not a SHA-256 digest'
}
Assert-StrictHead
Assert-PrivateKey
Ensure-KnownHosts
$runnerBytes = Get-CommittedBlobBytes (
    'scripts/run-u3w-admin-root-dependency.ps1'
)
$workerBytes = Get-CommittedBlobBytes (
    'scripts/u3w-admin-root-dependency-remote.py'
)
$script:RunnerSha256 = Get-BytesSha256 $runnerBytes
$script:WorkerSha256 = Get-BytesSha256 $workerBytes
$script:ApprovalBase64 = ''
$script:ApprovalReceiptSha256 = '0' * 64
$script:PlanBase64 = ''
$script:PlanReceiptSha256 = '0' * 64
$script:ExpectedLiveFactsSha256 = $null
if ($Mode -eq 'Adopt') {
    $script:PlanReceiptSha256 = Get-PlanDigest
    $script:ApprovalReceiptSha256 = Get-ApprovalDigest
}
$remoteArguments = @(
    '--mode', $Mode,
    '--run-id', $RunId,
    '--source-commit', $ExpectedCommit,
    '--runner-sha', $script:RunnerSha256,
    '--worker-sha', $script:WorkerSha256,
    '--expected-baseline-receipt-sha',
    $ExpectedBaselineReceiptSha256,
    '--expected-backup-receipt-sha',
    $ExpectedBackupReceiptSha256,
    '--lock-timeout-seconds', $LockTimeoutSeconds
)
if ($Mode -eq 'Adopt') {
    $remoteArguments += @(
        '--plan-sha', $script:PlanReceiptSha256,
        '--plan-json-base64', $script:PlanBase64,
        '--approval-sha', $script:ApprovalReceiptSha256,
        '--approval-json-base64', $script:ApprovalBase64
    )
}
$result = Invoke-RemoteWorker -Arguments $remoteArguments `
    -WorkerBytes $workerBytes -WorkerSha256 $script:WorkerSha256
Assert-StrictHead
if ($Mode -eq 'Plan') {
    if (
        $result.schema -ne
            'fbsir.u3wAdminRootDependencyPlan.v2' -or
        $result.mode -ne 'Plan' -or
        $result.runId -ne $RunId -or
        $result.sourceCommit -ne $ExpectedCommit -or
        $result.targetHost -ne 'api2.u3w.com' -or
        $result.database -ne 'fbsir' -or
        $result.databaseProtectionMode -ne (
            'HOST_FLOCK_NAMED_LOCK_REPEATABLE_READ_' +
            'FULL_CONTROL_RANGE_MDL_AND_APPROVED_NO_DDL_WINDOW'
        ) -or
        $result.baselineLatestReceiptSha256 -ne
            $ExpectedBaselineReceiptSha256 -or
        $result.backupLatestReceiptSha256 -ne
            $ExpectedBackupReceiptSha256 -or
        $result.runnerSha256 -ne $script:RunnerSha256 -or
        $result.workerSha256 -ne $script:WorkerSha256 -or
        $result.actionRequired -ne
            'ADOPT_W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY' -or
        $result.approvalRequired -ne $true -or
        $result.wouldWriteProductionFilesystem -ne $true -or
        $result.wouldWriteProductionDatabase -ne $false -or
        $result.wouldChangeProductionService -ne $false -or
        $result.wouldChangeOfficialExpertsPackage -ne $false -or
        $result.productionFilesystemChanged -ne $false -or
        $result.productionDatabaseChanged -ne $false -or
        $result.productionServiceChanged -ne $false -or
        $result.officialExpertsPackageChanged -ne $false -or
        $result.originalExecutionClaimed -ne $false -or
        $result.secretsDisclosed -ne $false
    ) {
        throw 'remote admin-root dependency plan result is invalid'
    }
    if (-not (Test-Path -LiteralPath $AnchorOutputDirectory)) {
        New-Item -ItemType Directory -Path $AnchorOutputDirectory -Force |
            Out-Null
    }
    if (-not $PlanReceiptPath) {
        $PlanReceiptPath = Join-Path (
            (Resolve-Path $AnchorOutputDirectory).Path
        ) "$RunId-admin-root-dependency-plan.json"
    }
    elseif (
        [IO.Path]::GetFullPath($PlanReceiptPath) -cne
            (Join-Path (
                (Resolve-Path $AnchorOutputDirectory).Path
            ) "$RunId-admin-root-dependency-plan.json")
    ) {
        throw 'admin-root dependency Plan must use the fixed evidence path'
    }
    if (Test-Path -LiteralPath $PlanReceiptPath) {
        throw 'immutable local admin-root dependency Plan already exists'
    }
    Write-ImmutableJson -Path $PlanReceiptPath -Value $result
    $script:PlanReceiptSha256 = Get-Sha256 $PlanReceiptPath
    $ExpectedPlanReceiptSha256 = $script:PlanReceiptSha256
    $script:ExpectedLiveFactsSha256 =
        [string]$result.liveFactsSha256
    $anchor = $null
}
else {
    if (
        $result.schema -ne
            'fbsir.u3wAdminRootDependencyWorkerResult.v3' -or
        $result.runId -ne $RunId -or
        $result.sourceCommit -ne $ExpectedCommit -or
        $result.productionDatabaseChanged -ne $false -or
        $result.productionServiceChanged -ne $false -or
        $result.officialExpertsPackageChanged -ne $false -or
        $result.originalExecutionClaimed -ne $false -or
        $result.secretsDisclosed -ne $false
    ) {
        throw 'remote admin-root dependency adoption result is invalid'
    }
    $anchor = Save-ExternalAnchor -WorkerResult $result
}

[ordered]@{
    schema = 'fbsir.u3wAdminRootDependencyRunnerResult.v4'
    mode = $Mode
    runId = $RunId
    sourceCommit = $ExpectedCommit
    expectedBaselineReceiptSha256 =
        $ExpectedBaselineReceiptSha256
    expectedBackupReceiptSha256 = $ExpectedBackupReceiptSha256
    planReceiptPath = $PlanReceiptPath
    planReceiptSha256 = $script:PlanReceiptSha256
    expectedLiveFactsSha256 = $script:ExpectedLiveFactsSha256
    approvalReceiptSha256 = $script:ApprovalReceiptSha256
    runnerSha256 = $script:RunnerSha256
    workerSha256 = $script:WorkerSha256
    result = $result
    externalAnchor = $anchor
} | ConvertTo-Json -Depth 20
