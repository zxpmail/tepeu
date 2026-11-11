# v3-spec-model-gateway

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.1 模型路由 · 05-interface-relationships.md §1/§2
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-model-gateway
> **分层**：L3 能力层（3.1 模型路由）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-22

---

## §1 业务目标

模型网关（调用准备阶段）：在 llm seam 的 prepareCall 阶段提供三类横切——① KV 缓存（相同请求去重，避免重复计费）；② 预算检查（会话/日 token 上限，超限拒绝）；③ 路由记录（产出 cost 记录对齐 cost_events）。对标 dsh `dsh-llm`（prepareCall）+ `dsh-token-meter`，复用 gnex2.0 agent/model/gateway 已有能力，目标是让每次模型调用「先检查后放行、留痕可审计」。

**计量与记账分工（D-P1-22）**：本插件涉及两个不同职责，不得混淆——

| 职责 | 载体 | 性质 | 消费方 |
|------|------|------|--------|
| **运行计量** | `tokenMeter`（进程内服务） | 运行时 token 用量累计，供压缩决策（compaction）等进程内消费读预算上下文 | compaction / 预算上下文 |
| **落库记账** | `cost_events`（DB 表） | 调用后**旁路写**落库的记账记录，对齐 E29 成本计量 | ops-service 只读聚合（token_usage/cost_events）、billing |

`tokenMeter` 由本插件**可选注入**（存在则计量，用于读预算上下文）；`cost_events` 落库为旁路写（失败不阻断主调用，放行+告警）。

**非目标**：不做费用聚合与计费（ops-service 只读聚合 token_usage/cost_events）；不做预算的持久化权威（DB 为准，本插件为执行点）；不做运行计量的持久化（tokenMeter 为进程内运行态，落库归 cost_events 通道）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-llm`（prepareCall）+ `dsh-token-meter`（token 计量） |
| 对标 GNEX2.0 模块 | agent-service `agent/model/gateway`（已有，改造为插件扩展）；`ModelPricingPort` / `TokenUsage` / `cost_events`（E29 成本计量，落库记账）、`AgentBudgetService`（预算控制） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① 把现网 model/gateway 的预算/记录逻辑封装为 llm seam 的 prepareCall 扩展（KV→预算→记录三步）② KV 缓存 key 必须带租户/会话作用域，禁跨租户命中；仅缓存非流式完整结果 ③ 预算检查对齐 `AgentBudgetService` 语义：会话/日 token 上限，超限即拒（BudgetExceeded）④ **计量/记账分工（D-P1-22）**：`tokenMeter`=运行计量（进程内、可选注入、读预算上下文，compaction 消费），`cost_events`=落库记账（调用后旁路写 DB，含 requestId/model/tokens/cost）；本插件为 `tokenMeter` 的注入者（可选）与 `cost_events` 的旁路写入者 ⑤ 记账旁路降级：cost_events/tokenMeter 故障放行+告警，不阻断主调用 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `llm`（扩展） | `com.gnex.model.gateway.LlmGatewayExtension` | prepareCall 网关阶段：KV 缓存 / 预算检查 / 路由记录（装配进 llm 服务扩展点） |

### 3.2 注入服务（inject）

- `llm`（必选）：被包装的调用（装配期注册扩展点，运行时经 prepareCall 回调）
- `tokenMeter`（**可选**，D-P1-22）：运行计量服务——存在时向其累计 usage（读预算上下文，供 compaction 等消费）；**缺失时不影响主调用**（跳过运行计量+观测告警，落库记账仍走 cost_events 旁路写）
- `settings`（必选）：网关配置段（`gnex.model.gateway.*`）
- `events`（可选）：预算超限/记账失败告警

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `llm/stream` | listen | 调用完成（done 载荷）→ 提取 usage：① tokenMeter 存在则写入（运行计量）② 旁路产出路由记录落 `cost_events`（对齐 E29 载荷） |

## §4 配置与装配

```yaml
gnex.model.gateway:
  cache:
    enabled: true
    ttl-seconds: 3600
    max-entries: 10000
  budget:
    session-token-cap: 1000000       # 会话 token 上限（超限拒单）
    daily-token-cap: 5000000         # 日 token 上限
  record:
    enabled: true                    # 路由记录（cost_events 落库记账，旁路写）
  metering:
    inject-token-meter: optional     # tokenMeter 运行计量为可选注入（D-P1-22）
```

装配声明：`gnex3-cap-model-gateway` 为 agent-service optional 插件，注册顺序位于熔断/限流之后、retry 之前。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 每次调用必须先经预算检查再放行；超限必须拒绝（BudgetExceeded）
- ✅ 落库记账必须旁路写 `cost_events`（记录含 requestId/model/tokens/cost，对齐 E29 载荷）；记账失败放行+告警，禁阻断主调用
- ✅ `tokenMeter`（可选注入）存在时必须计量（usage 只增，禁负/禁篡改）；缺失时跳过+告警，禁因此拒单
- ✅ KV 缓存 key 必须带租户/会话作用域，禁跨租户命中
- ❌ 缓存不得缓存流式响应正文（仅非流式完整结果 + 模型白名单）
- ❌ 不得绕过预算检查直连底层调用
- ❌ 不得把运行计量（tokenMeter）当落库记账权威（落库=cost_events；两者职责不同，禁互相替代，D-P1-22）
- ❌ 不得在网关内做重试/熔断（retry/llm seam 职责）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | KV 缓存命中 | `ModelGatewayCacheHitTest`：相同请求第二次命中缓存（调用次数=1） |
| V2 | 缓存租户隔离 | `ModelGatewayCacheTenantTest`：跨租户相同请求不命中 |
| V3 | 预算拒单 | `ModelGatewayBudgetDenyTest`：超会话/日上限抛 BudgetExceeded，调用未发出 |
| V4 | token 记账 | `ModelGatewayTokenMeterTest`：调用后 `tokenMeter` usage 正确累加 |
| V5 | 路由记录 | `ModelGatewayRecordTest`：`cost_events` 记录含 requestId/model/tokens/cost |
| V6 | 旁路降级 | `ModelGatewayMeterDownTest`：tokenMeter/cost_events 故障时放行+告警，主调用成功 |
| V7 | 流式不缓存 | `ModelGatewayStreamNoCacheTest`：流式请求不写入 KV 缓存 |
| V8 | tokenMeter 可选（D-P1-22） | `ModelGatewayMeterOptionalTest`：容器无 tokenMeter 服务时装配成功、主调用正常放行、cost_events 记账不受影响（观测告警发出） |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=ModelGateway*Test"
```

## §7 影响域

- 作用范围：**会话级（每请求）**——每次模型调用的准备与记账。
- 被谁消费：`llm` seam 调用链；`tokenMeter` 运行计量被 compaction 消费（读预算上下文）；`cost_events` 路由记录被 ops-service 只读聚合。
- 故障影响半径：记账缺陷 = 成本数据缺口（旁路降级 + 告警兜底）；缓存缺陷 = 重复计费（有界）。
- 升级影响：预算策略变更=无感（配置热更新）。

## §8 后续（未纳入首版）

- 实时成本告警（80% 阈值告警 / 120% 硬限，对齐 billing_budget 语义）
- KV 缓存淘汰策略（LRU + 白名单模型粒度）
- 跨服务预算聚合（多副本共享预算水位，Redis 原子扣减）

---

*GNEX3.0 模型网关 — KV/预算/路由记录 prepareCall 扩展·tokenMeter 运行计量与 cost_events 落库记账分工 SDD。*
