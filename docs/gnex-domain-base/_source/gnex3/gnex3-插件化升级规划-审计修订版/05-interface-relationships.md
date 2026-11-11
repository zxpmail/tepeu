# GNEX3.0 接口关系：服务目录 · 事件总线 · 影响域

> **状态**：审计修订版 v0.2（2026-08-18，全量接纳审计/06 意见）· 关联：04 插件目录（81 插件）· 03 融合设计（修订版）· 审计/02
> **原则**：接口关系对齐 dsh 规制；跨服务边界走既有 REST/Bus/RPC。
> **修订版新增 §0 跨进程解析规则**（D-P1-34）：插件服务解析仅限宿主服务内 Ctx；跨服务一律 Port/REST/Bus（gnex-contracts 端口接口），禁止 Ctx 跨进程注入。

---

## 1. 核心服务目录（provide / inject）

> 服务 = 插件在 Ctx 上注册的具名服务（类比 dsh ctx 键）。以下为核心服务（约 40 个），全部由 L1/L3/L4 插件提供，被能力插件注入。

| 服务名（ctx 键） | 提供插件 | 主要注入者 | 说明 |
|-----------------|----------|-----------|------|
| `ctx`（容器本身） | gnex3-l0-kernel | 全部 | 服务解析/隔离/拦截（root 由 kernel 创建，语义实现由 l1-ctx） |
| `events` | gnex3-l1-events | 全部 | waterfall/emit |
| `fiber` | gnex3-l1-fiber | 全部 | 生命周期/effect |
| `scope` | gnex3-l1-scope | 会话/工具插件 | 租户/项目/会话 realm |
| `settings` | gnex3-l1-settings | 模型/工具/策略 | 插件配置段 |
| `logger` | gnex3-l1-logger | 全部 | 命名日志 |
| `invariants` | gnex3-l1-invariants | 核心循环 | 运行时不变式 |
| `prompt` | gnex3-l1-prompt | agentLoop | 系统提示组装 |
| `llm` | gnex3-cap-model-llm | agentLoop/标题/压缩/web | 模型路由 |
| `agentDefaultModel` | gnex3-cap-model-default | 入口 | 默认模型 |
| `sessions` | gnex3-cap-session-store | agentLoop/查询/投影/UI | 会话日志 |
| `sessionPersistence` | gnex3-cap-session-persist | agentLoop/查询 | PG 持久化 |
| `sessionQuery` | gnex3-cap-session-query | 引用/UI/导出 | 查询/搜索 |
| `sessionProjection` | gnex3-cap-session-projection | UI/统计/标题 | 投影单元 |
| `sessionTitle` | gnex3-cap-session-title | UI | 标题 |
| `agents` | gnex3-cap-agent-registry | agentLoop/委派/UI | Agent 注册表 |
| `agentLoop` | gnex3-cap-agent-loop | 入口/工厂 | 循环驱动 |
| `tools` | gnex3-cap-tools | agentLoop/工具插件 | 工具注册表+管线 |
| `subagents` | gnex3-cap-subagents | 工具/委派 | 子代理注册表 |
| `workflowEngine` | gnex3-cap-workflow | tool-workflow | 工作流 |
| `goals` | gnex3-cap-goal | tool-goal/命令 | 目标领域 |
| `skills` | gnex3-cap-skills | tool-skill | 技能注册表 |
| `approval` | gnex3-cap-approval 🔒 | 工具管线/UI | 审批原语 |
| `permission` | gnex3-cap-permission 🔒 | 工具管线/审计 | 权限预设 |
| `sandbox` | gnex3-cap-sandbox | shell/fs/code 工具 | 执行世界 |
| `sandboxPolicy` | gnex3-cap-sandbox-policy 🔒 | sandbox/shell | 策略 |
| `fs` | gnex3-cap-sandbox（fs 并入沙箱 seam） | 文件工具 | 文件系统 seam（Definition 在 libs/gnex-sandbox） |
| `shell` | gnex3-cap-sandbox（shell 后端） | bash/pwsh 工具 | 命令执行 |
| `storage` | gnex3-cap-storage | 领域插件 | 存储 hub |
| `credentials` | gnex3-cap-credentials 🔒 | 模型适配器/UI | 密钥引用 |
| `attachments` | gnex3-cap-attachment | UI/会话 | 产物存储 |
| `workspaceRegistry` | gnex3-cap-workspace | UI | 工作区 |
| `compaction` | gnex3-cap-compaction | agentLoop/命令 | 压缩 |
| `tokenMeter` | gnex3-cap-compaction | 压缩/UI/**model-gateway** | 运行计量（与 cost_events 落库分工，D-P1-22） |
| `spill` | gnex3-cap-spill | 工具管线 | 溢出存储 |
| `userQuestions` | gnex3-cap-tool-ask-user | UI | 用户提问 |
| `jobs` | gnex3-cap-tool-jobs | 工具/UI | 后台任务 |
| `web` | gnex3-cap-tool-web | tool-web | Web 能力 |
| `mcp` | gnex3-cap-tool-mcp | 工具注册表 | MCP 客户端（连接管理在 registry-service；管线消费在 agent-service，D-P1-10） |
| `tenant` | gnex3-ent-tenant 🔒 | 全部服务入口 | 租户边界 |
| `gate` | gnex3-ent-gate 🔒 | agentLoop/委派 | 完成证据门 |
| `memory` | gnex3-ent-memory 🔒 | agentLoop/召回 | 企业记忆 SoR |
| `statePlane` | gnex3-ent-stateplane | UI/恢复 | 状态面 |
| `audit` | gnex3-ent-audit 🔒 | 全部写路径 | 审计 |
| `observability` | gnex3-ent-observability | 全部 | 指标/链路 |
| `auth` | gnex3-ent-auth 🔒 | 网关/服务入口 | 认证授权 |
| `bus` | gnex3-ent-bus | 跨服务插件 | 消息总线 |
| `billing` | gnex3-ent-billing | ops/配额 | 计费 |
| `org` | gnex3-ent-org | 角色/成员 | 组织（审计 P1-40 回填） |
| `api` | gnex3-ent-api | 网关 | 跨服务 API（审计 P1-40 回填） |
| `ha` | gnex3-ent-ha | 编排 | 多副本/弹性（审计 P1-40 回填） |

**归属规则（对齐 dsh 双平面判据）**：
- 服务内跨会话读者（UI/查询/审计）→ **服务层（L2/L4）注册**，能力插件经 Ctx 解析；
- 会话自有状态（plan/compaction/terminals）→ **会话作用域 realm**；
- 注册表（tools/skills/goals/subagents/agents）→ **宿主服务层单例**，禁止会话级复制；
- 🔒 受保护服务只允许平台管理员管理，租户/Agent 无装卸权。

---

> **口径声明（审计整改）**：本表为**核心契约词汇子集**（平台级高频事件）；各插件 spec §3.3 可声明域专属事件（如 sandbox/*、fs/*、shell/*、compaction/*、domain/*、schedule/*、job/*、question/*、billing/*、cost/*、org/*、ha/*、statePlane/*、observability/*），spec 为全量权威源，新事件应回填本表。服务目录同理：org/api/ha 等服务由对应 L4 插件提供（见 04 目录），本表为核心面。

## 2. 事件总线词汇（对齐 dsh 域/动作命名）

> 进程内：cordis-jvm 事件总线（waterfall/emit + scope 过滤）。跨服务：经 bus 插件发布到 RocketMQ topic（`gnex.<域>.<动作>`）。

| 命名空间 | 关键事件 | 方向 | 语义 |
|---------|----------|------|------|
| `plugin/*` | plugin/installed · plugin/started · plugin/stopped · plugin/uninstalled | emit | 插件生命周期（可观测） |
| `session/*` | session/created · session/event · session/flush · session/disposed | emit | 会话日志事件（对齐 dsh） |
| `agent/*` | agent/created · agent/disposed · agent/pre-step · agent/request · agent/request-error · agent/turn-stopping · agent/status | emit/waterfall | Agent 生命周期（对齐 dsh） |
| `turn/*` | turn/start · turn/end | emit | 轮次 |
| `step/*` | step/start · step/end | emit | 步骤 |
| `tool/*` | tool/call · tool/result | emit | 工具调用（先入日志再执行） |
| `tools/*` | tools/pre-execute · tools/execute · tools/post-execute · tools/result | waterfall | 工具管线（对齐 dsh） |
| `llm/*` | llm/stream · llm/adapters-updated | waterfall/emit | 模型调用 |
| `approval/*` | approval/request · approval/decided · approval/queue | waterfall/emit | 审批原语（🔒） |
| `gate/*` | gate/check · gate/passed · gate/denied | emit | 完成证据门（🔒） |
| `memory/*` | memory/recall · memory/promote · memory/rollback | emit | 企业记忆（🔒） |
| `subagent/*` | subagent/start · subagent/end · subagent/status | emit | 委派（对齐 dsh） |
| `workflow/*` | workflow/run-start · workflow/run-end | emit | 工作流 |
| `goal/*` | goal/change | emit | 目标 |
| `skills/*` | skills/change | emit | 技能目录变更 |
| `command/*` | command/run · command/done | emit | 命令（对齐 dsh） |
| `credentials/*` | credentials/updated | emit | 密钥变更 |
| `audit/*` | audit/record | emit | 审计（🔒） |
| `bus/*` | bus/published · bus/consumed | emit | 跨服务消息 |
| `tenant/*` | tenant/isolated | emit | 租户过滤（🔒） |
| `domain/*` | domain/changed | emit | 存储领域 KV 变更（D-P1-35 回填） |
| `sandbox/*` | sandbox/mode · sandbox/ready · sandbox/destroyed · sandbox/error · sandbox/recovering | emit | 沙箱生命周期（回填） |
| `fs/*` | fs/read · fs/write · fs/edit | emit | 文件系统操作（回填） |
| `shell/*` | shell/exec | emit | 命令执行（回填） |
| `compaction/*` | compaction/triggered | emit | 压缩触发（回填） |
| `schedule/*` | schedule/change | emit | 定时调度（回填） |
| `job/*` | job/start · job/end · job/status | emit | 后台任务（与 command/* 解耦，D-P2-04） |
| `question/*` | question/requested · question/resolved | emit | 用户提问（回填） |
| `org/*` | org/change | emit | 组织变更（回填） |
| `ha/*` | ha/heartbeat · ha/failover | emit | 多副本（回填） |
| `billing/*` | billing/budget-alert | emit | 预算告警（回填） |
| `statePlane/*` | statePlane/restored | emit | 状态恢复（回填） |

---

## 3. 影响域矩阵（插件 → 作用范围）

| 层/插件组 | 作用范围 | 故障影响半径 | 升级影响 |
|-----------|----------|--------------|----------|
| L0 组合与装载 | 服务启动期 | 全服务启动失败 | 装配清单变更=服务重启 |
| L1 内核层 | 服务进程内 | 全服务能力不可用 | 内核升级=全服务回归 |
| L3.1 模型路由 | 会话级（每请求） | 该会话模型调用失败 | 适配器增删=无感 |
| L3.2 会话 | 会话级 | 会话历史不可用 | 持久化格式变更=迁移 |
| L3.5 工具管线 | 每工具调用 | 单次调用被拦截 | Inspector 增删=全工具行为 |
| L3.6 工具集 | 每工具调用 | 单工具不可用 | 工具装卸=无感（其余不动） |
| L3.7 委派 | 会话级 | 子任务失败 | 后端增删=无感 |
| L3.8 审批/权限 | 每高风险调用 | 高风险操作被拒/待办 | 🔒 平台级升级 |
| L3.9 沙箱 | 每次执行 | 执行失败（不影响主进程） | 提供方增删=无感 |
| L3.10 压缩 | 上下文组装 | 上下文超窗 | 策略变更=无感 |
| L4 企业层 | 全平台 | 影响面最大 | 🔒 平台级升级+灰度 |
| L5 客户端 | 浏览器 | 单页面不可用 | 前端发版 |

---

## 4. 关键管线（接口关系落地）

1. **工具执行管线**（审计修订版 v0.2 · 融合 dsh + Inspector 链，跨进程裁决已定案 D-P0-01）：
   `tools/pre-execute`（waterfall，**全程在 agent-service 进程内**）→ guard 序（**mandatory**：approval → permission → sandbox-policy(SandboxEnforcer) → path → risk → security；**optional**：loop 检测缺失=跳过+观测告警）→ `tools/execute`（wrapper 顺序：**timeout（最外）→ spill → 本体**；v3.0 无工具级自动重试）→ `tools/post-execute`（监听序：pruner → spill）→ `tools/result`（观测）
   - **跨进程裁决（P0-01 决策）**：sandbox-policy（裁决器）**宿主 agent-service**，作为管线 guard 进程内裁决（读会话档位/网络策略），批准后经 `SandboxPort.ensure`（REST /internal/sandbox/ensure）请求 sandbox-runtime 执行；sandbox-runtime 只做执行平面，不参与管线事件。
   - **guard 归属（P1-07 决策）**：SandboxEnforcer/Path/Risk/Security 为 gnex3-cap-tools **内置 guard 实现**（配置化启停、顺序固定）；approval/permission 为独立插件（mandatory）。
   - **统一写证据契约（P1-08 决策）**：`writeEvidence { paths: string[], hash: string, turnId: string, ts: number }`——工具成功时 emit `write-evidence`，gate 消费；tool-fs/tool-str-replace/tool-http 统一引用。
2. **Agent 轮次循环**：inbox → `agent/pre-step` → 组装（prompt 插件）→ `agent/request`（llm 路由）→ `llm/stream` → tool 调用 → `gate/check`（🔒 完成门）→ `turn/end`
3. **跨服务消息**：能力插件事件（scope=跨服务）→ bus 插件 → `gnex.<域>.<动作>` topic → 消费方插件
4. **企业记忆召回**：`agent/request` → memory 插件（🔒）→ JWT 硬过滤 → 温库候选 → 冷正文 → 注入 `<memory_context>`

---

*修订版 v0.2 完成 · 关联：审计/02 接口影响域专项 · 06 路线图（修订版）· specs（修订版）。**
