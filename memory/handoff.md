# Handoff — Tepeu（从零重写）

> 到达后：本文件 → `CONTEXT.md` → [`docs/rewrite-0.md`](../docs/rewrite-0.md)。  
> 标本：`legacy/os-9/`。新库根 `tepeu/`。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 v1 档案。

**Last updated**: 2026-09-06（syscall 待人审；与 identity 不合并）

## 当前阶段

- 标本已迁：`legacy/os-9/os/`、`legacy/os-9/host/`。
- 已写：`identity`（已推）、`syscall`（待人审）。
- 纪律：一个组件写完，人审查通过才能继续。下一刀是 persist-api。

## 本刀 syscall

- 包 `com.tepeu.syscall`。不依赖 identity。
- 类型：`Syscall`、`SyscallResult`、`Usage`、`ArgDigest`。`Usage` 两参：input/output；`cost` 空 = 未知。
- 与 identity 不合并。identity 是谁/工作区/对话；syscall 是信封。彼此不依赖。`InvokeContext` 打包三项与取消位，不是信封。
- 未做：`SyscallHandler`、缓存 token、latency、logger
- 验证：`mvn -f tepeu/pom.xml test`（identity 3 + syscall 5 绿）

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
