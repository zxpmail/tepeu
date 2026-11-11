# v3-spec-tool-bash

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **审计修订：v0.2（2026-08-18）· 依据 D-P1-11 / P2-01 / P2-08 / P2-11**
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-bash
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

bash/进程执行工具：Agent 在沙箱内执行 shell 命令（单发 + 持久会话 persistent），复用工作区上下文。所有命令经 `shell` 服务（gnex3-cap-sandbox 的 shell 后端）执行，受 `sandboxPolicy` 档位约束（网络/写盘/资源；档位裁决为管线 guard 权威，见 §5），超时与取消可终止进程树。是 Agent 能力面最高风险工具之一，动态拼接/高危命令必须走 Approval（缺席审批=deny，fail-closed）。**非目标**：不做宿主机进程直启（仅运维显式授权的本地调试场景）；不做 Windows 语义（gnex3-cap-tool-pwsh）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-bash` + `dsh-tool-bash-persistent` |
| 代码实证来源 | agent-service `ProcessTool` `[源码实证：libs/gnex-agent-sdk/.../tools/ProcessTool.java]`（P2-01）+ `GnexSandboxExecTool`（沙箱执行）`[源码实证]` + gnex-agent-sdk `SandboxEnforcer` `[源码实证]` + platform-service `extension.spi.RiskClassifier` `[源码实证]` |
| 技术选型 | 🅶 GNEX2.0 模块改造（ProcessTool → 管线工具插件） |
| 改造要点 | ① 注册 `tool_bash` 到 `tools`（schema：command/workdir/timeout/env）② 执行走 `shell` 服务 → 沙箱 provider（local/opensandbox）③ sandboxPolicy 档位判定归管线 guard（D-P1-11：工具不重复裁决）④ persistent 会话回卷改为**会话 dispose/fiber effect**（P2-11；post-execute 仅资源检查） ⑤ 高危模式表（工具特有预检）路由 Risk→Approval |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `bashTools` | `BashTool` | 单发/持久 shell 执行实现（注册进 `tools`） |

### 3.2 注入服务（inject）

- `tools`（必选）— 注册工具定义
- `shell` / `sandboxPolicy`（必选）— 执行后端（沙箱 shell）与档位约束（档位裁决在管线 guard 内执行）
- `fs`（可选）— 工作区/路径映射
- `scope` / `events`（必选）— 会话隔离与管线收发
- `sessions`/fiber 生命周期（必选）— persistent 会话随会话 dispose 回卷（P2-11）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/pre-execute` | listen | **工具特有预检（D-P1-11）**：高危模式表命中（rm -rf / curl\|bash / sudo 等）→ 标记高影响转 Approval；档位裁决（网络/写盘/资源是否超档）归管线 sandbox-policy guard，本 listen 不做 |
| `tools/execute` | listen（waterfall） | 本体：解析命令 → shell 执行 → 结构化结果 |
| `tools/post-execute` | listen | **仅资源检查（P2-11）**：进程退出/句柄释放核查；persistent 会话回卷不在此时机（见 §5） |

## §4 配置与装配

```yaml
gnex.tools.bash:
  persistent-sessions: true
  high-risk-patterns: ["rm -rf", "\\|.*bash", "sudo", "curl.*\\|.*sh"]
  # 超时 SSOT（P2-08）：工具超时统一声明于 gnex3-cap-tools-timeout；
  # 此处不再配置第二超时源，避免双配置漂移
```

**超时单源（P2-08）**：超时策略（默认值/按工具 override/按档位分级）**SSOT 为 gnex3-cap-tools-timeout**（服务 `tools-timeout`，管线最外 wrapper）；本插件原 `timeout-ms: 60000` 双配置源删除——`tool_bash` 的默认 60s 经 tools-timeout 的按工具 override 声明，工具 schema 的 timeout 参数仅为单次调用上限（≤ override 值）。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 所有命令必须经 `shell` 服务在沙箱内执行（本地模式也走 sandbox 抽象，不直启宿主进程）
- ✅ **职责界定（D-P1-11）**：工具侧 `tools/pre-execute` listen 仅做工具特有预检——**高危模式表**（high-risk-patterns 命中 → Approval）；档位裁决（网络/写盘/资源）为**管线 sandbox-policy guard 权威**，工具不重复实现、不越权 deny
- ✅ 动态拼接/高危模式必须走 Approval，缺席审批=deny（fail-closed）
- ✅ 超时/取消必须终止整个进程树并释放沙箱（超时裁决归 tools-timeout wrapper，P2-08）
- ✅ **persistent 回卷时机（P2-11）**：持久会话在**会话 dispose / fiber effect 卸载**时回卷（生命周期与会话绑定）；`tools/post-execute` 仅做资源检查（进程退出/句柄释放），不承担回卷
- ❌ 持久会话不得跨会话复用（会话级；会话 dispose 必回卷）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 基本执行 | `BashRunTest`：echo 返回 stdout + 退出码 0 |
| V2 | 失败语义 | `BashFailTest`：非零退出码 + stderr 结构化返回 |
| V3 | 档位拒绝 | `BashTierDenyTest`：无网络档位执行 curl → 管线 sandbox-policy guard deny |
| V4 | 高危审批 | `BashApprovalTest`：`rm -rf` 无审批记录 → APPROVAL_REQUIRED（工具特有预检 → Approval） |
| V5 | 超时终止 | `BashTimeoutKillTest`：sleep 超时 → TIMED_OUT 且进程树已终止（tools-timeout SSOT，P2-08） |
| V6 | 持久会话 | `BashPersistentTest`：两次调用共享 cwd；会话 dispose/fiber 回卷生效（P2-11）；post-execute 仅资源检查 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=Bash*Test"
```

## §7 影响域

- 作用范围：**每工具调用**（L3.6 工具集）；被 Agent 循环（命令执行）、沙箱（后端）、审批（高危确认）、tools-timeout（超时 SSOT）消费。
- 故障影响半径：单工具不可用；命令执行失败不影响主进程（沙箱隔离）。
- 升级影响：工具装卸 = 无感（其余工具不动）。

## §8 后续（未纳入首版）

- 交互式 TTY 工具（长驻进程 stdin/stdout 流式）
- 命令历史/会话恢复（checkpoint 联动）与命令补全建议（loop 根因提示联动）

---

*GNEX3.0 bash/进程工具 — gnex3-cap-tool-bash SDD（审计修订版：预检职责界定 + 超时 SSOT + persistent 回卷时机）。*
