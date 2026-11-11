# v3-spec-session-projection

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.2（会话与持久化）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-session-projection
> **分层**：L3 能力层（3.2 会话与持久化）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：state-plane-service
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-23

---

## §1 业务目标

会话投影（CQRS 物化）：消费会话/turn/step/tool 事件，幂等物化会话读模型（消息、用量、状态聚合），并对上提供恢复包 `RestorePackage`（checkpoint + 事件序列 + token 用量 + 输出采纳），对齐 dsh-session-projection 与 gnex2.0 state-plane projector（E11/E12/E13：`MaterializationRouter`/`AgentRunEventProjector`/`IdempotencyGuard`/`RestorePackageAssembler`）。恢复公式：DB 基线 + MQ 增量回放，缓存只做加速。

本插件宿主为 state-plane-service，与事件生产方（agent-service 等）分属不同服务，因此事件消费必须经跨服务总线：session 域事件经既有 `agent_events` topic 到达投影器（见 §5 双通道分工）。

**非目标**：不接收热路径直写（事件经 bus 到达）；不做完成证据判定（归 gate）；不做检索（query）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-session-projection`（CQRS 物化投影） |
| 对标 GNEX2.0 模块 | `gnex-state-plane-service`：`MaterializationRouter` + `AgentRunEventProjector`/`CheckpointFlusher` + `IdempotencyGuard`（Redis/JDBC 双实现）+ `RestorePackageAssembler`/`StateRestoreService` |
| 技术选型 | 🅶 GNEX2.0 模块改造（projector 链插件化） |
| 改造要点 | ① 事件源从 MQ `agent_events` 扩展为 session/turn/step/tool 域事件（bus 桥）② projector 注册表化（domain → projector）③ 幂等物化保留（msgId 去重，ON CONFLICT，E9/E11）④ `sessionProjection` 服务暴露物化查询与 restore（E13）⑤ **bus 必选注入（D-P1-23）**：跨服务事件消费显式依赖 `bus` 服务，session 域事件经 `agent_events` topic 到达投影器 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `sessionProjection` | `com.gnex.plugin.session.SessionProjectionService` | materialize(event) / query(sessionId, model) / restore(sessionId) → RestorePackage |

### 3.2 注入服务（inject）

- `bus`（**必选**，D-P1-23）：跨服务事件总线——session 域事件（session/turn/step/tool）经既有 `agent_events` topic 订阅到达投影器；`BusUnavailableException` 重连语义见 §5
- `events`（必选）：进程内事件总线——listen 本服务（state-plane-service）内 emit 的域事件（进程内通道，见 §5 双通道分工）
- `storage`（必选）：pg 后端写读模型
- `settings`（必选）：配置段
- `logger`（必选）：日志
- `invariants`（可选）：运行时不变量

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `session/event` | listen | 通用日志条目 → 消息/用量读模型 |
| `session/created` · `session/disposed` | listen | 会话状态机（active/archived） |
| `turn/start` · `turn/end` | listen | 轮次聚合（turns/token） |
| `step/start` · `step/end` | listen | 步骤物化（含 tool 名摘要） |
| `tool/call` · `tool/result` | listen | 工具调用记录（先入日志后执行） |

> 到达通道（D-P1-23）：上表事件由 agent-service 等跨服务生产方经 bus 的 `agent_events` topic 投递到本投影器（bus 注入消费）；state-plane-service 进程内产生的事件经 `events` 通道 listen。两条通道进入同一 materialize 管线（§3.1），幂等去重对两通道统一生效。

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-cap-session-projection
    load: mandatory
    inject:
      - bus        # 必选：跨服务事件消费（D-P1-23）
      - events
      - storage
settings:
  sessionProjection:
    idempotency: redis      # redis|jdbc 幂等台账（E9）
    idempotencyTtlHours: 24 # agent_events 类 TTL
    batchSize: 32
    flushOnCheckpoint: true # 关键事件立即刷盘（CheckpointSaved 语义）
    bus:
      topic: agent_events   # session 域事件经既有 agent_events topic 到达
      consumerGroup: state-plane-projector
```

## §5 边界（禁止 ❌ / 必须 ✅）

**双通道事件分工（D-P1-23）**：

- `events`（进程内通道）：cordis-jvm 事件总线，仅承载**同服务（state-plane-service）内**的域事件 listen/emit——进程内、同步可见、scope 过滤。
- `bus`（跨服务通道）：跨服务事件一律经 bus（RocketMQ）投递；session 域事件（session/turn/step/tool，生产方在 agent-service）经既有 **`agent_events` topic** 到达本投影器（消费组 `state-plane-projector`），进入同一 materialize 管线。
- 两通道事件载荷同构（`gnex.<域>.<动作>` 命名），投影器不感知通道差异；幂等 msgId 去重统一覆盖两通道（防进程内与跨服务重复投递双写）。

边界清单：

- ✅ 物化必须幂等：msgId 去重（Redis Set 或 `state_plane_idempotency` ON CONFLICT），效果恰好一次（覆盖 events/bus 双通道）
- ✅ 恢复必须 `db_checkpoint + replay(events after last_step)` 拼合（E12），缓存仅加速
- ✅ 关键事件（checkpoint 保存）必须立即刷盘，不等批次
- ✅ 消费循环"先物化、再标幂等、最后 ack"；`BusUnavailableException` 必须重连，禁止当无消息
- ✅ 跨服务事件必须经 `bus` 注入消费（`agent_events` topic）；禁自建轮询/REST 拉取替代 bus 通道
- ❌ 不接收热路径直写（写经 agent-service 日志流，本插件只消费）
- ❌ 不得把 Redis 热状态当权威源（权威=结构化库基线+事件流）
- ❌ 晚到事件不得把 `RESUMABLE`/`COMPLETED` 盖回 `IN_PROGRESS`（checkpoint 状态回归禁止）
- ❌ 不在投影器内做完成证据判定/审计（归 gate/audit）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 幂等物化 | `ProjectionIdempotencyTest`：重复 msgId 只写一行（含 events/bus 双通道重复投递分支） |
| V2 | 恢复拼合 | `ProjectionRestoreTest`：restore 返回 checkpoint+增量事件，顺序正确 |
| V3 | 立即刷盘 | `ProjectionFlushTest`：checkpoint 事件不等批次即落盘 |
| V4 | 晚到事件 | `ProjectionLateEventTest`：COMPLETED 后 IN_PROGRESS 事件不覆盖状态 |
| V5 | 消费序 | `ProjectionOrderTest`：同 session 事件按 seq 顺序物化（ORDERLY） |
| V6 | 租户隔离 | `ProjectionRlsTest`：读模型行带 tenant_id，跨租户不可见 |
| V7 | bus 必选注入（D-P1-23） | `ProjectionBusInjectTest`：容器无 `bus` 服务时装配失败（fail-fast），错误信息指明缺失依赖；注入 bus 后正常订阅 `agent_events` topic 并物化 |
| V8 | 跨服务到达 | `ProjectionBusConsumeTest`：agent-service 侧 emit 的 `turn/end` 经 bus `agent_events` topic 到达投影器并物化（含 msgId 与进程内通道一致的去重） |

### 运行命令

```bash
mvn -pl services/gnex-state-plane-service test "-Dtest=SessionProjection*,Projection*Test"
```

## §7 影响域

- 作用范围：**会话级**（物化冷路径）+ **服务级**（state-plane 是 CQRS 读模型源）。
- 被谁消费：UI/统计、session-query（读模型）、session-title、恢复接口（E13）。
- 故障影响半径：state-plane 宕机不影响 Agent 运行（事件积压 MQ/bus，恢复后追平）。
- 升级影响：投影器/读模型 schema 变更 = 全量重放或迁移。

## §8 后续（未纳入首版）

- 与 persist 双写收敛（读模型统一由投影器产出）
- 跨会话聚合投影（用量/质量趋势，TimescaleDB）
- 投影回放工具（Dev 重放管线，对齐 DevBusMaterializationPoller）

---

*GNEX3.0 会话投影 — CQRS 物化·events 进程内与 bus 跨服务双通道 SDD。*
