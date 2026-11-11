# v3-spec-agent-registry

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.4（Agent 循环）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-agent-registry
> **分层**：L3 能力层
> **受保护级别**：normal（管理员可装卸；禁租户/Agent 装卸）
> **宿主服务**：registry-service
> **实现状态**：规划中

---

## §1 业务目标

Agent 注册表是 Agent 元数据的唯一事实源：注册、查询、启停、装卸与状态变更全生命周期管理。gnex2.0 已有 registry/agent（AgentRegistryService / AgentRegistryPort / agent_registry 表，AgentLoader 按 agent_registry 过滤加载），本插件将其收敛为 cordis-jvm 插件并暴露 `agents` 服务，供意图路由（AgentRouter）、agentLoop、委派与 UI 消费。归属遵循 05 归属规则：注册表为宿主服务层单例，禁止会话级复制。

**非目标**：不管理 Agent 运行时（归 agent-loop）；不管理 preset 装配（归 agent-preset）；不做跨服务注册表汇聚（v3.1）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-agent`（Agent 注册/生命周期） |
| 对标 GNEX2.0 模块 | `registry/agent`（AgentRegistryService、AgentRegistryPort、agent_registry 表、AgentLoader 过滤加载） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① AgentRegistryPort 实现改造成插件并提供 `agents` 服务；② 元数据含灵魂契约（SoulContractExtractor 提取的 Agent 契约）；③ 生命周期事件化（agent/created · disposed · status）；④ 装卸权归平台管理面，租户/Agent 无装卸权 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `agents` | `com.gnex.plugin.agent.AgentRegistry` | 注册/查询/启停/装卸/列表；按 scope 租户硬过滤 |

### 3.2 注入服务（inject）

`scope`（必选，租户硬过滤）、`events`（必选，生命周期事件）、`settings`（可选）、`storage`（可选，持久化后端）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `agent/created` | emit | {agentId, name, tenantId, version, capabilities} |
| `agent/disposed` | emit | {agentId, reason} |
| `agent/status` | emit | {agentId, status, ts} |

## §4 配置与装配

```yaml
# registry-service service-assembly.yml
plugins:
  - id: gnex3-cap-agent-registry
    load: mandatory
settings:
  agentRegistry:
    defaultStatus: ACTIVE
    allowTenantUninstall: false   # 租户不可卸载（装卸权仅管理员）
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 注册表查询/变更必须经 `scope` 租户硬过滤（串租户 = P0，对齐 scope 先硬过滤再打分）
- ✅ 元数据变更必须 emit `agent/status`（可观测，05 agent/* 词汇）
- ✅ 卸载必须拒绝/回收依赖该 Agent 的活跃运行与委派
- ❌ 不允许 Agent 自注册/自装卸（装卸权仅平台管理员）
- ❌ 不允许注册表存储会话态（会话状态归会话 realm）
- ❌ 不允许跨租户可见（禁全局 Agent 泄露）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 注册：register 返回 agentId 且可查询 | `AgentRegistryPluginTest.registerSucceeds` |
| V2 | 冲突：同租户同名重复注册被拒 | `...duplicateRejected` |
| V3 | 租户隔离：租户 A 查不到租户 B 的 Agent | `...tenantIsolated` |
| V4 | 事件：register 后收到 `agent/created` | `...emitsCreated` |
| V5 | 卸载回收：dispose 后活跃委派被拒 | `...disposeRejectsActive` |
| V6 | 禁用：status=DISABLED 的 Agent 不可被路由 | `...disabledNotRoutable` |

### 运行命令

```bash
mvn -pl services/gnex-registry-service test "-Dtest=AgentRegistryPluginTest"
```

## §7 影响域

- 作用范围：**服务级**（registry-service 单例）。
- 被谁消费：agentLoop、委派（subagents/workflow）、AgentRouter、UI。
- 故障影响半径：注册表不可用 = 意图路由与委派无法解析 Agent。
- 升级影响：注册表结构变更需数据迁移；装卸属管理员操作。

## §8 后续（未纳入首版）

- 跨服务注册表汇聚（多副本注册表同步）
- AgentCard / A2A 发现（E27 目标路由对齐）
- Agent 定义版本化（可回滚装卸）

---

*GNEX3.0 Agent 注册表 — registry/agent 插件化 SDD。*
