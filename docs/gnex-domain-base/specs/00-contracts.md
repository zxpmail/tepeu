# 契约索引

服务键、事件名、错误码以本文件和 `00-literals.md` 为准。模块规格不得另起同名不同义。

## 1. 模块服务键（进程内 Java 接口）

| 服务键 | 接口 | 提供模块 |
|---|---|---|
| idn.context | TenantContextBinder | daf-idn |
| idn.grant | AppGrantService | daf-idn |
| app.registry | AppRegistry | daf-app |
| app.router | AppRouter | daf-app |
| svc.catalog | BizServiceCatalog | daf-svc |
| ui.schema | PageSchemaStore | daf-ui |
| ui.shell | ShellReadModel | daf-ui |
| dat.source | DataSourceRegistry | daf-dat |
| dat.entity | EntityCatalog | daf-dat |
| dat.query | EntityQuery | daf-dat |
| dat.command | EntityCommand | daf-dat |
| sem.package | SemanticPackageStore | daf-sem |
| sem.assemble | AssemblyService | daf-sem |
| pkg.install | PackageInstaller | daf-pkg |
| brg.gnex | GnexBridge | daf-brg |
| pkg.hooks | CommandHook | daf-pkg |

接口方法的完整签名写在对应规格 §3，这里只锁定键。

## 2. 给智能体的工具名

`biz.describe` `biz.propose` `biz.validate` `biz.apply` `biz.query`

## 3. 事件名

`daf.app.published` `daf.app.suspended` `daf.package.installed` `daf.package.rolled-back` `daf.assembly.applied` `daf.assembly.rolled-back` `daf.audit.dispatch-failed`

## 4. 解耦红线（机检）

除 `specs/spec-brg-gnex.md` 外，其他规格的 §5 实现思路若出现下列词，视为越界设计（出现在「禁止」列举里除外，检查脚本只扫 §5）：

`agentLoop` `cordis` `TenantInterceptor` `service-assembly.yml`

基座不实现这些。需要租户语义时只调用 `brg.gnex`。
