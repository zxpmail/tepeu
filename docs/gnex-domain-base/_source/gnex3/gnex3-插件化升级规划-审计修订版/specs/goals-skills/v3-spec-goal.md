# v3-spec-goal

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.8
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-goal
> **分层**：能力层（L3）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service（热路径控制面）
> **实现状态**：规划中

---

## §1 业务目标

GNEX3.0 目标领域原语：把 GNEX2.0 散落在 agent-service 的 GoalService（conversation_session.goal 列 + JdbcTemplate）与 GoalController 中的"目标"提升为事件化领域模型，对齐 dsh-goal / dsh-goal-round-driver。提供 goals 服务：目标 CRUD、状态机（active/paused/cancelled/completed）、每轮 round 进度评估（生成下一步行动）。目标完成必须经 gnex3-ent-gate 完成证据门（P0-a：无证据不得 completed，禁止助手正文回退当证据），所有状态迁移 emit goal/change 供 UI/观测/审计消费。

**非目标**：不做目标→计划自动生成（归 gnex3-cap-plan）；不做跨会话目标聚合（后续）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | @deepseek-ai/dsh-goal + @deepseek-ai/dsh-goal-round-driver |
| 对标 GNEX2.0 模块 | agent-service GoalService + GoalController（conversation_session.goal） |
| 技术选型 | 🅳 Java 移植 dsh 实现（GNEX2.0 无独立目标模块，全新） |
| 改造要点 | ① Goal 领域模型 + 状态机（2.0 仅有 goal 字符串列）；② 状态迁移 emit goal/change；③ completed 出口统一挂 gate（P0-a，禁止正文回退当证据）；④ round-driver 按轮评估进度生成下一步；⑤ 租户/项目 scope 先过滤再返回（串租户=P0） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `goals` | com.gnex.cap.goal.GoalService | 目标 CRUD / 状态机 / round 驱动 / 变更订阅 |

### 3.2 注入服务（inject）

- `gate`（必选）— P0-a 完成证据门（completed 唯一出口）
- `scope`（必选）— 租户/项目硬过滤
- `audit`（必选）— 目标创建/变更/完成审计
- `userQuestions`（可选）— round 澄清目标歧义
- `skills`（可选）— round 驱动引用技能目录

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `goal/change` | emit | {goalId, tenantId, projectId, status, evidence?} |
| `command/run` | listen | /goal 命令入口（commandName=goal） |
| `command/done` | listen | 回合结束触发 round 评估（可选） |

## §4 配置与装配

```yaml
# service-assembly.yml（agent-service）
plugins:
  - id: gnex3-cap-goal
    load: mandatory
    config:
      goal:
        evidence-required: true      # P0-a：无证据不得 completed
        round-driver-enabled: true
        max-active-goals: 10         # 单会话并行目标上限
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 目标 completed 必须经 gate 证据门（P0-a），禁止正文回退当证据
- ✅ 目标查询必须先按 scope（租户/项目）硬过滤，再返回（串租户=P0）
- ✅ 状态迁移必须 emit goal/change（UI/观测/审计统一消费）
- ❌ goals 不得绕过 gate 直接置 completed（含 Todo/SSE/软 nudge 路径）
- ❌ 目标数据不得跨租户可见或可改（含缓存）
- ❌ Agent 不得自删/自篡改他人目标（写操作仅会话 owner/管理员）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 创建目标并迁移状态 | GoalServiceTest：active→paused→cancelled 各迁移 emit goal/change 一次 |
| V2 | 完成证据门 | GateIntegrationTest：无证据时 completed 被 gate 拒绝，状态保持 active |
| V3 | 故意失败 Write 不得 completed | GateDenyTest：故意失败 Write 后 round 评估不得置 completed |
| V4 | 租户隔离 | ScopeFilterTest：跨租户查询返回空，串项目被拒 |
| V5 | round 驱动 | RoundDriverTest：每轮 command/done 后生成下一步行动 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=GoalServiceTest,GateIntegrationTest,ScopeFilterTest,RoundDriverTest"
```

## §7 影响域

- 作用范围：**会话级**（单目标/会话）；写路径经 audit 落租户审计。
- 被谁消费：tool-goal、gnex3-cap-plan（计划输入）、UI 目标页、观测。
- 故障影响半径：goals 不可用 = 目标命令/round 驱动失败，会话其余能力不受影响。
- 升级影响：状态机/事件载荷变更需 UI 与审计消费方同步（版本化事件）。

## §8 后续（未纳入首版）

- 跨会话目标聚合/目标依赖图（DAG）
- 目标完成证据快照导出（对齐企业记忆 SoR 冷平面）
- round-driver LLM 策略可插拔（当前内置启发式）

---

*GNEX3.0 目标领域 — gnex3-cap-goal SDD。*
