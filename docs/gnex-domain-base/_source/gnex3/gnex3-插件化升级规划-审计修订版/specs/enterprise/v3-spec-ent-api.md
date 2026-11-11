# v3-spec-ent-api

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-api
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-api
> **分层**：企业层（L4）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：各 gnex 服务（agent-service / auth-service / observability-service 等）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P0-03（§6 运行命令改为显式七个测试类名，消除 `GatewayTest` 零匹配；裁决 GatewayAuthTest 与 ent-auth V7 的双归属）

---

## §1 业务目标

跨服务 API 网关：统一 REST/gRPC/SSE 协议面，收敛各服务 Controller 的认证粗校验、鉴权、限流、响应包装（ApiResponse）与错误码。企业语义：网关校验 JWT roles claim（粗粒度）后放行，下游服务信任（E20）；SSE 通道承载 Agent 原语（Action→Observation）与前端会话流（Redis sse key 外化支持多副本续接）；gRPC 为 3.0 新增协议面。插件化价值：`api` 服务成为对外暴露的统一出口，路由注册可插，内部服务不得绕过网关直通。

**非目标**：不做业务逻辑（薄转发）；不做 Kong 运维面（目标态经 Kong，插件先提供能力面）；不做消息面（归 bus）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-host-apiproxy` + `dsh-client-connection`（弱类比：Host RPC/连接面） |
| 对标 GNEX2.0 模块 | 各服务 Controller（REST + ApiResponse + @PreAuthorize）+ Kong（微服务规格目标态）+ agent-service SSE（gnex:active-run:sse:* 外化） |
| 技术选型 | 🅶 GNEX2.0 模块改造为插件（Controller 面 → api 服务） |
| 改造要点 | ① 插件化：`api` 提供路由注册/鉴权拦截/限流/协议转换（REST·gRPC·SSE），各服务 Controller 收编为网关路由；② 认证粗校验：JWT roles claim 校验 + 下游信任（E20 对齐），失败 fail-closed；③ SSE：会话流外化（Redis sse key）支持多副本续接与断线重连；④ gRPC：新增协议面（服务间与客户端，proto 契约）；⑤ 统一响应包装与错误码 + trace_id 透传 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `api` | `com.gnex.ent.api.ApiGateway` | 路由注册、认证粗校验、限流、响应包装、REST/gRPC/SSE 协议适配 |

### 3.2 注入服务（inject）

- `auth`（必选）：JWT 校验 + roles claim 粗粒度鉴权
- `audit`（可选）：API 访问审计（audit/record）
- `observability`（可选）：网关指标/链路（trace_id 透传）
- `scope` / `settings` / `events`（必选）：租户 realm、路由/限流配置、事件

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `audit/record` | emit | {action: API_CALL, resource: 路由, outcome, tenantId, traceId} |
| `bus/published` / `bus/consumed` | listen | 事件驱动接口（可选，回调式 API 经总线） |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-ent-api
    load: mandatory
    config:
      gnex.api.protocols: [rest, grpc, sse]      # 协议面
      gnex.api.auth.granularity: coarse           # 粗粒度 roles claim 校验
      gnex.api.rate-limit.per-tenant: 1000        # 租户级限流（rpm）
      gnex.api.response.wrapper: ApiResponse      # 统一包装
      gnex.api.trace.enabled: true                # trace_id 透传
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 认证必须经网关粗粒度校验（JWT roles claim）后放行，下游服务信任（E20）
- ✅ 鉴权失败必须 fail-closed（网关异常不得放行）；无效/过期 token 统一拒绝
- ✅ 必须统一响应包装（ApiResponse）+ 错误码 + trace_id 透传（链路可关联）
- ✅ SSE 会话流必须外化（多副本续接）+ 断线重连不丢事件
- ❌ 网关不得承载业务逻辑（薄转发）；不得绕过 auth 校验直通后端
- ❌ 租户插件不得注册绕过网关的内部路由（禁绕过 API 面直连服务）
- ❌ 密钥/令牌不得出现在网关日志与响应体（脱敏）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 认证粗校验 | `GatewayAuthTest`：无效/过期 token 拒绝；roles 不足返回 403（fail-closed） |
| V2 | 响应包装 | `ApiResponseTest`：统一包装 + 错误码 + trace_id 透传 |
| V3 | SSE 会话流 | `SseGatewayTest`：SSE 流外化 Redis 支持多副本续接，断线重连恢复 |
| V4 | gRPC 协议面 | `GrpcGatewayTest`：proto 契约调用经网关鉴权后路由到下游 |
| V5 | 限流 | `RateLimitTest`：超租户限流阈值返回 429 |
| V6 | 访问审计 | `ApiAuditTest`：API_CALL 审计含 tenantId/traceId |
| V7 | 路由注册 | `RouteRegistryTest`：新路由注册即生效，重复/冲突路由拒绝 |

### 运行命令（审计修订 D-P0-03）

原命令 `-Dtest=GatewayTest` 与 V1-V7 所列七个测试类**零匹配**（精确名不匹配 `GatewayAuthTest` 等），按命令跑不出任何所列断言。修订为显式七类全列（等价通配形式 `-Dtest=Gateway*Test` 仅覆盖 `GatewayAuthTest/SseGatewayTest/GrpcGatewayTest` 三类，**不足以覆盖全部**，故采用显式列表）：

```bash
mvn -pl services/gnex-agent-service test "-Dtest=GatewayAuthTest,ApiResponseTest,SseGatewayTest,GrpcGatewayTest,RateLimitTest,ApiAuditTest,RouteRegistryTest"
```

**测试归属裁决（D-P0-03，与 ent-auth V7 双归属）**：`GatewayAuthTest` 归**本 spec（ent-api）**——测网关面的认证粗校验（无效/过期 token 拒绝、roles 不足 403、fail-closed）；ent-auth V7 改测 **auth-service 侧** token 生命周期与吊销（`TokenLifecycleTest` 等），不重复承载网关面断言。两 spec 验收互不重叠：本 spec 管「网关校验行为」，ent-auth 管「token 签发/吊销正确性」。

## §7 影响域

- 作用范围：**全平台**（服务级；浏览器与外部客户端入口）。
- 被谁消费：d1-web（REST/SSE）、外部集成方（REST/gRPC）、内部服务间调用。
- 故障影响半径：网关不可用 = 对外 API 全断（内部总线通道不受影响）；鉴权绕过 = 合规 P0。
- 升级影响：协议面增补（gRPC）向后兼容；路由变更 = 服务重启。

## §8 后续（未纳入首版）

- Kong 网关运维面接入（路由托管/插件市场）
- OpenAPI/gRPC 契约注册中心（registry-service 汇聚）
- API 密钥级配额与按路由计费联动（billing）

---

*GNEX3.0 企业 API 网关插件 SDD。*
