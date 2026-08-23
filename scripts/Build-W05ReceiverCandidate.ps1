[CmdletBinding()]
param(
    [string]$OutputRoot = '',
    [string]$ReleaseId = '',
    [switch]$SkipExpensiveGates
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot 'work\release-builds'
}
$outputRootFull = [IO.Path]::GetFullPath($OutputRoot)
$commit = (& git -C $repoRoot rev-parse HEAD).Trim()
$tree = (& git -C $repoRoot rev-parse 'HEAD^{tree}').Trim()
$branch = [string](& git -C $repoRoot branch --show-current)
$branch = $branch.Trim()
$sourceBranch = 'codex/w05-receiver-contract-20260823'
$dirty = @(& git -C $repoRoot status --porcelain=v1)
if ($dirty.Count -ne 0) {
    throw "Candidate build requires a clean worktree; found $($dirty.Count) entries."
}
if ($branch -and $branch -cne $sourceBranch) {
    throw "Candidate branch is unexpected: $branch"
}
$remoteLine = (& git -C $repoRoot ls-remote --heads origin "refs/heads/$sourceBranch").Trim()
if ($LASTEXITCODE -ne 0 -or -not $remoteLine) {
    throw "Remote candidate branch does not exist: $sourceBranch"
}
$remoteCommit = $remoteLine.Split("`t")[0]
if ($remoteCommit -cne $commit) {
    throw "Remote candidate tip differs from local HEAD: $remoteCommit != $commit"
}
if ([string]::IsNullOrWhiteSpace($ReleaseId)) {
    $ReleaseId = 'w05-receiver-' + $commit.Substring(0, 12) + '-' +
        [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
}
if ($ReleaseId -cnotmatch '^w05-receiver-[0-9a-f]{12}-[0-9]{8}T[0-9]{6}Z$') {
    throw "ReleaseId is invalid: $ReleaseId"
}
$stageRoot = Join-Path $outputRootFull $ReleaseId
$release = Join-Path $stageRoot $ReleaseId
$evidence = Join-Path $release 'evidence'
if (Test-Path -LiteralPath $stageRoot) {
    throw "Candidate stage already exists: $stageRoot"
}
$null = New-Item -ItemType Directory -Path $evidence -Force

function Write-Utf8Json {
    param([string]$Path, $Value)
    [IO.File]::WriteAllText(
        $Path,
        (($Value | ConvertTo-Json -Depth 12) + [Environment]::NewLine),
        [Text.UTF8Encoding]::new($false))
}

function Invoke-Gate {
    param(
        [string]$Name,
        [string]$Executable,
        [string[]]$Arguments,
        [int]$TimeoutSeconds = 1200
    )
    $started = [DateTime]::UtcNow
    $old = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Executable @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $old }
    $text = ($output | ForEach-Object { $_.ToString() }) -join "`n"
    $logPath = Join-Path $evidence ($Name + '.log')
    [IO.File]::WriteAllText(
        $logPath, $text + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false))
    if ($exitCode -ne 0) {
        throw "Gate failed: $Name (exit $exitCode)"
    }
    return [ordered]@{
        name = $Name
        command = $Executable + ' ' + ($Arguments -join ' ')
        startedAt = $started.ToString('o')
        completedAt = [DateTime]::UtcNow.ToString('o')
        exitCode = $exitCode
        log = [IO.Path]::GetRelativePath($release, $logPath).Replace('\', '/')
        logSha256 = (Get-FileHash $logPath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

$gates = [Collections.Generic.List[object]]::new()
try {
    $gates.Add((Invoke-Gate -Name 'worker-contract' -Executable 'python' `
        -Arguments @((Join-Path $repoRoot 'scripts\u3w_w05_receiver_release_test.py'))))
    $gates.Add((Invoke-Gate -Name 'contract-assessment' -Executable 'pwsh' `
        -Arguments @(
            '-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',
            'C:\Users\dhc\.agents\skills\fbs-engineering-orchestrator\scripts\assess_contracts.ps1',
            '--contract',(Join-Path $repoRoot '.fbs-engineering\w05-receiver-contract.json'),'--json')))
    $gates.Add((Invoke-Gate -Name 'database-manifest' -Executable 'pwsh' `
        -Arguments @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',
            (Join-Path $repoRoot 'scripts\verify-database-manifest.ps1'),'-Json')))
    $gates.Add((Invoke-Gate -Name 'backend-all' -Executable 'mvn' `
        -Arguments @('-q','-f',(Join-Path $repoRoot 'pom.xml'),'-pl','FBSir-admin','-am','test')))
    if (-not $SkipExpensiveGates) {
        $dualReceipt = Join-Path $evidence 'identity-registry-dual-mysql.json'
        $gates.Add((Invoke-Gate -Name 'identity-registry-dual-mysql' -Executable 'pwsh' `
            -Arguments @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',
                (Join-Path $repoRoot 'scripts\run-independent-board-attribution-identity-registry-mysql-it.ps1'),
                '-AllowDestructiveTest','-ReceiptPath',$dualReceipt)))
        $gates.Add((Invoke-Gate -Name 'canonical-database-transaction' -Executable 'pwsh' `
            -Arguments @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',
                (Join-Path $repoRoot 'scripts\run-independent-board-mysql-transaction-it.ps1'),
                '-AllowDestructiveTest')))
    }
    $reproReceipt = Join-Path $evidence 'reproducible-build.json'
    $gates.Add((Invoke-Gate -Name 'reproducible-build' -Executable 'pwsh' `
        -Arguments @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',
            (Join-Path $repoRoot 'scripts\verify-w05-reproducible-build.ps1'),
            '-ReceiptPath',$reproReceipt)))

    $dirtyAfterGates = @(& git -C $repoRoot status --porcelain=v1)
    $headAfterGates = (& git -C $repoRoot rev-parse HEAD).Trim()
    if ($dirtyAfterGates.Count -ne 0 -or $headAfterGates -cne $commit) {
        throw 'Source worktree or HEAD changed while candidate gates were running.'
    }

    $jar = Join-Path $repoRoot 'FBSir-admin\target\fbsir-admin.jar'
    $migration043 = Join-Path $repoRoot 'sql\update_20260723_independent_board_attribution_v1.sql'
    $migration044 = Join-Path $repoRoot 'sql\update_20260823_independent_board_attribution_identity_registry.sql'
    foreach ($path in @($jar, $migration043, $migration044)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Candidate input is missing: $path"
        }
    }

    $productionCommit = '13c203a5c42d8a8f977be8a9834c2ad147b62bb8'
    $baseCommit = '5838f8a5eaa3116bcf734511d8f90cd03af2130d'
    $sourceDelta = [Collections.Generic.List[object]]::new()
    foreach ($line in @(& git -C $repoRoot diff --name-status "$productionCommit..$commit")) {
        if (-not $line) { continue }
        $parts = $line -split "`t"
        $status = $parts[0]
        $path = $parts[-1].Replace('\', '/')
        $full = Join-Path $repoRoot $path
        $entry = [ordered]@{ status = $status; path = $path }
        if (Test-Path -LiteralPath $full -PathType Leaf) {
            $entry.sizeBytes = (Get-Item $full).Length
            $entry.sha256 = (Get-FileHash $full -Algorithm SHA256).Hash.ToLowerInvariant()
            $entry.gitBlob = (& git -C $repoRoot hash-object -- $path).Trim()
        }
        $sourceDelta.Add($entry)
    }

    $runtimeFiles = [Collections.Generic.List[string]]::new()
    $runtimeRoots = @(
        'FBSir-business/src/main/java/com/wx/fbsir/business/board/attribution',
        'FBSir-business/src/main/resources/mapper/board/attribution',
        'FBSir-business/src/main/resources/contracts',
        'FBSir-admin/src/main/resources/application.yml'
    )
    foreach ($root in $runtimeRoots) {
        $full = Join-Path $repoRoot $root
        if (Test-Path -LiteralPath $full -PathType Leaf) {
            $runtimeFiles.Add($root)
        }
        elseif (Test-Path -LiteralPath $full -PathType Container) {
            Get-ChildItem -LiteralPath $full -Recurse -File | ForEach-Object {
                $runtimeFiles.Add([IO.Path]::GetRelativePath(
                    $repoRoot, $_.FullName).Replace('\', '/'))
            }
        }
    }
    $runtimeEntries = @($runtimeFiles | Sort-Object -Unique | ForEach-Object {
        $full = Join-Path $repoRoot $_
        [ordered]@{
            path = $_
            sizeBytes = (Get-Item $full).Length
            sha256 = (Get-FileHash $full -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    })
    $runtimeCanonical = ($runtimeEntries | ForEach-Object {
        $_.path + "`0" + $_.sizeBytes + "`0" + $_.sha256
    }) -join "`n"
    $runtimeTreeSha = [Convert]::ToHexString(
        [Security.Cryptography.SHA256]::HashData(
            [Text.UTF8Encoding]::new($false).GetBytes($runtimeCanonical + "`n"))
    ).ToLowerInvariant()

    $sourceManifest = [ordered]@{
        schemaVersion = 'fbsir.w05SourceManifest.v1'
        generatedAt = [DateTime]::UtcNow.ToString('o')
        repository = 'https://github.com/U3W-AI/U3W-AI.git'
        branch = $sourceBranch
        localCheckoutMode = if ($branch) { 'named_branch' } else { 'detached_head' }
        sourceCommit = $commit
        sourceTree = $tree
        remoteCommit = $remoteCommit
        productionCommit = $productionCommit
        baseCommit = $baseCommit
        inheritedCommitCount = [int](& git -C $repoRoot rev-list --count "$productionCommit..$baseCommit")
        totalCommitCountFromProduction = [int](& git -C $repoRoot rev-list --count "$productionCommit..$commit")
        delta = @($sourceDelta)
        runtimeTree = [ordered]@{
            algorithm = 'w05.sorted-path-size-sha256.v1'
            fileCount = $runtimeEntries.Count
            sha256 = $runtimeTreeSha
            files = $runtimeEntries
        }
        worktreeClean = $true
    }
    Write-Utf8Json -Path (Join-Path $evidence 'source-manifest.json') -Value $sourceManifest
    Write-Utf8Json -Path (Join-Path $evidence 'gate-summary.json') -Value ([ordered]@{
        schemaVersion = 'fbsir.w05GateSummary.v1'
        generatedAt = [DateTime]::UtcNow.ToString('o')
        sourceCommit = $commit
        skipExpensiveGates = [bool]$SkipExpensiveGates
        gates = @($gates)
        status = 'PASS'
    })

    $copyMap = [ordered]@{
        'backend/fbsir-admin.jar' = $jar
        'sql/public_init_043.sql' = $migration043
        'sql/public_init_044.sql' = $migration044
        'bin/u3w-w05-receiver-release.py' = (Join-Path $repoRoot 'scripts\u3w-w05-receiver-release.py')
        'bin/u3w-w05-receiver-shadow.py' = (Join-Path $repoRoot 'scripts\u3w-w05-receiver-shadow.py')
    }
    foreach ($relative in $copyMap.Keys) {
        $destination = Join-Path $release $relative
        $null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination)
        Copy-Item -LiteralPath $copyMap[$relative] -Destination $destination
    }
    $shadowSql = Join-Path $release 'shadow\sql'
    $null = New-Item -ItemType Directory -Force -Path $shadowSql
    Copy-Item -Path (Join-Path $repoRoot 'sql\*') `
        -Destination $shadowSql -Recurse -Force

    $files = [ordered]@{}
    Get-ChildItem -LiteralPath $release -Recurse -File | Sort-Object FullName | ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($release, $_.FullName).Replace('\', '/')
        $files[$relative] = [ordered]@{
            sizeBytes = $_.Length
            sha256 = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    }
    $manifest = [ordered]@{
        schemaVersion = 'fbsir.w05ReceiverCandidateManifest.v1'
        generatedAt = [DateTime]::UtcNow.ToString('o')
        releaseId = $ReleaseId
        repository = 'https://github.com/U3W-AI/U3W-AI.git'
        branch = $sourceBranch
        localCheckoutMode = if ($branch) { 'named_branch' } else { 'detached_head' }
        sourceCommit = $commit
        sourceTree = $tree
        remoteCommit = $remoteCommit
        productionCommit = $productionCommit
        baseCommit = $baseCommit
        migration043Sha256 = (Get-FileHash $migration043 -Algorithm SHA256).Hash.ToLowerInvariant()
        migration044Sha256 = (Get-FileHash $migration044 -Algorithm SHA256).Hash.ToLowerInvariant()
        runtimeTreeSha256 = $runtimeTreeSha
        replayContract = [ordered]@{
            allowlistValuesIncluded = $false
            expectedAllowlistCount = 4
            expectedAllowlistSetSha256 = '64ff2c3d8e9ab0e7a279530cfaf464a9862fe76ff01099fd79a2d73733ca98e3'
            maximumAgeHours = 168
            notAfterProvidedBySingleUseAuthorization = $true
            productCreditEligible = $false
        }
        schemaRollback = 'forward_only_retain_044'
        expensiveGatesSkipped = [bool]$SkipExpensiveGates
        files = $files
        status = 'CANDIDATE_NOT_DEPLOYED'
    }
    $manifestPath = Join-Path $release 'w05-candidate-manifest.json'
    Write-Utf8Json -Path $manifestPath -Value $manifest
    $manifestSha = (Get-FileHash $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()

    $archive = Join-Path $stageRoot ($ReleaseId + '.tar.gz')
    & tar.exe -czf $archive -C $stageRoot $ReleaseId
    if ($LASTEXITCODE -ne 0) { throw 'Candidate archive creation failed.' }
    $archiveSha = (Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    $receipt = [ordered]@{
        schemaVersion = 'fbsir.w05CandidateBuildReceipt.v1'
        generatedAt = [DateTime]::UtcNow.ToString('o')
        releaseId = $ReleaseId
        sourceCommit = $commit
        sourceTree = $tree
        remoteCommit = $remoteCommit
        manifestPath = $manifestPath
        manifestSha256 = $manifestSha
        archivePath = $archive
        archiveSha256 = $archiveSha
        archiveSizeBytes = (Get-Item $archive).Length
        jarSha256 = $files['backend/fbsir-admin.jar'].sha256
        migration044Sha256 = $manifest.migration044Sha256
        runtimeTreeSha256 = $runtimeTreeSha
        gateCount = $gates.Count
        worktreeClean = $true
        productionChanged = $false
        status = 'PASS'
    }
    $receiptPath = Join-Path $stageRoot 'candidate-build-receipt.json'
    Write-Utf8Json -Path $receiptPath -Value $receipt
    Write-Output ($receipt | ConvertTo-Json -Depth 8)
}
catch {
    $failure = [ordered]@{
        schemaVersion = 'fbsir.w05CandidateBuildFailure.v1'
        failedAt = [DateTime]::UtcNow.ToString('o')
        releaseId = $ReleaseId
        sourceCommit = $commit
        error = $_.Exception.Message
        productionChanged = $false
        status = 'FAIL'
    }
    Write-Utf8Json -Path (Join-Path $stageRoot 'candidate-build-failure.json') -Value $failure
    throw
}
