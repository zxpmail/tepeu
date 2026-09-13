# Handoff — Tepeu（从零重写）

> 到达后：本文件 → `CONTEXT.md` → [`docs/rewrite-0.md`](../docs/rewrite-0.md)。  
> 标本：`legacy/os-9/`。新库根 `tepeu/`。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 v1 档案。

**Last updated**: 2026-09-13（dispatch 过审 + 契约审计收口 cb68c03；llm 刀已落待人审）

## 当前阶段

- 标本已迁：`legacy/os-9/os/`、`legacy/os-9/host/`。
- 已写已推：`identity`、`syscall`、`persist-api`、`persist-sqlite`、`session`、`policy`、`dispatch`（2026-09-13 人审过）、`llm`（gateway+fake，已落待人审）。
- 2026-09-13 契约审计收口（`cb68c03`）：名字空白/policy 抛/审批通道抛合成 DENIED；handler 回 null 合成 HANDLER_ERROR；ArgDigest 长度前缀。契约其余欠账归 conformance 刀。
- 纪律：一个组件写完，人审查通过才能继续。下一刀未圈（run/ 层：execution 或 loop）。

## 本刀 llm（gateway+fake）

- 路径 `tepeu/run/llm/`（gateway + fake），父 POM 链 run → llm。gateway 依赖 session、syscall；不知道 dispatch / host。fake 只依赖 gateway、syscall。
- gateway 公开：`LlmGateway`（generate(SessionLog)）+ `visible` 纯函数（事件日志 → 模型可见序列）+ `LlmBackend`（实现口）+ `LlmMessage`/`Role`。
- **gateway 只读不写**：不追加事件、不记用量；assistant 事件由 loop 追加（§5 步骤归 loop）。结果与用量透传后端返回值。
- 可见词汇第一刀：`USER_MESSAGE`/`ASSISTANT_MESSAGE` 按日志序；TOOL_CALL/TOOL_RESULT/REASONING/PLAN_STEP 不可见（工具落地时再裁 TOOL_RESULT）。
- fake：离线固定文本 `FakeBackend.REPLY`，固定 Usage(1,1)。
- 接线（load 落地时）：`dispatch.register("llm.generate", (ctx, s) -> gateway.generate(session.log()))`，参数留空、会话从 ctx 取。
- 测试：gateway 3 绿 + fake 1 绿。
- 验证：`mvn -f tepeu/pom.xml test`（全树 53 绿，15 模块）

## 上一刀 dispatch

- 包 `com.tepeu.dispatch`。路径 `tepeu/kernel/dispatch`。依赖 identity、syscall、policy。不依赖 session / persist，不写日志。
- 公开：`Dispatch`（register / names 字典序 / invoke）+ `Handler`（函数接口）。
- **全部合成结果（2026-09-13 裁）**：不抛异常。invoke 序：取消 → 授权 → 查表 → 调用。错误码 `CANCELLED` / `DENIED` / `APPROVAL_REQUIRED` / `NOT_FOUND` / `HANDLER_ERROR`。
- 未装配 Policy 或审批通道按 DENIED（fail-closed）；策略返回 null 也 DENIED。NEED_APPROVAL **先 consume 后 ask**——顺序反了会把 decide 孤立掉。APPROVAL_REQUIRED 的 output 是 approvalId。handler 抛 RuntimeException 合成 HANDLER_ERROR（output 只带异常类名，不带 message 防泄漏）。
- **契约审计收口（2026-09-13）**：名字空白、策略抛错、审批通道抛错也合成 DENIED（原先会穿门抛异常）；handler 返回 null 合成 HANDLER_ERROR；`ArgDigest` 键值带长度前缀，`{"a":"b\nc=d"}` 不再与两键混淆。契约其余缺口（失败类型点名、seq 语义、put-后-list 序、单写者前提进 javadoc、保留码归属）归 conformance 刀。
- 测试：dispatch 12 绿（8+4），syscall 6 绿（5+1）。自带 FakeApprovalStore（未复制第三份 InMemoryPersist）。
- 验证：`mvn -f tepeu/pom.xml test`（全树 49 绿）

## dispatch 刀遗留（人审可裁）

- policy 的 `PolicyDeniedException` / `ApprovalRequiredException` 在全合成裁决下已无消费者（dispatch 不抛、loop 看结果）——policy 人审时定删或留。

## 上一刀 policy

- 包 `com.tepeu.policy`。路径 `tepeu/kernel/policy`。依赖 identity、syscall、persist-api。不关库。
- 公开：`PolicyVerdict`（封闭三值 ALLOW/DENY/NEED_APPROVAL）、`PolicyHook`（`evaluate(InvokeContext, Syscall)`）、`ApprovalStore`（ask 幂等 / decide 一次 / consume 取走即消费 / get / records）、`ApprovalRecord`、`PolicyDeniedException`、`ApprovalRequiredException`。
- 矩阵：`local/DefaultRuleMatrix`。`llm.*`、`execution.fs.read`、`execution.sandbox.probe` 放行；`execution.fs.write`、`execution.proc.*` 需批准；未登记 DENY。精确名 override 优先；`parse` 读 rules 文本。
- 适配：`com.tepeu.policy.persist.PersistedApprovalStore`。space `approval`（键 `ap-<seq>` 序号簿）+ `approval-pending`（会话+名称+指纹摘要索引，指向最新）。persist 无删除：决策与消费覆盖写置章（decidedAt / consumedAt）。审批绑 argsDigest，不单绑名称。
- 未做：dispatch 接线、`/approve` 命令、卫兵组合、标本的 SensitivePath/SensitiveCommand（第一刀不迁）。
- 验证：`mvn -f tepeu/pom.xml -pl kernel/policy -am test`（policy 11 绿；全树 36 绿）

## 已定（2026-09-13，可观测）

- 详尽日志 = 三本账写全 + host 一层诊断日志；内核组件不打行式日志（不打正文/args/密钥的纪律不变）。
- 被拒调用尝试：loop 记 `TOOL_RESULT` + `attrs.error` 短码，body 只带名；不新增事件词汇。
- host 落地时挂 slf4j（装配/启动/异常栈）；persist 补 kill -9 故障注入测试（归 conformance 或 loop 收口期）。
- 全文见 rewrite-0 §3.2。

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
