---
name: ec-customer-field-mgmt
description: 管理 CRM 客户字段和企业客户联系人字段及字段分组，包括查看、添加、修改、删除、启用、禁用和调整字段顺序，以及查询、添加、编辑、删除和调整顺序字段分组。用于字段配置相关请求。不负责字段从属关系和独立级联节点管理。
user-invocable: false
metadata:
   builtin_skill_version: "1.0"
description_zh: "管理 CRM 客户字段和企业客户联系人字段及字段分组，包括查看、添加、修改、删除、启用、禁用和调整字段顺序，以及查询、添加、编辑、删除和调整顺序字段分组。用于字段配置相关请求。不负责字段从属关系和独立级联节点管理。"
description_en: "Manage customer and company-contact fields and field groups, including creation, editing, deletion, status and ordering. Excludes field dependencies and cascading nodes."
version: "1.0.0"
author: EC
---

# EC 客户自定义字段管理

客户字段和企业客户联系人字段及字段分组的基础配置管理。负责字段查询、添加、修改、启用、禁用、删除和顺序调整，以及分组查询、添加、编辑、删除和顺序调整。

## Use When
- 用户请求涉及客户字段或联系人字段查询。
- 用户请求涉及自定义字段添加、修改、启用、禁用或删除。
- 用户请求涉及客户字段或联系人字段顺序调整。
- 用户请求涉及字段分组查询、添加、编辑、删除或调整顺序。
- 用户请求跨域时，本域部分由此 skill 处理。

## Avoid When
- 管理字段从属关系或级联节点。
- 管理客户标签或客户画像 → `ec-customer-insights`
- 创建、查询或修改客户资料 → `ec-customer-mgmt`

## 功能索引

| 意图关键词 | 执行功能 | 调用命令获取流程，严格执行 |
|---------|--------|---------|
| 查看客户字段/联系人字段 | 查询字段列表 | `ec-crm flow customer-field-list --help` |
| 添加自定义字段 | 添加字段 | `ec-crm flow customer-field-add --help` |
| 修改字段配置 | 修改字段 | `ec-crm flow customer-field-edit --help` |
| 删除自定义字段 | 删除字段 | `ec-crm flow customer-field-delete --help` |
| 启用/禁用字段 | 修改字段状态 | `ec-crm flow customer-field-status --help` |
| 调整字段顺序 | 移动客户字段或联系人字段 | `ec-crm flow customer-field-sort --help` |
| 查看字段分组 | 查询字段分组 | `ec-crm flow customer-field-group-list --help` |
| 添加字段分组 | 添加字段分组 | `ec-crm flow customer-field-group-add --help` |
| 编辑字段分组 | 修改字段分组名称 | `ec-crm flow customer-field-group-edit --help` |
| 删除字段分组 | 删除字段分组 | `ec-crm flow customer-field-group-delete --help` |
| 调整字段分组顺序 | 移动字段分组位置 | `ec-crm flow customer-field-group-sort --help` |

## 跨域边界

当用户请求跨多个域时：
- "修改客户资料中的字段值" → 转 `ec-customer-mgmt` 执行客户资料修改，不执行本 skill 的字段配置命令。

## 硬约束

### ⛔ 铁律：ec-crm 报错即停。以下规则不可协商，不可绕过。

| 错误类型 | 识别 | 行动 | 严禁 |
|--------|------|------|------|
| **命令不存在** | `command not found` 或找不到这个命令 | 停止调用，引导用户在 WorkBuddy 中安装或重新连接 EC CRM 连接器；由宿主管理安装环境，不自行全局安装 | — |
| **入参错误** | 参数格式/值不合法 | 读错误 → 修正 → 重试（≤2次）；超限后停并告知正确格式 | 猜参/删参/填默认值 |
| **业务错误** | 权限不足/字段或分组不存在/状态不允许 | 立即停，原样展示错误 | 重试/换命令/绕路 |
| **结果为空** | 字段或分组列表为空 | 告知「当前查询范围没有字段或分组」，结束 | 换条件重查 |
| **数据错误** | 字段缺失/格式异常/空数据 | 立即停，告知数据异常 | 跳过/补全字段 |
| **flow 命令限制** | 调用 `ec-crm flow` 相关命令 | 只能携带 `--help` 参数 | 传入任何其他参数或选项 |

> **任何未列出的错误 → 一律视为"停"。**

### 注意
- 当执行命令的返回结果过长，被系统截断时，主动去读取完整数据文件内容，确保后续步骤使用完整数据。

## 跨会话恢复
- 继续上次任务时，先使用当前对话中可核对的条件和对象标识；仅在宿主实际提供历史检索能力时使用该能力。标识缺失或无法确认时按本域查询流程重新定位；客户标识通过 `ec-customer-mgmt` 查询，多候选时请用户选择，不猜测或复用未核实的 ID。

## 连接与授权

- 在 WorkBuddy 的 EC CRM 连接器已连接后调用；未登录、登录过期或认证失败时停止业务操作，引导用户重新连接。权限不足按业务错误处理，不反复登录或绕过权限。
- 业务写入只执行用户已明确要求或确认的动作、对象及范围；信息不明确时先询问。已有明确授权无需重复确认，后续步骤不得扩大授权范围。
- 删除、禁用字段或删除字段分组前，核对字段对象、分组及影响范围。只有用户已明确授权相应动作和目标时才执行，否则先询问。
- 按实际返回结果说明已完成内容；写入结果不明或超时先核实状态，不盲目重复写入。错误信息保留错误码和业务原因，隐藏 Token、密钥等凭证。
- `ec-crm flow <功能名> --help` 用于取得流程说明，只携带 `--help`；实际业务命令、参数、必填项和返回结构按取得的说明执行，不猜写命令。
