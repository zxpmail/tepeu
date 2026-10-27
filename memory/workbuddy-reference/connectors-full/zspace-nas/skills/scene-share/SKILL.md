---
name: zspace-scene-share
description: 创建/管理分享链接，用标签组织文件，并跟踪异步任务。 触发词：极空间 / NAS / 分享与协作。
description_zh: 创建/管理分享链接，用标签组织文件，并跟踪异步任务。
description_en: Create and manage share links, organise files with labels, and follow async tasks.
version: 2.0.3
author: zspace
allowed-tools: copy,delete,download_file,file_info,file_labels,file_tasks,list_dir,mkdir,move,nas_query,read_file,recyclebin,rename,share,upload_file,user_space,write_file
---

# 场景：分享与协作

创建/管理分享链接，用标签组织文件，并跟踪异步任务。

- 组合服务：`zfilev2`
- 涉及工具：17 个（各服务的接口细节见对应服务 Skill）

## 步骤

- 1. 定位对象 → `search_files` / `list_dir`
- 2. 建分享 → `share {action:"create", paths, expire_days, with_password:true}`；把返回的**链接 + 提取码 + 有效期**一起回复用户（提取码由 NAS 随机生成，无法指定）
- 3. 管理 → `share {action:"list"}` → `setexpire`（传 ids + expire_days）/ `delete`（传 ids）
- 4. 组织 → `file_labels {action:"create"}` → `assign`；之后 `files` 按标签取回
- 5. 大批量操作 → `move`/`copy` → `file_tasks` 跟踪

## 要点

- ⚠️ 无提取码/无有效期的分享任何人拿到链接即可访问——创建前必须确认。
- 分享是"外网可达"的链接，注意不要分享敏感目录。
- 有效期最小单位是**天**（底层为秒）：不指定时 NAS 默认 2 天；提取码固定 4 位且由 NAS 生成。

## 通用约束

- 涉及删除/覆盖/清空等**高危**操作时，必须先列出对象清单并取得用户明确同意。
- 依赖「应用商店类」应用的能力，未安装时返回 `E_APP_NOT_INSTALLED`：向用户说明**是应用没装**，并引导到 NAS 应用商店安装。
- 返回统一为 `{ok:true,data}` 或 `{ok:false,error:{code,message}}`；列表被截断时用 `next_start` 翻页。
- 通用约定与错误码：`skills/zfilev2/references/api-conventions.md`。
