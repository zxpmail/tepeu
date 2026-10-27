---
name: ec-corp-manage
description: 管理 CRM 企业配置中的客户进展、企业客户文件夹和资料规则分组，支持进展配置、员工创建文件夹权限、文件夹管理、规则分组管理及部门归属调整。
user-invocable: false
metadata:
   builtin_skill_version: "1.0"
description_zh: "管理 CRM 企业配置中的客户进展、企业客户文件夹和资料规则分组，支持进展配置、员工创建文件夹权限、文件夹管理、规则分组管理及部门归属调整。"
description_en: "Manage corporate customer stages, folders, folder creation permissions, data rule groups and department assignments."
version: "1.0.0"
author: EC
---

# EC 企业管理

管理企业级 CRM 配置中的客户进展、企业客户文件夹和资料规则分组。

## Use When
- 用户请求查看客户进展列表或进展启用状态。
- 用户请求修改客户进展名称。
- 用户请求启用或禁用客户进展。
- 用户请求开关“允许员工创建文件夹”。
- 用户请求创建、启用、禁用、删除或重命名企业客户文件夹。
- 用户请求创建、重命名或删除资料规则分组。
- 用户请求给资料规则分组添加、删除或移动部门。

## Avoid When
- 查询或修改某个客户当前的进展值 → `ec-customer-mgmt`
- 管理人工标签、规则标签或客户画像 → `ec-customer-insights`
- 管理客户字段或字段分组 → `ec-customer-field-mgmt`

## 功能索引

| 意图关键词 | 执行功能 | 调用命令获取流程 |
|---------|--------|---------|
| 查看客户进展/拉取进展列表/有哪些进展 | 拉取客户进展列表 | `ec-crm flow corp-stage-list --help` |
| 修改进展名称/重命名进展 | 修改客户进展名称 | `ec-crm flow corp-stage-edit --help` |
| 启用进展/禁用进展/恢复进展 | 启用或禁用客户进展 | `ec-crm flow corp-stage-status --help` |
| 允许员工创建文件夹/禁止员工创建文件夹 | 开关员工创建文件夹权限 | `ec-crm flow corp-folder-manage --help` |
| 创建文件夹/新增客户文件夹 | 创建企业客户文件夹 | `ec-crm flow corp-folder-manage --help` |
| 启用文件夹/禁用文件夹 | 启用或禁用系统文件夹 | `ec-crm flow corp-folder-manage --help` |
| 删除文件夹/移除文件夹 | 删除企业客户自建文件夹 | `ec-crm flow corp-folder-manage --help` |
| 重命名文件夹/修改文件夹名称 | 重命名企业客户自建文件夹 | `ec-crm flow corp-folder-manage --help` |
| 创建资料规则分组/创建规则分组 | 创建资料规则分组 | `ec-crm flow corp-rule-group-create --help` |
| 重命名资料规则分组/修改规则分组名称 | 重命名资料规则分组 | `ec-crm flow corp-rule-group-rename --help` |
| 给规则分组添加部门/删除部门/移动部门 | 管理资料规则分组部门归属 | `ec-crm flow corp-rule-group-member --help` |
| 删除资料规则分组/删除规则分组 | 删除资料规则分组 | `ec-crm flow corp-rule-group-delete --help` |

## 连接与授权

- 在 WorkBuddy 的 EC CRM 连接器已连接后调用；未登录、登录过期或认证失败时停止业务操作，引导用户重新连接。权限不足按业务错误处理，不反复登录或绕过权限。
- 业务写入只执行用户已明确要求或确认的动作、对象及范围；信息不明确时先询问。已有明确授权无需重复确认，后续步骤不得扩大授权范围。
- 删除或禁用配置、调整权限或部门归属前核对企业配置对象与影响范围。只有用户已明确授权相应动作和目标时才执行，否则先询问。
- 按实际返回结果说明已完成内容；写入结果不明或超时先核实状态，不盲目重复写入。错误信息保留错误码和业务原因，隐藏 Token、密钥等凭证。
- `ec-crm flow <功能名> --help` 用于取得流程说明，只携带 `--help`；实际业务命令、参数、必填项和返回结构按取得的说明执行，不猜写命令。
- 命令不存在时停止调用，引导用户在 WorkBuddy 中安装或重新连接 EC CRM 连接器，不自行全局安装。参数错误仅按明确报错修正，最多重试 2 次；权限或业务错误立即停止。查询为空时如实说明；多步骤任一步失败即停止并说明已完成与未完成内容。
