# spec-dat-query

> 来源：FR-DAT-05 至 FR-DAT-08；决策 D-005、D-016
> 模块 ID：dat-query｜能力域：数据｜保护级别：core｜宿主：daf-dat｜实现状态：可开发

## §1 业务目标

页面和智能体读写信件式数据时，只能走实体查询和实体命令。单源能下推。跨源超过行数上限就拒绝。任意 SQL 文本一律拒绝。对应 FR-DAT-05、FR-DAT-06、FR-DAT-07、FR-DAT-08、NFR-01。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-DAT-05、FR-DAT-06、FR-DAT-07、FR-DAT-08、NFR-01、D-005、D-016 |
| 对标借鉴 | Hasura 连接器下推。首版不做其分布式查询引擎 |
| 技术选型 | 选自研窄查询（过滤、排序、分页、单实体）。备选是嵌入 Calcite。不选的反例：Calcite 计划生成失败时错误信息含内部表名和片段，且团队要维护一套优化器才能保住 60 秒安装这种与查询无关的目标。复决：见 D-005，一个月内合法页面因跨源上限被拒不少于 10 次 |
| 改造要点 | 谓词 = 租户条件 AND 权限谓词 AND 用户过滤。用户过滤不能替换前两段。命令走钩子 |

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| dat.query | `QueryResult query(String appKey, String entityKey, Query body)` | pageSize 超过上限则 422 |
| dat.command | `CommandResult execute(String appKey, String entityKey, String action, JsonNode payload)` | 无权限则 403。钩子拒绝则 RC-HOOK-FAILED |

### 3.2 REST

POST /apps/{appKey}/entities/{entityKey}/query；POST .../commands。查询响应含 rows、total、totalKnown。

### 3.3 事件

命令成功后审计，不把 payload 全文放进事件。只放实体键、动作、主键。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| idn.grant | 必选 | 谓词与打码 | 无授权模块则拒绝全部查询 |
| dat.entity | 必选 | 结构 | 404 |
| dat.source | 必选 | 连接器 | DOWN 则 RC-SOURCE-DOWN |
| pkg.hooks | 可选 | 写前 HTTP 钩子 | 无钩子则只做类型与必填校验 |

## §4 数据模型

无新表。查询不落库。命令写业务表。

## §5 实现思路

1. 若 filter 含键 `sql`，或去掉首尾空白后以 `select`、`insert`、`update`、`delete` 开头，返回 422，文案 `RC-DIRECT-SQL`。过滤只能是与行策略相同的 expr_json。
2. 托管单源：谓词译成参数化 SQL，值为参数。顺序是租户 AND 项目 AND 权限 AND 用户过滤。
3. 外接源：登记行已经按令牌租户隔离。源映射写了租户列才下推该列。没写则不拼接，也不把 PostgreSQL 行级策略说成对方库的能力。另一租户的令牌读不到这条数据源登记。
4. 排序字段必须是该实体字段。分页从 1 开始。未传页大小用 `LIT-C-PAGE-SIZE-DEFAULT`。超过 `LIT-C-QUERY-ROW-CAP` 则 422。
5. 打码字段在返回前替换为 `****`。
6. 跨源仅当页面显式写了两个实体的等值关联。先计数，超过 `LIT-C-JOIN-ROW-CAP` 或无法计数则 `RC-JOIN-CAP`，不访问第二源。
7. 命令：校验必填和类型。若应用登记了 HTTP 钩子，则调用 `beforeCommand(appKey, entityKey, action, payload)` 所对应的 HTTP，返回不允许则整单不写，文案 `RC-HOOK-FAILED`。然后连接器写入。托管表的更新和删除带租户和项目。

单源且不超过一页时，时限目标为 `LIT-C-QUERY-P95-MS`。

## §6 边界

- ✅ 租户谓词不可关闭。
- ❌ 禁止把用户输入拼进 SQL。
- ❌ 解耦红线：查询服务不得打开 gnex 会话库。

## §7 装配与配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| daf.dat.page-size-max | 见 LIT-C-QUERY-ROW-CAP | 单页上限 |
| daf.dat.join-cap | 见 LIT-C-JOIN-ROW-CAP | 跨源估算上限 |

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-Q-01 | 分页 | 托管实体有数据 | pageSize=20 | 200 且 SQL 为参数化 | L1 | FR-DAT-05 | Testcontainers | 待实现 |
| ACC-Q-02 | 超页 | pageSize 等于 LIT-C-QUERY-ROW-CAP 加 1 | 查询 | 422 | L3 | FR-DAT-05 | JUnit | 待实现 |
| ACC-Q-03 | 任意 SQL | filter 含键 sql，值为 delete from t | 查询 | 422 RC-DIRECT-SQL | L4 | FR-DAT-08 | JUnit | 待实现 |
| ACC-Q-04 | 串租户与串项目 | A、B 各有一行；同租户另有项目 P2 的一行 | 用 A 的 P1 查询 | 结果只有 A 且项目为 P1 | L4 | NFR-01 | Testcontainers | 待实现 |
| ACC-Q-05 | 跨源超限 | COUNT 返回 10001 | 跨源查询 | RC-JOIN-CAP 且第二源调用 0 次 | L4 | FR-DAT-06 | JUnit | 待实现 |
| ACC-Q-06 | 钩子拒绝 | 钩子返回拒绝 | create | 行数不变，code=RC-HOOK-FAILED | L3 | FR-PKG-05 | JUnit | 待实现 |
| ACC-Q-07 | 打码 | 字段 phone 在打码集 | 查询 | 该字段值为 `****` | L2 | FR-IDN-04 | JUnit | 待实现 |

### 运行命令

```bash
mvn -pl daf-dat test -Dtest=EntityQueryTest,EntityCommandTest
```

## §9 影响域

慢查询由页大小和跨源拒绝封顶。单实体命令失败不写半行。

## §10 后续

不做聚合报表。不做用户自定义 SQL 视图。
