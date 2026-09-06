# Handoff — Tepeu（从零重写）

> 到达后：本文件 → `CONTEXT.md` → [`docs/rewrite-0.md`](../docs/rewrite-0.md)。  
> 标本：`legacy/os-9/`。新库根 `tepeu/`。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 v1 档案。

**Last updated**: 2026-09-07（persist-sqlite 三处补完，待人审）

## 当前阶段

- 标本已迁：`legacy/os-9/os/`、`legacy/os-9/host/`。
- 已写：`identity`（已推）、`syscall`（已推）、`persist-api`、`persist-sqlite`（待人审）。
- 纪律：一个组件写完，人审查通过才能继续。下一刀是 session。

## 本刀 persist-sqlite

- 包 `com.tepeu.persist.sqlite`。依赖 persist-api。
- 实现：MyBatis-Plus 3.5.17（`mybatis-plus-core` + `mybatis-plus-jsqlparser`）+ sqlite-jdbc 3.53.4.0。slf4j-api 2.0.18（MP 运行要）。
- 不用 Spring Boot starter。host 以后才上 Spring。
- 公开类型：`SqlitePersist.open(Path)`、`memory()`。`AutoCloseable`。关后操作抛 `IllegalStateException("persist closed")`。
- 表 `persist_record`：id / space / rec_key / fields。通用行，不是 session 表。
- 2026-09-07 补：`close()` 置 closed + WAL checkpoint；UNIQUE 只认 `SQLITE_CONSTRAINT_UNIQUE` / `PRIMARYKEY`；`SQLiteConfig` WAL + `busy_timeout=5000`；装配关 SQL 日志；`memory()` 建库失败删临时文件。
- 未做：连接池、`putObject`、schema 迁移框架、原子 upsert
- 验证：`mvn -f tepeu/pom.xml -pl persist/sqlite -am test`（persist-api 4 + persist-sqlite 5 绿）

## 已定（2026-09-06 人圈）

- 库根 `tepeu/`。四层：`types/` · `persist/` · `kernel/` · `run/`。
- `load/`：装配。注入实现、拼系统提示、登记斜杠。
- `commands/`：斜杠表。第一刀 `/help` `/approve` `/status` `/btw`。`/compact` 以后加。
- `/btw`：旁问。看当前对话，不写事件日志，不调工具。用量进流水。第一刀仅 loop 空闲时。
- host：Spring Boot（无 Web）。`/` 行交给 commands。
- persist 第一刀 sqlite；llm 网关 + fake。host POM 依赖哪个用哪个。
- 依赖只朝下。
- 第一刀一个默认对话。默认授权：问模型/读文件放行；写文件/跑命令先问。
- 第一刀 llm 是 fake。真模型换 host POM。
- 第一刀不做：invoke、定时入队、`/compact`、`/btw` 并发、会话列表、`kernel/memory/`。
