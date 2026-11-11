# v3-spec-storage-workspace

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.3
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-workspace
> **分层**：L3 能力层（3.3 存储与凭证）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-02/D-P1-33（验收模块标注）

---

## §1 业务目标

工作区注册表管理 Agent 会话「工作区」的完整生命周期：注册、绑定（workspaceId ↔ sessionId ↔ sandboxId ↔ rootPath）、路径语义（会话盘 artifact 路径）、归档与清理。对标 dsh-workspace（会话工作区协调）与 gnex2.0 workspace 包：agent-service `com.gnex.workspace`（`WorkspaceService` / `WorkspaceController` / `SessionArtifactPathHints`）与 `com.gnex.agent.workspace`（`WorkSpaceSessionCoordinator`，ISOLATED 会话与 Always-On 自动创建绑定沙箱）。工作区是 Agent 文件产物的挂载点：元数据入 storage domain（PG），正文/大文件经 attachments（MinIO `gnex-workspace` 桶，会话后 7d TTL）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-workspace` |
| 对标 GNEX2.0 模块 | workspace 包：agent-service `com.gnex.workspace`（`WorkspaceService` / `WorkspaceController` / `SessionArtifactPathHints`）+ `com.gnex.agent.workspace`（`WorkSpaceSessionCoordinator`）；MinIO `gnex-workspace` 桶（会话后 7d） |
| 技术选型 | 🅶 gnex2.0 模块改造（workspace 包插件化） |
| 改造要点 | ① 工作区注册表：workspaceId ↔ session ↔ sandbox ↔ rootPath ② 会话盘路径语义统一（`SessionArtifactPathHints`）③ ISOLATED/Always-On 自动创建绑定沿用 `WorkSpaceSessionCoordinator` ④ 归档：目录 → MinIO `gnex-workspace` 桶（7d TTL）⑤ 元数据落 storage domain |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `workspaceRegistry` | `com.gnex.plugin.workspace.WorkspaceRegistryService` | 工作区注册 / 查询 / 绑定 / 归档 / 清理 |

### 3.2 注入服务（inject）

- 必选：`storage`（workspace 元数据 domain）、`ctx`、`events`、`settings`、`logger`
- 可选：`attachments`（归档正文）、`sandbox`（绑定沙箱执行世界）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `domain/changed` | listen | workspace 元数据 domain 数据形式变更时适配 |
| `credentials/updated` | listen（可选） | 归档走 MinIO 时密钥轮换刷新 |

## §4 配置与装配

```java
// WorkspaceProperties（prefix: gnex.storage.workspace）
public class WorkspaceProperties {
  String rootDir = "${gnex.workspace-dir}/workspaces"; // 本地根目录
  boolean autoCreateOnSession = true; // 会话创建自动注册工作区
  Duration archiveTtl = Duration.ofDays(7); // gnex-workspace 桶 TTL
}
```

```yaml
# service-assembly.yml 片段（agent-service）
plugins:
  - id: gnex3-cap-workspace
    load: mandatory
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 工作区必须先注册再绑定（workspaceId 唯一，绑定 session/sandbox/rootPath 三元组）
- ✅ 会话结束必须触发归档或清理（TTL 7d，对齐 `gnex-workspace` 桶策略）
- ✅ 路径访问必须经会话盘语义（`SessionArtifactPathHints` / SandboxWorkspacePort），禁止裸路径
- ✅ 元数据必须落 storage domain，正文/大文件走 attachments
- ❌ 禁止跨会话/跨租户复用工作区；禁止把工作区当通用文件存储
- ❌ 归档不得阻塞会话主流程（异步 + 预签名）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 注册/查询 | `WorkspaceRegistryTest`：register → get 往返一致 |
| V2 | 会话自动创建 | `WorkspaceAutoCreateTest`：会话创建后工作区自动注册并绑定 |
| V3 | 绑定约束 | `WorkspaceBindTest`：重复绑定/非法 session 拒绝 |
| V4 | 归档清理 | `WorkspaceArchiveTest`：会话结束归档到 `gnex-workspace` 桶并在 TTL 后清理 |
| V5 | 路径语义 | `WorkspacePathTest`：artifact 相对路径解析到会话盘 |
| V6 | 租户隔离 | `WorkspaceTenantIsolationTest`：跨租户工作区不可见 |

### 运行命令（审计修订 D-P1-02/D-P1-33）

```bash
# 目标模块（待 S 阶段新建，规划为 libs/gnex-plugin-storage 单模块——存储域多插件同模块，D-P1-02 裁决；不再单设 gnex-plugin-workspace 模块）
mvn -pl libs/gnex-plugin-storage test "-Dtest=WorkspaceRegistryTest,WorkspaceAutoCreateTest,WorkspaceBindTest,WorkspaceArchiveTest,WorkspacePathTest,WorkspaceTenantIsolationTest"
```

## §7 影响域

- 作用范围：**会话级 + 服务级**（agent-service）。
- 被谁消费：会话 / 沙箱 / 工具 fs / UI（`WorkspaceController`）。
- 故障影响半径：注册表不可用 = 新会话无法建工作区（可降级为无工作区模式）。
- 升级影响：路径语义变更 = 会话盘兼容迁移；归档策略变更 = 无感。

## §8 后续（未纳入首版）

- 工作区配额联动（`tenant_quota.storageQuotaMb`）
- 跨沙箱工作区迁移（归档 → 还原）
- Always-On 长驻工作区策略

---

*GNEX3.0 工作区注册表 — 会话工作区生命周期 SDD。*
