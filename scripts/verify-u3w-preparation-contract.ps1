[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Resolve-Executable {
    param(
        [Parameter(Mandatory = $true)][string]$EnvironmentVariable,
        [Parameter(Mandatory = $true)][string]$CommandName
    )
    $configured = [Environment]::GetEnvironmentVariable(
        $EnvironmentVariable)
    if ($configured) {
        if (-not (Test-Path -LiteralPath $configured -PathType Leaf)) {
            throw "$EnvironmentVariable does not name a file"
        }
        return (Resolve-Path -LiteralPath $configured).Path
    }
    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "$CommandName is unavailable; set $EnvironmentVariable"
    }
    return $command.Source
}

function Assert-PowerShellSyntax {
    param([Parameter(Mandatory = $true)][string[]]$Paths)
    $failures = @()
    foreach ($relativePath in $Paths) {
        $tokens = $null
        $errors = $null
        [Management.Automation.Language.Parser]::ParseFile(
            (Join-Path $RepoRoot $relativePath),
            [ref]$tokens,
            [ref]$errors
        ) | Out-Null
        foreach ($error in $errors) {
            $failures += '{0}:{1}:{2}: {3}' -f
                $relativePath,
                $error.Extent.StartLineNumber,
                $error.Extent.StartColumnNumber,
                $error.Message
        }
    }
    if ($failures.Count -gt 0) {
        throw ($failures -join [Environment]::NewLine)
    }
}

$node = Resolve-Executable -EnvironmentVariable 'U3W_NODE_EXE' `
    -CommandName 'node.exe'
$python = Resolve-Executable -EnvironmentVariable 'U3W_PYTHON_EXE' `
    -CommandName 'python.exe'

Push-Location $RepoRoot
try {
    & $node --test `
        scripts/independent-board-production-readiness.test.mjs `
        scripts/u3w-backup-restore-contract.test.mjs `
        scripts/u3w-interrupted-apply-recovery-runner-contract.test.mjs `
        scripts/u3w-release-runner-contract.test.mjs
    if ($LASTEXITCODE -ne 0) {
        throw 'W1A preparation Node contract tests failed'
    }

    & $python -m unittest `
        scripts/u3w_backup_restore_remote_test.py `
        scripts/u3w_preparation_remote_test.py `
        scripts/u3w_default_off_release_remote_test.py
    if ($LASTEXITCODE -ne 0) {
        throw 'W1A preparation Python contract tests failed'
    }

    Assert-PowerShellSyntax -Paths @(
        'scripts/deploy-independent-board-default-off.ps1',
        'scripts/reconcile-u3w-interrupted-apply.ps1',
        'scripts/run-u3w-admin-root-dependency.ps1',
        'scripts/run-u3w-default-off-configuration.ps1',
        'scripts/run-u3w-legacy-baseline-control-shape-mysql-it.ps1',
        'scripts/run-u3w-legacy-baseline.ps1',
        'scripts/run-u3w-production-backup-restore.ps1',
        'scripts/verify-independent-board-production-readiness.ps1',
        'scripts/verify-u3w-preparation-contract.ps1'
    )
}
finally {
    Pop-Location
}

[ordered]@{
    schema = 'fbsir.u3wPreparationContractVerification.v1'
    status = 'PASS'
    productionChanged = $false
    tests = [ordered]@{
        nodeFiles = 4
        pythonFiles = 3
        powershellFiles = 9
    }
    observedAt = [DateTime]::UtcNow.ToString('o')
} | ConvertTo-Json -Depth 5
