# v3-spec-model-default

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.1 模型路由 · 05-interface-relationships.md §1/§2
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-model-default
> **分层**：L3 能力层（3.1 模型路由）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

默认模型选择：向入口提供 `agentDefaultModel` 服务，解析「未显式指定模型」时的默认模型——支持全局默认、场景默认（FAST/STANDARD/DEEP）、agent 覆盖三级解析（对齐 dsh-agent-default-model 与 gnex2.0 `LLMConfigManager` 语义）。默认模型必须在 llm 适配器注册表中存在；适配器卸载导致默认模型失效时自动回退到 fallback 并告警，保证入口永不因默认模型解析失败而中断。

**非目标**：不做请求级路由（seam 职责）；不做模型可用性探测（注册表为准）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-agent-default-model` |
| 对标 GNEX2.0 模块 | `gnex-llm-adapt` / agent-service config 包的 `LLMConfigManager`（模型配置解析）、`DeepSeekEndpointModelIds` |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① 以 `LLMConfigManager` 配置解析为基座，升级为 `agentDefaultModel` 服务（ctx 服务名对齐 05）② 解析优先级：agent 覆盖 > 场景默认 > 全局默认（显式优先）③ 校验默认模型在 llm 注册表中存在；监听 `llm/adapters-updated` 失效重校验 ④ 失效回退链：默认 → fallback → 告警（不抛错）⑤ 纯配置解析，禁网络调用 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `agentDefaultModel` | `com.gnex.model.default.AgentDefaultModelService` | 默认模型解析：`resolve(scenario, agentId)` / `getGlobalDefault()` |

### 3.2 注入服务（inject）

- `settings`（必选）：默认模型配置段（`gnex.model.default.*`）
- `llm`（必选）：注册表校验（默认模型必须存在）
- `events`（可选）：失效告警上报

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `llm/adapters-updated` | listen | 适配器变更 → 重校验默认模型；失效则回退 fallback 并 emit 告警日志 |

## §4 配置与装配

```yaml
gnex.model.default:
  model: deepseek-chat               # 全局默认
  scenario-defaults:
    FAST: deepseek-chat
    STANDARD: deepseek-chat
    DEEP: deepseek-reasoner
  fallback: deepseek-chat            # 失效回退
  agent-override:
    enabled: true                    # 允许 agent 定义覆盖默认模型
```

装配声明：`gnex3-cap-model-default` 为 agent-service optional 插件，依赖 `gnex3-cap-model-llm` 与 `gnex3-l1-settings`。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 解析优先级必须为 agent 覆盖 > 场景默认 > 全局默认（显式声明优先）
- ✅ 默认模型必须经 llm 注册表校验存在；不存在则启动告警/拒绝
- ✅ 适配器卸载致默认模型失效时，必须回退 fallback 并告警（入口不中断）
- ✅ agent 覆盖必须显式声明（agent 定义文件或配置），未声明用全局默认
- ❌ 不得在源码硬编码模型名（全部走配置）
- ❌ 不得在默认模型解析中做网络调用（纯配置+注册表校验）
- ❌ 不得把默认模型解析失败直接抛给入口（先回退再告警）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 全局默认 | `DefaultModelGlobalTest`：未指定场景/agent 时返回 `deepseek-chat` |
| V2 | 场景默认 | `DefaultModelScenarioTest`：DEEP 场景返回 `deepseek-reasoner` |
| V3 | agent 覆盖 | `DefaultModelAgentOverrideTest`：agent 显式声明覆盖生效 |
| V4 | 失效回退 | `DefaultModelFallbackTest`：卸载 `deepseek-chat` 后回退 fallback 并产生告警 |
| V5 | 配置缺失 | `DefaultModelMissingConfigTest`：未配置默认模型时抛 ConfigError |
| V6 | 适配器更新重校验 | `DefaultModelAdaptersUpdatedTest`：`llm/adapters-updated` 后缓存失效重校验 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=DefaultModel*Test"
```

## §7 影响域

- 作用范围：**会话级**——每个新会话/请求的默认模型解析。
- 被谁消费：入口（agentLoop/入口工厂）；间接被 agent/标题/压缩消费。
- 故障影响半径：默认模型解析失败 = 该请求无法路由（有回退兜底）。
- 升级影响：默认值变更=新会话生效（进行中会话不受影响）。

## §8 后续（未纳入首版）

- 按租户默认模型（tenant 配置段，对齐企业层配额体系）
- 默认模型 A/B 灰度（百分比流量切换，观测驱动）
- 按时间窗口默认模型切换（高峰 DEEP 档、低峰 FAST 档）

---

*GNEX3.0 默认模型选择 — agentDefaultModel 三级解析 SDD。*
