param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$findings = New-Object System.Collections.Generic.List[string]

function Require-FileContains([string]$RelativePath, [string]$Pattern, [string]$Message) {
    $path = Join-Path $Root $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        $findings.Add("Missing required compatibility file: $RelativePath")
        return
    }
    if ((Get-Content -Raw -Encoding UTF8 -LiteralPath $path) -notmatch $Pattern) {
        $findings.Add($Message)
    }
}

Require-FileContains 'sql/wxfbsir.sql' 'CREATE DATABASE IF NOT EXISTS\s+\x60wxfbsir\x60' 'The initialization script must create the historical wxfbsir schema.'
Require-FileContains 'sql/wxfbsir.sql' 'USE\s+\x60wxfbsir\x60' 'The initialization script must select the historical wxfbsir schema.'
Require-FileContains 'FBSir-admin/src/main/resources/application-druid.yml' 'jdbc:mysql://127\.0\.0\.1:3306/wxfbsir\?' 'The default JDBC URL must continue to target the historical wxfbsir schema.'

if (Test-Path -LiteralPath (Join-Path $Root 'sql/fbsir.sql')) {
    $findings.Add('Do not introduce sql/fbsir.sql without an explicit database migration and rollback plan.')
}

$repositoryMarkdown = @(& git -c core.quotepath=false -C $Root ls-files --cached --others --exclude-standard -- '*.md')
if ($LASTEXITCODE -ne 0) {
    throw "Unable to enumerate repository-owned Markdown files under $Root."
}
$markdownFiles = $repositoryMarkdown |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    ForEach-Object { Join-Path $Root ($_ -replace '/', [IO.Path]::DirectorySeparatorChar) } |
    Where-Object { Test-Path -LiteralPath $_ } |
    ForEach-Object { Get-Item -LiteralPath $_ }
$forbiddenDocumentPatterns = @(
    'sql/fbsir\.sql',
    '\u6570\u636e\u5e93\s*\x60fbsir\x60',
    '\u9009\u62e9\s+fbsir\s+\u6570\u636e\u5e93',
    'mysqldump[^\r\n]*\s+fbsir\s+>'
)

foreach ($file in $markdownFiles) {
    $text = Get-Content -Raw -Encoding UTF8 -LiteralPath $file.FullName
    foreach ($pattern in $forbiddenDocumentPatterns) {
        if ($text -match $pattern) {
            $relativePath = $file.FullName.Substring($Root.Length).TrimStart([char]92)
            $findings.Add("Database compatibility drift in ${relativePath}: pattern ${pattern}")
        }
    }
}

$result = [PSCustomObject]@{
    root = $Root
    findingCount = $findings.Count
    findings = $findings
}
$result | ConvertTo-Json -Depth 4

if ($findings.Count -gt 0) {
    exit 1
}
