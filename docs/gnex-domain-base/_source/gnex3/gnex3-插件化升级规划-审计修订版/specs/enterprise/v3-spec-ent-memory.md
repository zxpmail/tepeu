# v3-spec-ent-memory

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-memory
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-memory
> **分层**：企业层（L4）
> **受保护级别**：protected（平台持有，禁租户/Agent 卸载）
> **宿主服务**：state-plane-service + platform-service（存储 SoR；副本只做计算）
> **实现状态**：规划中

---

## §1 业务目标

企业记忆 SoR（目标态，现网未达标）：把 gnex2.0 本机 `~/.gnex/memory` + 进程 `MemoryIndex`（多副本不一致、无租户 RLS、难审计/删除传播/容灾）升级为**热/温/冷三平面共享存储**——任意副本读写同一真相（**禁 sticky-replica** 才记得住）；scope 先硬过滤再打分；删除三路传播（温行 + 冷对象 + 热缓存失效）；可审计/可导出/可备份；写入闸（显式意图 + 长度上限/类型白名单 + 可审计可删，**禁止 AutoMemory 盲写执行摘要**）。这是 P0-b 路径 B（真平面）的存储底座；checkpoint Redis 禁混称记忆平面。

**非目标**：不做记忆提供方语义层（shared/project/personal/agent 归 ent-memory-providers）；不做召回打分/注入（provider 层）；本插件只保证三平面 SoR 的一致性、隔离、传播与审计。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-session-reference`（弱类比：仅跨会话引用的启发，dsh 无企业记忆平面） |
| 对标 GNEX2.0 模块 | 记忆 SoR 设计文档（`gnex-core-v2/docs/v3.0/运行时/企业记忆SoR与多副本.md`，目标态；现网 `GnexMemoryService`/`MemoryIndex`/`FileMemoryStore` 被明确定性「不合格」） |
| 技术选型 | 🅽 全新实现（企业记忆 SoR 存储底座，无现网等价物） |
| 改造要点 | ① 三平面唯一存储 SoR：热 Redis（短 TTL 缓存，**非权威**）+ 温 DB（条目元数据/scope/权限，RLS）+ 冷对象存储（正文，URI 读）② 多副本一致性：任意副本读写同一真相，副本无本地状态 ③ 删除/回滚传播管线（温行 + 冷对象 + 热缓存失效）④ 写入闸过滤 AutoMemory 盲写 ⑤ 数据层变更单纪律：无变更单不改码冒充达标 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `memory` | `com.gnex.plugin.enterprise.memory.EnterpriseMemorySoR` | 三平面读写：write/read/recallCandidates/delete/promote/rollback/export，scope 硬过滤内建 |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`tenant`、`storage`、`statePlane`、`audit`
- 可选：`credentials`（冷对象连接密钥引用）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `memory/recall` | emit | {tenantId, projectId, uid, plan, hits}：一次召回（含「未召回/召回未注入」可观测） |
| `memory/promote` | emit | {entryId, from, to, auditId}：personal→project→shared 动作 |
| `memory/rollback` | emit | {entryId, toVersion, auditId}：回滚（可删可回滚，冲突不静默覆盖） |

## §4 配置与装配

```yaml
# service-assembly.yml 片段（state-plane-service）
plugins:
  - id: gnex3-ent-memory
    load: mandatory
    protection: platform
settings:
  memory:
    personalDraftsEnabled: false   # ADR-016：默认关闭，重开须落温+冷
    hotTtlSeconds: 300             # 热缓存短 TTL，非权威
    warmRls: true                  # 温库 RLS 或服务端硬隔离
    coldBucket: gnex-memory        # 冷对象桶
    writeGate: { maxLengthBytes: 4096, typeWhitelist: [preference, conclusion, todoNote, projectFact, agentAsset] }
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 任意副本读写必须命中同一真相（禁 sticky-replica 才记得住；副本只做计算）
- ✅ 必须三平面：热 Redis（短 TTL 召回缓存，**非权威**）+ 温 DB（元数据/scope/权限，RLS）+ 冷对象（正文）
- ✅ 删除必须传播：温行 + 冷对象 + 热缓存失效（三路）
- ✅ 必须可审计、可导出、可备份；`~/.gnex` 仅 dev，不得当企业权威
- ✅ 必须写入闸：显式意图 + 长度上限/类型白名单 + 可审计可删（禁止盲写）
- ❌ checkpoint Redis 禁混称记忆平面（checkpoint/会话热状态 ≠ 记忆正文与索引）
- ❌ 禁止 AutoMemory 盲写执行摘要/工具流水/失败堆栈（写入闸硬拦）
- ❌ 禁止 sticky-replica（请求打 B 必须读到 A 写入的同一真相）
- ❌ 禁止串租户/串项目召回（scope 先硬过滤再打分，= P0）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 多副本同一真相 | `SoRMultiReplicaTest`：副本 A 写 → 副本 B 读到同一条目（无 sticky-replica） |
| V2 | 三平面读写 | `SoRThreePlaneTest`：温元数据 + 冷正文 + 热缓存命中，热缓存丢失可回源 |
| V3 | 删除传播 | `SoRDeletePropagationTest`：删温行 → 冷对象消失 → 热 key 失效 |
| V4 | scope 硬过滤 | `SoRScopeFilterTest`：跨租户候选为空（先过滤后打分） |
| V5 | 写入闸拦截盲写 | `SoRWriteGateTest`：无显式意图/超长/非白名单 → 拒绝且可审计 |
| V6 | promote/rollback | `SoRPromoteRollbackTest`：promote 写温/冷新 scope；rollback 回版本，冲突不静默覆盖 |
| V7 | checkpoint 不混称 | `SoRCheckpointNotMemoryTest`：checkpoint key 不入记忆召回候选 |

### 运行命令

```bash
mvn -pl services/gnex-state-plane-service test "-Dtest=SoRMultiReplicaTest,SoRThreePlaneTest,SoRDeletePropagationTest,SoRScopeFilterTest,SoRWriteGateTest,SoRPromoteRollbackTest,SoRCheckpointNotMemoryTest"
```

## §7 影响域

- 作用范围：**租户级/全平台**——所有记忆读写路径。
- 被谁消费：ent-memory-providers（语义层）、agentLoop 召回、审计/导出工具。
- 故障影响半径：一致性缺陷 = 假记忆（P0-b 未过）；泄漏 = P0；存储拓扑变更 = 数据迁移。
- 升级影响：🔒 平台级升级 + 灰度；数据层变更单门禁（无变更单不改码）。

## §8 后续（未纳入首版）

- 向量检索平面（pgvector → Milvus 演进，检索归 provider 层）
- 跨副本延迟/一致性观测（召回三态指标接入 observability）
- 备份/恢复演练（冷对象 + 温库快照恢复 SOP）

---

*GNEX3.0 企业记忆 SoR — 热温冷三平面多副本 SDD。*
