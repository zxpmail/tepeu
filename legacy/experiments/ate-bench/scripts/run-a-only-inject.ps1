# H1 方向 2：A-only = 条件 C（只注册、无被动 md）+ system 主动注入 call-path
# 拆开「注入」与「仓库里的被动 md」对 T2/T3 的贡献。
param(
    [string]$TaskFilter = "T2|T3",
    [double]$MaxBudgetUsd = 1.5
)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Parent = Split-Path (Resolve-Path (Join-Path $BenchRoot "..\..")).Path -Parent
$repo = Join-Path $Parent "tepeu-ate-experimental"
$ApplyReg = Join-Path $PSScriptRoot "apply-registry-only.ps1"
$GenInject = Join-Path $PSScriptRoot "generate_call_path_inject.py"
$RunOne = Join-Path $PSScriptRoot "run_one.py"
$PromptFile = Join-Path $BenchRoot "prompt\system-with-call-path.txt"
$TasksDir = Join-Path $BenchRoot "tasks"

if (-not (Test-Path $repo)) { throw "Missing $repo — run setup-fixtures.ps1" }

& python $GenInject
if ($LASTEXITCODE -ne 0) { throw "generate_call_path_inject.py failed" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $BenchRoot "results\A-only-inject-$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Results -> $runDir (A-only: registry + call-path inject, no passive md)"

$tasks = Get-ChildItem $TasksDir -Filter "*.json" | Sort-Object Name
$parts = @($TaskFilter -split "[|,]" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$tasks = @($tasks | Where-Object {
    $t = $_; $ok = $false
    foreach ($p in $parts) { if ($t.BaseName -like "*$p*" -or $t.Name -like "*$p*") { $ok = $true; break } }
    $ok
})
if ($tasks.Count -lt 1) { throw "No tasks matched $TaskFilter" }

$summary = New-Object System.Collections.Generic.List[object]
foreach ($taskFile in $tasks) {
    $task = Get-Content -Raw -Encoding UTF8 $taskFile.FullName | ConvertFrom-Json
    $taskId = [string]$task.id
    Write-Host "`n=== a-only / $taskId ==="

    git -C $repo reset --hard HEAD | Out-Null
    git -C $repo clean -fd | Out-Null
    & $ApplyReg -RepoRoot $repo

    $agentLog = Join-Path $runDir ("a-only--$taskId.agent.json")
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $line = & python $RunOne `
        --repo $repo `
        --task $taskFile.FullName `
        --system-prompt-file $PromptFile `
        --agent-log $agentLog `
        --max-budget-usd $MaxBudgetUsd 2>&1
    $ErrorActionPreference = $prevEap

    $jsonLine = ($line | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1)
    if (-not $jsonLine) {
        Write-Host "run_one failed: $line"
        $row = [pscustomobject]@{ variant = "a-only"; task_id = $taskId; passed = $false; num_turns = $null }
    } else {
        $obj = $jsonLine | ConvertFrom-Json
        $obj | Add-Member -NotePropertyName variant -NotePropertyValue "a-only" -Force
        ($obj | ConvertTo-Json -Depth 5) | Set-Content -Encoding utf8 (Join-Path $runDir "a-only--$taskId.json")
        $row = $obj
        Write-Host $jsonLine
    }
    $summary.Add($row) | Out-Null
}

$summaryPath = Join-Path $runDir "summary.json"
($summary | ConvertTo-Json -Depth 5) | Set-Content -Encoding utf8 $summaryPath
Write-Host "`nWrote $summaryPath"
Write-Host "Refs: C alone 20260716-120837; C+examples; A+B 20260716-125939"
Write-Host "RUN_DIR=$runDir"
