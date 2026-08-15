# 定向补方差：同场 Baseline vs 条件 C（仅注册、无被动 md），T2+T3 ×N
# 用于检验 glm 上 A+B 红利是否来自注册 alone
# 例：powershell -File run-c-variance-repeat.ps1 -Times 3 -Model glm-5.2 -FromTepeuProvider anthropic -MaxBudgetUsd 3.0
param(
    [int]$Times = 3,
    [string]$TaskFilter = "T2,T3",
    [double]$MaxBudgetUsd = 1.5,
    [string]$Model = "",
    [string]$BaseUrl = "",
    [string]$AuthToken = "",
    [string]$FromTepeuProvider = ""
)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Parent = Split-Path (Resolve-Path (Join-Path $BenchRoot "..\..")).Path -Parent
$PromptFile = Join-Path $BenchRoot "prompt\system.txt"
$RunOne = Join-Path $PSScriptRoot "run_one.py"
$ApplyReg = Join-Path $PSScriptRoot "apply-registry-only.ps1"
$TasksDir = Join-Path $BenchRoot "tasks"

$SettingsFile = ""
if ($FromTepeuProvider) {
    $check = Join-Path $PSScriptRoot "tepeu_provider_env.py"
    & python $check --provider-id $FromTepeuProvider --check-only
    if ($LASTEXITCODE -ne 0) { throw "tepeu provider check failed" }
    if (-not $Model) {
        $meta = & python $check --provider-id $FromTepeuProvider --check-only
        foreach ($line in $meta) {
            if ($line -match '^model=(.+)$') { $Model = $Matches[1] }
        }
    }
    $SettingsFile = Join-Path $env:TEMP ("ate-settings-" + [guid]::NewGuid().ToString() + ".json")
    $mk = Join-Path $PSScriptRoot "make_claude_settings.py"
    & python $mk --provider-id $FromTepeuProvider --model $Model --out $SettingsFile
    if ($LASTEXITCODE -ne 0) { throw "make_claude_settings.py failed" }
    Write-Host "Using Tepeu provider '$FromTepeuProvider' via --bare --settings (model=$Model)"
}

$Variants = @(
    @{ Name = "baseline"; Root = (Join-Path $Parent "tepeu-ate-baseline"); Apply = $null },
    @{ Name = "registry-only"; Root = (Join-Path $Parent "tepeu-ate-experimental"); Apply = $ApplyReg }
)

$parts = @($TaskFilter -split "[|,]" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$tasks = @(Get-ChildItem $TasksDir -Filter "*.json" | Sort-Object Name | Where-Object {
    $t = $_; $ok = $false
    foreach ($p in $parts) { if ($t.BaseName -like "*$p*" -or $t.Name -like "*$p*") { $ok = $true; break } }
    $ok
})
if ($tasks.Count -lt 1) { throw "No tasks matched $TaskFilter" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$safeModel = if ($Model) { ($Model -replace "[^a-zA-Z0-9._-]", "_") } else { "default" }
$runDir = Join-Path $BenchRoot "results\C-variance-repeat-$safeModel-$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Results -> $runDir (Times=$Times filter=$TaskFilter model=$(if ($Model) { $Model } else { 'CLI-default' }))"

$allRows = @()
foreach ($variant in $Variants) {
    $repo = $variant.Root
    if (-not (Test-Path $repo)) { throw "Missing $repo — run setup-fixtures.ps1" }
    for ($i = 1; $i -le $Times; $i++) {
        foreach ($taskFile in $tasks) {
            $task = Get-Content -Raw -Encoding UTF8 $taskFile.FullName | ConvertFrom-Json
            $taskId = [string]$task.id
            Write-Host "`n=== $($variant.Name) run $i / $Times / $taskId ==="
            git -C $repo reset --hard HEAD | Out-Null
            git -C $repo clean -fd | Out-Null
            if ($variant.Apply) { & $variant.Apply -RepoRoot $repo }

            $agentLog = Join-Path $runDir ("$($variant.Name)-run$i--$taskId.agent.json")
            $prevEap = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            $runArgs = @(
                "--repo", $repo,
                "--task", $taskFile.FullName,
                "--system-prompt-file", $PromptFile,
                "--agent-log", $agentLog,
                "--max-budget-usd", "$MaxBudgetUsd",
                "--variant", $variant.Name
            )
            if ($Model) { $runArgs += @("--model", $Model) }
            if ($BaseUrl) { $runArgs += @("--base-url", $BaseUrl) }
            if ($AuthToken) { $runArgs += @("--auth-token", $AuthToken) }
            if ($SettingsFile) { $runArgs += @("--bare", "--settings-file", $SettingsFile) }
            $line = & python $RunOne @runArgs 2>&1
            $ErrorActionPreference = $prevEap

            $jsonLine = ($line | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1)
            if (-not $jsonLine) { throw "run_one failed: $line" }
            $obj = $jsonLine | ConvertFrom-Json
            $obj | Add-Member -NotePropertyName run -NotePropertyValue $i -Force
            $obj | Add-Member -NotePropertyName variant -NotePropertyValue $variant.Name -Force
            if ($Model) { $obj | Add-Member -NotePropertyName requested_model -NotePropertyValue $Model -Force }
            $allRows += $obj
            ($obj | ConvertTo-Json -Depth 5) | Set-Content -Encoding utf8 (Join-Path $runDir "$($variant.Name)-run$i--$taskId.json")
            Write-Host ("variant={0} run={1} task={2} passed={3} turns={4} model={5} `$={6}" -f `
                $variant.Name, $i, $taskId, $obj.passed, $obj.num_turns, $obj.model, $obj.total_cost_usd)
        }
    }
}

function Stats($turns) {
    $t = @($turns | Where-Object { $_ -ne $null })
    if ($t.Count -eq 0) {
        return @{ n = 0; turns = @(); avg = $null; min = $null; max = $null; stdev = $null }
    }
    $mean = ($t | Measure-Object -Average).Average
    $stdev = $null
    if ($t.Count -ge 2) {
        $var = ($t | ForEach-Object { ($_ - $mean) * ($_ - $mean) } | Measure-Object -Average).Average
        $stdev = [math]::Round([math]::Sqrt($var), 2)
    }
    return @{
        n     = $t.Count
        turns = $t
        avg   = [math]::Round($mean, 2)
        min   = ($t | Measure-Object -Minimum).Minimum
        max   = ($t | Measure-Object -Maximum).Maximum
        stdev = $stdev
    }
}

$pairSums = @{ baseline = @(); "registry-only" = @() }
for ($i = 1; $i -le $Times; $i++) {
    foreach ($vName in @("baseline", "registry-only")) {
        $pair = @($allRows | Where-Object { $_.variant -eq $vName -and $_.run -eq $i -and -not $_.is_error })
        $t2 = $pair | Where-Object { $_.task_id -like "*T2*" } | Select-Object -First 1
        $t3 = $pair | Where-Object { $_.task_id -like "*T3*" } | Select-Object -First 1
        if ($t2 -and $t3 -and $t2.num_turns -gt 1 -and $t3.num_turns -gt 1) {
            $pairSums[$vName] += ($t2.num_turns + $t3.num_turns)
        }
    }
}

$baseT2 = Stats @($allRows | Where-Object { $_.variant -eq "baseline" -and $_.task_id -like "*T2*" -and (-not $_.is_error) } | ForEach-Object { $_.num_turns })
$baseT3 = Stats @($allRows | Where-Object { $_.variant -eq "baseline" -and $_.task_id -like "*T3*" -and (-not $_.is_error) } | ForEach-Object { $_.num_turns })
$cT2 = Stats @($allRows | Where-Object { $_.variant -eq "registry-only" -and $_.task_id -like "*T2*" -and (-not $_.is_error) } | ForEach-Object { $_.num_turns })
$cT3 = Stats @($allRows | Where-Object { $_.variant -eq "registry-only" -and $_.task_id -like "*T3*" -and (-not $_.is_error) } | ForEach-Object { $_.num_turns })
$baseSum = Stats $pairSums.baseline
$cSum = Stats $pairSums["registry-only"]

$deltaPct = $null
if ($baseSum.avg -and $cSum.avg -and $baseSum.avg -ne 0) {
    $deltaPct = [math]::Round(100.0 * ($cSum.avg - $baseSum.avg) / $baseSum.avg, 1)
}

$summary = [ordered]@{
    times                   = $Times
    task_filter             = $TaskFilter
    model                   = $(if ($Model) { $Model } else { "CLI-default" })
    from_tepeu_provider     = $FromTepeuProvider
    baseline_t2             = $baseT2
    baseline_t3             = $baseT3
    registry_only_t2        = $cT2
    registry_only_t3        = $cT3
    baseline_t2t3_sum       = $baseSum
    registry_only_t2t3_sum  = $cSum
    mean_delta_pct_on_sum   = $deltaPct
    glm_ab_variance_ref     = @{
        mean_delta_pct_on_sum = -24.6
        dir = "AB-variance-repeat-glm-5.2-20260716-164857"
        note = "compare: if C Δ ≈ A+B Δ then registry drives glm gain; if C ≈ flat then md/channel preference supported"
    }
}
$summaryPath = Join-Path $runDir "summary.json"
($summary | ConvertTo-Json -Depth 6) | Set-Content -Encoding utf8 $summaryPath

Write-Host "`n=== C variance repeat summary (model=$(if ($Model) { $Model } else { 'CLI-default' })) ==="
Write-Host ("baseline       T2={0} T3={1} sum={2} (avg={3})" -f ($baseT2.turns -join "/"), ($baseT3.turns -join "/"), ($baseSum.turns -join "/"), $baseSum.avg)
Write-Host ("registry-only  T2={0} T3={1} sum={2} (avg={3})" -f ($cT2.turns -join "/"), ($cT3.turns -join "/"), ($cSum.turns -join "/"), $cSum.avg)
Write-Host ("mean Δ% on T2+T3 sum ≈ {0}% (glm A+B ref ≈ -24.6%)" -f $deltaPct)
Write-Host "RUN_DIR=$runDir"
Write-Host "SUMMARY=$summaryPath"
