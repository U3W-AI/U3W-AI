[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$OutputRoot,

    [string]$OrchestratorSkillPath = '',

    [switch]$SkipSkillSnapshot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$outputRootFull = [System.IO.Path]::GetFullPath($OutputRoot)
if ($outputRootFull -eq [System.IO.Path]::GetPathRoot($outputRootFull)) {
    throw 'OutputRoot may not be a filesystem root.'
}
$repoPrefix = $repoRoot.TrimEnd('\') + '\'
if ($outputRootFull.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputRoot must be outside the repository to prevent recursive packaging.'
}

$dirty = @(& git -C $repoRoot status --porcelain=v1)
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to read Git status.'
}
if ($dirty.Count -ne 0) {
    throw 'Repository must be clean before packaging. Commit or explicitly preserve every change first.'
}

$branch = (& git -C $repoRoot branch --show-current).Trim()
$commit = (& git -C $repoRoot rev-parse HEAD).Trim()
$shortCommit = (& git -C $repoRoot rev-parse --short=8 HEAD).Trim()
$remote = (& git -C $repoRoot remote get-url origin).Trim()
if ([string]::IsNullOrWhiteSpace($branch) -or [string]::IsNullOrWhiteSpace($commit)) {
    throw 'A named branch and commit are required.'
}

$packageName = "DDH-Codex-Handoff-20260721-$shortCommit"
$packageRoot = Join-Path $outputRootFull $packageName
$zipPath = $packageRoot + '.zip'
if ((Test-Path -LiteralPath $packageRoot) -or (Test-Path -LiteralPath $zipPath)) {
    throw "Package output already exists; refusing to overwrite: $packageRoot"
}

New-Item -ItemType Directory -Path (Join-Path $packageRoot 'source') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $packageRoot 'handoff') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $packageRoot 'tools') -Force | Out-Null

$bundlePath = Join-Path $packageRoot 'source\repository.bundle'
& git -C $repoRoot bundle create $bundlePath --branches --tags
if ($LASTEXITCODE -ne 0) {
    throw "Git bundle creation failed with exit code $LASTEXITCODE"
}
& git bundle verify $bundlePath | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Git bundle verification failed with exit code $LASTEXITCODE"
}

Copy-Item -LiteralPath (Join-Path $repoRoot 'docs\migration\README-FIRST.md') -Destination (Join-Path $packageRoot 'README-FIRST.md')
Copy-Item -LiteralPath (Join-Path $repoRoot 'docs\migration\START-CODEX-PROMPT.md') -Destination (Join-Path $packageRoot 'START-CODEX-PROMPT.md')
Copy-Item -LiteralPath (Join-Path $repoRoot 'docs\migration\TARGET-MACHINE-CHECKLIST.md') -Destination (Join-Path $packageRoot 'handoff\TARGET-MACHINE-CHECKLIST.md')
Copy-Item -LiteralPath (Join-Path $repoRoot 'scripts\restore-codex-migration-package.ps1') -Destination (Join-Path $packageRoot 'tools\restore-and-verify.ps1')

$skillIncluded = $false
if (-not $SkipSkillSnapshot) {
    if ([string]::IsNullOrWhiteSpace($OrchestratorSkillPath)) {
        if ([string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
            throw 'USERPROFILE is unavailable; pass -OrchestratorSkillPath or use -SkipSkillSnapshot.'
        }
        $OrchestratorSkillPath = Join-Path $env:USERPROFILE '.codex\skills\fbs-engineering-orchestrator'
    }
    $skillSource = [System.IO.Path]::GetFullPath($OrchestratorSkillPath)
    if (-not (Test-Path -LiteralPath (Join-Path $skillSource 'SKILL.md') -PathType Leaf)) {
        throw "Orchestrator skill snapshot is unavailable: $skillSource"
    }
    $skillDestination = Join-Path $packageRoot 'codex\skills\fbs-engineering-orchestrator'
    New-Item -ItemType Directory -Path $skillDestination -Force | Out-Null
    Get-ChildItem -LiteralPath $skillSource -Force | Where-Object {
        $_.Name -notin @('__pycache__', '.pytest_cache') -and $_.Extension -ne '.pyc'
    } | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $skillDestination -Recurse -Force
    }
    $skillIncluded = $true
}

$environment = [ordered]@{
    schema = 'fbsir.codex-migration-environment/v1'
    sourceMachine = [ordered]@{
        os = (Get-CimInstance Win32_OperatingSystem).Caption
        architecture = $env:PROCESSOR_ARCHITECTURE
        powershell = $PSVersionTable.PSVersion.ToString()
        git = ((& git --version) -replace '^git version\s+', '').Trim()
        java = '17.0.17'
        maven = '3.9.9'
        node = '24.16.0'
        npm = '11.13.0'
    }
    targetMinimum = [ordered]@{
        java = '17'
        maven = '3.8+'
        node = '18+'
        npm = '9+'
        mysqlExactVerified = @('8.0.30', '8.4.8')
        redis = '6+ for full login/session flow'
    }
    machineLocalStateNotIncluded = @(
        'credentials and tokens',
        'MySQL login-path and data directories',
        'Redis data',
        'node_modules and Maven target directories',
        'Codex memory and sessions',
        'WorkBuddy and WorkBuddyAI user directories',
        'browser profiles and authentication state'
    )
}
$environment | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $packageRoot 'handoff\environment.json') -Encoding UTF8

$brandZh = -join @([char]0x798F, [char]0x5E2E, [char]0x624B)
$productName = -join @([char]0x72EC, [char]0x8463, [char]0x4F1A)
$manifest = [ordered]@{
    schema = 'fbsir.codex-migration-package/v1'
    product = [ordered]@{
        brandZh = $brandZh
        brandEn = 'FBSir'
        name = $productName
        listedVersion = '26.7.20'
    }
    generatedAt = (Get-Date).ToString('o')
    git = [ordered]@{
        branch = $branch
        commit = $commit
        remote = $remote
        bundle = 'source/repository.bundle'
        bundleSha256 = (Get-FileHash -LiteralPath $bundlePath -Algorithm SHA256).Hash.ToLowerInvariant()
        completeHistory = $true
        workingTree = 'clean'
    }
    frozenListedSurface = [ordered]@{
        state = 'officially_listed_user_confirmed'
        artifactName = 'fbsir-eight-seat-board_26.7.20_R.zip'
        artifactSha256 = '0cf445eef323e91e4af8f13788e0dc3c920eb71c859ec1c10808f86e8b394b8f'
        artifactIncluded = $false
        sourceDependency = $false
        writePolicy = 'frozen_post_listing_observation_no_writeback'
    }
    included = [ordered]@{
        repositoryBundle = $true
        orchestratorSkillSnapshot = $skillIncluded
        trackedMysqlVerificationSummaries = $true
        restoreAndVerifyTool = $true
    }
    excludedAsRedundantOrMachineLocal = @(
        'node_modules', 'target', 'dist', 'untracked work directories',
        'MySQL toolchains and data', 'Maven and npm caches', 'credentials',
        'Codex sessions and memory', 'WorkBuddy user directories', 'browser profiles'
    )
    sourceMachineTruthBoundary = 'Host truth reports remain source-machine snapshots after migration and do not prove target-machine current state.'
    currentDevelopmentBoundary = 'W4b.1 internal OAuth refresh security is locally verified; public OAuth/MCP routes remain closed.'
    nextSlice = 'W4b.2 default-off me/admin OAuth and Connector candidate on the existing RuoYi shell.'
    requiredRepositoryPaths = @(
        '.fbs-engineering/contract.json',
        'docs/independent-board/AUTHORITATIVE-ROOT.md',
        'docs/independent-board/implementation-status.json',
        'docs/independent-board/taskboard.json',
        'docs/independent-board/W4B-OAUTH-MCP-AUTHORIZATION-CONTRACT.md',
        'reports/independent-board/w4b-oauth-refresh-security-verification-20260721.json',
        'scripts/verify-independent-board-control-plane.ps1',
        'scripts/run-independent-board-mysql-transaction-it.ps1',
        'sql/update_20260721_independent_board_oauth_refresh_security.sql',
        'FBSir-ui/package-lock.json',
        'pom.xml'
    )
}
$manifestPath = Join-Path $packageRoot 'handoff\package-manifest.json'
$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

$checksumsPath = Join-Path $packageRoot 'handoff\SHA256SUMS.tsv'
$checksumRows = Get-ChildItem -LiteralPath $packageRoot -Recurse -File -Force |
    Where-Object { $_.FullName -ne $checksumsPath } |
    Sort-Object FullName |
    ForEach-Object {
        $relative = $_.FullName.Substring($packageRoot.Length + 1).Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash`t$relative"
    }
$checksumRows | Set-Content -LiteralPath $checksumsPath -Encoding UTF8

Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory(
    $packageRoot,
    $zipPath,
    [System.IO.Compression.CompressionLevel]::Optimal,
    $false)
$zipHash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
$zipHash | Set-Content -LiteralPath ($zipPath + '.sha256') -Encoding ASCII

[pscustomobject]@{
    schema = 'fbsir.codex-migration-build/v1'
    result = 'PASS'
    packageRoot = $packageRoot
    zip = $zipPath
    zipBytes = (Get-Item -LiteralPath $zipPath).Length
    zipSha256 = $zipHash
    branch = $branch
    commit = $commit
    skillSnapshotIncluded = $skillIncluded
} | ConvertTo-Json -Depth 5
