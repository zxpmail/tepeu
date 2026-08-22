# Project Memory — Tepeu（develop）

当前：**kernel 切片 + llm(fake) + Loop 答复路径 + 工具循环，不是 Agent OS。** 规范 [ADR-016](./decisions-log.md)。下一刀：llm 真 HTTP，或预算硬门，或 maintenance。

v1 工作台记忆（Chat 链路 / `ChatModelFactory` / 面板坑点）在 [`docs/archive/v1/project-memory-v1.md`](../docs/archive/v1/project-memory-v1.md)。**不要**按那份写 `os/`。

## Tech stack（develop）

- Runtime: Java 21
- 主线：`os/`（Maven；组件 session/policy/bus/llm/loop/compose；词汇 identity/syscall；harness conformance）
- 验证：`mvn -f os/pom.xml test`
- 持久化：SQLite WAL 是规范默认；schema 未写；conformance 现为内存
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
- 不要在 `legacy/` 加功能
- `Product-Spec` 七层 / 四智能体 / 记忆 P0 / WASM+V8 **不是** `os/` 现状
