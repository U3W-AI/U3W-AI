[CmdletBinding()]
param(
    [switch]$AllowDirty,
    [string]$ReceiptPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$jarPath = Join-Path $repoRoot 'FBSir-admin\target\fbsir-admin.jar'
if ([string]::IsNullOrWhiteSpace($ReceiptPath)) {
    $ReceiptPath = Join-Path $repoRoot 'work\release-builds\w05-reproducible-build-latest.json'
}
$receiptFullPath = [IO.Path]::GetFullPath($ReceiptPath)
$null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $receiptFullPath)

$gitHead = (& git -C $repoRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Git HEAD is unavailable.' }
$dirty = @(& git -C $repoRoot status --porcelain=v1)
$dirtyBefore = @($dirty | Sort-Object)
if (-not $AllowDirty -and $dirty.Count -ne 0) {
    throw "Reproducible candidate build requires a clean worktree; found $($dirty.Count) entries."
}
$eolPaths = @(
    'pom.xml',
    'FBSir-admin/pom.xml',
    'FBSir-admin/src/main/resources/application.yml',
    'FBSir-business/src/main/java/com/wx/fbsir/business/board/attribution',
    'FBSir-business/src/main/resources/contracts',
    'FBSir-business/src/main/resources/mapper/board/attribution'
)
$eolState = @(& git -C $repoRoot ls-files --eol -- @eolPaths)
$nonCanonicalEol = @($eolState | Where-Object {
    $_ -match 'w/(crlf|mixed)' -and $_ -match 'attr/text eol=lf'
})
if ($nonCanonicalEol.Count -ne 0) {
    throw "Candidate build inputs are not checked out with canonical LF endings: $($nonCanonicalEol.Count) file(s). Use a fresh checkout of the bound commit."
}

$rootPom = Join-Path $repoRoot 'pom.xml'
$adminPom = Join-Path $repoRoot 'FBSir-admin\pom.xml'
$rootPomText = Get-Content -LiteralPath $rootPom -Raw -Encoding UTF8
$adminPomText = Get-Content -LiteralPath $adminPom -Raw -Encoding UTF8
if ($rootPomText -notmatch '<project\.build\.outputTimestamp>[^<]+</project\.build\.outputTimestamp>' -or
    $adminPomText -notmatch '<outputTimestamp>\$\{project\.build\.outputTimestamp\}</outputTimestamp>') {
    throw 'Reproducible timestamp contract is missing from the Maven build.'
}

$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$tempRoot = Join-Path $tempBase ('u3w-w05-repro-' + [Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $tempRoot
$firstJar = Join-Path $tempRoot 'first-fbsir-admin.jar'

function Invoke-CleanPackage {
    $old = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & mvn -q -f $rootPom clean package -DskipTests -pl FBSir-admin -am 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $old }
    if ($exitCode -ne 0) {
        throw "Maven clean package failed: $($output -join [Environment]::NewLine)"
    }
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "Expected executable JAR is missing: $jarPath"
    }
}

try {
    Invoke-CleanPackage
    Copy-Item -LiteralPath $jarPath -Destination $firstJar
    $first = Get-Item -LiteralPath $firstJar
    $firstSha = (Get-FileHash -LiteralPath $firstJar -Algorithm SHA256).Hash.ToLowerInvariant()

    Invoke-CleanPackage
    $second = Get-Item -LiteralPath $jarPath
    $secondSha = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($first.Length -ne $second.Length -or $firstSha -cne $secondSha) {
        throw "Reproducible JAR mismatch: $($first.Length)/$firstSha versus $($second.Length)/$secondSha"
    }
    $gitHeadAfter = (& git -C $repoRoot rev-parse HEAD).Trim()
    $dirtyAfter = @(& git -C $repoRoot status --porcelain=v1 | Sort-Object)
    if ($gitHeadAfter -cne $gitHead -or
        @(Compare-Object -ReferenceObject $dirtyBefore -DifferenceObject $dirtyAfter).Count -ne 0) {
        throw 'Source HEAD or worktree state changed during the two builds.'
    }

    $report = [ordered]@{
        schemaVersion = 'fbsir.w05ReproducibleBuildReceipt.v1'
        generatedAt = [DateTime]::UtcNow.ToString('o')
        gitHead = $gitHead
        dirtyEntryCount = $dirty.Count
        dirtyAllowedForDevelopmentProof = [bool]$AllowDirty
        command = 'mvn -q -f <root-pom> clean package -DskipTests -pl FBSir-admin -am'
        outputTimestamp = ([regex]::Match(
            $rootPomText,
            '<project\.build\.outputTimestamp>([^<]+)</project\.build\.outputTimestamp>')).Groups[1].Value
        first = [ordered]@{ sizeBytes = $first.Length; sha256 = $firstSha }
        second = [ordered]@{ sizeBytes = $second.Length; sha256 = $secondSha }
        identical = $true
        gitHeadAfter = $gitHeadAfter
        worktreeStableDuringBuild = $true
        canonicalLfBuildInputs = $true
        rootPomSha256 = (Get-FileHash -LiteralPath $rootPom -Algorithm SHA256).Hash.ToLowerInvariant()
        adminPomSha256 = (Get-FileHash -LiteralPath $adminPom -Algorithm SHA256).Hash.ToLowerInvariant()
        productionChanged = $false
        status = 'PASS'
    }
    [IO.File]::WriteAllText(
        $receiptFullPath,
        (($report | ConvertTo-Json -Depth 6) + [Environment]::NewLine),
        [Text.UTF8Encoding]::new($false))
    Write-Output ($report | ConvertTo-Json -Depth 6)
}
finally {
    if (Test-Path -LiteralPath $tempRoot -PathType Container) {
        $resolved = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $tempRoot).Path)
        if (-not $resolved.StartsWith(
                $tempBase, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing cleanup outside the system temp root: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
