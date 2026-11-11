# spec-dat-source

> 来源：FR-DAT-01、FR-DAT-02、FR-SVC-05；决策 D-010、D-012
> 模块 ID：dat-source｜能力域：数据｜保护级别：core｜宿主：daf-dat｜实现状态：可开发

## §1 业务目标

业务数据可能在基座托管库，也可能在已有的 PostgreSQL、MySQL 或 REST。登记数据源时只记录类型和凭证引用，并探测是否通。对应 FR-DAT-01、FR-DAT-02。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-DAT-01、FR-DAT-02、NFR-06 |
| 对标借鉴 | ToolJet 清单把密钥标为加密字段。本系统连密文也不落基座库 |
| 技术选型 | 选三种连接器接口。备选是首版就上通用 JDBC 加任意 URL。不选的反例：任意 JDBC URL 能指向未审查的库，租户条件无法在未知方言里保证下推。复决：出现第四种已签署的企业数据源且前三种无法经 REST 包一层时，按同一接口加连接器，不开放任意 URL |
| 改造要点 | probe 成功才把状态记为 UP。凭证解析只在探测和查询瞬间发生，不写日志 |

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| dat.source | `DataSource register(String sourceKey, String type, String credentialRef, String endpointRef)` | type 仅三种 |
| dat.source | `ProbeResult probe(String sourceKey)` | 超时 LIT-C-PROBE-MS |

### 3.2 REST

POST /api/daf/v1/sources；POST /sources/{sourceKey}/probe。

### 3.3 事件

无。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| brg.gnex | 必选 | 解析凭证 | 探测 DOWN，不保存解析结果 |

托管库使用配置 `daf.dat.managed-credential-ref`，仍然是引用，不是连接串写在代码里。

## §4 数据模型

`daf_source`：tenant_id、source_key、type、credential_ref、endpoint_ref、health、checked_at。主键 (tenant_id, source_key)。无密码列。

## §5 实现思路

连接器接口 `DataConnector`：`probe`、`query`、`command`。实现类 `PostgresConnector`、`MysqlConnector`、`RestConnector`。REST 的 query 只支持清单里登记的操作模板（方法、路径、字段映射），不把用户输入拼进 URL 路径。过滤只允许模板声明的查询参数。

endpointRef 是逻辑名或主机引用，由凭证文档补充库名。规格不规定凭证文档的内部格式，那是 gnex credentials 的职责。

## §6 边界

- ✅ 未知 type 拒绝。
- ❌ 禁止请求体出现 password、jdbcUrl 全串。endpointRef 不得包含用户名口令（出现 `@` 且带 `:` 的用户信息段即拒绝）。
- ❌ 解耦红线：连接器不调用模型、不发起智能体。

## §7 装配与配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| daf.dat.managed-credential-ref | 空 | 空则不允许创建托管实体 |
| daf.dat.probe-ms | 2000 | 探测超时 |

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-DS-01 | 三种类型 | 管理员 | 分别登记 POSTGRES、MYSQL、REST | 均 201 | L1 | FR-DAT-02 | JUnit | 待实现 |
| ACC-DS-02 | 非法类型 | type=MONGO | 登记 | 422 | L3 | D-010 | JUnit | 待实现 |
| ACC-DS-03 | 口令出现在地址 | endpointRef 含 user:pass@ | 登记 | 422 | L4 | NFR-06 | JUnit | 待实现 |
| ACC-DS-04 | 探测超时 | 连接器阻塞 | probe | health=DOWN 且在超时后返回 | L3 | FR-DAT-01 | JUnit | 待实现 |
| ACC-DS-05 | 日志 | 探测成功 | 查测试日志 | 不含凭证解析值 | L4 | NFR-06 | JUnit | 待实现 |

### 运行命令

```bash
mvn -pl daf-dat test -Dtest=DataSourceRegistryTest
```

## §9 影响域

数据源 DOWN 只影响映射到它的实体。其他源不受影响。

## §10 后续

不做 SQL Server、Oracle、Mongo。
