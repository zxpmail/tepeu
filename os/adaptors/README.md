# ② 适配环

驱动插头与可换实现。清单见 docs/os-baseplate.md §3。

## 已有（单机 conformance）

- `inmemory.InMemoryCapabilityBus` — 卫兵 → Policy → handler（fail-closed）
- `inmemory.InMemorySession` / `InMemorySessionRegistry` — 日志 + Inbox（时钟注入、租约 TTL 惰性回收）+ surface 替换

## conformance 套件（Pi /testing 四件套模式，随 kernel 包发布）

`com.tepeu.os.kernel.conformance`：`SessionConformance`（12 用例：seq 分配/保序/get、FIFO/ack/nack、
**死租约可回收**（拨时钟）、surface 替换代数、区间校验、**词汇表钉死 7 类**）+
`BusConformance`（13 用例：往返/重复注册/NOT_FOUND、deny/NEED_APPROVAL/异常/null 全 fail-closed、
取消拒、handler 失败可见、before→handler→after 顺序、卫兵中断时 Policy 不被调）+
`MutableClock`（可拨时钟）。

桥接：`KernelPortsSmokeTest`（@TestFactory）——**任何新实现（如 SQLite SessionStore）过同一套件即合格**。

```bash
mvn -f os/pom.xml test   # 22 用例
```

其余 Tool/MCP/Execution/LlmProvider/Compaction/AuditSink/Metering 等待黄灯切片。
