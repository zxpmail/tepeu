# v3-spec-model-deepseek

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.1 模型路由 · 05-interface-relationships.md §1/§2
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-model-deepseek
> **分层**：L3 能力层（3.1 模型路由）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

DeepSeek 模型适配器：以 `LlmProvider` 身份注册进 `llm` seam 的 provider 注册表，提供 `deepseek-chat` / `deepseek-reasoner` 的非流式与流式调用（spring-ai 2.0.0 客户端），并支持 BYOK 密钥池（KeyPoolSelector 轮换）。适配器只做协议转换（请求参数 → DeepSeek HTTP API → 统一响应），重试/熔断/记账等横切由 llm seam 及其扩展插件承担，保证厂商实现与平台能力解耦。

**非目标**：不做多 provider 路由（seam 职责）；不做重试/熔断（retry 插件职责）；不做密钥明文存储（credentials 服务职责）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-llm-deepseek` |
| 对标 GNEX2.0 模块 | `gnex-llm-adapt` 的 spring-ai DeepSeek adapter（spring-ai 2.0.0 `spring-ai-model`）、`DeepSeekEndpointModelIds`；`KeyPoolSelector`/`ByokKeyPoolResolver`（BYOK 密钥池轮换）、`LLMConfigManager` |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① 把现网 spring-ai DeepSeek 调用封装为 `LlmProvider` 接口实现，注册进 llm seam 注册表 ② 模型 ID 固定为 `deepseek-chat`/`deepseek-reasoner`（对齐 DeepSeekEndpointModelIds），reasoner 走更高 max_tokens 档 ③ BYOK：请求时经 `credentials` 服务解析租户密钥，多密钥走 `KeyPoolSelector` 轮换 ④ 流式输出以 delta 形式汇入 `llm/stream` waterfall ⑤ 密钥/URL 全部配置化，禁硬编码 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `llm`（provider） | `com.gnex.model.deepseek.DeepSeekAdapter implements LlmProvider` | DeepSeek provider 注册项：chat / stream / 模型清单（deepseek-chat、deepseek-reasoner） |

### 3.2 注入服务（inject）

- `llm`（必选）：注册表（插件启动时注册自身，卸载时注销）
- `credentials`（必选）：BYOK 密钥解析（`deepseek` 密钥池）
- `settings`（必选）：适配器配置段（`gnex.model.deepseek.*`）
- `events`（可选）：观测（异常上报）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `llm/adapters-updated` | listen | 适配器注册表变更（如自身被移除 → 停止接受新请求，进行中请求完成） |
| `credentials/updated` | listen | BYOK 密钥变更 → 重载密钥池缓存 |

## §4 配置与装配

```yaml
gnex.model.deepseek:
  base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
  api-key-ref: credentials.deepseek       # 密钥引用（禁明文）
  models:
    chat: deepseek-chat
    reasoner: deepseek-reasoner
  options:
    temperature: 0.7
    max-tokens: 8192
    reasoner-max-tokens: 32768
```

装配声明：`gnex3-cap-model-deepseek` 为 agent-service optional 插件，随 `gnex3-cap-model-llm` 装配。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 必须实现 `LlmProvider` 接口并注册进 llm 注册表（否则路由不可见）
- ✅ 密钥必须经 `credentials` 服务解析引用，禁止明文入配置/日志
- ✅ 必须支持 `deepseek-chat` 与 `deepseek-reasoner`（对齐 DeepSeekEndpointModelIds）
- ✅ 流式输出必须以 delta 汇入 `llm/stream` waterfall
- ❌ 不得绕过 llm seam 直连暴露给 agentLoop（消费方只依赖 `llm` 服务）
- ❌ 不得在适配器内自建重试/熔断/记账（横切插件职责）
- ❌ 不得硬编码密钥/URL/模型名到源码（全部配置化）
- ❌ 不得在卸载时中断进行中请求（优雅排空）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 适配器注册 | `DeepSeekAdapterRegisterTest`：启动后 `llm.getProvider('deepseek')` 可解析 |
| V2 | 非流式调用 | `DeepSeekAdapterChatTest`：WireMock 断言请求体（模型/参数）与响应解析 |
| V3 | 流式调用 | `DeepSeekAdapterStreamTest`：delta 序列经 `llm/stream` 输出，done 事件收尾 |
| V4 | reasoner 档位 | `DeepSeekAdapterReasonerTest`：reasoner 请求使用更高 max-tokens |
| V5 | BYOK 密钥轮换 | `DeepSeekByokKeyTest`：多密钥池轮换；`credentials/updated` 后重载 |
| V6 | 卸载排空 | `DeepSeekAdapterDrainTest`：卸载后进行中请求完成，新请求拒绝 |

### 运行命令

```bash
mvn -pl libs/gnex-llm-adapt test "-Dtest=DeepSeek*Test"
```

## §7 影响域

- 作用范围：**会话级（每请求）**——deepseek provider 的每次调用。
- 被谁消费：`llm` seam（路由）；间接被 agentLoop/标题/压缩/web 消费。
- 故障影响半径：适配器缺陷 = deepseek 调用失败（其他 provider 不受影响）。
- 升级影响：适配器装卸=无感（注册表增删，消费方无感知）。

## §8 后续（未纳入首版）

- 多 base-url 负载均衡（地域就近）
- reasoner 思考内容流（thinking 增量单独事件通道）
- V3 兼容 JSON 模式 / 工具调用严格模式参数透传

---

*GNEX3.0 DeepSeek 适配器 — deepseek-chat/reasoner provider 实现 SDD。*
