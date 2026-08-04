# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
Phase 13（后台任务通知）已完成。Docker 仍暂缓。下一刀 Phase 14（Slash 命令框架）。

## 上次停在哪
- ✅ Phase 13：TaskEventNotifier + `GET /api/task-events` 常驻 SSE；前端 useNotifications + NotificationBell（徽章/下拉/浏览器通知）；ScheduleView 完成/失败标记 + 时间戳；mvn 246 全绿 + typecheck + gstack E2E（成功/失败双路径）
- ⏳ Docker 暂缓
- 下一刀：Phase 14（Slash 命令框架）

## 近期关键决定
- 后台任务通知走**独立** `/api/task-events` 通道而非复用 `/api/events`（ADR-013）：任务事件低频、每 tab 直连，与高频文件事件（合并 + leader 选举）职责分离
- 文件监听：监听全部 workspace + 事件带 workspaceId + 前端按当前工作区过滤（ADR-012）
- REST 删除免批；Agent/自主 delete_file 仍要批；自主仅 shell 免批
- Docker 暂缓
