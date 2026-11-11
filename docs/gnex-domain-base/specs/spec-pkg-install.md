# spec-pkg-install

> 来源：FR-PKG-01 至 FR-PKG-05；06/08 安装与恢复；决策 D-015
> 模块 ID：pkg-install｜能力域：应用包｜保护级别：core｜宿主：daf-pkg｜实现状态：可开发

## §1 业务目标

业务系统以清单安装。要么全部可用，要么和安装前一样。升级不跳版。业务规则用命令钩子，不用页面脚本。标准包要在时限内打开。对应 FR-PKG-01 至 FR-PKG-05、NFR-03。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-PKG-01、FR-PKG-02、FR-PKG-03、FR-PKG-04、FR-PKG-05、NFR-03、D-015 |
| 对标借鉴 | Odoo 清单的依赖与数据文件。钩子改为 HTTP 服务键，不采用 Java 类名 |
| 技术选型 | 选单数据库事务安装。备选是逐步安装加人工清理。不选的反例：第三步失败后菜单已发布，用户能打开空页面，回滚说明写在文档里但没人执行。复决：若必须调用的外部系统无法放进事务（例如客户要求安装时写远程 ERP），把该调用改成安装成功后的补偿任务，并在失败时自动走卸载补偿。首版不启用这条 |
| 改造要点 | 顺序按安装与恢复设计。只有成功的幂等键回放。跳版拒绝。发布安装同时写入 app-admin 成员 |

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| pkg.install | `Install start(JsonNode manifest, String idempotencyKey, String actorUid)` | 无外接源时同步结束。外接探测超时才返回进行中 |
| pkg.install | `Install status(String installId)` | 三种安装状态 |
| pkg.hooks | `HookResult beforeCommand(String appKey, String entityKey, String action, JsonNode payload)` | 转成对 biz 服务的 HTTP。返回 allowed 与 reason |

### 3.2 REST

POST /api/daf/v1/packages/install；GET /packages/installs/{installId}。

### 3.3 事件

| 事件名 | 方向 | 载荷要点 |
|---|---|---|
| daf.package.installed | 发出 | 公共载荷加 packageKey、version |
| daf.package.rolled-back | 发出 | 加原因码 |

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| app.registry | 必选 | 创建与发布 | 失败则回滚 |
| app.router | 必选 | 写路由 | 同上 |
| dat.entity | 必选 | 实体 | 同上 |
| ui.schema | 必选 | 页面 | 同上 |
| sem.package | 必选 | 语义 | 同上 |
| svc.catalog | 清单含 services 时必选 | 绑定 | 模块缺失则 RC-DEP-MISSING |
| dat.source | 有外接源时必选 | 探测已在事务外完成 | 探测失败不开始事务 |

## §4 数据模型

`daf_install`：install_id 主键。另有 tenant_id、project_id、actor_uid、path、idempotency_key、package_key、version、state、error_code、expires_at、started_at、finished_at。唯一约束是租户、项目、调用者、路径、幂等键。窗口过期后同一键可以再使用。只有 state 为成功才回放。已回滚允许再执行并覆盖该行。

`daf_package`：tenant_id、project_id、package_key、version、app_key、installed_at。主键 (tenant_id, project_id, package_key)。

`daf_hook`：tenant_id、project_id、app_key、service_key。主键 (tenant_id, project_id, app_key, service_key)。没有类名和 jar。

调用者必须是平台管理员，否则 403，文案 `RC-PERM-DENIED`。`publish` 为 true 时同一事务插入调用者的成员和角色 `app-admin`。

## §5 实现思路

步骤与安装与恢复设计一致，本文件不另写探测时机。

依赖：`depends` 的每一项是 packageKey 加 version。已装版本三段数字必须大于等于该项。

升级：主版本变化一律拒绝。相邻规则与 `upgradePath` 的连续要求以安装与恢复设计第 4 节为准，这里不改条件。

钩子只保存 serviceKey。安装不加载类。命令执行时由数据模块调用该 HTTP。

无外接源的安装禁止返回 202，必须在 `LIT-C-INSTALL-SEC` 内以 200 结束。有外接源的探测才允许在满 `LIT-C-INSTALL-ASYNC-SEC` 时返回 202，且当时不得写业务表。

## §6 边界

- ✅ 失败后 daf_app 不存在新应用，或仍是升级前版本。
- ❌ 禁止清单内嵌源码或脚本。
- ❌ 解耦红线：安装器不得热替换 gnex 插件，不得写 service-assembly.yml。

## §7 装配与配置

| 配置键 | 默认 | 说明 |
|---|---|---|
| daf.pkg.install-budget-sec | 见 LIT-C-INSTALL-SEC | 标准包预算 |

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-PKG-01 | 标准包 | 调用者 roles 含平台角色，托管源可用 | 安装标准包且 publish 为 true | HTTP 200，state=SUCCEEDED，调用者成为 app-admin，路由可解析，墙钟不超过 LIT-C-INSTALL-SEC | L1 | NFR-03 | Testcontainers | 待实现 |
| ACC-PKG-02 | 中途失败 | 页面模式非法 | 安装 | state=ROLLED_BACK 且 daf_app 无该键 | L4 | FR-PKG-02 | Testcontainers | 待实现 |
| ACC-PKG-03 | 缺依赖 | depends 含未装包 | 安装 | RC-DEP-MISSING 且无新表 | L3 | FR-PKG-04 | JUnit | 待实现 |
| ACC-PKG-04 | 跳版 | 已装 1.0.0 | 安装 1.2.0 且无路径 | 409 RC-PACKAGE-CONFLICT，版本仍为 1.0.0 | L4 | FR-PKG-03 | JUnit | 待实现 |
| ACC-PKG-05 | 幂等 | 第一次已成功 | 同一用户、同一路径、同一幂等键再安装 | 同一 installId，物理表只建一次 | L2 | D-022 | Testcontainers | 待实现 |
| ACC-PKG-06 | 钩子不是类 | 清单 hooks 含 className | 安装 | 422，因为未知字段。含合法 serviceKey 时安装成功且进程不加载 jar | L3 | FR-PKG-05 | JUnit | 待实现 |
| ACC-PKG-07 | 相邻升级 | 已装 1.0.0，迁移 from 1.0.0 | 安装 1.0.1 | SUCCEEDED 且版本 1.0.1 | L2 | FR-PKG-03 | Testcontainers | 待实现 |

### 运行命令

```bash
mvn -pl daf-pkg test -Dtest=PackageInstallerTest
```

## §9 影响域

锁粒度是单个应用。同一租户其他应用可同时安装。失败不改 gnex。

## §10 后续

不做安装包市场和签名。签名在启用外部制品前必须补上。进程内加载业务 jar 也不在首版，见 D-018。
