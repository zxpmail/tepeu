# 第二/第三模型最小复现套件
# 覆盖：C alone T2/T3、A-only T3、T6 baseline/exp 各 1 次
# 例：
#   -Model deepseek-v4-pro
#   -Model glm-5.2 -BaseUrl https://open.bigmodel.cn/api/anthropic -FromTepeuProvider anthropic
param(
    [string]$Model = "deepseek-v4-pro",
    [double]$MaxBudgetUsd = 3.0,
    [string]$BaseUrl = "",
    [string]$AuthToken = "",
    [string]$FromTepeuProvider = ""
)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Parent = Split-Path (Resolve-Path (Join-Path $BenchRoot "..\..")).Path -Parent
$ExpRoot = Join-Path $Parent "tepeu-ate-experimental"
$BaseRoot = Join-Path $Parent "tepeu-ate-baseline"
$ApplyReg = Join-Path $PSScriptRoot "apply-registry-only.ps1"
$ApplyExp = Join-Path $PSScriptRoot "apply-experimental.ps1"
$GenInject = Join-Path $PSScriptRoot "generate_call_path_inject.py"
$RunOne = Join-Path $PSScriptRoot "run_one.py"
$SysPlain = Join-Path $BenchRoot "prompt\system.txt"
$SysCall = Join-Path $BenchRoot "prompt\system-with-call-path.txt"
$TasksDir = Join-Path $BenchRoot "tasks"
$T6 = Join-Path $BenchRoot "tasks\dim\T6-debug-path-traversal.json"

foreach ($p in @($ExpRoot, $BaseRoot)) {
    if (-not (Test-Path $p)) { throw "Missing $p — run setup-fixtures.ps1" }
}

$SettingsFile = ""
if ($FromTepeuProvider) {
    $check = Join-Path $PSScriptRoot "tepeu_provider_env.py"
    & python $check --provider-id $FromTepeuProvider --check-only
    if ($LASTEXITCODE -ne 0) { throw "tepeu provider check failed" }
    if (-not $Model -or $Model -eq "deepseek-v4-pro") {
        # 从 DB 读默认模型名（用户显式 -Model 时不覆盖）
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

& python $GenInject
if ($LASTEXITCODE -ne 0) { throw "generate_call_path_inject.py failed" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$safeModel = ($Model -replace "[^a-zA-Z0-9._-]", "_")
$runDir = Join-Path $BenchRoot "results\model2-$safeModel-$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Results -> $runDir (model=$Model)"

function Invoke-Ate([string]$Label, [string]$Repo, [scriptblock]$Prepare, [string]$TaskPath, [string]$Prompt, [string]$Variant) {
    Write-Host "`n=== $Label ==="
    git -C $Repo reset --hard HEAD | Out-Null
    git -C $Repo clean -fd | Out-Null
    if ($Prepare) { & $Prepare }

    $agentLog = Join-Path $runDir ("$Label.agent.json")
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $runArgs = @(
        "--repo", $Repo,
        "--task", $TaskPath,
        "--system-prompt-file", $Prompt,
        "--agent-log", $agentLog,
        "--max-budget-usd", "$MaxBudgetUsd",
        "--model", $Model,
        "--variant", $Variant
    )
    if ($BaseUrl) { $runArgs += @("--base-url", $BaseUrl) }
    if ($AuthToken) { $runArgs += @("--auth-token", $AuthToken) }
    if ($SettingsFile) { $runArgs += @("--bare", "--settings-file", $SettingsFile) }
    $line = & python $RunOne @runArgs 2>&1
    $ErrorActionPreference = $prevEap

    $jsonLine = ($line | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1)
    if (-not $jsonLine) { throw "run_one failed ($Label): $line" }
    $obj = $jsonLine | ConvertFrom-Json
    $obj | Add-Member -NotePropertyName label -NotePropertyValue $Label -Force
    $obj | Add-Member -NotePropertyName requested_model -NotePropertyValue $Model -Force
    ($obj | ConvertTo-Json -Depth 6) | Set-Content -Encoding utf8 (Join-Path $runDir "$Label.json")
    Write-Host ("passed={0} turns={1} model={2} `$={3}" -f $obj.passed, $obj.num_turns, $obj.model, $obj.total_cost_usd)
    return $obj
}

$rows = @()

# 1) C alone
$rows += Invoke-Ate "C--T2" $ExpRoot { & $ApplyReg -RepoRoot $ExpRoot } `
    (Join-Path $TasksDir "T2-qa-add-tool-files.json") $SysPlain "experimental"
$rows += Invoke-Ate "C--T3" $ExpRoot { & $ApplyReg -RepoRoot $ExpRoot } `
    (Join-Path $TasksDir "T3-feature-weather-tool.json") $SysPlain "experimental"

# 2) A-only T3（注册 + call-path 注入，无被动 md）
$rows += Invoke-Ate "A-only--T3" $ExpRoot { & $ApplyReg -RepoRoot $ExpRoot } `
    (Join-Path $TasksDir "T3-feature-weather-tool.json") $SysCall "experimental"

# 3) T6 debug：Base vs Exp
$rows += Invoke-Ate "T6-baseline" $BaseRoot $null $T6 $SysPlain "baseline"
$rows += Invoke-Ate "T6-experimental" $ExpRoot { & $ApplyExp -RepoRoot $ExpRoot } $T6 $SysPlain "experimental"

$summary = [ordered]@{
    model = $Model
    flash_refs = @{
        C_T2 = 7; C_T3 = 13; A_only_T3 = 10
        T6_base_avg = 6; T6_exp_avg = 9.67
    }
    rows = $rows
}
$summaryPath = Join-Path $runDir "summary.json"
($summary | ConvertTo-Json -Depth 8) | Set-Content -Encoding utf8 $summaryPath

Write-Host "`n=== second-model summary ($Model) ==="
foreach ($r in $rows) {
    Write-Host ("{0,-18} passed={1} turns={2} model={3}" -f $r.label, $r.passed, $r.num_turns, $r.model)
}
if ($SettingsFile -and (Test-Path $SettingsFile)) {
    Remove-Item -Force $SettingsFile
    Write-Host "Removed temp settings file"
}

Write-Host "RUN_DIR=$runDir"
Write-Host "SUMMARY=$summaryPath"
