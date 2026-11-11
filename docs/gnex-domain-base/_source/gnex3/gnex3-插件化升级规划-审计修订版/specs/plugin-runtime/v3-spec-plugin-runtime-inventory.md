# v3-spec-plugin-runtime-inventory

> **来源**：../../04-gnex3.0-plugin-catalog.md §L0 组合与装载层
> **负责人**：<owner>
> **插件 ID**：gnex3-l0-inventory
> **分层**：L0 组合与装载层
> **受保护级别**：normal（🔐 管理员可装卸，租户/Agent 无装卸权）
> **宿主服务**：registry-service
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 修订依据：审计修订版/02-设计决策记录.md D-P1-19、D-P0-03

---

## §1 业务目标

提供跨服务的插件清单与管理面：聚合各服务插件状态/版本/保护级别，暴露管理 API（安装/卸载/版本比对），对齐 dsh-host-plugin-inventory；作为 registry-service 控制面扩展（对标其 agent 目录管理模式：管理角色 @PreAuthorize + 审计发布）。装卸操作委托 pluginLoader 执行，保护级别校验前置，管理员可装卸（🔐），租户/Agent 无权。

**非目标**：不做插件运行时执行（装载归 pluginLoader、执行归能力插件）；不做插件市场/供应链扫描（后续）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh-host-plugin-inventory |
| 对标 GNEX2.0 模块 | registry-service 扩展（注册控制面，仿 AgentRegistryController 模式） |
| 技术选型 | 🅶 GNEX2.0 模块改造（registry-service 扩展） |
| 改造要点 | ① 新增插件目录表 `plugin_registry`（id/version/status/host/protection，gnex-platform-persistence 扩展）② 管理 API 对齐 registry 既有纪律（@PreAuthorize 管理角色 + AuditPublisher 审计）③ 状态聚合：监听 plugin/* 事件 + 服务启动期全量上报 ④ 装卸编排委托 pluginLoader，protection 校验前置 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `pluginInventory` | `com.gnex.plugin.runtime.PluginInventory` | 清单查询/版本比对/装卸 API（管理面） |

### 3.2 注入服务（inject）

- `pluginRegistry`（必选）：kernel 进程内插件清单/状态查询
- `pluginLoader`（可选）：装卸执行（未装配 loader 的服务仅记录元数据）
- `bus`（**必选**，D-P1-19）：跨服务事件通道——各服务 plugin/* 生命周期事件经 bus topic `gnex.plugin.*` 上报聚合；本插件的管理动作事件也经 bus 对外发布（进程内事件总线无法跨服务到达 registry-service）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `plugin/installed` | listen | {pluginId, version, host} 聚合状态——**经 bus topic `gnex.plugin.installed` 跨服务接收**（各服务 emit 后由 bus 透出，D-P1-19） |
| `plugin/started` | listen | {pluginId, services[]} 聚合状态——经 bus `gnex.plugin.started` |
| `plugin/stopped` | listen | {pluginId, reason} 聚合状态——经 bus `gnex.plugin.stopped` |
| `plugin/uninstalled` | listen | {pluginId} 聚合状态——经 bus `gnex.plugin.uninstalled` |
| `plugin/installed` | emit | 管理 API 发起安装时对外发布（经 bus `gnex.plugin.installed` 跨服务广播） |
| `plugin/uninstalled` | emit | 管理 API 发起卸载时对外发布（经 bus `gnex.plugin.uninstalled`） |

## §4 配置与装配

```yaml
# service-assembly.yml（registry-service 声明）
plugins:
  - id: gnex3-l0-inventory
    load: mandatory
    protection: admin        # 🔐 管理员可装卸
gnex.inventory.api-prefix: /api/plugins
gnex.inventory.sync-interval: 60s   # 与各服务状态周期核对
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 装卸 API 必须经 @PreAuthorize 管理角色校验并发布审计（对齐 registry 既有 UPLOAD/INSTALL/UNINSTALL 审计纪律）
- ✅ 清单状态必须与 kernel 实际状态一致（事件驱动 + 周期核对）
- ✅ 版本比对必须支持升级/降级提示（版本号语义化比较）
- ✅ **双通道分工（D-P1-19）**：`events`（进程内事件总线）只承载宿主服务内的 plugin/* 分发；**跨服务**的 plugin/* 一律经 `bus` 透出为 topic `gnex.plugin.*`（本插件作为 bus 消费者聚合各服务状态）；进程内与 bus 不得重复消费同一事件（防双计）
- ❌ 租户/Agent 不得调用装卸 API（🔐 管理员专属，403 拒绝）
- ❌ 不得直接写插件运行态（仅查询/编排；装载执行一律经 pluginLoader）
- ❌ 不得经进程内 events 总线跨服务采集状态（进程内事件不跨进程；跨服务仅 bus）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 清单查询 | `PluginInventoryQueryTest`：状态/版本/宿主/保护级别齐全 |
| V2 | 安装编排 | `PluginInventoryInstallTest`：委托 pluginLoader 并发布 plugin/installed |
| V3 | 卸载编排 | `PluginInventoryUninstallTest`：protection=platform 拒绝、admin 放行 |
| V4 | 状态聚合 | `PluginInventoryEventTest`：plugin/started 事件驱动状态更新 |
| V5 | 权限拒绝 | `PluginInventoryAuthTest`：非管理角色调用装卸 API 被拒 |

### 运行命令

```bash
mvn -pl services/gnex-registry-service test "-Dtest=PluginInventory*Test"
# 通配覆盖 V1-V5 全部测试类（D-P0-03）
```

## §7 影响域

- 作用范围：**服务级（管理面）**——registry-service 内运行，跨服务只读聚合。
- 被谁消费：平台管理员 UI；运维脚本（版本巡检）；pluginLoader（装卸编排）。
- 故障影响半径：管理面故障不影响插件运行（只读聚合降级）；装卸失败由 loader 整组回滚兜底。
- 升级影响：目录表结构变更 = 迁移；管理 API 增删 = 前端适配。

## §8 后续（未纳入首版）

- 插件市场/供应链检查（依赖 OSV 类比扫描，对齐 Goose P1）
- 装配基线下发（registry 统一装配版本，多副本一致）
- 装卸审计报表与版本回滚记录

---

*GNEX3.0 插件清单与管理面 — registry-service 扩展 SDD。*
