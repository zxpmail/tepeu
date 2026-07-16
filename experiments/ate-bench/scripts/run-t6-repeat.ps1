# T6 调试稳定性：baseline vs experimental 各重复 N 次
param(
    [int]$Times = 3,
    [double]$MaxBudgetUsd = 1.5
)
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Parent = Split-Path (Resolve-Path (Join-Path $BenchRoot "..\..")).Path -Parent
$TaskFile = Join-Path $BenchRoot "tasks\dim\T6-debug-path-traversal.json"
$PromptFile = Join-Path $BenchRoot "prompt\system.txt"
$RunOne = Join-Path $PSScriptRoot "run_one.py"
$ApplyExp = Join-Path $PSScriptRoot "apply-experimental.ps1"

$Variants = @(
    @{ Name = "baseline"; Root = (Join-Path $Parent "tepeu-ate-baseline"); Apply = $null },
    @{ Name = "experimental"; Root = (Join-Path $Parent "tepeu-ate-experimental"); Apply = $ApplyExp }
)

if (-not (Test-Path $TaskFile)) { throw "Missing $TaskFile" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $BenchRoot "results\T6-repeat-$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Write-Host "Results -> $runDir (Times=$Times)"

$allRows = @()
foreach ($variant in $Variants) {
    $repo = $variant.Root
    if (-not (Test-Path $repo)) { throw "Missing $repo — run setup-fixtures.ps1" }
    for ($i = 1; $i -le $Times; $i++) {
        Write-Host "`n=== $($variant.Name) run $i / $Times ==="
        git -C $repo reset --hard HEAD | Out-Null
        git -C $repo clean -fd | Out-Null
        if ($variant.Apply) { & $variant.Apply -RepoRoot $repo }

        $agentLog = Join-Path $runDir ("$($variant.Name)-run$i.agent.json")
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $line = & python $RunOne `
            --repo $repo `
            --task $TaskFile `
            --system-prompt-file $PromptFile `
            --agent-log $agentLog `
            --max-budget-usd $MaxBudgetUsd 2>&1
        $ErrorActionPreference = $prevEap

        $jsonLine = ($line | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1)
        if (-not $jsonLine) { throw "run_one failed: $line" }
        $obj = $jsonLine | ConvertFrom-Json
        $obj | Add-Member -NotePropertyName run -NotePropertyValue $i -Force
        $obj | Add-Member -NotePropertyName variant -NotePropertyValue $variant.Name -Force
        $allRows += $obj
        ($obj | ConvertTo-Json -Depth 5) | Set-Content -Encoding utf8 (Join-Path $runDir "$($variant.Name)-run$i.json")
        Write-Host ("variant={0} run={1} passed={2} turns={3} `$={4}" -f $variant.Name, $i, $obj.passed, $obj.num_turns, $obj.total_cost_usd)
    }
}

function Stats($rows) {
    $t = @($rows | ForEach-Object { $_.num_turns } | Where-Object { $_ -ne $null })
    if ($t.Count -eq 0) { return @{ n = 0; turns = @(); avg = $null; min = $null; max = $null; stdev = $null } }
    $mean = ($t | Measure-Object -Average).Average
    $stdev = $null
    if ($t.Count -ge 2) {
        $var = ($t | ForEach-Object { ($_ - $mean) * ($_ - $mean) } | Measure-Object -Average).Average
        $stdev = [math]::Round([math]::Sqrt($var), 2)
    }
    return @{
        n     = $t.Count
        turns = $t
        avg   = [math]::Round($mean, 2)
        min   = ($t | Measure-Object -Minimum).Minimum
        max   = ($t | Measure-Object -Maximum).Maximum
        stdev = $stdev
        passed = @($rows | Where-Object { $_.passed }).Count
    }
}

$baseStats = Stats @($allRows | Where-Object { $_.variant -eq "baseline" })
$expStats = Stats @($allRows | Where-Object { $_.variant -eq "experimental" })
$summary = [ordered]@{
    times        = $Times
    baseline     = $baseStats
    experimental = $expStats
    prior_single = @{ baseline = 4; experimental = 8; dir = "dim-expand-20260716-145300" }
}
$summaryPath = Join-Path $runDir "summary.json"
($summary | ConvertTo-Json -Depth 6) | Set-Content -Encoding utf8 $summaryPath

Write-Host "`n=== T6 repeat summary ==="
Write-Host ("baseline     turns={0} avg={1} stdev={2} passed={3}/{4}" -f ($baseStats.turns -join "/"), $baseStats.avg, $baseStats.stdev, $baseStats.passed, $baseStats.n)
Write-Host ("experimental turns={0} avg={1} stdev={2} passed={3}/{4}" -f ($expStats.turns -join "/"), $expStats.avg, $expStats.stdev, $expStats.passed, $expStats.n)
Write-Host "RUN_DIR=$runDir"
Write-Host "SUMMARY=$summaryPath"
