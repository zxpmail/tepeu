---
name: zspace-scene-backup
description: 汇总备份中心、网盘备份、文档同步、FTP 备份、邮件备份的状态。 触发词：极空间 / NAS / 备份状态巡检。
description_zh: 汇总备份中心、网盘备份、文档同步、FTP 备份、邮件备份的状态。
description_en: Summarise backup status across backup centre, cloud drive, doc sync, FTP and mail backup.
version: 2.0.3
author: zspace
allowed-tools: backup_status,nas_query
---

# 场景：备份状态巡检

汇总备份中心、网盘备份、文档同步、FTP 备份、邮件备份的状态。

- 组合服务：`backupcenter` · `cloudbackup` · `ftprsync` · `mailmanager`
- 涉及工具：2 个（各服务的接口细节见对应服务 Skill）

## 步骤

- 1. 一次查多项 → `backup_status {targets:["center","cloud","sync","ftp","mail"]}`
- 2. 逐项解读：返回里每项独立，`{error:...}` 表示该项不可用或应用未安装
- 3. 应用未装 → `app_status {app_id}` 并引导用户在 NAS 应用商店安装

## 要点

- 备份族的后端部分在外部仓库，连接器只保证"逐项容错、如实转述"，不会编造状态。
- 部分备份任务接口尚未真机验证；若整项报错，如实告知用户该项暂不支持查询。

## 通用约束

- 涉及删除/覆盖/清空等**高危**操作时，必须先列出对象清单并取得用户明确同意。
- 依赖「应用商店类」应用的能力，未安装时返回 `E_APP_NOT_INSTALLED`：向用户说明**是应用没装**，并引导到 NAS 应用商店安装。
- 返回统一为 `{ok:true,data}` 或 `{ok:false,error:{code,message}}`；列表被截断时用 `next_start` 翻页。
- 通用约定与错误码：`skills/zfilev2/references/api-conventions.md`。
