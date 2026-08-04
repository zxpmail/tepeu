# Handoff — Tepeu Agentic OS

> 到达后阅读序：本文件 → `CONTEXT.md` → `decisions-log.md`（ADR-008/009/010/012/013）→ DEV-PLAN Phase 14+。

**Last updated**: 2026-08-05

## 当前阶段

- ✅ Spec §9 Phase 2 / v0.2.0 Harness（DEV-PLAN 5–9）
- ✅ Spec §9 Phase 3 计划已确认
- ✅ **DEV-PLAN Phase 5–12 审查项已收口**（Hook/多 Agent/MCP/成本/自主/工具细分/文件变更通知）
- ✅ **DEV-PLAN Phase 13 后台任务通知已完成**（2026-08-05）
- ✅ Phase 1–13 审查项收口（**不含 Docker**）
- ✅ Phase 4/9：**Docker 卷路径与实测暂缓**（按你要求先不做）
- ⏳ 下一刀：**Phase 14**（Slash 命令框架），或你指定阶段号

## Phase 13 交付要点（2026-08-05）

- `TaskEventNotifier`（SSE hub，镜像 FileWatcherService 模式）+ 新 `GET /api/task-events` 常驻 SSE（SseEmitter(0L)，GET 只读免令牌）
- `ScheduleService` 终态发布：SUCCESS→`task_completed`；EMPTY/FAILED/预算阻断/卡死恢复→`task_failed`。payload `{type, scheduleId, scheduleName, workspaceId, sessionId?, message}`
- 前端 `useNotifications`：模块 store（useSyncExternalStore）+ 单例 EventSource + 可选浏览器 Notification（请求权限）；`NotificationBell` 顶栏徽章/下拉（点击带 sessionId 跳会话，否则跳自主面板）；挂载 IdeShell + App 次级 header
- `ScheduleView`：SUCCESS 绿色、FAILED/EMPTY 红色；终态显示「完成/失败：时间戳」（updatedAt）；订阅 `onTaskEvent` 按 workspaceId 过滤刷新
- 设计决策（ADR-013）：**独立 `/api/task-events` 通道**，不复用 `/api/events`——任务事件低频每 tab 直连，与高频文件事件（250ms 合并 + 跨 tab leader）职责分离
- 验证：`mvn test` **246** 全绿 + `npm run typecheck` + `npm run build` + gstack E2E（成功：task_completed→徽章→下拉「完成」；失败：task_failed→下拉「失败」+原因 PROVIDER_DISABLED；ScheduleView 状态标记/时间戳）。E2E 数据已清理，后端已停

## Phase 12 交付要点（2026-08-04）

- `FileWatcherService`（JDK WatchService）递归监听全部 workspace；`GET /api/events` 常驻 SSE；前端 `WorkspaceEventsProvider` + `useFileBrowser` 按工作区过滤防抖刷新
- 后补：250ms 合并去重 + 多 tab 共享一条 SSE（`sharedFileEvents.ts` BroadcastChannel+localStorage leader）

## Phase 11 交付要点

- `FileTools`/`ShellTools` → 独立 `*Tool`；`search_files`/`read_output`；`CommandOutputStore` 会话隔离
- Hook 按 `toolKind` 审批；SSE 带 `toolKind`

## 工程挂账

- ToolCallback deprecated（ADR-007）；Crypto passthrough（ADR-006）
- 无 CI/CD；**本机无 Docker CLI → 镜像未实测**
- Spec M3.1「Hands 能力包」未做（超出定时调度切片）
- git tag `v0.2.0` 未打（可选）
