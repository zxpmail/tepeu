# v3-spec-storage-attachment

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.3
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-attachment
> **分层**：L3 能力层（3.3 存储与凭证）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-02/D-P1-33（验收模块标注）

---

## §1 业务目标

附件/产物存储统一到 MinIO 对象存储：会话产物（artifact）、沙箱大文件、Agent 输出经预签名 URL 直传直取，避免大文件过内存/消息总线/SSE。对标 dsh-attachment-local（本地附件目录），GNEX 以 MinIO 承载多副本共享真相（E1）。gnex2.0 已有 MinIO 设施（`MinioPresignedUrlProvider`、`MinioIamConfig` 租户桶 + IAM、`FileStoreInterface` 桶策略、`blob://` 访问协议）直接改造为插件；元数据入 storage domain（PG），正文入对象存储，生命周期（gnex-artifacts 90d LRU）沿用。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-attachment-local` |
| 对标 GNEX2.0 模块 | MinIO（已有）：`MinioPresignedUrlProvider`（bucket `gnex-sandbox`）、`MinioIamConfig`（租户桶 + IAM）、`FileStoreInterface`（`gnex-artifacts` 90d LRU / `gnex-workspace` 7d / `gnex-documents` 永久）、`blob://`；agent-service `WorkspaceService` / `WorkspaceArtifactPortAdapter` |
| 技术选型 | 🅶 gnex2.0 模块改造（MinIO 已有设施插件化） |
| 改造要点 | ① MinIO 客户端封装为附件服务并挂载到 storage hub ② 预签名 URL（PUT/GET，租户桶隔离）③ 大小分档：小对象直存 / 大对象流式分片 ④ 生命周期清理沿用 `DataCleanupScheduler`（TTL/LRU）⑤ 密钥经 `credentials` 引用，轮换重建客户端 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `attachments` | `com.gnex.plugin.attachment.AttachmentService` | 附件/产物上传下载 / 预签名 / 删除（按会话 + 租户） |

### 3.2 注入服务（inject）

- 必选：`storage`（附件元数据 domain）、`ctx`、`events`、`settings`、`logger`
- 可选：`credentials`（MinIO accessKey/secretKey 引用）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `domain/changed` | listen | 附件元数据 domain 数据形式变更时适配 |
| `credentials/updated` | listen（可选） | MinIO 密钥轮换后重建客户端 |

## §4 配置与装配

```java
// AttachmentProperties（prefix: gnex.storage.attachment）
public class AttachmentProperties {
  String endpoint;                  // MinIO endpoint（空 = 禁用，回退本地）
  String bucketPrefix = "gnex";     // 租户桶 gnex-{tenantId}-artifacts
  boolean presignEnabled = true;
  long presignExpirySeconds = 900;
  Duration artifactTtl = Duration.ofDays(90); // 桶生命周期
}
```

```yaml
# service-assembly.yml 片段（agent-service）
plugins:
  - id: gnex3-cap-attachment
    load: optional   # 无 MinIO 可缺省（本地回退 dsh-attachment-local 语义）
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 大文件必须经预签名 URL 传输，禁止经消息总线/SSE 传附件
- ✅ 必须租户桶 + IAM 隔离（`MinioIamConfig`，E15），桶名带 tenantId
- ✅ 密钥必须走 `credentials` 引用（accessKey/secretKey），轮换后重建客户端
- ✅ 生命周期清理必须沿用 TTL/LRU（`DataCleanupScheduler`）
- ❌ 禁止明文密钥进配置/日志；禁止跨租户读附件（跨桶 403）
- ❌ 禁止把附件正文写入结构化库大字段（元数据入 PG，正文入对象存储）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 上传/下载往返 | `AttachmentRoundTripTest`：PUT 对象 → GET 内容一致 |
| V2 | 预签名直传 | `PresignTest`：客户端未持有密钥即可直传/直取（URL 过期校验） |
| V3 | 租户桶隔离 | `AttachmentTenantIsolationTest`：跨租户桶访问 403 |
| V4 | 元数据与正文分离 | `AttachmentMetaTest`：元数据入 storage domain，正文仅 URI 引用 |
| V5 | TTL 清理 | `AttachmentTtlTest`：过期对象被 `DataCleanupScheduler` 清理 |
| V6 | 密钥轮换 | `AttachmentCredentialTest`：`credentials/updated` 后客户端重建成功 |
| V7 | 无 MinIO 回退 | `AttachmentFallbackTest`：endpoint 空时本地文件回退可用 |

### 运行命令（审计修订 D-P1-02/D-P1-33）

```bash
# 目标模块（待 S 阶段新建，规划为 libs/gnex-plugin-storage 单模块——存储域多插件同模块，D-P1-02 裁决；不再单设 gnex-plugin-attachment 模块）
mvn -pl libs/gnex-plugin-storage test "-Dtest=AttachmentRoundTripTest,PresignTest,AttachmentTenantIsolationTest,AttachmentMetaTest,AttachmentTtlTest,AttachmentCredentialTest,AttachmentFallbackTest"
```

## §7 影响域

- 作用范围：**会话级 + 服务级**（agent-service）。
- 被谁消费：UI（WorkspaceController）/ 会话 / 沙箱大文件 / 产物导出。
- 故障影响半径：MinIO 不可用 = 附件不可用（纠删码 <5s 容灾），主进程不受影响。
- 升级影响：桶策略/生命周期变更 = 无感；客户端升级 = 无感。

## §8 后续（未纳入首版）

- 分片/断点续传（超大产物）
- 对象版本化 / 回收站（防误删）
- MinIO → S3 兼容中间件桥（🅱）

---

*GNEX3.0 附件/产物存储 — MinIO 后端 SDD。*
