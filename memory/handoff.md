# Handoff — Tepeu Agentic OS

> 到达后阅读序：本文件 → `CONTEXT.md` → `decisions-log.md`（ADR-008/009/010）→ DEV-PLAN Phase 12+。

**Last updated**: 2026-08-04

## 当前阶段

- ✅ Spec §9 Phase 2 / v0.2.0 Harness（DEV-PLAN 5–9）
- ✅ Spec §9 Phase 3 计划已确认
- ✅ **DEV-PLAN Phase 5 Hook 审查项已收口**（含 REST/终端 HostChannelGuard、幻觉门禁、实例令牌鉴权）
- ✅ **DEV-PLAN Phase 6 多 Agent 审查项已收口**（审批等待、费用、VERDICT、Reviewer 工具、提示覆盖）
- ✅ **DEV-PLAN Phase 7 MCP 审查项已收口**（命名空间、资源 API、缓存、自主不免批 MCP、设置页状态）
- ✅ **DEV-PLAN Phase 8 成本仪表盘审查项已收口**（顶栏告警、零预算、回退估价、中文门禁）
- ✅ **DEV-PLAN Phase 10 自主 Agent 已完成**
- ✅ **DEV-PLAN Phase 11 工具分类细化已完成**
- ✅ **DEV-PLAN Phase 12 文件变更通知已完成**（fs-notify）
- ✅ Phase 1–12 审查项收口（**不含 Docker**）：DeleteFileTool 多 Agent 绑定、自主 delete 仍要批、终端工作区 CWD、文件删除 UI、Reviewer 只读白名单、MCP 资源读取、中文错误、DEV-PLAN 对齐
- ✅ Phase 4/9：**Docker 卷路径与实测暂缓**（按你要求先不做）
- ⏳ 下一刀：**Phase 13**（后台任务通知），或你指定阶段号

## Phase 12 交付要点（2026-08-04）

- `FileWatcherService`（JDK WatchService）：启动注册全部 workspace 根 + 递归子目录；忽略 `.git/node_modules/target/dist/.claude/.forge`；`WorkspaceService` create/delete 时动态注册/注销
- 新 `GET /api/events` 常驻 SSE（`SseEmitter(0L)`，GET 只读免令牌）；事件 `{type:"file_changed", path, workspaceId, operation}`
- 前端 `WorkspaceEventsProvider` 打开 `EventSource('/api/events')` 喂事件总线；`useFileBrowser` 订阅按当前工作区过滤 + 300ms 防抖重载；`FileBrowserView` 订阅同时刷目录树
- 设计决策（ADR-012）：监听全部 + 前端过滤（等价「只对当前工作区生效、不泄漏」），而非随切换启停
- 验证：`mvn test` 242 全绿 + `npm run typecheck` + gstack E2E（REST 写文件后自动出现在列表）

## Phase 11 交付要点

- `FileTools`/`ShellTools` → 独立 `*Tool`；新增 `search_files`、`read_output`
- `CommandOutputStore`：按会话隔离；采集最多 ~256KB，模型即时回传 ~32KB，`read_output` 真续读
- Hook 按 `toolKind`（`shell`/`mcp`）审批；审批/拒绝 SSE 带 `toolKind`；前端 ToolCard 显示类别
- 验证：`mvn test` + `npm run typecheck` 通过

## Phase 10 收口（2026-08-02）

- 卡死 RUNNING 自动恢复；runNow 先落库 RUNNING
- 自主会话仅 shell 免批（delete_file / MCP 仍要批）；TokenCostEstimator + task 入账
- 前端可编辑、可打开会话；provider 校验；空回复 EMPTY

## Phase 9 卫生（2026-08-02）

- 补根 `README.md`；`.dockerignore` 放行 README + 两份 RELEASE_NOTES
- RELEASE_NOTES / Spec / DEV-PLAN 诚实标注：docker build 待 CLI；tag 可选未打
- 清理 `task-history-archive` 重复行

## 工程挂账

- ToolCallback deprecated（ADR-007）；Crypto passthrough（ADR-006）
- 无 CI/CD；**本机无 Docker CLI → 镜像未实测**
- Spec M3.1「Hands 能力包」未做（超出定时调度切片）
- 自主完成推送仍属 Phase 13
- git tag `v0.2.0` 未打（可选）
