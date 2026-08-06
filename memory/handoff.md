# Handoff — Tepeu Agentic OS

> 到达后阅读序：本文件 → `CONTEXT.md` → `RELEASE_NOTES-v1.0.0.md` → `decisions-log.md`（ADR-015）。

**Last updated**: 2026-08-07

## 当前阶段

- ✅ **DEV-PLAN Phase 1–18 / v1.0.0 发布收口已完成**（2026-08-07）
- ⏳ Docker `docker build` 暂缓（无 CLI）
- ⏳ git tag `v1.0.0` / GitHub Release（须批准）

## Phase 18 交付要点（2026-08-07）

- `RELEASE_NOTES-v1.0.0.md`；版本号 `1.0.0`（pom / package.json / SetupWizard / README）
- Docker：`Dockerfile` + `docker-compose.yml` 定义校验；本机无 CLI 未实测 build
- Spec §10 基线表写入发布说明（性能/社区指标多数标明未测）
- **未**打 git tag / GitHub Release

## Phase 17 交付要点（2026-08-07）

- ADR-015：GraalJS `js-community` 24.2.1；wasmtime 原生 WASM 延后
- `ScriptSandbox` / `WorkspaceScriptFs`；工具 `run_skill_script`（内置 demo + `/scripts/*.js`）
- 配置 `tepeu.runtime.script-timeout-ms`；超时 `Context.close(true)`
- 验证：`ScriptSandboxTest` + `RunSkillScriptToolTest` + 相关回归通过

## Phase 16 交付要点（2026-08-07）

- `SkillMarketplaceService`：内置 `marketplace/catalog.json` + classpath 示例 `hello-assistant`；扫描本机 ReqForge；可选 `tepeu.marketplace.manifest-url`
- API：`GET /api/marketplace/catalog`、`POST /api/marketplace/install`；skill 记 `install_source` / `install_version`
- 前端「市场」面板：浏览/搜索/一键安装；侧栏入口
- 验证：`SkillMarketplaceServiceTest` + `SkillServiceTest` 通过；前端 typecheck 通过

## Phase 15 交付要点（2026-08-05）

- 移动断点统一 `max-width: 767px`：`useMediaQuery` hook + CSS media query 混合实现
- 左栏 → 抽屉 overlay（84vw≤320px，遮罩点击收起）；右预览 → 全屏 overlay
- 顶栏 40→48px 容纳 ≥44px 触控按钮；token/费用统计移动端隐藏（预算徽章保留）
- 顺手修复：预览 ✕ 现在真正关闭面板（原只清 openFile 留空面板）
- 验证：`mobile-shell.spec.ts`（375×667 开工作区入口/发消息/开文件预览）+ 桌面回归 specs + 后端 262 单测全绿
- 设计：ADR-014

## Phase 14/15 复查修复（2026-08-05，三路对抗审查）

- **前端**：Slash 目录异步竞态——目录未加载时 `/help` 会误走 LLM → `useSlashCommands` 加 `ready`，ChatView `handleSend` 未就绪时 slash 形式一律走后端；并发双击防重（`slashBusy` guard）；`runSlashLine` 抽公共（去 handleSend/handleSystemSlashPick 重复）
- **前端**：补全候选 `slashIndex` 过滤变短后越界吞 Enter → clamp；候选 popup 移动端 44px（`completer-popup`）
- **前端**：**provider 未就绪时发送静默丢消息**（`useChat.send` 空 provider 早退）→ `ChatView` 用 `providerReadyRef` await 就绪；确无 provider 时本地回合提示
- **前端**（Phase 15）：`useLayoutEffect` 防 resize 一帧抽屉盖屏；移回桌面断点自动重开侧栏；关闭抽屉 box-shadow 清除；移动端 composer 右 padding 恢复对称；删死代码 `.ide-topbar{height:48px}`
- **后端**：`SlashController` 删与 GlobalExceptionHandler 重复的 try/catch；`/tasks` 补工作区存在性校验（与 /status 一致，防伪 id 全零摘要）；测试补 `tasks_unknownWorkspace_throws`
- **e2e**：`mobile-shell.spec` 文件改为 `openIde` 前写入（解耦文件树刷新时序）、断言 scope 到 `file-preview`、增补 375px Slash 候选检查
- 验证：typecheck 0 / e2e（mobile+chat+app-shell+files+workspace）全绿 / 后端 263 单测 BUILD SUCCESS

## Phase 14 复查修复续（2026-08-07）

- 手打 `/clear` `/new` `/files` 回车拦截，不再误送 LLM
- `/compact`：`SessionService.clearMessages` 清空服务器历史 + 本屏；文案对齐真实语义
- `/` 候选过滤与系统/UI 同名的技能；目录加载失败自动重试一次
- e2e：`/help` 出结果、手打 `/clear` 清空

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
- git tag `v0.2.0` / `v1.0.0` 未打（可选，须批准）
