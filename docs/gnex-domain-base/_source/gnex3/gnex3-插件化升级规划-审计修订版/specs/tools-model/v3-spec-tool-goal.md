# v3-spec-tool-goal

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-goal
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

给模型提供读写会话目标的工具面（get/set/clear），让目标成为可观测、可广播的会话状态而非模型记忆里的口头承诺。目标读写统一经 `goals` 领域服务（gnex3-cap-goal）持久化，变更即 emit `goal/change`（含单调递增 revision），供 prompt 组装、UI、观测消费。gnex2.0 已有 GoalService，本插件将之事件化改造并移植 dsh-tool-goal 的工具契约。

**非目标**：目标「完成」状态机与完成证据（P0-a 口径：完成权归 gate）；多目标/优先级（留 §8）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-goal` |
| 对标 GNEX2.0 模块 | agent-service `GoalService`（agent.service）+ GoalController（PUT/GET /api/sessions/{sessionId}/goal，conversation_session.goal 列） |
| 技术选型 | 🅳 Java 移植 dsh-tool-goal（新增工具插件；目录口径：无对应新增） |
| 改造要点 | ① gnex2.0 已有 GoalService（agent.service），改造为事件化 goal/change 底座：写入/更新目标后广播 {sessionId, goal, revision} ② 移植 dsh-tool-goal 工具面（get/set/clear） ③ 读写统一经 `goals` 服务，禁止直改表 ④ 与 gate 解耦：goal 无 completed 状态机（P0-a），完成判定归 gate |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

无（纯工具插件；目标领域由 gnex3-cap-goal 提供 `goals` 服务，本插件只消费）。

### 3.2 注入服务（inject）

- `tools`（必选）：注册 tool-goal 工具回调
- `goals`（必选）：目标领域服务（gnex3-cap-goal 提供）
- `events`（必选）：goal/change 广播
- `scope`（必选）：会话 realm 隔离

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `goal/change` | emit（经 goals 服务转发） | {sessionId, goal, revision, action, updatedBy} |

## §4 配置与装配

```yaml
# service-assembly.yml 片段
plugins:
  - id: gnex3-cap-tool-goal
    load: mandatory
gnex.tool.goal:
  max-length: 2000   # 目标文本上限
  history-size: 10   # 会话目标历史保留条数
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 工具必须经 goals 服务读写目标，禁止直改 conversation_session 表
- ✅ 目标写入/更新必须触发 goal/change（revision 单调递增）
- ✅ 必须限定会话/租户 scope（目标不跨会话注入）
- ❌ 不得在无显式模型意图时擅自创建/改写目标
- ❌ goal 工具不得产生「完成」判定或完成证据（完成权归 gate，P0-a）
- ❌ 不得把目标全文无条件注入每轮上下文（按 prompt 策略裁剪）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 读写 | `GoalToolCrudTest`：get/set/clear 经 goals 服务持久化并回读一致 |
| V2 | 事件 | `GoalChangeEventTest`：set 后 emit goal/change，revision+1 |
| V3 | scope | `GoalToolScopeTest`：会话 B 读不到会话 A 的目标 |
| V4 | 无完成权 | `GoalToolNoCompletionTest`：goal 工具返回不含 completed 状态/证据 |
| V5 | 注册 | `GoalToolRegistryTest`：tools 含 tool-goal 定义（name/desc/JSON schema） |

### 运行命令

```bash
mvn -pl services/gnex-agent-service -am test "-Dtest=GoalTool*Test"
```

## §7 影响域

- 作用范围：**会话级**——目标属会话 realm。
- 被谁消费：agentLoop/prompt（目标注入）、UI（目标展示）、观测（goal/change）。
- 故障影响半径：单次目标读写失败不影响回合主流程，事件丢失可重放。
- 升级影响：工具装卸无感（05 §3 L3.6）；goal/change 载荷变更 = 消费方同步。

## §8 后续（未纳入首版）

- 目标状态机与 gate 完成证据链联动（P0-a 过线后）
- 多目标/优先级/目标拆解
- 目标变更审计（audit 消费 goal/change）

---

*GNEX3.0 目标工具插件 — GoalService 事件化 goal/change SDD。*
