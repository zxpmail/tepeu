# 准备 Baseline / Experimental 两个 git worktree，并在 Experimental 上应用改造补丁。
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$Baseline = "E:\work\tepeu-ate-baseline"
$Experimental = "E:\work\tepeu-ate-experimental"

Write-Host "Repo root: $Root"

function Ensure-Worktree([string]$path, [string]$branch) {
    if (Test-Path $path) {
        Write-Host "Worktree exists: $path — reset hard"
        git -C $path reset --hard HEAD
        git -C $path clean -fd
        return
    }
    $created = $false
    git -C $Root worktree add -b $branch $path HEAD 2>$null
    if ($LASTEXITCODE -eq 0) { $created = $true }
    if (-not $created) {
        git -C $Root worktree add $path $branch
        if ($LASTEXITCODE -ne 0) { throw "Failed to add worktree $path" }
    }
}

Ensure-Worktree $Baseline "ate/baseline"
Ensure-Worktree $Experimental "ate/experimental"

& (Join-Path $PSScriptRoot "apply-experimental.ps1") -RepoRoot $Experimental

Write-Host "Fixtures ready:"
Write-Host "  baseline     = $Baseline"
Write-Host "  experimental = $Experimental"
