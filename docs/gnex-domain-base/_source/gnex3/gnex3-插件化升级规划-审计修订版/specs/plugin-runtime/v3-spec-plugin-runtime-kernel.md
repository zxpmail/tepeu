# v3-spec-plugin-runtime-kernel

> **来源**：../../04-gnex3.0-plugin-catalog.md §L0
> **负责人**：<owner>
> **插件 ID**：gnex3-l0-kernel
> **分层**：L0 组合与装载层
> **受保护级别**：protected（platform 级，平台持有；禁租户/Agent 装卸）——与 04 插件目录 🔒 标注及三级词汇（normal/admin/protected）一致（D-P1-17 复核）
> **宿主服务**：全部 gnex 服务（agent-service / registry-service / sandbox-runtime / state-plane / observability / ops / bus / auth / platform）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 修订依据：审计修订版/02-设计决策记录.md D-P0-03、D-P1-04ab/12/13/17/18、D-P2-01a/b/c

---

## §1 业务目标

GNEX3.0「服务内部由插件组合」的运行时基座：在 Java 21 上以惯用法实现 dsh/Cordis 已验证的插件组合语义——Ctx 服务容器、Service 注册、Plugin 入口、Fiber 生命周期（装载/卸载/effect 回卷）、事件总线（waterfall/emit + scope 过滤）、Scope/Realm 隔离。目标：让 gnex 每个服务成为「内核 + 插件集」，能力边界可枚举、可装卸、可独立演进；插件接口关系与 dsh 对齐（ctx 键、事件命名）。

**非目标**：不做 jar 热加载（v3.1）；不做跨服务 RPC（走既有 REST/Bus）；不替代 Spring 的服务装配（Spring 管服务进程，内核管服务内能力组合）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `cordis`（Context/Service/Plugin/Fiber/Registry/Events）+ `dsh-scope`（作用域） |
| 对标 GNEX2.0 模块 | 无对应（新增）；Spring DI（ApplicationContext）为被桥接对象 |
| 技术选型 | 🅳 Java 移植 dsh/cordis 语义（TypeScript → Java 惯用法） |
| 改造要点 | ① 核心抽象：`Ctx`（get/provide/extend/isolate/effect/on/emit/waterfall）② `Service` 基类（注册/注销随 fiber）③ `Plugin` 接口（inject/provide/start/stop）④ `Fiber` 生命周期（依赖等待、卸载回卷）⑤ `Events`（waterfall 可拦截可否决 + emit 只通知 + scope 过滤）⑥ `Scope`（keyed by 租户/项目/会话）。参考 dsh `vendor/cordis` 语义，不复制其代理实现（Java 用接口 + 注册表） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

**收敛（D-P1-18）**：kernel 仅提供 `ctx`（root 容器）与 `pluginRegistry` 两个服务；`events`/`fiber`/`scope` 的语义服务由 L1 各插件（gnex3-l1-events / gnex3-l1-fiber / gnex3-l1-scope）提供，消除双写（05 §1 的 ctx 行已注"root 由 kernel 创建，语义实现由 l1-ctx"）。

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `ctx` | `com.gnex.plugin.runtime.Ctx` | 服务容器 root（所有插件的第一注入；root 由 kernel 创建，完整容器语义实现由 gnex3-l1-ctx 提供） |
| `pluginRegistry` | `com.gnex.plugin.runtime.PluginRegistry` | 插件清单/状态查询 |

**provide 重载（D-P2-01b）**：`provide(name, svc, availabilityCheck)` 重载——可用性谓词（插件声明其服务的就绪条件，映射 gnex `@ConditionalOnMissingBean` 兜底语义；谓词不满足时 inject 方挂起等待，而非立即失败）。

**isolate label-join（D-P2-01a）**：`isolate(name, label)` 支持同名 label 合并作用域——相同 label 的多次 isolate 返回同一派生作用域（供多装配共享 realm，如同一租户/项目下多个插件组共享隔离域）；无 label 时每次派生独立作用域。

### 3.2 注入服务（inject）

无（内核为最底层；Spring 桥注入为可选 `springContext`）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `plugin/installed` | emit | {pluginId, version} |
| `plugin/started` | emit | {pluginId, services[]} |
| `plugin/stopped` | emit | {pluginId, reason} |
| `plugin/uninstalled` | emit | {pluginId} |
| `plugin/error` | emit | {pluginId, error} |

**Spring 桥时序与规范（D-P1-04b / D-P1-13）**：
- `@PluginService` 注入的插件服务**全部 lazy + `ObjectProvider`** 注入（避免业务 Bean 启动期硬依赖插件装载完成）；
- `PluginRuntime` 在 `ApplicationContextInitializer` 阶段启动（先于业务 Bean 依赖建立）；
- 卸载时桥代理 **invalidate**：后续经桥调用该服务抛出明确异常（`PluginServiceUnavailableException`），不静默返回空；
- 插件服务接口为**纯接口**（禁止 Spring 注解污染契约，保证可移植与跨运行时测试）；
- **事务只在 Spring Provider 侧开启**：插件侧不感知事务边界，事务传播止于桥代理之外。

### 3.4 线程模型（D-P1-12）

1. **ctx 服务注册表**：`ConcurrentHashMap`（名称 → Provider）+ 服务实例 **volatile 发布**（保证装载线程写入对调度线程的可见性，避免半初始化服务被读）。
2. **事件分发**：
   - `emit` = **异步派发**：虚拟线程池，**每监听器独立执行 + 异常隔离**（单个监听器抛错/阻塞不影响主流程与其余监听器）；
   - `waterfall` = **同步**：在调度线程上按序执行整条链，配 `waterfall-timeout`（默认 **5s**）熔断——超时视为 deny（fail-closed），保证管线不被慢监听器挂死。
3. **fiber 生命周期**：装载/卸载互斥锁（同一 fiber 的 install/unload 串行化）+ 状态机（`LOADING → RUNNING → STOPPING → STOPPED`，非法状态迁移拒绝并告警）。
4. **装卸竞态**：卸载采用**引用计数 + drain**——进入 STOPPING 后拒绝新调用（在飞调用计数递增/递减），等待在飞调用归零（drain）后才执行 effect 回卷，杜绝"回卷后仍有调用打到已注销服务"。

## §4 配置与装配

```yaml
# service-assembly.yml（每服务一份）
runtime:
  kernel: cordis-jvm
plugins:
  - id: gnex3-l1-events
    load: mandatory
  - id: gnex3-l1-scope
    load: mandatory
    protection: platform
  # 能力插件按服务声明
runtime:
  waterfall-timeout: 5s        # waterfall 链同步超时熔断（超时=deny，fail-closed；D-P1-12）
  trace:
    mdc-propagation: true      # MDC/trace 透传（D-P1-13）
```

**MDC/trace 透传（D-P1-13）**：插件调用链经 `Ctx` 包装的 `TraceContext`——进出插件边界时拷贝/恢复 SLF4J MDC（traceId/tenantId/sessionId 等键），保证插件内日志与主链路同 trace；emit 异步派发时 MDC 随任务快照携带。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 内核必须提供 effect 可逆回卷（卸载插件时撤销其注册的服务/监听器/工具）
- ✅ 事件必须区分 waterfall（可拦截可否决）与 emit（只通知），scope 过滤
- ✅ 插件装载必须等待 inject 依赖就绪；依赖卸载则级联卸载
- ✅ 卸载必须等待在飞调用结束（引用计数 + drain）后才回卷（§3.4）
- ✅ 插件服务解析仅限宿主服务内 Ctx；跨服务一律 Port/REST/Bus（gnex-contracts 端口接口），禁止 Ctx 跨进程注入（D-P1-34 判定表：同服务→Ctx；跨服务→Port 或 REST/Bus）
- ❌ 内核不得直接依赖任何业务插件（反向依赖禁止，对齐 STRUCTURE-RULES R1/R2 精神）
- ❌ 内核不得提供"Agent 自改内核"入口（企业红灯）；装卸权仅平台管理员
- ❌ 内核不得替代 Spring 服务装配/事务/ORM
- ❌ 插件不得直接依赖 Spring Bean（**插件↔Spring 反向边界**，D-P2-01c）：插件经 contracts 端口访问数据面，bridgeBean 仅限基础设施类（DataSource/TransactionManager 等）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 插件装载：inject 依赖就绪后 start | `KernelLoadTest`：依赖乱序声明仍按依赖序启动 |
| V2 | 插件卸载：effect 回卷撤销服务/监听 | `KernelUnloadTest`：卸载后 ctx.get 返回空、监听器不再触发 |
| V3 | waterfall 拦截：监听器拒绝则下游不执行 | `EventWaterfallTest`：deny 后 execute 不执行 |
| V4 | emit 只通知：监听器异常不影响主流程 | `EventEmitTest` |
| V5 | scope 隔离：realm 内注册不污染父 scope | `ScopeIsolateTest` |
| V6 | 依赖缺失：inject 服务未注册时插件挂起不崩溃 | `KernelDependencyTest` |
| V7 | Spring 桥：插件服务可被 @PluginService 注入（lazy/ObjectProvider；卸载后桥代理 invalidate 抛明确异常；并发装载；循环依赖；事务边界只在 Provider 侧） | `SpringBridgeTest`（D-P1-04b 扩展） |
| V8 | ctx 注册表并发：多线程 provide/get 竞态下服务可见性与一致性（volatile 发布） | `KernelRegistryConcurrencyTest` |
| V9 | 事件分发线程语义：emit 异步（每监听器独立虚拟线程+异常隔离）、waterfall 同步 + 5s 超时熔断（超时=deny） | `EventDispatchModelTest` |
| V10 | fiber 装卸互斥与竞态：状态机迁移合法；卸载等待在飞调用（引用计数 + drain）归零后才回卷 | `KernelLifecycleRaceTest` |
| V11 | dsh↔cordis-jvm 双端语义一致性：同场景在 dsh(TS) 与 cordis-jvm 各跑断言同结果（装载序/卸载回卷/waterfall deny/scope 隔离） | `KernelDshAlignmentTest`（D-P1-04a） |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=Kernel*Test,Event*Test,Scope*Test,SpringBridge*Test,Ctx*Test"
# 目标模块（待 S 阶段新建，D-P1-02）；通配覆盖 V1-V11 全部测试类（D-P0-03）
```

## §7 影响域

- 作用范围：**服务进程级**——全部 gnex 服务的组合基座。
- 被谁消费：所有 L1/L3/L4 插件；经 Spring 桥被业务代码间接消费。
- 故障影响半径：内核缺陷 = 全服务能力不可用 → 验收 V1-V11 为内核发布门。
- 升级影响：内核升级 = 全服务回归（版本化内核，向后兼容承诺）。

## §8 后续（未纳入首版）

- v3.1 jar 热加载（PF4J 类加载器后端 + 版本管理 + 热装验收门）
- 跨服务插件注册（插件清单汇聚到 registry-service）
- 插件市场/供应链检查（npm/pypi OSV 类比，对齐 Goose P1）

---

*GNEX3.0 插件内核 — cordis-jvm 最小内核 SDD。*
