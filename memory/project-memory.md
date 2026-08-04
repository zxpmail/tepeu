# Project Memory — Tepeu Agentic OS

## Tech Stack
- **Runtime**: Java 21 (virtual threads)
- **Backend**: Spring Boot 4.0+ + Spring AI 2.0.0 GA
- **Agent tools**: 自研细粒度 `*Tool`（`@Tool`；未接 agent-utils）
- **Database**: SQLite (WAL mode)
- **Frontend**: React 18 + TypeScript 5 + Tailwind CSS 4 + Vite 6
- **Build**: Maven 3.9 (backend) + npm (frontend)
- **Deploy**: Docker multi-stage, single JAR；应用版本 **0.2.0**（Harness）

## Architecture
- Monorepo: `backend/` (Spring Boot) + `frontend/` (Vite/React)
- Frontend dev: Vite proxy to port 30141
- Frontend prod: built to `backend/src/main/resources/static/`
- SQLite schema auto-created on startup via DatabaseConfig.java
- All API responses use unified `ApiResponse<T>` wrapper
- React state management: useState（hooks 局部）+ props（原 EventLoop 计划已退役 ADR-003，eventLoop.ts 已删）
- No authentication in Phase 1 (local-only mode)

## 对话 / 工具运行时（DEV-PLAN「对话」切片，勿称 Spec Phase 2）
- **Chat 链路**：`ChatController` → `AgentOrchestrator` → `ChatService` / `ChatModelFactory`（DB 解密 key → Spring AI `ChatModel`）。SSE `message`：`token|final|error` + 工具事件。
- **会话**：`session` + `message`；`useChat` 用 fetch+ReadableStream（非 EventSource）。
- **工具（Phase 11）**：`ToolRegistry` + `ListDirTool`/`ReadFileTool`/`WriteFileTool`/`SearchFileTool`/`RunCommandTool`/`ReadOutputTool` 各自独立注册；`CommandOutputStore` 供同轮续读命令输出。可视化装饰器仍走 deprecated `ToolCallback...`（ADR-007）。SSE `toolKind`：`file_list`/`file_read`/`file_write`/`file_search`/`shell`/`shell_output`/`mcp`/`other`。共享基类 `WorkspaceBoundTool` 处理 workspace 绑定与路径安全解析。
- **testConnection**：在 `ChatService`（避与 `ChatModelFactory` 循环依赖，ADR-007）。
- **成本**：会话级 + **workspace 累计**（`GET /api/workspace/:id/stats`，顶栏/工作区列表）；完整仪表盘属 Spec Phase 2 M2.4。
- **Hook（Phase 5 / M2.3）**：`HookingToolCallback`；按 `toolKind` 审批（`shell`/`mcp`/`file_delete` 需批；`file_write`/`shell_output` 免批）；同会话同工具+参数授权；`HostChannelGuard` 覆盖 REST/终端；`HallucinationGuard`；本机实例令牌 `X-Tepeu-Token`；`ApprovalStore.enableAutonomous` 供自主调度免人工批。
- **自主调度（Phase 10）**：`ScheduleService` ticker；卡死 RUNNING 超时恢复；用量经 `TokenCostEstimator` 入 task；空回复状态 `EMPTY`。
- **后台任务通知（Phase 13）**：`TaskEventNotifier`（SSE hub）+ `GET /api/task-events` 常驻 SSE；`ScheduleService` 终态发布 `task_completed`/`task_failed`（payload 含 scheduleId/scheduleName/workspaceId/sessionId/message）；前端 `useNotifications` 模块 store（useSyncExternalStore）+ 单例 EventSource + 可选浏览器 Notification；`NotificationBell` 顶栏徽章/下拉（点击跳会话或自主面板）；`ScheduleView` 订阅 onTaskEvent 按 workspaceId 刷新 + 完成/失败时间戳。**独立通道不复用 /api/events**，理由见 ADR-013。
- **文件变更通知（Phase 12）**：`FileWatcherService`（JDK WatchService）递归监听全部 workspace 根目录；`GET /api/events` 常驻 SSE 推 `file_changed`（含 workspaceId，operation=create/modify/delete）；前端 `WorkspaceEventsProvider` 用事件源喂 `workspaceEventBus`；`useFileBrowser` 按当前工作区过滤 + 300ms 防抖重载，`FileBrowserView` 同时刷目录树。忽略目录名：`.git/node_modules/target/dist/.claude/.forge`。设计见 ADR-012。
  - **事件合并（2026-08-04 后补）**：`FileWatcherService` 250ms 窗口内同 (workspace,path) 只广播一条，保留「最显著」operation（delete > create > modify），降低频繁 MODIFY 刷屏。
  - **多 tab 共享连接（2026-08-04 后补）**：`frontend/src/lib/sharedFileEvents.ts` 用 BroadcastChannel + localStorage 心跳做 leader 选举，**仅 leader tab 持有** EventSource('/api/events')，收到后经 channel 广播给所有 tab（含自己）；leader 失效后其他 tab 接管；无 BroadcastChannel 时回退直连。
- **多 Agent（Phase 6 / M2.1）**：`MultiAgentOrchestrator` 三角色流水线 + `Goal`；`POST /api/multi-agent/stream`；`MultiAgentView`。
- **MCP（Phase 7 / M2.2）**：`McpToolBridge` 并入 ChatService；工具名 `mcp_*`；默认 `spring.ai.mcp.client.enabled=false`；`GET /api/mcp/status`。
- **成本（Phase 8 / M2.4）**：`workspace_budget` + `BudgetService`；告警阈值 + 可选硬门禁；`CostDashboardView`；超预算阻断 chat/multi-agent。

## 外部参照（权威在 ADR）
- ADR-008（Vibe-Trading）· ADR-009（ATE 裁切）— 细节勿在此重复。

## 已知坑点 / Gotchas

- **构建工具是 Maven（pom.xml），不是 Gradle**。`backend/gradle/` 是空目录残留，无 gradlew。后端命令用 `mvn`。DEV-PLAN/dev-map 曾误写 Gradle，已于 2026-07-11 修正。
- **Spring AI 2.0 GA starter 命名为 `spring-ai-starter-model-*`**（openai/anthropic/ollama）。旧名 `spring-ai-*-spring-boot-starter` 在 2.0 已废弃，BOM `spring-ai-bom:2.0.0` 不再管理——用了会报 "version is missing"。
- **Spring AI 2.0 + Boot 4.0.7 实测无冲突**（Phase 2 验证：ChatModel 程序化构建 + `stream()` 编译运行均正常）。原 spring-projects/spring-ai#6465 对齐担忧排除。
- **JdbcTemplate + RowMapper 若捕获注入的 blank-final 字段，必须把 RowMapper 移进构造器赋值**（field initializer 在 ctor body 前执行，会触发 "might not have been initialized"）。
- 前端无独立 `lint` 脚本（仅 typecheck）；后端未配 checkstyle。
- **Boot 4 默认 Jackson 3**（`tools.jackson.*`），不是 Jackson 2（`com.fasterxml.*`）。注入 `ObjectMapper` 必须用 `tools.jackson.databind.ObjectMapper`，否则报「无 ObjectMapper bean」。Jackson 3 仍兼容旧 `com.fasterxml.jackson.annotation.*` 注解（实测 `@JsonInclude(NON_NULL)` 生效）。
- **SQLite JDBC URL 不要带 `?mode=wal`**：Windows 下 `?` 是非法文件名字符 → `SQLITE_CANTOPEN`；WAL 已由 `DatabaseConfig` 的 `PRAGMA journal_mode=WAL` 设置。
- **本地 Maven 仓库在 `D:\maven\repo`**（非默认 `~/.m2`）。
- **§7.4 加密主密钥文件**：`<user.home>/.tepeu/master.key`（AES-256-GCM，32B，`enc:v1:` 存储格式）。**需备份，丢失则已存 API key 不可恢复**。配置项 `tepeu.security.master-key-file`。服务层返回明文 key 给内部调用（Phase 2 agent）；HTTP 永远脱敏。见 ADR-006。
- **`mvn spring-boot:run` fork 的子 JVM 不会被 `TaskStop` 杀掉**：停服务要 `taskkill //F //PID <java-pid>`（`netstat -ano | grep 30141` 找 PID），否则端口 30141 被占 → 下次启动报 "Port already in use"。
- **gstack browse 多步流程必须用 `chain`**：单条 `$B <cmd>` 之间不保留页面状态（每次回到 about:blank）；`$B js` 不 await Promise（不能用它做延时）；用 `wait --networkidle` 做真实等待。
- **FileBrowserView mount 自动加载已修复**（2026-07-11）：原 `useFileBrowser` 无 mount 触发，须点 `~` 面包屑才列文件；已给 `FileBrowserView` 加 `useEffect(() => loadFiles('/'), [loadFiles])`。开 Files 即列文件（gstack 验：`seed.txt` 自动出现，`GET /api/files/list` 自动 200）。
- **FileBrowserView 外部变更自动刷新**（2026-08-04，Phase 12）：外部/后台进程修改工作区文件后 5 秒内自动反映（FileWatcherService + `/api/events` SSE）。注意：常驻 SSE 连接会让 gstack `wait --networkidle` 永不触发，浏览器 E2E 用 `wait text=` 代替。
- Phase 1 **功能验收已于 2026-07-11 通过**（API / 加密 / 浏览器视觉）。后续已合入工具写能力、ATE、远程 git。
- **Git**：远程 `zxpmail/tepeu`，本地 `main` 跟踪 `origin/main`。尚无 v0.1.0 tag / GitHub Release（可选）。
- **LLM 可达性**：视本机网络而定；公有云 API 可能需代理；可用兼容端点或本地 Ollama。
- **Phase 3 新增前端依赖**：highlight.js、marked、xterm、xterm-addon-fit（Phase 3 构建验证通过）。
- **FileController 端点更新（Phase 3）**：`GET /api/files/history`、`POST /api/files/restore/{id}`、`POST /api/files/version`。全部接受可选的 workspaceId 参数。基于 FileVersionService（新增）。
- **MemoryController 搜索增强（Phase 3）**：`POST /api/memory/search` 新增可选的 `tags` 数组参数（SQLite `LIKE` 匹配 JSON 数组）。
- **Terminal WS 安全启用（Phase 3 C2）**：`/api/terminal/ws` 在 `WebSocketConfig` 注册，origin 锁 localhost + `TerminalWebSocketHandler` 远程地址校验 + Jackson 序列化取代手写 JSON + GBK charset 支持 Windows 中文输出。
- **Frontend 新增面板（Phase 3）**：MemoryView（搜索/创建/编辑/删除/标签过滤/来源追溯 + useMemory hook）、FileBrowserView 增强（highlight.js 语法高亮 + marked Markdown + 图片预览 + 版本面板 + 拖拽上传）、TerminalView（xterm.js + useTerminal WebSocket hook + AI CLI 自然语言→命令翻译）。
- **Workspace 文件隔离（Phase 3 M4）**：Workspace 模型新增 `root_path` 列。新 workspace 自动默认 `workspaces/<id>`，历史 workspace（root_path=null）回填为 `workspaces/<id>`。FileController 按 workspace 解析文件目录。
- **Phase 3 全部验证通过**：70 后端测试通过 + tsc 0 错误 + frontend build 2.69s。

