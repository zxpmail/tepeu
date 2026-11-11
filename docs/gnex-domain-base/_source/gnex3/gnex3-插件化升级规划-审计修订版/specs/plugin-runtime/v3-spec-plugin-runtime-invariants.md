# v3-spec-plugin-runtime-invariants

> **来源**：../../04-gnex3.0-plugin-catalog.md §L1 内核层
> **负责人**：<owner>
> **插件 ID**：gnex3-l1-invariants
> **分层**：L1 内核层
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：全部 gnex 服务
> **实现状态**：规划中

---

## §1 业务目标

运行时不变量注册表（dsh-invariants 的 Java 移植，gnex2.0 无对应、新增）：让关键不变量可枚举、可检查、可处置——插件/核心循环注册断言（如 effect 回卷完整性、事件顺序、服务存在性、scope 归属），在关键边界触发检查，违反时按 fail-policy（log/warn/stop）处置并记录。与 logger/observability 联动，把"设计约束"变成可执行检查（关键规则用可执行检查兜底）。

**非目标**：不做编译期架构规则（STRUCTURE-RULES/ArchUnit 承接）；不做指标采集（observability 承接）；不做热路径逐调用检查（仅关键边界触发）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh-invariants（运行时不变式） |
| 对标 GNEX2.0 模块 | 无对应（新增） |
| 技术选型 | 🅳 Java 移植 dsh 不变式语义 |
| 改造要点 | ① invariants 服务（register/check 注册表，检查可枚举）② 触发点绑定（插件生命周期事件/轮次边界）③ 违反处置策略（log/warn/stop，默认 warn）④ 与 logger/observability 关联（违反记录 + 指标）⑤ 核心循环不变量（effect 回卷完整性、事件顺序）由 kernel/fiber 插件注册 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `invariants` | `com.gnex.plugin.runtime.Invariants` | register(check)/run(failPolicy)/list() |

### 3.2 注入服务（inject）

- `ctx`（必选）：注册 invariants 服务
- `events`（必选）：监听触发点事件
- `logger`（可选）：违反记录

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `plugin/uninstalled` | listen | {pluginId} → 触发回卷完整性检查 |
| `turn/end` | listen | {sessionId} → 触发轮次边界检查 |

## §4 配置与装配

```yaml
# 插件段配置（settings 命名空间）
gnex3-l1-invariants:
  fail-policy: warn            # log | warn | stop（默认 warn，stop 需平台确认）
  checks:                      # 检查清单（插件段声明，可枚举）
    - id: effect-rollback-complete
      trigger: plugin/uninstalled
    - id: event-order-core
      trigger: turn/end
gnex.invariants.hot-path-exempt: true   # 热路径豁免（关键边界才触发）
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 不变量检查必须注册到统一注册表（可枚举、可审计）
- ✅ 违反必须按 fail-policy 处置并记录（warn 入日志、stop 中断对应操作）
- ✅ 触发必须绑定关键边界（生命周期事件/轮次边界），热路径豁免
- ❌ 不得在热路径逐调用执行重检查（性能防护）
- ❌ 检查不得抛出未捕获异常中断主流程（fail-policy 之外一律捕获记录）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 注册枚举 | `InvariantsRegisterTest`：注册后 list() 可枚举 |
| V2 | 边界触发 | `InvariantsTriggerTest`：plugin/uninstalled 触发回卷完整性检查 |
| V3 | policy=warn | `InvariantsWarnTest`：违反仅记录不中断 |
| V4 | policy=stop | `InvariantsStopTest`：违反中断对应操作并记录 |
| V5 | 热路径豁免 | `InvariantsHotPathTest`：热路径不触发检查（性能基准） |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=Invariants*Test"
```

## §7 影响域

- 作用范围：**服务进程级（检查面）**——核心循环与插件生命周期的约束守护。
- 被谁消费：kernel/fiber（回卷完整性）；agentLoop（轮次边界）；运维（违规报告）。
- 故障影响半径：检查缺陷 = 约束失守（回归靠 V1-V5 兜底）；误报 stop = 操作中断（policy 默认 warn 缓解）。
- 升级影响：新增不变量 = 无感（注册制）；触发点变更 = 相关循环回归。

## §8 后续（未纳入首版）

- 不变量检查结果上报 observability（指标/告警）
- 架构约束的运行时化（STRUCTURE-RULES 检查下沉为不变量）
- 违规趋势报表（运维视图）

---

*GNEX3.0 运行时不变量注册表 — dsh-invariants Java 移植 SDD。*
