---
name: 腾讯云TCHouse-D 智能建表与数据建模
description: >
  TCHouse-D（Doris）智能建表与数据建模 Skill。AI 根据用户描述的业务场景（日增数据量、查询模式、数据保留周期等），推荐合适的数据模型（DUPLICATE/AGGREGATE/UNIQUE），设计分区策略、分桶策略与排序键，生成完整的 DDL 语句及设计理由说明。支持新建表设计、MySQL 迁移方案、现有表结构优化诊断。
  触发词：建表、DDL、CREATE TABLE、表设计、表结构、数据建模、分区策略、分桶、Bucket、排序键、ORDER BY、PARTITION BY、DISTRIBUTED BY、数据模型、Duplicate、Unique、Aggregate、聚合模型、明细模型、主键模型、schema设计、索引设计、Bloom Filter、倒排索引、INVERTED INDEX、主键设计、分区键、TTL、动态分区、DYNAMIC PARTITION、数据保留、数据过期、宽表、维度表、事实表、日志表、订单表、用户表、MySQL迁移、迁移方案、表结构优化、Doris建表、TCHouse-D建表、cdwdoris、Colocate。
  本 Skill 包含 4 个子能力：①数据模型推荐 ②分区/分桶/排序键设计 ③完整 DDL 生成（含执行指引） ④现有表结构优化诊断。
  何时不触发：慢 SQL 诊断与自动调优（已有 SQL 的性能分析）、NL2SQL 数据分析查询、集群健康诊断与故障排查、集群选型与架构推荐、集群扩缩容操作、权限管理、数据导入导出等非建表/数据建模相关问题不走本 Skill。
allowed-tools:
  - TCHouseDDescribeInstances
  - TCHouseDDescribeInstance
  - TCHouseDDescribeDDL
  - TCHouseDDescribeClusterConfigs
  - ask_user # WorkBuddy 中为 AskUserQuestion
---

# 智能建表与数据建模

## 概述

本 Skill 提供 TCHouse-D（Doris）集群的智能建表与数据建模能力，包含四个子能力：

1. **数据模型推荐**：根据业务场景（是否需要去重、更新、聚合）推荐最合适的 Doris 数据模型（DUPLICATE/UNIQUE/AGGREGATE）
2. **分区/分桶/排序键设计**：根据日增数据量、查询模式、数据保留周期设计最优分区/分桶/Key 方案
3. **完整 DDL 生成（含执行指引）**：生成可直接使用的 CREATE TABLE 语句，并输出到 SQL 工作区手动执行的操作指引
4. **现有表结构优化诊断**：分析已有表的 DDL，发现设计问题并给出优化建议

## 依赖与运行环境

本 Skill 的所有调用通过 MCP Tool 完成（云 API 类工具由平台封装为 MCP Tool，Agent 直接调用工具名即可）。

**依赖工具清单**：

| #   | Tool 名称                      | 能力定位                                                                                                                         | 参考文档                                                      |
| --- | ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| 1   | TCHouseDDescribeInstances      | 列出地域下的集群列表（用于 instance_id 缺失时选集群）。**调用时 `InstanceType` 固定传 `0`**（本 Skill 仅服务分析型存算一体集群） | —                                                             |
| 2   | TCHouseDDescribeInstance       | 集群信息获取（单个集群详情）                                                                                                     | [参考](references/tchoused-describe-instance.md)        |
| 3   | TCHouseDDescribeDDL            | 根据库表名获取建表 DDL                                                                                                           | [参考](references/tchoused-describe-ddl.md)             |
| 4   | TCHouseDDescribeClusterConfigs | 集群配置参数                                                                                                                     | [参考](references/tchoused-describe-cluster-configs.md) |
| 5   | ask_user                       | 向用户询问确认信息（WorkBuddy 中为 AskUserQuestion）                                                                             | —                                                             |

## 凭证 / 环境变量

- `instance_id`：从会话 context 的 X-Context header 自动注入
- `region_id`：从会话 context 的 X-Context header 自动注入（可能是 `RegionId` 数字，也可能是 `Region` 字符串，也可能是中文地域名）
- 若以上参数缺失，通过 `ask_user`（WorkBuddy 中为 `AskUserQuestion`）询问用户

> ⚠️ **地域参数强制规则**：**任何工具调用前**都必须先按 [地域映射表](references/region-mapping.md) 把上下文中的地域信息补齐为 **`Region`（字符串）+ `RegionId`（数字）** 两种形式，禁止凭记忆填写。不同工具对参数形式的要求见 [工具传参形式速查](references/region-mapping.md#工具传参形式速查)：本 Skill 使用的 `TCHouseDXxx` 系工具 **只传 `Region` 字符串**。

> 💡 **多平台兼容说明**：本文档中所有提到的 `ask_user` 工具，在 WorkBuddy 平台中对应为 `AskUserQuestion`。后文不再重复标注。

## 核心工作流

### 步骤 0：参数确认

**必需参数**：

- `instance_id`（集群 ID）
- `region_id`（地域）

**可选参数**：

- 数据库名：用户指定或后续步骤中选择
- 业务场景描述：日增数据量、查询模式、保留周期等

**判断逻辑**：

- ✅ 参数齐全 → **强制**按 [地域映射表](references/region-mapping.md) 把地域补齐为 `Region`（字符串）+ `RegionId`（数字）两种形式（任何输入形式都要过这一步：中文名、英文串、数字 ID 都不例外），补齐后进入步骤 1
- ❌ 地域信息在映射表中匹配不到（或大区模糊，如"华南地区"）→ 调用 `ask_user` 确认后再补齐
- ❌ `instance_id` 缺失但 `region_id` 已知 → 调用 `TCHouseDDescribeInstances`（**`InstanceType` 固定传 `0`**，仅查分析型存算一体集群）列出该地域下的集群，通过 `ask_user`（WorkBuddy 中为 `AskUserQuestion`）以选项形式让用户从列表中挑选。**禁止**直接让用户手敲 `cdwdoris-xxxx` 集群 ID。
- ❌ `instance_id` 和 `region_id` 都缺失 → **一次** `ask_user` 同时收集「地域」和「集群 ID 或集群名」，例如："请提供要操作的 TCHouse-D 集群，格式：地域 + 集群 ID/名称（如：广州 cdwdoris-xxxxxxxx）"；用户只提供了地域未提供集群时，再调 `TCHouseDDescribeInstances`（**`InstanceType` 固定传 `0`**）列出集群让用户挑选
- ❌ 列出的集群为空 → 告知用户该地域下无可用集群，终止操作

> 💡 **调度友好原则**：尽量合并多个参数到**一次** `ask_user` 中收集，避免「地域问一轮 → 集群问另一轮 → 账密问又一轮」这种多轮连环。多轮 ask_user 间可能遇到调度静默挂起问题（上一轮结束后下一轮推理未被拉起）。

### 步骤 1：确认集群信息

调用 `TCHouseDDescribeInstance` 获取集群基本信息。

**判断逻辑**：

- ✅ 集群状态为 `Serving` → 进入步骤 2
- ❌ 集群状态异常 → 告知用户集群当前不可用，终止操作
- ❌ 调用失败（AuthFailure）→ 报告错误，提示检查权限
- ❌ 调用失败（ResourceNotFound）→ 检查 instance_id 格式（`cdwdoris-` 前缀），格式错则修正重试

**记录信息**：Doris 内核版本号（影响可用功能，如倒排索引需 ≥2.0、自增列需 ≥2.1）、FE/BE 节点规格和数量、是否存算分离架构。

### 步骤 2：场景分类与需求收集

| 场景          | 判定条件                         | 后续路径                     |
| ------------- | -------------------------------- | ---------------------------- |
| A. 新建表设计 | 用户描述业务需求，要求设计表结构 | → 步骤 3                     |
| B. MySQL 迁移 | 用户提到从 MySQL/其他数据库迁移  | → 步骤 3（额外收集源表 DDL） |
| C. 现有表优化 | 用户提到"查询慢"/"表结构有问题"  | → 步骤 2.5                   |

**场景 A/B 需收集的信息**（缺失时通过 `ask_user` 询问）：

| 信息项                         | 重要性 | 默认值（用户未提供时）            |
| ------------------------------ | ------ | --------------------------------- |
| 日增数据量                     | 必需   | 无默认，必须询问                  |
| 主要查询模式（按什么维度过滤） | 必需   | 无默认，必须询问                  |
| 是否需要按主键去重/更新        | 重要   | 默认不需要（追加写入）            |
| 聚合粒度（是否需要预聚合）     | 重要   | 默认不需要                        |
| 数据保留周期                   | 重要   | 默认永久保留                      |
| 字段列表及类型                 | 必需   | 无默认，必须询问或从源表 DDL 提取 |

**场景 B 额外收集**：源表 DDL（MySQL CREATE TABLE 语句）。

**判断逻辑**：

- ✅ 关键信息齐全 → 进入步骤 3
- ❌ 缺少必需信息 → 调用 `ask_user` 一次性询问所有缺失项

### 步骤 2.5：现有表结构诊断（场景 C）

**2.5.1 获取现有表结构**：

调用 `TCHouseDDescribeDDL` 获取目标表的建表 DDL。

**判断逻辑**：

- ✅ 成功 → 进入 2.5.2
- ❌ 表不存在 → 调用 `ask_user` 确认表名和数据库名
- ❌ 权限不足 → 告知用户无权限查看该表结构

**2.5.2 分析表结构问题**：

按 [数据模型与诊断指南](references/engine-selection-guide.md#6-诊断检查清单) 逐项检查：

- 数据模型选择是否合理（DUPLICATE/UNIQUE/AGGREGATE）
- 分区粒度是否合适
- 分桶（Bucket）数量是否合理（避免单 Tablet 过大或过小）
- 排序键设计是否匹配查询模式
- 是否缺少 Bloom Filter / 倒排索引
- 是否缺少 TTL / 动态分区配置
- 是否需要 Colocate Group

**2.5.3 生成优化建议**：

输出诊断报告 + ALTER TABLE 优化语句，进入步骤 6。

### 步骤 3：设计表结构

**3.1 选择数据模型**：

按 [数据模型选择决策树](references/engine-selection-guide.md#1-数据模型选择决策树) 选择：

| 业务特征                                    | 推荐模型                          |
| ------------------------------------------- | --------------------------------- |
| 纯追加写入，无更新无去重，明细查询          | **DUPLICATE**                     |
| 需要按主键去重（保留最新），点更新          | **UNIQUE**（推荐 Merge-on-Write） |
| 需要预聚合（SUM/MAX/MIN/REPLACE），聚合查询 | **AGGREGATE**                     |

**3.2 设计分区策略（PARTITION BY）**：

| 日增数据量      | 推荐分区粒度    | 分区表达式                                 |
| --------------- | --------------- | ------------------------------------------ |
| < 100 万行      | 按月            | `RANGE(date_col)` 按月手动建分区或动态分区 |
| 100 万 ~ 1 亿行 | 按天            | `RANGE(date_col)` 按天，启用动态分区       |
| > 1 亿行        | 按天 + 业务维度 | List + Range 多级分区                      |

**动态分区**（`DYNAMIC PARTITION`）建议默认开启，自动按时间滚动创建/淘汰分区。

**3.3 设计分桶策略（DISTRIBUTED BY HASH）**：

- **分桶键**：选高基数列（如 user_id、order_id），避免数据倾斜
- **分桶数（BUCKETS）**：单 Tablet 目标大小 1-3GB
  - 计算：`buckets = ceil(单分区数据量 / 1.5GB)`
  - 单分区 < 1GB 时 buckets=1
  - 单分区 100GB 时 buckets≈64
- **用户只给了行数、没给字节数时**：**不要凭经验拍单行字节**，按 [§4.1 从行数估算单分区字节数](references/engine-selection-guide.md#41-从行数估算单分区字节数用户只给行数时使用) 三步走 —— ①查「典型场景单行字节参考表」取压缩后估算值 → ②算单分区 GB → ③套 BUCKETS 公式；并在 DDL 说明里写明所用的单行字节假设，便于用户校准
- 2.0+ 支持 `BUCKETS AUTO` 自动分桶

**3.4 设计排序键（KEY 列）**：

排序键设计原则（按优先级）：

1. 将高频 WHERE 过滤字段放入 KEY 列
2. 字段顺序：基数低 → 基数高（如 date → city → user_id）
3. KEY 列字段数量控制在 3-5 个
4. 分区键字段通常作为 KEY 列的第一个字段
5. UNIQUE/AGGREGATE 模型下，KEY 列即为主键/聚合维度

**3.5 设计索引**：

| 字段特征                     | 推荐索引类型                                                                                                                                      |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| 高基数等值过滤（ID、手机号） | `BLOOM_FILTER`（**必须在 PROPERTIES 里配 `bloom_filter_columns`**，不能用 `INDEX ... USING BLOOM_FILTER`；详见 `references/ddl-templates.md` §5） |
| 字符串模糊匹配 / 全文检索    | `INVERTED INDEX`（≥2.0）                                                                                                                          |
| 数值范围查询                 | 自带前缀索引/ZoneMap                                                                                                                              |
| 低基数列                     | 通常不需要额外索引                                                                                                                                |

**3.6 设计 TTL / 动态分区**：

```sql
PROPERTIES (
  "dynamic_partition.enable" = "true",
  "dynamic_partition.time_unit" = "DAY",
  "dynamic_partition.start" = "-90",      -- 保留 90 天
  "dynamic_partition.end" = "3",          -- 预创建未来 3 天
  "dynamic_partition.prefix" = "p",
  "dynamic_partition.buckets" = "32"
)
```

**3.7 高级建议（按需）**：

- **Colocate Join**：高频 JOIN 的两张表分桶键相同时，加入同一 Colocate Group（`"colocate_with" = "group1"`）
- **副本数**：默认 3 副本（`"replication_num" = "3"`），单副本仅测试用

### 步骤 4：生成 DDL 语句

基于步骤 3 的设计，生成完整的 CREATE TABLE DDL。详见 [DDL 模板](references/ddl-templates.md)。

**DDL 必须包含**：

1. 完整的列定义（含数据类型、注释）
2. 数据模型声明（`DUPLICATE KEY` / `UNIQUE KEY` / `AGGREGATE KEY`）
3. PARTITION BY 表达式（如有分区）
4. DISTRIBUTED BY HASH(...) BUCKETS N
5. INDEX 索引（如有）
6. PROPERTIES（副本数、动态分区、压缩等）

### 步骤 5：输出 DDL 与手工执行指引

> ⚠️ 本步骤同样适用于场景 C 下输出的 `ALTER TABLE` 语句。

**5.1 向用户展示完整 DDL 并请求确认**：

通过 `ask_user` 向用户展示步骤 4 生成的完整 DDL 文本，并请求确认是否采用。

- ✅ 用户确认 → 进入 5.2
- ❌ 用户提出调整意见 → 回到步骤 3.x 相应设计环节修订 DDL，再回到本步骤

**5.2 输出手工执行指引**：

本 Skill **不直接连接集群执行 DDL**，Agent 需要在报告中向用户输出以下内容：

1. **完整可复制的 DDL 文本**（每条语句独立成块，多条 DDL 请拆分为多条独立代码块）
2. **推荐的执行方式**：登录腾讯云 TCHouse-D 控制台 → 打开目标集群 → 进入「SQL 工作区」→ 按顺序粘贴执行
3. **执行前置检查提示**：
   - 目标业务库是否存在（若不存在需先执行 `CREATE DATABASE \`{db}\``）
   - 目标库**必须**是业务库，禁止在 `mysql` / `information_schema` / `__internal_schema` 等保留系统库中建业务表
   - 多条 DDL 按顺序单条粘贴执行
4. **执行后回贴提示**：如需 Skill 协助排查 DDL 执行错误（语法、Bucket、权限等），请将 SQL 工作区返回的错误信息回贴，Agent 会给出修正建议

**5.3 常见执行错误的排查建议（供用户参考）**：

| 错误场景              | 处理方式                                                                                                                               |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| DDL 语法错误          | 用户回贴 `ErrMsg`，Agent 分析并修正 DDL 后重新输出                                                                                     |
| 表 / 库已存在         | 若用户接受，可改用 `IF NOT EXISTS`；否则告知用户                                                                                       |
| 权限不足              | 告知用户无 DDL 权限，建议联系管理员                                                                                                    |
| Bucket 数过大或过小   | 根据 BE 节点数和数据量重新计算 Bucket 数                                                                                               |
| 目标库为保留系统库    | `mysql` / `information_schema` / `__internal_schema` 禁止建业务表；`ask_user` 重新询问业务库名                                         |
| 目标业务库不存在      | 建议用户先执行 `CREATE DATABASE \`{db}\``，再执行建表 DDL                                                                              |
| Bloom Filter 语法错用 | 使用了 `INDEX ... USING BLOOM_FILTER` 或 `CREATE INDEX ... USING BLOOM_FILTER` → 改为 `PROPERTIES("bloom_filter_columns"="col1,col2")` |

### 步骤 6：输出结果

向用户输出：

1. 执行的 DDL 完整文本（原始 SQL，便于用户复制到 SQL 工作区执行）
2. 手工执行指引（参考步骤 5.2）
3. 设计理由说明（数据模型选择、分区策略、分桶策略、索引、动态分区等的依据）

## 执行阶段检查清单（步骤 5 输出 DDL 前必读）

- [ ] 是否已通过 `ask_user` 向用户展示完整 DDL 并获得**明确确认**
- [ ] 是否明确告知用户**由用户自行到 SQL 工作区执行**，本 Skill 不直接连接集群执行 DDL
- [ ] 多条 DDL 是否已拆分为多个独立代码块，避免用户一次性粘贴多条语句失败
- [ ] 是否在指引中提示用户先确认目标业务库存在（不存在则先建库）
- [ ] 是否提示用户禁止在 `mysql` / `information_schema` / `__internal_schema` 等保留系统库中建业务表
- [ ] 是否告知用户可在遇到执行错误时回贴错误信息，Agent 会协助排查

## 高频经验提醒

| 经验                          | 触发时机 | 说明                                                                        |
| ----------------------------- | -------- | --------------------------------------------------------------------------- |
| DDL 输出前必须用户确认        | 步骤 5.1 | 建库 / 建表 / 改表均为写操作，禁止未经 `ask_user` 直接给出成品 DDL          |
| 由用户手工执行 DDL            | 步骤 5.2 | 本 Skill 不直接执行 DDL；向用户输出完整 DDL 与 SQL 工作区执行指引           |
| 多条 DDL 拆分为多个代码块     | 步骤 5.2 | 建库 + 建表等多条语句必须拆分成多个独立代码块，便于用户逐条粘贴执行         |
| 禁止在保留系统库建业务表      | 步骤 5.2 | 目标业务库必须存在且非 `mysql` / `information_schema` / `__internal_schema` |
| Bucket 设计要看单 Tablet 大小 | 步骤 3.3 | 单 Tablet 目标 1-3GB，太小（<100MB）浪费元数据，太大（>5GB）影响并行度      |
| 默认 3 副本                   | 步骤 3.7 | 单副本/双副本只在测试场景使用，生产必须 3 副本                              |
| 动态分区强烈推荐              | 步骤 3.6 | 时序数据自动建/删分区，避免人为遗漏导致写入失败                             |
