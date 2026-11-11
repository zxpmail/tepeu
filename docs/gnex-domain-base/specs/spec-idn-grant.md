# spec-idn-grant

> 来源：01-需求分析.md、06-总体设计、specs/00-contracts.md
> 模块 ID：idn-grant｜能力域：身份｜保护级别：core｜宿主：daf-idn｜实现状态：可开发


## §1 业务目标

组织里的人进入某个应用时，还要有应用成员和应用角色。角色决定能对哪个实体做哪种动作，以及行过滤、字段打码。目标是查询编译前就能得到一份不可被调用方改写的权限条件。对应 FR-IDN-02、FR-IDN-03、FR-IDN-04、FR-IDN-06。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-IDN-02、FR-IDN-03、FR-IDN-04、FR-IDN-06、NFR-04 |
| 对标借鉴 | Directus 用条目过滤做权限。过滤表达式我们收得更窄 |
| 技术选型 | 选「角色—权限点—行策略」三张表。备选是把策略写成脚本。不选的反例：脚本能调用网络或拼 SQL，租户条件会被覆盖。复决：若两个真实业务系统出现字段间四则运算比较（不是等值、比较、枚举），把表达式版本号升到 v2，本决定重开 |
| 改造要点 | 权限点三段式。行策略编译成与租户条件 AND 在一起的谓词。变更写审计 |

决策 D-002、D-011。

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| idn.grant | `void replaceMember(String appKey, String uid, List<String> roleKeys)` | 空角色列表表示移除成员 |
| idn.grant | `GrantDecision decide(String appKey, String entityKey, String action)` | 返回允许与否、行谓词、打码字段。拒绝时不返回数据 |

### 3.2 REST

与接口设计第 2 节一致：`PUT /apps/{appKey}/members/{uid}`、`GET/PUT /apps/{appKey}/roles/{roleKey}`。

### 3.3 事件

成员或角色变更后经 `brg.gnex` 投递审计。不新造事件名。投递失败走 outbox，见安装与恢复设计。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| idn.context | 必选 | 当前用户 | 无上下文不授权 |
| app.registry | 必选 | 应用存在且属于本租户 | 应用不存在则 RC-APP-NOT-FOUND |
| brg.gnex | 必选 | 审计 | 业务变更仍提交，审计进行 outbox |

## §4 数据模型

| 表 | 字段 | 约束 |
|---|---|---|
| daf_member | tenant_id, project_id, app_key, uid, created_at | 主键 (tenant_id, project_id, app_key, uid)，列非空 |
| daf_role | tenant_id, project_id, app_key, role_key, name, mask_fields | 主键 (tenant_id, project_id, app_key, role_key)。mask_fields 为 jsonb，缺省 `[]` |
| daf_member_role | tenant_id, project_id, app_key, uid, role_key | 主键含这五列。分别指向成员和角色 |
| daf_role_perm | tenant_id, project_id, app_key, role_key, perm | 主键含 perm。perm 最长 128 |
| daf_row_policy | tenant_id, project_id, app_key, role_key, entity_key, expr_json | expr_json 为 jsonb。field 必须是该实体已有字段 |

`expr_json` 只允许：`{"op":"eq|ne|lt|le|gt|ge|in","field":"...","value":...}` 或 `{"op":"and|or","items":[...]}`。`value` 可以是常量或 `{"from":"uid"}`。

## §5 实现思路

1. 替换成员在一个事务里完成：删除旧的 daf_member_role，插入新关联。角色列表为空则删除成员行及其关联。事务内只插入一条 outbox，不访问 gnex。
2. `decide` 读取该用户全部角色的权限点并集。没有 `应用.实体.动作` 则拒绝，文案 RC-PERM-DENIED。保留角色 `app-admin` 表示应用管理员，可以改成员和路由，仍不能靠这个角色跳过实体权限点去读业务行。
3. 行策略取各角色策略的 OR，再与租户条件、项目条件 AND。
4. 打码字段取各角色 `mask_fields` 的交集。平台管理员不做打码。`[设计·惯例]`
5. 权限点正则见接口设计。不匹配则 422，文案 RC-SCHEMA-INVALID。
6. PUT 角色的正文必须同时带 `perms`、`maskFields`、`rowPolicies`，三项都按全量替换。缺任一项则 422。

## §6 边界

- ✅ 必须把租户条件留在谓词最外层 AND，调用方过滤不能删除它。
- ✅ 成员变更必须同一事务写 outbox。
- ❌ 禁止用 gnex 组织角色直接放行业务实体。平台管理员只看令牌 `roles` 是否包含配置的平台角色名。没有该角色就没有平台管理员。
- ❌ 解耦红线：不得调用 gnex 组织管理的写接口。组织数据只读且可选。

## §7 装配与配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| daf.grant.cache-ms | 0 | 首版不缓存授权，避免停用后仍放行 |

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-IDN-GR-01 | 非成员 | 用户无成员行 | 查询实体 | 403 RC-PERM-DENIED | L1 | FR-IDN-02 | JUnit | 待实现 |
| ACC-IDN-GR-02 | 有读权限 | 角色含 demo.order.read | 查询 | 200 | L2 | FR-IDN-03 | JUnit | 待实现 |
| ACC-IDN-GR-03 | 行过滤 | 策略 eq created_by uid | 查询 | SQL 谓词同时含 tenant_id 与 created_by，结果不含他人行 | L4 | FR-IDN-04 | Testcontainers | 待实现 |
| ACC-IDN-GR-04 | 非法权限点 | 提交 `read` 单段 | PUT 角色 | 422 RC-SCHEMA-INVALID | L3 | FR-IDN-03 | MockMvc | 待实现 |
| ACC-IDN-GR-05 | 审计失败仍可追溯 | 工作器尚未投递 | 替换成员 | 成员已更新且 outbox 有一行，事务内对 gnex 的调用次数为 0 | L3 | FR-IDN-06 | JUnit | 待实现 |
| ACC-IDN-GR-06 | 移除成员 | roleKeys 为空数组 | PUT 成员 | 再次查询 403 | L2 | FR-IDN-02 | MockMvc | 待实现 |

### 运行命令

```bash
mvn -pl daf-idn test -Dtest=AppGrantServiceTest
```

## §9 影响域

影响该应用内全部读写。故障为拒绝访问，不扩大数据可见范围。

## §10 后续

不做权限委托（把自己的权限转给别人）。不做按时间生效的临时授权。

