---
name: zspace-zreader
description: 电子书库、搜索、书架与最近阅读。 触发词：极空间 / NAS / 极阅读。
description_zh: 电子书库、搜索、书架与最近阅读。
description_en: E-book library, search, bookshelf and recent reading.
version: 2.0.3
author: zspace
allowed-tools: media_detail,media_list,media_search,nas_query
---

# 极阅读（zreader / bookLib）

电子书库、搜索、书架与最近阅读。

- 服务类型：**应用商店类（需先安装应用）**
- 对应目录：`NasServices/zreader`
- 对外前缀：`/zreader/api/v1/*`
- 依赖应用：`bookLib` —— 调用前连接器会自动过门禁，未安装返回 `E_APP_NOT_INSTALLED`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `media_detail` | 获取媒体条目详情：极影视**播放记录**、专辑/曲目信息、书籍详情与阅读进度、漫画章节。⚠️ 极影视的 detail 接口只返回 `play_*` 播放记录，**不含** title/score/genres/release_year 等基础元信息（这些字段在 media_list / media_search 里），需要元信息请先查列表再按 id 取播放进度；返回的 `play_file_path` 可能是历史/失效路径（甚至指向当前设备不存在的盘位），**不可假定其可访问**。 | 只读 |
| `media_list` | 浏览极空间媒体库内容：极影视（影片列表/最近添加）、极音乐（曲目/专辑/歌手）、极阅读（书籍/书架）、有声读物（播放列表/曲目）、极漫画（漫画列表）。对应应用未安装时会提示安装。 | 只读 |
| `media_search` | 在极空间媒体库中搜索：极影视（本地影片库）、极音乐（曲名过滤）、极阅读（书名/作者）、极漫画（漫画名）。有声读物无搜索接口，请改用 media_list。 | 只读 |
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| GET | `/zreader/api/v1/books` | 书籍列表 | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| GET | `/zreader/api/v1/books/search` | 书籍搜索（keyword） | 只读 | ✅ 可直接调用 |
| GET | `/zreader/api/v1/bookshelf` | 书架 | 只读 | ✅ 可直接调用 |
| GET | `/zreader/api/v1/recently/read` | 最近阅读 | 只读 | ✅ 可直接调用 |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 「找某本书」→ `media_search {library:"book", query:"书名"}`

## 6. 备注

- 该服务为 GET 接口（连接器已按 GET 调用）。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
