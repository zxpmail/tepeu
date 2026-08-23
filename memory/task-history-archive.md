# Task History Archive

> One row per session id. Written UTF-8 by compact-task-history.py.

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
| 2026-08-05 | phase13-notify | **DEV-PLAN Phase 13 后台任务通知** | TaskEventNotifier + GET `/api/task-events` 独立 SSE（ADR-013）；ScheduleView 完成/失败标记 |
| 2026-08-04 | phase12-fixup | **Phase 12 审查修复：合并+多 tab 共享** | FileWatcherService 250ms 去重；BroadcastChannel+leader 共享 SSE |
| 2026-08-04 | phase12-fsnotify | **DEV-PLAN Phase 12 文件变更通知** | 递归监听 + GET `/api/events` 常驻 SSE（ADR-012） |
| 2026-08-03 | p1-11-polish | **复查剩余项收口** | 中文错误扫尾、审批文案、ToolKindsTest；Docker 仍暂缓 |
| 2026-08-03 | p1-11-fixup | **Phase 1–11 审查项修改（无 Docker）** | 多 Agent 删工具绑定、终端 CWD、MCP 读资源；Docker 暂缓 |
| 2026-08-02 | phase1-fixup | **Phase 1 审查项全部修改** | FileTree、switchWorkspace、memory FTS5、REST 删除免批 |
| 2026-08-02 | phase2-fixup | **Phase 2 审查项全部修改** | 工具回放 TEPEU_TOOL_V1、delete_file、idempotencyKey |
| 2026-08-02 | phase3-fixup | **Phase 3 审查项全部修改** | workspaceId、图片 raw、版本 DIFF、AI 错误解释 |
| 2026-08-02 | phase4-nodocker | **Phase 4 非 Docker 收口** | SetupWizard v0.2.0 / Ollama / 欢迎文案；Docker 暂缓 |
| 2026-08-02 | phase5-hang | **Phase 5 三项挂账收口** | HostChannelGuard + HallucinationGuard + 实例令牌 |
| 2026-08-02 | phase8-fixup | **Phase 8 审查项全部修改** | 顶栏告警、零预算、回退估价、中文门禁 |
| 2026-08-02 | phase10-fixup | **Phase 10 review fixes** | autonomous auto-approve, cost, RUNNING recovery |
| 2026-08-02 | phase11-fixup | **Phase 11 审查项全部修改** | 会话级 store、真续读、Hook 按 kind |
| 2026-08-02 | phase11-tools | **DEV-PLAN Phase 11 工具分类细化** | search_files + read_output；CommandOutputStore；toolKind |
| 2026-07-18 | phase10-schedule | **DEV-PLAN Phase 10 自主 Agent** | agent_schedule + ScheduleView |
| 2026-07-18 | phase9-release | **DEV-PLAN Phase 9 / v0.2.0** | RELEASE_NOTES；无 Docker CLI 未实测镜像 |
| 2026-07-18 | phase8-cost | **DEV-PLAN Phase 8 成本仪表盘** | BudgetService + CostDashboardView |
| 2026-07-18 | phase7-mcp | **DEV-PLAN Phase 7 MCP** | McpToolBridge + Hook |
| 2026-07-18 | phase6-multi | **DEV-PLAN Phase 6 多 Agent** | MultiAgentOrchestrator + Goal |
| 2026-07-18 | phase5-hook | **DEV-PLAN Phase 5 Hook** | ToolHook + ApprovalStore + ApprovalBanner |
