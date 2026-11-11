# v3-spec-model-llm

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.1 模型路由 · 05-interface-relationships.md §1/§2
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-model-llm
> **分层**：L3 能力层（3.1 模型路由）
> **受保护级别**：normal（管理员可装卸；适配器增删对消费方无感）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

GNEX3.0 模型路由的唯一出口（LLM seam）：provider 注册表 + 场景分级路由，使 agentLoop / 标题 / 压缩 / web 等消费方只依赖 `llm` 服务，不感知具体厂商与密钥实现。路由按 FAST/STANDARD/DEEP 场景分级并沿降级阶梯兜底（对齐 gnex2.0 `ModelRouter` 语义），适配器可装卸（增删 provider 运行时无感）。同时是模型调用链的装配点：retry / gateway / default 插件均以扩展身份挂载到本 seam，调用链顺序可控、可观测（`llm/stream` waterfall）。

**非目标**：不实现具体厂商 HTTP 客户端（deepseek 适配器插件承担）；不做跨服务模型路由（v3.1，适配器清单由 registry 下发）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-llm`（Provider 注册表 / 路由 / prepareCall） |
| 对标 GNEX2.0 模块 | `gnex-llm-adapt`：`ModelRouter`（FAST/STANDARD/DEEP 分级+降级阶梯）、`LlmCircuitBreaker`（租户级熔断）、`LlmRateLimiter`、`KeyPoolSelector`/`ByokKeyPoolResolver`/`AuthProfileManager`（BYOK 密钥池）、`LLMConfigManager` |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① 以 gnex2.0 `ModelRouter` 分级路由为内核，外包一层 provider 注册表（id → Provider 实例），适配器装卸即注册表增删 ② 熔断/限流保留为 seam 内建横切（租户级，先于路由）③ 新增 `llm/stream` waterfall 与 `llm/adapters-updated` 事件（对齐 05 事件词汇）④ `LLMConfigManager` 改造为经 `settings` 服务读取配置段，密钥一律走 `credentials` 引用 ⑤ 调用链扩展点（retry/gateway）在装配期注册，顺序固定：熔断→限流→预算→重试→路由 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）
| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `llm` | `com.gnex.model.llm.LlmService` | 模型路由统一出口：`chat(request)` / `stream(request)` / `listProviders()` / `getProvider(id)` |

### 3.2 注入服务（inject）
- `settings`（必选）：模型/路由配置段（`gnex.model.*`）
- `credentials`（必选）：provider 密钥解析（BYOK 密钥引用，禁止明文）
- `events`（必选）：事件总线（发 `llm/stream`、`llm/adapters-updated`）
- `tokenMeter`（可选）：装配 model-gateway 后计量注入（llm seam 不直接依赖）

### 3.3 事件（emit / listen）
| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `llm/stream` | emit（waterfall） | {requestId, provider, model, delta, done, error}——流式输出可被监听器拦截/变换/重试 |
| `llm/adapters-updated` | emit | {adapterId, action: added\|removed, version}——适配器装卸后广播 |

## §4 配置与装配

```yaml
gnex.model:
  adapters:                     # provider 注册表（插件装卸驱动，亦可静态声明）
    - id: deepseek
      provider: deepseek
  router:
    scenario-levels: [FAST, STANDARD, DEEP]
    default-level: STANDARD     # 未显式指定场景时的默认档
  circuit-breaker:
    enabled: true
    failure-threshold: 5
    window-ms: 60000
  rate-limiter:
    qps: 100
```

装配声明（service-assembly.yml 片段）：`gnex3-cap-model-llm` 为 agent-service mandatory 插件；deepseek/retry/gateway/default 为 optional，扩展点按注册顺序装配。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 路由必须支持 FAST/STANDARD/DEEP 场景分级 + 降级阶梯（对齐 ModelRouter）
- ✅ 适配器装卸必须发 `llm/adapters-updated`，消费方不得缓存 provider 快照
- ✅ 熔断/限流必须先于路由执行（租户级，防单租户打爆共享池）
- ✅ 调用链扩展点顺序必须固定（熔断→限流→预算→重试→路由）
- ❌ seam 不得内置具体厂商 HTTP 实现（由适配器插件提供）
- ❌ seam 不得直接读明文密钥（一律经 `credentials` 服务解析引用）
- ❌ 不得绕过 `llm` 服务直连底层 client（agentLoop 等消费方禁止）
- ❌ 不得在 seam 内自行记账/重试（属 gateway/retry 插件职责）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | provider 注册表：注册/注销/重复注册拒绝 | `LlmSeamRegistryTest`：getProvider('deepseek') 可解析；重复注册抛 DuplicateProviderException |
| V2 | 场景分级路由 | `LlmRouterScenarioTest`：DEEP 场景请求路由到 reasoner 档 |
| V3 | 降级阶梯 | `LlmRouterFallbackTest`：首选 provider 失败后沿阶梯降级，最后抛 NoProviderAvailable |
| V4 | 熔断先于路由 | `LlmCircuitBreakerOrderTest`：熔断打开时请求直接拒绝，不触达 provider |
| V5 | `llm/stream` waterfall 拦截 | `LlmStreamWaterfallTest`：监听器拒绝后流不产生（拦截生效） |
| V6 | 适配器装卸事件 | `LlmAdaptersUpdatedTest`：add/remove 后 `llm/adapters-updated` 载荷正确 |
| V7 | 未知适配器 | `LlmUnknownAdapterTest`：路由到未注册 provider 抛 UnknownProviderException |

### 运行命令
```bash
mvn -pl libs/gnex-llm-adapt test "-Dtest=LlmSeam*Test,LlmRouter*Test,LlmCircuitBreaker*Test"
```

## §7 影响域

- 作用范围：**会话级（每请求）**——每个模型调用必经本 seam。
- 被谁消费：agentLoop / 标题 / 压缩 / web；retry、gateway、default 插件（扩展点）。
- 故障影响半径：seam 缺陷 = 该会话模型调用失败（其余会话不受影响）。
- 升级影响：适配器增删=无感（运行时热装卸）；路由策略变更=新请求生效。

## §8 后续（未纳入首版）

- 跨服务模型路由：适配器清单由 registry 下发，多副本一致装载
- per-tenant 模型白名单与场景映射（租户级策略注入路由）
- 流式背压与节流（llm/stream 增量速率控制）

---

*GNEX3.0 模型路由 seam — provider 注册表 / 分级路由 / 调用链装配 SDD。*
