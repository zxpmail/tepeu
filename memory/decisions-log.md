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
  - **分层位置（由内向外；实施底板 [`docs/os-baseplate.md`](../docs/os-baseplate.md)；规范以本 ADR 为准）**：  
    **① 内核三件**（总线入口 = 钩〔**Policy + 卫兵**：超时/取消/不变量/**配额限流**〕，实现属②；会话含主/子与**日志替换端口**；日志禁明文 secret）  
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
    | `ApprovalStore`（属 Policy 旁路状态） | 本地 SQLite（**审批是合规证据，禁内存默认**） | 共享存储 |
    | `ProjectionBus` | 本机直推 SSE | Redis/NATS 等 Pub/Sub（只做通知/投影，**不是**消息真相；下发前按**查看者 ACL** 过滤，防越权泄露） |
    | `Execution` | 本地盘/进程 | 共享盘 / 远程沙箱 |
  - **双真相域（裁决）**：  
    - **会话日志** = 对话 + Agent 工具事实的真相（模型可见 ⟺ 可还原）。  
    - **AuditSink** = 人手宿主操作等审计真相（企业导出必含）；**不**写入冒充对话的会话事实。  
    - `ProjectionBus` 禁止充当任一真相源。
- **Decision — 明确不进内核**:
  - **Agent Loop** = 编排环可替换运行时（turn/step），不是内核。
  - **Skill 资产文件、市场 UI、记忆面板 UI** = ⑤ 应用/呈现；Skill **激活**走 ③ PromptAssembly。  
  - **Tool 实现与 MCP Bridge** = **② 适配插头**（挂能力总线），**不是**⑤，也不是「无家可归的外围」——旧称「外围积木」仅指勿进①内核。  
  - 不引入 Cordis；宿主仍 Spring。包级乐高已升为 Maven 多模块（第十一轮：组件 ≠ 插件）。
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
  - **该有**：`ModelRouter`（④ 路由缝，Provider 实现在 ②）——输入目标/预算/延时/**合规约束（数据驻留等）**/请求指定等，输出 `providerId` + 可选 fallback；组装层只认此缝，禁止在 ChatController/Orchestrator 里写死选模型分支。
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
- **Decision — dsh 四路源码深读落位（2026-08-15 第三轮严苛对账）**:
  1. **传输层日志重建断言（最高优先）**：`llm.*` syscall 入口拦截器断言「请求 messages 与 `deriveMessages()` **逐字节相等**、config 与 folded request header 相等」——把「模型可见⟺日志可还原」从口号变机器检查（dsh `agent-loop/invariant.ts` 先例）。
  2. **Compaction 代数**：压缩**不删事件**——摘要为带 `surfaceOp{op:replace,start,end}` 的事件，`sourceEventSeqs` 必须完整覆盖被遮蔽节点；**双读者分离**：模型读 surface（旧文不可见）、人类 transcript 读 append-origin 事件（全文可见）；禁止改写日志。工具大结果先确定性剪枝再摘要；剪枝/摘要均记 shadow 记账事件。
  3. **事件日志立规**：`seq = log.length` 强制连续；append 点做 lossless 校验（坏事件在 append 失败，不在 flush）；未知事件默认 required-fail（需 `ignorable:true` 才可跳过，禁静默丢数据）；崩溃恢复**补合成闭合**（open turn 补 `turn/end{interrupted}`）不截断；fork/resume 用 `end-seed` 边界事件区分种子/活写。
  4. **Policy 封闭词汇表 + 审批单次许可**：Policy 返回值为封闭 union（allow|deny|ask 类），词汇表外返回/插件异常一律规范化为拒绝（fail-closed，禁异常穿透）；审批 = `asked/decided` 事件对落会话日志、必须在 open turn 内、许可严格**单次**（allowed-once）——**不发放长期能力，从结构上消灭「撤销已授权能力」**（显式债务大半关闭；残余=沙箱类运行中资源回收，如 dispose 撤销 ACL grant）。
  5. **执行隔离契约先行**：`SandboxPolicy`（mode + workspaceRoot + sessionId）随能力调用携带，在 spawn 点由 OS 级机制执行（Linux 方向：子进程 + bwrap/landlock；Windows 方向：Job Object/restricted token）；隔离完备性 `full|partial` 是**报告事实**而非承诺，配功能性 probe；**禁止静默未沙箱直通**（全不可用即失败可见）。Java 侧选型单独立项（黄灯），先落缝与契约。
  6. **编排缝契约四条**：Loop 三态 `idle|maintenance|running`（maintenance 为独占 idle 窗口，回答「两次 turn 之间谁拥有 agent」；后台/定时任务不得与模型 turn 抢执行面）；长程续跑 = **预约-复核**（先持久 checkpoint，再预约 `(taskId,revision,round)`，pre-step 前后各验一次，失效拒绝该 step 并归还被 claim 消息）；Subagent `delegationDepth` 持久化为**单调下界**（重启不得降级，防递归逃逸）；终态写权限来自**消息溯源**（host-attested user 源或精确匹配的机器轮次源），非 ambient 会话状态。
  7. **PromptAssembly 三补**：**静态 Section / 动态 PromptContext 分离**——稳定身份进 system prompt（KV-cache 前缀稳定），动态事实以 sourced user-role 快照落 durable history、只在变化或被压缩遮蔽时重发（企业省钱直接相关）；技能**目录/正文两段式懒加载**（目录仅 name+description 带 digest，正文按需加载、每次重读盘）；超预算丢弃必须**出账单**（先丢宽泛后截最具体 + 显式通知列省略路径——白盒可见性的另一半）。
  8. **Secret 四细则**：配置只放 branded 引用（不放假路径）；每次操作重解析、禁跨操作缓存（轮换下个请求生效）；解析结果永不进模型可见通道；文档诚实标注「克制品不是安全边界」。**GDPR Open 加候选 B**：导出侧脱敏、canonical 日志永不重写（dsh telemetry 先例），与 crypto-shredding 并列待裁。
- **Alternatives rejected**: 把 Loop/审批/多 Agent/思维链/提示词拼装塞进内核；照搬 Cordis；先做完整四智能体再卖；为 Teams 再写上帝编排器；用 system 行混装 thinking；Orchestrator 继续巨型 Prompt 字符串；固化期追求与 dsh 同等热插灵活；Policy 用异常穿透做拒绝；压缩改写/删除日志事件；审批发放会话级长期能力。
- **Decision — 企业评审落位（2026-08-15 第二轮严苛评审）**:
  - **配额/限流是入口卫兵**（per-principal/namespace 的速率、并发、token 硬顶，**入口处拒绝**）；Metering 只是事后仪表盘。刹车 ≠ 仪表。
  - **OS 类比诚实度**：当前内核 = syscall 表 + 事件日志 + 卫兵（journal-first），**不是**完整 OS。以下为**显式债务**，不许靠类比暗示已具备：调度公平/优先级队列、Agent 资源隔离边界（超时/取消只是部分覆盖）、运行中能力撤销（会话中途吊销工具授权，已发 syscall 如何处置）、日志 tamper-evidence（哈希链）。
  - **待裁决（Open）**：append-only 会话日志 vs 删除权（GDPR/个保法）——候选 crypto-shredding（按租户密钥加密日志段，删租户=销毁密钥）；未裁决前不得声称合规。
- **Decision — CC 源码对账落位（2026-08-16 第四轮严苛对账）**:
  > 参照 `docs/archive/reference/claude-code-reference.md`（claude-code-best v2.8.4 源码六路探查 + 严苛驳斥轮）。CC 为同构实现的压力测试参照；已冻方向（封闭 union、合成闭合、journal-first、单调 delegationDepth）获同构印证，下列为本轮新裁决与修正。
  1. **`llm.*` 断言规范形态（修正第三轮第 1 条的字面表述）**：断言目标 = `derive(log) ∘ normalize == sent` 逐字节相等；`normalize` 是**版本化纯函数**（provider 侧合法变形——连续 user 合并、media 上限剥离、cache_control 逐请求布点——只准发生在其中），版本号随事件落日志。禁止无版本、无记录的黑盒归一化：字面断言会误伤合法发送，黑盒断言会漏真漂移。
  2. **syscall 注册表规范序（内核不变量）**：能力总线 syscall 枚举序**确定且稳定**——工具数组顺序是缓存键与 `llm.*` config 相等断言的组成部分；禁止实现迭代序（HashMap 等）泄漏进断言。
  3. **卫兵组合代数**：多卫兵/多 hook 决策聚合 **deny > ask > allow** 格；任何 allow（含卫兵或编排器的批准）**不得**越过 Policy 的 deny/ask（「批准压不过拒绝」）；组件异常一律规范化为 deny（沿 fail-closed）。
  4. **Inbox 优先级与抢占**：InboxMessage 带 priority（now/next/later）；now 级用户消息在 turn 运行中到达 → 抢占当前流（合成闭合后让位），排队消息即上下文。claim/租约语义不变。
  5. **fork 跨 seed 记账**：fork 必须携带全部替换/surface 记账（CC 事故先例：不携带 → 永久 cache miss）；自身写入的 `replaceRange` 区间**不得伸进种子区**，越界即 append 失败。
  6. **取消传播拓扑**：delegation 树上取消沿父子边传播；**后台子代理不挂父取消**（父 turn 取消不杀后台，显式 kill 才杀）；兄弟连坐仅限执行类（shell）失败。
  7. **熔断器作用域参数化**：CircuitBreaker 卫兵必须带作用域（per syscall / per provider / per principal），禁全局单例熔断——一个主体的过载不得饿死他人（「调度公平」债务的具体化）。
  8. **Metering 拦截面（裁决为有意取舍，非遗漏）**：单机版开 turn 前拦 + provider 级 max_tokens 兜底；不做逐消息费用检查。多租户企业版再升格逐消息。
  9. **错误事件可见性**：需 resume 后模型可见的错误（API/传输失败，续跑须知道先前失败）是**会话事件**（新事件类型）；纯人看的宿主/运维错误 → AuditSink。「模型可见⟺日志可还原」照旧封口。
  10. **审批中间态拒绝理由（记账）**：拒绝「运行中交互产生的有界 TTL 记住型规则」。界线 = **事前声明 vs 会话中授予**：事前声明式规则走 Policy 配置面（可审计、可评审、可版本化）；运行中审批严格单次。若单机 UX 实测不可忍受，可再裁决「ApprovalStore 带 expiry 规则」为显式扩展，不静默引入。
  11. **maintenance 窗口两细则**：maintenance 有**强制上限**（超时让位给等待中的 now 级消息）；唤醒 latch = 开窗时快照 Inbox 水位，闭窗时重放其后到达的 now/next 消息进 claim 队列。
  12. **CommandDispatcher 端口两型**：③ 端口只见 `local`/`prompt`；UI 面板命令 = ⑤ 向 ③ 注册的 local 命令处理器（内不依赖外，照旧）。
- **Decision — Pi 源码对账落位（2026-08-16 第五轮严苛对账）**:
  > 参照 `docs/archive/reference/pi-reference.md`（Pi agent harness v0.84.2 五路探查，严苛节内置）。Pi 为极简参照（tepeu `os/` 的 TS 同行）；v3 之坑反证 tepeu 已冻决定，format-4 与 tepeu 趋同。本轮裁决：
  1. **会话设施三 store 化（修正内核三件第 3 项表述）**：事实设施拆为三个显式存储职责——**entries**（append-only 对话事实+审计；模型可见⟺可还原者皆在此；surface 替换只在此层）、**registers**（覆盖写可变状态：Inbox/claim 租约、分支 leaf、模型/思考档配置、进行时操作状态；**恢复=点查非重放**）、**ledger**（append-only 用量记账：token/费用）。「每个载荷恰好属于三者之一，没有第四个地方」（Pi format-4「no third place」）。Inbox/claim 的寄存器形态自此说破（原为隐含）。
  2. **配置与编排禁入事件词汇表（长期闸门）**：model_change / 思考档位 / 工具集切换等配置类状态一律走寄存器，不产生会话事件。现 `SessionEventType`（8 类）已合规，无需迁移；此为词汇表演进的否决项（Pi v3 把配置写进树、format-4 判错的教训）。
  3. **Policy 与 Sandbox 分工写明**：**Policy = 授权**（谁可请求什么；封闭 union；进程内判定，fail-closed）；**Execution/SandboxPolicy = 隔离**（OS 级机制在 spawn 点执行，完备性 full|partial 如实报告）。Policy 永不冒充隔离边界——「半吊子进程内沙箱比没有更危险」的批评（Pi security.md）据此吸收为**分工**而非取消。
  4. **裁决限期落码（工程规矩）**：每条新裁决须指认落码切片；**连续两轮未落码的裁决标「悬置」**，悬置裁决不得作为后续裁决的前提。防底板演化为「2941 行规范对 796 行实现」的 Pi harness 空壳形态。
- **Decision — OpenCode 对账落位（2026-08-16 第六轮严苛对账）**:
  > 参照 `docs/archive/reference/opencode-reference.md`（OpenCode v1.18.18 五路探查）。其 EventV2 与 tepeu ① 已冻决定逐条同构（seq 连续 / 未知 die / 幂等重放 / 事务内投影），生产级印证，不另立条。本轮两裁决：
  1. **事件词汇表机制（补 §9 立规，三件）**：① **per-type 版本化**——事件 schema 变更时 bump 该类型 `version`，持久化键为 `type.version`；旧版本定义保留专供历史 decode，当下发布走 `latest`。② **显式 manifest**——词汇表为编译期聚合清单（含对外暴露子集），重复定义启动即失败。③ **数量钉死测试**——manifest 成员与数量由测试断言，新增事件必须显式改测试（防词汇静默漂移；OpenCode 85→88 计数测试先例）。未知 type/version 仍 required-fail（沿第三轮立规）。**落码切片（按第五轮 C3 纪律指认）：② conformance 套件——SessionEventType manifest 钉死测试。**
  2. **A2 备注（不改裁决）**：OpenCode 实例级 always 记忆的跨 session 泄漏（A 会话批准 B 会话生效）是第四轮 A2 拒绝理由的**活例证**。若将来 UX 实测逼宫需解禁「记住」，唯一可接受形态 = **工具在 ask 时声明可记 pattern + 会话内 + 不跨 session + 显式 expiry**；届时另行裁决，不得静默引入。
- **Decision — 设计审计落位（2026-08-16 第七轮严苛审计）**:
  > 六轮累积后的全面审计（矛盾/冗余/遗漏/错误四类）；其中文档级缺陷（§3.1 表格损坏吞掉 AuditSink 行、抢占边界未落文、§5 未随三 store 更新、「编排禁入」措辞误伤 PLAN_STEP、entries「+审计」抢词、§0 标题失真、挂账无归集）**已立即修复入底板**。本轮四裁决：
  1. **压缩执行位置双轨（解 M1+M5）**：**触发式压缩**（上下文满/overflow，对话不可等待）**内联在 turn 路径内**执行，其 `llm.*` 调用占该 turn 预算、随 turn 过 Metering 门；**主动/后台压缩**（维护性摘要）走 maintenance 窗口，按**独立预算条目**过 Metering 门。「一切 `llm.*` 受 Metering」的落法：turn 内随 turn 门、maintenance 内按窗口预算条目，**不存在无门的 `llm.*` 调用**。同时调和第二轮「Metering 只是事后仪表」与第三轮「预算硬门」的措辞冲突：**配额=入口卫兵；预算门=Metering 供数、与 Policy 协作拦截；Metering 自身不产生裁决**。落码：③ Loop + Compaction 缝。
  2. **fork 后 seq 空间（解 E2）**：种子事件**保留原 seq**；自身写入从 end-seed 后**续接同一单调空间分配**。「seq = log.length」表述修正为：**seq 由 append 点分配、本日志内严格单调连续且唯一；无种子日志才等值于 length**。replaceRange 区间校验在同一空间内进行、禁伸种子区（沿第四轮）。落码：① session（seq 分配与区间校验 + 测试）。
  3. **C3「轮」的定义（解 E3）与当场核对**：「连续两轮未落码」的**轮 = 落码切片轮**（一次合入 develop 的实现切片计一轮；对账轮不计数）。核对结果：第四~六轮 18 条裁决的落码映射已入底板 §8.5 挂账清单；第五/六轮已指认 ② conformance 切片、未逾期。处置：**冻结新对账轮，直至 ② conformance 切片落地**（同时 discharge 第六轮 manifest 测试与第五轮三 store 用例）。**「冻结」的可操作定义（同日代码审计二 C7 补全）：冻结 = 不新增 ADR 裁决与底板红线变更；清点/吸收/审计类文档不受限；解除条件 = ② conformance 切片合入 develop。**
  4. **claim 租约 TTL/fencing（解 O2）**：租约必带 TTL——单机默认=持有进程存活 + 崩溃后过期可被回收，turn 级租约随 turn 终态释放；多副本升级为 fencing token（单调递增，Pi/OpenCode 先例），旧持有者写被 fence 拒绝。落码：① session（InboxClaim 契约 + conformance 用例「死租约可回收」「fence 拒绝旧写」）。
- **Decision — 蒸馏轮（2026-08-16 第八轮，用户指令）**:
  > 隐喻：EJB 思想先进但死于接口森林，Spring 蒸馏其思想则活。六参照+legacy+agent-utils+netty 吸收后，底板出现缝合怪风险（26 命名缝 × 每缝行为规范 × 五张对账索引表，209 行近半为 ADR 复述）。本轮为**减法裁决**（用户明令「该砍一定要砍，少也是多」），底板重写为最小内核规范。**砍单七刀**：
  1. §0–0.8 五张对账索引表全删（ADR 为真相，正文已落位，索引表是第三份复述）。
  2. 缝行为细则降级：底板每缝一行职责 + 指针，已裁细则完整保留于本 ADR 轮次（真相不动）——细则前置到无代码处即 Pi harness 式漂移（第五轮 C3 所防）。
  3. **④ 路由环并入 ③**：环的判据=独立不变量+独立替换边界，三个默认透传决策函数不够格。洋葱五环→四环；`os/routing/` 目录待 ③ 落地时并入。
  4. Execution/Tool/McpBridge/LlmProvider/Compaction 从「缝」降为 **syscall 命名族**（注册进总线即存在，内核不感知类型）；**内核必需端口收敛为 4 个**：SessionStore / InboxClaim / Policy·ApprovalStore / Metering。
  5. 红线 9→7：dsh 双模原则（过程原则）与裁决限期落码（§8.5 头部已有）移出红线。
  6. **`SYSTEM_NOTE` 从事件词汇表砍除**（语义未定义=垃圾抽屉；需要时按 manifest 流程显式加回）。落码=② conformance（manifest 钉 7 类）。
  7. Secret/KnowledgeSource/ProjectionBus/Identity 归并为支撑服务一行组（⑤/② 关切，非内核契约）。
  **不砍（稳定项）**：三 store、双真相+错误归属、封闭 union、fail-closed、§9 事件立规全节、三条进路门对称、红线 1–5/7/8、债务表与挂账账本。**蒸馏判据写进底板头**：内核=少量冻结概念+不变量；一切能力=注册进总线的插头；底板只写内核规范与红线。冻结状态：本轮为用户指令下的减法例外；对账冻结其余条款不变，下一动作仍为 ② conformance 切片落码。
- **Decision — kernel 端口演化刀（2026-08-17 第九轮，落码切片轮）**:
  > 第七轮 C3 纪律：轮 = 落码切片轮。本刀销代码审计二 C1/C2/C3 三挂账 + 计量槽位挂账 + ledger 零代码 + priority drift；bus/session 契约收紧、conformance 重写、adaptors 重建，`mvn -f os/pom.xml test` 31 用例全绿。
  1. **C1 审批端口形态 = 同步重试式 ask**：`ApprovalStore`（内核必需端口，与 Policy 同行）——`ask` 登记 asked（同 (session, syscall) 未决时幂等返回同一 approvalId）并由总线抛 `ApprovalRequiredException`；决策者 `decide(approvalId, allow, by)`；重试同一调用时 `consumeDecision` 取走即消费——**许可严格单次**，消费后再调同 syscall 须重新走审批。替代旧「ASK≡DENY」假实现。证据须持久（`ApprovalRecord` asked/decided 对）；生产默认 SQLite（审批是合规证据，**禁内存默认**），`InMemoryApprovalStore` 仅 conformance 用。审批按会话隔离（许可不跨 session 复用）。
  2. **C2 未装配默认 = fail-closed 拒绝**：未装配 `PolicyHook` → 一切调用 `PolicyDeniedException(DENY)`；NEED_APPROVAL 而未装配审批通道 → `PolicyDeniedException(NEED_APPROVAL)`。废除旧默认 ALLOW（fail-open）。
  3. **C3 失败双通道 = 拦截走异常、执行走结果**：拦截类（取消/卫兵/Policy/需审批）抛 `BusGuardException` / `PolicyDeniedException` / `ApprovalRequiredException`，**catch 方 = 调用方（③ Loop）**，总线不吞、不代写日志；执行类失败不抛穿（`ok=false + errorCode`：NOT_FOUND / HANDLER_ERROR）。TOOL_CALL/TOOL_RESULT 事件落账归属仍与「总线自动落事件」挂账同随 ③ 裁。
  4. **计量槽位入基座**：`SyscallResult` 增 `usage` / `latencyMs`；`Usage` 四字段 inclusive 双轨第一步（`inputTokens` = 非缓存输入；`totalInput()` = input + cacheRead + cacheWrite，OpenCode 不变式）。cost/分层计价随 `llm.*` 断言切片。
  5. **ledger 第一刀 + Metering 定形**：`SessionLedger` append-only 用量端口（seq 由 record 点分配、单调连续）+ `LedgerEntry`；**消耗从 ledger 派生，预算上限属 Metering/Policy 配置面**（第七轮正典不变）。`Metering` 端口同刀定形（`withinBudget`——供数 + 与 Policy 协作拦截，自身不产生裁决）。read-your-writes barrier 超时语义仍挂账（随持久化实现）。
  6. **Priority day-one 入签名**：`InboxMessage` 带 `NOW/NEXT/LATER`，领取序 NOW > NEXT > LATER、同级 FIFO；NOW 级抢占的**执行**语义属 ③ Loop（只切流式 chunk 边界，第四轮），内核只保证领取顺序。
  7. **`SessionRegistry` → `SessionStore` 改名**，对齐底板 §3.1 与第八轮内核必需端口四件套之名（SessionStore / InboxClaim / Policy·ApprovalStore / Metering）。
  8. **总线五道闸定序**：取消 → 卫兵 before → Policy（含同步重试式审批）→ handler → 卫兵 after；conformance 钉死「卫兵中断时 Policy 不被调用」。`GuardHook` verdict 载体（deny>ask>allow 组合代数）与 syscall 注册表确定性规范序**本刀未触及**，仍挂账；registers/RegisterStore 与 SessionLoop 两挂账裁决依赖 ③ Loop 选型，随 ③ 同裁。
- **Decision — gnex3 对账 + LlmProvider 选型 + 断言形态升格（2026-08-18 第十轮，对账轮——纯文档，落码随下刀）**:
  > 参照 `docs/archive/reference/gnex3-reference.md`（80 份 SDD / 8337 行全量探查；2026-08-17 单日产出的平行设计语料：gnex2 九服务 + dsh 组合语义 + 自研 cordis-jvm 插件内核，零实现）。定位 = **影子时间线**：吃同一批 dsh 输入，在每个 tepeu 说「不」的地方走了「是」——规范先行 8337:0、生态自研、67 插件接口森林、model 层接受 Spring AI 黑盒。姿态由用户指令钉死：**只吸取有利**。本轮纯文档（用户指令「只写文档不写代码」），三裁决的落码切片统一指认 `llm.*` 断言切片。
  1. **LlmProvider = 自研双协议族**：Anthropic Messages + OpenAI Chat Completions 两族投影自研（canonical→wire 版本化纯函数投影），传输层后续薄壳（JDK HttpClient 或官方 SDK，一族一刀后续裁）；**Spring AI ChatModel 不进 `llm.*` 路径**——legacy 实测其 wire 转换/工具循环/schema 生成全黑盒（零 preparedRequest 可观测、无 cache_control、工具序=bean 序），红线 §6-6 逐字节断言在其上结构性不可验证；legacy 4 provider 实测塌缩为两族（DeepSeek 走 Anthropic 兼容端点、Ollama 走 OpenAI 族）；gnex3 model 层（Spring AI 单族、wire 零可观测）为反面标本。缝可逆（换 LlmTransport 实现不动契约）；Spring AI 仅工具/MCP 面不受此裁，随 Tool 契约挂账（O5）另裁。
  2. **断言形态升格为「派生式」**：`llm.*` syscall **不接收**调用方拼装的 messages——messages 恒由 `derive(log)` 派生（对 7 类事件词汇表全定义），args 只携带 config（model/system/sampling/tools）。红线 §6-1（禁旁路拼消息）与 §6-6（`derive(log) ∘ normalize == sent`）由「两条规矩」合一为「一个机制」：非日志可派生者**结构性发不出去**。断言从「入口拦截比对」升格为「结构保证 + digest 对账」：每请求 prepared digest 与 derive/normalize/project 版本号落 ledger 条目 attrs；下次调用前复核上笔（replay verify，失配=失败可见，传输零调用）。断言 v1 范围：messages（派生保证）+ tools 序（须为总线规范序子列）+ cache 布点确定性（anthropic 投影内单断点、逐次稳定）；config 独立来源（system prompt 独立校验）随 PromptAssembly 快照事件 / registers 落位后再收。normalize 细目沿第四轮 B1：版本化纯函数，provider 合法变形（连续 user 合并、media 上限剥离、cache_control 布点、toolCallId 适配）只准发生在其中。
  3. **gnex3 吸收 8 条 / 反模式不吸收 7 条**（证据与落法见参照文档 §2/§3）：吸收 = 重试协议（可重试封闭集+requestId 幂等+熔断开路禁重试）、tool/call 先落日志再执行、registers 终态单调、TIMED_OUT 一等结果、DoomLoop guard 形状（指纹归一化+NUDGE 模型可见）、usage 只增禁负禁篡改（**写失败语义 tepeu 倾向 fail-closed，与 gnex3 降级放行相反**——预算门供数源不可丢账，其形态记备选）、注册表/工具集版本化快照锁存、修剪证物保护+spill 容量纪律。不吸收 = cordis-jvm 自研内核、67 插件×三角色接口森林、冷装插件仪式、开放事件词汇表、审批无单次消费、日志背压可 DROP、Spring AI 黑盒 model 层。§8.5 新增 4 行挂账、3 行并入备注、LlmProvider 行改 ◐ 已裁决。
  4. **落码切片指认（C3 纪律）**：`llm.*` 断言切片 = 新模块 `os/llm`（canonical 类型 / LogDeriver / SharedNormalizer / 双协议族投影 / CanonicalJson+digest / LlmGenerateHandler / LlmTransport / LlmConformance 套件）+ kernel 四处小改（`Usage` 增 cost 槽、`LedgerEntry` 增 attrs、`SessionLedger.record` attrs 重载、`CapabilityBus.registeredSyscalls()` 确定性规范序——顺销 drift 行）。fake 传输先行（离线全绿），真 HTTP 往返一族一刀后续裁。
- **Decision — 一切皆组件，不是一切皆插件（2026-08-22 第十一轮，结构切片）**:
  > 用户裁决：gnex-core-v3 的调试优势是「一件事一个模块」，不是插件仪式。洋葱分层仍成立，但是**依赖规则**，不是 `kernel`+`adaptors` 两个大包。tepeu 不引入 Plugin/Ctx/热插/三角色（第十轮不吸收条款不变）。
  1. **组件 = Maven 模块 = 一件事**（「事」= 可单独拥有/测，不是一个类型）。严苛口径：能叫组件的是 `session` / `policy` / `bus` / `compose`；`identity`/`syscall` 是词汇，`conformance` 是 harness。同日收回 `context`/`usage`/`metering` 三个类型 jar。默认实现跟组件走（`session.memory`、`bus.memory`、`policy.memory`），废除 `adaptors/` 垃圾桶。空 README 可留，空 jar 不预开。同日稍后 `os/llm` 落码，组件变为 5 个。
  2. **协作边界 = 模块边界**：改 Inbox 只编 session；改审批只编 policy；`mvn -pl session test` / `-pl bus test` 不拉全世界。compose 只接线（`MemoryAssembly`），不偷偷 ALLOW Policy。
  3. **内核三件仍是概念**，不是三个 jar：① = identity + session + bus（policy/syscall/guard 是门上的锁与信封，独立组件以免循环依赖与抢文件）。会话仍是一件（三 store 暂不拆模块）。
  4. **下一刀 `llm.*` 从第一天起就是 `os/llm` 独立组件**，不倒回任何大包。第十轮「kernel 四处小改」落点改为对应组件（`usage` / `session` ledger / `bus` 注册表序）。
- **Decision — ③ Loop 答复路径（2026-08-22 第十二轮，落码切片）**:
  1. **选型 = 阻塞式 + 显式门**（销 SessionLoop 串行域挂账）：`SessionLoop.run` 同步执行；`loop.state` 进 `SessionRegisters`（idle|running|maintenance）；running/maintenance 中再 run = INVALID。非事件驱动，maintenance 本刀不进。
  2. **完成 = 证据门**：`CompletionGate` 无新事件类型。REPLY 须非空 `ASSISTANT_MESSAGE`；TOOL_PAIR 须成对。SSE / idle / Todo ≠ completed。空白模型输出 = INCOMPLETE。
  3. **entries 由 ③ 写**：总线不自动落 TOOL_CALL/TOOL_RESULT（销该挂账）。本刀主路写 USER + ASSISTANT；工具执行循环后续。拦截异常 catch 在 Loop，不写 ASSISTANT、不 completed（D6）。
  4. **有界**：`maxSteps < 1` 在 claim 前 STOPPED。无工具则一步 generate。Loop **不** import 具体 Tool 类；syscall 名 `llm.generate` 字符串。
  5. **registers 薄端口挂 session**，不另开 jar。快照锁存 / DoomLoop / PromptAssembly / Command 仍挂账。
- **Decision — ③ Loop 工具循环（2026-08-22 第十三轮，落码切片）**:
  1. **一步 = 一次 `llm.generate`**，可附带至多一次工具。模型输出首行匹配 `syscall <name>`（Loop 步进语言，**不是**第三份 LLM 投影）则走工具；否则当答复写 `ASSISTANT_MESSAGE`。真 HTTP 落码后再把 tool_use 块映射到此语言。
  2. **先落日志再执行**（兑现 gnex3 §2.2，归属第十二轮已裁给 ③）：`TOOL_CALL` 入 entries 后才 `bus.invoke`。拦截类异常（卫兵/Policy/审批）catch 后**合成** `TOOL_RESULT`（`ok=false` + errorCode）再 FAILED——禁止孤儿 CALL。执行类 `ok=false`（含未注册 `NOT_FOUND`）写 RESULT 后继续 generate。
  3. **不 import 具体 Tool 类**。禁止把 `llm.generate` 当工具再 invoke（STRUCTURAL + 合成 RESULT + FAILED）。工具名即总线 syscall 名。
  4. **完成仍过 REPLY 门**。成功路径：USER + (CALL/RESULT)* + 非空 ASSISTANT。仅工具打满 `maxSteps` = STOPPED，不得 completed。DoomLoop / TIMED_OUT / 一块多 tool_use / `execution.*` 沙箱仍挂账。
- **Decision — Anthropic HTTP 薄壳（2026-08-22 第十四轮，落码切片）**:
  1. **JDK HttpClient**，不引入官方 SDK、不引入 Spring AI。`AnthropicHttpTransport` POST `{base}/v1/messages`；body = `prepared.wireJson`（投影 v2 必带 `max_tokens`，默认 1024，入 ledger attrs 供复核）。密钥只读 `ANTHROPIC_API_KEY`（可选 `ANTHROPIC_BASE_URL`）；compose 默认仍 fake，调用方注入传输。
  2. **缝可观测**：请求头 `anthropic-version=2023-06-01` + `anthropic-beta=prompt-caching-2024-07-31`（投影仍写 cache_control）。HTTP 非 2xx / 非 JSON → `TRANSPORT`，不落 ledger。只拼接 `content[].type=text`；tool_use 映射仍挂账。cost 空 = n/a。
  3. **本刀不包含**：OpenAI HTTP、重试协议、流式、live key 往返。离线 stub 证伪「发出的 body ≡ prepare」。
- **Decision — OpenAI HTTP 薄壳（2026-08-23 第十五轮，落码切片）**:
  1. **同一 JDK HttpClient 缝**，不引入官方 SDK / Spring AI。`OpenAiHttpTransport` POST `{base}/v1/chat/completions`；body = `prepared.wireJson`（投影 v1，无 cache_control）。密钥只读 `OPENAI_API_KEY`（可选 `OPENAI_BASE_URL`，默认 `https://api.openai.com`）；`Authorization: Bearer`。compose 默认仍 fake，调用方注入传输。Anthropic 族拒绝（CONFIG）。
  2. **用量拆轨**：OpenAI `prompt_tokens` **含**缓存；`Usage.inputTokens` 是非缓存输入 → `input = prompt_tokens - prompt_tokens_details.cached_tokens`，`cacheRead = cached_tokens`，`cacheWrite = 0`（无对等字段）。只读 `choices[0].message.content`（string 或 `type=text` 数组）；tool_calls 映射仍挂账。HTTP 非 2xx / 非 JSON → `TRANSPORT`，不落 ledger。cost 空 = n/a。
  3. **本刀不包含**：重试协议、流式、live key 往返、第三份投影。离线 stub 证伪「发出的 body ≡ prepare」。
- **Decision — 预算硬门进往返（2026-08-23 第十六轮，落码切片）**:
  1. **Metering 只供数**：`LedgerMetering` 从会话 ledger 派生 `usage.totalTokens()` 累计，和配置的 token 硬顶比较。无顶 = 不限；顶 0 = 零预算；**累计须严格小于顶**才 `withinBudget`。cost 空不参与（禁止假装有价）。Metering 不抛拒绝。
  2. **开 turn 前拦**：`SessionLoop` 在 `maxSteps` 之后、claim 之前读 `withinBudget`；false → `STOPPED`/`BUDGET`，不 claim、不写 USER、不调 `llm.generate`，Inbox 消息仍在。Metering 抛异常 → fail-closed `FAILED`。默认 `LedgerMetering.unlimited()`，compose 不偷设顶。
  3. **本刀不包含**：逐消息费用检查（第七轮单机裁决：开 turn 前 + provider `max_tokens` 兜底已在 Anthropic 投影）；人手旁路打总线不经 Loop，本刀不在 Policy/`llm.*` 入口再拦；默认规则矩阵 / 审批同 turn 回放仍挂账。
- **Decision — Loop maintenance 窗 + DoomLoop（2026-08-23 第十七轮，落码切片）**:
  1. **maintenance 独占 idle 窗口**：`SessionLoop.maintain`；`loop.state=MAINTENANCE`；running/maintenance 中再 `run`/`maintain` = INVALID。不 claim。强制上限（`MaintenanceConfig.maxWindow`，可注入时钟）；上限到点 → `STOPPED`/`MAINTENANCE_LIMIT`。开窗前若已有可领 NOW → 不开窗（`MAINTENANCE_NOW`）；窗内到达 NOW → 让位，消息仍在 Inbox。latch = 开窗时 `inbox.enqueued()` 写入 `loop.latch`（内存 Inbox 即 claim 队列，其后 now/next 无需另重放）。窗口预算独立（`windowMetering`，默认 unlimited）。干净结束 → `STOPPED`/`MAINTENANCE_DONE`（**不是**答复 COMPLETED）。主动压缩作业仍属 Compaction 缝，本刀只提供窗口。
  2. **DoomLoop**：同工具同输入连续 **3** 次（OpenCode 阈值）→ 第三刀不 `invoke`；合成 `TOOL_RESULT`（`errorCode=DOOM_LOOP`，body=NUDGE，模型可见）。指纹剥 `timestamp/ts/time/random/nonce/uuid/requestId/request_id` 键。不新开事件类型。NEED_APPROVAL 并入总线审批通道仍待 GuardHook verdict 载体（本刀 = 停 turn 等人）。
  3. **Inbox 水位**：`enqueued()` 单调；`hasClaimableNow()` 只认可领取 NOW。
- **Decision — PromptAssembly + CommandDispatcher（2026-08-23 第十八轮，落码切片）**:
  1. **orchestration 开成组件 jar**（不是第三份投影；Loop **不**依赖本模块）。`PromptAssembly` 按有序 Section 组装：STATIC → `system`；DYNAMIC → `dynamicBodies`（调用方作 user-role 快照，不混进 system）。超预算：STATIC 保前缀；DYNAMIC 从后往前（最具体优先），装不下则截最具体、更宽泛 DROP。weight = 字符数（不是 tokenizer）。收据 = `includedIds` + `bill`。不新开 `prompt_assembly` 事件（7 类词汇表钉死）。`skillCatalog` 仅 name/description/digest；正文另注册。无记忆平面则不得捏造 `memory_hits`。
  2. **CommandDispatcher** 只见 local/prompt。解析行首 `/name args`；未知命令失败、不进 Inbox、不经模型。prompt 型 `inbox.enqueue(expansion, source=command:<name>)`，不调用 Loop。内置 `/help`（local）。local-jsx 仍属 ⑤。
  3. **Loop 仍只转发 `LoopConfig.system`**。红线「须经 PromptAssembly」本刀 = 调用方/compose 纪律，不是 Loop 编译期依赖。compose 接线空 PromptAssembly + 已注册 Help 的 CommandDispatcher。AuditSink / Slash 宿主副作用仍缺。技能正文懒加载 = 调用时再 register 段；本刀不碰文件系统。
- **Decision — 内核契约收口（2026-08-23 第十九轮，落码切片）**:
  1. **fork**：`SessionStore.fork(source, atSeq)` 复制 `seq<=atSeq` 的审计事件（保留原 seq）与 surface 记账，写入 `END_SEED` 后续接同一单调空间。`replaceRange` 不得 `fromSeq <= seedEndSeq`。Inbox/ledger 不随种子。`loop.state` 不带 RUNNING 过叉。词汇表钉 8 类；`END_SEED` 只在审计日志、不进 surface / `derive`。
  2. **卫兵 verdict**：`GuardHook.before` 返回 `PolicyVerdict`；聚合 deny > ask > allow。DENY/`BusGuardException` 仍中断且不调 Policy。ASK 与 Policy ASK 共用 `ApprovalStore`。卫兵 ALLOW 压不过 Policy DENY。
  3. **AuditSink** 端口 + 内存实现；不进 entries。compose 接线。总线不自动落账（人手旁路由调用方写）。
  4. **崩溃补闭合**：`Session.recover()` 为未配对 `TOOL_CALL` 补 `TOOL_RESULT{errorCode=INTERRUPTED}`，不截断；`loop.state` 若存在且非 IDLE 则点查写回 IDLE。内核仍不知 turn，不新开 `turn/end` 类型。
  5. **persist-before-event**：`ContentStore`（sha256 CAS）；事件只放 locator。
  6. **本刀不包含**：SQLite schema（② 插头，规范默认仍未写）；多副本 fencing；timer 轮；哈希链 tamper-evidence；默认规则矩阵。
- **Decision — 本机单写者内核可发行（2026-08-23 第二十轮，落码切片）**:
  1. **SessionStore 发行插头** = SQLite WAL schema v1（事件列 `type_version` 默认 1；`meta.schema_version=1`，不匹配 fail-closed）。单 JDBC 连接 + 互斥；`busy_timeout=5000`、`synchronous=FULL`。过同一套 `SessionConformance` / `forkSuite`。进程重启后事件、fork 种子区、`recover()`、blobs、AuditSink 仍在。依赖锁定 `org.xerial:sqlite-jdbc:3.53.2.1`。
  2. **ApprovalStore 发行插头** = 独立 `approvals.sqlite`（policy 不共享 session 内部表）。过 `ApprovalConformance`；C1 语义不变（未决 ask 幂等、decide 一次、consume 严格单次）。
  3. **compose 发行默认** = `SqliteAssembly.file(dir)` → `dir/kernel.sqlite` + `dir/approvals.sqlite`。`MemoryAssembly` 仅 conformance / 单测。发行路径不得默认 `InMemoryApprovalStore`。`Wired` 实现 `AutoCloseable`。
  4. **ledger**：同连接 `record` 后 `readAll` 可见；store close 后再写抛 `SqliteStoreException`（fail-closed）。多副本 fencing / barrier 超时仍挂账。
  5. **本刀不包含**：多副本 fencing、timer 轮、哈希链、默认规则矩阵、live key 往返、`execution.*` 沙箱、Compaction 作业。口径 = **本机单写者内核可发行**；仍不得称 Agent OS / 骨架可演示 / 合规删除权。
- **Decision — Compaction 挂窗 + 默认规则矩阵 + execution 囚笼（2026-08-23 第二十一轮，落码切片）**:
  1. **主动压缩** = `CompactionWork` 挂 `SessionLoop.maintain`。经总线 `llm.generate`（占窗口 Metering），写回 `replaceRange`。不删审计事件；`seq > seedEnd` 才压；surface 条数 ≤ keepLast 或待压前缀全是 checkpoint → 停。触发式 turn 内压缩本刀不进。摘要质量不在本刀（fake 输出即可证伪代数）。
  2. **默认规则矩阵** = `DefaultRuleMatrix`：`llm.*` / `execution.fs.read` / `execution.sandbox.probe` → ALLOW；`execution.fs.write` / `execution.proc.*` → NEED_APPROVAL；其余 DENY。compose 装配此矩阵（不是 ALLOW-all）。同 turn 回放 = 已有 C1：decide 后重试 consume 一次。人手旁路打 compose 总线仍过矩阵。
  3. **execution 组件**（第 8 个）：工作区路径囚笼；`probe` 报 `isolation=partial`。`proc.spawn` 无 OS jail → `SANDBOX_UNAVAILABLE`，禁止静默直通。Policy=授权，Sandbox=隔离：批准 spawn 仍因无 jail 失败。
  4. **本刀不包含**：live key、turn 内 overflow 压缩、bwrap/Job Object、PLAN_STEP 完成门、DoomLoop 第三刀改 NEED_APPROVAL。仍不得称骨架可演示。
- **Decision — 五层骨架可演示（2026-08-23 第二十二轮，落码切片）**:
  1. **进模诚实**：`LlmTransports.fromEnv()` 供调用方注入。compose / `SqliteAssembly` **不**读密钥，默认仍 fake。`LiveHttpTransportTest` 有 `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` 才烧；CI 无 key skip。cost 空 = n/a。
  2. **控制循环**：`LoopConfig.compactOverflow`（默认 40）在每次 `llm.generate` 前若 live surface 超阈则 `CompactionWork` 一步。递减停机仍是 keepLast / 前缀全 checkpoint，不是模型质量。
  3. **完成权**：主路推断声称：永远 REPLY；有工具则 TOOL_PAIR；有 `PLAN_STEP` 则 PLAN；`TOOL_RESULT.locator` 则 FILE（64-hex 且 `ContentStore` 可取）。`plan ...` 与 `syscall ...` 并列。成功 `execution.fs.write` persist-before-event 写 locator。
  4. **执行缝**：Windows Job Object（JNA 5.19.1，`KILL_ON_JOB_CLOSE`）/ Linux `bwrap`（PATH 上才有）。spawn 只接受工作区相对 `path`。无 jail → `SANDBOX_UNAVAILABLE`。`probe` 报 `isolation=partial`（无 landlock / 无受限令牌，**禁止报 FULL**）+ `mechanism=job-object|bwrap|workspace-root` + `proc=available|unavailable`。
  5. **审批做真**：`DefaultRuleMatrix.parse` 精确名覆盖；`SqliteAssembly` 读 `dir/policy.rules`。`/approve <id> allow|deny` 走 CommandDispatcher，写 AuditSink，不进会话事件。
  6. **口径** = **本机 Agent OS 骨架可演示**。仍不得称企业 Agent OS / 完整 OS / GDPR 删除权。无 UI、无记忆平面、无多副本 fencing。
- **Decision — 审查修补（2026-08-23 第二十三轮）**:
  1. **compact × digest**：`replaceRange` 递增 `log.surfaceEpoch`。`LlmGenerateHandler` 把世代写入 ledger；`verifyPrevious` 在世代失配时跳过上笔 digest（压缩改写了 surface，不得 ASSERTION）。过真实 handler 的 compact→generate 用例。
  2. **Job Object**：`CreateProcessW` + `CREATE_SUSPENDED`，入 job 且 `IsProcessInJob` 后 `ResumeThread`。禁止 start 后再 assign 的竞态。
  3. **spawn 卫生**：环境白名单（剥离 `*API_KEY*` / `*SECRET*` 等）；stdout 按 8192 封顶读；`proc-*.out` 用后删；失败路径一律 `destroy`/`TerminateProcess`。bwrap `--clearenv` + `--ro-bind-try` `/usr` `/bin` `/lib` `/lib64`。
  4. **审批绑 args**：`ArgDigest`（排序 `k=v` sha256）；ask 幂等与 consume 匹配 `(session, name, argsDigest)`。SQLite `args_digest`（旧库 ALTER，空 Map 摘要作默认）。`/approve` 必须 `record.sessionId == ctx.sessionId`。
  5. **WorkspaceJail**：拒绝绝对路径；`NOFOLLOW_LINKS`；符号链接 / junction 一律拒；未存在文件看父路径 realpath 仍在根下。
  6. **隔离仍是 partial**。Job Object 无 FS 限额；禁止报 FULL。
- **Forward**: 实施以 `docs/os-baseplate.md` + `os/` 为准。下一动作按痛点：⑤ UI、记忆平面、多副本 fencing / timer 轮 / 哈希链；DoomLoop 第三刀改 NEED_APPROVAL 仍挂账。

