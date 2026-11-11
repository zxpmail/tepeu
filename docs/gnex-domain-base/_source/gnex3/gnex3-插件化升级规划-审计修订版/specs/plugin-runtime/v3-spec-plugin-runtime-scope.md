# v3-spec-plugin-runtime-scope

> **来源**：../../04-gnex3.0-plugin-catalog.md §L1 内核层
> **负责人**：<owner>
> **插件 ID**：gnex3-l1-scope
> **分层**：L1 内核层
> **受保护级别**：platform（🔒 平台持有，禁租户/Agent 装卸）
> **宿主服务**：全部 gnex 服务
> **实现状态**：规划中

---

## §1 业务目标

实现作用域 realm 语义（dsh-scope 的 Java 移植）：租户/项目/会话三级隔离原语，作为服务解析与事件分发的过滤维度。gnex2.0 的 TenantInterceptor / ProjectScopeInterceptor 改造为插件化 scope 服务：请求进入时建立 realm，realm 随请求传播（服务端解析，不可伪造）。🔒 平台持有：隔离边界本身不可被租户/Agent 装卸或绕过（继承企业租户硬过滤纪律）。

**非目标**：不做企业级租户策略（gnex3-ent-tenant 承接硬过滤+亲和调度）；不做会话数据模型（session-store 承接）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh-scope（作用域隔离） |
| 对标 GNEX2.0 模块 | TenantInterceptor / ProjectScopeInterceptor（改造） |
| 技术选型 | 🅶 GNEX2.0 模块改造（拦截器 → 插件化 scope 服务） |
| 改造要点 | ① Scope 原语（realm 键：tenant/project/session，keyed realm）② 拦截器改造为插件服务（请求进入建立 realm，Web/SSE/WS 入口统一）③ 与 ctx/events 联动：服务解析与事件分发按 realm 过滤 ④ 隔离边界硬校验（realm 不匹配即拒绝）⑤ 传播头服务端解析（X-Tenant-Id/X-Project-Id，不可客户端伪造） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `scope` | `com.gnex.plugin.runtime.Scope` | realm 建立/查询/校验（keyed by 租户/项目/会话） |

### 3.2 注入服务（inject）

- `ctx`（必选）：注册 scope 服务
- `events`（必选）：事件 realm 过滤元数据

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tenant/isolated` | emit | {tenantId, realm} 隔离动作可观测 |
| `session/created` | listen | {sessionId, tenantId, projectId} 建立会话 realm |
| `agent/created` | listen | {agentName, tenantId} 建立 Agent realm |

## §4 配置与装配

```yaml
# service-assembly.yml 片段（各服务声明，保护级别 platform）
plugins:
  - id: gnex3-l1-scope
    load: mandatory
    protection: platform        # 🔒 平台持有，禁租户/Agent 装卸
gnex.scope.realm-headers: [X-Tenant-Id, X-Project-Id]   # 服务端解析，不可伪造
gnex.scope.default-realm: system
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 跨租户访问必须被拒绝（realm 不匹配即隔离，不降级放行）
- ✅ realm 必须服务端解析传播头建立，禁止信任客户端自报
- ✅ 事件分发必须携带 realm 供过滤（与 events 插件联动）
- ❌ 租户/Agent 不得装卸或绕过 scope（🔒 平台持有）
- ❌ 不得把会话私有状态提升为服务级单例（双平面归属规则：会话自有状态归会话 realm）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 租户隔离 | `ScopeTenantTest`：跨租户服务解析被拒绝 |
| V2 | 会话 realm | `ScopeSessionTest`：session/created 建立会话 realm |
| V3 | 事件 realm 过滤 | `ScopeEventFilterTest`：realm 不匹配监听器不触发 |
| V4 | 传播头校验 | `ScopeHeaderTest`：伪造传播头被拒/回退默认 realm |
| V5 | 装卸拒绝 | `ScopeProtectionTest`：platform 插件 unload 被拒 |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=Scope*Test"
```

## §7 影响域

- 作用范围：**服务进程级（隔离维度）**——全部服务的请求/事件边界。
- 被谁消费：会话/工具插件（realm 解析）；events（过滤）；ent-tenant（企业硬过滤上游）。
- 故障影响半径：隔离缺陷 = 跨租户越权（安全事件）；V1/V4 为发布门。
- 升级影响：🔒 平台级升级 + 灰度（realm 语义变更影响全部服务）。

## §8 后续（未纳入首版）

- realm 传播到跨服务调用（bus 消息携带 realm 信封）
- 项目/会话级配额联动（billing）
- 作用域继承图可视化（租户→项目→会话树）

---

*GNEX3.0 作用域服务 — 租户/项目/会话 realm 隔离 SDD。*
