# compose — 开机接线

将各组件的默认实现插到端口上；配错即响。不承载业务。

**发行默认**：`SqliteAssembly.file(dir)` 组装 SQLite WAL（`kernel.sqlite` + `approvals.sqlite`）+ `DefaultRuleMatrix`（可选 `dir/policy.rules`）+ execution 工作区（`workspace/`）+ Loop + 空 PromptAssembly + `/help` + `/approve` + AuditSink。llm 默认仍 fake；密钥由调用方注入传输。

**测试/conformance**：`MemoryAssembly` 仍是内存件。发行路径**禁止**默认 `InMemoryApprovalStore`。

Policy：compose 装配 `PolicyRulesFile.builtins()`（syscall override + deny-path/deny-command + 内置清单）。`SequenceGuardHook` + `DoomLoopGuardHook` 注册于总线。`Wired` 含 `ProjectionBus`（UI 增量通知）与 `KnowledgeSource`（默认 empty）。

合同见 [`docs/os-handbook.md`](../../docs/os-handbook.md)「开机档」。

```bash
mvn -f os/pom.xml -pl compose -am test
```
