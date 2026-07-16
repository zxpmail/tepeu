# T1 注入消融重复 N 次（默认 7，用于与既往 n=3 合计 ≈10）
param([int]$Times = 7, [double]$MaxBudgetUsd = 1.5)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RepoRoot = (Resolve-Path (Join-Path $BenchRoot "..\..")).Path
$Parent = Split-Path $RepoRoot -Parent
$repo = Join-Path $Parent "tepeu-ate-experimental"
$apply = Join-Path $BenchRoot "scripts\apply-experimental.ps1"
$runOne = Join-Path $BenchRoot "scripts\run_one.py"
$task = Join-Path $BenchRoot "tasks\T1-qa-call-chain.json"
$prompt = Join-Path $BenchRoot "prompt\system-with-call-path.txt"
$genInject = Join-Path $BenchRoot "scripts\generate_call_path_inject.py"

if (-not (Test-Path $repo)) { throw "Missing $repo — run setup-fixtures.ps1 first" }

& python $genInject
if ($LASTEXITCODE -ne 0) { throw "generate_call_path_inject.py failed" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $BenchRoot "results\T1-inject-repeat-$stamp"
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
    $jsonLine = ($line | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1)
    if (-not $jsonLine) { throw "run_one failed: $line" }
    $obj = $jsonLine | ConvertFrom-Json
    $obj | Add-Member -NotePropertyName run -NotePropertyValue $i -Force
    $rows += $obj
    ($obj | ConvertTo-Json -Depth 5) | Set-Content -Encoding utf8 (Join-Path $runDir "run$i.json")
    Write-Host ("run={0} passed={1} turns={2} cost={3}" -f $i, $obj.passed, $obj.num_turns, $obj.total_cost_usd)
}

$turns = @($rows | ForEach-Object { $_.num_turns } | Where-Object { $_ -ne $null })
$avg = if ($turns.Count) { [math]::Round(($turns | Measure-Object -Average).Average, 2) } else { $null }
$min = if ($turns.Count) { ($turns | Measure-Object -Minimum).Minimum } else { $null }
$max = if ($turns.Count) { ($turns | Measure-Object -Maximum).Maximum } else { $null }
$variance = $null
$stdev = $null
if ($turns.Count -ge 2) {
    $mean = ($turns | Measure-Object -Average).Average
    $variance = ($turns | ForEach-Object { ($_ - $mean) * ($_ - $mean) } | Measure-Object -Average).Average
    $stdev = [math]::Round([math]::Sqrt($variance), 2)
}
$summary = [ordered]@{
    n = $Times
    turns = $turns
    avg_turns = $avg
    min_turns = $min
    max_turns = $max
    stdev_turns = $stdev
    baseline_turns_ref = 20
    passive_exp_turns_ref = 21
    first_inject_ref = 13
    prior_n3_turns = @(9, 17, 11)
}
$summaryPath = Join-Path $runDir "summary.json"
($summary | ConvertTo-Json -Depth 5) | Set-Content -Encoding utf8 $summaryPath
Write-Host "`nSUMMARY avg=$avg min=$min max=$max stdev=$stdev -> $summaryPath"
