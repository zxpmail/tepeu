---
name: ec-customer-contact
description: 对指定客户执行外呼拨打、按通讯渠道查询各类联系记录（电话/微信/短信/企微/邮件/拜访的详细通讯日志）、话术复盘、违规与SOP质检、ASR沟通分析。用于：给客户打电话、按渠道查联系记录、质检分析。不用于：按条件搜索客户（→ec-customer-mgmt）、创建或查询客户跟进记录（→ec-customer-sales）。
user-invocable: false
metadata:
   builtin_skill_version: "1.3"
description_zh: "对指定客户执行外呼拨打、按通讯渠道查询各类联系记录（电话/微信/短信/企微/邮件/拜访的详细通讯日志）、话术复盘、违规与SOP质检、ASR沟通分析。用于：给客户打电话、按渠道查联系记录、质检分析。不用于：按条件搜索客户（→ec-customer-mgmt）、创建或查询客户跟进记录（→ec-customer-sales）。"
description_en: "Query channel-specific communication records; add customers to calling lists or the AI phone assistant; inspect speech reviews, quality checks and ASR analysis."
version: "1.3.0"
author: EC
---

# EC 联络中心与质检 (Contact Center & QA)

聚焦于与客户实际发生通讯接触的场景（电话/微信/短信/企微/邮件/拜访）以及后续的合规、话术分析。负责按通讯渠道查询各类联系记录（电话/微信/短信/企微/邮件/拜访的详细通讯日志）、电话外呼拨打、语音转写分析（ASR）、标准 SOP 质检与话术复盘。

## Use When
- 用户请求涉及电话联系记录查询（按渠道查看通话详细日志）。
- 用户请求涉及微信/短信/企业微信/邮件/拜访联系记录查询（按渠道查看通讯详细日志）。
- 用户请求涉及将客户添加到待拨打列表或 AI 电话助手。
- 用户请求涉及话术复盘（热门问题/问题详情）。
- 用户请求涉及违规质检/SOP 质检（列表/详情）。
- 用户请求涉及沟通分析（ASR 记录列表/详情）。
- 用户请求跨域时，本域部分由此 skill 处理。

## Avoid When
- 搜索/查看/创建/转让客户 → `ec-customer-mgmt`
- 创建跟进/待办/工作报告 → `ec-customer-sales`
- 管理标签配置/给客户打标签/查画像 → `ec-customer-insights`
- 查询工商数据 → `ec-customer-business`
- 订单搜索/回款/编辑订单 → `ec-order-mgmt`

## 功能索引

| 意图关键词 | 执行功能 | 调用命令获取流程，严格执行 |
|---------|--------|---------|
| 通话记录/电话记录/联系记录 | 查询电话联系记录 | `ec-crm flow record-phone-list --help` |
| 微信联系记录/微信记录 | 查询微信联系记录 | `ec-crm flow record-wx-list --help` |
| 短信记录/短信联系记录 | 查询短信联系记录 | `ec-crm flow record-sms-list --help` |
| 企业微信记录/企微记录 | 查询企业微信联系记录 | `ec-crm flow record-workwx-list --help` |
| 邮件记录/邮件联系记录 | 查询邮件联系记录 | `ec-crm flow record-email-list --help` |
| 拜访记录/拜访联系记录 | 查询拜访联系记录 | `ec-crm flow record-visit-list --help` |
| 跟进联系记录/跟进动作记录 | 查询跟进联系记录（系统生成的跟进动作事件） | `ec-crm flow record-follow-list --help` |
| 添加到待拨打/加到待拨列表 | 添加到待拨打列表 | `ec-crm flow call-center-add --help` |
| 加到AI电话/自动外呼 | 添加到 AI 电话助手 | `ec-crm flow call-center-ai-add --help` |
| 话术复盘/热门问题/话术分析 | 查询话术复盘热门问题 | `ec-crm flow talk-review-top-list --help` |
| 话术复盘问题详情 | 查询话术复盘问题详情 | `ec-crm flow talk-review-quest --help` |
| 质检记录/违规记录 | 查询违规质检记录列表 | `ec-crm flow inspect-violation-list --help` |
| 违规质检详情 | 查询违规质检记录详情 | `ec-crm flow inspect-violation-detail --help` |
| SOP检查/SOP质检 | 查询 SOP 质检记录列表 | `ec-crm flow inspect-sop-list --help` |
| SOP质检详情 | 查询 SOP 质检记录详情 | `ec-crm flow inspect-sop-detail --help` |
| 沟通分析/通话分析/沟通记录分析 | 查询沟通分析记录列表 | `ec-crm flow asr-list --help` |
| 沟通分析详情 | 查询沟通分析记录详情 | `ec-crm flow asr-detail --help` |

## 意图拆分

当用户意图跨多个域时：
- "查通话记录然后加到待拨打" → 本 skill 依次执行「查询电话联系记录」→「添加到待拨打列表」。
- "先找到客户再加到待拨打" → `ec-customer-mgmt` 执行搜索，再由本 skill 执行「添加到待拨打列表」。
- "质检分析完后记一条跟进" → 本 skill 执行相应质检功能，再转 `ec-customer-sales` 执行「创建跟进」。
- 多步骤任一失败 → 停止后续步骤，告知用户已完成与未完成的内容。

## 硬约束

### ⛔ 铁律：ec-crm 报错即停。以下规则不可协商，不可绕过。

| 错误类型 | 识别 | 行动 | 严禁 |
|--------|------|------|------|
| **命令不存在** | `command not found` 或找不到这个命令 | 停止调用，引导用户在 WorkBuddy 中安装或重新连接 EC CRM 连接器；由宿主管理安装环境，不自行全局安装 | — |
| **入参错误** | 参数格式/值不合法 | 读错误 → 修正 → 重试（≤2次）；超限后停并告知正确格式 | 猜参/删参/填默认值 |
| **业务错误** | 权限不足/客户不存在/状态不允许 | 立即停，原样展示错误 | 重试/换命令/绕路 |
| **结果为空** | 命令正常退出但数据为空 | 告知「查询无结果」，结束 | 换条件重查/扩大范围 |
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
- 加入待拨打列表或 AI 电话助手可能触发后续外呼。执行前核对客户、号码及拨打方式；仅查询、分析联系记录不代表授权外呼。添加到列表成功不等于已完成通话。只有用户已明确授权相应动作和目标时才执行，否则先询问。
- 按实际返回结果说明已完成内容；写入结果不明或超时先核实状态，不盲目重复写入。错误信息保留错误码和业务原因，隐藏 Token、密钥等凭证。
- `ec-crm flow <功能名> --help` 用于取得流程说明，只携带 `--help`；实际业务命令、参数、必填项和返回结构按取得的说明执行，不猜写命令。
