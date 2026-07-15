# 跑 ATE：5 任务 × 2 变体（内部调用 run_one.py）。
param(
    [string]$TaskFilter = "",
    [double]$MaxBudgetUsd = 1.5
)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$TasksDir = Join-Path $BenchRoot "tasks"
$PromptFile = Join-Path $BenchRoot "prompt\system.txt"
$ResultsDir = Join-Path $BenchRoot "results"
$RunOne = Join-Path $PSScriptRoot "run_one.py"
$ApplyExp = Join-Path $PSScriptRoot "apply-experimental.ps1"

$Variants = @(
    @{ Name = "baseline"; Root = "E:\work\tepeu-ate-baseline"; Experimental = $false },
    @{ Name = "experimental"; Root = "E:\work\tepeu-ate-experimental"; Experimental = $true }
)

New-Item -ItemType Directory -Force -Path $ResultsDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $ResultsDir $stamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Results -> $runDir"

$tasks = Get-ChildItem $TasksDir -Filter "*.json" | Sort-Object Name
if ($TaskFilter) {
    $tasks = $tasks | Where-Object { $_.BaseName -like "*$TaskFilter*" -or $_.Name -like "*$TaskFilter*" }
}

$summary = New-Object System.Collections.Generic.List[object]

foreach ($variant in $Variants) {
    $repo = $variant.Root
    if (-not (Test-Path $repo)) {
        throw "Fixture missing: $repo — run setup-fixtures.ps1 first"
    }
    foreach ($taskFile in $tasks) {
        $task = Get-Content -Raw -Encoding UTF8 $taskFile.FullName | ConvertFrom-Json
        $taskId = [string]$task.id
        Write-Host "`n=== $($variant.Name) / $taskId ==="

        git -C $repo reset --hard HEAD | Out-Null
        git -C $repo clean -fd | Out-Null
        if ($variant.Experimental) {
            & $ApplyExp -RepoRoot $repo
        }

        $outJson = Join-Path $runDir "$($variant.Name)--$taskId.json"
        $agentLog = Join-Path $runDir "$($variant.Name)--$taskId.agent.json"

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
                task_id = $taskId; category = $task.category; passed = $false
                judge_note = "run_one_failed"; num_turns = $null; total_cost_usd = $null
                duration_ms = $null; is_error = $true; model = $null; claude_exit = -1
            }
        } else {
            $rowObj = $jsonLine | ConvertFrom-Json
        }
        $row = [ordered]@{
            variant        = $variant.Name
            task_id        = if ($rowObj.task_id) { $rowObj.task_id } else { $taskId }
            category       = $rowObj.category
            passed         = [bool]$rowObj.passed
            judge_note     = $rowObj.judge_note
            num_turns      = $rowObj.num_turns
            total_cost_usd = $rowObj.total_cost_usd
            duration_ms    = $rowObj.duration_ms
            is_error       = [bool]$rowObj.is_error
            model          = $rowObj.model
            claude_exit    = $rowObj.claude_exit
        }
        [System.IO.File]::WriteAllText(
            $outJson,
            ($row | ConvertTo-Json -Depth 5),
            [System.Text.UTF8Encoding]::new($false))
        $summary.Add([pscustomobject]$row) | Out-Null
        Write-Host ("passed={0} turns={1} cost={2}" -f $row.passed, $row.num_turns, $row.total_cost_usd)
    }
}

$summaryPath = Join-Path $runDir "summary.json"
[System.IO.File]::WriteAllText(
    $summaryPath,
    ($summary | ConvertTo-Json -Depth 5),
    [System.Text.UTF8Encoding]::new($false))
Write-Host "`nWrote $summaryPath"
python (Join-Path $PSScriptRoot "summarize.py") --summary $summaryPath
