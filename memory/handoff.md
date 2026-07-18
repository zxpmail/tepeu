# Handoff — Tepeu Agentic OS

> 到达后阅读序：本文件 → `CONTEXT.md` → `decisions-log.md`（ADR-008/009）→ Product-Spec §9 / DEV-PLAN 文首命名说明。

**Last updated**: 2026-07-18

## 当前阶段

- ✅ v0.1 底座与 Agent 友好改造已在 `main`（含 `ToolRegistry`、`write_file`、`run_command`）。
- ✅ ATE 初步验证收口；扩面等见 **ADR-009**。
- ✅ 外部参照边界见 **ADR-008**。
- 下一刀：**Product-Spec §9 Phase 2（Harness）** — 不是 DEV-PLAN 里已完成的「Phase 2 对话」。

## 发布 / 缺口

- 仓库：`https://github.com/zxpmail/tepeu` · **无** v0.1.0 tag / GitHub Release（可选）。
- Spec §3.5：**workspace 累计 Token** 未做（仅会话级）。
- 工程挂账：ToolCallback deprecated（ADR-007）、crypto passthrough（ADR-006）、无 CI/CD。

## 推荐下一步

- Spec Phase 2：M2.1 多 Agent · M2.3 Hook · M2.2 MCP · M2.4 成本（含补 workspace 累计或并入仪表盘）。
- 可选：`/release-builder`；推送本地未上传 commit。
