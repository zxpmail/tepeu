# v3-spec-agent-preset

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.4（Agent 循环）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-agent-preset
> **分层**：L3 能力层
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-32（§2 选型订正）· P1-1（§4 补 plugins 装配条目）

---

## §1 业务目标

Agent 会话装配 preset：把 Agent 定义、技能、模型、工具集与系统提示模板按 preset 模板装配成可运行的会话配置快照，供会话创建/agentLoop 消费。对齐 dsh-agent-presets 的预设装配语义，复用 gnex2.0 agent 定义装配 + skill 加载的既有素材（AgentLoader / SkillRegistry / PromptAssemblyLayers）。preset 只是配置，不是执行代码——不引入任何可执行/可装卸能力。

**非目标**：不管理注册表（归 agents）；不加载技能实现（归 skills）；不组装最终系统提示（归 l1-prompt，本插件只输出装配清单）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-agent-presets`（Agent 预设装配） |
| 对标 GNEX2.0 模块 | `agent-service` agent 定义装配 + skill 加载（**AgentLoader** / SkillRegistry / PromptAssemblyLayers 人设·章宪·配方序） |
| 技术选型 | 🅶 GNEX2.0 模块改造——**AgentLoader 改造为装配器**（D-P1-32）：与 04 目录修订版该行一致（`AgentLoader 改造为装配器（D-P1-32）`）；AgentLoader 为 gnex2.0 既有类（源码实证），改造后承担 preset 装配职责（build/snapshot/validate），更贴现状、避免新建装配链路 |
| 改造要点 | ① **AgentLoader 改造为装配器**（D-P1-32）：保留其装载入口，扩展为 preset 模式装配（人设/章宪/配方序分层，对齐 l1-prompt）② 装配校验（引用 Agent/技能存在且 scope 可见）③ preset 与 scope 绑定（租户/项目可覆盖默认 preset）④ 装配产物物化为会话 realm 配置，不写注册表 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `agentPresets` | `com.gnex.plugin.agent.AgentPresets` | preset 定义/查询/装配（build/snapshot/validate），由改造后的 AgentLoader 装配器承载 |

### 3.2 注入服务（inject）

`agents`（必选，引用校验）、`prompt`（必选，提示分层模板）、`scope`（必选，租户/项目覆盖）、`skills`（可选，技能绑定）、`settings`（可选）、`events`（可选，缓存失效）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `agent/created` | listen | 装配缓存失效（Agent 变更） |
| `skills/change` | listen | 装配缓存失效（技能目录变更） |

## §4 配置与装配（P1-1）

```yaml
plugins:
  - id: gnex3-cap-agent-preset
    load: optional            # normal 级：管理员可装卸
    inject:
      - agents                # 必选：引用校验
      - prompt                # 必选：提示分层模板
      - scope                 # 必选：租户/项目覆盖
      - skills                # 可选：技能绑定
settings:
  agentPresets:
    defaultPreset: default
    allowProjectOverride: true
    layers: [charter, persona, recipes]
presets:
  default:
    agent: assistant
    skills: [fs, bash, web]
    model: deepseek-chat
```

> 装配说明：plugins 装配条目声明插件 id / load 级别 / inject 依赖清单（P1-1 补全），与 plugin-runtime-assembly 的装配声明格式对齐；`agentPresets` 服务由改造后的 AgentLoader 装配器提供（D-P1-32）。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ preset 装配必须校验引用 Agent/技能存在且 scope 可见（租户硬过滤）
- ✅ 装配产物只落会话 realm，不得写 `agents`/`skills` 注册表
- ✅ preset 变更必须以新版本快照装配（运行中会话不受影响）
- ✅ 装配器实现基于 AgentLoader 改造（禁另起炉灶新建并行装配链路，D-P1-32）
- ❌ 不允许 preset 引用受保护（🔒）服务或获得装卸权
- ❌ 不允许 preset 内嵌代码/脚本/插件（preset 是配置，不是执行代码）
- ❌ 不允许跨租户引用（scope 过滤）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 装配：build 返回含 agent/skill/model/prompt 的快照 | `AgentPresetPluginTest.buildSnapshot` |
| V2 | 校验：引用不存在 Agent 拒绝装配 | `...missingAgentRejected` |
| V3 | 租户：跨租户引用被 scope 过滤拒绝 | `...tenantIsolated` |
| V4 | 快照：装配后修改 preset 不影响已建会话 | `...snapshotImmutable` |
| V5 | 默认：未指定 preset 使用 defaultPreset | `...defaultPresetApplied` |
| V6 | 装配条目（P1-1） | `AgentPresetPluginTest.pluginAssemblyEntry`：plugins 装配条目（id/load/inject 清单）经装配器解析成功，缺 `agents`/`prompt`/`scope` 必选依赖时 fail-fast |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=AgentPresetPluginTest"
```

## §7 影响域

- 作用范围：**会话级**（新会话装配时）。
- 被谁消费：会话创建入口、agentLoop、UI。
- 故障影响半径：新会话装配失败（既有会话不受影响）。
- 升级影响：preset 模式变更 = 新会话行为变化。

## §8 后续（未纳入首版）

- preset 模板共享/市场（组织级 preset 库）
- preset 版本化与审计（变更可追溯）
- preset 与权限/审批联动（受控 preset 仅管理员可改）

---

*GNEX3.0 Agent 会话装配 — preset·AgentLoader 装配器 SDD。*
