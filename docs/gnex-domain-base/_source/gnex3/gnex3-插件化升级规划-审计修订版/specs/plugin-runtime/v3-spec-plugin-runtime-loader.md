# v3-spec-plugin-runtime-loader

> **来源**：../../04-gnex3.0-plugin-catalog.md §L0 组合与装载层
> **负责人**：<owner>
> **插件 ID**：gnex3-l0-loader
> **分层**：L0 组合与装载层
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：全部 gnex 服务
> **实现状态**：规划中

---

## §1 业务目标

按装配模型驱动插件装载/卸载：消费 `assembly` 服务的组合结果，对插件做依赖拓扑排序与等待（inject 未就绪则挂起），驱动 kernel 的 `PluginRuntime` 装载；卸载时按 effect 栈回卷全部副作用。装载失败必须整组回滚（不残留半装载插件），受保护插件（platform）拒绝卸载（继承 ADR-046「闸门不可卸」）。对齐 cordis-plugin-loader / cordis-plugin-include 语义。

**非目标**：不做 jar 动态类加载（v3.1 由 PF4J 后端承接）；不做跨服务插件调度（每服务独立装载）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | cordis-plugin-loader / cordis-plugin-include |
| 对标 GNEX2.0 模块 | 无对应（新增） |
| 技术选型 | 🅳 Java 移植 dsh 装载语义 |
| 改造要点 | ① 消费 AssemblyModel 驱动 `PluginRuntime.load/unload` ② 按 inject 声明做依赖拓扑排序与等待 ③ 装载失败回滚已装载插件（整组原子性）④ 受保护插件卸载前置拒绝 ⑤ 卸载按 effect 栈 LIFO 回卷（服务/监听/工具注册全部撤销） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `pluginLoader` | `com.gnex.plugin.runtime.PluginLoader` | 装载控制：load(model)/unload(id)/list() |

### 3.2 注入服务（inject）

- `assembly`（必选）：装配模型输入
- `ctx`（必选）：kernel 根容器，驱动 PluginRuntime

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `plugin/installed` | emit | {pluginId, version} |
| `plugin/started` | emit | {pluginId, services[]} |
| `plugin/stopped` | emit | {pluginId, reason} |
| `plugin/uninstalled` | emit | {pluginId} |
| `plugin/error` | listen | {pluginId, error} → 触发失败回滚策略 |

## §4 配置与装配

```yaml
# service-assembly.yml 片段
plugins:
  - id: gnex3-l1-ctx
    load: mandatory
  - id: gnex3-cap-tool-bash
    load: optional
gnex.loader.fail-fast: true             # 任一 mandatory 插件装载失败 → 服务启动中止
gnex.loader.dependency-timeout: 30s     # 依赖等待超时
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 装载必须按依赖拓扑序执行；依赖未就绪必须等待（挂起）而非失败
- ✅ 卸载必须回卷 effect（插件注册的服务/监听器/工具全部注销，卸载后 ctx.get 返回空）
- ✅ 受保护插件（protection: platform）卸载必须被拒绝
- ✅ 装载失败必须整组回滚（已装载插件一并卸载，禁止半装载残留）
- ❌ 不得加载装配清单之外的插件（白名单装载）
- ❌ 不得在 v3.0 执行 jar 热装（服务重启生效）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 依赖乱序装载 | `LoaderDependencyTest`：声明顺序乱序仍按依赖序 start |
| V2 | 依赖等待 | `LoaderWaitTest`：依赖未注册时插件挂起不崩溃，就绪后继续 |
| V3 | 卸载回卷 | `LoaderUnloadTest`：卸载后服务/监听器全部注销 |
| V4 | 失败整组回滚 | `LoaderRollbackTest`：某插件 start 抛错，其余已装载插件全部回滚 |
| V5 | 保护插件卸载拒绝 | `LoaderProtectionTest`：platform 插件 unload 被拒 |
| V6 | 白名单外拒绝 | `LoaderWhitelistTest`：清单外插件 id 装载被拒 |
| V7 | 事件顺序 | `LoaderEventTest`：installed → started → stopped → uninstalled 顺序正确 |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=Loader*Test"
```

## §7 影响域

- 作用范围：**服务进程级**——每个服务的插件装载执行器。
- 被谁消费：kernel `PluginRuntime`（执行后端）；inventory（装卸编排）。
- 故障影响半径：装载缺陷 = 插件组合错乱/服务启动失败；fail-fast 与整组回滚兜底。
- 升级影响：loader 语义升级 = 全服务回归（版本化，向后兼容）。

## §8 后续（未纳入首版）

- PF4J 类加载器后端（v3.1 jar 热装：独立 classloader、版本管理、热装验收门）
- 跨服务装载编排（装配基线由 registry 下发，多副本一致装载）
- 装载清单快照与回放（支持 inventory 状态核对的审计证据）

---

*GNEX3.0 插件加载器 — 装载/依赖等待/卸载回卷 SDD。*
