# v3-spec-client-modules

> **来源**：../../04-gnex3.0-plugin-catalog.md §L5 gnex3-client-modules
> **负责人**：<owner>
> **插件 ID**：gnex3-client-modules
> **分层**：客户端层（L5）
> **受保护级别**：normal（管理员可装卸；前端模块化）
> **宿主服务**：前端（浏览器，d1-web）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-30/D-P0-03

---

## §1 业务目标

把 d1-web 的 22 个页面路由与约 40 个 API 客户端按功能域模块化：会话（ChatPage 时间线/任务/审批确认/用户提问/checkpoint resume）、工具（ToolsPage/AgentManagementPage：自定义工具/宏/MCP/Agent 安装）、运营（OperationsPage/ObservabilityPage/Plans/Workflow/Project）、审计（Governance 壳：Audit/用户/角色/凭证/设置/沙箱 Profile/Connect/计费/SSO）、设置（AccountSettingsPage）。每个模块自注册路由/菜单/API 客户端到 client-kernel，互不耦合。目标：前端能力边界可枚举、可独立演进，新增页面不碰内核。

**非目标**：不做内核能力（路由/状态/SSE 归 client-kernel）；不做后端逻辑（只消费 api 网关）；不做权限决策（角色判断走 AuthContext）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-client-ui-*`（32 个 UI 插件） |
| 对标 GNEX2.0 模块 | d1-web：22 页面路由（ChatPage/ToolsPage/OperationsPage/ObservabilityPage/GovernancePage 子路由/AccountSettingsPage 等）+ ~40 API 客户端（api/：chat、auth、tools、users、roles、audit、ssoConfig、operations、runEvents、messageBus…）+ components/（chat/operations/observability/tools/workflow/layout/confirmation） |
| 技术选型 | 🅶 GNEX2.0 前端改造（模块化；React ^19.2.6 + Vite ^8.0.16 + Tailwind ^4.3.0） |
| 改造要点 | ① 模块划分：chat/tools/ops/audit/settings 五域，每域 = 路由注册 + 菜单 + API 客户端 + 组件集；② 会话模块消费 session/* 事件流投影（对齐内核 append-only）；③ 审计/运营模块全部只读（写操作走管理面/审批）；④ API 客户端统一经 api 网关（约 40 个收敛为模块级 client）；⑤ 权限：角色判断经 AuthContext（pickDisplayRole），页面按角色守卫 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

> **审计修订（D-P1-30）**：契约类型改用 **TS 命名 + 前端文件路径**（`src/modules/<域>/` 目录约定，见 client-kernel §4），删除 Java 包名。每域模块经 `src/modules/registry.ts` 静态注册表向内核提供路由/菜单/客户端声明。

| 服务名 | TS 模块（前端文件路径） | 说明 |
|--------|------------------------|------|
| `chatUi` | `src/modules/chat/ChatUiModule.ts`（默认导出 `ChatUiModule implements UiModule`） | 会话时间线/任务面板/审批确认/用户提问/token 用量 |
| `toolsUi` | `src/modules/tools/ToolsUiModule.ts` | 工具管理/Agent 管理（自定义工具/宏/MCP） |
| `opsUi` | `src/modules/ops/OpsUiModule.ts` | 运维台/可观测台/计划/工作流/项目 |
| `auditUi` | `src/modules/audit/AuditUiModule.ts` | 审计/用户/角色/凭证/设置/SSO/计费 |
| `settingsUi` | `src/modules/settings/SettingsUiModule.ts` | 个人设置（资料/默认模型/BYOK/改密） |

统一模块契约（`src/kernel/module.ts`）：`interface UiModule { id: string; registerRoutes(router: RouterService): void; menu?: MenuItem[]; apiClients?: ApiClient[] }`

### 3.2 注入服务（inject）

- `router` / `state` / `sse`（必选）：client-kernel 内核服务（路由注册、事件投影、SSE 通道）
- `api`（可选）：api 网关客户端（REST/gRPC/SSE 协议面）

### 3.3 事件（emit / listen）

> **审计修订（D-P1-30）**：事件表分两栏——「后端 SSE 帧映射」（后端 cordis 事件经 SSE 到达前端）与「前端内部事件」（模块内组件通信，不落 SSE）。

**后端 SSE 帧映射**（经 client-kernel `sse.parse(frame)` → `state.reduce(event)` 投影）：

| 后端事件 | SSE 帧类型 | 模块消费 | 载荷要点 |
|----------|-----------|----------|----------|
| `session/event`、`session/flush` | `event` / `flush` 帧 | chatUi | 会话时间线渲染（append-only 投影） |
| `tool/call`、`tool/result` | tool 块帧 | chatUi | 工具调用块/结果渲染 |
| `approval/request`、`approval/decided` | approval 帧 | chatUi | 风险确认弹窗（RiskConfirmDialog） |
| `gate/check`、`gate/passed` | gate 帧 | chatUi | 完成证据门展示 |
| `audit/record` | audit 帧（审计页流） | auditUi | 审计页流水（只读展示） |

**前端内部事件**（模块内部/模块→内核，不经网络）：

| 事件 | 方向 | 模块 | 载荷要点 |
|------|------|------|----------|
| `module/route-registered` | emit → 内核 registry | 各 UiModule | 模块路由/菜单注册完成（静态注册表构建期生成） |
| `module/nav` | emit | 各 UiModule | 模块内导航/面板切换（UI 状态） |

## §4 配置与装配（审计修订 D-P1-29/D-P1-30：对齐内核静态模块化）

前端无 service-assembly.yml；「装卸」语义为**构建期模块边界**（目录 + 静态注册表），非运行期动态装载：

```ts
// src/modules/registry.ts（静态 import 图；注释掉某域 import = 构建期移除该模块）
import { chatUiModule } from './chat/ChatUiModule';
import { toolsUiModule } from './tools/ToolsUiModule';
import { opsUiModule } from './ops/OpsUiModule';
import { auditUiModule } from './audit/AuditUiModule';
import { settingsUiModule } from './settings/SettingsUiModule';
export const uiModules: UiModule[] = [chatUiModule, toolsUiModule, opsUiModule, auditUiModule, settingsUiModule];

// 模块级配置常量（src/modules/config.ts）
export const modulesConfig = {
  audit: { readonly: true },            // 审计/运营只读
  chat: { timelineVirtualized: true },  // 时间线虚拟化
  tools: { customEnabled: true },       // 自定义工具
} as const;
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 每个功能模块必须自注册路由/菜单/API 客户端（模块化，互不耦合；v3.0 为构建期模块边界——移除某域 import 即整体下线该模块，不影响其他域，D-P1-29）
- ✅ 会话时间线必须从事件流投影（append-only），与后端日志一致
- ✅ 审计/运营页面必须只读（写操作走管理面/审批，页面无写入口）
- ✅ 凭证/密钥展示必须掩码（masked），刷新走专用 API
- ❌ 模块不得直接访问后端（统一经 api 网关 + 模块级客户端）
- ❌ 模块不得硬编码角色判断（走 AuthContext 角色上下文 + 页面守卫）
- ❌ 前端模块不得绕过 SSE 事件流直接轮询会话（对齐 append-only 投影纪律）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 模块自注册 | `ModuleRegistryTest`：chat/tools/ops/audit/settings 路由+菜单注册，装卸不影响其他模块 |
| V2 | 会话时间线 | `ChatTimelineTest`：session/* 事件流投影渲染与后端日志一致 |
| V3 | 审批确认 | `ApprovalUiTest`：approval/request 弹窗，decided 后关闭并回显 |
| V4 | 审计只读 | `AuditUiTest`：审计页无写入口，action 中文说明回退原始码 |
| V5 | 凭证掩码 | `CredentialMaskTest`：masked key 展示，明文不落 DOM |
| V6 | 角色守卫 | `RoleGuardTest`：非管理员访问 Governance 路由被拒（重定向） |
| V7 | API 客户端 | `ApiClientTest`：模块级客户端统一走网关，错误统一包装解析 |

### 运行命令（审计修订 D-P0-03：`-Dtest=*Ui*` 后端零匹配，已删除；前端验收统一 vitest）

```bash
# 前端（vitest + testing-library；测试文件位于 src/modules/**/__tests__/）
pnpm -C d1-web/frontend test -- module-registry chat-timeline approval-ui audit-ui credential-mask role-guard api-client
# 后端契约面（审计/管理端点）：指明真实落点为各服务的 Controller 契约测试，归 ent-api/ent-audit spec 验收（本 spec 不重复）
```

## §7 影响域

- 作用范围：**浏览器**（单页控制台，按模块影响）。
- 被谁消费：运营/管理/开发者用户；经 api 网关消费后端能力。
- 故障影响半径：单模块缺陷 = 该页面不可用（其余模块不受影响）；SSE/网关断连 = 会话展示中断。
- 升级影响：前端发版；模块增删 = 构建期注册表增删 + 前端发版（v3.0 无运行期装卸，D-P1-29）。

## §8 后续（未纳入首版）

- 更多 dsh-client-ui-* 模块移植（terminal/lsp/run_code 等专业面板）
- 国际化（i18n）与主题化（暗色/品牌）
- 移动端适配（响应式布局收敛）
---
*GNEX3.0 前端功能模块插件 SDD。*
