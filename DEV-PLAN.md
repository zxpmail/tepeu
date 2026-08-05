# Development Plan — Tepeu Agentic OS

> This file records the project's development Phase breakdown, current progress, and remaining work.
> A new session should read this file first to understand the project status before continuing development.

> **命名注意（避免与 Product-Spec 混淆）**  
> - **本文件 Phase 1–4** = v0.1.0 **交付切片**（骨架 → 对话 → 记忆/终端 → 发布）。**已完成。**  
> - **本文件 Phase 5–9** = Product-Spec §9 **Phase 2（Harness / v0.2.0）** 交付切片（Hook → 多 Agent → MCP → 成本 → 发布）。**已完成。**  
> - **本文件 Phase 10–18** = Product-Spec §9 **Phase 3（自主与生态 / v0.3→v1.0）** 交付切片（见下）。**Phase 10–13 已完成；下一刀 Phase 14（Slash 命令框架）。**  
> - **Product-Spec §9 Phase 1–3** = **产品里程碑**（Phase 1≈v0.1；Phase 2=Harness；Phase 3=自主与生态）。  
> 勿将本文件「Phase 2 对话」与 Spec §9 Phase 2（Harness）混称。

---

## Phase 1: 项目骨架 + Web 工作台 + Workspace MVP

**Difficulty**: 🟡 中
**Nature**: Backend + UI
**Status**: ✅ 完成（FileTree、switch API、FTS5 记忆检索、加密/异常/REST 删除收口）

**Deliverables**:
- Spring Boot 4.0 + Java 21（虚线程）后端项目初始化（Maven，pom.xml）
- SQLite 数据库（WAL 模式），全部数据表：workspace、session、memory、task（含 outcome）、file_version；`memory_fts`（FTS5）
- Spring AI 2.0.0 GA 集成 + LLM Provider 凭证管理（§7.4：API Key 本地加密存储）
- Agent 工具：自研细粒度 `*Tool`（`@Tool` 适配层）
- 统一错误响应 `{code, message, details}`（中文友好）+ SSE error event 骨架
- Vite + React 18 + TypeScript 5 + Tailwind CSS 4 前端项目初始化
- 多面板布局（标题栏 + 左侧 sidebar + 主内容区 + 右侧面板）
- 主题切换（深色/浅色，跟随系统）
- Workspace CRUD 全栈（REST API + UI：创建、列表、`POST /switch` 切换、删除）
- 文件浏览器：可展开 `FileTree` + 文件列表；REST 删除沙箱免批；二进制 `/read` 明确错误
- 开发模式 Vite 代理到后端；生产构建输出到 `resources/static/`
- 状态管理：React `useState`（hooks 局部）+ props（EventLoop 计划已退役，见 ADR-003）

**Key Files**:
- `backend/src/main/java/com/tepeu/TepeuApplication.java` — 应用入口
- `backend/src/main/java/com/tepeu/config/` — DatabaseConfig（含 FTS）、GlobalExceptionHandler
- `backend/src/main/java/com/tepeu/model/` — Entity（Workspace, Session, Memory, Task, FileVersion）
- `backend/src/main/java/com/tepeu/repository/MemoryRepository.java` — FTS5 + LIKE 回退
- `backend/src/main/java/com/tepeu/service/CryptoService.java` / WorkspaceService / MemoryService
- `backend/src/main/java/com/tepeu/controller/` — workspace、memory（含 GET 列表）、files
- `frontend/src/components/common/FileTree.tsx` — 可展开目录树
- `frontend/src/components/layout/` — IdeShell, SessionSidebar
- `frontend/src/components/views/WorkspaceView.tsx` / `FileBrowserView.tsx`
- `frontend/src/hooks/useWorkspace.ts` — `switchWorkspace` 接后端
- `frontend/src/api/client.ts` / `styles/index.css`

**Acceptance Criteria**:
- ✅ `mvn spring-boot:run` 启动成功，SQLite 自动建表，后端 API 可用
- ✅ 浏览器多面板布局；深色/浅色主题
- ✅ 创建 Workspace → 列表 → `switch` 切换 → 删除
- ✅ FileTree + 文件列表；记忆 GET/POST search（FTS，失败 LIKE）
- ✅ LLM API Key 加密往返；统一错误 400/404；`mvn test` + `npm run typecheck`

**Primary metric**:
- `cd frontend && npm run build && cd ../backend && mvn -q test` exit 0 + 浏览器可见多面板布局

**Behavior**: 🟡 中（标准 dev-builder + code-review）

---

## Phase 2: Agent 对话（SSE 流式）

**Difficulty**: 🔴 高
**Nature**: Backend + UI
**Status**: ✅ 完成（工具过程可刷新回放、delete_file、超时提示、Ollama/草稿测连、中文错误、幂等键）

**Deliverables**:
- SSE 流式聊天 API（`POST /api/chat/stream`）：Agent Orchestrator + 工具循环
- 多模型：OpenAI、Anthropic、Ollama、DeepSeek（Spring AI 2.0）
- 细粒度 `*Tool`（含 `delete_file`）+ ToolEventEmittingCallback + toolKind
- 工具过程：ProcessDetails 折叠；工具事件持久化为 `TEPEU_TOOL_V1` system 行，刷新可回放
- Chat UI：流式 Token、工具卡片；原生 model thinking 仍属后续
- Provider 配置：Key/模型/连接测试（支持草稿凭证；Ollama 可无密钥）
- Session：侧栏列表 + 新建/切换；消息持久化
- 首次 LLM Key 引导（SetupWizard）

**Key Files**:
- `backend/.../ChatController.java` / `AgentOrchestrator.java` / `agent/tool/*`
- `backend/.../service/chat/ChatService.java` / `SessionService.java` / `ToolTraceCodec.java`
- `frontend/src/components/views/ChatView.tsx` / `ProviderSettingsView.tsx`
- `frontend/src/components/layout/SessionSidebar.tsx`
- `frontend/src/hooks/useChat.ts` / `components/chat/ProcessDetails.tsx`

**Acceptance Criteria**:
- ✅ Provider 页保存 Key → 连接测试（可用表单草稿）
- ✅ 新建对话 → SSE 流式：工具过程 + 最终回答（ProcessDetails；非原生 thinking）
- ✅ write_file 结果含内容预览；刷新后工具卡片可恢复
- ✅ 切换 OpenAI/Anthropic/Ollama 可用；Ollama 无密钥可保存
- ✅ session/workspace 不一致时拒绝；超时/中断有中文提示并可落部分回复
- ✅ `delete_file` 工具（需审批）满足删除验收
- ✅ 前端发送 `idempotencyKey`；`mvn test` + `npm run typecheck`

**Primary metric**:
- 单测/类型检查绿 + 端到端 SSE 对话可用

**Behavior**: 🔴 高（SSE 流式 + 工具循环较复杂，每段代码后自我评审，动刀甚微）

---

## Phase 3: 记忆 UI + 文件操作增强 + 终端

**Difficulty**: 🟡 中
**Nature**: Backend + UI
**Status**: ✅ 完成（workspaceId 贯通、图片 raw、版本 DIFF、侧栏文件入口、AI 错误解释；Windows cmd）

**Deliverables**:
- 记忆管理面板：搜索（FTS5，失败回退 LIKE）、浏览、编辑、删除、来源追溯（memory.source）
- 记忆标签过滤（tags → tag filter，按 workspace 过滤）
- 文件预览：语法高亮（含 yaml）、Markdown、图片（`/api/files/raw`）、PPTX
- 文件版本历史：列表、回滚、与当前文件 DIFF（`FileDiff` + `VersionPanel`）
- 拖拽上传：`FileBrowserView` → `/api/files/upload`（带 workspaceId）
- WebSocket 终端 UI：xterm.js（`/api/terminal/ws`）
- AI 辅助命令行：NL → Windows 命令词典；常见错误白话解释

**Key Files**:
- `frontend/src/components/views/MemoryView.tsx` / `hooks/useMemory.ts`
- `frontend/src/components/views/FileBrowserView.tsx` / `hooks/useFileBrowser.ts`
- `frontend/src/components/files/` — `RightFilePanel`、`VersionPanel`、`FileDiff`、`PptxPreview`
- `frontend/src/lib/codeHighlight.ts`
- `backend/.../FileController.java` / `FileVersionService.java`
- `frontend/src/components/views/TerminalView.tsx` / `hooks/useTerminal.ts`

**Acceptance Criteria**:
- ✅ 记忆搜索/标签/来源/编辑删除
- ✅ 拖拽上传成功并刷新；多工作区传 workspaceId
- ✅ 文本高亮 / Markdown / 图片（raw URL）
- ✅ 版本列表 + 回滚 + DIFF
- ✅ 终端 Windows：`dir` / `type` / `cd`；「显示当前目录」→ `dir`；错误有解释提示
- ✅ 侧栏「文件」入口；IDE 右栏版本 + 代码高亮
- ✅ `npm run typecheck` exit 0

**Primary metric**:
- typecheck 绿 + 上述验收

**Behavior**: 🟡 中（Terminal WS + AI CLI 需关注边界条件）

---

## Phase 4: 集成与发布（v0.1.0）

**Difficulty**: 🟢 低
**Nature**: Integration
**Status**: ✅ 引导/发布说明已完成；⏳ Docker 持久化路径与 compose 实测挂账（本机无 CLI / 用户暂缓）

**Deliverables**:
- 首次启动引导流程：LLM Key 配置 → 默认 workspace 创建 → 欢迎消息
- Dockerfile（多阶段构建：frontend build → Spring Boot JAR）定义就绪
- `.dockerignore`
- `docker-compose.yml`（单服务 + 数据卷定义；**卷路径与应用 `workspaces/`、`tepeu.db` 对齐待修**）
- 全面自检：已知限制 + 路线图（Harness 进展见 `RELEASE_NOTES-v0.2.0.md`）
- v0.1.0 发布说明（`RELEASE_NOTES-v0.1.0.md`）；正式 Git tag / GitHub Release 可选

**Key Files**:
- `frontend/src/components/views/SetupWizard.tsx` — 首次启动引导
- `Dockerfile` — 多阶段构建
- `docker-compose.yml` — 单服务编排
- `RELEASE_NOTES-v0.1.0.md` — 发布说明

**Acceptance Criteria**:
- ✅ 本地首次启动 → 引导界面 → 配置 API Key（Ollama 可无密钥）→ 进入主界面
- ⏳ `docker build` / `compose up` / 数据持久化：定义文件存在，实测与卷路径修复暂缓
- ✅ 正式 GitHub Release 可选（见 RELEASE_NOTES）

**Primary metric**:
- 本地引导可用；Docker 全链路验收挂账至有 CLI 时

**Behavior**: 🟢 低（标准流程）

---

## Phase 5: Hook 安全网（Spec M2.3）

**Difficulty**: 🔴 高
**Nature**: Backend + UI
**Status**: ✅ 完成（Agent 工具 + REST/终端宿主通道；幻觉门禁；本机实例令牌鉴权）

**Deliverables**:
- `ToolHook` + `DangerousToolHook`（`run_command` / `mcp_*` / 未知工具 → NEED_APPROVAL；灾难 shell → DENY；`write_file` 免批）
- `HookingToolCallback` 装饰链（可视化装饰器内侧）；可选阻塞等待批准（`tepeu.hook.approval-wait-seconds`）
- `ApprovalStore`：pending + **工具+参数** 会话授权；超时 → EXPIRED
- `HostChannelGuard`：REST 写/删/上传/恢复 + 终端 WS 走同一 Hook
- `HallucinationGuard`：写父目录须存在；回合结束扫描「已写入」声称路径
- 本机实例令牌（`X-Tepeu-Token`）：保护审批与危险宿主 API；localhost 可拉取
- SSE：`tool_approval_required` / `tool_denied` / `hallucination_warning`；前端审批条 Approve/Deny
- `POST /api/chat/approvals/{id}/decide`

**Key Files**:
- `backend/src/main/java/com/tepeu/agent/hook/*`
- `backend/src/main/java/com/tepeu/security/*`
- `backend/src/main/java/com/tepeu/controller/ApprovalController.java`
- `frontend/src/hooks/useChat.ts` / `useTerminal.ts` / `ApprovalBanner.tsx`

**Acceptance Criteria**:
- ✅ `run_command` / `mcp_*` / 未登记工具需 UI 审批；`write_file` 与只读工具免批
- ✅ Approve 后同会话**相同参数**可再执行；Deny/超时失败可见并清审批条
- ✅ 灾难性 shell 命令直接 DENY；终端安全命令免批，其余需批
- ✅ REST 删除沙箱免批（`file_rest`）；Agent `delete_file` 需批；写文件经幻觉父目录检查
- ✅ 审批/危险文件 API 要求实例令牌
- ✅ `mvn -q test` + `npm run typecheck` exit 0

**Primary metric**:
- 单测规则矩阵绿 + 上述验收

**Behavior**: 🔴 高

---

## Phase 6: 多 Agent 协作（Spec M2.1）

**Difficulty**: 🔴 高
**Nature**: Backend + UI
**Status**: ✅ 完成（审批面板、费用入账、VERDICT 严格解析、Reviewer 可读工具、提示可覆盖、会话打开、流水线锁）

**Deliverables**:
- Planner / Implementer / Reviewer（提示可 `tepeu.multi-agent.prompts.*` 覆盖）与共享执行面
- 失败可见传播（非闲聊式多 Agent）
- Goal/run 薄契约（验收标准挂任务，不只靠 Prompt）
- 多 Agent 面板工具审批（Hook 可阻塞等待批准）

**Key Files**:
- `backend/src/main/java/com/tepeu/agent/multi/`（角色编排）
- `frontend/src/components/views/MultiAgentView.tsx`
- `frontend/src/hooks/useMultiAgent.ts`

**Acceptance Criteria**:
- ✅ 可发起多角色一轮协作任务；失败步骤对用户可见
- ✅ 高危工具可在多 Agent 页批准；费用入账；会话可打开
- ✅ `mvn -q test` + `npm run typecheck` exit 0

**Primary metric**: 端到端多角色任务演示可用

**Behavior**: 🔴 高

---

## Phase 7: MCP 协议（Spec M2.2）

**Difficulty**: 🔴 高
**Nature**: Backend
**Status**: ✅ 完成（工具桥接+资源列表/读取、命名空间、缓存、状态 UI、自主会话 MCP 仍审批；真实 stdio server 联调需本机自行启用）

**Deliverables**:
- MCP 客户端接入（工具 + 资源列表/读取）
- 与 ToolRegistry / Hook 同 runtime、同权限边界
- `GET /api/mcp/status` + 服务商页 MCP 区块

**Key Files**:
- `backend/src/main/java/com/tepeu/agent/mcp/`
- `backend/src/main/resources/mcp-servers.example.yml`
- `frontend/src/components/views/ProviderSettingsView.tsx`

**Acceptance Criteria**:
- ✅ 可配置至少一个 MCP server 并被 Agent 调用；`mcp_*` 走 Hook（自主调度也不免批）
- ✅ 启用但无连接时状态 API 给出明确 warning/note
- ✅ `mvn -q test` + `npm run typecheck` exit 0

**Primary metric**: MCP 工具调用 + Hook 拦截联调通过

**Behavior**: 🔴 高

---

## Phase 8: 成本仪表盘（Spec M2.4）

**Difficulty**: 🟡 中
**Nature**: Backend + UI
**Status**: ✅ 完成（顶栏告警、硬门禁中文文案、零预算语义、未知模型回退估价、单测/E2E）

**Deliverables**:
- 复用 session/workspace stats；预算阈值配置
- 超预算告警 + 可选门禁（阻断新对话回合）
- 顶栏软告警/硬门禁徽章（不限于成本面板）

**Key Files**:
- `backend/src/main/java/com/tepeu/service/BudgetService.java`
- `backend/src/main/java/com/tepeu/service/TokenCostEstimator.java`
- `frontend/src/components/views/CostDashboardView.tsx`
- `frontend/src/components/layout/IdeShell.tsx`

**Acceptance Criteria**:
- ✅ 工作区可见累计与预算条；超阈值有告警（含顶栏）
- ✅ 硬门禁阻断聊天/多 Agent，中文错误提示
- ✅ `mvn -q test` + `npm run typecheck` exit 0

**Primary metric**: 仪表盘展示 + 告警路径可用

**Behavior**: 🟡 中

---

## Phase 9: v0.2.0 发布（Spec M2.5）

**Difficulty**: 🟢 低
**Nature**: Integration
**Status**: ✅ 文档/版本收口完成；⏳ 本机 `docker build` 待有 Docker CLI 时补验（可选 tag 未打）

**Deliverables**:
- `RELEASE_NOTES-v0.2.0.md`
- Docker 镜像构建验证
- 正式 Git tag / GitHub Release（可选）
- 根目录 `README.md`（运行/部署入口）

**Key Files**:
- `RELEASE_NOTES-v0.2.0.md`、`README.md`
- `Dockerfile` / `docker-compose.yml` / `.dockerignore`

**Acceptance Criteria**:
- ✅ 发布说明列出 Harness 能力与已知限制；版本号 0.2.0
- ⏳ `docker build` 成功（环境无 Docker CLI 时挂账，定义文件已就绪）
- ⏳ git tag / GitHub Release（可选，未执行）

**Primary metric**: 发布说明 + 镜像可构建

**Behavior**: 🟢 低

---

## Spec Phase 3 排期原则（ADR-010）

原始 Spec M3 顺序：**M3.1 自主 Agent → M3.4 多端 → M3.3 应用市场 → M3.2 WASM → M3.5 v1.0**  
理由：先交付「定时/后台跑任务」可见价值；响应式成本低；市场与 WASM 依赖技能/隔离模型成熟后再上。

**2026-07-20 新增 Phase 11–14（基础设施层）** 插入在自主 Agent 之后、多端适配之前：
- Phase 11 工具分类细化（权限/成本/UI 粒度）
- Phase 12 文件变更通知（fs-notify，修 FileBrowserView 不自动刷新）
- Phase 13 后台任务通知（Phase 10 自主调度补齐体验）
- Phase 14 Slash 命令框架（不调 LLM 的快速系统入口）

理由：这些是低投入高 ROI 的 cross-cutting 改进，趁 Phase 10 刚交付、代码还热的时候做，比拖到 v1.0 之后划算。

---

## Phase 10: 自主 Agent（Spec M3.1）

**Difficulty**: 🟡 中
**Nature**: Backend + UI
**Status**: ✅ 已完成（2026-08-02 收口：RUNNING 恢复、自主免批范围、费用入账）

**Deliverables**:
- 定时任务表 `agent_schedule`（间隔分钟、workspace、prompt、启用开关、上次运行/会话）
- 调度器（Spring `@Scheduled` ticker）触发既有 `AgentOrchestrator`
- REST：CRUD 日程 + 手动「立即运行」+ 结果落在 session
- 自主会话：`shell` 免批；`delete_file` / MCP 仍须批（无人值守则失败）
- RUNNING 卡死恢复、Token/费用入账、EMPTY、预算拦截
- UI：侧栏「自主」面板 — 列表/新建/编辑/启停/打开会话/最近结果与错误

**Key Files**:
- `backend/.../model/AgentSchedule.java`、`.../service/ScheduleService.java`、`.../controller/ScheduleController.java`
- `backend/.../config/DatabaseConfig.java`（建表）
- `frontend/src/components/views/ScheduleView.tsx`、`App.tsx`、`SessionSidebar.tsx`

**Acceptance Criteria**:
- ✅ 可创建「每 N 分钟」任务并在到期时自动开会话跑一轮
- ✅ 启停与手动触发可用；失败在 UI 可见（不静默）
- ✅ RUNNING 卡死可恢复；费用入账；预算超限中文失败
- ✅ 自主会话：shell 免批；delete_file / MCP 仍须批
- ✅ `mvn test` + `npm run typecheck` 通过

**Primary metric**: 至少一种周期触发路径端到端可用
**Behavior**: 🟡 中

---

## Phase 11: 工具分类细化（cross-cutting）

**Difficulty**: 🟢 低
**Nature**: Backend
**Status**: ✅ 已完成（2026-08-02）

**背景**：原粗颗粒 `FileTools` / `ShellTools` 已拆细；权限/成本/UI 按 `toolKind` 控制。

**Deliverables**:
- 独立工具：`ReadFileTool`, `WriteFileTool`, `ListDirTool`, `SearchFileTool`, `DeleteFileTool`
- `RunCommandTool`, `ReadOutputTool`（经 `CommandOutputStore` 续读）
- 各工具独立注册；SSE 携带 `toolKind`；`ToolKinds` 映射 `file_delete` / `file_rest`

**Key Files**:
- `backend/src/main/java/com/tepeu/agent/tool/*Tool.java`、`ToolKinds.java`、`CommandOutputStore.java`
- `backend/src/main/java/com/tepeu/agent/Tools.java`、`hook/DangerousToolHook.java`
- `frontend/src/hooks/useChat.ts`、`components/chat/MessageView.tsx`

**Acceptance Criteria**:
- ✅ 既有 `mvn test` 不破
- ✅ 同一 `ToolEventEmitter` 可按工具类型 emit 不同 `toolKind`
- ✅ Hook 按 `toolKind`：`shell`/`mcp`/`file_delete` 需批；`shell_output`/`file_list|read|write|search`/`file_rest` 免批

**Primary metric**: 所有 tool 调用路径端到端正常
**Behavior**: 🟢 低

---

## Phase 12: 文件变更通知（fs-notify）

**Difficulty**: 🟢 低
**Nature**: Backend + Frontend
**Status**: ✅ 已完成（2026-08-04：FileWatcherService 递归监听 + `GET /api/events` 常驻 SSE + 前端事件源自动刷新；mvn 242 测试全绿 + tsc + gstack E2E）

**背景**：`FileBrowserView` 不自动刷新（project-memory 已记录），外部修改文件后用户须手动操作才能看到变化。

**Deliverables**:
- `FileWatcherService`（JDK `WatchService`）监听 workspace 目录变更（递归注册子目录；忽略 `.git/node_modules/target/dist/.claude/.forge` 等）
- 收到 `ENTRY_CREATE/MODIFY/DELETE` 后经 `GET /api/events` 常驻 SSE 推 `file_changed`（带 workspaceId）
- 前端 `WorkspaceEventsProvider` 打开 `EventSource('/api/events')` 喂事件总线；`useFileBrowser` 订阅按当前工作区过滤防抖刷新；`FileBrowserView` 订阅同时刷新目录树
- workspace 创建/删除时经 `WorkspaceService` 动态注册/注销监听

**Key Files**:
- `backend/.../service/FileWatcherService.java` / `controller/FileEventsController.java`
- `backend/.../service/WorkspaceService.java`（create/delete 接线）
- `frontend/src/context/WorkspaceEvents.tsx`（事件源 + workspaceId 签名）
- `frontend/src/hooks/useFileBrowser.ts`（订阅防抖重载）
- `frontend/src/components/views/FileBrowserView.tsx`（树刷新）

**Acceptance Criteria**:
- ✅ 在工作区目录创建/修改/删除文件后前端 5 秒内自动反映（gstack E2E：REST 写文件后 `watcher-e2e-*.txt` 无手动操作自动出现在列表）
- ✅ 事件带 workspaceId、前端按当前工作区过滤，不泄漏到其他工作区（监听全部 + 前端过滤，见 ADR-012）
- ✅ 桌面布局回归不破（mvn test + typecheck 全绿）

**Primary metric**: 外部文件变更自动出现在文件列表中
**Behavior**: 🟢 低

---

## Phase 13: 后台任务通知

**Difficulty**: 🟢 低
**Nature**: Backend + UI
**Status**: ✅ 已完成（2026-08-05：TaskEventNotifier + `GET /api/task-events` 常驻 SSE；前端 useNotifications + NotificationBell 徽章/下拉；ScheduleView 状态标记与时间戳；mvn 246 全绿 + typecheck + gstack E2E 双路径）

**背景**：Phase 10 自主调度可周期性运行任务，但完成后用户无从知晓——须主动打开 Schedule 面板查看。

**Deliverables**:
- `ScheduleService` 任务完成后经 SSE 推 `task_completed` / `task_failed` 事件
- 前端监听事件：通知栏 badge + 浏览器 Notification API（可选）
- `ScheduleView` 新增完成/失败状态标记与时间戳

**Key Files**:
- `backend/.../service/TaskEventNotifier.java`（新建，SSE hub）、`controller/TaskEventController.java`（新建，`GET /api/task-events`）
- `backend/.../service/ScheduleService.java`（事件推送）
- `frontend/src/hooks/useNotifications.ts`（新建，store + EventSource + 浏览器通知）
- `frontend/src/components/layout/NotificationBell.tsx`（新建，徽章 + 下拉）
- `frontend/src/components/views/ScheduleView.tsx`（状态标记）

**Acceptance Criteria**:
- ✅ 自主任务完成后前端可见通知提示（gstack E2E：task_completed → 徽章 + 下拉「完成」）
- ✅ 失败通知区别于成功通知（task_failed → 下拉「失败」+ 原因）
- ✅ 既有 `mvn test`（246 绿）+ `npm run typecheck` 不破

**Primary metric**: 任务完成后 5 秒内前端可见通知（E2E 实测徽章出现）
**Behavior**: 🟢 低

---

## Phase 14: Slash 命令框架

**Difficulty**: 🟡 中
**Nature**: Backend + UI
**Status**: ✅ 已完成（2026-08-05：Registry + 5 内置命令 + `/api/slash`；ChatInput 候选；ChatView 拦截发送不经 LLM）

**背景**：当前聊天输入框纯文本→LLM，无法直接调用系统功能。Slash 命令为用户提供不经过 LLM 的内置操作入口。

**Deliverables**:
- 后端 `SlashCommandRegistry` + `SlashCommand` 接口
- 首批内置命令：`/help`、`/tasks`、`/schedule`、`/compact`、`/status`
- 前端输入框检测 `/` 弹出候选列表 + 参数提示
- 命令分两类：纯前端（`/clear` `/new` `/files`）和后端委派（`/help` `/schedule list` 等）

**Key Files**:
- `backend/.../agent/slash/SlashCommandRegistry.java` + `SlashCommand.java`
- `backend/.../agent/slash/commands/`（各命令实现）
- `backend/.../controller/SlashController.java`
- `frontend/src/components/chat/ChatInput.tsx`（/ 检测 + 候选浮层）
- `frontend/src/hooks/useSlashCommands.ts`（新建）

**Acceptance Criteria**:
- ✅ `/help` 返回内置命令清单，不调 LLM
- ✅ `/schedule list` 返回当前工作区日程，不调 LLM
- ✅ 输入 `/` 弹出候选列表（系统命令 + UI + 技能）
- ✅ 既有 `mvn test` + `npm run typecheck` 不破

**Primary metric**: 至少 3 个命令端到端可用（不消耗 LLM token）
**Behavior**: 🟡 中

---

## Phase 15: 多端适配（Spec M3.4）

**Difficulty**: 🟢 低  
**Nature**: UI  
**Status**: ✅ 完成（2026-08-05）

**Deliverables**:
- 窄屏布局：侧栏可折叠为底栏/抽屉；对话主区优先
- 触控目标 ≥ 44px；断点与 `IdeShell` 对齐
- Playwright 增补 mobile viewport 冒烟（chromium）

**Key Files**:
- `frontend/src/components/layout/IdeShell.tsx`、`SessionSidebar.tsx`、`src/styles/index.css`
- `frontend/e2e/mobile-shell.spec.ts`

**Acceptance Criteria**:
- 375×667 下可完成：开工作区入口、发一条消息、打开文件预览
- 桌面布局回归不破（既有 e2e）

**Primary metric**: 移动冒烟绿  
**Behavior**: 🟢 低

---

## Phase 16: 应用市场（Spec M3.3）

**Difficulty**: 🟡 中  
**Nature**: Backend + UI  
**Status**: ⏳ 待确认

**Deliverables**:
- 技能目录源：本地索引 + 可选远程清单 URL（默认 ReqForge / 配置项）
- 「市场」面板：浏览/搜索/一键安装到当前 workspace（复用现有 Skill API）
- 安装来源与版本记入 skill 元数据；失败可见

**Key Files**:
- `backend/.../service/SkillMarketplaceService.java`、`.../controller/MarketplaceController.java`
- `frontend/src/components/views/MarketplaceView.tsx`

**Acceptance Criteria**:
- 不配远程 URL 时仍可用内置/本地目录安装
- 安装后 `/技能名` 可调用

**Primary metric**: 从市场安装 ≥1 个技能成功  
**Behavior**: 🟡 中

---

## Phase 17: WASM+V8 运行时（Spec M3.2）

**Difficulty**: 🔴 高  
**Nature**: Backend  
**Status**: ⏳ 待确认

**Deliverables**:
- 选型落地（优先评估 GraalJS / wasmtime-java 与现有 JDK21 兼容性）
- 最小「技能脚本」沙箱：限时、无任意主机 FS（仅当前 workspace 显式 API）
- 与 Tool 注册桥接一条 demo 技能

**Key Files**:
- `backend/.../runtime/*`、`pom.xml` 依赖锁定精确版本
- ADR：运行时选型与威胁模型

**Acceptance Criteria**:
- Demo 脚本可读写 workspace 内约定路径，不能读 `user.home` 外路径
- 超时强制中断

**Primary metric**: 隔离边界测试绿  
**Behavior**: 🔴 高（须单独确认依赖与威胁模型）

---

## Phase 18: v1.0.0 发布（Spec M3.5）

**Difficulty**: 🟢 低  
**Nature**: Integration  
**Status**: ⏳ 待确认

**Deliverables**:
- `RELEASE_NOTES-v1.0.0.md`
- 版本号 1.0.0；Docker 定义校验（有守护进程则实测）
- 对照 Spec §10 成功指标做基线记录（能测则测，不能测则标明）

**Acceptance Criteria**:
- 发布说明覆盖 Phase 10–17 能力与已知限制
- tag / GitHub Release 仍可选（须你批准）

**Primary metric**: 发布说明 + 可构建  
**Behavior**: 🟢 低

---

## DEV-PLAN Phase 1 code-review carry-over（2026-07-11）— 状态更新 2026-07-18

> 下列项在 07-11 审查时推迟；**均已在后续交付切片落地**，不再作为待办：
> - ✅ **M4 Workspace 文件隔离**（DEV-PLAN Phase 3）：`workspace.root_path` + 按工作区解析 basePath。
> - ✅ **M3 Memory tags 过滤**（DEV-PLAN Phase 3）：记忆面板 + API tags 过滤。
> - ✅ **C2 Terminal WebSocket**（DEV-PLAN Phase 3）：origin 锁 localhost + 安全 handler + GBK 等。
> - ⏳ **m3 `ApiResponse @JsonInclude`**：仍可用 Jackson 2 注解（Boot 4 / Jackson 3 兼容实测生效）；非阻塞，有需要再迁 `tools.jackson.annotation`。

---

## Tech Stack

| Layer | Technology | Version | Notes |
|-------|-----------|---------|-------|
| 运行时 | Java | 21 LTS | 虚线程（Virtual Threads） |
| 后端框架 | Spring Boot | 4.0+ | 支持 Spring AI 2.0 |
| AI 集成 | Spring AI | 2.0.0 (GA) | 2026-06-12 发布，需 Boot 4.0 |
| Agent 工具 | 自研细粒度 `*Tool` | — | Spring AI `@Tool` + toolKind 事件装饰器 |
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
| `memory_fts` | Phase 1 | 记忆 FTS5 虚拟表 |
| `task` | Phase 1 | 任务记录，含 outcome |
| `file_version` | Phase 1 | 文件版本历史 |
| `agent_schedule` | Phase 10 | 自主 Agent 定时任务 |

## Development Rules

- **四步验证**：每个 Phase 完成后必须通过 Code Review → Test Completeness → Compile Verify → Functional Test
- **提交信息格式**：`phase-<N>: <description>`
- **包管理器**：Maven（后端）/ npm（前端）
- **开发模式**：`cd frontend && npm run dev`（Vite 代理 `localhost:30141` → 后端）
- **生产构建**：`cd frontend && npm run build` → 产物到 `src/main/resources/static/`
- **Agent 工具**：自研细粒度 `*Tool`；保持与 REST 文件 API 同语义
- **参数规范**：函数参数 > 4 个用 DTO/Record
- **状态管理**：React `useState`（hooks 局部）+ props（EventLoop 模式已退役，见 ADR-003；统一调度器推迟到确有需要时）
- **流式优先**：所有 Agent 输出 SSE 流式，前端逐 Token 渲染
- **第三方依赖标注**：非官方 Spring 组件（community/非GA）在版本列标注
