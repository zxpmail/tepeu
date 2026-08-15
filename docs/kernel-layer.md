# 分层架构图示（由内向外 · 如 OS）

> **地位**：本文是 **ADR-016 的投影/图示**，规范文本以 [`memory/decisions-log.md`](../memory/decisions-log.md) **ADR-016** 为准。若冲突，以 ADR 为准，改本文对齐。  
> **依赖**：外环依赖内环；内环不依赖外环。② 适配环 = 内核端口的**插头**，不是包住内核的外壳。

## 总览（由内 → 外）

```text
┌─────────────────────────────────────────────────────────────┐
│ ⑤ 应用 / 呈现                                                │
│    UI · SSE 投影 · Skill/记忆【资产文件】· 面板               │
│         │ 对话主路                    │ 人手 REST/终端       │
│         ▼                             ▼（向内：跳过④与 Loop）│
│  ┌───────────────────────────────────────────────────────┐  │
│  │ ④ 路由环（仅对话主路）                                  │  │
│  │    ThreadRouter · FlowRouter · ModelRouter(只选型)     │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │ ③ 编排环                                          │  │  │
│  │  │    兜底 Agent · Loop(turn/step)                   │  │  │
│  │  │    Team preset〔缝 TeamAdaptor〕                  │  │  │
│  │  │    Subagent〔缝 SubagentAdaptor〕                 │  │  │
│  │  │    LongTask〔缝 LongTaskAdaptor〕                 │  │  │
│  │  │    PromptAssembly · ReasoningPresenter            │  │  │
│  │  │    Command 分发 ← Slash 只进这里（不经模型/Loop）   │  │  │
│  │  │  ┌───────────────────────────────────────────┐  │  │  │
│  │  │  │ ② 适配环（驱动插头 · 可换）                  │  │  │  │
│  │  │  │  SessionStore · InboxClaim                 │  │  │  │
│  │  │  │  Execution · LLM Provider                  │  │  │  │
│  │  │  │  Tool 插头 · MCP Bridge                    │  │  │  │
│  │  │  │  Policy · ApprovalStore · AuditSink        │  │  │  │
│  │  │  │  Metering · KnowledgeSource                │  │  │  │
│  │  │  │  Identity · OrgNamespace · Secret          │  │  │  │
│  │  │  │  ProjectionBus（仅通知）                    │  │  │  │
│  │  │  │  ┌─────────────────────────────────────┐ │  │  │  │
│  │  │  │  │ ① 内核（冻住 · 仅三件）                │ │  │  │  │
│  │  │  │  │  · 主体 + 命名空间                    │ │  │  │  │
│  │  │  │  │  · 能力总线 syscall                   │ │  │  │  │
│  │  │  │  │      └ 入口调用〔Policy+卫兵〕        │ │  │  │  │
│  │  │  │  │        Policy 实现属②；此处只是钩点   │ │  │  │  │
│  │  │  │  │  · 会话：日志 + Inbox/claim           │ │  │  │  │
│  │  │  │  │        + 主/子关系                    │ │  │  │  │
│  │  │  │  │      └ 日志禁明文 secret              │ │  │  │  │
│  │  │  │  │      └ 维护：真压缩→经总线 llm.*      │ │  │  │  │
│  │  │  │  │        （同受卫兵 + Metering）         │ │  │  │  │
│  │  │  │  └─────────────────────────────────────┘ │  │  │  │
│  │  │  └───────────────────────────────────────────┘  │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
compose / app-boot：开机把插头插上（不单成环）
```

## 进内核的路（门对称）

两条路在碰能力总线时，**都过 Policy + 入口卫兵**（不是「卫兵只管 Agent、Policy 只管人」）。

```text
① 对话主路
   应用 → ④路由 → ③编排(Loop/Team…) → 内核 claim/syscall
         →〔Policy + 卫兵〕→ ②插头（Tool/Execution/LLM…）
         → 对话/工具事实写入【会话日志】

② 人手旁路（REST / 终端 · 不经 ④、不经 Loop）
   应用 → 内核能力总线 →〔Policy + 卫兵〕→ 同一套 ②插头
         → 【AuditSink】审计事件（不算会话对话事实）

③ Slash / 系统命令（裁决）
   应用 → ③ Command 分发（必经；不经模型、不经 Loop turn）
         → 若触发宿主动作：再走总线〔Policy + 卫兵〕→ ②
         → 审计走 AuditSink；不把 Slash 画进「人手旁路」混用
```

## 各环职责

| 环 | 放什么 | 不放什么 |
|----|--------|----------|
| **① 内核** | 主体+命名空间；能力总线；会话（日志+Inbox+主/子） | Tool 实现、MCP、UI、Turn 引擎 |
| **② 适配** | Store/Claim/Execution/LLM Provider/**Tool 插头**/MCP Bridge/Policy/Approval/AuditSink/Metering/Knowledge/Identity/OrgNamespace/Secret/ProjectionBus | 编排图、路由策略正文 |
| **③ 编排** | 兜底 Agent、Loop、Team/Subagent/LongTask（**积木+缝**）、PromptAssembly、ReasoningPresenter、Command | 磁盘/DB 驱动实现 |
| **④ 路由** | Thread / Flow / Model（选型）；Model 的 Provider 在 ② | 执行工具、拼长 Prompt |
| **⑤ 应用** | UI、Skill/记忆**资产**、面板 | 不当 syscall 实现 |

## 硬规矩（图上必须成立）

1. **对话/Agent 工具事实**：真相在会话日志；模型可见 ⟺ 日志可还原；禁止编排器旁路拼消息。  
2. **人手操作**：真相在 **AuditSink**（企业审计）；**不**冒充会话对话事实。  
3. **ProjectionBus** 禁止充当第二真相源。  
4. **工具之间禁止互引**；工具属 ② 插头，经总线调用。  
5. **底板 = 内核三件**（勿读成「最内三环」——② 可换砖）。  
6. **真压缩**经自家总线 `llm.*`，同受卫兵与 Metering（可耗用户 token，须计量）。
