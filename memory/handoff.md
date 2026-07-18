# Handoff — Tepeu Agentic OS

> 跨会话/跨客户端交接。离开前更新，到达后**先读此文件**（不依赖聊天历史）。
> 到达后阅读序：本文件 → project-memory.md + decisions-log.md → DEV-PLAN.md / CONTEXT.md。

**Last updated**: 2026-07-18

## 当前阶段

- ✅ **v0.1 产品底座**：工作台、对话 SSE、工具循环、记忆、文件、终端、IDE 三栏等已落地。
- ✅ **Agent 友好改造合入 main**：`Tools` / `ToolRegistry`、`IdempotencyService`、`MemoryFileMirror`；`FileTools`（含 `write_file`）+ `ShellTools`（`run_command`）。
- ✅ **ATE 初步验证收口**：glm×C×3 等结果已写回 `docs/essays/spring-ai-coding-side-legibility.md`；扩面/第二模型/Docker 实测 **不做**（ADR-009）。
- ✅ **外部参照边界**：Vibe-Trading 只吸 OS 思想，不抄垂直功能（ADR-008）；Hook / 多 Agent / MCP / Goal → Product-Spec Phase 2。

## 验证 / 发布状态

- 仓库：`https://github.com/zxpmail/tepeu`，分支 `main`（有 git）。
- **尚无** git tag / GitHub Release（正式发版可选，未做）。
- ATE Docker：仅有定义文件，本机不要求实测（ADR-009）。

## Blocker / 风险

- Spring AI 2.0 `ToolCallback...` API 均 `@Deprecated`（工具可视化装饰器路径，ADR-007）。
- M7 crypto 遗留明文 passthrough（Should-fix，ADR-006）。
- 无 CI/CD；前端无全局 toast。
- Workspace **累计** Token 视图未做满（现有会话级用量）；完整成本仪表盘属 Phase 2 M2.4。

## 关键 ADR（近期）

- **ADR-008**：Vibe-Trading 参照边界与排期
- **ADR-009**：ATE 扩面与 Docker 实测不做
- 另见 ADR-006/007（加密、testConnection / 工具注册）

## 推荐下一步

- 进入 **Product-Spec Phase 2（Harness）**：M2.1 多 Agent → M2.3 Hook → M2.2 MCP → M2.4 成本仪表盘（按需排序）。
- 可选：`/release-builder` 打 v0.1.0 tag + GitHub Release。
