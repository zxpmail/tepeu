# v3-spec-ent-memory-providers

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-memory-providers
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-memory-providers
> **分层**：企业层（L4）
> **受保护级别**：protected（平台持有，禁租户/Agent 卸载）
> **宿主服务**：agent-service（热路径召回/注入侧）
> **实现状态**：规划中

---

## §1 业务目标

企业记忆的**语义层**（L0-L4 provider）：把 gnex2.0 Memory 系统（`GnexMemoryService`/`MemoryIndex`/`MemoryPromoteTool`）改造为以 `memory` SoR 为唯一存储的 provider 注册表——四层语义 shared/project/personal/agent 是**权限模型**（映射到温/冷 scope），不是文件路径；promote/rollback 是写温/冷新 scope，不是 `mv` 本机文件；personal 默认关闭（ADR-016），重开必须落温+冷。召回走 SoR 一次召回路径（JWT 硬过滤 → 温库候选 → 冷正文 → 打分截断 → 可写热缓存 → 注入 `<memory_context>` + fence → 输出 scrub），召回三态（未召回/召回未注入/注入未改行为）可观测。

**非目标**：不做存储（归 ent-memory）；不做打分模型选型；不做向量检索（后续）；本插件只做语义映射、promote/rollback 产品动作与注入纪律。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh 无此原语（dsh 无企业记忆语义平面） |
| 对标 GNEX2.0 模块 | `agent-service` memory 系统（`GnexMemoryService` L1/L2 管线 / `MemoryIndex` / `MemoryPromoteTool` / `MemoryController` / `AutoMemoryHook`——L1 盲写必须停，改造点） |
| 技术选型 | 🅶 GNEX2.0 模块改造（Memory 系统收敛为 provider 语义层，存储底座换 ent-memory） |
| 改造要点 | ① provider 注册表（L0-L4：shared/project/personal/agent）映射温/冷 scope ② promote/rollback 走 `memory/promote`·`memory/rollback`（写新 scope，非 mv 文件）③ 停 AutoMemory 盲写（写入闸过滤）④ 召回三态指标（未召回/召回未注入/注入未改行为）⑤ 会话窗内已上下文不召回、子会话不全量灌父 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `memoryProviders` | `com.gnex.plugin.enterprise.memory.MemoryProviderRegistry` | 四层 provider 注册/查询、召回执行（经 `memory` SoR）、promote/rollback 动作、注入组装 |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`tenant`、`memory`（SoR）、`audit`
- 可选：`observability`（三态指标）、`llm`（注入后的行为观测）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `memory/recall` | listen（触发） | {tenantId, projectId, uid, sessionId, query}：SoR 已过滤 → provider 打分注入 |
| `memory/promote` | emit | {entryId, from, to, evidenceRef}：personal→project→shared 产品动作 |
| `memory/rollback` | emit | {entryId, toVersion, reason}：可删可回滚，冲突不静默覆盖 |

## §4 配置与装配

```yaml
# service-assembly.yml 片段（agent-service）
plugins:
  - id: gnex3-ent-memory-providers
    load: mandatory
    protection: platform
settings:
  memoryProviders:
    personal: { enabled: false }        # ADR-016 默认关闭；重开须落温+冷
    injection: { maxTokens: 2048, fence: "<memory_context>", scrubOutput: true }
    sessionWindow: { skipAlreadyInContext: true, noChildFullDump: true }
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 四层语义（shared/project/personal/agent）必须映射温/冷 scope（权限模型，非文件路径）
- ✅ promote 必须写温/冷新 scope（不是 `mv` 本机文件）；可删可回滚，冲突不静默覆盖（K）
- ✅ 召回必须一次路径：JWT 硬过滤 → 温库候选 → 冷正文 → 打分截断 → 可写热缓存 → 注入 `<memory_context>` + fence → 输出 scrub
- ✅ 召回三态必须可观测（未召回/召回未注入/注入未改行为）
- ✅ 会话窗内已在上下文的对话不得再召回；子会话不得全量灌父
- ❌ 禁止 AutoMemory 盲写执行摘要/工具流水/超时重试/失败堆栈/整份 Spec（写入闸，L1 盲写停止）
- ❌ 禁止 provider 自称另一套存储 SoR（语义 SoR 唯一：ent-memory；其它是管线或叙事）
- ❌ personal 默认开启或落本机 `drafts.md`（ADR-016）；禁止串租户注入（P0）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 四层 scope 映射 | `ProviderScopeMapTest`：四层条目分别落温/冷对应 scope |
| V2 | promote 写新 scope | `ProviderPromoteTest`：personal→project 后温/冷新 scope 可读，本机 drafts 不动 |
| V3 | rollback 可删可回滚 | `ProviderRollbackTest`：回滚到版本 N，冲突抛错不静默覆盖 |
| V4 | 盲写拦截 | `ProviderWriteGateTest`：AutoMemory 执行摘要被拒（无显式意图） |
| V5 | 召回三态可观测 | `ProviderRecallStateTest`：三种状态均有指标输出 |
| V6 | 会话窗去重 | `ProviderSessionWindowTest`：已在上下文的对话不召回；子会话不全量灌父 |
| V7 | 注入隔离 | `ProviderInjectionTest`：跨租户注入为空 + fence/scrub 生效 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=ProviderScopeMapTest,ProviderPromoteTest,ProviderRollbackTest,ProviderWriteGateTest,ProviderRecallStateTest,ProviderSessionWindowTest,ProviderInjectionTest"
```

## §7 影响域

- 作用范围：**会话级/租户级**——每次请求的召回与注入。
- 被谁消费：agentLoop（`agent/request` 前召回）、prompt 组装、UI 记忆库页面（observability 只读）。
- 故障影响半径：注入错误 = 上下文污染/串租户（P0）；provider 语义漂移 = 假记忆（P0-b）。
- 升级影响：🔒 平台级升级；provider 增删 = 无感（其余不动）。

## §8 后续（未纳入首版）

- 向量召回 provider（pgvector/Milvus，检索平面就绪后）
- 分角色进模策略表（P2 F+H：结构化引用进模）
- 记忆库 UI 三态可视化（对接 observability MemoryQueryInterface）

---

*GNEX3.0 记忆提供方 — L0-L4 语义层 SDD。*
