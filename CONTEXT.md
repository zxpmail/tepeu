# CONTEXT.md

当前：**本机 Agent OS 骨架可演示**（五层已落码可证伪）。仍不是企业 Agent OS / 完整 OS。规范 ADR-016。诚实度 [docs/agent-os-gap.md](docs/agent-os-gap.md)。

组件 10：session / policy / persist / observation / bus / llm / loop / orchestration / execution / compose。词汇：identity / syscall。harness：conformance。⑤ 应用：`host/`（CLI，不在 os/）。

发行入口：`SqliteAssembly.file(dir)` → `persist/sqlite` 的 `SqlitePersist`（`kernel.sqlite` + `approvals.sqlite` 分库、一套 JDBC）+ `workspace/` + 可选 `policy.rules`。llm 默认 fake；密钥由 **host** 注入（compose 不读）。内存内核 `InMemoryAssembly` 仅测试。隔离仍报 **partial**。压缩后 `log.surfaceEpoch` 跳过上笔 digest；审批绑 argsDigest。

下一刀按痛点：⑤ UI/SSE、记忆平面、多副本 fencing。`ProjectionBus` 是可选支撑接口（本机默认 Local 插头）；MQ 升版再换。禁止称企业 Agent OS / 完整 OS。运行时安全九宫格：[docs/archive/reference/agent-runtime-security-series.md](docs/archive/reference/agent-runtime-security-series.md)。不同线勿当内核参照：[tencent-harness-engineering.md](docs/archive/reference/tencent-harness-engineering.md)、[ai-eval-observability-pipeline.md](docs/archive/reference/ai-eval-observability-pipeline.md)、[terax-ai.md](docs/archive/reference/terax-ai.md)。
