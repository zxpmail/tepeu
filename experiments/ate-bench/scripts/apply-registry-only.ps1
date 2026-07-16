# 条件 C：仅显式注册（Tools/ToolRegistry），不放 AGENT_CALL_PATH.md
param(
    [Parameter(Mandatory = $true)][string]$RepoRoot
)
$ErrorActionPreference = "Stop"
$PatchDir = (Resolve-Path (Join-Path $PSScriptRoot "..\patches\experimental")).Path
$DestAgent = Join-Path $RepoRoot "backend\src\main\java\com\tepeu\agent"
New-Item -ItemType Directory -Force -Path (Join-Path $DestAgent "tool") | Out-Null
Copy-Item -Force (Join-Path $PatchDir "ToolRegistry.java") (Join-Path $DestAgent "tool\ToolRegistry.java")
Copy-Item -Force (Join-Path $PatchDir "Tools.java") (Join-Path $DestAgent "Tools.java")
Copy-Item -Force (Join-Path $PatchDir "ChatService.java") (Join-Path $RepoRoot "backend\src\main\java\com\tepeu\service\chat\ChatService.java")
Copy-Item -Force (Join-Path $PatchDir "AgentOrchestrator.java") (Join-Path $DestAgent "AgentOrchestrator.java")
Copy-Item -Force (Join-Path $PatchDir "ChatServiceTest.java") (Join-Path $RepoRoot "backend\src\test\java\com\tepeu\service\chat\ChatServiceTest.java")
$md = Join-Path $DestAgent "AGENT_CALL_PATH.md"
if (Test-Path $md) {
    Remove-Item -Force $md
    Write-Host "Removed AGENT_CALL_PATH.md (registry-only)"
}
Write-Host "Applied registry-only (C: Tools/ToolRegistry, no passive call-path doc) to $RepoRoot"
