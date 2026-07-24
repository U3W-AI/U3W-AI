[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Build', 'Plan', 'Stage', 'Apply', 'Rollback', 'Verify')]
    [string]$Mode,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string]$ExpectedCommit,
    [ValidatePattern('^w1a-release-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z$')]
    [string]$ReleaseId,
    [string]$BuildReceiptPath,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedBuildReceiptSha256,
    [string]$PlanReceiptPath,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedReleasePlanReceiptSha256,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedBackupReceiptSha256,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedLegacyBaselineReceiptDigest,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedConfigurationReceiptSha256,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedStageReceiptSha256,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ExpectedDeploymentReceiptSha256,
    [string]$ApprovalReceiptPath,
    [string]$PlanOutputPath,
    [string]$ExternalEvidenceDirectory,
    [string]$SshKeyPath = $(if ($env:U3W_SSH_KEY_PATH) { $env:U3W_SSH_KEY_PATH } else { Join-Path $env:USERPROFILE '.ssh\id_ed25519_api2' }),
    [string]$KnownHostsPath = $(Join-Path $env:TEMP 'u3w-default-off-release-known-hosts')
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$SshTarget = 'root@api2.u3w.com'
$ExpectedPublicKeyFingerprint =
    'SHA256:oCIzbO9W94qDBeS9MtetLCW0CjYMkyZqT6QkgcLkXk0'
$ExpectedRemoteHostKeyFingerprint =
    'SHA256:GkS/HJpLm48N+KaMV/WEVSBHnI8PKJsU+6ycAsn+mBA'
$RunnerContractVersion = 'fbsir.u3wDefaultOffReleaseRunner.v1'
$RequiredModes = @('Build', 'Plan', 'Stage', 'Apply', 'Rollback', 'Verify')
if (-not $ReleaseId) {
    $ReleaseId = 'w1a-release-{0}-{1}' -f $ExpectedCommit.Substring(0, 12),
        [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
}
if (-not $PlanOutputPath) {
    $PlanOutputPath = Join-Path $RepoRoot (
        'work\release-plans\w1a-default-off-release-plan-latest.json')
}
if (-not $PlanReceiptPath) {
    $PlanReceiptPath = $PlanOutputPath
}
if (-not $ExternalEvidenceDirectory) {
    $handoffRoot = Split-Path (
        Split-Path $RepoRoot -Parent) -Parent
    $ExternalEvidenceDirectory = Join-Path $handoffRoot (
        "deliverables\production-evidence\$ReleaseId")
}
$ZeroSha256 = '0' * 64

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "required file missing: $Path"
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).
        Hash.ToLowerInvariant()
}

function Resolve-JavaHome {
    $candidates = @(
        $env:U3W_JAVA_HOME,
        $env:JAVA_HOME,
        (Join-Path $env:USERPROFILE (
            '.cache\u3w-java-toolchain\jdk-17.0.19+10'))
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath (
                Join-Path $candidate 'bin\java.exe') -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw 'JDK is unavailable; set U3W_JAVA_HOME to a verified JDK 17 path'
}

function Resolve-MavenExecutable {
    $candidates = @(
        $env:U3W_MAVEN_EXE,
        (Join-Path $env:USERPROFILE (
            '.cache\u3w-java-toolchain\apache-maven-3.9.16\bin\mvn.cmd'))
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        $command = Get-Command mvn -ErrorAction SilentlyContinue
    }
    if ($null -eq $command) {
        throw 'Maven is unavailable; set U3W_MAVEN_EXE'
    }
    return $command.Source
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
    $bytes = Get-CommittedBlobBytes (
        'scripts/deploy-independent-board-default-off.ps1')
    return Get-BytesSha256 $bytes
}

function Get-WorkerSha256 {
    $bytes = Get-CommittedBlobBytes (
        'scripts/u3w-default-off-release-remote.py')
    return Get-BytesSha256 $bytes
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
        throw "origin branch is absent or ambiguous: $branch"
    }
    $remoteHead = ($remote[0] -split '\s+', 2)[0].ToLowerInvariant()
    $upstream = (& git -C $RepoRoot rev-parse '@{upstream}').
        Trim().ToLowerInvariant()
    if ($remoteHead -ne $ExpectedCommit -or
        $upstream -ne $ExpectedCommit) {
        throw 'HEAD, upstream and origin branch must be identical'
    }
    return [ordered]@{
        head = $head
        branch = $branch
        upstream = $upstream
        originHead = $remoteHead
    }
}

function Assert-RecoveryRunnerExact {
    $currentBytes = [IO.File]::ReadAllBytes($PSCommandPath)
    $committedSha = Get-RunnerSha256
    if ((Get-BytesSha256 $currentBytes) -ne $committedSha) {
        throw 'rollback/verify requires this exact committed runner file'
    }
    if ((Get-WorkerSha256) -notmatch '^[0-9a-f]{64}$') {
        throw 'rollback/verify cannot resolve the committed worker'
    }
    return [ordered]@{
        sourceCommit = $ExpectedCommit
        runnerSha256 = $committedSha
        workerSha256 = Get-WorkerSha256
        githubRequired = $false
        cleanWorktreeRequired = $false
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

function Get-TreeManifest {
    param([Parameter(Mandatory = $true)][string]$Root)
    $resolved = (Resolve-Path -LiteralPath $Root).Path
    $files = @(
        Get-ChildItem -LiteralPath $resolved -Recurse -File |
            Sort-Object FullName
    )
    if ($files.Count -eq 0) {
        throw "artifact tree is empty: $Root"
    }
    $lines = [Collections.Generic.List[string]]::new()
    [long]$bytes = 0
    $rootUri = [Uri]::new($resolved.TrimEnd('\') + '\')
    foreach ($file in $files) {
        if (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "artifact tree contains a reparse point: $($file.FullName)"
        }
        $relative = [Uri]::UnescapeDataString(
            $rootUri.MakeRelativeUri([Uri]::new($file.FullName)).ToString()
        ).Replace('\', '/')
        if ($relative.StartsWith('../') -or $relative.Contains("`n")) {
            throw 'artifact tree relative path is unsafe'
        }
        $sha = Get-Sha256 $file.FullName
        $lines.Add("$sha  $relative")
        $bytes += $file.Length
    }
    $payload = ($lines -join "`n") + "`n"
    $encoding = [Text.UTF8Encoding]::new($false)
    $hasher = [Security.Cryptography.SHA256]::Create()
    try {
        $digest = $hasher.ComputeHash($encoding.GetBytes($payload))
    }
    finally {
        $hasher.Dispose()
    }
    return [ordered]@{
        algorithm = 'u3w.sorted-posix-tree-sha256.v1'
        sha256 = (-join ($digest | ForEach-Object {
                    $_.ToString('x2')
                }))
        fileCount = $files.Count
        totalBytes = $bytes
        manifest = $payload
    }
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
        $encoding = [Text.UTF8Encoding]::new($false)
        [IO.File]::WriteAllText($partial, $Content, $encoding)
        Move-Item -LiteralPath $partial -Destination $Path -Force
    }
    finally {
        Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
    }
}

function Write-ContentAddressedPlanReceipt {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)
    $digest = Get-BytesSha256 $Bytes
    $directory = Join-Path $RepoRoot (
        'work\release-plans\by-sha256')
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    $path = Join-Path $directory "$digest.json"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $stream = $null
        try {
            $stream = [IO.File]::Open(
                $path,
                [IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write,
                [IO.FileShare]::None)
            $stream.Write($Bytes, 0, $Bytes.Length)
            $stream.Flush($true)
        }
        catch [IO.IOException] {
            if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
                throw
            }
        }
        finally {
            if ($null -ne $stream) {
                $stream.Dispose()
            }
        }
    }
    $item = Get-Item -LiteralPath $path -Force
    if (
        ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
        $item.Length -ne $Bytes.Length -or
        (Get-Sha256 $path) -cne $digest
    ) {
        throw 'content-addressed release Plan receipt is invalid'
    }
    if (-not $item.IsReadOnly) {
        $item.IsReadOnly = $true
        $item = Get-Item -LiteralPath $path -Force
    }
    if (-not $item.IsReadOnly) {
        throw 'content-addressed release Plan receipt is mutable'
    }
    return [ordered]@{
        path = $item.FullName
        sha256 = $digest
    }
}

function Resolve-BuildReceipt {
    if (-not $BuildReceiptPath -or
        -not (Test-Path -LiteralPath $BuildReceiptPath -PathType Leaf)) {
        throw "$Mode requires BuildReceiptPath"
    }
    $resolved = (Resolve-Path -LiteralPath $BuildReceiptPath).Path
    $receiptBytes = [IO.File]::ReadAllBytes($resolved)
    $digest = Get-BytesSha256 $receiptBytes
    if (-not $ExpectedBuildReceiptSha256 -or
        $digest -ne $ExpectedBuildReceiptSha256) {
        throw 'Build receipt anchor mismatch'
    }
    $receipt = [Text.UTF8Encoding]::new(
        $false, $true).GetString($receiptBytes) | ConvertFrom-Json
    if (
        $receipt.schema -ne 'fbsir.u3wDefaultOffBuildReceipt.v1' -or
        $receipt.sourceCommit -ne $ExpectedCommit -or
        $receipt.releaseId -ne $ReleaseId -or
        $receipt.runnerSha256 -ne (Get-RunnerSha256) -or
        $receipt.status -ne 'PASS'
    ) {
        throw 'Build receipt identity is invalid'
    }
    $backend = Join-Path $RepoRoot $receipt.backend.relativePath
    $frontend = Join-Path $RepoRoot $receipt.frontend.relativePath
    $verificationLog = Join-Path $RepoRoot (
        $receipt.verificationLog.relativePath)
    $packageLog = Join-Path $RepoRoot $receipt.packageLog.relativePath
    $releaseMarker = Join-Path $RepoRoot (
        $receipt.frontend.releaseMarker.relativePath)
    $frontendFacts = Get-TreeManifest $frontend
    if (
        (Get-Sha256 $backend) -ne $receipt.backend.sha256 -or
        (Get-Item -LiteralPath $backend).Length -ne
            [long]$receipt.backend.sizeBytes -or
        $frontendFacts.sha256 -ne $receipt.frontend.treeSha256 -or
        $frontendFacts.fileCount -ne [int]$receipt.frontend.fileCount -or
        $frontendFacts.totalBytes -ne [long]$receipt.frontend.totalBytes -or
        $receipt.verificationLog.exitCode -ne 0 -or
        $receipt.packageLog.exitCode -ne 0 -or
        (Get-Sha256 $verificationLog) -ne
            $receipt.verificationLog.sha256 -or
        (Get-Sha256 $packageLog) -ne $receipt.packageLog.sha256 -or
        (Get-Sha256 $releaseMarker) -ne
            $receipt.frontend.releaseMarker.sha256
    ) {
        throw 'Build artifacts no longer match the anchored receipt'
    }
    return [ordered]@{
        path = $resolved
        sha256 = $digest
        receipt = $receipt
        bytes = $receiptBytes
    }
}

function Resolve-PlanReceipt {
    param([Parameter(Mandatory = $true)]$Build)
    if (-not $PlanReceiptPath -or
        -not (Test-Path -LiteralPath $PlanReceiptPath -PathType Leaf)) {
        throw "$Mode requires PlanReceiptPath"
    }
    $resolved = (Resolve-Path -LiteralPath $PlanReceiptPath).Path
    $expectedPlanPath = Join-Path $RepoRoot (
        'work\release-plans\by-sha256\{0}.json' -f
            $ExpectedReleasePlanReceiptSha256.ToLowerInvariant())
    if (-not (Test-Path -LiteralPath $expectedPlanPath -PathType Leaf) -or
        $resolved -cne (Resolve-Path -LiteralPath $expectedPlanPath).Path) {
        throw 'release plan receipt is not the immutable content-addressed copy'
    }
    $item = Get-Item -LiteralPath $resolved -Force
    if (
        ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
        -not $item.IsReadOnly
    ) {
        throw 'release plan receipt custody is invalid'
    }
    $receiptBytes = [IO.File]::ReadAllBytes($resolved)
    $digest = Get-BytesSha256 $receiptBytes
    if (-not $ExpectedReleasePlanReceiptSha256 -or
        $digest -ne $ExpectedReleasePlanReceiptSha256) {
        throw 'release plan receipt anchor mismatch'
    }
    $receipt = [Text.UTF8Encoding]::new(
        $false, $true).GetString($receiptBytes) | ConvertFrom-Json
    $generated = [DateTimeOffset]::Parse($receipt.generatedAt)
    $expires = [DateTimeOffset]::Parse($receipt.expiresAt)
    if (
        $receipt.schema -ne 'fbsir.u3wDefaultOffReleasePlan.v1' -or
        $receipt.runnerContractVersion -ne $RunnerContractVersion -or
        $receipt.mode -ne 'Plan' -or
        $receipt.releaseId -ne $ReleaseId -or
        $receipt.sourceCommit -ne $ExpectedCommit -or
        $receipt.expectedSourceCommit -ne $ExpectedCommit -or
        $receipt.strictHeadClean -ne $true -or
        $receipt.productionChanged -ne $false -or
        $receipt.runnerSha256 -ne (Get-RunnerSha256) -or
        $receipt.workerSha256 -ne (Get-WorkerSha256) -or
        $receipt.buildReceiptSha256 -ne $Build.sha256 -or
        $generated -gt [DateTimeOffset]::UtcNow -or
        $expires -le [DateTimeOffset]::UtcNow -or
        ($expires - $generated).TotalHours -gt 24
    ) {
        throw 'release plan receipt identity or validity is invalid'
    }
    return [ordered]@{
        path = $resolved
        sha256 = $digest
        receipt = $receipt
        bytes = $receiptBytes
    }
}

function Invoke-Build {
    $git = Assert-StrictHead
    $verification = Join-Path $RepoRoot (
        'scripts\verify-independent-board-control-plane.ps1')
    $priorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $verificationOutput = @(
            & powershell.exe -NoProfile -ExecutionPolicy Bypass `
                -File $verification -Mode All 2>&1
        )
        $verificationExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $priorPreference
    }
    if ($verificationExitCode -ne 0) {
        throw 'control-plane verification/build failed'
    }
    $maven = Resolve-MavenExecutable
    $javaHome = Resolve-JavaHome
    $priorJavaHome = $env:JAVA_HOME
    try {
        $ErrorActionPreference = 'Continue'
        $env:JAVA_HOME = $javaHome
        $mavenOutput = @(
            & $maven -q -f (Join-Path $RepoRoot 'pom.xml') `
                -pl FBSir-admin -am -DskipTests package 2>&1
        )
        $mavenExitCode = $LASTEXITCODE
    }
    finally {
        if ($null -eq $priorJavaHome) {
            Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        }
        else {
            $env:JAVA_HOME = $priorJavaHome
        }
        $ErrorActionPreference = $priorPreference
    }
    if ($mavenExitCode -ne 0) {
        throw 'backend release package build failed'
    }
    $git = Assert-StrictHead
    $backendPath = Join-Path $RepoRoot (
        'FBSir-admin\target\fbsir-admin.jar')
    $frontendPath = Join-Path $RepoRoot 'FBSir-ui\dist'
    if (-not (Test-Path -LiteralPath $backendPath -PathType Leaf)) {
        throw 'backend build artifact is missing'
    }
    $releaseMarkerPath = Join-Path $frontendPath 'w1a-release.json'
    $releaseMarker = [ordered]@{
        schema = 'fbsir.u3wReleaseMarker.v1'
        releaseId = $ReleaseId
        sourceCommit = $ExpectedCommit
        officialExpertsPackageChanged = $false
    }
    Write-Utf8NoBomAtomic -Path $releaseMarkerPath -Content (
        ($releaseMarker | ConvertTo-Json -Compress) + "`n")
    $frontend = Get-TreeManifest $frontendPath
    $migrationPath = Join-Path $RepoRoot (
        'sql\update_20260723_independent_board_attribution_v1.sql')
    $receiptDirectory = Join-Path $RepoRoot (
        "work\release-builds\$ReleaseId")
    $verificationLogPath = Join-Path $receiptDirectory 'verification.log'
    $mavenLogPath = Join-Path $receiptDirectory 'maven-package.log'
    Write-Utf8NoBomAtomic -Path $verificationLogPath -Content (
        ($verificationOutput -join "`n") + "`n")
    Write-Utf8NoBomAtomic -Path $mavenLogPath -Content (
        ($mavenOutput -join "`n") + "`n")
    $receiptPath = Join-Path $receiptDirectory 'build-receipt.json'
    $receipt = [ordered]@{
        schema = 'fbsir.u3wDefaultOffBuildReceipt.v1'
        status = 'PASS'
        releaseId = $ReleaseId
        sourceCommit = $ExpectedCommit
        branch = $git.branch
        originHead = $git.originHead
        strictHeadClean = $true
        verificationCommand =
            'verify-independent-board-control-plane.ps1 -Mode All'
        verificationLog = [ordered]@{
            relativePath = (
                "work/release-builds/$ReleaseId/verification.log")
            sha256 = Get-Sha256 $verificationLogPath
            exitCode = $verificationExitCode
        }
        packageLog = [ordered]@{
            relativePath = (
                "work/release-builds/$ReleaseId/maven-package.log")
            sha256 = Get-Sha256 $mavenLogPath
            exitCode = $mavenExitCode
        }
        backend = [ordered]@{
            relativePath = 'FBSir-admin/target/fbsir-admin.jar'
            sha256 = Get-Sha256 $backendPath
            sizeBytes = (Get-Item -LiteralPath $backendPath).Length
        }
        frontend = [ordered]@{
            relativePath = 'FBSir-ui/dist'
            treeAlgorithm = $frontend.algorithm
            treeSha256 = $frontend.sha256
            fileCount = $frontend.fileCount
            totalBytes = $frontend.totalBytes
            releaseMarker = [ordered]@{
                relativePath = 'FBSir-ui/dist/w1a-release.json'
                sha256 = Get-Sha256 $releaseMarkerPath
            }
        }
        migrationSha256 = Get-Sha256 $migrationPath
        runnerSha256 = Get-RunnerSha256
        generatedAt = [DateTime]::UtcNow.ToString('o')
        productionChanged = $false
    }
    Write-Utf8NoBomAtomic -Path $receiptPath -Content (
        ($receipt | ConvertTo-Json -Depth 10) + "`n")
    return [ordered]@{
        schema = 'fbsir.u3wDefaultOffBuildRunnerResult.v1'
        mode = 'Build'
        releaseId = $ReleaseId
        sourceCommit = $ExpectedCommit
        buildReceiptPath = $receiptPath
        buildReceiptSha256 = Get-Sha256 $receiptPath
        backendSha256 = $receipt.backend.sha256
        frontendTreeSha256 = $receipt.frontend.treeSha256
        productionChanged = $false
    }
}

function Invoke-ReadOnlyRemotePlanSnapshot {
    Assert-PrivateKey
    Ensure-KnownHosts
    $collector = @'
import base64
import fcntl
import hashlib
import hmac
import json
import os
import pathlib
import re
import stat
import subprocess

ENV_PATH = pathlib.Path("/etc/u3w/fbsir-admin.env")
EVENT_KEY_PATH = pathlib.Path(
    "/etc/u3w/secrets/independent-board-attribution-event-key"
)
ADDITIONAL_CONFIG = pathlib.Path(
    "/opt/fbsir/admin/application-connector.yml"
)
RELEASE_ROOT = pathlib.Path("/opt/fbsir/admin/releases")
CURRENT_LINK = pathlib.Path("/opt/fbsir/admin/current")
RELEASE_DROPIN = pathlib.Path(
    "/etc/systemd/system/fbsir-admin.service.d/"
    "20-u3w-default-off-release.conf"
)
LATEST_RECEIPT = RELEASE_ROOT / "latest-receipt.json"
FLAGS = (
    "FBSIR_BOARD_ATTRIBUTION_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
    "FBSIR_BOARD_ATTRIBUTION_CREDIT_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_CANDIDATE_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_CREDIT_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_WRITER_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_INTENT_CLASSIFIER_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_ADMIN_READ_ENABLED",
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED",
)
EVENT_KEY_ID_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID"
)
EVENT_KEY_NAME = "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY"
PREVIOUS_EVENT_KEY_ID_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY_ID"
)
PREVIOUS_EVENT_KEY_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY"
)
SAME_BINDING_KEY_NAME = (
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET"
)
MANAGED_W1A_NAMES = FLAGS + (
    EVENT_KEY_ID_NAME,
    EVENT_KEY_NAME,
    PREVIOUS_EVENT_KEY_ID_NAME,
    PREVIOUS_EVENT_KEY_NAME,
    SAME_BINDING_KEY_NAME,
)
DATABASE_NAMES = (
    "WXFBSIR_MYSQL_URL",
    "WXFBSIR_MYSQL_USERNAME",
    "WXFBSIR_MYSQL_PASSWORD",
)
DATABASE_ALIASES = (
    "FBSIR_MYSQL_URL",
    "FBSIR_MYSQL_USERNAME",
    "FBSIR_MYSQL_PASSWORD",
)
SECURITY_NAMES = DATABASE_NAMES + DATABASE_ALIASES + FLAGS
FORBIDDEN_OVERRIDES = {
    "SPRING_APPLICATION_JSON",
    "SPRING_CONFIG_IMPORT",
    "SPRING_CONFIG_LOCATION",
    "SPRING_CONFIG_ADDITIONAL_LOCATION",
    "SPRING_CONFIG_NAME",
    "SPRING_CONFIG_ON_NOT_FOUND",
    "SPRING_PROFILES_ACTIVE",
    "SPRING_PROFILES_INCLUDE",
    "JAVA_TOOL_OPTIONS",
    "_JAVA_OPTIONS",
    "JDK_JAVA_OPTIONS",
}

def canonical_json(value):
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )

def sha(path):
    digest = hashlib.sha256()
    with pathlib.Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

def regular_manifest(path, allowed_modes=(0o600, 0o640, 0o644)):
    candidate = pathlib.Path(path)
    if not candidate.is_absolute():
        raise RuntimeError("manifest path is not absolute")
    status = candidate.lstat()
    if (
        not stat.S_ISREG(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink != 1
        or status.st_mode & 0o777 not in allowed_modes
    ):
        raise RuntimeError("manifest file custody is invalid")
    return {
        "path": str(candidate),
        "sha256": sha(candidate),
        "mode": status.st_mode & 0o777,
        "uid": status.st_uid,
        "gid": status.st_gid,
        "nlink": status.st_nlink,
    }

def stable_regular_manifest(
    path, allowed_modes=(0o600, 0o640, 0o644)
):
    candidate = pathlib.Path(path)
    if not candidate.is_absolute():
        raise RuntimeError("stable manifest path is not absolute")
    descriptor = os.open(
        candidate, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    )
    try:
        before = os.fstat(descriptor)
        if (
            not stat.S_ISREG(before.st_mode)
            or before.st_uid != 0
            or before.st_gid != 0
            or before.st_nlink != 1
            or before.st_mode & 0o777 not in allowed_modes
        ):
            raise RuntimeError("stable manifest custody is invalid")
        digest = hashlib.sha256()
        size = 0
        while True:
            block = os.read(descriptor, 1024 * 1024)
            if not block:
                break
            digest.update(block)
            size += len(block)
        after = os.fstat(descriptor)
        identity = lambda value: (
            value.st_dev, value.st_ino, value.st_mode, value.st_uid,
            value.st_gid, value.st_nlink, value.st_size,
            value.st_mtime_ns, value.st_ctime_ns,
        )
        if identity(before) != identity(after) or size != after.st_size:
            raise RuntimeError("stable manifest changed while reading")
        return {
            "path": str(candidate),
            "sha256": digest.hexdigest(),
            "mode": after.st_mode & 0o777,
            "uid": after.st_uid,
            "gid": after.st_gid,
            "nlink": after.st_nlink,
            "sizeBytes": size,
        }
    finally:
        os.close(descriptor)

def custody(path, allowed_modes=(0o600, 0o640, 0o644)):
    manifest = regular_manifest(path, allowed_modes)
    return {
        "path": manifest["path"],
        "uid": manifest["uid"],
        "gid": manifest["gid"],
        "mode": oct(manifest["mode"]),
        "nlink": manifest["nlink"],
    }

def parse_environment_file(path):
    values = {}
    for line_number, raw in enumerate(
        pathlib.Path(path).read_text(encoding="utf-8").splitlines(), 1
    ):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            raise RuntimeError(
                "invalid environment assignment at line {}".format(
                    line_number
                )
            )
        name, value = line.split("=", 1)
        name = name.strip()
        value = value.strip()
        if (
            re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name) is None
            or name in values
        ):
            raise RuntimeError("invalid or duplicate environment key")
        if (
            len(value) >= 2
            and value[0] == value[-1]
            and value[0] in ("'", '"')
        ):
            value = value[1:-1]
        values[name] = value
    if any(values.get(name) != "false" for name in FLAGS):
        raise RuntimeError("W1A flags are not explicitly false")
    if any(not values.get(name) for name in DATABASE_NAMES):
        raise RuntimeError("database credentials are absent")
    aliases = tuple(values.get(name) for name in DATABASE_ALIASES)
    if any(aliases) and (
        not all(aliases)
        or aliases != tuple(values[name] for name in DATABASE_NAMES)
    ):
        raise RuntimeError("database aliases drifted")
    return values

def environment_declaration(raw_value):
    value = str(raw_value or "")
    pattern = re.compile(
        r"""\s*(?P<token>-?(?:"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|[^\s()]+))
        (?:\s+\(ignore_errors=(?P<ignore>yes|no)\))?""",
        re.VERBOSE,
    )
    declarations = []
    position = 0
    while position < len(value):
        if not value[position:].strip():
            break
        match = pattern.match(value, position)
        if match is None:
            raise RuntimeError("EnvironmentFiles declaration is invalid")
        token = match.group("token")
        optional = token.startswith("-") or match.group("ignore") == "yes"
        if token.startswith("-"):
            token = token[1:]
        if (
            len(token) >= 2
            and token[0] in ("'", '"')
            and token[-1] == token[0]
        ):
            token = token[1:-1]
        declarations.append({"path": token, "ignoreErrors": optional})
        position = match.end()
    if declarations != [{
        "path": "/etc/u3w/fbsir-admin.env",
        "ignoreErrors": False,
    }]:
        raise RuntimeError(
            "fbsir-admin requires one mandatory fixed EnvironmentFile"
        )
    return declarations[0]

def process_environment(pid):
    path = pathlib.Path("/proc") / str(pid) / "environ"
    status = path.stat()
    if status.st_uid != 0:
        raise RuntimeError("process environment owner is invalid")
    values = {}
    for item in path.read_bytes().split(b"\0"):
        if not item:
            continue
        name_bytes, separator, value_bytes = item.partition(b"=")
        if not separator:
            raise RuntimeError("process environment entry is invalid")
        name = name_bytes.decode("utf-8", errors="strict")
        value = value_bytes.decode("utf-8", errors="strict")
        if name in values:
            raise RuntimeError("duplicate process environment key")
        values[name] = value
    return values

def decode_secret_material(encoded):
    value = str(encoded or "").strip()
    if not value:
        return None
    try:
        if value.startswith("base64:"):
            raw = value[7:]
            if len(raw) % 4 == 1:
                return None
            return base64.b64decode(
                raw + ("=" * (-len(raw) % 4)), validate=True
            )
        if value.startswith("hex:"):
            raw = value[4:]
            if (
                not raw
                or len(raw) % 2
                or re.fullmatch(r"[0-9a-fA-F]+", raw) is None
            ):
                return None
            return bytes.fromhex(raw)
        if value.startswith("utf8:"):
            value = value[5:]
        return value.encode("utf-8")
    except (ValueError, UnicodeError):
        return None

def security_evidence(process_values, configured_values):
    key_manifest = regular_manifest(EVENT_KEY_PATH, (0o600,))
    key = EVENT_KEY_PATH.read_bytes()
    if not key:
        raise RuntimeError("event key is empty")
    configured_event_key = decode_secret_material(
        configured_values.get(EVENT_KEY_NAME)
    )
    if (
        configured_event_key is None
        or not hmac.compare_digest(configured_event_key, key)
    ):
        raise RuntimeError("configured event key does not match API2 material")
    def digest(values, names):
        payload = canonical_json(
            [[name, values[name]] for name in names]
        ).encode("utf-8")
        return hmac.new(
            key,
            b"fbsir.u3wProcessSecurityConfiguration.v1\0" + payload,
            hashlib.sha256,
        ).hexdigest()
    expected_names = sorted(
        name for name in SECURITY_NAMES if name in configured_values
    )
    actual_names = sorted(
        name for name in SECURITY_NAMES if name in process_values
    )
    expected_digest = digest(configured_values, expected_names)
    actual_digest = digest(process_values, actual_names)
    configured_names = sorted(configured_values)
    process_configured_names = sorted(
        name for name in configured_names if name in process_values
    )
    configured_digest = digest(configured_values, configured_names)
    process_configured_digest = digest(
        process_values, process_configured_names
    )
    expected_database_names = sorted(
        name
        for name in DATABASE_NAMES + DATABASE_ALIASES
        if name in configured_values
    )
    actual_database_names = sorted(
        name
        for name in expected_database_names
        if name in process_values
    )
    expected_database_digest = digest(
        configured_values, expected_database_names
    )
    actual_database_digest = (
        digest(process_values, actual_database_names)
        if actual_database_names else None
    )
    database_matched = bool(
        actual_database_digest is not None
        and actual_database_names == expected_database_names
        and hmac.compare_digest(
            actual_database_digest, expected_database_digest
        )
        and all(
            name in process_values
            and hmac.compare_digest(
                process_values[name], configured_values[name]
            )
            for name in DATABASE_NAMES
        )
    )
    mismatch_names = sorted(
        name
        for name in process_configured_names
        if not hmac.compare_digest(
            process_values[name], configured_values[name]
        )
    )
    pending_names = sorted(
        set(configured_names) - set(process_configured_names)
    )
    configured_flags = {
        name: configured_values.get(name) for name in FLAGS
    }
    configured_matched = bool(
        process_configured_names == configured_names
        and hmac.compare_digest(
            process_configured_digest, configured_digest
        )
    )
    if configured_matched and not pending_names and not mismatch_names:
        load_state = "EXACT_CONFIGURED"
    elif (
        pending_names == sorted(MANAGED_W1A_NAMES)
        and not mismatch_names
        and all(name in configured_values for name in MANAGED_W1A_NAMES)
        and all(configured_flags[name] == "false" for name in FLAGS)
        and database_matched
    ):
        load_state = "LEGACY_W1A_PENDING_RESTART"
    else:
        load_state = "INVALID_PARTIAL_OR_DRIFTED"
    return {
        "api2EventKeyManifest": key_manifest,
        "processSecurityConfigurationNames": actual_names,
        "processSecurityConfigurationHmacSha256": actual_digest,
        "expectedSecurityConfigurationNames": expected_names,
        "expectedSecurityConfigurationHmacSha256": expected_digest,
        "processDatabaseBindingMatched": database_matched,
        "configuredEnvironmentSha256": sha(ENV_PATH),
        "configuredEnvironmentNames": configured_names,
        "configuredEnvironmentHmacSha256": configured_digest,
        "processConfiguredEnvironmentHmacSha256":
            process_configured_digest,
        "processConfiguredEnvironmentMatched": configured_matched,
        "configuredFlagValues": configured_flags,
        "processConfiguredEnvironmentMismatchNames": mismatch_names,
        "processPendingRestartEnvironmentNames": pending_names,
        "processConfiguredEnvironmentLoadState": load_state,
        "processConfiguredEnvironmentPreStageCompatible":
            load_state in {
                "EXACT_CONFIGURED",
                "LEGACY_W1A_PENDING_RESTART",
            },
    }

def spring_manifest():
    name_pattern = re.compile(
        r"application(?:-[A-Za-z0-9._-]+)?\.(?:yml|yaml|properties)",
        re.I,
    )
    admin_root = pathlib.Path("/opt/fbsir/admin")
    candidates = [
        entry for entry in admin_root.iterdir()
        if name_pattern.fullmatch(entry.name)
    ]
    config_root = admin_root / "config"
    if config_root.exists() or config_root.is_symlink():
        if config_root.is_symlink() or not config_root.is_dir():
            raise RuntimeError(
                "Spring default config directory custody is invalid"
            )
        for current, directories, files in os.walk(
            config_root, followlinks=False
        ):
            current_path = pathlib.Path(current)
            current_status = current_path.lstat()
            if (
                not stat.S_ISDIR(current_status.st_mode)
                or current_status.st_uid != 0
                or current_status.st_gid != 0
                or current_status.st_mode & 0o022
            ):
                raise RuntimeError(
                    "Spring config directory custody is invalid"
                )
            if any(
                (current_path / name).is_symlink()
                for name in directories
            ):
                raise RuntimeError("Spring config symlink is forbidden")
            candidates.extend(
                current_path / name for name in files
                if name_pattern.fullmatch(name)
            )
    expected = ADDITIONAL_CONFIG.resolve(strict=True)
    manifests = []
    seen = set()
    for candidate in candidates:
        manifest = regular_manifest(candidate)
        resolved = candidate.resolve(strict=True)
        if resolved != expected or resolved in seen:
            raise RuntimeError("unreviewed Spring config is present")
        seen.add(resolved)
        manifests.append({
            "path": manifest["path"],
            "sha256": manifest["sha256"],
            "mode": manifest["mode"],
        })
    if seen != {expected}:
        raise RuntimeError("reviewed Spring config is absent")
    return sorted(manifests, key=lambda item: item["path"])

def active_nginx_manifest():
    first = subprocess.run(
        ["nginx", "-T"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    ).stdout
    dump_bytes = first
    dump = dump_bytes.decode("utf-8", errors="strict")
    paths = sorted(set(re.findall(
        r"^# configuration file ([^:]+):$", dump, re.M
    )))
    manifests = []
    for value in paths:
        manifests.append(stable_regular_manifest(value))
    if not manifests:
        raise RuntimeError("active Nginx manifest is empty")
    second = subprocess.run(
        ["nginx", "-T"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    ).stdout
    if not hmac.compare_digest(first, second):
        raise RuntimeError("active Nginx changed during snapshot")
    return (
        manifests,
        hashlib.sha256(dump_bytes).hexdigest(),
    )

def release_root_manifest():
    if not RELEASE_ROOT.exists() and not RELEASE_ROOT.is_symlink():
        return []
    root_status = RELEASE_ROOT.lstat()
    if (
        not stat.S_ISDIR(root_status.st_mode)
        or stat.S_ISLNK(root_status.st_mode)
        or root_status.st_uid != 0
        or root_status.st_gid != 0
        or root_status.st_mode & 0o777 != 0o755
    ):
        raise RuntimeError("release root custody is invalid")
    entries = []
    for entry in sorted(RELEASE_ROOT.iterdir(), key=lambda item: item.name):
        status = entry.lstat()
        if (
            status.st_uid != 0
            or status.st_gid != 0
            or (
                not stat.S_ISLNK(status.st_mode)
                and status.st_mode & 0o022
            )
        ):
            raise RuntimeError("release root entry custody is invalid")
        item = {
            "name": entry.name,
            "uid": status.st_uid,
            "gid": status.st_gid,
            "mode": status.st_mode & 0o777,
            "nlink": status.st_nlink,
        }
        if stat.S_ISDIR(status.st_mode):
            item["type"] = "directory"
        elif stat.S_ISLNK(status.st_mode):
            if entry != LATEST_RECEIPT:
                raise RuntimeError("unexpected release root symlink")
            resolved = entry.resolve(strict=True)
            if RELEASE_ROOT.resolve(strict=True) not in resolved.parents:
                raise RuntimeError("latest receipt escaped release root")
            manifest = regular_manifest(resolved, (0o600,))
            item.update({
                "type": "symlink",
                "target": os.readlink(entry),
                "targetSha256": manifest["sha256"],
            })
        elif stat.S_ISREG(status.st_mode):
            item.update({
                "type": "file",
                "sha256": stable_regular_manifest(entry)["sha256"],
            })
        else:
            raise RuntimeError("unsupported release root entry type")
        entries.append(item)
    return entries

lock_path = pathlib.Path(
    "/opt/fbsir/admin/.u3w-production-change.lock"
)
lock_descriptor = os.open(
    lock_path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
)
lock_status = os.fstat(lock_descriptor)
if (
    not stat.S_ISREG(lock_status.st_mode)
    or lock_status.st_uid != 0
    or lock_status.st_gid != 0
    or lock_status.st_nlink != 1
    or lock_status.st_mode & 0o777 != 0o600
):
    raise RuntimeError("production lock custody is invalid")
fcntl.flock(lock_descriptor, fcntl.LOCK_SH)

show = subprocess.run([
    "systemctl", "show", "fbsir-admin.service",
    (
        "--property=ActiveState,MainPID,User,Group,FragmentPath,"
        "DropInPaths,EnvironmentFiles,ExecStart,WorkingDirectory,"
        "InvocationID,ExecMainStartTimestampMonotonic,NRestarts"
    ),
], check=True, text=True, stdout=subprocess.PIPE).stdout
service = {}
for line in show.splitlines():
    key, _, value = line.partition("=")
    service[key] = value
if (
    service.get("ActiveState") != "active"
    or service.get("User") not in ("", "root")
    or service.get("Group") not in ("", "root")
):
    raise RuntimeError("service identity is invalid")
service["User"] = service.get("User") or "root"
service["Group"] = service.get("Group") or "root"

declaration = environment_declaration(
    service.get("EnvironmentFiles")
)
env_manifest = regular_manifest(ENV_PATH, (0o600,))
env_manifest["ignoreErrors"] = declaration["ignoreErrors"]
configured_values = parse_environment_file(ENV_PATH)

pid = int(service.get("MainPID") or "0")
if pid <= 0:
    raise RuntimeError("active service PID is absent")
cmdline_path = pathlib.Path("/proc") / str(pid) / "cmdline"
if cmdline_path.stat().st_uid != 0:
    raise RuntimeError("process command line owner is invalid")
process_arguments = [
    item.decode("utf-8", errors="strict")
    for item in cmdline_path.read_bytes().split(b"\0")
    if item
]
if "-jar" not in process_arguments:
    raise RuntimeError("process JAR argument is absent")
process_jar = None
for argument in process_arguments[
    process_arguments.index("-jar") + 1:
]:
    if argument.startswith("/") and argument.endswith(".jar"):
        process_jar = argument
        break
jar_match = re.search(
    r"(/[A-Za-z0-9._/-]+\.jar)", service.get("ExecStart", "")
)
configured_jar = jar_match.group(1) if jar_match else None
if (
    not process_jar
    or not configured_jar
    or pathlib.Path(process_jar).resolve(strict=True)
        != pathlib.Path(configured_jar).resolve(strict=True)
):
    raise RuntimeError("configured and process JAR identity drifted")
process_values = process_environment(pid)
security = security_evidence(process_values, configured_values)
process_flags = {
    name: process_values.get(name) for name in FLAGS
}
normalized_flags = {
    re.sub(r"[^a-z0-9]", "", name.lower()) for name in FLAGS
}
forbidden_names = sorted(
    name for name in process_values
    if (
        name in FORBIDDEN_OVERRIDES
        or re.sub(r"[^a-z0-9]", "", name.lower()).startswith(
            "spring"
        )
        or (
            name not in FLAGS
            and re.sub(r"[^a-z0-9]", "", name.lower())
                in normalized_flags
        )
    )
)
if (
    any(
        value not in (None, "false")
        for value in process_flags.values()
    )
    or any(
        value != "false"
        for value in security["configuredFlagValues"].values()
    )
    or forbidden_names
    or security["processDatabaseBindingMatched"] is not True
    or security[
        "processConfiguredEnvironmentPreStageCompatible"
    ] is not True
    or security["processConfiguredEnvironmentMismatchNames"] != []
):
    raise RuntimeError("effective process configuration drifted")

fragment_manifest = regular_manifest(service.get("FragmentPath"))
dropin_manifest = sorted(
    [
        regular_manifest(value)
        for value in service.get("DropInPaths", "").split()
        if value
    ],
    key=lambda item: item["path"],
)
unit_files = sorted(
    [
        {"path": item["path"], "sha256": item["sha256"]}
        for item in [fragment_manifest] + dropin_manifest
    ],
    key=lambda item: item["path"],
)
external_manifest = spring_manifest()
nginx_manifest, nginx_dump_sha256 = active_nginx_manifest()

current_exists = CURRENT_LINK.exists() or CURRENT_LINK.is_symlink()
current_resolved = None
if CURRENT_LINK.is_symlink():
    current_resolved = str(CURRENT_LINK.resolve(strict=True))
prior_anchor = None
latest_document = None
if LATEST_RECEIPT.exists() or LATEST_RECEIPT.is_symlink():
    if not LATEST_RECEIPT.is_symlink():
        raise RuntimeError("latest release receipt link is invalid")
    latest_resolved = LATEST_RECEIPT.resolve(strict=True)
    if (
        RELEASE_ROOT.resolve(strict=True)
        not in latest_resolved.parents
    ):
        raise RuntimeError("latest release receipt escaped release root")
    latest_manifest = regular_manifest(latest_resolved, (0o600,))
    latest_document = json.loads(
        latest_resolved.read_text(encoding="utf-8")
    )
    if (
        latest_document.get("schema") in {
            "fbsir.u3wDefaultOffReleaseRollbackReceipt.v1",
            "fbsir.u3wDefaultOffRollbackVerificationReceipt.v1",
        }
        and latest_document.get("state") in {
            "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT",
            "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE",
        }
        and re.fullmatch(
            r"w1a-release-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z",
            str(latest_document.get("releaseId") or ""),
        )
        and re.fullmatch(
            r"[0-9a-f]{40}",
            str(latest_document.get("sourceCommit") or ""),
        )
    ):
        prior_anchor = {
            "releaseId": latest_document["releaseId"],
            "sourceCommit": latest_document["sourceCommit"],
            "receiptPath": str(latest_resolved),
            "receiptSha256": latest_manifest["sha256"],
            "receiptSchema": latest_document["schema"],
            "state": latest_document["state"],
        }

dropin_exists = RELEASE_DROPIN.exists() or RELEASE_DROPIN.is_symlink()
if (
    not RELEASE_ROOT.exists()
    and not RELEASE_ROOT.is_symlink()
    and not current_exists
    and not dropin_exists
    and latest_document is None
):
    stage_entry_topology = {
        "state": "UNTOUCHED_LEGACY",
        "priorRollbackAnchor": None,
    }
elif (
    RELEASE_ROOT.is_dir()
    and not RELEASE_ROOT.is_symlink()
    and not current_exists
    and dropin_exists
    and prior_anchor is not None
):
    stage_entry_topology = {
        "state": "EXACT_PRIOR_ROLLBACK_PREDECESSOR",
        "priorRollbackAnchor": prior_anchor,
    }
else:
    stage_entry_topology = {
        "state": "INVALID_STAGE_ENTRY",
        "priorRollbackAnchor": prior_anchor,
    }

snapshot = {
    "schema": "fbsir.u3wDefaultOffRemotePlanSnapshot.v1",
    "targetHost": "api2.u3w.com",
    "serviceUnit": "fbsir-admin.service",
    "service": service,
    "unitSha256": fragment_manifest["sha256"],
    "fragmentFileManifest": fragment_manifest,
    "dropInManifest": dropin_manifest,
    "unitFiles": unit_files,
    "environmentCustody": custody(ENV_PATH, (0o600,)),
    "environmentSha256": env_manifest["sha256"],
    "environmentFilePaths": [str(ENV_PATH)],
    "environmentFileManifest": [env_manifest],
    "additionalConfigCustody": custody(ADDITIONAL_CONFIG),
    "additionalConfigSha256": external_manifest[0]["sha256"],
    "externalConfigManifest": external_manifest,
    "activeJarPath": process_jar,
    "activeJarSha256": sha(process_jar),
    "configuredJarPath": configured_jar,
    "configuredJarSha256": sha(configured_jar),
    "processJarPath": process_jar,
    "processJarSha256": sha(process_jar),
    "processArgvSha256": hashlib.sha256(
        ("\0".join(process_arguments) + "\0").encode("utf-8")
    ).hexdigest(),
    "processEnvironmentNamesSha256": hashlib.sha256(
        ("\n".join(sorted(process_values)) + "\n").encode("utf-8")
    ).hexdigest(),
    "processFlagValues": process_flags,
    "processForbiddenOverrideNames": forbidden_names,
    "nginxConfigs": nginx_manifest,
    "activeNginxManifest": nginx_manifest,
    "nginxDumpSha256": nginx_dump_sha256,
    "releaseRootExists": RELEASE_ROOT.exists(),
    "releaseRootEntryManifest": release_root_manifest(),
    "currentLinkExists": current_exists,
    "currentLinkResolved": current_resolved,
    "currentLifecycleState": (
        prior_anchor["state"]
        if prior_anchor is not None
        else stage_entry_topology["state"]
    ),
    "stageEntryTopology": stage_entry_topology,
    "productionChanged": prior_anchor is not None,
    "productionChangedByPlan": False,
}
snapshot.update(security)
print(canonical_json(snapshot))
'@
    $collectorBytes = [Text.Encoding]::UTF8.GetBytes($collector)
    $collectorSha = Get-BytesSha256 $collectorBytes
    $payload = [Convert]::ToBase64String($collectorBytes)
    $bootstrap = 'import base64,hashlib,sys;' +
        'b=base64.b64decode(sys.stdin.buffer.read());' +
        'e=sys.argv.pop(1);a=hashlib.sha256(b).hexdigest();' +
        'a==e or sys.exit("collector sha256 mismatch");' +
        'exec(compile(b,"<u3w-release-plan-collector>","exec"),' +
        '{"__name__":"__main__"})'
    $escapedBootstrap = $bootstrap.Replace('"', '\"')
    $remoteCommand = "python3 -c '$escapedBootstrap' $collectorSha"
    $sshArguments = @(
        '-i', $SshKeyPath,
        '-o', 'BatchMode=yes',
        '-o', 'IdentitiesOnly=yes',
        '-o', 'IdentityAgent=none',
        '-o', 'ConnectTimeout=10',
        '-o', 'ConnectionAttempts=1',
        '-o', 'StrictHostKeyChecking=yes',
        '-o', "UserKnownHostsFile=$KnownHostsPath",
        $SshTarget, $remoteCommand
    )
    $output = @($payload | & ssh.exe @sshArguments)
    if ($LASTEXITCODE -ne 0 -or $output.Count -eq 0) {
        throw 'read-only release plan snapshot failed'
    }
    $result = ($output -join "`n") | ConvertFrom-Json
    return [ordered]@{
        snapshot = $result
        collectorSha256 = $collectorSha
    }
}

function Invoke-Plan {
    $git = Assert-StrictHead
    $build = Resolve-BuildReceipt
    $remoteCollection = Invoke-ReadOnlyRemotePlanSnapshot
    $remote = $remoteCollection.snapshot
    if (
        $remote.schema -ne
            'fbsir.u3wDefaultOffRemotePlanSnapshot.v1' -or
        $remote.targetHost -ne 'api2.u3w.com' -or
        $remote.serviceUnit -ne 'fbsir-admin.service' -or
        $remote.service.ActiveState -ne 'active' -or
        $remote.productionChangedByPlan -ne $false -or
        $remote.currentLinkExists -ne $false -or
        $remote.processDatabaseBindingMatched -ne $true -or
        $remote.processConfiguredEnvironmentPreStageCompatible -ne
            $true -or
        @($remote.processConfiguredEnvironmentMismatchNames).Count -ne
            0 -or
        @($remote.processForbiddenOverrideNames).Count -ne 0 -or
        @($remote.configuredFlagValues.psobject.Properties.Value |
            Where-Object { $_ -cne 'false' }).Count -ne 0 -or
        @($remote.processFlagValues.psobject.Properties.Value |
            Where-Object { $null -ne $_ -and $_ -cne 'false' }
        ).Count -ne 0 -or
        $remote.stageEntryTopology.state -notin @(
            'UNTOUCHED_LEGACY',
            'EXACT_PRIOR_ROLLBACK_PREDECESSOR'
        ) -or
        (
            $remote.stageEntryTopology.state -ceq
                'UNTOUCHED_LEGACY' -and
            (
                $remote.productionChanged -ne $false -or
                $remote.releaseRootExists -ne $false -or
                @($remote.releaseRootEntryManifest).Count -ne 0 -or
                $remote.processConfiguredEnvironmentLoadState -cne
                    'LEGACY_W1A_PENDING_RESTART' -or
                $remote.processConfiguredEnvironmentMatched -ne
                    $false -or
                $null -ne $remote.stageEntryTopology.priorRollbackAnchor
            )
        ) -or
        (
            $remote.stageEntryTopology.state -ceq
                'EXACT_PRIOR_ROLLBACK_PREDECESSOR' -and
            (
                $remote.productionChanged -ne $true -or
                $remote.releaseRootExists -ne $true -or
                $remote.processConfiguredEnvironmentLoadState -cne
                    'EXACT_CONFIGURED' -or
                $remote.processConfiguredEnvironmentMatched -ne
                    $true -or
                @($remote.processPendingRestartEnvironmentNames).Count -ne
                    0 -or
                $null -eq $remote.stageEntryTopology.priorRollbackAnchor
            )
        )
    ) {
        throw 'remote plan snapshot identity is invalid'
    }
    $null = Assert-StrictHead
    $runnerSha = Get-RunnerSha256
    $planGeneratedAt = [DateTime]::UtcNow
    $receipt = [ordered]@{
        schema = 'fbsir.u3wDefaultOffReleasePlan.v1'
        runnerContractVersion = $RunnerContractVersion
        mode = 'Plan'
        state = 'PLAN_VERIFIED_NOT_STAGE_ELIGIBLE'
        releaseId = $ReleaseId
        sourceCommit = $ExpectedCommit
        expectedSourceCommit = $ExpectedCommit
        branch = $git.branch
        originHead = $git.originHead
        strictHeadClean = $true
        runnerSha256 = $runnerSha
        workerSha256 = Get-WorkerSha256
        collectorSha256 = $remoteCollection.collectorSha256
        requiredModes = $RequiredModes
        buildReceiptPath = $build.path
        buildReceiptSha256 = $build.sha256
        backendBuildSha256 = $build.receipt.backend.sha256
        frontendTreeSha256 = $build.receipt.frontend.treeSha256
        migrationSha256 = $build.receipt.migrationSha256
        target = $remote
        candidateRoot =
            "/opt/fbsir/admin/releases/$ReleaseId"
        globalLock =
            '/opt/fbsir/admin/.u3w-production-change.lock'
        stagePrecondition = 'PREPARED_FOR_STAGE'
        stageState = 'STAGED_FOR_SWITCH'
        applyState = 'DEPLOYED_DEFAULT_OFF'
        prohibitedDuringPlan = @(
            'production file write',
            'database migration',
            'service restart',
            'nginx change',
            'traffic switch',
            'official experts package change'
        )
        recoveryBoundary =
            'backup recovery rehearsal is not database rollback'
        databaseRollbackSafety =
            'RETAIN_ADDITIVE_043_DORMANT_NO_DOWN'
        exactDatabaseDownContractPresent = $false
        applicationRollbackRehearsal =
            'REQUIRED_BEFORE_DEPLOYED_DEFAULT_OFF'
        generatedAt = $planGeneratedAt.ToString('o')
        expiresAt = $planGeneratedAt.AddHours(24).ToString('o')
        productionChanged = $false
    }
    $planContent = ($receipt | ConvertTo-Json -Depth 20) + "`n"
    $planEncoding = [Text.UTF8Encoding]::new($false)
    $immutablePlan = Write-ContentAddressedPlanReceipt -Bytes (
        $planEncoding.GetBytes($planContent))
    $latestPlanOutputPath = Join-Path $RepoRoot (
        'work\release-plans\w1a-default-off-release-plan-latest.json')
    Write-Utf8NoBomAtomic -Path $latestPlanOutputPath -Content $planContent
    $resolvedPlanOutputPath = [IO.Path]::GetFullPath($PlanOutputPath)
    $resolvedLatestPlanOutputPath =
        [IO.Path]::GetFullPath($latestPlanOutputPath)
    if (
        $resolvedPlanOutputPath -cne $resolvedLatestPlanOutputPath -and
        $resolvedPlanOutputPath -cne $immutablePlan.path
    ) {
        Write-Utf8NoBomAtomic -Path $PlanOutputPath -Content $planContent
    }
    return [ordered]@{
        schema = 'fbsir.u3wDefaultOffReleaseRunnerResult.v1'
        mode = 'Plan'
        state = $receipt.state
        releaseId = $ReleaseId
        sourceCommit = $ExpectedCommit
        planReceiptPath = $immutablePlan.path
        planReceiptSha256 = $immutablePlan.sha256
        buildReceiptSha256 = $build.sha256
        productionChanged = $false
    }
}

function Get-ApprovalDigest {
    if (-not $ApprovalReceiptPath -or
        -not (Test-Path -LiteralPath $ApprovalReceiptPath -PathType Leaf)) {
        throw "$Mode requires an explicit ApprovalReceiptPath"
    }
    $approvalBytes = [IO.File]::ReadAllBytes(
        (Resolve-Path -LiteralPath $ApprovalReceiptPath).Path)
    $approvalText = [Text.Encoding]::UTF8.GetString($approvalBytes)
    $approval = $approvalText | ConvertFrom-Json
    $fields = @(
        'schema', 'action', 'targetHost', 'runId', 'sourceCommit',
        'approvedAt', 'expiresAt', 'authorizedBy',
        'concurrentDdlProhibited', 'productionFilesystemWrite',
        'productionDatabaseWrite', 'productionServiceChange',
        'officialExpertsPackageChange',
        'expectedBuildReceiptSha256',
        'expectedReleasePlanReceiptSha256',
        'expectedBackupReceiptSha256',
        'expectedLegacyBaselineReceiptSha256',
        'expectedConfigurationReceiptSha256',
        'expectedStageReceiptSha256',
        'expectedDeploymentReceiptSha256',
        'runnerSha256', 'workerSha256'
    ) | Sort-Object
    $actual = @($approval.PSObject.Properties.Name | Sort-Object)
    if (($fields -join "`n") -ne ($actual -join "`n")) {
        throw 'release approval receipt has missing or unknown fields'
    }
    $expectedAction = @{
        Stage = 'STAGE_W1A_DEFAULT_OFF_RELEASE'
        Apply = 'APPLY_W1A_DEFAULT_OFF_RELEASE'
        Rollback = 'ROLLBACK_W1A_DEFAULT_OFF_RELEASE'
        Verify = 'VERIFY_W1A_DEFAULT_OFF_RELEASE'
    }[$Mode]
    $expectedDatabaseWrite = $Mode -eq 'Apply'
    $expectedServiceChange = $Mode -in @('Apply', 'Rollback')
    $stageAnchor = if ($ExpectedStageReceiptSha256) {
        $ExpectedStageReceiptSha256.ToLowerInvariant()
    } else {
        $ZeroSha256
    }
    $deploymentAnchor = if ($ExpectedDeploymentReceiptSha256) {
        $ExpectedDeploymentReceiptSha256.ToLowerInvariant()
    } else {
        $ZeroSha256
    }
    $approved = [DateTimeOffset]::Parse($approval.approvedAt)
    $expires = [DateTimeOffset]::Parse($approval.expiresAt)
    if (
        $approval.schema -ne
            'fbsir.u3wProductionChangeApprovalReceipt.v1' -or
        $approval.action -ne $expectedAction -or
        $approval.targetHost -ne 'api2.u3w.com' -or
        $approval.runId -ne $ReleaseId -or
        $approval.sourceCommit -ne $ExpectedCommit -or
        $approval.authorizedBy -ne 'workspace-user' -or
        $approval.concurrentDdlProhibited -ne $true -or
        $approval.productionFilesystemWrite -ne ($Mode -ne 'Verify') -or
        $approval.productionDatabaseWrite -ne $expectedDatabaseWrite -or
        $approval.productionServiceChange -ne $expectedServiceChange -or
        $approval.officialExpertsPackageChange -ne $false -or
        $approval.expectedBuildReceiptSha256 -ne
            $ExpectedBuildReceiptSha256 -or
        $approval.expectedReleasePlanReceiptSha256 -ne
            $ExpectedReleasePlanReceiptSha256 -or
        $approval.expectedBackupReceiptSha256 -ne
            $ExpectedBackupReceiptSha256 -or
        $approval.expectedLegacyBaselineReceiptSha256 -ne
            $ExpectedLegacyBaselineReceiptDigest -or
        $approval.expectedConfigurationReceiptSha256 -ne
            $ExpectedConfigurationReceiptSha256 -or
        $approval.expectedStageReceiptSha256 -ne $stageAnchor -or
        $approval.expectedDeploymentReceiptSha256 -ne
            $deploymentAnchor -or
        $approval.runnerSha256 -ne (Get-RunnerSha256) -or
        $approval.workerSha256 -ne (Get-WorkerSha256) -or
        $approved -gt [DateTimeOffset]::UtcNow -or
        $expires -le [DateTimeOffset]::UtcNow -or
        ($expires - $approved).TotalHours -gt 24
    ) {
        throw 'release approval identity, scope or validity is invalid'
    }
    return [ordered]@{
        sha256 = Get-BytesSha256 $approvalBytes
        base64 = [Convert]::ToBase64String($approvalBytes)
        bytes = $approvalBytes
    }
}

function Get-RemoteDeploymentContext {
    if (-not $ExpectedDeploymentReceiptSha256) {
        throw "$Mode requires ExpectedDeploymentReceiptSha256"
    }
    Assert-PrivateKey
    Ensure-KnownHosts
    $collector = @'
import hashlib,json,pathlib,re,stat
release_id=__RELEASE_ID__
source_commit=__SOURCE_COMMIT__
expected_sha=__DEPLOYMENT_SHA__
path=pathlib.Path(
  "/opt/fbsir/admin/releases",release_id,"deployment-receipt.json"
)
status=path.lstat()
if (
  not stat.S_ISREG(status.st_mode) or stat.S_ISLNK(status.st_mode)
  or status.st_uid != 0 or status.st_gid != 0
  or status.st_nlink != 1 or status.st_mode & 0o777 != 0o600
):
  raise RuntimeError("deployment receipt custody is invalid")
raw=path.read_bytes()
actual=hashlib.sha256(raw).hexdigest()
receipt=json.loads(raw.decode("utf-8"))
required=(
  "buildReceiptSha256","releasePlanReceiptSha256","backupReceiptSha256",
  "legacyBaselineReceiptSha256","configurationReceiptSha256",
  "stageReceiptSha256","backendBuildSha256","frontendBuildSha256",
  "migrationSha256","runnerSha256","workerSha256"
)
if (
  actual != expected_sha
  or receipt.get("schema")
    != "fbsir.u3wW1aDeploymentReadinessReceipt.v2"
  or receipt.get("state") != "DEPLOYED_DEFAULT_OFF"
  or receipt.get("releaseId") != release_id
  or receipt.get("sourceCommit") != source_commit
  or receipt.get("targetHost") != "api2.u3w.com"
  or receipt.get("serviceUnit") != "fbsir-admin.service"
  or receipt.get("database") != "fbsir"
  or any(re.fullmatch(r"[0-9a-f]{64}",str(receipt.get(k) or "")) is None
         for k in required)
  or receipt.get("databaseRollbackSafetyProven") is not True
  or receipt.get("databaseDownClaimed") is not False
  or type(receipt.get("productionDatabaseChangedThisRun")) is not bool
  or type(receipt.get("productionDatabaseChangedSinceStage")) is not bool
  or receipt.get("productionDatabaseChanged")
    != receipt.get("productionDatabaseChangedSinceStage")
):
  raise RuntimeError("deployment receipt identity is invalid")
print(json.dumps({
  "schema":"fbsir.u3wRemoteDeploymentRecoveryContext.v1",
  "releaseId":release_id,
  "sourceCommit":source_commit,
  "deploymentReceiptPath":str(path),
  "deploymentReceiptSha256":actual,
  **{key:receipt[key] for key in required}
},sort_keys=True))
'@
    $collector = $collector.
        Replace(
            '__RELEASE_ID__',
            ($ReleaseId | ConvertTo-Json -Compress)).
        Replace(
            '__SOURCE_COMMIT__',
            ($ExpectedCommit | ConvertTo-Json -Compress)).
        Replace(
            '__DEPLOYMENT_SHA__',
            ($ExpectedDeploymentReceiptSha256 |
                ConvertTo-Json -Compress))
    $collectorBytes = [Text.Encoding]::UTF8.GetBytes($collector)
    $collectorSha = Get-BytesSha256 $collectorBytes
    $payload = [Convert]::ToBase64String($collectorBytes)
    $bootstrap = 'import base64,hashlib,sys;' +
        'b=base64.b64decode(sys.stdin.buffer.read());' +
        'e=sys.argv.pop(1);a=hashlib.sha256(b).hexdigest();' +
        'a==e or sys.exit("collector sha256 mismatch");' +
        'exec(compile(b,"<u3w-deployment-recovery>","exec"),' +
        '{"__name__":"__main__"})'
    $escapedBootstrap = $bootstrap.Replace('"', '\"')
    $remoteCommand = "python3 -c '$escapedBootstrap' $collectorSha"
    $arguments = @()
    $arguments += Get-SshOptions
    $arguments += @($SshTarget, $remoteCommand)
    $output = @($payload | & ssh.exe @arguments)
    if ($LASTEXITCODE -ne 0 -or $output.Count -eq 0) {
        throw 'remote deployment recovery context failed'
    }
    $context = ($output -join "`n") | ConvertFrom-Json
    if (
        $context.schema -ne
            'fbsir.u3wRemoteDeploymentRecoveryContext.v1' -or
        $context.releaseId -ne $ReleaseId -or
        $context.sourceCommit -ne $ExpectedCommit -or
        $context.deploymentReceiptSha256 -ne
            $ExpectedDeploymentReceiptSha256
    ) {
        throw 'remote deployment recovery context is invalid'
    }
    return $context
}

function Set-OrAssertRecoveryAnchor {
    param(
        [Parameter(Mandatory = $true)][string]$VariableName,
        [Parameter(Mandatory = $true)][string]$Value
    )
    $current = Get-Variable -Name $VariableName -Scope Script `
        -ValueOnly -ErrorAction SilentlyContinue
    if ($current -and $current -ne $Value) {
        throw "$VariableName conflicts with the deployment receipt"
    }
    Set-Variable -Name $VariableName -Scope Script -Value $Value
}

function Resolve-RecoveryContext {
    $null = Assert-RecoveryRunnerExact
    $remote = Get-RemoteDeploymentContext
    Set-OrAssertRecoveryAnchor -VariableName (
        'ExpectedBuildReceiptSha256') `
        -Value $remote.buildReceiptSha256
    Set-OrAssertRecoveryAnchor -VariableName (
        'ExpectedReleasePlanReceiptSha256') `
        -Value $remote.releasePlanReceiptSha256
    Set-OrAssertRecoveryAnchor -VariableName (
        'ExpectedBackupReceiptSha256') `
        -Value $remote.backupReceiptSha256
    Set-OrAssertRecoveryAnchor -VariableName (
        'ExpectedLegacyBaselineReceiptDigest') `
        -Value $remote.legacyBaselineReceiptSha256
    Set-OrAssertRecoveryAnchor -VariableName (
        'ExpectedConfigurationReceiptSha256') `
        -Value $remote.configurationReceiptSha256
    Set-OrAssertRecoveryAnchor -VariableName (
        'ExpectedStageReceiptSha256') `
        -Value $remote.stageReceiptSha256
    if ($remote.runnerSha256 -ne (Get-RunnerSha256) -or
        $remote.workerSha256 -ne (Get-WorkerSha256)) {
        throw 'deployment receipt is bound to different release code'
    }
    $approval = Get-ApprovalDigest
    return [ordered]@{
        build = [ordered]@{
            sha256 = $remote.buildReceiptSha256
            receipt = [ordered]@{
                backend = [ordered]@{
                    sha256 = $remote.backendBuildSha256
                }
                frontend = [ordered]@{
                    treeSha256 = $remote.frontendBuildSha256
                }
                migrationSha256 = $remote.migrationSha256
            }
        }
        plan = [ordered]@{
            sha256 = $remote.releasePlanReceiptSha256
        }
        approval = $approval
        remoteDeployment = $remote
    }
}

function Assert-ReceiptAnchorsForMutatingMode {
    if (
        $Mode -in @('Rollback', 'Verify') -or
        (
            $Mode -eq 'Apply' -and
            $ExpectedDeploymentReceiptSha256
        )
    ) {
        return Resolve-RecoveryContext
    }
    if (
        -not $ExpectedBuildReceiptSha256 -or
        -not $ExpectedReleasePlanReceiptSha256 -or
        -not $ExpectedBackupReceiptSha256 -or
        -not $ExpectedLegacyBaselineReceiptDigest -or
        -not $ExpectedConfigurationReceiptSha256
    ) {
        throw "$Mode requires build, plan, backup, baseline and configuration receipt anchors"
    }
    if ($Mode -in @('Apply', 'Rollback', 'Verify') -and
        -not $ExpectedStageReceiptSha256) {
        throw "$Mode requires the staged receipt anchor"
    }
    $build = Resolve-BuildReceipt
    $plan = Resolve-PlanReceipt -Build $build
    $approval = Get-ApprovalDigest
    return [ordered]@{
        build = $build
        plan = $plan
        approval = $approval
    }
}

function Get-RemoteReceiptBytes {
    param(
        [Parameter(Mandatory = $true)][string]$RemotePath,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256
    )
    $releasePrefix = "/opt/fbsir/admin/releases/$ReleaseId/"
    $relativePath = if ($RemotePath.StartsWith(
            $releasePrefix, [StringComparison]::Ordinal)) {
        $RemotePath.Substring($releasePrefix.Length)
    } else {
        ''
    }
    $fixedReceiptPaths = @(
        'deployment-readiness-receipt.json',
        'deployment-receipt.json',
        'rollback-receipt.json',
        'rollback-verification-receipt.json',
        'evidence/application-rollback-assembly.json',
        'evidence/database-rollback-safety.json',
        'evidence/application-rollback-execution.json'
    )
    $failureReceiptNameMatched = $relativePath -cmatch (
        '^(?:apply|rollback)-failure-' +
        '[0-9]{8}T[0-9]{12}Z-[0-9a-f]{12}\.json$')
    if (
        -not $relativePath -or
        (
            $relativePath -notin $fixedReceiptPaths -and
            -not $failureReceiptNameMatched
        )
    ) {
        throw 'remote receipt path escaped the fixed release directory'
    }
    $temporary = Join-Path $env:TEMP (
        'u3w-release-receipt-{0}.json' -f
            [guid]::NewGuid().ToString('N'))
    try {
        $arguments = @()
        $arguments += Get-SshOptions
        $arguments += @(
            "$SshTarget`:$RemotePath",
            $temporary
        )
        & scp.exe @arguments
        if ($LASTEXITCODE -ne 0 -or
            -not (Test-Path -LiteralPath $temporary -PathType Leaf)) {
            throw 'remote receipt download failed'
        }
        $bytes = [IO.File]::ReadAllBytes($temporary)
        if ((Get-BytesSha256 $bytes) -ne $ExpectedSha256) {
            throw 'downloaded remote receipt SHA-256 drifted'
        }
        return ,$bytes
    }
    finally {
        Remove-Item -LiteralPath $temporary -Force `
            -ErrorAction SilentlyContinue
    }
}

function Invoke-CommittedRemoteWorker {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet(
            'PrepareStage', 'FinalizeStage', 'Apply', 'Rollback', 'Verify')]
        [string]$WorkerMode,
        [Parameter(Mandatory = $true)]$Context
    )
    Assert-PrivateKey
    Ensure-KnownHosts
    $workerBytes = Get-CommittedBlobBytes (
        'scripts/u3w-default-off-release-remote.py')
    $workerSha = Get-BytesSha256 $workerBytes
    $stageAnchor = if ($ExpectedStageReceiptSha256) {
        $ExpectedStageReceiptSha256
    } else {
        $ZeroSha256
    }
    $deploymentAnchor = if ($ExpectedDeploymentReceiptSha256) {
        $ExpectedDeploymentReceiptSha256
    } else {
        $ZeroSha256
    }
    $arguments = @(
        'u3w-default-off-release-remote.py',
        '--mode', $WorkerMode,
        '--release-id', $ReleaseId,
        '--source-commit', $ExpectedCommit,
        '--approval-sha', $Context.approval.sha256,
        '--approval-json-base64', $Context.approval.base64,
        '--runner-sha', (Get-RunnerSha256),
        '--worker-sha', $workerSha,
        '--build-receipt-sha', $Context.build.sha256,
        '--plan-receipt-sha', $Context.plan.sha256,
        '--backup-receipt-sha', $ExpectedBackupReceiptSha256,
        '--baseline-receipt-sha',
            $ExpectedLegacyBaselineReceiptDigest,
        '--configuration-receipt-sha',
            $ExpectedConfigurationReceiptSha256,
        '--stage-receipt-sha', $stageAnchor,
        '--deployment-receipt-sha', $deploymentAnchor,
        '--backend-sha', $Context.build.receipt.backend.sha256,
        '--frontend-tree-sha',
            $Context.build.receipt.frontend.treeSha256,
        '--migration-sha', $Context.build.receipt.migrationSha256
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
        'exec(compile(b,"<u3w-release-worker>","exec"),' +
        '{"__name__":"__main__"})'
    $escapedBootstrap = $bootstrap.Replace('"', '\"')
    $remoteCommand = "python3 -c '$escapedBootstrap'"
    $sshArguments = @()
    $sshArguments += Get-SshOptions
    $sshArguments += @($SshTarget, $remoteCommand)
    $output = @($payload | & ssh.exe @sshArguments)
    $workerExitCode = $LASTEXITCODE
    if ($output.Count -eq 0) {
        throw "$WorkerMode remote release worker failed"
    }
    $raw = ($output -join "`n").Trim()
    try {
        $result = $raw | ConvertFrom-Json
    }
    catch {
        throw "$WorkerMode remote release worker returned invalid JSON"
    }
    if ($workerExitCode -ne 0) {
        if (
            $result.schema -cne
                'fbsir.u3wDefaultOffReleaseWorkerError.v2' -or
            $result.state -cne 'WORKER_FAILED' -or
            $result.mode -cne $WorkerMode -or
            $result.releaseId -cne $ReleaseId -or
            $result.sourceCommit -cne $ExpectedCommit -or
            $result.approvalReceiptSha256 -cne
                $Context.approval.sha256 -or
            [string]$result.errorType -notmatch
                '^[A-Za-z_][A-Za-z0-9_.]{0,127}$' -or
            [string]$result.errorMessageSha256 -notmatch
                '^[0-9a-f]{64}$' -or
            $result.failureReceiptEvidenceValid -isnot [bool] -or
            $result.officialExpertsPackageChanged -ne $false
        ) {
            throw "$WorkerMode remote worker failure envelope is invalid"
        }
        $failureEnvelopeResult = [ordered]@{
            raw = $raw
            remoteReceiptPath = $null
            remoteReceiptSha256 = $null
            remoteReceiptBytes = $null
            remoteEvidenceReceipts = @()
        }
        $failureEnvelopeAnchor = Save-ExternalReleaseAnchor `
            -Name ($WorkerMode.ToLowerInvariant() + '-failure') `
            -WorkerResult $failureEnvelopeResult
        $failureReceiptBytes = $null
        $failureReceiptPath = [string]$result.failureReceiptPath
        $failureReceiptSha256 = [string]$result.failureReceiptSha256
        if ($result.failureReceiptEvidenceValid -eq $true) {
            if (
                $WorkerMode -notin @('Apply', 'Rollback') -or
                $failureReceiptSha256 -notmatch '^[0-9a-f]{64}$'
            ) {
                throw "$WorkerMode failure receipt anchor is invalid"
            }
            $failureReceiptBytes = Get-RemoteReceiptBytes `
                -RemotePath $failureReceiptPath `
                -ExpectedSha256 $failureReceiptSha256
            $failureReceipt = [Text.Encoding]::UTF8.GetString(
                $failureReceiptBytes) | ConvertFrom-Json
            $expectedFailureSchema = if ($WorkerMode -eq 'Apply') {
                'fbsir.u3wDefaultOffReleaseFailureReceipt.v1'
            } else {
                'fbsir.u3wDefaultOffRollbackFailureReceipt.v1'
            }
            $approvalField = if ($WorkerMode -eq 'Apply') {
                'applyApprovalReceiptSha256'
            } else {
                'rollbackApprovalReceiptSha256'
            }
            $allowedApplyFailureStates = @(
                'FAIL_CLOSED_BEFORE_APPLICATION_SWITCH',
                'PRE_APPLY_RECOVERY_FAILED_TOPOLOGY_UNCERTAIN',
                'APPLICATION_RESTORED_DATABASE_043_RETAINED_OR_FAIL_CLOSED',
                'APPLICATION_RESTORE_FAILED_TOPOLOGY_UNCERTAIN',
                'DEPLOYMENT_COMMIT_AMBIGUOUS_NO_AUTOMATIC_RESTORE',
                'POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE'
            )
            $failureStateValid = if ($WorkerMode -eq 'Apply') {
                $allowedApplyFailureStates -ccontains
                    [string]$failureReceipt.state
            } else {
                [string]$failureReceipt.state -cin @(
                    'ROLLBACK_FAILED_AFTER_BOUNDED_RELEASE_OWNED_RECOVERY',
                    'ROLLBACK_APPLICATION_RESTORED_EVIDENCE_FINALIZATION_FAILED'
                )
            }
            if (
                $failureReceipt.schema -cne $expectedFailureSchema -or
                $result.failureReceiptSchema -cne
                    $expectedFailureSchema -or
                $result.failureReceiptState -cne
                    [string]$failureReceipt.state -or
                -not $failureStateValid -or
                $failureReceipt.releaseId -cne $ReleaseId -or
                $failureReceipt.sourceCommit -cne $ExpectedCommit -or
                [string]$failureReceipt.$approvalField -cne
                    $Context.approval.sha256 -or
                $failureReceipt.officialExpertsPackageChanged -ne $false
            ) {
                throw "$WorkerMode downloaded failure receipt is invalid"
            }
        }
        elseif (
            $null -ne $result.failureReceiptPath -or
            $null -ne $result.failureReceiptSha256 -or
            $null -ne $result.failureReceiptSchema -or
            $null -ne $result.failureReceiptState
        ) {
            throw "$WorkerMode unverified failure receipt anchor is invalid"
        }
        $failureWorkerResult = [ordered]@{
            raw = $raw
            remoteReceiptPath = $failureReceiptPath
            remoteReceiptSha256 = $failureReceiptSha256
            remoteReceiptBytes = $failureReceiptBytes
            remoteEvidenceReceipts = @()
        }
        $failureAnchor = Save-ExternalReleaseAnchor `
            -Name ($WorkerMode.ToLowerInvariant() + '-failure') `
            -WorkerResult $failureWorkerResult
        if (
            $failureAnchor.path -cne $failureEnvelopeAnchor.path -or
            $failureAnchor.sha256 -cne $failureEnvelopeAnchor.sha256
        ) {
            throw "$WorkerMode failure envelope persistence drifted"
        }
        throw (
            "$WorkerMode remote release worker failed; external evidence: " +
            $failureAnchor.path)
    }
    $allowedStates = @{
        PrepareStage = @('READY_FOR_UPLOAD', 'ALREADY_STAGED')
        FinalizeStage = @('STAGED_FOR_SWITCH')
        Apply = @('DEPLOYED_DEFAULT_OFF')
        Rollback = @(
            'ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT',
            'ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE'
        )
        Verify = @('DEPLOYED_DEFAULT_OFF')
    }
    $transitionApprovalRequired = -not (
        $WorkerMode -eq 'PrepareStage' -and
        $result.state -ceq 'READY_FOR_UPLOAD'
    )
    $approvalShapeValid = (
        [string]$result.approvalReceiptSha256 -ceq
            [string]$Context.approval.sha256 -and
        (
            (
                $transitionApprovalRequired -and
                [string]$result.transitionApprovalReceiptSha256 -cmatch
                    '^[0-9a-f]{64}$'
            ) -or
            (
                -not $transitionApprovalRequired -and
                $null -eq $result.transitionApprovalReceiptSha256
            )
        )
    )
    $applyShapeValid = $true
    if ($WorkerMode -eq 'Apply') {
        $applyShapeValid = (
            $result.deploymentCommittedThisRun -is [bool] -and
            $result.deploymentAlreadyCommitted -is [bool] -and
            $result.latestReceiptLinkRepaired -is [bool] -and
            $result.recoveredExistingSideEffects -is [bool] -and
            $result.productionDatabaseChangedThisRun -is [bool] -and
            $result.productionDatabaseChangedSinceStage -is [bool] -and
            $result.productionDatabaseChanged -eq
                $result.productionDatabaseChangedThisRun -and
            (
                (
                    $result.deploymentCommittedThisRun -eq $true -and
                    $result.deploymentAlreadyCommitted -eq $false -and
                    $result.changeKind -ceq 'DEPLOYMENT_COMMIT' -and
                    $result.productionFilesystemChanged -eq $true -and
                    $result.productionServiceChanged -eq $true -and
                    $result.latestReceiptLinkRepaired -eq $true
                ) -or
                (
                    $result.deploymentCommittedThisRun -eq $false -and
                    $result.deploymentAlreadyCommitted -eq $true -and
                    $result.productionDatabaseChanged -eq $false -and
                    $result.productionDatabaseChangedThisRun -eq $false -and
                    $result.productionServiceChanged -eq $false -and
                    $result.productionFilesystemChanged -eq
                        $result.latestReceiptLinkRepaired -and
                    (
                        (
                            $result.latestReceiptLinkRepaired -eq $true -and
                            $result.changeKind -ceq
                                'RECEIPT_LINK_REPAIR'
                        ) -or
                        (
                            $result.latestReceiptLinkRepaired -eq $false -and
                            $result.changeKind -ceq
                                'IDEMPOTENT_REVALIDATION'
                        )
                    )
                )
            )
        )
    }
    if (
        $result.schema -ne
            'fbsir.u3wDefaultOffReleaseWorkerResult.v1' -or
        $result.mode -ne $WorkerMode -or
        $result.releaseId -ne $ReleaseId -or
        $result.sourceCommit -ne $ExpectedCommit -or
        -not $approvalShapeValid -or
        $allowedStates[$WorkerMode] -notcontains $result.state -or
        $result.productionFilesystemChanged -isnot [bool] -or
        $result.productionDatabaseChanged -isnot [bool] -or
        $result.productionServiceChanged -isnot [bool] -or
        $result.officialExpertsPackageChanged -ne $false -or
        (
            $WorkerMode -in @(
                'PrepareStage', 'FinalizeStage', 'Verify') -and
            (
                $result.productionDatabaseChanged -ne $false -or
                $result.productionServiceChanged -ne $false
            )
        ) -or
        (
            $WorkerMode -eq 'Apply' -and
            -not $applyShapeValid
        ) -or
        (
            $WorkerMode -eq 'Rollback' -and
            (
                $result.productionDatabaseChanged -ne $false -or
                (
                    $result.productionServiceChanged -eq $true -and
                    $result.productionFilesystemChanged -ne $true
                )
            )
        )
    ) {
        throw "$WorkerMode remote release worker result is invalid"
    }
    $receiptPath = $null
    $receiptSha256 = $null
    if ($WorkerMode -in @('FinalizeStage', 'Apply', 'Rollback')) {
        $receiptPath = [string]$result.receiptPath
        $receiptSha256 = [string]$result.receiptSha256
    }
    elseif ($WorkerMode -eq 'Verify') {
        $receiptPath = [string]$result.deploymentReceiptPath
        $receiptSha256 = [string]$result.deploymentReceiptSha256
    }
    $receiptBytes = $null
    $receipt = $null
    $supplementalEvidenceReceipts = @()
    if ($receiptPath) {
        if ($receiptSha256 -notmatch '^[0-9a-f]{64}$') {
            throw "$WorkerMode remote receipt digest is invalid"
        }
        $receiptBytes = Get-RemoteReceiptBytes `
            -RemotePath $receiptPath -ExpectedSha256 $receiptSha256
        $receipt = [Text.Encoding]::UTF8.GetString(
            $receiptBytes) | ConvertFrom-Json
        $expectedReceiptSchema = if (
            $WorkerMode -eq 'Rollback' -and
            $receiptPath.EndsWith(
                '/rollback-verification-receipt.json',
                [StringComparison]::Ordinal)
        ) {
            'fbsir.u3wDefaultOffRollbackVerificationReceipt.v1'
        }
        elseif ($WorkerMode -eq 'Rollback') {
            'fbsir.u3wDefaultOffReleaseRollbackReceipt.v1'
        } else {
            'fbsir.u3wW1aDeploymentReadinessReceipt.v2'
        }
        $receiptApprovalField = @{
            FinalizeStage = 'stageApprovalReceiptSha256'
            Apply = 'applyApprovalReceiptSha256'
            Rollback = 'rollbackApprovalReceiptSha256'
            Verify = 'applyApprovalReceiptSha256'
        }[$WorkerMode]
        $receiptApprovalValid = (
            [string]$receipt.$receiptApprovalField -ceq
                [string]$result.transitionApprovalReceiptSha256
        )
        if (
            $receipt.schema -ne $expectedReceiptSchema -or
            $receipt.releaseId -ne $ReleaseId -or
            $receipt.sourceCommit -ne $ExpectedCommit -or
            -not $receiptApprovalValid -or
            (
                $WorkerMode -eq 'FinalizeStage' -and
                $receipt.state -ne 'STAGED_FOR_SWITCH'
            ) -or
            (
                $WorkerMode -in @('Apply', 'Verify') -and
                $receipt.state -ne 'DEPLOYED_DEFAULT_OFF'
            ) -or
            (
                $WorkerMode -eq 'Rollback' -and
                (
                    $allowedStates.Rollback -notcontains $receipt.state -or
                    $result.state -cne $receipt.state
                )
            )
        ) {
            throw "$WorkerMode downloaded remote receipt is invalid"
        }
        if (
            $WorkerMode -eq 'Apply' -and
            (
                $receipt.productionDatabaseChangedThisRun -isnot [bool] -or
                $receipt.productionDatabaseChangedSinceStage -isnot [bool] -or
                $receipt.productionDatabaseChanged -ne
                    $receipt.productionDatabaseChangedSinceStage -or
                $result.productionDatabaseChangedSinceStage -ne
                    $receipt.productionDatabaseChangedSinceStage -or
                (
                    $result.deploymentCommittedThisRun -eq $true -and
                    $result.productionDatabaseChangedThisRun -ne
                        $receipt.productionDatabaseChangedThisRun
                )
            )
        ) {
            throw 'Apply database change receipt semantics are invalid'
        }
        if (
            $WorkerMode -eq 'Rollback' -and
            $receipt.schema -ceq
                'fbsir.u3wDefaultOffRollbackVerificationReceipt.v1'
        ) {
            $originalPath = [string]$receipt.rollbackReceiptPath
            $originalSha = [string]$receipt.rollbackReceiptSha256
            if (
                $originalPath -cne
                    "/opt/fbsir/admin/releases/$ReleaseId/" +
                    'rollback-receipt.json' -or
                $originalSha -notmatch '^[0-9a-f]{64}$' -or
                $receipt.databaseSafetyCurrentRead -cne 'VERIFIED' -or
                $receipt.databaseRollbackStrategy -cne
                    'RETAIN_ADDITIVE_043_DORMANT_NO_DOWN' -or
                $receipt.databaseDownClaimed -ne $false -or
                $receipt.productionFilesystemChanged -ne $true -or
                $receipt.productionDatabaseChanged -ne $false -or
                $receipt.productionServiceChanged -ne $false -or
                $receipt.officialExpertsPackageChanged -ne $false -or
                $receipt.retainedMigrationFacts.publicReceiptCount -ne 1 -or
                $receipt.retainedMigrationFacts.internalReceiptCount -ne 1 -or
                $receipt.retainedMigrationFacts.tableCount -ne 2 -or
                $receipt.retainedMigrationFacts.triggerCount -ne 2 -or
                $receipt.retainedMigrationFacts.permissionCount -ne 1 -or
                $receipt.retainedMigrationFacts.eventCount -isnot [long] -or
                $receipt.retainedMigrationFacts.eventCount -lt 0 -or
                $receipt.retainedMigrationFacts.journeyCount -isnot [long] -or
                $receipt.retainedMigrationFacts.journeyCount -lt 0 -or
                $receipt.retainedMigrationFacts.schemaFingerprintSha256 -cne
                    'fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d'
            ) {
                throw 'rollback verification original anchor is invalid'
            }
            $originalBytes = Get-RemoteReceiptBytes `
                -RemotePath $originalPath -ExpectedSha256 $originalSha
            $original = [Text.Encoding]::UTF8.GetString(
                $originalBytes) | ConvertFrom-Json
            if (
                $original.schema -cne
                    'fbsir.u3wDefaultOffReleaseRollbackReceipt.v1' -or
                $original.state -cne
                    'ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE' -or
                $original.releaseId -cne $ReleaseId -or
                $original.sourceCommit -cne $ExpectedCommit -or
                $original.deploymentReceiptPath -cne
                    $receipt.deploymentReceiptPath -or
                $original.deploymentReceiptSha256 -cne
                    $receipt.deploymentReceiptSha256 -or
                [string]$original.rollbackApprovalReceiptSha256 -notmatch
                    '^[0-9a-f]{64}$' -or
                $original.applicationRollbackVerified -ne $true -or
                $original.retainedRollbackDropIn -ne $true -or
                $original.databaseRollbackStrategy -cne
                    'RETAIN_ADDITIVE_043_DORMANT_NO_DOWN' -or
                $original.databaseDownClaimed -ne $false -or
                $original.databaseSafetyCurrentRead -notlike
                    'UNAVAILABLE:*' -or
                $null -ne $original.retainedMigrationFacts -or
                $original.allW1aFlagsExplicitFalse -ne $true -or
                $original.productionFilesystemChanged -ne $true -or
                $original.productionDatabaseChanged -ne $false -or
                $original.productionServiceChanged -isnot [bool] -or
                $original.officialExpertsPackageChanged -ne $false
            ) {
                throw 'downloaded original rollback receipt is invalid'
            }
            $supplementalEvidenceReceipts += [ordered]@{
                name = 'rollback-original'
                path = $originalPath
                sha256 = $originalSha
                schema = [string]$original.schema
                bytes = $originalBytes
            }
        }
    }
    $expectedEvidence = @{
        PrepareStage = @()
        FinalizeStage = @(
            'application-rollback-assembly',
            'database-rollback-safety'
        )
        Apply = @(
            'application-rollback-assembly',
            'database-rollback-safety',
            'application-rollback-execution'
        )
        Rollback = @(
            'application-rollback-assembly',
            'database-rollback-safety',
            'application-rollback-execution'
        )
        Verify = @(
            'application-rollback-assembly',
            'database-rollback-safety',
            'application-rollback-execution'
        )
    }
    $schemaByEvidenceName = @{
        'application-rollback-assembly' =
            'fbsir.u3wApplicationRollbackAssemblyReceipt.v1'
        'database-rollback-safety' =
            'fbsir.u3wDatabaseRollbackSafetyReceipt.v1'
        'application-rollback-execution' =
            'fbsir.u3wApplicationRollbackExecutionReceipt.v1'
    }
    $remoteEvidenceReceipts = @()
    $declaredEvidence = @($result.evidenceReceipts)
    $declaredNames = @(
        $declaredEvidence | ForEach-Object { [string]$_.name })
    if (
        $declaredEvidence.Count -ne $expectedEvidence[$WorkerMode].Count -or
        @($declaredNames | Sort-Object -Unique).Count -ne
            $declaredNames.Count -or
        ((@($declaredNames | Sort-Object) -join "`n") -cne
            (@($expectedEvidence[$WorkerMode] | Sort-Object) -join "`n"))
    ) {
        throw "$WorkerMode nested evidence receipt set is invalid"
    }
    foreach ($item in $declaredEvidence) {
        $name = [string]$item.name
        $path = [string]$item.path
        $sha256 = [string]$item.sha256
        $schema = [string]$item.schema
        if (
            $schemaByEvidenceName[$name] -cne $schema -or
            $sha256 -notmatch '^[0-9a-f]{64}$'
        ) {
            throw "$WorkerMode nested evidence receipt identity is invalid"
        }
        $nestedBytes = Get-RemoteReceiptBytes `
            -RemotePath $path -ExpectedSha256 $sha256
        $nested = [Text.Encoding]::UTF8.GetString(
            $nestedBytes) | ConvertFrom-Json
        if (
            $nested.schema -cne $schema -or
            $nested.releaseId -cne $ReleaseId -or
            $nested.sourceCommit -cne $ExpectedCommit
        ) {
            throw "$WorkerMode downloaded nested evidence is invalid"
        }
        if ($receipt -and $WorkerMode -ne 'Rollback') {
            $pathField = @{
                'application-rollback-assembly' =
                    'applicationRollbackAssemblyReceiptPath'
                'database-rollback-safety' =
                    'databaseRollbackSafetyReceiptPath'
                'application-rollback-execution' =
                    'applicationRollbackReceiptPath'
            }[$name]
            $shaField = @{
                'application-rollback-assembly' =
                    'applicationRollbackAssemblyReceiptSha256'
                'database-rollback-safety' =
                    'databaseRollbackSafetyReceiptSha256'
                'application-rollback-execution' =
                    'applicationRollbackReceiptSha256'
            }[$name]
            if (
                [string]$receipt.$pathField -cne $path -or
                [string]$receipt.$shaField -cne $sha256
            ) {
                throw "$WorkerMode top receipt did not anchor nested evidence"
            }
        }
        $remoteEvidenceReceipts += [ordered]@{
            name = $name
            path = $path
            sha256 = $sha256
            schema = $schema
            bytes = $nestedBytes
        }
    }
    $remoteEvidenceReceipts += $supplementalEvidenceReceipts
    return [ordered]@{
        result = $result
        raw = $raw
        remoteReceiptPath = $receiptPath
        remoteReceiptSha256 = $receiptSha256
        remoteReceiptBytes = $receiptBytes
        remoteEvidenceReceipts = $remoteEvidenceReceipts
    }
}

function Assert-NoReparsePointChain {
    param([Parameter(Mandatory = $true)][string]$Path)
    $current = [IO.Path]::GetFullPath($Path)
    while ($current) {
        $item = Get-Item -LiteralPath $current -Force `
            -ErrorAction SilentlyContinue
        if (
            $null -ne $item -and
            (
                ($item.Attributes -band
                    [IO.FileAttributes]::ReparsePoint) -ne 0 -or
                -not [string]::IsNullOrWhiteSpace(
                    [string]$item.LinkType)
            )
        ) {
            throw "external evidence path contains a link or reparse point: $current"
        }
        $parent = [IO.Directory]::GetParent($current)
        if ($null -eq $parent) {
            break
        }
        $current = $parent.FullName
    }
}

function Assert-ExternalEvidenceLeaf {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$RequireExisting
    )
    $fullEvidence = [IO.Path]::GetFullPath(
        $ExternalEvidenceDirectory).TrimEnd('\')
    $fullPath = [IO.Path]::GetFullPath($Path)
    if (
        [IO.Path]::GetDirectoryName($fullPath) -cne $fullEvidence
    ) {
        throw 'external evidence file escaped its exact release directory'
    }
    Assert-NoReparsePointChain -Path $fullEvidence
    $item = Get-Item -LiteralPath $fullPath -Force `
        -ErrorAction SilentlyContinue
    if ($RequireExisting -and $null -eq $item) {
        throw 'external evidence file is absent'
    }
    if ($null -ne $item) {
        if (
            $item.PSIsContainer -or
            ($item.Attributes -band
                [IO.FileAttributes]::ReparsePoint) -ne 0 -or
            -not [string]::IsNullOrWhiteSpace(
                [string]$item.LinkType)
        ) {
            throw 'external evidence leaf is not a regular unlinked file'
        }
        $hardlinks = @(
            & fsutil.exe hardlink list $fullPath 2>$null)
        if ($LASTEXITCODE -ne 0 -or $hardlinks.Count -ne 1) {
            throw 'external evidence leaf hardlink custody is invalid'
        }
    }
    return $fullPath
}

function Write-ImmutableEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][byte[]]$Bytes
    )
    $fullPath = Assert-ExternalEvidenceLeaf -Path $Path
    if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
        $null = Assert-ExternalEvidenceLeaf -Path $fullPath `
            -RequireExisting
        $attributes = [IO.File]::GetAttributes($fullPath)
        if (
            ($attributes -band [IO.FileAttributes]::ReadOnly) -eq 0
        ) {
            throw 'existing external evidence is not read-only'
        }
        $existing = [IO.File]::ReadAllBytes(
            (Resolve-Path -LiteralPath $fullPath).Path)
        if ((Get-BytesSha256 $existing) -ne (Get-BytesSha256 $Bytes)) {
            throw "external evidence path already contains different bytes: $fullPath"
        }
        return
    }
    $directory = Split-Path -Parent $fullPath
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    Assert-NoReparsePointChain -Path $directory
    $stream = [IO.File]::Open(
        $fullPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write,
        [IO.FileShare]::None)
    try {
        $stream.Write($Bytes, 0, $Bytes.Length)
        $stream.Flush($true)
    }
    finally {
        $stream.Dispose()
    }
    $null = Assert-ExternalEvidenceLeaf -Path $fullPath `
        -RequireExisting
    [IO.File]::SetAttributes(
        $fullPath,
        [IO.File]::GetAttributes($fullPath) -bor
            [IO.FileAttributes]::ReadOnly)
    $null = Assert-ExternalEvidenceLeaf -Path $fullPath `
        -RequireExisting
}

function Save-ExternalApprovalAnchor {
    param(
        [Parameter(Mandatory = $true)][string]$ApprovalMode,
        [Parameter(Mandatory = $true)]$Approval
    )
    $bytes = [byte[]]($Approval.bytes)
    if (
        $bytes.Count -eq 0 -or
        (Get-BytesSha256 $bytes) -cne [string]$Approval.sha256
    ) {
        throw 'approval bytes drifted before external persistence'
    }
    $modeLabel = $ApprovalMode.ToLowerInvariant()
    $fileName = "approval-$modeLabel-$($Approval.sha256).json"
    $path = Join-Path (
        [IO.Path]::GetFullPath($ExternalEvidenceDirectory)) $fileName
    Write-ImmutableEvidence -Path $path -Bytes $bytes
    $encoding = [Text.UTF8Encoding]::new($false)
    $shaBytes = $encoding.GetBytes(
        "$($Approval.sha256)  $fileName`n")
    Write-ImmutableEvidence -Path "$path.sha256" -Bytes $shaBytes
    return [ordered]@{
        path = $path
        sha256 = [string]$Approval.sha256
    }
}

function Assert-ExternalEvidenceWritable {
    $fullEvidence = [IO.Path]::GetFullPath($ExternalEvidenceDirectory)
    $fullRepo = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\') + '\'
    Assert-NoReparsePointChain -Path $RepoRoot
    if (($fullEvidence.TrimEnd('\') + '\').StartsWith(
            $fullRepo, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'external release evidence must be outside the Git repository'
    }
    if (-not (Test-Path -LiteralPath $fullEvidence -PathType Container)) {
        New-Item -ItemType Directory -Path $fullEvidence -Force | Out-Null
    }
    Assert-NoReparsePointChain -Path $fullEvidence
    $evidenceItem = Get-Item -LiteralPath $fullEvidence -Force
    if (
        -not $evidenceItem.PSIsContainer -or
        ($evidenceItem.Attributes -band
            [IO.FileAttributes]::ReparsePoint) -ne 0
    ) {
        throw 'external evidence directory custody is invalid'
    }
    $probeName = '.w1a-write-probe-' + [IO.Path]::GetRandomFileName()
    $probePath = Join-Path $fullEvidence $probeName
    $stream = $null
    try {
        $stream = [IO.File]::Open(
            $probePath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write,
            [IO.FileShare]::None)
        $probe = [Text.Encoding]::UTF8.GetBytes(
            "w1a-external-evidence-write-probe`n")
        $stream.Write($probe, 0, $probe.Length)
        $stream.Flush($true)
        $stream.Dispose()
        $stream = $null
        $observed = [IO.File]::ReadAllBytes($probePath)
        if ((Get-BytesSha256 $observed) -ne (Get-BytesSha256 $probe)) {
            throw 'external release evidence write probe drifted'
        }
    }
    finally {
        if ($null -ne $stream) {
            $stream.Dispose()
        }
        if (Test-Path -LiteralPath $probePath -PathType Leaf) {
            $resolvedProbe = [IO.Path]::GetFullPath(
                (Resolve-Path -LiteralPath $probePath).Path)
            $evidencePrefix = $fullEvidence.TrimEnd('\') + '\'
            if (-not $resolvedProbe.StartsWith(
                    $evidencePrefix,
                    [StringComparison]::OrdinalIgnoreCase)) {
                throw 'external evidence write probe escaped its directory'
            }
            Remove-Item -LiteralPath $resolvedProbe -Force
        }
    }
}

function Save-ExternalReleaseAnchor {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)]$WorkerResult
    )
    $fullEvidence = [IO.Path]::GetFullPath($ExternalEvidenceDirectory)
    $fullRepo = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\') + '\'
    if (($fullEvidence.TrimEnd('\') + '\').StartsWith(
            $fullRepo, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'external release evidence must be outside the Git repository'
    }
    $encoding = [Text.UTF8Encoding]::new($false)
    $bytes = $encoding.GetBytes($WorkerResult.raw + "`n")
    $sha = Get-BytesSha256 $bytes
    $fileName = "$Name-$sha-worker-result.json"
    $path = Join-Path $fullEvidence $fileName
    Write-ImmutableEvidence -Path $path -Bytes $bytes
    $shaBytes = $encoding.GetBytes("$sha  $fileName`n")
    Write-ImmutableEvidence -Path "$path.sha256" -Bytes $shaBytes
    $remoteReceiptEvidence = $null
    if ($WorkerResult.remoteReceiptBytes) {
        $remoteSha = Get-BytesSha256 $WorkerResult.remoteReceiptBytes
        if ($remoteSha -ne $WorkerResult.remoteReceiptSha256) {
            throw 'remote receipt evidence digest drifted before persistence'
        }
        $remoteFileName = (
            "$Name-$remoteSha-remote-receipt.json")
        $remotePath = Join-Path $fullEvidence $remoteFileName
        Write-ImmutableEvidence -Path $remotePath `
            -Bytes $WorkerResult.remoteReceiptBytes
        $remoteShaBytes = $encoding.GetBytes(
            "$remoteSha  $remoteFileName`n")
        Write-ImmutableEvidence -Path "$remotePath.sha256" `
            -Bytes $remoteShaBytes
        $remoteReceiptEvidence = [ordered]@{
            path = $remotePath
            sha256 = $remoteSha
        }
    }
    $nestedReceiptEvidence = @()
    foreach ($nested in @($WorkerResult.remoteEvidenceReceipts)) {
        $nestedSha = Get-BytesSha256 $nested.bytes
        if ($nestedSha -ne $nested.sha256) {
            throw 'nested remote evidence digest drifted before persistence'
        }
        $nestedFileName = (
            "$Name-$($nested.name)-$nestedSha-remote-evidence.json")
        $nestedPath = Join-Path $fullEvidence $nestedFileName
        Write-ImmutableEvidence -Path $nestedPath -Bytes $nested.bytes
        $nestedShaBytes = $encoding.GetBytes(
            "$nestedSha  $nestedFileName`n")
        Write-ImmutableEvidence -Path "$nestedPath.sha256" `
            -Bytes $nestedShaBytes
        $nestedReceiptEvidence += [ordered]@{
            name = $nested.name
            path = $nestedPath
            sha256 = $nestedSha
            schema = $nested.schema
        }
    }
    return [ordered]@{
        path = $path
        sha256 = $sha
        remoteReceipt = $remoteReceiptEvidence
        nestedReceipts = $nestedReceiptEvidence
    }
}

function New-StageArtifactSnapshot {
    param(
        [Parameter(Mandatory = $true)]$Context,
        [Parameter(Mandatory = $true)][string]$Root
    )
    $backendPath = Join-Path $Root 'backend/fbsir-admin.jar'
    $migrationPath = Join-Path $Root 'sql/public_init_043.sql'
    $runnerPath = Join-Path $Root 'release-runner.ps1'
    $workerPath = Join-Path $Root 'release-worker.py'
    $buildReceiptPath = Join-Path $Root 'evidence/build-receipt.json'
    $planReceiptPath = Join-Path $Root 'evidence/release-plan.json'
    $frontendArchive = Join-Path $Root 'frontend.tar'
    $frontendVerification = Join-Path $Root 'frontend-verification'
    foreach ($directory in @(
            (Split-Path -Parent $backendPath),
            (Split-Path -Parent $migrationPath),
            (Split-Path -Parent $buildReceiptPath),
            $frontendVerification)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }

    $backendSource = Join-Path $RepoRoot (
        [string]$Context.build.receipt.backend.relativePath)
    [IO.File]::Copy($backendSource, $backendPath, $false)
    [IO.File]::WriteAllBytes(
        $migrationPath,
        (Get-CommittedBlobBytes (
            'sql/update_20260723_independent_board_attribution_v1.sql')))
    [IO.File]::WriteAllBytes(
        $runnerPath,
        (Get-CommittedBlobBytes (
            'scripts/deploy-independent-board-default-off.ps1')))
    [IO.File]::WriteAllBytes(
        $workerPath,
        (Get-CommittedBlobBytes (
            'scripts/u3w-default-off-release-remote.py')))
    [IO.File]::WriteAllBytes(
        $buildReceiptPath,
        $Context.build.bytes)
    [IO.File]::WriteAllBytes(
        $planReceiptPath,
        $Context.plan.bytes)

    $frontendSource = Join-Path $RepoRoot (
        [string]$Context.build.receipt.frontend.relativePath)
    & tar.exe -C $frontendSource -cf $frontendArchive .
    if ($LASTEXITCODE -ne 0) {
        throw 'frontend release snapshot creation failed'
    }
    $lockedStreams = [Collections.Generic.List[IO.FileStream]]::new()
    try {
        foreach ($path in @(
                $backendPath,
                $migrationPath,
                $runnerPath,
                $workerPath,
                $buildReceiptPath,
                $planReceiptPath,
                $frontendArchive)) {
            $lockedStreams.Add([IO.File]::Open(
                    $path,
                    [IO.FileMode]::Open,
                    [IO.FileAccess]::Read,
                    [IO.FileShare]::Read))
        }
        & tar.exe -C $frontendVerification -xf $frontendArchive
        if ($LASTEXITCODE -ne 0) {
            throw 'frontend release snapshot verification extraction failed'
        }
        $frontendReparsePoint = Get-ChildItem `
            -LiteralPath $frontendVerification -Force -Recurse |
            Where-Object {
                ($_.Attributes -band
                    [IO.FileAttributes]::ReparsePoint) -ne 0
            } | Select-Object -First 1
        if ($null -ne $frontendReparsePoint) {
            throw 'frontend release snapshot contains a reparse point'
        }
        $frontendFacts = Get-TreeManifest $frontendVerification
        if (
            (Get-Sha256 $backendPath) -cne
                $Context.build.receipt.backend.sha256 -or
            (Get-Item -LiteralPath $backendPath).Length -ne
                [long]$Context.build.receipt.backend.sizeBytes -or
            (Get-Sha256 $migrationPath) -cne
                $Context.build.receipt.migrationSha256 -or
            (Get-Sha256 $runnerPath) -cne
                $Context.plan.receipt.runnerSha256 -or
            (Get-Sha256 $workerPath) -cne
                $Context.plan.receipt.workerSha256 -or
            (Get-Sha256 $buildReceiptPath) -cne $Context.build.sha256 -or
            (Get-Sha256 $planReceiptPath) -cne $Context.plan.sha256 -or
            $frontendFacts.sha256 -cne
                $Context.build.receipt.frontend.treeSha256 -or
            $frontendFacts.fileCount -ne
                [int]$Context.build.receipt.frontend.fileCount -or
            $frontendFacts.totalBytes -ne
                [long]$Context.build.receipt.frontend.totalBytes
        ) {
            throw 'stage artifact snapshot drifted from the anchored Build or Plan'
        }
        return [ordered]@{
            backendPath = $backendPath
            migrationPath = $migrationPath
            runnerPath = $runnerPath
            workerPath = $workerPath
            buildReceiptPath = $buildReceiptPath
            planReceiptPath = $planReceiptPath
            frontendArchive = $frontendArchive
            locks = @($lockedStreams)
        }
    }
    catch {
        foreach ($stream in $lockedStreams) {
            $stream.Dispose()
        }
        throw
    }
}

function Copy-ReleaseArtifact {
    param(
        [Parameter(Mandatory = $true)][string]$LocalPath,
        [Parameter(Mandatory = $true)][string]$RemotePath
    )
    $arguments = @()
    $arguments += Get-SshOptions
    $arguments += @(
        (Resolve-Path -LiteralPath $LocalPath).Path,
        "$SshTarget`:$RemotePath"
    )
    & scp.exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "release artifact upload failed: $RemotePath"
    }
}

function Invoke-ReadinessGate {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet(
            'PREPARED_FOR_STAGE',
            'STAGED_FOR_SWITCH',
            'DEPLOYED_DEFAULT_OFF')]
        [string]$RequiredStage,
        [string]$ExpectedRemoteReceiptSha256,
        [Parameter(Mandatory = $true)][string]$OutputLabel,
        [switch]$AllowAlreadyStaged
    )
    $verifier = Join-Path $PSScriptRoot (
        'verify-independent-board-production-readiness.ps1')
    $outputPath = Join-Path $RepoRoot (
        "work\production-readiness\$ReleaseId-$OutputLabel.json")
    $arguments = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', $verifier,
        '-SshKeyPath', $SshKeyPath,
        '-KnownHostsPath', $KnownHostsPath,
        '-ExpectedBackupReceiptSha256', $ExpectedBackupReceiptSha256,
        '-ExpectedLegacyBaselineReceiptDigest',
            $ExpectedLegacyBaselineReceiptDigest,
        '-ExpectedConfigurationReceiptSha256',
            $ExpectedConfigurationReceiptSha256,
        '-ExpectedReleasePlanReceiptSha256',
            $ExpectedReleasePlanReceiptSha256,
        '-ExpectedCommit', $ExpectedCommit,
        '-OutputPath', $outputPath,
        '-RequiredStage', $RequiredStage,
        '-RequireReady'
    )
    if ($ExpectedRemoteReceiptSha256) {
        if ($ExpectedRemoteReceiptSha256 -notmatch '^[0-9a-f]{64}$') {
            throw "$RequiredStage readiness receipt digest is invalid"
        }
        $arguments += @(
            '-ExpectedDeploymentReceiptSha256',
            $ExpectedRemoteReceiptSha256
        )
    }
    $output = @(& powershell.exe @arguments)
    if ($LASTEXITCODE -ne 0 -or $output.Count -eq 0) {
        throw "$RequiredStage readiness gate failed"
    }
    $result = ($output -join "`n") | ConvertFrom-Json
    $statusAccepted = (
        $result.status -ceq $RequiredStage -or
        (
            $AllowAlreadyStaged -and
            $RequiredStage -ceq 'PREPARED_FOR_STAGE' -and
            $result.status -ceq 'STAGED_FOR_SWITCH'
        )
    )
    if (-not $statusAccepted) {
        throw "$RequiredStage readiness status is invalid"
    }
    return $result
}

function Invoke-PreparedGate {
    return Invoke-ReadinessGate `
        -RequiredStage PREPARED_FOR_STAGE `
        -OutputLabel 'pre-stage' `
        -AllowAlreadyStaged
}

function Invoke-Stage {
    $null = Assert-StrictHead
    Assert-ExternalEvidenceWritable
    $context = Assert-ReceiptAnchorsForMutatingMode
    $approvalAnchor = Save-ExternalApprovalAnchor `
        -ApprovalMode Stage -Approval $context.approval
    $temporary = Join-Path $env:TEMP (
        "u3w-release-$ReleaseId-$PID")
    if (Test-Path -LiteralPath $temporary) {
        throw "temporary release directory already exists: $temporary"
    }
    New-Item -ItemType Directory -Path $temporary | Out-Null
    try {
        $snapshot = New-StageArtifactSnapshot `
            -Context $context -Root $temporary
        $prepared = Invoke-PreparedGate
        $prepare = Invoke-CommittedRemoteWorker `
            -WorkerMode PrepareStage -Context $context
        $prepareAnchor = Save-ExternalReleaseAnchor `
            -Name 'stage-prepare' -WorkerResult $prepare
        if ($prepare.result.state -eq 'READY_FOR_UPLOAD') {
            $incoming = "/opt/fbsir/admin/releases/.incoming-$ReleaseId"
            Copy-ReleaseArtifact `
                -LocalPath $snapshot.backendPath `
                -RemotePath "$incoming/backend/fbsir-admin.jar"
            Copy-ReleaseArtifact `
                -LocalPath $snapshot.migrationPath `
                -RemotePath "$incoming/sql/public_init_043.sql"
            Copy-ReleaseArtifact -LocalPath $snapshot.runnerPath `
                -RemotePath "$incoming/release-runner.ps1"
            Copy-ReleaseArtifact -LocalPath $snapshot.workerPath `
                -RemotePath "$incoming/release-worker.py"
            Copy-ReleaseArtifact -LocalPath $snapshot.buildReceiptPath `
                -RemotePath "$incoming/evidence/build-receipt.json"
            Copy-ReleaseArtifact -LocalPath $snapshot.planReceiptPath `
                -RemotePath "$incoming/evidence/release-plan.json"
            Copy-ReleaseArtifact -LocalPath $snapshot.frontendArchive `
                -RemotePath "$incoming/evidence/frontend.tar"
        }
        elseif ($prepare.result.state -ne 'ALREADY_STAGED') {
            throw 'PrepareStage returned an invalid state'
        }
    }
    finally {
        if ($null -ne $snapshot) {
            foreach ($stream in @($snapshot.locks)) {
                $stream.Dispose()
            }
        }
        if (Test-Path -LiteralPath $temporary) {
            $resolvedTemporary = (
                Resolve-Path -LiteralPath $temporary).Path
            $resolvedTempRoot = (
                Resolve-Path -LiteralPath $env:TEMP).Path.TrimEnd('\') + '\'
            if (($resolvedTemporary.TrimEnd('\') + '\').StartsWith(
                    $resolvedTempRoot,
                    [StringComparison]::OrdinalIgnoreCase)) {
                Remove-Item -LiteralPath $resolvedTemporary `
                    -Recurse -Force
            }
        }
    }
    $finalize = Invoke-CommittedRemoteWorker `
        -WorkerMode FinalizeStage -Context $context
    $finalizeAnchor = Save-ExternalReleaseAnchor `
        -Name 'stage-finalize' -WorkerResult $finalize
    $staged = Invoke-ReadinessGate `
        -RequiredStage STAGED_FOR_SWITCH `
        -ExpectedRemoteReceiptSha256 $finalize.result.receiptSha256 `
        -OutputLabel 'post-stage'
    $null = Assert-StrictHead
    return [ordered]@{
        schema = 'fbsir.u3wDefaultOffReleaseRunnerResult.v1'
        mode = 'Stage'
        state = $finalize.result.state
        releaseId = $ReleaseId
        sourceCommit = $ExpectedCommit
        preparedStatus = $prepared.status
        stagedStatus = $staged.status
        stageReceiptPath = $finalize.result.receiptPath
        stageReceiptSha256 = $finalize.result.receiptSha256
        approvalEvidence = $approvalAnchor
        prepareEvidence = $prepareAnchor
        finalizeEvidence = $finalizeAnchor
        productionFilesystemChanged =
            $finalize.result.productionFilesystemChanged
        productionDatabaseChanged = $false
        productionServiceChanged = $false
        officialExpertsPackageChanged = $false
    }
}

function Invoke-Apply {
    $null = Assert-StrictHead
    Assert-ExternalEvidenceWritable
    $context = Assert-ReceiptAnchorsForMutatingMode
    $approvalAnchor = Save-ExternalApprovalAnchor `
        -ApprovalMode Apply -Approval $context.approval
    $worker = Invoke-CommittedRemoteWorker `
        -WorkerMode Apply -Context $context
    $anchor = Save-ExternalReleaseAnchor `
        -Name 'apply' -WorkerResult $worker
    $deployed = Invoke-ReadinessGate `
        -RequiredStage DEPLOYED_DEFAULT_OFF `
        -ExpectedRemoteReceiptSha256 $worker.result.receiptSha256 `
        -OutputLabel 'post-apply'
    $null = Assert-StrictHead
    return [ordered]@{
        schema = 'fbsir.u3wDefaultOffReleaseRunnerResult.v1'
        mode = 'Apply'
        state = $worker.result.state
        releaseId = $ReleaseId
        sourceCommit = $ExpectedCommit
        deployedStatus = $deployed.status
        deploymentReceiptPath = $worker.result.receiptPath
        deploymentReceiptSha256 = $worker.result.receiptSha256
        approvalEvidence = $approvalAnchor
        externalEvidence = $anchor
        productionFilesystemChanged =
            $worker.result.productionFilesystemChanged
        productionDatabaseChanged =
            $worker.result.productionDatabaseChanged
        productionServiceChanged =
            $worker.result.productionServiceChanged
        officialExpertsPackageChanged = $false
    }
}

function Invoke-Rollback {
    $null = Assert-RecoveryRunnerExact
    Assert-ExternalEvidenceWritable
    $context = Assert-ReceiptAnchorsForMutatingMode
    $approvalAnchor = Save-ExternalApprovalAnchor `
        -ApprovalMode Rollback -Approval $context.approval
    $worker = Invoke-CommittedRemoteWorker `
        -WorkerMode Rollback -Context $context
    $null = Assert-RecoveryRunnerExact
    $anchor = Save-ExternalReleaseAnchor `
        -Name 'rollback' -WorkerResult $worker
    return [ordered]@{
        schema = 'fbsir.u3wDefaultOffReleaseRunnerResult.v1'
        mode = 'Rollback'
        state = $worker.result.state
        releaseId = $ReleaseId
        sourceCommit = $ExpectedCommit
        rollbackReceiptPath = $worker.result.receiptPath
        rollbackReceiptSha256 = $worker.result.receiptSha256
        approvalEvidence = $approvalAnchor
        databaseRollbackSafety =
            'RETAIN_ADDITIVE_043_DORMANT_NO_DOWN'
        externalEvidence = $anchor
        productionFilesystemChanged =
            $worker.result.productionFilesystemChanged
        productionDatabaseChanged =
            $worker.result.productionDatabaseChanged
        productionServiceChanged =
            $worker.result.productionServiceChanged
        officialExpertsPackageChanged = $false
    }
}

function Invoke-Verify {
    $null = Assert-RecoveryRunnerExact
    Assert-ExternalEvidenceWritable
    $context = Assert-ReceiptAnchorsForMutatingMode
    $approvalAnchor = Save-ExternalApprovalAnchor `
        -ApprovalMode Verify -Approval $context.approval
    $worker = Invoke-CommittedRemoteWorker `
        -WorkerMode Verify -Context $context
    $anchor = Save-ExternalReleaseAnchor `
        -Name 'verify' -WorkerResult $worker
    $null = Assert-RecoveryRunnerExact
    return [ordered]@{
        schema = 'fbsir.u3wDefaultOffReleaseRunnerResult.v1'
        mode = 'Verify'
        state = $worker.result.state
        releaseId = $ReleaseId
        sourceCommit = $ExpectedCommit
        verified = $worker.result.verified
        deployedStatus = $worker.result.state
        verificationScope =
            'POST_DEPLOYMENT_ONLY_NO_PLAN_TTL_OR_GITHUB_DEPENDENCY'
        deploymentReceiptSha256 =
            $worker.result.deploymentReceiptSha256
        approvalEvidence = $approvalAnchor
        externalEvidence = $anchor
        productionFilesystemChanged = $false
        productionDatabaseChanged = $false
        productionServiceChanged = $false
        officialExpertsPackageChanged = $false
    }
}

$result = switch ($Mode) {
    'Build' { Invoke-Build; break }
    'Plan' { Invoke-Plan; break }
    'Stage' { Invoke-Stage; break }
    'Apply' { Invoke-Apply; break }
    'Rollback' { Invoke-Rollback; break }
    'Verify' { Invoke-Verify; break }
}

$result | ConvertTo-Json -Depth 20
