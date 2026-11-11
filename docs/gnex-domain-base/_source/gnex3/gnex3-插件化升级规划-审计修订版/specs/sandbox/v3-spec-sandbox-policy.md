# v3-spec-sandbox-policy

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.9
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-sandbox-policy
> **分层**：L3 能力层（3.9 沙箱与执行）
> **受保护级别**：protected（平台持有，禁租户/Agent 卸载）
> **宿主服务**：agent-service（gnex-agent-service）——修订自 sandbox-runtime（D-P0-01）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P0-01、D-P1-25、D-P1-34

---

## §1 业务目标
沙箱策略插件：统一执行平面的准入裁决——权限档位、配额、网络策略、跨租户守卫，以及 backend 可用性兜底。**定位（D-P0-01）：agent-service 工具管线 guard（SandboxEnforcer 位置），进程内裁决**——读会话模式/档位/网络策略，在 agent-service 进程内同步完成 waterfall 裁决；批准后经 `SandboxPort.ensure`（既有 REST `/internal/sandbox/ensure`）请求 sandbox-runtime 执行（执行平面不做裁决）。

对齐 dsh-sandbox-policy 语义：权限档位只定义**文件写效果**（read-only / workspace-write / full-access），升级走 `sandbox_permissions` + 人审批；无可用 backend 抛 `SANDBOX_UNAVAILABLE`，禁止静默放行。继承 gnex2.0 sandbox-runtime 的 GateChain（TypeMatch→Quota→NetworkPolicy→CrossTenantGuard）门禁序与 preflight 校验（池水位配置合法性），策略调整不影响执行抽象与后端。🔒 受保护：策略是执行平面安全底线，租户/Agent 无装卸权。

**非目标**：不实现具体隔离（归 backends，宿主 sandbox-runtime）；不做池（归 pool）；不做工具级授权（归 gnex3-cap-permission，本插件只管沙箱域）；不在 sandbox-runtime 进程内裁决（裁决唯一入口 = agent-service 管线 guard）。

## §2 映射关系
| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-sandbox-policy`（confine(argv, policy)；档位=文件写效果；升级经 sandbox_permissions+人审批；无 backend 抛 SANDBOX_UNAVAILABLE） |
| 对标 GNEX2.0 模块 | `gnex-agent-sdk` SandboxEnforcer（裁决位置，agent-service 侧）+ `gnex-sandbox-runtime` sandbox/config（SandboxRuntimeProperties / OpenSandboxProperties）+ preflight（SandboxPoolWatermarkCheck）+ scheduler/gate（GateChain / QuotaGate / NetworkPolicyGate / TypeMatchGate / CrossTenantGuard）——门禁逻辑自 sandbox-runtime 上移至 agent-service 管线 guard（D-P0-01） |
| 技术选型 | 🅶 GateChain/SandboxEnforcer 位置改造为 agent-service 管线 guard 插件 |
| 改造要点 | ① 权限档位（read-only/workspace-write/full-access）文件写效果移植为策略对象 ② 升级 = `sandbox_permissions` + approval 人审批——经 `ConfirmationGateway`（gnex-contracts，REST/Bus 异步审批，D-P1-25），缺席/无法回答 = deny fail-closed（CW-1.3）③ GateChain 门禁序保留（后层只加严，CW-1 单调）④ 无可用 backend → SANDBOX_UNAVAILABLE（禁止静默放行）⑤ 门禁拒绝旁路 audit_log ⑥ 批准后经 SandboxPort.ensure 请求执行（裁决/执行分进程） |

## §3 插件接口（契约）
### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `sandboxPolicy` | `com.gnex.plugin.sandbox.policy.SandboxPolicyService` | 档位裁决/confine/升级请求（🔒 受保护服务；agent-service 进程内） |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`settings`、`logger`、`sandbox`（SandboxPort client，批准后经 ensure 请求 sandbox-runtime）
- 审批通道（D-P1-25）：升级人审批**经 `ConfirmationGateway`（gnex-contracts，REST/Bus）异步审批**——审批队列/回放语义由 approval 插件在 agent-service 提供，回调经网关；~~跨进程 Ctx 注入 `approval`~~ 已删除（sandbox-runtime 侧 Ctx 无法解析 agent-service 宿主服务，D-P1-34）
- 可选：`invariants`（运行时不变量注册）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `sandbox/mode` | emit | 档位切换/降级（{mode, scope, reason}） |

> **修订（D-P0-01）**：原 `tools/pre-execute` listen（跨进程 waterfall）改为——本插件**作为管线 guard 注册**（guard 序中的 sandbox-policy/SandboxEnforcer 位，见 05 §4），在 agent-service 进程内同步裁决：档位/配额/网络/跨租户。非 listen 跨进程事件。

## §4 配置与装配
```yaml
# service-assembly.yml（agent-service）——宿主修订（D-P0-01）
plugins:
  - id: gnex3-cap-sandbox-policy
    load: mandatory
    protection: platform    # 🔒 禁租户/Agent 装卸
sandbox:
  policy:
    default-mode: read-only        # 默认档位
    upgrade-approval: required     # 升级必须人审批（经 ConfirmationGateway）
    gates: [type-match, quota, network-policy, cross-tenant]  # GateChain 序
  ensure-endpoint: http://sandbox-runtime/internal/sandbox/ensure  # SandboxPort.ensure
```

## §5 边界（禁止 ❌ / 必须 ✅）
- ✅ 权限档位（read-only/workspace-write/full-access）文件写效果：档位只约束文件写，read-only 拒绝一切写
- ✅ 升级走 `sandbox_permissions` + 人审批（经 `ConfirmationGateway` REST/Bus 异步审批；缺席/无法回答 = deny，fail-closed）
- ✅ 必须保留 GateChain 门禁序（TypeMatch→Quota→NetworkPolicy→CrossTenantGuard），后层只加严（CW-1 单调）
- ✅ 门禁拒绝必须旁路审计（audit_log），档位切换 emit `sandbox/mode`
- ✅ 裁决必须在 agent-service 进程内完成（管线 guard），批准后经 `SandboxPort.ensure` 请求 sandbox-runtime 执行（裁决/执行分离，D-P0-01）
- ❌ 无可用 backend 禁止静默放行（必须抛 SANDBOX_UNAVAILABLE）
- ❌ 策略插件禁租户/Agent 装卸（🔒 platform 持有）；禁「Agent 自降档」入口
- ❌ 不得对跨租户请求放行（CrossTenantGuard；串租户 = P0）
- ❌ 禁止跨进程 Ctx 注入获取审批/执行服务（跨服务一律 Port/REST/Bus，D-P1-34）

## §6 验收
| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 档位文件写效果 | `SandboxPolicyTest`：read-only 写被拒 / workspace-write 限工作区 / full-access 全放 |
| V2 | 升级审批（ConfirmationGateway） | `SandboxUpgradeTest`：升级经 ConfirmationGateway 入队 → 审批通过生效；网关缺席/无响应 = deny |
| V3 | 无 backend | `SandboxUnavailableTest`：无可用 backend 抛 SANDBOX_UNAVAILABLE，不静默放行 |
| V4 | GateChain 单调 | `GateChainTest`：Quota 拒绝后 NetworkPolicy 不二次放行 |
| V5 | 跨租户守卫 | `CrossTenantTest`：跨租户请求拒绝并审计 |
| V6 | 审计旁路 + 裁决/执行分离 | `PolicyAuditTest`：门禁拒绝发 audit_log；guard 拒绝时 SandboxPort.ensure 不被调用 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=SandboxPolicyTest,SandboxUpgradeTest,SandboxUnavailableTest,GateChainTest,CrossTenantTest,PolicyAuditTest"
```

## §7 影响域
- 作用范围：**服务进程级（agent-service，管线 guard）**；裁决结果经 SandboxPort.ensure 驱动 sandbox-runtime 执行平面。
- 故障影响半径：策略故障 = 执行准入失效（fail-closed 拒绝，不放行）；🔒 平台级升级。
- 升级影响：策略调整 = 无感热生效；档位语义变更 = 全执行回归（V1-V6 为发布门）。

## §8 后续（未纳入首版）
- 策略版本化与灰度（分租户/分档位策略，对齐 E25 沙箱资源管理面）
- 网络策略细化（出网白名单/目标域策略）

---

*GNEX3.0 沙箱策略 — agent-service 管线 guard（dsh 档位语义 + gnex2.0 GateChain/SandboxEnforcer 融合；审批经 ConfirmationGateway；🔒 protected）SDD。*
