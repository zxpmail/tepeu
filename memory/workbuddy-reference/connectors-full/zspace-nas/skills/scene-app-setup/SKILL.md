---
name: zspace-scene-app-setup
description: 当某个能力报「未安装」时，如何判断、如何引导用户安装、如何复检。 触发词：极空间 / NAS / 应用可用性检查与引导。
description_zh: 当某个能力报「未安装」时，如何判断、如何引导用户安装、如何复检。
description_en: Handle "app not installed": explain it to the user, guide installation, then re-check.
version: 2.0.3
author: zspace
allowed-tools: app_status,nas_query
---

# 场景：应用可用性检查与引导

当某个能力报「未安装」时，如何判断、如何引导用户安装、如何复检。

- 组合服务：`appstore`
- 涉及工具：2 个（各服务的接口细节见对应服务 Skill）

## 步骤

- 1. 收到 `E_APP_NOT_INSTALLED` → 读 `error.app_id` 与 `error.app_name_zh`
- 2. 向用户说明：**是应用没装**（不是连接器故障），给出中文名与"去 NAS 应用商店安装"
- 3. 用户装完后 → `app_status {app_id, refresh:true}` 复检（跳过 30 分钟缓存）
- 4. 一览依赖 → `app_status {all:true}`

## 要点

- 连接器**不会**代替用户安装应用（产品决策）。
- 16 个受门禁管理的应用见 `skills/appstore/SKILL.md`。

## 通用约束

- 涉及删除/覆盖/清空等**高危**操作时，必须先列出对象清单并取得用户明确同意。
- 依赖「应用商店类」应用的能力，未安装时返回 `E_APP_NOT_INSTALLED`：向用户说明**是应用没装**，并引导到 NAS 应用商店安装。
- 返回统一为 `{ok:true,data}` 或 `{ok:false,error:{code,message}}`；列表被截断时用 `next_start` 翻页。
- 通用约定与错误码：`skills/zfilev2/references/api-conventions.md`。
