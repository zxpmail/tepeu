# v3-spec-tool-jobs

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-jobs
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

给模型提供后台任务原语：提交/查询/取消长耗时命令或工具调用，返回 jobId 后回合继续，任务与发起会话解耦（断线不丢、可恢复查询）。状态机（queued→running→done/failed/cancelled）单一定义，状态变更经 `command/run` · `command/done` 事件广播（对齐 dsh 命令事件），供 UI 与 session-loop 恢复消费。结果超限截断或 spill，禁止整段进上下文。**非目标**：跨服务任务队列（v3.0 内经 bus 分发）与任务重试策略（留 §8）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-jobs` |
| 对标 GNEX2.0 模块 | 无（新增）；参考 agent-service `AgentJobService`/`agent_job` 作业表语义与 `MacroTool` 运行时委托模式（registry 定义元数据、agent-service 执行，MacroRuntimePort） |
| 技术选型 | 🅳 Java 移植 dsh-tool-jobs（新增） |
| 改造要点 | ① 新增后台任务原语：提交/查询/取消 + 状态机单一定义 ② 任务与发起会话解耦（断线续跑可恢复） ③ 运行时委托模式参考 2.0 MacroTool/MacroRuntimePort（元数据与执行分离） ④ 状态经 command/run · command/done 广播 ⑤ 结果超限截断或经 `spill` 溢出 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `jobs` | `JobsService` | 任务提交/查询/取消/状态订阅 |

### 3.2 注入服务（inject）

- `tools`（必选）：注册 tool-jobs 工具回调；`fiber`/`events`（必选）：任务生命周期与事件广播
- `scope`（必选）：会话/租户 realm 隔离；`storage`（可选）：任务/结果持久化（断线恢复）
- `spill`（可选）：超长结果溢出存储

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `command/run` | emit | {jobId, command, sessionId, tenantId, submittedAt} |
| `command/done` | emit | {jobId, status, result, durationMs} |

## §4 配置与装配

```yaml
# service-assembly.yml 片段
plugins:
  - id: gnex3-cap-tool-jobs
    load: mandatory
gnex.tool.jobs:
  max-concurrent: 8        # 并发任务上限
  timeout-ms: 1800000      # 单任务超时
  result-max-bytes: 65536  # 结果上限（超出截断/spill）
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 后台任务必须与发起会话解耦（会话断线任务继续，恢复后可查询）；状态机必须单一定义，状态迁移必须可观测（command/run → command/done）
- ✅ 必须支持取消；取消必须停止执行并回卷副作用；结果超 result-max-bytes 必须截断或 spill，禁止整段进上下文
- ❌ 不得在 agent 回合主线程同步阻塞执行任务（受控执行器）；任务结果不得自动注入后续回合上下文（显式查询）
- ❌ 禁止跨租户查询/取消任务（scope 硬过滤）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 提交/查询 | `JobsSubmitTest`：提交返回 jobId，状态 queued→running→done 推进 |
| V2 | 取消 | `JobsCancelTest`：running 任务取消后状态 cancelled、执行停止 |
| V3 | 事件 | `JobsEventTest`：提交 emit command/run，终态 emit command/done |
| V4 | 断线续跑 | `JobsRecoveryTest`：会话断开任务完成，恢复后可查询结果 |
| V5 | 截断 | `JobsTruncateTest`：结果超限截断，不超上下文预算 |
| V6 | 隔离 | `JobsScopeTest`：跨租户查询/取消被拒 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service -am test "-Dtest=Jobs*Test"
```

## §7 影响域

- 作用范围：**会话级/服务级**——任务生命周期跨会话存活。
- 被谁消费：UI（任务列表）、session-loop（断线恢复）、观测（command/run · command/done）；故障影响半径：单个任务失败不影响主进程与其他任务，执行器故障 = 任务级不可用。
- 升级影响：工具装卸无感（05 §3 L3.6）；状态机/载荷变更 = 消费方同步。

## §8 后续（未纳入首版）

- 跨服务任务队列（经 bus 分发到 worker 节点）；任务重试策略与失败语义（对齐 P0 审计失败语义口径）
- 任务结果物化到 state-plane 供恢复/回放

---

*GNEX3.0 后台任务工具插件 — 新增 jobs 原语 SDD。*
