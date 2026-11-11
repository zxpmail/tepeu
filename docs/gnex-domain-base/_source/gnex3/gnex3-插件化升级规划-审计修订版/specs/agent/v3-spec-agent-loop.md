# v3-spec-agent-loop

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.4（Agent 循环）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-agent-loop
> **分层**：L3 能力层
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

把 gnex2.0 agent-service `agent.runtime` 的 Orchestrator / AgentRouter / ReActLoop / SessionLoop 收敛为事件化循环服务 `agentLoop`：每轮 turn 由 inbox 触发，经 `agent/pre-step`（waterfall 可拦截可否决）→ prompt 组装 → `agent/request`（llm 路由）→ 工具调用 → `gate/check`（🔒 完成门）→ `turn/end`。对齐 dsh-agent-loop 的 turn/step 语义与 05 事件词汇，让循环的每个阶段可观测、可拦截、可审计。

**非目标**：不裁决"完成"（归 gate 服务）；不直接实例化 Worker（归 subagents）；不做内核装卸。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-agent-loop`（turn/step 事件化循环） |
| 对标 GNEX2.0 模块 | `agent-service agent/runtime`（Orchestrator + AgentRouter + ReActLoop + SessionLoop；SDK SessionLoop 事件驱动循环） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① Orchestrator/ReActLoop 收敛为 `agentLoop` 服务，各阶段发 waterfall/emit 事件；② 完成判定统一走 `gate`（gnex3-ent-gate，P0-a），循环自身不做完成裁决；③ SessionLoop 事件驱动化（turn/start·end、step/start·end）；④ 委派经 `subagents` 服务，不直连 Worker |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `agentLoop` | `com.gnex.plugin.agent.AgentLoop` | turn/step 循环驱动（runTurn/stop/status/onTurnEvent） |

### 3.2 注入服务（inject）

`agents`（必选）、`sessions`（必选）、`llm`（必选）、`prompt`（必选）、`tools`（必选）、`gate`（必选，完成证据门）、`scope`（必选）、`events`（必选）、`subagents`（可选，委派时）、`compaction`（可选）、`approval`（可选，高风险拦截）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `agent/pre-step` | waterfall | {sessionId, agentId, turn, step, context} — 可拦截可否决 |
| `agent/request` | emit | {sessionId, agentId, request, model, tokens} |
| `agent/request-error` | emit | {sessionId, agentId, error, retryable} |
| `agent/turn-stopping` | emit | {sessionId, agentId, reason} |
| `agent/status` | emit | {agentId, status} |
| `turn/start` · `turn/end` | emit | {sessionId, turn, status} |
| `step/start` · `step/end` | emit | {sessionId, turn, step, tool} |

## §4 配置与装配

```yaml
# agent-service service-assembly.yml
plugins:
  - id: gnex3-cap-agent-loop
    load: mandatory
settings:
  agentLoop:
    maxTurns: 64
    timeoutMs: 300000
    preStepEvent: waterfall   # 全阶段事件化
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 完成证据门（gnex3-ent-gate）不得被 agent-loop 绕过：循环内任何 completed 判定必须经 `gate`，禁止助手正文回退当证据（P0-a 过线最低条）
- ✅ 每步执行前必须发 `agent/pre-step`（waterfall，可被审批/权限拦截）
- ✅ 停止必须发 `agent/turn-stopping` 并回卷进行中副作用
- ❌ 不允许 Agent 自改内核/自装插件（循环不提供任何内核/插件装卸入口；装卸权仅平台管理员）
- ❌ 不允许循环直接实例化 Worker/子代理（必须经 `subagents`，保证委派闸门统一）
- ❌ 不允许循环内写完成证据（证据写归 gate）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 循环：runTurn 正常完成，事件序 pre-step→request→turn/end | `AgentLoopPluginTest.turnEventSequence` |
| V2 | 拦截：pre-step 监听器否决后该步不执行 | `...preStepDenied` |
| V3 | 完成门：无证物请求 completed 被 gate 拒（INCOMPLETE_DELIVERABLE），循环不自行标 completed | `...gateNotBypassed` |
| V4 | 错误：模型异常发 `agent/request-error` 且可重试 | `...requestErrorRetryable` |
| V5 | 停止：stop 后发 `agent/turn-stopping`，进行中工具被取消 | `...stopRollsBack` |
| V6 | 委派：循环内委派经 subagents，TaskCapabilityGate 能力预拒生效 | `...delegationViaSubagents` |
| V7 | 租户：scope 不匹配的会话被拒 | `...tenantIsolated` |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=AgentLoopPluginTest"
```

## §7 影响域

- 作用范围：**会话级**（每 turn 每请求）。
- 被谁消费：入口/工厂/命令/委派；事件被会话存储、观测、审计消费。
- 故障影响半径：该会话回合失败（不影响其他会话）。
- 升级影响：循环语义变更 = 全会话行为回归。

## §8 后续（未纳入首版）

- 长运行续跑（RunContinueResumeService / SessionLoopRecoveryService 迁移）
- 跨服务 agentLoop 委托（bus 桥接）
- 模型 fallback 策略外置（llm 插件化后）

---

*GNEX3.0 Agent 循环 — agent.runtime 事件化 SDD。*
