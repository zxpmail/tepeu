# 定向补方差：同场 Baseline vs A+B，仅 T2+T3，各重复 N 次
# 例：
#   powershell -File run-ab-variance-repeat.ps1 -Times 3
#   powershell -File run-ab-variance-repeat.ps1 -Times 3 -Model glm-5.2 -FromTepeuProvider anthropic -MaxBudgetUsd 3.0
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
$ApplyExp = Join-Path $PSScriptRoot "apply-experimental.ps1"
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
    @{ Name = "experimental"; Root = (Join-Path $Parent "tepeu-ate-experimental"); Apply = $ApplyExp }
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
$runDir = Join-Path $BenchRoot "results\AB-variance-repeat-$safeModel-$stamp"
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
    $t = @($turns)
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

# 同 run 内 T2+T3 合计（配对场次）
$pairSums = @{ baseline = @(); experimental = @() }
for ($i = 1; $i -le $Times; $i++) {
    foreach ($vName in @("baseline", "experimental")) {
        $pair = @($allRows | Where-Object { $_.variant -eq $vName -and $_.run -eq $i })
        $sum = ($pair | ForEach-Object { $_.num_turns } | Where-Object { $_ -ne $null } | Measure-Object -Sum).Sum
        if ($null -ne $sum) { $pairSums[$vName] += $sum }
    }
}

$baseT2 = Stats @($allRows | Where-Object { $_.variant -eq "baseline" -and $_.task_id -like "*T2*" } | ForEach-Object { $_.num_turns })
$baseT3 = Stats @($allRows | Where-Object { $_.variant -eq "baseline" -and $_.task_id -like "*T3*" } | ForEach-Object { $_.num_turns })
$expT2 = Stats @($allRows | Where-Object { $_.variant -eq "experimental" -and $_.task_id -like "*T2*" } | ForEach-Object { $_.num_turns })
$expT3 = Stats @($allRows | Where-Object { $_.variant -eq "experimental" -and $_.task_id -like "*T3*" } | ForEach-Object { $_.num_turns })
$baseSum = Stats $pairSums.baseline
$expSum = Stats $pairSums.experimental

$deltaPct = $null
if ($baseSum.avg -and $expSum.avg -and $baseSum.avg -ne 0) {
    $deltaPct = [math]::Round(100.0 * ($expSum.avg - $baseSum.avg) / $baseSum.avg, 1)
}

$summary = [ordered]@{
    times              = $Times
    task_filter        = $TaskFilter
    model              = $(if ($Model) { $Model } else { "CLI-default" })
    from_tepeu_provider = $FromTepeuProvider
    baseline_t2        = $baseT2
    baseline_t3        = $baseT3
    experimental_t2    = $expT2
    experimental_t3    = $expT3
    baseline_t2t3_sum  = $baseSum
    experimental_t2t3_sum = $expSum
    mean_delta_pct_on_sum = $deltaPct
    flash_variance_ref = @{
        mean_delta_pct_on_sum = -3.9
        dir = "AB-variance-repeat-20260716-163146"
        note = "deepseek-v4-flash ×3; compare cross-model"
    }
}
$summaryPath = Join-Path $runDir "summary.json"
($summary | ConvertTo-Json -Depth 6) | Set-Content -Encoding utf8 $summaryPath

Write-Host "`n=== AB variance repeat summary (model=$(if ($Model) { $Model } else { 'CLI-default' })) ==="
Write-Host ("baseline     T2={0} (avg={1}) T3={2} (avg={3}) sum={4} (avg={5} stdev={6})" -f `
    ($baseT2.turns -join "/"), $baseT2.avg, ($baseT3.turns -join "/"), $baseT3.avg, `
    ($baseSum.turns -join "/"), $baseSum.avg, $baseSum.stdev)
Write-Host ("experimental T2={0} (avg={1}) T3={2} (avg={3}) sum={4} (avg={5} stdev={6})" -f `
    ($expT2.turns -join "/"), $expT2.avg, ($expT3.turns -join "/"), $expT3.avg, `
    ($expSum.turns -join "/"), $expSum.avg, $expSum.stdev)
Write-Host ("mean Δ% on T2+T3 sum ≈ {0}% (flash ref ≈ -3.9%)" -f $deltaPct)
Write-Host "RUN_DIR=$runDir"
Write-Host "SUMMARY=$summaryPath"
