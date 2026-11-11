# v3-spec-plugin-runtime-logger

> **来源**：../../04-gnex3.0-plugin-catalog.md §L1 内核层
> **负责人**：<owner>
> **插件 ID**：gnex3-l1-logger
> **分层**：L1 内核层
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：全部 gnex 服务
> **实现状态**：规划中

---

## §1 业务目标

命名日志与诊断服务：以插件 id 为 logger 命名空间（dsh/cordis logger 语义），自动注入 scope/会话/请求上下文（MDC），支持按插件覆盖日志级别；gnex2.0 的 SLF4J/Logback 保留为底层实现，本插件提供插件化命名与诊断上下文。敏感字段统一脱敏（密钥/token/secret 解析值不落日志）。生命周期事件自动写诊断日志。

**非目标**：不做日志采集/链路（observability 插件承接）；不做日志持久化策略（Logback 配置保留）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | cordis logger（命名日志） |
| 对标 GNEX2.0 模块 | SLF4J/Logback（改造为插件命名空间） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① logger 服务（getLogger(pluginId) 命名空间）② MDC 自动注入 scope/session/requestId（配合 scope 插件）③ 级别配置随 settings 插件段覆盖 ④ 敏感字段脱敏过滤器（mask-fields 表）⑤ 插件生命周期日志（listen plugin/*） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `logger` | `com.gnex.plugin.runtime.LoggerFactory` | getLogger(name)/诊断上下文注入 |

### 3.2 注入服务（inject）

- `ctx`（必选）：注册 logger 服务
- `scope`（可选）：realm 上下文注入 MDC

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `plugin/started` | listen | {pluginId, services[]} → 写生命周期日志 |
| `plugin/stopped` | listen | {pluginId, reason} → 写生命周期日志 |

## §4 配置与装配

```yaml
# 插件段配置（settings 命名空间）
gnex3-l1-logger:
  level: info                     # 全局默认级别
  plugin-levels:                  # 按插件覆盖
    gnex3-cap-tool-bash: debug
  mask-fields: [api-key, token, secret, password]   # 脱敏字段表
gnex.logger.mdc: [scope.tenantId, scope.sessionId, requestId]
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 日志必须携带 scope/session/requestId 上下文（可追踪、可过滤）
- ✅ 敏感字段必须脱敏（mask-fields 命中即替换，密钥/token 不落日志）
- ✅ 诊断必须支持按插件 id 覆盖级别（问题定位不打全量 DEBUG）
- ❌ 不得在日志输出完整凭据、secret 解析值、请求体敏感字段
- ❌ 日志写入不得同步阻塞业务线程（异步/无阻塞）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 命名日志 | `LoggerTest`：getLogger(pluginId) 名称正确 |
| V2 | MDC 上下文 | `LoggerMdcTest`：scope/session 注入 MDC |
| V3 | 脱敏 | `LoggerMaskTest`：mask-fields 命中字段被替换 |
| V4 | 级别覆盖 | `LoggerLevelTest`：插件段级别覆盖全局 |
| V5 | 生命周期日志 | `LoggerLifecycleTest`：plugin/started·stopped 落日志 |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=Logger*Test"
```

## §7 影响域

- 作用范围：**服务进程级**——全部插件的日志出口。
- 被谁消费：所有插件（诊断）；observability（采集上游）；运维（问题定位）。
- 故障影响半径：日志缺陷不阻断业务（无阻塞写入 + 异常隔离）；脱敏缺失 = 安全事件（V3 为发布门）。
- 升级影响：日志格式变更 = 采集端适配；级别策略变更 = 无感。

## §8 后续（未纳入首版）

- 结构化日志 schema（与 observability 联动 OTEL）
- 会话级日志关联视图（诊断界面）
- 日志采样与降噪策略（高流量插件）

---

*GNEX3.0 日志/诊断 — cordis logger 命名日志 SDD。*
