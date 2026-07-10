param(
    [string]$Root = 'C:\Users\dhc\Documents\Codex\2026-07-10\github-plugin-github-openai-curated-remote\U3W-AI'
)

$ErrorActionPreference = 'Stop'
$historicalWarningPattern = '\u4E0D\u518D\u4F7F\u7528|\u5DF2\u79FB\u9664|retired|deprecated'
$backtick = [string][char]96
$documentedPathPattern = [regex]::Escape($backtick) + '(?<path>WxFbsir-[^' + [regex]::Escape($backtick) + ']+)' + [regex]::Escape($backtick)

$rules = @(
    @{ name = 'legacy_brand'; pattern = '\u5FAE\u4FE1\u798F\u5E2E\u624B'; message = 'Document still contains the retired brand name.' },
    @{ name = 'engine_client_id_query'; pattern = 'wss?://[^`[:space:]]*/ws/engine\?[^`[:space:]]*clientId='; message = 'Engine WebSocket must not require clientId in its URL.' },
    @{ name = 'legacy_engine_artifact'; pattern = 'wxfbsir-engine-(1\.2\.6|1\.6\.5)\.jar|\u7248\u672C\*\*: (1\.2\.6|1\.6\.5)'; message = 'Document contains a retired Engine artifact version.' },
    @{ name = 'request_id_null'; pattern = '"requestId": null'; message = 'Request-result example must use a real requestId.' },
    @{ name = 'legacy_engine_config'; pattern = 'websocket\.admin\.'; message = 'Document references the retired websocket.admin configuration tree.' },
    @{ name = 'legacy_api_route'; pattern = '`/(business/(aigc|dailyassistant|documentparse|officialaccount|point|certificate)/|business/websocket/)'; message = 'Document references a retired HTTP route.' },
    @{ name = 'retired_source_path'; pattern = 'WxFbsir-business/src/main/resources/application\.yml|WxFbsir-ui/nginx\.conf|WxFbsir-ui/src/api/business/officeAccount\.js|WxFbsir-admin/main/resources|WxFbsir-ui/src/views/business/host/apps/|WxFbsir-ui/src/views/system/point/'; message = 'Document references a retired or nonexistent source path.' }
)

$targets = Get-ChildItem -Path $Root -Recurse -File -Include *.md |
    Where-Object { $_.FullName -notmatch '\\node_modules\\|\\target\\|\\dist\\' } |
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
            if ($path -match '\[|\.\.\.|\[version\]|\s|[^\x00-\x7F]' -or $path.EndsWith('/')) {
                continue
            }
            if (-not (Test-Path (Join-Path $Root $path))) {
                Add-Finding 'missing_documented_source_path' $target $lineNumber $line "Documented source path does not exist: $path"
            }
        }
    }
}

# Source-truth guardrails used by the documentation and release checklist.
$sourceAssertions = @(
    @{ rule = 'websocket_source_origin'; path = 'WxFbsir-ui/src/utils/websocket.js'; pattern = 'window\.location\.origin'; message = 'Relative API environments must derive WebSocket URLs from the current origin.' },
    @{ rule = 'preview_websocket_proxy'; path = 'WxFbsir-ui/vite.config.js'; pattern = 'ws:\s*true'; message = 'Vite API proxy must support WebSocket upgrades.' },
    @{ rule = 'footer_current_year'; path = 'WxFbsir-ui/src/settings.js'; pattern = 'Copyright.*2026'; message = 'Homepage footer must use the current requested year and brand.' }
)

foreach ($assertion in $sourceAssertions) {
    $sourcePath = Join-Path $Root $assertion.path
    if (-not (Test-Path $sourcePath)) {
        Add-Finding $assertion.rule $sourcePath 0 '' "Missing source file: $($assertion.path)"
        continue
    }
    $sourceText = Get-Content -Raw -Encoding UTF8 $sourcePath
    if ($sourceText -notmatch $assertion.pattern) {
        Add-Finding $assertion.rule $sourcePath 0 '' $assertion.message
    }
}

$websocketSourcePath = Join-Path $Root 'WxFbsir-ui/src/utils/websocket.js'
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

[PSCustomObject]@{
    root = $Root
    findingCount = $findings.Count
    findings = $findings
} | ConvertTo-Json -Depth 6
