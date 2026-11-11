# v3-spec-session-persist

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.2（会话与持久化）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-session-persist
> **分层**：L3 能力层（3.2 会话与持久化）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

会话持久化（PG）：把 `sessions` 日志的 flush 批次与会话元数据落到 PostgreSQL 16 结构化温库，对齐 dsh-session-persistence(-jsonl) 的"日志落盘"职责与 gnex2.0 gnex-agent-persistence（conversation_session / conversation_message / session_archive / context_summary）。多副本共享同一 PG 是 E1（副本无状态）的前提：写 A 读 B 一致；TTL/归档策略沿用（checkpoint 7 天、历史归档）。

**非目标**：不做查询/搜索（session-query）；不做投影物化（session-projection）；不做标题/导出。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-session-persistence-jsonl`（会话落盘持久化） |
| 对标 GNEX2.0 模块 | `libs/gnex-agent-persistence`（`ConversationSession`/`ConversationMessage`/`SessionArchive` 实体 + MyBatis-Plus Mapper）+ `gnex-persistence-common`（`DatabaseDialect`/`SchemaMigrationRunnerV2`/`PgRlsInitializer`/`PgHaAdapter`/`IdGenerator`） |
| 技术选型 | 🅶 GNEX2.0 模块改造（持久层收敛为插件服务） |
| 改造要点 | ① `sessionPersistence` 服务包装既有 Mapper：saveEntries/loadSession/saveTitle/archive ② 批量写入事务化 + seq 去重幂等（E9 effectively-once）③ 经 `storage` hub 的 pg 后端落库（不自建连接）④ 迁移/RLS/HA/ID 复用 persistence-common |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `sessionPersistence` | `com.gnex.plugin.session.SessionPersistenceService` | saveEntries(sessionId, entries, upToSeq) / loadSession(sessionId, fromSeq) / saveTitle / archive(sessionId) |

### 3.2 注入服务（inject）

`storage`（必选，pg 后端，domain: session）、`events`（必选，listen flush/disposed）、`scope`（必选）、`settings`、`credentials`（可选，PG 口令引用）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `session/flush` | listen | 接收 {sessionId, upToSeq} → 批量事务落 PG |
| `session/disposed` | listen | 封存归档：写 `session_archive`，标记会话终态 |
| `session/created` | listen | 首条元数据（conversation_session）初始化 |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-cap-session-persist
    load: mandatory
settings:
  sessionPersistence:
    batchSize: 200          # 单批条目上限
    flushAck: true          # 落库成功后回 ack（store 清缓冲）
    archiveOnDispose: true  # session/disposed 时归档
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 批量写入必须单事务；同 upToSeq 重复 flush 必须幂等（seq 唯一约束/ON CONFLICT）
- ✅ 落库必须经 `storage` hub 的 pg 后端，禁止自建数据源/绕过 hub（对齐 storage-pg §5）
- ✅ 生产必须启用 PG RLS 租户隔离（E15），行带 tenant_id
- ✅ 会话终态必须走 `session_archive` 归档，历史只追加不删除（删除=标记）
- ❌ 不存附件正文（对象存储，见 attachment 插件）
- ❌ 不在 persist 内做内容搜索/聚合（归 query/projection）
- ❌ 落库失败不得静默丢日志（重试/进 DLQ，P1 告警）
- ❌ 不得把 checkpoint/Redis 热状态当权威源写入 PG（权威=本日志）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | flush 批量落库 | `SessionPersistFlushTest`：saveEntries 后 PG 行数与条目一致 |
| V2 | 幂等去重 | `SessionPersistIdempotencyTest`：同 upToSeq 重复 flush 不产生重复行 |
| V3 | 加载回读 | `SessionPersistLoadTest`：loadSession(fromSeq) 与写入序一致 |
| V4 | 归档 | `SessionPersistArchiveTest`：dispose 后 archive 行生成，原表标记终态 |
| V5 | RLS 隔离 | `SessionPersistRlsTest`：跨租户行不可见 |
| V6 | 失败重试 | `SessionPersistRetryTest`：DB 瞬断重试成功，无静默丢日志 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=SessionPersist*Test"
```

## §7 影响域

- 作用范围：**会话级 + 租户级**（结构化温库）。
- 被谁消费：agentLoop、session-query/projection/export（读模型来源）、title（标题落库）。
- 故障影响半径：PG 不可用 = 会话日志不落盘（`PgHaAdapter` <30s 切换；store 缓冲积压）。
- 升级影响：表结构变更走 `SchemaMigrationRunnerV2` 前滚迁移。

## §8 后续（未纳入首版）

- 读写分离/只读副本（HA 扩展）
- TTL 分层（消息冷热分级，TimescaleDB）
- 与 state-plane 双写收敛（由 persist 发 bus 事件替代直写读模型）

---

*GNEX3.0 会话持久化 — PG 温库落盘 SDD。*
