# v3-spec-plugin-runtime-config

> **来源**：../../04-gnex3.0-plugin-catalog.md §L0 组合与装载层
> **负责人**：<owner>
> **插件 ID**：gnex3-l0-config
> **分层**：L0 组合与装载层
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：全部 gnex 服务
> **实现状态**：规划中

---

## §1 业务目标

服务内配置中心：统一管理插件配置段（对标 dsh-settings-file 的 file 层），支持默认值回退、类型校验与变更观察；含 secret 引用（`${secret:<credentialId>}`，明文不落地，解析委托 gnex3-cap-credentials）。gnex2.0 的 LLMConfigManager / config 包收敛为插件化配置服务，为 L1-settings 提供服务级数据源。

**非目标**：不替代 Spring 配置基础设施（application.yml 仍管服务进程）；不存储密钥明文（仅引用 id）；不做跨服务配置下发（后续经 registry 装配基线）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh-settings-file + dsh-credentials-local（file/secret 层） |
| 对标 GNEX2.0 模块 | LLMConfigManager / config 包（收敛改造） |
| 技术选型 | 🅶 GNEX2.0 模块改造（配置入口统一） |
| 改造要点 | ① 服务配置统一入口（插件配置段加载/校验/缓存）② secret 引用解析委托 credentials（不落地明文）③ 变更观察者（in-process 回调）④ 敏感字段脱敏（导出/审计不出现明文）⑤ 与 L1-settings 分层：config=服务级静态配置，settings=插件运行配置段 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `config` | `com.gnex.plugin.runtime.ConfigCenter` | get(key, default)/set/observe（类型安全读取） |

### 3.2 注入服务（inject）

- `ctx`（必选）：注册 config 服务
- `credentials`（可选，gnex3-cap-credentials）：secret 引用解析（未装配时引用解析失败回退默认值）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `credential/updated` | listen | {credentialId} → 刷新受影响 secret 引用缓存 |

## §4 配置与装配

```yaml
# 插件配置段（service-assembly.yml 或独立配置源）
gnex.config:
  reloadable: false            # v3.0 静态配置，变更重启生效
  secret-prefix: "secret:"     # 引用语法 ${secret:<credentialId>}
plugins:
  - id: gnex3-l1-settings
    config:
      llm:
        model: deepseek-chat
        api-key: ${secret:llm-main-key}   # 仅引用，不落地明文
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ secret 引用必须只存引用 id，解析结果不得落盘/入日志/入审计
- ✅ 配置读取必须带默认值回退与类型校验（读错类型抛配置错误）
- ✅ 配置导出/管理 API 必须脱敏（mask 敏感字段）
- ❌ 插件不得绕过 config 直接拼装文件路径读取配置（统一入口）
- ❌ 不得在运行期静默覆盖已加载配置（reloadable=false 时变更须重启）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 默认值回退 | `ConfigCenterTest`：缺失键返回默认值 |
| V2 | secret 引用解析 | `ConfigSecretTest`：${secret:...} 解析委托 credentials |
| V3 | 引用失败回退 | `ConfigSecretTest`：credentials 缺失时回退默认值并告警 |
| V4 | 脱敏断言 | `ConfigMaskTest`：导出内容不含明文密钥 |
| V5 | 刷新观察者 | `ConfigRefreshTest`：credential/updated 后引用值刷新 |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=ConfigCenterTest"
```

## §7 影响域

- 作用范围：**服务进程级**——全部 gnex 服务的配置入口。
- 被谁消费：L1-settings（数据源）；模型/工具/策略插件（配置段）。
- 故障影响半径：配置解析缺陷 = 依赖配置的插件启动失败（默认值回退缓解）；secret 泄漏 = 安全事件（脱敏为发布门）。
- 升级影响：配置键变更 = 消费插件回归；secret 迁移 = 无感（引用 id 不变）。

## §8 后续（未纳入首版）

- 配置热更新（reloadable=true + 版本化变更事件）
- 远程配置下发（registry 装配基线携带配置段）
- 配置审计时间线（谁在何时改了什么）

---

*GNEX3.0 配置中心 — settings-file/credentials-local 收敛 SDD。*
