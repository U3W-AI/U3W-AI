[CmdletBinding()]
param(
    [string]$ReceiptPath,
    [string]$ExpectedCommit,
    [string]$FrozenPackagePath = 'D:\Spg719\fbsir-eight-seat-board-26.7.20.zip',
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ReceiptSchema = 'fbsir.independent-board.credit-candidate-release-readiness/v1'
$FrozenSha256 = 'd2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd'
$ExpectedProfiles = @('MySQL Community 8.0.30', 'MySQL Community 8.4.8')
$CanonicalDomainInventoryPath = Join-Path $RepoRoot 'config\deployment\u3w-domain-inventory.json'

function Fail-Readiness {
    param([string]$Message)
    throw "CREDIT_CANDIDATE_RELEASE_NOT_READY: $Message"
}

function Assert-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        Fail-Readiness $Message
    }
}

function Assert-ExactProperties {
    param(
        [object]$Value,
        [string[]]$Expected,
        [string]$Source
    )
    Assert-Condition ($null -ne $Value) "$Source is required"
    $actual = @($Value.PSObject.Properties.Name)
    $missing = @($Expected | Where-Object { $_ -notin $actual })
    $unknown = @($actual | Where-Object { $_ -notin $Expected })
    Assert-Condition ($missing.Count -eq 0) "$Source has missing fields: $($missing -join ',')"
    Assert-Condition ($unknown.Count -eq 0) "$Source has unknown fields: $($unknown -join ',')"
}

function Assert-Match {
    param([string]$Value, [string]$Pattern, [string]$Source)
    Assert-Condition (-not [string]::IsNullOrWhiteSpace($Value) -and $Value -cmatch $Pattern) "$Source has an invalid format"
}

function Skip-JsonWhitespace {
    param([string]$Json, [ref]$Index)
    while ($Index.Value -lt $Json.Length -and [char]::IsWhiteSpace($Json[$Index.Value])) {
        $Index.Value++
    }
}

function Read-StrictJsonString {
    param([string]$Json, [ref]$Index, [string]$Source)
    Assert-Condition ($Index.Value -lt $Json.Length -and $Json[$Index.Value] -eq [char]34) "$Source is not valid JSON"
    $Index.Value++
    $builder = New-Object System.Text.StringBuilder
    while ($Index.Value -lt $Json.Length) {
        $current = $Json[$Index.Value]
        $Index.Value++
        if ($current -eq [char]34) {
            return $builder.ToString()
        }
        if ([int][char]$current -lt 32) {
            Fail-Readiness "$Source is not valid JSON"
        }
        if ($current -ne [char]92) {
            [void]$builder.Append($current)
            continue
        }
        Assert-Condition ($Index.Value -lt $Json.Length) "$Source is not valid JSON"
        $escape = $Json[$Index.Value]
        $Index.Value++
        switch ($escape) {
            '"' { [void]$builder.Append([char]34); break }
            '\' { [void]$builder.Append([char]92); break }
            '/' { [void]$builder.Append([char]47); break }
            'b' { [void]$builder.Append([char]8); break }
            'f' { [void]$builder.Append([char]12); break }
            'n' { [void]$builder.Append([char]10); break }
            'r' { [void]$builder.Append([char]13); break }
            't' { [void]$builder.Append([char]9); break }
            'u' {
                Assert-Condition ($Index.Value + 4 -le $Json.Length) "$Source is not valid JSON"
                $hex = $Json.Substring($Index.Value, 4)
                Assert-Condition ($hex -cmatch '^[0-9a-fA-F]{4}$') "$Source is not valid JSON"
                [void]$builder.Append([char][Convert]::ToInt32($hex, 16))
                $Index.Value += 4
                break
            }
            default { Fail-Readiness "$Source is not valid JSON" }
        }
    }
    Fail-Readiness "$Source is not valid JSON"
}

function Read-StrictJsonValue {
    param([string]$Json, [ref]$Index, [string]$Source, [int]$Depth)
    Assert-Condition ($Depth -le 64) "$Source nesting is too deep"
    Skip-JsonWhitespace -Json $Json -Index $Index
    Assert-Condition ($Index.Value -lt $Json.Length) "$Source is not valid JSON"
    $token = $Json[$Index.Value]
    if ($token -eq [char]123) {
        $Index.Value++
        Skip-JsonWhitespace -Json $Json -Index $Index
        $keys = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
        if ($Index.Value -lt $Json.Length -and $Json[$Index.Value] -eq [char]125) {
            $Index.Value++
            return
        }
        while ($true) {
            Skip-JsonWhitespace -Json $Json -Index $Index
            $name = Read-StrictJsonString -Json $Json -Index $Index -Source $Source
            Assert-Condition ($keys.Add($name)) "$Source contains duplicate object keys"
            Skip-JsonWhitespace -Json $Json -Index $Index
            Assert-Condition ($Index.Value -lt $Json.Length -and $Json[$Index.Value] -eq [char]58) "$Source is not valid JSON"
            $Index.Value++
            Read-StrictJsonValue -Json $Json -Index $Index -Source $Source -Depth ($Depth + 1)
            Skip-JsonWhitespace -Json $Json -Index $Index
            Assert-Condition ($Index.Value -lt $Json.Length) "$Source is not valid JSON"
            if ($Json[$Index.Value] -eq [char]125) {
                $Index.Value++
                return
            }
            Assert-Condition ($Json[$Index.Value] -eq [char]44) "$Source is not valid JSON"
            $Index.Value++
        }
    }
    if ($token -eq [char]91) {
        $Index.Value++
        Skip-JsonWhitespace -Json $Json -Index $Index
        if ($Index.Value -lt $Json.Length -and $Json[$Index.Value] -eq [char]93) {
            $Index.Value++
            return
        }
        while ($true) {
            Read-StrictJsonValue -Json $Json -Index $Index -Source $Source -Depth ($Depth + 1)
            Skip-JsonWhitespace -Json $Json -Index $Index
            Assert-Condition ($Index.Value -lt $Json.Length) "$Source is not valid JSON"
            if ($Json[$Index.Value] -eq [char]93) {
                $Index.Value++
                return
            }
            Assert-Condition ($Json[$Index.Value] -eq [char]44) "$Source is not valid JSON"
            $Index.Value++
        }
    }
    if ($token -eq [char]34) {
        [void](Read-StrictJsonString -Json $Json -Index $Index -Source $Source)
        return
    }
    foreach ($literal in @('true', 'false', 'null')) {
        if ($Json.Substring($Index.Value).StartsWith($literal, [System.StringComparison]::Ordinal)) {
            $Index.Value += $literal.Length
            return
        }
    }
    $number = [regex]::Match($Json.Substring($Index.Value), '^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?')
    Assert-Condition $number.Success "$Source is not valid JSON"
    $Index.Value += $number.Length
}

function Assert-UniqueJsonObjectKeys {
    param([string]$Json, [string]$Source)
    $position = 0
    Read-StrictJsonValue -Json $Json -Index ([ref]$position) -Source $Source -Depth 0
    Skip-JsonWhitespace -Json $Json -Index ([ref]$position)
    Assert-Condition ($position -eq $Json.Length) "$Source is not valid JSON"
}

function Read-StrictJson {
    param([string]$Path, [string]$Source)
    Assert-Condition (Test-Path -LiteralPath $Path -PathType Leaf) "$Source is missing: $Path"
    $json = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    Assert-UniqueJsonObjectKeys -Json $json -Source $Source
    try {
        return $json | ConvertFrom-Json -ErrorAction Stop
    }
    catch {
        Fail-Readiness "$Source is not valid JSON"
    }
}

function Assert-CleanGitWorktree {
    $status = & git -C $RepoRoot status --porcelain=v1
    Assert-Condition ($LASTEXITCODE -eq 0) 'git status failed'
    Assert-Condition ([string]::IsNullOrWhiteSpace(($status -join "`n"))) 'working tree is not clean'
}

function Assert-ExpectedCommitIsCheckedOut {
    param([string]$ExpectedSourceCommit)
    & git -C $RepoRoot cat-file -e ($ExpectedSourceCommit + '^{commit}') 2>$null
    Assert-Condition ($LASTEXITCODE -eq 0) 'ExpectedCommit is not a locally resolvable commit'
    $headOutput = & git -C $RepoRoot rev-parse HEAD
    $headExitCode = $LASTEXITCODE
    Assert-Condition ($headExitCode -eq 0) 'git rev-parse HEAD failed'
    $head = ([string]($headOutput | Select-Object -First 1)).Trim().ToLowerInvariant()
    Assert-Condition ($head -ceq $ExpectedSourceCommit) 'ExpectedCommit is not the currently checked out commit'
}

function Assert-CanonicalDomainInventory {
    param([object]$Inventory)
    Assert-ExactProperties -Value $Inventory -Expected @(
        'schema', 'authoritativeSource', 'alignmentKey', 'inventoryState', 'observedAt', 'discoveryCompleteness', 'domains', 'rules'
    ) -Source 'canonical domain inventory'
    Assert-Condition ($Inventory.schema -ceq 'fbsir.u3w-domain-inventory/v1') 'canonical domain inventory schema is unsupported'
    Assert-Condition ($Inventory.authoritativeSource -ceq 'this_repository') 'canonical domain inventory source is not this repository'
    Assert-Condition ($Inventory.alignmentKey -ceq 'gitCommit') 'canonical domain inventory alignment key is unsupported'
    Assert-Condition ($Inventory.domains -is [System.Collections.IEnumerable]) 'canonical domain inventory domains are invalid'
}

function Assert-InventoryTarget {
    param([object]$Inventory, [string]$TargetHost, [string]$Role)
    $inventoryMatches = @($Inventory.domains | Where-Object { $_.host -ceq $TargetHost -and $_.role -ceq $Role })
    Assert-Condition ($inventoryMatches.Count -eq 1) "target host is not an inventoried $Role surface: $TargetHost"
}

function Assert-PassMatrix {
    param([object]$Value, [string[]]$Expected, [string]$Source)
    Assert-ExactProperties -Value $Value -Expected $Expected -Source $Source
    foreach ($name in $Expected) {
        Assert-Condition ($Value.$name -ceq 'PASS') "$Source.$name is not PASS"
    }
}

function Test-ReleaseReceipt {
    param(
        [object]$Receipt,
        [string]$ExpectedSourceCommit,
        [object]$Inventory,
        [switch]$SkipWorktreeCheck
    )

    Assert-ExactProperties -Value $Receipt -Expected @(
        'schema', 'phase', 'sourceCommit', 'frozenPackage', 'target', 'database', 'access', 'flags', 'readback', 'rollback'
    ) -Source 'receipt'
    Assert-Condition ($Receipt.schema -ceq $ReceiptSchema) 'receipt schema is unsupported'
    Assert-Condition ($Receipt.phase -ceq 'CANDIDATE_ACTIVATION') 'receipt phase must be CANDIDATE_ACTIVATION'
    Assert-Match -Value $Receipt.sourceCommit -Pattern '^[0-9a-f]{40}$' -Source 'receipt.sourceCommit'
    Assert-Condition ($Receipt.sourceCommit -ceq $ExpectedSourceCommit) 'receipt sourceCommit does not match ExpectedCommit'

    Assert-ExactProperties -Value $Receipt.frozenPackage -Expected @('sha256') -Source 'receipt.frozenPackage'
    Assert-Match -Value $Receipt.frozenPackage.sha256 -Pattern '^[0-9a-f]{64}$' -Source 'receipt.frozenPackage.sha256'
    Assert-Condition ($Receipt.frozenPackage.sha256 -ceq $FrozenSha256) 'receipt frozen package hash is not the approved frozen artifact'
    Assert-Condition ((Get-FileHash -Algorithm SHA256 -LiteralPath $FrozenPackagePath).Hash.ToLowerInvariant() -ceq $FrozenSha256) 'local frozen package hash drifted'

    Assert-ExactProperties -Value $Receipt.target -Expected @('backendHost', 'adminHost', 'backendBuildSha256', 'frontendBuildSha256', 'domainInventorySha256') -Source 'receipt.target'
    Assert-Condition ($Receipt.target.backendHost -ceq 'api2.u3w.com') 'receipt backendHost is not api2.u3w.com'
    Assert-Condition ($Receipt.target.adminHost -ceq 'admin.u3w.com') 'receipt adminHost is not admin.u3w.com'
    Assert-Match -Value $Receipt.target.backendBuildSha256 -Pattern '^[0-9a-f]{64}$' -Source 'receipt.target.backendBuildSha256'
    Assert-Match -Value $Receipt.target.frontendBuildSha256 -Pattern '^[0-9a-f]{64}$' -Source 'receipt.target.frontendBuildSha256'
    Assert-Match -Value $Receipt.target.domainInventorySha256 -Pattern '^[0-9a-f]{64}$' -Source 'receipt.target.domainInventorySha256'
    Assert-Condition ($Receipt.target.domainInventorySha256 -ceq (Get-FileHash -Algorithm SHA256 -LiteralPath $CanonicalDomainInventoryPath).Hash.ToLowerInvariant()) 'receipt domain inventory hash does not match the canonical checked-out inventory'
    Assert-InventoryTarget -Inventory $Inventory -TargetHost $Receipt.target.backendHost -Role 'runtime_and_mcp_api'
    Assert-InventoryTarget -Inventory $Inventory -TargetHost $Receipt.target.adminHost -Role 'system_administration_portal'

    Assert-ExactProperties -Value $Receipt.database -Expected @('migrationId', 'profiles', 'firstApply', 'replay', 'currentRead', 'sysUserPointsProjection') -Source 'receipt.database'
    Assert-Condition ($Receipt.database.migrationId -ceq 'public_init_038') 'receipt database migrationId must be public_init_038'
    $profiles = @($Receipt.database.profiles)
    Assert-Condition ($profiles.Count -eq $ExpectedProfiles.Count -and @($profiles | Sort-Object -Unique).Count -eq $ExpectedProfiles.Count -and @($ExpectedProfiles | Where-Object { $_ -notin $profiles }).Count -eq 0) 'receipt database profiles are not the two approved MySQL builds'
    foreach ($field in @('firstApply', 'replay', 'currentRead')) {
        Assert-Condition ($Receipt.database.$field -ceq 'PASS') "receipt.database.$field is not PASS"
    }
    Assert-Condition ($Receipt.database.sysUserPointsProjection -ceq 'ZERO_DRIFT') 'receipt database sys_user.points projection is not ZERO_DRIFT'

    Assert-ExactProperties -Value $Receipt.access -Expected @('menuMigrationId', 'menuEnabled', 'authorizedRoleBindingCount', 'roleBindingSha256') -Source 'receipt.access'
    Assert-Condition ($Receipt.access.menuMigrationId -ceq 'candidate_20260722_independent_board_credit_menu_v1') 'receipt access menu migration is unexpected'
    Assert-Condition ($Receipt.access.menuEnabled -eq $true) 'receipt access menu is not enabled'
    Assert-Condition ($Receipt.access.authorizedRoleBindingCount -is [long] -or $Receipt.access.authorizedRoleBindingCount -is [int]) 'receipt access role binding count must be an integer'
    Assert-Condition ($Receipt.access.authorizedRoleBindingCount -ge 1 -and $Receipt.access.authorizedRoleBindingCount -le 3) 'receipt access role binding count must be between 1 and 3'
    Assert-Match -Value $Receipt.access.roleBindingSha256 -Pattern '^[0-9a-f]{64}$' -Source 'receipt.access.roleBindingSha256'

    Assert-ExactProperties -Value $Receipt.flags -Expected @('backendCandidateEnabled', 'frontendCandidateEnabled') -Source 'receipt.flags'
    Assert-Condition ($Receipt.flags.backendCandidateEnabled -eq $true) 'receipt backend candidate flag is not enabled'
    Assert-Condition ($Receipt.flags.frontendCandidateEnabled -eq $true) 'receipt frontend candidate flag is not enabled'
    Assert-PassMatrix -Value $Receipt.readback -Expected @('jwt401', 'jwt403', 'admin200', 'conflict409', 'legacyGrant410', 'rollback404') -Source 'receipt.readback'

    Assert-ExactProperties -Value $Receipt.rollback -Expected @('backendFlagCanDisable', 'frontendFlagCanDisable', 'menuCanDisable', 'rolesCanUnbind') -Source 'receipt.rollback'
    foreach ($field in @('backendFlagCanDisable', 'frontendFlagCanDisable', 'menuCanDisable', 'rolesCanUnbind')) {
        Assert-Condition ($Receipt.rollback.$field -eq $true) "receipt.rollback.$field is not true"
    }
    if (-not $SkipWorktreeCheck) {
        Assert-CleanGitWorktree
    }
}

function Invoke-SelfTest {
    $fixturePath = Join-Path $RepoRoot 'docs\independent-board\W3J-CREDIT-CANDIDATE-RELEASE-RECEIPT.example.json'
    $inventory = Read-StrictJson -Path $CanonicalDomainInventoryPath -Source 'canonical domain inventory'
    Assert-CanonicalDomainInventory -Inventory $inventory
    $valid = Read-StrictJson -Path $fixturePath -Source 'self-test fixture'
    $headOutput = & git -C $RepoRoot rev-parse HEAD
    $headExitCode = $LASTEXITCODE
    Assert-Condition ($headExitCode -eq 0) 'git rev-parse HEAD failed'
    $head = ([string]($headOutput | Select-Object -First 1)).Trim().ToLowerInvariant()
    Assert-ExpectedCommitIsCheckedOut -ExpectedSourceCommit $head
    $valid.sourceCommit = $head
    $valid.target.domainInventorySha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $CanonicalDomainInventoryPath).Hash.ToLowerInvariant()
    Test-ReleaseReceipt -Receipt $valid -ExpectedSourceCommit $head -Inventory $inventory -SkipWorktreeCheck

    $cases = @(
        @{ name = 'unknown_top_level'; mutate = { param($receipt) $receipt | Add-Member -NotePropertyName unexpected -NotePropertyValue 'x' } },
        @{ name = 'wrong_backend_host'; mutate = { param($receipt) $receipt.target.backendHost = 'untrusted.example' } },
        @{ name = 'missing_database_proof'; mutate = { param($receipt) $receipt.database.currentRead = 'PENDING' } },
        @{ name = 'rollback_not_proven'; mutate = { param($receipt) $receipt.rollback.menuCanDisable = $false } }
    )
    foreach ($case in $cases) {
        $candidate = Read-StrictJson -Path $fixturePath -Source 'self-test fixture'
        $candidate.sourceCommit = $head
        $candidate.target.domainInventorySha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $CanonicalDomainInventoryPath).Hash.ToLowerInvariant()
        & $case.mutate $candidate
        $rejected = $false
        try {
            Test-ReleaseReceipt -Receipt $candidate -ExpectedSourceCommit $candidate.sourceCommit -Inventory $inventory -SkipWorktreeCheck
        }
        catch {
            $rejected = $_.Exception.Message -like 'CREDIT_CANDIDATE_RELEASE_NOT_READY:*'
        }
        Assert-Condition $rejected "self-test negative vector was accepted: $($case.name)"
    }
    $duplicateRejected = $false
    $duplicateReceiptPath = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($duplicateReceiptPath, '{"sourceCommit":"first","sourceCommit":"second"}', [System.Text.Encoding]::UTF8)
        try {
            [void](Read-StrictJson -Path $duplicateReceiptPath -Source 'self-test duplicate-key fixture')
        }
        catch {
            $duplicateRejected = $_.Exception.Message -like 'CREDIT_CANDIDATE_RELEASE_NOT_READY:*'
        }
    }
    finally {
        if (Test-Path -LiteralPath $duplicateReceiptPath) {
            Remove-Item -LiteralPath $duplicateReceiptPath -Force
        }
    }
    Assert-Condition $duplicateRejected 'self-test duplicate-key vector was accepted'
    $nonHeadCommitRejected = $false
    $parentOutput = & git -C $RepoRoot rev-parse HEAD^
    $parentExitCode = $LASTEXITCODE
    $nonHeadCandidate = if ($parentExitCode -eq 0) { ([string]($parentOutput | Select-Object -First 1)).Trim().ToLowerInvariant() } else { '0000000000000000000000000000000000000000' }
    try {
        Assert-ExpectedCommitIsCheckedOut -ExpectedSourceCommit $nonHeadCandidate
    }
    catch {
        $nonHeadCommitRejected = $_.Exception.Message -like 'CREDIT_CANDIDATE_RELEASE_NOT_READY:*'
    }
    Assert-Condition $nonHeadCommitRejected 'self-test non-HEAD ExpectedCommit vector was accepted'
    [ordered]@{
        schema = 'fbsir.independent-board.credit-candidate-release-readiness-self-test/v1'
        ok = $true
        positive = 'PASS'
        negativeVectors = @($cases.name) + @('duplicate_json_key', 'non_head_expected_commit')
        productionChanged = $false
    } | ConvertTo-Json -Compress
}

if ($SelfTest) {
    Invoke-SelfTest
    return
}

Assert-Condition (-not [string]::IsNullOrWhiteSpace($ReceiptPath)) 'ReceiptPath is required'
Assert-Match -Value $ExpectedCommit -Pattern '^[0-9a-f]{40}$' -Source 'ExpectedCommit'
Assert-ExpectedCommitIsCheckedOut -ExpectedSourceCommit $ExpectedCommit
$receipt = Read-StrictJson -Path $ReceiptPath -Source 'release receipt'
$inventory = Read-StrictJson -Path $CanonicalDomainInventoryPath -Source 'canonical domain inventory'
Assert-CanonicalDomainInventory -Inventory $inventory
Test-ReleaseReceipt -Receipt $receipt -ExpectedSourceCommit $ExpectedCommit -Inventory $inventory
[ordered]@{
    schema = $ReceiptSchema
    ok = $true
    receiptSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $ReceiptPath).Hash.ToLowerInvariant()
    sourceCommit = $receipt.sourceCommit
    backendHost = $receipt.target.backendHost
    adminHost = $receipt.target.adminHost
    migrationId = $receipt.database.migrationId
    readyForHumanActivationReview = $true
    productionChanged = $false
} | ConvertTo-Json -Compress
