# spec-ui-schema

> 来源：FR-UI-01、FR-UI-02、FR-UI-04、FR-UI-05、FR-UI-06；决策 D-004、D-011
> 模块 ID：ui-schema｜能力域：界面｜保护级别：core｜宿主：daf-ui｜实现状态：可开发

## §1 业务目标

页面要能存、能校验、能换渲染器。正式格式只描述布局、区块、绑定和动作，不描述某个组件库的私有属性。校验失败的页面不能被读接口返回给外壳。对应 FR-UI-01、FR-UI-02、FR-UI-04、FR-UI-05、FR-UI-06。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-UI-01、FR-UI-02、FR-UI-04、FR-UI-05、FR-UI-06、D-004、D-011 |
| 对标借鉴 | Amis 证明 JSON 能画中后台。正式格式不采用 Amis |
| 技术选型 | 选基座页面模式（JSON Schema 描述）。备选是存储 Amis schema。不选的反例：历史页面全是 Amis 的 type 名，换渲染器要迁移全部生产页面。复决：官方渲染器对第一批两个业务系统的标准页覆盖低于 80% 时，允许增加 Amis 适配器，正式格式字段保持不变 |
| 改造要点 | 保存即校验。只把通过的版本标为可读。模式内禁止脚本和连接串 |

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| ui.schema | `PageSchema save(String appKey, String pageKey, JsonNode body)` | 失败抛 RC-SCHEMA-INVALID，不写新版本 |
| ui.schema | `PageSchema read(String appKey, String pageKey)` | 只返回已通过版本 |

### 3.2 REST

GET 与 PUT /apps/{appKey}/pages/{pageKey}。应用已发布时 PUT 一律 409，文案 `RC-APPROVAL-REQUIRED`。

### 3.3 事件

无。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| dat.entity | 必选 | 绑定的实体和字段必须存在 | 校验失败 |
| sem.assemble | 可选 | 已发布应用需要事务号 | 无事务号则 409 |

## §4 数据模型

表 `daf_page`：tenant_id、app_key、page_key、version、body_json、valid、created_at。主键 (tenant_id, app_key, page_key, version)。读接口取 valid=true 的最大 version。

body 结构：

```json
{"layout":"stack","blocks":[{"type":"table","entity":"order","columns":["no","title"]}],"actions":[{"type":"create","entity":"order"}]}
```

type 只能是字面量库的六种区块。actions.type 只能是四个动作。不得出现 `script`、`js`、`sql`、`credential` 键。

## §5 实现思路

保存步骤：解析 JSON → 拒绝未知顶层键 → 检查区块类型 → 检查实体归属同一应用 → 检查列名属于该实体 → 插入 version+1 且 valid=true。任一步失败，不插入。

读路径不做第二次全量校验。若磁盘上的 JSON 被人工改坏，读出时返回 422 并告警。首版不加远程缓存。核验不可用时请求在过滤器被拒绝，不会读到页面。

## §6 边界

- ✅ 非法模式不得把 valid 写成 true。
- ❌ 禁止在 body 中存放可执行字符串。
- ❌ 解耦红线：JSON 不得包含 amis 的 `type: crud` 等私有类型名。适配器若存在，只在渲染器进程内转换。

## §7 装配与配置

无。

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-UI-01 | 合法表格 | 实体 order 有 no、title | PUT 表格区块 | 200 且 GET 返回同一 version | L1 | FR-UI-01 | MockMvc | 待实现 |
| ACC-UI-02 | 未知区块 | type=kanban | PUT | 422 RC-SCHEMA-INVALID 且 GET 仍是旧版 | L3 | FR-UI-02 | JUnit | 待实现 |
| ACC-UI-03 | 脚本键 | body 含 script | PUT | 422 且库中无新行 | L4 | D-011 | JUnit | 待实现 |
| ACC-UI-04 | 未知键 | body 含 credential | PUT | 422 且库中无新行 | L4 | FR-UI-05 | JUnit | 待实现 |
| ACC-UI-05 | 已发布直写 | 应用 PUBLISHED，即使带组装号 | PUT | 409 RC-APPROVAL-REQUIRED | L4 | FR-SEM-04 | MockMvc | 待实现 |
| ACC-UI-06 | 读性能 | 已有合法页 | 连续 GET | 本地测试 p95 不超过 LIT-C-SCHEMA-P95-MS。此项只在带数据库的测试记录，不作为没有索引时的口头保证 | L2 | NFR-02 | JUnit 计时 | 待实现 |

### 运行命令

```bash
mvn -pl daf-ui test -Dtest=PageSchemaStoreTest
```

## §9 影响域

坏页面只影响该页。其他页面 version 独立。

## §10 后续

不做自由画布坐标。布局只有 stack（纵向）一种。
