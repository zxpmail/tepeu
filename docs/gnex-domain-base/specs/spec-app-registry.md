# spec-app-registry

> 来源：01-需求分析.md FR-APP-01、FR-APP-02；06 总体设计应用状态
> 模块 ID：app-registry｜能力域：应用｜保护级别：core｜宿主：daf-app｜实现状态：可开发

## §1 业务目标

应用是业务系统在租户里的身份。没有应用，页面和实体无处挂靠。本模块负责创建应用、保证租户内键唯一、执行状态迁移。发布后业务用户才能走路由。对应 FR-APP-01、FR-APP-02。安装标准包后应用应为已发布，该动作由安装模块调用本模块，不由用户跳步。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-APP-01、FR-APP-02、NFR-08 |
| 对标借鉴 | Frappe 站点里的应用列表。隔离粒度改成租户行，不另开数据库 |
| 技术选型 | 选单表加状态机。备选是每个应用一个数据库。不选的反例：租户内 30 个小应用就要 30 个库，备份和行级租户策略无法共用，和 gnex 一个租户一套令牌对不上。复决：若单个应用的托管表超过 200 张或需要独立恢复时间目标，允许该应用登记「独占模式」，本决定对独占应用重开 |
| 改造要点 | 状态迁移集中在一个方法。其他模块禁止直接 update state 列 |

决策 D-001、D-003。

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| app.registry | `App create(String appKey, String name)` | 初始状态 DRAFT |
| app.registry | `App require(String appKey)` | 不存在抛出映射为 RC-APP-NOT-FOUND |
| app.registry | `App transit(String appKey, String target)` | 非法迁移映射为 RC-APP-STATE |

### 3.2 REST

POST /api/daf/v1/apps；GET /api/daf/v1/apps/{appKey}；POST .../publish；POST .../suspend；POST .../retire。字段见接口设计。

### 3.3 事件

| 事件名 | 方向 | 载荷要点 |
|---|---|---|
| daf.app.published | 发出 | 公共载荷 |
| daf.app.suspended | 发出 | 公共载荷 |

退役不另造事件，审计走 outbox 文本 `retire`。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| idn.context | 必选 | 租户 | 拒绝 |
| brg.gnex | 必选 | 发事件与审计 | 事件写入 outbox |

## §4 数据模型

表 `daf_app`：tenant_id、app_key、name、state、version、created_at、updated_at。主键 (tenant_id, app_key)。state 检查约束仅允许字面量库中的四个值。version 为整数，从 1 起，每次发布加 1。

## §5 实现思路

允许的迁移：DRAFT→PUBLISHED、PUBLISHED→SUSPENDED、SUSPENDED→PUBLISHED、DRAFT→RETIRED、PUBLISHED→RETIRED、SUSPENDED→RETIRED。其他返回 409，文案 RC-APP-STATE。

发布时若该应用没有任何已发布路由，仍允许发布，外壳显示空菜单。这是为了先发布再装页面的安装顺序后半段。安装模块在同一事务里写路由再发布，用户不会看见空菜单。`[设计·依据]` 安装与恢复设计第 2 节。

应用键正则 `^[a-z][a-z0-9]{1,31}$`。中文只放 name。

## §6 边界

- ✅ 状态只能经 transit 改变。
- ✅ 查询必须带当前租户。跨租户得到 404 而不是 403，避免探明其他租户是否占用了同一个键。同一租户内键冲突才是 409 RC-PACKAGE-CONFLICT。
- ❌ 禁止物理删除应用行。退役即终点。
- ❌ 解耦红线：不得注册为 gnex agent。应用不是智能体。

## §7 装配与配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| daf.app.key-max | 32 | 与正则一致 |

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-APP-01 | 创建 | 管理员令牌 | POST /apps appKey=demo | 201 state=DRAFT | L1 | FR-APP-01 | MockMvc | 待实现 |
| ACC-APP-02 | 键重复 | 已有 demo | 再 POST | 409 RC-PACKAGE-CONFLICT | L3 | FR-APP-01 | MockMvc | 待实现 |
| ACC-APP-03 | 退役再发布 | state=RETIRED | POST publish | 409 RC-APP-STATE | L4 | FR-APP-02 | JUnit | 待实现 |
| ACC-APP-04 | 停用再恢复 | PUBLISHED 后 suspend | POST publish | 200 state=PUBLISHED 且 version 比停用前大 1 | L2 | FR-APP-02 | JUnit | 待实现 |
| ACC-APP-05 | 他租户 | 租户 B 已有 demo | 租户 A GET demo | 404 RC-APP-NOT-FOUND | L4 | NFR-01 | Testcontainers | 待实现 |

### 运行命令

```bash
mvn -pl daf-app test -Dtest=AppRegistryTest
```

## §9 影响域

应用状态错误会使整包路由不可用，不影响其他应用，不影响 gnex 循环。

## §10 后续

不做应用克隆。不做跨租户分享应用。
