---
name: zspace-go-base-set
description: 网口/链路聚合/网桥/公网 IP 等网络底层信息（只读）。 触发词：极空间 / NAS / 网络基础。
description_zh: 网口/链路聚合/网桥/公网 IP 等网络底层信息（只读）。
description_en: Network interfaces, link aggregation, bridges and public IP address (read-only).
version: 2.0.3
author: zspace
allowed-tools: nas_query
---

# 网络基础（go-base-set）

网口/链路聚合/网桥/公网 IP 等网络底层信息（只读）。

- 服务类型：**非应用商店类（核心服务）**
- 对应目录：`NasServices/go-base-set`
- 对外前缀：`/v1/interface/list · /network-settings/*`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| GET | `/v1/interface/list` | 网口清单（**GET**；POST 会 405。5055 网关仅代理这一条 go-base-set 只读路由） | 只读 | ✅ 可直接调用 |
| POST | `/v1/interface/show` | 单个网口详情（**POST**） | 只读 | ⚠️ 本机网关未暴露（实测 404，勿依赖） |
| GET | `/v1/bond/list` | 链路聚合清单（**GET**，源码 `router.HandlerFunc(http.MethodGet, …)` 核对） | 只读 | ⚠️ 本机网关未暴露（实测 404，勿依赖） |
| GET | `/v1/bridge/list` | 网桥清单（**GET**，同上） | 只读 | ⚠️ 本机网关未暴露（实测 404，勿依赖） |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 网络只读查询用 `nas_query {path:"/v1/interface/list", method:"GET"}`（该路径在只读白名单内）

## 5. 明确不提供

- `/v1/interface/update`、`/v1/bond/*`、`/v1/bridge/*` 的写操作——改错会直接断网，在硬黑名单中

## 6. 备注

- 该服务无专用工具，只读查询走 `nas_query` 兜底。
- ⚠️ 方法以**源码为准**（`go-base-set/routes.go`）：`/v1/interface/list`、`/v1/bond/list`、`/v1/bridge/list` 是 **GET**，`/v1/interface/show` 是 **POST**。若 GET 返回 404，**不是方法用错**，而是该机 5055 网关没有代理这条路由（`/v1/interface/list` 之外的三条常见如此）——请勿改成 POST 重试。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
