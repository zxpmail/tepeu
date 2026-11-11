# v3-spec-tools-timeout

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.5 工具注册表与管线
> **审计修订：v0.2（2026-08-18）· 依据 D-P1-06 / D-P1-09 / P2-09**
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tools-timeout
> **分层**：L3 能力层 · 3.5 工具注册表与管线
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

工具调用超时策略：为每个工具调用提供可配置的超时上限（默认 + 按工具覆盖 + 按沙箱档位分级），超时后中止执行并把结果如实标记 `TIMED_OUT`（含耗时与阶段），防止工具挂死拖住整个 Agent 回合。作为 `tools/execute` 的 **最外层 wrapper** 挂入管线（wrapper 顺序：timeout（最外）→ spill → 本体，D-P1-06）对全部工具统一生效；超时结果进入 `tool/result` 观测与审计，耗时/阶段可拆分。

**职责界定（D-P1-06）**：本插件**只管超时**（计时、到点取消、结果标记）。v3.0 **无工具级自动重试**——工具调用失败（含超时）由 Agent 循环重发 + 工具幂等设计兜底；LLM 请求级重试归 gnex3-cap-model-retry（model 域），与本插件无耦合。

**非目标**：不做 LLM 请求超时（gnex3-cap-model-retry 域）；不做沙箱心跳超时（sandbox-runtime 独立）；不做工具级重试（v3.0 明确不做，见 §8）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-call-timeout-policy` |
| 对标 GNEX2.0 模块 | 部分已有（agent-service 工具执行超时逻辑，OpenClaw P1 演进项，改造为策略插件） |
| 技术选型 | 🅶 GNEX2.0 模块改造 + dsh 策略语义移植 |
| 改造要点 | ① 策略模型：默认超时（30s）+ 按工具 override + 按 sandboxPolicy 档位收紧 ② wrapper 挂 `tools/execute` **最外层**（超时取消可穿透 spill 到达本体）：启动计时、到点取消（中断执行/终止进程树/释放沙箱）③ 挂起类工具走豁免表（D-P1-09）④ 结果统一标记 `TIMED_OUT` 并携带阶段 ⑤ 超时记录进 `tool/result` 观测，不自动重试（v3.0 无工具级重试） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `toolTimeoutPolicy` | `ToolTimeoutPolicy` | resolveTimeout(toolName, scope, tier)：策略查询（含豁免表判定） |

### 3.2 注入服务（inject）

- `tools`（必选）— 挂 execute wrapper（最外层）
- `events`（必选）— tools/execute 收发
- `sandboxPolicy`（可选）— 按档位收紧超时
- `observability` / `scope`（可选）— 观测与会话隔离

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/execute` | listen（waterfall wrapper，最外层） | 包住下游（spill → 本体）：计时/到点取消/标记；取消可穿透下游 wrapper |
| `tool/result` | listen | 观测超时语义 {timeoutMs, phase, cancelled} |

## §4 配置与装配

```yaml
gnex.tools.timeout:
  default-ms: 30000
  overrides: {tool_bash: 60000, tool_web: 20000}   # 按工具覆盖
  tier-multiplier: {LOW: 1, MEDIUM: 1, HIGH: 0.5}  # 高风险档位收紧
  suspend-exempt: [tool_ask_user, tool_question]   # 挂起类工具豁免表（D-P1-09）：不适用默认超时，
                                                    # 由工具自身注册 override（如 ask_user 120s 挂起上限）
  on-timeout: CANCEL           # 仅 CANCEL（中止执行）；MARK_ONLY 已删除（P2-09）
```

说明（D-P1-09）：挂起类（等待人工/外部输入的）工具列入 `suspend-exempt` 豁免表，**不受 default-ms 约束**，改由各挂起类工具 spec 自身声明挂起上限并注册 override（如 tool_ask_user 注册 120s）；豁免 ≠ 无上限——豁免工具若未注册任何 override，装配期校验失败（fail-fast），防止无限挂起。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 每个工具调用必须有确定超时上限（默认兜底，未配置不豁免；豁免表工具必须自带 override）
- ✅ **挂起类工具不得被默认超时中止**（D-P1-09）：suspend-exempt 表内工具不适用 30s 默认值，按其注册的挂起上限计时
- ✅ wrapper 位于 execute 链最外层（timeout → spill → 本体）：超时取消必须能穿透下游 wrapper 到达本体（D-P1-06）
- ✅ 超时后必须中止执行并返回 `TIMED_OUT` 标记结果，不得悬挂
- ✅ 超时结果必须进入 `tool/result` 观测与审计（耗时/阶段可拆）
- ✅ 本插件只管超时：不做重试、不做溢出、不做工具本体（职责单一）
- ❌ 超时不得自动重试、不得隐含任何重试语义（v3.0 无工具级重试；重试归 model-retry 的 LLM 请求级，本插件与其无推诿/耦合关系）
- ❌ 不得因超时静默返回"成功"（标记必须如实呈现给模型与观测）
- ❌ 不得绕过管线单独起执行（wrapper 缺失由 registry fail-closed 拒绝）
- ❌ 不得引入"仅观测标记不中止"模式（on-timeout 仅 CANCEL；观测标记场景不构成中止豁免，P2-09）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 默认超时生效 | `ToolTimeoutDefaultTest`：sleep 超时工具返回 TIMED_OUT，耗时≈上限 |
| V2 | 按工具覆盖 | `ToolTimeoutOverrideTest`：tool_bash 用 60s 覆盖生效 |
| V3 | 超时中止 | `ToolTimeoutCancelTest`：超时后底层执行线程/进程被终止 |
| V4 | 结果如实标记 | `ToolTimeoutMarkTest`：TIMED_OUT 结果不携带成功语义 |
| V5 | 不自动重试 | `ToolTimeoutNoRetryTest`：超时后调用次数 = 1 |
| V6 | 挂起类豁免 | `ToolTimeoutSuspendExemptTest`：tool_ask_user 挂起 60s（> default 30s）不被默认超时中止；按其 override（120s）计时（D-P1-09） |
| V7 | 豁免必带上限 | `ToolTimeoutExemptFailFastTest`：suspend-exempt 工具未注册 override → 装配期校验失败（不无限挂起） |
| V8 | wrapper 最外层 | `ToolTimeoutOutermostTest`：超时取消穿透 spill wrapper 到达本体并终止（D-P1-06） |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=ToolTimeout*Test"
```

（通配覆盖 V1-V8 全部测试类；测试类内方法名与 Vn 一一对应，对齐 D-P0-03。）

## §7 影响域

- 作用范围：**每工具调用**（L3.5 管线最外层 wrapper；挂起类工具经豁免表改用自带上限）。
- 被谁消费：`tools` 管线；模型（收到 TIMED_OUT 语义化结果）；观测/审计。
- 故障影响半径：单次调用被终止；策略缺失 = 管线 fail-closed 拒绝（不悬挂）。
- 升级影响：超时策略调整 = 全部工具行为（normal，管理员可装卸）。
- 关联决策：D-P1-06（wrapper 最外/无工具级重试）、D-P1-09（挂起类豁免）、P2-09（删 MARK_ONLY）。

## §8 后续（未纳入首版）

- 超时自适应（按历史 P95 动态建议，仅提示不自动改）
- 工具级重试机制评估（v3.1：显式 retry-on-timeout 白名单 + 幂等约束——v3.0 明确不做，D-P1-06）
- 跨服务长任务（tool-jobs）的异步超时语义

---

*GNEX3.0 工具调用超时策略 — gnex3-cap-tools-timeout SDD。审计修订 v0.2。*
