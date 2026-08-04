# Handoff — Tepeu Agentic OS

> 到达后阅读序：本文件 → `CONTEXT.md` → `decisions-log.md`（ADR-012/013）→ DEV-PLAN Phase 15+。

**Last updated**: 2026-08-05

## 当前阶段

- ✅ DEV-PLAN Phase 5–13（含复查修复）
- ✅ **DEV-PLAN Phase 14 Slash 命令框架已完成**（2026-08-05）
- ⏳ 下一刀：**Phase 15 多端适配**
- ⏳ Docker 暂缓

## Phase 14 交付要点（2026-08-05）

- `SlashCommandRegistry` + `/help` `/tasks` `/schedule` `/compact` `/status`
- `GET /api/slash/commands`、`POST /api/slash`（不调 LLM）
- 前端候选浮层 + 发送拦截；结果本地插入对话；`/compact` 清空本屏
- 验证：单测 + 全量 `mvn test` + `npm run typecheck`

## Phase 13 交付要点（2026-08-05；复查修复同日）

- `TaskEventNotifier`（SSE hub）+ `GET /api/task-events`；推送失败摘除死连接
- `ScheduleService` 终态发布；空回复/卡死恢复文案中文
- 前端通知铃 + `sessionNavBus` **pending 队列**（次级面板先投递再挂 IdeShell 不丢事件）；`App.openChatSession` 先切工作区再开会话
- `ScheduleView` 仅当前工作区刷新；徽章含失败未读用红、否则强调色
- 设计：ADR-013 独立通道

## Phase 12 交付要点（2026-08-04；复查修复 2026-08-05）

- `FileWatcherService`（JDK WatchService）递归监听全部 workspace；`GET /api/events` 常驻 SSE；前端 `WorkspaceEventsProvider` + `useFileBrowser` 按工作区过滤防抖刷新
- 后补：250ms 合并去重 + 多 tab 共享一条 SSE（`sharedFileEvents.ts` BroadcastChannel+localStorage leader）
- 复查修复：空 workspace 启动先建 WatchService；忽略目录按相对路径任一段过滤（防 `.git/objects` 误注册）；Sidebar / RightFilePanel / useChat 事件带 workspaceId 并过滤

## Phase 11 交付要点

- `FileTools`/`ShellTools` → 独立 `*Tool`；`search_files`/`read_output`；`CommandOutputStore` 会话隔离
- Hook 按 `toolKind` 审批；SSE 带 `toolKind`

## 工程挂账

- ToolCallback deprecated（ADR-007）；Crypto passthrough（ADR-006）
- 无 CI/CD；**本机无 Docker CLI → 镜像未实测**
- Spec M3.1「Hands 能力包」未做（超出定时调度切片）
- git tag `v0.2.0` 未打（可选）
