# Task History

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
| 2026-08-07 | phase18-v1 | **DEV-PLAN Phase 18 / v1.0.0** | RELEASE_NOTES；版本 1.0.0；§10 基线；Docker 定义校验；tag 未打待批 |
| 2026-08-07 | phase17-runtime | **DEV-PLAN Phase 17 技能脚本沙箱** | ADR-015 GraalJS 24.2.1；run_skill_script；超时强制中断；wasmtime 延后 |
| 2026-08-07 | phase16-market | **DEV-PLAN Phase 16 应用市场** | 内置目录+本机扫描+可选远程；MarketplaceView；install_source/version；离线可装 hello-assistant |
| 2026-08-05 | phase14-slash | **DEV-PLAN Phase 14 Slash 命令框架** | Registry+5 命令；GET/POST /api/slash；ChatInput 候选+发送拦截不经 LLM；mvn/tsc 通过 |
| 2026-08-05 | phase13-notify | **DEV-PLAN Phase 13 后台任务通知** | TaskEventNotifier + GET /api/task-events 独立 SSE 通道（ADR-013）；ScheduleService 终态发布 task_completed/task_failed；前端 useNotifications store + NotificationBell 徽章/下拉/浏览器通知；ScheduleView 完成/失败标记+时间戳+事件驱动刷新；mvn 246 全绿 + typecheck + gstack E2E 双路径 |
| 2026-08-04 | phase12-fixup | **Phase 12 风险修复：合并+多tab共享** | FileWatcherService 250ms 合并去重（delete>create>modify）；sharedFileEvents.ts BroadcastChannel+localStorage leader 多 tab 共享 SSE；mvn 243 全绿 + 双 tab E2E |
| 2026-08-04 | phase12-fsnotify | **DEV-PLAN Phase 12 文件变更通知** | FileWatcherService 递归监听 + `GET /api/events` 常驻 SSE + 前端事件源自动刷新；监听全部+前端过滤（ADR-012）；mvn 242 全绿 + tsc + gstack E2E |
| 2026-08-03 | p1-11-polish | **复查剩余项收口** | 中文错误扫尾、审批/审查文案、ToolKindsTest、handoff自主免批口径；Docker仍暂缓；mvn+tsc |
| 2026-08-03 | p1-11-fixup | **Phase 1–11 审查项修改（无 Docker）** | 多Agent删工具绑定、自主delete仍批、终端CWD、删文件UI、Reviewer白名单、MCP读资源；Docker暂缓；mvn+tsc |
| 2026-08-02 | phase1-fixup | **Phase 1 审查项全部修改** | FileTree、switchWorkspace、memory FTS5、REST删除免批、中文异常、Crypto/Memory单测；mvn+tsc |
| 2026-08-02 | phase2-fixup | **Phase 2 审查项全部修改** | 工具回放 TEPEU_TOOL_V1、delete_file、超时落库、Ollama/草稿测连、中文错误、idempotencyKey；mvn+tsc |
| 2026-08-02 | phase3-fixup | **Phase 3 审查项全部修改** | workspaceId/图片raw/版本DIFF/侧栏文件/右栏高亮/AI错误解释；typecheck |
| 2026-08-02 | phase4-nodocker | **Phase 4 非 Docker 收口** | SetupWizard v0.2.0/Ollama/欢迎文案；DEV-PLAN+RELEASE 对齐；Docker 暂缓 |
| 2026-08-02 | phase5-hang | **Phase 5 三项挂账收口** | HostChannelGuard + HallucinationGuard + 实例令牌；前端 token/终端审批/幻觉警告；mvn+tsc |
| 2026-08-02 | phase8-fixup | **Phase 8 审查项全部修改** | 顶栏告警、零预算、回退估价、中文门禁；mvn+tsc |
| 2026-08-02 | phase10-fixup | **Phase 10 review fixes** | autonomous auto-approve, cost, RUNNING recovery; mvn+tsc |
| 2026-08-02 | phase11-fixup | **Phase 11 审查项全部修改** | 会话级 store、真续读、Hook 按 kind；mvn+tsc |
| 2026-08-02 | phase11-tools | **DEV-PLAN Phase 11 工具分类细化** | search_files + read_output；CommandOutputStore；toolKind |
| 2026-07-18 | phase10-schedule | **DEV-PLAN Phase 10 自主 Agent** | agent_schedule + ScheduleView |
| 2026-07-18 | phase9-release | **DEV-PLAN Phase 9 / v0.2.0** | RELEASE_NOTES；无 Docker CLI 未实测镜像 |
| 2026-07-18 | phase8-cost | **DEV-PLAN Phase 8 成本仪表盘** | BudgetService + CostDashboardView |
| 2026-07-18 | phase7-mcp | **DEV-PLAN Phase 7 MCP** | McpToolBridge + Hook |
| 2026-07-18 | phase6-multi | **DEV-PLAN Phase 6 多 Agent** | MultiAgentOrchestrator + Goal |
| 2026-07-18 | phase5-hook | **DEV-PLAN Phase 5 Hook** | ToolHook + ApprovalStore + ApprovalBanner |
