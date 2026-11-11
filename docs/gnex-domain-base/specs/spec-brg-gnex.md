# spec-brg-gnex

> 来源：D-001、D-002、D-012、NFR-04、NFR-05；接口设计第 6、7 节
> 模块 ID：brg-gnex｜能力域：桥接｜保护级别：core｜宿主：daf-brg｜实现状态：可开发

## §1 业务目标

基座只在一个模块里认识 gnex。这个模块核验令牌、核对审批、投递审计、解析凭证，并向 gnex 登记五个只读或受控工具。基座停机不影响已经在 gnex 里、且不再调用基座的那一轮智能体。对应 NFR-04、NFR-05、FR-IDN-01、FR-SEM-04。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-IDN-01、FR-IDN-06、FR-SEM-04、NFR-04、NFR-05、NFR-06、D-001 |
| 对标借鉴 | gnex 能力缝：调用方依赖定义，不依赖对方进程内的类 |
| 技术选型 | 选 HTTP 桥。备选是把基座做成 cordis 插件以直接 inject。不选的反例：业务查询和页面流量进了智能体进程，发布基座就要重启循环，NFR-05 无法成立。复决：若工具调用往返 p95 超过 200 毫秒且缓存语义包后仍超过，只允许在智能体侧加只读缓存，数据库仍留在基座 |
| 改造要点 | URL 全部配置。outbox 负责审计。工具适配层校验调用者令牌后再调本进程服务 |

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| brg.gnex | `IntrospectionResult introspect(String bearer)` | 成功体含 tid、uid、projectId、roles。缺前三项视为不完整 |
| brg.gnex | `String submitApproval(String assemblyId, String summary)` | 返回审批号。失败不返回空号冒充成功 |
| brg.gnex | `ApprovalState fetchApproval(String approvalId)` | 不缓存 |
| brg.gnex | `void enqueue(AuditRecord record)` | 只插 outbox。投递在工作器 |
| brg.gnex | `Credential resolve(String credentialRef)` | 结果不得进入日志参数 |

### 3.2 REST

对外只增加工具用的内部路径 `/api/daf/v1/tools/biz.describe|propose|validate|apply|query`。这些路径仍要令牌。不提供无认证的内网万能接口。

### 3.3 事件

发出接口设计第 7 节的事件。业务事务只插入原始事件。工作器投递。超过 `LIT-C-OUTBOX-HOURS` 仍失败时另插一行 `daf.audit.dispatch-failed`，该行不再投递。此前失败只增加 attempts。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| 其他 daf 服务 | 工具路径需要 | 执行工具 | 服务缺失则工具返回 503，gnex 循环本身不退出 |

gnex URL 缺失：启动时若 introspect、audit 两个 URL 为空则拒绝启动。approval URL 为空则已发布组装不可用，但进程可启动。credential URL 为空则只能使用已经在环境里提供给托管库的引用解析桩，生产配置禁止为空。

## §4 数据模型

`daf_outbox`：id、tenant_id、event_name、payload_json、attempts、next_try_at、created_at。无密钥字段。投递成功删除行。超过 LIT-C-OUTBOX-HOURS 停止自动重试，行保留，日志告警一次。

## §5 实现思路

HTTP 客户端超时用 `LIT-C-INTROSPECT-MS`，不写死主机名。核验和审批不自动重试。

工具层转发用户令牌。biz.apply 调用组装服务。参数含 sql 则拒绝。

工作器间隔 `LIT-C-OUTBOX-POLL-MS`。多副本用 `SELECT ... FOR UPDATE SKIP LOCKED`。工作器使用只能访问 outbox 的数据库角色。

本模块是唯一允许出现 gnex URL 配置键的模块。ArchUnit：除 daf-brg 外不得读取 `gnex.` 配置键。

平台管理员：`roles` 包含 `daf.security.platform-role`。不读取名为 platformAdmin 的字段。

## §6 边界

- ✅ 核验失败不得返回部分租户标识给错误体 details。
- ✅ 审计载荷遵守 LIT-P 禁止字段。
- ❌ 禁止为了方便在基座保存 gnex 的服务账号口令。调用 gnex 使用配置引用 `gnex.caller-credential-ref`，解析方式与数据源相同，若 gnex 核验接口只接受用户令牌，则工具调用转发用户令牌，不换用超级令牌。
- ❌ 解耦红线的例外只在本模块：可以记载 gnex 的 URL 配置键。仍禁止依赖 cordis、agentLoop 的 Java 类。桥是 HTTP，不是进程内注入。

## §7 装配与配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| gnex.auth-introspect-url | 空 | 空则拒绝启动 |
| gnex.approval-url | 空 | 空则已发布不可落地 |
| gnex.audit-url | 空 | 空则拒绝启动 |
| gnex.credential-resolve-url | 空 | 生产不得为空 |
| gnex.caller-credential-ref | 空 | 不得把值写成口令 |
| daf.outbox.poll-ms | 见 LIT-C-OUTBOX-POLL-MS | 工作器间隔 |

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-BR-01 | 核验成功 | 测试桩返回 tid、uid、projectId、roles | introspect | 四项都在。roles 不含平台角色时 platform 判定为否 | L1 | FR-IDN-01 | JUnit | 待实现 |
| ACC-BR-02 | 核不重试 | 桩第一次超时 | 业务请求 | 桩调用次数为 1，HTTP 401 | L3 | FR-IDN-01 | JUnit | 待实现 |
| ACC-BR-03 | 审计只入队 | 成员变更事务 | 提交 | outbox 新增 1 行，事务内 HTTP 次数为 0 | L3 | NFR-04 | JUnit | 待实现 |
| ACC-BR-04 | 队列投递 | outbox 有行，桩改为 200 | 等到 poll | 行被删除 | L2 | NFR-04 | JUnit | 待实现 |
| ACC-BR-05 | 工具无 SQL | 参数含 sql | POST tools/biz.query | RC-DIRECT-SQL | L4 | FR-DAT-08 | MockMvc | 待实现 |
| ACC-BR-06 | 依赖方向 | 全代码 | ArchUnit | 非 brg 包不读取 gnex. 配置键 | L4 | D-001 | ArchUnit | 待实现 |
| ACC-BR-07 | 不测进单模块 | 无 | 无 | 本条不作为 daf-brg 单模块测试的通过条件。进程隔离由部署说明约束：基座与 gnex 分进程 | L2 | NFR-05 | 不自动化进本模块 | 待实现 |

### 运行命令

```bash
mvn -pl daf-brg test -Dtest=GnexBridgeTest,GnexBoundaryArchTest
```

## §9 影响域

桥接故障时新的写操作拒绝或审计延迟。已在 gnex 内且不调用本桥的循环继续。凭证解析失败只影响对应数据源。

## §10 后续

不在本模块做 gnex 插件热装。不转发任意 gnex 管理接口。
