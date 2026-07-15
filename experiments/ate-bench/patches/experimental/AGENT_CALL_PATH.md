# Agent 核心调用路径（深度 ≤ 3）

阅读本文件即可回答「用户发一条带工具的聊天后，下一步执行什么」。

```
1. ChatController.stream
      → AgentOrchestrator.streamTurn
2. AgentOrchestrator.streamTurn
      → 绑定 FileTools/ShellTools 工作区
      → 组装 Prompt（技能 / @文件 / 历史）
      → ChatService.streamWithTools
3. ChatService.streamWithTools
      → ToolRegistry.beans()（清单见 Tools.java）
      → ChatClient + ToolCallbacks（Spring AI 内部工具循环）
```

## 新增一个工具要改什么？

1. 新建 `*Tools.java`（`@Component` + `@Tool` 方法）
2. 在 `Tools.java` 的 `toolRegistry` 里加一行 `registry.register(...)`
3. 完成。不要改 `ChatService`。
