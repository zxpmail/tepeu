# CONTEXT.md

当前：**本机单写者内核可发行**（SQLite WAL schema v1 + `SqliteAssembly`；四端口过同一套 conformance；重启 recover / fork 种子区仍在）。仍不是 Agent OS，五层未钉死。规范 ADR-016。诚实度 [docs/agent-os-gap.md](docs/agent-os-gap.md)。

组件 7：session / policy / bus / llm / loop / orchestration / compose。词汇：identity / syscall。harness：conformance。

发行入口：`SqliteAssembly.file(dir)` → `kernel.sqlite` + `approvals.sqlite`。`MemoryAssembly` 仅测试，不得当生产默认。

下一刀：Compaction 挂 maintenance，或审批规则矩阵，或 execution.* 沙箱（黄灯）。禁止再写第三份投影。禁止称骨架可演示。
