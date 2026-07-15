# T1 消融：把 AGENT_CALL_PATH 强制注入 system prompt 后重跑 experimental
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$repo = "E:\work\tepeu-ate-experimental"
$bench = "E:\work\tepeu\experiments\ate-bench"
$apply = Join-Path $bench "scripts\apply-experimental.ps1"
$runOne = Join-Path $bench "scripts\run_one.py"
$task = Join-Path $bench "tasks\T1-qa-call-chain.json"
$prompt = Join-Path $bench "prompt\system-with-call-path.txt"

if (-not (Test-Path $repo)) {
    throw "Missing fixture $repo — run setup-fixtures.ps1 first"
}

git -C $repo reset --hard HEAD | Out-Null
git -C $repo clean -fd | Out-Null
& $apply -RepoRoot $repo

$runDir = Join-Path $bench ("results\T1-inject-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
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
