# v3-spec-ent-observability

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-observability
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-observability
> **分层**：企业层（L4）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：observability-service
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-41（§5 增观测诚实红线：空样本指标/风险档 SSOT；§6 增 ObsEmptySampleTest 与 turn 级四段延迟拆分断言，并修全运行命令测试类清单）

---

## §1 业务目标

把 GNEX2.0 observability-service 的只读查询 BFF 改造为企业可观测插件，覆盖运营中心 8 页（运行指标/运行追踪/决策归因/采纳追踪/记忆库/消息总线/审计/沙箱），并在 3.0 补齐三维：指标（Micrometer/Prometheus，对齐 E35 基建）、链路（trace_id 随 MQ Envelope 与服务间透传贯通）、告警（agent-slow-turn 等规则，新增 observability/alert 事件）。目标：运维按设计尺子「观测能拆开故障」区分选错工具/上下文问题/循环失控/沙箱·总线·模型故障；查询全部只读，DEVELOPER 数据范围 fail-closed。

**非目标**：不做写路径（无观测写入 API）；不做告警执行器（只产生告警事件，通知归 ops）；不替代 Micrometer 底层采集（复用既有指标导出）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-session-telemetry-otel`（弱类比：OTel 遥测面；dsh 无指标/告警/审计查询原语） |
| 对标 GNEX2.0 模块 | observability-service 8 QueryInterface（Operations/RunTrace/Adoption/Memory/BusObservability/AuditLog/Sandbox/MessageBus@Deprecated）+ 10 只读 Controller + `BusAdminInterface` 委托 |
| 技术选型 | 🅶 GNEX2.0 模块改造为插件（8 Adapter → observability 服务） |
| 改造要点 | ① 插件化：`observability` 服务聚合 8 QueryInterface，`*QueryInterfaceAdapter` 收编为插件实现；② 链路：trace_id 贯通 MQ Envelope → 查询过滤，服务间 HTTP 透传 trace 头；③ 指标：Micrometer 导出 + Prometheus 抓取（复用 E35）；④ 告警：规则引擎产 observability/alert（新增事件，待入 05 词汇）；⑤ 数据范围：DEVELOPER 强制项目级过滤，fail-closed |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `observability` | `com.gnex.ent.obs.ObservabilityService` | 指标查询（operations/trends/by-model）、运行追踪（run-events/tree/decisions）、审计查询、沙箱/总线/记忆/采纳只读面 |

### 3.2 注入服务（inject）

- `audit`（可选）：审计流水查询（AuditLogQueryInterface 委托）
- `bus`（可选）：RocketMQ 管理只读（BusAdminInterface；缺失时 503）
- `statePlane`（可选）：物化读模型查询（token_usage/output_adoption 等）
- `scope` / `settings` / `events`（必选）：租户/项目数据范围、配置、事件订阅

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `observability/alert` | emit | {ruleId, level, tenantId, projectId, metric, value, threshold}（新增，待入 05 词汇） |
| `session/*`、`agent/*`、`tool/*`、`turn/*`、`step/*`、`gate/*`、`memory/*` | listen | 事件流 → 指标/链路聚合 |
| `audit/record` | listen | 审计流水观测 |
| `bus/published`、`bus/consumed` | listen | 总线观测（backpressure/DLQ/位点） |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-ent-observability
    load: mandatory
    config:
      gnex.obs.trace-enabled: true           # trace_id 贯通与透传
      gnex.obs.alert-rules: [agent-slow-turn]  # 告警规则（对齐 E35）
      gnex.obs.developer-scope: project      # DEVELOPER 强制项目级范围（fail-closed）
      gnex.obs.metrics-export: prometheus    # /actuator/prometheus 抓取
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 全部查询端点必须只读（GET）且带角色控制（SYSTEM_ADMIN/ADMIN/DEVELOPER/VIEWER）
- ✅ trace_id 必须随 MQ Envelope 传递并贯通查询过滤；指标导出对齐 Micrometer/Prometheus
- ✅ 数据范围必须 fail-closed：DEVELOPER 强制项目级过滤，越权查询返回空而非全量
- ✅ BusAdmin 不可用必须显式 503（BusObservabilityUnavailableException），不得静默空数据
- ✅ **观测诚实（D-P1-41）**：空样本聚合指标不得输出 1.0——分母为 0 时输出 UNKNOWN 或隐藏该指标点（hit rate/成功率/采纳率等比率类指标一律适用），不得以 0/0 默认值冒充满分
- ✅ **风险档 SSOT 单源（D-P1-41）**：风险档位定义（档位表/阈值/文案）以单一权威源（配置 ↔ 代码对照表）维护，展示与分类必须同源；文档与代码漂移即缺陷
- ✅ **turn 级延迟拆分（D-P1-41）**：turn 延迟必须按 queue（入队等待）/ alloc（资源分配）/ exec（执行）/ llm（模型调用）四段拆分上报，支撑「观测能拆开故障」定位
- ❌ 观测不得暴露会话正文/提示词/密钥（只暴露元数据与统计）
- ❌ 观测插件不得写业务库（只读物化读模型），不得在 Agent 热路径埋点阻塞循环
- ❌ 观测不得跨租户串读（租户过滤为硬边界，非应用层软过滤）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 运行追踪查询 | `RunTraceQueryTest`：session/run/decision 树与 provider-trace 按 trace_id 过滤 |
| V2 | 指标聚合 | `OperationsMetricsTest`：token 成本/路由分级/趋势/按模型拆解 |
| V3 | 审计查询与导出 | `AuditObsTest`：filter + CSV 导出限流 |
| V4 | 总线观测降级 | `BusObsUnavailableTest`：BusAdmin 缺失 → 503 而非空数据 |
| V5 | 数据范围隔离 | `ObsScopeTest`：DEVELOPER 跨项目查询返回空（fail-closed） |
| V6 | 告警事件 | `AlertEmitTest`：agent-slow-turn 触发 emit observability/alert |
| V7 | 空样本诚实（D-P1-41） | `ObsEmptySampleTest`：无样本时段的比率类聚合指标输出 UNKNOWN/隐藏，**不得输出 1.0**（0/0 不冒充满分） |
| V8 | turn 级延迟拆分（D-P1-41） | `TurnLatencyBreakdownTest`：每 turn 的延迟拆分为 queue/alloc/exec/llm 四段，各段 ≥0 且四段之和 ≈ turn 总延迟（拆分完备性断言） |

### 运行命令（审计修订 D-P0-03/D-P1-41：显式全列，`Obs*Test` 通配漏类已修）

```bash
mvn -pl services/gnex-observability-service test "-Dtest=RunTraceQueryTest,OperationsMetricsTest,AuditObsTest,BusObsUnavailableTest,ObsScopeTest,AlertEmitTest,ObsEmptySampleTest,TurnLatencyBreakdownTest"
```

## §7 影响域

- 作用范围：**全平台**（服务级只读查询面，浏览器消费）。
- 被谁消费：运营中心/可观测台页面（d1-web ObservabilityPage）、运维告警通道。
- 故障影响半径：查询面不可用 = 观测台不可用，Agent 运行不受影响；数据范围越权 = 合规 P0。
- 升级影响：查询接口扩展向后兼容；新 QueryInterface 增补无感（Adapter 模式）。

## §8 后续（未纳入首版）

- 链路采样率/全链路火焰图（Jaeger compose 已就位，v3.1 接入）
- 告警执行器与通知渠道（ops 消费 observability/alert）
- 指标趋势预测/异常检测（AI 辅助归因）

---

*GNEX3.0 企业可观测插件 SDD。*
