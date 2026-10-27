---
name: zspace-appstore
description: 查询应用是否已安装/运行/版本——所有「应用商店类」能力的前置门禁。 触发词：极空间 / NAS / 应用商店与安装门禁。
description_zh: 查询应用是否已安装/运行/版本——所有「应用商店类」能力的前置门禁。
description_en: App install status, running state and versions — the gate for every store-app feature.
version: 2.0.3
author: zspace
allowed-tools: app_status,nas_query
---

# 应用商店与安装门禁（appstore）

查询应用是否已安装/运行/版本——所有「应用商店类」能力的前置门禁。

- 服务类型：**非应用商店类（核心服务）**
- 对应目录：`NasServices/appstore`
- 对外前缀：`/AppStore/* · /manager/*`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `app_status` | 查询极空间应用是否已安装/正在运行及其版本。当某个功能报「未安装」、或用户刚在应用商店装完应用时使用。app_id 可选值见下；不传 app_id 且 all=false 时返回应用清单与中文名。 | 只读 |
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| POST | `/AppStore/v1/open/app/status` | 单应用状态（JSON {app_id}，OAuth 专用；未在清单内返回 code 400）。⚠️ `app_id` 为**必填**：空参调用返回 `400 EOF`，请用专用工具 `app_status {app_id}` | 只读 | ⚠️ 需传必填参数 |
| POST | `/AppStore/v1/list` | 应用清单（已安装 + 内置 + 云端可装；installed 0/1）。⚠️ 真机实测原始响应约 400KB/57 项，**不适合 nas_query 兜底**（会被体积护栏切片摘要），请改用专用工具 app_status | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/AppStore/v1/open/volumes` | 存储卷列表（OAuth 专用） | 只读 | ⚠️ 本机网关未暴露（实测 404，勿依赖） |
| POST | `/AppStore/v1/pkg/tasks` | 安装/升级任务进度 | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/AppStore/v1/pkg/info` | 单个应用合并信息（含最新版本） | 只读 | ⚠️ 未收录（不可兜底） |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 「XX 功能怎么用不了」→ `app_status {app_id}` 判断是否未安装
- 刚装完应用 → `app_status {app_id, refresh:true}` 跳过缓存复检
- 一览所有依赖应用 → `app_status {all:true}`

## 5. 明确不提供

- 安装/卸载/启停应用（`pkg/install`、`pkg/remove`、`pkg/start|stop|restart`）——按产品决策只提示不代装

## 6. 备注

- 门禁语义（真机实测）：`open/app/status` 对未安装应用返回 `code:400`（确证未安装）；`/AppStore/v1/list` 非空但查无此应用同样是未安装；两个接口都失败时才「放行 + warnings」，避免误锁可用功能。
- ⚠️ `/AppStore/v1/list` 原始响应约 400KB（57 个应用的完整元数据）：`nas_query` 触发体积护栏后只会返回切片+摘要；要看应用清单请用 `app_status {all:true}`。
- ⚠️ `/AppStore/v1/open/volumes` 与 `/v1/bond/list` 同类：**本机网关未暴露**（真机 2/2 实测 404，改 method 无效）。契约注册表已标注 `exposure: service-only`，404 文案会追加"契约已标注未暴露"——请勿据此判断"存储卷能力不可用"，改用同服务其它只读接口。
- 该接口偶发 `N001411 无权限进行此操作`（实测约 1/3），连接器在门禁与兜底路径均已做一次瞬时重试。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
