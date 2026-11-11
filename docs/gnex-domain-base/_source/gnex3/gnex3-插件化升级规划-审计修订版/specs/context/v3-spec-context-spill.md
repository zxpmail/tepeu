# v3-spec-context-spill

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.10
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-spill
> **分层**：L3 能力层（3.10 上下文与压缩）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service（gnex-agent-service）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-28

---

## §1 业务目标
溢出存储插件：把超出上下文预算的内容（大工具输出、长文档正文）溢出到外部存储，进模时只放引用/摘要，需要时按引用取回（对齐 dsh-spill / dsh-spill-local / dsh-spill-policy）。与 pruner/compaction 协作构成上下文溢出链：pruner 修剪（原文先经 spill 服务 API 移交）→ spill 存原文 → compaction 压缩全局。存储走对象存储（MinIO，数据层 blob:// 语义，E15 租户桶隔离），TTL 策略强制回收，禁止无限增长。gnex2.0 无对应模块（新增）。明确边界：spill 是上下文溢出存储，不是企业记忆 SoR（P0-b 禁止混称）。

**非目标**：不做修剪/压缩本体（归 pruner/compaction）；不做企业记忆平面（归 gnex3-ent-memory）；不做附件/产物存储（归 gnex3-cap-attachment）。

## §2 映射关系
| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-spill` + `dsh-spill-local` + `dsh-spill-policy`（溢出存储 + TTL 策略） |
| 对标 GNEX2.0 模块 | 无（新增）；复用 `gnex-persistence-common`（FileStorePort / LocalFileStore / MinIO 桶）+ `gnex-agent-persistence`（SessionArchive / SessionArtifact） |
| 技术选型 | 🅳 Java 移植 dsh 实现 |
| 改造要点 | ① spill 服务：put/get/delete + 引用句柄（spill://<key>）② 存储后端 = FileStorePort（MinIO/本地，E16 引擎可移植）③ TTL 策略（dsh-spill-policy 语义，过期清理）④ 租户桶隔离（E15：gnex-{tenantId}-artifacts）⑤ 与 pruner 协作（D-P1-28）：**监听序 pruner → spill**，pruner 截断前先经 spill 服务 API put 原文，spill 监听器只做引用补记/兜底，进模只放引用 |

## §3 插件接口（契约）
### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `spill` | `com.gnex.plugin.context.SpillService` | 溢出存储：put/get/delete/ttl（引用句柄 spill://；pruner 经此 API 移交原文） |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`settings`、`logger`
- 可选：`storage`（FileStorePort 后端）、`compaction` / `tokenMeter`（体积联动）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/post-execute` | listen | 超大结果 spill。**监听序（D-P1-28）：pruner（先）→ spill（后）**——顺序由管线装载序固化（pruner 先于 spill 装载）；spill 监听器做引用补记/兜底，原文 put 由 pruner 经 spill 服务 API 在截断前完成，防止存残文 |
| `tools/result` | emit | spill 后结果引用（{tool, spillRef, size}） |

> spill 缺席（optional 未装载）时，pruner 自行走 session log 日志回退留痕（见 pruner spec §3.3/§6 V2b）——本插件不承担该回退。

## §4 配置与装配
```yaml
# service-assembly.yml（agent-service）
plugins:
  - id: gnex3-cap-spill
    load: optional
    order: after:gnex3-cap-pruner   # D-P1-28：post-execute 监听序 pruner → spill
context:
  spill:
    backend: minio        # minio | local（FileStorePort）
    ttl: 30d              # 溢出内容保留期
    max-bytes: 1048576    # 单条 spill 上限
```

## §5 边界（禁止 ❌ / 必须 ✅）
- ✅ 必须提供 put/get/delete + 引用句柄（spill://），溢出内容可按引用取回
- ✅ 必须与 pruner 协作（D-P1-28）：**监听序 pruner → spill**；修剪原文由 pruner 经 spill 服务 API 在截断前 put（存全文非残文），进模只放引用/摘要（不静默丢弃）
- ✅ 必须走 FileStorePort 对象存储（MinIO 租户桶，E15），引擎可移植（E16）
- ✅ 必须 TTL 策略强制回收，过期清理（禁止无限增长）
- ❌ 禁止把 spill 当企业记忆 SoR（P0-b：checkpoint/溢出存储 ≠ 温冷记忆平面）
- ❌ 禁止跨租户读取 spill 内容（租户桶隔离，串租户 = P0）
- ❌ 禁止无 TTL / 无上限写入（容量守卫：max-bytes + ttl 强制）
- ❌ 禁止 spill 监听器先于 pruner 执行时对已截断残文补存冒充原文（顺序违约 = 缺陷）

## §6 验收
| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | put/get/delete 往返 | `SpillServiceTest`：spill:// 引用取回一致 |
| V2 | pruner 协作 + 顺序（D-P1-28） | `SpillPrunerTest`（含顺序断言）：post-execute 监听序 pruner 先于 spill；修剪原文落 spill 为**截断前全文**，进模为引用 |
| V3 | TTL 回收 | `SpillTtlTest`：过期条目被清理任务回收 |
| V4 | 租户隔离 | `SpillTenantTest`：tenantA 引用对 tenantB 不可读 |
| V5 | 后端可移植 | `SpillBackendSwitchTest`：minio ↔ local 数据一致（E16） |
| V6 | 容量守卫 | `SpillCapacityTest`：超 max-bytes 拒绝 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=SpillServiceTest,SpillPrunerTest,SpillTtlTest,SpillTenantTest,SpillBackendSwitchTest,SpillCapacityTest"
```

## §7 影响域
- 作用范围：**会话级**（上下文组装）；被 pruner/compaction/工具管线消费。
- 故障影响半径：spill 不可用 = 大结果走降级（不 spill 直接截断，留痕日志），不影响主流程。
- 升级影响：引用格式变更 = 历史引用兼容（版本化句柄）；后端增删 = 无感。

## §8 后续（未纳入首版）
- 引用自动物化（进模前按需取回预热，降低取回延迟）
- 与附件/产物存储融合（gnex3-cap-attachment 共享桶与生命周期）

---

*GNEX3.0 溢出存储 — dsh-spill Java 移植（上下文溢出链：pruner → spill → compaction；监听序与原文移交语义 D-P1-28）SDD。*
