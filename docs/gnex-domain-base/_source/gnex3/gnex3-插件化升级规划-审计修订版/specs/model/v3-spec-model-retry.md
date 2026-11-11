# v3-spec-model-retry

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.1 模型路由 · 05-interface-relationships.md §1/§2
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-model-retry
> **分层**：L3 能力层（3.1 模型路由）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-21

---

## §1 业务目标

LLM 请求重试与模型 fallback：作为 llm seam **调用链扩展**（位于预算检查之后、模型路由之前）拦截每次 LLM 调用，捕获可重试错误（超时/限流/5xx）后按指数退避重试；重试耗尽或模型不可用时沿降级阶梯切换 fallback 模型（对齐 gnex2.0 `KeyPoolRetryingChatModel` 与 OpenClaw P1 演进项）。目标是让瞬态失败对调用方透明、模型故障有兜底，同时严格有界（重试次数/时间硬限，熔断打开时不重试），避免放大故障。

重试语义的实现形态为**调用链内同步包装**（D-P1-21）：本插件以扩展点身份装配进 llm 调用链，在链内拦截错误并决定重试/降级；**不以 waterfall 监听者身份参与重试裁决**（`llm/stream` waterfall 只读观测，见 §3.3）。

**非目标**：不做密钥池轮换重试的替代（BYOK 轮换仍归 llm seam/适配器）；不做工具副作用调用的重试（仅 LLM 调用）；不在事件通路上做重试决策。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-llm-retry` |
| 对标 GNEX2.0 模块 | `gnex-llm-adapt`：`KeyPoolRetryingChatModel`（密钥池轮换重试，改造为通用重试+fallback）、`LlmCircuitBreaker`（熔断联动）；OpenClaw P1 演进项（请求重试/模型 fallback） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① 形态裁定（D-P1-21）：本插件实现为 **llm 调用链扩展**（装配序：熔断→限流→预算→**重试**→路由），在链内同步拦截与重试 ② 把 `KeyPoolRetryingChatModel` 的轮换重试泛化为「错误分类重试 + 模型 fallback」两层 ③ 错误分类：可重试（TIMEOUT/RATE_LIMIT/5xx）vs 不可重试（4xx 业务错）④ 重试策略：指数退避+抖动，max-attempts 硬限 ⑤ 与 `LlmCircuitBreaker` 联动：熔断打开禁重试直接抛 ⑥ 同一 requestId 幂等重试（流式从零重放） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `llm`（扩展） | `com.gnex.model.retry.LlmRetryExtension` | 重试/fallback 包装，装配进 llm 调用链（熔断→限流→预算→重试→路由），链内同步拦截错误并执行重试/降级 |

### 3.2 注入服务（inject）

- `llm`（必选）：被包装的路由服务（重试后仍走统一路由）
- `settings`（必选）：重试策略配置段（`gnex.model.retry.*`）
- `events`（可选）：重试次数指标/告警上报

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `llm/stream` | listen（emit 观测，**不裁决**） | **仅观测重试指标**（D-P1-21）：listen `llm/stream` 的 error/done 载荷只记录 retry 统计（attempt 计数/退避时长/fallback 命中/最终成败），供 observability 上报；**不在此通路上判定或触发重试** |
| `llm/retry` | emit | 每次重试发生时上报指标事件（requestId/model/attempt/backoffMs/reason） |

> 边界说明（D-P1-21）：重试/fallback 的判定与执行全部发生在 §3.1 调用链扩展内部（同步、有界）；事件通路仅承担"观测重试指标"职责，waterfall 监听者不得修改调用结果或阻断调用。

## §4 配置与装配

```yaml
gnex.model.retry:
  max-attempts: 3                 # 总尝试次数硬限（含首次）
  backoff-base-ms: 1000
  backoff-multiplier: 2.0
  backoff-jitter-ratio: 0.2       # 抖动防惊群
  retryable-errors: [TIMEOUT, RATE_LIMIT, HTTP_5XX]
  fallback-models:                # 模型降级阶梯（沿 llm 路由顺序）
    deepseek-chat: [deepseek-reasoner]
  metrics:
    emit-retry-events: true       # llm/retry 指标事件开关
```

装配声明：`gnex3-cap-model-retry` 为 agent-service optional 插件，以调用链扩展形态注册，顺序位于 **budget（预算检查）之后、router（路由）之前**。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 重试仅限可重试错误（TIMEOUT/RATE_LIMIT/5xx）；4xx 业务错误必须直抛
- ✅ 重试必须指数退避+抖动，且受 max-attempts 硬限（禁无限重试）
- ✅ fallback 必须沿 llm 路由的降级阶梯，禁随机跳档/跨 provider 无节制降级
- ✅ 熔断打开时禁止重试（直接抛，避免放大故障）
- ✅ 重试请求必须携带同一 requestId（幂等，流式从零重放）
- ✅ 事件通路（listen `llm/stream`）仅观测重试指标，不得承担重试决策（D-P1-21）
- ❌ 不得对非幂等调用（工具副作用）做重试
- ❌ 不得绕过熔断/限流直接重试
- ❌ 重试期间不得吞掉非可重试错误
- ❌ 不得以 waterfall 监听者身份拦截/改写/阻断 `llm/stream` 调用结果（waterfall 无重试裁决权）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 超时重试 | `ModelRetryTimeoutTest`：首次超时后重试成功，调用方无感知 |
| V2 | 指数退避 | `ModelRetryBackoffTest`：断言退避序列（1000ms→2000ms±抖动） |
| V3 | 4xx 不重试 | `ModelRetryNoRetry4xxTest`：400/404 直抛，attempt=1 |
| V4 | 模型 fallback | `ModelRetryFallbackTest`：deepseek-chat 连续失败降级 deepseek-reasoner |
| V5 | 熔断联动 | `ModelRetryCircuitBreakerTest`：熔断打开时禁重试直接抛 |
| V6 | requestId 幂等 | `ModelRetryRequestIdTest`：多次尝试 requestId 一致 |
| V7 | 重试耗尽 | `ModelRetryExhaustedTest`：max-attempts 后抛原始错误 |
| V8 | 观测不裁决（D-P1-21） | `ModelRetryObserveOnlyTest`：移除本插件 waterfall 监听（或禁用监听）后重试行为不变；listen `llm/stream` 记录 retry 统计但调用结果与耗时不受监听者影响 |

### 运行命令

```bash
mvn -pl libs/gnex-llm-adapt test "-Dtest=ModelRetry*Test"
```

## §7 影响域

- 作用范围：**会话级（每请求）**——每次 LLM 调用的重试/降级。
- 被谁消费：`llm` seam 调用链；间接被 agentLoop 消费；retry 指标事件被 observability 消费。
- 故障影响半径：单次调用延迟被有界放大（max-attempts × backoff），不扩散到会话外。
- 升级影响：重试策略变更=无感（配置热更新）。

## §8 后续（未纳入首版）

- 重试预算：每会话/每轮次重试次数上限（防单轮风暴）
- 跨 provider fallback（deepseek → 其他厂商降级，需适配器注册多 provider）
- 重试可观测指标看板（attempt 计数/退避时长/fallback 命中率，消费 `llm/retry` 指标事件）

---

*GNEX3.0 请求重试与模型 fallback — 调用链扩展·有界重试/阶梯降级 SDD。*
