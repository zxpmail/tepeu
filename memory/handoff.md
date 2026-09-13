# Handoff — Tepeu（从零重写）

> 到达后：本文件 → `CONTEXT.md` → [`docs/rewrite-0.md`](../docs/rewrite-0.md)。  
> 标本：`legacy/os-9/`。新库根 `tepeu/`。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 v1 档案。

**Last updated**: 2026-09-13（session 人审过，下一刀 policy）

## 当前阶段

- 标本已迁：`legacy/os-9/os/`、`legacy/os-9/host/`。
- 已写已推：`identity`、`syscall`、`persist-api`、`persist-sqlite`、`session`（2026-09-13 人审过）。
- 纪律：一个组件写完，人审查通过才能继续。下一刀是 policy。

## 本刀 session

- 包 `com.tepeu.session`。路径 `tepeu/kernel/session`。依赖 identity、syscall、persist-api。不关库。
- 公开：`SessionStore.open`（已有则读回，没有则建）。默认 id `SessionStore.DEFAULT`。
- 五本账：事件日志（`append` 可带 attrs，空合法）/ 寄存器 / 用量流水 / 收件箱（NOW>NEXT>LATER，租约 300s，ack/nack）/ 操作审计。
- 适配：`com.tepeu.session.persist.PersistedSessionStore`。space 名 `session/{id}/…`。序号簿骨架 `appendDated`/`readBook` 一份（人审要求抽第三同构）。
- 未做：fork、recover、压缩面、blobs、投影通知、会话列表。
- 验证：`mvn -f tepeu/pom.xml -pl kernel/session -am test`（session 7 绿）

## policy 刀前要定的（session 遗留）

- `SessionStore.open` 辨不辨新建/读回；owner/workspace 与库里不一致是否 fail fast。
- `SessionId` 允许 `/`，space 拼接用它。第一刀撞不出（固定名表无 `/`）；做会话列表或动态名前，在 store 层或 identity 层收紧。
- session 五本账 seq 分配与收件箱领取是非原子读改写，单进程单写者前提。loop 线程化前收口。

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
