# 将 experimental 补丁应用到指定仓库根目录
param(
    [Parameter(Mandatory = $true)][string]$RepoRoot
)
$ErrorActionPreference = "Stop"
$PatchDir = (Resolve-Path (Join-Path $PSScriptRoot "..\patches\experimental")).Path
$DestAgent = Join-Path $RepoRoot "backend\src\main\java\com\tepeu\agent"
New-Item -ItemType Directory -Force -Path (Join-Path $DestAgent "tool") | Out-Null
Copy-Item -Force (Join-Path $PatchDir "ToolRegistry.java") (Join-Path $DestAgent "tool\ToolRegistry.java")
Copy-Item -Force (Join-Path $PatchDir "Tools.java") (Join-Path $DestAgent "Tools.java")
Copy-Item -Force (Join-Path $PatchDir "AGENT_CALL_PATH.md") (Join-Path $DestAgent "AGENT_CALL_PATH.md")
Copy-Item -Force (Join-Path $PatchDir "ChatService.java") (Join-Path $RepoRoot "backend\src\main\java\com\tepeu\service\chat\ChatService.java")
Copy-Item -Force (Join-Path $PatchDir "AgentOrchestrator.java") (Join-Path $DestAgent "AgentOrchestrator.java")
Copy-Item -Force (Join-Path $PatchDir "ChatServiceTest.java") (Join-Path $RepoRoot "backend\src\test\java\com\tepeu\service\chat\ChatServiceTest.java")
Write-Host "Applied experimental patch to $RepoRoot"
