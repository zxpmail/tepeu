# v3-spec-client-kernel

> **来源**：../../04-gnex3.0-plugin-catalog.md §L5 gnex3-client-kernel
> **负责人**：<owner>
> **插件 ID**：gnex3-client-kernel
> **分层**：客户端层（L5）
> **受保护级别**：normal（管理员可装卸；前端模块化基座）
> **宿主服务**：前端（浏览器，d1-web）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-29/D-P1-30/D-P0-03

---

## §1 业务目标

前端模块化内核：路由注册（react-router-dom 7，22 页面路由可被模块增量注册）、状态管理（AuthContext/ProjectContext + chat-event-reducer 会话事件投影）、SSE 解析（chat-sse-event-handler + useChatSseReconnect 断线重连）。对齐 dsh「模型可见 ⟺ 已落盘」纪律：前端不另维护「当前上下文」，全部会话 UI 从 append-only 会话事件流投影。插件化价值：`router`/`state`/`sse` 三个内核服务支撑 client-modules 各功能模块自注册，内核不感知业务页面。

**非目标**：不做业务页面（归 client-modules）；不做认证逻辑（token 管理与 401 登出经 api 网关）；不做后端直连（统一 api 客户端层）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-client-web` + `dsh-client-runtime`（Web UI 与运行时壳） |
| 对标 GNEX2.0 模块 | d1-web frontend 内核：App.tsx 路由表（react-router-dom ^7.15.1）、contexts/AuthContext + ProjectContext、lib/chat-sse-event-handler + chat-event-reducer + chat-turn、hooks/useChatSseReconnect、lib/auth-roles/token |
| 技术选型 | 🅶 GNEX2.0 前端改造（模块化内核，React ^19.2.6 + Vite ^8.0.16 + Tailwind ^4.3.0） |
| 改造要点 | ① 内核服务化：`router`（路由表注册/懒加载）、`state`（会话事件投影 reducer + 上下文）、`sse`（SSE 解析/重连/心跳）；② 事件流投影：SSE 帧 → session/* 事件 → reducer 投影 UI 状态，不另存上下文；③ 断线重连：指数退避 + checkpoint resume（useChatCheckpointResume 对齐后端恢复）；④ 模块化：页面/组件按 client-modules 插件注册，内核零业务引用；⑤ 保持 vitest 单测门（chat-sse-event-handler/chat-event-reducer 已具单测） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

> **审计修订（D-P1-30）**：契约类型改用 **TS 类型 + 前端文件路径**（前端宿主为 TS/React，无 Java 包名）。类型定义文件位于 `d1-web/frontend/src/kernel/`，client-modules 经静态 import 消费。

| 服务名 | TS 类型（定义文件） | 说明 |
|--------|--------------------|------|
| `router` | `RouterService`（`src/kernel/router.ts`）：`interface RouterService { registerRoute(path: string, lazyElement: LazyExoticComponent): void; guard(path: string, role: Role): boolean }` | 路由注册（registerRoute(path, lazyElement)）/导航守卫 |
| `state` | `AppStateService`（`src/kernel/state.ts`）：`interface AppStateService { reduce(event: SessionEvent): TimelineState; auth: AuthContextValue; project: ProjectContextValue }` | 会话事件投影（reduce(event)）、Auth/Project 上下文 |
| `sse` | `SseChannel`（`src/kernel/sse.ts`）：`interface SseChannel { parse(frame: MessageEvent): SessionEvent \| null; reconnect(): Promise<void>; heartbeat(): void }` | SSE 连接管理：parse(frame)/reconnect()/heartbeat() |

### 3.2 注入服务（inject）

无（内核为前端最底层；可选注入 `api` 网关客户端用于 401/鉴权联动）。

### 3.3 事件（emit / listen）

> **审计修订（D-P1-30）**：标注事件通道——后端 cordis 事件经 **SSE 帧映射**到达前端；`auth/*` 为前端内部事件。

| 事件 | 方向 | 通道 | 载荷要点 |
|------|------|------|----------|
| `session/event` | listen | 后端事件经 SSE 帧 → `chat-sse-event-handler` 解析 | SSE 帧解析出的会话事件（append-only 投影源） |
| `session/flush` | listen | 后端事件经 SSE 帧映射 | 事件批量提交/落盘 |
| `auth/*` | emit | 前端内部事件（不落 SSE） | 登录态变更（token 失效 → 401 登出） |

## §4 配置与装配（审计修订 D-P1-29：静态模块化）

v3.0 前端装载机制为**静态模块化**，不做动态装载（无浏览器端插件内核；「插件」= 构建期模块边界）：

- **目录约定**：`src/kernel/`（本插件：router/state/sse + AuthContext/ProjectContext）与 `src/modules/<域>/`（client-modules 各功能域）分目录，模块间禁止横向 import，仅可 import `src/kernel/` 与 `src/api/`。
- **模块注册表（静态 import 图）**：`src/modules/registry.ts` 静态聚合各域模块导出（`import { chatModule } from './chat'` …），构建期生成完整路由/菜单表——**无运行期装卸**。
- **按需懒加载**：路由元素经 `React.lazy(() => import('./chat/ChatPage'))` 分包，首屏只载内核与登录页（Vite 自动 code-splitting）。
- **配置到达方式**：无 service-assembly.yml；内核参数为构建期常量（`src/kernel/config.ts`），运行期可调参数经 `/api/client-config` 启动时拉取。

```ts
// src/kernel/config.ts（内核配置常量）
export const kernelConfig = {
  sse: { reconnectBaseMs: 1000, heartbeatMs: 30000 },   // 断线重连指数退避基数 / 心跳保活
  state: { projection: 'append-only' },                  // 事件流投影，不另存上下文
  router: { lazy: true },                                // 路由懒加载
} as const;
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 模型可见内容必须从会话事件流投影（append-only；进模必留痕，对齐 dsh「模型可见 ⟺ 已落盘」）
- ✅ SSE 必须断线重连 + 心跳 + checkpoint resume，不丢事件
- ✅ 路由/状态必须模块化注册（内核不感知业务页面，client-modules 增量挂载）
- ✅ v3.0 前端为**静态模块化**（目录约定 + React.lazy 懒加载；D-P1-29）——模块边界清晰、无运行期装卸
- ✅ token 管理必须安全（内存 + 刷新，401 统一登出），不得明文落 localStorage
- ❌ 前端不得直接拼装后端 API（统一 api 客户端层 + 网关）
- ❌ SSE 解析/渲染不得阻塞 UI 主线程（异步 + 时间线虚拟化）
- ❌ v3.0 禁止运行期动态装卸前端模块（动态插件化列为 v3.1 评估项，D-P1-29）
- ❌ 前端不得自行维护「当前上下文」副本（只从事件流投影，对齐 dsh）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | SSE 帧解析 | `chat-sse-event-handler` 单测：event/error/heartbeat 帧解析正确 |
| V2 | 事件投影 | `chat-event-reducer` 单测：同一事件流两次投影结果一致（确定性） |
| V3 | 断线重连 | `useChatSseReconnect` 测试：断线指数退避重连，事件续接不丢 |
| V4 | checkpoint resume | `useChatCheckpointResume` 测试：resume 后时间线从 checkpoint 续接 |
| V5 | 路由注册 | `RouterTest`：模块注册路由懒加载生效，冲突路由拒绝 |
| V6 | 401 登出 | `AuthFlowTest`：token 失效统一登出，不残留会话状态 |

### 运行命令（审计修订 D-P0-03：前端验收统一 vitest，命令与 V1-V6 测试文件一一对应）

```bash
# 前端（vitest，测试文件位于 d1-web/frontend/src/**/__tests__/）
pnpm -C d1-web/frontend test -- chat-sse-event-handler chat-event-reducer useChatSseReconnect useChatCheckpointResume router auth-flow
# 后端 SSE 契约（前端内核依赖项；显式类名，替代原 *Sse* 宽泛通配）
mvn -pl services/gnex-agent-service test "-Dtest=SseEventFrameTest,SseReconnectContractTest"
```

## §7 影响域

- 作用范围：**浏览器**（单页控制台）。
- 被谁消费：client-modules 全部功能模块；经 SSE/网关消费后端会话流。
- 故障影响半径：内核缺陷 = **全部前端页面不可用（前端最大影响半径）**；SSE 断线 = 会话展示中断（重连恢复）。
- 升级影响：前端发版（Vite 构建产物进 gnex-assembler static）。

## §8 后续（未纳入首版）

- 离线缓存（service worker）与会话本地回放
- 多标签页同步（BroadcastChannel 事件投影）
- 内核热更新（HMR 对模块生效，内核自身灰度）

---

*GNEX3.0 前端内核插件 SDD。*
