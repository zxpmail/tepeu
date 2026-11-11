# spec-svc-catalog

> 来源：FR-SVC-01 至 FR-SVC-05；决策 D-012、D-014
> 模块 ID：svc-catalog｜能力域：服务｜保护级别：standard｜宿主：daf-svc｜实现状态：可开发

## §1 业务目标

业务系统除了表，还会调用已有的业务接口。这些接口要登记版本和健康，并绑定到应用。它们不是 gnex 进程里的服务键。对应 FR-SVC-01 至 FR-SVC-05。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-SVC-01、FR-SVC-02、FR-SVC-03、FR-SVC-04、FR-SVC-05 |
| 对标借鉴 | Backstage 的「登记 + 健康」。不采用其每租户一套实例 |
| 技术选型 | 选自建目录表。备选是直接把 Backstage 嵌进来。不选的反例：Backstage 插件默认共库且官方多租户路径是每组织一套进程，会把研发目录和业务单据绑在一个升级节奏里。复决：若企业已有 Backstage 且要求同一目录给研发和业务同时看，再做只读同步，不把 Backstage 当基座的写入源头 |
| 改造要点 | 服务键强制前缀 `biz.`。密钥只存引用。健康 DOWN 时调用返回 RC-SOURCE-DOWN |

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| svc.catalog | `BizService register(String serviceKey, String version, String protocol, String endpointRef, String credentialRef)` | serviceKey 必须以 biz. 开头 |
| svc.catalog | `void bind(String appKey, String serviceKey, String version)` | 写入绑定历史 |
| svc.catalog | `Health probe(String serviceKey)` | 更新 UP、DEGRADED、DOWN |
| svc.catalog | `InvokeResult invoke(String appKey, String serviceKey, Map<String,Object> input)` | DOWN 时不发起下游调用 |

### 3.2 REST

POST /api/daf/v1/services；POST /services/{serviceKey}/probe；PUT /apps/{appKey}/service-bindings。

### 3.3 事件

无。健康变化记审计。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| brg.gnex | 必选 | 解析 credentialRef | 探测失败，状态 DOWN |
| app.registry | 必选 | 绑定前应用存在 | 404 |

## §4 数据模型

`daf_biz_service`：tenant_id、service_key、version、protocol、endpoint_ref、credential_ref、health、checked_at。主键 (tenant_id, service_key, version)。

`daf_service_binding`：tenant_id、app_key、service_key、version、bound_at。追加写，不覆盖历史。当前绑定是某应用下 bound_at 最大的一行。

protocol 首版只允许 `HTTP`。

## §5 实现思路

register 若 serviceKey 不以 `biz.` 开头，422 RC-SCHEMA-INVALID。若与契约索引里的 gnex 保留名相同（tenant、auth、agentLoop 等），同样拒绝。

probe 对 endpoint 发 GET，超时 LIT-C-PROBE-MS。2xx 为 UP，5xx 为 DOWN，超时为 DOWN。不跟随外网重定向超过 1 次。

invoke 在 DOWN 或缺少当前绑定时返回 RC-SOURCE-DOWN，HTTP 503。DEGRADED 仍调用，响应头 `X-Daf-Degraded: true`。

## §6 边界

- ✅ 响应和日志不得出现 credential 的解析结果。
- ❌ 禁止把服务键注册进 gnex Ctx。
- ❌ 解耦红线：本模块不实现智能体工具管线。invoke 是业务调用，不是工具调用。

## §7 装配与配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| daf.svc.probe-ms | 2000 | 与字面量库一致 |
| daf.svc.allowed-protocols | HTTP | 逗号分隔。首版仅 HTTP |

移除本模块时，应用仍可用托管实体。安装清单若含 services 段则安装失败并说明模块未启用。

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-SVC-01 | 前缀 | 管理员 | 注册键 order | 422 | L3 | FR-SVC-02 | JUnit | 待实现 |
| ACC-SVC-02 | 合法登记 | 键 biz.order | 注册 | 201 health 空则视为 DOWN 直到探测 | L1 | FR-SVC-01 | JUnit | 待实现 |
| ACC-SVC-03 | 绑定历史 | 已绑定 v1 | 再绑定 v2 | 两行都在，当前为 v2 | L2 | FR-SVC-03 | JUnit | 待实现 |
| ACC-SVC-04 | 不可用 | health=DOWN | invoke | 503 code=RC-SOURCE-DOWN 且下游 0 次调用 | L4 | FR-SVC-04 | JUnit | 待实现 |
| ACC-SVC-05 | 无密钥字段 | 请求体含 password | 注册 | 422，库中无该列写入 | L4 | FR-SVC-05 | MockMvc | 待实现 |

### 运行命令

```bash
mvn -pl daf-svc test -Dtest=BizServiceCatalogTest
```

## §9 影响域

一个服务 DOWN 只影响绑定它的应用调用。不改变实体查询。

## §10 后续

不做 gRPC。不做服务网格流量切分。
