# Project Memory — Tepeu（develop）

当前：**本机 Agent OS 骨架可演示**（五层落码）。仍不是企业 Agent OS / 完整 OS。规范 [ADR-016](./decisions-log.md)。下一刀按痛点：⑤ UI、记忆平面、多副本 fencing。

v1 工作台记忆（Chat 链路 / `ChatModelFactory` / 面板坑点）在 [`docs/archive/v1/project-memory-v1.md`](../docs/archive/v1/project-memory-v1.md)。**不要**按那份写 `os/`。

## Tech stack（develop）

- Runtime: Java 21
- 主线：`os/`（Maven；组件 session/policy/bus/llm/loop/orchestration/execution/compose；词汇 identity/syscall；harness conformance）
- 验证：`mvn -f os/pom.xml test`
- 持久化：SQLite WAL schema v1（发行 `SqliteAssembly` → `kernel.sqlite` + `approvals.sqlite`）；`MemoryAssembly` 仅测试
- `llm.*`：**禁止** Spring AI `ChatModel`（ADR-016 第十轮；自研双协议族）
- v1 工作台：`legacy/` / `main`（Spring Boot 4.0.7 + Spring AI 2.0.0 + React 18）。禁止在 `legacy/` 加功能

## Architecture（develop）

- 洋葱：① 内核概念 → ② 插头 → ③ 编排（④ 已并入）→ ⑤ 应用。物理单元 = 组件（一件事一模块）；洋葱是依赖方向不是两个大 jar
- 新代码只进 `os/`。根目录 `backend/` `frontend/` 已迁 `legacy/`
- `Product-Spec.md` 是 v1 档案（Forge 门要求留在根目录），不是 `os/` 规范

## 口径

冲突时：**ADR-016 > 底板 > 手册独有章 > v1 规格**。参照在 `docs/archive/`。禁止再开对账轮、再写第三份投影。

吸收：`docs/legacy-absorption.md` · `docs/work-docs-absorption.md` · `docs/archive/reference/gnex3-reference.md`

## Gotchas（develop 仍有效）

- 包管理器是 **Maven**，不是 Gradle。本机仓库常在 `D:\maven\repo`（非默认 `~/.m2`）
- 不要把 v1 `ChatModelFactory` / `@Tool` 装饰器路径抄进 `os/llm` 或任何内核组件
- 不要把新能力倒进「大包」；默认实现跟组件走。组件 ≠ 插件（无 Ctx / 无热插）
- Loop 不依赖 orchestration：`LoopConfig.system` 只转发；assemble 是调用方纪律
- **本机 Agent OS 骨架可演示** ≠ 企业 OS / 完整 OS。execution 隔离仍是 **partial**。compose 不读密钥。`MemoryAssembly` 不得当生产默认
- 压缩改写 surface 后必须 bump `log.surfaceEpoch`，否则下一笔 `llm.generate` 会 ASSERTION
- `/approve` 只许本会话的 approvalId；审批许可绑 argsDigest，不单绑 syscall 名
- 不要在 `legacy/` 加功能
- `Product-Spec` 七层 / 四智能体 / 记忆 P0 / WASM+V8 **不是** `os/` 现状
