# Tepeu — Agentic Operating System

产品规格说明书 (Specification)

版本: v1.0.0
状态: 正式发布
日期: 2026-07-05
作者: Tepeu Team
协议: Apache 2.0

---

## 一、产品概述

### 1.1 项目定位

Tepeu 是一个开源的通用智能体操作系统（Agentic OS），专为个人知识工作者和企业团队设计。它不是一个聊天应用，不是一个 LLM 封装器，而是一个完整的、可交付的操作系统级平台。

**核心理念：模型是 CPU，Tepeu 是操作系统。**

### 1.2 命名由来

Tepeu 源自玛雅基切族神话中的创世之神。他与古库马茨共同"思考"并创造了世界——以思想创造万物。这与 Agent OS 通过智能体将人类意图转化为现实产品的愿景完美契合。

发音: /tɛˈpeɪ.uː/ (特-佩-乌)

### 1.3 愿景声明

让每个人和企业都能拥有一个专属的智能体操作系统，将想法转化为行动，将对话转化为交付。

---

## 二、目标用户

| 用户类型 | 典型场景 | 核心诉求 |
|---------|---------|---------|
| 个人开发者 | 编程、写作、研究、个人项目管理 | 开箱即用、数据归个人、低门槛 |
| 企业团队 | 产品研发、市场分析、客户运营、跨部门协作 | 项目隔离、成本可控、可审计追溯 |
| 开源贡献者 | 二次开发、定制 Agent、接入现有系统 | 开放 API、可扩展、文档完善 |
| 独立软件供应商 (ISV) | 基于 Tepeu 构建垂直行业应用 | SDK 支持、插件生态、商业化授权 |

---

## 三、核心设计原则

### 3.1 原则一：双用户、同底座

人与 Agent 共享同一套底层能力。UI 界面和 Agent 工具接口调用的是同一组 API。

- 用户在 UI 拖拽文件 → Agent 的 filesystem 工具能看到同样的目录结构
- Agent 通过工具创建任务 → 日历应用实时更新
- 数据一致性是强制约束，而非可选特性

### 3.2 原则二：个人与企业数据隔离

采用"四智能体"架构，实现个人资产与企业资产的清晰边界：

| 智能体类型 | 归属 | 数据范围 | 生命周期 |
|-----------|------|---------|---------|
| 个人智能体 | 个人 | 私人知识、偏好、账号凭证 | 永久归属于个人 |
| 企业智能体 | 企业 | 企业知识库、流程规范、客户数据 | 随企业存在 |
| 岗位智能体 | 企业 | 岗位职责、SOP、权限模板 | 随岗位变化 |
| 任务智能体 | 临时 | 任务执行过程中的上下文 | 随任务销毁 |

**核心规则**：企业只能在任务执行过程中，通过任务智能体临时调用个人的特定能力（如代码审查），但无权访问或留存个人知识。

> **Phase 排期**：四智能体架构属 Phase 2 企业能力。Phase 1（v0.1.0）仅实现个人智能体；企业/岗位/任务智能体推迟到 Phase 2。

### 3.3 原则三：白盒记忆

记忆的生成、存储、检索和修改全程可视化、可追溯：

- 每条记忆都有明确的来源标识（来自哪次对话/任务）
- 用户可以直接编辑或删除任意记忆条目
- Agent 不能"偷偷"写入记忆，所有写入需经过用户确认或系统审计

### 3.4 原则四：项目级隔离（Workspace）

每个项目（Project）拥有独立的：

- 文件系统（虚拟目录树）
- 记忆库（不与其他项目交叉检索）
- 技能集（按项目积累的专属能力）
- 任务历史（完整的执行日志）

**目标**：并行处理多个项目时，Agent 的"上下文"完全隔离，无污染。

### 3.5 原则五：成本可追踪

- 每项任务的 Token 消耗精确计量
- 按项目/按用户/按时间段汇总统计
- 智能路由：简单任务用轻量模型，复杂任务用旗舰模型
- 提供预算告警机制
- **Phase 1 最小视图**：每个 workspace 提供累计 Token/成本显示（完整成本仪表盘见 M2.4，Phase 2）
  - **实现状态（2026-07-18）**：会话级用量已展示；**workspace 累计尚未实现**（Spec Phase 1 缺口，补做或并入 M2.4 前须闭合）

---

## 四、功能架构

### 4.1 七层 Harness 架构

Tepeu 基于成熟的七层 Agent Harness 架构设计：

```
┌─────────────────────────────────────────────────────────────────┐
│ L7: 持续改进循环 (Evolution Engine)                           │
│   - 错误反馈 → 规则更新 → Agent 进化                          │
├─────────────────────────────────────────────────────────────────┤
│ L6: Hook 安全网 (Security Guardrails)                         │
│   - 幻觉检测 · 危险操作拦截 · 权限校验 · 成本门禁             │
├─────────────────────────────────────────────────────────────────┤
│ L5: 上下文压缩 (Memory Guard)                                 │
│   - 长对话摘要 · 记忆交接 · 关键信息保留                      │
├─────────────────────────────────────────────────────────────────┤
│ L4: 子Agent与团队协作 (Multi-Agent)                           │
│   - Planner · Implementer · Reviewer · Tester                 │
├─────────────────────────────────────────────────────────────────┤
│ L3: 规则、技能与集成 (Rules & Skills)                         │
│   - 项目规则 · 内置技能库 · MCP 工具集成                      │
├─────────────────────────────────────────────────────────────────┤
│ L2: 记忆与规划 (Memory & Planning)                            │
│   - 项目记忆 · 任务历史 · 决策日志                            │
├─────────────────────────────────────────────────────────────────┤
│ L1: 工具与执行循环 (Tools & Execution Loop)                   │
│   - Plan → Task → 实现 · TDD 质量保证                         │
└─────────────────────────────────────────────────────────────────┘
```

> **架构关系说明**：§4.1 七层是 Agent 能力的**概念架构**（逻辑分层）；§5.2 是**部署架构**（物理分层）。二者正交——七层中的 L1–L7 能力分布在 §5.2 的各物理层中。

### 4.2 功能模块清单

| 模块 | 功能 | 优先级 |
|------|------|--------|
| Web 工作台 | 多面板布局、主题切换、文件浏览 | P0 |
| Agent 对话 | 流式聊天、工具调用可视化、推理过程展示 | P0 |
| Workspace 管理 | 项目创建/切换/删除、文件隔离、记忆隔离 | P0 |
| 记忆系统 | 记忆 CRUD、来源追溯、搜索检索 | P0 |
| 文件管理器 | 目录树、文件预览、拖拽上传、版本历史 | P0 |
| 终端 | Unix-like 命令、AI 辅助命令行 | P0 |
| 多 Agent 协作 | Planner/Implementer/Reviewer 角色分工 | P1 |
| MCP 协议支持 | 标准化 Agent-系统通信 | P1 |
| 成本仪表盘 | Token 使用统计、预算告警、模型路由 | P1 |
| 应用市场 | 社区技能/工具分享与安装 | P2 |
| 移动端适配 | 响应式布局、触屏优化 | P2 |

---

## 五、技术架构

### 5.1 整体技术栈

| 层级 | 技术选型 | 版本 | 理由 |
|------|---------|------|------|
| 前端框架 | React | 18.x | 组件化生态成熟 |
| 前端语言 | TypeScript | 5.x | 类型安全，降低维护成本 |
| UI 样式 | Tailwind CSS | 3.x | 快速定制，方便"换肤" |
| UI 框架 | Web UI（SPA，参考 pi-web 设计） | — | 浏览器访问，无需安装客户端 |
| 运行时 | Java | 21 | 虚线程（Virtual Threads）支持 |
| 后端框架 | Spring Boot | 4.0+ | 支持 Spring AI 2.0，企业级 Java 生态 |
| AI 集成 | Spring AI | 2.0.0 (GA) | 官方 MCP 协议支持，需 Spring Boot 4.0 |
| Agent 工具 | spring-ai-agent-utils | 0.10.0 | 社区项目（org.springaicommunity），文件/Shell/Web 工具 |
| Agent 运行时 | WebAssembly + V8（规划中，见 Phase 3） | — | ~6ms 冷启动，进程隔离 |
| 数据库 | SQLite + 文件系统 | — | 开箱即用，无需额外部署 |
| 协议 | MCP + SSE + REST | — | 标准化 + 实时通信 |
| 部署 | Docker + 单 JAR | — | 跨平台，一键启动 |

> **集群组件**（Redis、对象存储 S3/MinIO、独立 LLM Gateway）属远期规划（v1.0.0 之后，见 §8.2），未列入 Phase 1 技术栈。

### 5.2 系统架构图

#### 5.2.1 Phase 1 架构（当前）

```
┌─────────────────────────────────────────────────────────┐
│                  浏览器 (Browser SPA)                    │
│  ┌──────────┐  ┌───────────┐  ┌─────────────────────┐  │
│  │ Workbench│  │ Agent Chat│  │  Workspace Manager  │  │
│  │(面板布局) │  │(SSE 流式) │  │  (项目切换/隔离)    │  │
│  └────┬─────┘  └─────┬─────┘  └──────────┬──────────┘  │
│       │              │                   │              │
│       └──────────────┼───────────────────┘              │
│                      │ REST API / SSE                    │
└──────────────────────┼──────────────────────────────────┘
                       │
┌──────────────────────┼──────────────────────────────────┐
│           Spring Boot 进程 (单一部署)                    │
│  ┌───────────────────┼───────────────────────────────┐  │
│  │                   ▼                               │  │
│  │  ┌─────────────────────────────────────────────┐  │  │
│  │  │         API 路由层                           │  │  │
│  │  │  /api/chat · /api/workspace · /api/memory   │  │  │
│  │  │  /api/files · /api/terminal (WS)            │  │  │
│  │  └─────────────────────┬───────────────────────┘  │  │
│  │                        │                          │  │
│  │  ┌─────────────────────▼───────────────────────┐  │  │
│  │  │         Agent Orchestrator                   │  │  │
│  │  │  Plan → Tool Call → Observe → Loop          │  │  │
│  │  └────┬────────────┬──────────────┬────────────┘  │  │
│  │       │            │              │               │  │
│  │  ┌────▼────┐ ┌────▼────┐  ┌──────▼──────┐       │  │
│  │  │ Memory  │ │ Skills  │  │ spring-ai-  │       │  │
│  │  │ Manager │ │ Registry│  │ agent-utils │       │  │
│  │  └─────────┘ └─────────┘  │ (File/Shell │       │  │
│  │                           │  /Web 工具)  │       │  │
│  │                           └─────────────┘       │  │
│  │                        │                        │  │
│  │  ┌─────────────────────▼──────────────────────┐ │  │
│  │  │      LLM Gateway (模型路由)                 │ │  │
│  │  │      OpenAI / Anthropic / 本地模型          │ │  │
│  │  └────────────────────────────────────────────┘ │  │
│  └─────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────┐  │
│  │   SQLite + 文件系统                              │  │
│  └─────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

#### 5.2.2 目标架构（Phase 3 完成时）

```
┌───────────────────────────────────────────────────────────┐
│                    Web UI 层 (Browser SPA)                  │
│  ┌─────────────┐  ┌─────────────┐  ┌───────────────────┐ │
│  │  Workbench  │  │  Agent Chat │  │  Workspace Manager│ │
│  └─────────────┘  └─────────────┘  └───────────────────┘ │
│                        REST API / SSE                      │
├───────────────────────────────────────────────────────────┤
│                    API 网关 (Spring Boot)                   │
│  /api/chat · /api/workspace · /api/memory · /api/files    │
├───────────────────────────────────────────────────────────┤
│                  核心服务层 (Core Services)                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │  Agent   │ │  Memory  │ │  Skills  │ │    MCP     │  │
│  │Orchestr. │ │ Manager  │ │ Registry │ │   Gateway  │  │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘  │
├───────────────────────────────────────────────────────────┤
│                运行时层 (WASM + V8 Isolates)                │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐       │
│  │ Agent 1 │ │ Agent 2 │ │ Agent 3 │ │ Agent N │       │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘       │
├───────────────────────────────────────────────────────────┤
│              基础设施层 (Infrastructure)                    │
│  ┌───────┐ ┌───────────┐ ┌───────────┐ ┌──────────────┐ │
│  │ SQLite│ │   File    │ │    LLM    │ │   Config     │ │
│  │       │ │   System  │ │  Gateway  │ │   (规则)     │ │
│  └───────┘ └───────────┘ └───────────┘ └──────────────┘ │
└───────────────────────────────────────────────────────────┘
```

### 5.3 API 设计概要

#### 5.3.1 Agent 对话（流式 SSE）

```
POST /api/chat/stream
Content-Type: application/json

Request:
{
  "message": "帮我写一个 Spring Boot 的 REST API",
  "workspaceId": "ws_abc123",
  "sessionId": "sess_xyz789",
  "model": "auto"
}

Response (Server-Sent Events):
event: message
data: {"type": "thinking", "content": "分析用户需求..."}

event: message
data: {"type": "tool_call", "tool": "write_file", "params": {...}}

event: message
data: {"type": "tool_result", "content": "文件已创建"}

event: message
data: {"type": "final", "content": "已完成 REST API 创建"}
```

#### 5.3.2 Workspace 管理

```
GET    /api/workspace              — 列出所有项目
POST   /api/workspace              — 创建项目
GET    /api/workspace/:id          — 项目详情
PUT    /api/workspace/:id          — 更新项目配置
DELETE /api/workspace/:id          — 删除项目
POST   /api/workspace/:id/switch   — 切换当前项目
```

#### 5.3.3 记忆系统

```
POST   /api/memory/search          — 全文搜索记忆
POST   /api/memory                 — 创建记忆条目
PUT    /api/memory/:id             — 编辑记忆
DELETE /api/memory/:id             — 删除记忆
GET    /api/memory/:id             — 获取单条记忆详情
```

#### 5.3.4 文件操作

```
GET    /api/files/list?path=...    — 列出目录
POST   /api/files/read             — 读取文件内容
POST   /api/files/write            — 写入文件
POST   /api/files/upload           — 上传文件（multipart）
GET    /api/files/download/:id     — 下载文件
POST   /api/files/delete           — 删除文件/目录
GET    /api/files/preview/:id      — 预览文件
GET    /api/files/history/:id      — 文件版本历史
```

#### 5.3.5 终端

```
WebSocket /api/terminal/ws         — 终端会话
```

> **实现级契约（由 DEV-PLAN 定义，不在本规格展开）**：统一错误响应 `{code,message,details}` + HTTP 状态码约定、SSE `event: error` 语义（LLM 超时/工具失败）、列表端点 cursor 分页（memory/search、files/list）、终端 WebSocket 上下行消息 schema（命令/输出帧/退出码）、各 P0 面板空状态与边界。本节仅列端点契约骨架。

### 5.4 编码规范

1. **流式优先**：所有 Agent 输出采用 SSE 流式传输，前端逐 Token 渲染，不等待完整响应
2. **Agent 原语**：优先使用 `spring-ai-agent-utils` 提供的原语，不重复造轮子
3. **库优先**：能用现有库实现的功能，不手写函数
4. **函数参数**：参数超过 4 个必须使用实体类（DTO/Record）
5. **虚线程**：充分利用 Java 21 Virtual Threads 处理并发任务，减少显式线程池管理
6. **前端状态管理**：Phase 1 用 React `useState`（hooks 局部 + props）。原计划的 Netty EventLoop 统一调度器模式（ADR-003）经 code-review 确认从未落地、已退役；若后续状态交互复杂化再评估引入

---

## 六、数据模型

### 6.1 Workspace

```
workspace {
  id            TEXT PRIMARY KEY,
  name          TEXT NOT NULL,
  description   TEXT,
  type          TEXT CHECK(type IN ('personal', 'enterprise')),  -- Phase 2 增加 enterprise
  owner_id      TEXT NOT NULL,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP
}
```

### 6.2 Memory

```
memory {
  id            TEXT PRIMARY KEY,
  workspace_id  TEXT NOT NULL,
  source        TEXT NOT NULL,        -- 来源标识（对话/任务 ID）
  content       TEXT NOT NULL,
  tags          TEXT,                  -- JSON array
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (workspace_id) REFERENCES workspace(id)
}
```

### 6.3 Session

```
session {
  id            TEXT PRIMARY KEY,
  workspace_id  TEXT NOT NULL,
  title         TEXT,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (workspace_id) REFERENCES workspace(id)
}
```

### 6.4 FileVersion

```
file_version {
  id            TEXT PRIMARY KEY,
  workspace_id  TEXT NOT NULL,
  file_path     TEXT NOT NULL,
  version_no    INTEGER NOT NULL,
  content_ref   TEXT NOT NULL,        -- 文件系统内容引用
  created_by_session TEXT,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (workspace_id) REFERENCES workspace(id)
}
```

### 6.5 Task

```
task {
  id            TEXT PRIMARY KEY,
  workspace_id  TEXT NOT NULL,
  session_id    TEXT,
  status        TEXT CHECK(status IN ('pending', 'running', 'completed', 'failed', 'cancelled')),
  outcome       TEXT CHECK(outcome IN ('succeeded', 'partial', 'abandoned')),  -- Phase 1 用户产出统计
  model_used    TEXT,
  tokens_used   INTEGER,
  cost_usd      REAL,
  started_at    DATETIME,
  completed_at  DATETIME,
  FOREIGN KEY (workspace_id) REFERENCES workspace(id)
}
```

---

## 七、安全与权限

### 7.1 认证与授权

- **本地模式**：无强制认证（开箱即用）
- **企业模式（远期，v1.0.0 之后）**：支持 OAuth 2.0 / OIDC 集成（Keycloak、Okta）
- **角色**：Owner（项目所有者）、Member（成员）、Viewer（只读）
- **权限粒度**：可精细到单个文件或记忆条目的读写权限

### 7.2 Agent 权限控制

- **最小权限原则**：Agent 默认无任何权限，需要显式授予
- **工具级授权**：每个工具调用前校验 Agent 是否有权使用
- **用户确认模式**：高风险操作（如删除文件、发送邮件）需用户手动确认

### 7.3 数据加密

- **传输加密**：HTTPS / WSS (WebSocket Secure)
- **存储加密**：本地模式（Phase 1）用 SQLite 加密扩展（SEE）；企业模式（远期）可选 AWS KMS 集成

### 7.4 LLM 凭证管理（Phase 1）

- **存储**：API Key 本地加密存储（SQLite SEE）
- **配置 UI**：设置页提供 Provider 选择（OpenAI / Anthropic / 本地 Ollama）、API Key 录入、默认模型、连接测试
- **前置依赖**：M1.2 Agent 对话依赖此项完成

---

## 八、部署架构

### 8.1 单机部署（个人用户）

```
┌──────────────────────────────────┐
│           用户机器                │
│  ┌────────────────────────────┐  │
│  │   Docker / java -jar       │  │
│  │  ┌──────────────────────┐  │  │
│  │  │  Spring Boot 单 JAR   │  │  │
│  │  │  (REST API + Web UI)  │  │  │
│  │  └──────────────────────┘  │  │
│  │  ┌──────────────────────┐  │  │
│  │  │  SQLite + 文件系统    │  │  │
│  │  └──────────────────────┘  │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

特点：单命令启动，数据本地存储，无需额外服务器部署。AI 功能需 LLM API 网络连接。

### 8.2 企业部署（多用户/集群）— 远期规划（v1.0.0 之后）

> **Phase 1–3（v0.1.0–v1.0.0）均为单机部署（§8.1）。** 多用户集群部署（含数据库选型、Redis、对象存储）为 v1.0.0 之后的远期规划。下图为目标架构参考，非当前交付内容。

```
┌─────────────────────────────────────────────────┐
│                    负载均衡器                    │
├─────────────────────────────────────────────────┤
│  ┌─────────┐  ┌─────────┐  ┌─────────┐        │
│  │  App    │  │  App    │  │  App    │        │
│  │ Node 1  │  │ Node 2  │  │ Node N  │        │
│  └─────────┘  └─────────┘  └─────────┘        │
├─────────────────────────────────────────────────┤
│  ┌─────────┐  ┌─────────┐  ┌─────────────────┐ │
│  │  Redis  │  │  RDBMS  │  │  Object Storage │ │
│  │ (缓存)  │  │(PgSQL)  │  │ (S3/MinIO)     │ │
│  └─────────┘  └─────────┘  └─────────────────┘ │
│  ┌─────────────────────────────────────────────┐│
│  │  LLM Gateway (多模型路由)                   ││
│  └─────────────────────────────────────────────┘│
└─────────────────────────────────────────────────┘
```

---

## 九、开发路线图

> **与 DEV-PLAN 的 Phase 编号**：本节 Phase 1–3 是**产品里程碑**。DEV-PLAN 内另有「Phase 1–4」仅表示 v0.1.0 交付切片（对话/终端等），二者不可互换。见 `DEV-PLAN.md` 文首说明。

### Phase 1 — 核心底座（2026 Q3）

**目标**：可运行的 MVP，个人用户能开箱即用。

| 里程碑 | 功能 | 交付物 |
|--------|------|--------|
| M1.1 | Web 工作台 | 多面板布局、文件浏览、主题切换 |
| M1.2 | Agent 对话（SSE 流式） | 聊天界面 + 工具调用可视化 + 推理过程展示 |
| M1.3 | Workspace 隔离 | 项目创建/切换/删除、数据隔离 |
| M1.4 | 记忆系统 | 记忆 CRUD、搜索、来源追溯 |
| M1.5 | 文件操作（spring-ai-agent-utils） | 文件读写、上传下载、预览、版本历史 |
| M1.6 | 终端（WebSocket） | Unix-like 命令执行、AI 辅助命令行 |
| M1.7 | v0.1.0 发布 | Docker 镜像 + GitHub Release |

### Phase 2 — Harness 能力（2026 Q4）

**目标**：企业级能力，多 Agent 协作。

| 里程碑 | 功能 | 交付物 |
|--------|------|--------|
| M2.1 | 多 Agent 协作 | Planner/Implementer/Reviewer |
| M2.2 | MCP 协议支持 | Agent 与系统标准化通信 |
| M2.3 | Hook 安全网 | 幻觉检测、危险操作拦截 |
| M2.4 | 成本仪表盘 | Token 统计、预算告警 |
| M2.5 | v0.2.0 发布 | 企业功能可用 |

### Phase 3 — 自主与生态（2027 Q1-Q2）

**目标**：自主 Agent + 开源生态。

| 里程碑 | 功能 | 交付物 |
|--------|------|--------|
| M3.1 | 自主 Agent | 定时运行、Hands 能力包 |
| M3.2 | WASM+V8 运行时 | 轻量级 Agent 隔离 |
| M3.3 | 应用市场 | 社区技能/工具分享 |
| M3.4 | 多端适配 | 移动端响应式 |
| M3.5 | v1.0.0 正式发布 | 完整企业级 Agent 能力（集群高可用部署另立远期版本，见 §8.2） |

---

## 十、成功指标

| 指标 | Phase 1 目标 | Phase 3 目标 | 测量方式 |
|------|-------------|-------------|---------|
| Agent 冷启动时间 | < 5s | < 10ms | 性能测试 |
| 单机并发 Agent 数 | > 10 | > 100 | 压力测试 |
| 记忆检索准确率 | > 90% | > 90% | 人工评估 + 单元测试 |
| 用户上手时间 | < 5 分钟 | < 5 分钟 | 用户测试 |
| 开源社区贡献者 | — | > 50 人（6 个月内） | GitHub Insights |
| GitHub Stars | — | > 1,000（6 个月内） | GitHub 统计 |
| Docker 下载量 | — | > 10,000（6 个月内） | Docker Hub 统计 |
| 任务完成率（outcome=succeeded 占比） | 基线测量 | > 85% | Task 表统计 |
| 7 日留存 | 基线测量 | > 40% | 启动日志 |
| 周活 workspace | 基线测量 | > 50% MAU | 使用统计 |

---

## 十一、附录

### A. 参考项目

| 项目 | 借鉴点 |
|------|--------|
| ReqForge | 七层 Harness 架构、成熟度模型、Spec→Plan→Build 流程 |
| Agent UI | 流式对话 UI、工具调用可视化、推理过程展示 |
| ryOS | 窗口管理、内置应用布局模式 |
| Rivet agentOS | WASM+V8 隔离、~6ms 冷启动 |
| OpenFang | 自主 Agent、Hands 能力包、单二进制部署 |
| PilotDeck | Workspace 隔离、白盒记忆、智能路由 |
| pi-web | Web UI 设计参考：会话管理、SSE 流式交互、文件浏览、分支对话 |

### B. 开源协议说明

Tepeu 采用 Apache License 2.0：

- ✅ 商业友好，可闭源使用
- ✅ 专利授权保护
- ✅ 贡献者协议清晰
- ❌ 无"传染性"要求（与 GPL 不同）

### C. 发音指南

为了帮助全球开发者正确发音，在 README 和文档中统一标注：

Pronunciation: /tɛˈpeɪ.uː/ (Teh-PEH-oo)

---

*本规格说明书将随项目迭代持续更新。最新版本请参阅项目仓库 /docs/SPEC.md。*
