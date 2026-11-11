# v3-spec-ent-org

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-org
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-org
> **分层**：企业层（L4）
> **受保护级别**：protected（平台持有，禁租户/Agent 卸载）
> **宿主服务**：ops-service（组织管理面）+ auth-service（RBAC 内核，ADR-001）
> **实现状态**：规划中

---

## §1 业务目标

组织/成员/角色的企业管理面：继承 gnex2.0 ops/org（部门树 `dept` + `user_tenant` 成员归属）+ auth/rbac（`role_def`/`permission_def`/`user_role`，`RbacInitializer` 启动种入，ADR-001 角色数据归认证服务）+ resource_grant（E21：用户→具体资源操作许可，与功能角色正交，写后实时刷 Redis `grant:*`）。插件化为 `org` 服务：成员必须绑定租户（跨租户拒绝）、角色/权限变更 ≤5s 刷缓存（JWT roles claim + Gateway 粗粒度校验）、部门树防循环引用、权限判定必须走 RBAC + grant（不得绕过）。

**非目标**：不做认证签发/SSO（归 ent-auth）；不做资源授权细节 API（`resource_grant` CRUD 归 ops grant 包，本插件消费其 Port）；不做密码策略（E24 归 ops 已有）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh 无此原语（dsh 无企业组织/RBAC 平面） |
| 对标 GNEX2.0 模块 | `ops-service` org 包（`DeptService`/`DeptController`/`DeptEntity`）+ `auth-service` rbac 包（`RbacManagementInterface`/`RbacInitializer`/`user_role`，E20）+ ops `ResourceGrantService`（resource_grant，E21）+ platform-persistence `ProjectMember` |
| 技术选型 | 🅶 GNEX2.0 模块改造（ops org + auth rbac 保留，收敛为 `org` 服务 + 事件化） |
| 改造要点 | ① 部门/成员/角色查询收敛为 `org` 服务（ops + auth 双宿主适配）② 成员变更事件化（`org/member-*`/`org/role-changed`）③ 权限判定统一走 RBAC + grant（Gateway 粗校验 + 服务内细校验）④ 变更缓存 ≤5s 刷新 ⑤ 跨租户归属校验内建（user_tenant） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `org` | `com.gnex.plugin.enterprise.org.OrgService` | 部门树/成员管理/角色绑定/权限判定（委托 auth RBAC + ops grant） |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`tenant`、`auth`、`audit`
- 可选：`observability`（权限判定指标）、`billing`（成员配额联动）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `org/member-added` | emit | {tenantId, userId, deptId, roles[]} |
| `org/member-removed` | emit | {tenantId, userId} |
| `org/role-changed` | emit | {tenantId, userId, roleDiff[], grantDiff[]}：触发缓存刷新（≤5s） |
| `audit/record` | listen（可选） | 成员/角色/授权变更入审计 |

## §4 配置与装配

```yaml
# service-assembly.yml 片段（ops-service / auth-service）
plugins:
  - id: gnex3-ent-org
    load: mandatory
    protection: platform
settings:
  org:
    rbac: { cacheTtlSeconds: 5, jwtRolesClaim: roles }
    dept: { maxDepth: 10 }
    grant: { cacheKeyPrefix: "grant:" }
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 成员必须绑定租户（user_tenant 归属校验，跨租户成员/授权拒绝）
- ✅ 角色/权限变更必须 ≤5s 刷缓存（JWT roles claim + Gateway 粗粒度校验 + 服务内细校验）
- ✅ 权限判定必须走 auth RBAC + ops resource_grant（功能角色与资源授权正交，UNIQUE(user,tenant,type,resource,permission)）
- ✅ 部门树必须防循环引用；有子部门拒绝删除（级联防护）
- ❌ 禁止跨租户组织数据可见（部门/成员/角色按 tenant 隔离）
- ❌ 禁止绕过 RBAC/grant 直接授权资源或放行操作（权限旁路 = 越权）
- ❌ 禁止租户/Agent 装卸本插件（🔒）；禁止成员/角色数据脱离 auth 内核直改（ADR-001）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 成员租户归属 | `OrgMembershipTest`：跨租户加成员被拒（user_tenant 校验） |
| V2 | RBAC 缓存刷新 | `OrgRbacCacheTest`：角色变更 ≤5s 新权限生效（JWT roles 刷新） |
| V3 | 权限判定 | `OrgPermissionTest`：无角色无 grant → 拒绝；grant 正交生效 |
| V4 | 部门树 | `OrgDeptTreeTest`：循环引用拒绝；有子部门删除拒绝 |
| V5 | 事件与审计 | `OrgEventAuditTest`：member/role 变更 emit 且入审计 |
| V6 | 租户隔离 | `OrgIsolationTest`：跨租户部门/成员/角色不可见 |
| V7 | 权限旁路拦截 | `OrgBypassBlockTest`：绕过 RBAC 直接调用资源 API 被拒 |

### 运行命令

```bash
mvn -pl services/gnex-ops-service test "-Dtest=OrgMembershipTest,OrgRbacCacheTest,OrgPermissionTest,OrgDeptTreeTest,OrgEventAuditTest,OrgIsolationTest,OrgBypassBlockTest"
```

## §7 影响域

- 作用范围：**租户级/平台级**——组织数据与权限判定。
- 被谁消费：ent-auth（JWT roles）、ent-api（Gateway 校验）、ent-billing（成员配额）、UI 管理台。
- 故障影响半径：权限判定缺陷 = 越权（P0 级安全）；缓存不一致 ≤5s 窗口（RBAC 缓存兜底）。
- 升级影响：🔒 平台级升级；角色/权限语义变更 = 全租户权限回归（灰度 + 审计）。

## §8 后续（未纳入首版）

- SSO 组织同步（对接 ent-auth SSO 配置）
- 细粒度资源授权 UI（grant 管理面板）
- 组织层级配额继承（部门级预算/配额）

---

*GNEX3.0 组织与权限 — 部门/成员/角色 SDD。*
