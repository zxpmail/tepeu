# 从 Tools.java + AGENT_CALL_PATH.md 生成 / 校验 system-with-call-path.txt
# 用法:
#   powershell -File .../generate-call-path-inject.ps1
#   powershell -File .../generate-call-path-inject.ps1 -Check
param(
    [switch]$Check
)

$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$BenchRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$gen = Join-Path $BenchRoot "scripts\generate_call_path_inject.py"
$checkScript = Join-Path $BenchRoot "scripts\check_inject_sync.py"

if ($Check) {
    & python $checkScript --mode fail
    exit $LASTEXITCODE
}

& python $gen
exit $LASTEXITCODE
