# T1 消融：把 AGENT_CALL_PATH 强制注入 system prompt 后重跑 experimental
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

if (-not (Test-Path $repo)) {
    throw "Missing fixture $repo — run setup-fixtures.ps1 first"
}

# 同步债出路：跑前从 Tools.java + AGENT_CALL_PATH.md 再生 inject
& python $genInject
if ($LASTEXITCODE -ne 0) { throw "generate_call_path_inject.py failed" }

git -C $repo reset --hard HEAD | Out-Null
git -C $repo clean -fd | Out-Null
& $apply -RepoRoot $repo

$runDir = Join-Path $BenchRoot ("results\T1-inject-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$agentLog = Join-Path $runDir "experimental-inject--T1-qa-call-chain.agent.json"

Write-Host "Results -> $runDir"
$line = & python $runOne `
    --repo $repo `
    --task $task `
    --system-prompt-file $prompt `
    --agent-log $agentLog `
    --max-budget-usd 1.5

$rowPath = Join-Path $runDir "row.json"
Set-Content -Encoding utf8 -Path $rowPath -Value $line
Write-Host $line
Write-Host "RUN_DIR=$runDir"
