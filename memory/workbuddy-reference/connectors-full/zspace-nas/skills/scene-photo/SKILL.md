---
name: zspace-scene-photo
description: 围绕「极相册」：以文搜图、时间线浏览、按相册查看，以及分享/删除。 触发词：极空间 / NAS / 照片查找与整理。
description_zh: 围绕「极相册」：以文搜图、时间线浏览、按相册查看，以及分享/删除。
description_en: Work with the photo album: semantic/OCR photo search, timeline browsing, sharing and confirmed deletion.
version: 2.0.3
author: zspace
allowed-tools: album_ai_search,album_delete_feeds,album_feed_list,album_feeds,album_list,copy,delete,download_file,file_info,file_labels,file_tasks,list_dir,mkdir,move,nas_query,read_file,recyclebin,rename,share,upload_file,user_space,write_file
---

# 场景：照片查找与整理

围绕「极相册」：以文搜图、时间线浏览、按相册查看，以及分享/删除。

- 组合服务：`zalbumv2` · `zfilev2`
- 涉及工具：22 个（各服务的接口细节见对应服务 Skill）

## 步骤

- 1. 确认极相册可用 → `app_status {app_id:"photoAlbum"}`（未安装则引导安装）
- 2. 语义搜图 → `album_ai_search {query_content:"海边日落", mode:"semantic"}`；返回 running 再调一次
- 3. 按文字找图 → `album_ai_search {query_content:"发票", mode:"ocr"}`
- 4. 随便看看 → `album_feed_list {num:30}`（时间线）或 `album_list` → `album_feeds`
- 5. 分享照片 → 先 `album_feeds` 取路径 → `share {action:"create"}`
- 6. 删除 → `album_delete_feeds`（⚠️ 必须先列清单并经用户同意）

## 要点

- 相册类型 ID：10 普通 / 40 系统 / 50 家庭 / 100 AI 人像 / 110 AI 场景 / 120 节假日 / 130 足迹。
- AI 搜索是异步任务：同一次查询返回 `{running:true}` 时用相同参数再调，不要判定为失败。

## 通用约束

- 涉及删除/覆盖/清空等**高危**操作时，必须先列出对象清单并取得用户明确同意。
- 依赖「应用商店类」应用的能力，未安装时返回 `E_APP_NOT_INSTALLED`：向用户说明**是应用没装**，并引导到 NAS 应用商店安装。
- 返回统一为 `{ok:true,data}` 或 `{ok:false,error:{code,message}}`；列表被截断时用 `next_start` 翻页。
- 通用约定与错误码：`skills/zfilev2/references/api-conventions.md`。
