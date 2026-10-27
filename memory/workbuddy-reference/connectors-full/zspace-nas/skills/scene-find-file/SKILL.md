---
name: zspace-scene-find-file
description: 跨「文件搜索 + 文件管理」：按名字或内容找到文件，然后读取/下载/分享。 触发词：极空间 / NAS / 找文件并按需取回。
description_zh: 跨「文件搜索 + 文件管理」：按名字或内容找到文件，然后读取/下载/分享。
description_en: Find files by name or content, then read, download or share them.
version: 2.0.3
author: zspace
allowed-tools: copy,delete,download_file,file_info,file_labels,file_tasks,list_dir,mkdir,move,nas_query,read_file,recyclebin,rename,search_content,search_files,share,upload_file,user_space,write_file
---

# 场景：找文件并按需取回

跨「文件搜索 + 文件管理」：按名字或内容找到文件，然后读取/下载/分享。

- 组合服务：`file-search` · `zfilev2`
- 涉及工具：19 个（各服务的接口细节见对应服务 Skill）

## 步骤

- 1. 不确定路径 → 先 `user_space` 拿个人空间前缀（如 `/sata11/my/data`）
- 2. 按文件名找 → `search_files {name:"关键词"}`（支持 `? * + - "短语" #标签`）
- 3. 按内容找 → `search_content {name:"关键词"}`（依赖全文索引）
- 4. 需要确认同一目录下的其它文件 → `list_dir {path}`
- 5. 读内容 → `read_file`（>64KB 用 `offset` 分段；二进制加 `encoding:"base64"`）
- 6. 存到本地 → `download_file {path, local_path}`
- 7. 发给别人 → `share {action:"create", paths:[...], expire_days, with_password:true}`（先确认对象；链接与提取码由工具返回）

## 要点

- 搜索返回的路径可直接用于 `read_file`/`file_info`，不要自己拼路径。
- `/share/...` 是他人分享给我的空间，通常只读。

## 通用约束

- 涉及删除/覆盖/清空等**高危**操作时，必须先列出对象清单并取得用户明确同意。
- 依赖「应用商店类」应用的能力，未安装时返回 `E_APP_NOT_INSTALLED`：向用户说明**是应用没装**，并引导到 NAS 应用商店安装。
- 返回统一为 `{ok:true,data}` 或 `{ok:false,error:{code,message}}`；列表被截断时用 `next_start` 翻页。
- 通用约定与错误码：`skills/zfilev2/references/api-conventions.md`。
