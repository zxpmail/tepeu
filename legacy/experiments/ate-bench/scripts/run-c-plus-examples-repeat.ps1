# H1：C + 工具样例注入，对 T3（或指定任务）重复 N 次
param(
    [int]$Times = 3,
    [string]$TaskFilter = "T3",
    [double]$MaxBudgetUsd = 1.5
)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Parent = Split-Path (Resolve-Path (Join-Path $BenchRoot "..\..")).Path -Parent
$repo = Join-Path $Parent "tepeu-ate-experimental"
$ApplyReg = Join-Path $PSScriptRoot "apply-registry-only.ps1"
$RunOne = Join-Path $PSScriptRoot "run_one.py"
$PromptFile = Join-Path $BenchRoot "prompt\system-with-tool-examples.txt"
$TasksDir = Join-Path $BenchRoot "tasks"

if (-not (Test-Path $repo)) { throw "Missing $repo — run setup-fixtures.ps1" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $BenchRoot "results\C-plus-examples-repeat-$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Results -> $runDir (repeat=$Times filter=$TaskFilter)"

$tasks = Get-ChildItem $TasksDir -Filter "*.json" | Sort-Object Name
$parts = @($TaskFilter -split "[|,]" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$tasks = @($tasks | Where-Object {
    $t = $_; $ok = $false
    foreach ($p in $parts) { if ($t.BaseName -like "*$p*" -or $t.Name -like "*$p*") { $ok = $true; break } }
    $ok
})
if ($tasks.Count -lt 1) { throw "No tasks matched $TaskFilter" }

$allRows = @()
for ($i = 1; $i -le $Times; $i++) {
    Write-Host "`n=== repeat $i / $Times ==="
    foreach ($taskFile in $tasks) {
        $task = Get-Content -Raw -Encoding UTF8 $taskFile.FullName | ConvertFrom-Json
        $taskId = [string]$task.id
        git -C $repo reset --hard HEAD | Out-Null
        git -C $repo clean -fd | Out-Null
        & $ApplyReg -RepoRoot $repo
        $agentLog = Join-Path $runDir ("run$i--$taskId.agent.json")
        $line = & python $RunOne --repo $repo --task $taskFile.FullName --system-prompt-file $PromptFile --agent-log $agentLog --max-budget-usd $MaxBudgetUsd 2>&1
        $jsonLine = ($line | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1)
        if (-not $jsonLine) { throw "run_one failed: $line" }
        $obj = $jsonLine | ConvertFrom-Json
        $obj | Add-Member -NotePropertyName run -NotePropertyValue $i -Force
        $obj | Add-Member -NotePropertyName variant -NotePropertyValue "c-plus-examples" -Force
        $allRows += $obj
        ($obj | ConvertTo-Json -Depth 5) | Set-Content -Encoding utf8 (Join-Path $runDir "run$i--$taskId.json")
        Write-Host ("run={0} task={1} passed={2} turns={3} cost={4}" -f $i, $taskId, $obj.passed, $obj.num_turns, $obj.total_cost_usd)
    }
}

$t3 = @($allRows | Where-Object { $_.task_id -like "*T3*" } | ForEach-Object { $_.num_turns } | Where-Object { $_ -ne $null })
# also include first single-run ref 10 if we want combined later — keep this batch only
$avg = if ($t3.Count) { [math]::Round(($t3 | Measure-Object -Average).Average, 2) } else { $null }
$min = if ($t3.Count) { ($t3 | Measure-Object -Minimum).Minimum } else { $null }
$max = if ($t3.Count) { ($t3 | Measure-Object -Maximum).Maximum } else { $null }
$stdev = $null
if ($t3.Count -ge 2) {
    $mean = ($t3 | Measure-Object -Average).Average
    $var = ($t3 | ForEach-Object { ($_ - $mean) * ($_ - $mean) } | Measure-Object -Average).Average
    $stdev = [math]::Round([math]::Sqrt($var), 2)
}
$summary = [ordered]@{
    n = $Times
    task_filter = $TaskFilter
    t3_turns = $t3
    t3_avg = $avg
    t3_min = $min
    t3_max = $max
    t3_stdev = $stdev
    c_alone_t3_ref = 13
    c_plus_first_t3_ref = 10
    baseline_c_field_t3_ref = 11
}
$summaryPath = Join-Path $runDir "summary.json"
($summary | ConvertTo-Json -Depth 5) | Set-Content -Encoding utf8 $summaryPath
Write-Host "`nSUMMARY T3 avg=$avg min=$min max=$max stdev=$stdev -> $summaryPath"
