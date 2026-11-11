# v3-spec-ent-tenant

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-tenant
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-tenant
> **分层**：企业层（L4）
> **受保护级别**：protected（平台持有，禁租户/Agent 卸载）
> **宿主服务**：各 gnex 服务（agent-service / auth-service / ops-service / registry-service / state-plane-service / observability-service / platform-service / sandbox-runtime / bus-service）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P0-02、D-P1-37（§2 映射拆分「代码实证来源 / 设计规格来源」两栏；§6 加前置条件；类名标注现状/设计态）

---

## §1 业务目标

租户边界是企业红线第一闸：任何跨服务数据访问、模型召回、任务调度必须先经 JWT（`tid`/`uid`/`projectId`）硬过滤，命中当前租户/项目后才允许后续打分/路由——「scope 先硬过滤，再打分」，串租户 / 串项目 = **P0**。继承 gnex2.0 四层数据隔离（E15：PG RLS / Redis key 前缀 / MinIO 租户桶 + IAM / MyBatis 拦截器兜底 `WHERE tenant_id=?`）与分布式 Worker label 亲和调度（`agent:task:{tenantId}` 分流），在 3.0 插件化架构下统一为 `tenant` 服务：入口解析 → 上下文传播 → 硬过滤 → 亲和调度 → 状态拦截，全链路 fail-closed。

**非目标**：不做认证签发（归 ent-auth）；不做业务级授权细分（角色/资源授权归 ent-org）；硬过滤与亲和只做「边界」，不做评分。

## §2 映射关系

> **审计修订（D-P0-02/D-P1-37）**：映射拆分「代码实证来源」与「设计规格来源」两栏，避免现状（源码）与设计态混淆——验收基线以代码实证列为准；设计规格列的类名在落地前均视为 `[设计态/待落地]`。

| 维度 | 代码实证来源（源码可溯） | 设计规格来源（设计态/待落地） |
|------|--------------------------|------------------------------|
| 对标 dsh 插件 | dsh 无此原语（dsh 无企业平面，无租户边界概念） | — |
| 对标 GNEX2.0 模块 | `gnex-persistence-common`（E15 引用：四层隔离的持久层公共库；注意：`TenantInterceptor` / `ProjectScopeInterceptor` / `PgRlsInitializer` / `MinioIamConfig` 四个类名在 `_work/gnex-services-analysis.md` 九服务分析中**未命中**——该分析未展开 libs/ 目录，推测位于 `libs/gnex-persistence-common`，**源码待核**；核实前一律按 `[设计态/待落地]` 对待，不得作为验收基线） | 分布式规格 Worker label 亲和（`agent:task:{tenantId}` 分流）——`WorkerRegistry` 等调度侧类名同属设计文档来源，见 ent-ha spec |
| 技术选型 | 🅶 GNEX2.0 模块改造（拦截器/RLS 保留，统一收敛为 `tenant` 服务） | — |
| 改造要点 | ① 拦截器/RLS 收敛为 `tenant` 服务统一出口（入口解析 JWT → 绑定租户上下文）② 亲和调度插件化（调度器经 `tenant` 校验 Worker label）③ 租户禁用/挂起状态经 `tenant` 硬拦截（fail-closed）④ 每次硬过滤 emit `tenant/isolated` 供审计/观测 | `PgRlsInitializer` RLS 初始化与 `MinioIamConfig` 租户桶 IAM 若源码核实不存在，则 V6（RLS 兜底）在 2.0 基线上不可复现，须先落代码基线（见 §6 前置条件） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `tenant` | `com.gnex.plugin.enterprise.tenant.TenantBoundaryService` | 入口 JWT 解析、租户上下文绑定/传播、scope 硬过滤、亲和调度校验、租户状态拦截 |

### 3.2 注入服务（inject）

- 必选：`ctx`、`scope`、`events`、`logger`
- 可选：`auth`（JWT 校验/吊销查询）、`audit`（硬过滤审计）、`observability`（隔离指标）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tenant/isolated` | emit | {tenantId, projectId, uid, path, decision}：每次硬过滤拦截 emit（可审计） |
| `audit/record` | listen（可选） | 硬过滤违规/隔离事件入审计 |

## §4 配置与装配

```java
// TenantBoundaryProperties（prefix: gnex.tenant）
public class TenantBoundaryProperties {
  boolean failClosed = true;          // 租户上下文缺失默认拒绝（不默认放行）
  boolean affinityEnabled = true;     // Worker label 亲和调度开关
  String affinityLabel = "tenant";    // Worker label 键
  boolean enforceRls = true;          // 温库 RLS 强制（PgRlsInitializer）
}
```

```yaml
# service-assembly.yml 片段（各服务）
plugins:
  - id: gnex3-ent-tenant
    load: mandatory
    protection: platform
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 必须 JWT `tid`/`uid`/`projectId` 硬过滤：任何数据读取/召回先过滤再打分（scope 先硬过滤，再打分）
- ✅ 必须全服务入口解析 JWT 并绑定租户上下文，随请求/消息传播（跨服务 metadata 透传）
- ✅ 必须 Worker label 亲和：任务按 `agent:task:{tenantId}` 分流且仅调度到 label 匹配的 Worker
- ✅ 必须租户禁用/挂起状态硬拦截（禁用吊销 token；挂起网关 403 场景）
- ✅ 必须四层数据隔离兜底（RLS / key 前缀 / 租户桶 + IAM / 拦截器 `WHERE tenant_id=?`）
- ❌ 串租户 / 串项目 = **P0**（任何数据泄漏路径，含召回/搜索/导出/SSE）
- ❌ 禁止跳过硬过滤直接打分（相似度/评分不得先于 scope 过滤）
- ❌ 禁止租户上下文缺失时默认放行（fail-closed，缺失即拒绝）
- ❌ 禁止租户/Agent 装卸本插件（🔒 platform 持有）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | JWT 硬过滤：跨租户请求被拒 | `TenantBoundaryTest.crossTenantDenied`：tid=A 请求 B 数据 → 拒绝且 emit `tenant/isolated` |
| V2 | scope 先过滤再打分 | `...filterBeforeScore`：打分管线在过滤前不可达（调用序断言） |
| V3 | 亲和调度：任务只到匹配 label Worker | `...affinityRouting`：tenant=A 任务不落 `tenant=alpha` 之外的 Worker |
| V4 | 状态拦截：禁用/挂起租户拒绝 | `...suspendedTenantDenied`：挂起租户请求 403 |
| V5 | fail-closed：缺失 JWT 拒绝 | `...missingJwtDenied`：无 tid 请求默认拒绝 |
| V6 | RLS 兜底：温库隔离生效 | `...rlsEnforced`：跨租户 SQL 返回空/抛错 |

### 前置条件（审计修订 D-P0-02）

- §2「设计规格来源」列的类名（`TenantInterceptor` / `ProjectScopeInterceptor` / `PgRlsInitializer` / `MinioIamConfig`）须先在 `libs/gnex-persistence-common` 完成源码核实：命中则升级为「代码实证来源」并回填类全名+文件路径；未命中则标 `[设计态/待落地]` 并先落代码基线，再执行 V6（RLS 兜底）验收。
- V1-V5 以 JWT 解析/上下文传播/事件断言为主，不依赖上述待核类，可在基线核实前先行开发；**V6 以 RLS 代码基线落地为前置**，未落地不得宣称达标（排期纪律：无验证输出不得宣称落地）。

### 运行命令

```bash
# 目标模块（3.0 规划新模块，待 S 阶段新建；落地前 V1-V5 的等价测试可先落在宿主服务的拦截器测试内）
mvn -pl libs/gnex-plugin-enterprise test "-Dtest=TenantBoundaryTest"
```

## §7 影响域

- 作用范围：**租户级/全平台**——每个服务入口与每条数据路径。
- 被谁消费：全部服务入口、记忆召回（ent-memory）、调度器（ent-ha）、网关（ent-api）。
- 故障影响半径：边界缺陷 = 串租户 = P0（影响面最大）→ V1-V6 为发布门。
- 升级影响：🔒 平台级升级 + 灰度（边界语义变更 = 全服务回归）。

## §8 后续（未纳入首版）

- 跨服务租户上下文透传治理（bus Envelope `properties.tenantId` 强校验，E5）
- 租户级网络隔离（沙箱网络策略联动 sandbox-policy）
- 数据导出/删除的租户边界审计报告

---

*GNEX3.0 租户边界 — 硬过滤 + 亲和调度 SDD。*
