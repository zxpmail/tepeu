# v3-spec-tool-web

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **审计修订：v0.2（2026-08-18）· 依据 D-P1-11 / P2-12**
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-web
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

Web 搜索与抓取工具：Agent 经 `web` 服务（SearchGateway 多提供商路由 + 降级）执行 `tool_web_search`/`tool_web_fetch`。搜索提供商（Tavily/ValueSerp/Baidu/DirectFetch）按可用性与租户配置路由，抓取走 FetchGateway（JDK HttpClient + jsoup + Jina 回退）。结果带来源 URL 与源分级（可审计追溯），抓取限流/限大小/限时长；SSRF 防护：内网/回环地址抓取 deny（deny-cidrs 覆盖 IPv4+IPv6 段，D-P2-12；本插件 pre-execute 维护 SSRF 域名/地址清单为工具特有预检，Security guard 为管线权威，见 §5）。**非目标**：不做页面 JS 渲染（动态页面由提供商 SERP 侧处理）；不做检索索引（企业知识库归记忆/检索层）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-web` + `dsh-web-search-deepseek` |
| 代码实证来源 | agent-service `WebSearchTool`/`WebFetchTool` `[spring-ai 依赖]`（P2-01）+ `SearchGateway`（SearchProviderRouter/TavilyProvider/ValueSerpProvider/BaiduSerpBrowser/DirectFetchProvider/FetchGateway/SourceTierClassifier）`[源码实证]` |
| 技术选型 | 🅶 GNEX2.0 模块改造（SearchGateway → `web` 服务 + 管线工具插件） |
| 改造要点 | ① 注册 `tool_web_search`/`tool_web_fetch` 到 `tools` ② provide `web` 服务（search/fetch 门面，多提供商路由+降级）③ 抓取大小/时长上限 + 正文优先提取 ④ 结果带 sourceUrl + 源分级（SourceTierClassifier）⑤ SSRF 防护（deny-cidrs 硬约束，含 IPv6 段 ::1/fe80::/fc00::/7，D-P2-12） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `web` | `WebService` | search/fetch 门面：多提供商路由 + 降级 + 限流 |
| `webTools` | `WebTool` | web_search/web_fetch 工具实现（注册进 `tools`） |

### 3.2 注入服务（inject）

- `tools`（必选）— 注册工具定义
- `scope`（必选）— 租户配置路由
- `audit` / `credentials`（必选/可选）— 来源审计与提供商密钥引用
- `events`（必选）— 管线收发

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/pre-execute` | listen | **工具特有预检（D-P1-11）**：SSRF 预检——按本插件维护的提供商路由与 deny-cidrs 清单检查目标域名/地址（内网/回环/链路本地 → deny）；通用注入/内容安全检查为管线 Security guard 权威，本 listen 不重复 |
| `tools/execute` | listen（waterfall） | 本体：搜索/抓取 + 上限执行 |
| `tools/post-execute` | listen | 抓取资源释放核查 |

## §4 配置与装配

```yaml
gnex.tools.web:
  search-providers: [tavily, valueserp, baidu, direct-fetch]   # 按序降级
  fetch: {max-bytes: 524288, timeout-ms: 15000}
  deny-cidrs:   # SSRF 黑名单（IPv4 + IPv6，D-P2-12）
    - "127.0.0.0/8"
    - "10.0.0.0/8"
    - "172.16.0.0/12"
    - "192.168.0.0/16"
    - "169.254.169.254/32"
    - "::1/128"        # IPv6 回环
    - "fe80::/10"      # IPv6 链路本地
    - "fc00::/7"       # IPv6 ULA 内网
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 搜索/抓取必须经 `web` 服务（多提供商路由 + 降级，单提供商故障可切换）
- ✅ 抓取必须限流/限大小/限时长，正文提取后返回
- ✅ 结果必须带来源 URL 与源分级（审计可追溯）
- ✅ **职责界定（D-P1-11）**：本插件 `tools/pre-execute` 的 SSRF 预检（deny-cidrs 域名/地址清单，含 IPv6 段 ::1/fe80::/fc00::/7）为**工具特有预检**；通用 Security guard（注入/内容安全）为**管线权威**，工具不重复管线 guard 职责
- ❌ 不得绕过 `web` 直连第三方搜索 API（密钥/限流失控）
- ❌ 不得抓取内网/回环地址（SSRF 防护，deny-cidrs 硬约束，IPv4+IPv6 全覆盖）
- ❌ 抓取不得执行页面内脚本（静态抓取，无 JS 执行）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 搜索路由 | `WebSearchTest`：主提供商返回结果，带 sourceUrl |
| V2 | 提供商降级 | `WebSearchFallbackTest`：主提供商故障 → 降级链生效 |
| V3 | 抓取上限 | `WebFetchLimitTest`：超 max-bytes 截断，超时 TIMED_OUT |
| V4 | SSRF 拒绝 | `WebSsrFTest`：内网/169.254.169.254/IPv6（::1、fe80::、fc00::/7）抓取 → deny |
| V5 | 来源审计 | `WebAuditTest`：结果 sourceUrl 全部落审计 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=Web*Test"
```

## §7 影响域

- 作用范围：**每工具调用**（L3.6 工具集）+ 外部网络出口；被 Agent 循环、`web` 服务消费者、审计（来源合规）消费。
- 故障影响半径：单工具不可用；网络出口缺陷 = SSRF 风险（V4 为发布门）。
- 升级影响：提供商增删 = 无感（路由表配置驱动）。

## §8 后续（未纳入首版）

- 页面快照/归档（抓取结果入附件存储）
- 租户级提供商配额（billing 联动）与搜索质量回评（evaluation 联动）

---

*GNEX3.0 Web 搜索/抓取工具 — gnex3-cap-tool-web SDD（审计修订版：SSRF 预检职责界定 + IPv6 deny-cidrs）。*
