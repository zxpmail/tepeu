# H1 后续：条件 C（只注册）+ system 注入短工具样例（无 AGENT_CALL_PATH.md）
# 默认跑 T2|T3，与同日 C / A+B 对照。
param(
    [string]$TaskFilter = "T2|T3",
    [double]$MaxBudgetUsd = 1.5
)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RepoRoot = (Resolve-Path (Join-Path $BenchRoot "..\..")).Path
$Parent = Split-Path $RepoRoot -Parent
$ExperimentalRoot = Join-Path $Parent "tepeu-ate-experimental"
$BaselineRoot = Join-Path $Parent "tepeu-ate-baseline"
$ApplyReg = Join-Path $PSScriptRoot "apply-registry-only.ps1"
$RunOne = Join-Path $PSScriptRoot "run_one.py"
$PromptFile = Join-Path $BenchRoot "prompt\system-with-tool-examples.txt"
$TasksDir = Join-Path $BenchRoot "tasks"
$ResultsDir = Join-Path $BenchRoot "results"

if (-not (Test-Path $ExperimentalRoot)) { throw "Missing $ExperimentalRoot — run setup-fixtures.ps1" }
if (-not (Test-Path $PromptFile)) { throw "Missing $PromptFile" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $ResultsDir "C-plus-examples-$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Results -> $runDir (C + tool-examples inject)"

$tasks = Get-ChildItem $TasksDir -Filter "*.json" | Sort-Object Name
$parts = @($TaskFilter -split "[|,]" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$tasks = $tasks | Where-Object {
    $t = $_; $ok = $false
    foreach ($p in $parts) { if ($t.BaseName -like "*$p*" -or $t.Name -like "*$p*") { $ok = $true; break } }
    $ok
}

$summary = New-Object System.Collections.Generic.List[object]
$variant = "c-plus-examples"
$repo = $ExperimentalRoot

foreach ($taskFile in $tasks) {
    $task = Get-Content -Raw -Encoding UTF8 $taskFile.FullName | ConvertFrom-Json
    $taskId = [string]$task.id
    Write-Host "`n=== $variant / $taskId ==="

    git -C $repo reset --hard HEAD | Out-Null
    git -C $repo clean -fd | Out-Null
    & $ApplyReg -RepoRoot $repo

    $outJson = Join-Path $runDir "$variant--$taskId.json"
    $agentLog = Join-Path $runDir "$variant--$taskId.agent.json"
    $line = & python $RunOne `
        --repo $repo `
        --task $taskFile.FullName `
        --system-prompt-file $PromptFile `
        --agent-log $agentLog `
        --max-budget-usd $MaxBudgetUsd 2>&1
    $jsonLine = ($line | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1)
    if (-not $jsonLine) {
        Write-Host "run_one failed: $line"
        $rowObj = [pscustomobject]@{
            task_id = $taskId; passed = $false; judge_note = "run_one_failed"
            num_turns = $null; total_cost_usd = $null
        }
    } else {
        $rowObj = $jsonLine | ConvertFrom-Json
    }
    $row = [ordered]@{
        variant        = $variant
        task_id        = if ($rowObj.task_id) { $rowObj.task_id } else { $taskId }
        passed         = [bool]$rowObj.passed
        judge_note     = $rowObj.judge_note
        num_turns      = $rowObj.num_turns
        total_cost_usd = $rowObj.total_cost_usd
        model          = $rowObj.model
    }
    [System.IO.File]::WriteAllText($outJson, ($row | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))
    $summary.Add([pscustomobject]$row) | Out-Null
    Write-Host ("passed={0} turns={1} cost={2}" -f $row.passed, $row.num_turns, $row.total_cost_usd)
}

$summaryPath = Join-Path $runDir "summary.json"
[System.IO.File]::WriteAllText($summaryPath, ($summary | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))
Write-Host "`nWrote $summaryPath"
Write-Host "Compare to: registry-only 20260716-120837 ; A+B 20260716-125939"
