# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
Phase 12（fs-notify 文件变更通知）已完成。Docker 仍暂缓。下一刀 Phase 13。

## 上次停在哪
- ✅ Phase 12：FileWatcherService（递归监听全部 workspace）+ `GET /api/events` 常驻 SSE + 前端事件源自动刷新；mvn 242 全绿 + tsc + gstack E2E
- ⏳ Docker 暂缓
- 下一刀：Phase 13（后台任务通知）

## 近期关键决定
- 文件监听：监听全部 workspace + 事件带 workspaceId + 前端按当前工作区过滤（ADR-012）
- REST 删除免批；Agent/自主 delete_file 仍要批；自主仅 shell 免批
- Docker 暂缓
