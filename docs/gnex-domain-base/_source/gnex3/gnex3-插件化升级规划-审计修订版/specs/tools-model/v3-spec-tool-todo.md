# v3-spec-tool-todo

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-todo
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

任务清单工具：Agent 维护会话级待办清单（add/update/mark-complete/list）。todo 是"计划-执行"的可见状态，随会话可回放、可审计（谁/何时勾完，操作者= turnId/agentId）。对齐 P0-a：todo 勾完只是清单状态，**不得作为完成证据**（T3 缺口修复：勾完无证物不得呈现为交付成功；触顶禁止静默 `abandonIncompleteAsCompleted`）。变更入会话日志（事件溯源）。

**非目标**：不做跨会话任务系统（schedule/agent-job 归调度插件）；不做目标状态机（gnex3-cap-goal）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-todo` |
| 对标 GNEX2.0 模块 | agent-service `GnexTodoWriteTool`（TodoWriteTool） |
| 技术选型 | 🅶 GNEX2.0 模块改造（TodoWriteTool → 管线工具插件 + P0-a 对齐） |
| 改造要点 | ① 注册 `tool_todo_write`/`tool_todo_list` 到 `tools` ② 会话级 todo 状态（scope realm），append-only 变更事件可回放 ③ 每次变更带操作者（turnId/agentId）+ 时间戳 ④ 勾完/触顶仅改清单状态，不触发完成定案 ⑤ 清单内容进会话日志（sessions 事件溯源） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `todoTools` | `TodoTool` | add/update/complete/list 实现（注册进 `tools`） |

### 3.2 注入服务（inject）

- `tools`（必选）— 注册工具定义
- `scope`（必选）— 会话 realm 状态
- `sessions`（必选）— 变更入会话日志（可回放）
- `events`（必选）— 管线收发

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/pre-execute` | listen | 状态预检（清单上限/项存在性） |
| `tools/execute` | listen（waterfall） | 本体：变更 + 追加变更事件 |
| `session/event` | emit | todo 变更事件入会话日志（可回放） |

## §4 配置与装配

```yaml
gnex.tools.todo:
  max-items: 50
  complete-needs-evidence: false     # 勾完只是状态；完成定案归 gate/check
  forbid-abandon-as-completed: true  # 触顶禁 abandonIncompleteAsCompleted
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ todo 状态为会话级（scope realm），变更可回放、可审计（谁/何时）
- ✅ 勾完/触顶只改清单状态，**不得作为完成证据**（P0-a：无证物不得定案）
- ✅ 变更必须入会话日志（事件溯源）
- ❌ 触顶不得静默 `abandonIncompleteAsCompleted`（未完成项必须显式呈现）
- ❌ 完成证据门不得被工具结果绕过（todo 勾完 ≠ gate/check 通过）
- ❌ 不得跨会话/跨租户读写 todo（串 scope = P0）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 增查改 | `TodoCrudTest`：add/list/update/complete 状态流转正确 |
| V2 | 会话隔离 | `TodoScopeIsolationTest`：A 会话清单不影响 B |
| V3 | 变更回放 | `TodoReplayTest`：变更事件序列可重放还原状态 |
| V4 | 勾完非证据 | `TodoNotEvidenceTest`：全部勾完但无交付证据 → gate/check 仍 deny completed |
| V5 | 触顶不吞 | `TodoNoSilentAbandonTest`：触顶提示未完成项，不静默完成 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=Todo*Test"
```

## §7 影响域

- 作用范围：**每工具调用**（L3.6 工具集）+ 完成定案口径（P0-a 关联）。
- 被谁消费：Agent 循环；UI（清单呈现）；`gate`（证据门对照）。
- 故障影响半径：单工具不可用；清单状态缺陷 = 交付口径误导（V4/V5 为发布门）。
- 升级影响：工具装卸 = 无感。

## §8 后续（未纳入首版）

- todo 与 goal 状态机联动（goal/change 事件订阅）
- 多 Agent 共享清单（房间/团队场景）
- 清单导出（会话导出联动）

---

*GNEX3.0 任务清单工具 — gnex3-cap-tool-todo SDD。*
