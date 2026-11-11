# v3-spec-sandbox-pool

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.9
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-sandbox-pool
> **分层**：L3 能力层（3.9 沙箱与执行）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：sandbox-runtime（gnex-sandbox-runtime）
> **实现状态**：规划中

---

## §1 业务目标
沙箱池/集群/弹性插件：保留并插件化 gnex2.0 更强的池与集群能力——warm-pool 快路径分配（Redis 原子弹出）与慢路径创建、水位巡检/补货/回收、容量守卫、调度器多副本（Redis 心跳、Leader 守卫单写者、实例注册）、L3 检查点（Docker 文件备份 + 元数据索引）与 L1-L4 故障分级恢复。目标：执行平面高可用、弹性伸缩、会话续接（checkpoint/恢复）由平台吸收（设计尺子「可靠性内建」），工具/会话对池不可见。dsh 无此能力（个人本机形态），GNEX 保留并强化为企业级差异点。

**非目标**：不做分配裁决（门禁归 sandbox-policy 的 GateChain）；不做执行抽象（归 seam）；不做跨服务调度（走既有 sandbox_request / sandbox_events 总线）。

## §2 映射关系
| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | 无（dsh 无池/集群/checkpoint 平面） |
| 对标 GNEX2.0 模块 | `gnex-sandbox-runtime` pool/*（PoolAllocator / WarmPathReplenisher / PoolReconcileLoop / PoolRecycler / PoolCapacityGuard）+ cluster/*（SchedulerHeartbeatJob / SchedulerLeaderGuard / SchedulerInstanceRegistry）+ checkpoint/*（CheckpointService / CheckpointStorageAdapter）+ fault/*（FaultClassifier L1-L4）+ registry/*（RedisSandboxRegistry） |
| 技术选型 | 🅶 gnex2.0 池/集群/检查点保留改造为插件（企业差异点） |
| 改造要点 | ① warm-pool 快路径（Redis 原子弹出）+ 慢路径创建保留 ② 水位巡检/补货/回收 + 容量守卫保留 ③ 调度器多副本（心跳/Leader/优雅停机）保留 ④ L3 检查点（MinIO/本地双存储）保留 ⑤ 故障 L1-L4 分级恢复保留 ⑥ 指标空样本不得报 1.0（对齐 A 观测诚实） |

## §3 插件接口（契约）
### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `sandboxPool` | `com.gnex.plugin.sandbox.pool.SandboxPoolService` | 池/集群内部服务（宿主级单例，不进 05 公共目录） |

### 3.2 注入服务（inject）

- 必选：`sandbox`（seam，执行后端）、`sandboxPolicy`（门禁）、`ctx`、`events`、`settings`、`logger`
- 可选：`scope`（租户亲和校验）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `sandbox/ready` · `sandbox/destroyed` | emit | 池分配/销毁生命周期（sandbox_events 对齐） |
| `sandbox/error` · `sandbox/recovering` | emit | 故障分级与恢复（对齐 gnex2.0） |
| `sandbox/mode` | listen | 档位切换 → 池水位/容量策略联动 |

## §4 配置与装配
```yaml
# service-assembly.yml（sandbox-runtime）
plugins:
  - id: gnex3-cap-sandbox-pool
    load: mandatory
sandbox:
  pool:
    buffer-min: 2
    buffer-max: 10
    pool-max: 50
  checkpoint:
    storage-type: minio   # minio | local
  scheduler:
    enabled: true         # 多副本集群开关
```

## §5 边界（禁止 ❌ / 必须 ✅）
- ✅ 必须保留 warm-pool 快路径（Redis 原子弹出）+ 慢路径创建，命中率可观测
- ✅ 必须保留水位巡检/补货/回收与容量守卫（bufferMin ≤ bufferMax ≤ poolMax 入 preflight）
- ✅ 必须保留调度器多副本：Redis 心跳、Leader 守卫（单写者）、实例注册、优雅停机
- ✅ 必须保留 L3 检查点与 L1-L4 故障分级恢复（恢复编排不得静默跳过）
- ✅ 池指标空样本不得报 1.0（命中率需带样本数，对齐 A）
- ❌ 池/集群不得暴露为公共服务（对外只见 sandbox / sandboxPolicy，05 服务目录）
- ❌ 不得跨租户借用沙箱（CrossTenantGuard；串租户 = P0）
- ❌ 容量不足不得静默排队不报（QUOTA_EXCEEDED / POOL_EXHAUSTED → 429）

## §6 验收
| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | warm-pool 快路径分配 | `WarmPoolTest`：Redis 弹出命中，未命中走慢路径创建 |
| V2 | 水位补货/回收 | `PoolReconcileTest`：低于 bufferMin 补货、超保留期回收 |
| V3 | 容量守卫 | `PoolCapacityGuardTest`：超 poolMax 拒绝 → POOL_EXHAUSTED |
| V4 | Leader 单写者 | `ClusterLeaderTest`：双副本心跳，Leader 切换不双写 |
| V5 | L3 检查点 | `CheckpointTest`：Docker 备份 + 索引，MinIO/local 双存储 |
| V6 | 故障分级恢复 | `FaultRecoveryTest`：L1/L2/L4 各走对应恢复编排 |
| V7 | 指标样本数 | `PoolMetricsTest`：空样本命中率不报 1.0 |

### 运行命令

```bash
mvn -pl services/gnex-sandbox-runtime test "-Dtest=WarmPoolTest,PoolReconcileTest,PoolCapacityGuardTest,ClusterLeaderTest,CheckpointTest,FaultRecoveryTest,PoolMetricsTest"
```

## §7 影响域
- 作用范围：**服务进程级**（sandbox-runtime）；被 seam/工具调用与 agent-service（ensureReady）消费。
- 故障影响半径：池/集群故障 = 沙箱分配不可用（QUOTA/POOL 429），不影响 Agent 主循环与已分配沙箱。
- 升级影响：池参数变更 = 热生效；checkpoint 格式变更 = 迁移（版本化）。

## §8 后续（未纳入首版）
- 池弹性扩缩（consumer_lag 类比：分配延迟驱动水位扩缩）
- 跨集群池（多 sandbox-runtime 实例共享 warm-pool，对齐 E31 弹性伸缩）
---

*GNEX3.0 沙箱池/集群/弹性 — gnex2.0 warm-pool + checkpoint + 故障分级插件化 SDD。*
