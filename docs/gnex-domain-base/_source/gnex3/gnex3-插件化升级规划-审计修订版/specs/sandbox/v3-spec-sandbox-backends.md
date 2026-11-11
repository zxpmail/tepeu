# v3-spec-sandbox-backends

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.9
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-sandbox-backends
> **分层**：L3 能力层（3.9 沙箱与执行）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：sandbox-runtime（gnex-sandbox-runtime）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-26

---

## §1 业务目标
沙箱提供方插件：把 gnex2.0 sandbox-runtime 的 provider/* 改造为可装卸的隔离后端集合——OpenSandbox（容器级强隔离，v3.0 首版**真实实现**）、Local（本机进程，dsh 档位语义），统一实现 `SandboxProviderPort` SPI 并向 seam 注册。保留 gnex2.0 的路由（RoutingSandboxProvider / SandboxProviderFactory）、确认式销毁与补偿（ConfirmedSandboxDestroyer / OsbCreateCompensation）、批量创建（OpenSandboxBatchCreateAdapter）与 OSB 状态轮询桥（OpenSandboxStatusBridge）。

**实现状态口径（D-P1-26）**：gnex2.0 现状中 **FirecrackerProviderStub 为 Stub（[源码实证]）**，非真实隔离实现；**v3.0 首版强隔离档位 = OpenSandbox 容器级**（真实实现），Firecracker microVM 列 §8 后续。隔离后端按 sandboxPolicy 档位与 `IsolationBackend` 决定；Local 后端执行 dsh 权限档位的文件写效果（read-only/workspace-write/full-access），对齐 dsh-sandbox-local 与 dsh-sandbox-windows-acl 的 partial 诚实标注。

**非目标**：不做裁决（归 policy，agent-service 管线 guard）；不做池/集群（归 pool）；不新增隔离技术（v3.0 只保留 opensandbox/local 两后端 + firecracker stub 隔离声明，见 §8）。

## §2 映射关系
| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | 无直接对应（dsh 用 bwrap/landlock/sandbox-exec/ACL）；语义参考 `dsh-sandbox-local`（本地后端）+ `dsh-sandbox-windows-acl`（partial 隔离诚实标注） |
| 对标 GNEX2.0 模块 | `gnex-sandbox-runtime` provider/*（SandboxProviderPort / RoutingSandboxProvider / OpenSandboxDockerProvider / **FirecrackerProviderStub [源码实证：Stub，未实现]** / OpenSandboxK8sProviderStub [Stub]）+ `gnex-sandbox-api`（IsolationBackend / SandboxErrorCodes） |
| 技术选型 | 🅶 provider/* 保留改造为可装卸 backend 插件；**强隔离首版锚定 OpenSandbox 容器级（D-P1-26）** |
| 改造要点 | ① 后端实现 SandboxProviderPort SPI（create/destroy/getStatus/pause/resume/fork/batchCreate/renewExpiration）② 装配清单装卸后端（05 §3 L3.9：提供方增删=无感）③ 确认式销毁 + 创建补偿（失败不留幽灵）④ OSB 状态轮询桥保留（Terminal/404 → sandbox.destroyed）⑤ local 后端按档位执行文件写效果（执行 policy/guard 下发的 confine 规则，非自裁决）⑥ **stub 不参与 isolation=firecracker 路由**（firecracker 档位 v3.0 不可路由，见 §4/§6 V1） |

## §3 插件接口（契约）
### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| （backend 注册） | `SandboxProviderPort` 实现 | opensandbox（真实实现）/ local（真实实现）注册进 seam 后端注册表；firecracker 仅 Stub 声明，不注册路由 |

### 3.2 注入服务（inject）

- 必选：`sandbox`（seam，注册后端用）、`ctx`、`events`、`settings`、`logger`
- 可选：`scope`（租户隔离校验）；~~`sandboxPolicy`~~ 裁决在 agent-service 管线 guard，跨进程不注入（D-P0-01 连带；confine 规则经 ensure 请求下发）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `sandbox/mode` | listen | 档位/世界切换 → 按 IsolationBackend 选择后端（firecracker 除外，见 §4） |
| `sandbox/ready` · `sandbox/destroyed` · `sandbox/error` | emit | 后端生命周期（对齐 gnex2.0 sandbox_events） |

## §4 配置与装配
```yaml
# service-assembly.yml（sandbox-runtime）
plugins:
  - id: gnex3-cap-sandbox-backends
    load: optional            # 可装卸
sandbox:
  backends:
    default: opensandbox      # 路由默认后端
    isolation: opensandbox    # 强隔离档位后端（v3.0 首版 = OpenSandbox 容器级，D-P1-26）
    # isolation: firecracker  # ❌ v3.0 不可用：FirecrackerProviderStub 未实现（[源码实证]），见 §8
    local:
      workspace-root: /var/gnex/workspace
```

## §5 边界（禁止 ❌ / 必须 ✅）
- ✅ 必须实现 SandboxProviderPort 全 SPI（create/destroy/getStatus/pause/resume/fork/batchCreate/renewExpiration）——opensandbox/local 真实实现
- ✅ 必须按 IsolationBackend 路由（opensandbox/local），后端装卸走装配清单；**FirecrackerProviderStub 不得参与 isolation=firecracker 路由**（D-P1-26）
- ✅ v3.0 首版强隔离档位 = OpenSandbox 容器级（真实实现），spec/文档不得宣称 microVM 级强隔离
- ✅ local 后端必须执行 dsh 档位文件写效果（执行 policy 下发的 confine 规则：read-only/workspace-write/full-access；裁决归 agent-service 管线 guard）
- ✅ 销毁必须确认式 + 创建失败补偿（ConfirmedSandboxDestroyer / OsbCreateCompensation）
- ❌ backend 是执行者不是裁决者：不得绕过管线 guard 自行放行/降档
- ❌ 不得把容器/远程执行注册为本地 backend 冒充强隔离（dsh §9：有沙箱≠网络隔离）；**不得把 Stub 声明为可用隔离档位**
- ❌ backend 崩溃不得影响主进程（执行失败=单次失败）；不得静默吞错上报成功

## §6 验收
| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | SPI 契约 + stub 隔离 | `ProviderSpiTest`：opensandbox/local 各实现通过全 SPI 契约测试；**断言 FirecrackerProviderStub 不参与 isolation=firecracker 路由**（请求该档位 → 明确 unsupported/降级拒绝，非假通过）（D-P1-26） |
| V2 | 按 IsolationBackend 路由 | `ProviderRoutingTest`：档位→后端映射正确（强隔离→opensandbox） |
| V3 | 后端装卸 | `BackendLifecycleTest`：装卸后 sandbox/mode emit、工具调用零改动 |
| V4 | 确认式销毁 + 补偿 | `ConfirmedDestroyTest`：销毁失败触发补偿，无幽灵实例 |
| V5 | local 档位文件写效果 | `LocalBackendPolicyTest`：执行 policy/guard 下发的 confine 规则（read-only 写被拒 / workspace-write 限工作区；本测试只验执行，裁决语义归 policy spec V1） |
| V6 | OSB 桥 | `OsbBridgeTest`：Terminal/404 → sandbox.destroyed 并清理台账 |

### 运行命令

```bash
mvn -pl services/gnex-sandbox-runtime test "-Dtest=ProviderSpiTest,ProviderRoutingTest,BackendLifecycleTest,ConfirmedDestroyTest,LocalBackendPolicyTest,OsbBridgeTest"
```

## §7 影响域
- 作用范围：**服务进程级**（sandbox-runtime）；被 seam 与工具调用消费。
- 故障影响半径：单后端故障 = 该隔离档位不可用（不影响其它后端与主进程）。
- 升级影响：后端增删 = 无感（05 §3 L3.9）；后端实现升级 = 对应档位回归。

## §8 后续（未纳入首版）
- **Firecracker microVM 后端真实实现**（替换 FirecrackerProviderStub [源码实证]；工作量初估 10-15 人周：microVM 编排/镜像管理/生命周期与池联动，S 阶段实测重估）——落地后方可将 isolation 档位切回 firecracker
- 更多隔离后端（K8s sandbox / E2B 桥，对齐 dsh fs-e2b 方向）
- 后端健康画像注入调度器（心跳/启动超时/执行超时，对齐 E25 故障检测画像）

---

*GNEX3.0 沙箱提供方 — opensandbox/local 后端插件（Firecracker 现状 Stub [源码实证]、列 §8，D-P1-26）SDD。*
