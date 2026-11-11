# v3-spec-plan

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.8
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-plan
> **分层**：能力层（L3）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

计划模式（对齐 dsh-plan-mode）：在目标或命令进入执行前先生成计划、按计划执行并跟踪进度，避免"无计划直接干"。GNEX2.0 已有 PlansPage 后端（platform-service PlanController + AgentFlowPlanPort + agent-service PlanGenerator），但计划生成是孤立 REST 调用、不随目标/命令事件驱动。本插件把计划模式做成行为插件：消费 goal/change 与 command/run 触发重规划，计划状态与进度附着于 goals 域，执行经 agentLoop。

**非目标**：不做工作流引擎（归 gnex3-cap-workflow）；不做计划 UI 重写（沿用 PlansPage）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | @deepseek-ai/dsh-plan-mode |
| 对标 GNEX2.0 模块 | platform-service PlanController/AgentFlowPlanPort（PlansPage 后端）+ agent-service PlanGenerator |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① 计划生成由 REST 调用改为事件驱动（goal/change、command/run → 重规划）；② 计划状态/步骤进度写入 goals 域（目标附着计划）；③ 执行经 agentLoop，步骤完成回写；④ 计划产物结构化（步骤/证据指针），不做正文回退当进度 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| —（无独立服务） | 行为模式插件 | 计划模式经 goals 状态附着目标，执行经 agentLoop，不暴露跨插件服务 |

### 3.2 注入服务（inject）

- `goals`（必选）— 目标读取与计划状态回写
- `userQuestions`（必选）— 计划澄清（目标歧义/步骤确认）
- `skills`（可选）— 计划步骤引用可用技能
- `audit`（必选）— 计划生成/变更审计
- `agentLoop`（可选）— 计划步骤执行驱动

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `goal/change` | listen | 目标变更 → 触发重规划 |
| `command/run` | listen | 命令进入计划模式 → 先出计划再执行 |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-cap-plan
    load: mandatory
    config:
      plan:
        mode: auto          # auto/always/off
        steps-max: 8
        re-plan-on-goal-change: true
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 计划生成必须基于当前目标（goals 读取），步骤必须可执行（引用已注册技能/工具）
- ✅ 计划状态变更必须回写 goals 域并经 audit 审计
- ✅ 计划产物必须结构化（步骤 + 证据指针），禁止正文回退当进度
- ❌ 计划模式不得跳过执行前检查（权限/审批仍走工具管线 pre-execute）
- ❌ 计划不得伪造完成（步骤完成必须对应真实执行回执，对齐 P0-a 精神）
- ❌ 计划插件不得持有租户外目标数据（scope 先过滤）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | goal/change 触发重规划 | PlanEventTest：目标更新后自动生成新计划 |
| V2 | command/run 计划模式 | PlanModeTest：plan.mode=always 时命令先出计划再执行 |
| V3 | 步骤完成回写 | PlanStepTest：步骤执行回执后计划进度更新 |
| V4 | 伪造完成拒绝 | PlanFakeCompleteTest：无执行回执的步骤不得标记完成 |
| V5 | 计划产物结构化 | PlanArtifactTest：计划 JSON 含步骤/证据指针，无正文回退字段 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=PlanEventTest,PlanModeTest,PlanStepTest,PlanFakeCompleteTest,PlanArtifactTest"
```

## §7 影响域

- 作用范围：**会话级**（计划模式作用于会话执行）；经 audit 落租户审计。
- 被谁消费：goal 插件（目标附着计划）、PlansPage 前端、agentLoop（执行）。
- 故障影响半径：plan 不可用 = 计划模式失效（fallback 直执行，需显式配置），会话不中断。
- 升级影响：计划 schema 变更需 PlansPage 与执行侧同步。

## §8 后续（未纳入首版）

- 计划执行中断恢复（断点续跑对齐 run.continue 机制）
- 计划步骤级预算/超时策略
- 计划与工作流引擎（gnex3-cap-workflow）互转

---

*GNEX3.0 计划模式 — gnex3-cap-plan SDD。*
