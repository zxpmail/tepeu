# v3-spec-storage-credentials

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.3
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-credentials
> **分层**：L3 能力层（3.3 存储与凭证）
> **受保护级别**：🔒 protected（平台持有，禁租户/Agent 卸载）
> **宿主服务**：registry-service
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-02/D-P1-33（验收模块标注）

---

## §1 业务目标

集中管理平台全部密钥/凭据：LLM BYOK、MCP 服务器、PG/MinIO 连接、HTTP 工具、OAuth2 客户端。对标 dsh-credentials-local（本地密钥文件）与 gnex2.0 registry-service 的 credential 域（`CredentialService`：AES 加密落 `credential_store`、视图脱敏、LLM BYOK 保留命名空间、旧 `agent_credential` 一次性迁移）。凭据一律以「引用」形式（`${credentials:<name>}`）注入消费方，杜绝明文直配；任何变更 emit `credentials/updated` 驱动消费方刷新。🔒 平台持有：装卸权仅平台管理员，租户/Agent 无权限，跨租户解析被硬隔离。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-credentials-local` |
| 对标 GNEX2.0 模块 | registry-service credential 域：`CredentialService` / `CredentialManagementPort`（视图脱敏）/ `CredentialStore`（AES）/ `LlmByokCredentialAdapter` / `AgentCredentialMigrationService` / `AesEncryptor`；platform 侧 `CredentialHandler`（ApiKey / BasicAuth / OAuth2 注入策略） |
| 技术选型 | 🅶 gnex2.0 模块改造（registry-service CredentialService 插件化） |
| 改造要点 | ① 包为 gnex3 插件形态，装配于 registry-service ② 保留 AES 加密（`AesEncryptor`）与视图脱敏 ③ 引用语法 `${credentials:<name>}` 贯通 settings/后端/工具/MCP ④ 轮换原子更新 + emit `credentials/updated` ⑤ 复用旧 `agent_credential` 一次性迁移（幂等） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `credentials` | `com.gnex.plugin.credentials.CredentialsService` | 密钥 CRUD / 引用解析 / 轮换（视图脱敏、租户硬隔离） |

### 3.2 注入服务（inject）

- 必选：`storage`（凭据 domain 落 PG）、`ctx`、`events`、`settings`、`logger`
- 可选：`audit`（🔒 凭据变更审计记录）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `credentials/updated` | emit | {credentialId, namespace, revision}：创建/更新/删除/轮换后发布 |
| `domain/changed` | listen | 凭据 domain 数据形式变更时适配 schema |

## §4 配置与装配

```java
// CredentialsProperties（prefix: gnex.credentials）
public class CredentialsProperties {
  String masterKeyRef;                // AES 主密钥来源（env / K8s Secret），禁明文
  int viewMaskLength = 8;             // 视图保留末 8 位
  boolean migrationFromLegacy = true; // 旧 agent_credential 一次性迁移
}
```

```yaml
# service-assembly.yml 片段（registry-service）
plugins:
  - id: gnex3-cap-credentials
    load: mandatory
    protection: platform   # 🔒 禁租户/Agent 装卸
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 密文必须 AES 加密落库（`AesEncryptor`），读取接口视图脱敏
- ✅ 消费方必须经 `${credentials:...}` 引用解析，禁止明文直配
- ✅ 任何变更必须 emit `credentials/updated`；轮换支持新旧版本过渡（原子双写）
- ✅ 🔒 装卸权仅平台管理员；变更写审计（audit/record）
- ❌ 禁止返回明文给前端/日志；禁止密钥进 session / 消息总线载荷
- ❌ 禁止租户/Agent 装卸本插件；跨租户解析必须返回空（RLS/租户过滤）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | AES 加密落库 | `CredentialEncryptTest`：`credential_store` 密文字段非明文，解密往返一致 |
| V2 | 视图脱敏 | `CredentialMaskTest`：查询仅保留末 8 位 |
| V3 | 引用解析 | `CredentialRefTest`：`${credentials:llm-byok}` 在 settings/后端解析成功 |
| V4 | 轮换事件 | `CredentialRotateTest`：轮换后 emit `credentials/updated`，旧版本过渡期可用 |
| V5 | 租户隔离 | `CredentialTenantIsolationTest`：跨租户解析返回空 |
| V6 | 旧数据迁移 | `AgentCredentialMigrationTest`：agent_credential → credential_store 迁移幂等 |

### 运行命令（审计修订 D-P1-02/D-P1-33）

```bash
# 目标模块（待 S 阶段新建，规划为 libs/gnex-plugin-storage 单模块——存储域多插件同模块，D-P1-02 裁决；不再单设 gnex-plugin-credentials 模块）
mvn -pl libs/gnex-plugin-storage test "-Dtest=CredentialEncryptTest,CredentialMaskTest,CredentialRefTest,CredentialRotateTest,CredentialTenantIsolationTest,AgentCredentialMigrationTest"
```

## §7 影响域

- 作用范围：**服务级 + 租户级**（registry-service 单例，🔒 受保护）。
- 被谁消费：模型适配器（BYOK）/ MCP 连接 / PG / MinIO / HTTP 工具 / settings。
- 故障影响半径：凭据服务不可用 = 全部密钥解析失败（需熔断 + 解析缓存降级）。
- 升级影响：🔒 平台级升级 + 灰度（E32 探针）；主密钥轮换 = 全量重加密。

## §8 后续（未纳入首版）

- 外部 KMS/HSM 托管主密钥（主密钥外置）
- 凭据过期策略与主动轮换计划
- 多租户 BYOK 密钥池扩展（`KeyPoolSelector` 集成）

---

*GNEX3.0 凭证管理 — 🔒 平台受保护 SDD。*
