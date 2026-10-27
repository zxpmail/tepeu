---
name: zspace-scene-download
description: 把链接交给 NAS 下载，完成后归档到目标目录并整理。 触发词：极空间 / NAS / 下载与归档。
description_zh: 把链接交给 NAS 下载，完成后归档到目标目录并整理。
description_en: Hand a link to the NAS, track the download, then archive and organise the result.
version: 2.0.3
author: zspace
allowed-tools: copy,delete,download_add,download_file,download_tasks,file_info,file_labels,file_tasks,list_dir,mkdir,move,nas_query,read_file,recyclebin,rename,share,upload_file,user_space,write_file
---

# 场景：下载与归档

把链接交给 NAS 下载，完成后归档到目标目录并整理。

- 组合服务：`downloader` · `zfilev2`
- 涉及工具：19 个（各服务的接口细节见对应服务 Skill）

## 步骤

- 1. 确认「下载」应用 → `app_status {app_id:"downloads"}`
- 2. 选定保存目录 → `user_space` / `list_dir` 确认
- 3. 新建任务 → `download_add {action:"add", uri, dir}`
- 4. 看进度 → `download_tasks {}`
- 5. 完成后归档 → `move`/`copy`（异步任务）→ `file_tasks` 看进度
- 6. 命名整理 → `rename`；打标签 → `file_labels {action:"assign"}`

## 要点

- 下载任务 ID 来自 `download_tasks`；删除任务前先向用户确认清单。

## 通用约束

- 涉及删除/覆盖/清空等**高危**操作时，必须先列出对象清单并取得用户明确同意。
- 依赖「应用商店类」应用的能力，未安装时返回 `E_APP_NOT_INSTALLED`：向用户说明**是应用没装**，并引导到 NAS 应用商店安装。
- 返回统一为 `{ok:true,data}` 或 `{ok:false,error:{code,message}}`；列表被截断时用 `next_start` 翻页。
- 通用约定与错误码：`skills/zfilev2/references/api-conventions.md`。
