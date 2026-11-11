# v3-spec-tools-registry

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.5 工具注册表与管线
> **审计修订：v0.2（2026-08-18）· 依据 D-P1-05 / D-P1-06 / D-P1-07 / D-P1-08 / D-P1-11**
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tools
> **分层**：L3 能力层 · 3.5 工具注册表与管线
> **受保护级别**：protected（平台持有，禁租户/Agent 卸载；含内置 guard 集）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

GNEX3.0 的统一工具注册表与执行管线：工具插件把 schema/权限档/超时策略注册进 `tools` 服务，Agent 循环经单一入口执行。管线 = `tools/pre-execute`（waterfall guard 序）→ `tools/execute`（wrapper：timeout（最外）→ spill → 本体）→ `tools/post-execute` → `tools/result`（观测）。把 GNEX2.0 Inspector 链融合为管线 guard 序，风险检查从"调用方自选"提升为"管线强制、fail-closed"。

**guard 两级语义（D-P1-05）**：
- **mandatory guard**（approval/permission/sandbox-policy/tenant/gate 相关）：缺失 → **deny（fail-closed）**；
- **optional guard**（loop 检测器——成本保护、非安全边界）：缺失 → **跳过 + 观测告警**，不阻断调用。

**wrapper 顺序（D-P1-06）**：`tools/execute` 的 wrapper 链固定为 **timeout（最外）→ spill → 工具本体**；v3.0 **不做工具级自动重试**（重试语义归 gnex3-cap-model-retry，LLM 请求级；工具调用失败由 Agent 循环重发 + 工具幂等设计兜底）。

**非目标**：不实现工具本体（§3.6 工具插件提供）；不做 MCP 连接（gnex3-cap-tool-mcp）；不做跨进程工具执行；不做工具级自动重试（D-P1-06）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tools`（工具注册表 + pre/guard/execute/post/result 管线） |
| 对标 GNEX2.0 模块 | gnex-agent-sdk `InspectorChain`/`SandboxEnforcer`/`RiskAwareToolCallback`（设计态标注：类名以 gnex-agent-sdk 源码为最终核实基准）+ agent-service `agent.tools`（ToolCallback：WebSearchTool/GitTool/GnexSandboxExecTool 等）+ platform-service `extension.spi.RiskClassifier` |
| 技术选型 | 🅶 GNEX2.0 模块改造（Inspector 链融合 · 🔒 protected） |
| 改造要点 | ① `tools` = 注册表 + 管线驱动器 ② Inspector 链重挂为管线 guard 按序裁决，任一 deny 即拦截 ③ guard 分两类：四个内置 guard（D-P1-07）+ 独立插件 guard（approval/permission，mandatory）④ loop 为 optional guard（缺失跳过+告警）⑤ Approval fail-closed 硬接线 ⑥ emit `tool/call`/`tool/result` 先入日志 ⑦ 完成证据门（gate/check）独立于工具结果 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `tools` | `ToolsRegistry` + `ToolPipeline` | 工具注册/注销/查询 + 管线驱动器（guard/execute/post/result） |
| `sandboxGuard` | `SandboxEnforcer`（内置） | 沙箱档位裁决：读会话模式/档位/网络策略，批准经 `SandboxPort.ensure` 交 sandbox-runtime 执行（对齐 D-P0-01：裁决在 agent-service 进程内） |
| `pathGuard` | `PathInspector`（内置） | 路径越权/根逃逸检查（工作区根硬约束） |
| `riskGuard` | `RiskInspector`（内置） | 风险分级检查（对齐 RiskAwareToolCallback 语义） |
| `securityGuard` | `SecurityInspector`（内置） | 注入/命令高危模式等安全检查 |

**内置 guard 契约（D-P1-07）**：SandboxEnforcer / PathInspector / RiskInspector / SecurityInspector 为**本插件（gnex3-cap-tools）的内置 guard 实现**，随插件 mandatory 装配、**配置化启停**（仅允许整体禁用单个内置 guard，见 §4）、**顺序固定**（不可重排）；approval / permission 为**独立插件**（gnex3-cap-approval / gnex3-cap-permission），管线中以 mandatory 语义对待。

### 3.2 注入服务（inject）

- `events` / `scope` / `audit` / `observability`（必选）— 事件驱动、会话隔离、工具审计与观测
- `approval`（必选，mandatory guard）— 高风险审批原语（fail-closed：缺失=deny）
- `permission`（必选，mandatory guard）— 权限预设
- `sandboxPolicy`（可选注入，内置 sandbox guard 的档位来源）— 沙箱档位/网络策略查询
- `spill`（可选）— 溢出存储 wrapper（execute wrapper 链第二层）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/pre-execute` | emit（waterfall） | {toolName, args, scope, permissionTier}；guard 可 deny（mandatory 缺失也 deny） |
| `tools/execute` | emit（waterfall） | {toolName, args, executionContext}；wrapper 链 timeout→spill→工具本体 listener 返回结果 |
| `tools/post-execute` | emit（waterfall） | {toolName, result, duration}；清理/回卷（监听序 pruner→spill，对齐 D-P1-28） |
| `tools/result` | emit（waterfall） | {toolName, result, outcome, auditRef}；观测 |
| `tool/call` | emit | 执行前入日志 {toolCallId, toolName, args} |
| `tool/result` | emit | 执行后入日志 {toolCallId, outcome, truncated} |
| `write-evidence` | emit（由工具经管线发布） | 统一写证据契约 `writeEvidence { paths: string[], hash: string, turnId: string, ts: number }`（D-P1-08）——**工具成功后由工具 emit**，gate 消费 |
| `approval/request` | emit | 高风险调用入队（P1 E：默认入队 + 同 turn 回放） |
| `approval/decided` | listen | 通过 → 回放挂起调用；拒绝 → DENIED |
| `gate/check` | listen | 完成证据门独立校验（工具结果不得绕过；消费 `write-evidence`） |

## §4 配置与装配

```yaml
# service-assembly.yml（agent-service）
plugins:
  - {id: gnex3-cap-tools, load: mandatory, protection: platform}
gnex.tools.pipeline:
  fail-closed: true          # mandatory guard 缺席 → deny
  guard-order: [loop, permission, sandbox-enforcer, path, risk, security, approval]  # 只读常量序（代码内固定）
  guards:                    # 配置仅允许启停 optional/内置 guard，不可改序（D-P1-05/P2-10）
    loop: {enabled: true, mandatory: false}          # optional：缺失/禁用 = 跳过 + 观测告警
    permission: {mandatory: true}                     # 独立插件，mandatory
    sandbox-enforcer: {enabled: true}                 # 内置 guard（可配置启停，序固定）
    path: {enabled: true}
    risk: {enabled: true}
    security: {enabled: true}
    approval: {mandatory: true}                       # 独立插件，mandatory
  wrapper-order: [timeout, spill]   # 只读常量：timeout 最外 → spill → 本体（D-P1-06）
  log-before-execute: true   # tool/call 先入日志再执行
```

说明：`guard-order` 为**只读常量序**（编译期固定，配置不可重排——可配置序本身是绕过面）；配置项仅允许**启停 optional guard（loop）与内置 guard**；approval/permission 的 mandatory 语义不可配置关闭。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ guard 序固定：Loop → Permission → SandboxEnforcer → Path → Risk → Security → Approval（只读常量序；配置仅启停 optional/内置 guard，禁重排）
- ✅ guard 两级语义（D-P1-05）：**mandatory guard**（approval/permission/sandbox-policy/tenant/gate 相关）缺失 → **deny fail-closed**；**optional guard**（loop 检测器）缺失 → **跳过 + 观测告警**（loop 是成本保护非安全边界）
- ✅ 缺席审批=deny（fail-closed）：无 `approval/decided` 通过记录的高风险调用不得执行
- ✅ wrapper 顺序固定：timeout（最外）→ spill → 工具本体（D-P1-06）；v3.0 **无工具级自动重试**（不做重试 wrapper；重试语义归 model-retry 的 LLM 请求级）
- ✅ 内置 guard 契约（D-P1-07）：SandboxEnforcer/PathInspector/RiskInspector/SecurityInspector 为本插件内置实现（配置化启停、顺序固定）；approval/permission 为独立插件、mandatory
- ✅ 统一写证据契约（D-P1-08）：写路径工具成功后 emit `write-evidence`（`writeEvidence { paths, hash, turnId, ts }`），gate 消费；工具间不得另造证据词汇
- ✅ 管线 guard 为**安全权威**；工具插件侧 `tools/pre-execute` listen 仅做**工具特有预检**（如 bash 高危模式表、web 提供商路由/SSRF 域名清单），不重复管线 guard 职责（D-P1-11）
- ✅ 全部工具调用必须走 `tools` 单一入口；`tool/call` 先入日志再执行；注册表为宿主服务层单例（对齐 05 归属规则）
- ❌ 完成证据门不得被工具结果绕过（gate/check 独立；工具结果不得直接定案 completed）
- ❌ 未注册/无权限/绕过 guard 的调用不得执行（禁止静默降级为直接调用）
- ❌ mandatory guard 缺失不得静默放行；optional guard 缺失不得误判为 deny（两级语义不得互换）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 工具注册/查询 | `ToolsRegistryTest`：注册 ToolDefinition 后可解析，重复注册拒绝 |
| V2 | 管线顺序 | `ToolPipelineTest`：pre-execute 监听器按 guard-order 依序调用 |
| V3 | 任一 guard deny | `ToolPipelineTest`：Path/Approval/内置 Security 任一 deny → execute 不执行 |
| V4 | 审批缺席 fail-closed | `ToolApprovalFloorTest`：高风险无审批记录 → deny（APPROVAL_REQUIRED） |
| V5 | 日志先于执行 | `ToolAuditLogTest`：`tool/call` 先于 execute 落审计 |
| V6 | 完成证据门独立 | `ToolGateBypassTest`：工具返回成功但无 `write-evidence` → gate/check 仍 deny completed（统一 writeEvidence 契约，D-P1-08） |
| V7 | mandatory guard 缺失 fail-closed | `ToolGuardMandatoryTest`：卸载/禁用 permission 或 approval 注入 → 全部调用 deny（不得放行）（D-P1-05） |
| V8 | optional guard 缺失跳过+告警 | `ToolGuardOptionalTest`：loop 检测器缺席 → 调用照常执行 + observability 收到告警事件（不 deny）（D-P1-05） |
| V9 | wrapper 顺序 | `ToolWrapperOrderTest`：execute wrapper 链 = timeout 最外 → spill → 本体；超时取消可穿透 spill 到达本体（D-P1-06） |
| V10 | guard 序只读 | `ToolGuardOrderImmutableTest`：配置试图重排/改写 guard-order → 启动拒绝或忽略并告警；仅启停 optional/内置 guard 生效（D-P1-05/P2-10） |
| V11 | 内置 guard 启停 | `ToolBuiltinGuardToggleTest`：禁用内置 risk guard → 该检查跳过且有配置审计记录；sandbox/path/security 同理（D-P1-07） |
| V12 | 统一写证据发布 | `ToolWriteEvidenceTest`：写路径工具成功后 emit `write-evidence`，载荷字段 = writeEvidence 契约（D-P1-08） |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=Tool*Test"
```

（通配覆盖 V1-V12 全部测试类：ToolsRegistryTest/ToolPipelineTest/ToolApprovalFloorTest/ToolAuditLogTest/ToolGateBypassTest/ToolGuardMandatoryTest/ToolGuardOptionalTest/ToolWrapperOrderTest/ToolGuardOrderImmutableTest/ToolBuiltinGuardToggleTest/ToolWriteEvidenceTest；测试类内方法名与 Vn 一一对应，对齐 D-P0-03。）

## §7 影响域

- 作用范围：**每工具调用**（L3.5 工具管线）——全部工具执行路径；被 agentLoop 与全部 §3.6 工具插件消费。
- 故障影响半径：单次调用被拦截；guard 增删/启停 = 全工具行为变化（V1-V12 为发布门）；mandatory guard 缺失 = 全工具 deny（fail-closed）；optional（loop）缺失 = 失去成本保护 + 告警（调用不受阻）；升级：管线/guard 变更 = 全工具回归（🔒 平台级升级，禁租户/Agent 装卸）。
- 关联决策：D-P1-05（guard 两级语义）、D-P1-06（wrapper 序/无工具级重试）、D-P1-07（内置 guard 归属）、D-P1-08（writeEvidence 契约）、D-P1-11（工具侧仅特有预检）。

## §8 后续（未纳入首版）

- 工具级流控/配额（billing 联动）、guard 顺序可视化编排（inventory 管理面）、工具调用因果追溯（gate/check 证据链）
- 工具级重试机制（如 v3.1 评估：显式 retry-on-timeout 白名单 + 幂等约束；当前 v3.0 明确不做，D-P1-06）

---

*GNEX3.0 工具注册表与执行管线 — gnex3-cap-tools SDD（🔒 protected，含内置 guard 集）。审计修订 v0.2。*
