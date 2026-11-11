# v3-spec-tool-pwsh

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-pwsh
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

PowerShell 工具：Agent 在沙箱内执行 pwsh 7 命令/脚本，面向 Windows 自动化与跨平台 PowerShell 场景（bash 之外的第二执行语义）。与 bash 共享 `shell` 服务与 `sandboxPolicy` 档位约束，但使用独立 pwsh provider、独立命令集与高危模式表。所有调用走完整 `tools` 管线，高危模式走 Approval（缺席审批=deny，fail-closed）。

**非目标**：不做 Windows 宿主机直启（与 bash 一致，全部经沙箱）；不做 bash 语义兼容（两工具独立、独立配置）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-pwsh` |
| 对标 GNEX2.0 模块 | 无对应（新增能力；复用 `shell` 沙箱抽象与 `SandboxEnforcer`） |
| 技术选型 | 🅳 Java 移植 dsh-tool-pwsh（沙箱 shell 后端新增 pwsh 适配） |
| 改造要点 | ① 注册 `tool_pwsh` 到 `tools`（schema：command/script/workdir/timeout）② `shell` 服务新增 pwsh provider（复用 sandbox 抽象，profile 独立）③ 高危模式表独立维护（Remove-Item -Recurse/Invoke-Expression/下载执行）→ Risk→Approval ④ 错误按 pwsh 语义结构化（ErrorRecord）⑤ 超时/取消终止 pwsh 进程树 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `pwshTools` | `PwshTool` | pwsh 执行实现（注册进 `tools`） |

### 3.2 注入服务（inject）

- `tools`（必选）— 注册工具定义
- `shell` / `sandboxPolicy`（必选）— pwsh provider 后端与档位约束
- `fs`（可选）、`scope` / `events`（必选）— 工作区映射与会话隔离/管线收发

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/pre-execute` | listen | 档位自检 + 高危模式预检 |
| `tools/execute` | listen（waterfall） | 本体：pwsh 执行 → 结构化结果 |
| `tools/post-execute` | listen | 会话/进程清理 |

## §4 配置与装配

```yaml
gnex.tools.pwsh:
  executable: pwsh                     # pwsh 7；缺失则工具显式报错
  high-risk-patterns: ["Remove-Item\\s+-Recurse", "Invoke-Expression", "IEX", "DownloadString.*\\|.*Invoke"]
  timeout-ms: 60000
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 所有命令必须经 `shell` 服务在沙箱内执行（pwsh provider），不直启宿主机进程
- ✅ 命令受 `sandboxPolicy` 档位约束（网络/写盘/资源），档位不满足 deny
- ✅ 高危模式必须走 Approval，缺席审批=deny（fail-closed）
- ✅ 超时/取消必须终止 pwsh 进程树
- ❌ 不得复用 bash 的高危模式表（pwsh 语法独立，各自维护）
- ❌ pwsh 不可用时不得静默降级为 bash（必须显式报错）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 基本执行 | `PwshRunTest`：`Write-Output` 返回 stdout |
| V2 | 失败语义 | `PwshFailTest`：非零/ErrorRecord 结构化返回 |
| V3 | 档位拒绝 | `PwshTierDenyTest`：无网络档位执行 Invoke-WebRequest → deny |
| V4 | 高危审批 | `PwshApprovalTest`：Invoke-Expression 无审批 → APPROVAL_REQUIRED |
| V5 | 超时终止 | `PwshTimeoutKillTest`：Start-Sleep 超时 → TIMED_OUT 且进程树终止 |
| V6 | 不可用显式报错 | `PwshMissingTest`：executable 缺失 → 工具错误（不降级 bash） |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=Pwsh*Test"
```

## §7 影响域

- 作用范围：**每工具调用**（L3.6 工具集）；被 Agent 循环、沙箱（pwsh provider）、审批消费。
- 故障影响半径：单工具不可用；pwsh 后端缺陷不影响 bash（独立 provider）。
- 升级影响：工具装卸 = 无感。

## §8 后续（未纳入首版）

- 交互式 pwsh 会话（持久 TTY）
- DSC/模块管理（PowerShell Gallery）与 Windows 沙箱后端（opensandbox provider 就绪后）

---

*GNEX3.0 PowerShell 工具 — gnex3-cap-tool-pwsh SDD。*
