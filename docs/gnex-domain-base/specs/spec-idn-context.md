# spec-idn-context

> 来源：01-需求分析.md、06-总体设计、specs/00-contracts.md
> 模块 ID：idn-context｜能力域：身份｜保护级别：core｜宿主：daf-api 过滤器，校验经 daf-brg｜实现状态：可开发


## §1 业务目标

业务请求必须先知道租户、用户、项目，才能碰应用和数据。基座不发登录凭证，只核验 gnex 已发出的凭证。缺凭证或租户对不上就拒绝。现状是各业务系统自己解析令牌，容易漏。目标是进入任一模块前，上下文要么完整，要么请求已结束。对应 FR-IDN-01、FR-IDN-05、NFR-01。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-IDN-01、FR-IDN-05、NFR-01、NFR-08 |
| 对标借鉴 | gnex 租户规格的 fail-closed；不采用 Appsmith 工作区会话 |
| 技术选型 | 选「一次请求过滤器 + 数据库会话变量」。备选是在每个查询里手工传租户参数。不选的反例：新写的查询忘记参数，行级安全没有会话变量可对，测试环境能通过、生产漏数据。复决：若 PostgreSQL 会话变量在连接池归还后仍残留（集成测试能复现串租户），改为每个事务显式 `SET LOCAL` 且连接归还前 `RESET`，并重开本决定 |
| 改造要点 | 过滤器只调 `brg.gnex`。成功则写入线程上下文并 `SET LOCAL daf.tenant_id`。失败立即返回错误体。不建用户表 |

决策 D-002、D-003。

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| idn.context | `TenantContext current()` | 无上下文时抛出已映射为 RC-AUTH-MISSING 的异常，禁止返回空对象 |
| idn.context | `void bind(IntrospectionResult result)` | 仅过滤器可调用 |

### 3.2 REST

无单独资源。所有 `/api/daf/v1/**`（除 `GET /api/daf/v1/health`）经过此过滤器。健康检查不暴露租户数据。

### 3.3 事件

不发业务事件。拒绝由访问日志记录 `traceId`，不记令牌原文。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| brg.gnex | 必选 | 核验令牌 | 无法核验则拒绝，不用本地缓存的成功结果 |

## §4 数据模型

无业务表。数据库会话级变量 `daf.tenant_id`。连接归还连接池前必须 `RESET`。集成测试要覆盖「请求 A 结束后请求 B 复用连接」。

## §5 实现思路

1. 读取 `Authorization: Bearer`。没有则 401，文案 RC-AUTH-MISSING。
2. 调用桥接核验。桥接超时按核验失败，不重试第二次。超时用 `LIT-C-INTROSPECT-MS`。
3. 核验结果必须同时含 `tid`、`uid`、`projectId`。缺一则 401，文案 `RC-AUTH-INCOMPLETE`。`roles` 不是这三项的替代品，但平台管理员判定要看 `roles` 是否包含 `daf.security.platform-role`。
4. 路径、查询参数或 JSON 正文任一处出现 `tenantId` 且不等于 `tid`，则 403，文案 `RC-TENANT-MISMATCH`，`details` 为空对象。不按应用键做全库查找后再比租户。
5. 在事务开始时对两个变量执行 `SELECT set_config('...', ?, true)`，第二个参数为租户或项目。`true` 表示事务结束自动丢弃。
6. 线程上下文用请求范围对象，禁止放进可跨请求的静态 Map。核不成功的结果不缓存。核验服务不可用时除健康检查外全部 401，文案 `RC-AUTH-MISSING`。

数值与拒绝文案只引用 specs/00-literals.md，此处不抄写第二份。

## §6 边界

- ✅ 必须 fail-closed：核验失败、超时、声明缺失都拒绝。
- ✅ 必须在进入 app、dat、sem、pkg 之前完成绑定。
- ❌ 禁止建立 `daf_user` 表或保存口令、刷新令牌。
- ❌ 禁止在日志打印令牌和核验响应全文。
- ❌ 解耦红线：本模块不得实现租户硬过滤算法，不得出现 gnex 拦截器类名。只消费核验结果并设置本库会话变量。

## §7 装配与配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| daf.bridge.introspect-timeout-ms | 见 LIT-C-INTROSPECT-MS | 核验超时 |
| daf.security.platform-role | daf-platform-admin | 令牌 roles 包含此名才是平台管理员 |
| daf.security.permit | `/api/daf/v1/health` | 唯一匿名路径 |

移除本模块则整个服务拒绝启动（`core`）。

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-IDN-CTX-01 | 无凭证 | 服务已启动 | GET /api/daf/v1/apps/demo 不带头 | HTTP 401 且 code 为 RC-AUTH-MISSING | L1 | FR-IDN-01 | JUnit MockMvc | 待实现 |
| ACC-IDN-CTX-02 | 声明缺失 | 桥接返回没有 projectId | 同上带 Bearer | HTTP 401 且 code 为 RC-AUTH-INCOMPLETE | L3 | FR-IDN-01 | JUnit | 待实现 |
| ACC-IDN-CTX-03 | 租户不一致 | 令牌 tid=A。分别用查询参数、JSON 正文带 tenantId=B | GET 或 POST | 两种都是 HTTP 403，code 为 RC-TENANT-MISMATCH，details 为 {} | L4 | FR-IDN-05 | JUnit | 待实现 |
| ACC-IDN-CTX-04 | 连接复用 | 请求 A 绑定租户 A、项目 P1 并结束 | 同一连接执行请求 B | 两个会话变量等于 B 与其项目，查询不到 A 的行，也查不到同租户另一项目的行 | L4 | NFR-01 | Testcontainers | 待实现 |
| ACC-IDN-CTX-05 | 核验超时 | 桥接挂起超过配置 | 任意业务 GET | HTTP 401，且桥接只被调用 1 次 | L3 | FR-IDN-01 | JUnit | 待实现 |

### 运行命令

```bash
mvn -pl daf-idn,daf-api test -Dtest=TenantContextFilterTest,TenantContextPoolTest
```

## §9 影响域

影响全部业务请求。故障时写和读一起拒绝。不改变 gnex 登录。多副本无本地状态。

## §10 后续

不在首版做令牌主动吊销列表。吊销以 gnex 核验结果为准。若核验结果滞后，记入桥接规格的缺口，不在本模块缓存黑名单。

