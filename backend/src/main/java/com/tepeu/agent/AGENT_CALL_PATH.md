# Agent 核心调用路径（深度 ≤ 3）

阅读本文件即可回答「用户发一条带工具的聊天后，下一步执行什么」。

```
1. ChatController.stream
      → AgentOrchestrator.streamTurn
2. AgentOrchestrator.streamTurn
      → 绑定 ListDir/Read/Write/Search/RunCommand 工作区 + RunCommand/ReadOutput 会话
      → 组装 Prompt（技能 / @文件 / 历史）
      → ChatService.streamWithTools(..., sessionId)
3. ChatService.streamWithTools
   - ToolCallbacks.from(registry) + McpToolBridge（mcp_*）
   - HookingToolCallback（按 toolKind：shell/mcp 审批；file_write 免批）
   - ToolEventEmittingCallback（可视化 + toolKind）

多 Agent（Spec M2.1）：
1. MultiAgentController.stream
      → MultiAgentOrchestrator.run（Planner → Implementer → Reviewer）
2. Implementer 走 ChatService.chatWithTools（同 Hook/工具面）
3. Planner/Reviewer 走 ChatService.chat（无工具）
      → ToolRegistry.beans()（清单见 Tools.java）
      → ChatClient + ToolCallbacks（Spring AI 内部工具循环）
```

## 新增一个工具要改什么？

1. 新建 `*Tool.java`（`extends WorkspaceBoundTool` + `@Component` + `@Tool` 方法）
2. 在 `Tools.java` 的 `toolRegistry` 里用 **与 @Tool 名相同** 的键 `registry.register(...)`
3. 在 `ToolKinds` 登记 toolKind；若需会话态再加 bindSession
4. 在 `AgentOrchestrator`（及多 Agent）里注入并加 bind/unbind 配对
5. 完成。不要改 `ChatService`。
