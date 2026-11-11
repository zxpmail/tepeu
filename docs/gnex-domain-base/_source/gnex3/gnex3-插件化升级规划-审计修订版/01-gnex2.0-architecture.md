# GNEX 2.0 现状架构图与模块目录

> **状态**：定稿（v0.1）· 关联：00-PROJECT-TASKS.md · 数据来源：gnex-core-v2 源码 + docs（子代理深析，见 _work/）
> **目的**：为 dsh↔gnex 对比（02）与融合设计（03）提供 GNEX 侧事实基线。

---

## 1. 定位与技术栈

| 维度 | 事实 |
|------|------|
| 定位 | 企业级 AI Agent 编排与管理平台（Agent 全生命周期：注册/配置/执行/监控） |
| 技术栈 | Java 21 + Spring Boot 4.0.6 + Spring AI 2.0.0-M3 · PostgreSQL 16 / SQLite · MyBatis-Plus · RocketMQ 5.5 · Redis · MinIO · React 19 + Vite 8 |
| 部署 | 9 微服务（可独立 fat jar）+ k8s（mode-a-cluster / mode-b）+ Docker Compose（单机/双副本） |
| 运行模式 | `gnex.mode: standalone \| cluster`；Master-Worker 分布式执行 + 服务化控制面 |
| 分层纪律 | maven-enforcer：libs 不依赖 services；services → libs → contracts 单向依赖 |

---

## 2. 架构总览图（文字版）

```
┌────────────────────────────── GNEX 2.0 架构总览 ──────────────────────────────┐
│                                                                               │
│  ┌──────────────────────────── 前端面（d1-web）────────────────────────────┐   │
│  │ React 19 SPA · 22 页面路由（Chat/Agents/Tools/Projects/Plans/Workflows/   │   │
│  │ Operations/Observability/Governance(用户·角色·凭证·设置·沙箱·计费·审计·SSO) │   │
│  │ · ~40 个 REST API 客户端 · SSE 流式（chat）+ WebSocket（steering）          │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│   ▲ REST / SSE / WS                                                              │
│  ┌──────────────────────── 服务面（9 微服务 · 控制面+热路径）────────────────┐   │
│  │ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐      │   │
│  │ │ agent-service │ │ registry-    │ │ platform-    │ │ ops-service  │      │   │
│  │ │ 热路径控制面   │ │ service 注册  │ │ service 冷路径│ │ 运营管理      │      │   │
│  │ │ Chat/ReAct/   │ │ Agent/Skill/ │ │ 项目/审计/计费│ │ 租户/组织/配额│      │   │
│  │ │ Team/Room/    │ │ MCP/凭据/模型│ │ /技能质量/轨迹│ │ /授权/沙箱资源│      │   │
│  │ │ Swarm/Workflow│ │ 目录         │ │ /自定义工具   │ │ /计费熔断     │      │   │
│  │ └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘      │   │
│  │ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐      │   │
│  │ │ auth-service │ │ sandbox-     │ │ state-plane- │ │ observability│      │   │
│  │ │ 认证内核      │ │ runtime 执行  │ │ service 状态面│ │ -service 可观测│   │   │
│  │ │ JWT/SSO/RBAC │ │ 沙箱池/调度/  │ │ CQRS projector│ │ 8 类只读 Query│   │   │
│  │ │ /APIKey/密码  │ │ checkpoint/  │ │ /restore/审计 │ │ Interface BFF │   │   │
│  │ │ 策略         │ │ 集群/故障分级 │ │ 物化         │ │              │   │   │
│  │ └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘      │   │
│  │                          ┌──────────────┐                                  │   │
│  │                          │ bus-service  │ 消息总线（RocketMQ 治理/管理）    │   │
│  │                          └──────────────┘                                  │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│   ▲ 服务内 JVM Port 注入（gnex-contracts）+ ▲ 跨服务 Bus（RocketMQ topics）        │
│  ┌──────────────────────── 共享库面（9 libs · services→libs→contracts）────────┐ │
│  │ gnex-contracts(475 文件契约) · gnex-agent-sdk(ReAct/SessionLoop/记忆/检查点/  │  │
│  │ InspectorChain) · 5 持久化 libs · gnex-llm-adapt(ModelRouter/BYOK/熔断/限流)  │  │
│  │ · gnex-sandbox(api+client) · gnex-persistence-common(方言/RLS/租户/加密/HA)   │  │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│   ▲                                                                              │
│  ┌──────────────────────── 基础设施面（企业特性 E1-E36）──────────────────────┐   │
│  │ k8s 多副本+弹性(HPA) · PG(RLS) · Redis(热状态/锁/租约) · MinIO(租户桶) ·      │   │
│  │ RocketMQ(事务消息/顺序/幂等) · Prometheus+Jaeger · 企业记忆SoR(设计态)        │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 九个服务模块目录（摘要）

> 详细逐服务分析见 `_work/parts/gnex-*.md` 与 `_work/gnex-services-analysis.md`（含全部关键类与 REST 端点）。

| 服务 | 定位 | 核心模块 | 关键 Port/SPI（gnex-contracts） |
|------|------|----------|-------------------------------|
| **agent-service** | 热路径控制面（Chat/ReAct/Team/Room/Swarm） | agent.runtime（Orchestrator/AgentRouter/ReActLoop/SessionLoop）、agent.tools（WebSearch/Git/HttpClient/SandboxExec/Todo/Glob）、agent.search（SearchGateway 多提供商）、agent.sandbox（SandboxWorkspacePort Local/Remote）、agent.team/room/swarm、workflow.engine、evaluation、shell | ChatOrchestrationPort、AgentRuntimePort、SessionLoopPort、TeamTaskRoutingPort、SearchPort、SandboxPort、AgentEventPort、RunDiagnosticsPort、AgentBudgetManagementPort |
| **registry-service** | 注册控制面（Agent/Skill/MCP/凭据/模型目录） | registry.agent（安装/格式转换/卸载）、registry.skill（SkillRegistry + HookExecutor）、registry.mcp（McpConnectionRegistry + HttpTransport）、registry.credential（AES 加密存储）、registry.model（model-catalog.yml + pricing） | AgentRegistryPort、SkillRegistryPort、CredentialManagementPort、LlmConfigPort、McpServerManagementPort、McpToolRuntimePort、AgentAdapterPort |
| **platform-service** | 冷路径控制面（项目/技能质量/审计/计费/自定义工具/MCP 注册表） | platform.project、platform.audit（异步物化）、platform.billing（聚合查询）、skill.quality（QualityGate）、skill.tools（CustomToolRegistry/McpToolRegistry）、extension.spi（RiskClassifier/ApprovalHandler/NotificationHandler SPI）、project.trajectory | ProjectManagementPort、BillingQueryPort、AuditPublisher、ConfirmationPort、RiskClassifier/ApprovalHandler/NotificationHandler（extension SPI）、WorkflowManagementPort（消费） |
| **ops-service** | 运营管理（租户/组织/配额/授权/沙箱资源/计费编排） | ops.tenant（生命周期+配额）、ops.org（部门树）、ops.grant（资源授权）、ops.sandbox（模板/类型/配额）、ops.billing（预算/三级熔断）、ops.config（系统配置） | TenantManagementInterface、BillingManagementInterface、SandboxResourceInterface、ResourceGrantInterface、SystemConfigInterface、TenantAdminProvisioningPort |
| **auth-service** | 认证内核（登录/JWT/SSO/RBAC/APIKey/密码策略） | auth.core.login/token/password/apikey/session/sso、auth.rbac、auth.policy | AuthenticationPort、TokenPort、RbacManagementInterface、TenantAdminProvisioningPort、SsoConfigManagementInterface |
| **sandbox-runtime** | 执行平面（沙箱池/调度/检查点/集群/故障分级） | sandbox.scheduler（GateChain：TypeMatch→Quota→NetworkPolicy→CrossTenantGuard）、sandbox.pool（warm-pool）、sandbox.provider（SandboxProviderPort SPI：OpenSandbox/Firecracker）、sandbox.lifecycle（状态机+回收）、sandbox.checkpoint（L3）、sandbox.cluster（Leader 选举） | SandboxProviderPort、SandboxGate SPI、SandboxPort、EnsureReadyPort、CheckpointStorageAdapter |
| **state-plane-service** | 状态面（CQRS 冷路径物化 + 恢复） | stateplane.consumer（幂等消费）、stateplane.projector（6 类 projector）、stateplane.restore（RestorePackage 组装） | BusInterface（订阅）、StateRestoreInterface、MaterializationRouter |
| **observability-service** | 可观测只读查询层（BFF） | obs.controller（10 个只读控制器）、obs.query（8 个 QueryInterfaceAdapter）、obs.service（运营指标聚合） | RunTraceQueryInterface、OperationsQueryInterface、AdoptionQueryInterface、MemoryQueryInterface、BusObservabilityQueryInterface 等 8 个 |
| **bus-service** | 消息总线（RocketMQ 治理 + SDK 直连） | bus.transport（RocketMqTransport）、bus.admin（DLQ/订阅/offset 治理）、bus.reliability（事务消息/顺序消费） | BusInterface（sync/async/batch/定时/事务）、BusAdminInterface、StandardTopics |

---

## 4. 共享库目录（摘要）

| lib | 职责 | 关键接口 |
|-----|------|----------|
| gnex-contracts | 跨模块契约（475 文件）——所有跨服务能力的唯一定义地 | StateInterface、BusInterface、StandardTopics、AgentRuntimePort 族、ops 九接口、registry/governance/sandbox/search/memory/extension 域 Port |
| gnex-agent-sdk | Agent OS 核心：ReAct/SessionLoop、记忆、检查点/恢复、InspectorChain、租约 | AgentRuntime、ReActLoop、SessionLoop、GnexMemoryService、ReActCheckpointService、InspectorChain、RunLeaseService |
| gnex-llm-adapt | 进程内 LLM 装配/路由/弹性（不依赖 agent-sdk） | ModelRouter（FAST/STANDARD/DEEP）、LlmCircuitBreaker、KeyPoolSelector、BYOK、LlmRateLimiter |
| gnex-sandbox (api+client) | 沙箱团队边界：共享 API + 调用方 SDK | SandboxPort、EnsureReadyPort、SandboxPoolQueryPort、SandboxLifecycleEvent |
| gnex-persistence-common | 持久化基础设施：方言/迁移/租户隔离/加密/HA/TTL/ID | DatabaseDialect、TenantInterceptor、PgRlsInitializer、MinioIamConfig、StateInterface、IdGenerator |
| 5 个持久化 libs | 各域实体/Mapper（agent/auth/bus/platform/通用） | ConversationSession、AgentRegistry、User/Role、AuditLog、TokenUsage… |

---

## 5. 企业特性清单（E1–E36，详见 _work/gnex-distributed-analysis.md）

> 全部 36 项企业特性已编号可核对。按域归类：

| 域 | 特性 |
|----|------|
| 多副本/弹性/HA | E1 多副本无状态 · E2 跨副本运行态快照 · E3 取消广播 · E4 热状态外化 · E31 HPA 弹性 · E32 探针 · E36 金丝雀 |
| 消息总线 | E5 SDK 直连 RocketMQ · E6 事务消息 · E7 总线治理 · E8 顺序消费 · E9 at-least-once+幂等 |
| 状态/恢复 | E10 审计 MQ 化 · E11 CQRS projector · E12 三层恢复 · E13 RestorePackage |
| 数据/租户 | E15 四层租户隔离 · E16 引擎可移植 · E17 分布式锁 · E18 分布式 ID |
| 运营 | E19 租户管理/配额 · E20 RBAC · E21 资源授权 · E22 计费熔断 · E23 SSO · E24 密码策略 · E25 沙箱资源管理 |
| 执行 | E26 沙箱生命周期总线化 · E27 A2A 协作 · E28 LLM 路由/弹性/BYOK · E29 成本计量 · E30 质量校验 |
| 记忆/可观测 | E33 企业记忆三平面 SoR（设计态） · E34 删除传播 · E14 可观测 Query Interface · E35 遥测基建 |

---

## 6. 服务间协作机制（现状）

1. **进程内 Port 注入**：standalone / dev-assembler 单体模式下，9 服务同进程装配，经 gnex-contracts Port 接口 JVM 内注入（如 agent-service 注入 AgentRegistryPort、platform-service 注入 WorkflowManagementPort）。
2. **跨服务消息总线**：cluster 模式下 RocketMQ topics（agent_events / sandbox_events / cost_events / quality_events / audit_log / sandbox_request / a2a_delegate 等），orderKey 顺序 + 消费者组幂等。
3. **同步查询**：REST（内部端点 /internal/*、/api/state-plane/*）+ observability 只读 Query Interface。
4. **数据归属**：Database-per-service（独立 schema），跨服务经总线/查询接口，无 RestTemplate/Feign 服务间 HTTP（各服务注释确认）。

---

## 7. 痛点分析（为什么改起来麻烦 → 插件化动因）

| 痛点 | 根因 | 与 dsh 对比 |
|------|------|-------------|
| 加能力 = 改代码 + 改 pom + 重构建 + 重启 | 能力是**代码模块**不是**组合单元**；无装配清单 | dsh：加组合行（cordis.patch.yml id 覆盖）即可装卸 |
| Inspector 链顺序写死 | 安全管线**不可插拔扩展** | dsh：tools/pre-execute waterfall 可注册任意策略监听器 |
| 跨服务加逻辑要动多个服务 pom | 能力边界与服务边界耦合 | dsh：能力缝（Definition/Provider/Consumer）解耦 |
| 事件语义不统一（Spring Event / Bus / WebSocket / hooks 并存） | 无统一事件词汇与 waterfall 语义 | dsh：域/动作事件 + waterfall/emit 统一 |
| 循环/会话/工具逻辑集中在 agent-sdk 大包里 | 核心与扩展耦合（319 文件 SDK） | dsh：agent-loop 可替换、扩展挂事件外 |
| 修改影响面不可枚举 | 无插件清单/依赖图/影响域声明 | dsh：inject/provide 声明即依赖图 |

---

*下一步：02-dsh-gnex-comparison.md（对比）→ 03-fusion-design.md（融合）→ 04 插件目录。*
