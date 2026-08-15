# 注册 vs 文档消融：Baseline + registry-only，只跑 T2/T3（扩展工具类）
param([double]$MaxBudgetUsd = 1.5)
$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "run-ate.ps1") -Mode registry-only -TaskFilter "T2|T3" -MaxBudgetUsd $MaxBudgetUsd
