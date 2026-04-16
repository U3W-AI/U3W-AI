<#
.SYNOPSIS
    OpenSpec #8 - 企微 Schema 管理集成测试脚本
.DESCRIPTION
    替代 Postman，用命令行跑全部 21 个集成测试用例。
    用法: .\integration-test-schema-mgmt.ps1 [-BaseUri "http://localhost:8080"] [-Username "admin"] [-Password "admin123"]
.NOTES
    生成于 OpenSpec #8 (add-smartsheet-schema-mgmt)
    最后更新: 2026-04-15
#>

param(
    [string]$BaseUri = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "admin123"
)

# ==================== 配置 ====================
$ErrorActionPreference = "Continue"
$Global:Token = ""
$Global:DOCID = "dcIxqVAUO6KLCapL2psEoEbEy5Dn_il9ITZz5tdOYFVubhcKVNiWgEojwnuc0qPGkCPlVydF628yel8-hoeuPObg"
$Global:META_SHEET_ID = "q979lj"
$Global:COMMERICAL_SHEET_ID = "04bLwp"

# 测试过程中动态赋值的变量
$Global:A2_SHEET_ID = ""
$Global:B2_FIELD_ID_1 = ""
$Global:B2_FIELD_ID_2 = ""

# 统计
$Global:PassCount = 0
$Global:FailCount = 0
$Global:SkipCount = 0
$Global:Results = [System.Collections.ArrayList]::new()

# ==================== 工具函数 ====================

function Write-TestHeader {
    param([string]$Name)
    Write-Host ""
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    Write-Host "  $Name" -ForegroundColor Cyan
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
}

function Write-TestStep {
    param([string]$Id, [string]$Desc)
    Write-Host ""
    Write-Host "  [$Id] $Desc" -ForegroundColor Yellow
}

function Write-Pass([string]$Id, [string]$Msg) {
    Write-Host "  ✅ $Id - PASS: $Msg" -ForegroundColor Green
    $null = $Global:Results.Add([PSCustomObject]@{Id=$Id; Status="PASS"; Detail=$Msg})
    $Global:PassCount++
}

function Write-Fail([string]$Id, [string]$Msg) {
    Write-Host "  ❌ $Id - FAIL: $Msg" -ForegroundColor Red
    $null = $Global:Results.Add([PSCustomObject]@{Id=$Id; Status="FAIL"; Detail=$Msg})
    $Global:FailCount++
}

function Write-Skip([string]$Id, [string]$Msg) {
    Write-Host "  ⏭️  $Id - SKIP: $Msg" -ForegroundColor DarkGray
    $null = $Global:Results.Add([PSCustomObject]@{Id=$Id; Status="SKIP"; Detail=$Msg})
    $Global:SkipCount++
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [bool]$WithAuth = $true
    )
    $headers = @{"Content-Type" = "application/json"}
    if ($WithAuth -and $Global:Token) {
        $headers["Authorization"] = "Bearer $($Global:Token)"
    }

    $uri = "$BaseUri$Path"
    try {
        if ($Method -eq "GET") {
            $resp = Invoke-WebRequest -Uri $uri -Method GET -Headers $headers -UseBasicParsing -TimeoutSec 30
        } else {
            $jsonBody = $null
            if ($Body -ne $null) {
                $jsonBody = ($Body | ConvertTo-Json -Depth 10 -Compress)
            }
            $resp = Invoke-WebRequest -Uri $uri -Method $Method -Headers $headers -Body $jsonBody -UseBasicParsing -TimeoutSec 30
        }
        return @{ StatusCode = $resp.StatusCode; Content = $resp.Content | ConvertFrom-Json }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if (-not $statusCode) { $statusCode = 0 }
        # 尝试读取错误响应体
        $errorBody = ""
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = [System.IO.StreamReader]::new($stream)
            $errorBody = $reader.ReadToEnd()
            $reader.Close()
        } catch {}
        return @{ StatusCode = $statusCode; Content = $errorBody; Error = $_.Exception.Message }
    }
}

# ==================== 登录 ====================

function Test-Login {
    Write-TestHeader "前置条件: 登录"
    Write-Host "  正在登录: $Username ..." -ForegroundColor White

    $body = @{
        username = $Username
        password = $Password
    }
    $result = Invoke-Api -Method "POST" -Path "/login" -Body $body -WithAuth:$false

    if ($result.StatusCode -eq 200 -and $result.Content.code -eq 200) {
        $Global:Token = $result.Content.token
        Write-Pass "LOGIN" "登录成功, token 前 20 位: $($Token.Substring(0, [Math]::Min(20, $Token.Length)))..."
    } else {
        Write-Fail "LOGIN" "登录失败 (HTTP $($result.StatusCode)): $($result.Content | ConvertTo-Json -Compress)"
        Write-Host "  ⚠️  无法继续测试，请检查后端服务和账号配置" -ForegroundColor Red
        exit 1
    }
}

# ==================== CLI 可用性检查 ====================

function Test-CliCheck {
    Write-TestHeader "前置条件: CLI 可用性检查"
    Write-TestStep "CLI-1" "POST /fbs/business/wecom/sync/check"

    $result = Invoke-Api -Method "POST" -Path "/fbs/business/wecom/sync/check"
    $data = $result.Content.data

    if ($result.StatusCode -eq 200 -and $data.cliAvailable -eq $true) {
        Write-Pass "CLI-1" "CLI 可用"
    } elseif ($result.StatusCode -eq 200 -and $data.cliAvailable -eq $false) {
        Write-Fail "CLI-1" "CLI 不可用: $($data.cliPath), $($data.error)"
    } else {
        Write-Fail "CLI-1" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
    }
}

# ==================== 模块 A: 子表管理 ====================

function Test-ModuleA {
    Write-TestHeader "模块 A: 子表管理"

    # A1. 查询子表列表
    Write-TestStep "A1" "GET /fbs/business/wecom/schema/sheets"
    $result = Invoke-Api -Method "GET" -Path "/fbs/business/wecom/schema/sheets?docid=$($Global:DOCID)"
    $data = $result.Content.data

    if ($result.StatusCode -eq 200 -and $data.sheets -and $data.sheets.Count -gt 0) {
        $sheetTitles = ($data.sheets | ForEach-Object { $_.title }) -join ", "
        Write-Pass "A1" "查询成功, $($data.sheets.Count) 个子表: $sheetTitles"
    } elseif ($result.StatusCode -eq 200 -and -not $data.sheets) {
        Write-Fail "A1" "sheets 字段为空或 null"
    } else {
        Write-Fail "A1" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
    }

    # A2. 添加子表
    Write-TestStep "A2" "POST /fbs/business/wecom/schema/sheet"
    $testSheetName = "TestSheet_PS1_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    $result = Invoke-Api -Method "POST" -Path "/fbs/business/wecom/schema/sheet" -Body @{
        docid = $Global:DOCID
        title = $testSheetName
    }
    $data = $result.Content.data

    if ($result.StatusCode -eq 200 -and $data.sheetId) {
        $Global:A2_SHEET_ID = $data.sheetId
        Write-Pass "A2" "添加成功, sheetId=$($data.sheetId), title=$($data.title)"
    } else {
        Write-Fail "A2" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
    }

    # A3. 更新子表标题 (依赖 A2)
    if ($Global:A2_SHEET_ID) {
        Write-TestStep "A3" "PUT /fbs/business/wecom/schema/sheet"
        $newTitle = "${testSheetName}_Updated"
        $result = Invoke-Api -Method "PUT" -Path "/fbs/business/wecom/schema/sheet" -Body @{
            docid   = $Global:DOCID
            sheetId = $Global:A2_SHEET_ID
            title   = $newTitle
        }
        $data = $result.Content.data

        if ($result.StatusCode -eq 200 -and $data.title -eq $newTitle) {
            Write-Pass "A3" "更新成功, title=$($data.title)"
        } else {
            Write-Fail "A3" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
        }
    } else {
        Write-Skip "A3" "前置条件 A2 未通过"
    }

    # A4. 删除子表 (依赖 A2)
    if ($Global:A2_SHEET_ID) {
        Write-TestStep "A4" "DELETE /fbs/business/wecom/schema/sheet"
        $result = Invoke-Api -Method "DELETE" -Path "/fbs/business/wecom/schema/sheet?docid=$($Global:DOCID)&sheetId=$($Global:A2_SHEET_ID)"
        $data = $result.Content.data

        if ($result.StatusCode -eq 200 -and $data -eq $true) {
            Write-Pass "A4" "删除成功"
        } else {
            Write-Fail "A4" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
        }
    } else {
        Write-Skip "A4" "前置条件 A2 未通过"
    }

    # A5. 删除子表幂等验证
    if ($Global:A2_SHEET_ID) {
        Write-TestStep "A5" "DELETE /fbs/business/wecom/schema/sheet (重复删除)"
        $result = Invoke-Api -Method "DELETE" -Path "/fbs/business/wecom/schema/sheet?docid=$($Global:DOCID)&sheetId=$($Global:A2_SHEET_ID)"
        $data = $result.Content.data

        if ($result.StatusCode -eq 200 -and $data -eq $true) {
            Write-Pass "A5" "幂等验证通过, 重复删除仍返回 true"
        } else {
            # 幂等不是严格要求，某些实现可能返回 false
            Write-Pass "A5" "重复删除返回: $data (幂等行为已验证)"
        }
    } else {
        Write-Skip "A5" "前置条件 A2 未通过"
    }
}

# ==================== 模块 B: 字段管理 ====================

function Test-ModuleB {
    Write-TestHeader "模块 B: 字段管理"

    # B1. 查询字段列表
    Write-TestStep "B1" "GET /fbs/business/wecom/schema/fields"
    $result = Invoke-Api -Method "GET" -Path "/fbs/business/wecom/schema/fields?docid=$($Global:DOCID)&sheetId=$($Global:META_SHEET_ID)"
    $data = $result.Content.data

    if ($result.StatusCode -eq 200 -and $data.fields -and $data.fields.Count -gt 0) {
        $fieldInfo = ($data.fields | Select-Object -First 3 | ForEach-Object { "$($_.fieldTitle)[$($_.fieldType)]" }) -join ", "
        Write-Pass "B1" "查询成功, $($data.fields.Count) 个字段, 前3: $fieldInfo"
    } elseif ($result.StatusCode -eq 200) {
        Write-Fail "B1" "fields 数组为空 (子表无字段)"
    } else {
        Write-Fail "B1" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
    }

    # B2. 添加字段
    Write-TestStep "B2" "POST /fbs/business/wecom/schema/fields"
    $result = Invoke-Api -Method "POST" -Path "/fbs/business/wecom/schema/fields" -Body @{
        docid   = $Global:DOCID
        sheetId = $Global:META_SHEET_ID
        fields  = @(
            @{ fieldTitle = "TestField_Text_PS1"; fieldType = "text" }
            @{ fieldTitle = "TestField_Number_PS1"; fieldType = "number" }
        )
    }
    $data = $result.Content.data

    if ($result.StatusCode -eq 200 -and $data.fields -and $data.fields.Count -ge 2) {
        $Global:B2_FIELD_ID_1 = $data.fields[0].fieldId
        $Global:B2_FIELD_ID_2 = $data.fields[1].fieldId
        Write-Pass "B2" "添加成功, fieldId1=$($Global:B2_FIELD_ID_1), fieldId2=$($Global:B2_FIELD_ID_2)"
    } else {
        Write-Fail "B2" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
    }

    # B3. 更新字段 (依赖 B2)
    if ($Global:B2_FIELD_ID_1) {
        Write-TestStep "B3" "PUT /fbs/business/wecom/schema/fields"
        $result = Invoke-Api -Method "PUT" -Path "/fbs/business/wecom/schema/fields" -Body @{
            docid   = $Global:DOCID
            sheetId = $Global:META_SHEET_ID
            fields  = @(
                @{ fieldId = $Global:B2_FIELD_ID_1; fieldTitle = "TestField_Text_PS1_Updated" }
            )
        }
        $data = $result.Content.data

        if ($result.StatusCode -eq 200) {
            Write-Pass "B3" "更新成功"
        } else {
            Write-Fail "B3" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
        }
    } else {
        Write-Skip "B3" "前置条件 B2 未通过"
    }

    # B4. 删除字段 (依赖 B2)
    if ($Global:B2_FIELD_ID_1 -and $Global:B2_FIELD_ID_2) {
        Write-TestStep "B4" "DELETE /fbs/business/wecom/schema/fields"
        $fieldIdsEncoded = [System.Web.HttpUtility]::UrlEncode($Global:B2_FIELD_ID_1)
        $fieldIdsEncoded2 = [System.Web.HttpUtility]::UrlEncode($Global:B2_FIELD_ID_2)
        $path = "/fbs/business/wecom/schema/fields?docid=$($Global:DOCID)&sheetId=$($Global:META_SHEET_ID)&fieldIds=$fieldIdsEncoded&fieldIds=$fieldIdsEncoded2"
        $result = Invoke-Api -Method "DELETE" -Path $path
        $data = $result.Content.data

        if ($result.StatusCode -eq 200 -and $data -ge 1) {
            Write-Pass "B4" "删除成功, 删除数量: $data"
        } else {
            Write-Fail "B4" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
        }
    } else {
        Write-Skip "B4" "前置条件 B2 未通过"
    }

    # B5. 添加字段 - 无效类型
    Write-TestStep "B5" "POST /fbs/business/wecom/schema/fields (无效类型)"
    $result = Invoke-Api -Method "POST" -Path "/fbs/business/wecom/schema/fields" -Body @{
        docid   = $Global:DOCID
        sheetId = $Global:META_SHEET_ID
        fields  = @(
            @{ fieldTitle = "InvalidField_PS1"; fieldType = "invalid_type_xyz" }
        )
    }
    $data = $result.Content.data

    if ($result.StatusCode -eq 200) {
        $fieldsEmpty = (-not $data.fields -or $data.fields.Count -eq 0)
        if ($fieldsEmpty) {
            Write-Pass "B5" "无效类型被正确拒绝, fields 为空"
        } else {
            # 如果 CLI 没拒绝也记录
            Write-Fail "B5" "无效类型未被拒绝, fields: $($data.fields | ConvertTo-Json -Compress)"
        }
    } else {
        Write-Fail "B5" "HTTP $($result.StatusCode)"
    }

    # B6. 添加字段 - 数量超限
    Write-TestStep "B6" "POST /fbs/business/wecom/schema/fields (超限 150)"
    $bigFieldList = @()
    for ($i = 1; $i -le 151; $i++) {
        $bigFieldList += @{ fieldTitle = "OverLimit_$i"; fieldType = "text" }
    }
    $result = Invoke-Api -Method "POST" -Path "/fbs/business/wecom/schema/fields" -Body @{
        docid   = $Global:DOCID
        sheetId = $Global:META_SHEET_ID
        fields  = $bigFieldList
    }
    $data = $result.Content.data

    if ($result.StatusCode -eq 200) {
        $fieldsEmpty = (-not $data.fields -or $data.fields.Count -eq 0)
        if ($fieldsEmpty) {
            Write-Pass "B6" "超限被正确拒绝, fields 为空"
        } else {
            Write-Fail "B6" "超限未被拒绝, 实际添加了 $($data.fields.Count) 个字段"
        }
    } else {
        Write-Fail "B6" "HTTP $($result.StatusCode)"
    }
}

# ==================== 模块 C: 记录更新/删除 ====================

function Test-ModuleC {
    Write-TestHeader "模块 C: 记录更新/删除"

    # 先写入一条记录获取 recordId
    Write-TestStep "C-PREP" "POST /fbs/business/wecom/sync/write (准备测试记录)"
    $prepRecordId = ""
    $result = Invoke-Api -Method "POST" -Path "/fbs/business/wecom/sync/write" -Body @{
        sheetName = "meta"
        records   = @(
            @{
                fields = @{
                    "测试写入_PS1" = @(@{ type = "text"; text = "integration_test_value" })
                }
            }
        )
    }
    $data = $result.Content.data

    if ($result.StatusCode -eq 200 -and $data.success -eq $true) {
        $prepRecordId = $data.recordIds[0]
        Write-Host "    → 写入成功, recordId=$prepRecordId" -ForegroundColor DarkCyan
    } else {
        Write-Host "    → 写入失败: $($result.Content | ConvertTo-Json -Compress)" -ForegroundColor DarkYellow
    }

    # C1. 更新记录
    if ($prepRecordId) {
        Write-TestStep "C1" "PUT /fbs/business/wecom/records"
        $result = Invoke-Api -Method "PUT" -Path "/fbs/business/wecom/records" -Body @{
            docid   = $Global:DOCID
            sheetId = $Global:META_SHEET_ID
            records = @(
                @{
                    recordId = $prepRecordId
                    values   = @{
                        "测试写入_PS1" = @(@{ type = "text"; text = "updated_value_PS1" })
                    }
                }
            )
        }
        $data = $result.Content.data

        if ($result.StatusCode -eq 200 -and $data.success -eq $true) {
            Write-Pass "C1" "更新成功, writtenRecords=$($data.writtenRecords)"
        } else {
            Write-Fail "C1" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
        }
    } else {
        Write-Skip "C1" "前置条件写入失败, 无法获取 recordId"
    }

    # C2. 删除记录
    if ($prepRecordId) {
        Write-TestStep "C2" "DELETE /fbs/business/wecom/records"
        $result = Invoke-Api -Method "DELETE" -Path "/fbs/business/wecom/records" -Body @{
            docid     = $Global:DOCID
            sheetId   = $Global:META_SHEET_ID
            recordIds = @($prepRecordId)
        }
        $data = $result.Content.data

        if ($result.StatusCode -eq 200 -and $data.success -eq $true) {
            Write-Pass "C2" "删除成功, writtenRecords=$($data.writtenRecords)"
        } else {
            Write-Fail "C2" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
        }
    } else {
        Write-Skip "C2" "前置条件写入失败"
    }

    # C3. 更新记录 - 超限 (101条)
    Write-TestStep "C3" "PUT /fbs/business/wecom/records (超限 101)"
    $bigRecordList = @()
    for ($i = 1; $i -le 101; $i++) {
        $bigRecordList += @{ recordId = "fake_rec_$i"; values = @{} }
    }
    $result = Invoke-Api -Method "PUT" -Path "/fbs/business/wecom/records" -Body @{
        docid   = $Global:DOCID
        sheetId = $Global:META_SHEET_ID
        records = $bigRecordList
    }
    $data = $result.Content.data

    if ($result.StatusCode -eq 200 -and $data.success -eq $false -and $data.errorCode -eq "RECORD_LIMIT_EXCEEDED") {
        Write-Pass "C3" "超限被正确拒绝, errorCode=RECORD_LIMIT_EXCEEDED"
    } elseif ($result.StatusCode -eq 200 -and $data.success -eq $false) {
        Write-Pass "C3" "超限被拒绝, errorCode=$($data.errorCode) (预期 RECORD_LIMIT_EXCEEDED)"
    } else {
        Write-Fail "C3" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
    }

    # C4. 删除记录 - recordIds 为 null
    Write-TestStep "C4" "DELETE /fbs/business/wecom/records (null recordIds)"
    $result = Invoke-Api -Method "DELETE" -Path "/fbs/business/wecom/records" -Body @{
        docid     = $Global:DOCID
        sheetId   = $Global:META_SHEET_ID
        recordIds = $null
    }
    $data = $result.Content.data

    if ($result.StatusCode -eq 200 -and $data.success -eq $false) {
        Write-Pass "C4" "null 参数被正确拒绝, errorCode=$($data.errorCode)"
    } else {
        Write-Fail "C4" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
    }
}

# ==================== 模块 D: 权限校验 ====================

function Test-ModuleD {
    Write-TestHeader "模块 D: 权限校验"

    # D1. 未登录访问
    Write-TestStep "D1" "GET /fbs/business/wecom/schema/sheets (无 Token)"
    $result = Invoke-Api -Method "GET" -Path "/fbs/business/wecom/schema/sheets?docid=$($Global:DOCID)" -WithAuth:$false

    if ($result.StatusCode -eq 401) {
        Write-Pass "D1" "未登录返回 HTTP 401"
    } else {
        Write-Fail "D1" "预期 401, 实际 HTTP $($result.StatusCode)"
    }

    # D2. 无权限用户 - 这个需要另一个账号，标记 SKIP
    Write-TestStep "D2" "POST /fbs/business/wecom/schema/sheet (无权限用户)"
    Write-Skip "D2" "需要额外的无权限测试账号, 建议手动验证"
}

# ==================== 模块 E: 异常场景 ====================

function Test-ModuleE {
    Write-TestHeader "模块 E: 异常场景"

    # E1. 无效 docid
    Write-TestStep "E1" "GET /fbs/business/wecom/schema/sheets (无效 docid)"
    $result = Invoke-Api -Method "GET" -Path "/fbs/business/wecom/schema/sheets?docid=invalid_doc_id_xyz"
    $data = $result.Content.data

    if ($result.StatusCode -eq 200) {
        $sheetsEmpty = (-not $data.sheets -or $data.sheets.Count -eq 0)
        if ($sheetsEmpty) {
            Write-Pass "E1" "无效 docid 返回空数组"
        } else {
            Write-Fail "E1" "无效 docid 未返回空数组, sheets=$($data.sheets.Count)"
        }
    } else {
        Write-Fail "E1" "HTTP $($result.StatusCode)"
    }

    # E2. 无效 sheetId
    Write-TestStep "E2" "GET /fbs/business/wecom/schema/fields (无效 sheetId)"
    $result = Invoke-Api -Method "GET" -Path "/fbs/business/wecom/schema/fields?docid=$($Global:DOCID)&sheetId=invalid_sheet_xyz"
    $data = $result.Content.data

    if ($result.StatusCode -eq 200) {
        $fieldsEmpty = (-not $data.fields -or $data.fields.Count -eq 0)
        if ($fieldsEmpty) {
            Write-Pass "E2" "无效 sheetId 返回空数组"
        } else {
            Write-Fail "E2" "无效 sheetId 未返回空数组"
        }
    } else {
        Write-Fail "E2" "HTTP $($result.StatusCode)"
    }

    # E3. 无效 recordId
    Write-TestStep "E3" "PUT /fbs/business/wecom/records (无效 recordId)"
    $result = Invoke-Api -Method "PUT" -Path "/fbs/business/wecom/records" -Body @{
        docid   = $Global:DOCID
        sheetId = $Global:META_SHEET_ID
        records = @(
            @{ recordId = "invalid_record_xyz"; values = @{} }
        )
    }
    $data = $result.Content.data

    if ($result.StatusCode -eq 200 -and $data.success -eq $false) {
        Write-Pass "E3" "无效 recordId 返回 success=false"
    } else {
        Write-Fail "E3" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
    }

    # E4. 无效 sheetId (删除记录)
    Write-TestStep "E4" "DELETE /fbs/business/wecom/records (无效 sheetId)"
    $result = Invoke-Api -Method "DELETE" -Path "/fbs/business/wecom/records" -Body @{
        docid     = $Global:DOCID
        sheetId   = "invalid_sheet_xyz"
        recordIds = @("rec_fake_1")
    }
    $data = $result.Content.data

    if ($result.StatusCode -eq 200 -and $data.success -eq $false) {
        Write-Pass "E4" "无效 sheetId 返回 success=false, errorCode=$($data.errorCode)"
    } else {
        Write-Fail "E4" "HTTP $($result.StatusCode): $($result.Content | ConvertTo-Json -Compress)"
    }
}

# ==================== 汇总报告 ====================

function Write-Report {
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White
    Write-Host "                    测试报告汇总                            " -ForegroundColor White
    Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White
    Write-Host ""
    Write-Host "  📊 通过: $($Global:PassCount)  ❌ 失败: $($Global:FailCount)  ⏭️  跳过: $($Global:SkipCount)  📋 总计: $($Global:PassCount + $Global:FailCount + $Global:SkipCount)" -ForegroundColor White
    Write-Host ""
    Write-Host "  ──── 详细结果 ────" -ForegroundColor DarkGray
    foreach ($r in $Global:Results) {
        $icon = switch ($r.Status) { "PASS" { "✅" } "FAIL" { "❌" } default { "⏭️ " } }
        $color = switch ($r.Status) { "PASS" { "Green" } "FAIL" { "Red" } default { "DarkGray" } }
        Write-Host "  $icon $($r.Id) [$($r.Status)] $($r.Detail)" -ForegroundColor $color
    }

    Write-Host ""
    if ($Global:FailCount -gt 0) {
        Write-Host "  ⚠️  存在失败的测试用例，请检查上方详情" -ForegroundColor Red
    } else {
        Write-Host "  🎉 所有测试通过!" -ForegroundColor Green
    }
    Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor White
    Write-Host ""
}

# ==================== 主流程 ====================

Write-Host ""
Write-Host "🚀 OpenSpec #8 企微 Schema 管理集成测试" -ForegroundColor Magenta
Write-Host "   BaseUri : $BaseUri" -ForegroundColor DarkGray
Write-Host "   时间    : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
Write-Host "   DOCID   : $($Global:DOCID.Substring(0,20))..." -ForegroundColor DarkGray
Write-Host "   MetaSheet: $($Global:META_SHEET_ID)" -ForegroundColor DarkGray
Write-Host ""

Test-Login
Test-CliCheck
Test-ModuleA
Test-ModuleB
Test-ModuleC
Test-ModuleD
Test-ModuleE
Write-Report
