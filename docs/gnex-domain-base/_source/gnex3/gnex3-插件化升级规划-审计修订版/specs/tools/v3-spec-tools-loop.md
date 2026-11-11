# v3-spec-tools-loop

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.5 工具注册表与管线
> **审计修订：v0.2（2026-08-18）· 依据 D-P1-05**
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tools-loop
> **分层**：L3 能力层 · 3.5 工具注册表与管线
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

工具循环检测与重复提醒：检测 Agent 在同一会话内对同一工具（同参数指纹）的重复调用，命中阈值时向模型发出重复提醒（soft nudge），避免死循环空转与 token 浪费。挂在 `tools/pre-execute` 瀑布首位（Loop 检测 → Permission → …），对全部工具生效；检测状态为会话级（scope realm），跨回合持续、可观测。

**定位声明（D-P1-05）**：本插件是**成本保护（token/调用成本），非安全边界**——因此它在管线 guard 序中为 **optional guard**：检测器缺失/插件卸载时管线**跳过该 guard 并发观测告警**，不阻断调用（与 tools-registry §5 两级语义一致；安全边界的 fail-closed 由 approval/permission/sandbox-policy/tenant/gate 相关 mandatory guard 承担）。

**非目标**：不做模型侧 loop 抑制（prompt/compaction 负责上下文）；不做跨会话统计（企业级聚合归 observability-service）；不承担安全拦截职责（安全 guard 归管线 mandatory 序）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-repeat-tool-reminder` |
| 对标 GNEX2.0 模块 | gnex-agent-sdk `LoopInspector`（设计态标注：类名以 gnex-agent-sdk 源码为最终核实基准，改造为管线插件） |
| 技术选型 | 🅶 GNEX2.0 模块改造（LoopInspector → pre-execute listener，optional guard） |
| 改造要点 | ① 会话级调用指纹表（toolName + 参数规范化签名 + 时间窗/次数）② 命中阈值发 reminder（注入模型可见提示，默认不阻断）③ STRICT 档位：连续命中 deny（显式配置的成本熔断，非安全边界）④ 指纹规范化：剥离时间戳/随机参数，避免误报 ⑤ 注册为 optional guard：缺席=跳过+观测告警（D-P1-05） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `toolLoopDetector` | `ToolLoopDetector` | 指纹记录/命中判定/阈值查询（会话级） |

### 3.2 注入服务（inject）

- `tools`（必选）— 挂 pre-execute 首位（注册为 optional guard）
- `events`（必选）— tools/pre-execute 收发
- `scope`（必选）— 会话 realm 状态
- `observability`（可选）— 命中指标与缺席告警

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/pre-execute` | listen（waterfall 首位，optional guard） | 记录指纹；命中返回 NUDGE/deny 载荷 |
| `tool/result` | listen | 执行结果回写指纹表（成功/失败计数） |
| `observability/alert`（缺席告警，经 observability 通道） | emit | 本插件缺席/被卸载时由管线发告警（tools-registry 侧行为，此处声明告警语义消费） |

## §4 配置与装配

```yaml
gnex.tools.loop:
  window: {count: 5, withinMs: 120000}      # 5 次/120s 同指纹
  mode: NUDGE            # NUDGE（提醒不阻断）| STRICT（连续命中 deny，显式成本熔断）
  ignore-param-patterns: ["timestamp.*", "random.*", "id_.*"]
```

（guard 注册与启停见 tools-registry §4：`guards.loop = {enabled, mandatory: false}`。）

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 循环检测必须挂在 `tools/pre-execute` 瀑布首位（先于 Permission/Approval）
- ✅ 检测状态为会话级（scope realm），跨回合持续
- ✅ 命中默认 NUDGE（提醒不阻断）；STRICT 档位显式配置才 deny（STRICT 是成本熔断，非安全边界）
- ✅ 本插件定位为成本保护：不承担安全拦截职责（安全 fail-closed 归 mandatory guard）
- ❌ 不得因误报阻断合法循环（指纹规范化必须剥离时间戳/随机参数）
- ❌ 提醒不得被静默吞掉（模型必须可见 reminder 载荷）
- ❌ 不得跨租户/项目共享指纹表（串 scope = P0）
- ❌ 缺席语义不得自定：本插件缺席时管线行为由 tools-registry 统一裁定为"跳过 + 观测告警"，本插件不得引入额外缺席 deny/放行分支

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 指纹记录 | `LoopFingerprintTest`：同签名调用计数递增 |
| V2 | 命中提醒 | `LoopReminderTest`：5 次/120s 同指纹 → pre-execute 返回 NUDGE 载荷 |
| V3 | STRICT 阻断 | `LoopStrictTest`：STRICT 连续命中 → deny |
| V4 | 误报规避 | `LoopNoFalsePositiveTest`：仅时间戳/随机值变化的调用不判重复 |
| V5 | 会话隔离 | `LoopScopeIsolationTest`：A 会话指纹不影响 B 会话 |
| V6 | 缺席=跳过+告警 | `LoopAbsentSkipTest`：卸载本插件后调用照常执行（不 deny），且 observability 收到 loop guard 缺席告警（与 registry §6 V8 断言互补，D-P1-05） |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=Loop*Test"
```

（通配覆盖 V1-V6 全部测试类；测试类内方法名与 Vn 一一对应，对齐 D-P0-03。）

## §7 影响域

- 作用范围：**每工具调用**（L3.5 管线首位，optional guard）。
- 被谁消费：`tools` 管线；模型（reminder）；观测。
- 故障影响半径：单次调用被提醒/拦截（STRICT 才 deny）；**检测器缺失/插件卸载 = 管线跳过 loop guard + 观测告警，调用照常执行，不得 deny**（与 tools-registry §5 两级语义一致，D-P1-05）——代价是失去成本保护并产生告警噪音，**不构成安全弱化**（本插件非安全边界）。
- 升级影响：阈值调整 = 全工具行为（normal）。
- 关联决策：D-P1-05（guard 两级语义，本插件为 optional）。

## §8 后续（未纳入首版）

- 循环根因提示（重复失败的命令给出替代建议，需 LLM 参与）
- 跨回合跨会话聚合（收敛到 observability-service）
- 缺席告警升级策略（如持续缺席 N 个回合后触发装配建议）

---

*GNEX3.0 工具循环检测/重复提醒 — gnex3-cap-tools-loop SDD。审计修订 v0.2。*
