# Development Plan — Tepeu Agentic OS

> This file records the project's development Phase breakdown, current progress, and remaining work.
> A new session should read this file first to understand the project status before continuing development.

---

## Phase 1: 项目骨架 + Web 工作台 + Workspace MVP

**Difficulty**: 🟡 中
**Nature**: Backend + UI

**Deliverables**:
- Spring Boot 4.0 + Java 21（虚线程）后端项目初始化（Maven，pom.xml）
- SQLite 数据库（WAL 模式），全部数据表：workspace、session、memory、task（含 outcome）、file_version
- Spring AI 2.0.0 GA 集成 + LLM Provider 凭证管理（§7.4：API Key 本地加密存储）
- agent-utils 依赖集成（spring-ai-agent-utils 0.10.0，工具适配层）
- 统一错误响应 `{code, message, details}` + SSE error event 骨架
- Vite + React 18 + TypeScript 5 + Tailwind CSS 4 前端项目初始化
- 多面板布局（标题栏 + 左侧 sidebar + 主内容区 + 右侧面板）
- 主题切换（深色/浅色，跟随系统）
- Workspace CRUD 全栈（REST API + UI：创建、列表、切换、删除）
- 文件浏览器面板（目录树 + 文件列表，基于文件操作 API）
- 开发模式 Vite 代理到后端；生产构建输出到 `resources/static/`
- 状态管理框架（Netty EventLoop 模式：dispatch→统一调度器→state）

**Key Files**:
- `src/main/java/com/tepeu/TepeuApplication.java` — 应用入口
- `src/main/java/com/tepeu/config/` — 数据源、LLM Provider、WebSocket 配置
- `src/main/java/com/tepeu/model/` — Entity（Workspace, Session, Memory, Task, FileVersion）
- `src/main/java/com/tepeu/repository/` — SQLite Repository 层
- `src/main/java/com/tepeu/service/` — WorkspaceService, MemoryService, FileService（骨架）
- `src/main/java/com/tepeu/controller/` — REST 端点（workspace, memory, files 骨架）
- `src/main/java/com/tepeu/dto/` — Request/Response DTO，统一错误格式
- `src/main/java/com/tepeu/agent/` — Agent Orchestrator 骨架 + LLM Provider 管理
- `src/main/resources/application.yml` — Spring Boot 配置
- `pom.xml` — 依赖管理
- `frontend/package.json` — 前端依赖
- `frontend/vite.config.ts` — Vite 配置 + 代理设置
- `frontend/src/main.tsx` — 应用入口
- `frontend/src/App.tsx` — 根组件 + 路由
- `frontend/src/components/layout/` — AppShell, TitleBar, Sidebar, PanelContainer
- `frontend/src/components/views/WorkspaceView.tsx` — Workspace 管理页
- `frontend/src/components/views/FileBrowserView.tsx` — 文件浏览页
- `frontend/src/components/common/` — ThemeToggle, FileTree, Panel
- `frontend/src/api/` — API 客户端（fetch 封装）
- `frontend/src/store/` — 状态管理（EventLoop 模式）
- `frontend/src/hooks/` — useWorkspace, useTheme, useFileBrowser
- `frontend/src/styles/index.css` — Tailwind 4 语义色值（CSS-first 配置，经 `@tailwindcss/vite` 插件，无 tailwind.config）

**Acceptance Criteria**:
- `mvn spring-boot:run` 启动成功，SQLite 自动建表，后端 API 可用
- 浏览器打开 `http://localhost:30141` 显示多面板布局
- 深色/浅色主题切换工作
- 创建 Workspace → 出现在列表 → 切换 → 删除，全栈可用
- 文件浏览器列出当前 workspace 目录文件
- `curl POST /api/memory` + `curl POST /api/memory/search` 正常
- LLM API Key 加密存储到 SQLite，可读回
- 统一错误响应在 400/404 时返回

**Primary metric**:
- `cd frontend && npm run build && cd ../backend && mvn -q test` exit 0 + 浏览器可见多面板布局

**Behavior**: 🟡 中（标准 dev-builder + code-review）

---

## Phase 2: Agent 对话（SSE 流式）

**Difficulty**: 🔴 高
**Nature**: Backend + UI

**Deliverables**:
- SSE 流式聊天 API（`POST /api/chat/stream`）：Agent Orchestrator 执行 Plan→Tool Call→Observe→Loop
- 多模型支持：OpenAI、Anthropic、本地 Ollama（通过 Spring AI 2.0）
- 工具调用集成：spring-ai-agent-utils（FileTool, ShellTool, WebTool）
- 推理过程展示：思考步骤 → 工具选择 → 工具结果 → 最终回答
- Chat UI：消息列表、流式逐 Token 渲染、工具调用可视化卡片
- LLM Provider 配置 UI（API Key 填写、模型选择、连接测试）
- Session 管理 UI（对话列表、新建、切换），数据持久化到 session 表
- 首次 LLM Key 配置引导

**Key Files**:
- `src/main/java/com/tepeu/controller/ChatController.java` — SSE 流式端点
- `src/main/java/com/tepeu/agent/AgentOrchestrator.java` — Plan→Tool→Observe→Loop 循环
- `src/main/java/com/tepeu/agent/tools/` — 工具适配层（FileTool, ShellTool, WebTool）
- `src/main/java/com/tepeu/service/` — ChatService, SessionService
- `frontend/src/components/views/ChatView.tsx` — 对话主视图
- `frontend/src/components/chat/` — MessageList, MessageBubble, StreamRenderer, ToolCallCard, ReasoningDisplay
- `frontend/src/components/views/ProviderSettingsView.tsx` — LLM 配置页
- `frontend/src/components/views/SessionListView.tsx` — 会话列表
- `frontend/src/hooks/useChat.ts` — SSE 流式状态管理
- `frontend/src/hooks/useSessions.ts` — 会话 CRUD

**Acceptance Criteria**:
- Provider 设置页填入 OpenAI API Key → 连接测试成功
- 新建对话 → 发送消息 → SSE 流式显示 Agent 回复（思考 + 工具调用 + 回答）
- 工具调用结果显示（如创建文件后显示文件内容）
- 切换模型（OpenAI ↔ Anthropic ↔ Ollama）后对话正常
- 对话历史持久化，刷新页面后恢复
- 输入"删除这个文件" → Agent 调用文件工具删除并告知结果

**Primary metric**:
- `cd backend && mvn -q test && cd ../frontend && npm run typecheck` exit 0 + 端到端 SSE 流式对话可用

**Behavior**: 🔴 高（SSE 流式 + 工具循环较复杂，每段代码后自我评审，动刀甚微）

---

## Phase 3: 记忆 UI + 文件操作增强 + 终端

**Difficulty**: 🟡 中
**Nature**: Backend + UI

**Deliverables**:
- 记忆管理面板：搜索（全文检索）、浏览、编辑、删除、来源追溯（memory.source 字段）
- 记忆标签过滤（tags JSON array → tag filter，按 workspace 过滤）
- 文件预览：文本/代码语法高亮、Markdown 渲染、图片预览
- 文件版本历史：版本列表、回滚、差异对比（file_version 表）
- 拖拽上传：前端 drag-drop → /api/files/upload
- WebSocket 终端 UI：xterm.js 集成（/api/terminal/ws）
- AI 辅助命令行：自然语言 → Shell 命令翻译、错误解释

**Key Files**:
- `frontend/src/components/views/MemoryView.tsx` — 记忆面板
- `frontend/src/components/memory/` — MemorySearch, MemoryCard, MemoryEditor, SourceTrace
- `src/main/java/com/tepeu/controller/FileController.java` — 预览/版本历史端点
- `src/main/java/com/tepeu/service/FileVersionService.java` — 版本管理
- `frontend/src/components/views/FilesView.tsx` — 文件管理器主视图
- `frontend/src/components/file/` — FilePreview, FileDiff, VersionList, DragUpload, CodeViewer
- `frontend/src/components/views/TerminalView.tsx` — 终端视图（xterm.js）
- `frontend/src/hooks/useFileOps.ts` — 文件操作状态
- `frontend/src/hooks/useTerminal.ts` — WebSocket 终端连接管理
- `frontend/src/hooks/useMemory.ts` — 记忆检索与编辑

**Acceptance Criteria**:
- 记忆搜索 → 结果列表 → 展开查看详情 → 编辑 → 删除, 来源标注可见
- tag 过滤 + workspace 过滤正确
- 拖拽文件到浏览器 → 上传成功，目录树刷新
- 文本文件点击 → 语法高亮预览；Markdown → 渲染；图片 → 缩略图
- 文件版本列表可展开 → 回滚到旧版本
- 终端连接 → 执行 `ls`、`cat`、`pwd` 等基础命令
- 输入"显示当前目录" → AI 翻译为 `ls -la` 并执行，输出可见

**Primary metric**:
- `cd backend && mvn -q test && cd ../frontend && npm run typecheck` exit 0 + 记忆面板搜索 + 文件预览 + 终端命令执行可用

**Behavior**: 🟡 中（Terminal WS + AI CLI 需关注边界条件）

---

## Phase 4: 集成与发布（v0.1.0）

**Difficulty**: 🟢 低
**Nature**: Integration

**Deliverables**:
- 首次启动引导流程：LLM Key 配置 → 默认 workspace 创建 → 欢迎消息
- Dockerfile（多阶段构建：frontend build → Spring Boot JAR）
- `.dockerignore`
- `docker-compose.yml`（单服务 + SQLite 数据持久化卷）
- 全面自检：列出已知限制 + Phase 2/3 路线图（清晰对齐 spec §9）
- v0.1.0 版本发布（Git tag + GitHub Release + 发布说明）

**Key Files**:
- `frontend/src/components/views/SetupWizard.tsx` — 首次启动引导
- `Dockerfile` — 多阶段构建
- `docker-compose.yml` — 单服务编排
- `RELEASE_NOTES-v0.1.0.md` — 发布说明

**Acceptance Criteria**:
- 容器首次启动 → 引导界面出现 → 配置 API Key → 进入对话
- `docker build -t tepeu:v0.1.0 .` 成功，镜像 < 300MB
- `docker compose up` → 浏览器打开 → 全功能可用：workspace → chat → memory → file ops → terminal
- GitHub Release 发布（Docker image tag + release notes）

**Primary metric**:
- `docker compose up` → 浏览器访问 → 创建 workspace → 发消息 → 完整可用

**Behavior**: 🟢 低（标准流程）

---

## 远期 Phase（v0.1.0 之外）

> 对应 Product-Spec §9 Phase 2/3，非 v0.1.0 范围。
> - 多 Agent 协作（Planner/Implementer/Reviewer）
> - MCP 协议支持
> - Hook 安全网（幻觉检测、危险操作拦截）
> - 成本仪表盘
> - WASM+V8 运行时（Phase 3 远期）
> - 应用市场
> - 移动端适配

## Phase 1 code-review carry-over（2026-07-11）

> Phase 1 code-review 发现、明确推迟到后续 Phase 的项（已在代码留 TODO/注释）：
> - **M4 Workspace 文件隔离**（→ Phase 3 文件增强）：FileController 当前用单一全局 `<cwd>/workspace`，多 workspace 共享同一文件视图（违反 §3.4）。Phase 3 加 `workspace.root_path` 列、按工作区解析 basePath。
> - **M3 Memory tags 过滤**（→ Phase 3 记忆 UI）：tags 过滤参数已从 API 全链路移除（原实现静默无效）。Phase 3 记忆面板实现真正的 tag 过滤。
> - **C2 Terminal WebSocket**（→ Phase 3 终端）：已禁用端点（原实现 = 跨域无限制 shell / RCE）。Phase 3 启用时须：origin 锁 localhost + 显式 per-session enable + Jackson 序列化输出。
> - **m3 `ApiResponse @JsonInclude`**：暂留 Jackson 2 注解（实测生效）；`tools.jackson.annotation.JsonInclude` 类路径确认后再迁移 Jackson 3。

---

## Tech Stack

| Layer | Technology | Version | Notes |
|-------|-----------|---------|-------|
| 运行时 | Java | 21 LTS | 虚线程（Virtual Threads） |
| 后端框架 | Spring Boot | 4.0+ | 支持 Spring AI 2.0 |
| AI 集成 | Spring AI | 2.0.0 (GA) | 2026-06-12 发布，需 Boot 4.0 |
| Agent 工具 | spring-ai-agent-utils | 0.10.0 | 社区项目 (org.springaicommunity)，代理隔离 |
| 数据库 | SQLite | — | WAL 模式，单机嵌入 |
| 前端框架 | React | 18.x | 组件化生态 |
| 前端语言 | TypeScript | 5.x | 类型安全 |
| 构建工具 | Vite | 6.x | HMR + 代理到后端 |
| UI 样式 | Tailwind CSS | 4.x | 语义色值系统（@tailwindcss/vite） |
| 终端 | xterm.js | 5.x | 浏览器终端仿真 |
| 包管理（前端） | npm | 11.x | — |
| 构建（后端） | Maven | 3.9.x | pom.xml |
| 通信协议 | SSE + REST + WebSocket | — | 实时流式 + 标准 REST |
| 部署 | Docker | — | 多阶段单 JAR |

## Database Tables

| Table Name | Created In | Purpose |
|------------|-----------|---------|
| `workspace` | Phase 1 | 项目定义，type: personal/enterprise |
| `session` | Phase 1 | 对话会话 |
| `memory` | Phase 1 | 记忆条目，全文索引 |
| `task` | Phase 1 | 任务记录，含 outcome |
| `file_version` | Phase 1 | 文件版本历史 |

## Development Rules

- **四步验证**：每个 Phase 完成后必须通过 Code Review → Test Completeness → Compile Verify → Functional Test
- **提交信息格式**：`phase-<N>: <description>`
- **包管理器**：Maven（后端）/ npm（前端）
- **开发模式**：`cd frontend && npm run dev`（Vite 代理 `localhost:30141` → 后端）
- **生产构建**：`cd frontend && npm run build` → 产物到 `src/main/resources/static/`
- **Agent 原语优先**：引用 spring-ai-agent-utils，不重复造轮子
- **参数规范**：函数参数 > 4 个用 DTO/Record
- **状态管理**：React `useState`（hooks 局部）+ props（EventLoop 模式已退役，见 ADR-003；统一调度器推迟到确有需要时）
- **流式优先**：所有 Agent 输出 SSE 流式，前端逐 Token 渲染
- **第三方依赖标注**：非官方 Spring 组件（community/非GA）在版本列标注
