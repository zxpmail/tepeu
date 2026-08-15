# 准备 Baseline / Experimental 两个 git worktree（相对本仓库根目录）。
# Baseline = 初始提交（无 Tools.java）；Experimental = 同提交 + experimental 补丁。
$ErrorActionPreference = "Stop"
chcp 65001 | Out-Null

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$Parent = Split-Path $Root -Parent
$Baseline = Join-Path $Parent "tepeu-ate-baseline"
$Experimental = Join-Path $Parent "tepeu-ate-experimental"
$BaselineCommit = "7980e6d"

Write-Host "Repo root: $Root"
Write-Host "Baseline worktree: $Baseline (commit $BaselineCommit)"
Write-Host "Experimental worktree: $Experimental"

function Ensure-WorktreeAtCommit([string]$path, [string]$branch, [string]$commit) {
    if (Test-Path $path) {
        Write-Host "Worktree exists: $path — reset to $commit"
        git -C $path fetch --no-tags 2>$null | Out-Null
        git -C $path checkout -B $branch $commit
        git -C $path reset --hard $commit
        git -C $path clean -fd
        return
    }
    git -C $Root worktree add -b $branch $path $commit
    if ($LASTEXITCODE -ne 0) {
        git -C $Root worktree add $path $commit
        if ($LASTEXITCODE -ne 0) { throw "Failed to add worktree $path at $commit" }
        git -C $path checkout -B $branch
    }
}

Ensure-WorktreeAtCommit $Baseline "ate/baseline" $BaselineCommit
Ensure-WorktreeAtCommit $Experimental "ate/experimental" $BaselineCommit

& (Join-Path $PSScriptRoot "apply-experimental.ps1") -RepoRoot $Experimental

Write-Host "Fixtures ready:"
Write-Host "  baseline     = $Baseline"
Write-Host "  experimental = $Experimental"
