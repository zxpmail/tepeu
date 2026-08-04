# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
Phase 14（Slash 命令框架）已完成。Docker 仍暂缓。下一刀 Phase 15（多端适配）。

## 上次停在哪
- ✅ Phase 14：`/help` `/tasks` `/schedule` `/compact` `/status` + ChatInput 候选；发送拦截不经 LLM
- ⏳ Docker 暂缓
- 下一刀：Phase 15（多端适配）

## 近期关键决定
- Slash 后端注册表为权威目录；`/clear` `/new` `/files` 仍纯前端
- 后台任务通知走独立 `/api/task-events`（ADR-013）
- 文件监听：全部 workspace + 前端按工作区过滤（ADR-012）
- Docker 暂缓
