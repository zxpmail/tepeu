# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
本阶段（底座加固 + ATE 初步验证）已收口。文档/交接已对齐 ADR-008/009。下一阶段：Product-Spec Phase 2（Harness）。

## 上次停在哪
- ATE：glm×C×3；文 `docs/essays/spring-ai-coding-side-legibility.md`；C Δ≈-12.4% vs A+B ≈-24.6%
- 主线：`ToolRegistry` / `IdempotencyService` / `MemoryFileMirror`；`write_file` + `run_command` 已有
- 交接：`memory/handoff.md`（2026-07-18）

## 近期关键决定
- ATE 扩面 / 第二模型 / Docker 实测 **不做**（ADR-009）
- Vibe-Trading 仅 OS 思想参照（ADR-008）；Hook / 多 Agent / MCP / Goal → Phase 2
- 正式 GitHub Release 可选，未做
