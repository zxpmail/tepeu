# CONTEXT.md

当前：**本机 Agent OS 骨架可演示**（五层已落码可证伪）。仍不是企业 Agent OS / 完整 OS。规范 ADR-016。诚实度 [docs/agent-os-gap.md](docs/agent-os-gap.md)。

组件 8：session / policy / bus / llm / loop / orchestration / execution / compose。词汇：identity / syscall。harness：conformance。

发行入口：`SqliteAssembly.file(dir)` → `kernel.sqlite` + `approvals.sqlite` + `workspace/` + 可选 `policy.rules`。llm 默认 fake；密钥由调用方注入。`MemoryAssembly` 仅测试。隔离仍报 **partial**。压缩后 `log.surfaceEpoch` 跳过上笔 digest；审批绑 argsDigest。

下一刀按痛点：⑤ UI、记忆平面、多副本 fencing。禁止称企业 Agent OS / 完整 OS。
