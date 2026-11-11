# GNEX3.0 框架融合设计方案（框架融合 · 分层插件架构总图）

> **状态**：审计修订版 v0.2（2026-08-18）· 关联：审计/06 与 审计修订版/02-设计决策记录.md
> **核心命题**：服务是部署单元，插件是组合单元。GNEX 仍是服务化/企业级；服务内部由 cordis-jvm 插件内核组合能力。

---

## 1. 融合原则（取并集 · dsh 已验证按 dsh 规制）

| # | 原则 | 说明 |
|---|------|------|
| F1 | **部署形态取 gnex** | 9 服务、多副本、弹性伸缩、租户边界、k8s 全部保留（企业层） |
| F2 | **组合语义取 dsh** | ctx 服务容器 / inject-provide / waterfall-emit 事件 / fiber 生命周期 / realm 作用域 / 组合清单 —— dsh 已验证的规制直接移植为 cordis-jvm |
| F3 | **接口关系对齐 dsh** | 服务目录（provide/inject）、事件命名（域/动作）、工具管线、preset 组合 —— 对齐 dsh 的 53 个核心接口服务 |
| F4 | **企业红线受保护** | 完成证据门、审批地板、租户边界、记忆 SoR 为 platform 级 protected 插件（继承 ADR-046「闸门不可卸」） |
| F5 | **先接口后实现** | 全部插件先定 contracts（libs/gnex-contracts 纪律），再写实现 |
| F6 | **可观测优先** | 插件生命周期事件进可观测面；故障可拆到插件级 |
| F7 | **能力缝三角色**（dsh 已验证规制） | 可替换能力必须齐备三角：① Service Definition（拥有 ctx 键与词汇的服务接口包）② Service Provider（具体后端）③ Consumer（模型或其它插件编程的面，通常是工具 schema）。扩展插件只依赖 Definition，从不 import 具体 Provider——换后端不改工具名（如 fs-sandbox ↔ fs-local ↔ fs-e2b） |

---

## 2. GNEX3.0 分层插件架构（六层）

```
┌─────────────────────────────────────────────────────────────────────┐
│ L0 组合与装载层（服务装配 · 类比 dsh 启动与组合层 + 浏览器装载）        │
│   service-assembly.yml · cordis-jvm 内核 · 插件加载器 · 插件清单/管理  │
│   命令注册表 · 配置中心 · 插件生命周期门                              │
├─────────────────────────────────────────────────────────────────────┤
│ L1 内核层（服务内运行时 · 类比 dsh 核心服务层）                        │
│   Ctx 服务容器 · 事件总线(waterfall/emit) · Fiber 生命周期 ·          │
│   Scope/Realm(租户/项目/会话) · settings · logger · invariants       │
├─────────────────────────────────────────────────────────────────────┤
│ L2 服务层（部署单元 · gnex 9 服务保留，每服务 = 内核 + 插件集）        │
│   agent-service · registry-service · sandbox-runtime · state-plane   │
│   observability · ops · bus · auth · platform                        │
│   （跨服务通信：既有 REST / Bus / RPC 不动）                          │
├─────────────────────────────────────────────────────────────────────┤
│ L3 能力层（服务内插件 · 类比 dsh 模型/会话/工具/委派/技能/压缩）        │
│   模型路由 · 会话与持久化 · Agent 循环 · 工具注册表+管线 · 工具插件集   │
│   委派与子代理 · 目标/计划 · 技能 · 上下文与压缩 · 审批与权限 · 沙箱    │
├─────────────────────────────────────────────────────────────────────┤
│ L4 企业层（gnex 独有 · 类比 dsh 宿主服务层，但为企业强化）             │
│   租户边界(protected) · 多副本/弹性 · 记忆SoR多副本(protected) ·      │
│   计费/配额 · 状态面/审计/可观测 · 认证授权 · 总线可靠性 · 沙箱池      │
├─────────────────────────────────────────────────────────────────────┤
│ L5 客户端层（d1-web 前端 · 类比 dsh 浏览器平面）                      │
│   前端内核 · 功能模块（逐步插件化，先模块后插件）                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.1 分层 ↔ dsh 分类 ↔ gnex2.0 模块映射（总表框架）

| GNEX3.0 层 | 对标 dsh 分类 | 对标 gnex2.0 模块 | 策略 |
|-----------|--------------|------------------|------|
| L0 组合与装载 | 启动与组合层（10） | 无对应（新增）+ gnex-dev-assembler + 配置中心 | 新增 |
| L1 内核层 | 核心服务层（12） | Spring 基础 + contracts | Java 移植 |
| L2 服务层 | （宿主载体） | 9 个服务 + k8s | 保留 |
| L3 能力层 | 模型路由/会话/存储/沙箱/进程/工具/委派/目标/技能/压缩（≈95） | agent-service 内部模块 + registry + sandbox-runtime | 改造 |
| L4 企业层 | 宿主服务与RPC层（21，企业化） | auth/ops/observability/state-plane/bus + k8s | 保留+插件化 |
| L5 客户端层 | 客户端运行时层 + 浏览器UI插件层（42） | d1-web | 模块化 |

### 2.2 插件运行时（cordis-jvm）设计要点

见 `_work/plugin-framework-selection.md`：
- 最小内核：`Ctx / Service / Plugin / Fiber / Events(waterfall+emit) / Effect / Scope`
- Spring 双向桥：`@PluginService` 注入、`PluginBridge` 暴露
- 装配清单 `service-assembly.yml`：每服务声明插件集 + 受保护级别 + 覆盖
- v3.0 冷装（重启生效）；v3.1 热装（PF4J 类加载器后端）

---

## 3. 双平面 → 双面的映射（dsh 规制如何落进 gnex）

| dsh 概念 | gnex3.0 对应 | 说明 |
|----------|-------------|------|
| 宿主平面 Host | **L4 企业层 + L2 服务层** | 注册表/跨会话服务/受保护能力留在平台与服务层 |
| 代理平面 Agent | **L3 能力层（会话级装配）** | 每会话/每 Agent 的能力组合（preset 类比：会话装配片段） |
| 浏览器平面 Client | **L5 客户端层** | 前端模块化 |
| 双平面归属判据（注册表=宿主、模型侧呈现=preset） | 同判据 | 服务内：跨会话读者=服务层；会话自有=会话作用域 |
| 能力缝三角色（Definition/Provider/Consumer） | **contracts 三包纪律** | 每能力 = libs 定义包（ctx 键+词表）→ plugins 实现包（Provider）→ tool-* 消费包（模型面）；换后端不改工具 schema |
| 「模型可见 ⟺ 已落盘」 | session 事件化 | 进模内容必须能从 session 日志重建（对齐 P0 观测纪律 F/CW-2） |
| 新行为挂扩展点，不改 loop | agent/* 与 tools/* 事件 | 加能力 = 注册事件监听/工具，不动 agent-loop |
| 权限档位（read-only/workspace-write/full-access） | sandboxPolicy 插件 | 文件写效果策略；升级走审批（对齐 CW-1） |

---

## 4. 企业特性保留清单（继承 gnex，不进插件热路径）

| 特性 | 保留位置 | 插件化处理 |
|------|----------|-----------|
| 多副本/弹性伸缩/HA | k8s + 服务编排 | 不动；服务内插件无感 |
| 租户/项目/用户隔离 | 服务层 Interceptor | 内核 Scope/Realm 承载语义；Interceptor 作为受保护插件 |
| 企业记忆 SoR（热温冷多副本） | state-plane + persistence | 记忆插件（protected）；多副本机制平台层 |
| 完成证据门/审批地板 | Inspector 链 + gates | 受保护插件，禁卸 |
| 计费/配额/组织 | ops-service | 企业层插件化（admin 级） |
| 状态面 CQRS/审计/可观测 | state-plane/observability | 插件生命周期事件接入 |
| 认证授权（SSO/RBAC/APIKey） | auth-service | 企业层插件化（protected） |
| 消息总线可靠性 | bus-service (RocketMQ) | 保留；插件事件可发布到 Bus |

---

## 5. 技术路线（阶段划分，详见 06-upgrade-roadmap.md）

1. **P0 内核**：cordis-jvm 最小内核 + 装配清单 + 单测（2-4k 行）
2. **P1 试点**：2-3 个服务内改造（工具管线+Inspector 链融合、会话事件化、模型路由注册表化）
3. **P2 能力迁移**：按插件目录批量改造（每个插件一个 Maven 模块 + spec）
4. **P3 企业面插件化**：auth/ops/observability 插件化（protected）
5. **P4 前端模块化 + v3.1 热装（可选）**

---

*待补：04 插件目录（每个插件定义）· 05 接口关系 · 06 路线图。*
