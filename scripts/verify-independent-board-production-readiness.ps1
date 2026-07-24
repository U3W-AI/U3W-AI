[CmdletBinding()]
param(
    [string]$SshKeyPath = $(if ($env:U3W_SSH_KEY_PATH) { $env:U3W_SSH_KEY_PATH } else { Join-Path $env:USERPROFILE '.ssh\id_ed25519_api2' }),
    [string]$KnownHostsPath = $(Join-Path $env:TEMP 'u3w-production-readiness-known-hosts'),
    [string]$ExpectedBackupReceiptSha256,
    [string]$ExpectedBackupPlanReceiptSha256,
    [string]$ExpectedDeploymentReceiptSha256,
    [string]$ExpectedLegacyBaselineReceiptDigest,
    [string]$ExpectedAdminRootDependencyAdoptionReceiptSha256,
    [string]$ExpectedConfigurationReceiptSha256,
    [string]$ExpectedReleasePlanReceiptSha256,
    [string]$ExpectedCommit,
    [string]$OutputPath,
    [ValidateSet('PREPARED_FOR_STAGE', 'STAGED_FOR_SWITCH', 'DEPLOYED_DEFAULT_OFF')]
    [string]$RequiredStage,
    [switch]$RequireReady
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$SshTarget = 'root@api2.u3w.com'
$ExpectedPublicKeyFingerprint = 'SHA256:oCIzbO9W94qDBeS9MtetLCW0CjYMkyZqT6QkgcLkXk0'
$ExpectedRemoteHostKeyFingerprint = 'SHA256:GkS/HJpLm48N+KaMV/WEVSBHnI8PKJsU+6ycAsn+mBA'
$ServiceUnit = 'fbsir-admin.service'
$DatabaseBackupReceiptPath = '/opt/fbsir/admin/backups/latest/receipt.json'
$DeploymentReceiptPath = '/opt/fbsir/admin/releases/latest-receipt.json'
$LegacyBaselineReceiptPath =
    '/opt/fbsir/admin/baselines/latest/adoption-receipt.json'
$AdminRootDependencyAdoptionReceiptPath =
    '/opt/fbsir/admin/dependencies/latest/adoption-receipt.json'
$ConfigurationReceiptPath =
    '/opt/fbsir/admin/configuration/latest/configuration-receipt.json'
$Api2EventKeyPath =
    '/etc/u3w/secrets/independent-board-attribution-event-key'
if (-not $OutputPath) {
    $OutputPath = Join-Path $RepoRoot (
        'work\production-readiness\w1a-production-readiness-latest.json')
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

function Test-JsonStructuralEquality {
    param(
        [Parameter()][AllowNull()][object]$Left,
        [Parameter()][AllowNull()][object]$Right
    )

    if ($null -eq $Left -or $null -eq $Right) {
        return $null -eq $Left -and $null -eq $Right
    }

    $leftIsDictionary =
        $Left -is [System.Collections.IDictionary]
    $rightIsDictionary =
        $Right -is [System.Collections.IDictionary]
    $leftIsObject =
        $leftIsDictionary -or
        $Left.psobject.BaseObject -is [System.Management.Automation.PSCustomObject]
    $rightIsObject =
        $rightIsDictionary -or
        $Right.psobject.BaseObject -is [System.Management.Automation.PSCustomObject]
    if ($leftIsObject -or $rightIsObject) {
        if (-not ($leftIsObject -and $rightIsObject)) {
            return $false
        }
        [string[]]$leftNames = if ($leftIsDictionary) {
            @($Left.Keys | ForEach-Object { [string]$_ })
        } else {
            @($Left.psobject.Properties.Name)
        }
        [string[]]$rightNames = if ($rightIsDictionary) {
            @($Right.Keys | ForEach-Object { [string]$_ })
        } else {
            @($Right.psobject.Properties.Name)
        }
        [Array]::Sort($leftNames, [StringComparer]::Ordinal)
        [Array]::Sort($rightNames, [StringComparer]::Ordinal)
        if ($leftNames.Count -ne $rightNames.Count) {
            return $false
        }
        for ($index = 0; $index -lt $leftNames.Count; $index++) {
            $name = $leftNames[$index]
            if ($name -cne $rightNames[$index]) {
                return $false
            }
            $leftValue = if ($leftIsDictionary) {
                $Left[$name]
            } else {
                $Left.psobject.Properties[$name].Value
            }
            $rightValue = if ($rightIsDictionary) {
                $Right[$name]
            } else {
                $Right.psobject.Properties[$name].Value
            }
            if (-not (Test-JsonStructuralEquality `
                    -Left $leftValue -Right $rightValue)) {
                return $false
            }
        }
        return $true
    }

    $leftIsArray =
        $Left -is [System.Collections.IEnumerable] -and
        $Left -isnot [string]
    $rightIsArray =
        $Right -is [System.Collections.IEnumerable] -and
        $Right -isnot [string]
    if ($leftIsArray -or $rightIsArray) {
        if (-not ($leftIsArray -and $rightIsArray)) {
            return $false
        }
        $leftItems = @($Left)
        $rightItems = @($Right)
        if ($leftItems.Count -ne $rightItems.Count) {
            return $false
        }
        for ($index = 0; $index -lt $leftItems.Count; $index++) {
            if (-not (Test-JsonStructuralEquality `
                    -Left ($leftItems[$index]) `
                    -Right ($rightItems[$index]))) {
                return $false
            }
        }
        return $true
    }

    return (
        ($Left | ConvertTo-Json -Compress) -ceq
        ($Right | ConvertTo-Json -Compress))
}

function Get-CommittedFileSha256 {
    param([Parameter(Mandatory = $true)][string]$GitPath)
    $commit = if ($ExpectedCommit) {
        $ExpectedCommit.ToLowerInvariant()
    } else {
        (& git -C $RepoRoot rev-parse HEAD).Trim().ToLowerInvariant()
    }
    $objectId = (& git -C $RepoRoot rev-parse (
        "$commit`:$GitPath")).Trim()
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
        return Get-BytesSha256 $memory.ToArray()
    }
    finally {
        $memory.Dispose()
        $process.Dispose()
    }
}

function Resolve-NodeExecutable {
    $candidates = @(
        $env:U3W_NODE_EXE,
        $env:CODEX_NODE_EXE,
        (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe')
    ) | Where-Object { $_ -and $_.Trim().Length -gt 0 }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    $command = Get-Command node -ErrorAction SilentlyContinue
    if ($command -and $command.Source) {
        return $command.Source
    }
    throw 'node executable not found'
}

function Assert-SafeRemoteParameters {
    foreach ($anchor in @(
            $ExpectedBackupReceiptSha256,
            $ExpectedBackupPlanReceiptSha256,
            $ExpectedDeploymentReceiptSha256,
            $ExpectedLegacyBaselineReceiptDigest,
            $ExpectedAdminRootDependencyAdoptionReceiptSha256,
            $ExpectedConfigurationReceiptSha256,
            $ExpectedReleasePlanReceiptSha256)) {
        if ($anchor -and $anchor -notmatch '^[0-9a-fA-F]{64}$') {
            throw 'receipt anchors must be 64-hex SHA-256 values'
        }
    }
    $effectiveStage = if ($RequiredStage) {
        $RequiredStage
    } elseif ($RequireReady) {
        'PREPARED_FOR_STAGE'
    } else {
        $null
    }
    if ($effectiveStage -and -not $ExpectedBackupReceiptSha256) {
        throw 'PREPARED_FOR_STAGE requires the backup out-of-band SHA-256 anchor'
    }
    if ($effectiveStage -and -not $ExpectedBackupPlanReceiptSha256) {
        throw 'PREPARED_FOR_STAGE requires the approved backup Plan SHA-256 anchor'
    }
    if ($effectiveStage -and -not $ExpectedLegacyBaselineReceiptDigest) {
        throw 'PREPARED_FOR_STAGE requires the legacy baseline out-of-band SHA-256 anchor'
    }
    if ($effectiveStage -and -not $ExpectedConfigurationReceiptSha256) {
        throw 'PREPARED_FOR_STAGE requires the configuration out-of-band SHA-256 anchor'
    }
    if ($effectiveStage -and
        -not $ExpectedAdminRootDependencyAdoptionReceiptSha256) {
        throw (
            'PREPARED_FOR_STAGE requires the admin-root dependency ' +
            'adoption out-of-band SHA-256 anchor')
    }
    if ($effectiveStage -and -not $ExpectedReleasePlanReceiptSha256) {
        throw 'PREPARED_FOR_STAGE requires the release-plan out-of-band SHA-256 anchor'
    }
    if ($effectiveStage -and -not $ExpectedCommit) {
        throw "$effectiveStage requires an explicit 40-hex ExpectedCommit"
    }
    if ($effectiveStage -in @('STAGED_FOR_SWITCH', 'DEPLOYED_DEFAULT_OFF') -and
        -not $ExpectedDeploymentReceiptSha256) {
        throw "$effectiveStage requires the deployment out-of-band SHA-256 anchor"
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
    if ($LASTEXITCODE -ne 0 -or $fingerprint -notlike "*$ExpectedPublicKeyFingerprint*") {
        throw "SSH public key fingerprint mismatch; expected $ExpectedPublicKeyFingerprint"
    }
}

function Ensure-KnownHosts {
    $knownHostsDirectory = Split-Path -Parent $KnownHostsPath
    if ($knownHostsDirectory -and -not (Test-Path -LiteralPath $knownHostsDirectory)) {
        New-Item -ItemType Directory -Path $knownHostsDirectory | Out-Null
    }
    $matchesFingerprint = {
        param([string]$Path, [string]$HostName)
        $matchingLines = & ssh-keygen.exe -F $HostName -f $Path 2>$null |
            Where-Object { $_ -and -not $_.StartsWith('#') }
        if ($LASTEXITCODE -ne 0 -or @($matchingLines).Count -eq 0) {
            return $false
        }
        $fingerprints = $matchingLines |
            & ssh-keygen.exe -lf - -E sha256 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "host fingerprint inspection failed: $Path"
        }
        return @($fingerprints).Count -gt 0 -and
            @($fingerprints | Where-Object {
                    $_ -notmatch [regex]::Escape($ExpectedRemoteHostKeyFingerprint)
                }).Count -eq 0
    }
    $hostName = ($SshTarget -split '@', 2)[1]
    if (Test-Path -LiteralPath $KnownHostsPath -PathType Leaf) {
        if (-not (& $matchesFingerprint $KnownHostsPath $hostName)) {
            throw "remote host key fingerprint mismatch; expected $ExpectedRemoteHostKeyFingerprint"
        }
        return
    }
    $scanPath = "$KnownHostsPath.scan-$PID"
    try {
        & ssh-keyscan.exe -T 10 -t ed25519 $hostName |
            Set-Content -LiteralPath $scanPath -NoNewline -Encoding ascii
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $scanPath -PathType Leaf)) {
            throw "ssh-keyscan failed for $hostName"
        }
        if (-not (& $matchesFingerprint $scanPath $hostName)) {
            throw "remote host key fingerprint mismatch; expected $ExpectedRemoteHostKeyFingerprint"
        }
        Move-Item -LiteralPath $scanPath -Destination $KnownHostsPath -Force
    }
    finally {
        Remove-Item -LiteralPath $scanPath -Force -ErrorAction SilentlyContinue
    }
}

function Get-LocalTreeFacts {
    param([Parameter(Mandatory = $true)][string]$Root)
    $resolved = (Resolve-Path -LiteralPath $Root).Path
    $rootUri = [Uri]::new($resolved.TrimEnd('\') + '\')
    $files = @(
        Get-ChildItem -LiteralPath $resolved -Recurse -File |
            ForEach-Object {
                [pscustomobject]@{
                    file = $_
                    relative = [Uri]::UnescapeDataString(
                        $rootUri.MakeRelativeUri(
                            [Uri]::new($_.FullName)).ToString()
                    ).Replace('\', '/')
                }
            }
    )
    if ($files.Count -eq 0) {
        throw "release tree is empty: $Root"
    }
    [Array]::Sort(
        $files,
        [Comparison[object]]{
            param($left, $right)
            return [StringComparer]::Ordinal.Compare(
                [string]$left.relative,
                [string]$right.relative)
        })
    $lines = [Collections.Generic.List[string]]::new()
    [long]$bytes = 0
    foreach ($entry in $files) {
        $file = $entry.file
        if (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "release tree contains a reparse point: $($file.FullName)"
        }
        $relative = [string]$entry.relative
        if ($relative.StartsWith('../') -or $relative.Contains("`n")) {
            throw 'release tree contains an unsafe relative path'
        }
        $sha = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).
            Hash.ToLowerInvariant()
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
        sha256 = -join ($digest | ForEach-Object { $_.ToString('x2') })
        fileCount = $files.Count
        totalBytes = $bytes
    }
}

function Test-WorkerPyMySqlOrchestrationProof {
    param([Parameter(Mandatory = $true)]$Result)
    $proof = $Result.workerPyMySqlOrchestration
    if (
        $null -eq $proof -or
        $proof.status -cne 'PASS' -or
        [string]::IsNullOrWhiteSpace([string]$proof.pymysqlVersion) -or
        $proof.pymysqlDistributionVersion -cne '1.1.2'
    ) {
        return $false
    }
    $cases = @(
        @{
            Value = $proof.firstApply
            ChangedThisRun = $true
            ChangedSinceStage = $true
        },
        @{
            Value = $proof.exactAppliedRecovery
            ChangedThisRun = $false
            ChangedSinceStage = $true
        },
        @{
            Value = $proof.runningRecovery
            ChangedThisRun = $true
            ChangedSinceStage = $true
        }
    )
    foreach ($case in $cases) {
        $value = $case.Value
        $facts = $value.facts
        if (
            $value.databaseChangedThisRun -isnot [bool] -or
            $value.databaseChangedThisRun -ne $case.ChangedThisRun -or
            $value.databaseChangedSinceStage -isnot [bool] -or
            $value.databaseChangedSinceStage -ne
                $case.ChangedSinceStage -or
            [int]$value.lock.blockedWhileLeaseOpen -ne 0 -or
            [int]$value.lock.acquiredAfterClose -ne 1 -or
            [int]$facts.publicReceiptCount -ne 1 -or
            [int]$facts.internalReceiptCount -ne 1 -or
            [int]$facts.tableCount -ne 2 -or
            [int]$facts.triggerCount -ne 2 -or
            [int]$facts.permissionCount -ne 1 -or
            [int]$facts.eventCount -ne 0 -or
            [int]$facts.journeyCount -ne 0 -or
            $facts.schemaFingerprintSha256 -cne
                'fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d'
        ) {
            return $false
        }
    }
    return $true
}

function Get-GitState {
    $head = (& git -C $RepoRoot rev-parse HEAD).Trim().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-f]{40}$') {
        throw 'unable to resolve local git HEAD'
    }
    if (-not $ExpectedCommit) {
        $script:ExpectedCommit = $head
    }
    $expected = $ExpectedCommit.Trim().ToLowerInvariant()
    if ($expected -notmatch '^[0-9a-f]{40}$') {
        throw 'ExpectedCommit must be a 40-hex commit'
    }
    $branch = (& git -C $RepoRoot branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0 -or
        [string]::IsNullOrWhiteSpace($branch)) {
        throw 'production readiness requires a named branch'
    }
    $upstreamCommit = (
        & git -C $RepoRoot rev-parse '@{upstream}'
    ).Trim().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0 -or
        $upstreamCommit -notmatch '^[0-9a-f]{40}$') {
        throw 'production readiness requires an exact upstream'
    }
    $remoteRows = @(
        & git -C $RepoRoot ls-remote --exit-code origin (
            "refs/heads/$branch")
    )
    if ($LASTEXITCODE -ne 0 -or $remoteRows.Count -ne 1) {
        throw 'production readiness cannot resolve the origin branch'
    }
    $originHead = (
        $remoteRows[0] -split '\s+', 2
    )[0].ToLowerInvariant()
    $upstreamOriginAligned = (
        $head -ceq $upstreamCommit -and
        $head -ceq $originHead
    )
    $status = & git -C $RepoRoot status --porcelain=v1
    if ($LASTEXITCODE -ne 0) {
        throw 'git status failed'
    }
    $migrationPath = Join-Path $RepoRoot 'sql\update_20260723_independent_board_attribution_v1.sql'
    $migrationSha256 = (
        Get-FileHash -LiteralPath $migrationPath -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $compatibilityReceiptPaths = @(
        'reports\independent-board\w1a-attribution-v1-dual-mysql-latest.json',
        'reports\independent-board\w1a-attribution-v1-mysql-8.0.45-latest.json'
    )
    $verifiedVersions = [System.Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal)
    $compatibilityReceipts = [System.Collections.Generic.List[object]]::new()
    foreach ($relativePath in $compatibilityReceiptPaths) {
        $receiptPath = Join-Path $RepoRoot $relativePath
        if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) {
            continue
        }
        $receipt = Get-Content -LiteralPath $receiptPath -Raw -Encoding UTF8 |
            ConvertFrom-Json
        if ($receipt.schema -cne 'fbsir.independent-board.attribution-v1-dual-mysql-it/v1' -or
            $receipt.status -cne 'PASS' -or
            $receipt.migration -cne 'public_init_043' -or
            $receipt.migrationSha256 -cne $migrationSha256 -or
            $receipt.officialIdentity.productId -cne 'fbsir-eight-seat-board' -or
            $receipt.officialIdentity.listedManifestVersion -cne '26.7.21') {
            throw "MySQL compatibility receipt is invalid: $relativePath"
        }
        foreach ($result in @($receipt.results)) {
            if ($result.version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$' -or
                $result.exactShape -cne "$($result.version)|2|2|1|1|1" -or
                $result.migrationRerun -cne 'PASS' -or
                $result.append -cne 'PASS' -or
                $result.aggregate -cne '1|1|1|0' -or
                $result.updateRejected -cne 'PASS' -or
                $result.deleteRejected -cne 'PASS' -or
                $result.extraTriggerFingerprintRejected -cne 'PASS' -or
                $result.cascadingFkFingerprintRejected -cne 'PASS' -or
                $result.permissionSemanticFingerprintRejected -cne 'PASS' -or
                -not (Test-WorkerPyMySqlOrchestrationProof $result) -or
                [int]$result.authoritativeProductCredit -ne 0 -or
                $result.schemaFingerprintSha256 -cne 'fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d') {
                throw "MySQL compatibility result is invalid: $relativePath"
            }
            $null = $verifiedVersions.Add([string]$result.version)
        }
        $compatibilityReceipts.Add([ordered]@{
                path = $relativePath.Replace('\', '/')
                sha256 = (
                    Get-FileHash -LiteralPath $receiptPath -Algorithm SHA256
                ).Hash.ToLowerInvariant()
            })
    }
    $releasePlanRelativePath = if ($ExpectedReleasePlanReceiptSha256) {
        Join-Path 'work\release-plans\by-sha256' (
            "$($ExpectedReleasePlanReceiptSha256.ToLowerInvariant()).json")
    } else {
        'work\release-plans\w1a-default-off-release-plan-latest.json'
    }
    $releasePlanPath = Join-Path $RepoRoot $releasePlanRelativePath
    $releasePlanVerified = $false
    $releasePlanSourceCommit = $null
    $releaseRunnerContractVersion = $null
    $releasePlanReceiptSha256 = $null
    $releasePlanTarget = $null
    if (Test-Path -LiteralPath $releasePlanPath -PathType Leaf) {
        $releasePlanItem = Get-Item -LiteralPath $releasePlanPath -Force
        if (
            $ExpectedReleasePlanReceiptSha256 -and
            (
                ($releasePlanItem.Attributes -band
                    [IO.FileAttributes]::ReparsePoint) -ne 0 -or
                -not $releasePlanItem.IsReadOnly
            )
        ) {
            throw "content-addressed release plan receipt custody is invalid: $releasePlanRelativePath"
        }
        $releasePlanBytes = [IO.File]::ReadAllBytes($releasePlanPath)
        $releasePlanReceiptSha256 =
            Get-BytesSha256 $releasePlanBytes
        $releasePlan = [Text.UTF8Encoding]::new(
            $false, $true).GetString($releasePlanBytes) | ConvertFrom-Json
        $runnerSha256 = Get-CommittedFileSha256 (
            'scripts/deploy-independent-board-default-off.ps1')
        $workerSha256 = Get-CommittedFileSha256 (
            'scripts/u3w-default-off-release-remote.py')
        $requiredModes = @($releasePlan.requiredModes)
        $buildReceiptPath = [string]$releasePlan.buildReceiptPath
        $buildReceiptValid = $false
        if ($buildReceiptPath -and
            (Test-Path -LiteralPath $buildReceiptPath -PathType Leaf)) {
            $buildReceiptBytes =
                [IO.File]::ReadAllBytes($buildReceiptPath)
            $buildReceiptSha256 =
                Get-BytesSha256 $buildReceiptBytes
            $buildReceipt = [Text.UTF8Encoding]::new(
                $false, $true).GetString(
                    $buildReceiptBytes) | ConvertFrom-Json
            $backendPath = Join-Path $RepoRoot (
                [string]$buildReceipt.backend.relativePath)
            $frontendPath = Join-Path $RepoRoot (
                [string]$buildReceipt.frontend.relativePath)
            $verificationLogPath = Join-Path $RepoRoot (
                [string]$buildReceipt.verificationLog.relativePath)
            $packageLogPath = Join-Path $RepoRoot (
                [string]$buildReceipt.packageLog.relativePath)
            $backendValid = (
                $buildReceipt.backend.relativePath -ceq
                    'FBSir-admin/target/fbsir-admin.jar' -and
                (Test-Path -LiteralPath $backendPath -PathType Leaf) -and
                (Get-FileHash -LiteralPath $backendPath -Algorithm SHA256).
                    Hash.ToLowerInvariant() -ceq
                    $buildReceipt.backend.sha256 -and
                (Get-Item -LiteralPath $backendPath).Length -eq
                    [long]$buildReceipt.backend.sizeBytes
            )
            $frontendValid = $false
            if (
                $buildReceipt.frontend.relativePath -ceq 'FBSir-ui/dist' -and
                (Test-Path -LiteralPath $frontendPath -PathType Container)
            ) {
                $frontendFacts = Get-LocalTreeFacts $frontendPath
                $frontendValid = (
                    $frontendFacts.sha256 -ceq
                        $buildReceipt.frontend.treeSha256 -and
                    $frontendFacts.fileCount -eq
                        [int]$buildReceipt.frontend.fileCount -and
                    $frontendFacts.totalBytes -eq
                        [long]$buildReceipt.frontend.totalBytes
                )
            }
            $logsValid = (
                $buildReceipt.verificationLog.exitCode -eq 0 -and
                $buildReceipt.packageLog.exitCode -eq 0 -and
                (Test-Path -LiteralPath $verificationLogPath -PathType Leaf) -and
                (Test-Path -LiteralPath $packageLogPath -PathType Leaf) -and
                (Get-FileHash -LiteralPath $verificationLogPath `
                    -Algorithm SHA256).Hash.ToLowerInvariant() -ceq
                    $buildReceipt.verificationLog.sha256 -and
                (Get-FileHash -LiteralPath $packageLogPath `
                    -Algorithm SHA256).Hash.ToLowerInvariant() -ceq
                    $buildReceipt.packageLog.sha256
            )
            $buildReceiptValid = (
                $buildReceiptSha256 -ceq $releasePlan.buildReceiptSha256 -and
                $buildReceipt.schema -ceq
                    'fbsir.u3wDefaultOffBuildReceipt.v1' -and
                $buildReceipt.status -ceq 'PASS' -and
                $buildReceipt.runnerSha256 -ceq $runnerSha256 -and
                $buildReceipt.sourceCommit -ceq $head -and
                $buildReceipt.releaseId -ceq $releasePlan.releaseId -and
                $buildReceipt.backend.sha256 -ceq
                    $releasePlan.backendBuildSha256 -and
                $buildReceipt.frontend.treeSha256 -ceq
                    $releasePlan.frontendTreeSha256 -and
                $buildReceipt.migrationSha256 -ceq
                    $releasePlan.migrationSha256 -and
                $backendValid -and
                $frontendValid -and
                $logsValid
            )
        }
        $generatedAt = [DateTimeOffset]::Parse(
            [string]$releasePlan.generatedAt)
        $expiresAt = [DateTimeOffset]::Parse(
            [string]$releasePlan.expiresAt)
        $now = [DateTimeOffset]::UtcNow
        $releasePlanTimeValid = (
            $generatedAt -le $now.AddMinutes(1) -and
            $expiresAt -gt $now -and
            $expiresAt -gt $generatedAt -and
            ($expiresAt - $generatedAt).TotalHours -le 24
        )
        $releasePlanVerified = (
            $releasePlan.schema -ceq 'fbsir.u3wDefaultOffReleasePlan.v2' -and
            $releasePlan.runnerContractVersion -ceq 'fbsir.u3wDefaultOffReleaseRunner.v2' -and
            $releasePlan.mode -ceq 'Plan' -and
            $releasePlan.productionChanged -eq $false -and
            $releasePlan.strictHeadClean -eq $true -and
            $releasePlan.sourceCommit -ceq $head -and
            $releasePlan.expectedSourceCommit -ceq $expected -and
            $releasePlan.runnerSha256 -ceq $runnerSha256 -and
            $releasePlan.workerSha256 -ceq $workerSha256 -and
            $releasePlan.collectorSha256 -match '^[0-9a-f]{64}$' -and
            $releasePlanTimeValid -and
            ($requiredModes -join ',') -ceq
                'Build,Plan,Stage,Apply,Rollback,Verify' -and
            $buildReceiptValid -and
            $ExpectedReleasePlanReceiptSha256 -and
            $releasePlanReceiptSha256 -ceq
                $ExpectedReleasePlanReceiptSha256.ToLowerInvariant()
        )
        if (-not $releasePlanVerified) {
            throw "default-off release plan receipt is invalid: $releasePlanRelativePath"
        }
        $releasePlanSourceCommit = [string]$releasePlan.sourceCommit
        $releaseRunnerContractVersion = [string]$releasePlan.runnerContractVersion
        $releasePlanTarget = $releasePlan.target
    }
    return [ordered]@{
        clean = [string]::IsNullOrWhiteSpace(($status -join "`n"))
        sourceCommit = $head
        expectedSourceCommit = $expected
        branch = $branch
        upstreamCommit = $upstreamCommit
        originHead = $originHead
        upstreamOriginAligned = $upstreamOriginAligned
        w1a043CompatibilityVersions = @($verifiedVersions | Sort-Object)
        w1a043CompatibilityReceipts = @($compatibilityReceipts)
        # Full 035-042 canonical-chain compatibility is intentionally distinct
        # from the standalone 043 compatibility receipts above.
        canonicalBaselineCompatibilityVersions = @('8.0.30', '8.4.8')
        releasePlanVerified = $releasePlanVerified
        releasePlanSourceCommit = $releasePlanSourceCommit
        releaseRunnerContractVersion = $releaseRunnerContractVersion
        releasePlanReceiptPath = if ($releasePlanVerified) {
            $releasePlanRelativePath.Replace('\', '/')
        } else { $null }
        releasePlanReceiptSha256 = $releasePlanReceiptSha256
        releasePlanTarget = $releasePlanTarget
        releasePlanTargetMatchedLive = $false
        preparationSourceCommit = $null
        preparationCommitAncestorOfSourceCommit = $false
        preparationSourceCommitsConsistent = $false
    }
}

function Invoke-RemoteSnapshot {
    Assert-SafeRemoteParameters
    Assert-PrivateKey
    Ensure-KnownHosts

    $remotePython = @'
import hashlib
import hmac
import base64
import fcntl
import json
import os
import pathlib
import re
import stat
import subprocess
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timedelta, timezone

SERVICE_UNIT = __SERVICE_UNIT__
TARGET_HOST = __TARGET_HOST__
BACKUP_RECEIPT_PATH = __BACKUP_RECEIPT_PATH__
DEPLOYMENT_RECEIPT_PATH = __DEPLOYMENT_RECEIPT_PATH__
LEGACY_BASELINE_RECEIPT_PATH = __LEGACY_BASELINE_RECEIPT_PATH__
ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_PATH = (
    __ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_PATH__
)
CONFIGURATION_RECEIPT_PATH = __CONFIGURATION_RECEIPT_PATH__
API2_EVENT_KEY_PATH = __API2_EVENT_KEY_PATH__
EXPECTED_BACKUP_RECEIPT_SHA256 = __EXPECTED_BACKUP_RECEIPT_SHA256__
EXPECTED_BACKUP_PLAN_RECEIPT_SHA256 = (
    __EXPECTED_BACKUP_PLAN_RECEIPT_SHA256__
)
EXPECTED_DEPLOYMENT_RECEIPT_SHA256 = __EXPECTED_DEPLOYMENT_RECEIPT_SHA256__
EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST = __EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST__
EXPECTED_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256 = (
    __EXPECTED_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256__
)
EXPECTED_CONFIGURATION_RECEIPT_SHA256 = __EXPECTED_CONFIGURATION_RECEIPT_SHA256__
EXPECTED_RELEASE_PLAN_RECEIPT_SHA256 = __EXPECTED_RELEASE_PLAN_RECEIPT_SHA256__
EXPECTED_SOURCE_COMMIT = __EXPECTED_SOURCE_COMMIT__
EXPECTED_BACKUP_RUNNER_SHA256 = __EXPECTED_BACKUP_RUNNER_SHA256__
EXPECTED_BACKUP_WORKER_SHA256 = __EXPECTED_BACKUP_WORKER_SHA256__
EXPECTED_RESTORE_VERIFIER_SHA256 = __EXPECTED_RESTORE_VERIFIER_SHA256__
EXPECTED_BASELINE_RUNNER_SHA256 = __EXPECTED_BASELINE_RUNNER_SHA256__
EXPECTED_BASELINE_WORKER_SHA256 = __EXPECTED_BASELINE_WORKER_SHA256__
EXPECTED_ADMIN_ROOT_DEPENDENCY_RUNNER_SHA256 = (
    __EXPECTED_ADMIN_ROOT_DEPENDENCY_RUNNER_SHA256__
)
EXPECTED_ADMIN_ROOT_DEPENDENCY_WORKER_SHA256 = (
    __EXPECTED_ADMIN_ROOT_DEPENDENCY_WORKER_SHA256__
)
EXPECTED_CONFIGURATION_RUNNER_SHA256 = __EXPECTED_CONFIGURATION_RUNNER_SHA256__
EXPECTED_CONFIGURATION_WORKER_SHA256 = __EXPECTED_CONFIGURATION_WORKER_SHA256__
EXPECTED_RELEASE_RUNNER_SHA256 = __EXPECTED_RELEASE_RUNNER_SHA256__
EXPECTED_RELEASE_WORKER_SHA256 = __EXPECTED_RELEASE_WORKER_SHA256__
EXPECTED_W1A_SCHEMA_FINGERPRINT = (
    "fbeb2d4d8bc79f3eb1f3ea715b11437fed33038f9c5bee20fe0e313f2df5d54d"
)
LEGACY_MIGRATION_FACT_FIELDS = {
    "publicReceiptCount",
    "internalReceiptCount",
    "tableCount",
    "triggerCount",
    "permissionCount",
    "eventCount",
    "journeyCount",
    "schemaFingerprintSha256",
}
MIGRATION_FACT_FIELDS = LEGACY_MIGRATION_FACT_FIELDS | {
    "probeEventCount",
    "naturalEventCount",
    "nonProbeEventCount",
    "authoritativeProductCreditCount",
    "probeJourneyCount",
    "naturalJourneyCount",
    "nonProbeJourneyCount",
}
LEGACY_DEPLOYMENT_RECEIPT_SCHEMA = (
    "fbsir.u3wW1aDeploymentReadinessReceipt.v2"
)
DEPLOYMENT_RECEIPT_SCHEMA = (
    "fbsir.u3wW1aDeploymentReadinessReceipt.v3"
)
LEGACY_ROLLBACK_RECEIPT_SCHEMA = (
    "fbsir.u3wDefaultOffReleaseRollbackReceipt.v1"
)
ROLLBACK_RECEIPT_SCHEMA = (
    "fbsir.u3wDefaultOffReleaseRollbackReceipt.v2"
)
LEGACY_ROLLBACK_VERIFICATION_SCHEMA = (
    "fbsir.u3wDefaultOffRollbackVerificationReceipt.v1"
)
ROLLBACK_VERIFICATION_SCHEMA = (
    "fbsir.u3wDefaultOffRollbackVerificationReceipt.v2"
)
INTERRUPTED_APPLY_RECOVERY_RECEIPT_SCHEMA = (
    "fbsir.u3wDefaultOffInterruptedApplyRecoveryReceipt.v1"
)
INTERRUPTED_APPLY_RECOVERY_STATE = (
    "INTERRUPTED_APPLY_RECOVERED_APPLICATION_DATABASE_043_"
    "RETAINED_DORMANT"
)
INTERRUPTED_APPLY_RECOVERY_TOPOLOGY_STATE = (
    "EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR"
)
INTERRUPTED_APPLY_RECOVERY_PLAN_SCHEMA = (
    "fbsir.u3wInterruptedApplyRecoveryPlan.v1"
)
INTERRUPTED_APPLY_RECOVERY_PLAN_STATE = (
    "INTERRUPTED_APPLY_RECOVERY_CANONICALIZATION_PLANNED"
)
INTERRUPTED_APPLY_RECOVERY_APPROVAL_NAME = (
    "interrupted-apply-recovery-approval.json"
)
INTERRUPTED_APPLY_RECOVERY_PLAN_NAME = (
    "interrupted-apply-recovery-plan.json"
)
INTERRUPTED_APPLY_RECOVERY_RECEIPT_FIELDS = {
    "schema",
    "state",
    "releaseId",
    "sourceCommit",
    "recoveryRunId",
    "executorSourceCommit",
    "approvalReceiptSha256",
    "approvalNonce",
    "runnerSha256",
    "workerSha256",
    "recoveryPlanReceiptSha256",
    "stageReceiptPath",
    "stageReceiptSha256",
    "applyFailureReceiptPath",
    "applyFailureReceiptSha256",
    "applyFailureReceiptSchema",
    "applyFailureReceiptState",
    "applyFailureReceiptManifest",
    "applyFailureReceiptManifestSha256",
    "applicationRestored",
    "topologyRestored",
    "deploymentCommitOutcome",
    "deploymentReceiptAbsent",
    "rollbackReceiptAbsent",
    "currentLinkAbsent",
    "releaseDropInMatched",
    "retainedMigrationFacts",
    "allW1aFlagsExplicitFalse",
    "databaseDownClaimed",
    "productionFilesystemChanged",
    "productionDatabaseChanged",
    "productionDatabaseChangedThisRecoveryRun",
    "productionDatabaseChangedSinceStage",
    "productionServiceChanged",
    "productionServiceChangedThisRecoveryRun",
    "productionServiceChangedSinceStage",
    "officialExpertsPackageChanged",
    "observedAt",
}
INTERRUPTED_APPLY_RECOVERY_APPROVAL_FIELDS = {
    "schema",
    "action",
    "targetHost",
    "runId",
    "executorSourceCommit",
    "targetReleaseId",
    "targetSourceCommit",
    "approvedAt",
    "expiresAt",
    "authorizedBy",
    "concurrentDdlProhibited",
    "productionFilesystemWrite",
    "productionDatabaseWrite",
    "productionServiceChange",
    "officialExpertsPackageChange",
    "expectedStageReceiptSha256",
    "expectedApplyFailureReceiptSha256",
    "expectedApplyFailureManifestSha256",
    "requestDigest",
    "approvalNonce",
    "runnerSha256",
    "workerSha256",
}
INTERRUPTED_APPLY_RECOVERY_PLAN_FIELDS = {
    "schema",
    "mode",
    "state",
    "targetHost",
    "serviceUnit",
    "recoveryRunId",
    "executorSourceCommit",
    "targetReleaseId",
    "targetSourceCommit",
    "stageReceiptSha256",
    "applyFailureReceiptSha256",
    "applyFailureManifestSha256",
    "runnerSha256",
    "workerSha256",
    "productionFilesystemWrite",
    "productionDatabaseWrite",
    "productionServiceChange",
    "officialExpertsPackageChange",
    "generatedAt",
    "expiresAt",
}
LEGACY_APPLY_FAILURE_RECEIPT_SCHEMA = (
    "fbsir.u3wDefaultOffReleaseFailureReceipt.v1"
)
APPLY_FAILURE_RECEIPT_SCHEMA = (
    "fbsir.u3wDefaultOffReleaseFailureReceipt.v2"
)
APPLY_FAILURE_STATES = {
    "FAIL_CLOSED_BEFORE_APPLICATION_SWITCH",
    "PRE_APPLY_RECOVERY_FAILED_TOPOLOGY_UNCERTAIN",
    "APPLICATION_RESTORED_DATABASE_043_RETAINED_OR_FAIL_CLOSED",
    "APPLICATION_RESTORE_FAILED_TOPOLOGY_UNCERTAIN",
    "DEPLOYMENT_COMMIT_AMBIGUOUS_NO_AUTOMATIC_RESTORE",
    "POST_COMMIT_VALIDATION_FAILED_NO_TOPOLOGY_CHANGE",
}
FLAG_NAMES = [
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
]
DATABASE_ENVIRONMENT_NAMES = [
    "WXFBSIR_MYSQL_URL",
    "WXFBSIR_MYSQL_USERNAME",
    "WXFBSIR_MYSQL_PASSWORD",
]
DATABASE_ENVIRONMENT_ALIAS_NAMES = [
    "FBSIR_MYSQL_URL",
    "FBSIR_MYSQL_USERNAME",
    "FBSIR_MYSQL_PASSWORD",
]
PROCESS_SECURITY_ENVIRONMENT_NAMES = (
    DATABASE_ENVIRONMENT_NAMES
    + DATABASE_ENVIRONMENT_ALIAS_NAMES
    + FLAG_NAMES
    + ["FBSIR_ENGINE_TOKEN"]
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
ADMIN_ENGINE_TOKEN_NAME = "FBSIR_ENGINE_TOKEN"
ATTRIBUTION_INGRESS_PATH = (
    "/internal/independent-board/attribution/events"
)
DISABLED_INGRESS_HTTP_STATUS = 404
ATTRIBUTION_EVENT_SIGNED_STRING_FIELDS = (
    "agentName",
    "channel",
    "classificationSource",
    "classifierVersion",
    "confidenceBucket",
    "contractId",
    "embeddedContractVersion",
    "eventId",
    "eventType",
    "expiresAt",
    "hostClientFamily",
    "hostVersion",
    "intentSignal",
    "issuedAt",
    "journeyId",
    "keyId",
    "listedManifestVersion",
    "listedSurface",
    "marketplace",
    "nonce",
    "occurredAt",
    "outcome",
    "packageId",
    "previousEventDigest",
    "productId",
    "receiptId",
    "requestSource",
    "reviewMode",
    "sameBindingKey",
    "schemaVersion",
    "serverBindingId",
    "signatureAlgorithm",
    "tenantSubjectDigest",
    "terminal",
    "traceparent",
    "trafficAuthority",
    "trafficClass",
)
MANAGED_W1A_ENVIRONMENT_NAMES = tuple(
    FLAG_NAMES
    + [
        EVENT_KEY_ID_NAME,
        EVENT_KEY_NAME,
        PREVIOUS_EVENT_KEY_ID_NAME,
        PREVIOUS_EVENT_KEY_NAME,
        SAME_BINDING_KEY_NAME,
    ]
)
MANAGED_RESTART_ENVIRONMENT_NAMES = (
    MANAGED_W1A_ENVIRONMENT_NAMES + (ADMIN_ENGINE_TOKEN_NAME,)
)
PLAN_TARGET_FIELDS = frozenset(
    (
        "schema",
        "targetHost",
        "serviceUnit",
        "service",
        "unitSha256",
        "fragmentFileManifest",
        "dropInManifest",
        "unitFiles",
        "environmentCustody",
        "environmentSha256",
        "environmentFilePaths",
        "environmentFileManifest",
        "additionalConfigCustody",
        "additionalConfigSha256",
        "externalConfigManifest",
        "activeJarPath",
        "activeJarSha256",
        "configuredJarPath",
        "configuredJarSha256",
        "processJarPath",
        "processJarSha256",
        "processArgvSha256",
        "processEnvironmentNamesSha256",
        "processFlagValues",
        "configuredFlagValues",
        "processForbiddenOverrideNames",
        "api2EventKeyManifest",
        "processSecurityConfigurationNames",
        "processSecurityConfigurationHmacSha256",
        "expectedSecurityConfigurationNames",
        "expectedSecurityConfigurationHmacSha256",
        "processDatabaseBindingMatched",
        "configuredEnvironmentSha256",
        "configuredEnvironmentNames",
        "configuredEnvironmentHmacSha256",
        "processConfiguredEnvironmentHmacSha256",
        "processConfiguredEnvironmentMatched",
        "processConfiguredEnvironmentMismatchNames",
        "processPendingRestartEnvironmentNames",
        "processConfiguredEnvironmentLoadState",
        "processConfiguredEnvironmentPreStageCompatible",
        "nginxConfigs",
        "activeNginxManifest",
        "nginxDumpSha256",
        "releaseRootExists",
        "releaseRootEntryManifest",
        "currentLinkExists",
        "currentLinkResolved",
        "currentLifecycleState",
        "stageEntryTopology",
        "productionChanged",
        "productionChangedByPlan",
    )
)

def run(args, env=None):
    return subprocess.check_output(
        args, text=True, stderr=subprocess.DEVNULL, env=env
    ).strip()

def sha256_file(filename):
    digest = hashlib.sha256()
    with open(filename, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

def exact_w1a_migration_facts(facts):
    return bool(
        isinstance(facts, dict)
        and set(facts) == MIGRATION_FACT_FIELDS
        and facts.get("publicReceiptCount") == 1
        and facts.get("internalReceiptCount") == 1
        and facts.get("tableCount") == 2
        and facts.get("triggerCount") == 2
        and facts.get("permissionCount") == 1
        and all(
            type(facts.get(field)) is int and facts[field] >= 0
            for field in (
                "eventCount",
                "probeEventCount",
                "naturalEventCount",
                "nonProbeEventCount",
                "authoritativeProductCreditCount",
                "journeyCount",
                "probeJourneyCount",
                "naturalJourneyCount",
                "nonProbeJourneyCount",
            )
        )
        and facts.get("probeEventCount") == facts.get("eventCount")
        and facts.get("naturalEventCount") == 0
        and facts.get("nonProbeEventCount") == 0
        and facts.get("authoritativeProductCreditCount") == 0
        and facts.get("probeJourneyCount") == facts.get("journeyCount")
        and facts.get("naturalJourneyCount") == 0
        and facts.get("nonProbeJourneyCount") == 0
        and facts.get("schemaFingerprintSha256")
            == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )


def legacy_w1a_migration_facts(facts):
    return bool(
        isinstance(facts, dict)
        and set(facts) == LEGACY_MIGRATION_FACT_FIELDS
        and facts.get("publicReceiptCount") == 1
        and facts.get("internalReceiptCount") == 1
        and facts.get("tableCount") == 2
        and facts.get("triggerCount") == 2
        and facts.get("permissionCount") == 1
        and type(facts.get("eventCount")) is int
        and facts["eventCount"] >= 0
        and type(facts.get("journeyCount")) is int
        and facts["journeyCount"] >= 0
        and facts.get("schemaFingerprintSha256")
            == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )


def recorded_w1a_migration_facts_valid(facts, receipt_schema):
    if receipt_schema in {
        LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
        LEGACY_ROLLBACK_RECEIPT_SCHEMA,
        LEGACY_ROLLBACK_VERIFICATION_SCHEMA,
    }:
        return legacy_w1a_migration_facts(facts)
    if receipt_schema in {
        DEPLOYMENT_RECEIPT_SCHEMA,
        ROLLBACK_RECEIPT_SCHEMA,
        ROLLBACK_VERIFICATION_SCHEMA,
    }:
        return exact_w1a_migration_facts(facts)
    return False


def recorded_w1a_migration_matches_live(facts, receipt_schema, live):
    return bool(
        recorded_w1a_migration_facts_valid(facts, receipt_schema)
        and live.get("w1a043State") == "EXACT_043_RETAINED_DORMANT"
        and live.get("publicInit043Applied") is True
        and live.get("publicInit043AnyReceiptCount") == 1
        and live.get("boardAttributionInternalReceiptCount") == 1
        and live.get("boardAttributionTableCount") == 2
        and live.get("boardAttributionTriggerCount") == 2
        and live.get("boardAttributionPermissionCount") == 1
        and live.get("boardAttributionEventCount")
            == live.get("boardAttributionProbeEventCount")
        and live.get("boardAttributionNaturalEventCount") == 0
        and live.get("boardAttributionNonProbeEventCount") == 0
        and live.get(
            "boardAttributionAuthoritativeProductCreditCount"
        ) == 0
        and live.get("boardAttributionJourneyCount")
            == live.get("boardAttributionProbeJourneyCount")
        and live.get("boardAttributionNaturalJourneyCount") == 0
        and live.get("boardAttributionNonProbeJourneyCount") == 0
        and live.get("boardAttributionEventCount")
            == facts.get("eventCount")
        and live.get("boardAttributionJourneyCount")
            == facts.get("journeyCount")
        and live.get("w1aSchemaFingerprintSha256")
            == facts.get("schemaFingerprintSha256")
            == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )


def recorded_recovery_migration_matches_live(recorded, live):
    return bool(
        exact_w1a_migration_facts(recorded)
        and live.get("w1a043State") == "EXACT_043_RETAINED_DORMANT"
        and live.get("publicInit043Applied") is True
        and live.get("publicInit043AnyReceiptCount") == 1
        and live.get("boardAttributionInternalReceiptCount") == 1
        and live.get("boardAttributionTableCount") == 2
        and live.get("boardAttributionTriggerCount") == 2
        and live.get("boardAttributionPermissionCount") == 1
        and type(live.get("boardAttributionEventCount")) is int
        and type(live.get("boardAttributionJourneyCount")) is int
        and live.get("boardAttributionEventCount") >= recorded.get("eventCount")
        and live.get("boardAttributionJourneyCount") >= recorded.get("journeyCount")
        and live.get("boardAttributionEventCount")
            == live.get("boardAttributionProbeEventCount")
        and live.get("boardAttributionNaturalEventCount") == 0
        and live.get("boardAttributionNonProbeEventCount") == 0
        and live.get(
            "boardAttributionAuthoritativeProductCreditCount"
        ) == 0
        and live.get("boardAttributionJourneyCount")
            == live.get("boardAttributionProbeJourneyCount")
        and live.get("boardAttributionNaturalJourneyCount") == 0
        and live.get("boardAttributionNonProbeJourneyCount") == 0
        and live.get("w1aSchemaFingerprintSha256")
            == recorded.get("schemaFingerprintSha256")
            == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )


def interrupted_recovery_historic_anchors_valid(release, recovery):
    approval_path = (
        release / INTERRUPTED_APPLY_RECOVERY_APPROVAL_NAME
    )
    plan_path = release / INTERRUPTED_APPLY_RECOVERY_PLAN_NAME
    try:
        approval_manifest = regular_file_manifest(
            approval_path, allowed_modes=(0o600,)
        )
        plan_manifest = regular_file_manifest(
            plan_path, allowed_modes=(0o600,)
        )
        approval = json.loads(
            approval_path.read_bytes().decode("utf-8-sig")
        )
        plan = json.loads(
            plan_path.read_bytes().decode("utf-8-sig")
        )
        approved = datetime.fromisoformat(
            approval["approvedAt"].replace("Z", "+00:00")
        )
        approval_expires = datetime.fromisoformat(
            approval["expiresAt"].replace("Z", "+00:00")
        )
        generated = datetime.fromisoformat(
            plan["generatedAt"].replace("Z", "+00:00")
        )
        plan_expires = datetime.fromisoformat(
            plan["expiresAt"].replace("Z", "+00:00")
        )
        observed = datetime.fromisoformat(
            recovery["observedAt"].replace("Z", "+00:00")
        )
    except (
        OSError, KeyError, AttributeError, UnicodeError,
        ValueError, TypeError, json.JSONDecodeError,
    ):
        return False
    return bool(
        approval_path
            == release / "interrupted-apply-recovery-approval.json"
        and plan_path
            == release / "interrupted-apply-recovery-plan.json"
        and approval_manifest["sha256"]
            == recovery.get("approvalReceiptSha256")
        and plan_manifest["sha256"]
            == recovery.get("recoveryPlanReceiptSha256")
        and set(approval)
            == INTERRUPTED_APPLY_RECOVERY_APPROVAL_FIELDS
        and set(plan) == INTERRUPTED_APPLY_RECOVERY_PLAN_FIELDS
        and approval.get("schema")
            == "fbsir.u3wProductionChangeApprovalReceipt.v2"
        and approval.get("action")
            == "CANONICALIZE_W1A_INTERRUPTED_APPLY_RECOVERY"
        and approval.get("targetHost") == TARGET_HOST
        and approval.get("runId") == recovery.get("recoveryRunId")
        and approval.get("executorSourceCommit")
            == recovery.get("executorSourceCommit")
        and approval.get("targetReleaseId")
            == recovery.get("releaseId")
        and approval.get("targetSourceCommit")
            == recovery.get("sourceCommit")
        and approval.get("authorizedBy") == "workspace-user"
        and approval.get("concurrentDdlProhibited") is True
        and approval.get("productionFilesystemWrite") is True
        and approval.get("productionDatabaseWrite") is False
        and approval.get("productionServiceChange") is False
        and approval.get("officialExpertsPackageChange") is False
        and approval.get("expectedStageReceiptSha256")
            == recovery.get("stageReceiptSha256")
        and approval.get("expectedApplyFailureReceiptSha256")
            == recovery.get("applyFailureReceiptSha256")
        and approval.get("expectedApplyFailureManifestSha256")
            == recovery.get("applyFailureReceiptManifestSha256")
        and approval.get("requestDigest")
            == recovery.get("recoveryPlanReceiptSha256")
        and approval.get("approvalNonce")
            == recovery.get("approvalNonce")
        and approval.get("runnerSha256")
            == recovery.get("runnerSha256")
        and approval.get("workerSha256")
            == recovery.get("workerSha256")
        and plan.get("schema")
            == INTERRUPTED_APPLY_RECOVERY_PLAN_SCHEMA
        and plan.get("mode") == "RecoveryPlan"
        and plan.get("state")
            == INTERRUPTED_APPLY_RECOVERY_PLAN_STATE
        and plan.get("targetHost") == TARGET_HOST
        and plan.get("serviceUnit") == SERVICE_UNIT
        and plan.get("recoveryRunId")
            == recovery.get("recoveryRunId")
        and plan.get("executorSourceCommit")
            == recovery.get("executorSourceCommit")
        and plan.get("targetReleaseId")
            == recovery.get("releaseId")
        and plan.get("targetSourceCommit")
            == recovery.get("sourceCommit")
        and plan.get("stageReceiptSha256")
            == recovery.get("stageReceiptSha256")
        and plan.get("applyFailureReceiptSha256")
            == recovery.get("applyFailureReceiptSha256")
        and plan.get("applyFailureManifestSha256")
            == recovery.get("applyFailureReceiptManifestSha256")
        and plan.get("runnerSha256")
            == recovery.get("runnerSha256")
        and plan.get("workerSha256")
            == recovery.get("workerSha256")
        and plan.get("productionFilesystemWrite") is True
        and plan.get("productionDatabaseWrite") is False
        and plan.get("productionServiceChange") is False
        and plan.get("officialExpertsPackageChange") is False
        and all(
            value.tzinfo is not None
            for value in (
                approved,
                approval_expires,
                generated,
                plan_expires,
                observed,
            )
        )
        and approval_expires > approved
        and approval_expires - approved <= timedelta(hours=24)
        and plan_expires > generated
        and plan_expires - generated <= timedelta(hours=24)
        and observed >= approved
        and observed >= generated
        and observed < approval_expires
        and observed < plan_expires
    )


def recorded_final_default_off_current_read_valid(receipt):
    receipt_schema = receipt.get("schema")
    evidence = receipt.get("finalDefaultOffCurrentRead")
    service_after = receipt.get("serviceAfter")
    migration = receipt.get("migrationFacts")
    probe = (
        evidence.get("disabledAttributionIngressProbe")
        if isinstance(evidence, dict) else None
    )
    probe_identity = (
        probe.get("probeIdentity") if isinstance(probe, dict) else None
    )
    identity_before = (
        probe.get("identityCountsBefore")
        if isinstance(probe, dict) else None
    )
    identity_after = (
        probe.get("identityCountsAfter")
        if isinstance(probe, dict) else None
    )
    expected_identity_fields = {
        "eventId",
        "receiptId",
        "nonceHash",
        "journeyId",
    }
    expected_global_counts = {
        "eventCount": migration.get("eventCount"),
        "journeyCount": migration.get("journeyCount"),
    } if isinstance(migration, dict) else None
    expected_evidence_schema = (
        "fbsir.u3wDefaultOffFinalCurrentRead.v1"
        if receipt_schema == LEGACY_DEPLOYMENT_RECEIPT_SCHEMA
        else "fbsir.u3wDefaultOffFinalCurrentRead.v2"
        if receipt_schema == DEPLOYMENT_RECEIPT_SCHEMA
        else None
    )
    return bool(
        isinstance(evidence, dict)
        and isinstance(service_after, dict)
        and recorded_w1a_migration_facts_valid(
            migration, receipt_schema
        )
        and evidence.get("schema")
            == expected_evidence_schema
        and evidence.get("verified") is True
        and evidence.get("serviceStableDuringProbe") is True
        and evidence.get("serviceInvocationId")
            == service_after.get("invocationId")
        and evidence.get("serviceJarSha256")
            == service_after.get("jarSha256")
        and evidence.get("migrationFactsBeforeProbe") == migration
        and evidence.get("migrationFactsAfterProbe") == migration
        and evidence.get("eventAndJourneyCountsUnchanged") is True
        and isinstance(probe, dict)
        and set(probe) == {
            "schema",
            "path",
            "method",
            "httpStatus",
            "responseDisposition",
            "verifiedDisabled",
            "acceptedDisabledHttpStatuses",
            "trafficClass",
            "signingKeyId",
            "signatureAlgorithm",
            "probeIdentity",
            "identityCountsBefore",
            "identityCountsAfter",
            "globalCountsBefore",
            "globalCountsAfter",
            "rawNonceDisclosed",
            "rawSignatureDisclosed",
            "signingKeyMaterialDisclosed",
            "secretsDisclosed",
            "observedAt",
        }
        and probe.get("schema")
            == "fbsir.u3wSignedDisabledAttributionIngressProbe.v1"
        and probe.get("path") == ATTRIBUTION_INGRESS_PATH
        and probe.get("method") == "POST"
        and probe.get("httpStatus") == DISABLED_INGRESS_HTTP_STATUS
        and probe.get("responseDisposition") == "ROUTE_NOT_FOUND"
        and probe.get("verifiedDisabled") is True
        and probe.get("acceptedDisabledHttpStatuses")
            == [DISABLED_INGRESS_HTTP_STATUS]
        and probe.get("trafficClass") == "PROBE"
        and re.fullmatch(
            r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}",
            str(probe.get("signingKeyId") or ""),
        ) is not None
        and probe.get("signatureAlgorithm") == "hmac-sha256-v1"
        and isinstance(probe_identity, dict)
        and set(probe_identity) == expected_identity_fields
        and all(
            re.fullmatch(r"[0-9a-f]{64}", str(value or ""))
                is not None
            for value in probe_identity.values()
        )
        and identity_before
            == {name: 0 for name in expected_identity_fields}
        and identity_after
            == {name: 0 for name in expected_identity_fields}
        and probe.get("globalCountsBefore") == expected_global_counts
        and probe.get("globalCountsAfter") == expected_global_counts
        and probe.get("rawNonceDisclosed") is False
        and probe.get("rawSignatureDisclosed") is False
        and probe.get("signingKeyMaterialDisclosed") is False
        and probe.get("secretsDisclosed") is False
        and isinstance(probe.get("observedAt"), str)
    )

def stable_database_identity_matches(pre_stage, live):
    return bool(
        isinstance(pre_stage, dict)
        and isinstance(live, dict)
        and pre_stage.get("databaseEndpoint")
            == live.get("databaseEndpoint")
        and pre_stage.get("database") == live.get("database")
        and pre_stage.get("databaseServerUuid")
            == live.get("databaseServerUuid")
        and pre_stage.get("databaseServerVersion")
            == live.get("serverVersion")
        and pre_stage.get("legacyBaselineReceiptSha256")
            == live.get("legacyBaselineReceiptSha256")
        and pre_stage.get("legacyBaselineMigrationCount")
            == live.get("legacyBaselineMigrationCount")
        and live.get("legacyBaselineMigrationCount") == 1
    )

def live_w1a_prestate_matches(pre_stage, live, receipt_schema):
    if not stable_database_identity_matches(pre_stage, live):
        return False
    if receipt_schema not in {
        LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
        DEPLOYMENT_RECEIPT_SCHEMA,
    }:
        return False
    legacy_receipt = receipt_schema == LEGACY_DEPLOYMENT_RECEIPT_SCHEMA
    state = pre_stage.get("w1aDatabaseState")
    if state == "ABSENT":
        return bool(
            pre_stage.get("attributionEventCount") == 0
            and pre_stage.get("attributionJourneyCount") == 0
            and (
                legacy_receipt
                or (
                    pre_stage.get("attributionProbeEventCount") == 0
                    and pre_stage.get("attributionNaturalEventCount") == 0
                    and pre_stage.get("attributionNonProbeEventCount") == 0
                    and pre_stage.get(
                        "attributionAuthoritativeProductCreditCount"
                    ) == 0
                    and pre_stage.get("attributionProbeJourneyCount") == 0
                    and pre_stage.get("attributionNaturalJourneyCount") == 0
                    and pre_stage.get("attributionNonProbeJourneyCount") == 0
                )
            )
            and
            live.get("publicInit043Applied") is not True
            and live.get("boardAttributionTableCount") == 0
            and live.get("boardAttributionTriggerCount") == 0
            and live.get("boardAttributionPermissionCount") == 0
            and live.get("boardAttributionInternalReceiptCount") == 0
            and live.get("boardAttributionEventCount") == 0
            and live.get("boardAttributionProbeEventCount") == 0
            and live.get("boardAttributionNaturalEventCount") == 0
            and live.get("boardAttributionNonProbeEventCount") == 0
            and live.get(
                "boardAttributionAuthoritativeProductCreditCount"
            ) == 0
            and live.get("boardAttributionJourneyCount") == 0
            and live.get("boardAttributionProbeJourneyCount") == 0
            and live.get("boardAttributionNaturalJourneyCount") == 0
            and live.get("boardAttributionNonProbeJourneyCount") == 0
            and live.get("w1aSchemaFingerprintSha256") is None
        )
    if state == "EXACT_043_RETAINED_DORMANT":
        return bool(
            live.get("publicInit043Applied") is True
            and live.get("boardAttributionTableCount") == 2
            and live.get("boardAttributionTriggerCount") == 2
            and live.get("boardAttributionPermissionCount") == 1
            and live.get("boardAttributionInternalReceiptCount") == 1
            and live.get("boardAttributionEventCount")
                == pre_stage.get("attributionEventCount")
            and live.get("boardAttributionJourneyCount")
                == pre_stage.get("attributionJourneyCount")
            and (
                legacy_receipt
                or (
                    live.get("boardAttributionProbeEventCount")
                        == pre_stage.get("attributionProbeEventCount")
                    and live.get("boardAttributionNaturalEventCount")
                        == pre_stage.get("attributionNaturalEventCount")
                    and live.get("boardAttributionNonProbeEventCount")
                        == pre_stage.get("attributionNonProbeEventCount")
                    and live.get(
                        "boardAttributionAuthoritativeProductCreditCount"
                    ) == pre_stage.get(
                        "attributionAuthoritativeProductCreditCount"
                    )
                    and live.get("boardAttributionProbeJourneyCount")
                        == pre_stage.get("attributionProbeJourneyCount")
                    and live.get("boardAttributionNaturalJourneyCount")
                        == pre_stage.get("attributionNaturalJourneyCount")
                    and live.get("boardAttributionNonProbeJourneyCount")
                        == pre_stage.get("attributionNonProbeJourneyCount")
                )
            )
            and live.get("boardAttributionEventCount")
                == live.get("boardAttributionProbeEventCount")
            and live.get("boardAttributionNaturalEventCount") == 0
            and live.get("boardAttributionNonProbeEventCount") == 0
            and live.get(
                "boardAttributionAuthoritativeProductCreditCount"
            ) == 0
            and live.get("boardAttributionJourneyCount")
                == live.get("boardAttributionProbeJourneyCount")
            and live.get("boardAttributionNaturalJourneyCount") == 0
            and live.get("boardAttributionNonProbeJourneyCount") == 0
            and live.get("w1aSchemaFingerprintSha256")
                == EXPECTED_W1A_SCHEMA_FINGERPRINT
            and pre_stage.get("w1aSchemaFingerprintSha256")
                == EXPECTED_W1A_SCHEMA_FINGERPRINT
        )
    return False

def snapshot_dropin_exact(snapshot, expected_sha256):
    manifest = snapshot.get("dropInManifest")
    if not isinstance(manifest, list):
        return False
    entries = [
        item for item in manifest
        if isinstance(item, dict)
        and item.get("path") == (
            "/etc/systemd/system/fbsir-admin.service.d/"
            "20-u3w-default-off-release.conf"
        )
    ]
    return bool(
        len(entries) == 1
        and entries[0].get("sha256") == expected_sha256
        and entries[0].get("mode") == 0o644
    )

def snapshot_default_off(
    snapshot, allowed_load_states=("EXACT_CONFIGURED",)
):
    if not isinstance(snapshot, dict):
        return False
    state = snapshot.get("processConfiguredEnvironmentLoadState")
    configured_flags = snapshot.get("configuredFlagValues", {})
    process_flags = snapshot.get("processFlagValues", {})
    common = bool(
        state in allowed_load_states
        and snapshot.get("processForbiddenOverrideNames") == []
        and snapshot.get("processDatabaseBindingMatched") is True
        and snapshot.get(
            "processConfiguredEnvironmentPreStageCompatible"
        ) is True
        and snapshot.get(
            "processConfiguredEnvironmentMismatchNames"
        ) == []
        and all(
            configured_flags.get(name) == "false"
            for name in FLAG_NAMES
        )
    )
    if not common:
        return False
    if state == "EXACT_CONFIGURED":
        return bool(
            snapshot.get("processConfiguredEnvironmentMatched") is True
            and snapshot.get(
                "processPendingRestartEnvironmentNames"
            ) == []
            and snapshot.get(
                "processConfiguredEnvironmentHmacSha256"
            ) == snapshot.get("configuredEnvironmentHmacSha256")
            and snapshot.get("processSecurityConfigurationNames")
                == snapshot.get("expectedSecurityConfigurationNames")
            and snapshot.get(
                "processSecurityConfigurationHmacSha256"
            ) == snapshot.get(
                "expectedSecurityConfigurationHmacSha256"
            )
            and all(
                process_flags.get(name) == "false"
                for name in FLAG_NAMES
            )
        )
    if state == "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART":
        return bool(
            snapshot.get("processConfiguredEnvironmentMatched") is False
            and snapshot.get(
                "processPendingRestartEnvironmentNames"
            ) == sorted(MANAGED_RESTART_ENVIRONMENT_NAMES)
            and all(
                process_flags.get(name) is None
                for name in FLAG_NAMES
            )
        )
    if state == "ENGINE_CREDENTIAL_PENDING_RESTART":
        return bool(
            snapshot.get("processConfiguredEnvironmentMatched") is False
            and snapshot.get(
                "processPendingRestartEnvironmentNames"
            ) == [ADMIN_ENGINE_TOKEN_NAME]
            and all(
                process_flags.get(name) == "false"
                for name in FLAG_NAMES
            )
        )
    return False

def snapshot_configuration_matches(
    live, baseline, allowed_load_states=("EXACT_CONFIGURED",)
):
    fields = (
        "user",
        "group",
        "fragmentPath",
        "fragmentFileManifest",
        "environmentFiles",
        "environmentFilePaths",
        "environmentFileManifest",
        "processEnvironmentNamesSha256",
        "processSecurityConfigurationNames",
        "processSecurityConfigurationHmacSha256",
        "expectedSecurityConfigurationNames",
        "expectedSecurityConfigurationHmacSha256",
        "configuredEnvironmentSha256",
        "configuredEnvironmentNames",
        "configuredEnvironmentHmacSha256",
        "configuredFlagValues",
        "processConfiguredEnvironmentHmacSha256",
        "processConfiguredEnvironmentMatched",
        "processConfiguredEnvironmentMismatchNames",
        "processPendingRestartEnvironmentNames",
        "processConfiguredEnvironmentLoadState",
        "processConfiguredEnvironmentPreStageCompatible",
        "externalConfigManifest",
        "additionalConfigSha256",
        "api2EventKeyManifest",
    )
    return bool(
        isinstance(live, dict)
        and isinstance(baseline, dict)
        and all(live.get(field) == baseline.get(field) for field in fields)
        and snapshot_default_off(live, allowed_load_states)
        and snapshot_default_off(baseline, allowed_load_states)
    )

def snapshot_exact_loaded_from_baseline(live, baseline):
    static_fields = (
        "environmentFilePaths",
        "environmentFileManifest",
        "api2EventKeyManifest",
        "expectedSecurityConfigurationNames",
        "expectedSecurityConfigurationHmacSha256",
        "configuredEnvironmentSha256",
        "configuredEnvironmentNames",
        "configuredEnvironmentHmacSha256",
        "configuredFlagValues",
        "externalConfigManifest",
        "additionalConfigSha256",
    )
    return bool(
        isinstance(live, dict)
        and isinstance(baseline, dict)
        and all(
            live.get(field) == baseline.get(field)
            for field in static_fields
        )
        and snapshot_default_off(live, ("EXACT_CONFIGURED",))
        and snapshot_default_off(
            baseline,
            (
                "EXACT_CONFIGURED",
                "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART",
                "ENGINE_CREDENTIAL_PENDING_RESTART",
            ),
        )
    )

def authorized_admin_engine_configuration_evolution_matches(
    live,
    baseline,
    configuration,
    predecessor,
):
    if (
        not isinstance(live, dict)
        or not isinstance(baseline, dict)
        or not isinstance(configuration, dict)
        or not isinstance(predecessor, dict)
        or configuration.get("schema")
            != "fbsir.u3wDefaultOffConfigurationReceipt.v3"
    ):
        return False
    previous_manifest = baseline.get("environmentFileManifest", [])
    current_manifest = live.get("environmentFileManifest", [])
    if len(previous_manifest) != 1 or len(current_manifest) != 1:
        return False
    previous_file = dict(previous_manifest[0])
    current_file = dict(current_manifest[0])
    previous_file_sha = previous_file.pop("sha256", None)
    current_file_sha = current_file.pop("sha256", None)
    previous_names = baseline.get("configuredEnvironmentNames", [])
    current_names = live.get("configuredEnvironmentNames", [])
    expected_current_names = sorted(
        list(previous_names) + [ADMIN_ENGINE_TOKEN_NAME]
    )
    previous_security_names = baseline.get(
        "expectedSecurityConfigurationNames", []
    )
    expected_current_security_names = sorted(
        list(previous_security_names) + [ADMIN_ENGINE_TOKEN_NAME]
    )
    load_state = live.get("processConfiguredEnvironmentLoadState")
    exact_loaded = bool(
        load_state == "EXACT_CONFIGURED"
        and live.get("processConfiguredEnvironmentMatched") is True
        and live.get("processPendingRestartEnvironmentNames") == []
        and live.get("processSecurityConfigurationNames")
            == live.get("expectedSecurityConfigurationNames")
        and live.get("processSecurityConfigurationHmacSha256")
            == live.get("expectedSecurityConfigurationHmacSha256")
    )
    engine_pending = bool(
        load_state == "ENGINE_CREDENTIAL_PENDING_RESTART"
        and live.get("processConfiguredEnvironmentMatched") is False
        and live.get("processPendingRestartEnvironmentNames")
            == [ADMIN_ENGINE_TOKEN_NAME]
    )
    return bool(
        configuration.get("adminEngineCredentialProvisioningState")
            in {
                "CREATED_BY_RUN",
                "ADOPTED_EXISTING_EXACT_DELTA",
            }
        and predecessor.get("environmentAfterSha256")
            == baseline.get("configuredEnvironmentSha256")
        and previous_file_sha
            == baseline.get("configuredEnvironmentSha256")
        and configuration.get("environmentAfterSha256")
            == live.get("configuredEnvironmentSha256")
        and current_file_sha
            == live.get("configuredEnvironmentSha256")
        and previous_file == current_file
        and live.get("environmentFilePaths")
            == baseline.get("environmentFilePaths")
        and ADMIN_ENGINE_TOKEN_NAME not in previous_names
        and current_names == expected_current_names
        and ADMIN_ENGINE_TOKEN_NAME not in previous_security_names
        and live.get("expectedSecurityConfigurationNames")
            == expected_current_security_names
        and live.get("api2EventKeyManifest")
            == baseline.get("api2EventKeyManifest")
        and live.get("configuredFlagValues")
            == baseline.get("configuredFlagValues")
        and live.get("processDatabaseBindingMatched") is True
        and live.get(
            "processConfiguredEnvironmentPreStageCompatible"
        ) is True
        and live.get(
            "processConfiguredEnvironmentMismatchNames"
        ) == []
        and live.get("processForbiddenOverrideNames") == []
        and (exact_loaded or engine_pending)
        and all(
            live.get("configuredFlagValues", {}).get(name) == "false"
            and live.get("processFlagValues", {}).get(name) == "false"
            for name in FLAG_NAMES
        )
    )

def staged_predecessor_snapshot_matches(live, baseline):
    fields = (
        "workingDirectory",
        "configuredJarPath",
        "configuredJarSha256",
        "processJarPath",
        "processJarSha256",
        "processArgvSha256",
        "processFlagValues",
        "processForbiddenOverrideNames",
        "dropInManifest",
        "jarPath",
        "jarSha256",
    )
    baseline_load_state = (
        baseline.get("processConfiguredEnvironmentLoadState")
        if isinstance(baseline, dict) else None
    )
    allowed_load_states = (
        (baseline_load_state,)
        if baseline_load_state in {
            "EXACT_CONFIGURED",
            "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART",
            "ENGINE_CREDENTIAL_PENDING_RESTART",
        }
        else ()
    )
    return bool(
        snapshot_configuration_matches(
            live, baseline, allowed_load_states
        )
        and live.get("activeState") == "active"
        and all(live.get(field) == baseline.get(field) for field in fields)
    )

def decode_secret_material(encoded):
    value = str(encoded or "").strip()
    if not value:
        return None
    try:
        if value.startswith("base64:"):
            raw_base64 = value[7:]
            if len(raw_base64) % 4 == 1:
                return None
            padded_base64 = raw_base64 + ("=" * (-len(raw_base64) % 4))
            return base64.b64decode(padded_base64, validate=True)
        if value.startswith("hex:"):
            raw_hex = value[4:]
            if (
                not raw_hex
                or len(raw_hex) % 2 != 0
                or re.fullmatch(r"[0-9a-fA-F]+", raw_hex) is None
            ):
                return None
            return bytes.fromhex(raw_hex)
        if value.startswith("utf8:"):
            value = value[5:]
        return value.encode("utf-8")
    except (ValueError, UnicodeError):
        return None

def exact_admin_engine_delta_predecessor_sha256(path):
    raw = pathlib.Path(path).read_bytes()
    try:
        current = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise RuntimeError(
            "admin Engine credential environment is not UTF-8"
        ) from error
    match = re.search(
        r"(?:^|\n)FBSIR_ENGINE_TOKEN="
        r"(?P<credential>[A-Za-z0-9_-]{43,128})\n\Z",
        current,
    )
    if match is None:
        raise RuntimeError(
            "admin Engine credential is not one canonical append-only delta"
        )
    predecessor = current[:match.start()]
    if match.start() > 0:
        predecessor += "\n"
    return hashlib.sha256(predecessor.encode("utf-8")).hexdigest()

def parse_env_file(filename):
    values = {}
    for line_number, raw in enumerate(
        pathlib.Path(filename).read_text(
            encoding="utf-8", errors="strict"
        ).splitlines(),
        1,
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
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if (
            re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key) is None
            or key in values
        ):
            raise RuntimeError("invalid or duplicate environment key")
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        values[key] = value
    return values

def normalized_environment_file(raw_value):
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
        declarations.append({
            "path": token,
            "ignoreErrors": optional,
        })
        position = match.end()
    expected = [{
        "path": "/etc/u3w/fbsir-admin.env",
        "ignoreErrors": False,
    }]
    if declarations != expected:
        raise RuntimeError(
            "fbsir-admin requires one mandatory fixed EnvironmentFile"
        )
    return declarations[0]

def regular_file_manifest(filename, allowed_modes=(0o600, 0o640, 0o644)):
    candidate = pathlib.Path(filename)
    if candidate.is_symlink() or not candidate.is_file():
        raise RuntimeError("required regular file is absent")
    status = candidate.stat()
    if (
        status.st_uid != 0
        or status.st_gid != 0
        or status.st_nlink != 1
        or status.st_mode & 0o777 not in allowed_modes
    ):
        raise RuntimeError("regular file custody is invalid")
    return {
        "path": str(candidate),
        "sha256": sha256_file(candidate),
        "mode": status.st_mode & 0o777,
        "uid": status.st_uid,
        "gid": status.st_gid,
        "nlink": status.st_nlink,
    }

def active_nginx_manifest():
    nginx_dump_bytes = subprocess.run(
        ["nginx", "-T"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    ).stdout
    document = nginx_dump_bytes.decode("utf-8", errors="strict")
    paths = sorted(set(re.findall(
        r"^# configuration file ([^:]+):$", document, re.M
    )))
    manifests = []
    for value in paths:
        manifest = regular_file_manifest(value)
        manifest["sizeBytes"] = pathlib.Path(value).stat().st_size
        manifests.append(manifest)
    if not manifests:
        raise RuntimeError("active Nginx configuration manifest is empty")
    second = subprocess.run(
        ["nginx", "-T"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    ).stdout
    if not hmac.compare_digest(nginx_dump_bytes, second):
        raise RuntimeError(
            "active Nginx configuration changed during snapshot"
        )
    return manifests, hashlib.sha256(nginx_dump_bytes).hexdigest()

def release_root_entry_manifest():
    release_root = pathlib.Path("/opt/fbsir/admin/releases")
    latest_receipt = pathlib.Path(DEPLOYMENT_RECEIPT_PATH)
    if not release_root.exists() and not release_root.is_symlink():
        return []
    status = release_root.lstat()
    if (
        not stat.S_ISDIR(status.st_mode)
        or stat.S_ISLNK(status.st_mode)
        or status.st_uid != 0
        or status.st_gid != 0
        or status.st_mode & 0o777 != 0o755
    ):
        raise RuntimeError("release root custody is invalid")
    result = []
    for entry in sorted(release_root.iterdir(), key=lambda item: item.name):
        entry_status = entry.lstat()
        item = {
            "name": entry.name,
            "uid": entry_status.st_uid,
            "gid": entry_status.st_gid,
            "mode": entry_status.st_mode & 0o777,
            "nlink": entry_status.st_nlink,
        }
        if (
            entry_status.st_uid != 0
            or entry_status.st_gid != 0
            or (
                not stat.S_ISLNK(entry_status.st_mode)
                and entry_status.st_mode & 0o022
            )
        ):
            raise RuntimeError("release root entry custody is invalid")
        if stat.S_ISDIR(entry_status.st_mode):
            item["type"] = "directory"
        elif stat.S_ISLNK(entry_status.st_mode):
            if entry != latest_receipt:
                raise RuntimeError("unexpected release root symlink")
            resolved = entry.resolve(strict=True)
            if release_root.resolve(strict=True) not in resolved.parents:
                raise RuntimeError(
                    "latest release receipt escaped release root"
                )
            manifest = regular_file_manifest(
                resolved, allowed_modes=(0o600,)
            )
            item.update({
                "type": "symlink",
                "target": os.readlink(entry),
                "targetSha256": manifest["sha256"],
            })
        elif stat.S_ISREG(entry_status.st_mode):
            item.update({
                "type": "file",
                "sha256": sha256_file(entry),
            })
        else:
            raise RuntimeError("unsupported release root entry type")
        result.append(item)
    return result

def security_configuration_evidence(process_values, expected_values):
    event_key = pathlib.Path(API2_EVENT_KEY_PATH)
    event_key_manifest = regular_file_manifest(
        event_key, allowed_modes=(0o600,)
    )
    key = event_key.read_bytes()
    if not key:
        raise RuntimeError("API2 event key material is empty")
    configured_event_key = decode_secret_material(
        expected_values.get(EVENT_KEY_NAME)
    )
    if (
        configured_event_key is None
        or not hmac.compare_digest(configured_event_key, key)
    ):
        raise RuntimeError(
            "configured event key does not match API2 material"
        )
    engine_credential = str(
        expected_values.get(ADMIN_ENGINE_TOKEN_NAME, "")
    ).strip()
    if re.fullmatch(
        r"[A-Za-z0-9_-]{43,128}", engine_credential
    ) is None:
        raise RuntimeError(
            "admin Engine credential shape is invalid"
        )
    engine_material = engine_credential.encode("utf-8")
    for name in (
        EVENT_KEY_NAME,
        PREVIOUS_EVENT_KEY_NAME,
        SAME_BINDING_KEY_NAME,
        "FBSIR_TOKEN_SECRET",
        "WXFBSIR_TOKEN_SECRET",
    ):
        comparison = decode_secret_material(expected_values.get(name))
        if (
            comparison is not None
            and hmac.compare_digest(engine_material, comparison)
        ):
            raise RuntimeError(
                "admin Engine credential is not independent"
            )
    expected_names = sorted(
        name for name in PROCESS_SECURITY_ENVIRONMENT_NAMES
        if name in expected_values
    )
    actual_names = sorted(
        name for name in PROCESS_SECURITY_ENVIRONMENT_NAMES
        if name in process_values
    )

    def configuration_hmac(values, names):
        payload = canonical_json(
            [[name, values[name]] for name in names]
        ).encode("utf-8")
        return hmac.new(
            key,
            b"fbsir.u3wProcessSecurityConfiguration.v1\0" + payload,
            hashlib.sha256,
        ).hexdigest()

    expected_hmac = configuration_hmac(expected_values, expected_names)
    actual_hmac = (
        configuration_hmac(process_values, actual_names)
        if actual_names else None
    )
    expected_database_names = sorted(
        name
        for name in (
            DATABASE_ENVIRONMENT_NAMES
            + DATABASE_ENVIRONMENT_ALIAS_NAMES
        )
        if name in expected_values
    )
    actual_database_names = sorted(
        name
        for name in expected_database_names
        if name in process_values
    )
    expected_database_hmac = configuration_hmac(
        expected_values, expected_database_names
    )
    actual_database_hmac = (
        configuration_hmac(process_values, actual_database_names)
        if actual_database_names else None
    )
    database_matched = bool(
        actual_database_hmac is not None
        and actual_database_names == expected_database_names
        and hmac.compare_digest(
            actual_database_hmac, expected_database_hmac
        )
        and all(
            name in process_values
            and hmac.compare_digest(
                process_values[name], expected_values[name]
            )
            for name in DATABASE_ENVIRONMENT_NAMES
        )
    )
    configured_names = sorted(expected_values)
    process_configured_names = sorted(
        name for name in configured_names if name in process_values
    )
    configured_hmac = configuration_hmac(
        expected_values, configured_names
    )
    process_configured_hmac = (
        configuration_hmac(process_values, process_configured_names)
        if process_configured_names else None
    )
    configured_matched = bool(
        process_configured_hmac is not None
        and process_configured_names == configured_names
        and hmac.compare_digest(
            process_configured_hmac, configured_hmac
        )
    )
    mismatch_names = sorted(
        name
        for name in process_configured_names
        if not hmac.compare_digest(
            process_values[name], expected_values[name]
        )
    )
    pending_names = sorted(
        set(configured_names) - set(process_configured_names)
    )
    configured_flags = {
        name: expected_values.get(name) for name in FLAG_NAMES
    }
    all_managed_configured = all(
        name in expected_values
        for name in MANAGED_RESTART_ENVIRONMENT_NAMES
    )
    flags_configured_false = all(
        configured_flags[name] == "false" for name in FLAG_NAMES
    )
    if configured_matched and not pending_names and not mismatch_names:
        load_state = "EXACT_CONFIGURED"
    elif (
        pending_names == sorted(MANAGED_RESTART_ENVIRONMENT_NAMES)
        and not mismatch_names
        and all_managed_configured
        and flags_configured_false
        and database_matched
    ):
        load_state = "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART"
    elif (
        pending_names == [ADMIN_ENGINE_TOKEN_NAME]
        and not mismatch_names
        and all_managed_configured
        and flags_configured_false
        and database_matched
    ):
        load_state = "ENGINE_CREDENTIAL_PENDING_RESTART"
    else:
        load_state = "INVALID_PARTIAL_OR_DRIFTED"
    return {
        "api2EventKeyManifest": event_key_manifest,
        "processSecurityConfigurationNames": actual_names,
        "processSecurityConfigurationHmacSha256": actual_hmac,
        "expectedSecurityConfigurationNames": expected_names,
        "expectedSecurityConfigurationHmacSha256": expected_hmac,
        "processDatabaseBindingMatched": database_matched,
        "configuredEnvironmentSha256": sha256_file(
            "/etc/u3w/fbsir-admin.env"
        ),
        "configuredEnvironmentNames": configured_names,
        "configuredEnvironmentHmacSha256": configured_hmac,
        "processConfiguredEnvironmentHmacSha256":
            process_configured_hmac,
        "processConfiguredEnvironmentMatched": configured_matched,
        "configuredFlagValues": configured_flags,
        "processConfiguredEnvironmentMismatchNames": mismatch_names,
        "processPendingRestartEnvironmentNames": pending_names,
        "processConfiguredEnvironmentLoadState": load_state,
        "processConfiguredEnvironmentPreStageCompatible":
            load_state in {
                "EXACT_CONFIGURED",
                "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART",
                "ENGINE_CREDENTIAL_PENDING_RESTART",
            },
    }

def canonical_json(value):
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True,
        separators=(",", ":"), allow_nan=False
    )

def validate_release_plan_target_binding(release_directory, receipt):
    result = {
        "verified": False,
        "releasePlanTargetSha256": None,
        "releasePlanTargetComparableSha256": None,
    }
    plan_path = release_directory / "evidence/release-plan.json"
    try:
        plan_digest_mismatched = (
            not EXPECTED_RELEASE_PLAN_RECEIPT_SHA256
            or sha256_file(plan_path) != EXPECTED_RELEASE_PLAN_RECEIPT_SHA256
        )
    except OSError:
        return result
    if plan_digest_mismatched:
        return result
    descriptor = None
    try:
        descriptor = os.open(
            plan_path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
        )
        before = os.fstat(descriptor)
        if (
            not stat.S_ISREG(before.st_mode)
            or before.st_uid != 0
            or before.st_gid != 0
            or before.st_nlink != 1
            or before.st_mode & 0o777 not in (0o600, 0o644)
            or before.st_size <= 0
            or before.st_size > 4 * 1024 * 1024
        ):
            return result
        chunks = []
        size = 0
        digest = hashlib.sha256()
        while True:
            block = os.read(descriptor, 1024 * 1024)
            if not block:
                break
            size += len(block)
            if size > 4 * 1024 * 1024:
                return result
            digest.update(block)
            chunks.append(block)
        after = os.fstat(descriptor)
        identity = lambda value: (
            value.st_dev,
            value.st_ino,
            value.st_mode,
            value.st_uid,
            value.st_gid,
            value.st_nlink,
            value.st_size,
            value.st_mtime_ns,
            value.st_ctime_ns,
        )
        if (
            identity(before) != identity(after)
            or size != after.st_size
            or digest.hexdigest()
                != EXPECTED_RELEASE_PLAN_RECEIPT_SHA256
        ):
            return result
        plan = json.loads(b"".join(chunks).decode("utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        return result
    finally:
        if descriptor is not None:
            os.close(descriptor)
    target = plan.get("target") if isinstance(plan, dict) else None
    if (
        not isinstance(plan, dict)
        or plan.get("schema") != "fbsir.u3wDefaultOffReleasePlan.v2"
        or plan.get("mode") != "Plan"
        or plan.get("productionChanged") is not False
        or plan.get("strictHeadClean") is not True
        or plan.get("releaseId") != receipt.get("releaseId")
        or plan.get("sourceCommit") != receipt.get("sourceCommit")
        or plan.get("sourceCommit") != EXPECTED_SOURCE_COMMIT
        or plan.get("expectedSourceCommit") != EXPECTED_SOURCE_COMMIT
        or plan.get("runnerSha256") != EXPECTED_RELEASE_RUNNER_SHA256
        or plan.get("runnerSha256") != receipt.get("runnerSha256")
        or plan.get("workerSha256") != EXPECTED_RELEASE_WORKER_SHA256
        or plan.get("workerSha256") != receipt.get("workerSha256")
        or plan.get("buildReceiptSha256")
            != receipt.get("buildReceiptSha256")
        or plan.get("backendBuildSha256")
            != receipt.get("backendBuildSha256")
        or plan.get("frontendTreeSha256")
            != receipt.get("frontendBuildSha256")
        or plan.get("migrationSha256") != receipt.get("migrationSha256")
        or plan.get("requiredModes")
            != ["Build", "Plan", "Stage", "Apply", "Rollback", "Verify"]
        or not isinstance(target, dict)
        or set(target) != PLAN_TARGET_FIELDS
        or target.get("schema")
            != "fbsir.u3wDefaultOffRemotePlanSnapshot.v1"
        or target.get("targetHost") != TARGET_HOST
        or target.get("serviceUnit") != SERVICE_UNIT
        or target.get("productionChangedByPlan") is not False
    ):
        return result
    target_sha256 = hashlib.sha256(
        canonical_json(target).encode("utf-8")
    ).hexdigest()
    comparable = dict(target)
    comparable.pop("releaseRootExists")
    comparable.pop("releaseRootEntryManifest")
    comparable_sha256 = hashlib.sha256(
        canonical_json(comparable).encode("utf-8")
    ).hexdigest()
    release_plan_target_binding_verified = bool(
        hmac.compare_digest(
            target_sha256,
            str(receipt.get("releasePlanTargetSha256") or ""),
        )
        and hmac.compare_digest(
            comparable_sha256,
            str(
                receipt.get("releasePlanTargetComparableSha256")
                or ""
            ),
        )
        and hmac.compare_digest(
            comparable_sha256,
            str(
                receipt.get(
                    "finalizeStageLiveTargetComparableSha256"
                )
                or ""
            ),
        )
        and receipt.get("stageOwnedReleaseRootDeltaVerified") is True
    )
    return {
        "verified": release_plan_target_binding_verified,
        "releasePlanTargetSha256": target_sha256,
        "releasePlanTargetComparableSha256": comparable_sha256,
    }

CONTROL_TABLES = (
    "u3w_schema_migration",
    "u3w_legacy_schema_baseline_receipt_v2",
)
LEGACY_PROJECTION_EXCLUDED_TABLES = CONTROL_TABLES + (
    "fbs_board_attr_journey_v1",
    "fbs_board_attr_event_v1",
)
EXPECTED_CONTROL_OVERLAY_SHA256 = (
    "2dea9bedb7341ea61c8d8f0828e4eee2ac9dba0f5f60fe9094bde589ce896e66"
)
LEGACY_RECEIPT_FIELDS = {
    "schema", "baselineMode", "migrationVersion", "migrationDescription",
    "runId", "sourceCommit", "targetHost", "serviceUnit", "database",
    "databaseEndpoint", "databaseServerUuid",
    "serverVersion", "serverVersionComment", "fingerprintAlgorithm",
    "sourceFacts", "sourceFactsSha256", "preAdoptionLiveFacts",
    "postAdoptionBusinessProjectionFacts", "controlOverlaySha256",
    "backupRunId", "backupSourceCommit", "backupBundleReceiptSha256",
    "backupReceiptSha256", "restoreReceiptSha256", "backupSha256",
    "backupSizeBytes", "sourceJarSha256", "approvalReceiptSha256",
    "runnerSha256", "workerSha256", "canonicalHistoryClaimed",
    "publicInit035Through042ReceiptsWritten",
    "productionBusinessStateChanged", "productionServiceChanged",
    "officialExpertsPackageChanged", "observedAt",
}

def legacy_projection_predicate(alias=""):
    prefix = alias + "." if alias else ""
    quoted = ",".join(
        "'{}'".format(name)
        for name in LEGACY_PROJECTION_EXCLUDED_TABLES
    )
    return "{}table_name NOT IN ({})".format(prefix, quoted)

def legacy_metadata_queries():
    table_filter = legacy_projection_predicate()
    tc_filter = legacy_projection_predicate("tc")
    controls = ",".join(
        "'{}'".format(name)
        for name in LEGACY_PROJECTION_EXCLUDED_TABLES
    )
    return [
        """SELECT CONCAT_WS('|','T',HEX(table_name),HEX(table_type),
        HEX(COALESCE(engine,'')),HEX(COALESCE(table_collation,'')))
        FROM information_schema.tables WHERE table_schema=DATABASE()
        AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','C',HEX(table_name),LPAD(ordinal_position,6,'0'),
        HEX(column_name),HEX(column_type),HEX(is_nullable),
        HEX(COALESCE(column_default,'<NULL>')),HEX(extra),
        HEX(COALESCE(character_set_name,'')),HEX(COALESCE(collation_name,'')),
        HEX(COALESCE(generation_expression,'')))
        FROM information_schema.columns WHERE table_schema=DATABASE()
        AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','I',HEX(table_name),HEX(index_name),
        LPAD(seq_in_index,6,'0'),non_unique,HEX(COALESCE(column_name,'')),
        HEX(COALESCE(collation,'')),COALESCE(sub_part,-1),
        HEX(COALESCE(index_type,'')),HEX(COALESCE(expression,'')))
        FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','K',HEX(table_name),HEX(constraint_name),
        HEX(constraint_type)) FROM information_schema.table_constraints
        WHERE table_schema=DATABASE() AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','U',HEX(table_name),HEX(constraint_name),
        LPAD(ordinal_position,6,'0'),HEX(COALESCE(column_name,'')),
        HEX(COALESCE(referenced_table_name,'')),
        HEX(COALESCE(referenced_column_name,'')))
        FROM information_schema.key_column_usage
        WHERE table_schema=DATABASE() AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','F',HEX(constraint_name),HEX(table_name),
        HEX(referenced_table_name),HEX(update_rule),HEX(delete_rule),
        HEX(match_option)) FROM information_schema.referential_constraints
        WHERE constraint_schema=DATABASE() AND {} AND
        referenced_table_name NOT IN ({})""".format(table_filter, controls),
        """SELECT CONCAT_WS('|','H',HEX(tc.table_name),HEX(cc.constraint_name),
        HEX(cc.check_clause)) FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema=cc.constraint_schema
         AND tc.constraint_name=cc.constraint_name
         AND tc.constraint_type='CHECK'
        WHERE tc.table_schema=DATABASE() AND {}""".format(tc_filter),
        """SELECT CONCAT_WS('|','V',HEX(table_name),HEX(view_definition),
        HEX(check_option),HEX(is_updatable),HEX(definer),HEX(security_type),
        HEX(character_set_client),HEX(collation_connection))
        FROM information_schema.views WHERE table_schema=DATABASE()
        AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','G',HEX(trigger_name),HEX(event_manipulation),
        HEX(event_object_table),HEX(action_timing),HEX(action_orientation),
        HEX(action_statement),HEX(definer))
        FROM information_schema.triggers WHERE trigger_schema=DATABASE()
        AND event_object_table NOT IN ({})""".format(controls),
        """SELECT CONCAT_WS('|','R',HEX(routine_name),HEX(routine_type),
        HEX(COALESCE(data_type,'')),HEX(COALESCE(routine_definition,'')),
        HEX(is_deterministic),HEX(sql_data_access),HEX(security_type),HEX(definer))
        FROM information_schema.routines WHERE routine_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','E',HEX(event_name),HEX(event_definition),
        HEX(event_type),HEX(COALESCE(execute_at,'')),
        HEX(COALESCE(interval_value,'')),HEX(COALESCE(interval_field,'')),
        HEX(status),HEX(definer))
        FROM information_schema.events WHERE event_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','P',HEX(table_name),
        IF(partition_name IS NULL,'N',CONCAT('V',HEX(partition_name))),
        IF(subpartition_name IS NULL,'N',CONCAT('V',HEX(subpartition_name))),
        LPAD(partition_ordinal_position,6,'0'),
        IF(subpartition_ordinal_position IS NULL,'N',
          CONCAT('V',LPAD(subpartition_ordinal_position,6,'0'))),
        IF(partition_method IS NULL,'N',CONCAT('V',HEX(partition_method))),
        IF(subpartition_method IS NULL,'N',CONCAT('V',HEX(subpartition_method))),
        IF(partition_expression IS NULL,'N',CONCAT('V',HEX(partition_expression))),
        IF(subpartition_expression IS NULL,'N',
          CONCAT('V',HEX(subpartition_expression))),
        IF(partition_description IS NULL,'N',
          CONCAT('V',HEX(partition_description))))
        FROM information_schema.partitions WHERE table_schema=DATABASE()
        AND {}""".format(table_filter),
        """SELECT CONCAT_WS('|','A',
        IF(specific_name IS NULL,'N',CONCAT('V',HEX(specific_name))),
        LPAD(ordinal_position,6,'0'),
        IF(parameter_mode IS NULL,'N',CONCAT('V',HEX(parameter_mode))),
        IF(parameter_name IS NULL,'N',CONCAT('V',HEX(parameter_name))),
        HEX(data_type),HEX(dtd_identifier))
        FROM information_schema.parameters WHERE specific_schema=DATABASE()""",
    ]

def legacy_metadata_rows():
    rows = []
    for statement in legacy_metadata_queries():
        rows.extend(row for row in query(statement).splitlines() if row)
    return sorted(rows)

def legacy_projection_facts():
    controls = ",".join(
        "'{}'".format(name)
        for name in LEGACY_PROJECTION_EXCLUDED_TABLES
    )
    counts = query("""SELECT
      SUM(table_type='BASE TABLE'),SUM(table_type='VIEW'),
      SUM(table_type='BASE TABLE' AND engine<>'InnoDB')
      FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name NOT IN ({})""".format(controls)).split("\t")
    object_counts = query("""SELECT
      (SELECT COUNT(*) FROM information_schema.triggers
       WHERE trigger_schema=DATABASE()
       AND event_object_table NOT IN ({})),
      (SELECT COUNT(*) FROM information_schema.routines
       WHERE routine_schema=DATABASE()),
      (SELECT COUNT(*) FROM information_schema.events
       WHERE event_schema=DATABASE())""".format(controls)).split("\t")
    root_rows = query("""SELECT CONCAT_WS('|',HEX(menu_name),parent_id,
      HEX(COALESCE(path,'')),HEX(COALESCE(component,'')),
      HEX(COALESCE(perms,''))) FROM sys_menu
      WHERE parent_id=0 AND HEX(menu_name) IN
        ('E78BACE891A3E4BC9A',
         '496E646570656E64656E7420426F617264')
      ORDER BY BINARY menu_name,BINARY path""")
    sys_menu_shape = query("""SELECT CONCAT_WS('|',
      LPAD(ordinal_position,6,'0'),HEX(column_name),HEX(column_type),
      HEX(is_nullable),HEX(COALESCE(column_default,'<NULL>')),HEX(extra))
      FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='sys_menu'
      ORDER BY ordinal_position""")
    return {
      "fingerprintAlgorithm": "u3w.mysql-schema-metadata.v2",
      "schemaFingerprintSha256": hashlib.sha256(
        ("\n".join(legacy_metadata_rows()) + "\n").encode()
      ).hexdigest(),
      "prerequisiteShapeSha256": hashlib.sha256(
        (sys_menu_shape + "\n--ROOTS--\n" + root_rows + "\n").encode()
      ).hexdigest(),
      "baseTableCount": int(counts[0] or 0),
      "viewCount": int(counts[1] or 0),
      "triggerCount": int(object_counts[0] or 0),
      "routineCount": int(object_counts[1] or 0),
      "eventCount": int(object_counts[2] or 0),
      "independentBoardAdminRootCount": len(
        [row for row in root_rows.splitlines() if row]
      ),
      "allBaseTablesInnoDB": int(counts[2] or 0) == 0,
    }

def legacy_control_overlay_sha256():
    controls = ",".join("'{}'".format(name) for name in CONTROL_TABLES)
    statements = [
      """SELECT CONCAT_WS('|','T',HEX(table_name),HEX(table_type),
      HEX(COALESCE(engine,'')),HEX(COALESCE(table_collation,'')),
      HEX(COALESCE(create_options,'')),HEX(COALESCE(table_comment,'')))
      FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ({})""".format(controls),
      """SELECT CONCAT_WS('|','C',HEX(table_name),LPAD(ordinal_position,6,'0'),
      HEX(column_name),HEX(column_type),HEX(is_nullable),
      HEX(COALESCE(column_default,'<NULL>')),HEX(extra),
      HEX(COALESCE(character_set_name,'')),HEX(COALESCE(collation_name,'')),
      HEX(COALESCE(generation_expression,'')))
      FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name IN ({})""".format(controls),
      """SELECT CONCAT_WS('|','I',HEX(table_name),HEX(index_name),
      LPAD(seq_in_index,6,'0'),non_unique,HEX(COALESCE(column_name,'')),
      HEX(COALESCE(collation,'')),COALESCE(sub_part,-1),
      HEX(COALESCE(index_type,'')),HEX(COALESCE(expression,'')))
      FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name IN ({})""".format(controls),
      """SELECT CONCAT_WS('|','K',HEX(table_name),HEX(constraint_name),
      HEX(constraint_type),HEX(COALESCE(enforced,'')))
      FROM information_schema.table_constraints
      WHERE table_schema=DATABASE() AND table_name IN ({})""".format(controls),
      """SELECT CONCAT_WS('|','U',HEX(table_name),HEX(constraint_name),
      LPAD(ordinal_position,6,'0'),HEX(COALESCE(column_name,'')),
      COALESCE(position_in_unique_constraint,-1),
      HEX(COALESCE(referenced_table_schema,'')),
      HEX(COALESCE(referenced_table_name,'')),
      HEX(COALESCE(referenced_column_name,'')))
      FROM information_schema.key_column_usage
      WHERE table_schema=DATABASE() AND table_name IN ({})""".format(controls),
      """SELECT CONCAT_WS('|','F',HEX(constraint_name),HEX(table_name),
      HEX(COALESCE(unique_constraint_schema,'')),
      HEX(COALESCE(unique_constraint_name,'')),
      HEX(COALESCE(referenced_table_name,'')),HEX(update_rule),
      HEX(delete_rule),HEX(match_option))
      FROM information_schema.referential_constraints
      WHERE constraint_schema=DATABASE() AND table_name IN ({})""".format(
        controls
      ),
      """SELECT CONCAT_WS('|','H',HEX(tc.table_name),HEX(cc.constraint_name),
      HEX(cc.check_clause)) FROM information_schema.check_constraints cc
      JOIN information_schema.table_constraints tc
        ON tc.constraint_schema=cc.constraint_schema
       AND tc.constraint_name=cc.constraint_name
       AND tc.constraint_type='CHECK'
      WHERE tc.table_schema=DATABASE()
      AND tc.table_name IN ({})""".format(controls),
      """SELECT CONCAT_WS('|','G',HEX(trigger_name),HEX(event_manipulation),
      HEX(event_object_table),HEX(action_timing),HEX(action_orientation),
      HEX(action_statement),HEX(definer))
      FROM information_schema.triggers WHERE trigger_schema=DATABASE()
      AND event_object_table IN ({})""".format(controls),
      """SELECT CONCAT_WS('|','P',HEX(table_name),
      IF(partition_name IS NULL,'N',CONCAT('V',HEX(partition_name))),
      IF(subpartition_name IS NULL,'N',CONCAT('V',HEX(subpartition_name))),
      LPAD(partition_ordinal_position,6,'0'),
      IF(subpartition_ordinal_position IS NULL,'N',
        CONCAT('V',LPAD(subpartition_ordinal_position,6,'0'))),
      IF(partition_method IS NULL,'N',CONCAT('V',HEX(partition_method))),
      IF(subpartition_method IS NULL,'N',
        CONCAT('V',HEX(subpartition_method))),
      IF(partition_expression IS NULL,'N',
        CONCAT('V',HEX(partition_expression))),
      IF(subpartition_expression IS NULL,'N',
        CONCAT('V',HEX(subpartition_expression))),
      IF(partition_description IS NULL,'N',
        CONCAT('V',HEX(partition_description))))
      FROM information_schema.partitions WHERE table_schema=DATABASE()
      AND table_name IN ({})""".format(controls),
    ]
    rows = []
    for statement in statements:
        rows.extend(row for row in query(statement).splitlines() if row)
    return hashlib.sha256(
      ("\n".join(sorted(rows)) + "\n").encode()
    ).hexdigest() if rows else None

class RejectRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(
        self, request, file_pointer, code, message, headers, new_url
    ):
        return None

NO_REDIRECT_OPENER = urllib.request.build_opener(RejectRedirect())

def http_status(url):
    request = urllib.request.Request(url, method="GET")
    try:
        with NO_REDIRECT_OPENER.open(request, timeout=10) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code
    except Exception:
        return None

def http_json_status(url):
    request = urllib.request.Request(
        url, method="GET", headers={"User-Agent": "u3w-readiness/1"}
    )
    try:
        with NO_REDIRECT_OPENER.open(request, timeout=10) as response:
            status = response.status
            payload = response.read(1024 * 1024)
    except urllib.error.HTTPError as error:
        status = error.code
        payload = error.read(1024 * 1024)
    except Exception:
        return {"httpStatus": None, "businessCode": None}
    try:
        parsed = json.loads(payload.decode("utf-8"))
        code = parsed.get("code") if isinstance(parsed, dict) else None
    except (UnicodeError, json.JSONDecodeError):
        code = None
    return {"httpStatus": status, "businessCode": code}

def http_json_document(url):
    request = urllib.request.Request(
        url, method="GET", headers={"User-Agent": "u3w-readiness/1"}
    )
    try:
        with NO_REDIRECT_OPENER.open(request, timeout=10) as response:
            status = response.status
            payload = response.read(1024 * 1024)
    except urllib.error.HTTPError as error:
        status = error.code
        payload = error.read(1024 * 1024)
    except Exception:
        return {"httpStatus": None, "document": None}
    try:
        document = json.loads(payload.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError):
        document = None
    return {"httpStatus": status, "document": document}

def post_signed_attribution_probe(event):
    if not isinstance(event, dict):
        raise RuntimeError("signed attribution probe event is invalid")
    url = "http://127.0.0.1:8080" + ATTRIBUTION_INGRESS_PATH
    request = urllib.request.Request(
        url,
        data=canonical_json(event).encode("utf-8"),
        method="POST",
        headers={
            "User-Agent":
                "u3w-readiness-signed-disabled-route/1",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    try:
        with NO_REDIRECT_OPENER.open(request, timeout=10) as response:
            response.read(1024 * 1024)
            return response.status
    except urllib.error.HTTPError as error:
        error.read(1024 * 1024)
        return error.code
    except (OSError, TimeoutError, urllib.error.URLError) as error:
        raise RuntimeError(
            "signed attribution probe request failed"
        ) from error


def build_signed_attribution_probe_event(environment, observed=None):
    key_id = str(environment.get(EVENT_KEY_ID_NAME, "")).strip()
    key_material = decode_secret_material(
        environment.get(EVENT_KEY_NAME)
    )
    if (
        re.fullmatch(
            r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}",
            key_id,
        ) is None
        or key_material is None
        or len(key_material) < 32
    ):
        raise RuntimeError("attribution probe signing key is invalid")
    observed = observed or datetime.now(timezone.utc)
    if observed.tzinfo is None:
        raise RuntimeError("attribution probe time is invalid")
    observed = observed.astimezone(timezone.utc)
    entropy = os.urandom(32)

    def identifier(label):
        return hashlib.sha256(
            b"fbsir.u3wDefaultOffSignedProbe.v1\0"
            + label.encode("ascii")
            + b"\0"
            + entropy
        ).hexdigest()

    def timestamp(value):
        return (
            value.astimezone(timezone.utc)
            .isoformat(timespec="milliseconds")
            .replace("+00:00", "Z")
        )

    nonce = "u3w.default.off.probe." + identifier("nonce")[:32]
    event = {
        "schemaVersion":
            "fbsir.independentBoardAttributionEvent.v1",
        "eventId": identifier("event"),
        "receiptId": identifier("receipt"),
        "contractId": "FBSIR_INDEPENDENT_BOARD_W1A_V1",
        "eventType": "ENTRY_OBSERVED",
        "sequenceNo": 1,
        "occurredAt": timestamp(observed),
        "productId": "fbsir-eight-seat-board",
        "packageId": "fbsir-eight-seat-board",
        "agentName": "board-convener",
        "marketplace": "experts",
        "listedSurface": "listed_runtime_state",
        "listedManifestVersion": "26.7.21",
        "embeddedContractVersion": "26.7.20",
        "hostClientFamily": "WORKBUDDY",
        "hostVersion": "UNKNOWN",
        "terminal": "UNKNOWN",
        "channel": "OFFICIAL_EXPERTS",
        "requestSource": "UNKNOWN",
        "intentSignal": "default_off_probe",
        "classificationSource": "SERVER_CLASSIFIER",
        "classifierVersion": "u3w.default.off.probe.v1",
        "confidenceBucket": "UNKNOWN",
        "reviewMode": "UNKNOWN",
        "journeyId": identifier("journey"),
        "serverBindingId": "srv_" + identifier("binding")[:32],
        "sameBindingKey": "",
        "tenantSubjectDigest": identifier("tenant"),
        "trafficClass": "PROBE",
        "trafficAuthority": "API2_SERVER_CLASSIFIER_V1",
        "outcome": "WITHHELD",
        "previousEventDigest": "",
        "traceparent": "",
        "rawContentStored": False,
        "issuedAt": timestamp(observed),
        "expiresAt": timestamp(observed + timedelta(seconds=60)),
        "nonce": nonce,
        "keyId": key_id,
        "signatureAlgorithm": "hmac-sha256-v1",
    }
    signed_fields = {
        name: str(event[name]).strip()
        for name in ATTRIBUTION_EVENT_SIGNED_STRING_FIELDS
    }
    signed_fields["rawContentStored"] = "false"
    signed_fields["sequenceNo"] = "1"
    event["signature"] = "v1=" + hmac.new(
        key_material,
        canonical_json(signed_fields).encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    identity = {
        "eventId": event["eventId"],
        "receiptId": event["receiptId"],
        "nonceHash": hashlib.sha256(
            nonce.encode("utf-8")
        ).hexdigest(),
        "journeyId": event["journeyId"],
    }
    return event, identity


def attribution_probe_identity_counts(query, identity):
    if (
        not isinstance(identity, dict)
        or set(identity)
            != {"eventId", "receiptId", "nonceHash", "journeyId"}
        or any(
            re.fullmatch(r"[0-9a-f]{64}", str(value or ""))
                is None
            for value in identity.values()
        )
    ):
        raise RuntimeError("attribution probe identity is invalid")
    statements = {
        "eventId":
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE BINARY event_id=BINARY '{}'",
        "receiptId":
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE BINARY receipt_id=BINARY '{}'",
        "nonceHash":
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE BINARY nonce_hash=BINARY '{}'",
        "journeyId":
            "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
            "WHERE BINARY journey_id=BINARY '{}'",
    }
    return {
        name: int(query(statement.format(identity[name])))
        for name, statement in statements.items()
    }


def attribution_global_counts(query):
    return {
        "eventCount": int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1"
        )),
        "journeyCount": int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_journey_v1"
        )),
    }


def disabled_attribution_ingress_probe(
    query,
    environment,
):
    event, identity = build_signed_attribution_probe_event(
        environment
    )
    zero_counts = {name: 0 for name in identity}
    identity_before = attribution_probe_identity_counts(
        query,
        identity,
    )
    if identity_before != zero_counts:
        raise RuntimeError(
            "signed attribution probe identity is not unique"
        )
    global_before = attribution_global_counts(query)
    http_status = post_signed_attribution_probe(event)
    identity_after = attribution_probe_identity_counts(
        query,
        identity,
    )
    global_after = attribution_global_counts(query)
    verified = bool(
        http_status == DISABLED_INGRESS_HTTP_STATUS
        and identity_after == zero_counts
        and global_after == global_before
    )
    return {
        "schema": "fbsir.u3wSignedDisabledAttributionIngressProbe.v1",
        "path": ATTRIBUTION_INGRESS_PATH,
        "method": "POST",
        "httpStatus": http_status,
        "responseDisposition": (
            "ROUTE_NOT_FOUND" if http_status == 404 else "REJECTED"
        ),
        "verifiedDisabled": verified,
        "acceptedDisabledHttpStatuses":
            [DISABLED_INGRESS_HTTP_STATUS],
        "trafficClass": event["trafficClass"],
        "signingKeyId": event["keyId"],
        "signatureAlgorithm": event["signatureAlgorithm"],
        "probeIdentity": identity,
        "identityCountsBefore": identity_before,
        "identityCountsAfter": identity_after,
        "globalCountsBefore": global_before,
        "globalCountsAfter": global_after,
        "rawNonceDisclosed": False,
        "rawSignatureDisclosed": False,
        "signingKeyMaterialDisclosed": False,
        "secretsDisclosed": False,
        "observedAt": datetime.now(timezone.utc).isoformat(),
    }

def spring_external_config_manifest():
    admin_root = pathlib.Path("/opt/fbsir/admin")
    name_pattern = re.compile(
        r"application(?:-[A-Za-z0-9._-]+)?\.(?:yml|yaml|properties)",
        re.I,
    )
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
                    "Spring default config directory custody is invalid"
                )
            if any((current_path / name).is_symlink()
                   for name in directories):
                raise RuntimeError(
                    "Spring default config tree contains a symlink"
                )
            candidates.extend(
                current_path / name
                for name in files
                if name_pattern.fullmatch(name)
            )
    expected = pathlib.Path(
        "/opt/fbsir/admin/application-connector.yml"
    ).resolve()
    manifest = []
    seen = set()
    for candidate in candidates:
        status = candidate.lstat()
        resolved = candidate.resolve()
        if (
            not stat.S_ISREG(status.st_mode)
            or stat.S_ISLNK(status.st_mode)
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_nlink != 1
            or status.st_mode & 0o777 not in (0o600, 0o640, 0o644)
            or resolved != expected
            or resolved in seen
        ):
            raise RuntimeError(
                "unreviewed Spring external config is present"
            )
        seen.add(resolved)
        manifest.append({
            "path": str(candidate),
            "sha256": sha256_file(candidate),
            "mode": status.st_mode & 0o777,
        })
    if seen != {expected}:
        raise RuntimeError("reviewed Spring additional config is absent")
    return sorted(manifest, key=lambda item: item["path"])

lock_path = pathlib.Path("/opt/fbsir/admin/.u3w-production-change.lock")
lock_descriptor = os.open(
    lock_path,
    os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
)
lock_status = os.fstat(lock_descriptor)
if (
    not stat.S_ISREG(lock_status.st_mode)
    or lock_status.st_uid != 0
    or lock_status.st_gid != 0
    or lock_status.st_nlink != 1
    or lock_status.st_mode & 0o777 != 0o600
):
    raise RuntimeError("production change lock custody is invalid")
fcntl.flock(lock_descriptor, fcntl.LOCK_SH)

show = run([
    "systemctl", "show", SERVICE_UNIT,
    (
      "--property=FragmentPath,DropInPaths,EnvironmentFiles,ActiveState,SubState,"
      "ExecStart,MainPID,User,Group,WorkingDirectory,InvocationID,"
      "ExecMainStartTimestampMonotonic,NRestarts"
    )
])
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

environment_declaration = normalized_environment_file(
    service.get("EnvironmentFiles")
)
environment_file_path = environment_declaration["path"]
environment_file_manifest = regular_file_manifest(
    environment_file_path, allowed_modes=(0o600,)
)
environment_file_manifest["ignoreErrors"] = (
    environment_declaration["ignoreErrors"]
)
environment_file_paths = [environment_file_path]
environment_file_manifests = [environment_file_manifest]
environment = parse_env_file(environment_file_path)
if any(environment.get(name) != "false" for name in FLAG_NAMES):
    raise RuntimeError("W1A flags are not explicitly false")
if any(not environment.get(name) for name in DATABASE_ENVIRONMENT_NAMES):
    raise RuntimeError("expected process database credentials are absent")

exec_start = service.get("ExecStart", "")
jar_match = re.search(r"(/[A-Za-z0-9._/-]+\.jar)", exec_start)
configured_jar_path = jar_match.group(1) if jar_match else None
main_pid = int(service.get("MainPID") or "0")
process_jar_path = None
process_arguments = []
process_environment = {}
process_flag_values = {name: None for name in FLAG_NAMES}
process_forbidden_override_names = []
if main_pid > 0:
    process_cmdline_path = (
        pathlib.Path("/proc") / str(main_pid) / "cmdline"
    )
    process_cmdline_status = process_cmdline_path.stat()
    process_arguments = [
        value.decode("utf-8", errors="strict")
        for value in process_cmdline_path.read_bytes().split(b"\0")
        if value
    ]
    if (
        process_cmdline_status.st_uid != 0
        or "-jar" not in process_arguments
    ):
        raise RuntimeError("active U3W process command line is invalid")
    jar_index = process_arguments.index("-jar") + 1
    for argument in process_arguments[jar_index:]:
        if argument.startswith("/") and argument.endswith(".jar"):
            process_jar_path = argument
            break
    process_environment_path = (
        pathlib.Path("/proc") / str(main_pid) / "environ"
    )
    process_environment_status = process_environment_path.stat()
    if process_environment_status.st_uid != 0:
        raise RuntimeError("active U3W process environment owner is invalid")
    for item in process_environment_path.read_bytes().split(b"\0"):
        if not item:
            continue
        name_bytes, separator, value_bytes = item.partition(b"=")
        if not separator:
            raise RuntimeError("active U3W process environment is invalid")
        name = name_bytes.decode("utf-8", errors="strict")
        value = value_bytes.decode("utf-8", errors="strict")
        if name in process_environment:
            raise RuntimeError(
                "active U3W process environment has duplicate keys"
            )
        process_environment[name] = value
    process_flag_values = {
        name: process_environment.get(name) for name in FLAG_NAMES
    }
    normalized_flag_names = {
        re.sub(r"[^a-z0-9]", "", name.lower())
        for name in FLAG_NAMES
    }
    normalized_java_override_names = {
        re.sub(r"[^a-z0-9]", "", name.lower())
        for name in (
            "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS",
            "JDK_JAVA_OPTIONS",
        )
    }
    process_forbidden_override_names = sorted(
        name
        for name in process_environment
        if (
            re.sub(r"[^a-z0-9]", "", name.lower()).startswith(
                "spring"
            )
            or (
                name not in FLAG_NAMES
                and re.sub(r"[^a-z0-9]", "", name.lower())
                    in normalized_flag_names
            )
            or re.sub(r"[^a-z0-9]", "", name.lower())
                in normalized_java_override_names
        )
    )
if service.get("ActiveState") == "active" and not process_jar_path:
    raise RuntimeError("active U3W process JAR is absent")
expected_process_environment = parse_env_file(
    environment_file_path
)
process_security_evidence = security_configuration_evidence(
    process_environment, expected_process_environment
)
process_environment_names_sha256 = (
    hashlib.sha256(
        (
            "\n".join(sorted(process_environment)) + "\n"
        ).encode("utf-8")
    ).hexdigest()
    if process_environment else None
)
fragment_file_manifest = regular_file_manifest(
    service.get("FragmentPath")
)
process_argv_sha256 = (
    hashlib.sha256(
        ("\0".join(process_arguments) + "\0").encode("utf-8")
    ).hexdigest()
    if process_arguments else None
)
jar_path = process_jar_path or configured_jar_path
jar_digest = sha256_file(jar_path) if jar_path and pathlib.Path(jar_path).is_file() else None
external_config_manifest = spring_external_config_manifest()
current_dropin_manifest = []
release_dropin_manifest = []
for value in service.get("DropInPaths", "").split():
    entry = regular_file_manifest(value)
    current_dropin_manifest.append({
        "path": entry["path"],
        "sha256": entry["sha256"],
        "mode": entry["mode"],
    })
    release_dropin_manifest.append(entry)
current_dropin_manifest.sort(key=lambda item: item["path"])
release_dropin_manifest.sort(key=lambda item: item["path"])
live_service_snapshot = {
    "activeState": service.get("ActiveState"),
    "mainPid": main_pid,
    "user": service.get("User") or "root",
    "group": service.get("Group") or "root",
    "fragmentPath": service.get("FragmentPath"),
    "fragmentFileManifest": fragment_file_manifest,
    "environmentFiles": service.get("EnvironmentFiles"),
    "environmentFilePaths": environment_file_paths,
    "environmentFileManifest": environment_file_manifests,
    "execStart": service.get("ExecStart"),
    "workingDirectory": service.get("WorkingDirectory"),
    "invocationId": service.get("InvocationID", "").lower(),
    "execMainStartTimestampMonotonic": int(
        service.get("ExecMainStartTimestampMonotonic") or "0"
    ),
    "nRestarts": int(service.get("NRestarts") or "0"),
    "configuredJarPath": configured_jar_path,
    "configuredJarSha256": (
        sha256_file(configured_jar_path)
        if configured_jar_path
        and pathlib.Path(configured_jar_path).is_file()
        else None
    ),
    "processJarPath": process_jar_path,
    "processJarSha256": (
        sha256_file(process_jar_path)
        if process_jar_path
        and pathlib.Path(process_jar_path).is_file()
        else None
    ),
    "processArgvSha256": process_argv_sha256,
    "processEnvironmentNamesSha256":
        process_environment_names_sha256,
    "processFlagValues": process_flag_values,
    "configuredFlagValues":
        process_security_evidence["configuredFlagValues"],
    "processForbiddenOverrideNames":
        process_forbidden_override_names,
    "dropInManifest": current_dropin_manifest,
    "externalConfigManifest": external_config_manifest,
    "additionalConfigSha256": external_config_manifest[0]["sha256"],
    "jarPath": jar_path,
    "jarSha256": jar_digest,
}
live_service_snapshot.update(process_security_evidence)
attribution_class_count = None
if jar_path and pathlib.Path(jar_path).is_file():
    with zipfile.ZipFile(jar_path) as archive:
        attribution_class_count = sum(
            1 for name in archive.namelist()
            if "/business/board/attribution/" in name and name.endswith(".class")
        )

database = {
    "serverVersion": None,
    "database": None,
    "currentUser": None,
    "totalTableCount": None,
    "migrationTableCount": None,
    "migrationVersions": [],
    "migrationDescriptions": {},
    "boardAttributionTableCount": None,
    "boardAttributionTriggerCount": None,
    "boardAttributionPermissionCount": None,
    "boardAttributionInternalReceiptCount": None,
    "boardAttributionEventCount": None,
    "boardAttributionProbeEventCount": None,
    "boardAttributionNaturalEventCount": None,
    "boardAttributionNonProbeEventCount": None,
    "boardAttributionAuthoritativeProductCreditCount": None,
    "boardAttributionJourneyCount": None,
    "boardAttributionProbeJourneyCount": None,
    "boardAttributionNaturalJourneyCount": None,
    "boardAttributionNonProbeJourneyCount": None,
    "publicInit043Applied": None,
    "publicInit043AnyReceiptCount": None,
    "w1a043State": None,
    "w1aSchemaFingerprintSha256": None,
    "schemaBaselineMode": None,
    "legacyBaselineReceiptValid": False,
    "legacyBaselineReceiptAnchorMatched": False,
    # Intentionally false until a controlled runner and this collector
    # independently recompute every receipt field from live artifacts.
    "legacyBaselineLiveFactsMatched": False,
    "legacyAdminRootDependencyState": None,
    "legacyAdminRootDependencyStateVerified": False,
    "legacyAdminRootDependencyFactsSha256": None,
    "legacyAdminRootDependencyReceiptCount": 0,
    "legacyAdminRootDependencyVersionCount": 0,
    "legacyAdminRootIdentityCount": 0,
    "legacyAdminRootExactCount": 0,
    "legacyAdminRootRoleBindingCount": 0,
    "legacyAdminRootPageChildCount": 0,
    "legacyForbiddenPublicInit001Through042ReceiptCount": 0,
    "legacyBaselineReceiptDigest": None,
    "legacyBaselineReceiptSha256": None,
    "legacyBaselineMigrationCount": 0,
    "legacyBaselineSourceCommit": None,
    "legacyBaselineBackupBundleReceiptSha256": None,
    "adminRootDependencyAdoptionReceiptPath":
        ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_PATH,
    "adminRootDependencyAdoptionReceiptSchema": None,
    "adminRootDependencyAdoptionRunId": None,
    "adminRootDependencyAdoptionReceiptSha256": None,
    "adminRootDependencyAdoptionReceiptValid": False,
    "adminRootDependencyAdoptionReceiptAnchorMatched": False,
    "adminRootDependencyAdoptionSourceCommit": None,
    "adminRootDependencyAdoptionDatabaseServerUuid": None,
    "adminRootDependencyAdoptionLiveFactsSha256": None,
    "adminRootDependencyAdoptionDependencyRowsFingerprintSha256": None,
    "adminRootDependencyAdoptionW1a043State": None,
    "adminRootDependencyAdoptionPlanReceiptSha256": None,
    "adminRootDependencyAdoptionApprovalReceiptSha256": None,
    "adminRootDependencyAdoptionRunnerSha256": None,
    "adminRootDependencyAdoptionWorkerSha256": None,
    "adminRootDependencyAdoptionLiveFactsMatched": False,
    "adminRootDependencyAdoptionBaselineReceiptSha256": None,
    "adminRootDependencyAdoptionBackupReceiptSha256": None,
    "adminRootDependencyAdoptionHistoricalBindingsMatched": False,
}
jdbc_url = environment.get("WXFBSIR_MYSQL_URL")
mysql_user = environment.get("WXFBSIR_MYSQL_USERNAME")
mysql_password = environment.get("WXFBSIR_MYSQL_PASSWORD")
database_aliases = (
    environment.get("FBSIR_MYSQL_URL"),
    environment.get("FBSIR_MYSQL_USERNAME"),
    environment.get("FBSIR_MYSQL_PASSWORD"),
)
query = None
if any(database_aliases) and (
    not all(database_aliases)
    or database_aliases != (jdbc_url, mysql_user, mysql_password)
):
    raise RuntimeError("database credential aliases drifted")
if jdbc_url and mysql_user is not None and mysql_password is not None:
    match = re.match(
        r"^jdbc:mysql://(?P<host>[A-Za-z0-9._-]+)(?::(?P<port>[0-9]+))?/(?P<db>[A-Za-z0-9_]+)",
        jdbc_url,
    )
    if not match:
        raise RuntimeError("unsupported JDBC URL shape")
    host = match.group("host")
    port = match.group("port") or "3306"
    database_name = match.group("db")
    mysql_env = os.environ.copy()
    mysql_env["MYSQL_PWD"] = mysql_password

    def query(sql):
        return run([
            "mysql", "--protocol=tcp", "-h", host, "-P", port,
            "-u", mysql_user, "-D", database_name,
            "--batch", "--skip-column-names", "--raw", "-e", sql,
        ], env=mysql_env)

    identity = query(
        "SELECT @@version, DATABASE(), CURRENT_USER(), @@server_uuid"
    ).split("\t")
    sys_menu_table_count = int(query(
        "SELECT COUNT(*) FROM information_schema.tables "
        "WHERE table_schema=DATABASE() AND table_name='sys_menu'"
    ))
    database.update({
        "serverVersion": identity[0],
        "database": identity[1],
        "currentUser": identity[2],
        "databaseEndpoint": host + ":" + port,
        "databaseServerUuid": identity[3].lower(),
        "totalTableCount": int(query(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema = DATABASE()"
        )),
        "migrationTableCount": int(query(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema = DATABASE() "
            "AND table_name = 'u3w_schema_migration'"
        )),
        "boardAttributionTableCount": int(query(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema = DATABASE() AND table_name IN "
            "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
        )),
        "boardAttributionTriggerCount": int(query(
            "SELECT COUNT(*) FROM information_schema.triggers "
            "WHERE trigger_schema = DATABASE() AND event_object_table IN "
            "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
        )),
        "boardAttributionPermissionCount": (
            int(query(
                "SELECT COUNT(*) FROM sys_menu "
                "WHERE BINARY perms=BINARY 'board:attribution:query'"
            )) if sys_menu_table_count == 1 else 0
        ),
        "boardAttributionInternalReceiptCount": 0,
        "boardAttributionEventCount": 0,
        "boardAttributionProbeEventCount": 0,
        "boardAttributionNaturalEventCount": 0,
        "boardAttributionNonProbeEventCount": 0,
        "boardAttributionAuthoritativeProductCreditCount": 0,
        "boardAttributionJourneyCount": 0,
        "boardAttributionProbeJourneyCount": 0,
        "boardAttributionNaturalJourneyCount": 0,
        "boardAttributionNonProbeJourneyCount": 0,
    })
    if database["migrationTableCount"] == 1:
        database["boardAttributionInternalReceiptCount"] = int(query(
            "SELECT COUNT(*) FROM u3w_schema_migration WHERE version="
            "'20260723_independent_board_attribution_v1_043' AND description="
            "'APPLIED:exact WorkBuddy experts 26.7.21 attribution journey "
            "and append-only event ledger'"
        ))
        migration_rows = query(
            "SELECT version, description FROM u3w_schema_migration "
            "WHERE version LIKE 'public_init_0%' ORDER BY version"
        )
        for row in migration_rows.splitlines():
            if not row:
                continue
            version, description = row.split("\t", 1)
            database["migrationVersions"].append(version)
            database["migrationDescriptions"][version] = description
        database["publicInit043Applied"] = (
            database["migrationDescriptions"].get("public_init_043")
            == "APPLIED:Independent Board exact official experts attribution v1"
        )
        database["publicInit043AnyReceiptCount"] = int(query(
            "SELECT COUNT(*) FROM u3w_schema_migration "
            "WHERE version='public_init_043'"
        ))
        if database["publicInit043Applied"]:
            database["boardAttributionEventCount"] = int(query(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1"
            ))
            database["boardAttributionProbeEventCount"] = int(query(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE BINARY traffic_class=BINARY 'PROBE'"
            ))
            database["boardAttributionNaturalEventCount"] = int(query(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE BINARY traffic_class=BINARY 'NATURAL'"
            ))
            database["boardAttributionNonProbeEventCount"] = int(query(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE BINARY traffic_class<>BINARY 'PROBE'"
            ))
            database[
                "boardAttributionAuthoritativeProductCreditCount"
            ] = int(query(
                "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
                "WHERE authoritative_product_credit<>0"
            ))
            database["boardAttributionJourneyCount"] = int(query(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1"
            ))
            database["boardAttributionProbeJourneyCount"] = int(query(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
                "WHERE BINARY traffic_class=BINARY 'PROBE'"
            ))
            database["boardAttributionNaturalJourneyCount"] = int(query(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
                "WHERE BINARY traffic_class=BINARY 'NATURAL'"
            ))
            database["boardAttributionNonProbeJourneyCount"] = int(query(
                "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
                "WHERE BINARY traffic_class<>BINARY 'PROBE'"
            ))
            fingerprint_rows = query("""
SELECT row_value FROM (
  SELECT CONCAT_WS('|','C',HEX(table_name),LPAD(ordinal_position,3,'0'),
    HEX(column_name),HEX(column_type),is_nullable,
    HEX(COALESCE(column_default,'<NULL>')),HEX(extra),
    HEX(COALESCE(character_set_name,'')),HEX(COALESCE(collation_name,'')),
    HEX(COALESCE(generation_expression,''))) AS row_value
  FROM information_schema.columns
  WHERE table_schema=DATABASE()
    AND table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','I',HEX(table_name),HEX(index_name),non_unique,
    LPAD(seq_in_index,3,'0'),HEX(column_name),
    COALESCE(sub_part,''),HEX(COALESCE(collation,'')),HEX(index_type),
    HEX(nullable))
  FROM information_schema.statistics
  WHERE table_schema=DATABASE()
    AND table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','T',HEX(table_name),HEX(constraint_name),
    HEX(constraint_type))
  FROM information_schema.table_constraints
  WHERE table_schema=DATABASE()
    AND table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','K',HEX(table_name),HEX(constraint_name),
    HEX(column_name),LPAD(ordinal_position,3,'0'),
    HEX(COALESCE(referenced_table_name,'')),
    HEX(COALESCE(referenced_column_name,'')))
  FROM information_schema.key_column_usage
  WHERE table_schema=DATABASE()
    AND table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','F',HEX(constraint_name),HEX(table_name),
    HEX(referenced_table_name),HEX(update_rule),HEX(delete_rule),
    HEX(match_option))
  FROM information_schema.referential_constraints
  WHERE constraint_schema=DATABASE()
    AND table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','H',HEX(tc.table_name),HEX(cc.constraint_name),
    HEX(cc.check_clause))
  FROM information_schema.check_constraints cc
  JOIN information_schema.table_constraints tc
    ON tc.constraint_schema=cc.constraint_schema
   AND tc.constraint_name=cc.constraint_name
   AND tc.constraint_type='CHECK'
  WHERE tc.table_schema=DATABASE()
    AND tc.table_name IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','R',HEX(trigger_name),HEX(event_manipulation),
    HEX(event_object_table),HEX(action_timing),HEX(action_orientation),
    HEX(REGEXP_REPLACE(TRIM(action_statement),'[[:space:]]+',' ')))
  FROM information_schema.triggers
  WHERE trigger_schema=DATABASE()
    AND event_object_table IN
      ('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')
  UNION ALL
  SELECT CONCAT_WS('|','M',HEX(permission.menu_name),
    LPAD(permission.order_num,6,'0'),HEX(COALESCE(permission.path,'')),
    HEX(COALESCE(permission.component,'<NULL>')),
    HEX(COALESCE(permission.query,'<NULL>')),
    HEX(COALESCE(permission.route_name,'')),permission.is_frame,
    permission.is_cache,HEX(permission.menu_type),HEX(permission.visible),
    HEX(permission.status),HEX(permission.perms),HEX(permission.icon),
    HEX(COALESCE(root.menu_name,'<NULL>')),
    LPAD(COALESCE(root.order_num,-1),6,'0'),
    HEX(COALESCE(root.path,'<NULL>')),
    HEX(COALESCE(root.component,'<NULL>')),
    HEX(COALESCE(root.query,'<NULL>')),
    HEX(COALESCE(root.route_name,'<NULL>')),
    COALESCE(root.is_frame,-1),COALESCE(root.is_cache,-1),
    HEX(COALESCE(root.menu_type,'<NULL>')),
    HEX(COALESCE(root.visible,'<NULL>')),
    HEX(COALESCE(root.status,'<NULL>')),
    HEX(COALESCE(root.perms,'<NULL>')),
    HEX(COALESCE(root.icon,'<NULL>')),
    IF(root.parent_id=0,'ROOT','NONROOT'))
  FROM sys_menu permission
  LEFT JOIN sys_menu root ON root.menu_id=permission.parent_id
  WHERE BINARY permission.perms=BINARY 'board:attribution:query'
) AS fingerprint_rows
ORDER BY BINARY row_value
""")
            database["w1aSchemaFingerprintSha256"] = hashlib.sha256(
                (fingerprint_rows + "\n").encode("utf-8")
            ).hexdigest()
        forbidden_public_versions = ",".join(
            "'public_init_{:03d}'".format(index)
            for index in range(1, 43)
        )
        dependency_version = (
            "w1a_043_legacy_admin_root_dependency_20260724_001"
        )
        dependency_description = (
            "APPLIED:W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY_V1"
        )
        dependency_version_count = int(query(
            "SELECT COUNT(*) FROM u3w_schema_migration "
            "WHERE version='" + dependency_version + "'"
        ))
        dependency_receipt_count = int(query(
            "SELECT COUNT(*) FROM u3w_schema_migration "
            "WHERE version='" + dependency_version + "' "
            "AND description='" + dependency_description + "'"
        ))
        forbidden_public_history_count = int(query(
            "SELECT COUNT(*) FROM u3w_schema_migration "
            "WHERE version IN (" + forbidden_public_versions + ")"
        ))
        root_identity_predicate = (
            "HEX(menu_name)="
            "'E78BACE891A3E4BC9AE7AEA1E79086' "
            "OR BINARY path=BINARY 'independent-board-admin' "
            "OR BINARY route_name=BINARY 'IndependentBoardAdmin'"
        )
        exact_root_predicate = (
            "HEX(menu_name)='E78BACE891A3E4BC9AE7AEA1E79086' "
            "AND parent_id=0 AND order_num=5 "
            "AND BINARY path=BINARY 'independent-board-admin' "
            "AND component IS NULL AND query IS NULL "
            "AND BINARY route_name=BINARY 'IndependentBoardAdmin' "
            "AND is_frame=1 AND is_cache=0 "
            "AND BINARY menu_type=BINARY 'M' "
            "AND BINARY visible=BINARY '0' "
            "AND BINARY status=BINARY '0' "
            "AND BINARY COALESCE(perms,'')=BINARY '' "
            "AND BINARY icon=BINARY 'peoples'"
        )
        root_identity_count = int(query(
            "SELECT COUNT(*) FROM sys_menu WHERE "
            + root_identity_predicate
        )) if sys_menu_table_count == 1 else 0
        exact_root_count = int(query(
            "SELECT COUNT(*) FROM sys_menu WHERE "
            + exact_root_predicate
        )) if sys_menu_table_count == 1 else 0
        root_role_binding_count = int(query(
            "SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN "
            "(SELECT menu_id FROM sys_menu WHERE "
            + exact_root_predicate + ")"
        )) if sys_menu_table_count == 1 else 0
        root_page_child_count = int(query(
            "SELECT COUNT(*) FROM sys_menu WHERE menu_type IN ('M','C') "
            "AND parent_id IN (SELECT menu_id FROM sys_menu WHERE "
            + exact_root_predicate + ")"
        )) if sys_menu_table_count == 1 else 0
        dependency_fingerprint_rows = query(
            "SELECT row_value FROM ("
            "SELECT CONCAT_WS('|','D',HEX(version),HEX(description)) "
            "row_value FROM u3w_schema_migration WHERE version='"
            + dependency_version
            + "' UNION ALL SELECT CONCAT_WS('|','R',menu_id,"
            "HEX(menu_name),parent_id,order_num,"
            "HEX(COALESCE(path,'')),"
            "HEX(COALESCE(component,'<NULL>')),"
            "HEX(COALESCE(query,'<NULL>')),"
            "HEX(COALESCE(route_name,'')),is_frame,is_cache,"
            "HEX(menu_type),HEX(visible),HEX(status),"
            "HEX(COALESCE(perms,'')),HEX(icon)) "
            "FROM sys_menu WHERE " + exact_root_predicate
            + ") rows_ ORDER BY BINARY row_value"
        )
        dependency_facts_sha256 = hashlib.sha256(
            (dependency_fingerprint_rows + "\n").encode("utf-8")
        ).hexdigest()
        dependency_absent = bool(
            dependency_version_count == 0
            and dependency_receipt_count == 0
            and root_identity_count == 0
            and exact_root_count == 0
            and root_role_binding_count == 0
            and root_page_child_count == 0
            and len([
                row for row in dependency_fingerprint_rows.splitlines()
                if row
            ]) == 2
        )
        dependency_exact = bool(
            dependency_version_count == 1
            and dependency_receipt_count == 1
            and root_identity_count == 1
            and exact_root_count == 1
            and root_role_binding_count == 0
            and root_page_child_count == 0
        )
        dependency_state = (
            "EXACT_CONTROLLED_DEPENDENCY"
            if dependency_exact
            else "ABSENT"
            if dependency_absent
            else "INVALID"
        )
        dependency_state_verified = bool(
            forbidden_public_history_count == 0
            and dependency_state == "EXACT_CONTROLLED_DEPENDENCY"
        )
        database.update({
            "legacyAdminRootDependencyState": dependency_state,
            "legacyAdminRootDependencyStateVerified":
                dependency_state_verified,
            "legacyAdminRootDependencyFactsSha256":
                dependency_facts_sha256,
            "legacyAdminRootDependencyReceiptCount":
                dependency_receipt_count,
            "legacyAdminRootDependencyVersionCount":
                dependency_version_count,
            "legacyAdminRootIdentityCount": root_identity_count,
            "legacyAdminRootExactCount": exact_root_count,
            "legacyAdminRootRoleBindingCount": root_role_binding_count,
            "legacyAdminRootPageChildCount": root_page_child_count,
            "legacyForbiddenPublicInit001Through042ReceiptCount":
                forbidden_public_history_count,
            "w1a043State": (
                "ABSENT"
                if database["publicInit043AnyReceiptCount"] == 0
                and database["boardAttributionInternalReceiptCount"] == 0
                and database["boardAttributionTableCount"] == 0
                and database["boardAttributionTriggerCount"] == 0
                and database["boardAttributionPermissionCount"] == 0
                and database["boardAttributionEventCount"] == 0
                and database["boardAttributionProbeEventCount"] == 0
                and database["boardAttributionNaturalEventCount"] == 0
                and database["boardAttributionNonProbeEventCount"] == 0
                and database[
                    "boardAttributionAuthoritativeProductCreditCount"
                ] == 0
                and database["boardAttributionJourneyCount"] == 0
                and database["boardAttributionProbeJourneyCount"] == 0
                and database["boardAttributionNaturalJourneyCount"] == 0
                and database["boardAttributionNonProbeJourneyCount"] == 0
                else "EXACT_043_RETAINED_DORMANT"
                if database["publicInit043Applied"] is True
                and database["publicInit043AnyReceiptCount"] == 1
                and database["boardAttributionInternalReceiptCount"] == 1
                and database["boardAttributionTableCount"] == 2
                and database["boardAttributionTriggerCount"] == 2
                and database["boardAttributionPermissionCount"] == 1
                and database["boardAttributionEventCount"]
                    == database["boardAttributionProbeEventCount"]
                and database["boardAttributionNaturalEventCount"] == 0
                and database["boardAttributionNonProbeEventCount"] == 0
                and database[
                    "boardAttributionAuthoritativeProductCreditCount"
                ] == 0
                and database["boardAttributionJourneyCount"]
                    == database["boardAttributionProbeJourneyCount"]
                and database["boardAttributionNaturalJourneyCount"] == 0
                and database["boardAttributionNonProbeJourneyCount"] == 0
                else "INVALID"
            ),
        })
        legacy_table_count = int(query(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema=DATABASE() "
            "AND table_name='u3w_legacy_schema_baseline_receipt_v2'"
        ))
        if legacy_table_count == 1:
            legacy_rows = query("""
SELECT CONCAT_WS('\t',
  HEX(receipt_json),adoption_receipt_sha256,baseline_id,
  adoption_run_id,baseline_mode,source_commit,backup_run_id,
  backup_bundle_receipt_sha256,backup_sha256,backup_size_bytes,
  source_facts_sha256,approval_receipt_sha256,source_jar_sha256,
  adoption_runner_sha256,adoption_worker_sha256,
  canonical_history_claimed,
  public_init_035_through_042_receipts_written,
  production_business_state_changed)
FROM u3w_legacy_schema_baseline_receipt_v2
ORDER BY baseline_id
""")
            rows = [row for row in legacy_rows.splitlines() if row]
            if len(rows) == 1:
                parts = rows[0].split("\t")
                if len(parts) == 18:
                    try:
                        receipt_raw = bytes.fromhex(parts[0]).decode("utf-8")
                        payload = json.loads(receipt_raw)
                        canonical = canonical_json(payload)
                        actual_digest = hashlib.sha256(
                            canonical.encode("utf-8")
                        ).hexdigest()
                    except (ValueError, UnicodeError, json.JSONDecodeError):
                        payload = {}
                        canonical = ""
                        actual_digest = ""
                    receipt_path = pathlib.Path(
                        LEGACY_BASELINE_RECEIPT_PATH
                    )
                    receipt_file_valid = False
                    receipt_file_sha = None
                    if receipt_path.exists():
                        try:
                            resolved_receipt = receipt_path.resolve(strict=True)
                            status = resolved_receipt.stat()
                            receipt_file_sha = sha256_file(resolved_receipt)
                            receipt_file_valid = bool(
                                resolved_receipt.is_file()
                                and not resolved_receipt.is_symlink()
                                and status.st_uid == 0
                                and status.st_gid == 0
                                and status.st_mode & 0o777 == 0o600
                                and status.st_nlink == 1
                                and resolved_receipt.read_text(
                                    encoding="utf-8"
                                ) == canonical
                                and receipt_file_sha == actual_digest
                            )
                        except (OSError, UnicodeError):
                            receipt_file_valid = False
                    live_facts = legacy_projection_facts()
                    live_overlay = legacy_control_overlay_sha256()
                    source_facts_sha = hashlib.sha256(
                        canonical_json(payload.get("sourceFacts", {})).encode(
                            "utf-8"
                        )
                    ).hexdigest()
                    baseline_backup_bundle_valid = False
                    backup_run_id = str(payload.get("backupRunId") or "")
                    if re.fullmatch(
                        r"w1a-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}",
                        backup_run_id,
                    ):
                        baseline_backup_bundle_path = pathlib.Path(
                            "/opt/fbsir/admin/backups/w1a",
                            backup_run_id,
                            "receipt.json",
                        )
                        try:
                            baseline_backup_status = (
                                baseline_backup_bundle_path.stat()
                            )
                            baseline_backup_bundle_valid = bool(
                                baseline_backup_bundle_path.is_file()
                                and not baseline_backup_bundle_path.is_symlink()
                                and baseline_backup_status.st_uid == 0
                                and baseline_backup_status.st_gid == 0
                                and baseline_backup_status.st_mode
                                    & 0o777 == 0o600
                                and baseline_backup_status.st_nlink == 1
                                and sha256_file(
                                    baseline_backup_bundle_path
                                ) == payload.get(
                                    "backupBundleReceiptSha256"
                                )
                            )
                        except OSError:
                            baseline_backup_bundle_valid = False
                    migration_description = (
                        "APPLIED:LEGACY_ADOPTED_W1A_V2:" + actual_digest
                    )
                    migration_receipt_count = int(query(
                        "SELECT COUNT(*) FROM u3w_schema_migration "
                        "WHERE version="
                        "'legacy_w1a_baseline_20260724_001' "
                        "AND description='"
                        + migration_description + "'"
                    )) if re.fullmatch(
                        r"[0-9a-f]{64}", actual_digest
                    ) else 0
                    fabricated_history_count = int(query(
                        "SELECT COUNT(*) FROM u3w_schema_migration "
                        "WHERE version IN "
                        "('public_init_035','public_init_036',"
                        "'public_init_037','public_init_038',"
                        "'public_init_039','public_init_040',"
                        "'public_init_041','public_init_042')"
                    ))
                    row_fields_valid = bool(
                        parts[1] == actual_digest
                        and parts[2] == actual_digest
                        and parts[3] == payload.get("runId")
                        and parts[4] == "LEGACY_ADOPTED_W1A_V2"
                        and parts[5] == payload.get("sourceCommit")
                        and parts[6] == payload.get("backupRunId")
                        and parts[7]
                            == payload.get("backupBundleReceiptSha256")
                        and parts[8] == payload.get("backupSha256")
                        and int(parts[9])
                            == payload.get("backupSizeBytes")
                        and parts[10] == payload.get("sourceFactsSha256")
                        and parts[11]
                            == payload.get("approvalReceiptSha256")
                        and parts[12] == payload.get("sourceJarSha256")
                        and parts[13] == payload.get("runnerSha256")
                        and parts[14] == payload.get("workerSha256")
                        and parts[15:] == ["0", "0", "0"]
                    )
                    valid = bool(
                        set(payload) == LEGACY_RECEIPT_FIELDS
                        and canonical == receipt_raw
                        and payload.get("schema")
                            == "fbsir.u3wLegacyBaselineAdoptionReceipt.v2"
                        and payload.get("baselineMode")
                            == "LEGACY_ADOPTED_W1A_V2"
                        and payload.get("migrationVersion")
                            == "legacy_w1a_baseline_20260724_001"
                        and payload.get("migrationDescription")
                            == "APPLIED:LEGACY_ADOPTED_W1A_V2:"
                               "<receipt-sha256>"
                        and payload.get("targetHost") == TARGET_HOST
                        and payload.get("serviceUnit") == SERVICE_UNIT
                        and payload.get("database") == database_name
                        and payload.get("databaseEndpoint")
                            == host + ":" + port
                        and payload.get("databaseServerUuid")
                            == identity[3].lower()
                        and payload.get("serverVersion")
                            == database["serverVersion"]
                        and payload.get("fingerprintAlgorithm")
                            == "u3w.mysql-schema-metadata.v2"
                        and re.fullmatch(
                            r"[0-9a-f]{40}",
                            str(payload.get("sourceCommit") or ""),
                        ) is not None
                        and re.fullmatch(
                            r"[0-9a-f]{64}",
                            str(payload.get("sourceJarSha256") or ""),
                        ) is not None
                        and payload.get("runnerSha256")
                            == EXPECTED_BASELINE_RUNNER_SHA256
                        and payload.get("workerSha256")
                            == EXPECTED_BASELINE_WORKER_SHA256
                        and re.fullmatch(
                            r"[0-9a-f]{64}",
                            str(payload.get(
                                "backupBundleReceiptSha256"
                            ) or ""),
                        ) is not None
                        and baseline_backup_bundle_valid
                        and payload.get("sourceFactsSha256")
                            == source_facts_sha
                        and payload.get("sourceFacts") == live_facts
                        and payload.get("preAdoptionLiveFacts") == live_facts
                        and payload.get(
                            "postAdoptionBusinessProjectionFacts"
                        ) == live_facts
                        and payload.get("controlOverlaySha256")
                            == EXPECTED_CONTROL_OVERLAY_SHA256
                        and (
                            live_overlay == EXPECTED_CONTROL_OVERLAY_SHA256
                            or database.get("publicInit043Applied") is True
                        )
                        and payload.get("canonicalHistoryClaimed") is False
                        and payload.get(
                            "publicInit035Through042ReceiptsWritten"
                        ) is False
                        and payload.get(
                            "productionBusinessStateChanged"
                        ) is False
                        and payload.get("productionServiceChanged") is False
                        and payload.get(
                            "officialExpertsPackageChanged"
                        ) is False
                        and row_fields_valid
                        and receipt_file_valid
                        and migration_receipt_count == 1
                        and fabricated_history_count == 0
                        and dependency_state_verified
                    )
                    database.update({
                        "schemaBaselineMode": (
                            "LEGACY_ADOPTED_W1A_V2" if valid else None
                        ),
                        "legacyBaselineReceiptValid": valid,
                        "legacyBaselineReceiptAnchorMatched": bool(
                            valid
                            and EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST
                            and receipt_file_sha
                                == EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST
                        ),
                        "legacyBaselineLiveFactsMatched": valid,
                        "legacyBaselineReceiptDigest": actual_digest or None,
                        "legacyBaselineReceiptSha256": (
                            parts[1] if valid else None
                        ),
                        "legacyBaselineMigrationCount": (
                            migration_receipt_count if valid else 0
                        ),
                        "legacyBaselineSourceCommit": payload.get(
                            "sourceCommit"
                        ),
                        "legacyBaselineBackupBundleReceiptSha256":
                            payload.get("backupBundleReceiptSha256"),
                    })

    adoption_receipt_path = pathlib.Path(
        ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_PATH
    )
    adoption_receipt_sha256 = None
    adoption_receipt = {}
    adoption_receipt_file_valid = False
    try:
        resolved_adoption_receipt = adoption_receipt_path.resolve(
            strict=True
        )
        adoption_status = resolved_adoption_receipt.stat()
        adoption_receipt_sha256 = sha256_file(
            resolved_adoption_receipt
        )
        adoption_receipt = json.loads(
            resolved_adoption_receipt.read_text(encoding="utf-8")
        )
        adoption_receipt_file_valid = bool(
            resolved_adoption_receipt.is_file()
            and not resolved_adoption_receipt.is_symlink()
            and adoption_status.st_uid == 0
            and adoption_status.st_gid == 0
            and adoption_status.st_mode & 0o777 == 0o600
            and adoption_status.st_nlink == 1
        )
    except (OSError, UnicodeError, json.JSONDecodeError):
        adoption_receipt = {}

    adoption_receipt_fields = {
        "schema",
        "adoptionState",
        "adoptionClaim",
        "runId",
        "sourceCommit",
        "targetHost",
        "database",
        "databaseEndpoint",
        "databaseServerUuid",
        "serverVersion",
        "databaseProtectionMode",
        "dependencyMigrationVersion",
        "dependencyMigrationDescription",
        "publicInit043Version",
        "attributionInternal043Version",
        "liveFacts",
        "liveFactsSha256",
        "baselineLatestReceiptPath",
        "baselineLatestReceiptSha256",
        "backupLatestReceiptPath",
        "backupLatestReceiptSha256",
        "planReceiptSha256",
        "approvalReceiptSha256",
        "runnerSha256",
        "workerSha256",
        "originalExecutionClaimed",
        "productionFilesystemChanged",
        "productionDatabaseChanged",
        "productionServiceChanged",
        "officialExpertsPackageChanged",
        "secretsDisclosed",
        "observedAt",
    }
    adoption_live_fact_fields = {
        "databaseServerUuid",
        "serverVersion",
        "rootIdentityCount",
        "exactRootCount",
        "rootProjection",
        "rootRoleBindingCount",
        "rootPageChildCount",
        "dependencyVersionCount",
        "dependencyReceiptCount",
        "dependencyRowsFingerprintSha256",
        "forbiddenPublicInit001Through042ReceiptCount",
        "publicInit043ReceiptCount",
        "attributionInternalReceiptCount",
        "attributionTableCount",
        "attributionTriggerCount",
        "attributionPermissionCount",
        "attributionEventCount",
        "attributionProbeEventCount",
        "attributionNaturalEventCount",
        "attributionNonProbeEventCount",
        "attributionAuthoritativeProductCreditCount",
        "attributionJourneyCount",
        "attributionProbeJourneyCount",
        "attributionNaturalJourneyCount",
        "attributionNonProbeJourneyCount",
        "w1aSchemaFingerprintSha256",
        "w1a043State",
    }
    adoption_root_projection_fields = {
        "menuId",
        "menuName",
        "parentId",
        "orderNum",
        "path",
        "component",
        "query",
        "routeName",
        "isFrame",
        "isCache",
        "menuType",
        "visible",
        "status",
        "perms",
        "icon",
    }
    adoption_live_facts = adoption_receipt.get("liveFacts")
    adoption_live_facts_sha256 = (
        hashlib.sha256(
            canonical_json(adoption_live_facts).encode("utf-8")
        ).hexdigest()
        if isinstance(adoption_live_facts, dict) else None
    )
    adoption_attribution_observation_fields = (
        "attributionEventCount",
        "attributionProbeEventCount",
        "attributionNaturalEventCount",
        "attributionNonProbeEventCount",
        "attributionAuthoritativeProductCreditCount",
        "attributionJourneyCount",
        "attributionProbeJourneyCount",
        "attributionNaturalJourneyCount",
        "attributionNonProbeJourneyCount",
    )
    current_adoption_attribution_observations = {
        "w1a043State": database.get("w1a043State"),
        "attributionEventCount":
            database.get("boardAttributionEventCount"),
        "attributionProbeEventCount":
            database.get("boardAttributionProbeEventCount"),
        "attributionNaturalEventCount":
            database.get("boardAttributionNaturalEventCount"),
        "attributionNonProbeEventCount":
            database.get("boardAttributionNonProbeEventCount"),
        "attributionAuthoritativeProductCreditCount":
            database.get(
                "boardAttributionAuthoritativeProductCreditCount"
            ),
        "attributionJourneyCount":
            database.get("boardAttributionJourneyCount"),
        "attributionProbeJourneyCount":
            database.get("boardAttributionProbeJourneyCount"),
        "attributionNaturalJourneyCount":
            database.get("boardAttributionNaturalJourneyCount"),
        "attributionNonProbeJourneyCount":
            database.get("boardAttributionNonProbeJourneyCount"),
    }

    def adoption_attribution_observations_dormant_safe(facts):
        if not isinstance(facts, dict):
            return False
        if any(
            type(facts.get(field)) is not int or facts[field] < 0
            for field in adoption_attribution_observation_fields
        ):
            return False
        if facts.get("w1a043State") == "ABSENT":
            return all(
                facts[field] == 0
                for field in adoption_attribution_observation_fields
            )
        return bool(
            facts.get("w1a043State")
                == "EXACT_043_RETAINED_DORMANT"
            and facts["attributionEventCount"]
                == facts["attributionProbeEventCount"]
            and facts["attributionNaturalEventCount"] == 0
            and facts["attributionNonProbeEventCount"] == 0
            and facts[
                "attributionAuthoritativeProductCreditCount"
            ] == 0
            and facts["attributionJourneyCount"]
                == facts["attributionProbeJourneyCount"]
            and facts["attributionNaturalJourneyCount"] == 0
            and facts["attributionNonProbeJourneyCount"] == 0
        )

    def adoption_attribution_observations_monotonic(
        recorded_facts, current_facts
    ):
        return bool(
            adoption_attribution_observations_dormant_safe(
                recorded_facts
            )
            and adoption_attribution_observations_dormant_safe(
                current_facts
            )
            and recorded_facts.get("w1a043State")
                == current_facts.get("w1a043State")
            and all(
                current_facts[field] >= recorded_facts[field]
                for field in adoption_attribution_observation_fields
            )
        )

    def historical_receipt_matches(
        path_value,
        expected_sha256,
        root,
        filename,
    ):
        if (
            not isinstance(path_value, str)
            or re.fullmatch(
                r"[0-9a-f]{64}",
                str(expected_sha256 or ""),
            ) is None
        ):
            return False
        try:
            candidate = pathlib.Path(path_value).resolve(strict=True)
            root = pathlib.Path(root).resolve(strict=True)
            relative = candidate.relative_to(root)
            status = candidate.stat()
            return bool(
                len(relative.parts) == 2
                and relative.parts[1] == filename
                and candidate.is_file()
                and not candidate.is_symlink()
                and status.st_uid == 0
                and status.st_gid == 0
                and status.st_mode & 0o777 == 0o600
                and status.st_nlink == 1
                and sha256_file(candidate) == expected_sha256
            )
        except (OSError, RuntimeError, ValueError):
            return False

    adoption_historical_bindings_matched = bool(
        historical_receipt_matches(
            adoption_receipt.get("baselineLatestReceiptPath"),
            adoption_receipt.get("baselineLatestReceiptSha256"),
            "/opt/fbsir/admin/baselines/w1a",
            "adoption-receipt.json",
        )
        and historical_receipt_matches(
            adoption_receipt.get("backupLatestReceiptPath"),
            adoption_receipt.get("backupLatestReceiptSha256"),
            "/opt/fbsir/admin/backups/w1a",
            "receipt.json",
        )
        and adoption_receipt.get("baselineLatestReceiptSha256")
            == database.get("legacyBaselineReceiptSha256")
        and adoption_receipt.get("baselineLatestReceiptSha256")
            == EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST
    )
    adoption_live_facts_matched = bool(
        isinstance(adoption_live_facts, dict)
        and set(adoption_live_facts) == adoption_live_fact_fields
        and isinstance(
            adoption_live_facts.get("rootProjection"),
            dict,
        )
        and set(
            adoption_live_facts.get("rootProjection", {})
        ) == adoption_root_projection_fields
        and adoption_receipt.get("liveFactsSha256")
            == adoption_live_facts_sha256
        and adoption_live_facts.get("databaseServerUuid")
            == database.get("databaseServerUuid")
        and adoption_live_facts.get("serverVersion")
            == database.get("serverVersion")
        and adoption_live_facts.get("rootIdentityCount")
            == database.get("legacyAdminRootIdentityCount")
        and adoption_live_facts.get("exactRootCount")
            == database.get("legacyAdminRootExactCount")
        and adoption_live_facts.get("rootRoleBindingCount")
            == database.get("legacyAdminRootRoleBindingCount")
        and adoption_live_facts.get("rootPageChildCount")
            == database.get("legacyAdminRootPageChildCount")
        and adoption_live_facts.get("dependencyVersionCount")
            == database.get(
                "legacyAdminRootDependencyVersionCount"
            )
        and adoption_live_facts.get("dependencyReceiptCount")
            == database.get(
                "legacyAdminRootDependencyReceiptCount"
            )
        and adoption_live_facts.get(
            "dependencyRowsFingerprintSha256"
        ) == database.get("legacyAdminRootDependencyFactsSha256")
        and adoption_live_facts.get(
            "forbiddenPublicInit001Through042ReceiptCount"
        ) == database.get(
            "legacyForbiddenPublicInit001Through042ReceiptCount"
        )
        and adoption_live_facts.get("publicInit043ReceiptCount")
            == database.get("publicInit043AnyReceiptCount")
        and adoption_live_facts.get(
            "attributionInternalReceiptCount"
        ) == database.get("boardAttributionInternalReceiptCount")
        and adoption_live_facts.get("attributionTableCount")
            == database.get("boardAttributionTableCount")
        and adoption_live_facts.get("attributionTriggerCount")
            == database.get("boardAttributionTriggerCount")
        and adoption_live_facts.get("attributionPermissionCount")
            == database.get("boardAttributionPermissionCount")
        and adoption_attribution_observations_monotonic(
            adoption_live_facts,
            current_adoption_attribution_observations,
        )
        and adoption_live_facts.get("w1aSchemaFingerprintSha256")
            == database.get("w1aSchemaFingerprintSha256")
        and adoption_live_facts.get("w1a043State")
            == database.get("w1a043State")
        and database.get("w1a043State")
            in ("ABSENT", "EXACT_043_RETAINED_DORMANT")
    )
    adoption_receipt_valid = bool(
        adoption_receipt_file_valid
        and set(adoption_receipt) == adoption_receipt_fields
        and adoption_receipt.get("schema")
            == "fbsir.u3wLegacyAdminRootDependencyAdoptionReceipt.v3"
        and adoption_receipt.get("adoptionState")
            == "ADOPTED_EXISTING_EXACT_DEPENDENCY"
        and adoption_receipt.get("adoptionClaim")
            == "CURRENT_STATE_ONLY_NOT_ORIGINAL_EXECUTION"
        and re.fullmatch(
            r"w1a-admin-root-dependency-[0-9]{8}T[0-9]{6}Z-"
            r"[0-9a-f]{12}",
            str(adoption_receipt.get("runId") or ""),
        ) is not None
        and re.fullmatch(
            r"[0-9a-f]{40}",
            str(adoption_receipt.get("sourceCommit") or ""),
        ) is not None
        and adoption_receipt.get("targetHost") == TARGET_HOST
        and adoption_receipt.get("database") == database_name
        and adoption_receipt.get("databaseEndpoint")
            == host + ":" + port
        and adoption_receipt.get("databaseServerUuid")
            == database.get("databaseServerUuid")
        and adoption_receipt.get("serverVersion")
            == database.get("serverVersion")
        and adoption_receipt.get("databaseProtectionMode")
            == (
                "HOST_FLOCK_NAMED_LOCK_REPEATABLE_READ_"
                "FULL_CONTROL_RANGE_MDL_AND_APPROVED_NO_DDL_WINDOW"
            )
        and adoption_receipt.get("dependencyMigrationVersion")
            == "w1a_043_legacy_admin_root_dependency_20260724_001"
        and adoption_receipt.get("dependencyMigrationDescription")
            == "APPLIED:W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY_V1"
        and adoption_receipt.get("publicInit043Version")
            == "public_init_043"
        and adoption_receipt.get("attributionInternal043Version")
            == "20260723_independent_board_attribution_v1_043"
        and re.fullmatch(
            r"[0-9a-f]{64}",
            str(adoption_receipt.get("planReceiptSha256") or ""),
        ) is not None
        and re.fullmatch(
            r"[0-9a-f]{64}",
            str(adoption_receipt.get("approvalReceiptSha256") or ""),
        ) is not None
        and adoption_receipt.get("runnerSha256")
            == EXPECTED_ADMIN_ROOT_DEPENDENCY_RUNNER_SHA256
        and adoption_receipt.get("workerSha256")
            == EXPECTED_ADMIN_ROOT_DEPENDENCY_WORKER_SHA256
        and adoption_receipt.get("originalExecutionClaimed") is False
        and adoption_receipt.get("productionFilesystemChanged") is True
        and adoption_receipt.get("productionDatabaseChanged") is False
        and adoption_receipt.get("productionServiceChanged") is False
        and adoption_receipt.get("officialExpertsPackageChanged") is False
        and adoption_receipt.get("secretsDisclosed") is False
        and adoption_live_facts_matched
        and adoption_historical_bindings_matched
    )
    database.update({
        "adminRootDependencyAdoptionReceiptSchema":
            adoption_receipt.get("schema"),
        "adminRootDependencyAdoptionRunId":
            adoption_receipt.get("runId"),
        "adminRootDependencyAdoptionReceiptSha256":
            adoption_receipt_sha256,
        "adminRootDependencyAdoptionReceiptValid":
            adoption_receipt_valid,
        "adminRootDependencyAdoptionReceiptAnchorMatched": bool(
            adoption_receipt_valid
            and EXPECTED_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256
            and adoption_receipt_sha256
                == EXPECTED_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256
        ),
        "adminRootDependencyAdoptionSourceCommit":
            adoption_receipt.get("sourceCommit"),
        "adminRootDependencyAdoptionDatabaseServerUuid":
            adoption_receipt.get("databaseServerUuid"),
        "adminRootDependencyAdoptionLiveFactsSha256":
            adoption_receipt.get("liveFactsSha256"),
        "adminRootDependencyAdoptionDependencyRowsFingerprintSha256": (
            adoption_live_facts.get(
                "dependencyRowsFingerprintSha256"
            )
            if isinstance(adoption_live_facts, dict) else None
        ),
        "adminRootDependencyAdoptionW1a043State":
            adoption_live_facts.get("w1a043State")
            if isinstance(adoption_live_facts, dict) else None,
        "adminRootDependencyAdoptionPlanReceiptSha256":
            adoption_receipt.get("planReceiptSha256"),
        "adminRootDependencyAdoptionApprovalReceiptSha256":
            adoption_receipt.get("approvalReceiptSha256"),
        "adminRootDependencyAdoptionRunnerSha256":
            adoption_receipt.get("runnerSha256"),
        "adminRootDependencyAdoptionWorkerSha256":
            adoption_receipt.get("workerSha256"),
        "adminRootDependencyAdoptionLiveFactsMatched":
            adoption_live_facts_matched,
        "adminRootDependencyAdoptionBaselineReceiptSha256":
            adoption_receipt.get("baselineLatestReceiptSha256"),
        "adminRootDependencyAdoptionBackupReceiptSha256":
            adoption_receipt.get("backupLatestReceiptSha256"),
        "adminRootDependencyAdoptionHistoricalBindingsMatched":
            adoption_historical_bindings_matched,
    })

key_names = sorted(environment.keys())
explicit_false = sorted(
    name for name in FLAG_NAMES
    if environment.get(name, "").strip() == "false"
)
active_event_key_id = environment.get(
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID", ""
).strip()
active_event_key = environment.get(
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY", ""
).strip()
previous_event_key_id = environment.get(
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY_ID", ""
).strip()
previous_event_key = environment.get(
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY", ""
).strip()
active_event_key_pair_present = bool(active_event_key_id and active_event_key)
previous_event_key_pair_complete = bool(
    (not previous_event_key_id and not previous_event_key)
    or (previous_event_key_id and previous_event_key)
)
event_key_count = (
    (1 if active_event_key_pair_present else 0)
    + (1 if previous_event_key_id and previous_event_key else 0)
)
same_binding_value = environment.get(
    "FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET", ""
).strip()
same_binding_present = bool(same_binding_value)
environment_file_custody_secure = bool(environment_file_paths) and all(
    pathlib.Path(filename).is_file()
    and not pathlib.Path(filename).is_symlink()
    and pathlib.Path(filename).stat().st_uid == 0
    and pathlib.Path(filename).stat().st_gid == 0
    and (pathlib.Path(filename).stat().st_mode & 0o777) == 0o600
    and pathlib.Path(filename).stat().st_nlink == 1
    for filename in environment_file_paths
)
environment_file_path_exact = (
    environment_file_paths == ["/etc/u3w/fbsir-admin.env"]
)
active_event_material = decode_secret_material(active_event_key)
previous_event_material = decode_secret_material(previous_event_key)
same_binding_material = decode_secret_material(same_binding_value)
key_id_pattern = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}")
resolved_event_material = {}
for key_id, material in (
    (active_event_key_id, active_event_material),
    (previous_event_key_id, previous_event_material),
):
    if key_id and material is not None:
        existing = resolved_event_material.get(key_id)
        if existing is not None and existing != material:
            resolved_event_material = {}
            break
        resolved_event_material[key_id] = material
cryptographic_shape_valid = bool(
    active_event_key_pair_present
    and previous_event_key_pair_complete
    and same_binding_present
    and key_id_pattern.fullmatch(active_event_key_id)
    and (
        not previous_event_key_id
        or key_id_pattern.fullmatch(previous_event_key_id)
    )
    and active_event_material is not None
    and len(active_event_material) >= 32
    and (
        not previous_event_key_id
        or (
            previous_event_key_id != active_event_key_id
            and previous_event_material is not None
            and len(previous_event_material) >= 32
            and not hmac.compare_digest(
                previous_event_material, active_event_material
            )
        )
    )
    and same_binding_material is not None
    and len(same_binding_material) >= 32
    and resolved_event_material
    and all(
        not hmac.compare_digest(material, same_binding_material)
        for material in resolved_event_material.values()
    )
)
admin_engine_credential = str(
    environment.get("FBSIR_ENGINE_TOKEN", "")
).strip()
admin_engine_credential_valid = (
    re.fullmatch(
        r"[A-Za-z0-9_-]{43,128}",
        admin_engine_credential,
    ) is not None
)
admin_engine_material = admin_engine_credential.encode("utf-8")
admin_engine_comparison_material = (
    active_event_material,
    previous_event_material,
    same_binding_material,
    decode_secret_material(environment.get("FBSIR_TOKEN_SECRET")),
    decode_secret_material(environment.get("WXFBSIR_TOKEN_SECRET")),
)
admin_engine_credential_independent = bool(
    admin_engine_credential_valid
    and all(
        material is None
        or not hmac.compare_digest(admin_engine_material, material)
        for material in admin_engine_comparison_material
    )
)
api2_event_key_file = pathlib.Path(API2_EVENT_KEY_PATH)
api2_event_key_file_custody_secure = False
api2_event_key_file_matched = False
if api2_event_key_file.is_file() and not api2_event_key_file.is_symlink():
    api2_key_status = api2_event_key_file.stat()
    api2_event_key_file_custody_secure = bool(
        api2_key_status.st_uid == 0
        and api2_key_status.st_gid == 0
        and api2_key_status.st_mode & 0o777 == 0o600
        and api2_key_status.st_nlink == 1
    )
    if api2_event_key_file_custody_secure:
        api2_material = api2_event_key_file.read_bytes()
        api2_event_key_file_matched = bool(
            len(api2_material) >= 32
            and active_event_material is not None
            and hmac.compare_digest(api2_material, active_event_material)
        )

configuration_receipt_valid = False
configuration_receipt_anchor_matched = False
configuration_receipt_sha256 = None
configuration_receipt_source_commit = None
configuration_receipt = {}
configuration_predecessor_receipt = {}
configuration_receipt_path = pathlib.Path(CONFIGURATION_RECEIPT_PATH)
if configuration_receipt_path.exists():
    try:
        resolved_configuration_receipt = configuration_receipt_path.resolve(
            strict=True
        )
        receipt_status = resolved_configuration_receipt.stat()
        configuration_receipt_sha256 = sha256_file(
            resolved_configuration_receipt
        )
        configuration_receipt = json.loads(
            resolved_configuration_receipt.read_text(encoding="utf-8")
        )
        configuration_receipt_source_commit = configuration_receipt.get(
            "sourceCommit"
        )
        receipt_evidence = configuration_receipt.get(
            "configurationEvidence", {}
        )
        before_service = configuration_receipt.get(
            "serviceSnapshotBefore", {}
        )
        after_service = configuration_receipt.get(
            "serviceSnapshotAfter", {}
        )
        configuration_receipt_fields = {
            "schema", "mode", "state", "runId", "sourceCommit",
            "targetHost", "serviceUnit", "environmentPath",
            "environmentBackupPath", "environmentBackupSha256",
            "environmentBeforeSha256", "environmentAfterSha256",
            "api2EventKeyPath", "api2EventKeyProvisioningState",
            "stagedKeyMaterialMatched", "configurationEvidence",
            "environmentCustodySecure", "api2EventKeyCustodySecure",
            "environmentBackupCustodySecure", "serviceSnapshotBefore",
            "serviceSnapshotAfter", "approvalReceiptSha256",
            "runnerSha256", "workerSha256", "serviceRestarted",
            "productionDatabaseChanged", "productionFilesystemChanged",
            "productionConfigurationChanged", "productionServiceChanged",
            "configurationLoaded", "officialExpertsPackageChanged",
            "secretsDisclosed", "observedAt",
        }
        configuration_receipt_schema = configuration_receipt.get("schema")
        if (
            configuration_receipt_schema
            == "fbsir.u3wDefaultOffConfigurationReceipt.v3"
        ):
            configuration_receipt_fields.update({
                "predecessorConfigurationReceiptSha256",
                "adminEngineCredentialProvisioningState",
                "engineCounterpartClosureClaimed",
            })
        backup_path = pathlib.Path(
            str(configuration_receipt.get("environmentBackupPath") or "")
        )
        expected_backup_path = pathlib.Path(
            "/etc/u3w/backups/fbsir-admin-env",
            "{}-{}.env".format(
                configuration_receipt.get("runId", ""),
                str(configuration_receipt.get(
                    "environmentBeforeSha256", ""
                ))[:16],
            ),
        )
        backup_valid = False
        try:
            backup_status = backup_path.stat()
            backup_valid = bool(
                backup_path == expected_backup_path
                and backup_path.is_file()
                and not backup_path.is_symlink()
                and backup_status.st_uid == 0
                and backup_status.st_gid == 0
                and backup_status.st_mode & 0o777 == 0o600
                and backup_status.st_nlink == 1
                and sha256_file(backup_path)
                    == configuration_receipt.get(
                        "environmentBackupSha256"
                    )
                and configuration_receipt.get(
                    "environmentBackupSha256"
                ) == configuration_receipt.get(
                    "environmentBeforeSha256"
                )
            )
        except OSError:
            backup_valid = False
        configuration_reconciliation_valid = (
            configuration_receipt_schema
            == "fbsir.u3wDefaultOffConfigurationReceipt.v2"
        )
        if (
            configuration_receipt_schema
            == "fbsir.u3wDefaultOffConfigurationReceipt.v3"
        ):
            predecessor_sha = configuration_receipt.get(
                "predecessorConfigurationReceiptSha256"
            )
            predecessor_matches = []
            configuration_root = pathlib.Path(
                "/opt/fbsir/admin/configuration/w1a"
            )
            if re.fullmatch(
                r"[0-9a-f]{64}", str(predecessor_sha or "")
            ) is not None and predecessor_sha != "0" * 64:
                for run_directory in configuration_root.iterdir():
                    if (
                        run_directory.is_symlink()
                        or not run_directory.is_dir()
                        or re.fullmatch(
                            r"w1a-config-[0-9]{8}T[0-9]{6}Z-"
                            r"[0-9a-f]{12}",
                            run_directory.name,
                        ) is None
                    ):
                        continue
                    candidate = (
                        run_directory / "configuration-receipt.json"
                    )
                    if candidate.is_file() and not candidate.is_symlink():
                        candidate_status = candidate.stat()
                        if (
                            candidate_status.st_uid == 0
                            and candidate_status.st_gid == 0
                            and candidate_status.st_nlink == 1
                            and candidate_status.st_mode & 0o777 == 0o600
                            and sha256_file(candidate) == predecessor_sha
                        ):
                            predecessor_matches.append(candidate)
            predecessor_receipt = (
                json.loads(
                    predecessor_matches[0].read_text(encoding="utf-8")
                )
                if len(predecessor_matches) == 1 else {}
            )
            configuration_predecessor_receipt = predecessor_receipt
            credential_state = configuration_receipt.get(
                "adminEngineCredentialProvisioningState"
            )
            created_by_run = credential_state == "CREATED_BY_RUN"
            adopted_existing = (
                credential_state
                == "ADOPTED_EXISTING_EXACT_DELTA"
            )
            predecessor_environment_matched = bool(
                predecessor_receipt.get("environmentAfterSha256")
                == configuration_receipt.get(
                    "environmentBeforeSha256"
                )
                if created_by_run
                else (
                    adopted_existing
                    and configuration_receipt.get(
                        "environmentBeforeSha256"
                    ) == configuration_receipt.get(
                        "environmentAfterSha256"
                    )
                    and predecessor_receipt.get(
                        "environmentAfterSha256"
                    ) == exact_admin_engine_delta_predecessor_sha256(
                        "/etc/u3w/fbsir-admin.env"
                    )
                )
            )
            configuration_reconciliation_valid = bool(
                len(predecessor_matches) == 1
                and predecessor_receipt.get("schema")
                    == "fbsir.u3wDefaultOffConfigurationReceipt.v2"
                and predecessor_environment_matched
                and predecessor_receipt.get("api2EventKeyPath")
                    == API2_EVENT_KEY_PATH
                and predecessor_receipt.get(
                    "officialExpertsPackageChanged"
                ) is False
                and predecessor_receipt.get("secretsDisclosed") is False
                and configuration_receipt.get(
                    "api2EventKeyProvisioningState"
                ) == "REUSED_FROM_PREDECESSOR_RECEIPT"
                and configuration_receipt.get(
                    "adminEngineCredentialProvisioningState"
                ) == "ADOPTED_EXISTING_EXACT_DELTA"
                and configuration_receipt.get(
                    "engineCounterpartClosureClaimed"
                ) is False
                and configuration_receipt.get(
                    "productionConfigurationChanged"
                ) is False
                and receipt_evidence.get(
                    "adminEngineCredentialValid"
                ) is True
                and receipt_evidence.get(
                    "adminEngineCredentialIndependent"
                ) is True
                and int(
                    receipt_evidence.get(
                        "adminEngineCredentialMinimumCharacters"
                    ) or 0
                ) >= 43
                and admin_engine_credential_valid
                and admin_engine_credential_independent
            )
        configuration_receipt_valid = bool(
            set(configuration_receipt) == configuration_receipt_fields
            and
            resolved_configuration_receipt.is_file()
            and not resolved_configuration_receipt.is_symlink()
            and receipt_status.st_uid == 0
            and receipt_status.st_gid == 0
            and receipt_status.st_mode & 0o777 == 0o600
            and receipt_status.st_nlink == 1
            and configuration_receipt_schema
                == "fbsir.u3wDefaultOffConfigurationReceipt.v3"
            and configuration_receipt.get("mode") == "Apply"
            and configuration_receipt.get("state")
                == "CONFIGURED_NOT_LOADED"
            and configuration_receipt.get("targetHost") == TARGET_HOST
            and configuration_receipt.get("serviceUnit") == SERVICE_UNIT
            and configuration_receipt.get("environmentPath")
                == "/etc/u3w/fbsir-admin.env"
            and configuration_receipt.get("api2EventKeyPath")
                == API2_EVENT_KEY_PATH
            and configuration_receipt.get(
                "api2EventKeyProvisioningState"
            ) == (
                "REUSED_FROM_PREDECESSOR_RECEIPT"
                if configuration_receipt_schema
                    == "fbsir.u3wDefaultOffConfigurationReceipt.v3"
                else "CREATED_BY_RUN"
            )
            and configuration_reconciliation_valid
            and configuration_receipt.get(
                "stagedKeyMaterialMatched"
            ) is True
            and re.fullmatch(
                r"[0-9a-f]{40}",
                str(configuration_receipt_source_commit or ""),
            ) is not None
            and configuration_receipt.get("runnerSha256")
                == EXPECTED_CONFIGURATION_RUNNER_SHA256
            and configuration_receipt.get("workerSha256")
                == EXPECTED_CONFIGURATION_WORKER_SHA256
            and configuration_receipt.get("environmentAfterSha256")
                == sha256_file("/etc/u3w/fbsir-admin.env")
            and receipt_evidence.get("managedKeyCount")
                == len(FLAG_NAMES) + 5
            and all(
                receipt_evidence.get(name) is True
                for name in (
                    "allManagedKeysPresent",
                    "allDefaultOffFlagsExplicitFalse",
                    "activeEventKeyPairValid",
                    "previousEventKeyPairCompleteAndValid",
                    "sameBindingSecretValidAndIndependent",
                    "api2RawEventKeyMatchesU3wActiveMaterial",
                )
            )
            and receipt_evidence.get("secretsDisclosed") is False
            and before_service == after_service
            and after_service.get("activeState") == "active"
            and isinstance(after_service.get("mainPid"), int)
            and after_service.get("mainPid") > 0
            and str(after_service.get("jarPath") or "").startswith(
                "/opt/fbsir/admin/"
            )
            and re.fullmatch(
                r"[0-9a-f]{64}",
                str(after_service.get("jarSha256") or ""),
            ) is not None
            and bool(after_service.get("invocationId"))
            and isinstance(
                after_service.get("execMainStartTimestampMonotonic"), int
            )
            and after_service.get("nRestarts") == 0
            and configuration_receipt.get("serviceRestarted") is False
            and configuration_receipt.get(
                "productionDatabaseChanged"
            ) is False
            and configuration_receipt.get(
                "productionFilesystemChanged"
            ) is True
            and configuration_receipt.get(
                "productionConfigurationChanged"
            ) is (
                configuration_receipt.get(
                    "adminEngineCredentialProvisioningState"
                ) == "CREATED_BY_RUN"
                if configuration_receipt_schema
                    == "fbsir.u3wDefaultOffConfigurationReceipt.v3"
                else True
            )
            and configuration_receipt.get(
                "productionServiceChanged"
            ) is False
            and configuration_receipt.get(
                "configurationLoaded"
            ) is False
            and configuration_receipt.get(
                "officialExpertsPackageChanged"
            ) is False
            and configuration_receipt.get("secretsDisclosed") is False
            and environment_file_path_exact
            and environment_file_custody_secure
            and api2_event_key_file_custody_secure
            and api2_event_key_file_matched
            and cryptographic_shape_valid
            and configuration_receipt.get(
                "environmentBackupCustodySecure"
            ) is True
            and backup_valid
        )
        configuration_receipt_anchor_matched = bool(
            configuration_receipt_valid
            and EXPECTED_CONFIGURATION_RECEIPT_SHA256
            and configuration_receipt_sha256
                == EXPECTED_CONFIGURATION_RECEIPT_SHA256
        )
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        configuration_receipt_valid = False

def current_backup_schema_facts():
    if "query" not in globals():
        return None
    metadata_queries = [
        """SELECT CONCAT_WS('|','T',HEX(table_name),HEX(table_type),
        HEX(COALESCE(engine,'')),HEX(COALESCE(table_collation,'')))
        FROM information_schema.tables WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','C',HEX(table_name),LPAD(ordinal_position,6,'0'),
        HEX(column_name),HEX(column_type),HEX(is_nullable),
        HEX(COALESCE(column_default,'<NULL>')),HEX(extra),
        HEX(COALESCE(character_set_name,'')),HEX(COALESCE(collation_name,'')),
        HEX(COALESCE(generation_expression,'')))
        FROM information_schema.columns WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','I',HEX(table_name),HEX(index_name),
        LPAD(seq_in_index,6,'0'),non_unique,HEX(COALESCE(column_name,'')),
        HEX(COALESCE(collation,'')),COALESCE(sub_part,-1),
        HEX(COALESCE(index_type,'')),HEX(COALESCE(expression,'')))
        FROM information_schema.statistics WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','K',HEX(table_name),HEX(constraint_name),
        HEX(constraint_type)) FROM information_schema.table_constraints
        WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','U',HEX(table_name),HEX(constraint_name),
        LPAD(ordinal_position,6,'0'),HEX(COALESCE(column_name,'')),
        HEX(COALESCE(referenced_table_name,'')),
        HEX(COALESCE(referenced_column_name,'')))
        FROM information_schema.key_column_usage
        WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','F',HEX(constraint_name),HEX(table_name),
        HEX(referenced_table_name),HEX(update_rule),HEX(delete_rule),
        HEX(match_option)) FROM information_schema.referential_constraints
        WHERE constraint_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','H',HEX(tc.table_name),HEX(cc.constraint_name),
        HEX(cc.check_clause)) FROM information_schema.check_constraints cc
        JOIN information_schema.table_constraints tc
          ON tc.constraint_schema=cc.constraint_schema
         AND tc.constraint_name=cc.constraint_name
         AND tc.constraint_type='CHECK'
        WHERE tc.table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','V',HEX(table_name),HEX(view_definition),
        HEX(check_option),HEX(is_updatable),HEX(definer),HEX(security_type),
        HEX(character_set_client),HEX(collation_connection))
        FROM information_schema.views WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','G',HEX(trigger_name),HEX(event_manipulation),
        HEX(event_object_table),HEX(action_timing),HEX(action_orientation),
        HEX(action_statement),HEX(definer))
        FROM information_schema.triggers WHERE trigger_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','R',HEX(routine_name),HEX(routine_type),
        HEX(COALESCE(data_type,'')),HEX(COALESCE(routine_definition,'')),
        HEX(is_deterministic),HEX(sql_data_access),HEX(security_type),HEX(definer))
        FROM information_schema.routines WHERE routine_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','E',HEX(event_name),HEX(event_definition),
        HEX(event_type),HEX(COALESCE(execute_at,'')),
        HEX(COALESCE(interval_value,'')),HEX(COALESCE(interval_field,'')),
        HEX(status),HEX(definer))
        FROM information_schema.events WHERE event_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','P',HEX(table_name),
        IF(partition_name IS NULL,'N',CONCAT('V',HEX(partition_name))),
        IF(subpartition_name IS NULL,'N',CONCAT('V',HEX(subpartition_name))),
        LPAD(partition_ordinal_position,6,'0'),
        IF(subpartition_ordinal_position IS NULL,'N',
          CONCAT('V',LPAD(subpartition_ordinal_position,6,'0'))),
        IF(partition_method IS NULL,'N',CONCAT('V',HEX(partition_method))),
        IF(subpartition_method IS NULL,'N',CONCAT('V',HEX(subpartition_method))),
        IF(partition_expression IS NULL,'N',CONCAT('V',HEX(partition_expression))),
        IF(subpartition_expression IS NULL,'N',
          CONCAT('V',HEX(subpartition_expression))),
        IF(partition_description IS NULL,'N',
          CONCAT('V',HEX(partition_description))))
        FROM information_schema.partitions WHERE table_schema=DATABASE()""",
        """SELECT CONCAT_WS('|','A',
        IF(specific_name IS NULL,'N',CONCAT('V',HEX(specific_name))),
        LPAD(ordinal_position,6,'0'),
        IF(parameter_mode IS NULL,'N',CONCAT('V',HEX(parameter_mode))),
        IF(parameter_name IS NULL,'N',CONCAT('V',HEX(parameter_name))),
        HEX(data_type),HEX(dtd_identifier))
        FROM information_schema.parameters WHERE specific_schema=DATABASE()""",
    ]
    metadata_rows = []
    for sql in metadata_queries:
        metadata_rows.extend(row for row in query(sql).splitlines() if row)
    counts = query(
        """SELECT SUM(table_type='BASE TABLE'),SUM(table_type='VIEW'),
        SUM(table_type='BASE TABLE' AND engine<>'InnoDB')
        FROM information_schema.tables WHERE table_schema=DATABASE()"""
    ).split("\t")
    object_counts = query(
        """SELECT
        (SELECT COUNT(*) FROM information_schema.triggers
          WHERE trigger_schema=DATABASE()),
        (SELECT COUNT(*) FROM information_schema.routines
          WHERE routine_schema=DATABASE()),
        (SELECT COUNT(*) FROM information_schema.events
          WHERE event_schema=DATABASE())"""
    ).split("\t")
    root_rows = query(
        """SELECT CONCAT_WS('|',HEX(menu_name),parent_id,HEX(COALESCE(path,'')),
        HEX(COALESCE(component,'')),HEX(COALESCE(perms,'')))
        FROM sys_menu
        WHERE HEX(menu_name)='E78BACE891A3E4BC9AE7AEA1E79086'
           OR BINARY path=BINARY 'independent-board-admin'
           OR BINARY route_name=BINARY 'IndependentBoardAdmin'
        ORDER BY BINARY menu_name,BINARY path"""
    )
    sys_menu_shape = query(
        """SELECT CONCAT_WS('|',LPAD(ordinal_position,6,'0'),HEX(column_name),
        HEX(column_type),HEX(is_nullable),HEX(COALESCE(column_default,'<NULL>')),
        HEX(extra)) FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='sys_menu'
        ORDER BY ordinal_position"""
    )
    dependency_version = (
        "w1a_043_legacy_admin_root_dependency_20260724_001"
    )
    dependency_description = (
        "APPLIED:W1A_043_LEGACY_ADMIN_ROOT_DEPENDENCY_V1"
    )
    forbidden_versions = ",".join(
        "'public_init_{:03d}'".format(index)
        for index in range(1, 43)
    )
    exact_root = (
        "HEX(menu_name)='E78BACE891A3E4BC9AE7AEA1E79086' "
        "AND parent_id=0 AND order_num=5 "
        "AND BINARY path=BINARY 'independent-board-admin' "
        "AND component IS NULL AND query IS NULL "
        "AND BINARY route_name=BINARY 'IndependentBoardAdmin' "
        "AND is_frame=1 AND is_cache=0 "
        "AND BINARY menu_type=BINARY 'M' "
        "AND BINARY visible=BINARY '0' "
        "AND BINARY status=BINARY '0' "
        "AND BINARY COALESCE(perms,'')=BINARY '' "
        "AND BINARY icon=BINARY 'peoples'"
    )
    dependency_fingerprint_rows = query(
        "SELECT row_value FROM ("
        "SELECT CONCAT_WS('|','D',HEX(version),HEX(description)) "
        "row_value FROM u3w_schema_migration WHERE version='"
        + dependency_version
        + "' UNION ALL SELECT CONCAT_WS('|','R',menu_id,"
        "HEX(menu_name),parent_id,order_num,HEX(COALESCE(path,'')),"
        "HEX(COALESCE(component,'<NULL>')),"
        "HEX(COALESCE(query,'<NULL>')),"
        "HEX(COALESCE(route_name,'')),is_frame,is_cache,"
        "HEX(menu_type),HEX(visible),HEX(status),"
        "HEX(COALESCE(perms,'')),HEX(icon)) FROM sys_menu WHERE "
        + exact_root + ") rows_ ORDER BY BINARY row_value"
    )
    dependency_version_count = int(query(
        "SELECT COUNT(*) FROM u3w_schema_migration WHERE version='"
        + dependency_version + "'"
    ))
    dependency_receipt_count = int(query(
        "SELECT COUNT(*) FROM u3w_schema_migration WHERE version='"
        + dependency_version + "' AND description='"
        + dependency_description + "'"
    ))
    root_identity_count = len(
        [row for row in root_rows.splitlines() if row]
    )
    exact_root_count = int(query(
        "SELECT COUNT(*) FROM sys_menu WHERE " + exact_root
    ))
    root_role_count = int(query(
        "SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN "
        "(SELECT menu_id FROM sys_menu WHERE " + exact_root + ")"
    ))
    root_child_count = int(query(
        "SELECT COUNT(*) FROM sys_menu WHERE menu_type IN ('M','C') "
        "AND parent_id IN (SELECT menu_id FROM sys_menu WHERE "
        + exact_root + ")"
    ))
    forbidden_count = int(query(
        "SELECT COUNT(*) FROM u3w_schema_migration WHERE version IN ("
        + forbidden_versions + ")"
    ))
    public_043_count = int(query(
        "SELECT COUNT(*) FROM u3w_schema_migration "
        "WHERE version='public_init_043'"
    ))
    internal_043_count = int(query(
        "SELECT COUNT(*) FROM u3w_schema_migration WHERE version="
        "'20260723_independent_board_attribution_v1_043'"
    ))
    attribution_table_count = int(query(
        "SELECT COUNT(*) FROM information_schema.tables "
        "WHERE table_schema=DATABASE() AND table_name IN "
        "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
    ))
    attribution_trigger_count = int(query(
        "SELECT COUNT(*) FROM information_schema.triggers "
        "WHERE trigger_schema=DATABASE() AND event_object_table IN "
        "('fbs_board_attr_journey_v1','fbs_board_attr_event_v1')"
    ))
    attribution_permission_count = int(query(
        "SELECT COUNT(*) FROM sys_menu WHERE "
        "BINARY perms=BINARY 'board:attribution:query'"
    ))
    attribution_event_count = 0
    attribution_probe_event_count = 0
    attribution_natural_event_count = 0
    attribution_non_probe_event_count = 0
    attribution_authoritative_product_credit_count = 0
    attribution_journey_count = 0
    attribution_probe_journey_count = 0
    attribution_natural_journey_count = 0
    attribution_non_probe_journey_count = 0
    if attribution_table_count == 2:
        attribution_event_count = int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1"
        ))
        attribution_probe_event_count = int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE BINARY traffic_class=BINARY 'PROBE'"
        ))
        attribution_natural_event_count = int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE BINARY traffic_class=BINARY 'NATURAL'"
        ))
        attribution_non_probe_event_count = int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE BINARY traffic_class<>BINARY 'PROBE'"
        ))
        attribution_authoritative_product_credit_count = int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_event_v1 "
            "WHERE authoritative_product_credit<>0"
        ))
        attribution_journey_count = int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_journey_v1"
        ))
        attribution_probe_journey_count = int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
            "WHERE BINARY traffic_class=BINARY 'PROBE'"
        ))
        attribution_natural_journey_count = int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
            "WHERE BINARY traffic_class=BINARY 'NATURAL'"
        ))
        attribution_non_probe_journey_count = int(query(
            "SELECT COUNT(*) FROM fbs_board_attr_journey_v1 "
            "WHERE BINARY traffic_class<>BINARY 'PROBE'"
        ))
    exact_dependency = bool(
        dependency_version_count == 1
        and dependency_receipt_count == 1
        and root_identity_count == 1
        and exact_root_count == 1
        and root_role_count == 0
        and root_child_count == 0
        and forbidden_count == 0
        and len([
            row for row in dependency_fingerprint_rows.splitlines()
            if row
        ]) == 2
    )
    absent_043 = bool(
        public_043_count == 0
        and internal_043_count == 0
        and attribution_table_count == 0
        and attribution_trigger_count == 0
        and attribution_permission_count == 0
        and attribution_event_count == 0
        and attribution_probe_event_count == 0
        and attribution_natural_event_count == 0
        and attribution_non_probe_event_count == 0
        and attribution_authoritative_product_credit_count == 0
        and attribution_journey_count == 0
        and attribution_probe_journey_count == 0
        and attribution_natural_journey_count == 0
        and attribution_non_probe_journey_count == 0
        and database.get("w1a043State") == "ABSENT"
        and database.get("w1aSchemaFingerprintSha256") is None
    )
    retained_043 = bool(
        public_043_count == 1
        and internal_043_count == 1
        and attribution_table_count == 2
        and attribution_trigger_count == 2
        and attribution_permission_count == 1
        and attribution_event_count == attribution_probe_event_count
        and attribution_natural_event_count == 0
        and attribution_non_probe_event_count == 0
        and attribution_authoritative_product_credit_count == 0
        and attribution_journey_count == attribution_probe_journey_count
        and attribution_natural_journey_count == 0
        and attribution_non_probe_journey_count == 0
        and database.get("w1a043State")
            == "EXACT_043_RETAINED_DORMANT"
        and database.get("w1aSchemaFingerprintSha256")
            == EXPECTED_W1A_SCHEMA_FINGERPRINT
    )
    return {
        "fingerprintAlgorithm": "u3w.mysql-schema-metadata.v2",
        "schemaFingerprintSha256": hashlib.sha256(
            ("\n".join(sorted(metadata_rows)) + "\n").encode()
        ).hexdigest(),
        "prerequisiteShapeSha256": hashlib.sha256(
            (sys_menu_shape + "\n--ROOTS--\n" + root_rows + "\n").encode()
        ).hexdigest(),
        "baseTableCount": int(counts[0]),
        "viewCount": int(counts[1]),
        "triggerCount": int(object_counts[0]),
        "routineCount": int(object_counts[1]),
        "eventCount": int(object_counts[2]),
        "independentBoardAdminRootCount": root_identity_count,
        "legacyAdminRootDependencyState": (
            "EXACT_CONTROLLED_DEPENDENCY"
            if exact_dependency else "INVALID"
        ),
        "legacyAdminRootDependencyFactsSha256": hashlib.sha256(
            (dependency_fingerprint_rows + "\n").encode("utf-8")
        ).hexdigest(),
        "legacyAdminRootDependencyVersionCount":
            dependency_version_count,
        "legacyAdminRootDependencyReceiptCount":
            dependency_receipt_count,
        "legacyAdminRootIdentityCount": root_identity_count,
        "legacyAdminRootExactCount": exact_root_count,
        "legacyAdminRootRoleBindingCount": root_role_count,
        "legacyAdminRootPageChildCount": root_child_count,
        "legacyForbiddenPublicInit001Through042ReceiptCount":
            forbidden_count,
        "publicInit043AnyReceiptCount": public_043_count,
        "attributionInternalReceiptCount": internal_043_count,
        "attributionTableCount": attribution_table_count,
        "attributionTriggerCount": attribution_trigger_count,
        "attributionPermissionCount": attribution_permission_count,
        "attributionEventCount": attribution_event_count,
        "attributionProbeEventCount": attribution_probe_event_count,
        "attributionNaturalEventCount": attribution_natural_event_count,
        "attributionNonProbeEventCount":
            attribution_non_probe_event_count,
        "attributionAuthoritativeProductCreditCount":
            attribution_authoritative_product_credit_count,
        "attributionJourneyCount": attribution_journey_count,
        "attributionProbeJourneyCount":
            attribution_probe_journey_count,
        "attributionNaturalJourneyCount":
            attribution_natural_journey_count,
        "attributionNonProbeJourneyCount":
            attribution_non_probe_journey_count,
        "w1aSchemaFingerprintSha256":
            database.get("w1aSchemaFingerprintSha256"),
        "w1a043State": (
            "ABSENT"
            if absent_043
            else "EXACT_043_RETAINED_DORMANT"
            if retained_043
            else "INVALID"
        ),
        "allBaseTablesInnoDB": int(counts[2]) == 0,
    }

backup_fact_fields = {
    "fingerprintAlgorithm", "schemaFingerprintSha256",
    "prerequisiteShapeSha256", "baseTableCount", "viewCount",
    "triggerCount", "routineCount", "eventCount",
    "independentBoardAdminRootCount",
    "legacyAdminRootDependencyState",
    "legacyAdminRootDependencyFactsSha256",
    "legacyAdminRootDependencyVersionCount",
    "legacyAdminRootDependencyReceiptCount",
    "legacyAdminRootIdentityCount", "legacyAdminRootExactCount",
    "legacyAdminRootRoleBindingCount",
    "legacyAdminRootPageChildCount",
    "legacyForbiddenPublicInit001Through042ReceiptCount",
    "publicInit043AnyReceiptCount", "attributionInternalReceiptCount",
    "attributionTableCount", "attributionTriggerCount",
    "attributionPermissionCount", "attributionEventCount",
    "attributionProbeEventCount", "attributionNaturalEventCount",
    "attributionNonProbeEventCount",
    "attributionAuthoritativeProductCreditCount",
    "attributionJourneyCount", "attributionProbeJourneyCount",
    "attributionNaturalJourneyCount",
    "attributionNonProbeJourneyCount", "w1aSchemaFingerprintSha256",
    "w1a043State", "allBaseTablesInnoDB",
}
backup_attribution_observation_fields = {
    "attributionEventCount", "attributionProbeEventCount",
    "attributionNaturalEventCount", "attributionNonProbeEventCount",
    "attributionAuthoritativeProductCreditCount",
    "attributionJourneyCount", "attributionProbeJourneyCount",
    "attributionNaturalJourneyCount",
    "attributionNonProbeJourneyCount",
}

def backup_static_control_facts(facts):
    return {
        field: value
        for field, value in facts.items()
        if field not in backup_attribution_observation_fields
    }

def backup_attribution_observations_dormant_safe(facts):
    if not isinstance(facts, dict):
        return False
    if any(
        type(facts.get(field)) is not int or facts[field] < 0
        for field in backup_attribution_observation_fields
    ):
        return False
    if facts.get("w1a043State") == "ABSENT":
        return all(
            facts[field] == 0
            for field in backup_attribution_observation_fields
        )
    return bool(
        facts.get("w1a043State") == "EXACT_043_RETAINED_DORMANT"
        and facts["attributionEventCount"]
            == facts["attributionProbeEventCount"]
        and facts["attributionNaturalEventCount"] == 0
        and facts["attributionNonProbeEventCount"] == 0
        and facts["attributionAuthoritativeProductCreditCount"] == 0
        and facts["attributionJourneyCount"]
            == facts["attributionProbeJourneyCount"]
        and facts["attributionNaturalJourneyCount"] == 0
        and facts["attributionNonProbeJourneyCount"] == 0
    )

def backup_attribution_observations_monotonic(
    recorded_facts, current_facts
):
    return bool(
        recorded_facts.get("w1a043State")
            == current_facts.get("w1a043State")
        and all(
            current_facts[field] >= recorded_facts[field]
            for field in backup_attribution_observation_fields
        )
    )

backup_receipt_fields = {
    "schema", "runId", "sourceCommit", "planReceiptSha256",
    "targetHost", "database", "sourceDatabaseServerUuid",
    "serverVersion", "serverVersionComment", "generatedAt",
    "backupPath", "backupSha256", "backupSizeBytes",
    "backupPlaintextSha256", "encryptionContract",
    "encryptionKeyFingerprintSha256", "dumpToolVersion",
    "dumpOptionsContract", "ddlProtectionMode", "sourceFacts",
    "sourceTotalRows", "sourceTableRowCountsSha256",
    "sourceSnapshotExactlyMatched", "sourceJarSha256",
    "adminRootDependencyAdoptionReceiptSha256",
    "approvalReceiptSha256", "runnerSha256", "backupWorkerSha256",
    "businessDatabaseChanged", "serviceChanged",
    "officialExpertsPackageChanged",
}
restore_receipt_fields = {
    "schema", "runId", "sourceCommit", "planReceiptSha256",
    "targetHost", "database", "sourceDatabaseServerUuid",
    "sourceBackupPath", "sourceBackupSha256",
    "sourceBackupPlaintextSha256", "sourceBackupReceiptSha256",
    "startedAt", "completedAt", "isolatedTarget",
    "isolatedNetworkingDisabled", "isolatedDataRemoved",
    "serverVersion", "serverVersionComment", "restoredFacts",
    "restoredTotalRows", "restoredTableRowCountsSha256",
    "restoreLogPath", "restoreLogSha256", "isolationEvidencePath",
    "isolationEvidenceSha256", "mysqlcheckPath", "mysqlcheckSha256",
    "adminRootDependencyAdoptionReceiptSha256",
    "approvalReceiptSha256", "backupWorkerSha256", "verifierSha256",
    "businessDatabaseChanged", "serviceChanged",
    "officialExpertsPackageChanged",
}
bundle_receipt_fields = {
    "schema", "runId", "sourceCommit", "planReceiptSha256",
    "targetHost", "database", "sourceDatabaseServerUuid",
    "generatedAt", "backupReceiptPath", "backupReceiptSha256",
    "restoreReceiptPath", "restoreReceiptSha256", "backupPath",
    "backupSha256", "backupSizeBytes", "approvalReceiptSha256",
    "runnerSha256", "backupWorkerSha256", "verifierSha256",
    "adminRootDependencyAdoptionReceiptSha256",
    "productionBusinessStateChanged",
}
isolation_evidence_fields = {
    "schema", "runId", "sourceCommit", "observedAt",
    "productionMysqldPidBefore", "productionMysqldPidAfter", "runtime",
    "restoreStdoutSha256", "restoreStderrSha256", "mysqlcheckExitCode",
    "mysqlcheckOkObjectCount", "isolatedProcessExited",
    "isolatedSocketRemoved", "isolatedPidFileRemoved",
    "isolatedDatadirRemoved", "isolatedRuntimeDirectoryRemoved",
}
isolation_runtime_fields = {
    "pid", "binarySha256", "commandLineSha256", "datadir", "socket",
    "serverUuid", "skipNetworking", "version", "versionComment",
    "logBin", "eventScheduler", "tcpListenerAbsent",
}

backup = {
    "receiptPath": BACKUP_RECEIPT_PATH,
    "runId": None,
    "bundleSchema": None,
    "backupReceiptSchema": None,
    "restoreReceiptSchema": None,
    "externalAnchorSchema": None,
    "planReceiptSha256": None,
    "sourceCommit": None,
    "sourceDatabaseServerUuid": None,
    "adminRootDependencyAdoptionReceiptSha256": None,
    "proven": False,
    "receiptAnchorMatched": False,
    "externalAnchorVerified": False,
    "adoptionReceiptBindingMatched": False,
    "sourceDatabaseServerUuidMatched": False,
    "backupRestoreSchemaFactsMatched": False,
    "sourceRestoreObservationManifestMatched": False,
    "restoredManifestObserved": False,
    "sourceSnapshotExactlyMatched": None,
    "sha256": None,
    "sizeBytes": None,
    "restoreProcedureVerified": False,
    "restoreLiveFactsMatched": False,
}
backup_receipt = pathlib.Path(BACKUP_RECEIPT_PATH)
if backup_receipt.is_file():
    backup_receipt_sha256 = sha256_file(backup_receipt)
    bundle = json.loads(backup_receipt.read_text(encoding="utf-8"))
    run_id = bundle.get("runId")
    run_root = pathlib.Path("/opt/fbsir/admin/backups/w1a")
    run_directory = run_root / str(run_id or "")

    def run_artifact(path_value, filename):
        if not isinstance(path_value, str):
            return None
        candidate = pathlib.Path(path_value)
        try:
            resolved = candidate.resolve(strict=True)
            resolved.relative_to(run_root.resolve(strict=True))
        except (FileNotFoundError, RuntimeError, ValueError):
            return None
        status = resolved.stat()
        if (
            resolved != (run_directory / filename).resolve()
            or not resolved.is_file()
            or resolved.is_symlink()
            or status.st_uid != 0
            or status.st_gid != 0
            or status.st_mode & 0o777 != 0o600
            or status.st_nlink != 1
        ):
            return None
        return resolved

    backup_receipt_file = run_artifact(
        bundle.get("backupReceiptPath"), "backup-receipt.json"
    )
    restore_receipt_file = run_artifact(
        bundle.get("restoreReceiptPath"), "restore-receipt.json"
    )
    artifact = run_artifact(bundle.get("backupPath"), "fbsir.sql.gpg")
    restore_verified = False
    actual_digest = None
    actual_size = None
    if backup_receipt_file and restore_receipt_file and artifact:
        source_receipt = json.loads(
            backup_receipt_file.read_text(encoding="utf-8")
        )
        restore_receipt = json.loads(
            restore_receipt_file.read_text(encoding="utf-8")
        )
        evidence_file = run_artifact(
            restore_receipt.get("isolationEvidencePath"),
            "isolation-evidence.json",
        )
        restore_log = run_artifact(
            restore_receipt.get("restoreLogPath"), "restore.log"
        )
        mysqlcheck_log = run_artifact(
            restore_receipt.get("mysqlcheckPath"), "mysqlcheck.log"
        )
        evidence = (
            json.loads(evidence_file.read_text(encoding="utf-8"))
            if evidence_file else {}
        )
        runtime_evidence = evidence.get("runtime") or {}
        actual_digest = sha256_file(artifact)
        actual_size = artifact.stat().st_size
        receipt_fields_exact = bool(
            set(bundle) == bundle_receipt_fields
            and set(source_receipt) == backup_receipt_fields
            and set(restore_receipt) == restore_receipt_fields
            and set(evidence) == isolation_evidence_fields
            and isinstance(runtime_evidence, dict)
            and set(runtime_evidence) == isolation_runtime_fields
            and isinstance(source_receipt.get("sourceFacts"), dict)
            and set(source_receipt["sourceFacts"]) == backup_fact_fields
            and isinstance(restore_receipt.get("restoredFacts"), dict)
            and set(restore_receipt["restoredFacts"]) == backup_fact_fields
        )
        plan_receipt_binding_matched = bool(
            re.fullmatch(
                r"[0-9a-f]{64}",
                str(bundle.get("planReceiptSha256") or ""),
            ) is not None
            and bundle.get("planReceiptSha256")
                == EXPECTED_BACKUP_PLAN_RECEIPT_SHA256
            and source_receipt.get("planReceiptSha256")
                == EXPECTED_BACKUP_PLAN_RECEIPT_SHA256
            and restore_receipt.get("planReceiptSha256")
                == EXPECTED_BACKUP_PLAN_RECEIPT_SHA256
        )
        try:
            generated_at = datetime.fromisoformat(
                str(bundle.get("generatedAt", "")).replace("Z", "+00:00")
            )
            age_seconds = (
                datetime.now(timezone.utc)
                - generated_at.astimezone(timezone.utc)
            ).total_seconds()
            receipt_fresh = 0 <= age_seconds <= 86400
        except Exception:
            receipt_fresh = False
        mysqlcheck_payload = (
            mysqlcheck_log.read_bytes() if mysqlcheck_log else b""
        )
        live_backup_facts = current_backup_schema_facts()
        live_backup_facts_compatible = bool(
            isinstance(restore_receipt.get("restoredFacts"), dict)
            and backup_static_control_facts(live_backup_facts)
                == backup_static_control_facts(
                    restore_receipt["restoredFacts"]
                )
            and backup_attribution_observations_dormant_safe(
                live_backup_facts
            )
            and backup_attribution_observations_dormant_safe(
                restore_receipt["restoredFacts"]
            )
            and backup_attribution_observations_monotonic(
                restore_receipt["restoredFacts"],
                live_backup_facts,
            )
        )
        backup_source_commit = bundle.get("sourceCommit")
        adoption_receipt_binding_matched = bool(
            EXPECTED_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256
            and bundle.get(
                "adminRootDependencyAdoptionReceiptSha256"
            ) == EXPECTED_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256
            and source_receipt.get(
                "adminRootDependencyAdoptionReceiptSha256"
            ) == EXPECTED_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256
            and restore_receipt.get(
                "adminRootDependencyAdoptionReceiptSha256"
            ) == EXPECTED_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256
        )
        source_database_uuid_matched = bool(
            re.fullmatch(
                r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                r"[0-9a-f]{4}-[0-9a-f]{12}",
                str(bundle.get("sourceDatabaseServerUuid") or ""),
            ) is not None
            and bundle.get("sourceDatabaseServerUuid")
                == source_receipt.get("sourceDatabaseServerUuid")
            and bundle.get("sourceDatabaseServerUuid")
                == restore_receipt.get("sourceDatabaseServerUuid")
            and bundle.get("sourceDatabaseServerUuid")
                == database.get("databaseServerUuid")
        )
        backup_restore_schema_facts_matched = bool(
            isinstance(source_receipt.get("sourceFacts"), dict)
            and isinstance(restore_receipt.get("restoredFacts"), dict)
            and backup_static_control_facts(
                source_receipt["sourceFacts"]
            ) == backup_static_control_facts(
                restore_receipt["restoredFacts"]
            )
            and backup_attribution_observations_dormant_safe(
                source_receipt["sourceFacts"]
            )
            and backup_attribution_observations_dormant_safe(
                restore_receipt["restoredFacts"]
            )
            and backup_attribution_observations_monotonic(
                source_receipt["sourceFacts"],
                restore_receipt["restoredFacts"],
            )
        )
        source_restore_observation_manifest_matched = bool(
            isinstance(source_receipt.get("sourceTotalRows"), int)
            and source_receipt.get("sourceTotalRows") >= 0
            and restore_receipt.get("restoredTotalRows")
                == source_receipt.get("sourceTotalRows")
            and re.fullmatch(
                r"[0-9a-f]{64}",
                str(
                    source_receipt.get(
                        "sourceTableRowCountsSha256"
                    ) or ""
                ),
            ) is not None
            and restore_receipt.get("restoredTableRowCountsSha256")
                == source_receipt.get("sourceTableRowCountsSha256")
        )
        source_observation_manifest_valid = bool(
            isinstance(source_receipt.get("sourceTotalRows"), int)
            and source_receipt.get("sourceTotalRows") >= 0
            and re.fullmatch(
                r"[0-9a-f]{64}",
                str(
                    source_receipt.get(
                        "sourceTableRowCountsSha256"
                    ) or ""
                ),
            ) is not None
        )
        restored_manifest_observed = bool(
            isinstance(restore_receipt.get("restoredTotalRows"), int)
            and restore_receipt.get("restoredTotalRows") >= 0
            and re.fullmatch(
                r"[0-9a-f]{64}",
                str(
                    restore_receipt.get(
                        "restoredTableRowCountsSha256"
                    ) or ""
                ),
            ) is not None
        )
        restore_verified = bool(
            receipt_fields_exact
            and plan_receipt_binding_matched
            and bundle.get("schema")
                == "fbsir.u3wDatabaseBackupRestoreBundleReceipt.v3"
            and re.fullmatch(
                r"w1a-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}",
                str(run_id or ""),
            )
            and re.fullmatch(
                r"[0-9a-f]{40}",
                str(backup_source_commit or ""),
            ) is not None
            and bundle.get("targetHost") == TARGET_HOST
            and bundle.get("database") == "fbsir"
            and bundle.get("runnerSha256")
                == EXPECTED_BACKUP_RUNNER_SHA256
            and bundle.get("backupWorkerSha256")
                == EXPECTED_BACKUP_WORKER_SHA256
            and bundle.get("verifierSha256")
                == EXPECTED_RESTORE_VERIFIER_SHA256
            and bundle.get("productionBusinessStateChanged") is False
            and adoption_receipt_binding_matched
            and source_database_uuid_matched
            and bundle.get("backupReceiptSha256")
                == sha256_file(backup_receipt_file)
            and bundle.get("restoreReceiptSha256")
                == sha256_file(restore_receipt_file)
            and bundle.get("backupSha256") == actual_digest
            and bundle.get("backupSizeBytes") == actual_size
            and actual_size > 0
            and source_receipt.get("schema")
                == "fbsir.u3wDatabaseBackupReceipt.v4"
            and source_receipt.get("runId") == run_id
            and source_receipt.get("sourceCommit")
                == backup_source_commit
            and source_receipt.get("runnerSha256")
                == EXPECTED_BACKUP_RUNNER_SHA256
            and source_receipt.get("backupWorkerSha256")
                == EXPECTED_BACKUP_WORKER_SHA256
            and re.fullmatch(
                r"[0-9a-f]{64}",
                str(source_receipt.get("sourceJarSha256") or ""),
            ) is not None
            and source_receipt.get("backupSha256") == actual_digest
            and source_receipt.get("backupSizeBytes") == actual_size
            and source_receipt.get("encryptionContract")
                == "u3w.gnupg-aes256-symmetric.v1"
            and source_receipt.get("ddlProtectionMode")
                == (
                    "HOST_FLOCK_NAMED_LOCK_READ_ONLY_SNAPSHOT_"
                    "FULL_OBJECT_MDL_PRE_POST_STABILITY_"
                    "AND_APPROVED_NO_DDL_WINDOW"
                )
            and restore_receipt.get("schema")
                == "fbsir.u3wDatabaseRestoreRehearsalReceipt.v4"
            and restore_receipt.get("runId") == run_id
            and restore_receipt.get("sourceCommit")
                == backup_source_commit
            and restore_receipt.get("sourceBackupSha256") == actual_digest
            and restore_receipt.get("sourceBackupReceiptSha256")
                == sha256_file(backup_receipt_file)
            and backup_restore_schema_facts_matched
            and live_backup_facts_compatible
            and source_receipt.get("sourceSnapshotExactlyMatched") is False
            and source_receipt.get("businessDatabaseChanged") is False
            and source_receipt.get("serviceChanged") is False
            and source_receipt.get("officialExpertsPackageChanged") is False
            and source_observation_manifest_valid
            and isinstance(restore_receipt.get("restoredTotalRows"), int)
            and restored_manifest_observed
            and restore_receipt.get("isolatedTarget") is True
            and restore_receipt.get("isolatedNetworkingDisabled") is True
            and restore_receipt.get("isolatedDataRemoved") is True
            and restore_receipt.get("businessDatabaseChanged") is False
            and restore_receipt.get("serviceChanged") is False
            and restore_receipt.get("officialExpertsPackageChanged") is False
            and restore_receipt.get("verifierSha256")
                == EXPECTED_RESTORE_VERIFIER_SHA256
            and evidence_file
            and restore_receipt.get("isolationEvidenceSha256")
                == sha256_file(evidence_file)
            and restore_log
            and restore_receipt.get("restoreLogSha256")
                == sha256_file(restore_log)
            and mysqlcheck_log
            and restore_receipt.get("mysqlcheckSha256")
                == sha256_file(mysqlcheck_log)
            and b"OK" in mysqlcheck_payload
            and evidence.get("schema")
                == "fbsir.u3wIsolatedMysqlEvidence.v1"
            and evidence.get("runId") == run_id
            and evidence.get("sourceCommit") == backup_source_commit
            and evidence.get("productionMysqldPidBefore")
                == evidence.get("productionMysqldPidAfter")
            and evidence.get("mysqlcheckExitCode") == 0
            and isinstance(evidence.get("mysqlcheckOkObjectCount"), int)
            and evidence.get("mysqlcheckOkObjectCount") > 0
            and all(
                evidence.get(field) is True
                for field in (
                    "isolatedProcessExited",
                    "isolatedSocketRemoved",
                    "isolatedPidFileRemoved",
                    "isolatedDatadirRemoved",
                    "isolatedRuntimeDirectoryRemoved",
                )
            )
            and runtime_evidence.get("skipNetworking") is True
            and runtime_evidence.get("tcpListenerAbsent") is True
            and runtime_evidence.get("logBin") is False
            and runtime_evidence.get("eventScheduler") == "OFF"
            and runtime_evidence.get("version")
                == source_receipt.get("serverVersion")
            and runtime_evidence.get("versionComment")
                == source_receipt.get("serverVersionComment")
            and not pathlib.Path(
                "/var/lib/fbsir-w1a-restore", str(run_id)
            ).exists()
            and not pathlib.Path(
                "/run/fbsir-w1a-restore", str(run_id)
            ).exists()
            and receipt_fresh
        )
    backup.update({
        "runId": run_id,
        "bundleSchema": bundle.get("schema"),
        "backupReceiptSchema": (
            source_receipt.get("schema")
            if "source_receipt" in locals() else None
        ),
        "restoreReceiptSchema": (
            restore_receipt.get("schema")
            if "restore_receipt" in locals() else None
        ),
        "planReceiptSha256": bundle.get("planReceiptSha256"),
        "proven": restore_verified,
        "sourceCommit": (
            bundle.get("sourceCommit")
            if re.fullmatch(
                r"[0-9a-f]{40}",
                str(bundle.get("sourceCommit") or ""),
            ) is not None
            else None
        ),
        "sha256": actual_digest,
        "sizeBytes": actual_size,
        "sourceDatabaseServerUuid":
            bundle.get("sourceDatabaseServerUuid"),
        "adminRootDependencyAdoptionReceiptSha256":
            bundle.get(
                "adminRootDependencyAdoptionReceiptSha256"
            ),
        "adoptionReceiptBindingMatched": (
            adoption_receipt_binding_matched
            if "adoption_receipt_binding_matched" in locals()
            else False
        ),
        "sourceDatabaseServerUuidMatched": (
            source_database_uuid_matched
            if "source_database_uuid_matched" in locals()
            else False
        ),
        "backupRestoreSchemaFactsMatched": (
            backup_restore_schema_facts_matched
            if "backup_restore_schema_facts_matched" in locals()
            else False
        ),
        "sourceRestoreObservationManifestMatched": (
            source_restore_observation_manifest_matched
            if "source_restore_observation_manifest_matched" in locals()
            else False
        ),
        "restoredManifestObserved": (
            restored_manifest_observed
            if "restored_manifest_observed" in locals()
            else False
        ),
        "sourceSnapshotExactlyMatched": (
            source_receipt.get("sourceSnapshotExactlyMatched")
            if "source_receipt" in locals() else None
        ),
        "restoreProcedureVerified": restore_verified,
        "restoreLiveFactsMatched": restore_verified,
        "receiptAnchorMatched": (
            bool(EXPECTED_BACKUP_RECEIPT_SHA256)
            and backup_receipt_sha256 == EXPECTED_BACKUP_RECEIPT_SHA256
        ),
    })

nginx_configs, nginx_dump_sha256 = active_nginx_manifest()

deployment = {
    "state": None,
    "receiptValidated": False,
    "receiptAnchorMatched": False,
    "sourceCommit": None,
    "strictHeadBuildUploadSwitchReceiptScriptPresent": False,
    "applicationRollbackAssemblyVerified": False,
    "applicationRollbackProven": False,
    "databaseRollbackSafetyProven": False,
    "stableDatabaseIdentityMatched": False,
    "stagedLiveStateMatched": False,
    "rollbackLiveStateMatched": False,
    "recoveryLiveStateMatched": False,
    "releasePlanTargetBindingVerified": False,
    "releasePlanTargetSha256": None,
    "releasePlanTargetComparableSha256": None,
    "databaseDownClaimed": None,
    "actualActiveArtifactsMatched": False,
    "currentLinkResolved": None,
    "frontendTreeSha256": None,
    "u3wDirectCaptchaHealthy": False,
    "mePortalApiHealthy": False,
    "adminPortalApiHealthy": False,
    "mePortalReleaseMarkerMatched": False,
    "adminPortalReleaseMarkerMatched": False,
    "defaultOffIngressProbeVerified": False,
    "defaultOffIngressProbe": None,
    "receiptPath": DEPLOYMENT_RECEIPT_PATH,
}
deployment_receipt = pathlib.Path(DEPLOYMENT_RECEIPT_PATH)
rollback_latest = False
rollback_valid = False
recovery_latest = False
recovery_valid = False
recovery_live_state_matched = False
resolved_latest = None
latest_status = None
latest_sha256 = None
latest_document = None
latest = {}

def rollback_source_allowed(source_commit):
    return bool(
        re.fullmatch(r"[0-9a-f]{40}", str(source_commit or ""))
        and (
            not EXPECTED_DEPLOYMENT_RECEIPT_SHA256
            or source_commit == EXPECTED_SOURCE_COMMIT
        )
    )

if deployment_receipt.is_file():
    resolved_latest = deployment_receipt.resolve(strict=True)
    latest_status = resolved_latest.stat()
    latest_sha256 = sha256_file(resolved_latest)
    latest_document = json.loads(
        resolved_latest.read_text(encoding="utf-8")
    )
    latest = dict(latest_document)
    if (
        latest.get("schema")
            == INTERRUPTED_APPLY_RECOVERY_RECEIPT_SCHEMA
    ):
        recovery_latest = True
        recovery = latest
        release_root = pathlib.Path(
            "/opt/fbsir/admin/releases"
        ).resolve(strict=True)
        release_directory = resolved_latest.parent
        try:
            release_status = release_directory.lstat()
            stage_path = pathlib.Path(
                str(recovery.get("stageReceiptPath") or "")
            )
            failure_path = pathlib.Path(
                str(recovery.get("applyFailureReceiptPath") or "")
            )
            stage_manifest = regular_file_manifest(
                stage_path, allowed_modes=(0o600,)
            )
            failure_manifest_file = regular_file_manifest(
                failure_path, allowed_modes=(0o600,)
            )
            stage = json.loads(
                stage_path.read_text(encoding="utf-8")
            )
            terminal_failure = json.loads(
                failure_path.read_text(encoding="utf-8")
            )
            recorded_failure_manifest = recovery.get(
                "applyFailureReceiptManifest"
            )
            actual_failure_manifest = []
            failure_manifest_valid = bool(
                isinstance(recorded_failure_manifest, list)
                and len(recorded_failure_manifest) > 0
            )
            for failure_candidate in sorted(
                release_directory.glob("apply-failure-*.json")
            ):
                candidate_manifest = regular_file_manifest(
                    failure_candidate, allowed_modes=(0o600,)
                )
                candidate_receipt = json.loads(
                    failure_candidate.read_text(encoding="utf-8")
                )
                candidate_valid = bool(
                    re.fullmatch(
                        r"apply-failure-\d{8}T\d{12}Z-"
                        r"[0-9a-f]{12}\.json",
                        failure_candidate.name,
                    ) is not None
                    and candidate_receipt.get("schema") in {
                        LEGACY_APPLY_FAILURE_RECEIPT_SCHEMA,
                        APPLY_FAILURE_RECEIPT_SCHEMA,
                    }
                    and candidate_receipt.get("state")
                        in APPLY_FAILURE_STATES
                    and candidate_receipt.get("releaseId")
                        == release_directory.name
                    and candidate_receipt.get("sourceCommit")
                        == recovery.get("sourceCommit")
                    and candidate_receipt.get(
                        "officialExpertsPackageChanged"
                    ) is False
                    and re.fullmatch(
                        r"[0-9a-f]{64}",
                        str(candidate_receipt.get(
                            "applyApprovalReceiptSha256"
                        ) or ""),
                    ) is not None
                )
                failure_manifest_valid = bool(
                    failure_manifest_valid and candidate_valid
                )
                actual_failure_manifest.append({
                    "name": failure_candidate.name,
                    "sha256": candidate_manifest["sha256"],
                    "schema": candidate_receipt.get("schema"),
                    "state": candidate_receipt.get("state"),
                })
            failure_manifest_valid = bool(
                failure_manifest_valid
                and actual_failure_manifest
                    == recorded_failure_manifest
                and hashlib.sha256(
                    canonical_json(
                        actual_failure_manifest
                    ).encode("utf-8")
                ).hexdigest()
                    == recovery.get(
                        "applyFailureReceiptManifestSha256"
                    )
                and any(
                    item.get("name") == failure_path.name
                    and item.get("sha256")
                        == recovery.get(
                            "applyFailureReceiptSha256"
                        )
                    for item in actual_failure_manifest
                )
            )
            application_assembly_path = pathlib.Path(
                str(stage.get(
                    "applicationRollbackAssemblyReceiptPath"
                ) or "")
            )
            application_assembly_manifest = regular_file_manifest(
                application_assembly_path, allowed_modes=(0o600,)
            )
            application_assembly = json.loads(
                application_assembly_path.read_text(encoding="utf-8")
            )
            rollback_dropin = (
                release_directory
                / "evidence/rollback-systemd-dropin.conf"
            )
            rollback_dropin_manifest = regular_file_manifest(
                rollback_dropin, allowed_modes=(0o600,)
            )
            active_dropin = pathlib.Path(
                "/etc/systemd/system/fbsir-admin.service.d/"
                "20-u3w-default-off-release.conf"
            )
            active_dropin_manifest = regular_file_manifest(
                active_dropin, allowed_modes=(0o644,)
            )
            active_nginx = pathlib.Path(
                "/etc/nginx/conf.d/u3w-placeholder-sites.conf"
            )
            active_nginx_manifest = regular_file_manifest(active_nginx)
            predecessor_jar = (
                release_directory / "rollback/previous-admin.jar"
            )
            predecessor_jar_manifest = regular_file_manifest(
                predecessor_jar, allowed_modes=(0o600,)
            )
            baseline_snapshot = application_assembly.get(
                "serviceSnapshotBeforeStage", {}
            )
            rollback_argv_sha256 = hashlib.sha256(
                (
                    "\0".join([
                        "/usr/bin/java",
                        (
                            "-Dspring.config.additional-location=file:"
                            "/opt/fbsir/admin/application-connector.yml"
                        ),
                        "-jar",
                        str(predecessor_jar),
                    ]) + "\0"
                ).encode("utf-8")
            ).hexdigest()
            expected_dropin_manifest = [
                item
                for item in baseline_snapshot.get("dropInManifest", [])
                if item.get("path") != str(active_dropin)
            ] + [{
                "path": str(active_dropin),
                "sha256": rollback_dropin_manifest["sha256"],
                "mode": 0o644,
            }]
            expected_dropin_manifest.sort(
                key=lambda item: item["path"]
            )
            configured_predecessor_matched = bool(
                snapshot_exact_loaded_from_baseline(
                    live_service_snapshot, baseline_snapshot
                )
                or (
                    configuration_receipt_valid
                    and authorized_admin_engine_configuration_evolution_matches(
                        live_service_snapshot,
                        baseline_snapshot,
                        configuration_receipt,
                        configuration_predecessor_receipt,
                    )
                )
            )
            committed_receipts_absent = all(
                not candidate.exists()
                and not candidate.is_symlink()
                for candidate in (
                    release_directory / "deployment-receipt.json",
                    release_directory / "rollback-receipt.json",
                    release_directory
                        / "rollback-verification-receipt.json",
                )
            )
            current_link = pathlib.Path("/opt/fbsir/admin/current")
            current_link_absent = bool(
                not current_link.exists()
                and not current_link.is_symlink()
            )
            recovery_observed = datetime.fromisoformat(
                str(recovery.get("observedAt") or "").replace(
                    "Z", "+00:00"
                )
            )
            terminal_observed = datetime.fromisoformat(
                str(terminal_failure.get("observedAt") or "").replace(
                    "Z", "+00:00"
                )
            )
            recovery_live_state_matched = bool(
                configured_predecessor_matched
                and live_service_snapshot.get("activeState") == "active"
                and live_service_snapshot.get("workingDirectory")
                    == "/opt/fbsir/admin"
                and live_service_snapshot.get("configuredJarPath")
                    == str(predecessor_jar)
                and live_service_snapshot.get("processJarPath")
                    == str(predecessor_jar)
                and live_service_snapshot.get("configuredJarSha256")
                    == predecessor_jar_manifest["sha256"]
                    == application_assembly.get("previousJarSha256")
                and live_service_snapshot.get("processJarSha256")
                    == predecessor_jar_manifest["sha256"]
                and live_service_snapshot.get("jarSha256")
                    == predecessor_jar_manifest["sha256"]
                and live_service_snapshot.get("processArgvSha256")
                    == rollback_argv_sha256
                and live_service_snapshot.get("dropInManifest")
                    == expected_dropin_manifest
                and active_dropin_manifest["sha256"]
                    == rollback_dropin_manifest["sha256"]
                and active_nginx_manifest["sha256"]
                    == application_assembly.get("previousNginxSha256")
                and stable_database_identity_matches(
                    stage.get("preStageRuntimeIdentity", {}),
                    database,
                )
                and recorded_recovery_migration_matches_live(
                    recovery.get("retainedMigrationFacts"),
                    database,
                )
                and snapshot_default_off(
                    live_service_snapshot,
                    ("EXACT_CONFIGURED",),
                )
                and current_link_absent
                and committed_receipts_absent
            )
            terminal_failure_valid = bool(
                terminal_failure.get("schema")
                    == LEGACY_APPLY_FAILURE_RECEIPT_SCHEMA
                and terminal_failure.get("state")
                    == (
                        "APPLICATION_RESTORED_DATABASE_043_"
                        "RETAINED_OR_FAIL_CLOSED"
                    )
                and terminal_failure.get("releaseId")
                    == release_directory.name
                and terminal_failure.get("sourceCommit")
                    == recovery.get("sourceCommit")
                and terminal_failure.get("applicationStarted") is True
                and terminal_failure.get(
                    "applicationAlreadyCommitted"
                ) is False
                and terminal_failure.get("applicationRestored") is True
                and terminal_failure.get("topologyRestored") is True
                and terminal_failure.get("deploymentCommitOutcome")
                    == "NOT_COMMITTED"
                and terminal_failure.get("deploymentReceiptPath") is None
                and terminal_failure.get("deploymentReceiptSha256") is None
                and terminal_failure.get("databaseRollbackStrategy")
                    == "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
                and terminal_failure.get("databaseDownClaimed") is False
                and legacy_w1a_migration_facts(
                    terminal_failure.get("migrationFacts")
                )
                and terminal_failure.get(
                    "migrationFacts", {}
                ).get("eventCount") == 0
                and terminal_failure.get(
                    "migrationFacts", {}
                ).get("journeyCount") == 0
                and terminal_failure.get(
                    "officialExpertsPackageChanged"
                ) is False
            )
            stage_valid = bool(
                stage_path == (
                    release_directory
                    / "deployment-readiness-receipt.json"
                )
                and stage_manifest["sha256"]
                    == recovery.get("stageReceiptSha256")
                and stage.get("schema")
                    == LEGACY_DEPLOYMENT_RECEIPT_SCHEMA
                and stage.get("state") == "STAGED_FOR_SWITCH"
                and stage.get("releaseId") == release_directory.name
                and stage.get("sourceCommit")
                    == recovery.get("sourceCommit")
                and stage.get("databaseDownClaimed") is False
                and stage.get("productionDatabaseChanged") is False
                and stage.get("productionServiceChanged") is False
                and stage.get("officialExpertsPackageChanged") is False
                and application_assembly_path == (
                    release_directory
                    / "evidence/application-rollback-assembly.json"
                )
                and application_assembly_manifest["sha256"]
                    == stage.get(
                        "applicationRollbackAssemblyReceiptSha256"
                    )
                and application_assembly.get("schema")
                    == "fbsir.u3wApplicationRollbackAssemblyReceipt.v1"
                and application_assembly.get("releaseId")
                    == release_directory.name
                and application_assembly.get("sourceCommit")
                    == recovery.get("sourceCommit")
                and application_assembly.get("assemblyVerified") is True
                and application_assembly.get(
                    "applicationRollbackProven"
                ) is False
                and application_assembly.get("strategy")
                    == (
                        "IMMUTABLE_PREDECESSOR_DROPIN_RESTORE_NGINX_"
                        "AND_RESTART"
                    )
                and application_assembly.get(
                    "immutablePreviousJarPath"
                ) == str(predecessor_jar)
                and application_assembly.get("previousJarSha256")
                    == predecessor_jar_manifest["sha256"]
                and application_assembly.get("previousNginxPath")
                    == str(active_nginx)
                and application_assembly.get("previousNginxSha256")
                    == active_nginx_manifest["sha256"]
                and application_assembly.get(
                    "symlinkForwardAndReverseVerified"
                ) is True
                and application_assembly.get(
                    "productionServiceChanged"
                ) is False
                and application_assembly.get(
                    "productionDatabaseChanged"
                ) is False
            )
            recovery_valid = bool(
                set(recovery)
                    == INTERRUPTED_APPLY_RECOVERY_RECEIPT_FIELDS
                and deployment_receipt.is_symlink()
                and resolved_latest == (
                    release_directory
                    / "interrupted-apply-recovery-receipt.json"
                )
                and release_directory.parent == release_root
                and re.fullmatch(
                    r"w1a-release-[0-9a-f]{12}-"
                    r"\d{8}T\d{6}Z",
                    release_directory.name,
                ) is not None
                and stat.S_ISDIR(release_status.st_mode)
                and not stat.S_ISLNK(release_status.st_mode)
                and release_status.st_uid == 0
                and release_status.st_gid == 0
                and release_status.st_nlink >= 2
                and not release_status.st_mode & 0o022
                and latest_status.st_uid == 0
                and latest_status.st_gid == 0
                and latest_status.st_nlink == 1
                and latest_status.st_mode & 0o777 == 0o600
                and recovery.get("state")
                    == INTERRUPTED_APPLY_RECOVERY_STATE
                and recovery.get("releaseId")
                    == release_directory.name
                and rollback_source_allowed(
                    recovery.get("sourceCommit")
                )
                and re.fullmatch(
                    r"w1a-release-[0-9a-f]{12}-"
                    r"\d{8}T\d{6}Z",
                    str(recovery.get("recoveryRunId") or ""),
                ) is not None
                and re.fullmatch(
                    r"[0-9a-f]{40}",
                    str(recovery.get("executorSourceCommit") or ""),
                ) is not None
                and recovery.get("releaseId")
                    != recovery.get("recoveryRunId")
                and recovery.get("sourceCommit")
                    != recovery.get("executorSourceCommit")
                and all(
                    re.fullmatch(
                        r"[0-9a-f]{64}",
                        str(recovery.get(field) or ""),
                    ) is not None
                    for field in (
                        "approvalReceiptSha256",
                        "runnerSha256",
                        "workerSha256",
                        "recoveryPlanReceiptSha256",
                        "stageReceiptSha256",
                        "applyFailureReceiptSha256",
                        "applyFailureReceiptManifestSha256",
                    )
                )
                and re.fullmatch(
                    r"[0-9a-f]{32}",
                    str(recovery.get("approvalNonce") or ""),
                ) is not None
                and failure_path.parent == release_directory
                and re.fullmatch(
                    r"apply-failure-\d{8}T\d{12}Z-"
                    r"[0-9a-f]{12}\.json",
                    failure_path.name,
                ) is not None
                and failure_manifest_file["sha256"]
                    == recovery.get("applyFailureReceiptSha256")
                and recovery.get("applyFailureReceiptSchema")
                    == terminal_failure.get("schema")
                and recovery.get("applyFailureReceiptState")
                    == terminal_failure.get("state")
                and failure_manifest_valid
                and terminal_failure_valid
                and stage_valid
                and interrupted_recovery_historic_anchors_valid(
                    release_directory, recovery
                )
                and recovery.get("applicationRestored") is True
                and recovery.get("topologyRestored") is True
                and recovery.get("deploymentCommitOutcome")
                    == "NOT_COMMITTED"
                and recovery.get("deploymentReceiptAbsent") is True
                and recovery.get("rollbackReceiptAbsent") is True
                and recovery.get("currentLinkAbsent") is True
                and recovery.get("releaseDropInMatched") is True
                and recovery_live_state_matched
                and recovery.get("allW1aFlagsExplicitFalse") is True
                and recovery.get("databaseDownClaimed") is False
                and recovery.get("productionFilesystemChanged") is True
                and recovery.get("productionDatabaseChanged") is True
                and recovery.get(
                    "productionDatabaseChangedThisRecoveryRun"
                ) is False
                and recovery.get(
                    "productionDatabaseChangedSinceStage"
                ) is True
                and recovery.get("productionServiceChanged") is True
                and recovery.get(
                    "productionServiceChangedThisRecoveryRun"
                ) is False
                and recovery.get(
                    "productionServiceChangedSinceStage"
                ) is True
                and recovery.get(
                    "officialExpertsPackageChanged"
                ) is False
                and recovery_observed.tzinfo is not None
                and terminal_observed.tzinfo is not None
                and recovery_observed > terminal_observed
            )
        except (
            OSError, ValueError, TypeError, KeyError,
            json.JSONDecodeError
        ):
            recovery_valid = False
            recovery_live_state_matched = False
        deployment.update({
            "state": (
                recovery.get("state") if recovery_valid else None
            ),
            "receiptValidated": recovery_valid,
            "receiptAnchorMatched": bool(
                recovery_valid
                and EXPECTED_DEPLOYMENT_RECEIPT_SHA256
                and latest_sha256
                    == EXPECTED_DEPLOYMENT_RECEIPT_SHA256
            ),
            "sourceCommit": (
                recovery.get("sourceCommit")
                if recovery_valid else None
            ),
            "strictHeadBuildUploadSwitchReceiptScriptPresent":
                recovery_valid,
            "applicationRollbackAssemblyVerified": recovery_valid,
            "applicationRollbackProven": recovery_valid,
            "databaseRollbackSafetyProven": bool(
                recovery_valid and recovery_live_state_matched
            ),
            "stableDatabaseIdentityMatched": bool(
                recovery_valid and recovery_live_state_matched
            ),
            "stagedLiveStateMatched": False,
            "rollbackLiveStateMatched": False,
            "recoveryLiveStateMatched": bool(
                recovery_valid and recovery_live_state_matched
            ),
            "databaseDownClaimed":
                recovery.get("databaseDownClaimed"),
            "actualActiveArtifactsMatched": False,
            "currentLinkResolved": None,
            "frontendTreeSha256": None,
            "u3wDirectCaptchaHealthy": (
                http_json_status(
                    "http://127.0.0.1:8080/captchaImage"
                ) == {"httpStatus": 200, "businessCode": 200}
            ),
            "mePortalApiHealthy": False,
            "adminPortalApiHealthy": False,
        })
    elif latest.get("schema") in {
        LEGACY_ROLLBACK_RECEIPT_SCHEMA,
        ROLLBACK_RECEIPT_SCHEMA,
        LEGACY_ROLLBACK_VERIFICATION_SCHEMA,
        ROLLBACK_VERIFICATION_SCHEMA,
    }:
        rollback_latest = True
        release_directory = resolved_latest.parent
        rollback_chain_valid = True
        if (
            latest.get("schema") in {
                LEGACY_ROLLBACK_VERIFICATION_SCHEMA,
                ROLLBACK_VERIFICATION_SCHEMA,
            }
        ):
            verification = latest
            original_path = (
                release_directory / "rollback-receipt.json"
            )
            try:
                original_status = original_path.stat()
                original = json.loads(
                    original_path.read_text(encoding="utf-8")
                )
                rollback_chain_valid = bool(
                    verification.get("state")
                        == (
                            "ROLLED_BACK_APPLICATION_DATABASE_043_"
                            "RETAINED_DORMANT"
                    )
                    and verification.get("releaseId")
                        == release_directory.name
                    and rollback_source_allowed(
                        verification.get("sourceCommit")
                    )
                    and verification.get("rollbackReceiptPath")
                        == str(original_path)
                    and verification.get("rollbackReceiptSha256")
                        == sha256_file(original_path)
                    and verification.get("databaseSafetyCurrentRead")
                        == "VERIFIED"
                    and verification.get(
                        "databaseRollbackStrategy"
                    ) == "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
                    and verification.get("databaseDownClaimed") is False
                    and verification.get(
                        "productionFilesystemChanged"
                    ) is True
                    and verification.get(
                        "productionDatabaseChanged"
                    ) is False
                    and verification.get(
                        "productionServiceChanged"
                    ) is False
                    and verification.get(
                        "officialExpertsPackageChanged"
                    ) is False
                    and recorded_w1a_migration_facts_valid(
                        verification.get("retainedMigrationFacts"),
                        verification.get("schema"),
                    )
                    and original_status.st_uid == 0
                    and original_status.st_gid == 0
                    and original_status.st_nlink == 1
                    and original_status.st_mode & 0o777 == 0o600
                    and original.get("schema") in {
                        LEGACY_ROLLBACK_RECEIPT_SCHEMA,
                        ROLLBACK_RECEIPT_SCHEMA,
                    }
                    and original.get("state")
                        == (
                            "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_"
                            "READ_UNAVAILABLE"
                        )
                    and original.get("releaseId")
                        == verification.get("releaseId")
                    and original.get("sourceCommit")
                        == verification.get("sourceCommit")
                    and original.get("deploymentReceiptPath")
                        == verification.get("deploymentReceiptPath")
                    and original.get("deploymentReceiptSha256")
                        == verification.get(
                            "deploymentReceiptSha256"
                        )
                    and original.get(
                        "applicationRollbackVerified"
                    ) is True
                    and original.get("retainedRollbackDropIn") is True
                    and original.get("databaseRollbackStrategy")
                        == "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
                    and original.get("databaseDownClaimed") is False
                    and original.get(
                        "databaseSafetyCurrentRead", ""
                    ).startswith("UNAVAILABLE:")
                    and original.get("retainedMigrationFacts") is None
                    and original.get(
                        "allW1aFlagsExplicitFalse"
                    ) is True
                    and original.get(
                        "productionFilesystemChanged"
                    ) is True
                    and original.get(
                        "productionDatabaseChanged"
                    ) is False
                    and type(original.get(
                        "productionServiceChanged"
                    )) is bool
                    and original.get(
                        "officialExpertsPackageChanged"
                    ) is False
                )
                effective = dict(original)
                effective["state"] = verification["state"]
                effective["databaseSafetyCurrentRead"] = "VERIFIED"
                effective["retainedMigrationFacts"] = verification[
                    "retainedMigrationFacts"
                ]
                effective["retainedMigrationFactsSchema"] = verification[
                    "schema"
                ]
                latest = effective
            except (
                OSError, ValueError, TypeError, KeyError,
                json.JSONDecodeError
            ):
                rollback_chain_valid = False
                latest = {}
        deployment_path = pathlib.Path(
            str(latest.get("deploymentReceiptPath") or "")
        )
        rollback_dropin = (
            release_directory
            / "evidence/rollback-systemd-dropin.conf"
        )
        application_assembly_path = (
            release_directory
            / "evidence/application-rollback-assembly.json"
        )
        retained_database_current_read = False
        try:
            deployment_source = json.loads(
                deployment_path.read_text(encoding="utf-8")
            )
            application_assembly = json.loads(
                application_assembly_path.read_text(encoding="utf-8")
            )
            baseline_snapshot = application_assembly.get(
                "serviceSnapshotBeforeStage", {}
            )
            predecessor_jar_path = str(
                release_directory / "rollback/previous-admin.jar"
            )
            rollback_argv_sha256 = hashlib.sha256(
                (
                    "\0".join([
                        "/usr/bin/java",
                        (
                            "-Dspring.config.additional-location=file:"
                            "/opt/fbsir/admin/application-connector.yml"
                        ),
                        "-jar",
                        predecessor_jar_path,
                    ]) + "\0"
                ).encode("utf-8")
            ).hexdigest()
            rollback_dropin_manifest = [
                item
                for item in baseline_snapshot.get("dropInManifest", [])
                if item.get("path") != (
                    "/etc/systemd/system/fbsir-admin.service.d/"
                    "20-u3w-default-off-release.conf"
                )
            ] + [{
                "path": (
                    "/etc/systemd/system/fbsir-admin.service.d/"
                    "20-u3w-default-off-release.conf"
                ),
                "sha256": sha256_file(rollback_dropin),
                "mode": 0o644,
            }]
            rollback_dropin_manifest.sort(
                key=lambda item: item["path"]
            )
            rollback_live_state_matched = bool(
                (
                    snapshot_exact_loaded_from_baseline(
                        live_service_snapshot, baseline_snapshot
                    )
                    or (
                        configuration_receipt_valid
                        and authorized_admin_engine_configuration_evolution_matches(
                            live_service_snapshot,
                            baseline_snapshot,
                            configuration_receipt,
                            configuration_predecessor_receipt,
                        )
                    )
                )
                and live_service_snapshot.get("activeState") == "active"
                and live_service_snapshot.get("workingDirectory")
                    == "/opt/fbsir/admin"
                and live_service_snapshot.get("configuredJarPath")
                    == predecessor_jar_path
                and live_service_snapshot.get("processJarPath")
                    == predecessor_jar_path
                and live_service_snapshot.get("configuredJarSha256")
                    == application_assembly.get("previousJarSha256")
                and live_service_snapshot.get("processJarSha256")
                    == application_assembly.get("previousJarSha256")
                and live_service_snapshot.get("jarSha256")
                    == application_assembly.get("previousJarSha256")
                and live_service_snapshot.get("processArgvSha256")
                    == rollback_argv_sha256
                and live_service_snapshot.get("dropInManifest")
                    == rollback_dropin_manifest
                and stable_database_identity_matches(
                    deployment_source.get("preStageRuntimeIdentity", {}),
                    database,
                )
            )
            retained_migration = latest.get("retainedMigrationFacts")
            retained_migration_schema = latest.get(
                "retainedMigrationFactsSchema",
                latest.get("schema"),
            )
            retained_database_current_read = bool(
                recorded_w1a_migration_matches_live(
                    retained_migration,
                    retained_migration_schema,
                    database,
                )
            )
            rollback_valid = bool(
                rollback_chain_valid
                and
                latest_status.st_uid == 0
                and latest_status.st_gid == 0
                and latest_status.st_nlink == 1
                and latest_status.st_mode & 0o777 == 0o600
                and latest.get("state")
                    == (
                        "ROLLED_BACK_APPLICATION_DATABASE_043_"
                        "RETAINED_DORMANT"
                )
                and latest.get("releaseId") == release_directory.name
                and rollback_source_allowed(latest.get("sourceCommit"))
                and latest.get("applicationRollbackVerified") is True
                and latest.get("retainedRollbackDropIn") is True
                and latest.get("databaseRollbackStrategy")
                    == "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
                and latest.get("databaseDownClaimed") is False
                and latest.get("databaseSafetyCurrentRead") == "VERIFIED"
                and recorded_w1a_migration_facts_valid(
                    retained_migration, retained_migration_schema
                )
                and latest.get("allW1aFlagsExplicitFalse") is True
                and latest.get("productionFilesystemChanged") is True
                and latest.get("productionDatabaseChanged") is False
                and type(latest.get("productionServiceChanged")) is bool
                and latest.get("officialExpertsPackageChanged") is False
                and type(
                    latest.get("recoveredExistingSideEffects")
                ) is bool
                and isinstance(latest.get("serviceBefore"), dict)
                and isinstance(latest.get("serviceAfter"), dict)
                and deployment_path.parent == release_directory
                and deployment_path.name == "deployment-receipt.json"
                and deployment_path.is_file()
                and not deployment_path.is_symlink()
                and sha256_file(deployment_path)
                    == latest.get("deploymentReceiptSha256")
                and deployment_source.get("schema") in {
                    LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
                    DEPLOYMENT_RECEIPT_SCHEMA,
                }
                and deployment_source.get("state")
                    == "DEPLOYED_DEFAULT_OFF"
                and application_assembly_path.is_file()
                and application_assembly.get("schema")
                    == "fbsir.u3wApplicationRollbackAssemblyReceipt.v1"
                and application_assembly.get("assemblyVerified") is True
                and application_assembly.get(
                    "applicationRollbackProven"
                ) is False
                and rollback_dropin.is_file()
                and pathlib.Path(
                    "/etc/systemd/system/fbsir-admin.service.d/"
                    "20-u3w-default-off-release.conf"
                ).is_file()
                and sha256_file(pathlib.Path(
                    "/etc/systemd/system/fbsir-admin.service.d/"
                    "20-u3w-default-off-release.conf"
                )) == sha256_file(rollback_dropin)
                and not pathlib.Path(
                    "/opt/fbsir/admin/current"
                ).exists()
                and not pathlib.Path(
                    "/opt/fbsir/admin/current"
                ).is_symlink()
                and jar_digest
                    == application_assembly.get("previousJarSha256")
                and sha256_file(pathlib.Path(
                    "/etc/nginx/conf.d/u3w-placeholder-sites.conf"
                )) == application_assembly.get(
                    "previousNginxSha256"
                )
                and rollback_live_state_matched
            )
        except (OSError, ValueError, TypeError, json.JSONDecodeError):
            rollback_valid = False
        deployment.update({
            "state": (
                latest.get("state") if rollback_valid else None
            ),
            "receiptValidated": rollback_valid,
            "receiptAnchorMatched": bool(
                rollback_valid
                and EXPECTED_DEPLOYMENT_RECEIPT_SHA256
                and latest_sha256
                    == EXPECTED_DEPLOYMENT_RECEIPT_SHA256
            ),
            "sourceCommit": (
                latest.get("sourceCommit") if rollback_valid else None
            ),
            "strictHeadBuildUploadSwitchReceiptScriptPresent":
                rollback_valid,
            "applicationRollbackAssemblyVerified": rollback_valid,
            "applicationRollbackProven": rollback_valid,
            "databaseRollbackSafetyProven": bool(
                rollback_valid and retained_database_current_read
            ),
            "stableDatabaseIdentityMatched": bool(
                rollback_valid and rollback_live_state_matched
            ),
            "stagedLiveStateMatched": False,
            "rollbackLiveStateMatched": bool(
                rollback_valid and rollback_live_state_matched
            ),
            "databaseDownClaimed": latest.get("databaseDownClaimed"),
            "actualActiveArtifactsMatched": False,
            "currentLinkResolved": None,
            "frontendTreeSha256": None,
            "u3wDirectCaptchaHealthy": (
                http_json_status(
                    "http://127.0.0.1:8080/captchaImage"
                ) == {"httpStatus": 200, "businessCode": 200}
            ),
            "mePortalApiHealthy": False,
            "adminPortalApiHealthy": False,
        })
if (
    deployment_receipt.is_file()
    and not rollback_latest
    and not recovery_latest
):
    resolved_deployment_receipt = deployment_receipt.resolve(strict=True)
    deployment_receipt_status = resolved_deployment_receipt.stat()
    deployment_receipt_sha256 = sha256_file(resolved_deployment_receipt)
    receipt = json.loads(
        resolved_deployment_receipt.read_text(encoding="utf-8")
    )
    deployment_receipt_schema = receipt.get("schema")
    legacy_deployment_receipt = (
        deployment_receipt_schema == LEGACY_DEPLOYMENT_RECEIPT_SCHEMA
    )
    expected_database_safety_schema = (
        "fbsir.u3wDatabaseRollbackSafetyReceipt.v1"
        if legacy_deployment_receipt
        else "fbsir.u3wDatabaseRollbackSafetyReceipt.v2"
        if deployment_receipt_schema == DEPLOYMENT_RECEIPT_SCHEMA
        else None
    )
    expected_rollback_execution_schema = (
        "fbsir.u3wApplicationRollbackExecutionReceipt.v1"
        if legacy_deployment_receipt
        else "fbsir.u3wApplicationRollbackExecutionReceipt.v2"
        if deployment_receipt_schema == DEPLOYMENT_RECEIPT_SCHEMA
        else None
    )
    deployment_state = receipt.get("state")
    source_commit = receipt.get("sourceCommit")
    backend_digest = receipt.get("backendBuildSha256")
    frontend_digest = receipt.get("frontendBuildSha256")
    runner_digest = receipt.get("runnerSha256")
    worker_digest = receipt.get("workerSha256")
    application_rollback_assembly_digest = receipt.get(
        "applicationRollbackAssemblyReceiptSha256"
    )
    application_rollback_execution_digest = receipt.get(
        "applicationRollbackReceiptSha256"
    )
    database_safety_digest = receipt.get(
        "databaseRollbackSafetyReceiptSha256"
    )
    release_root = pathlib.Path("/opt/fbsir/admin/releases").resolve(
        strict=True
    )
    try:
        resolved_deployment_receipt.relative_to(release_root)
        receipt_under_release_root = True
    except ValueError:
        receipt_under_release_root = False
    release_directory = resolved_deployment_receipt.parent
    release_plan_binding = validate_release_plan_target_binding(
        release_directory, receipt
    )
    release_plan_target_binding_verified = (
        release_plan_binding["verified"]
    )
    target_sha256 = release_plan_binding[
        "releasePlanTargetSha256"
    ]
    target_comparable_sha256 = release_plan_binding[
        "releasePlanTargetComparableSha256"
    ]

    def verified_release_file(path_value, expected_digest):
        if not isinstance(path_value, str):
            return False
        path = pathlib.Path(path_value)
        if path.is_symlink():
            return False
        try:
            candidate = path.resolve(strict=True)
        except FileNotFoundError:
            return False
        try:
            candidate.relative_to(release_directory)
        except ValueError:
            return False
        status = candidate.stat()
        return (
            candidate.is_file()
            and status.st_uid == 0
            and status.st_gid == 0
            and status.st_nlink == 1
            and status.st_mode & 0o022 == 0
            and re.fullmatch(
                r"[0-9a-f]{64}", str(expected_digest or "")
            ) is not None
            and sha256_file(candidate) == expected_digest
        )

    def frontend_tree_sha256(root):
        root = pathlib.Path(root).resolve(strict=True)
        rows = []
        for candidate in sorted(
            root.rglob("*"),
            key=lambda item: item.relative_to(root).as_posix(),
        ):
            if candidate.is_symlink():
                raise RuntimeError("frontend tree contains a symlink")
            if candidate.is_dir():
                continue
            if not candidate.is_file():
                raise RuntimeError("frontend tree contains an unsafe entry")
            relative = candidate.relative_to(root).as_posix()
            status = candidate.stat()
            if (
                status.st_uid != 0
                or status.st_gid != 0
                or status.st_nlink != 1
                or status.st_mode & 0o022
            ):
                raise RuntimeError("frontend tree custody is invalid")
            rows.append("{}  {}".format(
                sha256_file(candidate), relative
            ))
        if not rows:
            return None
        return hashlib.sha256(
            ("\n".join(rows) + "\n").encode("utf-8")
        ).hexdigest()

    backend_verified = verified_release_file(
        receipt.get("backendBuildPath"), backend_digest
    )
    frontend_manifest_verified = verified_release_file(
        receipt.get("frontendBuildPath"), frontend_digest
    )
    runner_verified = verified_release_file(
        receipt.get("runnerPath"), runner_digest
    ) and runner_digest == EXPECTED_RELEASE_RUNNER_SHA256
    worker_verified = verified_release_file(
        str(release_directory / "release-worker.py"), worker_digest
    ) and worker_digest == EXPECTED_RELEASE_WORKER_SHA256
    application_rollback_assembly_verified = verified_release_file(
        receipt.get("applicationRollbackAssemblyReceiptPath"),
        application_rollback_assembly_digest,
    )
    application_rollback_execution_verified = verified_release_file(
        receipt.get("applicationRollbackReceiptPath"),
        application_rollback_execution_digest,
    )
    database_safety_verified = verified_release_file(
        receipt.get("databaseRollbackSafetyReceiptPath"),
        database_safety_digest,
    )
    application_rollback_assembly = {}
    application_rollback_execution = {}
    if application_rollback_assembly_verified:
        application_rollback_assembly = json.loads(pathlib.Path(
            receipt["applicationRollbackAssemblyReceiptPath"]
        ).read_text(encoding="utf-8"))
        pre_application_state = application_rollback_assembly.get(
            "preStageApplicationState"
        )
        prior_rollback_anchor = application_rollback_assembly.get(
            "priorRollbackAnchor"
        )
        prior_recovery_anchor = application_rollback_assembly.get(
            "priorRecoveryAnchor"
        )
        prior_recovery_receipt_valid = False
        if isinstance(prior_recovery_anchor, dict):
            try:
                anchored_recovery_path = pathlib.Path(
                    str(prior_recovery_anchor.get("receiptPath") or "")
                )
                anchored_recovery_manifest = regular_file_manifest(
                    anchored_recovery_path, allowed_modes=(0o600,)
                )
                anchored_recovery = json.loads(
                    anchored_recovery_path.read_text(encoding="utf-8")
                )
                anchored_recovery_release = (
                    anchored_recovery_path.parent
                )
                anchored_recovery_release_status = (
                    anchored_recovery_release.lstat()
                )
                anchored_stage_path = pathlib.Path(
                    str(anchored_recovery.get("stageReceiptPath") or "")
                )
                anchored_stage_manifest = regular_file_manifest(
                    anchored_stage_path, allowed_modes=(0o600,)
                )
                anchored_stage = json.loads(
                    anchored_stage_path.read_text(encoding="utf-8")
                )
                anchored_failure_path = pathlib.Path(
                    str(
                        anchored_recovery.get(
                            "applyFailureReceiptPath"
                        ) or ""
                    )
                )
                anchored_failure_manifest = regular_file_manifest(
                    anchored_failure_path, allowed_modes=(0o600,)
                )
                anchored_failure = json.loads(
                    anchored_failure_path.read_text(encoding="utf-8")
                )
                anchored_predecessor_evidence_path = pathlib.Path(
                    str(anchored_stage.get(
                        "applicationRollbackAssemblyReceiptPath"
                    ) or "")
                )
                anchored_predecessor_evidence_manifest = (
                    regular_file_manifest(
                        anchored_predecessor_evidence_path,
                        allowed_modes=(0o600,),
                    )
                )
                anchored_predecessor_evidence = json.loads(
                    anchored_predecessor_evidence_path.read_text(
                        encoding="utf-8"
                    )
                )
                anchored_predecessor_jar = (
                    anchored_recovery_release
                    / "rollback/previous-admin.jar"
                )
                anchored_predecessor_jar_manifest = (
                    regular_file_manifest(
                        anchored_predecessor_jar,
                        allowed_modes=(0o600,),
                    )
                )
                anchored_rollback_dropin = (
                    anchored_recovery_release
                    / "evidence/rollback-systemd-dropin.conf"
                )
                anchored_rollback_dropin_manifest = (
                    regular_file_manifest(
                        anchored_rollback_dropin,
                        allowed_modes=(0o600,),
                    )
                )
                active_recovery_dropin = pathlib.Path(
                    "/etc/systemd/system/fbsir-admin.service.d/"
                    "20-u3w-default-off-release.conf"
                )
                active_recovery_dropin_manifest = (
                    regular_file_manifest(
                        active_recovery_dropin,
                        allowed_modes=(0o644,),
                    )
                )
                active_recovery_nginx = pathlib.Path(
                    "/etc/nginx/conf.d/u3w-placeholder-sites.conf"
                )
                active_recovery_nginx_manifest = (
                    regular_file_manifest(active_recovery_nginx)
                )
                anchored_failure_receipt_manifest = (
                    anchored_recovery.get(
                        "applyFailureReceiptManifest"
                    )
                )
                anchored_actual_failure_manifest = []
                anchored_failure_manifest_current = bool(
                    isinstance(
                        anchored_failure_receipt_manifest, list
                    )
                    and len(anchored_failure_receipt_manifest) > 0
                )
                for anchored_failure_candidate in sorted(
                    anchored_recovery_release.glob(
                        "apply-failure-*.json"
                    )
                ):
                    anchored_candidate_manifest = (
                        regular_file_manifest(
                            anchored_failure_candidate,
                            allowed_modes=(0o600,),
                        )
                    )
                    anchored_candidate_receipt = json.loads(
                        anchored_failure_candidate.read_text(
                            encoding="utf-8"
                        )
                    )
                    anchored_candidate_valid = bool(
                        re.fullmatch(
                            r"apply-failure-\d{8}T\d{12}Z-"
                            r"[0-9a-f]{12}\.json",
                            anchored_failure_candidate.name,
                        ) is not None
                        and anchored_candidate_receipt.get("schema")
                            in {
                                LEGACY_APPLY_FAILURE_RECEIPT_SCHEMA,
                                APPLY_FAILURE_RECEIPT_SCHEMA,
                            }
                        and anchored_candidate_receipt.get("state")
                            in APPLY_FAILURE_STATES
                        and anchored_candidate_receipt.get(
                            "releaseId"
                        ) == anchored_recovery_release.name
                        and anchored_candidate_receipt.get(
                            "sourceCommit"
                        ) == anchored_recovery.get("sourceCommit")
                        and anchored_candidate_receipt.get(
                            "officialExpertsPackageChanged"
                        ) is False
                        and re.fullmatch(
                            r"[0-9a-f]{64}",
                            str(anchored_candidate_receipt.get(
                                "applyApprovalReceiptSha256"
                            ) or ""),
                        ) is not None
                    )
                    anchored_failure_manifest_current = bool(
                        anchored_failure_manifest_current
                        and anchored_candidate_valid
                    )
                    anchored_actual_failure_manifest.append({
                        "name": anchored_failure_candidate.name,
                        "sha256":
                            anchored_candidate_manifest["sha256"],
                        "schema":
                            anchored_candidate_receipt.get("schema"),
                        "state":
                            anchored_candidate_receipt.get("state"),
                    })
                anchored_failure_manifest_current = bool(
                    anchored_failure_manifest_current
                    and anchored_actual_failure_manifest
                        == anchored_failure_receipt_manifest
                    and hashlib.sha256(
                        canonical_json(
                            anchored_actual_failure_manifest
                        ).encode("utf-8")
                    ).hexdigest()
                        == anchored_recovery.get(
                            "applyFailureReceiptManifestSha256"
                        )
                    and sum(
                        1
                        for item in anchored_actual_failure_manifest
                        if item.get("name")
                            == anchored_failure_path.name
                        and item.get("sha256")
                            == anchored_recovery.get(
                                "applyFailureReceiptSha256"
                            )
                    ) == 1
                )
                prior_recovery_receipt_valid = bool(
                    set(anchored_recovery)
                        == INTERRUPTED_APPLY_RECOVERY_RECEIPT_FIELDS
                    and anchored_recovery_path.name
                        == "interrupted-apply-recovery-receipt.json"
                    and anchored_recovery_release.parent
                        == pathlib.Path(
                            "/opt/fbsir/admin/releases"
                        )
                    and stat.S_ISDIR(
                        anchored_recovery_release_status.st_mode
                    )
                    and not stat.S_ISLNK(
                        anchored_recovery_release_status.st_mode
                    )
                    and anchored_recovery_release_status.st_uid == 0
                    and anchored_recovery_release_status.st_gid == 0
                    and anchored_recovery_release_status.st_nlink >= 2
                    and not anchored_recovery_release_status.st_mode
                        & 0o022
                    and anchored_recovery_manifest["sha256"]
                        == prior_recovery_anchor.get("receiptSha256")
                    and anchored_recovery.get("schema")
                        == INTERRUPTED_APPLY_RECOVERY_RECEIPT_SCHEMA
                    and anchored_recovery.get("state")
                        == INTERRUPTED_APPLY_RECOVERY_STATE
                    and anchored_recovery.get("releaseId")
                        == prior_recovery_anchor.get("releaseId")
                        == anchored_recovery_release.name
                    and anchored_recovery.get("sourceCommit")
                        == prior_recovery_anchor.get("sourceCommit")
                    and anchored_stage_path == (
                        anchored_recovery_release
                        / "deployment-readiness-receipt.json"
                    )
                    and anchored_stage_manifest["sha256"]
                        == anchored_recovery.get(
                            "stageReceiptSha256"
                        )
                    and anchored_stage.get("schema")
                        == LEGACY_DEPLOYMENT_RECEIPT_SCHEMA
                    and anchored_stage.get("state")
                        == "STAGED_FOR_SWITCH"
                    and anchored_stage.get("releaseId")
                        == anchored_recovery_release.name
                    and anchored_stage.get("sourceCommit")
                        == anchored_recovery.get("sourceCommit")
                    and anchored_failure_path.parent
                        == anchored_recovery_release
                    and anchored_failure_manifest["sha256"]
                        == anchored_recovery.get(
                            "applyFailureReceiptSha256"
                        )
                    and anchored_failure.get("schema")
                        == anchored_recovery.get(
                            "applyFailureReceiptSchema"
                        )
                    and anchored_failure.get("state")
                        == anchored_recovery.get(
                            "applyFailureReceiptState"
                        )
                    and anchored_failure.get("applicationRestored")
                        is True
                    and anchored_failure.get("topologyRestored") is True
                    and anchored_failure.get(
                        "deploymentCommitOutcome"
                    ) == "NOT_COMMITTED"
                    and anchored_failure.get(
                        "officialExpertsPackageChanged"
                    ) is False
                    and anchored_failure_manifest_current
                    and anchored_predecessor_evidence_path == (
                        anchored_recovery_release
                        / "evidence/application-rollback-assembly.json"
                    )
                    and anchored_predecessor_evidence_manifest["sha256"]
                        == anchored_stage.get(
                            "applicationRollbackAssemblyReceiptSha256"
                        )
                    and anchored_predecessor_evidence.get("schema")
                        == (
                            "fbsir.u3wApplicationRollbackAssembly"
                            "Receipt.v1"
                        )
                    and anchored_predecessor_evidence.get(
                        "immutablePreviousJarPath"
                    ) == str(anchored_predecessor_jar)
                    and anchored_predecessor_jar_manifest["sha256"]
                        == anchored_predecessor_evidence.get(
                            "previousJarSha256"
                        )
                    and active_recovery_dropin_manifest["sha256"]
                        == anchored_rollback_dropin_manifest["sha256"]
                    and active_recovery_nginx_manifest["sha256"]
                        == anchored_predecessor_evidence.get(
                            "previousNginxSha256"
                        )
                    and live_service_snapshot.get("activeState")
                        == "active"
                    and live_service_snapshot.get("configuredJarSha256")
                        == anchored_predecessor_jar_manifest["sha256"]
                    and live_service_snapshot.get("processJarSha256")
                        == anchored_predecessor_jar_manifest["sha256"]
                    and live_service_snapshot.get("jarSha256")
                        == anchored_predecessor_jar_manifest["sha256"]
                    and snapshot_default_off(
                        live_service_snapshot,
                        ("EXACT_CONFIGURED",),
                    )
                    and not pathlib.Path(
                        "/opt/fbsir/admin/current"
                    ).exists()
                    and not pathlib.Path(
                        "/opt/fbsir/admin/current"
                    ).is_symlink()
                    and recorded_recovery_migration_matches_live(
                        anchored_recovery.get(
                            "retainedMigrationFacts"
                        ),
                        database,
                    )
                    and interrupted_recovery_historic_anchors_valid(
                        anchored_recovery_release,
                        anchored_recovery,
                    )
                    and anchored_recovery.get(
                        "applicationRestored"
                    ) is True
                    and anchored_recovery.get(
                        "topologyRestored"
                    ) is True
                    and anchored_recovery.get(
                        "deploymentReceiptAbsent"
                    ) is True
                    and anchored_recovery.get(
                        "rollbackReceiptAbsent"
                    ) is True
                    and anchored_recovery.get("currentLinkAbsent")
                        is True
                    and anchored_recovery.get(
                        "releaseDropInMatched"
                    ) is True
                    and anchored_recovery.get(
                        "allW1aFlagsExplicitFalse"
                    ) is True
                    and anchored_recovery.get("databaseDownClaimed")
                        is False
                    and anchored_recovery.get(
                        "productionFilesystemChanged"
                    ) is True
                    and anchored_recovery.get(
                        "productionDatabaseChanged"
                    ) is True
                    and anchored_recovery.get(
                        "productionDatabaseChangedThisRecoveryRun"
                    ) is False
                    and anchored_recovery.get(
                        "productionDatabaseChangedSinceStage"
                    ) is True
                    and anchored_recovery.get(
                        "productionServiceChanged"
                    ) is True
                    and anchored_recovery.get(
                        "productionServiceChangedThisRecoveryRun"
                    ) is False
                    and anchored_recovery.get(
                        "productionServiceChangedSinceStage"
                    ) is True
                    and anchored_recovery.get(
                        "officialExpertsPackageChanged"
                    ) is False
                    and all(
                        not candidate.exists()
                        and not candidate.is_symlink()
                        for candidate in (
                            anchored_recovery_release
                                / "deployment-receipt.json",
                            anchored_recovery_release
                                / "rollback-receipt.json",
                            anchored_recovery_release
                                / "rollback-verification-receipt.json",
                        )
                    )
                )
            except (
                OSError, ValueError, TypeError, KeyError,
                json.JSONDecodeError
            ):
                prior_recovery_receipt_valid = False
        prior_stage_anchor_valid = bool(
            (
                pre_application_state == "UNTOUCHED_LEGACY"
                and prior_rollback_anchor is None
                and prior_recovery_anchor is None
            )
            or (
                pre_application_state
                    == "EXACT_PRIOR_ROLLBACK_PREDECESSOR"
                and isinstance(prior_rollback_anchor, dict)
                and prior_recovery_anchor is None
                and prior_rollback_anchor.get("schema")
                    == "fbsir.u3wPriorRollbackStageAnchor.v1"
                and prior_rollback_anchor.get("state")
                    == (
                        "ROLLED_BACK_APPLICATION_DATABASE_043_"
                        "RETAINED_DORMANT"
                    )
                and re.fullmatch(
                    r"[0-9a-f]{64}",
                    str(prior_rollback_anchor.get("receiptSha256") or ""),
                ) is not None
            )
            or (
                pre_application_state
                    == "EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR"
                and prior_rollback_anchor is None
                and isinstance(prior_recovery_anchor, dict)
                and prior_recovery_anchor.get("schema")
                    == "fbsir.u3wPriorInterruptedApplyRecoveryStageAnchor.v1"
                and prior_recovery_anchor.get("state")
                    == INTERRUPTED_APPLY_RECOVERY_STATE
                and prior_recovery_anchor.get("receiptSchema")
                    == INTERRUPTED_APPLY_RECOVERY_RECEIPT_SCHEMA
                and re.fullmatch(
                    r"w1a-release-[0-9a-f]{12}-"
                    r"\d{8}T\d{6}Z",
                    str(prior_recovery_anchor.get("releaseId") or ""),
                ) is not None
                and re.fullmatch(
                    r"[0-9a-f]{40}",
                    str(prior_recovery_anchor.get("sourceCommit") or ""),
                ) is not None
                and pathlib.Path(str(
                    prior_recovery_anchor.get("receiptPath") or ""
                )).name == "interrupted-apply-recovery-receipt.json"
                and re.fullmatch(
                    r"[0-9a-f]{64}",
                    str(prior_recovery_anchor.get("receiptSha256") or ""),
                ) is not None
                and prior_recovery_receipt_valid
            )
        )
        immutable_previous_jar = (
            release_directory / "rollback/previous-admin.jar"
        )
        pre_stage_snapshot = application_rollback_assembly.get(
            "serviceSnapshotBeforeStage", {}
        )
        observed_pre_stage_load_state = pre_stage_snapshot.get(
            "processConfiguredEnvironmentLoadState"
        )
        expected_pre_stage_load_state = (
            "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART"
            if observed_pre_stage_load_state
                == "LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART"
            and prior_rollback_anchor is None
            and prior_recovery_anchor is None
            else (
                observed_pre_stage_load_state
                if observed_pre_stage_load_state in {
                    "EXACT_CONFIGURED",
                    "ENGINE_CREDENTIAL_PENDING_RESTART",
                }
                and (
                    prior_rollback_anchor is not None
                    or prior_recovery_anchor is not None
                )
                else None
            )
        )
        application_rollback_assembly_verified = (
            application_rollback_assembly.get("schema")
                == "fbsir.u3wApplicationRollbackAssemblyReceipt.v1"
            and application_rollback_assembly.get("sourceCommit")
                == source_commit
            and application_rollback_assembly.get("releaseId")
                == receipt.get("releaseId")
            and application_rollback_assembly.get(
                "assemblyVerified"
            ) is True
            and application_rollback_assembly.get(
                "applicationRollbackProven"
            ) is False
            and application_rollback_assembly.get("strategy")
                == (
                    "IMMUTABLE_PREDECESSOR_DROPIN_RESTORE_NGINX_"
                    "AND_RESTART"
                )
            and prior_stage_anchor_valid
            and application_rollback_assembly.get(
                "immutablePreviousJarPath"
            ) == str(immutable_previous_jar)
            and verified_release_file(
                str(immutable_previous_jar),
                application_rollback_assembly.get("previousJarSha256"),
            )
            and isinstance(
                application_rollback_assembly.get(
                    "serviceSnapshotBeforeStage"
                ),
                dict,
            )
            and application_rollback_assembly.get(
                "serviceSnapshotBeforeStage", {}
            ).get("configuredEnvironmentSha256")
                == receipt.get(
                    "preStageRuntimeIdentity", {}
                ).get("environmentSha256")
            and expected_pre_stage_load_state is not None
            and snapshot_default_off(
                pre_stage_snapshot,
                (expected_pre_stage_load_state,),
            )
            and application_rollback_assembly.get(
                "symlinkForwardAndReverseVerified"
            ) is True
            and application_rollback_assembly.get(
                "productionServiceChanged"
            ) is False
            and application_rollback_assembly.get(
                "productionDatabaseChanged"
            ) is False
            and re.fullmatch(
                r"[0-9a-f]{64}",
                str(application_rollback_assembly.get(
                    "releasePlanTargetSha256"
                ) or ""),
            ) is not None
            and application_rollback_assembly.get(
                "releasePlanTargetSha256"
            ) == receipt.get("releasePlanTargetSha256")
            and re.fullmatch(
                r"[0-9a-f]{64}",
                str(application_rollback_assembly.get(
                    "releasePlanTargetComparableSha256"
                ) or ""),
            ) is not None
            and application_rollback_assembly.get(
                "releasePlanTargetComparableSha256"
            ) == receipt.get("releasePlanTargetComparableSha256")
            and re.fullmatch(
                r"[0-9a-f]{64}",
                str(application_rollback_assembly.get(
                    "finalizeStageLiveTargetComparableSha256"
                ) or ""),
            ) is not None
            and application_rollback_assembly.get(
                "finalizeStageLiveTargetComparableSha256"
            ) == receipt.get(
                "finalizeStageLiveTargetComparableSha256"
            )
            and application_rollback_assembly.get(
                "releasePlanTargetComparableSha256"
            ) == application_rollback_assembly.get(
                "finalizeStageLiveTargetComparableSha256"
            )
            and application_rollback_assembly.get(
                "stageOwnedReleaseRootDeltaVerified"
            ) is True
            and receipt.get(
                "stageOwnedReleaseRootDeltaVerified"
            ) is True
        )
    if application_rollback_execution_verified:
        application_rollback_execution = json.loads(pathlib.Path(
            receipt["applicationRollbackReceiptPath"]
        ).read_text(encoding="utf-8"))
        candidate_before = application_rollback_execution.get(
            "candidateBeforeRollback", {}
        )
        predecessor_after = application_rollback_execution.get(
            "predecessorAfterRollback", {}
        )
        candidate_after = application_rollback_execution.get(
            "candidateAfterReapply", {}
        )
        candidate_jar_path = (
            "/opt/fbsir/admin/current/backend/fbsir-admin.jar"
        )
        predecessor_jar_path = str(
            release_directory / "rollback/previous-admin.jar"
        )
        candidate_argv_sha256 = hashlib.sha256(
            (
                "\0".join([
                    "/usr/bin/java",
                    (
                        "-Dspring.config.additional-location=file:"
                        "/opt/fbsir/admin/application-connector.yml"
                    ),
                    "-jar",
                    candidate_jar_path,
                ]) + "\0"
            ).encode("utf-8")
        ).hexdigest()
        predecessor_argv_sha256 = hashlib.sha256(
            (
                "\0".join([
                    "/usr/bin/java",
                    (
                        "-Dspring.config.additional-location=file:"
                        "/opt/fbsir/admin/application-connector.yml"
                    ),
                    "-jar",
                    predecessor_jar_path,
                ]) + "\0"
            ).encode("utf-8")
        ).hexdigest()
        candidate_dropin_sha256 = sha256_file(
            release_directory / "evidence/systemd-dropin.conf"
        )
        rollback_dropin_sha256 = sha256_file(
            release_directory / "evidence/rollback-systemd-dropin.conf"
        )
        invocation_ids = {
            candidate_before.get("invocationId"),
            predecessor_after.get("invocationId"),
            candidate_after.get("invocationId"),
        }
        predecessor_sha256 = application_rollback_assembly.get(
            "previousJarSha256"
        )
        baseline_snapshot = application_rollback_assembly.get(
            "serviceSnapshotBeforeStage", {}
        )
        stable_snapshot_fields = (
            "user",
            "group",
            "fragmentPath",
            "fragmentFileManifest",
            "environmentFiles",
            "environmentFilePaths",
            "environmentFileManifest",
            "api2EventKeyManifest",
            "expectedSecurityConfigurationNames",
            "expectedSecurityConfigurationHmacSha256",
            "configuredEnvironmentSha256",
            "configuredEnvironmentNames",
            "configuredEnvironmentHmacSha256",
            "configuredFlagValues",
            "externalConfigManifest",
            "additionalConfigSha256",
        )
        candidate_snapshots_valid = all(
            isinstance(snapshot, dict)
            and snapshot.get("configuredJarPath")
                == candidate_jar_path
            and snapshot.get("processJarPath") == candidate_jar_path
            and snapshot.get("configuredJarSha256") == backend_digest
            and snapshot.get("processJarSha256") == backend_digest
            and snapshot.get("jarSha256") == backend_digest
            and snapshot.get("activeState") == "active"
            and snapshot.get("workingDirectory")
                == "/opt/fbsir/admin"
            and snapshot.get("processArgvSha256")
                == candidate_argv_sha256
            and snapshot_default_off(snapshot)
            and snapshot.get("processDatabaseBindingMatched") is True
            and snapshot_dropin_exact(
                snapshot, candidate_dropin_sha256
            )
            and all(
                snapshot.get(field) == baseline_snapshot.get(field)
                for field in stable_snapshot_fields
            )
            for snapshot in (candidate_before, candidate_after)
        )
        predecessor_snapshot_valid = bool(
            isinstance(predecessor_after, dict)
            and predecessor_after.get("configuredJarPath")
                == predecessor_jar_path
            and predecessor_after.get("processJarPath")
                == predecessor_jar_path
            and predecessor_after.get("configuredJarSha256")
                == predecessor_sha256
            and predecessor_after.get("processJarSha256")
                == predecessor_sha256
            and predecessor_after.get("jarSha256")
                == predecessor_sha256
            and predecessor_after.get("activeState") == "active"
            and predecessor_after.get("workingDirectory")
                == "/opt/fbsir/admin"
            and predecessor_after.get("processArgvSha256")
                == predecessor_argv_sha256
            and snapshot_default_off(predecessor_after)
            and predecessor_after.get(
                "processDatabaseBindingMatched"
            ) is True
            and snapshot_dropin_exact(
                predecessor_after, rollback_dropin_sha256
            )
            and all(
                predecessor_after.get(field)
                    == baseline_snapshot.get(field)
                for field in stable_snapshot_fields
            )
        )
        application_rollback_execution_verified = (
            application_rollback_execution.get("schema")
                == expected_rollback_execution_schema
            and application_rollback_execution.get("sourceCommit")
                == source_commit
            and application_rollback_execution.get("releaseId")
                == receipt.get("releaseId")
            and application_rollback_execution.get("verified") is True
            and application_rollback_execution.get("strategy")
                == (
                    "LIVE_CANDIDATE_TO_IMMUTABLE_PREDECESSOR_TO_"
                    "CANDIDATE"
                )
            and application_rollback_execution.get("database043Retained")
                is True
            and application_rollback_execution.get(
                "productionServiceChanged"
            ) is True
            and application_rollback_execution.get(
                "productionDatabaseChanged"
            ) is False
            and recorded_w1a_migration_facts_valid(
                application_rollback_execution.get(
                    "retainedMigrationFacts"
                ),
                deployment_receipt_schema,
            )
            and application_rollback_execution.get(
                "retainedMigrationFacts"
            ) == receipt.get("migrationFacts")
            and candidate_snapshots_valid
            and predecessor_snapshot_valid
            and len(invocation_ids) == 3
            and all(
                re.fullmatch(r"[0-9a-f]{32}", str(value or ""))
                    is not None
                for value in invocation_ids
            )
        )
    if database_safety_verified:
        database_safety = json.loads(pathlib.Path(
            receipt["databaseRollbackSafetyReceiptPath"]
        ).read_text(encoding="utf-8"))
        pre_stage_runtime = receipt.get("preStageRuntimeIdentity", {})
        database_prestate = database_safety.get(
            "preDeploymentDatabaseState"
        )
        retained_prestate = database_safety.get(
            "preDeploymentRetainedMigrationFacts"
        )
        database_prestate_valid = bool(
            (
                database_prestate == "ABSENT"
                and database_safety.get(
                    "preDeploymentPublic043Absent"
                ) is True
                and database_safety.get(
                    "preDeploymentAttributionTablesAbsent"
                ) is True
                and retained_prestate is None
                and pre_stage_runtime.get("w1aDatabaseState") == "ABSENT"
                and pre_stage_runtime.get("public043ReceiptCount") == 0
                and pre_stage_runtime.get(
                    "attributionInternalReceiptCount"
                ) == 0
                and pre_stage_runtime.get(
                    "attributionTableCount"
                ) == 0
                and pre_stage_runtime.get(
                    "attributionTriggerCount"
                ) == 0
                and pre_stage_runtime.get(
                    "attributionPermissionCount"
                ) == 0
                and pre_stage_runtime.get(
                    "attributionEventCount"
                ) == 0
                and pre_stage_runtime.get(
                    "attributionJourneyCount"
                ) == 0
                and (
                    legacy_deployment_receipt
                    or (
                        pre_stage_runtime.get(
                            "attributionProbeEventCount"
                        ) == 0
                        and pre_stage_runtime.get(
                            "attributionNaturalEventCount"
                        ) == 0
                        and pre_stage_runtime.get(
                            "attributionNonProbeEventCount"
                        ) == 0
                        and pre_stage_runtime.get(
                            "attributionAuthoritativeProductCreditCount"
                        ) == 0
                        and pre_stage_runtime.get(
                            "attributionProbeJourneyCount"
                        ) == 0
                        and pre_stage_runtime.get(
                            "attributionNaturalJourneyCount"
                        ) == 0
                        and pre_stage_runtime.get(
                            "attributionNonProbeJourneyCount"
                        ) == 0
                    )
                )
                and pre_stage_runtime.get(
                    "w1aSchemaFingerprintSha256"
                ) is None
            )
            or (
                database_prestate
                    == "EXACT_043_RETAINED_DORMANT"
                and database_safety.get(
                    "preDeploymentPublic043Absent"
                ) is False
                and database_safety.get(
                    "preDeploymentAttributionTablesAbsent"
                ) is False
                and recorded_w1a_migration_facts_valid(
                    retained_prestate, deployment_receipt_schema
                )
                and pre_stage_runtime.get("w1aDatabaseState")
                    == database_prestate
                and retained_prestate.get("eventCount")
                    == pre_stage_runtime.get(
                        "attributionEventCount"
                    )
                and retained_prestate.get("journeyCount")
                    == pre_stage_runtime.get(
                        "attributionJourneyCount"
                    )
                and (
                    legacy_deployment_receipt
                    or (
                        retained_prestate.get("probeEventCount")
                            == pre_stage_runtime.get(
                                "attributionProbeEventCount"
                            )
                        and retained_prestate.get("naturalEventCount")
                            == pre_stage_runtime.get(
                                "attributionNaturalEventCount"
                            )
                        and retained_prestate.get("nonProbeEventCount")
                            == pre_stage_runtime.get(
                                "attributionNonProbeEventCount"
                            )
                        and retained_prestate.get(
                            "authoritativeProductCreditCount"
                        ) == pre_stage_runtime.get(
                            "attributionAuthoritativeProductCreditCount"
                        )
                        and retained_prestate.get("probeJourneyCount")
                            == pre_stage_runtime.get(
                                "attributionProbeJourneyCount"
                            )
                        and retained_prestate.get("naturalJourneyCount")
                            == pre_stage_runtime.get(
                                "attributionNaturalJourneyCount"
                            )
                        and retained_prestate.get("nonProbeJourneyCount")
                            == pre_stage_runtime.get(
                                "attributionNonProbeJourneyCount"
                            )
                    )
                )
                and retained_prestate.get(
                    "schemaFingerprintSha256"
                ) == pre_stage_runtime.get(
                    "w1aSchemaFingerprintSha256"
                )
            )
        )
        database_safety_verified = (
            database_safety.get("schema")
                == expected_database_safety_schema
            and database_safety.get("sourceCommit") == source_commit
            and database_safety.get("releaseId")
                == receipt.get("releaseId")
            and database_safety.get("verified") is True
            and database_safety.get("strategy")
                == "RETAIN_ADDITIVE_043_DORMANT_NO_DOWN"
            and database_safety.get("migrationSha256")
                == receipt.get("migrationSha256")
            and database_prestate_valid
            and database_safety.get(
                "legacyJarAttributionClassCount"
            ) == 0
            and database_safety.get("databaseDownClaimed") is False
            and database_safety.get(
                "allW1aFlagsExplicitFalse"
            ) is True
            and database_safety.get(
                "productionDatabaseChanged"
            ) is False
        )
    try:
        frontend_tree_digest = frontend_tree_sha256(
            release_directory / "frontend"
        )
    except (OSError, RuntimeError):
        frontend_tree_digest = None
    frontend_verified = bool(
        frontend_manifest_verified
        and frontend_tree_digest == frontend_digest
    )
    try:
        generated_at = datetime.fromisoformat(
            str(receipt.get("generatedAt", "")).replace("Z", "+00:00")
        )
        receipt_age_seconds = (
            datetime.now(timezone.utc) - generated_at.astimezone(timezone.utc)
        ).total_seconds()
        receipt_is_fresh = 0 <= receipt_age_seconds <= 86400
    except Exception:
        receipt_is_fresh = False
    rollback_shape_valid = bool(
        re.fullmatch(
            r"[0-9a-f]{64}",
            str(application_rollback_assembly_digest or ""),
        ) is not None
        and re.fullmatch(
            r"[0-9a-f]{64}", str(database_safety_digest or "")
        ) is not None
        and application_rollback_assembly_verified
        and database_safety_verified
        and (
            (
                deployment_state == "STAGED_FOR_SWITCH"
                and receipt.get(
                    "applicationRollbackAssemblyVerified"
                ) is True
                and receipt.get("applicationRollbackProven") is False
                and receipt.get("applicationRollbackReceiptPath") is None
                and receipt.get(
                    "applicationRollbackReceiptSha256"
                ) is None
            )
            or (
                deployment_state == "DEPLOYED_DEFAULT_OFF"
                and re.fullmatch(
                    r"[0-9a-f]{64}",
                    str(application_rollback_execution_digest or ""),
                ) is not None
                and application_rollback_execution_verified
                and receipt.get(
                    "applicationRollbackAssemblyVerified"
                ) is True
                and receipt.get("applicationRollbackProven") is True
            )
        )
    )
    receipt_validated = (
        receipt_under_release_root
        and deployment_receipt_status.st_uid == 0
        and deployment_receipt_status.st_gid == 0
        and deployment_receipt_status.st_nlink == 1
        and deployment_receipt_status.st_mode & 0o777 == 0o600
        and deployment_receipt_schema in {
            LEGACY_DEPLOYMENT_RECEIPT_SCHEMA,
            DEPLOYMENT_RECEIPT_SCHEMA,
        }
        and deployment_state in {
            "STAGED_FOR_SWITCH", "DEPLOYED_DEFAULT_OFF"
        }
        and (
            deployment_state != "DEPLOYED_DEFAULT_OFF"
            or recorded_final_default_off_current_read_valid(receipt)
        )
        and re.fullmatch(
            r"w1a-release-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z",
            str(receipt.get("releaseId") or ""),
        ) is not None
        and release_directory.name == receipt.get("releaseId")
        and receipt.get("targetHost") == TARGET_HOST
        and receipt.get("serviceUnit") == SERVICE_UNIT
        and receipt.get("database") == "fbsir"
        and re.fullmatch(r"[0-9a-f]{40}", str(source_commit or "")) is not None
        and re.fullmatch(r"[0-9a-f]{64}", str(backend_digest or "")) is not None
        and re.fullmatch(r"[0-9a-f]{64}", str(frontend_digest or "")) is not None
        and re.fullmatch(r"[0-9a-f]{64}", str(runner_digest or "")) is not None
        and backend_verified
        and frontend_verified
        and runner_verified
        and worker_verified
        and release_plan_target_binding_verified
        and rollback_shape_valid
        and receipt.get("databaseRollbackSafetyProven") is True
        and receipt.get("databaseDownClaimed") is False
        and type(
            receipt.get("productionDatabaseChangedThisRun")
        ) is bool
        and type(
            receipt.get("productionDatabaseChangedSinceStage")
        ) is bool
        and receipt.get("productionDatabaseChanged")
            == receipt.get("productionDatabaseChangedSinceStage")
        and receipt.get("backupReceiptSha256")
            == EXPECTED_BACKUP_RECEIPT_SHA256
        and receipt.get("legacyBaselineReceiptSha256")
            == EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST
        and receipt.get(
            "adminRootDependencyAdoptionReceiptSha256"
        ) == EXPECTED_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256
        and receipt.get("configurationReceiptSha256")
            == EXPECTED_CONFIGURATION_RECEIPT_SHA256
        and receipt.get("releasePlanReceiptSha256")
            == EXPECTED_RELEASE_PLAN_RECEIPT_SHA256
        and re.fullmatch(
            r"[0-9a-f]{64}",
            str(receipt.get("releasePlanTargetSha256") or ""),
        ) is not None
        and re.fullmatch(
            r"[0-9a-f]{64}",
            str(receipt.get(
                "releasePlanTargetComparableSha256"
            ) or ""),
        ) is not None
        and re.fullmatch(
            r"[0-9a-f]{64}",
            str(receipt.get(
                "finalizeStageLiveTargetComparableSha256"
            ) or ""),
        ) is not None
        and receipt.get("releasePlanTargetComparableSha256")
            == receipt.get(
                "finalizeStageLiveTargetComparableSha256"
            )
        and receipt.get("stageOwnedReleaseRootDeltaVerified") is True
        and receipt_is_fresh
    )
    current_link = pathlib.Path("/opt/fbsir/admin/current")
    current_link_resolved = None
    if current_link.is_symlink():
        try:
            current_link_resolved = str(current_link.resolve(strict=True))
        except FileNotFoundError:
            current_link_resolved = None
    pre_stage_runtime = receipt.get("preStageRuntimeIdentity", {})
    stable_database_identity_matched = (
        stable_database_identity_matches(pre_stage_runtime, database)
    )
    baseline_service_snapshot = application_rollback_assembly.get(
        "serviceSnapshotBeforeStage", {}
    )
    predecessor_nginx_path = pathlib.Path(
        "/etc/nginx/conf.d/u3w-placeholder-sites.conf"
    )
    staged_live_state_matched = bool(
        deployment_state == "STAGED_FOR_SWITCH"
        and application_rollback_assembly_verified
        and not current_link.exists()
        and not current_link.is_symlink()
        and staged_predecessor_snapshot_matches(
            live_service_snapshot, baseline_service_snapshot
        )
        and predecessor_nginx_path.is_file()
        and not predecessor_nginx_path.is_symlink()
        and sha256_file(predecessor_nginx_path)
            == application_rollback_assembly.get("previousNginxSha256")
        and live_w1a_prestate_matches(
            pre_stage_runtime, database, receipt.get("schema")
        )
    )
    direct_captcha = http_json_status(
        "http://127.0.0.1:8080/captchaImage"
    )
    me_api_captcha = http_json_status(
        "https://me.u3w.com/prod-api/captchaImage"
    )
    admin_api_captcha = http_json_status(
        "https://admin.u3w.com/prod-api/captchaImage"
    )
    me_release_marker = http_json_document(
        "https://me.u3w.com/w1a-release.json"
    )
    admin_release_marker = http_json_document(
        "https://admin.u3w.com/w1a-release.json"
    )
    u3w_direct_healthy = bool(
        direct_captcha["httpStatus"] == 200
        and direct_captcha["businessCode"] == 200
    )
    me_api_healthy = bool(
        me_api_captcha["httpStatus"] == 200
        and me_api_captcha["businessCode"] == 200
    )
    admin_api_healthy = bool(
        admin_api_captcha["httpStatus"] == 200
        and admin_api_captcha["businessCode"] == 200
    )
    expected_release_marker = {
        "schema": "fbsir.u3wReleaseMarker.v1",
        "releaseId": receipt.get("releaseId"),
        "sourceCommit": source_commit,
        "officialExpertsPackageChanged": False,
    }
    me_release_marker_matched = bool(
        me_release_marker["httpStatus"] == 200
        and me_release_marker["document"] == expected_release_marker
    )
    admin_release_marker_matched = bool(
        admin_release_marker["httpStatus"] == 200
        and admin_release_marker["document"] == expected_release_marker
    )
    default_off_ingress_probe = None
    default_off_ingress_probe_verified = False
    if (
        receipt_validated
        and deployment_state == "DEPLOYED_DEFAULT_OFF"
        and callable(query)
        and database.get("publicInit043Applied") is True
    ):
        route_probe = disabled_attribution_ingress_probe(
            query,
            environment,
        )
        counts_before_probe = route_probe.get("globalCountsBefore")
        counts_after_probe = attribution_global_counts(query)
        post_probe_show = run([
            "systemctl", "show", SERVICE_UNIT,
            (
                "--property=ActiveState,SubState,MainPID,InvocationID,"
                "ExecStart,DropInPaths,EnvironmentFiles"
            ),
        ])
        post_probe_service = {}
        for line in post_probe_show.splitlines():
            key, _, value = line.partition("=")
            post_probe_service[key] = value
        stable_service_fields = (
            "ActiveState",
            "SubState",
            "MainPID",
            "InvocationID",
            "ExecStart",
            "DropInPaths",
            "EnvironmentFiles",
        )
        service_stable = bool(
            all(
                post_probe_service.get(name) == service.get(name)
                for name in stable_service_fields
            )
            and post_probe_service.get("ActiveState") == "active"
            and post_probe_service.get("SubState") == "running"
            and jar_path
            and pathlib.Path(jar_path).is_file()
            and sha256_file(jar_path) == jar_digest
        )
        default_off_ingress_probe_verified = bool(
            route_probe.get("verifiedDisabled") is True
            and route_probe.get("httpStatus")
                == DISABLED_INGRESS_HTTP_STATUS
            and route_probe.get("identityCountsBefore")
                == {
                    name: 0
                    for name in (
                        "eventId",
                        "receiptId",
                        "nonceHash",
                        "journeyId",
                    )
                }
            and route_probe.get("identityCountsAfter")
                == route_probe.get("identityCountsBefore")
            and route_probe.get("globalCountsBefore")
                == counts_before_probe
            and route_probe.get("globalCountsAfter")
                == counts_after_probe
            and counts_before_probe == counts_after_probe
            and service_stable
        )
        default_off_ingress_probe = {
            "path": route_probe.get("path"),
            "signedProbe": route_probe,
            "databaseCountsBeforeProbe": counts_before_probe,
            "databaseCountsAfterProbe": counts_after_probe,
            "eventAndJourneyCountsUnchanged":
                counts_before_probe == counts_after_probe,
            "serviceStableDuringProbe": service_stable,
            "verified": default_off_ingress_probe_verified,
        }
    active_artifacts_matched = False
    if receipt_validated and deployment_state == "DEPLOYED_DEFAULT_OFF":
        expected_backend = pathlib.Path(
            receipt["backendBuildPath"]
        ).resolve(strict=True)
        active_jar_resolved = (
            pathlib.Path(jar_path).resolve(strict=True)
            if jar_path and pathlib.Path(jar_path).is_file()
            else None
        )
        configured_jar_resolved = (
            pathlib.Path(configured_jar_path).resolve(strict=True)
            if configured_jar_path
            and pathlib.Path(configured_jar_path).is_file()
            else None
        )
        nginx_path = pathlib.Path(
            str(receipt.get("nginxPath") or "")
        )
        expected_nginx = release_directory / (
            "evidence/u3w-portal-sites.conf"
        )
        expected_dropin = release_directory / (
            "evidence/systemd-dropin.conf"
        )
        active_dropin = pathlib.Path(
            "/etc/systemd/system/"
            "fbsir-admin.service.d/20-u3w-default-off-release.conf"
        )
        expected_candidate_arguments = [
            "/usr/bin/java",
            (
                "-Dspring.config.additional-location=file:"
                "/opt/fbsir/admin/application-connector.yml"
            ),
            "-jar",
            "/opt/fbsir/admin/current/backend/fbsir-admin.jar",
        ]
        expected_candidate_argv_sha256 = hashlib.sha256(
            (
                "\0".join(expected_candidate_arguments) + "\0"
            ).encode("utf-8")
        ).hexdigest()
        current_dropin_manifest = []
        for value in service.get("DropInPaths", "").split():
            candidate = pathlib.Path(value)
            if candidate.is_file() and not candidate.is_symlink():
                current_dropin_manifest.append({
                    "path": str(candidate),
                    "sha256": sha256_file(candidate),
                    "mode": candidate.stat().st_mode & 0o777,
                })
        current_dropin_manifest.sort(key=lambda item: item["path"])
        candidate_after_reapply = application_rollback_execution.get(
            "candidateAfterReapply", {}
        )
        additional_config = pathlib.Path(
            "/opt/fbsir/admin/application-connector.yml"
        )
        migration_baseline = receipt.get("migrationFacts", {})
        migration_baseline_valid = recorded_w1a_migration_facts_valid(
            migration_baseline, deployment_receipt_schema
        )
        active_artifacts_matched = bool(
            current_link_resolved == str(release_directory)
            and active_jar_resolved == expected_backend
            and configured_jar_resolved == expected_backend
            and jar_digest == backend_digest
            and frontend_tree_digest == frontend_digest
            and nginx_path
                == pathlib.Path(
                    "/etc/nginx/conf.d/u3w-placeholder-sites.conf"
                )
            and nginx_path.is_file()
            and not nginx_path.is_symlink()
            and sha256_file(nginx_path) == receipt.get("nginxSha256")
            and expected_nginx.is_file()
            and sha256_file(expected_nginx)
                == receipt.get("nginxSha256")
            and receipt.get("activeNginxManifestAfterApply")
                == nginx_configs
            and receipt.get("nginxDumpSha256AfterApply")
                == nginx_dump_sha256
            and active_dropin.is_file()
            and not active_dropin.is_symlink()
            and expected_dropin.is_file()
            and sha256_file(active_dropin)
                == sha256_file(expected_dropin)
            and process_argv_sha256 == expected_candidate_argv_sha256
            and process_jar_path
                == "/opt/fbsir/admin/current/backend/fbsir-admin.jar"
            and all(
                process_flag_values.get(name) == "false"
                for name in FLAG_NAMES
            )
            and process_forbidden_override_names == []
            and snapshot_configuration_matches(
                live_service_snapshot, candidate_after_reapply
            )
            and current_dropin_manifest
                == candidate_after_reapply.get("dropInManifest")
            and process_argv_sha256
                == candidate_after_reapply.get("processArgvSha256")
            and process_flag_values
                == candidate_after_reapply.get("processFlagValues")
            and candidate_after_reapply.get(
                "processForbiddenOverrideNames"
            ) == []
            and external_config_manifest
                == candidate_after_reapply.get(
                    "externalConfigManifest"
                )
            and service.get("EnvironmentFiles")
                == candidate_after_reapply.get("environmentFiles")
            and additional_config.is_file()
            and not additional_config.is_symlink()
            and sha256_file(additional_config)
                == receipt.get(
                    "preStageRuntimeIdentity", {}
                ).get("additionalConfigSha256")
            and sha256_file(additional_config)
                == candidate_after_reapply.get(
                    "additionalConfigSha256"
                )
            and service.get("ActiveState") == "active"
            and service.get("SubState") == "running"
            and service.get("NRestarts") == "0"
            and stable_database_identity_matched
            and receipt.get("actualActiveArtifactsMatched") is True
            and database.get("publicInit043Applied") is True
            and database.get("boardAttributionTableCount") == 2
            and database.get("boardAttributionTriggerCount") == 2
            and database.get("boardAttributionPermissionCount") == 1
            and database.get("boardAttributionInternalReceiptCount") == 1
            and database.get("w1a043State")
                == "EXACT_043_RETAINED_DORMANT"
            and database.get("boardAttributionEventCount")
                == database.get("boardAttributionProbeEventCount")
            and database.get("boardAttributionNaturalEventCount") == 0
            and database.get("boardAttributionNonProbeEventCount") == 0
            and database.get(
                "boardAttributionAuthoritativeProductCreditCount"
            ) == 0
            and database.get("boardAttributionJourneyCount")
                == database.get("boardAttributionProbeJourneyCount")
            and database.get("boardAttributionNaturalJourneyCount") == 0
            and database.get("boardAttributionNonProbeJourneyCount") == 0
            and isinstance(
                database.get("boardAttributionEventCount"), int
            )
            and database.get("boardAttributionEventCount") >= 0
            and isinstance(
                database.get("boardAttributionJourneyCount"), int
            )
            and database.get("boardAttributionJourneyCount") >= 0
            and migration_baseline_valid
            and database.get("boardAttributionEventCount")
                >= migration_baseline.get("eventCount")
            and database.get("boardAttributionJourneyCount")
                >= migration_baseline.get("journeyCount")
            and database.get("w1aSchemaFingerprintSha256")
                == EXPECTED_W1A_SCHEMA_FINGERPRINT
            and all(environment.get(name, "").strip() == "false"
                    for name in FLAG_NAMES)
            and u3w_direct_healthy
            and me_api_healthy
            and admin_api_healthy
            and me_release_marker_matched
            and admin_release_marker_matched
            and default_off_ingress_probe_verified
        )
    deployment.update({
        "state": deployment_state if receipt_validated else None,
        "receiptValidated": receipt_validated,
        "receiptAnchorMatched": (
            bool(EXPECTED_DEPLOYMENT_RECEIPT_SHA256)
            and deployment_receipt_sha256
                == EXPECTED_DEPLOYMENT_RECEIPT_SHA256
        ),
        "sourceCommit": source_commit if receipt_validated else None,
        "strictHeadBuildUploadSwitchReceiptScriptPresent": (
            receipt.get("strictHeadBuildUploadSwitchReceiptScriptPresent") is True
            and runner_verified
        ),
        "applicationRollbackAssemblyVerified": (
            receipt.get("applicationRollbackAssemblyVerified") is True
            and application_rollback_assembly_verified
        ),
        "applicationRollbackProven": (
            receipt.get("applicationRollbackProven") is True
            and application_rollback_execution_verified
        ),
        "databaseRollbackSafetyProven": (
            receipt.get("databaseRollbackSafetyProven") is True
            and database_safety_verified
            and (
                (
                    deployment_state == "STAGED_FOR_SWITCH"
                    and staged_live_state_matched
                )
                or (
                    deployment_state == "DEPLOYED_DEFAULT_OFF"
                    and stable_database_identity_matched
                )
            )
        ),
        "stableDatabaseIdentityMatched":
            stable_database_identity_matched,
        "stagedLiveStateMatched": staged_live_state_matched,
        "rollbackLiveStateMatched": False,
        "releasePlanTargetBindingVerified":
            release_plan_target_binding_verified,
        "releasePlanTargetSha256": target_sha256,
        "releasePlanTargetComparableSha256":
            target_comparable_sha256,
        "databaseDownClaimed": receipt.get("databaseDownClaimed"),
        "actualActiveArtifactsMatched": active_artifacts_matched,
        "currentLinkResolved": current_link_resolved,
        "frontendTreeSha256": frontend_tree_digest,
        "u3wDirectCaptchaHealthy": u3w_direct_healthy,
        "mePortalApiHealthy": me_api_healthy,
        "adminPortalApiHealthy": admin_api_healthy,
        "mePortalReleaseMarkerMatched": me_release_marker_matched,
        "adminPortalReleaseMarkerMatched":
            admin_release_marker_matched,
        "defaultOffIngressProbeVerified":
            default_off_ingress_probe_verified,
        "defaultOffIngressProbe": default_off_ingress_probe,
    })

def release_file_custody(path):
    candidate = pathlib.Path(path)
    if not candidate.is_file() or candidate.is_symlink():
        return None
    status = candidate.stat()
    return {
        "path": str(candidate),
        "uid": status.st_uid,
        "gid": status.st_gid,
        "mode": oct(status.st_mode & 0o777),
        "nlink": status.st_nlink,
    }

fragment_path = service.get("FragmentPath")
unit_files = sorted(
    [
        {"path": item["path"], "sha256": item["sha256"]}
        for item in [fragment_file_manifest] + release_dropin_manifest
    ],
    key=lambda item: item["path"],
)
current_link = pathlib.Path("/opt/fbsir/admin/current")
try:
    current_link_resolved_fact = (
        str(current_link.resolve(strict=True))
        if current_link.is_symlink() else None
    )
except FileNotFoundError:
    current_link_resolved_fact = None
current_link_exists_fact = (
    current_link.exists() or current_link.is_symlink()
)
release_dropin_path = pathlib.Path(
    "/etc/systemd/system/fbsir-admin.service.d/"
    "20-u3w-default-off-release.conf"
)
release_dropin_exists = (
    release_dropin_path.exists() or release_dropin_path.is_symlink()
)
prior_rollback_anchor = None
if rollback_valid and latest_document is not None:
    prior_rollback_anchor = {
        "releaseId": latest_document["releaseId"],
        "sourceCommit": latest_document["sourceCommit"],
        "receiptPath": str(resolved_latest),
        "receiptSha256": latest_sha256,
        "receiptSchema": latest_document["schema"],
        "state": latest_document["state"],
    }
prior_recovery_anchor = None
if recovery_valid and latest_document is not None:
    prior_recovery_anchor = {
        "releaseId": latest_document["releaseId"],
        "sourceCommit": latest_document["sourceCommit"],
        "receiptPath": str(resolved_latest),
        "receiptSha256": latest_sha256,
        "receiptSchema": latest_document["schema"],
        "state": latest_document["state"],
    }
if (
    not pathlib.Path("/opt/fbsir/admin/releases").exists()
    and not pathlib.Path("/opt/fbsir/admin/releases").is_symlink()
    and
    not current_link_exists_fact
    and not release_dropin_exists
    and latest_document is None
):
    stage_entry_topology = {
        "state": "UNTOUCHED_LEGACY",
        "priorRollbackAnchor": None,
        "priorRecoveryAnchor": None,
    }
elif (
    pathlib.Path("/opt/fbsir/admin/releases").is_dir()
    and not pathlib.Path("/opt/fbsir/admin/releases").is_symlink()
    and
    not current_link_exists_fact
    and release_dropin_exists
    and prior_rollback_anchor is not None
    and prior_recovery_anchor is None
):
    stage_entry_topology = {
        "state": "EXACT_PRIOR_ROLLBACK_PREDECESSOR",
        "priorRollbackAnchor": prior_rollback_anchor,
        "priorRecoveryAnchor": None,
    }
elif (
    pathlib.Path("/opt/fbsir/admin/releases").is_dir()
    and not pathlib.Path("/opt/fbsir/admin/releases").is_symlink()
    and
    not current_link_exists_fact
    and release_dropin_exists
    and prior_rollback_anchor is None
    and prior_recovery_anchor is not None
    and recovery_live_state_matched
):
    stage_entry_topology = {
        "state":
            "EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR",
        "priorRollbackAnchor": None,
        "priorRecoveryAnchor": prior_recovery_anchor,
    }
else:
    stage_entry_topology = {
        "state": "INVALID_STAGE_ENTRY",
        "priorRollbackAnchor": prior_rollback_anchor,
        "priorRecoveryAnchor": prior_recovery_anchor,
    }
release_target_facts = {
    "schema": "fbsir.u3wDefaultOffRemotePlanSnapshot.v1",
    "targetHost": TARGET_HOST,
    "serviceUnit": SERVICE_UNIT,
    "service": service,
    "unitSha256": (
        sha256_file(fragment_path)
        if fragment_path and pathlib.Path(fragment_path).is_file()
        else None
    ),
    "fragmentFileManifest": fragment_file_manifest,
    "dropInManifest": release_dropin_manifest,
    "unitFiles": unit_files,
    "environmentCustody": release_file_custody(
        "/etc/u3w/fbsir-admin.env"
    ),
    "environmentSha256": sha256_file(
        "/etc/u3w/fbsir-admin.env"
    ),
    "environmentFilePaths": environment_file_paths,
    "environmentFileManifest": environment_file_manifests,
    "additionalConfigCustody": release_file_custody(
        "/opt/fbsir/admin/application-connector.yml"
    ),
    "additionalConfigSha256": sha256_file(
        "/opt/fbsir/admin/application-connector.yml"
    ),
    "externalConfigManifest": external_config_manifest,
    "activeJarPath": jar_path,
    "activeJarSha256": jar_digest,
    "configuredJarPath": configured_jar_path,
    "configuredJarSha256": (
        sha256_file(configured_jar_path)
        if configured_jar_path
        and pathlib.Path(configured_jar_path).is_file()
        else None
    ),
    "processJarPath": process_jar_path,
    "processJarSha256": (
        sha256_file(process_jar_path)
        if process_jar_path
        and pathlib.Path(process_jar_path).is_file()
        else None
    ),
    "processArgvSha256": process_argv_sha256,
    "processEnvironmentNamesSha256":
        process_environment_names_sha256,
    "processFlagValues": process_flag_values,
    "configuredFlagValues":
        process_security_evidence["configuredFlagValues"],
    "processForbiddenOverrideNames":
        process_forbidden_override_names,
    "processSecurityConfigurationNames":
        process_security_evidence[
            "processSecurityConfigurationNames"
        ],
    "processSecurityConfigurationHmacSha256":
        process_security_evidence[
            "processSecurityConfigurationHmacSha256"
        ],
    "expectedSecurityConfigurationNames":
        process_security_evidence[
            "expectedSecurityConfigurationNames"
        ],
    "expectedSecurityConfigurationHmacSha256":
        process_security_evidence[
            "expectedSecurityConfigurationHmacSha256"
        ],
    "processDatabaseBindingMatched":
        process_security_evidence[
            "processDatabaseBindingMatched"
        ],
    "configuredEnvironmentSha256":
        process_security_evidence[
            "configuredEnvironmentSha256"
        ],
    "configuredEnvironmentNames":
        process_security_evidence[
            "configuredEnvironmentNames"
        ],
    "configuredEnvironmentHmacSha256":
        process_security_evidence[
            "configuredEnvironmentHmacSha256"
        ],
    "processConfiguredEnvironmentHmacSha256":
        process_security_evidence[
            "processConfiguredEnvironmentHmacSha256"
        ],
    "processConfiguredEnvironmentMatched":
        process_security_evidence[
            "processConfiguredEnvironmentMatched"
        ],
    "processConfiguredEnvironmentMismatchNames":
        process_security_evidence[
            "processConfiguredEnvironmentMismatchNames"
        ],
    "processPendingRestartEnvironmentNames":
        process_security_evidence[
            "processPendingRestartEnvironmentNames"
        ],
    "processConfiguredEnvironmentLoadState":
        process_security_evidence[
            "processConfiguredEnvironmentLoadState"
        ],
    "processConfiguredEnvironmentPreStageCompatible":
        process_security_evidence[
            "processConfiguredEnvironmentPreStageCompatible"
        ],
    "api2EventKeyManifest":
        process_security_evidence["api2EventKeyManifest"],
    "nginxConfigs": nginx_configs,
    "activeNginxManifest": nginx_configs,
    "nginxDumpSha256": nginx_dump_sha256,
    "releaseRootExists": pathlib.Path(
        "/opt/fbsir/admin/releases"
    ).exists(),
    "releaseRootEntryManifest": release_root_entry_manifest(),
    "currentLinkExists": current_link_exists_fact,
    "currentLinkResolved": current_link_resolved_fact,
    "currentLifecycleState": (
        prior_recovery_anchor["state"]
        if prior_recovery_anchor is not None
        else prior_rollback_anchor["state"]
        if prior_rollback_anchor is not None
        else (
            deployment.get("state")
            or stage_entry_topology["state"]
        )
    ),
    "stageEntryTopology": stage_entry_topology,
    "productionChanged": (
        deployment.get("state") in {
            "DEPLOYED_DEFAULT_OFF",
            "ROLLED_BACK_APPLICATION_DATABASE_043_RETAINED_DORMANT",
            "ROLLED_BACK_APPLICATION_DATABASE_CURRENT_READ_UNAVAILABLE",
            INTERRUPTED_APPLY_RECOVERY_STATE,
        }
    ),
    "productionChangedByPlan": False,
}

print(json.dumps({
    "observedAt": datetime.now(timezone.utc).isoformat(),
    "target": {
        "host": TARGET_HOST,
        "authorityObserved": run(["id", "-un"]),
        "serviceUnit": SERVICE_UNIT,
        "serviceState": service.get("ActiveState"),
    },
    "runtime": {
        "jarPath": jar_path,
        "jarSha256": jar_digest,
        "configuredJarPath": configured_jar_path,
        "configuredJarSha256": (
            sha256_file(configured_jar_path)
            if configured_jar_path
            and pathlib.Path(configured_jar_path).is_file()
            else None
        ),
        "processJarPath": process_jar_path,
        "processJarSha256": (
            sha256_file(process_jar_path)
            if process_jar_path
            and pathlib.Path(process_jar_path).is_file()
            else None
        ),
        "fragmentFileManifest": fragment_file_manifest,
        "processEnvironmentNamesSha256":
            process_environment_names_sha256,
        "processSecurityConfigurationNames":
            process_security_evidence[
                "processSecurityConfigurationNames"
            ],
        "processSecurityConfigurationHmacSha256":
            process_security_evidence[
                "processSecurityConfigurationHmacSha256"
            ],
        "processDatabaseBindingMatched":
            process_security_evidence[
                "processDatabaseBindingMatched"
            ],
        "configuredEnvironmentSha256":
            process_security_evidence[
                "configuredEnvironmentSha256"
            ],
        "configuredEnvironmentNames":
            process_security_evidence[
                "configuredEnvironmentNames"
            ],
        "configuredEnvironmentHmacSha256":
            process_security_evidence[
                "configuredEnvironmentHmacSha256"
            ],
        "processConfiguredEnvironmentHmacSha256":
            process_security_evidence[
                "processConfiguredEnvironmentHmacSha256"
            ],
        "processConfiguredEnvironmentMatched":
            process_security_evidence[
                "processConfiguredEnvironmentMatched"
            ],
        "configuredFlagValues":
            process_security_evidence["configuredFlagValues"],
        "processConfiguredEnvironmentMismatchNames":
            process_security_evidence[
                "processConfiguredEnvironmentMismatchNames"
            ],
        "processPendingRestartEnvironmentNames":
            process_security_evidence[
                "processPendingRestartEnvironmentNames"
            ],
        "processConfiguredEnvironmentLoadState":
            process_security_evidence[
                "processConfiguredEnvironmentLoadState"
            ],
        "processConfiguredEnvironmentPreStageCompatible":
            process_security_evidence[
                "processConfiguredEnvironmentPreStageCompatible"
            ],
        "externalConfigManifest": external_config_manifest,
        "attributionClassCount": attribution_class_count,
    },
    "database": database,
    "portals": {
        "meHttpStatus": http_status("https://me.u3w.com/"),
        "adminHttpStatus": http_status("https://admin.u3w.com/"),
        "u3wDirectCaptcha": http_json_status(
            "http://127.0.0.1:8080/captchaImage"
        ),
        "meApiCaptcha": http_json_status(
            "https://me.u3w.com/prod-api/captchaImage"
        ),
        "adminApiCaptcha": http_json_status(
            "https://admin.u3w.com/prod-api/captchaImage"
        ),
    },
    "configuration": {
        "environmentKeyNames": key_names,
        "explicitFalseKeyNames": explicit_false,
        "eventKeyEntryCount": event_key_count,
        "activeEventKeyPairPresent": active_event_key_pair_present,
        "activeEventKeyId": active_event_key_id or None,
        "previousEventKeyPairComplete": previous_event_key_pair_complete,
        "sameBindingSecretPresent": same_binding_present,
        "stagedApi2EventKeyMaterialMatched": api2_event_key_file_matched,
        "api2EventKeyFileCustodySecure":
            api2_event_key_file_custody_secure,
        "environmentFilePathExact": environment_file_path_exact,
        "environmentFileCustodySecure": environment_file_custody_secure,
        "cryptographicConfigurationShapeValid": cryptographic_shape_valid,
        "adminEngineCredentialValid": bool(
            admin_engine_credential_valid
            and receipt_evidence.get("adminEngineCredentialValid") is True
        ),
        "adminEngineCredentialIndependent": bool(
            admin_engine_credential_independent
            and receipt_evidence.get(
                "adminEngineCredentialIndependent"
            ) is True
        ),
        "configurationReceiptValid": configuration_receipt_valid,
        "configurationReceiptAnchorMatched":
            configuration_receipt_anchor_matched,
        "configurationReceiptSourceCommit":
            configuration_receipt_source_commit,
        "configurationReceiptSha256": configuration_receipt_sha256,
        "configurationReceiptSchema":
            configuration_receipt.get("schema"),
        "engineCounterpartClosureClaimed":
            configuration_receipt.get(
                "engineCounterpartClosureClaimed"
            ),
    },
    "backup": backup,
    "deploymentChannel": deployment,
    "releaseTargetFacts": release_target_facts,
}, ensure_ascii=False))
'@
    $backupAnchor = if ($ExpectedBackupReceiptSha256) {
        $ExpectedBackupReceiptSha256.ToLowerInvariant()
    } else { '' }
    $backupPlanAnchor = if ($ExpectedBackupPlanReceiptSha256) {
        $ExpectedBackupPlanReceiptSha256.ToLowerInvariant()
    } else { '' }
    $deploymentAnchor = if ($ExpectedDeploymentReceiptSha256) {
        $ExpectedDeploymentReceiptSha256.ToLowerInvariant()
    } else { '' }
    $legacyBaselineAnchor = if ($ExpectedLegacyBaselineReceiptDigest) {
        $ExpectedLegacyBaselineReceiptDigest.ToLowerInvariant()
    } else { '' }
    $adminRootDependencyAdoptionAnchor = if (
        $ExpectedAdminRootDependencyAdoptionReceiptSha256
    ) {
        $ExpectedAdminRootDependencyAdoptionReceiptSha256.ToLowerInvariant()
    } else { '' }
    $configurationAnchor = if ($ExpectedConfigurationReceiptSha256) {
        $ExpectedConfigurationReceiptSha256.ToLowerInvariant()
    } else { '' }
    $releasePlanAnchor = if ($ExpectedReleasePlanReceiptSha256) {
        $ExpectedReleasePlanReceiptSha256.ToLowerInvariant()
    } else { '' }
    $backupRunnerSha256 = Get-CommittedFileSha256 (
        'scripts/run-u3w-production-backup-restore.ps1')
    $backupWorkerSha256 = Get-CommittedFileSha256 (
        'scripts/u3w-production-backup-remote.py')
    $restoreVerifierSha256 = Get-CommittedFileSha256 (
        'scripts/u3w-isolated-restore-verifier-remote.py')
    $baselineRunnerSha256 = Get-CommittedFileSha256 (
        'scripts/run-u3w-legacy-baseline.ps1')
    $baselineWorkerSha256 = Get-CommittedFileSha256 (
        'scripts/u3w-legacy-baseline-remote.py')
    $adminRootDependencyRunnerSha256 = Get-CommittedFileSha256 (
        'scripts/run-u3w-admin-root-dependency.ps1')
    $adminRootDependencyWorkerSha256 = Get-CommittedFileSha256 (
        'scripts/u3w-admin-root-dependency-remote.py')
    $configurationRunnerSha256 = Get-CommittedFileSha256 (
        'scripts/run-u3w-default-off-configuration.ps1')
    $configurationWorkerSha256 = Get-CommittedFileSha256 (
        'scripts/u3w-default-off-configuration-remote.py')
    $releaseRunnerSha256 = Get-CommittedFileSha256 (
        'scripts/deploy-independent-board-default-off.ps1')
    $releaseWorkerSha256 = Get-CommittedFileSha256 (
        'scripts/u3w-default-off-release-remote.py')
    $remotePython = $remotePython.
        Replace('__SERVICE_UNIT__', ($ServiceUnit | ConvertTo-Json -Compress)).
        Replace('__TARGET_HOST__', ((($SshTarget -split '@', 2)[1]) | ConvertTo-Json -Compress)).
        Replace('__BACKUP_RECEIPT_PATH__', ($DatabaseBackupReceiptPath | ConvertTo-Json -Compress)).
        Replace('__DEPLOYMENT_RECEIPT_PATH__', ($DeploymentReceiptPath | ConvertTo-Json -Compress)).
        Replace('__LEGACY_BASELINE_RECEIPT_PATH__', ($LegacyBaselineReceiptPath | ConvertTo-Json -Compress)).
        Replace('__ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_PATH__', ($AdminRootDependencyAdoptionReceiptPath | ConvertTo-Json -Compress)).
        Replace('__CONFIGURATION_RECEIPT_PATH__', ($ConfigurationReceiptPath | ConvertTo-Json -Compress)).
        Replace('__API2_EVENT_KEY_PATH__', ($Api2EventKeyPath | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_BACKUP_RECEIPT_SHA256__', ($backupAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_BACKUP_PLAN_RECEIPT_SHA256__', ($backupPlanAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_DEPLOYMENT_RECEIPT_SHA256__', ($deploymentAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_LEGACY_BASELINE_RECEIPT_DIGEST__', ($legacyBaselineAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_ADMIN_ROOT_DEPENDENCY_ADOPTION_RECEIPT_SHA256__', ($adminRootDependencyAdoptionAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_CONFIGURATION_RECEIPT_SHA256__', ($configurationAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_RELEASE_PLAN_RECEIPT_SHA256__', ($releasePlanAnchor | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_SOURCE_COMMIT__', ($ExpectedCommit.ToLowerInvariant() | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_BACKUP_RUNNER_SHA256__', ($backupRunnerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_BACKUP_WORKER_SHA256__', ($backupWorkerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_RESTORE_VERIFIER_SHA256__', ($restoreVerifierSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_BASELINE_RUNNER_SHA256__', ($baselineRunnerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_BASELINE_WORKER_SHA256__', ($baselineWorkerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_ADMIN_ROOT_DEPENDENCY_RUNNER_SHA256__', ($adminRootDependencyRunnerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_ADMIN_ROOT_DEPENDENCY_WORKER_SHA256__', ($adminRootDependencyWorkerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_CONFIGURATION_RUNNER_SHA256__', ($configurationRunnerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_CONFIGURATION_WORKER_SHA256__', ($configurationWorkerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_RELEASE_RUNNER_SHA256__', ($releaseRunnerSha256 | ConvertTo-Json -Compress)).
        Replace('__EXPECTED_RELEASE_WORKER_SHA256__', ($releaseWorkerSha256 | ConvertTo-Json -Compress))
    $remoteBytes = [Text.Encoding]::UTF8.GetBytes($remotePython)
    $remoteSha256 = Get-BytesSha256 $remoteBytes
    $payload = [Convert]::ToBase64String($remoteBytes)
    $bootstrap = 'import base64,hashlib,sys;' +
        'b=base64.b64decode(sys.stdin.buffer.read());' +
        'e=sys.argv.pop(1);a=hashlib.sha256(b).hexdigest();' +
        'a==e or sys.exit("collector sha256 mismatch");' +
        'exec(compile(b,"<u3w-readiness-collector>","exec"),' +
        '{"__name__":"__main__"})'
    $escapedBootstrap = $bootstrap.Replace('"', '\"')
    $remoteCommand = "python3 -c '$escapedBootstrap' $remoteSha256"
    $output = $payload | & ssh.exe `
        -i $SshKeyPath `
        -o BatchMode=yes `
        -o IdentitiesOnly=yes `
        -o IdentityAgent=none `
        -o ConnectTimeout=10 `
        -o ConnectionAttempts=1 `
        -o StrictHostKeyChecking=yes `
        -o UserKnownHostsFile=$KnownHostsPath `
        $SshTarget `
        $remoteCommand
    if ($LASTEXITCODE -ne 0) {
        throw 'remote production-readiness snapshot failed'
    }
    return (($output -join "`n") | ConvertFrom-Json)
}

$gitStateBefore = Get-GitState
$snapshot = Invoke-RemoteSnapshot
$gitState = Get-GitState
if (
    $gitStateBefore.sourceCommit -cne $gitState.sourceCommit -or
    $gitStateBefore.releasePlanReceiptSha256 -cne
        $gitState.releasePlanReceiptSha256 -or
    -not $gitState.clean
) {
    throw 'local Git or release-plan state drifted during remote collection'
}
$handoffRoot = Split-Path (Split-Path $RepoRoot -Parent) -Parent
$productionEvidenceDirectory =
    Join-Path $handoffRoot 'deliverables\production-evidence'
$backupExternalAnchorVerified = $false
$backupExternalAnchorSchema = $null
$backupRunId = [string]$snapshot.backup.runId
if (
    $backupRunId -cmatch
        '^w1a-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$'
) {
    $backupAnchorPath = Join-Path $productionEvidenceDirectory (
        "$backupRunId-anchor.json")
    $backupReceiptPath = Join-Path $productionEvidenceDirectory (
        "$backupRunId-receipt.json")
    $backupPlanPath = Join-Path $productionEvidenceDirectory (
        "$backupRunId-plan.json")
    if (
        $ExpectedBackupReceiptSha256 -and
        $ExpectedBackupPlanReceiptSha256 -and
        $ExpectedAdminRootDependencyAdoptionReceiptSha256 -and
        (Test-Path -LiteralPath $backupAnchorPath -PathType Leaf) -and
        (Test-Path -LiteralPath $backupReceiptPath -PathType Leaf) -and
        (Test-Path -LiteralPath $backupPlanPath -PathType Leaf)
    ) {
        $backupAnchor = Get-Content -Raw -LiteralPath $backupAnchorPath |
            ConvertFrom-Json
        $backupAnchorFields = @(
            $backupAnchor.psobject.Properties.Name | Sort-Object
        )
        $expectedBackupAnchorFields = @(
            'schema', 'runId', 'sourceCommit', 'targetHost',
            'planReceiptSha256', 'planReceiptPath',
            'bundleReceiptPath', 'bundleReceiptSha256',
            'adminRootDependencyAdoptionReceiptSha256',
            'sourceDatabaseServerUuid', 'capturedAt'
        ) | Sort-Object
        $localBackupReceiptSha256 = (
            Get-FileHash -LiteralPath $backupReceiptPath -Algorithm SHA256
        ).Hash.ToLowerInvariant()
        $localBackupPlanSha256 = (
            Get-FileHash -LiteralPath $backupPlanPath -Algorithm SHA256
        ).Hash.ToLowerInvariant()
        try {
            $backupAnchorCapturedAt =
                [DateTimeOffset]::Parse([string]$backupAnchor.capturedAt)
            $backupAnchorCapturedAtValid = (
                $backupAnchorCapturedAt.Offset -eq [TimeSpan]::Zero -and
                $backupAnchorCapturedAt -le [DateTimeOffset]::UtcNow
            )
        }
        catch {
            $backupAnchorCapturedAtValid = $false
        }
        $backupExternalAnchorSchema = [string]$backupAnchor.schema
        $backupExternalAnchorVerified = (
            ($backupAnchorFields -join "`n") -ceq
                ($expectedBackupAnchorFields -join "`n") -and
            $backupAnchor.schema -ceq
                'fbsir.u3wDatabaseBackupRestoreExternalAnchor.v3' -and
            $backupAnchor.runId -ceq $backupRunId -and
            $backupAnchor.sourceCommit -ceq
                [string]$snapshot.backup.sourceCommit -and
            $backupAnchor.planReceiptSha256 -ceq
                $ExpectedBackupPlanReceiptSha256.ToLowerInvariant() -and
            $backupAnchor.planReceiptSha256 -ceq
                [string]$snapshot.backup.planReceiptSha256 -and
            $backupAnchor.planReceiptPath -ceq $backupPlanPath -and
            $localBackupPlanSha256 -ceq
                $ExpectedBackupPlanReceiptSha256.ToLowerInvariant() -and
            $backupAnchor.targetHost -ceq 'api2.u3w.com' -and
            $backupAnchor.bundleReceiptPath -ceq $backupReceiptPath -and
            $backupAnchor.bundleReceiptSha256 -ceq
                $localBackupReceiptSha256 -and
            $localBackupReceiptSha256 -ceq
                $ExpectedBackupReceiptSha256.ToLowerInvariant() -and
            $backupAnchor.adminRootDependencyAdoptionReceiptSha256 -ceq
                $ExpectedAdminRootDependencyAdoptionReceiptSha256.
                    ToLowerInvariant() -and
            $backupAnchor.sourceDatabaseServerUuid -ceq
                [string]$snapshot.backup.sourceDatabaseServerUuid -and
            $backupAnchorCapturedAtValid
        )
    }
}
$snapshot.backup | Add-Member -NotePropertyName externalAnchorSchema `
    -NotePropertyValue $backupExternalAnchorSchema -Force
$snapshot.backup | Add-Member -NotePropertyName externalAnchorVerified `
    -NotePropertyValue $backupExternalAnchorVerified -Force

$adoptionExternalAnchorVerified = $false
$adoptionRunId =
    [string]$snapshot.database.adminRootDependencyAdoptionRunId
if (
    $adoptionRunId -cmatch
        '^w1a-admin-root-dependency-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{12}$'
) {
    $adoptionAnchorPath = Join-Path $productionEvidenceDirectory (
        "$adoptionRunId-admin-root-dependency-external-anchor.json")
    $adoptionReceiptPath = Join-Path $productionEvidenceDirectory (
        "$adoptionRunId-admin-root-dependency-adoption-receipt.json")
    if (
        $ExpectedAdminRootDependencyAdoptionReceiptSha256 -and
        (Test-Path -LiteralPath $adoptionAnchorPath -PathType Leaf) -and
        (Test-Path -LiteralPath $adoptionReceiptPath -PathType Leaf)
    ) {
        $adoptionAnchor =
            Get-Content -Raw -LiteralPath $adoptionAnchorPath |
                ConvertFrom-Json
        $adoptionAnchorFields = @(
            $adoptionAnchor.psobject.Properties.Name | Sort-Object
        )
        $expectedAdoptionAnchorFields = @(
            'schema', 'runId', 'sourceCommit', 'targetHost',
            'adoptionReceiptPath', 'adoptionReceiptSha256',
            'databaseServerUuid', 'liveFactsSha256',
            'baselineLatestReceiptSha256',
            'backupLatestReceiptSha256', 'planReceiptPath',
            'planReceiptSha256', 'approvalReceiptSha256',
            'runnerSha256', 'workerSha256', 'capturedAt'
        ) | Sort-Object
        $localAdoptionReceiptSha256 = (
            Get-FileHash -LiteralPath $adoptionReceiptPath -Algorithm SHA256
        ).Hash.ToLowerInvariant()
        $planReceiptPath = [string]$adoptionAnchor.planReceiptPath
        $expectedPlanReceiptPath = Join-Path $productionEvidenceDirectory (
            "$adoptionRunId-admin-root-dependency-plan.json")
        $localPlanReceiptSha256 = if (
            $planReceiptPath -ceq $expectedPlanReceiptPath -and
            (Test-Path -LiteralPath $planReceiptPath -PathType Leaf)
        ) {
            (
                Get-FileHash -LiteralPath $planReceiptPath -Algorithm SHA256
            ).Hash.ToLowerInvariant()
        } else { $null }
        $adoptionExternalAnchorVerified = (
            ($adoptionAnchorFields -join "`n") -ceq
                ($expectedAdoptionAnchorFields -join "`n") -and
            $adoptionAnchor.schema -ceq
                'fbsir.u3wAdminRootDependencyExternalAnchor.v3' -and
            $adoptionAnchor.runId -ceq $adoptionRunId -and
            $adoptionAnchor.sourceCommit -ceq
                [string]$snapshot.database.
                    adminRootDependencyAdoptionSourceCommit -and
            $adoptionAnchor.targetHost -ceq 'api2.u3w.com' -and
            $adoptionAnchor.adoptionReceiptPath -ceq
                $adoptionReceiptPath -and
            $adoptionAnchor.adoptionReceiptSha256 -ceq
                $localAdoptionReceiptSha256 -and
            $localAdoptionReceiptSha256 -ceq
                $ExpectedAdminRootDependencyAdoptionReceiptSha256.
                    ToLowerInvariant() -and
            $adoptionAnchor.databaseServerUuid -ceq
                [string]$snapshot.database.databaseServerUuid -and
            $adoptionAnchor.liveFactsSha256 -ceq
                [string]$snapshot.database.
                    adminRootDependencyAdoptionLiveFactsSha256 -and
            $adoptionAnchor.baselineLatestReceiptSha256 -ceq
                [string]$snapshot.database.
                    adminRootDependencyAdoptionBaselineReceiptSha256 -and
            $adoptionAnchor.backupLatestReceiptSha256 -ceq
                [string]$snapshot.database.
                    adminRootDependencyAdoptionBackupReceiptSha256 -and
            $adoptionAnchor.planReceiptPath -ceq
                $expectedPlanReceiptPath -and
            $adoptionAnchor.planReceiptSha256 -ceq
                $localPlanReceiptSha256 -and
            $adoptionAnchor.planReceiptSha256 -ceq
                [string]$snapshot.database.
                    adminRootDependencyAdoptionPlanReceiptSha256 -and
            $adoptionAnchor.approvalReceiptSha256 -ceq
                [string]$snapshot.database.
                    adminRootDependencyAdoptionApprovalReceiptSha256 -and
            $adoptionAnchor.runnerSha256 -ceq
                [string]$snapshot.database.
                    adminRootDependencyAdoptionRunnerSha256 -and
            $adoptionAnchor.workerSha256 -ceq
                [string]$snapshot.database.
                    adminRootDependencyAdoptionWorkerSha256 -and
            [string]$adoptionAnchor.capturedAt -cmatch
                '^[0-9]{4}-[0-9]{2}-[0-9]{2}T'
        )
    }
}
$snapshot.database.adminRootDependencyAdoptionReceiptAnchorMatched = (
    $snapshot.database.adminRootDependencyAdoptionReceiptAnchorMatched -eq
        $true -and
    $adoptionExternalAnchorVerified
)
$preparationSourceCommits = @(
    [string]$snapshot.configuration.configurationReceiptSourceCommit,
    [string]$snapshot.backup.sourceCommit,
    [string]$snapshot.database.adminRootDependencyAdoptionSourceCommit
)
$expectedPreparationReceiptCount = 3
$preparationSourceCommits = @(
    $preparationSourceCommits |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
)
$uniquePreparationSourceCommits = @(
    $preparationSourceCommits | Sort-Object -Unique
)
$preparationSourcesConsistent = (
    $preparationSourceCommits.Count -eq
        $expectedPreparationReceiptCount -and
    $uniquePreparationSourceCommits.Count -eq 1 -and
    $uniquePreparationSourceCommits[0] -cmatch '^[0-9a-f]{40}$'
)
$preparationSourceCommit = if ($preparationSourcesConsistent) {
    [string]$uniquePreparationSourceCommits[0]
} else { $null }
$preparationCommitAncestor = $false
$legacyBaselineCommitAncestor = $false
if ($preparationSourceCommit) {
    & git -C $RepoRoot merge-base --is-ancestor `
        $preparationSourceCommit $gitState.sourceCommit 2>$null
    $preparationCommitAncestor = $LASTEXITCODE -eq 0
    $legacyBaselineSourceCommit =
        [string]$snapshot.database.legacyBaselineSourceCommit
    if ($legacyBaselineSourceCommit -cmatch '^[0-9a-f]{40}$') {
        & git -C $RepoRoot merge-base --is-ancestor `
            $legacyBaselineSourceCommit $preparationSourceCommit 2>$null
        $legacyBaselineCommitAncestor = $LASTEXITCODE -eq 0
    }
}
$gitState.preparationSourceCommit = $preparationSourceCommit
$gitState.preparationCommitAncestorOfSourceCommit =
    $preparationCommitAncestor
$gitState.preparationSourceCommitsConsistent =
    $preparationSourcesConsistent
$gitState['legacyBaselineCommitAncestorOfPreparationSourceCommit'] =
    $legacyBaselineCommitAncestor
$plannedTarget = $gitState.releasePlanTarget
$liveTarget = $snapshot.releaseTargetFacts
$releaseTargetMatched = $false
if ($plannedTarget -and $liveTarget) {
    $serviceFields = @(
        'ActiveState', 'MainPID', 'FragmentPath', 'DropInPaths',
        'EnvironmentFiles', 'User', 'Group',
        'ExecStart', 'WorkingDirectory', 'InvocationID',
        'ExecMainStartTimestampMonotonic', 'NRestarts'
    )
    $serviceMatched = @($serviceFields | Where-Object {
            [string]$plannedTarget.service.$_ -cne
                [string]$liveTarget.service.$_
        }).Count -eq 0
    $plannedNginx = @($plannedTarget.nginxConfigs)
    $liveNginx = @($liveTarget.nginxConfigs)
    $plannedActiveNginx = @($plannedTarget.activeNginxManifest)
    $liveActiveNginx = @($liveTarget.activeNginxManifest)
    $plannedDropIns = @($plannedTarget.dropInManifest)
    $liveDropIns = @($liveTarget.dropInManifest)
    $plannedEnvironmentPaths =
        @($plannedTarget.environmentFilePaths)
    $liveEnvironmentPaths = @($liveTarget.environmentFilePaths)
    $plannedEnvironmentManifest =
        @($plannedTarget.environmentFileManifest)
    $liveEnvironmentManifest =
        @($liveTarget.environmentFileManifest)
    $plannedCustody = $plannedTarget.environmentCustody
    $liveCustody = $liveTarget.environmentCustody
    $plannedAdditionalConfigCustody =
        $plannedTarget.additionalConfigCustody
    $liveAdditionalConfigCustody =
        $liveTarget.additionalConfigCustody
    $plannedExternalConfigManifest =
        @($plannedTarget.externalConfigManifest)
    $liveExternalConfigManifest =
        @($liveTarget.externalConfigManifest)
    $plannedUnits = @($plannedTarget.unitFiles)
    $liveUnits = @($liveTarget.unitFiles)
    $plannedStageEntry = $plannedTarget.stageEntryTopology
    $liveStageEntry = $liveTarget.stageEntryTopology
    $plannedStageEntryNames = @(
        $plannedStageEntry.psobject.Properties.Name)
    $liveStageEntryNames = @(
        $liveStageEntry.psobject.Properties.Name)
    $allowedStageEntryNames = @(
        'state', 'priorRollbackAnchor', 'priorRecoveryAnchor')
    $stageEntryFieldsValid = (
        @($plannedStageEntryNames | Where-Object {
                $_ -cnotin $allowedStageEntryNames
            }).Count -eq 0 -and
        @($liveStageEntryNames | Where-Object {
                $_ -cnotin $allowedStageEntryNames
            }).Count -eq 0 -and
        'state' -cin $plannedStageEntryNames -and
        'priorRollbackAnchor' -cin $plannedStageEntryNames -and
        'state' -cin $liveStageEntryNames -and
        'priorRollbackAnchor' -cin $liveStageEntryNames
    )
    $stageEntryMatched = (
        $stageEntryFieldsValid -and
        [string]$plannedStageEntry.state -ceq
            [string]$liveStageEntry.state -and
        (Test-JsonStructuralEquality `
            -Left $plannedStageEntry.priorRollbackAnchor `
            -Right $liveStageEntry.priorRollbackAnchor) -and
        (Test-JsonStructuralEquality `
            -Left $plannedStageEntry.priorRecoveryAnchor `
            -Right $liveStageEntry.priorRecoveryAnchor)
    )
    $plannedApi2EventKey = $plannedTarget.api2EventKeyManifest
    $liveApi2EventKey = $liveTarget.api2EventKeyManifest
    $plannedConfiguredFlags = $plannedTarget.configuredFlagValues
    $liveConfiguredFlags = $liveTarget.configuredFlagValues
    $plannedProcessFlags = $plannedTarget.processFlagValues
    $liveProcessFlags = $liveTarget.processFlagValues
    $plannedMismatchNames =
        @($plannedTarget.processConfiguredEnvironmentMismatchNames)
    $liveMismatchNames =
        @($liveTarget.processConfiguredEnvironmentMismatchNames)
    $plannedPendingNames =
        @($plannedTarget.processPendingRestartEnvironmentNames)
    $livePendingNames =
        @($liveTarget.processPendingRestartEnvironmentNames)
    $managedW1aNames = @(
        'FBSIR_BOARD_ATTRIBUTION_ENABLED',
        'FBSIR_BOARD_ATTRIBUTION_CANDIDATE_ENABLED',
        'FBSIR_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED',
        'FBSIR_BOARD_ATTRIBUTION_CREDIT_ENABLED',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_ENABLED',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_CANDIDATE_ENABLED',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PUBLIC_ROUTE_ENABLED',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_CREDIT_ENABLED',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_WRITER_ENABLED',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_INTENT_CLASSIFIER_ENABLED',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_OBSERVATION_ADMIN_READ_ENABLED',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PRODUCT_CREDIT_ENABLED',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY_ID',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_EVENT_KEY',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY_ID',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_PREVIOUS_EVENT_KEY',
        'FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_SAME_BINDING_SECRET',
        'FBSIR_ENGINE_TOKEN'
    ) | Sort-Object
    $liveProcessFlagValues =
        @($liveTarget.processFlagValues.psobject.Properties.Value)
    $liveLoadState = [string](
        $liveTarget.processConfiguredEnvironmentLoadState)
    $liveLoadStateValid = (
        (
            $liveLoadState -ceq 'EXACT_CONFIGURED' -and
            $liveTarget.processConfiguredEnvironmentMatched -eq $true -and
            @($liveTarget.processPendingRestartEnvironmentNames).Count -eq
                0 -and
            @($liveProcessFlagValues |
                Where-Object { $_ -cne 'false' }).Count -eq 0
        ) -or (
            $liveLoadState -ceq
                'LEGACY_MANAGED_CONFIGURATION_PENDING_RESTART' -and
            $liveTarget.processConfiguredEnvironmentMatched -eq $false -and
            (Test-JsonStructuralEquality `
                -Left $livePendingNames -Right $managedW1aNames) -and
            @($liveProcessFlagValues |
                Where-Object { $null -ne $_ }).Count -eq 0
        ) -or (
            $liveLoadState -ceq 'ENGINE_CREDENTIAL_PENDING_RESTART' -and
            $liveTarget.processConfiguredEnvironmentMatched -eq $false -and
            @($livePendingNames).Count -eq 1 -and
            $livePendingNames[0] -ceq 'FBSIR_ENGINE_TOKEN' -and
            @($liveProcessFlagValues |
                Where-Object { $_ -cne 'false' }).Count -eq 0
        )
    )
    $staticIdentityMatched = (
        $plannedTarget.schema -ceq $liveTarget.schema -and
        $plannedTarget.productionChangedByPlan -eq $false -and
        $liveTarget.productionChangedByPlan -eq $false -and
        $plannedTarget.targetHost -ceq $liveTarget.targetHost -and
        $plannedTarget.serviceUnit -ceq $liveTarget.serviceUnit -and
        $plannedTarget.unitSha256 -ceq $liveTarget.unitSha256 -and
        (Test-JsonStructuralEquality `
            -Left $plannedTarget.fragmentFileManifest `
            -Right $liveTarget.fragmentFileManifest) -and
        (Test-JsonStructuralEquality `
            -Left $plannedCustody -Right $liveCustody) -and
        $plannedTarget.environmentSha256 -ceq
            $liveTarget.environmentSha256 -and
        (Test-JsonStructuralEquality `
            -Left $plannedEnvironmentPaths `
            -Right $liveEnvironmentPaths) -and
        (Test-JsonStructuralEquality `
            -Left $plannedEnvironmentManifest `
            -Right $liveEnvironmentManifest) -and
        (Test-JsonStructuralEquality `
            -Left $plannedAdditionalConfigCustody `
            -Right $liveAdditionalConfigCustody) -and
        $plannedTarget.additionalConfigSha256 -ceq
            $liveTarget.additionalConfigSha256 -and
        (Test-JsonStructuralEquality `
            -Left $plannedExternalConfigManifest `
            -Right $liveExternalConfigManifest) -and
        (Test-JsonStructuralEquality `
            -Left @($plannedTarget.expectedSecurityConfigurationNames) `
            -Right @($liveTarget.expectedSecurityConfigurationNames)) -and
        $plannedTarget.expectedSecurityConfigurationHmacSha256 -ceq
            $liveTarget.expectedSecurityConfigurationHmacSha256 -and
        $plannedTarget.configuredEnvironmentSha256 -ceq
            $liveTarget.configuredEnvironmentSha256 -and
        (Test-JsonStructuralEquality `
            -Left @($plannedTarget.configuredEnvironmentNames) `
            -Right @($liveTarget.configuredEnvironmentNames)) -and
        $plannedTarget.configuredEnvironmentHmacSha256 -ceq
            $liveTarget.configuredEnvironmentHmacSha256 -and
        (Test-JsonStructuralEquality `
            -Left $plannedConfiguredFlags `
            -Right $liveConfiguredFlags) -and
        @($liveTarget.configuredFlagValues.psobject.Properties.Value |
            Where-Object { $_ -cne 'false' }).Count -eq 0 -and
        (Test-JsonStructuralEquality `
            -Left $plannedApi2EventKey `
            -Right $liveApi2EventKey)
    )
    $statefulProcessIdentityMatched = (
        $plannedTarget.processEnvironmentNamesSha256 -ceq
            $liveTarget.processEnvironmentNamesSha256 -and
        (Test-JsonStructuralEquality `
            -Left @($plannedTarget.processSecurityConfigurationNames) `
            -Right @($liveTarget.processSecurityConfigurationNames)) -and
        $plannedTarget.processSecurityConfigurationHmacSha256 -ceq
            $liveTarget.processSecurityConfigurationHmacSha256 -and
        (Test-JsonStructuralEquality `
            -Left $plannedProcessFlags `
            -Right $liveProcessFlags) -and
        (Test-JsonStructuralEquality `
            -Left @($plannedTarget.processForbiddenOverrideNames) `
            -Right @($liveTarget.processForbiddenOverrideNames)) -and
        @($liveTarget.processForbiddenOverrideNames).Count -eq 0 -and
        $plannedTarget.processDatabaseBindingMatched -eq $true -and
        $liveTarget.processDatabaseBindingMatched -eq $true -and
        $plannedTarget.processConfiguredEnvironmentHmacSha256 -ceq
            $liveTarget.processConfiguredEnvironmentHmacSha256 -and
        $plannedTarget.processConfiguredEnvironmentMatched -eq
            $liveTarget.processConfiguredEnvironmentMatched -and
        (Test-JsonStructuralEquality `
            -Left $plannedMismatchNames -Right $liveMismatchNames) -and
        @($liveTarget.processConfiguredEnvironmentMismatchNames).Count -eq
            0 -and
        (Test-JsonStructuralEquality `
            -Left $plannedPendingNames -Right $livePendingNames) -and
        $plannedTarget.processConfiguredEnvironmentLoadState -ceq
            $liveTarget.processConfiguredEnvironmentLoadState -and
        $plannedTarget.processConfiguredEnvironmentPreStageCompatible -eq
            $true -and
        $liveTarget.processConfiguredEnvironmentPreStageCompatible -eq
            $true -and
        $liveLoadStateValid
    )
    $identityMatched = (
        $staticIdentityMatched -and $statefulProcessIdentityMatched)
    $deploymentState = [string]$snapshot.deploymentChannel.state
    if ($deploymentState -eq 'DEPLOYED_DEFAULT_OFF') {
        $releaseTargetMatched = (
            $staticIdentityMatched -and
            $snapshot.deploymentChannel.receiptValidated -eq $true -and
            $snapshot.deploymentChannel.releasePlanTargetBindingVerified -eq
                $true -and
            $snapshot.deploymentChannel.sourceCommit -ceq
                $gitState.sourceCommit -and
            $snapshot.deploymentChannel.actualActiveArtifactsMatched -eq
                $true -and
            $liveLoadState -ceq 'EXACT_CONFIGURED' -and
            $liveTarget.processConfiguredEnvironmentMatched -eq $true -and
            $liveTarget.releaseRootExists -eq $true -and
            $liveTarget.currentLinkExists -eq $true -and
            [string]$liveTarget.currentLinkResolved -ceq
                [string]$snapshot.deploymentChannel.currentLinkResolved -and
            [string]$liveTarget.currentLinkResolved -like
                '/opt/fbsir/admin/releases/w1a-release-*' -and
            $liveTarget.productionChanged -eq $true
        )
    }
    elseif ($deploymentState -eq 'STAGED_FOR_SWITCH') {
        $releaseTargetMatched = (
            $identityMatched -and
            $snapshot.deploymentChannel.receiptValidated -eq $true -and
            $snapshot.deploymentChannel.releasePlanTargetBindingVerified -eq
                $true -and
            $snapshot.deploymentChannel.stagedLiveStateMatched -eq $true -and
            $serviceMatched -and
            (Test-JsonStructuralEquality `
                -Left $plannedUnits -Right $liveUnits) -and
            $plannedTarget.activeJarPath -ceq
                $liveTarget.activeJarPath -and
            $plannedTarget.activeJarSha256 -ceq
                $liveTarget.activeJarSha256 -and
            $plannedTarget.configuredJarSha256 -ceq
                $liveTarget.configuredJarSha256 -and
            $plannedTarget.processJarSha256 -ceq
                $liveTarget.processJarSha256 -and
            $plannedTarget.processArgvSha256 -ceq
                $liveTarget.processArgvSha256 -and
            (Test-JsonStructuralEquality `
                -Left $plannedDropIns -Right $liveDropIns) -and
            (Test-JsonStructuralEquality `
                -Left $plannedNginx -Right $liveNginx) -and
            (Test-JsonStructuralEquality `
                -Left $plannedActiveNginx -Right $liveActiveNginx) -and
            $plannedTarget.nginxDumpSha256 -ceq
                $liveTarget.nginxDumpSha256 -and
            $liveTarget.releaseRootExists -eq $true -and
            $plannedTarget.currentLinkExists -eq
                $liveTarget.currentLinkExists -and
            $liveTarget.productionChanged -eq $false
        )
    }
    elseif ($deploymentState -like 'ROLLED_BACK_APPLICATION_DATABASE_*') {
        $plannedPriorRollback =
            $plannedTarget.stageEntryTopology.priorRollbackAnchor
        $releaseTargetMatched = (
            $identityMatched -and
            $serviceMatched -and
            (Test-JsonStructuralEquality `
                -Left $plannedUnits -Right $liveUnits) -and
            (Test-JsonStructuralEquality `
                -Left $plannedDropIns -Right $liveDropIns) -and
            $plannedTarget.activeJarPath -ceq
                $liveTarget.activeJarPath -and
            $plannedTarget.activeJarSha256 -ceq
                $liveTarget.activeJarSha256 -and
            $plannedTarget.configuredJarPath -ceq
                $liveTarget.configuredJarPath -and
            $plannedTarget.configuredJarSha256 -ceq
                $liveTarget.configuredJarSha256 -and
            $plannedTarget.processJarPath -ceq
                $liveTarget.processJarPath -and
            $plannedTarget.processJarSha256 -ceq
                $liveTarget.processJarSha256 -and
            $plannedTarget.processArgvSha256 -ceq
                $liveTarget.processArgvSha256 -and
            (Test-JsonStructuralEquality `
                -Left $plannedNginx -Right $liveNginx) -and
            (Test-JsonStructuralEquality `
                -Left $plannedActiveNginx -Right $liveActiveNginx) -and
            $plannedTarget.nginxDumpSha256 -ceq
                $liveTarget.nginxDumpSha256 -and
            $stageEntryMatched -and
            $plannedTarget.stageEntryTopology.state -ceq
                'EXACT_PRIOR_ROLLBACK_PREDECESSOR' -and
            $plannedTarget.currentLifecycleState -ceq
                $liveTarget.currentLifecycleState -and
            $plannedPriorRollback.sourceCommit -ceq
                $snapshot.deploymentChannel.sourceCommit -and
            $snapshot.deploymentChannel.receiptValidated -eq $true -and
            $snapshot.deploymentChannel.rollbackLiveStateMatched -eq
                $true -and
            $snapshot.deploymentChannel.databaseRollbackSafetyProven -eq
                $true -and
            $liveTarget.releaseRootExists -eq $true -and
            $plannedTarget.releaseRootExists -eq $true -and
            (Test-JsonStructuralEquality `
                -Left @($plannedTarget.releaseRootEntryManifest) `
                -Right @($liveTarget.releaseRootEntryManifest)) -and
            $liveTarget.currentLinkExists -eq $false -and
            $null -eq $liveTarget.currentLinkResolved -and
            $liveTarget.productionChanged -eq $true
        )
    }
    elseif (
        $deploymentState -eq
            'INTERRUPTED_APPLY_RECOVERED_APPLICATION_DATABASE_043_RETAINED_DORMANT'
    ) {
        $plannedPriorRecovery =
            $plannedTarget.stageEntryTopology.priorRecoveryAnchor
        $releaseTargetMatched = (
            $identityMatched -and
            $serviceMatched -and
            (Test-JsonStructuralEquality `
                -Left $plannedUnits -Right $liveUnits) -and
            (Test-JsonStructuralEquality `
                -Left $plannedDropIns -Right $liveDropIns) -and
            $plannedTarget.activeJarPath -ceq
                $liveTarget.activeJarPath -and
            $plannedTarget.activeJarSha256 -ceq
                $liveTarget.activeJarSha256 -and
            $plannedTarget.configuredJarPath -ceq
                $liveTarget.configuredJarPath -and
            $plannedTarget.configuredJarSha256 -ceq
                $liveTarget.configuredJarSha256 -and
            $plannedTarget.processJarPath -ceq
                $liveTarget.processJarPath -and
            $plannedTarget.processJarSha256 -ceq
                $liveTarget.processJarSha256 -and
            $plannedTarget.processArgvSha256 -ceq
                $liveTarget.processArgvSha256 -and
            (Test-JsonStructuralEquality `
                -Left $plannedNginx -Right $liveNginx) -and
            (Test-JsonStructuralEquality `
                -Left $plannedActiveNginx -Right $liveActiveNginx) -and
            $plannedTarget.nginxDumpSha256 -ceq
                $liveTarget.nginxDumpSha256 -and
            $stageEntryMatched -and
            $plannedTarget.stageEntryTopology.state -ceq
                'EXACT_PRIOR_INTERRUPTED_APPLY_RECOVERY_PREDECESSOR' -and
            $null -eq
                $plannedTarget.stageEntryTopology.priorRollbackAnchor -and
            $plannedTarget.currentLifecycleState -ceq
                $liveTarget.currentLifecycleState -and
            $plannedPriorRecovery.sourceCommit -ceq
                $snapshot.deploymentChannel.sourceCommit -and
            $snapshot.deploymentChannel.receiptValidated -eq $true -and
            $snapshot.deploymentChannel.recoveryLiveStateMatched -eq
                $true -and
            $snapshot.deploymentChannel.databaseRollbackSafetyProven -eq
                $true -and
            $liveTarget.releaseRootExists -eq $true -and
            $plannedTarget.releaseRootExists -eq $true -and
            (Test-JsonStructuralEquality `
                -Left @($plannedTarget.releaseRootEntryManifest) `
                -Right @($liveTarget.releaseRootEntryManifest)) -and
            $liveTarget.currentLinkExists -eq $false -and
            $null -eq $liveTarget.currentLinkResolved -and
            $liveTarget.productionChanged -eq $true
        )
    }
    else {
        $releaseTargetMatched = (
            $identityMatched -and
            [string]::IsNullOrEmpty($deploymentState) -and
            $serviceMatched -and
            (Test-JsonStructuralEquality `
                -Left $plannedUnits -Right $liveUnits) -and
            (Test-JsonStructuralEquality `
                -Left $plannedDropIns -Right $liveDropIns) -and
            $plannedTarget.activeJarPath -ceq
                $liveTarget.activeJarPath -and
            $plannedTarget.activeJarSha256 -ceq
                $liveTarget.activeJarSha256 -and
            $plannedTarget.configuredJarSha256 -ceq
                $liveTarget.configuredJarSha256 -and
            $plannedTarget.processJarSha256 -ceq
                $liveTarget.processJarSha256 -and
            $plannedTarget.processArgvSha256 -ceq
                $liveTarget.processArgvSha256 -and
            (Test-JsonStructuralEquality `
                -Left $plannedNginx -Right $liveNginx) -and
            (Test-JsonStructuralEquality `
                -Left $plannedActiveNginx -Right $liveActiveNginx) -and
            $plannedTarget.nginxDumpSha256 -ceq
                $liveTarget.nginxDumpSha256 -and
            $stageEntryMatched -and
            $plannedTarget.currentLifecycleState -ceq
                $liveTarget.currentLifecycleState -and
            $plannedTarget.stageEntryTopology.state -ceq
                'UNTOUCHED_LEGACY' -and
            $null -eq
                $plannedTarget.stageEntryTopology.priorRecoveryAnchor -and
            $plannedTarget.releaseRootExists -eq
                $liveTarget.releaseRootExists -and
            $plannedTarget.currentLinkExists -eq
                $liveTarget.currentLinkExists -and
            $liveTarget.productionChanged -eq $false
        )
    }
}
$gitState.releasePlanTargetMatchedLive = $releaseTargetMatched
$snapshot | Add-Member -NotePropertyName local -NotePropertyValue $gitState -Force

$temporarySnapshot = Join-Path $env:TEMP "u3w-production-readiness-$PID.json"
$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
try {
    $snapshot | ConvertTo-Json -Depth 10 |
        Set-Content -LiteralPath $temporarySnapshot -Encoding UTF8
    $node = Resolve-NodeExecutable
    $evaluator = Join-Path $PSScriptRoot 'independent-board-production-readiness.mjs'
    $resultJson = & $node $evaluator --snapshot $temporarySnapshot --output $OutputPath
    if ($LASTEXITCODE -ne 0) {
        throw 'production-readiness evaluator failed'
    }
    $result = ($resultJson -join "`n") | ConvertFrom-Json
    $effectiveStage = if ($RequiredStage) {
        $RequiredStage
    } elseif ($RequireReady) {
        'PREPARED_FOR_STAGE'
    } else {
        $null
    }
    if ($effectiveStage) {
        $stageSatisfied = switch ($effectiveStage) {
            'PREPARED_FOR_STAGE' {
                $result.status -in @(
                    'PREPARED_FOR_STAGE',
                    'STAGED_FOR_SWITCH',
                    'DEPLOYED_DEFAULT_OFF'
                )
            }
            'STAGED_FOR_SWITCH' {
                $result.status -in @('STAGED_FOR_SWITCH', 'DEPLOYED_DEFAULT_OFF')
            }
            'DEPLOYED_DEFAULT_OFF' {
                $result.status -eq 'DEPLOYED_DEFAULT_OFF'
            }
        }
        if (-not $stageSatisfied) {
            $failed = @($result.failedGateIds) + @($result.postDeployFailedGateIds)
            throw "U3W_PRODUCTION_STAGE_NOT_REACHED[$effectiveStage]: $($failed -join ',')"
        }
    }
    $result | ConvertTo-Json -Depth 10
}
finally {
    Remove-Item -LiteralPath $temporarySnapshot -Force -ErrorAction SilentlyContinue
}
