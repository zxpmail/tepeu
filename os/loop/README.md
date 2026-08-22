# loop — ③ SessionLoop

一件事：claim → 有界 turn（`llm.generate`，可选工具）→ 完成证据门。阻塞式 + 显式门。不 import 具体 Tool 类。

工具：模型输出首行 `syscall <name>` → 先落 `TOOL_CALL` 再 `bus.invoke` → 落 `TOOL_RESULT`。拦截失败合成 RESULT。禁止把 `llm.generate` 当工具。

未做：maintenance / DoomLoop / PromptAssembly / Command / 真 HTTP tool_use 映射。

```bash
mvn -f os/pom.xml -pl loop test
```
