# spec-dat-entity

> 来源：FR-DAT-03、FR-DAT-04、FR-DAT-07
> 模块 ID：dat-entity｜能力域：数据｜保护级别：core｜宿主：daf-dat｜实现状态：可开发

## §1 业务目标

实体是业务单据的结构说明：有哪些字段、落在哪个源。托管实体由基座建表。外接实体只记录映射，不改对方表结构。每张实体都隐含租户列。对应 FR-DAT-03、FR-DAT-04、FR-DAT-07。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-DAT-03、FR-DAT-04、FR-DAT-07 |
| 对标借鉴 | Frappe DocType 的元数据建表。不采用其全库共享且无租户列的默认 |
| 技术选型 | 选元数据表加托管 DDL。备选是全部外接、基座不建表。不选的反例：标准包没有客户库就装不了，60 秒安装目标无法演示。复决：若企业规定业务行不得进入基座库，可关闭托管，标准包改为必须带外接源，并改写 NFR-03 的标准包定义 |
| 改造要点 | 字段类型只用字面量库。托管建表带 tenant_id NOT NULL。外接映射要求源上存在租户列或 REST 模板强制带租户参数 |

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| dat.entity | `Entity define(String appKey, EntityDraft draft)` | 校验后写入。托管则执行 DDL |
| dat.entity | `Entity require(String appKey, String entityKey)` | 不存在则 404 |

### 3.2 REST

POST /apps/{appKey}/entities。

### 3.3 事件

无单独事件。随安装或组装事务的事件一起出现。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| dat.source | 必选 | 源必须 UP | 否则 RC-SOURCE-DOWN |
| app.registry | 必选 | 应用归属 | 404 |

## §4 数据模型

`daf_entity`：tenant_id、app_key、entity_key、source_key、storage（MANAGED 或 MAPPED）、physical_name、tenant_column。

`daf_field`：tenant_id、app_key、entity_key、field_key、field_type、required、enum_values。

托管物理表名 `t_<租户短码>_<实体键>`。短码是租户标识的小写截断加校验位，长度 8，只含字母数字。物理表列包含 `tenant_id`、`project_id` 与各字段。主键 (tenant_id, project_id, id)，id 为 bigint 自增。

外接实体不执行改表。若映射提供了租户列名，查询可以下推该列。若没有，定义仍然允许，但规格不得把对方库描述成已有行级隔离。隔离验收改测「其他租户不能读到这条数据源登记」。

## §5 实现思路

define 在一个事务：写元数据，若 MANAGED 则 `CREATE TABLE`。DDL 失败则元数据回滚。MAPPED 不执行 DDL。MAPPED 不要求源上存在租户列。

禁止字段键 `tenant_id`、`project_id`、`id`，这三个由系统占有。

标准包字段数不超过 `LIT-C-ENTITY-FIELDS`，超过仍允许创建，但不计入标准包计时口径。

## §6 边界

- ✅ 托管表必须有 tenant_id。
- ❌ 禁止对 MAPPED 源执行 ALTER。
- ❌ 解耦红线：实体键不是 gnex 记忆对象。不得写入企业记忆。

## §7 装配与配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| daf.dat.allow-managed | true | false 时拒绝 MANAGED |

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-EN-01 | 托管建表 | 托管源 UP | 定义 2 个字段的实体 | 元数据与物理表都存在，表含 tenant_id 与 project_id | L1 | FR-DAT-04 | Testcontainers | 待实现 |
| ACC-EN-02 | 失败回滚 | DDL 被测试桩抛错 | 定义 | daf_entity 无该行 | L4 | FR-PKG-02 | JUnit | 待实现 |
| ACC-EN-03 | 外接不改表 | MAPPED | 定义 | 连接器 alter 次数为 0 | L3 | FR-DAT-04 | JUnit | 待实现 |
| ACC-EN-04 | 非法类型 | field_type=json | 定义 | 422 | L3 | FR-DAT-03 | JUnit | 待实现 |
| ACC-EN-05 | 外接无租户列 | MAPPED 且不填租户列 | 定义 | 201。查询规格不得为此拼接 tenant_id。其他租户 GET 该数据源为 404 | L4 | FR-DAT-04 | JUnit | 待实现 |

### 运行命令

```bash
mvn -pl daf-dat test -Dtest=EntityCatalogTest
```

## §9 影响域

建表失败不影响其他实体。物理表留在 biz 模式，不放进 daf 模式。

## §10 后续

不做实体继承。不做跨应用外键。ref 类型首版只保存目标 id，不级联删除。
