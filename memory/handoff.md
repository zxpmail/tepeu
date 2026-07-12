# Handoff — Tepeu Agentic OS

> 跨会话/跨客户端交接。离开前更新，到达后**先读此文件**（不依赖聊天历史）。
> 到达后阅读序：本文件 → project-memory.md + decisions-log.md → DEV-PLAN.md 当前 Phase。

**Last updated**: 2026-07-11（IDE 三栏布局）

## 当前阶段

- ✅ **IDE 三栏**：左会话+文件树、中对话、右预览（可折叠）；次级面板从左下角进入。
- ✅ **对话细节**：助手消息用量脚注、过程详情折叠、输入底栏服务商选择。
- ✅ **聊天失败修复**：Spring AI sync+async client 凭证。
- ✅ **对话界面改版** / pi-web 四项加强 / v0.1.0 Phase 4。

## 验证状态（2026-07-11）

- 前端 `tsc` + `npm run build` 通过；静态资源已同步。
- 浏览器：三栏 DOM 正常（左 240 / 中 flex / 右默认 0）。

## 本会话关键文件

- `IdeShell.tsx`、`SessionSidebar.tsx`、`RightFilePanel.tsx`
- `ChatView.tsx`、`ChatInput.tsx`、`MessageView.tsx`、`ProcessDetails.tsx`、`groupMessages.ts`
- `App.tsx`、`index.css`、`useChat.ts`
- `docs/superpowers/specs/2026-07-11-ide-shell-design.md`

## Blocker / 风险
- 暂无。请 **强制刷新**（Ctrl+F5）查看新界面。

- 本地运行库 ackend/tepeu.db* 与工作区目录不入库；API key 仅存加密 DB，主密钥在 ~/.tepeu/master.key。
- 源码无硬编码真实 key；测试仅 sk-test / sk-xxx / sk-ant-test 占位。已推送 GitHub。
- **Spring AI 2.0 `ToolCallback...` API 均 @Deprecated**：`defaultToolCallbacks` + `.toolCallbacks(ToolCallback...)` 都标弃用。工具事件可视化装饰器（`ToolEventEmittingCallback`）暂不可避免走 deprecated API（carry-over，见 ADR-007）。
- M7 crypto passthrough（Should-fix，ADR-006 文档化）。

## 运行状态

- **后端正在后台运行**：`http://localhost:30141`，**PID 6060**（background task `b0mqxr7vz`）。
- **停止**：`mvn spring-boot:run` fork 的子 JVM 不随 TaskStop 退出 → `netstat -ano | grep 30141` 找 PID → `taskkill //F //PID <pid>`。
- SQLite：`backend/tepeu.db`（WAL，FK ON DELETE CASCADE）。主密钥：`<user.home>/.tepeu/master.key`（**需备份**）。本地 Maven repo：`D:\maven\repo`。
- 前端 dev：`cd frontend && npm run dev`（Vite 代理 :30141）。前端 prod 已 build 到 `backend/src/main/resources/static/`。

## 本会话（2026-07-11）变更文件

- 后端：`service/chat/ChatService.java`（+testConnection、+Logger、M1 streamWithTools 单次注册）、`service/LlmProviderService.java`（移除 testConnection 占位）、`controller/ProviderController.java`（注入 ChatService、testConnection 调 chatService）、新增 `src/test/java/com/tepeu/service/chat/ChatServiceTest.java`（5 测试）。
- 前端：`hooks/useChat.ts`（M3 appendToken + finally 不可变）、`components/views/ProviderSettingsView.tsx`（M2 Test Connection 按钮 + testing 状态 + footer 文案）。
- 文档：`memory/{task-history,decisions-log,project-memory,handoff}.md`（本会话记录 + ADR-007）。

## 关键 ADR

- ADR-000/001/002/004/005 不变；**ADR-003 已退役**（EventLoop→useState）；**ADR-006** API key 加密（SEE 偏差声明）；**ADR-007** testConnection 实现位置（ChatService，避循环依赖）+ streamWithTools 工具单次注册 + Spring AI 2.0 ToolCallback API deprecated carry-over。
- 事实：构建 = Maven（后端）/ npm（前端）；JSON = Jackson 3（Boot 4）；状态管理 = useState；FK = ON DELETE CASCADE + per-connection `foreign_keys=ON`；**无 git**。

## 推荐下一步

- ✅ **v0.1.0 全部 4 Phase 已完成。** Tepeu 首个可发布版本的开发告一段落。
- 接下来可以根据需要：
  - 运行 **/release-builder** 进行正式发布（Git tag + GitHub Release + Docker 镜像推送）
  - 运行 **/code-review** 做全量回顾
  - 运行 **/change-manager** 添加新的功能变更（brownfield）
  - 考虑 `git init` + 初始 commit 恢复 git 工作流
