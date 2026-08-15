# ② 适配环

驱动插头与可换实现。清单见 docs/os-baseplate.md §3.1。

## 已有（单机冒烟）

- `inmemory.InMemoryCapabilityBus` — 卫兵 → Policy → handler
- `inmemory.InMemorySession` / `InMemorySessionRegistry` — 日志 + Inbox + surface 替换

其余 Tool/MCP/Execution/LlmProvider/Compaction/AuditSink/Metering 等待黄灯切片。
