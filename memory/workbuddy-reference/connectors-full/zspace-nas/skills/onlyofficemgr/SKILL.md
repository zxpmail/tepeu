---
name: zspace-onlyofficemgr
description: 在线文档编辑（OnlyOffice）的会话与转换能力。 触发词：极空间 / NAS / 文档编辑。
description_zh: 在线文档编辑（OnlyOffice）的会话与转换能力。
description_en: Online document editing (OnlyOffice) sessions and conversion.
version: 2.0.3
author: zspace
allowed-tools: nas_query
---

# 文档编辑（onlyofficemgr / onlyoffice）

在线文档编辑（OnlyOffice）的会话与转换能力。

- 服务类型：**应用商店类（需先安装应用）**
- 对应目录：`NasServices/onlyofficemgr`
- 对外前缀：`/onlyoffice/* · /hosting/wopi/*`
- 依赖应用：`onlyoffice` —— 调用前连接器会自动过门禁，未安装返回 `E_APP_NOT_INSTALLED`
- 真机状态：—（无 HTTP 接口或不涉及）

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| ANY | `/onlyoffice/*` | OnlyOffice 文档服务（编辑器/转换/字体等由内置 Document Server 提供） | 只读 | —（访客/分享令牌接口，不兜底） |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 本连接器**未封装**该应用的文档会话/转换接口；文件读写请用 `zfilev2` 的工具

## 5. 明确不提供

- 文档会话创建、格式转换、编辑器 UI 相关接口

## 6. 备注

- 该应用的完整接口属于内置 OnlyOffice Document Server，不在本仓库范围内。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
