# v3-spec-tool-http

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **审计修订：v0.2（2026-08-18）· 依据 D-P1-08 / D-P1-11 / P2-12**
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-http
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

给模型提供 HTTP 请求工具（method/headers/body/timeout/redirect），复用 2.0 HttpClientTool（JDK java.net.http.HttpClient）并插件化纳入工具管线。新增 SSRF 防护（默认拒绝内网/元数据地址，deny-cidrs 含 IPv4+IPv6 段，D-P2-12）；远程写（POST/PUT/PATCH/DELETE）按风险分级入队 approval 审批（E 审批地板）；响应体上限截断；非 2xx 不得发布统一写证据 `writeEvidence`（D-P1-08；P0-a：故意 http_post 非 2xx 不得 completed）。**非目标**：OAuth2 认证流/代理与响应结构化解析（留 §8）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | 无（HttpClientTool 无 dsh 直接对应） |
| 代码实证来源 | agent-service `com.gnex.agent.tools.HttpClientTool` `[源码实证]`（已有，JDK HttpClient） |
| 技术选型 | 🅶 gnex2.0 模块改造 |
| 改造要点 | ① 保留 2.0 HttpClientTool（JDK java.net.http.HttpClient），插件化并纳入 tools 管线 ② 新增 SSRF 防护（内网/链路本地/元数据地址拒绝——deny-cidrs 覆盖 IPv4 与 IPv6 段，D-P2-12；重定向后复查） ③ 远程写按风险分级入队 approval（E 审批地板：默认入队 + 同 turn 回放 + 可列待办） ④ 响应体超限截断 ⑤ 非 2xx 不得发布写证据（P0-a，统一契约 writeEvidence，D-P1-08） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

无（纯工具插件，经 tools 注册表暴露工具回调）。

### 3.2 注入服务（inject）

- `tools`（必选）：注册 http_client 工具回调；`credentials`（可选）：请求头凭据注入（CredentialHandlerPort）
- `approval`（必选）：远程写审批（E 审批地板）；`scope`（必选）：会话/租户 realm
- `events`（必选）：tool/call · tool/result 发布

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tool/call` | emit | {method, url, headers 摘要} |
| `tool/result` | emit | {method, url, status} |
| `write-evidence` | emit | 统一写证据（D-P1-08）：`writeEvidence { paths: string[], hash: string, turnId: string, ts: number }` → gate 消费远程写证据（paths 记录远端写目标标识）；非 2xx 不发布（P0-a） |

## §4 配置与装配

```yaml
# service-assembly.yml 片段
plugins:
  - id: gnex3-cap-tool-http
    load: mandatory
gnex.tool.http:
  timeout-ms: 30000         # 单请求超时
  max-response-bytes: 1048576  # 响应体上限（超出截断）
  allow-private-net: false  # SSRF：默认拒绝内网
  deny-cidrs:               # SSRF 黑名单（IPv4 + IPv6，D-P2-12）
    - "127.0.0.0/8"
    - "10.0.0.0/8"
    - "172.16.0.0/12"
    - "192.168.0.0/16"
    - "169.254.169.254/32"
    - "::1/128"             # IPv6 回环
    - "fe80::/10"           # IPv6 链路本地
    - "fc00::/7"            # IPv6 ULA 内网
  approval-level: HIGH      # 远程写风险档
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 必须支持 method/headers/body/timeout/redirect 策略；必须实施 SSRF 防护（默认拒绝内网/链路本地/元数据地址 169.254.169.254；deny-cidrs 覆盖 IPv4 与 IPv6 段 ::1/fe80::/fc00::/7，D-P2-12；redirect 后 SSRF 复查）
- ✅ 远程写必须按风险分级入队 approval（默认入队 + 同 turn 回放 + 可列待办）；响应体超 max-response-bytes 必须截断
- ❌ 禁止把请求头/凭据/响应体写入日志（脱敏）；禁止绕过审批直接执行高风险远程写
- ❌ 非 2xx 不得发布写证据 writeEvidence（P0-a：故意 http_post 非 2xx 不得 completed；契约见 05 §4，D-P1-08）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 基础请求 | `HttpToolRequestTest`：GET/POST 头体/超时行为正确 |
| V2 | SSRF | `HttpToolSsrfGuardTest`：内网/元数据/IPv6（::1、fe80::、fc00::/7）地址请求被拒 |
| V3 | 审批 | `HttpToolApprovalTest`：远程写默认入队，approval 批准后执行 |
| V4 | 截断 | `HttpToolTruncateTest`：响应超限截断 |
| V5 | P0-a | `HttpToolEvidenceGateTest`：故意 http_post 非 2xx → 无 writeEvidence、不得 completed |
| V6 | 脱敏 | `HttpToolRedactionTest`：请求头/凭据不出现在日志 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service -am test "-Dtest=HttpTool*Test"
```

## §7 影响域

- 作用范围：**会话级**——每工具调用。
- 被谁消费：agentLoop（工具结果）、approval（远程写审批）、gate（写证据 → 完成门，经 write-evidence）；故障影响半径：单次请求失败不影响会话主流程，超时兜底；SSRF 误拒 = 单次调用失败。
- 升级影响：工具装卸无感（05 §3 L3.6）；writeEvidence 载荷变更 = gate 消费方同步（05 §4 契约为 SSOT）。

## §8 后续（未纳入首版）

- 代理与 OAuth2 认证流（client-credentials 令牌刷新）；响应结构化解析（JSON/XML → 类型化结果）
- 与 web 搜索/抓取工具合并策略

---

*GNEX3.0 HTTP 工具插件 — HttpClientTool 插件化 + SSRF（IPv4/IPv6）/审批/统一证据门 SDD（审计修订版）。*
