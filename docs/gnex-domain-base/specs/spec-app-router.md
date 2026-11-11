# spec-app-router

> 来源：FR-APP-03 至 FR-APP-06；决策 D-013
> 模块 ID：app-router｜能力域：应用｜保护级别：core｜宿主：daf-app｜实现状态：可开发

## §1 业务目标

用户打开的路径必须落到唯一页面。菜单从这些路径生成，避免两套导航。停用的应用不能借路由漏到别的应用。对应 FR-APP-03、FR-APP-04、FR-APP-05、FR-APP-06。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-APP-03、FR-APP-04、FR-APP-05、FR-APP-06 |
| 对标借鉴 | 管理端信息架构：路径即菜单。不引入第二份菜单表 |
| 技术选型 | 选路由表加查询投影。备选是独立菜单 JSON。不选的反例：停用路由后菜单仍显示，用户点进去才 404，培训和权限审计对不上。复决：若产品要求菜单分组且分组无法用路径前缀表达（同一路径要出现在两个分组），再增加可选 group 字段，仍不建第二份可编辑菜单 |
| 改造要点 | 唯一约束 (tenant_id, app_key, path)。菜单查询只取 published=true 且应用为 PUBLISHED |

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| app.router | `Route upsert(String appKey, String path, String pageKey, boolean published)` | 路径冲突且不是同一条则 409 |
| app.router | `List<MenuItem> menu(String appKey)` | 仅已发布。应用非 PUBLISHED 且调用者不是 app-admin 时 403，文案 RC-PERM-DENIED |
| app.router | `Route resolve(String appKey, String path)` | 停用或退役且调用者不是 app-admin 时 403，文案 RC-PERM-DENIED |

### 3.2 REST

GET /apps/{appKey}/routes；PUT /apps/{appKey}/routes/{routeId}。见接口设计。

### 3.3 事件

无独立事件。发布应用时菜单自然变化。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| app.registry | 必选 | 应用状态 | 应用不存在则 404 |
| ui.schema | 必选 | pageKey 必须已存在 | 否则 422 RC-SCHEMA-INVALID |
| idn.grant | 必选 | 管理员才能改路由 | 403 |

## §4 数据模型

表 `daf_route`：tenant_id、app_key、route_id、path、page_key、published、title。主键 (tenant_id, app_key, route_id)。唯一 (tenant_id, app_key, path)。path 以 `/` 开头，不含域名，不含 `..`。

## §5 实现思路

菜单项字段只有 title、path、pageKey。排序按 path 字典序。`[设计·惯例]` 首版不做拖拽排序，避免又一份顺序数据。

resolve 时若应用 SUSPENDED 或 RETIRED，非应用管理员得到 403，文案 `RC-PERM-DENIED`。应用管理员仍可用 GET routes 读配置。非法状态迁移仍是 409，文案 `RC-APP-STATE`，由应用注册模块返回。

## §6 边界

- ✅ 菜单查询不得返回未发布路由。
- ❌ 禁止菜单表。
- ❌ 禁止路径指向其他应用的 pageKey。
- ❌ 解耦红线：路由不是 gnex 前端内核路由。不得复用 gnex client-kernel 的会话页路径。

## §7 装配与配置

无额外配置。

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-RT-01 | 路径唯一 | 已有 /orders | 再写入 /orders 指向另一页 | 409 | L3 | FR-APP-03 | JUnit | 待实现 |
| ACC-RT-02 | 菜单投影 | 一条 published 一条否，应用已发布 | GET shell 的菜单 | 只含已发布那条 | L2 | FR-APP-04 | MockMvc | 待实现 |
| ACC-RT-03 | 停用 | 应用 SUSPENDED，调用者不是 app-admin | resolve /orders | HTTP 403，code 为 RC-PERM-DENIED，正文无页面 | L4 | FR-APP-05 | JUnit | 待实现 |
| ACC-RT-04 | 草稿不可见 | 应用 DRAFT，调用者是普通成员 | GET routes | 403 RC-PERM-DENIED | L3 | FR-APP-06 | MockMvc | 待实现 |
| ACC-RT-05 | 管理员可见草稿 | 应用 DRAFT，调用者是应用管理员 | GET routes | 200 且含未发布路由 | L2 | FR-APP-06 | MockMvc | 待实现 |

### 运行命令

```bash
mvn -pl daf-app test -Dtest=AppRouterTest
```

## §9 影响域

只影响该应用的导航和打开页面。错误路由不会串到其他应用，因为解析条件含 appKey 与 tenant_id。

## §10 后续

不做路径参数（如 /orders/{id}）。详情用查询参数 id，页面仍是详情区块。
