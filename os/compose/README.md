# compose — 开机接线

将各组件的默认实现插到端口上；配错即响。不承载业务。

**发行默认**：`SqliteAssembly.file(dir)` 组装 SQLite WAL `SessionStore`（`kernel.sqlite`）+ `ApprovalStore`（`approvals.sqlite`）+ CapabilityBus + Loop + 空 PromptAssembly + 已注册 `/help` 的 CommandDispatcher + AuditSink。llm 默认仍 fake。真 HTTP / 预算顶由调用方注入，compose 不读密钥。

**测试/conformance**：`MemoryAssembly` 仍是内存件。发行路径**禁止**默认 `InMemoryApprovalStore`。

Policy 仍须调用方装配（未装配即 fail-closed，compose 不偷偷 ALLOW）。

合同（四端口齐 / 规范默认 vs conformance 内存件 / 禁内存审批发行）见 [`docs/os-handbook.md`](../../docs/os-handbook.md)「开机档」。

```bash
mvn -f os/pom.xml -pl compose test
```
