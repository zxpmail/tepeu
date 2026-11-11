# v3-spec-delegation-workflow

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.7（委派与工作流）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-workflow
> **分层**：L3 能力层
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

工作流引擎：把 gnex2.0 workflow 包（WorkflowEngine / AgentFlowEngine / NodeExecutor / WorkflowGraphParser / WorkflowSquadExecutor / WorkflowApprovalGate）改造为 `workflowEngine` 服务：执行 JSON 定义的工作流（顺序 / FORK / JOIN / APPROVAL 节点），节点经 subagents 委派执行，支持断点续跑与事件观测（workflow/run-start · run-end）。对齐 dsh-workflow + dsh-workflow-worker-thread 语义。完成判定逐节点挂 gate（P0-a：AgentFlow 无闸 → 统一挂闸，T7 关闭）。

**非目标**：不替代委派闸门（归 subagents/gate）；不做计划生成（PlanGenerator 为输入之一）；不做审批原语本身（归 approval）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-workflow` + `dsh-workflow-worker-thread` |
| 对标 GNEX2.0 模块 | `agent-service workflow` 包（WorkflowEngine, AgentFlowEngine, PlanGenerator, NodeExecutor, WorkflowGraphParser, WorkflowSquadExecutor, WorkflowApprovalGate）+ platform-persistence（WorkflowDefinition / WorkflowInstance） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① 引擎收敛为 `workflowEngine` 服务；② 节点执行经 `subagents` 委派（WorkflowSquadExecutor 语义）；③ APPROVAL 节点桥接 approval 原语（E 做真：默认入队 + 同 turn 回放）；④ run-start/run-end 事件化；⑤ 断点续跑（WorkflowInstance 持久化状态） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `workflowEngine` | `com.gnex.plugin.delegation.WorkflowEngine` | 定义解析/运行/状态（run/pause/resume/status/nodeLog） |

### 3.2 注入服务（inject）

`subagents`（必选，节点委派执行）、`agents`（必选）、`gate`（必选，节点完成证据门）、`approval`（可选，APPROVAL 节点）、`scope`（必选）、`events`（必选）、`sessionPersistence`（可选，断点）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `workflow/run-start` | emit | {runId, definitionId, tenantId, nodes[]} |
| `workflow/run-end` | emit | {runId, status, evidence, durationMs} |
| `subagent/start` · `subagent/end` | emit | 节点委派透传（可观测） |

## §4 配置与装配

```yaml
settings:
  workflowEngine:
    maxParallelNodes: 8
    approvalRequired: true   # 高风险节点默认入队
    checkpoint: true         # 断点续跑
workflows:
  example:
    nodes: [ {id: a}, {id: b, type: fork}, {id: c, type: approval} ]
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 工作流"完成"必须逐节点经 `gate` 证据门（P0-a：AgentFlow/Swarm 统一挂闸，T7 关闭）
- ✅ 节点完成证据不得用助手正文回退（P0-a 禁止正文回退）
- ✅ APPROVAL 节点必须走 approval 原语（高风险默认入队 + 同 turn 回放，审批地板不可跳过）
- ✅ 断点续跑必须从持久化状态恢复（不重跑已完成节点）
- ❌ 不允许工作流节点绕过 `subagents` 直接执行
- ❌ 不允许工作流自改内核/自装插件
- ❌ 不允许未审批的高风险节点自动通过（E 做真，CW-1 授权序）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 顺序：线性节点按序执行，run-end = completed | `WorkflowEnginePluginTest.linearNodes` |
| V2 | FORK/JOIN：并行节点全部完成后 JOIN | `...forkJoinBarrier` |
| V3 | 证据门：节点无证物 → 该节点不得 completed，run-end 标 failed/incomplete | `...gatePerNode` |
| V4 | 审批：APPROVAL 节点未审批不执行后续节点 | `...approvalHolds` |
| V5 | 断点：中断后 resume 从断点续跑不重跑 | `...resumeFromCheckpoint` |
| V6 | 委派：节点经 subagents 注册与回收 | `...nodesViaSubagents` |
| V7 | 租户：跨租户定义被拒 | `...tenantIsolated` |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=WorkflowEnginePluginTest"
```

## §7 影响域

- 作用范围：**会话级/运行级**（该工作流运行）。
- 被谁消费：tool-workflow、命令、UI 工作流面板。
- 故障影响半径：该工作流运行失败（不影响其他会话/运行）。
- 升级影响：引擎语义变更 = 存量定义兼容回归（WorkflowDefinition 兼容）。

## §8 后续（未纳入首版）

- 工作流审批回放 UI（同 turn 回放可视化）
- 跨服务执行（workflow-service 独立拆分）
- 分布式断点（Redis checkpoint，对齐 E2 快照续接）

---

*GNEX3.0 工作流引擎 — workflow 包插件化 SDD。*
