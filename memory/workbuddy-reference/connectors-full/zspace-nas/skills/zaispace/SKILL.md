---
name: zspace-zaispace
description: AI 空间应用（本仓库仅有打包脚本，无 HTTP 接口）。 触发词：极空间 / NAS / AI 空间。
description_zh: AI 空间应用（本仓库仅有打包脚本，无 HTTP 接口）。
description_en: AI Space app — packaging only, no runtime HTTP API.
version: 2.0.3
author: zspace
allowed-tools: nas_query
---

# AI 空间（zaispace）

AI 空间应用（本仓库仅有打包脚本，无 HTTP 接口）。

- 服务类型：**应用商店类（需先安装应用）**
- 对应目录：`NasServices/zaispace`
- 对外前缀：`—`
- 依赖应用：`zaispace` —— 调用前连接器会自动过门禁，未安装返回 `E_APP_NOT_INSTALLED`
- 真机状态：—（无 HTTP 接口或不涉及）

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |

## 3. 典型用法

- 无可用接口：请引导用户在极空间客户端中使用该应用

## 6. 备注

- 该目录只包含打包/分发内容，运行时不提供 HTTP API，因此不提供工具与接口说明。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
