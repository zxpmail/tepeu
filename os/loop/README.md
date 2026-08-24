# loop — ③ SessionLoop

一件事：claim → 有界 turn（`llm.generate`，可选工具 / plan）→ 完成证据门。阻塞式 + 显式门。不 import 具体 Tool 类。

工具：模型输出首行 `syscall <name>` → 先落 `TOOL_CALL` 再 `bus.invoke` → 落 `TOOL_RESULT`。成功 `execution.fs.write` 会把内容写入 ContentStore 并在 RESULT 放 `locator`。
计划：首行 `plan ...` → 落 `PLAN_STEP`。
完成：主路永远过 REPLY；有工具则 TOOL_PAIR；有 PLAN_STEP 则 PLAN；有 locator 则 FILE。

未做：真 HTTP tool_use 映射。PromptAssembly / Command 在 `orchestration/`，Loop 不依赖（`LoopConfig.system` 只转发）。
开 turn 前：`Metering.withinBudget` 为 false 则 STOPPED，不 claim。
DoomLoop：同工具同输入连续 3 次 → 总线卫兵 NEED_APPROVAL（第三刀须审批）。
maintenance：`SessionLoop.maintain` 独占窗口（强制上限 / NOW 让位 / latch=`inbox.enqueued()`）。
压缩：`CompactionWork` 挂该窗；turn 内若 live surface 超过 `LoopConfig.compactOverflow`（默认 40）则在下一次 generate 前压一步。不删审计；种子区不压。

```bash
mvn -f os/pom.xml -pl loop -am test
```
