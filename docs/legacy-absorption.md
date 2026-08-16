# v1.0（legacy/）吸收清单

> **地位**：切片规划输入，非对账轮（第七轮冻结的是外部参照→新裁决轮；本文不开任何裁决，需裁决项一律走 §8.5 挂账）。
> **红线**：ADR-016 Forward——本清单**不自动授权**从 legacy 搬代码；动手前按黄灯确认切片。legacy/ 只读。
> **来源**：2026-08-16 结构清点（124 Java + 47 前端 + 49 测试）+ 本会话已载的 DEV-PLAN Phase 1–18 / project-memory / handoff。

---

## A. 行为规格吸收（v1 生产验证过、os/ 直接继承的「已验证决定」）

| # | v1 行为 | 证据 | 落到 os/ 哪 | 方式 |
|---|---------|------|------------|------|
| A1 | **审批矩阵**：shell/mcp/file_delete 需批；file_write/shell_output/file_rest 免批；灾难 shell 直接 DENY | `DangerousToolHook` + 测试矩阵 | Policy 配置面的**第一版默认规则集**（事前声明式 seed） | 规则迁移 |
| A2 | **写不变量两条**：写父目录须存在；回合末扫描「已写入」声称路径 | `HallucinationGuard` | ② 卫兵「不变量」类的首个实现 | 改造 |
| A3 | **人手旁路与 Agent 工具同一 Hook**（REST 删/传/恢复 + 终端 WS） | `HostChannelGuard` | 底板 §2「门对称」的 v1 实证——设计印证，无须搬 | 印证 |
| A4 | 幂等键（前端 idempotencyKey + 服务端去重） | `IdempotencyService` | Inbox 消息/请求级幂等 id（与 OpenCode 重放幂等同族） | 契约吸收 |
| A5 | 未知模型回退估价 + 分层计价 | `TokenCostEstimator` | Metering 兜底策略 | 参考 |
| A6 | 预算阈值告警 + 可选硬门禁（超限阻断、中文文案、零预算语义） | `BudgetService` | Metering 预算门行为规格（第七轮已定门位） | 行为规格 |
| A7 | 自主调度：RUNNING 卡死超时恢复、空回复 EMPTY、费用入账 | `ScheduleService` | LongTaskAdaptor 首版行为规格（预约-复核是底板新增要求） | 行为规格 |
| A8 | 草稿凭证测连（表单未保存也能 test）；Ollama 无密钥；testConnection 放 ChatService 避循环依赖（ADR-007） | `LlmProviderService`/`ChatService` | LlmProvider 缝行为细节 | 行为规格 |
| A9 | FTS5 检索失败 LIKE 回退；记忆文件镜像 | `MemoryService`/`MemoryFileMirror` | KnowledgeSource 候选实现的容错行为 | 参考 |
| A10 | 文件事件 250ms 窗口合并（同 path 保留最显著 op：delete>create>modify）；多 tab leader 选举单连接 | `FileWatcherService` + 前端 `sharedFileEvents` | ProjectionBus/文件事件适配器的行为规格 | 行为规格 |
| A11 | 通知通道分离：文件事件与任务通知两条独立常驻 SSE（ADR-013）；死连接摘除 | `FileEventsController`/`TaskEventNotifier` | ProjectionBus 通道划分参考 | 参考 |
| A12 | 本机实例令牌 `X-Tepeu-Token`（localhost 可拉取，保护审批/危险 API） | `security/InstanceToken*` | ⑤ 应用层 REST 面整体迁移候选 | 搬码（⑤，黄灯） |
| A13 | 工具过程持久化（`TEPEU_TOOL_V1` system 行）+ 刷新回放 | `ToolTraceCodec` | entries 类型化事件（TOOL_CALL/RESULT）的 v1 前身——印证，os/ 已更干净 | 印证 |
| A14 | toolKind 分类学（file_list/read/write/search/delete、shell、shell_output、mcp、file_rest、other） | `ToolKinds` | **syscall 注册元数据带 kind 轴**（供 Policy 规则、UI 分组、成本归集共用） | 契约吸收 |
| A15 | 长输出分页续读（RunCommand→ReadOutput 配对，会话隔离） | `CommandOutputStore` | Execution 缝 shell 语义 + §9 spill/retrievalHint 的 v1 雏形 | 改造 |
| A16 | workspace 文件隔离（root_path 解析 + 历史回填） | `WorkspacePathResolver` | Execution 的 workspaceRoot 语义（v1 踩过的回填坑直接继承） | 改造 |

## B. 代码资产迁移候选（按缝，全部待黄灯切片）

| 缝 | v1 资产 | 迁移方式 | 备注 |
|----|---------|----------|------|
| Execution/Sandbox | **`ScriptSandbox` + `WorkspaceScriptFs`（GraalJS，ADR-015）** | **最直接可搬**：超时 `Context.close(true)`、`Java.type` 拒绝、workspace 边界、穿越拒绝——已是「隔离完备性 partial 如实报告」形状 | 作为脚本域沙箱第一实现；OS 级（bwrap/JobObject）另立项 |
| Secret | `CryptoService`（AES-256-GCM、`enc:v1:`、master.key） | 改造：格式/密钥文件管理可留，**须按 Secret 四细则重做**（重解析禁缓存、解析结果不进模型通道） | 黄灯 |
| TeamAdaptor | `AgentRole(Prompts)` + `Goal` + `VerdictParser` | **preset 化迁移**：Planner→Implementer→Reviewer 收编为第一个 Team preset；Goal 薄契约照搬 | 结构不搬（见 C1） |
| CommandDispatcher | `/help` `/tasks` `/schedule` `/compact` `/status`（后端）+ `/clear` `/new` `/files`（前端 local） | 命令语义清单直接继承；`/compact` 在 os/ 对应 surface 全替换操作 | 首批命令清单 |
| Metering | `TokenCostEstimator` + `BudgetService` | 行为规格重写（A5/A6） | — |
| ⑤ 应用层 | 前端组件库（IdeShell 三栏/移动 767px 断点/审批条/通知铃/Slash 候选浮层/providerReady 就绪等待）、`SkillMarketplaceService`、marketplace catalog | 等 ⑤ 面契约定型后渐进迁移；**不在 os/ 重写阶段搬** | 47 ts/tsx + 市场资产 |
| 测试 | 49 个测试文件（Hook 矩阵、Sandbox 隔离、Marketplace、Schedule 恢复…） | **改造成 os/ conformance 用例输入**：Hook 矩阵→Policy conformance；Sandbox 隔离测试→Execution conformance；Schedule 恢复→LongTask conformance | 弹药，非搬运 |

## C. 不吸收（反模式 / 已被裁决否决）

1. **上帝编排器结构**——`AGENT_CALL_PATH.md` 自认：新增一个工具要改 `Tools.java` 注册 + `ToolKinds` + `AgentOrchestrator` bind/unbind 配对（×多 Agent 再来一遍）。os/ 里加工具 = 总线注册一个 syscall + 过 conformance。这是 ADR-016 的起因本身。
2. **装饰器链**（`HookingToolCallback`/`ToolEventEmittingCallback`/`RenamingToolCallback`，ADR-007 deprecated）——os/ 用总线入口卫兵与事件取代。
3. **内存态 ApprovalStore**——已裁 SQLite 禁内存。
4. **工具轨迹混进 system 行**——os/ 类型化事件取代。
5. `Tools.java` 静态注册表、EventLoop（已退役 ADR-003）。

## D-2. 与 spring-ai-agent-utils 的关系（选型定位，2026-08-16）

**不用作原语层**：它是工具箱不是运行时（无 bus/Policy/journal/Inbox/Loop）；`@Tool` 直挂 ChatClient 的形态绕过总线=绕过门（违背身份陈述）；垫在 ③ 下当底座 = v1 装饰器链老路换库重演（ADR-016 起因）。其 subagent SPI 无 delegationDepth/父集减法/取消拓扑，照搬违反第四/五轮裁决。

**定位 = ② 工具插头的候选零件库，legacy 优先、utils 补缺**：Grep/Glob 纯 Java 实现、WebFetch/WebSearch（legacy 均无）值得抄；A2A 模块为远期多副本 SubagentAdaptor 后端参考；AutoMemory/Skills/TodoWrite 属 ⑤ 域 os/ 阶段不碰。引入纪律：包进 Tool 插头过门 + SandboxPolicy + toolKind 元数据 + conformance；外部依赖黄灯；须锁精确发布版（本地 0.11.0-SNAPSHOT 不可直接用）。

## D. 对切片的输入（与挂账的接口）

- **② conformance 切片**：B-测试行的三个改造方向即用例来源（Policy 矩阵 / Sandbox 隔离 / Schedule 恢复）。
- **`llm.*` 断言切片**：A8（测连/无密钥/Ollama 语义）为 LlmProvider 行为规格。
- **③ Loop/Team 切片**：B-TeamAdaptor preset 迁移是 Teams 第一刀；A7 为 LongTask 行为规格。
- **⑤ 面定型后**：A12 + B-⑤ 整体迁移窗口。
- 需要裁决的项（如 syscall kind 元数据 A14 若要进内核契约）→ 走底板 §8.5 挂账，不开新轮。
