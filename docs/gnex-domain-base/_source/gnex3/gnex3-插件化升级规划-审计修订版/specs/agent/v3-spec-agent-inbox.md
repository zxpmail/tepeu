# v3-spec-agent-inbox

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.4（Agent 循环）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-agent-inbox
> **分层**：L3 能力层
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

会话 inbox：agentLoop 的输入队列，承载 steer（转向）/ followup（追问）/ inject（注入系统事件）三类消息。对齐 dsh-agent 的 inbox 与 tool-presentation 语义，把 gnex2.0 `AgentInboxBusSubscriber`（agent_delegate topic 串行派发 + AgentInboxIdempotencyGuard + InboxMessageStore 恢复）改造为 inbox 插件服务。同一消息信封同时支持进程内注入与跨服务 bus 派发，保证行为一致。

**非目标**：不执行回合（归 agentLoop）；不裁决完成（归 gate）；不做消息总线本身（归 ent-bus）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-agent`（inbox）+ `dsh-agent-tool-presentation` |
| 对标 GNEX2.0 模块 | `agent-service agent/runtime` inbox（AgentInboxBusSubscriber + AgentInboxIdempotencyGuard + InboxMessageStore；agent_delegate topic 消费） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① inbox 收敛为 `agentInbox` 服务，作为 agentLoop 输入端口（队列/幂等/恢复）；② steer/followup/inject 统一信封；③ agent_delegate 总线派发桥接为 inbox 注入；④ msgId 幂等去重 = effectively-once（对齐总线 at-least-once） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `agentInbox` | `com.gnex.plugin.agent.AgentInbox` | 会话输入队列（enqueue/steer/followup/inject/cancel/dequeue） |

### 3.2 注入服务（inject）

`agentLoop`（必选，循环消费 inbox）、`sessions`（必选）、`scope`（必选）、`events`（必选）、`bus`（可选，跨服务派发桥接）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `agent/turn-stopping` | listen | 取消该会话 inbox 排队消息（可选） |
| `session/disposed` | listen | 清理 inbox 队列 |

## §4 配置与装配

```yaml
settings:
  agentInbox:
    queueDepth: 256
    idempotencyTtlMs: 86400000
    consumeOrder: FIFO        # 同会话串行保序
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ inbox 必须按会话串行消费（同会话保序，对齐 AgentInboxBusSubscriber 按 agentName 加锁）
- ✅ 注入必须带 msgId 幂等去重（effectively-once）
- ✅ 进程内注入与跨服务 bus 派发行为一致
- ❌ 不允许 inbox 消息直接标记会话完成（完成判定归 gate）
- ❌ 不允许跳过 agentLoop 直接执行（inbox 只是队列，不是执行器）
- ❌ 不允许跨租户投递（scope 过滤）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 队列：enqueue 后 agentLoop 按 FIFO 消费 | `AgentInboxPluginTest.fifoOrder` |
| V2 | 幂等：相同 msgId 重复投递只执行一次 | `...idempotentDedup` |
| V3 | 串行：同会话并发投递不并发执行 | `...serialPerSession` |
| V4 | 取消：turn-stopping 后排队消息可取消 | `...cancelQueued` |
| V5 | 恢复：进程重启后未消费消息从 InboxMessageStore 恢复 | `...restoreAfterRestart` |
| V6 | 租户：跨租户投递被拒 | `...tenantIsolated` |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=AgentInboxPluginTest"
```

## §7 影响域

- 作用范围：**会话级**（该会话输入路径）。
- 被谁消费：agentLoop、bus（agent_delegate）、UI/命令（steer/followup）。
- 故障影响半径：该会话输入积压（不影响其他会话）。
- 升级影响：消息信封变更需迁移（inbox 持久化格式）。

## §8 后续（未纳入首版）

- 优先级队列（steer 优先于 followup）
- 多副本 inbox 外化（Redis，对齐 E4 热状态外化）
- A2A 目标路由桥接（E27 a2a_delegate 消费）

---

*GNEX3.0 会话 inbox — agent/runtime inbox 插件化 SDD。*
