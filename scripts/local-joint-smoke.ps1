param(
    [string]$BackendBaseUrl = 'http://127.0.0.1:18080',
    [string]$FrontendBaseUrl = 'http://127.0.0.1:18082',
    [string]$Username = 'admin',
    [string]$Password = 'admin123',
    [string]$EngineId = 'engine-001',
    [string]$OpenClawHostId = 'test',
    [switch]$SkipFrontend,
    [switch]$SkipOpenClaw
)

$ErrorActionPreference = 'Stop'

function New-CheckResult {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )

    [PSCustomObject]@{
        name = $Name
        passed = $Passed
        detail = $Detail
    }
}

function Get-HttpStatusCode {
    param(
        [scriptblock]$Action
    )

    try {
        & $Action | Out-Null
        return 200
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

$results = New-Object System.Collections.Generic.List[object]
$token = $null

try {
    if (-not $SkipFrontend) {
        $frontendHtml = Invoke-WebRequest -Uri ($FrontendBaseUrl + '/') -UseBasicParsing
        $frontendPassed = ($frontendHtml.Content -match '<title>') -and ($frontendHtml.Content -match '<script type="module"')
        $results.Add((New-CheckResult -Name 'frontend_html_served' -Passed $frontendPassed -Detail 'Front page should include a title and a module entry script'))
    }

    $loginBody = @{
        username = $Username
        password = $Password
        code = ''
        uuid = ''
    } | ConvertTo-Json

    $loginResponse = Invoke-RestMethod -Method Post -Uri ($BackendBaseUrl + '/login') -ContentType 'application/json' -Body $loginBody
    $token = $loginResponse.token
    Assert-True (-not [string]::IsNullOrWhiteSpace($token)) 'Login did not return a token.'

    $headers = @{ Authorization = 'Bearer ' + $token }
    $results.Add((New-CheckResult -Name 'login' -Passed $true -Detail 'Login returned JWT token'))

    $anonymousStatus = Get-HttpStatusCode -Action {
        Invoke-WebRequest -Method Get -Uri ($BackendBaseUrl + '/ws/engine/list') -UseBasicParsing
    }
    $results.Add((New-CheckResult -Name 'anonymous_engine_list_blocked' -Passed ($anonymousStatus -eq 401) -Detail ("Expected 401, actual {0}" -f $anonymousStatus)))

    $engineList = Invoke-RestMethod -Method Get -Uri ($BackendBaseUrl + '/ws/engine/list') -Headers $headers
    $engine = $engineList.engines | Where-Object { $_.engineId -eq $EngineId } | Select-Object -First 1
    Assert-True ($null -ne $engine) ("Engine {0} not found in /ws/engine/list" -f $EngineId)
    $results.Add((New-CheckResult -Name 'engine_registered' -Passed ($engine.status -eq 'REGISTERED') -Detail ("Engine status is {0}" -f $engine.status)))

    $whitelist = Invoke-RestMethod -Method Get -Uri ($BackendBaseUrl + '/business/host/whitelist/list?pageNum=1&pageSize=20') -Headers $headers
    $engineHost = $whitelist.rows | Where-Object { $_.hostId -eq $EngineId } | Select-Object -First 1
    Assert-True ($null -ne $engineHost) ("Host whitelist entry {0} not found" -f $EngineId)
    $results.Add((New-CheckResult -Name 'engine_whitelist_online' -Passed ($engineHost.onlineStatus -eq 'online') -Detail ("Whitelist onlineStatus is {0}" -f $engineHost.onlineStatus)))

    if (-not $SkipOpenClaw) {
        $openClawHost = $whitelist.rows | Where-Object { $_.hostId -eq $OpenClawHostId } | Select-Object -First 1
        Assert-True ($null -ne $openClawHost) ("OpenClaw whitelist entry {0} not found" -f $OpenClawHostId)
        $results.Add((New-CheckResult -Name 'openclaw_whitelist_online' -Passed ($openClawHost.onlineStatus -eq 'online') -Detail ("OpenClaw onlineStatus is {0}" -f $openClawHost.onlineStatus)))
    }

    $healthBody = @{
        engineId = $EngineId
        type = 'HEALTH_CHECK'
        payload = @{}
    } | ConvertTo-Json -Depth 4

    $healthResponse = Invoke-RestMethod -Method Post -Uri ($BackendBaseUrl + '/ws/engine/request') -Headers $headers -ContentType 'application/json' -Body $healthBody
    $requestId = $healthResponse.requestId
    $sourceType = $healthResponse.data.sourceType
    $engineSuccess = $healthResponse.data.success

    $results.Add((New-CheckResult -Name 'engine_health_http' -Passed ($healthResponse.success -and $engineSuccess) -Detail ("requestId={0}; sourceType={1}" -f $requestId, $sourceType)))
    $results.Add((New-CheckResult -Name 'engine_health_source_type' -Passed ($sourceType -eq 'HTTP') -Detail ("Expected HTTP, actual {0}" -f $sourceType)))
} catch {
    $results.Add((New-CheckResult -Name 'script_exception' -Passed $false -Detail $_.Exception.Message))
}

$passedCount = ($results | Where-Object { $_.passed }).Count
$failedResults = $results | Where-Object { -not $_.passed }
$summary = [PSCustomObject]@{
    backend = $BackendBaseUrl
    frontend = $FrontendBaseUrl
    engineId = $EngineId
    passed = ($failedResults.Count -eq 0)
    passedCount = $passedCount
    totalCount = $results.Count
    checks = $results
}

$summary | ConvertTo-Json -Depth 6

if ($failedResults.Count -gt 0) {
    exit 1
}

exit 0
