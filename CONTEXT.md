# CONTEXT.md

当前：**本机 Agent OS 骨架可演示**（五层已落码可证伪）。仍不是企业 Agent OS / 完整 OS。规范 ADR-016。诚实度 [docs/agent-os-gap.md](docs/agent-os-gap.md)。

组件 10：session / policy / persist / observation / bus / llm / loop / orchestration / execution / compose。词汇：identity / syscall。harness：conformance。⑤ 应用：`host/`（CLI，不在 os/）。

发行入口：host 建连接后包成 `Persist` → compose `SessionPersistence` / `ApprovalPersistence` + `workspace/` + 可选 `policy.rules`。适配器只认 Persist，不关库。llm 默认 fake；密钥由 **host** 注入（compose 不读）。`os/` 用 spring-jdbc，不起 Boot。内存内核 `InMemoryAssembly` 仅测试。隔离仍报 **partial**。压缩后 `log.surfaceEpoch` 跳过上笔 digest；审批绑 argsDigest。

红线 §6-8：完成权唯一（证据在 entries，宣判在 ③ `CompletionGate`），控制循环不唯一。内核无调度器；不按 CC 整机模式。重试资格统一入账本仍挂账。
下一刀按痛点：⑤ UI/SSE、记忆平面、多副本 fencing。`ProjectionBus` 是可选支撑接口（本机默认 Local 插头）；MQ 升版再换。禁止称企业 Agent OS / 完整 OS。运行时安全九宫格：[docs/archive/reference/agent-runtime-security-series.md](docs/archive/reference/agent-runtime-security-series.md)。不同线勿当内核参照：[tencent-harness-engineering.md](docs/archive/reference/tencent-harness-engineering.md)、[ai-eval-observability-pipeline.md](docs/archive/reference/ai-eval-observability-pipeline.md)、[terax-ai.md](docs/archive/reference/terax-ai.md)。
