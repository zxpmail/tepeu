# T1 注入消融重复 N 次，输出每次 turns 与汇总统计
param([int]$Times = 3, [double]$MaxBudgetUsd = 1.5)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$repo = "E:\work\tepeu-ate-experimental"
$bench = "E:\work\tepeu\experiments\ate-bench"
$apply = Join-Path $bench "scripts\apply-experimental.ps1"
$runOne = Join-Path $bench "scripts\run_one.py"
$task = Join-Path $bench "tasks\T1-qa-call-chain.json"
$prompt = Join-Path $bench "prompt\system-with-call-path.txt"

if (-not (Test-Path $repo)) { throw "Missing $repo" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $bench "results\T1-inject-repeat-$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Results -> $runDir"

$rows = @()
for ($i = 1; $i -le $Times; $i++) {
    Write-Host "`n=== inject run $i / $Times ==="
    git -C $repo reset --hard HEAD | Out-Null
    git -C $repo clean -fd | Out-Null
    & $apply -RepoRoot $repo
    $agentLog = Join-Path $runDir ("run$i.agent.json")
    $line = & python $runOne --repo $repo --task $task --system-prompt-file $prompt --agent-log $agentLog --max-budget-usd $MaxBudgetUsd
    $obj = $line | ConvertFrom-Json
    $obj | Add-Member -NotePropertyName run -NotePropertyValue $i -Force
    $rows += $obj
    ($obj | ConvertTo-Json -Depth 5) | Set-Content -Encoding utf8 (Join-Path $runDir "run$i.json")
    Write-Host ("run={0} passed={1} turns={2} cost={3}" -f $i, $obj.passed, $obj.num_turns, $obj.total_cost_usd)
}

$turns = @($rows | ForEach-Object { $_.num_turns } | Where-Object { $_ -ne $null })
$avg = if ($turns.Count) { ($turns | Measure-Object -Average).Average } else { $null }
$min = if ($turns.Count) { ($turns | Measure-Object -Minimum).Minimum } else { $null }
$max = if ($turns.Count) { ($turns | Measure-Object -Maximum).Maximum } else { $null }
$summary = [ordered]@{
    n = $Times
    turns = $turns
    avg_turns = $avg
    min_turns = $min
    max_turns = $max
    baseline_turns_ref = 20
    passive_exp_turns_ref = 21
    first_inject_ref = 13
}
$summaryPath = Join-Path $runDir "summary.json"
($summary | ConvertTo-Json -Depth 5) | Set-Content -Encoding utf8 $summaryPath
Write-Host "`nSUMMARY avg=$avg min=$min max=$max -> $summaryPath"
