# Decisions Log

## ADR-000: Tech Stack Selection (2026-07-10)
- **Decision**: Spring Boot 4.0+ + Spring AI 2.0.0 GA + Java 21 + SQLite + React 18 + Vite 6
- **Rationale**: Spring AI 2.0 GA (Jun 2026) requires Spring Boot 4.0. Java 21 virtual threads for concurrency. SQLite for zero-config single-machine deployment.
- **Alternatives considered**: Spring Boot 3.4 + Spring AI 1.x (user explicitly chose Boot 4.0)

## ADR-001: Single JAR Deployment (2026-07-10)
- **Decision**: Frontend SPA is built to backend/src/main/resources/static/ and served from Spring Boot
- **Rationale**: Dev mode uses Vite proxy to backend for HMR; production single JAR simplifies distribution

## ADR-002: SQLite with WAL Mode (2026-07-10)
- **Decision**: SQLite with WAL mode for concurrent read performance
- **Rationale**: v1.0 is single-machine only. WAL allows >10 concurrent agents (mostly reads) without contention

## ADR-003: EventLoop State Management (2026-07-10) — RETIRED 2026-07-11
- **Decision (原)**: React 状态管理用自定义 EventLoop 模式（dispatch→scheduler→state）
- **Rationale (原)**: Netty 风格单线程事件循环，避免分散 setState
- **Retirement (2026-07-11, code-review C3)**: `store/eventLoop.ts` 从未被任何组件引用（零 importer），实际状态管理全用 `useState`。属"声称合规但实际不存在"的 drift。**已删除 eventLoop.ts**。Phase 1 状态管理正式 = useState（hooks 局部）+ props；统一调度器推迟到确有需要时再引入。Spec §5.4.6 / DEV-PLAN / project-memory 相应描述已更正。

## ADR-004: Database Layer (2026-07-10)
- **Decision**: Spring JdbcTemplate instead of JPA for SQLite
- **Rationale**: JPA + SQLite dialect support is finicky. JdbcTemplate provides direct control without ORM complexity

## ADR-005: No Authentication in Phase 1 (2026-07-10)
- **Decision**: Phase 1 local mode has no authentication
- **Rationale**: Single-machine deployment. Auth (OAuth2/OIDC) deferred to Phase 2 enterprise mode

## ADR-006: API Key At-Rest Encryption (2026-07-11)
- **Decision**: AES-256-GCM；主密钥首启自动生成（32 字节随机），base64 写入 `<user.home>/.tepeu/master.key`（POSIX owner-only 权限；Windows 继承 home 目录 ACL）。可经 `tepeu.security.master-key-file` 覆盖。
- **Rationale**: §7.4 要求加密存储。单机本地 + Phase1 无认证 → 零配置优先（自动密钥文件 > 环境变量 > OS DPAPI）。GCM 提供认证加密。DB 单独被拷走无法解密。
- **Spec 偏差声明 (code-review M1)**: Spec §7.3/§7.4 原文写「SQLite 加密扩展（SEE）」；本实现改用**应用层 AES-256-GCM**（仅加密 `llm_provider.api_key` 列，非整库）。理由：xerial `sqlite-jdbc` 不带 SEE 构建/授权；应用层 GCM 提供认证加密且 DB 单独被拷走不可解。**代价**：memory/workspace 行在磁盘仍为明文（Phase 1 单机可接受；Phase 2 若需整库加密再评估 SEE/SQLCipher）。
- **Stored format**: `"enc:v1:" + base64(iv(12B) ‖ ciphertext ‖ tag(16B))`。非 `enc:` 前缀值视为遗留明文（decrypt passthrough，重存即加密）。
- **GET 行为**: 永不回显明文/密文；脱敏 `first(3)+••••+last(4)`。服务层解密后返回明文给内部调用（Phase 2 agent 用真 key）。
- **实现**: `CryptoService`（加解密+主密钥）；`LlmProviderService` 写入加密/读出解密；`ProviderController` 脱敏回显。
- **Alternatives**: 环境变量（需运维管理）、Windows DPAPI（Java 调用复杂需 JNI/JNA）、passphrase 派生（与无认证冲突）。
- **Caveat**: 主密钥文件**需备份**，丢失则已存 API key 不可恢复。

## ADR-007: testConnection 实现位置 + streamWithTools 工具注册 (2026-07-11)
- **Decision (testConnection)**: `LlmProviderService.testConnection` 占位（恒 `return true`）移除，真实实现放 `ChatService.testConnection(providerId)`——build `ChatModel`（经 `ChatModelFactory`，复用校验+解密）+ `model.call(new Prompt(new UserMessage("ping")))` + 任何 `RuntimeException` → `false`。`ProviderController` 注入 `ChatService` 调用之。
- **Rationale**: `ChatModelFactory` 依赖 `LlmProviderService`（单向）；若 `testConnection` 留在 `LlmProviderService` 并注入 `ChatModelFactory` 会构成构造器循环依赖。`ChatService` 已持有 `ChatModelFactory` 且语义上是"发一次 chat call"的归属层，无循环。真实 round-trip（非仅校验配置）才能验"连接测试成功"（Phase 2 验收标准 1）——占位 `true` 使该标准形同虚设。
- **Cost**: Test 按钮触发一次真实（云厂商计费、极小）调用；Ollama 本地免费。可接受（手动触发）。
- **Decision (streamWithTools M1)**: 原 `ChatService.streamWithTools` 三重注册工具（`defaultToolCallbacks(wrapped)` + `.tools(fileTools)` + `.toolCallbacks(wrapped)`）→ 改为单次 per-request `.toolCallbacks(wrapped)`。`wrapped` 已装饰 `ToolCallbacks.from(fileTools)`，`.tools()` 冗余；模型原本会收到重复 tool schema。
- **Rationale**: 装饰器模式（`ToolEventEmittingCallback` 包 `ToolCallback` 做工具事件可视化）要求注册预构建 `ToolCallback[]`，而 Spring AI 2.0 中接受 `ToolCallback...` 的两个 API（`defaultToolCallbacks` + `toolCallbacks`）**均已 @Deprecated**；非 deprecated 的 `.tools(Object...)` 只接受裸 `@Tool` bean（内部 `ToolCallbacks.from`），无法注入 wrapped 回调。故装饰器路径暂不可避免地走 deprecated API（原代码即如此，本改动把 deprecated 调用从 2 处减到 1 处）。
- **Carry-over**: 真实 LLM e2e 未验（机器离线）——工具循环是否实际触发/不重复执行，待用户提供 key 后验证。若 deprecated API 未来移除，需改用 `ToolCallingManager` 自定义或 Advisor 观察工具执行（更大重构）。

## ADR-008: Vibe-Trading 参照边界与排期（2026-07-18）
- **来源**: [HKUDS/Vibe-Trading](https://github.com/HKUDS/Vibe-Trading)（金融垂直 Agent 工作台，非通用 OS）
- **Decision**: 只吸收 OS 级思想作设计约束；不复刻交易/因子/券商/IM 渠道等垂直功能；不因参照提前扩大 **Product-Spec §9 Phase 1** 已闭合后的范围（勿提前开 Spec Phase 2）。
- **可吸的 7 条核心思想**:
  1. 证据路径（接地 → 执行 → 校验 → 可检查产物）
  2. 高风险默认关、授权才开（fail-closed）
  3. 一个 runtime、多个入口（Web/CLI/MCP 同状态与权限）
  4. 多 Agent = 可配置分工 + 共享执行面 + 失败传播（非闲聊）
  5. 内核薄、能力外挂（技能/预设挂在原语外）
  6. 失败可见（禁静默成功）
  7. 任务契约 Goal（验收标准，不只靠 Prompt）
- **排期**:
  - **底座阶段（已收口）**: 工具显式注册与失败可见、同 API runtime、证据路径薄版；ATE 评测以现有小样本为止（见 ADR-009）。
  - **Phase 2（规格 M2.x）**: Hook/授权边界（M2.3）、多 Agent（M2.1）、MCP（M2.2）、Goal/run 契约与协作状态机、成本仪表盘（M2.4）。
  - **Phase 3+**: 应用市场/技能生态厚版、自主定时等。
- **明确不吸**: Alpha Zoo、Shadow Account 细节、行情 fallback 链、渠道堆叠、技能数量本身。
- **Rationale**: 垂直产品的 Star/功能密度不能外推为 Tepeu 路线图；与 Product-Spec §9 / DEV-PLAN「远期 Phase」一致，避免 scope 蔓延。

## ADR-009: ATE 扩面与 Docker 实测不做（2026-07-18）
- **Decision**: 终止下列待办，不再列入当前阶段缺口：
  1. ATE 20 任务扩面
  2. ATE 第二模型对照
  3. `experiments/ate-bench` Docker build / smoke 本机实测
- **保留**: 已有小样本结果（含 glm×C×3、T1 注入等）与 Dockerfile 定义文件本身；文章结论维持「初步验证」表述，不声称更大样本。
- **Rationale**: 用户明确裁切范围；继续扩面会占用底座/产品主线精力，且本机无 Docker 守护进程。
- **Follow-up (同日)**: 交接与 RELEASE 已知限制已对齐；本阶段文档收口完成。

## ADR-010: Spec Phase 3 交付顺序（2026-07-18）
- **Decision**: DEV-PLAN Phase 10–14 对应 Spec §9 Phase 3，顺序为 **自主 Agent（M3.1）→ 多端（M3.4）→ 应用市场（M3.3）→ WASM（M3.2）→ v1.0（M3.5）**。
- **Rationale**: 定时/后台任务最先产生可见产品价值且复用现有 Orchestrator；响应式改动面小；市场与 WASM 依赖技能生态与隔离模型，靠后降低返工。
- **Gate**: 须用户确认后写 plan-confirmed.json 再开 /dev-builder；WASM 阶段单独确认依赖选型。
- **Out of scope for v0.3 首切片**: 集群高可用（Spec §8.2 远期）、交易/垂直业务复刻（ADR-008）。

## ADR-012: 文件监听范围 — 监听全部 + 前端过滤（2026-08-04）
- **Decision**: `FileWatcherService` 监听**全部** workspace 根目录（启动时经 `WorkspaceRepository` 枚举注册，`WorkspaceService` create/delete 时动态注册/注销）；`GET /api/events` 常驻 SSE 事件带 `workspaceId`；前端 `useFileBrowser` 按当前工作区过滤 + 防抖刷新。**不**做「随 workspace 切换启停」。
- **Rationale**: DEV-PLAN Phase 12 原文「watcher 随 workspace 切换启停」实为「不泄漏到其他工作区」的意图；监听全部 + 前端过滤在效果上等价（非当前工作区的树不刷新），但实现更简单健壮——无重连窗口、无每工作区引用计数边角、workspace 数量少（单机个人工具）资源可忽略。
- **Implied**: WatchService 不递归，需手动递归注册子目录并在新建目录时补注册；跳过 `.git/node_modules/target/dist/.claude/.forge` 等噪声目录，防 SSE 刷屏。
- **Forward**: Phase 13（后台任务通知）可复用 `/api/events` 通道或在 `FileWatcherService` 上加通用 listener 总线。

## ADR-011: 本机实例令牌保护危险宿主 API（2026-08-02）
- **Decision**: 用本机生成的实例令牌（X-Tepeu-Token，文件 ~/.tepeu/instance.token）保护审批与写/删/上传/恢复文件 API，以及终端 WebSocket；仅 localhost 可拉取令牌。可用 	epeu.security.instance-token-enabled 关闭。
- **Rationale**: Phase 1 无完整登录（ADR-005），但审批与危险操作不能完全裸奔；实例令牌是单机最小门禁。
- **Not**: 多用户 OAuth/OIDC（仍属远期）。

## ADR-015: 技能脚本运行时选型 — GraalJS 沙箱，WASM 原生延后（2026-08-07）
- **Decision**: Phase 17（Spec M3.2）采用 **GraalJS（Polyglot `js-community` 24.2.1）** 作为「技能脚本」隔离执行引擎；**不引入 wasmtime-java / 原生 WASM 宿主**。脚本仅通过显式 `fs` 宿主 API 读写当前 workspace；禁止 IO/进程/线程/任意 Java 类查找；默认超时强制 `Context.close(true)` 中断。
- **Rationale**:
  1. Spec 写「WASM+V8 Isolates」目标是轻量 Agent 隔离；GraalJS Context 提供相近的 per-run 隔离与限权模型，且 Maven 精确版本可在现有 **JDK 21 HotSpot** 上嵌入，无需换 GraalVM JDK。
  2. `wasmtime-java` 依赖平台原生库与 JNI，部署面（Windows/Linux 双轨、CI）远大于本阶段「最小沙箱 + demo 工具」收益。
  3. 威胁模型（本阶段）：不可信脚本 → 默认无主机能力；唯一出口是 `WorkspaceScriptFs`（路径必须落在 workspace 根内，穿越/`..` 拒绝）；超时防死循环；不暴露 `java`/`Packages`/`Process`。残留风险：同进程内存侧信道、Polyglot 解释器性能；完整多租户硬隔离仍需进程/容器层（远期）。
- **Alternatives**: wasmtime-java（原生 WASM，部署重）；纯 Java AST 解释器（生态差）；GraalWasm（可与 JS 同 Polyglot，但本阶段无 WASM 技能包需求）。
- **Forward**: 若社区技能需要真正 `.wasm` 模块，再评估 GraalWasm 或独立 wasmtime 子进程；保持同一 `run_skill_script` 工具面。
- **Config**: `tepeu.runtime.script-timeout-ms`（默认 5000）；工具名 `run_skill_script`（toolKind=`script`，免批，因已沙箱化）。

## ADR-014: 移动端布局 — 抽屉 + 全屏预览 + 44px 触控（2026-08-05）
- **Decision**: 移动断点统一 `max-width: 767px`（与 minimap 断点一致）。`useMediaQuery` hook（JSX：顶栏统计折叠、抽屉状态、遮罩渲染）与 CSS media query（定位/触控）混合实现，不引入 Tailwind 响应式前缀（本仓零 `sm:/lg:`）。左栏在移动端变 fixed 抽屉（`min(84vw,320px)`，遮罩点击收起）；右预览变全屏 overlay（z-50）；顶栏 40→48px 容纳 ≥44px 按钮；顶栏 token/费用统计移动端隐藏（预算徽章保留）。预览 ✕ 现在真正关闭面板（原只清 `openFile` 留空面板，属顺手修复；桌面布局唯一行为变更）。
- **Rationale**: DEV-PLAN Phase 15「侧栏可折叠为底栏/抽屉」——底栏放 9 个次级面板过紧，抽屉是 IDE 类常规形态且改动面小；右预览本就是沉浸式主任务，全屏最省事。44px 触控只覆盖主路径控件（顶栏/抽屉/发送/文件行/预览工具栏/返回），不逐像素打磨表格单元格、版本面板等深层次要控件。
- **Verification**: `frontend/e2e/mobile-shell.spec.ts`（375×667：开工作区入口 → 发一条消息 → 打开文件预览）+ 桌面回归 specs（app-shell/files/workspace/chat）全绿；`useMediaQuery` 首次挂载用 `window.matchMedia`（浏览器环境，无 SSR 风险）。

## ADR-013: 后台任务通知用独立 `/api/task-events` SSE 通道（2026-08-05）
- **Decision**: Phase 13 自主任务完成/失败通知走**独立** `GET /api/task-events` 常驻 SSE（`TaskEventNotifier` hub + `TaskEventController`），不复用 Phase 12 的 `/api/events` 文件事件通道。
- **Rationale**: ADR-012 forward 提出「可复用 `/api/events` 或在 FileWatcherService 上加通用 listener 总线」，但两类事件特性不同：文件事件高频、需 250ms 合并 + 跨 tab leader 选举共享一条连接；任务通知低频、需每个 tab 都能即时弹出徽章/浏览器通知，且 `sharedFileEvents.ts` 已按 `file_changed` 专型化（parse + BroadcastChannel），塞入 task 事件会把它变成多职责模块。独立通道使后端（TaskEventNotifier 镜像 FileWatcherService 的 subscribe/sendJson/broadcast，~60 行）与前端（`useNotifications` 单例 EventSource，无需 leader 选举）都保持单一职责。事件同样走 `{type, ...}` 形状 + `SseEmitter.event().name("message")`，与 Phase 12 一致。
- **Implied**: 任务事件低频 → 每 tab 直连一条 SSE 可接受（不上 BroadcastChannel/leader）。`GET /api/task-events` 只读，免实例令牌。
- **Forward**: 后续其他低频后台事件（如 MCP 状态变化提示）可并入 `TaskEventNotifier` 或再开独立通道，按事件频率与职责划分。

## ADR-016: 企业 OS 内核、Adaptor 缝与编排积木（2026-08-15）
- **Status**: Accepted（架构边界；未要求立刻大重构）
- **Context**: 对照 DeepSeek Harness「能力缝 / 事件日志 / 插件化」后，确认 tepeu 最大问题是编排器耦合高，不利于多人开发与调试；企业方向需「饼心可卖 + 饼边可摊」，且 Subagent / Agent Teams / 长程任务不能再堆进上帝编排器。
- **Decision — 严格内核仅 3 件（冻住）**:
  1. **主体 + 命名空间**：谁（人/Agent）× 在哪（Workspace/将来租户）；个人知识默认仅本人。
  2. **能力总线（syscall 表）**：注册/分发；**入口策略钩子**（审批等）挂在此，不另立「权限层」。
  3. **会话设施（Session）**——仍是**一件**，不是第四内核：
     - **仅追加的事实日志**：承载**对话 + Agent 工具**事实；**模型可见 ⟺ 可从本日志还原**；日志**禁止**落明文 secret。人手宿主操作的审计真相在 `AuditSink`，**不**与本日志混为一谈（见「双真相域」）。回放对话/计量对话 token 以本日志（及 Metering）为准。
     - **有序 Inbox / claim**：活如何进入本会话、由谁领取进入 turn（禁止编排器私自「旁路拼消息」）。
     - **会话关系**：主/子会话（话题枝）、`parentId` / 分叉点等；单调序号属日志实现细节。
     无 Inbox 契约则只有账本、没有进场规则，编排器会再次耦合。
  - **分层位置（由内向外；实施底板 [`docs/os-baseplate.md`](../docs/os-baseplate.md)，短图 [`docs/kernel-layer.md`](../docs/kernel-layer.md)；规范以本 ADR 为准）**：  
    **① 内核三件**（总线入口 = 钩〔**Policy + 卫兵**〕，实现属②；会话含主/子与**日志替换端口**；日志禁明文 secret）  
    → **② 适配环**（驱动插头，清单见底板 §3.1；含 Tool、MCP、**Compaction**、AuditSink、Metering…）  
    → **③ 编排环**（兜底 Agent、Loop、Team/Subagent/LongTask、PromptAssembly、ReasoningPresenter、Command）  
    → **④ 路由环**（Thread/Flow/Model；Model 只选型）  
    → **⑤ 应用/呈现**。  
    **人机同底座**：对话主路→会话日志；人手旁路→AuditSink；Slash→③ Command（不与人手旁路混写）。主路旁路碰总线时门对称。  
    **开 Agent turn 前**：Metering 预算硬门。  
    **真压缩**：属 **`Compaction` 缝**（非内核内嵌 LLM）；经总线 `llm.*`，同受卫兵+Metering；写回经会话日志替换端口。  
    **TurnContext** 显式跨环传递；禁止单例 bind。  
    **底板 = 内核三件**。compose 只接线。
- **Decision — 会话/多副本相关必须有可扩展 Adaptor（单机默认，集群可换）**:
  - 内核只认 Session 契约（日志 + Inbox/claim + 会话关系）；**存储与跨副本通知不得写死在内核**。
  - 至少预留下列缝（接口先定，单机先简实现）：
    | Adaptor | 单机默认 | 多副本可换为 |
    |---------|----------|--------------|
    | `SessionStore` | 本地 SQLite/文件 | 中央 DB / 日志服务 |
    | `InboxClaim` | 进程内锁 | 分布式租约抢占（防双跑） |
    | `ApprovalStore`（属 Policy 旁路状态） | 内存 | 共享存储 |
    | `ProjectionBus` | 本机直推 SSE | Redis/NATS 等 Pub/Sub（只做通知/投影，**不是**消息真相） |
    | `Execution` | 本地盘/进程 | 共享盘 / 远程沙箱 |
  - **双真相域（裁决）**：  
    - **会话日志** = 对话 + Agent 工具事实的真相（模型可见 ⟺ 可还原）。  
    - **AuditSink** = 人手宿主操作等审计真相（企业导出必含）；**不**写入冒充对话的会话事实。  
    - `ProjectionBus` 禁止充当任一真相源。
- **Decision — 明确不进内核**:
  - **Agent Loop** = 编排环可替换运行时（turn/step），不是内核。
  - **Skill 资产文件、市场 UI、记忆面板 UI** = ⑤ 应用/呈现；Skill **激活**走 ③ PromptAssembly。  
  - **Tool 实现与 MCP Bridge** = **② 适配插头**（挂能力总线），**不是**⑤，也不是「无家可归的外围」——旧称「外围积木」仅指勿进①内核。  
  - 不引入 Cordis；宿主仍 Spring；先包级乐高 + 依赖规则，再视需要升 Maven 多模块。
- **Decision — Adaptor 缝（接口先定，默认实现先简；边界图须显式列出）**:  
  `Identity` · `OrgNamespace` · `Policy` · `AuditSink` · `Metering` · `KnowledgeSource` · `Execution` · `LlmProvider` · `Tool` · `McpBridge` · `Compaction` · `Secret` · `SessionStore` · `InboxClaim` · `ProjectionBus` · `SubagentAdaptor` · `TeamAdaptor` · `LongTaskAdaptor` · `PromptAssembly` · `ReasoningPresenter` · `CommandDispatcher` · `ThreadRouter` · `FlowRouter` · `ModelRouter`。  
  分类与单机/集群默认见 `docs/os-baseplate.md` §3。业务只依赖接口；`agentKind` 默认 `PERSONAL`。  
  **工具之间禁止互引**；Loop **禁止** import 具体 Tool 类。
- **Decision — 企业最小可卖（MVP）挂在缝上，不摊全企业**:  
  多人角色 · 组织下项目隔离 · 危险操作审批 · 审计导出 · 项目预算 · 薄项目共享知识。  
  **不做**：完整四智能体、SSO/OIDC、多租户集群、市场/移动/进化引擎当卖点。
- **Decision — 三类编排积木（运行时之上，可插拔）**:
  | 积木 | 缝 | 规则 |
  |------|----|------|
  | **Subagent** | `SubagentAdaptor` | 派生 principal；工具集只减不增；子事件挂 parent/delegation；回传摘要 |
  | **Agent Teams** | `TeamAdaptor` + **Preset 配置** | 现有 Planner→Implementer→Reviewer 收编为 preset，禁止第三套硬编码编排器 |
  | **长程任务** | `LongTaskAdaptor` | 持久状态机 + 多次短 turn；`waiting_human`；从现有 Schedule/task **演进**，不平行再造 |
  卖点顺序建议：长程薄版 → Subagent → Teams preset 化。MVP 可不做前两块卖点，但**接口与事件字段先留**。
- **Decision — Agent Flow vs Agentic Flow（两种编排积木，勿混一个 Orchestrator）**:
  - **Agentic Flow**：目标驱动；模型在 turn/step 工具循环里自选下一步（普通对话、长程每次唤醒的内环）。积木：`Loop` +（可选）`LongTask` 外壳。
  - **Agent Flow**：流程驱动；按 Team/Workflow **preset 图**走角色与边（现有 Planner→Implementer→Reviewer 应收编为此）。积木：`TeamAdaptor` / workflow preset。
  - **组合**：企业常见「外层 Agent Flow、节点内 Agentic Flow」；Subagent 两种均可挂；门 / PromptAssembly / 记忆为共用缝，不按 flow 复制。
  - 产品与文档可用此二词；实现认积木名，不新增第三套编排器。
- **Decision — 兜底 Agent（每项目默认运行身份）**:
  - **系统必须有兜底 Agent**：每个 Workspace 隐含（或自动创建）一个默认 Agent 上下文，用作普通对话、Skill 激活、工具循环的承载者；**禁止**「无 Agent 却走 Orchestrator 调 Skill」。
  - **定位**：不是第四套编排器，而是 **Agentic Flow 的默认 principal**（`agentKind` 默认 `PERSONAL` 或项目配置的默认种）；无 Team preset / 无显式 Agent 时一律落兜底。
  - **与 Skill**：Skill 仍属项目资产；调用时挂到**兜底（或当前选中）Agent** 的 PromptAssembly，不经「无主体 Orchestrator」。
  - **与项目隔离**：兜底 Agent 的会话/记忆/授权按 Workspace 隔离，不跨项目共享脑子。
  - **可替换**：企业可把兜底换成岗位预设人格，仍是「默认 Agent」，不是取消兜底。
- **Decision — 与 DeepSeek Harness（dsh）插件的关系**:
  - dsh 是可参照的 **harness 底盘**（缝、事件、组装），不是 tepeu 的个人版 SKU，也不是插件市场。
  - **禁止**假设「安装 dsh Cordis 插件即可被 tepeu 加载」——运行时（Node/Cordis vs Java/Spring）与扩展 ABI 不兼容。
  - **允许的复用**：设计对齐；**MCP** 互通；技能 Markdown 内容迁移；可选经 ACP/JSON-RPC/子进程把 dsh 当**外部引擎**桥接。tepeu 自有扩展面仍是 Adaptor / Tool / 技能包。
- **Decision — 继续可向 dsh 学的（榨干清单）与「开发活 / 固化稳」双模**:
  - **仍值得学（固化也要）——稳、准、效率优先**:
    1. **运行时不变量（invariants）**：tool_call↔result 成对、turn/step 包裹、序号单调；违反即失败可见（准/稳）。
    2. **真·压缩缝（`Compaction`）**：经总线 `llm.*` + 会话日志替换端口；同受卫兵与 Metering（非内核内嵌调模型；非仅 clear-history）。
    3. **工具超时 / 循环卫生 / 取消**：防死循环与悬挂 turn（稳/效率）。
    4. **TurnContext 显式传递**（ScopedValue 等），禁止单例 bind 污染（准/稳，尤其虚线程）。
    5. **配错即响**：缺引用/错误 preset 启动或首用失败，禁静默跳过（准）。
    6. **回放 / 快照测**：关键路径可对事件流或固定输出做回归（准）。
    7. **token 计量与压力信号**：压缩与预算同源测量（效率）。
  - **开发模式可学、固化不必追（灵活实验）**:
    - Cordis 热插、`--dump-config` 式整树打印、agent 自改插件树、海量 Provider 并行、100% 覆盖门禁仪式、为灵活而灵活的多层 patch。
    - 开发期可用本机 dsh **外部引擎/对照实验**；固化发行 **冻结 Adaptor 集合与事件 schema**，默认透传路由，不热换野生插件。
  - **双模原则**：开发 = 多缝、可换、日志拉满、可桥接 dsh 学习；固化 = 薄内核不变、实现集收敛、不变量与超时默认开、灵活让位于稳定/准确/效率。企业卖的是后者。
- **Decision — 模型路由层（该有缝，不该急着做聪明实现）**:
  - **该有**：`ModelRouterAdaptor`（可挂在 LLM 能力上）——输入目标/预算/延时/请求指定等，输出 `providerId` + 可选 fallback；组装层只认此缝，禁止在 ChatController/Orchestrator 里写死选模型分支。
  - **默认实现（MVP）**：透传请求中的 `providerId`（可加静态 fallback 列表）；**不做**「简单/复杂自动选大/小模型」分类器。
  - **企业省钱优先**：预算硬门 + 项目默认模型，覆盖智能路由大部分诉求；真·智能路由以后换 Adaptor 实现，不改内核。
  - **勿混**：模型路由 ≠ Agent Flow 图路由 ≠ 能力总线分发 ≠ HTTP 路由。`FlowRouter` / `ThreadRouter` / `ModelRouter` 同属 **④ 路由环**（薄、默认可透传）；Model 的 Provider 在 ②。
- **Decision — 思维链 / 推理过程（不进内核，必留事件缝）**:
  - **三分开，禁止混成一种「thinking」字符串**:
    1. **Model reasoning**（厂商原生 thinking/reasoning_content 流）— 模型侧隐式推理  
    2. **Agent plan**（显式计划/待办，如 todo、Goal 步骤）— 编排侧可执行意图  
    3. **Tool trace**（工具调用与结果）— 已有过程事件，不算思维链  
  - **落点**：均为**会话事实事件**（如 `assistant/reasoning`、`plan/step`），由 UI 投影；**不**塞进普通 `assistant` 正文冒充最终答复。  
  - **缝：`ReasoningPresenter`（可选 Adaptor）**：控制是否持久化全文、是否对用户可见、是否进下一轮模型上下文（企业可关「推理回灌」防泄密/省 token）。  
  - **与编排关系**：Subagent/Teams 各自可有独立 reasoning 事件，挂同一 `delegationId`；长程任务每次短 turn 的 reasoning **按 turn 追加**，任务级只保留计划投影，避免把多日隐式思维拼成巨上下文。  
  - **企业 MVP**：至少能**展示并回放** tool trace；原生 model thinking 与 plan 事件**接口先留**，默认实现可先「透传存储 + 折叠展示」，策略默认：reasoning **可展示、默认不回灌**（可配）。  
  - **不做**：把 CoT 提示词技巧写进内核；用 system 消息糊全部思维过程（违背事件日志原则）。
- **Decision — 用户提示词与系统提示词（组装缝，不进内核）**:
  - **用户提示词（User）**：会话事实事件 `user/message`（可含附件/引用元数据）；是 Inbox 输入，不是拼进上帝字符串的临时变量。`@文件` / 粘贴等 → 先落事件或 attachment，再由组装器决定是否进模型。
  - **系统提示词（System）**：禁止「Orchestrator 里一大段 String 拼接」。改为 **`PromptAssembly` 缝**——按有序 **Section** 注册组装：
    | Section 例 | 来源 | 备注 |
    |------------|------|------|
    | `base` | 产品/预设 | 身份与总规，版本化 |
    | `workspace_rules` | 项目规则文件 | 随 Workspace |
    | `skills` | Skill/Knowledge 缝 | 激活技能正文 |
    | `memory_hits` | 记忆检索 | 须可追溯来源 id |
    | `tools_schema` | 能力总线 | 工具描述随注册变化 |
    | `team_role` | Team preset | 多角色时按角色切换 |
    | `task_brief` | LongTask | 长程每次唤醒的目标摘要 |
  - **组装规则**：Section 有 id、顺序、是否进模型、是否持久化；**进模型的内容必须可从日志或「prompt_assembly 快照事件」还原**（满足 model-visible ⟺ logged）。  
  - **pre-step 钩子**（挂 Loop，非内核）：可改写/拒绝本轮进入模型的 messages；技能命中、记忆注入走 Section，不手写进 Orchestrator。  
  - **与 ReasoningPresenter**：若策略允许「推理回灌」，也只是多一个 Section（如 `prior_reasoning`），默认关闭。  
  - **企业**：组织规范 / 共享知识只经 `KnowledgeSource` → Section；个人记忆 Section 不得在无授权时进入企业任务上下文。  
  - **Slash / 系统命令**：进 ③ Command 分发（不经模型、不进 PromptAssembly）；与人手 REST 旁路分离。  
  - **MVP**：先把现有「技能 + 记忆 + 历史」收成 Section 接口 + 默认组装器；UI 可折叠展示「本轮用了哪些 Section」（白盒）。
- **Decision — 依赖与事件硬规矩**:
  - 上层可依赖下层接口；**工具属 ②、彼此禁止互引**；Loop **禁止** import 具体 Tool 类；Loop/Orchestrator **禁止**直接拼系统提示词长字符串（须经 PromptAssembly）；**禁止编排器旁路拼消息**（必经 Inbox/claim）。
  - 委派/团队交接/任务状态变更必须落会话（或任务投影）事件，带 `delegationId` / `taskId` 供 Audit/Metering。
  - reasoning / plan 事件与 assistant 正文分列；回灌须显式经 `ReasoningPresenter` 策略，禁止静默拼进 Prompt。
  - 用户消息与系统 Section 分列；禁止把技能/记忆默默写进一条匿名 system 而无法追溯 section id。
  - **模型可见 ⟺ 会话日志可还原**；人手宿主操作 → AuditSink，不冒充会话对话事实。
- **Alternatives rejected**: 把 Loop/审批/多 Agent/思维链/提示词拼装塞进内核；照搬 Cordis；先做完整四智能体再卖；为 Teams 再写上帝编排器；用 system 行混装 thinking；Orchestrator 继续巨型 Prompt 字符串；固化期追求与 dsh 同等热插灵活。
- **Forward**: 实施以 `docs/os-baseplate.md` + 仓库 `os/` 骨架为准；优先 TurnContext、会话事件最小集、PromptAssembly、总线+Policy；本 ADR 不自动授权大范围从 legacy 搬功能，动手前按黄灯确认切片。

