[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$DestinationRoot,

    [string]$RepositoryName = 'u3w-independent-board-control-plane'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$packageRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$manifestPath = Join-Path $packageRoot 'handoff\package-manifest.json'
$checksumsPath = Join-Path $packageRoot 'handoff\SHA256SUMS.tsv'
$bundlePath = Join-Path $packageRoot 'source\repository.bundle'

foreach ($required in @($manifestPath, $checksumsPath, $bundlePath)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Migration package is missing required file: $required"
    }
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$checksumLines = Get-Content -LiteralPath $checksumsPath -Encoding UTF8 |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
foreach ($line in $checksumLines) {
    $parts = $line -split "`t", 2
    if ($parts.Count -ne 2 -or $parts[0] -notmatch '^[0-9a-f]{64}$') {
        throw "Invalid checksum row: $line"
    }
    $relative = $parts[1].Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $path = [System.IO.Path]::GetFullPath((Join-Path $packageRoot $relative))
    if (-not $path.StartsWith($packageRoot.TrimEnd('\') + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Checksum path escapes package root: $relative"
    }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Checksummed file is missing: $relative"
    }
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $parts[0]) {
        throw "Checksum mismatch: $relative"
    }
}

& git bundle verify $bundlePath | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Git bundle verification failed with exit code $LASTEXITCODE"
}

$destination = [System.IO.Path]::GetFullPath($DestinationRoot)
if ($destination -eq [System.IO.Path]::GetPathRoot($destination)) {
    throw 'DestinationRoot may not be a filesystem root.'
}
if (-not (Test-Path -LiteralPath $destination)) {
    New-Item -ItemType Directory -Path $destination | Out-Null
}
$repoPath = [System.IO.Path]::GetFullPath((Join-Path $destination $RepositoryName))
if (-not $repoPath.StartsWith($destination.TrimEnd('\') + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Repository destination escapes DestinationRoot.'
}
if (Test-Path -LiteralPath $repoPath) {
    throw "Repository destination already exists; refusing to overwrite: $repoPath"
}

& git -c core.longpaths=true clone --branch $manifest.git.branch --single-branch $bundlePath $repoPath
if ($LASTEXITCODE -ne 0) {
    throw "Git clone failed with exit code $LASTEXITCODE"
}
& git -C $repoPath config core.longpaths true
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to persist core.longpaths for the restored Windows checkout.'
}
& git -C $repoPath remote set-url origin $manifest.git.remote
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to restore the GitHub origin URL.'
}
& git -C $repoPath fsck --full
if ($LASTEXITCODE -ne 0) {
    throw 'Restored repository failed git fsck --full.'
}

$actualCommit = (& git -C $repoPath rev-parse HEAD).Trim()
$actualBranch = (& git -C $repoPath branch --show-current).Trim()
$actualRemote = (& git -C $repoPath remote get-url origin).Trim()
$dirty = @(& git -C $repoPath status --porcelain=v1)
if ($actualCommit -ne $manifest.git.commit) {
    throw "Restored commit mismatch: $actualCommit"
}
if ($actualBranch -ne $manifest.git.branch) {
    throw "Restored branch mismatch: $actualBranch"
}
if ($actualRemote -ne $manifest.git.remote) {
    throw "Restored origin mismatch: $actualRemote"
}
if ($dirty.Count -ne 0) {
    throw 'Restored repository is not clean.'
}
foreach ($relative in $manifest.requiredRepositoryPaths) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoPath $relative))) {
        throw "Restored repository is missing required path: $relative"
    }
}

[pscustomobject]@{
    schema = 'fbsir.codex-migration-restore/v1'
    result = 'PASS'
    repository = $repoPath
    branch = $actualBranch
    commit = $actualCommit
    origin = $actualRemote
    clean = $true
    next = 'Open the repository in Codex and paste START-CODEX-PROMPT.md.'
    hostTruthBoundary = 'Source-machine host reports are historical snapshots; target-machine current-read is unverified.'
} | ConvertTo-Json -Depth 5
