# v3-spec-permission

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.8
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-permission
> **分层**：能力层（L3）
> **受保护级别**：protected（平台持有，禁租户/Agent 卸载）
> **宿主服务**：auth-service
> **实现状态**：规划中

---

## §1 业务目标

权限预设（对齐 dsh-permission-presets）：把 GNEX2.0 auth-service 的 RBAC（user/tenant/role/permission）与工具管线 pre-execute 的权限检查改造为统一权限原语。提供 permission 服务：权限预设档位（如 read-only / sandbox / admin）、工具/操作→所需权限映射、授权序（CW-1）单调收紧判定、以及工具管线 pre-execute 首位的硬权限闸。所有写/高风险操作在进入工具执行前必须通过 permission 判定；授权一旦收紧不可放宽。permission 是 approval 的前置判定（审批人决策前先过权限）。

**非目标**：不做身份认证（SSO/JWT 归 gnex3-ent-auth）；不做审批（归 gnex3-cap-approval）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | @deepseek-ai/dsh-permission-presets |
| 对标 GNEX2.0 模块 | auth-service RBAC（user/tenant/role/permission）+ 工具管线 InspectorChain 权限检查 |
| 技术选型 | 🅶 GNEX2.0 模块改造（🔒 protected） |
| 改造要点 | ① 权限预设档位化（预设→工具/操作权限映射，单一事实源）；② 授权序单调收紧（CW-1）：一次会话/执行内权限只降不升，禁止越权放宽；③ 工具管线 pre-execute 首位硬执行（先于 Risk/Approval）；④ 判定/拒绝/收紧结果经 audit 落审计；⑤ 租户/项目 scope 参与判定（串租户=P0） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `permission` | com.gnex.cap.permission.PermissionService | 预设档位、操作权限判定、授权序单调收紧校验 |

### 3.2 注入服务（inject）

- `auth`（必选）— RBAC 身份/角色/租户解析
- `audit`（必选）— 判定结果/收紧事件审计
- `scope`（必选）— 租户/项目上下文参与判定

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| 无独立事件 | — | permission 为同步判定服务；判定/收紧结果经 `audit/record` 落审计 |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-cap-permission
    load: mandatory
    protection: platform
    config:
      permission:
        presets:
          read-only: [read, search, list]
          sandbox: [read, write, exec-in-sandbox]
          admin: [*]
        monotonic-tighten: true   # CW-1 授权序单调收紧
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 对齐 CW-1 授权序单调收紧：一次会话/执行内权限只降不升，禁止越权放宽
- ✅ 权限判定必须在工具管线 pre-execute 首位硬执行（先于 Risk/Approval/执行）
- ✅ 判定结果必须经 audit 落审计（含拒绝与收紧）
- ❌ 权限判定不得被任何插件/Agent 绕过（工具管线首位硬闸）
- ❌ 不得允许 Agent 自提权/自改预设（预设仅平台管理员维护）
- ❌ 权限预设不得跨租户生效（scope 参与判定，串租户=P0）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 预设档位判定 | PermissionPresetTest：read-only 档拒绝 write 工具 |
| V2 | 授权序单调收紧 | PermissionMonotonicTest：收紧后同会话不得放宽，放宽被拒 |
| V3 | 管线首位执行 | PermissionFirstTest：未过 permission 的工具不进入 Risk/Approval/执行 |
| V4 | 不可绕过 | PermissionBypassTest：插件直接调用底层执行 → 被拒 |
| V5 | 租户隔离 | PermissionScopeTest：预设按租户生效，跨租户判定拒绝 |
| V6 | 审计 | PermissionAuditTest：判定/拒绝/收紧事件完整可查 |

### 运行命令

```bash
mvn -pl services/gnex-auth-service test "-Dtest=PermissionPresetTest,PermissionMonotonicTest,PermissionFirstTest,PermissionBypassTest,PermissionScopeTest,PermissionAuditTest"
```

## §7 影响域

- 作用范围：**每工具调用**（L3.8 影响域：权限判定为全局写路径闸）；故障影响半径：permission 不可用 = 写操作 fail-closed 拒绝（安全侧）。
- 被谁消费：工具管线 pre-execute（首位）、gnex3-cap-approval（风险档位→权限校验）、审计。
- 升级影响：🔒 平台级升级；预设档位变更=全工具行为回归（灰度发布）。

## §8 后续（未纳入首版）

- 权限变更审批流（与 approval 联动）
- 按项目/资源的细粒度 ACL
- 权限预设模板共享（企业内复用）

---

*GNEX3.0 权限预设 — gnex3-cap-permission SDD（🔒 protected）。*
