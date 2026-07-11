param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$historicalWarningPattern = '\u4E0D\u518D\u4F7F\u7528|\u5DF2\u79FB\u9664|\u517C\u5BB9|\u5386\u53F2|retired|deprecated|legacy'
$backtick = [string][char]96
$documentedPathPattern = [regex]::Escape($backtick) + '(?<path>(?:FBSir-[^' + [regex]::Escape($backtick) + ']+|sql/[^' + [regex]::Escape($backtick) + ']+|scripts/[^' + [regex]::Escape($backtick) + ']+|docs/[^' + [regex]::Escape($backtick) + ']+))' + [regex]::Escape($backtick)
$markdownLinkPattern = '\[[^\]]*\]\((?<target>[^)]+)\)'

$rules = @(
    @{ name = 'legacy_brand'; pattern = '\u5FAE\u4FE1\u798F\u5E2E\u624B'; message = 'Document still contains the retired brand name.' },
    @{ name = 'legacy_environment_variable_primary'; pattern = 'WXFBSIR_[A-Z0-9_]+'; message = 'Document uses a legacy environment variable as the primary spelling.' },
    @{ name = 'local_absolute_user_path'; pattern = '[A-Za-z]:\\Users\\[^\\[:space:]]+'; message = 'Document contains a machine-local user path.' },
    @{ name = 'engine_client_id_query'; pattern = 'wss?://[^`[:space:]]*/ws/engine\?[^`[:space:]]*clientId='; message = 'Engine WebSocket must not require clientId in its URL.' },
    @{ name = 'legacy_engine_artifact'; pattern = 'wxfbsir-engine-(1\.2\.6|1\.6\.5)\.jar|\u7248\u672C\*\*: (1\.2\.6|1\.6\.5)'; message = 'Document contains a retired Engine artifact version.' },
    @{ name = 'request_id_null'; pattern = '"requestId": null'; message = 'Request-result example must use a real requestId.' },
    @{ name = 'legacy_engine_config'; pattern = 'websocket\.admin\.'; message = 'Document references the retired websocket.admin configuration tree.' },
    @{ name = 'legacy_api_route'; pattern = '`/(business/(aigc|dailyassistant|documentparse|officialaccount|point|certificate)/|business/websocket/)'; message = 'Document references a retired HTTP route.' },
    @{ name = 'retired_source_path'; pattern = 'FBSir-business/src/main/resources/application\.yml|FBSir-ui/nginx\.conf|FBSir-ui/src/api/business/officeAccount\.js|FBSir-admin/main/resources|FBSir-ui/src/views/business/host/apps/|FBSir-ui/src/views/system/point/'; message = 'Document references a retired or nonexistent source path.' }
)

$targets = Get-ChildItem -Path $Root -Recurse -File -Include *.md |
    Where-Object { $_.FullName -notmatch '\\node_modules\\|\\target\\|\\dist\\|\\\.fbs-engineering\\' } |
    Select-Object -ExpandProperty FullName
$findings = New-Object System.Collections.Generic.List[object]

function Add-Finding([string]$Rule, [string]$File, [int]$Line, [string]$Text, [string]$Message) {
    $findings.Add([PSCustomObject]@{
        rule = $Rule
        file = $File.Substring($Root.Length).TrimStart([char]92)
        line = $Line
        text = $Text.Trim()
        message = $Message
    })
}

function Resolve-BrandCompatiblePath([string]$RelativePath) {
    return Join-Path $Root ($RelativePath -replace '/', [IO.Path]::DirectorySeparatorChar)
}

foreach ($rule in $rules) {
    foreach ($target in $targets) {
        $matches = Select-String -Path $target -Pattern $rule.pattern -AllMatches
        foreach ($match in $matches) {
            if ($match.Line -match $historicalWarningPattern) {
                continue
            }
            Add-Finding $rule.name $target $match.LineNumber $match.Line $rule.message
        }
    }
}

# Verify exact source paths in backticks. Directories, placeholders, and prose fragments are excluded.
foreach ($target in $targets) {
    $lineNumber = 0
    foreach ($line in Get-Content -Encoding utf8 $target) {
        $lineNumber++
        foreach ($match in [regex]::Matches($line, $documentedPathPattern)) {
            $path = $match.Groups['path'].Value.Trim()
            if ($path -match '\[|\.\.\.|\[version\]|YYYY|\$\{|(^|/)target/' -or $path.EndsWith('/') -or $line -match '\u23F3.*\u521B\u5EFA') {
                continue
            }
            if (-not (Test-Path (Resolve-BrandCompatiblePath $path))) {
                Add-Finding 'missing_documented_source_path' $target $lineNumber $line "Documented source path does not exist: $path"
            }
        }
    }
}

# Verify repository-local Markdown links and published attachments.
foreach ($target in $targets) {
    $lineNumber = 0
    foreach ($line in Get-Content -Encoding utf8 $target) {
        $lineNumber++
        foreach ($match in [regex]::Matches($line, $markdownLinkPattern)) {
            $linkTarget = $match.Groups['target'].Value.Trim().Trim('<', '>')
            if ($linkTarget -match '^(?:https?://|mailto:|#|data:)' -or $linkTarget -match 'YYYY|\.\.\.|\$\{|\s+["'']') {
                continue
            }
            $linkPath = ($linkTarget -split '#', 2)[0]
            if ([string]::IsNullOrWhiteSpace($linkPath) -or $linkPath -notmatch '\.(?:md|zip|png|jpe?g|gif|svg|sql)$') {
                continue
            }
            $decodedPath = [Uri]::UnescapeDataString($linkPath) -replace '/', [IO.Path]::DirectorySeparatorChar
            $resolvedPath = if ($linkPath.StartsWith('/')) {
                Join-Path $Root $decodedPath.TrimStart([IO.Path]::DirectorySeparatorChar)
            } else {
                Join-Path ([IO.Path]::GetDirectoryName($target)) $decodedPath
            }
            if (-not (Test-Path -LiteralPath $resolvedPath)) {
                Add-Finding 'missing_markdown_link_target' $target $lineNumber $line "Markdown link target does not exist: $linkTarget"
            }
        }
    }
}

# Source-truth guardrails used by the documentation and release checklist.
$sourceAssertions = @(
    @{ rule = 'websocket_source_origin'; path = 'FBSir-ui/src/utils/websocket.js'; pattern = 'window\.location\.origin'; message = 'Relative API environments must derive WebSocket URLs from the current origin.' },
    @{ rule = 'preview_websocket_proxy'; path = 'FBSir-ui/vite.config.js'; pattern = 'ws:\s*true'; message = 'Vite API proxy must support WebSocket upgrades.' },
    @{ rule = 'footer_current_year'; path = 'FBSir-ui/src/settings.js'; pattern = 'Copyright.*2026'; message = 'Homepage footer must use the current requested year and brand.' },
    @{ rule = 'legacy_database_default'; path = 'FBSir-admin/src/main/resources/application-druid.yml'; pattern = 'jdbc:mysql://127\.0\.0\.1:3306/wxfbsir\?'; message = 'The default JDBC URL must preserve the historical wxfbsir database name.' },
    @{ rule = 'legacy_database_script'; path = 'sql/wxfbsir.sql'; pattern = 'CREATE DATABASE IF NOT EXISTS `wxfbsir`[\s\S]*USE `wxfbsir`'; message = 'The initialization script must preserve the historical wxfbsir database name.' },
    @{ rule = 'token_secret_no_default'; path = 'FBSir-admin/src/main/resources/application.yml'; pattern = 'secret:\s*\$\{FBSIR_TOKEN_SECRET:\$\{WXFBSIR_TOKEN_SECRET:\}\}'; message = 'Token signing secret must not ship with a non-empty default.' },
    @{ rule = 'database_password_no_default'; path = 'FBSir-admin/src/main/resources/application-druid.yml'; pattern = 'password:\s*\$\{FBSIR_MYSQL_PASSWORD:\$\{WXFBSIR_MYSQL_PASSWORD:\}\}'; message = 'Database password must not ship with a non-empty default.' },
    @{ rule = 'druid_console_disabled_default'; path = 'FBSir-admin/src/main/resources/application-druid.yml'; pattern = 'enabled:\s*\$\{FBSIR_DRUID_STAT_ENABLED:false\}'; message = 'Druid console must be disabled by default.' }
)

foreach ($assertion in $sourceAssertions) {
    $sourcePath = Resolve-BrandCompatiblePath $assertion.path
    if (-not (Test-Path $sourcePath)) {
        Add-Finding $assertion.rule $sourcePath 0 '' "Missing source file: $($assertion.path)"
        continue
    }
    $sourceText = Get-Content -Raw -Encoding UTF8 $sourcePath
    if ($sourceText -notmatch $assertion.pattern) {
        Add-Finding $assertion.rule $sourcePath 0 '' $assertion.message
    }
}

$websocketSourcePath = Resolve-BrandCompatiblePath 'FBSir-ui/src/utils/websocket.js'
if ((Test-Path $websocketSourcePath) -and (Get-Content -Raw -Encoding UTF8 $websocketSourcePath) -match 'DEFAULT_BACKEND') {
    Add-Finding 'websocket_no_fixed_backend' $websocketSourcePath 0 '' 'WebSocket utility must not retain a fixed localhost backend fallback.'
}

$fbsMenuMigration = Get-ChildItem -Path (Join-Path $Root 'sql') -File -Filter 'update_20260409_*.sql' |
    Where-Object { (Get-Content -Raw -Encoding UTF8 $_.FullName) -match 'FBS.*0,\s*10,' } |
    Select-Object -First 1
if (-not $fbsMenuMigration) {
    Add-Finding 'fbs_root_menu_source' (Join-Path $Root 'sql') 0 '' 'FBS menu migration must insert the menu at the root.'
}

# Dated migrations are located by their ASCII date prefix so this helper remains
# safe to run under Windows PowerShell with legacy system encodings.
$repairMigration = Get-ChildItem -Path (Join-Path $Root 'sql') -File -Filter 'update_20260710_*.sql' | Select-Object -First 1
if (-not $repairMigration) {
    Add-Finding 'fbs_parent_repair_migration' (Join-Path $Root 'sql') 0 '' 'The dated FBS parent repair migration is missing.'
} elseif ((Get-Content -Raw -Encoding UTF8 $repairMigration.FullName) -notmatch 'parent_id\s*=\s*0') {
    Add-Finding 'fbs_parent_repair_migration' $repairMigration.FullName 0 '' 'The dated FBS parent repair migration must set parent_id to zero.'
}

# Internal plans and runtime evidence may exist locally, but they must not be tracked by Git.
$trackedPaths = @(& git -C $Root ls-files)
foreach ($trackedPath in $trackedPaths) {
    if ($trackedPath -match '(^|/)\.fbs-engineering/|U3W-AI-.*\u5355\u4F53\u7CFB\u5B9E\u65BD\u603B\u7EB2|\u5168\u529F\u80FD\u8054\u6D4B\u4E0EBug\u4FEE\u590D\u6E05\u5355|(^|/)(NEXT-ROUND-MEMO|taskboard|contract)') {
        Add-Finding 'public_boundary_internal_artifact' (Join-Path $Root $trackedPath) 0 '' 'Internal engineering material must not be tracked in the public repository.'
    }
}

[PSCustomObject]@{
    root = $Root
    findingCount = $findings.Count
    findings = $findings
} | ConvertTo-Json -Depth 6
