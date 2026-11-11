# v3-spec-sandbox-seam

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.9
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-sandbox
> **分层**：L3 能力层（3.9 沙箱与执行）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：契约库 libs/gnex-sandbox（被 sandbox-runtime / agent-service 引用；无独立宿主进程）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P0-01

---

## §1 业务目标
沙箱 seam 是 GNEX3.0 的「执行世界**契约库**」（Definition）：把 dsh 已验证的沙箱能力缝三角色（Service Definition / Service Provider / Consumer）语义移植为 Java 接口，定义统一的执行世界访问契约——文件系统（fs）、命令执行（shell）与沙箱执行（sandbox）。对齐 dsh 语义：Definition 拥有 ctx 键与词汇（sandbox/fs/shell 服务 + sandbox/* 词表），Provider 是具体后端（归 sandbox-backends），Consumer 是模型可见的工具 schema；换后端不改工具名（模型仍见 read/write/bash，变的是执行世界）。继承 gnex2.0 gnex-sandbox-api 的 SandboxPort 契约（execute/isAlive，Profile LOCAL/REMOTE）与 sandbox-runtime 生命周期状态机。

**裁决与执行分离（D-P0-01）**：本插件**只定义契约，不参与工具管线**——沙箱裁决在 **agent-service** 工具管线 guard（SandboxEnforcer 位置，见 sandbox-policy spec）进程内完成；批准后经 `SandboxPort.ensure`（既有 REST `/internal/sandbox/ensure`）请求 sandbox-runtime 执行。跨进程参与 `tools/pre-execute` waterfall 同步语义不可行，已删除该 listen 声明。

**非目标**：seam 不做权限裁决（归 sandbox-policy，agent-service 管线 guard）；不做池/集群（归 sandbox-pool）；不做后端实现（归 sandbox-backends）；不做远程执行代理（跨服务走既有 REST/Bus，经 gnex-contracts 端口接口，禁止 Ctx 跨进程注入）。

## §2 映射关系
| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-sandbox`（Definition：ctx.sandbox 接口 + sandbox/* 词表）+ `dsh-fs`（fs/* 词表）+ `dsh-shell`（命令执行） |
| 对标 GNEX2.0 模块 | `gnex-sandbox-api`（SandboxPort / EnsureReadyPort / SandboxLifecycleEvent / IsolationBackend）——本插件即该契约库的插件化形态，宿主 libs/gnex-sandbox |
| 技术选型 | 🅶 gnex2.0 已有抽象改造；seam 语义对齐 dsh（三角色分离） |
| 改造要点 | ① 以 Java 接口实现能力缝三角色：`SandboxService`（Definition）+ 后端注册表 SPI（Provider 侧，实现在 backends）+ 工具 schema（Consumer）② 复用 SandboxPort 契约（execute/isAlive/ensure）与 IsolationBackend 枚举 ③ fs/shell 词表（fs/*、shell/*）走事件总线（对齐 dsh fs 词表）④ backend 注册/注销 emit `sandbox/mode`，UI/审计可观测 ⑤ 契约库被 sandbox-runtime（执行侧）与 agent-service（guard 经 SandboxPort.ensure 调用）双向引用 |

## §3 插件接口（契约）
### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `sandbox` | `com.gnex.plugin.sandbox.SandboxService` | 执行世界抽象契约：execute/isAlive/confine + backend 注册表 SPI |
| `fs` | `com.gnex.plugin.sandbox.FsSeam` | 文件系统 seam 契约（fs/* 词表，对齐 dsh-fs） |
| `shell` | `com.gnex.plugin.sandbox.ShellSeam` | 命令执行 seam 契约（对齐 dsh-shell） |

> 契约由引用方在各自进程内 provide 实现：sandbox-runtime 侧由 sandbox-backends 提供后端实现；agent-service 侧经 SandboxPort REST client 消费（不含裁决——裁决归 sandbox-policy guard）。

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`settings`、`logger`
- 可选：`scope`、`invariants`（运行时不变量注册）
- ~~`sandboxPolicy`（裁决委托）~~：**已删除（D-P0-01）**——裁决不在本进程，跨服务走 Port/REST（`SandboxPort.ensure`），禁止 Ctx 跨进程注入。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `sandbox/mode` | emit | {mode, backend, scope}：执行世界/档位切换 |
| `sandbox/ready` · `sandbox/destroyed` · `sandbox/error` | emit | 生命周期（对齐 gnex2.0 sandbox_events） |

> **修订（D-P0-01）**：删除原 `tools/pre-execute` listen 声明——该事件在 agent-service 进程内总线发出，跨进程同步裁决语义无定义。沙箱裁决入口 = agent-service 管线 guard（见 sandbox-policy spec §3.3）；执行经 `SandboxPort.ensure` 请求 sandbox-runtime。

## §4 配置与装配
```yaml
# 契约库（libs/gnex-sandbox）：无独立装配，随引用方编译期依赖。
# sandbox-runtime 侧装配（执行平面）：
# service-assembly.yml（sandbox-runtime）
plugins:
  - id: gnex3-cap-sandbox            # 契约 Definition（提供 sandbox/fs/shell 接口与注册表）
    load: mandatory                  # 执行世界抽象，后端插件前置
  - id: gnex3-cap-sandbox-backends
    load: optional                   # 后端可装卸，缺省抛 SANDBOX_UNAVAILABLE
# agent-service 侧：裁决由 gnex3-cap-sandbox-policy（管线 guard）承担，
# 经 SandboxPort REST client（/internal/sandbox/ensure）调用 sandbox-runtime，不装载后端。
```

## §5 边界（禁止 ❌ / 必须 ✅）
- ✅ 必须实现能力缝三角色分离：Consumer 只依赖 `sandbox` 服务与工具 schema，禁止 import 具体 backend
- ✅ 必须复用 gnex-sandbox-api 契约（SandboxPort.execute/isAlive/ensure、IsolationBackend）与生命周期事件
- ✅ 换 backend 不改工具名与词表（read/write/bash 语义不变，变的是执行世界）
- ✅ 档位/backend 切换必须 emit `sandbox/mode`，消费方（UI/审计/观测）可观测
- ✅ 跨服务交互只经 Port/REST（`SandboxPort.ensure`），裁决权在 agent-service 管线 guard（D-P0-01/D-P1-34）
- ❌ seam 不得自行做权限/配额裁决（裁决归 agent-service 管线 guard，禁止旁路）；**不得 listen 跨进程管线事件**
- ❌ 不得把容器/远程执行注册为本地 sandbox backend 冒充强隔离（对齐 dsh §9：换世界 = 换 fs+shell Provider）
- ❌ seam 不得提供「Agent 自改执行世界」入口（企业红灯，装卸权仅管理员）

## §6 验收
| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 三角色分离：Consumer 经 ctx.sandbox 调用 | `SandboxSeamTest`：换 backend 后工具调用零改动 |
| V2 | fs/shell 词表经事件总线派发 | `FsShellVocabularyTest`：write/bash 均派发 fs/*、shell/* 事件 |
| V3 | 裁决/执行分离：guard 批准后经 SandboxPort.ensure 执行 | `SeamEnsurePathTest`：guard 拒绝时 SandboxPort.ensure 不被调用；批准时 ensure 恰被调用一次（REST mock） |
| V4 | 生命周期事件 | `SeamLifecycleTest`：ready/destroyed/error 各 emit 一次 |
| V5 | 契约兼容 | `SandboxPortCompatibilityTest`：SandboxPort 实现与 gnex2.0 client 互通 |

### 运行命令

```bash
mvn -pl services/gnex-sandbox-runtime test "-Dtest=SandboxSeamTest,FsShellVocabularyTest,SeamEnsurePathTest,SeamLifecycleTest,SandboxPortCompatibilityTest"
```

## §7 影响域
- 作用范围：**契约库（libs/gnex-sandbox）+ 执行进程级（sandbox-runtime）**；被 shell/fs/code 工具与 agent-service（裁决 guard 经 SandboxPort.ensure）消费。
- 故障影响半径：执行失败 = 单次工具失败（不影响主进程，05 §3 L3.9）；契约变更 = 编译期影响全部引用方。
- 升级影响：词表/契约变更 = 全部执行工具回归；backend 增删 = 无感。

## §8 后续（未纳入首版）
- 执行世界元数据（world manifest 版本化，供恢复/审计重建）
- 远程执行 seam（对齐 dsh E2B 方向，作为 fs/shell Provider 而非 sandbox backend）

---

*GNEX3.0 沙箱 seam — 执行世界契约库（dsh 三角色语义 + gnex-sandbox-api 契约；裁决在 agent-service 管线、执行经 SandboxPort.ensure，D-P0-01）SDD。*
