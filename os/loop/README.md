# loop — ③ SessionLoop

一件事：claim → 有界 turn（`llm.generate`，可选工具）→ 完成证据门。阻塞式 + 显式门。不 import 具体 Tool 类。

工具：模型输出首行 `syscall <name>` → 先落 `TOOL_CALL` 再 `bus.invoke` → 落 `TOOL_RESULT`。拦截失败合成 RESULT。禁止把 `llm.generate` 当工具。

未做：真 HTTP tool_use 映射。PromptAssembly / Command 在 `orchestration/`，Loop 不依赖（`LoopConfig.system` 只转发）。
开 turn 前：`Metering.withinBudget` 为 false 则 STOPPED，不 claim。
DoomLoop：同工具同输入连续 3 次熔断，NUDGE 写入 TOOL_RESULT。
maintenance：`SessionLoop.maintain` 独占窗口（强制上限 / NOW 让位 / latch=`inbox.enqueued()`）。

```bash
mvn -f os/pom.xml -pl loop test
```
