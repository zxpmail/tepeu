# 维度扩展：调试 T6 + 重构 T7（baseline vs experimental A+B）
# 不进入默认 5 任务套件；结果写入 results/dim-expand-<stamp>/
param(
    [double]$MaxBudgetUsd = 1.5,
    [string]$TaskFilter = ""
)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RepoRoot = (Resolve-Path (Join-Path $BenchRoot "..\..")).Path
$Parent = Split-Path $RepoRoot -Parent
$TasksDir = Join-Path $BenchRoot "tasks\dim"
$PromptFile = Join-Path $BenchRoot "prompt\system.txt"
$RunOne = Join-Path $PSScriptRoot "run_one.py"
$ApplyExp = Join-Path $PSScriptRoot "apply-experimental.ps1"

$BaselineRoot = Join-Path $Parent "tepeu-ate-baseline"
$ExperimentalRoot = Join-Path $Parent "tepeu-ate-experimental"

$Variants = @(
    @{ Name = "baseline"; Root = $BaselineRoot; Apply = $null },
    @{ Name = "experimental"; Root = $ExperimentalRoot; Apply = $ApplyExp }
)

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $BenchRoot "results\dim-expand-$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Results -> $runDir"

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
if (-not $tasks) { throw "No tasks matched under $TasksDir" }

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

        # NativeCommandError：Python 写 stderr 时，Stop 会打断；此处临时放宽
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $line = & python $RunOne `
            --repo $repo `
            --task $taskFile.FullName `
            --system-prompt-file $PromptFile `
            --agent-log $agentLog `
            --max-budget-usd $MaxBudgetUsd `
            --variant $variant.Name 2>&1
        $ErrorActionPreference = $prevEap

        $jsonLine = ($line | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1)
        if (-not $jsonLine) {
            Write-Host "WARN: no JSON row from run_one: $line"
            $row = [pscustomobject]@{
                variant = $variant.Name; task_id = $taskId; passed = $false
                num_turns = $null; total_cost_usd = $null; note = "$line"
            }
        } else {
            $obj = $jsonLine | ConvertFrom-Json
            $obj | Add-Member -NotePropertyName variant -NotePropertyValue $variant.Name -Force
            ($obj | ConvertTo-Json -Depth 6) | Set-Content -Encoding utf8 $outJson
            $row = $obj
            Write-Host $jsonLine
        }
        $summary.Add($row) | Out-Null
    }
}

$summaryPath = Join-Path $runDir "summary.json"
($summary | ConvertTo-Json -Depth 6) | Set-Content -Encoding utf8 $summaryPath

Write-Host "`n=== dim-expand summary ==="
foreach ($r in $summary) {
    $turns = $r.num_turns
    $cost = $r.total_cost_usd
    $pass = $r.passed
    Write-Host ("{0,-14} {1,-32} passed={2} turns={3} `$={4}" -f $r.variant, $r.task_id, $pass, $turns, $cost)
}
Write-Host "RUN_DIR=$runDir"
Write-Host "SUMMARY=$summaryPath"
