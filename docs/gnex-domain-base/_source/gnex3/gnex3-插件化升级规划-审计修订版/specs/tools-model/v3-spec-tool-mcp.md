# v3-spec-tool-mcp

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **审计修订：v0.2（2026-08-18）· 依据 D-P1-10 / P2-01**
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-mcp
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service（D-P1-10 修订：原 registry-service → **agent-service**）
> **实现状态**：规划中

---

## §1 业务目标

MCP（Model Context Protocol）客户端工具：将外部 MCP 服务器的工具纳入 Agent 工具集。**架构裁定（D-P1-10）**：agent-service 工具管线为**唯一执行入口**——本插件宿主 agent-service，作为管线工具（包装 `McpToolRuntimePort` 远程调用 registry-service 管理的连接）；registry-service 只做 MCP 连接/目录管理（McpConnectionRegistry 保留在 registry 侧）。工具发现（tools/list）与 JSON-RPC 调用经 Port 远程完成；`tool/call`·`tool/result` 事件**仅由 agent-service 管线 emit（单一来源）**；连接不可达经状态端口上报并驱逐。**非目标**：SSE/stdio 传输扩展（首版 Streamable HTTP）与 MCP 场景包编排（留 §8）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-mcp-client` |
| 代码实证来源 | registry.mcp `McpConnectionRegistry` + `McpHttpTransport`（Streamable HTTP/JSON-RPC）`[源码实证]`（P2-01，保留在 registry-service）+ platform `skill.tools.mcp.McpToolRegistry`（McpToolRuntimePort 运行时）`[源码实证]` + 自定义工具注册表（CustomToolMetadataRegistry / CustomToolRegistry）`[源码实证]` |
| 技术选型 | 🅶 gnex2.0 模块改造 |
| 改造要点 | ① 连接注册表/HTTP 传输/凭据头注入保留在 registry-service（连接/目录管理）② 本插件宿主 **agent-service**：作为管线工具包装 `McpToolRuntimePort`（跨服务 Port 契约，D-P1-34 解析规则），远程调用 registry 侧连接 ③ 工具调用统一走 agent-service tools 管线，tool/call · tool/result **仅由 agent-service 管线 emit（单一来源，D-P1-10）** ④ 连接状态经 McpConnectionStatusPort 上报（registry 侧），不可达驱逐 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `mcp` | `McpClientService` | 管线工具门面：toolsList/call（经 McpToolRuntimePort 远程）；connect/disconnect/reconnect 属 registry-service 连接管理，非本插件职责 |

### 3.2 注入服务（inject）

- `tools`（必选）：注册 MCP 工具到 agent-service 管线（唯一执行入口，D-P1-10）
- `McpToolRuntimePort`（必选）：跨服务 Port 契约（gnex-contracts，REST），包装 registry-service 侧连接执行 MCP 调用
- `settings`（必选）：超时配置；`credentials`（必选）：凭据头注入（CredentialHandlerPort，🔒 服务）
- `scope`（必选）：租户隔离（连接不跨租户复用）
- `events`（必选）：管线事件收发（见 §3.3）

### 3.3 事件（emit / listen）

**管线消费说明（D-P1-10）**：本插件宿主 agent-service，事件在 agent-service 进程内管线（`tools`）上收发——跨服务执行经 McpToolRuntimePort（Port/REST），**不做跨进程事件 listen**（D-P1-34：跨服务一律 Port/REST/Bus）：

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/pre-execute` | listen | 工具特有预检：目标工具在白名单（tools/list 发现结果）内 |
| `tools/execute` | listen（waterfall） | 本体：经 McpToolRuntimePort 远程 JSON-RPC 调用 + 载荷截断 |
| `tools/post-execute` | listen | 调用后连接状态检查 |
| `tool/call` | emit（唯一来源） | {serverName, toolName, args}——仅 agent-service 管线 emit（D-P1-10） |
| `tool/result` | emit（唯一来源） | {serverName, toolName, status, result}（截断后） |

## §4 配置与装配

```yaml
# service-assembly.yml 片段（agent-service）
plugins:
  - id: gnex3-cap-tool-mcp
gnex.mcp:
  connect-timeout-ms: 5000    # 握手超时（经 Port 远程）
  request-timeout-ms: 60000   # JSON-RPC 调用超时
  max-tools-per-server: 100   # 单服务器工具白名单上限
  credential-mode: inherit    # 凭据头注入模式
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 调用必须完成 initialize 握手与协议版本协商（Streamable HTTP/JSON-RPC，registry 侧执行）；凭据必须经 CredentialHandlerPort 头注入，禁止明文落库/日志
- ✅ 工具必须经 tools/list 发现后才可调用（白名单）；连接不可达必须上报状态并驱逐（McpConnectionStatusPort，registry 侧）
- ✅ **唯一执行入口（D-P1-10）**：所有 MCP 工具调用必须经 agent-service 工具管线；tool/call · tool/result 仅由 agent-service 管线 emit，registry-service 不得 emit 工具事件
- ❌ 禁止跨租户复用连接（scope 硬隔离，串租户 = P0）；禁止绕过工具管线直接调用 MCP 工具（审计/审批旁路）
- ❌ 禁止跨服务 Ctx 注入（连接管理在 registry-service，消费在 agent-service，只经 McpToolRuntimePort/REST，D-P1-34）
- ❌ 禁止执行未注册工具或未知方法；服务器返回超限载荷必须截断（防上下文溢出）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 连接握手 | `McpConnectHandshakeTest`：initialize + 协议版本协商（经 Port 远程到 registry 侧连接） |
| V2 | 工具发现 | `McpToolsListTest`：tools/list 结果注册为白名单，未注册工具调用拒绝 |
| V3 | 凭据注入 | `McpCredentialInjectionTest`：credentialHandler 头注入且日志脱敏 |
| V4 | 状态上报 | `McpUnreachableTest`：连接不可达 → 状态上报 + 驱逐 |
| V5 | 租户隔离 | `McpTenantIsolationTest`：不同租户连接不共享 |
| V6 | 管线合规（D-P1-10/D-P0-03） | `McpPipelineTest`（**归属 agent-service**）：调用走 agent-service tools 管线，tool/call · tool/result 仅由 agent-service emit；registry-service 不产生工具事件 |

### 运行命令

```bash
# D-P0-03/D-P1-10：MCP 管线测试归属 agent-service（自 registry-service 迁移）
mvn -pl services/gnex-agent-service -am test "-Dtest=McpToolPlugin*Test,McpPipeline*Test"
```

## §7 影响域

- 作用范围：**每工具调用**（agent-service 管线）+ **服务级/租户级连接**（registry-service 连接管理）。
- 被谁消费：agent-service 工具注册表（管线内，包装 McpToolRuntimePort）、registry-service（连接/目录）、UI（连接管理，registry 侧 API）、审计（tool/result，agent 侧）；故障影响半径：单个 MCP 服务器连接失败不影响其他服务器/工具，连接不可达自动驱逐。
- 升级影响：插件化改造保持 2.0 接口兼容；工具装卸无感（05 §3 L3.6；05 §1 注：连接管理在 registry、管线消费在 agent）。

## §8 后续（未纳入首版）

- SSE/stdio 传输扩展与连接池/热重连；MCP 场景包（McpScenePackage）联动编排
- MCP 工具 schema 校验与参数规约

---

*GNEX3.0 MCP 客户端工具插件 — 宿主 agent-service、管线唯一执行入口 SDD（审计修订版，D-P1-10）。*
