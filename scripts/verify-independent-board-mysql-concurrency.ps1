[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Database,
    [string]$LoginPath = "fbsir-local",
    [string]$MySqlExe = "mysql.exe"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($LoginPath -notmatch '^[A-Za-z0-9_.-]+$') {
    throw "LoginPath may contain only letters, numbers, dot, underscore, and dash."
}
if ($Database -notmatch '^[A-Za-z0-9_]{1,64}$') {
    throw "Database must be 1-64 characters and contain only letters, numbers, and underscore."
}
if ($Database -notmatch '(?i)(^|_)scratch(_|$)') {
    throw "Concurrency verification is destructive test traffic and may run only against a database whose name contains the standalone token 'scratch'."
}

$mysqlCommand = Get-Command $MySqlExe -ErrorAction Stop
$mysqlPath = $mysqlCommand.Source
$workerCount = 32
$testToken = [guid]::NewGuid().ToString("N")
$testMarker = "IBCONC_$testToken"
$productCode = "IBT_$testToken"
$metricCode = "MT_$testToken"
$bucketDate = "2099-12-31"
$numberSeed = [Convert]::ToInt64($testToken.Substring(0, 12), 16) % 100000000000
$enterpriseBase = 700000000000 + $numberSeed
$memberBase = 800000000000 + $numberSeed
$tenantBudget = $enterpriseBase + 1
$tenantInsert = $enterpriseBase + 2
$tenantCrossA = $enterpriseBase + 3
$tenantCrossB = $enterpriseBase + 4
$memberBudget = $memberBase + 1
$memberInsert = $memberBase + 2
$memberCrossA = $memberBase + 3
$memberCrossB = $memberBase + 4

function Get-Sha256Hex {
    param([Parameter(Mandatory = $true)][string]$Value)

    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function New-MySqlStartInfo {
    $arguments = "--login-path=$LoginPath --default-character-set=utf8mb4 --batch --raw --skip-column-names --database=$Database"
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlPath
    $startInfo.Arguments = $arguments
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    return $startInfo
}

function Start-MySqlSql {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = New-MySqlStartInfo
    if (-not $process.Start()) {
        $process.Dispose()
        throw "Unable to start mysql worker '$Name'."
    }
    try {
        $process.StandardInput.Write($Sql)
        $process.StandardInput.Close()
    }
    catch {
        $process.Dispose()
        throw
    }
    return [pscustomobject]@{
        Name = $Name
        Process = $process
    }
}

function Complete-MySqlSql {
    param([Parameter(Mandatory = $true)]$Worker)

    $process = $Worker.Process
    try {
        $stdout = $process.StandardOutput.ReadToEnd().Trim()
        $stderr = $process.StandardError.ReadToEnd().Trim()
        $process.WaitForExit()
        return [pscustomobject]@{
            Name = $Worker.Name
            ExitCode = $process.ExitCode
            Stdout = $stdout
            Stderr = $stderr
        }
    }
    finally {
        $process.Dispose()
    }
}

function Invoke-MySqlSql {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$Name = "mysql"
    )

    $result = Complete-MySqlSql -Worker (Start-MySqlSql -Sql $Sql -Name $Name)
    if ($result.ExitCode -ne 0) {
        throw "mysql '$Name' exited with code $($result.ExitCode): $($result.Stderr)"
    }
    return $result.Stdout
}

function Invoke-ConcurrentSql {
    param(
        [Parameter(Mandatory = $true)][int]$Count,
        [Parameter(Mandatory = $true)][scriptblock]$SqlFactory,
        [Parameter(Mandatory = $true)][string]$NamePrefix
    )

    $workers = [System.Collections.Generic.List[object]]::new()
    try {
        for ($index = 0; $index -lt $Count; $index++) {
            $workers.Add((Start-MySqlSql -Sql (& $SqlFactory $index) -Name "$NamePrefix-$index"))
        }

        $results = [System.Collections.Generic.List[object]]::new()
        foreach ($worker in $workers) {
            $results.Add((Complete-MySqlSql -Worker $worker))
        }
        $workers.Clear()
        return @($results)
    }
    finally {
        foreach ($worker in $workers) {
            try {
                if (-not $worker.Process.HasExited) {
                    $worker.Process.Kill()
                    $worker.Process.WaitForExit()
                }
            }
            finally {
                $worker.Process.Dispose()
            }
        }
    }
}

function Assert-ZeroTestRows {
    $remaining = Invoke-MySqlSql -Name "cleanup-readback" -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM fbs_usage_operation
   WHERE product_code = '$productCode'
     AND enterprise_id IN ($tenantBudget,$tenantInsert,$tenantCrossA,$tenantCrossB)),
  (SELECT COUNT(*) FROM fbs_usage_budget
   WHERE product_code = '$productCode'
     AND enterprise_id IN ($tenantBudget,$tenantInsert,$tenantCrossA,$tenantCrossB))
);
"@
    if ($remaining -ne "0|0") {
        throw "Marker-scoped cleanup verification failed for '$testMarker': '$remaining'."
    }
}

$cleanupRequired = $false
try {
    $preflight = Invoke-MySqlSql -Name "preflight" -Sql @"
SELECT CONCAT_WS('|',
  VERSION(),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = 'public_init_028' AND description LIKE 'APPLIED:%'),
  (SELECT COUNT(*) FROM u3w_schema_migration
   WHERE version = '20260720_independent_board_control_plane_v1'
     AND description = 'Independent Board generic product plan, entitlement, budget, operation and receipt control plane'),
  (SELECT COUNT(*) FROM information_schema.tables
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_product_plan','fbs_product_entitlement','fbs_usage_budget','fbs_usage_operation','fbs_entitlement_receipt')
     AND engine = 'InnoDB'),
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name IN ('fbs_product_plan','fbs_product_entitlement','fbs_usage_budget','fbs_usage_operation','fbs_entitlement_receipt')),
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'fbs_usage_operation'
     AND index_name = 'uk_usage_operation_enterprise_operation'
     AND non_unique = 0)
);
"@
    $preflightParts = @($preflight.Split('|'))
    if ($preflightParts.Count -ne 6 -or $preflightParts[0] -notmatch '^8\.' -or
        $preflightParts[1] -ne '1' -or $preflightParts[2] -ne '1' -or
        $preflightParts[3] -ne '5' -or $preflightParts[4] -ne '67' -or
        $preflightParts[5] -ne '2') {
        throw "Target '$Database' is not a MySQL 8 scratch database with a passing 028 current-read contract: '$preflight'."
    }

    $cleanupRequired = $true
    Invoke-MySqlSql -Name "setup-budget" -Sql @"
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
INSERT INTO fbs_usage_budget
  (enterprise_id, member_id, product_code, metric_code, bucket_date, daily_limit, reserved_count, used_count, version)
VALUES
  ($tenantBudget, $memberBudget, '$productCode', '$metricCode', '$bucketDate', 5, 0, 0, 0);
COMMIT;
"@ | Out-Null

    $updateResults = Invoke-ConcurrentSql -Count $workerCount -NamePrefix "budget" -SqlFactory {
        param($index)
        @"
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
DO SLEEP(0.35);
START TRANSACTION;
UPDATE fbs_usage_budget
SET reserved_count = reserved_count + 1,
    version = version + 1
WHERE enterprise_id = $tenantBudget
  AND member_id = $memberBudget
  AND product_code = '$productCode'
  AND metric_code = '$metricCode'
  AND bucket_date = '$bucketDate'
  AND reserved_count + used_count + 1 <= daily_limit;
SELECT ROW_COUNT();
COMMIT;
"@
    }
    $updateFailures = @($updateResults | Where-Object { $_.ExitCode -ne 0 })
    if ($updateFailures.Count -ne 0) {
        throw "Conditional UPDATE worker failed: $($updateFailures[0].Name): $($updateFailures[0].Stderr)"
    }
    $updateCounts = @($updateResults | ForEach-Object {
        $lines = @($_.Stdout -split "`r?`n" | Where-Object { $_ -ne '' })
        if ($lines.Count -ne 1 -or $lines[0] -notmatch '^[01]$') {
            throw "Conditional UPDATE worker '$($_.Name)' returned an invalid ROW_COUNT: '$($_.Stdout)'."
        }
        [int]$lines[0]
    })
    $updateSuccessCount = @($updateCounts | Where-Object { $_ -eq 1 }).Count
    $budgetReadback = Invoke-MySqlSql -Name "budget-readback" -Sql @"
SELECT CONCAT_WS('|', daily_limit, reserved_count, used_count, version)
FROM fbs_usage_budget
WHERE enterprise_id = $tenantBudget
  AND member_id = $memberBudget
  AND product_code = '$productCode'
  AND metric_code = '$metricCode'
  AND bucket_date = '$bucketDate';
"@
    if ($updateSuccessCount -ne 5 -or $budgetReadback -ne "5|5|0|5") {
        throw "Conditional UPDATE invariant failed: successes=$updateSuccessCount, readback='$budgetReadback'."
    }

    $sharedOperationId = "${testMarker}_SAME_TENANT"
    $sharedDigest = Get-Sha256Hex "$testMarker|same-tenant"
    $insertResults = Invoke-ConcurrentSql -Count $workerCount -NamePrefix "insert" -SqlFactory {
        param($index)
        @"
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
DO SLEEP(0.35);
START TRANSACTION;
INSERT INTO fbs_usage_operation
  (operation_id, request_digest, enterprise_id, member_id, user_id, product_code, metric_code,
   bucket_date, units, status, effective_plan_code, agenda_count, seat_count, remaining_count)
VALUES
  ('$sharedOperationId', '$sharedDigest', $tenantInsert, $memberInsert, $memberInsert,
   '$productCode', '$metricCode', '$bucketDate', 1, 'PENDING', 'TEST_PLAN', 1, 1, 0);
COMMIT;
"@
    }
    $insertSuccesses = @($insertResults | Where-Object { $_.ExitCode -eq 0 })
    $insertDuplicates = @($insertResults | Where-Object {
        $_.ExitCode -ne 0 -and $_.Stderr -match '(?i)(ERROR\s+1062|Duplicate entry)'
    })
    $insertUnexpected = @($insertResults | Where-Object {
        $_.ExitCode -ne 0 -and $_.Stderr -notmatch '(?i)(ERROR\s+1062|Duplicate entry)'
    })
    $sameTenantReadback = Invoke-MySqlSql -Name "same-tenant-readback" -Sql @"
SELECT COUNT(*) FROM fbs_usage_operation
WHERE enterprise_id = $tenantInsert AND operation_id = '$sharedOperationId' AND product_code = '$productCode';
"@
    if ($insertSuccesses.Count -ne 1 -or $insertDuplicates.Count -ne 31 -or
        $insertUnexpected.Count -ne 0 -or $sameTenantReadback -ne '1') {
        throw "Same-tenant INSERT invariant failed: success=$($insertSuccesses.Count), duplicate=$($insertDuplicates.Count), unexpected=$($insertUnexpected.Count), rows='$sameTenantReadback'."
    }

    $crossOperationId = "${testMarker}_CROSS_TENANT"
    $crossDigest = Get-Sha256Hex "$testMarker|cross-tenant"
    $crossTenants = @($tenantCrossA, $tenantCrossB)
    $crossMembers = @($memberCrossA, $memberCrossB)
    $crossResults = Invoke-ConcurrentSql -Count 2 -NamePrefix "cross-tenant" -SqlFactory {
        param($index)
        $tenant = $crossTenants[$index]
        $member = $crossMembers[$index]
        @"
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
DO SLEEP(0.35);
START TRANSACTION;
INSERT INTO fbs_usage_operation
  (operation_id, request_digest, enterprise_id, member_id, user_id, product_code, metric_code,
   bucket_date, units, status, effective_plan_code, agenda_count, seat_count, remaining_count)
VALUES
  ('$crossOperationId', '$crossDigest', $tenant, $member, $member,
   '$productCode', '$metricCode', '$bucketDate', 1, 'PENDING', 'TEST_PLAN', 1, 1, 0);
COMMIT;
"@
    }
    $crossFailures = @($crossResults | Where-Object { $_.ExitCode -ne 0 })
    $crossReadback = Invoke-MySqlSql -Name "cross-tenant-readback" -Sql @"
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM fbs_usage_operation WHERE enterprise_id = $tenantCrossA AND operation_id = '$crossOperationId' AND product_code = '$productCode'),
  (SELECT COUNT(*) FROM fbs_usage_operation WHERE enterprise_id = $tenantCrossB AND operation_id = '$crossOperationId' AND product_code = '$productCode')
);
"@
    if ($crossFailures.Count -ne 0 -or $crossReadback -ne '1|1') {
        $firstCrossError = if ($crossFailures.Count -gt 0) { $crossFailures[0].Stderr } else { "none" }
        throw "Cross-tenant INSERT invariant failed: failed=$($crossFailures.Count), rows='$crossReadback', firstError='$firstCrossError'."
    }

    [pscustomobject]@{
        schemaVersion = 1
        status = "PASS"
        database = $Database
        testMarker = $testMarker
        mysqlVersion = $preflightParts[0]
        conditionalUpdate = [pscustomobject]@{
            workers = $workerCount
            successCount = $updateSuccessCount
            rejectedCount = $workerCount - $updateSuccessCount
            dailyLimit = 5
            reservedCount = 5
        }
        sameTenantInsert = [pscustomobject]@{
            workers = $workerCount
            successCount = $insertSuccesses.Count
            duplicateCount = $insertDuplicates.Count
        }
        crossTenantInsert = [pscustomobject]@{
            tenantCount = 2
            successCount = 2
        }
        cleanupScope = "exact marker product and generated tenant ids"
    } | ConvertTo-Json -Depth 5 -Compress
}
finally {
    if ($cleanupRequired) {
        Invoke-MySqlSql -Name "cleanup" -Sql @"
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
DELETE FROM fbs_usage_operation
WHERE product_code = '$productCode'
  AND enterprise_id IN ($tenantBudget,$tenantInsert,$tenantCrossA,$tenantCrossB);
DELETE FROM fbs_usage_budget
WHERE product_code = '$productCode'
  AND enterprise_id IN ($tenantBudget,$tenantInsert,$tenantCrossA,$tenantCrossB);
COMMIT;
"@ | Out-Null
        Assert-ZeroTestRows
    }
}
