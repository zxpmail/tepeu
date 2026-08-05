# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
Phase 15（多端适配）已完成。Docker 仍暂缓。下一刀 Phase 16（应用市场）。

## 上次停在哪
- ✅ Phase 14：`/help` `/tasks` `/schedule` `/compact` `/status` + ChatInput 候选；发送拦截不经 LLM
- ✅ Phase 15：移动断点 ≤767px——左栏抽屉、右预览全屏 overlay、44px 触控、顶栏统计折叠；`mobile-shell.spec.ts` 375×667 冒烟绿
- ⏳ Docker 暂缓
- 下一刀：Phase 16（应用市场，Spec M3.3）

## 近期关键决定
- Slash 后端注册表为权威目录；`/clear` `/new` `/files` 仍纯前端
- 后台任务通知走独立 `/api/task-events`（ADR-013）
- 文件监听：全部 workspace + 前端按工作区过滤（ADR-012）
- 移动端：断点 767px 统一；左栏抽屉 + 右预览全屏 overlay + 44px 主路径触控（ADR-014）
- Docker 暂缓
