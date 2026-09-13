# CONTEXT.md

**阶段**：第一刀。标本已迁 `legacy/os-9/`。`session` / `policy` / `dispatch` / `llm` / `execution` / `loop` 已过审；`commands` 已落待人审（2026-09-14）。下一刀未圈（load / host 或 conformance）。

**规划**：[docs/rewrite-0.md](docs/rewrite-0.md)

**标本**：[legacy/os-9/](legacy/os-9/) · 结构摘录 [docs/archive/os-9/](docs/archive/os-9/) · v1 [legacy/](legacy/)

**新树**：`tepeu/`（identity / syscall / persist / session / policy / dispatch / run-llm / run-execution / run-loop / commands 已推）

**产品**：单用户、单机、单进程 CLI。一个默认对话。`/help` `/approve` `/status` `/btw`。写文件/跑命令先问。llm 第一刀 fake。
