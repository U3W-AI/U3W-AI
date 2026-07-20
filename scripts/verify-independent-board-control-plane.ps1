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
        'docs\independent-board\AUTHORITATIVE-ROOT.md',
        'docs\independent-board\PORTAL-PROTOTYPE-SPEC.md',
        'docs\independent-board\prototypes\portals\index.html',
        'sql\update_20260720_independent_board_control_plane.sql',
        'scripts\verify-database-manifest.ps1',
        'scripts\verify-independent-board-live-database.ps1',
        'scripts\verify-independent-board-mysql-concurrency.ps1'
    )
    foreach ($path in $required) {
        Assert-PathExists -RelativePath $path
        if ([System.IO.Path]::GetExtension($path) -eq '.json') {
            $null = Get-Content -LiteralPath (Join-Path $RepoRoot $path) -Raw -Encoding UTF8 | ConvertFrom-Json
        }
    }

    $prototype = Get-Content -LiteralPath (Join-Path $RepoRoot 'docs\independent-board\prototypes\portals\index.html') -Raw -Encoding UTF8
    # Keep PowerShell source ASCII-safe for Windows PowerShell 5.1. The UTF-8
    # prototype itself is checked through stable ASCII structural markers.
    foreach ($marker in @('FBSir', 'U3W-AI', 'lang="zh-CN"', 'data-mode="me"', 'data-mode="admin"', 'prototype-banner')) {
        if (-not $prototype.Contains($marker)) {
            throw "Portal prototype is missing required marker: $marker"
        }
    }

    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot 'scripts\verify-database-manifest.ps1')
    if ($LASTEXITCODE -ne 0) { throw "Database manifest verification failed with exit code $LASTEXITCODE" }
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
    databaseEvidence = 'manifest_and_dry_run_static_only_in_this_command'
    liveDatabaseVerified = $false
    releaseReady = $false
    w1EvidenceNextAction = 'RUN_DATABASE_LIVE_AND_DATABASE_CONCURRENCY_ON_AN_ISOLATED_SCRATCH_DATABASE'
    scratchEvidenceCommands = @('database-live', 'database-concurrency')
} | ConvertTo-Json -Depth 4 -Compress
