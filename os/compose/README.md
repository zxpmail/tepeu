# compose — 开机接线

将各组件的默认实现插到端口上；配错即响。不承载业务。

**发行默认**：host 打开两份 `Persist`，compose `SqliteAssembly.file(dir, kernel, approvals)` 只认已打开的访问口。`SessionPersistence` / `ApprovalPersistence` 只访问、不关库。+ `DefaultRuleMatrix`（可选 `dir/policy.rules`）+ execution 工作区（`workspace/`）+ Loop + 空 PromptAssembly + `/help` + `/approve` + AuditSink。llm 默认仍 fake。sqlite 仅测试可见。

**测试/conformance**：内存内核 `InMemoryAssembly` 在测试源。发行路径**禁止**默认 `InMemoryApprovalStore`。端口套件与内存夹具在各组件 `src/test`，不进发行 jar。

Policy：compose 装配 `PolicyRulesFile.builtins()`（syscall override + deny-path/deny-command + 内置清单）。`SequenceGuardHook` + `DoomLoopGuardHook` 注册于总线。开机装 `Observation.install(ContextShapers.defaults())`。`Wired` 含 `ProjectionBus` 与 `KnowledgeSource`（默认 empty）。投影契约是接口；本骨架默认实现 `LocalProjectionBus`。其他组件可不实现、可不订阅。Redis/NATS/MQ 升版再换，开机不绑 broker。

合同见 [`docs/os-handbook.md`](../../docs/os-handbook.md)「开机档」。

```bash
mvn -f os/pom.xml -pl compose -am test
```
