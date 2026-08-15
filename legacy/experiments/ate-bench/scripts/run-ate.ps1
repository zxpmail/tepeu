# 跑 ATE：默认 5 任务 × baseline + experimental(A+B)；可用 -Variant registry-only 跑条件 C。
param(
    [string]$TaskFilter = "",
    [double]$MaxBudgetUsd = 1.5,
    [ValidateSet("ab", "registry-only", "both")][string]$Mode = "ab"
)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RepoRoot = (Resolve-Path (Join-Path $BenchRoot "..\..")).Path
$Parent = Split-Path $RepoRoot -Parent
$TasksDir = Join-Path $BenchRoot "tasks"
$PromptFile = Join-Path $BenchRoot "prompt\system.txt"
$ResultsDir = Join-Path $BenchRoot "results"
$RunOne = Join-Path $PSScriptRoot "run_one.py"
$ApplyExp = Join-Path $PSScriptRoot "apply-experimental.ps1"
$ApplyReg = Join-Path $PSScriptRoot "apply-registry-only.ps1"

$BaselineRoot = Join-Path $Parent "tepeu-ate-baseline"
$ExperimentalRoot = Join-Path $Parent "tepeu-ate-experimental"

$Variants = @()
if ($Mode -eq "ab" -or $Mode -eq "both") {
    $Variants += @{ Name = "baseline"; Root = $BaselineRoot; Apply = $null }
    $Variants += @{ Name = "experimental"; Root = $ExperimentalRoot; Apply = $ApplyExp }
}
if ($Mode -eq "registry-only" -or $Mode -eq "both") {
    if ($Mode -eq "registry-only") {
        $Variants += @{ Name = "baseline"; Root = $BaselineRoot; Apply = $null }
    }
    $Variants += @{ Name = "registry-only"; Root = $ExperimentalRoot; Apply = $ApplyReg }
}

New-Item -ItemType Directory -Force -Path $ResultsDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $ResultsDir $stamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Results -> $runDir (Mode=$Mode)"

$tasks = Get-ChildItem $TasksDir -Filter "*.json" | Sort-Object Name
if ($TaskFilter) {
    $parts = @($TaskFilter -split "[|,]" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $tasks = $tasks | Where-Object {
        $t = $_
        $ok = $false
        foreach ($p in $parts) {
            if ($t.BaseName -like "*$p*" -or $t.Name -like "*$p*") { $ok = $true; break }
        }
        $ok
    }
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
        if ($variant.Apply) {
            & $variant.Apply -RepoRoot $repo
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
