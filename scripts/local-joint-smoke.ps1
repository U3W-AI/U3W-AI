param(
    [string]$BackendBaseUrl = 'http://127.0.0.1:18080',
    [string]$FrontendBaseUrl = 'http://127.0.0.1:18082',
    [string]$Username = $env:U3W_SMOKE_USERNAME,
    [string]$Password = $env:U3W_SMOKE_PASSWORD,
    [string]$EngineId = 'engine-001',
    [string]$OpenClawHostId = 'test',
    [switch]$SkipFrontend,
    [switch]$SkipOpenClaw
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
    throw 'Provide -Username/-Password or set U3W_SMOKE_USERNAME and U3W_SMOKE_PASSWORD. The smoke test no longer ships default credentials.'
}

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

function Expand-LeafRoutes {
    param(
        [object[]]$Nodes,
        [string]$ParentPath = ''
    )

    foreach ($node in $Nodes) {
        $path = if ($node.path -and $node.path.StartsWith('/')) {
            $node.path
        } elseif ($ParentPath) {
            $ParentPath.TrimEnd('/') + '/' + $node.path.TrimStart('/')
        } else {
            '/' + $node.path.TrimStart('/')
        }

        if ($node.children -and @($node.children).Count -gt 0) {
            Expand-LeafRoutes -Nodes $node.children -ParentPath $path
        } else {
            [PSCustomObject]@{
                path = $path
                component = $node.component
            }
        }
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

    if (-not $SkipFrontend) {
        foreach ($apiPrefix in @('/prod-api', '/stage-api')) {
            $proxyLoginResponse = Invoke-RestMethod -Method Post -Uri ($FrontendBaseUrl + $apiPrefix + '/login') -ContentType 'application/json' -Body $loginBody
            $proxyToken = $proxyLoginResponse.token
            $proxyPassed = -not [string]::IsNullOrWhiteSpace($proxyToken)
            $checkName = 'frontend_proxy_login_' + $apiPrefix.TrimStart('/').Replace('-api', '')
            $results.Add((New-CheckResult -Name $checkName -Passed $proxyPassed -Detail ("{0}/login should proxy to the backend" -f $apiPrefix)))
        }

        $wsBaseUrl = $FrontendBaseUrl -replace '^https:', 'wss:' -replace '^http:', 'ws:'
        $wsUri = [Uri]($wsBaseUrl.TrimEnd('/') + '/prod-api/ws/client?clientType=web&token=' + [Uri]::EscapeDataString($token))
        $webSocket = New-Object System.Net.WebSockets.ClientWebSocket
        $webSocketTimeout = New-Object System.Threading.CancellationTokenSource(10000)
        try {
            $webSocket.ConnectAsync($wsUri, $webSocketTimeout.Token).GetAwaiter().GetResult() | Out-Null
            $buffer = New-Object byte[] 4096
            $segment = New-Object System.ArraySegment[byte] -ArgumentList @(,$buffer)
            $receiveResult = $webSocket.ReceiveAsync($segment, $webSocketTimeout.Token).GetAwaiter().GetResult()
            $connectedMessage = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $receiveResult.Count) | ConvertFrom-Json
            $wsPassed = ($webSocket.State -eq [System.Net.WebSockets.WebSocketState]::Open) -and ($connectedMessage.type -eq 'CONNECTED')
            $results.Add((New-CheckResult -Name 'frontend_proxy_websocket' -Passed $wsPassed -Detail 'Same-origin /prod-api WebSocket received CONNECTED'))
        } finally {
            $webSocket.Dispose()
            $webSocketTimeout.Dispose()
        }
    }

    $routerResponse = Invoke-RestMethod -Method Get -Uri ($BackendBaseUrl + '/getRouters') -Headers $headers
    $leafRoutes = @(Expand-LeafRoutes -Nodes $routerResponse.data)
    $requiredFbsRoutes = @(
        '/fbs/scenePack', '/fbs/authCode', '/fbs/userPack', '/fbs/enterprise',
        '/fbs/enterprisePack', '/fbs/enterpriseMember', '/myFbs/activateAuthCode',
        '/myFbs/claimablePacks', '/myFbs/myApikey', '/myFbs/myPacks'
    )
    $missingFbsRoutes = @($requiredFbsRoutes | Where-Object { $_ -notin $leafRoutes.path })
    $routerPassed = ($leafRoutes.Count -ge 52) -and ($missingFbsRoutes.Count -eq 0)
    $results.Add((New-CheckResult -Name 'dynamic_routes_complete' -Passed $routerPassed -Detail ("leafRoutes={0}; missingFbsRoutes={1}" -f $leafRoutes.Count, ($missingFbsRoutes -join ','))))

    $pointsSummary = Invoke-RestMethod -Method Get -Uri ($BackendBaseUrl + '/points/getPointsSummary') -Headers $headers
    $pointsRecords = Invoke-RestMethod -Method Get -Uri ($BackendBaseUrl + '/points/getPointsRecord?pageNum=1&pageSize=10') -Headers $headers
    $pointsPassed = ($pointsSummary.code -eq 200) -and ($pointsRecords.code -eq 200)
    $results.Add((New-CheckResult -Name 'points_overview_database' -Passed $pointsPassed -Detail ("summaryCode={0}; recordsCode={1}" -f $pointsSummary.code, $pointsRecords.code)))

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
        $results.Add((New-CheckResult -Name 'simulated_openclaw_whitelist_online' -Passed ($openClawHost.onlineStatus -eq 'online') -Detail ("Simulated OpenClaw onlineStatus is {0}" -f $openClawHost.onlineStatus)))
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
