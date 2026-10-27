---
name: zspace-filerescue
description: 误删文件扫描与恢复任务。 触发词：极空间 / NAS / 恢复助手。
description_zh: 误删文件扫描与恢复任务。
description_en: Deleted-file scan and recovery tasks.
version: 2.0.3
author: zspace
allowed-tools: backup_status,nas_query
---

# 恢复助手（filerescue）

误删文件扫描与恢复任务。

- 服务类型：**应用商店类（需先安装应用）**
- 对应目录：`NasServices/filerescue`
- 对外前缀：`/AppStore/filerescue/* → /filerescue/*`
- 依赖应用：`filerescue` —— 调用前连接器会自动过门禁，未安装返回 `E_APP_NOT_INSTALLED`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `backup_status` | 查看极空间各类备份/恢复的状态：备份中心、文档同步、邮件备份、恢复助手（可用），以及 FTP 备份、网盘备份（需应用内登录，连接器只能报告其限制）。各项独立容错。 | 只读 |
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| POST | `/AppStore/filerescue/list` | 扫描/恢复任务列表（真机已验证，返回 {list:[]}） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 「恢复任务进度」→ `backup_status {targets:["rescue"]}`（会先过 `filerescue` 门禁）

## 5. 明确不提供

- 扫描/恢复的写操作（未开放）

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
