param(
    [ValidateSet('Contract', 'Backend', 'Frontend', 'All')]
    [string]$Mode = 'All'
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Assert-PathExists {
    param([string]$RelativePath)
    $Path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required path is missing: $RelativePath"
    }
}

function Invoke-ContractChecks {
    $required = @(
        '.fbs-engineering\contract.json',
        'config\deployment\u3w-domain-inventory.json',
        'docs\independent-board\implementation-status.json',
        'docs\independent-board\taskboard.json',
        'docs\independent-board\AUTHORITATIVE-ROOT.md'
    )
    foreach ($path in $required) {
        Assert-PathExists -RelativePath $path
        if ([System.IO.Path]::GetExtension($path) -eq '.json') {
            $null = Get-Content -LiteralPath (Join-Path $RepoRoot $path) -Raw -Encoding UTF8 | ConvertFrom-Json
        }
    }
}

function Invoke-BackendChecks {
    Push-Location $RepoRoot
    try {
        & mvn.cmd -q -pl FBSir-business -am test
        if ($LASTEXITCODE -ne 0) { throw "Backend verification failed with exit code $LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }
}

function Invoke-FrontendChecks {
    $UiRoot = Join-Path $RepoRoot 'FBSir-ui'
    Push-Location $UiRoot
    try {
        if (-not (Test-Path -LiteralPath (Join-Path $UiRoot 'node_modules'))) {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE" }
        }
        & npm.cmd run verify:menu-components
        if ($LASTEXITCODE -ne 0) { throw "Menu verification failed with exit code $LASTEXITCODE" }
        & npm.cmd run build:prod
        if ($LASTEXITCODE -ne 0) { throw "Frontend build failed with exit code $LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }
}

Invoke-ContractChecks

if ($Mode -in @('Backend', 'All')) {
    Invoke-BackendChecks
}

if ($Mode -in @('Frontend', 'All')) {
    Invoke-FrontendChecks
}

[pscustomobject]@{
    schema = 'fbsir.independent-board-verification/v1'
    ok = $true
    mode = $Mode
    repoRoot = $RepoRoot
    observedAt = [DateTimeOffset]::Now.ToString('o')
} | ConvertTo-Json -Depth 4 -Compress
