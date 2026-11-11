# v3-spec-plugin-runtime-assembly

> **来源**：../../04-gnex3.0-plugin-catalog.md §L0 组合与装载层
> **负责人**：<owner>
> **插件 ID**：gnex3-l0-assembly
> **分层**：L0 组合与装载层
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：全部 gnex 服务（服务启动期）
> **实现状态**：规划中

---

## §1 业务目标

定义"服务 = 内核 + 插件集"的声明式装配模型：每个 gnex 服务在启动期解析 `service-assembly.yml`，经校验后产出不可变的组合结果（插件清单、装载序、保护级别、配置段），供 loader 按序装载。组合语义对标 dsh 的 profile·bundle·patch（多环境 profile 覆盖、bundle 分组、patch 增量合并），让服务能力边界可枚举、可审计。装配变更以服务重启生效（v3.0 不做热装配）。

**非目标**：不做运行期装配变更（装卸走 inventory/pluginLoader）；不做 jar 热加载；不替代 Spring application.yml 的服务进程配置（本插件管插件组合层）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh-app-boot / dsh（profile·bundle·patch 组合） |
| 对标 GNEX2.0 模块 | gnex-dev-assembler + application.yml |
| 技术选型 | 🅳 Java 移植 dsh 装配组合语义（YAML → 装配模型） |
| 改造要点 | ① `service-assembly.yml` 解析为装配模型（插件 id/load/protection/配置段）② 支持 profile 覆盖 + bundle 分组 + patch 增量合并（对齐 dsh 组合行语义）③ 校验：未知插件 id、依赖闭包完整性、保护级别与装卸权一致性 ④ 组合结果不可变，启动期一次性求值并缓存 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `assembly` | `com.gnex.plugin.runtime.AssemblyModel` | 只读装配模型：插件清单/装载序/保护级别/组合审计信息 |

### 3.2 注入服务（inject）

无（启动期最先加载，先于 kernel 装载；不依赖任何插件服务）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| （无自定义事件） | — | 组合完成由 loader 以 `plugin/installed` 系列事件对外呈现 |

## §4 配置与装配

```yaml
# service-assembly.yml（每服务一份，启动期解析）
assembly:
  profiles:
    active: prod          # profile 覆盖（对标 dsh profile）
  bundles:
    core: [gnex3-l1-ctx, gnex3-l1-events, gnex3-l1-fiber]
plugins:
  - id: gnex3-l1-scope
    load: mandatory
    protection: platform
  - id: gnex3-cap-tool-bash
    load: optional
gnex.assembly.fail-fast: true   # 校验失败即启动中止
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 装配清单必须经校验后才可进入装载（未知插件 id / 依赖缺失 / 保护级别冲突 → 启动失败并给出清单级错误）
- ✅ profile/bundle/patch 组合必须可回溯（组合过程写入审计信息，供 inventory 版本比对）
- ✅ 每个插件必须声明版本，组合结果必须幂等（同输入同输出）
- ❌ 不得在运行期重读/改写装配清单（运行期装卸走 inventory/pluginLoader）
- ❌ 不得绕过校验直接装载（未通过校验的清单禁止进入 loader）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 解析合法清单 | `AssemblyParseTest`：插件/装载序/保护级别解析正确 |
| V2 | profile 覆盖 | `AssemblyProfileTest`：active=prod 时覆盖项生效 |
| V3 | patch 增量合并 | `AssemblyPatchTest`：patch 追加不破坏原清单 |
| V4 | 未知插件 id | `AssemblyValidateTest`：抛清单级错误，启动中止 |
| V5 | 依赖缺失 | `AssemblyValidateTest`：缺失依赖被检出并定位 |
| V6 | 组合幂等 | `AssemblyIdempotencyTest`：同输入两次求值结果一致 |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=Assembly*Test"
```

## §7 影响域

- 作用范围：**服务启动期**——装配清单决定每个服务的能力组合。
- 被谁消费：gnex3-l0-loader（装载驱动）；inventory（版本/状态比对）。
- 故障影响半径：装配解析/校验缺陷 = 全服务启动失败（fail-fast 为发布门）。
- 升级影响：装配清单变更 = 服务重启生效（v3.0 无热装配）。

## §8 后续（未纳入首版）

- 装配清单 schema 外部化校验（JSON Schema 类比，独立校验插件）
- 远程装配源（registry-service 下发装配基线，多副本一致）
- v3.1 热装配（装配变更不停机生效，配合 jar 热装）

---

*GNEX3.0 服务装配清单解析器 — service-assembly.yml 组合语义 SDD。*
