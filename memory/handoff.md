# Handoff — Tepeu（从零重写）

> 到达后：本文件 → `CONTEXT.md` → [`docs/rewrite-0.md`](../docs/rewrite-0.md)。  
> 标本：`legacy/os-9/`。新库根 `tepeu/`。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 v1 档案。

**Last updated**: 2026-09-13（policy 过审 + 全树审计；dispatch 刀已落待人审）

## 当前阶段

- 标本已迁：`legacy/os-9/os/`、`legacy/os-9/host/`。
- 已写已推：`identity`、`syscall`、`persist-api`、`persist-sqlite`、`session`、`policy`（2026-09-13 人审过）、`dispatch`（已落待人审）。
- 2026-09-13 全树审计：解耦/错误全过；types 收紧（Usage 禁负、failure 必带码）；`SessionStore.open` javadoc 补 fail-fast 语义。
- 纪律：一个组件写完，人审查通过才能继续。下一刀未圈（run/ 层：llm 网关或 execution）。

## 本刀 dispatch

- 包 `com.tepeu.dispatch`。路径 `tepeu/kernel/dispatch`。依赖 identity、syscall、policy。不依赖 session / persist，不写日志。
- 公开：`Dispatch`（register / names 字典序 / invoke）+ `Handler`（函数接口）。
- **全部合成结果（2026-09-13 裁）**：不抛异常。invoke 序：取消 → 授权 → 查表 → 调用。错误码 `CANCELLED` / `DENIED` / `APPROVAL_REQUIRED` / `NOT_FOUND` / `HANDLER_ERROR`。
- 未装配 Policy 或审批通道按 DENIED（fail-closed）；策略返回 null 也 DENIED。NEED_APPROVAL **先 consume 后 ask**——顺序反了会把 decide 孤立掉。APPROVAL_REQUIRED 的 output 是 approvalId。handler 抛 RuntimeException 合成 HANDLER_ERROR（output 只带异常类名，不带 message 防泄漏）。
- 测试：dispatch 8 绿，自带 FakeApprovalStore（未复制第三份 InMemoryPersist）。
- 验证：`mvn -f tepeu/pom.xml -pl kernel/dispatch -am test`（全树 44 绿）

## dispatch 刀遗留（人审可裁）

- policy 的 `PolicyDeniedException` / `ApprovalRequiredException` 在全合成裁决下已无消费者（dispatch 不抛、loop 看结果）——policy 人审时定删或留。

## 上一刀 policy

- 包 `com.tepeu.policy`。路径 `tepeu/kernel/policy`。依赖 identity、syscall、persist-api。不关库。
- 公开：`PolicyVerdict`（封闭三值 ALLOW/DENY/NEED_APPROVAL）、`PolicyHook`（`evaluate(InvokeContext, Syscall)`）、`ApprovalStore`（ask 幂等 / decide 一次 / consume 取走即消费 / get / records）、`ApprovalRecord`、`PolicyDeniedException`、`ApprovalRequiredException`。
- 矩阵：`local/DefaultRuleMatrix`。`llm.*`、`execution.fs.read`、`execution.sandbox.probe` 放行；`execution.fs.write`、`execution.proc.*` 需批准；未登记 DENY。精确名 override 优先；`parse` 读 rules 文本。
- 适配：`com.tepeu.policy.persist.PersistedApprovalStore`。space `approval`（键 `ap-<seq>` 序号簿）+ `approval-pending`（会话+名称+指纹摘要索引，指向最新）。persist 无删除：决策与消费覆盖写置章（decidedAt / consumedAt）。审批绑 argsDigest，不单绑名称。
- 未做：dispatch 接线、`/approve` 命令、卫兵组合、标本的 SensitivePath/SensitiveCommand（第一刀不迁）。
- 验证：`mvn -f tepeu/pom.xml -pl kernel/policy -am test`（policy 11 绿；全树 36 绿）

## 已定（2026-09-13，session 遗留收口）

- `SessionStore.open` 不辨新建/读回；owner/workspace 与库里不一致 fail fast（IllegalStateException）。
- `SessionId` 禁 `/`，收在 identity 层构造器。
- session 五本账与 policy approvalId 的序号分配都是非原子读改写（`list().size()+1`），单进程单写者前提，loop 线程化前收口。

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
