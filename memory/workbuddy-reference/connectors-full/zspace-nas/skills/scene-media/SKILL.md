---
name: zspace-scene-media
description: 一个入口检索影视/音乐/阅读/有声书/漫画五个库，先查应用是否安装再取数据。 触发词：极空间 / NAS / 媒体库统一检索。
description_zh: 一个入口检索影视/音乐/阅读/有声书/漫画五个库，先查应用是否安装再取数据。
description_en: Search and browse all media libraries (movies, music, books, audiobooks, comics) from one entry point.
version: 2.0.3
author: zspace
allowed-tools: media_convert,media_detail,media_list,media_search,nas_query
---

# 场景：媒体库统一检索

一个入口检索影视/音乐/阅读/有声书/漫画五个库，先查应用是否安装再取数据。

- 组合服务：`zvideo_v2` · `zmusicv2` · `zreader` · `zaudio` · `zcomic` · `media_converter`
- 涉及工具：5 个（各服务的接口细节见对应服务 Skill）

## 步骤

- 1. 先确认目标库的应用是否安装 → `app_status {app_id}`（video/music/bookLib/audioBooks/comics）
- 2. 搜索 → `media_search {library, query}`
- 3. 浏览 → `media_list {library}`；详情 → `media_detail {library, id}`
- 4. 需要转码/处理 → `media_convert {action:"create", ...}`（imgProcessing）

## 要点

- `library` 取值：video / music / book / audio / comic。
- 有声读物没有搜索接口，`media_search {library:"audio"}` 会返回 `{unsupported:true}`，改用 `media_list`。
- 五个库的分页家族不同（影视 1-based 页码、音乐 1-based 页码、阅读/有声书/漫画 start+num），连接器已统一适配并给出 `next_start`。
- `media_detail` 查不到时会返回 `{not_found:true, reason}`，不再"静默成功返回空对象"；极影视用 `collection_id`、极漫画用文件路径、有声读物用 `track_id`。

## 通用约束

- 涉及删除/覆盖/清空等**高危**操作时，必须先列出对象清单并取得用户明确同意。
- 依赖「应用商店类」应用的能力，未安装时返回 `E_APP_NOT_INSTALLED`：向用户说明**是应用没装**，并引导到 NAS 应用商店安装。
- 返回统一为 `{ok:true,data}` 或 `{ok:false,error:{code,message}}`；列表被截断时用 `next_start` 翻页。
- 通用约定与错误码：`skills/zfilev2/references/api-conventions.md`。
