# v3-spec-plugin-runtime-fiber

> **来源**：../../04-gnex3.0-plugin-catalog.md §L1 内核层
> **负责人**：<owner>
> **插件 ID**：gnex3-l1-fiber
> **分层**：L1 内核层
> **受保护级别**：normal（默认；装配声明 mandatory 时实际不可卸载）
> **宿主服务**：全部 gnex 服务
> **实现状态**：规划中

---

## §1 业务目标

实现插件生命周期管理（cordis Fiber 的 Java 移植）：装载/卸载状态机、effect 栈（LIFO 回卷）、依赖等待与级联卸载；与 Spring Bean 生命周期桥接（容器启动时驱动插件装载、关闭时优雅卸载）。生命周期状态可查询（供 inventory/诊断），状态迁移发 `plugin/*` 事件。对齐"插件随 fiber 卸载自动注销服务"的 dsh 语义。

**非目标**：不做依赖拓扑计算（loader 负责排序，本插件执行生命周期）；不做 jar 热装。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | cordis Fiber（生命周期/effect 回卷） |
| 对标 GNEX2.0 模块 | Spring Bean 生命周期（桥接对象） |
| 技术选型 | 🅳 Java 移植 dsh 生命周期语义 |
| 改造要点 | ① Fiber 状态机（pending/starting/started/stopping/stopped/error）② effect 栈 LIFO 回卷（服务/监听/工具注册随卸载撤销）③ 依赖等待与级联卸载（被依赖方卸载前置拒绝）④ 状态迁移发 plugin/started · plugin/stopped ⑤ Spring 桥（ApplicationRunner/DisposableBean 驱动） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `fiber` | `com.gnex.plugin.runtime.Fiber` | 生命周期控制：start/stop/status/effect |

### 3.2 注入服务（inject）

- `ctx`（必选）：注册 fiber 服务并执行插件生命周期
- `events`（必选）：发布生命周期事件

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `plugin/started` | emit | {pluginId, services[]} |
| `plugin/stopped` | emit | {pluginId, reason} |
| `plugin/error` | listen | {pluginId, error} → 状态落 error 并触发回滚 |

## §4 配置与装配

```yaml
# service-assembly.yml 片段
plugins:
  - id: gnex3-l1-fiber
    load: mandatory
gnex.fiber.graceful-stop-timeout: 10s   # 优雅卸载超时
gnex.fiber.error-policy: rollback       # 启动失败回滚该插件已注册副作用
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 插件启动失败必须进入 error 态并回卷其已注册 effect（不留半启动残留）
- ✅ 卸载必须按 effect 栈 LIFO 回卷，超时后强制回卷并记录
- ✅ 生命周期状态必须可查询（fiber.status(pluginId) 供 inventory/诊断）
- ❌ 不得跳过依赖插件直接卸载被依赖方（级联卸载须先卸载依赖方）
- ❌ 不得让 stop 回调的未捕获异常破坏回卷顺序（逐项捕获隔离）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 状态机迁移 | `FiberStateTest`：pending→starting→started→stopping→stopped |
| V2 | effect LIFO 回卷 | `FiberEffectTest`：后注册的 effect 先回卷 |
| V3 | 级联卸载 | `FiberCascadeTest`：卸载被依赖方前先卸载依赖方 |
| V4 | 优雅超时 | `FiberTimeoutTest`：stop 超时后强制回卷并记录 |
| V5 | error 回滚 | `FiberErrorTest`：start 抛错进入 error 态且副作用回卷 |
| V6 | 事件顺序 | `FiberEventTest`：started/stopped 载荷与顺序正确 |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=Fiber*Test"
```

## §7 影响域

- 作用范围：**服务进程级**——全部插件的生命周期执行器。
- 被谁消费：loader（装载驱动）；inventory（状态查询）；可观测（plugin/* 事件）。
- 故障影响半径：生命周期缺陷 = 插件装卸错乱/服务启动失败；LIFO 回卷与超时兜底。
- 升级影响：生命周期语义升级 = 全服务回归（版本化，向后兼容）。

## §8 后续（未纳入首版）

- 插件热装生命周期（v3.1：start/stop 在运行期执行，配合 PF4J）
- 生命周期事务日志（状态迁移时间线）
- 卸载前一致性检查（invariants 联动：回卷完整性断言）

---

*GNEX3.0 插件生命周期 — cordis Fiber Java 移植 SDD。*
