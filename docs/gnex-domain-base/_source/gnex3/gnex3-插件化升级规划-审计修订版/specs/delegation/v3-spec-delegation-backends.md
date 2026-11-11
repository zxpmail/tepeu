# v3-spec-delegation-backends

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.7（委派与工作流）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-subagent-backends
> **分层**：L3 能力层
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

spawn/fork 执行后端：为 subagents 提供可插拔的执行后端抽象。spawn = 独立新回合（对齐 dsh-subagent-spawn），fork-in-process = 继承父上下文快照（对齐 dsh-subagent-fork-in-process）。对齐 gnex2.0 子 Agent 调度（已有）与 SDK WorkerAgent 语义。后端差异只影响执行方式，不影响完成判定——所有后端共享同一 gate 完成证据门（05 影响域：后端增删 = 无感）。

**非目标**：不做委派生命周期管理（归 subagents）；不做跨节点 Worker 调度（后续）；不做沙箱执行（归 sandbox）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-subagent-spawn` + `dsh-subagent-fork-in-process` |
| 对标 GNEX2.0 模块 | agent-service 子 Agent 调度（已有）+ gnex-agent-sdk（WorkerAgent / WorkerAgentFactory）+ A2A（E27 目标路由） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① Backend SPI + 注册表（spawn/fork 两实现）；② fork-in-process 继承父上下文快照（写隔离）；③ spawn 走独立回合 + inbox；④ 后端不得绕过 subagents 注册表与 gate |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `subagentBackends` | `com.gnex.plugin.delegation.SubagentBackends` | 后端注册表（register/get/create: spawn\|fork） |

### 3.2 注入服务（inject）

`subagents`（必选，注册/结果回收）、`agentLoop`（必选，spawn 独立回合）、`agents`（可选）、`gate`（必选，完成证据门）、`scope`（必选）、`events`（必选）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `subagent/start` | emit | {subagentId, backend: spawn/fork, task} |
| `subagent/end` | emit | {subagentId, backend, result, evidence} |

## §4 配置与装配

```yaml
settings:
  subagentBackends:
    default: spawn
    spawn: { maxTurns: 32, timeoutMs: 600000 }
    fork:
      maxSnapshotSizeKb: 512
      inheritScope: true
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ spawn 与 fork 必须共享同一完成证据门（gate），后端差异不影响完成判定
- ✅ fork-in-process 必须上下文快照隔离（子代理写不污染父上下文）
- ✅ 后端必须经 `subagents` 注册表注册/回收（禁绕过）
- ❌ 不允许新增后端绕过 TaskCapabilityGate 能力预拒
- ❌ 不允许 fork 复制受保护/超限上下文（maxSnapshotSizeKb 硬上限）
- ❌ 不允许子代理经后端自装插件/改内核

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | spawn：创建独立回合子代理，结束回写结果 | `SubagentBackendsPluginTest.spawnIndependentTurn` |
| V2 | fork：继承快照，子代理写不污染父上下文 | `...forkSnapshotIsolated` |
| V3 | 同门：两后端走同一 gate，无证物均不得 completed | `...sharedGate` |
| V4 | 能力门：后端调用保留 TaskCapabilityGate 预拒 | `...capabilityGateHeld` |
| V5 | 快照上限：超限 fork 拒绝 | `...snapshotSizeLimit` |
| V6 | 注册：未注册后端调用被拒 | `...unknownBackendRejected` |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=SubagentBackendsPluginTest"
```

## §7 影响域

- 作用范围：**会话级**（该子代理执行）。
- 被谁消费：subagents、工具（spawn/fork 工具）、workflow 节点。
- 故障影响半径：该子代理启动失败（不影响父回合与其它委派）。
- 升级影响：后端增删 = 无感（其余插件不动）。

## §8 后续（未纳入首版）

- 远程后端（跨节点 Worker 调度，对齐分布式 Task→Worker）
- A2A 后端（E27：targetAgentId 目标路由执行）
- 沙箱化子代理执行（sandbox 后端）

---

*GNEX3.0 spawn/fork 后端 — 子 Agent 调度插件化 SDD。*
