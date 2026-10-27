---
name: zspace-zspoolv3
description: 存储池/RAID/缓存/磁盘健康与 SMART——存储事实的唯一权威。 触发词：极空间 / NAS / 存储池与磁盘健康。
description_zh: 存储池/RAID/缓存/磁盘健康与 SMART——存储事实的唯一权威。
description_en: Storage pools, RAID, cache and disk health/SMART — the authoritative storage source.
version: 2.0.3
author: zspace
allowed-tools: nas_query,smart_report,storage_info
---

# 存储池与磁盘健康（zspoolv3）

存储池/RAID/缓存/磁盘健康与 SMART——存储事实的唯一权威。

- 服务类型：**非应用商店类（核心服务）**
- 对应目录：`NasServices/zspoolv3`
- 对外前缀：`/zspool/*`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |
| `smart_report` | 单块磁盘的 SMART 健康报告。sn 来源：`disk_info` 的 `mounts.disks[].sn`；该机型/账号下 `mounts.disks` 可能为 null，此时用 `disk_info` 的 `disk_summary[].sn`（等价于 `system_info {fields:["disk"]}` 里 `ssds[].smart.serial_number`）。 | 只读 |
| `storage_info` | 极空间存储池总览：存储池列表/容量/状态（含缓存、空闲盘、外接挂载）。磁盘清单、序列号与健康请用 disk_info；单盘 SMART 用 smart_report。⚠️ 口径差异（OBS-06）：本工具是**存储池口径**（含元数据/保留区），与 disk_info 的**磁盘口径**容量天然不同（真机实测同机 512.1GB vs 486.3GB，差约 24GiB），不是数据错误。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| ANY | `/zspool/info` | 存储池/缓存/空闲盘/外接挂载总览 | 只读 | ✅ 可直接调用 |
| POST | `/zspool/smart/report` | 单盘 SMART（sn + 语言 _l） | 只读 | ✅ 可直接调用 |
| POST | `/zspool/internal/details` | 内部明细（只读） | 只读 | ⚠️ 未收录（不可兜底） |
| POST | `/zspool/internal/poolinfo` | 池内部信息（只读） | 只读 | ⚠️ 未收录（不可兜底） |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 容量还剩多少 → `storage_info`（`pool_list[].total_size/free_size`）
- 硬盘健康吗 → `disk_info {include:["mounts"]}` 取 `disks[].sn`（该机型为 null 时取 `disk_summary[].sn`）→ `smart_report {sn}`

## 5. 明确不提供

- 格式化/重置存储池、删除池、移除磁盘、外接盘擦除、快照回滚、存储服务关机（均在硬黑名单中）

## 6. 备注

- 真机实测：`/zspool/info` 在部分机型**不返回磁盘数组**，磁盘清单与 sn 请用 `disk_info` 的 `mounts.disks[]`。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
