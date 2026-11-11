# 字面量库

本文件是数值、拒绝原因、枚举的唯一出处。规格正文引用编号，不另写一套中文。

## LIT-C 数值

| 编号 | 值 | 出处 |
|---|---|---|
| LIT-C-SCHEMA-P95-MS | 80 | D-015 的读路径；NFR-02 |
| LIT-C-INSTALL-SEC | 60 | D-015；NFR-03 |
| LIT-C-INSTALL-ASYNC-SEC | 55 | 仅外接源探测。满此时长可返回进行中。无外接源禁止 |
| LIT-C-INTROSPECT-MS | 1500 | 令牌核验与桥接 HTTP 共用 |
| LIT-C-PAGE-SIZE-DEFAULT | 20 | 查询未传页大小时 |
| LIT-C-OUTBOX-POLL-MS | 10000 | 外发工作器间隔 |
| LIT-C-QUERY-P95-MS | 300 | 单源、不超过 LIT-C-QUERY-ROW-CAP 行。设计目标 |
| LIT-C-QUERY-ROW-CAP | 100 | 单页 pageSize 上限 |
| LIT-C-JOIN-ROW-CAP | 10000 | D-016 估算行数上限 |
| LIT-C-ASSEMBLE-TOOLS | 3 | 智能组装设计。不含 describe |
| LIT-C-APPROVAL-HOURS | 24 | 智能组装设计。若与 gnex 审批超时冲突，以 gnex 为准并改本行 |
| LIT-C-IDEMPOTENCY-HOURS | 24 | 接口设计 |
| LIT-C-OUTBOX-HOURS | 24 | 安装与恢复。超时后告警仍留行 |
| LIT-C-POLL-MIN-MS | 1000 | 安装轮询最小间隔 |
| LIT-C-ENTITY-FIELDS | 15 | 标准包实体字段上限，用于计时口径 |
| LIT-C-PROBE-MS | 2000 | 数据源探测超时 |

## LIT-E 枚举

应用状态：`DRAFT` `PUBLISHED` `SUSPENDED` `RETIRED`

组装状态：`PROPOSED` `VALIDATED` `APPROVAL_PENDING` `APPLIED` `ROLLED_BACK`

安装状态：`RUNNING` `SUCCEEDED` `ROLLED_BACK`

健康：`UP` `DEGRADED` `DOWN`

数据源类型：`POSTGRES` `MYSQL` `REST`

字段类型：`string` `text` `int` `decimal` `bool` `date` `datetime` `enum` `ref`

动作：`read` `create` `update` `delete`

平台角色名默认：`daf-platform-admin`（配置键 `daf.security.platform-role` 可改，令牌 `roles` 必须包含该字符串）

保留应用角色：`app-admin`（安装发布时写入调用者，表示应用管理员）

区块：`table` `form` `detail` `filter` `stat` `empty`

保护级别：`core` `standard`

## LIT-R 拒绝原因

`message` 必须与下表逐字一致。

| 编号 | code | message |
|---|---|---|
| RC-AUTH-MISSING | RC-AUTH-MISSING | 缺少有效登录凭证 |
| RC-AUTH-INCOMPLETE | RC-AUTH-INCOMPLETE | 登录凭证缺少租户、用户或项目 |
| RC-TENANT-MISMATCH | RC-TENANT-MISMATCH | 租户不一致，已拒绝 |
| RC-APP-NOT-FOUND | RC-APP-NOT-FOUND | 应用不存在 |
| RC-APP-STATE | RC-APP-STATE | 应用当前状态不允许此操作 |
| RC-PERM-DENIED | RC-PERM-DENIED | 没有权限执行此操作 |
| RC-SCHEMA-INVALID | RC-SCHEMA-INVALID | 页面或语义校验未通过 |
| RC-SOURCE-DOWN | RC-SOURCE-DOWN | 数据源不可用 |
| RC-JOIN-CAP | RC-JOIN-CAP | 跨源数据量超过上限，已拒绝 |
| RC-APPROVAL-REQUIRED | RC-APPROVAL-REQUIRED | 已发布应用的变更需要审批 |
| RC-PACKAGE-CONFLICT | RC-PACKAGE-CONFLICT | 应用或版本冲突 |
| RC-ROLLBACK | RC-ROLLBACK | 操作已回滚 |
| RC-SEMANTIC-UNKNOWN | RC-SEMANTIC-UNKNOWN | 语义包含有未登记的类型或动作 |
| RC-DIRECT-SQL | RC-DIRECT-SQL | 不接受任意 SQL |
| RC-DEP-MISSING | RC-DEP-MISSING | 依赖未满足 |
| RC-HOOK-FAILED | RC-HOOK-FAILED | 业务规则拒绝了本次提交 |

## LIT-P 公共载荷字段

`tenantId` `appKey` `actorUid` `occurredAt` `traceId`

事件禁止字段：`password` `secret` `credentialValue` `sql` `rows`
