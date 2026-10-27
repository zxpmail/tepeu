---
name: zspace-vm
description: 虚拟机列表、详情与运行状态（只读）。 触发词：极空间 / NAS / 虚拟机。
description_zh: 虚拟机列表、详情与运行状态（只读）。
description_en: Virtual machine list, details and running state (read-only).
version: 2.0.3
author: zspace
allowed-tools: nas_query,vm_info
---

# 虚拟机（vm / virtualMachine）

虚拟机列表、详情与运行状态（只读）。

- 服务类型：**应用商店类（需先安装应用）**
- 对应目录：`NasServices/vm`
- 对外前缀：`/v1/vm/*`
- 依赖应用：`virtualMachine` —— 调用前连接器会自动过门禁，未安装返回 `E_APP_NOT_INSTALLED`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |
| `vm_info` | 「虚拟机」应用：查看虚拟机列表与运行状态；传 vm_id 可看单台详情。仅查询，不提供开关机。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| POST | `/v1/vm/domain/domainList` | 虚拟机列表 | 只读 | ✅ 可直接调用 |
| POST | `/v1/vm/domain/domainDetail` | 虚拟机详情 | 只读 | ⚠️ 未收录（不可兜底） |
| POST | `/v1/vm/domain/getPhyHostStatus` | 物理机状态 | 只读 | ⚠️ 未收录（不可兜底） |
| POST | `/v1/vm/domain/domainSetStatus` | 开关机（未开放） | **高危** | —（写操作，仅专用工具） |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 「有几台虚拟机在跑」→ `vm_info {}`

## 5. 明确不提供

- `domainSetStatus`（开关机/强制停止）——高危运维，未开放

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
