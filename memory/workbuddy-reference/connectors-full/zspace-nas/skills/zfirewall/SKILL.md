---
name: zspace-zfirewall
description: 防火墙状态、网口清单、可放行服务——安全态势的只读视图。 触发词：极空间 / NAS / 安全与防火墙。
description_zh: 防火墙状态、网口清单、可放行服务——安全态势的只读视图。
description_en: Firewall status, network interfaces and allowed-service list (read-only security view).
version: 2.0.3
author: zspace
allowed-tools: nas_query,security_status
---

# 安全与防火墙（zfirewall）

防火墙状态、网口清单、可放行服务——安全态势的只读视图。

- 服务类型：**非应用商店类（核心服务）**
- 对应目录：`NasServices/zfirewall`
- 对外前缀：`/v1/zfirewall/*`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |
| `security_status` | 极空间安全与网络概览：防火墙状态与规则、网口清单、可放行服务清单。仅查询。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| GET | `/v1/zfirewall/info` | 防火墙状态与规则 | 只读 | ✅ 可直接调用 |
| GET | `/v1/zfirewall/interface` | 网口清单 | 只读 | ✅ 可直接调用 |
| GET | `/v1/zfirewall/services` | 可放行的服务清单 | 只读 | ✅ 可直接调用 |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 「防火墙开着吗」→ `security_status {include:["firewall"]}`

## 5. 明确不提供

- `/v1/zfirewall/apply`（整表替换规则）、`/v1/zfirewall/enable`（关闭会清空所有链）——在硬黑名单中

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
