# v3-spec-approval

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.8
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-approval
> **分层**：能力层（L3）
> **受保护级别**：protected（平台持有，禁租户/Agent 卸载）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

审批原语（对齐 dsh-user-approval）：把 GNEX2.0 "高风险确认不是原语"（extension.spi.RiskClassifier/ApprovalHandler/NotificationHandler——DefaultRiskClassifier 工具名正则 + ConfirmationGateway SSE，有时弹、有时豁免、难同 turn 回放、难列队）改造为平台级审批原语（P1 E 做真）。提供 approval 服务：高风险调用默认入队、同 turn 回放（经 userQuestions 向用户提问）、待办列表与决策（approve/reject）。企业口径下审批不可跳过（MC-1），缺席审批 = deny（fail-closed）。WorkflowApprovalGate（workflow.engine APPROVAL 节点）接入本服务。

**非目标**：不做审批流引擎/多级审批（后续）；不做完成门（P0-a 归 gnex3-ent-gate，人闸不得顶完成门）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | @deepseek-ai/dsh-user-approval |
| 对标 GNEX2.0 模块 | platform-service extension.spi.RiskClassifier/ApprovalHandler/NotificationHandler（ConfirmationGateway SSE）+ agent-service workflow.engine.WorkflowApprovalGate |
| 技术选型 | 🅶 GNEX2.0 模块改造（🔒 protected） |
| 改造要点 | ① 审批队列持久化（approval_queue），高风险默认入队；② 同 turn 回放：入队后当前 turn 经 userQuestions 回放问题并挂起等待决策；③ 待办列表/决策 API（approve/reject，经 audit 落审计）；④ 事件化：approval/request（waterfall 可追加可否决）· approval/decided · approval/queue；⑤ 工具管线 pre-execute 中 Risk→Approval 环节硬接线；⑥ WorkflowApprovalGate 复用本服务 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `approval` | com.gnex.cap.approval.ApprovalService | 入队/同 turn 回放/待办列表/决策/风险分类（RiskClassifier） |

### 3.2 注入服务（inject）

- `permission`（必选）— 风险档位→权限校验（授权序单调收紧）
- `userQuestions`（必选）— 同 turn 回放提问
- `audit`（必选）— 请求/决策全链路审计
- `scope`（必选）— 待办按租户/项目隔离

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `approval/request` | emit / listen(waterfall) | {requestId, tool, args, risk, tenantId}；监听方可追加/否决 |
| `approval/decided` | emit / listen | {requestId, decision(approve/reject), by} |
| `approval/queue` | emit | {tenantId, pending[]} 待办队列变化 |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-cap-approval
    load: mandatory
    protection: platform
    config:
      approval:
        high-risk-default-enqueue: true   # P1 E：高风险默认入队
        same-turn-replay: true            # 同 turn 回放
        fail-closed: true                 # 缺席审批=deny
        queue-ttl: 24h
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 高风险默认入队 + 同 turn 回放 + 可列出待办（P1 E 做真）
- ✅ 缺席审批=deny（fail-closed）
- ❌ 审批不得被任何插件/Agent 绕过（企业口径不可跳过）
- ✅ 每次请求/决策必须经 audit 落审计（可追溯）
- ✅ 待办必须按租户/项目隔离（串租户=P0）
- ❌ 审批不得顶替 P0-a 完成门（人闸 ≠ 证据门）
- ❌ 风险分类不得由 Agent 自述（RiskClassifier 为平台侧规则，禁止模型自报低风险豁免）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 高风险默认入队 | ApprovalEnqueueTest：高风险工具调用自动入队并 emit approval/request |
| V2 | 同 turn 回放 | ApprovalReplayTest：入队后当前 turn 经 userQuestions 回放并挂起 |
| V3 | 待办列表 | ApprovalQueueTest：可列出待办（含租户过滤），approval/queue 事件 |
| V4 | 缺席审批=deny | ApprovalFailClosedTest：无决策者/超时 → deny，工具不执行 |
| V5 | 不可绕过 | ApprovalBypassTest：插件/Agent 直接调用工具不经 approval → 被 pre-execute 拒绝 |
| V6 | 工作流接入 | WorkflowApprovalGateTest：APPROVAL 节点复用 approval 服务，reject 断流 |
| V7 | 决策审计 | ApprovalAuditTest：请求/决策记录完整可查 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=ApprovalEnqueueTest,ApprovalReplayTest,ApprovalQueueTest,ApprovalFailClosedTest,ApprovalBypassTest,WorkflowApprovalGateTest,ApprovalAuditTest"
```

## §7 影响域

- 作用范围：**每高风险调用**（L3.8 影响域：高风险操作被拒/待办）；故障影响半径：approval 不可用 = 高风险操作 fail-closed 拒绝（安全侧），低风险不受影响。
- 被谁消费：工具管线 pre-execute（Risk→Approval 环节）、WorkflowApprovalGate、UI 审批中心、审计。
- 升级影响：🔒 平台级升级；队列 schema/事件载荷变更=全消费方回归。

## §8 后续（未纳入首版）

- 多级审批流/审批人指派（按组织角色）
- 审批 SLA/超时升级策略
- 审批与租户配额联动

---

*GNEX3.0 审批原语 — gnex3-cap-approval SDD（🔒 protected）。*
