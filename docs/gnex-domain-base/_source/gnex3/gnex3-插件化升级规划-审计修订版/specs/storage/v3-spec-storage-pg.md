# v3-spec-storage-pg

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.3
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-storage-pg
> **分层**：L3 能力层（3.3 存储与凭证）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：各 gnex 服务（agent-service / registry-service / platform-service / state-plane-service 等）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-02/D-P1-33（验收模块标注）

---

## §1 业务目标

PG 后端把 storage hub 的领域数据落到 PostgreSQL 16 结构化库：domain 数据形式映射为表，schema 由迁移版本管理。对标 dsh-storage-json 的「具体后端」定位（dsh 以 JSON 文件实现本地存储，GNEX 以关系表 + 迁移替代），继承 gnex2.0 MyBatis-Plus 持久层：`DatabaseDialect` 方言自动探测（PG/SQLite）、`PgRlsInitializer` 租户 RLS、`PgHaAdapter` 高可用、`SchemaMigrationRunnerV2` 迁移。多副本共享同一结构化库是 E1（无状态副本）与 E15（租户隔离）的前提；checkpoint 7 天、成本/质量 30 天等 TTL 策略沿用。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-storage-json`（具体后端实现定位） |
| 对标 GNEX2.0 模块 | MyBatis-Plus 持久层：`gnex-persistence-common`（`DatabaseDialect` / `PgRlsInitializer` / `PgHaAdapter` / `SchemaMigrationRunnerV2` / `IdGenerator`）+ 各服务 Mapper |
| 技术选型 | 🅶 gnex2.0 模块改造（MyBatis-Plus 持久层封装为 StorageBackend） |
| 改造要点 | ① 以 `StorageBackend` SPI 实现包装既有 Mapper 数据访问 ② domain → 表映射 + `SchemaMigrationRunnerV2` 迁移版本 ③ `PgRlsInitializer` 租户 RLS（E15）④ Snowflake `IdGenerator`（E18）⑤ 连接口令经 `credentials` 引用 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `storagePg` | `com.gnex.plugin.storage.pg.PgStorageBackend` | PG 后端实现，启动时自注册进 `storage` hub 的 backend 注册表 |

### 3.2 注入服务（inject）

- 必选：`storage`（hub，注册目标）、`ctx`、`events`、`settings`、`logger`
- 可选：`credentials`（PG 连接口令引用，避免明文）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `domain/changed` | listen | 感知 domain 注册 → 建表/校验 schema（{domain, schemaVersion}） |
| `credentials/updated` | listen（可选） | PG 口令轮换后重建数据源 |

## §4 配置与装配

```java
// StoragePgProperties（prefix: gnex.storage.pg）
public class StoragePgProperties {
  String url;               // jdbc:postgresql://…（缺省经 DatabaseDialect 探测）
  String credentialRef;     // ${credentials:pg-admin}，禁明文口令
  boolean enableRls = true; // 租户 RLS（PgRlsInitializer）
  int maxPoolSize = 20;
}
```

```yaml
# service-assembly.yml 片段
storage:
  backends:
    - id: pg
      plugin: gnex3-cap-storage-pg
      priority: 0     # 默认结构化后端
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 必须经 `SchemaMigrationRunnerV2` 管理 schema，domain 表变更走迁移版本前滚
- ✅ 生产必须启用 `PgRlsInitializer` 租户 RLS（E15）；开发无 PG URL 时 `DatabaseDialect` 回退 SQLite
- ✅ 连接口令必须走 `credentials` 引用或环境变量，禁止明文配置
- ✅ 复用 `PgHaAdapter`（<30s 切换）与 Snowflake `IdGenerator`
- ❌ 业务插件禁止绕过 hub 直连本后端 Mapper（统一走 `storage` 服务）
- ❌ 后端内不得自造跨引擎方言（SQL 方言归 `DatabaseDialect`）
- ❌ 不得在 PG 大字段存附件正文（正文走对象存储，见 attachment 插件）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | domain → 表建表 | `PgSchemaMigrationTest`：registerDomain 后表存在且迁移版本递增 |
| V2 | CRUD 往返 | `PgCrudTest`：set/get/scan 一致性（含类型化 domain） |
| V3 | RLS 租户隔离 | `PgRlsTest`：设置 `app.current_tenant` 后跨租户查询为空 |
| V4 | hub 路由 | `PgRouteTest`：hub 将 domain 路由到 pg 后端（priority 生效） |
| V5 | SQLite 回退 | `DialectFallbackTest`：无 PG URL 自动 SQLite，业务断言不变 |
| V6 | 口令引用 | `PgCredentialRefTest`：credentialRef 解析成功且配置无明文 |

### 运行命令（审计修订 D-P1-02/D-P1-33）

```bash
# 目标模块（待 S 阶段新建，规划为 libs/gnex-plugin-storage 单模块——存储域多插件同模块，D-P1-02 裁决；不再单设 gnex-plugin-storage-pg 模块）
mvn -pl libs/gnex-plugin-storage test "-Dtest=PgSchemaMigrationTest,PgCrudTest,PgRlsTest,PgRouteTest,DialectFallbackTest,PgCredentialRefTest"
```

## §7 影响域

- 作用范围：**服务级 + 租户级**（结构化温库）。
- 被谁消费：storage hub 及全部领域插件（session-persist / checkpoint / credentials / workspace 元数据）。
- 故障影响半径：PG 不可用 = 结构化 domain 不可用（`PgHaAdapter` <30s 切换）。
- 升级影响：迁移脚本前滚 = 服务重启；方言/RLS 变更 = 全租户行为。

## §8 后续（未纳入首版）

- 只读副本 / 读写分离（HA 扩展）
- TimescaleDB 时序扩展（checkpoint 温库趋势）
- 大数据量演进 Doris（E16 触发线：token_usage > 1 亿行等）

---

*GNEX3.0 存储 PG 后端 — 结构化领域数据 SDD。*
