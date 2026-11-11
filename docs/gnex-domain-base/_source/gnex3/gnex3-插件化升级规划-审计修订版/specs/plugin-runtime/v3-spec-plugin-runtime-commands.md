# v3-spec-plugin-runtime-commands

> **来源**：../../04-gnex3.0-plugin-catalog.md §L0 组合与装载层
> **负责人**：<owner>
> **插件 ID**：gnex3-l0-commands
> **分层**：L0 组合与装载层
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

把用户斜杠命令（/命令）注册、解析、分发插件化：对齐 dsh-commands + dsh-command-*；将 agent-service 的 shell / ShellRouter 改造为插件命令注册表——命令处理器随插件装载注册、随卸载注销（effect 回卷），命令执行发 `command/run`·`command/done` 事件供可观测与审计。命令与工具管线解耦：命令是用户显式入口，工具是模型驱动的执行单元。

**非目标**：不做命令执行沙箱（工具执行走 sandbox）；不做跨服务命令调度（命令在 agent-service 内解析执行）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh-commands + dsh-command-*（命令集） |
| 对标 GNEX2.0 模块 | agent-service shell / ShellRouter（改造） |
| 技术选型 | 🅶 GNEX2.0 模块改造（ShellRouter → 插件命令注册表） |
| 改造要点 | ① ShellRouter 改造为命令注册表（命令名 → CommandHandler，随插件 provide 注册）② 参数解析（斜杠 + 选项 + 位置参数）③ 执行发 command/run · command/done 事件 ④ 命令处理器随插件 effect 回卷注销（无残留）⑤ 内建命令（/help /status）平台声明，插件不可覆盖 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `commands` | `com.gnex.plugin.runtime.CommandRegistry` | 命令注册/注销/解析/执行 |

### 3.2 注入服务（inject）

- `ctx`（必选）：注册命令处理器
- `events`（必选）：命令执行事件

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `command/run` | emit | {command, args, sessionId} |
| `command/done` | emit | {command, result, error?} |

## §4 配置与装配

```yaml
# service-assembly.yml（agent-service 声明）
plugins:
  - id: gnex3-l0-commands
    load: mandatory
gnex.commands.builtin: [help, status, cancel]   # 平台内建，禁覆盖
gnex.commands.max-args-length: 4096
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 命令注册必须随插件装载/卸载自动增删（卸载后命令不可解析）
- ✅ 命令执行必须发 command/run · command/done 事件（可观测/审计）
- ✅ 命令名冲突必须拒绝注册（防插件覆盖既有命令）
- ❌ 命令处理器不得绕过 Ctx 直接访问会话存储（经 sessions 等服务）
- ❌ 内建命令不得被插件覆盖或注销（平台声明优先）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 命令注册 | `CommandRegistryTest`：注册后 parse/execute 可用 |
| V2 | 冲突拒绝 | `CommandRegistryTest`：同名注册抛冲突异常 |
| V3 | 执行事件 | `CommandEventTest`：执行发 command/run → command/done 且载荷正确 |
| V4 | 卸载注销 | `CommandUnloadTest`：插件卸载后命令解析返回未注册 |
| V5 | 内建保护 | `CommandBuiltinTest`：覆盖 /help 被拒 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=PluginCommandsTest"
```

## §7 影响域

- 作用范围：**会话级（每命令）**——agent-service 内用户显式入口。
- 被谁消费：聊天回合入口（ShellRouter 改造后）；UI 命令面板；审计（command/* 事件）。
- 故障影响半径：单个命令处理器故障 = 该命令不可用（其余不动）；注册表故障 = 命令入口整体不可用。
- 升级影响：命令集装卸 = 无感（其余命令不动）；注册表 API 变更 = 全命令回归。

## §8 后续（未纳入首版）

- 命令模板/别名系统（dsh-command-alias 类比）
- 命令级权限（与 gnex3-cap-permission 联动，🔒 平台预设）
- 跨服务命令代理（远端服务命令经 bus 转发）

---

*GNEX3.0 用户命令注册表 — ShellRouter 插件化 SDD。*
