# compose — 开机接线

将各组件的默认实现插到端口上；配错即响。不承载业务。

当前：`MemoryAssembly` 组装内存 SessionStore + CapabilityBus + ApprovalStore。
Policy 仍须调用方装配（未装配即 fail-closed，compose 不偷偷 ALLOW）。

合同（四端口齐 / 规范默认 vs conformance 内存件 / 禁内存审批发行）见 [`docs/os-handbook.md`](../../docs/os-handbook.md)「开机档」。

```bash
mvn -f os/pom.xml -pl compose test
```
