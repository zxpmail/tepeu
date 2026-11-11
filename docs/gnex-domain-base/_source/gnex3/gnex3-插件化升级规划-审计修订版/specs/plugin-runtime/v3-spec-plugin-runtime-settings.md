# v3-spec-plugin-runtime-settings

> **来源**：../../04-gnex3.0-plugin-catalog.md §L1 内核层
> **负责人**：<owner>
> **插件 ID**：gnex3-l1-settings
> **分层**：L1 内核层
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：全部 gnex 服务
> **实现状态**：规划中

---

## §1 业务目标

插件设置服务：为每个插件提供命名空间化的配置段读写（dsh-settings 语义），带默认值、schema 校验与变更观察者；gnex2.0 的 @ConfigurationProperties 收敛为插件化设置段。与 L0-config 分层：config = 服务级静态配置（含 secret 引用），settings = 插件运行期配置段（可由管理面调整）。敏感字段读写统一委托 config/credentials 脱敏。

**非目标**：不存密钥明文（secret 引用委托 credentials）；不做跨服务设置同步（各服务独立）；不做运行期 schema 热变更。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh-settings（插件配置段） |
| 对标 GNEX2.0 模块 | @ConfigurationProperties（改造为插件化设置段） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① settings 命名空间（pluginId.配置段）读写 API ② 默认值 + schema 校验（类型/枚举/范围）③ 变更观察者（in-process 回调，配置热更新一致性）④ 与 L0-config 分层（config=静态服务级，settings=插件运行配置）⑤ 敏感字段脱敏返回 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `settings` | `com.gnex.plugin.runtime.Settings` | get/read(namespace)/write/observe |

### 3.2 注入服务（inject）

- `ctx`（必选）：注册 settings 服务
- `config`（可选，gnex3-l0-config）：服务级数据源（未装配时从默认值初始化）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `plugin/started` | listen | {pluginId} → 初始化该插件配置段 |

## §4 配置与装配

```yaml
# 插件配置段示例（settings 命名空间 = 插件 id）
gnex3-cap-model-llm:
  provider: deepseek
  temperature: 0.7          # schema 校验：0.0-2.0
  api-key: ${secret:llm-main-key}   # 敏感字段经 config/credentials
gnex.settings.reloadable: true      # 管理面可调整，观察者通知消费插件
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 插件配置段必须带 schema 校验与默认值（非法写入拒绝并报错）
- ✅ 配置变更必须通知观察者（热更新一致性，消费插件同步生效）
- ✅ 敏感字段读取必须脱敏（管理面返回 mask）
- ❌ 插件不得绕过 settings 直接读全局 application.yml（统一入口）
- ❌ 不得在无 schema 的命名空间写入任意键（配置漂移防护）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 读写命名空间 | `SettingsTest`：pluginId 命名空间读写隔离 |
| V2 | 默认值 | `SettingsTest`：缺失键返回 schema 默认值 |
| V3 | schema 拒绝 | `SettingsSchemaTest`：越界/错类型写入被拒 |
| V4 | 观察者通知 | `SettingsObserverTest`：write 后观察者回调且值一致 |
| V5 | 脱敏返回 | `SettingsMaskTest`：敏感字段返回 mask 值 |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=Settings*Test"
```

## §7 影响域

- 作用范围：**服务进程级（插件配置面）**——全部插件的运行配置。
- 被谁消费：模型/工具/策略插件（配置段）；管理面 UI（读写）。
- 故障影响半径：设置缺陷 = 配置错乱插件行为异常；schema 校验与默认值兜底。
- 升级影响：设置段 schema 变更 = 消费插件回归；配置迁移 = 无感（默认值兼容）。

## §8 后续（未纳入首版）

- 配置段版本化与回滚（管理面时间线）
- 跨服务设置下发（装配基线携带设置段）
- 设置项合规扫描（敏感项自动转 secret 引用）

---

*GNEX3.0 设置服务 — 插件配置段读写 SDD。*
