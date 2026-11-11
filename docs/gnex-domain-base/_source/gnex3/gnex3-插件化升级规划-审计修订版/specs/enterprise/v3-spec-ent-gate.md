# v3-spec-ent-gate

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-gate
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-gate
> **分层**：企业层（L4）
> **受保护级别**：protected（平台持有，禁租户/Agent 卸载）
> **宿主服务**：agent-service（装配在 Agent OS 热路径控制面）
> **实现状态**：规划中

---

## §1 业务目标

P0-a「完成 = 证据」的统一出口：把 gnex2.0 已有局部硬闸（`DelegationDeliverableResolver` 无证据 → 硬 `INCOMPLETE_DELIVERABLE`、`TaskCapabilityGate` 能力预拒）收敛为**同一个** `gate` 证据门，补齐 T1–T7 缺口——SSE COMPLETED 软 nudge、TodoWrite 任意勾完、Chat 回合结束即 completed、非 Write 委派任意正文、Swarm/AgentFlow/Goal 无闸——所有「标交付 completed / 用户可见『已写入』」的定案路径必须挂同一证据门，**禁止助手正文回退当证据**。过线最低条：故意失败 Write / http_post 非 2xx → 任一路不得 completed；Todo 勾完 / SSE COMPLETED 无证物不得呈现为交付成功。

**非目标**：不替代委派闸门的范围判定（`TaskReentryGate` 修正归 subagents，修须按指纹禁加次数）；不做审批原语（归 approval）；不做评分/评审（只做可校验证物判定）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh 无此原语（dsh 无完成证据门；R-07 为观感证伪，不得单凭其宣称过线） |
| 对标 GNEX2.0 模块 | `agent-service` 委派闸（`DelegationDeliverableResolver` / `TaskCapabilityGate` / `WriteGateTest`，已有硬闸）+ SSE 完成路径（`SseEvent.completed`，T4 缺口）+ `GnexTodoWriteTool`（T3 缺口） |
| 技术选型 | 🅶 GNEX2.0 模块改造（已有硬闸保留并收敛为统一 `gate` 服务） |
| 改造要点 | ① 所有 completed 出口（委派/Todo/SSE/Swarm/AgentFlow/Goal/工作流节点/回合结束）调用同一 `gate` ② 证物类型白名单（Write 成功路径 / 远程 2xx 记录 / 结构化工件 URI），删除正文回退分支 ③ 无证物 → 状态 = incomplete/denied，SSE 不得发完成事件 ④ `gate/check`→`gate/passed|denied` 事件化（可观测） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `gate` | `com.gnex.plugin.enterprise.gate.CompletionEvidenceGate` | 统一完成证据门：check(evidence, scope) → passed/denied；证物注册、审计指针、覆盖表（gated vs ungated） |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`tenant`、`audit`
- 可选：`sessions`（回合结束路径）、`tools`（Todo/Write 工具结果）、`observability`

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `gate/check` | emit | {path, taskId, deliverableClaim, evidence[]}：进入检查 |
| `gate/passed` | emit | {path, evidenceRef, auditId}：有证物放行 |
| `gate/denied` | emit | {path, reason, missing[]}：无证物拒绝，不得 completed |

## §4 配置与装配

```yaml
# service-assembly.yml 片段（agent-service）
plugins:
  - id: gnex3-ent-gate
    load: mandatory
    protection: platform
settings:
  gate:
    evidenceWhitelist: [writePath, remote2xx, artifactUri]  # 证物类型白名单
    denyTextFallback: true      # 禁止正文回退（恒 true，不可关）
    todoRequiresEvidence: true  # Todo 勾完须证物（T3 关闭）
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 所有标交付 completed 路径必须挂**同一**证据门（委派/Todo/SSE/Swarm/AgentFlow/Goal/工作流节点/回合结束）
- ✅ 故意失败 Write / http_post 非 2xx → 任一路不得 completed（过线最低条 ②，可证伪断言）
- ✅ 证物必须可校验：Write 成功路径（`turnWritePaths`）/ 远程 2xx 记录 / 结构化工件 URI，带审计指针
- ✅ 无证物时状态必须 = incomplete/denied，UI/SSE 不得呈现为交付成功
- ❌ 禁止助手正文回退当证据（`resolveCompletionEvidence` 整段正文回退 = 自我开洞，T6 关闭）
- ❌ Todo 勾完 / SSE COMPLETED 无证物不得呈现为交付成功（过线最低条 ③）
- ❌ 禁止软 nudge / SSE 完成事件替代硬闸（T1 关闭）
- ❌ 禁止非 Write 委派以任意非空正文定案（T5 关闭）；禁止租户/Agent 装卸本插件（🔒）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 故意失败 Write → 不得 completed | `GateFailWriteTest`：Write 抛错 → gate/denied，状态 incomplete |
| V2 | http_post 非 2xx → 不得 completed | `GateHttpNon2xxTest`：500 响应 → denied，无 `gate/passed` |
| V3 | Todo 无证物勾完 → 不得呈现成功 | `GateTodoNoEvidenceTest`：勾完但无 writePath/artifact → denied |
| V4 | SSE COMPLETED 无证物 → 不发完成事件 | `GateSseNoEvidenceTest`：`SseEvent.completed` 仅在 passed 后发出 |
| V5 | 正文回退分支不可达 | `GateTextFallbackBlockedTest`：evidence 为空时抛 INCOMPLETE_DELIVERABLE，不取正文 |
| V6 | 委派 Write 有证物 → passed | `GateWriteEvidencePassTest`：`outputs/` 落盘 → passed + 审计指针 |
| V7 | 覆盖表：全部 completed 路径已挂闸 | `GateCoverageTableTest`：gated vs ungated 清单为空 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=GateFailWriteTest,GateHttpNon2xxTest,GateTodoNoEvidenceTest,GateSseNoEvidenceTest,GateTextFallbackBlockedTest,GateWriteEvidencePassTest,GateCoverageTableTest"
```

## §7 影响域

- 作用范围：**会话级/委派级**——所有完成定案路径。
- 被谁消费：agentLoop、委派（subagents）、工作流节点、Todo 工具、SSE 输出、UI 完成展示。
- 故障影响半径：误杀合法多步委派（同 `TaskReentryGate` 教训，修须按指纹禁加次数）；放行 = P0-a 未过。
- 升级影响：🔒 平台级升级；证据门语义变更 = 全完成路径回归（V1-V7 为 P0-a 过线门禁）。

## §8 后续（未纳入首版）

- 证物类型扩展（结构化引用，外部对标 H/LX-3）
- 完成证据门跨服务（workflow-service 独立部署时经 bus 调用）
- 闸门失败重评估（外部对标 I：denied 后带原因重试闭环）

---

*GNEX3.0 完成证据门 — P0-a 统一出口 SDD。*
