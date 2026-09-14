# CONTEXT.md

**阶段**：第一刀。标本已迁 `legacy/os-9/`。`session` / `policy` / `dispatch` / `llm` / `execution` / `loop` / `commands` / `load` + `host` 已过审；`conformance` 已落待人审（2026-09-14）。产品已可真跑。下一刀未圈（真模型，或 `/compact`）。

**规划**：[docs/rewrite-0.md](docs/rewrite-0.md)

**标本**：[legacy/os-9/](legacy/os-9/) · 结构摘录 [docs/archive/os-9/](docs/archive/os-9/) · v1 [legacy/](legacy/)

**新树**：`tepeu/`（identity / syscall / persist / session / policy / dispatch / run-llm / run-execution / run-loop / commands / load / host / conformance 已落）

**产品**：单用户、单机、单进程 CLI，已可真跑：`java -jar tepeu/host/target/tepeu-host-0.0.1-SNAPSHOT.jar`（工作区目录可选参数）。一个默认对话。`/help` `/approve` `/status` `/btw`。写文件/跑命令先问。llm 第一刀 fake。db `tepeu.db` 落当前目录。
