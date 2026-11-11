# v3-spec-session-checkpoint

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.2（会话与持久化）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-session-checkpoint
> **分层**：L3 能力层（3.2 会话与持久化）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

检查点（Redis）：把 gnex2.0 `ReActCheckpointService`（react_checkpoint 表 + lease + last_event_seq 水印）与 `RedisActiveRunSnapshotStore`（`gnex:active-run:*` 三类 key，E2/E4 跨副本续接）收敛为会话级检查点服务 `sessionCheckpoint`：每 turn 结束持久化运行态快照，支持崩溃恢复、断线续跑与多副本接管（用户最坏恢复时间 ≈ lease-ttl 15s）。对齐 dsh-session-checkpoint-policy 的"何时存/存什么/何时恢复"策略语义。

**非目标**：不存会话正文权威（归 persist/日志）；禁止混称记忆平面（P0-b）；不做完成判定。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-session-checkpoint-policy`（checkpoint 策略） |
| 对标 GNEX2.0 模块 | `libs/gnex-agent-sdk`：`ReActCheckpointService`（saveProgress/markResumable/markCompleted/cleanupOrphanedInProgress + checkpoint lease）+ `RedisActiveRunSnapshotStore`（E2：`gnex:active-run:run/session/sse:*`，`hydrateFromRemoteStore`） |
| 技术选型 | 🅶 GNEX2.0 模块改造（agent-sdk checkpoint 收敛为插件服务） |
| 改造要点 | ① `sessionCheckpoint` 包装 ReActCheckpointService + ActiveRunSnapshotStore：save/load/resume/lease ② 状态机 IN_PROGRESS/RESUMABLE/COMPLETED 保留，晚到事件不回退 ③ Redis 快照 key 带 `t_{tenant}:` 前缀 + TTL（E15）④ 配置对齐 `checkpoint-interval-turns`/`lease-ttl-seconds`(15)/`lease-heartbeat-interval-seconds`(5) |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `sessionCheckpoint` | `com.gnex.plugin.session.SessionCheckpointService` | save(snapshot) / load(sessionId) / resume(sessionId) / lease(sessionId) / findResumable(sessionId) |

### 3.2 注入服务（inject）

`storage`（必选，redis/StateInterface 后端，key `gnex:active-run:*`）、`events`（必选，listen turn 生命周期）、`scope`（必选）、`settings`、`logger`。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `turn/end` | listen | 每 turn 结束触发 save（受 checkpoint-interval-turns 降频，slice/PAUSED 强制） |
| `session/flush` | listen | 同步水印/快照一致性（可选） |
| `session/disposed` | listen | 清理孤儿 checkpoint（对齐 cleanupOrphanedInProgress） |

## §4 配置与装配

```yaml
settings:
  sessionCheckpoint:
    backend: redis           # redis | stateinterface
    keyPrefix: "gnex:active-run:"
    checkpointIntervalTurns: 1   # 对齐 gnex checkpoint-interval-turns
    leaseTtlSeconds: 15
    leaseHeartbeatSeconds: 5
    snapshotTtlMinutes: 60   # 会话活跃期 + 30min 兜底
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 每 turn 结束必须持久化 checkpoint（降频配置下 slice 边界与 PAUSED 强制落盘）
- ✅ 状态机必须单向：IN_PROGRESS → RESUMABLE/COMPLETED，晚到事件禁止回退覆盖
- ✅ 租约必须心跳续约（lease 防双副本重复接管，E4）；进程恢复经 `hydrateFromRemoteStore`
- ✅ Redis key 必须带租户前缀 + TTL（E15），孤儿 checkpoint 必须清理
- ❌ checkpoint 不得混称/混存企业记忆（P0-b 钉死：会话 Redis checkpoint ≠ 记忆平面）
- ❌ 不存附件/大正文（快照仅运行态，正文走 persist/对象存储）
- ❌ 恢复不得绕过 `sessionCheckpoint` 直读 Redis（统一入口，防状态回归）
- ❌ 不在 checkpoint 内做完成证据判定（归 gate）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 每 turn 快照 | `CheckpointSaveTest`：turn/end 后 Redis 快照可 load，内容一致 |
| V2 | 状态机 | `CheckpointStateTest`：COMPLETED 后 IN_PROGRESS 写入被拒（不回退） |
| V3 | 断线续跑 | `CheckpointResumeTest`：resume 从 RESUMABLE 恢复，transcript 热窗口正确 |
| V4 | 租约 | `CheckpointLeaseTest`：心跳停止 → 租约过期 → 他副本接管（≈15s） |
| V5 | 跨副本 | `CheckpointCrossReplicaTest`：A 写快照 B hydrate 同 run_id（E2 端到端） |
| V6 | 孤儿清理 | `CheckpointCleanupTest`：dispose 后孤儿 IN_PROGRESS 被清理 |
| V7 | 租户前缀 | `CheckpointKeyScopeTest`：key 带 `t_{tenant}:`，跨租户不可读 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=SessionCheckpoint*Test"
```

## §7 影响域

- 作用范围：**会话级**（每运行态快照）+ **进程级**（跨副本续接）。
- 被谁消费：agentLoop（save/resume）、SessionLoopRecoveryService / RunContinueResumeService（续跑）、state-plane（restore 基线）。
- 故障影响半径：Redis 不可用 = 崩溃恢复退化为单副本（不丢已落 PG 日志）。
- 升级影响：快照 schema 变更 = 旧快照兼容迁移（版本字段）。

## §8 后续（未纳入首版）

- 快照压缩/增量（大 transcript 懒加载优化）
- checkpoint 与三层恢复协议深度整合（E12 全量）
- Redis Cluster 演进（内存 >10GB 触发线）

---

*GNEX3.0 会话检查点 — Redis 运行态快照 SDD。*
