# CONTEXT.md

**阶段**：第一刀。标本已迁 `legacy/os-9/`。`session` / `policy` / `dispatch` / `llm` / `execution` / `loop` / `commands` / `load` + `host` / `conformance` / 真模型已过审；`/compact` 刀已落待人审（2026-09-14）。产品已可真跑（默认 fake；`-P real` 打包 + `ANTHROPIC_AUTH_TOKEN` 走真模型）。下一刀未圈。

**规划**：[docs/rewrite-0.md](docs/rewrite-0.md)

**标本**：[legacy/os-9/](legacy/os-9/) · 结构摘录 [docs/archive/os-9/](docs/archive/os-9/) · v1 [legacy/](legacy/)

**新树**：`tepeu/`（identity / syscall / persist / session / policy / dispatch / run-llm（gateway+fake+anthropic）/ run-execution / run-loop / commands / load / host / conformance 已落）

**产品**：单用户、单机、单进程 CLI。默认打包：`java -jar tepeu/host/target/tepeu-host-0.0.1-SNAPSHOT.jar`（fake 回复）；真模型：`mvn -f tepeu/pom.xml -P real package` 后跑同 jar，env `ANTHROPIC_AUTH_TOKEN` 必需（`ANTHROPIC_BASE_URL` / `ANTHROPIC_MODEL` 可选）。一个默认对话。`/help` `/approve` `/status` `/btw` `/compact`。写文件/跑命令先问。db `tepeu.db` 落当前目录。
