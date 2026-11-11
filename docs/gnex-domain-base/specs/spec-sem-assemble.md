# spec-sem-assemble

> 来源：FR-SEM-02 至 FR-SEM-05；06/05 智能组装设计；决策 D-007
> 模块 ID：sem-assemble｜能力域：语义｜保护级别：core｜宿主：daf-sem｜实现状态：可开发

## §1 业务目标

智能体可以提议改页面和加可选字段，但不能直接写元数据表。已发布应用要等 gnex 审批。草稿可以落地并留审计。落地失败或事后撤销要回到前像。对应 FR-SEM-02、FR-SEM-03、FR-SEM-04、FR-SEM-05。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-SEM-02、FR-SEM-03、FR-SEM-04、FR-SEM-05、D-007 |
| 对标借鉴 | 变更单而不是直接改生产配置。审批用 gnex，不自建流程引擎 |
| 技术选型 | 选组装事务状态机。备选是让智能体提交 Git 合并请求。不选的反例：合并请求不理解租户行和页面校验，模型会改到别的文件，审批系统也变成两套。复决：若企业规定一切变更必须进统一 Git 且基座校验能做成合并前检查，再把 APPLIED 的结果导出为提交，事务仍是写入源头 |
| 改造要点 | 状态机以智能组装设计为准。落地事务写前像。禁止在此事务删字段或改类型 |

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| sem.assemble | `Assembly propose(String appKey, JsonNode diff)` | 状态 PROPOSED |
| sem.assemble | `Assembly validate(String id)` | 通过则 VALIDATED |
| sem.assemble | `Assembly apply(String id, String approvalId)` | 见状态机 |
| sem.assemble | `Assembly rollback(String id)` | 仅 APPLIED |

### 3.2 REST

POST /apps/{appKey}/assemblies；POST /assemblies/{id}/validate；POST /assemblies/{id}/apply；POST /assemblies/{id}/rollback。

工具 biz.propose、biz.validate、biz.apply 只调用以上方法。

### 3.3 事件

| 事件名 | 方向 | 载荷要点 |
|---|---|---|
| daf.assembly.applied | 发出 | 公共载荷加 assemblyId。无 diff 全文 |

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| brg.gnex | 必选 | 提交审批与核对 | 已发布应用无法进入待审批或无法落地 |
| ui.schema | 必选 | 写页面 | 校验失败则留在 PROPOSED |
| dat.entity | 必选 | 只加可选字段 | 其他差异 422 |
| idn.grant | 必选 | 提议者须是成员或平台管理员 | 403 |

## §4 数据模型

`daf_assembly`：id 为主键。另有 tenant_id、project_id、app_key、state、diff_json、errors_json、before_json、approval_id、updated_at。按 id 查询必须同时带租户和项目。

before_json 在进入 APPLIED 之前写入。

## §5 实现思路

状态迁移以智能组装设计为准。补充三条执行细节，不改变迁移表的条件：

- 已发布应用校验成功时调用 `submitApproval`。成功才进入 APPROVAL_PENDING。提交失败保持 VALIDATED。
- apply 只接受 APPROVAL_PENDING。审批号为空则 409，文案 `RC-APPROVAL-REQUIRED`，状态不变。核对为通过且号码一致才落地。拒绝或超过 `LIT-C-APPROVAL-HOURS` 回到 PROPOSED。核对调用失败保持 APPROVAL_PENDING。
- 已发布应用的页面 PUT 一律 409。不接受带着组装号直接改页面。
- 组装回滚只发 `daf.assembly.rolled-back`。
- 不含 describe 的工具调用超过 `LIT-C-ASSEMBLE-TOOLS` 则拒绝继续，文案 `RC-SCHEMA-INVALID`。

diff 允许的操作仍是 `addOptionalField`、`addPage`、`setUiHint`。

## §6 边界

- ✅ 智能体身份也是用户令牌，不能绕过成员检查。
- ❌ 禁止本模块执行任意 SQL 文本。
- ❌ 解耦红线：不得实现审批流、不得写入 gnex 会话日志表。只保存审批号。

## §7 装配与配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| daf.assemble.approval-hours | 24 | 与字面量库一致。若桥接报告 gnex 超时不同，以桥接为准并记录配置覆盖 |

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-AS-01 | 草稿落地 | 应用 DRAFT，diff 为 addPage | propose→validate→apply | state=APPLIED 且页面可读 | L1 | FR-SEM-05 | JUnit | 待实现 |
| ACC-AS-02 | 已发布无审批 | 应用 PUBLISHED | apply 不带审批号 | 409 RC-APPROVAL-REQUIRED 且页面版本不变 | L4 | FR-SEM-04 | JUnit | 待实现 |
| ACC-AS-03 | 审批通过 | 桥接返回通过 | apply | APPLIED | L2 | FR-SEM-04 | JUnit | 待实现 |
| ACC-AS-04 | 删字段 | diff 含 dropField | validate | 仍为 PROPOSED 且 errors 非空 | L3 | FR-SEM-06 | JUnit | 待实现 |
| ACC-AS-05 | 回滚 | 已 APPLIED | rollback | 页面版本回到 before_json，state=ROLLED_BACK | L4 | FR-SEM-03 | Testcontainers | 待实现 |
| ACC-AS-06 | 非成员 | 用户无成员 | propose | 403 | L3 | FR-SEM-02 | MockMvc | 待实现 |
| ACC-AS-07 | 直写拦截 | 测试试图以智能体令牌 PUT 元数据表 | 仅开放工具路径 | 无 SQL 通道。此条由代码评审清单保证：模块没有 JdbcTemplate 公开给工具层 | L4 | FR-SEM-02 | 架构测试 ArchUnit | 待实现 |

### 运行命令

```bash
mvn -pl daf-sem test -Dtest=AssemblyServiceTest
```

## §9 影响域

一个事务只改一个应用。回滚不删除已写入业务行里的新可选列的值，列标记退役。见安装与恢复设计。

## §10 后续

不在组装里做数据迁移脚本。
