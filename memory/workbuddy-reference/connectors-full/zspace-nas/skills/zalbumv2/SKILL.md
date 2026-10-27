---
name: zspace-zalbumv2
description: 相册、时间线、AI 语义搜索（CLIP）与 OCR 图片文字搜索。 触发词：极空间 / NAS / 极相册。
description_zh: 相册、时间线、AI 语义搜索（CLIP）与 OCR 图片文字搜索。
description_en: Photo albums, timeline, CLIP semantic photo search and OCR text search.
version: 2.0.3
author: zspace
allowed-tools: album_ai_search,album_delete_feeds,album_feed_list,album_feeds,album_list,nas_query
---

# 极相册（zalbumv2 / photoAlbum）

相册、时间线、AI 语义搜索（CLIP）与 OCR 图片文字搜索。

- 服务类型：**应用商店类（需先安装应用）**
- 对应目录：`NasServices/zalbumv2`
- 对外前缀：`/v2/album/*`
- 依赖应用：`photoAlbum` —— 调用前连接器会自动过门禁，未安装返回 `E_APP_NOT_INSTALLED`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `album_ai_search` | 极相册 AI 搜索。异步任务：提交后本工具会轮询至完成或超时；若返回 running，可用同一 query_content（或返回的 task_id）再次调用继续查询。⚠️ 三种模式的**返回粒度不同**：`semantic` 走 `/v2/album/search/*`，命中结果是**相册**（`albumListByType`，需要 `types`）；`clip` 走 `/v2/album/feed/search/*`，命中结果是**照片**（`list` + `score`，与 App 的「语义搜索」一致，支持 `image_path` 以图搜图）；`ocr` 走 `/v2/album/ai/ocr/search`。**想按内容找照片就用 `clip`**；空结果时会附 `index_progress`（图片级特征提取进度）帮助区分"索引没建完"与"确实没有匹配"。 | 只读 |
| `album_delete_feeds` | ⚠️ 高危：从极相册删除照片（feed）。调用前必须向用户列出待删照片清单（数量/相册/时间）并取得明确同意。 | **高危** |
| `album_feed_list` | 极相册时间线照片流（全部照片，按时间浏览）。⚠️ 返回结构随 `grouped` 变化：`grouped:false` → `data.list` 是**照片对象**数组；`grouped:true` → `data.list` 是**日期分段**数组（元素 `{date,num}`，另含 `total`/`wa`）。两种模式都**没有** `timeline` 字段，按 `data.list` 取即可。 | 只读 |
| `album_feeds` | 极相册内照片/视频列表（分页） | 只读 |
| `album_list` | 极相册列表（含 AI 人像/宠物/场景/回忆等类型）。include_dirs=true 时一并返回相册来源目录。📌 返回结构与其它列表工具**不同**：相册列表在 `data.albums.list`（`albums.total`/`albums.truncated`/`albums.next_start` 同层），来源目录在 `data.dirs` —— 不要按 `data.list` 取值。 | 只读 |
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| POST | `/v2/album/albums` | 相册列表（types 过滤；⚠️ 未知取值后端不报错且返回全量，与省略等价） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/v2/album/dirs` | 相册来源目录 | 只读 | ✅ 可直接调用 |
| POST | `/v2/album/album/feeds` | 相册内照片（album_id 维度） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/v2/album/feed/list` | 全部照片时间线（`grouped:true` 改走 timeline 按日期分段，含未文档化的 `wa` 字段，且仍受 200 条硬上限） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/v2/album/feed/timeline` | 时间线（按日期分段） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/v2/album/search/create` | 语义搜索建任务 | 只读 | —（写操作，仅专用工具） |
| POST | `/v2/album/search/query` | 语义搜索查结果（status: 1=running/2=成功/3=失败） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/v2/album/feed/search/create` | CLIP 以文搜图建任务 | 只读 | —（写操作，仅专用工具） |
| POST | `/v2/album/feed/search/query` | CLIP 以文搜图查结果 | 只读 | —（写操作，仅专用工具） |
| POST | `/v2/album/feed/search/ai_state` | 图片级 AI 特征提取进度（feedCount/processedCount/runngingStatus）——区分"索引没建完"与"确实没有匹配" | 只读 | ⚠️ 未收录（不可兜底） |
| POST | `/v2/album/ai/ocr/search` | OCR 图片文字搜索 | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/v2/album/feed/delete` | 删除照片（全局） | **高危** | —（写操作，仅专用工具） |
| POST | `/v2/album/album/feed/delete` | 从相册移除（语义不同，勿混用） | 写 | —（写操作，仅专用工具） |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 「有没有海边的照片」→ `album_ai_search {query_content:"海边", mode:"clip"}`（**图片级**，与 App「语义搜索」一致；返回 running 就再调一次）；`mode:"semantic"` 是**相册级**，用于找"某个相册"而不是找照片
- 「最近拍的照片」→ `album_feed_list {num:30}`
- 「某个相册里有什么」→ `album_list` 取 album_id → `album_feeds`

## 4. 风险与确认

- `album_delete_feeds`：必须先列出待删照片清单并取得用户同意

## 6. 备注

- 真机实测：相册接口全部可达；2026-09-18 复测时间线有 35 条（含录屏视频）、相册 4 个（旧备注「无照片数据」已过期，见 OBS-04）。AI 搜索的 `types` 为**必填**，连接器已给默认全量值；语义索引未就绪时返回 N003597 并给出 hint。
- ⚠️ `album_feeds` 的 `album_id` 后端按**字符串**比较：连接器已接受字符串或数字并统一转成字符串（此前传数字会静默返回空相册或 schema 报错，v6 报告 P9）。
- ⚠️ `album_list` 的返回结构**有意与其它列表工具不同**（OBS-10）：相册列表在 `data.albums.list`，`total`/`truncated`/`next_start` 同在 `albums` 内；`include_dirs=true` 时目录在 `data.dirs`。工具 description 已注明，别按 `data.list` 取值。
- ⚠️ `album_list {types}` 传**未知取值**时后端不报错、直接返回全量（与省略 `types` 等价，OBS-11 实测：`types:"999"` → total 2369 = 全量，`types:"100"` → 2304 = 过滤生效）——不要用返回条数判断某个类型是否存在。
- ⚠️ **AI 搜索有两种粒度，别混用**（M7/M8，源码 `zalbumv2/server/router.go` 核对）：`mode:"semantic"` 走 `/v2/album/search/*`（`PageCtr`）→ 返回 **相册级**结果 `albumListByType`（按类型分组的**相册**，`types` 必填）；`mode:"clip"`/`"ocr"` 走 `/v2/album/feed/search/*`（`FeedCtr`）→ 返回 **图片级**结果 `{total, list:[… 带 score]}`。用户在 App「极相册 → 语义搜索」看到的是**图片级**结果，对应连接器的 **`clip`** 模式；拿 `semantic` 找照片会"恒空"（它找的是相册）。连接器已在 semantic 空结果的 hint 里直接提示改用 `clip`。
- ⚠️ 图片级检索的**索引进度**可查：`/v2/album/feed/search/ai_state`（字段 `feedCount`/`processedCount`/`runngingStatus`——后端就是这种拼写）。`album_ai_search` 在空结果与超时（running）时会附带 `index_progress{feed_count,processed_count,percent,ready,extracting}`，用于区分"索引没建完"与"确实没有匹配"。
- 相机类型 ID：10 普通 / 40 系统 / 50 家庭 / 60 宝宝 / 100 AI 人像 / 110 AI 场景 / 120 AI 节假日 / 130 AI 足迹。
- 修复记录：v2.0.0 修正了两个 404 路径（`album/album/album/feeds` → `album/album/feeds`；`album/album/feed/list` → `album/feed/list`），真机已验证可达。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
