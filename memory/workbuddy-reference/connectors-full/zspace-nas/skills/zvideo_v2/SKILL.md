---
name: zspace-zvideo
description: 影片库浏览、最近添加、本地搜索与播放记录。 触发词：极空间 / NAS / 极影视。
description_zh: 影片库浏览、最近添加、本地搜索与播放记录。
description_en: Movie library browsing, latest additions, local search and playback history.
version: 2.0.3
author: zspace
allowed-tools: media_detail,media_list,media_search,nas_query
---

# 极影视（zvideo_v2 / video）

影片库浏览、最近添加、本地搜索与播放记录。

- 服务类型：**应用商店类（需先安装应用）**
- 对应目录：`NasServices/zvideo_v2`
- 对外前缀：`/zvideo/*`
- 依赖应用：`video` —— 调用前连接器会自动过门禁，未安装返回 `E_APP_NOT_INSTALLED`
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
| POST | `/zvideo/video/v2/list` | 分类影片列表 | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/zvideo/video/latest` | 多维度最新 | 只读 | ✅ 可直接调用 |
| POST | `/zvideo/search/local` | 本地搜索（key_word） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/zvideo/video/playlist/info` | 播放记录详情 | 只读 | ⚠️ 未收录（不可兜底） |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 「有没有《星际穿越》」→ `media_search {library:"video", query:"星际穿越"}`
- 「最近加了什么片」→ `media_list {library:"video"}`（外部路径 `/zvideo/video/latest` 由场景 Skill 说明）

## 6. 备注

- vhost 会剥离 `/zvideo` 前缀，外部路径与后端路径不同（连接器已处理）。
- 该应用另有 Emby/Jellyfin 面（`/emby/*`），自带 `X-Emby-Token` 凭证体系，**连接器未使用**。
- 字段差异（真机实测 OBS-02）：同一影片在 `media_list` 与 `media_search` 的返回值不同——`is_fav`（1 vs 0）、`tmdb_id`（519182 vs 0）；后端两接口字段完整度不同，需要这些字段时以 `media_list` 为准。
- ⚠️ 取详情要用 **`detail_id`**（列表/搜索条目已由连接器补齐，= `collection_id`）：条目里的 `video_id` 在影视库中**常为空串**，直接传给 `media_detail` 会得到 `N001212`（v6 报告 P5）。
- 分页语义（源码 `GetVideoList` 核对）：`start` 是 **1-based 页码**，缺省 1 且 `0` 会被强制为 1（`if startInt == 0 { startInt = 1 }`）；连接器把工具的 `start` 作为 0-based 页码索引透传为 `start+1`，因此 `start:0` 即第 1 页。超出末页时连接器返回 `empty:true` + 页码说明（v6 报告 P8）。
- `media_detail {library:"video"}` 只返回 `play_*` **播放记录**（不含 title/score/genres 等元信息，需先查 `media_list`/`media_search`）；其中的 `play_file_path` 可能是失效的历史路径，不可假定可访问（OBS-03）。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
