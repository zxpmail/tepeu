# Phase 2 实现计划 — Agent 对话（SSE 流式）

> 2026-07-11 dev-builder Loading Phase 产出。DEV-PLAN Phase 2 为权威；本文件是其 Task 分解 + 关键决策。
> **Foundation 已验证**：Spring AI 2.0.0 `ChatClient`/`StreamingChatModel`/`ChatModel` 在 classpath；app 带 starter 启动正常（Boot 4.0.7 无硬冲突）。版本对齐风险留 T1 首次真实流式调用验证。
> **Nature**：Backend + UI；Standard size。**Dispatch**：Backend Task → implementer（无 git，无 worktree）；UI Task → 主 session 直接写。

## 关键决策（需在 T1 前确认）
- **Boot 版本**：保持 4.0.7（spring-boot-starter-parent）。若 T1 流式调用暴露 4.1.0 对齐冲突 → 升 Boot 到 4.1.x（依赖升级，需用户批准）。
- **SSE 实现**：`POST /api/chat/stream` 返回 `SseEmitter`（或 `Flux<ServerSentEvent>` + WebFlux? 当前是 servlet 栈 → 用 `SseEmitter`）。事件 schema 对齐 Spec §5.3.1：`thinking` / `tool_call` / `tool_result` / `final` / `error`。
- **工具循环**：Spring AI 原生 tool-calling（`ChatClient.prompt().tools(...)` + `@Tool` 方法或 `ToolCallback`）优先；agent-utils（社区、`provided`、当前未用）作为可选适配层——若 Spring AI 原生工具够用则不引入 agent-utils（YAGNI）。
- **多模型路由**：三个 starter 各自动配置 `ChatModel` bean（OpenAiChatModel / AnthropicChatModel / OllamaChatModel）。按 provider 选择用哪个。需一个 `ChatModelRouter` 按 `llm_provider.enabled + defaultModel` 选模型。
- **API Key**：从 `llm_provider` 表读（已加密，CryptoService.decrypt）注入 ChatModel，或用 Spring AI 的 base-url/api-key 配置 + per-request override。
- **M7（carry-over）**：T0 可选——加 CryptoService 启动期自检（flag 缺 `enc:v1:` 前缀的行）。

## Task 分解
**Backend（implementer dispatch + TDD）：**
- **T0（可选）**：CryptoService 启动期明文检测（M7）。
- **T1**：Spring AI foundation + ChatModelRouter + ChatService——多模型选择、从 DB 读 key 注入；RED 测试：stream 一个 chat completion（需真实 key，验收依赖）。**首次验证 Boot 4.0.7 对齐。**
- **T2**：`ChatController` SSE 端点 `POST /api/chat/stream`（`SseEmitter`，事件 schema §5.3.1）+ `error` event 兜底。
- **T3**：`AgentOrchestrator`——Plan→Tool→Observe→Loop（Spring AI tool-calling）；工具：FileTool（受限到 workspace basePath）/ 可选 ShellTool/WebTool。
- **T4**：`SessionService`——session CRUD（session 表已存在）+ 消息持久化（chat 表或 session 扩展；Spec §6.3 session 表无消息列 → 决策：加 message 表 or 存 session.title）。

**UI（主 session 直接写）：**
- **T5**：`useChat` hook（SSE 流式状态：EventSource 消费、逐 token、工具卡片状态）+ `ChatView`。
- **T6**：chat 组件 `MessageList`/`MessageBubble`/`StreamRenderer`/`ToolCallCard`/`ReasoningDisplay`。
- **T7**：`ProviderSettingsView`——API key 配置、模型选择、连接测试（后端 `/api/provider/*` 已就绪）。
- **T8**：`SessionListView`——会话列表、新建、切换（调 `/api/workspace/{id}/switch` + session 端点）。
- **T9**：首次 LLM key 引导（SetupWizard 雏形）。

## 验收（DEV-PLAN Phase 2）
- Provider 设置页填 OpenAI key → 连接测试成功。
- 新建对话 → 发消息 → SSE 流式显示（思考+工具+回答）。
- 工具调用结果可视化。
- 切换模型（OpenAI↔Anthropic↔Ollama）对话正常。
- 历史持久化，刷新恢复。
- Primary metric：`cd backend && mvn -q test && cd ../frontend && npm run typecheck` exit 0 + 端到端 SSE 可用。

## 真实依赖
- **需一个真实 LLM API key**（OpenAI 或 Anthropic）才能验端到端流式 + 工具循环。Ollama 本地无 key 但需本地 ollama 服务。→ 验收前用户提供 key 或起 ollama。

## 执行建议
- 🔴 高 + ~13 文件 + 跨前后端 → 建议在**新鲜上下文**（/clear 后）执行，一个 Task 一个 implementer dispatch + review。
- 本会话上下文已大量消耗； depleted context 跑 🔴 Phase 风险高（半成品）。
