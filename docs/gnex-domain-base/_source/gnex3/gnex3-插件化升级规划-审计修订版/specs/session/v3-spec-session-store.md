# v3-spec-session-store

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.2（会话与持久化）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-session-store
> **分层**：L3 能力层（3.2 会话与持久化）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

会话日志 append-only 事件溯源：以 `sessions` 服务提供会话创建/追加/读取/flush，把 turn/step/tool 生命周期沉淀为不可变日志条目，对齐 dsh-session 的会话日志语义与 agent_run_event（V27）的 append-only 运行事件表。目标是让"会话发生了什么"成为可回放的事实源：热路径只追加（内存缓冲），`session/event` 每条触发通知，`session/flush` 批量交 persist 落库，供投影/查询/导出/恢复消费。

**非目标**：不做 PG 落库（归 session-persist）；不做内容搜索（归 session-query）；不做标题/导出（归对应插件）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-session`（会话日志 append-only） |
| 对标 GNEX2.0 模块 | `agent-service agent/runtime`：`SessionLoop`（会话生命周期）+ `agent_run_event` 表（append-only 事件，`AgentRunEventService` 自建树） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① SessionLoop/AgentRunEventService 收敛为 `sessions` 服务：create/dispose/append/readRange/flush ② append 走内存缓冲 + `session/event` emit，`session/flush` 批量交 persist（对齐 05 "tool/call 先入日志再执行"）③ 条目带全局 seq/水印，flush 幂等 ④ 会话作用域 realm 隔离（scope） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `sessions` | `com.gnex.plugin.session.SessionLogService` | create(sessionId, meta) / dispose / append(entry) / readRange(from, to) / flush() |

### 3.2 注入服务（inject）

`events`（必选）、`scope`（必选，会话 realm 隔离）、`settings`（必选）、`logger`（必选）、`storage`（可选，缓冲溢出临时后端）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `session/created` | emit | {sessionId, tenantId, projectId, agentId, meta} |
| `session/event` | emit | {sessionId, seq, type, payload} — 每条追加 |
| `session/flush` | emit | {sessionId, upToSeq} — 缓冲达阈值/定时 |
| `session/disposed` | emit | {sessionId, reason} |
| `turn/start` · `turn/end` | listen | 追加轮次条目（{sessionId, turn, status}） |
| `step/start` · `step/end` | listen | 追加步骤条目（{sessionId, turn, step}） |
| `tool/call` · `tool/result` | listen | 先入日志再执行：append 工具调用/结果条目 |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-cap-session-store
    load: mandatory
settings:
  sessionStore:
    bufferSize: 512        # 每会话内存缓冲条目上限
    flushIntervalMs: 1000  # 定时 flush
    hotWindowTurns: 20     # 热窗口（对齐 transcript-hot-window-size）
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 日志必须 append-only：历史条目不可改写/删除，修正以新条目表达
- ✅ 每条追加必须发 `session/event`（携带递增 seq）；`session/flush` 必须携带 upToSeq 水印，flush 幂等
- ✅ `tool/call` 必须先入日志再执行工具（05 语义）
- ✅ 会话创建/销毁必须发 `session/created` / `session/disposed`
- ❌ 不直接写 PG/Redis 持久化（落库归 session-persist）
- ❌ 不提供内容级搜索/聚合（归 session-query / session-projection）
- ❌ append 不得阻塞 ReAct 热路径（缓冲异步，背压可 DROP 低优先条目）
- ❌ 不得跨租户/项目读写会话日志（scope realm 硬隔离，E15）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 追加与事件序 | `SessionStoreAppendTest`：append 后 `session/event` 携带 seq 递增，顺序与追加一致 |
| V2 | append-only | `SessionStoreImmutabilityTest`：对已写条目 update/delete 被拒 |
| V3 | tool 先入日志 | `SessionStoreToolOrderTest`：`tool/call` 条目在工具执行前落日志（时间戳断言） |
| V4 | flush 幂等 | `SessionStoreFlushTest`：同 upToSeq 重复 flush 只触发一次落库 |
| V5 | 生命周期事件 | `SessionStoreLifecycleTest`：create/dispose 发 `session/created`/`session/disposed` |
| V6 | 租户隔离 | `SessionStoreScopeTest`：跨 realm 读取为空 |
| V7 | 热路径不阻塞 | `SessionStoreBackpressureTest`：缓冲满 DROP 低优先条目，主循环无阻塞 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=SessionStore*Test"
```

## §7 影响域

- 作用范围：**会话级**（每会话日志）。
- 被谁消费：agentLoop（写入）、session-persist（flush 落库）、session-projection/query/title/export（事件消费）。
- 故障影响半径：store 故障 = 该会话日志丢失/不可回放（不阻塞其他会话）。
- 升级影响：条目 schema 变更 = 全量日志兼容迁移。

## §8 后续（未纳入首版）

- 跨副本会话日志汇聚（bus 桥接后 observability 侧聚合）
- 日志条目 TTL/归档策略外置（数据层变更单）
- 审计联动：`session/disposed` 触发 audit（🔒）

---

*GNEX3.0 会话日志 — append-only 事件溯源 SDD。*
