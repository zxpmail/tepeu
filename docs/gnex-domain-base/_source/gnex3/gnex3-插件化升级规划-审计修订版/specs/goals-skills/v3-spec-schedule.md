# v3-spec-schedule

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.8
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-schedule
> **分层**：能力层（L3）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

定时调度（对齐 dsh-schedule）：提供 schedule 服务——cron/间隔任务注册、触发、暂停/恢复、执行回执，对齐 GNEX2.0 已有的 AgentScheduler / AlwaysOnTaskService（"定时任务（部分）"）。GNEX3.0 把调度提升为事件化原语：调度变更 emit schedule/change（UI/观测同步），到点触发经 agentLoop 执行（可走命令通道 command/run → command/done 回执）。约束：租户隔离（调度任务不得跨租户执行），失败语义显式（重试/跳过/告警）。

**非目标**：不做分布式任务队列（保留 Redis Stream 消息机制）；不做 cron 表达式引擎自研（用标准 cron 解析）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | @deepseek-ai/dsh-schedule |
| 对标 GNEX2.0 模块 | agent-service AgentScheduler + AlwaysOnTaskService（定时任务部分） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① 调度注册表事件化（schedule/change）；② 触发执行经 agentLoop 统一走工具管线（审批/权限不绕过）；③ command/done 回执记录执行结果；④ 租户/项目 scope 绑定任务；⑤ 失败语义显式（max-retry/跳过策略） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `schedule` | com.gnex.cap.schedule.ScheduleService | cron/间隔任务注册、触发、暂停/恢复、回执查询 |

### 3.2 注入服务（inject）

- `agentLoop`（必选）— 到点触发执行
- `audit`（必选）— 调度创建/变更/执行审计
- `goals`（可选）— 定时目标任务执行
- `jobs`（可选）— 后台任务承载长执行

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `schedule/change` | emit | {scheduleId, tenantId, action(create/update/pause/delete)} |
| `command/run` | emit | 触发命令通道任务（可选，经 l0-commands 消费） |
| `command/done` | listen | 执行回执 → 记录结果/重试决策 |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-cap-schedule
    load: mandatory
    config:
      schedule:
        executor-pool: 4
        default-max-retry: 2
        miss-policy: skip   # skip/fire-immediately
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 调度任务必须绑定租户/项目 scope，触发时先过滤再执行（串租户=P0）
- ✅ 调度触发的执行必须走 agentLoop 工具管线（审批/权限/沙箱不绕过）
- ✅ 每次执行必须产出回执（成功/失败/重试）并经 audit 落审计
- ❌ 调度任务不得在无 scope 上下文下执行（禁止匿名/全局任务）
- ❌ 失败不得无限重试（max-retry 硬上限，超限标记 failed 并告警）
- ❌ 调度插件不得持有凭据明文（凭证经 credentials 服务引用）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | cron 注册与触发 | ScheduleServiceTest：cron 到点触发一次执行 |
| V2 | 变更事件 | ScheduleChangeEventTest：create/pause/delete 各 emit schedule/change |
| V3 | 执行回执 | ScheduleReceiptTest：command/done 回执更新执行结果 |
| V4 | 失败重试上限 | ScheduleRetryTest：超 max-retry 标记 failed 且不再触发 |
| V5 | 租户隔离 | ScheduleScopeTest：跨租户任务不可见/不可触发 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=ScheduleServiceTest,ScheduleChangeEventTest,ScheduleReceiptTest,ScheduleRetryTest,ScheduleScopeTest"
```

## §7 影响域

- 作用范围：**会话/租户级**（定时任务按 scope 执行）；故障影响半径：单任务失败不影响主进程。
- 被谁消费：agentLoop、UI 调度页、观测/告警、审计。
- 升级影响：调度 schema 变更需 UI 与持久化同步；触发机制变更=全定时任务回归。

## §8 后续（未纳入首版）

- 分布式调度（多副本互斥触发，对齐 Redis Stream 抢占）
- 时区/夏令时策略
- 调度与目标联动（目标未完成自动续调度）

---

*GNEX3.0 定时调度 — gnex3-cap-schedule SDD。*
