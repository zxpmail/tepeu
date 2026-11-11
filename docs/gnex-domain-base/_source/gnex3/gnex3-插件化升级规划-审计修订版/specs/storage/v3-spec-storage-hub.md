# v3-spec-storage-hub

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.3
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-storage
> **分层**：L3 能力层（3.3 存储与凭证）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：各 gnex 服务（agent-service / registry-service / platform-service / state-plane-service 等）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-02/D-P1-33（验收模块标注）

---

## §1 业务目标

存储 hub 是 GNEX3.0 领域数据（domain）的统一访问面：插件以「domain 数据形式」声明持久化形态（KV/结构化/文件/向量…），hub 维护 backend 注册表并按「租户 + domain」路由到已注册后端。继承 gnex2.0 `gnex-persistence-common` 的引擎可移植纪律（E16：切换引擎 = 换实现类，业务零改动）与四层租户隔离（E15：PG RLS / Redis key 前缀 / MinIO 租户桶 + IAM / 拦截器兜底），让领域插件（会话持久化/凭证/附件/工作区/checkpoint）零感知底层引擎。对标 dsh-storage / dsh-storage-domain 语义，以「backend 注册表 + domain 路由」适配多引擎企业部署，并统一提供事务/扫描/分布式锁等原语。

**非目标**：hub 不实现具体引擎（PG/Redis/MinIO 由后端插件提供）；不做跨服务存储代理（跨服务走既有 REST/Bus）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-storage` + `dsh-storage-domain`（domain 数据形式声明） |
| 对标 GNEX2.0 模块 | `gnex-persistence-common`（`StateInterface` / `FileStorePort` / `DatabaseDialect` / `ConfigurableStorageProvider` / `PortRegistry` / `IdGenerator`） |
| 技术选型 | 🅳 Java 移植 dsh 实现（TypeScript 语义 → Java 接口 + 注册表） |
| 改造要点 | ① Domain 数据形式声明（form/schema/version）② Backend SPI（`StorageBackend`：get/set/scan/query/事务/锁）③ 按 tenant+domain 路由与后端优先级 ④ 复用 `DatabaseDialect` / `IdGenerator`（Snowflake，E18）/ `TenantInterceptor` 作为默认适配基线 ⑤ domain 变更 emit `domain/changed` |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `storage` | `com.gnex.plugin.storage.StorageService` | 领域数据访问门面：domain 注册/注销、backend 路由、CRUD/scan/事务/锁 |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`settings`、`logger`
- 可选：`credentials`（后端连接密钥经凭据服务引用解析）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `domain/changed` | emit | {domain, form, backend, schemaVersion}：domain 注册/注销/变更 |
| `credentials/updated` | listen（可选） | 后端密钥轮换时刷新连接（{credentialId, namespace}） |

## §4 配置与装配

```java
// StorageHubProperties（prefix: gnex.storage）
public class StorageHubProperties {
  boolean autoRegisterDefaultBackends = true; // 无显式后端时装配内置实现
  String defaultBackend = "pg";               // 默认结构化后端
  boolean domainValidation = true;            // domain schema 校验
}
```

```yaml
# service-assembly.yml 片段
plugins:
  - id: gnex3-cap-storage
    load: mandatory          # 领域插件的前置
  - id: gnex3-cap-storage-pg # 后端可装卸，缺省时回退内置
    load: optional
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 必须提供 domain 注册/注销与 backend 注册表，按「租户 + domain」路由
- ✅ 必须继承 E15 四层租户隔离（RLS / key 前缀 / 桶+IAM / 拦截器兜底），hub 不得绕过
- ✅ 后端切换必须无感：domain 数据形式不变则引擎可换（E16）
- ✅ domain 增删改必须 emit `domain/changed`，供后端与消费方响应
- ❌ hub 不得在编译期依赖具体引擎驱动（PG/Redis/MinIO 依赖走后端插件）
- ❌ hub 不得做跨服务 RPC（跨服务存储走既有 REST/Bus）
- ❌ hub 不得替代 Spring 事务/ORM 语义（事务由后端实现）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | domain 注册后 CRUD 路由到默认后端 | `StorageHubTest`：registerDomain → set/get 往返一致 |
| V2 | backend 注册表增删后端 | `StorageBackendRegistryTest`：register/unregister 后路由切换 |
| V3 | domain 变更事件 | `DomainEventTest`：register/update/unregister 各 emit 一次 `domain/changed` |
| V4 | 租户隔离 | `StorageTenantIsolationTest`：tenantA 数据对 tenantB 不可见 |
| V5 | 引擎可移植 | `BackendSwitchTest`：同 domain 在 pg ↔ sqlite 后端数据一致 |
| V6 | 密钥轮换刷新 | `CredentialRotationTest`：`credentials/updated` 后后端连接重建 |

### 运行命令（审计修订 D-P1-02/D-P1-33）

```bash
# 目标模块（待 S 阶段新建，规划为 libs/gnex-plugin-storage 单模块——存储域多插件同模块，D-P1-02 裁决）
mvn -pl libs/gnex-plugin-storage test "-Dtest=StorageHubTest,StorageBackendRegistryTest,DomainEventTest,StorageTenantIsolationTest,BackendSwitchTest,CredentialRotationTest"
```

## §7 影响域

- 作用范围：**服务进程级**；被领域插件（session-persist / credentials / attachment / workspace / checkpoint）消费。
- 故障影响半径：hub 不可用 = 全部领域数据访问不可用 → V1-V6 为发布门。
- 升级影响：domain 数据形式变更 = 数据迁移（schemaVersion 版本化）；后端增删 = 无感。

## §8 后续（未纳入首版）

- 跨服务存储代理（远程 hub，经 gnex3-ent-bus）
- 检索/向量后端注册（对接 `SearchInterface` / `VectorStoreInterface`）
- domain schema 迁移工具（集成 `SchemaMigrationRunnerV2`）

---

*GNEX3.0 存储 hub — backend 注册表 + domain 数据形式 SDD。*
