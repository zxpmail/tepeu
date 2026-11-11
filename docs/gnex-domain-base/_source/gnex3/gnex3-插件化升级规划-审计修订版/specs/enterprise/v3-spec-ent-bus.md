# v3-spec-ent-bus

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-bus
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-bus
> **分层**：企业层（L4）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：bus-service（SDK 嵌入各业务服务 + admin 治理）
> **实现状态**：规划中

---

## §1 业务目标

跨服务消息总线：Java SDK（嵌入各业务服务）+ RocketMQ 5.x 集群 + admin 治理微服务三件套，全部直连 broker 互不转发。提供 BusInterface 全族（produce/produceAsync/produceBatch/produceScheduled/produceAt/produceInTransaction/subscribe/unsubscribe）与统一信封（MessageEnvelope：msgId/dedupId/orderKey/filter/properties.tenantId+traceId），保证 at-least-once + 消费者幂等 = effectively-once，同 orderKey 强顺序，事务消息保证「本地写+发消息」原子性。插件化价值：`bus` 服务成为跨服务事件通道的唯一出口，RocketMqTransport 为一等实现，过渡传输可切换。

**非目标**：不做 payload 解析/消息存储/全局 retry 状态机（SDK 定位，RocketMQ 是消息事实源）；不做 exactly-once；不承载 Action→Observation 原语通道（走 WebSocket）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | 无（dsh 无此原语；cordis events 为进程内总线，非跨服务） |
| 对标 GNEX2.0 模块 | bus-service：transport `RocketMqTransport` + `RocketMqAdminService`（现网已有）+ gnex-contracts `BusInterface`/`MessageEnvelope`/`StandardTopics`（11 topic）+ admin（死信 requeue/订阅 pause-resume/reset-offset/消息检索/治理审计） |
| 技术选型 | 🅶 GNEX2.0 模块改造为插件（transport 族 → bus 服务） |
| 改造要点 | ① 插件化：`bus` 提供 BusInterface + BusAdminInterface，RocketMqTransport 为一等实现，database/Redis-Streams/in-process 保留为过渡传输；② 可靠性语义固化：at-least-once + 幂等消费 + 原生重试/DLQ（18 级延迟，%DLQ%{group} 72h，maxReconsumeTimes=16）；③ 事务消息（produceInTransaction + LocalTransactionExecutor 回查）用于 cost_events/audit_log；④ 命名硬规则校验（snake_case、禁点号、Topic=业务类别、filter=UPPER_SNAKE_CASE、消费组=角色）；⑤ 信封校验：payload ≤256KB 超限走 blob:// 旁路，缺 tenantId/traceId 拒绝 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `bus` | `com.gnex.contracts.bus.BusInterface`（produce/produceAsync/produceBatch/produceInTransaction/subscribe/unsubscribe）+ `BusAdminInterface`（管理查询） | 跨服务消息通道 |

### 3.2 注入服务（inject）

- `storage`（可选）：过渡传输后端（database/Redis-Streams）与 blob:// 旁路
- `scope` / `settings` / `events`（必选）：租户 realm、topic/group 配置、发布/消费观测
- `audit`（可选）：治理审计（GovernanceAuditStore）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `bus/published` | emit | {topic, msgId, orderKey, tenantId, traceId} 发布成功（观测/审计） |
| `bus/consumed` | emit | {topic, msgId, group, result: SUCCESS/RETRY} 消费处理结果（观测） |
| `audit/record` | emit | 治理操作审计（requeue/pause/reset-offset） |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-ent-bus
    load: mandatory
    config:
      gnex.bus.transport: rocketmq              # rocketmq | database | redis-streams | in-process
      gnex.bus.nameserver: ${ROCKETMQ_NAMESERVER}
      gnex.bus.envelope.max-payload-bytes: 262144  # 256KB，超限走 blob:// 旁路
      gnex.bus.consume.max-reconsume: 16        # 耗尽进 DLQ
      gnex.bus.admin.enabled: true              # 治理面（不在热路径）
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 消息必须统一信封（tenantId/traceId 必填），payload ≤256KB 超限走 blob:// 旁路
- ✅ at-least-once + 消费者幂等（msgId/dedupId）= effectively-once；禁止承诺 exactly-once
- ✅ 同 orderKey 内强顺序（ORDERLY），多副本扩缩不破坏顺序（组内单副本消费）
- ✅ 事务消息保证「本地写+发消息」原子性（cost_events/audit_log，broker 回查）
- ✅ 命名硬规则：snake_case、禁点号、Topic=业务类别（禁 tenant/entity 高基数）、filter=UPPER_SNAKE_CASE、消费组=角色（禁带 tenant）
- ❌ bus 不得解析 payload / 不存消息 / 不建全局 retry 状态机（SDK 定位，RocketMQ 是消息事实源）
- ❌ 不得因总线不可用静默吞消息（BusUnavailableException 必须重连/显式降级）
- ❌ admin 治理不得进入热路径；租户不得绕过 topic 注册校验与 7 步入场评审

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 发布/订阅 | `BusProduceConsumeTest`：produce → subscribe 收到，orderKey 顺序保持 |
| V2 | 事务消息 | `BusTransactionTest`：本地写 + 发消息原子，回查 UNKNOWN → 最终一致 |
| V3 | 幂等消费 | `BusIdempotentTest`：同 msgId 重投消费者只处理一次 |
| V4 | 顺序消费 | `BusOrderlyTest`：同 orderKey 强顺序，多副本扩缩不破坏 |
| V5 | 重试/DLQ | `BusDlqTest`：maxReconsumeTimes=16 耗尽进 %DLQ%{group}，admin 可 requeue |
| V6 | 信封校验 | `EnvelopeValidationTest`：缺 tenantId/traceId 拒绝；>256KB 走 blob:// 旁路 |
| V7 | 命名规则 | `TopicNamingTest`：snake_case/禁点号/高基数 Topic 注册被拒 |

### 运行命令

```bash
mvn -pl services/gnex-bus-service test "-Dtest=Bus*Test"
```

## §7 影响域

- 作用范围：**全平台**（服务级）——所有跨服务事件通道（sandbox/agent/cost/quality/a2a/audit）。
- 被谁消费：全部业务服务（SDK 嵌入）、state-plane（物化订阅）、observability（管理查询）、ops（治理）。
- 故障影响半径：broker 不可用 = 跨服务事件中断（热路径原语走 WebSocket 不受影响）；顺序破坏 = 会话恢复错乱。
- 升级影响：传输切换（RocketMQ 一等）灰度；topic 新增走 7 步入场评审 + 注册模板。

## §8 后续（未纳入首版）

- 总线消息检索全文（消息体索引）
- consumer_lag 驱动的弹性扩容指标接入 HPA（E31 对齐）
- 过渡传输退役（database/Redis-Streams 在 RocketMQ 全面就绪后下线）

---

*GNEX3.0 企业消息总线插件 SDD。*
