# v3-spec-plugin-runtime-ctx

> **来源**：../../04-gnex3.0-plugin-catalog.md §L1 内核层
> **负责人**：<owner>
> **插件 ID**：gnex3-l1-ctx
> **分层**：L1 内核层
> **受保护级别**：normal（默认；装配声明 mandatory 时实际不可卸载）
> **宿主服务**：全部 gnex 服务
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 修订依据：审计修订版/02-设计决策记录.md D-P1-18、D-P2-01a

---

## §1 业务目标

实现 Ctx 服务容器语义（dsh Context 的 Java 惯用移植）：服务解析（get）、注册（provide）、作用域派生（extend/isolate）、访问拦截（intercept）。Ctx 是所有插件的第一注入载体——插件经 ctx 获取/注册具名服务，接口关系与 dsh 对齐（ctx 键命名）。root ctx 由 kernel（gnex3-l0-kernel）创建，本插件提供完整容器语义实现并暴露为 `ctx` 服务。

**非目标**：不做服务实现（服务由各插件提供）；不做事件分发（events 插件）；不做生命周期执行（fiber 插件）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | cordis Context（代理化服务容器） |
| 对标 GNEX2.0 模块 | Spring DI（ApplicationContext）——被桥接对象 |
| 技术选型 | 🅳 Java 移植 dsh/cordis 语义（接口 + 注册表，不复制代理实现） |
| 改造要点 | ① `Ctx` 接口（get/provide/extend/isolate/intercept/effect）② 服务注册表（名称 → Provider，返回注销句柄随 fiber 注销）③ 作用域链解析（当前 → 父级）④ intercept 服务访问拦截（审计/权限切面）⑤ Spring 桥（@PluginService 注入插件服务） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

**服务归属说明（D-P1-18）**：`ctx` 服务容器 **root 由 kernel（gnex3-l0-kernel）创建**，本插件负责**完整容器语义的实现与暴露**（get/provide/extend/isolate/intercept/effect）——kernel 只提供 root 与 `pluginRegistry`，容器语义服务由本插件提供，消除双写（与 05 §1 ctx 行注一致）。

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `ctx` | `com.gnex.plugin.runtime.Ctx` | 服务容器（root 由 kernel 创建，语义实现归本插件；所有插件的第一注入） |

**isolate label-join（D-P2-01a）**：`isolate(name, label)` 支持同名 label 合并作用域——相同 label 的多次 isolate 返回**同一派生作用域**（供多装配共享 realm，如同一租户/项目下多个插件组共享隔离域）；无 label 时每次派生独立作用域。

### 3.2 注入服务（inject）

- kernel（gnex3-l0-kernel，必选）：`PluginRuntime` 创建 root ctx

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| （无自定义事件） | — | 生命周期事件由 fiber/kernel 发 `plugin/*` |

## §4 配置与装配

```yaml
# service-assembly.yml 片段
plugins:
  - id: gnex3-l1-ctx
    load: mandatory          # 容器为全部插件前置依赖
gnex.ctx.intercept-registry: kernel-ext   # intercept 扩展点由安全/审计插件注册
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 服务解析必须按"当前作用域 → 父作用域"链查找，未命中返回空而非抛错
- ✅ 服务注册必须返回注销句柄，并随插件 effect 回卷自动注销
- ✅ intercept 必须对服务访问生效（可审计/可拒绝）
- ✅ `isolate(name, label)` 同名 label 必须合并到同一派生作用域（label-join，多装配共享 realm）
- ❌ 不得在 isolate 作用域内反向污染父作用域注册
- ❌ 插件不得绕过 Ctx 直接持有 Spring Bean 相互引用（跨插件协作一律经契约接口）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | provide/get | `CtxProvideTest`：注册后按名解析命中 |
| V2 | 作用域链 | `CtxScopeChainTest`：子作用域解析回退父级 |
| V3 | isolate 隔离 | `CtxIsolateTest`：realm 内注册不污染父作用域 |
| V4 | intercept 拦截 | `CtxInterceptTest`：拦截器可记录/拒绝服务访问 |
| V5 | 随 fiber 注销 | `CtxDisposeTest`：注销句柄执行后 get 返回空 |
| V6 | isolate label-join | `CtxLabelJoinTest`：同名 label 两次 isolate 返回同一作用域（共享 realm），不同 label 相互隔离 |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=Ctx*Test"
```

## §7 影响域

- 作用范围：**服务进程级**——全部插件的依赖解析载体。
- 被谁消费：所有 L1/L3/L4 插件；经 Spring 桥被业务代码间接消费。
- 故障影响半径：容器缺陷 = 全服务插件能力不可用（V1-V6 为发布门）。
- 升级影响：容器语义升级 = 全服务回归（版本化，向后兼容承诺）。

## §8 后续（未纳入首版）

- 服务访问粒度的安全拦截策略（与 permission 插件联动）
- 代理化懒解析（dsh 代理语义增强，按需实例化）
- Ctx 快照/诊断（服务解析路径可观测）

---

*GNEX3.0 Ctx 服务容器 — cordis Context Java 移植 SDD。*
