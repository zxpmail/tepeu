# v3-spec-ent-stateplane

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-stateplane
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-stateplane
> **分层**：企业层（L4）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：state-plane-service
> **实现状态**：规划中

---

## §1 业务目标

CQRS 冷路径执行者：作为总线订阅方消费 agent_events / sandbox_events / cost_events / quality_events / audit_log，经解码→校验→幂等去重→按 topic 路由到 6 类 projector，把事件物化写入 PostgreSQL 读模型（agent_run_event/react_checkpoint/sandbox_lifecycle/token_usage/quality_score_event/audit_log）。提供三层恢复协议（checkpoint 基线 + MQ 增量 replay + 热缓存加速）与恢复包 RestorePackage（checkpoint+events+token_usage+output_adoption）。插件化价值：`statePlane` 服务成为状态面统一入口，projector 可增删而不动消费框架。

**非目标**：不做合并算法（恢复只暴露数据源，注释原文语义）；不做业务查询（归 observability）；不做热路径缓存库直写（归 StateInterface SDK）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | 无（dsh 无此原语；catalog 曾弱类比 dsh-session-projection-cache） |
| 对标 GNEX2.0 模块 | state-plane-service：MaterializationHandler/MaterializationRouter/IdempotencyGuard + 6 类 Projector（AgentRunEvent/CheckpointFlusher/SandboxLifecycle/TokenUsage/QualityScore/AuditLog）+ StateRestoreService/RestorePackageAssembler |
| 技术选型 | 🅶 GNEX2.0 模块改造为插件（projector 族 → statePlane 服务） |
| 改造要点 | ① 插件化：`statePlane` 提供 StateRestoreInterface + projector 注册表（register(topic, filter, projector)）；② 消费纪律保持：先物化→再标幂等→最后 ack；③ 幂等双实现：Redis（agent_events 24h/sandbox_events 6h/audit_log 1h）或 state_plane_idempotency 表；④ 三层恢复协议落地（现网仍引擎同步直写，迁移依赖总线 RocketMQ 传输就绪）；⑤ CheckpointSaved 立即刷盘，晚到事件不得覆盖终态 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `statePlane` | `com.gnex.contracts.state.StateRestoreInterface` | restore(sessionId) → RestorePackage；projector 注册表（register/unregister） |

### 3.2 注入服务（inject）

- `bus`（必选）：订阅 5 类 topic（subscribe + CLUSTERING/ORDERLY，group 可加 suffix）与 Dev 探针 produce
- `storage`（可选）：物化表 Mapper（TokenUsage/OutputAdoption/AuditLog/QualityScoreEvent/SandboxLifecycle）
- `scope` / `settings` / `events`（必选）：租户 realm、消费组/批次配置、事件

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `bus/consumed` | listen | {topic, msgId, filter} → 路由到 projector 幂等物化（SUCCESS/RETRY） |
| `bus/published` | emit | Dev 探针 produce（agent/sandbox/cost/audit 事件） |
| `statePlane/restored` | emit | {sessionId, restoreMs, checkpointStep, replayed}（新增，待入 05 词汇） |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-ent-stateplane
    load: mandatory
    config:
      gnex.state-plane.consumer-group-suffix: ""   # 多环境隔离
      gnex.state-plane.idempotency.redis-enabled: true  # Redis 幂等，缺失回退 JDBC 台账
      gnex.state-plane.quality-events.enabled: true
      gnex.state-plane.dev.enabled: false          # Dev 发布探针
      gnex.state-plane.batch-size: 32              # 批量上限
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 消费必须「先物化、再标幂等、最后 ack」（反序会丢消息）
- ✅ 物化必须幂等（msg_id 去重：Redis TTL 或 DB 唯一索引），CheckpointSaved 立即刷盘
- ✅ 恢复必须三层拼合（db_checkpoint + replay(events after last_step) + 热缓存加速）
- ✅ BusUnavailableException 必须重连，绝不能当"无消息"；总线全挂 → 只读降级，拒新 Action 下发
- ✅ 晚到事件不得把 RESUMABLE/COMPLETED 状态盖回 IN_PROGRESS（checkpoint UPSERT 守卫）
- ❌ state-plane 不得依赖 Agent 引擎（状态面宕机不影响 Agent 运行，事件积压 MQ）
- ❌ 不得承诺 exactly-once 投递（at-least-once + 幂等物化 = effectively-once）
- ❌ 不得把审计 DLQ 当普通消息吞掉（P1 告警 + requeue，90 天合规）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | topic 路由物化 | `MaterializationRouterTest`：5 类 topic 按 filter 路由到对应 projector |
| V2 | 幂等去重 | `IdempotencyGuardTest`：同 msgId 重投不重复物化（Redis 与 JDBC 双实现） |
| V3 | checkpoint 刷盘 | `CheckpointFlusherTest`：CHECKPOINT_SAVED 立即 UPSERT，状态不倒退回 |
| V4 | 三层恢复 | `StateRestoreTest`：restore 返回 checkpoint+events+token+adoption，replay 增量补齐 |
| V5 | 乱序/晚到事件 | `LateEventTest`：晚到事件不覆盖终态 |
| V6 | 消费组隔离 | `ConsumerGroupTest`：suffix 生效，多副本单消费不破坏顺序 |
| V7 | Bus 不可用重连 | `BusReconnectTest`：BusUnavailableException 触发重连不吞消息 |

### 运行命令

```bash
mvn -pl services/gnex-state-plane-service test "-Dtest=StatePlane*Test"
```

## §7 影响域

- 作用范围：**全平台**（服务级；状态面为 CQRS 冷路径事实源）。
- 被谁消费：observability（物化读模型）、UI（会话恢复）、恢复协议（checkpoint 权威基线）。
- 故障影响半径：状态面宕机不影响 Agent 热路径（MQ 积压）；物化延迟 = 观测/恢复滞后。
- 升级影响：projector 增删无感（插件化）；读模型 schema 变更走迁移。

## §8 后续（未纳入首版）

- 三层恢复在现网落地（当前 checkpoint/audit/token 仍引擎同步直写，依赖总线 RocketMQ 传输就绪）
- 检索库演进（ES：审计 >5000 万行）
- projector 热插拔验证（新增维度 topic 走插件注册）

---

*GNEX3.0 企业状态面插件 SDD。*
