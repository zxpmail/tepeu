# dsh JS 插件生态在 GNEX（Java）上的复用：中间件插件方案论证

> **状态**：方案论证（v0.1）· 关联：00-PROJECT-TASKS.md（D8）· 04 插件目录
> **问题**：未来 GNEX3.0 插件化完成后，能否用一个"中间件插件"让 dsh（JavaScript/TypeScript）生态的插件在 GNEX（Java）上运行？

---

## 1. 问题拆解

dsh 插件是 npm 包（TypeScript → JS bundle），运行在 Node.js + Cordis 之上，依赖四类能力：

| 能力类别 | dsh 侧 | gnex 侧对应 |
|----------|--------|-------------|
| 容器语义 | cordis ctx / Service / Plugin / Fiber / Events | cordis-jvm 内核（L0/L1） |
| 模型/工具 | llm seam、tools 注册表、工具插件 | L3 模型/工具插件 |
| 执行环境 | 文件系统、进程、shell（Node API） | L3.9 沙箱 + L3.6 工具 |
| 外部协议 | MCP client、HTTP | registry/mcp + 工具 |

**核心矛盾**：JS 插件调用的是 Node/cordis API，不是 Java API。桥接 = 让 JS 插件以为自己在 dsh 里运行，实际能力由 gnex 提供。

---

## 2. 四个候选方案

### 方案 A：GraalJS 嵌入式桥（推荐主方案）

在 gnex 内嵌 GraalVM JavaScript（polyglot，支持 Node.js 兼容），`gnex3-bridge-dsh-js` 插件加载 dsh npm 包，用一层 **JS shim** 实现 cordis 兼容 API 并把调用桥到 cordis-jvm。

```
┌─ gnex 服务进程 ─────────────────────────────────────────┐
│ cordis-jvm 内核（ctx/events/fiber）                     │
│   ▲  JS 桥插件（gnex3-bridge-dsh-js）                   │
│   │   JS Shim：cordis ctx/Service/Events/Effect 兼容层   │
│   ▼                                                    │
│ GraalJS Engine（Node 兼容 · 受限上下文）                 │
│   └─ dsh 插件 npm 包（@deepseek-ai/dsh-tool-* 等）       │
└─────────────────────────────────────────────────────────┘
```

**可行点**：
- GraalJS 21（JDK 21 内置 polyglot）可执行 CommonJS/ESM JS，含大部分 Node API（graal-js + node-compat）；
- dsh 插件接口面小（ctx 服务 + 事件 + 工具注册），shim 层可完全翻译；
- 工具类插件（tool-*）是最大生态，恰好是最容易桥的一类（输入 schema + 执行函数）。

**难点与对策**：
| 难点 | 对策 |
|------|------|
| dsh 插件依赖 cordis npm 包（@deepseek-ai/cordis） | JS shim 提供同名模块实现（映射到 cordis-jvm），不加载真实 cordis |
| npm 依赖树解析（插件可能带第三方依赖） | 桥插件内置 npm 解析（package.json deps），GraalJS 内 mock Node require；版本锁定 + 供应链检查（对齐 Goose P1 恶意软件检查） |
| 文件/进程/网络能力边界 | shim 把 fs/child_process 映射到 gnex 沙箱 seam（🔒 sandboxPolicy 强制），不直通宿主 |
| 性能（JS↔Java 边界） | 批量化工具调用、事件按需同步；工具类插件开销可接受 |
| 企业红线（租户/审批/完成门） | 在桥边界强制：JS 插件不感知租户，但每次工具调用仍走 gnex tools 管线（pre-execute 链），审批/沙箱/审计不因 JS 绕过 |

### 方案 B：dsh Sidecar 进程桥（备选）

gnex 为桥插件启动一个 dsh sidecar（Node 进程），双向 RPC（typert 协议移植为 Java 客户端 + Node 服务端）。

**优点**：无语言嵌入风险；可用完整 dsh 运行时（含 HMR/原生模块）；隔离好。
**缺点**：双进程生命周期管理复杂；多副本下 sidecar 数量随服务伸缩（运维成本）；跨进程事件同步延迟；每个服务副本要拉起 Node 进程（企业部署负担）。

### 方案 C：MCP 桥（最低公共分母，辅助）

把 dsh 工具类插件包装为 MCP server（或 dsh 生态若提供 MCP server），gnex 用已有 MCP client 消费。

**优点**：零嵌入；gnex 已有 registry/mcp；标准协议。
**缺点**：只覆盖**工具**（tools），不覆盖 dsh 的会话/循环/UI/事件语义；能力范围窄；丢失 dsh 插件的丰富接口关系。

### 方案 D：转译/重写为 Java（否决）

把 JS 插件转译或重写为 Java 插件。

**否决**：dsh 生态量大且持续演进，转译不可维护；生态价值在于"直接用"，重写 = 双倍维护。

---

## 3. 推荐与架构定位

**推荐：方案 A（GraalJS 桥）为主 + 方案 C（MCP）为辅。**

- 定位：`gnex3-bridge-dsh-js` 插件（L3 能力层 · 可选 · admin 级），宿主 = sandbox-runtime 或独立 bridge 服务（多副本下独立服务便于伸缩）。
- 能力范围：优先 **工具类插件**（tool-*）与**纯逻辑插件**；循环/UI 类插件**不桥**（企业禁 Agent 自改内核；UI 用 gnex 前端）。
- 安全：JS 插件在 GraalJS 受限上下文执行；文件/进程/网络全经 gnex 沙箱 seam；审批/审计/租户在桥边界强制（**JS 无特权**）。
- 生命周期：桥插件管理 dsh 插件的安装/版本/启停（走 gnex3-l0-inventory 插件清单）；npm 依赖供应链检查为安装前置。
- 事件：dsh 插件事件经 shim → cordis-jvm 事件总线（对齐 `域/动作` 命名）；跨服务经 bus 插件。

**建议排期**：非主线。在 Gnex3.0 核心插件化（P0-P2）完成、桥插件（P3）落地后，作为 v3.2 增值项；先做 2-3 个工具插件 PoC（如 dsh-tool-fs-search、dsh-tool-web）验证 shim 可行性。

---

## 4. 原型要点（PoC 验收）

1. GraalJS 加载一个 dsh 工具插件（如 @deepseek-ai/dsh-tool-web），shim 提供 `ctx.tools.register`；
2. gnex tools 管线调用该工具 → schema/结果经 shim 双向翻译；
3. 沙箱策略生效：JS 插件调用 fs 被 sandboxPolicy 拦截/重定向；
4. 验收断言：工具可注册、可调用、可审计、可装卸（V1-V4）。

---

*结论：可行，但仅限工具/逻辑类插件；以 GraalJS 桥为主、MCP 为辅；企业红线在桥边界强制。*
