# v3-spec-delegation-subagents

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.7（委派与工作流）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-subagents
> **分层**：L3 能力层
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

子代理注册表：委派域唯一事实源，管理子代理（spawn/fork 的一次性 Worker 会话）的注册、状态、结果与证据回收。对齐 dsh-subagent 语义，把 gnex2.0 "Task→Worker"（Coordinator 调度 + SDK WorkerAgent/WorkerAgentFactory + AgentDelegationController）收敛为可枚举、可观测、带闸门的委派域。委派完成统一挂 gate 完成证据门（P0-a 过线最低条），TaskCapabilityGate 能力预拒保留。

**非目标**：不实现执行后端（归 subagent-backends）；不实现工作流编排（归 workflow）；不做分布式调度（Task→Worker 跨节点为后续）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-subagent`（子代理注册/委派） |
| 对标 GNEX2.0 模块 | Task→Worker（Coordinator 调度）+ gnex-agent-sdk（WorkerAgent / WorkerAgentFactory）+ AgentDelegationController；分布式 Task→Worker 重调度（retryCount≤3）；A2A（E27 目标路由） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① `subagents` 服务承载委派生命周期（start/end/status/result）；② 委派写路径挂 gate 完成证据门（P0-a）；③ TaskCapabilityGate 能力预拒保留；④ subagent/start·end 事件对齐 dsh 命名 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `subagents` | `com.gnex.plugin.delegation.SubagentRegistry` | spawn/fork/list/get/result/cancel；委派生命周期 |

### 3.2 注入服务（inject）

`agents`（必选，父/子 Agent 解析）、`agentLoop`（必选，子代理回合驱动）、`gate`（必选，完成证据门）、`scope`（必选）、`events`（必选）、`jobs`（可选，后台任务）、`approval`（可选，高风险委派审批）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `subagent/start` | emit | {parentSessionId, subagentId, mode: spawn/fork, task} |
| `subagent/end` | emit | {subagentId, result, deliverable, evidence} |
| `subagent/status` | emit | {subagentId, status, ts} |

## §4 配置与装配

```yaml
settings:
  subagents:
    maxConcurrent: 16
    maxDepth: 3            # 嵌套深度硬上限
    defaultMode: spawn
    requireEvidence: true  # 无证物不得 completed
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 所有委派完成必须经 `gate` 完成证据门：无证物 → INCOMPLETE_DELIVERABLE，不发 subagent/end(completed)
- ✅ 委派 Write 声称必须经 DelegationDeliverableResolver 校验本地/远程证据（P0-a 已有硬闸保留）
- ✅ TaskCapabilityGate 能力预拒：Worker 无 Write 而任务要落盘 → 预拒
- ✅ 子代理运行必须隔离（独立回合，scope 继承）
- ❌ 不允许子代理自改内核/自装插件
- ❌ 不允许把子代理正文回退当证据（T6 关闭，禁止正文回退）
- ❌ 不允许嵌套深度无限（maxDepth 硬上限）
- ❌ 不允许跨租户委派（scope 硬过滤）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | spawn：创建子代理并发 subagent/start | `DelegationSubagentsPluginTest.spawnEmitsStart` |
| V2 | 证据门：子代理声称 Write 无证据 → INCOMPLETE_DELIVERABLE | `...noEvidenceIncomplete` |
| V3 | 能力门：Worker 无 Write + 任务要落盘 → 预拒 | `...capabilityGateRejects` |
| V4 | 故意失败：故意失败 Write → 不得 completed | `...failedWriteNotCompleted` |
| V5 | 正文回退：仅正文无证物 → 不得 completed | `...noBodyFallbackEvidence` |
| V6 | 深度：嵌套超 maxDepth 拒绝 | `...depthLimit` |
| V7 | 租户：跨租户 spawn 被拒 | `...tenantIsolated` |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=DelegationSubagentsPluginTest"
```

## §7 影响域

- 作用范围：**会话级**（该会话委派）。
- 被谁消费：agentLoop、tool-subagent、workflow（节点委派）、UI 委派列表。
- 故障影响半径：该会话委派失败（不影响其他会话）。
- 升级影响：委派语义变更需回归（P0-a 关联，验收 V2-V5 为发布门）。

## §8 后续（未纳入首版）

- A2A 跨 Agent 委派（E27 a2a_delegate/a2a_response 桥接）
- 多副本委派外化（RedisActiveDelegationStore，E3/E4）
- 委派计费/审计联动（cost_events / audit_log）

---

*GNEX3.0 子代理注册表 — Task→Worker 插件化 SDD。*
