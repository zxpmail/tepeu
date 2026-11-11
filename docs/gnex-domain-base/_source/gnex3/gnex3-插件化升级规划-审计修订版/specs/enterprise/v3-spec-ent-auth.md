# v3-spec-ent-auth

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-auth
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-auth
> **分层**：企业层（L4）
> **受保护级别**：protected（🔒 平台持有，禁租户/Agent 装卸）
> **宿主服务**：auth-service
> **实现状态**：规划中

---

## §1 业务目标

认证授权内核：登录认证（用户名密码 + OIDC/SAML2 SSO）、JWT 签发/校验/刷新/吊销（双密钥轮换）、API Key 管理（哈希存储）、密码策略与历史、RBAC 角色权限（role_def/permission_def/user_role）、设备会话管理（数量限制/踢出）。企业语义：权限写入 JWT roles claim，网关做粗粒度校验、下游服务信任；RBAC 变更 ≤5s 刷缓存。插件化价值：`auth` 服务成为全部服务入口的统一认证出口，SSO 提供方可插（oidc/saml2/stub）。

**非目标**：不做资源级授权（resource_grant 归 gnex3-ent-org）；不做审计存储（经总线发 audit_log，归 ent-audit 物化）；不做前端登录页（归 client-modules）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | 无（dsh 无此原语；dsh 本地单用户，无 SSO/RBAC/APIKey） |
| 对标 GNEX2.0 模块 | auth-service：core/token `JwtTokenEncoder`/TokenService/TokenValidator/TokenRefresher/TokenRevoker、rbac/service `RbacService`/RbacInitializer、core/apikey ApiKeyService/ApiKeyEncoder、core/sso SsoService/SsoIdpClientRouter（OIDC/SAML2/stub）、core/password PasswordService/BcryptHasher、infrastructure/cache TokenWhitelistCache/TokenBlacklistCache |
| 技术选型 | 🅶 GNEX2.0 模块改造为插件（ports/SPI → auth 服务） |
| 改造要点 | ① 插件化：`auth` 服务聚合 Authentication/Token/Password/ApiKey/Rbac 端口，AuthenticationPort/TokenPort 等收编为插件实现；② 网关粗校验：JWT roles claim 由 api 网关校验，下游信任（E20）；③ 双密钥轮换 + 令牌白/黑名单（StateInterface，Redis 可选，降级日志）；④ SSO 提供方插件化（SsoIdpClient 注册表按 providerType 路由）；⑤ 审计经 bus 发 audit_log（无总线降级本地日志） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `auth` | `com.gnex.contracts.auth.AuthenticationInterface` / `TokenInterface` / `PasswordInterface` / `ApiKeyInterface` + `com.gnex.contracts.ops.RbacManagementInterface` | 认证/令牌/密码/APIKey/RBAC 统一出口 |

### 3.2 注入服务（inject）

- `bus`（可选）：审计事件异步发布（AUDIT_LOG topic；缺失降级本地日志）
- `storage`（可选）：StateInterface 缓存（令牌白/黑名单、RBAC 权限缓存 TTL 30min）
- `scope` / `settings` / `events`（必选）：租户 realm、JWT/SSO/策略配置、事件

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `audit/record` | emit | LOGIN_SUCCESS/LOGIN_FAILED/TOKEN_REFRESHED/TOKEN_REVOKED/APIKEY_CREATED/APIKEY_USED/ACCOUNT_LOCKED/SSO_USER_LINKED/ROTATION_DETECTED/PASSWORD_CHANGED 等 |
| `bus/published` | listen | audit 消息发布结果（可选观测） |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-ent-auth
    load: mandatory
    protection: platform
    config:
      gnex.auth.jwt.secret-ref: ${JWT_SECRET_REF}   # secret 引用，禁硬编码
      gnex.auth.jwt.dual-key-rotation: true          # 双密钥轮换
      gnex.auth.sso.provider-type: oidc             # oidc | saml2 | stub
      gnex.auth.session.max-per-user: 5             # 设备会话限制
      gnex.auth.password.policy: default            # 复杂度/过期/历史 N 次/锁定阈值
      gnex.auth.user.default-role: DEVELOPER        # 最小权限原则
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 认证结果经网关粗粒度校验 + 下游信任（权限写 JWT roles claim，E20；RBAC 变更 ≤5s 刷缓存）
- ✅ JWT 必须支持刷新/吊销/双密钥轮换；黑名单经 StateInterface（Redis 可选，缺失降级日志）
- ✅ API Key 必须哈希存储（SHA-256）+ 掩码展示；密码 BCrypt，策略与历史校验
- ✅ 审计事件必须异步发布（bus 缺失降级本地日志，不阻塞登录）
- ❌ 认证/授权不可被租户插件绕过（🔒：租户/Agent 无装卸权，auth 为平台持有）
- ❌ 不得硬编码密钥/默认凭据（secret 引用 + Secret 注入）
- ❌ SSO 断言不得跳过 IdP 校验（stub 仅 dev profile）；令牌/口令不得明文落日志

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 登录签发 | `AuthLoginTest`：用户名密码登录签发 JWT（含 roles claim），失败锁定阈值生效 |
| V2 | 令牌生命周期 | `TokenLifecycleTest`：validate/refresh/revoke + 双密钥轮换后旧钥宽限期仍可验 |
| V3 | API Key | `ApiKeyTest`：创建返回明文一次、存储为 SHA-256 哈希、吊销即失效 |
| V4 | RBAC | `RbacTest`：角色 CRUD/权限分配，预置角色不可删改（RbacInitializer 种子） |
| V5 | SSO | `SsoTest`：OIDC 断言验证 + 用户关联 + 多租户选择 + SLO 登出 |
| V6 | 密码策略 | `PasswordPolicyTest`：复杂度/历史 N 次拒绝 + 过期强制改密 |
| V7 | 网关粗校验 | `GatewayAuthTest`：无效/过期 token 被 api 网关拒绝（fail-closed） |

### 运行命令

```bash
mvn -pl services/gnex-auth-service test "-Dtest=Auth*Test,TokenServiceTest,RbacServiceTest"
```

## §7 影响域

- 作用范围：**全平台**（租户级）——全部服务入口与网关的认证出口。
- 被谁消费：api 网关（粗校验）、全部下游服务（信任）、d1-web（登录/SSO/用户管理）。
- 故障影响半径：auth 不可用 = 全平台无法登录/鉴权（🔒 最高影响面）；黑名单丢失 = 已吊销 token 复活窗口。
- 升级影响：🔒 平台级升级 + 灰度；密钥轮换、schema 变更（user/rbac 表）走变更单。

## §8 后续（未纳入首版）

- WebAuthn/Passkey 多因素认证
- 细粒度资源授权联动（resource_grant 归 ent-org，auth 校验委托）
- 身份联邦（SCIM 用户目录同步）

---

*GNEX3.0 企业认证授权插件 SDD。*
