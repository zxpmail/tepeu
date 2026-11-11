# v3-spec-ent-ha

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-ha
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-ha
> **分层**：企业层（L4）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：platform-service / registry-service（Master 侧协调）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P0-02、D-P1-38（§2 映射拆分「代码实证来源 / 设计规格来源」两栏；§6 加「先落 Worker 注册/心跳代码基线」前置条件）

---

## §1 业务目标

多副本/弹性/心跳/重调度的平台底座：继承 gnex2.0 分布式规格（Worker 注册写 PG、心跳 5s 写 Redis key `worker:heartbeat:{workerId}` TTL 15s、Master 10s 扫描、连续 3 次未心跳 → UNREACHABLE → 重调度、retryCount ≤ 3、DRAINING 优雅下线、HPA CPU 70% min2/max6、consumer_lag 驱动扩容），插件化为 `ha` 服务：副本只做计算、状态全部外化（E1/E2/E4），Worker 生命周期事件化（注册/失联/排空/重调度），并保持租户隔离（`agent:task:{tenantId}` 分流 + Worker label 亲和，联动 ent-tenant）。

**非目标**：不做 A2A 协议与 AI 调度（第二版，E27 留接口）；不做消息总线本身（归 ent-bus）；k8s HPA/探针为运维装配（对齐 E32，manifest 属部署物）。

## §2 映射关系

> **审计修订（D-P0-02/D-P1-38）**：`WorkerRegistry` / `TaskScheduler` / `HealthMonitor` / `RedisActiveRunSnapshotStore` / `ActiveRunRegistry` 五个类名在 `_work/gnex-services-analysis.md` 九服务代码分析中**零命中**——它们来自分布式规格（设计文档），属**设计规格来源**，非代码实证；落地前不得作为验收基线。

| 维度 | 代码实证来源（源码可溯） | 设计规格来源（设计态/待落地） |
|------|--------------------------|------------------------------|
| 对标 dsh 插件 | dsh 无此原语（dsh 无多副本/心跳/重调度平台设施） | — |
| 对标 GNEX2.0 模块 | k8s mode-a-cluster HPA（E31/E32）与 nginx LB 双副本（E36）等部署物 | **[设计规格来源]** 分布式规格：`WorkerRegistry`（Worker 注册写 PG）/ `TaskScheduler`（重调度）/ `HealthMonitor`（心跳 5s 写 Redis key `worker:heartbeat:{workerId}` TTL 15s、Master 10s 扫描、连续 3 次未心跳 → UNREACHABLE）/ `RedisActiveRunSnapshotStore` + `ActiveRunRegistry`（E2 跨副本续接）——五者均为设计文档类名，代码零命中，**先落代码基线**（见 §6 前置条件） |
| 技术选型 | 🅶 GNEX2.0 模块改造 | WorkerRegistry/HealthMonitor 保留、事件化 + 插件装配（设计态方案的实现承接） |
| 改造要点 | ① Worker 生命周期事件化（`ha/worker-*`）② 心跳/注册/重调度收敛为 `ha` 服务（Redis + PG 双通道）③ 重调度幂等（retryCount 上限 3）④ DRAINING 优雅下线闭环 ⑤ consumer_lag 扩容信号接入 observability（E31） | 上述要点均以设计规格来源列的五个类先落地为前提；ha/* 事件已在 05 §2 词汇表回填（D-P1-40） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `ha` | `com.gnex.plugin.enterprise.ha.HaCoordinator` | Worker 注册/心跳/扫描/失联判定/重调度/排空；状态查询（WorkerTable） |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`tenant`、`bus`
- 可选：`observability`（心跳/滞后指标）、`statePlane`（恢复包联动）、`storage`（注册表持久化）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `ha/worker-registered` | emit | {workerId, host, port, capacity, labels} |
| `ha/worker-unreachable` | emit | {workerId, missingHeartbeats}：连续 3 次未心跳 |
| `ha/worker-drained` | emit | {workerId}：IN_PROGRESS 完成可安全停 |
| `ha/task-rescheduled` | emit | {taskId, tenantId, retryCount}：重调度（≤3 次） |

## §4 配置与装配

```yaml
# service-assembly.yml 片段（platform-service）
plugins:
  - id: gnex3-ent-ha
    load: mandatory
settings:
  ha:
    heartbeat: { intervalSeconds: 5, ttlSeconds: 15 }
    scanner: { intervalSeconds: 10, missingThreshold: 3 }
    reschedule: { maxRetry: 3 }
    mode: cluster       # standalone 退化为单副本（零配置）
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ Worker 必须注册后消费（PG 注册记录 status=ACTIVE）；心跳必须外化（Redis key + TTL）
- ✅ 连续 3 次未心跳必须标记 UNREACHABLE 并触发任务重调度
- ✅ 重调度必须幂等：retryCount 递增 ≤ 3，达限标记 TASK_FAILED（禁无限重试）
- ✅ 下线必须 DRAINING → 停止新任务 → 等 IN_PROGRESS 完成 → DRAINED（滚动升级不停服）
- ✅ 状态必须外化（副本只做计算）：跨副本续接走 Redis 快照（E2），请求打 A/B 不丢
- ✅ 任务必须按 `agent:task:{tenantId}` 分流 + Worker label 亲和（联动 ent-tenant，串租户 = P0）
- ❌ 禁止 sticky-replica（「该 Worker 才有状态」）；禁止无状态外化的多副本
- ❌ 禁止重调度绕过租户边界/配额；禁止 HPA 扩缩破坏顺序消费（consumer ≤ 队列数）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 心跳超时 → 失联 → 重调度 | `HaHeartbeatTest`：停心跳 >15s → UNREACHABLE → IN_PROGRESS 任务重新入流 |
| V2 | 重调度幂等与上限 | `HaRescheduleCapTest`：retryCount 递增，第 4 次 TASK_FAILED 不重复调度 |
| V3 | DRAINING 优雅下线 | `HaDrainTest`：排空后不消费新任务，IN_PROGRESS 完成后 DRAINED |
| V4 | 跨副本续接 | `HaSnapshotResumeTest`：副本 A 写快照 → 副本 B hydrate 同 run_id（E2） |
| V5 | 租户分流亲和 | `HaTenantAffinityTest`：tenant=A 任务不落非匹配 label Worker |
| V6 | 注册表一致性 | `HaRegistryTest`：注册/心跳写 PG 与 Redis 双通道一致 |
| V7 | standalone 退化 | `HaStandaloneTest`：`gnex.mode=standalone` 单副本零配置可用 |

### 前置条件（审计修订 D-P1-38）

- **先落 Worker 注册/心跳代码基线**：`WorkerRegistry` / `TaskScheduler` / `HealthMonitor` / `RedisActiveRunSnapshotStore` / `ActiveRunRegistry` 为设计规格类名（代码零命中），V1-V7 全部测试对象在 2.0 基线上**尚不存在**——须先按 §2 设计规格来源列落地代码基线（注册写 PG、心跳写 Redis、快照续接），再执行本表验收；未落地前不得宣称任何 V 项达标。
- 落地时同步在 04 目录对应行加「设计态」标注，并将类名升级为代码实证来源（D-P0-02 落点闭环）。

### 运行命令

```bash
mvn -pl services/gnex-platform-service test "-Dtest=HaHeartbeatTest,HaRescheduleCapTest,HaDrainTest,HaSnapshotResumeTest,HaTenantAffinityTest,HaRegistryTest,HaStandaloneTest"
```

## §7 影响域

- 作用范围：**平台级/集群级**——Worker 调度与副本生命周期。
- 被谁消费：所有执行型服务（agent/sandbox-runtime）、ops 集群面板（ClusterPage）、ent-tenant（亲和校验）。
- 故障影响半径：调度缺陷 = 任务丢失/重复执行（at-least-once + 幂等兜底）；Master 无状态可重启恢复。
- 升级影响：心跳/重调度语义变更 = 全集群回归（灰度 + 双副本 dev 发布，E36）。

## §8 后续（未纳入首版）

- A2A 协议与 AI 调度优化（第二版，E27 接口预留）
- HPA 全服务 manifest（auth/ops/registry/bus/state-plane/observability 补 k8s）
- 弹性预算联动（缩容前 DRAINING 与 billing 配额联动）

---

*GNEX3.0 多副本高可用 — 心跳/弹性/重调度 SDD。*
