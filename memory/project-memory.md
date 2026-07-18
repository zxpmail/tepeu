# Project Memory — Tepeu Agentic OS

## Tech Stack
- **Runtime**: Java 21 (virtual threads)
- **Backend**: Spring Boot 4.0+ + Spring AI 2.0.0 GA
- **Agent tools**: spring-ai-agent-utils 0.10.0 (community, org.springaicommunity)
- **Database**: SQLite (WAL mode)
- **Frontend**: React 18 + TypeScript 5 + Tailwind CSS 4 + Vite 6
- **Build**: Maven 3.9 (backend) + npm (frontend)
- **Deploy**: Docker multi-stage, single JAR

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
- **工具**：`ToolRegistry` + `FileTools`（含 `write_file`）+ `ShellTools`（`run_command`）；可视化装饰器仍走 deprecated `ToolCallback...`（ADR-007）。
- **testConnection**：在 `ChatService`（避与 `ChatModelFactory` 循环依赖，ADR-007）。
- **成本**：会话级用量已有；**workspace 累计**未做（Spec §3.5 缺口）。
- **高危授权 / Hook / 多 Agent / MCP**：Product-Spec **§9 Phase 2**（Harness），见 ADR-008。

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

